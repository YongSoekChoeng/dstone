# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**dstone** is a Java 21 / Spring Boot 3.5 enterprise multi-module framework providing:
- **dstone-common**: Shared library (JAR) — utilities, security, data access, messaging
- **dstone-boot**: Web application framework (WAR) — includes a Java source code static analyzer feature
- **dstone-batch**: Spring Batch processing framework (JAR) — standardized job development
- **dstone-batchadmin**: Web application (WAR) — manages `dstone-batch` jobs (list/detail/register screens, start/stop/restart, CRON auto-scheduling) across one or more `dstone-batch` server instances

`dstone-boot`, `dstone-batch`, and `dstone-batchadmin` all depend on `dstone-common`.

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

# dstone-batchadmin (port 7082)
java -jar target/dstone-batchadmin.war
```

Docker deployment scripts are in `dstone-boot/docs/docker/` and `dstone-batch/docs/docker/` (not yet added for `dstone-batchadmin`).

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

The decryption key is `jasypt.encryptor.password` in `conf/env.properties`.

### Security

- **dstone-boot**: Spring Security enabled (`spring.security.enabled: true`), with custom auth handlers in `net.dstone.boot.common.security`, OAuth2 social login (Google/Naver/Kakao), and Redis-based distributed sessions (`dstone:session` namespace).
- **dstone-batch**: Spring Security excluded via `spring.autoconfigure.exclude`.

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
- `common` → `dstone_batchadmin` schema (static, own login users/`TB_ADMIN_USER`, server registry/`TB_BATCH_SERVER`, job metadata/`TB_BATCH_JOB`)
- `batch` → the `RoutingDataSource` described above (dynamic, one target per registered server)

Since `RoutingDataSource` prevents MyBatis's `databaseIdProvider` from resolving per-query, MySQL/PostgreSQL differences (mainly paging syntax) are handled with an explicit `DBMS_TYPE` query parameter and `<choose>` branches in `sqlmap/job/BatchJobExecDao.xml`, rather than the `databaseId` mapper attribute.

Registered Job metadata (`TB_BATCH_JOB.JOB_NM`) must match the `@AutoRegJob(name=...)` value on the target `dstone-batch` server exactly — `dstone-batchadmin` cannot create new Job logic, only manage metadata and trigger the existing REST API. `common/scheduler/JobScheduleManager.java` uses Spring's built-in `ThreadPoolTaskScheduler` + `CronTrigger` (no Quartz) to auto-start jobs whose `SCHEDULE_USE_YN='Y'`.

Security is simplified vs. `dstone-boot`: login is required (`TB_ADMIN_USER`, `BCryptPasswordEncoder`) but there is no per-URL role/permission check — it's a single-role internal admin tool.

## Required Infrastructure

| Infrastructure | Purpose | Modules |
|---|---|---|
| MySQL | Main data store | All |
| Redis | Session store, cache | dstone-boot |
| RabbitMQ | Message queue | dstone-boot |

## Key Environment Variables (`conf/env.properties`)

| Variable | Description |
|---|---|
| `APP_HOME` | Application home directory |
| `APP_CONF_DIR` | Config file directory path |
| `DB_HOST` / `DB_PORT` | Database server |
| `REDIS_HOST` / `REDIS_PORT` | Redis server |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | RabbitMQ server (dstone-boot) |
| `FILE_UPLOAD_ROOT` | File upload root path (dstone-boot) |
| `jasypt.encryptor.password` | Jasypt decryption key |

## CI/CD

Jenkins pipelines are defined in:
- `dstone-batch/Jenkinsfile` — builds with `mvn clean package -DskipTests`, deploys via Docker Compose
- `dstone-boot/Jenkinsfile` — same pattern

## Module Ports

| Module | Port | Packaging |
|---|---|---|
| dstone-boot | 7081 | WAR |
| dstone-batch | 6081 | JAR |
| dstone-batchadmin | 7082 | WAR |
