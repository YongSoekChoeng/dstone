# localcommandcaveatTh

> 생성일시: 2026-11-14 10:58

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

---

## 질문

<pasted_content id="304f">
1. PDFBox 3.x로 정리(필수): dstone-common을 3.0.x로 올리고 FileUtil을 3.x API에 맞춥니다. 그리고 추출 단계에서 Error도 잡아 400으로 돌려줍니다.
    => OK.    
2. 구조를 인식하는 청킹: Tika의 XHTML 출력(h1~`h6, p, table, pre)을 받아 제목 단위로 섹션을 나눕니다. 청크 머리말에 제목 경로를 붙이고, 표는 행 단위로 유지하며, 코드 블록은 자르지 않습니다.
  PDF는 제목 태그가 나오지 않으니 북마크(outline)를 쓰거나, 가이드를 Markdown으로 변환해 올리는 경로(.md` 제목 기준 청킹)를 함께 두는 편이 현실적입니다.
    => Tika 의 기능만으로 넥사크로/웹스퀘어 가이드 문서(PDF/DOCX)등을 올려서 RAG로 활용가능한가? 
3. ai-engine 연동:
   - 문서 청크 크기와 max-content-chars를 맞춥니다(예: 문서 청크 약 1,000자).
    => OK.    
   - projectId가 있을 때 프로젝트 없는 공용 문서도 검색에 포함합니다.
    => projectId 말고도 문서ID 같은 키 값을 Agent에서 선택,삭제 할 수 있게 해서 복수개의 자료를 rag로 선택할 수 있게 하는건 어때? 
   - 필요하면 앞뒤 청크를 가져오는 Tool을 추가합니다.
    => OK.    
</pasted_content id="304f">

---

## 답변

먼저 2번 질문에 근거를 갖고 답하려고, Tika가 DOCX와 PDF에서 구조 정보(제목, 표, 북마크)를 실제로 얼마나 넘겨주는지 확인하겠습니다.

---

## 답변

`FileUtil`에서 고칠 곳은 `PDDocument.load` 두 군데뿐입니다. dstone-boot에 들어 있던 `pdfbox-tools 2.0.26`이 어디서 오는지도 확인합니다.

---

## 답변

코드를 고칩니다.

---

## 답변

Now catching `Error` during extraction in the upload service. Let me check how ai-engine also uses Tika.

---

## 답변

ai-engine은 지금 Tika를 직접 쓰지 않으니 손댈 곳은 knowledge의 추출기뿐입니다.

---

## 답변

빌드하고 의존성 트리를 확인합니다(실행 중인 jar를 덮어쓰지 않도록 knowledge는 `package` 대신 compile·classpath 확인만 합니다).

---

## 답변

dstone-boot에서 문제가 하나 생깁니다. `tess4j 5.4.0`이 `pdfbox-tools 2.0.26`을 끌고 오는데, 이제 core는 3.0.7이 됩니다. boot가 tess4j로 PDF를 다루는지 확인합니다.

---

## 답변

tess4j는 boot pom에 선언만 되어 있고 Java 코드에서는 쓰지 않습니다. 버전이 섞인 채로 남기지 않도록 tess4j에서 PDFBox 2.x 도구를 빼겠습니다.

---

## 답변

이제 다섯 모듈을 모두 컴파일해 보고, knowledge의 실제 클래스패스로 PDF 추출을 다시 시험합니다.

---

## 답변

PDF 추출과 `FileUtil`의 PDF→이미지 변환 모두 정상입니다. 중간에 잘린 PDF도 400 경로(TikaException)로 가는지 확인합니다.

---

## 답변

1번 작업은 끝났고 검증도 마쳤습니다. 관련 문서에 PDFBox 버전 규칙을 한 줄 추가합니다.

---

## 답변

## 1번 PDFBox 정리: 완료

| 파일 | 변경 |
|---|---|
| `dstone-common/pom.xml` | `pdfbox` 2.0.27 → 3.0.7. Tika 3.3.1이 쓰는 버전과 맞췄습니다. |
| `dstone-common/.../FileUtil.java` | `PDDocument.load(file)` → `Loader.loadPDF(file)` (2곳) |
| `dstone-knowledge/.../DocumentTextExtractor.java` | 파서에서 나는 `RuntimeException`, `LinkageError`, `StackOverflowError`를 `TikaException`으로 바꿔 500 대신 400을 돌려줍니다. 라이브러리 문제일 때는 "서버 설정 문제"라고 알립니다. |
| `dstone-boot/pom.xml` | tess4j가 끌어오던 `pdfbox-tools`/`pdfbox-debugger` 2.0.26을 뺐습니다. 그대로 두면 3.0.7과 섞입니다. tess4j는 선언만 되어 있고 코드에서는 쓰지 않습니다. |
| `.claude/docs/knowledge-search-incremental.md` | PDFBox 버전 규칙 한 줄 추가 |

**확인한 것**
- 다섯 모듈 모두 컴파일됩니다.
- knowledge의 실제 클래스패스로 돌려 보니 PDF는 5,211자, 북마크가 있는 PDF는 724자, DOCX는 6,587자가 정상적으로 추출됐습니다.
- `FileUtil.convertPdfToBufferedImage`도 3쪽짜리 PDF를 이미지 3장으로 정상 변환했습니다.

**아직 하지 않은 것**
- 커밋과 재배포는 하지 않았습니다. 떠 있는 4081 인스턴스에는 다시 빌드·배포해야 반영됩니다.
- 유닛 테스트는 돌리지 않았습니다.

## 질문 2: Tika 기능만으로 PDF/DOCX 가이드를 RAG로 쓸 수 있나?

**DOCX는 됩니다. PDF는 Tika만으로는 부족하지만, Tika에 이미 들어 있는 PDFBox로 메울 수 있습니다.** 새 라이브러리는 필요 없습니다. 아래는 Tika가 만든 XHTML을 직접 열어 확인한 결과입니다.

| | DOCX | PDF |
|---|---|---|
| 장·절 제목 | `<h1>`, `<h2>`로 나옴. 단, Word의 "제목" 스타일을 쓴 문서일 때만 | 태그 없음. 일반 `<p>`와 구별되지 않음 |
| 표 | `<tr>`/`<td>`로 행이 그대로 남음 | 줄 단위 텍스트로 풀림 |
| 코드 예제 | 문단 스타일 이름(`class="no_Spacing"` 등)으로만 구별됨. 가이드마다 이름이 다름 | 그냥 줄 |
| 쪽 구분 | 없음 | `<div class="page">` |
| 북마크(목차) | 해당 없음 | 문서 끝에 `<ul><li>` 목록으로만 나오고, **쪽 번호가 없음** |

PDF는 PDFBox로 북마크를 직접 읽으면 "이 쪽은 `3. Grid > 3.2 속성`에 속함"을 알 수 있습니다. 그러면 Tika가 쪽 단위로 나눠 준 글에 제목 경로를 붙일 수 있습니다. 북마크가 없는 PDF는 쪽 번호만 붙이고 문단 단위로 자릅니다. 다만 PDF 안의 표 구조와 스크린샷 내용은 이 방법으로도 살릴 수 없습니다.

넥사크로·웹스퀘어 가이드 PDF에 북마크가 들어 있는지는 실제 파일을 봐야 압니다. 파일 하나만 주시면 추출 결과를 먼저 보여 드리겠습니다. 북마크가 있다면 Markdown 변환 경로는 따로 만들지 않아도 됩니다.

## 질문 3: Agent가 문서 ID를 골라 여러 자료를 RAG로 쓰게 하면?

좋은 방향입니다. 다만 세 가지를 조정하면 좋겠습니다.

1. **선택 단위로 문서 묶음을 둡니다.** 업로드할 때 묶음 이름(예: `nexacro-guide`)을 붙이면, Agent는 PDF 열 개의 ID 대신 묶음 하나만 고르면 됩니다. 묶음이나 문서 ID로 고르면 프로젝트와 상관없이 찾을 수 있습니다. 그래서 앞서 말한 "프로젝트 없는 공용 문서 포함" 항목은 이것으로 대신합니다.
2. **Tool은 세 개입니다.**
   - 목록: 올린 문서와 묶음 목록을 돌려줍니다.
   - 검색: 묶음이나 문서 ID를 지정해 그 안에서만 찾습니다.
   - 앞뒤 청크: 이미 OK하신 항목입니다.
3. **Agent에게 삭제는 맡기지 않는 편을 권합니다.** 올린 문서는 모든 Workflow가 같이 쓰기 때문에, LLM이 한 번 잘못 판단하면 다른 작업까지 자료를 잃습니다. 삭제는 화면에서만 하도록 두는 게 안전합니다. "선택에서 빼기"라면 저장할 상태 없이 다음 호출에서 빼고 부르면 됩니다. Workflow 동안 선택을 유지해야 하면 Workflow 입력이나 `state`에 담으면 됩니다.

## 정해 주실 것

1. **PDF 처리:** 북마크로 제목 경로를 붙이는 방식으로 진행해도 될까요? 샘플 가이드 파일을 먼저 주시면 그것으로 검증하겠습니다.
2. **Agent의 문서 선택:** 묶음 + 문서 ID로 고르기, Agent의 삭제는 빼기. 이렇게 진행해도 될까요?

정해 주시면 2번(구조 인식 청킹)과 3번(청크 약 1,000자, 문서 선택, 앞뒤 청크 Tool)을 함께 구현하겠습니다. 같은 코드를 건드리는 작업들이라 한 번에 하는 편이 낫습니다.
