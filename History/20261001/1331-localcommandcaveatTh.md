# localcommandcaveatTh

> 생성일시: 2026-10-01 13:31

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="19d3">
dstone-ai-engine 의 workflow 를 진행하는데 있어서 드는 의문점 및 개선점이야 네 의견을 말해줘.

A. APPROVAL/SUPERVISOR 의 경우 차이가 아래와 같은 것 같은데 맞나?
    1. APPROVAL
        - 진행 step 의 승인여부를 확인
        - 별도의 agent 를 호출하지 않는다.(승인여부를 확인하는 것은 사람이다)
        - approverRole(승인자) 이 존재한다.
    2. SUPERVISOR
        - 진행 step 의 적합성 여부를 확인(이 부분은 APPROVAL 과 크게 다르지 않은 듯)
        - 검토자가 존재하지 않고 적합성 여부를 확인하는 별도의 agent 를 호출한다.
        
B. dstone-ai-engine 에 sub agent 개념을 넣고 싶은데 계획을 세워서 말해줘.
    - 아직은 소스를 건드리지 말고 계획만 이야기를 나누자.
</pasted_content id="19d3">

---

## 답변

먼저 실제 코드를 확인하고 의견을 드리겠습니다.

---

## 답변

코드(`ApprovalStepDefinition`/`SupervisorStepDefinition`, 두 실행기, `WorkFlowExecutor.nextStepId()`)를 확인하고 드리는 의견입니다. 소스는 건드리지 않았습니다.

## A. APPROVAL / SUPERVISOR 차이

큰 틀은 맞습니다. 다만 세 군데는 바로잡아야 합니다.

| | APPROVAL | SUPERVISOR |
|---|---|---|
| 판정자 | 사람 (decision API) | LLM (`ref`의 Agent) |
| 판정 대상 | 없음 (`ref`도 `input`도 없음). 사람이 실행 상세에서 컨텍스트 전체를 보고 판단 | step의 `input`에 넣어준 값만 |
| 실행 방식 | Workflow가 `WAITING_APPROVAL`로 멈췄다가 결정 후 같은 step을 재실행 | 멈추지 않고 그 자리에서 판정 |
| output (엔진 고정) | `{approved, approver, comment}` | `{pass, reason}` |
| forEach / memory | 불가 | 가능 |

- **`approverRole`은 기록용입니다.** 서버가 역할을 검사하지 않아서, 지금은 decision API를 부를 수 있으면 누구나 승인할 수 있습니다.
- **SUPERVISOR는 "진행 step"을 검사하는 것이 아닙니다.** `input`으로 넘긴 값을 검사하므로, 직전 step 결과든 여러 step을 jq로 합친 값이든 넣는 대로 봅니다.
- **"크게 다르지 않다"는 흐름 제어 관점에서만 맞습니다.** 둘 다 통과면 `onSuccess`, 아니면 `onFailure`로 가는 관문입니다. 하지만 SUPERVISOR는 자동 재시도 루프용(`onFailure: convert`)이고, APPROVAL은 책임 소재가 필요한 지점에서 실행을 끊고 기다리는 용도라 타입은 분리해 두는 것이 맞습니다.

개선할 만한 점은 두 가지입니다.

1. **`approverRole` 실제 검사**: decision API에서 `CallerContext`의 caller와 대조하거나, 검사하지 않을 거면 이름을 `approverHint` 정도로 바꿔 오해를 없애는 것이 좋습니다.
2. **APPROVAL에 옵셔널 `input` 추가**: 지금은 승인자가 컨텍스트 전체를 뒤져야 합니다. `input: "${ .steps.design.output }"`처럼 "승인자에게 보여줄 것"을 선언하면 SUPERVISOR와 대칭이 되고 승인 화면도 단순해집니다.

## B. Sub Agent 도입 계획

### 방향: Agent를 Tool처럼 노출

"sub agent"는 두 가지로 해석할 수 있는데, 저는 1번을 추천합니다.

1. **Agent-as-Tool (추천)**: 부모 Agent의 LLM이 실행 중에 스스로 판단해서 다른 Agent에게 일을 맡깁니다.
2. **Sub-Workflow step** (`type: WORKFLOW`): Java가 정해진 순서로 다른 Workflow를 부릅니다. 이것은 "Workflow 재사용"이라 별개 기능으로 보는 것이 맞습니다.

1번을 추천하는 이유:

- **엔진 원칙과 맞습니다.** "순서는 Java, step 안의 판단은 LLM"에서 위임 여부는 step 안의 판단입니다. 새 step 타입이 필요 없어 `WorkFlowExecutor`를 건드리지 않습니다.
- **컨텍스트가 격리됩니다.** 2026-10-01의 700KB 요청 사고처럼 Tool 결과가 대화에 쌓이는 문제를 구조적으로 줄입니다. 소스 조사 sub agent가 파일 수십 개를 읽어도 부모에게는 요약만 돌아옵니다.
- **계약 모델을 그대로 씁니다.** sub agent의 `input` 스키마가 Tool 인자 스키마, `output`이 Tool 응답, `description`이 Tool 설명이 됩니다.

### YAML 모양

```yaml
agent:
  id: pilot-orchestrator-agent
  prompt: |
    ... 조사가 필요하면 source-investigator에게 맡겨라 ...
  subAgents:
    - pilot-source-investigator-agent
    - pilot-sql-convert-agent
```

`toolsEnabled`와는 독립입니다. Tool 없이 sub agent만 쓰는 조율 전용 Agent도 가능합니다.

### 구현 단계

1. **정의**: `AgentDefinition`에 `List<String> subAgents`를 추가합니다.
2. **부팅 검증** (`AgentRegistry`, 첫 오류에서 부팅 실패):
   - 가리키는 id가 존재하는지
   - 순환 참조(A→B→A, 자기 자신)가 없는지
   - 깊이가 `dstone.ai.agent.sub-agent.max-depth` 이내인지
   - sub agent에 `description`이 있는지 (LLM이 이것을 보고 고르므로 필수로 승격)
   - Tool 이름과 충돌하지 않는지
3. **실행** (`runtime.agent`):
   - `SubAgentToolCallback`을 새로 만들고, `AgentExecutor.buildSpec()`이 `subAgents`마다 하나씩 붙입니다.
   - 호출되면 `AgentRegistry.resolve(id, caller)`로 `allowedCallers`를 검사한 뒤 `AgentExecutor.call()`을 부릅니다.
   - conversation id는 주지 않습니다. 매번 새 대화여야 격리가 됩니다.
   - caller는 콜백을 만들 때 값으로 넣어 둡니다. 스트리밍에서는 스레드가 바뀔 수 있어 `CallerContext`에 기대면 안 됩니다.
4. **실패 처리**:
   - `AgentContractException`(입출력 모양 틀림)은 오류 문구를 Tool 응답으로 돌려줘서 부모 LLM이 고쳐 다시 부르게 합니다.
   - 시스템 오류는 그대로 던져 기존 규칙대로 실행 전체를 FAILED로 끝냅니다.
5. **안전장치**:
   - 결과는 기존 `LimitedToolCallback`으로 감싸 `max-result-chars`를 적용합니다.
   - 부모 호출 1회당 sub agent 호출 횟수 상한을 둡니다.
   - LLM 호출이 중첩되어 step 하나가 길어지므로 응답 타임아웃을 재점검합니다.
6. **가시성**: sub agent 호출을 `ConfigCallLog` 포인트컷에 넣어 로그로 남깁니다.
7. **API**: `GET /api/ai/chat` 목록에 `subAgents`를 노출합니다. 채팅 API는 `AgentExecutor`를 공유하므로 자동으로 지원됩니다.
8. **샘플·검증·문서**:
   - 샘플 조율 Agent와 Workflow를 추가합니다.
   - 깨진 YAML(없는 id, 순환, description 누락, 깊이 초과)이 부팅에서 거부되는지 확인합니다.
   - `docs/09.dstone-ai-engine.md` §7·§8과 `CLAUDE.md`를 갱신합니다.

### 정해야 할 것

1. **해석**: Agent-as-Tool로 갈지, Sub-Workflow도 필요하신지.
2. **최대 깊이**: 기본 1(부모→자식만)을 권합니다. 손자까지 허용하면 비용과 지연이 곱으로 늘고 추적이 어려워집니다.
3. **호출 내역 기록**: 지금 `steps.<id>`에는 `{input, output, error}`만 남아 sub agent 호출이 보이지 않습니다. 1차는 로그만 남기고, 실행 상세 화면에서 볼 필요가 생기면 `StepHistoryEntry`에 호출 trace를 추가하는 2차로 나누는 것을 권합니다.
4. **SUPERVISOR/ROUTER Agent의 `subAgents`**: 기술적으로는 그냥 동작합니다. 다만 판정용 Agent는 가볍게 두는 편이 좋아 1차에서는 AGENT step과 채팅 API만 허용하는 것을 권합니다.

---

## 질문

그러면 subAgents의 Context는 부모Agent 의 것을 그대로 복사해서 진행하는 것인가?

---

## 답변

아니요, 복사하지 않습니다. 제 계획에서 sub agent는 **빈 대화에서 새로 시작**하고, 부모가 호출할 때 인자로 넘긴 값만 봅니다.

## sub agent가 보는 것과 못 보는 것

| | sub agent에게 전달되는가 |
|---|---|
| 자기 자신의 시스템 프롬프트 (`agents/*.yml`의 `prompt`) | 예 |
| 부모 LLM이 호출하면서 넘긴 인자 (sub agent의 `input` 스키마 모양) | 예. 이것이 사용자 메시지가 됩니다 |
| 부모의 시스템 프롬프트 | 아니요 |
| 부모의 대화 이력 (앞서 부른 Tool 결과 포함) | 아니요 |
| Workflow 실행 컨텍스트 (`input`, `steps.*`) | 아니요 |
| caller(tenant) | 예. 대화 내용이 아니라 권한 검사용으로만 넘깁니다 |

돌아올 때도 마찬가지로, 부모 대화에는 sub agent의 **최종 output만** Tool 응답으로 들어갑니다. sub agent가 중간에 읽은 파일이나 Tool 결과는 부모에게 쌓이지 않습니다.

## 복사하지 않는 이유

- **격리가 도입 목적입니다.** 부모 대화를 통째로 복사하면 sub agent 호출마다 그 크기만큼 토큰이 다시 나가서, 700KB 사고 같은 문제가 줄지 않고 오히려 곱으로 늘어납니다.
- **지금 엔진 규칙과 같습니다.** 현재도 Workflow input은 프롬프트에 직접 닿지 않고, Agent는 자기 `input`으로 받은 것만 압니다. sub agent도 같은 규칙을 따르면 "Agent는 계약으로 받은 것만 안다"가 예외 없이 유지됩니다.
- **재사용이 쉽습니다.** 부모가 누구든 같은 input이면 같은 조건에서 동작하므로, sub agent를 채팅 API로 단독 호출해 테스트할 수 있습니다.

## 그 대가

부모 LLM이 필요한 맥락을 인자에 직접 담아 넘겨야 합니다. 빠뜨리면 sub agent는 그 사실을 알 방법이 없습니다. 그래서 두 가지가 중요해집니다.

- **sub agent의 `input`을 object 스키마로 선언**: `input: string`으로 두면 부모가 "이 파일 조사해줘" 한 줄만 넘기기 쉽습니다. `{target, question, constraints}`처럼 필드를 `required`로 두면 부모 LLM이 채우지 않을 수 없습니다.
- **`description`을 구체적으로**: 부모 LLM은 이 설명과 input 스키마만 보고 무엇을 넘길지 정합니다.

## 대안: 컨텍스트를 물려주는 방식

부모 대화를 그대로 이어받는 fork 방식도 있기는 합니다. 맥락 누락이 없다는 장점이 있지만, 토큰 비용이 크고 격리 효과가 사라지며 Spring AI의 Tool 콜백 안에서는 부모의 대화 이력에 접근하는 표준 경로가 없어 구현도 억지스럽습니다. 1차에서는 넣지 않는 것을 권합니다.

Workflow 컨텍스트의 특정 값을 sub agent에게 **항상** 넘겨야 하는 경우가 생기면, 그것은 부모 Agent의 `input`에 그 값을 넣고 부모 프롬프트에서 "sub agent에게 이 값을 전달하라"고 지시하는 것으로 해결됩니다. 엔진이 몰래 주입하는 경로는 만들지 않는 편이 "암묵적 이름 없음" 원칙과 맞습니다.
