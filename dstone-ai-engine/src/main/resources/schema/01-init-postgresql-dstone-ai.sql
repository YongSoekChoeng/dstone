-- dstone-ai-engine 전용 롤/데이터베이스 초기화. 이 DB에는 Workflow 실행 이력이 들어간다(테이블은 02-create-table-postgresql-dstone-ai.sql).
-- postgres 슈퍼유저 권한으로 실행해야 한다: sudo -u postgres psql -f 01-init-postgresql-dstone-ai.sql
-- 상세 배경: docs/software/05.postgresql.md 6절, docs/09.dstone-ai-engine.md 참고.

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

-- 확장(vector / uuid-ossp / hstore)은 만들지 않는다. 예전에 이 모듈이 RAG 문서를 직접 저장할 때 쓰던 것인데,
-- 지금은 문서의 저장과 검색을 dstone-knowledge가 맡는다.
