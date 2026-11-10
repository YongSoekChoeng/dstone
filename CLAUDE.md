# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**dstone** is a Java 21 / Spring Boot 4.1 (Spring Framework 7) enterprise multi-module framework providing:
- **dstone-common**: Shared library (JAR) — utilities, security, data access, messaging
- **dstone-boot**: Web application framework (WAR) — includes a Java source code static analyzer feature
- **dstone-batch**: Spring Batch processing framework (JAR) — standardized job development
- **dstone-batchadmin**: Web application (WAR) — manages `dstone-batch` jobs (list/detail/register screens, start/stop/restart, CRON auto-scheduling) across one or more `dstone-batch` server instances
- **dstone-ai-engine**: AI & MLOps Core Engine (JAR) — Spring AI-based, provider-agnostic AI serving platform intended for reuse across future SI projects (root package `net.dstone.ai`)
- **dstone-knowledge**: Java static analysis → Knowledge Graph → RAG data platform (JAR, root package `net.dstone.knowledge`) — M0–M8 done; M9 migration: the `dstone-ai-engine` half is done, the `dstone-boot` analyzer half is next — destructive, compare feature by feature and get the user's confirmation first

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
- **dstone-knowledge**: Spring Security excluded the same way. `common.security.ApiKeyAuthFilter` (`X-API-Key`, `dstone.knowledge.security.auth.enabled`, default off, `/api/system/health` exempt) sets the caller in `CallerContext`; the caller is the tenant of uploaded general documents only — code analysis results are not split per caller.
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

Spring AI 기반, provider에 독립적인 AI 엔진. **Workflow → Step → Agent → Tool** 모델: Java(`runtime.workflow.WorkFlowExecutor`)가 순서·분기·forEach·루프·승인 대기를 제어하고, 각 Step 안의 판단은 LLM이 한다. 무엇을 하는지는 Java가 아니라 `conf/definitions/` 아래 정의 파일에 있다 (`workflows/**/<id>.yml`, `agents/**/<id>.md` = front matter + 프롬프트 본문, `skills/**/<name>/SKILL.md`, `mcp/**/<id>.yml`; 입출력은 Picoschema). 루트 패키지 `net.dstone.ai`.

반드시 지킬 규칙 (상세 근거는 아래 문서):
- **YAML에 로직을 넣지 않는다.** 표현식은 값을 읽는 경로(`${input…}`/`${state.x.y}`/`${item…}`/`:기본값`)뿐이다. 필터·검증·문서 렌더링은 TOOL step이 부르는 Java Tool로 만든다.
- **Workflow 전용 Tool 클래스를 만들지 않는다.** 순서: 정의 파일 수정 → 기존 범용 Tool → 새 범용 Tool → Workflow 단순화. 엔진 전용 Tool은 `@AiTool(stepOnly = true)`.
- Step 결과는 `save`/`saveError`/`saveInput`/`saveEach`로 이름을 붙여 `state`에 저장한다. 모든 이름은 Workflow `state:`에 선언해야 하며, 저장되지 않은 결과는 읽을 수 없다.
- `ChatClient` 호출은 `AgentExecutor`를 거친다. 직접 부르는 새 코드는 `ConfigChatClient.requestOptions()`를 붙인다 (Spring AI 2.0.1에서 timeout이 기본 60초로 덮어써짐).
- `Advisor`를 Spring bean으로 만들지 않는다 (`defaultAdvisors` 목록을 대체해 `LlmLoggerAdvisor`가 빠진다). 호출마다 `AgentExecutor.buildSpec()`에서 붙인다.
- 새 DB 코드는 MyBatis: `resources/sqlmap/` 아래 `*Dao.xml` + `common.biz.BaseDao` 상속.
- 정의 파일 검증은 부팅 시 수행된다. 정의 개수를 테스트에서 단정하지 않는다.
- 테스트: `mvn test -DskipTests=false` (pom에서 기본 skip). 실제 LLM은 `-Ddstone.ai.test.llmcall=true`. 테스트는 main의 `conf/definitions`와 `conf/application.yml`을 그대로 쓴다 (테스트 전용 정의 파일 없음).
- Workflow 실행은 `WorkFlowThreadPools`의 풀에서 `QUEUED`→`RUNNING`으로 돈다. 상태 전이는 조건부 UPDATE(`markQueued`/`markStarted`)로만 하고, 고아 실행 정리는 heartbeat 기준이라 같은 DB에 시험 사본을 띄워도 된다(`ai-engine-features.md` "Concurrent use").
- 실행 중인 `target/*.jar|war`를 그 자리에서 다시 빌드하지 않는다. 사본을 다른 포트로 띄워 시험한다.

필요할 때 읽을 문서 (`.claude/docs/`):

| 파일 | 읽을 때 |
|---|---|
| `ai-engine-packages.md` | 패키지별 역할, 정의 파일 키(Agent/Workflow/Step), 레지스트리 검증 규칙 |
| `ai-engine-dataflow.md` | Step 간 데이터 흐름(state 모델), Tool 호출 구조, timeout, 거버넌스 확장 지점 |
| `ai-engine-features.md` | 일시정지/재개/되감기, `requiredTools`, Step 파일 기록·문서 팝업, Agent별 provider, RAG 위임, Skill·백그라운드 Sub Agent, 무재시작 정의 편집(디자이너) |
| `ai-engine-testing.md` | 서버 없이 Workflow 테스트(`WorkflowTestBase`, `ScriptedChatModel`, `stepper`), 문서 위치 |
| `ai-engine-tuning.md` | Tool 호출 예산·결과 압축·reasoning, `sm-workflow` 측정과 개선 내역 |
| `ai-engine-pilot-workflow.md` | `pilot-workflow` step id, 신규개발/일반 경로, 측정 결과 |
| `ai-engine-history.md` | 형식 변경 이력과 과거 검증 기록 |

현재 상태의 전체 설명은 `docs/09.dstone-ai-engine.md` (§7 YAML 필드 레퍼런스, §8 템플릿).

### dstone-knowledge: Java Analysis → Knowledge Graph → RAG

Java 애플리케이션(Spring과 레거시 plain Java, JSP, WebSquare/Nexacro 화면 포함)을 분석 모델로 만든 뒤 Knowledge Graph와 RAG 청크로 투영한다. 현재 상태와 마일스톤은 `docs/11.dstone-knowledge.md`.

반드시 지킬 규칙:
- **패키지**: 분석 코드는 `analysis.passNN.<단계>`(번호 = 실행 순서, 실제 순서는 Pass의 `order()` — 단계를 끼워 넣으면 둘을 같이 맞춘다), 단계 공용 도구는 `analysis.common`, 실행 틀은 `analysis.job`. 임베딩·검색은 `rag`.
- **DB 기반 파이프라인.** 프로젝트 전체를 메모리에 올리지 않는다. Pass는 파일 하나씩 처리하고 행을 쓴 뒤 AST를 버린다.
- 새 Pass = 새 `AnalysisPass` bean. 재실행에 멱등이어야 하고 루프 안에서 `context.checkCancelled()`를 부른다. 파일 단위 Pass는 무거운 작업을 `prepare`(DB 쓰기·트랜잭션 없음)에, DB 쓰기를 `write`에 둔다.
- `sqlSessionBatch` 트랜잭션 안에서 `sqlSessionCommon`을 쓰지 않는다.
- 프레임워크 관례(애노테이션, SQL 이름을 받는 메소드, 상위 타입, 화면 함수)는 먼저 규칙 파일에 **코드 모양 그대로** 적는다(`conf/rules/README.md`, `docs/11-2`). 폴더가 우선순위다: `projects/{프로젝트ID}/` > `common/` > `guess/`. 규칙 파일의 절(`layer:` `entry:` `sql:` …)마다 처리기가 하나다(`LayerResolver`, `EntryFinder`, `SqlLinker` …). 규칙으로 안 되는 것만 새 `SemanticStep` bean을 만들어 `SemanticPass.steps()`에 넣고, Java를 다시 파싱하지 말고 `analysis_*` 테이블에서 SQL로 도출한다. 규칙으로 만든 결과에는 `ruleAt`(파일:줄)을 남긴다. 지원 범위는 `docs/11` 4.7.6절.
- 이름(statement, 거래 ID, 화면 경로)은 **계산하고 추측하지 않는다.** 따옴표 리터럴만 인정한다.
- 비밀값은 저장하지 않는다(`****`). XML은 모두 `common.util.SafeXml`로 읽는다. JSQLParser는 fast mode, 호출 스레드에서만.
- `analysis_*`만 원본이다. `kg_*`는 VIEW, `rag_*`는 언제든 재생성. 임베딩 키는 `(content_hash, model)`이며 모델명·차원은 `RagSettings`로 읽는다.
- 대화형 요청에서 `EmbeddingModel`을 부를 때는 `rag.EmbeddingGate`를 거친다.
- Lombok을 쓰지 않는다 (저장소 전체 규칙).
- Pass를 바꾸면 증분 분석과 전체 분석 결과가 같아야 한다 (`knowledge-search-incremental.md`의 비교 절차).
- 4081 포트 인스턴스가 이미 떠 있을 수 있다. 시험 인스턴스는 다른 포트, `-Ddstone.knowledge.rag.embedding.enabled=false`, `bin`이 아닌 작업 디렉터리에서 띄운다.

필요할 때 읽을 문서 (`.claude/docs/`):

| 파일 | 읽을 때 |
|---|---|
| `knowledge-core.md` | 설계 원칙, Job 모델, DECLARE/타입 해석/RESOLVE/LINK/SEMANTIC/RAG Pass, 비Java 리소스, 행 방지, DB 접근 |
| `knowledge-screens-rich-client.md` | WebSquare/Nexacro 화면, 호출·필드·오픈 규칙, 공통 스크립트, Query XML, 설정 기반 레이어, TRANSACTION 엔드포인트 |
| `knowledge-search-incremental.md` | 하이브리드 검색, 영향도 분석, 일반 문서 업로드, `rerunFrom`, 증분 분석, 리비전 비교, 보관 정책 |
| `knowledge-ui.md` | `dstone-boot`의 Knowledge 화면, 프록시, 콘솔 디자인, 노드 맵, 테이블 한글명 우선순위 |


## Required Infrastructure

| Infrastructure | Purpose | Modules |
|---|---|---|
| MySQL | Main data store | dstone-boot, dstone-batch, dstone-batchadmin |
| Redis | Session store, cache | dstone-boot (dstone-ai-engine from Phase 1) |
| RabbitMQ | Message queue | dstone-boot |
| Anthropic API (or other LLM provider) | Chat model inference | dstone-ai-engine |
| PostgreSQL | Workflow execution history (`dstone_ai` DB, MyBatis) | dstone-ai-engine |
| PostgreSQL + pgvector | Analysis results, graph, RAG chunks/embeddings (`dstone_knowledge` DB) | dstone-knowledge |
| dstone-knowledge | RAG document storage/embedding/search and the `KnowledgeTool` | dstone-ai-engine (when `dstone.ai.rag.enabled=true` or the Tool is used) |
| Ollama (or OpenAI) | Embedding model inference for code RAG (analysis still completes without it; chunks wait in the queue) | dstone-knowledge |

## Key Environment Variables (`conf/env.properties`)

| Variable | Description |
|---|---|
| `APP_HOME` | Application home directory |
| `APP_CONF_DIR` | Config file directory path |
| `DB_HOST` / `DB_PORT` | Database server |
| `REDIS_HOST` / `REDIS_PORT` | Redis server |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | RabbitMQ server (dstone-boot) |
| `FILE_UPLOAD_ROOT` | File upload root path (dstone-boot) |
| `KNOWLEDGE_HOST` / `KNOWLEDGE_PORT` | dstone-ai-engine and dstone-boot: where `dstone-knowledge` listens (`localhost` locally, the kind bridge gateway `172.18.0.1` in `env-k8s.properties`); used by `dstone.ai.tool.knowledge.base-url` (ai-engine Tool and RAG) and `interface.knowledge.base-url` (dstone-boot screens) |
| `MCP_STDIO_COMMAND_PREFIX` | dstone-ai-engine only, Windows-only: tokens prepended to every STDIO MCP server's command (e.g. `cmd.exe /c`) so `.cmd`/`.bat`-based commands like `npx` can be launched — `ProcessBuilder` can't run them directly on Windows without going through the command interpreter. Unset on Linux/WSL/k8s. |

Jasypt's decryption key is **not** an env var — see "Sensitive Config Encryption" above.

## Cloud Architecture Simulation

The WSL dev environment mirrors a cloud deployment shape: `dstone-boot` and `dstone-ai-engine` run as containerized Pods in a local `kind` Kubernetes cluster (`<module>/Dockerfile`, `<module>/k8s/`), while `dstone-batch`, `dstone-batchadmin` and `dstone-knowledge` run as VM-style processes controlled by plain shell scripts (`bin/startApp.sh`/`stopApp.sh`/`statusApp.sh` — **no systemd**). The `dstone-boot`, `dstone-knowledge` and `dstone-ai-engine` scripts share one shape (aligned 2026-10-25; plus `.bat` twins for Windows, profile `local`): stdout goes to `/dev/null`, so **logs exist only where `conf/log4j2.xml` puts them** (`${APP_HOME}/LOGS/<module>/execution/execution.log` — no more `logs/<module>.out`), and the PID file `bin/application.pid` is written by the app itself (`ApplicationPidFileWriter`), so every script `cd`s to `bin` first; `DSTONE_PROFILE` picks the profile; `startApp.sh` refuses (exit 1) when that PID is alive (a second `dstone-knowledge` would fail running analysis jobs); `stopApp.sh` waits up to 30 s and, if the process is still alive, keeps the PID file and exits 1 instead of `kill -9`. Repo-root `bin/startApps.sh`/`stopApps.sh`/`statusApps.sh` handle the three together (start knowledge → ai-engine → boot, stop in reverse). `dstone-batch`/`dstone-batchadmin` keep their own scripts (script-written PID, `logs/*.out`) and now also start the app from `bin`. A crash before log4j2 starts leaves no trace — run the commented-out foreground line at the end of `startApp.sh` to see it, and MySQL/Redis/RabbitMQ/Kafka stand in for CSP-managed services outside the cluster. `dstone-ai-engine`'s manifests (`dstone-ai-engine/k8s/`) and Dockerfile follow `dstone-boot`'s pattern exactly (same namespace `dstone`, same `localhost:5000` local registry) — see `docs/04.cloud-architecture.md` for the full mapping, networking, and CI/CD design.

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

Keep this file short (it is loaded on every session). Record change history, measurements and long design notes in `.claude/docs/*.md` (or the module docs), not here — add only a rule or a one-line pointer to this file.

## Korean Writing Policy

한국어 문장이나 문서를 새로 작성하거나 수정할 때 다음 규칙을 적용한다.

1. `humanizer` Skill의 한국어 작성 지침을 따른다.
2. 자연스러운 한국어 어순과 조사, 어미를 사용한다.
3. 영어 기술 문장을 직역한 듯한 번역투를 피한다.
4. 불필요하게 장황하거나 반복적인 표현을 사용하지 않는다.
5. 사용자가 읽기 편한 간결하고 명확한 문장을 작성한다.
6. Java 주석과 JavaDoc은 기술적 정확성과 업무 의미를 보존한다.
7. 한국어 작성 또는 교정 작업에서 `humanizer` Skill이 관련되면
   자동으로 호출하여 적용한다.
8. 코드, SQL, API, 식별자 등 기술 요소는 문체 개선을 이유로 변경하지 않는다.

이 규칙은 Java 주석, JavaDoc, Markdown 문서, README,
설계서, 오류 메시지 및 사용자에게 보여 주는 한국어 설명에 적용한다.
