# dstoneaiengine_의_con

> 생성일시: 2026-10-21 08:27

## 질문

dstone-ai-engine 의 /conf/definitions 들이 파일명 및 id 등이 한글로 작성될 경우 문제될게 있을까?

---

## 답변

코드에서 id·파일명 검사와 인코딩 처리 부분을 확인해 보겠습니다.

---

## 답변

JS가 이미 인코딩한 경로를 서버가 한 번 더 인코딩하는지 직접 확인해 보겠습니다.

---

## 답변

경로 표현식(`${state.x.필드}`)이 한글 필드명을 받는지도 확인해 보겠습니다.

---

## 답변

결론부터 말씀드리면 **Workflow·Agent의 파일명과 `id`는 한글로 써도 엔진 자체는 대부분 동작합니다.** 다만 한글을 쓰면 안 되는 이름이 따로 있고, 디자이너 화면에서는 실제로 깨지는 곳이 하나 있습니다.

## 1. 한글을 쓰면 부팅에 실패하는 것 (검사 규칙이 영문만 허용)

| 대상 | 규칙 | 위치 |
|---|---|---|
| step id, forEach 항목 변수명 | `[A-Za-z_][A-Za-z0-9_]*` | `WorkFlowRegistry.java:95` |
| `state:` 이름 (`save` 대상) | 같은 규칙 | `WorkFlowRegistry.java:101` |
| **Sub Agent로 쓰는 Agent의 id** | `[A-Za-z0-9_-]{1,64}` | `AgentRegistry.java:62` |
| 프롬프트 `{변수}` 이름 | 영문만 (한글이면 오류 없이 건너뜀) | `AgentExecutor.java:109` |

Sub Agent id를 영문으로 묶은 이유는 그 id가 LLM에게 Tool 이름으로 그대로 전달되기 때문입니다. OpenAI 계열 function name 규칙이 같은 패턴이라서, 이 제한은 풀 수 없습니다. 그래서 **나중에 Sub Agent로 쓸 수도 있는 Agent는 처음부터 영문 id로 두는 게 안전합니다.**

## 2. 한글을 써도 되는 것

- **Workflow id, Agent id(Sub Agent가 아닌 경우)**: 형식 검사가 없습니다. 실행 기록 테이블의 `WORKFLOW_ID VARCHAR(100)`도 PostgreSQL이 바이트가 아니라 글자 수로 세므로 문제없습니다.
- **파일명·폴더명**: 로더가 UTF-8로 읽고 씁니다. 디자이너의 경로 검사(`DefinitionService.java:52`)도 `가-힣`을 허용합니다. 현재 WSL, Windows(MS949), Docker 이미지(temurin은 `LANG=en_US.UTF-8`) 모두 한글 파일명을 다룰 수 있습니다. 이 WSL은 `sun.jnu.encoding = UTF-8`인 것을 직접 확인했습니다.
- **Picoschema 필드명, `${state.x.한글필드}` 경로**: 경로 파서가 `Character.isLetterOrDigit`를 쓰므로 한글이 통과합니다. 필드명에 공백이 들어가면 경로로는 읽을 수 없습니다.
- **REST 호출**: `/api/ai/workflow/{id}/execute`처럼 id가 URL 경로에 들어가는 경우, 호출하는 쪽에서 퍼센트 인코딩만 하면 됩니다. Tomcat이 UTF-8로 풀어 줍니다. `dstone-boot`의 `WorkFlowTestService`는 WebClient가 알아서 인코딩합니다.

## 3. 실제로 깨지는 곳: Workflow 디자이너에서 한글 id 열기

`workflow-designer.js:276`은 `encodeURIComponent(id)`로 이미 인코딩한 경로를 보냅니다. 그런데 `WorkFlowDesignerService.java:59`가 `UriComponentsBuilder…build().encode()`로 한 번 더 인코딩합니다. 같은 Spring 7.0.9로 재현해 보니 결과가 이렇게 나옵니다.

```
한글 → /workflows/%25ED%2595%259C%25EA%25B8%2580   (이중 인코딩)
```

이 때문에 엔진은 id를 `%ED%95%9C…`라는 글자로 받고, 한글 id인 Workflow·Agent를 디자이너에서 열면 "없음"이 됩니다. 영문 id는 인코딩할 글자가 없어서 지금까지 드러나지 않았습니다. 고치려면 서버 쪽에서 `.build(true)`(이미 인코딩됨)로 바꾸거나 JS에서 인코딩을 빼면 됩니다. 아직 수정하지는 않았습니다.

## 4. 운영상 주의할 점 (치명적이지는 않음)

- 다른 서버로 옮길 때 JVM 로케일이 `POSIX`/`C`이면 한글 파일명이 `?`로 바뀌어 정의를 읽지 못합니다. 지금 쓰는 환경들은 괜찮지만, 새 서버를 붙일 때는 `LANG`이 UTF-8 계열인지 확인해야 합니다.
- zip으로 주고받거나 Windows ↔ Linux로 복사할 때 한글 파일명이 깨질 수 있습니다. git은 경로를 UTF-8로 다루므로 git으로 동기화하면 괜찮습니다.

**추천**: Workflow id와 파일명은 한글로 써도 되지만, Agent id는 영문으로 두고 사람이 읽을 이름은 `description`에 적는 쪽을 권합니다. 한글 id를 실제로 쓸 계획이라면 3번 디자이너 버그부터 고쳐 드릴까요?
