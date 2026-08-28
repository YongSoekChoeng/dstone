
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
  ID          BIGINT        NOT NULL AUTO_INCREMENT,
  SAGA_ID     VARCHAR(64)   NOT NULL,
  STEP_NAME   VARCHAR(100)  NOT NULL,
  RESULT      VARCHAR(20)   NOT NULL,
  ERROR_MSG   VARCHAR(1000),
  CREATED_DT  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (ID),
  KEY IX_SAGA_STEP_SAGA_ID (SAGA_ID)
) ;

/**********************************************
아웃박스 패턴용 테이블. 이 SQL을 사용하는 모듈(dstone-boot, dstone-batch 등)의 대상 스키마에 직접 실행할 것.
(Spring Batch 메타테이블과 동일하게 initialize-schema 자동생성 대상이 아니라 수동 생성)
**********************************************/

CREATE TABLE IF NOT EXISTS TB_OUTBOX_MESSAGE (
  ID          BIGINT        NOT NULL AUTO_INCREMENT,
  TOPIC       VARCHAR(200)  NOT NULL,
  MSG_KEY     VARCHAR(200),
  PAYLOAD     TEXT          NOT NULL,
  STATUS      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  RETRY_CNT   INT           NOT NULL DEFAULT 0,
  ERROR_MSG   VARCHAR(1000),
  CREATED_DT  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  SENT_DT     DATETIME,
  PRIMARY KEY (ID),
  KEY IX_OUTBOX_STATUS (STATUS, ID)
) ;
