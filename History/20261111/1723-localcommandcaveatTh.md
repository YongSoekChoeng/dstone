# localcommandcaveatTh

> 생성일시: 2026-11-11 17:23

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="3099">
/docs/11.dstone-knowledge.md 에 아래의 작업을 해줘. 그리고 올려줘.

<작업대상 단락>
### 2-8. SEMANTIC — 이것은 무엇인가

<작업 내용>
#### 처리기 안의 호출 순서
#### 처리기별 상세설명
이 두 단락을 합쳤으면 좋겠어.

규칙(rule)을 사용하는 것과 사용하지 않는 것 포함해서 일괄적으로 17개의 표를 각자 생성하는 것은 어떨까?
그리고 #### 처리기와 순서 의 링크는 처리기와 17개의 소제목과 링크를 연결하면 어떨까 싶어.
</pasted_content id="3099">

---

## 답변

기존 2-8절을 다 읽었습니다. 이제 "호출 순서"와 "상세설명"을 처리기 17개 소제목으로 합친 새 본문을 쓰고 있습니다.

---

## 답변

문서 정리를 마쳤습니다. 커밋하고 푸시하겠습니다.

---

## 답변

Remote 커밋은 Java 파일 두 개만 바꿔서 문서와 겹치지 않습니다. rebase 후 다시 푸시합니다.

---

## 답변

`docs/11.dstone-knowledge.md` 2-8절을 고쳐서 커밋(`3314bc9e`)하고 푸시했습니다.

**바뀐 점**
- **두 절을 합쳤습니다.** "처리기 안의 호출 순서"와 "처리기별 상세설명"을 "처리기별 설명" 한 절로 묶었습니다. 그 아래에 `##### 1. ServletEntries (서블릿 입구)`부터 `##### 17. TableDictionaryPlugin (테이블 사전)`까지 처리기마다 소제목이 하나씩 있습니다.
- **처리기마다 같은 모양으로 적었습니다.** 규칙을 쓰는 처리기와 쓰지 않는 처리기 모두 아래 순서를 따릅니다.
  1. 요약 표: 규칙 절, 읽는 것, 만드는 것, 순서의 까닭
  2. 호출 순서
  3. 규칙을 쓰는 처리기만: 처리 방식, 지금 규칙 파일에 적힌 것, 저장하는 테이블과 컬럼, 패턴을 더할 때
- **"처리기와 순서" 표의 처리기 이름을 각 소제목에 링크했습니다.** 규칙 절 칸은 링크 없이 글자만 남겼습니다.
- **처리기 두 개가 함께 쓰던 설명은 나눴습니다.**
  - `jpa:`: 키 표는 6번 `JpaQueryReader`에 두고, 9번 `JavaSqlLinker`는 `jpql` 처리와 저장 내용만 적은 뒤 6번으로 링크합니다.
  - `screen:`: 항목 종류 표는 12번 `ScreenLinker`에 두고, 13번 `ScreenMenuReader`는 `menu` 처리만 적은 뒤 12번으로 링크합니다.
- **"8. 규칙 없이 도는 처리기" 표는 없앴습니다.** 그 내용은 처리기별 요약 표로 옮겼습니다.
- **요약 표의 "순서의 까닭"에 새로 한 줄을 넣었습니다.** 원래 표에서 비어 있던 7번 `MapperLinker` 칸에 "SQL 관계를 지우므로 8·9번보다 먼저"라고 적었습니다. 기존 jpa 설명에 나온 내용입니다.

푸시하기 전에 원격에 새 커밋 `499fe8b8`이 있어서 rebase로 합쳤습니다. 이 커밋은 `SemanticPass.java`에 194줄을 더하고 `ServletEntries.java`를 고쳤는데, 이번 문서 정리에는 반영하지 않았습니다. 그래서 2-8절의 실행 틀이나 1번 처리기 호출 순서가 바뀐 코드와 어긋날 수 있습니다. 원하시면 확인해서 맞추겠습니다.

---

## 질문

<pasted_content id="3099">
/docs/11.dstone-knowledge.md 에 아래의 작업을 해줘. 그리고 올려줘.

<작업대상 단락>
#### 처리기와 순서

<작업 내용>
규칙(rule)이 없더라도 
**저장하는 테이블과 컬럼.**
단락을 추가해줘.
필요하다면 **패턴을 더할 때.** 단락도 추가해줘.

</pasted_content id="3099">

---

## 답변

추가한 내용을 확인했습니다. 커밋하고 푸시합니다.

---

## 답변

규칙이 없는 처리기 8개에도 "저장하는 테이블과 컬럼" 단락을 넣고, 커밋(`0c21d888`)해서 푸시했습니다. 이 단락들은 "처리기와 순서" 표 아래 "처리기별 설명"의 각 처리기 소제목 안에 들어 있습니다. 이제 17개 처리기 모두 이 단락이 있습니다.

| # | 처리기 | 저장하는 테이블과 컬럼 | 패턴을 더할 때 |
|---|---|---|---|
| 1 | `ServletEntries` | `analysis_endpoint`(`SERVLET`), `analysis_resource`(매핑·필터·리스너) | `web.xml`에 없는 입구는 4번 `entry:`의 상속 입구로 적는다 |
| 2 | `StrutsEntries` | `analysis_endpoint`(`HTTP`), `analysis_symbol`(`CONTROLLER`), `RENDERS` | Struts가 아닌 프레임워크의 설정 XML은 4번 `entry:`의 설정 XML 입구로 적는다 |
| 7 | `MapperLinker` | SQL 관계 삭제, 다시 넣는 `EXECUTES_SQL`·`READS_TABLE`·`WRITES_TABLE`, `analysis_mapper.method_id` 초기화 | SQL 이름을 인자로 넘기는 방식은 8번 `sql:`로 적는다 |
| 11 | `JspLinker` | `_jspService` 채우기, `REQUESTS`, `INCLUDES`, `SCREEN_NAME` | 넣지 않음 |
| 14 | `SourceNamePlugin` | `SOURCE_TABLE_NAME`, `SOURCE_NAME` | 넣지 않음 |
| 15 | `MethodTitlePlugin` | `METHOD_TITLE`, `METHOD_TITLE_SKIP`, `SEMANTIC_STATUS` | 주석 양식이 다르면 `common.util.MethodTitle`을 고친다 |
| 16 | `ScreenDictionaryPlugin` | `DICT_SCREEN_NAME`, `SEMANTIC_STATUS` | `dstone.knowledge.dictionary.screen-file.{프로젝트ID}`에 CSV를 적는다 |
| 17 | `TableDictionaryPlugin` | `DICT_TABLE_NAME`, `DICT_COLUMN_NAME`, `SEMANTIC_STATUS` | `dstone.knowledge.dictionary.file.{프로젝트ID}`에 CSV를 적는다 |

- **저장 내용 출처:** 같은 절의 "처리기별 테이블"에서 옮겼습니다.
- **15~17번 패턴 단락:** 각 처리기의 소스(`MethodTitle`)와 `conf/application.yml` 주석, 설정 키를 확인해서 썼습니다.
- **11번과 14번:** 규칙으로 대신하거나 설정으로 넓힐 방법이 없어서 "패턴을 더할 때" 단락을 넣지 않았습니다.
- **소개 문단:** 처리기별 설명 앞의 소개 문단도 지금 구성에 맞게 고쳤습니다.

---

## 질문

EntryFinder.insertXmlEntries() 가 커버하는게 진입점 종류 중에 어떤 종류들인가?

---

## 답변

`insertXmlEntries()`가 맡는 진입점 종류는 코드에 정해져 있지 않습니다. 진입점 종류가 아니라 규칙 값의 모양, 곧 `{ file, xml }`으로 적었는지를 보고 이 메소드로 보냅니다.

**근거 코드**
- `RuleFileParser.readEntries()`(248행 근처)는 `entry:` 아래 키를 하나씩 읽습니다. 값이 매핑이고 `xml` 키가 있으면 종류와 상관없이 `readXmlEntry()`로 보내 `xmlEntries`에 담습니다.
- 종류 이름은 `Rule.XmlEntry.endpointType`에 그대로 실립니다. `EntryFinder.insertXmlEndpoint()`(329행)는 이 값을 `endpointType`에 넣기만 합니다.

**그래서 이렇게 됩니다**
- **새 종류:** 영문 대문자와 `_`로 20자 이내인 이름이면 됩니다. `TRANSACTION`, `BATCH_JOB`처럼 새로 지은 종류도 `{ file, xml }`으로 적으면 이 메소드가 처리합니다.
- **`HTTP`:** `HTTP` 아래에도 `{ file, xml }`을 적을 수 있습니다. 이때 `verb`를 생략하면 `ALL`이 됩니다(347행). 다른 종류는 `verb`를 생략하면 비워 둡니다.
- **기존 종류 이름:** 파서는 `SERVLET`, `MAIN`, `JSP` 같은 이름을 막지 않습니다. 다만 이 종류들은 다른 처리기가 따로 만들기 때문에, 같은 이름으로 XML 규칙을 적으면 섞여서 헷갈리기 쉽습니다.

**지금 실제로 처리하는 것은 없습니다.** 현재 `conf/rules`에는 `{ file, xml }` 모양의 `entry:` 규칙이 하나도 없습니다. 문서 예시는 `TRANSACTION`을 쓰지만, 지금 분석에서 `TRANSACTION` 진입점은 화면 쪽 `ScreenLinker`가 만듭니다.

`docs/11` 2-8절의 진입점 종류 표에는 `TRANSACTION`의 만드는 곳이 "규칙(설정 XML)"으로만 적혀 있어서, 설정 XML 입구가 `TRANSACTION` 전용처럼 읽힐 수 있습니다. 원하시면 "모든 종류에 쓸 수 있다"는 설명을 그 표에 덧붙이겠습니다.
