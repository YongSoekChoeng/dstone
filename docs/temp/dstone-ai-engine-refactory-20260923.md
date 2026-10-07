# dstone-ai-engine 타입/패키지 정리 계획 (2026-09-23)

## 0. 배경

`dstone-ai-engine`은 2026-09-15 전면 재설계 이후 Agent/Tool/Workflow가 계속 늘어나면서, 타입들이 "일단 만들고 보니 여기저기 흩어진" 상태가 됐다. 사용자가 지적한 세 가지를 이번에 정리한다.

1. **패키지 구성/클래스명 재정의** — `common.definition`에 YAML 바인딩 레코드가 아닌 enum(`StepType`, `McpTransport`)이 섞여 있고, `runtime.status`에는 성격이 전혀 다른 클래스 10개가 뭉쳐 있다.
2. **StepType 분류 재정의** — `AGENT/SUPERVISOR/ROUTER`(LLM 호출)와 `TOOL/APPROVAL`(LLM 미호출)이 성격이 다른데 지금은 코드 곳곳의 `switch`/주석으로만 암묵적으로 구분된다.
3. **IN/OUT 재정의** — `runtime` 안의 Executor/Runner가 주고받는 타입이 너무 많고, 실제로는 같은 모양인데 이름만 다른 것들이 있다.

**주석 원칙**: 이번 작업은 "옮기기/쪼개기/합치기" 위주라, 기존 Javadoc 문장은 **그대로 재사용**하고 클래스/패키지 경로가 바뀐 부분만 최소한으로 고친다. 새로 생기는 타입(sealed interface 등)에만 새 설명을 짧게 붙인다. 서술형 문장을 다시 쓰거나 다듬는 작업은 하지 않는다.

**코드 스타일**: 기존 코드 전반이 람다 대신 익명 클래스를 쓰는 스타일을 유지하고 있으므로(`AgentExecutor.buildSpec()`의 `new Consumer<>(){...}` 참고) 이번 리팩토링도 그 스타일을 따른다. Java 21 `sealed interface` + `switch` 패턴 매칭은 람다가 아니므로 사용 가능하다.

---

## 1. 조사로 확인한 사실 (근거)

- `runtime.workflow.WorkFlowContext`는 **아무도 참조하지 않는 죽은 클래스**다(자기 자신 외 참조 0건, grep 확인). `WorkFlowExecutor`는 실제로는 `WorkFlowExecution`(record)의 `variables` 맵을 직접 쓰고 있어서, `WorkFlowContext`는 예전 설계의 잔재로 보인다.
- `StepFlow.error(...)`와 `StepFlow.waitingApproval(...)` 팩토리, 그리고 `StepStatus.ERROR`/`StepStatus.WAITING_APPROVAL` 값은 **한 번도 실제로 만들어지지 않는다**(grep 확인 — 정의부 `StepFlow.java`에서만 나타나고 호출부가 없음). `WorkFlowExecutor`는 예외가 나면 `StepFlow`를 거치지 않고 곧장 `persistFailed(...)`를 부르고, APPROVAL 대기도 `StepFlow`가 아니라 별도의 `StepRunResult.pending()` 경로로 처리한다. 즉 이 두 값은 껍데기만 있는 죽은 분기다.
- `StepPayload(primaryText, data)`는 `StepOutput`의 성공 케이스가 쓰는 필드 2개(`primaryText`, `data`)와 **완전히 같은 모양**이다. `AgentStepRunner.runStructuredAgent()`가 `StepPayload`로 받은 값을 그대로 `StepOutput.successWithData(payload.primaryText(), payload.data())`로 옮겨 담는 용도 외에는 쓰이지 않는다.
- `resources/workflows/**/*.yml` 14개 파일에서 `type:` 값으로 쓰이는 문자열은 `AGENT(13) / TOOL(10) / APPROVAL(5) / SUPERVISOR(1) / ROUTER(1)` 뿐이다. → **YAML의 `type:` 리터럴 값 자체는 절대 바꾸지 않는다**(바꾸면 14개 YAML을 전부 고쳐야 하고 실사용 대비 편익이 없음). enum 이름은 유지하고 **패키지 위치만** 옮긴다.
- 테스트 코드(`src/test`)가 존재하지 않는다 → 리팩토링 검증은 **컴파일 성공 + 애플리케이션 기동 후 §11(API 레퍼런스)의 대표 API 수동 호출**로 대신한다.

---

## 2. 패키지 구성 재정의 (요청 #1)

### 2.1 원칙

- `common.definition` = **YAML이 그대로 바인딩되는 레코드만** 남긴다 (`WorkFlowDefinition`, `StepDefinition`, `AgentDefinition`, `McpServerDefinition`).
- `common.consts` = **enum과 상수**만 모은다. 기존 `Constants.java` 옆에 `StepType`, `McpTransport`를 이동시킨다.
- `runtime.status` 패키지는 **폐지**한다. 안에 있던 10개 클래스를 "누가 실제 주인인가" 기준으로 재배치한다(§4에서 상세).
- 패키지 1레벨(`api/common/runtime/tools`)과 `runtime.agent/tool/step/workflow` 2레벨 구조는 그대로 둔다(사용자가 문제 삼지 않은 부분).

### 2.2 `common.definition` → `common.consts` 이동

| 클래스 | 이동 사유 |
|---|---|
| `StepType` (enum) | YAML `type:` 값에 바인딩되는 건 맞지만, 그 자체는 레코드가 아니라 열거값 — `McpTransport`와 같은 성격 |
| `McpTransport` (enum) | 이미 사용자가 예시로 지적한 그대로 |

이동은 단순 `package` 선언 변경 + import 경로 일괄 치환이며, **로직 변화 없음**. 영향받는 파일(현재 import 중인 곳): `StepDefinition`, `McpServerDefinition`, `AgentStepRunner`, `StepRunner`, `WorkFlowRegistry`, `WorkFlowExecutor`, `StepHistoryEntry`, `WorkFlowExecutionStore`, `ConfigCallLog`, `StepOutput`(→ 이후 `StepOutcome`), `RouteDecision`, `ConfigMcp` 등.

---

## 3. StepType 분류 재정의 (요청 #2)

### 3.1 방향

`AGENT/SUPERVISOR/ROUTER`는 LLM을 호출하고, `TOOL/APPROVAL`은 호출하지 않는다는 구분은 이미 코드 구조에 반쯤 반영되어 있다 — `WorkFlowExecutor.runnerFor()`가 정확히 이 경계로 3개 Runner를 나눠 호출한다(`AgentStepRunner` ↔ `AGENT/SUPERVISOR/ROUTER`, `ToolStepRunner` ↔ `TOOL`, `ApprovalStepRunner` ↔ `APPROVAL`). 따라서:

- **Runner 클래스를 추가로 쪼개지 않는다** — 이미 "호출 주체가 같은 것끼리 묶여있는" 구조라 실익이 없다.
- **YAML의 `type:` 리터럴(AGENT/TOOL/...)도 그대로 유지한다** — 14개 YAML 파일을 고칠 이유가 없다.
- 대신 `StepType` enum 자체에 **분류를 코드로 질의할 수 있는 방법**을 추가해서, 지금은 주석으로만 설명되어 있는 "이 타입이 LLM을 부르는지"를 코드가 선언적으로 답하게 한다.

### 3.2 구체안

`common.consts.StepType`에 아래와 같은 형태를 추가한다(이름은 예시, 확정 아님 — §9에서 사용자 확인):

```java
public enum StepType {

    AGENT(Kind.AGENT_CALL),
    SUPERVISOR(Kind.AGENT_CALL),
    ROUTER(Kind.AGENT_CALL),
    TOOL(Kind.DETERMINISTIC),
    APPROVAL(Kind.DETERMINISTIC);

    private final Kind kind;

    StepType(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    /** 이 StepType이 LLM을 호출하는 계열인지(AGENT_CALL), 아니면 결정적으로 처리되는 계열인지(DETERMINISTIC)를 나타냅니다. */
    public enum Kind {
        AGENT_CALL, DETERMINISTIC
    }
}
```

- `WorkFlowExecutor.runnerFor()`의 `switch` 자체는 그대로 두되(Runner는 여전히 타입별로 다르게 골라야 하므로), 주석에서 "이 세 개는 LLM 호출 계열"이라고 설명하던 부분을 `StepType.Kind.AGENT_CALL`을 언급하는 방식으로 바꿔서 **문서와 코드가 어긋나지 않게** 한다.
- `ConfigCallLog`나 향후 governance 코드가 "LLM을 부르는 step만 로깅/과금하고 싶다" 같은 요구가 생기면 `step.type().kind() == Kind.AGENT_CALL`로 바로 질의 가능해진다 — 지금 당장 그런 코드를 추가하지는 않는다(요청 범위 밖).

---

## 4. IN/OUT 재정의 (요청 #3)

### 4.1 현재 `runtime.status` 10개 클래스 진단

| 클래스 | 실제 역할 | 진짜 주인(호출 관계) |
|---|---|---|
| `StepInput` | `StepRunner.run()`의 입력 | `runtime.step` |
| `StepOutput` | `StepRunner.run()`의 출력(5개 필드, 타입마다 2~3개는 항상 null) | `runtime.step` |
| `StepResult` (enum) | `StepOutput.result()`의 성공/실패/대기 구분 | `StepOutput`에 종속 |
| `StepPayload` | structuredOutput=true인 AGENT step이 받는 LLM 응답 스키마 — `StepOutput`의 성공 케이스와 필드가 100% 동일 | `runtime.agent`(LLM 호출 스키마) |
| `Verdict` | SUPERVISOR가 받는 LLM 응답 스키마(pass/reason) | `runtime.agent` |
| `RouteDecision` | ROUTER가 받는 LLM 응답 스키마(route/reason) | `runtime.agent` |
| `ToolOutput` | TOOL이 받는 Tool 응답 스키마(success/message) | `runtime.tool` |
| `StepFlow` | `WorkFlowExecutor`가 스텝 하나를 끝내고 내리는 "다음엔 뭘 할지" 결정(3개 필드, 실제로는 2개 값(ERROR/WAITING_APPROVAL)이 죽어있음) | `runtime.workflow` |
| `StepStatus` (enum) | `StepFlow.status()`의 6가지 값 중 2개가 죽어있음(§1) | `StepFlow`에 종속 |
| `WorkFlowExecutionStatus` (enum) | `WorkFlowExecution`(영속 실행 1건)의 생명주기 상태 | `runtime.workflow.execution` |

### 4.2 재정의 방향

**(A) `StepInput`** — 그대로 `runtime.step`으로 이동. 내용 변경 없음.

**(B) `StepOutput` + `StepResult` + `StepPayload` → `StepOutcome` (신규 sealed interface, `runtime.step`)**

Java 21 `sealed interface`로 "성공/실패/대기/라우팅"을 각각 별도 타입으로 쪼개서, 지금처럼 한 record에 안 쓰는 필드가 null로 남는 상황을 없앤다.

```java
public sealed interface StepOutcome {

    /** 성공. structuredOutput=true AGENT step의 LLM 응답 스키마로도 그대로 재사용한다(기존 StepPayload 폐지). */
    record Success(String primaryText, Map<String, Object> data) implements StepOutcome {}

    /** ROUTER 전용 성공 - 어느 route를 골랐는지 함께 담는다. */
    record Routed(String primaryText, Map<String, Object> data, String route) implements StepOutcome {}

    /** 실패. */
    record Failure(String primaryText, String reason) implements StepOutcome {}

    /** APPROVAL 대기 중(ApprovalStepRunner 전용). */
    record Pending() implements StepOutcome {}
}
```

- `Success`는 `AgentExecutor.callForEntity(..., StepOutcome.Success.class)`로 **LLM 구조화 응답을 직접 받는 타입으로도 재사용**한다 → `StepPayload` 클래스 자체를 삭제할 수 있다(§4.1의 "필드 100% 동일" 진단 근거).
- `WorkFlowExecutor`/`AgentStepRunner`/`ToolStepRunner`/`ApprovalStepRunner`의 `output.result() == StepResult.XXX` 분기는 `switch` 패턴 매칭(`switch (output) { case StepOutcome.Success s -> ...; case StepOutcome.Failure f -> ...; ... }`)으로 바뀐다. 람다가 아니라 표준 `switch`이므로 "람다 지양" 방침에 위배되지 않는다.
- `StepResult` enum은 삭제(값 자체가 sealed interface의 각 record로 대체됨).

**(C) `StepFlow` + `StepStatus` → `WorkflowTransition` (신규 sealed interface, `runtime.workflow`)**

§1에서 확인했듯 `ERROR`/`WAITING_APPROVAL`은 실사용이 전혀 없으므로 **가져오지 않는다**(죽은 코드를 새 구조로 옮기지 않고 이번에 정리).

```java
public sealed interface WorkflowTransition {

    record NextStep(String stepId) implements WorkflowTransition {}

    record Loop(String stepId) implements WorkflowTransition {}

    record Done(String message) implements WorkflowTransition {}

    record Failed(String message) implements WorkflowTransition {}
}
```

- `WorkFlowExecutor.decideTransition()` / `decideRouterTransition()` / `resolveNextIdToFlow()`가 만들어 돌려주는 값과 `run()`의 `switch`를 이 4가지 케이스로 맞춘다.
- 이름을 `Success`/`Fail`에서 `Done`/`Failed`로 바꾼 이유: `StepOutcome.Success`(스텝 하나의 성공)와 `WorkflowTransition`(Workflow 전체의 종료)이 같은 "Success"라는 이름을 쓰면 로그나 import에서 헷갈리기 쉽다. `WorkFlowExecutionStatus`가 이미 `DONE`/`FAILED`라는 이름을 쓰고 있으므로 거기에 맞춘다(§4.2(D) 참고 — 이름 통일).

**(D) `ToolOutput` → `runtime.tool`로 이동 (이름은 유지 권장)**

`Verdict`/`RouteDecision`과 성격이 같은 "구조화 응답 스키마" 계열이지만, LLM이 아니라 Tool이 만드는 값이라는 차이가 있어 의미상 분리 유지. 이동만 하고 이름은 바꾸지 않는 것을 기본안으로 제안한다(§9에 "ToolOutcome으로 개명" 옵션을 별도 확인 항목으로 남김 — 개명 시 `ToolStepRunner`, `tools.sql.SqlSyntaxTool`, `tools.jenkins.JenkinsTriggerBuildTool` 등 7개 파일 영향).

**(E) `Verdict`, `RouteDecision` → `runtime.agent`로 이동**

`AgentExecutor.callForVerdict()`/`callForEntity()`가 만드는 "LLM에게 강제하는 JSON 스키마"라는 같은 역할이므로 `AgentExecutor`와 같은 패키지에 둔다. 이름/필드 변경 없음.

**(F) `WorkFlowExecutionStatus` → `runtime.workflow.execution`으로 이동**

`WorkFlowExecution`(같은 패키지의 record)의 `status` 필드 전용 enum이므로 실제 주인 옆으로 옮긴다. 각 값(RUNNING/WAITING_APPROVAL/DONE/FAILED/CANCELLED)은 전부 실사용 중이므로(§1과 달리 죽은 값 없음) 그대로 유지, sealed interface로 바꾸지 않는다(상태별로 별도 필드가 필요 없고 `WorkFlowExecution` record가 이미 모든 필드를 갖고 있어 굳이 쪼갤 이유가 없음).

### 4.3 `WorkFlowContext` 삭제 제안

§1에서 확인한 대로 완전히 죽은 클래스다. `runtime.status`를 정리하는 김에 **삭제**를 제안한다(§9에서 최종 확인).

---

## 5. 최종 패키지 트리 (제안)

```
net.dstone.ai
├── api/                                (변경 없음)
├── common/
│   ├── annotation/                     (변경 없음)
│   ├── config/                         (변경 없음)
│   ├── consts/
│   │     Constants.java
│   │     StepType.java                 ← common.definition에서 이동 + Kind 분류 추가
│   │     McpTransport.java             ← common.definition에서 이동
│   ├── definition/                     ← YAML 바인딩 레코드만
│   │     WorkFlowDefinition.java
│   │     StepDefinition.java
│   │     AgentDefinition.java
│   │     McpServerDefinition.java
│   ├── exec/                           (변경 없음)
│   ├── loader/                         (변경 없음)
│   ├── rag/                            (변경 없음)
│   ├── registry/                       (변경 없음)
│   ├── security/                       (변경 없음)
│   └── session/                        (변경 없음)
├── runtime/
│   ├── agent/
│   │     AgentExecutor.java
│   │     Verdict.java                  ← runtime.status에서 이동
│   │     RouteDecision.java            ← runtime.status에서 이동
│   ├── tool/
│   │     ToolExecutor.java
│   │     ToolOutput.java               ← runtime.status에서 이동
│   ├── step/
│   │     StepRunner.java
│   │     StepInput.java                ← runtime.status에서 이동
│   │     StepOutcome.java              ← 신규 sealed interface (StepOutput+StepResult+StepPayload 대체)
│   │     AgentStepRunner.java
│   │     ToolStepRunner.java
│   │     ApprovalStepRunner.java
│   └── workflow/
│        WorkFlowExecutor.java
│        WorkflowTransition.java        ← 신규 sealed interface (StepFlow+StepStatus 대체)
│        [WorkFlowContext.java 삭제]
│        └── execution/
│             WorkFlowExecution.java
│             WorkFlowExecutionStore.java
│             StepHistoryEntry.java
│             WorkFlowExecutionStatus.java  ← runtime.status에서 이동
└── tools/                              (변경 없음)
```

`runtime.status` 패키지는 완전히 없어진다(디렉토리 삭제).

---

## 6. 진행 순서 (매 단계 컴파일 성공 유지)

1. **낮은 위험 이동부터**: `McpTransport`, `StepType`(Kind 없이 우선 이동만) → `common.consts`. import 일괄 치환.
2. `WorkFlowExecutionStatus` → `runtime.workflow.execution` 이동.
3. `Verdict`, `RouteDecision` → `runtime.agent` 이동. `ToolOutput` → `runtime.tool` 이동.
4. `StepInput` → `runtime.step` 이동.
5. `StepType`에 `Kind` 분류 추가(§3.2), `WorkFlowExecutor.runnerFor()` 주석만 갱신(로직 불변).
6. **가장 손이 많이 가는 단계**: `StepOutcome` sealed interface 신설 → `StepOutput`/`StepResult`/`StepPayload` 대체.
   - `AgentStepRunner`(4개 private 메서드 전부), `ToolStepRunner`, `ApprovalStepRunner`, `WorkFlowExecutor`(`runOne`/`runForEach`/`decideTransition` 등) 수정.
7. `WorkflowTransition` sealed interface 신설 → `StepFlow`/`StepStatus` 대체(죽은 ERROR/WAITING_APPROVAL 값은 이관하지 않음).
   - `WorkFlowExecutor`의 `decideTransition`/`decideRouterTransition`/`resolveNextIdToFlow`/`run()`의 `switch` 수정.
8. `WorkFlowContext.java` 삭제.
9. (선택, §9 확인 후) `ToolOutput` → `ToolOutcome` 개명.
10. `runtime.status` 빈 디렉토리 제거.
11. `mvn -pl dstone-ai-engine -am compile`로 전체 컴파일 확인.
12. 로컬 기동 후 §11 대표 API 수동 호출로 회귀 확인:
    - `POST /api/ai/chat` (일반 질문 1건)
    - `POST /api/ai/workflow/{id}/execute` — AGENT/TOOL이 섞인 sample Workflow 1개
    - `POST /api/ai/workflow/{id}/execute` — ROUTER가 포함된 Workflow 1개(분기 확인용)
    - `POST /api/ai/workflow/{id}/execute` — APPROVAL이 포함된 Workflow 1개(WAITING_APPROVAL → decision API로 재개까지)

---

## 7. 문서 갱신 (CLAUDE.md 지침: "리소스가 바뀌면 문서도 확인/재작성")

- **`docs/09.dstone-ai-engine.md`**: §3(패키지 구조), §6(Step IN/OUT 완전 참조 — `StepInput`/`StepOutput`/`StepPayload`/`ToolOutput`/`Verdict`/`RouteDecision` 언급 전체)을 새 타입명/패키지로 재작성. §15(이전 구조에서 달라진 점)에 `2026-09-23` 항목 추가.
- **`/app/dstone/CLAUDE.md`**: `dstone-ai-engine` 섹션의 패키지 표에서 `common.definition` 행의 "`StepType`" 언급을 `common.consts`로 수정, `common.consts` 행 신설, `runtime.status`를 언급하는 부분이 있다면 제거.
- 이 변경들은 **구현 단계에서** 실제 코드가 확정된 뒤에 반영한다(지금은 계획 단계).

---

## 8. 영향받는 파일 목록 (grep 기준, 최종 개수는 구현 시 재확인)

- `StepType` import: `StepDefinition`, `McpServerDefinition`(간접), `AgentStepRunner`, `StepRunner`, `WorkFlowRegistry`, `WorkFlowExecutor`, `StepHistoryEntry`, `WorkFlowExecutionStore`, `ConfigCallLog`
- `runtime.status.*` import 전체: 위 §1에서 열거한 10개 클래스를 import하는 모든 파일(`AgentExecutor`, `AgentStepRunner`, `ToolStepRunner`, `ApprovalStepRunner`, `StepRunner`, `WorkFlowExecutor`, `WorkFlowExecution`, `ConfigCallLog` 등)
- 테스트 코드 없음 → 컴파일 + 수동 API 호출로 검증(§6-12)

---

## 9. 사용자 확인이 필요한 결정 사항

1. **`StepOutcome`/`WorkflowTransition` sealed interface 도입** — 이번 계획의 핵심이자 가장 큰 변경. 다른 대안(필드만 정리한 record 유지, 또는 그대로 두고 이름만 통일)보다 손이 많이 가지만 "안 쓰는 필드가 null로 남는" 문제와 "죽은 ERROR/WAITING_APPROVAL 분기"를 근본적으로 없앨 수 있음. 승인 여부?
2. **`WorkFlowContext.java` 삭제** — 완전히 죽은 코드로 확인됨. 삭제해도 되는지, 아니면 향후 계획이 있어 남겨둬야 하는지?
3. **`ToolOutput` → `ToolOutcome` 개명 여부** — 이름 통일성은 올라가지만 Tool 구현체 쪽(SqlSyntaxTool, JenkinsTriggerBuildTool 등)까지 건드려야 함. 이번에 같이 할지, 다음으로 미룰지?
4. **`StepType.Kind` 분류의 실제 활용처** — 이번엔 "질의 가능하게만" 만들어 두고 실제로 그 값을 쓰는 새 로직(예: governance/로깅 필터링)은 추가하지 않는 것으로 이해했는데 맞는지?
5. **진행 방식** — 이 문서는 계획이며, 실제 코드 변경(§6의 12단계)은 사용자의 별도 지시가 있을 때 시작. 한 번에 다 진행할지, 단계별로 나눠서 커밋할지?
