# localcommandcaveatTh

> 생성일시: 2026-11-20 08:24

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
