# localcommandcaveatTh

> 생성일시: 2026-11-28 17:11

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

---

## 질문

응 확인해봐.

---

## 답변

원인은 **테스트 서버(엔진 쪽 `startTestServer`)의 결함**입니다. 웹앱을 한 번 다시 올린 뒤, 테스트 드라이버가 **버려진 클래스로더에서 만든 DB 연결**을 계속 써서 날짜 컬럼 조회가 실패했습니다. 서버 로그와 코드로 확인했고, 따로 재현해 보지는 않았습니다. 아직 고친 것은 없습니다.

## 경과
| 시각 | 일어난 일 |
|---|---|
| 16:27 | 테스트 서버 기동 |
| 16:32:41 | `sm-auto-fix-agent`가 `meetingReserveRegi.jsp` 1개를 고치고 `startTestServer` 호출 |
| 16:32:49 | 엔진이 **context reload** 실행. 기존 웹앱 클래스로더가 중지됨 |
| 16:34:12 ~ | `NoClassDefFoundError: com/mysql/cj/result/SqlDateValueFactory`가 반복. 화면 조회와 `dbQuery`가 모두 실패 |
| 16:39:11 | `sm-verifier-agent`가 `startTestServer`를 다시 호출했지만 "바뀐 것이 없어 그대로 씁니다"라는 답을 받아 복구되지 않음 |

`sm-verifier-agent`는 "웹앱이 계속 리로딩된다"고 판단했지만 **재로딩은 한 번뿐**이었습니다. 그래서 기다려도 나아질 수 없었고, 20초 대기와 타임아웃도 여기서 나왔습니다.

## 원인 두 가지

**1. 재로딩 후 DB 연결이 깨짐 (핵심)**
- 테스트 드라이버는 Tomcat 공용 `lib`에 있어 재로딩해도 살아남습니다. 테스트 데이터를 커밋하지 않고 유지하려고 실제 DB 연결 하나를 계속 잡습니다(`Holder.physical`).
- 그 연결의 MySQL 드라이버는 **웹앱의 `WEB-INF/lib`에서 로딩**한 것입니다(`Holder.connectReal`이 스레드의 클래스로더를 사용).
- 재로딩 뒤에도 연결은 열린 상태라 그대로 재사용됩니다. 이때까지 로딩하지 않은 클래스(날짜 변환 클래스 등)는 중지된 로더에서 가져올 수 없어 실패합니다.
- 그래서 이미 로딩된 클래스만 쓰는 조회는 성공하고, 날짜 컬럼을 읽는 조회만 실패했습니다.

**2. JSP만 고쳐도 재로딩함**
- 도구 설명은 "JSP·정적 파일은 바로 반영"인데, `TestServerTool.java:492`는 `WEB-INF/` 아래 파일이 바뀌면 모두 재로딩합니다.
- 이번에 고친 것은 `WEB-INF/views` 아래의 JSP 하나였습니다.

## 영향
- 16:32 이후의 시간표 표시 확인은 **앱 결함인지 판정할 수 없습니다.** 이 검증 결과의 해당 항목은 믿지 말고, 수정 후 step35를 다시 돌려야 합니다.
- 재로딩이 필요한 수정(Java, XML)을 하면 다른 프로젝트에서도 같은 문제가 날 수 있습니다.

## 수정안
1. **실제 DB 드라이버를 웹앱과 분리해 로딩합니다.** 테스트 드라이버가 자체 클래스로더로 `WEB-INF/lib`의 jar를 읽게 하면, 재로딩해도 연결과 테스트 데이터가 유지됩니다. `log4jdbc`처럼 드라이버를 감싸는 경우까지 시험해야 합니다.
2. **JSP는 재로딩 없이 반영합니다.** `WEB-INF/` 아래라도 JSP 계열(`.jsp`, `.jspf`, `.tag`)은 복사만 합니다.
3. **MCP 요청 제한 시간 설정을 추가합니다**(앞서 제안한 90초). 급한 것은 아니지만 느린 화면에 대비할 수 있습니다.

2번만 고치면 이번 상황은 피하지만 Java를 고치면 다시 나므로, **1번과 2번을 함께** 고치는 것을 권합니다. 1~3번을 모두 진행할까요?

---

## 질문

<pasted_content id="abb6">
dstone-ai-engine 에서 아래의 내용 확인해줘.

<확인 요청>
스탭 실행아이디 : 42359899-81ce-4639-b539-52b9889f5a89
현재 재개발내용을 잘 이행하는지 추적해줘.
</pasted_content id="abb6">

---

## 답변

실행 `42359899`의 현재 상태와 재개발 지시 내용부터 확인하겠습니다.

---

## 답변

개발이 끝났습니다. 이제 Agent가 설명한 근본 원인이 실제 코드와 맞는지 대조합니다.

---

## 답변

**이번 지시는 이행되지 않았습니다.** `sm-developer-agent`가 코드를 고치고 컴파일·형식 검사도 통과했지만, **고친 곳이 원인이 아니라 삭제 버튼은 여전히 반응하지 않을 것**입니다. 대상 앱의 코드와 Kendo 라이브러리를 읽어 확인했고, 화면에서 직접 눌러 보지는 않았습니다.

## 진행 경과
| 시각 | 내용 | 결과 |
|---|---|---|
| 16:53:47 | **재개발(서버 없이)**: ① 삭제 버튼 무반응 ② 예약 후 서버 오류 | 16:54에 둘 다 고쳤다고 보고 |
| 16:59:19 | **재개발(서버 없이)**: 삭제 버튼 무반응 (재발) | 17:02에 `meetingRoom.jsp` 수정 |
| 17:02:43 | step19 **개발완료 승인** 대기 | 17:03 기준으로 새 결정 없음 |

## 삭제 버튼: 실제 원인
**`deleteMeetingRoom()` 함수가 아예 호출되지 않습니다.**

- `meetingRoom.jsp:252`의 버튼은 `name: "destroy"`에 `click: deleteMeetingRoom`을 지정했습니다.
- 이 앱의 Kendo UI(v2016.1.112)는 `destroy` 버튼에 `k-grid-delete` 클래스를 붙입니다.
- 사용자 지정 `click`은 이름으로 만든 선택자 `a.k-grid-destroy`에 연결합니다. 클래스가 서로 달라 **클릭이 연결되지 않습니다.**
- Kendo 기본 삭제 동작은 `editable` 설정이 있어야 붙는데, 이 그리드에는 없습니다. 그래서 아무 일도 일어나지 않습니다.
- `수정` 버튼은 `edit` → `k-grid-edit`로 이름이 맞아 정상 동작합니다.

**고칠 곳은 한 줄**입니다. 252행의 `name: "destroy"`를 `name: "remove"`처럼 Kendo 예약어가 아닌 이름으로 바꾸면 됩니다.

## Agent가 놓친 것
세 번의 수정(`genexon.confirm` → `window.confirm` → 직접 만든 확인창)은 모두 **실행되지 않는 함수의 내부**만 바꿨습니다.

- "`window.confirm`이 iframe에서 차단된다"는 설명은 **근거가 없습니다.** 앱의 화면과 공통 스크립트에서 `sandbox` 설정을 찾지 못했습니다.
- 이 잘못된 원인이 `03-1.design.md`의 수정 이력에 남아, 다음 재개발도 같은 방향으로 갈 수 있습니다.
- "서버 없이"를 선택해 동작 확인 없이 완료로 보고됐습니다.

## 예약 서버 오류 (16:53 지시의 ②)
날짜·시간 컬럼을 `DATE_FORMAT`, `TIME_FORMAT`으로 문자열로 바꾸는 수정이 매퍼에 반영돼 있습니다. 방향은 타당해 보이지만 **동작은 확인되지 않았습니다.** 동작 검증을 돌리면 앞서 확인한 테스트 서버의 재로딩 결함에 걸릴 수 있습니다.

## 권장 조치
step19에서 승인하지 말고 **재개발(서버 없이)**에 아래 의견을 넣으십시오.

> 삭제 버튼은 deleteMeetingRoom()이 호출되지 않는 것이 원인이다. Kendo 그리드에서 command name "destroy"는 class가 k-grid-delete가 되어 사용자 click(a.k-grid-destroy)이 연결되지 않는다. meetingRoom.jsp의 command name을 예약어가 아닌 이름으로 바꾸고, 확인창은 원래 프로젝트 방식으로 되돌려라. 설계서 수정 이력의 iframe 차단 설명은 바로잡아라.

재발을 막으려면 다음처럼 나눠 반영하는 것을 권합니다.
- **범용 원칙(Skill):** 같은 증상이 반복되면 원인을 정하기 전에 해당 코드가 실행되는지부터 근거로 확인합니다.
- **프로젝트 Skill:** 이 그리드의 `destroy` 이름 함정을 적습니다.
- **평가 사례:** 이번 step17 입력을 사례로 만듭니다.

저는 이번에 파일을 고치거나 승인 결정을 내리지 않았습니다.

---

## 질문

너는 이렇게 찾는 것을 왜 내 Agent 는 찾지 못하지?

---

## 답변

추측으로 답하지 않고, Agent가 실제로 무엇을 찾아봤고 어떤 도구를 가졌는지 확인하겠습니다.

---

## 답변

**모델 차이만은 아닙니다. 가장 큰 이유는 Agent의 검색 도구로는 근거가 되는 코드를 볼 수 없었다는 것입니다.** 저는 Python으로 라이브러리 파일에서 일치한 위치 주변을 잘라 봤지만, Agent에게는 그런 수단이 없습니다.

## 1. 도구가 근거를 보여 주지 못함
`sm-developer-agent`도 17:01:34에 Kendo 라이브러리를 검색했습니다. 그런데 `searchInFiles`에는 제약이 있습니다(`FileTool.java`).

| 제약 | 결과 |
|---|---|
| 1MB 넘는 파일은 건너뜀 | 화면이 실제로 쓰는 `kendo.all.min.js`(2.6MB)는 **검색되지 않았고, 건너뛰었다는 안내도 없음** |
| 긴 줄은 **앞부분만** 보여 줌(200자) | 압축된 js는 한 줄이 수만 자라서, 일치했다고 나오지만 **일치한 부분은 보이지 않음** |
| 정규식이 아닌 문자열 검색 | 16:59에 `confirm\(`로 검색해 0건이 나옴 |

`readFileLines`도 줄 단위로 읽어서, 이런 파일의 중간 부분을 골라 보기 어렵습니다. 원인인 클릭 연결 코드는 Agent가 사실상 볼 수 없었습니다.

## 2. 볼 수 있던 것도 확인하지 않음
- **전제를 의심하지 않았습니다.** 함수가 호출되는지 확인하지 않고, 확인창이 안 뜬다고 보고 함수 안쪽만 고쳤습니다.
- **이전 이력에 끌려갔습니다.** 설계서에 적힌 "확인창 문제"라는 원인에서 출발했습니다. "같은 증상이면 다른 층을 보라"는 Skill도 읽었지만, 확인창 방식만 바꿨습니다.
- **비교를 놓쳤습니다.** 정상 화면인 `sampleBoard.jsp`를 읽었습니다. 그 화면은 `destroy`에 `click`이 없고 `editable`이 있는데, 이 차이를 짚지 못했습니다.
- **문서 검색을 하지 않았습니다.** `testapp-basics`는 Kendo 화면의 오류를 고치기 전에 문서를 찾으라고 하는데, 호출이 0건입니다. 문서에 답이 있었는지는 확인하지 않았습니다.
- **프로젝트 Skill을 늦게 읽었습니다.** `gnx-crud-screen`은 코드를 고친 뒤인 17:02:39에 불렀습니다.

## 3. 구조가 추측을 허용함
- **"서버 없이"** 경로라 눌러 볼 수 없었습니다.
- `troubleshooting` Skill은 확인 못 한 것을 `unresolved`에 적으라고 하는데, **개발 Agent의 출력에는 그 칸이 없습니다.** 그래서 추측한 원인이 확정된 수정으로 보고되고 설계서에도 남았습니다.

## 모델 차이
이 Agent는 POC용 `qwen3.8-27b`이고, 저는 더 큰 모델이며 도구도 자유롭습니다. Kendo의 예약된 버튼 이름을 의심하는 사전 지식 차이도 있었을 것입니다. 각 요인의 비중은 측정해 보지 않아 모릅니다.

## 고칠 순서

| 순서 | 구분 | 내용 |
|---|---|---|
| 1 | 엔진(범용 Tool) | `searchInFiles`가 긴 줄에서는 **일치한 위치 주변**을 보여 주고, 크기 때문에 건너뛴 파일을 알려 줌 |
| 2 | 범용 Skill | "반응 없음"은 **핸들러가 연결되고 실행되는 근거**부터 확인. 필요하면 라이브러리 코드까지 확인 |
| 3 | Agent 출력 | `unresolved` 칸을 추가해, 확인 못 한 원인을 승인 화면에 보이게 함 |
| 4 | 프로젝트 Skill | Kendo `destroy` 버튼 이름의 함정 |
| 5 | 평가 사례 | 이번 step17 입력을 저장해 위 변경 전후와 모델별 결과를 비교 |

4번만 넣으면 이번 문제는 풀리겠지만 다음 라이브러리에서 같은 일이 반복됩니다. **1~3번을 먼저 하고, 4번 없이 5번으로 찾아내는지** 보는 것을 권합니다. 1번부터 진행할까요?
