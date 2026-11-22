/**********************************************
dstone_knowledge 기본 데이터(사용자 관리). 02-create-table-postgresql-dstone-knowledge.sql 다음에 실행한다.
여러 번 실행해도 이미 있는 행은 그대로 둔다(ON CONFLICT DO NOTHING). 운영 중 바꾼 메뉴·권한을 덮어쓰지 않는다.

초기 관리자: admin / admin1234 (첫 로그인 때 비밀번호를 바꿔야 한다, PW_CHANGE_YN='Y')
그룹과 권한
  ADMIN(관리자) → 권한 ADMIN(관리자 표시) + VIEWER
  USER(사용자)  → 권한 VIEWER
  ADMIN  → 관리 > 분석 실행 · 진단, 관리자 메뉴 전부
  VIEWER → 현행 파악(프로젝트, 노드 맵, 검색 · 문서), 변경 영향(영향도, 리비전 비교)
대시보드와 사용 안내는 로그인한 모두에게 보인다(ALL_USERS_YN='Y').
**********************************************/

INSERT INTO knowledge_management_user (USER_ID, USER_NAME, PASSWORD_HASH, USE_YN, PW_CHANGE_YN, CREATED_BY, UPDATED_BY) VALUES
    ('admin', '관리자', 'pbkdf2$120000$tPXLXzOLD21hdd/h7X7KHg==$PQJahIHgCe5UW7Tc2MHr5GqQnVpBsyu2un6pn+bMBHc=', 'Y', 'Y', 'system', 'system')
ON CONFLICT (USER_ID) DO NOTHING;

INSERT INTO knowledge_management_group (GROUP_ID, GROUP_NAME, DESCRIPTION, SORT_ORDER) VALUES
    ('ADMIN', '관리자', '분석 실행과 관리자 메뉴', 10),
    ('USER',  '사용자', '분석 결과를 보는 사람', 20)
ON CONFLICT (GROUP_ID) DO NOTHING;

INSERT INTO knowledge_management_auth (AUTH_ID, AUTH_NAME, DESCRIPTION, ADMIN_YN, SORT_ORDER) VALUES
    ('ADMIN',  '관리자', '분석 실행 · 진단, 관리자 메뉴', 'Y', 10),
    ('VIEWER', '분석 결과 보기', '현행 파악, 변경 영향', 'N', 20)
ON CONFLICT (AUTH_ID) DO NOTHING;

INSERT INTO knowledge_management_menu (MENU_ID, PARENT_ID, MENU_NAME, URL, ICON, SORT_ORDER, USE_YN, ALL_USERS_YN) VALUES
    ('DASHBOARD', NULL,       '대시보드',          '/knowledge/dashboard/dashboard.html', 'dashboard', 10, 'Y', 'Y'),
    ('ASIS',      NULL,       '현행 파악',         NULL,                                  NULL,        20, 'Y', 'N'),
    ('PROJECT',   'ASIS',     '프로젝트',          '/knowledge/project/project.html',     'folder',    21, 'Y', 'N'),
    ('GRAPH',     'ASIS',     '노드 맵',           '/knowledge/graph/graph.html',         'graph',     22, 'Y', 'N'),
    ('SEARCH',    'ASIS',     '검색 · 문서',       '/knowledge/search/search.html',       'search',    23, 'Y', 'N'),
    ('CHANGE',    NULL,       '변경 영향',         NULL,                                  NULL,        30, 'Y', 'N'),
    ('IMPACT',    'CHANGE',   '영향도',            '/knowledge/impact/impact.html',       'impact',    31, 'Y', 'N'),
    ('DIFF',      'CHANGE',   '리비전 비교',       '/knowledge/diff/diff.html',           'diff',      32, 'Y', 'N'),
    ('MANAGE',    NULL,       '관리',              NULL,                                  NULL,        40, 'Y', 'N'),
    ('ANALYSIS',  'MANAGE',   '분석 실행 · 진단',  '/knowledge/index.html',               'tool',      41, 'Y', 'N'),
    ('GUIDE',     'MANAGE',   '사용 안내',         '/knowledge/guide/guide.html',         'book',      42, 'Y', 'Y'),
    ('ADMIN',     NULL,       '관리자',            NULL,                                  NULL,        90, 'Y', 'N'),
    ('MG_USER',   'ADMIN',    '사용자관리',        '/knowledge/admin/management/user.html',  'user',   91, 'Y', 'N'),
    ('MG_GROUP',  'ADMIN',    '사용자그룹관리',    '/knowledge/admin/management/group.html', 'user',   92, 'Y', 'N'),
    ('MG_MENU',   'ADMIN',    '메뉴관리',          '/knowledge/admin/management/menu.html',  'list',   93, 'Y', 'N'),
    ('MG_AUTH',   'ADMIN',    '권한관리',          '/knowledge/admin/management/auth.html',  'tool',   94, 'Y', 'N')
ON CONFLICT (MENU_ID) DO NOTHING;

INSERT INTO knowledge_management_auth_menu (AUTH_ID, MENU_ID) VALUES
    ('ADMIN', 'ANALYSIS'), ('ADMIN', 'MG_USER'), ('ADMIN', 'MG_GROUP'), ('ADMIN', 'MG_MENU'), ('ADMIN', 'MG_AUTH'),
    ('VIEWER', 'PROJECT'), ('VIEWER', 'GRAPH'), ('VIEWER', 'SEARCH'), ('VIEWER', 'IMPACT'), ('VIEWER', 'DIFF')
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_management_group_auth (GROUP_ID, AUTH_ID) VALUES
    ('ADMIN', 'ADMIN'), ('ADMIN', 'VIEWER'), ('USER', 'VIEWER')
ON CONFLICT DO NOTHING;

INSERT INTO knowledge_management_group_user (GROUP_ID, USER_ID) VALUES
    ('ADMIN', 'admin')
ON CONFLICT DO NOTHING;
