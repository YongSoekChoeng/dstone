# localcommandcaveatTh

> 생성일시: 2026-11-14 10:43

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="304f">
dstone-boot>knowledge 에서
검색 · 문서> 문서 올리기 에 대해서 물어볼께.
나는 넥사크로나 웹스퀘어 개발가이드등을 PDF나 DOC파일로 확보해서 문서를 올리고 dstone-ai-engine에서 rag로 활용하려고 해.
그러려면 청킹을 제대로 해야 효율적인 rag기능으로서 활용될텐데....
현재 구현된 기능에서 해당 요구를 제대로 만족시킬 수 있는지 확인해줘.
</pasted_content id="304f">

---

## 답변

현재 문서 올리기 구현을 확인해 보겠습니다.

---

## 답변

Now let me check how search and the ai-engine side use these chunks.

---

## 답변

코드 구조는 파악했습니다. 실제 PDF/DOCX에서 Tika가 뽑아 주는 글 모양이 청킹 품질을 좌우하므로, 직접 돌려서 확인해 보겠습니다.

---

## 답변

python-docx로 개발 가이드 모양의 DOCX를, PDFBox로 PDF를 만들어 실제 추출·청킹 결과를 보겠습니다.

---

## 답변

PDFBox 버전이 섞여 있는 게 눈에 띕니다(`pdfbox-2.0.27` + `pdfbox-tools-3.0.7`, Tika 3.3.1은 PDFBox 3.x를 씁니다). 실제로 PDF가 읽히는지 확인이 필요합니다.

---

## 답변

Now a Java program that builds a PDF and runs extraction + chunking the same way the app does.

---

## 답변

PDF는 아예 읽히지 않습니다. 원인을 확인하겠습니다.

---

## 답변

PDFBox 3.0.7로 바꿔 끼우면 PDF가 어떻게 뽑히는지도 확인해 보겠습니다(시험용 클래스패스에서만).

---

## 답변

마지막으로 ai-engine 쪽에서 검색된 청크를 어떻게 받아 쓰는지(앞뒤 조각을 더 가져올 수 있는지) 보겠습니다.

---

## 답변

## 결론

지금 구현으로는 이 용도를 충분히 받쳐 주지 못합니다. PDF는 아예 올라가지 않고, DOCX는 올라가긴 하지만 문서 구조를 무시하고 잘립니다. 게다가 ai-engine에서 꺼내 쓸 때도 문서 내용이 잘려서 LLM에 전달됩니다.

시험용으로 개발 가이드 모양의 DOCX와 PDF를 만들고, 앱 jar에 든 라이브러리 그대로 `DocumentTextExtractor`와 같은 방식으로 추출한 뒤 `TextChunker.split(text, 1800, 200)`으로 잘라 봤습니다.

## 1. PDF 업로드가 실패합니다 (가장 심각)

```
java.lang.NoSuchFieldError: Class org.apache.pdfbox.io.MemoryUsageSetting does not have member field ... streamCache
    at org.apache.tika.parser.pdf.PDFParser.parse(PDFParser.java:191)
```

- **원인:** `dstone-common/pom.xml:257`이 `pdfbox 2.0.27`을 직접 지정하고 있습니다. 그런데 Tika 3.3.1의 PDF 파서는 PDFBox 3.x가 있어야 동작합니다. 현재 knowledge jar 안에는 `pdfbox-2.0.27`, `fontbox-2.0.27`이 들어 있고 `pdfbox-io`는 아예 없습니다.
- 이 오류는 `Exception`이 아니라 `Error`입니다. 그래서 `UploadDocumentService`의 catch(IOException/TikaException)에 걸리지 않고 500 오류로 끝납니다.
- 시험용 클래스패스에서만 PDFBox 3.0.7로 바꿔 끼워 보니 정상적으로 추출됐습니다(5,211자, 청크 3개).
- `dstone-ai-engine` jar에도 똑같이 2.0.27이 들어 있습니다. `dstone-common`의 `FileUtil`이 PDFBox 2 API를 쓰고 있어서, 버전을 올리려면 그 코드도 함께 고쳐야 합니다.

## 2. DOCX는 읽히지만 장·절 구조가 반영되지 않습니다

Tika가 Word 문단을 **줄바꿈 하나**로만 구분해서 넘깁니다. 그런데 `TextChunker`는 **빈 줄**을 문단 경계로 봅니다. 그래서 시험 문서 전체에서 문단이 4개로만 잡혔고, 결국 "1,800자를 채우면 줄 경계에서 자르기"와 다를 바 없이 동작했습니다.

| 시험 결과 | 영향 |
|---|---|
| 청크 #1 끝이 `...섹션3 문단2.⏎4. 트랜잭션 4`로 끝남 | 장 제목은 앞 청크에 붙고, 그 장의 본문은 다음 청크로 넘어감 |
| 청크 하나에 2~3개 장이 섞임 | 임베딩 벡터가 흐려져 검색 정확도가 떨어짐 |
| 표가 `enableevent⏎boolean⏎이벤트 발생 여부`처럼 셀 단위로 흩어짐 | 속성표(속성/타입/설명)의 행 관계가 사라짐 |
| 이어 붙일 문단 하나가 거의 1,800자라 overlap(200자 이하 문단만 반복)이 동작하지 않음 | 경계에 걸친 내용은 한쪽 청크에서만 찾힘 |
| 청크 머리말이 `[문서] 제목 (n/m)`뿐임 | 개발 가이드에서 중요한 "Grid > 속성 > autofittype" 같은 위치 정보가 없음 |

코드 예제(`this.ds_list.addRow()`)도 1,800자 경계에 걸리면 중간에서 잘립니다.

## 3. ai-engine에서 쓸 때의 문제

- **청크 뒷부분이 잘립니다.** `dstone.ai.tool.knowledge.max-content-chars: 800`이라서, 최대 1,800자인 문서 청크도 LLM에는 앞 800자만 전달됩니다.
- **프로젝트에 붙이지 않은 문서는 검색되지 않습니다.** `knowledgeSearch`는 항상 `projectId`를 보내고, `RagDao.xml`의 `documentScope`는 `d.project_id = #{projectId}` 조건으로 거릅니다. 그래서 넥사크로 가이드처럼 여러 프로젝트가 같이 쓰는 문서를 프로젝트 없이 올리면 Tool 검색에 나오지 않습니다.
- 문서 검색은 기본으로 꺼져 있습니다(`includeDocuments` 기본값 false). 켜면 코드 청크와 같은 순위 경쟁을 합니다. `docTypes=UPLOAD`로 문서만 골라 찾을 수는 있습니다.
- 검색된 청크의 앞뒤 조각을 더 가져오는 Tool이 없습니다.
- 그 밖에, 스캔한 PDF의 OCR과 가이드 속 스크린샷 내용은 지원하지 않습니다. 둘 다 이미 알려진 제약입니다.

## 잘 되어 있는 부분

업로드 → 우선 임베딩(priority 10) → 하이브리드 검색으로 이어지는 흐름 자체는 갖춰져 있습니다. `addRow`, `transaction` 같은 영문 API 이름은 이름 검색에도 걸리고, 같은 이름으로 다시 올리면 교체되며, 파일은 50MB·500만 자까지 받습니다.

## 손볼 순서 제안

1. **PDFBox 3.x로 정리(필수):** `dstone-common`을 3.0.x로 올리고 `FileUtil`을 3.x API에 맞춥니다. 그리고 추출 단계에서 `Error`도 잡아 400으로 돌려줍니다.
2. **구조를 인식하는 청킹:** Tika의 XHTML 출력(`h1`~`h6`, `p`, `table`, `pre`)을 받아 제목 단위로 섹션을 나눕니다. 청크 머리말에 제목 경로를 붙이고, 표는 행 단위로 유지하며, 코드 블록은 자르지 않습니다. PDF는 제목 태그가 나오지 않으니 북마크(outline)를 쓰거나, 가이드를 Markdown으로 변환해 올리는 경로(`.md` 제목 기준 청킹)를 함께 두는 편이 현실적입니다.
3. **ai-engine 연동:**
   - 문서 청크 크기와 `max-content-chars`를 맞춥니다(예: 문서 청크 약 1,000자).
   - `projectId`가 있을 때 프로젝트 없는 공용 문서도 검색에 포함합니다.
   - 필요하면 앞뒤 청크를 가져오는 Tool을 추가합니다.

1번은 작고 확실한 수정이니 바로 진행할 수 있습니다. 2번은 청킹 방식이 바뀌는 일이라 범위(PDF 북마크까지 할지, Markdown 경로만 둘지)를 정해 주시면 그에 맞춰 설계하겠습니다.

---

## 질문

<task-notification>
<task-id>byzmka1u0</task-id>
<tool-use-id>toolu_01KxYBeF1qwUGY7PEn2NR8M3</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/ab482fe1-ee63-43b9-81aa-d3de5035fbe9/tasks/byzmka1u0.output</output-file>
<status>failed</status>
<summary>Background command "시험용 문서와 변환 도구 찾기" failed with exit code 1</summary>
</task-notification>

---

## 답변

앞서 백그라운드로 돌린 시험용 문서 검색 명령이 실패로 끝났다는 알림이 왔습니다. 그 뒤에 시험 문서를 직접 만들어 확인했으니 위 결론은 그대로입니다.

1번(PDFBox 정리)부터 진행할지, 2번 청킹 범위를 어디까지 할지 정해 주시면 이어서 작업하겠습니다.
