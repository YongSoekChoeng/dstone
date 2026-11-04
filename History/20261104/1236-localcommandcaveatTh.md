# localcommandcaveatTh

> 생성일시: 2026-11-04 12:36

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

windows 에서 진행한 workflow 실패건에 대해 원인 분석해줘. "실행 중 예외가 발생했습니다(executionId=e3457418-43e9-4d59-9606-d0aeca6f9136)
java.lang.IllegalStateException: No ToolCallback found for tool name: searchInFiles..."

---

## 답변

## 원인 분석: `e3457418-…` 실행 실패

**결론: LLM이 자기에게 주지 않은 Tool(`searchInFiles`)을 불렀고, Spring AI가 이를 예외로 던져 Workflow 전체가 FAILED가 됐습니다.** 엔진의 Tool 설정이나 코드가 잘못된 것은 아닙니다.

### 실패 과정
Windows 로그 `D:/AppHome/framework/dstone/LOGS/dstone-ai-engine/execution/execution.log`의 215151행 부근입니다.

1. `sm-workflow`의 `step13`(`sm-developer-agent`)이 실행됐습니다. 모델은 OpenRouter `qwen/qwen3.8-27b`입니다.
2. 1~2회차에는 허용된 `readFileLines`로 참고 소스 7개를 읽었습니다. 그중 `D:/AppHome/testApp/src/main/java/kr/co/gnx/comm/service/FileService.java`는 결과가 113자뿐이었습니다. 이 파일은 그 경로에 없고 실제 위치는 `kr/co/gnx/system/file/FileService.java`라서, 결과는 "파일을 읽을 수 없다"는 실패 안내였을 가능성이 큽니다.
3. 3회차에 모델이 파일을 찾으려고 `searchInFiles`를 두 번 요청했습니다.
4. 그런데 `sm-developer-agent.md`의 `tools.allowed`에는 `readFileLines, isFileExist, writeFile, replaceInFile, editFileTexts, appendFile, copyFileWithReplace`만 있고 `searchInFiles`는 없습니다. 그래서 LLM에게도 그 Tool 정의가 보내지지 않았습니다.
5. Spring AI 2.0.1의 `DefaultToolCallingManager.executeToolCall()`은 이 Agent의 Tool 목록에서 이름을 찾고, 없으면 `ToolCallbackResolver`에서 찾고, 그래도 없으면 `IllegalStateException("No ToolCallback found …")`을 던집니다. 바이트코드로 확인했습니다.
6. 이 예외는 `AgentContractException`(step 실패 → `onFailure`)이 아니라 일반 시스템 예외로 취급됩니다. 그래서 LLM에게 "그런 Tool은 없다"는 답이 돌아가지 못하고 실행 전체가 바로 FAILED로 끝났습니다.

### 처음이 아닙니다
같은 날 09:46에 실패한 `4989df1b-…`도 똑같습니다. `step13`, 3회차에 `searchInFiles` 두 번, 같은 예외로 끝났습니다. 그 실행에서는 FileService 대신 `memberRegi.jsp`와 `schema.sql`을 읽은 뒤였습니다. 그러니 우연한 일이 아니라, **이 모델이 개발 단계에서 파일을 찾아야 할 때마다 `searchInFiles`를 꺼내 쓰는 습관**으로 보는 게 맞습니다. 다른 sm Agent(영향도 분석, 설계)는 `searchInFiles`를 갖고 있고 프롬프트에서도 이 이름을 씁니다. 다만 developer Agent의 요청에 그 문서들이 들어갔는지는 확인하지 못했습니다.

### 고칠 곳 (아직 고치지 않았습니다)
- **엔진 쪽, 근본 대책:** 모르는 Tool 이름을 받으면 예외 대신 `실패: 'searchInFiles'라는 Tool은 없습니다. 받은 Tool 목록의 이름만 쓰세요.`를 Tool 결과로 돌려주게 합니다. `ToolCallbackResolver` 빈을 하나 두거나 `ToolCallingManager`를 감싸면 됩니다. 이렇게 하면 모델이 스스로 다른 방법을 고르고, 어떤 Agent든 같은 일로 실행 전체가 죽지 않습니다. Tool 이름은 이미 기동할 때 `AgentRegistry`가 검사하므로, 설정 오류가 이 처리에 가려질 걱정은 없습니다.
- **정의 쪽, 이번 경우:** `sm-developer-agent`의 `tools.allowed`에 `searchInFiles`를 넣거나, 프롬프트에 "경로에 파일이 없으면 찾지 말고 '보류'로 둔다"를 적습니다. 설계서에 FileService 경로가 잘못 적힌 것도 원인이므로 설계 단계의 경로 확인도 함께 보는 것이 좋습니다.

엔진 쪽 대책부터 적용할 것을 권합니다.
