# localcommandcaveatTh

> 생성일시: 2026-11-25 16:33

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

조금전에 진행했던 풍선도움말 Workflow 실행 관리 에서도 넣어줘.

---

## 답변

직전 커밋에서 한 풍선도움말 작업을 먼저 확인하겠습니다.

---

## 답변

방금 커밋(`e199ad0a`)에서 넣은 풍선도움말이 이미 Workflow 실행 관리 화면(`admin/workflow/workflow.html`)에 들어 있습니다. 새로 고칠 곳을 찾지 못했습니다.

- `workflow-admin.js`의 `renderDecisionButtons`가 `pendingApproval.hints`를 읽습니다. 안내문이 있는 결정은 줄에 `title`로 붙이고 점선 밑줄(`.wa-choice-name.has-hint`)로 표시합니다.
- 실행 관리에서 풍선도움말이 뜨는 곳은 승인 대기 중인 결정 선택 목록뿐입니다.
- 안내문은 sm-workflow의 설계 승인(`step13`)과 `sample-approval-routes-loop`에만 적혀 있습니다. 다른 Workflow의 승인 step은 `approval.hints`를 적어야 풍선도움말이 뜹니다.

화면에 안 보인다면 실행 중인 서버가 옛 jar일 가능성이 큽니다. `PendingApproval`에 `hints` 필드가 새로 생겼으니 재빌드해서 새 포트로 띄워 확인해 보세요. 실행 중인 jar는 그 자리에서 다시 빌드하지 않는 규칙이 있습니다.

아래 중 원하시는 것이 있으면 알려 주세요. 바로 하겠습니다.

1. 진행 기록에서 이미 내린 결정(예: `승인(개발진행, 간이 확인)`)에도 같은 풍선도움말을 붙입니다.
2. Workflow 디자이너에서 `approval.hints`를 편집할 수 있게 합니다. 지금은 편집 항목 목록에 없습니다.
3. 다른 Workflow의 승인 step에도 `hints`를 적습니다.
