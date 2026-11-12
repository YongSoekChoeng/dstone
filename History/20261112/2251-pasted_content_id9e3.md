# pasted_content_id9e3

> 생성일시: 2026-11-12 22:51

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
