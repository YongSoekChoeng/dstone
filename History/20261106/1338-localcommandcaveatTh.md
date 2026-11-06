# localcommandcaveatTh

> 생성일시: 2026-11-06 13:38

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
