# 기존_dstoneaiengine을_배

> 생성일시: 2026-10-09 10:18

## 질문

기존 dstone-ai-engine을 배재하고
아래의 내용을 참고로 새로 module을 만드려고 해. 계획을 검토해줘.
/app/dstone/docs/temp/dstone-workflow-design.md

---

## 답변

설계 문서를 먼저 읽어보겠습니다.

---

## 답변

**결론: 방향은 맞지만, 이 문서만으로는 착수하기 이릅니다.** 승인·재개(15장)는 구체적인데, 지금 `dstone-ai-engine`이 실제로 하고 있는 일의 절반 이상이 문서에 없습니다. 착수 전에 아래 1~4번은 정해야 합니다.

기술 전제는 확인했습니다. LangGraph4j 최신 1.9.3은 Spring AI 2.0.1 기준으로 빌드되고 `langgraph4j-postgres-saver`·`mysql-saver`가 있어 현재 스택(Boot 4.1.1, Spring AI 2.0.1)과 버전은 맞습니다. 실제로 붙여 돌려 보지는 않았습니다.

## 먼저 정해야 할 것

1. **"배제"의 범위가 없습니다.** 문서는 Workflow 실행기만 다루는데, 아래는 버리는지 옮기는지 적혀 있지 않습니다.
   - Tool 계층: `@AiTool`, `FileUtil` 편집 Tool, `KnowledgeTool`, MCP, 결과 길이 제한
   - Tool 루프 제어: 호출 예산, 결과 압축, 끊긴 인자 처리
   - 공급자 대응: 60초 타임아웃 우회, `reasoning` 매핑, 공급자 오류 메시지
   - 그 밖: RAG, Chat API, Sub Agent, `dstone-boot`의 AI 화면
   
   실제 장애는 대부분 Tool 루프에서 났는데, 문서는 Agent를 "한 번 호출하고 JSON 검증"으로만 봅니다.

2. **표현식이 jq에서 경로 참조로 후퇴합니다.** 지금 `pilot-workflow`의 신규개발 경로는 검증과 문서 렌더링을 jq로 합니다. `${state.x}`만 되면 그 로직이 전부 `RULE` Step의 Java 코드가 되어, "Java를 안 고친다"는 목표와 어긋납니다. jq를 유지하길 권합니다.

3. **State 모델이 암묵적 이름으로 돌아갑니다.** `output: state.requirementAnalysis` 방식은 Step들이 전역 State를 같이 쓰는 구조입니다.
   - 재시도 루프에서 값이 덮어써지고, 병렬 실행에서는 충돌합니다.
   - 지금의 `steps.<id>.{input,output,error}`는 이 문제를 피하려고 만든 것입니다.
   - 문서에 `forEach`(병렬)가 아예 없는데, 신규개발 경로의 핵심입니다.
   - 9.3의 `CompiledGraph<PilotWorkflowState>`는 Workflow별 타입이라 범용 엔진과 모순됩니다. `Map` 기반 State여야 합니다.

4. **LangGraph4j가 덜어 주는 일이 생각보다 적습니다.** 15장의 실행·승인·이력·이벤트 테이블, 재개 Worker, 멱등성, 버전 고정은 LangGraph4j가 있어도 전부 직접 만듭니다. 얻는 것은 체크포인트와 interrupt/resume, 조건 엣지 정도이고, 영속화는 두 벌(`wf_*`와 saver 테이블)이 됩니다. 10월 7일에 Spring AI Alibaba Graph를 같은 이유로 기각했으니, 이번엔 무엇이 다른지 문서에 적어야 합니다.

## 문서 안의 문제

- **로드맵 순서**: Phase 1에서 LangGraph4j 없이 실행기를 만들고 Phase 2에서 통합하면 Phase 1 런타임을 버리게 됩니다. LangGraph4j 검증을 Phase 0으로 앞에 두어야 합니다.
- **Phase 0에서 확인할 것**: interrupt 후 재개 때 노드가 재실행되는지, 체크포인트 직렬화 방식(큰 State, 클래스 변경 시 호환), 병렬 노드, 반복 한도.
- **TOOL Step이 Phase 3**: "소스 파일은 TOOL Step만 건드린다"가 지금까지의 가장 큰 교훈이므로 Phase 1에 있어야 합니다.
- **StepType 불일치**: enum에 `ROUTER`와 `END`가 없는데 15장 그림과 YAML에서는 씁니다. `SUPERVISOR`를 어떻게 할지도 없습니다.
- **버전 고정의 구현 방법이 없음**: 파일 기반 정의에서 실행 중 버전을 유지하려면, 실행 시작 때 Workflow·Agent·Prompt·Schema 묶음을 DB에 스냅샷으로 저장해야 합니다(해시로 중복 제거).
- **승인 대상 산출물이 정의되지 않음**: `artifact: analysis-document`가 State 필드인지 파일인지 없습니다. 이게 없으면 해시 대조를 구현할 수 없습니다.
- **반려 사유 전달 경로 없음**: 5장 예시에서 반려돼 `step03`으로 돌아가도 그 `input`에 반려 사유가 없습니다. `rejectTo`와 `routes.REJECTED`도 중복입니다.
- **승인 권한**: `approverRole`을 검증하려면 사용자 신원이 필요한데, 지금 구조는 API 키로 호출자만 압니다. 권한을 `dstone-boot`에서 검증할지 이 모듈에서 할지 정해야 합니다.
- **Outbox와 재개 Worker는 과합니다**: 단일 인스턴스 전제라면 조건부 `UPDATE`(대기→재개 중)와 부팅 시 복구로 충분합니다. `dstone-knowledge`의 `AnalysisJobRecovery`와 같은 방식입니다.
- **재시도와 부작용**: `maxAttempts: 2`는 Tool을 쓰는 Agent에서 파일 수정을 두 번 실행합니다. 재시도는 Tool 없는 Agent에만 허용해야 합니다.
- **Prompt 예시**: Spring AI 기본 템플릿은 `{var}`인데 예시는 `{{requirement}}`입니다. 사용자 입력을 system prompt에 넣고 있어, 같은 절의 "경계 분리" 원칙과도 어긋납니다.
- **저장소 규칙 미반영**: `conf/` 설정 패턴, MyBatis `BaseDao`, `bin/*.sh`, 포트, DB 종류, 람다·Lombok 금지가 없습니다. `requirment` 오타도 있습니다.

## 권고

- 문서에 **"ai-engine 기능별 이관표"**(이관 / 재작성 / 폐기)를 추가합니다.
- **완료 기준**을 넣습니다. 이미 측정값이 있는 `testApp` FAQ 요청을 새 엔진에서 같은 결과로 돌리는 것이 적당합니다.
- 범위를 "Workflow 실행기만 LangGraph4j로 교체하고 Agent·Tool 계층은 그대로 이관"으로 좁히면 위험이 가장 작습니다.

이 방향으로 설계 문서를 고쳐 드릴 수 있습니다. 그러려면 1번(배제 범위)에 대한 결정이 필요합니다.
