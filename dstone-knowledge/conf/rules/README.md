# 의미 분석 규칙 (conf/rules)

프레임워크마다 다른 관례를 적는 곳이다. 어떤 애노테이션이 서비스인지, 어떤 호출의 몇 번째 인자가 SQL 이름인지 같은 것이다.

**찾고 싶은 코드를 생긴 그대로 적고, 그 옆에 뜻을 적는다.**

```yaml
layer:
  SERVICE: "@Service"                               # @Service 가 붙은 클래스는 서비스
  REPOSITORY: extends com.acme.fw.BaseDao           # BaseDao 를 상속하면 DAO
sql:
  - com.acme.fw.BaseDao#executeList(_, SQL)         # executeList 의 둘째 인자가 SQL 이름
entry:
  SCHEDULED: "@Scheduled(cron = KEY)"               # @Scheduled 메소드는 입구. cron 값이 주소 자리
screen:
  - { call: doRequestAjax(ID), to: "perform{ID}" }  # 화면의 doRequestAjax("A01") → performA01 메소드
```

자세한 설명과 예제는 `docs/11.dstone-knowledge.md` 3장에 있다.

## 폴더 = 우선순위

```text
conf/rules/
├─ projects/{프로젝트ID}/   ① 그 프로젝트만의 관례. 가장 먼저 본다
├─ common/                  ② 모든 프로젝트의 관례(Java, 서블릿, Spring, JPA, MyBatis)
└─ guess/                   ③ 짐작. 다른 규칙이 못 정한 것만 채운다(@Component, 이름 끝말)
```

- 한 클래스에 규칙 여럿이 맞으면 **① → ② → ③** 순서로 먼저 맞은 것이 이긴다. 같은 폴더 안에서는 애노테이션 → 상속 → 이름 순서다.
- `common/`, `guess/`는 모든 프로젝트에 쓴다. 해당하는 코드가 없으면 아무 일도 하지 않는다.
- `projects/hi-japan/`은 프로젝트 `HI-Japan`에만 쓴다(대소문자는 가리지 않는다). 같은 프레임워크를 쓰는 다른 프로젝트는 등록할 때 `"ruleSets": "hi-japan"`으로 빌려 쓴다.
- 파일 이름은 마음대로 지어도 된다. 규칙 이름은 경로다(`common/spring.yml` → `common/spring`).
- 서버가 뜰 때 모두 검사한다. 틀리면 **파일과 줄 번호, 올바른 예**를 알려 주며 서버가 뜨지 않는다.
- 분석할 때마다 다시 읽는다. 고친 뒤에는 `rerunFrom: "SEMANTIC"`으로 다시 돌리면 된다.

## 절마다 적는 법

| 절 | 알고 싶은 것 | 적는 모양 |
|---|---|---|
| `layer:` | 이 클래스는 어느 계층인가 | `계층: "@애노테이션"` / `extends 상위.타입` / `name *끝말` |
| `entry:` | 이 메소드는 입구인가 | `종류: "@애노테이션(속성 = KEY)"` / `상위.타입#메소드()` / `{ file, xml }` / `HTTP: { class, GET, ... }` |
| `inject:` `proxy:` | 주입받는 필드, 프록시가 끼는 메소드 | `"@Autowired"` |
| `sql:` | 이 호출은 어떤 SQL을 실행하나 | `_.메소드(SQL)` / `타입#메소드(_, SQL)` / `new 타입(SQL)` |
| `view:` | 이 호출은 어떤 JSP를 여나 | `{ call: new ModelAndView(VIEW), file: "{VIEW}.jsp" }` |
| `screen:` | 화면이 어느 서버 메소드를 부르나, 어느 화면을 여나 | `{ call: 함수(ID), to: "perform{ID}" }` / `{ field: 필드, to: ... }` / `{ open: 함수(ID), to: "{ID}.xml" }` / `{ menu: { name: 키, path: 키 } }` |
| `jpa:` | JPA 엔티티와 쿼리 | `entity`, `table`, `naming`, `query`, `namedQuery`, `repository`, `jpql` |

### 패턴 글자

| 적는 법 | 뜻 |
|---|---|
| `"@Service"` | 애노테이션(단순 이름). `@`로 시작하므로 **따옴표로 감싼다** |
| `"@GetMapping(value\|path = URL)"` | 애노테이션과 값을 꺼낼 속성. 여럿이면 `\|`로 잇는다. `(KEY)`처럼 자리만 적으면 `value` 속성 |
| `extends a.b.Base` | 이 타입을 바로 상속(구현)한 클래스. 전체 이름으로 적는다 |
| `name *Dao` | 클래스 단순 이름. `*`는 아무 글자, `?`는 한 글자 |
| `_.selectList(SQL)` | 아무 객체의 selectList 호출 |
| `a.b.BaseDao#execute(_, SQL)` | BaseDao(하위 타입 포함)의 execute 호출. `_`는 보지 않는 인자 |
| `new QueryProperty(SQL)` | 생성자 호출 |
| `java.lang.Runnable#run()` | entry 에서는 "Runnable 을 구현한 클래스의 run()". `()`는 인자 0개, `(...)`는 인자 수를 보지 않음, `perform*`처럼 이름 글롭도 된다 |
| `URL` `KEY` `SQL` `VIEW` `ID` `CLASS` `METHOD` `VERB` | 값을 꺼낼 자리. 절마다 쓸 수 있는 자리가 정해져 있다 |

`[a, b]` 안에 쉼표가 든 호출(`#execute(_, SQL)`)을 적으면 YAML 이 쉼표에서 나눈다. 그런 것은 한 줄에 하나씩 `-`로 적는다.

### 화면 ID 다듬기 (`id:`)

거래 ID가 메소드 이름과 글자 그대로 같지 않을 때 쓴다. `match` → `case` → `map` 순서로 다듬는다.

```yaml
screen:
  # callService("SVC_ORDER_LIST") → svc_order_list 메소드
  - call: callService(ID)
    to: "{ID}"
    id: { match: '^SVC_(.*)$', replace: 'svc_$1', case: LOWER }
  # 거래 ID → 클래스.메소드 대응표. 이 규칙 파일이 있는 폴더에서 찾는다
  - call: callTx(ID)
    to: "{ID}"
    id: { map: tx-map.csv }
```

## 결과에 남는 근거

규칙으로 만든 결과에는 그 규칙의 자리가 남는다.

- 진입점, 관계(INJECTS, EXECUTES_SQL, RENDERS, REQUESTS, INCLUDES), JPA statement, 메뉴 화면명: `properties_json.ruleAt` = `common/spring.yml:17`
- 계층: `analysis_symbol.properties_json`의 `layerRuleAt`(파일:줄)과 `layerBy`(적힌 모양, 예: `@Service`, `struts-config`, `JSP`)

"이 클래스가 왜 CONTROLLER 인가?"를 물으면 이 값을 보면 된다.

## 새 관례를 규칙으로 만드는 순서

1. **후보 보기**: `GET /api/revisions/{id}/rules/candidates` — 규칙이 아직 모르는 애노테이션, 상위 타입, SQL 이름을 받는 호출과 붙여 넣을 규칙 조각(`suggestion`)이 나온다. 계층과 진입점 종류는 짐작이니 꼭 고친다.
2. **시험 적용**: `POST /api/revisions/{id}/rules/trial`, 본문 `{"path": "projects/hen/backend.yml", "rules": "layer: ..."}` — SEMANTIC 을 한 트랜잭션 안에서 돌려 전후 차이를 알려 준 뒤 되돌린다. 본문 없이 부르면 지금 파일 그대로 돌린다.
3. **저장**: `conf/rules/projects/{프로젝트ID}/` 아래에 파일로 저장한다.
4. **반영**: `rerunFrom: "SEMANTIC"`으로 다시 분석한다. `GET /api/revisions/{id}/rules`가 분석할 때 쓴 규칙과 지금 파일을 견줘 준다.
