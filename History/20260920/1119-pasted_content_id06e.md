# pasted_content_id06e

> 생성일시: 2026-09-20 11:19

## 질문

<pasted_content id="06e6">
net.dstone.ai.runtime.workflow.WorkFlowExecutor.run(WorkFlowDefinition, WorkFlowExecution)
호출 시 진행되는 프로세스를 아주 자세하고 쉽고 친절하게 설명한 자료를 /app/dstone/docs/temp 아래 생성해줘.
</pasted_content id="06e6">

---

## 답변

사용자가 요청한 `WorkFlowExecutor.run()` 프로세스 설명 자료를 만들기 위해 먼저 실제 소스 코드를 찾아 분석하겠습니다.

---

## 답변

파일에 인코딩 깨짐 여부를 확인하겠습니다.

---

## 답변

`docs/temp/workflow-executor-run-process.md` 파일에 `WorkFlowExecutor.run()` 실행 흐름을 실제 소스코드(`WorkFlowExecutor`, `StepDefinition`, `StepRunner` 3종, `WorkFlowExecution`, `WorkFlowExecutionStore` 등) 기준으로 자세히 정리했습니다.

주요 내용:
- 등장인물(설계도/스텝/실행상태/러너/결과) 소개
- run() 전체 흐름 다이어그램 + 코드 한 줄씩 대응 설명
- `decideTransition`의 SUCCESS/FAIL/NEXT_STEP/LOOP 판단 로직
- 단일 스텝(`runOne`) vs 병렬 그룹(`runGroup`) 실행 차이
- AGENT/SUPERVISOR/TOOL/APPROVAL 각 타입의 성공·실패 판정 방식
- DB 저장(영속화) 시점 총정리, 종료/중단 경로 7가지 표
- `oracle-to-postgresql` 워크플로우 예시로 실제 값 흐름 워크스루

---

## 질문

<pasted_content id="06e6">
dstone-ai-engine java 소스중에 람다 식이 있으면 람다식을 제거하고 이전 스타일로 수정해줘.
그리고 기억해. 앞으로 소스를 만들 때 람다식은 최대 지양을 해줘.
</pasted_content id="06e6">

---

## 답변

まず이 지침을 기억해두고, 그 다음 실제 람다식을 찾아 수정하겠습니다.

---

## 답변

Now let's inspect each file to find actual lambda expressions/method references (not just false-positive matches like generics or comments).

---

## 답변

switch 표현식(`case X ->`)은 람다식이 아니므로 그대로 두고, 실제 람다식·메서드 참조만 골라서 변경하겠습니다. 관련 파일들을 마저 확인합니다.

---

## 답변

이제 `WorkFlowExecutionService.java`의 두 곳(비동기 실행 람다, computeIfAbsent 람다)을 수정합니다.

---

## 답변

Good, only the SQL string formatting differs slightly; content is otherwise the same. Now converting the two `this::mapExecution` and one `this::mapHistoryEntry` method references to anonymous `RowMapper` instances.

---

## 답변

Matches what I had. Now converting the lambda-based `supplyAsync` call to an anonymous `Supplier`.

---

## 답변

Add the `Supplier` import.

---

## 답변

Now let's fix the reactive lambda chain in `HttpCallTool.java`.

---

## 답변

테스트 코드에도 람다식이 있는지 추가로 확인하겠습니다.

---

## 답변

No test sources exist. Now let's verify the whole module still compiles.

---

## 답변

Compile succeeded with no errors. Let's re-run the lambda scan to confirm nothing real remains.

---

## 답변

All remaining matches are switch expressions (`case X ->`), comments, or string literals — not actual lambdas/method references. The refactor is complete and the module compiles cleanly.

**변경 요약** (`dstone-ai-engine`):

| 파일 | 변경 내용 |
|---|---|
| `WorkFlowExecutionController.java` | `.stream().map(WorkFlowExecutionSummary::from).toList()` → for문 + `ArrayList` |
| `WorkFlowExecutionService.java` | `CompletableFuture.runAsync(() -> ...)` → 익명 `Runnable`; `computeIfAbsent(key, key -> ...)` → `get`+`instanceof`+`put` 명시적 분기 |
| `WorkFlowExecutionStore.java` | `this::mapExecution`/`this::mapHistoryEntry` 메서드 참조 → 익명 `RowMapper` 반환 헬퍼(`executionRowMapper()`/`historyRowMapper()`) |
| `WorkFlowExecutor.java` | `CompletableFuture.supplyAsync(() -> ...)` → 익명 `Supplier<StepOutput>` |
| `HttpCallTool.java` | `exchangeToMono(response -> ... .map(body -> ...))` 중첩 람다 → 익명 `Function<ClientResponse, Mono<HttpOutcome>>` + 익명 `Function<String, HttpOutcome>` |

`case X -> ...` 형태의 **switch 표현식**은 람다식이 아닌 별개 문법이라 그대로 두었습니다(변경을 원하시면 말씀해주세요).

그리고 "람다식 최대 지양" 지침은 메모리에 저장해서, 앞으로 이 저장소의 모든 Java 코드 작성 시 기본적으로 람다/메서드 참조 대신 익명 클래스·for문 스타일을 쓰도록 반영해두었습니다.

---

## 질문

<pasted_content id="06e6">
parallelGroup 으로 묶인 step들이 존재하고 그 parallelGroup의 next step이 존재한다고 할 때
net.dstone.ai.runtime.workflow.WorkFlowExecutor.decideTransition(WorkFlowDefinition, StepDefinition, boolean, String)
메소드 안에서 next step 의 StepFlow 를 구할 때 상기 상황에 대한 고려가 들어가 있나?
</pasted_content id="06e6">

---

## 답변

**결론: 부분적으로만 고려되어 있습니다.** "이 그룹의 next step을 어떻게 정할지" 자체는 명시적으로 처리하지만, 그 처리가 성립하려면 검증되지 않은 전제 두 가지가 조용히 깔려 있습니다.

## 실제로 되어 있는 것

`decideTransition`은 `step`(currentIndex가 가리키던 그 스텝) 대신 **그룹의 마지막 멤버**를 받아서 판단합니다.

```java
// WorkFlowExecutor.java:126-127
StepDefinition lastOfGroup = group.get(group.size() - 1);
StepFlow transition = this.decideTransition(workflow, lastOfGroup, groupResult.success(), groupResult.combinedText());
```

`group`은 `parallelGroupOf()`가 `workflow.steps()` 전체를 순서대로 훑으며 같은 `parallelGroup` 값을 모은 것이므로(310행), `group.get(group.size()-1)`은 **항상 steps 리스트 전체에서 인덱스가 가장 큰(제일 뒤에 선언된) 그룹 멤버**가 됩니다 — currentIndex가 그룹의 몇 번째 멤버를 가리키고 있었는지와 무관하게요. 그래서:

- `decideTransition` 안에서 `nextId = success ? step.onSuccess() : step.onFailure()` (279-280행 근처)는 **그룹의 마지막 멤버에 적힌 onSuccess/onFailure만** 봅니다.
- 그게 비어 있으면 `nextSequentialId(workflow.steps(), step.id())`(285행)로 **steps 리스트에서 lastOfGroup 바로 다음 항목**을 찾습니다.

즉 "병렬 그룹 다음에 진짜 다음 스텝이 있다"는 상황 자체는 **의도적으로 그룹의 마지막 멤버를 대표로 세워서** 처리하도록 설계돼 있습니다. (이전에 만든 `docs/temp/workflow-executor-run-process.md` 5-7절에도 이 부분을 이미 적어뒀습니다.)

## 그런데 검증 없이 전제만 깔려 있는 두 가지

`WorkFlowRegistry.load()`(기동 시 YAML 검증)를 확인해보니 **APPROVAL이 parallelGroup을 못 갖는다는 것만** 검증하고, 아래 두 가지는 전혀 검증하지 않습니다.

1. **그룹 멤버가 steps 리스트에서 연속 배치돼야 한다는 것.** `parallelGroupOf`는 같은 `parallelGroup` 값이면 리스트 어디에 흩어져 있어도 다 모아버립니다. 만약 YAML 작성자가 그룹 멤버 사이에 다른 스텝을 끼워 넣으면, `nextSequentialId`는 "lastOfGroup 바로 다음 인덱스"만 보므로 실제로는 "그룹 다음 스텝"이 아니라 엉뚱한 스텝(끼어 있던 스텝이거나 같은 그룹의 또 다른 멤버)을 다음 스텝으로 잘못 계산할 수 있습니다.
2. **onSuccess/onFailure는 반드시 "마지막 순서 멤버"에 적어야 한다는 것.** 그룹의 첫 번째나 중간 멤버에 onSuccess/onFailure를 적어도 `decideTransition`은 그 값을 아예 쳐다보지 않습니다(조용히 무시됨) — YAML 작성자가 실수로 첫 멤버에 적어두면 "왜 다음 스텝으로 안 넘어가지?" 하고 헤맬 수 있는 지점입니다.

정리하면, **"parallelGroup 다음에 next step이 있다"는 시나리오 자체를 처리하는 로직은 분명히 있지만(lastOfGroup을 대표로 세우는 것), 그 로직이 올바로 작동하려면 "그룹은 연속 배치, onSuccess/onFailure는 마지막 멤버에" 라는 두 가지 규칙을 YAML 작성자가 지켜야 하고, 코드는 이걸 강제하지 않습니다.** 원하시면 `WorkFlowRegistry`에 이 두 규칙을 검증하는 로직을 추가해드릴까요?

---

## 질문

응 추가해줘. 그리고 주석은 최대한 쉽고 친절하게 넣어줘.

---

## 답변

이제 컴파일 확인 후, 실제로 두 규칙을 위반하는 테스트 YAML로 검증해보겠습니다.

---

## 답변

기존에 등록된 실제 워크플로우 YAML들이 새 검증 규칙을 통과하는지 확인하겠습니다.

---

## 답변

이 워크플로우는 두 규칙을 모두 정확히 지키고 있습니다 (연속 배치 + onSuccess/onFailure가 마지막 스텝에만 있음). 전체 모듈을 빌드해서 기동 시 검증이 정상 통과하는지 최종 확인하겠습니다.

---

## 답변

CLAUDE.md 지침에 따라 관련 문서(`docs/09.dstone-ai-engine.md`)에도 이 검증 규칙을 반영해야 하는지 확인하겠습니다.

---

## 답변

Line 198 설명에 새 검증 규칙 한 문장을 추가해서 문서를 최신 상태로 맞추겠습니다.

---

## 답변

`WorkFlowRegistry`에 검증 로직을 추가하고, 관련 아키텍처 문서(`docs/09.dstone-ai-engine.md`)도 함께 업데이트했습니다.

**추가한 내용** (`WorkFlowRegistry.java`, `validateParallelGroups()`):
- **규칙 1**: 같은 `parallelGroup` 값의 스텝들이 `steps` 목록에서 서로 붙어 있어야 함 → 중간에 다른 스텝이 끼면 `IllegalStateException`
- **규칙 2**: `onSuccess`/`onFailure`는 그룹의 **마지막 스텝**에만 적을 수 있음 → 마지막이 아닌 멤버에 적으면 (어차피 실행 시 무시되므로) 기동 시점에 바로 에러

두 검증 모두 기존 `validateApprovalStepsNotParallel()`처럼 `load()`에서 워크플로우 등록 시 자동으로 실행됩니다. 주석은 "왜 이 검사가 필요한지"(전제가 안 지켜지면 어떤 문제가 생기는지)부터 "각 계산식이 뭘 확인하는 건지"까지 최대한 쉽게 풀어 썼습니다.

**검증**: 람다 없이 for문으로 구현했고, `mvn compile` 통과 확인 + 리플렉션으로 3가지 케이스(정상/규칙1 위반/규칙2 위반)를 직접 호출해 의도대로 통과·실패하는 것 확인했습니다. 기존에 실제 등록된 `parallel-test.yml`은 두 규칙을 이미 잘 지키고 있어 영향 없습니다.

---

## 질문

<pasted_content id="06e6">
이 내용이 parallelGroup 내에서도 APPROVAL 대기 상황이 존재할 수 있다는 상황도 포함하나?
</pasted_content id="06e6">

---

## 답변

**아닙니다. 이 검증(`validateParallelGroups`)은 그 상황을 다루지 않습니다** — 왜냐하면 그 상황 자체가 **다른, 이미 있던 규칙**에 의해 애초에 발생할 수 없도록 막혀 있기 때문입니다.

## 왜 그 상황이 안 생기는지

`WorkFlowRegistry.load()`를 보면 제가 이번에 추가한 `validateParallelGroups()`보다 **먼저부터 있던** 검증이 하나 더 있습니다.

```java
// WorkFlowRegistry.java - validateApprovalStepsNotParallel (기존 코드, 제가 건드리지 않음)
if (step.type() == StepType.APPROVAL && !StringUtil.isEmpty(step.parallelGroup())) {
    throw new IllegalStateException("... APPROVAL 스텝은 parallelGroup을 가질 수 없습니다.");
}
```

즉 **APPROVAL 타입 스텝은 애초에 parallelGroup 값을 가질 수 없습니다** — YAML에 그렇게 적으면 기동 시점에 바로 막힙니다. 그리고 `PENDING`이라는 결과는 오직 `ApprovalStepRunner`만 만들어냅니다(`StepResult.PENDING`의 Javadoc: "ApprovalStepRunner가 사람의 승인/반려 결정을 아직 못 받았을 때만 반환한다"). 그래서 "parallelGroup 안에 PENDING(승인 대기) 스텝이 존재하는 상황"은 논리적으로 나타날 수가 없습니다 — APPROVAL이 그룹에 못 들어가니까요.

## 그런데 코드를 보면 흥미로운 지점이 하나 있습니다

`runGroup()`(그룹 실행 담당 메서드)을 실제로 열어보면, `runOne()`과 달리 **`PENDING`을 명시적으로 처리하는 분기가 아예 없습니다.**

```java
// runOne()에는 있음:
if (output.result() == StepResult.PENDING) {
    return GroupResult.pendingResult();
}

// runGroup()에는 이런 분기가 없음 - success만 체크:
boolean success = output.result() == StepResult.SUCCESS;
```

즉 `runGroup()`은 **"그룹 멤버 중에 PENDING을 반환하는 애는 절대 없다"는 전제를 그냥 믿고 짜여 있습니다.** 만약(정말 만약) 그 전제가 깨진다면 — 예를 들어 나중에 누군가 새 StepType을 추가하면서 실수로 PENDING을 반환하게 만들거나, `validateApprovalStepsNotParallel` 체크를 우회하는 경로가 생긴다면 — `runGroup()`은 그걸 `SUCCESS`가 아니니까 그냥 **조용히 실패로 처리**해버립니다(사유도 안 남긴 채로, `output.failureReason()`이 null이라서). "승인 대기 중"이라는 진짜 상황을 "실패"로 오판하는 것이므로, 사용자 입장에선 매우 헷갈리는 버그가 됩니다.

## 정리

| 질문 | 답 |
|---|---|
| 제가 추가한 `validateParallelGroups`(연속배치/onSuccess소유권 규칙)가 이 상황을 다루나? | **아니요.** 그건 별개 관심사입니다. |
| 그럼 이 상황 자체가 지금 막혀 있나? | **네.** 기존에 있던 `validateApprovalStepsNotParallel`이 "APPROVAL은 parallelGroup 자체를 못 가진다"고 원천 차단하고 있어서, 지금 코드베이스에서는 이 상황이 나올 수 없습니다. |
| 그래도 안전한가? | `runGroup()`이 "그룹엔 PENDING이 없다"는 걸 스스로 재확인하지 않고 **전적으로 그 startup 검증 하나만 믿고 있다**는 점은 잠재적 위험 요소입니다. 원하시면 `runGroup()`에도 방어적으로 `PENDING` 분기(예: 그룹 안에서 PENDING이 나오면 "아직 지원하지 않는 조합"이라는 명확한 예외를 던지도록)를 추가해서 이중 안전장치를 걸어드릴까요?

---

## 질문

<pasted_content id="06e6">
내말은 아래와 같은 흐름을 가질때
step1 -> step2-1(parallelGroup:step2) -> step2-2(parallelGroup:step2) -> step2-3(parallelGroup:step2) -> step3
step2-2 가 type: APPROVAL 이라고 할 때 문제가 없겠느냐는 말이야.
</pasted_content id="06e6">

---

## 답변

네, 정확히 그 상황이 이미 막혀 있습니다 — 다만 제가 지난번에 추가한 규칙이 아니라 **원래부터 있던 `validateApprovalStepsNotParallel`** 때문입니다. 그 규칙은 스텝이 그룹의 첫번째/중간/마지막 어느 위치에 있는지는 전혀 안 보고, "이 스텝의 `type`이 APPROVAL인데 `parallelGroup` 값이 채워져 있는가"만 봅니다. `step2-2`가 `parallelGroup: step2`를 가진 채로 `type: APPROVAL`이면 바로 걸립니다. 사용자가 준 예시로 실제 재현해서 확인해보겠습니다.

---

## 답변

**재현 확인 결과: 그 구성은 애초에 서버가 뜨지도 못합니다.**

```
막힘(예상대로): workflow[example]의 step[step2-2]: APPROVAL 스텝은 parallelGroup을 가질 수 없습니다.
```

## 왜 "가운데 있는 step"인지 여부가 상관없는지

`validateApprovalStepsNotParallel`은 그룹 내 위치(첫/중간/마지막)를 전혀 안 보고, `steps` 전체를 순회하면서 **스텝 하나하나에 대해** 딱 이 조건만 봅니다.

```java
if (step.type() == StepType.APPROVAL && !StringUtil.isEmpty(step.parallelGroup())) {
    throw new IllegalStateException(...);
}
```

`step2-2`는 `parallelGroup: step2`(비어있지 않음) + `type: APPROVAL`이므로, 그룹의 몇 번째 멤버인지와 무관하게 바로 걸립니다. (참고로 이건 `WorkFlowRegistry.load()`의 `@PostConstruct`에서 던져지는 예외라, `example`이라는 이 워크플로우 하나만 조용히 빠지는 게 아니라 **Spring 컨텍스트 기동 자체가 실패**합니다 — 그래서 "일단 떠서 실행해봤더니 이상하게 동작하더라" 같은 상황이 안 생기고, 배포 시점에 바로 걸러집니다.)

## 정리

질문하신 `step1 → (step2-1/step2-2/step2-3, parallelGroup=step2) → step3`에서 `step2-2`가 APPROVAL인 구성은:

- 제가 지난번 추가한 `validateParallelGroups`(연속배치/onSuccess소유권)와는 **무관**하고
- **더 앞서 있던 `validateApprovalStepsNotParallel`**이 정확히 이 케이스를 막아서
- YAML을 그렇게 작성하는 순간 서버 기동 시 `IllegalStateException`으로 즉시 실패합니다.

즉 "parallelGroup 안에 APPROVAL이 껴서 대기 상태가 되는" 시나리오는 지금 코드에서 **문법적으로 아예 등록조차 안 되도록** 막혀 있어서, 걱정하신 문제는 발생하지 않습니다.

---

## 질문

응 알았어

---

## 답변

네, 알겠습니다. 추가로 확인하거나 진행할 게 있으면 말씀해주세요.

---

## 질문

<pasted_content id="06e6">
net.dstone.ai.runtime.workflow.WorkFlowExecutor.decideTransition(WorkFlowDefinition, StepDefinition, boolean, String)
메소드 안에서 하단 
int currentIndex = this.indexOf(workflow.steps(), step.id()); 이하 소스코드는 어떤 케이스에서 타게되는가?
</pasted_content id="06e6">

---

## 답변

`int currentIndex = this.indexOf(...)` 이후 코드는 딱 아래 조건을 **전부 만족할 때만** 실행됩니다.

```java
String nextId = success ? step.onSuccess() : step.onFailure();
if (nextId == null) { ... return; }                                   // ① 여기 안 걸려야 함
if (Constants.WorkFlow.SUCCESS_SENTINEL.equals(nextId)) { ... return; } // ② 여기 안 걸려야 함
if (Constants.WorkFlow.FAIL_SENTINEL.equals(nextId)) { ... return; }    // ③ 여기 안 걸려야 함
int currentIndex = this.indexOf(workflow.steps(), step.id());          // ← 여기 도달
```

즉 **"성공했으면 onSuccess에, 실패했으면 onFailure에, `SUCCESS`/`FAIL` 예약어가 아닌 실제 다른 스텝의 id가 명시적으로 적혀 있는 경우"** 에만 여기까지 옵니다. 구체적으로는 4가지 케이스로 나뉩니다.

| 케이스 | 예시 | 결과 |
|---|---|---|
| ① 뒤로 되돌아가는 재시도 루프 | `validate` 실패 → `onFailure: convert` (convert가 validate보다 앞) | `nextIndex ≤ currentIndex` → `LOOP` |
| ② 자기 자신을 다시 가리킴 | `onFailure: (자기 id)` | `nextIndex == currentIndex` → 이것도 `≤`라서 `LOOP` |
| ③ 목록상 뒤쪽으로 건너뛰는 명시적 분기 | `onSuccess`에 중간 스텝들을 건너뛰고 더 뒤의 스텝 id 지정 | `nextIndex > currentIndex` → `NEXT_STEP` |
| ④ 오타/존재하지 않는 id | `onSuccess: step99` 같은데 그런 id가 없음 | `nextIndex < 0` → `IllegalStateException` 던짐 |

반대로 여기 안 오는 경우는: `onSuccess`/`onFailure`를 아예 안 적어서 "그냥 목록상 다음 스텝으로" 넘어가는 가장 흔한 순차 실행(①번 null 분기), 그리고 `"SUCCESS"`/`"FAIL"` 예약어로 그 자리에서 즉시 종료시키는 경우입니다.

---

**그런데 이번에 다시 코드를 보다가 예전에 제가 문서에 잘못 적어둔 부분을 하나 발견했습니다.** 위 표의 ④번(오타로 인한 `IllegalStateException`)이 던져지는 지점 — `decideTransition()` 호출 — 은 `run()` 메서드에서 아래처럼 **`try/catch` 블록 바깥**에 있습니다.

```java
try {
    groupResult = group.size() > 1 ? this.runGroup(group, current) : this.runOne(step, current);
} catch (Exception e) {
    return this.persistFailed(current, "...");   // 여기서만 잡힘
}
...
StepFlow transition = this.decideTransition(workflow, lastOfGroup, groupResult.success(), groupResult.combinedText());  // ← try 밖!
```

즉 `onSuccess`/`onFailure`에 오타로 존재하지 않는 스텝 id를 적으면, 그 예외가 **`run()` 밖으로 그대로 던져집니다** — `FAILED` 상태로 DB에 저장되지 않고 그냥 예외가 터집니다. 제가 지난번 만들어드린 `docs/temp/workflow-executor-run-process.md`의 10번 표에 "⑥ 존재하지 않는 step id → FAILED로 흡수됨"이라고 적어뒀는데, 그건 제가 잘못 적은 것입니다(실제로는 FAILED로 안 남고 예외가 그대로 튐).

이거, ①번 케이스(스텝 실행 중 예외)와 동일하게 `FAILED`로 곱게 저장되도록 `decideTransition()` 호출도 try/catch로 감싸고, 문서도 같이 고쳐드릴까요?

---

## 질문

<pasted_content id="06e6">
먼저 소스를 수정했으니 다시 파악을 해줘.
그리고 이 메소드가 여전히 파악이 힘든게...
net.dstone.ai.runtime.workflow.WorkFlowExecutor.decideTransition(WorkFlowDefinition, StepDefinition, boolean, String)
if~else 케이스에서 벗어나는 빈구석이 존재할 수도 있을 것 같은 느낌이라서... 놓치는게 있지 않을까 하는 염려가 들어.
그래서 로직을 if~else 로 모든 부분을 커버할 수 있도록 수정해 줬으면 해. 중첩 if~else 를 사용하더라도 if~else로 정리해줘.
if 만 별도로 사용하지 말고.
또한 방금 언급한 보완건 보완수정해주고.
</pasted_content id="06e6">

---

## 답변

현재 파일 내용을 확인했습니다. `decideTransition()`을 "빈 틈 없는 if~else 사슬"로 재정리하고, 지난번 발견한 보완건(전이 계산 중 예외가 `run()` 밖으로 새나가는 문제)도 함께 고치겠습니다.

---

## 답변

이제 `run()`에서 `decideTransition()` 호출을 try/catch로 감싸 예외가 새나가지 않도록 고치겠습니다.

---

## 답변

컴파일 확인 후, 오타 케이스로 실제 예외가 이제 FAILED로 잘 흡수되는지 검증하겠습니다.

---

## 답변

모든 9가지 케이스가 리팩터링 전과 동일하게 동작함을 확인했습니다(로직 변경 없이 구조만 재정리). 이제 `docs/temp/workflow-executor-run-process.md`도 이번에 고친 내용에 맞춰 정확하게 업데이트하겠습니다.

---

## 답변

Now I'll update 5-7 to reflect the try/catch wrapping and the if/else rewrite.

---

## 답변

Let's update section 6's code block and numbered list to match the actual (now if/else-only) implementation, and fix the "decideTransition ①/④번 규칙" references in section 10.

---

## 답변

마지막으로 전체 컴파일과 오탈자(mojibake) 여부를 재확인하겠습니다.

---

## 답변

**완료했습니다.** 두 가지 모두 반영했습니다.

## 1. `decideTransition()` — 빈틈없는 if~else 사슬로 재정리

기존에는 `if (조건) { return ...; }` 형태의 **독립적인 조기 리턴들이 연달아 있는 구조**였습니다(각 if가 자기 할 일만 하고 다음 줄로 넘어가는 식이라, "혹시 여기 걸리는 것도 없이 그냥 지나가는 경우가 있나?"를 눈으로 확인하기 어려웠습니다). 이걸 **하나의 if~else if~else 사슬**로 바꿨고, 그 안의 중첩 분기(`!success` 여부, `sequentialNextId` 유무, `nextIndex < 0` 여부, `nextIndex <= currentIndex` 여부)도 전부 if~else 쌍으로만 구성했습니다. 결과적으로 모든 가지의 끝이 `return` 아니면 `throw`이고, "else 없이 끝나는 if"가 하나도 없습니다 — 구조만 봐도 빠짐없이 커버된다는 게 보입니다.

리플렉션으로 9가지 대표 케이스(순차진행/마지막스텝종료/실패종료/SUCCESS·FAIL예약어/루프/스킵분기/자기루프/오타id)를 직접 호출해서 **리팩터링 전과 정확히 동일한 결과**가 나오는 것도 확인했습니다 — 로직은 그대로, 구조만 바꾼 순수 리팩터링입니다.

## 2. 지난번 발견한 보완건 — `decideTransition()` 예외가 새나가던 문제 수정

`run()`에서 `decideTransition()` 호출을 전용 `try/catch`로 감쌌습니다. 이제 `onSuccess`/`onFailure`에 오타로 존재하지 않는 step id를 적어두면, 예외가 `run()` 밖으로 새나가지 않고 **스텝 실행 예외와 똑같이 `FAILED`로 곱게 저장된 뒤 리턴**됩니다.

## 3. 문서 정정

`docs/temp/workflow-executor-run-process.md`에서 예전에 잘못 적어뒀던 "오타 id → ②경로(스텝 실행 예외)로 흡수" 설명을 "별도의 decideTransition 전용 catch로 흡수"로 정정하고, 6절의 코드/설명도 새 if~else 구조와 `[경우 1]~[경우 4]` 라벨에 맞춰 다시 썼습니다.

---

## 질문

이런 오류 발생해 

<pasted_content id="06e6">
Caused by: java.lang.NullPointerException: Cannot invoke "net.dstone.ai.common.loader.YamlDefinitionLoader$AgentFile.agent()" because "file" is null
    at net.dstone.ai.common.loader.YamlDefinitionLoader.loadAgents(YamlDefinitionLoader.java:79)
    at net.dstone.ai.common.registry.AgentRegistry.load(AgentRegistry.java:33)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
    at java.base/java.lang.reflect.Method.invoke(Method.java:580)
    at org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor$LifecycleMethod.invoke(InitDestroyAnnotationBeanPostProcessor.java:453)
    at org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor$LifecycleMetadata.invokeInitMethods(InitDestroyAnnotationBeanPostProcessor.java:397)
    at org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor.postProcessBeforeInitialization(InitDestroyAnnotationBeanPostProcessor.java:217)
    ... 33 more
</pasted_content id="06e6">

---

## 답변

원인을 찾았습니다. `src/main/resources/agents/sql-conversion-agent.yml` 파일이 **0바이트(빈 파일)**로 되어 있어서, YAML 파서가 빈 문서를 읽으면 `null`을 반환하고 그게 `AgentFile`로 바인딩되면서 `file` 자체가 `null`이 되어 NPE가 난 것입니다. 이 파일이 원래 어떤 내용이었는지 git 이력을 확인하겠습니다.

---

## 답변

This looks complete and well-formed. Let's restore this last known-good content to the currently-empty file.

---

## 답변

다른 YAML 리소스 폴더에도 같은 문제(빈 파일)가 있는지 한 번 더 점검하겠습니다.

---

## 답변

다른 빈 파일은 없습니다. `git status`로 최종 변경사항도 확인하겠습니다.

---

## 답변

**원인과 조치 완료했습니다.**

## 원인
`src/main/resources/agents/sql-conversion-agent.yml` 파일이 **0바이트(빈 파일)**로 되어 있었습니다. `YamlDefinitionLoader.readAs()`는 내부적으로 SnakeYAML로 파일을 읽는데, 빈 문서는 `null`을 반환합니다. 그 `null`이 그대로 `AgentFile` 타입으로 바인딩되면서 `file` 변수 자체가 `null`이 되고, 바로 다음 줄의 `file.agent()` 호출에서 NPE가 난 것입니다(`YamlDefinitionLoader.java:79`, `if (file.agent() == null)`).

git 이력을 보니 이 파일은 원래 9198바이트짜리 정상 YAML(오라클→PostgreSQL SQL 변환 규칙 22개가 담긴 `sql-conversion-agent` Agent 정의)이었는데, 최근 커밋에서 120줄이 통째로 삭제되어 빈 파일로 남아 있었습니다 — 이번 세션에서 저희가 수정한 파일 목록에는 없던 파일이라, 다른 경로(다른 편집 중 실수 저장 등)로 비워진 것으로 보입니다.

## 조치
`0eab2bf` 커밋(가장 최근의 정상 버전 — `toolsEnabled: true`, `ragEnabled: false`) 시점의 내용을 그대로 복원했습니다.

## 검증
- `mvn compile` 통과
- `YamlDefinitionLoader.loadAgents()`를 직접 호출해서 5개 Agent(`general-chat`, `sql-analysis-agent`, `sql-conversion-agent`, `sql-conversion-report`, `sql-fix-agent`) 전부 정상 로딩되는 것 확인
- `agents/`, `workflows/`, `mcp/` 폴더 전체를 스캔해서 다른 빈 YAML 파일은 없는 것도 확인

이제 서버를 다시 기동해보시면 이 NPE는 해결되어 있을 것입니다.
