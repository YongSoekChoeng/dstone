# dstoneboot_knowledge

> 생성일시: 2026-10-24 09:11

## 질문

dstone-boot > knowledge > graph 에서  
테이블노드를 선택했을 때 보여주는 우측 상세내용에 테이블명 도 함께 보여주도록 수정해줘.
그리고 테이블명 아래에 컬럼정보도 함께 보여줘.

---

## 답변

DDL을 저장하는 곳이 없어서, 테이블을 쓰는 SQL 본문에서 컬럼을 뽑는 방향으로 보고 있습니다. 관련 코드를 더 확인하겠습니다.

---

## 답변

SQL 파서로 컬럼을 테이블별로 가려내는 방식을 시험해 봤고, 이제 실제 코드에 넣겠습니다.

---

## 답변

Now the extractor edits.

---

## 답변

Now tests, then MyBatisPlugin, DAO, service, screen.

---

## 답변

My test was wrong (REG_DT really is a column of that table). Fixing the expectation.

---

## 답변

Tests pass. Now storing columns, the query, and the service.

---

## 답변

Now the screen.

---

## 답변

Knowledge 모듈 복사본을 4181 포트로 띄워 실제 데이터로 확인하는 중입니다.

---

## 답변

First a look at the current table node before re-running (to confirm the "no column info" path):

---

## 답변

Spot-check a suspicious one (`SEQ`):

---

## 답변

Good: this project has a DDL (`db/schema.sql`), so I can check precision across all tables.

---

## 답변

Two tables have mismatches — checking whether the extractor or the DDL is off:

---

## 답변

The extractor is right there; `testApp/db/schema.sql` is just a cut-down DDL (`tbin_scd` has 4 columns there, but the mapper reads 16). Now a quick render check of the new screen function:

---

## 답변

Stopping the test instance and updating docs.

---

## 답변

Both mismatched tables check out against the mappers. Fixing the section reference and adding a CLAUDE.md note.

---

## 답변

노드 맵에서 테이블 노드를 고르면 오른쪽 상세에 **테이블명**이 나오고, 그 바로 아래에 **컬럼 목록**이 나오도록 고쳤습니다.

먼저 알려 드릴 점이 있습니다. 지금 분석 결과에는 테이블의 컬럼을 담아 두는 곳이 없었고, 분석한 프로젝트들에서 DDL 파일도 읽지 않습니다. 그래서 컬럼은 **SQL 매퍼에 적힌 컬럼을 모아** 보여 줍니다. DB에 정의된 컬럼 목록이 아니라서 화면에도 그렇게 적어 두었습니다.

**바뀐 것**
- **컬럼 추출** (`SqlTableExtractor`): SQL 파서로 읽은 SQL에서 테이블마다 쓰인 컬럼을 모읍니다. 어느 테이블의 컬럼인지 확실할 때만 넣습니다.
  - `A.COL`은 별칭 A가 가리키는 테이블의 컬럼입니다.
  - 별칭이 없는 `COL`은 그 FROM에 테이블이 하나뿐일 때만 넣습니다.
  - INSERT의 컬럼 목록과 UPDATE의 SET 왼쪽은 대상 테이블의 컬럼입니다.
  - `SYSDATE`나 SELECT 목록의 별칭, 서브쿼리 별칭으로 적힌 컬럼은 뺍니다.
  - 정규식으로만 읽은 SQL에서는 컬럼을 뽑지 않습니다.
- **저장** (`MyBatisPlugin`): SEMANTIC 단계에서 테이블 관계마다 `properties_json.columns`에 저장합니다. 테이블을 새로 만들지 않았습니다.
- **API** (`GET .../graph/node`): 테이블 노드 응답에 `columns`(컬럼명, 쓰인 SQL 수, C/R/U/D)와 `columnCoverage`(컬럼을 못 읽은 SQL 수)를 더했습니다.
- **화면** (`graph.js`): 테이블명, 컬럼 목록(예: `MB_ID  SQL 15건 · C·R·U·D`) 순서로 보여 주고, 그 아래에 원래 있던 "이 테이블을 건드리는 SQL"이 이어집니다. 컬럼을 못 읽은 SQL이 있으면 그 건수도 함께 알려 줍니다.
- **문서**: `docs/11.dstone-knowledge.md`와 `CLAUDE.md`에 반영했습니다.

**확인한 것**
- 단위 테스트를 포함해 `dstone-knowledge` 테스트가 모두 통과했습니다. 새 컬럼 테스트 5개가 들어 있습니다.
- jar 복사본을 4181 포트로 띄워 `testApp`을 확인했습니다. 예를 들어 `TBSY_MEMBER`는 컬럼 45개가 나왔고, `db/schema.sql`의 45개와 같습니다.
- DDL이 있는 테이블 17개를 모두 대조했습니다. 뽑은 컬럼 199개 가운데 DDL에 없는 것은 19개였고, 모두 `tbin_scd`와 `tbin_empmst` 두 테이블에서 나왔습니다. 매퍼를 열어 보니 두 테이블 다 실제로 그 컬럼을 읽고 있어서, 추출이 틀린 게 아니라 `schema.sql`이 컬럼을 줄여 적은 것이었습니다.
- 화면은 새 출력 함수만 node로 돌려 확인했습니다. 브라우저에서 띄워 보지는 않았습니다.

**알아 두실 것**
- 이 기능 전에 분석한 리비전은 컬럼이 비어 있고, 화면에도 그렇게 안내합니다. SEMANTIC부터 다시 돌리면 채워집니다: `{"revisionLabel": "...", "rerunFrom": "SEMANTIC"}`.
- 확인하려고 `testApp`의 리비전 1에서 SEMANTIC을 한 번 다시 돌렸습니다. 그래서 그 리비전에는 이미 컬럼이 들어 있습니다. 결과는 이전과 같고 컬럼 정보만 더해집니다.
- `SELECT *`처럼 이름이 적히지 않은 컬럼은 나오지 않습니다.
- 작업 중에 기존 코드에서 버그로 보이는 곳을 발견했지만 손대지 않았습니다. `SqlTableExtractor.normalize()`가 이름에 `X_`가 들어간 테이블을 모두 버립니다. 그래서 `TB_TAX_RATE` 같은 테이블은 테이블 관계에서 빠집니다. 원하시면 따로 고치겠습니다.

커밋은 하지 않았습니다. 올려 드릴까요?

---

## 질문

X_ 가 들어간 테이블을 버리는 이유가 뭐야?

---

## 답변

SQL 안의 `${…}` 자리를 표시하려고 넣은 글자가 `X_`라서 그렇습니다. 그런데 거르는 조건이 너무 넓어서 진짜 테이블까지 같이 버리고 있습니다.

**원래 의도**

`clean()`이 SQL을 파서에 넣기 전에 MyBatis/iBATIS 자리표시자를 바꿉니다.
- `#{id}`, `#id#` → `?` (값이 들어가는 자리)
- `${tableName}`, `$tableName$` → `X_` (SQL 글자 자체가 끼워지는 자리)

`${…}`는 실행할 때 무엇이 들어올지 모릅니다. 그래서 그 자리로 만들어진 테이블 이름은 "모르는 이름"으로 보고 버리는 것입니다. 이 모듈의 "계산할 수 없으면 짐작하지 않는다" 규칙과 같습니다.

```
FROM ${tableName}          → FROM X_            → 버림 (맞음)
FROM TB_LOG_${yyyymm}      → FROM TB_LOG_X_     → 버림 (맞음)
FROM ${schema}.TB_ORDER    → FROM X_.TB_ORDER   → 버림
```

**문제**

`normalize()`가 `cleaned.indexOf("X_") >= 0`, 즉 이름 어디에든 `X_`가 있으면 버립니다. 그래서 자리표시자와 상관없는 진짜 이름도 걸립니다.

- `TB_TA`**`X_`**`RATE`, `FA`**`X_`**`INFO`, `TB_BO`**`X_`**`ITEM`, `TB_IND`**`EX_`**…처럼 X로 끝나는 단어 뒤에 `_`가 오는 이름은 전부 해당됩니다.
- 이런 테이블은 `READS_TABLE`/`WRITES_TABLE` 관계가 아예 생기지 않습니다. 그래서 노드 맵, 테이블 영향도, 테이블 목록에서 모두 빠집니다. 정규식 쪽도 같은 `normalize()`를 거치므로 피할 길이 없습니다.
- 방금 넣은 컬럼 필터(`startsWith("X_")`)도 같은 방식이라 `X_POS` 같은 컬럼을 버립니다. 이쪽은 그런 이름이 드물어서 영향이 작습니다.

**고치는 방법(제안)**

자리표시자를 실제 SQL에 나올 리 없는 이름으로 바꾸고, 그 이름이 들어 있을 때만 버리면 됩니다. 예를 들어 `DSTONE_DYNAMIC_` 같은 이름이고, 파서가 읽을 수 있도록 식별자 모양은 유지합니다. 그러면 위의 세 경우는 지금처럼 버리고, `TB_TAX_RATE`는 정상적으로 잡힙니다.

`${schema}.TB_ORDER`는 실제 테이블이 `TB_ORDER`가 확실하므로 살려도 됩니다. 다만 동작이 하나 더 바뀌는 것이라 같이 할지는 정해 주세요.

고치고 나면 기존 리비전에 반영하려면 SEMANTIC부터 다시 분석해야 합니다(`rerunFrom: SEMANTIC`). 진행할까요?

---

## 질문

normalize 가 그 로직을 넣은 이유가 뭐야?

---

## 답변

`normalize()`의 `X_` 검사는 자리표시자 `X_`와 같은 커밋(`d4f20ff9`, 2026-10-05, M5 "Java 밖의 자원 분석 추가")에서 함께 들어갔습니다. 그 뒤로 이 줄은 바뀌지 않았습니다. 다만 왜 `indexOf`(이름 어디든 포함)로 했는지 적어 둔 주석이나 문서는 없습니다. 아래는 코드를 보고 추측한 것입니다.

**코드로 보이는 의도**

`X_`를 거르는 곳이 두 군데입니다.

1. `NOT_TABLES`의 `|X_|`는 이름이 정확히 `X_`인 경우입니다. `FROM ${tableName}` → `FROM X_`가 여기서 걸립니다.
2. `normalize()`의 `cleaned.indexOf("X_") >= 0`은 이름 안 어디든 `X_`가 있는 경우입니다.

1번만으로도 `${tableName}` 하나짜리는 이미 걸러집니다. 그런데도 2번을 따로 넣은 것은 `${…}`가 **이름의 일부**로 쓰인 경우를 잡으려던 것으로 보입니다.

```
FROM TB_LOG_${yyyymm}     → TB_LOG_X_      (끝에 붙음)
FROM ${prefix}_ORDER      → X__ORDER       (앞에 붙음)
FROM ${schema}.TB_ORDER   → X_.TB_ORDER    (스키마 자리)
```

이런 이름은 실행할 때 정해지므로 "모르는 테이블"이고, 버려야 "계산할 수 없으면 짐작하지 않는다" 규칙에 맞습니다.

**놓친 점**

- 그 커밋의 테스트는 `${tableName}`, `$tableName$`처럼 **이름 전체가 자리표시자인 경우만** 확인합니다. 위의 부분 치환 경우는 테스트가 없습니다. 그래서 2번이 실제로 필요한지, 너무 넓지 않은지가 검증된 적이 없습니다.
- `X_`처럼 짧은 글자를 표시로 고르면서, 그 글자가 `TAX_`, `FAX_`, `BOX_`처럼 진짜 이름에도 흔히 들어간다는 점을 고려하지 않은 것으로 보입니다. 가장 넓은 조건(`indexOf`)을 고른 것도 같은 이유로 보입니다.

정리하면 의도는 "실행할 때 만들어지는 테이블 이름 버리기"로 타당합니다. 문제는 표시 글자를 너무 흔한 글자로 고른 데 있습니다. 앞에서 말씀드린 대로 표시를 실제 SQL에 나올 리 없는 이름으로 바꾸면 의도는 그대로 두고 문제만 없앨 수 있습니다. 이때 위 세 가지 경우를 테스트로 같이 넣겠습니다.

---

## 질문

그런데 테이블명이랑 컬럼명 안나오는 것 같은데?

---

## 답변

변경은 이미 `c6982983`로 커밋되어 올라가 있습니다. 화면 쪽에서 무엇이 빠졌는지 확인해 보겠습니다.

---

## 답변

WSL에는 띄워진 서버가 없으니 Windows에서 확인하신 것으로 보고, 먼저 화면 코드 자체가 맞게 그리는지 실제 응답 모양으로 다시 확인하겠습니다.

---

## 답변

코드는 맞게 동작하는 것으로 보입니다. 새로 바뀐 파일 세 가지가 모두 Windows 쪽에 반영되어야 보입니다.

**어디서 막혔는지 화면으로 구분하는 방법**

| 오른쪽 상세에 보이는 것 | 원인 | 할 일 |
|---|---|---|
| "테이블명" 줄도 "컬럼" 제목도 없음 | 브라우저나 `dstone-boot`가 예전 `graph.js`를 쓰고 있음 | 먼저 **Ctrl+F5**를 눌러 보세요. `graph.js` 주소에 버전 표시가 없어서 캐시된 파일이 그대로 쓰이기 쉽습니다. 그래도 안 되면 `dstone-boot`를 다시 빌드하고 띄워 주세요 |
| 테이블명은 나오는데 컬럼이 "SQL에 이름이 적힌 컬럼이 없습니다"로 나옴 | `dstone-knowledge`가 예전 jar임 | `dstone-knowledge`를 `mvn clean package` 하고 다시 띄워 주세요 |
| 테이블명은 나오는데 "컬럼 정보가 없습니다 … SEMANTIC 부터 다시 분석" | 새 jar이지만 분석 결과에 컬럼이 아직 없음 | 그 리비전을 SEMANTIC부터 다시 분석해 주세요 (아래) |

다시 분석하는 요청입니다:
```
POST /api/projects/{프로젝트ID}/analyses
{"revisionLabel": "<그 리비전 라벨>", "rerunFrom": "SEMANTIC"}
```
WSL에서 다시 분석한 것은 `testApp`의 리비전 1 하나뿐입니다. Windows의 `dstone-knowledge`도 같은 PostgreSQL을 쓰고 있다면 `testApp`의 테이블은 다시 분석하지 않아도 컬럼이 나와야 합니다. 다른 DB를 쓰거나 다른 프로젝트라면 다시 분석해야 합니다.

**방금 고친 것**

`dstone-knowledge`가 예전 jar라서 컬럼 정보를 주지 않으면, 이제 화면에 "dstone-knowledge 가 컬럼을 주지 않는 예전 버전입니다. 새로 빌드해서 다시 띄워 주세요."가 나옵니다. 예전에는 "SELECT * 등"이라고 나와서 원인을 잘못 짚게 했습니다. 이 수정은 `graph.js` 한 파일이고 아직 커밋하지 않았습니다.

화면에 어느 경우로 보이는지 알려 주시면 그쪽을 더 살펴보겠습니다. `graph.js` 주소에 버전을 붙여 캐시 문제가 다시 생기지 않게 할 수도 있는데, 같이 할까요? 방금 수정도 커밋해서 올려 드릴까요?

---

## 질문

http://localhost:7081/defaultLink.do?defaultLink=knowledge/graph/graph 여기서 확인했어.

---

## 답변

원인을 찾았습니다. **Windows에서 떠 있는 `dstone-knowledge`(4081)가 이번 변경 전의 jar입니다.** WSL에서 그 서버에 직접 물어봤습니다.

```
GET http://<Windows>:4081/api/revisions/1/graph/node?id=T:TBSY_MEMBER
→ 키: id, kind, subType, name, fullName, layer, usage
```

새 버전이라면 `columns`와 `columnCoverage`가 함께 와야 하는데 둘 다 없습니다. 같은 DB의 리비전 1에는 아까 WSL에서 다시 분석해서 컬럼이 이미 들어 있는데도 안 나옵니다. 그러니 DB 문제가 아니라 서버 코드가 예전 것입니다.

하나 더 있습니다. 08:16에 `testApp`을 새로 분석하셨습니다(리비전 2, `20261024-081631`). 이 분석도 예전 jar로 돌았기 때문에 **리비전 2에는 컬럼 정보가 저장되지 않았습니다.** 화면은 최신 리비전인 2를 보여 주므로, jar만 바꿔서는 컬럼이 나오지 않습니다.

`dstone-boot`(7081) 쪽은 로그인이 필요해서 WSL에서 `graph.js`를 받아 보지 못했습니다. 화면에 "테이블명" 줄도 없었다면 `dstone-boot`도 예전 파일을 쓰고 있는 것입니다.

**Windows에서 하실 일**
1. `git pull`
2. `dstone-knowledge`: `mvn clean package` → 다시 띄우기
3. `dstone-boot`: 다시 빌드해서 띄운 뒤 브라우저에서 **Ctrl+F5**
4. 리비전 2를 SEMANTIC부터 다시 분석:
   ```
   POST http://localhost:4081/api/projects/testApp/analyses
   {"revisionLabel": "20261024-081631", "rerunFrom": "SEMANTIC"}
   ```

방금 고친 `graph.js`(예전 서버를 따로 알려 주는 문구)는 아직 커밋하지 않아서 `git pull`로는 받을 수 없습니다. 지금 커밋해서 올려 드릴까요? 올려 두시면 위 1~3단계에서 함께 반영됩니다.

---

## 질문

응 올려줘.

---

## 답변

`graph.js` 수정을 `b3e22165`로 커밋해서 GitHub `main`에 올렸습니다. 바뀐 파일은 `dstone-boot/src/main/webapp/knowledge/assets/js/graph.js` 하나입니다.

Windows에서 이렇게 하시면 됩니다.
1. `git pull`
2. `dstone-knowledge`를 `mvn clean package` 한 뒤 다시 띄우기
3. `dstone-boot`를 다시 빌드해서 띄운 뒤 브라우저에서 **Ctrl+F5**
4. `testApp`의 최신 리비전인 2를 SEMANTIC부터 다시 분석:
   ```
   POST http://localhost:4081/api/projects/testApp/analyses
   {"revisionLabel": "20261024-081631", "rerunFrom": "SEMANTIC"}
   ```

분석이 끝난 뒤 노드 맵에서 테이블을 고르면 테이블명, 그 아래 컬럼 목록이 나와야 합니다.

---

## 질문

우측 상세내용에 보여줄 테이블명 이라하면 한글명을 이야기 하는거고 컬럼정보 에서도 컬럼명, 컬럼한글명 이렇게 보여졌으면 좋겠는데?

---

## 답변

한글명을 어디서 가져올 수 있는지부터 확인하겠습니다. 지금 분석 결과에는 한글명이 없어서, 원천이 될 만한 곳을 찾아봐야 합니다.

---

## 답변

한글명을 어디서 가져올지 정하기 전에 확인하고 싶은 점이 있으신 것 같습니다. 어떤 부분이 궁금하신가요?

예를 들어 이런 것들을 정리해 드릴 수 있습니다.
- 실제로 쓰실 프로젝트에 한글명이 어디에 적혀 있는지 (SQL 주석, DB 코멘트, 테이블 정의서 등)
- 분석 서버에서 대상 DB에 접속할 수 있는지
- 방식마다 화면에 무엇이 나오고, 한글명이 없을 때는 어떻게 보이는지

---

## 질문

2번을 최우선으로 하되 대부분 DB정보가 제공되지 않으므로 옵셔널해야할 것 같고, 4번, 1번 을 참고해야 할 것 같아.
우선순위: 2번->4번->1번

---

## 답변

우선순위대로 만들겠습니다: **① 대상 DB 코멘트(설정이 있을 때만) → ② 프로젝트 안 DDL 파일의 COMMENT → ③ 매퍼 SQL 주석**. 먼저 분석 단계와 프로젝트 설정이 어떻게 짜여 있는지 확인하겠습니다.

---

## 답변

한글명은 세 군데에서 가져오도록 짜고 있습니다. 데이터를 담을 기존 테이블 구조를 확인하는 중입니다.

---

## 답변

RESOURCE 단계 구조를 파악했습니다. 이제 DDL 파일을 읽는 클래스부터 만듭니다.

---

## 답변

`lineOf`가 문장마다 파일 처음부터 줄을 세면 큰 파일에서 느려지므로, 줄 번호를 이어서 세도록 고칩니다.

---

## 답변

Now the classifier and RESOURCE pass hookup.

---

## 답변

다음은 SQL 주석에서 한글명을 뽑는 부분입니다(`SqlTableExtractor`).

---

## 답변

조인 조건(`ON tb2.a = tb1.a /* … */`)의 주석이 컬럼 한글명으로 잘못 붙을 수 있어서, 대입 패턴은 값이 파라미터나 리터럴일 때만 맞도록 좁힙니다.

---

## 답변

Now MyBatisPlugin, then tests.

---

## 답변

Tests for comments and the DDL reader:

---

## 답변

테스트가 모두 통과했습니다. 이제 대상 DB 코멘트 조회 부분입니다. 먼저 jar에 JDBC 드라이버가 들어 있는지 확인합니다.

---

## 답변

Only `conf/application.yml`. Writing the DB comment reader:

---

## 답변

Now the merge service and the queries.

---

## 답변

컴파일됩니다. 설정 예시를 `conf/application.yml`에 넣습니다.

---

## 답변

Now the screen. Checking the existing detail-panel styles first:

---

## 답변

Meanwhile, a harness that runs the real classes over `testApp`'s DDL and mappers:

---

## 답변

`INSCO_CD = "한화생명 유지율도 필요함"`은 이름이 아니라 메모로 보여서 원문을 확인합니다. 백그라운드 빌드 결과도 함께 봅니다.

---

## 답변

DDL과 DB가 일치합니다(`TB_SYS` 컬럼 19개, knowledge DDL의 `CREATE TABLE` 21개). 실제 API 응답을 넣어 화면 함수가 어떻게 그리는지도 확인합니다.

---

## 답변

Stopping the test instance and updating the docs.

---

## 답변

테이블 상세에서 **테이블명이 한글명**으로 나오고, 컬럼은 **컬럼명 / 컬럼한글명 / 타입 / SQL** 표로 나옵니다. 한글명은 말씀하신 순서인 **DB → DDL → SQL 주석**으로 찾습니다. 각 한글명 옆에 출처 표시(`DB`, `DDL`, `SQL 주석`)가 붙습니다.

**한글명을 찾는 곳**
1. **대상 DB 코멘트 (선택 사항)**: 프로젝트마다 접속 정보를 적었을 때만 씁니다. 적지 않으면 DB에 접속하지 않습니다.
   ```yaml
   dstone.knowledge.dictionary.db.testApp:
       url: jdbc:mysql://...
       username: ...
       password: ENC(...)
       # schema: APP   (선택)
   ```
   - 상세를 열 때 그 테이블 하나만 읽기 전용으로 조회하고, 10분 동안 캐시합니다. 분석 결과에는 저장하지 않습니다.
   - 접속이 실패해도 화면은 그대로 열립니다. 실패 이유는 상세 아래쪽 안내에 나옵니다.
   - 드라이버는 MySQL/MariaDB, PostgreSQL, Oracle이 이미 들어 있습니다.
2. **DDL 파일**: 이제 `.sql`/`.ddl` 파일도 스캔합니다. RESOURCE 단계에서 MySQL의 `COMMENT '…'`와 Oracle/PostgreSQL의 `COMMENT ON …`을 읽습니다.
3. **SQL 주석**: SEMANTIC 단계에서 매퍼의 `tb1.mb_nm /* 회사명 VARCHAR(100) */`, `FROM tbsy_member tb1 /* 회원사관리 */` 같은 주석을 읽습니다.
   - 끝에 붙은 타입(`VARCHAR(100)` 등)은 떼고 한글명만 남깁니다.
   - 어느 테이블의 컬럼인지 확실할 때만 넣습니다.
   - 별칭 설명(`AS X /* … */`), 값의 뜻(`= 'L34' /* … */`), 조인 조건에 단 주석은 넣지 않습니다.

**확인한 것**
- 단위 테스트는 모두 통과했습니다. 새 테스트는 DDL 3개, 주석 3개입니다.
- **DB 경로**: `analyzeDB`의 `TB_SYS`로 4181 테스트 인스턴스에서 확인했습니다. 테이블명 "시스템"과 컬럼 19개의 한글명, 타입이 DB에서 나왔습니다. 접속이 거부될 때 이유가 표시되는 것도 확인했습니다.
- **DDL 경로**: 실제 DDL 파일로 확인했습니다. `02-create-table-mysql-dstone-analyze.sql`에서 테이블 9개, 컬럼 100개와 그 설명을 모두 읽었고, DB 내용과 일치합니다.
- **SQL 주석 경로**: `testApp` 매퍼로 확인했습니다. 테이블 14개의 컬럼 한글명 164개와 테이블 한글명 7개가 나왔습니다. 예를 들어 `TBSY_MEMBER`는 45개 컬럼 모두 한글명이 붙었습니다.
  - 처음에는 `insco_cd = 'L34' /* 한화생명 유지율도 필요함 */` 같은 메모가 컬럼명으로 잡혀서 규칙을 좁혔습니다.
- 화면은 실제 API 응답을 넣어 그리는 함수만 확인했습니다. 브라우저에서 띄워 보지는 않았습니다.

**`testApp`에서 보시려면**
- `testApp/db/schema.sql`에는 COMMENT가 없고, `testApp` DB 계정은 여기서 접속이 거부되었습니다. 그래서 **SQL 주석의 한글명만** 나옵니다.
- 테이블명은 주석 그대로 `[SY][시스템] 회원사관리`처럼 나옵니다. 앞의 `[SY][시스템]`는 떼지 않았습니다. 떼려면 이 프로젝트의 관례에 맞춘 규칙이 하나 더 필요합니다.
- Windows에서는 `git pull` 뒤 `dstone-knowledge`와 `dstone-boot`를 다시 빌드하고, **새로 분석**해 주세요. 다시 분석하는 방식에 따라 나오는 것이 다릅니다.
  - SQL 주석만 보려면 `rerunFrom: SEMANTIC`으로 충분합니다.
  - DDL까지 보려면 `.sql` 파일이 이번에 처음 스캔되므로 새 리비전으로 분석해야 합니다.

아직 커밋하지 않았습니다. 커밋해서 올릴까요?

---

## 질문

커밋해서 올려줘.

---

## 답변

`28ea8a98`로 커밋해서 GitHub `main`에 올렸습니다. 변경한 19개 파일이 모두 들어갔고 `docs/temp`는 넣지 않았습니다.

Windows에서 확인하실 순서입니다.
1. `git pull`
2. `dstone-knowledge`를 `mvn clean package` 한 뒤 다시 띄우기
3. `dstone-boot`를 다시 빌드해서 띄우고, 브라우저에서 **Ctrl+F5**
4. `testApp`을 다시 분석:
   - DDL까지 반영하려면 새 리비전으로 분석합니다. `.sql` 파일이 이번에 처음 스캔되기 때문입니다.
     ```
     POST http://localhost:4081/api/projects/testApp/analyses
     {}
     ```
   - SQL 주석의 한글명만 보려면 최신 리비전에 `{"revisionLabel": "<최신 리비전 라벨>", "rerunFrom": "SEMANTIC"}`로 충분합니다.

`testApp`은 DDL에 COMMENT가 없고 DB 접속 설정도 없어서, 한글명은 SQL 주석에서 나옵니다. 예를 들어 `TBSY_MEMBER`는 테이블명이 `[SY][시스템] 회원사관리`로 나오고, 컬럼 45개 모두에 한글명이 붙습니다.

---

## 질문

테이블명과 컬럼명은 DDL 뿐만 아니라 소스에서도 유추할 수 있어야 할 것 같아. 심지어 DDL 에서도 정식 Comment 가 아닌 주석으로 테이블 한글명을 넣은 것도 있어.

---

## 답변

먼저 실제 소스에서 한글명이 어떤 모양으로 적혀 있는지 확인하겠습니다. DDL의 일반 주석과 Java 소스(VO 필드 주석 등)를 보겠습니다.

---

## 답변

You've hit your weekly limit · resets 12pm (Asia/Seoul)

---

## 질문

크레딧 구매했어 진행해줘.

---

## 답변

Now DDL plain comments in `DdlReader`:

---

## 답변

다음은 Java 소스에서 한글명을 찾는 SEMANTIC 플러그인입니다. 먼저 쿼리를 작성합니다.

---

## 답변

Now the plugin:

---

## 답변

Now updating the merge service with the new priority order:

---

## 답변

Now the DAO queries (DDL `commentKind`, source dictionary):

---

## 답변

Compiles. Tests for DDL notes and the source-comment helpers:

---

## 답변

`testApp` 경로를 잠시 `/app/testApp`으로 바꿔 분석하겠습니다. 먼저 지금 설정을 그대로 저장해 두고, 진행 중인 분석 작업이 없는지 확인합니다.

---

## 답변

Analysis finished. Restoring the project path right away, then checking the result:

---

## 답변

WSL 서버는 `D:` 경로를 볼 수 없어서 되돌리기가 거부되었습니다. 경로를 확인할 수 있는 Windows 쪽 서버(4081)로 되돌리겠습니다.

---

## 답변

Restored (all other fields were null, so nothing else changed). Now the results:

---

## 답변

Updating docs and CLAUDE.md for the new priority order:

---

## 답변

DDL의 일반 주석과 Java 소스에서도 테이블명·컬럼명의 한글명을 찾도록 넓혔습니다. 테스트는 모두 통과했고, `testApp`을 실제로 다시 분석해서 동작을 확인했습니다.

**한글명을 찾는 순서**: DB 코멘트 → DDL COMMENT → **DDL 일반 주석** → SQL 주석 → **소스**

- **DDL 일반 주석**: 정식 COMMENT가 없을 때 씁니다.
  - 테이블: `CREATE TABLE` 바로 윗줄(빈 줄 없이)이나 그 줄 끝에 단 주석
  - 컬럼: 컬럼 정의 줄 끝의 `-- 회원사ID`, 또는 바로 윗줄에 주석만 적은 줄
  - 파일 맨 위 설명처럼 빈 줄로 떨어진 주석은 쓰지 않습니다.
- **소스(Java)**:
  - JPA `@Table`/`@Column` 엔티티는 테이블이 확실하므로 먼저 씁니다.
  - 그다음은 이름이 컬럼과 같은 VO 필드입니다(`mb_nm`, `mbNm` → `MB_NM`).
  - 설명은 `@ApiModelProperty`·`@Schema`·`@Comment` → 필드 줄 끝 주석(`; /* 회사명 VARCHAR(100) */`) → 바로 위 Javadoc 순서로 찾습니다.
  - 바로 위의 `// 회원사 정보` 같은 주석은 여러 필드를 묶는 제목인 경우가 많아서 쓰지 않습니다.
  - 이름만 같은 필드는 어느 테이블의 것인지 알 수 없습니다. 그래서 가장 많은 필드가 쓴 글을 고르고, 화면에서는 **점선 표시**로 덜 확실하다고 알립니다.
- **근거 확인**: 출처 표시(`DB`, `DDL`, `DDL 주석`, `SQL 주석`, `소스`)에 마우스를 올리면 가져온 곳이 나옵니다. 예: `db/schema.sql:14`, `User.scd 외 (필드 2/2개가 같은 글)`

**`testApp` 분석 결과** (리비전 4)
- `TBSY_MEMBER` 테이블명: `[SY] 회원사(테넌트/회사) 마스터` (DDL 주석). 전에 나오던 SQL 주석보다 우선순위가 높습니다.
- `TBIN_SCD`: SQL 주석이 없는데도 컬럼 16개 중 10개가 소스로 채워졌습니다(`SCD = 조직코드` 등).
- 소스에서는 후보 필드 175개 중 140개에서 한글명을 찾았습니다.

**분석하면서 바뀐 것**
- WSL에서 분석하려고 `testApp` 경로를 잠시 `/app/testApp`으로 바꿨다가, Windows 서버를 통해 `D:/AppHome/testApp`으로 되돌렸습니다. WSL 서버는 `D:` 경로를 확인할 수 없어서 되돌리기를 거부했기 때문입니다. 나머지 설정은 원래 비어 있어서 바뀐 것이 없습니다.
- 이 분석으로 `testApp`에 **리비전 4**가 생겼습니다. Windows의 `dstone-knowledge`를 새로 빌드하기 전까지는 이 리비전도 예전 화면으로 보입니다.

아직 커밋하지 않았습니다. 커밋해서 올릴까요?
