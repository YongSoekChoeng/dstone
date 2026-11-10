# pasted_content_id489

> 생성일시: 2026-11-10 10:08

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

---

## 질문

<pasted_content id="4897">
응 모두 네 계획대로 진행해줘. 그리고 올려줘.
  1. 범위: 백엔드까지 한 번에 바꿀까요, 아니면 파일 형식(앞선 계획) → 백엔드 순서로 두 번에 나눌까요? 저는 한 번에를 권합니다.
    =>> 한번에 진행.
  2. 결과 근거(rule 출처) 기록을 넣을까요? properties_json만 쓰므로 DB 스키마는 바뀌지 않습니다.
    =>> 넣어줘.
  3. 처리기 이름: LayerResolver, EntryFinder, SqlLinker… 처럼 질문 이름으로 짓는 방식이 괜찮을까요?
    =>> ok.
</pasted_content id="4897">

---

## 답변

먼저 현재 환경(실행 중인 인스턴스, DB, 분석된 프로젝트)을 확인하겠습니다.

---

## 답변

계획대로 진행합니다. 백엔드 코드를 먼저 모두 읽겠습니다. 규칙 패키지부터 봅니다.

---

## 답변

SEMANTIC 백엔드의 SQL과 Plugin 코드를 읽고 있습니다. 구조를 다 파악한 다음 새 형식으로 옮겨 쓰겠습니다.

---

## 답변

코드를 계속 읽는 중입니다. 지금까지 SEMANTIC 단계의 Plugin 10개 정도를 봤고, 이제 화면 규칙(RichClientPlugin)과 JPA 쪽을 확인하겠습니다.

---

## 답변

규칙 API 쪽(RuleService)까지 읽었습니다. 컨트롤러와 진단 서비스를 확인한 다음 새 규칙 모델을 작성하기 시작하겠습니다.

---

## 답변

기준 스냅샷을 먼저 뜨겠습니다. 어떤 프로젝트와 리비전이 있는지, 지금 돌고 있는 분석이 있는지 DB에서 확인합니다.

---

## 답변

빌드하는 동안 전후 비교용 스냅샷 SQL을 만들겠습니다.

---

## 답변

변경 전 jar가 4181 포트에서 떴습니다. 리비전 16개의 SEMANTIC을 다시 돌려 기준 결과를 만들겠습니다. 몇 분 걸립니다.

---

## 답변

기준 결과를 만드는 동안 새 코드를 작성하겠습니다. 테스트 코드에서 규칙 모델을 쓰는 곳도 확인합니다.

---

## 답변

모델부터 작성합니다. 먼저 `rule` 패키지(우선순위 단계, 규칙 하나, 패턴 해석기)입니다.

---

## 답변

이제 화면 규칙 클래스(ScreenScript.CallRule, ScreenMenu.Rule)가 근거 자리(at)를 들고 다니도록 고칩니다.

---

## 답변

이제 규칙 파일 하나를 담는 `RuleFile`과 해석기 `RuleFileParser`를 씁니다.

---

## 답변

기준 실행이 절반쯤 진행됐습니다(testApp은 Windows 경로라 건너뜁니다). 그 사이 규칙 선택 로직(`RuleFiles`, `ProjectRules`)을 씁니다.

---

## 답변

규칙 파일을 새 형식으로 옮깁니다. 옛 파일 8개를 `common/`, `guess/`, `projects/hi-japan/`으로 나눠 다시 씁니다.

---

## 답변

이제 Java 쪽입니다. 옛 규칙 클래스 4개를 지우고, Plugin 인터페이스를 순서 번호 없는 `SemanticStep`으로 바꿉니다.

---

## 답변

`StrutsEntries`의 계층 기록과 `SemanticDao`를 새 규칙 모델에 맞게 고칩니다. 먼저 SemanticDao 전체 메소드를 확인합니다.

---

## 답변

이어서 SemanticDao.xml의 SQL을 고칩니다.

---

## 답변

계층 처리기 `LayerResolver`를 씁니다. 우선순위 순서가 이 클래스 한 곳에 보이도록 했습니다.

---

## 답변

이제 입구 처리기 `EntryFinder`입니다. 기존 SpringPlugin(HTTP·애노테이션), XmlEndpointPlugin, PlainJavaPlugin(main·JSP·상위 타입)을 한곳에 모읍니다.

---

## 답변

SQL 쪽입니다. MapperLinker(옛 MyBatisPlugin)에서 statement 이름표를 `StatementIndex`로 떼어 내고, 규칙으로 잇는 부분은 새 `SqlLinker`로 옮깁니다.

---

## 답변

JPA·화면·JSP 처리기들을 새 `ProjectRules`에 맞춥니다.

---

## 답변

새 처리기(LayerResolver, EntryFinder, WiringMarker, SqlLinker)를 다 썼고, 지금은 남은 클래스의 이름·주석과 화면 메뉴 처리기를 정리하고 있습니다.

---

## 답변

Now the JSP linker: remove the layer and RENDERS parts (they moved to LayerResolver and the new ViewLinker).

---

## 답변

이제 `SemanticPass`에 실행 순서를 목록 하나로 적습니다.

---

## 답변

서비스 계층(RuleService, ProjectService, DiagnosticService, RuleController)을 새 규칙 모델에 맞춥니다.

---

## 답변

서비스 계층 수정을 마무리하고 있습니다. 곧 컴파일해서 오류를 잡겠습니다.

---

## 답변

컴파일해 봅니다.

---

## 답변

컴파일이 통과했습니다. `order()`를 지운 자리에 남은 빈 줄을 정리하고 테스트 코드를 고칩니다.

---

## 답변

테스트도 YAML 규칙 글을 실제 해석기로 읽어 만들도록 고칩니다.

---

## 답변

단위 테스트 195개가 모두 통과했습니다. 그런데 기준 실행이 dstone-knowledge 리비전에서 오래 머물러 있어 상태를 확인합니다.

---

## 답변

스크립트가 `DONE_WITH_WARNING` 상태를 몰라서 기다리고만 있었습니다. 고쳐서 남은 리비전부터 이어 돌리겠습니다.

---

## 답변

기준 실행이 다시 돌고 있습니다. 그동안 옛 규칙 형식을 가리키는 문서·화면·설정 문구를 찾습니다.

---

## 답변

`pkill`이 새로 띄운 실행까지 같이 멈췄습니다. 다시 띄웁니다.

---

## 답변

주석 정리를 마쳤습니다. 기준 실행 진행 상황을 확인합니다.

---

## 답변

앞서 `pkill`이 스크립트 수정 전에 실행을 끊어서 수정이 반영되지 않았습니다. 이번엔 PID로 멈추고 스크립트를 제대로 고칩니다.

---

## 답변

HI-Japan 작업 하나가 아직 돌고 있습니다. 남은 6개 리비전을 이어서 시작합니다(같은 실행기에서 차례로 돕니다).

---

## 답변

그동안 새 코드의 jar를 별도 폴더에서 빌드합니다(실행 중인 4081 jar는 건드리지 않습니다).

---

## 답변

문서를 다시 쓰기 전에 저장소 규칙대로 한국어 작성 지침(humanizer)을 불러옵니다.

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


ARGUMENTS: conf/rules/README.md 와 docs/11-2 규칙 문서를 새로 쓸 때 적용할 한국어 작성 지침

---

## 답변

지침대로 쉼표와 번역투를 줄이고 예제를 앞세워 README를 새로 씁니다.

---

## 답변

새 jar가 빌드됐고 기준 실행은 6개 중 2개 끝났습니다. 그동안 `docs/11-2`를 다시 쓰려고 기존 문서의 따라 하기·API·오류 절을 확인합니다.

---

## 답변

기존 문서 내용을 파악했습니다. `docs/11-2`를 새 형식 기준으로 다시 씁니다.

---

## 답변

기준 실행이 모두 끝났습니다. 스냅샷을 뜨고, 변경 전 인스턴스를 내린 뒤 새 jar를 다른 포트로 띄웁니다.

---

## 답변

모니터 알림을 기다리는 동안 `docs/11.dstone-knowledge.md`의 규칙 관련 절을 확인합니다.

---

## 답변

New jar is up on 4281 and read all 8 rule files. Running SEMANTIC on the same 15 revisions with the new code.

---

## 답변

메인 문서 4.7절의 처리기 표와 4.7.1~4.7.5절을 새 구조로 바꿉니다. 자세한 예제는 `11-2`로 넘깁니다.

---

## 답변

메인 문서 다른 곳의 옛 Plugin 이름과 키도 바꿉니다.

---

## 답변

Now the remaining spots in the main doc (lines ~275–281, 543–549, 573, 626–631, 839, 848, 978, 1520s, 1560s, 1795).

---

## 답변

새 코드 실행은 15개 중 12개가 끝났습니다. 그동안 `.claude/docs`와 CLAUDE.md의 규칙 설명을 고칩니다.

---

## 답변

남은 3개를 기다리는 동안 전후 비교 쿼리를 준비합니다.

---

## 답변

실행 로그가 처리기 이름대로 잘 나옵니다. `ScreenLinker`의 로그 이름만 옛 이름(richClient)이라 `screen`으로 바꿉니다.

---

## 답변

Waiting for the remaining two revisions (anybiz_prd, jpa-app) to finish on the new code.

---

## 답변

새 코드로 15개 중 13개 리비전을 다시 돌렸습니다. 남은 두 개(anybiz_prd, jpa-app)가 끝나면 알림이 오고, 그때 변경 전후 결과를 비교하겠습니다.

---

## 질문

<task-notification>
<task-id>b4yiqaav3</task-id>
<tool-use-id>toolu_01Hh6PsWjBPM5UsdnVBRzaXC</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/63f87f4b-5153-4bba-9a4c-20bf13608f46/tasks/b4yiqaav3.output</output-file>
<status>completed</status>
<summary>Background command "새 코드로 15개 리비전 SEMANTIC 재실행" completed (exit code 0)</summary>
</task-notification>

---

## 답변

Only 2 rows differ out of 86,450. Let me see what they are.

---

## 답변

근거가 모든 결과에 남았습니다(진입점 3,213건, 관계 수천 건 모두). 문서의 예시를 실제 값으로 바꾸려고 HI-Japan 예를 하나 뽑습니다.

---

## 답변

Now updating the doc example with these real values and recording the verification in `docs/11` §10.2.

---

## 답변

문서 기록을 마쳤습니다. 커밋 전에 새 API(목록·후보·시험 적용)를 4281 인스턴스에서 한 번씩 확인합니다.

---

## 답변

Tests pass. HEAD moved since I started (f9787813), so I'm checking what changed before committing.

---

## 답변

Those are only automatic history commits. Committing my changes.

---

## 답변

규칙 파일과 백엔드를 계획대로 한 번에 바꿨고, `main`에 커밋해 푸시했습니다(`7073c0e9`).

**4081 인스턴스는 다시 빌드해서 띄워야 합니다.** 지금 떠 있는 jar는 옛 코드라 새 규칙 파일을 읽지 못하고, 다음 분석의 SEMANTIC 단계에서 실패합니다. 실행 중인 jar를 그 자리에서 다시 빌드하면 안 되니 `stopApp.sh` → `mvn package` → `startApp.sh` 순서로 해야 합니다. 말씀해 주시면 제가 하겠습니다.

**검증 결과:** 바꾸기 전 jar와 새 jar로 같은 리비전 15개의 SEMANTIC을 다시 돌렸습니다. SEMANTIC이 만드는 행 86,450개를 비교했는데 다른 행은 2개뿐이고, 우선순위를 바꿔서 의도한 차이입니다.
- `@Component class JwtAuthenticationFilter implements Filter`가 `COMPONENT`에서 `WEB_FILTER`(근거 `common/servlet.yml:11`)로 바뀌었습니다. 공통 규칙이 짐작 규칙보다 먼저이기 때문입니다.
- HI-Japan은 이제 프로젝트 규칙이 공통 규칙보다 먼저인데도 달라진 것이 없습니다.
- testApp은 소스 경로가 Windows 쪽이라 돌리지 못했습니다.
- 단위 테스트 195개는 모두 통과했습니다.

**규칙 파일 형식**
- 찾을 코드를 생긴 그대로 적습니다. 예: `SERVICE: "@Service"`, `extends a.b.BaseDao`, `name *Dao`, `a.b.Dao#execute(_, SQL)`, `new QueryProperty(SQL)`, `{ call: doRequestAjax(ID), to: "perform{ID}" }`
- 폴더가 우선순위입니다: `projects/{프로젝트}/` > `common/` > `guess/`. `order`, `autoApply`, `name`은 없앴습니다.
- 잘못 적으면 파일과 줄 번호, 올바른 예를 함께 알려 줍니다. 옛 형식의 키를 쓰면 새 형식으로 어떻게 적는지도 알려 줍니다.

**백엔드**
- 규칙 파일의 절마다 처리기가 하나씩 있습니다: `layer:` → `LayerResolver`, `entry:` → `EntryFinder`, `sql:` → `SqlLinker`처럼 이어집니다.
- 실행 순서는 `SemanticPass.steps()` 한 곳에 까닭과 함께 적혀 있습니다.
- 세 곳에 흩어져 있던 계층 결정을 `LayerResolver` 하나로 모았습니다.
- 규칙으로 만든 결과마다 근거가 남습니다. 진입점과 관계에는 `ruleAt`(파일:줄)이, 계층에는 `layerRuleAt`·`layerBy`가 남습니다. 예를 들어 `JMAT0105M`은 `projects/hi-japan/backend.yml:8`의 `extends …AbstractMainService` 때문에 CONTROLLER입니다.

**API·문서 변경**
- 시험 적용 API의 본문이 `{"path": "projects/x/backend.yml", "rules": "..."}`로 바뀌었습니다. 후보 API의 제안도 새 형식으로 나옵니다.
- 규칙 이름이 모두 바뀌어서(`spring` → `common/spring`) 예전 리비전은 규칙 비교 API에서 `REMOVED`/`ADDED`로 보입니다. SEMANTIC부터 다시 돌리면 맞춰집니다.
- `conf/rules/README.md`와 `docs/11-2`를 새로 썼고, `docs/11`, `docs/11-1`, `CLAUDE.md`, 메모 문서도 새 구조에 맞췄습니다.

---

## 질문

docs/11~.md 세 파일을 11.dstone-knowledge.md 하나에 모아줘. 겹치는 내용은 조리있게 합쳐주고.

---

## 답변

세 파일의 크기와 목차부터 확인합니다.

---

## 답변

세 문서 합계 4,600줄입니다. 겹치는 곳을 정하려고 11-1 전체와 본문의 해당 절을 읽습니다.

---

## 답변

Now the main doc's sections that overlap with 11-1 (0.6~0.8, 0.14, 4.1~4.6, 4.14, 5.6).

---

## 답변

The main doc already covers most of 11-1's stage details (4.2~4.6). Let me check the remaining overlap points: 4.14 증분, 5.6 관계, and proxy/화면 mentions.

---

## 답변

Merged. Now checking for duplicates and stale references inside the new 4.7 (the handler table appears both at the top of 4.7 and in 4.7.8).

---

## 답변

The 4.7.8 handler table duplicates the one at the top of 4.7, and the "형식이 바뀐 날" paragraph duplicates 10.2. Trimming those to pointers.

---

## 답변

Those remaining 4.7.6 references correctly point to the evidence section. Now the old-format example in 4.10 that I spotted earlier.

---

## 답변

Rules part is merged. Now folding in 11-1's unique content. First the request path (screen → proxy → API → Job) into 4.1, plus small additions to 4.2, 4.3 and 4.6.

---

## 답변

ID rules and LINK cycle handling are already covered (5.x and 4.9), so only the request path, the SCAN note, incremental detail, edge summary and code map need merging.

---

## 답변

Now rebuilding 4.14 (incremental analysis) from the existing summary plus 11-1's detailed chapter 6.

---

## 답변

Some sentence endings (합니다체 → 한다체) need manual fixing; the bulk replacement left a few broken forms. Fixing them one by one.

---

## 답변

Lines 55–56 are quoted server messages, so they keep their original wording. Checking the one odd "개이다".

---

## 답변

Now 5.6 (relations): adding 11-1's "증분에서 옮기나" column, node IDs, and the "다시 돌려도 결과가 같은 까닭" table.

---

## 답변

Last merge item: adding the screen/proxy and incremental-SQL locations from 11-1 §9 to the code map (0.14).

---

## 질문

[Usage limit reached; a short grace allowance remains, then this turn is cut off without warning. Judge what's left: if it's genuinely only a few steps, finish it properly, then stop and say so without extra polishing. Otherwise don't attempt it all: finish the piece in hand and at most two more like it, then stop. The allowance also pays for any subagents you have running: they don't get this message and will keep going, so it's up to you how to handle them. Either way, end by telling the user their usage limit was reached, what's done and what's left.]

---

## 답변

Fixing the last three links, deleting the two merged files, and committing.

---

## 답변

세 문서를 `docs/11.dstone-knowledge.md` 하나로 합쳐 `main`에 커밋하고 푸시했습니다(`c39c796f`). `11-1`과 `11-2` 파일은 지웠습니다.

**합친 결과**
- **규칙 파일(옛 11-2)**: 4.7절 안으로 들어갔습니다. 4.7.1~4.7.8절이 규칙 파일 설명이고, 지원 범위와 적용 진단은 4.7.9절로 밀렸습니다.
  - 4.7.1 규칙 파일이 왜 필요한가
  - 4.7.2 규칙 파일의 절 = 묻는 질문 = 처리기
  - 4.7.3 따라 해 보기
  - 4.7.4 절마다 예제
  - 4.7.5 우선순위와 적용 범위
  - 4.7.6 결과에 남는 근거와 규칙 기록
  - 4.7.7 새 관례를 규칙으로 만드는 순서와 자주 만나는 오류
  - 4.7.8 규칙 백엔드 들여다보기
- **분석 과정(옛 11-1)**: 본문 4.2~4.6절에 이미 같은 내용이 자세히 있어서, 거기에 없던 것만 옮겼습니다.
  - 4.1절: 분석 요청이 지나는 길(화면 → 중계 → 시작 API 10단계 → 끝난 뒤)
  - 4.2절: 리비전마다 `file_id`를 새로 매긴다는 설명
  - 4.14절: 증분 분석을 새로 썼습니다(CARRY·CARRY_RESOLVE의 SQL, jpa-app 105→106 예제, 조심할 점).
  - 5.6절: 관계 표에 "증분 분석에서" 열, 노드 ID, 다시 돌려도 관계가 두 번 생기지 않는 까닭
  - 0.14절: 코드 지도에 화면·중계와 증분 분석 위치
- **겹친 곳 정리**
  - 처리기 표는 4.7절 첫머리 한 곳에만 둡니다.
  - 규칙 형식 2판의 전후 비교 결과는 10.2절에만 둡니다.
  - 4.10절에 남아 있던 옛 규칙 형식 예시를 새 형식으로 고쳤습니다.
- **링크**: `CLAUDE.md`, `conf/rules/README.md`, `.claude/docs/knowledge-core.md`가 이제 `docs/11` 4.7절을 가리킵니다.

옛 11-1은 "~합니다" 체, 본문은 "~다" 체라 4.14절의 문장 끝을 맞췄습니다. 일괄 변환 뒤 어색해진 곳은 하나씩 고쳤지만, 4.14절의 문장 다듬기는 한 번 더 손볼 여지가 있습니다.

사용량 한도에 도달했습니다. 요청하신 통합은 모두 끝났고, 남은 것은 그 문장 다듬기 정도입니다.
