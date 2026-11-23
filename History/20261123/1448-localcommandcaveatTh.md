# localcommandcaveatTh

> 생성일시: 2026-11-23 14:48

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
