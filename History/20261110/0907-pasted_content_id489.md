# pasted_content_id489

> 생성일시: 2026-11-10 09:07

## 질문

<pasted_content id="4897">
dstone-knowledge 에서 
/conf/rules 이 가장 문제야. 몇 번 재작성 시도를 해봐도 뭔가 직관적이지 못해.
이부분에 대해서 획기적인 전환이 필요할 수도 있을 것 같아. 
누가 봐도 이해하기 쉽고 직관적인 재설계를 해주되 먼저 계획을 알려줘.
</pasted_content id="4897">

---

## 답변

규칙 파일과 이를 읽는 코드 구조를 살펴보는 중입니다. 조금만 더 확인하고 계획을 정리하겠습니다.

---

## 답변

# conf/rules 재설계 계획

## 1. 지금 형식이 어려운 이유

규칙 8개 파일과 README, `docs/11-2`(1,105줄), `RuleSetParser`(681줄)를 읽어 봤습니다. 문서를 다시 써도 나아지지 않은 건 문장 탓이 아니라 형식 자체 때문입니다.

| 문제 | 실제 모습 |
|---|---|
| **① 분석기 내부 구조대로 나뉘어 있음** | 사람은 "`BaseDao`는 뭐지?"를 묻는데, 답을 적으려면 `layers.superTypes`, `callArguments.receiverTypes`, `endpoints.superTypes` 세 곳에 흩어 적어야 합니다. 같은 사실을 여러 번 적게 됩니다. |
| **② 키 이름이 분석기 용어** | `callArguments`, `target: SQL`, `receiverTypes`, `pathAttributes`, `methodAttribute`/`httpMethod`, `idPattern`/`idReplace`/`idCase`/`idMap`… 키 이름만 봐서는 실제 코드 어디에 맞는지 알기 어렵습니다. |
| **③ 우선순위를 예측하기 어려움** | `order` 숫자는 같은 키 안에서만 효과가 있고, HIGH/MEDIUM/LOW는 키 종류가 정합니다. 그래서 `fallback`(900)의 `@Component`가 프로젝트 규칙의 `BaseAction`을 이깁니다. 문서 4.2절에서 따로 설명해야 할 만큼 직관과 반대입니다. |
| **④ 적용 범위를 정하는 장치가 셋** | `autoApply` 기본값이 폴더마다 다르고, 폴더 이름과 프로젝트 ID를 맞추는 방식, API의 `ruleSets` 빌려 쓰기까지 섞여 있습니다. |
| **⑤ 메타 필드가 많음** | `name`은 경로와 같아야 하고, `order`와 `autoApply`도 있습니다. 대부분 적을 필요가 없거나 실수를 부르는 값입니다. |

## 2. 핵심 아이디어: "코드 모양 그대로 적는다"

Semgrep처럼 **찾고 싶은 코드를 생긴 그대로 적고**, 그 옆에 뜻을 적습니다. 파일 구성도 분석기 내부 구조가 아니라 **사람이 묻는 질문**(계층, 입구, SQL, 화면…)을 따릅니다.

**지금 (`hi-japan/backend.yml`)**
```yaml
name: hi-japan/backend
order: 500
layers:
  superTypes:
    - { layer: CONTROLLER, types: [jef.application.services.AbstractMainService] }
    - { layer: REPOSITORY, types: [jef.application.dao.jdbc.JdbcDAO] }
callArguments:
  - target: SQL
    constructors: [QueryProperty]
```

**바꾼 뒤**
```yaml
layer:                       # 이런 클래스는 이 계층이다
  CONTROLLER: [extends jef.application.services.AbstractMainService]
  REPOSITORY: [extends jef.application.dao.jdbc.JdbcDAO]

sql:                         # SQL 자리의 글자가 SQL 이름이다
  - new QueryProperty(SQL)
```

**B사 예제 (문서 1.2절)**
```yaml
layer:
  CONTROLLER: [extends com.acme.fw.BaseAction]
  REPOSITORY: [extends com.acme.fw.BaseDao]
sql:
  - com.acme.fw.BaseDao#executeList(_, SQL)      # 두 번째 인자. receiverTypes·argIndex가 따로 필요 없음
entry:
  TRANSACTION:
    - file: config/service-.*\.xml
      xml: <service id="KEY" class="CLASS" method="METHOD"/>   # XML도 생긴 그대로
```

**Spring (`spring.yml`) 일부**
```yaml
entry:
  HTTP:
    class:  "@RequestMapping(URL)"          # 클래스 주소
    GET:    "@GetMapping(URL)"
    POST:   "@PostMapping(URL)"
    ANY:    "@RequestMapping(URL, method=HTTP_METHOD)"
  SCHEDULED: ["@Scheduled(cron|fixedRate|fixedDelay = KEY)"]
  THREAD:    [java.lang.Runnable#run(), java.util.concurrent.Callable#call()]
view:
  - code: [new ModelAndView(VIEW), _.setViewName(VIEW)]
    file: "{VIEW}.jsp"
    skip: "^(redirect|forward):"
inject: ["@Autowired", "@Inject", "@Resource"]
proxy:  ["@Transactional", "@Async", "@Cacheable"]
```

**화면 (`hi-japan/frontend.yml`)**
```yaml
screen:
  - code: doRequestAjax(ID)                      # 화면이 거래 ID로 서버를 부른다
    calls: perform{ID}
  - code: fnOpenPage(_, ID)                      # 두 번째 인자. argIndex 대신 자리로 표현
    opens: "{ID}.xml"
  - code: <input name="tranId" value="ID">
    calls: perform{ID}
  - code: callService(ID)
    id: { match: '^SVC_(.*)$', replace: 'svc_$1', case: LOWER }   # ID 다듬기를 한곳에 모음
    calls: "{ID}"
```

### 패턴 문법 (전체가 이 정도로 작습니다)

| 적는 법 | 뜻 |
|---|---|
| `@Name`, `@Name(URL)`, `@Name(a\|b = KEY)` | 애노테이션. 괄호 안은 값을 읽을 속성. `(URL)`은 `value`/`path` |
| `extends 전체.타입이름` | 이 타입을 바로 상속하거나 구현한 클래스 |
| `타입#메소드(…)` / `_.메소드(…)` / `new 타입(…)` | 그 타입(하위 타입 포함)의 메소드 / 아무 객체의 메소드 / 생성자 |
| `_`, `...`, `()` | 아무 값 / 나머지 인자 / 인자 없음(`paramCount: 0` 대체) |
| `SQL`, `VIEW`, `URL`, `KEY`, `ID`, `CLASS`, `METHOD` | 값을 꺼내는 자리(대문자 이름) |
| `name *Dao` | 클래스 이름(정규식 대신 글롭. 정규식이 필요하면 `re:`) |

## 3. 우선순위와 적용 범위: 폴더 하나로 정함

`order`, `autoApply`, `name` 필드는 없앱니다.

```text
conf/rules/
├─ projects/HI-Japan/   ① 프로젝트 규칙: 그 프로젝트에만 적용 (빌려 쓰기는 지금처럼 ruleSets)
├─ common/              ② 프레임워크 규칙: 항상 적용 (java, servlet, spring, jpa, mybatis)
└─ guess/               ③ 짐작 규칙: 항상 적용, 가장 약함 (@Component, 이름 끝말)
```

규칙은 한 줄로 끝납니다. **"내 프로젝트 규칙 > 프레임워크 규칙 > 짐작"**이고, 같은 단계 안에서는 애노테이션 > 상속 > 이름 순입니다. HIGH/MEDIUM/LOW 신뢰도도 이 단계에서 나옵니다.

## 4. 진행 단계

백엔드 위험을 줄이려고 **새 형식을 지금의 `RuleSet` 모델로 변환**합니다. 그러면 Plugin 20여 개는 거의 손대지 않아도 됩니다.

1. **형식 확정**: 이 계획과 8개 파일의 변환안을 먼저 보여 드리고 승인을 받습니다.
2. **새 파서**: 패턴 문법 → `RuleSet`. 부팅 검증 오류는 `파일:줄`, 틀린 패턴, 올바른 예를 함께 보여 줍니다.
3. **우선순위 3단계**: 계층 Plugin들이 `order` 대신 폴더 단계를 쓰게 바꿉니다. `ProjectService`의 묶음 고르기도 단순하게 정리합니다.
4. **파일 8개 변환과 결과 비교**: HI-Japan, websquare-app, hen 등을 변환 전후로 SEMANTIC만 다시 돌려 `analysis_*` 결과를 비교합니다. 우선순위를 바꿔서 생긴 차이만 목록으로 보고합니다. 시험 인스턴스는 다른 포트에서 띄웁니다.
5. **API 맞춤**: 후보 API(`suggestion`), trial API, 리비전 규칙 기록, dstone-boot `project.js`의 `ruleSets` 입력을 새 형식에 맞춥니다.
6. **문서 다시 쓰기**: README와 `docs/11-2`를 "이런 코드면 이렇게 적는다" 예제 표 중심으로 짧게 다시 씁니다.

## 5. 먼저 정해 주셔야 할 것

1. **패턴 문자열 방식**(2절)으로 가도 될까요? 키 이름만 쉽게 바꾸는 보수적인 방법도 있지만, 그 방법으로는 ①과 ③이 해결되지 않습니다.
2. **우선순위 변경으로 결과가 달라지는 걸 받아들일지**: 예를 들어 `@Service`가 붙은 `AbstractMainService` 하위 클래스는 지금 SERVICE지만, 바꾼 뒤에는 프로젝트 규칙이 이겨서 CONTROLLER가 됩니다. 4단계에서 실제로 몇 건이 달라지는지 보여 드리겠습니다.
3. **옛 형식 읽기는 없애도 될까요?** 파일이 8개뿐이라 모두 변환하고, 형식을 하나만 남기는 쪽을 권합니다.
4. 규칙 파일을 바꾸면 해시가 달라지므로 이전 리비전은 모두 규칙이 `CHANGED`로 표시됩니다.

말씀해 주시면 1단계로 8개 파일의 변환안 전체부터 만들어 보여 드리겠습니다.

---

## 질문

백엔드도 마찬가지로 쉽게 바꿀 수 없나?

---

## 답변

# 답: 네, 백엔드도 쉽게 바꿀 수 있습니다

`pass08/semantic` 아래를 살펴보니 백엔드에도 규칙 파일과 같은 문제가 그대로 있습니다. 앞서 낸 계획처럼 새 형식을 지금의 `RuleSet`으로 바꿔 넣기만 하면, 파일은 쉬워져도 코드는 그대로 어렵게 남습니다. 백엔드까지 함께 바꾸는 쪽을 권합니다.

## 1. 백엔드가 어려운 이유

| 문제 | 실제 모습 |
|---|---|
| **① Plugin 이름과 하는 일이 다름** | `SpringPlugin`이 모든 프레임워크의 애노테이션 계층을 정합니다. `MyBatisPlugin`은 JEF의 `new QueryProperty(...)`까지 포함해 모든 SQL 호출 인자를 맡습니다. `PlainJavaPlugin`은 상위 타입 진입점 전부(Runnable, JEF `perform*`)를 처리합니다. 이름만 보고는 어디를 고쳐야 할지 알 수 없습니다. |
| **② 한 질문의 답이 여러 곳에 흩어짐** | 계층 하나를 정하는 데 `SpringPlugin`(100), `ConfiguredLayerPlugin`(800), `NamingLayerPlugin`(900) 세 곳이 나뉘어 관여합니다. 우선순위는 Plugin 순서 숫자와 SQL 10곳의 `AND layer IS NULL`이 암묵적으로 정합니다. |
| **③ 실행 순서가 숫자에 숨어 있음** | Plugin 17개가 100~980 사이 숫자로 순서를 정합니다. 어떤 Plugin이 어떤 결과에 기대는지는 코드에 드러나지 않습니다. |
| **④ 규칙 모델이 평평한 목록 22개** | `RuleSet`은 서로 관계없는 목록 22개를 늘어놓은 구조입니다. 이를 만드는 `RuleSetParser`가 681줄입니다. |
| **⑤ 결과의 근거를 추적할 수 없음** | "이 클래스가 왜 CONTROLLER인가?"를 물으면 어느 규칙 파일의 몇 번째 줄 때문인지 답할 수 없습니다. |

## 2. 바꾼 뒤의 모습: 규칙 파일과 같은 질문으로 나눈다

규칙 파일의 절(`layer`, `entry`, `sql`, `view`, `screen`…)과 Java 패키지를 1:1로 맞춥니다. **파일의 `sql:`을 고쳤으면 `semantic/sql/`을 보면 됩니다.**

```text
analysis/pass08/semantic/
├─ rule/            규칙 파일 읽기
│   ├─ RuleFiles         폴더 → 프로젝트·공통·짐작 3단계
│   ├─ PatternParser     "extends X", "@A(URL)", "T#m(_, SQL)" → Pattern 객체
│   └─ Rule              패턴 + 뜻 + 출처(파일:줄) + 단계
├─ layer/LayerResolver      계층       ← 파일의 layer:
├─ entry/EntryFinder        입구       ← entry:
├─ sql/SqlLinker            SQL 잇기   ← sql:, jpa:
├─ view/ViewLinker          JSP 잇기   ← view:
├─ screen/ScreenLinker      화면       ← screen:
├─ wiring/WiringMarker      주입·프록시 ← inject:, proxy:
├─ builtin/  규칙이 필요 없는 표준 형식: web.xml, struts, JSP, main(), 매퍼 인터페이스, Java 문자열 SQL
└─ dictionary/  이름 사전 (지금과 같음)
```

### 핵심 변경 네 가지

1. **패턴이 직접 일한다.** 패턴 종류는 여섯 가지입니다(애노테이션, 상속, 메소드 호출, 생성자, 이름, XML·화면 요소). 패턴마다 "`analysis_*` 테이블에서 무엇을 찾는가"를 직접 압니다. 처리기는 패턴을 받아 결과를 쓰기만 하므로, 프레임워크별 분기가 사라집니다.
2. **계층은 한 곳에서 정한다.** `LayerResolver`가 클래스마다 맞는 규칙을 모두 모은 뒤 **프로젝트 > 공통 > 짐작**, 같은 단계 안에서는 **애노테이션 > 상속 > 이름** 순으로 하나를 고릅니다. `layer IS NULL`로 순서를 흉내 내던 SQL 10곳은 없어집니다.
3. **실행 순서는 목록 하나에 적는다.** 숫자 대신 `SemanticPass`에 `계층 → 입구 → SQL → JSP → 화면 → 주입·프록시 → 사전` 순서와 그 이유를 함께 적습니다(예: 주소 물려받기는 CONTROLLER 계층이 먼저 정해져야 합니다).
4. **결과마다 근거를 남긴다.** 계층, 진입점, 관계 행의 `properties_json`에 `"rule": "projects/HI-Japan/backend.yml:4"`처럼 근거를 기록합니다. "왜 이렇게 나왔나"를 화면과 API로 바로 답할 수 있게 됩니다.

## 3. 진행 단계

1. **안전망 먼저**: 지금 코드로 HI-Japan, HI-Japan-batch, websquare-app, hen을 분석해 SEMANTIC 결과를 기준 스냅샷으로 저장합니다. 대상은 `analysis_endpoint`·`relation`·`mapper`·`resource`와 `symbol.layer`입니다. 같은 기준으로 다시 비교할 수 있게 비교 SQL·스크립트를 만들어 둡니다.
2. **규칙 형식 확정**: 이전 계획의 1단계입니다. 8개 파일의 변환안을 보여 드리고 승인을 받습니다.
3. **`rule/` 새로 작성**: `PatternParser`와 `Rule` 모델, 단위 테스트를 만듭니다. `RuleSetParserTest`를 대체합니다.
4. **처리기를 하나씩 옮기기**: `sql` → `view` → `entry` → `wiring` → `screen` → `layer` 순서로 옮깁니다. 하나를 옮길 때마다 1단계 스냅샷과 비교해 **차이가 없음**을 확인합니다. `layer`는 우선순위 변경으로 생기는 차이만 목록으로 보고합니다.
5. **기존 코드 삭제**: `RuleSet`, `RuleSetParser`, `SemanticRules`, Plugin 이름 정리, `layer IS NULL` 순서에 기대던 코드를 지웁니다.
6. **API·화면 맞춤**: 후보 API의 `suggestion`, trial, 리비전 규칙 기록, `project.js`를 새 형식에 맞추고 "근거" 표시를 추가합니다.
7. **문서 정리**: `docs/11-2`는 1·2부(규칙 쓰기)와 3부(백엔드: 질문별 패키지 표 하나)로 다시 씁니다. `knowledge-core.md`의 SEMANTIC 설명도 고칩니다.

규모는 SEMANTIC 패키지 약 1.1만 줄 가운데 규칙과 이어진 약 3~4천 줄입니다. 4단계를 처리기 단위로 나누고 매번 스냅샷과 비교하므로, 중간에 멈춰도 동작하는 상태가 유지됩니다.

## 4. 정해 주셔야 할 것

1. **범위**: 백엔드까지 한 번에 바꿀까요, 아니면 파일 형식(앞선 계획) → 백엔드 순서로 두 번에 나눌까요? 저는 **한 번에**를 권합니다. 나눠서 하면 중간 단계를 위한 변환 코드를 만들었다가 버려야 합니다.
2. **결과 근거(`rule` 출처) 기록**을 넣을까요? `properties_json`만 쓰므로 DB 스키마는 바뀌지 않습니다.
3. **처리기 이름**: `LayerResolver`, `EntryFinder`, `SqlLinker`… 처럼 질문 이름으로 짓는 방식이 괜찮을까요?

정해 주시면 1단계(기준 스냅샷)부터 시작하겠습니다. 이 작업은 코드를 바꾸지 않고 다른 포트의 시험 인스턴스에서만 진행합니다.
