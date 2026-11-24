# dstoneaiengine_프로젝트의

> 생성일시: 2026-11-24 11:55

## 질문

dstone-ai-engine 프로젝트의 sm-workflow 에서 tool 을 보면 '실패'가 실제로는 실패가 아닌 것들이 있는 것 같아.
이런 step들에 대해서 '실패'를 다른 내용으로 바꿔줘.

---

## 답변

sm-workflow 정의와 Tool step의 '실패' 표시가 어디서 나오는지부터 확인하겠습니다.

---

## 답변

이제 sm-workflow 정의를 고칩니다.

---

## 답변

테스트에 "실행 이력에 성공으로 남는지" 확인을 더합니다.

---

## 답변

테스트를 돌립니다.

---

## 답변

countUp 테스트가 원래부터 건너뛰어지고 있어서, LLM 호출을 끄고 다시 돌려 봅니다.

---

## 답변

8개 모두 통과했습니다(countUp 경로 포함). 전체 테스트도 돌립니다.

---

## 답변

변경 전에도 이 3건은 실패했습니다(step 이름 앞에 붙은 `NN-` 번호 때문이고, 이번 수정과는 관계없습니다). 이제 문서를 맞춥니다.

---

## 답변

sm-workflow에서 실패가 아닌데 실행 이력에 '실패'로 남던 Tool step 3개를 고쳤습니다. 이제 이 step들은 '성공'으로 남고, 결과는 값으로 나와 그 값에 따라 다음 step으로 갑니다. 고친 3개 step의 테스트(`SmVerifyLoopTest` 8건)는 모두 통과했습니다. 커밋은 하지 않았습니다.

| step | 예전 이력 | 지금 이력 → 다음 step |
|---|---|---|
| step30 기동 실패가 개발 결과 탓인지 | 환경 탓이면 실패 | 성공, `false` → 개발 승인 |
| step31 자동 수정 횟수 확인 | 2번을 다 쓰면 실패 | 성공, `over` → 개발 승인 |
| step36 실패한 완료 기준이 있는지 | **FAIL이 없으면(좋은 결과인데도) 실패** | 성공, `false` → 개발 승인 |

세 step 모두 Tool이 정말 오류를 낼 때만 `onFailure`로 가서 '실패'로 남습니다.

**바꾼 내용**
- **Tool:** `containsValue`는 이제 `true`/`false`를 돌려줍니다. `countUp`은 `{route: within|over, count, scope, message}`를 돌려줍니다. 둘 다 이 Workflow에서만 씁니다.
- **엔진:** `countUp`은 결과 전체를 state에 저장하면서 갈 곳도 정해야 합니다. 그래서 TOOL step이 맵으로 답하면 그 안의 `route` 값으로 `routes`를 고르게 했습니다(ROUTER의 `{route, reason}`과 같은 모양). 이 부분은 `ToolStepExecutor.java`에 있습니다.
- **정의:** `sm-workflow.yml`을 2.6.1로 올렸습니다. 디자이너 화면의 화살표 모양(`layout.json`)도 맞췄습니다.
- **문서:** `docs/09`, `ai-engine-history.md`, 디자이너 안내 문구도 고쳤습니다.

**실패로 그대로 둔 것** (실제로 일이 안 된 경우라서입니다)
- step18 컴파일 오류
- step29 격리 기동 실패
- step28 요청서에 프로젝트ID가 없을 때: 자동 검증을 못 하고 그 이유를 들고 개발 승인으로 갑니다. 이것도 바꾸고 싶으시면 말씀해 주세요.

**테스트**
- 전체 259건 중 3건이 실패했습니다. 모두 `SmDecisionFlowTest`에 있고, 이번 수정 전에도 똑같이 실패했습니다. 테스트가 기다리는 step 이름에 `NN-` 번호가 없어서입니다. 이번 작업 범위가 아니라 손대지 않았습니다.
- `countUp` 테스트는 평소 설정에서 건너뛰어집니다. 그래서 `-Ddstone.ai.test.llmcall=false`를 주고 따로 돌려 통과를 확인했습니다.

이미 떠 있는 서버에는 다시 빌드하고 재시작해야 반영됩니다.

---

## 질문

뭘 어떻게 바꾼거지? 그리고 isFileExist 같은 tool은 어떻게 router 설정을 할 수 있는 거지?

---

## 답변

## 1. 무엇을 바꿨나

이 step들은 "있다/없다", "한도 안/한도 끝" 같은 **판정**을 하는데, 예전에는 판정 결과를 **Tool의 성공/실패**로 표현했습니다. 그래서 판정이 "아니오"로 나오면 엔진이 이력에 '실패'를 남기고 `onFailure`로 보냈습니다.

**step36을 예로 들면** (완료 기준에 FAIL이 있는지 보는 step)

```yaml
# 예전 (2.6.0)
  - id: step36
    tool: containsValue           # 있으면 {success:true}, 없으면 {success:false}
    with: { value: ..., field: result, expected: FAIL }
    next: step31                  # FAIL 있음(성공) → 자동 수정
    onFailure: step19             # FAIL 없음(실패) → 개발 승인   ← 좋은 결과인데 '실패'
```

```yaml
# 지금 (2.6.1)
  - id: step36
    tool: containsValue           # 있으면 true, 없으면 false (항상 성공)
    with: { value: ..., field: result, expected: FAIL }
    routes:
      "true": step31              # FAIL 있음 → 자동 수정
      "false": step19             # FAIL 없음 → 개발 승인     ← 이력에는 '성공'
    onFailure: step19             # Tool이 정말 오류를 냈을 때만
```

바꾼 곳은 세 군데입니다.

| 어디 | 예전 | 지금 |
|---|---|---|
| `DataTool.containsValue` | `{success: true/false, message}` | `true` / `false` |
| `DataTool.countUp` | `{success: true/false, count, scope, message}` | `{route: "within"/"over", count, scope, message}` |
| `ToolStepExecutor` | 결과가 맵이면 routes로 갈 곳을 고를 수 없었음(실패 처리) | 맵이면 그 안의 `route` 값으로 고름 |

step30(`containsValue`)과 step31(`countUp`, `routes: {within: step32, over: step19}`)도 같은 방식으로 바꿨습니다.

## 2. isFileExist 같은 Tool은 어떻게 routes를 쓰나

ROUTER step처럼 LLM이 고르는 게 아니라, **Tool이 돌려준 값이 그대로 route 이름**이 됩니다. 별도의 라우터 설정은 없고, TOOL step에 `routes:`만 적으면 됩니다.

```yaml
  - id: step07
    type: TOOL
    tool: isFileExist             # Java: public boolean isFileExist(String filePath)
    with:
      filePath: ${input}/${state.now}/02.impact.md
    routes:
      "true": step38              # Tool이 true를 돌려주면 여기로
      "false": step08             # false면 여기로
    onFailure: FAIL               # 받은 값이 routes에 없거나 Tool 오류일 때
```

엔진은 이렇게 처리합니다(`ToolStepExecutor.run()` → `succeeded()`).

1. Tool을 부르고 응답을 JSON으로 읽습니다. `isFileExist`는 `true`나 `false`를 돌려줍니다.
2. 먼저 성공/실패를 판정합니다. `{success:false}`이거나 `"실패:"`로 시작하는 글자면 실패로 보고 `onFailure`로 갑니다.
3. 성공이고 step에 `routes`가 있으면 갈 곳을 고릅니다.
   - 결과가 글자·숫자·true/false 하나면 그 값을 글자로 바꿔 route 이름으로 씁니다(`true` → `"true"`).
   - 결과가 맵이면 그 안의 `route` 필드 값을 씁니다. 이번에 추가한 부분입니다.
   - 그 이름이 `routes`에 없으면 실패로 보고 `onFailure`로 갑니다.
4. `WorkFlowExecutor.nextStepId()`가 `routes`에서 그 이름을 찾아 다음 step으로 갑니다.

YAML에 `"true"`처럼 따옴표를 붙이는 이유는, 붙이지 않으면 YAML이 키를 boolean으로 읽기 때문입니다. route 이름은 글자로 비교합니다.

기동할 때는 `WorkFlowRegistry.checkToolRoutes()`가 다음을 검사합니다.
- route가 하나 이상 있어야 합니다.
- `next`와 함께 쓸 수 없습니다.
- `forEach`와 함께 쓸 수 없습니다. 반복마다 결과가 따로 나와서 값 하나로 갈 곳을 고를 수 없기 때문입니다.

**정리하면**, 판정용 Tool은 성공/실패 대신 **값**으로 답하게 만들고 그 값으로 `routes`를 나누면 됩니다. 그러면 '실패'는 Tool이 정말 오류를 냈을 때만 남습니다.

---

## 질문

내 말은 디자이너에서 routes 를 어디에서 설정하냐는 말이야.

---

## 답변

디자이너에는 step 설정 칸에 routes 입력란이 없습니다. **화살표를 선택해서 그 화살표의 '종류'를 경로(routes)로 바꾸는 방식**입니다. `workflow-designer.js`의 화살표 설정 패널(3200줄 부근)에 이 기능이 있습니다.

## TOOL step에 routes 다는 순서 (예: isFileExist)

1. **첫 화살표를 경로로 바꾸기**
   - TOOL step 노드의 아래 동그라미를 끌어 갈 step에 놓습니다. TOOL에 routes가 아직 없으면 이 화살표는 `next`가 됩니다.
   - 그 화살표를 누르면 오른쪽에 **화살표** 패널이 뜹니다.
   - **종류**를 `다음(next)`에서 `경로(routes)`로 바꿉니다. 임시 이름 `경로1`이 붙습니다.
   - **경로 이름** 칸에 Tool이 돌려주는 값을 똑같이 적습니다. 예: `true`

2. **두 번째 경로 잇기**
   - 같은 노드에서 다른 step으로 화살표를 하나 더 끕니다. 이미 routes가 있는 TOOL이라 새 화살표는 자동으로 경로(`경로2`)가 됩니다.
   - 그 화살표를 눌러 경로 이름을 `false`로 바꿉니다.

3. **실패 화살표는 따로 둡니다**
   - `onFailure`는 routes와 함께 쓸 수 있습니다. 화살표 종류를 `실패(onFailure)`로 고르면 됩니다.

그러면 YAML에는 이렇게 저장됩니다.
```yaml
    routes:
      "true": step38
      "false": step08
    onFailure: FAIL
```

## 헷갈리기 쉬운 점

- **순서가 중요합니다.** routes가 없는 TOOL step에서 화살표를 새로 끌면 `next`가 먼저 생기고, 그다음은 `onFailure`가 됩니다. 경로로 나누려면 첫 화살표를 1번처럼 먼저 `경로(routes)`로 바꿔야 그 뒤의 화살표도 경로로 생깁니다.
- **TOOL은 `next`와 `routes`를 함께 쓸 수 없습니다.** 둘이 섞이면 디자이너가 막고 오류 문구를 보여 줍니다.
- **경로 이름은 Tool이 돌려주는 값과 글자까지 같아야 합니다.** `isFileExist`·`containsValue`는 `true`/`false`, `countUp`은 `within`/`over`입니다. 값이 routes에 없으면 실행할 때 실패로 보고 `onFailure`로 갑니다.
- **경로 이름은 그림에 표시됩니다.** 화살표 위에 `true`·`false` 같은 이름이 보입니다.

step을 선택했을 때 나오는 설정 패널에는 routes 항목이 없어서 찾기 어려운 게 맞습니다. 원하시면 TOOL step 설정 패널에 "결과 값으로 나누기(routes)" 칸을 두어, 거기서 값과 갈 곳을 바로 적게 만들 수 있습니다.
