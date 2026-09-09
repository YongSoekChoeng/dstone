/**********************************************
dstone-ai-engine RAG(Phase 2) VectorStore 테이블 (pgvector)
dstone_ai 데이터베이스 안에서 실행할 것 (01-init-postgresql-dstone-ai.sql로 먼저 생성/확장 활성화 필요).

주의: 이 테이블은 spring.ai.vectorstore.pgvector.initialize-schema: true로 설정되어 있으면
dstone-ai-engine이 최초 기동 시 아래와 동일한 내용을 자동으로 생성한다(CREATE TABLE/INDEX IF NOT EXISTS라
멱등적이라 이 스크립트를 미리 실행해 둬도 충돌하지 않는다). 다른 모듈(dstone-boot/dstone-batch/
dstone-batchadmin)의 initialize-schema: NEVER 컨벤션과 맞추고 싶다면, 이 스크립트를 먼저 실행한 뒤
application.yml에서 initialize-schema를 false로 바꾸면 된다.

컬럼/인덱스 정의는 실제 기동 중인 인스턴스에서 \d vector_store로 확인한 값 그대로다.
embedding 차원(1024)은 spring.ai.ollama.embedding.model(bge-m3)의 출력 차원과 반드시 일치해야
한다 - 임베딩 모델을 바꾸면 이 값과 spring.ai.vectorstore.pgvector.dimensions를 함께 바꾸고
테이블을 재생성해야 한다(차원이 다른 벡터는 기존 테이블에 섞어 넣을 수 없음).
**********************************************/

CREATE TABLE IF NOT EXISTS vector_store (
  id        uuid          NOT NULL DEFAULT uuid_generate_v4(),
  content   text,
  metadata  json,
  embedding vector(1024),
  PRIMARY KEY (id)
);

-- 코사인 유사도 검색용 HNSW 인덱스. 이름(spring_ai_vector_index)은 Spring AI PgVectorStore가
-- 고정으로 쓰는 이름과 동일하게 맞췄다.
CREATE INDEX IF NOT EXISTS spring_ai_vector_index
  ON vector_store USING hnsw (embedding vector_cosine_ops);
