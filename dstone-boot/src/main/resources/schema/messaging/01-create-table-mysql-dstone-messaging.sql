
/**********************************************
사가(Saga) 오케스트레이션용 테이블. 이 SQL을 사용하는 모듈의 대상 스키마에 직접 실행할 것.
**********************************************/

CREATE TABLE IF NOT EXISTS TB_SAGA_INSTANCE (
  SAGA_ID       VARCHAR(64)   NOT NULL,
  SAGA_TYPE     VARCHAR(100)  NOT NULL,
  STATUS        VARCHAR(20)   NOT NULL,
  CURRENT_STEP  VARCHAR(100),
  CREATED_DT    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UPDATED_DT    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (SAGA_ID)
) ;

/**********************************************
사가 스텝 실행 이력 테이블. 이 SQL을 사용하는 모듈의 대상 스키마에 직접 실행할 것.
**********************************************/

CREATE TABLE IF NOT EXISTS TB_SAGA_STEP_HISTORY (
  ID                     BIGINT        NOT NULL AUTO_INCREMENT,
  SAGA_ID                VARCHAR(64)   NOT NULL,
  STEP_NAME               VARCHAR(100)  NOT NULL,
  RESULT                  VARCHAR(20)   NOT NULL,
  ERROR_MSG               VARCHAR(1000),
  PAYLOAD                 TEXT,
  -- 보상(compensate) 처리 결과. NULL=아직 보상 안됨, SUCCESS/FAILED=보상 시도 결과(SagaOrchestrator.compensate() 참고)
  COMPENSATE_RESULT       VARCHAR(20),
  COMPENSATE_ERROR_MSG    VARCHAR(1000),
  COMPENSATED_DT          DATETIME,
  CREATED_DT              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (ID),
  KEY IX_SAGA_STEP_SAGA_ID (SAGA_ID),
  -- 동일 사가의 동일 스텝은 한 번만 SUCCESS/FAILED로 기록되어야 한다(Kafka at-least-once 재전달로 인한
  -- 중복 실행 시 SagaOrchestrator.runStep()의 existsSuccessStep() 사전 체크를 통과하더라도 이 제약이
  -- 최종 안전망 역할을 한다 — 위반 시 DuplicateKeyException을 SagaOrchestrator가 잡아 무시한다)
  UNIQUE KEY UX_SAGA_STEP (SAGA_ID, STEP_NAME)
) ;

/**********************************************
아웃박스 패턴용 테이블. 이 SQL을 사용하는 모듈(dstone-boot, dstone-batch 등)의 대상 스키마에 직접 실행할 것.
(Spring Batch 메타테이블과 동일하게 initialize-schema 자동생성 대상이 아니라 수동 생성)
**********************************************/

CREATE TABLE IF NOT EXISTS TB_OUTBOX_MESSAGE (
  ID              BIGINT        NOT NULL AUTO_INCREMENT,
  TOPIC           VARCHAR(200)  NOT NULL,
  MSG_KEY         VARCHAR(200),
  PAYLOAD         TEXT          NOT NULL,
  -- PENDING(발행대기) -> SENDING(릴레이가 클레임해서 발행 시도중) -> SENT(발행성공) / FAILED(재시도한도초과)
  -- SENDING인 채로 DISPATCHED_DT가 오래된 행은 OutboxRelay.requeueStale()이 PENDING으로 되돌린다
  -- (릴레이가 Kafka send 성공 후 markSent() 반영 전에 죽은 경우에 대한 복구).
  STATUS          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  RETRY_CNT       INT           NOT NULL DEFAULT 0,
  ERROR_MSG       VARCHAR(1000),
  -- claimPending() 호출 시 발급되는 토큰. 같은 호출에서 SENDING으로 전환한 행만 정확히 골라내기 위함
  -- (여러 OutboxRelay 인스턴스가 동시에 폴링해도 서로 다른 행을 가져가게 하는 용도).
  DISPATCH_TOKEN  VARCHAR(64),
  DISPATCHED_DT   DATETIME,
  CREATED_DT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  SENT_DT         DATETIME,
  PRIMARY KEY (ID),
  KEY IX_OUTBOX_STATUS (STATUS, ID),
  KEY IX_OUTBOX_DISPATCH (STATUS, DISPATCHED_DT)
) ;
