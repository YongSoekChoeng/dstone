# localcommandcaveatTh

> 생성일시: 2026-11-12 17:11

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

---

## 질문

응 구현해줘.

---

## 답변

DB 조회 Tool의 설정 방식과 의존성을 확인하는 중입니다. 곧 코드 작성으로 넘어갑니다.

---

## 답변

먼저 dstone-knowledge의 `/configs`·`/resources` API에 값 조건(`value`)을 추가합니다.

---

## 답변

다음은 ai-engine 쪽입니다. JSQLParser 4.7의 Select 구조를 확인합니다.

---

## 답변

H2를 테스트용으로 쓸 수 있습니다. 이제 코드를 작성합니다.

---

## 답변

Now the connection settings class and SQL guard.

---

## 답변

Now the main Tool.

---

## 답변

이제 `KnowledgeTool`에 `knowledgeDatasources`를 추가합니다. 먼저 placeholder 컬럼 형식을 확인합니다.

---

## 답변

yml 설정 예시를 넣고 컴파일합니다.

---

## 답변

컴파일 통과. 테스트를 작성합니다.

---

## 답변

DB Tool 테스트 11건이 모두 통과했습니다. 이제 `knowledgeDatasources`를 실행 중인 dstone-knowledge(4081)에 붙여서 확인해 봅니다.

---

## 답변

실 서버 확인 전에, 응답을 흉내 낸 가짜 `KnowledgeClient`로 고정 테스트를 하나 만듭니다.

---

## 답변

트랜잭션 매니저가 섞여 나오는 건 군더더기라서 빼겠습니다.

---

## 답변

실제 dstone-knowledge(4081)의 분석 결과로 한 번 돌려 봅니다(임시 테스트, 확인 후 삭제).

---

## 답변

testApp에서 `${Globals.Url}`이 안 풀렸습니다. 실제 설정에 어떤 키가 있는지 봅니다.

---

## 답변

testApp은 분석 결과에 설정 파일이 아예 없어서(configs 0건) 변수를 풀 수 없었습니다. 동작은 맞습니다. 이제 문서를 반영합니다.

---

## 답변

Now the doc edits.

---

## 답변

문서 반영을 마쳤습니다. 다른 테스트가 깨지지 않았는지 ai-engine 전체 테스트를 돌립니다.

---

## 답변

ai-engine 테스트 154건 모두 통과했습니다(LLM 호출 17건은 기본 skip). knowledge 쪽은 테스트를 돌리고, 바뀐 SQL을 실제 DB에도 실행해 봅니다.

---

## 답변

knowledge pom은 테스트 건너뛰기가 고정되어 있어서, 바뀐 SQL을 DB에 직접 실행해 확인합니다.

---

## 답변

구현을 마쳤습니다. 계획대로 두 부분으로 나눴습니다. `KnowledgeTool`이 데이터소스를 찾고, 새 `DbQueryTool`이 실제로 접속해 조회합니다. ai-engine 테스트는 새로 넣은 14건을 포함해 154건이 모두 통과했습니다(실제 LLM을 부르는 17건은 원래대로 건너뜀). 커밋은 하지 않았습니다.

## 추가한 Tool

**`DbQueryTool`** (`tools/db/`)

| Tool | 하는 일 |
|---|---|
| `dbListConnections` | 등록된 DB 별칭 목록(주소, 계정, 설명) |
| `dbListTables` | 테이블과 뷰 목록 |
| `dbDescribeTable` | 컬럼, 타입, PK, 코멘트 |
| `dbQuery` | SELECT 한 문장 실행 |

- **접속 정보는 LLM에게 받지 않습니다.** 관리자가 `dstone.ai.tool.db.connections.{별칭}`에 url, 계정, `ENC(...)` 비밀번호를 등록하고, LLM은 별칭만 넘깁니다. 등록된 DB가 없으면 모든 호출이 실패로 끝납니다. 즉 별칭을 등록해야 이 Tool이 켜집니다.
- **쓰기 차단은 세 겹입니다.**
  1. 실행 전에 `DbSqlGuard`가 JSQLParser로 SELECT 한 문장인지 확인합니다. 여러 문장, DML·DDL, `INTO`, `FOR UPDATE`는 거부합니다.
  2. 읽기 전용 연결로 실행합니다.
  3. 자동 커밋을 끄고, 끝나면 항상 롤백합니다.
- 결과는 최대 100행, 칸당 200자까지만 돌려줍니다. password·token 같은 이름의 컬럼은 `****`로 가립니다.
- 모든 호출은 `tool-audit: db alias=...` 형식으로 로그에 남습니다.

**`KnowledgeTool.knowledgeDatasources(projectId)`**
- 분석 결과에서 데이터소스 설정만 골라 줍니다. 설정 파일의 url·username·driver 같은 접속 키와 Spring XML의 DataSource 빈이 대상이고, 값마다 정의된 파일과 줄이 붙습니다.
- `${...}` 변수는 같은 프로젝트 설정에서 한 단계까지 풀어 봅니다.
- 트랜잭션 매니저나 풀 크기 같은 튜닝 값은 뺍니다.

## dstone-knowledge 변경
- `/configs`와 `/resources` API에 `value` 조건(대소문자 무시)을 추가했습니다. 바뀐 SQL은 실제 DB에 직접 실행해 확인했습니다.
- knowledge의 테스트는 돌리지 못했습니다. pom에 `skipTests`가 `true`로 고정되어 있어 명령줄 옵션으로 켤 수 없습니다.
- Tool이 받은 결과를 한 번 더 거르기 때문에, 지금 4081에서 도는 이전 빌드에서도 동작합니다.

## 실서버로 확인한 결과
- **testApp**: `context-datasource.xml`의 `dataSource`(dbcp2)를 찾았습니다.
- 다만 `${Globals.Url}`은 풀지 못했습니다. 이 프로젝트는 분석 결과에 설정 파일이 하나도 없어서, 결과에는 "프로젝트 설정에 없음(환경 변수나 실행 옵션으로 주는 값)"으로 나옵니다.

## 한계
- Java `@Bean`으로 만든 데이터소스와 WAS 쪽 JNDI 설정(server.xml 등)은 분석 결과에 없어서 찾지 못합니다.
- 함수 호출의 부작용까지는 막지 못합니다. DB 계정은 읽기 권한만 있는 것으로 등록하세요.

## 실제로 쓰려면
1. `conf/application.yml`의 `dstone.ai.tool.db.connections` 예시 주석을 풀고 대상 DB를 등록합니다. ai-engine은 Pod에서 돌기 때문에 DB 주소는 `localhost`가 아니라 Pod에서 닿는 주소여야 합니다.
2. 새 Tool이 쓰이려면 ai-engine을 다시 배포해야 합니다. `value` 조건을 쓰려면 knowledge도 다시 배포해야 합니다.
3. 쓸 Agent의 `tools.allowed`에 `knowledgeDatasources`와 `db*` Tool을 넣습니다.

문서는 `docs/09`(Tool 표, "DB 조회" 설명, 설정 레퍼런스), `docs/11` 7장(API), `.claude/docs/ai-engine-packages.md`에 반영했습니다.
