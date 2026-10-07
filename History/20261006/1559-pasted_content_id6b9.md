# pasted_content_id6b9

> 생성일시: 2026-10-06 15:59

## 질문

<pasted_content id="6b9e">
dstone-knowledge 에서 전체 호출구조를 비주얼하게 한눈에 볼 수 있는 노드(Node)맵 기능을 넣을 수 있겠는가?
1. 전체 노드맵 조회
2. 특정 노드를 선택했을 때 해당 노드와 관계를 맺는 노드트리들만 강조 혹은 별도 팝업
3. 선택된 특정 노드에 대한 상세설명 노출
</pasted_content id="6b9e">

---

## 답변

가능 여부를 정확히 답하려고, 지금 있는 관계 API와 화면 구조부터 확인하겠습니다.

---

## 답변

네, 넣을 수 있습니다. 분석 결과가 이미 관계 테이블(`analysis_relation`)에 그래프 형태로 쌓여 있어서 분석 단계는 손댈 필요가 없고, 조회 API 2~3개와 `dstone-boot` 화면 1개만 추가하면 됩니다. 아직 구현은 시작하지 않았습니다.

다만 1번 "전체 노드맵"은 메서드 단위로 그대로 그리면 쓸 수 없습니다. `anybiz_prd`만 해도 메서드가 14,762개라 브라우저가 버티기 어렵고, 그려져도 읽을 수 없습니다. 그래서 전체 보기는 묶어서 보여주고, 필요한 곳만 펼치는 방식을 권합니다.

## 요구사항별 구현 방안

| 요구사항 | 방안 | 지금 있는 것 | 새로 필요한 것 |
|---|---|---|---|
| 1. 전체 노드맵 | 클래스(타입) 단위로 묶은 지도. 노드는 클래스·SQL문·테이블·JSP, 선은 메서드 호출을 클래스 간으로 합친 것(굵기는 호출 수). 계층(Controller/Service/DAO)별 색상과 패키지·계층 필터 | 관계 데이터, 계층 정보 | 묶음 그래프 API (`GET /api/revisions/{id}/graph`) |
| 2. 선택 노드의 관계 트리 | 노드를 누르면 위쪽(호출하는 쪽: 화면 → 엔드포인트)과 아래쪽(호출되는 쪽: SQL → 테이블)으로 이어진 노드만 강조하고 나머지는 흐리게. "따로 보기" 버튼으로 그 부분만 팝업에 메서드 단위로 표시 | 영향 분석의 위쪽 탐색 쿼리, `/callers`·`/callees` (깊이 5 제한) | 한 노드 기준 양방향 부분 그래프 API |
| 3. 상세설명 | 오른쪽 패널에 종류, 계층, 파일·줄 번호, 엔드포인트 경로, 호출/피호출 수, 실행 SQL, 사용 테이블, 주석, 소스 일부 표시 | `/symbols/{symbolId}`, RAG 문서 본문 | 노드 종류별 상세를 한 번에 주는 API (기존 조회 조합) |

## 미리 알아둘 점

- **그래프 라이브러리**: `dstone-boot`에 그래프 라이브러리가 없어서 하나 추가해야 합니다. Cytoscape.js를 권합니다. 계층형 배치, 이웃 강조, 수천 노드 처리가 기본 기능입니다.
- **큰 프로젝트**: 클래스 단위로 묶어도 `anybiz_prd`는 노드가 수천 개가 될 수 있습니다. 처음에는 패키지 단위로 접어서 보여주고 눌러서 펼치거나, 노드 수 상한과 필터를 두어야 합니다.
- **신뢰도 표시**: `LOW` 관계는 추정이므로 점선으로 구분하고 기본은 숨기는 편이 좋습니다.
- **기존 규칙 유지**: 화면은 `dstone-boot`의 프록시(`/knowledge/api/call.do`)를 그대로 쓰고, 순환 방어(깊이 제한, 중복 제거)도 기존 쿼리와 같은 방식으로 넣습니다.

## 작업 범위

- `dstone-knowledge`: `GraphService`, 컨트롤러 엔드포인트 3개, `RelationDao.xml` 쿼리 추가, 단위 테스트
- `dstone-boot`: 화면 1개(`knowledge/graph/graph.jsp`, `graph.js`), 메뉴 링크, 안내 페이지 보강
- 문서: `docs/11.dstone-knowledge.md`, `CLAUDE.md`
- 검증: `struts-app`(작은 샘플, 기대값 있음)과 `anybiz_prd`(큰 실물)

## 진행 전에 정해주실 것

1. **전체 보기 단위**: 클래스 단위 + 패키지 접기로 진행해도 될까요? 메서드 단위 전체 표시는 권하지 않습니다.
2. **라이브러리 반입 방식**: Cytoscape.js 파일을 `dstone-boot/webapp` 아래에 직접 넣어도 될까요? 폐쇄망 SI 환경을 생각하면 CDN보다 이쪽이 안전합니다.

두 가지가 괜찮으시면 바로 구현하겠습니다.

---

## 질문

응 괜찮아.

---

## 답변

진행하겠습니다. 먼저 기존 코드(컨트롤러, 관계 쿼리, 스키마, 화면 공통 스크립트)를 읽겠습니다.

---

## 답변

Now writing the backend. SQL first.

---

## 답변

화면 코드(JSP, 스크립트, 스타일, 메뉴 링크) 작성을 마쳤고, 지금은 헤드리스 브라우저로 실제 화면을 띄워 동작과 모양을 확인하는 중입니다.

---

## 답변

`dstone-boot`를 빌드하는 동안(실제 JSP와 로그인 경로로 한 번 더 확인하기 위함) 문서를 갱신하고 있습니다.

---

## 답변

문서 갱신 스크립트가 앞 단계에서 중단돼 적용되지 않았습니다. 지금 `docs/11`과 `CLAUDE.md` 갱신을 다시 적용하고 마무리 점검을 합니다.

---

## 답변

노드 맵 기능을 넣었습니다. `dstone-boot`의 "코드 분석(Knowledge)" 위쪽 메뉴에 **노드 맵**이 생겼고, 요청하신 세 가지가 모두 동작합니다. 커밋은 하지 않았습니다.

## 만든 것

1. **전체 노드맵**: 클래스·화면(JSP)·SQL 매퍼·테이블을 노드로, 화면 → 컨트롤러 → 서비스 → DAO → SQL 매퍼 → 테이블 순으로 왼쪽에서 오른쪽으로 그립니다.
   - 노드가 250개를 넘으면 패키지(폴더) 묶음으로 접혀서 나오고, 묶음을 두 번 누르면 펼쳐집니다. `anybiz_prd`는 1,434개가 241개로 접혀 나옵니다.
   - 계층별 켜고 끄기, 이름으로 찾기, 자유 배치가 있습니다.
2. **선택 노드의 관계 강조와 팝업**:
   - 노드를 누르면 이어진 것만 남고 나머지는 흐려집니다. 부르는 쪽은 파란 테, 불리는 쪽은 주황 테입니다.
   - "관계 트리 따로 보기"를 누르면 메소드 단위 트리가 팝업으로 열립니다. 가운데가 고른 노드, 왼쪽이 부르는 쪽, 오른쪽이 SQL·테이블까지 불리는 쪽입니다.
3. **상세설명**: 오른쪽 패널에 위치, 계층, 처리하는 주소, 메소드나 SQL 목록, 그리고 분석이 이미 만들어 둔 설명 문서(검색에 쓰는 것과 같은 글)를 보여줍니다.

## 알아두실 점

- **전체 맵의 강조는 실제 호출 경로보다 넓게 켜집니다.** 클래스끼리의 선을 따라가기 때문에, 실제로는 이어지지 않는 길도 포함됩니다. 정확한 경로는 팝업 트리로 보셔야 합니다.
- **큰 프로젝트는 "바로 옆만" 강조로 시작합니다.** 공통 클래스(`CommUtil`, `BaseService`)를 거치면 거의 전부가 이어지기 때문입니다. 범위는 화면에서 바꿀 수 있습니다.
- **모델(VO) 계층은 기본으로 꺼져 있습니다.** getter/setter 호출이 그림을 덮습니다.
- **어디서나 쓰이는 클래스는 트리가 잘립니다.** `BaseService` 같은 클래스는 한 방향 3,000개 한도에 걸리므로, 메소드 하나를 골라서 보셔야 합니다.
- **자유 배치는 느립니다.** 노드 244개에 4초가 걸려서 400개까지만 허용했습니다.
- **중계 한도를 늘렸습니다.** `dstone-boot` 중계의 응답 한도가 256KB라 `anybiz_prd` 전체 맵(약 1MB)이 502로 막혔고, `KnowledgeProxyService`에서 32MB로 올렸습니다.

## 확인한 것

- `struts-app` 전체 맵의 관계 수가 표본 README의 기대값과 일치합니다.
- `anybiz_prd`는 전체 맵 1.3초, 보통 클래스의 트리 0.1~1초입니다.
- `dstone-boot`를 로컬로 띄워 로그인한 뒤 헤드리스 Chromium으로 실제 화면을 그려 확인했습니다. 노드 누르기와 강조, 묶음 펼치기/접기, 찾기, 트리 팝업, 상세까지 마우스 조작으로 통과했고 콘솔 오류는 없습니다.
- 사람이 직접 써 본 것은 아니므로, 배치나 색 같은 사용감은 한번 봐주시면 좋겠습니다.

## 실행 상태

- `dstone-knowledge`(4081)는 새 jar로 다시 띄워 둔 상태입니다. `bin/*.sh`에 실행 권한이 없어 `bash bin/startApp.sh`로 실행했습니다.
- 확인용으로 띄웠던 로컬 `dstone-boot`(7081)는 내렸습니다. 화면을 보시려면 `dstone-boot`를 새 war로 다시 배포해야 합니다.
- Cytoscape.js 3.34.3(MIT)은 `dstone-boot/src/main/webapp/knowledge/assets/lib/`에 넣었습니다.

문서는 `docs/11.dstone-knowledge.md`(4.18절, API 표, 현재 상태)와 `CLAUDE.md`, 화면의 사용 안내 페이지를 함께 갱신했습니다.

---

## 질문

dstone-knowledge 서버 내려줘

---

## 답변

`dstone-knowledge` 서버를 내렸습니다. 프로세스가 없고 4081 포트도 닫힌 것을 확인했습니다.

다시 띄울 때는 `bin/*.sh`에 실행 권한이 없으므로 `bash bin/startApp.sh`로 실행하시면 됩니다.

---

## 질문

dstone-ai-engine 어플리케이션의 db 접근 방식을 MyBatis로 수정해줘.
그리고 rag 기능을 dstone-knowledge 와 연동해서 진행할 수 있도록 수정해줘.

---

## 답변

먼저 `dstone-ai-engine`이 지금 DB를 어떻게 쓰는지와 RAG 관련 코드를 확인하겠습니다.

---

## 답변

두 가지 모두 반영했고, 로컬에서 두 서버를 띄워 실제로 확인했습니다. 커밋은 하지 않았습니다.

## 1. DB 접근을 MyBatis로

- **바뀐 곳**: JdbcTemplate을 쓰던 곳은 실행 이력 저장소(`WorkFlowExecutionStore`)와 문서 목록 조회 두 군데였습니다. 전자는 MyBatis Dao로 바꿨고, 후자는 RAG 이관으로 DB 조회 자체가 없어졌습니다.
- **구성**: SQL은 `resources/sqlmap/workflow/WorkFlowExecutionDao.xml`에 있고, `common.config.ConfigMapper`와 `common.biz.BaseDao`를 추가했습니다. DataSource는 기존 `spring.datasource.*` 설정을 그대로 씁니다.
- **확인**: Tool 워크플로 실행, 승인 대기 → 재개, 실행 목록/상세/스텝 이력 조회가 모두 정상입니다.

## 2. RAG를 dstone-knowledge와 연동

`dstone-ai-engine`은 이제 임베딩 모델도 pgvector도 갖지 않고, 문서 저장·임베딩·검색을 모두 `dstone-knowledge` API에 맡깁니다.

| 기능 | 지금 동작 |
|---|---|
| 문서 올리기 / 삭제 / 목록 | `dstone-knowledge`의 `/api/documents`로 전달 |
| 검색 (`searchDocuments` Tool, `/api/ai/rag/search`) | `dstone-knowledge`의 `/api/search` 호출 후 걸러서 반환 |
| Agent의 `ragEnabled` | 그대로 유지. 검색하는 부분만 교체 |
| `dstone-ai-engine`의 API 주소와 응답 모양 | 그대로. `dstone-boot`의 문서 관리 화면 코드는 고치지 않음 (화면에서 직접 눌러 보지는 않음) |

확인한 것: 시험 문서를 올린 뒤 이름 검색, 뜻 검색(유사도 0.71), 무관한 질문(0건), 임계값 0.9(0건)가 기대대로 나왔습니다. `ragEnabled` Agent도 실제 LLM 호출 1회로 올린 문서를 근거로 답했습니다. 삭제, 재삭제, 빈 파일(400)도 확인했습니다.

## 달라진 점

- **올린 직후에는 뜻으로 찾히지 않습니다.** 임베딩은 `dstone-knowledge`가 뒤에서 따로 하며, 이번 확인에서는 30~45초 걸렸습니다. 그 전에는 영문 이름이 글자 그대로 들어간 질문으로만 찾힙니다.
- **기존 `vector_store`의 문서는 옮기지 않았습니다.** 테이블은 지우지 않고 그대로 두었고, 코드만 더 이상 읽지 않습니다. 필요한 문서는 다시 올려야 합니다.
- **`dstone-knowledge`가 내려가 있으면 RAG 호출이 실패합니다.** 기동 직후 첫 검색은 임베딩 모델 로딩 때문에 한 번 타임아웃이 났습니다.
- **호출자별 문서 격리는 `dstone-ai-engine`이 문서 이름 앞에 호출자를 붙여서(`shop::약관.pdf`) 처리합니다.** `dstone-knowledge`가 보는 호출자가 엔진 하나뿐이기 때문입니다. 이 환경은 인증이 꺼져 있어 호출자가 있는 경우는 실행해 보지 못했습니다.
- **knowledge 쪽 변경은 한 줄입니다.** 검색 결과에 `documentId`를 추가했습니다.
- **`KnowledgeTool`은 컴파일만 확인했습니다.** HTTP 호출 코드를 RAG와 같이 쓰는 `KnowledgeClient`로 옮겼고, 같은 경로를 RAG 검색으로는 확인했지만 Tool 자체를 LLM으로 불러 보지는 않았습니다.

## 남은 일

- `dstone-ai-engine/k8s/configmap.yaml`은 오래된 최소 설정이라 datasource, knowledge 주소, RAG 설정이 없습니다. Pod로 올리기 전에 추가해야 합니다. 이번에는 손대지 않았습니다.
- 확인 과정에서 `dstone_ai` DB에 시험 실행 이력 3건이 남았습니다.
- 시험용으로 띄운 `dstone-ai-engine`과 `dstone-knowledge`는 모두 다시 내렸습니다.

문서는 `docs/09.dstone-ai-engine.md`, `docs/11.dstone-knowledge.md`(M9 중 ai-engine 쪽 완료로 표시), `docs/02.environment.md`, `docs/software/05.postgresql.md`, `CLAUDE.md`를 갱신했습니다.

---

## 질문

<pasted_content id="6b9e">
그럼 dstone-boot 에서 아래의 내용들이 수정되어야 하지 않나?
1. dstone AI > 채팅 
    문서검색(RAG) 체크박스 기능 정리 필요하지 않아?
2. dstone AI > 문서 임베딩 관리 
    이 메뉴는 삭제되어야 하지 않아?
</pasted_content id="6b9e">

---

## 답변

두 화면이 지금 어떻게 되어 있는지 먼저 확인하겠습니다.

---

## 답변

네, 2번은 맞고 1번은 절반만 맞습니다. 아직 수정하지는 않았고, 확인한 내용과 제안을 먼저 드립니다.

## 1. 채팅의 "문서검색(RAG)" 체크박스

기능은 지금도 그대로 동작합니다. 체크하면 고른 Agent에 `ragEnabled`가 켜지고, 검색만 `dstone-knowledge`로 가도록 바뀌었을 뿐입니다. 그래서 없앨 필요는 없고 설명만 정리하면 됩니다.

- 라벨을 "올린 문서 검색(RAG)"처럼 바꾸고, 문서를 어디서 올리는지(Knowledge > 검색 · 문서) 안내 한 줄을 붙입니다.
- AI 첫 화면과 Workflow 예시에 남아 있는 "RAG 문서 관리 화면에서 적재한…" 문구도 같이 고칩니다.

## 2. "문서 임베딩 관리" 메뉴

삭제하는 쪽이 맞습니다. 이유는 두 가지입니다.

- **기능이 겹칩니다.** Knowledge > 검색 · 문서 화면에 문서 올리기/목록/삭제가 이미 있고, 지금은 두 화면이 같은 저장소에 넣습니다.
- **목록이 실제와 어긋납니다.** 이 화면은 `dstone-boot` 자체 테이블(`TB_AI_DOCUMENT`)의 업로드 이력을 보여주기 때문에, Knowledge 화면에서 올리거나 지운 문서는 반영되지 않습니다.

삭제하면 달라지는 점은 다음과 같습니다.

- **업로드한 사람 기록이 없어집니다.** `TB_AI_DOCUMENT`에는 올린 사용자 ID가 남지만 `dstone-knowledge`는 그 정보를 저장하지 않습니다.
- **나중에 `dstone-ai-engine`의 API 키 인증을 켜면 문제가 생깁니다.** 인증된 호출자는 자기 이름이 붙은 문서만 검색하는데, Knowledge 화면에서 올린 문서에는 그 이름이 붙지 않아 RAG에 안 잡힙니다. 지금은 인증이 꺼져 있어 문제없습니다.

## 정해주실 것

삭제 범위를 어디까지 할지입니다.

- **권장안**: 메뉴, 화면(JSP·JS), 컨트롤러·서비스·Dao·매퍼는 삭제하고, `TB_AI_DOCUMENT` 테이블과 그 안의 이력은 DB에 남겨 둡니다. 스키마 파일에서는 정의를 빼고 "더 이상 쓰지 않음"으로 표시합니다.
- **대안**: 테이블까지 DROP합니다. 이력이 필요 없다는 확인이 있어야 합니다.

권장안으로 진행해도 될까요? 그러면 1번 문구 정리와 문서 갱신까지 함께 하겠습니다.

---

## 질문

진행해주되 더이상 사용되지 않는 리소스들(소스, 테이블 등... )은 모두 삭제해줘.

---

## 답변

진행하겠습니다. 삭제 대상을 먼저 빠짐없이 찾고, 지우기 전에 테이블 안의 내용도 확인합니다.

---

## 답변

Now the schema scripts, then the actual DB drops.

---

## 답변

소스·테이블 삭제와 화면 확인은 끝났습니다. 마지막으로 문서(`docs/06.dstone-boot.md` 등)에서 삭제한 화면을 설명하던 부분을 정리하고 있습니다.

---

## 답변

`dstone-boot` 정리를 마쳤고, 더 이상 쓰지 않는 소스와 테이블을 모두 삭제했습니다. 이번 변경분은 커밋하지 않았습니다(앞 작업분은 그사이 저장소에 커밋되어 있습니다).

## 1. 채팅의 RAG 체크박스

- 라벨을 "올린 문서 검색(RAG)"으로 바꾸고, 문서를 어디서 올리는지(코드 분석 > 검색 · 문서) 안내와 링크를 붙였습니다. 기능은 그대로입니다.
- AI 첫 화면의 설명과 Workflow 테스트의 예시 문구도 새 위치를 가리키도록 고쳤습니다.
- 코드 분석 > 검색 · 문서의 문서 올리기 안내에 "AI 채팅의 RAG도 여기서 올린 문서에서 찾는다"는 한 줄을 넣었습니다.

## 2. "문서 임베딩 관리" 삭제

- **화면과 소스**: 상단 메뉴 링크, 화면(JSP·JS), 컨트롤러·서비스·Dao·VO·매퍼 XML, 전용 CSS를 지웠습니다. 첫 화면의 카드는 코드 분석 > 검색 · 문서로 가는 "문서 올리기" 카드로 바꿨습니다.
- **스키마 파일**: `TB_AI_DOCUMENT` 정의, `dstone_ai` 초기화 스크립트의 확장 생성 구문을 뺐습니다.

## 실제 DB에서 삭제한 것

| DB | 삭제한 것 | 들어 있던 내용 |
|---|---|---|
| `sampleDB` (MySQL) | `TB_AI_DOCUMENT` | 업로드 이력 3건 (`ora-to-pg-0001`~`0003`, 모두 ADMIN01) |
| `dstone_ai` (PostgreSQL) | `vector_store` | 문서 1건(`ora-to-pg-0003`), 조각 24개 |
| `dstone_ai` | 확장 `vector`, `uuid-ossp`, `hstore` | `vector_store`용이었음 |
| `dstone_ai` | `analysis_*` 5개, `rag_*` 3개 테이블 | 모두 0건 |

마지막 줄의 8개 테이블은 요청 범위 밖에서 제가 발견한 것입니다. `dstone-knowledge` 스키마를 이 DB에 잘못 실행해서 생긴 것으로 보이고, 전부 비어 있었으며 `vector` 확장을 지우려면 함께 지워야 해서 삭제했습니다. 이제 `dstone_ai`에는 실행 이력 테이블 2개만 남았습니다.

## 확인한 것

`dstone-boot`를 clean 빌드로 다시 띄워 로그인한 뒤 확인했습니다. 메뉴에서 항목이 사라졌고, 옛 화면 주소는 404이며, 채팅 화면의 새 라벨과 안내가 보이고, 스크립트 오류는 없습니다. 확인 후 서버는 내렸습니다.

첫 확인 때는 수정 전 화면이 나왔고 원인은 확인하지 못했습니다. 그 빌드를 clean 없이 해서 삭제한 파일이 war에 남아 있던 것은 확인했습니다. 배포할 때는 `mvn clean package`로 빌드해야 합니다.

## 남겨 둔 것

- **`dstone-ai-engine`의 `/api/ai/embed/*` API**: 화면은 없어졌지만 엔진을 직접 호출하는 앱이 문서를 올리는 경로라 남겼습니다. 이것도 지우길 원하시면 말씀해 주세요.
- **`sampleDB`의 `TB_AI_SQLCONVERT` 테이블**: 저장소 어디에서도 참조하지 않는 테이블입니다. 이번 변경과 무관한 예전 "SQL 변환" 화면의 흔적으로 보여 손대지 않았습니다. 지워도 되는지 알려주시면 삭제하겠습니다.
- **인증을 켤 때 주의**: 나중에 `dstone-ai-engine`의 API 키 인증을 켜면, 코드 분석 화면에서 올린 문서는 인증된 호출자의 RAG 검색에 잡히지 않습니다. 인증을 켤 때 함께 정해야 합니다.

문서는 `docs/06.dstone-boot.md`(해당 절 삭제, 번호 정리), `docs/11.dstone-knowledge.md`, `docs/02.environment.md`, `docs/software/04.mysql.md`, `docs/software/05.postgresql.md`, `CLAUDE.md`를 갱신했습니다.

---

## 질문

dstone-ai-engine 에서도 삭제될 대상은 없는가?

---

## 답변

네, 있었습니다. 확실히 안 쓰는 것은 방금 지웠고, 판단이 필요한 두 가지는 남겨 두었습니다. 커밋은 하지 않았습니다.

## 지금 삭제·정리한 것

- **Lombok 의존성**: `dstone-ai-engine` 소스 어디에서도 쓰지 않아 `pom.xml`에서 뺐습니다.
- **테스트컨테이너 의존성**(`spring-ai-spring-boot-testcontainers`): 이 모듈에는 테스트 코드가 없어 함께 뺐습니다.
- **예제 YAML의 낡은 안내 3곳**: 없어진 "문서 임베딩 관리" 화면을 가리키던 문구를 "코드 분석(Knowledge) > 검색 · 문서"로 고쳤습니다. 지난 작업에서 제가 놓친 부분입니다.

clean 빌드는 통과했고, 산출물 jar에 Lombok·테스트컨테이너·pgvector·Tika 라이브러리가 없는 것을 확인했습니다. 이번에는 서버를 띄워 보지는 않았습니다.

## 정해주실 것: 문서 관리 API와 검색 미리보기 API

저장소 안에서 호출하는 곳이 없어진 API가 두 묶음 있습니다.

| 대상 | 내용 | 지우면 |
|---|---|---|
| `/api/ai/embed/*` (`EmbedController`, `EmbedService`, 응답 DTO 2개, 업로드 크기 설정) | 문서 올리기/삭제/목록을 `dstone-knowledge`로 넘기는 통로 | 문서를 올리는 경로가 코드 분석 화면(`dstone-knowledge` API) 하나로 정리됩니다 |
| `/api/ai/rag/search` (`RagController`) | LLM 없이 검색 결과만 보는 운영용 API | 같은 확인은 코드 분석 > 검색에서 "올린 문서"를 체크하면 됩니다 |

**둘 다 지우는 쪽을 권합니다.** 지우면 문서 이름 앞에 호출자를 붙이는 규칙(`RagSourceId`)도 함께 없앨 수 있고, 지난번에 말씀드린 "인증을 켜면 코드 분석 화면에서 올린 문서가 RAG에 안 잡히는 문제"가 사라집니다.

대신 포기하는 것이 하나 있습니다. 엔진을 호출하는 앱별로 문서를 가려 주는 기능이 없어져, 올린 문서는 모든 호출자가 함께 보게 됩니다. 지금은 인증이 꺼져 있어 실제 동작은 달라지지 않습니다. 앱별 격리가 나중에 필요하면 `dstone-knowledge` 쪽에서 다시 설계해야 합니다.

Agent의 `ragEnabled`와 `searchDocuments` Tool은 어느 쪽을 택해도 그대로 동작합니다.

## 삭제 대상이 아닌 것

- **Ollama 의존성과 설정**: 임베딩용으로는 안 쓰지만 채팅 모델 선택지(`spring.ai.model.chat: ollama`)라 남겼습니다.
- **`spring-ai-rag`**: `ragEnabled`가 검색 결과를 프롬프트에 붙일 때 씁니다.
- **`k8s/configmap.yaml`**: 삭제가 아니라 보완 대상입니다. datasource, knowledge 주소, RAG 설정이 빠진 옛 설정이라 Pod 배포 전에 채워야 합니다.

두 API를 권장대로 지울까요?

---

## 질문

응 지워줘 . 그리고 sampleDB의 TB_AI_SQLCONVERT 테이블도 삭제해줘.

---

## 답변

두 API와 `TB_AI_SQLCONVERT` 테이블을 삭제했고, 두 서버를 띄워 확인까지 마쳤습니다. 이번 변경분은 커밋하지 않았습니다.

## dstone-ai-engine에서 삭제한 것

- **문서 관리 API** `/api/ai/embed/*`: `EmbedController`, `EmbedService`, 응답 DTO 2개, 업로드 크기 설정.
- **검색 미리보기 API** `/api/ai/rag/search`: `RagController`, 요청 DTO.
- **호출자별 문서 이름 규칙**(`RagSourceId`)과 검색 체인·Tool에 남아 있던 호출자 구분 코드.
- **knowledge 호출 창구의 올리기·삭제 메소드**: 호출하는 곳이 없어져 함께 뺐습니다.

`ragEnabled`와 `searchDocuments` Tool은 그대로 두고 검색만 하도록 단순화했습니다.

## 삭제한 테이블

`sampleDB`의 `TB_AI_SQLCONVERT`를 DROP했습니다. 예전 "SQL 변환" 화면의 변환 이력 64건(2026-09-14 ~ 09-20)이 들어 있었고, 다른 테이블이 참조하지 않는 것을 확인한 뒤 지웠습니다. 스키마 파일에는 원래 정의가 없어 고칠 것이 없었습니다.

## 확인한 것

- 지운 두 API는 404로 응답합니다.
- `dstone-knowledge`에 문서를 직접 올린 뒤, RAG Tool 워크플로가 그 문서를 찾았습니다.
- `ragEnabled` Agent가 올린 문서를 근거로 답했습니다(LLM 호출 1회).
- 확인 후 시험 문서는 지웠고, 띄웠던 `dstone-knowledge`와 `dstone-ai-engine`은 모두 내렸습니다.

## 달라진 점

- **올린 문서는 엔진을 쓰는 모든 호출자가 함께 봅니다.** 앱별 격리가 필요해지면 `dstone-knowledge` 쪽에서 설계해야 합니다.
- **문서를 올리는 경로는 `dstone-knowledge` 하나입니다.** 코드 분석 > 검색 · 문서 화면이나 그 서버의 `/api/documents`를 씁니다. 지난번에 말씀드린 "인증을 켜면 화면에서 올린 문서가 RAG에 안 잡히는 문제"는 없어졌습니다.

문서는 `docs/09.dstone-ai-engine.md`, `docs/11.dstone-knowledge.md`, `CLAUDE.md`를 갱신했습니다.

---

## 질문

dstone-ai-engine, dstone-knowledge 화면 에 홈(dstone-boot)로 돌아오는 링크 붙여줘.

---

## 답변

AI 화면과 코드 분석(Knowledge) 화면의 상단 메뉴 맨 앞에 "← Dstone 홈" 링크를 붙였습니다. 누르면 `dstone-boot` 첫 화면(`/views/main`, 왼쪽 메뉴의 "main"과 같은 곳)으로 갑니다. 커밋은 하지 않았습니다.

- **위치**: 두 화면의 공통 헤더(`ai/common/header.jsp`, `knowledge/common/header.jsp`)에 넣어 각 화면의 모든 페이지에 나옵니다.
- **모양**: 그 화면의 메뉴가 아니라 나가는 링크라서, 굵은 글씨에 오른쪽 구분선을 두어 다른 메뉴와 구별했습니다.
- **확인**: 빌드한 war를 띄워 두 화면 모두에서 링크가 보이고, 눌렀을 때 `dstone-boot` 첫 화면이 열리는 것을 확인했습니다.

## 확인 중 알게 된 것

지금 `localhost:7081`에는 제가 띄우지 않은 `dstone-boot`가 응답하고 있습니다. WSL이 mirrored 네트워킹이라 Windows 쪽에서 띄운 인스턴스로 보이며, 건드리지 않았습니다.

- 그 인스턴스에는 이번 수정이 반영되어 있지 않습니다. 화면에서 보시려면 새로 빌드한 war로 다시 띄우셔야 합니다(`mvn clean package`).
- 제 확인은 포트를 7181로 바꿔 따로 띄워서 했고, 끝난 뒤 내렸습니다.
- 지난번 "문서 임베딩 관리" 삭제 확인 때 첫 시도에서 수정 전 화면이 나왔던 원인도 이것으로 보입니다. 당시 재확인은 제 인스턴스가 포트를 잡은 것을 확인한 뒤에 했으므로 그 결과는 유효합니다.

다음부터 같은 착오가 없도록 이 환경 사실을 메모리에 저장했습니다. 문서는 `docs/06.dstone-boot.md`와 `docs/11.dstone-knowledge.md`에 링크 설명을 한 줄씩 추가했습니다.

---

## 질문

<pasted_content id="6b9e">
2026-10-06 01:25:39 ERROR [net.dstone.knowledge.common.exception.ApiExceptionHandler] 처리 중 오류가 났습니다.
org.springframework.http.converter.HttpMessageNotWritableException: Could not write JSON: ServletOutputStream failed to write: java.io.IOException: 현재 연결은 사용자의 호스트 시스템의 소프트웨어의 의해 중단되었습니다
    at org.springframework.http.converter.AbstractJacksonHttpMessageConverter.writeInternal(AbstractJacksonHttpMessageConverter.java:444)
    at org.springframework.http.converter.AbstractSmartHttpMessageConverter.write(AbstractSmartHttpMessageConverter.java:116)
    at org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodProcessor.writeWithMessageConverters(AbstractMessageConverterMethodProcessor.java:342)
    at org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor.handleReturnValue(RequestResponseBodyMethodProcessor.java:213)
    at org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite.handleReturnValue(HandlerMethodReturnValueHandlerComposite.java:77)
    at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:135)
    at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:934)
    at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:853)
    at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:86)
    at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:963)
    at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:866)
    at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1000)
    at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:903)
    at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:649)
    at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:874)
    at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:710)
    at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:132)
    at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:59)
    at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111)
    at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:101)
    at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111)
    at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100)
    at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
    at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111)
    at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93)
    at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
    at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111)
    at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:199)
    at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
    at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111)
    at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:165)
    at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:77)
    at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:535)
    at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:115)
    at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:86)
    at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:71)
    at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:347)
    at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:406)
    at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:71)
    at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:1307)
    at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:2036)
    at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:74)
    at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:949)
    at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:483)
    at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:74)
    at java.base/java.lang.Thread.run(Thread.java:1583)
Caused by: tools.jackson.databind.DatabindException: ServletOutputStream failed to write: java.io.IOException: 현재 연결은 사용자의 호스트 시스템의 소프트웨어의 의해 중단되었습니다
</pasted_content id="6b9e">

---

## 답변

시험용으로 띄운 knowledge(4181 포트)가 아직 살아 있어 정리하는 중입니다.

---

## 답변

이 오류는 `dstone-knowledge`의 처리 실패가 아니라, **호출한 쪽이 응답을 받기 전에 연결을 끊어서** 생긴 것입니다. 서버가 결과를 다 만들어 보내려는데 받을 상대가 이미 없었던 상황입니다.

## 원인

로그 한 건만으로는 어떤 호출이었는지 특정할 수 없습니다. 스택으로 알 수 있는 것은 POST 요청이었다는 것뿐입니다. 가능한 경우는 이렇습니다.

- **제 확인 작업일 가능성**: 로그 시각 `01:25:39`는 콘솔 로그가 12시간제라서 실제로는 13:25:39입니다. 그 무렵 제가 홈 링크 확인용으로 띄웠던 `dstone-boot`(7181 포트)를 강제 종료했는데, 그 인스턴스가 사용자님 쪽 `dstone-knowledge`(4081)를 호출하던 중이었을 수 있습니다. 확인하지는 못했습니다.
- **호출한 쪽의 대기 시간 초과**: `dstone-ai-engine`은 60초, `dstone-boot` 중계는 120초입니다. 임베딩 서버가 바쁘면 검색이 그보다 오래 걸릴 수 있습니다.
- **응답 크기 초과**: 오늘 수정 전의 `dstone-boot` 중계는 256KB까지만 받고 연결을 끊습니다.
- **브라우저를 닫았거나 화면을 떠난 경우**.

같은 오류가 다시 나면 아래 수정 덕분에 어떤 호출이었는지 로그에 남습니다.

## 고친 것

`dstone-knowledge`의 공통 오류 처리(`ApiExceptionHandler`)를 수정했습니다. 커밋은 하지 않았습니다.

- **연결 끊김은 한 줄 경고로만 남깁니다.** 긴 스택과 함께 ERROR로 찍히던 것이 `부른 쪽이 응답을 받기 전에 연결을 끊었습니다: GET /api/...` 형태의 WARN 한 줄이 됩니다.
- **진짜 오류 로그에도 호출 주소가 함께 찍힙니다.**

다른 포트(4181)로 띄워 응답 도중 클라이언트가 끊는 상황을 두 번 만들어 확인했습니다. 두 번 모두 한 줄 경고만 남고 스택은 없었으며, 정상 호출과 404 응답은 그대로입니다. 시험용 프로세스는 내렸습니다.

사용자님이 띄워 둔 `dstone-knowledge`에는 새로 빌드해 다시 띄워야 반영됩니다.

## 함께 발견한 것

모든 모듈의 콘솔 로그 시각이 12시간제(`hh`)로 찍혀 오전·오후를 구분할 수 없습니다. 파일 로그는 24시간제(`HH`)입니다. 각 모듈의 `conf/log4j2.xml`과 k8s configmap 두 곳에서 한 글자씩 바꾸면 되는데, 이번에는 손대지 않았습니다. 원하시면 일괄로 고치겠습니다.

---

## 질문

일괄로 수정해줘. 그리고 git에 올려줘.

---

## 답변

콘솔 로그 시각을 24시간제로 일괄 수정했고, GitHub `main`에 푸시했습니다 (커밋 `d109f40`).

- **수정한 곳**: 6개 모듈의 `conf/log4j2.xml`과 `dstone-boot`·`dstone-ai-engine`의 `k8s/configmap.yaml`, 총 8개 파일에서 콘솔 패턴의 `hh`를 `HH`로 바꿨습니다. 저장소에 12시간제 패턴은 남아 있지 않습니다.
- **함께 올린 것**: 직전에 고친 `dstone-knowledge`의 연결 끊김 로그 처리(`ApiExceptionHandler`)도 같은 커밋에 넣었습니다.
- **확인**: 고친 XML과 YAML이 형식상 깨지지 않았는지만 확인했습니다. 설정 한 글자 변경이라 서버를 띄워 로그를 찍어 보지는 않았습니다.
- **브랜치**: 이 저장소가 `main`에 직접 올리는 방식이라 별도 브랜치 없이 `main`에 커밋했습니다.

오늘 앞서 작업한 내용(노드 맵, MyBatis 전환, RAG 연동, 화면·테이블 정리, 홈 링크)은 이미 `main`에 올라가 있어서, 지금 미커밋 변경은 없습니다.

실행 중인 서버에는 재기동해야 반영되고, kind에 올린 Pod는 configmap을 다시 적용해야 합니다.

---

## 질문

<pasted_content id="6b9e">

 {executed in 7 msec}
2026-10-06 13:35:26  WARN [net.dstone.knowledge.common.exception.ApiExceptionHandler] 부른 쪽이 응답을 받기 전에 연결을 끊었습니다: POST /api/search (부른 쪽의 대기 시간이나 수신 크기 한도를 확인하세요) - HttpMessageNotWritableException: Could not write JSON: ServletOutputStream failed to write: java.io.IOException: 현재 연결은 사용자의 호스트 시스템의 소프트웨어의 의해 중단되었습니다
2026-10-06 14:01:03  WARN [net.dstone.knowledge.rag.EmbeddingWorker] 임베딩 실패(16건, 연속 1번째): ResourceAccessException: I/O error on POST request for "http://localhost:11434/api/embed": Connection reset - 30초 뒤에 다시 시도합니다.
2026-10-06 14:01:06  WARN [com.zaxxer.hikari.pool.HikariPool] HikariPool-1 - Thread starvation or clock leap detected (housekeeper delta=21m59s170ms574µs300ns).
</pasted_content id="6b9e">

---

## 답변

네, 원인을 찾았고 고쳤습니다. 검색 자체가 느린 것이 아니라, **백그라운드 임베딩이 임베딩 서버를 붙잡고 있어서 검색이 그 뒤에 줄을 선 것**이었습니다. 아직 커밋하지 않았습니다.

## 원인

- **임베딩 서버(Ollama)는 요청을 한 번에 하나씩 처리합니다.** 검색 때 필요한 "질문 한 줄 임베딩"은 혼자면 0.1초인데, 대기열을 비우는 작업이 보낸 묶음이 끝날 때까지 기다려야 했습니다.
- **이 PC는 GPU 없이 CPU로 임베딩합니다.** 3,500자 조각 하나에 약 4초가 걸려서, 16건 한 묶음이 66.8초였습니다. Windows 쪽 그래픽은 AMD Radeon 780M 내장형이라 Ollama가 쓰지 못합니다.
- **대기열에 2,400여 건이 남아 있었습니다.** 그동안 검색이 59초에서 100초 이상 걸렸고, `dstone-ai-engine`의 대기 시간 60초를 넘겼습니다.
- **RAG 검색 실패 하나가 워크플로 전체를 중단시켰습니다.** 보내주신 로그의 `pilot-workflow` step01 실패가 이것입니다.

로그의 14:01 두 줄(임베딩 `Connection reset`, Hikari `clock leap 21m59s`)은 별개입니다. PC가 약 22분간 절전 상태였다가 깨어나며 생긴 것으로 보이고, 임베딩 작업은 30초 뒤 스스로 재시도합니다.

## 고친 것

| 어디 | 내용 |
|---|---|
| `dstone-knowledge` | 검색이 진행 중이거나 막 끝난 직후에는 백그라운드 임베딩이 새 묶음을 보내지 않고 양보합니다 (`EmbeddingGate`) |
| `dstone-knowledge` | 임베딩 묶음 크기를 16에서 1로 줄였습니다 (`conf/application.yml`의 `batch-size`). 검색이 기다리는 최대 시간이 한 건(약 4초)으로 줄어듭니다 |
| `dstone-ai-engine` | RAG 검색이 실패해도 경고만 남기고 참고자료 없이 진행합니다. `ragAllowEmptyContext: false`로 설정한 Agent만 실패합니다 |
| `dstone-ai-engine` | 시간 초과일 때 "서버가 떠 있는지 확인" 대신 "N초 안에 답하지 않았습니다"로 안내합니다 |
| `dstone-knowledge` | 연결 끊김 경고에 경과 시간과 호출자를 함께 남깁니다 |

묶음을 1로 줄여도 처리 속도는 같습니다. CPU에서는 16건 66.8초, 4건 16.5초로 건당 시간이 같았기 때문입니다.

## 확인한 것

- **검색 시간**: 같은 대기열이 도는 상태에서 새 `dstone-knowledge`로 검색 8번이 0.9~1.9초였습니다 (수정 전 59~100초).
- **RAG 실패 시 동작**: `dstone-knowledge`에 닿지 못하게 한 상태에서 `ragEnabled` Agent가 "참고자료 없이 답변드립니다"로 정상 응답했고, 경고 로그가 남았습니다.
- 시험은 다른 포트(4181, 8181)에서 했고 프로세스는 모두 내렸습니다. 띄워 두신 서버는 건드리지 않았습니다.

## 남는 한계

- **대기열 처리 자체는 여전히 느립니다.** 큰 조각 기준 분당 15건 정도라 남은 2,400여 건에 2~3시간이 걸리고, 검색이 잦으면 더 늦어집니다. 임베딩을 빠르게 하려면 GPU가 있는 서버가 필요합니다.
- **임베딩이 안 끝난 조각은 뜻으로는 찾히지 않습니다.** 이름 검색으로는 찾힙니다.

띄워 두신 서버에는 새로 빌드해 다시 띄워야 반영됩니다. 문서는 `docs/09`, `docs/11`, `CLAUDE.md`에 원인과 측정값을 기록했습니다. git에 올릴까요?

---

## 질문

현재 임베딩 작업이 얼마나 밀려있고 완료되려면 얼마나 걸리나?

---

## 답변

지금 밀린 임베딩은 **2,174건**(약 329만 자)이고, 다 끝내는 데 **약 1시간~1시간 반**이 걸릴 것으로 추정합니다. 다만 현재는 `dstone-knowledge`가 내려가 있어서 대기열이 전혀 줄지 않고 있습니다.

## 현재 상태

| 항목 | 값 |
|---|---|
| 임베딩 완료 | 14,798건 |
| 대기 중 | 2,430건 (실제 처리할 것 2,174건 + 지워진 리비전의 잔여 256건) |
| 최근 1분간 처리 | 0건 |
| `dstone-knowledge` (4081) | 응답 없음 |
| Ollama | 떠 있으나 올라온 모델 없음 (일하지 않는 상태) |

잔여 256건은 가리키는 문서가 이미 없어진 것이라, 작업이 돌 때 임베딩 없이 대기열에서 빠집니다.

## 대기열에 남은 것

| 프로젝트 | 종류 | 건수 | 평균 글자 수 |
|---|---|---|---|
| `anybiz_prd` | 화면(JSP) | 625 | 3,186 |
| `testApp` | 메소드 | 590 | 926 |
| `anybiz_prd` | 메소드 | 약 240 | 550 |
| `cms4_prd` | 메소드 | 144 | 561 |
| `testApp` | 타입·파일·SQL·화면 | 328 | 458~2,770 |
| `dstone-boot`, `dstone-batchadmin` | 여러 종류 | 약 240 | 373~2,332 |

`anybiz_prd`는 리비전 두 개(28, 42)가 같은 내용을 공유해서 한 번만 임베딩하면 됩니다. 전체 글자 수의 60%가 `anybiz_prd` 화면(JSP) 625건에 몰려 있습니다.

## 소요 시간 추정

- **근거**: 아까 잰 속도가 1,000자당 약 1.2초입니다 (3,500자 조각 16건에 66.8초).
- **계산**: 329만 자 × 1.2초 ÷ 1,000 ≈ 65분입니다.
- **실제**: 검색이 끼어들면 그만큼 늦어지므로 1시간~1시간 반으로 봅니다. 추정치이고 끝까지 돌려서 확인한 값은 아닙니다.

## 진행하려면

`dstone-knowledge`를 띄워 두기만 하면 알아서 처리합니다. 진행 상황은 `GET /api/revisions/{id}/rag`나 코드 분석 화면의 리비전 요약 "임베딩 진행"에서 볼 수 있습니다.

방금 고친 버전(검색 우선 양보, 묶음 크기 1)은 아직 커밋 전입니다. 새 버전으로 띄우면 대기열이 도는 동안에도 검색이 1~2초에 끝납니다. 올려 드릴까요?

---

## 질문

임베딩을 마치려면 dstone-knowledge 서버를 계속 띄워놔야 하나?

---

## 답변

네, 임베딩은 `dstone-knowledge` 서버 안의 백그라운드 작업이 처리하므로 서버가 떠 있는 동안에만 진행됩니다. Ollama도 함께 떠 있어야 합니다.

다만 한 번에 끝까지 띄워 둘 필요는 없습니다.

- **중간에 내려도 잃는 것이 없습니다.** 끝난 조각은 건별로 DB에 저장되고, 대기열도 DB에 있어서 다시 띄우면 남은 것부터 이어서 합니다.
- **나눠서 해도 됩니다.** 오늘 30분, 내일 30분 식으로 띄워도 총량은 같습니다(남은 양은 약 1시간~1시간 반).
- **PC가 절전에 들어가면 멈춥니다.** 깨어나면 30초 뒤 스스로 재시도하므로 따로 조치할 것은 없습니다.
- **Ollama만 내려가 있는 경우**에도 실패로 처리하지 않고 30초마다 다시 시도합니다.

임베딩이 덜 끝난 상태에서도 서비스는 쓸 수 있습니다. 호출 관계, 영향도, 노드 맵은 임베딩과 무관하고, 검색은 이름으로 찾는 쪽이 바로 동작합니다. 뜻으로 찾는 검색만 임베딩이 끝난 조각에서만 결과가 나옵니다.

---

## 질문

알겠어 일단 git 에 올려줘.

---

## 답변

GitHub `main`에 올렸습니다 (커밋 `afd196f`). 지금 미커밋 변경은 없고 로컬과 원격이 같습니다.

- **올린 내용**: `dstone-knowledge`의 검색 우선 양보(`EmbeddingGate`), 임베딩 묶음 크기 1, 연결 끊김 경고 보강과 `dstone-ai-engine`의 RAG 검색 실패 시 계속 진행, 시간 초과 안내 문구, 그리고 관련 문서입니다.
- **원격의 새 커밋과 합쳤습니다.** 원격에 사용자님 커밋(`a1cc3a8`, pilot Agent YAML 4개 수정)이 먼저 올라와 있어서 그 위로 제 커밋을 올렸습니다. 수정한 파일이 겹치지 않아 충돌은 없었습니다.

Windows 쪽에서 받아 새로 빌드해 띄우시면 반영됩니다. 삭제된 파일이 있었으니 `mvn clean package`로 빌드하세요.

---

## 질문

dstone-knowledge, dstone-ai-engine 의 /bin 아래 스크립트를 모두 dstone-boot/bin 아래의 스크립트처럼 만들어줘. 
의도는 /conf/log4j2.xml 의 설정대로 로그를 만들고 싶은 거거든.

---

## 답변

ai-engine을 새 스크립트로 띄워 보는 중인데, 로그가 예상보다 적게 쌓여서 기동이 끝까지 됐는지 다시 확인합니다.

---

## 답변

`dstone-knowledge`와 `dstone-ai-engine`의 `bin` 스크립트를 `dstone-boot/bin`과 같은 모양으로 바꿨습니다. 이제 화면 출력을 버리기 때문에 로그는 `conf/log4j2.xml` 설정대로만 남습니다. 커밋은 하지 않았습니다.

## 바뀐 것

- **스크립트 구성**: 두 모듈 모두 `startApp.sh`, `stopApp.sh`를 `dstone-boot` 방식으로 다시 썼고, Windows용 `startApp.bat`, `stopApp.bat`(프로파일 `local`)을 새로 만들었습니다.
- **로그 위치**: `${APP_HOME}/LOGS/<모듈>/execution/execution.log` 한 곳입니다. 예전의 `logs/<모듈>.out`은 더 만들지 않습니다.
- **PID 파일**: 스크립트가 아니라 프로그램이 뜨면서 `bin/application.pid`에 기록합니다 (`dstone-boot`와 같은 방식).

## dstone-boot 스크립트와 다르게 둔 세 가지

Jenkins 배포가 이 스크립트를 쓰기 때문에 아래는 유지했습니다.

- **어디서 불러도 되도록** 시작할 때 `bin` 폴더로 이동합니다.
- **`DSTONE_PROFILE`로 프로파일을 바꿀 수 있습니다.** 기본은 `wsl`이고 Jenkins는 `vm`으로 부릅니다.
- **`stopApp.sh`가 프로그램이 내려갈 때까지 최대 30초 기다립니다.** 중지 직후 바로 띄울 때 포트가 겹치지 않게 하기 위해서입니다.

`statusApp.sh`는 `dstone-boot`에는 없지만 Jenkins의 상태 확인 단계가 쓰고 있어 그대로 두었습니다. `dstone-knowledge/Jenkinsfile`에서 실패 시 보여 주는 로그 경로도 새 위치로 고쳤습니다.

## 확인한 것

- **`dstone-ai-engine`**: 새 스크립트로 기동·상태·중지를 실제로 돌렸습니다. PID 파일이 `bin`에 생기고, 로그가 `execution.log`에 쌓이며, `.out` 파일은 갱신되지 않았습니다. 중지 후 PID 파일도 지워졌습니다. 8081에는 Windows 쪽에서 띄우신 인스턴스가 있어서 포트만 8181로 바꿔 시험했습니다.
- **`dstone-knowledge`**: 문법 검사만 했습니다. 지금 WSL에서 실행 중인 인스턴스(PID 24862)가 있어 중지 스크립트를 돌리지 않았습니다. 그 인스턴스는 예전 스크립트로 띄운 것이라 다음에 새 스크립트로 띄울 때부터 로그가 한 곳으로 모입니다.
- **`.bat` 파일**: `dstone-boot`의 것을 그대로 따랐고, Windows에서 실행해 보지는 않았습니다.

## 알아두실 점

- **기동 초기 실패는 흔적이 남지 않습니다.** 화면 출력을 버리므로 log4j2가 뜨기 전에 죽는 경우(jar 없음, Java 옵션 오류)는 로그가 없습니다. 그럴 때는 `startApp.sh` 맨 끝의 주석 처리된 명령으로 직접 띄워 보시면 됩니다.
- **저장소 전체 파일의 권한이 바뀌어 있습니다.** 제가 한 변경이 아닌데, 2,709개 파일이 실행 권한(644 → 755)으로 바뀌어 git에 전부 변경으로 잡힙니다. 그대로 `git add .` 하면 모두 커밋됩니다. 의도하신 게 아니라면 `git config core.fileMode false`로 권한 변경을 무시하게 하거나 권한을 되돌리는 편이 좋습니다. 어떻게 할지 알려주시면 처리하겠습니다.

문서는 `docs/03.build.md`, `docs/11.dstone-knowledge.md`, `CLAUDE.md`를 갱신했습니다.

---

## 질문

임베딩 진행현황을 확인할 수 있는 화면이 있나 ?

---

## 답변

네, 있습니다. 다만 리비전 단위로만 볼 수 있고, 전체 대기열을 한눈에 보는 화면은 없습니다.

## 지금 볼 수 있는 곳

- **코드 분석 > 프로젝트 · 분석 > 리비전의 "요약"**
  - 맨 위 "한눈에 보기"에 `임베딩이 N% 진행됐습니다(완료 / 전체)`가 나옵니다.
  - 아래 "임베딩 진행" 표에 DONE / PENDING / FAILED 건수가 나옵니다.
- **코드 분석 > 검색 · 문서**
  - 검색 결과 위 안내 줄에 그 리비전의 임베딩 현황이 함께 표시됩니다.
  - "올린 문서" 목록에는 문서별 조각 수와 "임베딩 끝남" 수가 나옵니다.

방금 조회해 보니 `anybiz_prd` 리비전 42는 완료 9,328건, 대기 636건(약 94%)이고, 작업 스레드가 정상으로 돌고 있습니다(기동 후 231건 처리, 오류 없음).

## 없는 것

- **전체 대기열 합계**: 프로젝트와 리비전을 통틀어 몇 건 남았는지는 화면에 없습니다.
- **작업 상태**: 작업 스레드가 돌고 있는지, 마지막 오류가 무엇인지는 API(`GET /api/revisions/{id}/rag`의 `worker`)에는 있지만 화면에 표시하지 않습니다.
- **자동 갱신과 남은 시간**: 요약은 열 때 한 번 조회하므로, 진행을 보려면 다시 열어야 합니다.

전체 대기열 합계, 작업 상태(실행 여부·마지막 오류), 처리 속도와 예상 남은 시간을 보여 주는 "임베딩 현황" 영역을 프로젝트 · 분석 화면 위쪽에 추가할 수 있습니다. 원하시면 만들겠습니다.

---

## 질문

응 만들어줘

---

## 답변

Writing the backend (status API) and the screen panel.

---

## 답변

화면이 정상으로 그려지고 10초마다 갱신되는 것을 확인했습니다. 화면 모양을 한 번 보고, 시험용 프로세스를 정리한 뒤 문서를 갱신합니다.

---

## 답변

"임베딩 현황" 영역을 코드 분석 > 프로젝트 · 분석 화면 맨 위에 만들었습니다. 프로젝트와 리비전을 통틀어 전체 대기열을 보여 주고 10초마다 스스로 갱신합니다. 커밋은 하지 않았습니다.

## 화면에 나오는 것

- **상태 딱지**: 완료 / 진행 중 / 기다리는 중 / 멈춤, 그리고 상태별 안내 한 줄.
- **진행률 막대와 숫자 카드**: 진행률, 끝난 것, 남은 것, 실패, 최근 속도(건/분), 예상 남은 시간.
- **프로젝트별 남은 것**: 남은 건수, 글자 수, 전체 남은 일에서 차지하는 비율.
- **오류 안내**: 작업의 마지막 오류나 실패 사유가 있으면 표시합니다.
- **"지금 다시 보기" 단추**.

방금 조회한 값은 진행률 89.1%, 완료 15,136건, 남은 것 1,836건, 속도 분당 10.7건, 예상 약 1시간 4분입니다. 남은 일의 47%가 `anybiz_prd`, 38%가 `testApp`입니다.

## 예상 남은 시간 계산 방식

건수가 아니라 글자 수로 계산합니다. 임베딩 한 건의 시간이 길이에 비례해서(500자 0.6초, 3,500자 4초) 건수로 나누면 크게 어긋나기 때문입니다. 최근 10분 동안 처리한 글자 수로 속도를 내고 남은 글자 수를 나눕니다. 검색이 끼어들면 임베딩이 양보하므로 실제로는 더 걸릴 수 있습니다.

## 만든 것

- **`dstone-knowledge`**: `GET /api/system/embedding` (`EmbeddingStatusService`, 조회 쿼리 4개). 응답 시간은 0.4초입니다.
- **`dstone-boot`**: `knowledge/assets/js/embedding.js`, `knowledge/index.jsp`의 영역, 진행률 막대 스타일.

## 확인한 것

새 빌드의 복사본을 다른 포트(4181, 7181)로 띄워 로그인 후 확인했습니다. 카드, 막대, 프로젝트 표가 그려지고 10초 뒤 자동 갱신되며 스크립트 오류는 없습니다. 시험용 인스턴스는 임베딩 작업을 꺼 두어서 상태가 "멈춤"으로 나왔고, "진행 중"과 "기다리는 중" 상태는 화면에서 직접 보지 못했습니다. 시험용 프로세스는 모두 내렸습니다.

## 알아두실 점

- **실행 중인 서버에는 아직 반영되지 않았습니다.** 띄워 두신 `dstone-knowledge`(PID 24862)는 예전 버전이라 이 API가 없습니다. 새 버전으로 다시 띄워야 화면에 현황이 나오고, 그 전에는 "임베딩 현황을 가져오지 못했습니다"로 표시됩니다.
- **빌드로 실행 중인 jar를 교체했습니다.** 이번 작업 중 `mvn clean package`로 `target/dstone-knowledge.jar`와 `dstone-boot.war`를 새로 만들었는데, 그 파일로 서버가 실행 중이었습니다. 지금은 `dstone-knowledge`가 정상 응답하고 임베딩도 진행 중이지만(30초에 5건), 이 상태의 JVM은 이후 오류가 날 수 있습니다. 편하실 때 다시 띄우시는 것을 권합니다. 임베딩은 중단된 지점부터 이어집니다.
- **미커밋 변경이 쌓여 있습니다.** `bin` 스크립트 변경과 이번 임베딩 현황이 함께 있습니다. 앞서 말씀드린 저장소 전체 권한 변경(2,709개 파일)도 그대로라, 올릴 때는 제가 수정한 파일만 골라서 커밋하겠습니다.

문서는 `docs/11.dstone-knowledge.md`와 `CLAUDE.md`를 갱신했습니다. git에 올릴까요?

---

## 질문

지금 로컬에서 dstone-boot => dstone-ai-engine 호출 할 때 dstone-knowledge 를 이용한 RAG 를 동원하도록 의도되어 있는데...
이게 지켜지고 있나?

---

## 답변

연결 자체는 지켜지고 있지만, **의도하신 효과는 나오지 않고 있습니다.** 호출은 `dstone-knowledge`까지 가는데, 가져오는 참고자료가 작업과 무관한 문서입니다.

## 지켜지는 것

- **호출 경로**: `dstone-boot` → `dstone-ai-engine` → `dstone-knowledge` 검색이 실제로 일어납니다. 엔진 설정(`dstone.ai.rag.enabled: true`, knowledge 주소 `localhost:4081`)도 맞습니다.
- **Workflow**: `pilot` Agent 4개(요구사항 분석, 영향도 분석, 설계, 개발)가 `ragEnabled: true`라서 step마다 검색합니다. 아까 보내주신 13:35 로그의 검색 호출이 그 증거입니다.

## 지켜지지 않는 것

- **RAG는 "올린 일반 문서"만 찾습니다.** 분석해 둔 `testApp` 코드(메소드, SQL, 화면)는 RAG 대상이 아닙니다. 그쪽은 knowledge Tool(`knowledgeSearch`, `knowledgeImpact` 등)로만 쓸 수 있는데, `pilot` Agent의 `tools`에는 그 Tool이 하나도 없습니다.
- **올라가 있는 문서가 무관한 1건뿐입니다.** `Java_Application_Knowledge_Graph_RAG_Analysis_Platform_설계서_v1.0.docx`(오늘 13:08 업로드) 하나라서, `pilot` Agent는 `testApp` 오류 수정 작업 중에 이 설계서 조각을 참고자료로 받고 있습니다.
- **검색 질의가 질문이 아니라 입력 JSON입니다.** Workflow step에서는 Agent 입력(`{"workDir": "D:/.../work001[log-error-fix]", "feedback": ""}`)이 그대로 질의가 됩니다. 실제로 이 질의로 검색해 보니 경로에서 뽑힌 `log`, `error`, `fix`, `feedback` 같은 낱말 때문에 설계서 조각 10건이 걸렸고, 그중 5건이 프롬프트에 붙습니다.
- **채팅은 체크박스가 Agent 설정을 덮어씁니다.** "올린 문서 검색(RAG)"을 체크하지 않으면 Agent YAML이 `ragEnabled: true`여도 꺼집니다.

요약하면 지금 RAG는 `pilot` Workflow에 도움이 되기보다 무관한 내용을 프롬프트에 섞고 있습니다.

## 제 쪽 결함 하나

"질문에 든 이름이 글자 그대로 있으면 유사도와 상관없이 남긴다"는 규칙을 제가 넣었는데, 경로 속의 흔한 영어 낱말(`log`, `error`)까지 이름으로 취급되어 무관한 조각을 통과시킵니다. 이 규칙은 고쳐야 합니다.

## 권장 방향

1. **`pilot` Agent에 knowledge Tool을 붙입니다.** 코드 조사에는 이쪽이 맞습니다. 프로젝트 ID(`testApp`)를 주고 검색, 호출 관계, 테이블 사용, 영향도를 Agent가 필요할 때 부르게 합니다.
2. **`pilot` Agent의 `ragEnabled`는 끕니다.** 업무 문서(요건서, 운영 매뉴얼 등)를 올려서 쓸 계획이 생기면 그때 켭니다.
3. **유사도 예외 규칙을 좁힙니다.** 아직 임베딩되지 않은 조각이면서 제목에 이름이 정확히 맞을 때만 예외로 둡니다.

대안으로, RAG가 코드 분석 결과까지 자동으로 찾도록 넓히는 방법도 있습니다(Agent에 프로젝트 ID와 검색 범위 설정 추가). 다만 질의가 입력 JSON인 문제를 함께 풀어야 해서 설계가 더 필요합니다.

권장안(1~3)으로 진행할까요? 3번은 어느 쪽을 택하시든 고치겠습니다.

---

## 질문

1번 대로만 해도 의도한 결과 나오지 ?

---

## 답변

절반만 나옵니다. 1번만 하면 Agent가 코드 분석 결과를 **쓸 수 있게는** 되지만, 아래 세 가지가 함께 맞아야 의도한 결과가 됩니다.

## 1번만으로 부족한 이유

- **무관한 설계서가 계속 프롬프트에 붙습니다.** `ragEnabled`가 켜져 있는 한, 지금 올라가 있는 유일한 문서(knowledge 설계서) 조각이 step마다 참고자료로 들어갑니다. 그래서 2번(`ragEnabled` 끄기)이나 3번(예외 규칙 좁히기) 중 적어도 하나는 같이 해야 합니다.
- **Tool을 붙여도 Agent가 쓰도록 지시해야 합니다.** Tool 목록에 넣는 것만으로는 LLM이 기존 방식(`searchInFiles`로 파일 뒤지기)을 그대로 쓸 수 있습니다. 프롬프트에 "호출 관계와 영향 범위는 knowledge Tool로 먼저 확인하라"는 규칙과 프로젝트 ID(`testApp`)를 넣어야 합니다.
- **`testApp`의 임베딩이 거의 안 끝났습니다.** 방금 확인하니 949건 중 31건만 완료입니다. 그래서 "뜻으로 찾는 검색"은 지금 거의 빈 결과입니다. 이름으로 찾기, 호출 관계, 테이블 사용, 영향도는 임베딩과 무관해서 바로 쓸 수 있습니다.

## 그리고 한계 하나

knowledge의 분석 결과는 11:06에 분석한 시점의 스냅샷입니다. 개발 Agent가 소스를 고친 뒤에는 다시 분석하기 전까지 고친 내용이 반영되지 않습니다. 원인 조사와 영향도 분석(수정 전 단계)에는 문제가 없고, 수정 후 검증 단계에서는 주의가 필요합니다.

## 제안

1번에 아래를 묶어서 하는 것을 권합니다.

- `pilot` Agent 4개(조사, 요구사항, 영향도, 설계)에 knowledge Tool을 붙이고, 프롬프트에 사용 규칙과 프로젝트 ID를 넣습니다.
- 같은 Agent들의 `ragEnabled`는 끕니다.
- 유사도 예외 규칙을 좁힙니다(제 결함 수정).

이렇게 진행할까요? 프로젝트 ID는 Agent YAML에 `testApp`으로 고정할지, Workflow 입력(요청서)에서 받게 할지도 정해 주시면 좋습니다. 저는 요청서의 '소스경로' 옆에 프로젝트 ID를 적게 하는 쪽을 권합니다. 다른 프로젝트에도 같은 Agent를 그대로 쓸 수 있기 때문입니다.

---

## 질문

응 진행해주고, Workflow 입력(요청서)에 프로젝트ID를 적게 해.

---

## 답변

Now editing the pilot Agents.

---

## 답변

Agent가 knowledge Tool을 실제로 쓰는 것까지 확인했습니다. 시험용 프로세스를 정리하고 문서를 갱신한 뒤 정리해서 알려드리겠습니다.

---

## 답변

`pilot` Agent가 코드 분석 결과를 knowledge Tool로 쓰도록 바꿨고, 프로젝트 ID는 요청서에서 받게 했습니다. 실제 호출로 Agent가 knowledge Tool을 쓰는 것까지 확인했습니다. 커밋은 하지 않았습니다.

## 요청서에 추가할 내용

`00-request.md`에 아래 항목을 넣으시면 됩니다. 요청서 파일은 제가 수정하지 않았습니다.

```markdown
## 프로젝트ID
testApp
```

이 항목이 없으면 Agent는 knowledge Tool을 쓰지 않고 예전처럼 파일 Tool로만 조사합니다.

## 바뀐 것

| Agent | knowledge Tool | `ragEnabled` |
|---|---|---|
| 소스 조사 (Sub Agent) | 검색, 메서드 찾기, 호출자, 피호출자, 테이블 사용 | 꺼짐 (원래 꺼져 있었음) |
| 요구사항 분석 | 위와 같음 | 끔 |
| 영향도 분석 | 위 + 영향도 | 끔 |
| 설계 | 위 + 영향도 | 끔 |
| 개발 | 없음 | 끔 |
| 영향도 리뷰 | 없음 (그대로) | 꺼짐 (그대로) |

- **프롬프트 규칙**: 각 Agent에 "knowledge Tool 사용 규칙"을 넣었습니다. knowledge 결과는 어디를 볼지 정하는 데만 쓰고, 문서에 적는 사실과 줄 번호는 여전히 파일을 열어 확인하게 했습니다. Tool이 실패하면 반복하지 않고 파일 Tool로 넘어갑니다.
- **Sub Agent 전달**: 영향도 분석 Agent가 소스 조사를 맡길 때 프로젝트 ID를 함께 넘깁니다.
- **개발 Agent에는 Tool을 주지 않았습니다.** 소스를 고치는 순간부터 분석 결과가 낡고, 무엇을 고칠지는 설계서에 이미 있기 때문입니다.
- **RAG 예외 규칙을 좁혔습니다.** 임베딩이 끝난 조각은 유사도로만 판단합니다.

## 확인한 것

소스 조사 Agent에 `testApp`의 "로그인이력" 진입점을 물었습니다.

- **knowledge가 떠 있을 때**: `knowledgeSearch`를 2번 쓰고, 화면 파일·URL·Controller·Service·DAO·쿼리ID·테이블을 줄 번호와 함께 찾았습니다 (2분 16초).
- **knowledge가 응답하지 않을 때**: Tool이 실패하자 파일 Tool로 넘어가 같은 결과를 냈습니다 (3분 21초).

`pilot-workflow` 전체는 돌리지 않았습니다. 사용자님 작업 폴더에 산출물을 쓰기 때문입니다. 요구사항·영향도·설계 Agent는 기동 검증과 Tool 등록까지만 확인했습니다.

## 정정할 것

앞서 "무관한 설계서가 붙는 것은 제가 넣은 예외 규칙 탓"이라고 말씀드렸는데, 정확하지 않았습니다. 다시 보니 그 조각들은 유사도가 0.46~0.55로 기준(0.35)을 넘어서 정상 통과한 것이었습니다. 실제 원인은 질의가 Agent 입력 JSON이었던 점과 기준값이 낮은 점입니다. 예외 규칙은 약속드린 대로 좁혔지만, 이 문제를 해결한 것은 `ragEnabled`를 끈 쪽입니다.

## 알아두실 점

- **지금 4081의 `dstone-knowledge`가 응답하지 않습니다.** WSL에서 띄우셨던 인스턴스는 14:57에 정상 종료된 기록이 있고, 임베딩은 15:20 이후 멈춰 있습니다 (남은 것 843건). knowledge Tool을 쓰려면 다시 띄우셔야 합니다.
- **미커밋 변경이 쌓여 있습니다.** `bin` 스크립트, 임베딩 현황 화면, 이번 `pilot` Agent 변경이 함께 있습니다.

문서는 `docs/09.dstone-ai-engine.md`와 `CLAUDE.md`를 갱신했습니다. git에 올릴까요?

---

## 질문

응 올려줘

---

## 답변

GitHub `main`에 올렸습니다 (커밋 `4302f90`). 지금 미커밋 변경은 없고 로컬과 원격이 같습니다.

- **이번 커밋에 들어간 것**: `pilot` Agent 5개와 Workflow 설명, RAG 예외 규칙 수정(`RagRetrievalChain`), 문서 2개(`docs/09`, `CLAUDE.md`)입니다. 총 9개 파일입니다.
- **앞선 작업은 이미 올라가 있었습니다.** `bin` 스크립트 변경과 임베딩 현황 화면은 그사이 사용자님 커밋(`2392bcb`)에 포함되어 원격에 있습니다. 저장소 전체의 파일 권한 변경도 그 커밋에 함께 들어간 것으로 보입니다.

Windows 쪽에서 받아 `mvn clean package`로 빌드해 다시 띄우시고, 요청서(`00-request.md`)에 아래 항목을 넣으시면 `pilot-workflow`가 knowledge Tool을 쓰기 시작합니다.

```markdown
## 프로젝트ID
testApp
```

`dstone-knowledge`도 함께 띄워 두셔야 Tool이 응답합니다. 떠 있지 않으면 Agent는 파일 Tool로만 조사합니다.

---

## 질문

testApp의 경우 분석을 로컬에서 진행하다보니 소스폴더가 D:/AppHome/testApp 로 잡혀있는데 이게 서버에서 서비스 될 때 문제가 될 수 있는가?

---

## 답변

조회에는 문제가 없고, **서버에서 다시 분석할 때만** 문제가 됩니다.

## 문제가 없는 부분

이미 분석해 둔 결과를 조회하는 기능은 소스 폴더를 다시 읽지 않습니다. 검색, 호출 관계, 영향도, 노드 맵, 노드 상세, knowledge Tool 모두 DB에 저장된 결과만 사용합니다.

- **저장된 경로는 모두 상대 경로입니다.** `testApp` 리비전 43을 확인해 보니 파일 경로가 `src/main/java/kr/co/gnx/...` 형태이고, `D:/`나 절대 경로로 저장된 것은 0건입니다.
- **소스 내용도 DB에 들어 있습니다.** 검색 문서와 노드 상세에 보이는 소스는 분석 시점에 복사해 둔 것입니다.

그래서 같은 DB를 바라보는 서버라면 `D:/AppHome/testApp`이 없어도 현재 리비전은 그대로 서비스됩니다.

## 문제가 되는 부분

- **서버에서 분석을 다시 돌릴 때**: 분석 시작 시 등록된 소스 폴더가 그 서버에 실제로 있는지 확인합니다. Linux 서버에는 `D:/AppHome/testApp`이 없으므로 "프로젝트의 소스 경로가 없거나 폴더가 아닙니다"로 거절됩니다. 증분 분석도 마찬가지입니다.
- **해결 방법**: 서버에 소스를 내려받고, 같은 projectId로 소스 폴더만 서버 경로로 다시 등록하면 됩니다 (같은 ID로 등록하면 수정). 기존 리비전과 심볼 ID는 상대 경로 기준이라 그대로 이어집니다.
- **증분 분석의 효과가 줄 수 있습니다.** Windows에서 받은 소스와 서버에서 받은 소스의 줄바꿈(CRLF/LF)이 다르면 파일 체크섬이 달라져, 첫 증분 분석은 사실상 전체 분석이 됩니다. 오류는 아니고 시간만 더 걸립니다.

## 함께 봐야 할 것

`pilot` Workflow의 요청서에 적는 '소스경로'는 knowledge와 별개로, `dstone-ai-engine`이 파일을 직접 열 때 쓰는 경로입니다. 엔진이 Linux 서버에서 돌면 `D:/AppHome/testApp`을 열 수 없으므로, 요청서의 소스경로도 그 서버에서 보이는 경로로 적어야 합니다. knowledge가 돌려주는 경로는 상대 경로라 Agent가 소스경로와 이어 붙여 쓰는 데는 문제가 없습니다.
