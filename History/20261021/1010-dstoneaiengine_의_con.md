# dstoneaiengine_의_con

> 생성일시: 2026-10-21 10:10

## 질문

dstone-ai-engine 의 /conf/definitions 들이 파일명 및 id 등이 한글로 작성될 경우 문제될게 있을까?

---

## 답변

코드에서 id·파일명 검사와 인코딩 처리 부분을 확인해 보겠습니다.

---

## 답변

JS가 이미 인코딩한 경로를 서버가 한 번 더 인코딩하는지 직접 확인해 보겠습니다.

---

## 답변

경로 표현식(`${state.x.필드}`)이 한글 필드명을 받는지도 확인해 보겠습니다.

---

## 답변

결론부터 말씀드리면 **Workflow·Agent의 파일명과 `id`는 한글로 써도 엔진 자체는 대부분 동작합니다.** 다만 한글을 쓰면 안 되는 이름이 따로 있고, 디자이너 화면에서는 실제로 깨지는 곳이 하나 있습니다.

## 1. 한글을 쓰면 부팅에 실패하는 것 (검사 규칙이 영문만 허용)

| 대상 | 규칙 | 위치 |
|---|---|---|
| step id, forEach 항목 변수명 | `[A-Za-z_][A-Za-z0-9_]*` | `WorkFlowRegistry.java:95` |
| `state:` 이름 (`save` 대상) | 같은 규칙 | `WorkFlowRegistry.java:101` |
| **Sub Agent로 쓰는 Agent의 id** | `[A-Za-z0-9_-]{1,64}` | `AgentRegistry.java:62` |
| 프롬프트 `{변수}` 이름 | 영문만 (한글이면 오류 없이 건너뜀) | `AgentExecutor.java:109` |

Sub Agent id를 영문으로 묶은 이유는 그 id가 LLM에게 Tool 이름으로 그대로 전달되기 때문입니다. OpenAI 계열 function name 규칙이 같은 패턴이라서, 이 제한은 풀 수 없습니다. 그래서 **나중에 Sub Agent로 쓸 수도 있는 Agent는 처음부터 영문 id로 두는 게 안전합니다.**

## 2. 한글을 써도 되는 것

- **Workflow id, Agent id(Sub Agent가 아닌 경우)**: 형식 검사가 없습니다. 실행 기록 테이블의 `WORKFLOW_ID VARCHAR(100)`도 PostgreSQL이 바이트가 아니라 글자 수로 세므로 문제없습니다.
- **파일명·폴더명**: 로더가 UTF-8로 읽고 씁니다. 디자이너의 경로 검사(`DefinitionService.java:52`)도 `가-힣`을 허용합니다. 현재 WSL, Windows(MS949), Docker 이미지(temurin은 `LANG=en_US.UTF-8`) 모두 한글 파일명을 다룰 수 있습니다. 이 WSL은 `sun.jnu.encoding = UTF-8`인 것을 직접 확인했습니다.
- **Picoschema 필드명, `${state.x.한글필드}` 경로**: 경로 파서가 `Character.isLetterOrDigit`를 쓰므로 한글이 통과합니다. 필드명에 공백이 들어가면 경로로는 읽을 수 없습니다.
- **REST 호출**: `/api/ai/workflow/{id}/execute`처럼 id가 URL 경로에 들어가는 경우, 호출하는 쪽에서 퍼센트 인코딩만 하면 됩니다. Tomcat이 UTF-8로 풀어 줍니다. `dstone-boot`의 `WorkFlowTestService`는 WebClient가 알아서 인코딩합니다.

## 3. 실제로 깨지는 곳: Workflow 디자이너에서 한글 id 열기

`workflow-designer.js:276`은 `encodeURIComponent(id)`로 이미 인코딩한 경로를 보냅니다. 그런데 `WorkFlowDesignerService.java:59`가 `UriComponentsBuilder…build().encode()`로 한 번 더 인코딩합니다. 같은 Spring 7.0.9로 재현해 보니 결과가 이렇게 나옵니다.

```
한글 → /workflows/%25ED%2595%259C%25EA%25B8%2580   (이중 인코딩)
```

이 때문에 엔진은 id를 `%ED%95%9C…`라는 글자로 받고, 한글 id인 Workflow·Agent를 디자이너에서 열면 "없음"이 됩니다. 영문 id는 인코딩할 글자가 없어서 지금까지 드러나지 않았습니다. 고치려면 서버 쪽에서 `.build(true)`(이미 인코딩됨)로 바꾸거나 JS에서 인코딩을 빼면 됩니다. 아직 수정하지는 않았습니다.

## 4. 운영상 주의할 점 (치명적이지는 않음)

- 다른 서버로 옮길 때 JVM 로케일이 `POSIX`/`C`이면 한글 파일명이 `?`로 바뀌어 정의를 읽지 못합니다. 지금 쓰는 환경들은 괜찮지만, 새 서버를 붙일 때는 `LANG`이 UTF-8 계열인지 확인해야 합니다.
- zip으로 주고받거나 Windows ↔ Linux로 복사할 때 한글 파일명이 깨질 수 있습니다. git은 경로를 UTF-8로 다루므로 git으로 동기화하면 괜찮습니다.

**추천**: Workflow id와 파일명은 한글로 써도 되지만, Agent id는 영문으로 두고 사람이 읽을 이름은 `description`에 적는 쪽을 권합니다. 한글 id를 실제로 쓸 계획이라면 3번 디자이너 버그부터 고쳐 드릴까요?

---

## 질문

그럼 step 들의 id 는 한글명이어도 상관없나?

---

## 답변

안 됩니다. step id에 한글을 쓰면 **부팅할 때 검사에서 막힙니다.**

- 검사 규칙은 `WorkFlowRegistry.java:95`의 `STEP_ID = [A-Za-z_][A-Za-z0-9_]*`이고, `WorkFlowRegistry.java:330`에서 모든 step id를 이 규칙으로 확인합니다.
- 영문자나 밑줄로 시작하고 영문·숫자·밑줄만 쓸 수 있습니다. 한글, 하이픈, 공백이 하나라도 있으면 그 Workflow가 등록되지 않습니다. 기동할 때면 부팅이 실패하고, 디자이너에서 저장할 때면 검사 실패로 저장되지 않습니다.
- forEach 항목 변수명(`itemKey`)과 `state:` 이름(`save` 대상)도 같은 규칙입니다.

게다가 이 프로젝트는 step id를 `step00`~`step99` 순번으로만 쓰기로 이미 정해 두었습니다. 그래서 한글을 허용하더라도 이름이 섞인 id는 쓰지 않는 것이 맞습니다.

step이 무슨 일을 하는지 한글로 보이게 하고 싶다면 id 대신 이렇게 하시면 됩니다.

```yaml
steps:
  # 요구사항 분석
  - id: step02
    type: AGENT
    ...
```

YAML 주석으로 다는 방법입니다. 다만 디자이너에서 모델을 고치면 YAML이 다시 만들어지면서 본문 주석이 사라지는 점은 알고 계셔야 합니다(머리말 주석은 남습니다). 디자이너 노드에 한글 이름을 띄우려면 step에 `description` 같은 필드를 새로 추가해야 하는데, 지금은 그런 필드가 없습니다. 원하시면 그 방향으로 만들어 드리겠습니다.

---

## 질문

아냐. 알겠어. 한글은 지양하는걸로.

---

## 답변

네, 알겠습니다. 앞으로 정의 파일을 만들 때 파일명과 Workflow·Agent·step id는 영문으로 짓고, 사람이 읽을 이름은 `description`이나 주석에 적겠습니다. 다음 대화에서도 이어지도록 이 결정을 메모리에 저장해 두었습니다.

앞에서 말씀드린 디자이너 이중 인코딩 버그는 한글 id를 쓰지 않으면 드러나지 않습니다. 그래서 고치지 않고 그대로 두었습니다.

---

## 질문

dstone-ai-engine 의 agent 디자이너에서 Tool 과 Sub Agent 등을 검색팝업 방식으로 하는게 어떨까? 갯수가 늘어날 것 같아.
그리고 '참고자료'가 뭔지 모르겠어.

---

## 답변

먼저 Agent 창의 양식 부분을 살펴보겠습니다.

---

## 답변

`참고자료`는 `context.sources`를 가리킵니다. 엔진에서 어떤 의미인지 확인하고, 화면의 대화상자 구조도 함께 보겠습니다.

---

## 답변

JSP에 검색 팝업 틀을 추가합니다.

---

## 답변

Agent 양식의 Tool·Sub Agent·참고자료 부분을 바꿉니다.

---

## 답변

이어서 CSS를 추가합니다.

---

## 답변

`checkList`가 없어지면서 쓰는 곳이 사라진 `checkedValues`와 `.wd-check-list` CSS도 함께 정리합니다.

---

## 답변

이제 실제 화면에서 동작을 확인하겠습니다. 지금 떠 있는 인스턴스부터 봅니다.

---

## 답변

이전 검증 때 쓴 환경(엔진 8181, boot 7181, Playwright)이 남아 있으니 그대로 다시 띄워 보겠습니다.

---

## 답변

엔진 복사본(정의 수정 끔)과 수정한 boot를 다른 포트로 띄웁니다.

---

## 답변

오류가 없어졌습니다. 화면 모양을 보겠습니다.

---

## 답변

문서도 새 방식에 맞게 고칩니다.

---

## 답변

말씀하신 대로 Tool과 Sub Agent는 검색 팝업으로 고르게 바꿨고, '참고자료'는 이름과 설명을 다시 썼습니다. 커밋은 하지 않았습니다.

## '참고자료'가 무엇이었나

Agent 파일의 `context.sources` 설정입니다. 실제로 의미 있는 값은 하나뿐이었습니다.

- **`retrievedDocuments`**: 켜면 Agent를 부를 때마다 dstone-knowledge에 올린 문서에서 비슷한 내용을 찾아 프롬프트에 붙입니다(RAG). 검색어는 그 Agent가 받은 input입니다. 소스 코드는 찾지 않습니다.
- **`workflowInput`**: Agent가 받는 input은 이 값이 없어도 늘 전달됩니다. 체크해도 아무 효과가 없는 칸이었습니다.

그래서 섹션 이름을 **"문서 검색(RAG)"**으로 바꾸고, 위 설명을 붙인 체크 하나만 남겼습니다. 예전 파일에 `workflowInput`이 적혀 있으면 화면에는 보이지 않지만 파일에는 그대로 남습니다.

## Tool·Sub Agent 고르기 창

- Agent 창에는 **고른 것만 칩으로** 보입니다. 칩의 ×를 누르면 빠지고, **[+ 찾아서 추가]**를 누르면 고르기 창이 Agent 창 위에 뜹니다.
- 고르기 창에서는 이름과 설명으로 찾을 수 있습니다. Sub Agent는 폴더(`pilot`, `sample` 등)로도 찾습니다. 낱말을 여러 개 적으면 모두 들어 있는 것만 남습니다.
- 한 줄마다 설명이 두 줄까지 보이고, **[고른 것만]** 체크로 고른 것만 추려 볼 수 있습니다.
- [고르기]를 눌러야 반영됩니다. [취소]나 Esc를 누르면 창 안에서 바꾼 것은 버려집니다. Esc는 고르기 창만 닫고 Agent 창은 그대로 둡니다.
- Sub Agent로 고를 수 없는 Agent(설명이 없거나 자기 Sub Agent가 있는 것)는 지금처럼 흐리게 보이고 체크할 수 없습니다. 등록되지 않은 이름은 노란 칩으로 표시됩니다.

## 확인한 것

엔진 복사본(8181, 정의 수정 끔)과 수정한 boot(7181)를 띄우고 헤드리스 Chromium으로 돌렸습니다.

- 검색, 추가, 빼기, 취소, Esc, [고른 것만]이 모두 기대대로 동작했고, 고른 결과가 [텍스트] 탭에 들어갔습니다.
- RAG를 켜고 끄면 retrieval 칸이 나왔다 사라지고, 끄면 `context`가 지워졌습니다.
- 수정한 Agent는 엔진 [검사]를 통과했습니다.
- 390px 좁은 화면에서도 화면이 깨지지 않았습니다.
- 테스트 중에 오류가 하나 있었습니다. 검색 칸에서 Esc를 누르면 창이 닫힌 뒤에 이벤트가 한 번 더 와서 스크립트 오류가 났는데, 고친 뒤 다시 돌려 오류가 없는 것을 확인했습니다.
- 사람이 직접 써 보지는 않았습니다.

## 바뀐 파일

- `designer.jsp`: 고르기 창 틀
- `workflow-designer.js`: 칩 목록, 고르기 창, RAG 체크. 쓰는 곳이 없어진 `checkList`와 `checkedValues`는 지웠습니다.
- `main.css`: 칩과 고르기 창 스타일. 안 쓰게 된 `.wd-check-list`는 지웠습니다.
- `docs/06.dstone-boot.md`, `CLAUDE.md`: 설명을 새 방식에 맞게 고쳤습니다.

---

## 질문

올려줘.

---

## 답변

커밋해서 `origin/main`에 푸시했습니다(커밋 `77a7f59f`). 로컬 `main`과 원격이 같은 상태입니다.

커밋에 들어간 파일은 이번에 고친 다섯 개입니다. 고르기 창 화면(`designer.jsp`, `workflow-designer.js`, `main.css`)과 설명 문서 두 개(`docs/06.dstone-boot.md`, `CLAUDE.md`)입니다.

---

## 질문

이 내용과는 별개로 헤드리스가 뭐지?

---

## 답변

헤드리스(headless)는 **화면 없이 돌아가는 브라우저**를 말합니다. 'head'가 사람이 보는 창을 가리키는 말이라, 창 없이 뒤에서만 돈다는 뜻입니다.

평소 쓰는 크롬과 엔진은 같습니다. 페이지를 불러오고, JavaScript를 실행하고, 화면 배치(레이아웃)도 똑같이 계산합니다. 다만 그 결과를 모니터에 띄우지 않고 메모리 안에서만 그립니다.

## 이번에 쓴 방식

1. Playwright라는 도구로 헤드리스 Chromium(크롬의 오픈소스 버전)을 띄웠습니다.
2. 스크립트가 사람 대신 화면을 조작했습니다. 로그인하고, 디자이너를 열고, step을 클릭하고, [+ 찾아서 추가]를 누르고, 검색 칸에 `pilot`을 입력하고, 체크하고, Esc를 누르는 식입니다.
3. 동작이 끝날 때마다 결과를 확인했습니다. 칩이 늘었는지, [텍스트] 탭에 `readFileLines`가 들어갔는지, 스크립트 오류가 없는지 등입니다.
4. 중간중간 스크린샷을 찍어 모양을 확인했습니다. 앞에서 보여 드린 이미지가 그것입니다.

## 쓰는 이유

- 이 WSL에는 모니터가 붙어 있지 않아서, 화면을 확인하려면 이 방법밖에 없습니다.
- 클릭 순서가 스크립트로 남아 있어서, 고친 뒤 같은 확인을 몇 초 만에 다시 돌릴 수 있습니다. 이번에 Esc 오류를 고치고 재확인한 것도 이렇게 했습니다.

## 한계

- 한글 글꼴이 깔려 있지 않아 스크린샷에서 한글이 네모로 보였습니다. 화면 문제가 아니라 그 브라우저 환경 문제입니다.
- 스크립트가 확인하는 건 정해 둔 항목뿐입니다. "보기 불편하다", "버튼이 어디 있는지 모르겠다" 같은 사람의 느낌은 잡지 못합니다. 그래서 확인 결과에 "사람이 직접 써 보지는 않았다"고 따로 적어 드렸습니다.

---

## 질문

dstone-ai-engine 의 workflow 디자이너에서 settings.checkpoint 는 항상 true 이어야 하지 않나? 체크하지 않으면 step 진행 할 때마다 저장하지 않아?

---

## 답변

엔진 코드에서 `checkpoint`가 실제로 무엇을 하는지 확인해 보겠습니다.

---

## 답변

아니요, 체크하지 않아도 저장합니다. 화면의 체크박스가 오해를 부르는 것뿐입니다.

## 엔진의 실제 동작

- `WorkFlowExecutor`는 `checkpoint` 값을 아예 읽지 않습니다. step이 끝날 때마다 무조건 `executionStore.update()`를 부릅니다(`WorkFlowExecutor.java:279`). 승인 대기에 들어갈 때(229), 끝날 때(267), 실패할 때(298)도 마찬가지입니다.
- `checkpoint`를 보는 곳은 부팅 검사 한 곳뿐입니다(`WorkFlowRegistry.java:303`). `false`를 적으면 "true만 적을 수 있습니다(엔진은 항상 저장합니다. 끄는 기능은 없습니다)"라며 막습니다.
- 결국 `true`라고 적든 안 적든 동작은 똑같습니다. 이 키는 설계 문서 형식을 따라 남겨 둔 표시일 뿐이고, 실제로 `checkpoint: true`를 적은 정의도 `pilot`, `sm`, `sample-approval-pause-resume` 세 개뿐입니다.

## 화면의 문제

디자이너에는 "step마다 상태 저장" 체크박스가 있습니다(`workflow-designer.js:1345`). 체크를 풀면 키가 지워질 뿐이고 저장은 계속됩니다. 그런데 화면만 보면 꺼질 것처럼 읽힙니다.

체크박스를 빼고 "실행 상태는 step마다 항상 저장됩니다"라는 안내 한 줄로 바꾸는 걸 권합니다. 파일에 `checkpoint: true`가 있으면 그대로 두고, 화면에서는 고칠 수 없게 하는 방식입니다. 이렇게 바꿔 드릴까요?

---

## 질문

응 그게 맞는 것 같아.

---

## 답변

문서에는 이 체크박스 설명이 없어서 고칠 곳이 없습니다. 화면만 빠르게 확인하겠습니다.

---

## 답변

디자이너의 Workflow 설정 패널에서 `checkpoint` 체크박스를 빼고, 그 자리에 "실행 상태는 step이 끝날 때마다 늘 DB에 저장됩니다(settings.checkpoint와 상관없음)." 안내 한 줄을 넣었습니다. 체크 값을 파일에 쓰던 코드도 지웠습니다.

이전과 같은 방식(엔진 8181, boot 7181, 헤드리스 Chromium)으로 `pilot-workflow`를 열어 확인했습니다.

- 체크박스는 없어지고 안내 문구가 보입니다.
- `maxIterations`를 고쳐도 파일에 원래 있던 `checkpoint: true`는 그대로 남습니다.
- 스크립트 오류는 없었습니다.

문서는 이미 "엔진은 늘 저장하고 `false`는 기동 실패"라고 맞게 적혀 있어서 고치지 않았습니다. 바뀐 파일은 `workflow-designer.js` 하나이고, 아직 커밋하지 않았습니다. 올려 드릴까요?

---

## 질문

올려줘.

---

## 답변

커밋해서 `origin/main`에 푸시했습니다(커밋 `4626954e`). 로컬 `main`과 원격이 같은 상태입니다.

---

## 질문

<pasted_content id="916b">
아래와 같은 에러가 나는 이유가 뭘까?
|--------------------------------------------------------------------------------------------------------------------------------------|
[AgentExecutor - agent(id=input-check-agent)] Call 정보 : AgentExecutor.call() Start !!!
<input>
agent=[AgentDefinition[id=input-check-agent, version=1.0.0, role=null, description=IN/OUT 체크 테스트용 에이전트, model=Model[name=null, temperature=null, reasoning=none], prompt=너는 IN/OUT 을 체크하는 에이전트다.[역할]input 으로 들어오는 {inputStr}을  {div}를 구분자로 나누어서 스트링배열을 만든 후 {outArr}에 넣어서 반환한다., input=SchemaDefinition[schema={type=object, properties={inputStr={type=string, description=토큰으로 나눌 인풋스트링}, div={type=string, description=토큰으로 나눌 구분자}}, required=[inputStr, div]}], output=SchemaDefinition[schema={type=object, properties={outArr={type=array, description=토큰으로 나뉜 input 스트링, items={type=string}}}, required=[outArr]}], context=null, tools=null, subAgents=null, allowedCallers=null, execution=Execution[maxAttempts=3, timeoutSeconds=null, onInvalidOutput=RETRY, maxToolCalls=null]]], conversationId=[], caller=[], variables=[], input=[{
  "inputStr" : "바나나|딸기|사과",
  "div" : "|"
}], ragOverride=[], toolsOverride=[], modelOverride=[], engineRule=[]
</input>
|--------------------------------------------------------------------------------------------------------------------------------------|

2026-10-21 09:46:52 ERROR [net.dstone.ai.runtime.workflow.WorkFlowExecutor] step[step01] 실행 중 예외가 발생했습니다(executionId=023d8259-4dea-455b-8a1d-38b552fa289f)
java.lang.IllegalStateException: Not all variables were replaced in the template. Missing variable names are: [outArr].
    at org.springframework.ai.template.st.StTemplateRenderer.validate(StTemplateRenderer.java:145)
    at org.springframework.ai.template.st.StTemplateRenderer.apply(StTemplateRenderer.java:111)
    at org.springframework.ai.chat.prompt.PromptTemplate.render(PromptTemplate.java:143)
    at net.dstone.ai.runtime.agent.AgentExecutor.buildSpec(AgentExecutor.java:428)
    at net.dstone.ai.runtime.agent.AgentExecutor.call(AgentExecutor.java:154)
    ...
</pasted_content id="916b">

---

## 답변

프롬프트의 `{변수}`를 채우는 부분을 확인해 보겠습니다.

---

## 답변

원인은 프롬프트에 적은 **`{outArr}`**입니다.

## 왜 나는가

Agent 프롬프트의 `{이름}`은 "여기에 값을 끼워 넣으라"는 자리 표시입니다. 엔진은 이 자리를 **Agent가 받은 input의 필드 값으로만** 채웁니다(`AgentExecutor.java:428`, Spring AI `PromptTemplate`).

이 Agent의 input 필드는 `inputStr`과 `div` 둘뿐입니다. 그래서 세 자리가 이렇게 처리됩니다.

| 프롬프트의 자리 | 채울 값 | 결과 |
|---|---|---|
| `{inputStr}` | input에 있음 | `바나나\|딸기\|사과`로 바뀜 |
| `{div}` | input에 있음 | `\|`로 바뀜 |
| `{outArr}` | **없음** (output 필드라서) | 채울 값이 없어 오류 |

Spring AI는 채우지 못한 자리가 하나라도 남으면 `Not all variables were replaced… [outArr]`라는 예외를 던집니다. 이 예외는 시스템 오류로 처리되기 때문에 `RETRY`로 다시 시도하지도 않고, 그 자리에서 Workflow 전체가 FAILED로 끝납니다.

output 필드 이름을 프롬프트에 적고 싶다면 중괄호 없이 쓰면 됩니다. 답의 모양은 엔진이 output 스키마를 보고 따로 알려 주므로, 프롬프트에서는 이름만 말해 주면 충분합니다.

## 고치는 법

```markdown
[역할]
input으로 들어오는 {inputStr}을 {div}를 구분자로 나누어 문자열 배열을 만든 뒤, outArr에 넣어 반환한다.
```

`{outArr}`을 `outArr`로 바꾸기만 하면 됩니다.

## 엔진 쪽에서 막을 수도 있습니다

지금은 이런 실수를 **부를 때가 돼서야** 영어 예외로 알게 됩니다. 두 가지를 개선할 수 있습니다.

1. **기동·검사 단계에서 막기**: input이 필드 묶음인 Agent라면, 프롬프트의 `{이름}`이 input 필드에 없을 때 "프롬프트의 {outArr}는 input 필드가 아닙니다(output 필드 이름이면 중괄호를 빼세요)" 같은 오류를 냅니다. 디자이너의 [검사]에서도 바로 보입니다.
2. **실행 중 메시지 바꾸기**: 혹시 통과하더라도 위와 같은 한국어 메시지로 바꿔서 보여 줍니다.

다만 1번은 채팅 API의 `variables`로 채우는 자리까지 오류로 잡을 수 있습니다. 그래서 오류 대신 경고로 둘지 정해야 합니다. 원하시면 이 부분을 확인하고 만들어 드릴까요?

---

## 질문

내가 전반적으로 헷갈리는게 이런 부분들이야. 이전까지 명확하지 않은 IN/OUT 에 대해서 계속 고민했던 부분들이고.
되도록 기능이 통일적으로 적용되었으면 좋겠고 이번건 해당 내용 + 전반적인 가이드를 agent 디자이너의 프롬프트쪽에 가이드팝업으로 붙여줘. UI의 모양도 신경써주고.

---

## 답변

먼저 지금 엔진이 IN/OUT을 실제로 어떻게 처리하는지 정확히 확인하겠습니다. 가이드 내용이 코드와 어긋나면 안 되니까요.

---

## 답변

질문에서 어떤 부분을 더 설명해 드리면 될까요? 막히는 부분을 말씀해 주시면 그에 맞춰 다시 정리하겠습니다.

예를 들어 이런 것들이 있을 수 있습니다.

- `variables`가 정확히 무엇이고, 지금 누가 쓰고 있는지
- 두 선택지가 실제 Agent 파일 작성에서 어떻게 달라지는지
- 아니면 질문 자체가 아니라 IN/OUT 전체 그림을 먼저 보고 싶으신지
