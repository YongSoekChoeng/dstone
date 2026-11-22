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

/**********************************************
ai_management_* (사용자 관리, 2026-11-22)

AI 화면(콘솔)의 로그인과 메뉴 권한. 규칙은 dstone-common 의 ConsoleManagementService, SQL 은 dstone-common 의 sqlmap/console/ConsoleManagementDao.xml.
  사용자 ─(그룹 소속)→ 사용자그룹 ─(권한 부여)→ 권한 ─(메뉴 연결)→ 메뉴
  메뉴는 "모두에게 보임(ALL_USERS_YN)"이거나 사용자의 그룹이 가진 권한에 연결되어 있으면 보인다. ADMIN_YN 권한을 가진 사람은 관리자다.
  비밀번호는 PBKDF2 해시만 저장한다(PasswordHasher). 5번 연달아 틀리면 잠기고, 관리자가 초기화하면 풀린다.
기본 데이터(관리자 계정, 그룹, 권한, 메뉴)는 03-create-data-postgresql-*.sql 에 있다.
**********************************************/
CREATE TABLE IF NOT EXISTS ai_management_user (
    USER_ID           VARCHAR(50)  PRIMARY KEY,
    USER_NAME         VARCHAR(100) NOT NULL,
    PASSWORD_HASH     VARCHAR(200) NOT NULL,
    USE_YN            CHAR(1)      NOT NULL DEFAULT 'Y',
    PW_CHANGE_YN      CHAR(1)      NOT NULL DEFAULT 'Y',
    LOGIN_FAIL_COUNT  INT          NOT NULL DEFAULT 0,
    LAST_LOGIN_AT     TIMESTAMPTZ,
    CREATED_AT        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CREATED_BY        VARCHAR(50),
    UPDATED_AT        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UPDATED_BY        VARCHAR(50)
);
CREATE TABLE IF NOT EXISTS ai_management_group (
    GROUP_ID          VARCHAR(50)  PRIMARY KEY,
    GROUP_NAME        VARCHAR(100) NOT NULL,
    DESCRIPTION       VARCHAR(500),
    SORT_ORDER        INT          NOT NULL DEFAULT 0,
    CREATED_AT        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UPDATED_AT        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS ai_management_group_user (
    GROUP_ID          VARCHAR(50)  NOT NULL REFERENCES ai_management_group(GROUP_ID) ON DELETE CASCADE,
    USER_ID           VARCHAR(50)  NOT NULL REFERENCES ai_management_user(USER_ID) ON DELETE CASCADE,
    PRIMARY KEY (GROUP_ID, USER_ID)
);
CREATE TABLE IF NOT EXISTS ai_management_auth (
    AUTH_ID           VARCHAR(50)  PRIMARY KEY,
    AUTH_NAME         VARCHAR(100) NOT NULL,
    DESCRIPTION       VARCHAR(500),
    ADMIN_YN          CHAR(1)      NOT NULL DEFAULT 'N',
    SORT_ORDER        INT          NOT NULL DEFAULT 0,
    CREATED_AT        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UPDATED_AT        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS ai_management_group_auth (
    GROUP_ID          VARCHAR(50)  NOT NULL REFERENCES ai_management_group(GROUP_ID) ON DELETE CASCADE,
    AUTH_ID           VARCHAR(50)  NOT NULL REFERENCES ai_management_auth(AUTH_ID) ON DELETE CASCADE,
    PRIMARY KEY (GROUP_ID, AUTH_ID)
);
CREATE TABLE IF NOT EXISTS ai_management_menu (
    MENU_ID           VARCHAR(50)  PRIMARY KEY,
    PARENT_ID         VARCHAR(50),
    MENU_NAME         VARCHAR(100) NOT NULL,
    URL               VARCHAR(300),
    ICON              VARCHAR(30),
    SORT_ORDER        INT          NOT NULL DEFAULT 0,
    USE_YN            CHAR(1)      NOT NULL DEFAULT 'Y',
    ALL_USERS_YN      CHAR(1)      NOT NULL DEFAULT 'N',
    CREATED_AT        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UPDATED_AT        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS ai_management_auth_menu (
    AUTH_ID           VARCHAR(50)  NOT NULL REFERENCES ai_management_auth(AUTH_ID) ON DELETE CASCADE,
    MENU_ID           VARCHAR(50)  NOT NULL REFERENCES ai_management_menu(MENU_ID) ON DELETE CASCADE,
    PRIMARY KEY (AUTH_ID, MENU_ID)
);

/**********************************************
ai_management_project / ai_management_project_db (프로젝트 관리, 2026-11-22)

작업 대상 프로젝트. 화면은 관리자 > 프로젝트관리(/ai/admin/management/project.html), API 는 /api/ai/projects.
프로젝트 DB 는 여러 개 둘 수 있고, DB Tool(dbQuery 등)이 "프로젝트ID.DB이름" 별칭으로 접속한다(tools.db.DbQueryTool).
비밀번호는 EncUtil 로 암호화해 저장하고 API 는 어떤 경우에도 돌려주지 않는다(****). 계정은 읽기 권한만 있는 것을 쓴다.
**********************************************/
CREATE TABLE IF NOT EXISTS ai_management_project (
    PROJECT_ID        VARCHAR(50)   PRIMARY KEY,
    PROJECT_NAME      VARCHAR(200)  NOT NULL,
    DESCRIPTION       TEXT,
    SOURCE_DIR        VARCHAR(1000),
    SCM_TYPE          VARCHAR(20),
    SCM_URL           VARCHAR(1000),
    USE_YN            CHAR(1)       NOT NULL DEFAULT 'Y',
    CREATED_AT        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CREATED_BY        VARCHAR(50),
    UPDATED_AT        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UPDATED_BY        VARCHAR(50)
);
CREATE TABLE IF NOT EXISTS ai_management_project_db (
    PROJECT_ID        VARCHAR(50)   NOT NULL REFERENCES ai_management_project(PROJECT_ID) ON DELETE CASCADE,
    DB_SEQ            INT           NOT NULL,
    DB_NAME           VARCHAR(50)   NOT NULL,
    JDBC_URL          VARCHAR(1000) NOT NULL,
    USERNAME          VARCHAR(200),
    PASSWORD_ENC      TEXT,
    SCHEMA_NAME       VARCHAR(200),
    DESCRIPTION       VARCHAR(500),
    SORT_ORDER        INT           NOT NULL DEFAULT 0,
    CREATED_AT        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UPDATED_AT        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (PROJECT_ID, DB_SEQ),
    UNIQUE (PROJECT_ID, DB_NAME)
);

/**********************************************
AI_EVAL_RUN

Agent 평가 실행 1건 = 1행(2026-11-22). 평가 사례(conf/eval/**/*.eval.yml) 하나를 Agent에게 한 번 풀게 한 결과다.
runtime.eval.EvalRunner가 대기열에 넣을 때 QUEUED로 쓰고, 돌면서 RUNNING, 끝나면 DONE(Agent가 답함) 또는 FAILED(Agent 호출 실패)로 바꾼다.
같은 요청으로 여러 번 돌린 것은 BATCH_ID가 같다(반복 측정). 정의·모델을 바꾸기 전후를 AGENT_VERSION, MODEL로 비교한다.
채점 결과(CHECKS_JSON)와 Agent 답(ANSWER_JSON)은 화면이 그대로 보여 준다. 작업 폴더(WORK_DIR)에는 Agent가 만든 파일이 남는다.
기존 DB에는 이 절만 실행하면 된다(IF NOT EXISTS).
**********************************************/
CREATE TABLE IF NOT EXISTS AI_EVAL_RUN (
    RUN_ID              VARCHAR(36) PRIMARY KEY,
    BATCH_ID            VARCHAR(36) NOT NULL,
    CASE_ID             VARCHAR(100) NOT NULL,
    AGENT_ID            VARCHAR(100) NOT NULL,
    AGENT_VERSION       VARCHAR(30),
    MODEL               VARCHAR(200),
    PARAMS_JSON         JSONB NOT NULL DEFAULT '{}',
    STATUS              VARCHAR(20) NOT NULL,      -- QUEUED/RUNNING/DONE/FAILED
    PASSED              INT,
    TOTAL               INT,
    CHECKS_JSON         JSONB,
    ANSWER_JSON         JSONB,
    FAILURE             TEXT,
    DURATION_MS         BIGINT,
    ROUNDS              INT,
    TOOL_CALLS          INT,
    TOOLS_JSON          JSONB,
    PROMPT_TOKENS       BIGINT,
    COMPLETION_TOKENS   BIGINT,
    WORK_DIR            VARCHAR(1000),
    REQUESTED_BY        VARCHAR(100),
    OWNER_INSTANCE      VARCHAR(100),
    CREATED_AT          TIMESTAMPTZ NOT NULL DEFAULT now(),
    STARTED_AT          TIMESTAMPTZ,
    FINISHED_AT         TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS IDX_AI_EVAL_RUN_CASE ON AI_EVAL_RUN(CASE_ID, CREATED_AT DESC);
CREATE INDEX IF NOT EXISTS IDX_AI_EVAL_RUN_CREATED ON AI_EVAL_RUN(CREATED_AT DESC, RUN_ID);

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

COMMENT ON TABLE  AI_EVAL_RUN                    IS 'Agent 평가 실행 1건 = 1행. 평가 사례(conf/eval)를 Agent에게 한 번 풀게 한 결과와 채점';
COMMENT ON COLUMN AI_EVAL_RUN.RUN_ID             IS '평가 실행 ID';
COMMENT ON COLUMN AI_EVAL_RUN.BATCH_ID           IS '같은 요청으로 여러 번 돌린 묶음 ID(반복 측정)';
COMMENT ON COLUMN AI_EVAL_RUN.CASE_ID            IS '평가 사례 id(*.eval.yml의 id)';
COMMENT ON COLUMN AI_EVAL_RUN.AGENT_ID           IS '푼 Agent id';
COMMENT ON COLUMN AI_EVAL_RUN.AGENT_VERSION      IS '그때 Agent 정의의 version';
COMMENT ON COLUMN AI_EVAL_RUN.MODEL              IS '쓴 모델(요청에서 바꿨으면 그 값, 아니면 Agent·provider 기본)';
COMMENT ON COLUMN AI_EVAL_RUN.PARAMS_JSON        IS '사례 params에 넣은 값';
COMMENT ON COLUMN AI_EVAL_RUN.STATUS             IS '상태: QUEUED/RUNNING/DONE(Agent가 답함)/FAILED(Agent 호출 실패·준비 실패)';
COMMENT ON COLUMN AI_EVAL_RUN.PASSED             IS '통과한 채점 항목 수';
COMMENT ON COLUMN AI_EVAL_RUN.TOTAL              IS '채점 항목 수';
COMMENT ON COLUMN AI_EVAL_RUN.CHECKS_JSON        IS '채점 항목별 결과 [{name, kind, ok, detail}]';
COMMENT ON COLUMN AI_EVAL_RUN.ANSWER_JSON        IS 'Agent 답(앞부분만)';
COMMENT ON COLUMN AI_EVAL_RUN.FAILURE            IS '실패 사유';
COMMENT ON COLUMN AI_EVAL_RUN.DURATION_MS        IS 'Agent 호출에 걸린 시간(밀리초)';
COMMENT ON COLUMN AI_EVAL_RUN.ROUNDS             IS 'LLM을 부른 횟수(재시도 포함)';
COMMENT ON COLUMN AI_EVAL_RUN.TOOL_CALLS         IS 'Tool 요청 수 합';
COMMENT ON COLUMN AI_EVAL_RUN.TOOLS_JSON         IS 'Tool 이름별 요청 수';
COMMENT ON COLUMN AI_EVAL_RUN.PROMPT_TOKENS      IS '입력 토큰 합(회차마다 보낸 대화 전체를 더한 값)';
COMMENT ON COLUMN AI_EVAL_RUN.COMPLETION_TOKENS  IS '출력 토큰 합';
COMMENT ON COLUMN AI_EVAL_RUN.WORK_DIR           IS '이 실행의 작업 폴더(Agent가 만든 파일이 남는다)';
COMMENT ON COLUMN AI_EVAL_RUN.REQUESTED_BY       IS '평가를 시작한 사용자';
COMMENT ON COLUMN AI_EVAL_RUN.OWNER_INSTANCE     IS '이 평가를 돌린 엔진 인스턴스 id. 그 인스턴스가 다시 뜨면 끝나지 않은 행을 FAILED로 정리한다';
COMMENT ON COLUMN AI_EVAL_RUN.CREATED_AT         IS '요청 시각';
COMMENT ON COLUMN AI_EVAL_RUN.STARTED_AT         IS '시작 시각';
COMMENT ON COLUMN AI_EVAL_RUN.FINISHED_AT        IS '끝난 시각';

COMMENT ON TABLE  ai_management_user             IS '콘솔 사용자. 비밀번호는 PBKDF2 해시만 저장';
COMMENT ON COLUMN ai_management_user.USER_ID     IS '아이디';
COMMENT ON COLUMN ai_management_user.USER_NAME   IS '이름';
COMMENT ON COLUMN ai_management_user.PASSWORD_HASH IS '비밀번호 해시(pbkdf2$반복$소금$해시)';
COMMENT ON COLUMN ai_management_user.USE_YN      IS '사용 여부(N이면 로그인 불가)';
COMMENT ON COLUMN ai_management_user.PW_CHANGE_YN IS '다음 로그인 때 비밀번호를 바꿔야 하는지(관리자가 만들거나 초기화하면 Y)';
COMMENT ON COLUMN ai_management_user.LOGIN_FAIL_COUNT IS '연달아 틀린 횟수(5번이면 잠김)';
COMMENT ON COLUMN ai_management_user.LAST_LOGIN_AT IS '마지막 로그인 시각';
COMMENT ON TABLE  ai_management_group            IS '사용자그룹';
COMMENT ON TABLE  ai_management_group_user       IS '사용자그룹에 속한 사용자';
COMMENT ON TABLE  ai_management_auth             IS '권한. 메뉴를 묶어 그룹에 준다';
COMMENT ON COLUMN ai_management_auth.ADMIN_YN    IS '관리자 권한인지(관리 화면·API, 남의 실행 조작)';
COMMENT ON TABLE  ai_management_group_auth       IS '사용자그룹이 가진 권한';
COMMENT ON TABLE  ai_management_menu             IS '왼쪽 메뉴. URL 이 없으면 묶음 제목';
COMMENT ON COLUMN ai_management_menu.PARENT_ID   IS '상위 메뉴(묶음) ID';
COMMENT ON COLUMN ai_management_menu.ICON        IS '아이콘 이름(assets/js/console.js 의 ICONS)';
COMMENT ON COLUMN ai_management_menu.ALL_USERS_YN IS '로그인한 모두에게 보이는지';
COMMENT ON TABLE  ai_management_auth_menu        IS '권한이 여는 메뉴';

COMMENT ON TABLE  ai_management_project              IS '작업 대상 프로젝트';
COMMENT ON COLUMN ai_management_project.PROJECT_ID   IS '프로젝트ID(요청서의 프로젝트ID, DB 별칭 앞부분)';
COMMENT ON COLUMN ai_management_project.PROJECT_NAME IS '프로젝트명';
COMMENT ON COLUMN ai_management_project.DESCRIPTION  IS '프로젝트 설명';
COMMENT ON COLUMN ai_management_project.SOURCE_DIR   IS '소스 디렉토리(절대경로)';
COMMENT ON COLUMN ai_management_project.SCM_TYPE     IS '형상관리 종류(GIT/SVN/NONE …)';
COMMENT ON COLUMN ai_management_project.SCM_URL      IS '형상관리 URL';
COMMENT ON COLUMN ai_management_project.USE_YN       IS '사용 여부';
COMMENT ON TABLE  ai_management_project_db           IS '프로젝트가 쓰는 DB 접속 정보(여러 개). DB Tool 별칭은 프로젝트ID.DB이름';
COMMENT ON COLUMN ai_management_project_db.DB_SEQ    IS '프로젝트 안의 번호';
COMMENT ON COLUMN ai_management_project_db.DB_NAME   IS 'DB 이름(영문·숫자·_-). 별칭의 뒷부분';
COMMENT ON COLUMN ai_management_project_db.JDBC_URL  IS 'JDBC URL';
COMMENT ON COLUMN ai_management_project_db.USERNAME  IS '접속 계정(읽기 전용 권장)';
COMMENT ON COLUMN ai_management_project_db.PASSWORD_ENC IS 'EncUtil 로 암호화한 비밀번호. API 는 돌려주지 않는다';
COMMENT ON COLUMN ai_management_project_db.SCHEMA_NAME IS '스키마';
