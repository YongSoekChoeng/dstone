/**********************************************
dstone_ai 데이터베이스 안에서 실행할 것 (01-init-postgresql-dstone-ai.sql로 먼저 데이터베이스를 만든다).

RAG용 vector_store 테이블은 더 이상 여기에 없다. 문서의 저장, 임베딩, 검색은 dstone-knowledge가 맡는다
(dstone-knowledge의 rag_document / rag_chunk / rag_embedding). 예전에 만들어 둔 vector_store 테이블이 남아 있다면
이 모듈은 더 이상 읽지도 쓰지도 않는다. 안에 든 문서가 필요 없다는 것을 확인한 뒤 직접 지우면 된다(DROP TABLE vector_store).
**********************************************/

/**********************************************
AI_WORKFLOW_EXECUTION / AI_WORKFLOW_EXECUTION_STEP_HISTORY

Workflow 실행 1건의 현재 상태와, 그 실행이 지나온 스텝 하나하나의 기록을 담는다. 이 모듈은 다른 dstone
모듈처럼 스키마를 코드가 자동 생성하지 않는다(spring.jpa.hibernate.ddl-auto 같은 것 없음) - 배포 전에
DBA/운영자가 이 파일을 수동으로 한 번 실행해야 한다.

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
    STATUS              VARCHAR(20) NOT NULL,      -- RUNNING/WAITING_APPROVAL/DONE/FAILED/CANCELLED
    CURRENT_STEP_INDEX  INT NOT NULL DEFAULT 0,
    CONTEXT_JSON        JSONB NOT NULL DEFAULT '{}',
    RESULT_TEXT         TEXT,
    ERROR_MESSAGE       TEXT,
    CREATED_AT          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UPDATED_AT          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IDX_AI_WORKFLOW_EXECUTION_STATUS ON AI_WORKFLOW_EXECUTION(STATUS);

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
  
  