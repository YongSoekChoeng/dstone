# dstoneboot_Workflow_

> 생성일시: 2026-09-26 07:28

## 질문

dstone-boot > Workflow 테스트 메뉴에서 workflowId 를 선택할 때마다 message/variables 등에 테스트를 진행할 수 있는 기본샘플 값들을 자동으로 채워줘.

---

## 답변

Workflow YAML 주석에 적힌 권장 샘플값들을 모았습니다. 이제 프롬프트 변수 쓰는 Agent를 확인합니다.

---

## 답변

Workflow 테스트 화면에서 workflowId를 고를 때마다 message와 variables 칸에 테스트용 샘플값이 자동으로 채워집니다. 브라우저에서 직접 눌러보지는 않았고, JS 파일 문법 검사만 통과했습니다.

**변경 내용**
- `dstone-boot/src/main/webapp/ai/assets/js/workflow.js`
  - `WORKFLOW_SAMPLES` 맵을 추가했습니다. workflowId마다 message, variables, 주의사항(note)이 들어 있습니다.
  - 등록된 15개 Workflow(`sample-*` 14개 + `testApp-sdlc`)의 값을 모두 넣었습니다. 각 YAML 맨 위 주석에 적힌 테스트 예시를 그대로 옮겼습니다.
    - variables는 `sample-foreach-parallel`에만 `{"sqlList": [...]}`, `sample-router-multiway`와 `sample-supervisor-verdict-gate`에만 `{"role": "친절한 상담원"}`이 들어갑니다.
  - 새로 만든 `fillSample()`이 드롭다운을 바꿀 때와 목록을 처음 불러올 때 실행됩니다. 이미 입력해 둔 값은 덮어쓰고, 맵에 없는 workflowId를 고르면 두 칸을 비웁니다.
- `dstone-boot/src/main/webapp/WEB-INF/views/ai/workflow/workflow.jsp`: message 아래에 `#workflow-sample-note` 안내 문구를 추가했습니다. "SELEC 오타는 일부러 넣은 것", "APP_HOME 경로에 맞게 수정" 같은 주의사항이 여기에 표시됩니다.
- 문서: `docs/06.dstone-boot.md` §10.4와 `docs/09.dstone-ai-engine.md` §7.8에 이 기능을 설명하고, Workflow를 추가하면 이 맵도 함께 고쳐야 한다고 적었습니다.

**직접 확인이 필요한 샘플값**
- **`sample-mcp-filesystem-list`**: 경로를 WSL/k8s 기준인 `/app/dstone/...`로 넣었습니다. Windows 로컬에서 쓰려면 경로를 바꿔야 합니다.
- **RAG 샘플 2종**(`sample-agent-rag-augmented`, `sample-tool-rag-search`): 검색어에 "Spring Batch"를 임시로 넣었습니다. 실제로 적재한 문서에 있는 키워드로 바꿔야 결과가 나옵니다.

**다른 방식:** 지금은 샘플값을 화면 쪽 JS에만 두었기 때문에, 엔진에 Workflow YAML을 새로 추가하면 이 맵에도 한 줄을 따로 넣어야 합니다. YAML에 `sample:` 필드를 두고 `GET /api/ai/workflow`가 함께 내려주게 하면 YAML 하나만 관리하면 됩니다. 원하시면 그렇게 바꾸겠습니다.

---

## 질문

git 에 올려줘

---

## 답변

GitHub `main` 브랜치에 커밋해서 올렸습니다 (`9e460ef`).

- **커밋에 넣은 파일:** `workflow.js`, `workflow.jsp`, `docs/06.dstone-boot.md`, `docs/09.dstone-ai-engine.md`
- **커밋에서 뺀 파일:** 처음부터 수정돼 있던 `dstone-boot/src/main/webapp/ai/assets/css/main.css`는 이번 작업과 관계없어 넣지 않았습니다. 그런데 방금 확인하니 작업 폴더에 수정사항이 남아 있지 않습니다. 그 사이 다른 곳에서 커밋됐거나 되돌려진 것 같습니다.

---

## 질문

기본값으로 테스트 했을 때 오류가 나는데? 

<pasted_content id="643e">

||===================================== [net.dstone.ai.api.controller.WorkFlowController] START ======================================||
2026-09-26 07:26:35  INFO [net.dstone.ai.common.config.ConfigCallLog] +->[CONTROLLER] {WorkFlowController.submit(String[sample-foreach-parallel]WorkFlowRequest[message=foreach 테스트, sessionId=null, variables={sqlList=[SELECT 1 FROM dual, SELEC 1 FROM dual]}], , )}
2026-09-26 07:26:35  INFO [net.dstone.ai.common.config.ConfigCallLog] +--->[SERVICE ] {WorkFlowExecutionService.checkInputs(WorkFlowDefinition[id=sample-foreach-parallel, description=forEach 동작 확인용 테스트 Workflow - TOOL step 하나를 sqlList 항목 수만큼 동시 실행한다., maxIterations=5, allowedCallers=null, inputs={sqlList=FieldDefinition[type=list<string>, description=null]}, output=null, steps=[StepDefinition[id=validate-each, type=TOOL, ref=validateSqlSyntax, onFailure=FAIL, onSuccess=SUCCESS, input={sql={{item}}}, output=null, approverRole=null, routes=null, forEach=inputs.sqlList, itemVariable=null]]]{"sqlList":["SELECT 1 FROM dual","SELEC 1 FROM dual"]}, String[foreach 테스트], )}
2026-09-26 07:26:35  INFO [net.dstone.ai.common.config.ConfigCallLog] +--->[SERVICE ] {WorkFlowExecutionService.submitAsync(WorkFlowDefinition[id=sample-foreach-parallel, description=forEach 동작 확인용 테스트 Workflow - TOOL step 하나를 sqlList 항목 수만큼 동시 실행한다., maxIterations=5, allowedCallers=null, inputs={sqlList=FieldDefinition[type=list<string>, description=null]}, output=null, steps=[StepDefinition[id=validate-each, type=TOOL, ref=validateSqlSyntax, onFailure=FAIL, onSuccess=SUCCESS, input={sql={{item}}}, output=null, approverRole=null, routes=null, forEach=inputs.sqlList, itemVariable=null]]]String[9813ccd8-99ce-4307-8888-a8f54cbbdc19], , {"sqlList":["SELECT 1 FROM dual","SELEC 1 FROM dual"]}, String[foreach 테스트], )}
2026-09-26 07:26:35  INFO [jdbc.sqltiming] INSERT INTO AI_WORKFLOW_EXECUTION (  EXECUTION_ID, WORKFLOW_ID, CALLER, SESSION_ID, STATUS, CURRENT_STEP_INDEX, CONTEXT_JSON, RESULT_TEXT, ERROR_MESSAGE, CREATED_AT, UPDATED_AT ) VALUES (   'f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c', 'sample-foreach-parallel', NULL, '9813ccd8-99ce-4307-8888-a8f54cbbdc19', 'RUNNING', 0, '{"inputs":{"sqlList":["SELECT 1 FROM dual","SELEC 1 FROM dual"],"message":"foreach 테스트"},"steps":{},"previous":{"text":"foreach 테스트"}}'::jsonb, NULL, NULL, '09/26/2026 07:26:35.782', '09/26/2026 07:26:35.782' )
 {executed in 170 msec}
||===================================== [net.dstone.ai.api.controller.WorkFlowController] END ======================================||

2026-09-26 07:26:35  INFO [net.dstone.ai.common.config.ConfigCallLog] 

|--------------------------------------------------------------------------------------------------------------------------------------|
[WorkFlowExecutor - workFlow(id=sample-foreach-parallel)] Start !!!
<input>
WorkFlowDefinition[id=sample-foreach-parallel, description=forEach 동작 확인용 테스트 Workflow - TOOL step 하나를 sqlList 항목 수만큼 동시 실행한다., maxIterations=5, allowedCallers=null, inputs={sqlList=FieldDefinition[type=list<string>, description=null]}, output=null, steps=[StepDefinition[id=validate-each, type=TOOL, ref=validateSqlSyntax, onFailure=FAIL, onSuccess=SUCCESS, input={sql={{item}}}, output=null, approverRole=null, routes=null, forEach=inputs.sqlList, itemVariable=null]]]WorkFlowExecution[executionId=f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c, workflowId=sample-foreach-parallel, caller=null, sessionId=9813ccd8-99ce-4307-8888-a8f54cbbdc19, status=RUNNING, currentStepIndex=0, context={inputs={sqlList=[SELECT 1 FROM dual, SELEC 1 FROM dual], message=foreach 테스트}, steps={}, previous={text=foreach 테스트}}, resultText=null, errorMessage=null, createdAt=2026-09-25T22:26:35.782894900Z, updatedAt=2026-09-25T22:26:35.782894900Z], 
|--------------------------------------------------------------------------------------------------------------------------------------|

2026-09-26 07:26:35  INFO [net.dstone.ai.common.config.ConfigCallLog] 

|--------------------------------------------------------------------------------------------------------------------------------------|
[StepRunner - step(id=validate-each, type=TOOL, ref=validateSqlSyntax)] Start !!!
<input>
WorkFlowExecution[executionId=f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c, workflowId=sample-foreach-parallel, caller=null, sessionId=9813ccd8-99ce-4307-8888-a8f54cbbdc19, status=RUNNING, currentStepIndex=0, context={inputs={sqlList=[SELECT 1 FROM dual, SELEC 1 FROM dual], message=foreach 테스트}, steps={}, previous={text=foreach 테스트}}, resultText=null, errorMessage=null, createdAt=2026-09-25T22:26:35.782894900Z, updatedAt=2026-09-25T22:26:35.782894900Z]StepDefinition[id=validate-each, type=TOOL, ref=validateSqlSyntax, onFailure=FAIL, onSuccess=SUCCESS, input={sql={{item}}}, output=null, approverRole=null, routes=null, forEach=inputs.sqlList, itemVariable=null], StepInput[text=null, arguments={sql=SELEC 1 FROM dual}, workflowInputs={sqlList=[SELECT 1 FROM dual, SELEC 1 FROM dual], message=foreach 테스트}], 
|--------------------------------------------------------------------------------------------------------------------------------------|

2026-09-26 07:26:35  INFO [net.dstone.ai.common.config.ConfigCallLog] 

|--------------------------------------------------------------------------------------------------------------------------------------|
[StepRunner - step(id=validate-each, type=TOOL, ref=validateSqlSyntax)] Start !!!
<input>
WorkFlowExecution[executionId=f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c, workflowId=sample-foreach-parallel, caller=null, sessionId=9813ccd8-99ce-4307-8888-a8f54cbbdc19, status=RUNNING, currentStepIndex=0, context={inputs={sqlList=[SELECT 1 FROM dual, SELEC 1 FROM dual], message=foreach 테스트}, steps={}, previous={text=foreach 테스트}}, resultText=null, errorMessage=null, createdAt=2026-09-25T22:26:35.782894900Z, updatedAt=2026-09-25T22:26:35.782894900Z]StepDefinition[id=validate-each, type=TOOL, ref=validateSqlSyntax, onFailure=FAIL, onSuccess=SUCCESS, input={sql={{item}}}, output=null, approverRole=null, routes=null, forEach=inputs.sqlList, itemVariable=null], StepInput[text=null, arguments={sql=SELECT 1 FROM dual}, workflowInputs={sqlList=[SELECT 1 FROM dual, SELEC 1 FROM dual], message=foreach 테스트}], 
|--------------------------------------------------------------------------------------------------------------------------------------|

2026-09-26 07:26:35  INFO [net.dstone.ai.common.config.ConfigCallLog] 

|--------------------------------------------------------------------------------------------------------------------------------------|
[Tools - ToolExecutor.call()] Start !!!
<input>
String[validateSqlSyntax], String[{"sql":"SELEC 1 FROM dual"}], 
|--------------------------------------------------------------------------------------------------------------------------------------|

2026-09-26 07:26:35  INFO [net.dstone.ai.common.config.ConfigCallLog] 

|--------------------------------------------------------------------------------------------------------------------------------------|
[Tools - ToolExecutor.call()] Start !!!
<input>
String[validateSqlSyntax], String[{"sql":"SELECT 1 FROM dual"}], 
|--------------------------------------------------------------------------------------------------------------------------------------|



||===================================== [net.dstone.ai.api.controller.WorkFlowExecutionController] START ======================================||
2026-09-26 07:26:36  INFO [net.dstone.ai.common.config.ConfigCallLog] +->[CONTROLLER] {WorkFlowExecutionController.detail(String[f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c])}
2026-09-26 07:26:36  INFO [net.dstone.ai.common.config.ConfigCallLog] +--->[SERVICE ] {WorkFlowExecutionService.find(String[f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c])}
2026-09-26 07:26:36  INFO [net.dstone.ai.common.config.ConfigCallLog] 

|--------------------------------------------------------------------------------------------------------------------------------------|
[Tools - ToolExecutor.call()] End !!!
<output>
{"success":true,"message":"문법 구조상 문제가 없습니다."}
|--------------------------------------------------------------------------------------------------------------------------------------|

2026-09-26 07:26:36  INFO [net.dstone.ai.common.config.ConfigCallLog] 

|--------------------------------------------------------------------------------------------------------------------------------------|
[Tools - ToolExecutor.call()] End !!!
<output>
{"success":false,"message":"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \"SELEC\" <S_IDENTIFIER>\n    at line 1, column 1.\n\nWas expecting one of:\n\n    \"(\"\n    \"ALTER\"\n    \"ANALYZE\"\n    \"BEGIN\"\n    \"CALL\"\n    \"COMMENT\"\n    \"COMMIT\"\n    \"CREATE\"\n    \"DECLARE\"\n    \"DELETE\"\n    \"DESCRIBE\"\n    \"DROP\"\n    \"EXEC\"\n    \"EXECUTE\"\n    \"EXPLAIN\"\n    \"GRANT\"\n    \"IF\"\n    \"INSERT\"\n    \"MERGE\"\n    \"PURGE\"\n    \"RENAME\"\n    \"REPLACE\"\n    \"RESET\"\n    \"ROLLBACK\"\n    \"SAVEPOINT\"\n    \"SET\"\n    \"SHOW\"\n    \"TRUNCATE\"\n    \"UPDATE\"\n    \"UPSERT\"\n    \"USE\"\n    \"VALUE\"\n    \"VALUES\"\n    \"WITH\"\n    <K_SELECT>\n    <ST_SEMICOLON>\n"}
|--------------------------------------------------------------------------------------------------------------------------------------|

2026-09-26 07:26:36  INFO [net.dstone.ai.common.config.ConfigCallLog] 

|--------------------------------------------------------------------------------------------------------------------------------------|
[StepRunner - step(id=validate-each, type=TOOL, ref=validateSqlSyntax)] End !!!
<output>
Success[text={"success":true,"message":"문법 구조상 문제가 없습니다."}, output={}]
|--------------------------------------------------------------------------------------------------------------------------------------|

2026-09-26 07:26:36  INFO [net.dstone.ai.common.config.ConfigCallLog] 

|--------------------------------------------------------------------------------------------------------------------------------------|
[StepRunner - step(id=validate-each, type=TOOL, ref=validateSqlSyntax)] End !!!
<output>
Failure[text={"success":false,"message":"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \"SELEC\" <S_IDENTIFIER>\n    at line 1, column 1.\n\nWas expecting one of:\n\n    \"(\"\n    \"ALTER\"\n    \"ANALYZE\"\n    \"BEGIN\"\n    \"CALL\"\n    \"COMMENT\"\n    \"COMMIT\"\n    \"CREATE\"\n    \"DECLARE\"\n    \"DELETE\"\n    \"DESCRIBE\"\n    \"DROP\"\n    \"EXEC\"\n    \"EXECUTE\"\n    \"EXPLAIN\"\n    \"GRANT\"\n    \"IF\"\n    \"INSERT\"\n    \"MERGE\"\n    \"PURGE\"\n    \"RENAME\"\n    \"REPLACE\"\n    \"RESET\"\n    \"ROLLBACK\"\n    \"SAVEPOINT\"\n    \"SET\"\n    \"SHOW\"\n    \"TRUNCATE\"\n    \"UPDATE\"\n    \"UPSERT\"\n    \"USE\"\n    \"VALUE\"\n    \"VALUES\"\n    \"WITH\"\n    <K_SELECT>\n    <ST_SEMICOLON>\n"}, failureReason=문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "SELEC" <S_IDENTIFIER>    at line 1, column 1.Was expecting one of:    "("    "ALTER"    "ANALYZE"    "BEGIN"    "CALL"    "COMMENT"    "COMMIT"    "CREATE"    "DECLARE"    "DELETE"    "DESCRIBE"    "DROP"    "EXEC"    "EXECUTE"    "EXPLAIN"    "GRANT"    "IF"    "INSERT"    "MERGE"    "PURGE"    "RENAME"    "REPLACE"    "RESET"    "ROLLBACK"    "SAVEPOINT"    "SET"    "SHOW"    "TRUNCATE"    "UPDATE"    "UPSERT"    "USE"    "VALUE"    "VALUES"    "WITH"    <K_SELECT>    <ST_SEMICOLON>]
|--------------------------------------------------------------------------------------------------------------------------------------|

2026-09-26 07:26:36  INFO [jdbc.sqltiming] SELECT * FROM AI_WORKFLOW_EXECUTION WHERE EXECUTION_ID = 'f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c'
 {executed in 26 msec}
2026-09-26 07:26:36  INFO [net.dstone.ai.common.config.ConfigCallLog] +--->[SERVICE ] {WorkFlowExecutionService.history(String[f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c])}
2026-09-26 07:26:36  INFO [jdbc.sqltiming] SELECT * FROM AI_WORKFLOW_EXECUTION_STEP_HISTORY WHERE EXECUTION_ID = 'f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c' ORDER BY EXECUTED_AT ASC, ID ASC
 {executed in 9 msec}
||===================================== [net.dstone.ai.api.controller.WorkFlowExecutionController] END ======================================||

2026-09-26 07:26:36  INFO [jdbc.sqltiming] INSERT INTO AI_WORKFLOW_EXECUTION_STEP_HISTORY (EXECUTION_ID, STEP_ID, STEP_TYPE, STEP_REF, SUCCESS, DURATION_MS, OUTPUT_SUMMARY, FAILURE_REASON, EXECUTED_AT) VALUES ('f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c', 'validate-each[0]', 'TOOL', 'validateSqlSyntax', 1, 70, '{"success":true,"message":"문법 구조상 문제가 없습니다."}', NULL, '09/26/2026 07:26:36.056')
 {executed in 57 msec}
2026-09-26 07:26:36  INFO [jdbc.sqltiming] INSERT INTO AI_WORKFLOW_EXECUTION_STEP_HISTORY (EXECUTION_ID, STEP_ID, STEP_TYPE, STEP_REF, SUCCESS, DURATION_MS, OUTPUT_SUMMARY, FAILURE_REASON, EXECUTED_AT) VALUES ('f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c', 'validate-each[1]', 'TOOL', 'validateSqlSyntax', 0, 70, NULL, '문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "SELEC" <S_IDENTIFIER>
    at line 1, column 1.

Was expecting one of:

    "("
    "ALTER"
    "ANALYZE"
    "BEGIN"
    "CALL"
    "COMMENT"
    "COMMIT"
    "CREATE"
    "DECLARE"
    "DELETE"
    "DESCRIBE"
    "DROP"
    "EXEC"
    "EXECUTE"
    "EXPLAIN"
    "GRANT"
    "IF"
    "INSERT"
    "MERGE"
    "PURGE"
    "RENAME"
    "REPLACE"
    "RESET"
    "ROLLBACK"
    "SAVEPOINT"
    "SET"
    "SHOW"
    "TRUNCATE"
    "UPDATE"
    "UPSERT"
    "USE"
    "VALUE"
    "VALUES"
    "WITH"
    <K_SELECT>
    <ST_SEMICOLON>
', '09/26/2026 07:26:36.116')
 {executed in 33 msec}
2026-09-26 07:26:36  INFO [jdbc.sqltiming] UPDATE AI_WORKFLOW_EXECUTION SET STATUS = 'FAILED', CURRENT_STEP_INDEX = 0, CONTEXT_JSON = '{"inputs":{"sqlList":["SELECT 1 FROM dual","SELEC 1 FROM dual"],"message":"foreach 테스트"},"steps":{"validate-each":{"input":null,"output":{},"text":"{\"success\":true,\"message\":\"문법 구조상 문제가 없습니다.\"}\n{\"success\":false,\"message\":\"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \\\"SELEC\\\" <S_IDENTIFIER>\\n    at line 1, column 1.\\n\\nWas expecting one of:\\n\\n    \\\"(\\\"\\n    \\\"ALTER\\\"\\n    \\\"ANALYZE\\\"\\n    \\\"BEGIN\\\"\\n    \\\"CALL\\\"\\n    \\\"COMMENT\\\"\\n    \\\"COMMIT\\\"\\n    \\\"CREATE\\\"\\n    \\\"DECLARE\\\"\\n    \\\"DELETE\\\"\\n    \\\"DESCRIBE\\\"\\n    \\\"DROP\\\"\\n    \\\"EXEC\\\"\\n    \\\"EXECUTE\\\"\\n    \\\"EXPLAIN\\\"\\n    \\\"GRANT\\\"\\n    \\\"IF\\\"\\n    \\\"INSERT\\\"\\n    \\\"MERGE\\\"\\n    \\\"PURGE\\\"\\n    \\\"RENAME\\\"\\n    \\\"REPLACE\\\"\\n    \\\"RESET\\\"\\n    \\\"ROLLBACK\\\"\\n    \\\"SAVEPOINT\\\"\\n    \\\"SET\\\"\\n    \\\"SHOW\\\"\\n    \\\"TRUNCATE\\\"\\n    \\\"UPDATE\\\"\\n    \\\"UPSERT\\\"\\n    \\\"USE\\\"\\n    \\\"VALUE\\\"\\n    \\\"VALUES\\\"\\n    \\\"WITH\\\"\\n    <K_SELECT>\\n    <ST_SEMICOLON>\\n\"}","error":"validate-each[1]: 문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \"SELEC\" <S_IDENTIFIER>\n    at line 1, column 1.\n\nWas expecting one of:\n\n    \"(\"\n    \"ALTER\"\n    \"ANALYZE\"\n    \"BEGIN\"\n    \"CALL\"\n    \"COMMENT\"\n    \"COMMIT\"\n    \"CREATE\"\n    \"DECLARE\"\n    \"DELETE\"\n    \"DESCRIBE\"\n    \"DROP\"\n    \"EXEC\"\n    \"EXECUTE\"\n    \"EXPLAIN\"\n    \"GRANT\"\n    \"IF\"\n    \"INSERT\"\n    \"MERGE\"\n    \"PURGE\"\n    \"RENAME\"\n    \"REPLACE\"\n    \"RESET\"\n    \"ROLLBACK\"\n    \"SAVEPOINT\"\n    \"SET\"\n    \"SHOW\"\n    \"TRUNCATE\"\n    \"UPDATE\"\n    \"UPSERT\"\n    \"USE\"\n    \"VALUE\"\n    \"VALUES\"\n    \"WITH\"\n    <K_SELECT>\n    <ST_SEMICOLON>\n","items":[{"input":{"sql":"SELECT 1 FROM dual"},"output":{},"text":"{\"success\":true,\"message\":\"문법 구조상 문제가 없습니다.\"}","error":null},{"input":{"sql":"SELEC 1 FROM dual"},"output":{},"text":"{\"success\":false,\"message\":\"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \\\"SELEC\\\" <S_IDENTIFIER>\\n    at line 1, column 1.\\n\\nWas expecting one of:\\n\\n    \\\"(\\\"\\n    \\\"ALTER\\\"\\n    \\\"ANALYZE\\\"\\n    \\\"BEGIN\\\"\\n    \\\"CALL\\\"\\n    \\\"COMMENT\\\"\\n    \\\"COMMIT\\\"\\n    \\\"CREATE\\\"\\n    \\\"DECLARE\\\"\\n    \\\"DELETE\\\"\\n    \\\"DESCRIBE\\\"\\n    \\\"DROP\\\"\\n    \\\"EXEC\\\"\\n    \\\"EXECUTE\\\"\\n    \\\"EXPLAIN\\\"\\n    \\\"GRANT\\\"\\n    \\\"IF\\\"\\n    \\\"INSERT\\\"\\n    \\\"MERGE\\\"\\n    \\\"PURGE\\\"\\n    \\\"RENAME\\\"\\n    \\\"REPLACE\\\"\\n    \\\"RESET\\\"\\n    \\\"ROLLBACK\\\"\\n    \\\"SAVEPOINT\\\"\\n    \\\"SET\\\"\\n    \\\"SHOW\\\"\\n    \\\"TRUNCATE\\\"\\n    \\\"UPDATE\\\"\\n    \\\"UPSERT\\\"\\n    \\\"USE\\\"\\n    \\\"VALUE\\\"\\n    \\\"VALUES\\\"\\n    \\\"WITH\\\"\\n    <K_SELECT>\\n    <ST_SEMICOLON>\\n\"}","error":"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \"SELEC\" <S_IDENTIFIER>\n    at line 1, column 1.\n\nWas expecting one of:\n\n    \"(\"\n    \"ALTER\"\n    \"ANALYZE\"\n    \"BEGIN\"\n    \"CALL\"\n    \"COMMENT\"\n    \"COMMIT\"\n    \"CREATE\"\n    \"DECLARE\"\n    \"DELETE\"\n    \"DESCRIBE\"\n    \"DROP\"\n    \"EXEC\"\n    \"EXECUTE\"\n    \"EXPLAIN\"\n    \"GRANT\"\n    \"IF\"\n    \"INSERT\"\n    \"MERGE\"\n    \"PURGE\"\n    \"RENAME\"\n    \"REPLACE\"\n    \"RESET\"\n    \"ROLLBACK\"\n    \"SAVEPOINT\"\n    \"SET\"\n    \"SHOW\"\n    \"TRUNCATE\"\n    \"UPDATE\"\n    \"UPSERT\"\n    \"USE\"\n    \"VALUE\"\n    \"VALUES\"\n    \"WITH\"\n    <K_SELECT>\n    <ST_SEMICOLON>\n"}]}},"previous":{"input":null,"output":{},"text":"{\"success\":true,\"message\":\"문법 구조상 문제가 없습니다.\"}\n{\"success\":false,\"message\":\"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \\\"SELEC\\\" <S_IDENTIFIER>\\n    at line 1, column 1.\\n\\nWas expecting one of:\\n\\n    \\\"(\\\"\\n    \\\"ALTER\\\"\\n    \\\"ANALYZE\\\"\\n    \\\"BEGIN\\\"\\n    \\\"CALL\\\"\\n    \\\"COMMENT\\\"\\n    \\\"COMMIT\\\"\\n    \\\"CREATE\\\"\\n    \\\"DECLARE\\\"\\n    \\\"DELETE\\\"\\n    \\\"DESCRIBE\\\"\\n    \\\"DROP\\\"\\n    \\\"EXEC\\\"\\n    \\\"EXECUTE\\\"\\n    \\\"EXPLAIN\\\"\\n    \\\"GRANT\\\"\\n    \\\"IF\\\"\\n    \\\"INSERT\\\"\\n    \\\"MERGE\\\"\\n    \\\"PURGE\\\"\\n    \\\"RENAME\\\"\\n    \\\"REPLACE\\\"\\n    \\\"RESET\\\"\\n    \\\"ROLLBACK\\\"\\n    \\\"SAVEPOINT\\\"\\n    \\\"SET\\\"\\n    \\\"SHOW\\\"\\n    \\\"TRUNCATE\\\"\\n    \\\"UPDATE\\\"\\n    \\\"UPSERT\\\"\\n    \\\"USE\\\"\\n    \\\"VALUE\\\"\\n    \\\"VALUES\\\"\\n    \\\"WITH\\\"\\n    <K_SELECT>\\n    <ST_SEMICOLON>\\n\"}","error":"validate-each[1]: 문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \"SELEC\" <S_IDENTIFIER>\n    at line 1, column 1.\n\nWas expecting one of:\n\n    \"(\"\n    \"ALTER\"\n    \"ANALYZE\"\n    \"BEGIN\"\n    \"CALL\"\n    \"COMMENT\"\n    \"COMMIT\"\n    \"CREATE\"\n    \"DECLARE\"\n    \"DELETE\"\n    \"DESCRIBE\"\n    \"DROP\"\n    \"EXEC\"\n    \"EXECUTE\"\n    \"EXPLAIN\"\n    \"GRANT\"\n    \"IF\"\n    \"INSERT\"\n    \"MERGE\"\n    \"PURGE\"\n    \"RENAME\"\n    \"REPLACE\"\n    \"RESET\"\n    \"ROLLBACK\"\n    \"SAVEPOINT\"\n    \"SET\"\n    \"SHOW\"\n    \"TRUNCATE\"\n    \"UPDATE\"\n    \"UPSERT\"\n    \"USE\"\n    \"VALUE\"\n    \"VALUES\"\n    \"WITH\"\n    <K_SELECT>\n    <ST_SEMICOLON>\n","items":[{"input":{"sql":"SELECT 1 FROM dual"},"output":{},"text":"{\"success\":true,\"message\":\"문법 구조상 문제가 없습니다.\"}","error":null},{"input":{"sql":"SELEC 1 FROM dual"},"output":{},"text":"{\"success\":false,\"message\":\"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \\\"SELEC\\\" <S_IDENTIFIER>\\n    at line 1, column 1.\\n\\nWas expecting one of:\\n\\n    \\\"(\\\"\\n    \\\"ALTER\\\"\\n    \\\"ANALYZE\\\"\\n    \\\"BEGIN\\\"\\n    \\\"CALL\\\"\\n    \\\"COMMENT\\\"\\n    \\\"COMMIT\\\"\\n    \\\"CREATE\\\"\\n    \\\"DECLARE\\\"\\n    \\\"DELETE\\\"\\n    \\\"DESCRIBE\\\"\\n    \\\"DROP\\\"\\n    \\\"EXEC\\\"\\n    \\\"EXECUTE\\\"\\n    \\\"EXPLAIN\\\"\\n    \\\"GRANT\\\"\\n    \\\"IF\\\"\\n    \\\"INSERT\\\"\\n    \\\"MERGE\\\"\\n    \\\"PURGE\\\"\\n    \\\"RENAME\\\"\\n    \\\"REPLACE\\\"\\n    \\\"RESET\\\"\\n    \\\"ROLLBACK\\\"\\n    \\\"SAVEPOINT\\\"\\n    \\\"SET\\\"\\n    \\\"SHOW\\\"\\n    \\\"TRUNCATE\\\"\\n    \\\"UPDATE\\\"\\n    \\\"UPSERT\\\"\\n    \\\"USE\\\"\\n    \\\"VALUE\\\"\\n    \\\"VALUES\\\"\\n    \\\"WITH\\\"\\n    <K_SELECT>\\n    <ST_SEMICOLON>\\n\"}","error":"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \"SELEC\" <S_IDENTIFIER>\n    at line 1, column 1.\n\nWas expecting one of:\n\n    \"(\"\n    \"ALTER\"\n    \"ANALYZE\"\n    \"BEGIN\"\n    \"CALL\"\n    \"COMMENT\"\n    \"COMMIT\"\n    \"CREATE\"\n    \"DECLARE\"\n    \"DELETE\"\n    \"DESCRIBE\"\n    \"DROP\"\n    \"EXEC\"\n    \"EXECUTE\"\n    \"EXPLAIN\"\n    \"GRANT\"\n    \"IF\"\n    \"INSERT\"\n    \"MERGE\"\n    \"PURGE\"\n    \"RENAME\"\n    \"REPLACE\"\n    \"RESET\"\n    \"ROLLBACK\"\n    \"SAVEPOINT\"\n    \"SET\"\n    \"SHOW\"\n    \"TRUNCATE\"\n    \"UPDATE\"\n    \"UPSERT\"\n    \"USE\"\n    \"VALUE\"\n    \"VALUES\"\n    \"WITH\"\n    <K_SELECT>\n    <ST_SEMICOLON>\n"}]}}'::jsonb, RESULT_TEXT = NULL, ERROR_MESSAGE = 'validate-each[1]: 문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "SELEC" <S_IDENTIFIER>
    at line 1, column 1.

Was expecting one of:

    "("
    "ALTER"
    "ANALYZE"
    "BEGIN"
    "CALL"
    "COMMENT"
    "COMMIT"
    "CREATE"
    "DECLARE"
    "DELETE"
    "DESCRIBE"
    "DROP"
    "EXEC"
    "EXECUTE"
    "EXPLAIN"
    "GRANT"
    "IF"
    "INSERT"
    "MERGE"
    "PURGE"
    "RENAME"
    "REPLACE"
    "RESET"
    "ROLLBACK"
    "SAVEPOINT"
    "SET"
    "SHOW"
    "TRUNCATE"
    "UPDATE"
    "UPSERT"
    "USE"
    "VALUE"
    "VALUES"
    "WITH"
    <K_SELECT>
    <ST_SEMICOLON>
', UPDATED_AT = '09/26/2026 07:26:36.156' WHERE EXECUTION_ID = 'f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c'
 {executed in 79 msec}
2026-09-26 07:26:36  INFO [net.dstone.ai.common.config.ConfigCallLog] 

|--------------------------------------------------------------------------------------------------------------------------------------|
[WorkFlowExecutor - workFlow(id=sample-foreach-parallel)] End !!!
<output>
WorkFlowExecution[executionId=f2bfbbed-2b5b-4edd-b699-5e3a21b86c3c, workflowId=sample-foreach-parallel, caller=null, sessionId=9813ccd8-99ce-4307-8888-a8f54cbbdc19, status=FAILED, currentStepIndex=0, context={inputs={sqlList=[SELECT 1 FROM dual, SELEC 1 FROM dual], message=foreach 테스트}, steps={validate-each={input=null, output={}, text={"success":true,"message":"문법 구조상 문제가 없습니다."}{"success":false,"message":"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \"SELEC\" <S_IDENTIFIER>\n    at line 1, column 1.\n\nWas expecting one of:\n\n    \"(\"\n    \"ALTER\"\n    \"ANALYZE\"\n    \"BEGIN\"\n    \"CALL\"\n    \"COMMENT\"\n    \"COMMIT\"\n    \"CREATE\"\n    \"DECLARE\"\n    \"DELETE\"\n    \"DESCRIBE\"\n    \"DROP\"\n    \"EXEC\"\n    \"EXECUTE\"\n    \"EXPLAIN\"\n    \"GRANT\"\n    \"IF\"\n    \"INSERT\"\n    \"MERGE\"\n    \"PURGE\"\n    \"RENAME\"\n    \"REPLACE\"\n    \"RESET\"\n    \"ROLLBACK\"\n    \"SAVEPOINT\"\n    \"SET\"\n    \"SHOW\"\n    \"TRUNCATE\"\n    \"UPDATE\"\n    \"UPSERT\"\n    \"USE\"\n    \"VALUE\"\n    \"VALUES\"\n    \"WITH\"\n    <K_SELECT>\n    <ST_SEMICOLON>\n"}, error=validate-each[1]: 문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "SELEC" <S_IDENTIFIER>    at line 1, column 1.Was expecting one of:    "("    "ALTER"    "ANALYZE"    "BEGIN"    "CALL"    "COMMENT"    "COMMIT"    "CREATE"    "DECLARE"    "DELETE"    "DESCRIBE"    "DROP"    "EXEC"    "EXECUTE"    "EXPLAIN"    "GRANT"    "IF"    "INSERT"    "MERGE"    "PURGE"    "RENAME"    "REPLACE"    "RESET"    "ROLLBACK"    "SAVEPOINT"    "SET"    "SHOW"    "TRUNCATE"    "UPDATE"    "UPSERT"    "USE"    "VALUE"    "VALUES"    "WITH"    <K_SELECT>    <ST_SEMICOLON>, items=[{input={sql=SELECT 1 FROM dual}, output={}, text={"success":true,"message":"문법 구조상 문제가 없습니다."}, error=null}, {input={sql=SELEC 1 FROM dual}, output={}, text={"success":false,"message":"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \"SELEC\" <S_IDENTIFIER>\n    at line 1, column 1.\n\nWas expecting one of:\n\n    \"(\"\n    \"ALTER\"\n    \"ANALYZE\"\n    \"BEGIN\"\n    \"CALL\"\n    \"COMMENT\"\n    \"COMMIT\"\n    \"CREATE\"\n    \"DECLARE\"\n    \"DELETE\"\n    \"DESCRIBE\"\n    \"DROP\"\n    \"EXEC\"\n    \"EXECUTE\"\n    \"EXPLAIN\"\n    \"GRANT\"\n    \"IF\"\n    \"INSERT\"\n    \"MERGE\"\n    \"PURGE\"\n    \"RENAME\"\n    \"REPLACE\"\n    \"RESET\"\n    \"ROLLBACK\"\n    \"SAVEPOINT\"\n    \"SET\"\n    \"SHOW\"\n    \"TRUNCATE\"\n    \"UPDATE\"\n    \"UPSERT\"\n    \"USE\"\n    \"VALUE\"\n    \"VALUES\"\n    \"WITH\"\n    <K_SELECT>\n    <ST_SEMICOLON>\n"}, error=문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "SELEC" <S_IDENTIFIER>    at line 1, column 1.Was expecting one of:    "("    "ALTER"    "ANALYZE"    "BEGIN"    "CALL"    "COMMENT"    "COMMIT"    "CREATE"    "DECLARE"    "DELETE"    "DESCRIBE"    "DROP"    "EXEC"    "EXECUTE"    "EXPLAIN"    "GRANT"    "IF"    "INSERT"    "MERGE"    "PURGE"    "RENAME"    "REPLACE"    "RESET"    "ROLLBACK"    "SAVEPOINT"    "SET"    "SHOW"    "TRUNCATE"    "UPDATE"    "UPSERT"    "USE"    "VALUE"    "VALUES"    "WITH"    <K_SELECT>    <ST_SEMICOLON>}]}}, previous={input=null, output={}, text={"success":true,"message":"문법 구조상 문제가 없습니다."}{"success":false,"message":"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \"SELEC\" <S_IDENTIFIER>\n    at line 1, column 1.\n\nWas expecting one of:\n\n    \"(\"\n    \"ALTER\"\n    \"ANALYZE\"\n    \"BEGIN\"\n    \"CALL\"\n    \"COMMENT\"\n    \"COMMIT\"\n    \"CREATE\"\n    \"DECLARE\"\n    \"DELETE\"\n    \"DESCRIBE\"\n    \"DROP\"\n    \"EXEC\"\n    \"EXECUTE\"\n    \"EXPLAIN\"\n    \"GRANT\"\n    \"IF\"\n    \"INSERT\"\n    \"MERGE\"\n    \"PURGE\"\n    \"RENAME\"\n    \"REPLACE\"\n    \"RESET\"\n    \"ROLLBACK\"\n    \"SAVEPOINT\"\n    \"SET\"\n    \"SHOW\"\n    \"TRUNCATE\"\n    \"UPDATE\"\n    \"UPSERT\"\n    \"USE\"\n    \"VALUE\"\n    \"VALUES\"\n    \"WITH\"\n    <K_SELECT>\n    <ST_SEMICOLON>\n"}, error=validate-each[1]: 문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "SELEC" <S_IDENTIFIER>    at line 1, column 1.Was expecting one of:    "("    "ALTER"    "ANALYZE"    "BEGIN"    "CALL"    "COMMENT"    "COMMIT"    "CREATE"    "DECLARE"    "DELETE"    "DESCRIBE"    "DROP"    "EXEC"    "EXECUTE"    "EXPLAIN"    "GRANT"    "IF"    "INSERT"    "MERGE"    "PURGE"    "RENAME"    "REPLACE"    "RESET"    "ROLLBACK"    "SAVEPOINT"    "SET"    "SHOW"    "TRUNCATE"    "UPDATE"    "UPSERT"    "USE"    "VALUE"    "VALUES"    "WITH"    <K_SELECT>    <ST_SEMICOLON>, items=[{input={sql=SELECT 1 FROM dual}, output={}, text={"success":true,"message":"문법 구조상 문제가 없습니다."}, error=null}, {input={sql=SELEC 1 FROM dual}, output={}, text={"success":false,"message":"문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: \"SELEC\" <S_IDENTIFIER>\n    at line 1, column 1.\n\nWas expecting one of:\n\n    \"(\"\n    \"ALTER\"\n    \"ANALYZE\"\n    \"BEGIN\"\n    \"CALL\"\n    \"COMMENT\"\n    \"COMMIT\"\n    \"CREATE\"\n    \"DECLARE\"\n    \"DELETE\"\n    \"DESCRIBE\"\n    \"DROP\"\n    \"EXEC\"\n    \"EXECUTE\"\n    \"EXPLAIN\"\n    \"GRANT\"\n    \"IF\"\n    \"INSERT\"\n    \"MERGE\"\n    \"PURGE\"\n    \"RENAME\"\n    \"REPLACE\"\n    \"RESET\"\n    \"ROLLBACK\"\n    \"SAVEPOINT\"\n    \"SET\"\n    \"SHOW\"\n    \"TRUNCATE\"\n    \"UPDATE\"\n    \"UPSERT\"\n    \"USE\"\n    \"VALUE\"\n    \"VALUES\"\n    \"WITH\"\n    <K_SELECT>\n    <ST_SEMICOLON>\n"}, error=문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "SELEC" <S_IDENTIFIER>    at line 1, column 1.Was expecting one of:    "("    "ALTER"    "ANALYZE"    "BEGIN"    "CALL"    "COMMENT"    "COMMIT"    "CREATE"    "DECLARE"    "DELETE"    "DESCRIBE"    "DROP"    "EXEC"    "EXECUTE"    "EXPLAIN"    "GRANT"    "IF"    "INSERT"    "MERGE"    "PURGE"    "RENAME"    "REPLACE"    "RESET"    "ROLLBACK"    "SAVEPOINT"    "SET"    "SHOW"    "TRUNCATE"    "UPDATE"    "UPSERT"    "USE"    "VALUE"    "VALUES"    "WITH"    <K_SELECT>    <ST_SEMICOLON>}]}}, resultText=null, errorMessage=validate-each[1]: 문법 오류 - net.sf.jsqlparser.parser.ParseException: Encountered unexpected token: "SELEC" <S_IDENTIFIER>    at line 1, column 1.Was expecting one of:    "("    "ALTER"    "ANALYZE"    "BEGIN"    "CALL"    "COMMENT"    "COMMIT"    "CREATE"    "DECLARE"    "DELETE"    "DESCRIBE"    "DROP"    "EXEC"    "EXECUTE"    "EXPLAIN"    "GRANT"    "IF"    "INSERT"    "MERGE"    "PURGE"    "RENAME"    "REPLACE"    "RESET"    "ROLLBACK"    "SAVEPOINT"    "SET"    "SHOW"    "TRUNCATE"    "UPDATE"    "UPSERT"    "USE"    "VALUE"    "VALUES"    "WITH"    <K_SELECT>    <ST_SEMICOLON>, createdAt=2026-09-25T22:26:35.782894900Z, updatedAt=2026-09-25T22:26:36.156657Z]
|--------------------------------------------------------------------------------------------------------------------------------------|
</pasted_content id="643e">

---

## 답변

로그를 보면 엔진은 정상 동작했습니다. 샘플에 일부러 넣은 오타(`SELEC`) 때문에 FAIL로 끝난 것입니다. 기본값은 성공하도록 바꾸는 게 맞겠습니다. 다른 샘플 중에도 기본값으로 실패하는 게 있는지 확인합니다.

---

## 답변

화면 샘플값이 YAML 주석 예시와 같다고 문서에 적어 두었으니, YAML 주석도 같이 맞춥니다.

---

## 답변

문서의 curl 예시도 같은 값을 쓰면서 결과를 `DONE`으로 적어 두어 실제 결과와 맞지 않습니다. 이것도 고칩니다.

---

## 답변

엔진 오류가 아니라 제가 넣은 샘플값이 문제였습니다. `sample-foreach-parallel`의 기본값에 문법 오류가 나도록 일부러 쓴 SQL(`SELEC 1 FROM dual`)이 들어 있었습니다. 그래서 로그처럼 `validate-each[1]`이 실패하고 실행 전체가 FAILED로 끝났습니다. YAML 주석의 "실패 동작 확인용" 예시를 그대로 옮기다 보니 그렇게 됐습니다.

**수정 내용** (`a9b0787`, `main`에 push 완료)
- **`workflow.js`**: 기본값을 문법이 맞는 SQL 두 개(`SELECT 1 FROM dual`, `SELECT name FROM member WHERE id = 1`)로 바꿨습니다. 실패하는 경우를 보는 방법은 안내 문구로 옮겼습니다.
- **`sample-foreach-parallel.yml`**: 맨 위 주석의 예시를 같은 값으로 맞췄습니다. 주석만 바뀌어서 엔진 동작은 그대로입니다.
- **`docs/09.dstone-ai-engine.md`**: curl 예시도 같은 SQL을 쓰면서 결과를 `DONE`으로 적어 두었는데, 실제로는 실패하는 예시였습니다. 이것도 함께 고쳤습니다.

다른 샘플의 기본값은 YAML 설명 기준으로 SUCCESS가 나오게 되어 있습니다. 실행해서 확인한 건 아닙니다. `sample-tool-gated-external`은 화이트리스트가 비어 있으면 각 step이 실패하지만, 마지막에는 SUCCESS로 끝나게 만들어져 있습니다.

바뀐 샘플값은 dstone-boot를 다시 배포해야 화면에 나옵니다. 저는 재배포도, 새 값으로 다시 실행해 보는 것도 하지 않았습니다.
