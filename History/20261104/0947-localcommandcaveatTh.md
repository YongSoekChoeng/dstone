# localcommandcaveatTh

> 생성일시: 2026-11-04 09:47

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="d5d9">
dstone-boot>ai 의 Workflow 실행 관리 에서
step 마다 작성된 md 파일들을 팝업으로 조회하고 수정할 수 있는 기능이 있었으면 좋겠어.
팝업에서 md 파일 보여줄 때 브라우저에서 부드럽게 보여줄 수 있도록 UI 라이브러리가 적용되었으면 좋겠고.
계획을 알려줘.
</pasted_content id="d5d9">

---

## 답변

Workflow 실행 관리 화면과 관련 코드를 먼저 살펴보겠습니다.

---

## 답변

# 계획: Workflow 실행 관리 화면에서 step별 md 문서 보기·수정

## 먼저 풀어야 할 문제: 어느 step이 어떤 md를 썼는지 지금은 알 수 없음

살펴보니 엔진에는 이 정보가 남지 않습니다.
- step 이력(`StepHistoryEntry`)에는 stepId, Agent 이름, 결과 요약만 있습니다. Agent가 `writeFile`/`editFileTexts`로 저장한 파일 경로는 기록되지 않습니다.
- 경로는 Agent 프롬프트 안에만 적혀 있습니다(예: `${input.base_dir}/02.impact.md`).
- 화면(dstone-boot)과 파일은 서로 다른 곳에 있습니다(boot는 Pod, 파일은 엔진이 접근하는 경로). 그래서 boot가 파일을 직접 읽을 수 없고, 엔진 API를 거쳐야 합니다.

검토한 방법은 세 가지입니다.

| 방법 | 판단 |
|---|---|
| **A. Tool 호출 시점에 기록** (파일 Tool이 성공하면 실행 id·step id·경로를 DB에 남김) | **추천합니다.** 모든 Workflow에 그대로 쓸 수 있고, step과 파일이 정확히 이어집니다 |
| B. 입력 폴더의 `*.md`를 훑기 | sm-workflow처럼 입력이 폴더인 경우에만 됩니다. 어느 step이 썼는지도 알 수 없습니다 |
| C. YAML step에 `documents:` 키 추가 | 프롬프트에 적힌 경로와 따로 놀 수 있고, 정의 형식이 늘어납니다 |

## 1단계: 엔진(dstone-ai-engine)이 파일 접근을 기록

1. **실행 정보를 Tool까지 전달합니다.** 지금 `toolContext`에는 caller 하나만 실립니다. 여기에 `executionId`/`stepId`를 더합니다. 넘기는 경로는 다음과 같습니다.
   - `AgentStepExecutor`/`SupervisorStepExecutor`/`RouterStepExecutor` → `AgentExecutor.call()`/`callForSchema()`
   - `ToolStepExecutor` → `ToolExecutor.call()`
   - Sub Agent(`SubAgentToolCallback`, 백그라운드 포함)도 부모의 값을 그대로 넘깁니다.
   - ThreadLocal은 쓰지 않습니다. forEach와 백그라운드 Sub Agent가 다른 스레드에서 돌기 때문입니다.
2. **기록은 한 곳에서 합니다.** 모든 Tool을 감싸는 `ConfigTool.LimitedToolCallback`에서 처리합니다. 파일 Tool(`writeFile`, `appendFile`, `replaceInFile`, `editFileTexts`, `editFileLines`, `copyFileWithReplace`, `readFile` 계열)이 `실패:` 없이 끝나면 인자에서 경로를 꺼내 저장합니다.
   - 쓰기(WRITE)와 읽기(READ)를 구분해 둡니다. 그래야 `step00`에서 읽은 `00.request.md`도 보입니다.
   - 같은 (실행, step, 경로)는 한 행으로 합치고 마지막 시각과 횟수만 바꿉니다. 재분석 루프로 step이 다시 돌아도 행이 계속 늘지 않습니다.
3. **테이블을 하나 추가합니다.** `AI_WORKFLOW_EXECUTION_FILE`(실행 id, step id, 경로, 동작, 횟수, 마지막 시각)과 `schema/04-alter-table-execution-file.sql`을 만들고, 기존 방식대로 MyBatis `sqlmap/workflow/WorkFlowExecutionFileDao.xml`을 둡니다.
4. **API를 추가합니다.** 기존 `WorkFlowExecutionController`에 붙입니다.
   - `GET /executions/{id}/files`: step별 파일 목록과 현재 존재 여부, 크기, 수정 시각
   - `GET /executions/{id}/files/content?path=`: 내용, 글자셋, 내용 해시
   - `POST /executions/{id}/files/content` `{path, text, baseHash}`: 저장
5. **안전장치를 둡니다.**
   - 그 실행에 기록된 경로만 열 수 있습니다. 임의 경로는 400으로 막습니다.
   - 수정은 `.md`만 허용합니다(확장자 목록은 설정으로 바꿀 수 있게 합니다).
   - 쓰기 기능은 `dstone.ai.workflow.file-edit.enabled`로 켭니다. 디자이너의 `edit-enabled`와 같은 방식입니다.
   - `baseHash`가 다르면 409를 돌려줍니다. 열어 본 뒤 Agent가 파일을 다시 썼다면 덮어쓰지 않습니다.
   - 실행 상태가 `RUNNING`이면 보기만 됩니다. Agent가 쓰고 있는 파일과 충돌하지 않게 하기 위해서입니다.
   - 저장할 때 원래 글자셋과 줄바꿈 방식(CRLF/LF)을 유지합니다. `FileTool`에 있는 로직을 다시 씁니다.

## 2단계: 화면(dstone-boot)

1. **프록시를 추가합니다.** `WorkFlowAdminService`/`WorkFlowAdminController`에 목록·조회·저장 3개를 붙입니다. 409/403을 화면까지 그대로 전달합니다.
2. **실행 상세 화면을 고칩니다.**
   - step 이력 표에 '문서' 열을 넣고, step마다 md 파일을 칩으로 보여 줍니다. 쓰기와 읽기는 표시를 다르게 합니다.
   - 실행 전체의 문서를 모아 보는 목록도 하나 둡니다.
3. **팝업을 만듭니다.**
   - 보기 탭: 렌더링한 문서를 보여 줍니다.
   - 수정 탭: 왼쪽은 원문 편집, 오른쪽은 실시간 미리보기입니다.
   - [저장], [전체 화면](디자이너와 같은 방식), Esc로 닫기를 넣습니다.
   - 승인 대기 중이면 팝업 안에 "수정 후 승인하면 다음 step이 이 파일을 읽습니다" 안내를 띄웁니다.
4. **UI 라이브러리**는 폐쇄망을 고려해 CDN을 쓰지 않고 `webapp/ai/assets/lib/`에 직접 넣습니다.
   - **marked**(Markdown → HTML), **DOMPurify**(LLM이 쓴 HTML 걸러내기), **github-markdown-css**(GitHub 스타일 표·코드·목록), **highlight.js**(코드 블록 색칠, Knowledge 화면에 이미 들어 있는 11.11.1 재사용)를 씁니다.
   - 수정은 원문 textarea로 합니다. Toast UI Editor 같은 WYSIWYG 편집기도 검토했습니다. 그런데 이런 편집기는 저장할 때 표나 공백을 정리해 버립니다. 그러면 뒤 step Agent가 `editFileTexts`로 정확한 글자를 찾지 못해 실패할 수 있어서 제외했습니다.

## 3단계: 검증과 문서

- **엔진 테스트**: `WorkflowTestBase`에서 `answerToolCalls("writeFile", …)`로 스크립트 실행을 돌립니다. step별 기록이 남는지, 허용되지 않은 경로가 400인지, 해시가 다를 때 409인지, RUNNING 중 저장이 거부되는지 확인합니다.
- **화면 확인**: 엔진과 boot 사본을 8181/7181 포트에 띄웁니다. 그다음 headless Chromium으로 팝업 열기, 렌더링, 수정·저장, 409 처리를 확인합니다.
- **문서**: `docs/09.dstone-ai-engine.md`(API·설정·테이블)와 CLAUDE.md를 고칩니다.

## 미리 알아 두실 점

- **기능을 넣기 전에 실행된 건에는 문서 목록이 나오지 않습니다.** 기록이 그때부터 쌓이기 때문입니다.
- **문서를 고쳐도 반영되지 않는 경우가 있습니다.** 이미 `state`에 읽어 둔 내용은 바뀌지 않습니다. sm-workflow에서 보면 이렇습니다.
  - 반영됨: 영향도 승인(step06) 전에 `02.impact.md`를 고치면 step07이 다시 읽습니다. 설계 승인(step09) 전에 `03-*.md`를 고치면 step10/11이 다시 읽습니다.
  - 반영 안 됨: 리뷰 불통과(step04)에서 `01.customer_requirement.md`를 고치고 '그대로 진행'을 고르면, 그 뒤 step은 이미 `state.requirement`에 읽어 둔 옛 내용을 씁니다.
  - 팝업에도 이 차이를 안내하겠습니다.
- **기록하는 파일**: 개발 step이 고친 Java 소스 같은 md가 아닌 파일도 함께 기록합니다. 다만 화면에서는 기본으로 md만 보여 주고, 그런 파일은 보기 전용으로 둘 생각입니다.

진행하시면 1단계(엔진 기록과 API)부터 시작하겠습니다. 라이브러리를 Toast UI Editor로 바꾸거나 RUNNING 중에도 수정을 허용하고 싶으시면 그때 말씀해 주세요.
