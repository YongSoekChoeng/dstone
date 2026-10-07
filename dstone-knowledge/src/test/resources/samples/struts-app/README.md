# struts-app (Struts 1 + iBATIS + JSP 검증 표본)

`dstone-knowledge`가 **Java 밖에 적힌 것까지 이어서 따라가는지** 확인하려고 만든 작은 표본이다.
주소는 `struts-config.xml`에, SQL은 iBATIS `sqlMap`에, 화면은 JSP에 있다. 실제 앱 두 개(`anybiz_prd`, `cms4_prd`)에는 Struts와 iBATIS가 없어서 따로 만들었다.
분석기의 입력으로만 쓴다. Struts / Spring jar가 없으므로 컴파일되지 않는다(분석은 jar 없이 끝나야 한다).

## 들어 있는 것

| 무엇 | 어디에 | 기대하는 동작 |
|---|---|---|
| 주소 → 액션 클래스 | `struts-config.xml`의 `/board/list`, `/board/save` + `web.xml`의 `*.do` | HTTP 진입점 `/board/list.do`, `/board/save.do`. 처리 메소드는 `execute` |
| 클래스 없이 화면으로 바로 넘기는 액션 | `/board/edit` (`forward="/board/edit.jsp"`) | 진입점 `/board/edit.do`는 생기지만 타입과 메소드는 없다 |
| 요청 파라미터로 메소드를 고르는 액션 | `/board/admin` (`BoardAdminAction extends DispatchAction`, `parameter="cmd"`) | 진입점 `/board/admin.do`가 타입까지만 이어진다. 메소드는 비어 있다 |
| 끝나고 가는 화면 | `forward`의 `path` | JSP로 가는 것만 `RENDERS`. `redirect="true"`로 다른 주소(`/board/list.do`)에 보내는 것과, 파일이 없는 `global-forwards`의 `/common/error.jsp`는 잇지 않는다 |
| SQL 이름을 상수와 이어 붙임 | `BoardDAO`: `NS + "selectBoardList"` (`NS = "Board."`) | 상수 값을 계산해 `Board.selectBoardList`와 잇는다 |
| 네임스페이스 없이 `id`만 넘김 | `BoardDAO.countBoard()`: `queryForObject("countBoard")` | 그 `id`의 statement가 하나뿐이라 잇는다 |
| 문자열 그대로 | `BoardDAO.deleteBoard()`: `"Board.deleteBoard"` | 잇는다 |
| SQL 조각과 조건 태그 | `board.xml`: `<include refid="boardColumns"/>`, `<dynamic prepend="WHERE">`, `<isNotEmpty prepend="AND">` | 조각을 채우고 태그를 벗긴 뒤 SQL 파서로 읽힌다(`HIGH`). 테이블은 `TB_BOARD`, `TB_USER` |
| 화면 안의 주소 | `common/header.jsp`의 `/board/list.do`, `board/edit.jsp`의 `/board/save.do` | 진입점 주소와 같으므로 `REQUESTS` |
| 화면 끼워 넣기 | `list.jsp`의 `<jsp:include page="/common/header.jsp"/>`, `edit.jsp`의 `<%@ include file="../common/header.jsp" %>` | 절대 경로와 상대 경로 모두 `INCLUDES` |

## 기대 값

| 항목 | 값 |
|---|---|
| Job 상태 | `DONE` (오류 0) |
| 파일 | 11: Java 5, JSP 3, iBATIS 매퍼 1, Struts 설정 1, `web.xml` 1 |
| JSP에서 만든 Java | 0 (JSP 3개 모두 스크립틀릿이 없어 DECLARE에서 건너뛴다) |
| SQL statement (`analysis_mapper`) | 5: `SELECT` 2, `INSERT` 1, `DELETE` 1, 조각(`SQL_FRAGMENT`) 1 |
| 진입점 | `HTTP` 4 (`/board/admin.do`, `/board/edit.do`, `/board/list.do`, `/board/save.do`), `SERVLET` 1 (`*.do`), `JSP` 3 |
| 계층 | 액션 3개 `CONTROLLER`(`HIGH`, Struts 설정에서), `BoardDAO` `REPOSITORY`(`LOW`, 이름), `BoardVO` `MODEL`(`LOW`, 이름) |
| `EXECUTES_SQL` | 4 (`BoardDAO`의 메소드 4개가 하나씩, 모두 `HIGH`) |
| `READS_TABLE` | 3 (`selectBoardList` → `TB_BOARD`, `TB_USER` / `countBoard` → `TB_BOARD`) |
| `WRITES_TABLE` | 2 (`insertBoard` → `TB_BOARD` `crud=C` / `deleteBoard` → `TB_BOARD` `crud=D`) |
| `RENDERS` | 2 (`BoardListAction#execute` → `board/list.jsp`, `BoardSaveAction#execute` → `board/edit.jsp`) |
| `REQUESTS` | 2 (`common/header.jsp` → `BoardListAction#execute`, `board/edit.jsp` → `BoardSaveAction#execute`) |
| `INCLUDES` | 2 (`board/list.jsp`, `board/edit.jsp` → `common/header.jsp`) |
| 검색 문서 | 24: `METHOD` 7, `TYPE` 5, `FILE` 5, `MAPPER` 4, `VIEW` 3 |

## 한 줄로 이어지는지

- `/board/list.do`의 처리 메소드(`BoardListAction#execute`)에서 피호출자를 깊이 3으로 조회하면 여는 화면 `board/list.jsp`(깊이 1), `BoardDAO`의 `selectBoardList` / `countBoard`가 실행하는 SQL 2개(깊이 2), 그 SQL이 읽는 테이블 `TB_BOARD`, `TB_USER`(깊이 3, `READS_TABLE` 3건)까지 나온다.
- `GET /api/revisions/{id}/tables/TB_BOARD`는 statement 4개와 그것을 실행하는 `BoardDAO`의 메소드 4개를 돌려준다.

## 검색 (임베딩이 끝난 뒤)

| 질문 | 1위 |
|---|---|
| 게시글을 삭제하는 SQL | `MAPPER` `Board.deleteBoard` |
| 게시글 목록 화면 | `VIEW` `board/list.jsp` |
| 게시글 저장 요청을 받는 곳 | `TYPE` `com.sample.board.web.BoardSaveAction` |
