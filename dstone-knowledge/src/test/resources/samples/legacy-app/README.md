# legacy-app (구버전 Java 검증 표본)

`dstone-knowledge`가 Spring이 아닌 **구버전 일반 Java 프로그램**도 분석할 수 있는지 확인하려고 만든 작은 표본이다.
실제로 돌아가는 프로그램이 아니라 분석기의 입력으로만 쓴다(컴파일하지 않는다).

## 일부러 넣어 둔 특징

| 특징 | 어디에 | 분석기가 확인할 것 |
|---|---|---|
| 인코딩이 **EUC-KR** | `*.java`, `*.jsp`, `web.xml` (이 README만 UTF-8) | 파일별 인코딩 자동 감지. 한글 주석이 깨지지 않아야 한다 |
| 소스 루트가 `WEB-INF/src` | 전체 | `src/main/java`가 아니어도 `package` 선언으로 소스 루트를 찾는다 |
| 빌드 파일 없음 (pom.xml/build.gradle 없음, `WEB-INF/lib`도 없음) | 전체 | 클래스패스가 불완전해도 분석이 끝나야 한다. `javax.servlet.*`은 외부 미해결로 남는다 |
| `enum`을 변수 이름으로 사용 (Java 1.4 문법) | `OrderServiceImpl.totalAmount()` | 문법 수준을 1.4로 잡아야 파싱된다. 최신 문법으로는 실패한다 |
| 제네릭 없는 raw 타입 (`List`, `Vector`) | DAO, Service | 타입 인자가 없어도 호출 관계를 푼다 |
| 애노테이션이 하나도 없음 | 전체 | 계층은 이름 규칙(`*Servlet`, `*ServiceImpl`, `*DAO`, `*VO`)으로만 분류한다 |
| 인터페이스 + 구현체, `new`로 직접 생성 | `OrderService` / `OrderServiceImpl` | `OrderServlet` → `OrderService.findOrders` 호출의 구현 후보가 `OrderServiceImpl` |
| 익명 클래스 | `OrderServiceImpl.findOrders()`의 `Comparator` | 익명 타입과 그 안의 메소드 |
| 중첩(static) 클래스 + `Runnable` | `OrderBatchMain.Worker` | 중첩 타입, 스레드 진입점 |
| static 초기화 블록 + `Class.forName` | `DBUtil` | 리플렉션은 정적 분석 한계로 따로 표시 |
| JDBC 직접 사용, SQL이 문자열 | `OrderDAO` | 상수 SQL(`SELECT_SQL`)과 이어 붙인 SQL(`delete`) |
| 서블릿 + `web.xml` 매핑 | `OrderServlet`, `/order.do` | 진입점(SERVLET) |
| `main()` | `OrderBatchMain` | 진입점(MAIN) |
| JSP 스크립틀릿에서 Java 직접 호출 | `order/list.jsp` | JSP → `OrderServiceImpl.findOrders` 호출 관계 |

## 기대 값 (분석 결과와 대조하는 기준)

| 항목 | 값 | 내역 |
|---|---|---|
| Java 파일 | 7 | |
| 이름 있는 타입 | 8 | `OrderVO`, `DBUtil`, `OrderDAO`, `OrderService`, `OrderServiceImpl`, `OrderServlet`, `OrderBatchMain`, `OrderBatchMain.Worker` |
| 익명 타입 | 1 | `OrderServiceImpl.findOrders()` 안의 `Comparator` |
| 메소드 (생성자 제외, 익명 타입 제외) | 19 | OrderVO 6, DBUtil 2, OrderDAO 2, OrderService 2, OrderServiceImpl 3, OrderServlet 2, OrderBatchMain 1, Worker 1 |
| 익명 타입의 메소드 | 1 | `compare` |
| 소스에 적힌 생성자 | 2 | `DBUtil()`, `Worker(String)` |
| 필드 | 8 | OrderVO 3, DBUtil 1, OrderDAO 1, OrderServiceImpl 1, OrderServlet 1, Worker 1 |
| 상속/구현 | 4 | `OrderVO`→`Serializable`, `OrderServiceImpl`→`OrderService`, `OrderServlet`→`HttpServlet`, `Worker`→`Runnable` (익명 `Comparator` 별도) |
| 진입점 | 4종 | 서블릿 `/order.do`, `main`, `Runnable.run`, JSP `order/list.jsp` |
