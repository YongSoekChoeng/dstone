# localcommandcaveatTh

> 생성일시: 2026-10-21 15:27

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-boot>ai 에서 새 Agent/Supervisor/Router 를 작성하기 위해 agent 디자이너를 띄울 때 모범적인 프롬프팅 템플릿을 기본으로 세팅하도록 수정해줘.

---

## 답변

Agent 디자이너에서 새 Agent를 만들 때 기본값이 어떻게 채워지는지부터 살펴보겠습니다.

---

## 답변

ROUTER 문장의 조사가 경로 이름에 따라 어색해질 수 있어 고치고, 만든 템플릿을 실제로 렌더링해 YAML 파싱과 엔진 검사까지 돌려 보겠습니다.

---

## 답변

세 종류 모두 YAML로 읽힙니다. 이제 엔진의 [검사] API(파일을 쓰지 않음)로 실제 검증 규칙을 통과하는지 봅니다.

---

## 답변

세 템플릿 모두 엔진 검사를 통과했습니다. 디자이너 설명이 문서에 있는지 확인합니다.

---

## 답변

이제 디자이너에서 step 설정의 [새 Agent]를 누르면, 그 step 종류(AGENT/SUPERVISOR/ROUTER)에 맞는 모범 템플릿이 채워진 상태로 Agent 창이 열립니다.

**템플릿 구성** (`workflow-designer.js`의 `newAgent(step)` / `newAgentId()` / `agentTemplate()`)
- 저장소에 있는 `agents/sample/steptype/*.md` 샘플과 같은 짜임입니다. 본문은 역할 한 줄 → [입력] → [할 일] 또는 [통과 기준] 또는 [고를 수 있는 이름] → [답 쓰는 법] 순서이고, 괄호 ( ) 안만 채우면 됩니다.
- 안내는 머리말 맨 위 주석에 적었습니다. 채울 곳, 꼭 지킬 것, 좋은 프롬프트를 쓰는 요령이 들어갑니다. 맨 위 주석은 [양식]으로 고쳐도 지워지지 않습니다. 엔진이 늘 앞에 넣는 규칙(`EnginePrompt`)은 본문에 다시 적지 않았습니다.
- **step 종류별 기본값**
  - AGENT: `temperature: 0.2`이고, output 작성 예와 `tools.allowed` 안내를 주석으로 넣었습니다.
  - SUPERVISOR: `temperature: 0`이고, 통과 기준은 "만족하는 경우 / 만족하지 않는 경우" 예를 함께 적는 형식입니다. reason 쓰는 법도 들어 있습니다.
  - ROUTER: `model.reasoning: none`이고, **그 step의 `routes` 이름을 본문에 미리 적어 둡니다.** 그래서 경로 이름 검사에 걸리지 않습니다. routes가 아직 없으면 `경로1`/`경로2`를 넣습니다.
- step의 `with`가 맵이면 그 키 이름들을 `input` 필드(`any`)로 넣고 [입력] 구간에도 나열합니다. `with`가 맵이 아니면 `input: string`이 됩니다.
- id는 `<Workflow id에서 -workflow를 뗀 것>-<step id>-agent`로 지어 둡니다(예: `sm-step03-agent`). 이미 있는 id면 뒤에 번호를 붙입니다.

**검증한 것**
- 세 종류의 템플릿을 Node로 만들어 머리말이 YAML로 읽히는지 확인했습니다.
- 그 결과를 8081 엔진의 `POST /api/ai/definitions/agents/check`(파일을 쓰지 않는 검사)에 보냈고, 세 개 모두 통과했습니다.
- 브라우저에서 디자이너 화면을 직접 눌러 보지는 않았습니다.

`docs/06.dstone-boot.md`의 디자이너 설명에도 이 동작을 적었습니다. 커밋은 하지 않았습니다.
