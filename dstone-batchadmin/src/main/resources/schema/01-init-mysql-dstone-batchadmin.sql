SET GLOBAL validate_password.policy = LOW;
SET GLOBAL validate_password.length = 4;

-- dstone-batchadmin 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS batchadmin CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- dstone-batchadmin 데이터베이스 사용자 생성 및 권한 부여
CREATE USER IF NOT EXISTS 'batchadmin'@'%' IDENTIFIED BY 'batchadmin123';
GRANT ALL PRIVILEGES ON batchadmin.* TO 'batchadmin'@'%';

-- 권한 적용
FLUSH PRIVILEGES;
