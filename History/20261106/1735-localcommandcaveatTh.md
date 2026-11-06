# localcommandcaveatTh

> 생성일시: 2026-11-06 17:35

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

---

## 질문

<pasted_content id="75cd">
혹시 이런 엣지 관계를 소스 바깥으로 빼서 설정자료처럼 관리할 수 없을까?
추후 새로운 환경의 어플리케이션에 대해서 분석을 해야 할 때 소스를 건드리지 않고 설정자료만 작업하면 되도록 말이야.
가능성을 분석해줘.
</pasted_content id="75cd">

---

## 답변

현재 어떤 연결 규칙이 이미 설정으로 빠져 있고, 어떤 규칙이 코드에 박혀 있는지부터 확인하겠습니다.

---

## 답변

Now the hardcoded parts — the plugins' built-in lists.

---

## 답변

## 결론

**가능합니다.** 다만 엣지 전체를 설정으로 뺄 수 있는 건 아닙니다. 엣지는 성격에 따라 세 종류로 나뉘고, 그중 설정으로 빼기에 맞는 것은 "프레임워크와 프로젝트의 관례"에 해당하는 부분입니다. 이 부분은 지금 절반쯤 설정으로 빠져 있고, 나머지 절반은 코드 안에 목록으로 박혀 있어서 옮기기도 어렵지 않습니다.

## 1. 엣지의 세 종류

| 종류 | 엣지 | 설정으로 뺄 수 있나 |
|---|---|---|
| **Java 언어 규칙** | `CALLS`, `EXTENDS`/`IMPLEMENTS`, `OVERRIDES`, `CALLS_POSSIBLE_IMPLEMENTATION` | 뺄 필요가 없습니다. 어느 프로젝트에서나 똑같이 적용되는 규칙입니다 |
| **프레임워크 관례** | 진입점, 계층, `INJECTS`, `EXECUTES_SQL`, `RENDERS` | **가능합니다.** 지금은 대부분 코드에 박혀 있습니다 |
| **프로젝트 관례** | 거래 ID 연결, 화면 열기, 상위 타입별 계층 | **이미 설정으로 빠져 있습니다** |

새 환경을 분석할 때 달라지는 것은 거의 항상 둘째와 셋째 줄입니다. 그래서 목표 자체는 현실적입니다.

## 2. 현재 상태

**이미 설정에 있는 것** (`conf/application.yml`의 `dstone.knowledge.semantic.*`)
- `screen.call-rules`: `doRequestAjax=perform{id}`. 화면의 거래 ID 호출을 서버 메소드로 잇습니다
- `screen.field-rules`, `screen.open-rules`, `screen.menu-rules`
- `layer.super-types`: `AbstractMainService=CONTROLLER` 같은 상위 타입별 계층 규칙

HI-Japan(JEF 프레임워크)은 사실상 이 설정만으로 연결했습니다. 이 방식이 실제로 통한다는 근거입니다.

**코드에 박혀 있는 것** (모두 목록이라 옮기기 쉽습니다)

| 위치 | 박혀 있는 값 | 새 환경에서 바꿔야 하는 경우 |
|---|---|---|
| `MyBatisPlugin.MAPPER_CALLS` | `selectList`, `queryForList` 등 13개 | 자체 DAO 래퍼(`dao.select("ns.id")`처럼 다른 이름) |
| `MyBatisPlugin.NAME_HOLDERS` | `QueryProperty` | SQL 이름을 객체에 담아 넘기는 다른 프레임워크 |
| `SpringPlugin` | 계층용 애노테이션(`Controller`, `Service` 등) | 사내 커스텀 애노테이션(`@BizService`) |
| `SemanticDao.xml` | `RequestMapping` 계열, `Scheduled`, `KafkaListener` 등, 주입용 `Autowired`/`Inject`/`Resource` | 커스텀 매핑·리스너 애노테이션 |
| `SemanticDao.xml` (`RENDERS`) | `new ModelAndView`, `setViewName` | 자체 뷰 반환 방식 |
| `ServletPlugin` | `HttpServlet`, `Filter` 상위 타입 | — |
| `StrutsPlugin` | `execute`, `perform` | — |
| `NamingLayerPlugin.RULES` | 이름 끝말 정규식 | `…M`/`…S`/`…D` 같은 사내 명명 규칙 |

## 3. 제안: 3단계

### 1단계 — 박힌 목록을 설정으로 옮기기 (위험 낮음, 1~2일)
지금 값을 그대로 기본값으로 두고 위 목록을 yml로 옮깁니다. 동작은 바뀌지 않고, 바꿀 수 있는 손잡이만 생깁니다.

### 2단계 — "규칙 종류"는 Java에, "규칙 내용"은 설정에 (핵심, 1~2주)
지금의 call rule을 일반화하는 방향입니다. Java로는 **몇 가지 규칙 모양만** 만들고, 설정 파일은 그 모양을 채우기만 합니다. dstone-ai-engine에서 "로직은 범용 Tool, 내용은 정의 파일"로 나눈 원칙과 같습니다.

| 규칙 모양 | 뜻 | 흡수하는 기존 코드 / 새로 되는 것 |
|---|---|---|
| **리터럴 인자 → 이름 연결** | 호출 `X`의 첫 인자 글자를 틀에 넣어 대상(메소드/statement/화면/주소)을 찾습니다 | `MAPPER_CALLS`, `NAME_HOLDERS`, `RENDERS`, 화면 call rule. 새로 되는 것: Java → Java 디스패치(`ServiceLocator.get("orderService")`, `TxDispatcher.call("JCST0200")`) |
| **애노테이션 → 진입점/계층/주입** | `@X(path)`는 HTTP 진입점, `@Y`는 SERVICE 계층 | Spring 하드코딩 전부, 사내 애노테이션 |
| **상위 타입 → 계층/진입점** | `X`를 상속하면 CONTROLLER, `MessageListener`를 구현하면 LISTENER 진입점 | `layer.super-types`, `ServletPlugin` |
| **이름 패턴 → 계층** | 정규식 | `NamingLayerPlugin` |
| **설정 XML → 진입점** (선택) | 파일 판별 + 요소/속성 경로(XPath)로 주소와 클래스를 뽑습니다 | 사내 라우팅 XML. 단 `struts-config` 수준의 상속·forward 처리까지는 무리입니다 |

설정 예시:

```yaml
# conf/rules/jef.yml (프로젝트 등록 때 프로파일을 고른다)
edges:
  - type: literal-call           # 리터럴 인자 → 이름 연결
    call: TxDispatcher.call
    target: method
    template: "perform{id}"
  - type: literal-call
    call: dao.select
    target: statement           # EXECUTES_SQL
layers:
  annotations: { SERVICE: [BizService] }
  super-types: { CONTROLLER: [jef.application.services.AbstractMainService] }
endpoints:
  annotations: [{ name: TxMapping, kind: TRANSACTION, path-attr: value }]
```

### 3단계 — 안 하는 것
- **설정에 SQL이나 스크립트를 직접 적는 방식은 권하지 않습니다.** 검증할 수 없고, 잘못 적으면 `analysis_relation`이 오염됩니다. 규칙 모양은 늘 Java가 정합니다.
- **새 파일 형식** (새 화면 기술, 새 SQL XML 형식의 파서)은 여전히 코드 작업입니다. `QUERY_XML` 정도의 단순한 형식은 XPath 설정으로 흡수할 여지가 있습니다.

## 4. 꼭 함께 풀어야 할 점

1. **"계산하고 추측하지 않는다" 원칙은 그대로 둡니다.** 설정은 *어디를 볼지*만 정하고, 값은 여전히 따옴표 리터럴과 `ConstantEvaluator`로만 구합니다.
2. **DECLARE의 한계**: 지금은 `arg_text`에 **첫 번째 인자만**, 그것도 문자열 리터럴이 있을 때만 저장합니다(`DeclarationCollector.java:530, 568`). "두 번째 인자가 ID"인 규칙을 쓰려면 리터럴 인자를 모두 저장하도록 한 번 바꿔야 합니다. 그 뒤로는 규칙만 바꾸면 됩니다.
3. **재현성**: `analysis_*`가 원본이므로, 같은 소스라도 규칙이 바뀌면 결과가 달라집니다. **리비전마다 그때 쓴 규칙의 사본(또는 해시)을 저장해야** 리비전 비교와 증분 분석(규칙이 바뀌면 SEMANTIC 이후를 다시 돌림)이 맞게 동작합니다.
4. **프로젝트별 프로파일**: 지금은 전역 yml 하나에 여러 프로젝트 규칙을 함께 적는 방식입니다. 명명 규칙처럼 프로젝트끼리 부딪히는 규칙이 생기면 깨집니다. 프로젝트 등록 때 `ruleProfile`을 지정하고, `conf/rules/<profile>.yml`을 읽는 방식을 권합니다.
5. **적용 비용이 낮습니다**: SEMANTIC 규칙만 바뀌면 `rerunFrom: SEMANTIC`으로 다시 파싱하지 않고 돌릴 수 있습니다. 다만 TYPE/METHOD 문서 내용이 바뀌므로 해당 청크는 다시 임베딩됩니다.
6. **규칙 작성자를 위한 확인 수단**: 설정만 만지는 사람은 코드를 디버깅할 수 없습니다. "규칙별 적중 수, 연결 실패 수, 예시 몇 건"을 돌려주는 드라이런 API나 화면이 사실상 필수입니다. 지금 플러그인이 남기는 요약 한 줄("상위 타입으로 계층을 정한 타입 N")을 규칙 단위로 넓히면 됩니다.

## 5. 정리

| | 효과 | 작업량 | 위험 |
|---|---|---|---|
| 1단계 (목록 이전) | 사내 애노테이션·DAO 래퍼·명명 규칙을 설정으로 대응 | 작음 | 거의 없음 |
| 2단계 (규칙 모양 일반화 + 프로파일 + 규칙 사본 + 드라이런) | 새 환경 대부분을 설정만으로 대응. Java → Java 디스패치처럼 지금은 안 되는 연결도 생김 | 중간 | 기존 플러그인 결과와 같은지 표본 4종(`struts-app`, `websquare-app`, HI-Japan, `anybiz_prd`)으로 비교해야 함 |
| 코드가 계속 필요한 것 | 새 파일 형식 파서, Java 언어 규칙 | — | — |

1단계를 먼저 하고, 다음 새 환경이 실제로 들어올 때 그 요구에 맞춰 2단계 규칙 모양을 하나씩 늘려 가는 순서를 추천합니다. 진행할까요?

---

## 질문

지금은 가능성 타진중. 생각해본 후 결정할께.

---

## 답변

알겠습니다. 결정하시면 그때 이어서 진행하겠습니다. 다시 보실 때는 1단계(코드에 박힌 목록을 설정으로 옮기기)만 따로 떼어 해도 된다는 점을 기억해 두세요. 범위가 작고 기존 동작도 바뀌지 않습니다.
