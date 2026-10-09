# appdstonedstoneaieng

> 생성일시: 2026-10-10 08:11

## 질문

/app/dstone/dstone-ai-engine/src/main/resources/definitions/agents/*.yml 들에 대해서
파일명을 agent의 id 와 동일하게 수정해줘.

---

## 답변

먼저 agent 정의 파일들의 현재 파일명과 `id` 값을 확인하겠습니다.

---

## 답변

id가 추출되지 않았습니다. 파일 형식을 직접 확인하겠습니다.

---

## 답변

`sample/`, `testApp/` 은 이미 파일명과 id가 같고, 다른 것은 `pilot/` 의 11개뿐입니다. 이름을 바꾸기 전에 옛 파일명을 참조하는 곳을 찾겠습니다.

---

## 답변

이름 변경과 참조 갱신이 끝났습니다. 정의 파일 검증 테스트를 돌려 확인하겠습니다.

---

## 답변

`agents/` 아래 yml 파일명을 모두 agent `id`와 같게 맞췄고, `DefinitionFilesTest`도 통과했습니다.

`sample/` 13개와 `testApp/` 2개는 이미 id와 같아서 그대로 두었고, 바뀐 것은 `pilot/` 11개입니다(`git mv`로 변경).

| 이전 | 이후 |
|---|---|
| `00.source-investigator-agent.yml` | `pilot-source-investigator-agent.yml` |
| `01.requirment-analyzer-agent.yml` | `pilot-requirment-analyzer-agent.yml` |
| `02.impact-analyzer-agent.yml` | `pilot-impact-analyzer-agent.yml` |
| `03.impact-analyzer-review-agent.yml` | `pilot-impact-analyzer-review-agent.yml` |
| `04.architect-agent.yml` | `pilot-architect-agent.yml` |
| `05.developer-agent.yml` | `pilot-developer-agent.yml` |
| `10.request-router-agent.yml` | `pilot-request-router-agent.yml` |
| `21.newdev-reference-agent.yml` | `pilot-newdev-reference-agent.yml` |
| `22.newdev-analyzer-agent.yml` | `pilot-newdev-analyzer-agent.yml` |
| `23.newdev-file-designer-agent.yml` | `pilot-newdev-file-designer-agent.yml` |
| `24.newdev-file-developer-agent.yml` | `pilot-newdev-file-developer-agent.yml` |

- **참조 갱신**: 옛 파일명은 코드에는 없고 주석·문서에만 있어서 그것만 고쳤습니다. 대상은 pilot agent yml 4개의 주석, `pilot-workflow.yml` 주석 1곳, `docs/09.dstone-ai-engine.md`, `CLAUDE.md`입니다. `History/`는 과거 기록이라 그대로 두었습니다.
- **`target/classes` 정리**: 옛 이름 파일이 빌드 결과물에 남아 "agent id가 중복 등록되었습니다"로 테스트가 실패했습니다. `target/classes/definitions/agents/pilot/`의 옛 이름 yml 11개만 지웠습니다.
- **서버 반영 시 주의**: 엔진을 다시 띄울 때는 `mvn clean package`로 빌드하세요. Windows 쪽 클론도 pull 후 clean 빌드를 하지 않으면 같은 중복 오류가 납니다.
- **순서 번호 사라짐**: 파일명 앞 번호(00~24)가 없어져 폴더에서 알파벳순으로 보입니다.

커밋은 하지 않았습니다.

---

## 질문

git에 올려줘

---

## 답변

커밋 `1b909d9`로 `main`에 푸시했습니다.

이번 파일명 변경분(pilot agent yml 11개, `pilot-workflow.yml` 주석, `docs/09.dstone-ai-engine.md`, `CLAUDE.md`)만 담았습니다.

작업 전부터 있던 두 가지는 제가 만든 변경이 아니라서 커밋에 넣지 않았습니다. 같이 올리려면 말씀해 주세요.
- `docs/temp/Agent_Workflow_안정화_방안.docx` 삭제
- `docs/temp/dstone-workflow-design.md` 신규(미추적)

---

## 질문

pilot-workflow.yml 에서 step00 의 output 의 result 를 보면 result: "state.request" 이런 형식으로 되어있는데 
step03 를 보면 request: "${state.request}" 이런 형식으로 사용되고 있어.
이 의미가 정확하게 무엇인가?

---

## 답변

두 표기는 같은 저장소(`state.request`)를 가리키지만 방향이 반대입니다. `output` 쪽은 값을 **넣을 자리의 주소**이고, `input` 쪽은 그 자리에 든 **값을 꺼내는 식**입니다.

```yaml
- id: step00
  type: TOOL
  tool: readFile
  input:
    fileFullPath: "${input}/00-request.md"
  output:
    result: "state.request"        # 쓰기: 이 step의 결과를 state.request 에 넣는다

- id: step03
  type: AGENT
  agent: pilot-impact-analyzer-agent
  input:
    workDir: "${input}"
    request: "${state.request}"    # 읽기: state.request 의 값을 꺼내 Agent 입력의 request 로 준다
```

**`output: { result: "state.request" }` (쓰기)**
- 왼쪽 `result`는 "무엇을 저장할지"입니다. step의 결과(`result`), step에 들어간 입력(`input`), 오류 메시지(`error`) 중 하나를 고릅니다.
- 오른쪽 `"state.request"`는 "어디에 저장할지"입니다. 값이 아니라 이름이므로 `${ }`를 붙이지 않습니다.
- step00에서는 `readFile`이 읽은 `00-request.md` 내용이 `state.request`에 들어갑니다.
- 이렇게 저장하지 않은 결과는 뒤 step에서 읽을 수 없습니다. "직전 step 결과" 같은 암묵적인 이름이 없기 때문입니다.

**`request: "${state.request}"` (읽기)**
- `${ }`는 "이 경로의 값으로 바꿔 넣어라"는 뜻입니다.
- 왼쪽 `request`는 state와 무관하고, `pilot-impact-analyzer-agent`의 입력 스키마에 있는 필드 이름입니다. 여기서는 우연히 이름이 같을 뿐입니다.
- `${ }`를 빼고 `request: "state.request"`라고 쓰면 Agent는 파일 내용이 아니라 `state.request`라는 글자 그대로를 받습니다.

같은 step03 안의 다른 식도 같은 규칙입니다.
- `"${input}"`은 Workflow 요청으로 들어온 입력(작업 폴더 경로)을 읽습니다.
- `"${state.review.reason:}"`의 끝 `:`는 기본값입니다. 값이 아직 없으면(첫 실행이라 리뷰가 없을 때) 빈 문자열이 됩니다.
- step03 자신의 결과는 `result: "state.impact"`로 저장되고, 다음 step들이 `${state.impact…}`로 읽습니다.

기동 검사도 이 짝을 확인합니다. 어떤 step이 `${state.request}`를 읽는데 그 이름에 저장하는 step이 없거나, 저장하는 step이 읽는 step보다 먼저 실행될 수 없으면 서버가 뜨지 않습니다.

---

## 질문

만약에 output 의 result 에 무조건 스트링 "성공"이라는 말을 넣고자 한다면 어떻게 해야하나 ?

---

## 답변

지금 형식으로는 `output`에 고정 글자를 직접 넣을 수 없습니다. `output`의 오른쪽은 값이 아니라 저장할 자리(`state.이름`)여야 하고, 왼쪽은 그 step의 `result` / `input` / `error`(+하위 필드) 중 하나만 됩니다. `result: "성공"`이라고 쓰면 기동 검사에서 "저장 위치는 state.이름이어야 한다"로 실패합니다.

대신 목적에 따라 아래 방법을 쓸 수 있습니다.

1. **읽는 쪽에 직접 적기**: `${ }`가 없는 글자는 그대로 값이므로, 굳이 state를 거칠 필요가 없습니다. 3번과 달리 실행해 보지는 않았고, `input`과 `output.value`가 같은 방식으로 풀린다는 점에 근거한 것입니다.
   ```yaml
   input:
     status: "성공"          # 뒤 step의 input에 바로
   ```
   Workflow 최종 결과에 넣고 싶을 때도 같습니다.
   ```yaml
   output:
     value:
       status: "성공"
       impact: "${state.impact}"
   ```

2. **기본값으로 적기**: 어떤 step이 값을 저장했으면 그 값을, 아니면 "성공"을 쓰고 싶을 때입니다.
   ```yaml
   status: "${state.status:성공}"
   ```

3. **step의 `input`을 저장하기**: `output` 왼쪽에 `input.필드`를 쓸 수 있으므로, 입력에 넣은 고정 글자를 state로 옮길 수 있습니다.
   ```yaml
   input:
     status: "성공"
   output:
     input.status: "state.status"
   ```
   다만 `input`은 부르는 Agent/Tool의 입력 스키마와 맞아야 해서, `status`라는 입력을 받는 대상에서만 가능합니다. 또 `output`은 성공·실패 모두에 기록되므로 step이 실패해도 "성공"이 저장됩니다.

4. **값을 그대로 돌려주는 범용 Tool 추가**: state에 꼭 고정값을 넣어야 한다면(예: 여러 갈래가 같은 이름에 서로 다른 상태를 쓰는 경우) 이 방법이 가장 깔끔합니다. 지금은 그런 Tool이 없어서 새로 만들어야 합니다.
   ```yaml
   - id: step10
     type: TOOL
     tool: setValue          # 받은 value를 그대로 돌려주는 stepOnly Tool (신규)
     input:
       value: "성공"
     output:
       result: "state.status"
     next: step11
   ```

대부분은 1번이나 2번으로 충분합니다. 4번이 필요하시면 말씀해 주세요.

---

## 질문

내말은 step00 에서 output 의 result 에 "state.request" 가 아닌  "성공" 이라는 값을 주어서 무조건 "성공" 값을 줄 수도 있는건지를 묻는거야.

---

## 답변

아니요, 안 됩니다. `output`의 `result:` 오른쪽은 "저장할 값"이 아니라 "저장할 자리"만 적는 칸이라서, `result: "성공"`이라고 쓰면 엔진은 "성공이라는 자리에 결과를 저장하라"로 읽습니다. 자리는 반드시 `state.이름` 꼴이어야 하므로 기동 검사에서 실패해 서버가 뜨지 않습니다.

```yaml
output:
  result: "state.request"   # (O) readFile의 결과를 state.request 에 저장
  result: "성공"             # (X) "성공"은 state.이름 꼴이 아님 → 기동 실패
```

step의 결과값 자체는 YAML에서 정할 수 없습니다. step00의 결과는 언제나 `readFile` Tool이 돌려준 것(파일 내용)이고, `output`은 그것을 어디에 둘지만 정합니다. 결과를 "성공"이라는 글자로 바꿔치기하는 문법은 없습니다.

---

## 질문

여기서 state 라는 예약어에 대해서 조금 더 자세하게 설명해줘.

---

## 답변

`state`는 **Workflow 실행 1건마다 하나씩 생기는 공용 저장소**이고, step 사이에 값이 오가는 유일한 통로입니다.

## 실행 컨텍스트 안에서의 위치

실행 1건은 아래 네 갈래로 된 트리 하나를 가집니다. `state`는 그중 하나입니다.

| 이름 | 내용 | YAML에서 |
|---|---|---|
| `input` | 실행 요청으로 들어온 입력 그대로 | 읽기만 (`${input}`) |
| `state` | step들이 저장해 둔 값 | 읽기(`${state.x}`) + 쓰기(`output`) |
| `approvals` | 아직 쓰지 않은 승인 결정 | 엔진 내부용, 읽을 수 없음 |
| `definition` | 실행을 시작한 Workflow의 id·version | 엔진 내부용, 읽을 수 없음 |

## 동작 규칙

- **처음엔 비어 있습니다.** 실행이 시작될 때 `state`는 빈 맵이고, step이 `output`으로 넣은 것만 들어갑니다.
- **쓰기는 step의 `output`으로만 합니다.** `output: { result: "state.request" }`처럼 자리를 적으면 step이 끝날 때 엔진이 그 자리에 값을 넣습니다.
- **읽기는 `${state.…}` 식으로 합니다.** 하위 필드(`${state.review.reason}`), 목록 원소(`${state.list[0].x}`), 기본값(`${state.review.reason:}`)을 쓸 수 있습니다.
- **암묵적인 이름이 없습니다.** "직전 step 결과"나 `steps.<id>.output` 같은 것은 없어서, `output`으로 저장하지 않은 결과는 뒤에서 읽을 수 없습니다.
- **이름은 step id와 무관합니다.** `state.request`의 `request`는 YAML 작성자가 붙인 이름입니다.
- **여러 step이 같은 이름에 쓸 수 있습니다.** 나중에 쓴 값이 덮어씁니다. ROUTER의 각 갈래가 모두 `state.reply`에 쓰는 식으로 활용합니다.
- **성공·실패 모두 기록됩니다.** 루프로 같은 step을 다시 돌 때 이전 값이 남지 않게 하기 위해서입니다. 실패한 SUPERVISOR의 `{pass: false, reason}`도 이렇게 읽힙니다.
- **없는 경로는 `null`입니다.** 아직 실행되지 않은 step의 값을 읽으면 null이므로, 그럴 수 있는 곳은 `:` 기본값을 붙입니다.
- **값의 타입이 유지됩니다.** `"${state.impact}"`처럼 식 하나만 쓰면 객체·목록이 그대로 전달됩니다. `"경로: ${state.x}"`처럼 글자와 섞으면 문자열로 끼워 넣어집니다.

## 저장과 수명

- `state`는 실행 기록과 함께 DB(`AI_WORKFLOW_EXECUTION`의 `CONTEXT_JSON`)에 JSON으로 저장됩니다. APPROVAL에서 멈췄다가 며칠 뒤 이어서 실행해도 그대로 남아 있습니다.
- 실행 1건에만 속하므로 다른 실행과 섞이지 않습니다.
- Workflow가 `END`에 도달했을 때 `output.value`를 따로 적지 않으면 `state` 전체가 최종 결과로 나갑니다. `pilot-workflow`는 파일 내용처럼 큰 값(`state.sources` 등)을 빼려고 `output.value`를 적어 둡니다.

## 기동 시 검사

- `${state.x}`를 읽는 step이 있는데 `state.x`에 저장하는 step이 하나도 없으면 기동에 실패합니다.
- 저장하는 step이 읽는 step보다 먼저 실행될 수 없는 경우도 기동에 실패합니다. `next`/`onFailure`/`routes`를 따라 도달 가능한지를 보므로 루프로 되돌아오는 경우는 통과합니다.
- 그보다 깊은 필드 경로(`state.impact.summary`의 `summary`)는 Workflow의 `state.schema`나 저장하는 step의 결과 스키마와 대조해서 경고만 냅니다.

## `pilot-workflow` 앞부분 예

```
step00  readFile            → state.request   (00-request.md 내용)
step03  영향도 분석 Agent    ← ${state.request}, ${state.review.reason:}
                            → state.impact
```

step03의 `${state.review.reason:}`은 뒤쪽 리뷰 step이 저장하는 값입니다. 첫 실행 때는 아직 없어서 빈 값이 되고, 리뷰에서 반려되어 step03으로 되돌아왔을 때는 그 사유가 들어갑니다. `state`가 실행 내내 유지되기 때문에 이런 되먹임이 가능합니다.

---

## 질문

workflow 의 input 값에 들어갈 수 있는 것은 무엇들이 있는가? string, ... ?

---

## 답변

Workflow의 `input`에는 **JSON Schema로 표현할 수 있는 모양이면 무엇이든** 선언할 수 있습니다. `input`을 아예 적지 않으면 `string`으로 봅니다.

## 1. 타입 이름 하나만 적기 (줄임 표기)

| 적는 값 | 의미 | 요청 예 (`{"input": …}`) |
|---|---|---|
| `string` | 글자 | `"/app/work/req01"` |
| `number` | 숫자(소수 포함) | `3.14` |
| `integer` | 정수 | `10` |
| `boolean` | 참/거짓 | `true` |
| `object` | 객체(필드 제한 없음) | `{"a": 1}` |
| `array` | 목록(항목 제한 없음) | `[1, "a"]` |
| `list<타입>` | 그 타입의 목록 | `list<string>` → `["a", "b"]` |

```yaml
input: string
```
```yaml
input: list<string>
```

이 밖의 이름(`int`, `text`, `map` 등)을 쓰면 "알 수 없는 타입 이름입니다"로 기동에 실패합니다.

## 2. 필드가 있는 객체 등 자세한 모양

표준 JSON Schema를 그대로 적습니다. `properties`의 값과 `items` 자리에서는 줄임 표기를 쓸 수 있습니다.

```yaml
input:
  schema:
    type: object
    properties:
      workDir: string            # 줄임 표기
      mode: { type: string, enum: [strict, loose] }
      files: list<string>
    required: [workDir]
```

`enum`, `required`, `minimum`, `pattern`, 중첩 객체 같은 JSON Schema(2020-12) 키워드를 쓸 수 있습니다.

## 3. 스키마 파일로 빼기

```yaml
input:
  schema: schemas/my-workflow-input.schema.json
```

경로는 `schemas/`로 시작해야 하고, 로더가 파일 내용을 읽어 그 자리에 넣습니다.

## 선언이 쓰이는 곳

- **요청 검사**: `/execute`, `/submit`으로 들어온 `input`이 선언과 다르면 실행하지 않고 400을 돌려줍니다.
- **식 검사**: step에서 `${input.workDir}`처럼 읽는 경로가 선언에 없으면 기동 때 경고합니다.
- **목록 제공**: `GET /api/ai/workflow`가 이 스키마를 함께 돌려주므로 호출하는 쪽이 무엇을 보내야 하는지 알 수 있습니다.

`pilot-workflow`는 `input`이 `string`(작업 폴더 경로)이라서 step에서 `"${input}/00-request.md"`처럼 통째로 읽습니다. 객체로 선언했다면 `${input.workDir}`처럼 필드로 읽게 됩니다.

---

## 질문

workflow 의 settings 에 대해서 설명해줘

---

## 답변

`settings`는 Workflow 실행 전체에 적용되는 옵션 묶음입니다. 키는 세 개인데, 실제로 동작을 바꾸는 것은 `maxIterations` 하나이고 나머지 둘은 엔진이 항상 하는 동작을 명시하는 용도입니다.

`pilot-workflow.yml`에는 이렇게 적혀 있습니다.

```yaml
settings:
  checkpoint: true
  maxIterations: 100
  onError: STOP
```

## `maxIterations`: step 실행 횟수 상한

- **의미**: Workflow가 실행할 수 있는 step의 총 횟수입니다. "루프를 몇 바퀴 도는가"가 아니라 "step을 몇 번 실행했는가"를 셉니다.
- **기본값**: 적지 않으면 10입니다.
- **넘으면**: 실행이 `FAILED`로 끝나고 "최대 실행 횟수(settings.maxIterations = N)를 초과했습니다(루프 정지)" 메시지가 남습니다.
- **목적**: `onFailure`나 `routes`로 앞 step에 되돌아가는 구성이 끝없이 도는 것을 막습니다.
- **허용 값**: 1 이상이어야 하고, 0이나 음수는 기동에 실패합니다.

주의할 점이 세 가지 있습니다.
- **step이 많으면 꼭 늘려야 합니다.** 되돌아가지 않고 일직선으로만 가도 step 수만큼 셉니다. step이 10개를 넘는 Workflow를 기본값으로 두면 정상 실행도 실패합니다. `pilot-workflow`가 100으로 잡은 이유입니다.
- **승인 대기에서 이어서 실행하면 횟수가 0부터 다시 시작됩니다.** 카운터가 저장되지 않고 실행을 한 번 돌릴 때마다 새로 셉니다. 따라서 "실행 1건 전체의 상한"이 아니라 "멈추지 않고 한 번에 달리는 구간의 상한"입니다.
- **forEach step**의 반복은 step 1회로 세는 것으로 보입니다. 카운터가 step 단위 루프에 있기 때문인데, forEach 내부 코드까지 따라가 확인하지는 않았습니다.

## `checkpoint`: step마다 상태 저장

- `true`만 적을 수 있습니다. 엔진은 step이 끝날 때마다 실행 상태(`state` 포함)를 항상 DB에 저장하며, 끄는 기능이 없습니다.
- `false`를 적으면 기동에 실패합니다.
- 이 저장 덕분에 APPROVAL에서 멈췄다가 이어서 실행하거나, 서버가 재시작된 뒤 마지막 step부터 이어갈 수 있습니다.

## `onError`: 시스템 오류 시 동작

- `STOP`만 적을 수 있습니다. step 실행 중 시스템 오류(예외)가 나면 실행 전체가 `FAILED`로 끝납니다.
- 다른 값(`CONTINUE` 등)을 적으면 기동에 실패합니다.
- step 실패 시 다른 곳으로 보내려면 `settings`가 아니라 그 step의 `onFailure`를 씁니다. 이것은 Agent 계약 위반(출력 형식 틀림 등)이나 Tool의 `실패:` 응답 같은 step 실패에 적용됩니다.

## 그 밖의 키

- 세 키 외의 것(예: `timeout`, `retry`)을 적으면 YAML을 읽는 단계에서 기동에 실패합니다.
- `settings` 블록 전체를 생략해도 됩니다. 그러면 `maxIterations` 10에 나머지는 위와 같은 동작입니다.
- 샘플 Workflow들은 대부분 `maxIterations: 3`만 적어 둡니다.

---

## 질문

workflow 의 output 형식에 대해서 설명해줘

---

## 답변

Workflow의 `output`은 **실행이 `END`로 성공했을 때 호출자에게 돌려줄 최종 결과의 모양**을 정합니다. 지금까지 보신 step의 `output`과 이름은 같지만 뜻이 반대에 가깝습니다.

| | step의 `output` | Workflow의 `output` |
|---|---|---|
| 하는 일 | step 결과를 `state`에 **저장** | `state`에서 값을 **꺼내** 최종 결과로 조립 |
| 적는 것 | 저장할 자리 (`"state.request"`) | 값을 읽는 식 (`"${state.request}"`) |
| 키 | `result` / `input` / `error` / `items` | `value`, `schema` |

## 키는 두 개

```yaml
output:
  value: ...     # 무엇을 돌려줄지
  schema: ...    # (선택) 그 값이 지켜야 할 모양
```

### `value`: 돌려줄 값

step의 `input`과 같은 규칙으로 풀립니다. `${ }` 식은 값으로 바뀌고, 식이 없는 글자는 그대로 나갑니다.

- **값 하나**
  ```yaml
  output:
    value: "${state.answer}"
  ```
- **여러 값을 묶은 객체** (`pilot-workflow`의 방식)
  ```yaml
  output:
    value:
      route: "${state.classify.route}"
      requirements: "${state.requirements}"
      impact: "${state.impact}"
      newdevAnalysisCheck: "${state.impactCheck.text}"
  ```
- **값이 없는 경로는 `null`입니다.** `pilot-workflow`는 일반/신규개발 두 경로 중 하나만 타므로, 타지 않은 경로의 항목은 null로 나갑니다.
- **타입이 유지됩니다.** 식 하나만 쓴 자리는 객체·목록이 그대로 나가고, 글자와 섞으면 문자열이 됩니다.
- **읽을 수 있는 것은 `input`과 `state`뿐입니다.** 어떤 step도 저장하지 않는 `state` 경로를 읽으면 기동에 실패합니다.

### `schema`: 결과 모양 검사 (선택)

Workflow `input`과 같은 방식으로 적습니다(줄임 표기, JSON Schema, `schemas/….json` 파일 경로). 적어 두면 `END` 시점에 조립된 값을 검사하고, 모양이 다르면 실행이 `FAILED`로 끝납니다("Workflow output이 output.schema 모양이 아닙니다").

## `output`을 생략하면

`state` 전체가 최종 결과로 나갑니다. step이 적은 샘플에서는 편하지만, `state`에 큰 값이 있으면 그대로 다 나갑니다. `pilot-workflow`가 `value`를 직접 적은 이유가 이것입니다. 읽어 둔 파일 내용(`state.sources`, `state.designTargets`, `state.developInputs`)이 수십만 자라서 일부러 뺐습니다.

## 언제 만들어지고 어디로 가는가

- **만들어지는 시점**: 어떤 step의 `next`(또는 `routes`)가 `END`를 가리켜 실행이 성공으로 끝날 때 한 번입니다.
- **`FAIL`로 끝나면 만들어지지 않습니다.** 대신 실패 사유가 남습니다.
- **승인 대기 중에는 아직 없습니다.** `END`에 도달하지 않았기 때문입니다.
- **응답**: `/execute`의 응답과 `/status/{executionId}` 조회 결과의 `output` 필드로 나갑니다.
- **저장**: 실행 기록 테이블의 `RESULT_TEXT` 컬럼에 JSON 글자로 저장됩니다.

---

## 질문

그러면 output 은 value 혹은 schema 둘 중 하나를 선택해서 기술하면 되는가 ?

---

## 답변

아니요, 둘 중 하나를 고르는 관계가 아닙니다. `value`는 필수이고 `schema`는 `value`에 덧붙이는 선택 항목입니다.

| 적는 방식 | 결과 |
|---|---|
| `output` 자체를 생략 | `state` 전체가 최종 결과 |
| `value`만 | `value`를 계산한 값이 최종 결과, 모양 검사 없음 |
| `value` + `schema` | `value`를 계산한 뒤 `schema`로 검사, 다르면 `FAILED` |
| `schema`만 | **기동 실패** ("output을 적었으면 output.value가 있어야 합니다") |

두 키의 역할이 다릅니다.
- **`value`**는 무엇을 돌려줄지를 정합니다.
- **`schema`**는 그렇게 만든 값이 어떤 모양이어야 하는지를 검사합니다. 값을 만들어 주지는 않습니다.

```yaml
# 가장 흔한 형태: value만
output:
  value: "${state.answer}"
```

```yaml
# 호출하는 쪽과 결과 모양을 약속해야 할 때: 둘 다
output:
  value:
    sql: "${state.converted.sql}"
    approvedBy: "${state.review.approver}"
  schema:
    type: object
    properties:
      sql: string
      approvedBy: string
    required: [sql]
```

`schema`는 다른 시스템이 이 Workflow의 결과를 받아 쓸 때, 필드 누락 같은 문제를 Workflow 쪽에서 실패로 드러내고 싶을 때 씁니다. 그런 필요가 없으면 `value`만 적으면 됩니다. `pilot-workflow`도 `value`만 씁니다.

---

## 질문

input 도 schema 정의가 존재하는가 ?

---

## 답변

네, 있습니다. Workflow의 `input`은 **그 자체가 schema 선언**입니다. 값은 실행 요청(`{"input": …}`)으로 들어오므로 `output`과 달리 `value`가 없고 `schema`만 있습니다.

```yaml
input:
  schema:
    type: object
    properties:
      workDir: string
      mode: { type: string, enum: [strict, loose] }
    required: [workDir]
```

적는 방법은 세 가지이고 모두 같은 뜻으로 펼쳐집니다.

| 적는 방식 | 펼쳐진 모양 |
|---|---|
| `input: string` (줄임 표기) | `input: {schema: {type: string}}` |
| `input: {schema: {…JSON Schema…}}` | 그대로 |
| `input: {schema: schemas/xxx.schema.json}` | 파일 내용이 그 자리에 들어감 |
| `input` 생략 | `{type: string}` |

`input`, `state`, `output` 세 곳의 schema를 나란히 보면 이렇습니다.

| 블록 | 키 | schema의 역할 | 어겼을 때 |
|---|---|---|---|
| `input` | `schema` | 요청으로 받을 값의 모양 | 실행하지 않고 400 |
| `state` | `schema` (선택) | step들이 저장하는 값의 모양 | 기동 때 경로 대조 후 경고만 |
| `output` | `value` + `schema` (선택) | 최종 결과의 모양 | 실행이 `FAILED` |

같은 `input`이라는 이름이 세 군데에서 다른 뜻으로 쓰이니 구분해 두시면 좋습니다.
- **Workflow의 `input`**은 schema 선언입니다(무엇을 받을지).
- **Agent 정의(`agents/*.yml`)의 `input`**도 schema 선언입니다(`input: {schema: …}`).
- **step의 `input`**은 schema가 아니라 실제로 넘길 값입니다(`workDir: "${input}"`). 이 값이 부르는 Agent의 input schema와 맞는지는 기동 때 검사합니다.
