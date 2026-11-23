# localcommandcaveatTh

> 생성일시: 2026-11-23 14:36

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
