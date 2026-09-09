-- dstone-ai-engine RAG(Phase 2) 전용 롤/데이터베이스 초기화.
-- postgres 슈퍼유저 권한으로 실행해야 한다: sudo -u postgres psql -f 01-init-postgresql-dstone-ai.sql
-- 상세 배경: docs/software/postgresql.md 6절, docs/dstone-ai-engine.md 9절 참고.

-- dstone_ai 롤(앱 전용 계정) 생성 - 이미 있으면 건너뜀
DO $$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'dstone_ai') THEN
      -- CHANGE_ME: 실제 비밀번호로 바꿔서 실행할 것. dstone-ai-engine/conf/application.yml의
      -- spring.datasource.password는 이 값을 Jasypt로 암호화해서 ENC(...) 형태로 넣는다:
      --   cd dstone-common && mvn -q exec:java -Dexec.mainClass=net.dstone.common.utils.EncUtil -Dexec.args="<실제 비밀번호>"
      CREATE ROLE dstone_ai LOGIN PASSWORD 'CHANGE_ME';
   END IF;
END
$$;

-- dstone_ai 데이터베이스 생성 - 이미 있으면 건너뜀 (\gexec가 조건에 맞을 때만 CREATE DATABASE를 실행)
SELECT 'CREATE DATABASE dstone_ai OWNER dstone_ai'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'dstone_ai')\gexec

-- 아래부터는 dstone_ai 데이터베이스 안에서 실행되어야 한다(psql -f로 이 파일 전체를 실행하면
-- \c 이후 이어지는 명령들이 자동으로 그 접속 세션을 이어받는다).
\c dstone_ai

-- pgvector: RAG VectorStore(02-create-table-postgresql-dstone-ai.sql)가 사용하는 vector 타입/HNSW 인덱스.
-- postgresql-18-pgvector 패키지가 미리 설치되어 있어야 한다(docs/software/postgresql.md 7.2절 참고).
CREATE EXTENSION IF NOT EXISTS vector;

-- uuid-ossp: vector_store.id의 기본값(uuid_generate_v4())에 필요.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- hstore: PgVectorStoreAutoConfiguration이 스키마 초기화 시 함께 활성화하는 확장(현재 dstone-ai-engine이
-- 직접 사용하는 곳은 없지만, 실제 운영 중인 인스턴스의 확장 목록과 맞춰 두었다).
CREATE EXTENSION IF NOT EXISTS hstore;
