# localcommandcaveatTh

> 생성일시: 2026-10-18 18:07

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="9c7d">
StepType 에 따른 agent 용 md 파일의 샘플을 보여줘.
/** LLM에게 일을 한 번 시킵니다(AgentStepDefinition). */
StepType.AGENT
/** LLM이 {pass, reason}으로 통과/불통과를 판정합니다(SupervisorStepDefinition). */
StepType.SUPERVISOR
/** LLM이 routes 중 갈 곳 하나를 {route, reason}으로 고릅니다(RouterStepDefinition). */
StepType.ROUTER
</pasted_content id="9c7d">

---

## 질문

<pasted_content id="9c7d">
net.dstone.ai.common.definition.agent.AgentDefinition 의 주석을 보기좋고 쉽게 정리해줘. 그리고 StepType 에 따른 agent 용 md 파일의 샘플을 추가해줘.
StepType.AGENT
StepType.SUPERVISOR
StepType.ROUTER
</pasted_content id="9c7d">

---

## 답변

AgentDefinition 파일과 기존 Agent 샘플부터 보겠습니다.

---

## 질문

Base directory for this skill: /app/dstone/.claude/skills/friendly-comments

# 쉽고 친근한 주석 쓰기

주석은 한 번 읽고 바로 이해되어야 한다. 다시 읽어야 뜻이 잡히는 문장은 고쳐 쓴다.

## 오해하지 말 것

**주석을 줄이라는 뜻이 아니다.** 이 코드베이스는 원래 "왜 이렇게 했는지", 결정 배경, 이력을 길게 풀어 쓰는 한글 주석을 쓴다 (dstone-ai-engine의 `ConfigTool`, `CallerContext` 등). 그 관례는 그대로 이어간다. 바꾸는 것은 **말투와 문장**이다.

## 이렇게 쓴다

- 짧고 평범한 문장으로 끊어 쓴다.
- 딱딱한 한자어나 논문 같은 표현 대신, 구체적이고 직관적인 말을 고른다.
- "왜"는 그대로 남긴다. 표현만 쉽게 바꾼다.

```java
// 피한다
// 조건부 빈 등록에 기인한 의존성 부재 가능성을 고려하여 지연 조회 방식을 채택함.

// 이렇게 쓴다
// RAG를 꺼 두면 이 빈이 아예 없다.
// 그래서 꼭 필요할 때만 꺼내 쓴다.
```

## 왜

사용자가 2026-09-14에 dstone-ai-engine 영향 분석 내용을 검토한 뒤 직접 요청했다.

---

## 답변

진행 상황: 구조를 다 확인했고, 이제 `AgentDefinition` 주석을 다시 쓰고 샘플 Agent 파일 3개를 만듭니다.

---

## 답변

이제 엔진이 켜질 때 새 Agent 3개가 검사를 통과하는지 확인합니다.

---

## 답변

세 Agent 모두 경고 없이 등록됐습니다. 문서 §8.3에 새 샘플 위치를 한 줄 추가합니다.

---

## 답변

`AgentDefinition` 주석을 다시 정리했고, step 타입별 샘플 Agent 3개를 추가했습니다. 테스트 `DefinitionBootTest`(엔진을 실제로 띄워 정의 파일 검사)를 돌려 보니 세 Agent 모두 경고 없이 등록됐습니다. LLM을 붙여 실제로 호출해 보지는 않았습니다.

**1. `AgentDefinition.java` 주석 정리**
- 클래스 설명을 `■` 소제목으로 나눴습니다: 어디서 오나 / 누가 부르나 / 파일 모양 / 항목 한눈에 보기 / input·output / step 타입마다 다른 점 / model / context / tools / subAgents / execution.
- **"머리말 항목 한눈에 보기"**를 새로 넣어 모든 키를 한 줄씩 볼 수 있게 했습니다.
- **"step 타입마다 다른 점"**도 새로 넣었습니다. SUPERVISOR와 ROUTER는 `output`과 `subAgents`를 적으면 기동에 실패하고, `RETRY`는 AGENT step과 채팅에서만 쓰인다는 내용입니다.
- 파일 모양 예시는 짧게 줄이고, 아래의 샘플 파일 위치를 적어 두었습니다. `@param` 설명도 짧게 다듬었습니다. 코드는 바꾸지 않았습니다.

**2. 샘플 추가 (`definitions/agents/sample/steptype/`)**

| 파일 | 내용 |
|---|---|
| `sample-steptype-agent-agent.md` | 소스 리뷰 Agent. object `input`/`output`(목록, enum), `model`, `tools.allowed`, `execution`(RETRY, 대기 시간, Tool 한도)를 담았고, 쓰지 않는 항목(`context`, `subAgents`, `allowedCallers`)은 주석으로 보여 줍니다 |
| `sample-steptype-supervisor-agent.md` | 답변 검토 Agent. `output`을 적지 않고(`{pass, reason}`은 엔진이 정함), 판정 기준은 예를 들어 적었습니다 |
| `sample-steptype-router-agent.md` | 문의 분류 Agent. `question`/`bug`/`other` 각 이름의 기준을 적었고 `reasoning: none`을 넣었습니다 |

파일마다 맨 위 주석에 지켜야 할 점과, 그 Agent를 부르는 Workflow step 예시(`with`/`save`/`routes`/`onFailure`)를 적어 두었습니다.

**프롬프트 변수 주의:** 샘플을 쓰다가 확인한 점이 있습니다. 프롬프트의 `{변수}`에는 꼭 오는 필드만 써야 합니다. Spring AI는 기본 설정에서 채울 값이 없는 변수가 있으면 오류를 냅니다. 그래서 없어도 되는 필드(`focus?`)에 `{focus}`를 쓰면 값이 안 왔을 때 프롬프트를 만들다 실패합니다. 이것은 실제로 실행해 보고 확인한 것은 아닙니다. 샘플에서는 이런 필드를 본문에 말로만 설명했습니다.

**3. 문서:** `docs/09.dstone-ai-engine.md` §8.3에 샘플 표와 이 주의사항을 추가했습니다.

커밋은 하지 않았습니다.
