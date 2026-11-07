# dstoneknowledge_에서_호

> 생성일시: 2026-11-07 10:04

## 질문

dstone-knowledge 에서
호출관계를 분석하는데에 있어서 티어마다(ui -> 컨트롤러 -> 서비스 -> ...) 분석하는 로직이 어떻게 되?

---

## 답변

The doc has a section exactly on this. Let me read it.

---

## 답변

**결론: 계층마다 따로 도는 분석 로직은 없습니다.** "컨트롤러 분석기"나 "서비스 분석기" 같은 건 없고, 분석기는 계층 대신 **경계의 종류**를 봅니다. 경계마다 관계를 만드는 담당이 따로 있고, 만든 관계는 모두 `analysis_relation` 테이블 하나에 같은 형태로 저장됩니다. 조회할 때 이 관계를 한 줄로 이어 따라갑니다. (근거 문서: `docs/11.dstone-knowledge.md` 0.9절)

## 경계별로 관계를 만드는 담당

| 경계 | 관계 | 담당 (단계) | 근거 |
|---|---|---|---|
| 화면 → 서버 | `REQUESTS` | `JspPlugin`, `RichClientPlugin` (SEMANTIC) | 화면 안의 `/path` 글자가 진입점 주소와 **정확히** 같은지 비교. WebSquare·Nexacro는 call rule(`doRequestAjax=perform{id}`)로 거래 ID와 메소드 이름을 맞춤 |
| 주소 → 처리 메소드 | 진입점(`analysis_endpoint`) | `SpringPlugin`, `StrutsPlugin`, `ServletPlugin` | `@RequestMapping`(클래스+메소드 경로 결합), `struts-config.xml`, `web.xml` |
| 메소드 → 메소드 (컨트롤러→서비스→DAO) | `CALLS` | `DeclarePass`가 기록하고 `ReferenceResolver`(RESOLVE)가 풂 | 소스의 호출과 타입 정보 |
| 인터페이스 → 구현체 | `CALLS_POSSIBLE_IMPLEMENTATION` | `LinkPass` (SQL만 사용) | 상속·구현 관계 |
| DAO → SQL | `EXECUTES_SQL` | `MyBatisPlugin` + `ConstantEvaluator` | 첫 인자로 넘긴 statement 이름을 계산 |
| SQL → 테이블 | `READS_TABLE` / `WRITES_TABLE` | `MyBatisPlugin` (`SqlTableExtractor`) | JSQLParser로 파싱하고, 실패하면 정규식 사용 |
| 메소드 → 화면 | `RENDERS` | `JspPlugin`, `StrutsPlugin` | `ModelAndView("x")`의 x로 끝나는 JSP가 하나뿐인 경우 |

## 핵심인 `CALLS`는 두 단계로 만듭니다

1. **DECLARE**: 파일을 하나씩 읽고 "여기서 `orderService.cancel`을 인자 1개로 부른다"는 사실만 `analysis_reference`에 적습니다. 다른 파일은 보지 않으므로 이 시점에는 타입을 모릅니다.
2. **RESOLVE**: 모든 선언이 DB에 들어간 뒤 호출을 하나씩 풉니다. 위 단계에서 풀리면 아래로 내려가지 않습니다.
   - ① JavaSymbolSolver로 풀리면 `HIGH`
   - ② 대상 식의 타입(변수 선언, import, 앞 호출의 반환 타입)을 알면 그 타입과 상위 타입에서 이름·인자 수로 찾음. 결과는 `MEDIUM`, 후보가 여럿이면 `LOW`, 프로젝트 밖 타입이면 `EXTERNAL`
   - ③ 프로젝트 전체에서 이름·인자 수가 같은 메소드가 5개 이하면 `LOW`(짐작)
   - 그래도 안 되면 `UNRESOLVED`로 남기고 이유를 기록

이 규칙은 컨트롤러→서비스든 서비스→DAO든 유틸→유틸이든 똑같이 적용됩니다. `@Autowired`로 받았든 `new`로 만들었든 상관없고, 필드나 변수의 타입만 알면 풀립니다.

`orderService`가 인터페이스면 `OrderService#cancel`에서 길이 끊깁니다. 이 끊긴 곳은 `LinkPass`가 구현체 `OrderServiceImpl#cancel`로 선을 하나 더 그어 메웁니다(`MEDIUM`). 몸통이 있는 일반 메소드는 일부러 하위 클래스로 넓히지 않습니다. `anybiz_prd`에서 그렇게 했더니 쓸모없는 선이 27,715개 생겼기 때문입니다.

## "컨트롤러/서비스/DAO" 이름표는 따로 붙습니다

`analysis_symbol.layer`는 SEMANTIC 단계에서 우선순위 순서로 붙고, 먼저 정해진 값을 뒤에서 덮어쓰지 않습니다.

- `SpringPlugin`(100, 애노테이션, `HIGH`)
- `ServletPlugin`(200)
- `StrutsPlugin`(250)
- `ConfiguredLayerPlugin`(800, 설정에 적은 상위 타입)
- `NamingLayerPlugin`(900, 클래스 이름 끝말, `LOW`)

이 이름표는 노드 맵의 열 배치, 검색의 `layer` 조건, RAG 문서에만 쓰입니다. **호출 관계를 만들거나 따라갈 때는 쓰지 않으므로**, 이름표가 비어 있는 클래스도 호출 사슬에서 빠지지 않습니다.

## 조회: 관계 종류 목록만 바꿔 재귀로 따라갑니다

`WITH RECURSIVE` 쿼리 하나로 한 칸씩 따라가고, 계층별 쿼리는 따로 없습니다.

- `/callees`: `CALLS`, `CALLS_POSSIBLE_IMPLEMENTATION`, `EXECUTES_SQL`, `READS_TABLE`, `WRITES_TABLE`, `RENDERS`를 따라가므로 DAO 다음 칸이 SQL, 그다음 칸이 테이블이 됩니다.
- `/callers`: `CALLS`, `CALLS_POSSIBLE_IMPLEMENTATION`, `REQUESTS`를 따라가므로 컨트롤러 다음 칸이 화면이 됩니다.
- 안전장치: 깊이는 최대 5(`/impact`는 10)이고, `UNION`으로 중복을 없애 순환 호출에서도 끝나며, 같은 선은 가장 얕은 깊이의 것만 남깁니다.

## 사슬이 끊기는 경우

원칙은 "계산할 수 있는 것만 잇는다"입니다. 그래서 아래 경우에는 선이 없거나 약하게 남습니다.

- 거래 ID나 SQL 이름을 변수로 넘김 (`queryForList(sqlId)`)
- 리플렉션 사용
- Struts `DispatchAction`
- `return "order/list";`로 뷰를 고르는 경우
- jar가 없어서 이름으로만 고른 호출 (`LOW`)

끊긴 곳은 `UNRESOLVED` 행으로 이유와 함께 남으므로, 결과가 비어 있으면 그 행부터 확인하면 됩니다. 단계별 상세 규칙은 같은 문서의 4.5절(RESOLVE), 4.6절(LINK), 4.7절(SEMANTIC), 4.10절(SQL·화면)에 있습니다.

---

## 질문

혹시 이런 엣지 관계를 소스 바깥으로 빼서 설정자료처럼 관리할 수 없을까?
추후 새로운 환경의 어플리케이션에 대해서 분석을 해야 할 때 소스를 건드리지 않고 추가적으로 발견된 패턴만 설정자료에 작업하면 되도록 말이야.
가능성을 분석해줘.

---

## 답변

가능합니다. 다만 **모든 엣지를 설정으로 뺄 수 있는 건 아니고**, 경계의 성격에 따라 갈립니다. 현재 코드를 보면 이미 절반쯤은 그 방향으로 와 있습니다.

## 1. 지금 상태: 이미 설정으로 빠진 것과 코드에 박힌 것

**이미 설정으로 빠진 것** (`conf/application.yml`의 `dstone.knowledge.semantic.*`)

| 설정 | 만드는 것 |
|---|---|
| `screen.call-rules` (`doRequestAjax=perform{id}`) | 화면 → 서버 `REQUESTS` |
| `screen.field-rules` (`tranId=perform{id}`) | 화면 → 서버 `REQUESTS` |
| `screen.open-rules` (`fnOpenPage={id}.xml`) | 화면 → 화면 `INCLUDES` |
| `screen.menu-rules` | 화면명 |
| `layer.super-types` (`JdbcDAO=REPOSITORY`) | 계층 이름표 |

HI-Japan과 JEF 계열을 분석할 때 생긴 이 규칙들이 바로 "새 환경에서 발견한 패턴을 설정에만 적는" 방식입니다. 효과가 이미 확인된 셈입니다.

**아직 코드에 박혀 있는 것**

| 위치 | 하드코딩된 내용 | 설정으로 뺄 수 있나 |
|---|---|---|
| `MyBatisPlugin.java:47` `MAPPER_CALLS` | `selectList`, `queryForList` 등 SQL 이름을 받는 메소드 | 바로 가능 |
| `MyBatisPlugin.java:55` `NAME_HOLDERS` | `QueryProperty` (JEF) | 바로 가능 |
| `SpringPlugin.java:58-65` | 애노테이션 → 계층 | 바로 가능 |
| `SemanticDao.xml:57,99,115,136` | `RequestMapping`/`Scheduled`/`KafkaListener`/`Autowired` 목록 | 가능 (SQL 파라미터화) |
| `NamingLayerPlugin.java:24` `RULES` | 클래스 이름 끝말 정규식 | 바로 가능 |
| `ServletPlugin.java:96-97` | `HttpServlet`, `Filter` 상위 타입 | 바로 가능 |
| `PlainJavaPlugin` | `main`, `Runnable.run`, `Callable.call` | 가능 |
| `JspPlugin` | `ModelAndView("x")`, `setViewName("x")` → `RENDERS` | 가능 |
| `StrutsPlugin`, `ServletPlugin` | `struts-config.xml`, `web.xml` 구조 읽기 | 부분적으로 가능 (아래 4번) |

## 2. 설정으로 뺄 수 있는 것과 없는 것

기준은 **"언어의 의미"인가, "프레임워크의 관례"인가**입니다.

**코드에 남겨야 하는 것 (언어 규칙, 엔진)**
- `CALLS` 해석 (`ReferenceResolver`): 타입 추론이라 프레임워크와 무관합니다. 이미 범용이라 새 환경 때문에 고칠 일이 거의 없습니다.
- `CALLS_POSSIBLE_IMPLEMENTATION` (`LinkPass`): 상속 관계만 봅니다.
- `ConstantEvaluator`(이름 계산), `SqlTableExtractor`(SQL 파싱), `JspToJava`, 파일 분류처럼 **새 언어나 파일 형식을 읽는 일**

**설정으로 뺄 수 있는 것 (프레임워크 관례)**

새 환경에서 실제로 마주치는 패턴은 대부분 아래 다섯 가지 모양 중 하나입니다.

| 규칙 모양 | 근거 테이블 | 예 | 만드는 엣지 |
|---|---|---|---|
| **A. 호출 인자 규칙** | `analysis_reference.arg_text` | `메소드명 X`(또는 `new 타입 X`)의 n번째 인자가 리터럴이면 → statement / 뷰 / 진입점과 연결 | `EXECUTES_SQL`, `RENDERS`, `CALLS`(문자열 디스패치) |
| **B. 애노테이션 규칙** | `analysis_annotation` | `@XxxMapping(경로)` → HTTP 진입점, `@MyService` → SERVICE | 진입점, 계층, `INJECTS` |
| **C. 상위 타입 규칙** | `analysis_relation` (EXTENDS/IMPLEMENTS) | `AbstractMainService` 하위 클래스의 `perform*` → 진입점 | 진입점, 계층 |
| **D. 이름 규칙** | `analysis_symbol` | 클래스명이 `…M$`이면 CONTROLLER | 계층 |
| **E. 화면 스크립트 규칙** | 화면 파일 | 이미 있는 call/field/open/menu 규칙 | `REQUESTS`, `INCLUDES` |

다섯 가지 모두 **이미 DB에 들어 있는 사실을 SQL로 엮는 작업**입니다. CLAUDE.md의 "Java를 다시 파싱하지 말고 `analysis_*` 테이블에서 SQL로 도출한다" 원칙과 잘 맞습니다. 엔진 쪽에서는 규칙 모양마다 파라미터화된 SQL을 하나씩 두고, 설정은 그 파라미터만 채우면 됩니다.

## 3. 설정 파일 모습 (예시)

`application.yml`의 쉼표 문자열은 규칙이 늘면 금방 한계에 부딪힙니다. 별도 파일로 빼는 편이 낫습니다.

```yaml
# conf/rules/jef.yml  (프레임워크·프로젝트별로 파일을 나눈다)
layers:
  - superType: jef.application.services.AbstractMainService
    layer: CONTROLLER
  - namePattern: "M$"
    layer: CONTROLLER

endpoints:
  - kind: TRANSACTION
    superType: jef.application.services.AbstractMainService
    methodPattern: "perform(?<id>.+)"      # 진입점 ID = 메소드 이름에서 뽑음

sqlCalls:                                   # 규칙 A: DAO → SQL
  - method: executeVOQuery
    receiverType: jef.application.dao.jdbc.JdbcDAO   # 이름만 맞으면 엉뚱한 update()도 잡히므로 좁힌다
    argIndex: 0
  - constructor: QueryProperty
    argIndex: 0

views:                                      # 규칙 A: 메소드 → 화면
  - method: forward
    argIndex: 0
    template: "/WEB-INF/jsp/{name}.jsp"
```

기존의 하드코딩 목록은 `conf/rules/builtin-spring.yml`, `builtin-mybatis.yml` 같은 **기본 규칙 파일**로 옮깁니다. 그러면 지금과 결과가 같은 상태에서 출발할 수 있습니다.

## 4. 경계가 애매한 부분

- **Struts·web.xml 같은 XML 설정 읽기**: "XPath로 url/class/method를 뽑는다"는 규칙으로 일반화할 수 있습니다. 하지만 `*.do` 확장자 결합, 모듈 prefix, Struts 2의 namespace/wildcard처럼 해석 로직이 붙습니다. 단순한 사내 프레임워크의 action XML은 설정으로 처리하고, Struts·Servlet 같은 복잡한 것은 지금처럼 코드로 남기는 혼합 방식이 현실적입니다.
- **문자열 디스패치** (`invoke("orderService.cancel")`, 거래 ID → Bean 메소드 매핑 테이블): 규칙 A의 변형으로 처리할 수 있지만, 매핑이 DB나 properties 파일에 있으면 그 파일을 먼저 `analysis_config`에 넣어야 합니다. 이때는 RESOURCE 단계가 그 파일을 읽을 수 있어야 합니다.
- **완전히 새로운 화면 기술이나 파일 형식**: 파서가 필요하므로 코드 작업입니다.

## 5. 지켜야 할 제약

1. **"계산하고 추측하지 않는다"를 규칙 엔진이 강제해야 합니다.** 설정 작성자가 느슨한 정규식을 써도 엔진은 리터럴로 확인된 값만 잇고, 후보 수에 따른 신뢰도(1개 `HIGH`, 2~5개 `LOW`, 그 이상은 `UNRESOLVED`)는 엔진이 정합니다. 설정이 신뢰도를 직접 지정하지 못하게 해야 합니다.
2. **규칙도 분석 결과의 일부로 기록해야 합니다.** 규칙을 바꾸면 같은 소스라도 엣지가 달라집니다. 리비전에 적용한 규칙의 해시나 스냅샷을 남겨야 리비전 비교나 증분 분석 결과를 설명할 수 있습니다. 규칙을 바꾼 뒤에는 `rerunFrom=SEMANTIC`으로 다시 돌리는 절차가 맞습니다. 이미 있는 기능이고, 소스 재파싱이 필요 없어 빠릅니다.
3. **증분과 전체 분석의 결과가 같아야 합니다.** SEMANTIC은 매번 지우고 다시 만드므로 규칙화해도 이 성질은 유지됩니다.
4. **규칙의 적용 범위**: 지금은 전역 설정 하나에 "다른 프로젝트 규칙을 함께 적어 두어도 된다"는 방식입니다. 규칙이 많아지면 서로 간섭합니다(`update` 같은 흔한 이름). 프로젝트 등록 시 사용할 규칙 파일 목록을 고르게 하는 편이 안전합니다.
5. **잘못된 규칙은 부팅이나 분석 시작 시점에 검증해서 실패시켜야 합니다.** ai-engine의 정의 파일 검증과 같은 방식입니다.

## 6. 새 환경 작업 흐름이 이렇게 바뀝니다

1. 기본 규칙만으로 분석합니다.
2. `UNRESOLVED` 관계와 `arg_text`가 리터럴인데 연결되지 않은 호출을 모아 **"패턴 후보" 리포트**로 봅니다. 예: "`executeVOQuery` 호출 1,200건이 리터럴 인자를 가졌지만 어떤 규칙에도 걸리지 않음"
3. 규칙 파일에 한 줄을 추가하고 **dry-run API**로 생길 엣지 수와 샘플을 미리 봅니다.
4. `rerunFrom=SEMANTIC`으로 반영합니다.

2번과 3번이 있어야 "소스를 건드리지 않는다"가 실제로 편해집니다. 규칙 파일만 있으면 무엇을 적어야 할지 찾는 일이 여전히 사람 몫으로 남습니다.

## 7. 권장 진행 순서

| 단계 | 작업 | 규모 | 비고 |
|---|---|---|---|
| 1 | 규칙 파일 로더와 검증, 리비전에 규칙 해시 기록 | 중 | 기반 |
| 2 | 하드코딩 목록을 기본 규칙 파일로 이전 (`MAPPER_CALLS`, `NAME_HOLDERS`, 애노테이션, 이름, 상위 타입, 뷰 메소드) | 중 | `struts-app`·`legacy-app`·`anybiz_prd`에서 **이전 전후 관계 수가 같은지** 비교 |
| 3 | 규칙 A에 `receiverType`/`argIndex`/`template` 추가 | 소~중 | 새 환경 대응력이 가장 크게 늘어남 |
| 4 | 기존 `screen.*`, `layer.super-types`를 새 형식으로 이동 (기존 키는 당분간 함께 읽음) | 소 | |
| 5 | 패턴 후보 리포트와 dry-run API | 중 | 운영 편의 |
| 6 | XPath 기반 사내 프레임워크 XML 매핑 규칙 | 중~대 | 필요가 생길 때 |

**정리하면**, 새 환경에서 추가로 발견되는 패턴의 대부분(SQL 호출 메소드, 진입점 애노테이션이나 상위 타입, 뷰 이동 메소드, 계층 판정, 화면 호출 함수)은 설정으로 처리할 수 있습니다. 호출 해석과 SQL 파싱 같은 엔진은 코드에 남는데, 이쪽은 프레임워크를 타지 않아 새 환경 때문에 고칠 일이 드뭅니다. 1~3단계만 해도 효과가 크고, 2단계의 "결과 동일성 비교"로 기존 동작이 바뀌지 않았음을 확인할 수 있습니다.

원하시면 이 분석을 설계 메모로 정리하거나(`docs/11.dstone-knowledge.md` 또는 `.claude/docs/`), 1~2단계 구현 계획을 세분화하겠습니다.
