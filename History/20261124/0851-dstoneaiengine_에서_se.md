# dstoneaiengine_에서_se

> 생성일시: 2026-11-24 08:51

## 질문

dstone-ai-engine 에서 sendboxserver 가 뭔가?

---

## 답변

`startSandboxServer`는 `TestServerTool`에 있는 Tool로, **원래 환경을 건드리지 않는 일회용 "격리 시험 서버"를 띄웁니다.** 코드는 `dstone-ai-engine/src/main/java/net/dstone/ai/tools/build/TestServerTool.java:212`, 설명 문서는 `docs/09.dstone-ai-engine.md` 11.2절에 있습니다.

## 무엇을 하나

원본 소스에 개발 결과(`dev-resources`)를 얹은 사본을 만들어 빈 포트에 서버로 띄웁니다. Agent는 이 서버에서 고친 내용이 실제로 동작하는지 확인합니다. 앱 사본 폴더는 프로젝트마다 하나를 다시 씁니다.

비슷한 Tool로 `startTestServer`도 있습니다. 차이는 DB입니다.

| | `startTestServer` | `startSandboxServer` |
|---|---|---|
| DB | 원래 DB에 그대로 붙음 | 호출할 때마다 **일회용 DB 복제본**을 만들어 붙음 |
| DDL(`03-3.ddl`) | 반영 안 함 | 복제본에만 반영 |
| 외부 접속 | 막지 않음 | 원래 DB 주소는 복제본으로 돌리고 나머지는 끊음 |
| 용도 | 개발·시험 DB에서 사람이 지켜볼 때 | 자동 검증·자동 수정 (쓰기·DDL이 생기는 시험) |

CLAUDE.md에도 "자동으로 쓰기·DDL을 하는 시험은 `startSandboxServer`로만 한다. 격리를 보장하지 못하면 원래 DB로 대신하지 말고 실패시킨다"는 규칙이 있습니다.

## 무엇을 어떻게 떼어 놓나

- **DB**: MySQL·PostgreSQL은 별도 DB 서버를 띄워 복제합니다. Oracle은 시험 전용 CDB에 PDB를 만들었다가 지웁니다.
- **네트워크**: 앱 JVM의 새 소켓을 모두 엔진의 `SandboxGuard`로 보냅니다. 원래 DB로 가는 접속은 복제본으로 돌리고, 나머지는 끊습니다.
- **파일**: 사본과 복제 데이터에 들어 있는 원본 폴더 경로를 사본 경로로 바꿉니다.
- **Agent**: 서버를 띄운 step이 끝날 때까지 `dbQuery`는 복제본 별칭(`sandbox-…`)으로만, `browser_navigate`는 시험 서버 주소로만 가게 묶습니다. 서버를 띄우기 전에는 Agent 정의의 `execution.toolPrerequisites`가 DB 조회와 브라우저 조작을 막습니다.

## 서버를 언제 내리나

- 서버를 띄운 step이 끝나도 **다음 step 하나까지만** 남겨 둡니다. 다음 step이 같은 입력으로 다시 부르면 새로 띄우지 않고 그 서버를 그대로 줍니다. 서버를 한 번 띄우는 데 90~107초가 걸려서 이렇게 했습니다.
- 입력이나 파일이 바뀌었으면 기존 서버를 내리고 새로 띄웁니다.
- 실행이 끝나거나 실패하거나, 승인 대기·일시정지에 들어가면 그 실행의 서버를 모두 내립니다.

## 지원 범위

- **DB**: MySQL, PostgreSQL, Oracle
- **빌드·기동**: Maven 단일 모듈의 Spring Boot(`java -jar`), 또는 WAR + 설치된 Tomcat
- **띄우지 않는 경우**: NIO 기반 접속(Lettuce, Kafka, MongoDB 등)이 들어 있는 앱은 격리를 보장할 수 없어서 띄우지 않습니다. 범위 밖이면 원래 DB로 대신 띄우지 않고 `cause: environment`로 실패합니다.

이 장치는 보안 경계가 아니라 실수를 막는 용도입니다. 그래서 개발 장비에서 개발 DB로만 씁니다.

## 어디서 쓰나

- `sm-workflow`의 기동 확인 step (`sm-workflow.yml:428`)
- `sm-verifier-agent`, `sm-auto-fix-agent`, `sm-troubleshooter-agent`: 재현하고, 고친 뒤 다시 확인할 때
- Agent 평가(`EvalRunner`): `http`·`dbQuery`로 채점하는 사례

---

## 질문

언제 testserver 를 쓰고 언제 sandboxserver 를 쓰나?

---

## 답변

**기본은 `startSandboxServer`입니다.** `startTestServer`는 격리를 지원하지 않는 프로젝트에서 사람이 지켜볼 때만 대체 경로로 씁니다.

## 기준은 "원래 DB에 써도 되는가"

| 상황 | 쓰는 것 | 이유 |
|---|---|---|
| 사람 없이 자동으로 도는 검증·수정 | `startSandboxServer`만 | 등록·수정·삭제나 DDL이 원래 DB에 닿으면 안 됨 |
| 사람이 개입하는 전문가 해결 | `startSandboxServer`를 먼저 부름 | 복제본이라 데이터를 마음대로 바꿔도 됨 |
| 위에서 `cause: environment`로 실패함 (격리 미지원) | `startTestServer` | 원래 DB에 붙음. 기존 데이터는 건드리지 않고, Agent가 만든 시험 데이터로만 확인 |

격리를 지원하지 않는 경우는 Gradle·다중 모듈 빌드, SQL Server·MariaDB 같은 DB, NIO 기반 접속(Lettuce, Kafka 등)이 들어 있는 앱입니다.

## Agent별로 받은 Tool

- **`sm-verifier-agent`** (자동 검증): `startSandboxServer`만 받습니다. `toolPrerequisites` 때문에 서버가 뜨기 전에는 DB 조회와 브라우저 조작도 막힙니다.
- **`sm-auto-fix-agent`** (자동 수정): `startSandboxServer`만 받습니다. `startTestServer`, `applyOverlay`, `runShellScript`는 일부러 주지 않았습니다. 사람 없이 원래 DB로 서버를 띄우거나 원본에 반영하지 못하게 하려는 것입니다.
- **`sm-troubleshooter-agent`** (전문가 해결, 사람이 개입): 둘 다 받습니다. 1.8.0부터 `startSandboxServer`가 먼저이고, environment 실패일 때만 `startTestServer`로 넘어갑니다. 이 Agent에는 `toolPrerequisites`를 걸지 않았습니다. 그렇게 막으면 이 대체 경로를 쓸 수 없기 때문입니다.
- **`sm-workflow`의 기동 확인 TOOL step**과 **Agent 평가(`EvalRunner`)**: `startSandboxServer`

정리하면 `startTestServer`는 원래 DB에 붙으므로 **개발·시험 DB에서, 사람이 개입하는 흐름에서만** 씁니다.

두 곳의 설명이 지금 동작과 다릅니다.
- `sm-workflow.yml:33` 주석: 전문가 해결 Agent가 `startTestServer`를 띄운다고 적혀 있습니다.
- `docs/09` 11.1절: 전문가 해결 설명에 같은 내용이 남아 있습니다.

troubleshooter 1.8.0부터는 `startSandboxServer`가 먼저이므로, 원하시면 두 곳을 맞춰 고치겠습니다.

---

## 질문

응 수정해줘.

---

## 답변

두 Tool이 같은 `start()`를 거쳐 시험계정 줄을 돌려준다는 것까지 확인했습니다. 이제 세 곳을 고칩니다.

---

## 답변

세 곳을 고쳤습니다. 주석과 문서만 바꿨고 동작 코드는 건드리지 않았습니다.

**1. `dstone-ai-engine/conf/definitions/workflows/sm/sm-workflow.yml:33`** (전문가 해결 설명 주석)
- 고치기 전: "임시 시험 서버(startTestServer)를 띄워 재현·확인"
- 고친 뒤: "격리 시험 서버(startSandboxServer)를 띄워 재현·확인. 격리를 지원하지 않는 프로젝트에서만 임시 시험 서버(startTestServer)를 씀"

**2. `docs/09.dstone-ai-engine.md` 11.1절의 전문가 해결 항목**
- 화면 재현은 `startSandboxServer`가 먼저라고 고쳤습니다.
- `cause: environment`로 실패할 때만 `startTestServer`로 넘어가고, 이때는 기존 데이터를 고치지 않고 직접 만든 시험 데이터로만 확인한다고 덧붙였습니다.

**3. 같은 문서의 시험 서버 설명 문단**
- "기본은 임시 시험 서버"라는 문장과 바로 뒤의 "전문가 해결은 startSandboxServer를 먼저"라는 문장이 서로 맞지 않았습니다. 그래서 순서를 다시 썼습니다.
  - 기본은 `startSandboxServer`입니다.
  - 자동 검증·자동 수정 Agent는 `startSandboxServer`만 씁니다.
  - 전문가 해결은 격리를 지원하지 않을 때만 `startTestServer`를 씁니다.
- 시험계정은 원래 `startTestServer` 결과로만 받는다고 적혀 있었습니다. 실제로는 두 Tool이 같은 `start()`를 거쳐 시험계정 줄을 돌려주므로 `startSandboxServer`도 함께 적었습니다.

`.claude/docs/ai-engine-features.md`에도 `startTestServer`가 나오지만 날짜가 붙은 변경 기록이라 그대로 두었습니다. 커밋은 하지 않았습니다.
