# localcommandcaveatTh

> 생성일시: 2026-11-12 16:11

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-boot>knowledge>검색 · 문서 화면에서
프로젝트:dstone-knowledge
찾을 내용:"analysis_reference 테이블이 사용된 소스를 찾아줘." 라는 질문을 했을 때 나온 대답이 정상인가?

---

## 답변

진행 상황부터 말씀드리면, 화면이 호출하는 검색 API를 찾아 같은 질문을 직접 보내 보고, 그 결과를 실제 소스와 대조하겠습니다.

---

## 답변

Knowledge 서버가 떠 있으니 화면 기본값(HYBRID, topK 10, 코드)으로 같은 질문을 보내 보겠습니다.

---

## 답변

이제 실제로 `analysis_reference`를 쓰는 SQL이 몇 개인지 소스에서 세어 보겠습니다.

---

## 답변

질문에 정확히 답하는 API(`/tables/{table}`)도 있으니, 분석 결과 자체에는 몇 개가 잡혀 있는지 확인합니다.

---

## 답변

분석 결과에는 18개가 모두 잡혀 있습니다. 그런데 검색에서 이름(KEYWORD)으로 맞은 건 5개뿐이라, 이름 검색이 나머지를 왜 놓쳤는지 확인하겠습니다.

---

## 답변

**판정: 반은 맞고 반은 틀렸습니다.** 화면 기본값(뜻+이름, 10건, 코드)으로 같은 질문을 knowledge API에 보내 재현했고, 결과를 실제 소스와 대조했습니다.

**나온 10건**

| 순위 | 결과 | 실제로 `analysis_reference`를 쓰는가 |
|---|---|---|
| 1 | `RelationDao.updateReference` (UPDATE) | O |
| 2 | `CarryDao.resetReferencesToResolve` (UPDATE) | O |
| 3 | `DeclarationDao.insertReference` (INSERT) | O |
| 4 | `ReferenceRow` (이 테이블의 행 모델) | O |
| 5 | `CarryDao.carryResolvePasses` (읽기) | O |
| 6~10 | `SqlTableExtractor.TableUse` 생성자 2개, `MapperLinker.extractTables`, `ResourceController.getTableUsage`, `CarryDao.analyzeTable` | **X**. "테이블"이라는 뜻만 비슷한 일반 코드입니다 |

**문제는 두 가지입니다**

1. **13개가 빠졌습니다.** 이 테이블을 쓰는 SQL은 실제로 18개입니다. `sqlmap`에서 직접 세어도, 분석 결과 API(`/api/revisions/115/tables/analysis_reference`)로 봐도 18개로 같습니다. 그런데 검색에는 5개만 나왔습니다. `selectReferenceSummary`, `selectUnresolved*` 6개, `insertMetrics`, `selectRepositories`, `selectMapperCallPage` 등이 빠졌습니다.
2. **관계없는 5건이 그 자리를 차지했습니다.**

분석이 틀린 것은 아닙니다. 이름 검색(KEYWORD)은 18개를 모두 찾았습니다(KEYWORD 모드로 39건). 순위를 합치는 단계에서 밀려났습니다.
- 테이블 이름은 청크 **본문**에만 있어서 이름 점수가 모두 1점입니다. 제목에 맞을 때 받는 가중치 2배를 받지 못합니다.
- `HybridRanker`의 RRF에서 이름으로만 맞은 결과는 최대 1/61점입니다. 벡터 검색 상위권(1/61, 1/62…)과 비기거나 집니다.
- 그래서 양쪽에 다 걸린 5건만 위로 올라오고, 나머지 자리는 "테이블" 뜻만 비슷한 벡터 결과가 채웁니다.

**정리하면** "이 테이블을 쓰는 곳 전부"처럼 빠짐없이 나열해야 하는 질문은 유사도 검색으로 풀기 어렵습니다. 정확한 답은 이미 테이블 사용처 API에 있지만, dstone-boot 화면 어디에서도 이 API를 부르지 않습니다.

고치는 방법으로 다음 두 가지를 생각했습니다.
- **A. 순위 보정:** 질문에서 고른 이름이 청크의 `reads`/`writes` 테이블과 정확히 같으면, 제목에 맞은 것처럼 이름 점수를 2배로 칩니다. 변경은 `RagDao.xml`의 `searchChunksByKeyword`와 `HybridRanker`에 국한됩니다. 다만 topK 10으로는 여전히 18개를 다 보여 줄 수 없습니다.
- **B. 화면에 테이블 사용처 연결:** 고른 이름이 분석된 테이블이면, 검색 결과 위에 "이 테이블을 쓰는 SQL 18개"를 사용처 API로 함께 보여 줍니다.

A와 B를 같이 하는 것을 권합니다. 진행할까요?
