# knowledgegraphgraph_

> 생성일시: 2026-10-24 16:03

## 질문

knowledge/graph/graph 에서 테이블 노드를 클릭하면 우측에 보여지는 테이블명(한글명)이 많이 수집되지 않은 것 같아. 
예를 들어서 JC_BENEFIT 의 경우 D-01_테이블목록_서브ERD.md 에는 '보험금' 으로 수집되어 있는데 왜 knowledge 에는 수집되지 않아서 '한글명 없음'으로 보이는가?

---

## 답변

먼저 그 문서가 어디 있는지, 그리고 knowledge가 한글명을 어디서 찾는지부터 확인하겠습니다.

---

## 답변

문서에 따르면 테이블 한글명은 "SQL 주석과 약어 규칙으로 붙였다"고 되어 있습니다. 실제 소스에서 `JC_BENEFIT` 옆에 한글 주석이 있는지 보겠습니다.

---

## 답변

`BEFORE_TABLE_COMMENT` 패턴은 `FROM|JOIN|INTO|UPDATE` 바로 뒤의 테이블만 봅니다. 실제 주석 위치 주변과 DB에 저장된 값을 확인하겠습니다.

---

## 답변

`JC_BENEFIT`이 '한글명 없음'으로 나오는 이유는 두 가지입니다. knowledge의 SQL 주석 수집 규칙이 이 프로젝트에서 흔한 SQL 모양을 놓치고 있고, ERD 문서는 knowledge와 다른 방법으로 이름을 붙였습니다.

## 1. 테이블 주석을 놓치는 모양이 있습니다 (knowledge 버그)

소스에는 `JC_BENEFIT`의 한글 주석이 실제로 있습니다.

```sql
-- JCPY0100D.xml:889-890
FROM
     JC_BENEFIT A       /* 보험금       */
-- JCST0100D.xml:863-864
FROM JC_ACDT_AGMT S1    /* 클레임_사고계약 */
    ,JC_BENEFIT S2      /* 클레임_보험금   */
```

주석을 읽는 곳은 `dstone-knowledge/src/main/java/net/dstone/knowledge/semantic/SqlTableExtractor.java:300` `commentsOf()`입니다. 이 메서드는 주석 앞의 텍스트를 **같은 줄에서만** 잘라 냅니다. 그리고 테이블 주석으로 인정하려면 그 텍스트가 `BEFORE_TABLE_COMMENT`(263행), 곧 `FROM|JOIN|INTO|UPDATE 테이블 [별칭]` 모양이어야 합니다. 그래서 다음 두 경우가 모두 버려집니다.

- `FROM`을 앞줄에 쓰고 테이블과 주석은 다음 줄에 쓴 경우 (위 890행)
- 쉼표 조인에서 두 번째 이후 테이블 (`,JC_BENEFIT S2 /* … */`)

HI-Japan 최신 리비전(54)의 수치입니다.
- 테이블 231개 중 한글명이 붙은 테이블은 36개입니다.
- 소스를 따로 훑어 보니 테이블 주석이 어딘가에 있는 테이블은 약 45개였습니다. 그중 21개는 주석이 위 두 모양으로만 달려 있습니다(정규식으로 대략 센 값).

## 2. ERD 문서는 knowledge와 다른 방법으로 이름을 붙였습니다

`D-01_테이블목록_서브ERD.md`에는 "테이블 한글명은 SQL 주석과 **약어 규칙**으로 붙였다"고 적혀 있습니다. 주석이 없는 테이블은 이름의 약어를 보고 추정해서 채웠다는 뜻입니다. 그 결과 CSV의 테이블은 모두 한글명을 갖고 있습니다.

knowledge는 "계산할 수 있는 것만 쓰고 추측하지 않는다"는 원칙을 따릅니다. 우선순위는 DB 코멘트 → DDL → SQL 주석 → Java 소스입니다. 약어로 추정하는 단계는 없습니다. 이 프로젝트는 DDL도 확보되지 않았습니다(문서에도 "DDL 미확보"로 나옵니다). 그래서 1번을 고쳐도 한글명이 붙는 테이블은 많아야 50~60개 정도로 예상합니다. 나머지 170여 개는 계속 '한글명 없음'으로 남습니다.

## 고치는 방법 제안

1. **`commentsOf()` 수정 (권장, 버그)**
   - 주석 앞 줄이 테이블 이름(+별칭)만으로 되어 있을 때 바로 앞의 SQL 키워드가 `FROM`/`JOIN`이면 테이블 주석으로 봅니다.
   - 쉼표 조인도 FROM 절 안이면 테이블 주석으로 봅니다.
   - 단위 테스트를 추가하고, HI-Japan에 `rerunFrom: SEMANTIC`을 다시 돌리면 반영됩니다.
2. **(선택) 확인된 사전 파일 가져오기**
   - `D-01_테이블목록.csv` 같은 테이블·컬럼 사전을 프로젝트 설정으로 읽게 합니다.
   - 출처 태그는 `DICT` 등으로 따로 붙여 추정값임을 보이게 합니다.
   - 다만 이 값에는 사람이나 LLM이 추정한 이름이 섞여 있습니다. 넣을지와 우선순위를 어디에 둘지는 정해 주셔야 합니다.

1번부터 진행할까요?

---

## 질문

<task-notification>
<task-id>bbxu7jgfo</task-id>
<tool-use-id>toolu_013JgF3u7puA8oFVPiVPARsi</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/a43bff25-43b7-4e43-937d-55ca9de86f05/tasks/bbxu7jgfo.output</output-file>
<status>completed</status>
<summary>Background command "cd /app; find / -name "D-01_테이블목록*" 2&gt;/dev/null | grep -v ^/proc | head; " completed (exit code 0)</summary>
</task-notification>

---

## 답변

처음에 띄워 둔 파일 찾기 작업은 이제 끝났습니다. 그사이 다른 방법으로 이미 `D-01_테이블목록_서브ERD.md`를 찾아서 분석했기 때문에 결과는 바뀌지 않습니다.

1번(`commentsOf()` 수정)을 진행할지 알려 주세요.

---

## 질문

1번과 2번을 진행하고 올려줘.

---

## 답변

1번(SQL 주석 인식 보완)과 2번(사전 파일 읽기)을 진행하겠습니다. 먼저 관련 코드를 읽겠습니다.

---

## 답변

Now the comment part of `SqlTableExtractor` and its existing tests.

---

## 답변

구현을 시작합니다. 먼저 1번 `SqlTableExtractor`입니다.

---

## 답변

이제 테스트를 추가합니다.

---

## 답변

동작은 맞고, 같은 테이블은 먼저 나온 주석을 쓰는 기존 규칙대로 나왔습니다. 기대값을 고칩니다.

---

## 답변

1번 테스트가 통과했습니다. 이제 2번 사전 파일 리더를 만듭니다.

---

## 답변

`headerKey()`를 너무 복잡하게 썼습니다. 정리하겠습니다.

---

## 답변

파일 첫머리 BOM을 `parse()`에서는 떼지 않아서 생긴 문제입니다. `parse()`에서도 떼도록 고칩니다.

---

## 답변

`Write` 과정에서 `\uFEFF`가 실제 BOM 글자로 바뀌어 들어갔습니다. 소스에서는 보이지 않는 글자이므로 이스케이프로 되돌립니다.

---

## 답변

테스트가 통과했습니다. 이제 `TableDictionaryService`에 6번째 출처(`DICT`)를 붙입니다.

---

## 답변

설정 파일의 한글명 안내가 출처 3개 기준으로 낡아 있어서, 안내를 고치고 HI-Japan 사전 파일을 등록합니다.

---

## 답변

Now the screen side (`graph.js`).

---

## 답변

Now the docs. Find where docs/11 describes Korean names.

---

## 답변

진행 중인 Job도, 임베딩 대기도 없습니다. 4081 인스턴스를 내리고 빌드한 뒤 다시 띄웁니다.

---

## 답변

3초 만에 응답해서 이전 프로세스가 내려가지 않았을 가능성이 있습니다. 확인하겠습니다.

---

## 답변

새 프로세스로 떴습니다. 먼저 재분석 없이 사전 파일이 적용되는지 확인합니다.

---

## 답변

SQL 주석 쪽도 같은 문제가 있는지 보겠습니다. 주석의 테이블 이름은 `lastPart()`로 스키마를 떼는데, 관계에는 스키마가 붙은 채 저장됩니다.

---

## 답변

모듈 전체 테스트 113개가 통과했습니다. 다시 빌드하고 띄운 뒤 SEMANTIC부터 재분석해서 다시 셉니다.

---

## 답변

HI-Japan 테이블 231개 중 225개에 한글명이 붙었습니다. 화면 스크립트는 문법 검사만 하겠습니다.

---

## 답변

Now update the docs (`docs/11.dstone-knowledge.md` and CLAUDE.md).

---

## 답변

문서까지 고쳤습니다. 커밋하고 올리겠습니다.

---

## 답변

1번과 2번을 모두 반영해 커밋 `4bcb530e`로 `main`에 올렸습니다. 이제 HI-Japan의 `JC_BENEFIT`은 '한글명 없음' 대신 **보험금**(출처: SQL 주석)으로 나옵니다.

**결과 (HI-Japan 최신 리비전 54, 테이블 231개)**

| | 전 | 후 |
|---|---|---|
| SQL 주석에서 찾은 한글명 | 36 | 58 |
| 사전 파일에서 찾은 한글명 | – | 167 |
| 한글명 없음 | 195 | 6 |

남은 6개(`HJSMIG.*` 4개, `DEMO_USER`, `JA_OP_EXPEV_M`)는 SQL 주석도 없고 사전 파일에도 없는 테이블입니다.

**1. SQL 주석 인식 확대** (`SqlTableExtractor`)
- `FROM`을 앞줄에 쓴 경우와 쉼표로 늘어놓은 두 번째 이후 테이블의 주석도 테이블 한글명으로 읽습니다. 그 위치가 `FROM`/`JOIN`/`INTO`/`UPDATE` 절 안일 때만 읽으므로 서브쿼리 안의 목록도 되고, 조회 목록의 별칭 주석은 테이블로 잡히지 않습니다.
- 작업 중에 버그를 하나 더 찾아 고쳤습니다. `DPHJSDBA.JZ_EMP_INFO e /* 사원정보 */`처럼 스키마를 붙인 테이블은 주석이 테이블과 짝을 짓지 못하고 버려지고 있었습니다.

**2. 사전 파일(CSV) 읽기** (`DictionaryFileReader`, 새 파일)
- 설정 키는 `dstone.knowledge.dictionary.file.{프로젝트ID}`입니다. `conf/application.yml`에 HI-Japan과 HI-Japan-batch용으로 `D-01_테이블목록.csv`와 `D-01_테이블컬럼사전.csv`를 등록했습니다. 상대 경로는 프로젝트 소스 폴더 기준이라 Windows 쪽 인스턴스에서도 같은 설정이 맞습니다.
- 열은 머리글 이름(`테이블`, `한글명`, `컬럼`, `컬럼설명` 등)으로 찾습니다. 파일은 화면에서 상세를 열 때 읽으므로, CSV를 고치면 재분석이나 재기동 없이 바로 반영됩니다.
- **우선순위는 제가 맨 뒤로 정했습니다.** 순서는 DB → DDL → DDL 주석 → SQL 주석 → 소스 → 사전입니다. CSV에 약어를 보고 짐작한 이름이 섞여 있기 때문입니다. 사전을 더 앞에 두고 싶으시면 말씀해 주세요.
- 화면에는 출처가 "사전"으로 표시되고, 마우스를 올리면 파일명과 줄 번호가 나옵니다.

**확인한 것**
- 모듈 단위 테스트 113개가 모두 통과했습니다. 새로 추가한 테스트는 `SqlTableExtractorTest` 3개와 `DictionaryFileReaderTest` 5개입니다.
- 4081의 dstone-knowledge를 새 jar로 다시 띄우고, HI-Japan을 `rerunFrom: SEMANTIC`으로 재분석한 뒤 API로 위 수치를 확인했습니다.

**확인하지 못한 것**
- 7081의 `dstone-boot`는 다시 배포하지 않았습니다. 터미널(pts/0)에서 띄운 인스턴스라 건드리지 않았습니다. 화면에서 "사전" 표시를 보려면 `dstone-boot`를 다시 빌드해서 띄워 주세요. `graph.js`는 문법 검사만 했고 화면에서는 보지 못했습니다. 다시 배포하지 않아도 `JC_BENEFIT` 같은 한글명은 지금 화면에서 바로 보입니다. 다만 사전에서 가져온 이름은 출처 태그가 "DICT"로만 보입니다.
- `docs/11.dstone-knowledge.md`와 `CLAUDE.md`의 해당 부분도 고쳐서 같은 커밋에 넣었습니다.
