# pasted_content_id3a8

> 생성일시: 2026-10-09 14:25

## 질문

<pasted_content id="3a8d">
dstone-ai-engine 와 상관없이 dstone-workflow 모듈을 만들고자 해.
네 의견을 정리해서 말해줘.
<의견정리파일>
/app/dstone/docs/temp/dstone-workflow-design.md
</pasted_content id="3a8d">

---

## 답변

문서를 읽었습니다. LangGraph4j는 직접 써 보거나 버전 호환을 확인한 것이 아니라, 문서 내용과 지금 엔진 구조에 비춰 본 의견입니다.

## 결론

모듈을 새로 만드는 것은 찬성입니다. 다만 이 문서에서 값진 부분은 §15(영속 승인·재개·버전 고정·감사)이고, **LangGraph4j는 빼고 직접 만든 상태 기계로 가는 쪽을 권합니다.**

## LangGraph4j를 권하지 않는 이유

- **어려운 부분은 어차피 직접 만듭니다.** `wf_execution`/`wf_approval`/Outbox/멱등 재개/버전 고정은 모두 자체 구현이고, 문서도 §15.9에서 "체크포인트만으로 자동 재개되지 않는다"고 적고 있습니다. LangGraph4j가 맡는 것은 "다음 노드 고르기"뿐인데, 이는 `WorkFlowExecutor.nextStepId()` 수준의 작은 코드입니다.
- **진실이 두 군데가 됩니다.** 그래프 체크포인트와 `wf_execution`이 따로 있으면 둘이 어긋났을 때의 복구 규칙이 또 필요합니다. 직접 만들면 "Step이 끝날 때마다 컨텍스트 + 다음 Step id를 한 트랜잭션으로 저장"이 곧 체크포인트입니다.
- **로드맵이 스스로 증명합니다.** Phase 1은 LangGraph4j 없이 AGENT+APPROVAL을 돌리고 Phase 2에서 끼워 넣습니다. Phase 1 런타임을 버리게 되거나, 필요 없었다는 뜻이 됩니다.
- **10-07에 같은 판단을 이미 했습니다.** Spring AI Alibaba Graph를 "`WorkFlowExecutor`를 대체할 뿐"이라며 채택하지 않았는데, 이번도 같은 종류입니다.
- **호환성이 미확인입니다.** Spring Boot 4.1 / Spring AI 2.0.1 조합에서 도는지, 중단 노드가 재개 때 다시 실행되는지를 모릅니다.

그래도 쓰고 싶다면 1단계를 "승인 대기 → 프로세스 재시작 → 재개" 하나만 검증하는 스파이크로 바꾸고, 그 결과로 정하는 것이 안전합니다.

## 문서에서 꼭 가져갈 것 (지금 엔진에 없는 것)

- **정의 버전·해시 고정**: 지금은 재배포하면 대기 중이던 실행이 새 YAML로 재개됩니다.
- **승인을 독립 엔티티로**: 라운드마다 새 `approval_id`, 산출물 해시 연결, 승인자 권한 검증, 타임아웃·에스컬레이션. 지금은 컨텍스트 안의 값이고 소비되면 지워집니다.
- **멱등 재개와 재시작 복구**, Step 실행 이력·감사 이벤트 테이블.
- **Agent 단위 재시도 정책** (`maxAttempts`, `onInvalidOutput`).

## 문서가 지금 엔진보다 후퇴하는 부분

| 문서 | 문제 | 권고 |
|---|---|---|
| Step의 `output: state.x` + 공유 state | 이름을 Step이 정하는 암묵 결합이고, 엔진에서 일부러 없앤 구조입니다 | `steps.<id>.output` 고정, 계약은 Agent가 소유 |
| `${state.a.b}` 경로식 | 문자열 조립·기본값이 안 되고 `${ENV}` 치환과 충돌합니다 | jq 유지 |
| 단일 `StepDefinition` + 공통 `StepExecutor(Map→Map)` | 타입에 없는 키가 조용히 무시됩니다 | sealed 타입별 record + `switch` |
| Agent `role` enum, `context.sources` | 실행 의미가 불명확합니다 | 빼거나 의미를 먼저 정의 |
| `maxIterations: 3` 전역 | 루프별인지 Step별인지 모호합니다 | Step 방문 횟수 기준으로 명시 |
| `rejectTo`와 `routes.REJECTED` | 중복입니다 | `routes`만 |
| `{{requirement}}` | Spring AI 템플릿은 `{var}`입니다 | 하나로 통일 |

그 밖에 문서에 없는 것이 있습니다.

- **빠진 기능**: `forEach`, SUPERVISOR/ROUTER, Step별 메모리, Sub Agent가 없습니다. 지금 pilot이 쓰는 기능입니다.
- **예시가 실제 pilot과 다릅니다**: 문서는 JSON 산출물을 state로 넘기지만, 실제로는 긴 문서를 파일로 쓰고 다음 Agent가 읽습니다. 1만 자 넘는 문서를 STRICT JSON으로 받으면 `max-tokens` 절단 문제가 다시 납니다. "산출물은 저장소 참조"(§15.5)를 Step 계약 수준까지 내려야 합니다.
- **가장 과소평가된 부분**: "Spring AI ChatClient 연동" 한 줄입니다. 60초 타임아웃 우회, Tool 호출 한도, 결과 자르기·압축, 끊긴 인자 처리, 빈 응답 처리, 엔진 규칙 프롬프트가 모두 여기 들어갑니다.

## 정해 주셔야 할 것

"ai-engine과 상관없이"의 범위입니다. Agent/Tool 실행부(`AgentExecutor`, `ConfigTool`, `tools.*`)까지 새로 만들면 위 교훈을 전부 복제해야 하고, 두 벌을 유지하게 됩니다. 저는 **오케스트레이션·영속·승인만 새 모듈**로 하고, Agent/Tool 실행부는 공용 모듈로 뽑아 둘이 같이 쓰는 쪽을 권합니다. 완전 독립을 원하시면 복사해서 시작하되, ai-engine을 언제 걷어낼지 함께 정해야 합니다.
