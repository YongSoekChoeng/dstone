# dstone-ai-engine의 RAG 메카니즘 완전 정복

> 대상 패키지: `net.dstone.ai.common.rag`, `net.dstone.ai.api.service.EmbedService`, `net.dstone.ai.api.controller.EmbedController`,
> `net.dstone.ai.tools.rag.RagSearchTool`, `net.dstone.ai.runtime.agent.AgentExecutor`
> 위치: `dstone-ai-engine/src/main/java/net/dstone/ai/...`
>
> 이 문서는 dstone-ai-engine에 실제로 구현된 RAG(Retrieval-Augmented Generation) 구조를, 실제 소스코드
> (2026-09-20 기준, `RagRetrievalChain`에 Agent별 topK/threshold/allowEmptyContext override를 추가한
> 직후 상태)를 직접 읽고 정리한 자료입니다. 뒷부분은 이 구조에서 부족하다고 판단되는 지점을 짚습니다.

---

## 1. 한 줄 요약

dstone-ai-engine의 RAG는 **"적재"와 "검색-증강"을 완전히 분리된 두 책임**으로 나눠서 구현돼 있습니다.

- **적재(ingest)** — `EmbedController` → `EmbedService`: 문서를 임베딩으로 만들어 pgvector에 넣고 빼는 일만 한다.
- **검색-증강(retrieve & augment)** — `RagRetrievalChain`: 이미 적재된 임베딩을 "읽기"만 한다. Spring AI의
  RAG 조립 부품(`VectorStoreDocumentRetriever` + `ContextualQueryAugmenter` + `RetrievalAugmentationAdvisor`)을
  감싸는 단일 클래스이고, `AgentExecutor`(AGENT의 `ragEnabled` 경로)와 `RagSearchTool`(TOOL 경로) 둘 다
  이 클래스 하나만 거쳐서 "무엇을 검색 대상으로 볼지"를 정한다.

두 책임이 완전히 분리돼 있다는 점, 그리고 검색 로직이 단 한 곳(`RagRetrievalChain.buildRetriever()`)에만
있다는 점이 이 구조의 핵심 설계 의도입니다(클래스 상단 주석에 명시).

---

## 2. 전체 흐름 그림

```
[적재 흐름]
클라이언트 --(POST /api/ai/embed/documents, multipart file + sourceId)--> EmbedController
                                                                              │
                                                                              ▼
                                                                        EmbedService.ingest()
                                                                     .json/.jsonl?  ──yes──> readJsonlAsDocuments()
                                                                          │no                 (Oracle/PG 변환쌍 전용 포맷)
                                                                          ▼
                                                              Tika 추출 + TokenTextSplitter(800토큰)
                                                                          │
                                                                          ▼
                                                        청크마다 metadata(sourceId, tenant=caller) 태깅
                                                                          │
                                                                          ▼
                                              (upsert) 같은 sourceId 기존 청크 삭제 → vectorStore.add()
                                                                          │
                                                                          ▼
                                                                     pgvector: vector_store 테이블


[검색-증강 흐름 · 경로 A: AGENT의 ragEnabled]
AgentExecutor.buildSpec()
  ragEnabled=true → ragRetrievalChain.buildAdvisor(caller, agent.ragTopK(), agent.ragSimilarityThreshold(), agent.ragAllowEmptyContext())
                       │
                       ▼
            RetrievalAugmentationAdvisor  (spec.advisors(...)로 ChatClient 요청에 부착)
              ├─ VectorStoreDocumentRetriever (topK/threshold/tenant필터로 pgvector 검색)
              └─ ContextualQueryAugmenter     (검색 결과를 "{query}\n\n[참고자료]\n{context}"로 프롬프트에 삽입)
                       │
                       ▼
              LLM 호출 시 사용자 메시지에 [참고자료] 섹션이 자동으로 덧붙은 채 전달됨


[검색-증강 흐름 · 경로 B: TOOL의 RagSearchTool]
Workflow의 TOOL step 또는 Agent의 tool-calling
  → RagSearchTool.searchDocuments(query, topK)
       → ragRetrievalChain.search(new RagSearchRequest(query, topK, null, null), caller=null)
            → buildRetriever() 동일 로직 재사용, 단 tenant 필터 없이 전체 검색
       → 검색된 청크 텍스트를 "\n---\n"로 이어붙인 순수 문자열 반환 (LLM 호출 없음)
```

---

## 3. 등장인물

| 클래스 | 역할 |
|---|---|
| `api.controller.EmbedController` | `/api/ai/embed/documents` REST API(적재/삭제). 검색 기능은 없음 |
| `api.service.EmbedService` | 실제 적재 로직 - Tika 추출/청킹 또는 JSONL 특수 파싱, tenant/sourceId 태깅, upsert |
| `common.rag.RagRetrievalChain` | 검색-증강의 유일한 진입점. `buildAdvisor()`(AGENT용), `search()`(TOOL/API용) |
| `runtime.agent.AgentExecutor` | `AgentDefinition.ragEnabled`를 보고 `buildAdvisor()` 결과를 ChatClient에 advisor로 부착 |
| `tools.rag.RagSearchTool` | `@AiTool` - `RagRetrievalChain.search()`를 감싼 얇은 Tool, LLM 없이 순수 검색 |
| `common.definition.AgentDefinition` | YAML의 `ragEnabled`(+ 이번에 추가된 `ragTopK`/`ragSimilarityThreshold`/`ragAllowEmptyContext`) |
| `common.consts.Constants.Rag` | 메타데이터 key 상수(`sourceId`, `tenant`) - 적재/검색 양쪽이 공유 |

---

## 4. 적재(ingest) 상세 — `EmbedService`

### 4.1 API 계약

```
POST /api/ai/embed/documents   (multipart/form-data: file, sourceId)  → IngestResponse(sourceId, chunkCount)
DELETE /api/ai/embed/documents/{sourceId}
```

- 파일 업로드만 지원한다. 원문을 텍스트 그대로 body에 실어 적재하는 API는 없다.
- 폴더를 스캔해서 기동 시 자동으로 문서를 적재하는 기능도 없다 - 반드시 이 API를 호출해야 한다.
- `sourceId`는 필수다. 같은 `sourceId`로 다시 적재하면 **upsert**(기존 청크를 먼저 지우고 새로 넣음)로 동작한다
  (`EmbedService.java:96-125`). 단, 새로 추출한 청크가 0건이면 기존 청크를 지우지 않는다 - 손상된 파일을
  잘못 재적재했을 때 기존 정상 데이터까지 날리는 사고를 막기 위한 방어 코드다(`:116-119`).

### 4.2 파일 형식별 분기

`EmbedService.ingest()`는 파일 확장자로 두 경로 중 하나를 탄다(`:102-103`, `isJsonlSource()`).

- **`.json`/`.jsonl`** → `readJsonlAsDocuments()`(`:152-190`): 한 줄 = `{instruction, input, output, notes}`
  JSON 객체 하나로 보고, 청킹 없이 그대로 아래 포맷으로 변환해서 청크 1개로 저장한다.

  ```
  [오라클 쿼리]
  {input}

  [PostgreSQL 변환 결과]
  {output}

  [설명/주의사항]
  {notes 또는 "-"}
  ```

  이 포맷은 **Oracle→PostgreSQL SQL 변환 사례를 지식베이스로 태우는 용도로 전용 설계**된 것이다. 메타데이터로
  `instruction`, `has_notes`도 함께 저장한다.

- **그 외 전부**(PDF/DOCX/TXT 등) → `splitGenericDocument()`(`:145-150`): `TikaDocumentReader`로 텍스트 추출 →
  `TokenTextSplitter`(청크 크기 `dstone.ai.rag.ingest.chunk-size`, 기본 800 토큰)로 청킹. Overlap 등 다른
  파라미터는 건드리지 않고 라이브러리 기본값 그대로 쓴다.

### 4.3 메타데이터 태깅과 격리

청크마다 `sourceId`는 항상, `tenant`(=caller)는 caller가 있을 때만 붙인다(`:105-112`). caller는
`CallerContext.get(servletRequest)`(`EmbedController.java:40`)로 구한다 - `dstone.ai.security.auth.enabled=false`면
항상 `null`이라 tenant 태깅 자체가 안 된다.

---

## 5. 검색-증강(retrieve & augment) 상세 — `RagRetrievalChain`

### 5.1 공통 게이트: `requireVectorStore()`

```java
if (!Boolean.parseBoolean(configProperty.getProperty("dstone.ai.rag.enabled"))) throw ...;
VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
if (vectorStore == null) throw ...;
```

`VectorStore` 빈은 `dstone.ai.rag.enabled=true`이고 `spring.ai.model.embedding`이 올바를 때만 Spring AI가
자동 생성해준다(아예 안 뜰 수 있음). 그래서 이 클래스는 **항상 컴포넌트로 등록**되지만, "RAG를 실제로 쓸 수
있는가"는 메서드 호출 시점에만 판단한다 - `ObjectProvider<VectorStore>`로 감싸서 빈이 없어도 기동은 깨지지
않게 한 설계다.

### 5.2 검색 파라미터가 결정되는 단 하나의 지점: `buildRetriever()`

```java
private VectorStoreDocumentRetriever buildRetriever(Integer topK, Double similarityThreshold, String caller, String sourceId) {
    VectorStore vectorStore = requireVectorStore();
    var builder = VectorStoreDocumentRetriever.builder()
        .vectorStore(vectorStore)
        .topK(topK == null ? defaultTopK() : topK)
        .similarityThreshold(similarityThreshold == null ? defaultSimilarityThreshold() : similarityThreshold);
    Filter.Expression filter = buildFilter(caller, sourceId);
    if (filter != null) builder.filterExpression(filter);
    return builder.build();
}
```

`buildAdvisor()`와 `search()` 둘 다 결국 이 메서드를 거치므로, topK/threshold/tenant필터의 의미가 두 경로에서
어긋날 여지가 구조적으로 없다.

- `defaultTopK()` = `dstone.ai.rag.retrieval.top-k` (미설정 시 5)
- `defaultSimilarityThreshold()` = `dstone.ai.rag.retrieval.similarity-threshold` (미설정 시 0.35) - 코드
  주석에 "bge-m3(로컬 임베딩) 기준 실측: 실제 관련 있는 문서/질의 쌍도 코사인 유사도가 0.5를 못 넘는 경우가
  흔해서(0.48 등) 0.5를 기본값으로 두면 데이터가 있어도 결과가 통째로 빈다"고 남겨져 있다 - 경험적으로
  낮춘 값이다.
- `buildFilter(caller, sourceId)`: `tenant = caller` AND/OR `sourceId = sourceId` 조건만 지원한다. 둘 다
  없으면 필터 없음(전체 검색).

### 5.3 경로 A — `buildAdvisor()` (AGENT의 `ragEnabled`)

```java
public Advisor buildAdvisor(String caller) {                       // 기본 진입점(하위호환) - 전부 null로 위임
    return buildAdvisor(caller, null, null, null);
}
public Advisor buildAdvisor(String caller, Integer topK, Double similarityThreshold, Boolean allowEmptyContext) {
    VectorStoreDocumentRetriever retriever = buildRetriever(topK, similarityThreshold, caller, null);
    ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
        .promptTemplate(CONTEXT_PROMPT_TEMPLATE)
        .allowEmptyContext(allowEmptyContext == null ? true : allowEmptyContext)
        .build();
    return RetrievalAugmentationAdvisor.builder()
        .documentRetriever(retriever).queryAugmenter(queryAugmenter).build();
}
```

- `CONTEXT_PROMPT_TEMPLATE`은 `"{query}\n\n[참고자료]\n{context}"` 고정 상수. Spring AI 기본 템플릿과 달리
  "컨텍스트에 없으면 모른다고 답하라"는 강제 문구가 없다 - 이 프로젝트의 `ragEnabled` Agent들은 system prompt에
  이미 업무 지식/규칙을 전부 갖고 있고 RAG는 보조 자료일 뿐이라는 전제다.
- **`allowEmptyContext`**: `true`(기본값)면 검색 결과가 0건이어도 원래 질의 그대로 진행한다(빈 `[참고자료]`
  섹션만 덧붙는 정도). `false`를 넘기면 Spring AI 원래 동작(근거 없으면 "모른다"고 답하도록 강제)으로 돌아간다.
- **`buildFilter`에 `sourceId`는 항상 `null`로 고정**돼 있다 - AGENT 경로는 caller(tenant) 전체 범위에서만
  검색하고, 특정 문서로 좁혀서 검색할 방법이 없다(§7.3 참고).
- `AgentExecutor.buildSpec()`(4단계, `AgentExecutor.java:150-156`)이 `agent.ragTopK()/ragSimilarityThreshold()/
  ragAllowEmptyContext()`를 그대로 이 메서드에 전달한다 - Agent YAML에 값이 없으면(`null`) 전역 기본값을 쓰고,
  값을 채우면 그 Agent만 다른 검색 범위/threshold/빈컨텍스트 처리를 가져갈 수 있다(2026-09-20 확장분).

### 5.4 경로 B — `search()` (TOOL·API용)

```java
public List<RetrievedChunk> search(RagSearchRequest request, String caller) {
    VectorStoreDocumentRetriever retriever = buildRetriever(request.topK(), request.similarityThreshold(), caller, request.sourceId());
    ...
    return retrieved; // List<RetrievedChunk(text, metadata, score)>
}
```

`RagSearchRequest(query, topK, similarityThreshold, sourceId)`로 호출마다 topK/threshold/sourceId를 자유롭게
override할 수 있다(전부 nullable). LLM을 부르지 않고 순수 검색 결과(청크 리스트)만 돌려준다.

이 경로의 유일한 소비자는 **`tools.rag.RagSearchTool`**(`@AiTool searchDocuments(query, topK)`)이고, 여기서
**`caller`를 항상 `null`로 호출한다**(`RagSearchTool.java:39`). 클래스 주석에 이 한계가 명시돼 있다 - "Tool
호출 경로에 caller가 전달되지 않으므로, 이 검색은 tenant 격리 없이 전체 문서를 대상으로 한다."

---

## 6. Agent 쪽 소비 지점 — `AgentExecutor.buildSpec()`

```java
boolean ragEnabled = ragOverride != null ? ragOverride : agent.ragEnabled();
...
if (ragEnabled) {
    spec = spec.advisors(this.ragRetrievalChain.buildAdvisor(
        caller, agent.ragTopK(), agent.ragSimilarityThreshold(), agent.ragAllowEmptyContext()));
}
```

- `ChatController`(`POST /api/ai/chat`)는 `ChatRequest.ragEnabled()`로 **요청 1회에 한해** Agent 정의값을
  무시하고 RAG를 강제로 켜거나 끌 수 있다. 단 topK/threshold/allowEmptyContext는 요청 단위로 override할 방법이
  없다 - Agent YAML 값이 그대로 적용된다.
- Workflow의 AGENT/SUPERVISOR step(`AgentStepRunner`)은 `ragOverride`를 항상 `null`로 넘긴다 - Workflow
  안에서는 Agent 정의값을 그대로 쓴다(`AgentStepRunner.java:44-49` 주석에 명시).

---

## 7. 격리(tenant) 모델

| 구분 | caller 전달 여부 | tenant 필터 |
|---|---|---|
| 적재 (`EmbedService.ingest`) | `CallerContext.get(servletRequest)` | 청크에 `tenant` metadata 태깅 |
| 검색 - AGENT 경로 (`buildAdvisor`) | `AgentExecutor`가 caller를 알고 있어 전달 | **적용됨** |
| 검색 - TOOL 경로 (`RagSearchTool`) | 항상 `null` | **적용 안 됨**(전체 검색) |

`dstone.ai.security.auth.enabled=false`(현재 기본값)면 `caller`가 항상 `null`이라 애초에 tenant 격리 자체가
의미 없는 no-op 상태다. `security.auth`를 켜서 caller가 실제로 채워지는 순간부터 위 표의 비대칭(AGENT는 격리,
TOOL은 미격리)이 실질적인 데이터 유출 경로가 될 수 있다.

---

## 8. 설정값 정리 (`conf/application.yml`)

```yaml
spring:
  ai:
    model:
      embedding: ollama            # anthropic은 임베딩 미지원 - chat provider와 별개로 설정
    ollama:
      embedding:
        model: bge-m3               # 로컬, 1024차원, 다국어/한국어 지원
    vectorstore:
      pgvector:
        table-name: vector_store
        dimensions: 1024             # bge-m3 출력 차원과 반드시 일치
        index-type: HNSW
        distance-type: COSINE_DISTANCE
dstone:
  ai:
    rag:
      enabled: true                 # 전체 게이트 - false면 EmbedService/RagRetrievalChain 둘 다 호출 시 예외
      ingest:
        chunk-size: 800              # 청크당 최대 토큰 수 (TokenTextSplitter)
      retrieval:
        top-k: 5
        similarity-threshold: 0.35   # bge-m3 실측 기반으로 낮춰놓은 전역 기본값
```

---

## 9. 실제 사용 현황 (2026-09-20 기준)

| Agent | `ragEnabled` | 비고 |
|---|---|---|
| `sql-analysis-agent` | **true** | oracle-to-postgresql Workflow의 `analyze` step. 실제 검색은 수행되나 지식베이스가 비어 있어(§10) 항상 빈 컨텍스트로 진행 |
| `sql-conversion-agent` | false | `convert` step. `toolsEnabled: true`라 `RagSearchTool`을 tool-calling으로 호출할 **수는** 있지만, 프롬프트에 `[참고자료]` 섹션을 처리하라는 지시문(`sql-conversion-agent.yml:11-15`)이 있음에도 `ragEnabled=false`라 `buildAdvisor()` 경로로는 절대 채워지지 않는다 - Advisor 자동 증강은 죽어 있고, Agent가 스스로 `searchDocuments` Tool을 부르지 않는 한 `[참고자료]`는 항상 빈 채로 남는다 |
| `sql-fix-agent`, `sql-conversion-report`, `general-chat` | false | RAG 미사용 |

## 10. 실제 데이터 현황

`vector_store`에 지금까지 실제로 적재된 문서는 라이브 검증(§13, `docs/09.dstone-ai-engine.md`) 때 넣은
스모크테스트용 문서 1건뿐이다(`sourceId=rag-live-test`, 내용: "dstone-ai-engine의 마스코트 이름은
'스톤이'..." 수준의 무관한 문장). **Oracle→PostgreSQL 변환 지식베이스는 한 번도 시딩된 적이 없다** - §4.2에서
설명한 JSONL 전용 포맷(`readJsonlAsDocuments`)이 구현돼 있음에도 실제 사용 사례가 없는 상태다.

---

## 11. 부족한 부분

1. **검색 미리보기/조회 API가 없다.** `RagRetrievalChain.search()`는 존재하지만 이를 노출하는 REST 엔드포인트가
   없다(`EmbedController`엔 적재/삭제만 있음). "이 문서를 넣으면 실제로 검색이 되는가"를 확인하려면
   `RagSearchTool`을 Agent를 통해서만 간접 호출해야 한다 - RAG 콘텐츠 운영/튜닝을 어렵게 만드는 구조적 공백이다.
2. **적재된 문서 목록 조회 API가 없다.** `sourceId` 기준 삭제는 되는데, 지금 무엇이(어떤 `sourceId`들이)
   적재돼 있는지 조회하는 API가 없다 - 확인하려면 pgvector 테이블을 직접 쿼리해야 한다.
3. **`RagSearchTool` 경로는 tenant 격리가 안 된다.** §7에서 설명한 비대칭 - `security.auth`를 켜는 순간
   AGENT 경로와 TOOL 경로의 데이터 노출 범위가 달라진다. Tool 호출 체인에 caller를 실어 보낼 방법(Spring AI의
   `ToolContext` 활용)이 필요하다(코드 주석에도 이미 알려진 한계로 명시돼 있다).
4. **필터가 tenant/sourceId 2개로 고정.** 문서 카테고리, 등록일, 부서 등 다른 메타데이터로 검색 범위를
   좁히고 싶어도 `buildFilter()` 시그니처 자체에 그 통로가 없다.
5. **재랭킹/하이브리드 검색이 없다.** 순수 코사인 유사도(`VectorStoreDocumentRetriever`) 하나뿐이고, BM25
   같은 키워드 검색과의 결합이나 별도 재랭킹 단계가 없다 - 임베딩이 놓치는 정확 매칭(테이블명, 함수명 등
   SQL 변환에서 특히 중요한 고유명사)에 약할 수 있다.
6. **청킹이 단순하다.** `TokenTextSplitter` 기본 파라미터(overlap 등 미조정)만 쓰고, 문서 구조(제목/섹션/표)를
   인식하는 청킹은 없다. 일반 문서(PDF/DOCX)에 특히 영향이 크다.
7. **JSONL 전용 포맷이 SQL 변환에 너무 특화돼 있다.** `readJsonlAsDocuments()`의 필드(`instruction/input/
   output/notes`)와 출력 라벨(`[오라클 쿼리]/[PostgreSQL 변환 결과]/[설명/주의사항]`)이 하드코딩돼 있어,
   이 엔진이 목표로 하는 "여러 SI 프로젝트 재사용"과 맞지 않는다 - 다른 도메인의 지식베이스를 JSONL로
   구조화해서 넣고 싶어도 이 포맷을 그대로 쓸 수 없고, 결국 일반 텍스트 경로(Tika+청킹)로 우회해야 한다.
8. **실제 지식베이스가 비어 있다.** §10에서 확인한 대로 Oracle→PostgreSQL 변환 사례가 한 건도 적재돼 있지
   않다 - 지금 상태로는 `sql-analysis-agent`의 RAG가 켜져 있어도 실질적인 효과가 없다. RAG를 "제대로" 쓰려면
   실제 변환 사례를 JSONL로 만들어 시딩하는 작업이 선행돼야 한다.
9. **Agent 설정과 프롬프트 지시문의 불일치.** `sql-conversion-agent`는 `ragEnabled=false`인데 프롬프트엔
   `[참고자료]` 처리 지시문이 그대로 남아 있다(§9) - 죽은 지시문이라 프롬프트 토큰만 낭비하고 있을 가능성이
   있다. `ragEnabled=true`로 켜거나, 지시문을 지우거나 둘 중 하나로 정리가 필요하다.
10. **문서 상 서술과 코드가 어긋나는 부분이 있다.** 루트 `CLAUDE.md`의 `dstone-ai-engine` 패키지 표는
    `api` 패키지에 `RagController`가 있다고 적혀 있으나 실제 컨트롤러는 `ChatController`/`EmbedController`/
    `WorkFlowController`/`WorkFlowExecutionController` 4개뿐이고 `RagController`는 존재하지 않는다. 또한
    `docs/09.dstone-ai-engine.md` §13(검증 상태)의 2026-09-18 기록은 "`convert` 스텝이 `ragEnabled: true`"
    라고 적혀 있는데 현재 `sql-conversion-agent.yml`은 `ragEnabled: false`다(git 이력상 그 사이 값이
    되돌아간 것으로 보인다) - 두 문서 모두 최신 코드 기준으로 갱신이 필요하다.
11. **프롬프트 템플릿이 여전히 전역 상수.** 2026-09-20에 topK/threshold/allowEmptyContext는 Agent별로
    override할 수 있게 됐지만(§5.3), `CONTEXT_PROMPT_TEMPLATE`(`[참고자료]` 라벨, 한국어 고정)은 여전히
    `RagRetrievalChain`의 `static final` 상수라 모든 RAG Agent가 공유한다 - 다국어 SI 프로젝트나 다른 라벨
    체계가 필요한 경우 이 클래스를 직접 고쳐야 한다.

---

## 12. 요약 결론

아키텍처(적재/검색 책임 분리, 검색 파라미터를 단일 지점에서 결정, AGENT·TOOL 두 경로가 같은 코드를 공유)는
잘 짜여 있고, Agent별 topK/threshold/allowEmptyContext override까지 갖춰 RAG를 쓰는 Agent가 늘어나도 확장이
가능한 상태입니다. 다만 **실제로 이 구조를 "제대로" 쓰려면** (a) tenant 격리를 TOOL 경로까지 일관되게
맞추고, (b) 검색 미리보기/목록 조회 같은 운영 편의 API를 보강하고, (c) 무엇보다 **실제 Oracle→PostgreSQL
변환 지식베이스를 JSONL로 만들어 시딩**하는 작업이 선행되어야 합니다 - 지금은 파이프라인만 검증됐을 뿐
그 파이프라인에 태울 실제 데이터가 없는 상태입니다.
