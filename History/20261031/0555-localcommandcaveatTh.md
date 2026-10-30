# localcommandcaveatTh

> 생성일시: 2026-10-31 05:55

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-boot>knowledge 의 graph 에서 Agent를 독립적으로 생성할 수 있었으면 좋겠어.
말하자면 workflow를 설계할 때 필요한 만큼의 Agent를 먼저 만들어 놓고 workflow를 만들면서 Agent를 선택만 할 수 있게.

---

## 답변

먼저 현재 Workflow 디자이너와 knowledge graph 화면에서 Agent를 어떻게 만들고 고르는지 코드로 확인합니다.

---

## 답변

knowledge 화면에는 Agent 코드가 없습니다. 말씀하신 화면은 AI의 Workflow 디자이너로 보고, Agent를 다루는 부분을 자세히 읽겠습니다.

---

## 답변

디자이너에 Agent 관리 창을 추가합니다. Workflow·step을 고르지 않아도 Agent를 만들고, 고치고, 지울 수 있게 합니다.

---

## 답변

코드 반영은 끝났습니다. 운영 중인 7081은 건드리지 않고, WAR 복사본을 7181 포트에 띄워 브라우저로 실제 동작을 확인합니다.

---

## 답변

브라우저 테스트에서 Workflow 목록이 끝내 채워지지 않아 멈췄습니다. 원인을 확인하고 있습니다.

---

## 답변

페이지 자체 문제가 아니라, 정적 파일과 프록시 호출이 로그인 화면으로 넘어가서 생긴 일입니다. 테스트 복사본만 보안을 끄고 다시 띄웁니다.

---

## 답변

모바일 화면 사진에서 "쓸 곳" 칸이 보이지 않습니다. 한글이 네모로 나오는 것은 테스트 환경에 글꼴이 없어서이고, 이 칸만 따로 확인합니다.

---

## 답변

Workflow 디자이너에 **[Agent 관리]** 창을 추가해서, Workflow나 step 없이도 Agent를 먼저 만들어 둘 수 있습니다. 브라우저 테스트는 모두 통과했지만, 아직 커밋하지 않았고 실행 중인 7081에도 반영되지 않았습니다.

말씀하신 "knowledge의 graph"(노드 맵)에는 Agent나 Workflow 기능이 없어서, AI 메뉴의 **Workflow 디자이너**를 뜻하신 것으로 보고 작업했습니다. 지금까지는 step을 먼저 놓아야 그 step 설정에서 [새 Agent]를 누를 수 있었습니다.

## 사용 방법
1. 위쪽 단추 줄에서 **[Agent 관리]** 를 누릅니다. 전체 화면에서는 맨 위 띠에 있습니다.
2. 쓸 곳(AGENT/SUPERVISOR/ROUTER), 폴더, Agent id를 적고 **[새 Agent 만들기]** 를 누릅니다.
3. 쓸 곳에 맞는 템플릿으로 기존 Agent 창이 열리고, 내용을 채워 저장하면 엔진에 바로 등록됩니다.
4. Workflow를 그릴 때 step 설정의 agent 목록에서 고르면 됩니다.

같은 창의 목록에서 이름이나 설명으로 Agent를 찾아 열거나 지울 수도 있습니다.

## 동작 규칙
- **id 검사:** 영문, 숫자, `-`, `_`만 받고, 이미 있는 id는 막습니다.
- **삭제:** Workflow가 쓰고 있는 Agent는 지워지지 않고 이유가 창에 나옵니다.
- **출력 형식이 있는 Agent:** output을 적은 Agent는 SUPERVISOR·ROUTER step 목록에 나오지 않으므로, 관리 목록에 "AGENT step 전용"으로 표시합니다.
- **ROUTER용 Agent:** 템플릿의 `경로1`, `경로2`를 실제 경로 이름으로 바꿔야 합니다. 이 이름이 step의 `routes` 이름과 다르면 Workflow [검사]에서 막힙니다.

## 확인한 것
WAR 복사본을 7181 포트에 띄우고 헤드리스 Chromium으로 확인했습니다.
- **통과한 항목:** 세 종류 Agent 생성과 저장, 새 Workflow의 step 목록에서 고르기, 사용 중인 Agent 삭제 거부, 전체 화면, 390px 좁은 화면
- **테스트 중 고친 버그:** 삭제 후 Esc로 창이 닫히지 않던 문제와 좁은 화면에서 "쓸 곳" 칸이 잘리던 문제
- **뒷정리:** 테스트로 만든 Agent 파일은 모두 지웠고, 7181도 내렸습니다.

로그인 계정이 없어 정적 파일은 소스 폴더에서 주고, `/ai/designer/call.do` 호출은 8081 엔진으로 바로 넘겨 테스트했습니다. 그래서 로그인한 상태의 실제 화면과 `dstone-boot` 프록시를 거치는 경로는 확인하지 못했습니다. 프록시 코드는 바꾸지 않았습니다.

## 반영
바뀐 파일은 `designer.jsp`, `workflow-designer.js`, `main.css`이고, `docs/06.dstone-boot.md` 10.6절에 설명을 추가했습니다. 화면에서 쓰려면 `dstone-boot`를 다시 빌드해 재기동해야 합니다.
