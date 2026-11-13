# pasted_content_id9ee

> 생성일시: 2026-11-14 06:19

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
