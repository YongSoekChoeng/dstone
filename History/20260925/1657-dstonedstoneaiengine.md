# dstonedstoneaiengine

> 생성일시: 2026-09-25 16:57

## 질문

dstone/dstone-ai-engine/src/main/resources/workflows/workflow-yml-guide.md
에서 2.5 inputs 에 대한 설명을 보다 쉽고 다양하게 수정해줘. 실제로 샘플용 workflow.yml를 표현하고 yml 로부터 각 단계마다 파싱하는 단계 및 전달되는 모양을 입체적으로 표현해줘.
dstone/dstone-ai-engine/src/main/resources/agents/agent-yml-guide.md 도 마찬가지로 수정해줘.

---

## 답변

두 가이드 파일부터 읽어보겠습니다.

---

## 답변

Now the core code paths for input flow.

---

## 답변

코드 흐름 확인이 끝났습니다. 이제 workflow 가이드의 `2.5 inputs`를 새로 작성합니다.

---

## 답변

샘플 속 주장 몇 가지(빈 문자열 통과 여부, `??`의 누락 경로 처리)를 코드로 검증하겠습니다.

---

## 답변

검증 완료, 문서 내용과 일치합니다. 이제 workflow 가이드의 기존 2.5 절(102~191행)을 새 내용으로 교체합니다.

---

## 답변

검증이 필요한 부분(AgentDefinition 필드, `{{ }}`를 prompt에 적었을 때의 동작)을 확인하겠습니다.

---

## 답변

`description` 필드를 목록에 추가하고, `{{ }}`/`{a.b}`를 prompt에 넣었을 때 실제 동작은 jshell로 직접 확인해 보겠습니다.

---

## 답변

이제 agent 가이드의 기존 `{변수}` 블록(75~90행)을 교체합니다.

---

## 답변

두 가이드를 고쳤습니다. 둘 다 **샘플 YAML → 파싱 → 요청 → 실행 중 전달 → LLM에게 실제로 가는 메시지** 순서로 따라가며 보여 주는 구조입니다. 커밋은 하지 않았습니다.

### `workflow-yml-guide.md` — 2.5 `inputs` 새로 작성
- **도입**: `inputs`를 Java 메서드의 매개변수로, `variables`를 넘기는 인자로 비유했습니다.
- **2.5.1 한눈에 보기**: ①~⑫ 단계를 기동 시(한 번) / 요청 시(매번) / step 실행 시(step마다)로 나눠 한 그림에 담았습니다.
- **2.5.2 샘플**: 설명용 `sql-review` Workflow와 Agent, 호출 요청 JSON입니다. 네 가지 타입(`list<string>`, `string`, `integer`, `object`)을 모두 씁니다.
- **2.5.3 단계별 따라가기**: 각 단계에서 값의 모양을 차례로 보여 줍니다.
  - SnakeYAML이 만든 Map
  - Jackson이 만든 `FieldDefinition` (축약형과 확장형이 같은 모양이 됨)
  - 기동 시 참조 검사 표
  - 요청 검사 순서와, JSON 값이 어떤 Java 타입으로 바뀌는지
  - 실행 컨텍스트 트리, forEach 반복, 채우기 전/후 비교, 최종 LLM 메시지 상자
- **2.5.4**: 같은 값이 TOOL 인자 / 글자 속 / Agent prompt / forEach 중 어디에 쓰이느냐에 따라 어떤 모양이 되는지 비교한 표입니다.
- **2.5.5 패턴 A~F**: 선언 없음, 문자열, 리스트+forEach, `list<object>`, 숫자/boolean, 그리고 선택값 흉내(`object` + `??`)입니다.
- **2.5.6~2.5.8**: 타입별 통과 값, 요청별 결과(실제 오류 문구 포함), 선언 쪽 경우별 정리입니다. 기존 내용은 모두 옮겨 담았습니다.

### `agent-yml-guide.md` — 1.2 `prompt`의 `{변수}` 채우기 새로 작성
Agent 가이드에는 `inputs` 절이 따로 없어서, 같은 역할을 하는 `{변수}` 채우기를 같은 방식으로 풀었습니다.
- 중괄호 세 가지(`${VAR}` / `{x}` / `{{input.x}}`)를 언제, 무엇으로 채우는지 비교한 표를 넣었습니다.
- **1.2.1~1.2.3**: 채팅 API와 Workflow step 두 경로를 한 그림으로 보여 주고, 설명용 `doc-translator-agent`로 `AgentDefinition`, 두 경로, 최종 메시지 구성(system / 이전 대화 / user + RAG + 형식 지시)까지 따라갑니다.
- **1.2.4~1.2.6**: 두 경로 비교, 패턴 A~E, 경우별 결과입니다. 기존의 "step 종류별로 덧붙이는 것" 표는 1.2.7로 번호만 붙였습니다.

### 직접 실행해 보고 바로잡은 점
Spring AI `PromptTemplate`을 직접 돌려 보니 기존 문서의 서술과 다른 점이 있어서 두 문서에 실제 동작으로 반영했습니다.
- prompt에 `{{input.x}}`를 적으면 오류 없이 `input.x`라는 글자가 들어갑니다. 조용히 틀리므로 문서에 경고로 적었습니다.
- `{options.strict}`처럼 맵 안쪽 키를 꺼내는 것은 **동작합니다**.
- 리스트는 구분자 없이 `ab`로, 맵은 키 이름만 `strict`로 들어갑니다. 기존 문서는 "모양을 보장하지 않는다"고만 적혀 있었습니다.

### 참고
- 두 샘플(`sql-review`, `doc-translator-agent`)은 **설명용이라 실제 파일은 만들지 않았고**, 본문에도 그렇게 적었습니다. 실제로 돌려 볼 수 있는 가장 가까운 샘플(`sample-foreach-parallel.yml`, `sample-general-chat.yml`)도 함께 안내했습니다. 원하시면 실제 샘플 파일로도 만들어 드리겠습니다.
- `docs/09.dstone-ai-engine.md`는 요약만 두고 자세한 내용은 이 두 가이드를 가리키고 있으며, 서로 어긋나는 서술이 없어서 고치지 않았습니다.
