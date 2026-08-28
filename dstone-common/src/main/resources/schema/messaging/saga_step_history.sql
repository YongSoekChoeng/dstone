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
