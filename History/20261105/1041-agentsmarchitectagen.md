# agentsmarchitectagen

> 생성일시: 2026-11-05 10:41

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
