/**********************************************
이미 만들어 둔 dstone_ai 데이터베이스의 "step별 파일 기록" 테이블에 CREATED 열을 넣을 때 한 번 실행한다.
새로 설치할 때는 02-create-table-postgresql-dstone-ai.sql에 이미 들어 있으므로 필요 없다.

CREATED = 그 step이 없던 파일을 새로 만들었는지(WRITE일 때만 의미가 있다).
재실행([여기서 다시]) 때 되돌리는 구간의 step이 새로 만든 파일을 실제로 지우는 데 쓴다.
이 열이 생기기 전의 기록은 NULL이고, 그런 기록은 md 문서만 지운다(소스 파일은 원래 있던 파일일 수 있어서).

새 버전의 dstone-ai-engine을 띄우기 전에 실행한다. 이 열이 없으면 파일 기록과 문서 목록 조회가 실패한다.
**********************************************/
ALTER TABLE AI_WORKFLOW_EXECUTION_FILE ADD COLUMN IF NOT EXISTS CREATED BOOLEAN;
