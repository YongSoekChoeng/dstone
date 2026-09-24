

### 1. Agent 항목

`agent:` 아래에 적는다(`common.definition.AgentDefinition`).

| 항목 | 필수 | 타입 | 설명 |
|---|---|---|---|
| `id` | ✅ | 문자열 | Agent 이름. step의 `ref`와 채팅 API의 `agent`가 이 값을 쓴다. 중복 불가 |
| `prompt` | ✅ | 문자열(여러 줄) | 시스템 프롬프트 원문. `{변수}`는 호출 시 채워진다(Workflow에서는 컨텍스트의 `input`, 채팅에서는 요청의 `variables`) |
| `description` | | 문자열 | 사람이 읽는 설명. `GET /api/ai/chat` 목록에 쓰인다 |
| `model` | | 문자열 | 이 Agent만 쓸 모델 이름(같은 provider 안에서). 비우면 공통 기본값. 다른 provider의 모델 이름이면 기동이 아니라 첫 호출 때 실패한다 |
| `toolsEnabled` | | boolean | LLM이 Tool을 스스로 골라 부를 수 있는지. 기본 `false` |
| `ragEnabled` | | boolean | 적재된 문서를 검색해서 답변에 참고하는지. 기본 `false` |
| `ragTopK` | | 정수 | RAG 검색 결과 최대 개수. 비우면 `dstone.ai.rag.retrieval.top-k` |
| `ragSimilarityThreshold` | | 실수 | RAG 최소 유사도. 비우면 `dstone.ai.rag.retrieval.similarity-threshold` |
| `ragAllowEmptyContext` | | boolean | 검색 결과가 없을 때도 답을 시도할지. 비우면 `true`. `false`면 "모른다"고 답하도록 강제 |
| `allowedCallers` | | 문자열 리스트 | 호출을 허락할 caller 목록. 비우면 누구나(⚠️ Workflow와 같은 주의사항) |

**StepType별 prompt 작성 요령**

| 용도 | prompt에 꼭 넣을 것 |
|---|---|
| AGENT(schema 없음) | 역할과 규칙, 결과물의 형식 |
| AGENT + `output.schema` | 역할과 규칙. JSON 형식 지시는 엔진이 자동으로 붙이므로 적지 않아도 된다 |
| SUPERVISOR | 무엇을 기준으로 pass/fail을 판정할지, reason에 무엇을 적을지 |
| ROUTER | route로 쓸 수 있는 이름 목록과 각각의 기준(Workflow의 `routes` 키와 정확히 같게) |

### 2. 제공되는 샘플

`src/main/resources/{agents,workflows,mcp}/` 아래에 두 묶음이 있다. 각 파일 맨 위 주석에 dstone-boot "Workflow 테스트"
화면에서 어떤 workflowId/message/variables로 호출하면 되는지 적혀 있다.

**Workflow**

| 기능 | Workflow |
|---|---|
| AGENT step 기본(변수/Tool 없이) | `sample/sample-agent-basic-echo.yml` |
| AGENT의 자율 tool-calling | `sample/sample-agent-tool-calling.yml` |
| AGENT의 RAG(ragEnabled) 증강 | `sample/sample-agent-rag-augmented.yml` |
| Agent별 model override | `sample/sample-agent-model-override.yml` |
| TOOL step 연쇄(LLM 없이) | `sample/sample-tool-chain-basic.yml` |
| MCP Tool + `output.parse: lines` + 앞 step data로 `forEach` | `sample/sample-mcp-filesystem-list.yml` |
| TOOL로 RAG 검색만 직접 호출 | `sample/sample-tool-rag-search.yml` |
| 위험 Tool(http/shell/python) 기본 거부 확인 | `sample/sample-tool-gated-external.yml` |
| AGENT `output.schema` → `{{steps.<id>.data.<키>}}`, Workflow `output` | `sample/sample-structured-output-chain.yml` |
| SUPERVISOR(pass/reason) 판정 | `sample/sample-supervisor-verdict-gate.yml` |
| ROUTER 다지 분기(routes) | `sample/sample-router-multiway.yml` |
| onFailure 재시도 루프 + `previous`/`??`/`steps.<id>.error` | `sample/sample-loop-retry-until-valid.yml` |
| `forEach` 병렬 실행 + Workflow `inputs` 계약 | `sample/sample-foreach-parallel.yml` |
| APPROVAL 일시중단/재개(HITL) | `sample/sample-approval-pause-resume.yml` |
| 실전 8단계 승인형 SDLC(AGENT+APPROVAL+TOOL 종합) | `testApp/testApp-sdlc.yml` |

**Agent**

| Agent | 용도 |
|---|---|
| `sample-basic-echo-agent` | 가장 단순한 AGENT(변수/Tool 없음) |
| `sample-general-chat` | 일반 대화(`{role}` 변수). dstone-boot 채팅 화면 기본값 |
| `sample-tool-demo-agent` | 로컬 Tool 자율 호출(`toolsEnabled: true`) |
| `sample-mcp-filesystem-agent` | MCP filesystem Tool 자율 호출 |
| `sample-rag-demo-agent` | RAG 증강(`ragEnabled: true`, `ragTopK`/`ragSimilarityThreshold`) |
| `sample-model-override-agent` | Agent별 `model` 지정 |
| `sample-structured-extract-agent` | `output.schema` AGENT용(문장에서 SQL 추출) |
| `sample-fix-agent` | 재시도 루프용(검증 실패한 SQL 수정) |
| `sample-verdict-judge-agent` | SUPERVISOR용(pass/reason 판정) |
| `sample-router-classifier-agent` | ROUTER용(billing/technical/other 분류) |
| `testApp-analysis-agent` / `testApp-spec-writer-agent` / `testApp-codegen-agent` | `testApp-sdlc`의 분석/명세/코드 생성 |


### 3 Agent 뼈대

```yaml
# <이 Agent가 무엇을 하는지 한두 줄>
agent:
  id: <agent-id>
  description: <사람이 읽는 설명>
  prompt: |
    당신은 <역할>입니다.
    <지켜야 할 규칙>
    답변은 항상 한국어로 합니다.
  toolsEnabled: false
  ragEnabled: false
  # model: <모델 이름>                  # 선택. 비우면 공통 기본 모델
  # allowedCallers: [<caller>]          # 선택. 인증을 켠 환경에서만 채운다
```

**Tool을 스스로 쓰는 Agent**

```yaml
agent:
  id: <agent-id>
  prompt: |
    당신은 <역할>입니다. 필요하면 제공된 Tool을 호출해서 사실을 확인한 뒤 답하십시오.
  toolsEnabled: true                    # caller 화이트리스트를 통과한 Tool 전부가 후보가 된다
  ragEnabled: false
```

**RAG를 쓰는 Agent**

```yaml
agent:
  id: <agent-id>
  prompt: |
    당신은 적재된 문서를 참고해서 답하는 어시스턴트입니다.
    [참고자료]가 주어지면 그 내용을 근거로 답하고, 없으면 일반 지식으로 답한다는 점을 밝히십시오.
  toolsEnabled: false
  ragEnabled: true
  ragTopK: 3                            # 선택
  ragSimilarityThreshold: 0.3           # 선택
  ragAllowEmptyContext: true            # 선택. 기본 true
```

**SUPERVISOR용 Agent**

```yaml
agent:
  id: <agent-id>
  prompt: |
    당신은 주어진 텍스트가 <기준>을 만족하는지 판정하는 검토자입니다.
    만족하면 pass=true, 아니면 pass=false로 답하고,
    reason에는 그렇게 판정한 이유를 한국어로 한 줄 적으십시오.
  toolsEnabled: false
  ragEnabled: false
```

**ROUTER용 Agent**

```yaml
agent:
  id: <agent-id>
  prompt: |
    당신은 요청을 아래 갈래 중 정확히 하나로 분류하는 라우터입니다.
    - billing: <기준>
    - technical: <기준>
    - other: 위에 해당하지 않는 모든 요청
    route에는 반드시 billing/technical/other 중 하나만 그대로 적으십시오.
    reason에는 왜 그렇게 분류했는지 한국어로 한 줄 적으십시오.
  toolsEnabled: false
  ragEnabled: false
```
