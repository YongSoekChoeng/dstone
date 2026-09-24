# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**dstone** is a Java 21 / Spring Boot 4.1 (Spring Framework 7) enterprise multi-module framework providing:
- **dstone-common**: Shared library (JAR) — utilities, security, data access, messaging
- **dstone-boot**: Web application framework (WAR) — includes a Java source code static analyzer feature
- **dstone-batch**: Spring Batch processing framework (JAR) — standardized job development
- **dstone-batchadmin**: Web application (WAR) — manages `dstone-batch` jobs (list/detail/register screens, start/stop/restart, CRON auto-scheduling) across one or more `dstone-batch` server instances
- **dstone-ai-engine**: AI & MLOps Core Engine (JAR) — Spring AI-based, provider-agnostic AI serving platform intended for reuse across future SI projects (root package `net.dstone.ai`)

`dstone-boot`, `dstone-batch`, `dstone-batchadmin`, and `dstone-ai-engine` all depend on `dstone-common`.

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
| `common.definition` | Pure records the YAML binds to: `WorkFlowDefinition` (incl. `inputs`/`output`)/`StepDefinition` (`input`/`output`/`forEach`)/`StepOutputDefinition`/`FieldDefinition`/`AgentDefinition`/`McpServerDefinition` — enums live in `common.consts` instead, see below |
| `common.consts` | `Constants` (config-key/reserved-word constants, incl. the execution-context tree names under `Constants.WorkFlow.Context`) plus the enums `StepType` (`AGENT`/`SUPERVISOR`/`ROUTER`/`TOOL`/`APPROVAL`, each tagged with a `Kind` of `AGENT_CALL` or `DETERMINISTIC`), `ToolParse` (`TEXT`/`JSON`/`LINES`) and `McpTransport` (`STDIO`/`SSE`) |
| `common.template` | `Template` — the `{{ path ?? path }}` renderer every step `input`/`forEach`/Workflow `output` goes through, plus reference extraction for load-time validation |
| `common.schema` | `FieldTypes` — `FieldDefinition` type names (`string`/`number`/`integer`/`boolean`/`object`/`list<T>`): validity check, JSON Schema conversion, value type check |
| `common.loader` | `YamlDefinitionLoader` — SnakeYAML + Jackson `convertValue` from `classpath:workflows/**/*.yml` / `agents/**/*.yml` / `mcp/**/*.yml` into the records above (with `${VAR}` substitution) |
| `common.registry` | `WorkFlowRegistry`/`AgentRegistry`/`McpServerRegistry` — id/name → definition, plus `allowedCallers` (tenant) whitelist check. `WorkFlowRegistry` also validates every step's shape and every `{{ }}` reference at startup and fails boot on the first error |
| `common.config` | `Config`/`ConfigCallLog` (AOP call logging)/`ConfigChatClient` (single shared `ChatClient`)/`ConfigRedis`/`ConfigTool` (`@AiTool` bean scanning)/`ConfigMcp` (MCP client connections) |
| `common.session` | `RedisChatMemoryRepository` — Spring AI `ChatMemoryRepository` over Redis (system prompts are inlined in `agents/*.yml`'s `prompt` field — there is no separate prompt registry) |
| `common.security` | `ApiKeyAuthFilter`/`RateLimitFilter`/`CallerContext` |
| `runtime.workflow` | `WorkFlowExecutor` — the sequential/branch/parallel(`forEach`)/loop/approval-wait state machine; it renders each step's `input` template before calling the runner and records the result into the execution context — and `WorkflowTransition` (sealed interface: `NextStep`/`Loop`/`Done`/`Failed`), its per-step "what next" decision. `runtime.workflow.execution` holds the persisted `WorkFlowExecution` (its `context` map = `CONTEXT_JSON` column)/`WorkFlowContext` (context-tree helpers)/`WorkFlowExecutionStatus`/`StepHistoryEntry`/`WorkFlowExecutionStore` |
| `runtime.agent` | `AgentExecutor` — the one place that turns an `AgentDefinition` into a `ChatClient` call — plus `SchemaOutputConverter` (YAML `output.schema` → JSON-Schema format instruction + parse/validate) and the LLM structured-reply schemas `Verdict` (SUPERVISOR) and `RouteDecision` (ROUTER) |
| `runtime.tool` | `ToolExecutor` — the one place a `TOOL` step calls a Tool by name without going through the LLM — plus `ToolOutcome`, the structured success/message reply schema a `@Tool` method can return (a tool that wants to emit structured `data` returns any record/Map instead and the YAML step sets `output.parse: json`) |
| `runtime.step` | `StepRunner`'s IN/OUT (`StepInput` — already-rendered `text` or `arguments` plus `workflowInput`, and `StepOutcome` — a sealed interface: `Success`/`Routed`/`Failure`/`Pending`) alongside its implementations `AgentStepRunner`/`ToolStepRunner`/`ApprovalStepRunner` — per-`StepType` dispatch used by `WorkFlowExecutor` (no `StepType.RAG`/`RagStepRunner` — RAG is either an AGENT's `ragEnabled` advisor or the `RagSearchTool` `@AiTool`) |
| `common.rag` | `RagRetrievalChain` — the only place that turns an already-ingested `VectorStore` into search results/an `Advisor` (`buildAdvisor()`/`search()`); ingest/delete is `api.service.EmbedService` instead, gated by `dstone.ai.rag.enabled` |
| `tools` | `@AiTool` implementations: `sample`/`sql`/`shell`/`python`/`http`/`jenkins`/`rag` (`RagSearchTool`) |
| `api` | `ChatController` (`GET /api/ai/chat` - agent list; `POST /api/ai/chat[/stream]`, one Agent call), `WorkFlowController` (`GET /api/ai/workflow` - id+description list; `POST /api/ai/workflow/{id}/execute`, sync; `/submit`+`/status/{executionId}`, async — all three entry paths go through `api.service.WorkFlowExecutionService`), `WorkFlowExecutionController`, `EmbedController` (`/api/ai/embed/documents` - ingest/delete/list), `RagController` (`POST /api/ai/rag/search` - search preview) |

Step-to-step data flow (redesigned 2026-09-24): every step declares what it takes in `input` (a string for AGENT/SUPERVISOR/ROUTER = the LLM user message, a map for TOOL = the tool arguments; `{{input.x}}`/`{{steps.<id>.text|data.<k>|input.<k>|error|items}}`/`{{previous.text}}`/`{{item}}`, `a ?? b` fallback, nothing else) and what `data` it emits in `output` (AGENT `schema`, TOOL `parse`). All state lives in one execution-context tree (`input`/`steps`/`previous`/`approvals`); a missing reference fails the step, a runner exception fails the whole run. `${VAR}` in YAML is a separate load-time env substitution. MCP tool calls are serialized per server (`ConfigMcp.SerializedToolCallback`) because one STDIO client can't take concurrent requests. Design record: `docs/temp/dstone-ai-engine-refactory-20260924-1.md`.

Full architecture, the YAML field reference (§7) with copy-paste templates for every step type/Agent/MCP server (§8), and the API/config reference live in `docs/09.dstone-ai-engine.md` — it describes the current state only, no change history.

The engine shares a single LLM provider (`spring.ai.model.chat`, Anthropic by default) across every Agent — there is no per-Agent provider override, but `AgentDefinition.model` lets a Workflow/Agent YAML override just the model within that shared provider (`runtime.agent.AgentExecutor.buildSpec()` applies it as a per-request `ChatOptions`; an unset `model` falls back to `spring.ai.{provider}.chat.options.model`, and a model name from a different provider than the active one only fails at that Agent's first call, not at startup). `spring.ai.anthropic.api-key` in `dstone-ai-engine/conf/application.yml` follows the same `ENC(...)` convention as DB passwords (see "Sensitive Config Encryption" above) rather than sourcing from `env.properties`. Spring AI is on the 2.x line (`spring-ai.version` in `dstone-ai-engine/pom.xml`); Azure OpenAI is not supported as a chat provider (Spring AI 2.x dropped `spring-ai-starter-model-azure-openai` — Azure remains vector-store-only).

RAG (`dstone.ai.rag.enabled`, default `false`) needs Postgres+pgvector and an embedding provider (`spring.ai.model.embedding: openai | ollama` — not `anthropic`, which has no embeddings API); this environment uses a local Ollama (`bge-m3`, 1024 dims, see `docs/software/14.ollama.md`). `api.service.EmbedService` tags every ingested chunk with a `tenant` metadata field keyed off `CallerContext`'s caller and it plus `common.rag.RagRetrievalChain` force a matching filter on search/delete/RAG-augmented chat, so one caller's documents never leak into another's — a no-op when `dstone.ai.security.auth.enabled=false` (caller is always null).

Tool calling has no infrastructure dependency — it's plain Java executed on demand. `common.annotation.AiTool` (meta-annotated `@Component`) marks a class containing `@org.springframework.ai.tool.annotation.Tool` methods; `common.config.ConfigTool` scans for `@AiTool` beans via `ApplicationContext.getBeansWithAnnotation` and wraps them into a `ToolCallbackProvider`, filterable per caller via `dstone.ai.tool.allowed-by-caller`. An `AGENT`/`SUPERVISOR` Workflow step lets the LLM pick tools through Spring AI's own tool-calling loop; a `TOOL` step calls one deterministically by name via `runtime.tool.ToolExecutor` (no LLM involved) — both paths go through the same `ConfigTool` whitelist. `tools.shell.ShellExecTool`/`tools.python.PythonExecTool`/`tools.http.HttpCallTool` have no separate on/off flag — they only ever run a whitelisted script path or hit a whitelisted host (`dstone.ai.tool.shell.allowed-commands` / `.python.allowed-scripts` / `.http.allowed-hosts`, empty by default), never an LLM-supplied raw command/URL body. An empty whitelist is what keeps them inert by default; there used to be a `dstone.ai.tool.{shell,python,http}.enabled` flag documented alongside the whitelist, but it was never actually wired into the code (no `@ConditionalOnProperty` on the classes), so it was removed rather than implemented — the whitelist alone is the real gate.

Governance (PII/sensitive-word guardrails, usage logging/quota) is out of scope for this redesign, but two seams exist so it can be re-added without touching call sites: `common.config.ConfigChatClient.chatClient(...)` takes a plain `List<Advisor>` (Spring auto-collects any `@Bean Advisor`, so a governance module just adds one), and `runtime.tool.ToolExecutor.call(...)` is the single choke point every `TOOL` step call passes through.

Verified live end-to-end on 2026-09-15 (real Anthropic calls, local Redis/PostgreSQL+pgvector/Ollama): `POST /api/ai/chat` with a plain question and with a tool-calling question (`getCurrentDateTime`), and the `oracle-to-postgresql` sample Workflow (`analyze`→`convert`→`validate` AGENT/AGENT/TOOL steps) both synchronously (`/execute`) and via the async `/submit`+`/status/{jobId}` contract — an Oracle `NVL(...)`/`ROWNUM` query converted correctly to PostgreSQL `COALESCE(...)`/`LIMIT` and passed syntax validation on the first pass in both cases. (`oracle-to-postgresql` itself was replaced on 2026-09-21 by a broader 9-Agent/13-Workflow test-coverage set under `resources/{agents,workflows}/` — see `docs/09.dstone-ai-engine.md` §7.8 for the current sample list.)

## Required Infrastructure

| Infrastructure | Purpose | Modules |
|---|---|---|
| MySQL | Main data store | dstone-boot, dstone-batch, dstone-batchadmin |
| Redis | Session store, cache | dstone-boot (dstone-ai-engine from Phase 1) |
| RabbitMQ | Message queue | dstone-boot |
| Anthropic API (or other LLM provider) | Chat model inference | dstone-ai-engine |
| PostgreSQL + pgvector | RAG vector store | dstone-ai-engine (Phase 2, when `dstone.ai.rag.enabled=true`) |
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

The WSL dev environment mirrors a cloud deployment shape: `dstone-boot` and `dstone-ai-engine` run as containerized Pods in a local `kind` Kubernetes cluster (`<module>/Dockerfile`, `<module>/k8s/`), while `dstone-batch` and `dstone-batchadmin` run as VM-style processes controlled by plain shell scripts (`bin/startApp.sh`/`stopApp.sh`/`statusApp.sh` — **no systemd**), and MySQL/Redis/RabbitMQ/Kafka stand in for CSP-managed services outside the cluster. `dstone-ai-engine`'s manifests (`dstone-ai-engine/k8s/`) and Dockerfile follow `dstone-boot`'s pattern exactly (same namespace `dstone`, same `localhost:5000` local registry) — see `docs/04.cloud-architecture.md` for the full mapping, networking, and CI/CD design.

## CI/CD

Jenkins pipelines are defined in:
- `dstone-boot/Jenkinsfile`, `dstone-ai-engine/Jenkinsfile` — Maven reactor build → Docker build/push to a local registry (`localhost:5000`) → deploy to the `dstone` namespace in `kind` via `kubectl`
- `dstone-batch/Jenkinsfile`, `dstone-batchadmin/Jenkinsfile` — Maven reactor build → copy artifact/conf/bin to `/app/dstone/<module>` (the module's own directory in this same repo — no separate deploy tree) → redeploy via that module's `bin/stopApp.sh` + `bin/startApp.sh` (`DSTONE_PROFILE=vm`)

Jenkins Job SCM checkout must be the full monorepo root (not a per-module sparse checkout) since builds use `mvn -pl <module> -am` reactor builds and the Docker build context needs `dstone-common` alongside `dstone-boot`.

## Module Ports

| Module | Port | Packaging |
|---|---|---|
| dstone-boot | 7081 | WAR |
| dstone-batch | 6081 | JAR |
| dstone-batchadmin | 5081 | WAR |
| dstone-ai-engine | 8081 | JAR |

## Documentation

Whenever changes are made on resources, Check if docs should be changed too.
And Rewrite docs if it is necessary.
