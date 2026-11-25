# localcommandcaveatTh

> 생성일시: 2026-11-25 13:03

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

testserver 와 sendboxserver 의 차이와 언제 호출하는지 확인해줘.

---

## 답변

No Java hits? Let me check the source and definitions more directly.

---

## 답변

둘 다 `dstone-ai-engine`의 같은 Tool 클래스 `tools.build.TestServerTool`에 있는 메소드입니다. 원본 소스에 개발 결과를 얹은 사본을 빈 포트로 띄운다는 점은 같고, **DB를 떼어 놓느냐**가 다릅니다.

## 차이

| | `startTestServer` (임시 시험 서버) | `startSandboxServer` (격리 시험 서버) |
|---|---|---|
| DB | 원본 설정 그대로 **원래 DB에 붙음** | 띄울 때마다 프로젝트 DB를 **일회용 DB로 복제**해서 붙음 |
| DDL | 반영 안 함 | `ddlFile`을 복제본에만 반영 |
| 원본 폴더 경로(업로드 등) | 원래 경로 그대로 | 사본 쪽으로 돌림 (`sandbox.TestSandbox`) |
| `projectId` | 선택 (Java 버전 찾기, 사본 폴더 이름에 씀) | **필수** (복제할 DB를 프로젝트 관리에서 찾음) |
| 띄우는 방식 | WAR+Tomcat, 또는 Maven 플러그인(`tomcat7:run`, `spring-boot:run`, `jetty:run`) | 빌드 결과만 띄움 (Boot는 `java -jar`, WAR는 설치된 Tomcat). 플러그인 방식은 격리를 확인할 수 없어서 쓰지 않음 |
| 실패할 때 | `ToolOutcome.fail` | 원래 DB로 대신 띄우지 않고 실패함. `cause`로 원인을 나눔: `code`(개발 결과 문제, 고치면 됨) / `environment`(환경 문제, 코드를 고쳐도 안 됨) |
| 돌려주는 값 | 접속주소, 서버로그, 시험계정 | 접속주소, 서버로그, `dbQuery`에 쓸 복제본 별칭 `sandbox-...`, 원래 환경과 다른 점 |
| 화면에서 등록·수정·삭제 | 원래 DB가 바뀜. 자기가 만든 시험 데이터로만 확인해야 함 | 해도 됨 |

같은 점:
- 사본 폴더 `{작업 루트}/{프로젝트ID}-server/app`을 같이 씁니다.
- 내리는 것은 `stopTestServer` 하나입니다.
- Agent가 내리지 않아도 엔진이 내립니다. 띄운 step 다음 step까지만 두고, 다음 step이 같은 입력으로 부르면 그 서버를 그대로 씁니다.

(`TestServerTool.java:101-107`, `:190-240`)

## 언제 호출되나

원칙은 `CLAUDE.md`와 `docs/09` 11.2절에 있습니다. 자동으로 쓰기나 DDL을 하는 시험은 `startSandboxServer`로만 합니다. `startTestServer`는 사람이 있는 경로에서, 격리가 안 될 때 쓰는 예비 수단입니다.

| 호출하는 곳 | 쓰는 것 |
|---|---|
| `sm-workflow` step29 "33-격리 기동 확인" (TOOL step) | `startSandboxServer` |
| `sm-workflow` step45 "43-격리 기동만 확인" (설계 승인에서 `승인(개발진행, 기동만 확인)`을 고른 경우) | `startSandboxServer` |
| 동작 검증 Agent `sm-verifier-agent` (step35) | `startSandboxServer`만 (`requiredTools`에 있음. 바로 앞 step29가 띄운 서버를 이어받음) |
| 자동 수정 Agent `sm-auto-fix-agent` | `startSandboxServer`만. 사람 없이 돌기 때문에 `startTestServer`는 일부러 주지 않음 |
| 전문가 해결 Agent `sm-troubleshooter-agent` (step22) | `startSandboxServer`를 먼저 부름. **`cause: environment`로 실패했을 때만** `startTestServer`로 넘어감 (1.8.0부터) |
| Agent 평가 `EvalRunner` | 사례에 `http`/`dbQuery` 채점이 있으면 `startSandboxServer` |

정리하면, 지금 `startTestServer`를 실제로 부르는 곳은 전문가 해결 Agent가 격리에 실패한 경우 하나뿐입니다.

## 고쳐야 할 문서

`docs/software/16.playwright-mcp.md` 59번 줄과 67번 줄은 아직 `sm-troubleshooter-agent`가 기본으로 `startTestServer`를 쓴다고 적혀 있습니다. 1.8.0 이전 설명입니다. 원하시면 지금 동작에 맞게 고치겠습니다.

---

## 질문

그렇다면 전문가해결이 굳이 testserver 를 호출하는 이유는?

---

## 답변

이유는 하나입니다. **격리 시험 서버가 띄울 수 없는 프로젝트에서도 화면 재현은 해야 하기 때문**입니다. 이 경로에는 사람이 있어서 원래 DB에 붙는 위험을 감수하기로 한 것입니다.

## 1. 격리 서버가 지원하는 범위가 좁습니다

`startSandboxServer`는 격리를 보장하지 못하면 원래 DB로 대신 띄우지 않고 `cause: environment`로 실패합니다. 지원 범위는 `docs/09` 11.2절 "지원 범위"에 있습니다.

| 항목 | 격리 서버가 못 띄우는 것 |
|---|---|
| DB | SQL Server, MariaDB, Tibero 등 (MySQL·PostgreSQL·Oracle만 됨) |
| 빌드·기동 | Gradle, 다중 모듈, Maven 플러그인 기동 (`tomcat7:run`, `jetty:run` 등) |
| 접속 수단 | NIO 클라이언트(Lettuce, Kafka, MongoDB, Netty, SQL Server 드라이버 등). 들어 있으면 SOCKS 가드를 우회할 수 있어서 아예 띄우지 않음 |
| 계정 | 프로젝트 관리에 앱이 쓰는 DB 계정이 없는 경우 |

`startTestServer`는 DB를 건드리지 않으니 이런 제약이 없습니다. Maven 플러그인 방식으로도 띄울 수 있어서 더 많은 앱을 띄웁니다. 예를 들어 Redis를 Lettuce로 쓰는 Spring Boot 앱이 그렇습니다. 요즘 Boot 기본값이라 흔한데, 격리로는 못 띄우고 임시 시험 서버로는 띄웁니다.

## 2. 전문가 해결에게는 재현이 핵심 수단입니다

전문가 해결은 사람이 "컴파일은 되는데 실제로 동작하지 않는다"고 판단했을 때 부릅니다. 이때 증상을 직접 보지 못하면 코드만 보고 추측해야 합니다. 예전에 `sm-troubleshooter-agent`가 실패했던 원인도 모델이 아니라 **관측 수단이 없었던 것**이었습니다(Agent 정의 주석, 실행 f2cf12b3 분석). 예비 수단이 없으면 지원 범위 밖 프로젝트에서 이 Agent는 다시 눈을 가린 채 일하게 됩니다.

## 3. 사람이 있는 경로라서 허용했습니다

같은 일을 하는 `sm-auto-fix-agent`에게는 `startTestServer`를 일부러 주지 않았습니다. 정의 파일에도 "사람 없이 돌기 때문에 원래 DB로 서버를 띄우는 Tool을 주지 않는다"고 적혀 있습니다. 전문가 해결은 사람이 고른 뒤에 돌고, 결과는 다시 개발 승인으로 돌아가서 사람이 확인합니다. 그래서 원래 DB를 쓰는 위험을 여기서만 허용했습니다.

순서로 보면 `startTestServer`가 먼저 있었습니다. 1.4.0(11-22)에서는 이것이 기본이었고, 격리 서버가 생긴 뒤 1.8.0에서 격리가 기본이 되었습니다. 예전 수단을 예비로 남겨 둔 것입니다.

## 약점

원래 DB를 보호하는 장치가 **프롬프트 한 줄**뿐입니다. "기존 데이터를 고치거나 지우지 않고 네가 만든 시험 데이터로만 확인한다"는 문장입니다. 엔진이 막아 주지는 않습니다. 실제로 11-22 측정에서 이 Agent는 공유 `testApp`의 행 `게시물1-1`을 정말로 지웠습니다(`ai-engine-tuning.md` 67번 줄). 그때는 이 규칙이 생기기 전이었지만, 프롬프트만으로 막는다는 점은 지금도 같습니다.

선택지는 셋입니다.
1. **지금처럼 둔다.** 재현 범위가 넓지만 원래 DB에 쓸 위험이 남습니다.
2. **프로젝트별로 허용한다.** 프로젝트 관리에 "격리 실패 시 임시 시험 서버 허용" 같은 값을 두고, 켠 프로젝트에서만 엔진이 `startTestServer`를 실행합니다. 개발 DB라고 사람이 확인한 프로젝트만 원래 DB를 쓰게 됩니다. CLAUDE.md의 "예외 설정은 프로젝트별로만 둔다"와도 맞습니다.
3. **없앤다.** 격리가 안 되면 화면은 재현하지 않습니다(`reproduced: false`). 대신 DB 조회, 서버 로그, 코드 비교로만 진단합니다.

저는 2번을 권합니다. 원하시면 설계안을 정리해 드리겠습니다.

---

## 질문

그냥 지금처럼 둬.

---

## 답변

알겠습니다. 지금 구성 그대로 두겠습니다.

다만 앞에서 말한 문서 하나는 실제 동작과 다릅니다. `docs/software/16.playwright-mcp.md` 59번 줄과 67번 줄에는 아직 `sm-troubleshooter-agent`가 기본으로 `startTestServer`를 쓴다고 적혀 있습니다. 실제로는 1.8.0부터 `startSandboxServer`를 먼저 부르고, 격리에 실패했을 때만 `startTestServer`로 넘어갑니다. 이 두 줄만 고칠지 알려 주세요.

---

## 질문

응 고쳐줘.

---

## 답변

`docs/software/16.playwright-mcp.md`를 1.8.0 이후 동작에 맞게 고쳤습니다.

- **59번 줄 (`sm-troubleshooter-agent` 설명):** 기본은 격리 시험 서버(`startSandboxServer`)이고, 격리를 지원하지 않아 `cause: environment`로 실패할 때만 임시 시험 서버(`startTestServer`)를 쓴다고 바꿨습니다.
- **시험 서버 구성 표:** 원래 "임시 시험 서버(기본)" 한 줄이었는데 두 줄로 나눴습니다.
  - "격리 시험 서버(기본)": 일회용 DB를 복제해서 띄운다는 설명을 붙이고, 지원 범위는 `docs/09` 11.2절을 보라고 적었습니다.
  - "임시 시험 서버(격리 실패 시)": 기존 설명에 사본이 원래 DB에 붙는다는 점을 덧붙였습니다.

바꾼 것은 이 파일 하나이고, 커밋은 아직 하지 않았습니다.

---

## 질문

그런데 이미지게시판 하나 작성하는데 이렇게나 오래걸리고 결함이 많이 걸리는 이유가 뭐라고 생각해? 이 병목점을 뚫어줄 방법이 없나?

---

## 답변

지난 실행 기록(`daa7c276`, `ff9ec3c7`)의 분석 자료를 모으고 있습니다. 그 뒤에 무엇이 반영됐는지 확인하고 정리하겠습니다.

---

## 답변

이미지 게시판은 사람에게는 쉬운 기능이지만, 모델에게는 **그 프로젝트 고유의 관례를 가장 많이 알아야 하는 기능**입니다. 그 관례를 모델이 처음부터 모르고, 틀린 것을 뒤늦게 사람이 찾아낸다는 점이 병목입니다. 아래는 지난 실행 분석(`daa7c276`, `ff9ec3c7`, `0bc967d2`, `9dc51bb8`)과 `ai-engine-tuning.md`의 측정값을 근거로 정리했습니다.

## 1. 시간은 대부분 사람 대기입니다

| 실행 | 전체 | 사람 승인 대기 | 기계 시간 |
|---|---|---|---|
| `daa7c276` | 118분 | **89.7분 (76%)** | 28.6분 |
| `ff9ec3c7` 설계 구간 | 49분 | **약 32분** | 약 12분 (기계는 Agent만 집계) |

사람이 결함을 하나 찾아낼 때마다 사람 왕복이 한 번 더 생기고, 한 번에 20~40분이 걸립니다. `daa7c276`에서는 검증이 고장 난 팝업을 PASS로 판정한 것 하나 때문에 기계 시간 17분과 사람 대기 43분이 더 들었습니다. 그래서 속도를 높이는 방법은 엔진을 빠르게 하는 것보다 **사람에게 가기 전에 결함을 잡는 것**입니다.

## 2. 결함이 많은 이유: 이미지 게시판은 관례 덩어리입니다

같은 엔진으로 만든 범용 CRUD는 성적이 괜찮았습니다. 합성 앱에서 메모, 검색, 공지를 만든 15회 중 10회가 끝까지 통과했고, 중앙값은 약 7분이었습니다. 이미지 게시판에서 나온 결함은 하나도 빠짐없이 **프로젝트의 사내 관례**에서 나왔습니다.

| 실행 | 결함 | 원인 |
|---|---|---|
| `9dc51bb8` | 업로드 파일이 `[object Object]`로 전송됨 | 오래된 Kendo의 select 이벤트 모양을 모름. 업로드에 실제로 쓰는 관례(`async.saveUrl`) 대신 FormData를 새로 만들어 씀 |
| `0bc967d2` | 그리드 핸들러의 `this`가 엉뚱한 객체를 가리킴 | 참고 코드를 가져올 때 그 코드를 누가 어떻게 부르는지까지 옮기지 않음 |
| `0bc967d2` | 팝업에서 `json_string`을 해석하지 않음 | 보내는 쪽은 회원 기능에서, 받는 쪽은 샘플 게시판에서 가져와 둘이 짝이 맞지 않음 |
| `f2cf12b3` | `FileService.imageSave` 호출 전에 `setUserSession`이 빠짐 | 사내 공통 서비스의 사전 조건을 모름 |
| `daa7c276` | 썸네일 404 | 웹앱 밖 업로드 폴더를 여는 정적 경로가 시험 서버에 없음 (환경 차이) |

여기에 구조적인 요인이 겹칩니다.
- **설계에서 결함이 들어갑니다.** 아키텍트 `sm-architect-agent`가 설계서에 코드를 모두 쓰고, 개발 Agent `sm-developer-agent`는 그것을 옮겨 적습니다. 그래서 결함은 대부분 설계에서 생깁니다.
- **실행 전 검사가 Java 컴파일뿐입니다.** JSP, JS, Mapper XML, 설계 문서 형식은 서버를 띄우거나 사람이 봐야 드러납니다. `ff9ec3c7`에서는 DDL 문서의 펜스 하나 때문에 기동이 두 번 실패했습니다.
- **검증 한 번에 결함이 하나씩만 나옵니다.** `0bc967d2`에서는 검증을 세 번 돌리는 데 22분이 걸렸습니다.
- **모델이 작습니다.** qwen3.8-27b는 같은 생각을 되풀이하다 출력 한도에 걸리거나, 일을 일찍 멈춥니다(`ff9ec3c7`에서 4건 보류).

## 3. 병목을 뚫는 방법 (효과 순)

**A. 아키텍트가 쓰기 전에 참고 기능을 기계적으로 모아 줍니다 (범용 Workflow·Tool)**
- 지금은 아키텍트가 참고할 코드를 스스로 찾습니다. 그 과정에서 짝이 안 맞는 코드를 고르거나, 관례를 모른 채 새로 만들어 씁니다.
- 설계 앞에 TOOL step을 하나 둡니다. 요청에 필요한 능력(파일 업로드, 팝업, 그리드 등)별로 dstone-knowledge에서 **그 능력을 이미 쓰는 동작하는 기능**을 찾습니다. 그리고 화면 → Controller → Service → SQL 사슬을 통째로 묶어 아키텍트에게 넘깁니다.
- 원칙은 "새로 짓지 말고, 동작하는 기능 하나를 복제해서 바꾼다"입니다. 앱을 모르는 범용 장치이고, `source-investigation` §4(보내는 쪽과 받는 쪽을 같은 참고 기능에서 가져온다)를 프롬프트가 아니라 입력으로 강제하는 것입니다.
- 위 표의 결함 다섯 중 앞의 넷이 이것으로 막히는 종류입니다.

**B. 사람에게 보내기 전에 정적 검사를 합니다 (범용 Tool)**
- 설계 승인 전에는 Markdown 펜스와 표 형식을 확인하고, DDL은 `SandboxDdl.parse`를 DB 없이 돌려 봅니다.
- 개발 뒤에는 JSP·JS 구문, Mapper XML의 짝과 SQL 파싱을 확인합니다. 개발 결과에 보류(`pending`)가 있으면 개발 Agent를 한 번 더 부릅니다.
- 몇 초면 되는 검사로 사람 왕복 한 번(20분 이상)을 막습니다. 11-24에 제안했는데 아직 반영되지 않았습니다.

**C. 현장 관례는 프로젝트 Skill로 한 번만 만듭니다 (프로젝트 Skill)**
- 업로드 컴포넌트를 쓰는 법, 팝업 주고받기, 공통 파일 서비스의 사전 조건처럼 관례가 무거운 영역은 현장마다 한 번 적어 두면 이후 모든 요청에서 재사용됩니다.
- `0bc967d2`에서 측정해 보니 통과가 1/3에서 3/3이 됐습니다.
- 저장소에는 틀만 두고, 내용은 현장 것입니다. A가 자동으로 찾지 못하는 부분을 메우는 역할입니다.

**D. 사람 대기 자체를 줄입니다 (엔진)**
- 지금 엔진에는 승인 대기를 사람에게 알리는 장치가 없습니다. 승인 대기에 들어갈 때 웹훅이나 메일로 알리면, 사람이 화면을 다시 볼 때까지 놀던 시간이 줄어듭니다.
- 쉬운 요청은 2.7.0의 검증 수준 선택처럼 승인 단계를 줄이는 쪽으로 넓힐 수 있습니다.

**E. 기계 시간을 다듬습니다 (나중에)**
- 같은 step에서 서버를 다시 띄울 때 DB 재복제와 `clean`을 생략하면 한 번에 40~60초 정도 줄 것으로 봅니다(추정).
- 모델 교체는 폐쇄망 POC라는 조건 때문에 뒤로 미룹니다. 굳이 한다면 한 번 쓰고 영향이 가장 큰 아키텍트에만 적용하는 것이 효율적입니다.

## 권장

**A와 B를 먼저** 하기를 권합니다. 결함이 들어가는 곳(설계 입력)과 결함이 사람에게 넘어가는 곳(승인 전)을 막는 것이라, 시간과 결함 수가 함께 줄어듭니다. 효과는 기존 평가 사례 `design-imageboard`로 기준선과 비교하고, 앱이 다른 사례도 하나 더 만들어 testApp에만 맞춘 개선이 아닌지 확인하겠습니다.

이번에는 분석만 했고 파일은 고치지 않았습니다. 어느 것부터 진행할지 알려 주세요.

---

## 질문

A, B 진행해줘.

---

## 답변

A와 B를 진행합니다. 먼저 sm-workflow 구조와 기존 Tool을 확인하겠습니다.

---

## 답변

A 설계에 쓸 knowledge Tool과 API를 확인하고 있습니다.

---

## 답변

현재 4081 포트의 knowledge 인스턴스에 분석된 프로젝트를 보면서, 참고 기능의 코드 사슬을 어떤 API 조합으로 뽑을 수 있는지 확인하고 있습니다.

---

## 답변

B를 어디에 끼울지 정하려고 개발 Agent와 자동 수정 Agent 정의를 확인하고 있습니다.

---

## 답변

계획을 정했습니다. 구현 전에 공용 Tool(`containsValue`·`countUp`)과 DDL 파서의 모양을 확인하겠습니다.

---

## 답변

구현을 시작합니다. 먼저 dstone-knowledge에 "기능 한 묶음"을 돌려주는 API를 만들겠습니다. 기존 CallGraph 코드의 형식을 따르겠습니다.

---

## 답변

knowledge 쪽 SQL과 서비스를 작성합니다.

---

## 답변

Now the Dao and service.

---

## 답변

knowledge 컴파일이 통과했습니다. 4081 인스턴스는 건드리지 않고, 다른 포트로 시험 인스턴스를 띄워 실제 데이터로 확인하겠습니다.

---

## 답변

시험 인스턴스(4181)가 뜨기를 기다리고 있습니다. 뜨면 실제 testApp 데이터로 새 API를 확인하겠습니다.

---

## 답변

주소로 시작해도 같은 기능 묶음이 나옵니다. 다른 구조의 앱(H-Energy, Vue SPA)에서도 확인하겠습니다.

---

## 답변

ai-engine 쪽 Tool을 작성하고 있습니다. 기능 묶음 API를 부르고, 원본 소스에서 해당 줄을 읽어 `02-1.reference-code.md` 같은 문서로 남기는 Tool입니다.

---

## 답변

ai-engine 컴파일이 통과했습니다. 두 Tool의 단위 테스트를 작성합니다.

---

## 답변

테스트에 람다를 썼는데, 저장소 규칙에 맞게 람다 없이 고치겠습니다.

---

## 답변

`StaticCheckTool` 테스트 6건이 모두 통과했습니다(node로 하는 JS 검사 포함). 이어서 `KnowledgeFeatureTool` 테스트를 작성합니다.

---

## 답변

엔진은 성공한 step의 `saveError` 값을 null로 비웁니다. 묵은 값이 남지 않게 다시 시도하는 경로는 별도 Agent step으로 두겠습니다. sm-workflow를 고칩니다.

---

## 답변

정의 파일을 모두 고쳤습니다. 전체 테스트를 돌려 기존 Workflow 테스트(`SmDecisionFlowTest` 등)가 새 step과 맞는지 확인하겠습니다.

---

## 답변

테스트가 도는 동안 문서를 갱신합니다. 먼저 고칠 위치를 찾겠습니다.

---

## 답변

ai-engine 전체 테스트가 끝났습니다. 결과를 확인하겠습니다.

---

## 답변

세 건 실패했습니다. 두 건은 새 step 때문에 생긴 것으로 예상한 결과입니다. 나머지 한 건(`다시 리뷰`)은 이번 변경과 관련이 있는지 확인하겠습니다.

---

## 답변

한도 계산이 너무 보수적입니다(40,000자에서 멈춤). 넣은 조각의 위치 줄 몫은 돌려주도록 고치겠습니다.

---

## 답변

Tool 쪽 마무리가 끝났습니다. 이제 효과를 재려고 기존 평가 사례 `design-imageboard`의 내용을 확인하겠습니다.

---

## 답변

한도의 상당 부분을 "위치만 적은" 조각의 제목 줄이 차지하고 있습니다. 못 넣은 조각은 각 절 끝에 한 줄짜리 목록으로 줄이겠습니다.

---

## 답변

평가가 도는 동안 knowledge 시험 인스턴스(4181)를 내리고 변경 이력 문서를 정리하겠습니다.

---

## 답변

평가 6회가 끝나기를 기다리고 있습니다. 끝나면 알림이 옵니다.

---

## 답변

평가 결과를 기다리는 중입니다.

---

## 답변

The first baseline run is in progress. I'll get a notification when all six finish.

---

## 답변

Results will come in as the remaining runs finish. I'll wait for the notification.

---

## 질문

<task-notification>
<task-id>bindk9j2l</task-id>
<tool-use-id>toolu_01Ksgio4hdt2f83iDQrVzbCE</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/f5ddb5c7-c4d1-4797-bc32-abd38ebb3764/tasks/bindk9j2l.output</output-file>
<status>completed</status>
<summary>Background command "until [ "$(curl -s 'localhost:8181/api/ai/eval/runs?limit=6' | python3 -c "import sys,json;print(sum(1 for r in json.load(sys.stdin) if r['status'] not in ('QUEUED','RUNNING')))")" -ge 3 ]; do sleep 20; done; curl -s 'localhost:8181/api/ai/eval/runs?limit=6' | python3 -c "import sys,json;[print(r['caseId'][-4:],r['status'],r['passed'],r['total'],r['durationMs']) for r in json.load(sys.stdin)]"" completed (exit code 0)</summary>
</task-notification>

---

## 답변

기준선 세 번이 끝났습니다(7/7, 6/7, 7/7). 본뜰 코드를 넣은 세 번이 돌고 있습니다.

---

## 답변

이 설계는 `imageSave` 대신 같은 `FileService`의 `fileSave(MultipartFile, UPLOADS, FileVO)`를 썼습니다. 공통 서비스를 쓴다는 점은 같아서 결함이라기보다 채점이 너무 좁았던 것입니다. 정리하면, 이 사례는 영향도분석서가 이미 사람 손으로 정확하게 다듬어져 있어 기준선도 거의 다 맞힙니다. 본뜰 코드가 효과를 내는 상황(영향도가 거칠 때)을 가려내지는 못합니다. 남은 3회에서는 회귀가 없는지, 시간과 토큰이 어떻게 바뀌는지를 보겠습니다.

---

## 질문

<task-notification>
<task-id>bi615811w</task-id>
<tool-use-id>toolu_01DjbuCjRXJBhwZnbG9u4wJ5</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/f5ddb5c7-c4d1-4797-bc32-abd38ebb3764/tasks/bi615811w.output</output-file>
<status>completed</status>
<summary>Background command "until [ "$(curl -s 'localhost:8181/api/ai/eval/runs?limit=20' | python3 -c "
import sys,json
ids=set('b50f42c6-250c-4de3-a717-eb0596a0ca7a d03023f8-529f-4b14-8b5d-a83a225aad01 d4650ee6-39c4-44f0-8926-34e8f7eb1b04 506d5689-9854-4b80-9002-a70ee735ffa4 cca1395f-6a58-4e57-857b-035de89a02aa 441f0b57-299f-4e99-8afc-f6a0b2698b06'.split())
rs=[r for r in json.load(sys.stdin) if r.get('runId') in ids]
print(sum(1 for r in rs if r.get('status') not in ('RUNNING','QUEUED','PENDING')))
" 2&gt;/dev/null)" = "6" ]; do sleep 30; done; echo DONE" completed (exit code 0)</summary>
</task-notification>

---

## 답변

본뜰 코드를 넣은 세 번도 끝났습니다(7/7, 7/7, 6/7). 3회차가 놓친 "클릭 함수의 호출 방식" 항목이 진짜 결함인지 확인하겠습니다.

---

## 답변

A와 B를 모두 구현했습니다. 실제 모델로 평가해 보니 정확도가 좋아졌는지는 이번 평가 사례로는 가릴 수 없었습니다. 시간과 토큰은 줄어든 쪽으로 나왔지만 3회씩이라 확정할 수는 없습니다. 커밋은 하지 않았습니다.

## A. 본뜰 기능의 코드를 모아 설계에 넘김

- **dstone-knowledge**: 새 API `GET /api/revisions/{id}/feature?entry=`를 만들었습니다. 화면 경로나 주소 하나를 주면, 저장된 관계를 따라 그 기능의 화면 → 주소 → Controller → Service → SQL과 팝업 화면을 한 묶음으로 돌려줍니다.
  - 확인: JSP 앱(testApp, anybiz_prd), Vue 앱, Nexacro 앱에서 모두 동작했습니다.
  - testApp 회원 화면 하나로 시작하면 팝업 `memberRegi.jsp`, 업로드 `infoUrl` → `FileService.imageSave`, SQL까지 한 기능의 것만 짝으로 나옵니다.
- **ai-engine**: 새 Tool `knowledgeFeatureCode`가 이 묶음의 원본 코드를 `02-1.reference-code.md`에 모읍니다(기본 56,000자 한도).
- **흐름**: 영향도 Agent(`sm-analyze-impact-agent`)가 `## 참고 기능` 절에 본뜰 기능을 적고, 새 step46~49가 위 문서를 만들어 아키텍트(`sm-architect-agent`)에 넘깁니다. 프로젝트ID나 그 절이 없으면 예전처럼 설계합니다.

## B. 사람에게 가기 전 형식 검사

- **설계 뒤**: 새 Tool `checkDocuments`가 닫히지 않은 코드 블록, 칸이 어긋난 표, DDL을 격리 서버와 같은 규칙으로 읽어 봅니다. 걸리면 문서 수정 Agent가 형식만 한 번 고칩니다. `ff9ec3c7`에서 기동을 두 번 막은 DDL 문서 끝의 ``` 같은 것을 여기서 잡습니다.
- **개발 뒤**: 보류가 남으면 개발 Agent(`sm-developer-agent`)를 한 번 더 부릅니다.
- **컴파일 뒤**: 새 Tool `checkSources`가 XML, JSP 태그 짝, JavaScript 구문(`node --check`)을 보고, 걸리면 개발 Agent가 한 번 고칩니다. 개발, 자동 수정, 전문가 해결 Agent도 이 Tool로 스스로 확인합니다.
- 틀렸다고 확신할 수 있는 것만 문제로 냅니다. JSP 코드가 섞인 `<script>`나 화면 템플릿은 건너뜁니다.

## 테스트

- 새로 만든 테스트 14건(`StaticCheckToolTest`, `KnowledgeFeatureToolTest`, `SmStaticCheckTest`)과 기존 `SmCompileCheckTest`는 통과합니다.
- `SmDecisionFlowTest`·`SmKeepFixesTest`에서 6건이 실패하는데, 이번 변경 전부터 깨져 있던 것입니다.
  - 원인: 오늘 커밋 `fcc36ffd`에서 step 이름 앞에 순번이 붙고 step06 선택지가 바뀌었습니다(`다시 리뷰` 삭제 등).
  - 지금까지 드러나지 않은 이유: `conf/application.yml`의 `dstone.ai.test.llmcall: true` 때문에 이 테스트들이 평소에는 건너뛰어집니다.

## 실제 모델 평가 (qwen3.8-27b, 각 3회)

| | 통과 (7개 항목) | 평균 시간 | Tool 호출 | 입력 토큰 |
|---|---|---|---|---|
| 기준선 | 7, 6, 7 | 261초 | 33 | 99.5만 |
| 본뜰 코드 있음 | 7, 7, 6 | 90초 | 22 | 63.9만 |

- **정확도는 비교가 안 됩니다.** 이 사례는 영향도분석서에 회원 업로드·팝업의 줄 번호가 이미 사람 손으로 적혀 있어서, 기준선도 거의 다 맞힙니다.
- 기준선의 6점은 결함이 아니라 채점 항목이 너무 좁아서 나왔습니다(`imageSave` 대신 같은 서비스의 `fileSave`를 씀).
- 본뜰 코드 쪽의 6점은 진짜 결함입니다. `0bc967d2`와 같은 그리드 핸들러의 `this` 문제가 다시 나왔으니, 본뜰 코드만으로는 막지 못한다는 뜻입니다.
- 시간과 토큰이 줄어든 것은 기준선 1회가 631초로 유난히 길었던 영향도 있어 확정하지 않습니다.
- 진짜 효과는 영향도 Agent가 `## 참고 기능`을 직접 쓰는 전체 실행에서 확인해야 하는데, 아직 돌리지 않았습니다.

문서는 `docs/09`(Tool 표, 11.1절, 설정 키), `docs/11`(새 API), `.claude/docs/ai-engine-tuning.md`(측정 기록)에 반영했습니다. 평가 사례 두 개는 `conf/eval/testApp/`에 있습니다(git 관리 밖).

다음 할 일로 세 가지가 남아 있습니다. 진행할 것을 알려 주세요.
1. 이번 변경을 커밋합니다.
2. 깨져 있던 sm 테스트 6건을 지금 정의에 맞춥니다.
3. 이미지 게시판 요청으로 전체 실행을 한 번 돌려 A와 B의 실제 효과를 잽니다.

---

## 질문

모두 진행해줘.

---

## 답변

세 가지를 순서대로 진행합니다. 먼저 깨져 있던 sm 테스트 6건을 지금 정의에 맞추고, 커밋한 뒤, 전체 실행을 돌리겠습니다.

---

## 답변

ai-engine 전체 테스트 292건이 모두 통과했습니다(미리 넣은 답 방식, 건너뜀 0). knowledge 모듈 테스트도 돌리겠습니다.

---

## 답변

knowledge 테스트도 259건 모두 통과했습니다. 커밋하겠습니다.

---

## 답변

testApp 프로젝트가 등록되어 있습니다(DB 1개, Java 8). 요청서 폴더를 만들고 sm-workflow 실행을 시작하겠습니다. 실행 API를 확인합니다.

---

## 답변

요구사항 리뷰(step05)까지 진행됐습니다. 승인 대기가 오면 이어서 처리하겠습니다.

---

## 질문

<task-notification>
<task-id>b0ffadcod</task-id>
<tool-use-id>toolu_01MVrY6sjjLFA4EkLN9QgLpT</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/f5ddb5c7-c4d1-4797-bc32-abd38ebb3764/tasks/b0ffadcod.output</output-file>
<status>completed</status>
<summary>Background command "/tmp/claude-1000/-app-dstone/3cb6934e-3a27-4e25-a106-52909a8aeb67/scratchpad/wait.sh 55b0be49-35b1-4de3-8302-f3721a632769" completed (exit code 0)</summary>
</task-notification>

---

## 답변

Review passed and the impact analysis is done. Let me check whether the impact agent wrote the `## 참고 기능` section.

---

## 답변

While development and verification run, I'll fix the starting-point parsing bug I just found (it read the parenthetical note as an entry).

---

## 답변

The fix and its test pass. I'm waiting for the run to finish development and verification.

---

## 질문

아직 진행중인거지?

---

## 답변

The routing was correct: the developer left 1 pending item (the DDL for a column wasn't in `03-3`), so step57 ran. Let me see why step57 failed.

---

## 답변

Yes, it's still running. When the run reaches the development approval (step19) or ends, I'll report the overall results.

---

## 질문

영어말고 한국어로 진행상황이야기해.

---

## 답변

죄송합니다. 앞으로는 한국어로만 쓰겠습니다. 현재 상태를 확인하겠습니다.

---

## 답변

아직 실행 중이고, 자동 수정(step34, `sm-auto-fix-agent`) 단계입니다.

**지금까지 진행 상황 (실행 `55b0be49`)**
- **요구사항 → 리뷰 → 영향도**: 리뷰는 한 번에 통과했습니다. 영향도 Agent가 새로 넣은 `## 참고 기능` 절에 회원 관리 화면 두 개를 적었습니다.
- **본뜰 기능 코드 모으기(step46~49)**: 정상 동작했습니다. 53,393자 문서가 아키텍트에게 넘어갔습니다.
- **설계**: 지난 실행들에서 결함이 났던 관례를 모두 맞게 따랐습니다.
  - 업로드: `kendoUpload` → `setUserSession` → `imageSave`
  - 팝업 Controller가 `json_string`을 풂
  - `.call(this)`와 `this.dataItem` 섞임 없음
- **설계 문서 형식 검사(step50)**: 문제가 없어 바로 설계 승인으로 갔습니다.
- **개발**: 47초에 끝났고, 보류 1건(DB 컬럼 추가 DDL)이 남았습니다.
- **보류 이어서 개발(step57)**: 실패했습니다. 모델이 같은 Skill을 5번 연달아 불러 반복 방지 장치에 걸렸습니다. 다음 단계로는 정상적으로 넘어갔습니다. 다만 남은 보류는 사람이 DB에 반영할 일이었으니, 이 재시도는 애초에 필요 없었습니다.
- **컴파일과 형식 검사(step53)**: 통과했습니다.
- **격리 서버 기동**: 45초에 성공했습니다.
- **동작 검증**: FAIL 1건, BLOCKED 1건이 나왔습니다.
  - FAIL: 썸네일을 눌러도 이미지 팝업이 안 뜹니다. 이 기능은 본뜰 기능이 없어 영향도에서 "확인 필요"로 남긴 부분입니다. 팝업 div를 부모 프레임에 만들고, 초기화는 iframe 쪽 `$`로 해서 생긴 문제입니다.
  - BLOCKED: 데이터가 3건뿐이라 페이징을 확인할 수 없었습니다.

자동 수정이 끝나면 다시 검증이 돌고, 개발 승인(step19)에 도착하면 전체 결과를 정리해 알려 드리겠습니다.

---

## 질문

<task-notification>
<task-id>bpzh8310d</task-id>
<tool-use-id>toolu_01DTdrymAvUAiYrehfd1phJf</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/f5ddb5c7-c4d1-4797-bc32-abd38ebb3764/tasks/bpzh8310d.output</output-file>
<status>completed</status>
<summary>Background command "/tmp/claude-1000/-app-dstone/3cb6934e-3a27-4e25-a106-52909a8aeb67/scratchpad/wait.sh 55b0be49-35b1-4de3-8302-f3721a632769" completed (exit code 0)</summary>
</task-notification>

---

## 답변

실행이 개발 승인(step19)에 도착했습니다. 결과를 기록하고, 고친 부분을 커밋한 뒤 시험 인스턴스를 내리겠습니다.

---

## 답변

세 가지 모두 끝냈습니다. 이미지 게시판 요청이 개발 승인까지 사람 개입 없이 16분 만에 왔고, 지난 실행들에서 반복되던 결함은 하나도 나오지 않았습니다. 다만 한 번 돌린 결과라 확정하기는 이릅니다.

## 1. 깨져 있던 sm 테스트 정리
- 디자이너가 바꾼 step 이름(`NN-` 순번)과 step06 선택지 이름에 맞춰 테스트 5건을 고쳤습니다.
- 지워진 선택지 `다시 리뷰`를 보던 테스트 1건은 삭제했습니다. `docs/09`의 흐름 그림과 설명도 바뀐 선택지에 맞췄습니다.
- 미리 넣은 답 방식(`-Ddstone.ai.test.llmcall=false`)으로 ai-engine 292건, knowledge 259건이 모두 통과합니다.

## 2. 커밋
- `1875279a`: A·B 구현, 테스트·문서 정리
- `da6fdcfb`: 전체 실행에서 찾은 작은 버그 수정과 측정 기록

푸시는 하지 않았습니다.

## 3. 이미지 게시판 전체 실행 (실행 `55b0be49`, qwen3.8-27b)
승인은 모두 앞으로 가는 선택지로 제가 내렸고, 한 번에 25초 안쪽이었습니다.

| 단계 | 결과 |
|---|---|
| 요구사항 → 리뷰 → 영향도 | 리뷰 한 번에 통과. 영향도 Agent가 `## 참고 기능`에 회원 관리 화면 두 개를 적고, 썸네일 이미지 팝업은 "본뜰 기능 없음, 확인 필요"로 표시 |
| 본뜰 코드 모으기 | 53,393자, 40조각을 설계에 전달 |
| 설계 | 지난 실행들의 결함 패턴을 모두 피함: 업로드 관례(`kendoUpload` → `setUserSession` → `imageSave`), 팝업 Controller의 `json_string` 처리, `.call(this)`와 `this.dataItem` 섞임 없음 |
| 형식 검사 | 설계 문서와 개발 결과 모두 통과 |
| 첫 검증 | 15개 기준 중 PASS 13, FAIL 1, BLOCKED 1 |
| 자동 수정 1회 뒤 재검증 | PASS 14, BLOCKED 1(데이터가 3건뿐이라 페이징 확인 불가) |
| 요청 → 개발 승인 | **16분 7초** |

- 비교: 11-24 실행 `daa7c276`은 기계 시간만 28.6분이었고, 고장 난 팝업을 검증이 PASS로 잘못 판정해 사람이 직접 찾아야 했습니다.
- 유일한 FAIL은 본뜰 기능이 없던 썸네일 이미지 팝업에서 나왔습니다. 그대로 본뜰 코드가 있는 부분은 맞게 나오고, 없는 부분에서만 틀린 셈입니다.

## 발견한 문제
- **보류 재시도(step57)가 불필요하게 돌았습니다.** 남은 보류가 "사람이 실제 DB에 반영할 DDL"이었는데도 개발 Agent를 다시 불렀고, 모델이 같은 Skill을 5번 연달아 불러 반복 방지 장치에 걸렸습니다. 흐름은 정상적으로 이어졌지만, 지금 분기는 "못 끝낸 일"과 "사람이 할 일"을 구별하지 못합니다. 개발 Agent가 보류를 두 종류로 나눠 답하게 하는 방법을 생각하고 있습니다.
- **본뜰 코드 Tool이 `## 참고 기능` 절의 설명 문장을 시작점으로 읽었습니다.** 해가 없는 실수였고, 경로나 주소 모양만 받도록 고쳐 커밋했습니다.

## 남은 상태
- 실행 `55b0be49`는 개발 승인 대기로 두었습니다. 시험 인스턴스는 내렸지만 기록은 공유 DB에 남아 있어 실행 관리 화면에서 보입니다. 필요 없으면 중지해 주세요.
- 작업 폴더는 `/app/dstone/temp/sm-run/20261125-1242/`이고, git 관리 밖입니다.

보류 재시도 분기를 고칠지, 푸시할지 알려 주세요.

---

## 질문

보류재시도 분기를 고치고 푸시해줘.

---

## 답변

보류를 두 종류로 나누겠습니다. 개발 Agent가 다시 부르면 이어서 할 수 있는 "못 끝낸 일"의 수(`unfinished`)를 따로 답하게 하고, step54는 그 값만 보게 합니다. 값이 비면 0으로 봐서 재시도하지 않습니다.

---

## 답변

정의와 테스트를 고쳤습니다. sm 테스트를 돌리겠습니다.

---

## 답변

70건 모두 통과했습니다. 문서를 맞추고 커밋한 뒤 푸시하겠습니다.

---

## 답변

보류 재시도 분기를 고치고 GitHub `main`에 푸시했습니다(`8ba1d13d`).

**고친 내용 (sm-workflow 2.8.1, `sm-developer-agent` 2.5.0)**
- 개발 Agent가 답에 `unfinished`를 따로 적습니다. 보류 가운데 손대지 못해서, 다시 부르면 이어서 할 수 있는 것(파일을 아직 만들지 않음 등)의 수입니다.
- step54는 이 값만 봅니다. 사람이 정해야 하는 것, DB에 반영할 것, 선언을 찾지 못한 것, 고쳐 봐도 남은 오류는 세지 않으므로 이런 보류만 남으면 다시 부르지 않습니다.
- 답에 이 값이 없으면 0으로 보고 다시 부르지 않습니다.
- 실행 `55b0be49`의 경우(DDL 반영 보류 하나)라면 이제 재시도 없이 바로 컴파일 확인으로 갑니다.

**확인한 것**
- sm 관련 테스트 70건이 모두 통과했습니다. 새 경우 두 가지(사람이 할 보류만 남은 경우, `unfinished`가 없는 경우)를 테스트에 더했습니다.
- 실제 모델로는 다시 돌리지 않았습니다. 그래서 모델이 `unfinished`를 제대로 나눠 세는지는 아직 확인하지 못했습니다.

**푸시된 커밋**
- `1875279a`: 본뜰 기능 코드와 형식 검사(2.8.0)
- `da6fdcfb`: 참고 기능 절의 설명 문장을 시작점으로 읽던 버그 수정
- `8ba1d13d`: 이번 보류 재시도 수정

Windows 쪽 클론에는 pull하면 반영됩니다. 실행 `55b0be49`는 아직 개발 승인 대기로 남아 있으니, 필요 없으면 실행 관리 화면에서 중지해 주세요.
