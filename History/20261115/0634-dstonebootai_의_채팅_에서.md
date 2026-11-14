# dstonebootai_의_채팅_에서

> 생성일시: 2026-11-15 06:34

## 질문

dstone-boot>ai 의 채팅 에서
Tool 찾기 팝업을 열면 보여지는 소스 RAG, 문서 RAG, MCP, 일반 을 나누는 기준이 뭐야?
어떤 항목(컬럼)을 보고 구분해?

---

## 답변

Now find where MCP category is assigned and the default.

---

## 답변

DB 컬럼이 아닙니다. dstone-ai-engine이 Tool마다 붙여 보내는 **`category` 값** 하나로 나눕니다. 이 값은 Tool 클래스에 붙은 `@AiTool` 애노테이션의 `category` 속성에서 나옵니다.

## 값이 정해지는 곳

`dstone-ai-engine/src/main/java/net/dstone/ai/common/config/ConfigTool.java:98-117`에서 부팅할 때 Tool 이름별로 `Map<String, ToolCategory>`를 만듭니다.

| 화면 표 | `category` 값 | 정해지는 방식 |
|---|---|---|
| 소스 RAG | `CODE_RAG` | 클래스에 `@AiTool(category = ToolCategory.CODE_RAG)`를 붙임. 지금은 `KnowledgeTool`만 해당 (`tools/knowledge/KnowledgeTool.java:50`) |
| 문서 RAG | `DOC_RAG` | `@AiTool(category = ToolCategory.DOC_RAG)`를 붙임. 지금은 `KnowledgeDocTool`만 해당 (`tools/knowledge/KnowledgeDocTool.java:38`) |
| MCP | `MCP` | 애노테이션에 적지 않습니다. `ConfigMcp`가 MCP 서버에서 받아 온 Tool이면 `ConfigTool`이 자동으로 `MCP`를 붙입니다 |
| 일반 | `GENERAL` | `@AiTool`의 기본값입니다. category를 적지 않았거나 `@AiTool`이 아예 없으면 이 값이 됩니다 |

종류는 **클래스 단위**로 정해집니다. 그래서 같은 클래스 안의 `@Tool` 메소드는 모두 같은 종류로 묶입니다. 예를 들어 `KnowledgeTool` 안의 `knowledgeSearch`, `knowledgeCallers`, `knowledgeTableUsage` 등은 전부 소스 RAG에 들어갑니다.

## 화면까지 가는 경로

1. 엔진의 `ConfigTool.categoryOf(toolName)`이 종류를 찾습니다. 모르는 이름이면 `GENERAL`을 돌려줍니다.
2. 엔진은 `GET /api/ai/chat` 응답의 `availableTools[].category`에 이 값을 담아 보냅니다(`api/dto/ToolSummary`).
3. dstone-boot는 이 값을 `ToolSummaryResult.category`로 받아 그대로 전달합니다.
4. 화면에서는 `dstone-boot/src/main/webapp/ai/assets/js/tool-picker.js:19-25`의 `CATEGORIES` 표가 코드를 표 이름으로 바꿉니다. 이 표에 없는 값은 "일반"으로 처리합니다.
   - 같은 표에는 다섯 번째 종류 `UNKNOWN`("등록되지 않음")도 있습니다. Agent 파일에는 적혀 있지만 엔진에 없는 Tool을 따로 모아 보여 줍니다.

새 Tool을 소스 RAG나 문서 RAG 표에 넣으려면 그 Tool 클래스의 `@AiTool`에 `category`를 지정하면 됩니다. 이 값은 화면에서 묶어 보여 줄 때만 씁니다. Tool 실행이나 화이트리스트 검사에는 영향이 없습니다(`ToolCategory` JavaDoc).
