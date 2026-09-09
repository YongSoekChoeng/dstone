# dstone-ai-engine 🤖

## 목차

- [1. 개요](#1-개요)
- [2. 기술 스택](#2-기술-스택)
- [3. 패키지 구조 (Phase별)](#3-패키지-구조-phase별)
- [4. Phase 로드맵](#4-phase-로드맵)
- [5. Phase 0~1 — Chat API, Gateway, Session, Prompt](#5-phase-01--chat-api-gateway-session-prompt)
  - [5.1 아키텍처](#51-아키텍처)
  - [5.2 Gateway — provider를 설정 한 줄로 교체](#52-gateway--provider를-설정-한-줄로-교체)
  - [5.3 Session — Redis 기반 대화 히스토리](#53-session--redis-기반-대화-히스토리)
  - [5.4 Prompt — 템플릿 버저닝](#54-prompt--템플릿-버저닝)
- [6. Phase 2 — RAG (Retrieval-Augmented Generation)](#6-phase-2--rag-retrieval-augmented-generation)
  - [6.1 RAG 파이프라인 한눈에 보기](#61-rag-파이프라인-한눈에-보기)
  - [6.2 문서 적재 (ingest)](#62-문서-적재-ingest)
  - [6.3 임베딩 (embedding)](#63-임베딩-embedding)
  - [6.4 검색 (retrieval) 과 RAG-증강 채팅](#64-검색-retrieval-과-rag-증강-채팅)
  - [6.5 on/off 스위치 하나로 켜고 끄기](#65-onoff-스위치-하나로-켜고-끄기)
- [7. API 레퍼런스](#7-api-레퍼런스)
- [8. 설정 레퍼런스 (`conf/application.yml`)](#8-설정-레퍼런스-confapplicationyml)
- [9. 필요 인프라](#9-필요-인프라)
- [10. 빌드 및 실행](#10-빌드-및-실행)
- [11. 문제 해결 (실제로 겪은 에러 모음)](#11-문제-해결-실제로-겪은-에러-모음)
- [12. 다음 단계 (Phase 3~4)](#12-다음-단계-phase-34)

## 1. 개요

`dstone-ai-engine`은 Spring AI 기반의 **provider-agnostic AI & MLOps 코어 엔진**이다. 특정 SI 프로젝트 하나만을 위한 기능이 아니라, **미래의 여러 SI 프로젝트가 공통으로 가져다 쓸 수 있는 AI 서빙 플랫폼**을 목표로 Phase 0부터 단계적으로 쌓아 올리고 있다.

> 💡 **왜 "provider-agnostic"이 중요한가?**
> LLM/임베딩 provider(Anthropic, OpenAI, 로컬 Ollama 등)는 프로젝트마다, 시기마다 바뀔 수 있다. `dstone-ai-engine`은 코드를 고치지 않고 `application.yml`의 값 하나만 바꿔서 provider를 교체할 수 있게 설계되어 있다 — 이게 이 엔진의 핵심 설계 철학이다.

- **Artifact ID:** `dstone-ai-engine`
- **Packaging:** JAR (Spring Boot executable)
- **Main Class:** `net.dstone.ai.DstoneAiEngineApplication`
- **기본 포트:** 8081
- **루트 패키지:** `net.dstone.ai`
- **의존:** `dstone-common` (Redis/DB/Jasypt 등 공통 인프라 재사용)
- **배포 형태:** `dstone-boot`와 동일하게 컨테이너화 후 로컬 `kind` 클러스터에 Pod로 배포(상세: [cloud-architecture.md](cloud-architecture.md)) — ⚠️ 매니페스트/Dockerfile은 준비돼 있지만 **아직 실제로 `kubectl apply` 하지는 않은 상태**다.

---

## 2. 기술 스택

| 영역 | 기술 |
|---|---|
| 코어 | Java 21, Spring Boot 4.1.x (Spring Framework 7), dstone-common |
| AI | **Spring AI 2.0.1** (Boot 4 / Framework 7 대응 2.x 라인) |
| Chat LLM | Anthropic Claude (`spring-ai-starter-model-anthropic`) — OpenAI/Ollama starter도 함께 클래스패스에 두고 설정으로만 교체 |
| Embedding | Ollama 로컬 모델(`bge-m3`) — OpenAI로도 교체 가능 |
| 세션 저장소 | Redis (`dstone-common`의 Redis 인프라 재사용) |
| 벡터 저장소 | PostgreSQL + **pgvector** 0.8.1 (`spring-ai-starter-vector-store-pgvector`) |
| 문서 적재 | Apache Tika (`spring-ai-tika-document-reader`) — PDF/DOCX/PPTX/HTML/TXT 등 대부분 포맷 커버 |
| 텍스트 분할 | `TokenTextSplitter` (토큰 단위 청킹) |
| DB 접근 | HikariCP + log4jdbc (SQL 로깅), 표준 Spring Boot 단일 DataSource 자동설정 |
| 관리 콘솔(부가) | [Ollama Web UI Lite](software/ollama-webui-lite.md) — Ollama 모델 관리/수동 채팅 테스트용, dstone 애플리케이션과 런타임 의존 없음 |
| 빌드 | Maven (JAR 패키징, `spring-boot-maven-plugin`) |

---

## 3. 패키지 구조 (Phase별)

```
src/main/java/net/dstone/ai/
├── DstoneAiEngineApplication.java   # 메인 진입점 (env-<profile>.properties 부트스트랩)
├── config/                          # Phase 0 — ChatClient 등 공통 빈 wiring
│   ├── Config.java                  # dstone-common @Import (net.dstone.common 컴포넌트 스캔 보완)
│   ├── ConfigChatClient.java        # provider 무관하게 동작하는 ChatClient 빈
│   ├── ConfigChatMemory.java        # ChatMemory(windowing) 빈 - Phase 1
│   ├── ConfigRedis.java             # Redis 인프라
│   └── ConfigAspect.java            # AOP(컨트롤러/서비스 프로파일링)
├── api/                             # Phase 0 — REST 컨트롤러
│   ├── ChatController.java          # POST /api/ai/chat (+ RAG-증강 옵션)
│   ├── RagController.java           # /api/ai/rag/* (Phase 2)
│   └── dto/                         # ChatRequest/Response, RagSearchRequest, IngestResponse, RetrievedChunk
├── gateway/                         # Phase 1 — LLM provider 추상화
│   ├── AiProvider.java              # anthropic | openai | ollama
│   ├── GatewayProperties.java       # spring.ai.model.chat 검증(fail-fast)
│   └── provider/                    # provider별 커스터마이징 필요해지면 담을 자리(현재 비어있음)
├── prompt/                          # Phase 1 — 프롬프트 템플릿 관리
│   ├── PromptTemplateRegistry.java  # classpath:prompts/{name}/{version}.st 렌더링
│   └── PromptProperties.java        # 템플릿별 활성 버전 설정
├── session/                         # Phase 1 — 대화 히스토리
│   └── RedisChatMemoryRepository.java  # Spring AI ChatMemoryRepository의 Redis 구현체
├── rag/                             # Phase 2 — RAG 파이프라인 (dstone.ai.rag.enabled=true일 때만 활성)
│   ├── ingest/DocumentIngestService.java     # Tika 추출 → 청킹 → VectorStore 저장(upsert)
│   ├── embedding/EmbeddingProvider.java      # openai | ollama (anthropic 불가)
│   ├── embedding/EmbeddingProperties.java    # spring.ai.model.embedding 검증(fail-fast)
│   └── retrieval/RetrievalService.java       # 유사도 검색 (topK/threshold 기본값 관리)
├── agent/          # Phase 3(예정) — Tool/Function calling, 오케스트레이션, memory
├── governance/     # Phase 4(예정) — Guardrail, PII 필터, rate limit, 인증
└── observability/  # Phase 4(예정) — 토큰 사용량/비용/트레이싱/Eval
```

---

## 4. Phase 로드맵

```mermaid
timeline
    title dstone-ai-engine Phase 로드맵
    Phase 0 : Chat API 스켈레톤 : Anthropic 하드코딩 : POST /api/ai/chat
    Phase 1 : Gateway (provider 추상화) : Session (Redis 대화 히스토리) : Prompt (템플릿 버저닝)
    Phase 2 : RAG 파이프라인 : ingest(Tika+청킹) : embedding(Ollama/OpenAI) : retrieval(pgvector)
    Phase 3 (예정) : Agent : Tool/Function calling : 오케스트레이션
    Phase 4 (예정) : Governance : Observability : Guardrail·비용추적·Eval
```

| Phase | 상태 | 핵심 산출물 |
|:---:|:---:|---|
| 0 | ✅ 완료 | `ChatController`, Anthropic 하드코딩, Jasypt `ENC(...)` 키 관리 |
| 1 | ✅ 완료 | `gateway`(provider 추상화), `session`(Redis 히스토리), `prompt`(템플릿 버저닝) |
| 2 | ✅ 완료 (실동작 검증됨) | `rag.ingest`/`rag.embedding`/`rag.retrieval`, pgvector, `RagController`, `ChatController.ragEnabled` |
| 3 | ⏳ 예정 | `agent` — Tool calling, 오케스트레이션 |
| 4 | ⏳ 예정 | `governance`/`observability` — Guardrail, 인증, 비용/토큰 추적, Eval |

---

## 5. Phase 0~1 — Chat API, Gateway, Session, Prompt

### 5.1 아키텍처

```mermaid
flowchart LR
    Client(["클라이언트"]) -->|"POST /api/ai/chat"| Ctrl[ChatController]
    Ctrl --> CC["ChatClient<br/>(ConfigChatClient)"]
    CC -->|"spring.ai.model.chat 값에 따라<br/>단 하나만 자동설정됨"| GW{{"Gateway<br/>anthropic / openai / ollama"}}
    CC -->|"ChatMemory.CONVERSATION_ID"| Mem[("Redis<br/>dstone:ai:session:*")]
    Ctrl -->|"promptName 지정 시"| Prompt["PromptTemplateRegistry<br/>classpath:prompts/{name}/{ver}.st"]
    GW --> LLM(["Claude / GPT / 로컬 LLM"])
```

- `GatewayProperties`가 `@PostConstruct`에서 `spring.ai.model.chat` 값을 검증해, 값이 비어있거나 지원하지 않는 값이면 **"ChatModel 빈이 없다"는 원인불명 에러 대신** 명확한 메시지로 기동을 실패시킨다.
- `ConfigChatClient`는 `GatewayProperties`를 빈 팩토리 메서드의 첫 파라미터로 받아, 그 검증이 `ChatClient.Builder` 해석보다 먼저 실행되도록 순서를 강제한다.

### 5.2 Gateway — provider를 설정 한 줄로 교체

```yaml
spring:
  ai:
    model:
      chat: anthropic   # anthropic | openai | ollama 중 하나로만 교체하면 끝
```

| Provider | 설정 위치 | 비고 |
|---|---|---|
| `anthropic` (현재 사용 중) | `spring.ai.anthropic.api-key` | API 키는 Jasypt `ENC(...)`로 암호화, `env.properties` 경유하지 않음 |
| `openai` | `spring.ai.openai.api-key`, `base-url` | 로컬 vLLM(OpenAI 호환 API) 연결 시 `base-url`만 vLLM 서버로 override |
| `ollama` | `spring.ai.ollama.base-url` | 사내망/로컬 Ollama, API 키 불필요 |

> ⚠️ Azure OpenAI는 Spring AI 2.x에서 chat model provider로 완전히 제거되어(vector-store 용도만 남음) 지원 목록에 없다.

### 5.3 Session — Redis 기반 대화 히스토리

Spring AI가 공식 제공하는 `ChatMemoryRepository`는 jdbc/cassandra/neo4j뿐이라(Redis 없음), `RedisChatMemoryRepository`를 직접 구현했다.

```mermaid
flowchart TB
    subgraph Redis["Redis"]
        L["dstone:ai:session:{conversationId}<br/>(List, 메시지를 JSON으로 순서대로 저장)"]
        S["dstone:ai:session:index<br/>(Set, 존재하는 conversationId 목록)"]
    end
    App["net.dstone.ai.session.RedisChatMemoryRepository"] -->|findByConversationId| L
    App -->|saveAll| L
    App -->|findConversationIds| S
```

| 설정 | 기본값 | 설명 |
|---|---:|---|
| `dstone.ai.session.max-messages` | 20 | 세션당 유지할 최대 메시지 수 (초과 시 오래된 것부터 잘림) |
| `dstone.ai.session.ttl-seconds` | 86400 (1일) | Redis에 저장된 세션 히스토리 만료 시간 |

`dstone-boot`의 `dstone:session`(HTTP 세션) 네임스페이스와 겹치지 않도록 `dstone:ai:session:*`으로 분리했다.

### 5.4 Prompt — 템플릿 버저닝

```
src/main/resources/prompts/
└── {템플릿명}/
    ├── v1.st
    └── v2.st   (SI 프로젝트별로 파일만 추가/교체하면 커스터마이징 끝 - 코드 변경 불필요)
```

```yaml
dstone:
  ai:
    prompt:
      default-version: v1
      versions:
        sample-system: v2   # 템플릿명별 override, 없으면 default-version 사용
```

---

## 6. Phase 2 — RAG (Retrieval-Augmented Generation)

### 6.1 RAG 파이프라인 한눈에 보기

```mermaid
flowchart LR
    subgraph Ingest["① 문서 적재 (rag.ingest)"]
        Doc[("문서<br/>PDF/DOCX/HTML/TXT...")] -->|Tika| Extract["원문 추출"]
        Extract -->|TokenTextSplitter| Chunk["청크 분할"]
    end
    subgraph Embed["② 임베딩 (rag.embedding)"]
        Model{{"Ollama bge-m3<br/>(1024차원)"}}
    end
    subgraph Store["③ 저장"]
        PG[("PostgreSQL<br/>+ pgvector<br/>HNSW 인덱스")]
    end
    subgraph Retrieve["④ 검색 (rag.retrieval)"]
        Query(["사용자 질의"]) -->|임베딩| Model
        Model -->|코사인 유사도 검색| PG
        PG --> Result["관련 청크 Top-K"]
    end
    subgraph Chat["⑤ RAG-증강 채팅"]
        Result -->|QuestionAnswerAdvisor| LLM(["Claude"])
        LLM --> Answer(["최종 답변"])
    end

    Chunk -->|임베딩| Model
    Model -->|저장| PG
```

**실제로 검증된 e2e 흐름**: 문서 업로드 → pgvector 저장 확인 → 유사도 검색으로 관련 청크 반환 → `ragEnabled:true`로 채팅했을 때 Claude가 방금 넣은 문서 내용을 정확히 인용해 답변 — 전 과정이 정상 동작함을 실제로 확인했다.

### 6.2 문서 적재 (ingest)

`DocumentIngestService`가 담당한다.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Ctrl as RagController
    participant Ingest as DocumentIngestService
    participant Tika as TikaDocumentReader
    participant Splitter as TokenTextSplitter
    participant VS as VectorStore(pgvector)

    Client->>Ctrl: POST /api/ai/rag/documents<br/>(file, sourceId)
    Ctrl->>Ingest: ingest(resource, sourceId, metadata)
    Ingest->>Tika: get() - 원문 추출
    Tika-->>Ingest: List&lt;Document&gt;
    Ingest->>Splitter: apply() - 청크 분할
    Splitter-->>Ingest: List&lt;Document&gt; (청크들)
    Note over Ingest: 청크마다 metadata에<br/>sourceId 태깅
    Ingest->>VS: delete(filter: sourceId == ?)
    Note over Ingest,VS: 같은 sourceId 재적재 시<br/>upsert처럼 동작(기존 청크 선삭제)
    Ingest->>VS: add(tagged chunks)
    VS-->>Client: {"sourceId": "...", "chunkCount": N}
```

- **왜 upsert가 필요한가**: `sourceId`(호출 쪽이 부여하는 논리적 문서 식별자, 예: 파일명)로 재적재하면 오래된 청크를 먼저 지운 뒤 새로 넣는다 — 안 그러면 문서를 갱신할 때마다 오래된 청크가 검색 결과에 계속 섞여 나온다.
- **포맷 커버리지**: Apache Tika 하나로 PDF/DOCX/PPTX/HTML/TXT 등 대부분의 SI 문서 포맷을 커버한다.
- **빈 결과 안전장치**: 텍스트 추출/청킹 결과가 비어 있으면 기존 청크를 지우지 않고 그대로 종료한다(손상된 파일 재적재로 기존 정상 데이터가 날아가는 것을 방지).

### 6.3 임베딩 (embedding)

```mermaid
flowchart LR
    subgraph "선택 가능 provider"
        direction TB
        OpenAI["openai<br/>(API 키 필요, 유료)"]
        Ollama["ollama ✅ 현재 사용<br/>(로컬, 무료, bge-m3)"]
    end
    Anthropic["anthropic ❌<br/>임베딩 API 자체가 없음"]
    style Anthropic stroke-dasharray: 5 5
```

| 항목 | 값 |
|---|---|
| 활성 provider | `ollama` (`spring.ai.model.embedding: ollama`) |
| 모델 | `bge-m3` (다국어/한국어 지원, 1024차원) |
| 선택 이유 | Anthropic은 임베딩 API 미제공, 이 환경엔 OpenAI 키도 없어서 로컬 무료 모델 채택 |
| 검증 | `EmbeddingProperties`가 `GatewayProperties`와 동일한 철학으로 fail-fast 검증(단, `dstone.ai.rag.enabled=true`일 때만 빈이 존재 — RAG를 안 쓰는 배포에는 영향 없음) |

> 📌 **채팅 provider와 임베딩 provider는 완전히 독립적으로 선택된다.** 지금은 "채팅=Anthropic Claude + 임베딩=Ollama bge-m3" 조합이지만, `spring.ai.model.chat`/`spring.ai.model.embedding` 두 값을 각각 바꾸면 어떤 조합도 가능하다.

### 6.4 검색 (retrieval) 과 RAG-증강 채팅

`RetrievalService`가 `VectorStore.similaritySearch`를 감싸며, 독립 검색 API와 채팅 통합 양쪽에서 재사용된다.

```mermaid
flowchart TB
    RS[RetrievalService]
    RS -->|"POST /api/ai/rag/search"| Direct["단독 검색<br/>(디버깅/검증용)"]
    RS -->|"ChatController.ragEnabled=true"| QA["QuestionAnswerAdvisor로<br/>ChatClient에 자동 연결"]
    QA --> Advised["검색된 청크가<br/>시스템 프롬프트에 자동 삽입된 채<br/>Claude 호출"]
```

| 설정 | 기본값 | 비고 |
|---|---:|---|
| `dstone.ai.rag.retrieval.top-k` | 5 | 검색할 청크 수 |
| `dstone.ai.rag.retrieval.similarity-threshold` | **0.35** | ⚠️ 실측 기반 조정값(6.5절 하단 참고) |

> ⚠️ **실측으로 찾은 함정**: 처음엔 0.5로 뒀는데, `bge-m3` 기준으로는 진짜 관련 있는 문서/질의 쌍도 코사인 유사도가 0.48 정도밖에 안 나와서 **데이터가 정상 적재됐는데도 검색 결과가 통째로 비어버리는** 문제가 있었다. 임베딩 모델/도메인마다 유사도 분포가 다르므로 실제 서비스에서는 자신의 데이터로 임계값을 다시 측정/조정해야 한다.

### 6.5 on/off 스위치 하나로 켜고 끄기

```yaml
dstone:
  ai:
    rag:
      enabled: true   # false(또는 미설정)면 Postgres/pgvector/임베딩 설정 없이도 그대로 기동
```

`rag.ingest`/`rag.embedding`/`rag.retrieval`의 모든 빈과 `RagController`, `ChatController`의 `ragEnabled` 옵션은 `dstone.ai.rag.enabled=true`일 때만 활성화된다(`@ConditionalOnProperty`). **RAG가 필요 없는 SI 프로젝트는 이 값만 안 켜면 Phase 0/1까지의 기능만으로 완전히 정상 동작한다** — 이게 이 엔진을 "여러 프로젝트가 공유하는 플랫폼"으로 설계한 핵심 포인트다.

---

## 7. API 레퍼런스

### `POST /api/ai/chat` — 채팅 (일반 / RAG-증강)

<details>
<summary>요청/응답 예시 펼쳐보기</summary>

```bash
# 일반 채팅 (Phase 0/1)
curl -X POST http://localhost:8081/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
        "message": "안녕, 짧게 인사만 해줘.",
        "sessionId": null,
        "promptName": null,
        "variables": null,
        "ragEnabled": false
      }'
# → {"message":"안녕하세요! 😊","provider":"anthropic","sessionId":"3fde76a9-..."}
```

```bash
# RAG-증강 채팅 (Phase 2, dstone.ai.rag.enabled=true 필요)
curl -X POST http://localhost:8081/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "우리 RAG 파이프라인에서 임베딩 모델로 뭘 쓰는지 알려줘.", "ragEnabled": true}'
# → Claude가 pgvector에 저장된 관련 문서 조각을 인용해서 답변
```
</details>

| 필드 | 타입 | 설명 |
|---|---|---|
| `message` | String | 사용자 메시지 (필수) |
| `sessionId` | String | 비워두면 서버가 새로 발급 (응답의 `sessionId`로 이어서 사용) |
| `promptName` | String | 지정 시 `PromptTemplateRegistry`가 렌더링해 시스템 프롬프트로 사용 |
| `variables` | Map | `promptName` 템플릿 렌더링용 변수 |
| `ragEnabled` | Boolean | `true`면 `QuestionAnswerAdvisor`로 검색 결과 자동 삽입 (RAG 꺼져 있으면 `IllegalStateException`) |

### `POST /api/ai/rag/documents` — 문서 적재 (Phase 2, RAG 활성 시)

```bash
curl -X POST http://localhost:8081/api/ai/rag/documents \
  -F "file=@spec.pdf" -F "sourceId=spec-v1"
# → {"sourceId":"spec-v1","chunkCount":12}
```

### `DELETE /api/ai/rag/documents/{sourceId}` — 문서 삭제

```bash
curl -X DELETE http://localhost:8081/api/ai/rag/documents/spec-v1
```

### `POST /api/ai/rag/search` — 단독 유사도 검색 (디버깅/검증용)

```bash
curl -X POST http://localhost:8081/api/ai/rag/search \
  -H "Content-Type: application/json" \
  -d '{"query": "환불 정책이 어떻게 되나요?", "topK": 3, "similarityThreshold": null, "sourceId": null}'
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `query` | String | 검색 질의 (필수) |
| `topK` | Integer | 비우면 `dstone.ai.rag.retrieval.top-k` 기본값 |
| `similarityThreshold` | Double | 비우면 `dstone.ai.rag.retrieval.similarity-threshold` 기본값 |
| `sourceId` | String | 지정 시 그 문서로 적재된 청크로만 검색 범위 제한 |

응답: `[{"text": "...", "metadata": {...}, "score": 0.48}, ...]`

---

## 8. 설정 레퍼런스 (`conf/application.yml`)

<details>
<summary>전체 설정 트리 펼쳐보기 (실제 값은 예시로 마스킹)</summary>

```yaml
spring:
  data:
    redis:
      enabled: true
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
  datasource:                                    # RAG(Phase 2) 전용, pgvector가 사용
    driver-class-name: net.sf.log4jdbc.sql.jdbcapi.DriverSpy
    url: jdbc:log4jdbc:postgresql://${DB_HOST}:${DB_PORT}/dstone_ai
    username: dstone_ai
    password: ENC(...)
  servlet:
    multipart:
      max-file-size: 20MB                        # 문서 업로드 대비 상향
      max-request-size: 20MB
  ai:
    model:
      chat: anthropic                            # anthropic | openai | ollama
      embedding: ollama                          # openai | ollama (RAG용, anthropic 불가)
    anthropic:
      api-key: ENC(...)
    ollama:
      base-url: http://localhost:11434
      embedding:
        model: bge-m3
    vectorstore:
      pgvector:
        initialize-schema: true
        dimensions: 1024                         # 임베딩 모델 출력 차원과 반드시 일치

dstone:
  ai:
    session:
      max-messages: 20
      ttl-seconds: 86400
    prompt:
      default-version: v1
    rag:
      enabled: true                              # false면 RAG 관련 빈 전부 비활성화
      ingest:
        chunk-size: 800
      retrieval:
        top-k: 5
        similarity-threshold: 0.35
```
</details>

---

## 9. 필요 인프라

| 인프라 | 용도 | 필수 여부 |
|---|---|:---:|
| Anthropic API (또는 다른 LLM provider) | Chat 모델 추론 | ✅ 항상 |
| Redis | 대화 세션 히스토리 | ✅ 항상 (Phase 1부터) |
| PostgreSQL + pgvector | RAG 벡터 저장소 | `dstone.ai.rag.enabled=true`일 때만 |
| Ollama (또는 OpenAI) | RAG 임베딩 모델 추론 | `dstone.ai.rag.enabled=true`일 때만 |

설치 방법은 각각 [software/redis.md](software/redis.md), [software/postgresql.md](software/postgresql.md), [software/ollama.md](software/ollama.md) 참고. 부가 관리 도구로 [Ollama Web UI Lite](software/ollama-webui-lite.md)도 함께 운용 중이다.

---

## 10. 빌드 및 실행

```bash
# dstone-common을 먼저 설치해야 함(다른 모듈과 동일)
cd dstone-common && mvn clean install

# 빌드
cd dstone-ai-engine && mvn clean package

# 로컬(WSL) 실행 - env-wsl.properties 프로파일
java -Dspring.profiles.active=wsl -jar target/dstone-ai-engine.jar
```

기동 로그에서 아래 두 줄이 보이면 gateway/RAG 검증이 정상 통과한 것이다:
```
dstone-ai-engine gateway: 활성 LLM provider = ANTHROPIC
dstone-ai-engine rag: 활성 임베딩 provider = OLLAMA
```

> 컨테이너 빌드/kind 배포 절차는 `dstone-boot`과 동일한 패턴을 따른다 — [build.md 6절](build.md#6-dstone-boot--dstone-ai-engine--컨테이너-빌드--kind-배포) 참고.

---

## 11. 문제 해결 (실제로 겪은 에러 모음)

Phase 2를 실제로 붙이고 e2e 테스트하는 과정에서 겪은 진짜 에러들이다 — 같은 삽질을 반복하지 않도록 원인/조치를 남긴다.

| 증상 | 원인 | 조치 |
|---|---|---|
| 문서 적재 시 `NoClassDefFoundError: ChecksumInputStream` | `dstone-common`이 직접 고정한 `commons-io 2.15.1`이 Maven "nearest wins"로 이겨서, `spring-ai-tika-document-reader`(→ commons-compress 1.28.0)가 필요로 하는 `commons-io 2.16.0+`의 클래스가 없음 | `dstone-ai-engine/pom.xml`의 `dependencyManagement`에서 이 모듈만 `commons-io 2.20.0`으로 상향 |
| 검색 결과가 데이터 있는데도 항상 `[]` | `similarity-threshold` 기본값 0.5가 `bge-m3` 실측 유사도(0.48 등)보다 높음 | 기본값을 0.35로 하향(6.4절 참고) — 임베딩 모델/도메인별 재조정 필요 |
| `ERROR: extension "vector" is not available` | `postgresql`/`postgresql-18` 패키지만으로는 pgvector 확장이 없음 | `sudo apt-get install -y postgresql-18-pgvector` 후 `CREATE EXTENSION` 재시도 — 상세: [software/postgresql.md 7절](software/postgresql.md#7-문제-해결-phase-2-설치-중-실제로-겪은-에러) |
| Ollama Web UI Lite에서 채팅 시 `Uh-oh! There was an issue connecting to Ollama.` | ① Ollama가 IPv4 루프백에만 바인딩되어 브라우저의 IPv6(`::1`) 시도가 거부됨, ② 채팅 가능한 모델이 없이 임베딩 전용 `bge-m3`만 설치돼 있었음(둘 다 웹UI 자체 버그로 동일한 문구로만 표시됨) | ① `OLLAMA_HOST=[::]:11434`(dual-stack)로 바인딩 확장 ② 채팅용 모델(`llama3.2`) 별도 pull — 상세: [software/ollama.md 7절](software/ollama.md#7-문제-해결-ollama-web-ui-lite-연동-중-실제로-겪은-에러), [software/ollama-webui-lite.md 8절](software/ollama-webui-lite.md#8-문제-해결-실제로-겪은-에러) |

---

## 12. 다음 단계 (Phase 3~4)

| Phase | 패키지 | 계획 |
|---|---|---|
| 3 | `agent` | Tool/Function calling, 멀티스텝 오케스트레이션, 에이전트 메모리 |
| 4 | `governance` | Guardrail, PII 필터링, rate limiting, 비용 트래킹, 인증/인가(API 키 또는 OAuth2 client-credentials) |
| 4 | `observability` | 토큰 사용량/비용/트레이싱, Eval 결과 로깅 |

대량/오프라인 AI 작업(재임베딩, 주기적 Eval, 세션 정리)은 엔진 자체에 스케줄러를 두지 않고 기존 `dstone-batch`(`@AutoRegJob`) 패턴에 위임할 계획이다.

> 전체 모듈 빌드 명령, 배포 방식, CI/CD 파이프라인 종합 정리는 [build.md](build.md) 참고. 클라우드 아키텍처 시뮬레이션 관점의 배포 설계는 [cloud-architecture.md](cloud-architecture.md) 참고.
