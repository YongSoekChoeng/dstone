# localcommandcaveatTh

> 생성일시: 2026-11-25 09:27

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="060d">
dstone-ai-engine 의 sm-workflow 에서

개발승인 스텝에서 아래의 진행루트 내용을 상세하고 쉽게 (노드별로 자세하게)설명해줘.
재개발 → 21-재개발 전 설계서 다시 읽기 (step20)
</pasted_content id="060d">

---

## 답변

여기서 말하는 '재개발'은 개발 승인(`step19`, 20-개발 승인)에서 사람이 고르는 선택지 가운데 하나입니다. 고르면 `step20`부터 아래 순서로 진행합니다. 정의 파일은 `dstone-ai-engine/conf/definitions/workflows/sm/sm-workflow.yml`(2.7.0)입니다.

## 전체 흐름 한눈에 보기

```
[step19] 20-개발 승인 ── 사람이 "재개발" + 의견
   │
   ▼
[step20] 21-재개발 전 설계서 다시 읽기          (TOOL readFile)
   ▼
[step21] 22-재개발 전 태스크아이템 목록 다시 읽기 (TOOL readFile)
   ▼
[step17] 18-개발                                 (AGENT sm-developer-agent)
   ▼
[step18] 19-컴파일 확인 ──실패──────────────────────────────▶ step19
   │통과
   ▼
[step28] 29-자동 검증할 프로젝트 확인 ──프로젝트ID 없음─────▶ step19
   ▼
[step42] 30-첫 검증인지 ── over(재개발은 늘 여기) ─▶ step41
   ▼
[step41] 31-검증 전 설계서 다시 읽기
   ▼
[step40] 32-검증 전 바뀐 파일 확인
   ▼
[step29] 33-격리 기동 확인 ──기동 실패──▶ [step30] 개발 결과 탓? ─아니오─▶ step19
   │기동 성공                                    │예
   ▼                                             ▼
[step35] 39-동작 검증                         [step31] 자동 수정 횟수 확인
   ▼                                             │남음        │다 씀
[step36] 40-FAIL이 있는지 ─없음─▶ step19          ▼            ▼
   │있음                                    step32→33→34    step19
   └──────────▶ step31                     (자동 수정 후 step18로)
```

`step16`(17-기존개발디렉토리초기화)은 거치지 않습니다. 재개발은 지난 개발 결과를 지우지 않고 그 위에서 고칩니다.

---

## 노드별 설명

### ① step19 · 20-개발 승인 (APPROVAL, 출발점)
- 사람(PL)이 개발 결과를 보고 **재개발**을 고르고, 의견란에 무엇을 고칠지 적습니다. 예: "목록 정렬을 등록일 역순으로".
- 이 결정은 `state.developApproval`에 저장됩니다. 엔진은 결정 기록(`decisions`)에도 이 결정을 남깁니다.
- 뒤에 나오는 개발 Agent는 이 결정을 `${arrival}`로 받아 "이번에 할 일"로 삼습니다. 중간에 TOOL step을 몇 개 거쳐도 마지막 결정은 그대로 이 재개발 지시입니다.

### ② step20 · 21-재개발 전 설계서 다시 읽기 (TOOL `readFile`)
- **하는 일**: 작업 폴더의 `03-1.design.md`를 최대 60,000자까지 읽어 `state.design`에 다시 넣습니다.
- **왜 다시 읽나**: `state.design`에 들어 있는 것은 설계 승인 직후(`step14`)에 읽은 옛 내용입니다. 그 뒤로 설계서가 바뀌었을 수 있습니다.
  - 지난 재개발에서 개발 Agent가 고친 내용을 설계서에도 적어 두었을 수 있습니다(`## 수정 이력` 포함).
  - 전문가 해결(`sm-troubleshooter-agent`)이나 자동 수정(`sm-auto-fix-agent`)이 설계서를 고쳤을 수 있습니다.
  - 다시 읽지 않으면 개발 Agent가 옛 설계서를 기준으로 작업해서 앞서 고친 내용을 되돌릴 수 있습니다.
- **실패하면**: 파일이 없거나 60,000자를 넘으면 `onFailure: FAIL`로 Workflow 전체가 멈춥니다. 잘린 설계서로 개발하지 않게 하려는 것입니다.

### ③ step21 · 22-재개발 전 태스크아이템 목록 다시 읽기 (TOOL `readFile`)
- **하는 일**: `03-2.taskitems.md`를 읽어 `state.taskitems`에 다시 넣습니다.
- **왜 다시 읽나**: 지난 개발에서 Agent가 이 파일의 항목마다 '완료'나 '보류'와 변경 요약을 적었습니다. 재개발에서 추가된 항목이 들어 있을 수도 있습니다. 개발 Agent가 이번에 무엇이 끝났고 무엇이 남았는지 알려면 최신 목록이 필요합니다.
- **실패하면**: `step20`과 마찬가지로 FAIL로 멈춥니다.
- 끝나면 `next: step17`로 개발 step에 **되돌아갑니다**.

### ④ step17 · 18-개발 (AGENT `sm-developer-agent`)
**받는 입력**

| 입력 | 값 | 의미 |
|---|---|---|
| `base_dir` | `${input}/${state.now}` | 작업 폴더 |
| `source_dir` | `state.sourceDir` | 원본 소스 루트(읽기만 함) |
| `design` / `taskitems` | 방금 다시 읽은 최신 내용 | 개발 기준 |
| `instruction` | `${arrival}` = 재개발 결정과 의견 | **이번에 할 일** |
| `history` | `${decisions}` | 지금까지 내려진 모든 결정(참고용) |
| `compile_error` | `state.compileError` | 지난 컴파일 오류가 있으면 먼저 고침 |

**재개발일 때 Agent가 따르는 규칙** (`agents/sm/sm-developer-agent.md`)
1. `dev-resources/`에 지난 결과가 그대로 있습니다. **지시와 관련된 곳만** 고칩니다. '완료'인 항목도 지시와 관련 있으면 다시 확인합니다.
2. 재개발에서는 **사람의 지시가 설계서보다 앞섭니다.** 지시 때문에 설계서와 달라지는 부분은 '보류'로 두지 않고 고칩니다.
3. 고친 내용은 **설계서에도 옮겨 적습니다**:
   - 설계서에 있는 코드면 dev-resources에 쓴 것과 같은 before/after로 설계서도 고칩니다.
   - 설계서에 없는 곳이면 '변경 전/변경 후' 한 쌍을 덧붙입니다.
   - 파일을 새로 만들었거나 지워야 할 파일이 생기면 설계서와 `03-2.taskitems.md`에 그 항목을 더합니다.
   - 끝으로 설계서 맨 끝 `## 수정 이력`에 "재개발: (지시 요약) → (고친 파일과 내용)" 한 줄을 덧붙입니다.
   - 이렇게 하는 이유가 있습니다. 나중에 '설계 수정'을 거쳐 `step16`이 개발 폴더를 비우고 설계서대로 다시 만들 때 재개발로 고친 내용이 사라지지 않아야 합니다.
4. 원본 소스는 건드리지 않습니다. 고칠 파일은 `dev-resources/` 아래로 복사한 뒤 복사본을 고칩니다. 지울 파일은 `_delete-files.txt`에 적습니다.
5. 끝나기 전에 `compileJava`로 직접 컴파일해 보고, 실패하면 최대 3번까지 고칩니다.
6. `03-2.taskitems.md`의 상태 칸('완료' 또는 '보류'와 이유)을 고쳐 저장합니다. 다음 개발 승인 화면에서 사람이 보는 결과가 이 기록입니다.

**답**: `completed`, `pending`, `pendingReasons`, `notApplied`(반영하지 않은 지시와 이유), `taskitemsRecorded`, `designSynced`(설계서에 옮겨 적었는지).
- 지시를 이 단계에서 반영할 수 없으면 `notApplied`에 적습니다(Skill `handling-instructions`). 예를 들어 실제 DB를 고치라는 지시가 여기에 해당합니다.
- Agent가 실패하면 `onFailure: FAIL`로 멈춥니다.

### ⑤ step18 · 19-컴파일 확인 (TOOL `compileJava`)
- Agent의 자체 확인과 별개로 Workflow가 **한 번 더** 컴파일합니다. 원본 전체를 빌드하지 않습니다. 원본이 만들어 둔 클래스 폴더와 의존성을 참조해서 `dev-resources`에서 바뀐 파일과 그 파일을 쓰는 원본 파일만 `compile-check/`에 컴파일합니다. `_delete-files.txt`에 적힌 파일은 빼고 컴파일합니다.
- **통과하면**: `state.compileResult`에 저장하고 `step28`로 갑니다.
- **실패하면**: 오류를 `state.compileError`에 저장하고 자동 검증 없이 **곧장 `step19`(개발 승인)** 로 돌아갑니다. 사람이 오류를 보고 다시 재개발을 고르면 그 오류가 `compile_error`로 개발 Agent에게 넘어갑니다.

### ⑥ step28 · 29-자동 검증할 프로젝트 확인 (TOOL `readMarkdownSection`)
- 요청서 `00.request.md`의 `## 프로젝트ID` 절을 읽어 `state.projectId`에 둡니다. 격리 시험 서버를 띄울 때 DB 정보를 찾는 데 씁니다.
- **없으면**: 이유를 `state.projectIdError`에 남기고 자동 검증 없이 `step19`로 갑니다.

### ⑦ step42 · 30-첫 검증인지 (TOOL `countUp`, max 1)
- `state.verifyRound`를 하나 올려서 이번이 첫 검증인지 셉니다.
- 첫 검증이면 `within`이 나와 설계 승인에서 고른 검증 생략 옵션('컴파일만', '기동만 확인')을 따릅니다(`step43`→`step44`).
- **재개발로 왔다면 이미 한 번 이상 지나간 뒤라서 늘 `over`입니다.** 그래서 생략 옵션은 보지 않고 **화면 검증 경로(`step41`)** 로 갑니다. 설계 승인 때 "컴파일만"을 골랐더라도 재개발 뒤에는 서버를 띄워 확인합니다. 생략은 첫 검증에만 적용하는 규칙이기 때문입니다.

### ⑧ step41 · 31-검증 전 설계서 다시 읽기 (TOOL `readFile`)
- 개발 Agent가 방금 재개발 내용을 설계서에 옮겨 적었으므로 `03-1.design.md`를 다시 읽어 `state.design`을 갱신합니다.
- 검증 Agent가 고치기 전 설계서를 기준으로 판정하지 않게 하려는 것입니다.
- 실패하면 `step19`로 갑니다.

### ⑨ step40 · 32-검증 전 바뀐 파일 확인 (TOOL `fileChanges`)
- 지난 동작 검증 때 찍어 둔 작업 폴더 스냅숏(`state.workChanges.snapshot`)과 지금을 비교합니다. `compile-check`는 제외합니다.
- 결과 `state.workChanges`(`summary`)는 검증 Agent에 넘어갑니다. 검증 Agent는 이번 재개발로 바뀐 것과 지난번에 안 된 것을 깊게 보고, 나머지는 가볍게 봅니다.
- 실패해도 그대로 `step29`로 갑니다. 비교만 못 할 뿐 검증은 진행합니다.

### ⑩ step29 · 33-격리 기동 확인 (TOOL `startSandboxServer`)
- 일회용 DB 복제본을 만들고 `03-3.ddl(데이터베이스).md`를 반영한 뒤, 원본 + `dev-resources` + 삭제 목록으로 빌드해 시험 서버를 띄웁니다. 원래 DB는 건드리지 않습니다.
- **성공하면**: `state.sandboxCheck`에 저장하고 `step35`로 갑니다. 이 서버는 바로 다음 `step35`가 그대로 넘겨받아 씁니다. 그래서 두 step 사이에는 다른 step을 넣지 않습니다.
- **실패하면**: 이유를 `state.sandboxError`에 저장하고 `step30`으로 갑니다.

### ⑪ step30 · 34-기동 실패가 개발 결과 탓인지 (TOOL `containsValue`)
- `sandboxCheck.cause`가 `code`인지 봅니다.
  - `true`(개발 결과 탓) → `step31` 자동 수정으로 갑니다.
  - `false`(환경 탓: DB 미지원, 포트 등) → 자동 수정으로는 못 고치므로 `step19`로 가서 사람에게 묻습니다.

### ⑫ step35 · 39-동작 검증 (AGENT `sm-verifier-agent`)
- 요구사항분석서의 완료 기준마다 격리 서버에서 실제로 화면을 눌러 보거나 DB를 조회해 **PASS / FAIL / BLOCKED**를 매기고 근거를 남깁니다.
- 입력으로 최신 설계서(`step41`), 바뀐 파일 요약(`step40`), 지난 검증 결과(`previous`)를 받습니다.
- 시험 환경이 원래 서버와 달라서 생긴 실패는 FAIL이 아니라 BLOCKED로 냅니다. 이건 자동 수정 대상이 아닙니다.
- 결과는 `state.verification`에 저장합니다. Agent가 끝내지 못하면 `verificationError`에 남기고 `step19`로 갑니다.

### ⑬ step36 · 40-실패한 완료 기준이 있는지 (TOOL `containsValue`)
- `verification.scenarios` 가운데 `result`가 `FAIL`인 것이 있는지 봅니다.
  - 없음 → **`step19` 개발 승인**. 사람이 재개발 결과와 검증 결과를 함께 보고 다시 판단합니다.
  - 있음 → `step31`

### ⑭ step31 · 35-자동 수정 횟수 확인 (TOOL `countUp`, max 2, `scope: ${decisions}`)
- 자동 수정은 **사람의 결정 하나마다 최대 2번**입니다. `scope`가 결정 기록이라서, 사람이 방금 재개발을 새로 골랐으면 횟수가 0부터 다시 셉니다.
  - `within` → `step32`
  - `over`(2번 다 씀) → `step19`. 남은 실패는 사람이 판단합니다.

### ⑮ step32 → step33 → step34 · 자동 수정
- `step32`/`step33`: 설계서와 태스크아이템 목록을 다시 읽습니다. 재개발 단계에서 이미 바뀌었기 때문입니다.
- `step34` 38-자동 수정(`sm-auto-fix-agent`): 기동 오류(`boot_error`)나 검증 FAIL(`verification`)을 보고 `dev-resources`를 고칩니다.
- 그다음 `step18`(컴파일 확인)로 돌아가 ⑤부터 다시 돕니다. `step42`는 계속 `over`라서 또 화면 검증을 거칩니다.
- 이 고리는 FAIL이 없어지거나, 자동 수정 2번을 다 쓰거나, 환경 문제가 생길 때까지 돕니다. 어느 경우든 마지막에는 `step19` 개발 승인으로 돌아옵니다.

---

## 정리: 재개발 경로의 요점

1. **지우지 않고 고친다**: `step16`(개발 폴더 비우기)을 건너뛰고, 지난 결과 위에서 지시받은 곳만 고칩니다.
2. **최신 문서로 시작한다**: `step20`·`step21`이 설계서와 태스크 목록을 다시 읽습니다. 앞선 재개발, 전문가 해결, 자동 수정에서 고친 내용을 잃지 않기 위해서입니다.
3. **고친 것은 설계서에 남긴다**: 나중에 설계부터 다시 개발해도 같은 결과가 나오게 하려는 것입니다(`## 수정 이력`).
4. **검증은 생략하지 않는다**: 재개발 뒤에는 늘 화면 검증까지 거칩니다. 실패하면 자동 수정이 결정 하나당 최대 2번 붙습니다.
5. **결국 사람에게 돌아온다**: 어느 갈래로 끝나든 `step19` 개발 승인에서 사람이 승인, 재개발, 전문가 해결 같은 다음 결정을 내립니다.
