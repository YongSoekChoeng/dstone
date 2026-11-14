# localcommandcaveatTh

> 생성일시: 2026-11-14 12:29

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

---

## 질문

<pasted_content id="304f">
1. PDF 처리: 북마크로 제목 경로를 붙이는 방식으로 진행해도 될까요? 샘플 가이드 파일을 먼저 주시면 그것으로 검증하겠습니다.
    => 북마크로 제목 경로를 붙이는 방법이라는게 정확하게 뭔지 모르겠어. 그리고 그 영향도가 뭔지도.
2. Agent의 문서 선택: 묶음 + 문서 ID로 고르기, Agent의 삭제는 빼기. 이렇게 진행해도 될까요?
    => OK.    내가 말한 삭제는 선택한 묶음 + 문서 ID 를 비선택으로 돌리는 기능을 이야기 한거야.
</pasted_content id="304f">

---

## 답변

## 북마크로 제목 경로를 붙이는 방법

PDF 뷰어 왼쪽에 보이는 목차 패널이 북마크입니다. PDF 안에는 북마크마다 "제목"과 "몇 쪽으로 가는지"가 저장되어 있습니다. 예를 들어 넥사크로 가이드라면 이런 모양일 것입니다.

```
3. Grid                 → 40쪽
   3.1 개요             → 40쪽
   3.2 속성             → 42쪽
       3.2.1 autofittype → 45쪽
       3.2.2 binddataset → 47쪽
```

이 정보로 "45~46쪽은 `3. Grid > 3.2 속성 > 3.2.1 autofittype` 절"이라는 것을 알 수 있습니다. 이를 이용해 청크를 다음처럼 만듭니다.

**지금**
```
[문서] 넥사크로 개발 가이드 (37/412)
...Grid 열 너비를 자동으로 맞춥니다... 3.2.2 binddataset ... (절 두 개가 섞이고 1,800자에서 잘림)
```

**바뀐 뒤**
```
[문서] 넥사크로 개발 가이드 > 3. Grid > 3.2 속성 > 3.2.1 autofittype (45쪽)
...Grid 열 너비를 자동으로 맞춥니다...
```

- 청크는 절 경계에서 나뉩니다. 한 절이 1,000자를 넘을 때만 그 안에서 문단 단위로 더 자르고, 잘린 조각에도 같은 제목 경로를 붙입니다.
- 절이 쪽 중간에서 시작하면, 그 쪽 안에서 북마크 제목과 같은 줄을 찾아 거기서 나눕니다. 그 줄을 못 찾으면 쪽 단위로 나눕니다.
- PDF 글은 지금처럼 Tika로 뽑고, 북마크만 PDFBox로 읽습니다. 둘 다 이미 들어 있는 라이브러리입니다.

## 영향

**좋아지는 점**
- **검색 정확도:** 청크마다 주제가 하나라서 임베딩이 흐려지지 않습니다. "그리드 열 너비 자동 맞춤"으로 물으면 머리말의 `Grid`, `autofittype`도 검색에 걸립니다.
- **답변 근거:** LLM이 "가이드 3.2.1절(45쪽)"처럼 출처를 댈 수 있고, 사람이 원문을 찾아 확인하기도 쉽습니다.
- **앞뒤 청크 Tool:** "같은 절의 다음 조각"을 정확히 가져올 수 있습니다.

**바뀌는 범위**
- 일반 문서 업로드의 청킹만 바뀝니다. 코드 분석 청크와 검색 방식은 그대로입니다.
- 이미 올린 문서는 옛 방식 청크로 남아 있으므로, 새 방식을 쓰려면 다시 올려야 합니다. 그때 임베딩도 다시 하며, 이 PC(CPU)에서는 1,000자 청크 하나에 약 1초가 걸립니다.

**한계**

| 상황 | 결과 |
|---|---|
| 북마크가 없는 PDF | 쪽 번호만 붙이고 문단 단위로 자름. 지금보다는 낫지만 제목 경로는 없음 |
| 북마크가 장 단위로만 있음 | 제목 경로도 장 단위까지만 |
| PDF 안의 표, 스크린샷 | 표는 줄 단위 글로 풀리고, 그림 속 글자는 못 읽음. 이 방법으로도 해결되지 않음 |
| DOCX | 북마크가 아니라 Word 제목 스타일(`h1`~`h6`)로 같은 머리말을 만듦 |

실제 가이드 PDF에 북마크가 몇 단계까지 들어 있는지에 따라 효과가 달라집니다. 그래서 샘플 파일로 먼저 보자고 말씀드린 것입니다.

## "비선택으로 돌리기"

말씀하신 뜻으로 이해했습니다. Tool은 상태를 서버에 저장하지 않고, 검색할 때마다 "이번에 볼 묶음과 문서 ID"를 받는 방식으로 하려 합니다. 그러면 비선택은 다음 검색부터 그 ID를 빼고 부르는 것으로 끝납니다.

- 한 Agent 안에서는 대화 기록이 현재 선택을 기억합니다.
- 여러 Step에 걸쳐 유지해야 하면 선택 목록을 `state`에 저장해 넘깁니다(`save`).

이 방식이면 선택 상태를 따로 저장하는 테이블이나 API가 필요 없습니다. 같은 문서를 동시에 쓰는 다른 Workflow에도 영향이 없습니다.

## 다음 단계

1. 북마크 방식으로 진행해도 될지 정해 주세요. 가능하면 실제 가이드 PDF/DOCX 파일 경로도 알려 주세요. 파일이 있으면 북마크 단계와 추출 결과를 먼저 보여 드리겠습니다.
2. 선택/비선택을 위 방식(검색할 때마다 넘기기)으로 해도 될지 알려 주세요. 서버가 선택을 기억하는 방식을 원하시면 그렇게 설계하겠습니다.

---

## 질문

<pasted_content id="304f">
"비선택으로 돌리기"
말씀하신 뜻으로 이해했습니다. Tool은 상태를 서버에 저장하지 않고, 검색할 때마다 "이번에 볼 묶음과 문서 ID"를 받는 방식으로 하려 합니다. 
그러면 비선택은 다음 검색부터 그 ID를 빼고 부르는 것으로 끝납니다.
==>> 이게 무슨 말인지 모르겠어.
</pasted_content id="304f">

---

## 답변

설명이 추상적이었습니다. 예를 들어 다시 설명드리겠습니다.

## 지금 생각한 방식: 검색할 때마다 범위를 적어 보낸다

Agent는 Tool을 부를 때 "이 문서들 안에서만 찾아 줘"라고 매번 함께 적어 보냅니다. 서버는 그 범위를 기억하지 않습니다.

```
① knowledgeSearch(query="Grid 열 너비 자동 맞춤", 범위=[nexacro-guide, websquare-guide])
   → 넥사크로 가이드와 웹스퀘어 가이드에서 찾음

② knowledgeSearch(query="Dataset 행 추가", 범위=[nexacro-guide])
   → 웹스퀘어 가이드를 빼고 넥사크로 가이드에서만 찾음
```

②에서 웹스퀘어 가이드를 "비선택"하는 별도 동작은 없습니다. 범위에 안 적으면 그것이 비선택입니다. 웹 검색창에서 필터를 매번 체크해서 보내는 것과 같습니다.

## 다른 방식: 서버가 선택 목록을 기억한다

장바구니처럼, 서버에 "지금 선택된 문서" 목록을 두고 Agent가 넣고 뺍니다.

```
① selectDocuments([nexacro-guide, websquare-guide])   → 선택 목록에 두 개
② knowledgeSearch(query="Grid 열 너비 자동 맞춤")      → 선택 목록 안에서 찾음
③ unselectDocuments([websquare-guide])                → 선택 목록에서 빼기(말씀하신 "비선택")
④ knowledgeSearch(query="Dataset 행 추가")            → 이제 넥사크로 가이드만
```

이 방식은 "선택 목록이 누구의 것인지"를 정해야 합니다. 이 실행만의 것인지, 이 Agent의 것인지, 이 사용자의 것인지에 따라 저장할 곳이 생기고, 실행이 끝나면 지우는 처리도 필요합니다.

## 확인하고 싶은 것

말씀하신 "선택·비선택"을 **누가, 언제** 하는지에 따라 맞는 방식이 달라집니다.

| 누가 고르나 | 예 | 맞는 방식 |
|---|---|---|
| 실행 중인 Agent(LLM)가 스스로 | 질문을 보고 "넥사크로 질문이니 넥사크로 가이드만" | 첫 번째(매번 적어 보내기) |
| Agent·Workflow를 만드는 사람이 정의 파일에 | "이 Agent는 항상 넥사크로 가이드만 본다" | 첫 번째. 정의 파일이나 Workflow 입력에 범위를 적어 둠 |
| 사람이 실행 전에 화면에서 체크박스로 | 실행 화면에서 문서를 골라 켜고 끔 | 두 번째(서버가 기억) |

어떤 경우를 생각하고 계셨는지 알려 주세요. 여러 개라면 모두 알려 주셔도 됩니다.

---

## 질문

응 이해했어. 선택은 LLM 스스로 하는게 맞을 것 같아. 그럼 Agent 에서 rag 기능을 체크해 놓으면 어떤 문서에도 제약없이 LLM이 판단해서 필요한 문서의 정보를 조회해 오는건가?

---

## 답변

현재 Agent 정의에서 RAG를 켜면 실제로 어떻게 동작하는지 코드로 확인하고 답하겠습니다.

---

## 답변

아닙니다. 지금 RAG를 쓰는 길은 두 가지이고, 동작 방식이 다릅니다. "LLM이 판단해서 필요한 문서를 조회"하는 것은 그중 Tool 쪽입니다. 코드로 확인한 내용입니다.

## 1. Agent의 `ragEnabled`(체크박스): 자동 첨부, LLM이 판단하지 않음

`RagRetrievalChain`이 Spring AI Advisor로 붙어서 이렇게 동작합니다.

1. LLM을 부르기 **직전에** 엔진이 사용자 메시지를 그대로 검색어로 씁니다.
2. 올린 문서 **전체**에서 비슷한 청크 상위 5개를 찾습니다(`sourceTypes=DOCUMENT`, 범위 지정 없음).
3. 찾은 청크를 프롬프트에 붙여서 LLM을 한 번 부릅니다.

그래서 이런 한계가 있습니다.
- LLM은 무엇을 찾을지 고를 수 없고, 다시 찾을 수도 없습니다. 검색은 한 번뿐입니다.
- 넥사크로 질문에 웹스퀘어 가이드 청크가 섞여 들어올 수 있습니다.
- Workflow Step처럼 사용자 메시지가 길면(이전 결과, 지시문 포함) 검색어 자체가 흐려집니다.

## 2. `searchDocuments` / `knowledgeSearch` Tool: LLM이 판단

Agent의 `tools.allowed`에 넣으면, LLM이 필요하다고 판단할 때 직접 검색어를 만들어 부릅니다. 결과가 부족하면 검색어를 바꿔 여러 번 부를 수도 있습니다. 다만 지금은 이런 제약이 있습니다.
- 범위를 지정할 수 없어서 늘 전체 문서에서 찾습니다.
- 어떤 문서가 올라와 있는지 LLM이 알 방법이 없습니다.
- 검색된 조각의 앞뒤를 더 볼 수 없고, 결과는 800자에서 잘립니다.

## 이번 작업으로 달라지는 것

말씀하신 "제약 없이, LLM이 판단해서"는 2번 Tool 경로를 보강하는 것입니다. 앞서 합의한 작업이 바로 그 내용입니다.

| 추가하는 것 | LLM이 할 수 있게 되는 일 |
|---|---|
| 문서 목록 Tool | "넥사크로 가이드, 웹스퀘어 가이드가 있구나" 하고 파악 |
| 검색 Tool의 범위(묶음/문서 ID) | 질문에 맞는 묶음만 골라 찾기. 비선택은 다음 호출에서 빼기 |
| 앞뒤 청크 Tool | 답이 조각 경계에 걸려 있으면 같은 절의 다음 조각 더 읽기 |
| 청크 약 1,000자 + 제목 경로 | 잘리지 않은 결과와 "어느 절인지" 정보를 받기 |

이렇게 되면 Agent에 이 Tool들을 허용해 두는 것만으로, 어떤 문서든 LLM이 스스로 골라서 조회합니다. 범위는 올린 문서 전체이고, 인증을 켜면 같은 API 키로 올린 문서 전체입니다.

`ragEnabled` 체크박스는 간단한 채팅용 자동 첨부로 그대로 둡니다. 가이드 문서를 근거로 개발하는 Agent라면 체크박스보다 Tool 방식을 권합니다. Agent 프롬프트에 "넥사크로 관련 질문은 먼저 문서 목록을 보고 해당 가이드에서 찾아라" 정도의 지시를 넣으면 됩니다.

이 방향으로 진행할까요? 남은 결정은 PDF 북마크 방식(앞서 설명드린 것)을 쓸지와, 실제 가이드 샘플 파일이 있는지입니다.

---

## 질문

Agent 에서 호출 하게 될 Tool 기능 한번 더 확인할께.
searchDocuments : 어떤 문서들이 있는지 확인. 여기서의 문서범위는 Document만 인건가?
knowledgeSearch : Document + 소스 합쳐서 호출 인건가?

---

## 답변

현재 코드 기준으로 정확히 확인하겠습니다.

---

## 답변

두 가지 모두 조금 다릅니다. 현재 코드 기준으로 정리하면 다음과 같습니다.

## searchDocuments: 문서 목록이 아니라 문서 **내용 검색**입니다

- **범위:** 올린 문서(DOCUMENT)만 찾습니다. 소스 코드는 찾지 않습니다(`RagRetrievalChain.search`가 `sourceTypes=DOCUMENT`로 고정).
- **프로젝트:** 프로젝트를 가리지 않고 올린 문서 전체에서 찾습니다.
- **돌려주는 것:** 검색어와 가까운 청크의 본문입니다. "어떤 문서들이 있는지" 알려 주는 기능은 아닙니다. 지금은 그런 Tool이 **없습니다**.
- **조건:** `dstone.ai.rag.enabled=true`여야 동작합니다. 꺼져 있으면 `실패:`를 돌려줍니다.

## knowledgeSearch: 기본은 **소스만**, 옵션을 켜면 소스 + 문서입니다

- `projectId`가 필수이고, 기본으로는 그 프로젝트의 분석 결과(메소드, 타입, SQL, 화면)만 찾습니다.
- `includeDocuments=true`로 부르면 문서도 같이 찾습니다. 다만 **그 프로젝트에 붙여 올린 문서만** 찾습니다. 프로젝트 없이 올린 넥사크로 가이드는 여기서 나오지 않습니다.
- 소스와 문서가 한 순위로 섞여서 나옵니다.

## 정리와 제안

| Tool | 지금 | 이번 작업 후(제안) |
|---|---|---|
| (새로 추가) 문서 목록 | 없음 | 올린 문서와 묶음 목록. LLM이 "무엇이 있는지" 파악할 때 씀 |
| `searchDocuments` | 문서만, 전체 범위에서 검색 | 문서만 찾는 것은 그대로. **묶음/문서 ID 범위 파라미터 추가**, 결과에 문서 ID·청크 번호·제목 경로 포함 |
| (새로 추가) 앞뒤 청크 | 없음 | 문서 ID + 청크 번호로 이웃 조각 가져오기 |
| `knowledgeSearch` | 소스 중심, 옵션으로 프로젝트 문서 | 그대로(소스 분석용) |

역할을 이렇게 나누면 Agent가 헷갈리지 않습니다. 가이드 문서는 `searchDocuments` 계열로 찾고, 소스는 `knowledgeSearch`로 찾습니다. 가이드와 소스를 같이 봐야 하는 Agent에는 두 Tool을 모두 허용하면, LLM이 질문에 따라 골라 부릅니다.

이 구성으로 진행해도 될까요? PDF 북마크 방식을 쓸지와 샘플 가이드 파일이 있는지도 함께 알려 주시면 2번(구조 인식 청킹)부터 시작하겠습니다.

---

## 질문

진행하기에 앞서서 확인할께. 현재 Agent 에서 knowledgeSearch 를 찾을때 소스+문서 옵션은 뭐야?

---

## 답변

Agent 정의나 프롬프트에서 이 옵션을 쓰는 곳이 있는지 확인하겠습니다.

---

## 답변

Agent 설정 항목이 아니라, **`knowledgeSearch` Tool의 파라미터 `includeDocuments`**입니다.

```java
// KnowledgeTool.java:102
@ToolParam(description = "올린 일반 문서(설계서 등)도 같이 찾을지. 기본 false", required = false) Boolean includeDocuments
```

## 동작

- **누가 정하나:** LLM이 Tool을 부를 때마다 직접 채웁니다. Agent 정의 파일(front matter)에서 켜고 끄는 항목은 없습니다.
- **기본값:** `false`입니다. LLM이 값을 안 주면 소스만 찾습니다.
- **`true`일 때:** knowledge에 `sourceTypes=[CODE, DOCUMENT]`를 보내 소스와 문서를 한 순위로 섞어 찾습니다. 문서는 **그 `projectId`에 붙여 올린 것만** 대상입니다.

## 실제로는 거의 쓰이지 않습니다

`knowledgeSearch`를 허용한 Agent는 pilot 계열 다섯 개입니다(developer, architect, newdev-reference, source-investigator, impact-analyzer, requirment-analyzer). 그런데 이 Agent들의 프롬프트 어디에도 `includeDocuments`를 쓰라는 지시가 없습니다. 그래서 LLM은 보통 이 값을 비워 두고, 지금 Agent들은 사실상 **소스만** 찾고 있습니다.

예를 들어 넥사크로 가이드를 프로젝트 없이 올렸다면, LLM이 `includeDocuments=true`로 불러도 그 가이드는 나오지 않습니다.

그래서 앞서 제안한 구성에서는 `knowledgeSearch`를 소스 분석용으로 그대로 두고, 가이드 문서는 범위를 지정할 수 있는 `searchDocuments` 계열로 찾게 하려는 것입니다.

---

## 질문

<pasted_content id="304f">
그럼 이렇게 정리하면 어떨까?

1. includeDocuments => 삭제
2. 문서 맵핑 projectId 기능 => 삭제. (화면에서도 삭제) 프로젝트별로 묶이는 문서는 없음.
3. Agent 설정 화면에서 ragEnabled 체크박스 삭제. (RAG 관련 Tool 만 세팅되면 RAG 검색 가능)
4. RAG 관련 Tool 정리
    knowledgeSearchDocList => (새로 추가) 문서 목록. 올린 문서와 묶음 목록. LLM이 "무엇이 있는지" 파악할 때 씀 
    knowledgeSearchDocs => 문서만 찾는 것은 그대로. 묶음/문서 ID 범위 파라미터 추가, 결과에 문서 ID·청크 번호·제목 경로 포함. (searchDocuments 를 knowledgeSearchDocs로 리펙토링.)
    knowledgeGetChunks => 문서 ID + 청크 번호로 이웃 조각 가져오기
    knowledgeSearch => 그대로(소스 분석용) 
</pasted_content id="304f">

---

## 답변

좋은 정리입니다. 다만 2번과 3번은 지우는 범위가 넓어서, 실제로 무엇이 함께 지워지는지 먼저 확인하겠습니다.

---

## 답변

전체 방향은 좋습니다. 역할이 "소스는 `knowledgeSearch`, 문서는 문서 Tool"로 깔끔하게 나뉩니다. 다만 사용처를 확인해 보니, 2번과 3번은 생각보다 지우는 범위가 넓어서 미리 말씀드립니다.

## 1. `includeDocuments` 삭제: 그대로 진행

Tool 파라미터만 지웁니다. knowledge의 `/api/search`에 있는 `sourceTypes`는 boot 검색 화면이 쓰고 있어서 API에는 남겨 둡니다.

## 2. 문서의 projectId 연결 삭제: 진행하되 두 가지를 같이 처리

- **DB 컬럼은 남깁니다.** `rag_document.project_id`는 코드 분석 결과가 쓰는 컬럼입니다. 업로드할 때 값을 넣지 않고, 검색할 때 문서 범위에서 프로젝트 조건만 뺍니다.
- **이미 프로젝트에 붙여 올린 문서**가 있으면, 그 연결(`project_id`)을 비우는 정리 SQL을 함께 돌립니다.
- 업로드 화면에서는 프로젝트 선택 칸을 지우고, 그 자리에 **묶음 이름** 입력 칸을 둡니다.

## 3. RAG 체크박스 삭제: 실제로 지워지는 것은 "자동 첨부 기능" 전체

지금 이 기능은 두 곳에 있습니다.
- **Agent 정의:** `context.sources: [retrievedDocuments]`와 `context.retrieval.{topK, similarityThreshold, allowEmptyContext}`. 디자이너의 Agent 폼에 이 항목들이 있습니다.
- **채팅 화면:** "올린 문서 검색(RAG)" 체크박스

말씀대로 Tool만으로 RAG를 하려면, 자동 첨부 장치(`RagRetrievalChain`의 Advisor)를 통째로 지우는 게 맞습니다. 함께 정리할 것은 다음과 같습니다.

| 구분 | 대상 |
|---|---|
| ai-engine 코드 | `AgentDefinition`/`AgentRegistry`/`AgentExecutor`의 retrieval 처리, `ChatRequest.ragEnabled`, `RagRetrievalChain`, `RagSearchTool` |
| boot 화면 | 채팅 화면 체크박스, `ChatService`, 디자이너 Agent 폼의 retrieval 항목 |
| 정의 파일 | `sample-rag-demo-agent`, `sample-agent-rag-augmented`, `sample-tool-rag-search`(새 Tool로 바꾸거나 삭제) |
| 설정·문서 | `dstone.ai.rag.*` 설정, docs 06/09/11 |

**알아 두실 차이:** 자동 첨부는 매번 반드시 검색했지만, Tool 방식에서는 LLM이 검색을 건너뛸 수 있습니다. 가이드를 반드시 참고해야 하는 Agent에는 이미 있는 `execution.requiredTools: [knowledgeSearchDocs]`를 걸면 됩니다. 그러면 한 번도 검색하지 않은 응답은 실패로 처리되어 다시 시도합니다.

**채팅 화면에서 문서를 찾으려면:** 체크박스 대신 "도구호출"을 켜고, 문서 Tool이 허용된 Agent를 고르면 됩니다.

## 4. Tool 정리: 그대로 진행

네 개 모두 `KnowledgeTool`에 둡니다. 이제 `dstone.ai.rag.enabled` 설정 없이, knowledge 주소만 있으면 동작합니다.

한 가지만 제안합니다. `knowledgeSearchDocList`는 검색이 아니라 목록이니 `knowledgeListDocs`가 LLM에게 덜 헷갈릴 것 같습니다. 원하시는 이름으로 하겠습니다.

## 진행 순서

1. **knowledge:** 구조 인식 청킹(DOCX 제목 스타일, PDF 북마크, 약 1,000자), 묶음 필드, projectId 연결 제거, 목록·이웃 청크 API
2. **ai-engine:** Tool 네 개 정리, 자동 첨부(RAG Advisor) 제거, 샘플 정의 정리
3. **boot:** 업로드 화면(묶음 입력), 채팅 체크박스와 디자이너 retrieval 항목 제거
4. **문서 갱신과 검증:** 실제 업로드 → Agent 호출까지

**정해 주실 것 두 가지**
- PDF 북마크 방식으로 진행해도 될까요? 샘플 가이드 파일이 있으면 경로를 알려 주세요.
- 목록 Tool 이름은 `knowledgeSearchDocList`와 `knowledgeListDocs` 중 어느 쪽으로 할까요?

정해 주시면 1단계부터 시작하겠습니다.
