# agentsmarchitectagen

> 생성일시: 2026-11-05 11:00

## 질문

agent[sm-architect-agent] 에서 tool 호출을 과도하게 호출하지 않도록 할 수 있는 방법이 있을까?

---

## 답변

로그에서 sm-architect-agent의 실제 Tool 호출 내역을 확인하는 중입니다.

---

## 답변

sm-architect-agent는 Tool 호출 자체가 많은 것도 문제지만, 매 회차마다 지금까지 읽은 내용을 전부 다시 보내기 때문에 비용이 커집니다. 방법은 여러 가지 있고, 아래는 최근 로그(11/03)를 근거로 정리한 것입니다.

## 실제로 어땠는지

| 실행 | LLM 회차 | Tool 호출 | 입력 토큰 | 시간 |
|---|---|---|---|---|
| 1차 | 20 | readFileLines 24, searchInFiles 5, write/append 6 | 1,192,213 | 371초 |
| 2차 (work004) | 14 | readFileLines 18, searchInFiles 2, readFileListAll 1, write 3 | 630,853 | 170초 |

설계와 상관없는 호출이 상당히 많았습니다.
- **고치지 않는 공통 파일을 읽었습니다.** `BaseVO.java`(32,275자), `BaseDAO`/`BaseService`/`BaseController`, `genexon.js`, `comm-mapper.xml`이 그렇습니다.
- **예전 산출물을 다시 읽었습니다.** 덮어쓸 파일인 `03-1.design.md`(32,142자)를 두 번 나눠 읽었고, 입력으로 이미 받은 `02.impact.md`도 파일로 다시 열었습니다.
- **같은 호출을 되풀이했습니다.** `SampleBoardController.java`를 통째로 세 번 읽었고, `mapperLocations` 검색도 세 번 했습니다. `schema.sql`은 겹치는 구간을 여러 번 읽었습니다.
- **쓰지 않는 Tool이 붙어 있습니다.** knowledge Tool 6개는 두 실행 모두 0회 호출됐습니다. 그런데도 Tool 정의는 회차마다 함께 전송됩니다.

## 방법 (효과가 크고 손이 덜 가는 순서)

**1. Agent 정의만 고치기 (바로 할 수 있음)**
- `tools.allowed` 줄이기: knowledge Tool 6개와 `readFileListAll`을 빼고 `searchInFiles`, `readFileLines`, `writeFile`, `appendFile`만 남깁니다.
- `execution.maxToolCalls`를 50에서 20~25로 낮춥니다. 남은 호출이 1/5이 되면 엔진이 "지금 저장하라"고 알려 주므로, 산출물이 저장되지 않고 끝나는 일은 막을 수 있습니다.
- 프롬프트의 `[할 일]`에 읽기 범위를 못 박습니다.
  - 읽는 파일은 영향도분석서의 변경 대상과 참고 구현 파일뿐이다.
  - `Base*` 클래스, 공통 JS, 공통 매퍼처럼 고치지 않는 공통 파일은 열지 않는다.
  - `03-*.md` 산출물은 다시 읽지 않는다. 다시 설계할 때는 feedback만 보고 새로 쓴다.
  - 300줄이 넘는 파일은 필요한 구간만 `readFileLines`로 읽는다.
- `model.reasoning: high`를 낮추는 것도 검토할 만합니다. 회차마다 추론 시간이 붙기 때문입니다. 다만 모델에 따라 효과가 없을 수 있으니 실제로 재 봐야 합니다.

**2. Workflow에서 미리 읽어 넘기기 (효과가 가장 큼)**
- `step00`/`02`/`07`처럼 TOOL step(`readFiles`)이 대상 파일을 LLM 없이 읽어서 `with`로 넘기는 방식입니다. 그러면 Architect는 읽는 데 회차를 거의 쓰지 않습니다.
- 그러려면 영향도 Agent가 변경 대상과 참고 파일 경로를 **구조화된 output**(경로 목록)으로 돌려줘야 합니다. 영향도 Agent 계약도 함께 바꿔야 해서 손이 더 갑니다.

**3. 엔진에서 같은 호출 막기 (모든 Agent에 효과)**
- 한 Agent 호출 안에서 같은 Tool을 같은 인자로 다시 부르면 실제로 실행하지 않고 "이미 읽었습니다. 앞의 결과를 보세요"라고 답하게 합니다. `ToolCallBudgetCallback` 근처에 넣을 수 있습니다.
- 단, `ToolResultCompactionAdvisor`가 잘라 낸 결과는 다시 불러야 하므로 그 경우는 허용해야 합니다. CLAUDE.md에 "아직 안 한 일"로 적혀 있는 '엔진 쪽 읽기 캐시'에 해당합니다.

제 추천은 **1번을 지금 바로 적용**하고, 다음 실행의 `agent[sm-architect-agent] 합계` 로그로 효과를 확인하는 것입니다. 그래도 많으면 2번으로 넘어가면 됩니다. 1번부터 적용해 드릴까요?

---

## 질문

<task-notification>
<task-id>bkvy9fr2r</task-id>
<tool-use-id>toolu_01Ksv4ZjQUfKocSkifTTJcc6</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/f9d8808f-45e1-4ca0-bf49-a3c17bc02dfc/tasks/bkvy9fr2r.output</output-file>
<status>completed</status>
<summary>Background command "cd /app/dstone; L=$(ls -d /app/*/LOGS/dstone-ai-engine/execution 2&gt;/dev/null; grep -h APP_HOME dstone-ai-engine/conf/env*.properties | head -3); echo "$L"; f=$(find / -path '*/LOGS/dstone-ai-engine/execution/execution.log' 2&gt;/dev/null | head -1); echo $f; grep -n 'sm-architect-agent' $f | grep -E '합계|total|rounds|라운드' | tail -10; git log --oneline -5 -- dstone-ai-engine/conf/definitions/agents/sm/sm-architect-agent.md" completed (exit code 0)</summary>
</task-notification>

---

## 답변

앞서 로그 파일 위치를 찾으려고 백그라운드로 돌려 둔 검색이 끝났다는 알림입니다. 분석에 필요한 로그는 이미 백업 압축 파일에서 꺼내 확인했기 때문에 결과가 바뀌는 것은 없습니다.

1번(Agent 정의 수정)부터 적용할지 알려 주시면 바로 진행하겠습니다.

---

## 질문

agent 에서 subAgent 를 이용해서 성능을 개선할 방법은 없을까? agent 내에서 loop 돌리면서 subAgent를 돌리는 방식으로는 설계가 힘들거나 효율적이지 않은가?

---

## 답변

가능은 하지만, Architect처럼 원문을 그대로 옮겨 적어야 하는 Agent는 **Agent 안에서 LLM이 Sub Agent를 반복 호출하는 방식보다, Workflow에서 `forEach`로 파일마다 Agent를 돌리는 쪽이 훨씬 효율적**입니다. 이 프로젝트에서 이미 두 방식을 모두 써 봤기 때문에 근거가 있습니다.

## Sub Agent로 얻을 수 있는 것

지금 비용이 큰 진짜 이유는 Tool 호출 횟수가 아니라 **누적된 대화**입니다. 14회차에 입력이 63만 토큰이었다는 건 회차마다 앞서 읽은 파일 전부를 다시 보냈다는 뜻입니다.

Sub Agent는 빈 대화로 시작해서 결과만 돌려줍니다. 그래서 파일 원문은 자식 쪽에만 남고 부모에게는 요약만 쌓입니다. `subAgents: {background: true}`로 두면 자식 여러 개를 동시에 돌릴 수도 있습니다(스레드 풀 4개).

## 그런데 Agent 안의 Sub Agent 반복이 잘 안 맞는 이유

1. **이미 실측에서 나빴습니다.** pilot 일반 경로 step03은 "FR마다 위임"하게 했더니, 같은 내용을 조사하는 Sub Agent가 4번 돌고 JSP 하나를 8번 열었습니다. step02~03에 20분, Tool 91회가 들었습니다. Sub Agent를 빼자 step03이 732초에서 211초로 줄었습니다.
2. **Architect는 원문이 필요합니다.** '변경 전'은 파일에서 글자 그대로 옮겨야 합니다. 자식이 요약해서 돌려주면 정확한 텍스트가 사라지고, 부모가 그 파일을 다시 엽니다. 그래서 `excerpts` 규칙(줄 번호가 붙은 원문 구간을 돌려받고 부모는 다시 열지 않는다)을 만들었지만, 결국 원문이 부모 대화에 다시 쌓입니다.
3. **위임 결정도 LLM이 합니다.** 몇 번, 무엇을 위임할지가 실행마다 달라집니다. 자식끼리 찾은 내용을 공유하지 못해 같은 파일을 서로 다시 읽습니다.
4. **고정 비용이 자식마다 붙습니다.** 엔진 규칙, 프롬프트, Tool 정의, 추론 시간이 자식마다 새로 듭니다.
5. **제어가 느슨합니다.** Sub Agent 호출은 부모의 `maxToolCalls` 예산에 들어가지 않고, 자식 안에서 다시 위임할 수도 없습니다(깊이 1 고정).

## 더 나은 방법: Workflow에서 반복하기

신규개발 경로에서 이미 검증한 방식입니다. 반복은 Java 엔진(`forEach`)이 맡고, LLM에는 파일 하나씩 깨끗한 입력만 줍니다.

| | Agent 안 Sub Agent 반복 | Workflow `forEach` |
|---|---|---|
| 몇 번 돌지 | LLM이 정함 | 목록 길이로 정해짐 |
| 파일 읽기 | LLM이 Tool로 | TOOL step이 LLM 없이 한 번 |
| 대화 누적 | 부모에 계속 쌓임 | 호출마다 새 대화 |
| 병렬 | background일 때만 | 기본 병렬 |
| 실측 | step03 732초 | step17 파일 8개 설계 23~27초 |

sm-workflow에 적용하면 다음과 같습니다.

```
step07  영향도 Agent  → output에 대상 목록 [{path, 참고파일, 변경요지}] (구조화)
step08a TOOL readItemFiles   → 대상/참고 파일 내용을 state에 (LLM 없음)
step08b AGENT forEach  파일별 설계 Agent (Tool 없음, reasoning none)
                       → {path, 변경전, 변경후, 이유} 
step08c TOOL checkData       → 각 '변경 전'이 파일에 정확히 한 번 있는지 검사
step08d AGENT  설계서·태스크 작성 Agent (writeFile/appendFile만, 파일 읽기 없음)
```

이 구조로 가면 몇 가지를 함께 챙겨야 합니다.
- **파일 사이에 공유하는 이름**(클래스명, URL, 쿼리 ID)은 앞 단계에서 먼저 정해 두어야 합니다. 신규개발 경로의 `sharedNames`가 이 역할입니다.
- forEach는 병렬로 돌기 때문에 같은 파일이 목록에 두 번 들어가면 안 됩니다.
- 영향도 Agent의 output 계약이 바뀝니다. 지금처럼 md 문서만 쓰는 방식에서 구조화된 목록까지 돌려주는 방식으로 바꿔야 합니다.

## Agent 안 Sub Agent가 맞는 경우

조사 단위가 서로 독립적이고, 부모가 원문이 아니라 **결론만** 필요할 때입니다. 예를 들면 "이 진입점의 호출 흐름을 추적해서 관련 파일 목록만 줘" 같은 일입니다. sm-workflow에서는 Architect보다 영향도 Agent 쪽에 가깝습니다.

## 정리

- 단기: 앞에서 말씀드린 1번(Tool 축소, `maxToolCalls` 하향, 읽기 범위 규칙)
- 근본: Architect를 **"파일별 forEach 설계 + 조립 Agent"**로 나누기
- Agent 안 Sub Agent 반복: Architect에는 권하지 않습니다

forEach 구조로 바꾸는 설계안을 sm-workflow 기준으로 더 구체적으로 잡아 드릴까요? 영향도 Agent의 output 계약부터 정해야 합니다.
