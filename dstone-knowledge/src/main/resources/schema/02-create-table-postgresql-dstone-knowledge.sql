/**********************************************
dstone-knowledge 테이블 (PostgreSQL + pgvector)
dstone_knowledge 데이터베이스 안에서 앱 계정으로 실행할 것
(01-init-postgresql-dstone-knowledge.sql로 롤/DB/확장을 먼저 만들어야 한다):

  psql -h 127.0.0.1 -U dstone_knowledge -d dstone_knowledge -f 02-create-table-postgresql-dstone-knowledge.sql

이 모듈도 다른 dstone 모듈처럼 스키마를 코드가 자동으로 만들지 않는다. 배포 전에 이 파일을 한 번 실행한다.
모든 문장이 IF NOT EXISTS / OR REPLACE 라서 여러 번 실행해도 된다.
테이블·컬럼 코멘트는 맨 끝 9절에 있다. 컬럼을 새로 추가하면 9절에도 코멘트를 적는다.

## 테이블을 나누는 기준

1. 원천 분석 데이터(analysis_*)와 RAG 파생 데이터(rag_*)를 나눈다.
   rag_* 는 analysis_* 만 있으면 언제든 다시 만들 수 있어야 한다.
2. 그래프(kg_node/kg_edge)는 따로 저장하지 않는다. analysis_* 위에 얹은 VIEW 다.
   같은 내용을 두 군데 저장하면 어긋나기 때문이다. Neo4j 로 옮길 때는 이 VIEW 를 내보내면 된다.

## 리비전(revision)

리비전 하나 = 어느 시점의 소스 전체를 분석한 결과 한 벌.
심볼/관계처럼 분석으로 생기는 행에는 모두 revision_id 가 붙는다.
심볼 ID(symbol_id, method_id, field_id)는 리비전과 상관없이 같은 대상이면 같은 값이다
(프로젝트 + 종류 + 이름/시그니처의 해시). 그래서 두 리비전을 ID 로 바로 비교할 수 있다.

## 분석은 DB 에 쓰면서 진행한다

큰 프로젝트를 메모리에 다 올리면 OutOfMemoryError 가 난다.
그래서 파일 하나를 처리할 때마다 결과를 DB 에 쓰고 메모리에서 버린다.
- analysis_file_pass : 파일마다, 단계(pass)마다 어디까지 했는지 기록. 죽어도 이어서 할 수 있다.
- analysis_reference : 1차 패스에서 모아 둔 "아직 누구를 가리키는지 모르는 참조". 2차 패스가 풀어서 analysis_relation 으로 옮긴다.

## 외래키(FK)

행이 많은 테이블(symbol/method/field/reference/relation/chunk ...)에는 FK 를 걸지 않았다.
대량 저장과 리비전 단위 삭제가 느려지기 때문이다. 정합성은 revision_id 로 묶어 코드에서 관리한다.
**********************************************/

/**********************************************
1. 프로젝트 / 리비전 / 분석 Job
**********************************************/

-- 분석 대상 프로젝트
CREATE TABLE IF NOT EXISTS analysis_project (
    project_id        VARCHAR(100)  PRIMARY KEY,        -- 사용자가 정하는 키 (예: order-system)
    project_name      VARCHAR(200)  NOT NULL,
    repository        VARCHAR(500),                     -- git 저장소 URL (없으면 NULL)
    local_path        VARCHAR(1000),                    -- 소스가 있는 로컬 경로
    branch            VARCHAR(200),
    root_packages     TEXT,                             -- 분석할 루트 패키지들 (쉼표 구분). 이 밖의 코드는 분석하지 않는다
    java_version      VARCHAR(10),                      -- 소스 문법 수준 (예: 1.4, 8, 21). NULL 이면 자동
    source_encoding   VARCHAR(30),                      -- 소스 인코딩 (예: EUC-KR). NULL 이면 파일마다 자동 감지
    description       TEXT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- 클래스패스: 호출이 누구를 가리키는지 풀 때 쓰는 라이브러리 jar 의 위치.
-- jar 파일이나 jar 가 든 폴더를 쉼표/줄바꿈/경로 구분자(리눅스 ':', 윈도우 ';')로 이어 적는다.
-- (mvn dependency:build-classpath 의 출력을 그대로 넣어도 된다.)
-- 프로젝트 폴더 안의 jar(WEB-INF/lib 등)는 적지 않아도 자동으로 찾는다.
-- 테이블을 이미 만든 DB 에도 컬럼이 생기도록 ALTER 로 추가한다.
ALTER TABLE analysis_project ADD COLUMN IF NOT EXISTS classpath TEXT;

-- 빌려 쓸 다른 프로젝트의 의미 분석 규칙(conf/rules/projects 아래 폴더 이름 hi-japan 이나 파일 hi-japan/backend, 쉼표로 이어 적는다).
-- common/ 과 guess/ 의 규칙, 그리고 이 프로젝트 폴더(conf/rules/projects/{프로젝트ID})의 규칙은 적지 않아도 쓴다.
ALTER TABLE analysis_project ADD COLUMN IF NOT EXISTS rule_sets TEXT;

-- 리비전: 한 프로젝트의 어느 시점(커밋 등)
CREATE TABLE IF NOT EXISTS analysis_revision (
    revision_id         BIGSERIAL     PRIMARY KEY,
    project_id          VARCHAR(100)  NOT NULL REFERENCES analysis_project(project_id),
    revision_label      VARCHAR(200)  NOT NULL,          -- 커밋 해시나 사용자가 붙인 이름
    branch              VARCHAR(200),
    parent_revision_id  BIGINT,                          -- 증분 분석의 기준이 된 리비전 (전체 분석이면 NULL)
    status              VARCHAR(20)   NOT NULL DEFAULT 'CREATED',  -- CREATED/ANALYZING/READY/FAILED
    analyzer_version    VARCHAR(20),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (project_id, revision_label)
);

-- 분석 Job: 리비전 하나를 분석하는 실행 1건
CREATE TABLE IF NOT EXISTS analysis_job (
    analysis_id       VARCHAR(30)   PRIMARY KEY,         -- 예: A20261004001
    project_id        VARCHAR(100)  NOT NULL REFERENCES analysis_project(project_id),
    revision_id       BIGINT        NOT NULL REFERENCES analysis_revision(revision_id),
    job_type          VARCHAR(20)   NOT NULL DEFAULT 'ANALYZE',    -- ANALYZE/RAG_BUILD
    status            VARCHAR(20)   NOT NULL DEFAULT 'READY',      -- READY/RUNNING/DONE/DONE_WITH_WARNING/FAILED/CANCELLED
    current_pass      VARCHAR(30),                       -- 지금 돌고 있는 단계 (SCAN/DECLARE/RESOLVE/...)
    options_json      JSONB         NOT NULL DEFAULT '{}',
    total_files       INT           NOT NULL DEFAULT 0,
    done_files        INT           NOT NULL DEFAULT 0,
    error_message     TEXT,
    started_at        TIMESTAMPTZ,
    ended_at          TIMESTAMPTZ,
    heartbeat_at      TIMESTAMPTZ,                       -- 살아 있다는 표시. 오래 안 바뀌면 죽은 Job 으로 본다
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_analysis_job_revision ON analysis_job(revision_id);
CREATE INDEX IF NOT EXISTS idx_analysis_job_status   ON analysis_job(status);

-- 리비전별/단계별 진행 상태. "이 리비전은 SCAN 을 끝냈나?" 를 여기서 본다.
-- Job 이 죽거나 취소된 뒤 같은 리비전으로 다시 시작하면, DONE 인 단계는 건너뛰고 그 다음부터 이어서 한다.
-- (파일 하나하나의 진행 상태는 analysis_file_pass 가 따로 맡는다.)
CREATE TABLE IF NOT EXISTS analysis_revision_pass (
    revision_id       BIGINT        NOT NULL,
    pass              VARCHAR(30)   NOT NULL,            -- SCAN/DECLARE/RESOLVE/...
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',    -- PENDING/RUNNING/DONE/FAILED/CANCELLED
    analysis_id       VARCHAR(30),                       -- 마지막으로 이 단계를 돌린 Job
    total_count       INT           NOT NULL DEFAULT 0,  -- 이 단계가 처리할 건수 (모르면 0)
    done_count        INT           NOT NULL DEFAULT 0,
    started_at        TIMESTAMPTZ,
    ended_at          TIMESTAMPTZ,
    PRIMARY KEY (revision_id, pass)
);


/**********************************************
2. 파일과 진행 상태
**********************************************/

-- 스캔한 파일 (Java/XML/YAML/Properties/JSP/빌드 파일)
CREATE TABLE IF NOT EXISTS analysis_file (
    file_id           BIGSERIAL     PRIMARY KEY,
    revision_id       BIGINT        NOT NULL,
    module            VARCHAR(200),                      -- 멀티모듈일 때 모듈 이름
    path              VARCHAR(1000) NOT NULL,            -- 프로젝트 루트 기준 상대 경로
    source_root       VARCHAR(1000),                     -- 이 파일이 속한 소스 루트 (예: src/main/java, WEB-INF/src)
    language          VARCHAR(20)   NOT NULL,            -- JAVA/XML/YAML/PROPERTIES/JSP/...
    file_type         VARCHAR(30),                       -- SOURCE/MYBATIS_MAPPER/SPRING_XML/WEB_XML/CONFIG/BUILD/...
    package_name      VARCHAR(500),
    encoding          VARCHAR(30),                       -- 실제로 읽을 때 쓴 인코딩
    language_level    VARCHAR(20),                       -- 실제로 파싱에 성공한 Java 문법 수준
    checksum          VARCHAR(64)   NOT NULL,            -- 파일 내용의 SHA-256. 증분 분석 때 바뀐 파일을 찾는 기준
    size_bytes        BIGINT,
    line_count        INT,
    parse_status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',    -- PENDING/OK/FAILED/SKIPPED
    parse_error       TEXT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (revision_id, path)
);
CREATE INDEX IF NOT EXISTS idx_analysis_file_checksum ON analysis_file(revision_id, checksum);

-- 파일별/단계별 진행 상태. "이 단계에서 아직 안 한 파일 N개 주세요" 를 여기서 꺼낸다
CREATE TABLE IF NOT EXISTS analysis_file_pass (
    file_id           BIGINT        NOT NULL,
    pass              VARCHAR(30)   NOT NULL,            -- DECLARE/RESOLVE/SEMANTIC/DOCUMENT ...
    revision_id       BIGINT        NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',    -- PENDING/DONE/FAILED/SKIPPED
    error_message     TEXT,
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (file_id, pass)
);
CREATE INDEX IF NOT EXISTS idx_analysis_file_pass_todo ON analysis_file_pass(revision_id, pass, status);


/**********************************************
3. 심볼 (타입 / 메소드 / 필드 / 애노테이션)
**********************************************/

-- 타입: class / interface / enum / record / annotation
CREATE TABLE IF NOT EXISTS analysis_symbol (
    revision_id       BIGINT        NOT NULL,
    symbol_id         VARCHAR(40)   NOT NULL,            -- 리비전과 무관한 안정 ID
    kind              VARCHAR(20)   NOT NULL,            -- CLASS/INTERFACE/ENUM/RECORD/ANNOTATION/ANONYMOUS(익명 클래스)
    fqn               VARCHAR(1000) NOT NULL,            -- 패키지 포함 전체 이름
    simple_name       VARCHAR(300)  NOT NULL,
    package_name      VARCHAR(500),
    outer_symbol_id   VARCHAR(40),                       -- 중첩 타입이면 바깥 타입
    visibility        VARCHAR(20),
    is_abstract       BOOLEAN       NOT NULL DEFAULT FALSE,
    is_final          BOOLEAN       NOT NULL DEFAULT FALSE,
    is_static         BOOLEAN       NOT NULL DEFAULT FALSE,
    layer             VARCHAR(30),                       -- CONTROLLER/SERVICE/REPOSITORY/... (의미 분석이 채움)
    layer_confidence  VARCHAR(10),                       -- 애노테이션으로 알았으면 HIGH, 이름 규칙으로 짐작했으면 LOW
    properties_json   JSONB         NOT NULL DEFAULT '{}',
    file_id           BIGINT        NOT NULL,
    line_start        INT,
    line_end          INT,
    PRIMARY KEY (revision_id, symbol_id)
);
CREATE INDEX IF NOT EXISTS idx_analysis_symbol_fqn    ON analysis_symbol(revision_id, fqn);
CREATE INDEX IF NOT EXISTS idx_analysis_symbol_simple ON analysis_symbol(revision_id, simple_name);
CREATE INDEX IF NOT EXISTS idx_analysis_symbol_file   ON analysis_symbol(file_id);

-- 메소드 / 생성자
CREATE TABLE IF NOT EXISTS analysis_method (
    revision_id       BIGINT        NOT NULL,
    method_id         VARCHAR(40)   NOT NULL,
    owner_symbol_id   VARCHAR(40)   NOT NULL,
    name              VARCHAR(300)  NOT NULL,
    signature         VARCHAR(2000) NOT NULL,            -- 이름(파라미터 타입들). 예: cancelOrder(java.lang.Long)
    return_type       VARCHAR(1000),
    param_count       INT           NOT NULL DEFAULT 0,
    parameters_json   JSONB         NOT NULL DEFAULT '[]',         -- [{name, type}]
    visibility        VARCHAR(20),
    is_static         BOOLEAN       NOT NULL DEFAULT FALSE,
    is_abstract       BOOLEAN       NOT NULL DEFAULT FALSE,
    is_constructor    BOOLEAN       NOT NULL DEFAULT FALSE,
    -- 소스에는 없지만 컴파일하면 생기는 멤버 (Lombok 의 getter/setter 등).
    -- 이것도 정식 메소드로 넣어 둬야 호출 관계를 풀 수 있다. 위치는 근거가 된 필드/애노테이션의 줄이다.
    is_synthetic      BOOLEAN       NOT NULL DEFAULT FALSE,
    synthetic_origin  VARCHAR(50),                       -- 예: LOMBOK_GETTER, LOMBOK_DATA, DEFAULT_CONSTRUCTOR
    proxy_related     BOOLEAN       NOT NULL DEFAULT FALSE,        -- Spring 프록시가 끼어들 수 있는 메소드 (@Transactional 등)
    properties_json   JSONB         NOT NULL DEFAULT '{}',
    file_id           BIGINT        NOT NULL,
    line_start        INT,
    line_end          INT,
    PRIMARY KEY (revision_id, method_id)
);
CREATE INDEX IF NOT EXISTS idx_analysis_method_owner ON analysis_method(revision_id, owner_symbol_id, name);
CREATE INDEX IF NOT EXISTS idx_analysis_method_name  ON analysis_method(revision_id, name);
CREATE INDEX IF NOT EXISTS idx_analysis_method_file  ON analysis_method(file_id);

-- 필드
CREATE TABLE IF NOT EXISTS analysis_field (
    revision_id         BIGINT        NOT NULL,
    field_id            VARCHAR(40)   NOT NULL,
    owner_symbol_id     VARCHAR(40)   NOT NULL,
    name                VARCHAR(300)  NOT NULL,
    type                VARCHAR(1000),
    visibility          VARCHAR(20),
    is_static           BOOLEAN       NOT NULL DEFAULT FALSE,
    is_final            BOOLEAN       NOT NULL DEFAULT FALSE,
    is_synthetic        BOOLEAN       NOT NULL DEFAULT FALSE,      -- 예: Lombok @Slf4j 가 만드는 log 필드
    synthetic_origin    VARCHAR(50),
    initializer_summary VARCHAR(500),                    -- 초기값 요약 (상수 추적용)
    file_id             BIGINT        NOT NULL,
    line_start          INT,
    line_end            INT,
    PRIMARY KEY (revision_id, field_id)
);
CREATE INDEX IF NOT EXISTS idx_analysis_field_owner ON analysis_field(revision_id, owner_symbol_id, name);
CREATE INDEX IF NOT EXISTS idx_analysis_field_file  ON analysis_field(file_id);

-- 애노테이션이 붙은 자리 (어디에, 무엇이, 어떤 속성으로)
CREATE TABLE IF NOT EXISTS analysis_annotation (
    annotation_id     BIGSERIAL     PRIMARY KEY,
    revision_id       BIGINT        NOT NULL,
    target_kind       VARCHAR(20)   NOT NULL,            -- TYPE/METHOD/FIELD/PARAMETER
    target_id         VARCHAR(40)   NOT NULL,            -- symbol_id / method_id / field_id
    annotation_name   VARCHAR(300)  NOT NULL,            -- 단순 이름 (예: Transactional)
    annotation_fqn    VARCHAR(1000),                     -- 풀렸으면 전체 이름
    attributes_json   JSONB         NOT NULL DEFAULT '{}',
    file_id           BIGINT        NOT NULL,
    line_start        INT
);
CREATE INDEX IF NOT EXISTS idx_analysis_annotation_target ON analysis_annotation(revision_id, target_id);
CREATE INDEX IF NOT EXISTS idx_analysis_annotation_name   ON analysis_annotation(revision_id, annotation_name);
CREATE INDEX IF NOT EXISTS idx_analysis_annotation_file   ON analysis_annotation(file_id);


/**********************************************
4. 참조(아직 안 풀린 것)와 관계(풀린 것)
**********************************************/

-- 1차 패스가 모아 두는 참조. 이 시점에는 "이름"만 알고 누구를 가리키는지는 모른다
CREATE TABLE IF NOT EXISTS analysis_reference (
    reference_id      BIGSERIAL     PRIMARY KEY,
    revision_id       BIGINT        NOT NULL,
    file_id           BIGINT        NOT NULL,
    from_kind         VARCHAR(20)   NOT NULL,            -- TYPE/METHOD/FIELD
    from_id           VARCHAR(40)   NOT NULL,            -- 참조가 들어 있는 쪽
    ref_kind          VARCHAR(30)   NOT NULL,            -- CALL/CREATE/FIELD_ACCESS/TYPE_USE/THROWS/EXTENDS/IMPLEMENTS/ANONYMOUS_SUPER/METHOD_REF
    name              VARCHAR(1000) NOT NULL,            -- 메소드/타입/필드 이름
    scope_text        VARCHAR(1000),                     -- 호출 대상 식 (예: orderService, this.dao)
    arg_count         INT,
    line_start        INT,
    column_start      INT,                               -- 2차 패스에서 같은 AST 노드를 다시 찾는 키
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',    -- PENDING/RESOLVED/EXTERNAL(프로젝트 밖)/HEURISTIC(짐작)/UNRESOLVED/IGNORED(풀 대상 아님)
    fail_reason       VARCHAR(500)
);
-- 호출의 첫 인자를 소스에 적힌 그대로 담는다(문자열이 들어 있을 때만).
-- sqlSession.selectList("order.findAll", vo) 의 "order.findAll", new ModelAndView("order/list") 의 "order/list" 처럼
-- 문자열로 다른 것을 가리키는 호출을 풀 때 쓴다. 테이블을 이미 만든 DB 에도 생기도록 ALTER 로 추가한다.
ALTER TABLE analysis_reference ADD COLUMN IF NOT EXISTS arg_text VARCHAR(500);
-- 둘째 인자부터 따옴표 글자가 든 인자를 적힌 그대로 담은 JSON 배열(자리를 맞추려고 첫 칸과 따옴표 없는 인자는 null).
-- 예: dao.execute(param, "Order.list") → [null, "\"Order.list\""]
-- 규칙 파일(conf/rules)의 호출 인자 규칙에서 argIndex 를 1 이상으로 적었을 때 읽는다. 둘째 인자부터 따옴표 글자가 없으면 NULL.
ALTER TABLE analysis_reference ADD COLUMN IF NOT EXISTS args_json JSONB;

-- 증분 분석: 이 파일의 분석 결과를 앞 리비전의 어느 파일에서 옮겨 왔는지(바뀌지 않은 파일). 새 파일이거나 바뀐 파일이면 NULL.
-- 앞 리비전이 나중에 지워져도 옮겨 온 결과는 이 리비전의 것으로 온전히 남는다(이 값은 "어디서 왔는지" 의 기록일 뿐이다).
ALTER TABLE analysis_file ADD COLUMN IF NOT EXISTS carried_from_file_id BIGINT;
-- 증분 분석: 이 파일의 이 단계를 실제로 돌리지 않고 앞 리비전의 결과를 옮겨 왔는지
ALTER TABLE analysis_file_pass ADD COLUMN IF NOT EXISTS carried BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_analysis_reference_file   ON analysis_reference(file_id, status);
CREATE INDEX IF NOT EXISTS idx_analysis_reference_status ON analysis_reference(revision_id, status, ref_kind);

-- 관계: 그래프의 간선. 모든 관계 종류가 이 한 테이블에 들어간다
--   EXTENDS/IMPLEMENTS/HAS_METHOD/HAS_FIELD/CALLS/CALLS_POSSIBLE_IMPLEMENTATION/ACCESSES_FIELD/CREATES/
--   USES_TYPE/THROWS/ANNOTATED_BY/OVERRIDES/IMPLEMENTED_BY/DEPENDS_ON/DECLARES/REFERENCES/MAPS_TO/HANDLES/INJECTS ...
CREATE TABLE IF NOT EXISTS analysis_relation (
    relation_id       BIGSERIAL     PRIMARY KEY,
    revision_id       BIGINT        NOT NULL,
    from_kind         VARCHAR(20)   NOT NULL,
    from_id           VARCHAR(40)   NOT NULL,
    relation_type     VARCHAR(40)   NOT NULL,
    to_kind           VARCHAR(20)   NOT NULL,            -- 프로젝트 밖이면 EXTERNAL_TYPE / EXTERNAL_METHOD
    to_id             VARCHAR(40),                       -- 프로젝트 안의 대상. 밖이면 NULL
    to_external       VARCHAR(2000),                     -- 프로젝트 밖 대상의 이름 (예: java.util.List.add(E))
    -- 불확실한 것을 지우지 않고 정도를 남긴다
    confidence        VARCHAR(10)   NOT NULL DEFAULT 'HIGH',       -- HIGH/MEDIUM/LOW/UNRESOLVED
    resolution_status VARCHAR(20)   NOT NULL DEFAULT 'RESOLVED',   -- RESOLVED/HEURISTIC/UNRESOLVED/DYNAMIC(리플렉션 등)
    properties_json   JSONB         NOT NULL DEFAULT '{}',
    file_id           BIGINT,                            -- 근거가 된 소스 위치. SQL 로 계산한 관계는 NULL 일 수 있다
    line_start        INT,
    line_end          INT
);
CREATE INDEX IF NOT EXISTS idx_analysis_relation_from ON analysis_relation(revision_id, from_id, relation_type);
CREATE INDEX IF NOT EXISTS idx_analysis_relation_to   ON analysis_relation(revision_id, to_id, relation_type);
CREATE INDEX IF NOT EXISTS idx_analysis_relation_file ON analysis_relation(file_id);


/**********************************************
5. 의미 분석 결과 (진입점 / 설정 / 매퍼 / 그 밖의 리소스)
**********************************************/

-- 진입점: 밖에서 이 프로그램으로 들어오는 입구.
-- 설계서의 이름(endpoint)을 그대로 쓰지만 HTTP 만 담지 않는다. 구버전 일반 Java 프로그램도 다뤄야 하기 때문이다
CREATE TABLE IF NOT EXISTS analysis_endpoint (
    endpoint_id       BIGSERIAL     PRIMARY KEY,
    revision_id       BIGINT        NOT NULL,
    endpoint_type     VARCHAR(20)   NOT NULL,            -- HTTP/SERVLET/MAIN/SCHEDULED/LISTENER/JSP/THREAD/TRANSACTION
    http_method       VARCHAR(20),
    path              VARCHAR(1000),                     -- URL 패턴. MAIN 처럼 URL 이 없으면 NULL. TRANSACTION 은 거래 ID
    symbol_id         VARCHAR(40),
    method_id         VARCHAR(40),                       -- 이 진입점을 처리하는 메소드
    properties_json   JSONB         NOT NULL DEFAULT '{}',
    file_id           BIGINT        NOT NULL,
    line_start        INT
);
CREATE INDEX IF NOT EXISTS idx_analysis_endpoint_method ON analysis_endpoint(revision_id, method_id);
CREATE INDEX IF NOT EXISTS idx_analysis_endpoint_path   ON analysis_endpoint(revision_id, path);
CREATE INDEX IF NOT EXISTS idx_analysis_endpoint_file   ON analysis_endpoint(file_id);

-- 설정 값: application.yml / *.properties 의 키 하나하나
CREATE TABLE IF NOT EXISTS analysis_config (
    config_id         BIGSERIAL     PRIMARY KEY,
    revision_id       BIGINT        NOT NULL,
    key_path          VARCHAR(1000) NOT NULL,            -- 예: spring.datasource.url
    value             TEXT,
    value_type        VARCHAR(30),
    placeholder       VARCHAR(500),                      -- 값 안에 들어 있는 동적 변수 참조
    file_id           BIGINT        NOT NULL,
    line_start        INT
);
CREATE INDEX IF NOT EXISTS idx_analysis_config_key  ON analysis_config(revision_id, key_path);
CREATE INDEX IF NOT EXISTS idx_analysis_config_file ON analysis_config(file_id);

-- SQL 매퍼: MyBatis / iBATIS 의 statement 하나하나
CREATE TABLE IF NOT EXISTS analysis_mapper (
    mapper_id         BIGSERIAL     PRIMARY KEY,
    revision_id       BIGINT        NOT NULL,
    mapper_type       VARCHAR(20)   NOT NULL,            -- MYBATIS/IBATIS/QUERY_XML/JAVA(Java 문자열 SQL)/JPA
    namespace         VARCHAR(1000),
    statement_id      VARCHAR(300)  NOT NULL,
    statement_type    VARCHAR(20),                       -- SELECT/INSERT/UPDATE/DELETE
    parameter_type    VARCHAR(1000),
    result_type       VARCHAR(1000),
    sql_body          TEXT,
    method_id         VARCHAR(40),                       -- 이 statement 와 짝이 되는 Java 메소드 (찾았으면)
    file_id           BIGINT        NOT NULL,
    line_start        INT,
    line_end          INT
);
CREATE INDEX IF NOT EXISTS idx_analysis_mapper_stmt   ON analysis_mapper(revision_id, namespace, statement_id);
CREATE INDEX IF NOT EXISTS idx_analysis_mapper_method ON analysis_mapper(revision_id, method_id);
CREATE INDEX IF NOT EXISTS idx_analysis_mapper_file   ON analysis_mapper(file_id);
-- Java 소스에 문자열로 적은 SQL(mapper_type JAVA, DECLARE 단계)과 JPA 쿼리(mapper_type JPA, SEMANTIC 단계)도 이 테이블에 넣는다.
-- 그 행의 덧붙임 정보: 어느 메소드 · 상수의 것인지, 무엇에 넘겼는지, JPQL 인지 등. 이미 만든 DB 에도 생기도록 ALTER 로 추가한다.
ALTER TABLE analysis_mapper ADD COLUMN IF NOT EXISTS properties_json JSONB NOT NULL DEFAULT '{}';

-- 그 밖의 리소스 항목: XML 의 element/attribute, Spring XML 의 bean 정의, web.xml 의 서블릿 매핑 등
CREATE TABLE IF NOT EXISTS analysis_resource (
    resource_id       BIGSERIAL     PRIMARY KEY,
    revision_id       BIGINT        NOT NULL,
    resource_type     VARCHAR(30)   NOT NULL,            -- XML_ELEMENT/SPRING_BEAN/SERVLET_MAPPING/DEPENDENCY/...
    name              VARCHAR(1000),
    location          VARCHAR(1000),                     -- 파일 안의 위치 (XPath 비슷한 경로)
    value             TEXT,
    properties_json   JSONB         NOT NULL DEFAULT '{}',
    file_id           BIGINT,                            -- 어느 파일에서 나왔는지. 소스 파일이 아닌 곳(테이블 사전 CSV 등)에서 온 행은 NULL
    line_start        INT
);
CREATE INDEX IF NOT EXISTS idx_analysis_resource_type ON analysis_resource(revision_id, resource_type, name);
CREATE INDEX IF NOT EXISTS idx_analysis_resource_file ON analysis_resource(file_id);
-- 테이블 사전(DICT_TABLE_NAME / DICT_COLUMN_NAME)은 프로젝트 소스 밖의 CSV 에서 오므로 파일이 없다. 이미 만든 DB 에도 적용되도록 ALTER 로 푼다.
ALTER TABLE analysis_resource ALTER COLUMN file_id DROP NOT NULL;


/**********************************************
6. 품질 (오류 / 지표)
**********************************************/

-- 분석 중 생긴 오류. 실패한 파일과 못 푼 심볼을 숨기지 않고 여기에 남긴다
CREATE TABLE IF NOT EXISTS analysis_error (
    error_id          BIGSERIAL     PRIMARY KEY,
    analysis_id       VARCHAR(30)   NOT NULL,
    revision_id       BIGINT        NOT NULL,
    file_id           BIGINT,
    pass              VARCHAR(30),
    error_type        VARCHAR(50)   NOT NULL,            -- PARSE_ERROR/ENCODING/CLASSPATH/RESOLVE_TIMEOUT/...
    message           TEXT,
    detail            TEXT,
    line_start        INT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_analysis_error_analysis ON analysis_error(analysis_id, error_type);

-- 분석 품질 지표 (parseSuccessRate, callResolutionRate ...)
CREATE TABLE IF NOT EXISTS analysis_metric (
    analysis_id       VARCHAR(30)   NOT NULL,
    metric_name       VARCHAR(100)  NOT NULL,
    metric_value      NUMERIC(20,4),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (analysis_id, metric_name)
);


/**********************************************
7. RAG (문서 / 청크 / 임베딩)
**********************************************/

-- 논리 문서: 코드에서 만든 것(타입/메소드/파일 ...)과 사용자가 올린 일반 문서(PDF/Word ...) 둘 다 담는다
CREATE TABLE IF NOT EXISTS rag_document (
    doc_seq           BIGSERIAL     PRIMARY KEY,
    document_id       VARCHAR(200)  NOT NULL,            -- 같은 대상이면 리비전이 달라도 같은 값
    source_type       VARCHAR(20)   NOT NULL,            -- CODE(분석 결과에서 생성) / DOCUMENT(업로드한 일반 문서)
    tenant            VARCHAR(100),                      -- 호출자별 격리 키. 다른 호출자의 문서가 검색에 섞이지 않게 한다
    collection        VARCHAR(100),                      -- DOCUMENT 일 때만. 올린 문서를 묶는 이름(예: nexacro). 검색 범위를 좁힐 때 쓴다
    revision_id       BIGINT,                            -- CODE 일 때만. 프로젝트는 이 리비전(analysis_revision)으로 찾는다
    doc_type          VARCHAR(30)   NOT NULL,            -- FILE/TYPE/METHOD(Java) / MAPPER(SQL statement) / VIEW(JSP) / UPLOAD(올린 일반 문서)
    ref_kind          VARCHAR(20),                       -- 이 문서의 근거가 된 대상 종류 (TYPE/METHOD/FILE ...)
    ref_id            VARCHAR(40),                       -- 그 대상의 ID
    title             VARCHAR(1000),
    source_path       VARCHAR(1000),
    metadata_json     JSONB         NOT NULL DEFAULT '{}',
    content_hash      VARCHAR(64),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rag_document_revision ON rag_document(revision_id, doc_type);
CREATE INDEX IF NOT EXISTS idx_rag_document_ref      ON rag_document(revision_id, ref_id);
-- 테이블을 이미 만든 DB 에도 맞추기 위한 변경(2026-11-14).
--   collection: 올린 문서의 묶음 이름을 새로 둔다.
--   project_id: 지운다. 코드 문서는 revision_id 로 프로젝트를 찾고, 올린 문서는 프로젝트에 붙이지 않는다.
ALTER TABLE rag_document ADD COLUMN IF NOT EXISTS collection VARCHAR(100);
ALTER TABLE rag_document DROP COLUMN IF EXISTS project_id;
CREATE INDEX IF NOT EXISTS idx_rag_document_tenant   ON rag_document(tenant, source_type);
CREATE INDEX IF NOT EXISTS idx_rag_document_collection ON rag_document(tenant, collection) WHERE source_type = 'DOCUMENT';
CREATE INDEX IF NOT EXISTS idx_rag_document_docid    ON rag_document(document_id);
-- 파일 하나의 문서를 다시 만들 때(같은 파일을 다시 처리할 때) 그 파일의 문서를 빨리 찾아 지우기 위한 인덱스
CREATE INDEX IF NOT EXISTS idx_rag_document_path     ON rag_document(revision_id, source_path);

-- 청크: 문서를 의미 단위로 자른 조각. 검색 결과로 돌려주는 단위다
CREATE TABLE IF NOT EXISTS rag_chunk (
    chunk_id          BIGSERIAL     PRIMARY KEY,
    doc_seq           BIGINT        NOT NULL,
    revision_id       BIGINT,
    tenant            VARCHAR(100),
    chunk_no          INT           NOT NULL DEFAULT 0,
    chunk_type        VARCHAR(30)   NOT NULL,            -- FILE/TYPE/METHOD/... (METHOD 는 CODE 와 CONTEXT 로 더 나뉠 수 있다)
    content           TEXT          NOT NULL,
    content_hash      VARCHAR(64)   NOT NULL,            -- 내용의 SHA-256. 임베딩을 찾는 키
    char_count        INT,
    metadata_json     JSONB         NOT NULL DEFAULT '{}',
    file_id           BIGINT,                            -- 원본 파일/줄로 되짚어 가기 위한 정보
    line_start        INT,
    line_end          INT
);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_doc      ON rag_chunk(doc_seq);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_hash     ON rag_chunk(content_hash);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_revision ON rag_chunk(revision_id, chunk_type);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_file     ON rag_chunk(file_id);

-- 임베딩: 키가 (내용 해시, 모델) 이다.
-- 같은 내용은 리비전이 달라도, 분석을 다시 돌려도 한 번만 임베딩한다. 임베딩이 제일 오래 걸리는 작업이기 때문이다.
-- 이 테이블이 곧 대기열이다. status=PENDING 인 행을 배치로 꺼내 임베딩하고 DONE 으로 바꾼다. 중간에 죽어도 이어서 한다.
--
-- vector(1024) 는 bge-m3 의 출력 차원이다. 차원이 다른 모델로 바꾸려면 이 컬럼과 인덱스를 다시 만들어야 한다.
CREATE TABLE IF NOT EXISTS rag_embedding (
    content_hash      VARCHAR(64)   NOT NULL,
    model             VARCHAR(100)  NOT NULL,            -- 예: bge-m3:latest
    dimensions        INT           NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',    -- PENDING/DONE/FAILED
    attempts          INT           NOT NULL DEFAULT 0,
    last_error        TEXT,
    embedding         vector(1024),                      -- 임베딩 전에는 NULL
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    embedded_at       TIMESTAMPTZ,
    priority          INT           NOT NULL DEFAULT 0,  -- 큰 것부터 임베딩한다. 코드 0, 올린 일반 문서 10
    PRIMARY KEY (content_hash, model)
);
-- 우선순위: 사람이 올린 문서 한 건이 큰 프로젝트의 청크 수만 건 뒤에서 몇 시간씩 기다리지 않게 한다.
-- 테이블을 이미 만든 DB 에도 생기도록 ALTER 로 추가한다.
ALTER TABLE rag_embedding ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 0;
-- 대기 중인 것만 빨리 꺼내기 위한 부분 인덱스
CREATE INDEX IF NOT EXISTS idx_rag_embedding_todo ON rag_embedding(status, created_at) WHERE status <> 'DONE';
-- 코사인 유사도 검색용 HNSW 인덱스 (NULL 인 행은 인덱스에 들어가지 않는다)
CREATE INDEX IF NOT EXISTS idx_rag_embedding_hnsw ON rag_embedding USING hnsw (embedding vector_cosine_ops);


/**********************************************
8. Knowledge Graph VIEW
그래프를 따로 저장하지 않고 analysis_* 를 node/edge 모양으로 보여 준다.
**********************************************/

-- 컬럼의 타입이 바뀌면 CREATE OR REPLACE 가 실패하므로(노드 종류를 더하면서 타입이 넓어졌다) 지우고 다시 만든다.
DROP VIEW IF EXISTS kg_node;
CREATE VIEW kg_node AS
    SELECT r.project_id, s.revision_id, s.symbol_id AS node_id, 'TYPE'::varchar AS node_type, s.kind AS sub_type
         , s.simple_name AS name, s.fqn AS fqn, s.properties_json, s.file_id, s.line_start, s.line_end
      FROM analysis_symbol s JOIN analysis_revision r ON r.revision_id = s.revision_id
    UNION ALL
    SELECT r.project_id, m.revision_id, m.method_id, 'METHOD'::varchar
         , (CASE WHEN m.is_constructor THEN 'CONSTRUCTOR' ELSE 'METHOD' END)::varchar
         , m.name, (o.fqn || '#' || m.signature)::varchar, m.properties_json, m.file_id, m.line_start, m.line_end
      FROM analysis_method m
      JOIN analysis_revision r ON r.revision_id = m.revision_id
      JOIN analysis_symbol o ON o.revision_id = m.revision_id AND o.symbol_id = m.owner_symbol_id
    UNION ALL
    SELECT r.project_id, f.revision_id, f.field_id, 'FIELD'::varchar, 'FIELD'::varchar
         , f.name, (o.fqn || '#' || f.name)::varchar, '{}'::jsonb, f.file_id, f.line_start, f.line_end
      FROM analysis_field f
      JOIN analysis_revision r ON r.revision_id = f.revision_id
      JOIN analysis_symbol o ON o.revision_id = f.revision_id AND o.symbol_id = f.owner_symbol_id
    UNION ALL
    -- SQL statement (MyBatis/iBATIS 매퍼). 관계에서 가리킬 때 쓰는 ID 는 'S' + mapper_id 다(다른 ID 와 겹치지 않게)
    SELECT r.project_id, m.revision_id, ('S' || m.mapper_id)::varchar, 'SQL'::varchar, m.statement_type
         , m.statement_id, (COALESCE(m.namespace || '.', '') || m.statement_id)::varchar, '{}'::jsonb, m.file_id, m.line_start, m.line_end
      FROM analysis_mapper m JOIN analysis_revision r ON r.revision_id = m.revision_id
     WHERE m.statement_type <> 'SQL_FRAGMENT'
    UNION ALL
    -- 파일. JSP 처럼 파일 자체가 관계의 한쪽이 되는 경우에 쓴다. ID 는 'F' + file_id
    SELECT r.project_id, f.revision_id, ('F' || f.file_id)::varchar, 'FILE'::varchar, f.language
         , f.path, f.path, '{}'::jsonb, f.file_id, 1, f.line_count
      FROM analysis_file f JOIN analysis_revision r ON r.revision_id = f.revision_id;

CREATE OR REPLACE VIEW kg_edge AS
    SELECT r.project_id, e.revision_id, e.relation_id AS edge_id
         , e.from_id AS from_node_id, e.relation_type AS edge_type, e.to_id AS to_node_id, e.to_external
         , e.confidence, e.resolution_status, e.properties_json, e.file_id, e.line_start, e.line_end
      FROM analysis_relation e JOIN analysis_revision r ON r.revision_id = e.revision_id;


/**********************************************
9. 테이블 · 컬럼 코멘트
COMMENT ON 은 덮어쓰기라 여러 번 실행해도 된다. kg_node 는 위에서 다시 만들므로 이 절이 맨 끝에 있어야 한다.
테이블이나 컬럼을 새로 추가하면 여기에도 코멘트를 함께 적는다.
**********************************************/

/**********************************************
9-1. 코멘트: 프로젝트 / 리비전 / 분석 Job
**********************************************/

COMMENT ON TABLE  analysis_project                   IS '분석 대상 프로젝트';
COMMENT ON COLUMN analysis_project.project_id        IS '프로젝트 ID. 사용자가 정하는 키 (예: order-system)';
COMMENT ON COLUMN analysis_project.project_name      IS '프로젝트 이름';
COMMENT ON COLUMN analysis_project.repository        IS 'git 저장소 URL (없으면 NULL)';
COMMENT ON COLUMN analysis_project.local_path        IS '소스가 있는 로컬 경로';
COMMENT ON COLUMN analysis_project.branch            IS '브랜치';
COMMENT ON COLUMN analysis_project.root_packages     IS '분석할 루트 패키지 (쉼표 구분). 이 밖의 코드는 분석하지 않는다';
COMMENT ON COLUMN analysis_project.java_version      IS '소스 문법 수준 (예: 1.4, 8, 21). NULL 이면 자동';
COMMENT ON COLUMN analysis_project.source_encoding   IS '소스 인코딩 (예: EUC-KR). NULL 이면 파일마다 자동 감지';
COMMENT ON COLUMN analysis_project.description       IS '설명';
COMMENT ON COLUMN analysis_project.created_at        IS '등록 시각';
COMMENT ON COLUMN analysis_project.updated_at        IS '마지막 수정 시각';
COMMENT ON COLUMN analysis_project.classpath         IS '호출 해석에 쓸 라이브러리 jar 또는 jar 폴더 목록 (쉼표/줄바꿈/경로 구분자). 프로젝트 폴더 안의 jar 는 자동으로 찾는다';
COMMENT ON COLUMN analysis_project.rule_sets         IS '빌려 쓸 다른 프로젝트의 의미 분석 규칙 (conf/rules/projects 아래 폴더 또는 폴더/파일 이름, 쉼표 구분)';

COMMENT ON TABLE  analysis_revision                  IS '리비전: 한 프로젝트의 어느 시점 소스 전체를 분석한 결과 한 벌';
COMMENT ON COLUMN analysis_revision.revision_id      IS '리비전 ID (일련번호)';
COMMENT ON COLUMN analysis_revision.project_id       IS '프로젝트 ID';
COMMENT ON COLUMN analysis_revision.revision_label   IS '커밋 해시나 사용자가 붙인 이름. 프로젝트 안에서 유일';
COMMENT ON COLUMN analysis_revision.branch           IS '브랜치';
COMMENT ON COLUMN analysis_revision.parent_revision_id IS '증분 분석의 기준이 된 리비전 (전체 분석이면 NULL)';
COMMENT ON COLUMN analysis_revision.status           IS '상태: CREATED/ANALYZING/READY/FAILED';
COMMENT ON COLUMN analysis_revision.analyzer_version IS '분석한 분석기 버전. 다르면 증분 분석을 하지 않고 전체 분석한다';
COMMENT ON COLUMN analysis_revision.created_at       IS '생성 시각';

COMMENT ON TABLE  analysis_job                       IS '분석 Job: 리비전 하나를 분석하는 실행 1건';
COMMENT ON COLUMN analysis_job.analysis_id           IS '분석 ID (예: A20261004001)';
COMMENT ON COLUMN analysis_job.project_id            IS '프로젝트 ID';
COMMENT ON COLUMN analysis_job.revision_id           IS '분석하는 리비전';
COMMENT ON COLUMN analysis_job.job_type              IS 'Job 종류: ANALYZE/RAG_BUILD';
COMMENT ON COLUMN analysis_job.status                IS '상태: READY/RUNNING/DONE/DONE_WITH_WARNING/FAILED/CANCELLED';
COMMENT ON COLUMN analysis_job.current_pass          IS '지금 돌고 있는 단계 (SCAN/DECLARE/RESOLVE/...)';
COMMENT ON COLUMN analysis_job.options_json          IS '실행 옵션 (JSON)';
COMMENT ON COLUMN analysis_job.total_files           IS '처리할 파일 수';
COMMENT ON COLUMN analysis_job.done_files            IS '처리한 파일 수';
COMMENT ON COLUMN analysis_job.error_message         IS '실패했을 때의 오류 메시지';
COMMENT ON COLUMN analysis_job.started_at            IS '시작 시각';
COMMENT ON COLUMN analysis_job.ended_at              IS '종료 시각';
COMMENT ON COLUMN analysis_job.heartbeat_at          IS '살아 있다는 표시. 오래 안 바뀌면 죽은 Job 으로 본다';
COMMENT ON COLUMN analysis_job.created_at            IS '요청 시각';

COMMENT ON TABLE  analysis_revision_pass             IS '리비전별·단계별 진행 상태. 다시 시작하면 DONE 인 단계는 건너뛴다';
COMMENT ON COLUMN analysis_revision_pass.revision_id IS '리비전 ID';
COMMENT ON COLUMN analysis_revision_pass.pass        IS '단계: SCAN/CARRY/DECLARE/RESOURCE/CARRY_RESOLVE/RESOLVE/LINK/SEMANTIC/DOCUMENT';
COMMENT ON COLUMN analysis_revision_pass.status      IS '상태: PENDING/RUNNING/DONE/FAILED/CANCELLED';
COMMENT ON COLUMN analysis_revision_pass.analysis_id IS '마지막으로 이 단계를 돌린 Job';
COMMENT ON COLUMN analysis_revision_pass.total_count IS '이 단계가 처리할 건수 (모르면 0)';
COMMENT ON COLUMN analysis_revision_pass.done_count  IS '처리한 건수';
COMMENT ON COLUMN analysis_revision_pass.started_at  IS '시작 시각';
COMMENT ON COLUMN analysis_revision_pass.ended_at    IS '종료 시각';


/**********************************************
9-2. 코멘트: 파일과 진행 상태
**********************************************/

COMMENT ON TABLE  analysis_file                      IS '스캔한 파일 (Java/XML/YAML/Properties/JSP/빌드 파일 등)';
COMMENT ON COLUMN analysis_file.file_id              IS '파일 ID (일련번호, 리비전마다 새로 매긴다)';
COMMENT ON COLUMN analysis_file.revision_id          IS '리비전 ID';
COMMENT ON COLUMN analysis_file.module               IS '멀티모듈일 때 모듈 이름';
COMMENT ON COLUMN analysis_file.path                 IS '프로젝트 루트 기준 상대 경로';
COMMENT ON COLUMN analysis_file.source_root          IS '이 파일이 속한 소스 루트 (예: src/main/java, WEB-INF/src)';
COMMENT ON COLUMN analysis_file.language             IS '언어: JAVA/XML/YAML/PROPERTIES/JSP/SQL/SCRIPT';
COMMENT ON COLUMN analysis_file.file_type            IS '파일 용도: SOURCE/MYBATIS_MAPPER/IBATIS_MAPPER/SPRING_XML/WEB_XML/CONFIG/BUILD/JSP/WEBSQUARE/NEXACRO/...';
COMMENT ON COLUMN analysis_file.package_name         IS 'Java 패키지 이름';
COMMENT ON COLUMN analysis_file.encoding             IS '실제로 읽을 때 쓴 인코딩';
COMMENT ON COLUMN analysis_file.language_level       IS '실제로 파싱에 성공한 Java 문법 수준';
COMMENT ON COLUMN analysis_file.checksum             IS '파일 내용의 SHA-256. 증분 분석 때 바뀐 파일을 찾는 기준';
COMMENT ON COLUMN analysis_file.size_bytes           IS '파일 크기 (바이트)';
COMMENT ON COLUMN analysis_file.line_count           IS '줄 수';
COMMENT ON COLUMN analysis_file.parse_status         IS '파싱 상태: PENDING/OK/FAILED/SKIPPED';
COMMENT ON COLUMN analysis_file.parse_error          IS '파싱 실패 사유';
COMMENT ON COLUMN analysis_file.created_at           IS '등록 시각';
COMMENT ON COLUMN analysis_file.carried_from_file_id IS '증분 분석: 결과를 옮겨 온 앞 리비전의 파일 ID. 새 파일이거나 바뀐 파일이면 NULL';

COMMENT ON TABLE  analysis_file_pass                 IS '파일별·단계별 진행 상태. 아직 안 한 파일을 여기서 꺼낸다';
COMMENT ON COLUMN analysis_file_pass.file_id         IS '파일 ID';
COMMENT ON COLUMN analysis_file_pass.pass            IS '단계: DECLARE/RESOURCE/RESOLVE/DOCUMENT ...';
COMMENT ON COLUMN analysis_file_pass.revision_id     IS '리비전 ID';
COMMENT ON COLUMN analysis_file_pass.status          IS '상태: PENDING/DONE/FAILED/SKIPPED';
COMMENT ON COLUMN analysis_file_pass.error_message   IS '실패했을 때의 오류 메시지';
COMMENT ON COLUMN analysis_file_pass.updated_at      IS '마지막 상태 변경 시각';
COMMENT ON COLUMN analysis_file_pass.carried         IS '증분 분석: 이 단계를 돌리지 않고 앞 리비전의 결과를 옮겨 왔는지';


/**********************************************
9-3. 코멘트: 심볼 (타입 / 메소드 / 필드 / 애노테이션)
**********************************************/

COMMENT ON TABLE  analysis_symbol                    IS '타입: class / interface / enum / record / annotation / 익명 클래스';
COMMENT ON COLUMN analysis_symbol.revision_id        IS '리비전 ID';
COMMENT ON COLUMN analysis_symbol.symbol_id          IS '타입 ID. 리비전과 상관없이 같은 타입이면 같은 값 (해시)';
COMMENT ON COLUMN analysis_symbol.kind               IS '종류: CLASS/INTERFACE/ENUM/RECORD/ANNOTATION/ANONYMOUS';
COMMENT ON COLUMN analysis_symbol.fqn                IS '패키지를 포함한 전체 이름';
COMMENT ON COLUMN analysis_symbol.simple_name        IS '단순 이름';
COMMENT ON COLUMN analysis_symbol.package_name       IS '패키지 이름';
COMMENT ON COLUMN analysis_symbol.outer_symbol_id    IS '중첩 타입이면 바깥 타입의 ID';
COMMENT ON COLUMN analysis_symbol.visibility         IS '접근 제한자: public/protected/package/private';
COMMENT ON COLUMN analysis_symbol.is_abstract        IS 'abstract 여부';
COMMENT ON COLUMN analysis_symbol.is_final           IS 'final 여부';
COMMENT ON COLUMN analysis_symbol.is_static          IS 'static 여부 (중첩 타입)';
COMMENT ON COLUMN analysis_symbol.layer              IS '계층: CONTROLLER/SERVICE/REPOSITORY/MODEL/CONFIG/... (의미 분석이 채운다)';
COMMENT ON COLUMN analysis_symbol.layer_confidence   IS '계층 판단의 확실성: 애노테이션·규칙이면 HIGH, 이름으로 짐작했으면 LOW';
COMMENT ON COLUMN analysis_symbol.properties_json    IS '덧붙임 정보 (JSON)';
COMMENT ON COLUMN analysis_symbol.file_id            IS '선언된 파일 ID';
COMMENT ON COLUMN analysis_symbol.line_start         IS '시작 줄';
COMMENT ON COLUMN analysis_symbol.line_end           IS '끝 줄';

COMMENT ON TABLE  analysis_method                    IS '메소드 / 생성자';
COMMENT ON COLUMN analysis_method.revision_id        IS '리비전 ID';
COMMENT ON COLUMN analysis_method.method_id          IS '메소드 ID. 리비전과 상관없이 같은 메소드면 같은 값 (해시)';
COMMENT ON COLUMN analysis_method.owner_symbol_id    IS '이 메소드를 가진 타입의 ID';
COMMENT ON COLUMN analysis_method.name               IS '메소드 이름';
COMMENT ON COLUMN analysis_method.signature          IS '이름(파라미터 타입들). 예: cancelOrder(java.lang.Long)';
COMMENT ON COLUMN analysis_method.return_type        IS '반환 타입';
COMMENT ON COLUMN analysis_method.param_count        IS '파라미터 개수';
COMMENT ON COLUMN analysis_method.parameters_json    IS '파라미터 목록 [{name, type}]';
COMMENT ON COLUMN analysis_method.visibility         IS '접근 제한자';
COMMENT ON COLUMN analysis_method.is_static          IS 'static 여부';
COMMENT ON COLUMN analysis_method.is_abstract        IS 'abstract 여부 (인터페이스 메소드 포함)';
COMMENT ON COLUMN analysis_method.is_constructor     IS '생성자 여부';
COMMENT ON COLUMN analysis_method.is_synthetic       IS '소스에는 없지만 컴파일하면 생기는 멤버인지 (Lombok getter, 기본 생성자 등)';
COMMENT ON COLUMN analysis_method.synthetic_origin   IS '만들어진 근거: LOMBOK_GETTER/LOMBOK_DATA/DEFAULT_CONSTRUCTOR/RECORD_ACCESSOR/...';
COMMENT ON COLUMN analysis_method.proxy_related      IS 'Spring 프록시가 끼어들 수 있는 메소드인지 (@Transactional 등)';
COMMENT ON COLUMN analysis_method.properties_json    IS '덧붙임 정보 (JSON)';
COMMENT ON COLUMN analysis_method.file_id            IS '선언된 파일 ID';
COMMENT ON COLUMN analysis_method.line_start         IS '시작 줄 (합성 멤버는 근거가 된 필드·애노테이션의 줄)';
COMMENT ON COLUMN analysis_method.line_end           IS '끝 줄';

COMMENT ON TABLE  analysis_field                     IS '필드';
COMMENT ON COLUMN analysis_field.revision_id         IS '리비전 ID';
COMMENT ON COLUMN analysis_field.field_id            IS '필드 ID. 리비전과 상관없이 같은 필드면 같은 값 (해시)';
COMMENT ON COLUMN analysis_field.owner_symbol_id     IS '이 필드를 가진 타입의 ID';
COMMENT ON COLUMN analysis_field.name                IS '필드 이름';
COMMENT ON COLUMN analysis_field.type                IS '필드 타입';
COMMENT ON COLUMN analysis_field.visibility          IS '접근 제한자';
COMMENT ON COLUMN analysis_field.is_static           IS 'static 여부';
COMMENT ON COLUMN analysis_field.is_final            IS 'final 여부';
COMMENT ON COLUMN analysis_field.is_synthetic        IS '소스에 없이 생기는 필드인지 (예: Lombok @Slf4j 의 log)';
COMMENT ON COLUMN analysis_field.synthetic_origin    IS '만들어진 근거';
COMMENT ON COLUMN analysis_field.initializer_summary IS '초기값 요약 (상수 추적용)';
COMMENT ON COLUMN analysis_field.file_id             IS '선언된 파일 ID';
COMMENT ON COLUMN analysis_field.line_start          IS '시작 줄';
COMMENT ON COLUMN analysis_field.line_end            IS '끝 줄';

COMMENT ON TABLE  analysis_annotation                IS '애노테이션이 붙은 자리 (어디에, 무엇이, 어떤 속성으로)';
COMMENT ON COLUMN analysis_annotation.annotation_id  IS '애노테이션 행 ID (일련번호)';
COMMENT ON COLUMN analysis_annotation.revision_id    IS '리비전 ID';
COMMENT ON COLUMN analysis_annotation.target_kind    IS '붙은 대상 종류: TYPE/METHOD/FIELD/PARAMETER';
COMMENT ON COLUMN analysis_annotation.target_id      IS '붙은 대상의 ID (symbol_id / method_id / field_id)';
COMMENT ON COLUMN analysis_annotation.annotation_name IS '단순 이름 (예: Transactional)';
COMMENT ON COLUMN analysis_annotation.annotation_fqn IS '풀렸으면 전체 이름';
COMMENT ON COLUMN analysis_annotation.attributes_json IS '속성 값 (JSON)';
COMMENT ON COLUMN analysis_annotation.file_id        IS '파일 ID';
COMMENT ON COLUMN analysis_annotation.line_start     IS '줄';


/**********************************************
9-4. 코멘트: 참조(아직 안 풀린 것)와 관계(풀린 것)
**********************************************/

COMMENT ON TABLE  analysis_reference                 IS 'DECLARE 가 모아 둔 참조. 이름만 알고 대상은 모른다. RESOLVE 가 풀어서 analysis_relation 으로 옮긴다';
COMMENT ON COLUMN analysis_reference.reference_id    IS '참조 ID (일련번호)';
COMMENT ON COLUMN analysis_reference.revision_id     IS '리비전 ID';
COMMENT ON COLUMN analysis_reference.file_id         IS '파일 ID';
COMMENT ON COLUMN analysis_reference.from_kind       IS '참조가 들어 있는 쪽의 종류: TYPE/METHOD/FIELD';
COMMENT ON COLUMN analysis_reference.from_id         IS '참조가 들어 있는 쪽의 ID';
COMMENT ON COLUMN analysis_reference.ref_kind        IS '참조 종류: CALL/CREATE/FIELD_ACCESS/TYPE_USE/THROWS/EXTENDS/IMPLEMENTS/ANONYMOUS_SUPER/METHOD_REF';
COMMENT ON COLUMN analysis_reference.name            IS '메소드/타입/필드 이름';
COMMENT ON COLUMN analysis_reference.scope_text      IS '호출 대상 식 (예: orderService, this.dao)';
COMMENT ON COLUMN analysis_reference.arg_count       IS '인자 개수';
COMMENT ON COLUMN analysis_reference.line_start      IS '줄';
COMMENT ON COLUMN analysis_reference.column_start    IS '칸. RESOLVE 에서 같은 AST 노드를 다시 찾는 키';
COMMENT ON COLUMN analysis_reference.status          IS '상태: PENDING/RESOLVED/EXTERNAL(프로젝트 밖)/HEURISTIC(짐작)/UNRESOLVED/IGNORED(풀 대상 아님)';
COMMENT ON COLUMN analysis_reference.fail_reason     IS '풀지 못한 이유';
COMMENT ON COLUMN analysis_reference.arg_text        IS '첫 인자를 소스에 적힌 그대로 (문자열이 들어 있을 때만). 예: "order.findAll"';
COMMENT ON COLUMN analysis_reference.args_json       IS '둘째 인자부터 따옴표 글자가 든 인자를 적힌 그대로 담은 JSON 배열 (첫 칸과 따옴표 없는 인자는 null)';

COMMENT ON TABLE  analysis_relation                  IS '관계: 그래프의 간선. 모든 관계 종류가 이 한 테이블에 들어간다';
COMMENT ON COLUMN analysis_relation.relation_id      IS '관계 ID (일련번호)';
COMMENT ON COLUMN analysis_relation.revision_id      IS '리비전 ID';
COMMENT ON COLUMN analysis_relation.from_kind        IS '출발 대상 종류: TYPE/METHOD/FIELD/SQL/FILE';
COMMENT ON COLUMN analysis_relation.from_id          IS '출발 대상 ID (SQL 은 S+mapper_id, 파일은 F+file_id)';
COMMENT ON COLUMN analysis_relation.relation_type    IS '관계 종류: CALLS/EXTENDS/IMPLEMENTS/OVERRIDES/INJECTS/EXECUTES_SQL/READS_TABLE/WRITES_TABLE/REQUESTS/RENDERS/...';
COMMENT ON COLUMN analysis_relation.to_kind          IS '도착 대상 종류: TYPE/METHOD/FIELD/SQL/FILE/TABLE, 프로젝트 밖이면 EXTERNAL_TYPE/EXTERNAL_METHOD/EXTERNAL_FIELD';
COMMENT ON COLUMN analysis_relation.to_id            IS '프로젝트 안의 도착 대상 ID. 밖이면 NULL';
COMMENT ON COLUMN analysis_relation.to_external      IS '프로젝트 밖 대상이나 테이블의 이름 (예: java.util.List.add(E))';
COMMENT ON COLUMN analysis_relation.confidence       IS '확실성: HIGH/MEDIUM/LOW/UNRESOLVED';
COMMENT ON COLUMN analysis_relation.resolution_status IS '해석 상태: RESOLVED/HEURISTIC/UNRESOLVED/DYNAMIC(리플렉션 등)';
COMMENT ON COLUMN analysis_relation.properties_json  IS '덧붙임 정보 (JSON)';
COMMENT ON COLUMN analysis_relation.file_id          IS '근거가 된 파일 ID. SQL 로 계산한 관계는 NULL 일 수 있다';
COMMENT ON COLUMN analysis_relation.line_start       IS '근거가 된 시작 줄';
COMMENT ON COLUMN analysis_relation.line_end         IS '근거가 된 끝 줄';


/**********************************************
9-5. 코멘트: 의미 분석 결과 (진입점 / 설정 / 매퍼 / 그 밖의 리소스)
**********************************************/

COMMENT ON TABLE  analysis_endpoint                  IS '진입점: 밖에서 이 프로그램으로 들어오는 입구 (HTTP 뿐 아니라 main, 스케줄 등도)';
COMMENT ON COLUMN analysis_endpoint.endpoint_id      IS '진입점 ID (일련번호)';
COMMENT ON COLUMN analysis_endpoint.revision_id      IS '리비전 ID';
COMMENT ON COLUMN analysis_endpoint.endpoint_type    IS '종류: HTTP/SERVLET/MAIN/SCHEDULED/LISTENER/JSP/THREAD/TRANSACTION';
COMMENT ON COLUMN analysis_endpoint.http_method      IS 'HTTP 메소드 (GET/POST/...)';
COMMENT ON COLUMN analysis_endpoint.path             IS 'URL 패턴. TRANSACTION 은 거래 ID. URL 이 없으면 NULL';
COMMENT ON COLUMN analysis_endpoint.symbol_id        IS '처리하는 타입의 ID';
COMMENT ON COLUMN analysis_endpoint.method_id        IS '처리하는 메소드의 ID';
COMMENT ON COLUMN analysis_endpoint.properties_json  IS '덧붙임 정보 (JSON. 예: inheritedFrom, unresolvedPath)';
COMMENT ON COLUMN analysis_endpoint.file_id          IS '파일 ID';
COMMENT ON COLUMN analysis_endpoint.line_start       IS '줄';

COMMENT ON TABLE  analysis_config                    IS '설정 값: application.yml / *.properties 의 키 하나하나';
COMMENT ON COLUMN analysis_config.config_id          IS '설정 행 ID (일련번호)';
COMMENT ON COLUMN analysis_config.revision_id        IS '리비전 ID';
COMMENT ON COLUMN analysis_config.key_path           IS '키 (예: spring.datasource.url)';
COMMENT ON COLUMN analysis_config.value              IS '값. 비밀값은 **** 로 가린다';
COMMENT ON COLUMN analysis_config.value_type         IS '값 종류: STRING/NUMBER/BOOLEAN/NULL/MASKED(가린 비밀값)';
COMMENT ON COLUMN analysis_config.placeholder        IS '값 안에 들어 있는 동적 변수 참조 (예: ${DB_HOST})';
COMMENT ON COLUMN analysis_config.file_id            IS '파일 ID';
COMMENT ON COLUMN analysis_config.line_start         IS '줄';

COMMENT ON TABLE  analysis_mapper                    IS 'SQL statement 하나하나: MyBatis/iBATIS/Query XML, Java 문자열 SQL, JPA 쿼리';
COMMENT ON COLUMN analysis_mapper.mapper_id          IS 'statement ID (일련번호). 그래프에서는 S+mapper_id';
COMMENT ON COLUMN analysis_mapper.revision_id        IS '리비전 ID';
COMMENT ON COLUMN analysis_mapper.mapper_type        IS '출처: MYBATIS/IBATIS/QUERY_XML/JAVA(Java 문자열 SQL)/JPA';
COMMENT ON COLUMN analysis_mapper.namespace          IS '네임스페이스';
COMMENT ON COLUMN analysis_mapper.statement_id       IS 'statement 이름';
COMMENT ON COLUMN analysis_mapper.statement_type     IS 'SQL 종류: SELECT/INSERT/UPDATE/DELETE/OTHER/SQL_FRAGMENT';
COMMENT ON COLUMN analysis_mapper.parameter_type     IS '파라미터 타입';
COMMENT ON COLUMN analysis_mapper.result_type        IS '결과 타입';
COMMENT ON COLUMN analysis_mapper.sql_body           IS 'SQL 본문';
COMMENT ON COLUMN analysis_mapper.method_id          IS '짝이 되는 Java 메소드 ID (찾았으면)';
COMMENT ON COLUMN analysis_mapper.file_id            IS '파일 ID';
COMMENT ON COLUMN analysis_mapper.line_start         IS '시작 줄';
COMMENT ON COLUMN analysis_mapper.line_end           IS '끝 줄';
COMMENT ON COLUMN analysis_mapper.properties_json    IS '덧붙임 정보 (JSON). JAVA·JPA 행의 소속 메소드·상수, 넘긴 호출, JPQL 여부 등';

COMMENT ON TABLE  analysis_resource                  IS '그 밖의 리소스 항목: Spring bean, 서블릿 매핑, 의존 라이브러리, 테이블 사전, DDL 등';
COMMENT ON COLUMN analysis_resource.resource_id      IS '리소스 ID (일련번호)';
COMMENT ON COLUMN analysis_resource.revision_id      IS '리비전 ID';
COMMENT ON COLUMN analysis_resource.resource_type    IS '종류: SPRING_BEAN/SERVLET_MAPPING/FILTER_MAPPING/DEPENDENCY/DDL_TABLE/DDL_COLUMN/DICT_TABLE_NAME/SCREEN_NAME/RULE_SET/...';
COMMENT ON COLUMN analysis_resource.name             IS '이름';
COMMENT ON COLUMN analysis_resource.location         IS '파일 안의 위치 (XPath 비슷한 경로)';
COMMENT ON COLUMN analysis_resource.value            IS '값';
COMMENT ON COLUMN analysis_resource.properties_json  IS '덧붙임 정보 (JSON)';
COMMENT ON COLUMN analysis_resource.file_id          IS '파일 ID. 소스 밖(테이블 사전 CSV 등)에서 온 행은 NULL';
COMMENT ON COLUMN analysis_resource.line_start       IS '줄';


/**********************************************
9-6. 코멘트: 품질 (오류 / 지표)
**********************************************/

COMMENT ON TABLE  analysis_error                     IS '분석 중 생긴 오류. 실패한 파일과 못 푼 심볼을 숨기지 않고 남긴다';
COMMENT ON COLUMN analysis_error.error_id            IS '오류 ID (일련번호)';
COMMENT ON COLUMN analysis_error.analysis_id         IS '분석 Job ID';
COMMENT ON COLUMN analysis_error.revision_id         IS '리비전 ID';
COMMENT ON COLUMN analysis_error.file_id             IS '파일 ID (파일과 상관없으면 NULL)';
COMMENT ON COLUMN analysis_error.pass                IS '오류가 난 단계';
COMMENT ON COLUMN analysis_error.error_type          IS '종류: PARSE_ERROR/ENCODING/CLASSPATH/RESOLVE_TIMEOUT/TOO_DEEP/...';
COMMENT ON COLUMN analysis_error.message             IS '오류 메시지';
COMMENT ON COLUMN analysis_error.detail              IS '자세한 내용 (스택 등)';
COMMENT ON COLUMN analysis_error.line_start          IS '줄';
COMMENT ON COLUMN analysis_error.created_at          IS '발생 시각';

COMMENT ON TABLE  analysis_metric                    IS '분석 품질 지표';
COMMENT ON COLUMN analysis_metric.analysis_id        IS '분석 Job ID';
COMMENT ON COLUMN analysis_metric.metric_name        IS '지표 이름: parseSuccessRate/typeResolutionRate/callResolutionRate/callHeuristicRate/callUnresolvedRate/callCount/relationCount';
COMMENT ON COLUMN analysis_metric.metric_value       IS '지표 값';
COMMENT ON COLUMN analysis_metric.created_at         IS '기록 시각';


/**********************************************
9-7. 코멘트: RAG (문서 / 청크 / 임베딩)
**********************************************/

COMMENT ON TABLE  rag_document                       IS 'RAG 논리 문서: 분석 결과에서 만든 것(CODE)과 사용자가 올린 일반 문서(DOCUMENT)';
COMMENT ON COLUMN rag_document.doc_seq               IS '문서 일련번호';
COMMENT ON COLUMN rag_document.document_id           IS '문서 ID. 같은 대상이면 리비전이 달라도 같은 값';
COMMENT ON COLUMN rag_document.source_type           IS '출처: CODE(분석 결과에서 생성) / DOCUMENT(업로드한 일반 문서)';
COMMENT ON COLUMN rag_document.tenant                IS '호출자별 격리 키. 다른 호출자의 문서가 검색에 섞이지 않게 한다';
COMMENT ON COLUMN rag_document.collection            IS '묶음 이름 (DOCUMENT 일 때만). 올린 문서를 묶어 검색 범위를 좁힌다';
COMMENT ON COLUMN rag_document.revision_id           IS '리비전 ID (CODE 일 때만). 프로젝트는 이 리비전으로 찾는다';
COMMENT ON COLUMN rag_document.doc_type              IS '문서 종류: FILE/TYPE/METHOD/MAPPER/VIEW/UPLOAD';
COMMENT ON COLUMN rag_document.ref_kind              IS '근거가 된 대상 종류 (TYPE/METHOD/FILE/SQL/DOCUMENT)';
COMMENT ON COLUMN rag_document.ref_id                IS '근거가 된 대상 ID';
COMMENT ON COLUMN rag_document.title                 IS '제목';
COMMENT ON COLUMN rag_document.source_path           IS '원본 경로';
COMMENT ON COLUMN rag_document.metadata_json         IS '덧붙임 정보 (JSON)';
COMMENT ON COLUMN rag_document.content_hash          IS '문서 내용의 해시';
COMMENT ON COLUMN rag_document.created_at            IS '생성 시각';

COMMENT ON TABLE  rag_chunk                          IS 'RAG 청크: 문서를 의미 단위로 자른 조각. 검색 결과로 돌려주는 단위';
COMMENT ON COLUMN rag_chunk.chunk_id                 IS '청크 ID (일련번호)';
COMMENT ON COLUMN rag_chunk.doc_seq                  IS '소속 문서 일련번호 (rag_document.doc_seq)';
COMMENT ON COLUMN rag_chunk.revision_id              IS '리비전 ID (코드 청크일 때)';
COMMENT ON COLUMN rag_chunk.tenant                   IS '호출자별 격리 키';
COMMENT ON COLUMN rag_chunk.chunk_no                 IS '문서 안에서의 순번';
COMMENT ON COLUMN rag_chunk.chunk_type               IS '청크 종류: FILE/TYPE/METHOD/MAPPER/VIEW/UPLOAD';
COMMENT ON COLUMN rag_chunk.content                  IS '청크 본문';
COMMENT ON COLUMN rag_chunk.content_hash             IS '본문의 SHA-256. 임베딩을 찾는 키';
COMMENT ON COLUMN rag_chunk.char_count               IS '글자 수';
COMMENT ON COLUMN rag_chunk.metadata_json            IS '덧붙임 정보 (JSON). 올린 문서는 headingPath(제목 경로), pageStart/pageEnd(PDF 쪽)';
COMMENT ON COLUMN rag_chunk.file_id                  IS '원본 파일 ID';
COMMENT ON COLUMN rag_chunk.line_start               IS '원본 시작 줄';
COMMENT ON COLUMN rag_chunk.line_end                 IS '원본 끝 줄';

COMMENT ON TABLE  rag_embedding                      IS 'RAG 임베딩. 키는 (내용 해시, 모델). 같은 내용은 한 번만 임베딩한다. status=PENDING 인 행이 곧 대기열';
COMMENT ON COLUMN rag_embedding.content_hash         IS '청크 본문의 SHA-256 (rag_chunk.content_hash)';
COMMENT ON COLUMN rag_embedding.model                IS '임베딩 모델 (예: bge-m3:latest)';
COMMENT ON COLUMN rag_embedding.dimensions           IS '벡터 차원';
COMMENT ON COLUMN rag_embedding.status               IS '상태: PENDING/DONE/FAILED';
COMMENT ON COLUMN rag_embedding.attempts             IS '시도 횟수';
COMMENT ON COLUMN rag_embedding.last_error           IS '마지막 실패 사유';
COMMENT ON COLUMN rag_embedding.embedding            IS '임베딩 벡터 (임베딩 전에는 NULL)';
COMMENT ON COLUMN rag_embedding.created_at           IS '대기열에 들어온 시각';
COMMENT ON COLUMN rag_embedding.embedded_at          IS '임베딩한 시각';
COMMENT ON COLUMN rag_embedding.priority             IS '우선순위. 큰 것부터 임베딩한다 (코드 0, 올린 일반 문서 10)';


/**********************************************
9-8. 코멘트: Knowledge Graph VIEW
**********************************************/

COMMENT ON VIEW   kg_node                            IS '그래프 노드 VIEW: 타입/메소드/필드/SQL/파일을 노드 모양으로 보여 준다';
COMMENT ON COLUMN kg_node.project_id                 IS '프로젝트 ID';
COMMENT ON COLUMN kg_node.revision_id                IS '리비전 ID';
COMMENT ON COLUMN kg_node.node_id                    IS '노드 ID (SQL 은 S+mapper_id, 파일은 F+file_id)';
COMMENT ON COLUMN kg_node.node_type                  IS '노드 종류: TYPE/METHOD/FIELD/SQL/FILE';
COMMENT ON COLUMN kg_node.sub_type                   IS '세부 종류: 타입의 kind, METHOD/CONSTRUCTOR, SQL 종류, 파일 언어';
COMMENT ON COLUMN kg_node.name                       IS '이름';
COMMENT ON COLUMN kg_node.fqn                        IS '전체 이름 (메소드는 타입fqn#시그니처)';
COMMENT ON COLUMN kg_node.properties_json            IS '덧붙임 정보 (JSON)';
COMMENT ON COLUMN kg_node.file_id                    IS '파일 ID';
COMMENT ON COLUMN kg_node.line_start                 IS '시작 줄';
COMMENT ON COLUMN kg_node.line_end                   IS '끝 줄';

COMMENT ON VIEW   kg_edge                            IS '그래프 간선 VIEW: analysis_relation 에 프로젝트 ID 를 붙여 보여 준다';
COMMENT ON COLUMN kg_edge.project_id                 IS '프로젝트 ID';
COMMENT ON COLUMN kg_edge.revision_id                IS '리비전 ID';
COMMENT ON COLUMN kg_edge.edge_id                    IS '간선 ID (analysis_relation.relation_id)';
COMMENT ON COLUMN kg_edge.from_node_id               IS '출발 노드 ID';
COMMENT ON COLUMN kg_edge.edge_type                  IS '간선 종류 (relation_type)';
COMMENT ON COLUMN kg_edge.to_node_id                 IS '도착 노드 ID. 프로젝트 밖이면 NULL';
COMMENT ON COLUMN kg_edge.to_external                IS '프로젝트 밖 대상이나 테이블의 이름';
COMMENT ON COLUMN kg_edge.confidence                 IS '확실성: HIGH/MEDIUM/LOW/UNRESOLVED';
COMMENT ON COLUMN kg_edge.resolution_status          IS '해석 상태';
COMMENT ON COLUMN kg_edge.properties_json            IS '덧붙임 정보 (JSON)';
COMMENT ON COLUMN kg_edge.file_id                    IS '근거가 된 파일 ID';
COMMENT ON COLUMN kg_edge.line_start                 IS '근거가 된 시작 줄';
COMMENT ON COLUMN kg_edge.line_end                   IS '근거가 된 끝 줄';
