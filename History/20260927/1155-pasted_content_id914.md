# pasted_content_id914

> 생성일시: 2026-09-27 11:55

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
