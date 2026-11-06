# localcommandcaveatTh

> 생성일시: 2026-11-06 12:35

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-boot>knowledge 의 graph 에서 관계트리 따로보기 팝업에서 
화면 을 클릭했을 때 보여지는 우측 상세설명에서 [소스보기]버튼을 클릭하면 보여지는 소스가 전체를 담고있는 것은 아닌 것 같은데, 의도된 건가?

---

## 답변

RAG 문서에서 소스를 꺼내는 구조라, 화면(VIEW) 문서를 만드는 쪽에서 잘라내는지 확인하고 있습니다.

---

## 답변

네, 잘린 게 맞습니다. 다만 의도해서 자른 건 검색(RAG)용 문서이고, 팝업이 그 문서를 그대로 가져다 쓰다 보니 같이 잘린 것입니다. 팝업 입장에서는 의도하지 않은 부작용으로 보는 게 맞습니다.

**원인**

1. [소스 보기]의 내용은 원본 파일이 아닙니다. 분석 때 만들어 둔 설명 문서(RAG 청크)에서 "소스:" 뒤쪽을 잘라 온 것입니다. `GraphService.putDocument()`가 `selectDocumentChunks` 결과를 `DocumentSections.split()`으로 나눕니다.
2. 화면(VIEW) 문서는 만들 때부터 길이를 제한합니다. `ResourceDocumentBuilder`의 내용은 이렇습니다.
   ```java
   /** 화면 문서 하나의 최대 청크 수. JSP는 대부분 HTML이라 길게 담아도 검색에 보탬이 적고 임베딩 시간만 늘어납니다. */
   private static final int MAX_VIEW_CHUNKS = 2;
   ...
   List<String> contents = documents.splitByLines(sb.toString(), chunkMaxChars * 2);   // 1800 * 2
   if (contents.size() > MAX_VIEW_CHUNKS) { contents = contents.subList(0, MAX_VIEW_CHUNKS); }
   ```
   빈 줄과 들여쓰기를 뺀 뒤에도 앞의 설명 부분까지 합쳐 **약 7,200자까지만** 남고 나머지는 버려집니다.
3. 팝업에는 "빈 줄과 들여쓰기를 뺀 내용입니다"라는 안내만 나오고, 잘렸다는 표시는 없습니다. 게다가 줄 범위는 `1 ~ 전체 줄 수`로 나와서 파일 전체를 보여 주는 것처럼 보입니다.

참고로 Java 클래스와 메소드 문서(`CodeDocumentBuilder`)에는 이런 청크 수 제한이 없어서 소스가 다 들어 있습니다. 이번 문제는 화면(JSP·WebSquare·Nexacro)에만 해당합니다.

**고치는 방법 (제안)**

검색용 문서는 지금처럼 짧게 둡니다. 화면 노드의 [소스 보기]만 원본 파일을 직접 읽게 바꿉니다. `putMethodTitles()`가 이미 같은 방식으로 원본을 읽고 있습니다. 프로젝트 `localPath`에서 파일을 찾고, checksum이 맞을 때만 인코딩을 판별해서 읽습니다. 이 방식이면 들여쓰기와 빈 줄도 원래대로 보입니다. 파일이 없거나 분석 뒤에 바뀌었으면 지금처럼 문서 속 소스를 보여 주고, 앞부분만이라는 사실을 팝업에 적으면 됩니다.

이렇게 고칠까요?
