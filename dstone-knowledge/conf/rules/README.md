# 의미 분석 규칙 (conf/rules)

`dstone-knowledge`의 SEMANTIC 단계가 쓰는 **프레임워크 관례**를 적는 곳이다. 어떤 애노테이션이 진입점인지, 어떤 메소드의 몇 번째 인자가 SQL 이름인지, 어떤 상위 타입을 상속하면 서비스인지 같은 것이다.

새 환경의 프로젝트를 분석하다 처음 보는 관례를 만나면 Java 코드를 고치지 않고 여기에 규칙 파일을 더한다.

- 파일 하나가 **규칙 묶음** 하나다. 파일 이름(확장자 뺀 것)이 묶음 이름이다.
- 서버가 뜰 때 모든 파일을 검사한다. 모르는 키나 없는 계층 이름, 틀린 정규식이 있으면 **서버가 뜨지 않고** 어느 파일의 어느 자리인지 알려 준다.
- 분석할 때마다 파일을 다시 읽는다. 규칙을 고친 뒤에는 서버를 다시 띄울 필요 없이 `rerunFrom: "SEMANTIC"`으로 다시 돌리면 된다.
- 리비전마다 어떤 묶음을 썼는지 해시와 함께 남는다(`GET /api/revisions/{id}/rules`).

## 들어 있는 묶음

| 파일 | order | 내용 |
|---|---|---|
| `java.yml` | 100 | `Runnable`/`Thread`/`TimerTask`의 `run()`, `Callable`의 `call()` → THREAD 진입점 |
| `servlet.yml` | 150 | `HttpServlet` → CONTROLLER, `Filter` → WEB_FILTER |
| `spring.yml` | 200 | `@RequestMapping` 계열 HTTP 진입점, `@Scheduled`, 리스너, 주입, 프록시, 스테레오타입 계층, `ModelAndView` 화면 |
| `mybatis.yml` | 300 | SQL 이름을 첫 인자로 받는 `SqlSession`/`SqlMapClient` 메소드 |
| `jef.yml` | 500 | JEF 계열 상위 타입 계층, `new QueryProperty("…")` SQL 이름 |
| `hi-japan.yml` | 500 | HI-Japan 화면의 거래 ID 호출 함수, 출력 폼 필드, 화면 열기 함수, 메뉴 목록 |
| `fallback.yml` | 900 | `@Component` 계층, 클래스 이름 끝말로 짐작하는 계층(LOW) |

코드에 남아 있는 것은 언어나 표준 형식 자체의 규칙이다. `main()`, JSP 진입점, `web.xml`, `struts-config.xml`, 매퍼 인터페이스 방식(namespace = 인터페이스 이름), 호출 해석(RESOLVE), SQL 파싱이 그렇다.

## 어느 묶음을 쓰나

- `autoApply: true`(기본)인 묶음은 모든 프로젝트에 쓴다. 해당하는 코드가 없는 프로젝트에서는 아무 일도 하지 않으므로 여러 프로젝트의 규칙을 함께 두어도 된다.
- `autoApply: false`인 묶음은 프로젝트를 등록할 때 `ruleSets`에 이름을 적은 경우에만 쓴다. 메소드 이름이 흔해서 다른 프로젝트에 잘못 걸릴 수 있는 규칙은 이렇게 둔다.

```json
POST /api/projects
{ "projectId": "order-system", "localPath": "/src/order", "ruleSets": "order-fw" }
```

- 묶음은 `order`가 작은 것부터 적용한다(같으면 이름 순). 계층은 "먼저 정한 것을 뒤에서 바꾸지 않는다"라서 순서가 결과를 바꿀 수 있다. 뜻이 넓은 규칙은 order를 크게 둔다.

## 파일 모양

```yaml
name: order-fw          # 파일 이름과 같아야 한다. 생략하면 파일 이름
description: 주문 시스템 자체 프레임워크
order: 500              # 기본 500
autoApply: false        # 기본 true

endpoints:
  http:                 # HTTP 진입점. 클래스 주소 + 메소드 주소
    - classAnnotations: [RequestMapping]
      pathAttributes: [value, path]              # 주소가 적힌 속성. 앞에 있는 것부터 본다
      methodAnnotations:
        - { annotation: RequestMapping, methodAttribute: method }  # HTTP 메소드를 속성에서 읽는다. 비었으면 ALL
        - { annotation: GetMapping, httpMethod: GET }              # HTTP 메소드가 정해져 있다
  annotations:          # 애노테이션이 붙은 메소드 → 진입점
    - { endpointType: SCHEDULED, annotations: [Scheduled], pathAttributes: [cron] }
  superTypes:           # 이 상위 타입을 바로 상속한 클래스의 메소드 → 진입점
    - endpointType: THREAD
      superTypes: [java.lang.Runnable]
      methods: [run]                  # 또는 methodPattern: '^perform'
      paramCount: 0                   # 생략하면 보지 않는다

injects:                # 이 애노테이션이 붙은 필드 → INJECTS 관계
  annotations: [Autowired]
proxies:                # 이 애노테이션이 붙은 메소드(또는 클래스) → proxy_related
  annotations: [Transactional]

layers:
  annotations:          # 애노테이션 → 계층(HIGH)
    - { layer: SERVICE, annotations: [Service] }
  superTypes:           # 바로 위의 상위 타입 → 계층(MEDIUM)
    - { layer: REPOSITORY, types: [com.fw.BaseDao] }
  names:                # 클래스 단순 이름(PostgreSQL 정규식) → 계층(LOW)
    - { layer: CONTROLLER, pattern: 'M$' }

callArguments:          # 호출의 n번째 인자로 넘긴 글자 → SQL 또는 화면과 잇는다
  - target: SQL                       # SQL(EXECUTES_SQL) | VIEW(JSP로 RENDERS)
    methods: [execute]                # 메소드 호출 이름
    constructors: [QueryProperty]     # 생성자 호출 이름(new 타입(...))
    argIndex: 1                       # 0부터 센다. 기본 0. 최대 9
    receiverTypes: [com.fw.BaseDao]   # 호출 대상 타입(하위 타입 포함). 이름이 흔한 메소드를 좁힐 때
    template: "Order.{value}"         # 계산한 값의 앞뒤에 붙일 글자. SQL 기본 "{value}", VIEW 기본 "{value}.jsp"
    exclude: '^redirect:'             # 이 정규식에 맞는 값은 버린다

screens:                # 리치클라이언트 화면(WebSquare, Nexacro)
  calls:  [{ function: doRequestAjax, method: "perform{id}" }]   # 화면 함수의 첫 인자(거래 ID) → 메소드
  fields: [{ field: tranId, method: "perform{id}" }]            # 폼 필드 값(거래 ID) → 메소드
  opens:  [{ function: fnOpenPage, screen: "{id}.xml" }]        # 화면 ID → 화면 파일
  menus:  [{ nameKey: MENU_NM, pathKey: SRC_PATH }]             # 메뉴 목록 → 화면명
```

- 계층: `CONTROLLER, SERVICE, REPOSITORY, MODEL, VIEW, CONFIG, WEB_FILTER, UTIL, EXCEPTION, BATCH, COMPONENT, ASPECT`
- 진입점 종류: 영문 대문자와 `_`로 20자 이내. 새 이름을 써도 된다(예: `BATCH_JOB`).
- 애노테이션은 `@` 없이 단순 이름으로 적는다(`Service`). 상위 타입과 `receiverTypes`는 전체 이름으로 적는다.
- 목록 자리에 값을 하나만 적어도 된다(`annotations: Service`).
- `{`로 시작하는 값과 `@`로 시작하는 값은 따옴표로 감싼다(YAML 문법).

### 호출 인자 규칙이 잇는 기준

이름은 **계산할 수 있을 때만** 잇는다. 짐작으로 이은 선은 영향도 분석을 틀리게 만들기 때문이다.

- SQL: 따옴표 글자, `+`, 그 타입과 상위 타입의 상수 필드, `return` 한 줄짜리 메소드까지 계산한다. 모르는 조각(지역 변수, 매개변수)이 섞이면 잇지 않는다. 이름은 알았는데 그런 statement가 없으면 `UNRESOLVED`로 이름만 남긴다.
- VIEW: 따옴표 글자 하나만 본다. 틀을 채운 경로로 끝나는 JSP가 하나일 때만 잇는다.
- 첫 인자는 DECLARE가 원래 남기던 `arg_text`를 쓴다. 둘째 인자부터는 `args_json`을 쓰는데, 분석기 1.1에서 생긴 컬럼이라 그 전에 분석한 리비전은 DECLARE부터 다시 분석해야 `argIndex: 1` 이상이 맞는다.
- `receiverTypes`는 다음 가운데 하나면 맞는다고 본다. RESOLVE가 그 호출을 그 타입(또는 하위 타입)의 메소드로 풀었거나, 프로젝트 밖 메소드 `타입.이름(...)`으로 풀었거나, 대상 없이(`this`/`super` 포함) 부른 호출이고 부르는 클래스가 그 타입의 하위 타입일 때다.

## 새 관례를 찾아 규칙으로 만드는 순서

1. **후보 보기**: `GET /api/revisions/{id}/rules/candidates`
   - `callArguments`: 따옴표 글자를 인자로 받는 호출 가운데 그 글자가 이 프로젝트의 SQL 이름이나 JSP 화면 이름과 같은 것. 몇 번째 인자인지, RESOLVE가 가장 많이 푼 대상 타입도 함께 나온다.
   - `annotations`: 규칙이 모르는 애노테이션 가운데 많이 붙은 것. `unexplained`는 계층 없는 타입이나 진입점 아닌 메소드에 붙은 수다.
   - `superTypes`: 규칙이 모르는 상위 타입 가운데 계층이 비었거나 LOW인 하위 타입이 많은 것
   - 후보마다 붙여 넣을 규칙 조각(`suggestion`)이 있다. 계층과 진입점 종류는 짐작이니 꼭 고친다.
2. **시험 적용**: `POST /api/revisions/{id}/rules/trial`, 본문 `{"ruleSet": "name: order-fw\n..."}`
   - SEMANTIC을 한 트랜잭션 안에서 실제로 돌려 보고 전후 차이(묶음별 수, 새로 생긴 것과 없어진 것의 보기)를 알려 준 뒤 모두 되돌린다. DB에는 아무것도 남지 않는다.
   - 같은 이름의 파일이 있으면 그 파일 대신 쓴다. 본문 없이 부르면 지금 파일 그대로 돌려, 규칙 파일을 고친 뒤 무엇이 바뀌는지 본다.
3. **저장**: 이 폴더에 `<name>.yml`로 저장한다.
4. **반영**: `POST /api/projects/{id}/analyses` `{"revisionLabel": "...", "rerunFrom": "SEMANTIC"}`

## 옛 설정 키

예전에는 `application.yml`의 `dstone.knowledge.semantic.screen.call-rules` / `field-rules` / `open-rules` / `menu-rules`와 `dstone.knowledge.semantic.layer.super-types`에 규칙을 적었다. 지금 값은 `hi-japan.yml`, `jef.yml`로 옮겼다. 이 키가 남아 있으면 `application-yml`이라는 묶음으로 맨 뒤에 붙여 쓰고 경고를 남긴다.
