# localcommandcaveatTh

> 생성일시: 2026-11-12 15:40

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
