# localcommandcaveatTh

> 생성일시: 2026-11-23 15:55

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

sm-workflow 를 보면 05-고객요구사항분석서 읽기(step04)에서 01.customer_requirement.md 를 읽으려고 하는데 최초에는 당연히 없어서 실패가 나. 이건 정상적인 흐름인데 실패로 로그가 남는게 이상해. 좋은 방법이 없을까?

---

## 답변

진행 상황: 로그에서 step04가 실제로 실패한 기록을 찾고 있습니다.

---

## 답변

실제 실패 기록을 보니 step04가 아니라 step07(02.impact.md)과 step11(03-1.design.md)이었습니다. 엔진에서 분기하는 방법을 확인하겠습니다.

---

## 답변

## 결론부터

로그를 확인해 보니 처음 실행할 때 실패로 남는 건 step04가 아니었습니다. 실제로는 아래 두 step입니다. step04는 바로 앞 step03이 `01.customer_requirement.md`를 써 두기 때문에 매번 성공합니다.

```
14:04  step07 (08-기존 영향도분석서 확인)  실패: 파일을 읽을 수 없습니다 … 02.impact.md
14:29  step11 (기존 설계서 확인)           실패: 파일을 읽을 수 없습니다 … 03-1.design.md
```

이 두 step은 파일이 있는지 보려고 `readFileLines`로 첫 줄을 읽고, 읽지 못하면 `onFailure`로 분기합니다. `sm-workflow.yml` 머리 주석에도 "처음 실행 때 이 두 step이 실패로 남는 것은 정상"이라고 적혀 있습니다. 말씀하신 대로 이건 우회책에 가깝습니다.

## 원인

LLM 없이 분기하는 수단이 엔진에 하나뿐입니다. TOOL step이 성공하면 `next`, 실패하면 `onFailure`로 갑니다. 그래서 "없음"이라는 정상적인 결과도 실패로 표현할 수밖에 없고, step 이력의 `SUCCESS=false`와 실패 사유에 그대로 남습니다. step31(`countUp`, 자동 수정 횟수 초과 시 `onFailure: step19`)도 같은 모양입니다.

## 제안: TOOL step이 결과 값으로 분기하게 하기

1. **TOOL step에 `routes` 추가 (엔진)**: Tool이 돌려준 값이 `routes`의 키와 같으면 그쪽으로 갑니다. ROUTER나 APPROVAL의 `routes`와 같은 모양이고, 값을 그대로 맞춰 보기만 하므로 "YAML에 로직을 넣지 않는다" 규칙에도 어긋나지 않습니다. `onFailure`는 경로가 잘못됐거나 권한이 없는 경우처럼 진짜 실패에만 씁니다.
2. **범용 Tool `fileExists` 추가 (`stepOnly`)**: 경로를 받아 `EXISTS` 또는 `MISSING`을 돌려줍니다. 특정 Workflow를 모르는 범용 Tool입니다.

이렇게 바꾸면 정의는 다음과 같습니다.

```yaml
  - id: step07
    name: 08-기존 영향도분석서 확인
    type: TOOL
    tool: fileExists
    with:
      fileFullPath: ${input}/${state.now}/02.impact.md
    routes:
      EXISTS: step38
      MISSING: step08
    onFailure: FAIL
```

- 처음 실행할 때도 이력에는 성공과 `MISSING`만 남고, 실패 목록에서 빠집니다.
- 부팅 검증도 함께 넣습니다. `routes`의 대상 step이 있는지, `next`와 `routes`를 같이 쓰지 않았는지 확인합니다.
- 원하시면 `countUp`도 같은 방식으로 `UNDER`/`OVER`를 돌려주게 해서 step31의 "가짜 실패"도 없앨 수 있습니다.

## 고려했지만 권하지 않는 방법

- **`onFailure`로 간 실패를 "예상된 실패"로 표시하는 플래그**: 로그 등급만 낮출 뿐, 이력에는 여전히 실패로 남습니다. 진짜 실패와도 구분되지 않습니다.
- **`readFileLines`가 파일이 없을 때 빈 값으로 성공하기**: 파일이 꼭 있어야 하는 step04 같은 곳에서는 실패를 놓치게 됩니다.

진행할까요? 진행하면 엔진(`ToolStepDefinition`, `WorkFlowExecutor`, `WorkFlowRegistry`), `fileExists` Tool, `sm-workflow` step07·step11(원하시면 step31까지), `docs/09`와 `.claude/docs`의 관련 문서를 함께 고치겠습니다.

---

## 질문

step을 추가 삽입하자는 말인가?

---

## 답변

아닙니다. step은 새로 넣지 않습니다. 이미 있는 step07과 step11을 그 자리에서 고치는 것입니다.

**지금 (step07)**
```yaml
  - id: step07
    name: 08-기존 영향도분석서 확인
    type: TOOL
    tool: readFileLines          # 첫 줄을 읽어 보고
    with:
      fileFullPath: ${input}/${state.now}/02.impact.md
      startLine: 1
      endLine: 1
    next: step38                 # 읽으면 → 수정
    onFailure: step08            # 못 읽으면(=실패) → 새로 작성
```

**바꾼 뒤 (같은 step07)**
```yaml
  - id: step07
    name: 08-기존 영향도분석서 확인
    type: TOOL
    tool: fileExists             # 있는지만 확인 (Tool만 바뀜)
    with:
      fileFullPath: ${input}/${state.now}/02.impact.md
    routes:
      EXISTS: step38             # 있으면 → 수정
      MISSING: step08            # 없으면 → 새로 작성 (실패 아님)
    onFailure: FAIL              # 진짜 오류일 때만
```

step id, step 수, 흐름은 그대로이고 달라지는 것은 두 가지뿐입니다.
- 쓰는 Tool이 `readFileLines`에서 `fileExists`로 바뀝니다.
- 분기를 `next`/`onFailure`가 아니라 `routes`로 정합니다.

새로 만드는 것은 엔진 쪽입니다. TOOL step이 `routes`를 읽도록 하는 기능과 범용 Tool `fileExists`를 추가합니다. step11도 같은 방식으로 고칩니다.

---

## 질문

만일 이 용도만이라면 왜 애시당초 fileExists 를 사용하지 않았을까?

---

## 답변

`fileExists`를 썼어도 분기할 수 없었기 때문입니다.

지금 엔진에서 TOOL step이 다음 step을 고르는 기준은 성공했는지 실패했는지 하나뿐입니다. Tool이 무엇을 돌려줬는지는 분기에 쓰지 않습니다.

- `fileExists`가 `true`나 `false`를 돌려주는 Tool이라면, 파일이 없어도 Tool 호출 자체는 성공합니다. 그래서 늘 `next`로만 갑니다.
- 분기하려면 파일이 없을 때 Tool이 **실패**해야 합니다.
- 파일이 없을 때 실패하는 Tool은 이미 있었습니다. 바로 `readFileLines`입니다.

규칙상 순서는 "정의 파일 수정 → 기존 범용 Tool → 새 범용 Tool"이었습니다. 그래서 sm-workflow 2.3.0(커밋 306c2b50)에서 step07과 step11을 만들 때 새 Tool을 만들지 않고 기존 `readFileLines`로 첫 줄을 읽게 했습니다. 이력에 실패로 남는 대가는 머리 주석에 "정상"이라고 적어 두었습니다.

결국 문제는 Tool이 없어서가 아니라 엔진에 "값으로 분기"하는 기능이 없어서 생겼습니다. 그래서 제안의 핵심은 `fileExists`가 아니라 TOOL step의 `routes`입니다. `fileExists`는 `routes`가 생겨야 쓸모가 생기는 짝입니다.

---

## 질문

이제안을 진행하는 경우 엔진소스를 수정해야 하는건 아니지?

---

## 답변

아니요, 엔진 소스를 고쳐야 합니다. 이 제안은 엔진이 Tool이 돌려준 값으로 분기하게 만드는 것이 핵심이고, 지금 엔진에는 그런 기능이 없습니다.

고칠 곳은 대략 다음과 같습니다.

| 구분 | 고칠 것 |
|---|---|
| 정의 모델 | `ToolStepDefinition`에 `routes` 키 추가 |
| 실행 | `ToolStepExecutor`/`WorkFlowExecutor`: 결과 값과 같은 `routes` 키로 이동, 맞는 키가 없으면 실패 |
| 부팅 검증 | `WorkFlowRegistry`: `routes` 대상 step이 있는지, `next`와 함께 쓰지 않았는지, 도달 가능성 계산에 `routes` 포함 |
| Tool | 범용 `fileExists` 새로 추가 |
| 화면 | 디자이너가 TOOL step의 `routes` 화살표를 그리는지 확인하고, 그리지 못하면 고침 |
| 정의·문서 | `sm-workflow` step07·step11, `docs/09`, `.claude/docs` |

엔진을 고치지 않고 할 수 있는 방법도 따져 봤지만 마땅한 것이 없습니다.
- **ROUTER step으로 LLM에게 파일이 있는지 고르게 하기**: 정의만 바꾸면 되지만, 파일이 있는지 보는 데 LLM을 부르는 셈이라 느리고 결과도 매번 같다는 보장이 없습니다. 권하지 않습니다.
- **실패하는 Tool만 `fileExists`로 바꾸기**: 이력에는 여전히 실패로 남아 지금과 다르지 않습니다.

그래서 남는 선택은 두 가지입니다.
1. 엔진을 고쳐서 제대로 없앤다. 작업 범위는 위 표 정도입니다.
2. 엔진은 그대로 두고, 지금처럼 머리 주석으로 "정상 실패"라고 안내한다.

어느 쪽으로 할까요?

---

## 질문

1. 엔진소르를 수정하는 방법으로 가자. 그리고 workflow 디자인파일도 수정해줘.

---

## 답변

엔진 소스부터 확인하겠습니다.

---

## 답변

엔진 구조를 파악했습니다. 이제 Tool과 디자이너 화면 코드를 확인하겠습니다.

---

## 답변

엔진에서 고칠 곳을 모두 찾았습니다. 정의 record, 실행기, 부팅 검증, 승인 수신자 계산, 이렇게 네 곳입니다. 이제 수정을 시작합니다.

---

## 답변

Now the ToolStepExecutor: when routes are present, use the result value as the route.

---

## 답변

Now the registry checks, the executor Javadoc, and the approval receiver calculation.

---

## 답변

이제 범용 Tool `fileExists`를 추가합니다.

---

## 답변

Now the workflow definition: step07, step11, and the header comment.

---

## 답변

다음은 디자이너 화면입니다. TOOL step의 `routes`를 그리고 편집할 수 있는지 확인합니다.

---

## 답변

디자이너는 `routes` 화살표를 이미 종류와 상관없이 그리고 있습니다. 그래서 TOOL step에서 `routes`를 만들고 편집할 수 있게만 고치면 됩니다.

---

## 답변

이제 빌드하고, 관련 테스트가 무엇을 기대하는지 확인합니다.

---

## 답변

Existing tests cover step07/step11 (both "file exists" and "missing" paths). Running the relevant tests.

---

## 답변

The relevant tests pass (skipped ones are real-LLM tests). Let me add a test that pins the new behavior: when the file is missing, the step is recorded as a **success**.

---

## 답변

Adding a `history()` accessor to the stepper and asserting success in the two "missing" tests.

---

## 답변

Adding a check test for the "next + routes together" rule, using sm-workflow's step07 text.

---

## 답변

모두 통과했습니다. 다른 곳이 깨지지 않았는지 전체 테스트도 한 번 돌리겠습니다.

---

## 답변

전체 247개가 통과했습니다(실패 0). 이제 문서를 고칩니다.

---

## 답변

잠깐, 문서를 보니 `FileTool`에 이미 `isFileExist`가 있습니다. 먼저 확인하겠습니다.

---

## 답변

테스트는 모두 통과합니다. 이제 `docs/09`에서 step 키 표와 관련 설명을 고칩니다.

---

## 답변

엔진을 고쳤습니다. 이제 step07과 step11은 처음 실행해서 문서가 없어도 실패로 남지 않고 성공으로 기록됩니다. 저장된 결과는 `false`입니다. 전체 테스트 247개는 실패 없이 통과했습니다(건너뛴 31개는 실제 LLM이 필요한 테스트입니다). 실행 중인 8081 서버와 디자이너 화면은 아직 그대로라서, 빌드하고 다시 띄워야 바뀝니다.

**계획과 달라진 점:** `fileExists`를 새로 만들지 않았습니다. 작업하다 보니 `FileTool`에 `true`/`false`를 돌려주는 `isFileExist`가 이미 있었습니다. 기존 범용 Tool을 먼저 쓰는 규칙에 따라 그것을 썼습니다. 그래서 `routes`의 이름도 `EXISTS`/`MISSING`이 아니라 `"true"`/`"false"`입니다.

**바뀐 정의 (sm-workflow 2.4.1)**
```yaml
  - id: step07
    name: 08-기존 영향도분석서 확인
    type: TOOL
    tool: isFileExist
    with:
      filePath: ${input}/${state.now}/02.impact.md
    routes:
      "true": step38
      "false": step08
    onFailure: FAIL
```
step11(`03-1.design.md` → step37/step12)도 같은 모양입니다. 머리 주석의 "실패로 남는 것은 정상" 문장은 새 설명으로 바꿨습니다.

**엔진**
- **정의와 실행:** TOOL step에 `routes`를 적을 수 있습니다. Tool이 돌려준 값(글자, 숫자, true/false)으로 갈 곳을 고릅니다. 값이 `routes`에 없으면 실패로 보고 `onFailure`로 갑니다.
- **부팅 검증:** `routes`에 경로가 하나 이상 있어야 합니다. `next`나 `forEach`와 함께 쓰면 부팅이 막힙니다.
- **승인 화면:** "이 결정을 고르면 누가 받는가"를 계산할 때 TOOL step의 `routes`도 따라갑니다.
- **`isFileExist` 설명:** "저장 결과 안내 문구를 돌려준다"는 엉뚱한 문장이 있어 바로잡았습니다.

**디자이너 화면**
- TOOL step의 화살표 종류에 '경로(routes)'를 추가했습니다.
- `next`와 `routes`를 함께 만들려고 하면 막습니다.
- 마지막 경로를 지우면 `routes` 키도 함께 지웁니다.

**테스트**
- 파일이 없을 때 step07과 step11이 성공으로 기록되고 결과가 `false`인지 확인하는 단언을 추가했습니다.
- `next`와 `routes`를 함께 적으면 검사에서 막히는지 확인하는 테스트를 추가했습니다.

**문서:** `docs/09`(step 키 표와 예시), `.claude/docs/ai-engine-packages.md`, `ai-engine-history.md`를 고쳤습니다.

`countUp`(step31, 자동 수정 횟수 초과)과 `containsValue`도 정상 결과를 실패로 나누는 같은 모양이지만, 이번에는 그대로 두었습니다. 원하시면 같은 방식으로 바꾸겠습니다. 커밋은 아직 하지 않았습니다.

---

## 질문

커밋 푸시해줘.

---

## 답변

커밋하고 `main`에 푸시했습니다. 커밋은 `757c0535`이고 파일 15개가 들어갔으며, 로컬과 `origin/main`이 같은 상태입니다.

실행 중인 8081 서버에는 아직 바뀐 내용이 반영되지 않았습니다. 엔진 코드와 디자이너 화면이 함께 바뀌었으므로, 빌드한 뒤 다시 띄워야 적용됩니다. 실행 중인 `target/*.jar`는 그 자리에서 다시 빌드하지 말고, 서버를 내린 뒤 빌드하세요.

---

## 질문

지금 실행 아이디 6d1784d8-bf59-465c-a6d1-56c9cb4965d0 가 sendboxserver에서 테스트 중이야. 잘 진행되고 있는건지 모니터링해줘.

---

## 답변

진행 상황: 검증 Agent가 실제로 등록까지 해 봤는지 서버 로그에서 확인하고 있습니다.

---

## 답변

실행 `6d1784d8`은 정상적으로 진행 중입니다. 지금은 step35(36-동작 검증, `sm-verifier-agent`)를 시작한 지 약 5분 됐고, Windows 쪽 인스턴스에서 돌고 있습니다. heartbeat도 계속 갱신되고 있습니다.

**지금까지 흐름 (전부 성공)**
- step13 설계 승인에서 "설계 수정"을 골랐고, step25가 설계서를 고친 뒤 다시 "승인(개발진행)"을 받았습니다.
- step17 개발: 6건 중 5건을 끝냈고 1건은 보류입니다. 보류는 메뉴 데이터 INSERT로, DDL 파일에 넣어 두었습니다.
- step18 컴파일 확인은 통과했습니다.
- step29 시험 서버는 일회용 DB에 띄웠고, 88초 걸렸습니다.

**step35 진행 내용 (Tool 호출 53번, 상한 120번)**
1. 로그인은 `testapp`과 `PRD`로 실패한 뒤 `admin`으로 성공했습니다. 시험계정을 찾느라 호출 10번 남짓을 썼습니다.
2. 이미지 게시판 메뉴로 들어가 목록을 열었습니다.
3. 등록에서 제목을 넣고 이미지를 올렸습니다. 파일은 서버에 저장되고 파일 테이블 INSERT도 됐습니다.
4. 그런데 저장 버튼으로 보낸 `insertImageBoard.ajax` 요청 뒤에 게시판 INSERT SQL이 로그에 없습니다. 개발 결과에 문제가 있을 가능성이 있습니다.
5. Agent도 이 부분을 보고 있습니다. 요청 본문과 헤더를 열어 보고, 샘플 게시판의 저장 방식과 비교하는 중입니다.

**흐름상 지켜볼 점**
- 등록이 정말 안 되는 것이라면 검증 결과에 FAIL이 나옵니다. 그러면 자동 수정으로 넘어가고, 사람의 결정 하나당 최대 2번까지 고친 뒤 다시 검증합니다. 이것은 설계대로의 흐름입니다.
- 이 실행은 지난 커밋 전 엔진에서 돌고 있습니다. 그래서 step11이 이력에 실패로 남아 있는데, 예전 방식 그대로라 이번 실행에서는 정상입니다.
- 로그인 계정을 찾는 데 호출을 쓰고 있습니다. 프로젝트 관리에 시험계정이 등록돼 있는지 한 번 확인해 보시면 좋겠습니다.

백그라운드 감시를 걸어 두었습니다. 20초마다 확인하고, step이나 상태가 바뀌거나 heartbeat가 2분 넘게 멈추면 바로 알려 드리겠습니다.

---

## 질문

프로젝트관리에 계정은 등록되어 있어. 왜 못찾는지 그게 더 의문이야.

---

## 답변

진행 상황: step35에서 Agent가 받은 시험 서버 결과에 시험계정이 어떻게 적혀 있었는지 로그에서 찾고 있습니다.

---

## 답변

검증이 끝나 FAIL이 나왔고, 자동 수정 1회차(step34)로 넘어갔습니다. 검증 결과를 보겠습니다.

---

## 답변

Agent가 계정을 못 찾은 게 아니었습니다. 등록된 ID **`testapp`이 그 앱의 로그인 테이블에 없어서** 로그인이 실패한 것입니다.

**로그에서 확인한 순서**
1. Agent가 받은 시험 서버 결과에는 `시험계정: ID testapp, 비밀번호 칸에는 ${secret:testApp.testPassword}`가 들어 있었습니다.
2. Agent는 처음부터 그대로 넣었습니다. ID `testapp`에 비밀번호는 자리표시자 그대로였습니다. 그런데 서버가 `BadCredentialsException: 사용자아이디가 없거나 비밀번호가 맞지 않습니다`로 거절했습니다.
3. 그래서 Agent가 복제본 DB를 뒤졌습니다.
   - `tbsy_member`: `PRD` 한 건만 있었습니다. 이것은 회사 코드입니다.
   - `tbsy_login`(실제 로그인 ID): **`admin`, `admin2` 두 건뿐이고 `testapp`은 없습니다.**
4. `PRD`로 시도해 실패했고, `admin`에 같은 비밀번호 자리표시자를 넣자 로그인됐습니다.

프로젝트 관리 DB에도 `testApp` 프로젝트의 시험계정 ID가 `testapp`으로 저장돼 있습니다(2026-11-22 17:33 수정). 프로젝트 ID와 같은 이름이 ID 칸에 들어간 것으로 보입니다. 비밀번호는 `admin`의 비밀번호와 맞습니다.

**조치:** 프로젝트 관리에서 testApp의 시험계정 ID를 `admin`으로 고치시면 됩니다. 비밀번호 칸은 비워 두면 저장된 값을 그대로 씁니다.

**따로 생각해 볼 점:** Agent가 등록된 비밀번호를 다른 ID(`PRD`, `admin`)에 넣어 보며 맞춰 봤습니다. 이번에는 일회용 복제본이라 문제가 없었습니다. 하지만 자리표시자가 등록된 ID와 짝일 때만 바뀌도록 엔진이 묶는 편이 안전합니다. 원하시면 따로 손보겠습니다.

**진행 상황:** 동작 검증(step35, 8분)이 실제 결함 3건을 찾아 FAIL로 냈습니다.
- 저장 시 HTTP 415가 납니다. 화면은 multipart로 보내는데 컨트롤러는 `@RequestBody` JSON을 받습니다.
- 목록에 체크박스 열이 없습니다.
- 등록자에 로그인 사용자명이 자동으로 들어가지 않습니다.

그 결과 자동 수정 1회차(step34, 최대 2회)로 들어갔습니다. 설계대로의 흐름입니다. 감시는 다시 걸어 두었고, 다음 step으로 넘어가거나 멈추면 알려 드리겠습니다.

---

## 질문

<task-notification>
<task-id>bl3q38d1h</task-id>
<tool-use-id>toolu_01U7o9wuuhRkewjWSG86zwoz</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/8b03a4a7-ae90-47d4-81d9-36c4f02d8713/tasks/bl3q38d1h.output</output-file>
<status>completed</status>
<summary>Background command "/tmp/claude-1000/-app-dstone/a7e894e6-0cc5-4526-b837-6955c74cf684/scratchpad/watch.sh" completed (exit code 0)</summary>
</task-notification>

---

## 답변

자동 수정 1회차(step34)가 88초 만에 끝났고, 고친 코드로 다시 확인하는 단계에 들어갔습니다.

**찾은 원인과 고친 내용**

| 결함 | 원인 | 수정 |
|---|---|---|
| TASK-01 체크박스 열 없음 | `selectable: multiple`은 행 선택만 켜고 체크박스 열은 만들지 않음 | `imageBoard.jsp` 그리드에 체크박스 열을 추가하고, 전체선택과 삭제가 그 체크박스를 보게 바꿈 |
| TASK-07 등록자 빈 값 | 등록 팝업을 열 때 로그인 사용자 이름을 채우지 않음 | `ImageBoardController.imageBoardRegiPop()`에서 비어 있으면 로그인 사용자 이름을 넣음 |
| TASK-09 저장 시 415 | 화면은 multipart로 보내는데 컨트롤러는 JSON을 기대함 | `imageBoardRegi.jsp`의 저장을 form submit 대신 다른 방식으로 바꿈(수정 내용 요약이 중간에 잘려 자세한 방식은 아직 못 봤습니다) |

이어서 컴파일 확인(step18, 통과)과 step28을 지났고, 지금은 step29에서 시험 서버를 새로 띄우고 있습니다. 서버가 뜨면 step35에서 다시 검증합니다.

시험계정 ID가 아직 `testapp`이라면 이번 검증에서도 로그인부터 같은 시행착오를 거칩니다. 지난번에는 이 과정에 Tool 호출 10번 남짓을 썼습니다. 지금 프로젝트 관리에서 `admin`으로 고치시면, step29가 프로젝트 관리를 다시 읽는 구조라 이번 검증부터 바로 적용될 가능성이 큽니다.

감시는 계속 걸어 두었습니다.

---

## 질문

<task-notification>
<task-id>by0h8h1z5</task-id>
<tool-use-id>toolu_016SPZguDUW6F55szCLj1d7G</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/8b03a4a7-ae90-47d4-81d9-36c4f02d8713/tasks/by0h8h1z5.output</output-file>
<status>completed</status>
<summary>Background command "/tmp/claude-1000/-app-dstone/a7e894e6-0cc5-4526-b837-6955c74cf684/scratchpad/watch.sh" completed (exit code 0)</summary>
</task-notification>

---

## 답변

시험 서버를 새로 띄웠고(step29, 99초, `http://localhost:54518/`), 지금은 2차 동작 검증(step35)을 하고 있습니다. 1차 수정에서 고친 파일 3개가 서버에 반영됐습니다.

프로젝트 관리의 시험계정 ID는 아직 `testapp`입니다(마지막 수정 2026-11-22). 그래서 이번 검증도 로그인할 때 `testapp`으로 실패한 뒤 `admin`을 찾아가는 과정을 다시 거칠 가능성이 큽니다. 1차 때는 여기에 Tool 호출 10번 남짓을 썼습니다. 계정을 지금 고치셔도 이번 검증에는 반영되지 않습니다. 시험 서버를 띄울 때 받은 안내를 그대로 쓰기 때문입니다.

검증이 끝나면 결과(PASS, FAIL, BLOCKED)를 정리해서 알려 드리겠습니다.

---

## 질문

<pasted_content id="39f2">
dstone-ai-engine 에서 아래의 내용 확인해줘.

<확인 요청>
sendboxserver 는 어떤 단위로 격리되는가? 
[30-격리 기동 확인(step29)]은 모든 개발프로세스가 거치는 스탭인가? 너무 오래 걸려.
</pasted_content id="39f2">

---

## 답변

실행이 개발 승인(step19)에 도착해 사람의 결정을 기다리고 있습니다. 2차 검증 결과와, 시험 서버를 step이 끝날 때 내리는지 함께 확인하겠습니다.

---

## 답변

## 1. 격리는 어떤 단위로 되나

**`startSandboxServer`를 한 번 부를 때마다** 격리 환경이 하나 생깁니다. 띄울 때 새로 만들고, 부른 step이 끝나면 내리고 지웁니다.

| 대상 | 단위 | 근거 |
|---|---|---|
| DB | 부를 때마다 일회용 복제본을 새로 만듦 | 이번 실행에서도 별칭이 `sandbox-bef2fa85`, `1d4cd3ed`, `e1a9f525`…로 매번 달랐습니다 |
| 앱 사본 | 프로젝트마다 하나(`{작업 루트}/{프로젝트ID}-server/app`) | 지우지 않고 다시 씁니다. 바뀐 파일만 맞춥니다("원본에서 바뀐 파일 1개 복사, 개발 결과 3개 덮어씀") |
| 동시 실행 | 프로젝트마다 한 실행만 | `server.lock` 파일 잠금 |
| 수명 | 띄운 **step**이 끝날 때까지 | `TestServerTool.stepEnded()`가 그 step이 띄운 서버를 내립니다 |
| Agent 묶기 | 띄운 step 안에서만 | DB 조회는 복제본으로, 브라우저는 시험 서버 주소로만 갑니다 |

## 2. step29는 모든 개발 과정이 거치나

**네. 프로젝트ID가 있는 요청이면 컴파일을 통과할 때마다 거칩니다.**

```
개발(17) · 재개발 · 전문가 해결(24) · 자동 수정(34)
   → 컴파일 확인(18) 통과 → 프로젝트ID 확인(28) → step29 → 동작 검증(35)
```
요청서에 `## 프로젝트ID`가 없을 때만 step28이 실패해 개발 승인으로 바로 갑니다.

## 오래 걸리는 진짜 이유: 한 회차에 서버를 두 번 띄움

이번 실행의 로그 시각입니다.

| 회차 | step29 (TOOL) | step35 안에서 Agent가 다시 부름 |
|---|---|---|
| 1회차 | 15:00:10 ~ 15:01:38 (**88초**) | 15:01:40 ~ 15:03:25 (**105초**) |
| 2회차 | 15:11:20 ~ 15:12:59 (**99초**) | 15:13:05 ~ 15:14:52 (**107초**) |

step29가 서버를 띄워도 step29가 끝나는 순간 그 서버는 내려갑니다(step 단위 수명). 그런데 `sm-verifier-agent`는 `requiredTools`에 `startSandboxServer`가 있어서 처음부터 다시 띄웁니다. **검증 한 회차마다 3분 남짓이 서버 기동에 쓰이고, 그 절반은 똑같은 일을 반복하는 셈입니다.**

step29가 따로 있는 이유는, 서버가 안 뜰 때 그 원인이 개발 결과 탓인지 환경 탓인지를 LLM을 부르기 전에 가르기 위해서입니다(step30). 그러니 step29를 없애기보다 중복을 없애는 쪽이 맞습니다.

**제안:** step29가 띄운 서버를 step35가 그대로 이어받게 합니다. 엔진에서 "같은 실행에서 같은 인자로 띄웠고 그 뒤에 사본이 바뀌지 않았으면, 이미 떠 있는 서버를 돌려준다"는 재사용 규칙을 넣고, step29가 끝나도 바로 내리지 않게 합니다. 특정 Workflow를 모르는 범용 규칙이고, Agent 프롬프트와 `requiredTools`는 그대로 둡니다. 검증 한 회차에 100초 안팎이 줄어듭니다. 88~107초가 빌드, DB 복제, Tomcat 기동에 어떻게 나뉘는지는 아직 재 보지 않았습니다. 필요하면 로그로 나눠 재겠습니다.

## 실행 6d1784d8 결과

2차 검증 9건 중 **PASS 7건, BLOCKED 2건, FAIL 0건**입니다. 1차의 저장 415 오류, 체크박스 열 누락, 등록자 빈 값이 모두 풀렸습니다. 실행은 **개발 승인(step19)에서 결정을 기다리고 있습니다.**
- **BLOCKED 2건(TASK-06 썸네일 팝업, TASK-08 파일 선택):** iframe 안의 kendoUpload에는 Playwright의 파일 올리기가 먹히지 않았습니다. 코드 결함이 아니라 시험 도구의 한계로 보입니다. 이 두 건은 사람이 직접 확인해야 합니다.
- step36(`containsValue`, "FAIL이 없음")은 이력에 **실패**로 남았습니다. 아까 고친 것과 같은 "정상 결과를 실패로 분기"하는 모양입니다. 이것도 `routes`로 바꿀 수 있습니다.

서버 재사용을 진행할지 정해 주시면 작업하겠습니다. step36(`containsValue`)과 step31(`countUp`)도 같이 바꿀지 알려 주세요.

---

## 질문

응 서버 재사용 진행해. 어쨓든 작업이 끝나면 (성공이건 실패건)서버는 down되어야 해.

---

## 답변

서버 재사용을 진행하겠습니다. 먼저 `TestServerTool`과 `SandboxRegistry`가 서버와 step을 어떻게 묶는지 읽어 보겠습니다.

---

## 답변

Now the `TestServerTool` changes: fingerprint, carry-over, reuse, and `executionLeft`.

---

## 답변

Name clash with an existing helper; renaming mine to `sameFormPath`.

---

## 답변

컴파일됩니다. 이제 테스트를 붙입니다. 기존 `TestServerToolTest`가 어떤 방식인지 먼저 보겠습니다.

---

## 답변

라이프사이클을 단위 테스트로 확인할 수 있게, 같은 패키지에서만 쓰는 작은 진입점 두 개를 둡니다. 서버를 맡기는 것과 떠 있는지 보는 것입니다.

---

## 답변

새 테스트 4건을 포함해 모두 통과했습니다. 전체 테스트도 돌리겠습니다.

---

## 답변

전체 251개가 통과했습니다(실패 0). 문서를 고칩니다.

---

## 답변

서버 재사용을 넣었습니다. 바로 다음 step이 같은 입력으로 다시 부르면 이미 떠 있는 서버를 그대로 씁니다. 그리고 실행이 어떤 이유로든 멈추거나 끝나면 그 실행의 서버를 모두 내립니다. 전체 테스트 251개는 실패 없이 통과했습니다. 다만 실제 서버로 띄워서 줄어든 시간을 재 보지는 않았습니다.

**서버를 언제 내리나**

| 상황 | 서버 |
|---|---|
| 띄운 step이 끝남 | 바로 내리지 않고 **다음 step 하나까지만** 둠 |
| 다음 step이 같은 입력으로 다시 부름 | 새로 띄우지 않고 그대로 씀. 그 step이 끝나면 내림 |
| 다음 step이 다시 쓰지 않음 | 그 step이 끝나면 내림 |
| 실행이 엔진 루프를 벗어남(끝남, 실패, 승인 대기, 일시정지, 예외) | 그 실행의 서버를 **무조건 모두 내림** |
| 엔진 종료 | 모두 내림(전과 같음) |

**다시 쓰는 조건은 넷을 모두 만족할 때만입니다.** 아니면 전처럼 내리고 새로 띄웁니다.
- 같은 실행이면서, 서버를 띄운 step과 다른 step에서 부를 것. 같은 step에서 다시 부르면 고친 뒤 확인하는 것이라 새로 띄웁니다.
- 입력 경로(프로젝트, 원본, 개발 결과, DDL, 삭제 목록)가 같을 것. 역슬래시나 끝 슬래시 같은 표기 차이는 같은 것으로 봅니다.
- 개발 결과 폴더, DDL, 삭제 목록 파일의 크기와 수정 시각이 그대로일 것.
- 서버 프로세스가 살아 있을 것.

서버를 다시 쓸 때는 결과 앞에 그 사실을 적어 줍니다. 앞 step이 바꾼 데이터가 남아 있을 수 있다는 안내도 함께 붙입니다.

sm-workflow에서는 step29가 띄운 서버를 step35의 검증 Agent가 그대로 받게 됩니다. 그래서 검증 한 회차마다 기동 한 번, 약 100초가 줄어들 것으로 봅니다. 이번 실행 기준으로 1회차 105초, 2회차 107초가 빠지는 셈입니다.

**고친 파일**
- `TestServerTool`: 서버를 다음 step으로 넘기고, 다시 쓸지 판단하고, 실행이 멈추면 내리는 처리
- `StepEndListener`: 실행이 루프를 벗어날 때 부르는 `executionLeft()`를 추가했습니다. 기본값은 아무것도 하지 않습니다.
- `WorkFlowExecutor`: 실행 루프의 마지막 정리(`finally`)에서 `executionLeft()`를 부릅니다.
- `EvalRunner`: Agent 평가는 step 하나로 끝나므로, 평가가 끝나면 서버를 바로 모두 내립니다.
- 새 테스트 `TestServerCarryTest`(4건): 서버를 넘기고 내리는 순서, 다른 실행은 건드리지 않는지, 실행이 끝나면 모두 내리는지, 입력이나 파일이 바뀌면 다른 것으로 보는지를 확인합니다.
- 문서: `docs/09`(11.2절 "격리 단위와 서버의 수명" 표 추가), `.claude/docs/ai-engine-features.md`, `ai-engine-history.md`

8081 서버에 반영하려면 빌드하고 다시 띄워야 합니다. 커밋은 아직 하지 않았으니, 필요하시면 말씀해 주세요. 다음 실행에서 step29와 step35 사이에 서버를 다시 띄우지 않는지는 실행 로그에서 확인할 수 있습니다.

---

## 질문

커밋 푸시해줘.

---

## 답변

커밋할 변경이 없다고 나옵니다. 그 사이에 다른 커밋(`7d20610d .`)이 제 변경을 이미 담아 갔는지 확인하겠습니다.

---

## 답변

새 커밋은 만들지 않았습니다. 이번 변경 8개 파일은 이미 커밋 `7d20610d`(15:54, 메시지 ".")에 모두 들어가 `origin/main`에 푸시되어 있습니다. 제가 커밋하려던 시점에는 작업 폴더가 이미 깨끗했습니다.

이 커밋은 제가 만든 것이 아닙니다. 사용자 계정으로 기록되어 있어서, 직접 하셨거나 그 사이에 자동 커밋이 돈 것으로 보입니다. 그래서 커밋 메시지가 "."뿐이고, 제가 쓰려던 설명과 Co-Authored-By 줄은 없습니다. 메시지를 바꾸려면 이미 푸시한 커밋을 고쳐 강제 푸시해야 하므로, 따로 요청하지 않으시면 그대로 두겠습니다.
