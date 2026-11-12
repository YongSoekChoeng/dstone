# localcommandcaveatTh

> 생성일시: 2026-11-12 16:55

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-ai-engine 에서 특정 어플리케이션의 데이터소스를 찾아서 연결정보를 추적하고 테이블쿼리를 할 수 있게 하려면 어떤 Tool을 만들어서 붙여야 할까?

---

## 답변

dstone-ai-engine에 **DB 전용 범용 Tool 하나를 새로 만들고**, 데이터소스를 찾는 일은 **기존 `KnowledgeTool`에 메소드 하나를 더해서** 맡기면 됩니다. 이미 있는 것과 새로 만들 것을 나눠 보면 다음과 같습니다.

## 1. 데이터소스 찾기: `KnowledgeTool` 확장

dstone-knowledge는 이미 분석 단계에서 데이터소스 정보를 모으고 있습니다.

- `analysis_config`: `spring.datasource.url = jdbc:...` 같은 설정 키 (`ResourceReaders`, pass04)
- `analysis_resource`: `SPRING_BEAN: dataSource = BasicDataSource` 같은 빈 정의
- 조회 API: `ResourceController`의 `/api/revisions/{rev}/configs`, `/resources`

그래서 `KnowledgeTool`에 `knowledgeDatasources(projectId)`를 추가하면 됩니다. 이 메소드는 위 두 API를 호출해 데이터소스 단위로 묶은 결과(이름, driver, url, username, 정의 위치 `파일:줄`)를 돌려줍니다.

다만 이 결과만으로는 실제로 접속할 수 없습니다.
- 비밀번호는 knowledge 규칙에 따라 `****`로 저장되어 있습니다.
- `${DB_HOST}` 같은 플레이스홀더나 JNDI 이름은 값이 풀리지 않은 채로 남습니다.

따라서 이 단계의 역할은 **"어떤 DB를 쓰는지 알아내는 것"까지**로 정합니다.

## 2. 접속과 조회: 새 범용 Tool `DbQueryTool`

`net.dstone.ai.tools.db.DbQueryTool`을 `@AiTool`로 만듭니다. Agent도 부를 수 있어야 하므로 `stepOnly`는 붙이지 않습니다.

**접속 정보는 LLM에게서 받지 않습니다.** ai-engine 설정에 별칭으로 등록해 두고, LLM은 별칭만 넘깁니다. knowledge의 `DbCommentReader`가 쓰는 방식과 같습니다.

```yaml
dstone.ai.tool.db.connections:
  sample-app:
    url: jdbc:mysql://...
    username: ...
    password: ENC(...)        # EncPropertyEnvironmentPostProcessor가 풀어 줌
    max-rows: 200
    query-timeout-seconds: 30
```

| 메소드 | 하는 일 |
|---|---|
| `dbListConnections()` | 등록된 별칭과 url 목록(비밀번호 제외). 1단계에서 찾은 url과 맞춰 볼 때 씀 |
| `dbListTables(alias, schema, pattern)` | JDBC `DatabaseMetaData.getTables`로 조회해 DB 종류별 SQL이 필요 없음 |
| `dbDescribeTable(alias, table)` | `getColumns`로 컬럼, 타입, 코멘트, PK 조회 |
| `dbQuery(alias, sql)` | 조회 쿼리만 실행 |

`dbQuery`에는 안전장치를 겹겹이 둡니다.
- JSQLParser로 문장이 하나뿐이고 `Select`인지 확인합니다. `SqlSyntaxTool`과 같은 방식입니다.
- `connection.setReadOnly(true)`로 연결합니다. 가능하면 DB 계정도 읽기 전용으로 만듭니다.
- `setMaxRows`와 `setQueryTimeout`을 걸고, 결과가 길면 잘라서 "잘림" 표시를 붙입니다.
- `password`, `ssn` 같은 민감한 컬럼명은 값을 `****`로 가립니다.
- 실행한 SQL과 별칭은 로그로 남깁니다.

## 3. Workflow에서의 흐름

```
knowledgeDatasources(projectId)   → 어떤 DB를 쓰는지(url) 알아냄
dbListConnections()               → 그 url에 해당하는 등록 별칭을 찾음 (없으면 "등록 필요"로 끝냄)
dbListTables / dbDescribeTable    → 스키마 확인
knowledgeTableUsage(table)        → 코드에서 그 테이블을 어떻게 쓰는지 확인
dbQuery(alias, select ...)        → 실제 데이터 확인
```

순서는 Agent 프롬프트(`.md`)와 Workflow 정의에만 적습니다. 특정 Workflow 전용 Tool은 만들지 않습니다.

## 같이 챙길 것

- **네트워크**: ai-engine은 kind Pod 안에서 돕니다. 대상 DB가 Pod에서 닿는지 확인해야 하고, `KNOWLEDGE_HOST`처럼 `172.18.0.1` 경유가 필요할 수 있습니다.
- **JDBC 드라이버**: ai-engine pom에 대상 DB 드라이버(MySQL, PostgreSQL, Oracle 등)를 넣어야 합니다.
- **커넥션 관리**: 별칭마다 작은 HikariCP 풀을 지연 생성해서 캐시하면 됩니다. batchadmin의 `BatchServerDataSourceRegistry`와 비슷한 구조입니다.
- **MCP 서버는 대안이 되기 어렵습니다**: `conf/definitions/mcp`에 postgres MCP 같은 DB MCP 서버를 붙이는 방법도 있습니다. 하지만 DB마다 서버를 띄워야 하고, 조회 전용 강제나 행 제한 같은 거버넌스를 걸기 어렵습니다. knowledge 결과와 연결하기도 어려워 권하지 않습니다.

이 구성으로 구현할까요? 순서는 `DbQueryTool`(+설정, 테스트) → `knowledgeDatasources` → `docs/09` 문서 반영으로 진행하면 됩니다.
