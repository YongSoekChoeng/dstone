# dstoneknowledge_에서_호

> 생성일시: 2026-11-07 09:57

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
