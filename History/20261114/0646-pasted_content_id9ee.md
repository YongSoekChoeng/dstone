# pasted_content_id9ee

> 생성일시: 2026-11-14 06:46

## 질문

<pasted_content id="9ee4">
dstone-boot>knowledge 의 노드 맵 에서
프로젝트: dstone-knowledge 의 테이블 및 컬럼 추출해 낸 걸 보니까
ANALYSIS_* 이외의 테이블들이 많이 추출된 것 같아. 오탐인 것 같은데...
이런 현상을 없애기 위한 좋은 방법이 없을까? druid 를 사용하면 될까?
</pasted_content id="9ee4">

---

## 답변

먼저 실제로 어떤 테이블이 뽑혔는지, 어디서 나왔는지 확인해 보겠습니다.

---

## 답변

DB에서 dstone-knowledge(리비전 201)로 뽑힌 테이블 목록을 확인하는 중입니다.

---

## 답변

DDL 쪽은 정상입니다. 이번엔 SQL에서 읽고 쓴다고 잡힌 테이블(READS/WRITES_TABLE)을 봅니다.

---

## 답변

두 갈래로 나뉩니다. JSQLParser가 파싱해서 낸 HIGH 건과, 파싱이 실패해서 정규식(`via: PATTERN`)으로 떨어진 MEDIUM 건입니다. 원본 SQL과 폴백 코드를 봅니다.

---

## 답변

이제 추출기 쪽에서 CTE·임시 테이블·시스템 카탈로그·정규식 폴백을 어떻게 다루는지 봅니다.

---

## 답변

원인이 거의 잡혔습니다. 파서가 왜 실패했는지, 그리고 Druid나 JSQLParser 최신판이면 읽히는지 실제 SQL로 비교해 보겠습니다.

---

## 답변

Classpath에 4.7을 함께 넣어 다시 돌립니다.

---

## 답변

Druid 쪽은 클래스로더 문제(`java.sql`)라 부모 로더를 고치고, `<sql>` 조각은 빼고 다시 돌립니다.

---

## 답변

저장된 SQL은 `<include>`가 아직 펴지지 않은 상태라, 실제 파이프라인처럼 include를 채운 뒤 다시 비교합니다.

---

## 답변

## 결론

Druid로 바꾸는 건 권하지 않습니다. 이번 오탐은 파서 성능보다 **파서가 실패했을 때 쓰는 정규식 대안(byPattern)의 구멍** 때문에 생겼습니다. 정규식을 두세 군데 고치면 바로 없어집니다.

## 실제로 뽑힌 것 (리비전 201)

테이블은 모두 33개입니다. ANALYSIS_*와 RAG_*가 21개이고, 나머지 12개는 아래처럼 나뉩니다.

| 이름 | 출처 | 판정 | 원인 |
|---|---|---|---|
| `LIT`, `STMT`, `VIEW_HIT` | `RuleDao.xml:119` | **오탐** | `lit AS MATERIALIZED (` 꼴의 WITH 이름을 `CTE_NAME` 정규식이 못 잡음 |
| `TEXT` | `RuleDao.xml:119`, `SemanticDao.xml:439` | **오탐** | `trim(both '"' from a.text)`의 `from`을 테이블 앞 키워드로 봄 |
| `RETURN_TYPE`, `VISIBILITY` | `DiffDao.xml:144,151` | **오탐** | `o.visibility IS DISTINCT FROM n.visibility`의 `FROM`을 테이블 앞 키워드로 봄 |
| `TRIAL_BEFORE`, `TRIAL_AFTER` | `RuleDao.xml:70~95` | 진짜지만 임시 테이블 | 같은 매퍼에서 `CREATE TEMP TABLE`로 만든 테이블 |
| `TABLES`, `COLUMNS`, `PG_EXTENSION` | `SystemDao.xml`, `CarryDao.xml` | 진짜지만 시스템 카탈로그 | `normalize()`가 스키마를 떼서 `information_schema.tables`가 `TABLES`로 바뀜 |
| `KG_NODE` | `GraphDao`, `RelationDao` | **정상** | 실제로 있는 VIEW |

오탐 6개는 모두 `via: PATTERN`, 즉 JSQLParser가 실패해서 정규식으로 넘어간 SQL에서만 나왔습니다.

## Druid가 나을까? 직접 비교해 봤습니다

세 프로젝트의 매퍼 SQL을 include까지 채운 뒤 `clean()`을 거쳐 그대로 넣어 봤습니다. 숫자는 파싱에 성공한 문장 수입니다.

| 프로젝트 | 문장 수 | JSQLParser 4.7 (현재) | JSQLParser 5.3 | Druid 1.2.27 | 4.7이 실패한 문장 중 Druid가 읽은 수 |
|---|---|---|---|---|---|
| H-Energy (Oracle) | 2010 | **1881** | 1788 | 1840 | 73 / 129 |
| HI-Japan (Oracle) | 2090 | **1951** | 1947 | 1945 | 97 / 139 |
| dstone-knowledge (PG) | 294 | 267 | **270** | 260 | 12 / 27 |

- **Druid로 바꾸면 안 되는 이유:** 전체 성공률은 오히려 지금보다 낮습니다. 게다가 별칭과 컬럼을 찾는 `ColumnFinder`(약 300줄)를 새로 짜야 하고, 커넥션 풀까지 딸린 무거운 의존성이 하나 늘어납니다. 5.3으로 올려도 나아지지 않습니다.
- **두 번째 단계로 쓰면 효과는 있습니다:** "JSQLParser → Druid → 정규식" 순서로 두면 Oracle 프로젝트에서 정규식까지 가는 SQL이 절반 넘게 줄어듭니다. 다만 Druid로는 테이블과 CRUD만 받고 컬럼은 버리는 쪽이 현실적입니다. 또 Druid는 문법이 깨진 SQL도 받아 주는 경우가 있어서 검증이 필요합니다.

## 제안 (효과가 큰 순서)

1. **정규식 대안 보완 (작고 효과가 바로 남):** 지금 보이는 오탐 6개가 모두 사라집니다.
   - `CTE_NAME`이 `AS [NOT] MATERIALIZED (`도 받게 합니다.
   - `byPattern`에서 `DISTINCT FROM`과, 함수 괄호 안의 `FROM`(`trim`, `extract`, `substring`, `position` 등)은 건너뜁니다.
2. **임시 테이블과 시스템 카탈로그 구분:**
   - 같은 프로젝트에서 `CREATE TEMP TABLE`로 만든 이름은 빼거나 TEMP로 표시합니다.
   - `information_schema.*`, `pg_catalog`, `pg_*`, Oracle의 `SYS.`, `ALL_`/`USER_`/`DBA_`, `V$`는 스키마를 떼기 전에 SYSTEM으로 따로 표시합니다.
3. **근본 원인 손보기:** `MapperXmlReader`는 `<choose>`의 when과 otherwise 가지를 전부 이어 붙입니다. 그래서 `VALUES … SELECT NULL … WHERE FALSE`처럼 문법에 안 맞는 SQL이 생기고, 이런 SQL은 어떤 파서로도 못 읽습니다. 이번 프로젝트에서 실패한 27건 중 상당수가 이 경우입니다. 테이블을 뽑을 때만 가지별로 따로 파싱해서 합치면 정규식까지 가는 일 자체가 줄어듭니다.
4. (선택) 그래도 남는 실패를 줄이고 싶으면 그때 Druid를 두 번째 단계로 붙입니다.

1번과 2번은 금방 할 수 있으니, 원하시면 바로 고치고 재분석해서 노드 맵 결과를 비교해 보겠습니다. 단, Pass를 바꾸는 일이라 증분 분석과 전체 분석 결과가 같은지도 함께 확인하겠습니다.

비교에 쓴 코드는 `/tmp/claude-1000/-app-dstone/24296b29-7789-4a66-8048-da4b125fc713/scratchpad/cmp/Cmp.java`에 있습니다.

---

## 질문

모두 적용해줘. 그리고 올려줘.

---

## 답변

네 가지를 모두 적용하고 커밋·푸시까지 하겠습니다. 먼저 관련 코드와 문서를 읽습니다.

---

## 답변

SQL 추출기·매퍼 읽기·링커 구조를 다 파악했습니다. 이제 매퍼 읽기 쪽에서 SQL 본문을 만드는 부분과, 그걸 저장하는 곳을 확인합니다.

---

## 답변

Druid API를 확인합니다.

---

## 답변

API 확인 끝. 이제 `SqlTableExtractor`부터 고칩니다. (1) 정규식 대안 보완, (2) 시스템 카탈로그 표시, (4) Druid 2단계, (3) 가지별 SQL 합치기 순서로 넣습니다.

---

## 답변

Now the regex fallback (`byPattern`): skip `DISTINCT FROM`, `FROM` inside function parentheses, and function names after `FROM`.

---

## 답변

Now the constants and the CTE pattern fix.

---

## 답변

Now the `extract` flow: JSQLParser → Druid → regex, plus the multi-branch merge.

---

## 답변

Druid 의존성을 pom에 추가합니다.

---

## 답변

이제 매퍼 읽기에서 `<choose>` 가지별 SQL을 따로 펴서 저장하도록 합니다.

---

## 답변

매퍼 저장 SQL에 `properties_json`을 추가하고, 링커가 읽을 쿼리를 넣습니다.

---

## 답변

JSON을 Java에서 풀지 않도록, 가지별 SQL은 SQL에서 행으로 꺼내도록 바꿉니다.

---

## 답변

이제 `MapperLinker`를 고칩니다: 가지별 SQL로 테이블을 뽑고, 시스템·임시 테이블은 관계에서 뺍니다.

---

## 답변

이제 Java 문자열 SQL을 다루는 `JavaSqlLinker`도 똑같이 맞춥니다.

---

## 답변

컴파일은 통과했습니다. `via` 값을 보는 다른 곳이 있는지 확인합니다.

---

## 답변

기존 테스트를 먼저 돌려 봅니다.

---

## 답변

pom에 `skipTests`가 `true`로 박혀 있어서, 임시 pom 사본으로 테스트를 돌립니다.

---

## 답변

기존 테스트 27개는 통과했습니다. 새 동작에 대한 테스트를 추가합니다.

---

## 답변

기존 테스트가 `extract(null, …)`을 불러 오버로드가 모호해졌습니다. 목록용 메소드 이름을 `extractAll`로 바꿉니다.

---

## 답변

추출기 테스트 25개는 통과했습니다. 매퍼 테스트는 제 기대 문자열의 공백이 실제와 달라서, 실제 값을 확인합니다.

---

## 답변

Variants are correct; only my body assertion was wrong. Fixing the assertion.

---

## 답변

전체 201개 중 `RuleFileParserTest` 하나만 실패합니다. 제 변경과 무관한지 원래 코드로 확인합니다.

---

## 답변

원래 코드에서도 똑같이 실패하므로 기존 문제입니다(제 변경과 무관). 이제 실제 분석으로 확인합니다. 증분/전체 비교 절차를 문서에서 확인합니다.

---

## 답변

분석기 버전을 1.4로 올렸습니다. 이제 시험 인스턴스를 띄워 실제 재분석을 합니다. 먼저 4081 인스턴스가 어떤 jar로 떠 있는지 확인합니다.

---

## 답변

4081 인스턴스가 `target/dstone-knowledge.jar`로 떠 있어서 그 자리에서 다시 빌드하면 안 됩니다. 모듈을 scratch로 복사해 그곳에서 빌드합니다.

---

## 답변

실행 중인 분석 Job은 없습니다. 4181 포트에 시험 인스턴스를 띄웁니다(임베딩 끔, `bin`이 아닌 작업 디렉터리).

---

## 답변

시험 인스턴스가 떴습니다. dstone-knowledge 프로젝트로 전체 분석을 시작합니다.

---

## 답변

JDBC가 jsonb의 `?` 연산자를 매개 변수로 읽었습니다. `IS NOT NULL` 형태로 바꿉니다.

---

## 답변

Rebuilding the copy and restarting the test instance, then resuming with `rerunFrom: SEMANTIC` on revision 202.

---

## 답변

Rev 202 is done. Comparing extracted tables against rev 201:

---

## 답변

진짜 테이블 관계는 하나도 빠지지 않았습니다(새로 생긴 2건은 이번에 추가한 SQL입니다). 이제 증분 분석 결과가 전체 분석과 같은지 확인합니다.

---

## 답변

증분과 전체 결과가 완전히 같습니다(차이 0건). 시험 인스턴스를 내리고 문서를 고칩니다.

---

## 답변

Now updating docs (docs/11 and knowledge-core.md) in one pass.

---

## 답변

람다가 섞이지 않았는지 확인하고 커밋·푸시합니다.

---

## 답변

네 가지를 모두 적용해서 `main`에 푸시했습니다(커밋 `7a3e833a`).

## 바꾼 것

1. **정규식 대안 보완:** 함수 괄호 안의 `FROM`(`trim(both '"' from a.text)`, `extract(year from d)`)과 `IS DISTINCT FROM`, `FROM unnest(...)` 같은 함수 호출은 이제 테이블로 잡지 않습니다. `lit AS MATERIALIZED (` 꼴의 WITH 이름도 마찬가지입니다.
2. **시스템 카탈로그와 임시 테이블 제외:**
   - `information_schema.*`, `pg_catalog`, 알려진 `pg_*` 카탈로그, Oracle 사전 뷰, `V$`/`GV$`는 테이블 관계로 넣지 않습니다.
   - 프로젝트 SQL이 `CREATE TEMP TABLE`로 만드는 임시 테이블도 넣지 않습니다.
   - `PG_PAYMENT`나 `USER_INFO` 같은 업무 테이블이 걸리지 않도록, 접두어가 아니라 알려진 이름만 봅니다.
3. **`<choose>` 가지별 파싱:** 매퍼를 읽을 때 가지마다 편 SQL을 `analysis_mapper.properties_json.chooseVariants`에 따로 남기고, 테이블을 뽑을 때 가지별로 읽어 합칩니다. include로 가져오는 조각도 같은 방식으로 처리합니다. `sql_body`는 그대로라서 화면과 문서에 보이는 SQL은 바뀌지 않습니다.
4. **Druid를 두 번째 단계로 추가:** 순서는 JSQLParser → Druid 1.2.27 → 정규식입니다. Druid로 읽은 결과는 `via = DRUID`로 표시하고 테이블과 CRUD만 남깁니다.

예전 리비전에서 복사한 매퍼 행에는 `chooseVariants`가 없습니다. 그래서 `analyzer-version`을 1.4로 올려, 예전 리비전을 기준으로 증분 분석하지 않고 전체 분석을 돌게 했습니다.

## 확인 결과

dstone-knowledge 프로젝트를 4181 포트의 시험 인스턴스로 다시 분석했습니다.

| | 이전 (rev 201) | 이후 (rev 204) |
|---|---|---|
| 테이블 수 | 33 | **22** (ANALYSIS_* 18, RAG_* 3, VIEW `KG_NODE`) |
| 정규식으로 찾은 관계 | 103 | **18** |
| Druid로 찾은 관계 | 0 | 25 |

- statement별로 비교했을 때 진짜 테이블 관계는 하나도 빠지지 않았습니다. 사라진 것은 오탐 12개뿐입니다.
- 증분 분석(rev 203)과 전체 분석(rev 204)의 매퍼 행과 SQL 관계를 비교했고, 차이는 0건입니다.
- 단위 테스트는 201개 중 200개가 통과했습니다. 새 테스트 5개도 모두 통과했습니다. 실패한 1개(`RuleFileParserTest.옛_형식의_키는_새_형식을_알려_준다`)는 제 변경을 빼고 돌려도 똑같이 실패합니다.
- 처음 돌릴 때 jsonb의 `?` 연산자를 JDBC가 매개 변수로 읽어 분석이 실패했습니다. 고친 뒤 다시 돌렸고, 이 주의점은 문서에 적어 두었습니다.

## 직접 하셔야 할 일

- **4081 인스턴스는 아직 예전 jar로 떠 있습니다.** 실행 중인 jar를 그 자리에서 다시 빌드하면 안 돼서 건드리지 않았습니다. `stopApp.sh` → `mvn clean package` → `startApp.sh` 순서로 다시 띄워야 반영됩니다.
- **H-Energy와 HI-Japan은 다시 분석해야** 노드 맵에 반영됩니다. 분석기 버전이 바뀌었으니 자동으로 전체 분석으로 돕니다.
- 시험하면서 dstone-knowledge 프로젝트에 리비전 202~204가 생겼습니다. 202는 중간에 실패했다가 이어서 돌린 것이고, 204가 깨끗한 전체 분석입니다. 필요 없으면 지우셔도 됩니다.

문서는 `docs/11.dstone-knowledge.md`와 `.claude/docs/knowledge-core.md`에 반영했습니다.
