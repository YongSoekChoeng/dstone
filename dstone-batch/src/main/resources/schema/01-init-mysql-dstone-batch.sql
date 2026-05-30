-- Dataflow 데이터베이스[배치관리서버용] 생성
CREATE DATABASE IF NOT EXISTS dataflow CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Dataflow 데이터베이스[배치관리서버용] 사용자 생성 및 권한 부여
CREATE USER IF NOT EXISTS 'dataflow'@'%' IDENTIFIED BY 'dataflow';
GRANT ALL PRIVILEGES ON dataflow.* TO 'dataflow'@'%';

-- 권한 적용
FLUSH PRIVILEGES;