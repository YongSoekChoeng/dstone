# localcommandcaveatTh

> 생성일시: 2026-11-25 11:16

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
