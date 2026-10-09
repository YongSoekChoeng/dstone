# pasted_content_id3a8

> 생성일시: 2026-10-09 14:48

## 질문

<pasted_content id="3a8d">
dstone-ai-engine 와 상관없이 dstone-workflow 모듈을 만들고자 해.
네 의견을 정리해서 말해줘.
<의견정리파일>
/app/dstone/docs/temp/dstone-workflow-design.md
</pasted_content id="3a8d">

---

## 답변

문서를 읽었습니다. LangGraph4j는 직접 써 보거나 버전 호환을 확인한 것이 아니라, 문서 내용과 지금 엔진 구조에 비춰 본 의견입니다.

## 결론

모듈을 새로 만드는 것은 찬성입니다. 다만 이 문서에서 값진 부분은 §15(영속 승인·재개·버전 고정·감사)이고, **LangGraph4j는 빼고 직접 만든 상태 기계로 가는 쪽을 권합니다.**

## LangGraph4j를 권하지 않는 이유

- **어려운 부분은 어차피 직접 만듭니다.** `wf_execution`/`wf_approval`/Outbox/멱등 재개/버전 고정은 모두 자체 구현이고, 문서도 §15.9에서 "체크포인트만으로 자동 재개되지 않는다"고 적고 있습니다. LangGraph4j가 맡는 것은 "다음 노드 고르기"뿐인데, 이는 `WorkFlowExecutor.nextStepId()` 수준의 작은 코드입니다.
- **진실이 두 군데가 됩니다.** 그래프 체크포인트와 `wf_execution`이 따로 있으면 둘이 어긋났을 때의 복구 규칙이 또 필요합니다. 직접 만들면 "Step이 끝날 때마다 컨텍스트 + 다음 Step id를 한 트랜잭션으로 저장"이 곧 체크포인트입니다.
- **로드맵이 스스로 증명합니다.** Phase 1은 LangGraph4j 없이 AGENT+APPROVAL을 돌리고 Phase 2에서 끼워 넣습니다. Phase 1 런타임을 버리게 되거나, 필요 없었다는 뜻이 됩니다.
- **10-07에 같은 판단을 이미 했습니다.** Spring AI Alibaba Graph를 "`WorkFlowExecutor`를 대체할 뿐"이라며 채택하지 않았는데, 이번도 같은 종류입니다.
- **호환성이 미확인입니다.** Spring Boot 4.1 / Spring AI 2.0.1 조합에서 도는지, 중단 노드가 재개 때 다시 실행되는지를 모릅니다.

그래도 쓰고 싶다면 1단계를 "승인 대기 → 프로세스 재시작 → 재개" 하나만 검증하는 스파이크로 바꾸고, 그 결과로 정하는 것이 안전합니다.

## 문서에서 꼭 가져갈 것 (지금 엔진에 없는 것)

- **정의 버전·해시 고정**: 지금은 재배포하면 대기 중이던 실행이 새 YAML로 재개됩니다.
- **승인을 독립 엔티티로**: 라운드마다 새 `approval_id`, 산출물 해시 연결, 승인자 권한 검증, 타임아웃·에스컬레이션. 지금은 컨텍스트 안의 값이고 소비되면 지워집니다.
- **멱등 재개와 재시작 복구**, Step 실행 이력·감사 이벤트 테이블.
- **Agent 단위 재시도 정책** (`maxAttempts`, `onInvalidOutput`).

## 문서가 지금 엔진보다 후퇴하는 부분

| 문서 | 문제 | 권고 |
|---|---|---|
| Step의 `output: state.x` + 공유 state | 이름을 Step이 정하는 암묵 결합이고, 엔진에서 일부러 없앤 구조입니다 | `steps.<id>.output` 고정, 계약은 Agent가 소유 |
| `${state.a.b}` 경로식 | 문자열 조립·기본값이 안 되고 `${ENV}` 치환과 충돌합니다 | jq 유지 |
| 단일 `StepDefinition` + 공통 `StepExecutor(Map→Map)` | 타입에 없는 키가 조용히 무시됩니다 | sealed 타입별 record + `switch` |
| Agent `role` enum, `context.sources` | 실행 의미가 불명확합니다 | 빼거나 의미를 먼저 정의 |
| `maxIterations: 3` 전역 | 루프별인지 Step별인지 모호합니다 | Step 방문 횟수 기준으로 명시 |
| `rejectTo`와 `routes.REJECTED` | 중복입니다 | `routes`만 |
| `{{requirement}}` | Spring AI 템플릿은 `{var}`입니다 | 하나로 통일 |

그 밖에 문서에 없는 것이 있습니다.

- **빠진 기능**: `forEach`, SUPERVISOR/ROUTER, Step별 메모리, Sub Agent가 없습니다. 지금 pilot이 쓰는 기능입니다.
- **예시가 실제 pilot과 다릅니다**: 문서는 JSON 산출물을 state로 넘기지만, 실제로는 긴 문서를 파일로 쓰고 다음 Agent가 읽습니다. 1만 자 넘는 문서를 STRICT JSON으로 받으면 `max-tokens` 절단 문제가 다시 납니다. "산출물은 저장소 참조"(§15.5)를 Step 계약 수준까지 내려야 합니다.
- **가장 과소평가된 부분**: "Spring AI ChatClient 연동" 한 줄입니다. 60초 타임아웃 우회, Tool 호출 한도, 결과 자르기·압축, 끊긴 인자 처리, 빈 응답 처리, 엔진 규칙 프롬프트가 모두 여기 들어갑니다.

## 정해 주셔야 할 것

"ai-engine과 상관없이"의 범위입니다. Agent/Tool 실행부(`AgentExecutor`, `ConfigTool`, `tools.*`)까지 새로 만들면 위 교훈을 전부 복제해야 하고, 두 벌을 유지하게 됩니다. 저는 **오케스트레이션·영속·승인만 새 모듈**로 하고, Agent/Tool 실행부는 공용 모듈로 뽑아 둘이 같이 쓰는 쪽을 권합니다. 완전 독립을 원하시면 복사해서 시작하되, ai-engine을 언제 걷어낼지 함께 정해야 합니다.

---

## 질문

그런데 너는 dstone-ai-engine 에서 pilot-workflowpilot-workflow.yml 의 fileContents 같은 장황한 내용이 합리적인 설계라고 생각해 ?

---

## 답변

아니요, 합리적이지 않습니다. 방향은 맞았지만 그 로직을 둔 자리가 틀렸습니다.

## 맞았던 것과 틀린 것

"검증·문서 조립·파일 수정처럼 정해진 일은 LLM에게 시키지 않는다"는 판단은 옳습니다. 문제는 그 결정적 로직을 Java가 아니라 YAML 안의 jq로 짰다는 점입니다. 지금 `pilot-workflow.yml`의 신규개발 경로는 선언이 아니라, YAML 문자열 안에 든 프로그램입니다.

- **테스트할 수 없습니다.** `newCheck`의 검증 규칙 9개와 `newTasksDoc`의 상태 판정(완료/실패/확인 필요)은 업무 로직인데 단위 테스트가 없습니다. 부팅 검증은 jq가 컴파일되는지만 보므로, 틀린 것은 실제 실행에서만 드러납니다.
- **"비개발자도 쓰는 YAML"이라는 엔진 전제와 어긋납니다.** `transpose | map(select(.[1] == true) | .[0]) | unique_by(.path)`는 개발자도 한 번에 못 읽습니다.
- **엔진에 없는 기능을 우회한 흔적입니다.**
  - 계산만 하는 Step이 없어서 `writeFile`의 인자를 변수처럼 쓰고, 뒤에서 `.steps.newCheck.input.fileContents`로 다시 읽습니다.
  - forEach 결과에 원래 항목이 붙어 오지 않아서 `[input, output] | transpose`가 열 번쯤 반복됩니다.
  - 문서 템플릿 기능이 없어서 마크다운을 `"…" + … + "\n"`으로 이어 붙입니다.
- **문자열에 의존합니다.** `startswith("복사했습니다")`, `!= "문제 없음"`, `startswith("실패")`처럼 Tool의 안내 문구를 분기 조건으로 씁니다. 문구를 고치면 조용히 깨집니다.
- **같은 코드가 복붙되어 있습니다.** `def c:`와 `def lines:`가 Step마다 다시 정의됩니다.

## 어떻게 됐어야 하나

| 지금 | 바람직한 모습 |
|---|---|
| `newCheck` / `newDesignCheck`의 jq 30줄 | Java 검증기 하나가 `{ok, problems[]}`를 반환하고 단위 테스트를 둠. YAML은 `ref` + 입력 3줄 |
| `newReqDoc` / `newImpactDoc` / `newDesignDoc` | 템플릿 파일 + "템플릿에 데이터 끼우기" Step |
| `newTasksDoc`의 상태 판정 | Java |
| `transpose` 반복 | forEach 결과를 `{item, output, error}`로 묶어서 반환 |
| `.steps.x.input.fileContents` 읽기 | 값을 계산하는 Step을 따로 두고 그 `output`을 읽음 |

이렇게 하면 이 파일의 신규개발 경로는 대략 1/4로 줄고, YAML에는 "무엇을 어디로 넘기는지"만 남습니다.

## dstone-workflow 의견 정정

앞에서 "표현식은 jq 유지"를 권했는데, 이 파일을 보고 반만 철회합니다.

- **표현식은 배선에만 씁니다.** 경로 읽기, 기본값(`//`), 짧은 문자열 연결까지만 허용하고, `def`·`reduce`·여러 줄 표현식은 부팅 때 거부하거나 경고합니다. 표현력이 넉넉하면 로직이 YAML로 새는 것을 이 파일이 보여 줍니다.
- **결정적 로직용 Step을 처음부터 둡니다.** 설계 문서의 `RULE`을 "허용된 조건식 실행"이 아니라 "등록된 Java 함수(타입 있는 입출력, 테스트 대상) 호출"로 정의하고, 문서 조립용 템플릿 Step을 추가합니다.
- **forEach 결과에 원래 항목을 포함**하고, Tool 성공·실패는 문구가 아니라 구조화된 값으로 받습니다.

설계 문서 §11의 "임의 로직을 정의 파일에 허용하지 않음"에 "표현식으로 짠 로직도 포함"이라고 명시해 두는 것이 좋겠습니다.

---

## 질문

<pasted_content id="3a8d">
나는 dstone-workflow 모듈을 새로 만들지 않고 dstone-ai-engine 을 그대로 사용하고자 한다면
jq 기능을 제거하는게 어떨까해.
그리고 각종 설정파일들은 /app/dstone/docs/temp/dstone-workflow-design.md 에 정의된 내용을 적용하면 어떨까 싶고.
workflow,  agent, agent용 schema를 정의한 json 등.
</pasted_content id="3a8d">

---

## 답변

둘 다 방향에는 찬성입니다. 다만 jq는 "문법은 두고 엔진만 걷어내는" 방식으로, 설계 문서는 "새로 생기는 것만 받고 이름만 바뀌는 것은 받지 않는" 방식으로 하기를 권합니다.

## 1. jq 제거

지금 Workflow 18개의 표현식을 세어 봤습니다.

| 종류 | 개수 | 어디에 |
|---|---|---|
| 경로 + 기본값뿐 (`.steps.a.output.x // ""`) | 한 줄 표현식 107개 중 86개 | 전체 |
| 문자열 이어 붙이기 (`.input + "/" + $entry.name`) | pilot 밖에서 5개 | sample 2개 파일 |
| 그 밖의 한 줄짜리 | 약 16개 | pilot에만 |
| 여러 줄짜리 프로그램 | 약 16개 | pilot에만 |

즉 jq다운 기능을 쓰는 곳은 `pilot-workflow.yml` 하나뿐이고, 나머지는 경로 읽기입니다. 그래서 이렇게 권합니다.

- **문법은 그대로 둡니다.** `"${ .steps.a.output.x // "" }"`와 `$item`, 문자열 `+`만 지원하는 작은 평가기를 직접 만들면, 위 표에서 91개(86 + 5)는 YAML을 고치지 않아도 됩니다. `${state.x}`로 바꾸면 전부 고쳐야 하고 `${ENV}` 치환과도 겹칩니다.
- **그 밖의 것은 부팅 실패로 막습니다.** `map`, `select`, `def`, `transpose`, 파이프가 해당합니다.
- **순서가 중요합니다.** pilot의 로직이 갈 곳이 먼저 있어야 jq를 뺄 수 있습니다.
  1. 엔진에 세 가지를 추가합니다: 등록된 Java 함수를 부르는 Step, 템플릿 파일에 데이터를 끼우는 Step, forEach 결과를 `{item, output, error}`로 묶어 주는 것.
  2. pilot 신규개발 경로의 검증·문서 조립·상태 판정을 Java로 옮기고 단위 테스트를 둡니다.
  3. 표현식을 줄이고 jackson-jq 의존성을 뺍니다.

2번은 생각보다 안전합니다. 옮기는 대상이 전부 LLM 없는 Step이라, 기존 실행의 Step 입력을 그대로 넣으면 새 Java 코드가 같은 문서를 내는지 글자 단위로 비교할 수 있습니다.

비용도 있습니다. forEach 대상을 걸러내는 표현식(`select(.changeType == "신규")` 등)도 Java 함수로 가야 해서, pilot 전용 함수가 대여섯 개 생깁니다. 저는 그것이 맞는 자리라고 봅니다.

## 2. 설계 문서의 설정 형식 적용

**받을 것 (지금 엔진에 없는 것)**

- **Prompt를 별도 파일로.** pilot 프롬프트는 길고 자주 고치므로 효과가 가장 큽니다. 짧은 sample용으로 인라인도 계속 허용합니다.
- **Schema를 JSON 파일로 참조.** 큰 것과 여러 Agent가 함께 쓰는 것만 파일로 두고, `output: string` 같은 축약은 유지합니다.
- **`version` + 정의 해시.** 다만 classpath에는 한 버전만 있으므로 버전 번호만 적어서는 고정되지 않습니다. 실행을 시작할 때 정의 사본을 실행 행에 저장해야 재배포 후에도 원래 정의로 재개됩니다.
- **Agent 실행 정책** (`maxAttempts`, `timeoutSeconds`, `onInvalidOutput`).
- **승인 설정과 승인 테이블** (산출물 해시, 타임아웃, 반려 시 의견 필수, 라운드별 이력). 이것은 따로 한 단계로 잡습니다.

**받지 않을 것**

| 문서의 항목 | 이유 |
|---|---|
| Step의 `output: result: state.x` + state schema | 지금의 `steps.<id>.output` 고정이 더 단순하고 부팅 때 검증됩니다 |
| `next` | `onSuccess`/`onFailure`와 뜻이 같습니다. 파일 18개를 고쳐도 얻는 것이 없습니다 |
| 최상위 `workflow:` / `agent:` 감싸기 | 모양만 바뀝니다 |
| `tools.allowed` | 지금의 `tools`와 같습니다 |
| `role`, `context.sources` | 실행 의미가 정해져 있지 않습니다 |
| `model.routing` | provider가 하나라 `model`로 충분합니다. 필요해지면 그때 추가합니다 |
| `{{var}}` 템플릿 | Spring AI는 `{var}`이고, `{{ }}`는 엔진이 이미 금지한 옛 문법입니다 |
| LangGraph4j | 앞에서 말한 이유 그대로입니다 |

## 권하는 순서

1. 엔진 기능 추가 (함수 Step, 템플릿 Step, forEach 결과 모양)
2. pilot 로직을 Java로 이전하고 기존 산출물과 비교
3. 표현식 축소, jackson-jq 제거
4. Prompt·Schema 파일 참조
5. 버전·정의 사본 고정, Agent 재시도 정책
6. 승인 엔티티

1~3은 한 묶음이고, 4 이후는 서로 독립이라 필요한 것만 골라서 할 수 있습니다. 파일 형식을 바꾸면 `docs/09.dstone-ai-engine.md` §7·§8과 부팅 검증 메시지도 함께 고쳐야 합니다.
