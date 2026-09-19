# `WorkFlowExecutor.run(WorkFlowDefinition, WorkFlowExecution)` 완전 정복

> 대상 클래스: `net.dstone.ai.runtime.workflow.WorkFlowExecutor`
> 위치: `dstone-ai-engine/src/main/java/net/dstone/ai/runtime/workflow/WorkFlowExecutor.java`
>
> 이 문서는 `dstone-ai-engine`을 처음 보는 사람도 "run() 한 번 호출하면 내부에서 정확히 무슨 일이 벌어지는가"를
> 코드 흐름 그대로 따라갈 수 있도록 아주 자세하게 풀어 쓴 자료입니다. 실제 소스코드(2026-09-20 기준)를 직접
> 읽고 정리했습니다.

---

## 1. 한 줄 요약

`WorkFlowExecutor.run()`은 **YAML로 정의된 Workflow(steps 목록)를 처음부터 끝까지(또는 멈춰야 할 때까지)
한 스텝씩 실행하는 "상태 기계(state machine)"** 입니다. 별도의 그래프 엔진 없이,

> "지금 몇 번째 step인가?" → "그 step을 실행한다" → "성공/실패에 따라 다음 step 번호를 계산한다" → 반복

이 단순한 루프만으로 **순차 실행 / 분기 / 병렬 실행 / 루프(재시도)** 4가지 패턴을 전부 처리합니다.

---

## 2. 등장인물 먼저 알아두기

run()을 읽기 전에 아래 5개 타입만 머릿속에 넣어두면 코드가 훨씬 쉽게 읽힙니다.

| 타입 | 역할 | 비유 |
|---|---|---|
| `WorkFlowDefinition` | YAML에서 읽어온 **설계도**. `steps` 목록, `maxIterations`(반복 상한) 등을 담음 | 레시피 |
| `StepDefinition` | 설계도 안의 스텝 하나. `type`(AGENT/TOOL/SUPERVISOR/APPROVAL), `onSuccess`/`onFailure`(다음 스텝 id) 등 | 레시피의 한 단계("끓인다", "간을 본다") |
| `WorkFlowExecution` | **지금 진행 중인 실행 1건의 상태**(record). `currentStepIndex`, `variables`(누적 변수), `status` 등을 담고 있고, DB(`AI_WORKFLOW_EXECUTION` 테이블)와 1:1 대응 | 지금 요리가 몇 번째 단계까지 왔는지 적어둔 메모 |
| `StepRunner` | 스텝 하나를 실제로 실행하는 부품(AGENT/SUPERVISOR용 `AgentStepRunner`, TOOL용 `ToolStepRunner`, APPROVAL용 `ApprovalStepRunner`) | 그 단계를 실제로 수행하는 요리사 |
| `StepOutput` / `StepResult` | 스텝 하나를 실행한 결과. `SUCCESS`/`FAILURE`/`PENDING` 중 하나 | 그 단계가 잘 됐는지 안 됐는지 적힌 결과표 |

`WorkFlowExecution`은 **불변 객체(record)** 입니다. 상태가 바뀔 때마다(`advanceTo`/`done`/`failed`/`waitingApproval`)
새 인스턴스를 만들어서 돌려주는 방식이라, "지금 이 실행이 어떤 상태였는지"를 어느 시점에 봐도 안전하게 추적할 수 있습니다.
다만 `variables` 맵만은 예외적으로 **같은 Map 인스턴스를 계속 공유**해서, 스텝들이 값을 계속 누적해 넣습니다.

---

## 3. run()이 호출되는 두 가지 상황

`run()`은 딱 하나의 메서드지만, 실제로는 두 가지 다른 상황에서 호출됩니다.

1. **신규 실행**: 사용자가 `POST /api/ai/workflow/{id}/execute`(동기) 또는 `/submit`(비동기)을 호출 →
   `WorkFlowExecution.start(...)`로 `currentStepIndex=0`, `status=RUNNING`인 새 실행이 만들어지고 → `run()` 호출
2. **승인(APPROVAL) 재개**: 이전에 `run()`이 `WAITING_APPROVAL` 상태로 멈춰서 리턴한 실행 건에 대해, 나중에
   사람이 승인/반려 결정을 내리면 → 그 결정이 `variables.approvals.{stepId}`에 기록된 뒤 → **같은 실행(같은 executionId,
   같은 currentStepIndex)** 을 가지고 `run()`이 다시 호출됨

즉 `run()` 입장에서는 "신규냐 재개냐"를 구분하는 코드가 전혀 없습니다. `execution.currentStepIndex()`부터
그냥 이어서 실행할 뿐입니다. 이게 이 클래스가 재기동/재개를 별도 로직 없이 자연스럽게 지원하는 핵심 아이디어입니다.

---

## 4. 전체 흐름 그림

```
run(workflow, execution) 호출
        │
        ▼
  ┌───────────────────────────────────────────┐
  │  while (true) 무한 루프                      │
  │                                             │
  │  1) 실행 횟수 체크 → maxIterations 초과 시 FAILED로 종료
  │  2) 지금 step 번호(currentIndex)로 StepDefinition 조회
  │  3) 같은 parallelGroup을 가진 인접 step이 있으면 묶어서 "그룹"으로 취급
  │  4) 그룹 크기가 1이면 runOne(), 2개 이상이면 runGroup() 실행
  │        └ 실행 중 예외 발생 → 그 자리에서 FAILED로 종료
  │  5) 결과가 PENDING(APPROVAL 대기)이면
  │        └ WAITING_APPROVAL 상태로 저장하고 즉시 리턴 (루프 탈출)
  │  6) 결과 데이터를 variables에 병합, 결과 텍스트를 "__previous"에 저장
  │  7) decideTransition()으로 다음 행동 결정
  │        ├ SUCCESS → DONE으로 저장하고 리턴 (루프 탈출)
  │        ├ FAIL    → FAILED로 저장하고 리턴 (루프 탈출)
  │        ├ NEXT_STEP / LOOP → currentIndex 갱신, RUNNING으로 저장, 루프 계속
  │        └ (그 외) → 이론상 발생하지 않는 방어 코드
  └───────────────────────────────────────────┘
```

핵심은 **"한 바퀴 = 스텝(또는 병렬 그룹) 하나 처리"** 이고, 한 바퀴가 끝날 때마다 DB에 상태를 저장한다는 점입니다.
그래서 서버가 중간에 재기동되어도, 마지막으로 저장된 `currentStepIndex`부터 다시 시작할 수 있습니다.

---

## 5. 코드 한 줄씩 따라가기

### 5-1. 준비 단계

```java
int maxIterations = workflow.maxIterations() == null ? Constants.WorkFlow.DEFAULT_MAX_ITERATIONS : workflow.maxIterations();
int currentIndex = execution.currentStepIndex();
WorkFlowExecution current = execution;
int executed = 0;
```

- `maxIterations`: YAML에 안 적혀 있으면 기본값 **5** (`Constants.WorkFlow.DEFAULT_MAX_ITERATIONS`)
- `currentIndex`: 신규 실행이면 0, 재개 실행이면 멈췄던 그 번호
- `current`: 매 바퀴마다 새로운 `WorkFlowExecution`으로 갱신되는 "지금 상태" 변수
- `executed`: 이번 `run()` 호출 안에서 지금까지 몇 바퀴(=몇 스텝) 돌았는지 세는 카운터

> ⚠️ 주의: `executed`는 **이번 run() 호출 한 번 안에서의** 카운터입니다. 승인 대기로 멈췄다가 재개된 경우
> 새로운 `run()` 호출이므로 `executed`는 다시 0부터 시작합니다. 즉 `maxIterations`는 "전체 누적 스텝 수"가
> 아니라 "한 번의 run() 호출(=승인 대기로 안 끊긴 구간) 안에서의 스텝 수 상한"입니다.

### 5-2. 무한루프 방지 체크

```java
while (true) {
    if (++executed > maxIterations) {
        return this.persistFailed(current, "최대 실행 횟수(...)를 초과했습니다(루프 정지) - ...");
    }
    ...
```

`onFailure`로 이전 스텝으로 되돌아가는 구성(재시도 루프)을 잘못 만들면 영원히 끝나지 않을 수 있는데,
이 카운터 하나가 그걸 막는 유일한 안전장치입니다. 초과하면 즉시 `FAILED`로 저장하고 리턴합니다.

### 5-3. 지금 스텝(또는 병렬 그룹) 찾기

```java
StepDefinition step = workflow.steps().get(currentIndex);
List<StepDefinition> group = this.parallelGroupOf(workflow.steps(), step);
```

- `workflow.steps()`는 YAML에 적힌 순서 그대로의 리스트입니다. `currentIndex`로 지금 스텝을 꺼냅니다.
- `parallelGroupOf()`는 이 스텝이 `parallelGroup` 값을 갖고 있으면, **같은 값을 가진 모든 스텝**을 찾아서
  리스트로 묶어줍니다(순서는 원래 선언 순서 그대로). `parallelGroup`이 없으면 자기 자신 하나만 담긴
  리스트를 돌려줍니다.

### 5-4. 실행: 단일 스텝이냐 병렬 그룹이냐

```java
GroupResult groupResult;
try {
    groupResult = group.size() > 1 ? this.runGroup(group, current) : this.runOne(step, current);
} catch (Exception e) {
    return this.persistFailed(current, "step[" + step.id() + "] 실행 중 예외가 발생했습니다 - " + e.getMessage());
}
```

- 그룹 크기가 1이면 `runOne()` (평범한 순차 실행)
- 2개 이상이면 `runGroup()` (동시 실행)
- **여기서 예외가 그대로 새어나오면** (StepRunner 내부에서 던진 RuntimeException 등) 그 즉시 `FAILED`로
  종료합니다. 이건 `onFailure` 분기를 타지 않는, "시스템 에러로 인한 강제 종료"입니다. (뒤에 5-8절에서 다시 설명)

이 두 메서드는 각각 5-6, 5-7절에서 자세히 설명합니다. 우선 둘 다 결과를 **`GroupResult`** 라는
내부 전용 값 객체(`pending`, `success`, `combinedText`, `mergedData`)로 통일해서 돌려준다는 것만
기억하고 넘어갑니다.

### 5-5. 승인 대기 처리

```java
if (groupResult.pending()) {
    current = current.waitingApproval(currentIndex);
    this.executionStore.update(current);
    return current;
}
```

`APPROVAL` 스텝이 아직 사람의 결정을 못 받았으면 `groupResult.pending() == true`입니다. 이 경우
**루프를 여기서 즉시 탈출**하고, 상태를 `WAITING_APPROVAL`로 바꿔 DB에 저장한 뒤 그 상태를 그대로 리턴합니다.
`currentIndex`는 그대로 유지되므로(같은 스텝 번호), 나중에 재개될 때 이 APPROVAL 스텝부터 다시 실행됩니다.

### 5-6. 변수 병합 & "직전 결과 텍스트" 저장

```java
this.mergeVariables(current.variables(), groupResult.mergedData());
current.variables().put(Constants.WorkFlow.PREVIOUS_TEXT_VARIABLE_KEY, groupResult.combinedText());
```

- `mergeVariables`: 스텝(또는 그룹)이 만들어낸 구조화 데이터(`StepOutput.data()`)를 Workflow 전역
  `variables` 맵에 그대로 `putAll` 합니다. 다음 스텝들이 `{변수명}` 토큰으로 이 값을 참조할 수 있게 됩니다.
- `PREVIOUS_TEXT_VARIABLE_KEY`(실제 값은 `"__previous"`, 사용자 변수명과 안 겹치도록 밑줄 2개로 시작)에
  방금 끝난 스텝(그룹)의 결과 텍스트를 저장해둡니다. 다음 스텝의 `inputTemplate`에 있는 `{previous}` 토큰이
  바로 이 값으로 치환됩니다.

### 5-7. 다음 행동 결정: `decideTransition`

```java
StepDefinition lastOfGroup = group.get(group.size() - 1);
StepFlow transition = this.decideTransition(workflow, lastOfGroup, groupResult.success(), groupResult.combinedText());
```

병렬 그룹이었다면 **그룹의 마지막 스텝**(선언 순서 기준)의 `onSuccess`/`onFailure`만 보고 다음 행동을
결정합니다(그룹 안의 다른 스텝들의 onSuccess/onFailure는 무시됩니다 — 병렬 그룹을 만들 때 이 점을
염두에 둬야 합니다). 이 메서드가 실제로 어떻게 판단하는지는 6절에서 따로 자세히 다룹니다.

### 5-8. 전이 결과에 따라 분기

```java
switch (transition.status()) {
    case SUCCESS:
        current = current.done(transition.message());
        this.executionStore.update(current);
        return current;
    case FAIL:
        return this.persistFailed(current, transition.message());
    case NEXT_STEP:
    case LOOP:
        currentIndex = this.indexOf(workflow.steps(), transition.nextStepId());
        current = current.advanceTo(currentIndex);
        this.executionStore.update(current);
        break;
    default:
        return this.persistFailed(current, "알 수 없는 스텝 전이 상태입니다: " + transition.status());
}
```

| status | 의미 | 동작 |
|---|---|---|
| `SUCCESS` | Workflow 전체가 성공적으로 끝남 | `status=DONE`, `resultText`에 마지막 결과 텍스트 저장 후 **리턴** |
| `FAIL` | Workflow 전체가 실패로 끝남 | `status=FAILED`, `errorMessage`에 실패 사유 저장 후 **리턴** |
| `NEXT_STEP` | 앞으로 있는 다른 스텝으로 이동 | `currentIndex` 갱신, `status=RUNNING` 저장 후 **while 루프 계속** |
| `LOOP` | 같은/이전 스텝으로 되돌아감(재시도) | 위와 동작은 완전히 동일 — 로그/가독성 구분용일 뿐 |
| (그 외) | 이론상 도달 불가 | 방어적 코드. `ERROR`/`WAITING_APPROVAL`은 `decideTransition`이 절대 만들지 않음(각각 5-4의 catch, 5-5의 pending 분기에서 이미 처리되기 때문) |

`NEXT_STEP`과 `LOOP`는 실제 동작이 완전히 같다는 점이 재미있는 부분입니다. 둘을 구분하는 유일한 목적은
"이게 앞으로 가는 정상 진행인지, 뒤로 돌아가는 재시도인지"를 로그에서 사람이 읽기 쉽게 구분하기 위함이고,
무한루프를 막는 실질적인 안전장치는 오직 5-2절의 `maxIterations` 카운터 하나뿐입니다.

---

## 6. `decideTransition` 상세 — "다음 스텝을 어떻게 정하는가"

```java
private StepFlow decideTransition(WorkFlowDefinition workflow, StepDefinition step, boolean success, String text) {
    String nextId = success ? step.onSuccess() : step.onFailure();
    if (nextId == null) {
        if (!success) {
            return StepFlow.fail(...);                       // ① onFailure 없이 실패 → 즉시 Workflow 실패
        }
        String sequentialNextId = this.nextSequentialId(workflow.steps(), step.id());
        return sequentialNextId == null ? StepFlow.success(text) : StepFlow.next(sequentialNextId);
                                                              // ② onSuccess 없이 성공 → 목록상 다음 스텝(없으면 전체 성공)
    }
    if (Constants.WorkFlow.SUCCESS_SENTINEL.equals(nextId)) {
        return StepFlow.success(text);                       // ③ onSuccess/onFailure == "SUCCESS" 예약어
    }
    if (Constants.WorkFlow.FAIL_SENTINEL.equals(nextId)) {
        return StepFlow.fail(text);                          // ④ onSuccess/onFailure == "FAIL" 예약어
    }
    int currentIndex = this.indexOf(workflow.steps(), step.id());
    int nextIndex = this.indexOf(workflow.steps(), nextId);
    if (nextIndex < 0) {
        throw new IllegalStateException(...);                // ⑤ 존재하지 않는 step id → 예외(→ 5-4의 catch에서 FAILED 처리)
    }
    return nextIndex <= currentIndex ? StepFlow.loop(nextId) : StepFlow.next(nextId);
                                                              // ⑥ 지정된 step id의 위치로 순서 비교해서 LOOP/NEXT_STEP 결정
}
```

정리하면 판단 순서는 이렇습니다.

1. **성공했으면 `onSuccess`, 실패했으면 `onFailure`** 값을 본다.
2. 그 값이 **없으면(null)**:
   - 실패였다면 → 바로 Workflow 실패로 끝난다 (실패를 다룰 다음 스텝이 지정 안 됐으므로 당연한 결과).
   - 성공이었다면 → YAML에 적힌 순서상 **바로 다음 스텝**으로 진행한다. 이게 없으면(마지막 스텝이면)
     Workflow 전체가 성공으로 끝난다. → **가장 흔한 "그냥 순서대로 실행"이 바로 이 경로입니다.**
3. 값이 있는데 예약어 `"SUCCESS"`/`"FAIL"`이면 → 그 자리에서 즉시 Workflow를 종료시킨다.
4. 그 외의 값(다른 스텝의 id)이면 → 그 스텝이 지금 스텝보다 **목록에서 앞쪽에 있으면 LOOP**,
   **뒤쪽(또는 같은 자리)에 있으면 NEXT_STEP** 으로 표시한다(동작은 동일, 이름만 다름).

> 성공/실패는 스텝 종류(`StepType`)마다 판정 방식이 다릅니다. `AGENT`는 항상 성공, `TOOL`은 응답 텍스트가
> "실패:"로 시작하는지, `SUPERVISOR`는 구조화된 `Verdict.pass()`, `APPROVAL`은 사람의 승인/반려로 정해집니다.
> (자세한 내용은 7절 참고)

---

## 7. 스텝 하나(`runOne`) vs 병렬 그룹(`runGroup`)

### 7-1. `runOne` — 평범한 단일 스텝 실행

```java
private GroupResult runOne(StepDefinition step, WorkFlowExecution execution) {
    StepInput input = new StepInput(this.previousText(execution), execution.variables());
    long start = System.nanoTime();
    StepOutput output = this.runnerFor(step.type()).run(execution, step, input);
    long durationMs = (System.nanoTime() - start) / 1_000_000;

    if (output.result() == StepResult.PENDING) {
        return GroupResult.pendingResult();
    }

    boolean success = output.result() == StepResult.SUCCESS;
    this.executionStore.appendHistory(execution.executionId(), new StepHistoryEntry(...));
    return new GroupResult(false, success, output.primaryText(), output.data() == null ? Map.of() : output.data());
}
```

1. `StepInput`을 만든다 — `previousText(execution)`(직전 스텝 결과, `"__previous"` 변수)와 전역 변수 맵을 담음.
2. `runnerFor(step.type())`로 이 스텝 타입에 맞는 `StepRunner` 구현체를 찾아 `run()` 호출.
   - `AGENT`/`SUPERVISOR` → `AgentStepRunner`
   - `TOOL` → `ToolStepRunner`
   - `APPROVAL` → `ApprovalStepRunner`
3. 결과가 `PENDING`이면(=APPROVAL이 아직 결정을 못 받음) 곧바로 `GroupResult.pendingResult()`를 돌려주고,
   history는 남기지 않는다(아직 끝난 게 아니므로).
4. 그 외의 경우(SUCCESS/FAILURE)에는 **이력 테이블(`AI_WORKFLOW_EXECUTION_STEP_HISTORY`)에 실행 기록 1건을
   즉시 추가**한다. 스텝 id, 타입, ref, 성공 여부, 걸린 시간(ms), 결과 요약, 실패 사유, 실행 시각이 저장된다.

### 7-2. `runGroup` — 병렬 그룹 실행

```java
private GroupResult runGroup(List<StepDefinition> group, WorkFlowExecution execution) {
    ...
    for (StepDefinition step : group) {
        futures.put(step, CompletableFuture.supplyAsync(() -> this.runnerFor(step.type()).run(execution, step, input)));
    }
    // 이후 futures를 "선언 순서대로" join()하며 결과를 모은다
    ...
}
```

- 같은 `parallelGroup` 값을 가진 형제 스텝들을 **모두 같은 `StepInput`(같은 `{previous}`, 같은 변수 스냅샷)**
  으로 동시에 실행합니다(`CompletableFuture.supplyAsync`).
- 실행 자체는 동시에 일어나지만, **결과를 모으는 순서는 항상 그룹의 선언 순서 그대로**입니다. 그래서:
  - `combinedText`(결합 텍스트)는 항상 같은 순서로 줄바꿈(`\n`)을 사이에 두고 이어붙여지고,
  - `mergedData`(변수 병합)도 항상 같은 순서로 `putAll`되므로,
  - **매번 실행할 때마다 결과가 결정적(deterministic)** 입니다 — 실행 순서는 랜덤이어도 "누구 결과가
    먼저 합쳐지는가"는 랜덤이 아닙니다.
- 형제 중 하나라도 예외를 던지면(`entry.getValue().join()`이 예외) 그 스텝은 실패로 기록되고
  `anyFailed = true`가 되지만, **다른 형제들의 실행/기록은 계속 진행**됩니다. 즉 병렬 그룹은 "하나 죽으면
  나머지도 강제 중단"이 아니라 "다 끝날 때까지 기다린 다음, 하나라도 실패했으면 그룹 전체를 실패로 본다"는
  방식입니다.
- **주의**: 병렬 그룹 안의 스텝 중 하나가 `PENDING`(APPROVAL)을 반환하는 상황은 현재 코드에서
  다뤄지지 않습니다. `StepDefinition`의 클래스 주석에도 명시되어 있듯 **APPROVAL 타입은 `parallelGroup`을
  가질 수 없다**는 제약이 있어서, 이 상황 자체가 애초에 발생하지 않도록 설계 단계에서 막혀 있습니다.

---

## 8. 각 `StepType`이 실제로 무엇을 하는지 (참고)

`run()`을 완전히 이해하려면 각 `StepRunner`가 "성공/실패를 어떻게 판정하는지"도 알아야 합니다.

| StepType | 담당 Runner | 실제 동작 | 성공/실패 판정 |
|---|---|---|---|
| `AGENT` | `AgentStepRunner.runAgent` | `AgentRegistry`에서 `ref`로 찾은 Agent로 LLM을 1회 호출 | **항상 성공** (`StepOutput.success`) |
| `SUPERVISOR` | `AgentStepRunner.runSupervisor` | AGENT와 동일하게 LLM 호출하되, 응답을 자유 텍스트가 아니라 구조화된 `Verdict(pass, reason)`로 강제 파싱 | `verdict.pass()==true`면 성공, 그 외(명시적 false 또는 파싱 예외)는 **fail-closed**로 실패 처리 |
| `TOOL` | `ToolStepRunner` | LLM 없이 `ToolExecutor`로 등록된 Tool을 직접 호출(결정적 검증, 예: SQL 문법 검사) | Tool 응답이 `"실패:"`로 시작하면 실패, 아니면 성공 |
| `APPROVAL` | `ApprovalStepRunner` | `ref` 없음. `variables.approvals.{stepId}`에 결정이 있는지만 확인 | 결정이 없으면 **PENDING**, 있으면 `approved`값으로 성공/실패 |

몇 가지 세부 사항이 눈여겨볼 만합니다.

- **TOOL 스텝은 성공 시 "Tool의 응답 문구"가 아니라 "검증받은 원본 입력"을 그대로 다음 스텝에 넘깁니다.**
  ("통과했습니다" 같은 메시지 자체는 다음 스텝에 새로운 정보가 아니기 때문입니다.) 실패 시에는 원본 뒤에
  실패 사유를 덧붙여서, `onFailure`로 되돌아간 스텝이 "무엇을, 왜 고쳐야 하는지" 둘 다 볼 수 있게 합니다.
- **TOOL 스텝은 Tool을 호출하기 전에 LLM이 만들어낸 흔한 "군더더기"(마크다운 코드펜스, `[변환된 SQL]`
  같은 대괄호 레이블)를 정규식으로 벗겨냅니다.** 앞선 AGENT 스텝이 프롬프트 지시를 완벽히 지키지 않는
  경우가 실전에서 반복 관찰되었기 때문에, "이전 스텝 출력 = 다음 스텝이 쓸 순수 데이터"라는 계약을
  지키기 위한 방어 코드입니다.
- **APPROVAL 스텝은 "재개"를 위한 별도 코드가 없습니다.** 그냥 같은 스텝을 다시 실행했을 때 이번엔
  결정이 존재하니 SUCCESS/FAILURE를 반환할 뿐입니다.

---

## 9. 저장(영속화) 시점 총정리

`run()`이 진행되는 동안 DB에 쓰기가 발생하는 시점은 정확히 다음 4곳입니다.

| 시점 | 저장 내용 | 메서드 |
|---|---|---|
| 스텝(또는 그룹의 각 스텝)이 SUCCESS/FAILURE로 끝날 때마다 | `AI_WORKFLOW_EXECUTION_STEP_HISTORY`에 이력 1건 추가 | `executionStore.appendHistory(...)` |
| APPROVAL 스텝이 PENDING을 반환했을 때 | 실행 상태를 `WAITING_APPROVAL`로 업데이트 | `executionStore.update(current)` (5-5절) |
| 스텝 처리가 끝나 다음 스텝으로 넘어갈 때(NEXT_STEP/LOOP) | 실행 상태를 `RUNNING` + 새 `currentStepIndex`로 업데이트 | `executionStore.update(current)` (5-8절) |
| Workflow가 SUCCESS로 끝날 때 | 상태 `DONE` + `resultText` 저장 | `executionStore.update(current)` |
| Workflow가 FAIL/예외/최대반복초과로 끝날 때 | 상태 `FAILED` + `errorMessage` 저장 | `persistFailed()` → `executionStore.update(failed)` |

**"한 스텝(또는 병렬 그룹) 처리 = 상태 저장 1번"** 이 원칙이기 때문에, 서버가 어느 시점에 죽어도
잃어버리는 건 "지금 처리 중이던 딱 한 스텝"뿐이고, 그 이전까지의 진행 상황은 전부 보존됩니다.

---

## 10. Workflow가 끝나는(또는 멈추는) 모든 경로 한눈에 보기

| 경로 | 트리거 | 최종 상태 | 비고 |
|---|---|---|---|
| ① 최대 반복 초과 | `executed > maxIterations` | `FAILED` | onFailure 루프 설계 오류를 의심해봐야 함 |
| ② 스텝 실행 중 예외 | `runOne`/`runGroup`이 예외를 던짐 | `FAILED` | 5-4절의 catch. Tool/Agent 호출 자체가 죽은 경우 등 |
| ③ 승인 대기 | APPROVAL 스텝이 아직 결정 없음 | `WAITING_APPROVAL` | 나중에 같은 실행으로 재개 가능 (유일하게 "끝나지 않고 잠시 멈추는" 경로) |
| ④ onFailure 없이 실패 | 스텝 실패인데 `onFailure`가 null | `FAILED` | decideTransition ①번 규칙 |
| ⑤ onSuccess/onFailure == "FAIL" | 예약어 사용 | `FAILED` | decideTransition ④번 규칙 |
| ⑥ 존재하지 않는 step id로 이동 시도 | `onSuccess`/`onFailure`에 오타 등 | `FAILED` | `IllegalStateException`이 던져지고 ②경로로 흡수됨 |
| ⑦ 정상 종료(성공) | 마지막 스텝까지 순조롭게 진행되었거나 `onSuccess`가 예약어 "SUCCESS" | `DONE` | 가장 흔한 정상 경로 |

---

## 11. 예시로 실제 흐름 따라가보기

`docs/09.dstone-ai-engine.md`에 언급된 실제 검증 사례인 `oracle-to-postgresql` Workflow
(`analyze`(AGENT) → `convert`(AGENT) → `validate`(TOOL), `validate`의 `onFailure`가 `convert`를 가리키는
재시도 루프 구성이라고 가정)를 예로 들어보겠습니다.

1. **최초 호출**: `currentIndex=0`(`analyze`). `parallelGroupOf` → 그룹 크기 1 → `runOne` →
   `AgentStepRunner`가 LLM 호출 → 항상 SUCCESS. `onSuccess`가 없으므로 → 목록상 다음 스텝인 `convert`로 이동
   (`NEXT_STEP`). 상태 저장(`RUNNING`, index=1).
2. **두 번째 바퀴**: `currentIndex=1`(`convert`). 마찬가지로 AGENT → 항상 SUCCESS → 다음 스텝 `validate`로
   이동. 상태 저장(`RUNNING`, index=2).
3. **세 번째 바퀴**: `currentIndex=2`(`validate`, TOOL). `ToolStepRunner`가 SQL 문법 검증 Tool을 호출.
   - **문법이 맞으면**: SUCCESS → `validate`의 `onSuccess`가 없으므로(마지막 스텝) → `StepFlow.success(text)`
     → `DONE`으로 저장하고 리턴. **끝.**
   - **문법이 틀리면**: FAILURE → `validate`의 `onFailure`가 `convert`를 가리킴 → `convert`의 인덱스(1)가
     지금 인덱스(2)보다 **앞쪽**이므로 `StepFlow.loop("convert")` → `currentIndex=1`로 되돌아감 →
     `RUNNING`으로 저장. **다시 2번부터 반복.** (단, `executed` 카운터가 계속 올라가므로 `maxIterations`
     번 넘게 실패하면 결국 ①경로로 `FAILED` 종료됩니다.)

여기에 만약 `validate` 다음에 사람 승인이 필요한 `deploy`(APPROVAL) 스텝이 하나 더 있었다면:

4. `validate` 성공 → `deploy`로 이동 → `ApprovalStepRunner.run()` 호출 → 아직 아무도 승인 안 했으므로
   `PENDING` 반환 → `run()`은 `WAITING_APPROVAL` 상태로 저장하고 **즉시 리턴**.
5. (시간이 지난 뒤) 담당자가 승인 API를 호출 → `variables.approvals.deploy = {approved:true, ...}` 기록 →
   **같은 executionId, 같은 currentIndex(=deploy의 인덱스)** 로 `run()`이 다시 호출됨.
6. 이번엔 `ApprovalStepRunner`가 결정을 발견 → SUCCESS 반환 → `deploy`가 마지막 스텝이면 `DONE`으로 종료.

---

## 12. 한 문장 정리

> `WorkFlowExecutor.run()`은 "지금 스텝 실행 → 성공/실패 판정 → onSuccess/onFailure로 다음 스텝 계산 →
> 상태 저장 → 반복"이라는 단순한 루프 하나로, AGENT/TOOL/SUPERVISOR/APPROVAL 4가지 스텝 타입과
> 순차/분기/병렬/루프 4가지 실행 패턴을 전부 처리하며, 매 바퀴마다 DB에 상태를 저장해두기 때문에
> 승인 대기나 서버 재기동으로 끊겨도 정확히 멈췄던 지점부터 다시 이어갈 수 있습니다.
