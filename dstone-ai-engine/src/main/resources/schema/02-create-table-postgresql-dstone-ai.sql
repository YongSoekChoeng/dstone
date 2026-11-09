/**********************************************
dstone_ai 데이터베이스 안에서 실행할 것 (01-init-postgresql-dstone-ai.sql로 먼저 데이터베이스를 만든다).

RAG용 vector_store 테이블은 더 이상 없다. 문서의 저장, 임베딩, 검색은 dstone-knowledge가 맡는다
(dstone-knowledge의 rag_document / rag_chunk / rag_embedding).
**********************************************/

/**********************************************
AI_WORKFLOW_EXECUTION / AI_WORKFLOW_EXECUTION_STEP_HISTORY

Workflow 실행 1건의 현재 상태와, 그 실행이 지나온 스텝 하나하나의 기록을 담는다. 이 모듈은 다른 dstone
모듈처럼 스키마를 코드가 자동 생성하지 않는다(spring.jpa.hibernate.ddl-auto 같은 것 없음) - 배포 전에
DBA/운영자가 이 파일을 수동으로 한 번 실행해야 한다. 테이블·컬럼 코멘트는 맨 끝 절에 있다
(컬럼을 새로 추가하면 그 절에도 코멘트를 적는다).

- AI_WORKFLOW_EXECUTION: 실행 1건 = 1행. WAITING_APPROVAL 상태로 멈춰 있는 실행을 찾거나(STATUS 인덱스),
  같은 실행을 재개할 때(SESSION_ID로 대화 히스토리 이어가기) 이 테이블만 보면 된다.
- AI_WORKFLOW_EXECUTION_STEP_HISTORY: 실행 하나가 거쳐간 스텝마다 한 행씩 쌓인다(같은 스텝을 재실행하면
  또 한 행이 쌓인다 - 예: validate 실패 후 fix를 거쳐 validate를 다시 통과하면 validate가 2행 남는다).
  실행 목록/상세 조회 API가 이 두 테이블을 그대로 JOIN 없이 각각 조회해서 응답을 만든다.
**********************************************/

CREATE TABLE AI_WORKFLOW_EXECUTION (
    EXECUTION_ID        VARCHAR(36) PRIMARY KEY,
    WORKFLOW_ID         VARCHAR(100) NOT NULL,
    CALLER              VARCHAR(100),
    SESSION_ID          VARCHAR(100) NOT NULL,
    STATUS              VARCHAR(20) NOT NULL,      -- QUEUED/RUNNING/WAITING_APPROVAL/PAUSED/DONE/FAILED/CANCELLED
    CURRENT_STEP_INDEX  INT NOT NULL DEFAULT 0,
    CONTEXT_JSON        JSONB NOT NULL DEFAULT '{}',
    RESULT_TEXT         TEXT,
    ERROR_MESSAGE       TEXT,
    CREATED_AT          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UPDATED_AT          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PAUSE_REQUESTED     BOOLEAN NOT NULL DEFAULT FALSE, -- 사람이 일시정지를 요청함(다음 step 전에 멈춘다)
    REQUESTED_BY        VARCHAR(100),              -- 실행을 시작한 사용자(dstone-boot 로그인 id). 화면의 "내 실행"과 조작 권한에 쓴다
    OWNER_INSTANCE      VARCHAR(100),              -- 이 실행을 돌리는(또는 대기열에 넣은) 엔진 인스턴스 id
    HEARTBEAT_AT        TIMESTAMPTZ                -- OWNER_INSTANCE가 살아 있다고 마지막으로 알린 시각
);
CREATE INDEX IDX_AI_WORKFLOW_EXECUTION_STATUS ON AI_WORKFLOW_EXECUTION(STATUS);
CREATE INDEX IDX_AI_WORKFLOW_EXECUTION_CREATED ON AI_WORKFLOW_EXECUTION(CREATED_AT DESC, EXECUTION_ID);
CREATE INDEX IDX_AI_WORKFLOW_EXECUTION_REQUESTED_BY ON AI_WORKFLOW_EXECUTION(REQUESTED_BY, CREATED_AT DESC);

CREATE TABLE AI_WORKFLOW_EXECUTION_STEP_HISTORY (
    ID                BIGSERIAL PRIMARY KEY,
    EXECUTION_ID      VARCHAR(36) NOT NULL REFERENCES AI_WORKFLOW_EXECUTION(EXECUTION_ID),
    STEP_ID           VARCHAR(100) NOT NULL,
    STEP_TYPE         VARCHAR(20) NOT NULL,
    STEP_REF          VARCHAR(200),
    SUCCESS           BOOLEAN NOT NULL,
    DURATION_MS       BIGINT,
    OUTPUT_SUMMARY    TEXT,
    FAILURE_REASON    TEXT,
    EXECUTED_AT       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IDX_AI_WORKFLOW_EXECUTION_STEP_HISTORY_EXEC ON AI_WORKFLOW_EXECUTION_STEP_HISTORY(EXECUTION_ID);
  
  

/**********************************************
AI_WORKFLOW_EXECUTION_FILE

Workflow 실행 중에 step이 읽고 쓴 파일의 목록이다. 파일 Tool(writeFile, readFile 등)이 성공할 때마다
runtime.workflow.file.WorkFlowFileRecorder가 남긴다. 실행 관리 화면이 step별 문서를 보여 주고 고칠 때 이 목록을 쓴다.
같은 (실행, step, 경로, 동작)은 한 행으로 합치고 횟수와 마지막 시각만 바꾼다(재분석으로 step이 다시 돌아도 행이 늘지 않는다).
파일 내용은 저장하지 않는다. 화면이 열 때마다 파일에서 바로 읽는다.
CREATED는 그 step이 없던 파일을 새로 만들었는지다. 재실행 때 이 실행이 만든 파일만 지우는 데 쓴다
(원래 있던 소스를 고친 파일은 지우지 않는다).
**********************************************/
CREATE TABLE IF NOT EXISTS AI_WORKFLOW_EXECUTION_FILE (
    EXECUTION_ID   VARCHAR(36) NOT NULL REFERENCES AI_WORKFLOW_EXECUTION(EXECUTION_ID),
    STEP_ID        VARCHAR(100) NOT NULL,
    FILE_PATH      VARCHAR(1000) NOT NULL,
    ACTION         VARCHAR(10) NOT NULL,              -- READ / WRITE
    ACCESS_COUNT   INT NOT NULL DEFAULT 1,
    FIRST_AT       TIMESTAMPTZ NOT NULL DEFAULT now(),
    LAST_AT        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CREATED        BOOLEAN,                           -- WRITE일 때 이 step이 새로 만든 파일이면 true(이 열이 생기기 전 기록은 NULL)
    PRIMARY KEY (EXECUTION_ID, STEP_ID, FILE_PATH, ACTION)
);


/**********************************************
테이블 · 컬럼 코멘트
COMMENT ON 은 덮어쓰기라 이 절만 따로 여러 번 실행해도 된다(이미 테이블이 있는 DB 에 코멘트만 달 때).
테이블이나 컬럼을 새로 추가하면 여기에도 코멘트를 함께 적는다.
**********************************************/

COMMENT ON TABLE  AI_WORKFLOW_EXECUTION                      IS 'Workflow 실행 1건의 현재 상태 (실행 1건 = 1행)';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.EXECUTION_ID         IS '실행 ID (UUID)';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.WORKFLOW_ID          IS '실행한 Workflow 정의 ID';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.CALLER               IS '호출자 (X-API-Key 로 확인한 호출 시스템)';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.SESSION_ID           IS '대화 세션 ID. 재개할 때 대화 히스토리를 이어 가는 키';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.STATUS               IS '상태: QUEUED/RUNNING/WAITING_APPROVAL/PAUSED/DONE/FAILED/CANCELLED';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.CURRENT_STEP_INDEX   IS '다음에 실행할(또는 멈춘) step 의 순번';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.CONTEXT_JSON         IS '실행 문맥 (JSON): input, state, 정의 사본 등. 재개·되감기에 쓴다';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.RESULT_TEXT          IS '최종 결과';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.ERROR_MESSAGE        IS '실패했을 때의 오류 메시지';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.CREATED_AT           IS '요청 시각';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.UPDATED_AT           IS '마지막 상태 변경 시각';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.PAUSE_REQUESTED      IS '사람이 일시정지를 요청했는지 (다음 step 전에 멈춘다)';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.REQUESTED_BY         IS '실행을 시작한 사용자 (dstone-boot 로그인 id). 화면의 "내 실행"과 조작 권한에 쓴다';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.OWNER_INSTANCE       IS '이 실행을 돌리는(또는 대기열에 넣은) 엔진 인스턴스 id';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION.HEARTBEAT_AT         IS 'OWNER_INSTANCE 가 살아 있다고 마지막으로 알린 시각. 오래되면 고아 실행으로 정리한다';

COMMENT ON TABLE  AI_WORKFLOW_EXECUTION_STEP_HISTORY                 IS 'Workflow 실행이 거쳐 간 step 기록. 같은 step 을 다시 돌면 행이 또 쌓인다';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_STEP_HISTORY.ID              IS '기록 ID (일련번호)';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_STEP_HISTORY.EXECUTION_ID    IS '실행 ID';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_STEP_HISTORY.STEP_ID         IS 'step id (step00~step99)';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_STEP_HISTORY.STEP_TYPE       IS 'step 종류: AGENT/SUPERVISOR/ROUTER/TOOL/APPROVAL';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_STEP_HISTORY.STEP_REF        IS 'step 이 부른 Agent id 나 Tool 이름';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_STEP_HISTORY.SUCCESS         IS '성공 여부';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_STEP_HISTORY.DURATION_MS     IS '걸린 시간 (밀리초)';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_STEP_HISTORY.OUTPUT_SUMMARY  IS '성공했을 때의 결과 요약';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_STEP_HISTORY.FAILURE_REASON  IS '실패 사유';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_STEP_HISTORY.EXECUTED_AT     IS '실행 시각';

COMMENT ON TABLE  AI_WORKFLOW_EXECUTION_FILE                 IS 'Workflow 실행 중 step 이 읽고 쓴 파일 목록. 같은 (실행, step, 경로, 동작)은 한 행. 파일 내용은 저장하지 않는다';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_FILE.EXECUTION_ID    IS '실행 ID';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_FILE.STEP_ID         IS 'step id';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_FILE.FILE_PATH       IS '파일 경로';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_FILE.ACTION          IS '동작: READ/WRITE';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_FILE.ACCESS_COUNT    IS '접근 횟수';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_FILE.FIRST_AT        IS '처음 접근한 시각';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_FILE.LAST_AT         IS '마지막으로 접근한 시각';
COMMENT ON COLUMN AI_WORKFLOW_EXECUTION_FILE.CREATED         IS 'WRITE 일 때 이 step 이 새로 만든 파일인지. 재실행 때 이 파일만 지운다 (이 열이 생기기 전 기록은 NULL)';
