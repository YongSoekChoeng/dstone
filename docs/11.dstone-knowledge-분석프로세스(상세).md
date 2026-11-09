# Knowledge 분석은 어떻게 돌아가나

`dstone-boot` → knowledge → **프로젝트 · 분석** 화면에서 [분석 시작]을 누르면 무슨 일이 일어나는지, 버튼을 누른 순간부터 분석이 끝나 그래프의 선(엣지)이 생기기까지 차례대로 따라갑니다.

- 6장은 **리비전과 증분 분석(CARRY, CARRY_RESOLVE)** 을 실제 DB에 쌓인 데이터와 함께 설명합니다.
- 7장은 **엣지(관계)를 어떻게 만드는지** 를 자세히 다룹니다.

> 기준: 2026-11-09 `main`. 패키지를 `analysis.passNN.<단계>`로 정리한 커밋 `febcd98a` 뒤입니다.
> 파일 경로는 저장소 루트 기준이고, Java 클래스는 `net.dstone.knowledge` 아래의 경로로 적었습니다(예: `analysis.pass02.carry.CarryPass`).

---

## 0. 한눈에 보기

### 0.1 전체 흐름

```
[브라우저]                         [dstone-boot :7081]                  [dstone-knowledge :4081]
project.js                         KnowledgeProxyController              ProjectController
 startAnalysis() ──POST────────▶   /knowledge/api/call.do  ──POST───▶   /api/projects/{id}/analyses
   K.call()                         KnowledgeProxyService                  AnalysisService.startAnalysis()
                                    (경로 검사, X-API-Key)                   ├ 리비전 만들기 / 이어 하기
                                                                            ├ analysis_job INSERT (READY)
                                                                            └ AnalysisJobRunner.submit()  ──▶ 백그라운드 스레드
   ◀──────────── 202 {analysisId, revisionId ...} ◀───────────────────────
 watchJob()  ──3초마다 GET /api/analyses/{analysisId}──────────────────▶  AnalysisService.getAnalysis()

                                          [백그라운드: analysis-job-N 스레드]
   SCAN(100) → CARRY(150) → DECLARE(200) → RESOURCE(250) → CARRY_RESOLVE(290)
   → RESOLVE(300) → LINK(400) → SEMANTIC(500) → DOCUMENT(600)
                                          [따로 도는 데몬: EmbeddingWorker → rag_embedding 비우기]
```

괄호 안 숫자는 단계의 실행 순서(`order()`)입니다. CARRY와 CARRY_RESOLVE는 **증분 분석일 때만** 일을 하고, 전체 분석에서는 아무것도 하지 않고 지나갑니다.

### 0.2 먼저 알아 둘 말

| 말 | 뜻 | 테이블 |
|---|---|---|
| 프로젝트 | 분석할 소스 폴더 하나 | `analysis_project` |
| **리비전** | 프로젝트를 한 번 분석한 결과 묶음. 그 시점의 **프로젝트 전체 모습**이 들어 있다 | `analysis_revision` |
| Job | 리비전 하나를 만들기 위해 도는 작업 하나. 실패하면 같은 리비전에 Job을 다시 돌릴 수 있다 | `analysis_job` |
| 단계(Pass) | SCAN, DECLARE 같은 처리 단위. 리비전마다 단계별 진행 상태가 남는다 | `analysis_revision_pass` |
| 파일 단계 | 파일 하나가 그 단계를 끝냈는지. 증분 분석에서는 "옮겨 왔는지(carried)" 도 적는다 | `analysis_file_pass` |
| 참조 | DECLARE가 적어 둔 "이름만 아는" 호출·타입 사용 | `analysis_reference` |
| 관계(엣지) | 참조를 실제 대상과 이은 것. 그래프의 선 | `analysis_relation` |
| 증분 분석 | 바뀌지 않은 파일의 결과를 앞 리비전에서 **복사**해 새 리비전을 빨리 만드는 방법 | — |

### 0.3 엣지가 생기는 곳은 세 단계뿐

| 단계 | 엣지를 만드는 방법 | 대표 엣지 |
|---|---|---|
| RESOLVE | 파일마다 AST를 다시 보고 "이름만 적힌 참조"를 실제 대상에 잇는다 | `CALLS`, `EXTENDS`, `IMPLEMENTS`, `USES_TYPE`, `CREATES`, `THROWS`, `ACCESSES_FIELD` |
| LINK | 이미 만든 엣지끼리 SQL로 엮는다 | `OVERRIDES`, `CALLS_POSSIBLE_IMPLEMENTATION` |
| SEMANTIC | 애노테이션 · 매퍼 · 화면 파일과 규칙 파일(`conf/rules`)로 뜻을 붙인다 | `INJECTS`, `EXECUTES_SQL`, `READS_TABLE`, `WRITES_TABLE`, `RENDERS`, `REQUESTS`, `INCLUDES` |

---

## 1. 화면 (브라우저)

### 1.1 입력 칸 — `dstone-boot/src/main/webapp/WEB-INF/views/knowledge/index.jsp`

프로젝트 표에서 프로젝트를 고르면(`selectProject`) 분석 패널(`#kn-analysis-panel`)이 열립니다.

| 요소 id | 뜻 | 요청 본문 키 |
|---|---|---|
| `kn-revision-label` | 리비전 라벨. 비우면 서버가 지금 시각으로 짓는다. **이미 있는 라벨이면 그 리비전을 이어서 분석**한다 | `revisionLabel` |
| `kn-rerun-from` | "이 단계부터 다시" (SCAN ~ DOCUMENT). 이어 하기에서만 쓴다 | `rerunFrom` |
| `kn-incremental` | "바뀐 파일만 다시 분석"(증분 분석). 새 리비전에서만 쓴다 | `incremental` |
| `kn-analysis-start-btn` | [분석 시작] 버튼 | — |

### 1.2 버튼을 누르면 — `dstone-boot/src/main/webapp/knowledge/assets/js/project.js`

`init()`에서 버튼에 `startAnalysis`를 걸어 둡니다.

```js
function startAnalysis() {
    var body = {};
    if (라벨 입력) body.revisionLabel = ...;
    if (증분 체크) body.incremental = true;
    if (단계 선택) body.rerunFrom = ...;
    K.call("POST", "/api/projects/" + encodeURIComponent(projectId) + "/analyses", null, body)
      .then(function (started) {
          // "분석을 시작했습니다: 리비전 #106 (20261109-144013), 증분 분석(기준 #105)"
          K.message(...);
          watchJob(started.analysisId);   // 진행 상황을 3초마다 묻기 시작
          loadRevisions();                // 리비전 표 새로 고침
      })
      .catch(function (err) { K.message(..., err.message, true); });
}
```

비어 있는 칸은 본문에 넣지 않습니다. 아무것도 적지 않으면 `{}`가 갑니다(새 리비전, 전체 분석).

### 1.3 모든 호출이 지나는 함수 — `knowledge-common.js`의 `call()`

브라우저는 dstone-knowledge를 직접 부르지 않습니다. 모든 호출이 dstone-boot의 프록시 주소 하나로 모입니다.

```js
fetch(contextPath + "/knowledge/api/call.do", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ method: "POST", path: "/api/projects/P1/analyses", query: null, body: {...} })
}).then(readResponse);
```

`readResponse`는 응답이 4xx/5xx이면 서버가 준 `message`로 `Error`를 던집니다. 그래서 화면에는 서버의 문장이 그대로 나옵니다.

### 1.4 진행 상황 보기 — `watchJob()` / `renderJob()`

- 3초마다 `GET /api/analyses/{analysisId}`를 부릅니다.
- 상태가 `READY` / `RUNNING`이 아니게 되면(DONE, DONE_WITH_WARNING, FAILED, CANCELLED) 멈추고 리비전 표를 다시 읽습니다.
- `renderJob`은 Job 배지, 지금 단계(`currentPass`), 오류 건수, 단계별 표(단계 · 상태 · 처리 · 전체 · 시작 · 끝), 앞쪽 오류 20건을 그립니다.
- [취소]는 `POST /api/analyses/{id}/cancel`을 보냅니다. 돌고 있는 단계가 다음 확인 지점(`checkCancelled()`)에 닿아야 멈춥니다.

---

## 2. dstone-boot 프록시

### 2.1 `net.dstone.boot.knowledge.controller.KnowledgeProxyController`

`@PostMapping("/knowledge/api/call.do")`가 `{method, path, query, body}`를 받아 서비스로 넘깁니다. 이 주소는 로그인한 사용자만 쓸 수 있으므로(Spring Security), 로그인 기능이 없는 내부 서버 dstone-knowledge를 dstone-boot가 앞에서 지켜 주는 셈입니다.

### 2.2 `net.dstone.boot.knowledge.service.KnowledgeProxyService.call()`

1. **메소드 확인**: GET / POST / DELETE만 받습니다. 나머지는 400.
2. **주소 만들기**
   - 기준 주소는 `interface.knowledge.base-url`(`KNOWLEDGE_HOST` / `KNOWLEDGE_PORT`로 채움). 없으면 503.
   - `path`는 `/api/`로 시작해야 하고, `..` `//` `?` `#` `\`가 있으면 400입니다. 다른 서버나 경로로 새어 나가지 않게 막습니다.
   - `query`의 빈 값은 빼고 붙인 뒤 인코딩합니다.
3. **API 키**: `interface.knowledge.api-key`가 있으면 `X-API-Key` 헤더를 붙입니다(dstone-knowledge의 `ApiKeyAuthFilter`를 켰을 때 필요).
4. **보내기**: WebClient(읽기 제한 120초, 응답 최대 32MB)로 보내고, 상태 코드와 본문을 **그대로** 돌려줍니다.
5. **연결 실패**: 502와 `{status, message}`를 돌려줍니다. 예외를 던지지 않는 까닭은 dstone-boot의 공통 예외 처리가 상태 코드를 200으로 바꿔 버리기 때문입니다.

---

## 3. dstone-knowledge: 분석 시작 API

### 3.1 `api.controller.ProjectController.startAnalysis()`

`POST /api/projects/{projectId}/analyses`. 본문에서 `revisionLabel`, `rerunFrom`, `incremental`, `baseRevisionId`를 꺼내 `AnalysisService.startAnalysis()`를 부르고 **202 Accepted**로 곧바로 돌아옵니다. 이때 분석은 아직 시작 전입니다.

### 3.2 `api.service.AnalysisService.startAnalysis()` — `synchronized`

"돌고 있는 Job이 있나" 확인과 Job 등록 사이에 다른 요청이 끼어들지 못하게 메소드 전체를 잠급니다.

| 순서 | 하는 일 | 실패하면 |
|---|---|---|
| 1 | 프로젝트를 읽고 `localPath`가 실제 폴더인지 본다 | 400 |
| 2 | 같은 프로젝트에 READY / RUNNING Job이 있는지 본다 | 409 |
| 3 | 라벨을 정한다. 없으면 `yyyyMMdd-HHmmss`. 200자를 넘으면 거절 | 400 |
| 4 | 같은 라벨의 리비전이 있으면 **이어 하기**(`resumed=true`), 없으면 새 리비전을 만든다. 이어 하기에 `incremental`을 주면 거절 | 400 |
| 5 | 새 리비전이고 증분이면 **기준 리비전**을 정해 `parent_revision_id`에 적는다(6.3절). 없거나 분석기 버전이 다르면 전체 분석으로 돌리고 `note`에 까닭을 남긴다 | — |
| 6 | `rerunFrom`이 있으면(이어 하기일 때만) 그 단계와 뒤 단계의 "끝남" 표시를 지운다(`resetRevisionPasses`) | 400 |
| 7 | Job ID를 짓는다: `A` + 날짜 + 그날의 일련번호 3자리(예: `A20261109011`) | — |
| 8 | `analysis_job`을 넣는다(READY) | — |
| 9 | `AnalysisJobRunner.submit()`. 대기열이 꽉 찼으면 Job을 FAILED로 바꾸고 503 | 503 |
| 10 | `{analysisId, projectId, revisionId, revisionLabel, resumed, incremental, baseRevisionId, note, rerunPasses, status:"READY"}`를 돌려준다 | — |

리비전을 어떻게 만들고 잇는지는 6장에서 자세히 봅니다.

---

## 4. 백그라운드 Job 실행

### 4.1 스레드 풀 — `common.config.ConfigJob.analysisJobExecutor`

- 동시에 도는 Job 수는 `dstone.knowledge.job.max-concurrent`(기본 2), 대기열 100, 스레드 이름은 `analysis-job-`입니다.
- 한 프로젝트에서는 한 번에 하나만 돕니다(3.2절 2번). 서로 다른 프로젝트는 최대 2개가 함께 돕니다.

### 4.2 `analysis.job.AnalysisJobRunner`

`submit()`은 Job을 `activeJobs` 맵(취소 요청을 전하려고)에 넣고 풀에 맡깁니다. 그 스레드에서는 `ThreadContext.put("SUPPRESS_SQL_LOG","Y")`로 SQL 로그를 끕니다. 수만 건의 INSERT가 로그를 뒤덮지 않게 하려는 것입니다.

```
checkCancelled()
analysis_job → RUNNING, analysis_revision → ANALYZING
for pass in (AnalysisPass 빈을 order() 순으로 정렬):
    checkCancelled()
    if 이 리비전에서 pass가 DONE: 건너뜀            ← 이어 하기가 되는 까닭
    analysis_job.current_pass = pass, analysis_revision_pass 시작 기록
    pass.run(context)
    analysis_revision_pass → DONE
끝: analysis_error가 있으면 DONE_WITH_WARNING, 없으면 DONE
    analysis_revision → READY
    RetentionService.applyQuietly()  // 오래된 리비전 정리
```

- `JobCancelledException` → Job은 CANCELLED, 리비전은 CREATED. 하던 단계는 끝나지 않은 채 남아, 다음에 그 단계부터 다시 합니다.
- 그 밖의 오류 → Job FAILED, 리비전 FAILED.
- 서버가 죽어 RUNNING으로 남은 Job은 다음에 뜰 때 `AnalysisJobRecovery`가 FAILED로 정리합니다.

### 4.3 단계 목록

`AnalysisPass`(`name()`, `order()`, `run()`)를 구현한 빈을 Spring이 모아 줍니다. 새 단계는 빈만 만들면 들어갑니다. 패키지 번호는 실행 순서와 맞춰 두었습니다(실제 순서는 `order()`가 정한다).

| order | 이름 | 클래스 | 처리 단위 | 하는 일 |
|---|---|---|---|---|
| 100 | SCAN | `analysis.pass01.scan.ScanPass` | 폴더 전체 | 파일 목록 → `analysis_file` |
| 150 | CARRY | `analysis.pass02.carry.CarryPass` | 리비전 | (증분) 바뀌지 않은 파일의 선언 · 자원 복사 |
| 200 | DECLARE | `analysis.pass03.declare.DeclarePass` | 파일 | Java / JSP 파싱 → 선언 + **이름만 적힌 참조** + Java 문자열 SQL |
| 250 | RESOURCE | `analysis.pass04.resource.ResourcePass` | 파일 | 매퍼 XML, 설정, pom, Spring XML, DDL 저장 |
| 290 | CARRY_RESOLVE | `analysis.pass05.carryresolve.CarryResolvePass` | 리비전 | (증분) 옮겨도 되는 "풀린 관계"만 복사 |
| 300 | RESOLVE | `analysis.pass06.resolve.ResolvePass` | 파일 | 참조 → **엣지** |
| 400 | LINK | `analysis.pass07.link.LinkPass` | SQL | **엣지끼리 엮기** + 품질 지표 |
| 500 | SEMANTIC | `analysis.pass08.semantic.SemanticPass` | SQL + 일부 파일 | 진입점 · 계층 + **뜻이 있는 엣지** |
| 600 | DOCUMENT | `analysis.pass09.document.DocumentPass` | 파일 | RAG 문서 · 청크, 임베딩 대기열 |

---

## 5. 단계별 설명

### 5.1 파일을 하나씩 처리하는 실행기 — `analysis.job.FilePassRunner`

DECLARE, RESOURCE, RESOLVE, DOCUMENT는 모두 이 실행기를 거칩니다.

1. `seedFilePass`: 이 단계에서 처리할 파일마다 `analysis_file_pass` 행(PENDING)을 만듭니다. **이미 행이 있으면 그대로 둡니다**(`ON CONFLICT DO NOTHING`). 증분 분석에서 CARRY가 미리 넣어 둔 "끝남(옮겨 옴)" 행이 덮이지 않는 것이 이 때문입니다.
2. PENDING 파일을 100개씩 꺼내 하나씩 처리합니다. 100개마다 진행 건수를 남기므로 화면의 "처리 / 전체" 숫자가 이렇게 올라갑니다.
3. 파일 하나는 두 걸음입니다.
   - **prepare**: 감시 스레드(`FileWatchdog`)에서 돕니다. 읽기 · 파싱 · 해석만 하고 **DB에 쓰지 않습니다**.
   - **write**: Job 스레드에서 트랜잭션 하나로 결과와 "이 파일 끝남" 표시를 함께 커밋합니다.
4. 파일 하나가 전체를 붙잡지 못하게 하는 장치
   - 예외 → 그 파일만 FAILED(`INTERNAL`)
   - `StackOverflowError` → FAILED(`TOO_DEEP`)
   - prepare가 600초(`file-hard-timeout-seconds`)를 넘김 → FAILED(`HUNG`). 작업 스레드를 버리고 `handler.reset()`. 버린 수가 3(`max-stuck-files`)을 넘으면 Job 전체 실패

prepare에서 DB에 쓰지 않는 까닭이 여기 있습니다. 버려진 스레드가 커넥션이나 잠금을 쥐고 있으면 안 되기 때문입니다.

### 5.2 SCAN — 파일 목록 만들기

- `Files.walkFileTree`로 `localPath`를 훑습니다. `scan.exclude-dirs`에 있는 폴더는 통째로 건너뜁니다.
- `FileClassifier`가 분석 대상이 아니라고 보면(이미지, class, jar 등) 넘어갑니다.
- 파일마다 언어, 파일 종류, 인코딩(`analysis.common.EncodingDetector`), **SHA-256(`checksum`)**, 크기, 줄 수, Java면 패키지와 소스 루트, 빌드 파일 기준의 모듈을 구합니다. `checksum`은 6장의 증분 분석이 "같은 파일인가"를 가릴 때 씁니다.
- 5MB(`scan.max-file-bytes`)를 넘으면 해시만 구하고 SKIPPED.
- 200개씩 모아 씁니다. 다시 돌면 같은 경로를 덮어써서 중복이 생기지 않습니다.
- **리비전마다 `file_id`를 새로 매깁니다.** 같은 파일이라도 리비전 105에서는 247685번, 106에서는 247697번입니다(6.7절).

### 5.3 DECLARE — 선언과 "이름만 적힌 참조"

`DeclarePass` → `JavaSourceParser` → `DeclarationCollector`. Java 파일을 먼저, 그다음 JSP를 같은 방식으로 처리합니다.

**prepare (`parseFile`)**
1. 프로젝트에 `rootPackages`가 있으면 그 밖의 Java 파일은 읽지 않고 SKIPPED.
2. SCAN이 정한 인코딩으로 읽습니다. JSP면 `JspToJava`가 스크립틀릿을 `_jspService()` 몸통으로 바꿉니다. 지운 글자 자리에 줄바꿈을 남겨 줄 번호가 원본 JSP와 같습니다. Java 코드가 없는 JSP는 건너뜁니다.
3. 파싱은 문법 수준 21 → 8 → 1.4 차례로 시도합니다(옛 소스는 `enum`, `assert`를 변수 이름으로 쓰기도 한다).
4. `DeclarationCollector`가 **이 파일 하나만 보고** 선언과 참조를 뽑습니다.

**선언과 ID 규칙** — 리비전이 달라도 같은 대상이면 같은 값입니다. 이것이 증분 분석에서 행을 그대로 복사할 수 있는 근거입니다.

| 대상 | 테이블 | ID |
|---|---|---|
| 타입 | `analysis_symbol` | SHA-1(프로젝트 \| 소스 루트 \| `T` \| FQN) |
| 메소드 | `analysis_method` | SHA-1(프로젝트 \| 소스 루트 \| `M` \| FQN#시그니처) |
| 필드 | `analysis_field` | SHA-1(프로젝트 \| 소스 루트 \| `F` \| FQN#이름) |
| 애노테이션 | `analysis_annotation` | 대상 종류 + 대상 ID |

시그니처는 **소스에 적힌 파라미터 타입 그대로**(제네릭만 지움)입니다. 다른 파일을 봐야 하는 FQN 해석을 넣으면 ID가 처리 순서에 따라 달라지기 때문입니다. Lombok이 만들 멤버도 `is_synthetic=true`로 함께 넣습니다(`LombokExpander`).

**참조**(`analysis_reference`, status=PENDING)

| ref_kind | 소스 모양 | 위치 |
|---|---|---|
| `CALL` | `orderService.find(x)`, `this(...)`, `super(...)` | 메소드 **이름** 토큰 |
| `CREATE` | `new Foo(...)` | 타입 |
| `FIELD_ACCESS` | `a.b` | 이름 |
| `TYPE_USE` | 변수 · 파라미터 · 반환 타입, 제네릭 인자 | 타입(한 멤버 안에서 같은 타입은 한 번만) |
| `EXTENDS` / `IMPLEMENTS` | 상속 · 구현 | 타입 |
| `THROWS` | `throws X` | 타입 |
| `ANONYMOUS_SUPER` | 익명 클래스의 상위 타입 | 생성식 |
| `METHOD_REF` | `Foo::bar` | 식 |

참조마다 어디에 들어 있는지(`from_kind/from_id`), `name`, `scope_text`(예: `orderService`), `arg_count`, 줄 · 칸을 남깁니다. 호출 인자에 따옴표 문자열이 있으면 첫 인자는 `arg_text`, 둘째 인자부터는 `args_json`에 적힌 그대로 담습니다. 나중에 SQL 이름이나 화면 이름을 찾는 단서입니다.

위치를 식의 시작이 아니라 **이름 토큰**으로 잡는 까닭: `a.get().get()`처럼 이어진 호출은 식의 시작 위치가 모두 같습니다. 이름 토큰이어야 RESOLVE가 같은 노드를 다시 찾을 수 있습니다.

**Java 문자열 SQL**(2026-11-09): 메소드 본문에 문자열로 적은 SQL(`"SELECT …" + …`, `sql += …`, `sb.append(…)`, 같은 클래스의 상수)을 `JavaSqlCollector`가 찾아 `analysis_mapper`에 `mapper_type = JAVA`로 넣습니다. 모르는 값은 `?`로 둡니다. 메소드 · 테이블과 잇는 일은 SEMANTIC의 `JavaSqlPlugin`이 합니다.

**write (`saveFile`)**: 같은 소스 루트에 같은 FQN이 이미 있으면 파일 이름이 클래스 이름과 같은 쪽을 남기고 다른 쪽은 SKIPPED(`DUPLICATE_TYPE`). 그다음 그 파일의 선언 · 참조 · Java SQL을 지우고 다시 넣습니다.

> 이 단계는 **엣지를 하나도 만들지 않습니다.** "타입이 메소드를 가진다" 같은 소속은 엣지가 아니라 `owner_symbol_id` 컬럼으로 나타냅니다.

### 5.4 RESOURCE — Java가 아닌 파일 저장

| 파일 | 넣는 곳 |
|---|---|
| MyBatis / iBATIS 매퍼, JEF 쿼리 XML | `analysis_mapper`(statement 하나에 한 행, `<include>`는 표시만) |
| `.properties`, YAML | `analysis_config`(비밀값은 `****`) |
| `pom.xml`, `build.gradle`, Spring XML, DDL | `analysis_resource` |

모든 XML은 `SafeXml`로 읽습니다. 여기서는 저장만 하고 잇는 일은 SEMANTIC이 합니다. `<include>` 조각이 다른 파일에 있을 수 있어서, 모든 파일을 읽은 뒤에 이어야 하기 때문입니다.

### 5.5 RESOLVE, LINK, SEMANTIC

엣지를 만드는 핵심이라 7장에서 따로 설명합니다.

### 5.6 DOCUMENT와 임베딩

- 파일마다 DB에 쌓인 사실(타입, 메소드, 호출, 진입점, 실행 SQL, 여는 화면 등)과 줄 번호로 잘라 온 소스를 엮어 FILE / TYPE / METHOD 문서를 만듭니다. 매퍼는 statement마다 MAPPER 문서, 화면은 VIEW 문서입니다. 다시 파싱하지 않습니다.
- METHOD 문서 앞쪽에는 "진입점 주소, 계층, 부르는 메소드, 가능한 구현, 불리는 곳" 같은 **엣지 정보를 한국어 문장으로** 넣습니다. "주문 취소는 어디서 처리하나" 같은 질문은 소스 글자와 닮지 않았기 때문입니다.
- 청크의 내용 해시를 `rag_embedding`에 PENDING으로 올리면 이 단계는 끝납니다. 실제 임베딩은 서버 안의 `EmbeddingWorker`가 따로 합니다. 그래서 Job이 DONE이 된 뒤에도 임베딩은 한동안 이어집니다. 임베딩 키가 `(내용 해시, 모델)`이라 증분 분석에서 내용이 그대로인 청크는 다시 임베딩하지 않습니다.

---

## 6. 리비전과 증분 분석 — CARRY, CARRY_RESOLVE 자세히

### 6.1 리비전이란

**리비전 = 그 시점의 프로젝트 전체 사진**입니다. 분석 결과 테이블(`analysis_file`, `analysis_symbol`, `analysis_method`, `analysis_relation` …)의 모든 행에는 `revision_id`가 붙어 있고, 한 리비전만 읽으면 그때의 프로젝트를 빠짐없이 볼 수 있습니다. 다른 리비전을 같이 읽을 필요가 없습니다.

이 약속 덕분에 다음이 쉬워집니다.

- **리비전 비교**(`DiffService`): 두 리비전을 각각 통째로 읽어 견주면 된다.
- **보관 정책**(`RetentionService`): 오래된 리비전을 지워도 다른 리비전이 깨지지 않는다. 증분 분석의 기준 리비전도 지울 수 있다. 증분 분석은 앞 리비전의 행을 **가리키지 않고 복사**하기 때문이다.

리비전마다 다른 값과 같은 값을 구분해 두면 증분 분석이 이해됩니다.

| 값 | 리비전이 바뀌면 | 까닭 |
|---|---|---|
| `revision_id` | 바뀐다 | 리비전의 번호 |
| `file_id` | **바뀐다** | SCAN이 리비전마다 파일 행을 새로 만든다 |
| 타입 · 메소드 · 필드 ID | **그대로** | 프로젝트 · 소스 루트 · FQN · 시그니처로 지은 해시라 리비전 번호가 들어가지 않는다 |
| SQL statement ID(`'S' \|\| mapper_id`) | 바뀐다 | 자동 번호 |

그래서 복사할 때는 `revision_id`와 `file_id`만 새 값으로 바꾸고 나머지는 그대로 옮기면 됩니다. 복사한 메소드와 새로 파싱한 메소드가 같은 ID 체계를 쓰므로, 뒤 단계에서 둘이 섞여도 서로를 찾을 수 있습니다.

### 6.2 리비전을 만드는 세 가지 길

| 길 | 요청 | 결과 리비전 | CARRY / CARRY_RESOLVE |
|---|---|---|---|
| ① 새 리비전, 전체 분석 | `{}` 또는 `{revisionLabel: "새 라벨"}` | 새 행. `parent_revision_id = NULL` | 아무것도 안 함 |
| ② 새 리비전, 증분 분석 | `{incremental: true}` (또는 `baseRevisionId: 105`) | 새 행. `parent_revision_id = 기준 리비전` | 일한다 |
| ③ 이어 하기 / 다시 하기 | `{revisionLabel: "있는 라벨"}` (+ `rerunFrom`) | **같은 행**을 다시 쓴다 | 그 리비전이 증분으로 만들어졌으면 다시 일한다(`parent_revision_id`를 다시 읽는다) |

- ③은 죽은 분석을 이어 가거나, 규칙 파일만 고친 뒤 `rerunFrom=SEMANTIC`으로 뒷부분만 다시 돌릴 때 씁니다. DONE으로 남은 단계는 건너뜁니다.
- ③에 `incremental`을 주면 400입니다. 증분 여부는 리비전을 처음 만들 때 정해집니다.

### 6.3 기준 리비전 고르기와, 전체 분석으로 바뀌는 경우

`AnalysisService.findBaseRevision()`

1. `baseRevisionId`를 줬으면 그 리비전. 이 프로젝트의 것이 아니거나 READY가 아니면 400.
2. 안 줬으면 이 프로젝트에서 **분석이 끝난(READY) 가장 최근 리비전**.

기준 리비전을 찾았어도 다음이면 **전체 분석으로 바꿉니다**(응답 `incremental: false`, `note`에 까닭).

| 경우 | note |
|---|---|
| READY 리비전이 하나도 없다 | "기준으로 쓸 분석이 끝난 리비전이 없어 전체 분석으로 돌립니다." |
| 기준 리비전의 `analyzer_version`이 지금 설정(`dstone.knowledge.analyzer-version`)과 다르다 | "기준 리비전의 분석기 버전(1.1)이 지금(1.2)과 달라 전체 분석으로 돌립니다." |
| 분석 도중 기준 리비전이 지워졌다 | (CARRY가 `baseRevisionOf()`에서 null을 받고 아무것도 안 한다 → 결과적으로 전체 분석) |

분석기 버전은 "옛 결과를 그대로 옮기면 안 될 만큼 분석 규칙이 바뀌었을 때" 올립니다. 2026-11-09에 DECLARE가 Java 문자열 SQL을 남기게 되면서 1.2로 올렸습니다. 그래서 1.1 리비전을 기준으로는 증분 분석을 하지 않습니다.

### 6.4 리비전이 이어지는 모양

```
analysis_revision                         analysis_file (같은 경로의 파일)
 ┌──────────────────────┐                  ┌─────────────────────────────────────────────┐
 │ #104  parent = NULL  │  전체 분석        │ 247685  sql/OrderSql.java   checksum 70a78af7 │
 └──────────▲───────────┘                  └───────────────────────▲─────────────────────┘
            │ parent_revision_id                                   │ carried_from_file_id
 ┌──────────┴───────────┐                  ┌───────────────────────┴─────────────────────┐
 │ #105  parent = 104   │  증분 분석        │ 247685 은 104 의 것. 105 에서도 같은 내용이라  │
 └──────────▲───────────┘                  │ 105 의 행은 104 의 파일을 가리킨다             │
            │                              └─────────────────────────────────────────────┘
 ┌──────────┴───────────┐
 │ #106  parent = 105   │  증분 분석        OrderSql.java 를 고쳤으므로 carried_from = NULL
 └──────────────────────┘                  (새로 DECLARE / RESOLVE 한다)
```

- 리비전 사이의 연결은 `analysis_revision.parent_revision_id` 하나뿐입니다.
- 파일 사이의 연결은 `analysis_file.carried_from_file_id`입니다. "이 파일의 결과를 앞 리비전의 어느 파일에서 옮겨 왔나"를 적은 **기록**이고, 행을 읽을 때 이 값을 따라가지는 않습니다. 결과는 이미 복사돼 있으니까요.
- 증분의 증분도 됩니다(104 → 105 → 106). 106은 104를 몰라도 됩니다.

### 6.5 CARRY 단계 (order 150) — 바뀌지 않은 파일의 선언과 자원 옮기기

**한 줄로**: 앞 리비전과 내용이 똑같은 파일은 다시 파싱하지 않고, 앞 리비전의 결과를 이번 리비전으로 복사해 둡니다. 그러면 DECLARE와 RESOURCE는 바뀐 파일만 처리합니다.

**왜 SCAN 바로 뒤인가**: "같은 파일인가"를 알려면 새 리비전의 파일 목록과 `checksum`이 있어야 하고(SCAN이 만든다), DECLARE가 돌기 전에 "이 파일은 끝났음" 표시를 넣어 둬야 DECLARE가 건너뜁니다.

**하는 일**(`CarryPass.run()`, 트랜잭션 하나, SQL은 `sqlmap/analysis/CarryDao.xml`)

| 순서 | SQL id | 하는 일 |
|---|---|---|
| 0 | `clearCarriedFrom` | 이 리비전 파일들의 `carried_from_file_id`를 먼저 비운다(이 단계를 다시 돌릴 때를 위해) |
| 1 | `markCarriedFiles` | 새 리비전의 파일마다, 기준 리비전에서 **경로 · checksum · 언어 · 파일 종류 · 모듈 · 인코딩**이 모두 같은 파일을 찾아 `carried_from_file_id`에 그 파일 번호를 적는다 |
| 2 | `carryFilePasses` | 짝이 있는 파일 가운데, 기준 리비전에서 DECLARE · RESOURCE가 **DONE**이었던 것만 `analysis_file_pass`에 `DONE, carried = TRUE`로 넣는다 |
| 3 | `copyRows` × 6 | DECLARE 테이블(`analysis_symbol`, `analysis_method`, `analysis_field`, `analysis_annotation`, `analysis_reference`, `analysis_mapper`)의 행을 복사한다. 복사 직전에 `deleteCarriedRows`로 그 파일들에 이미 있던 행을 지운다 |
| 4 | `carryFileParsed` | DECLARE가 파일 행에 남기는 값(파싱 결과, 문법 수준, 패키지, 소스 루트)도 옮긴다 |
| 5 | `copyRows` × 3 | RESOURCE 테이블(`analysis_mapper`, `analysis_config`, `analysis_resource`)을 같은 방법으로 복사한다 |
| 6 | `analyzeTable` | 트랜잭션이 끝난 뒤 복사한 테이블마다 `ANALYZE`로 통계를 새로 낸다 |

조건마다 까닭이 있습니다.

- **분류까지 같아야 같은 파일**: 내용이 같아도 위 폴더에 빌드 파일이 생기면 모듈이 바뀌고, 인코딩 판정이 바뀌면 한글 주석이 달리 읽힙니다. 분석 결과가 달라질 수 있는 것은 모두 비교합니다.
- **DONE이었던 것만 옮긴다**: 앞에서 FAILED나 SKIPPED였던 파일은 이번에는 성공할 수 있어서 다시 처리합니다. 다시 해도 오래 걸리지 않습니다.
- **어떤 행을 옮기나**: `analysis_file_pass`에 그 단계가 `carried = TRUE`로 적힌 파일의 행만입니다. `analysis_mapper`는 DECLARE(Java 파일의 Java SQL)와 RESOURCE(XML 매퍼) 양쪽에서 옮기는데, 한 파일에 두 단계가 함께 있는 경우는 없어서(Java 파일에는 RESOURCE가 없고 XML 파일에는 DECLARE가 없다) 두 번 들어가지 않습니다.
- **컬럼 목록은 DB에서 읽는다**(`selectCopyColumns`, `information_schema`): 테이블에 컬럼이 늘어도 여기를 고치지 않아서 그 컬럼만 조용히 빠지는 일이 없습니다. 자동 번호(`nextval`) 컬럼은 빼고 새로 매기게 합니다.
- **`ANALYZE`를 꼭 돌린다**: 이것을 빼면 DB가 "이 리비전에는 행이 거의 없다"고 잘못 알고 나쁜 실행 계획을 골라, LINK의 SQL이 1초에서 2분 넘게 늘어난 일이 있었습니다.

**결과**: DECLARE와 RESOURCE의 `FilePassRunner`는 이미 DONE인 파일을 건너뛰므로 새로 생겼거나 고친 파일만 읽습니다. 지워진 파일은 새 SCAN 목록에 없으니 아무것도 복사되지 않고 저절로 빠집니다.

**여기서 옮기지 않는 것**

- **관계(`analysis_relation`)**: 내 파일이 그대로여도, 내가 부르는 쪽 파일이 바뀌면 호출이 가리키는 대상이 달라질 수 있습니다. 바뀐 파일의 선언이 다 들어온 뒤에야 판단할 수 있으므로 CARRY_RESOLVE가 따로 합니다.
- **LINK · SEMANTIC · DOCUMENT의 결과**: 이 단계들은 증분이어도 언제나 리비전 전체를 보고 새로 만듭니다. 예를 들어 JPA 쿼리 statement(`mapper_type = JPA`)는 Java 파일의 행이라 CARRY가 함께 복사하지만, SEMANTIC의 `JpaPlugin`이 시작할 때 지우고 다시 만듭니다.

**실패하면**: 트랜잭션 하나로 묶여 있어서 도중에 죽으면 하나도 복사되지 않은 상태로 돌아갑니다. 다시 시작하면 처음부터 복사합니다.

### 6.6 CARRY_RESOLVE 단계 (order 290) — 옮겨도 되는 "풀린 관계"만 옮기기

**한 줄로**: 바뀌지 않은 파일의 호출 · 상속 같은 관계를, 다시 풀 필요가 없는 파일만 골라 앞 리비전에서 복사합니다. 골라지지 않은 파일은 RESOLVE가 다시 풉니다.

**왜 따로 있나**: 선언은 파일 하나만 보면 정해지지만, `orderSql.COUNT_ALL`이 어느 필드인지, `orderService.find()`가 어느 메소드인지는 **다른 파일**에 달려 있습니다. A.java가 그대로여도 A가 부르는 B.java가 바뀌었으면 A의 호출은 다시 풀어야 합니다. 그래서 바뀐 파일의 선언이 다 들어온 뒤(DECLARE · RESOURCE 뒤), RESOLVE 바로 앞에서 돕니다.

**하는 일**(`CarryResolvePass.run()`, 트랜잭션 하나)

| 순서 | SQL id | 하는 일 |
|---|---|---|
| 1 | `carryResolvePasses` | 다시 풀지 않아도 되는 파일에 `RESOLVE = DONE, carried = TRUE`를 넣는다(아래 판정) |
| 2 | `copyRows(analysis_relation, RESOLVE, 빼는 종류)` | 그 파일들의 관계를 복사한다. LINK와 SEMANTIC이 만드는 9종(`OVERRIDES`, `CALLS_POSSIBLE_IMPLEMENTATION`, `INJECTS`, `EXECUTES_SQL`, `READS_TABLE`, `WRITES_TABLE`, `RENDERS`, `REQUESTS`, `INCLUDES`)은 뺀다 |
| 3 | `resetReferencesToResolve` | 다시 풀 파일의 참조를 `PENDING`으로 되돌린다. 복사해 온 참조에는 앞 리비전에서 푼 상태(RESOLVED 등)가 적혀 있기 때문이다 |
| 4 | `analyzeTable` | `analysis_relation`, `analysis_reference`, `analysis_file_pass`의 통계를 새로 낸다 |

**다시 풀어야 하는 파일 고르기**(`carryResolvePasses`의 WITH 절을 차례로 풀면)

```
touched_file   이번에 "건드려진" 파일
               - NEW: 새 리비전에서 carried_from 이 없는 파일(새로 생겼거나 고친 파일)
               - OLD: 기준 리비전에서 새 리비전으로 옮겨지지 못한 파일(고쳤거나 지운 파일의 옛 모습)
touched_id     그 파일들에 선언된 타입 · 메소드 · 필드의 ID (옛 모습 + 새 모습)
touched_name   새 모습(NEW)에 선언된 타입 · 메소드 · 필드의 "이름"
affected       다시 풀 파일 (기준 리비전 기준)
               ① 관계가 touched_id 를 가리키던 파일
                  → 가리키던 것의 시그니처가 바뀌었거나 없어졌을 수 있다
               ② 확실히 풀지 못한 참조(EXTERNAL / HEURISTIC / UNRESOLVED)의 이름이 touched_name 과 같은 파일
                  → 그때는 밖의 것으로 봤지만 이번에는 새로 생긴 선언으로 풀릴 수 있다
결과           DECLARE 를 옮겨 왔고(carried), 기준 리비전에서 RESOLVE 가 DONE 이었고, affected 가 아닌 파일만
               "RESOLVE 끝남(옮겨 옴)"
```

- **조심스러운 쪽으로 기울어 있습니다.** 헷갈리면 다시 풉니다. 다시 푼다고 결과가 틀려지지는 않고 시간만 조금 더 듭니다.
- **놓치는 경우**: 분석 대상의 라이브러리(jar)가 바뀐 것은 알아채지 못합니다. 호출이 밖의 어느 타입으로 풀리는지가 달라질 수 있으니, jar를 바꿨으면 전체 분석을 돌립니다.

### 6.7 예제로 따라가기 — `jpa-app` 리비전 105 → 106

표본 `dstone-knowledge/src/test/resources/samples/jpa-app`(Java 파일 12개)으로 실제로 돌린 결과입니다(2026-11-09). 리비전 105를 기준으로 증분 분석해 106을 만들었고, 그사이 두 파일을 바꿨습니다.

- `web/Urls.java`: 105에서 붙였던 주석 한 줄을 지움
- `sql/OrderSql.java`: 끝에 주석 한 줄을 더함

요청과 응답:

```json
POST /api/projects/jpa-app/analyses   {"incremental": true, "baseRevisionId": 105}

{"analysisId":"A20261109011","projectId":"jpa-app","revisionId":106,"revisionLabel":"20261109-144013",
 "resumed":false,"incremental":true,"baseRevisionId":105,"status":"READY"}
```

#### ① `analysis_revision` — 리비전이 줄로 이어진다

| revision_id | revision_label | parent_revision_id | status | analyzer_version |
|---|---|---|---|---|
| 104 | 20261109-142638 | (없음) | READY | 1.2 |
| 105 | 20261109-143948 | 104 | READY | 1.2 |
| 106 | 20261109-144013 | 105 | READY | 1.2 |

#### ② `analysis_file` — CARRY의 `markCarriedFiles`가 짝을 적는다

106의 파일 행과, 같은 경로의 105 파일을 나란히 놓았습니다(경로 앞의 `src/main/java/com/shop/`는 뺐습니다).

| 106 file_id | 경로 | 106 checksum | **carried_from_file_id** | 105 file_id | 105 checksum |
|---|---|---|---|---|---|
| 247689 | dao/OrderJdbcDao.java | 3e3640c2 | **247677** | 247677 | 3e3640c2 |
| 247699 | entity/Customer.java | 015e623e | **247687** | 247687 | 015e623e |
| 247700 | entity/Order.java | 7c9cdb9c | **247688** | 247688 | 7c9cdb9c |
| 247698 | entity/OrderItem.java | 9eb62050 | **247686** | 247686 | 9eb62050 |
| 247696 | repository/OrderRepository.java | c670afa2 | **247684** | 247684 | c670afa2 |
| 247697 | sql/OrderSql.java | **a9bab364** | **(없음)** | 247685 | 70a78af7 |
| 247694 | web/AdminApi.java | d7d814b2 | **247682** | 247682 | d7d814b2 |
| 247692 | web/AdminController.java | 8feb1669 | **247680** | 247680 | 8feb1669 |
| 247693 | web/BaseCrudController.java | 625c8fae | **247681** | 247681 | 625c8fae |
| 247690 | web/ItemController.java | b3130bec | **247678** | 247678 | b3130bec |
| 247691 | web/OrderController.java | bdfbc963 | **247679** | 247679 | bdfbc963 |
| 247695 | web/Urls.java | **587eac68** | **(없음)** | 247683 | fa538949 |

- `file_id`는 리비전마다 다른 번호입니다(247677 → 247689).
- checksum이 다른 두 파일만 `carried_from_file_id`가 비어 있습니다. 이 둘이 "바뀐 파일"입니다.

#### ③ `analysis_file_pass` — 어느 단계를 옮겨 왔고 어느 단계를 실제로 돌렸나

| 경로 | DECLARE | RESOLVE | DOCUMENT |
|---|---|---|---|
| dao/OrderJdbcDao.java | DONE **(carried)** | DONE | DONE |
| entity/Customer.java | DONE **(carried)** | DONE **(carried)** | DONE |
| entity/Order.java | DONE **(carried)** | DONE **(carried)** | DONE |
| … (나머지 7개 같음) | DONE **(carried)** | DONE **(carried)** | DONE |
| sql/OrderSql.java | DONE | DONE | DONE |
| web/Urls.java | DONE | DONE | DONE |

- 10개 파일의 DECLARE는 CARRY가 옮겨 왔습니다. 고친 두 파일만 DECLARE가 실제로 파싱했습니다.
- **`OrderJdbcDao.java`는 내용이 그대로인데 RESOLVE가 옮겨지지 않았습니다.** 까닭은 ⑤에 있습니다.
- DOCUMENT는 증분이어도 언제나 다시 돕니다(옮겨 온 표시가 없다).

API로도 같은 요약을 볼 수 있습니다(`GET /api/revisions/106`의 `incremental`).

```json
{"baseRevisionId": 105,
 "files":  {"files": 12, "unchangedFiles": 10, "changedFiles": 2, "addedFiles": 0, "removedFiles": 0},
 "passes": [{"pass": "DECLARE", "carriedFiles": 10, "analyzedFiles": 2},
            {"pass": "RESOLVE", "carriedFiles": 9,  "analyzedFiles": 3}]}
```

#### ④ 복사한 행은 ID가 그대로다 — `analysis_method`

같은 메소드 `OrderJdbcDao.countAll()`의 105 행과 106 행입니다.

| revision_id | file_id | method_id | signature |
|---|---|---|---|
| 105 | 247677 | 5aa05d97393a… | countAll() |
| 106 | **247689** | **5aa05d97393a…** | countAll() |

`file_id`만 새 파일 번호로 바뀌었고 `method_id`는 그대로입니다. 그래서 106에서 새로 푼 관계가 이 메소드를 가리키든, 복사한 관계가 가리키든 같은 대상을 찾습니다.

파일 하나의 테이블별 행 수도 그대로입니다(`repository/OrderRepository.java`).

| 테이블 | 105 | 106 | 누가 채웠나(106) |
|---|---|---|---|
| analysis_symbol | 1 | 1 | CARRY 복사 |
| analysis_method | 7 | 7 | CARRY 복사 |
| analysis_annotation | 5 | 5 | CARRY 복사 |
| analysis_reference | 19 | 19 | CARRY 복사 |
| analysis_mapper | 6 | 6 | CARRY가 복사한 뒤 SEMANTIC(`JpaPlugin`)이 지우고 다시 만든 JPA 쿼리 |
| analysis_relation | 33 | 33 | 아래 표 |

106에서 이 파일의 관계 33건이 어디서 왔는지 보면, CARRY_RESOLVE가 옮기는 것과 옮기지 않는 것이 나뉩니다.

| relation_type | 건수 | 어디서 |
|---|---|---|
| USES_TYPE | 18 | CARRY_RESOLVE가 105에서 복사 |
| EXTENDS | 1 | CARRY_RESOLVE가 105에서 복사 |
| EXECUTES_SQL | 6 | SEMANTIC이 새로 만듦(복사 대상에서 뺀다) |
| READS_TABLE | 6 | SEMANTIC이 새로 만듦 |
| WRITES_TABLE | 2 | SEMANTIC이 새로 만듦 |

#### ⑤ CARRY_RESOLVE가 `OrderJdbcDao.java`를 다시 풀게 한 까닭

기준 리비전 105에 이런 관계가 있었습니다.

| revision_id | 출발 파일 | relation_type | to_id | 가리키는 필드 | confidence |
|---|---|---|---|---|---|
| 105 | OrderJdbcDao.java | ACCESSES_FIELD | 47d24b8c5668… | `OrderSql.COUNT_ALL` | HIGH |

`OrderSql.java`는 바뀐 파일이므로 그 안의 필드 ID(`47d24b8c5668…`)는 `touched_id`에 듭니다. 이 ID를 가리키던 `OrderJdbcDao.java`는 `affected`(①번 규칙)가 되어 RESOLVE를 옮겨 오지 않습니다. 실제로는 주석 한 줄만 바뀌었지만, 분석기는 그것까지 알 수 없으니 조심스럽게 다시 풉니다.

이 파일의 참조 38건은 `resetReferencesToResolve`가 PENDING으로 되돌렸고, RESOLVE가 다시 풀었습니다. 다시 푼 결과는 105와 같습니다.

| revision_id | file_id | ref_kind | name | scope_text | 줄 | status |
|---|---|---|---|---|---|---|
| 105 | 247677 | FIELD_ACCESS | COUNT_ALL | OrderSql | 42 | RESOLVED |
| 105 | 247677 | CALL | queryForObject | jdbcTemplate | 42 | EXTERNAL |
| 106 | 247689 | FIELD_ACCESS | COUNT_ALL | OrderSql | 42 | RESOLVED |
| 106 | 247689 | CALL | queryForObject | jdbcTemplate | 42 | EXTERNAL |

반대로 `Urls.java`도 바뀌었지만, 컨트롤러들은 `Urls` 상수를 애노테이션 안에서만 씁니다(`@RequestMapping(Urls.ORDER)`). 애노테이션 속성은 참조나 관계가 아니라서 Urls를 가리키는 관계가 없고, 컨트롤러는 다시 풀 대상이 되지 않았습니다. 주소 상수는 SEMANTIC이 매번 새로 계산하므로 문제가 없습니다.

#### ⑥ 바뀐 파일의 Java 문자열 SQL — DECLARE가 다시 만든다

`OrderSql.java`의 상수 SQL 두 개입니다. 106에서는 CARRY가 아니라 DECLARE가 새로 만든 행이라 `file_id`가 새 파일 번호이고, 필드 ID는 그대로입니다.

| revision_id | file_id | mapper_type | statement | properties_json |
|---|---|---|---|---|
| 105 | 247685 | JAVA | com.shop.sql.OrderSql.CLOSE_OLD | `{"fieldId": "9a930118…"}` |
| 105 | 247685 | JAVA | com.shop.sql.OrderSql.COUNT_ALL | `{"fieldId": "47d24b8c…"}` |
| 106 | 247697 | JAVA | com.shop.sql.OrderSql.CLOSE_OLD | `{"fieldId": "9a930118…"}` |
| 106 | 247697 | JAVA | com.shop.sql.OrderSql.COUNT_ALL | `{"fieldId": "47d24b8c…"}` |

#### ⑦ `analysis_revision_pass`와 로그 — 단계별로 걸린 시간

| 단계 | 104(전체 분석) | 106(증분 분석) |
|---|---|---|
| SCAN | 0.07초 | 0.07초 |
| CARRY | 0.00초(할 일 없음) | **7.6초** |
| DECLARE | 0.23초 | 0.25초 |
| RESOURCE | 0.04초 | 0.04초 |
| CARRY_RESOLVE | 0.00초(할 일 없음) | **9.7초** |
| RESOLVE | 0.33초 | 4.5초 |
| LINK · SEMANTIC · DOCUMENT | 0.5초 | 1.1초 |

```
CARRY: 기준 리비전 105과 같은 파일 10개, 옮겨 온 단계 10건, 옮긴 행 179건. analysisId=A20261109011
CARRY_RESOLVE: 관계를 옮겨 온 파일 9개(관계 41건), 다시 풀 참조 38건. analysisId=A20261109011
```

이렇게 작은 표본에서는 **증분 분석이 오히려 느립니다.** CARRY와 CARRY_RESOLVE의 `ANALYZE`가 이 리비전만이 아니라 DB에 쌓인 모든 리비전의 테이블 전체를 대상으로 하기 때문입니다. 같은 날 따로 재 보니 `ANALYZE analysis_relation`(약 212만 행) 2.1초, `analysis_reference` 2.6초, `analysis_method` 5.2초가 걸렸습니다. 파일 수와 상관없이 드는 고정 비용입니다. RESOLVE가 0.33초에서 4.5초로 늘어난 까닭은 따로 확인하지 않았습니다(다시 푼 파일은 3개뿐이다). 증분 분석은 큰 프로젝트에서 이득이 납니다. `anybiz_prd` 사본에서는 전체 분석 215초가 증분 분석 80~102초로 줄었습니다(RESOLVE 131초 → 2.5~27초).

#### ⑧ 결과가 전체 분석과 같은가

증분 분석의 약속은 **"전체 분석과 결과가 같다"** 입니다. 같은 날 리비전 98(전체)과 99(98 기준 증분, `OrderJdbcDao.java` 한 파일만 고침)를 견줬을 때, statement · 테이블 관계 · 메소드 연결 · 진입점의 해시가 모두 같았습니다. 패스를 고칠 때마다 이 비교를 다시 해야 합니다(`.claude/docs/knowledge-search-incremental.md`의 비교 절차).

### 6.8 정리: 증분 분석에서 단계별로 무엇이 일어나나

| 단계 | 바뀌지 않은 파일 | 바뀐 · 새 파일 | 지운 파일 |
|---|---|---|---|
| SCAN | 새 `file_id`로 다시 등록 | 새 `file_id`로 등록 | 목록에 없다 |
| CARRY | 선언 · 자원 복사, DECLARE/RESOURCE "끝남" | 아무것도 안 함 | 아무것도 안 함(그래서 빠진다) |
| DECLARE / RESOURCE | 건너뜀 | 파싱해서 넣음 | — |
| CARRY_RESOLVE | 다시 풀 필요가 없으면 관계 복사 + RESOLVE "끝남". 있으면 참조를 PENDING으로 | 아무것도 안 함 | 그 선언을 가리키던 파일을 다시 풀게 한다 |
| RESOLVE | 표시가 없는 것만 다시 푼다 | 푼다 | — |
| LINK · SEMANTIC · DOCUMENT | 리비전 전체를 다시 만든다 | 같음 | — |

### 6.9 조심할 점

- **jar가 바뀐 것은 모릅니다.** 분석 대상의 라이브러리를 바꿨으면 증분을 체크하지 말고 전체 분석을 돌립니다.
- **분석기 버전이 다르면 저절로 전체 분석**입니다. 버전을 올린 직후의 첫 분석은 느립니다.
- **이어 하기와는 다릅니다.** 이어 하기는 같은 리비전을 다시 쓰고, 증분 분석은 새 리비전을 만듭니다.
- **기준 리비전을 지워도 됩니다.** 결과가 복사돼 있으므로 106은 105가 없어도 온전합니다. `carried_from_file_id`는 "어디서 왔는지" 기록일 뿐입니다. 다만 보관 정책은 돌고 있는 증분 분석의 기준 리비전은 지우지 않습니다.
- **실패하면 처음부터**: CARRY와 CARRY_RESOLVE는 각각 트랜잭션 하나라, 도중에 죽으면 그 단계에서 복사한 것이 모두 되돌아갑니다.

---

## 7. 엣지(관계) 자세히

### 7.1 엣지가 사는 곳: `analysis_relation` 한 테이블

모든 종류의 엣지가 한 테이블에 들어갑니다.

| 컬럼 | 뜻 |
|---|---|
| `revision_id` | 어느 리비전의 엣지인가 |
| `from_kind`, `from_id` | 출발 노드(METHOD / TYPE / FIELD / SQL / FILE) |
| `relation_type` | 엣지 종류(`CALLS` 등) |
| `to_kind`, `to_id` | 도착 노드. 프로젝트 안에 있을 때만 `to_id`가 찬다 |
| `to_external` | 프로젝트 밖 대상의 이름(예: `java.util.List.add(E)`, 테이블 이름 `TB_ORDER`) |
| `confidence` | HIGH / MEDIUM / LOW / UNRESOLVED |
| `resolution_status` | RESOLVED / HEURISTIC / UNRESOLVED / DYNAMIC |
| `properties_json` | 어떻게 찾았는지(`via`)와 덧붙임 정보(`crud`, `columns`, `path`, `declared` …) |
| `file_id`, `line_start` | 근거가 된 소스 위치. CARRY_RESOLVE가 파일별로 복사할 때 이 `file_id`를 기준으로 삼는다 |

그래프 화면과 API는 이 테이블을 바로 쓰거나 VIEW로 읽습니다.

- `kg_node` VIEW = 타입 + 메소드 + 필드 + SQL statement(`'S'||mapper_id`) + 파일(`'F'||file_id`)
- `kg_edge` VIEW = `analysis_relation` 그대로(`from_id → to_id`)

**노드 ID**: 타입 · 메소드 · 필드는 40자 SHA-1, SQL statement는 `S123`, 파일(JSP · 화면)은 `F456`. 앞 글자 덕분에 서로 겹치지 않습니다. 테이블은 노드가 없고 `to_external`에 이름만 둡니다.

**원칙 두 가지**
1. **불확실해도 지우지 않고 정도를 남긴다.** 짐작한 연결은 LOW / HEURISTIC, 못 푼 연결은 UNRESOLVED와 이유(`analysis_reference.fail_reason`)를 남깁니다.
2. **이름은 계산하고 추측하지 않는다.** SQL 이름, 화면 경로, 거래 ID, 주소 상수는 따옴표 문자열과 상수를 따라가 확정된 것만 씁니다.

### 7.2 두 걸음: 참조 → 엣지

```
DECLARE (파일 하나만 봄)             RESOLVE (다른 파일도 봄)
analysis_reference                  analysis_relation
 from: OrderController#list()   ──▶  from: OrderController#list()
 CALL name=findOrders                CALLS → OrderService#findOrders()  HIGH
 scope=orderService                  (reference.status = RESOLVED)
 line 42, col 27
```

DECLARE는 순서와 상관없이 돌 수 있는 일(파일 하나 보기)만 하고, 다른 파일이 필요한 판단은 모든 선언이 DB에 들어간 뒤 RESOLVE로 미룹니다. 프로젝트 전체를 메모리에 올리지 않으려는 구조이고, 증분 분석이 "선언은 CARRY, 관계는 CARRY_RESOLVE"로 나뉘는 것도 이 두 걸음 때문입니다.

### 7.3 RESOLVE — 참조를 엣지로 바꾸는 규칙

`analysis.pass06.resolve.ResolvePass` → `ReferenceResolver.resolveFile()`

**타입 해석기 준비**(이 차례로 엮는다)
1. `DbTypeSolver`: "이 FQN은 어느 파일에 있나"를 `analysis_symbol`에서 찾아 그 파일만 파싱합니다. AST는 LRU 캐시(기본 100개)에만 둡니다.
2. `ReflectionTypeSolver(JdkClassFilter)`: JDK 타입(`java.`, `javax.`, `jdk.`, `org.w3c.dom.` 등)만. 거르지 않으면 대상의 `org.springframework…`를 분석기 자신이 쓰는 Spring 클래스로 풀어 버립니다.
3. `JarTypeSolver`들: 프로젝트 폴더의 `*.jar`와 프로젝트의 `classpath` 값(`ClasspathResolver`). 대상 프로젝트를 빌드하지는 않습니다.

`CombinedTypeSolver`는 캐시 없이 만들고, 파일마다 `JavaParserFacade.clearInstances()`를 불러 메모리가 쌓이지 않게 합니다.

**노드 맞추기**: 그 파일의 참조를 `"종류|줄|칸"` 키로 맵에 넣고, AST를 걸어 내려가며 같은 위치의 노드를 꺼냅니다. 끝까지 못 꺼낸 참조는 "같은 위치의 코드를 찾지 못했습니다"로 UNRESOLVED가 됩니다.

**메소드 호출(`CALL`) 판정 사다리** — 위에서 풀리면 아래로 내려가지 않습니다.

| 단계 | 방법 | 결과 엣지 | confidence |
|---|---|---|---|
| 1 | JavaSymbolSolver의 `solve(call)` 성공, 선언이 프로젝트 소스에 있음 | `CALLS → METHOD(id)` | HIGH |
| 1' | 해석은 됐고 프로젝트 타입이지만 소스에 없는 멤버(기본 생성자, enum `values()`) | DECLARE가 만든 멤버면 그것, 아니면 `EXTERNAL_METHOD`(`via=COMPILER_GENERATED`) | HIGH |
| 1'' | 해석은 됐고 프로젝트 밖 메소드 | `CALLS → EXTERNAL_METHOD`, `to_external = java.util.List.add(E)` | HIGH |
| 2 | 해석 실패. 대상 식의 **타입**만 알아냄(변수 선언 + import, Lombok 필드, 앞 호출의 반환 타입) → 그 타입이 프로젝트 밖 | `CALLS → EXTERNAL_METHOD Type.name`(`via=SCOPE_TYPE`) | MEDIUM |
| 3 | 그 타입과 프로젝트 안 상위 타입에서 이름 + 인자 수로 찾음 | 1개 · Lombok 멤버 → HIGH, 1개 · 소스 멤버 → MEDIUM, 여러 개 → 모두 LOW(`via=OWNER_TYPE / SYNTHETIC_MEMBER`) | HIGH / MEDIUM / LOW |
| 3' | 타입은 프로젝트 안인데 그런 메소드가 없음 | 엣지 없음. 프로젝트 밖 상위 타입에서 물려받았다고 보고 **짐작하지 않음** | UNRESOLVED |
| 4 | 타입도 모름 → 프로젝트 전체에서 이름 + 인자 수가 같은 메소드가 5개 이하 | 후보 모두에 `CALLS`(`via=NAME`, `candidates=n`) | LOW |
| 5 | 후보가 없거나 5개보다 많음(`get`, `toString` 등) | 엣지 없음, 이유 기록 | UNRESOLVED |

**선언을 메소드 ID로 바꾸는 법**: 이름으로 찾지 않습니다. 해석기가 돌려준 선언의 AST 노드가 "어느 파일의 몇째 줄"인지 보고 그 파일의 메소드 색인(`줄|이름|파라미터 수`)에서 찾습니다. 익명 클래스나 지역 클래스의 메소드는 타입 이름으로 가리킬 수 없기 때문입니다.

**그 밖의 참조**

| ref_kind | 엣지 | 판정 |
|---|---|---|
| `CREATE`(`new Foo()`) | `CREATES → TYPE` + 생성자가 풀리면 `CALLS → <init>` | 타입 판정과 같음 |
| `TYPE_USE` | `USES_TYPE` | 해석기 HIGH → import로 FQN 추정 MEDIUM(`via=IMPORT`) → 같은 단순 이름 타입이 하나뿐 LOW(`via=NAME`) → UNRESOLVED |
| `EXTENDS` / `IMPLEMENTS` / `THROWS` | 같은 이름의 엣지 | 위와 같음. 대상이 프로젝트 안이면 `to_kind=TYPE`, 밖이면 `EXTERNAL_TYPE` |
| `ANONYMOUS_SUPER` | 상위 타입이 인터페이스면 `IMPLEMENTS`, 아니면 `EXTENDS`(`anonymous:true`). 종류를 모르면 EXTENDS + `kindUnknown` | |
| `FIELD_ACCESS` | `ACCESSES_FIELD → FIELD` 또는 `EXTERNAL_FIELD` | |
| `METHOD_REF`(`Foo::bar`) | 이름으로만 찾음(4단계) | LOW |
| 타입 파라미터 `<T>` | 엣지 없음, IGNORED | |

**시간 제한**: 파일 하나에 120초(`resolve.file-timeout-seconds`). 넘으면 그때까지 푼 것은 저장하고 남은 참조는 "시간 초과"로 UNRESOLVED, 파일에는 `RESOLVE_TIMEOUT` 경고를 남깁니다.

**저장**(`RelationDao.replaceRelationsInBatch`): 한 트랜잭션에서 그 파일의 엣지를 지우고(`DELETE … WHERE file_id = ?`) 새 엣지를 넣고 참조 상태를 고칩니다. 파일 단위로 지우고 다시 넣으므로 다시 돌려도 엣지가 두 번 생기지 않습니다.

### 7.4 LINK — 엣지끼리 엮기 (SQL만)

`analysis.pass07.link.LinkPass`는 한 트랜잭션에서 다음을 차례로 합니다. 파일을 읽지 않습니다.

1. `deleteLinkRelations`: 이 리비전의 `OVERRIDES`, `CALLS_POSSIBLE_IMPLEMENTATION`을 지운다(몇 번 돌려도 같게).
2. `insertOverrides`
   - 재귀 CTE로 `EXTENDS` / `IMPLEMENTS`를 따라 올라가 (타입, 조상) 쌍을 만든다. 최대 20단계, `UNION`으로 중복을 거르고, 자기 자신이 조상으로 나오는 행(순환 상속)은 버린다.
   - 하위 타입 메소드와 조상 메소드가 이름 · 파라미터 수가 같고 생성자 · static · private가 아니면 `메소드 ─OVERRIDES→ 상위 메소드`.
   - 시그니처 글자까지 같으면 HIGH, 아니면 MEDIUM. 단 조상에 시그니처까지 같은 메소드가 따로 있으면 그쪽만 잇는다.
3. `insertPossibleImplementations`
   - `호출자 ─CALLS→ X`가 있고, X가 **몸통 없는 메소드(abstract · 인터페이스)** 이고, `Y ─OVERRIDES→ X`인 구현 Y가 있으면 `호출자 ─CALLS_POSSIBLE_IMPLEMENTATION→ Y`(MEDIUM, `properties_json.declared = X`).
   - 몸통 있는 메소드까지 넣지 않는 까닭: `BaseVO.setId()` 호출 천여 건이 재정의한 하위 클래스 전부로 퍼져 잡음이 된다(`anybiz_prd`에서 27,715건). 그런 경우는 `OVERRIDES`를 따라가면 된다.
4. `replaceMetrics`: 품질 지표(`parseSuccessRate`, `callCount`, `callResolutionRate`, `callHeuristicRate`, `callUnresolvedRate`, `typeResolutionRate`, `relationCount`)를 `analysis_metric`에 쓴다. 리비전 요약의 "한눈에 보기"가 이 값을 쓴다.

```
OrderController#list ─CALLS(HIGH)──────────────────────▶ OrderService#findOrders (interface)
        │                                                        ▲
        └─CALLS_POSSIBLE_IMPLEMENTATION(MEDIUM)──▶ OrderServiceImpl#findOrders ─OVERRIDES(HIGH)┘
```

### 7.5 SEMANTIC — 뜻이 있는 엣지

`analysis.pass08.semantic.SemanticPass`는 시작할 때 규칙 파일(`conf/rules/*.yml`)을 다시 읽어(`SemanticRuleRegistry`) 싣고, **한 트랜잭션**에서 다음을 합니다.

1. `SemanticDao.clear()`: 진입점, SEMANTIC이 만든 `analysis_resource` 행, `INJECTS`, 계층, 프록시 표시, 화면 엣지(`RENDERS` / `REQUESTS` / `INCLUDES`)를 지운다.
2. 적용한 규칙 묶음을 `RULE_SET` 행으로 남긴다(이름, 파일, 해시).
3. `SemanticPlugin` 빈을 order 순으로 돌린다.

도중에 실패하면 지운 것까지 함께 되돌아갑니다. 규칙 파일만 고쳤다면 `rerunFrom=SEMANTIC`으로 이 단계부터 다시 돌리면 됩니다(서버 재기동 불필요).

| order | 플러그인(`pass08.semantic.` 아래) | 만드는 엣지 / 결과 |
|---|---|---|
| 100 | `web.SpringPlugin` | 애노테이션 계층(먼저), HTTP · 스케줄 · 리스너 진입점(HTTP는 `HttpEndpointBuilder`: 주소 상수 계산, 상위 클래스 · 인터페이스 주소 물려받기), **`INJECTS`**, 프록시 표시 |
| 200 | `web.ServletPlugin` | web.xml / `@WebServlet` → SERVLET 진입점 |
| 250 | `web.StrutsPlugin` | Struts 진입점, **`RENDERS`**(forward / result) |
| 260 | `web.XmlEndpointPlugin` | 규칙 `endpoints.xml`로 설정 XML의 진입점 |
| 300 | `web.PlainJavaPlugin` | `main`, Thread / Runnable, JSP 진입점 |
| 390 | `sql.JpaPlugin` | JPA 쿼리 statement(`@Query`, `@NamedQuery`, 메소드 이름 쿼리) |
| 400 | `sql.MyBatisPlugin` | 매퍼의 **`EXECUTES_SQL`**, **`READS_TABLE`**, **`WRITES_TABLE`** |
| 410 | `sql.JavaSqlPlugin` | Java 문자열 SQL · JPA 쿼리의 **`EXECUTES_SQL`**, **`READS_TABLE`**, **`WRITES_TABLE`** |
| 500 | `screen.JspPlugin` | **`RENDERS`**, **`REQUESTS`**, **`INCLUDES`**, VIEW 계층 |
| 510 | `screen.RichClientPlugin` | WebSquare / Nexacro 화면의 **`REQUESTS`**, **`INCLUDES`**, TRANSACTION 진입점 |
| 520 | `screen.ScreenMenuPlugin` | 메뉴 목록의 화면명 |
| 800 | `layer.ConfiguredLayerPlugin` | 상위 타입 규칙으로 계층 |
| 900 | `layer.NamingLayerPlugin` | 이름 끝말로 계층(LOW, 빈 것만) |
| 950~980 | `dictionary.SourceNamePlugin`, `MethodTitlePlugin`, `ScreenDictionaryPlugin`, `TableDictionaryPlugin` | 한글명 · 사전(엣지 없음) |

앞 플러그인이 만든 진입점이 있어야 뒤 플러그인이 화면의 주소를 맞출 수 있으므로 순서가 중요합니다.

#### (1) `INJECTS` — `SemanticDao.xml#insertInjects`

```
analysis_annotation (필드에 붙은 @Autowired / @Inject / @Resource 등, 규칙 injects.annotations)
  JOIN analysis_field
  JOIN analysis_relation (그 필드의 USES_TYPE 엣지)   ← RESOLVE가 만든 것
⇒ 필드를 가진 타입 ─INJECTS→ 필드의 타입   (confidence는 USES_TYPE 것을 물려받음)
```

`List<Foo>`처럼 타입이 여럿이면 맨 앞(바깥) 타입 하나만 씁니다. 애노테이션은 단순 이름으로 맞춥니다(와일드카드 import면 FQN을 알 수 없어서). 생성자 · setter 주입은 아직 다루지 않습니다.

#### (2) `EXECUTES_SQL` — `sql.MyBatisPlugin`

먼저 `analysis_mapper`로 색인을 만듭니다(`네임스페이스.id → S{mapper_id}`, 조각은 따로. Java 문자열 SQL은 이름으로 부르는 것이 아니라 뺀다).

- **인터페이스 방식**(`ResourceDao.xml#insertInterfaceMapperRelations`): 매퍼 namespace가 인터페이스 FQN이고 statement id가 메소드 이름이면 `메소드 ─EXECUTES_SQL→ S{id}`(HIGH, `via=MAPPER_INTERFACE`).
- **문자열 방식**(`linkCalls`)
  1. 규칙 `callArguments`(target: SQL)마다 맞는 호출을 `analysis_reference`에서 500건씩 꺼냅니다. 규칙은 메소드 이름, `argIndex`, `receiverTypes`(RESOLVE가 정한 대상 타입과 하위 타입)로 거릅니다. 예: `sqlSession.selectList("Order.list", vo)`, JEF의 `new QueryProperty("파일.id")`, JPA의 `em.createNamedQuery("Order.findAll")`.
  2. 인자 글자를 `ConstantEvaluator`로 **계산**합니다. 문자열, `+`, 자기 · 상위 타입 필드의 초기값, 파라미터 없는 메소드의 단일 `return`까지 따라갑니다. 지역 변수나 파라미터가 하나라도 섞이면 전체를 "모름"으로 둡니다.
     ```
     getSqlSession().selectList(getOrderMapper() + "selectOrderList", vo)
       getOrderMapper() → BaseDAO에서 return orderMapper; → "Order."
       ⇒ "Order.selectOrderList"
     ```
  3. 규칙의 `template`(앞뒤 글자)을 붙이고, 이름 모양(영문 · 숫자 · `_` `.` `-`)인지 봅니다.
  4. statement를 찾습니다. 같은 이름이 여러 파일에 있으면 부르는 Java와 같은 소스 루트의 것을 고릅니다(펼친 WAR의 `WEB-INF/src`와 `WEB-INF/classes`). 점이 없는 id만 넘기면(iBATIS) 그 id가 하나뿐일 때만 잇습니다.
  5. 찾으면 `EXECUTES_SQL → S{id}` HIGH(`via=STATEMENT_NAME`). 이름은 알았지만 statement가 없으면 `to_id=null`, `to_external=이름`의 **UNRESOLVED 엣지로 남깁니다**.

`sql.JavaSqlPlugin`은 Java 문자열 SQL과 JPA 쿼리를 메소드와 잇습니다. 메소드 안의 SQL은 그 메소드(`JAVA_STRING`), SQL 상수는 그 상수를 읽는 메소드(같은 클래스는 `JAVA_STRING`, 다른 클래스는 `ACCESSES_FIELD`를 따라 `JAVA_CONSTANT`, MEDIUM), JPA는 `@Query`가 붙은 메소드 · 이름이 같은 저장소 메소드 · 메소드 이름 쿼리의 메소드입니다.

#### (3) `READS_TABLE` / `WRITES_TABLE`

매퍼 statement는 `MyBatisPlugin.extractTables`가 500건씩 읽어 `<include>`를 조각 본문으로 채우고(최대 6단계) `analysis.common.SqlTableExtractor`로 테이블을 뽑습니다.

- JSQLParser(fast mode, 호출 스레드에서만) 성공 → HIGH, `columns` 포함
- 실패하거나 괄호 12단계 초과 · 2만 자 초과 → `FROM / JOIN / INTO / UPDATE` 정규식 → MEDIUM, `via=PATTERN`
- `crud`가 R이면 `READS_TABLE`, C / U / D면 `WRITES_TABLE`. SQL 주석의 한글명은 `columnComments` / `tableComment`로 남깁니다.

Java 문자열 SQL은 `JavaSqlPlugin`이 같은 추출기로, JPQL은 `JpqlTableExtractor`가 엔티티를 찾고 `JpaEntities`가 테이블로 바꿉니다(`@Table` 이름이면 HIGH, 이름 짓는 관례로 지었으면 MEDIUM).

#### (4) `RENDERS` — 메소드가 화면을 연다

- `screen.JspPlugin` → `SemanticDao.xml#insertViewArgumentRelations`: 규칙 `callArguments`(target: VIEW, 예: `new ModelAndView("x")`, `setViewName("x")`)에 맞는 호출 가운데 인자가 **따옴표 문자열 하나**인 것만 봅니다. `prefix + x + suffix`로 끝나는 JSP가 **정확히 하나**일 때만 `메소드 ─RENDERS→ F{file_id}` HIGH.
- `web.StrutsPlugin`: struts-config의 forward / result.
- `return "order/list"` 방식이나 Tiles 정의 이름은 아직 다루지 않습니다.

#### (5) `REQUESTS` / `INCLUDES` — 화면이 서버를 부르고, 화면이 화면을 끼운다

`screen.JspPlugin.run()`
1. 진입점 표에서 `주소 → 처리 메소드들` 맵을 만듭니다(진입점 수만큼만 메모리를 쓴다).
2. JSP를 500개씩 읽어 `/`로 시작하는 경로처럼 생긴 글자를 **모두** 찾고, 진입점 주소와 **정확히 같은 것만** `F{jsp} ─REQUESTS→ 메소드`(MEDIUM, `via=PATH_TEXT`, `path`). `<script src>`로 불러온 스크립트의 주소도 봅니다. 모양(form action, ajax url, location.href …)을 가리지 않는 대신 진입점과 같지 않은 글자는 버려서 잘못 잇는 일을 줄입니다.
3. `jsp:include` / include 지시자의 경로가 가리키는 JSP가 하나로 정해지면 `INCLUDES`. `${...}`나 `<%`가 든 경로는 실행할 때 정해지므로 건너뜁니다.

`screen.RichClientPlugin`(WebSquare / Nexacro)
- 주소 글자 맞추기(PATH_TEXT, MEDIUM), 컨텍스트 경로 첫 마디를 떼고 다시 맞추기(LOW)
- 거래 ID 호출(`screens.calls` 규칙, `CALL_RULE`): ID → 메소드 이름 틀. 메소드가 하나면 HIGH, 여럿이면 모두 LOW, 없으면 UNRESOLVED. `idPattern` / `idCase` / `idMap`으로 ID를 다듬을 수 있다
- 폼 필드로 보내는 거래 ID(`screens.fields`)
- 공통 스크립트(.js / .xjs) 함수 안의 주소 · 거래 호출 · 화면 열기를 따라감(`COMMON_SCRIPT`, MEDIUM)
- 화면 열기(`screens.opens`, `OPEN_RULE`): 화면 ID → 경로 틀. 그 경로로 끝나는 화면이 하나일 때만 `INCLUDES`
- `argIndex`가 1 이상이면 인자를 나눠 그 자리가 따옴표 문자열 하나일 때만 씁니다. 문자열이 아니라 건너뛴 호출은 `SEMANTIC_STATUS`에 규칙별로 셉니다.

### 7.6 엣지 종류 한눈에

| 엣지 | from → to | 만드는 곳 | 근거 | 대표 신뢰도 | 증분에서 옮기나 |
|---|---|---|---|---|---|
| `CALLS` | METHOD → METHOD / EXTERNAL_METHOD | RESOLVE | 메소드 호출, `new`의 생성자 | HIGH~LOW | CARRY_RESOLVE |
| `CREATES` | METHOD · FIELD → TYPE | RESOLVE | `new Foo()` | HIGH / MEDIUM | CARRY_RESOLVE |
| `USES_TYPE` | METHOD · FIELD · TYPE → TYPE | RESOLVE | 타입 사용 | HIGH~LOW | CARRY_RESOLVE |
| `EXTENDS` / `IMPLEMENTS` | TYPE → TYPE / EXTERNAL_TYPE | RESOLVE | 상속 · 구현, 익명 클래스 | HIGH~LOW | CARRY_RESOLVE |
| `THROWS` | METHOD → TYPE | RESOLVE | throws 절 | HIGH~LOW | CARRY_RESOLVE |
| `ACCESSES_FIELD` | METHOD → FIELD | RESOLVE | `a.b` | HIGH / MEDIUM | CARRY_RESOLVE |
| `OVERRIDES` | METHOD → METHOD | LINK | 상속 사슬 + 같은 이름 · 인자 수 | HIGH / MEDIUM | 매번 새로 |
| `CALLS_POSSIBLE_IMPLEMENTATION` | METHOD → METHOD | LINK | CALLS(추상) + OVERRIDES | MEDIUM | 매번 새로 |
| `INJECTS` | TYPE → TYPE | SEMANTIC / Spring | 주입 애노테이션 필드 + USES_TYPE | USES_TYPE을 따름 | 매번 새로 |
| `EXECUTES_SQL` | METHOD → SQL(`S…`) | SEMANTIC / MyBatis · JavaSql | 매퍼 인터페이스, 계산한 SQL 이름, Java 문자열 SQL, JPA | HIGH / MEDIUM / UNRESOLVED | 매번 새로 |
| `READS_TABLE` / `WRITES_TABLE` | SQL → 테이블 이름 | SEMANTIC / MyBatis · JavaSql | SQL 파싱, 정규식, JPQL 엔티티 | HIGH / MEDIUM | 매번 새로 |
| `RENDERS` | METHOD → FILE(`F…`) | SEMANTIC / Jsp · Struts | 뷰 이름 문자열, forward | HIGH | 매번 새로 |
| `REQUESTS` | FILE → METHOD | SEMANTIC / Jsp · RichClient | 주소 글자 = 진입점, 거래 ID 규칙 | MEDIUM(HIGH / LOW) | 매번 새로 |
| `INCLUDES` | FILE → FILE | SEMANTIC / Jsp · RichClient | include, 화면 열기 규칙 | HIGH / MEDIUM | 매번 새로 |

### 7.7 엣지를 따라가 보기

```
order/list.jsp (F81)
  └─REQUESTS (MEDIUM, path=/order/list.do)──▶ OrderController#list()
        ├─RENDERS (HIGH, view=order/list)───▶ order/list.jsp (F81)
        ├─CALLS (HIGH)──────────────────────▶ OrderService#findOrders()   [interface]
        └─CALLS_POSSIBLE_IMPLEMENTATION ────▶ OrderServiceImpl#findOrders()
                                                 └─CALLS (HIGH)──▶ OrderDAO#selectList()
                                                       └─EXECUTES_SQL (HIGH)──▶ Order.selectOrderList (S203)
                                                              └─READS_TABLE (HIGH)──▶ TB_ORDER
```

이 사슬이 있으면 "TB_ORDER를 바꾸면 어느 화면이 영향을 받나"(영향도 분석)를 거꾸로 따라갈 수 있습니다. 호출 그래프 조회는 깊이 5 이하이고 `UNION`으로 중복을 걸러, 순환이 있어도 끝납니다.

### 7.8 다시 돌려도 결과가 같은 까닭

| 만든 단계 | 지우는 범위 |
|---|---|
| RESOLVE | 파일 단위: `DELETE … WHERE file_id = ?` 뒤 다시 넣음 |
| CARRY_RESOLVE | 파일 단위: 옮길 파일의 관계를 먼저 지우고(`deleteCarriedRows`) 복사 |
| LINK | 리비전 단위: `OVERRIDES`, `CALLS_POSSIBLE_IMPLEMENTATION` 전부 |
| SEMANTIC | 리비전 단위: `clear()`(INJECTS, 화면 엣지, 진입점 등) + MyBatis가 시작할 때 SQL 관계 전부 + JpaPlugin이 JPA statement 전부 |

`rerunFrom=RESOLVE`를 주면 RESOLVE · LINK · SEMANTIC · DOCUMENT의 상태가 지워져 이 차례로 다시 만들어집니다.

---

## 8. 분석이 끝난 뒤

1. 마지막 단계가 끝나면 Job이 `DONE` 또는 `DONE_WITH_WARNING`(파일 단위 오류가 하나라도 있으면), 리비전이 `READY`가 됩니다. 이제 이 리비전은 다음 증분 분석의 기준이 될 수 있습니다.
2. `RetentionService.applyQuietly()`가 보관 개수(`retention.max-revisions`, 기본 5)를 넘는 옛 READY 리비전을 지웁니다. 돌고 있는 Job의 리비전이나 돌고 있는 증분 분석의 기준 리비전은 지우지 않습니다. 실패해도 분석 결과에는 영향이 없습니다.
3. 화면의 폴링이 끝난 상태를 보고 멈추고 리비전 표를 다시 읽습니다.
4. 리비전 표의 [요약]은 `GET /api/revisions/{id}`와 `/rag`를 불러 카드와 "한눈에 보기"를 그립니다. 증분 리비전이면 `incremental`(옮겨 온 파일 수, 단계별 옮긴 / 분석한 파일 수)도 함께 옵니다. 호출 해석률이 95% 이상이면 "믿고 써도 된다", 80% 미만이면 "jar 위치를 적고 다시 분석하라"고 안내합니다. "적용 진단"은 `GET /api/revisions/{id}/diagnostics`입니다.
5. 임베딩은 `EmbeddingWorker`가 뒤에서 계속합니다. 끝나기 전에도 이름 검색, 호출 관계, 영향도는 바로 쓸 수 있습니다.

---

## 9. 코드 위치 모음

Java 클래스는 `dstone-knowledge/src/main/java/net/dstone/knowledge/` 아래입니다.

| 구분 | 파일 |
|---|---|
| 화면 | `dstone-boot/src/main/webapp/WEB-INF/views/knowledge/index.jsp` |
| 화면 스크립트 | `dstone-boot/src/main/webapp/knowledge/assets/js/project.js`, `knowledge-common.js` |
| 프록시 | `dstone-boot/src/main/java/net/dstone/boot/knowledge/controller/KnowledgeProxyController.java`, `service/KnowledgeProxyService.java` |
| 시작 API | `api/controller/ProjectController.java`, `api/service/AnalysisService.java`(기준 리비전 고르기 `findBaseRevision`) |
| 상태 · 취소 API | `api/controller/AnalysisController.java` |
| Job | `analysis/job/AnalysisJobRunner.java`, `FilePassRunner.java`, `FileWatchdog.java`, `common/config/ConfigJob.java` |
| 단계 | `analysis/pass01/scan/ScanPass.java`, `pass02/carry/CarryPass.java`, `pass03/declare/DeclarePass.java` · `DeclarationCollector.java` · `JavaSqlCollector.java`, `pass04/resource/ResourcePass.java`, `pass05/carryresolve/CarryResolvePass.java`, `pass06/resolve/ResolvePass.java` · `ReferenceResolver.java` · `DbTypeSolver.java`, `pass07/link/LinkPass.java`, `pass08/semantic/SemanticPass.java`, `pass09/document/DocumentPass.java` |
| 증분 분석 SQL | `dstone-knowledge/src/main/resources/sqlmap/analysis/CarryDao.xml`(`markCarriedFiles`, `carryFilePasses`, `copyRows`, `carryResolvePasses`, `resetReferencesToResolve`, `selectCarrySummary`), `api/dao/CarryDao.java` |
| 의미 플러그인 | `analysis/pass08/semantic/` 아래 `web/`(Spring · 서블릿 · Struts · XML 진입점), `sql/`(MyBatis · JPA · Java SQL), `screen/`(JSP · 리치클라이언트 · 메뉴), `layer/`, `dictionary/`, `rule/`(규칙 파일) |
| 엣지 SQL | `sqlmap/analysis/RelationDao.xml`(RESOLVE 저장, LINK, 지표), `SemanticDao.xml`(INJECTS, RENDERS, HTTP 진입점 재료, clear), `ResourceDao.xml`(매퍼 EXECUTES_SQL, 테이블), `JavaSqlDao.xml`(Java SQL · JPA) |
| 스키마 | `dstone-knowledge/src/main/resources/schema/02-create-table-postgresql-dstone-knowledge.sql`(`analysis_revision.parent_revision_id`, `analysis_file.carried_from_file_id`, `analysis_file_pass.carried`, `analysis_reference`, `analysis_relation`, `kg_node`, `kg_edge`) |
| 규칙 파일 | `dstone-knowledge/conf/rules/*.yml`, `conf/rules/README.md` |
| 설계 메모 | `.claude/docs/knowledge-core.md`, `.claude/docs/knowledge-search-incremental.md`(증분 분석과 전체 분석 비교 절차), `docs/11.dstone-knowledge.md`(4.14절 증분 분석) |
