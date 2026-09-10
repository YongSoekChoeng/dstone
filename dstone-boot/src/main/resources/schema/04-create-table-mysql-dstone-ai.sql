/**********************************************
AI(dstone-ai-engine 연동) 관련 테이블

TB_AI_DOCUMENT는 dstone-ai-engine의 pgvector에 실제로 적재된 상태와는 별개로, dstone-boot 쪽에서
"무엇을 업로드했는지" 화면에 보여주기 위한 자체 메타데이터다. dstone-ai-engine의 RagController는
적재된 sourceId 목록을 조회하는 API가 없어(업로드/삭제/검색만 제공) 이 테이블로 대신한다.
**********************************************/

USE sampleDB;

CREATE TABLE IF NOT EXISTS TB_AI_DOCUMENT (
  SOURCE_ID     VARCHAR(100)  NOT NULL,
  FILE_NAME     VARCHAR(255)  NOT NULL,
  CHUNK_COUNT   INT           NOT NULL DEFAULT 0,
  UPLOADER_ID   VARCHAR(30)   NOT NULL,
  INPUT_DT      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (SOURCE_ID)
) ;
