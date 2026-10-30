# localcommandcaveatTh

> 생성일시: 2026-10-30 16:08

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

---

## 질문

Skill 은 LLM프로바이더 디펜던트 한건가?

---

## 답변

아니요. 이 라이브러리의 Skill은 LLM provider와 상관없이 동작합니다.

**동작 방식**

`SkillsTool`이 만드는 것은 Spring AI의 평범한 Tool 하나(`FunctionToolCallback`, 이름 `Skill`)입니다.

1. Tool 설명문에 Skill 목록(name, description)을 글로 넣어 LLM에 보냅니다.
2. LLM이 필요하다고 판단하면 `Skill(command="이름")`으로 Tool을 호출합니다.
3. 엔진이 해당 `SKILL.md` 본문을 읽어 Tool 결과로 돌려줍니다.

모두 일반 Tool 호출이라서 지금 엔진에 붙은 `readFile` 같은 Tool과 같은 경로를 탑니다. Anthropic, OpenAI/OpenRouter, Ollama 어디서든 Tool 호출만 지원하면 됩니다.

**헷갈리기 쉬운 것**

Anthropic API에도 "Agent Skills"라는 기능이 있는데, 이 라이브러리와는 별개입니다. 그쪽은 Skill을 Anthropic 서버에 올리고 코드 실행 컨테이너 안에서 돌리는 방식이라 Anthropic에서만 됩니다. Skill이라는 개념(`SKILL.md` + 필요할 때 불러오기)만 같고, 이 라이브러리는 그것을 Tool 호출로 흉내 낸 것입니다.

**모델에 따라 달라지는 점**

provider가 아니라 모델 능력에 따라 달라지는 부분은 있습니다.
- **Tool 호출 지원 여부:** Ollama는 모델마다 다릅니다. Tool 호출을 못 하는 모델이면 Skill도 못 씁니다.
- **Skill을 제때 고르는지:** description만 보고 알맞은 Skill을 고르는 능력은 모델마다 차이가 큽니다. 작은 모델은 Skill을 아예 부르지 않거나 엉뚱한 것을 부르기도 합니다. 그래서 계획에서처럼 Agent마다 `skills.allowed`로 후보를 좁혀 두는 편이 안전합니다.
- **본문이 긴 경우:** Skill 본문은 대화에 그대로 들어가므로 컨텍스트를 차지합니다. 우리 엔진의 결과 압축(`ToolResultCompactionAdvisor`)이 오래된 Tool 결과를 줄이는데, Skill 본문도 여기서 잘릴 수 있습니다. 구현할 때 Skill 결과를 압축 대상에서 뺄지 정해야 합니다.

정리하면, 지금 쓰는 OpenRouter `qwen/qwen3.8-27b`에서도 구조상으로는 그대로 쓸 수 있습니다. 실제로 Skill을 잘 골라 쓰는지는 구현 뒤에 한 번 돌려 봐야 압니다.

---

## 질문

기존 subAgents 와 Sub Agent 와의 차이점과 통합방법에 대해서 더 자세하게 설명해줘

---

## 답변

두 방식을 기능별로 비교하고, 합칠 수 있는 방법 세 가지를 정리했습니다.

## 1. 겉모습: LLM에게 무엇이 보이나

**기존 `subAgents`(우리 엔진):** 맡길 Agent 하나가 Tool 하나로 보입니다.
```
Tool 목록
├─ validateSqlSyntax(sql)
├─ sample-structured-extract-agent(input: string)        ← Agent id가 곧 Tool 이름
└─ sample-fix-agent(sql: string, error: string)          ← 자식의 input 스키마가 그대로 인자
```

**라이브러리 `TaskTool`:** Tool은 `Task` 하나뿐이고, 어떤 Agent에 맡길지는 인자로 고릅니다.
```
Tool 목록
├─ Task(description, prompt, subagent_type, model?, resume?, run_in_background?)
│      Tool 설명문 안에 "-explore: /설명", "-plan: /설명" 식의 목록
└─ TaskOutput(task_id, block?, timeout?)                  ← 백그라운드 결과 받기
```

## 2. 기능 비교

| 항목 | 기존 `subAgents` | 라이브러리 `TaskTool` |
|---|---|---|
| 인자 | **자식의 input 스키마 그대로**(object도 가능). 문자열 입력이면 `{input: …}`으로 감쌈 | `prompt` 문자열 하나. 구조화된 입력이 없음 |
| 입출력 검사 | `AgentExecutor.call()`이 input과 output 스키마를 모두 검사. 위반하면 `실패: …`로 돌려줘 부모가 고쳐서 다시 부름 | 없음. 자식 답 문자열을 그대로 돌려줌 |
| 실행 경로 | `AgentExecutor` → 엔진 규칙(`SUB_AGENT`), `requestOptions()`(타임아웃·reasoning), Tool 예산, 결과 압축, RAG 설정 | 기본 실행기가 `ChatClient`를 직접 부름. 위 장치가 모두 빠짐(교체 가능) |
| 자식 정의 | 기존 Agent `.md` 그대로 | Claude Code 형식 `.md`(`name`/`tools`/`disallowedTools`/`model`/`skills`). 내장 Explore/Plan/Bash/General이 자동으로 붙음 |
| **동시 실행** | 없음. 부르면 끝날 때까지 기다림 | **`run_in_background: true`로 맡기고 바로 돌아옴.** 나중에 `TaskOutput`으로 결과를 받음 |
| 이어서 시키기(resume) | 없음. 매번 빈 대화 | 인자는 있지만 기본 실행기가 **무시함**(구현 안 됨) |
| 모델 선택 | 자식 Agent의 `model.name` | 인자 `model`이 있지만 기본 실행기가 **무시함**. 정의 파일의 `model`은 반영 |
| 호출 횟수 제한 | `dstone.ai.agent.sub-agent.max-calls`(기본 10) | 없음 |
| 깊이 | 1단계(기동할 때 검사) | 제한 없음(자식에게 Task Tool을 주면 재귀) |
| 백그라운드 저장소 | 해당 없음 | 메모리 + 무제한 스레드 풀. 서버를 재시작하면 사라짐 |

정리하면 기존 `subAgents`가 라이브러리보다 **부족한 기능은 백그라운드 실행 하나뿐**입니다. 나머지(타입 있는 입력, 계약 검사, 엔진 장치, 횟수 제한)는 기존 방식이 더 엄격합니다. resume과 model 인자는 라이브러리에서도 실제로 동작하지 않습니다.

**병렬 실행에 대해 짚어 둘 점:** LLM이 한 번의 답에서 Sub Agent 세 개를 동시에 호출해도, 지금은 하나씩 차례로 실행된다고 알고 있습니다. Spring AI의 Tool 실행이 한 응답 안의 Tool 호출을 순서대로 처리하기 때문입니다. 2.0.1 소스로 직접 확인하지는 않았으니 구현 전에 확인하겠습니다. 그래서 "진짜 동시에 돌리기"는 백그라운드 실행 방식으로만 얻을 수 있습니다.

## 3. 통합 방법 세 가지

### 방법 A. 둘 다 두기(처음 계획)
```yaml
subAgents: [sample-fix-agent]                        # 기존: 동기, 타입 있는 입력
tasks: {agents: [sample-investigator-agent], background: true}   # 새로: Task Tool
```
- 장점: 기존 코드를 건드리지 않습니다.
- 단점: 일을 맡기는 방법이 두 가지가 되어, YAML을 쓰는 사람이 언제 무엇을 쓸지 헷갈립니다. Task 쪽은 문자열 입력만 받을 수 있습니다.

### 방법 B. `subAgents` 하나로 합치고, 라이브러리의 백그라운드 부품만 가져오기 ← **권장**
```yaml
subAgents:
  agents: [sample-structured-extract-agent, sample-fix-agent]
  background: true          # 생략하면 false = 지금과 똑같이 동작
```
LLM에게는 이렇게 보입니다.
```
├─ sample-fix-agent(sql, error, run_in_background?)   ← 기존 Tool에 인자 하나 추가
├─ sample-structured-extract-agent(input, run_in_background?)
└─ TaskOutput(task_id, block?, timeout?)               ← background: true일 때만 붙음
```
- `SubAgentToolCallback`은 그대로 두고, `background: true`일 때만 인자 스키마에 `run_in_background`를 덧붙입니다.
- `run_in_background`가 참이면 라이브러리의 `TaskRepository.putTask()`에 실행을 맡기고 `task_id`를 바로 돌려줍니다. 실행 자체는 지금처럼 `AgentExecutor.call()`로 합니다.
- `TaskOutput` Tool은 라이브러리 `TaskOutputTool`을 그대로 씁니다.
- 라이브러리의 `Task` Tool(문자열 `prompt` 하나)과 Claude 형식 정의 파일, 내장 Sub Agent는 **쓰지 않습니다.**
- 장점: 맡기는 방법이 하나로 유지됩니다. 타입 있는 입력과 계약 검사도 그대로 남습니다. 백그라운드는 원하는 Agent만 켭니다.
- 단점: 라이브러리에서 가져오는 것이 저장소와 `TaskOutput` 정도라 "TaskTool을 도입했다"고 하기에는 작습니다.

### 방법 C. 라이브러리 `Task` Tool로 완전히 바꾸기
```yaml
subAgents: [sample-structured-extract-agent, sample-fix-agent]   # 형식은 그대로, 내부만 Task로
```
- `TaskTool.builder().subagentTypes(new SubagentType(우리Resolver, 우리Executor))`로 만들고, 실행기 안에서 `AgentExecutor.call()`을 부릅니다.
- 단점이 큽니다.
  - Tool 인자가 `prompt` 문자열 하나로 줄어, `sample-fix-agent`처럼 object를 받는 Agent는 LLM이 JSON을 글로 써서 넘겨야 합니다. 스키마가 Tool 정의에서 사라지므로 틀리는 일이 늘어납니다.
  - Tool 설명문이 Claude Code용 문구(Read/Glob Tool 언급, 코드 예시)라서 바꿔 써야 합니다.
  - 지금 잘 돌아가는 `sample-subagent-orchestrator-agent`와 `pilot-source-investigator-agent`의 동작이 바뀌어 다시 측정해야 합니다.

## 4. 방법 B를 고를 때 바뀌는 것

| 위치 | 변경 |
|---|---|
| `AgentDefinition` | `subAgents`가 목록이면 지금처럼 읽고, `{agents, background}` 모양도 받음. 기존 파일은 고치지 않아도 됨 |
| `SubAgentToolCallback` | `background`면 `run_in_background` 인자를 더하고, 참일 때 저장소에 넣은 뒤 `task_id`를 돌려줌 |
| `AgentExecutor.subAgentCallbacks()` | 부모 호출 한 번마다 `TaskRepository`를 새로 만들고, `TaskOutputTool`을 붙임. 부모 호출이 끝나면 남은 task를 취소함 |
| 공용 스레드 풀 | 크기를 제한함(`dstone.ai.agent.sub-agent.max-concurrent`). 무제한 풀은 쓰지 않음 |
| 횟수 제한 | 백그라운드 호출도 `max-calls`에 포함 |
| 로그 | 백그라운드 스레드에서도 `parent → child` 로그를 남김. 호출 로그와 `execution.log`가 섞이지 않게 task_id를 붙임 |
| 엔진 규칙 | `background`일 때 "맡긴 뒤 `TaskOutput`으로 결과를 모두 받고 나서 답하라"는 규칙을 `EnginePrompt`에 추가 |
| 디자이너 | Agent 대화상자의 Sub Agent 칩 옆에 "동시 실행" 체크박스 |

## 5. 어떤 경우에 쓸모 있나

- **쓸모 있는 경우:** LLM이 상황을 보고 몇 갈래로 나눌지 정하는 조사형 Agent. 예를 들어 일반 경로에서 진입점 세 곳을 `pilot-source-investigator-agent` 셋에게 동시에 맡기는 경우입니다. 지금은 차례로 돌아서 진입점 수만큼 시간이 늘어납니다.
- **쓸모 없는 경우:** 나눌 대상이 미리 정해진 일. 이런 일은 Workflow `forEach`가 더 낫습니다. 이미 병렬로 돌고, 결과가 `state`에 남고, 테스트하기도 쉽습니다.
- **제약:** OpenRouter의 동시 요청 한도, MCP 서버별 직렬화, 파일 편집 Tool의 공용 잠금 때문에 동시에 맡겨도 기대만큼 빨라지지 않을 수 있습니다.

방법 B로 진행할까요? 정해 주시면 Skill 작업과 묶어서 구현 순서를 다시 짜겠습니다.
