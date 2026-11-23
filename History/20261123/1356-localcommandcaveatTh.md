# localcommandcaveatTh

> 생성일시: 2026-11-23 13:56

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

Workflow 디자이너 에서
화살표를 선택했을 때 보여지는 우측 상세사항 섹션에서 [가는곳]에 step아이디만 보여주지 말고 이름도 함께 보여줘.

---

## 답변

문서에도 한 줄 반영하는 중입니다.

---

## 답변

화살표를 골랐을 때 오른쪽 상세 칸의 [가는 곳] 목록이 이제 step 이름도 함께 보여 줍니다. 문법 검사(`node --check`)만 통과했고, 실제 화면에서는 아직 확인하지 않았습니다.

- **표시 방식**: 이름이 있는 step은 `step명 (step04)`처럼 나옵니다. 이름이 없는 step, END·FAIL, 없는 step을 가리키는 화살표는 지금처럼 id만 나옵니다.
- **고른 값**: 목록에서 고른 값은 그대로 step id로 저장되므로 동작은 바뀌지 않습니다.
- **수정한 파일**: `dstone-ai-engine/src/main/resources/static/ai/assets/js/workflow-designer.js`의 `targetOptions()`를 고쳤습니다.
- **문서**: `docs/09.dstone-ai-engine.md`의 디자이너 설명에도 한 줄 넣었습니다.

정적 파일이 jar 안에 들어 있어서, 화면에서 보려면 dstone-ai-engine을 다시 빌드한 뒤 재시작해야 합니다.
