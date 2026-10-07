# websquare-app (WebSquare 화면 + 쿼리 XML 검증 표본)

`dstone-knowledge`가 **리치클라이언트 화면(WebSquare)을 서버 쪽 메소드와 잇는지** 확인하려고 만든 작은 표본이다.
WebSquare 화면은 JSP와 달리 서버가 열어 주지 않고, 화면이 떠서 데이터만 주고받는다. 그래서 화면과 Java를 잇는 것은 "화면이 무엇을 부르나"(`REQUESTS`) 하나뿐이다.
분석기의 입력으로만 쓴다. WebSquare 엔진도 Spring jar도 없다(분석은 jar 없이 끝나야 한다).

실제 프로젝트(`HI-Japan`)에서 본 모양을 그대로 줄여 담았다. 그 프로젝트는 표준 `submission`을 하나도 쓰지 않고, 화면이 공통 함수에 **거래 ID**를 넘겨 서버를 부른다. SQL도 MyBatis가 아니라 JEF 계열 프레임워크의 **쿼리 XML**(`<document><query><statement>`)에 있다. 그래서 이 표본 하나로 화면 → 메소드 → SQL → 테이블이 한 줄로 이어지는지 본다.

## 들어 있는 것

| 무엇 | 어디에 | 기대하는 동작 |
|---|---|---|
| WebSquare 화면 알아보기 | `ui/**/*.xml` 3개 (맨 위 `<html>`에 `xmlns:w2="http://www.inswave.com/websquare"`) | 파일 종류 `WEBSQUARE`. 엔진 설정 `ui/WebSquare.xml`은 화면이 아니므로 `XML` |
| 주소로 부르기 (표준 방식) | `OrderList.xml`: `<xf:submission action="/order/list.do">` | 진입점 주소와 같으므로 `REQUESTS` (`MEDIUM`, `via = PATH_TEXT`) |
| 거래 ID로 부르기 | `OrderList.xml`: `ShopJS.svr.doRequestAjax("ORD0100M01S", …)`, `'ORD0100M02U'` | 규칙 `doRequestAjax=perform{id}` → `OrderService#performORD0100M01S`, `#performORD0100M02U` (`HIGH`, `via = CALL_RULE`) |
| 서버에 없는 거래 | `doRequestAjax("ORD0100M09S", …)` | 짐작하지 않고 `UNRESOLVED`로 남긴다. 찾던 메소드 이름 `performORD0100M09S`는 `to_external` |
| 거래 ID를 변수로 넘김 | `doRequestAjax(tranId, …)` | 잇지 않는다(로그의 "ID를 글자로 적지 않은 호출" 1건) |
| 지워 둔 옛 호출 | `// …doRequestAjax("ORD0100M03D"…`, `/* … */` | 주석 안이므로 잇지 않는다 |
| 컨텍스트 경로를 붙여 쓴 주소 | `OrderPopup.xml`: `action : "/shop/order/detail.do"` | 첫 마디(`/shop`)를 떼면 진입점과 같다 → `REQUESTS` (`LOW`) |
| 쿼리 XML 알아보기 | `WEB-INF/src/shop/dao/OrderD.xml` (맨 위 `<document>`, 안에 `id`와 `<statement>`) | 파일 종류 `QUERY_XML`. 네임스페이스는 파일 이름 `OrderD` |
| 감싸는 태그와 SQL 종류 | `<query id="selectOrderList">`, `<delete id="deleteOrder">` | 종류는 태그가 아니라 SQL의 첫 단어로 정한다. 맨 앞 주석(`/* OrderD.xml_001 */`)은 건너뛴다 → `SELECT`, `DELETE` |
| 조건에 따라 붙는 SQL | `#if($status && $status != "ALL") … #elseif(…) … #else … #end` | 지시문만 떼고 안의 SQL은 모두 남긴다. SQL 파서로 읽힌다(`HIGH`) |
| SQL 이름을 객체에 담아 넘김 | `OrderD.java`: `new QueryProperty("OrderD.selectOrderList")` | `OrderD#selectOrderList` → `OrderD.selectOrderList` (`EXECUTES_SQL`, `HIGH`) |
| 쿼리 파일에 없는 이름 | `new QueryProperty("OrderD.selectGone")` | `UNRESOLVED`로 남긴다 |
| 이름을 변수로 넘김 | `new QueryProperty(queryName)` | 잇지 않는다 |
| 거래 ID로 불리는 메소드 = 진입점 | `OrderService#performORD0100M01S`, `#performORD0100M02U` | 규칙의 이름 틀(`perform{id}`)에 맞는 공개 메소드 → 진입점 `TRANSACTION`, 주소 자리에 거래 ID |
| 상위 타입으로 계층 정하기 | `OrderService extends AbstractMainService`, `OrderD extends JdbcDAO` (둘 다 jar 없음, import로 전체 이름만 안다) | 설정 `layer.super-types` → `OrderService` `CONTROLLER`, `OrderD` `REPOSITORY` (`MEDIUM`). 이름 끝말(`…Service`)로 짐작한 `SERVICE`보다 먼저다 |
| 화면이 다른 화면을 끼워 넣거나 띄움 | `OrderList.xml`: `<w2:wframe src="/shop/ui/common/Header.xml">`, `openPopup("/shop/ui/order/OrderPopup.xml?mode=view")` | `INCLUDES` 2건. 앞의 컨텍스트 경로와 뒤의 `?파라미터`는 떼고 찾는다 |

## 기대 값

| 항목 | 값 |
|---|---|
| Job 상태 | `DONE` (오류 0) |
| 파일 | 8: Java 3, WebSquare 화면 3, 쿼리 XML 1, 그 밖의 XML 1 |
| 진입점 | `HTTP` 2 (`/order/list.do`, `/order/detail.do`), `TRANSACTION` 2 (`ORD0100M01S`, `ORD0100M02U`) |
| 계층 | `OrderController` `CONTROLLER`(`HIGH`, 애노테이션), `OrderService` `CONTROLLER`(`MEDIUM`, 상위 타입), `OrderD` `REPOSITORY`(`MEDIUM`, 상위 타입) |
| `REQUESTS` | 5: `OrderList.xml` → `OrderController#list`(`MEDIUM`), `OrderService#performORD0100M01S`(`HIGH`), `#performORD0100M02U`(`HIGH`), `performORD0100M09S`(`UNRESOLVED`) / `OrderPopup.xml` → `OrderController#detail`(`LOW`) |
| `INCLUDES` | 2: `OrderList.xml` → `OrderPopup.xml`, `Header.xml` |
| `RENDERS` | 0 (리치클라이언트 화면은 서버가 열지 않는다) |
| SQL statement (`analysis_mapper`) | 2: `OrderD.selectOrderList`(`SELECT`), `OrderD.deleteOrder`(`DELETE`). `mapper_type = QUERY_XML` |
| `EXECUTES_SQL` | 3: `OrderD#selectOrderList`, `#deleteOrder`(`HIGH`) / `OrderD.selectGone`(`UNRESOLVED`) |
| `READS_TABLE` | 2 (`selectOrderList` → `TB_ORDER`, `TB_CUSTOMER`) |
| `WRITES_TABLE` | 1 (`deleteOrder` → `TB_ORDER` `crud=D`) |
| 검색 문서 | 20: `METHOD` 9, `TYPE` 3, `FILE` 3, `MAPPER` 2, `VIEW` 3 |
| 영향 분석 (`table=TB_CUSTOMER`) | 화면 `ui/order/OrderList.xml`(`REQUESTS`), 진입점 `/order/list.do`와 거래 `ORD0100M01S`. 테이블 → SQL → `OrderD` → `OrderService` → 화면 |
| 영향 분석 (`methodId` = `performORD0100M01S`) | 화면 `ui/order/OrderList.xml`, 진입점 `/order/list.do` |
| 노드 맵 | 화면 노드 3개(`kind = JSP`, `subType = WEBSQUARE`), 클래스 3개, 매퍼 1개(`subType = QUERY_XML`), 테이블 2개 |

## 규칙 설정

거래 ID 규칙은 `conf/application.yml`의 `dstone.knowledge.semantic.screen.call-rules`에 적는다(`함수=메소드이름틀`, 쉼표로 여럿).
이 표본과 `HI-Japan`은 같은 규칙 `doRequestAjax=perform{id}`를 쓴다. 주소로 부르는 화면은 규칙 없이도 이어진다.

계층 규칙은 `dstone.knowledge.semantic.layer.super-types`에 적는다(`상위 타입의 전체 이름=계층`, 쉼표로 여럿). 기본값에 JEF 계열의 규칙이 들어 있다.
