# localcommandcaveatTh

> 생성일시: 2026-11-28 16:41

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="abb6">
dstone-ai-engine 에서 아래의 내용 확인해줘.

<확인 요청>
스탭 실행아이디 : 42359899-81ce-4639-b539-52b9889f5a89

Tools - browser_wait_for] Call 정보 : ToolCallGate.invoke() Start !!!            
            <input>            
            toolName=[browser_wait_for], toolInput=[{"time": 20}], toolContext=[ToolContext[context={net.dstone.ai.workflow.executionId=42359899-81ce-4639-b539-52b9889f5a89, net.dstone.ai.security.caller=, net.dstone.ai.workflow.stepId=step35}]], delegate=[ConfigMcp.SerializedToolCallback[delegate=org.springframework.ai.mcp.SyncMcpToolCallback@5268e0a9,serverLock=java.lang.Object@26b13a42]]            
            </input>            
2026-11-28 16:35:52  INFO [net.dstone.ai.common.config.ConfigCallLog] +--->[SERVICE ] {WorkFlowExecutionService.find(executionId=[42359899-81ce-4639-b539-52b9889f5a89])}
2026-11-28 16:36:04 ERROR [org.springframework.ai.mcp.SyncMcpToolCallback] Exception while tool calling: 
reactor.core.Exceptions$ReactiveException: java.util.concurrent.TimeoutException: Did not observe any item or terminal signal within 20000ms in 'source(MonoDeferContextual)' (and no fallback has been configured)
    at reactor.core.Exceptions.propagate(Exceptions.java:410)
    at reactor.core.publisher.BlockingSingleSubscriber.blockingGet(BlockingSingleSubscriber.java:102)
    at reactor.core.publisher.Mono.block(Mono.java:1773)
    at io.modelcontextprotocol.client.McpSyncClient.callTool(McpSyncClient.java:237)
    at org.springframework.ai.mcp.SyncMcpToolCallback.call(SyncMcpToolCallback.java:134)
    at net.dstone.ai.common.config.ConfigMcp$SerializedToolCallback.call(ConfigMcp.java:251)
    at net.dstone.ai.runtime.tool.ToolCallGate.invoke(ToolCallGate.java:112)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
    at java.base/java.lang.reflect.Method.invoke(Method.java:580)
</pasted_content id="abb6">

---

## 답변

스텝 실행 정보와 MCP timeout 설정부터 확인하겠습니다.

---

## 답변

원인은 거의 확인됐고(MCP 클라이언트의 요청 제한 20초), 지금은 영향 범위와 설정할 자리를 보고 있습니다.

---

## 답변

원인은 **엔진의 MCP 요청 제한 시간(20초)**입니다. Agent나 테스트 서버, Playwright의 문제는 아닙니다. 아직 고친 것은 없습니다.

## 원인

| 구분 | 내용 |
|---|---|
| 엔진 | `ConfigMcp.java:119-121`이 `McpClient.sync(...)`를 만들면서 `requestTimeout`을 지정하지 않습니다. MCP Java SDK 2.0.0의 기본값인 **20초**가 적용됩니다(jar 바이트코드로 확인). |
| Playwright MCP | `browser_wait_for`의 `time`은 **최대 30초**까지 받습니다. 그 시간을 다 기다린 뒤 화면 스냅숏까지 만들어 응답합니다(WSL에 설치된 0.0.86 코드 기준). |
| 결과 | `{"time": 20}`은 대기만 20초라 응답이 항상 20초를 넘깁니다. `time`이 20 이상이면 실패합니다. |

## 이번 실행에서 확인한 것
Windows 쪽 로그(`/mnt/d/AppHome/framework/dstone/LOGS/.../execution.log`)를 읽기만 했습니다.

- 실행 `42359899`의 동작 검증 step35(`sm-verifier-agent`)에서 **같은 오류가 3번** 났습니다(16:36:04, 16:37:17, 16:38:47). 모두 `{"time": 20}`이었습니다.
- 오류 뒤에도 Tool 호출은 이어졌습니다. 실행이 바로 죽지는 않았지만, 실패할 때마다 20초와 Tool 호출 1회(한도 120회)를 버립니다.
- 최종 판정에 영향이 있었는지는 확인하지 않았습니다.

## 영향 범위
이 제한은 모든 MCP Tool에 걸립니다. 예를 들어 Playwright의 화면 이동 제한은 기본 60초라서, 느린 화면에서는 `browser_navigate`나 `browser_click`도 같은 오류가 날 수 있습니다.

## 수정안
프롬프트에 "20초 이상 기다리지 마라"를 넣는 방법은 권하지 않습니다. 엔진에서 난 실패이고 느린 화면 문제도 남기 때문입니다.

1. `McpServerDefinition`에 선택 항목 `requestTimeoutSeconds`를 추가합니다. 비워 두면 지금처럼 20초입니다.
2. `ConfigMcp.connectOne`에서 값이 있으면 `.requestTimeout(Duration.ofSeconds(...))`를 적용합니다.
3. `mcp/common/browser.yml`에 **90초**를 지정합니다. 화면 이동 60초에 스냅숏 시간을 더한 여유값입니다.
4. `docs/09`의 MCP 정의 설명을 함께 고칩니다.

범용 설정이라 특정 Workflow나 대상 앱에 묶이지 않습니다. MCP 정의는 다시 읽지 않으므로 반영하려면 엔진을 다시 빌드해 띄워야 합니다. 실행 중인 jar는 그 자리에서 빌드하지 않겠습니다.

이대로 수정할까요?

---

## 질문

어떤 내용을 호출하다가 타임아웃이 난건데?

---

## 답변

타임아웃 앞뒤의 호출을 로그에서 따라가 보겠습니다.

---

## 답변

**어떤 URL이나 화면을 호출하다 난 타임아웃이 아닙니다.** 세 번 모두 `browser_wait_for {"time": 20}`, 즉 아무 조건 없이 **20초 동안 가만히 기다리는 호출**이었습니다. 대기 20초에 응답 시간이 더해져 MCP 제한 20초를 넘긴 것입니다.

## 왜 기다리고 있었나
`sm-verifier-agent`가 남긴 말을 보면, 테스트 서버의 웹앱이 **다시 로딩되는 중**이라고 보고 끝나기를 기다렸습니다.

| 타임아웃 | 직전에 하던 일 | Agent가 남긴 말(요약) |
|---|---|---|
| 16:36:04 | 업무지원 → 회의실예약 메뉴 클릭 후 화면 확인 | "아직 리로딩 중이다. 더 오래 기다리겠다" |
| 16:37:17 | `index.go`를 다시 열고 화면 확인 | "웹앱이 계속 리로딩된다. 더 기다렸다 한 번 더 해 보겠다" |
| 16:38:47 | 회의실예약 메뉴를 다시 클릭하고 화면 확인 | "계속 리로딩된다. 길게 기다리고 서버 로그를 보겠다" |

확인하려던 것은 **저장한 예약이 회의실예약 시간표에 표시되는지**였습니다. 예약 데이터가 DB에 저장된 것은 그 전에 조회로 확인했습니다.

## 더 큰 문제는 테스트 서버
타임아웃은 곁가지이고, 검증을 막은 것은 테스트 서버의 불안정입니다.

- Agent가 서버 로그에서 `이 웹 애플리케이션 인스턴스는 이미 중지되었습니다`와 `NoClassDefFoundError`를 봤다고 적었습니다.
- 16:39:11에 `startTestServer`를 다시 불렀지만, 16:39:26에 **"테스트 환경 불안정으로 시간표 화면 확인이 막혔다"**며 마무리하겠다고 판단했습니다.

따라서 MCP 제한 시간을 늘려도 20초 대기 오류만 없어지고, 시간표 확인이 막힌 문제는 그대로입니다. 리로딩이 왜 반복됐는지는 아직 확인하지 않았습니다. 해당 실행의 `server.log`를 보면 원인을 추적할 수 있는데, 이어서 볼까요?
