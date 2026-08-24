
/**********************************************
dstone-batchadmin 데이터베이스[MySQL] 테이블
**********************************************/
USE batchadmin;

-- 관리자 로그인 계정
CREATE TABLE IF NOT EXISTS TB_ADMIN_USER (
    USER_ID         VARCHAR(50)     NOT NULL PRIMARY KEY,
    USER_PW         VARCHAR(200)    NOT NULL,   -- BCrypt 해시
    USER_NM         VARCHAR(100)    NOT NULL,
    USE_YN          CHAR(1)         NOT NULL DEFAULT 'Y',
    LAST_LOGIN_DT   DATETIME        NULL,
    REG_DT          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
) ;

-- 관리대상 dstone-batch 서버 레지스트리
CREATE TABLE IF NOT EXISTS TB_BATCH_SERVER (
    SERVER_ID       BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    SERVER_NM       VARCHAR(100)    NOT NULL,
    REST_BASE_URL   VARCHAR(300)    NOT NULL,   -- 예: http://localhost:6081/batch
    DB_HOST         VARCHAR(200)    NOT NULL,
    DB_PORT         VARCHAR(10)     NOT NULL,
    DB_NAME         VARCHAR(100)    NOT NULL,
    DB_USER         VARCHAR(100)    NOT NULL,
    DB_PASSWORD     VARCHAR(300)    NOT NULL,   -- ENC(...) 암호화 저장
    DBMS_TYPE       VARCHAR(20)     NOT NULL DEFAULT 'MYSQL',  -- MYSQL/POSTGRES
    USE_YN          CHAR(1)         NOT NULL DEFAULT 'Y',
    DESCRIPTION     VARCHAR(500)    NULL,
    REG_DT          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATE_DT       DATETIME        NULL
) ;

-- 배치Job 메타데이터/스케줄 정의 (실제 Job 로직은 dstone-batch측 @AutoRegJob 빈으로 존재)
CREATE TABLE IF NOT EXISTS TB_BATCH_JOB (
    JOB_ID              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    JOB_NM              VARCHAR(200)    NOT NULL,   -- dstone-batch @AutoRegJob(name=...) 값과 일치해야 함
    SERVER_ID           BIGINT          NOT NULL,
    DESCRIPTION         VARCHAR(500)    NULL,
    CRON_EXPRESSION     VARCHAR(100)    NULL,        -- Spring CronTrigger 형식 (초 단위 포함, 예: 0 0 1 * * *)
    SCHEDULE_USE_YN     CHAR(1)         NOT NULL DEFAULT 'N',
    OWNER_NM            VARCHAR(100)    NULL,
    USE_YN              CHAR(1)         NOT NULL DEFAULT 'Y',
    REG_DT              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATE_DT           DATETIME        NULL,
    CONSTRAINT UK_TB_BATCH_JOB_NM_SERVER UNIQUE (JOB_NM, SERVER_ID),
    CONSTRAINT FK_TB_BATCH_JOB_SERVER FOREIGN KEY (SERVER_ID) REFERENCES TB_BATCH_SERVER(SERVER_ID)
) ;

-- 기본 관리자계정(ID:admin / PW:admin1234) - 최초 로그인 후 비밀번호 변경 권장
INSERT INTO TB_ADMIN_USER (USER_ID, USER_PW, USER_NM, USE_YN)
SELECT 'admin', '$2a$10$AOlFfDbPg.M/22bbSxB/xe4TKkySxM2szq7yZvaRGgg/jXir4ZZ7a', '관리자', 'Y'
WHERE NOT EXISTS (SELECT 1 FROM TB_ADMIN_USER WHERE USER_ID = 'admin');
