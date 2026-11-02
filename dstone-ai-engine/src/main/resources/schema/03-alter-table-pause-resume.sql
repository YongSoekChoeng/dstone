/**********************************************
이미 만들어 둔 dstone_ai 데이터베이스에 일시정지 기능을 넣을 때 한 번 실행한다.
새로 설치할 때는 02-create-table-postgresql-dstone-ai.sql에 이미 들어 있으므로 필요 없다.
새 버전의 dstone-ai-engine을 띄우기 전에 실행해야 한다. 옛 버전은 이 컬럼을 쓰지 않으므로 실행해도 영향이 없다.
**********************************************/

ALTER TABLE AI_WORKFLOW_EXECUTION ADD COLUMN IF NOT EXISTS PAUSE_REQUESTED BOOLEAN NOT NULL DEFAULT FALSE;
