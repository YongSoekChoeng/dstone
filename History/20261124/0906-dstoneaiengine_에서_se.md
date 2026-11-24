# dstoneaiengine_에서_se

> 생성일시: 2026-11-24 09:06

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

---

## 질문

<pasted_content id="251c">
Workflow 디자이너 에서 아래의 작업을 진행해줘.

1. [자동 배치]기능에 대해서 아래와 같이 수정.
    - 서로 유관한 노드가 가까이 배치되도록 수정.
2. [FAIL로 가는 실패 화살표 숨기기] 옆에 [화살표 재배치]기능 추가.
    - 현재 노드들은 그대로 놔두고 화살표만 재배치.
</pasted_content id="251c">

---

## 답변

먼저 Workflow 디자이너 화면의 자동 배치와 화살표 그리는 코드를 찾아보겠습니다.

---

## 답변

먼저 실제 Workflow가 어떤 모양인지 보겠습니다. 배치 알고리즘을 그 모양에 맞춰 정하려는 것입니다.

---

## 답변

Node가 있으니 배치 코드를 떼어 내 실제 Workflow로 돌려 보며 비교하겠습니다. 그림으로 확인할 도구가 있는지도 봅니다.

---

## 답변

지금 자동 배치 결과를 그림으로 뽑아 문제를 확인하고 있습니다.

---

## 질문

[Image: original 2566x782, displayed at 2000x610. Multiply coordinates by 1.28 to map to original image.]

---

## 질문

[Image: original 2554x806, displayed at 2000x631. Multiply coordinates by 1.28 to map to original image.]

---

## 질문

[Image: original 2018x788, displayed at 2000x781. Multiply coordinates by 1.01 to map to original image.]

---

## 질문

[Image: original 2046x778, displayed at 2000x761. Multiply coordinates by 1.02 to map to original image.]

---

## 질문

[Image: original 2246x776, displayed at 2000x691. Multiply coordinates by 1.12 to map to original image.]

---

## 답변

겹침 벌점이 너무 커서 step25가 step13 곁을 떠났습니다. 벌점을 "한 줄 어긋나게 두는 쪽"보다 조금만 비싸게 낮추겠습니다.

---

## 답변

결과가 그대로라 step25의 자리별 비용을 직접 찍어 보겠습니다.

---

## 질문

[Image: original 2276x778, displayed at 2000x684. Multiply coordinates by 1.14 to map to original image.]

---

## 답변

이제 화면에 버튼을 붙입니다. 먼저 체크박스 주변 HTML을 봅니다.

---

## 답변

버튼과 스타일을 추가하고 있습니다. `main.css`는 두 앱에 복사본이 있어서(이미 조금 어긋나 있음) 양쪽에 같은 규칙을 넣겠습니다.

---

## 답변

문법 검사는 통과했습니다. 이제 화살표 재배치를 시험합니다. 저장된 `sm-workflow.layout.json`의 노드 위치를 그대로 두고 화살표만 다시 놓아 봅니다.

---

## 답변

실패 화살표가 맨 아래 한 줄로 모였습니다. 이번에는 사람이 노드를 조금씩 옮긴 경우와 노드가 겹친 경우(길 찾기로 넘어가야 함)를 시험합니다.

---

## 질문

[Image: original 2276x778, displayed at 2000x684. Multiply coordinates by 1.14 to map to original image.]

---

## 답변

Spurious: in fullscreen the proxy button should be used. I'll use the proxy button.

---

## 답변

문서 수정까지 마쳤습니다. 마지막으로 `autoLayout` 머리 주석의 "단 나누기" 설명을 새 동작에 맞게 고칩니다.

---

## 답변

Workflow 디자이너에 두 기능을 넣었습니다. 시험용 가짜 서버로 실제 브라우저에서 띄워 보니 둘 다 동작했고 화면 오류도 없었습니다. 실제 엔진에서는 아직 확인하지 않았습니다.

## 1. [자동 배치]: 이어진 노드를 가까이

그동안은 노드를 흐름 순서대로 신문 단처럼 채우기만 했습니다. 그래서 앞쪽 step으로 되돌아가는 보조 step이 모두 맨 뒤 단에 놓였고, 그 화살표가 그림 전체를 가로질렀습니다. sm-workflow에서는 step25 설계서 수정 → step13 설계 승인, step26 → step09, step39 → step04 같은 것들입니다.

바꾼 방식은 이렇습니다.
- 큰 흐름은 지금처럼 흐름 순서대로 신문 단에 놓습니다. 읽는 순서는 바뀌지 않습니다.
- 단마다 오른쪽에 곁 단을 둡니다. 나가는 화살표가 모두 앞쪽 step으로 되돌아가는 보조 step은 이어진 step 곁의 빈칸으로 옮길 수 있습니다.
- 어디에 둘지는 화살표 길이 합이 가장 작아지는 쪽으로 고릅니다. 같은 Workflow면 늘 같은 결과가 나옵니다.

처음에는 모든 노드를 자유롭게 옮겨 보았는데, 주 흐름이 무너져서 위 방식으로 좁혔습니다. sm-workflow에서 화살표 길이 합이 49,447px에서 47,383px로 줄었습니다. 작은 sample Workflow는 한 개만 빼고 결과가 그대로입니다.

## 2. [화살표 재배치] 버튼

[FAIL로 가는 실패 화살표 숨기기] 옆에 붙였습니다. 노드는 그대로 두고 화살표의 꺾는 점과 연결점만 새로 정합니다.
- 노드의 지금 위치에서 단과 줄을 읽어 내고, 자동 배치와 같은 통로 규칙으로 노드를 피해 길을 냅니다.
- 노드 사이가 좁으면 통로 안에 선을 좁혀 넣습니다. 노드가 조금 어긋나 있으면 줄 사이에서 한 번 꺾습니다.
- FAIL이 모든 step보다 오른쪽에 있으면 실패 화살표를 가로 통로 한 줄로 모읍니다.
- 노드가 겹쳐서 단과 줄을 나눌 수 없으면 연결점만 방향에 맞게 고르고, 길은 선 모양의 길 찾기에 맡깁니다. 이때는 상태 줄에 그렇다고 알려 줍니다.

[자동 배치]와 [화살표 재배치] 모두 [되돌리기]로 돌아갈 수 있게 했습니다. 전에는 [자동 배치]가 되돌리기 기록에 남지 않았습니다.

## 고친 파일
- `dstone-ai-engine/.../static/ai/assets/js/workflow-designer.js`
  - 새 함수: `arrangeCells`, `rerouteEdges`, `readGrid`, `freeRouteEdges`
  - px로 바꾸는 부분을 `gridGeometry`·`writeEdgeGeometry`로 떼어 두 기능이 같이 씁니다.
  - `planEdges`는 실패 화살표를 모을 가로 통로를 인자(`failGap`)로 받게 바꿨습니다.
- `designer.html`: 버튼을 추가했습니다.
- `main.css`: 버튼 스타일을 넣었습니다. CLAUDE.md 규칙에 따라 ai와 knowledge 두 복사본에 모두 넣었습니다. 두 파일은 원래부터 조금 달랐는데 그대로 두었습니다.
- `docs/09.dstone-ai-engine.md`: 디자이너 설명을 고쳤습니다.

## 알아 두실 점
- 화면 폭 1600px 정도에서는 탭 줄이 넘쳐 [전체 화면] 버튼이 다음 줄로 내려갑니다. 원하시면 버튼 글자나 여백을 줄이겠습니다.
- 띄워 둔 엔진은 jar 안의 정적 파일을 쓰므로, 다시 빌드하고 띄워야 바뀐 화면이 보입니다.
- 저장된 `sm-workflow.layout.json`은 건드리지 않았습니다. 버튼을 누른 뒤 [저장]해야 반영됩니다.
- 커밋은 하지 않았습니다.
