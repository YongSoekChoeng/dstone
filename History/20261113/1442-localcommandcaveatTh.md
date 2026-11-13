# localcommandcaveatTh

> 생성일시: 2026-11-13 14:42

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="01a2">
아래의 단계별로 테이블(CRUD) 와 간략한 설명을 기술해줘.
| 단계              | 테이블(CRUD)  | 기능
| --------------- | 
| ① SCAN          | ...
| ② CARRY         |
| ③ DECLARE       |
| ④ RESOURCE      |
| ⑤ CARRY_RESOLVE |
| ⑥ RESOLVE       |
| ⑦ LINK          |
| ⑧ SEMANTIC      |
| ⑨ DOCUMENT      |
</pasted_content id="01a2">

---

## 답변

There's already a section 2.10. Let me read it and the per-pass sections briefly.

---

## 답변

`docs/11.dstone-knowledge.md`의 0.4절과 2.10절 표, 그리고 각 Pass 코드의 DAO 호출을 대조해서 정리했습니다. 표기는 **C** = 행 추가, **R** = 조회, **U** = 컬럼 수정, **D** = 행 삭제, **복사** = 앞 리비전에서 행 복사입니다. 관리 테이블(`analysis_job`, `analysis_revision_pass`, `analysis_file_pass`, `analysis_error`)은 표에서 뺐습니다.

| 단계 | 테이블(CRUD) | 기능 |
|---|---|---|
| ① SCAN | `analysis_file` **C** | 소스 폴더의 파일을 모두 훑어 목록을 만들고 종류(Java, JSP, XML, properties 등)를 나눕니다. 이후 모든 단계가 이 목록을 기준으로 돕니다. |
| ② CARRY<br/>(증분 분석만) | `analysis_revision` **R**<br/>`analysis_file` **U** (`carried_from_file_id`, 파싱 결과)<br/>`analysis_symbol` / `_method` / `_field` / `_annotation` / `_reference` **복사**<br/>`analysis_mapper` / `_config` / `_resource` **복사** | 앞 리비전과 비교해 바뀌지 않은 파일의 선언과 자원 행을 그대로 옮겨 옵니다. 이 파일들은 다시 파싱하지 않습니다. |
| ③ DECLARE | `analysis_file` **R/U** (`parse_status`, `package_name` 등)<br/>`analysis_symbol` / `_method` / `_field` / `_annotation` **D·C**<br/>`analysis_reference` **D·C**<br/>`analysis_mapper` **D·C** (`JAVA`: Java 문자열 SQL) | Java 파일과 JSP 안의 Java를 한 파일씩 파싱해서 클래스, 메소드, 필드, 애노테이션을 기록합니다. 호출이나 타입 참조는 대상을 풀지 않고 이름만 적어 둡니다. |
| ④ RESOURCE | `analysis_file` **R**<br/>`analysis_mapper` **D·C** (`MYBATIS`, `IBATIS`, `QUERY_XML`)<br/>`analysis_config` **D·C**<br/>`analysis_resource` **D·C** (빈, 의존성, DDL) | Java가 아닌 파일을 읽습니다. 대상은 MyBatis/iBATIS 매퍼 XML의 SQL, properties·YAML 설정, 빌드 파일 의존성, DDL입니다. |
| ⑤ CARRY_RESOLVE<br/>(증분 분석만) | `analysis_relation` **복사**<br/>`analysis_reference` **U** (`status` → `PENDING`) | 앞 리비전에서 풀어 둔 관계 가운데 옮겨도 안전한 것만 복사합니다. 바뀐 파일의 영향을 받는 참조는 다시 풀도록 `PENDING`으로 되돌립니다. |
| ⑥ RESOLVE | `analysis_symbol` / `_method` / `_field` / `_reference` **R**<br/>`analysis_reference` **U** (`status`, `fail_reason`)<br/>`analysis_relation` **D·C** (`CALLS` 등 7종) | Java 파일을 다시 파싱하고 DB의 선언 정보를 조회해서 ③에서 이름만 적어 둔 참조의 실제 대상을 찾습니다. 그 결과가 "누가 누구를 부르나" 관계가 됩니다. |
| ⑦ LINK | `analysis_symbol` / `_method` **R**<br/>`analysis_relation` **D·C** (`OVERRIDES`, `CALLS_POSSIBLE_IMPLEMENTATION`)<br/>`analysis_metric` **D·C** | 파싱 없이 SQL만으로 재정의 관계와 인터페이스→구현 관계를 잇고, 품질 지표를 계산합니다. |
| ⑧ SEMANTIC | `analysis_*` 전반 **R** (+ 규칙 파일 `conf/rules`, `web.xml`, Struts 설정)<br/>`analysis_symbol` **U** (`layer`, `layer_confidence`, `properties_json`)<br/>`analysis_method` **U** (`proxy_related`)<br/>`analysis_endpoint` **D·C/U** (`method_id`)<br/>`analysis_relation` **D·C** (`INJECTS`, `EXECUTES_SQL`, `READS_TABLE`, `WRITES_TABLE`, `RENDERS`, `REQUESTS`, `INCLUDES`)<br/>`analysis_mapper` **D·C** (`JPA`), **U** (`method_id`)<br/>`analysis_resource` **D·C** (매핑, 화면명, 기능명, 이름 사전, 규칙 기록) | 규칙과 처리기(`LayerResolver`, `EntryFinder`, `SqlLinker` 등)로 의미를 붙입니다. 계층을 정하고, 진입점(URL·서블릿·JSP·거래)을 찾고, 주입 관계와 SQL·테이블·화면 연결을 만듭니다. |
| ⑨ DOCUMENT | `analysis_*` 전반 **R** (+ 소스의 해당 줄)<br/>`rag_document`, `rag_chunk` **D·C**<br/>`rag_embedding` **C** (새 `content_hash`만, 대기열) | 분석 결과를 파일 단위 설명 문서로 풀어 쓰고 청크로 자릅니다. 임베딩 대기열에도 올리며, 실제 임베딩 계산은 Job과 별도로 도는 `EmbeddingWorker`가 맡습니다. |

- 테이블 쓰기(C/U/D/복사)는 문서 2.10절 표와 같습니다. 조회(R)는 2.10절에 없어서 각 Pass가 부르는 DAO 메소드를 보고 덧붙였습니다.
- ⑧과 ⑨의 "`analysis_*` 전반 R"은 모든 테이블을 뜻하지 않고, 이 두 단계가 앞 단계의 결과 테이블을 두루 조회한다는 뜻입니다.

원하시면 이 R 열을 2.10절 표에 추가해 두겠습니다.

---

## 질문

/docs/11.dstone-knowledge.md > 0.4 분석 공정 9단계 단락의 표를 이 내용으로 교체해줘.

---

## 답변

`docs/11.dstone-knowledge.md`의 0.4절 표를 단계, 테이블(CRUD), 기능 세 열짜리 새 표로 바꿨습니다.

- **범례 추가:** 표 위에 C·R·U·D·복사 기호 설명과 관리 테이블을 뺐다는 안내, 그리고 2.10절 표로 가는 안내를 한 줄 넣었습니다.
- **각주 추가:** 표 아래에 ⑧·⑨의 "`analysis_*` 전반 R"이 앞 단계 결과 테이블을 두루 읽는다는 뜻이라는 설명을 붙였습니다.
- **문체:** 이 문서 본문과 맞추려고 문장 끝을 "~한다"체로 고쳤습니다.
- **빠진 열:** 원래 표에 있던 "비유하면"과 "담당 클래스" 열은 없어졌습니다. 담당 클래스는 2.0절 표에도 있어서 정보가 사라지지는 않습니다. 비유가 아쉬우시면 기능 열 앞에 다시 넣어 드리겠습니다.

커밋은 하지 않았습니다.

---

## 질문

올려줘.

---

## 답변

`docs/11.dstone-knowledge.md` 0.4절 표 교체를 커밋(`370c5192`)해서 `origin/main`에 푸시했습니다. 로컬과 원격이 같은 상태입니다.

---

## 질문

왜 dstone-knowledge 의 쿼리는 시스템 콘솔에 보이지 않을까?

---

## 답변

dstone-knowledge가 분석과 임베딩 중에 SQL 로그를 일부러 끄도록 만들어져 있어서 그렇습니다. 실행 방법에 따라 콘솔 출력 자체가 버려지는 경우도 있습니다.

**1. 분석·임베딩 스레드는 SQL 로그를 끈다 (주된 원인)**

`conf/log4j2.xml`의 `jdbc.sqltiming` 로거는 `INFO`로 켜져 있지만, 아래 필터가 붙어 있습니다.

```xml
<ThreadContextMapFilter onMatch="DENY" ...>
    <KeyValuePair key="SUPPRESS_SQL_LOG" value="Y"/>
</ThreadContextMapFilter>
```

아래 세 곳이 자기 스레드에 `SUPPRESS_SQL_LOG=Y`를 넣습니다. 그래서 이 스레드들이 보내는 SQL은 콘솔과 파일 어디에도 찍히지 않습니다.

| 위치 | 대상 |
|---|---|
| `analysis/job/AnalysisJobRunner.java:69` | 분석 Job 스레드 전체(① SCAN ~ ⑨ DOCUMENT) |
| `analysis/job/FilePassRunner.java:130` | 파일 단위 Pass의 작업 스레드 |
| `rag/EmbeddingWorker.java:129` | 임베딩 대기열 처리 스레드 |

주석에 이유가 적혀 있습니다. 분석은 저장 SQL을 수천에서 수만 번 보내기 때문에, 전부 남기면 로그가 SQL로 덮이고 분석도 느려집니다. 실제로 `LOGS/dstone-knowledge/execution/execution.log` 310줄 가운데 SQL은 25줄뿐입니다. 남은 것도 `analysis_revision_pass`, `analysis_revision` UPDATE처럼 Job 스레드 밖에서 실행된 것입니다. API 요청(검색, 요약 조회 등)을 처리하는 웹 스레드의 SQL은 그대로 찍힙니다.

**2. `bin/startApp.sh`로 띄우면 콘솔 출력이 버려진다**

`startApp.sh:26`이 `> /dev/null 2>&1`로 실행합니다. 그래서 이 방법으로 띄웠다면 웹 요청 SQL도 콘솔에서는 볼 수 없고, `${APP_HOME}/LOGS/dstone-knowledge/execution/execution.log`에서만 볼 수 있습니다. IDE나 `startApp.sh` 맨 끝의 주석 처리된 포그라운드 줄로 실행하면 콘솔에 나옵니다.

분석 SQL을 잠깐 봐야 한다면 두 가지 방법이 있습니다.
- **임시 확인:** `log4j2.xml`에서 `jdbc.sqltiming`의 `ThreadContextMapFilter`를 빼고 재기동합니다. 대신 로그가 매우 많아집니다.
- **설정으로 켜고 끄기:** 이 억제를 `dstone.knowledge.…` 같은 설정값으로 켜고 끌 수 있게 바꿉니다. 원하시면 이렇게 고쳐 드리겠습니다.

---

## 질문

<pasted_content id="01a2">
Windows 에서 수행한 
testApp 분석결과를 보면... 아래와 같이 나오는데 이런 경우는 해결할 수 없는 영역인가?

src/main/java/kr/co/gnx/comm/jwt/JwtTokenUtil.java:44    
METHOD_REF    
Claims.getSubject    
getUsernameFromToken(String)    
프로젝트에 같은 이름의 메소드가 없습니다    프로젝트에 같은 이름의 메소드가 없습니다. 메소드 참조(::)는 이름으로만 찾습니다.
</pasted_content id="01a2">

---

## 답변

ReferenceResolver에서 메소드 참조(`::`)를 어떻게 처리하는지 확인하겠습니다.

---

## 답변

해결 가능한지 보려면 일반 메소드 호출이 외부 라이브러리를 어떻게 처리하는지와 비교해야 해서, 그 부분을 확인하겠습니다.

---

## 답변

해결할 수 있습니다. 이 결과는 분석기의 한계 때문이 아니라 메소드 참조(`::`) 처리가 단순하게 구현돼 있어서 생겼습니다.

**원인**

`pass06/resolve/ReferenceResolver.java:275`의 `walk()`는 `Foo::bar` 형태를 만나면 아무것도 따지지 않고 바로 `guessByName()`을 부릅니다. 메소드 이름만으로 프로젝트 전체를 찾는 방식입니다.

```java
} else if (node instanceof MethodReferenceExpr) {
    // Foo::bar 는 해석기가 잘 풀지 못한다. 이름으로만 후보를 찾는다.
    guessByName(context, reference, "CALLS", (String) reference.get("name"), null, "메소드 참조(::)는 이름으로만 찾습니다.");
```

`Claims::getSubject`의 `Claims`는 jjwt 라이브러리의 `io.jsonwebtoken.Claims`입니다. 프로젝트 안에는 `getSubject`라는 메소드가 없으니 "같은 이름의 메소드가 없습니다"가 되고, 결과는 `UNRESOLVED`로 남습니다.

**일반 호출은 이미 이렇게 처리합니다**

`claims.getSubject()` 같은 일반 호출은 `resolveCall()`(335행)이 세 단계로 처리합니다.
1. 해석기(javaparser)로 먼저 풀어 봅니다.
2. 실패하면 `typeOfExpression()`으로 앞쪽 대상의 타입을 알아냅니다. jar가 없어도 import만으로 `io.jsonwebtoken.Claims`라는 걸 압니다.
3. 그 타입이 프로젝트 밖이면 `EXTERNAL_METHOD` `io.jsonwebtoken.Claims.getSubject`로 적고 상태를 `EXTERNAL`로 둡니다.

메소드 참조에도 같은 순서를 적용하면 이 건은 `EXTERNAL`로 바뀝니다. 대략 이렇게 고치면 됩니다.

| 순서 | 처리 | 예 |
|---|---|---|
| 1 | 해석기로 먼저 풀기 (`MethodReferenceExpr.resolve()`) | jar가 있으면 정확히 풀림 |
| 2 | 앞쪽(`getScope()`)의 타입을 `typeOfExpression()`으로 알아내기. 타입 이름, 변수, `this`, `super` 모두 해당 | `Claims` → import로 `io.jsonwebtoken.Claims` |
| 3 | 프로젝트 밖 타입이면 `EXTERNAL_METHOD`로 적기 | `io.jsonwebtoken.Claims.getSubject` |
| 4 | 프로젝트 안 타입이면 그 타입의 같은 이름 메소드로 잇기. 인자 수를 모르므로 이름만 봄 | `UserVO::getName` → 정확히 연결 |
| 5 | 타입을 끝내 모를 때만 지금처럼 `guessByName()` | |

`Foo::new` 같은 생성자 참조도 같은 틀에서 생성자(`<init>`)로 이을 수 있습니다.

**고칠 때 주의할 점**
- 메소드 참조는 인자 수를 알 수 없습니다. 프로젝트 안 타입에 같은 이름의 오버로드가 여러 개 있으면 후보가 여럿이 되고, 이 경우는 `HEURISTIC`으로 남습니다.
- RESOLVE Pass를 고치는 것이므로 CLAUDE.md 규칙대로 증분 분석과 전체 분석 결과가 같은지 비교해야 합니다. testApp으로 고치기 전후의 `METHOD_REF` 상태 분포도 함께 비교하겠습니다.

고쳐 드릴까요?

---

## 질문

현재 분석기가 람다식도 해석할 수 있는 상태인거지?

---

## 답변

람다를 따로 처리하는 코드가 있는지 확인하겠습니다.

---

## 답변

"람다식을 해석한다"를 람다 안의 호출을 찾아 잇는 것으로 보면, 반은 맞습니다. 람다 안의 호출은 찾아서 풀려고 하지만, 람다 파라미터의 타입을 알아낼 수 있을 때만 정확하게 풀립니다. 아래는 코드를 읽고 정리한 것이고, testApp 결과에서 직접 확인하지는 않았습니다.

**람다는 어떻게 다뤄지나**
- **저장 방식:** 람다는 따로 타입이나 메소드로 저장하지 않습니다. 람다 안의 호출은 바깥 메소드에서 나가는 `CALL`로 적힙니다(`DeclarationCollector.java:726`, 문서 2-3절 "못 하는 것").
- **푸는 방식:** RESOLVE의 `walk()`는 람다 본문까지 내려갑니다. 그래서 람다 안의 `m.getName()`도 일반 호출처럼 `resolveCall()`로 풀립니다.

**경우별로 이렇게 됩니다**

| 람다 모양 | 결과 | 이유 |
|---|---|---|
| `(MemberVO m) -> m.getName()`<br/>파라미터 타입을 적음 | 잘 풀림 | `declaredTypeOf()`가 람다 파라미터의 선언 타입을 읽습니다(1099행). |
| `list.forEach(m -> m.getName())`<br/>`List<MemberVO>`이고 `MemberVO`가 프로젝트 타입 | 대체로 풀림 | javaparser가 `forEach(Consumer<? super T>)`에서 `m`의 타입을 추론합니다. JDK 타입은 `ReflectionTypeSolver`, 프로젝트 타입은 `DbTypeSolver`가 답합니다. |
| 람다를 받는 쪽이 외부 라이브러리이고 그 jar가 클래스패스에 없음 | 잘 안 풀림 | 함수형 인터페이스를 몰라서 파라미터 타입 추론이 실패합니다. |
| 추론이 실패한 경우 전부 | 이름으로 짐작하거나 `UNRESOLVED` | 타입을 적지 않은 파라미터는 `declaredTypeOf()`가 `null`을 돌려줍니다(`ClassOrInterfaceType`이 아님). 그러면 대상 타입을 모르는 호출이 되어 `guessByName()`으로 프로젝트 전체에서 같은 이름을 찾습니다. 결과는 `HEURISTIC`/`LOW`, 없으면 `UNRESOLVED`입니다. |
| `Claims::getSubject`, `this::foo` 같은 메소드 참조 | 이름으로만 찾음 | 앞에서 말씀드린 약점입니다. |
| `new Thread(() -> ...)`의 람다 본문 | 진입점으로 보지 않음 | `EntryFinder.java:53`에 지원하지 않는다고 적혀 있습니다. |

**정리하면**
- **잘 되는 경우:** 람다 안의 호출이 JDK 컬렉션·스트림 위에서 프로젝트 타입을 다루는 흔한 경우입니다.
- **약한 경우:** 외부 라이브러리 jar가 없는 상태에서 타입을 적지 않은 람다 파라미터입니다. 이때 엉뚱한 같은 이름 메소드와 이어질 위험이 있습니다(`LOW`로 표시됨).
- **Windows 분석:** testApp을 Windows에서 분석할 때 의존 jar가 클래스패스에 잡혔다면 결과가 더 좋을 것입니다.

**고칠 수 있는 것**
1. **메소드 참조(`::`)** 를 일반 호출처럼 앞쪽 타입부터 따지게 고칩니다. 앞 질문의 건으로, 쉽고 효과가 확실합니다.
2. **추론에 실패한 람다 파라미터**는 DB 정보로 보강할 수 있습니다. 예를 들어 `list.forEach(m -> …)`에서 `list`의 선언 타입 `List<MemberVO>`의 제네릭 인자로 `m`을 `MemberVO`로 짐작하는 방식입니다. 할 수는 있지만 경우가 많아서 1번보다 손이 많이 갑니다.

testApp에서 람다 안의 호출이 실제로 얼마나 풀렸는지 숫자로 보고 싶으시면, 분석 DB에서 상태 분포를 뽑아 드리겠습니다.

---

## 질문

1번으로 고쳐줘. 그런데 먼저 영향도부터 파악해줘.

---

## 답변

먼저 영향 범위를 파악하겠습니다. 코드에서 `METHOD_REF` 결과를 쓰는 곳과 DB에 실제로 쌓인 건수를 함께 보겠습니다.

---

## 답변

Local 분석 DB에서 프로젝트별 `METHOD_REF` 건수를 뽑는 중입니다.

---

## 답변

영향 범위 파악을 마쳤습니다. 결론부터 말씀드리면 영향은 작고, 고쳐도 다른 단계를 손볼 필요는 없습니다.

## 1. 바뀌는 코드

`pass06/resolve/ReferenceResolver.java` 한 곳만 바뀝니다. `walk()`의 `MethodReferenceExpr` 분기를 새 메소드 `resolveMethodRef()`로 바꾸고, 지금 일반 호출에 쓰는 `typeOfExpression()`, `emitDeclaredMembers()`, `emitCallable()`, `guessByName()`을 그대로 다시 씁니다. DECLARE에서 남기는 `METHOD_REF` 참조 행, 테이블, SQL, 규칙 파일은 바뀌지 않습니다.

## 2. 실제 데이터에 미치는 영향 (WSL DB, testApp 리비전 117)

| 항목 | 값 |
|---|---|
| 전체 참조 | 9,281건 |
| `METHOD_REF` | 2건 (둘 다 `UNRESOLVED`) |
| 해당 코드 | `JwtTokenUtil.java:44` `Claims::getSubject`, `:67` `Claims::getExpiration` |

고친 뒤에는 이 2건이 `EXTERNAL` 상태가 되고, `CALLS` 관계 2개(`EXTERNAL_METHOD`, `io.jsonwebtoken.Claims.getSubject` / `getExpiration`)가 새로 생깁니다. 다른 참조 9,279건은 지나는 코드가 달라서 결과가 바뀌지 않습니다. 테스트 샘플 프로젝트 7개(`src/test/resources/samples`)에는 `::`가 하나도 없습니다.

## 3. 뒤 단계에 미치는 영향

| 단계·기능 | 영향 |
|---|---|
| ⑤ CARRY_RESOLVE (증분 분석) | 없음. 다시 풀 파일을 고르는 기준은 두 가지입니다. 하나는 관계의 `to_id`가 바뀐 파일을 가리키는지이고, 다른 하나는 `EXTERNAL`/`HEURISTIC`/`UNRESOLVED` 참조의 이름이 새로 생긴 이름과 같은지입니다. `METHOD_REF`도 일반 `CALL`과 같은 상태값과 관계를 만들기 때문에 이 기준이 그대로 들어맞습니다. |
| ⑦ LINK | 프로젝트 안 인터페이스 메소드를 가리키는 `X::foo`가 이제 정확히 이어지므로, 구현체로 가는 `CALLS_POSSIBLE_IMPLEMENTATION`이 더 정확해집니다. 지금은 이름이 같은 엉뚱한 메소드와 이어질 수 있습니다. |
| ⑧ SEMANTIC | 없음. `sql:`/`view:` 규칙은 호출 인자를 보는데, 메소드 참조에는 인자가 없습니다. |
| ⑨ DOCUMENT, 영향도, 노드 맵 | 호출 관계가 정확해진 만큼 결과도 따라서 좋아집니다. 형식 변경은 없습니다. |
| 해석 품질 지표 (`analysis_metric`) | testApp의 `UNRESOLVED`가 2건 줄어듭니다. |

## 4. 알고 있어야 할 점

- **오버로드:** 메소드 참조는 인자 수를 모릅니다. 프로젝트 안 타입에 같은 이름의 오버로드가 여러 개 있으면 모두 후보로 잇고 `HEURISTIC`으로 남깁니다. 그래도 지금처럼 프로젝트 전체에서 찾는 것보다는 범위가 훨씬 좁습니다.
- **증분 분석의 기존 한계:** import 없이 같은 패키지에 같은 이름의 타입이 새로 생기면, 바뀌지 않은 파일은 다시 풀리지 않습니다. 이 한계는 일반 `CALL`에도 이미 있는 것이고, 이번 수정으로 새로 생기지는 않습니다.
- **문서:** `docs/11.dstone-knowledge.md`에서 "이름으로만 찾는다"는 부분 세 군데(1265, 1268, 1420행)를 고쳐야 합니다.

## 5. 검증 계획

1. **샘플 추가:** `::`가 없으므로 `cycle-app` 같은 샘플에 메소드 참조 몇 가지를 넣습니다. 외부 타입, 프로젝트 타입, `this::`, 변수, `Foo::new`를 하나씩 넣습니다.
2. **시험 인스턴스:** 다른 포트에서 `-Ddstone.knowledge.rag.embedding.enabled=false`로 띄웁니다.
3. **비교:** testApp과 샘플을 전체 분석한 뒤 증분 분석을 한 번 더 돌리고, 두 결과의 `analysis_reference` 상태와 `analysis_relation`이 같은지 비교합니다. 다만 testApp의 소스는 Windows 쪽(`D:\AppHome\testApp`)에 있으므로, WSL 인스턴스에서는 읽기 전용 경로인 `/mnt/d/AppHome/testApp`을 따로 프로젝트로 등록해서 돌리겠습니다.

이대로 진행할까요?

---

## 질문

응 진행해줘.

---

## 답변

구현하겠습니다. `walk()`의 분기를 새 메소드로 바꿉니다.

---

## 답변

`this::foo`는 해석기가 실패했을 때 타입을 못 찾을 수 있어서, 감싸고 있는 타입을 쓰도록 보강합니다.

---

## 답변

익명 클래스 안의 `this::foo`가 바깥 타입으로 잘못 이어지지 않도록 막아 두겠습니다.

---

## 답변

기존 샘플은 다른 테스트가 쓰고 있어서, 메소드 참조 전용 샘플 `methodref-app`을 새로 만들겠습니다.

---

## 답변

샘플을 만들었습니다. 시험 인스턴스를 띄우기 전에, 4081 포트에 기존 인스턴스가 떠 있는지 확인합니다.

---

## 답변

기존 인스턴스는 떠 있지 않습니다. 빌드하고, 기동 방식을 확인하겠습니다.

---

## 답변

시험 인스턴스(4181)가 뜨기를 기다립니다.

---

## 답변

샘플 분석이 끝났습니다. 결과를 보겠습니다.

---

## 답변

testApp 분석도 끝났습니다. Windows에서 고치기 전 코드로 분석한 리비전 117과, 고친 코드로 분석한 리비전 119를 비교합니다.

---

## 답변

2차 비교도 모두 0건이고, 원래대로 되돌린 결과는 처음 전체 분석(118)과 같습니다. testApp 증분 분석이 도는 동안 단위 테스트를 돌립니다.

---

## 답변

196개 중 1개가 실패했습니다. 실패한 `RuleFileParserTest`는 이번에 고친 코드와 관련이 없어 보입니다. 원래부터 실패하던 것인지 확인하겠습니다.

---

## 답변

testApp에서 증분(124)과 전체(119) 사이에 `analysis_relation` 차이가 694건 나왔습니다. 이번 변경 때문인지 원래 있던 문제인지 확인하겠습니다.

---

## 답변

양쪽에 같은 수만큼 있어서 실제 차이가 아니라 비교 방식 문제로 보입니다. SEMANTIC 관계의 `to_id`가 리비전마다 새로 매겨지는 번호인지 확인합니다.

---

## 답변

Now edit docs.

---

## 답변

문서 정리를 마쳤습니다. 시험 인스턴스를 내리고 최종 diff를 확인합니다.

---

## 답변

시험 인스턴스를 내렸습니다. 종료 코드 144는 `pkill`이 내린 신호 때문입니다. 남은 확인을 다시 실행합니다.

---

## 답변

메소드 참조(`::`)도 이제 일반 메소드 호출과 같은 순서로 풀립니다. testApp에서 `UNRESOLVED`였던 `Claims::getSubject`, `Claims::getExpiration` 2건은 `EXTERNAL`로 바뀝니다. 그 밖의 결과는 고치기 전과 같고, 증분 분석과 전체 분석 결과도 일치합니다. 커밋은 아직 하지 않았습니다.

## 바꾼 것

**`ReferenceResolver.java`**: `resolveMethodRef()`를 새로 만들어 아래 순서로 풉니다.
1. 해석기(javaparser)로 먼저 풀어 봅니다.
2. 못 풀면 `::` 앞의 타입을 알아냅니다. 프로젝트 밖 타입이면 `EXTERNAL_METHOD`로 적고, 프로젝트 안 타입이면 그 타입과 상위 타입에서 같은 이름의 메소드를 찾습니다. `this::`는 감싸고 있는 타입으로 보되, 익명 클래스 안에서는 이렇게 짐작하지 않습니다.
3. 타입도 모를 때만 지금처럼 이름으로 짐작합니다.
4. `Foo::new`는 `Foo`의 생성자로 잇고, `String[]::new`는 `IGNORED`로 둡니다.

**샘플 `samples/methodref-app`**: 메소드 참조 8가지 모양을 담은 샘플을 새로 만들고, `README.md`에 기대 값을 적었습니다.

**문서**: `docs/11.dstone-knowledge.md`의 2-3절, 2-6절, 6.3절과 `.claude/docs/knowledge-core.md`에서 "이름으로만 찾는다"는 설명을 고쳤습니다.

## 검증 결과

검증은 4181 포트에 띄운 시험 인스턴스로 했고, 끝난 뒤 내렸습니다.

| 확인 | 결과 |
|---|---|
| 샘플 8건 | 모두 기대대로 나왔습니다. 오버로드가 있는 `formatter::format`도 해석기가 `format(String)`으로 정확히 골랐습니다. |
| testApp, 고치기 전(117)과 후(119) | `METHOD_REF` 2건이 `UNRESOLVED`에서 `EXTERNAL`로 바뀌고 `CALLS`가 2건 늘었습니다. 다른 차이는 없습니다. |
| 증분과 전체 비교 (샘플 2회, testApp 1회) | 8개 테이블 모두 차이 0건입니다. 1차는 참조 대상 쪽 파일만 고쳐서, 해석기가 실패한 뒤 타입으로 찾는 경로까지 실제로 지나가게 했습니다. |
| 단위 테스트 | 196개 중 195개 통과, 1개 실패. 실패한 `RuleFileParserTest.옛_형식의_키는_새_형식을_알려_준다`는 규칙 파일 파서만 시험해서 이번 변경과는 관계가 없어 보입니다. 다만 고치기 전 코드로 다시 돌려 보지는 않았습니다. |

증분과 전체 비교 중에 testApp에서 차이가 694건 나왔다가, 다시 보니 비교 스크립트 문제였습니다. `F…`·`S…` 형식의 관계 ID가 리비전마다 새로 매겨지는데 그 번호를 그대로 비교했기 때문입니다. 경로와 statement 이름으로 바꿔서 다시 비교하니 0건이었습니다.

## 확인해 주실 것

1. **검증용 프로젝트 정리**: WSL DB에 `mref-sample`과 `testapp-wsl` 프로젝트, 그리고 리비전 118~124가 남아 있습니다. 지워도 될까요?
2. **실패하는 테스트**: `pom.xml`에 `skipTests`가 `true`로 박혀 있어서 `mvn test -DskipTests=false`로는 테스트가 돌지 않았습니다. 그래서 위 테스트는 JUnit을 직접 실행해서 돌렸습니다. 이 때문에 실패하는 테스트가 그동안 드러나지 않았을 수 있습니다. 따로 살펴볼까요?
3. **커밋**: 커밋하고 푸시할까요?

---

## 질문

1,3 진행해줘.

---

## 답변

먼저 프로젝트를 지우는 기능이 있는지 확인하겠습니다.

---

## 답변

리비전 삭제 API(`DELETE /api/revisions/{id}`)가 있습니다. 이 API가 무엇을 지우는지 확인합니다.

---

## 답변

시험 인스턴스를 다시 띄워 API로 리비전 7개를 지우겠습니다. 그다음 남은 프로젝트 행은 SQL로 정리합니다.

---

## 답변

정리가 끝났고 남은 프로젝트는 `testApp` 하나입니다. 시험 인스턴스가 내려갔는지 확인한 뒤 커밋하고 푸시합니다.

---

## 답변

요청하신 1번(검증용 데이터 정리)과 3번(커밋·푸시)을 마쳤습니다.

**1. 검증용 데이터 정리**
- 리비전 118~124 일곱 개는 시험 인스턴스의 리비전 삭제 API(`DELETE /api/revisions/{id}`)로 지웠습니다.
- 리비전을 지운 뒤 남아 있던 `analysis_project`의 `mref-sample`, `testapp-wsl` 두 행은 SQL로 지웠습니다.
- 지운 뒤 확인해 보니 리비전 118~124에 딸린 행은 어느 테이블에도 남아 있지 않고, 남은 프로젝트는 원래 있던 `testApp` 하나뿐입니다.
- 시험 인스턴스(4181)는 내렸습니다. 백그라운드 작업이 "실패(종료 코드 144)"로 표시된 것은 제가 프로세스를 직접 종료해서 그렇습니다.

**3. 커밋·푸시**
`9ffa6dd3 fix(knowledge): 메소드 참조(::)를 일반 호출처럼 대상 타입부터 풀기`를 `origin/main`에 푸시했습니다. 로컬과 원격이 같은 상태입니다. 커밋에 들어간 파일은 다음과 같습니다.
- `ReferenceResolver.java`
- 새 샘플 `samples/methodref-app`(Java 파일 3개와 `README.md`)
- 문서 2개(`docs/11.dstone-knowledge.md`, `.claude/docs/knowledge-core.md`)

Windows 쪽 dstone-knowledge는 아직 이전 코드입니다. testApp 결과에 반영하려면 Windows 클론에서 `git pull`로 받아 다시 빌드한 뒤 testApp을 다시 분석해야 합니다.

---

## 질문

<pasted_content id="01a2">
dstone-boot>knowledge 의 프로젝트 · 분석 에서

리비전 요약 > 한눈에 보기를 보면 
"메소드 호출 4,291건 가운데 99.74% 가 누구를 부르는지 풀렸습니다" 
라는 표현이 있는데 99.74% 를 볼드로 표현하고 싶어. 
</pasted_content id="01a2">

---

## 답변

CSS가 굵은 글씨를 막지 않는지 확인하고, 문법도 검사하겠습니다.

---

## 답변

한눈에 보기 문장에서 `99.74%` 부분이 굵게 나오도록 `dstone-boot/src/main/webapp/knowledge/assets/js/project.js`를 고쳤습니다. 문법 검사는 통과했지만 실제 화면에서는 아직 보지 않았습니다.

- **문장:** 비율 앞뒤를 `**`로 감쌌습니다(`"건 가운데 **" + rate + "%** 가 …"`).
- **출력:** `verdictBox()`가 글을 `K.escapeHtml`로 먼저 이스케이프한 뒤, `**…**`로 감싼 부분만 `<strong>`으로 바꿉니다. 이스케이프를 먼저 하므로 글 안의 다른 HTML은 지금처럼 글자로 나옵니다. 테스트 문자열로 돌려 보니 의도대로 나왔습니다.
- **다른 줄:** 한눈에 보기의 다른 문장에도 `**`를 붙이면 같은 방식으로 굵게 할 수 있습니다.

비율이 95% 미만인 경고·위험 줄은 CSS에서 이미 문장 전체가 굵게(`font-weight: 600`) 나옵니다. 이 줄에서는 비율이 조금 더 굵어질 뿐이라 차이가 크지 않습니다. 이 줄에서도 비율을 더 눈에 띄게 하려면 색을 넣는 방법이 있습니다.

정적 파일만 바꿨으니 dstone-boot가 쓰는 webapp 경로에 따라 새로고침만으로 반영될 수도 있습니다. Pod로 도는 dstone-boot는 다시 빌드해서 배포해야 반영됩니다. 커밋은 하지 않았습니다.

---

## 질문

<pasted_content id="01a2">
dstone-boot>knowledge 의 영향도 · 호출 관계 에서

kr.co.gnx.base.BaseController#getCommonCodeService() 을(를) 부르는 쪽 을 보면...
jsp 는 전부 신뢰도가 MIDEUM 이던데 그리고 동일한 내용이 여러줄 표현되고...
내용을 확인해줘.
</pasted_content id="01a2">

---

## 답변

DB에서 testApp 최신 리비전(117)을 기준으로, 이 메소드를 부르는 관계를 확인하겠습니다.

---

## 답변

최신 리비전이 125로 바뀌어 있어서 125 기준으로 다시 봅니다.

---

## 답변

Run the same query (depth 3) on revision 125.

---

## 답변

신뢰도가 `MEDIUM`인 이유는 확인됐습니다. 이제 dstone-boot 화면이 부르는 쪽 목록을 어떤 열로 그리는지 확인하겠습니다.

---

## 답변

확인해 보니 두 현상 모두 버그는 아닙니다. 다만 두 번째 현상은 화면에 정보가 하나 빠져 있어서 같은 줄이 반복되는 것처럼 보입니다. 최신 리비전 125(Windows에서 분석한 testApp)로 확인했습니다.

## 화면에 나오는 것의 구조

`getCommonCodeService()`를 직접 부르는 것은 `CommonCodeController`의 메소드 10개입니다. 호출은 18건이고 모두 `HIGH`입니다. JSP는 그 컨트롤러 메소드의 주소를 요청하는 쪽이라 **거리 2**에 나옵니다.

```
JSP ──REQUESTS(MEDIUM)──▶ CommonCodeController#getCommonCode() ──CALLS(HIGH)──▶ BaseController#getCommonCodeService()
```

## 1. JSP가 모두 MEDIUM인 이유: 원래 그렇게 정해 둔 것

JSP에서 컨트롤러로 가는 `REQUESTS` 관계는 `JspLinker`가 항상 `MEDIUM`/`HEURISTIC`으로 만듭니다(`JspLinker.java:167`, `:215`).
- **JSP 글자에 적힌 주소**(`PATH_TEXT`): 예를 들어 `commoncode.jsp:141`의 `"/commoncode/getCommonCodeGroup.ajax"`입니다. 실행 코드가 아니라 글자가 진입점 주소와 같다는 것만 보고 잇기 때문에 `MEDIUM`입니다.
- **`<script src>`로 불러온 .js에 적힌 주소**(`SCRIPT_SRC`): 화면이 그 스크립트 함수를 실제로 부르는지는 모르기 때문에 `MEDIUM`입니다(주석에 그렇게 적혀 있습니다).

Java 호출처럼 컴파일러 수준으로 확인할 방법이 없으니, 이 단계를 `MEDIUM`으로 두는 것이 맞다고 봅니다.

## 2. 같은 내용이 여러 줄 나오는 이유: 화면에 "어느 메소드를 거쳐서"가 없음

`layout.jsp`, `layoutPop.jsp`, `login.jsp`, `smsAuthPop.jsp`는 각각 5줄씩 나옵니다. 네 파일 모두 해당 줄이 같은 코드입니다.

```html
<script type="text/javascript" src="/resources/js/common/genexon.js"></script>
```

`genexon.js` 안에 `/commoncode/...` 주소가 5개 적혀 있어서, 각 JSP가 이 `<script>` 한 줄을 근거로 컨트롤러 메소드 5개에 따로 이어집니다.

| JSP 줄 | 요청 주소(`properties_json.path`) | 거쳐 가는 메소드 |
|---|---|---|
| `layout.jsp:26` | `/commoncode/getCommonCode.ajax` | `CommonCodeController`의 메소드 ① |
| `layout.jsp:26` | `/commoncode/getAllBondGbn.ajax` | ② |
| `layout.jsp:26` | `/commoncode/getAllBondType.ajax` | ③ |
| `layout.jsp:26` | `/commoncode/getCommonCodeGroup.ajax` | ④ |
| `layout.jsp:26` | `/commoncode/getProductgroupKind2.ajax` | ⑤ |

DB에 있는 다섯 행은 서로 다른 관계입니다. 그런데 `impact.js:169`의 표에는 거리, 관계, 신뢰도, 부르는 쪽, 적힌 파일, 줄만 나옵니다. **어느 메소드(어느 주소)로 이어졌는지**가 빠져서 같은 줄이 반복되는 것처럼 보입니다. `commoncode.jsp`는 줄 번호가 달라서 구분이 되고, 공통 스크립트를 불러오는 네 파일만 똑같아 보입니다.

## 고치는 방법

표에 **"→ 대상"** 열을 하나 더하는 것을 권합니다. 값은 그 단계에서 불리는 메소드와, `REQUESTS`라면 요청 주소입니다. 예를 들면 `CommonCodeController#getCommonCode()` · `/commoncode/getCommonCode.ajax` 같은 식입니다.
- **dstone-knowledge**: `RelationDao.xml`의 `selectCallers`에서 `toId`의 이름(`kg_node`)과 `properties_json`의 `path`·`via`를 함께 돌려줍니다.
- **dstone-boot**: `impact.js`에 그 열을 추가합니다.

다섯 줄을 한 줄로 합치는 방법도 있습니다. 하지만 그러면 이 화면이 어느 주소들 때문에 이어졌는지가 사라지므로, 열을 추가하는 쪽이 낫다고 봅니다. 고쳐 드릴까요?

---

## 질문

응 수정하고 올려줘.

---

## 답변

먼저 `kg_node`가 메소드 이름을 어떤 모양으로 돌려주는지 확인합니다.

---

## 답변

Now edit the SQL.

---

## 답변

SQL을 testApp 리비전 125에 바로 돌려 확인하고, 이 쿼리를 쓰는 다른 곳도 찾아봅니다.

---

## 답변

SQL 결과는 의도대로 나옵니다. 이제 화면의 표 그리는 함수 `K.table`이 열을 어떻게 받는지 확인합니다.

---

## 답변

화면 코드를 고쳤습니다. 문법을 검사하고, dstone-knowledge를 다시 빌드해 4181 시험 인스턴스에서 API 응답을 확인합니다.

---

## 답변

API 응답은 46건으로 이전과 같고, 새 필드도 들어 있습니다. 화면 함수에 이 응답을 넣어 실제로 어떻게 그려지는지 확인합니다.

---

## 답변

화면에서도 다섯 줄이 거쳐 가는 메소드와 요청 주소로 구분됩니다. 시험 인스턴스를 내리고 문서를 갱신하겠습니다.

---

## 답변

Add a short paragraph after line 650 in docs and a line in knowledge-ui.md.

---

## 답변

앞에서 한 굵은 글씨 수정(`project.js`)도 아직 커밋되지 않은 상태라, 커밋을 두 개로 나눠 올리겠습니다.

---

## 답변

영향도 화면의 '부르는 쪽' 표에서 같은 줄이 반복되던 문제를 고쳐 `origin/main`에 올렸습니다. 이제 각 행이 어느 메소드를 거치고 어느 주소를 요청하는지 보여서 구분됩니다.

**바꾼 것**
- **dstone-knowledge** (`RelationDao.xml` `selectCallers`): 부르는 쪽 각 행에 그 단계에서 불리는 메소드 이름(`to`)을 함께 돌려줍니다. 화면이 주소를 요청한 경우(`REQUESTS`)에는 요청 주소(`requestPath`)와 근거(`via`)도 돌려줍니다.
- **dstone-boot** (`impact.js`): 부르는 쪽 표에 "→ 거쳐 가는 곳" 열을 추가했습니다.
  - 고른 메소드를 바로 부르는 행(거리 1)은 "이 메소드"로 나옵니다.
  - 그 밖의 행은 `CommonCodeController#getCommonCode(…) · /commoncode/getCommonCode.ajax`처럼 나옵니다.
  - 공통 스크립트를 거쳐 이어진 경우에는 뒤에 "(불러온 스크립트)"가 붙습니다.
- **문서**: `docs/11.dstone-knowledge.md` 0.9절과 `.claude/docs/knowledge-ui.md`에 반영했습니다. JSP가 언제나 `MEDIUM`인 이유도 함께 적었습니다.

**확인한 것**
testApp 리비전 125의 `getCommonCodeService()`를 깊이 2로 조회했습니다.
- **API**: 4181 시험 인스턴스에서 응답이 이전과 같은 46건이었고, 새 필드가 들어 있었습니다. 시험 인스턴스는 확인 후 내렸습니다.
- **화면 표시**: 그 응답을 `impact.js`의 표시 함수에 넣어 보니 `layout.jsp:26`의 다섯 줄이 각각 다른 메소드와 주소로 나왔습니다.
- **브라우저**: 실제 dstone-boot 화면에서는 보지 않았습니다.

**커밋**
- `06ab5115`: 앞에서 고친 '한눈에 보기' 굵은 글씨(`project.js`). 커밋되지 않은 채 남아 있어서 따로 함께 올렸습니다.
- `83c06b25`: 이번 수정.

화면에 반영하려면 두 모듈 모두 다시 배포해야 합니다. dstone-knowledge는 다시 빌드해서 재기동하고, dstone-boot는 다시 빌드해서 Pod로 배포하면 됩니다. Windows 쪽 인스턴스라면 `git pull`로 받은 뒤 다시 빌드하면 됩니다.

---

## 질문

<pasted_content id="01a2">
dstone-boot>knowledge 의 영향도 · 호출 관계 에서

부르는 쪽을 표현하는 화면이 조금 어색하고 불편해.
이유는 왜 그런지 알겠는데 오히려 기존의 화면이 보기에 나아. 그렇다고 동일해보이는 정보를 여러개 반복해서 표현하기도 좀 그렇고...
좋은 방법이 없을까? 
</pasted_content id="01a2">

---

## 답변

좋은 방법이 있습니다. **이전 화면의 열은 그대로 두고, 같은 줄을 한 행으로 합친 뒤 "몇 개를 거쳐 가는지"만 작게 붙이는 방식**을 권합니다. 펼쳐 보는 것은 원할 때만 하면 됩니다.

지금 화면이 불편한 이유는 정보가 많아져서라기보다, 대부분의 경우 필요 없는 정보가 모든 행에 붙기 때문입니다. 거쳐 가는 곳은 같은 줄이 여러 행으로 나올 때만 의미가 있습니다. Java 호출(`CALLS`)이나 주소를 하나만 요청하는 JSP는 행 하나에 거쳐 가는 곳도 하나라서, 보여 줘도 얻는 게 없습니다.

## 제안: 같은 줄은 한 행으로 합치고, 개수만 표시

거리·관계·신뢰도·부르는 쪽·적힌 파일·줄이 모두 같은 행을 하나로 합칩니다. 합쳐진 행에만 `▸ 주소 5개` 표시를 붙이고, 누르면 그 아래에 펼쳐집니다.

```
거리 관계      신뢰도  부르는 쪽                                적힌 파일                     줄
 1   CALLS     HIGH    CommonCodeController#getCommonCode(…)   …/CommonCodeController.java   38
 1   CALLS     HIGH    CommonCodeController#getAllBondGbn(…)   …/CommonCodeController.java   55
 2   REQUESTS  MEDIUM  …/sitemesh/layout.jsp  ▸ 주소 5개         …/sitemesh/layout.jsp         26
 2   REQUESTS  MEDIUM  …/views/comm/login.jsp ▸ 주소 5개         …/views/comm/login.jsp        11
 2   REQUESTS  MEDIUM  …/commoncode/commoncode.jsp              …/commoncode.jsp             141
```

`▸ 주소 5개`를 누르면 이렇게 펼쳐집니다.

```
 2   REQUESTS  MEDIUM  …/sitemesh/layout.jsp  ▾ 주소 5개 (불러온 스크립트 genexon.js)   26
                         /commoncode/getCommonCode.ajax        → getCommonCode(…)
                         /commoncode/getAllBondGbn.ajax        → getAllBondGbn(…)
                         /commoncode/getAllBondType.ajax       → getAllBondType(…)
                         /commoncode/getCommonCodeGroup.ajax   → getCommonCodeGroup(…)
                         /commoncode/getProductgroupKind2.ajax → getProductgroupKind2(…)
```

**좋은 점**
- 열 구성과 모양이 이전 화면과 같습니다. 달라지는 것은 합쳐진 행에 붙는 작은 표시 하나뿐입니다.
- 같은 내용이 반복되지 않습니다. testApp의 `getCommonCodeService()`는 46행이 30행 안팎으로 줄어듭니다. JSP 4개 × 5행이 4행이 됩니다.
- 왜 이어졌는지 알고 싶을 때만 펼쳐서 보면 됩니다.
- 위 제목의 "· N건"은 합친 뒤의 행 수로 바꾸고, 원래 관계 수는 괄호로 붙입니다. 예: `30건 (관계 46)`.

**고칠 곳**
- dstone-boot `impact.js` 하나만 고칩니다. 지난번에 추가한 API 필드(`to`, `requestPath`, `via`)를 그대로 써서 화면에서 묶기만 하면 됩니다.
- 지난번에 추가한 "→ 거쳐 가는 곳" 열은 없앱니다.
- dstone-knowledge는 바꾸지 않습니다.

## 다른 방법 (권하지 않음)

| 방법 | 아쉬운 점 |
|---|---|
| 합치기만 하고 펼쳐 보기는 없음 (이전 화면 + 개수만) | 화면은 가장 깔끔하지만, JSP가 어느 주소 때문에 이어졌는지 다시 볼 수 없습니다. |
| 합친 행에 마우스를 올리면 툴팁으로 목록 표시 | 복사할 수 없고, 모바일에서는 보기 어렵습니다. |
| 거리별로 접히는 트리 형태 | 구조는 가장 정확하지만, 지금 표와 전혀 달라서 익숙해지는 데 시간이 걸립니다. |

제안한 방식(합치기 + 펼쳐 보기)으로 진행할까요?

---

## 질문

그래 그게 낫겠다. 진행하고 올려줘.

---

## 답변

문법 검사는 통과했습니다. 저장해 둔 실제 API 응답으로 묶음 결과를 확인합니다.

---

## 답변

묶음은 46건에서 30행으로 의도대로 줄었습니다. 실제 CSS를 입혀 표를 그린 뒤 헤드리스 크롬으로 화면을 찍어 확인합니다.

---

## 답변

영향도 화면의 '부르는 쪽' 표를 제안한 방식으로 바꿔 커밋(`b3d5aaa7`)하고 `origin/main`에 푸시했습니다.

**바뀐 화면**
- **열 구성**: 지난번에 추가한 "→ 거쳐 가는 곳" 열을 없애서 이전 화면과 같습니다.
- **행 합치기**: 거리, 관계, 신뢰도, 부르는 쪽, 적힌 파일, 줄이 모두 같은 행은 한 행으로 합칩니다. 합친 행에만 부르는 쪽 이름 아래에 `▸ 주소 5개 (불러온 스크립트)`가 붙습니다. 이걸 누르면 `/commoncode/getCommonCode.ajax → CommonCodeController#getCommonCode(…)` 같은 목록이 펼쳐집니다. Java 호출이 합쳐진 경우에는 "대상 N개"로 나옵니다.
- **제목**: 행이 합쳐졌을 때만 원래 관계 수를 함께 보여 줍니다. 예: `30건 (관계 46)`.

**확인한 것**
- testApp 리비전 125의 `getCommonCodeService()` 응답을 넣어 보니 46건이 30행으로 줄었습니다. JSP 4개는 각각 한 행이 됐고, `commoncode.jsp`처럼 줄이 다른 행은 그대로 남았습니다.
- 실제 CSS(`console.css`, `knowledge.css`)를 입힌 표를 헤드리스 크롬으로 찍어 접힘과 펼침 모양을 확인했습니다.
- 로그인한 dstone-boot 화면에서 직접 보지는 않았습니다.

**고친 파일**
- `impact.js`, `knowledge.css`: 화면 동작과 모양
- `docs/11.dstone-knowledge.md`, `.claude/docs/knowledge-ui.md`: 문서

dstone-knowledge는 이번에 바뀌지 않았습니다. 다만 화면이 지난 커밋(`83c06b25`)에서 추가한 API 필드를 쓰므로, dstone-knowledge도 그 커밋이 들어간 버전이어야 펼친 목록이 나옵니다. 이전 버전이면 목록에 메소드 이름 대신 ID가 나옵니다. dstone-boot는 다시 빌드해서 배포해야 반영됩니다.

---

## 질문

<pasted_content id="01a2">
dstone-boot>knowledge 의 영향도 · 호출 관계 에서

화면 하단 영향도 분석 을 보면
어떤 항목에 대한 영향도 분석인지 직관적으로 드러나지 않아.
개선 계획을 말해줘.
</pasted_content id="01a2">

---

## 답변

영향도 분석 부분의 화면 코드와 JSP를 먼저 보겠습니다.

---

## 답변

화면 코드는 확인했습니다. API가 돌려주는 대상 정보(`result.target`)에 무엇이 들어 있는지도 보겠습니다.

---

## 답변

영향도 분석은 무엇을 분석한 결과인지가 화면에 거의 드러나지 않습니다. 대상을 알려 주는 곳이 결과 위의 회색 상태 메시지 한 줄뿐이고, 그마저 내부 코드로 나옵니다. 아래 계획으로 고치는 것을 제안합니다.

## 지금 왜 안 드러나나

| 문제 | 지금 모습 | 원인 |
|---|---|---|
| 1. 대상이 상태 메시지로만 나옴 | `대상: METHOD c807f333bac77d06…` | `impact.js:52`가 `result.target.kind`와 `name`을 회색 메시지 줄(`kn-message`)에 그대로 찍습니다. "분석 중..."이 나왔던 바로 그 자리라 결과 제목처럼 보이지 않습니다. |
| 2. 메소드면 해시가 나옴 | 대상 입력칸과 메시지에 40자리 `methodId` | 호출 관계 표의 "영향도" 단추가 `runImpact("methodId", method.methodId)`로 실행합니다. API도 이름 없이 ID만 돌려줍니다(`ImpactService.java:109`). |
| 3. 종류가 영어 코드 | `TABLE`, `STATEMENT`, `TYPE`, `METHOD` | 화면 문구로 바꾸지 않습니다. |
| 4. 조건이 안 보임 | 깊이, 읽기/쓰기, 가능한 구현 포함 여부가 결과 어디에도 없음 | 입력칸에만 있고, 입력칸 값을 바꾸면 이미 나온 결과와 맞지 않게 됩니다. |
| 5. 결과 표에도 대상이 없음 | 요약 카드는 "직접 건드리는 메소드 3"뿐이고, 무엇을 직접 건드리는지는 없음 | 대상이 결과 영역에 한 번도 나오지 않습니다. |
| 6. 위 패널에서 넘어올 때 연결이 약함 | 위의 "호출 관계"에서 영향도 단추를 누르면 아래 패널의 메시지 줄로 스크롤만 됨 | 어떤 메소드에서 넘어왔는지 보여 주지 않습니다. |

## 개선 계획

### ① 결과 맨 위에 "대상 머리글"을 둡니다 (가장 효과가 큼)

요약 카드 위에 대상 블록을 하나 둡니다. 상태 메시지 줄은 "분석 중..."과 오류만 보여 주도록 되돌립니다.

```
┌──────────────────────────────────────────────────────────────────────┐
│ [메소드]  BaseController#getCommonCodeService()                       │
│           kr.co.gnx.base.BaseController · src/main/java/…/BaseController.java:88 · CONTROLLER │
│           리비전 20261113-135641 · 깊이 5 · 인터페이스 경유 호출 포함                  │
└──────────────────────────────────────────────────────────────────────┘
  직접 건드리는 메소드 10   닿는 메소드 28   진입점 10   화면 5
```

종류별 표시는 다음과 같습니다.

| 종류 | 큰 글씨 | 작은 글씨 |
|---|---|---|
| 테이블 | `TB_COMMON_CODE` | 테이블 한글명(있으면) · 읽기/쓰기 중 무엇을 봤는지 |
| SQL | `CommonCode.selectList` | SELECT/INSERT 등 · 매퍼 파일:줄 |
| 타입 | `CommonCodeController` | 전체 이름 · 계층 · 파일 |
| 메소드 | `BaseController#getCommonCodeService()` | 전체 클래스 이름 · 파일:줄 · 계층 |

종류 배지(`[메소드]`)는 한글로 보여 줍니다. 요약 카드 문구도 대상이 테이블이면 "이 테이블을 직접 쓰는 메소드"처럼 대상에 맞게 바꿉니다.

### ② API가 사람이 읽을 대상 정보를 돌려줍니다 (dstone-knowledge)

`ImpactService`의 `target`에 이름 외의 정보를 덧붙입니다. 기존 필드는 그대로 두고 더하기만 하므로, ai-engine의 `KnowledgeTool` 같은 다른 사용처에는 영향이 없습니다.
- **메소드:** 클래스 전체 이름, 시그니처, 파일, 줄, 계층
- **타입:** 계층, 파일
- **SQL:** 종류(SELECT 등), 매퍼 파일, 줄
- **테이블:** 한글명(기존 테이블 한글명 우선순위 그대로), 실제로 본 읽기/쓰기 범위

조건(깊이, 가능한 구현 포함 여부)은 이미 응답에 있으니 그대로 씁니다.

### ③ 입력칸에 해시가 보이지 않게 합니다

호출 관계에서 넘어오면 대상 칸에 `BaseController#getCommonCodeService()`를 보여 주고, 실제 `methodId`는 따로 들고 있습니다. 사용자가 대상 칸을 고치면 들고 있던 ID를 버리고 입력한 글자로 찾습니다. 대상 종류 목록의 "메소드 (methodId)"도 "메소드"로 바꿉니다.

### ④ 넘어올 때 머리글로 스크롤합니다

호출 관계의 "영향도" 단추나 테이블 이름을 누르면, 메시지 줄이 아니라 ①의 대상 머리글로 스크롤합니다. 머리글이 잠깐 강조돼서 무엇이 바뀌었는지 보이게 합니다.

## 고칠 곳과 검증

| 모듈 | 파일 |
|---|---|
| dstone-knowledge | `ImpactService.java`. 대상 정보 조회가 필요하면 `RelationDao.xml`에 SQL 한두 개 추가 |
| dstone-boot | `impact.js`, `impact.jsp`(종류 문구), `knowledge.css`(머리글 모양) |
| 문서 | `docs/11.dstone-knowledge.md` 4.1절, `.claude/docs/knowledge-ui.md` |

**검증:**
- testApp 리비전 125의 네 종류 대상(테이블, SQL, 타입, 메소드)으로 API 응답을 확인합니다.
- 시험 인스턴스에서 실제 CSS를 입혀 화면을 찍어, 넓은 화면과 좁은 화면(모바일)에서 머리글이 어떻게 보이는지 확인합니다.

①~④를 모두 할지, ①과 ②만 먼저 할지 정해 주시면 진행하겠습니다. 저는 넷 다 하는 것을 권합니다. ③과 ④는 작은 수정이고, ①만 하면 입력칸의 해시가 여전히 거슬립니다.
