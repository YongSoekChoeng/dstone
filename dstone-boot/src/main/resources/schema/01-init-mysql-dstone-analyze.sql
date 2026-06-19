-- Analyze 데이터베이스[Application용] 생성
CREATE DATABASE IF NOT EXISTS analyzeDB CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Analyze 데이터베이스[Application용] 사용자 생성 및 권한 부여
CREATE USER IF NOT EXISTS 'analyzeuser'@'%' IDENTIFIED BY 'analyzeuser';
GRANT ALL PRIVILEGES ON analyzeDB.* TO 'analyzeuser'@'%';

-- 권한 적용
FLUSH PRIVILEGES;

/**********************************************
신규 테이블 / 컬럼 추가 DDL
**********************************************/

USE analyzeDB;

-- TB_FUNC 에 메서드내용 컬럼 추가 (기존 테이블이 이미 존재하는 경우를 위한 ALTER)
ALTER TABLE TB_FUNC ADD COLUMN IF NOT EXISTS MTD_BODY LONGTEXT COMMENT '메서드내용' AFTER MTD_URL;

-- 쿼리[TB_QUERY] 테이블 신규 생성
CREATE TABLE IF NOT EXISTS TB_QUERY (
  SYS_ID       VARCHAR(20)  NOT NULL COMMENT '시스템ID',
  SQL_KEY      VARCHAR(500) NOT NULL COMMENT '쿼리KEY(네임스페이스_SQL아이디)',
  SQL_NAMESPACE VARCHAR(300) COMMENT '네임스페이스',
  SQL_ID       VARCHAR(300) COMMENT 'SQL아이디',
  SQL_KIND     VARCHAR(10)  COMMENT 'SQL종류(SELECT/INSERT/UPDATE/DELETE)',
  SQL_BODY     LONGTEXT     COMMENT 'SQL구문',
  CALL_TBL_LIST TEXT        COMMENT '호출테이블목록(콤마구분)',
  WORKER_ID    VARCHAR(10)  NOT NULL COMMENT '입력자ID',
  PRIMARY KEY (SYS_ID, SQL_KEY)
) COMMENT '쿼리';