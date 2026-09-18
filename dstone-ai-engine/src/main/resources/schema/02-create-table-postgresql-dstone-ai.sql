/**********************************************
dstone-ai-engine RAG(Phase 2) VectorStore 테이블 (pgvector)
dstone_ai 데이터베이스 안에서 실행할 것 (01-init-postgresql-dstone-ai.sql로 먼저 생성/확장 활성화 필요).

주의: 
이 테이블은 spring.ai.vectorstore.pgvector.initialize-schema: true로 설정되어 있으면 dstone-ai-engine이 최초 기동 시 아래와 동일한 내용을 자동으로 생성한다
(CREATE TABLE/INDEX IF NOT EXISTS라 멱등적이라 이 스크립트를 미리 실행해 둬도 충돌하지 않는다). 
다른 모듈(dstone-boot/dstone-batch/dstone-batchadmin)의 initialize-schema: NEVER 컨벤션과 맞추고 싶다면, 
이 스크립트를 먼저 실행한 뒤 application.yml에서 initialize-schema를 false로 바꾸면 된다.

컬럼/인덱스 정의는 실제 기동 중인 인스턴스에서 \d vector_store로 확인한 값 그대로다.
embedding 차원(1024)은 spring.ai.ollama.embedding.model(bge-m3)의 출력 차원과 반드시 일치해야 한다 
- 임베딩 모델을 바꾸면 이 값과 spring.ai.vectorstore.pgvector.dimensions를 함께 바꾸고 테이블을 재생성해야 한다(차원이 다른 벡터는 기존 테이블에 섞어 넣을 수 없음).
**********************************************/

CREATE TABLE IF NOT EXISTS vector_store (
  id        uuid          NOT NULL DEFAULT uuid_generate_v4(),
  content   text,
  metadata  json,
  embedding vector(1024),
  PRIMARY KEY (id)
);

-- 코사인 유사도 검색용 HNSW 인덱스. 이름(spring_ai_vector_index)은 Spring AI PgVectorStore가
-- 고정으로 쓰는 이름과 동일하게 맞췄다.
CREATE INDEX IF NOT EXISTS spring_ai_vector_index
  ON vector_store USING hnsw (embedding vector_cosine_ops);

  
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
    VARIABLES_JSON      JSONB NOT NULL DEFAULT '{}',
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
  
  