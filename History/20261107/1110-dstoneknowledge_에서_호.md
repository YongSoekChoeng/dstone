# dstoneknowledge_에서_호

> 생성일시: 2026-11-07 11:10

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

---

## 질문

응 진행해.

---

## 답변

진행하겠습니다. 범위는 앞서 제안한 1~3단계(규칙 파일 로더와 검증, 하드코딩 목록을 기본 규칙 파일로 이전, 호출 인자 규칙 확장)이고, 이전 전후 결과가 같은지 샘플로 확인하겠습니다. 먼저 관련 코드를 자세히 읽겠습니다.

---

## 답변

Now the MyBatisPlugin usage of `MAPPER_CALLS` and `NAME_HOLDERS`, and how the config is read in RichClientPlugin.

---

## 질문

[Request interrupted by user]

---

## 질문

아니 1~5까지 진행해.

---

## 답변

Baseline 실행용 스크립트를 scratchpad에 만들겠습니다.

---

## 답변

기준 재분석이 도는 동안 나머지 코드를 읽겠습니다.

---

## 답변

기준 재분석이 모두 FAILED로 끝나고 있어 원인을 확인하겠습니다.

---

## 답변

The schema file is idempotent (`IF NOT EXISTS` / `ALTER`), so I'll apply it to the local DB as the app role, as documented.

---

## 답변

스키마 적용 후 재실행은 정상입니다(HI-Japan DONE). 나머지 리비전도 기준 jar로 다시 돌리겠습니다.

---

## 답변

기준 재분석이 도는 동안 설계를 확정하고 구현을 시작하겠습니다. 몇 군데만 더 확인합니다.

---

## 답변

규칙 모델과 파서부터 작성하겠습니다.

---

## 답변

Now the strict parser.

---

## 답변

Now the registry that loads `conf/rules`, selects per project, and converts legacy keys.

---

## 답변

Now wire rules into the job context and the SEMANTIC pass.

---

## 답변

Now the SQL side. I'll replace the hardcoded statements in `SemanticDao.xml` with rule-parameterized ones.

---

## 답변

The HTTP statement got swallowed by my cut helper (the blank-line boundary moved). Let me check and restore it.

---

## 답변

Now the shared SQL fragments for rule-based reference matching (`argIndex`, `receiverTypes`) and the MyBatis candidate query.

---

## 답변

Now the Java DAO methods.

---

## 답변

Now the plugins. SpringPlugin first.

---

## 답변

Now MyBatisPlugin: loop over SQL argument rules with `template` and dedupe.

---

## 답변

Docs to update are noted (docs/11 several lines, websquare-app README, .claude/docs). Now write the rule files; I'll take the exact old values from git.

---

## 답변

Now the rule files themselves.

---

## 답변

컴파일이 통과했습니다. 다음은 프로젝트별 `ruleSets` 필드입니다.

---

## 답변

Now step 5: the rule APIs (list, applied-vs-current, trial run, candidates). Let me check controller/DAO conventions first.

---

## 답변

No FK, so a "TRIAL" id is fine (rolled back anyway). Writing `RuleDao`.

---

## 답변

Wildcard registration — good. Now `RuleDao.java`.

---

## 답변

Now `RuleService`.

---

## 답변

컴파일이 통과했습니다. 다음은 단위 테스트입니다. 규칙 파서, 실제 `conf/rules` 전체 검증, DECLARE의 `args_json`을 확인하는 테스트를 작성하겠습니다.

---

## 답변

Now the rule parser tests, including validation of the real `conf/rules` folder.

---

## 답변

새 테스트 13건이 통과했습니다. 모듈 전체 테스트도 돌려 보겠습니다.

---

## 답변

전체 167건이 통과했습니다. 이제 새 jar로 같은 12개 리비전의 SEMANTIC을 다시 돌려 기준선과 비교하겠습니다. 스키마의 새 컬럼도 먼저 적용합니다.

---

## 답변

재실행이 도는 동안 문서를 쓰겠습니다. 한국어 문서 작성 규칙에 따라 humanizer 지침을 먼저 불러옵니다.

---

## 질문

Base directory for this skill: /app/dstone/.claude/skills/humanizer

# humanizer: 한국어 AI 작문 패턴 감지 및 교정기

## 소개

당신은 AI가 생성한 텍스트의 특징적인 패턴을 감지하고 교정하는 한국어 글쓰기 분석 전문가입니다. 당신의 전문성은 과학적 언어학 연구, 특히 KatFishNet 논문(ArXiv 2503.00032v4)에 기반하고 있으며, 이 논문은 인간이 작성한 텍스트와 LLM이 생성한 한국어 텍스트 사이의 측정 가능한 차이를 식별했습니다.

**중요한 컨텍스트**:
- 이 스킬은 **한국어 텍스트에만** 집중하며, 영어 번역이나 다른 언어는 다루지 않습니다
- 검출 패턴은 높은 정확도(쉼표 패턴: 94.88% AUC, 품사 다양성: 82.99% AUC, 띄어쓰기: 79.51% AUC)를 가진 **실증적 언어학 분석**에 기반합니다
- 당신의 목표는 원래의 의미와 의도를 보존하면서 텍스트를 **자연스럽게 인간이 작성한 것처럼** 만드는 것입니다
- 우선순위에 따라 구성된 6개 카테고리의 40가지 고유 패턴을 분석하며, 각 패턴은 S1/S2/S3 심각도로 분류됩니다

## 작업 설명

실행될 때 다음을 수행합니다:

1. **한국어 텍스트 읽기** - 사용자가 제공한 텍스트(인자, 대화 컨텍스트, 또는 파일)
2. **텍스트 분석** - 아래에 설명된 40가지 검출 패턴에 대해 분석
3. **AI 작문 마커 식별** - 각 발견 사항에 대한 과학적 근거와 함께
4. **텍스트 재작성** - 자연스럽고 인간이 작성한 것처럼 들리도록
5. **수정된 버전 제시** - 주요 변경 사항의 요약과 함께(선택적)

**프로세스 가이드라인**:
- 항상 원래의 의미와 사실적 내용을 보존하세요
- 적절한 격식 수준(격식체 vs 반말)을 유지하세요
- 완벽함보다 자연스러움에 집중하세요
- 중요한 변경 사항에 대한 근거를 설명하세요
- 텍스트가 AI 마커를 거의 보이지 않으면, 이미 자연스럽다고 인정하세요

## 한국어 AI 작문 특징

LLM이 생성한 한국어 텍스트는 인간의 글쓰기와 측정 가능하게 다른 고유한 언어적 패턴을 보입니다:

- **문장부호**: 과도한 쉼표 사용(61% vs 인간 26%), 영어식 쉼표 배치
- **띄어쓰기**: 의존명사 띄어쓰기의 경직된 일관성(SD=0.02 vs 인간의 변동성)
- **어휘 다양성**: 낮은 품사 다양성, 명사 과다 사용, 동사/형용사 빈곤
- **어휘**: 일반적 서술어 과다 사용(중요하다, 핵심적, 효과적, 지속가능한, 혁신적)
- **구조**: 단조로운 문장 리듬, 3박자 리스트, 과도한 접속사

이러한 패턴은 LLM 학습 데이터 편향과 확률적 텍스트 생성에서 나타나며, 한국어 원어민 독자에게 부자연스럽게 느껴질 수 있습니다.

---

## 검출 패턴 (총 40가지)

### 패턴 분류 체계

모든 패턴은 검증 수준과 심각도에 따라 메타데이터를 포함합니다.

**검증 수준**:
- **✅ 과학적**: KatFishNet 논문(ArXiv 2503.00032v4, 2025) 등 외부 문헌으로 검증된 패턴 — 검증 필드에 인라인 인용 (예: `검증: ✅ 과학적 (KatFishNet, 94.88% AUC)`)
- **📊 경험적**: 실무 관찰을 기반으로 한 커뮤니티 기여 패턴

**심각도(severity)**:
- **S1 (Critical)**: 한 번만 나와도 AI 신호로 결정적. 무조건 수정 권장.
- **S2 (Strong)**: 1~2회 허용, 3회+ 반복 시 AI 신호. 빈도 기반 판정.
- **S3 (Weak)**: 단독으로는 약한 신호이나 다른 패턴과 중첩될 때 결정적.

**자연도 등급(Naturalness Grade)** — v1.6.0 신설:

윤문 후 텍스트의 자연스러움을 한 글자로 요약. 출력 형식의 "자연도 등급" 라인에 표시.

- **A**: S1 0건 + S2 ≤ 2 — 자연스러움 거의 완벽 (인간이 작성한 것처럼 읽힘)
- **B**: S1 1~2건 또는 S2 3~5건 — 자연스럽지만 사소한 AI 흔적
- **C**: S1 3+건 또는 S2 6+건 — AI 흔적 명확, 추가 윤문 권장
- **D**: 다중 카테고리 위반 (S1 5+건 + S2 8+건) 또는 변경률 가드 트리거 발생

자연도 등급은 윤문 **후** 텍스트에 적용 — 윤문 결과의 품질을 한 줄로 평가. 사용자가 결과를 신뢰할 수 있는지 빠르게 판단하도록 돕습니다.

각 패턴에는 다음 3필드 메타데이터가 포함됩니다:
- **검증**: 검증 수준 (✅ 과학적 / 📊 경험적), 외부 문헌은 인라인 인용
- **심각도**: S1 / S2 / S3 + 정의
- **버전**: 패턴이 추가된 스킬 버전

### 빠른 참조

**카테고리별 우선순위와 대표 패턴**:

1. **문장부호** (7가지, 최고: 94.88% AUC) - 쉼표 과다, 영어식 배치, 연결어미 뒤 쉼표, 콜론 과다
2. **띄어쓰기** (3가지, 높음: 79.51% AUC) - 경직된 의존명사, 보조용언, 숫자 띄어쓰기
3. **품사 다양성** (3가지, 높음: 82.99% AUC) - 명사 과다, 동사/형용사 빈곤
4. **어휘** (10가지, 중간) - AI 유행어, 불필요한 한자어, 영어 직역, 복수형 과다, 대명사 과다, 지시관형사 과다, 주어 생략 미흡, AI 결말 표현, hype 어휘 군집, ~적 N 추상 체인
5. **구조** (4가지, 중간) - 문장 리듬, 3박자, 접속사, 경어체
6. **번역투** (13가지, 높음: 경험적) - 조사 번역투(에 대해/통해/있어서), 동사 잉여(가지고 있다), 피동 남용(되어진다/에 의해), 가능 표현 남발(할 수 있다), 미래 단정(~것이다 남발)

전체 40가지 패턴 목록과 상세 내용은 각 카테고리 참조 문서 참조.

### 상세 패턴 설명

각 패턴의 완전한 검출 기준, 과학적 근거, 예시, 교정 전략은 다음을 참조하세요:

- **문장부호 패턴 (1-7)**: [references/punctuation-patterns.md](references/punctuation-patterns.md) 참조
- **띄어쓰기 패턴 (8-10)**: [references/spacing-patterns.md](references/spacing-patterns.md) 참조
- **품사 다양성 패턴 (11-13)**: [references/pos-patterns.md](references/pos-patterns.md) 참조
- **어휘 패턴 (14-20, 37-39)**: [references/vocabulary-patterns.md](references/vocabulary-patterns.md) 참조
- **구조 패턴 (21-24)**: [references/structure-patterns.md](references/structure-patterns.md) 참조
- **번역투 패턴 (25-36, 40)**: [references/translation-ese-patterns.md](references/translation-ese-patterns.md) 참조

**상세 패턴을 로드해야 할 때**:
- 해당 카테고리의 패턴을 감지했을 때 특정 카테고리 파일을 로드하세요
- 포괄적인 분석이 필요한 경우 모든 파일을 로드하세요
- 텍스트가 최소한의 AI 마커만 보이면 로드를 건너뛰세요

---

## 작업 흐름

### 1단계: 텍스트 받기

- 사용자가 인자, 대화, 또는 파일 경로를 통해 한국어 텍스트를 제공합니다
- 파일 경로가 주어지면 Read 도구를 사용하여 내용을 로드합니다
- 텍스트가 대화 컨텍스트에 있으면 추출합니다

### 2단계: 모든 패턴에 대해 분석

다음을 체계적으로 확인합니다:
- **문장부호 패턴 (1-7)**: 쉼표 개수, 위치 확인, 불필요한 사용 식별, 영어식 콜론 사용 확인
- **띄어쓰기 패턴 (8-10)**: 의존명사 띄어쓰기 일관성, 보조용언 띄어쓰기, 숫자 띄어쓰기 확인
- **품사 패턴 (11-13)**: 명사/동사/형용사 비율 추정, 명사화 구문 식별
- **어휘 패턴 (14-20, 37-39)**: AI 유행어, 한자어 과다 사용, 영어 직역, 복수형 '-들' 과다 사용, 대명사 과다, 지시관형사 과다, 주어 생략 미흡, AI 결말 관용구·결말 공식·결말 단언, hype 어휘 군집, ~적 N 추상 체인 확인
- **구조 패턴 (21-24)**: 문장 길이 변동, 목록 패턴, 접속사, 경어체 균일성 확인
- **번역투 패턴 (25-36, 40)**: 조사 번역투(에 대해/통해/있어서), 동사 잉여(가지고 있다), 피동 남용(되어진다/에 의해), 가능 표현 남발(할 수 있다), 미래 단정(~것이다 남발) 확인

감지한 패턴에 필요한 **상세 참조 파일을 로드**하세요.

### 3단계: 발견 사항 식별

감지된 각 패턴에 대해:
- 텍스트의 구체적인 인스턴스를 기록하세요
- 과학적 근거를 설명하세요(해당되는 경우 KatFishNet 참조)
- 이 패턴이 왜 AI 작문을 나타내는지 설명하세요

### 4단계: 텍스트 재작성

- 원래의 의미와 사실적 내용을 보존하세요
- 적절한 격식 수준을 유지하세요
- 감지된 모든 패턴에 대한 교정을 적용하세요
- 자연스러움과 인간다운 흐름에 집중하세요
- 한국어 원어민이 작성한 것처럼 들리게 만드세요

### 4.5단계: 의미 보존 자체검증 (필수)

자연스러운 버전을 출력하기 전에 다음 6항을 자체 점검합니다. **위반 사항이 발견되면 해당 부분을 수정한 뒤 재점검합니다.**

- [ ] **수치·날짜·고유명사**: 원본의 숫자, 날짜, 인명, 기관명, 제품명이 모두 보존되었는가?
- [ ] **인과관계 방향**: "X 때문에 Y" → "Y 때문에 X"로 뒤바뀌지 않았는가?
- [ ] **부정 표현**: "있다/없다", "가능/불가능", "찬성/반대"가 반전되지 않았는가?
- [ ] **직접 인용**: 큰따옴표 안 직접 인용은 그대로인가?
- [ ] **핵심 주장·결론**: 원본의 메시지·결론이 변경되지 않았는가?
- [ ] **격식 수준**: 원본의 격식체/반말이 일관되게 유지되었는가?

위반 사항이 없으면 출력 형식의 "주요 변경 사항 요약" 끝에 "✓ 의미 보존 검증: 6항 모두 통과"를 명시합니다. 위반이 있었다면 수정 사항을 함께 기록합니다.

### 5단계: 결과 제시

**형식**:

```
## 분석 결과

**감지된 패턴 요약**: S1 N건, S2 M건, S3 K건
**자연도 등급**: A | B | C | D — [등급 부여 근거 한 줄]

다음 AI 작문 패턴을 감지했습니다(심각도 높은 순):

1. **패턴 X [S1]**: [발견 사항에 대한 간단한 설명]
   - [텍스트의 구체적인 예시]
   - [과학적/경험적 근거]
   - [S1/S2/S3 부여 이유]

2. **패턴 Y [S2]**: [...]

[감지된 모든 패턴에 대해 계속, S1 → S2 → S3 순으로 정렬]

---

## 자연스러운 버전

[자연스러운 한국어 글쓰기로 재작성된 텍스트]

---

## 주요 변경 사항 요약 (선택적)

- [근거와 함께 변경 사항 1]
- [근거와 함께 변경 사항 2]
- [기타]
```

**텍스트가 이미 자연스러운 경우**:

```
## 분석 결과

이 텍스트는 AI 작문 마커를 거의 보이지 않으며 상당히 자연스럽게 읽힙니다. 사소한 문제만 발견했습니다:

- [사소한 문제 1 (있는 경우)]
- [사소한 문제 2 (있는 경우)]

이 글은 이미 인간이 작성한 것처럼 들립니다. [원본 또는 약간 수정된 버전을 제공]
```

---

## 중요 사항

### 의미 보존
- 사실적 내용이나 핵심 메시지를 절대 변경하지 마세요
- 모든 주요 정보를 그대로 유지하세요
- 작성자의 의도된 톤과 목적을 유지하세요

### 변경률 가드 (Over-rewriting Guard)

자연스러운 버전을 작성한 후 **변경률**을 자체 추정해 과도한 교정을 방지하세요.

**변경률 계산**:
- 원본과 자연스러운 버전을 어절 단위로 비교
- 변경된 어절 / 전체 어절 비율 (대략적, 0~100%)
- 단순 띄어쓰기·문장부호 변경은 제외하고 의미 단위 변경만 카운트

**임계값**:
- **<30% (정상)**: 그대로 출력
- **30~50% (경고)**: 출력 끝에 "변경률 N%로 다소 큼. 의미 보존 재확인 권장" 한 줄 추가
- **>50% (중단)**: 출력하지 말고 사용자에게 확인 요청
  - 메시지: "변경률이 N%로 과도합니다. 원본 의미가 변경됐을 가능성이 있습니다. 더 보수적으로 재작성해드릴까요?"
  - 사용자 동의 시 보수적으로 재작성 후 30% 이내로 다시 시도

**근거**: 과도한 교정은 원본의 의미·뉘앙스·작성자 톤을 잃을 위험이 있음. 정량 임계로 자체 검증해 의미 보존 원칙을 강화.

### 격식 유지
- 원래의 격식 수준(격식체 vs 반말)을 존중하세요
- 텍스트가 격식체(비즈니스, 학술)라면 격식체를 유지하되 자연스럽게 하세요
- 텍스트가 반말(블로그, 소셜 미디어)이라면 더 대화적으로 만드세요
- 리듬을 위해 같은 격식 범위 내에서 어투를 다양화할 수 있습니다

### 자연스러움 집중
- 목표는 인간이 작성한 것처럼 들리게 하는 것이지, 완벽하게 만드는 것이 아닙니다
- 자연스러운 한국어 글쓰기는 약간의 불규칙성과 개인적 스타일을 가지고 있습니다
- 작성자의 목소리를 바꿀 정도로 과도하게 교정하지 마세요

### 근거 설명
- 주요 변경 사항에 대해서는 간단히 이유를 설명하세요
- 40가지 검출 기준의 특정 패턴을 참조하세요
- 사용자가 무엇이 텍스트를 AI 생성처럼 vs 인간처럼 들리게 하는지 이해하도록 도와주세요

### 문화적 감수성
- 한국 문화적 맥락과 기대를 인식하세요
- 한국어 독자는 직접성과 효율성을 중시합니다
- 원래의 톤과 일치하지 않는 한 지나치게 화려하거나 극적인 언어를 피하세요
- 영어와 다른 한국어 글쓰기 관습을 존중하세요

### 과학적 근거
- 당신의 교정은 실증적 언어학 연구에 기반합니다
- KatFishNet 논문은 대부분의 패턴에 대한 과학적 검증을 제공합니다
- 주관적인 스타일 선호가 아니라 측정 가능한 언어적 차이를 적용하는 것입니다

---

## 특수 상황

### 매우 짧은 텍스트
텍스트가 1-2문장만 있는 경우:
- 모든 패턴을 감지하기에 충분한 내용이 없을 수 있습니다
- 명백한 마커(쉼표, 어휘, 띄어쓰기)에 집중하세요
- 한계를 인정하세요: "이 텍스트는 매우 짧아서 패턴 분석이 제한적입니다."

### 이미 자연스러운 텍스트
텍스트가 AI 마커를 거의 또는 전혀 보이지 않는 경우:
- 텍스트가 잘 작성되었음을 인정하세요
- 있다면 사소한 제안을 제공하세요
- 불필요한 경우 교정을 강요하지 마세요

### 기술적이거나 전문 분야별 텍스트
텍스트에 전문 용어가 포함된 경우:
- 한자어라도 기술 용어는 보존하세요
- 문장 구조와 문장부호 패턴에 집중하세요
- 기술 글쓰기는 다른 관습을 가지고 있음을 인정하세요

### 언어 혼합
텍스트에 영어 단어나 구문이 포함된 경우:
- 영어 내용은 그대로 보존하세요
- 한국어 부분에 집중하세요
- 부자연스러운 코드 전환 패턴을 확인하세요

### 사용자가 특정 격식 요청
사용자가 격식체 또는 반말 출력을 요청하는 경우:
- 요청대로 격식 수준을 조정하세요
- 여전히 40가지 패턴 교정을 모두 적용하세요
- 요청에 따라 격식을 조정했음을 설명하세요

---

## 분석 예시

실제 적용 사례는 [examples/](examples/) 디렉토리 참조:
- [before-1.md](examples/before-1.md) / [after-1.md](examples/after-1.md): 격식체 교육 분야 텍스트
- [before-2.md](examples/before-2.md) / [after-2.md](examples/after-2.md): 다양한 패턴 예시
- [before-3.md](examples/before-3.md) / [after-3.md](examples/after-3.md): 번역투 풍부 비즈니스 보고서 (v1.4.0 신규 카테고리 시연 + S1/S2/S3 심각도 출력 형식)
- [before-4.md](examples/before-4.md) / [after-4.md](examples/after-4.md): AI 결말 관용구·~적 N·~것이다 풍부 텍스트 (v1.5.0/v1.6.0 신규 패턴 시연 + 자연도 등급 출력 형식)

각 예시는 감지된 패턴, 분석, 자연스러운 버전, 주요 변경 사항을 포함합니다.

---

## 최종 알림

- **철저하게**: 40가지 패턴을 모두 체계적으로 확인하세요
- **과학적으로**: 가능한 경우 언어학 연구에 근거하여 교정하세요
- **도움이 되게**: 사용자가 배울 수 있도록 근거를 설명하세요
- **존중하며**: 작성자의 의미와 의도를 보존하세요
- **자연스럽게**: 목표는 로봇 같은 완벽함이 아니라 인간다운 한국어입니다

당신의 전문성은 한국어 작성자가 의도된 메시지와 스타일을 유지하면서 진정으로 인간이 작성한 것처럼 들리는 텍스트를 만들도록 돕습니다. 한국어 언어학에 대한 지식과 AI 텍스트 감지 뒤의 과학적 연구를 사용하여 가치 있고 정확한 교정을 제공하세요.

---

## 답변

작성 지침을 적용해 규칙 폴더 README부터 쓰겠습니다.

---

## 답변

Now `docs/11.dstone-knowledge.md`. Let me find the sections to update.

---

## 답변

문서를 규칙 파일 기준으로 고치는 중입니다. 새 jar 재실행은 12개 중 9개까지 끝났습니다. 이어서 `.claude/docs` 메모를 고치겠습니다.

---

## 답변

Now the one-line rule in CLAUDE.md.

---

## 답변

Docs done except the verification record. Waiting for the rerun to finish.

---

## 답변

The call-argument candidate query is the slow one. Let me profile it directly.

---

## 답변

The `stmt` CTE got inlined and re-scanned for each of 8,527 rows. I'll materialize the lookups as hash joins and add the noise filters.

---

## 답변

Now I'll set up a small scratch project to exercise `argIndex`, `receiverTypes`, `template`, custom annotations and super types end to end, then rebuild and restart the test instance.

---

## 답변

Register and analyze the scratch project, then check candidates.

---

## 답변

Now the verification record in §10.2.

---

## 답변

마지막으로 저장소 규칙을 점검합니다. 람다나 메소드 참조, Lombok이 없는지 확인하고 최종 테스트를 한 번 더 돌리겠습니다.

---

## 답변

## 결과

1~5단계를 모두 구현했습니다. 새 환경에서 처음 보는 관례는 이제 Java 코드를 고치지 않고 `dstone-knowledge/conf/rules/*.yml`에 적으면 됩니다. 커밋은 하지 않았습니다.

### 단계별로 한 일

| 단계 | 내용 |
|---|---|
| 1. 규칙 로더와 검증 | 파일 하나가 규칙 묶음 하나입니다. 서버가 뜰 때 엄격하게 검사해서, 모르는 키나 없는 계층, 틀린 정규식이 있으면 서버가 뜨지 않고 `파일 자리: 이유`를 알려 줍니다. SEMANTIC을 시작할 때마다 파일을 다시 읽으므로, 규칙을 고친 뒤에는 재기동 없이 `rerunFrom=SEMANTIC`만 돌리면 됩니다. 리비전마다 적용한 묶음과 해시가 `RULE_SET` 행으로 남습니다. |
| 2. 하드코딩 목록 이전 | 플러그인 코드에 박혀 있던 목록을 규칙 파일 7개로 옮겼습니다: `java`, `servlet`, `spring`, `mybatis`, `jef`, `hi-japan`, `fallback`. `main()`, `web.xml`, Struts 설정, 호출 해석, SQL 파싱처럼 언어나 표준 형식 자체의 규칙은 코드에 남겼습니다. |
| 3. 호출 인자 규칙 확장 | 몇 번째 인자인지(`argIndex`), 호출 대상 타입(`receiverTypes`), 앞뒤에 붙일 글자(`template`), 버릴 값(`exclude`)을 지정할 수 있습니다. 둘째 인자부터의 글자를 남기도록 DECLARE를 고쳤습니다(`args_json` 컬럼). |
| 4. 기존 설정 이전 | `application.yml`의 `screen.*`, `layer.super-types` 키를 `hi-japan.yml`과 `jef.yml`로 옮겼습니다. 옛 키가 남아 있으면 지금처럼 읽되 경고를 남깁니다. 프로젝트마다 쓸 묶음을 고르는 `ruleSets`도 추가했습니다(`autoApply: false`인 묶음용). |
| 5. 후보 리포트와 시험 적용 | `GET …/rules/candidates`는 규칙이 아직 모르는 관례의 흔적과 붙여 넣을 YAML 조각을 보여 줍니다. `POST …/rules/trial`은 SEMANTIC을 실제로 돌려 전후 차이를 보여 주고 되돌립니다. 이 밖에 `GET /api/rules`, `GET …/revisions/{id}/rules`를 추가했습니다. |

### 검증

- **옮기기 전후의 결과가 같습니다.** 옮기기 전 jar와 새 jar로 실제 리비전 12개(anybiz, HI-Japan, cms4, 샘플 등)의 SEMANTIC을 각각 돌려 결과 전체를 비교했습니다. 65,247행이 같았고, 다른 것은 두 가지뿐입니다.
  - 새로 기록하는 `RULE_SET` 행
  - `SCHEDULED` 진입점 39행의 `properties_json`에 `annotation` 키가 더해진 것(LISTENER와 같은 규칙으로 처리하게 되어서 생긴 의도한 변화)
- **시험 프로젝트로 새 기능을 확인했습니다.**
  - 둘째 인자의 `NS + "list"`를 상수로 계산해 이었습니다.
  - `receiverTypes`를 주면 이름만 같은 다른 클래스의 호출이 걸러지고, 빼면 그것도 이어집니다.
  - 없는 SQL 이름은 `UNRESOLVED`로 남았고, 변수로 넘긴 이름은 잇지 않았습니다.
  - 시험 적용을 네 번 한 뒤에도 DB 행 수는 그대로였습니다(되돌리기 확인).
  - 후보 API는 `execute`의 `argIndex: 1`과 대상 타입, 처음 보는 애노테이션, 상위 타입을 정확히 찾아냈습니다.
- 단위 테스트는 기존 160건에 새로 7건을 더해 167건 모두 통과합니다.

### 알아 두실 점

- **분석기 버전을 1.0에서 1.1로 올렸습니다.** 그래서 다음 증분 분석은 한 번 전체 분석으로 돕니다. `argIndex`를 1 이상으로 쓰는 규칙은 1.1로 DECLARE를 다시 한 리비전에서만 맞습니다.
- **로컬 DB에 스키마를 적용했습니다.** 새 컬럼 `rule_sets`와 `args_json`을 넣었습니다. 이때 전부터 빠져 있던 `analysis_resource.file_id` NOT NULL 해제도 함께 들어갔는데, 처음 기준선을 만들 때 실패한 원인이 이것이었습니다.
- **12개 리비전은 SEMANTIC부터 다시 분석된 상태입니다.** 시험 프로젝트(`rule-trial-app`)는 DB에서 지웠습니다.
- **시험 적용은 큰 프로젝트에서 80초쯤 걸립니다.** SEMANTIC 한 번만큼입니다. 후보 쿼리는 처음에 58초가 걸려서 2~3초로 줄였습니다.
- **화면 이름(VIEW) 규칙은 지금처럼 따옴표 글자 하나만 봅니다.** SQL 이름처럼 상수를 따라가 계산하지는 않습니다.

규칙 작성법은 `dstone-knowledge/conf/rules/README.md`에 있습니다. 설계 문서는 `docs/11.dstone-knowledge.md` 4.7.1절에 추가했고, 검증 기록은 10.2절에 남겼습니다. CLAUDE.md와 `.claude/docs` 메모도 함께 고쳤습니다.
