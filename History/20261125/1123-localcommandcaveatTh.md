# localcommandcaveatTh

> 생성일시: 2026-11-25 11:23

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
