# localcommandcaveatTh

> 생성일시: 2026-11-20 08:44

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-ai-engine 내에서 모든 tool 호출은 net.dstone.ai.runtime.tool.ToolExecutor.call(String, StepRef, String, String) 이 메소드를 거치는게 맞지?

---

## 답변

아니요. `ToolExecutor.call(String, StepRef, String, String)`은 **Workflow의 TOOL step에서만** 거치는 통로입니다. 클래스 주석(`ToolExecutor.java:17`)의 "유일한 통로"도 TOOL step에 한정된 말입니다.

## 호출 경로 두 가지

**1. TOOL step (LLM 없이 직접 호출)**
`ToolStepExecutor.java:52` → `ToolExecutor.call()` → `ConfigTool.findByName()`으로 ToolCallback을 찾고 caller 화이트리스트를 검사 → `callback.call(json, toolContext)`.
코드 전체에서 `ToolExecutor`를 부르는 곳은 여기 하나뿐입니다.

**2. AGENT step과 `/chat` (LLM이 Tool을 골라 부름)**
`AgentExecutor.buildSpec()`(`AgentExecutor.java:539-557`)이 ToolCallback 목록을 `spec.toolCallbacks(...)`에 넘깁니다. 그 뒤로는 Spring AI의 tool-calling 루프가 각 `ToolCallback.call()`을 바로 실행하고, `ToolExecutor`는 거치지 않습니다. 이 경로의 Tool은 다음 래퍼로 감싸져 있습니다.
- `ToolCallBudgetCallback`, `RereadGuardCallback`: 호출 예산과 같은 파일 다시 읽기 방지
- `SubAgentToolCallback`, `SkillTool`: 엔진이 직접 만든 Tool
- `UnknownToolGuard.UnknownToolCallback`, `SubAgentTasks.OutputCallback` 등

## 실제로 두 경로가 함께 지나는 곳

두 경로가 공통으로 지나는 곳은 `ConfigTool.LimitedToolCallback`(`ConfigTool.java:333`)입니다.
- 로컬 `@AiTool`, MCP Tool, stepOnly Tool을 등록할 때 모두 이 클래스로 감쌉니다(`ConfigTool.java:120-133`). 결과 크기 상한, 호출 로그, 파일 기록이 여기서 처리됩니다.
- Skill Tool과 Sub Agent Tool도 `configTool.limited(...)`로 감쌉니다(`AgentExecutor.java:549`, `:802`).

그래서 Tool 호출을 감사하거나 제한하는 기능을 한 곳에 넣으려면 `ToolExecutor`보다 `LimitedToolCallback`이 맞습니다. `ToolExecutor`에 넣으면 AGENT step의 호출이 빠집니다. 같은 이유로 `ConfigCallLog`의 `TOOL_POINTCUT`(`ToolExecutor.*`)도 TOOL step의 호출만 로그로 남깁니다.

원하시면 `ToolExecutor`의 클래스 주석을 "TOOL step 한정"으로 고쳐 두겠습니다.

---

## 질문

그렇다면 모든 tool 호출의 포인트컷을 잡으려고 하는 net.dstone.ai.common.config.ConfigCallLog.TOOL_POINTCUT 는 어떻게 수정하는게 나을까?

---

## 답변

결론부터 말하면 **포인트컷 문자열만 바꿔서는 해결되지 않습니다.** 두 경로가 실제로 함께 지나는 곳은 `ConfigTool.LimitedToolCallback`인데, 이 클래스는 Spring bean이 아닙니다. `@PostConstruct`에서 `new`로 만드는 private static 클래스라서 `execution(* ...LimitedToolCallback.call(..))`로 써도 Spring AOP 프록시가 걸리지 않습니다.

## 방법 비교

| 방법 | 포인트컷 | 잡히는 범위 | 문제 |
|---|---|---|---|
| A. `@Tool` 메소드에 직접 | `@annotation(org.springframework.ai.tool.annotation.Tool)` | `@AiTool` bean의 로컬 Tool | MCP Tool, Sub Agent, Skill Tool이 빠집니다. Spring AI가 CGLIB 프록시를 거쳐 호출하는지도 확인해야 합니다. |
| **B. 호출을 bean 하나로 모으기 (권장)** | `execution(* net.dstone.ai.runtime.tool.ToolCallGate.invoke(..))` | `LimitedToolCallback`이 감싼 모든 Tool. TOOL step, AGENT step, MCP, Sub Agent, Skill이 모두 포함됩니다. | 클래스 하나가 늘어납니다. |

## B안 구성

1. 새 `@Component` `runtime.tool.ToolCallGate`를 만듭니다.
   ```java
   public String invoke(String toolName, ToolCallback delegate, String toolInput, ToolContext toolContext) {
       return toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
   }
   ```
   - 이 클래스는 `ConfigTool`에 의존하면 안 됩니다. `ToolExecutor`는 이미 `ConfigTool`을 쓰고 있어서, 이 메소드를 `ToolExecutor`에 넣으면 순환 참조가 생깁니다.
   - 인자 이름을 `toolName`으로 두면 지금의 `doToolsProfiling`이 Tool 이름을 꺼내는 코드를 그대로 쓸 수 있습니다.
2. `ConfigTool`에 `@Autowired ToolCallGate`를 주입하고 `LimitedToolCallback` 생성자로 넘깁니다. `LimitedToolCallback.call()` 안의 `this.delegate.call(...)`은 `gate.invoke(...)`로 바꿉니다. 주입받은 프록시 참조로 호출해야 AOP가 걸립니다.
3. `ConfigCallLog`를 다음처럼 바꿉니다.
   ```java
   private final static String TOOL_POINTCUT = "execution(* net.dstone.ai.runtime.tool.ToolCallGate.invoke(..))" + " && !" + NO_LOG_REGEX;
   ```
   TOOL step도 `ToolExecutor`에서 `LimitedToolCallback`을 거쳐 `ToolCallGate`로 들어옵니다. 그래서 `ToolExecutor`는 포인트컷에서 빼야 같은 호출이 두 번 찍히지 않습니다.
4. 함께 정리할 것들:
   - `LimitedToolCallback.logged()`가 남기는 한 줄 로그는 AOP 로그와 겹칩니다. 남길지 지울지 정해야 합니다.
   - 예산 초과, 같은 파일 다시 읽기, 모르는 Tool처럼 `ToolCallBudgetCallback`, `RereadGuardCallback`, `UnknownToolGuard`가 막은 호출은 Tool이 실행되지 않으므로 이 로그에 나오지 않습니다. 실제로 실행된 호출만 남는 것이라 의도에 맞다고 봅니다.
   - `SubAgentTasks.OutputCallback`은 `limited()`로 감싸는지 구현할 때 확인해야 합니다.
   - `ToolExecutor`의 클래스 주석과 `doToolsProfiling` 주석의 "tools 패키지"라는 표현도 실제 동작에 맞게 고칩니다.

B안으로 진행할까요? 진행하면 `docs/09`와 `.claude/docs/ai-engine-dataflow.md`의 Tool 호출 구조 설명도 같이 맞추겠습니다.

---

## 질문

응 B안으로 진행해.

---

## 답변

구현에 필요한 코드부터 확인하겠습니다.

---

## 답변

`ToolCallGate`를 새로 만들고 `ConfigTool`과 `ConfigCallLog`를 고치겠습니다.

---

## 답변

이제 `ConfigCallLog`와 `ToolExecutor` 주석을 고칩니다.

---

## 답변

`ToolContext`는 `toString()`이 없을 수 있어 내용(map)을 찍도록 보완합니다.

---

## 답변

컴파일은 통과했습니다. 실제로 AOP 로그가 찍히는지 테스트로 확인하겠습니다.

---

## 답변

TOOL step 테스트와 Agent Tool 호출 테스트를 돌려 보겠습니다.

---

## 답변

출력이 너무 길어서 surefire 보고서와 로그 파일만 따로 보겠습니다.

---

## 답변

테스트는 모두 통과했습니다. 로그에 Tool 호출이 찍혔는지 보겠습니다.

---

## 답변

Agent 경로의 `validateSqlSyntax`는 찍혔습니다. `Skill`과 `readFile`이 안 보여서 해당 테스트가 실제로 돌았는지 확인합니다.

---

## 답변

`conf/application.yml`에서 `llmcall: true`로 되어 있어 미리 넣은 답을 쓰는 Agent 테스트가 건너뛰어졌습니다. 이번 실행에만 `false`로 바꿔 다시 돌리겠습니다.

---

## 답변

B안대로 적용했습니다. 이제 TOOL step, Agent가 고른 Tool, MCP, Skill, Sub Agent 호출이 모두 한 지점에서 로그로 남습니다.

## 바꾼 내용
- **새 클래스 `runtime/tool/ToolCallGate.java`**: Tool을 실제로 실행하는 `invoke(toolName, toolInput, toolContext, delegate)` 메소드 하나만 있는 bean입니다. `ConfigTool`을 쓰지 않으니 순환 참조가 생기지 않습니다.
- **`ConfigTool`**: `ToolCallGate`를 주입받아 `LimitedToolCallback`에 넘기고, Tool 실행을 `toolCallGate.invoke(...)`로 바꿨습니다. 예전 한 줄 로그(`dstone-ai-engine tool: [x] 호출 …`)를 남기던 `logged()`는 AOP 로그와 겹쳐서 지웠습니다.
- **`ConfigCallLog`**:
  - `TOOL_POINTCUT`을 `execution(* net.dstone.ai.runtime.tool.ToolCallGate.invoke(..))`로 바꿨습니다.
  - 입력 로그에서 `ToolCallback` 인자는 내부 객체라 빼고, `ToolContext`는 안에 든 값(caller, 실행 id, step id)만 찍습니다.
  - 결과가 null이어도 NPE가 나지 않게 `String.valueOf`로 바꿨습니다.
- **`ToolExecutor` 주석**: TOOL step에만 쓰인다는 점을 적었습니다.
- **문서**: `docs/09`(패키지 표, 패키지 트리, 로깅 절, 결과 상한 절, 거버넌스 확장 지점, 문제 해결 표), `.claude/docs/ai-engine-dataflow.md`, `ai-engine-packages.md`를 맞췄습니다.

## 확인
`mvn test`로 7개 테스트 클래스 40건을 돌렸고 모두 통과했습니다. 로그에도 경로마다 `[Tools - …] ToolCallGate.invoke()` 블록이 남았습니다.
- TOOL step: `checkData`, `editFileTexts`, `filterList`, `writeFile`
- Agent가 고른 Tool: `readFile`, `validateSqlSyntax`
- Skill: `Skill`
- Sub Agent: `sample-skill-agent`, `TaskOutput`

## 알아 두실 점
- `conf/application.yml`에 `llmcall: true`가 설정되어 있어서, 미리 넣은 답을 쓰는 Agent 테스트는 그대로 돌리면 건너뜁니다. 이번에는 `-Ddstone.ai.test.llmcall=false`를 붙여 확인했습니다.
- AOP 로그에는 결과가 자르기 전 값 그대로 남습니다. 예전 한 줄 로그는 결과 길이만 남겼으니, Agent가 큰 파일을 자주 읽으면 `execution.log`가 빠르게 커질 수 있습니다. 필요하면 Tool 로그에 글자 수 상한을 두겠습니다.
- 예산 초과, 끊긴 인자, 같은 파일 다시 읽기처럼 앞 단계에서 막힌 호출은 Tool이 실행되지 않으므로 이 로그에 남지 않습니다.

커밋은 아직 하지 않았습니다.

---

## 질문

커밋 푸시해줘.

---

## 답변

변경한 7개 파일을 `main`에 커밋하고 `origin/main`에 푸시했습니다. 로컬과 원격이 같은 커밋을 가리킵니다.

- 커밋: `bd252b4e` ai-engine: 모든 Tool 실행을 ToolCallGate로 모아 호출 로그 포인트컷 일원화
- 포함 파일:
  - `ToolCallGate.java`(새 파일)
  - `ConfigCallLog.java`, `ConfigTool.java`, `ToolExecutor.java`
  - `docs/09.dstone-ai-engine.md`
  - `.claude/docs/ai-engine-dataflow.md`, `.claude/docs/ai-engine-packages.md`
