# jpa-app (JPA · Java 문자열 SQL · 상수 주소 검증 표본)

`dstone-knowledge`가 다음 세 가지를 Java 소스를 고치지 않고 분석하는지 확인하는 작은 표본이다(2026-11-09).
실제로 돌아가는 프로그램이 아니라 분석기의 입력으로만 쓴다(빌드 파일과 jar 가 없다).

1. 상수로 적은 `@RequestMapping` 주소와, 상위 클래스 · 인터페이스에 붙은 `@RequestMapping`
2. JPA 쿼리(`@Query`, `@NamedQuery`, Spring Data 메소드 이름 쿼리, `EntityManager.createQuery`)
3. Java 소스에 문자열로 적은 SQL(JdbcTemplate, StringBuilder, 상수)

## 일부러 넣어 둔 특징과 기대 값

| 특징 | 어디에 | 기대 값 |
|---|---|---|
| 주소 상수(`Urls.ORDER = API + "/order"`) | `OrderController` | `GET /api/order/list`(처리 메소드 `OrderController.list`, 애노테이션 없이 재정의), `POST /api/order/store`, `GET /api/order/{id}` |
| 상위 클래스의 주소 메소드 | `BaseCrudController` → `ItemController` | `GET /api/item/list`, `POST /api/item/save`, `GET /api/item/{id}`(처리 클래스 `ItemController`). abstract 클래스 자신의 진입점은 없다 |
| 재정의하면서 다른 주소를 붙임 | `OrderController.save` | `/api/order/store` 만 있다(`/api/order/save` 는 없다) |
| 인터페이스의 클래스 주소 + 메소드 주소, 중첩 클래스 상수 | `AdminApi` → `AdminController` | `GET /admin/users`(처리 메소드 `AdminController.users`) |
| static import 한 상수 | `AdminController.legacy` | 계산하지 못한다. 주소에 `LEGACY` 가 남고 `properties_json.unresolvedPath` 가 있다 |
| `@Table` 이 있는 엔티티 / 없는 엔티티 / `@Entity(name)` | `Order`(ORDERS), `OrderItem`(ORDER_ITEM, 관례), `Customer`(엔티티 이름 `Client`, TB_CUSTOMER) | |
| `@Query` JPQL + 조인 경로 | `OrderRepository.findBigOrders` | ORDERS(R), ORDER_ITEM(R) |
| `@Query` 네이티브(이어 붙인 글) | `OrderRepository.findWithItemsNative` | ORDERS(R), ORDER_ITEM(R) |
| `@Query` update | `OrderRepository.changeStatus` | ORDERS(U) |
| 메소드 이름 쿼리 | `findByStatus`, `countByStatusAndCreatedAtAfter`, `deleteByStatus` | ORDERS(R), ORDERS(R), ORDERS(D) |
| 이름 있는 쿼리를 저장소 메소드로 | `OrderRepository.findRecent` → `Order.findRecent` | ORDERS(R) |
| `@NamedQueries` 안의 쿼리, `createNamedQuery` | `Customer`, `OrderJdbcDao.byName` | `Client.byName` → TB_CUSTOMER(R) |
| `createQuery` 의 JPQL(하위 쿼리) | `OrderJdbcDao.bigCustomers` | TB_CUSTOMER(R), ORDERS(R) |
| `createNativeQuery` 의 SQL | `OrderJdbcDao.nativeItems` | ORDER_ITEM(R) |
| StringBuilder 로 조립한 SQL(조건부 덧붙임) | `OrderJdbcDao.search` | ORDERS(R), TB_CUSTOMER(R) |
| 같은 클래스 상수로 조립한 SQL | `OrderJdbcDao.INSERT_HISTORY` → `writeHistory` | ORDER_HISTORY(C) |
| 다른 클래스의 SQL 상수 | `OrderSql.COUNT_ALL` → `OrderJdbcDao.countAll` | ORDERS(R), 메소드와는 필드 접근으로 잇는다(MEDIUM) |
| 테이블 이름이 파라미터 | `OrderJdbcDao.deleteFrom` | SQL 은 남지만 테이블은 없다(`dynamic`) |
| 사람에게 보이는 글 | `OrderJdbcDao.message` | SQL 로 보지 않는다 |
