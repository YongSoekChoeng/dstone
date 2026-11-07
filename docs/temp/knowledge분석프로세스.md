# Knowledge "분석 시작" 처리 흐름

`dstone-boot` → knowledge → **프로젝트 · 분석** 화면에서 [분석 시작]을 누른 순간부터 분석이 끝나고 그래프의 선(엣지)이 만들어지기까지를 코드 순서대로 따라갑니다.
뒤쪽 6장에서 엣지를 어떻게 만드는지 따로 자세히 다룹니다.

> 기준: 2026-11-07 `main` (커밋 `e6b15e44`). 파일 경로는 저장소 루트 기준입니다.

---

## 0. 한눈에 보기

```
[브라우저]                         [dstone-boot :7081]                  [dstone-knowledge :4081]
project.js                         KnowledgeProxyController              ProjectController
 startAnalysis() ──POST────────▶   /knowledge/api/call.do  ──POST───▶   /api/projects/{id}/analyses
   K.call()                         KnowledgeProxyService                  AnalysisService.startAnalysis()
                                    (경로 검사, X-API-Key)                   ├ 리비전 생성/재사용
                                                                            ├ analysis_job INSERT (READY)
                                                                            └ AnalysisJobRunner.submit()  ──▶ 백그라운드 스레드
   ◀──────────── 202 {analysisId, revisionId ...} ◀───────────────────────
 watchJob()  ──3초마다 GET /api/analyses/{analysisId}──────────────────▶  AnalysisService.getAnalysis()

                                          [백그라운드: analysis-job-N 스레드]
   SCAN(100) → CARRY(150) → DECLARE(200) → RESOURCE(250) → CARRY_RESOLVE(290)
   → RESOLVE(300) → LINK(400) → SEMANTIC(500) → DOCUMENT(600)
                                          [별도 데몬: EmbeddingWorker → rag_embedding 비우기]
```

엣지가 생기는 곳은 세 단계뿐입니다.

| 단계 | 엣지를 만드는 방법 | 대표 엣지 |
|---|---|---|
| RESOLVE | 파일마다 AST를 다시 보고 "이름만 적힌 참조"를 실제 대상에 연결 | `CALLS`, `EXTENDS`, `IMPLEMENTS`, `USES_TYPE`, `CREATES`, `THROWS`, `ACCESSES_FIELD` |
| LINK | 이미 만든 엣지끼리 SQL로 엮기 | `OVERRIDES`, `CALLS_POSSIBLE_IMPLEMENTATION` |
| SEMANTIC | 애노테이션·매퍼·화면 파일과 규칙 파일(`conf/rules`)로 의미 붙이기 | `INJECTS`, `EXECUTES_SQL`, `READS_TABLE`, `WRITES_TABLE`, `RENDERS`, `REQUESTS`, `INCLUDES` |

---

## 1. 프론트엔드 (브라우저)

### 1.1 화면 요소 — `dstone-boot/src/main/webapp/WEB-INF/views/knowledge/index.jsp`

프로젝트 표에서 프로젝트를 고르면(`selectProject`) 분석 패널(`#kn-analysis-panel`)이 열립니다. 입력 칸은 세 개입니다.

| 요소 id | 뜻 | 요청 본문 키 |
|---|---|---|
| `kn-revision-label` | 리비전 라벨. 비우면 서버가 현재 시각으로 만든다. 이미 있는 라벨이면 그 리비전을 이어서 분석 | `revisionLabel` |
| `kn-rerun-from` | "이 단계부터 다시" (SCAN ~ DOCUMENT) | `rerunFrom` |
| `kn-incremental` | "바뀐 파일만 다시 분석" 체크 | `incremental` |
| `kn-analysis-start-btn` | [분석 시작] 버튼 | — |

### 1.2 클릭 처리 — `dstone-boot/src/main/webapp/knowledge/assets/js/project.js`

`init()`에서 버튼에 `startAnalysis`를 붙여 둡니다.

```js
function startAnalysis() {
    var body = {};
    if (라벨 입력) body.revisionLabel = ...;
    if (증분 체크) body.incremental = true;
    if (단계 선택) body.rerunFrom = ...;
    K.call("POST", "/api/projects/" + encodeURIComponent(projectId) + "/analyses", null, body)
      .then(function (started) {
          // "분석을 시작했습니다: 리비전 #12 (20261107-153000), 증분 분석(기준 #11)"
          K.message(...);
          watchJob(started.analysisId);   // 진행 상황 폴링 시작
          loadRevisions();                // 리비전 표 새로 고침
      })
      .catch(function (err) { K.message(..., err.message, true); });
}
```

값이 빈 칸은 본문에 넣지 않습니다. 아무것도 적지 않으면 `{}`가 갑니다.

### 1.3 공통 호출 함수 — `knowledge-common.js`의 `call()`

브라우저는 dstone-knowledge를 직접 부르지 않습니다. 모든 호출이 dstone-boot의 프록시 하나로 모입니다.

```js
fetch(contextPath + "/knowledge/api/call.do", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ method: "POST", path: "/api/projects/P1/analyses", query: null, body: {...} })
}).then(readResponse);
```

`readResponse`는 4xx/5xx이면 응답의 `message`를 담은 `Error`를 던집니다. 그래서 화면 쪽 `catch`는 서버가 준 문장을 그대로 보여 줍니다.

### 1.4 진행 상황 폴링 — `watchJob()` / `renderJob()`

- 3초마다 `GET /api/analyses/{analysisId}`를 부릅니다.
- 상태가 `READY`/`RUNNING`이 아니면(DONE, DONE_WITH_WARNING, FAILED, CANCELLED) 폴링을 멈추고 리비전 표를 다시 읽습니다.
- `renderJob`은 Job 배지, 현재 단계(`currentPass`), 오류 건수, 단계별 표(`passes`: 단계·상태·처리·전체·시작·끝), 앞쪽 오류 20건을 그립니다.
- [취소] 버튼은 `POST /api/analyses/{id}/cancel`을 보냅니다. 돌고 있는 단계가 다음 확인 지점(`checkCancelled()`)에 닿아야 멈춥니다.

---

## 2. dstone-boot 프록시

### 2.1 `net.dstone.boot.knowledge.controller.KnowledgeProxyController`

`@PostMapping("/knowledge/api/call.do")`가 `{method, path, query, body}`를 받아 서비스로 넘깁니다. 로그인한 사용자만 이 주소를 쓸 수 있으므로(Spring Security), 로그인이 없는 내부 서버인 dstone-knowledge 앞을 dstone-boot가 막아 주는 셈입니다.

### 2.2 `net.dstone.boot.knowledge.service.KnowledgeProxyService.call()`

1. **메소드 검사** (`methodOf`): GET / POST / DELETE만 허용. 나머지는 400.
2. **주소 만들기** (`uriOf`)
   - 기준 주소는 `interface.knowledge.base-url` (`KNOWLEDGE_HOST`/`KNOWLEDGE_PORT`로 채움). 없으면 503.
   - `path`는 `/api/`로 시작해야 하고 `..`, `//`, `?`, `#`, `\`가 있으면 400. 다른 서버나 경로로 새지 않게 막는 장치입니다.
   - `query`의 빈 값은 빼고 붙인 뒤 인코딩합니다.
3. **API 키**: `interface.knowledge.api-key`가 있으면 `X-API-Key` 헤더를 붙입니다(dstone-knowledge의 `ApiKeyAuthFilter`가 켜져 있을 때 필요).
4. **전송**: WebClient(읽기 제한 120초, 응답 최대 32MB)로 보내고, 상태 코드와 본문을 **손대지 않고** 돌려줍니다.
5. **연결 실패**: 502와 `{status, message}`. 예외를 던지지 않는 이유는 dstone-boot 공통 예외 처리가 상태 코드를 200으로 바꿔 버리기 때문입니다.

---

## 3. dstone-knowledge: 분석 시작 API

### 3.1 `api.controller.ProjectController.startAnalysis()`

`POST /api/projects/{projectId}/analyses`. 본문에서 `revisionLabel`, `rerunFrom`, `incremental`, `baseRevisionId`를 꺼내 `AnalysisService.startAnalysis()`를 부르고 **202 Accepted**로 바로 돌아옵니다. 분석은 아직 시작 전입니다.

### 3.2 `api.service.AnalysisService.startAnalysis()` — `synchronized`

"돌고 있는 Job이 있나" 확인과 Job 등록 사이에 다른 요청이 끼지 못하게 메소드 전체를 동기화합니다.

| 순서 | 하는 일 | 실패 시 |
|---|---|---|
| 1 | 프로젝트 조회, `localPath`가 실제 폴더인지 확인 | 400 |
| 2 | 같은 프로젝트에 READY/RUNNING Job이 있는지 (`countActiveJobByProject`) | 409 |
| 3 | 라벨 결정: 없으면 `yyyyMMdd-HHmmss`. 200자 초과면 거절 | 400 |
| 4 | 같은 라벨의 리비전이 있으면 **이어 하기**(`resumed=true`), 없으면 새 리비전 INSERT | — |
| 5 | 새 리비전 + 증분이면 기준 리비전을 찾음(`findBaseRevision`: 지정 번호 또는 가장 최근 READY). 없거나 `analyzer-version`이 다르면 전체 분석으로 돌리고 `note`에 이유를 남김 | — |
| 6 | `rerunFrom`이 있으면 (이어 하기일 때만) 그 단계와 뒤 단계들의 상태를 지움(`resetRevisionPasses`) | 400 |
| 7 | Job ID 발급: `A` + 날짜 + 그날 일련번호 3자리 (예: `A20261107001`) | — |
| 8 | `analysis_job` INSERT (READY) | — |
| 9 | `AnalysisJobRunner.submit()`. 대기열이 꽉 차면 Job을 FAILED로 바꾸고 503 | 503 |
| 10 | `{analysisId, projectId, revisionId, revisionLabel, resumed, incremental, baseRevisionId, note, rerunPasses, status:"READY"}` 반환 | — |

**이어 하기의 원리**: 단계마다 끝나면 `analysis_revision_pass`에 DONE이 남습니다. 같은 라벨로 다시 시작하면 DONE인 단계를 건너뜁니다. 죽은 분석을 이어 가거나, 새로 추가된 단계만 옛 리비전에 돌릴 때 이 방법을 씁니다.

---

## 4. 백그라운드 Job 실행

### 4.1 스레드 풀 — `common.config.ConfigJob.analysisJobExecutor`

- 동시 실행 수 `dstone.knowledge.job.max-concurrent` (기본 2), 대기열 100, 스레드 이름 `analysis-job-`.
- 한 프로젝트에서는 한 번에 하나만 돌고(3.2의 2번), 서로 다른 프로젝트는 최대 2개가 같이 돕니다.

### 4.2 `job.AnalysisJobRunner`

`submit()`은 Job을 `activeJobs` 맵(취소 요청 전달용)에 넣고 풀에 `Runnable`을 맡깁니다. 그 스레드에서는 `ThreadContext.put("SUPPRESS_SQL_LOG","Y")`로 SQL 로그를 끕니다. 수만 건의 INSERT가 로그를 덮지 않게 하려는 것입니다.

`runJob()`의 흐름:

```
checkCancelled()
analysis_job → RUNNING, analysis_revision → ANALYZING
for pass in (AnalysisPass 빈들을 order() 순으로 정렬):
    checkCancelled()
    if 이 리비전에서 pass가 DONE: 건너뜀
    analysis_job.current_pass = pass, analysis_revision_pass 시작 기록
    pass.run(context)
    analysis_revision_pass → DONE
끝: analysis_error가 있으면 DONE_WITH_WARNING, 없으면 DONE
    analysis_revision → READY
    RetentionService.applyQuietly()  // 오래된 리비전 정리
```

- `JobCancelledException` → Job CANCELLED, 리비전 CREATED. 하던 단계는 끝나지 않은 채로 남아 다음에 거기부터 다시 합니다.
- 그 밖의 `Throwable` → Job FAILED, 리비전 FAILED.
- 서버가 죽어서 RUNNING으로 남은 Job은 다음 기동 때 `AnalysisJobRecovery`가 FAILED로 정리합니다.

### 4.3 단계(Pass) 목록

`AnalysisPass` 인터페이스(`name()`, `order()`, `run()`)를 구현한 빈을 Spring이 모아 줍니다. 새 단계를 넣으려면 빈만 만들면 됩니다.

| order | 이름 | 클래스 | 단위 | 하는 일 |
|---|---|---|---|---|
| 100 | SCAN | `scanner.ScanPass` | 폴더 전체 | 파일 목록 → `analysis_file` |
| 150 | CARRY | `incremental.CarryPass` | 리비전 | (증분) 안 바뀐 파일의 선언·자원 복사 |
| 200 | DECLARE | `parser.DeclarePass` | 파일 | Java/JSP 파싱 → 선언 + **이름만 적힌 참조** |
| 250 | RESOURCE | `resource.ResourcePass` | 파일 | 매퍼 XML, 설정, pom, Spring XML, DDL 저장 |
| 290 | CARRY_RESOLVE | `incremental.CarryResolvePass` | 리비전 | (증분) 옮겨도 되는 "풀린 관계"만 복사 |
| 300 | RESOLVE | `resolver.ResolvePass` | 파일 | 참조 → **엣지** |
| 400 | LINK | `resolver.LinkPass` | SQL | **엣지끼리 엮기** + 품질 지표 |
| 500 | SEMANTIC | `semantic.SemanticPass` | SQL + 일부 파일 | 진입점·계층 + **의미 엣지** |
| 600 | DOCUMENT | `rag.DocumentPass` | 파일 | RAG 문서/청크, 임베딩 대기열 |

---

## 5. 단계별 상세

### 5.1 파일 단위 실행기 — `job.FilePassRunner`

DECLARE, RESOURCE, RESOLVE, DOCUMENT는 모두 이 실행기를 거칩니다.

1. `seedFilePass`: 이 단계에서 처리할 파일마다 `analysis_file_pass` 행(PENDING)을 만듭니다. 이미 있으면 그대로 둡니다.
2. PENDING 파일을 100개씩 꺼내 하나씩 처리합니다. 100개마다 진행 건수를 기록하므로 화면의 "처리/전체" 숫자가 이렇게 올라갑니다.
3. 파일 하나는 두 걸음입니다.
   - **prepare**: 감시 스레드(`FileWatchdog`)에서 실행. 읽기·파싱·해석만 하고 **DB에 쓰지 않습니다**.
   - **write**: Job 스레드에서 트랜잭션 하나로 결과 저장 + "이 파일 끝남" 표시를 같이 커밋.
4. 파일 하나가 전체를 붙잡지 못하게 막는 장치
   - 예외 → 그 파일만 FAILED(`INTERNAL`)
   - `StackOverflowError` → FAILED(`TOO_DEEP`)
   - prepare가 600초(`file-hard-timeout-seconds`)를 넘김 → FAILED(`HUNG`), 작업 스레드를 버리고 `handler.reset()`. 버린 수가 3(`max-stuck-files`)을 넘으면 Job 전체 실패

prepare에서 DB에 쓰지 않는 이유가 여기 있습니다. 버려진 스레드가 커넥션이나 잠금을 쥐고 있지 않아야 하기 때문입니다.

### 5.2 SCAN — 파일 목록 만들기

- `Files.walkFileTree`로 `localPath`를 훑습니다. `scan.exclude-dirs`의 폴더는 통째로 건너뜁니다.
- `FileClassifier.languageOf()`가 분석 대상이 아니라고 하면(이미지, class, jar 등) 무시합니다.
- 파일마다 언어, 파일 종류, 인코딩(`EncodingDetector`), SHA-256, 크기, 줄 수, Java면 패키지(정규식, 주석 제거 후)와 소스 루트, 빌드 파일이 있는 폴더 기준의 모듈을 구합니다.
- 5MB(`scan.max-file-bytes`)를 넘으면 해시만 구하고 `SKIPPED`.
- 200개(`commit-size`)씩 모아서 씁니다. 다시 돌면 같은 경로를 덮어써서 중복이 생기지 않습니다.

### 5.3 CARRY (증분일 때만)

내용 해시가 같은 파일의 DECLARE·RESOURCE 결과를 기준 리비전에서 복사하고, 그 파일의 해당 단계를 "끝남"으로 표시합니다. 그래서 DECLARE와 RESOURCE는 바뀐 파일만 처리합니다. 기준 리비전이 없으면 아무 일도 하지 않습니다.

### 5.4 DECLARE — 선언과 "이름만 적힌 참조"

`DeclarePass` → `JavaSourceParser` → `DeclarationCollector`. JAVA 파일을 먼저, 그다음 JSP를 같은 handler로 돌립니다.

**prepare (`parseFile`)**
1. 프로젝트에 `rootPackages`가 있으면 그 밖의 Java 파일은 읽지 않고 SKIPPED.
2. SCAN이 정한 인코딩으로 읽습니다. JSP면 `JspToJava.convert()`로 스크립틀릿을 `_jspService()` 몸통으로 바꿉니다. 지운 글자 자리에 줄바꿈을 남겨 줄 번호가 원본 JSP와 같습니다. Java 코드가 없는 JSP는 건너뜁니다.
3. 파싱은 언어 수준 21 → 8 → 1.4 순서로 시도합니다(옛 소스는 `enum`, `assert`를 변수 이름으로 쓰기도 하니까요).
4. `DeclarationCollector`가 **이 파일 하나만 보고** 선언과 참조를 뽑습니다.

**선언과 ID 규칙** (리비전이 달라도 같은 대상이면 같은 값)

| 대상 | 테이블 | ID |
|---|---|---|
| 타입 | `analysis_symbol` | SHA-1(프로젝트 \| 소스 루트 \| `T` \| FQN) |
| 메소드 | `analysis_method` | SHA-1(프로젝트 \| 소스 루트 \| `M` \| FQN#시그니처) |
| 필드 | `analysis_field` | SHA-1(프로젝트 \| 소스 루트 \| `F` \| FQN#이름) |
| 애노테이션 | `analysis_annotation` | 대상 종류 + 대상 ID |

시그니처는 **소스에 적힌 파라미터 타입 그대로**(제네릭만 지움)입니다. 다른 파일을 봐야 하는 FQN 해석을 넣으면 ID가 처리 순서에 따라 달라지기 때문입니다. Lombok이 만들 멤버는 `is_synthetic=true`로 함께 넣습니다(`LombokExpander`).

**참조** (`analysis_reference`, status=PENDING)

| ref_kind | 소스 모양 | 기록 위치 |
|---|---|---|
| `CALL` | `orderService.find(x)`, `this(...)`, `super(...)` | 메소드 **이름** 토큰 위치 |
| `CREATE` | `new Foo(...)` | 타입 위치 |
| `FIELD_ACCESS` | `a.b` | 이름 위치 |
| `TYPE_USE` | 변수·파라미터·반환 타입, 제네릭 인자 | 타입 위치 (한 멤버 안에서 같은 타입은 한 번만) |
| `EXTENDS` / `IMPLEMENTS` | 상속·구현 | 타입 위치 |
| `THROWS` | `throws X` | 타입 위치 |
| `ANONYMOUS_SUPER` | 익명 클래스의 상위 타입 | 생성식 위치 |
| `METHOD_REF` | `Foo::bar` | 식 위치 |

참조마다 `from_kind/from_id`(참조가 들어 있는 메소드·필드·타입), `name`, `scope_text`(예: `orderService`), `arg_count`, 줄·칸을 남깁니다. 호출 인자에 따옴표 문자열이 있으면 첫 인자는 `arg_text`, 둘째 인자부터는 `args_json`에 적힌 그대로 담습니다. 이 값이 나중에 SQL 이름이나 화면 이름을 찾는 단서가 됩니다.

**왜 위치를 이름 토큰으로 잡나**: `a.get().get()`처럼 이어진 호출은 식의 시작 위치가 모두 같습니다. 이름 토큰 위치라야 RESOLVE가 같은 노드를 다시 찾을 수 있습니다.

**write (`saveFile`)**: 같은 소스 루트에 같은 FQN이 이미 있으면 파일 이름이 클래스 이름과 같은 쪽을 남기고 다른 쪽은 SKIPPED(`DUPLICATE_TYPE`). 그다음 `replaceDeclarationsInBatch`로 그 파일의 선언·참조를 지우고 다시 넣습니다.

> 이 단계에서는 **엣지를 하나도 만들지 않습니다.** "타입이 메소드를 가진다"는 소속은 엣지가 아니라 `analysis_method.owner_symbol_id`, `analysis_field.owner_symbol_id` 컬럼으로 표현합니다. 스키마 주석에 `HAS_METHOD`/`HAS_FIELD`가 적혀 있지만 실제로 만드는 코드는 없습니다.

### 5.5 RESOURCE — Java가 아닌 파일 저장

MyBatis/iBATIS 매퍼 → `analysis_mapper`(statement 하나에 한 행, `<include>`는 표시만 남김), `.properties`/YAML → `analysis_config`(비밀값은 `****`), `pom.xml`/`build.gradle`/Spring XML/DDL → `analysis_resource`. 모든 XML은 `SafeXml`로 읽습니다. 여기서는 저장만 하고 연결은 SEMANTIC이 합니다. `<include>` 조각이 다른 파일에 있을 수 있어서 모든 파일을 읽은 뒤에 이어야 하기 때문입니다.

### 5.6 CARRY_RESOLVE (증분일 때만)

안 바뀐 파일이라도 그 파일이 부르는 다른 파일이 바뀌었으면 호출을 다시 풀어야 합니다. 그래서 다음 파일은 옮기지 않고 RESOLVE에 맡깁니다.
1. 기준 리비전에서 "바뀌었거나 없어진 파일의 선언"을 가리키던 파일
2. 기준 리비전에서 확실히 풀지 못한 참조 이름이 새로 생겼거나 고쳐진 파일의 선언 이름과 같은 파일

나머지는 기준 리비전의 관계를 복사하고 RESOLVE를 "끝남"으로 표시합니다. LINK와 SEMANTIC 엣지는 옮기지 않고 늘 리비전 전체에서 새로 만듭니다.

### 5.7 RESOLVE, LINK, SEMANTIC

엣지의 핵심이라 6장에서 따로 설명합니다.

### 5.8 DOCUMENT와 임베딩

- 파일마다 DB의 사실(타입, 메소드, 호출, 진입점, 실행 SQL, 여는 화면 등)과 줄 번호로 잘라 온 소스를 엮어 FILE/TYPE/METHOD 문서를 만듭니다. 매퍼는 statement마다 MAPPER, JSP는 VIEW 문서입니다. 다시 파싱하지 않습니다.
- METHOD 문서 앞쪽에는 "진입점 주소, 계층, 부르는 메소드, 가능한 구현, 불리는 곳" 같은 **엣지 정보가 한국어 문장**으로 들어갑니다. "주문 취소는 어디서 처리하나" 같은 질문은 소스와 닮지 않았기 때문입니다.
- 청크 해시를 `rag_embedding`에 PENDING으로 올리면 이 단계는 끝납니다. 실제 임베딩은 서버 안의 `EmbeddingWorker` 데몬이 따로 처리합니다. 그래서 Job이 DONE이 된 뒤에도 임베딩은 한동안 계속 돕니다(리비전 요약의 "임베딩 N%").

---

## 6. 엣지(관계) 정의 — 자세히

### 6.1 엣지가 사는 곳: `analysis_relation` 한 테이블

모든 종류의 엣지가 한 테이블에 들어갑니다.

| 컬럼 | 뜻 |
|---|---|
| `revision_id` | 어느 리비전의 엣지인가 |
| `from_kind`, `from_id` | 출발 노드 (METHOD / TYPE / FIELD / SQL / FILE) |
| `relation_type` | 엣지 종류 (`CALLS` 등) |
| `to_kind`, `to_id` | 도착 노드. 프로젝트 안에 있을 때만 `to_id`가 찬다 |
| `to_external` | 프로젝트 밖의 대상 이름 (예: `java.util.List.add(E)`, 테이블 이름 `TB_ORDER`) |
| `confidence` | HIGH / MEDIUM / LOW / UNRESOLVED |
| `resolution_status` | RESOLVED / HEURISTIC / UNRESOLVED / DYNAMIC |
| `properties_json` | 어떻게 찾았는지(`via`), 부가 정보(`crud`, `columns`, `path`, `declared` …) |
| `file_id`, `line_start` | 근거가 된 소스 위치 |

그래프 화면과 API는 이 테이블을 바로 쓰거나 VIEW로 읽습니다.

- `kg_node` VIEW = 타입 + 메소드 + 필드 + SQL statement(`'S'||mapper_id`) + 파일(`'F'||file_id`)
- `kg_edge` VIEW = `analysis_relation` 그대로 (`from_id → to_id`)

**노드 ID 체계**: 타입·메소드·필드는 40자 SHA-1, SQL statement는 `S123`, 파일(JSP·화면)은 `F456`. 접두어 덕분에 서로 겹치지 않습니다. 테이블은 노드가 없고 `to_external`에 이름만 둡니다.

**원칙 두 가지**
1. **불확실해도 지우지 않고 정도를 남긴다.** 짐작한 연결은 LOW/HEURISTIC, 못 푼 연결은 `UNRESOLVED`와 이유(`analysis_reference.fail_reason`)를 남깁니다.
2. **이름은 계산하고 추측하지 않는다.** SQL 이름, 화면 경로, 거래 ID는 따옴표 리터럴과 상수 추적으로 확정된 것만 씁니다.

### 6.2 두 걸음 구조: 참조 → 엣지

```
DECLARE (파일 하나만 봄)             RESOLVE (다른 파일도 봄)
analysis_reference                  analysis_relation
 from: OrderController#list()   ──▶  from: OrderController#list()
 CALL name=findOrders                CALLS → OrderService#findOrders()  HIGH
 scope=orderService                  (reference.status = RESOLVED)
 line 42, col 27
```

DECLARE는 순서와 무관하게 병렬로 돌 수 있는 일(파일 하나 보기)만 하고, 다른 파일이 필요한 판단은 모든 선언이 DB에 들어간 뒤 RESOLVE로 미룹니다. 메모리에 프로젝트 전체를 올리지 않기 위한 구조입니다.

### 6.3 RESOLVE — 참조를 엣지로 바꾸는 규칙

`ResolvePass` → `ReferenceResolver.resolveFile()`

**준비물 (Engine)**: 타입 해석기를 다음 순서로 엮습니다.
1. `DbTypeSolver`: "이 FQN은 어느 파일에 있나"를 `analysis_symbol`에서 찾아 그 파일만 파싱합니다. AST는 LRU 캐시(기본 100개)에만 둡니다.
2. `ReflectionTypeSolver(JdkClassFilter)`: JDK 타입(`java.`, `javax.`, `jdk.`, `org.w3c.dom.` 등)만. 거르지 않으면 대상의 `org.springframework…`를 분석기 자신이 쓰는 Spring 클래스로 풀어 버립니다.
3. `JarTypeSolver`들: 프로젝트 폴더의 `*.jar`와 프로젝트의 `classpath` 값. 대상 프로젝트를 빌드하지는 않습니다.

`CombinedTypeSolver`는 `NoCache`로 만들고, 파일마다 `JavaParserFacade.clearInstances()`를 불러 메모리가 쌓이지 않게 합니다.

**노드 맞추기**: 그 파일의 참조를 읽어 `"종류|줄|칸"` 키로 맵에 넣고, AST를 걸어 내려가며 같은 위치의 노드를 꺼냅니다(`take`). 끝까지 꺼내지 못한 참조는 "같은 위치의 코드를 찾지 못했습니다"로 UNRESOLVED가 됩니다.

**메소드 호출(`CALL`) 판정 사다리** — 위에서 풀리면 아래로 내려가지 않습니다.

| 단계 | 방법 | 결과 엣지 | confidence |
|---|---|---|---|
| 1 | JavaSymbolSolver로 `solve(call)` 성공, 선언이 프로젝트 소스에 있음 | `CALLS → METHOD(id)` | HIGH |
| 1' | 해석 성공, 프로젝트 타입이지만 소스에 없는 멤버(기본 생성자, enum `values()`) | DECLARE가 만든 멤버면 그것, 아니면 `EXTERNAL_METHOD`(`via=COMPILER_GENERATED`) | HIGH |
| 1'' | 해석 성공, 프로젝트 밖 메소드 | `CALLS → EXTERNAL_METHOD`, `to_external = java.util.List.add(E)` | HIGH |
| 2 | 해석 실패. 대상 식의 **타입**만 알아냄(변수 선언+import, Lombok 필드, 앞 호출의 반환 타입) → 그 타입이 프로젝트 밖 | `CALLS → EXTERNAL_METHOD Type.name` (`via=SCOPE_TYPE`) | MEDIUM |
| 3 | 그 타입과 프로젝트 안 상위 타입에서 이름+인자 수로 찾음 | 1개·Lombok 멤버 → HIGH, 1개·소스 멤버 → MEDIUM, 여러 개 → 전부 LOW (`via=OWNER_TYPE/SYNTHETIC_MEMBER`) | HIGH/MEDIUM/LOW |
| 3' | 타입은 프로젝트 안인데 그런 메소드가 없음 | 엣지 없음. 프로젝트 밖 상위 타입에서 물려받은 것으로 보고 **짐작하지 않음** | UNRESOLVED |
| 4 | 타입도 모름 → 프로젝트 전체에서 이름+인자 수가 같은 메소드가 5개 이하 | 후보 전부에 `CALLS` (`via=NAME`, `candidates=n`) | LOW |
| 5 | 후보가 없거나 5개보다 많음(`get`, `toString` 등) | 엣지 없음, 이유 기록 | UNRESOLVED |

**선언을 메소드 ID로 바꾸는 법 (`methodIdOf`)**: 이름으로 찾지 않습니다. 해석기가 돌려준 선언의 AST 노드가 "어느 파일(`FILE_ID`)의 몇 번째 줄"인지 보고 그 파일의 메소드 색인(`줄|이름|파라미터 수`)에서 찾습니다. 익명 클래스나 지역 클래스의 메소드는 타입 이름으로 가리킬 수 없기 때문입니다.

**그 밖의 참조**

| ref_kind | 엣지 | 판정 |
|---|---|---|
| `CREATE` (`new Foo()`) | `CREATES → TYPE` + 생성자가 풀리면 `CALLS → <init>` | 타입 판정과 같음 |
| `TYPE_USE` | `USES_TYPE` | 해석기 HIGH → import로 FQN 추정 MEDIUM(`via=IMPORT`) → 같은 단순 이름 타입이 하나뿐 LOW(`via=NAME`) → UNRESOLVED |
| `EXTENDS` / `IMPLEMENTS` / `THROWS` | 같은 이름의 엣지 | 위와 같음. 대상이 프로젝트 안이면 `to_kind=TYPE`, 밖이면 `EXTERNAL_TYPE` |
| `ANONYMOUS_SUPER` | 상위 타입이 인터페이스면 `IMPLEMENTS`, 아니면 `EXTENDS` (`anonymous:true`). 종류를 모르면 EXTENDS + `kindUnknown` | |
| `FIELD_ACCESS` | `ACCESSES_FIELD → FIELD` 또는 `EXTERNAL_FIELD` | |
| `METHOD_REF` (`Foo::bar`) | 이름으로만 찾음(4단계) | LOW |
| 타입 파라미터 `<T>` | 엣지 없음, IGNORED | |

**시간 제한**: 파일 하나에 120초(`resolve.file-timeout-seconds`). 넘기면 그때까지 푼 것은 저장하고 남은 참조는 "시간 초과"로 UNRESOLVED, 파일에는 `RESOLVE_TIMEOUT` 경고.

**저장 (`RelationDao.replaceRelationsInBatch`)**: 한 트랜잭션에서 `DELETE FROM analysis_relation WHERE file_id = ?` → 새 엣지 INSERT → 참조 상태 UPDATE. 파일 단위로 지우고 다시 넣으므로 다시 돌려도 엣지가 두 번 생기지 않습니다.

### 6.4 LINK — 엣지끼리 엮기 (SQL만)

`LinkPass`는 한 트랜잭션에서 다음을 순서대로 합니다. 파일을 읽지 않습니다.

1. `deleteLinkRelations`: 이 리비전의 `OVERRIDES`, `CALLS_POSSIBLE_IMPLEMENTATION`을 지움 (멱등)
2. `insertOverrides`
   - 재귀 CTE로 `EXTENDS`/`IMPLEMENTS` 엣지를 따라 올라가 (타입, 조상) 쌍을 만듭니다. 최대 20단계, `UNION`으로 중복 제거, 자기 자신이 조상으로 나오는 행(순환 상속)은 버립니다.
   - 하위 타입 메소드와 조상 메소드가 이름·파라미터 수가 같고, 생성자·static·private가 아니면 `메소드 ─OVERRIDES→ 상위 메소드`.
   - 시그니처 글자까지 같으면 HIGH, 아니면 MEDIUM. 단, 조상에 시그니처까지 같은 메소드가 따로 있으면 그쪽만 잇습니다.
3. `insertPossibleImplementations`
   - `호출자 ─CALLS→ X`가 있고, X가 **몸통 없는 메소드(abstract/인터페이스)**이고, `Y ─OVERRIDES→ X`인 구현 Y가 있으면
     `호출자 ─CALLS_POSSIBLE_IMPLEMENTATION→ Y` (MEDIUM, `properties_json.declared = X`).
   - 몸통 있는 메소드까지 넣지 않는 이유: `BaseVO.setId()` 호출 천여 건이 재정의한 하위 클래스 전부로 퍼져 잡음이 됩니다(`anybiz_prd`에서 27,715건). 그런 경우는 `OVERRIDES`를 따라가면 됩니다.
4. `replaceMetrics`: 품질 지표(`parseSuccessRate`, `callCount`, `callResolutionRate`, `callHeuristicRate`, `callUnresolvedRate`, `typeResolutionRate`, `relationCount`)를 `analysis_metric`에 씁니다. 리비전 요약의 "한눈에 보기"가 이 값을 씁니다.

```
OrderController#list ─CALLS(HIGH)──────────────────────▶ OrderService#findOrders (interface)
        │                                                        ▲
        └─CALLS_POSSIBLE_IMPLEMENTATION(MEDIUM)──▶ OrderServiceImpl#findOrders ─OVERRIDES(HIGH)┘
```

### 6.5 SEMANTIC — 의미 엣지

`SemanticPass`는 시작할 때 규칙 파일(`conf/rules/*.yml`)을 다시 읽어(`SemanticRuleRegistry.rulesFor`) 컨텍스트에 싣고, **한 트랜잭션**에서 다음을 합니다.

1. `SemanticDao.clear()`: 진입점, SEMANTIC이 만든 `analysis_resource` 행, `INJECTS`, 계층, proxy 표시, 화면 엣지(`RENDERS`/`REQUESTS`/`INCLUDES`)를 지움
2. 적용한 규칙 묶음을 `RULE_SET` 행으로 기록(이름, 파일, 해시)
3. `SemanticPlugin` 빈을 order 순으로 실행

도중에 실패하면 지운 것까지 함께 되돌아갑니다. 규칙 파일만 고쳤다면 `rerunFrom=SEMANTIC`으로 이 단계부터 다시 돌리면 됩니다(재기동 불필요).

| order | 플러그인 | 만드는 엣지 / 결과 |
|---|---|---|
| 100 | `SpringPlugin` | HTTP·스케줄·리스너 진입점, **`INJECTS`**, proxy 표시, 애노테이션 계층 |
| 200 | `ServletPlugin` | web.xml / `@WebServlet` → SERVLET 진입점 |
| 250 | `StrutsPlugin` | Struts 진입점, **`RENDERS`**(forward/result) |
| 300 | `PlainJavaPlugin` | `main`, Thread/Runnable, JSP 진입점 |
| 400 | `MyBatisPlugin` | **`EXECUTES_SQL`**, **`READS_TABLE`**, **`WRITES_TABLE`** |
| 500 | `JspPlugin` | **`RENDERS`**, **`REQUESTS`**, **`INCLUDES`**, VIEW 계층 |
| 510 | `RichClientPlugin` | WebSquare/Nexacro 화면의 **`REQUESTS`**, **`INCLUDES`**, TRANSACTION 진입점 |
| 520 | `ScreenMenuPlugin` | 화면 메뉴 정보 |
| 800 | `ConfiguredLayerPlugin` | 상위 타입 규칙으로 계층 |
| 900 | `NamingLayerPlugin` | 이름 접미사로 계층(LOW, 빈 것만) |
| 950~980 | `SourceNamePlugin`, `MethodTitlePlugin`, `ScreenDictionaryPlugin`, `TableDictionaryPlugin` | 한글명·사전 (엣지 없음) |

앞 단계의 진입점이 있어야 뒤 단계가 화면 주소를 맞출 수 있으므로 순서가 중요합니다.

#### (1) `INJECTS` — `SemanticDao.xml#insertInjects`

```
analysis_annotation (FIELD에 @Autowired/@Inject/@Resource 등, 규칙 injects.annotations)
  JOIN analysis_field
  JOIN analysis_relation (그 필드의 USES_TYPE 엣지)   ← RESOLVE가 만든 것
⇒ 필드 주인 타입 ─INJECTS→ 필드 타입   (confidence는 USES_TYPE 것을 물려받음)
```

`List<Foo>`처럼 타입이 여럿이면 맨 앞(바깥) 타입 하나만 씁니다. 애노테이션은 단순 이름으로 맞춥니다(와일드카드 import면 FQN을 알 수 없어서). 생성자·setter 주입은 아직 다루지 않습니다.

#### (2) `EXECUTES_SQL` — `MyBatisPlugin`

먼저 `analysis_mapper`로 색인을 만듭니다(`네임스페이스.id → S{mapper_id}`, 조각은 따로).

- **인터페이스 방식** (`ResourceDao.xml#insertInterfaceMapperRelations`): 매퍼 namespace = 인터페이스 FQN, statement id = 메소드 이름이면 `메소드 ─EXECUTES_SQL→ S{id}` (HIGH, `via=MAPPER_INTERFACE`).
- **문자열 방식** (`linkCalls`):
  1. 규칙 파일의 `callArguments`(target: SQL) 하나마다 맞는 호출을 `analysis_reference`에서 500건씩 꺼냅니다(`selectMapperCallPage`). 규칙은 메소드 이름, `argIndex`, `receiverTypes`(RESOLVE가 정한 대상 타입과 그 하위 타입)로 거릅니다. 예: `sqlSession.selectList("Order.list", vo)`, JEF의 `new QueryProperty("파일.id")`.
  2. 인자 글자를 `ConstantEvaluator`로 **계산**합니다. 리터럴, `+`, 자기·상위 타입 필드의 초기값, 파라미터 없는 메소드의 단일 `return`까지 따라갑니다. 지역 변수나 파라미터가 하나라도 섞이면 전체를 "모름"으로 둡니다.
     ```
     getSqlSession().selectList(getOrderMapper() + "selectOrderList", vo)
       getOrderMapper() → BaseDAO에서 return orderMapper; → "Order."
       ⇒ "Order.selectOrderList"
     ```
  3. 규칙의 `prefix`/`suffix`(template)를 붙이고, 이름 모양(영문·숫자·`_` `.` `-`)인지 확인합니다.
  4. statement를 찾습니다. 같은 이름이 여러 파일에 있으면 부르는 Java와 같은 소스 루트의 것을 고릅니다(펼친 WAR의 `WEB-INF/src`와 `WEB-INF/classes`). 점이 없는 id만 넘기면(iBATIS) 그 id가 하나뿐일 때만 잇습니다.
  5. 찾으면 `EXECUTES_SQL → S{id}` HIGH(`via=STATEMENT_NAME`). 이름은 알았지만 statement가 없으면 `to_id=null`, `to_external=이름`, **UNRESOLVED 엣지로 남깁니다**.

#### (3) `READS_TABLE` / `WRITES_TABLE` — `MyBatisPlugin.extractTables`

statement를 500건씩 읽어 `<include>`를 조각 본문으로 채우고(최대 6단계) `SqlTableExtractor`로 테이블을 뽑습니다.

- JSQLParser(fast mode, 호출 스레드에서만) 성공 → HIGH, `columns` 포함
- 실패하거나 괄호 12단계 초과·2만 자 초과 → `FROM/JOIN/INTO/UPDATE` 정규식 → MEDIUM, `via=PATTERN`
- `crud`가 R이면 `READS_TABLE`, C/U/D면 `WRITES_TABLE`. 테이블은 노드가 없어 `to_external`에 이름만 둡니다. SQL 주석의 한글명은 `columnComments`/`tableComment`로 남깁니다.

#### (4) `RENDERS` — 메소드가 화면을 연다

- `JspPlugin` → `SemanticDao.xml#insertViewArgumentRelations`: 규칙의 `callArguments`(target: VIEW, 예: `new ModelAndView("x")`, `setViewName("x")`)에 맞는 호출 중 인자가 **따옴표 하나짜리 리터럴**인 것만 봅니다. `prefix + x + suffix`로 끝나는 JSP가 **정확히 하나**일 때만 `메소드 ─RENDERS→ F{file_id}` HIGH.
- `StrutsPlugin`: struts-config의 forward/result.
- `return "order/list"` 방식이나 Tiles 정의 이름은 아직 다루지 않습니다.

#### (5) `REQUESTS` / `INCLUDES` — 화면이 서버를 부른다, 화면이 화면을 끼운다

`JspPlugin.run()`:
1. 진입점 표에서 `주소 → 처리 메소드들` 맵을 만듭니다(진입점 수만큼만 메모리 사용).
2. JSP를 500개씩 읽어 `/`로 시작하는 경로처럼 생긴 글자를 **전부** 찾고, 진입점 주소와 **정확히 같은 것만** `F{jsp} ─REQUESTS→ 메소드` (MEDIUM, `via=PATH_TEXT`, `path`). 모양(form action, ajax url, location.href …)을 따지지 않는 대신, 진입점과 같지 않은 글자는 버려서 잘못 잇는 일을 줄입니다.
3. `jsp:include`/include 지시자의 경로가 가리키는 JSP가 하나로 정해지면 `INCLUDES`. `${...}`나 `<%`가 들어간 경로는 실행 때 정해지므로 건너뜁니다.

`RichClientPlugin`(WebSquare/Nexacro):
- 주소 글자 매칭(PATH_TEXT, MEDIUM), 컨텍스트 경로 첫 마디를 떼고 다시 맞추기(LOW)
- 거래 ID 호출(`screens.calls` 규칙, `CALL_RULE`): ID → 메소드 이름 틀. 메소드가 하나면 HIGH, 여럿이면 모두 LOW, 없으면 UNRESOLVED
- 폼 필드로 거래 ID 전송(`screens.fields`)
- 공통 스크립트(.js/.xjs) 함수 안의 주소·거래 호출·화면 열기를 따라감(`COMMON_SCRIPT`, MEDIUM)
- 화면 열기(`screens.opens`, `OPEN_RULE`): 화면 ID → 경로 틀, 그 경로로 끝나는 화면이 하나일 때만 `INCLUDES`
- `argIndex` ≥ 1이면 인자를 나눠 그 자리가 따옴표 리터럴 하나일 때만 씁니다. 리터럴이 아니라 건너뛴 호출은 `SEMANTIC_STATUS`에 규칙별로 셉니다.

### 6.6 엣지 종류 총정리

| 엣지 | from → to | 만드는 곳 | 근거 | 대표 신뢰도 |
|---|---|---|---|---|
| `CALLS` | METHOD → METHOD / EXTERNAL_METHOD | RESOLVE | 메소드 호출, `new`의 생성자 | HIGH~LOW |
| `CREATES` | METHOD·FIELD → TYPE | RESOLVE | `new Foo()` | HIGH/MEDIUM |
| `USES_TYPE` | METHOD·FIELD·TYPE → TYPE | RESOLVE | 타입 사용 | HIGH~LOW |
| `EXTENDS` / `IMPLEMENTS` | TYPE → TYPE / EXTERNAL_TYPE | RESOLVE | 상속·구현, 익명 클래스 | HIGH~LOW |
| `THROWS` | METHOD → TYPE | RESOLVE | throws 절 | HIGH~LOW |
| `ACCESSES_FIELD` | METHOD → FIELD | RESOLVE | `a.b` | HIGH/MEDIUM |
| `OVERRIDES` | METHOD → METHOD | LINK | 상속 사슬 + 같은 이름·인자 수 | HIGH/MEDIUM |
| `CALLS_POSSIBLE_IMPLEMENTATION` | METHOD → METHOD | LINK | CALLS(추상) + OVERRIDES | MEDIUM |
| `INJECTS` | TYPE → TYPE | SEMANTIC/Spring | 주입 애노테이션 필드 + USES_TYPE | USES_TYPE 따름 |
| `EXECUTES_SQL` | METHOD → SQL(`S…`) | SEMANTIC/MyBatis | 매퍼 인터페이스 / 계산한 SQL 이름 | HIGH / UNRESOLVED |
| `READS_TABLE` / `WRITES_TABLE` | SQL → 테이블 이름 | SEMANTIC/MyBatis | SQL 파싱 / 정규식 | HIGH / MEDIUM |
| `RENDERS` | METHOD → FILE(`F…`) | SEMANTIC/Jsp·Struts | 뷰 이름 리터럴, forward | HIGH |
| `REQUESTS` | FILE → METHOD | SEMANTIC/Jsp·RichClient | 주소 글자 = 진입점, 거래 ID 규칙 | MEDIUM (HIGH/LOW) |
| `INCLUDES` | FILE → FILE | SEMANTIC/Jsp·RichClient | include, 화면 열기 규칙 | HIGH/MEDIUM |

### 6.7 엣지를 따라가 보는 예

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

이 사슬이 있으면 "TB_ORDER를 바꾸면 어느 화면이 영향받나"(영향도 분석)를 거꾸로 따라갈 수 있습니다. 호출 그래프 조회는 깊이 5 이하, `UNION`으로 중복을 걸러 순환이 있어도 끝나도록 만들어져 있습니다.

### 6.8 다시 돌려도 결과가 같은 이유 (멱등성)

| 만든 단계 | 지우는 범위 |
|---|---|
| RESOLVE | 파일 단위: `DELETE … WHERE file_id = ?` 후 다시 넣음 |
| LINK | 리비전 단위: `OVERRIDES`, `CALLS_POSSIBLE_IMPLEMENTATION` 전부 |
| SEMANTIC | 리비전 단위: `clear()`(INJECTS, 화면 엣지, 진입점 등) + MyBatis는 시작할 때 `clearSqlRelations` |

`rerunFrom=RESOLVE`를 주면 RESOLVE·LINK·SEMANTIC·DOCUMENT 상태가 지워져 이 순서로 다시 만들어집니다.

---

## 7. 분석이 끝난 뒤

1. 마지막 단계가 끝나면 Job이 `DONE` 또는 `DONE_WITH_WARNING`(파일 단위 오류가 하나라도 있을 때), 리비전이 `READY`가 됩니다.
2. `RetentionService.applyQuietly()`가 보관 개수(`retention.max-revisions`)를 넘는 옛 리비전을 지웁니다. 실패해도 분석 결과에는 영향이 없습니다.
3. 화면의 폴링이 끝난 상태를 보고 멈추고 리비전 표를 다시 읽습니다.
4. 리비전 표의 [요약]은 `GET /api/revisions/{id}`와 `/rag`를 불러 카드와 "한눈에 보기"를 그립니다. 호출 해석률 95% 이상이면 "믿고 써도 된다", 80% 미만이면 "jar 위치를 적고 다시 분석하라"고 안내합니다.
5. 임베딩은 `EmbeddingWorker`가 뒤에서 계속합니다. 끝나기 전에도 이름 검색, 호출 관계, 영향도는 바로 쓸 수 있습니다.

---

## 8. 코드 위치 모음

| 구분 | 파일 |
|---|---|
| 화면 | `dstone-boot/src/main/webapp/WEB-INF/views/knowledge/index.jsp` |
| 화면 스크립트 | `dstone-boot/src/main/webapp/knowledge/assets/js/project.js`, `knowledge-common.js` |
| 프록시 | `dstone-boot/src/main/java/net/dstone/boot/knowledge/controller/KnowledgeProxyController.java`, `service/KnowledgeProxyService.java` |
| 시작 API | `dstone-knowledge/.../api/controller/ProjectController.java`, `api/service/AnalysisService.java` |
| 상태·취소 API | `api/controller/AnalysisController.java` |
| Job | `job/AnalysisJobRunner.java`, `job/FilePassRunner.java`, `job/FileWatchdog.java`, `common/config/ConfigJob.java` |
| 단계 | `scanner/ScanPass.java`, `incremental/CarryPass.java`, `parser/DeclarePass.java`, `parser/DeclarationCollector.java`, `resource/ResourcePass.java`, `incremental/CarryResolvePass.java`, `resolver/ResolvePass.java`, `resolver/ReferenceResolver.java`, `resolver/LinkPass.java`, `semantic/SemanticPass.java`, `rag/DocumentPass.java` |
| 의미 플러그인 | `semantic/SpringPlugin.java`, `MyBatisPlugin.java`, `ConstantEvaluator.java`, `JspPlugin.java`, `RichClientPlugin.java`, `StrutsPlugin.java` … |
| 엣지 SQL | `dstone-knowledge/src/main/resources/sqlmap/analysis/RelationDao.xml`(RESOLVE 저장, LINK, 지표), `SemanticDao.xml`(INJECTS, RENDERS, clear), `ResourceDao.xml`(EXECUTES_SQL, 테이블) |
| 스키마 | `dstone-knowledge/src/main/resources/schema/02-create-table-postgresql-dstone-knowledge.sql` (`analysis_reference`, `analysis_relation`, `kg_node`, `kg_edge`) |
| 규칙 파일 | `dstone-knowledge/conf/rules/*.yml`, `conf/rules/README.md` |
| 설계 메모 | `.claude/docs/knowledge-core.md`, `docs/11.dstone-knowledge.md` |
