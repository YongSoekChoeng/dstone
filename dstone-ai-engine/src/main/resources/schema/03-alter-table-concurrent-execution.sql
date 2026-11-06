/**********************************************
2026-11-07 변경분. 02-create-table-postgresql-dstone-ai.sql로 이미 만든 dstone_ai 데이터베이스에 한 번 실행한다.
(새로 만드는 DB는 02 파일에 이미 들어 있으므로 실행하지 않아도 된다. 두 번 실행해도 문제없다.)

여러 개발자가 동시에 Workflow를 돌리기 위한 변경이다.
- REQUESTED_BY: 실행을 시작한 사용자. 실행 관리 화면의 "내 실행만"과 조작 권한(본인 또는 관리자)에 쓴다.
- OWNER_INSTANCE / HEARTBEAT_AT: 실행을 맡은 엔진 인스턴스와 그 인스턴스가 살아 있다고 마지막으로 알린 시각.
  heartbeat가 끊긴 실행만 실패로 바꾸므로 엔진을 여러 대 띄워도 서로의 실행을 건드리지 않는다.
- 목록 정렬(CREATED_AT)과 "내 실행만" 조회용 인덱스.
- STATUS에 QUEUED(빈 실행 슬롯을 기다리는 중)가 새로 생긴다. 컬럼 형식은 그대로다.
**********************************************/

ALTER TABLE AI_WORKFLOW_EXECUTION ADD COLUMN IF NOT EXISTS REQUESTED_BY VARCHAR(100);
ALTER TABLE AI_WORKFLOW_EXECUTION ADD COLUMN IF NOT EXISTS OWNER_INSTANCE VARCHAR(100);
ALTER TABLE AI_WORKFLOW_EXECUTION ADD COLUMN IF NOT EXISTS HEARTBEAT_AT TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS IDX_AI_WORKFLOW_EXECUTION_CREATED ON AI_WORKFLOW_EXECUTION(CREATED_AT DESC, EXECUTION_ID);
CREATE INDEX IF NOT EXISTS IDX_AI_WORKFLOW_EXECUTION_REQUESTED_BY ON AI_WORKFLOW_EXECUTION(REQUESTED_BY, CREATED_AT DESC);
