/**********************************************
이미 만들어 둔 dstone_ai 데이터베이스에 "step별 파일 기록" 테이블을 넣을 때 한 번 실행한다.
새로 설치할 때는 02-create-table-postgresql-dstone-ai.sql에 이미 들어 있으므로 필요 없다.
새 버전의 dstone-ai-engine을 띄우기 전에 실행한다. 테이블이 없으면 기록만 실패하고(경고 로그) Workflow는 그대로 돈다.
**********************************************/
CREATE TABLE IF NOT EXISTS AI_WORKFLOW_EXECUTION_FILE (
    EXECUTION_ID   VARCHAR(36) NOT NULL REFERENCES AI_WORKFLOW_EXECUTION(EXECUTION_ID),
    STEP_ID        VARCHAR(100) NOT NULL,
    FILE_PATH      VARCHAR(1000) NOT NULL,
    ACTION         VARCHAR(10) NOT NULL,              -- READ / WRITE
    ACCESS_COUNT   INT NOT NULL DEFAULT 1,
    FIRST_AT       TIMESTAMPTZ NOT NULL DEFAULT now(),
    LAST_AT        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (EXECUTION_ID, STEP_ID, FILE_PATH, ACTION)
);
