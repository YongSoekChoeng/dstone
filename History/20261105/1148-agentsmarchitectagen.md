# agentsmarchitectagen

> 생성일시: 2026-11-05 11:48

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

---

## 질문

<pasted_content id="0a97">
dstone-boot>ai 의 Workflow 실행 관리 에서
windows 에서 실행한 아래의 실행건에 대해서 확인해줘.
executionId : 3389ca80-5f7c-477e-99ff-387f234ed9f7
1. step09 을 재진행 했는데 왜 설계문서들 03-*.*.md 이 화면에서 안보이지?
</pasted_content id="0a97">

---

## 답변

Windows에서 실행한 건이라도 DB는 같은 PostgreSQL을 쓸 테니, 실행 이력과 파일 기록부터 조회하겠습니다.

---

## 답변

DB 기록은 확인했습니다. 이제 화면이 그 기록을 어떻게 보여 주는지 JSP 로직을 보고 있습니다.

---

## 답변

원인은 거의 찾았습니다. 두 번째 step09가 정말 파일을 저장하지 않고 끝났는지, Windows 쪽 로그에서 마지막 회차를 확인하고 있습니다.

---

## 답변

## 결론

화면 오류가 아닙니다. 다시 돌린 step09(아키텍처 설계)가 **설계 문서를 하나도 저장하지 않고 끝났습니다.** 그런데 실행은 '성공'으로 기록됐고, 그 상태로 승인과 개발까지 이어졌습니다.

## 확인한 내용

**1. 파일 기록**: `AI_WORKFLOW_EXECUTION_FILE`에 남은 step09의 쓰기 기록은 모두 첫 번째 실행 때 것입니다.

| 파일 | 마지막 쓰기 |
|---|---|
| 03-1.design.md | 10:37:52 |
| 03-2.taskitems.md | 10:38:15 (그 뒤 11:00:55에 step13이 고침) |
| 03-3.ddl(데이터베이스).md | 10:38:30 |

D: 드라이브의 실제 파일 수정 시각도 같습니다. 두 번째 실행(10:43~10:50)에서는 쓰기가 0건입니다.

**2. Windows 엔진 로그**: 두 번째 실행은 9회차 동안 Tool을 22번 호출했는데, 모두 읽기였습니다(readFileLines 14, readFileListAll 6, searchInFiles 2). writeFile/appendFile은 한 번도 없습니다. 마지막 9회차는 262초를 쓰고 Tool 요청 없이 끝났습니다. 그때 돌려준 답이 이렇습니다.

> 모든 구조를 파악했습니다. 핵심은 BaseVO가 이미 `seq` 필드를 가지고 있어… **이제** 피드백을 반영해 seq 흐름을 명시적으로 설계하고 산출물을 작성합니다.

"이제 작성하겠다"고만 말하고 거기서 대화를 끝낸 겁니다. 이 Agent는 출력이 글자(`output: string`)여서 엔진은 이 답도 정상 결과로 받았습니다.

**3. 화면 동작**: 스텝 이력의 '문서' 칸은 그 step의 **마지막 회차에 건드린 파일만** 보여 줍니다. 회차마다 새 폴더를 만드는 Workflow 때문에 일부러 그렇게 만들었습니다(`workflow-admin.js` `renderHistoryDocs`). 두 번째 회차에는 건드린 파일이 없으니 칩이 비어 있는 게 맞습니다. 아래 'step 문서' 표에는 첫 회차 문서가 그대로 있습니다.

## 실제 문제: 고치지 않은 설계로 개발까지 진행됨

- 10:57 설계 승인("개발 진행해")을 했을 때 디스크에 있던 `03-1.design.md`는 **첫 번째 설계**였습니다. seq 지적이 반영되지 않은 버전입니다.
- step11/12가 그 파일을 읽었고, step13 개발도 그 설계를 기준으로 `dev-resources/` 아래 파일 8개를 만들었습니다.
- 그래서 지금 개발 결과에는 승인 때 지적한 "ImageBoardVO 키값(seq)" 문제가 그대로 남아 있을 가능성이 큽니다. 생성된 소스는 아직 직접 열어 보지는 않았습니다. 다시 하려면 Workflow 실행 관리에서 step09 줄의 [여기서 다시]로 재실행하는 것을 권합니다.

## 다시 생기지 않게 하는 방법

1. **엔진에서 막기 (추천)**: 파일을 저장해야 하는 Agent가 쓰기 Tool을 한 번도 부르지 않고 끝나면, 성공이 아니라 계약 위반으로 처리합니다. `onInvalidOutput: RETRY`이면 다시 시도하고, 아니면 `onFailure`로 넘어갑니다. 예를 들어 Agent에 `execution.requireTools: [writeFile, appendFile]` 같은 항목을 새로 두는 식입니다. 범용이라 sm-workflow의 다른 Agent에도 그대로 쓸 수 있습니다.
2. **Workflow에서 막기**: step09 다음 TOOL step에서 "이번 step에서 03-1.design.md를 썼는지"를 확인합니다. 그런데 지금 있는 `filesExist` 규칙은 지난 회차 파일이 남아 있으면 통과해 버려서, 이것만으로는 이번 사례를 잡지 못합니다.
3. **화면에서 알리기**: 이전 회차에는 문서를 썼는데 마지막 회차에는 하나도 안 쓴 step이면 '이번 회차에 작성한 문서 없음'이라고 경고를 띄웁니다. 승인하는 사람이 바로 알아챌 수 있습니다.

1번과 3번을 함께 하는 게 가장 확실합니다. 어떤 방식으로 진행할까요?

---

## 질문

1번, 3번 진행하고 올려줘.

---

## 답변

1번(엔진에서 쓰기 Tool 필수 검사)과 3번(화면 경고)을 진행하겠습니다. 먼저 관련 코드를 보겠습니다.

---

## 답변

먼저 Agent 정의와 검증 쪽을 확인하겠습니다.

---

## 답변

Agent 정의에 `execution.requiredTools`를 추가합니다.

---

## 답변

이제 기동/[검사] 때 `requiredTools` 이름을 검사하도록 AgentRegistry를 고칩니다.

---

## 답변

이제 Tool 호출을 세는 `ToolCallBudgetCallback`에 "성공한 Tool 이름" 기록을 넣습니다.

---

## 답변

이제 AgentExecutor에서 답을 받은 뒤 `requiredTools`를 확인하게 합니다.

---

## 답변

컴파일은 통과했습니다. 디자이너의 Agent 양식이 `execution` 블록을 어떻게 다루는지 확인합니다. 새 키가 지워지면 안 되기 때문입니다.

---

## 답변

양식은 키 단위로 고치므로 새 키가 지워지지는 않습니다. 같은 방식으로 `requiredTools` 입력 칸도 하나 추가하겠습니다.

---

## 답변

엔진 쪽은 끝났습니다. 다음은 화면 경고(3번)입니다. 스텝 이력의 '문서' 칸에 경고를 넣겠습니다.

---

## 답변

화면 로직을 이번 실행 건의 실제 기록으로 확인해 보겠습니다(Node로 함수만 떼어 실행).

---

## 답변

그 사이에 이 실행 건이 DB에서 삭제됐습니다(Windows에서 지운 것으로 보입니다). 앞에서 조회해 둔 기록으로 테스트 데이터를 만들어 확인하겠습니다.

---

## 답변

화면 로직은 의도대로 동작합니다. 두 번째 step09에서만 경고가 뜨고, 첫 회차와 step06에서는 뜨지 않습니다. 이제 sm Agent 정의에 적용합니다.

---

## 답변

sm Workflow 테스트가 Architect의 답을 스크립트로 넣고 있다면, 이번 변경 때문에 깨질 수 있습니다. 확인하겠습니다.

---

## 답변

sm `WorkFlowTest`가 실패합니다. 원래부터 깨져 있던 것인지 확인하려고, 변경을 잠시 빼고 같은 테스트를 돌려 보겠습니다.

---

## 답변

Tool 인자 이름을 확인했습니다. 테스트를 작성합니다.

---

## 답변

새 테스트 3건과 정의 검사 테스트가 통과했습니다. 다른 테스트가 깨지지 않았는지 전체를 한 번 돌리겠습니다.

---

## 답변

기존에 깨져 있던 sm `WorkFlowTest` 3건 말고는 113건이 모두 통과합니다. 이제 문서를 고칩니다.

---

## 답변

CLAUDE.md에 이번 변경 기록을 한 단락 추가하고 있습니다. 그다음 커밋하고 푸시합니다.

---

## 답변

1번(엔진 검사)과 3번(화면 경고)을 반영해서 `main`에 커밋하고 푸시했습니다(`9a37ec29`).

## 1. 엔진: 꼭 성공해야 하는 Tool 검사

Agent의 `execution` 블록에 `requiredTools`라는 항목을 새로 만들었습니다.

```yaml
execution:
  requiredTools:
    - writeFile
  onInvalidOutput: RETRY
  maxAttempts: 2
```

- 여기 적은 Tool을 한 번도 성공하지 못하고 답을 끝내면 계약 위반으로 처리합니다. 아예 부르지 않은 경우와 `실패: ...`만 받은 경우가 모두 해당합니다. `RETRY`면 한 번 더 부르고, 그래도 안 되면 step이 실패합니다.
- 실패 메시지에는 빠진 Tool 이름과 답 앞부분이 들어갑니다. 예: `…꼭 성공해야 하는 Tool [writeFile]를 성공하지 못한 채 답을 끝냈습니다… 답 앞부분: 이제 산출물을 작성합니다.`
- `tools.allowed`에 없는 이름을 적으면 기동이나 [검사] 단계에서 막힙니다.
- 적용한 Agent: sm 요구사항, 영향도, 설계 Agent 세 개입니다(version 1.2.0).
- Workflow 디자이너의 Agent 양식에도 입력 칸을 추가했습니다.

## 3. 화면: 이번 회차에 문서를 안 쓴 step 경고

Workflow 실행 관리의 스텝 이력 '문서' 칸에 경고가 뜹니다. 조건은 앞 회차에는 문서를 썼는데 마지막 회차에는 한 문서도 쓰지 않은 경우입니다.

> ⚠ 이번 회차에 작성한 문서가 없습니다. 지난 회차 문서 3개가 그대로 남아 있습니다.

마우스를 올리면 지난 회차 문서 경로가 보입니다.

## 확인한 것

- 새 테스트 `RequiredToolsTest` 3건이 통과합니다. 저장 없이 두 번 끝나면 실패하고, 두 번째에 저장하면 성공하고, 잘못된 이름은 검사에서 막히는 경우입니다.
- 전체 테스트 113건 중 실패는 3건뿐이고, 모두 `workflow/sm/WorkFlowTest`입니다. 이번 변경을 빼고 돌려도 똑같이 실패해서, step id가 바뀐 뒤 테스트가 따라가지 못한 기존 문제로 보고 손대지 않았습니다.
- 화면 경고는 이번 실행 건의 파일 기록으로 함수만 떼어 Node에서 돌려 확인했습니다. 두 번째 step09에서만 경고가 뜨고, 첫 회차와 step06에서는 뜨지 않습니다. 브라우저로 직접 띄워 보지는 않았습니다.

## 참고

- 실행 `3389ca80-…`은 작업 도중 DB에서 삭제됐습니다. Windows 쪽에서 지우신 것으로 보입니다.
- 다시 시도할 때는 Agent 호출을 처음부터 다시 합니다. 설계 Agent는 한 번에 약 7분이 걸려서, 같은 일이 생기면 그만큼 더 걸립니다.
- Windows 쪽 엔진과 boot는 재기동해야 반영됩니다.

---

## 질문

깨져있는 테스트코드는 제거해줘.

---

## 답변

전체 110건이 모두 통과합니다. CLAUDE.md와 문서에서 지운 테스트를 설명하는 부분을 고치겠습니다.

---

## 답변

깨져 있던 sm 테스트 3건을 지우고 `main`에 푸시했습니다(`90ff2ad2`). 이제 전체 테스트 110건이 모두 통과합니다.

`workflow/sm/WorkFlowTest`에서 지운 테스트는 다음 3건입니다. 모두 sm-workflow의 step id 번호가 바뀐 뒤 테스트가 따라가지 못해서 실패하던 것입니다.
- `testSmWorkflow`
- `전체_흐름에서_문서는_입력으로_넘어가고_다시_돌릴_때_사유가_전달된다`
- `요청서가_없으면_LLM을_부르지_않고_실패한다`

같은 파일의 `설계_step이_태스크_목록을_저장하지_못하면_개발로_넘어가지_않는다`는 통과하는 테스트라 남겨 두었습니다. 지운 테스트만 쓰던 보조 메서드와 import도 함께 정리했습니다.

CLAUDE.md와 `docs/09.dstone-ai-engine.md`에 지운 테스트가 예시로 남아 있던 부분은 고쳤습니다. step 하나만 떼어 실행하는 테스트의 실제 예는 이제 `workflow/sm/RequiredToolsTest`를 가리킵니다.
