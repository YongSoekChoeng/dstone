# dstoneboot_knowledge

> 생성일시: 2026-10-24 07:57

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
