# dstoneknowledge_에서_S

> 생성일시: 2026-11-11 10:42

## 질문

dstone-knowledge 에서 
SCAN 단계에서는 jsp 파일은 건너뛰나?

---

## 답변

아니요, SCAN 단계는 JSP를 건너뛰지 않고 스캔 대상에 넣습니다.

**JSP로 분류되는 확장자** (`pass01/scan/FileClassifier.java:37`)
- `.jsp`, `.jspf`, `.jspx`, `.tag`는 `language = "JSP"`, `fileType = "JSP"`로 분류됩니다.

**SCAN이 JSP 파일에 하는 일** (`ScanPass.java:178` `scanFile`)
- 파일을 읽어 체크섬, 인코딩, 줄 수를 구합니다.
- `analysis_file`에 `parseStatus = "PENDING"`으로 한 행을 씁니다.
- 내용은 파싱하지 않습니다. JSP 내용은 뒤 단계에서 읽습니다.

**JSP라도 SCAN에서 빠지거나 분석에서 제외되는 경우는 두 가지입니다.**
1. **제외 폴더 아래에 있을 때**: `dstone.knowledge.scan.exclude-dirs`에 지정된 폴더(`target`, `build`, `node_modules` 등) 안의 파일은 아예 훑지 않습니다.
2. **5MB를 넘을 때**: `max-file-bytes`(기본 5MB)보다 크면 행은 남지만 `parseStatus = "SKIPPED"`가 되어 분석에서 빠집니다.

참고로 `conf/application.yml:93`의 `exclude-dirs`에 `/test/`가 들어 있는데, 이 값은 동작하지 않습니다. `ScanPass.preVisitDirectory`는 폴더 이름 하나(`dir.getFileName()`)만 비교하므로 슬래시가 들어간 값과는 일치할 수 없습니다. 그래서 지금은 `test` 폴더도 스캔됩니다. 원래 `test` 폴더를 빼려던 설정이라면 고쳐야 합니다. 원하시면 수정하겠습니다.

---

## 질문

DeclarePass 에서는 java, jsp 만 처리한다고 했는데 해당 파일타입을 필터링하는 부분은 어디에 있나?

---

## 답변

Now trace into FilePassRunner and the SQL.

---

## 답변

필터링은 SQL에서 합니다. Java 코드에서 확장자를 거르는 부분은 없고, 언어 값을 넘겨 받은 `INSERT` 문의 `WHERE` 절이 대상을 고릅니다.

**1. `DeclarePass`가 언어를 지정해 두 번 실행합니다** (`src/main/java/net/dstone/knowledge/analysis/pass03/declare/DeclarePass.java:93`)
```java
filePassRunner.run(context, NAME, "JAVA", handler);
filePassRunner.run(context, NAME, "JSP", handler);
```

**2. `FilePassRunner.run()`이 그 언어 값을 `seedFilePass`에 넘깁니다** (`src/main/java/net/dstone/knowledge/analysis/job/FilePassRunner.java:70`)
```java
filePassDao.seedFilePass(revisionId, pass, language);
```

**3. 실제 필터링은 이 `INSERT`의 `WHERE`가 합니다** (`src/main/resources/sqlmap/analysis/FilePassDao.xml:9`)
```sql
INSERT INTO analysis_file_pass (file_id, pass, revision_id, status)
SELECT file_id, #{pass}, revision_id
     , CASE WHEN parse_status = 'SKIPPED' THEN 'SKIPPED' ELSE 'PENDING' END
  FROM analysis_file
 WHERE revision_id = #{revisionId}
   AND language = #{language}     -- 여기서 걸러짐
ON CONFLICT (file_id, pass) DO NOTHING
```
`language`가 `JAVA` 또는 `JSP`인 파일만 `analysis_file_pass`에 이 단계의 작업 행으로 들어갑니다. 이 값은 SCAN 단계에서 `FileClassifier.languageOf()`가 정해 둔 것입니다.

**4. 처리할 파일은 이 작업 행에서 꺼냅니다** (`selectPendingFiles`, 같은 XML 28행)
`analysis_file_pass`에서 `pass = 'DECLARE'`이고 `status = 'PENDING'`인 행만 읽습니다. 3번에서 행이 만들어지지 않은 XML, YAML 같은 파일은 이 단계에서 아예 보이지 않습니다.

`parseFile()` 안에서도 대상을 한 번 더 거릅니다 (`DeclarePass.java:124`).
- Java 파일 중 패키지가 `rootPackages` 밖에 있으면 건너뜁니다. JSP는 패키지가 없으므로 이 조건을 적용하지 않습니다.
- JSP는 `JspToJava.convert()`로 Java 소스로 바꾼 뒤 Java와 같은 방식으로 파싱합니다. Java 코드가 없는 JSP는 `"Java 코드가 없는 JSP입니다."`라는 사유로 SKIPPED 처리됩니다.

---

## 질문

dstone-knowledge 에서 
analysis_reference.ref_kind 의 종류(범례)와 각 종류에 대한 설명을 해줘.

---

## 답변

Now let me see where each kind is produced.

---

## 답변

`analysis_reference.ref_kind` 값은 9가지입니다. 스키마 주석(`src/main/resources/schema/02-create-table-postgresql-dstone-knowledge.sql:264`)과 이 값을 만드는 코드가 일치합니다.

이 테이블에는 DECLARE 단계가 소스에서 **이름만 보고 적어 둔 참조**가 들어갑니다. 이 시점에는 그 이름이 실제로 무엇을 가리키는지 아직 모릅니다. 값은 `DeclarationCollector`가 넣고, RESOLVE 단계의 `ReferenceResolver`가 이를 풀어 `analysis_relation`의 관계로 바꿉니다.

| ref_kind | 기록 대상 (코드 예) | `name`에 들어가는 값 | `from_kind` | RESOLVE 후 관계 |
|---|---|---|---|---|
| `CALL` | 메소드 호출 `orderService.save(vo)`<br>생성자 안의 `this(...)`, `super(...)` | 메소드 이름 `save`<br>`this(...)`/`super(...)`는 `<init>` | METHOD (필드 초기화식이면 FIELD) | `CALLS` |
| `CREATE` | 객체 생성 `new OrderVo(a, b)` | 타입 이름 `OrderVo` (제네릭 제거) | METHOD / FIELD | `CREATES` (타입)와 `CALLS` (생성자) |
| `FIELD_ACCESS` | 필드 접근 `this.dao`, `Const.MAX` | 필드 이름 `dao`, `MAX` | METHOD / FIELD | `ACCESSES_FIELD` |
| `TYPE_USE` | 파라미터·반환·변수·제네릭 인자에 쓴 타입 `List<OrderVo> list` | 타입 이름 (`List`, `OrderVo`) | METHOD / FIELD | `USES_TYPE` |
| `THROWS` | 메소드 선언의 `throws BizException` | 예외 타입 이름 | METHOD | `THROWS` |
| `EXTENDS` | 클래스 선언의 `extends BaseDao` | 상위 클래스 이름 | TYPE | `EXTENDS` |
| `IMPLEMENTS` | `implements OrderService` (class, enum, record) | 인터페이스 이름 | TYPE | `IMPLEMENTS` |
| `ANONYMOUS_SUPER` | 익명 클래스의 상위 타입 `new Runnable() { ... }`<br>몸통이 따로 있는 enum 상수 | 상위 타입 이름 `Runnable` | TYPE (익명 클래스 `바깥타입$번호`) | 클래스면 `EXTENDS`, 인터페이스면 `IMPLEMENTS` |
| `METHOD_REF` | 메소드 참조 `OrderVo::getId` | 메소드 이름 `getId` | METHOD / FIELD | `CALLS` (이름으로만 추정) |

**종류별로 알아 둘 점**

- **`CALL`**
  - 호출 대상이 되는 식(`orderService`, `this.dao`)은 `scope_text`에, 인자 개수는 `arg_count`에 들어갑니다.
  - 첫 인자가 문자열 리터럴이면 `arg_text`에, 둘째 인자부터의 문자열 리터럴은 `args_json`에 적힌 그대로 담깁니다.
  - `sqlSession.selectList("order.findAll")`이나 `new ModelAndView("order/list")`처럼 문자열로 SQL이나 화면을 가리키는 호출을 SEMANTIC 단계의 규칙(`conf/rules`)이 이 값으로 연결합니다. `CREATE`도 같은 방식입니다.
- **`TYPE_USE`**
  - 한 멤버 안에서 같은 타입을 여러 번 써도 한 번만 기록합니다. 행 수를 줄이려는 처리입니다 (`DeclarationCollector.java:704`).
- **`ANONYMOUS_SUPER`**
  - DECLARE 시점에는 `new X() {}`의 `X`가 클래스인지 인터페이스인지 알 수 없어서 따로 둔 종류입니다.
  - RESOLVE가 이를 판별해 `EXTENDS`나 `IMPLEMENTS`로 연결합니다. 프로젝트 밖 타입이라 판별할 수 없으면 `EXTENDS`로 두고 `kindUnknown: true`를 붙입니다 (`ReferenceResolver.java:617`).
- **`METHOD_REF`**
  - `Foo::bar`는 해석기로 정확히 풀기 어렵습니다. 그래서 이름으로만 후보를 찾고 `HEURISTIC`으로 남깁니다 (`ReferenceResolver.java:237`).
- **람다**
  - 람다는 별도 타입으로 만들지 않습니다. 람다 안의 호출은 바깥 메소드에서 나간 `CALL`로 기록됩니다.
- **JSP**
  - JSP의 스크립틀릿은 `JspToJava`가 Java 소스로 바꿔 같은 방식으로 수집합니다. 그래서 JSP 전용 `ref_kind`는 없습니다.

**함께 보면 좋은 `status` 컬럼** (같은 테이블)

| status | 의미 |
|---|---|
| `PENDING` | 아직 RESOLVE 전 |
| `RESOLVED` | 프로젝트 안의 대상을 정확히 찾음 |
| `EXTERNAL` | 프로젝트 밖(JDK, 라이브러리)의 대상 |
| `HEURISTIC` | 이름 등으로 추정해 연결함 |
| `UNRESOLVED` | 찾지 못함. 사유는 `fail_reason`에 있음 |
| `IGNORED` | 풀 대상이 아님 |
