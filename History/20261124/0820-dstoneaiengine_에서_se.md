# dstoneaiengine_에서_se

> 생성일시: 2026-11-24 08:20

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
