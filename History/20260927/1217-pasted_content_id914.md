# pasted_content_id914

> 생성일시: 2026-09-27 12:17

## 질문

<pasted_content id="914e">
net.dstone.ai.common.consts.StepType 5개별로 
    AGENT
    SUPERVISOR
    APPROVAL
    ROUTER
    TOOL
아래의 StepDefinition 을 나누어서 만드는 것은 어떨까?
net.dstone.ai.common.definition.StepDefinition 은 혹시 상속/혹은 implements 를 통해서 공통 되는 부분을 묶어서 관리가 가능할까?
</pasted_content id="914e">

---

## 답변

나눌 수 있고, 저도 나누는 쪽을 추천해요. 다만 "상속으로 필드를 공유"하는 건 record로는 안 되고, **interface로 공통 접근자를 묶는 방식**이 돼요. 아래는 현재 코드(`StepDefinition`, `WorkFlowRegistry.validateStepShape`, `WorkFlowExecutor`, 각 Runner)를 확인하고 정리한 내용이에요. 아직 코드는 고치지 않았어요.

## 1. 지금 구조의 문제

현재 `StepDefinition`은 5가지 타입의 필드를 전부 담은 레코드 하나라서 아래 문제가 있어요.
- `input`이 `Object` 타입이에요. AGENT 계열은 `String`, TOOL은 `Map`이라서 registry와 executor에서 `instanceof`로 검사하거나 `toString()`으로 바꿔 써요.
- `StepType` javadoc의 ✅/⭕/❌/— 표를 `validateStepShape`가 손으로 하나씩 검사해요. 예: "schema는 AGENT만", "parse·pattern은 TOOL만", "forEach는 APPROVAL·ROUTER 금지".
- Runner와 Executor 곳곳에 `step.type() == StepType.XXX` 분기가 흩어져 있어요.

타입마다 전용 record가 있으면 이런 규칙 상당수가 **컴파일 타임의 타입**으로 옮겨가요.

## 2. 상속으로 묶을 수 있나?

- **record는 `extends`를 못 해요.** 모든 record는 이미 `java.lang.Record`를 상속하거든요. 그래서 공통 필드 선언 자체를 부모 클래스로 올릴 수는 없어요.
- **`implements`는 돼요.** 공통 부분은 "필드"가 아니라 "접근자 계약"으로 interface에 묶으면 돼요. record 컴포넌트 이름이 interface 메서드와 같으면 자동으로 구현돼요.
- 필드까지 공유하려면 abstract class 계층으로 가야 해요. 하지만 불변성·간결함을 잃고 Jackson 바인딩도 번거로워져서 추천하지 않아요. 공통부를 `flow: {onSuccess, onFailure}` 같은 중첩 record로 빼는 방법도 있지만, YAML 모양이 바뀌어서 이것도 추천하지 않아요.

## 3. 추천 구조

현재 필드 사용표를 그대로 옮기면 공통 축이 세 개 나와요.

| 축 | 해당 타입 |
|---|---|
| `id`, `type`, `onFailure` (모든 타입) | 5개 전부 |
| `ref` + 문자열 `input` (LLM 호출) | AGENT / SUPERVISOR / ROUTER |
| `forEach` / `itemVariable` (반복 가능) | AGENT / SUPERVISOR / TOOL |
| `onSuccess` (성공·실패 두 갈래 분기) | ROUTER를 뺀 4개 |

```java
// 모든 step 공통
public sealed interface StepDefinition
	permits AgentStep, SupervisorStep, RouterStep, ToolStep, ApprovalStep {
	String id();
	StepType type();
	String onFailure();
}

// LLM을 부르는 step (Kind.AGENT_CALL과 같은 묶음)
public interface AgentCallStep extends StepDefinition {
	String ref();
	String input();      // 문자열로 고정 → instanceof 검사 불필요
}

// forEach로 병렬 반복할 수 있는 step
public interface ForEachStep {
	String forEach();
	String itemVariable();
}

// 성공/실패 두 갈래로 분기하는 step (ROUTER 제외)
public interface PassFailStep {
	String onSuccess();
}
```

```java
public record AgentStep(String id, String ref, String input, AgentOutput output,
		String onSuccess, String onFailure, String forEach, String itemVariable)
	implements AgentCallStep, ForEachStep, PassFailStep {
	public StepType type() { return StepType.AGENT; }
}

public record SupervisorStep(String id, String ref, String input,
		String onSuccess, String onFailure, String forEach, String itemVariable)
	implements AgentCallStep, ForEachStep, PassFailStep { ... }

public record RouterStep(String id, String ref, String input,
		Map<String, String> routes, String onFailure)
	implements AgentCallStep { ... }

public record ToolStep(String id, String ref, Map<String, Object> input, ToolOutput output,
		String onSuccess, String onFailure, String forEach, String itemVariable)
	implements StepDefinition, ForEachStep, PassFailStep { ... }

public record ApprovalStep(String id, String approverRole, String onSuccess, String onFailure)
	implements StepDefinition, PassFailStep { ... }
```

`StepOutputDefinition`도 같이 나누는 게 자연스러워요. AGENT용 `AgentOutput(schema)`와 TOOL용 `ToolOutput(parse, pattern)`로 나누고, 나머지 세 타입은 output 필드 자체를 없애요. YAML 키는 그대로 `output:`이에요.

**YAML은 한 줄도 바뀌지 않아요.** `YamlDefinitionLoader`가 Jackson `convertValue`로 바인딩하니까 기존 `type:` 값을 구분자로 쓰면 돼요.

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = AgentStep.class,      name = "AGENT"),
	@JsonSubTypes.Type(value = SupervisorStep.class, name = "SUPERVISOR"),
	...
})
public sealed interface StepDefinition ...
```

## 4. 얻는 것

- `validateStepShape`의 input·output·forEach 모양 검사 대부분이 사라져요. 필드가 아예 없는 타입에 그 키를 쓰면 Jackson이 바인딩 단계에서 막아요. loader가 기본 `new ObjectMapper()`를 써서 `FAIL_ON_UNKNOWN_PROPERTIES=true`이기 때문이에요.
- Runner가 구체 타입을 받아요. `AgentStepRunner`는 `AgentCallStep`, `ToolStepRunner`는 `ToolStep`을 받으니까 `definition.output().schema()` 같은 null 탐색이 줄어요.
- sealed interface라서 `WorkFlowExecutor.runnerFor`나 `outputKeysOf` 같은 곳의 `switch`가 빠짐없이 모든 타입을 다루는지 컴파일러가 확인해요.
- `StepType` javadoc의 사용항목 표가 record 선언 자체로 대체돼요. 문서와 코드가 어긋날 여지가 없어져요.

## 5. 주의할 점

1. **"— 무시" 항목이 "에러"로 바뀌어요.** 지금은 TOOL step에 `routes:`나 `approverRole:`을 적어도 조용히 무시돼요. 분리하면 모르는 키라서 로딩이 실패해요. 더 엄격해지는 거라 저는 오히려 좋다고 보지만, 현재 샘플 YAML에 그런 잉여 키가 있는지는 확인이 필요해요.
2. **에러 메시지 품질이 떨어질 수 있어요.** 지금은 "TOOL step의 input은 맵이어야 합니다" 같은 한국어 메시지가 나와요. Jackson에 맡기면 `MismatchedInputException` 같은 영문 예외가 나와요. loader에서 이 예외를 잡아 "workflow X / step Y: …" 형식의 한국어 메시지로 바꿔주는 처리가 필요해요.
3. **필드 선언 중복은 남아요.** `id`, `onFailure`, `forEach` 등이 record 5개에 반복 선언돼요. 계약은 interface로 통일되지만 선언 자체는 record의 한계라서 피할 수 없어요.
4. **바뀌는 파일이 10여 개예요.**
   - `WorkFlowRegistry`, `WorkFlowExecutor`
   - Runner 3개와 `StepRunner`
   - `StepOutputDefinition`, `ConfigCallLog`, `StepHistoryEntry` 생성부
   - `docs/09.dstone-ai-engine.md` §7
5. 컨벤션에 맞춰 `switch`는 화살표(`->`) 없이 콜론(`case X:`) 스타일로 쓸게요. 지금 registry와 executor에는 `case ... ->`가 조금 섞여 있어요.

원하시면 위 구조로 리팩터링을 진행할게요. 진행 전에 두 가지만 정해주세요.
- 무시(—) 항목을 에러로 바꿔도 될지
- `StepOutputDefinition`도 같이 나눌지

---

## 질문

<pasted_content id="914e">
  - 무시(—) 항목을 에러로 바꿔도 될지 
  ==>> 바꿔.
  - StepOutputDefinition도 같이 나눌지
  ==>> 응 바꿔줘.
다만, net.dstone.ai.common.definition 단일패키지 안에 여러 성격의 컴퍼넌트가 많아지는 것 같아서...
혹시 net.dstone.ai.common.definition 의 서브패키지를 나눈다면 어떻게 분류하는게 좋을 지 의견을 말해줘. 이 부분 정리된 후에 리펙터링 진행하자.
</pasted_content id="914e">

---

## 답변

제 추천은 **YAML 파일 종류(= loader가 읽는 디렉터리)를 기준으로 나누는 것**이에요. `common.definition`의 record들은 결국 "YAML 한 파일이 바인딩되는 모양"이라서, `resources/workflows`, `agents`, `mcp`와 1:1로 맞추면 어디에 뭐가 있는지 바로 찾을 수 있어요.

## 추천 구조

```
common.definition
├── FieldDefinition                 ← 여러 곳이 같이 쓰는 타입이라 루트에 둠
├── workflow
│   ├── WorkFlowDefinition
│   └── step
│       ├── StepDefinition          (sealed interface — 모든 step 공통)
│       ├── AgentCallStep           (interface — AGENT/SUPERVISOR/ROUTER)
│       ├── ForEachStep             (interface — AGENT/SUPERVISOR/TOOL)
│       ├── PassFailStep            (interface — ROUTER 제외)
│       ├── AgentStep / SupervisorStep / RouterStep / ToolStep / ApprovalStep
│       └── AgentOutput / ToolOutput
├── agent
│   └── AgentDefinition
└── mcp
    └── McpServerDefinition
```

## 이렇게 나누는 이유

1. **step 관련 파일은 한 패키지에 모아야 해요.** 우리 프로젝트는 모듈(`module-info.java`) 없이 돌아가요. 이 경우 Java 규칙상 sealed interface와 거기에 `permits`로 적은 record들은 같은 패키지에 있어야 해요. 그래서 "interface는 `step.contract`, record는 `step.impl`"처럼 쪼갤 수가 없어요. `workflow.step` 하나에 계약 4개, 구현 5개, output 2개, 합쳐서 11개가 모여요.
2. **`step`을 `workflow` 밑에 둬요.** step은 Workflow 안에서만 존재하고 단독 YAML 파일이 없어요. 패키지 경로가 `workflows/*.yml` 안의 `steps:` 중첩 구조를 그대로 따라가요.
3. **`AgentOutput`과 `ToolOutput`은 `step`에 같이 둬요.** 각각 AgentStep, ToolStep에서만 쓰여서 `step.output`으로 따로 빼기엔 파일이 2개뿐이에요. 따로 두면 오히려 찾기 번거로워요.
4. **`FieldDefinition`은 루트에 남겨요.** 이 타입은 세 곳이 같이 써요.
   - Workflow의 `inputs`
   - AgentOutput의 `schema`
   - `common.schema.FieldTypes`와 `runtime.agent.*`

   어느 한 하위 패키지에 넣으면 다른 쪽이 그 패키지를 끌어다 쓰는 모양이 돼서, 공용 타입으로 루트에 두는 게 자연스러워요.
5. **`agent`와 `mcp`는 지금 파일이 하나씩뿐이에요.** 그래도 registry(`AgentRegistry`, `McpServerRegistry`)와 YAML 디렉터리가 이미 이 단위로 나뉘어 있어서 대칭을 맞추는 게 좋다고 봤어요. `AgentDefinition`은 rag 관련 필드 4개를 나중에 중첩 record로 묶을 여지도 있어요.
   - 더 적게 쪼개고 싶으시면, 이 둘은 루트에 두고 `workflow`와 `workflow.step`만 만드는 방법도 있어요. 다만 그러면 루트에 성격이 다른 파일 3개가 섞여서 저는 위 구조를 추천해요.

## 그대로 두는 것

- **`StepType`, `ToolParse` enum은 `common.consts`에 그대로 둬요.** "enum은 `common.consts`에 둔다"는 게 CLAUDE.md에 적힌 기존 규칙이에요. `ToolParse`는 이제 `ToolOutput`만 쓰게 돼서 step 패키지로 옮길 수도 있지만, 규칙을 깨면서까지 옮길 이점은 크지 않다고 봤어요.
- **`StepOutputDefinition`은 없애요.** `AgentOutput`과 `ToolOutput`으로 대체돼요.

이 구조로 괜찮으시면 패키지 이동과 Step 분리 리팩터링을 한 번에 진행할게요. CLAUDE.md와 `docs/09.dstone-ai-engine.md`의 패키지 표도 함께 고칠게요.

---

## 질문

응 이렇게 진행하자. 주석의 내용들도 모두 반영해줘.

---

## 답변

Now the step package — the sealed interface first.

---

## 답변

Now updating `WorkFlowRegistry`.

---

## 답변

Now `WorkFlowExecutor`.

---

## 답변

Now the runners and `StepRunner` (made generic so each runner receives its concrete step type).

---

## 답변

Now the `StepType` javadoc tables (the "— 무시" semantics no longer exist).

---

## 답변

Compiles cleanly. Now I'll verify that all real YAML files still load and pass registry validation, plus that the new error paths fire, using a scratch harness.

---

## 답변

All 15 real workflows load. Now negative cases for the new strictness and error messages:

---

## 답변

Everything is caught with clear messages. One polish: "LinkedHashMap 모양" should read naturally.

---

## 답변

Validation is solid. Next, a runtime smoke test of `WorkFlowExecutor` (typed dispatch, forEach, ROUTER routing, approval pause) with stubbed tool/agent calls — let me check what needs stubbing.

---

## 답변

Runtime smoke test passes end-to-end: forEach, ROUTER routing, AGENT schema, SUPERVISOR, APPROVAL pause/resume, and history `ref` (null for APPROVAL). Now the docs.

---

## 답변

Now editing `docs/09.dstone-ai-engine.md`.

---

## 답변

Now the workflow-yml-guide.md updates.

---

## 답변

All docs updated. Final full build check and diff summary:

---

## 답변

리팩터링을 끝냈어요. `dstone-ai-engine` 전체 빌드(`clean package`)가 성공했고, 실제 워크플로우 YAML 15개도 수정 없이 모두 로딩·검증을 통과해요. 커밋은 하지 않았어요.

## 1. 패키지 구조

```
common.definition
├── FieldDefinition
├── workflow/WorkFlowDefinition
├── workflow/step/
│   ├── StepDefinition   (sealed interface: id, type, onFailure + type 값 → record 매핑)
│   ├── AgentCallStep    (ref, 문자열 input)         AGENT / SUPERVISOR / ROUTER
│   ├── ForEachStep      (forEach, itemVariable)     AGENT / SUPERVISOR / TOOL
│   ├── PassFailStep     (onSuccess)                 ROUTER 제외
│   ├── AgentStep / SupervisorStep / RouterStep / ToolStep / ApprovalStep
│   └── AgentOutput(schema) / ToolOutput(parse, pattern)
├── agent/AgentDefinition
└── mcp/McpServerDefinition
```

`StepOutputDefinition`은 없앴어요.

## 2. 코드 변경

- **`StepRunner`**: 제네릭 `StepRunner<T>`로 바꿨어요. 각 러너가 자기 step 타입을 직접 받아서 `type()` 비교나 형변환이 없어졌어요.
- **`WorkFlowExecutor`**: `runnerFor(StepType)` 대신 `runStep()`이 step 타입별로 러너를 골라요. 새 step 종류를 추가하고 여기를 빠뜨리면 컴파일 오류가 나요. 손댄 메서드에 있던 람다 하나도 익명 클래스로 바꿨어요.
- **`WorkFlowRegistry`**: `instanceof`로 하던 input·forEach 모양 검사를 지웠어요. 대신 YAML 키만으로는 알 수 없는 것만 검사해요.
  - `ref`가 비었는지 (새로 추가)
  - schema 필드가 1개 이상 있는지, 타입 이름이 올바른지
  - `pattern`이 `parse: lines`와 함께 쓰였는지, 정규식이 올바른지
  - routes가 1개 이상 있는지
- **`YamlDefinitionLoader`**: Jackson 영문 오류를 YAML 경로가 담긴 한국어 메시지로 바꿔줘요. 예:
  ```
  [workflow.steps[0].routes] 'routes'는 이 자리(ToolStep)에서 쓸 수 없는 키입니다 (… 쓸 수 있는 키 = [output, ref, onSuccess, …]).
  [workflow.steps[0]] step의 type이 없거나 올바르지 않습니다(적은 값 = FOO, 쓸 수 있는 값 = AGENT, …).
  [workflow.steps[0].input] 값의 모양이 맞지 않습니다(문자열이어야 합니다 …).
  ```

## 3. 동작이 바뀐 점

- **"무시"되던 키가 이제 기동 실패예요.** 예전엔 조용히 무시되던 조합들이에요. 지금 샘플 YAML에는 해당하는 게 없어요.
  - TOOL에 `routes`
  - APPROVAL에 `ref`
  - ROUTER에 `onSuccess`
  - `forEach` 없는 step 종류(APPROVAL, ROUTER)에 `itemVariable`
- **AGENT, SUPERVISOR, ROUTER, TOOL에서 `ref`를 빠뜨리면 기동이 실패해요.** 예전엔 실행 중에야 드러났어요.

## 4. 검증

- **기동 오류 케이스 10개**: 잘못된 키, `type` 누락·오류, input 모양, `ref` 누락, output 키 참조, 잘못된 parse 값을 넣어 봤어요. 전부 기대한 메시지로 막혔어요.
- **실행 흐름**: Tool과 Agent 호출만 가짜로 바꾼 스크래치 코드로 `WorkFlowExecutor`를 돌렸어요. 아래가 모두 정상 동작했어요.
  - TOOL(lines) → TOOL forEach → ROUTER 분기 → AGENT schema → SUPERVISOR
  - APPROVAL 대기 → 승인 → 완료(DONE)
  - APPROVAL의 실행 이력에 `ref=null`로 남는 것까지 확인했어요.
- 실제 LLM을 호출하는 서버 기동 테스트(kind 배포)는 하지 않았어요.

## 5. 주석과 문서

요청하신 대로 주석과 문서에도 모두 반영했어요.
- **주석**: 새 record와 interface 각각에 YAML 예시와 필드 설명을 넣었어요. `StepType` javadoc의 ✅⭕❌— 표는 "필수/선택 키 + record 이름"으로 바꿨어요. `Constants`, `RouteDecision`, `StepOutcome`, `AgentRegistry`, DTO에 남아 있던 예전 클래스 이름도 고쳤어요.
- **문서**:
  - `docs/09.dstone-ai-engine.md`: 패키지 트리, 기동 검증 설명, §7.3 표, 러너 선택 다이어그램
  - `workflow-yml-guide.md`: 표와 각 항목별 "경우 → 결과" 오류 메시지
  - `agent-yml-guide.md`, `mcp-yml-guide.md`: 패키지 경로
  - `CLAUDE.md`: 패키지 표

`docs/temp/`의 예전 설계 기록은 이력이라 그대로 뒀어요.

---

## 질문

net.dstone.ai.runtime.step 에 있는 StepRunner들은 다른 runtime 에 들어있는 Executor 들과 명명이 달라.
이 것도 맞추고 싶어. StepRunner들도 StepExecutor로 이름을 수정하는게 어떨까?

---

## 답변

좋은 방향이라고 봐요. `runtime` 아래 `AgentExecutor`, `ToolExecutor`, `WorkFlowExecutor`와 이름 규칙이 맞춰지고, "step 하나를 실행하는 담당"이라는 뜻도 그대로 전달돼요. `AgentExecutor`와 `AgentStepExecutor`처럼 이름이 비슷해지는 점만 주석으로 역할을 분명히 해 둘게요. 메서드 이름 `run()`은 `WorkFlowExecutor.run()`과 같아서 그대로 둬요. 바로 진행할게요.

---

## 답변

`History/`는 대화 기록이라 건드리지 않고, 소스·YAML 주석·docs/09·CLAUDE.md만 바꿀게요.

---

## 답변

동작은 그대로예요. `AgentExecutor`/`ToolExecutor`와 헷갈리지 않도록 인터페이스 주석에 역할 구분을 한 문단 넣고, 빌드와 스모크 테스트를 다시 돌릴게요.

---

## 답변

`StepRunner`들을 `StepExecutor`로 바꿨어요. 동작은 그대로이고, 빌드(`clean package`)가 성공했어요. 실제 워크플로우 YAML 15개도 모두 로딩을 통과해요.

## 바뀐 이름

| 이전 | 이후 |
|---|---|
| `StepRunner<T>` | `StepExecutor<T>` |
| `AgentStepRunner` | `AgentStepExecutor` |
| `ToolStepRunner` | `ToolStepExecutor` |
| `ApprovalStepRunner` | `ApprovalStepExecutor` |

- **`WorkFlowExecutor`**: 필드 이름도 `agentStepExecutor`, `toolStepExecutor`, `approvalStepExecutor`로 바꿨어요.
- **`ConfigCallLog`**: AOP 대상을 `StepExecutor+.run(..)`으로 바꿨어요. 그래서 로그에는 `[StepRunner - ...]` 대신 `[StepExecutor - ...]`로 찍혀요. 로그를 grep하는 곳이 있다면 같이 바꿔야 해요.
- **메서드 이름 `run()`**: 그대로 뒀어요. `WorkFlowExecutor.run()`과 맞춘 거예요.

## 이름이 비슷한 클래스와의 구분

`AgentExecutor`와 `AgentStepExecutor`처럼 이름이 비슷해져서, `StepExecutor` 주석에 둘의 역할 차이를 적어 뒀어요.
- **`StepExecutor`**: Workflow의 step 하나를 맡아요. step 정의를 읽고, 성공/실패를 판정하고, output을 정리해요.
- **`AgentExecutor` / `ToolExecutor`**: LLM 호출 한 건, Tool 호출 한 건을 실제로 하는 유일한 지점이에요.
- 그래서 `AgentStepExecutor`는 `AgentExecutor`를, `ToolStepExecutor`는 `ToolExecutor`를 불러서 일을 시켜요.

## 함께 고친 곳

- 소스 주석, 샘플 YAML 주석, `workflow-yml-guide.md`, `agent-yml-guide.md`, `docs/09.dstone-ai-engine.md`, `CLAUDE.md`를 모두 새 이름으로 바꿨어요.
- 주석에 쓰던 "러너"라는 말도 "실행기"로 바꿨어요.
- `History/` 대화 기록과 `docs/temp/` 예전 설계 기록은 건드리지 않았어요.
- 공용 프로세스 실행 유틸 `ExternalProcessRunner`는 이름이 비슷하지만 다른 클래스라 그대로 뒀어요.

## 확인한 것

- 스텁을 쓴 실행 테스트를 다시 돌렸어요. TOOL, forEach, ROUTER, AGENT(schema), SUPERVISOR를 거쳐 APPROVAL에서 대기했다가, 승인 후 완료(DONE)까지 이전과 똑같이 동작했어요.

파일 이름은 `git mv`로 바꿔서, 이름 변경 4건은 index에 스테이징된 상태예요. 커밋은 하지 않았어요.

---

## 질문

커밋 및 푸시까지 해줘.

---

## 답변

커밋하고 `origin/main`에 푸시까지 끝냈어요. 로컬과 원격이 같은 상태예요.

- **`360ebda`** `dstone-ai-engine: StepRunner를 StepExecutor로 이름 변경`: 이번에 커밋한 부분이에요. 클래스 이름과 모든 참조, 로그 pointcut, 주석, 샘플 YAML, 가이드 문서, `docs/09`, `CLAUDE.md` 변경이 들어 있어요.
- **`98cc6c5`**: 히스토리 자동 커밋이에요. `*StepRunner.java` → `*StepExecutor.java` 파일 이름 변경 4건이 내용 없이 여기에 먼저 들어가 있었어요. 그래서 이 커밋만 체크아웃하면 컴파일되지 않고, `360ebda`부터 정상이에요.
- **`14d9aff`**: 그 전 단계(Step 분리, 서브패키지 정리)예요. 메시지가 `.`인 커밋으로 이미 들어가 있었어요.
