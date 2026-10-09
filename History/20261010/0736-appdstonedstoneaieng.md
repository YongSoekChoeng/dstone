# appdstonedstoneaieng

> 생성일시: 2026-10-10 07:36

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
