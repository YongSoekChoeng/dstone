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
