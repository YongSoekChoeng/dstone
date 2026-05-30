-- Analyze 데이터베이스[Application용] 생성
CREATE DATABASE IF NOT EXISTS analyzeDB CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Analyze 데이터베이스[Application용] 사용자 생성 및 권한 부여
CREATE USER IF NOT EXISTS 'analyzeuser'@'%' IDENTIFIED BY 'analyzeuser';
GRANT ALL PRIVILEGES ON analyzeDB.* TO 'analyzeuser'@'%';

-- 권한 적용
FLUSH PRIVILEGES;