# localcommandcaveatTh

> 생성일시: 2026-11-06 14:01

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-knowledge 에서
호출관계를 분석하는데에 있어서 티어마다(ui -> 컨트롤러 -> 서비스 -> ...) 분석하는 로직이 어떻게 되?

---

## 답변

먼저 결론부터 말씀드리면, dstone-knowledge에는 티어별 분석 로직이 따로 없습니다. 호출 관계는 티어와 무관한 하나의 관계 테이블(`analysis_relation`)에 엣지로 쌓입니다. 티어(계층)는 타입에 붙이는 라벨(`analysis_symbol.layer`)일 뿐이고, 엣지를 만들 때는 이 라벨을 보지 않습니다. 대신 구간마다 엣지를 만드는 주체가 다릅니다.

## 구간별로 엣지를 만드는 곳

```
화면 ─REQUESTS→ 컨트롤러 메소드 ─CALLS→ 서비스 ─CALLS→ DAO ─EXECUTES_SQL→ statement ─READS/WRITES_TABLE→ 테이블
            (컨트롤러 ─RENDERS→ JSP,  화면 ─INCLUDES→ 화면)
```

| 구간 | 관계 | 만드는 곳 | 방식 |
|---|---|---|---|
| UI → 컨트롤러 | `REQUESTS` | SEMANTIC: `JspPlugin`, `RichClientPlugin` | 화면 안의 `/path` 텍스트가 endpoint 경로와 정확히 같을 때 연결합니다(MEDIUM). WebSquare/Nexacro는 call rule(`doRequestAjax=perform{id}`), field rule(`tranId=...`), 공통 스크립트 함수 추적도 씁니다. endpoint는 `SpringPlugin`(`@RequestMapping`), `ServletPlugin`(`web.xml`), `StrutsPlugin`, `TRANSACTION`(거래 ID)에서 나옵니다. |
| 컨트롤러 → 서비스 → DAO (Java 사이 호출 전부) | `CALLS` | DECLARE → RESOLVE (`ReferenceResolver`) | 아래 "Java 호출 해석"에서 설명합니다. |
| 인터페이스 → 구현체 | `CALLS_POSSIBLE_IMPLEMENTATION` | LINK (`LinkPass`, SQL만 사용) | `OVERRIDES`를 만든 뒤, 추상 메소드를 부르는 호출을 구현 메소드로 펼칩니다. `Service` 인터페이스를 거쳐 `ServiceImpl`로 넘어가는 구간이 여기서 이어집니다. |
| 주입 | `INJECTS` | SEMANTIC: `SpringPlugin` | `@Autowired` 필드만 다룹니다. 생성자·setter 주입은 아직 지원하지 않습니다. |
| DAO → SQL | `EXECUTES_SQL` | SEMANTIC: `MyBatisPlugin` + `ConstantEvaluator` | `selectList("ns.id")`처럼 첫 인자를 계산해서 statement를 찾습니다. 리터럴, `+`, 상수 필드만 인정하고, 변수가 하나라도 섞이면 연결하지 않습니다. JEF는 `new QueryProperty("File.id")`로 처리합니다. |
| SQL → 테이블 | `READS_TABLE` / `WRITES_TABLE` | `SqlTableExtractor` | JSQLParser를 fast mode로만 씁니다. 파싱에 실패하면 정규식으로 대신합니다(MEDIUM). |
| 컨트롤러 → JSP | `RENDERS` | `JspPlugin` | `new ModelAndView("x")` / `setViewName("x")` |

## Java 호출 해석 (컨트롤러·서비스·DAO 공통)

1. **DECLARE** (`DeclarationCollector`): 파일 하나만 보고 심볼을 만들고, 호출은 해석하지 않은 채 `analysis_reference`에 위치와 함께 저장합니다.
2. **RESOLVE** (`ReferenceResolver`): 참조 하나마다 아래 순서로 시도합니다.
   - JavaSymbolSolver(`DbTypeSolver`가 DB에서 선언 파일을 찾아 파싱) → **HIGH**
   - owner 타입은 알고, 그 타입과 상위 타입에서 이름·인자 수로 찾음 → **MEDIUM** (후보가 여러 개면 LOW)
   - owner가 외부 타입이면 `EXTERNAL_METHOD` → MEDIUM
   - 프로젝트 전체에서 이름·인자 수가 같은 후보가 5개 이하 → **LOW** (참고용 힌트일 뿐입니다)
   - 그래도 없으면 `UNRESOLVED`와 이유를 남깁니다.

   owner 타입은 solver가 알려 주고, 모를 때는 변수 선언 텍스트와 import, Lombok `log`, 앞쪽 호출의 반환 타입(빌더 체인)으로 추정합니다.

## 티어(layer) 라벨을 붙이는 순서

SEMANTIC 플러그인이 순서대로 실행되고, 앞에서 정한 값은 뒤에서 덮어쓰지 않습니다.

1. `SpringPlugin`(100): `@Controller`/`@Service`/`@Repository`/`@Mapper` 등 → HIGH
2. `ServletPlugin`(200): `HttpServlet` 하위 클래스 → CONTROLLER, Filter → WEB_FILTER
3. `ConfiguredLayerPlugin`(800): 설정 `dstone.knowledge.semantic.layer.super-types`에 적은 상위 타입 → MEDIUM (예: JEF의 `AbstractMainService=CONTROLLER`)
4. `NamingLayerPlugin`(900): 접미사 규칙(`Controller|Action$`, `Service|ServiceImpl|Biz$`, `DAO|Mapper$` 등) → LOW, 아직 비어 있는 타입에만 붙입니다.

이 라벨은 조회·문서·화면 표시에만 쓰입니다. 예를 들어 RAG METHOD 문서에 "계층: SERVICE"가 들어갑니다. 엣지를 연결할지는 라벨과 상관없이 정해집니다.

## 조회할 때 (티어를 따라 내려가기)

`RelationDao.xml`의 `WITH RECURSIVE walk` CTE가 엣지를 `depth`만큼(서비스 기준 최대 5) 따라갑니다. callees는 `CALLS`, `CALLS_POSSIBLE_IMPLEMENTATION`, `EXECUTES_SQL`, `READS/WRITES_TABLE`, `RENDERS`를 따라가고, callers는 여기에 `REQUESTS`를 더합니다. `UNION`이 같은 행을 다시 넣지 않고 depth에 상한이 있어서 순환 호출이 있어도 끝납니다. 영향도 분석(테이블에서 화면까지)도 같은 그래프를 거꾸로 올라갈 뿐입니다.

## 현재 한계

- 생성자·setter 주입, 상수로 쓴 매핑 경로, 상속받은 클래스 레벨 매핑은 처리하지 않습니다.
- `return "order/list";` 형태의 view 이름과 Tiles는 `RENDERS`로 잡지 않습니다.
- statement 이름이 변수로 넘어오면 `EXECUTES_SQL`가 끊깁니다. 추측하지 않는다는 원칙 때문입니다.
- LOW 신뢰도 엣지는 정확도가 낮습니다. 측정 결과 27개 중 11개만 맞았습니다.

자세한 근거는 `.claude/docs/knowledge-core.md`(RESOLVE/LINK/SEMANTIC/M5)와 `knowledge-screens-rich-client.md`(화면 쪽 규칙)에 있습니다.

---

## 질문

내가 염려하는 바는 어플리케이션마다 UI -> 컨트롤러 호출방식이 다를텐데 어플리케이션이 추가되면 어떻게 대응해야 하나야?
이 모든 파싱로직이 소스에 녹아있나? 혹은 설정처럼 밖으로 빠져있어서 호출로직이 다른 어플리케이션이 추가될 때마다 설정만 추가하면 되는 식으로 되어있나?

---

## 답변

섞여 있습니다. **흔한 몇 가지 호출 방식만 설정으로 빠져 있고, 나머지 대부분은 소스에 들어 있습니다.** 그리고 지금 있는 설정도 프로젝트별이 아니라 서버 전체에 하나만 있습니다.

## 1. 설정으로 바꿀 수 있는 것

`conf/application.yml`의 `dstone.knowledge.semantic.*` 아래에 있는 다섯 개뿐입니다.

| 키 | 대상 | 예 |
|---|---|---|
| `screen.call-rules` | 화면이 **함수(거래 ID)**로 서버를 부르는 방식 | `doRequestAjax=perform{id}` |
| `screen.field-rules` | 폼의 **hidden 필드 값**으로 거래 ID를 보내는 방식 | `tranId=perform{id}` |
| `screen.open-rules` | 화면 ID로 다른 화면을 여는 방식 | `fnOpenPage={id}.xml` |
| `screen.menu-rules` | 메뉴 목록에서 화면명을 가져오는 방식 | `MENU_NM=SRC_PATH` |
| `layer.super-types` | 자체 프레임워크의 상위 타입으로 계층 정하기 | `...AbstractMainService=CONTROLLER` |

화면 쪽 규칙은 모두 한 가지 틀만 표현합니다. "어떤 함수(또는 필드)의 **첫 인자가 따옴표 리터럴**이면, 그 값을 `{id}`에 넣은 이름의 메소드(또는 화면)와 잇는다"는 틀입니다. 새 애플리케이션의 호출 방식이 이 틀에 들어맞으면 설정 한 줄을 추가하고 `rerunFrom: SEMANTIC`으로 다시 돌리면 됩니다.

## 2. 소스에 들어 있는 것

| 무엇 | 위치 |
|---|---|
| URL 경로 텍스트를 endpoint 경로와 정확히 맞추는 방식, 컨텍스트 경로를 떼고 다시 시도하는 규칙 | `JspPlugin`, `RichClientPlugin`, `ScreenScript` |
| endpoint 만들기: `@RequestMapping` 계열, `web.xml`, `struts-config.xml`/`struts.xml` | `SpringPlugin`, `ServletPlugin`, `StrutsPlugin` |
| WebSquare/Nexacro 파일 판별, `<xf:submission>`, `wframe`, `Div url` 같은 태그 해석 | `FileClassifier`, `RichClientPlugin` |
| 공통 스크립트 함수(`com_lib.js`) 추적 | `ScriptFunctions` |
| 계층 애노테이션 목록, 이름 접미사 규칙(`Controller$`, `Service$`, `DAO$` …) | `SpringPlugin`, `NamingLayerPlugin` (Java 배열) |
| `ModelAndView`/`setViewName`으로 찾는 `RENDERS` | `JspPlugin` |
| SQL을 부르는 방식(MyBatis/iBATIS, JEF `QueryProperty`), SQL 파일 형식 | `MyBatisPlugin`, `MapperXmlReader` |
| 컨트롤러 → 서비스 → DAO 사이의 Java 호출 | `ReferenceResolver` (프레임워크와 무관한 일반 로직이라 설정이 필요 없습니다) |

## 3. 애플리케이션이 추가될 때의 대응

| 새 애플리케이션의 UI → 컨트롤러 방식 | 대응 |
|---|---|
| URL 경로(`/order/list.do`)를 문자열로 부르고, 서버는 Spring/Servlet/Struts | **아무것도 안 해도 됩니다** |
| `xxx("거래ID", …)` 형태의 공통 함수 + 메소드 이름 규칙(`perform{id}`) | **설정 추가** (`call-rules`) |
| hidden 필드로 거래 ID 전달 | **설정 추가** (`field-rules`) |
| 자체 프레임워크라 애노테이션도 접미사도 없음 | **설정 추가** (`super-types`) |
| 거래 ID → 클래스 매핑이 **XML이나 DB 테이블**에 있음 (예: `<service id="X" class="..."/>`) | **코드 필요**: 새 `SemanticPlugin` |
| 거래 ID와 메소드 이름 사이에 규칙이 없음 (`{id}` 템플릿으로 표현할 수 없음) | **코드 필요** |
| URL을 상수나 변수로 조립 (`BASE + "/list.do"`) | 지금은 연결 안 됨 ("계산하고 추측하지 않는다" 원칙). 화면 쪽 상수 평가를 코드로 추가해야 합니다 |
| 새 화면 기술 (React/Vue 빌드 결과, MiPlatform, Flex 등) | **코드 필요**: 파일 판별부터 추가 |
| 컨트롤러 프레임워크가 다름 (JAX-RS, 자체 Dispatcher 등) | **코드 필요**: endpoint를 만드는 플러그인 |

정리하면 "함수 이름 + 리터럴 ID + 이름 규칙" 형태의 변형은 설정으로 해결되고, 매핑 정보를 다른 파일에서 읽어야 하거나 새 프레임워크인 경우에는 플러그인 하나를 추가해야 합니다. 플러그인 구조(`SemanticPlugin` bean 추가, 기존 `analysis_*` 테이블에서 SQL로 도출) 덕분에 다른 코드를 건드릴 필요는 없습니다.

## 4. 지금 구조에서 신경 쓰이는 점

- **규칙이 서버 전체에 하나뿐입니다.** 프로젝트 테이블(`ProjectDao.xml`)에는 `root_packages`, `java_version`, `classpath` 정도만 있고 규칙 컬럼이 없습니다. 지금은 "그 함수가 없는 프로젝트에서는 아무 일도 하지 않으니 규칙을 모아 적어도 된다"는 전제로 운영합니다. 그래서 두 애플리케이션이 **같은 함수 이름을 다른 의미로** 쓰면 충돌합니다. 예를 들어 A는 `doRequestAjax=perform{id}`, B는 `doRequestAjax=exec{id}`인 경우입니다.
- 설정을 바꾸면 재기동한 뒤 `rerunFrom: SEMANTIC`으로 다시 돌려야 하고, 계층이나 endpoint가 바뀐 TYPE/METHOD 청크는 다시 임베딩됩니다.
- `NamingLayerPlugin`의 접미사 규칙은 Java 배열에 박혀 있어서, 접미사 체계가 다른 프로젝트(예: `…Ctl`, `…Svc`)는 코드를 고쳐야 합니다.

필요하시면 다음 두 가지를 개선할 수 있습니다.
1. 규칙을 **프로젝트별**로 저장 (프로젝트 등록 API의 필드로 받거나 별도 테이블에 저장하고, 없으면 전역값 사용)
2. 하드코딩된 이름 접미사 규칙과 계층 애노테이션 목록을 설정으로 빼기

진행할지 알려 주세요.

---

## 질문

1, 2를 적용했을때의 영향도를 아직 잘 모르겠어.

---

## 답변

코드 확인을 마쳐서 영향도를 정리했습니다. 두 가지 모두 기본값을 지금 동작과 똑같이 두면 기존 분석 결과는 하나도 바뀌지 않습니다. 영향은 새로 생기는 선택지와 그 관리 쪽에 몰려 있습니다.

## 1. 규칙을 프로젝트별로 저장

### 바꿔야 하는 곳

| 영역 | 파일 | 내용 |
|---|---|---|
| DB | `schema/02-create-table-postgresql-dstone-knowledge.sql` | `analysis_project`에 컬럼 추가. `classpath`처럼 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`로 넣으므로 기존 DB도 그대로 씁니다. 형태는 규칙 5개를 담는 `semantic_rules JSONB` 한 컬럼을 권장합니다. |
| DAO | `ProjectDao.xml` | 조회 컬럼과 등록·수정 SQL |
| API | `ProjectService`, `ProjectController` | 등록 요청에서 `rules` 필드를 받습니다. 필드가 빠진 기존 요청은 지금처럼 동작합니다. |
| 분석 | `RichClientPlugin`, `ScreenMenuPlugin`, `ConfiguredLayerPlugin` | `configProperty.getProperty(...)` 대신 "프로젝트 값이 있으면 그 값, 없으면 전역값"을 읽습니다. 프로젝트 정보는 이미 `AnalysisJobContext.getProjectValue()`로 플러그인까지 전달되고 있어서 구조를 바꿀 필요가 없습니다. |
| 화면 | `dstone-boot`의 `knowledge/assets/js/project.js`와 등록 화면 JSP | 입력란 추가. 화면은 나중에 해도 API만으로 쓸 수 있습니다. |
| 문서 | `docs/11.dstone-knowledge.md`, `.claude/docs/knowledge-screens-rich-client.md` | 규칙을 어디에 적는지 갱신 |

**바꿀 필요가 없는 곳**
- DECLARE, RESOLVE, LINK와 CTE 조회
- `dstone-ai-engine`의 `KnowledgeTool`: 프로젝트 등록 필드를 쓰지 않습니다.
- 증분 분석: LINK, SEMANTIC, DOCUMENT는 증분 분석에서도 항상 리비전 전체를 다시 계산합니다. 규칙이 어디에서 오든 "증분 결과 = 전체 결과" 조건은 깨지지 않습니다.

### 주의할 점

1. **규칙의 우선순위**: "프로젝트 규칙이 있으면 전역 규칙 대신 쓴다"와 "전역 규칙에 더한다" 중 하나를 정해야 합니다. 충돌을 피하려는 게 목적이므로 **대신 쓰는 쪽**을 권장합니다. 다만 이렇게 하면 HI-Japan 규칙을 그 프로젝트로 옮긴 뒤에 전역 기본값을 비울지도 함께 정해야 합니다.
2. **재현성**: 규칙을 프로젝트에 저장하면 규칙을 바꾼 뒤 예전 리비전을 `rerunFrom`으로 다시 돌릴 때 결과가 달라집니다. 전역 설정도 지금 같은 문제가 있지만, 프로젝트별로 나뉘면 바뀌는 일이 더 잦아집니다. 분석할 때 쓴 규칙을 `analysis_job.options_json`(이미 있는 컬럼)에 남겨 두면 나중에 확인할 수 있습니다.
3. **리비전 비교 화면**: 두 리비전 사이에 규칙이 달라지면 코드 변경이 없어도 `REQUESTS`나 계층이 바뀐 것으로 보입니다. 2번의 기록이 있으면 원인을 설명할 수 있습니다.
4. **재임베딩**: 규칙을 바꿔 다시 돌리면 endpoint와 계층 문구가 바뀐 TYPE/METHOD 청크를 다시 임베딩합니다. 지금과 같은 비용입니다.

## 2. 이름 접미사 규칙과 계층 애노테이션을 설정으로 빼기

### 바꿔야 하는 곳

| 영역 | 파일 | 내용 |
|---|---|---|
| 분석 | `NamingLayerPlugin` | Java 배열 `RULES`(10개 정규식)를 설정에서 읽습니다. SQL(`updateLayerByName`, PostgreSQL `~`)은 그대로입니다. |
| 분석 | `SpringPlugin` | 계층 애노테이션 목록(`Controller`, `Service`, `Repository`, `Mapper` …)을 설정에서 읽습니다. SQL(`updateLayerByAnnotation`)은 그대로입니다. |
| 설정 | `conf/application.yml` | 지금 값을 기본값으로 그대로 옮깁니다. |
| 1과 함께 할 때 | 위 `semantic_rules` | 프로젝트별로 덮어쓰기 |

### 주의할 점

1. **설정을 잘못 적었을 때 생기는 위험이 새로 생깁니다.** 지금은 코드에 박혀 있어서 틀릴 일이 없습니다. 설정으로 빼면 정규식 오타가 생길 수 있습니다. PostgreSQL 정규식 오류는 SEMANTIC 트랜잭션 전체를 실패시키므로, 부팅 시나 등록 시에 Java로 미리 검증해야 합니다. `ConfiguredLayerPlugin`이 잘못된 규칙을 버리는 방식을 그대로 따르면 됩니다.
2. **설정 파일이 커집니다.** 정규식 10줄과 애노테이션 목록이 `application.yml`에 들어갑니다. 실제로 이 값을 바꿀 프로젝트가 드물다면 득보다 실이 클 수 있습니다.
3. **적용 순서는 그대로 유지합니다.** 애노테이션(HIGH) → 서블릿 → 설정 상위 타입(MEDIUM) → 이름(LOW) 순서이고 앞에서 정한 값은 덮어쓰지 않습니다. 따라서 이름 규칙을 넓혀도 애노테이션이나 상위 타입으로 정한 계층은 바뀌지 않습니다.
4. `dstone-boot`의 `knowledge-terms.js`는 계층 **이름**(CONTROLLER 등)만 표시합니다. 계층 종류 자체를 늘리지 않는 한 화면은 바꾸지 않아도 됩니다.

## 결론

| | 기존 결과 변화 | 작업량 | 효과 |
|---|---|---|---|
| 1. 프로젝트별 규칙 | 없음 (전역값으로 대체) | 중간: DB 컬럼 1개, API, 플러그인 3개, 화면 | **큼**: 애플리케이션이 늘어날 때 생기는 충돌 문제를 직접 해결합니다. |
| 2. 하드코딩 규칙을 설정으로 | 없음 (기본값 = 현재 값) | 작음: 플러그인 2개, 검증 | 작음: 접미사 체계가 다른 프로젝트가 나올 때만 쓸모가 있습니다. |

1은 지금 진행할 가치가 있다고 봅니다. 2는 1에 같이 얹되 범위를 좁히는 쪽을 권합니다. 이름 규칙(`NamingLayerPlugin`)만 프로젝트별로 "추가"할 수 있게 하고, 애노테이션 목록은 표준 Spring이라 코드에 남겨 둡니다.

검증은 HI-Japan의 규칙을 전역 설정에서 프로젝트로 옮긴 뒤 `rerunFrom: SEMANTIC`으로 다시 돌려서 하면 됩니다. `REQUESTS`, `INCLUDES`, `TRANSACTION` 수와 계층 분포가 지금 수치(680 + 2, 399, 644 …)와 같으면 영향이 없다는 확인이 됩니다.

이 방향으로 진행할지, 규칙 우선순위를 "대신 쓴다"로 할지 정해 주세요.
