/**********************************************
dstone_ai 기본 데이터(사용자 관리). 02-create-table-postgresql-dstone-ai.sql 다음에 실행한다.
여러 번 실행해도 이미 있는 행은 그대로 둔다(ON CONFLICT DO NOTHING). 운영 중 바꾼 메뉴·권한을 덮어쓰지 않는다.

초기 관리자: admin / admin1234 (첫 로그인 때 비밀번호를 바꿔야 한다, PW_CHANGE_YN='Y')
그룹과 권한
  ADMIN(관리자)  → 권한 ADMIN(관리자 표시)   → 관리자 메뉴 전부
  BIZ(현업)      → 권한 SM_REQUEST           → SM > 현업요청
  ITOPS(전산관리) → 권한 SM_OPS               → SM > 전산관리
  DEV(개발)      → 권한 SM_DEV               → SM > 개발관리
개요, 채팅은 로그인한 모두에게 보인다(ALL_USERS_YN='Y').
**********************************************/

INSERT INTO ai_management_user (USER_ID, USER_NAME, PASSWORD_HASH, USE_YN, PW_CHANGE_YN, CREATED_BY, UPDATED_BY) VALUES
    ('admin', '관리자', 'pbkdf2$120000$tPXLXzOLD21hdd/h7X7KHg==$PQJahIHgCe5UW7Tc2MHr5GqQnVpBsyu2un6pn+bMBHc=', 'Y', 'Y', 'system', 'system')
ON CONFLICT (USER_ID) DO NOTHING;

INSERT INTO ai_management_group (GROUP_ID, GROUP_NAME, DESCRIPTION, SORT_ORDER) VALUES
    ('ADMIN', '관리자', '관리자 메뉴 전부', 10),
    ('BIZ',   '현업', '업무 요청을 올리는 사람', 20),
    ('ITOPS', '전산관리', '요청을 검토·승인하는 전산 담당', 30),
    ('DEV',   '개발', '개발과 개발 승인 담당', 40)
ON CONFLICT (GROUP_ID) DO NOTHING;

INSERT INTO ai_management_auth (AUTH_ID, AUTH_NAME, DESCRIPTION, ADMIN_YN, SORT_ORDER) VALUES
    ('ADMIN',      '관리자', '관리자 메뉴, 남의 Workflow 실행 조작', 'Y', 10),
    ('SM_REQUEST', 'SM 현업요청', 'SM > 현업요청', 'N', 20),
    ('SM_OPS',     'SM 전산관리', 'SM > 전산관리', 'N', 30),
    ('SM_DEV',     'SM 개발관리', 'SM > 개발관리', 'N', 40)
ON CONFLICT (AUTH_ID) DO NOTHING;

INSERT INTO ai_management_menu (MENU_ID, PARENT_ID, MENU_NAME, URL, ICON, SORT_ORDER, USE_YN, ALL_USERS_YN) VALUES
    ('HOME',        NULL,    '개요',               '/ai/index.html',                      'home',      10, 'Y', 'Y'),
    ('CHAT',        NULL,    '채팅',               '/ai/chat/chat.html',                  'chat',      20, 'Y', 'Y'),
    ('SM',          NULL,    'SM',                 NULL,                                  NULL,        30, 'Y', 'N'),
    ('SM_REQUEST',  'SM',    '현업요청',            '/ai/sm/request.html',                 'book',      31, 'Y', 'N'),
    ('SM_OPS',      'SM',    '전산관리',            '/ai/sm/ops.html',                     'list',      32, 'Y', 'N'),
    ('SM_DEV',      'SM',    '개발관리',            '/ai/sm/dev.html',                     'tool',      33, 'Y', 'N'),
    ('ADMIN',       NULL,    '관리자',              NULL,                                  NULL,        90, 'Y', 'N'),
    ('WF_TEST',     'ADMIN', 'Workflow 테스트',     '/ai/workflow/workflow.html',          'play',      91, 'Y', 'N'),
    ('WF_EXEC',     'ADMIN', 'Workflow 실행관리',   '/ai/admin/workflow/workflow.html',    'list',      92, 'Y', 'N'),
    ('WF_DESIGNER', 'ADMIN', 'Workflow 디자이너',   '/ai/designer/designer.html',          'flow',      93, 'Y', 'N'),
    ('EVAL',        'ADMIN', 'Agent 평가',          '/ai/admin/eval/eval.html',            'diff',      94, 'Y', 'N'),
    ('MG_USER',     'ADMIN', '사용자관리',          '/ai/admin/management/user.html',      'user',      95, 'Y', 'N'),
    ('MG_GROUP',    'ADMIN', '사용자그룹관리',      '/ai/admin/management/group.html',     'user',      96, 'Y', 'N'),
    ('MG_MENU',     'ADMIN', '메뉴관리',            '/ai/admin/management/menu.html',      'list',      97, 'Y', 'N'),
    ('MG_AUTH',     'ADMIN', '권한관리',            '/ai/admin/management/auth.html',      'tool',      98, 'Y', 'N'),
    ('MG_PROJECT',  'ADMIN', '프로젝트관리',        '/ai/admin/management/project.html',   'folder',    99, 'Y', 'N')
ON CONFLICT (MENU_ID) DO NOTHING;

INSERT INTO ai_management_auth_menu (AUTH_ID, MENU_ID) VALUES
    ('ADMIN', 'WF_TEST'), ('ADMIN', 'WF_EXEC'), ('ADMIN', 'WF_DESIGNER'), ('ADMIN', 'EVAL'),
    ('ADMIN', 'MG_USER'), ('ADMIN', 'MG_GROUP'), ('ADMIN', 'MG_MENU'), ('ADMIN', 'MG_AUTH'), ('ADMIN', 'MG_PROJECT'),
    ('SM_REQUEST', 'SM_REQUEST'), ('SM_OPS', 'SM_OPS'), ('SM_DEV', 'SM_DEV')
ON CONFLICT DO NOTHING;

INSERT INTO ai_management_group_auth (GROUP_ID, AUTH_ID) VALUES
    ('ADMIN', 'ADMIN'), ('BIZ', 'SM_REQUEST'), ('ITOPS', 'SM_OPS'), ('DEV', 'SM_DEV')
ON CONFLICT DO NOTHING;

INSERT INTO ai_management_group_user (GROUP_ID, USER_ID) VALUES
    ('ADMIN', 'admin')
ON CONFLICT DO NOTHING;
