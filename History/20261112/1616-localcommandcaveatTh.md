# localcommandcaveatTh

> 생성일시: 2026-11-12 16:16

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

---

## 질문

이에 앞서 이 화면기능 및 현재 구현된 rag 의 메커니즘에 대해서 자세하게 알고싶어.

---

## 답변

화면 코드와 RAG 쪽 코드를 차례로 읽고 정리하겠습니다. 먼저 관련 파일 목록과 문서부터 보겠습니다.

---

## 답변

Chunk 실제 모양을 보기 위해 앞서 나온 MAPPER·METHOD 청크 본문 하나씩과, 임베딩 쪽(Worker/Gate)과 문서 업로드 쪽을 읽겠습니다.

---

## 답변

"검색 · 문서" 화면과 dstone-knowledge의 RAG 구조를 코드 기준으로 정리했습니다. 마지막 절에서는 앞의 `analysis_reference` 결과가 왜 그렇게 나왔는지 이 구조에 대입해 설명합니다.

## 0. 먼저 짚을 점: 이 화면은 "답"을 만들지 않습니다

RAG는 검색(Retrieval)과 답 생성(Generation)으로 나뉩니다. dstone-knowledge는 **검색까지만** 합니다(`SearchService` 주석: "이 모듈은 답을 지어내지 않습니다"). 화면에 보이는 것은 LLM이 쓴 답이 아니라 **찾은 조각(청크)을 순위대로 나열한 목록**입니다. 이 조각으로 답을 쓰는 일은 dstone-ai-engine의 Agent(`KnowledgeTool`, RAG)가 맡습니다.

그래서 "analysis_reference를 쓰는 소스를 **다** 찾아줘"처럼 빠짐없이 나열해야 하는 질문은, 상위 N개를 고르는 이 구조와 처음부터 맞지 않습니다.

## 1. 전체 흐름

```
[색인 쪽: 분석 Job의 DOCUMENT 단계 + 백그라운드 임베딩]
 analysis_* 테이블(분석 결과) + 소스 파일
   └─ DocumentPass (pass09, order 600)
        ├─ Java → FILE / TYPE / METHOD 문서      (CodeDocumentBuilder)
        ├─ 매퍼 XML → statement마다 MAPPER 문서  (ResourceDocumentBuilder)
        └─ JSP·WebSquare·Nexacro → VIEW 문서
   → rag_document / rag_chunk 저장, rag_embedding에 PENDING으로 등록
   └─ EmbeddingWorker(서버 안 상주 스레드) → Ollama bge-m3 → vector(1024) 저장, DONE

[검색 쪽: 화면 → dstone-boot 프록시 → POST /api/search]
 질문 ─┬─ 질문 임베딩 → 벡터 검색(HNSW, 코사인) → minScore 미만 버림
       └─ 이름 추출(SearchKeywords) → 이름 검색(글자 일치 SQL)
   → HybridRanker(RRF)로 합쳐 topK건 → 화면에 나열
```

## 2. 색인: 무엇을 어떻게 저장하나

### 2-1. 테이블 세 개 (`02-create-table-postgresql-dstone-knowledge.sql` 7장)

| 테이블 | 단위 | 핵심 컬럼 |
|---|---|---|
| `rag_document` | 논리 문서 하나(메소드 1개, SQL 1개 …) | `source_type`(CODE/DOCUMENT), `doc_type`, `title`, `ref_kind`/`ref_id`(원래 분석 대상), `tenant` |
| `rag_chunk` | 검색 결과로 돌려주는 조각 | `content`, `content_hash`(SHA-256), `metadata_json`, `line_start/end` |
| `rag_embedding` | 벡터 + **대기열** | PK `(content_hash, model)`, `status`(PENDING/DONE/FAILED), `embedding vector(1024)`, `priority` |

- 임베딩 키가 **내용 해시**입니다. 리비전이 바뀌거나 분석을 다시 돌려도 내용이 같으면 다시 임베딩하지 않습니다. 증분 분석이 빠른 이유입니다.
- `rag_*`는 원본이 아니라서 언제든 `analysis_*`에서 다시 만들 수 있습니다.

### 2-2. 문서 종류와 청크에 들어가는 글

핵심 설계는 **소스만 넣지 않고, 분석으로 알아낸 사실을 글로 풀어 함께 넣는 것**입니다. "주문 취소는 어디서 처리하나" 같은 질문은 소스 글자와 닮지 않았기 때문입니다. 그 메소드가 받는 URL, 계층, 호출 관계를 적어 두면 질문과 가까워집니다.

| doc_type | 만드는 곳 | 내용 |
|---|---|---|
| FILE | Java 파일마다 | 경로, 패키지, 선언 타입, import |
| TYPE | 타입마다 | 계층, 상속·구현·주입, 진입점, 필드·메소드 목록 (소스 없음) |
| METHOD | 메소드마다 | 반환, 계층, 설명(주석), 진입점, 애노테이션, 호출하는 것/호출받는 곳(최대 10), 실행 SQL·여는 화면 + **소스** |
| MAPPER | SQL statement마다 | 매퍼 종류, 파라미터, **읽는/쓰는 테이블**, 실행하는 메소드 + **SQL 원문** |
| VIEW | 화면 파일마다 | 화면 이름, 연결된 주소·호출 |
| UPLOAD | 사람이 올린 문서 | Tika로 뽑은 글을 문단 단위로 자름(`TextChunker`, 경계 200자 겹침) |

앞서 나온 실제 MAPPER 청크는 이런 모양입니다.
```
[SQL] net.dstone.knowledge.api.dao.RelationDao.updateReference (UPDATE)
파일: resources/sqlmap/analysis/RelationDao.xml (줄 22-27)
쓰는 테이블: ANALYSIS_REFERENCE(고치기)
실행하는 메소드: RelationDao#replaceRelationsInBatch(long,List,List)
SQL:
UPDATE analysis_reference SET status = #{status} ...
```
- 단순 접근자(get/set/is, 3줄 이하, 같은 이름의 필드가 있음)는 METHOD 문서를 만들지 않습니다(`skip-accessors: true`).
- 긴 메소드는 `chunk-max-chars: 1800`자 기준으로 줄 경계에서 나눕니다. "이어서 2/2" 같은 표시가 붙습니다.

### 2-3. 임베딩: 분석 Job과 분리된 대기열

- DOCUMENT 단계는 청크를 **PENDING으로 등록만** 하고 바로 끝납니다. 임베딩은 CPU 환경에서 청크 한 건에 약 4초가 걸려서 Job과 떼어 두었습니다.
- `EmbeddingWorker`가 서버 안에서 계속 돌며 PENDING을 꺼내 Ollama(bge-m3)에 보내고 DONE으로 바꿉니다. 테이블 자체가 대기열이라 서버가 죽었다 살아나도 남은 것부터 이어서 합니다. 같은 청크가 세 번 실패하면 FAILED로 둡니다.
- 올린 문서는 우선순위 10, 코드는 0입니다. 사람이 올린 문서가 큰 프로젝트의 대기열 뒤에서 기다리지 않게 하려는 것입니다.
- **`EmbeddingGate`**: Ollama는 요청을 하나씩 처리합니다. 검색할 때 질문 한 줄을 임베딩하는 요청이 Worker가 보낸 묶음 뒤에 줄을 서면 수십 초가 걸립니다. 그래서 검색 중과 검색 직후 1.5초 동안은 Worker가 새 묶음을 보내지 않고, `batch-size: 1`로 둡니다.
- 임베딩이 끝나지 않은 청크는 벡터 검색에 안 나오지만 이름 검색으로는 나옵니다. 현재 리비전 115는 2,222개 모두 DONE입니다.

## 3. 검색: `/api/search` 내부

### 3-1. 화면이 보내는 요청 (`search.js`)
```json
{ "query": "...", "projectId": "dstone-knowledge", "mode": "HYBRID", "topK": 10,
  "sourceTypes": ["CODE"], "docTypes": [...] }
```
- 브라우저는 knowledge를 직접 부르지 않고 dstone-boot의 `/knowledge/api/call.do` 프록시를 거칩니다.
- 프로젝트를 고르면 그 프로젝트에서 **분석이 끝난 가장 최근 리비전**을 찾습니다. 프로젝트를 고르지 않으면 올린 문서에서만 찾습니다.
- 종류 체크(METHOD/TYPE/MAPPER/VIEW/FILE)를 하나도 안 고르면 전부 찾습니다.

### 3-2. 단계별 처리 (`SearchService.search`)

**① 이름 추출 (`SearchKeywords.extract`)**
- 영문·숫자·`_`·`$`·`.`·`/`·`#`로 이어진 덩어리를 고르고, 3자 미만은 버립니다. 최대 8개까지 고릅니다.
- `.`과 `#`에서 나눕니다(`BoardDAO.deleteBoard` → `BoardDAO`, `deleteBoard`). `/`가 있으면 주소로 보고 통째로 씁니다.
- **한글 낱말은 고르지 않습니다.** 이번 질문에서는 `analysis_reference` 하나만 나왔고 "테이블", "소스"는 무시됐습니다.
- 이름이 하나도 없으면 HYBRID로 요청해도 실제로는 VECTOR만 씁니다.

**② 벡터 검색 (`RagDao.searchChunks`)**
- 질문 전체를 bge-m3로 임베딩한 뒤 `ORDER BY embedding <=> 질문벡터`(코사인 거리), HNSW 인덱스로 찾습니다.
- 리비전 조건 때문에 결과가 모자라지 않게 `SET LOCAL hnsw.iterative_scan = relaxed_order`를 켭니다.
- 후보 수는 이름이 있으면 `max(30, topK×3)`(최대 150), 없으면 topK입니다.
- **유사도가 기준보다 낮은 결과는 버립니다.** 코드는 0.5, 올린 문서는 0.35입니다(2026-11-12 실측으로 정한 값). 버린 건수와 그중 가장 높았던 유사도는 `belowMinScore`/`bestBelowMinScore`로 돌려줍니다.

**③ 이름 검색 (`RagDao.searchChunksByKeyword`)**
- 이름마다 점수를 매겨 더합니다.
  - 제목에 한 낱말로 들어 있음(정규식, 앞뒤가 이름 글자가 아님) → **3**
  - 제목에 들어 있음 → **2**
  - 본문에만 있음 → **1**
- 이름 점수가 높은 순으로, 같으면 질문과의 유사도가 높은 순으로 후보 수만큼 가져옵니다.
- 이름으로 찾은 결과에는 유사도 기준을 적용하지 않습니다.

**④ 합치기 (`HybridRanker.fuse`, RRF)**
- 두 점수는 단위가 달라서 더하지 않고 **순위만** 씁니다. 각 목록에서 n등이면 `1/(60+n)`점을 받고, 두 목록의 점수를 더합니다.
- 제목에 이름이 정확히 맞으면(이름 점수 3 이상) 이름 쪽 점수를 **2배**로 칩니다.
- 위에서부터 topK건을 돌려주고, 각 결과에 `matched`(VECTOR/KEYWORD)와 `rankScore`를 붙입니다.

### 3-3. 화면 표시
결과마다 번호, 종류 배지, 제목, `경로:줄`, "나온 곳 VECTOR+KEYWORD", 유사도, 이름 점수가 나오고 본문은 접혀 있습니다. 상단에는 건수, 실제로 쓴 방법, 고른 이름, 경고(예: 임베딩 서버가 내려가 이름으로만 찾음)가 표시됩니다.

## 4. 앞의 결과를 이 구조에 대입하면

| 단계 | 실제로 일어난 일 |
|---|---|
| ① 이름 추출 | `analysis_reference` 하나 |
| ② 벡터 | 후보 30건. 실제 사용처 SQL보다 **"테이블"이라는 뜻에 가까운 일반 코드**의 유사도가 더 높았습니다(`TableUse` 생성자 0.6448 > `updateReference` 0.6267) |
| ③ 이름 | 39건이 맞았고(SQL 18개 + 그 SQL을 부르는 DAO 메소드 등), 그중 30건을 가져왔습니다. 테이블 이름은 **제목이 아니라 본문에만** 있어서 **모두 1점**입니다. 2배 가중치를 받은 결과는 없습니다 |
| ④ RRF | 양쪽에 다 걸린 5건이 1~5위를 차지했습니다. 6~10위는 벡터에만 걸린 1~5등이 받았습니다(1/61 ~ 1/65 = 0.01639 ~ 0.01538). 이름에만 걸린 결과 가운데 가장 높은 것도 이름 목록 6등이라 1/66 = 0.01515로 **간발의 차로 밀렸습니다** |

정리하면 분석(`analysis_*`)은 18개를 정확히 알고 있고 이름 검색도 18개를 다 찾았습니다. 그런데 순위를 합치는 단계에서 "본문에만 이름이 있는 결과"를 "뜻만 비슷한 결과"와 같은 무게로 다루기 때문에 13개가 잘렸습니다.

## 5. 지금 구조의 한계

- **나열형 질문에 약합니다.** "다 찾아줘"에 정확히 답하는 곳은 `GET /api/revisions/{id}/tables/{table}`(18건)인데, 검색 화면은 이 API를 부르지 않습니다.
- **본문에서 맞은 이름은 가중치가 없습니다.** 테이블 이름, 주소, 부르는 메소드 이름이 그렇습니다. MAPPER의 `reads`/`writes`처럼 구조화된 메타데이터에 정확히 맞아도 1점입니다.
- **한글 질의어는 이름 검색에 쓰이지 않습니다.** "주문 테이블"처럼 물으면 벡터 검색만 탑니다.
- **minScore 0.5는 "상관없는 것"만 거릅니다.** "비슷하지만 틀린 것"은 걸러내지 못합니다. 이번 결과의 6~10위가 모두 0.63 안팎이었습니다.

더 깊이 볼 부분(예: 청크를 실제로 만드는 코드, 업로드 문서 경로, ai-engine이 이 결과를 쓰는 방식)이 있으면 말씀해 주세요. 앞서 제안한 A(순위 보정)와 B(테이블 사용처 연결)도 이 설명을 바탕으로 다시 판단하시면 됩니다.
