# dstonebootai_의_workf

> 생성일시: 2026-11-02 09:28

## 질문

dstone-boot>ai 의 workflow designer 에서
approval 에 대한 편집은 어떻게 하지?

---

## 답변

APPROVAL은 전용 창이 없고, **캔버스의 step과 화살표를 눌렀을 때 오른쪽 속성 칸**에서 편집합니다.

## 1. step 추가
왼쪽 팔레트의 **APPROVAL**을 캔버스로 끌어다 놓으면 됩니다. 승인자 역할(`approverRole`)은 `PL`로 미리 채워집니다.

## 2. 승인 설정: step 클릭

| 항목 | 내용 |
|---|---|
| `id` / `name` | step ID와 사람이 읽는 이름 |
| `approval.artifact` | 무엇을 승인하는지 적는 이름 |
| `approval.approverRole` | 결정할 사람의 역할 |
| `approval.requireCommentOnReject` | 체크하면 `APPROVED`가 아닌 결정에 의견이 꼭 있어야 함(없으면 400) |
| `save` | 결정 결과 `{decision, approver, comment}`를 저장할 state 이름 |

## 3. 결정 경로: 화살표로 연결
승인자가 고를 선택지는 `routes`이고, **화살표 하나가 선택지 하나**입니다.

1. step의 동그라미를 끌어 다음 step이나 END/FAIL에 놓습니다.
2. 경로 이름은 첫 화살표가 `APPROVED`, 둘째가 `REJECTED`, 그다음부터 `경로1`, `경로2`…로 자동으로 붙습니다.
3. 이름이나 갈 곳을 바꾸려면 화살표를 눌러 **경로 이름**과 **가는 곳**을 고칩니다. `진행`, `재작업`, `중단`처럼 자유롭게 지어도 되고, 이 이름이 승인 화면의 버튼이 됩니다.

반려 후 고쳐서 다시 승인받게 하려면 `REJECTED` 화살표를 앞 step으로 이으면 됩니다. 되돌아간 step에서 `${state.approval.comment:}`로 승인자의 의견을 읽을 수 있습니다(`save: approval`인 경우).

## 4. 저장
**[검사]** 후 저장합니다. YAML 탭에서 직접 고쳐도 되고, 결과는 이런 모양입니다.

```yaml
- id: step03
  type: APPROVAL
  approval:
    artifact: analysis-document
    approverRole: PL
    requireCommentOnReject: true
  save: approval
  routes:
    APPROVED: step04
    REJECTED: step02
```

`save`에 적은 이름은 Workflow의 `state:`에도 선언되어 있어야 합니다. 빠지면 검사에서 실패합니다.

## 주의할 점
- **`artifact` 도움말이 실제와 다릅니다.** 디자이너에는 "승인자에게 보여 줄 값(표현식)"이라고 나오지만, 엔진은 `${state.x}`를 풀지 않고 적힌 글자 그대로 `pendingApproval`에 내보냅니다. 게다가 실행 관리 화면(`workflow-admin.js`)은 이 값을 표시하지 않으니, 지금은 승인자가 산출물 내용을 화면에서 볼 수 없습니다.
- **`approverRole`은 표시용입니다.** 서버가 권한을 검사하지 않으므로 누구나 결정할 수 있습니다.
- **`next`와 `onFailure`는 쓸 수 없습니다.** APPROVAL의 화살표 종류는 경로(`routes`)뿐입니다.
- **`approval.rejectTo`는 화면에서 새로 만들 수 없습니다.** YAML에 이미 있는 값은 화살표로 표시되며, 새로 만들 때는 `REJECTED` 경로를 쓰면 됩니다.

실제 승인과 반려는 디자이너가 아니라 **관리자 > Workflow 실행 관리** 화면에서 합니다. 이 내용은 코드를 읽고 확인했으며, 화면을 직접 띄워 보지는 않았습니다.

---

## 질문

왜 연결점을 편집할 때 들어가는쪽의  아래는  안보이는가?

---

## 답변

**코드에서 일부러 뺐기 때문입니다.** 아래쪽 점은 나가는 전용, 위쪽 점은 들어오는 전용으로 정해져 있습니다(`workflow-designer.js:1147-1148`).

```js
var SOURCE_PORTS = ["out", "left", "right"];   // 나가는 쪽: 아래, 왼쪽, 오른쪽
var TARGET_PORTS = ["in", "left", "right"];    // 들어가는 쪽: 위, 왼쪽, 오른쪽
```

| 연결점 | 나가는 쪽 | 들어가는 쪽 |
|---|---|---|
| 위(`in`) | 불가 | 가능 |
| 아래(`out`) | 가능 | 불가 |
| 왼쪽, 오른쪽 | 가능 | 가능 |

그래서 나가는 쪽 목록에도 **위**가 없습니다. 이 제한은 목록에만 있는 게 아니라 세 군데에 걸려 있습니다.

- **그림에서 끌어 잇기:** `validateConnection`(1199행)이 아래쪽 점에 화살표를 놓지 못하게 막습니다.
- **저장된 배치 값:** `portsOf`(1596행)와 `setEdgePorts`(1605행)는 목록에 없는 값을 기본값(아래에서 나가 위로 들어감)으로 되돌립니다.
- **노드 종류:** 시작 노드에는 아래쪽 점만, END/FAIL 노드에는 위쪽 점만 기본으로 붙습니다.

이유가 주석에 적혀 있지는 않습니다. 위에서 아래로 흐르는 배치(dagre)에 맞춰 위쪽은 입구, 아래쪽은 출구로 정하고, 2026-10-21에 좌우 점을 추가하면서 양방향을 허용한 것으로 보입니다(커밋 `791d089d`).

이 값은 그림에만 쓰이고 실행에는 영향이 없으므로, 기술적인 제약은 아닙니다. 아래쪽으로 들어가게 하려면 `TARGET_PORTS`에 `"out"`을 넣는 것만으로는 부족합니다. 끌어 놓기가 되도록 아래쪽 점의 자석 설정과 `validateConnection`도 함께 손봐야 합니다.
