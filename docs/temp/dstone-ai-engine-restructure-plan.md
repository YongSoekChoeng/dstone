# dstone-ai-engine 구조 재설계 계획

## Context (왜 다시 손대는가)

`dstone-ai-engine`은 2026-09-15에 이미 한 차례 전면 재작성되어 `Workflow → Step(Agent/Tool)` 모델, YAML 정의, 동기/비동기 실행, RAG, 세션, 인증/레이트리밋까지 갖춘 상태다 (`docs/09.dstone-ai-engine.md` 참고). 이번 요청은 그 위에 patch를 얹는 게 아니라, 아래 두 가지 **구조적 공백**을 메우기 위해 실행 엔진과 패키지 일부를 과감히 다시 짜는 것이다.

1. **휴먼 승인(HITL) 개념이 아예 없다.** 현재 `WorkflowExecutor.run()`은 한 번 호출되면 끝까지 동기적으로 실행되는 단일 상태 머신이고, 중간 상태를 영속화하지 않는다. `SUPERVISOR` 스텝은 LLM 자기검증이지 사람 승인이 아니다. → Step 사이에 "승인 대기"로 멈췄다가 외부 신호로 재개되는 지점이 새로 필요하다.
2. **MCP 연동이 전무하다.** 코드/설정 전체에 MCP 관련 의존성·클래스가 0건 (grep 확인 완료). → 외부 MCP 서버의 툴을 가져다 쓰는 클라이언트 경로를 신규로 설계해야 한다.

그 외 패키지 구조(`api/common/runtime/tools`)와 리소스 구조(`resources/workflows`, `resources/agents`)는 사용자가 제시한 초안과 이미 거의 일치하므로, **잘 맞는 부분은 유지**하고 신규 요구사항이 실제로 요구하는 지점만 바꾼다 (무조건 전부 새로 쓰지 않음 — "기존 소스가 방해될 때만 과감히 포기"라는 공통지침을 그대로 따름).

사용자 결정 사항(질의응답으로 확정):
- 승인 대기 상태 영속화 → **PostgreSQL 신규 테이블** (기존 pgvector용 datasource 재사용, Redis TTL 방식 폐기)
- 이번 범위 → **MCP 클라이언트만** 설계/구현. MCP 서버(외부 노출)는 이번엔 제외, 향후 과제로만 언급.
- **RAG는 별도 Step 유형이 아니다** — LLM 프롬프트에 Advising되는 것이므로 `AgentDefinition.ragEnabled` 경로로만 존재하고, LLM 없는 순수 검색은 `RagSearchTool`(TOOL 스텝)로 처리한다.

---

## 1. 작업단위 정의: Workflow → Step → (Agent / Tool / Approval)

### 1.1 Step 유형 재정리

기존 `StepType`: `AGENT`, `TOOL`, `RAG`, `SUPERVISOR`. 이번에 **`RAG`를 폐지**하고 **`APPROVAL`**을 추가한다 — RAG는 "검색 결과를 어디에 꽂을지"의 문제이지 독립된 작업 단위가 아니기 때문.

**RAG 유형을 없앤 이유**: 현재 `RAG` StepType(`RagStepRunner`)은 벡터 검색만 하고 LLM을 부르지 않는 스텝인데, 실제 YAML 어디에도 쓰이지 않고 있고(`oracle-to-postgresql`/`parallel-test` 모두 미사용), `AgentDefinition.ragEnabled`로 이미 존재하는 "Advisor를 통한 프롬프트 증강" 경로와 검색 로직이 두 곳(`RagStepRunner` vs Advisor 빌더)으로 쪼개져 있었다. RAG는 본질적으로 "LLM 호출 전에 컨텍스트를 채워주는 것"이므로:
- **LLM이 필요한 경우** → `AgentDefinition.ragEnabled: true` (AGENT 스텝의 Advisor로 자동 적용, §5)
- **LLM 없이 검색 결과만 필요한 경우** (예: 검색된 문서를 그대로 외부 스크립트에 넘기고 싶을 때) → 별도 StepType이 아니라 **`tools.rag.RagSearchTool`**(신규, `@AiTool`)을 만들어 일반 `TOOL` 스텝에서 호출한다. `SqlSyntaxTool`처럼 위험하지 않은 조회성 동작이라 화이트리스트 없이 항상 활성화.

이렇게 하면 "검색"이라는 동작이 `RagRetrievalChain`(§5) 하나로 통일되고, Step 유형은 실행 주체 기준으로 4가지로 단순해진다.

| Step 유형 | 실행 주체 | 성공/실패 판정 | 비고 |
|---|---|---|---|
| `AGENT` | LLM 호출 (`AgentExecutor`) | 항상 성공 | 기존 유지. `ragEnabled: true`면 `RagRetrievalChain`이 만든 Advisor가 자동으로 프롬프트를 증강 |
| `TOOL` | 내부/외부 프로그램 (`ToolExecutor`) | 결과 문자열 `"실패:"` 접두어 | 기존 유지. LLM 없는 순수 검색이 필요하면 `RagSearchTool`을 이 경로로 호출 |
| `SUPERVISOR` | LLM 구조화 판정(`Verdict`) | `Verdict.pass()` | 기존 유지 — **사람이 아닌 LLM 자기검증**이라는 의미를 문서/주석에 명확히 남긴다 |
| `APPROVAL` (신규) | 사람 (외부 API 호출) | 승인/반려 | 실행을 **중단(PAUSE)**하고 외부 신호를 기다린다 |

### 1.2 HITL 흐름 설계

예시: `Step1 -> Step2 -> 승인자 승인 -> Step3`

```
WorkflowExecutor.run(executionId)
  ├─ Step1 실행 → 결과를 WorkflowExecution에 영속화(step index 증가)
  ├─ Step2 실행 → 결과 영속화
  ├─ Step(APPROVAL) 도달
  │     → WorkflowExecution.status = WAITING_APPROVAL 로 저장하고 즉시 리턴 (스레드 반납)
  │     → 승인자는 GET /api/ai/workflow/executions?status=WAITING_APPROVAL 로 대기 목록 조회
  │     → 승인자가 POST /api/ai/workflow/executions/{executionId}/decision
  │        { approved: true|false, approver: "...", comment: "..." } 호출
  │     → 서버는 decision을 WorkflowContext.variables.approvals.{stepId} 에 기록,
  │        status = RUNNING 으로 바꾸고 다음 스텝(onSuccess/onFailure)부터 **재개**
  └─ Step3 실행 → ... → DONE/FAILED
```

`ApprovalStepRunner`도 다른 러너와 **완전히 동일한 방식으로 호출**된다(§2) — `WorkflowExecutor`가 `StepType.APPROVAL`을 미리 분기해서 특별 취급하지 않는다. 대신 `ApprovalStepRunner.run()` 내부에서 `WorkflowContext.variables.approvals.{stepId}`에 결정이 이미 들어있는지 확인해, 없으면 `PENDING`을 반환한다. **재개(resume)는 "다음 스텝으로 건너뛰기"가 아니라 "같은 스텝을 다시 실행하기"다** — `decision` API가 호출되면 결정을 `variables`에 기록한 뒤 같은 `approve-step`을 다시 실행하고, 이번엔 결정이 있으니 `SUCCESS`/`FAILURE`를 반환해 정상적으로 `onSuccess`/`onFailure`로 흘러간다. 이 덕분에 `WorkflowExecutor`의 스텝 디스패치 루프가 모든 StepType에 대해 완전히 균일해진다.

핵심 설계 원칙: **모든 스텝 실행 후 상태를 저장한다.** 승인 스텝만 특별 취급하는 게 아니라, `WorkflowExecutor`를 "매 스텝마다 영속화 → 다음 스텝 판단" 루프로 바꾼다. 이렇게 하면:
- 승인 대기뿐 아니라 서버 재기동/장애 후에도 마지막 완료 스텝부터 재개 가능해진다 (부가 이득).
- 기존 동기 실행(`/execute`)과 비동기 실행(`/submit`+`/status`)이 **같은 영속화 모델 위에서 동작**하게 되어, `AsyncJobService`(Redis Hash)와 새 승인-대기 모델(PostgreSQL)이 서로 다른 저장소로 쪼개져 있던 것도 하나로 합쳐진다.

### 1.3 신규 실행 상태 모델

```java
enum WorkflowExecutionStatus { RUNNING, WAITING_APPROVAL, DONE, FAILED, CANCELLED }

record WorkflowExecution(
    String executionId,
    String workflowId,
    String caller,
    WorkflowExecutionStatus status,
    int currentStepIndex,
    Map<String, Object> variables,   // WorkflowContext 스냅샷
    List<StepHistoryEntry> history,  // 스텝별 StepOutput 기록 (감사/디버깅용)
    String resultText,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {}

record StepHistoryEntry(String stepId, StepType type, boolean success, String outputSummary, Instant executedAt) {}
```

기존 `AsyncJobService`는 폐기하고 `runtime.workflow.execution.WorkflowExecutionService` + `WorkflowExecutionStore`(JdbcTemplate 기반)로 대체한다. `/execute`(동기)도 내부적으로는 같은 `WorkflowExecutionService.runToCompletionOrPause()`를 호출하되, `WAITING_APPROVAL`이 되면 즉시 그 상태를 응답으로 반환(폴링 안내 메시지 포함)한다.

### 1.4 PostgreSQL 스키마 (신규, `dstone-ai-engine/src/main/resources/schema/01-create-table-postgresql-ai-workflow-execution.sql`)

`dstone-batch`의 "스키마는 수동 실행" 관례를 그대로 따른다 (`initialize-schema: NEVER` 패턴).

테이블명·컬럼명 모두 대문자 스네이크케이스로 통일한다 — `dstone-batch`/`dstone-batchadmin`의 `BATCH_JOB_INSTANCE`, `TB_ADMIN_USER`, `TB_BATCH_SERVER` 등 리포지토리 전반의 SQL 명명 관례와 맞춘다 (사용자 지시: 테이블명 대문자화, 컬럼명도 동일 관례로 확장 적용).

```sql
CREATE TABLE AI_WORKFLOW_EXECUTION (
    EXECUTION_ID        VARCHAR(36) PRIMARY KEY,
    WORKFLOW_ID          VARCHAR(100) NOT NULL,
    CALLER               VARCHAR(100),
    STATUS               VARCHAR(20) NOT NULL,      -- RUNNING/WAITING_APPROVAL/DONE/FAILED/CANCELLED
    CURRENT_STEP_INDEX   INT NOT NULL DEFAULT 0,
    VARIABLES_JSON       JSONB NOT NULL DEFAULT '{}',
    RESULT_TEXT          TEXT,
    ERROR_MESSAGE        TEXT,
    CREATED_AT           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UPDATED_AT           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IDX_AI_WORKFLOW_EXECUTION_STATUS ON AI_WORKFLOW_EXECUTION(STATUS);

CREATE TABLE AI_WORKFLOW_EXECUTION_STEP_HISTORY (
    ID                BIGSERIAL PRIMARY KEY,
    EXECUTION_ID      VARCHAR(36) NOT NULL REFERENCES AI_WORKFLOW_EXECUTION(EXECUTION_ID),
    STEP_ID           VARCHAR(100) NOT NULL,
    STEP_TYPE         VARCHAR(20) NOT NULL,
    STEP_REF          VARCHAR(200),      -- 사용된 에이전트/툴/MCP 이름 (StepDefinition.ref) — §1.6 로깅과 동일 정보를 조회 API(§10)에서도 볼 수 있도록 컬럼화
    SUCCESS           BOOLEAN NOT NULL,
    DURATION_MS        BIGINT,            -- 스텝 처리 소요시간
    OUTPUT_SUMMARY     TEXT,
    FAILURE_REASON     TEXT,              -- 실패/에러 사유 (StepOutput.failureReason 또는 예외 메시지)
    EXECUTED_AT        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IDX_AI_WORKFLOW_EXECUTION_STEP_HISTORY_EXEC ON AI_WORKFLOW_EXECUTION_STEP_HISTORY(EXECUTION_ID);
```

MyBatis는 이 모듈에 없으므로(현재도 없음) 새 sqlmap을 도입하지 않고, `JdbcTemplate` + 간단한 RowMapper로 `WorkflowExecutionStore`를 짠다 — 모듈의 기존 경량 스타일(라이브러리 최소 의존)과 일치.

### 1.5 Step 종료 경우의 수 (StepStatus)

Step은 두 계층에서 "끝"을 판단한다 — (a) 개별 스텝 러너의 실행 결과, (b) 그 결과를 받아 `WorkflowExecutor`가 내리는 다음 동작 결정.

**(a) 스텝 러너의 실행 결과 (`StepOutput.result`, `StepResult` enum 3가지 + 예외 1가지 = 4가지)**

| 결과 | 의미 | 발생 조건 |
|---|---|---|
| `StepResult.SUCCESS` | 정상 성공 | AGENT 정상 응답, TOOL 결과에 `"실패:"` 없음, SUPERVISOR `Verdict.pass()==true`, APPROVAL 결정이 승인 |
| `StepResult.FAILURE` | 비즈니스 로직상 실패 | `failureReason` 有 — TOOL `"실패:"` 접두어, SUPERVISOR `Verdict.pass()==false`, APPROVAL 결정이 반려 |
| `StepResult.PENDING` | 아직 끝나지 않음 | `ApprovalStepRunner`가 아직 결정이 없는 걸 확인하고 반환 (§2) — `StepType.APPROVAL`만 반환 가능 |
| ERROR (enum 아님, 예외) | 처리 불가능한 예외 | LLM API 타임아웃/오류, DB 연결 끊김, MCP 서버 무응답 등 — 러너가 던진 예외를 `WorkflowExecutor`가 캐치. `StepOutput`으로 정상 반환되지 않는다 |

**(b) `WorkflowExecutor`의 다음 동작 결정 (`StepStatus`, 기존 4종 + 신규 2종)**

기존 `StepStatus`(`NEXT_STEP`/`LOOP`/`SUCCESS`/`FAIL`)에 `ERROR`, `WAITING_APPROVAL`을 추가해 6가지로 확장한다.

| StepStatus | 트리거 | 워크플로우 동작 |
|---|---|---|
| `NEXT_STEP` | SUCCESS/FAILURE + `onSuccess`/`onFailure`가 다른 스텝 id를 가리킴 | 그 스텝으로 진행, 실행 상태 영속화 |
| `LOOP` | `onSuccess`/`onFailure`가 이전/현재 스텝을 다시 가리킴 (validate↔fix) | `maxIterations` 카운트 증가 후 재실행, 초과 시 강제 `FAIL` |
| `SUCCESS` | `onSuccess`/`onFailure`가 `"SUCCESS"` sentinel | `WorkflowExecution.status=DONE`, 워크플로우 정상 종료 |
| `FAIL` | `onSuccess`/`onFailure`가 `"FAIL"` sentinel, 또는 `LOOP` 초과 | `WorkflowExecution.status=FAILED`, 워크플로우 종료 |
| `ERROR` (신규) | 스텝 러너가 예외를 던짐 | `onFailure` 분기를 **무시**하고 즉시 `WorkflowExecution.status=FAILED` + `errorMessage` 기록 |
| `WAITING_APPROVAL` (신규) | 러너가 `StepOutput.result()==PENDING` 반환 (`ApprovalStepRunner`만 해당) | `WorkflowExecution.status=WAITING_APPROVAL`로 저장, 실행 중단. `decision` API 호출 시 같은 스텝을 재실행 → 이번엔 `SUCCESS`/`FAILURE`로 `NEXT_STEP` 재평가 |

핵심 설계 판단: **ERROR(시스템 예외)와 FAILURE(비즈니스 실패)를 분리**한다. 기존 코드는 TOOL의 `"실패:"` 접두어 하나로 실패를 판정했는데, 이 규칙을 예외 상황까지 확장하면 "DB가 죽어서 응답을 못 받은 것"과 "SQL 문법이 틀려서 실패한 것"이 같은 `onFailure` 분기(예: fix-loop)로 흘러가 버려 무한 재시도/오탐이 날 수 있다. `ERROR`는 `onFailure`를 거치지 않고 바로 워크플로우를 죽인다.

### 1.6 Step IN/OUT 로깅

목표: 로그 한 줄(또는 시작/종료 두 줄)만 보고 **"이 실행에서, 이 스텝이, 무슨 유형으로, 어떤 Agent/Tool을 불러서, 어떻게 끝났는지"**가 바로 보여야 한다.

**새 클래스를 만들지 않고 기존 `common.config.ConfigCallLog`(AOP 메서드 entry/exit 로그를 담당하는 그 Aspect)를 확장**한다. 이게 가능한 이유: 모든 StepRunner가 §2에서 정의하는 동일한 인터페이스 한 메서드(`StepOutput run(WorkflowExecution execution, StepDefinition definition, StepInput input)`)로 통일되므로, `ConfigCallLog`에 이 시그니처 하나만 겨냥하는 전용 `@Around` advice를 추가하면 인자/반환값에서 필요한 필드를 전부 그대로 꺼낼 수 있다. 기존의 범용 entry/exit 로깅 advice와는 별개의 advice 메서드로 같은 클래스 안에 공존시킨다(포인트컷이 다르므로 겹치지 않음).

- **위치**: `common.config.ConfigCallLog`에 `@Around("execution(* net.dstone.ai.runtime.step.StepRunner.run(..))")` advice 1개 추가. 전용 로거(`net.dstone.ai.workflow.audit`)를 이 advice 안에서만 쓴다.
- **필드 추출**: 첫 인자 `WorkflowExecution`에서 `executionId`/`workflowId`, 둘째 인자 `StepDefinition`에서 `stepId`/`stepType`/`ref`, 셋째 인자 `StepInput`에서 `renderedText`(=input) — 전부 리플렉션 없이 타입 그대로 캐스팅해서 꺼낸다. 반환값 `StepOutput`에서 `result`/`primaryText`/`failureReason`을 꺼내고, advice가 직접 `System.nanoTime()` 전후 차이로 `durationMs`를 잰다.
- **`transition`은 이 로그에 없다** — `NEXT_STEP`/`LOOP`/`SUCCESS`/`FAIL` 같은 워크플로우 전이는 `WorkflowExecutor`가 join point가 끝난 **뒤에** 결정하는 것이라 advice가 알 수 없다. 이 로그는 "스텝 자체의 IN/OUT"만 책임지고, 흐름 제어는 로그 순서(같은 `executionId`로 grep했을 때 stepId가 나열되는 순서)와 §10의 DB 조회로 충분히 재구성된다.
- **포맷**: 사람이 grep하기 쉬운 logfmt 스타일(`key=value`) 한 줄. 필드 순서 고정.

```
# 스텝 시작
WF_STEP phase=START executionId=8f3a2b.. workflowId=oracle-to-postgresql stepId=convert stepType=AGENT ref=sql-conversion-agent input="CREATE TABLE ... (500자 초과 시 자름)"

# 스텝 종료 - 성공
WF_STEP phase=END   executionId=8f3a2b.. workflowId=oracle-to-postgresql stepId=convert stepType=AGENT ref=sql-conversion-agent result=SUCCESS durationMs=842 output="COALESCE(...) LIMIT 10"

# 스텝 종료 - 비즈니스 실패
WF_STEP phase=END   executionId=8f3a2b.. workflowId=oracle-to-postgresql stepId=validate stepType=TOOL ref=sql-syntax-validator result=FAILURE durationMs=12 failureReason="구문 오류: ..."

# 스텝 종료 - 시스템 예외 (advice의 catch 블록에서 기록)
WF_STEP phase=END   executionId=8f3a2b.. workflowId=oracle-to-postgresql stepId=convert stepType=AGENT ref=sql-conversion-agent result=ERROR durationMs=5002 error="Anthropic API timeout"

# 스텝 종료 - 승인 대기
WF_STEP phase=END   executionId=8f3a2b.. workflowId=sample-approval-flow stepId=approve-step stepType=APPROVAL ref=team-lead result=PENDING durationMs=3
```

- **긴 값 처리**: `input`/`output`은 `ExternalProcessRunner`가 이미 쓰는 것과 동일한 방식으로 길이 제한(기본 500자, `Constants`에 상수화) 후 말줄임 처리 — 로그가 한 줄을 넘어가지 않게.
- **로그 라우팅**: `net.dstone.ai.workflow.audit` 로거를 `conf/log4j2.xml`에서 기존에 이미 존재하는 `LOGS/dstone-ai-engine/execution/execution.log` 어펜더로 보낸다 (이미 이 경로로 로테이션/백업까지 되고 있으므로 새 파일을 만들 필요가 없다).
- **DB 연동**: 같은 정보(`ref`, `durationMs`, `failureReason`)를 같은 advice가 `WorkflowExecutionStore`를 통해 `AI_WORKFLOW_EXECUTION_STEP_HISTORY`(§1.4)에도 적재할지, 아니면 `WorkflowExecutionService`가 스텝 실행 후 별도로 적재할지는 Phase 2 구현 시 결정 — 어느 쪽이든 로그와 DB가 같은 값(같은 join point 시점)에서 나오므로 서로 어긋나지 않는다.

---

## 2. Step Input/Output 계약 명확화

현재는 `WorkflowContext`(느슨한 `Map<String,Object>`)와 각 `*StepRunner`가 알아서 문자열을 다듬는 방식이라 "이 스텝이 뭘 받고 뭘 돌려주는지"가 코드를 읽어야만 보인다. 아래처럼 타입을 명시하고, **모든 StepRunner가 구현하는 공통 인터페이스 하나**로 통일한다 (`runtime.status`/`runtime.step` 패키지, §4의 패키지 구조 참고. 기존 `StepOutcome`을 대체/흡수):

```java
// runtime.status.StepInput — 각 StepRunner에 전달되는 입력
record StepInput(
    String renderedText,           // inputTemplate 렌더링 결과 (TOOL/AGENT 공통 입력 원문)
    Map<String, Object> variables  // 워크플로우 전역 변수(읽기 전용 뷰)
) {}

// runtime.status.StepResult — Step 종료 경우의 수(§1.5-a) 중 StepOutput으로 표현되는 3가지
enum StepResult { SUCCESS, FAILURE, PENDING }   // ERROR는 예외로 표현되므로 이 enum에 없음(§1.5)

// runtime.status.StepOutput — 각 StepRunner가 반환하는 출력 (기존 StepOutcome 대체)
record StepOutput(
    StepResult result,
    String primaryText,         // 다음 스텝의 {previous} 로 이어지는 주 결과
    Map<String, Object> data,   // 워크플로우 변수에 병합될 구조화 결과 (선택)
    String failureReason        // FAILURE일 때 사유, 그 외 null
) {}

// runtime.step.StepRunner — 4개 StepType 러너가 전부 구현하는 공통 인터페이스
interface StepRunner {
    StepOutput run(WorkflowExecution execution, StepDefinition definition, StepInput input);
}
```

`AgentStepRunner`/`ToolStepRunner`/`ApprovalStepRunner`가 전부 이 인터페이스 하나를 구현한다. `WorkflowExecutor`는 `StepType`으로 러너를 찾아 항상 똑같이 `runner.run(execution, definition, input)`을 호출하고, `StepOutput.result`만 보고 분기한다 — `StepType.APPROVAL`을 미리 갈라내는 특수 분기가 없다. `StepOutput.data`는 `WorkflowContext.variables`에 병합되고, `primaryText`는 다음 스텝의 `{previous}` 토큰에 바인딩된다. 이렇게 하면 YAML의 `inputTemplate` 문서화(§4)와 코드 계약이 1:1로 맞아떨어지고, 이 하나의 메서드 시그니처를 §1.6의 `ConfigCallLog` advice가 그대로 겨냥할 수 있다.

**`ApprovalStepRunner`의 PENDING/재개 동작** (§1.2에서 이미 설명): `WorkflowContext.variables.approvals.{stepId}`에 결정이 없으면 `StepOutput(PENDING, ...)`을 반환한다. `decision` API가 호출되면 그 결정을 `variables`에 기록한 뒤 **같은 스텝을 다시 `run()`**하고, 이번엔 결정이 있으니 `SUCCESS`(승인)/`FAILURE`(반려)를 반환한다 — "재개"는 워크플로우 엔진 입장에서 특별한 코드 경로가 아니라 그냥 같은 스텝의 재실행이다.

---

## 3. 패키지 구조 (증분 변경)

기존 구조를 대부분 유지하고, 신규/이름변경만 굵게 표시:

```
net.dstone.ai
├── DstoneAiEngineApplication
├── api
│   ├── controller
│   │   ├── ChatController
│   │   ├── RagController                   (ingest/delete만 유지, search 액션은 삭제 — §5)
│   │   ├── WorkflowController              (동기 /execute 는 유지, 비동기 submit/status는 execution 기반으로 내부 교체)
│   │   └── WorkflowExecutionController     **신규** — 진행상태/내역 조회 + 승인 처리 API, 상세는 §10
│   ├── dto
│   └── service
│       └── (AsyncJobService 삭제 → runtime.workflow.execution.WorkflowExecutionService 로 대체)
├── common
│   ├── config        (Config, ConfigChatClient, ConfigTool, ConfigRedis)
│   │   ├── ConfigCallLog **확장** — 기존 범용 entry/exit advice에 §1.6의 `StepRunner.run(..)` 전용 advice 추가 (신규 클래스 없이 여기서 처리)
│   │   └── ConfigMcp **신규** — MCP 클라이언트 ToolCallbackProvider 구성
│   ├── consts
│   ├── definition     (WorkflowDefinition, StepDefinition, StepType(RAG 폐지+APPROVAL 추가), AgentDefinition(promptName 필드 삭제 → prompt 필드로 대체, §4))
│   │   └── McpServerDefinition **신규** — mcp yml 바인딩 레코드
│   ├── loader         (YamlDefinitionLoader — mcp/*.yml 패턴 추가)
│   ├── registry       (WorkflowRegistry, AgentRegistry)
│   │   └── McpServerRegistry **신규**
│   ├── security
│   ├── session
│   ├── annotation
│   └── exec           (ExternalProcessRunner)
│   └── ~~prompt~~ **삭제** — PromptTemplateRegistry/PromptProperties 폐지, §4 참고
├── rag
│   ├── RagIngestService   (Tika/JSONL → 청킹 → tenant 태깅 → VectorStore.add/delete, §5)
│   └── RagRetrievalChain  (Advisor 빌더 + 순수 검색 메서드, §5)
├── runtime
│   ├── workflow
│   │   ├── WorkflowContext, WorkflowExecutor(재작성 — StepRunner 균일 호출 루프, §2)
│   │   └── execution **신규 패키지**
│   │       ├── WorkflowExecution, StepHistoryEntry
│   │       ├── WorkflowExecutionStore   (JdbcTemplate 영속화)
│   │       └── WorkflowExecutionService (동기/비동기/승인대기 실행의 단일 진입점)
│   ├── agent
│   │   └── AgentExecutor
│   ├── step
│   │   └── StepRunner(공통 인터페이스, 신규, §2), AgentStepRunner, ToolStepRunner, ApprovalStepRunner **신규** — RagStepRunner는 삭제
│   ├── tool
│   │   └── ToolExecutor (MCP 툴도 동일 경로로 호출되므로 변경 없음)
│   └── status **신규 패키지** — 상태/결과를 나타내는 순수 타입만 모음
│       └── WorkflowExecutionStatus, StepStatus, StepInput, StepOutput, StepResult **(신규, StepOutcome 대체)**, Verdict
├── mcp **신규 패키지 (클라이언트 전용)**
│   └── McpToolProvider — resources/mcp/*.yml 로 정의된 서버들에 접속해 ToolCallbackProvider를 만들고 ConfigTool에 합류
└── tools
    ├── ExternalProcessTool **신규 추상클래스** — ShellExecTool/PythonExecTool 공통 로직(화이트리스트 조회 + ExternalProcessRunner 호출 + 성공/실패 접두어 판정) 추출
    ├── sample.DateTimeTool        (DateTimeTools → 단수형으로 리네임, 명명 통일)
    ├── sql.SqlSyntaxTool          (SqlSyntaxTools → 단수형으로 리네임)
    ├── rag.RagSearchTool          **신규** — StepType.RAG 폐지를 대체. RagRetrievalChain을 감싸 LLM 없이 검색 결과만 필요할 때 TOOL 스텝에서 호출
    ├── shell.ShellExecTool        (ExternalProcessTool 상속)
    ├── python.PythonExecTool      (ExternalProcessTool 상속)
    └── http.HttpCallTool
```

**명명 규칙 통일**: 모든 `@AiTool` 클래스는 단수형 `...Tool` (복수형 `...Tools` 금지 — 현재 `DateTimeTools`/`SqlSyntaxTools`만 예외였음). 모든 정의 로딩 3종 세트(Workflow/Agent/McpServer)는 `definition`/`loader`/`registry`/`config` 4계층을 동일하게 반복해 구조적 일관성을 유지한다. `runtime` 하위는 역할별로 `workflow`(엔진+영속화) / `agent` / `step` / `tool` / `status`(순수 타입) 5개 패키지로 나눠, "무슨 역할의 코드를 찾는지"만 알면 바로 위치를 알 수 있게 한다.

---

## 4. 리소스 구조

```
resources/
├── workflows/*.yml      (기존 유지, StepType에서 RAG 제거·APPROVAL 추가)
├── agents/*.yml         (프롬프트를 파일 안에 인라인, §4.0 — prompts/ 디렉토리 폐지)
├── mcp/*.yml            **신규** — MCP 서버 접속 정의, 파일명 무관·내부 id가 식별자
└── schema/*.sql         **신규** — §1.4의 AI_WORKFLOW_EXECUTION DDL (수동 실행, dstone-batch 관례와 동일)
```

### 4.0 프롬프트를 Agent YAML에 인라인

`resources/prompts/{name}/{version}.st` + `dstone.ai.prompt.default-version`/`.versions.*` 설정 + `common.prompt.PromptTemplateRegistry`/`PromptProperties` 전부 **폐지**한다. `agentId → promptName → version → .st 파일` 4단 간접참조가 "이 에이전트가 정확히 무슨 프롬프트로 동작하는지" 확인하려면 두 디렉토리를 오가야 하게 만들었는데, 지금 설정엔 실질적인 버전 분기(A/B 등)가 전혀 쓰이고 있지 않다 — 실제로 쓰는 값은 항상 `v1` 하나뿐이었다. 프롬프트를 통째로 `agents/*.yml`에 넣으면 에이전트 하나의 정의(설명/프롬프트/RAG여부/툴여부)가 파일 하나로 완결된다. "이전 프롬프트로 되돌리고 싶다"는 요구는 이 YAML 파일의 git 이력이 그대로 대신한다.

```yaml
agents:
  - name: sql-conversion-agent
    description: Oracle SQL을 PostgreSQL로 변환한다
    prompt: |
      당신은 Oracle SQL을 PostgreSQL로 변환하는 전문가입니다.
      NVL(...)은 COALESCE(...)로, ROWNUM 페이징은 LIMIT으로 변환하십시오.
      ...
    toolsEnabled: false
    ragEnabled: true
```

`common.definition.AgentDefinition`은 `promptName` 필드를 `prompt`(원문 문자열)로 바꾼다. 변수 치환({caller}, {today} 등)이 필요하면 `runtime.agent.AgentExecutor`가 Spring AI의 `PromptTemplate`을 그 자리에서 직접 써서 렌더링한다(`new PromptTemplate(definition.prompt()).render(variables)`) — 커스텀 레지스트리 클래스 없이도 템플릿 치환 기능 자체는 그대로 유지된다.

### 4.1 APPROVAL 스텝 YAML 예시 (`workflows/*.yml`)

```yaml
workflow:
  id: sample-approval-flow
  steps:
    - id: step2
      type: AGENT
      ref: draft-agent
      onSuccess: approve-step
    - id: approve-step
      type: APPROVAL
      approverRole: "team-lead"      # 문서/감사용 메타데이터, 서버가 강제하진 않음(누가 호출하든 decision API로 승인 가능 — 실제 권한 검증은 dstone-ai-engine 밖의 API Gateway/캐스팅 시스템 책임으로 명시)
      onSuccess: step3                 # 승인(approved=true) 시 다음 스텝
      onFailure: FAIL                  # 반려(approved=false) 시 처리
    - id: step3
      type: TOOL
      ref: someTool
```

### 4.2 MCP 서버 정의 YAML 예시 (`mcp/filesystem.yml`)

```yaml
mcpServer:
  id: filesystem
  transport: STDIO            # STDIO | SSE
  command: "npx"
  args: ["-y", "@modelcontextprotocol/server-filesystem", "/data"]
  allowedTools: ["read_file", "list_directory"]   # 비워두면 전체 허용 — 기존 tool whitelist와 동일한 "빈 값 fail-open은 위험하니 명시적으로 채우는 걸 권장" 관례
  allowedCallers: []           # AgentDefinition.allowedCallers 와 동일한 패턴
```

SSE 예시(`mcp/internal-search.yml`)는 `transport: SSE`, `url: http://...` 형태로 동일 스키마를 공유.

---

## 5. RAG 체인 재설계

현재 `RagService`는 Ingest(Tika→TokenTextSplitter→pgvector)와 Retrieval(단순 유사도 검색 + `QuestionAnswerAdvisor`)이 한 클래스에 섞여 있다. "체인"이라는 요구에 맞춰 Spring AI 2.x의 `RetrievalAugmentationAdvisor`(pluggable QueryTransformer/DocumentRetriever/QueryExpander) 기반으로 명시적 파이프라인화한다.

**Ingest 체인** (변경 없음, 그대로 유지):
```
Source(File/JSONL) → DocumentReader(Tika | JsonlReader) → TokenTextSplitter(chunk-size) → tenant 메타데이터 태깅 → VectorStore.add()
```

**Retrieval 체인** (신규, `rag.chain` 서브패키지로 분리):
```
사용자 질의
  → QueryTransformer (선택, 대화맥락 압축 — CompressionQueryTransformer)
  → VectorStoreDocumentRetriever (topK, similarityThreshold, tenant 필터)
  → DocumentPostProcessor (선택, 중복 제거 — 재랭킹은 1단계 범위 밖, 확장 지점으로만 남김)
  → RetrievalAugmentationAdvisor 가 프롬프트에 컨텍스트 주입
```

`RagService`를 `rag.RagIngestService` + `rag.RagRetrievalChain`(Advisor 빌더 + 순수 검색 메서드) 두 개로 분리한다. `AgentExecutor.buildSpec()`은 `ragEnabled=true`일 때 `RagRetrievalChain.buildAdvisor(caller)`만 호출하면 되므로 결합도가 낮아진다.

**Step 모델과의 연결**: `StepType.RAG`는 폐지한다(§1.1). AGENT 스텝은 `ragEnabled: true`일 때 이 체인을 Advisor로 자동 적용받고, LLM 없이 검색 결과만 필요한 워크플로우는 `tools.rag.RagSearchTool`(신규, `RagRetrievalChain.search()`를 그대로 호출하는 얇은 `@AiTool` 래퍼)을 일반 TOOL 스텝으로 호출한다. 검색 로직 자체(tenant 필터·topK·threshold)는 항상 `RagRetrievalChain` 한 곳에만 존재한다.

**`RagController`는 남지만 범위를 줄인다.** 검색 경로가 Advisor(§1.1)와 `RagSearchTool`(위 문단) 두 곳으로 이미 커버되므로, `RagController`의 `/api/ai/rag/search` 액션은 **삭제**한다 — 지금도 어디서도 호출되지 않는 중복 경로였다 (확인: `dstone-boot`의 `DocumentController`는 upload/list/delete만 쓰고 search는 쓰지 않는다). 반면 `/api/ai/rag/documents`의 **ingest/delete는 그대로 유지**한다 — `dstone-boot`의 `net.dstone.boot.ai.controller.DocumentController` → `DocumentService.uploadDocument()`/`.deleteDocument()`가 이 두 엔드포인트를 실제로 호출하고 있어(§11), 없애면 dstone-boot의 문서 업로드 화면이 깨진다. 즉 `RagController`는 "문서를 벡터스토어에 넣고 빼는" 관리 작업 전용으로 남고, "검색"은 전부 Advisor/Tool 경로로 일원화된다.

---

## 6. Tools 골격 (내부/외부 프로그램)

- **내부 Java 툴**: `@AiTool` + `@Tool` 메서드. `tools.sample.DateTimeTool`, `tools.sql.SqlSyntaxTool`, `tools.rag.RagSearchTool` 모두 명명 단수형 통일, 화이트리스트 없이 항상 활성(조회성/무해 동작).
- **외부 프로그램 공통 골격**: 신규 추상 클래스

```java
// tools.ExternalProcessTool — Shell/Python 공통 로직
abstract class ExternalProcessTool {
    protected abstract Map<String, String> allowedCommands(); // name -> 실제 경로/스크립트 (설정에서 주입)
    protected String runWhitelisted(String name, List<String> args) {
        String path = allowedCommands().get(name);
        if (path == null) return "실패: 허용되지 않은 명령입니다 - " + name;
        return ExternalProcessRunner.run(path, args); // 기존 그대로 재사용
    }
}
```

`ShellExecTool`/`PythonExecTool`은 이 클래스를 상속해 `allowedCommands()`만 각자의 설정 프로퍼티(`dstone.ai.tool.shell.allowed-commands` / `.python.allowed-scripts`)에서 읽어오도록 구현 — 중복 제거, "빈 화이트리스트 = 비활성"이라는 fail-closed 설계는 그대로 유지.
- **HttpCallTool**: 프로세스가 아니라 HTTP 호출이므로 상속 대상 아님. 대신 동일한 "빈 화이트리스트=비활성" 관례와 성공/실패 접두어 관례(`Constants`에 정의된 통과/실패 프리픽스)를 그대로 따르도록 코드 리뷰 체크리스트에 명시.

---

## 7. MCP 클라이언트 설계 (이번 범위)

- `pom.xml`에 `spring-ai-starter-mcp-client` 추가 (Spring AI 2.0.1과 호환 버전 확인 필요 — 실제 착수 시 `mvn dependency:tree`로 검증).
- `common.definition.McpServerDefinition` — `mcp/*.yml`을 바인딩하는 레코드 (§4.2 스키마).
- `common.loader.YamlDefinitionLoader`에 `classpath*:mcp/*.yml` 패턴 추가.
- `common.registry.McpServerRegistry` — id → 정의 맵, `allowedCallers` 검사 (Agent/Workflow 레지스트리와 동일 패턴).
- `common.config.ConfigMcp` — 등록된 MCP 서버마다 클라이언트(STDIO/SSE)를 만들고 `McpToolCallbackProvider`로 감싸, 기존 `ConfigTool`이 만드는 `ToolCallbackProvider`와 **병합**한다. 결과적으로 AGENT 스텝의 LLM 툴 호출 루프와 TOOL 스텝의 `ToolExecutor.findByName()` 양쪽 모두 로컬 `@AiTool`과 MCP 툴을 구분 없이 쓸 수 있다 — **신규 StepType 불필요**, 기존 `TOOL`/tool-calling 경로 그대로 확장.
- 실패 격리: 특정 MCP 서버 접속 실패가 앱 기동을 막지 않도록 `ConfigMcp`는 서버별로 try-catch 후 로그만 남기고 넘어간다 (한 서버 장애가 전체 툴 목록을 무너뜨리지 않게).
- MCP 서버(외부 노출)는 이번 계획에서 제외 — §9 "향후 과제"에만 기록.

---

## 8. 유지/폐기 정리

| 대상 | 처리 |
|---|---|
| `WorkflowExecutor`, `AsyncJobService` | **재작성** — §1.3/§2의 영속화 루프 + 균일 StepRunner 호출로 교체, AsyncJobService 삭제, `runtime.workflow`로 이동 |
| `StepOutcome` | **대체** — `runtime.status.StepInput`/`StepOutput`/`StepResult`로 교체 (§2) |
| `StepType.RAG` / `RagStepRunner` | **폐지** — Advisor(`ragEnabled`) 경로와 신규 `RagSearchTool`(TOOL 스텝)로 대체 (§1.1, §5) |
| `AgentStepRunner`/`ToolStepRunner` | **`StepRunner` 인터페이스 구현으로 조정** (§2), 내부 로직 대부분 유지, `runtime.step`으로 이동 |
| `RagService` | **분리** — `RagIngestService`+`RagRetrievalChain`으로 리팩터 (§5), 로직 재사용 |
| `RagController.search()` | **삭제** — 아무도 호출하지 않는 중복 경로, Advisor/`RagSearchTool`로 흡수 (§5). ingest/delete는 dstone-boot 의존으로 유지 |
| `common.prompt`(`PromptTemplateRegistry`/`PromptProperties`), `resources/prompts/*.st` | **폐지** — `AgentDefinition.prompt` 필드에 인라인 (§4.0) |
| `ConfigTool`, `ToolExecutor`, `AgentExecutor` | **유지** — MCP 툴도 같은 경로로 흡수되므로 변경 최소화 |
| `DateTimeTools`/`SqlSyntaxTools` | **리네임만** (`DateTimeTool`/`SqlSyntaxTool`) |
| `ShellExecTool`/`PythonExecTool` | **공통 부모 클래스로 추출**, 동작은 동일 |
| `WorkflowController`/`ChatController` | **대부분 유지**, WorkflowController의 submit/status만 새 execution 모델 호출로 교체 |
| `ConfigCallLog` | **확장** — §1.6의 `StepRunner.run(..)` 전용 `@Around` advice 추가 (신규 `WorkflowAuditLogger` 클래스는 만들지 않음) |
| `common.session`, `common.security`, `ConfigChatClient` | **변경 없음** |

## 9. 진행 순서 (단계별)

1. **Phase 1** — `runtime` 패키지를 `workflow`/`agent`/`step`/`tool`/`status`로 재편(§3) + `StepRunner` 공통 인터페이스와 `StepInput`/`StepOutput`/`StepResult` 도입(§2) + `StepType.RAG`/`RagStepRunner` 제거 + Tools 리네임/공통클래스 추출 + `RagSearchTool` 추가 + 프롬프트를 `agents/*.yml`에 인라인하고 `common.prompt`/`resources/prompts` 삭제(§4.0) (위험 낮음, 회귀 테스트로 검증 쉬움)
2. **Phase 2** — PostgreSQL 스키마 추가 + `runtime.workflow.execution` 패키지(WorkflowExecutionStore/Service) + `WorkflowExecutor` 재작성 + `APPROVAL` StepType(`ApprovalStepRunner`) + `ConfigCallLog`에 Step IN/OUT advice 추가(§1.6) + `WorkflowExecutionController`(§10, 조회+승인 API) (핵심/가장 큰 변경)
3. **Phase 3** — RAG 체인 분리 (`RetrievalAugmentationAdvisor` 전환)
4. **Phase 4** — MCP 클라이언트 (`ConfigMcp`, `McpServerDefinition/Registry`, `resources/mcp/*.yml`)
5. **Phase 5** — `dstone-boot`의 `net.dstone.boot.ai.*` 테스트 화면(§11)을 새 API 계약(`executionId`, `/executions` 조회, `/decision`)에 맞춰 갱신 — Phase 2가 끝나야 붙일 수 있으므로 그 뒤에 진행
6. **Phase 6 (향후 과제, 이번 범위 아님)** — MCP 서버로 자체 노출, 승인 알림(Slack/메일) 연동, 재랭킹 고도화

각 Phase 종료 시 `docs/09.dstone-ai-engine.md`를 갱신한다 (CLAUDE.md 지침: "리소스 변경 시 문서도 갱신"). 특히 §5 실행모델, §6 Agent와 Tool, §7 Session/RAG/Security, §10 API 레퍼런스(본 계획서의 §10 조회 API 반영), §11 설정 레퍼런스(로깅 관련 log4j2 설정 추가), §14 변경이력 섹션이 영향받는다.

## 10. Workflow 실행 상태/내역 조회 API

`WorkflowExecutionController`(§3)가 `AI_WORKFLOW_EXECUTION`/`AI_WORKFLOW_EXECUTION_STEP_HISTORY`(§1.4)를 `WorkflowExecutionStore`를 통해 조회해 제공한다. 승인 처리(§1.2의 decision)도 같은 컨트롤러에 둔다 — "실행을 보고 판단해서 조작한다"는 하나의 흐름이기 때문.

| Method/Path | 설명 | 응답 |
|---|---|---|
| `GET /api/ai/workflow/executions?status=&workflowId=&caller=&page=&size=` | 실행 목록 조회 (필터: 상태/워크플로우id/호출자, 페이징) | 목록 각 항목: `executionId, workflowId, caller, status, currentStepIndex, createdAt, updatedAt` (가벼운 요약, history 미포함) |
| `GET /api/ai/workflow/executions/{executionId}` | 실행 1건 상세 + 전체 스텝 히스토리 | `executionId, workflowId, caller, status, currentStepIndex, variables, resultText, errorMessage, createdAt, updatedAt, history: [{stepId, stepType, ref, success, durationMs, outputSummary, failureReason, executedAt}, ...]` |
| `POST /api/ai/workflow/executions/{executionId}/decision` | `WAITING_APPROVAL` 상태의 실행을 승인/반려 | 요청 `{approved, approver, comment}` → 응답은 재개된 실행의 최신 상태 (동기 재개 시 최종 상태까지, 비동기면 `RUNNING`) |

- `status` 필터는 특히 `WAITING_APPROVAL`로 필터링해 "지금 승인 기다리는 실행 목록" 화면을 그대로 만들 수 있게 하는 것이 목적이다.
- `history` 배열의 각 필드는 §1.6에서 로그로도 남기는 것과 동일한 값(`ref`, `durationMs`, `failureReason`)이라 로그 파일과 API 응답이 항상 일치한다.
- 기존 `WorkflowController`의 `GET /status/{jobId}`(Redis 기반, §8에서 폐기 대상)는 이 API로 흡수되므로 별도 엔드포인트를 유지하지 않는다 — 다만 기존 클라이언트 호환이 필요하면 `jobId==executionId`로 간주해 얇은 리다이렉트만 남기는 것도 가능 (필요 시에만).

---

## 11. dstone-boot 테스트 화면 연동

`dstone-boot`에 이미 `net.dstone.boot.ai.*`(ChatController/DocumentController/SqlConvertController/**WorkflowTestController**) + `webapp/ai/assets/js/*.js`로 이루어진, `dstone-ai-engine`을 `WebClient`로 호출해보는 수동 테스트 화면이 있다 (`interface.ai-engine.base-url` 설정 사용). 새로 만들 필요 없이 이 자산을 이번 API 변경에 맞춰 확장한다.

**기존 코드 중 API 계약 변경으로 반드시 손대야 하는 부분** (`WorkflowTestController`/`WorkflowTestService`):
- `submit.do`가 호출하는 `POST /api/ai/workflow/{id}/submit`은 그대로 두되, 응답의 `jobId`를 **`executionId`로 리네임**해 엔진 쪽 §1.3/§10 변경과 용어를 맞춘다 (`WorkflowSubmitResult`, `WorkflowSubmitCallResult` 필드명 변경).
- `status.do`가 부르던 `GET /api/ai/workflow/status/{jobId}`는 **폐기** — 대신 `GET /api/ai/workflow/executions/{executionId}`(§10)를 호출하도록 `WorkflowTestService.status()`를 교체한다. 응답 상태값에 `WAITING_APPROVAL`/`CANCELLED`가 새로 추가되므로 `WorkflowStatusResult`/`WorkflowStatusCallResult`도 그 값을 그대로 통과시키게 확인.

**신규로 추가할 화면/액션** (같은 `WorkflowTestController`/`workflow.js`에 기능 추가, 컨트롤러를 새로 쪼개지 않음 — 이미 "Workflow 테스트"라는 하나의 화면 주제이므로):
- **승인 처리 테스트**: 상태가 `WAITING_APPROVAL`이면 화면에 승인/반려 입력(approver, comment)과 버튼을 노출하고, `POST /decision.do` 신규 액션이 `POST /api/ai/workflow/executions/{executionId}/decision`(§10)을 호출한다.
- **실행 목록/이력 조회 화면**: `GET /list.do`(상태 필터 포함, `?status=WAITING_APPROVAL` 등)가 `GET /api/ai/workflow/executions`를 호출해 테이블로 보여주고, 행 클릭 시 `GET /detail.do?executionId=`가 `GET /api/ai/workflow/executions/{executionId}`를 호출해 `history`(스텝별 유형/ref/성공여부/소요시간/실패사유)까지 그대로 화면에 표시한다 — 이번 요청의 "WorkFlow 진행상태 및 내역 조회"를 사람이 직접 눈으로 확인하는 화면이 된다.

**변경 불필요한 부분**:
- `ChatController`/`DocumentController`(RAG)/`SqlConvertController`는 API 계약이 그대로라 수정할 필요 없다. MCP로 추가되는 툴이나 신규 `RagSearchTool`은 AGENT의 tool-calling 경로에 투명하게 얹히므로, 기존 Chat 테스트 화면과 (RagSearchTool을 쓰는) Workflow 테스트 화면만으로 별도 화면 없이 검증된다.
- `interface.ai-engine.base-url` 설정은 변경 없음.

---

## 검증 방법

- `cd dstone-ai-engine && mvn clean package` 컴파일 통과.
- 기존 수동 검증 시나리오 재실행: `POST /api/ai/chat` (일반/툴콜링), `oracle-to-postgresql` 워크플로우 동기 실행.
- 신규: APPROVAL 스텝이 포함된 샘플 워크플로우로 `/execute` 호출 → 응답이 `WAITING_APPROVAL` 인지 확인 → `POST /executions/{id}/decision` 호출 → 다음 스텝까지 완료되는지 확인.
- 신규: 로컬 MCP 서버(예: `@modelcontextprotocol/server-filesystem`) 하나 붙여 AGENT 스텝에서 해당 툴이 호출되는지 확인.
- RAG: `RagController`로 문서 ingest 후, `RagSearchTool`(TOOL 스텝)과 `ragEnabled` Agent(Advisor 경로) 양쪽에서 같은 문서가 검색되는지 확인. `RagController.search()`는 삭제했으므로 더 이상 존재하지 않는지도 확인.
- 프롬프트 인라인: `agents/*.yml`의 `prompt` 필드만으로 `ChatController`/`AgentExecutor`가 정상 동작하는지, `resources/prompts` 디렉토리와 `dstone.ai.prompt.*` 설정을 지워도 기동에 문제가 없는지 확인.
- 로깅: 워크플로우 1회 실행 후 `execution.log`에서 `WF_STEP` 라인만 grep해, 스텝 수만큼 START/END 쌍이 찍히고 `stepType`/`ref`/`result`/`transition` 값이 실제 실행과 일치하는지 확인. 의도적으로 LLM 호출을 실패시켜(예: 잘못된 API 키) `result=ERROR`가 `result=FAILURE`와 다른 로그로 구분되는지 확인.
- 조회 API: `GET /api/ai/workflow/executions?status=WAITING_APPROVAL`로 대기 중인 실행이 잡히는지, `GET /api/ai/workflow/executions/{id}`의 `history` 배열 내용이 위 로그/DB 값과 일치하는지 확인.
- dstone-boot 연동: `dstone-boot`의 Workflow 테스트 화면에서 APPROVAL 스텝이 있는 워크플로우를 `submit.do`로 실행 → 화면에 승인 대기 표시 → 승인/반려 버튼으로 `decision.do` 호출 → 최종 결과까지 화면에서 눈으로 확인. `list.do`/`detail.do` 화면에서도 같은 실행의 상태·히스토리가 보이는지 확인.
