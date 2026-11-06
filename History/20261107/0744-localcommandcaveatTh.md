# localcommandcaveatTh

> 생성일시: 2026-11-07 07:44

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

Let me read it with the Read tool in chunks.

---

## 답변

dstone-knowledge에는 "컨트롤러 → 서비스" 같은 티어별 전용 분석 로직이 따로 없습니다. 분석은 두 갈래로 나뉩니다.

- **연결(관계)**: 두 노드 사이가 어떤 종류의 연결이냐에 따라 만드는 Pass와 규칙이 다릅니다. 결과는 모두 `analysis_relation`에 `relation_type`과 신뢰도(HIGH/MEDIUM/LOW)를 달고 들어갑니다.
- **티어(layer)**: 클래스마다 `analysis_symbol.layer` 라벨을 따로 붙입니다. 이 라벨은 화면에 보여 주거나 걸러낼 때만 쓰고, 연결을 만드는 데는 쓰지 않습니다.

## 1. 구간별로 연결을 만드는 방법

```
화면 ─REQUESTS→ 진입 메소드 ─CALLS→ … ─CALLS→ DAO 메소드 ─EXECUTES_SQL→ statement ─READS/WRITES_TABLE→ 테이블
화면 ─INCLUDES→ 화면        메소드 ─RENDERS→ JSP
```

### ① 화면 → 컨트롤러 (`REQUESTS`, SEMANTIC 단계)

먼저 진입점(`analysis_endpoint`)을 플러그인이 만들어 둡니다.
- `SpringPlugin`(100): 클래스와 메소드에 붙은 `@RequestMapping` 경로를 SQL(`jsonb_array_elements_text`)로 이어 붙입니다.
- `ServletPlugin`(200): `web.xml`을 읽습니다.
- `StrutsPlugin`: `struts-config.xml`을 읽습니다.
- `RichClientPlugin`: 거래 ID 방식일 때 `TRANSACTION` 진입점을 만듭니다.

그다음 화면 쪽 텍스트를 이 진입점과 맞춰 봅니다.

| 화면 종류 | 연결 방법 | 신뢰도 |
|---|---|---|
| JSP | `/path`처럼 생긴 텍스트가 진입점 경로와 정확히 같을 때만 연결 | MEDIUM |
| WebSquare/Nexacro, 경로 텍스트 | `PATH_TEXT`. context path가 붙어 있으면 첫 구간을 떼고 다시 맞춰 봄 | MEDIUM(떼고 맞으면 LOW) |
| WebSquare/Nexacro, 거래 ID 호출 | `call-rules` 설정. 예: `doRequestAjax("JCST0200M01S")` → `performJCST0200M01S` 메소드 | 1개면 HIGH, 2~5개면 각각 LOW |
| hidden 필드 | `field-rules` 설정. 예: `tranId=perform{id}` | 같은 방식 |
| 공통 스크립트 | `com_lib.js` 같은 공통 함수 본문의 호출을 그 함수를 부르는 화면으로 전파 | MEDIUM |

원칙은 "계산하고 추측하지 않는다"입니다. 따옴표로 쓴 리터럴만 인정합니다. 변수로 넘긴 ID는 연결하지 않고 개수만 세며, 주석 안의 텍스트는 무시합니다.

### ② 컨트롤러 → 서비스 → DAO (`CALLS`, DECLARE → RESOLVE → LINK)

여기서는 티어를 구분하지 않고 Java 메소드 호출을 일반적으로 해석합니다.
1. **DECLARE**(`DeclarationCollector`): 파일 하나만 보고 호출을 해석하지 않은 채 `analysis_reference`에 저장합니다. 이름 토큰의 위치도 함께 남깁니다.
2. **RESOLVE**(`ReferenceResolver`): 아래 순서대로 시도합니다.
   - Symbol Solver로 풀리면 **HIGH**
   - 소유 타입은 알 때, 그 타입과 프로젝트 안 조상에서 이름과 인자 수로 찾음: Lombok 멤버 1개면 HIGH, 일반 멤버 1개면 MEDIUM, 여러 개면 LOW
   - 외부 타입 메소드면 `EXTERNAL_METHOD`, **MEDIUM**
   - 프로젝트 전체에서 이름과 인자 수로 찾아 후보가 5개 이하면 **LOW**
   - 여기까지 안 되면 `UNRESOLVED`와 이유를 남깁니다.

   소유 타입은 Solver가 먼저 알려 줍니다. 안 되면 변수 선언 텍스트와 import, `log` 같은 synthetic 필드, 앞선 호출의 반환 타입(빌더 체인) 순서로 추정합니다.
3. **LINK**(`LinkPass`, SQL만 씀): `OVERRIDES` 관계를 만듭니다. 호출 대상이 **추상 메소드일 때만** `CALLS_POSSIBLE_IMPLEMENTATION`을 만들어 인터페이스 호출을 구현체로 이어 줍니다. 그래서 `XxxService` 인터페이스 호출도 `XxxServiceImpl`까지 이어집니다. 구체 메소드까지 넓히면 잡음 엣지가 크게 늘어서 일부러 막았습니다.

`@Autowired` 필드는 `SpringPlugin`이 `INJECTS` 관계로 따로 저장합니다. 호출 해석에는 쓰지 않습니다.

### ③ DAO → SQL (`EXECUTES_SQL`, `MyBatisPlugin`)

`ConstantEvaluator`가 statement 이름을 계산합니다.
- 계산할 수 있는 것: 리터럴, `+`로 이은 문자열, 해당 타입과 조상의 필드 초기값, `return` 하나뿐인 인자 없는 메소드
- 지역 변수나 파라미터가 한 군데라도 섞이면 전체를 "모름"으로 처리합니다.
- JEF 프레임워크 프로젝트의 `new QueryProperty("File.id")`처럼 이름을 담는 객체를 생성하는 경우도 연결합니다.
- 같은 이름의 statement가 여러 파일에 있으면 호출자와 같은 source root에 있는 쪽을 고릅니다.

### ④ SQL → 테이블 (`READS_TABLE` / `WRITES_TABLE`)

`SqlTableExtractor`가 JSQLParser를 fast mode로만, 호출 스레드에서 실행합니다. 파싱에 실패하면 `FROM`/`JOIN`/`INTO`/`UPDATE` 정규식으로 대신 찾고 MEDIUM으로 기록합니다(`via = PATTERN`).

### ⑤ 역방향과 화면 간 연결

- `RENDERS`: `new ModelAndView("x")`나 `setViewName("x")`를 경로가 `x.jsp`로 끝나는 JSP 하나와 연결합니다.
- `INCLUDES`: 화면에서 화면으로 가는 연결입니다. `wframe`, 팝업, Div url, `open-rules`, 공통 스크립트 경유를 포함합니다.

## 2. 티어(layer) 라벨 붙이기

`SemanticPass`가 플러그인을 순서대로 실행하고, 앞에서 정한 값은 뒤 플러그인이 덮어쓰지 않습니다.

| 순서 | 플러그인 | 기준 | 신뢰도 |
|---|---|---|---|
| 100 | `SpringPlugin` | `@Controller`/`@Service`/`@Repository` 같은 스테레오타입 | HIGH |
| 200 | `ServletPlugin` | `HttpServlet` 하위 클래스 → CONTROLLER | MEDIUM |
| 800 | `ConfiguredLayerPlugin` | 설정한 상위 타입. 예: `AbstractMainService=CONTROLLER`, `JdbcDAO=REPOSITORY` | MEDIUM |
| 900 | `NamingLayerPlugin` | 클래스 이름 접미사 | LOW |

## 3. 티어를 따라가는 조회

`GraphDao.xml`의 `selectNeighborEdges`가 `analysis_relation`을 재귀 CTE로 따라갑니다.
- `direction`이 UP이면 호출하는 쪽으로, DOWN이면 호출되는 쪽으로 갑니다.
- 첫 걸음(`anchorTypes`)과 그다음 걸음(`stepTypes`)에 쓰는 관계 종류가 다릅니다. 화면에서 내려갈 때만 `REQUESTS`를, 화면으로 올라갈 때만 `RENDERS`를 따라갑니다.
- `hideLayers`에 넣은 티어(예: VO)의 메소드로는 들어가지 않습니다. getter/setter 호출이 그림을 덮지 않게 하려는 것입니다.
- 깊이(`depth`)로 끝을 정하고 `UNION`으로 중복을 없애므로 순환 호출이 있어도 멈춥니다. 같은 엣지는 가장 얕은 깊이 하나만 남깁니다.

그래서 테이블 `JC_OUTSTDNG`에서 UP으로 따라가면 SQL → DAO → 서비스 → `perform…` → 화면 15개가 나옵니다.

## 아직 처리하지 않는 것

- 상수로 지정한 매핑 경로, 상속받은 클래스 레벨 매핑
- 생성자/setter 주입
- `return "order/list";` 형식의 뷰 이름과 Tiles

LOW 신뢰도 연결은 참고용 힌트로만 봐야 합니다. Nexacro와 표준 WebSquare submission 방식은 직접 만든 샘플로만 확인했습니다.

자세한 내용은 `.claude/docs/knowledge-core.md`와 `.claude/docs/knowledge-screens-rich-client.md`에 있습니다.
