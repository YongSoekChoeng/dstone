# netdstonecommonmessa

> 생성일시: 2026-08-29 16:45

## 질문

net.dstone.common.messaging.saga.SagaOrchestrator.start() 가 시작되서 호출되는 모든 흐름을 자세하게 알려줘.

---

## 답변

Let me read through the core saga files and the sample implementation to trace the full flow.

---

## 답변

Now let me look at the outbox appender/relay and the step handler implementations to complete the picture.

---

## 답변

## `SagaOrchestrator.start()` 호출 시 전체 흐름

샘플(`OrderSagaController` → 주문 사가: 재고차감→결제→주문확정)을 기준으로, 호출부터 사가 종결/보상까지 순서대로 정리했습니다.

### 1. 진입점 — `OrderSagaController.start()` (dstone-boot/.../saga/controller/OrderSagaController.java:41)
- HTTP 요청에서 `ORDER_ID/ITEM_ID/QTY/AMOUNT`로 `command` Map을 만들고
- `sagaOrchestrator.start("ORDER", "inventoryReserve", command)` 호출 (아직 Kafka는 전혀 관여하지 않음)

### 2. `SagaOrchestrator.start()` (dstone-common/.../saga/SagaOrchestrator.java:74)
1. `UUID.randomUUID()`로 `sagaId` 생성 — 이후 이 사가의 모든 이벤트를 묶는 상관관계 키(Kafka 파티션 key로도 재사용됨)
2. `saga` Map 구성: `SAGA_ID/SAGA_TYPE=ORDER/STATUS=STARTED/CURRENT_STEP=inventoryReserve`
3. `sagaStore.insert(saga)` → `SagaDao.insert()` (dstone-boot) → MyBatis로 `TB_SAGA_INSTANCE`에 INSERT
4. `runStep(sagaId, "inventoryReserve", command)` 호출 → 아래 3번으로

### 3. `runStep()` — 첫 스텝(inventoryReserve) 실행 (SagaOrchestrator.java:144)
1. `findHandler("inventoryReserve")` — `List<SagaStepHandler>`(Spring이 `@Service` 구현체들을 자동 수집)에서 이름이 일치하는 핸들러 탐색 → `InventoryReserveStepService`
2. `handler.handle(command)` 동기 호출 (같은 JVM, 로컬 호출)
   - `InventoryReserveStepService.handle()`: `QTY >= 100`이면 재고부족으로 예외 발생(데모 실패 조건), 아니면 command를 그대로 반환
3. 성공 시:
   - `result.put("SAGA_ID", sagaId)` 주입
   - `sagaStore.insertStepHistory(...)` → `TB_SAGA_STEP_HISTORY`에 `RESULT=SUCCESS`, `PAYLOAD`(command의 JSON) 기록
   - `sagaStore.updateStatus(sagaId, "STEP_DONE", "inventoryReserve")` → `TB_SAGA_INSTANCE` 갱신
   - `outboxAppender.append("inventoryReserve-reply", sagaId, result)` 호출 → 4번으로
4. 실패 시(예외):
   - `insertStepHistory(...RESULT=FAILED...)` 기록 후 `compensate(sagaId, "inventoryReserve")` 호출 → 8번(보상 흐름)으로 분기하고 종료 (outbox에는 아무것도 쌓이지 않음)

### 4. `OutboxAppenderImpl.append()` (dstone-common/.../outbox/OutboxAppenderImpl.java:32)
- payload(Map)를 JSON 문자열로 직렬화해 `TOPIC/MSG_KEY/PAYLOAD/STATUS=PENDING` 행을 `outboxStore.insert()`로 `TB_OUTBOX_MESSAGE`에 INSERT
- **이 시점까지 Kafka로는 아무것도 전송되지 않음** — DB 쓰기(스텝 이력/상태/아웃박스)가 하나의 로컬 트랜잭션으로 묶여야 Transactional Outbox 패턴의 원자성이 성립

여기까지가 `start()` 호출 스택 내에서 동기적으로 끝나는 부분입니다. 이후는 별도 스레드/컨슈머가 이어받습니다.

### 5. `OutboxRelayScheduler` (dstone-boot) → `OutboxRelay.dispatchPending()` (비동기, 별도 스레드)
- `@Scheduled(fixedDelayString = "${messaging.outbox.relay-interval-ms:1000}")` — 기본 1초마다 `dispatchPending(100)` 호출
- `TB_OUTBOX_MESSAGE`에서 `PENDING` 레코드를 최대 100건 조회 → 각 건마다
  - JSON payload를 Map으로 역직렬화
  - `kafkaTemplate.send(topic, key, payload).get()` — 실제 Kafka 브로커로 동기 전송(ack 대기)
  - 성공 시 `markSent`, 실패 시 `markFailed`(재시도 카운트 증가, 다음 폴링에서 재시도)
- 여기서 실제로 `inventoryReserve-reply` 토픽에 `key=sagaId`, `value=result(JSON)` 메시지가 발행됨

### 6. `OrderSagaReplyListener` — Kafka Consumer가 다음 스텝을 트리거 (dstone-boot/.../saga/listener/OrderSagaReplyListener.java)
- `onInventoryReserved()` (`@KafkaListener(topics="inventoryReserve-reply")`)가 메시지 수신
  - `payload.get("SAGA_ID")` 추출 후 `sagaOrchestrator.proceed(sagaId, "payment", payload)` 호출
- `SagaOrchestrator.proceed()` → 내부적으로 다시 `runStep(sagaId, "payment", payload)` (3번 로직 재실행, 핸들러만 `PaymentStepService`로 바뀜)
  - `AMOUNT >= 1000000`이면 실패 → compensate 분기
  - 성공하면 다시 outbox → relay → `payment-reply` 토픽 발행
- `onPaid()` (`payment-reply` 구독)가 받아서 `proceed(sagaId, "orderConfirm", payload)` → `OrderConfirmStepService.handle()` 실행 → `orderConfirm-reply` 발행
- `onOrderConfirmed()` (`orderConfirm-reply` 구독)가 받아서 **이번엔 proceed가 아니라** `sagaOrchestrator.complete(sagaId, "orderConfirm")` 호출

즉 스텝 순서(inventoryReserve → payment → orderConfirm, 그리고 어디가 마지막인지)는 오케스트레이터가 아니라 **이 리스너가 알고 있음** — 엔진은 스텝 이름으로 핸들러를 찾아 실행할 뿐 순서를 모르는 재사용 가능한 구조.

### 7. `SagaOrchestrator.complete()` (SagaOrchestrator.java:123)
- `sagaStore.updateStatus(sagaId, "COMPLETED", "orderConfirm")`로 `TB_SAGA_INSTANCE` 갱신하고 끝. (이벤트 발행 없음 — 필요하면 호출자가 별도로 outbox에 적재해야 함)

### 8. 실패 시 — `compensate()` (SagaOrchestrator.java:178, 보상/SEC 흐름)
어느 스텝에서든 `handle()`이 예외를 던지면 (재고부족 or 결제한도초과):
1. `updateStatus(sagaId, "COMPENSATING", failedStep)`
2. `sagaStore.findSuccessStepHistory(sagaId)` — 이 사가에서 `RESULT=SUCCESS`로 끝난 스텝들을 **최신순(역순)**으로 조회 (실패한 스텝 자신은 제외)
3. 각 성공 스텝에 대해:
   - `findHandler(stepName)`으로 핸들러 재탐색
   - `TB_SAGA_STEP_HISTORY.PAYLOAD`에 저장돼 있던 JSON을 파싱해 원래 command 복원
   - `handler.compensate(payload)` **동기·로컬 호출** (Kafka/Outbox를 전혀 거치지 않음)
     - 예: 결제 단계에서 실패하면 → 이미 성공한 `inventoryReserve`의 `compensate()`(재고 복원 로그)만 역순으로 호출됨
4. 마지막으로 `updateStatus(sagaId, "FAILED", failedStep)`

### 핵심 설계 포인트 요약
- **인프로세스 동기 실행 + Transactional Outbox**: 각 스텝 실행/DB기록/발행예약은 한 트랜잭션, 실제 Kafka 전송은 별도 스레드(Relay)가 담당해 이중 쓰기 문제 제거
- **`sagaId`가 파티션 키**: 같은 사가의 이벤트는 항상 같은 파티션 → 순서 보장
- **엔진은 스텝 순서를 모름**: 순서/종결 판단은 각 모듈의 `@KafkaListener`(예: `OrderSagaReplyListener`)가 가짐 → 엔진 자체는 사가 종류에 무관하게 재사용 가능
- **보상은 이벤트 기반이 아닌 오케스트레이터 직접 지휘(SEC)**: `compensate()`가 DB의 성공 이력을 읽어 로컬에서 역순 호출
