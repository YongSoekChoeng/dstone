# localcommandcaveatTh

> 생성일시: 2026-11-25 16:45

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

---

## 질문

2번을 진행하게 되면 화살표에서 설정하게 되는건가?

---

## 답변

네, 화살표에 붙이는 방식이 가장 자연스럽다고 봅니다. 다만 디자이너 화면 코드를 아직 자세히 읽지 않아서, 구체적인 모양은 확인 뒤에 정해야 합니다.

- `approval.hints`의 키는 `routes`에 적은 결정 이름과 같습니다. 그래서 APPROVAL step에서 나가는 화살표(결정 하나)마다 안내문이 하나씩 대응합니다.
- 디자이너는 `workflow-designer.js`에서 나가는 화살표를 `{kind: "route", route: 이름, target}` 목록으로 이미 다룹니다. 화살표를 고르면 결정 이름과 도착 step을 편집하는 곳이 있을 테니, 거기에 "안내문(풍선도움말)" 입력칸을 하나 더 두면 됩니다.
- 화살표의 결정 이름을 바꾸거나 지울 때 `hints`의 키도 함께 바꾸거나 지워야 합니다. 안 그러면 엔진이 부팅 때 "routes에 없는 키"라고 거절합니다. 이것만 놓치지 않으면 됩니다.

대안은 APPROVAL step의 속성 양식에 "결정별 안내문" 표를 따로 두는 것입니다. 하지만 결정 이름을 두 곳에서 관리하게 되어 어긋나기 쉽습니다. 그래서 화살표 쪽을 권합니다.

화살표 방식으로 진행할까요?

---

## 질문

<pasted_content id="7e09">
  - 화살표의 결정 이름을 바꾸거나 지울 때 hints의 키도 함께 바꾸거나 지워야 합니다. 안 그러면 엔진이 부팅 때 "routes에 없는 키"라고 거절합니다. 이것만 놓치지 않으면
    됩니다.
    ==>> 이해가 안되는데... hint는 아래와 같이 가는것 아닌가?
    {kind: "route", route: 이름, hint: 어쩌구 저쩌구(옵셔널, 빈값 허용-빈값일 경우 풍선도움말 안보여줌), target}
</pasted_content id="7e09">

---

## 답변

맞습니다. 화면에서 다루는 편집 모델은 말씀하신 모양이 맞습니다.

```
{kind: "route", route: 이름, hint: "…"(선택, 빈값이면 안 보임), target}
```

제가 걱정한 건 저장되는 YAML의 모양입니다. 엔진은 `hints`를 화살표가 아니라 승인 step 안에 따로 모아 둔 맵으로 받습니다.

```yaml
approval:
  hints:
    승인(개발진행, 전체 확인): 안내문…
routes:
  승인(개발진행, 전체 확인): step14
```

그래서 디자이너가 화살표 편집 모델을 YAML로 저장할 때 `hint`를 `approval.hints[이름]`으로 옮겨 담아야 합니다. 결정 이름을 바꾸면 `hints`의 키도 같이 바뀌게 하고, 화살표를 지우면 해당 `hints` 항목도 지웁니다. 빈값이면 `hints`에 아예 넣지 않고, `hints`가 비면 `hints:` 키도 지웁니다.

디자이너가 화살표별 `hint`를 들고 있다가 저장할 때 맵으로 합치면, 이름을 바꾸거나 지울 때 키가 어긋날 일이 없습니다. 화면에서는 화살표 하나에 `hint`가 달린 것으로만 보입니다. 이 방식으로 진행하겠습니다.

---

## 질문

네 말은 YAML 모드에서 편집, 저장할때의 경우를 말하는 거지?

---

## 답변

네, 맞습니다. 다만 "YAML 모드"라는 화면이 따로 있는지는 아직 확인하지 못했습니다. 제가 말한 건 디자이너가 편집 내용을 YAML로 저장하는 순간입니다. 그때 화살표의 `hint`를 `approval.hints` 맵으로 바꿔 써야 합니다. 반대로 YAML을 읽어 화면에 올릴 때는 `approval.hints[이름]`을 해당 화살표의 `hint`로 옮깁니다.

디자이너에 YAML 텍스트를 직접 고치는 모드가 따로 있다면, 거기서 `hints`의 키를 잘못 쓰거나 `routes`에서 이름을 바꾸고 `hints`는 그대로 두는 실수는 사용자가 낼 수 있습니다. 그 경우는 저장 때 엔진과 같은 검사(`hints` 키가 `routes`에 없으면 거절)를 하고 오류 메시지로 알려 주면 됩니다.

구현에 들어가면 먼저 `workflow-designer.js`에서 그 모드가 있는지, 화살표 편집과 YAML 변환이 어떻게 이어지는지 읽어 보겠습니다. 그 결과에 맞춰 위 방식을 적용하겠습니다.
