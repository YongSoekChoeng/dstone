-- Sample 데이터베이스[Application용] 생성
CREATE DATABASE IF NOT EXISTS sampleDB CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Sample 데이터베이스[Application용] 사용자 생성 및 권한 부여
CREATE USER IF NOT EXISTS 'sampleuser'@'%' IDENTIFIED BY 'sampleuser';
GRANT ALL PRIVILEGES ON sampleDB.* TO 'sampleuser'@'%';

-- 권한 적용
FLUSH PRIVILEGES;