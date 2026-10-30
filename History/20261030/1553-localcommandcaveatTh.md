# localcommandcaveatTh

> 생성일시: 2026-10-30 15:53

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-ai-engine 에 spring-ai-agent-utils 을 추가했어. 아래의 기능을 dstone-ai-engine 에 추가하고자 해. 계획을 세워줘.
- Agent Skills
- TaskTools – Sub-Agent 오케스트레이션

---

## 답변

라이브러리 API를 확인하려고 Maven Central에서 jar를 받아 클래스 구성을 살펴보는 중입니다.

---

## 답변

Now the engine side: the current Sub Agent, the tool-attach spot, the loader, and the reloader.

---

## 답변

계획을 정리했습니다. 라이브러리 소스(0.13.0)와 현재 엔진 코드를 같이 읽고 세웠습니다.

## 시작 전에 확인할 것

**의존성은 `/app/dstone`에 아직 없습니다.** `spring-ai-agent-utils` 0.13.0은 Windows 쪽 클론(`D:/AppHome/framework/dstone/dstone-ai-engine/pom.xml`, 128~133행)에만 들어가 있고 커밋도 되지 않았습니다. 작업은 `/app/dstone`에서만 하기로 했으니, 구현 첫 단계에서 이곳 pom에 같은 의존성을 넣겠습니다. 버전은 맞습니다. Spring AI는 2.0.1, Spring은 7.0.x 기준이고, 함께 들어오는 것은 `spring-ai-agent-utils-common`과 `flexmark-html2md-converter`입니다.

## 라이브러리를 읽고 확인한 점

| 기능 | 라이브러리가 하는 일 | 엔진에 그대로 쓰면 생기는 문제 |
|---|---|---|
| **SkillsTool** | `SKILL.md`(front matter `name`/`description` + 본문)를 읽고 `Skill` Tool 하나를 만듭니다. Tool 설명에는 Skill 목록이 들어가고, LLM이 이름으로 부르면 본문과 기준 폴더를 돌려줍니다. | 큰 문제는 없습니다. 다만 이름이 겹치면 경고만 남기고 넘어갑니다. 빌더의 `addSkill()`은 기준 폴더를 버리기 때문에 Agent마다 필요한 Skill만 골라 붙이기가 어색합니다. |
| **TaskTool / TaskOutputTool** | `Task` Tool 하나로 `subagent_type`을 골라 일을 맡깁니다. `run_in_background`로 백그라운드 실행을 하고, `TaskOutput`으로 결과를 받습니다. | 기본 실행기 `ClaudeSubagentExecutor`가 `ChatClient`를 직접 부르기 때문에 **`AgentExecutor`가 지키는 것들을 모두 건너뜁니다.** 엔진 규칙, `requestOptions()`(60초 타임아웃 문제), 입출력 계약 검사, `ToolCallBudgetCallback`, 결과 압축, `LimitedToolCallback`, 호출 로그가 빠집니다. Claude 종류로 등록하면 Explore/Plan/Bash 같은 내장 Sub Agent가 자동으로 붙는데, 이들은 우리에게 없는 Tool 이름(Read/Grep/Bash)과 Claude 모델 별칭을 씁니다. `model`/`resume` 인자는 무시됩니다(소스에 TODO로 남아 있음). 백그라운드 저장소 `DefaultTaskRepository`는 메모리 안의 무제한 스레드 풀입니다. |

따라서 **Skill은 라이브러리 부품을 그대로 쓰고, Task는 껍데기(`Task`/`TaskOutput` Tool, `TaskRepository`)만 쓰는** 방향을 권합니다. 실행기는 `SubagentType` 확장점에 엔진 전용 구현을 끼웁니다.

## 1. Agent Skills

**정의 위치와 형식**
- `conf/definitions/skills/<폴더>/<skill-name>/SKILL.md`에 둡니다. 같은 폴더의 참고 파일(`reference.md` 등)도 함께 둘 수 있습니다.
- `Constants.Definition`에 `skills/`를 추가합니다. `YamlDefinitionLoader`는 라이브러리의 `Skills.loadDirectory()`로 파싱합니다.

**Agent에서 쓰는 방법**
- Agent `.md` front matter에 `skills: {allowed: [name, ...]}`를 둡니다. `tools.allowed`와 같은 모양이고, `AgentDefinition.Skills` 레코드와 `skillNames()`를 추가합니다.

**구현**
1. `common.registry.SkillRegistry`를 새로 만듭니다. 이름으로 `Skill`을 찾고 기동 시 검사합니다. 이름 중복, 이름 형식(`[A-Za-z0-9_-]`), `description` 누락은 기동 실패로 처리합니다. 라이브러리는 이런 경우 경고만 남기지만, 다른 정의와 같은 기준을 적용합니다.
2. `AgentRegistry.check()`에서 모르는 Skill 이름을 쓰면 기동을 실패시킵니다. Tool 이름 `Skill`과 Sub Agent id가 겹치는지도 검사합니다.
3. `AgentExecutor.buildSpec()` 5단계에서 Agent의 Skill만 담은 `FunctionToolCallback("Skill", new SkillsTool.SkillsFunction(map))`을 붙입니다. 설명문은 우리 문구로 쓰고, `configTool.limited()`와 `budgeted()`로 감쌉니다. Agent마다 한 번 만들어 캐시하고, 정의를 다시 불러올 때 버립니다.
4. 참고 파일은 Agent가 `readFile`/`readFileLines`로 읽습니다. 그래서 **참고 파일이 있는 Skill을 쓰는 Agent는 `tools.allowed`에 `readFile`도 있어야 합니다.** 이 조건이 빠지면 경고를 남깁니다.
5. **Skill 안의 스크립트 실행은 1차 범위에서 뺍니다.** 라이브러리의 `ShellTools`는 우리 화이트리스트(`ShellExecTool`)를 우회합니다.
6. `DefinitionReloader`에 Skill을 포함합니다. 변경을 덧씌운 뒤 Skill → Agent → Workflow 순으로 검사하고, 모두 통과해야 반영합니다. 같은 경로로 `DefinitionController` 카탈로그와 `GET /api/ai/chat` 목록에도 Skill을 추가합니다.
7. 샘플은 `skills/sample/...` 1~2개와 이를 쓰는 `sample-skill-agent.md`, 시험용 Workflow 하나를 만듭니다.

## 2. TaskTools(Sub Agent 오케스트레이션)

**기존 `subAgents`와의 관계(권장안)**
- `subAgents`는 지금처럼 둡니다. 동기 실행, 타입이 있는 입력 스키마, 깊이 1 구조입니다.
- Task는 그 위에 **"LLM이 판단해서 여러 Agent를 동시에 돌리고 결과를 나중에 모으는"** 기능만 더합니다. 같은 일을 하는 경로가 둘이 되지 않게 하려는 것입니다.

**정의**
- Agent front matter에 `tasks: {agents: [id, ...], background: true|false}`를 둡니다. 맡길 대상은 기존 Agent `.md`이므로 새 파일 형식이 생기지 않습니다.
- `TaskCall.prompt`는 문자열이라서 **입력이 `string`인 Agent만 `tasks.agents`에 올 수 있습니다.** 그 밖의 입력이면 기동을 실패시킵니다.

**구현**
1. `runtime.agent.task.DstoneSubagentResolver`/`DstoneSubagentDefinition`을 만듭니다. kind는 `DSTONE`이고, id로 `AgentRegistry`의 정의를 찾습니다. Claude 종류가 아니므로 내장 Sub Agent는 붙지 않습니다.
2. `DstoneSubagentExecutor`는 `AgentExecutor.call()`로 실행합니다. 엔진 규칙 `SUB_AGENT`, 계약 검사, 예산, 타임아웃, 호출 로그가 모두 그대로 적용됩니다. 계약 위반은 `실패: ...` 응답으로 바꾸고, 시스템 오류는 그대로 던집니다. `SubAgentToolCallback`과 같은 규칙입니다.
3. `TaskRepository`는 **부모 Agent 호출 하나마다 새로 만듭니다.** 다른 호출이나 caller의 task를 보지 못하게 하기 위해서입니다. 스레드 풀은 공용 빈 하나로 두고 크기를 제한합니다(`dstone.ai.agent.task.max-concurrent`).
4. 부모 호출이 끝날 때 남은 task는 취소합니다. 이렇게 하면 Workflow step이 끝난 뒤에 task가 계속 도는 일이 없습니다.
5. 횟수 제한은 `dstone.ai.agent.sub-agent.max-calls`와 합산합니다. `TaskOutput` 대기 시간의 상한은 설정값으로 둡니다. 라이브러리 기본값은 30초이고 최대 600초입니다.
6. `resume`과 `model` 인자는 받지 않습니다. 모델은 Agent의 `model.name`을 따릅니다.
7. 1차 검증은 `ScriptedChatModel`로 합니다. Task 동기/백그라운드, `TaskOutput`, 취소, 입력이 문자열이 아닌 Agent 거부를 시험하고, 실제 LLM으로는 한 번 실행합니다.

**미리 알아 둘 한계**
- MCP 호출은 서버별로 직렬화되어 있습니다(`ConfigMcp.SerializedToolCallback`). 파일 편집 Tool도 잠금 하나를 공유합니다. 그래서 병렬로 돌려도 빨라지지 않는 경우가 있습니다.
- Workflow 단위의 병렬 처리는 이미 `forEach`로 할 수 있습니다. Task가 쓸모 있는 곳은 "몇 개로 나눌지 LLM이 정하는" 경우로 한정됩니다.

## 3. 공통 작업과 순서

1. pom 의존성 추가 → 빌드와 기동 확인(기존 28개 Agent / 20개 Workflow, 경고 0개)
2. Skills: 정의, 레지스트리, 실행, 재로딩, 샘플, 테스트
3. Tasks: Resolver/Executor, 범위 제한 저장소, 검사, 샘플, 테스트
4. 디자이너(`dstone-boot`): Agent 대화상자에 Skill/Task 칩을 넣습니다. 지금의 Tool/Sub Agent 칩과 같은 방식이고, Skill 파일 편집은 다음 단계로 미룹니다.
5. 문서: `docs/09.dstone-ai-engine.md` §7/§8(필드 설명과 템플릿), CLAUDE.md의 ai-engine 절

## 정해 주실 것

1. **Task와 `subAgents`의 관계:** 권장안(둘 다 두고 Task는 백그라운드·병렬용)과, `subAgents`를 Task로 통합하는 방안 중 어느 쪽으로 할까요? 통합하면 타입 있는 입력을 포기해야 합니다.
2. **Skill 스크립트 실행:** 1차에서 빼도 될까요?
3. **Claude 내장 Sub Agent(Explore/Plan/Bash/General):** 권장안대로 쓰지 않을까요?

정해 주시면 1단계(pom 반영과 기동 확인)부터 시작하겠습니다.
