# pasted_content_id9e3

> 생성일시: 2026-11-12 23:00

## 질문

<pasted_content id="9e3b">
dstone-boot > knowledge > 검색 · 문서 에서  
1. 문서 올리기를 해두면 어디서 사용할 수 있나? 
2. 소스분석을 해도 rag에 등록되고 문서올리기를 해도 rag에 등록되는건가?
3. dstone-ai-engine 에서 agent가 rag 옵션을 켜고 진행할 때 소스분석된 rag를 사용하는건가 아니면 문서올리기 rag를 사용하는건가?
4. dstone-ai-engine 에서 agent의 tool 을 보면 knowledge* tool들이 많은데 이들은 rag를 사용하는건가? 아니면 그냥 analysis_* 테이블조회를 하는건가?
</pasted_content id="9e3b">

---

## 답변

코드를 확인해서 답하겠습니다.

---

## 답변

코드 기준으로 정리했습니다.

## 1. 올린 문서는 어디서 쓰이나

`dstone-knowledge`의 `rag_document`/`rag_chunk`에 `source_type=DOCUMENT`로 저장되고, 임베딩이 끝나면 아래 세 곳에서 검색됩니다.

| 사용하는 곳 | 방법 |
|---|---|
| dstone-boot의 "검색 · 문서" 화면 | `POST /api/search`로 검색. 코드와 문서를 같이 찾거나 `sourceTypes`로 골라서 찾습니다 |
| ai-engine에서 RAG를 켠 Agent | `RagRetrievalChain.buildAdvisor()`가 찾은 조각을 질문 뒤에 `[참고자료]`로 붙입니다 |
| ai-engine Tool | `ragSearch`(`RagSearchTool`)와 `knowledgeSearch(includeDocuments=true)` |

API 키 인증이 켜져 있으면 문서는 올린 caller(tenant)별로 나뉩니다. 인증이 꺼져 있으면 tenant가 null이라서 모두가 같이 봅니다.

## 2. 소스분석과 문서 올리기 모두 RAG에 들어가나

네, 둘 다 같은 `rag_*` 테이블로 들어갑니다. `source_type`만 다릅니다.

- **소스분석**: 분석이 끝나면 RAG Pass가 `analysis_*` 결과로 청크를 만듭니다. `source_type=CODE`이고, `doc_type`은 METHOD·TYPE·FILE·MAPPER·VIEW 중 하나이며 리비전에 묶입니다.
- **문서 올리기**: Tika로 텍스트를 뽑아 청크로 나눕니다. `source_type=DOCUMENT`, `doc_type=UPLOAD`이고 리비전은 없습니다. 임베딩 우선순위가 10이라서 코드(0)보다 먼저 처리됩니다.

## 3. Agent의 RAG 옵션은 어느 쪽을 쓰나

**올린 문서만 씁니다.** `RagRetrievalChain.search()`가 `sourceTypes`를 `DOCUMENT`로 고정해서 보냅니다.

```java
// 올린 일반 문서에서만 찾는다. 코드 분석 결과는 tools.knowledge.KnowledgeTool의 몫이다.
body.put("sourceTypes", Arrays.asList("DOCUMENT"));
```

`RagSearchTool`도 같은 `search()`를 거치므로 올린 문서만 찾습니다. 소스분석으로 만든 RAG 청크를 쓰려면 Agent가 `knowledgeSearch` Tool을 불러야 합니다.

## 4. `knowledge*` Tool은 RAG를 쓰나, 테이블을 조회하나

Tool마다 다릅니다. 모두 dstone-knowledge REST API를 부르는데, 그 API가 무엇을 읽는지가 갈립니다.

| Tool | 호출하는 API | 읽는 데이터 |
|---|---|---|
| `knowledgeSearch` | `POST /api/search` | **RAG** (벡터 검색과 이름 검색을 섞은 하이브리드 검색). 기본은 CODE만 찾고, `includeDocuments=true`면 DOCUMENT도 찾습니다 |
| `knowledgeListProjects` | `/api/projects` | 프로젝트 목록 |
| `knowledgeFindMethods` | `/api/revisions/{id}/methods` | 분석 결과 테이블 |
| `knowledgeCallers` / `knowledgeCallees` | `/methods/{id}/callers`, `/callees` | 분석 결과(호출 관계) |
| `knowledgeTableUsage` | `/tables/{table}` | 분석 결과(SQL과 테이블 CRUD) |
| `knowledgeImpact` | `/impact` | 분석 결과(`impactWalk` 재귀 CTE) |
| `knowledgeDatasources` | `/configs`, `/resources` | 분석 결과(설정 파일) |

`knowledgeSearch` 하나만 RAG를 씁니다. 나머지는 임베딩 없이 `analysis_*` 테이블(그 위의 `kg_*` VIEW 포함)을 SQL로 조회합니다. 그래서 임베딩 서버가 꺼져 있어도 동작하고, 결과가 정확한 구조 정보입니다.

**한 줄로 정리하면** Agent의 RAG 옵션은 "올린 문서"를 참고자료로 붙이는 기능이고, 소스코드는 Agent가 `knowledge*` Tool을 불러서 찾습니다. 의미 검색이 필요하면 `knowledgeSearch`가 RAG를 쓰고, 호출 관계·영향도·테이블처럼 구조를 알아야 하면 나머지 Tool이 분석 결과 테이블을 조회합니다.

---

## 질문

그렇다면 knowledgeSearch 와 RagSearchTool 은 기능이 같은건가?

---

## 답변

비슷해 보이지만 같은 기능은 아닙니다. 둘 다 dstone-knowledge의 `POST /api/search`를 부르는데, 검색 범위와 거르는 기준이 다릅니다.

| 항목 | `knowledgeSearch` (KnowledgeTool) | `ragSearch` (RagSearchTool) |
|---|---|---|
| 기본 검색 대상 | 소스분석 청크(CODE)이고 프로젝트의 최신 리비전만 찾습니다 | 올린 문서(DOCUMENT)만 찾습니다 |
| 올린 문서 | `includeDocuments=true`일 때만 같이 찾습니다 | 항상 찾습니다 |
| `projectId` | 필수 | 받지 않음 |
| 종류 필터 | `docTypes`(METHOD/TYPE/FILE/MAPPER/VIEW/UPLOAD) | 없음 |
| 유사도 기준 | dstone-knowledge 서버 기본값(code 0.5, document 0.35) | ai-engine의 `dstone.ai.rag.retrieval.similarity-threshold`(기본 0.35)를 보내고, 받은 결과를 한 번 더 거릅니다 |
| 켜는 조건 | knowledge 주소만 있으면 동작합니다 | `dstone.ai.rag.enabled=true`여야 합니다. 아니면 실패 문구를 돌려줍니다 |
| 공유하는 코드 | 자체 구현 | Agent RAG 옵션과 같은 `RagRetrievalChain.search()`를 씁니다 |

정리하면 `ragSearch`는 **Agent RAG 옵션을 Tool로 꺼낸 것**입니다. 자동으로 붙는 `[참고자료]` 대신 LLM이 필요할 때 직접 부릅니다. `knowledgeSearch`는 **분석한 소스코드를 찾는 Tool**이고, 올린 문서 검색은 덤으로 붙어 있습니다.

### 겹치는 부분의 함정

`knowledgeSearch(includeDocuments=true)`로 찾은 문서가 `ragSearch`로 찾은 문서와 같지는 않습니다. `RagDao.xml`의 `documentScope`에 아래 조건이 있기 때문입니다.

```xml
<if test="projectId != null">AND d.project_id = #{projectId}</if>
```

`knowledgeSearch`는 항상 `projectId`를 보내므로, **그 프로젝트를 지정해서 올린 문서만** 찾힙니다. 프로젝트 없이 올린 문서는 `knowledgeSearch`로는 찾을 수 없고 `ragSearch`나 Agent RAG 옵션으로만 찾힙니다. 반대로 `ragSearch`는 `projectId`를 보내지 않아서 프로젝트 지정 여부와 상관없이 tenant가 같은 문서를 모두 찾습니다.

### 어느 쪽을 쓸지

- 코드에서 어떤 메소드·SQL·화면이 관련 있는지 찾으려면 `knowledgeSearch`를 씁니다.
- 설계서나 규정 같은 올린 문서를 근거로 삼으려면 `ragSearch`를 쓰거나 Agent의 RAG 옵션을 켭니다.
- 특정 프로젝트의 코드와 그 프로젝트에 붙여 올린 문서를 한 번에 찾으려면 `knowledgeSearch(includeDocuments=true)`를 씁니다.
