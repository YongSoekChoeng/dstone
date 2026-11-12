# pasted_content_id9e3

> 생성일시: 2026-11-12 23:08

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

---

## 질문

dstone-boot > knowledge > 검색 · 문서 에서 문서 올리기 를 하면...
청킹로직이 어떻게 되는가? 청킹이 rag 검색할 때 충분이 효율적일만큼 구조적으로 적합한가?
만일 docx로 되어있는 넥사크로 개발가이드를 문서로 올린다면 dstone-ai-engine 에서 rag를 사용하여 충분히 넥사크로 개발을 진행할 수 있을까?

---

## 답변

Tika가 docx에서 문단 사이를 어떻게 출력하는지에 따라 청킹 결과가 크게 달라집니다. 실제로 돌려서 확인하겠습니다.

---

## 답변

문서 올리기의 청킹 과정을 코드로 확인했습니다. 그리고 docx 샘플(제목, 본문, 코드, 표 3세트)을 만들어 실제 Tika와 `TextChunker`에 넣어 봤습니다. 결론부터 말하면 **지금 청킹은 docx 구조를 거의 살리지 못합니다.** 넥사크로 가이드를 올리면 참고 자료로는 쓸 수 있지만, 그것만으로 넥사크로 개발을 맡기기는 어렵습니다.

## 1. 지금 청킹이 하는 일

`UploadDocumentService`에서 시작하는 처리 순서입니다.

1. **Tika**(`DocumentTextExtractor`)가 `BodyContentHandler`로 글자만 뽑습니다. 제목 수준, 표 구조, 그림 같은 서식 정보는 버립니다.
2. **`TextChunker.split(text, 1800, 200)`**
   - 빈 줄을 문단 경계로 보고 나눕니다.
   - 문단을 이어 붙이다가 1,800자를 넘기 전에 끊습니다.
   - 1,800자보다 긴 문단은 줄바꿈, 문장 끝, 빈칸 순으로 끊을 자리를 찾습니다. 이때 절반(900자)보다 뒤에 있는 자리만 씁니다.
   - 앞 청크의 마지막 문단이 200자 이하면 다음 청크 앞에 한 번 더 넣습니다(overlap).
3. 청크마다 첫 줄에 `[문서] 제목 (3/120)`을 붙여 임베딩합니다(bge-m3).

## 2. docx에서 실제로 나온 결과

Tika가 docx에서 뽑은 텍스트를 보면 **Word 문단 사이가 빈 줄이 아니라 줄바꿈 한 번**입니다. 빈 줄은 표의 행 뒤에만 생깁니다.

```
1. Transaction 사용법 섹션1        ← 제목인지 본문인지 구분이 없음
1.1 기본 호출
transaction 함수는 ...
this.fn_search = function()        ← 코드도 그냥 줄
<TAB>인자 <TAB>설명 <TAB>예        ← 표: 셀마다 한 줄, 행 뒤에만 빈 줄
```

그래서 청킹은 이렇게 됐습니다.

```
--- len 1002 starts: 넥사크로 개발 가이드/1. Transaction 사용법 섹션1/...
--- len 1014 starts: strURL/ URL/ svc::a.do//2. Transaction 사용법 섹션2/...
--- len 1014 starts: strURL/ URL/ svc::a.do//3. Transaction 사용법 섹션3/...
```

여기서 보이는 문제는 다음과 같습니다.

| 문제 | 영향 |
|---|---|
| 문단 경계를 찾지 못함 | 표가 없는 구간은 전체가 문단 하나로 잡혀 900~1,800자마다 줄 단위로 기계적으로 잘립니다. 절 중간에서 끊기는 일이 흔합니다 |
| 오히려 표 행이 문단 경계가 됨 | 위 결과처럼 표가 행 중간에서 잘리고, 다음 청크는 머리글 없는 표 조각(`strURL / URL`)으로 시작합니다 |
| 제목·절 정보가 없음 | 청크 머리에는 문서 제목만 붙고 "3장 > 3.1 Transaction"처럼 어느 절인지는 남지 않습니다. 절 제목이 청크 경계 앞에 있으면 뒤 청크는 무엇에 대한 설명인지 모릅니다 |
| 표의 의미가 사라짐 | 셀이 한 줄씩 흩어져 "이 값이 어느 열인지"가 사라집니다. 속성·인자 표가 많은 개발 가이드에서 특히 손해가 큽니다 |
| 코드 블록 보호가 없음 | 예제 코드가 청크 경계에서 반으로 잘릴 수 있습니다 |
| overlap이 무의미함 | 겹쳐 넣는 "마지막 문단"이 대부분 표 행 조각입니다 |
| 그림 손실 | 스크린샷과 다이어그램은 사라집니다(OCR 없음). Studio 화면 캡처로 설명하는 부분은 내용이 비게 됩니다 |

**구조적으로 적합하냐는 질문에는 "아니다"가 답입니다.** 지금 청킹은 빈 줄로 문단을 나누는 평문(txt, md)을 전제로 만들어졌고, docx나 PDF처럼 구조가 있는 문서에서는 사실상 고정 길이로 자르는 것과 같습니다.

## 3. 넥사크로 가이드를 올리면 ai-engine에서 개발이 될까

청킹 말고도 걸리는 부분이 있습니다.

1. **검색 질의가 다듬어지지 않습니다.** Agent RAG 옵션은 `RetrievalAugmentationAdvisor`에 retriever와 augmenter만 달아서 씁니다(질의 변환기 없음). 그래서 Workflow가 Agent에 넘긴 사용자 메시지를 **통째로 질의로 삼아 한 번만** 검색합니다. 메시지가 길고 여러 주제가 섞여 있으면 검색이 흐려집니다.
2. **양이 작습니다.** 기본값이 top-k 5에 유사도 0.35 이상이라서, 많아야 1,800자 청크 5개(약 9천 자)입니다. 화면 하나를 만들려면 Dataset, Grid, transaction, 공통 함수, 명명 규칙을 두루 알아야 하는데, 한 번의 검색으로는 모자랍니다.
3. **가이드에 없는 지식이 필요합니다.** 개발 가이드는 보통 규칙과 관례를 다룹니다. 실제로 화면을 만들려면 컴포넌트 속성·메소드 같은 API 레퍼런스와 **그 프로젝트의 실제 화면 코드 패턴**이 더 중요합니다. LLM이 넥사크로를 따로 아는 지식도 많지 않아서 `.xfdl` XML 구조를 틀리기 쉽습니다.
4. **검증 수단이 없습니다.** 만든 xfdl을 Studio에서 열어 보거나 실행해 보는 단계가 Workflow 안에 없습니다.

정리하면 **명명 규칙이나 transaction 호출 규약을 지키게 하는 보조 수단으로는 쓸 만하지만, 이것만으로 넥사크로 개발을 맡길 수는 없습니다.**

## 4. 쓸 만하게 만들려면 (효과가 큰 순서)

1. **기존 넥사크로 프로젝트를 소스분석에 함께 올리기.** dstone-knowledge는 Nexacro 화면을 분석합니다. `knowledgeSearch(docTypes=VIEW)`로 비슷한 기존 화면을 찾아 그 모양을 따라 하게 하는 편이 가이드 문서보다 훨씬 정확합니다.
2. **항상 지켜야 할 핵심 규칙은 Skill로 만들기.** 명명 규칙, 화면 골격, transaction/callback 패턴, 공통 함수처럼 어떤 화면을 만들든 필요한 것은 `skills/**/SKILL.md`에 넣습니다. 그러면 검색 운에 기대지 않고 Agent가 늘 봅니다. 가이드에서 수십 쪽 분량을 추리면 됩니다.
3. **세부 내용은 RAG에 두되, Agent는 `ragSearch` Tool로 여러 번 찾게 하기.** "Grid 콤보 컬럼 설정"처럼 필요한 순간에 좁은 질의로 찾는 편이 자동 첨부보다 낫습니다.
4. **청킹 개선**(코드 수정이 필요합니다)
   - Tika를 `ToXMLContentHandler`(XHTML)로 받아 `h1~h6`, `p`, `table`, `pre`를 구분합니다.
   - 절 단위로 청크를 만들고 머리에 절 경로를 붙입니다. 예: `[문서] 넥사크로 가이드 > 3. Transaction > 3.1 기본 호출`
   - 표는 행 단위로 묶되 머리글을 반복하거나 `인자=strURL, 설명=URL` 꼴로 펼칩니다.
   - 코드 블록은 자르지 않습니다.
5. **당장 쓸 수 있는 우회 방법:** docx를 Markdown으로 바꾼 뒤 절 사이에 빈 줄을 두고, `.md`로 올립니다(예: pandoc `docx → md`). 그러면 지금 청커도 문단 경계를 제대로 찾습니다. 절 경로는 여전히 붙지 않지만 지금보다 훨씬 낫습니다.

원하시면 4번 청킹 개선(XHTML 기반으로 절 단위·표·코드 블록을 처리)을 구현하겠습니다. 샘플 docx와 확인용 코드는 `/tmp/claude-1000/-app-dstone/ab482fe1-ee63-43b9-81aa-d3de5035fbe9/scratchpad/chunk/` 아래에 있습니다.
