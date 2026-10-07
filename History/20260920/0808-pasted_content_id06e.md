# pasted_content_id06e

> 생성일시: 2026-09-20 08:08

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
