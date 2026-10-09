# localcommandcaveatTh

> 생성일시: 2026-10-09 10:15

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

아래의 내용을 참고로 새로 module을 만드려고 해. 계획을 검토해줘.
/app/dstone/docs/temp/dstone-workflow-design.md

---

## 답변

설계 문서를 먼저 읽어 보겠습니다.

---

## 답변

계획을 검토했습니다. 결론부터 말하면 **이 문서대로 새 module을 처음부터 만드는 것은 권하지 않습니다.** 문서 범위의 대부분이 `dstone-ai-engine`에 이미 있고, 정말 새로운 부분(§15 승인·재개의 운영 요건)은 LangGraph4j 없이도 만들 수 있기 때문입니다.

## 1. 가장 큰 문제: 기존 엔진과의 관계가 없습니다

문서에 `dstone-ai-engine`이 한 번도 나오지 않습니다. 그런데 아래 항목은 이미 구현되어 돌아가고 있습니다.

| 문서 항목 | `dstone-ai-engine` 현재 상태 |
|---|---|
| YAML Workflow/Agent 정의, 로더, 부팅 시 검증 | 있음 (`YamlDefinitionLoader`, `WorkFlowRegistry`의 참조·도달 가능성 검사) |
| JSON Schema 입출력 계약, 출력 파싱·검증 | 있음 (`AgentExecutor`, `SchemaOutputConverter`) |
| 표현식 | 있음 (`${ jq }`) |
| 순차·분기·루프·병렬, `maxIterations` | 있음 (`WorkFlowExecutor`, `forEach`) |
| AGENT / TOOL / APPROVAL / ROUTER | 있음 (SUPERVISOR 포함) |
| 승인 대기 후 재개, 승인 결과별 경로 | 있음 (`routes` 방식 APPROVAL, `CONTEXT_JSON`에 영속) |
| Tool 화이트리스트, MCP, RAG | 있음 |

10월 7일에 Spring AI Alibaba Graph를 "`WorkFlowExecutor`만 대체할 뿐"이라는 이유로 채택하지 않았는데, LangGraph4j도 같은 자리를 차지합니다. 그때와 무엇이 달라졌는지가 문서에 있어야 합니다.

## 2. 문서에서 실제로 새로운 것

아래는 기존 엔진에 없고, 코드에서 빈 곳도 확인했습니다.

- **중복 재개 방지**: `WorkFlowExecutionService.decide()`는 조회 → 상태 확인 → 실행 순서라서, 같은 승인 요청이 동시에 두 번 오면 둘 다 재개됩니다.
- **정의 버전 고정**: 재개할 때 현재 레지스트리의 정의를 `currentStepIndex`(순번)로 찾습니다. 승인 대기 중에 YAML의 step 순서가 바뀌면 엉뚱한 step에서 이어집니다.
- **승인 요청 HTTP 스레드 점유**: `decide()`가 재개를 동기로 끝까지 실행합니다.
- **승인 이력 테이블**: 승인 라운드, 산출물 해시, 승인자 권한, 타임아웃이 없습니다.
- **재시작 복구**: `RUNNING`으로 남은 실행을 부팅 때 정리하는 코드를 찾지 못했습니다.
- **그 밖**: Prompt 파일 분리, Agent 단위 출력 재시도(`onInvalidOutput`), `RULE`, `SUB_WORKFLOW`.

이 항목들은 테이블 2~3개와 조건부 `UPDATE`(`WAITING_APPROVAL` → `RESUMING`), 정의 스냅샷 저장으로 풀립니다. 그래프 엔진이 필요한 일이 아닙니다.

## 3. LangGraph4j에 대해 확인한 것

- **호환성은 문제없습니다.** 최신 1.9.3(2026-10-01)의 `spring-ai.version`이 2.0.1이고 Java 17 기준이며, Postgres 체크포인트 저장 모듈도 있습니다.
- **API가 아직 움직입니다.** 최근 2주에 릴리스가 4번 나왔고, 1.9.2는 `StateSnapshot`의 호환이 깨지는 변경, 1.9.3은 재개 방식 재작업이 들어 있습니다. 문서의 핵심인 중단·재개가 바로 그 영역입니다.
- **확인하지 못한 것**: 실행 시점에 개수가 정해지는 병렬(`forEach`)을 그래프로 표현할 수 있는지, 중단된 노드가 재개 때 다시 실행되는지는 릴리스 노트에서 알 수 없었습니다. 문서 §15.9도 미정으로 둔 부분입니다.
- **구조 비용**: 상태 저장소가 `wf_execution`과 체크포인트 둘이 되고, 체크포인트 저장 모듈은 MyBatis가 아닌 자체 JDBC와 테이블을 씁니다. 파일 내용처럼 큰 값이 state에 들어가면 체크포인트마다 직렬화됩니다.

## 4. 문서 내부의 빈틈

- **로드맵 순서가 어긋납니다.** Phase 1에서 LangGraph4j 없이 실행 엔진을 만들고 Phase 2에서 통합하면 실행기를 두 번 만듭니다. §15.13의 순서와도 다릅니다.
- **TOOL이 Phase 3입니다.** `pilot` 측정 결과는 "Agent는 데이터만 내고 파일은 TOOL step이 다룬다"였습니다. §5 예시의 Agent 5개 직렬 연결은 느리고 불안정하다고 이미 측정한 형태입니다.
- **옛 정의를 보관할 곳이 없습니다.** "시작 당시 버전으로 재개"하려면 옛 버전 정의를 읽을 수 있어야 하는데, `definitions/` 디렉터리만 있습니다.
- **표현식이 경로만 됩니다.** `${state.x}`로는 조건이나 문자열 조립을 못 하고, `RULE`의 "허용된 조건식" 문법이 정의되지 않았습니다. Prompt의 `{{requirement}}`도 Spring AI 기본 `{var}`와 다릅니다.
- **`StepExecutor`가 `Map`만 돌려줍니다.** 대기·실패·경로를 표현할 수 없습니다. 기존 `StepOutcome`에는 있습니다.
- **예시끼리 다릅니다.** §5는 `rejectTo`와 `routes`가 중복이고 `next: END`인데, §15.4는 `type: END` step입니다. `StepType`에 ROUTER와 END가 없고, Agent id도 서로 다릅니다(`requirment` 오타 포함).
- **주체가 없는 기능이 있습니다.** `TIMEOUT` 경로를 누가 발동하는지(스케줄러), `approverRole`을 어디서 검증하는지(사용자는 `dstone-boot`에 있음), 산출물 해시를 누가 계산하는지가 없습니다.
- **Outbox와 Worker는 과합니다.** 단일 인스턴스 구조에서는 조건부 상태 전이와 부팅 시 복구로 시작해도 됩니다.

## 5. 권고

1. **권장: `dstone-ai-engine`을 확장합니다.** 2번의 항목을 §15 설계대로 넣습니다. 15개 샘플과 `pilot` Workflow, `dstone-boot` 화면이 그대로 유지됩니다.
2. **LangGraph4j 자체가 목적이라면 2~3일 검증을 먼저 합니다.** 기존 Workflow 하나(`forEach` + 되돌아가는 APPROVAL + 승인 대기 중 재시작)를 Postgres 체크포인트로 옮겨 보고 결정합니다. 채택하더라도 새 module이 아니라 `WorkFlowExecutor` 자리의 어댑터로 넣는 것이 중복이 적습니다.
3. **새 module은 엔진을 대체할 계획일 때만 합니다.** 그 경우 기존 YAML과 화면의 이전 계획이 문서에 추가되어야 합니다.

새 module을 따로 두려는 이유가 LangGraph4j 채택인지, 엔진 교체인지, 승인 기능 강화인지 알려 주시면 그 방향으로 계획을 구체화하겠습니다.

Sources:
- [LangGraph4j releases](https://github.com/langgraph4j/langgraph4j/releases)
- [LangGraph4j parent pom.xml](https://raw.githubusercontent.com/langgraph4j/langgraph4j/main/pom.xml)
