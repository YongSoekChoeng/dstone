# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**dstone** is a Java 21 / Spring Boot 4.1 (Spring Framework 7) enterprise multi-module framework providing:
- **dstone-common**: Shared library (JAR) — utilities, security, data access, messaging
- **dstone-boot**: Web application framework (WAR) — includes a Java source code static analyzer feature
- **dstone-batch**: Spring Batch processing framework (JAR) — standardized job development
- **dstone-batchadmin**: Web application (WAR) — manages `dstone-batch` jobs (list/detail/register screens, start/stop/restart, CRON auto-scheduling) across one or more `dstone-batch` server instances
- **dstone-ai-engine**: AI & MLOps Core Engine (JAR) — Spring AI-based, provider-agnostic AI serving platform intended for reuse across future SI projects (root package `net.dstone.ai`)
- **dstone-knowledge**: Java static analysis → Knowledge Graph → RAG data platform (JAR, root package `net.dstone.knowledge`) — under construction (M0 skeleton done 2026-10-04); will absorb `dstone-ai-engine`'s embed/RAG feature and `dstone-boot`'s source analyzer, which are then removed

`dstone-boot`, `dstone-batch`, `dstone-batchadmin`, `dstone-ai-engine`, and `dstone-knowledge` all depend on `dstone-common`.

## Build Commands

```bash
# Always build dstone-common first (required by other modules)
cd dstone-common && mvn clean install

# Build web app (WAR)
cd dstone-boot && mvn clean package

# Build batch (JAR)
cd dstone-batch && mvn clean package

# Build batch admin web app (WAR)
cd dstone-batchadmin && mvn clean package

# Build AI Core Engine (JAR)
cd dstone-ai-engine && mvn clean package

# Build knowledge platform (JAR)
cd dstone-knowledge && mvn clean package

# Build all from root
mvn clean install

# Skip tests
mvn clean package -DskipTests
```

## Running

```bash
# dstone-boot (port 7081)
java -jar target/dstone-boot.war

# dstone-batch — run a specific job
java -jar -Dspring.batch.job.names=sampleJob target/dstone-batch-1.0.0-SNAPSHOT.jar

# dstone-batchadmin (port 5081)
java -jar target/dstone-batchadmin.war

# dstone-ai-engine (port 8081)
java -jar target/dstone-ai-engine.jar

# dstone-knowledge (port 4081) — normally via bin/startApp.sh
java -Dspring.profiles.active=wsl -jar target/dstone-knowledge.jar
```

See "Cloud Architecture Simulation" below for how each module is deployed in this environment.

## Architecture

### Configuration Pattern (all modules)

```
conf/
├── env.properties      # Sensitive config (DB credentials, Jasypt key) — loaded as System Properties at startup
├── application.yml     # App config — references ${ENV_VAR} from env.properties
└── log4j2.xml          # Logging config
```

Startup sequence: `setSysProperties()` loads `conf/env.properties` → `application.yml` is read → Log4j2 initializes.

For server deployment, comment out `application.yml` and `log4j2.xml` from `src/main/resources` in the build so that the `conf/` directory versions take precedence.

### Database

All modules use HikariCP + MyBatis + log4jdbc. The `log4jdbc` driver wraps the real driver to log SQL:

```yaml
driver-class-name: net.sf.log4jdbc.sql.jdbcapi.DriverSpy
jdbc-url: jdbc:log4jdbc:mysql://${DB_HOST}:${DB_PORT}/<database>
```

MyBatis XML mappers live under `src/main/resources/sqlmap/`.

### Sensitive Config Encryption (Jasypt)

DB passwords and other secrets in `application.yml` use `ENC(...)` format:

```yaml
password: ENC(ydLjxrknr8dD59e6E+HvxdxRaGiFa9jOCpJJDtb0uak=)
```

Decryption is **not** handled by jasypt-spring-boot-starter (it has no Spring Boot 4-compatible release) — `dstone-common`'s `net.dstone.common.config.ConfigProperty` declares a nested static `EncPropertyEnvironmentPostProcessor` (registered via `dstone-common/src/main/resources/META-INF/spring.factories`) that wraps every `PropertySource` and transparently decrypts any `ENC(...)` value using `EncUtil.getEncryptor()` (`net.dstone.common.utils.EncUtil`, PBEWithSHA256And128BitAES-CBC-BC, key hardcoded in that class) before Spring binds it — including `@ConfigurationProperties`-bound values like `ConfigDatasource`'s HikariCP passwords, which never pass through `ConfigProperty.getProperty()` at all. This applies automatically to every module that depends on `dstone-common`; no per-module `ConfigEnc`/`@EnableEncryptableProperties` bean is needed anymore. To generate a new `ENC(...)` value locally without ever typing the plaintext secret into Claude Code's chat (run this in a separate terminal, not via the assistant):

```bash
cd dstone-common && mvn -q exec:java -Dexec.mainClass=net.dstone.common.utils.EncUtil -Dexec.args="<plaintext>"
```

### Security

- **dstone-boot**: Spring Security enabled (`spring.security.enabled: true`), with custom auth handlers in `net.dstone.boot.common.security`, OAuth2 social login (Google/Naver/Kakao), and Redis-based distributed sessions (`dstone:session` namespace).
- **dstone-batch**: Spring Security excluded via `spring.autoconfigure.exclude`.
- **dstone-ai-engine**: Spring Security (and its Actuator management-endpoint security) excluded via `spring.autoconfigure.exclude` — no Spring Security filter chain. Auth is instead a lightweight opt-in servlet filter, `common.security.ApiKeyAuthFilter` (`X-API-Key` header, `dstone.ai.security.auth.enabled`), paired with `common.security.RateLimitFilter` (`dstone.ai.security.ratelimit.enabled`).

### dstone-boot: Multiple Datasources

Three independent datasources configured in `ConfigDatasource.java`:
- `common` → `sampleDB` (main app data)
- `sample` → `sampleDB` (sample data)
- `analyzer` → `analyzeDB` (code analysis results)

### dstone-boot: Source Code Analyzer

A built-in static analysis engine for Java web applications:

- `AnalysisController` → accepts async analysis requests
- `AppAnalyzer` (`net.dstone.boot.common.tools.analyzer`) → core engine
  - `.java` files: parsed with **javaparser 3.28.1** (class hierarchy, method calls, URL mappings)
  - `.jsp` files: parsed with **jsoup 1.11.3** (page structure, links)
  - MyBatis `.xml` files: parsed with **JSQLParser 4.7** (SQL, CRUD table relationships)
- Results stored in the `analyzer` datasource, visualized via `ReportController`

### dstone-batch: Job Development Pattern

All batch jobs extend `BaseJobConfig` and are annotated with `@AutoRegJob`:

```java
@Component
@AutoRegJob(name = "myJob")
public class MyJobConfig extends BaseJobConfig {
    @Override
    public void configJob() throws Exception {
        this.addStep(this.createStep("step1"));
        this.addStep(this.createMultiThreadStep("step2", 20, 5, reader, processor, writer));
        this.addFlow(this.createSplitFlow("parallelFlow"));
    }
}
```

`ConfigAutoReg.java` scans for `@AutoRegJob` and registers jobs in `JobRegistry`.

- `auto-register-jobs: true` → all jobs registered at startup (REST API mode)
- `auto-register-jobs: false` → jobs registered individually at execution time (CLI / SCDF mode)

Spring Batch metadata tables must be created manually from `src/main/resources/schema/*.sql` (`initialize-schema: NEVER`). Spring Batch 6 renamed the `BATCH_JOB_SEQ` sequence table to `BATCH_JOB_INSTANCE_SEQ` — existing databases created from an older schema file need that table renamed (or the updated `02-create-table-mysql-dstone-batch.sql` re-run) before job launches will work.

### dstone-batchadmin: Batch Job Management

Manages `dstone-batch` jobs via two mechanisms, both configured per registered batch server (`TB_BATCH_SERVER`):
- **Direct DB read** for list/detail/history — queries the target server's Spring Batch metadata tables (`BATCH_JOB_INSTANCE`/`BATCH_JOB_EXECUTION`/`BATCH_STEP_EXECUTION`) through a `RoutingDataSource` (`common/datasource/RoutingDataSource.java`) that resolves the correct `HikariDataSource` per server at runtime (built/cached by `BatchServerDataSourceRegistry`).
- **REST calls** for control actions (start/stop/restart/abandon/delete) — `common/rest/BatchRestClient.java` calls the target server's `dstone-batch` `RestApiRunner` endpoints (`/batch/startJob/{jobName}`, `/batch/stopJob/{id}`, etc.).

Two datasources:
- `common` → `batchadmin` schema (static, own login users/`TB_ADMIN_USER`, server registry/`TB_BATCH_SERVER`, job metadata/`TB_BATCH_JOB`)
- `batch` → the `RoutingDataSource` described above (dynamic, one target per registered server)

Since `RoutingDataSource` prevents MyBatis's `databaseIdProvider` from resolving per-query, MySQL/PostgreSQL differences (mainly paging syntax) are handled with an explicit `DBMS_TYPE` query parameter and `<choose>` branches in `sqlmap/job/BatchJobExecDao.xml`, rather than the `databaseId` mapper attribute.

Registered Job metadata (`TB_BATCH_JOB.JOB_NM`) must match the `@AutoRegJob(name=...)` value on the target `dstone-batch` server exactly — `dstone-batchadmin` cannot create new Job logic, only manage metadata and trigger the existing REST API. `common/scheduler/JobScheduleManager.java` uses Spring's built-in `ThreadPoolTaskScheduler` + `CronTrigger` (no Quartz) to auto-start jobs whose `SCHEDULE_USE_YN='Y'`.

Security is simplified vs. `dstone-boot`: login is required (`TB_ADMIN_USER`, `BCryptPasswordEncoder`) but there is no per-URL role/permission check — it's a single-role internal admin tool.

### dstone-ai-engine: AI & MLOps Core Engine

A Spring AI-based, provider-agnostic engine meant to be reused across future SI projects. Fully redesigned on 2026-09-15 around a **Workflow → Step → Agent → Tool** model: Java (`runtime.workflow.WorkFlowExecutor`) controls the overall sequence/branch/parallel/loop shape, while the intelligent judgment inside each step (analysis, conversion strategy, tool choice) is left to the LLM. What a Workflow or Agent actually does is declared in YAML (`resources/workflows/*.yml`, `resources/agents/*.yml`), not Java code or `application.yml` — adding a new one is a new YAML file, no redeploy-affecting code change. Root package `net.dstone.ai`:

| Package | Purpose |
|---|---|
| `common.definition` | Pure records that mirror the YAML files 1:1, split by YAML directory: `SchemaDefinition` (one input/output contract = a standard JSON Schema map; YAML shorthand like `output: string` / `list<string>` is expanded at bind time) / `agent.AgentDefinition` (agents/*.yml, incl. `input`/`output` contracts — `inputSchema()`/`outputSchema()` default to `{type: string}`) / `mcp.McpServerDefinition` (mcp/*.yml) / `workflow.WorkFlowDefinition` (workflows/*.yml, incl. `input` contract + required `output`) + `workflow.WorkFlowOutputDefinition` (`value` + optional `schema`) / `workflow.step` — one record per step type (`AgentStepDefinition`/`SupervisorStepDefinition`/`RouterStepDefinition`/`ToolStepDefinition`/`ApprovalStepDefinition`, chosen by YAML `type` via Jackson `@JsonSubTypes` on the sealed interface `StepDefinition`, which only carries `id`/`type`/`onFailure` plus static `refOf`/`onSuccessOf`/`forEachOf`/`itemKeyOf`/`memoryOf`/`routesOf` helpers). **No step has an `output` key** — the callee owns the contract (Agent `input`/`output`, Tool arg schema, engine-fixed shapes for SUPERVISOR/ROUTER/APPROVAL). A key a step type doesn't have fails YAML binding at startup (loader turns Jackson errors into Korean YAML-path messages, with hints for removed keys: step `output`, TOOL `pattern`, `workflow.inputs`, and schemas written without `schema:`) |
| `common.consts` | `Constants` (config-key/reserved-word constants, incl. the execution-context tree names under `Constants.WorkFlow.Context`: `input`/`steps`/`approvals`, step record fields `input`/`output`/`error`) plus the enums `StepType` (`AGENT`/`SUPERVISOR`/`ROUTER`/`TOOL`/`APPROVAL`) and `McpTransport` (`STDIO`/`SSE`) |
| `common.schema` | `ExpressionEvaluator` (`@Component`) — the `"${ jq }"` evaluator (jackson-jq 1.x on Jackson 2 `JsonNode`, jq 1.7 syntax) every step `input`/`forEach`/Workflow `output.value` goes through: `resolve()` (whole-value `${ … }` only → result keeps its JSON type; maps/lists resolved per value; anything else is a literal; zero results → null, several → error), `check()` (boot-time compile + dry run on `{input: null, steps: {}}` to catch unknown functions/variables), `references()` (text scan for `.steps.<id>…`/`.input…` paths before the first `|`). `JsonSchemas` — shorthand expansion (`normalize`), JSON Schema 2020-12 meta-schema check (`checkSchema`), value validation (`validate`, networknt json-schema-validator 3.x — already on the classpath via Spring AI, used through its String API so no Jackson 3 types leak), boot-time path check of what an expression reads against a schema (`checkPath`), `toText()` (text as-is, anything else as JSON text). `StepOutputSchemas` — engine-fixed output schemas: SUPERVISOR `{pass, reason}`, ROUTER `{route (enum = routes keys), reason}`, APPROVAL `{approved, approver, comment}` |
| `common.loader` | `YamlDefinitionLoader` — SnakeYAML + Jackson `convertValue` from `classpath:workflows/**/*.yml` / `agents/**/*.yml` / `mcp/**/*.yml` into the records above (with `${VAR}` substitution) |
| `common.registry` | `WorkFlowRegistry`/`AgentRegistry`/`McpServerRegistry` — id/name → definition, plus `allowedCallers` (tenant) whitelist check. `AgentRegistry` (injects `ConfigTool`) meta-validates each Agent's `input`/`output` schema and its `tools`/`subAgents`: `"*"` mixed with names, unknown sub agent id, a sub agent that itself has `subAgents` (depth is fixed at 1 — this one rule also blocks self/cyclic references), a sub agent without `description`, or a sub agent id that isn't `[A-Za-z0-9_-]{1,64}` or collides with a Tool name all fail boot; an unregistered Tool name or a caller mismatch between parent and sub agent only warns. A removed `toolsEnabled` key fails YAML binding with a hint. `WorkFlowRegistry` (injects `AgentRegistry` + `ConfigTool` + `ExpressionEvaluator`) validates at startup, failing boot on the first error: schemas, `output.value` present, step ids match `[A-Za-z_][A-Za-z0-9_]*` (so `.steps.<id>` is valid jq) and aren't `SUCCESS`/`FAIL`, every `onSuccess`/`onFailure`/`routes` target exists, AGENT-type `ref` exists + step `input` shape matches the Agent's input schema (string↔single value, object↔map incl. property names/required; a whole-value expression is checked at run time), SUPERVISOR/ROUTER Agents declare no `output` and no `subAgents` (`tools: ["*"]` there warns), TOOL `input` arg names match the Tool's input schema (unknown Tool → warning only), `forEach` is an expression; and for every value in step `input`/`forEach`/`output.value`: no old `{{ }}`, no `${ }` mixed into text, no unresolved `${ENV}`, jq compiles and dry-runs without unknown functions/variables (`$item` only inside a forEach step's `input`), `.steps.<id>` exists and is followed by `input`/`output`/`error`, and the referenced step can run before the referencing one (reachability over `onSuccess`/`onFailure`/`routes`, so retry loops pass). Field paths beyond that are walked through Workflow input schema / Agent contracts / engine schemas and only **warn** |
| `common.config` | `Config`/`ConfigCallLog` (AOP call logging)/`ConfigChatClient` (single shared `ChatClient`)/`ConfigRedis`/`ConfigTool` (`@AiTool` bean scanning)/`ConfigMcp` (MCP client connections) |
| `common.session` | `RedisChatMemoryRepository` — Spring AI `ChatMemoryRepository` over Redis (system prompts are inlined in `agents/*.yml`'s `prompt` field — there is no separate prompt registry) |
| `common.security` | `ApiKeyAuthFilter`/`RateLimitFilter`/`CallerContext` |
| `runtime.workflow` | `WorkFlowExecutor` — the sequential/branch/parallel(`forEach`)/loop/approval-wait state machine; it resolves each step's `input` via `ExpressionEvaluator.resolve()` (forEach passes the item as the jq variable `$item`/`$<itemVariable>`) before calling the step executor and records `{input, output, error}` into the execution context; on SUCCESS it resolves `output.value` (and validates `output.schema`). Its per-step "what next" decision is `nextStepId()` returning a step id or the `SUCCESS`/`FAIL` sentinel. `runtime.workflow.execution` holds the persisted `WorkFlowExecution` (its `context` map = `CONTEXT_JSON` column, `output` = `RESULT_TEXT` stored as JSON text)/`WorkFlowContext` (context-tree helpers)/`WorkFlowExecutionStatus`/`StepHistoryEntry`/`WorkFlowExecutionStore` |
| `runtime.agent` | `AgentExecutor` — the one place that turns an `AgentDefinition` into a `ChatClient` call and enforces its contract: `call()` (validates input against `inputSchema()`, sends text as-is / anything else as JSON text, returns the raw reply for a string `outputSchema()` or a parsed+validated JSON value otherwise), `callForSchema()` (same input check, engine-given output schema — SUPERVISOR/ROUTER), `stream()` (string-output Agents only). Prompt `{var}`s (Spring AI `PromptTemplate`) are filled from **the Agent's own input** (object fields; chat `variables` layered on top; non-string values as JSON text; names that aren't `[A-Za-z_][A-Za-z0-9_]*` skipped) — Workflow input never reaches a prompt directly. The system prompt is always **engine rules + task prompt**: `runtime.prompt.EnginePrompt.compose()` puts fixed Java-constant rules (`COMMON` on every call, plus the caller-supplied `engineRule` — `SUPERVISOR`/`ROUTER`/`SUB_AGENT`; AGENT steps and the chat API pass `null`) in front of the Agent's rendered `prompt`, so YAML authors (possibly non-developers) can't drop them — there is deliberately no "role" enum, the caller just hands over the text. `buildSpec()` attaches only the Tools named in the Agent's `tools` list (∩ the caller whitelist; `["*"]` = all; chat `toolsEnabled` can only switch that list off, never widen it) and wraps each `subAgents` entry in a `SubAgentToolCallback` (Agent-as-Tool: name = agent id, description = `description`, args = input schema — non-object inputs are wrapped as `{input: ...}`; the child starts with an empty conversation and sees only the args; contract violations / exceeding `dstone.ai.agent.sub-agent.max-calls` (default 10) come back as `실패: ...` tool replies, system errors propagate; calls are logged by the callback itself because the self-invocation bypasses the `ConfigCallLog` proxy). `SchemaOutputConverter` (JSON Schema → format instruction + strict parse/validate), `AgentContractException` (contract violation → step failure / chat 400, as opposed to system errors → FAILED), and `ProviderErrorMessage` (appends the upstream provider name + real reason from OpenRouter's `error.metadata.raw` to a failed step's message, since the outer message is just `Provider returned error`; when the provider gave no detail it appends the exception's cause chain instead — `Error reading response (원인: IOException: ... <- ...)` — and `WorkFlowExecutor` writes the full stack to `execution.log` via `stackTraceOf()` rather than `printStackTrace()`, because a bare `Error reading response`/`Request failed` says nothing about who cut the connection). There are no `Verdict`/`RouteDecision` classes |
| `runtime.prompt` | `EnginePrompt` — the fixed engine-rule texts and `compose(engineRule, taskPrompt)` |
| `runtime.tool` | `ToolExecutor` — the one place a `TOOL` step calls a Tool by name without going through the LLM — plus `ToolOutcome`, the structured success/message reply schema a `@Tool` method can return (a tool that wants structured `output` returns any record/Map/List instead; `ToolStepExecutor` puts a JSON reply into `steps.<id>.output` as-is and a non-JSON reply as text — there is no step-level parse option) |
| `runtime.step` | One executor per step definition, no shared interface: `AgentStepExecutor`/`SupervisorStepExecutor`/`RouterStepExecutor`/`ToolStepExecutor`/`ApprovalStepExecutor`, each with a typed `run(execution, <XxxStepDefinition>, ...)` (so `ConfigCallLog` matches `runtime.step.*StepExecutor.run(..)`), plus `StepOutcome` — one flat record (`success`/`pending`/`input`/`output`/`error`/`route`/`durationMs`) whose `toRecord()` = `{input, output, error}` is exactly what lands in context `steps.<id>` (forEach: `input`/`output` are per-iteration lists). `WorkFlowExecutor.runStep()` dispatches by an exhaustive `switch` over the sealed step type (no `StepType.RAG`/`RagStepRunner` — RAG is either an AGENT's `ragEnabled` advisor or the `RagSearchTool` `@AiTool`) |
| `common.rag` | `RagRetrievalChain` — the only place that turns an already-ingested `VectorStore` into search results/an `Advisor` (`buildAdvisor()`/`search()`); ingest/delete is `api.service.EmbedService` instead, gated by `dstone.ai.rag.enabled` |
| `tools` | `@AiTool` implementations: `sample`/`sql`/`shell`/`python`/`http`/`jenkins`/`rag` (`RagSearchTool`) |
| `api` | `ChatController` (`GET /api/ai/chat` - agent list incl. input/output schemas and `tools`/`subAgents` names; `POST /api/ai/chat[/stream]` `{agent, input, variables...}` → `{output, ...}`, one Agent call; stream only for string-output Agents), `WorkFlowController` (`GET /api/ai/workflow` - id+description+input schema list; `POST /api/ai/workflow/{id}/execute` `{input, sessionId}` → `{status, output, ...}`, sync; `/submit`+`/status/{executionId}`, async — all three entry paths go through `api.service.WorkFlowExecutionService`, which validates `input` against the Workflow input schema → 400), `WorkFlowExecutionController`, `EmbedController` (`/api/ai/embed/documents` - ingest/delete/list), `RagController` (`POST /api/ai/rag/search` - search preview) |

Step-to-step data flow (contract model 2026-09-28, jq expressions 2026-09-29): **the callee owns the contract** — an Agent declares what it takes and returns (`agents/*.yml` `input`/`output`, standard JSON Schema written as YAML under `schema:`, shorthand `input: string`/`list<string>` allowed, both default to `string`), a Tool's contract is its arg schema/reply, and SUPERVISOR/ROUTER/APPROVAL outputs are engine-fixed (their Agents must not declare `output`). APPROVAL has two modes: approve/reject (`onSuccess`/`onFailure`, output `{approved, approver, comment}`) or, when the step declares `routes`, a human-picked choice (decision body `{route, ...}`, output `{route, approver, comment}`, never fails; `routes` + `onSuccess`/`onFailure` together fails boot, an unknown `route` is a 400 and the run stays waiting). `ApprovalStepExecutor` deletes the decision from `approvals` once consumed, so a flow may loop back to the same APPROVAL step and be asked again (the last decision stays readable at `steps.<id>.output`); `GET .../executions/{id}` exposes `pendingApproval {stepId, approverRole, routes}` so the dstone-boot admin screen can render one button per route. A step only says what to put in (`input`, required for AGENT/SUPERVISOR/ROUTER, shaped per the Agent's input schema: text for string, map for object; TOOL is an arg map) and where to go next. A Workflow declares its request contract (`input`, default `string`; request body is `{input, sessionId}` — no `message`/`variables`) and its result (`output: {value, schema?}`, `value` required). All state lives in one execution-context tree with **no implicit names**: `input` (the request input as-is), `steps.<id>.{input,output,error}`, `approvals` (internal). There is no `previous`, `text`, `items` or `inputs.message`; `.steps.<id>.<anything but input/output/error>` fails startup. Every value is JSON; the only expression form is a whole value `"${ <jq> }"` over the context `{input, steps}` (`approvals` hidden), e.g. `"${ .steps.fix.output.sql // .input }"`, `'${ "(분류: " + .steps.classify.output.route + ") " + .input.message }'` — no mid-string interpolation (build text with jq `+`), no `{{ }}` (fails boot with a hint). jq was chosen over SpEL because it's side-effect free (no arbitrary Java calls from shared YAML). A missing path is `null` (use `//`), a jq runtime error fails the step (→ `onFailure`), a step executor exception fails the whole run, an `AgentContractException` is a step failure (→ `onFailure`). `${VAR}` in YAML is a separate load-time env substitution that only matches `[A-Z0-9_]+`, so it never collides with `${ .jq }`. MCP tool calls are serialized per server (`ConfigMcp.SerializedToolCallback`) because one STDIO client can't take concurrent requests. Steps do **not** share chat memory: `MessageChatMemoryAdvisor` is not a `ChatClient` default advisor — `AgentExecutor.buildSpec()` attaches it only when given a conversation id (chat API: request `sessionId`; Workflow: only AGENT/SUPERVISOR/ROUTER steps with `memory: true`, room `sessionId:stepId` via `WorkFlowExecution.conversationIdOf()`, e.g. for retry loops; `memory` + `forEach` fails boot). Design record: `docs/temp/dstone-ai-engine-refactory-20260928-1.md`. Engine prompt layer / per-Agent Tool list / Sub Agent (2026-10-01): `docs/temp/dstone-ai-engine-subagent-20261001.md`.

Full architecture, the YAML field reference (§7) with copy-paste templates for every step type/Agent/MCP server (§8), and the API/config reference live in `docs/09.dstone-ai-engine.md` — it describes the current state only, no change history.

`spring.ai.openai.timeout` does **not** reach chat calls on its own in Spring AI 2.0.1: `OpenAiChatModel.buildRequestOptions()` overrides the client timeout on every request with `OpenAiChatOptions.getTimeout()`, which has no config key and defaults to 60s (seen 2026-10-02: a slow model writing a long document was cut at exactly 60s — `Error reading response (원인: InterruptedIOException: timeout <- StreamResetException: stream was reset: CANCEL)`). So `ConfigChatClient.requestOptions(model)` builds the per-request options — an `OpenAiChatOptions` builder carrying that timeout when the provider is `openai`, plus the model override — and `AgentExecutor.buildSpec()` attaches it on every call; putting it in `ChatClient.defaultOptions` is not enough because a per-request `options(...)` replaces the defaults wholesale. Any new code that calls `ChatClient` without going through `AgentExecutor` must use `requestOptions()` too.

The engine shares a single LLM provider (`spring.ai.model.chat`, Anthropic by default) across every Agent — there is no per-Agent provider override, but `AgentDefinition.model` lets a Workflow/Agent YAML override just the model within that shared provider (`runtime.agent.AgentExecutor.buildSpec()` applies it as a per-request `ChatOptions`; an unset `model` falls back to `spring.ai.{provider}.chat.options.model`, and a model name from a different provider than the active one only fails at that Agent's first call, not at startup). `spring.ai.anthropic.api-key` in `dstone-ai-engine/conf/application.yml` follows the same `ENC(...)` convention as DB passwords (see "Sensitive Config Encryption" above) rather than sourcing from `env.properties`. Spring AI is on the 2.x line (`spring-ai.version` in `dstone-ai-engine/pom.xml`); Azure OpenAI is not supported as a chat provider (Spring AI 2.x dropped `spring-ai-starter-model-azure-openai` — Azure remains vector-store-only).

RAG (`dstone.ai.rag.enabled`, default `false`) needs Postgres+pgvector and an embedding provider (`spring.ai.model.embedding: openai | ollama` — not `anthropic`, which has no embeddings API); this environment uses a local Ollama (`bge-m3`, 1024 dims, see `docs/software/14.ollama.md`). `api.service.EmbedService` tags every ingested chunk with a `tenant` metadata field keyed off `CallerContext`'s caller and it plus `common.rag.RagRetrievalChain` force a matching filter on search/delete/RAG-augmented chat, so one caller's documents never leak into another's — a no-op when `dstone.ai.security.auth.enabled=false` (caller is always null).

Tool calling has no infrastructure dependency — it's plain Java executed on demand. `common.annotation.AiTool` (meta-annotated `@Component`) marks a class containing `@org.springframework.ai.tool.annotation.Tool` methods; `common.config.ConfigTool` scans for `@AiTool` beans via `ApplicationContext.getBeansWithAnnotation` and wraps them into a `ToolCallbackProvider`, filterable per caller via `dstone.ai.tool.allowed-by-caller`. An Agent only ever sees the Tools named in its own `tools` list (there is no `toolsEnabled` switch anymore — a boolean that exposed every registered Tool was too loose for YAML written by non-developers). An `AGENT`/`SUPERVISOR` Workflow step lets the LLM pick among those through Spring AI's own tool-calling loop; a `TOOL` step calls one deterministically by name via `runtime.tool.ToolExecutor` (no LLM involved) — both paths go through the same `ConfigTool` whitelist. `ConfigTool` also wraps every Tool (local and MCP) in `LimitedToolCallback`, which cuts any single result longer than `dstone.ai.tool.max-result-chars` (default 60000) and appends a "narrow the range and call again" notice — Tool results pile up in the conversation and are re-sent on every LLM call, so one oversized result blows the context limit or the response timeout (seen 2026-10-01: a project-root file listing including `.git` made a 700KB request → `OpenAIIoException: Request failed`). `tools.utils.FileUtil` limits itself first (`dstone.ai.tool.file.*`: `readFileListAll` skips `exclude-dirs` and caps the count, `searchInFiles` returns capped `path:line: text` matches for a plain keyword, `readFile`/`readFileLines`/`readFileTail` cap characters); source-investigating Agents are told to `searchInFiles` first and open only the files they need, and to use `readFileLines` (`N: text` lines, optional `startLine`/`endLine`) whenever they need line numbers — with plain `readFile` a reasoning model counts lines by hand, burns the whole `max-tokens` on reasoning and returns an empty answer (`finishReason=LENGTH`, seen 2026-10-02 on `pilot-workflow` step03; `AgentExecutor.textOf()` now turns that case into an explicit `AgentContractException`, and SUPERVISOR/ROUTER failure messages carry the real reason instead of a constant `answer[null]`). `readFile` itself stays number-free because some Agents write what they read back with `writeFile`. `tools.shell.ShellExecTool`/`tools.python.PythonExecTool`/`tools.http.HttpCallTool` have no separate on/off flag — they only ever run a whitelisted script path or hit a whitelisted host (`dstone.ai.tool.shell.allowed-commands` / `.python.allowed-scripts` / `.http.allowed-hosts`, empty by default), never an LLM-supplied raw command/URL body. An empty whitelist is what keeps them inert by default; there used to be a `dstone.ai.tool.{shell,python,http}.enabled` flag documented alongside the whitelist, but it was never actually wired into the code (no `@ConditionalOnProperty` on the classes), so it was removed rather than implemented — the whitelist alone is the real gate.

Governance (PII/sensitive-word guardrails, usage logging/quota) is out of scope for this redesign, but two seams exist so it can be re-added without touching call sites: `common.config.ConfigChatClient.chatClient(...)` takes a plain `List<Advisor>` (Spring auto-collects any `@Bean Advisor`, so a governance module just adds one), and `runtime.tool.ToolExecutor.call(...)` is the single choke point every `TOOL` step call passes through.

Verified live end-to-end on 2026-09-15 (real Anthropic calls, local Redis/PostgreSQL+pgvector/Ollama): `POST /api/ai/chat` with a plain question and with a tool-calling question (`getCurrentDateTime`), and the `oracle-to-postgresql` sample Workflow (`analyze`→`convert`→`validate` AGENT/AGENT/TOOL steps) both synchronously (`/execute`) and via the async `/submit`+`/status/{jobId}` contract — an Oracle `NVL(...)`/`ROWNUM` query converted correctly to PostgreSQL `COALESCE(...)`/`LIMIT` and passed syntax validation on the first pass in both cases. (`oracle-to-postgresql` itself was replaced on 2026-09-21 by a broader 9-Agent/13-Workflow test-coverage set under `resources/{agents,workflows}/` — see `docs/09.dstone-ai-engine.md` §7.8 for the current sample list.) The 2026-09-28 contract-model redesign was re-verified the same way against all 15 sample Workflows' boot validation plus live runs (tool chain, forEach, MCP `directory_tree`→`read_text_file` forEach, APPROVAL resume, structured-output chain, retry loop with an object-in/object-out Agent, ROUTER, SUPERVISOR fail path, chat API with string/object Agents) and 11 deliberately broken YAMLs, each rejected at boot with the intended message. The 2026-09-29 switch from `{{ }}` to `${ jq }` was verified the same way: all 15 sample Workflows boot with no warnings, LLM-free runs (tool chain, forEach success/fail, MCP forEach with jq string concat, APPROVAL resume, gated tools) and local-Ollama LLM runs (retry loop, ROUTER + object-input `sample-role-reply-agent` filling prompt `{role}`, SUPERVISOR, structured-output chain, basic echo) all end `DONE`, and 14 deliberately broken/edge YAMLs (old `{{ }}`, mixed `${ }`, jq syntax, hyphen/reserved step ids, unknown step/field, unreachable reference, `$item` outside forEach, path-style `forEach`, bad target, unresolved env → boot failure; unknown schema field → warning; a retry-loop back-reference → accepted).

### dstone-knowledge: Java Analysis → Knowledge Graph → RAG

Analyzes Java applications (Spring **and** legacy plain Java: Java 1.4–7 syntax, XML config, servlets, raw JDBC, JSP) into a structured knowledge model first, then projects that model into a Knowledge Graph and RAG chunks. Current state, design principles, schema and the milestone table (M0–M9) live in `docs/11.dstone-knowledge.md`; the source design is `docs/temp/Java_Application_Knowledge_Graph_RAG_Analysis_Platform_설계서_v1.0.docx`. Rules that were decided with the user and must hold for all later milestones:

- **DB-backed pipeline, never whole-project-in-memory.** Each pass handles one file, writes rows, drops the AST (`analysis_file_pass` tracks per-file/per-pass progress so a run resumes; `analysis_reference` holds not-yet-resolved references between the DECLARE and RESOLVE passes). JavaSymbolSolver's default source type solver caches every parsed file and must be replaced by a DB-index-backed solver with a bounded cache.
- **Framework-neutral core.** Scan/AST/symbols/call graph need plain Java only; Spring is one semantic-analyzer plugin among others (plain-Java entry points, Spring XML, iBATIS, Struts, JSP).
- **Lombok handled at declaration time**: generated members go in as `is_synthetic=true` symbols (no delombok — it shifts line numbers).
- **Embedding keyed by `(content_hash, model)`** in `rag_embedding`, which doubles as the resumable queue; Spring AI is used only for the embedding model call, vector SQL is MyBatis (no `PgVectorStore`).
- **`analysis_*` is the only source of truth**; `kg_node`/`kg_edge` are VIEWs, `rag_*` is always regenerable. Every analysis row carries `revision_id`; symbol ids are revision-independent hashes. Full snapshot per revision + retention.
- No LLM chat in this module — `dstone-ai-engine` Agents call its REST API through Tools.

DB access: single `dataSourceCommon` (`spring.datasource.common.hikari.*`, PostgreSQL `dstone_knowledge`), MyBatis `sqlSessionCommon` + `sqlSessionBatch` (`ExecutorType.BATCH`, for bulk writes inside a transaction), and a programmatic `txTemplateCommon` instead of the name-based AOP transactions other modules use (analysis runs for hours and commits every N files). Schema is manual (`src/main/resources/schema/01-init…sql` as postgres superuser, `02-create-table…sql` as the app role; idempotent). `spring-boot-starter-test` carries a `spring-boot-starter-logging` exclusion — without it logback lands in the runnable jar and boot dies with `log4j-slf4j2-impl cannot be present with log4j-to-slf4j`. `src/test/resources/samples/legacy-app` is a deliberately EUC-KR, Java-1.4-syntax sample (do not re-save as UTF-8); its README holds the expected counts.

## Required Infrastructure

| Infrastructure | Purpose | Modules |
|---|---|---|
| MySQL | Main data store | dstone-boot, dstone-batch, dstone-batchadmin |
| Redis | Session store, cache | dstone-boot (dstone-ai-engine from Phase 1) |
| RabbitMQ | Message queue | dstone-boot |
| Anthropic API (or other LLM provider) | Chat model inference | dstone-ai-engine |
| PostgreSQL + pgvector | RAG vector store | dstone-ai-engine (Phase 2, when `dstone.ai.rag.enabled=true`) |
| PostgreSQL + pgvector | Analysis results, graph, RAG chunks/embeddings (`dstone_knowledge` DB) | dstone-knowledge |
| Ollama (or OpenAI) | Embedding model inference for RAG | dstone-ai-engine (Phase 2, when `dstone.ai.rag.enabled=true`) |

## Key Environment Variables (`conf/env.properties`)

| Variable | Description |
|---|---|
| `APP_HOME` | Application home directory |
| `APP_CONF_DIR` | Config file directory path |
| `DB_HOST` / `DB_PORT` | Database server |
| `REDIS_HOST` / `REDIS_PORT` | Redis server |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | RabbitMQ server (dstone-boot) |
| `FILE_UPLOAD_ROOT` | File upload root path (dstone-boot) |
| `MCP_STDIO_COMMAND_PREFIX` | dstone-ai-engine only, Windows-only: tokens prepended to every STDIO MCP server's command (e.g. `cmd.exe /c`) so `.cmd`/`.bat`-based commands like `npx` can be launched — `ProcessBuilder` can't run them directly on Windows without going through the command interpreter. Unset on Linux/WSL/k8s. |

Jasypt's decryption key is **not** an env var — see "Sensitive Config Encryption" above.

## Cloud Architecture Simulation

The WSL dev environment mirrors a cloud deployment shape: `dstone-boot` and `dstone-ai-engine` run as containerized Pods in a local `kind` Kubernetes cluster (`<module>/Dockerfile`, `<module>/k8s/`), while `dstone-batch`, `dstone-batchadmin` and `dstone-knowledge` run as VM-style processes controlled by plain shell scripts (`bin/startApp.sh`/`stopApp.sh`/`statusApp.sh` — **no systemd**), and MySQL/Redis/RabbitMQ/Kafka stand in for CSP-managed services outside the cluster. `dstone-ai-engine`'s manifests (`dstone-ai-engine/k8s/`) and Dockerfile follow `dstone-boot`'s pattern exactly (same namespace `dstone`, same `localhost:5000` local registry) — see `docs/04.cloud-architecture.md` for the full mapping, networking, and CI/CD design.

## CI/CD

Jenkins pipelines are defined in:
- `dstone-boot/Jenkinsfile`, `dstone-ai-engine/Jenkinsfile` — Maven reactor build → Docker build/push to a local registry (`localhost:5000`) → deploy to the `dstone` namespace in `kind` via `kubectl`
- `dstone-batch/Jenkinsfile`, `dstone-batchadmin/Jenkinsfile`, `dstone-knowledge/Jenkinsfile` — Maven reactor build → copy artifact/conf/bin to `/app/dstone/<module>` (the module's own directory in this same repo — no separate deploy tree) → redeploy via that module's `bin/stopApp.sh` + `bin/startApp.sh` (`DSTONE_PROFILE=vm`)

Jenkins Job SCM checkout must be the full monorepo root (not a per-module sparse checkout) since builds use `mvn -pl <module> -am` reactor builds and the Docker build context needs `dstone-common` alongside `dstone-boot`.

## Module Ports

| Module | Port | Packaging |
|---|---|---|
| dstone-boot | 7081 | WAR |
| dstone-batch | 6081 | JAR |
| dstone-batchadmin | 5081 | WAR |
| dstone-ai-engine | 8081 | JAR |
| dstone-knowledge | 4081 | JAR |

## Documentation

Whenever changes are made on resources, Check if docs should be changed too.
And Rewrite docs if it is necessary.
