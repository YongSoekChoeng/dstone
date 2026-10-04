-- dstone-knowledge 전용 롤/데이터베이스 초기화.
-- postgres 슈퍼유저 권한으로 실행해야 한다: sudo -u postgres psql -f 01-init-postgresql-dstone-knowledge.sql
-- 상세 배경: docs/software/05.postgresql.md, docs/11.dstone-knowledge.md 참고.

-- dstone_knowledge 롤(앱 전용 계정) 생성 - 이미 있으면 건너뜀
DO $$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'dstone_knowledge') THEN
      -- CHANGE_ME: 실제 비밀번호로 바꿔서 실행할 것. dstone-knowledge/conf/application.yml의
      -- spring.datasource.common.hikari.password는 이 값을 Jasypt로 암호화해서 ENC(...) 형태로 넣는다:
      --   cd dstone-common && mvn -q exec:java -Dexec.mainClass=net.dstone.common.utils.EncUtil -Dexec.args="<실제 비밀번호>"
      CREATE ROLE dstone_knowledge LOGIN PASSWORD 'CHANGE_ME';
   END IF;
END
$$;

-- dstone_knowledge 데이터베이스 생성 - 이미 있으면 건너뜀 (\gexec가 조건에 맞을 때만 CREATE DATABASE를 실행)
SELECT 'CREATE DATABASE dstone_knowledge OWNER dstone_knowledge'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'dstone_knowledge')\gexec

-- 아래부터는 dstone_knowledge 데이터베이스 안에서 실행되어야 한다(psql -f로 이 파일 전체를 실행하면
-- \c 이후 이어지는 명령들이 자동으로 그 접속 세션을 이어받는다).
\c dstone_knowledge

-- pgvector: rag_embedding 테이블의 vector 타입/HNSW 인덱스에 필요.
-- postgresql-18-pgvector 패키지가 미리 설치되어 있어야 한다(docs/software/05.postgresql.md 7.2절 참고).
CREATE EXTENSION IF NOT EXISTS vector;
