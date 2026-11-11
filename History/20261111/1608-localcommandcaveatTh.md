# localcommandcaveatTh

> 생성일시: 2026-11-11 16:08

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="9ebd">
/docs/11.dstone-knowledge.md 의 아래 내용중 질문에 대답해줘.

<단락위치>
처리기별 상세설명 > 2. entry: 진입점 (EntryFinder)

<내용>
2-1. 진입점 종류. entry: 아래 키가 analysis_endpoint.endpoint_type이 된다. 영문 대문자·숫자·_ 20자 이내면 새로 지어도 된다.

종류    뜻    만드는 곳
HTTP    주소로 들어오는 요청    규칙(EntryFinder), StrutsEntries
SCHEDULED    정해진 시각에 도는 메소드. path에 주기    규칙
LISTENER    메시지·이벤트를 받는 메소드. path에 토픽·큐 이름    규칙
THREAD    다른 스레드에서 도는 메소드(run(), call())    규칙
TRANSACTION    거래 ID로 불리는 메소드. path에 거래 ID    규칙(설정 XML), ScreenLinker
SERVLET    web.xml·@WebServlet의 서블릿    ServletEntries
MAIN    public static void main(String[])    코드
JSP    주소로 바로 열 수 있는 JSP(WEB-INF 밖)    코드

<질문>
내용중에 종류별로 로직을 처리하는 부분이 어디인지 확인해줘.
</pasted_content id="9ebd">

---

## 답변

Need EntryFinder.run body and SemanticDao methods + SQL ids.

---

## 답변

핵심부터 말하면, 코드는 **진입점 종류 이름(SCHEDULED, LISTENER…)으로 분기하지 않습니다.** 규칙 파일에 적은 **모양**으로 분기합니다. 종류 이름은 `endpoint_type` 컬럼에 그대로 들어가는 값일 뿐입니다. 그래서 문서에 "새로 지어도 된다"고 적혀 있습니다. 예외는 `HTTP` 하나이고, `MAIN`·`JSP`는 규칙 없이 코드에 고정돼 있습니다.

## 1. 규칙 파일을 읽고 모양별로 나누는 곳

`analysis/pass08/semantic/rule/RuleFileParser.java:248` `readEntries()`

| 규칙에 적은 모양 | 담기는 곳 | 해당 종류 |
|---|---|---|
| `"@애노테이션(... = KEY)"` | `Rule.AnnotationEntry` | SCHEDULED, LISTENER |
| `상위타입#메소드()` | `Rule.ExtendsEntry` | THREAD |
| `{ file:, xml: }` | `Rule.XmlEntry` | TRANSACTION(설정 XML) |
| `HTTP:` 아래 `class:`/`GET:` 같은 키 | `Rule.HttpEntry` | HTTP (이름으로 분기하는 유일한 경우, `:262`) |

## 2. 종류별 처리 위치

`EntryFinder.run()`(`analysis/pass08/semantic/entry/EntryFinder.java:85`)이 아래를 차례로 부릅니다.

| 종류 | 처리 위치 | 실제 SQL (`sqlmap/analysis/SemanticDao.xml`) |
|---|---|---|
| **HTTP** (규칙) | `EntryFinder.insertHttpEntries()` `:113`, 주소 조립은 `HttpEndpointBuilder` | Java에서 행을 만들고 `insertEndpoint` `:285`로 넣음 (`:169`) |
| **HTTP** (Struts) | `StrutsEntries.addAction()` `:113` → `addEndpoint()` `:254` | `insertEndpoint` |
| **SCHEDULED / LISTENER** | `EntryFinder.run()` 루프 → `SemanticDao.insertAnnotationEntries()` `:93` | `insertAnnotationEntries` `:173` |
| **THREAD** | `EntryFinder.insertExtendsEntries()` `:223` → `SemanticDao.insertExtendsEntries()` `:110` | `insertExtendsEntries` `:322` |
| **TRANSACTION** (설정 XML) | `EntryFinder.insertXmlEntries()` `:245` → `addXmlEntry()` `:283` → `insertXmlEndpoint()` `:316`, XML 읽기는 `XmlEndpointReader` | `insertEndpoint` |
| **TRANSACTION** (화면) | `screen/ScreenLinker.insertEndpoints()` `:246` | `insertCallRuleEndpoints` `:575`, `insertTransactionEndpoint` `:602` |
| **SERVLET** | `ServletEntries`: web.xml은 `addServletEndpoints()` `:94`, `@WebServlet`은 `SemanticDao.insertWebServletEndpoints()` | `insertEndpoint`, `insertWebServletEndpoints` `:237` |
| **MAIN** | `SemanticDao.insertMainEndpoints()` `:250` | `insertMainEndpoints` `:305`, `'MAIN'` 고정 |
| **JSP** | `SemanticDao.insertJspEndpoints()` `:254` | `insertJspEndpoints` `:355`, `'JSP'` 고정 |

## 3. 정리

- SCHEDULED와 LISTENER는 같은 코드를 탑니다. 둘의 차이는 규칙 파일(`conf/rules/common/spring.yml:24`, `:26`)에 적은 애노테이션뿐입니다.
- THREAD 규칙은 `conf/rules/common/java.yml:8`에 있습니다. 같은 모양으로 `BATCH_JOB: [some.Job#execute()]`처럼 새 종류를 적어도 `insertExtendsEntries`가 그대로 처리합니다.
- 종류 이름에 따라 동작이 달라지는 곳은 분석 쪽이 아니라 결과를 쓰는 쪽입니다. 문서를 만들 때 종류별로 설명을 붙이는 `pass09/document/CodeDocumentBuilder.java:623`~`:639`, 그리고 JSP·SERVLET을 진단에서 빼는 `DiagnosticService.java:236`이 그 예입니다.

---

## 질문

<pasted_content id="9ebd">
/docs/11.dstone-knowledge.md 에 아래의 작업을 해줘. 그리고 올려줘.

<작업대상 단락>
2-2. 규칙을 읽어 처리하는 순서

<작업 내용>
상기 내용을 작업대상 단락에 보충.
</pasted_content id="9ebd">

---

## 답변

`docs/11.dstone-knowledge.md`의 2-2절(`entry:` 진입점)에 내용을 보충하고 `main`에 커밋해 푸시했습니다(`1e1925ad`).

추가한 내용은 세 가지입니다.

1. **처리 방법을 정하는 기준**: 기존 순서 목록 위에 한 단락을 넣었습니다. 처리 방법은 종류 이름이 아니라 규칙 값의 모양으로 정해지고, `RuleFileParser.readEntries()`가 모양에 따라 규칙을 네 가지로 나눈다는 설명입니다. 그래서 `SCHEDULED`와 `LISTENER`는 같은 코드를 타고, 새 종류를 지어도 모양만 맞으면 코드를 고칠 필요가 없습니다. 이름으로 처리가 갈리는 종류는 `HTTP` 하나뿐입니다.
2. **단계별 처리 코드와 SQL 표**: 맨 아래에 표를 넣었습니다. `EntryFinder`가 처리하는 다섯 단계에 더해, `EntryFinder` 밖에서 진입점을 만드는 Struts(`StrutsEntries`), `SERVLET`(`ServletEntries`), 화면 `TRANSACTION`(`ScreenLinker`)도 담았습니다.
3. **결과를 쓰는 쪽**: 분석이 끝난 뒤 종류 이름에 따라 동작이 갈리는 곳으로 `CodeDocumentBuilder`와 `DiagnosticService`를 짧게 적었습니다.

코드를 고치면 줄 번호가 금방 어긋나므로 문서에는 줄 번호 없이 클래스와 메소드 이름만 적었습니다.
