# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**dstone** is a Java 21 / Spring Boot 3.5 enterprise multi-module framework providing:
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

The decryption key is **not** `jasypt.encryptor.password` (jasypt-spring-boot-starter's default) — each module supplies its own `StringEncryptor` bean instead: a `ConfigEnc` class (`net.dstone.<module>.common.config.ConfigEnc` or, for `dstone-ai-engine`, `net.dstone.ai.config.ConfigEnc`) annotated `@EnableEncryptableProperties` with a `@Bean("jasyptStringEncryptor")` that returns `EncUtil.getEncryptor()` (`net.dstone.common.utils.EncUtil`, PBEWithSHA256And128BitAES-CBC-BC, key hardcoded in that class). To generate a new `ENC(...)` value locally without ever typing the plaintext secret into Claude Code's chat (run this in a separate terminal, not via the assistant):

```bash
cd dstone-common && mvn -q exec:java -Dexec.mainClass=net.dstone.common.utils.EncUtil -Dexec.args="<plaintext>"
```

### Security

- **dstone-boot**: Spring Security enabled (`spring.security.enabled: true`), with custom auth handlers in `net.dstone.boot.common.security`, OAuth2 social login (Google/Naver/Kakao), and Redis-based distributed sessions (`dstone:session` namespace).
- **dstone-batch**: Spring Security excluded via `spring.autoconfigure.exclude`.
- **dstone-ai-engine**: Spring Security (and its Actuator management-endpoint security) excluded via `spring.autoconfigure.exclude` in Phase 0 — no auth yet. Real auth (API key / OAuth2 client-credentials) is planned for the `governance` package in a later phase.

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

Spring Batch metadata tables must be created manually from `src/main/resources/schema/*.sql` (`initialize-schema: NEVER`).

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

A Spring AI-based, provider-agnostic engine meant to be built out incrementally and reused across future SI projects — not a single-purpose feature. Root package `net.dstone.ai`, with one package per concern, each mapped to a build phase (see each package's `package-info.java`):

| Package | Purpose | Phase |
|---|---|---|
| `config` | `ChatClient` wiring | 0 |
| `api` | REST controllers (Chat/RAG/Admin API) | 0 |
| `gateway` / `gateway.provider` | LLM provider abstraction (OpenAI/Anthropic/Azure/local vLLM-Ollama), swappable via config | 1 |
| `prompt` | Prompt template management/versioning — SI-specific customization point | 1 |
| `session` | Conversation session/history, reusing `dstone-common`'s Redis infra | 1 |
| `rag` (`ingest`/`embedding`/`retrieval`) | Document ingestion → embedding → vector search | 2 |
| `agent` | Tool/function calling, orchestration, memory | 3 |
| `governance` | Guardrails, PII filtering, rate limiting, cost tracking, auth | 4 |
| `observability` | Token usage, cost, tracing, eval results | 4 |

Phase 0 ships a single hardcoded provider (Anthropic, via `spring-ai-starter-model-anthropic`) behind `POST /api/ai/chat`, to validate the skeleton end-to-end before building out the gateway abstraction. Bulk/offline AI work (re-embedding, periodic eval, session cleanup) is delegated to `dstone-batch` (`@AutoRegJob`) rather than scheduled inside the engine itself, per the existing dstone-batch pattern.

`spring.ai.anthropic.api-key` in `dstone-ai-engine/conf/application.yml` follows the same `ENC(...)` convention as DB passwords (see "Sensitive Config Encryption" above) rather than sourcing from `env.properties` — there is no `ANTHROPIC_API_KEY` env var.

Spring AI is pinned to the 1.x line (`spring-ai.version` in `dstone-ai-engine/pom.xml`) because Spring AI 2.x targets Spring Boot 4 — the whole reactor is still on Spring Boot 3.5.x. Revisit this pin if/when the reactor moves to Boot 4.

## Required Infrastructure

| Infrastructure | Purpose | Modules |
|---|---|---|
| MySQL | Main data store | All (except dstone-ai-engine in Phase 0) |
| Redis | Session store, cache | dstone-boot (dstone-ai-engine from Phase 1) |
| RabbitMQ | Message queue | dstone-boot |
| Anthropic API (or other LLM provider) | Model inference | dstone-ai-engine |

## Key Environment Variables (`conf/env.properties`)

| Variable | Description |
|---|---|
| `APP_HOME` | Application home directory |
| `APP_CONF_DIR` | Config file directory path |
| `DB_HOST` / `DB_PORT` | Database server |
| `REDIS_HOST` / `REDIS_PORT` | Redis server |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | RabbitMQ server (dstone-boot) |
| `FILE_UPLOAD_ROOT` | File upload root path (dstone-boot) |

Jasypt's decryption key is **not** an env var — see "Sensitive Config Encryption" above.

## Cloud Architecture Simulation

The WSL dev environment mirrors a cloud deployment shape: `dstone-boot` and `dstone-ai-engine` run as containerized Pods in a local `kind` Kubernetes cluster (`<module>/Dockerfile`, `<module>/k8s/`), while `dstone-batch` and `dstone-batchadmin` run as VM-style processes controlled by plain shell scripts (`bin/startApp.sh`/`stopApp.sh`/`statusApp.sh` — **no systemd**), and MySQL/Redis/RabbitMQ/Kafka stand in for CSP-managed services outside the cluster. `dstone-ai-engine`'s manifests (`dstone-ai-engine/k8s/`) and Dockerfile follow `dstone-boot`'s pattern exactly (same namespace `dstone`, same `localhost:5000` local registry) — see `docs/cloud-architecture.md` for the full mapping, networking, and CI/CD design.

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
