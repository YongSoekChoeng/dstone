# 지금_dstoneaiengine_에서

> 생성일시: 2026-09-13 10:07

## 질문

지금 dstone-ai-engine 에서 LLM을 anthropic 을 사용하지 않고 ollama로 사용하려 한다면 아래 부분만 변경하면되는게 아닌가?
<application.yml>
spring.ai.model.chat: ollama

---

## 답변

dstone-ai-engine 코드 상태를 먼저 확인해보겠습니다.

---

## 답변

확인해봤습니다. 결론부터 말하면 **아니요, 그 한 줄만으로는 안 됩니다.**

## Java 코드는 손댈 필요 없음 (맞는 부분)

이미 Phase 1 게이트웨이 패턴이 코드에 구현돼 있어서:
- `pom.xml`에 anthropic/openai/ollama 스타터가 전부 이미 포함돼 있고 (`dstone-ai-engine/pom.xml:88-99`)
- `AiProvider` enum에 `OLLAMA`가 이미 있고 (`common/consts/AiProvider.java:26`)
- `ConfigChatClient`는 provider 무관하게 `ChatClient.Builder`만 주입받아 build()만 함 (`config/ConfigChatClient.java:16`)

→ `spring.ai.model.chat` 값만 바꾸면 실제로 활성화되는 provider의 자동설정이 통째로 바뀌는 구조가 맞습니다.

## 하지만 application.yml에서 추가로 손볼 게 있습니다

1. **YAML 키 중복 문제** — `application.yml:112-116`에 주석 처리된 `# ollama:` chat 설정 블록이 있는데, 이걸 그대로 주석 해제하면 **123번째 줄에 이미 있는 임베딩용 `ollama:` 블록과 같은 레벨에 키가 두 번** 생깁니다. YAML은 같은 레벨 중복 키를 뒤엣것이 덮어써버려서, 임베딩(`bge-m3`) 설정이 날아갈 수 있습니다. 반드시 하나의 `ollama:` 블록으로 합쳐서 `chat.options.model`을 추가해야 합니다:
```yaml
ollama:
    base-url: http://localhost:11434
    chat:
        options:
            model: llama3.1   # 로컬에 pull된 채팅 모델
    embedding:
        model: bge-m3
```

2. **chat 모델 지정 필수** — 로컬 Ollama엔 지금 임베딩 전용 `bge-m3`만 설치돼 있습니다(`docs/software/14.ollama.md`). 이건 채팅을 못 하는 모델이라, `chat.options.model`을 비워두면 Spring AI 기본값(대략 mistral)으로 요청했다가 로컬에 없어서 실패합니다. `ollama pull llama3.1` 등으로 채팅 가능한 모델을 별도로 받아야 합니다.

## 참고로 발견한 별개 이슈

`ChatRequest.java:21-33` 주석에는 요청 단위로 `provider`/`ollamaModel`을 지정해 기본 provider와 별개로 라우팅하는 기능(`ConfigOllamaOverride`, `dstone.ai.gateway.ollama-override.*`)이 설명돼 있는데, 실제로는 `ConfigOllamaOverride` 클래스 자체가 존재하지 않고 `ChatService.getSpec()`도 이 필드들을 전혀 참조하지 않습니다 — 즉 지금은 문서만 있고 미구현 상태입니다. 요청에 `provider`를 넣어도 무시되고, 여전히 `spring.ai.model.chat` 전역 설정 하나로만 라우팅됩니다. (별도 작업 필요 여부는 말씀 주시면 확인하겠습니다.)

---

## 질문

ollama 는 응답할 때 띄어쓰기가 안되나?
이 ai모델이 어느정도 스마트하게 응답처리를 할 수 있는지 궁금해.
ollama를 조금 더 똑똑하게 할 수 있는 방법이 있을까?

---

## 답변

llama3.2를 실제로 호출해서 확인해봤습니다.

---

## 답변

실제 테스트로 재현됐습니다. 짧은 질문엔 멀쩡하지만,

```
"저는 지능형 아이프onia 인입니다... 다양한 도메인에서 자격이 있습니다...
altri 언어를 포함합니다."
```

자유 응답에서는 "아이프onia"(한국어+영어가 공백 없이 붙음), "altri"(이탈리아어 단어가 갑자기 튐) 같은 깨짐이 나옵니다. 이건 버그가 아니라 **모델 자체의 한계**입니다 — 지금 붙어있는 `llama3.2:latest`는 3.2B 파라미터짜리 소형 모델이고(Q4_K_M 양자화), 학습 데이터가 영어 중심이라 한국어 토큰 경계 처리가 약합니다. Claude/GPT급 모델은 파라미터 규모와 다국어 학습량 자체가 다르니, 지금 환경(WSL, CPU 전용, RAM 8GB, GPU 없음)에서 로컬로 그 수준을 기대하긴 어렵습니다.

**똑똑하게 만드는 현실적인 방법 (자원 제약 고려)**

| 방법 | 효과 | 트레이드오프 |
|---|---|---|
| 모델 교체 (`ollama pull exaone3.5:2.4b` 또는 `qwen2.5:3b`) | 비슷한 크기에서 한국어 품질 크게 개선 (EXAONE은 LG가 한국어 특화 학습) | 다운로드/디스크 필요, 여전히 소형 모델의 reasoning 한계는 있음 |
| 시스템 프롬프트에 "한국어로만, 정확한 띄어쓰기로" 명시 (`dstone.ai.prompt`) | 즉시 적용 가능, 실제로 완화 효과 있음 | 근본 해결은 아님 |
| `temperature` 낮추기 (`spring.ai.ollama.chat.options.temperature`) | 헛소리/언어 뒤섞임 확률 감소 | 창의성/다양성 감소 |
| RAG로 사실 근거 제공 (이미 `dstone.ai.rag.enabled=true`) | 사실 기반 답변 품질 개선 | 언어 유창성 자체는 안 고쳐짐 |
| 더 큰 모델(7B~14B) | 확실히 똑똑해짐 | 지금 RAM 8GB/GPU 없는 환경에선 체감 속도 크게 느려질 가능성 높음 |

가장 먼저 시도해볼 만한 건 **모델 교체**입니다. 지금 크기 그대로 한국어 품질만 올릴 수 있어서 비용이 가장 낮습니다. `ollama pull exaone3.5:2.4b` 해보고 `spring.ai.ollama.chat.options.model`을 그걸로 바꿔서 비교해볼까요?

---

## 질문

/app/dstone/docs 의 모든 md 파일들을 갱신해줘.
- 현재까지 진행된 모든 추가/수정 작업에 대해서 유관한 md 파일이 존재하지 않을 경우 신규 작성.
- 현재까지 진행된 모든 추가/수정 작업에 대해서 유관한 md 파일들이 존재하면 내용을 수정.
- 내용은 최대한 친절하고 풍부하고 비주얼적으로 풍성하게 표현한다.
- /app/dstone 소스 내에 불필요하거나 구현내용과 동일하지 않은 주석이 있을 경우 주석을 바로잡아줘.

---

## 답변

범위가 `/app/dstone/docs` 전체(26개 md 파일)와 5개 모듈 소스 전체의 주석 점검이라 상당히 큽니다. 모듈별로 나눠 병렬로 감사(실제 코드 vs 문서 대조)한 뒤 직접 갱신하도록 7개로 나눠 진행하겠습니다.

---

## 답변

7개 모듈/영역별로 나눠서 백그라운드에서 문서 감사·갱신 및 주석 정리 작업을 병렬로 시작했습니다:

1. dstone-ai-engine → `docs/09.dstone-ai-engine.md`
2. dstone-common → `docs/05.dstone-common.md`
3. dstone-boot + SAGA → `docs/06.dstone-boot.md`, `docs/10.dstone-saga.md`
4. dstone-batch → `docs/07.dstone-batch.md`
5. dstone-batchadmin → `docs/08.dstone-batchadmin.md`
6. 빌드/CI-CD/클라우드 아키텍처 → `docs/03.build.md`, `docs/04.cloud-architecture.md`
7. 환경/인덱스/소프트웨어 설치 가이드 → `docs/01.index.md`, `docs/README.md`, `docs/02.environment.md`, `docs/software/*.md`(15개)

각 에이전트가 실제 소스 코드를 읽고 문서와 대조해 갱신하고, 담당 모듈의 소스 주석 중 실제 구현과 다르거나 불필요한 것만 바로잡습니다. 완료되는 대로 결과를 모아서 정리해 알려드리겠습니다.

---

## 질문

<task-notification>
<task-id>af7067bf4a1d5dd7e</task-id>
<tool-use-id>toolu_01Kg3eump3toeDRCE7ZqdnTq</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/7acaa229-0ae5-4fbd-9d28-0a284f158bb2/tasks/af7067bf4a1d5dd7e.output</output-file>
<status>completed</status>
<summary>Agent "Audit build and cloud-architecture docs" finished</summary>
<note>A task-notification fires each time this agent stops with no live background children of its own. The user can send it another message and resume it, so the same task-id may notify more than once.</note>
<result>docs/03.build.md와 docs/04.cloud-architecture.md 문서 갱신 완료.

**수정한 파일**
- `docs/03.build.md`
- `docs/04.cloud-architecture.md`

**확인 결과 (대체로 정확했음)**
- 두 문서 모두 이미 매우 상세하고 실제 Dockerfile/Jenkinsfile/bin 스크립트/k8s manifest와 정확히 일치했음(포트, namespace `dstone`, registry `localhost:5000`, `DSTONE_PROFILE=vm`, 4개 Jenkinsfile 전부 대조 확인). `docs/images/`의 SVG 3종(jenkins-job-setup-flow, jenkins-boot-pipeline-flow, jenkins-batch-pipeline-flow)도 실제 존재하고 04.cloud-architecture.md에서 정확히 참조 중.

**실제로 바뀐 핵심 내용**
- 유일하게 발견한 실질적 드리프트: `dstone-ai-engine/k8s/configmap.yaml`이 Phase 0 시점 스냅샷 그대로 방치돼 있고, 실제 `dstone-ai-engine/conf/application.yml`(Phase 1~4까지 진행)과 크게 어긋남 — `spring.ai.model.chat` 값 누락(현재 상태로 배포하면 기동 실패), `RedisAutoConfiguration` exclude로 세션 기능이 깨지는 상태, `autoconfigure.exclude` 경로가 Boot 4 이전 패키지, PostgreSQL/RAG/세션/프롬프트/거버넌스/관측 설정 통째로 누락.
- 두 문서 모두에 이 내용을 구체적 체크리스트로 추가(`03.build.md` 6절, `04.cloud-architecture.md` 7절), 서로 교차 링크 처리.
- Dockerfile/Jenkinsfile/bin 스크립트 자체의 주석은 실제 동작과 전부 일치해 수정 불필요.</result>
<usage><subagent_tokens>171877</subagent_tokens><tool_uses>15</tool_uses><duration_ms>93661</duration_ms></usage>
</task-notification>

---

## 답변

빌드/클라우드 아키텍처 문서 작업이 끝났습니다 (7개 중 1개). 그 과정에서 실제 배포 이슈 하나를 발견했습니다: `dstone-ai-engine/k8s/configmap.yaml`이 Phase 0 시점 스냅샷 그대로 방치돼 있어서, 실제 `application.yml`(Phase 1~4까지 진행된 내용)과 어긋나 있습니다 — 지금 그대로 배포하면 `spring.ai.model.chat` 누락 등으로 기동 실패할 수 있습니다. 일단 문서(`03.build.md`, `04.cloud-architecture.md`)에 체크리스트로 남겨뒀고, 나머지 6개 작업 마저 기다리는 중입니다.

---

## 질문

<task-notification>
<task-id>a3c0ed15a8f47fb52</task-id>
<tool-use-id>toolu_01B8txuJvShDNJAn9w9RDTCp</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/7acaa229-0ae5-4fbd-9d28-0a284f158bb2/tasks/a3c0ed15a8f47fb52.output</output-file>
<status>completed</status>
<summary>Agent "Audit and update dstone-batchadmin docs" finished</summary>
<note>A task-notification fires each time this agent stops with no live background children of its own. The user can send it another message and resume it, so the same task-id may notify more than once.</note>
<result>작업 완료했습니다.

**(a) 수정한 파일**
- `docs/08.dstone-batchadmin.md`
- `dstone-batchadmin/src/main/java/net/dstone/batchadmin/DstoneBatchAdminApplication.java`

**(b) 문서에서 실제로 바뀐 핵심 내용**
- 프로젝트 구조 트리의 오류 수정: `web/` 패키지가 "BaseController 등 공통 웹 처리"로 잘못 설명돼 있었음 → 실제로는 `SessionListener`(세션/중복로그인 추적)만 있고, `BaseController`는 `biz/` 패키지에 있음(코드 확인 완료).
- 4장 도입부에 두 연동 경로(직접 DB 조회 vs REST 제어)를 한눈에 보여주는 mermaid flowchart 추가.
- 4.5절(CRON 스케줄링)에 "저장 시점 즉시 반영" 동작과 "수동 시작/자동 스케줄이 결국 같은 경로를 탄다"는 설명 보강.
- 신규 4.6절 "데이터 모델 한눈에 보기" 추가 — `TB_ADMIN_USER`/`TB_BATCH_SERVER`/`TB_BATCH_JOB`/`TB_BATCH_JOB_PARAM` ER 다이어그램, `DBMS_TYPE`이 서버별로 MySQL/PostgreSQL을 독립 지정할 수 있다는 점(코드로 실제 구현 확인됨), 샘플 데이터에 `dstone-batch`의 11개 실제 샘플 잡이 미리 등록돼 있다는 점을 명시.
- 목차에 4.6 항목 추가.

**(c) 고친 주석**
- `DstoneBatchAdminApplication.java:102` — `// TODO: handle exception`(IDE 자동생성 플레이스홀더, 실제로는 아무 처리도 안 하고 의도적으로 무시하는 코드) → `// env 파일 로딩 실패는 기동을 막을 이유가 아니므로 무시하고 기본 System Properties로 계속 진행한다.`로 실제 동작을 설명하도록 수정.

**참고(범위 밖, 손대지 않음):** `dstone-batchadmin`의 나머지 소스(Controller/Service/Dao/VO, MyBatis XML)는 전수 검토했지만 주석이 실제 구현과 대체로 정확하게 일치했다 — 추가로 고칠 만한 항목은 없었다.</result>
<usage><subagent_tokens>176113</subagent_tokens><tool_uses>30</tool_uses><duration_ms>124238</duration_ms></usage>
</task-notification>

---

## 답변

dstone-batchadmin 문서 작업도 완료됐습니다 (2/7). 남은 5개 계속 진행 중입니다.

---

## 질문

<task-notification>
<task-id>a1e7ec9406ee7746d</task-id>
<tool-use-id>toolu_015EopgYtUsUcjr4AbDwd97u</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/7acaa229-0ae5-4fbd-9d28-0a284f158bb2/tasks/a1e7ec9406ee7746d.output</output-file>
<status>completed</status>
<summary>Agent "Audit and update dstone-boot and saga docs" finished</summary>
<note>A task-notification fires each time this agent stops with no live background children of its own. The user can send it another message and resume it, so the same task-id may notify more than once.</note>
<result>**작업 완료 보고**

**(a) 수정한 파일**
- `docs/06.dstone-boot.md` (수정)
- `docs/10.dstone-saga.md` — 실제 코드(OrderSagaController.java:47, OutboxRelayScheduler.java:27, OrderSagaReplyListener.java:54, saga-*.svg 7종 존재)와 전부 대조 확인했으나 이미 정확해서 **수정 없음**
- dstone-boot 소스 주석 — 점검 결과 수정할 만큼 잘못되거나 불필요한 주석을 찾지 못해 **수정 없음**

**(b) docs/06.dstone-boot.md에서 실제로 바뀐 내용**
1. **3절 패키지 구조 트리 전면 수정**(가장 큰 오류): 
   - `analyzer/`가 실제로는 `AnalysisController.java`가 바로 밑에 있는 게 아니라 `controller/`·`service/`·`dao/`·`cud/`·`taskitem/`·`vo/` 하위 패키지 구조임 → 반영
   - `ConfigSecurity.java`가 문서엔 `common/security/`로 돼 있었는데 실제로는 `common/config/`에 있고, `common/security/`엔 인증 핸들러/프로바이더/dao/svc/vo/web이 들어있음 → 정정
   - `common/messaging/`(SAGA/Outbox 배선: `saga/`, `outbox/`) 패키지가 트리에서 통째로 빠져 있었음 → 추가하고 10.dstone-saga.md로 상호참조
   - `common/tools/`에 `bizgen/`·`datagen/`·`rule/`이 누락돼 있었음 → 추가
   - `sample/` 하위에 실제 존재하는 `admin/`·`kafka/`·`rabbitmq/`·`saga/`·`user/`·`vo/`가 문서에 없었음 → 추가
2. **5.2절 주요 클래스 표**: FQCN이 실제와 다름(`analyzer.AnalysisController` → `analyzer.controller.AnalysisController` 등), 빠져 있던 `ConfigurationController` 추가
3. **8절 샘플 코드 표**: `admin/`·`kafka/`·`rabbitmq/`·`saga/`·`user/` 행 추가, 특히 `saga/`는 10.dstone-saga.md로 링크

**검증만 하고 문제없어 그대로 둔 것**: 10절(AI 연동) 전체 — ChatController/ChatService/DocumentController/DocumentService 실제 코드, JSP 경로(`ai/chat/chat.jsp` 등), `interface.ai-engine.base-url` 설정 키까지 하나하나 대조했고 문서 내용과 100% 일치.

**(c) 고친 주석**: 없음. TODO 마커(DstoneBootApplication 3곳, BizGenerator/TestBiz1/2)는 실제 미구현 상태를 정확히 나타내는 정상 마커라 "잘못된 주석"이 아니어서 그대로 뒀고, ConfigDatasource/ConfigSecurity/OutboxRelayScheduler 등 핵심 클래스 주석도 실제 동작과 일치함을 확인.</result>
<usage><subagent_tokens>182797</subagent_tokens><tool_uses>22</tool_uses><duration_ms>158578</duration_ms></usage>
</task-notification>

---

## 답변

dstone-boot/SAGA 문서 작업도 완료됐습니다 (3/7) — 패키지 구조 트리가 실제 코드와 꽤 어긋나 있던 걸 바로잡았네요. 남은 4개 계속 대기 중입니다.

---

## 질문

<task-notification>
<task-id>a91b5644fde668bee</task-id>
<tool-use-id>toolu_012GLscvhKcLvhnLwGo5EX3Y</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/7acaa229-0ae5-4fbd-9d28-0a284f158bb2/tasks/a91b5644fde668bee.output</output-file>
<status>completed</status>
<summary>Agent "Audit and update dstone-batch docs" finished</summary>
<note>A task-notification fires each time this agent stops with no live background children of its own. The user can send it another message and resume it, so the same task-id may notify more than once.</note>
<result>dstone-batch 담당 작업 완료.

**(a) 수정한 파일**
- `docs/07.dstone-batch.md`
- `dstone-batch/src/main/java/net/dstone/batch/common/DstoneBatchApplication.java`
- `.../common/biz/BaseService.java`
- `.../common/config/ConfigAutoReg.java`
- `.../common/runner/RestApiRunner.java`
- `.../common/runner/ScdfTaskRunner.java`

**(b) 문서에서 실제로 바뀐 핵심 내용**
- 신규 "7.3 방법 3 - REST API로 제어 (`RestApiRunner`)" 절 추가: 실제 `/batch/**` 엔드포인트 12개 전체 표로 정리 + `startJob`이 즉시 `STARTED`로 응답하는 이유(sync `jobLauncher` vs async `asyncJobLauncher` 이원화, `ConfigJob.java` 실제 코드 근거)를 콜아웃과 mermaid 시퀀스 다이어그램으로 설명.
- "8. 샘플 잡 목록" 표를 `(update 관련 잡들)` 같은 뭉뚱그린 표현 대신 코드에서 확인한 실제 `@AutoRegJob(name=...)` 11개 전부로 교체(`tableDataGenType01/02Job`, `tableUpdateType01/02/03Job`, `fileDataGenJob`, `fileCopyType01/02Job`, `fileToTableJob`, `tableToFileJob`).
- 목차에 7.3 항목 추가.
- `BATCH_JOB_SEQ`→`BATCH_JOB_INSTANCE_SEQ` 관련 기존 서술은 실제 스키마 파일(`02-create-table-mysql-dstone-batch.sql`)과 대조해 정확함을 확인, 변경 없음.

**(c) 고친 주석**
- `DstoneBatchApplication.java:1-15` — 이 프로젝트와 무관한 Apache 2.0 "Copyright 2012-2013 the original author or authors" 라이선스 헤더(다른 어떤 파일에도 없는 유일한 잔재, 어딘가에서 복붙된 흔적) 제거.
- `RestApiRunner.java:28-34` — 클래스 Javadoc이 존재하지 않는 URL 패턴 `/batch/restapi/{jobName}`을 설명하고 있었음 → 실제 12개 엔드포인트 목록으로 교체.
- `RestApiRunner.java:77`, `ConfigAutoReg.java:44`, `BaseService.java:144`, `ScdfTaskRunner.java:143` — 4곳 모두 존재하지 않는 어노테이션 `@AutoRegisteredJob`을 언급하는 동일한 주석이 반복돼 있었음 → 실제 어노테이션명 `@AutoRegJob`으로 통일.

git commit은 하지 않았습니다.</result>
<usage><subagent_tokens>157267</subagent_tokens><tool_uses>27</tool_uses><duration_ms>156096</duration_ms></usage>
</task-notification>

---

## 답변

dstone-batch도 완료됐습니다 (4/7) — 존재하지도 않는 어노테이션(`@AutoRegisteredJob`)을 언급하던 주석 4곳과 무관한 라이선스 헤더 하나를 정리했네요. 3개 남았습니다.

---

## 질문

<task-notification>
<task-id>a9427295f028ce7c2</task-id>
<tool-use-id>toolu_01V9R72hj49zgK7PTyaQeAu3</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/7acaa229-0ae5-4fbd-9d28-0a284f158bb2/tasks/a9427295f028ce7c2.output</output-file>
<status>completed</status>
<summary>Agent "Audit and update dstone-common docs" finished</summary>
<note>A task-notification fires each time this agent stops with no live background children of its own. The user can send it another message and resume it, so the same task-id may notify more than once.</note>
<result>Numbering is consistent. Task complete.

**(a) 수정한 파일**
- `docs/05.dstone-common.md`
- `dstone-common/src/main/java/net/dstone/common/messaging/saga/SagaOrchestrator.java` (주석만)

**(b) 문서에서 실제로 바뀐 핵심 내용**
- `config/` 패키지 설명 정정: "Redis/RabbitMQ/WebSocket 설정"이라는 기존 서술이 틀렸음을 확인 — 실제로는 각 모듈이 자기 `config/`에서 직접 구성하고, `dstone-common`의 `config/`는 ENC 복호화(`ConfigProperty`)와 로깅 AOP 뼈대(`ConfigAspect`)만 담당. 콜아웃으로 명시.
- 신규 5절 "공통 베이스 클래스" 추가 — `BaseController`/`BaseService`/`BaseDao`/`BaseVo`(리포지토리 전체 31곳에서 상속)를 mermaid 클래스 다이어그램 + 표로 정리, `ErrCd`/`DsExceptionResolver` 에러 응답 체계도 함께 설명.
- `BaseVo`의 `@XmlRootElement(name="MemberVo")`가 모든 하위 VO에 고정 적용되는 실제 동작을 경고 콜아웃으로 문서화(코드는 스코프 밖이라 안 건드림, 문서에만 기록).
- "애플리케이션 기동 순서" 절: `setSysProperties()`가 `dstone-common`의 공유 구현이 아니라 **4개 모듈이 각자 구현한 동일 컨벤션**이라는 점을 명확히 정정. 미사용 레거시 `InitServlet`(참조 0건 확인)도 명시.
- 패키지 구조 트리에 `queue`/`socket`/`task`/`websocket` 각각의 실제 사용처(또는 미사용) 주석 추가 — `socket`은 grep 결과 리포지토리 전체에서 참조 0건인 레거시로 확인.
- 헤더 번호를 5~9 → 6~10으로 재정렬, 목차와 상호 참조(`7절 참고` 등) 동기화.

**(c) 고친 주석**
- `SagaOrchestrator.java:60-61` — 생성자 Javadoc의 `@param outboxAppender -`(내용 없음), `@param stepHandlers`(설명 없음)를 실제 역할 설명으로 채움.
- 그 외 `ConfigProperty`/`EncUtil`/`Config`/`ConfigAspect`/`OutboxRelay`/`OutboxAppender(Impl)`/`NoAspectLog` 주석은 실제 구현과 대조 확인했으나 전부 정확해서 손대지 않음.

범위 밖 발견 사항(수정 안 함, 참고용): `BaseVo`의 `@XmlRootElement` 하드코딩은 문서에만 기록.</result>
<usage><subagent_tokens>172838</subagent_tokens><tool_uses>33</tool_uses><duration_ms>184063</duration_ms></usage>
</task-notification>

---

## 답변

dstone-common도 완료됐습니다 (5/7) — 공통 베이스 클래스(BaseController/Service/Dao/Vo) 설명이 통째로 문서에 빠져 있던 걸 새로 추가했네요. 2개 남았습니다 (ai-engine, 환경/소프트웨어 가이드).
