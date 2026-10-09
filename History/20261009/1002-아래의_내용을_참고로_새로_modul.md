# 아래의_내용을_참고로_새로_modul

> 생성일시: 2026-10-09 10:02

## 질문

아래의 내용을 참고로 새로 module을 만드려고 해. 계획을 검토해줘.
/app/dstone/docs/temp/dstone-workflow-design.md

---

## 답변

검토 결론부터 말씀드리면, **이 설계서대로 새 모듈을 만드는 것은 권하지 않습니다.** 설계 내용의 대부분이 이미 `dstone-ai-engine`에 구현되어 있는데, 설계서는 그 엔진을 한 번도 언급하지 않습니다. 반면 지난 몇 주간 실측으로 얻은 장치들은 빠져 있습니다.

## 1. 기존 `dstone-ai-engine`과 겹치는 범위

| 설계서 항목 | `dstone-ai-engine` 현재 상태 |
|---|---|
| YAML Workflow/Agent 정의, 로더, 레지스트리 | 있음 (`YamlDefinitionLoader`, `WorkFlowRegistry`, `AgentRegistry`) |
| JSON Schema 입출력 계약, STRICT 검증 | 있음 (`JsonSchemas`, `SchemaOutputConverter`, `AgentContractException`) |
| 참조·도달 가능성 등 기동 시 검증 (§11) | 있음. 설계서보다 범위가 넓음 (jq 컴파일, 경로 검사 포함) |
| AGENT / TOOL / APPROVAL step | 있음. ROUTER, SUPERVISOR, forEach, Sub Agent도 있음 |
| 승인 중단·재개, 반려 후 되돌아가기 | 있음 (`WAITING_APPROVAL`, `routes` 방식 승인, 같은 step 재진입) |
| 표현식 (`ContextResolver`) | 있음 (`${ jq }`, 부작용 없음) |
| 실행 상태·이력 DB, `maxIterations` | 있음 (`AI_WORKFLOW_EXECUTION*`) |
| Tool 화이트리스트, 임의 셸 금지 | 있음 (`ConfigTool`, 화이트리스트 방식) |

설계서에서 **실제로 새로운 것**은 다음뿐입니다.

- Workflow/Agent/Prompt 버전과 실행 시점 고정
- 승인 이력 테이블과 산출물 해시 연결
- Prompt 파일 분리
- `RULE`, `SUB_WORKFLOW` step
- Agent 단위 재시도·타임아웃
- 모델 라우팅(여러 provider)
- 실행 엔진을 LangGraph4j로 교체

마지막 항목을 빼면 모두 기존 엔진에 필드 몇 개와 테이블 하나를 더하는 크기입니다.

## 2. LangGraph4j 도입 자체에 대해

- **버전 호환은 문제가 없습니다.** Maven Central 기준 최신 1.9.3(2026-10-01)의 `spring-ai.version`이 2.0.1이고 Java 17 타깃이라 현재 스택과 맞습니다. `langgraph4j-postgres-saver`도 있습니다. Spring Boot 4.1 위에서 실제로 돌려 보지는 않았습니다.
- **2026-10-07의 결정과 충돌합니다.** Spring AI Alibaba Graph를 "`WorkFlowExecutor`만 대체할 뿐"이라는 이유로 채택하지 않았는데, LangGraph4j도 대체하는 범위가 같습니다. 그때와 무엇이 달라졌는지가 설계서에 없습니다.
- **LangGraph4j가 실제로 주는 것**은 노드 단위 체크포인트, 서브그래프, 그래프 시각화(Studio)입니다. 이것이 꼭 필요한지가 판단 기준입니다.
- **그대로 대응되지 않는 부분**은 스파이크로 확인이 필요합니다.
  - forEach 병렬 실행과 부분 실패 처리
  - 같은 APPROVAL step으로 여러 번 되돌아오는 루프
  - 체크포인트 직렬화: 제가 아는 바로는 기본이 Java 직렬화라 JSON 직렬화기를 따로 붙여야 합니다.
  - 재배포 후 옛 정의 버전의 그래프로 재개하기

## 3. 설계서 자체의 문제

1. **Phase 순서가 이중 작업입니다.** Phase 1에서 자체 실행기로 APPROVAL까지 만들고 Phase 2에서 LangGraph4j로 바꾸면 실행기를 두 번 만듭니다. 승인은 중단·재개가 본질이라 Phase 1에서 제대로 만들 수도 없습니다.
2. **상태 모델이 후퇴합니다.** `output: result: state.x` 방식은 step이 공유 상태에 이름을 지어 쓰는 구조입니다. 2026-09-28에 "호출받는 쪽이 계약을 갖고 step에는 `output` 키가 없다"로 일부러 걷어낸 방식입니다.
3. **조건 분기를 쓸 방법이 없습니다.** 표현식이 경로 참조뿐이고, `RULE`의 "허용된 조건식"은 정의되지 않았습니다. 예시에서 분기는 APPROVAL의 `routes`뿐입니다.
4. **파일 수정 방식에 대한 언급이 없습니다.** `state.implementation`에 개발 Agent 출력을 담는다고만 되어 있습니다. 실측 결론은 "Agent는 데이터만 내고 파일은 TOOL step이 고친다"였고, Tool 루프로 고치게 하면 6개 중 3–4개가 호출 한도를 소진했습니다. 그런데 TOOL은 Phase 3이고 forEach는 아예 없어서, 지금의 `pilot-workflow` 신규개발 경로를 이 엔진으로는 재현할 수 없습니다.
5. **반려 흐름이 어색합니다.**
   - `rejectTo: step03`과 `routes.REJECTED: step03`이 중복입니다.
   - 반려하면 분석(step01/02)이 아니라 리뷰 Agent로 돌아가 같은 분석을 다시 리뷰합니다.
   - 반려 사유를 받는 step 입력이 예시에 없습니다.
6. **Prompt 예시가 원칙과 어긋납니다.** 시스템 프롬프트 파일에 `{{requirement}}`로 사용자 입력을 넣는데, 같은 절에서 "사용자 입력과 시스템 지침의 경계 분리"를 요구합니다. Spring AI `PromptTemplate` 기본 문법도 `{var}`입니다.
7. **재시도가 세 겹인데 관계가 없습니다.** Agent `maxAttempts`, Workflow `maxIterations`, `onError: STOP`이 어떻게 맞물리는지 정의되지 않았습니다. `timeoutSeconds: 120`도 비현실적입니다. 실측 step이 5분까지 걸렸고, Spring AI 2.0.1은 요청별 옵션으로 넣지 않으면 60초에 끊깁니다.
8. **버전의 출처가 세 군데입니다.** YAML의 `version`, 파일 경로의 `v1.st`, git입니다. 또 "실행 중에는 시작 시점 버전 유지"는 파일 기반 정의로는 지켜지지 않습니다. 배포하면 옛 파일이 사라지므로 실행 시작 때 정의 본문과 해시를 DB에 스냅샷해야 하는데, 그 설계가 없습니다.
9. **Java 인터페이스가 요구사항을 담지 못합니다.**
   - `StepExecutor.execute()`가 `Map`만 반환해 "승인 대기"를 표현할 수 없습니다.
   - 실행 ID와 호출자가 전달되지 않습니다.
   - `CompiledGraph<PilotWorkflowState>`는 범용 엔진이 특정 업무 상태 타입에 묶인 형태입니다.
10. **빠진 것들**이 있습니다.
    - 호출자(테넌트) 제한
    - Tool 호출 예산과 결과 압축, 잘린 인자 처리
    - 엔진 고정 프롬프트 계층
    - step별 대화 메모리
    - 다중 인스턴스에서의 재개 멱등성 구현 방법
11. **자잘한 것들**입니다.
    - "YAML/XML 등"은 YAML 하나로 충분합니다.
    - `pilot-requirment-…`는 오타입니다.
    - `definitions/`가 `src/main/resources` 밖에 있어 로딩·배포 방식이 불명확합니다.
    - 저장소 규칙이 반영되지 않았습니다 (`conf/` 설정, MyBatis `BaseDao`, `bin/*.sh`, 포트, 람다·Lombok 금지).

## 4. 권고

**A안 (권장): 새 모듈을 만들지 않고 `dstone-ai-engine`에 빠진 것만 추가합니다.**

1. 실행 시작 시 정의 스냅샷(본문과 해시)을 `AI_WORKFLOW_EXECUTION`에 저장하고, 재개는 그 스냅샷으로 합니다.
2. 승인 이력 테이블을 만듭니다 (승인자, 결정, 사유, 산출물 해시). 산출물이 바뀌면 재승인을 요구합니다.
3. Agent YAML에 `promptFile`을 추가합니다. 인라인 `prompt`와 둘 중 하나만 쓰게 합니다.
4. `SUB_WORKFLOW` step과 step 단위 `retry`를 추가합니다. `RULE`은 jq 분기 step으로 충분합니다.
5. 모델 라우팅은 여러 provider가 실제로 필요해질 때 합니다.

**B안: LangGraph4j가 목적이라면 새 모듈 대신 스파이크를 먼저 합니다.** 기존 YAML 문법은 그대로 두고 `WorkFlowExecutor` 내부만 그래프로 바꿔, 2절의 네 가지를 `pilot-workflow`로 확인한 뒤 결정합니다.

**C안(설계서 그대로)** 은 엔진이 두 개가 되고, `dstone-ai-engine`과 `pilot` YAML, `dstone-boot` 관리 화면의 처리 방침을 먼저 정해야 합니다.

방향을 정하려면 한 가지가 필요합니다. **지금 `dstone-ai-engine`으로 안 되거나 불편해서 새 모듈을 생각하신 지점이 무엇인지** 알려 주시면, A안과 B안 중 맞는 쪽으로 설계서를 고쳐 드리겠습니다.
