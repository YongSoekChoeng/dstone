# localcommandcaveatTh

> 생성일시: 2026-11-06 17:20

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

dstone-knowledge는 계층마다 따로 분석 로직을 두지 않습니다. 대신 **경계마다 다른 관계(엣지)를 만들어 두고**, 조회할 때 이 관계들을 이어서 따라갑니다. 계층(CONTROLLER/SERVICE/REPOSITORY)은 호출 관계를 만드는 데 쓰이지 않습니다. 나중에 타입에 붙이는 라벨일 뿐입니다.

## 전체 사슬

```
화면 ─REQUESTS→ 진입 메소드 ─CALLS→ 서비스 ─CALLS→ DAO ─EXECUTES_SQL→ statement ─READS/WRITES_TABLE→ 테이블
      (JSP/WebSquare/Nexacro)         (+ CALLS_POSSIBLE_IMPLEMENTATION: 인터페이스 → 구현체)
메소드 ─RENDERS→ JSP,  화면 ─INCLUDES→ 화면
```

## 경계별로 관계를 만드는 방법

**1. 화면 → 서버 (`REQUESTS`, SEMANTIC 단계)**
- JSP는 화면 안에서 `/path`처럼 보이는 문자열을 모두 모은 뒤, 엔드포인트 경로와 정확히 일치하는 것만 남깁니다(MEDIUM). 이 작업은 `JspPlugin`이 합니다.
- WebSquare/Nexacro는 `RichClientPlugin`(510)이 맡고, 연결 방법이 세 가지입니다.
  - 경로 문자열 일치: JSP와 같은 방식입니다.
  - **call rule**: 화면이 URL 대신 거래 ID로 서버를 부르는 경우입니다. 예를 들어 `doRequestAjax("JCST0200M01S")`는 `performJCST0200M01S` 메소드로 연결됩니다. 이 규칙에 맞는 메소드는 `TRANSACTION` 엔드포인트로도 등록됩니다.
  - field rule(hidden `tranId`)과 공통 스크립트 함수 본문 안의 호출도 연결합니다.
- 어느 방법이든 **따옴표 리터럴만** 인정합니다. 변수로 넘긴 ID는 추측하지 않습니다.

**2. 진입점 판별 (SEMANTIC)**
- `SpringPlugin`: `@RequestMapping` 등으로 HTTP/SCHEDULED/LISTENER 엔드포인트를 등록합니다. 클래스 경로와 메소드 경로는 SQL로 합칩니다.
- `ServletPlugin`(`web.xml`), `StrutsPlugin`(`struts-config.xml`), `PlainJavaPlugin`(`main`, `Runnable`)도 각자 진입점을 등록합니다.

**3. 컨트롤러 → 서비스 → DAO (`CALLS`, DECLARE → RESOLVE → LINK)**
여기에는 계층 구분이 전혀 없습니다. 모든 Java 메소드 호출을 똑같이 처리합니다.
- **DECLARE**(`DeclarePass`): 파일 하나만 파싱해서 호출을 이름과 위치만 담아 `analysis_reference`에 미해결 상태로 저장합니다.
- **RESOLVE**(`ReferenceResolver`): 호출마다 아래 순서로 대상을 찾습니다.
  1. 심볼 솔버 → HIGH
  2. 소유 타입을 알면 그 타입과 상위 타입에서 이름과 인자 수로 찾기 → MEDIUM. 후보가 여러 개면 LOW
  3. 외부 라이브러리 타입이면 `EXTERNAL_METHOD` → MEDIUM
  4. 프로젝트 전체에서 이름과 인자 수가 같은 후보가 5개 이하일 때 → LOW
  5. 그래도 없으면 `UNRESOLVED`와 이유를 남깁니다.
- **LINK**(`LinkPass`, SQL만 사용): `OVERRIDES`와 `CALLS_POSSIBLE_IMPLEMENTATION`을 만듭니다. 그래서 `orderService.cancel()`처럼 **인터페이스를 부르는 호출이 구현체 `OrderServiceImpl.cancel()`까지 이어집니다**. 이 확장은 abstract 대상에만 합니다. 일반 메소드까지 넓혔더니 노이즈가 2만 건 넘게 생겼기 때문입니다.
- `@Autowired` 필드는 `SpringPlugin`이 `INJECTS` 관계로 따로 기록합니다.

**4. DAO → SQL → 테이블 (`EXECUTES_SQL`, `READS/WRITES_TABLE`)**
- `ResourcePass`가 MyBatis/iBATIS/Query XML을 `analysis_mapper`에 저장합니다.
- `MyBatisPlugin`은 `sqlSession.selectList("ns.id", …)`의 첫 번째 인자를 `ConstantEvaluator`로 **계산합니다**. 리터럴, `+`, 상수 필드, `return` 하나뿐인 메소드까지만 계산하고, 값을 모르는 부분이 하나라도 있으면 전체를 모르는 것으로 봅니다. Query XML은 `new QueryProperty("File.id")`처럼 statement를 만드는 쪽을 읽습니다.
- 테이블은 `SqlTableExtractor`가 JSQLParser fast mode로 뽑습니다. 파싱에 실패하면 `FROM`/`JOIN`/`INTO`/`UPDATE` 정규식으로 대신합니다(MEDIUM).

## 계층 라벨 (`analysis_symbol.layer`)
아래 플러그인이 순서대로 실행되고, 앞에서 정한 값은 뒤에서 덮어쓰지 않습니다.
- `SpringPlugin`(100): `@Controller`/`@Service`/`@Repository` → HIGH
- `ServletPlugin`(200): `HttpServlet` 하위 타입 → CONTROLLER, MEDIUM
- `ConfiguredLayerPlugin`(800): 설정한 상위 타입 기준 → MEDIUM. 예: `AbstractMainService=CONTROLLER`, `JdbcDAO=REPOSITORY`. 어노테이션이 없는 레거시 프로젝트를 위한 규칙입니다.
- `NamingLayerPlugin`(900): 클래스 이름 접미사 → LOW, 비어 있는 것만 채웁니다.

이 라벨은 화면 표시와 RAG 문서 문구에 쓰이고, 호출 관계를 만들 때는 쓰지 않습니다.

## 조회할 때
`/callees`, `/callers`는 `RelationDao.xml`의 `selectCallees`/`selectCallers`가 처리합니다. 이 쿼리는 `analysis_relation`을 `WITH RECURSIVE`로 따라가며, 따라갈 관계 종류를 목록으로 받습니다. 깊이는 최대 5까지이고, `UNION`으로 중복을 없애 순환 호출이 있어도 끝납니다. 같은 엣지가 여러 깊이에서 나오면 가장 얕은 것 하나만 남깁니다. 결과적으로 "화면 → 거래 메소드 → 서비스 → DAO → SQL → 테이블"이 한 번의 탐색으로 이어지고, 반대 방향(테이블 → 화면)도 똑같이 됩니다.

정리하면 계층마다 전용 로직이 있는 게 아니라, **경계의 종류**(화면↔서버, Java↔Java, 인터페이스↔구현, Java↔SQL, SQL↔테이블)마다 관계를 만드는 단계가 따로 있는 구조입니다.

자세한 설계 메모는 `.claude/docs/knowledge-core.md`와 `knowledge-screens-rich-client.md`에 있습니다. 특정 단계를 코드로 더 따라가 보고 싶으면 말씀해 주세요.

---

## 질문

이 내용을 아래의 문서에 추가해줘. 
내용은 직관적이고 쉽고 자세하게 해줘.
/docs/11.dstone-knowledge.md

---

## 답변

플러그인 순서와 노드 맵에서 계층이 쓰이는 방식을 코드로 확인해 두 군데를 바로잡고, 문서에 넣겠습니다.

---

## 답변

`docs/11.dstone-knowledge.md`에 **0.9절 「계층을 넘는 호출은 어떻게 이어지나」**를 새로 넣었습니다. 처음 읽는 사람용 안내인 0절 안, 0.8절(호출 하나는 어떻게 풀리나) 바로 뒤입니다.

**들어간 내용**
- **결론을 먼저 둠**: 계층마다 따로 도는 분석 로직은 없고, 경계의 종류마다 관계를 만드는 담당이 다르다는 점을 맨 앞에 썼습니다.
- **흐름 그림(mermaid)**: 화면 → 컨트롤러 → 서비스 → 구현체 → DAO → SQL → 테이블. 화살표마다 관계 이름과 그 관계를 만드는 분석 단계(③ DECLARE, ⑥ RESOLVE, ⑦ LINK, ⑧ SEMANTIC)를 적었습니다.
- **경계별 요약 표**: 경계 8가지마다 소스 예시, 생기는 관계, 담당, 근거를 정리했습니다.
- **경계별 설명 ①~⑥**:
  - 화면 → 서버: JSP 경로 맞추기, call rule, field rule, 공통 스크립트
  - 주소 → 처리 메소드
  - `CALLS`: 주입 방식과 상관없이 필드 타입만 알면 풀린다는 점
  - 인터페이스 → 구현체: 일반 메소드까지 넓히지 않는 이유(노이즈 27,715건)
  - DAO → SQL: 이름을 계산할 수 있는 경우와 없는 경우를 표로 정리
  - SQL → 테이블
- **계층 이름표**: 플러그인 순서(100/200/250/800/900)와 신뢰도를 표로 넣었습니다. 이름표는 화면 표시와 검색에만 쓰이고, 호출 관계를 만들 때는 쓰이지 않는다는 점도 적었습니다.
- **조회할 때 이어 걷기**: `/callees`, `/callers`, `/impact`가 따라가는 관계 종류를 표로 넣고, `struts-app` 예시를 깊이 3까지 펼쳤습니다. 깊이 제한, 순환 호출, 프로젝트 밖으로 나가는 호출을 막는 안전장치도 설명했습니다.
- **사슬이 끊기는 곳**: 변수로 넘긴 ID, 리플렉션, `DispatchAction`, `return "뷰이름"` 방식, `LOW` 신뢰도, 구현체가 여럿인 경우를 표로 정리했습니다.

**번호 변경**: 기존 0.9~0.15절이 0.10~0.16절로 하나씩 밀렸습니다. 문서 안팎에서 이 절 번호를 가리키는 곳은 없어서 깨진 참조는 없습니다.

플러그인 순서는 코드의 `order()` 값으로, 기대 결과는 `struts-app/README.md`로 확인했습니다. 그 과정에서 초안 두 군데를 고쳤습니다. 하나는 Struts 플러그인 순서를 250으로 적은 것이고, 다른 하나는 노드 맵이 계층을 "색"이 아니라 "열 배치"에 쓴다는 점입니다.

커밋은 하지 않았습니다. 필요하면 말씀해 주세요.

---

## 질문

올려줘.

---

## 답변

`docs/11.dstone-knowledge.md` 변경을 커밋해서 GitHub `origin/main`에 올렸습니다. 이 저장소의 기존 커밋처럼 `main`에 바로 커밋했고, 커밋은 `e145c4be docs(knowledge): 계층을 넘는 호출 연결 방식 설명 추가 (0.9절)`입니다.
