# dstoneaiengine_관련_개발

> 생성일시: 2026-09-15 10:04

## 질문

dstone-ai-engine 관련 개발을 전면적으로 제설계/재작성 하려고 해. 
기존 내용은 완전히 삭제/배재하고 아래의 <재설계기반계획> 내용을 기반으로 다시 계획을 검토해줘.

<추가 내용>
1. 프레임웍 공통(dstone-common)을 기본 공통모듈로 사용.
2. 람다식은 왠만하면 지양.
3. governance 관련된 내용은 현 작업에서는 배재하고 추후 필요에 따라서 add-on 할 때 너무 많은 리팩토링을 피할 수 있는 수준으로만 설계.
4. 아래의 대화내용을 잘 분석해서 최종적인 구조를 검토.

============================================================
<재설계기반계획>

# 오늘 대화 정리 — Spring AI Agent / Workflow / dstone-ai-engine

> 날짜: 2026-09-15

---

## 1. Spring AI Agent에서 Step/Workflow를 누가 제어하는가?

질문:
> Spring AI로 AI 에이전트 프로그램을 만든다고 할 때 Step 수행 호출 및 결과에 따른 loop back 혹은 Next Step 호출 등의 Workflow는 프로그램으로 제어하는지 혹은 LLM으로 제어하는지?

### 핵심 결론

둘 다 가능하지만, 엔터프라이즈용 AI Agent라면 다음과 같은 **Hybrid 구조**를 추천한다.

- **Workflow의 큰 흐름**: Java 프로그램이 제어
- **Step 내부의 지능적인 판단 / Tool 선택**: LLM이 제어
- **Tool 실행**: Spring AI/Application이 담당
- **보안, 권한, Timeout, Retry, Transaction, Audit**: Java가 통제

즉:

```text
Workflow의 뼈대는 Java가 잡고,
Step 내부의 지능적인 판단은 LLM에게 맡긴다.
```

### Workflow 방식

정해진 순서를 Java가 관리한다.

```text
Analyze
   ↓
Convert
   ↓
Validate
   ↓
Fix
   ↓
Validate
   ↓
Finalize
```

### Agent 방식

LLM이 Tool을 선택하면서 동적으로 진행한다.

```text
LLM
 ↓
analyzeSql()
 ↓
결과 확인
 ↓
convertSql()
 ↓
validateSql()
 ↓
실패
 ↓
fixSql()
 ↓
validateSql()
 ↓
성공
```

Spring AI의 Tool Calling에서는 기본적으로 다음과 같은 Tool loop가 가능하다.

```text
LLM
 ↓
Tool
 ↓
Tool Result
 ↓
LLM
 ↓
Tool
 ↓
Tool Result
 ↓
LLM
 ↓
Final
```

`ToolCallingAdvisor`가 이러한 반복적인 Tool 실행 lifecycle을 담당할 수 있다.

---

## 2. 추천한 Hybrid 구조

```text
┌───────────────────────┐
│      AI Workflow      │
│        Engine         │
│        (Java)         │
└───────────┬───────────┘
            │
     ┌──────┼──────┐
     ↓      ↓      ↓
  Analyze Convert Validate
     │      │      │
     └──────┼──────┘
            ↓
           LLM
```

### Java가 담당

```text
전체 Workflow
Step 순서
Retry
Timeout
Transaction
Security
권한
최대 Loop 횟수
실패 처리
Audit
상태 관리
```

### LLM이 담당

```text
분석
판단
Tool 선택
변환
오류 원인 분석
수정 방법 결정
다음 행동에 대한 판단
```

### 판단 기준

| 상황 | 담당 |
|---|---|
| Step 순서가 항상 동일 | Java |
| 반드시 실행해야 하는 Step | Java |
| 최대 Loop 횟수 | Java |
| Timeout | Java |
| 권한/보안 | Java |
| Transaction | Java |
| Retry 정책 | Java |
| 오류 발생 시 다음 상태 | Java |
| 어떤 Tool을 사용할지 | LLM |
| SQL 분석 | LLM |
| 변환 전략 판단 | LLM |
| 오류 원인 분석 | LLM |
| 수정 방법 판단 | LLM |
| 여러 Tool 중 적절한 Tool 선택 | LLM |

---

# 3. `WorkflowStep → Agent → Tool → Loop/NextStep` 실제 Spring AI 2.0.1 설계

전체 구조:

```text
WorkflowExecutor
      │
      ▼
 WorkflowStep
      │
      ▼
    Agent
      │
      ▼
    LLM
      │
      ├── Tool A
      ├── Tool B
      └── Tool C
      │
      ▼
 Tool Result
      │
      ├── LOOP
      ├── NEXT_STEP
      ├── SUCCESS
      └── FAIL
```

## 패키지 구조

```text
net.dstone.ai
│
├── workflow
│   ├── WorkflowExecutor.java
│   ├── WorkflowContext.java
│   ├── WorkflowStep.java
│   ├── StepResult.java
│   └── StepStatus.java
│
├── agent
│   ├── AiAgent.java
│   └── SqlConversionAgent.java
│
├── tool
│   ├── SqlAnalyzeTool.java
│   ├── SqlConvertTool.java
│   └── SqlValidateTool.java
│
└── api
    └── SqlConversionService.java
```

---

## 3.1 WorkflowContext

```java
public class WorkflowContext {

    private final Map<String, Object> data = new HashMap<>();

    public void put(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }

    public Map<String, Object> data() {
        return data;
    }
}
```

Workflow 전체에서 다음과 같은 데이터를 공유한다.

```text
sql
analysis
convertedSql
validationResult
retryCount
error
```

---

## 3.2 StepStatus

```java
public enum StepStatus {

    NEXT_STEP,
    LOOP,
    SUCCESS,
    FAIL
}
```

의미:

```text
NEXT_STEP → 다음 Step
LOOP      → 현재 Step 또는 지정 Step 재실행
SUCCESS   → Workflow 종료 성공
FAIL      → Workflow 종료 실패
```

---

## 3.3 StepResult

```java
public record StepResult(
        StepStatus status,
        String nextStep,
        String message
) {

    public static StepResult next(String nextStep) {
        return new StepResult(
                StepStatus.NEXT_STEP,
                nextStep,
                null
        );
    }

    public static StepResult loop() {
        return new StepResult(
                StepStatus.LOOP,
                null,
                null
        );
    }

    public static StepResult success(String message) {
        return new StepResult(
                StepStatus.SUCCESS,
                null,
                message
        );
    }

    public static StepResult fail(String message) {
        return new StepResult(
                StepStatus.FAIL,
                null,
                message
        );
    }
}
```

---

## 3.4 WorkflowStep

```java
public interface WorkflowStep {

    String name();

    StepResult execute(WorkflowContext context);
}
```

---

## 3.5 WorkflowExecutor

Workflow 전체의 흐름은 Java가 통제한다.

```java
public class WorkflowExecutor {

    private final Map<String, WorkflowStep> steps;

    public WorkflowExecutor(Map<String, WorkflowStep> steps) {
        this.steps = steps;
    }

    public WorkflowContext execute(
            String startStep,
            WorkflowContext context) {

        String currentStep = startStep;

        while (currentStep != null) {

            WorkflowStep step = steps.get(currentStep);

            if (step == null) {
                throw new IllegalStateException(
                        "Unknown workflow step: " + currentStep
                );
            }

            StepResult result = step.execute(context);

            switch (result.status()) {

                case NEXT_STEP -> {
                    currentStep = result.nextStep();
                }

                case LOOP -> {
                    // currentStep 유지
                }

                case SUCCESS -> {
                    return context;
                }

                case FAIL -> {
                    throw new RuntimeException(
                            "Workflow failed: " +
                            result.message()
                    );
                }
            }
        }

        return context;
    }
}
```

---

# 4. `AiAgent` 인터페이스

대화 중 지적된 중요한 부분이다.

처음 예제에서 `AiAgent<I,O>`를 정의해 놓고 `SqlConversionAgent`가 이를 구현하지 않았는데, 이는 설계상 일관성이 부족했다.

재사용 가능한 `dstone-ai-engine`이라면 다음과 같이 구현하는 것이 적절하다.

```java
public interface AiAgent<I, O> {

    O execute(I input);
}
```

그리고 SQL 변환 Agent는:

```java
@Component
public class SqlConversionAgent
        implements AiAgent<SqlConversionInput, SqlConversionOutput> {

    private final ChatClient chatClient;

    public SqlConversionAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public SqlConversionOutput execute(
            SqlConversionInput input) {

        String result = chatClient.prompt()
                .system("""
                    You are an Oracle to PostgreSQL
                    SQL conversion expert.

                    Analyze the Oracle SQL and convert it
                    to PostgreSQL SQL.

                    Preserve MyBatis syntax.
                    """)
                .user("""
                    Oracle SQL:
                    %s
                    """.formatted(input.oracleSql()))
                .tools(
                    new SqlAnalyzeTool(),
                    new SqlConvertTool()
                )
                .call()
                .content();

        return new SqlConversionOutput(result);
    }
}
```

입출력 DTO:

```java
public record SqlConversionInput(
        String oracleSql
) {
}
```

```java
public record SqlConversionOutput(
        String postgresqlSql
) {
}
```

---

# 5. Agent와 WorkflowStep의 역할 분리

이 부분이 중요하다.

### Agent

```text
"AI에게 일을 시키는 단위"
```

### WorkflowStep

```text
"프로세스의 한 단계"
```

따라서:

```text
WorkflowStep
    ↓
Agent
    ↓
LLM + Tools
```

구조가 된다.

예:

```java
public class ConvertStep implements WorkflowStep {

    private final SqlConversionAgent agent;

    @Override
    public StepResult execute(WorkflowContext context) {

        SqlConversionInput input =
                new SqlConversionInput(
                        context.get("oracleSql")
                );

        SqlConversionOutput output =
                agent.execute(input);

        context.put(
                "postgresqlSql",
                output.postgresqlSql()
        );

        return StepResult.next("VALIDATE");
    }
}
```

---

# 6. Agent를 업무별로 나누는 구조

`SqlConversionAgent`의 `analyze()` 같은 메서드를 무작정 늘리기보다는 업무 단위로 Agent를 분리하는 것이 좋다.

```text
Workflow
 │
 ├── AnalyzeStep
 │      └── SqlAnalysisAgent
 │
 ├── ConvertStep
 │      └── SqlConversionAgent
 │
 ├── ValidateStep
 │      └── SqlValidationAgent
 │
 └── FixStep
        └── SqlFixAgent
```

각각:

```java
public class SqlAnalysisAgent
        implements AiAgent<SqlAnalysisInput, SqlAnalysisOutput> {
}
```

```java
public class SqlConversionAgent
        implements AiAgent<SqlConversionInput, SqlConversionOutput> {
}
```

```java
public class SqlValidationAgent
        implements AiAgent<SqlValidationInput, SqlValidationOutput> {
}
```

```java
public class SqlFixAgent
        implements AiAgent<SqlFixInput, SqlFixOutput> {
}
```

이렇게 하면 Agent가 명확한 책임을 갖는다.

---

# 7. XML/YAML로 Workflow와 Agent를 정의하는 아이디어

이후 대화에서 더 발전된 아이디어가 나왔다.

> Agent, Workflow 등을 XML로 정의해 놓고 특정 Workflow가 호출되면 정의된 대로 일을 수행하게 하는 것은 어떤가?

이에 대해 **`dstone-ai-engine`에는 상당히 좋은 방향**이라고 판단했다.

핵심 개념:

```text
XML/YAML
   ↓
Definition
   ↓
Java Runtime
   ↓
Spring AI
   ↓
LLM / Tool
```

즉:

> XML/YAML은 선언적 정의(Definition), Java는 실행 엔진(Runtime), LLM은 지능적 판단을 담당한다.

---

# 8. Workflow XML 예시

```xml
<?xml version="1.0" encoding="UTF-8"?>

<workflow
    id="oracle-to-postgresql"
    version="1.0">

    <description>
        Convert Oracle MyBatis SQL to PostgreSQL MyBatis SQL
    </description>

    <input>
        <param name="oracleSql" type="string"/>
    </input>

    <steps>

        <step
            id="analyze"
            agent="sql-analysis-agent"
            next="convert"/>

        <step
            id="convert"
            agent="sql-conversion-agent"
            next="validate"/>

        <step
            id="validate"
            agent="sql-validation-agent">

            <on condition="VALID">
                <next>success</next>
            </on>

            <on condition="INVALID">
                <next>fix</next>
            </on>

        </step>

        <step
            id="fix"
            agent="sql-fix-agent"
            next="validate"
            max-retry="3"/>

    </steps>

    <result>
        <success>success</success>
    </result>

</workflow>
```

Java 코드 수정 없이 Workflow 정의를 변경할 수 있다.

---

# 9. Agent XML 예시

```xml
<agent
    id="sql-analysis-agent"
    type="llm">

    <model>
        <provider>ollama</provider>
        <model>qwen2.5-coder:7b</model>
    </model>

    <prompt>
        <system>
            You are an Oracle SQL analysis expert.
        </system>

        <instruction>
            Analyze Oracle-specific syntax.
        </instruction>
    </prompt>

    <tools>
        <tool ref="oracle-parser"/>
        <tool ref="sql-metadata"/>
    </tools>

</agent>
```

Conversion Agent:

```xml
<agent
    id="sql-conversion-agent"
    type="llm">

    <model>
        <provider>ollama</provider>
        <model>qwen2.5-coder:7b</model>
    </model>

    <prompt>
        <system>
            You are an Oracle to PostgreSQL
            conversion expert.
        </system>
    </prompt>

    <tools>
        <tool ref="sqlglot-converter"/>
        <tool ref="oracle-function-analyzer"/>
    </tools>

</agent>
```

---

# 10. Tool Registry

Tool도 Registry 형태로 관리할 수 있다.

```xml
<tool
    id="sqlglot-converter"
    type="python"
    executable="/app/tools/sql_converter.py"
    timeout="30"/>

<tool
    id="oracle-parser"
    type="java"
    bean="oracleParserTool"/>

<tool
    id="sql-metadata"
    type="http"
    url="http://localhost:8080/api/sql/metadata"/>
```

Agent에서는 Tool ID만 참조한다.

```xml
<tools>
    <tool ref="sqlglot-converter"/>
    <tool ref="oracle-parser"/>
</tools>
```

이렇게 하면 Agent와 실제 Tool 구현을 분리할 수 있다.

---

# 11. Application에서는 Workflow ID만 호출

응용 애플리케이션은 XML/YAML의 세부 내용을 알 필요가 없다.

```java
aiEngine.execute(
    "oracle-to-postgresql",
    Map.of(
        "oracleSql", sql
    )
);
```

실행 흐름:

```text
Application
     │
     │ "oracle-to-postgresql"
     ▼
AiEngine
     │
     ▼
WorkflowRegistry
     │
     ▼
oracle-to-postgresql.xml
     │
     ▼
WorkflowExecutor
     │
     ├── analyze
     │      ↓
     │    Agent
     │      ↓
     │    LLM
     │      ↓
     │    Tools
     │
     ├── convert
     │
     ├── validate
     │      │
     │      ├── VALID → success
     │      │
     │      └── INVALID → fix
     │
     └── fix
            │
            └──────→ validate
```

---

# 12. dstone-ai-engine의 추천 패키지 구조

```text
net.dstone.ai
│
├── definition
│   ├── WorkflowDefinition
│   ├── StepDefinition
│   ├── AgentDefinition
│   └── ToolDefinition
│
├── loader
│   └── XmlDefinitionLoader
│
├── registry
│   ├── WorkflowRegistry
│   ├── AgentRegistry
│   └── ToolRegistry
│
├── runtime
│   ├── WorkflowExecutor
│   ├── StepExecutor
│   ├── AgentExecutor
│   └── ToolExecutor
│
└── spring
    └── SpringAiAdapter
```

---

# 13. XML보다 YAML을 추천하는 이유

XML도 가능하지만 AI Workflow 정의는 구조가 깊어질 가능성이 높기 때문에 YAML이 읽기 쉽다.

XML:

```xml
<workflow>
    <step>
        <agent>
            <tools>
                <tool/>
                <tool/>
            </tools>
        </agent>
    </step>
</workflow>
```

YAML:

```yaml
workflow:
  id: oracle-to-postgresql

  steps:

    - id: analyze
      agent: sql-analysis-agent
      next: convert

    - id: convert
      agent: sql-conversion-agent
      next: validate

    - id: validate
      agent: sql-validation-agent

      on:
        VALID: success
        INVALID: fix

    - id: fix
      agent: sql-fix-agent
      next: validate
      max-retry: 3
```

따라서 추천은:

> **YAML 우선 + 필요하면 XML 지원**

이다.

---

# 14. LLM이 Workflow 정의 자체를 변경하면 안 된다

중요한 보안/안정성 원칙이다.

잘못된 구조:

```text
LLM
 ↓
Workflow 자체 변경
 ↓
새 Step 생성
 ↓
Validate 생략
```

권장 구조:

```text
              Workflow Definition
                      │
                      │ immutable
                      ▼
              Workflow Runtime
                      │
                      ▼
                    LLM
```

LLM은 정의된 Workflow 범위 안에서만 행동한다.

---

# 15. LLM에게 허용할 판단

예를 들어 Workflow가 다음처럼 정의되어 있다.

```yaml
- id: validate
  agent: sql-validation-agent

  transitions:
    - condition: VALID
      next: success

    - condition: INVALID
      next: fix

    - condition: UNSUPPORTED
      next: manual-review
```

LLM은:

```text
VALID
INVALID
UNSUPPORTED
```

중 하나를 판단할 수 있다.

하지만:

```text
새로운 Step을 만들어라
fix를 10번 실행하라
validate를 건너뛰어라
```

같은 것은 허용하지 않는다.

**Workflow 정의가 허용한 transition만 Java Runtime이 실행한다.**

---

# 16. 최종적으로 추천하는 dstone-ai-engine 구조

4계층으로 생각하는 것이 가장 명확하다.

```text
① Workflow
   "전체 업무 프로세스"

        ↓

② Step
   "프로세스의 한 단계"

        ↓

③ Agent
   "LLM을 이용해 지능적인 작업 수행"

        ↓

④ Tool
   "실제 기능/데이터/외부 시스템 접근"
```

전체 실행 구조:

```text
                 ┌───────────────┐
                 │   Workflow    │ ← YAML/XML
                 └───────┬───────┘
                         ↓
                 ┌───────────────┐
                 │     Step      │ ← YAML/XML
                 └───────┬───────┘
                         ↓
                 ┌───────────────┐
                 │     Agent     │ ← YAML/XML
                 └───────┬───────┘
                         ↓
                 ┌───────────────┐
                 │      LLM      │
                 └───────┬───────┘
                         ↓
                 ┌───────────────┐
                 │     Tool      │ ← Registry
                 └───────────────┘
```

최종적인 원칙:

```text
Workflow/Step의 순서와 상태 전이
        → Java Runtime

Agent 내부의 판단과 Tool 선택
        → LLM

실제 Tool 실행
        → Spring AI/Application

정의
        → YAML/XML

실행
        → dstone-ai-engine Runtime
```

이렇게 만들면 `dstone-ai-engine`은 단순한 Spring AI Wrapper가 아니라:

**Workflow Definition + Agent Definition + Tool Registry + Runtime**

을 갖춘 독립적인 **AI Agent Platform**으로 발전시킬 수 있다.

---

## 답변

현재 dstone-ai-engine과 dstone-common 구조를 먼저 파악한 뒤, 계획을 정리해서 검토받겠습니다.

---

## 질문

[Request interrupted by user for tool use]

---

## 질문

패키지구조를 아래와 같이 바꿔줘.
net.dstone.ai
    DstoneAiEngineApplication.java
    common (runtime, rag, tools, api을 제외한 나머지를 모두 이 밑으로 이동)
        annotation
        exec
        definition
        loader
        registry
        config (net.dstone.ai.spring 를 이 패키지로 이동)
        session
        security
    runtime
        agent
        tool
        step
    rag
    tools
    api

---

## 답변

Plan updated to match the requested package layout (`common`에 definition/loader/registry/config/session/security/annotation/exec를 모두 모으고, `runtime`은 agent/tool/step 서브패키지만 유지). 내용을 확인해주세요.

---

## 답변

계획이 승인되었습니다. 먼저 현재 상태를 체크포인트 커밋으로 남긴 뒤 삭제 작업을 시작하겠습니다.

---

## 답변

実装を進めます。まず `common` 하위 기반 패키지(definition/loader/registry/annotation/exec)부터 작성하겠습니다.

---

## 답변

Now the executor and the agent/tool/step runners it delegates to.

---

## 답변

Now the `AsyncJobService` and `WorkflowController`.

---

## 답변

Good, dstone-common is already installed. Now let's compile dstone-ai-engine.

---

## 답변

There's already an `env-wsl.properties` — exactly the profile for this environment.

---

## 답변

Exactly what I need. Let me restart with the `wsl` profile active.

---

## 답변

Exit 124 means it was still running when `timeout` killed it after 30s — a good sign (no crash). Let's check the log.

---

## 답변

The application started successfully with no errors. Let's view the full log to check the registry/loader logs (agent/workflow discovery) and Redis/DB connections.

---

## 답변

App is running persistently now (PID 29811). Let's hit the chat endpoint with the `general-chat` agent.

---

## 답변

Async submit/poll flow works too. Let's shut the app down cleanly now that verification is complete.

---

## 답변

Process confirmed stopped. Now let's finish the plan's remaining step: rewriting `docs/09.dstone-ai-engine.md` to reflect the new architecture, and updating CLAUDE.md's ai-engine section.

---

## 답변

Now I'll replace this entire section with a concise description of the new architecture.

---

## 답변

Redesign complete for `dstone-ai-engine`, and it's live-verified end to end. Summary:

**What's done**
- Deleted the entire old `net.dstone.ai.*` tree (capability/process/agent/governance/Phase 0–10) after a checkpoint commit, and rebuilt it around **Workflow → Step → Agent → Tool**, exactly per the approved plan's package layout (`common/{definition,loader,registry,config,prompt,session,security,annotation,exec}`, `runtime/{agent,tool,step}`, `rag`, `tools`, `api`).
- Workflow/Agent are now declared in `resources/workflows/*.yml` / `resources/agents/*.yml`, loaded via `YamlDefinitionLoader` (SnakeYAML + Jackson `convertValue`, no lambdas) — no more `application.yml`-embedded definitions.
- `capability` layer removed entirely: `/api/ai/chat` calls an Agent directly, `/api/ai/workflow/{id}` calls a Workflow directly, both caller-gated via `allowedCallers` on the definitions themselves.
- Governance (PII/sensitive-word/usage) dropped, with two explicit re-attachment seams: `ConfigChatClient`'s `List<Advisor>` injection point, and `ToolExecutor.call()` as the single Tool-invocation choke point.
- Session, RAG, API-key auth, and rate-limit were ported (not dropped) per your earlier answer, just moved under `common.session`/`rag`/`common.security` and renamed from `governance.auth`/`governance.ratelimit` → `security.auth`/`security.ratelimit`.
- `docs/09.dstone-ai-engine.md` and the CLAUDE.md ai-engine section were rewritten for the new architecture; a few other docs (`05.postgresql.md`, `06.redis.md`, `14.ollama.md`) had stale class-path/anchor references fixed.

**Verified live** (real Anthropic calls, local Redis/Postgres+pgvector/Ollama): clean boot with 4 agents + 1 workflow + 2 tools auto-registered; `/api/ai/chat` plain reply and tool-calling reply both correct; `oracle-to-postgresql` workflow (analyze→convert→validate) converted `NVL/ROWNUM` → `COALESCE/LIMIT` correctly and passed validation on the sync `/execute` **and** async `/submit`+`/status` paths.

**One thing I found but didn't touch**: `dstone-boot`'s SQL-conversion screen (`SqlConvertService`) calls the old contract — `POST /api/ai/chat` with `{message, sessionId, capability:"oracle-to-postgresql"}`. That field no longer exists, so this dstone-boot feature is now broken (400 "agent는 필수입니다"). The natural fix is pointing it at the new `POST /api/ai/workflow/oracle-to-postgresql/execute` contract instead — but that's a change to a different module, outside what you asked me to redesign, so I left it alone rather than deciding unilaterally.

Want me to fix `SqlConvertService` now, or leave it as a separate follow-up?

---

## 질문

fix it

---

## 답변

Compiles cleanly. Now let's update the doc section describing this flow.

---

## 답변

Let's verify this fix actually works against the live engine (same request shape dstone-boot now sends).

---

## 답변

The fixed request shape works end-to-end (`message`/`sessionId` only, no `capability`, hitting `/api/ai/workflow/oracle-to-postgresql/execute`) — exactly what `SqlConvertService` now sends, and the JSON matches `WorkflowCallResult(message, sessionId, workflowId)` correctly. Let's shut the app down.

---

## 답변

Stopped cleanly.

**Fix summary:**
- `dstone-boot/.../SqlConvertService.java`: now calls `POST /api/ai/workflow/oracle-to-postgresql/execute` with just `{message, sessionId}` (no `capability`), deserializing into a new `WorkflowCallResult(message, sessionId, workflowId)` VO instead of the chat-shaped `ChatCallResult`.
- Added `dstone-boot/.../vo/WorkflowCallResult.java`.
- `docs/06.dstone-boot.md` §10.5 (text + sequence diagram) updated to match.
- `dstone-boot` compiles cleanly, and I verified the exact new request shape against a live `dstone-ai-engine` — correct conversion + validation came back.

Both modules are now consistent with the redesigned engine.
