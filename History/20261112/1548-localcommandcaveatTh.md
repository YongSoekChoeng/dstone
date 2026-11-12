# localcommandcaveatTh

> 생성일시: 2026-11-12 15:48

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
"대한민국 수도가 어디야?" 라는 질문을 했을 때 대답이 나오는게 정상인가?

---

## 답변

지금은 검색 API 코드를 보면서 결과를 거르는 최소 유사도 기준이 있는지 확인하고 있습니다.

---

## 답변

네, 지금 구조에서는 정상입니다. 다만 그 화면은 질문에 **대답하는** 곳이 아니라 **비슷한 청크를 순서대로 나열하는** 곳이라서, 나온 결과는 서울이라는 답이 아니라 질문과 가장 덜 먼 코드나 문서 몇 건일 겁니다.

**이유**

- 화면(`dstone-boot/.../knowledge/assets/js/search.js:66`)은 `POST /api/search`를 부르고 받은 hits를 목록으로 그립니다. LLM이 답을 만드는 단계는 없습니다.
- 벡터 검색 SQL(`dstone-knowledge/src/main/resources/sqlmap/analysis/RagDao.xml:583`)은 유사도 순으로 정렬해서 `LIMIT #{topK}`만 걸고, 최소 유사도 조건은 없습니다. 그래서 어떤 질문을 넣어도 가장 가까운 topK건은 항상 나옵니다.
- 이름 검색(KEYWORD)은 질문에 클래스·테이블 같은 이름이 없으면 거의 아무것도 찾지 못합니다. 그래서 이번 결과는 대부분 VECTOR 쪽에서 나왔을 겁니다.

**확인하는 법**

각 결과의 `유사도` 값과 `나온 곳`을 보면 됩니다. 관련 없는 질문이면 유사도가 낮게 나오고 `나온 곳`은 `VECTOR`만 찍혀 있을 겁니다. 제대로 맞은 결과는 대개 `VECTOR+KEYWORD`이고 유사도도 높습니다.

**고칠지 여부**

"관련 없으면 결과 없음"이 나오길 원하면 최소 유사도 기준을 둘 수 있습니다. 예를 들면 `dstone.knowledge.rag.search.min-score`를 `RagSettings`로 읽어 `searchChunks`에 `AND 1 - (e.embedding <=> …) >= #{minScore}`를 넣고, 걸러져서 0건이면 화면에 "질문과 가까운 내용이 없습니다"를 띄우는 방식입니다. 기준값은 쓰는 임베딩 모델에 따라 다르니 실제 유사도 분포를 보고 정해야 합니다. 원하시면 이 방향으로 작업하겠습니다.

---

## 질문

응 진행해줘. 그리고 현재 사용하는 모델은 bge-m3 야.

---

## 답변

min-score 작업을 시작합니다. 먼저 검색 서비스와 설정 코드를 보겠습니다.

---

## 답변

WSL 쪽에는 4081이 떠 있지 않습니다. 그래서 Ollama로 질문을 직접 임베딩한 뒤 DB에서 유사도를 재 보겠습니다.

---

## 답변

jq가 없어서 측정 스크립트를 python으로 바꿔 다시 돌립니다.

---

## 답변

Controller, SearchQuery, SearchService를 고칩니다.

---

## 답변

이제 설정 파일과 화면, ai-engine 쪽을 맞춥니다.

---

## 답변

Build 확인과 문서 위치 확인을 같이 진행합니다.

---

## 답변

Docs를 갱신합니다.

---

## 답변

컴파일은 통과했습니다. 이제 jar를 만들어 다른 포트(4181)에서 띄우고 실제 API로 확인하겠습니다.

---

## 답변

모든 경우가 의도대로 동작합니다. 시험 인스턴스를 내립니다.

---

## 답변

작업을 마쳤습니다. 이제 "대한민국 수도가 어디야?"를 물으면 0건이 나오고, 화면에 "질문과 가까운 내용이 없습니다"가 뜹니다. 4181 포트에 새 jar를 따로 띄워 API로 확인했고, dstone-boot 화면에서 직접 눌러 보지는 않았습니다.

**기준값 정하기 (bge-m3로 실측)**

| 질문 | 1등 유사도 |
|---|---|
| 상관없는 질문 6개 (수도, 날씨, 김치찌개, 주식, "hello how are you" 등) | 0.34~0.47 |
| 관련 있는 질문 12개 (로그인 인증, 세션 저장, 분석 작업 취소 등) | 0.51~0.72 |

이 결과로 **코드는 0.5**로 정했습니다. **올린 문서는 0.35**로 따로 두었습니다. ai-engine RAG를 만들 때 사람이 쓴 문서는 관련 있는 것도 0.48처럼 0.5를 못 넘는 경우가 있었기 때문입니다.

**바뀐 동작**
- 뜻으로 찾은 결과 중 유사도가 기준보다 낮은 것은 버립니다.
- 이름으로 찾은 결과는 이름이 글자 그대로 맞은 것이라 거르지 않습니다. 예를 들어 질문에 `CustomAuthenticationProvider`를 섞으면 그 클래스는 유사도가 0.49여도 그대로 나옵니다.
- 검색 요청에 `minScore`를 주면 기준이 그 값으로 바뀌고, 0을 주면 거르지 않습니다. 0~1 밖의 값은 400 오류입니다.
- 응답에 `minScore`(적용한 기준), `belowMinScore`(버린 건수), `bestBelowMinScore`(버린 것 중 최고 유사도)가 추가됩니다. 화면은 이 값으로 결과가 없는 이유를 보여 줍니다.
- 거르기는 SQL이 아니라 Java에서 합니다. SQL에서 거르면 상관없는 질문일 때 HNSW 반복 스캔이 인덱스를 끝까지 훑을 수 있기 때문입니다.

**수정한 파일**
- **dstone-knowledge**: `RagSettings`, `SearchQuery`, `SearchController`, `SearchService`, `conf/application.yml`의 `rag.search.min-score.code` / `.document`
- **dstone-boot**: `knowledge/assets/js/search.js`(0건일 때 안내), `search.jsp`(도움말 한 줄)
- **dstone-ai-engine**: `RagRetrievalChain`이 자기 기준값 0.35를 `minScore`로 보내도록 했습니다. 서버 기준에 먼저 잘리지 않게 하려는 것입니다. `KnowledgeTool`은 서버 기준을 그대로 따라서, 상관없는 질문이면 "검색 결과가 없습니다"를 받습니다.
- **문서**: `docs/11` 0.10절·4.3절·6.1절(측정표 포함), `docs/09`, `.claude/docs/knowledge-search-incremental.md`

**확인 결과** (4181 포트, testApp)
- "대한민국 수도가 어디야?" → 0건 (10건 버림, 최고 0.37)
- "로그인 인증 처리하는 곳" → 10건 (1등 0.60)
- 같은 질문에 이름을 섞으면 → 이름으로 찾은 5건이 나옵니다
- `minScore: 0` → 예전처럼 10건
- `minScore: 2` → 400 오류

**남은 일**
- 실제로 반영하려면 dstone-knowledge(4081)를 다시 띄워야 합니다. dstone-boot와 dstone-ai-engine은 Pod 재배포가 필요합니다. 셋 다 아직 하지 않았습니다.
- 기준값은 bge-m3로 잰 값이라, 임베딩 모델을 바꾸면 다시 재야 합니다.
- 올린 문서 기준 0.35는 지금 DB에 올린 문서가 없어서 직접 재지는 못했습니다. ai-engine RAG에서 쓰던 값을 그대로 가져온 것입니다.
- 커밋은 하지 않았습니다.
