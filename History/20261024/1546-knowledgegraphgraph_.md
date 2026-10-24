# knowledgegraphgraph_

> 생성일시: 2026-10-24 15:46

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
