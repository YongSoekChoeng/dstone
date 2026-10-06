# pasted_content_id5c1

> 생성일시: 2026-10-04 18:51

## 질문

<pasted_content id="5c16">
아래의 문서를 확인하고 계획을 세워봐.
신규 모듈(dstone-knowledge)로 계획하고 있어.
/app/dstone/docs/temp/Java_Application_Knowledge_Graph_RAG_Analysis_Platform_설계서_v1.0.docx
</pasted_content id="5c16">

---

## 답변

설계서 내용부터 확인하겠습니다.

---

## 답변

설계서는 확인했습니다. 이제 기존 저장소와 맞물리는 지점(모듈 구성, 기존 분석기, RAG/pgvector 구성)을 확인하겠습니다.

---

## 답변

설계서(32개 절)와 현재 저장소를 대조해서 `dstone-knowledge` 계획을 세웠습니다. 아직 파일은 만들지 않았습니다.

## 1. 설계서와 저장소가 다른 부분

| 설계서 | 저장소 현실 | 계획 |
|---|---|---|
| Spring Boot 3.5.x 이상 | 루트 pom이 Spring Boot 4.1.1 / Java 21 | 4.1.1을 그대로 따름 |
| JavaParser + JavaSymbolSolver | `dstone-boot`가 이미 3.28.1 두 아티팩트를 사용 | 같은 버전 사용, 버전 속성은 루트 pom으로 올림 |
| PostgreSQL + pgvector, BGE-M3 | `dstone-ai-engine`가 로컬 PostgreSQL(`dstone_ai`) + Ollama `bge-m3`(1024차원) 사용 중 | 같은 PostgreSQL에 `dstone_knowledge` DB를 따로 만듦 |
| `repository` 패키지 | 전 모듈이 HikariCP + MyBatis + log4jdbc | MyBatis DAO + `sqlmap/` 관례를 따름 |
| Query → … → LLM → Answer | LLM 호출은 `dstone-ai-engine`의 Agent/Tool 구조가 담당 | knowledge는 LLM 채팅을 하지 않음(아래 2-①) |
| `kg_node/kg_edge`(§11)와 `analysis_*`(§15)가 중복 | — | 원천은 하나로 정함(아래 2-②) |
| KG 저장이 Phase 7 | 저장소 없이는 Phase 1도 검증 불가 | 저장을 첫 단계로 당김 |

## 2. 핵심 설계 결정

1. **모듈 경계**: `dstone-knowledge`는 결정론적 분석, 그래프, 임베딩, 검색 API까지만 맡습니다. 질의 분류와 답변 생성은 `dstone-ai-engine`에 `@AiTool`(호출자/피호출자, 영향도, 코드검색)과 Agent YAML을 추가해 REST로 호출합니다. 설계서 §28 마지막 항목, §29의 "LLM이 호출관계를 추측하지 않는다"와 맞습니다.
2. **저장 모델**: `analysis_*` 타입 테이블을 유일한 원천으로 둡니다. `kg_node/kg_edge`는 그 위의 DB VIEW로 만들고, 탐색은 `analysis_relation`에 재귀 CTE를 씁니다. Neo4j 전환은 나중에 이 VIEW를 내보내면 됩니다.
3. **안정 ID**: `symbolId = hash(projectId + kind + FQN + signature)`로 revision과 무관하게 만들고, 행의 키는 `(revision_id, symbol_id)`로 둡니다. 이렇게 해야 두 revision 비교가 조인 한 번으로 됩니다.
4. **클래스패스**: 대상 프로젝트에서 `mvn dependency:build-classpath`로 jar 목록을 얻어 타입 해석기에 넣습니다. 실패하면 소스와 JDK만으로 해석하고 confidence를 낮춥니다. Gradle은 MVP 이후로 미룹니다.
5. **임베딩**: Spring AI의 Ollama 임베딩과 PgVectorStore를 테이블 이름만 `rag_embedding`으로 바꿔 씁니다. `spring-ai.version`은 루트 pom으로 올려 `dstone-ai-engine`와 공유합니다.
6. **실행 형태**: 한 jar에서 REST 서버와 CLI(`analyze`, `rag-build`)를 모두 지원합니다. 분석은 비동기 Job으로 돌리고 `analysisId`로 상태를 조회합니다.
7. **기존 `dstone-boot` 분석기**: 건드리지 않습니다. 모델이 달라 재사용하지 않고, MyBatis 분석 단계에서 `MybatisParseQuery`의 JSQLParser 사용법만 참고합니다.

## 3. 단계별 계획

설계서의 12개 Phase를 검증 가능한 단위로 다시 묶었습니다. 검증 대상은 이 저장소 자체로, 작은 `dstone-batchadmin`에서 시작해 `dstone-boot`로 넓힙니다.

| 단계 | 내용 (설계서 Phase) | 완료 확인 |
|---|---|---|
| **M0 골격** | pom과 루트 모듈 등록, `conf/`, `bin/*.sh`, `dstone_knowledge` DB와 스키마 SQL, 설계서 §16 패키지 구조 | 기동 및 헬스 체크 |
| **M1 스캔·심볼** (1, 3, 7) | 프로젝트/리비전/Job, 파일 스캔과 checksum, AST, Type/Method/Field/Annotation, 상속·구현·오버라이드 관계, root package 필터, `analysis_error` | batchadmin의 타입·메소드 수가 실제와 일치, 파싱 실패 파일이 조회됨 |
| **M2 호출 그래프** (2, 4) | 심볼 해석, `CALLS`와 confidence, 인터페이스 구현 후보 관계, 객체 생성·필드 접근·타입 사용, 호출자/피호출자/구현체 API, 품질 지표 계산 | 내부 호출 해석률 90% 이상, 표본 메소드 수작업 대조 |
| **M3 Spring 의미** (5) | Bean, 주입 관계(`@Qualifier`/`@Primary`), 엔드포인트, `@Transactional`, 계층 분류, DI 정보로 구현 후보 좁히기 | 엔드포인트 목록이 실제 컨트롤러와 일치 |
| **M4 RAG** (8, 9) | FILE/TYPE/METHOD 문서, 의미 경계 청킹, 메타데이터, 임베딩, 벡터 검색 API | 검색 결과에 코드, 관계 context, 파일·라인이 함께 나옴 → **MVP(§27) 완료** |
| **M5 리소스** (6) | MyBatis mapper와 Java 메소드 연결, yml/properties 키와 사용 코드, pom 의존성, MAPPER/CONFIG/ENDPOINT 청크 | 엔드포인트에서 SQL까지 경로 조회 |
| **M6 Graph-RAG·영향도** (10) | 하이브리드 검색, 영향도 분석(§20), `dstone-ai-engine` Tool과 Agent | Agent가 "이 메소드를 고치면 영향받는 API"에 근거와 함께 답함 |
| **M7 증분·리비전** (11) | checksum 기반 재분석, 리비전 비교, 변경 청크만 재임베딩 | 증분 결과가 전체 재분석 결과와 동일 |
| **M8 품질 화면** (12) | 지표와 미해결 심볼 보고서 화면 | — |

문서는 `docs/11.dstone-knowledge.md` 신설과 `CLAUDE.md`, `01.index.md`, `04.cloud-architecture.md`, `software/05.postgresql.md` 갱신을 M0과 각 단계 끝에 같이 합니다.

## 4. 주요 위험

- **Lombok**: 생성되는 getter/setter는 소스에 없어서 심볼 해석이 실패합니다. 필드와 애노테이션으로 가상 메소드를 추론해 MEDIUM confidence로 연결하는 보완이 M2에 필요합니다. 이게 없으면 90% 목표를 넘기 어렵습니다.
- **성능과 메모리**: 심볼 해석은 대형 프로젝트에서 느립니다. 파일 단위 예외 격리, 해석 캐시, 시간 제한을 두고, 병렬화는 스레드 안전성을 확인한 뒤에 넣습니다.
- **리비전 데이터량**: MVP는 리비전마다 전체 스냅샷을 저장하고 보관 개수를 제한합니다. 차분 저장은 M7에서 다시 판단합니다.
- **임베딩 시간**: 로컬 Ollama로 메소드 수만 건을 임베딩하면 오래 걸립니다. 배치 처리와 재개가 필요합니다.

## 5. 정해주셔야 할 것

1. **배포 형태**: VM 방식(`bin/startApp.sh`, batch 계열과 동일)을 권장합니다. 로컬 소스 경로와 git, 대상 프로젝트의 `mvn` 실행이 필요해서 Pod로 가면 볼륨과 빌드 도구를 이미지에 넣어야 합니다.
2. **포트**: 4081을 제안합니다(기존 5081/6081/7081/8081과 겹치지 않음).
3. **임베딩 소유**: 위 2-⑤처럼 knowledge가 직접 갖는 안을 권장합니다. 대안은 `dstone-ai-engine`의 `/api/ai/embed`에 위임하는 것인데, 그래프 테이블과 조인하는 하이브리드 검색이 어려워집니다.

이 세 가지가 정해지면 M0부터 시작하겠습니다. 원하시면 이 계획을 `docs/temp/`에 문서로 남기겠습니다.

---

## 질문

M1 에서 batchadmin 은 무슨 관계인가?

---

## 답변

기능상 관계는 없습니다. `dstone-batchadmin`은 M1에서 만든 분석기가 제대로 동작하는지 확인하려고 **분석 대상으로 돌려볼 샘플 프로젝트**로 고른 것뿐입니다.

- **의존 관계 없음**: `dstone-knowledge`는 batchadmin을 참조하지 않고, batchadmin 코드도 고치지 않습니다. 소스 디렉터리를 읽기만 합니다.
- **고른 이유**: 이 저장소 안에서 규모가 작고 전형적인 Spring 구조(Controller → Service → DAO, MyBatis)라서, 분석 결과의 타입·메소드 수를 실제 소스와 직접 세어 대조하기 쉽습니다.
- **쓰는 방식**: `analyze --source /app/dstone/dstone-batchadmin --root-package net.dstone.batchadmin`처럼 실행해서 결과가 맞는지 봅니다. 패키지명은 아직 확인하지 않은 추정입니다.

검증용 표본은 어떤 Java 프로젝트여도 되므로, 다른 대상을 원하시면 그걸로 바꾸면 됩니다.

---

## 질문

추가적으로 이 dstone-knowledge 은 Spring 뿐만 아니라 구버젼의 일반 java 프로그램도 분석가능해야 해.

---

## 답변

반영하겠습니다. 설계서 §1도 "일반 Java/Spring"을 대상으로 적고 있지만, 제 계획은 Spring 애노테이션과 Maven을 전제로 짠 부분이 있어서 다음을 바꿉니다. "구버전"은 Java 1.4~7 문법에 애노테이션 이전 방식(XML 설정, Servlet, 직접 JDBC)으로 만든 프로그램까지로 가정했습니다.

## 바뀌는 설계 원칙

**코어는 프레임워크를 모르게 하고, Spring은 여러 의미 분석 플러그인 중 하나로 내립니다.** 스캔·AST·심볼·호출 그래프(M1~M2)는 순수 Java만으로 완결되고, 의미 분석기가 하나도 맞지 않는 프로젝트도 그래프와 RAG까지 정상 생성되어야 합니다.

## 단계별 추가 사항

| 단계 | 추가되는 것 | 이유 |
|---|---|---|
| **M1** | 프로젝트별 Java 문법 수준 지정(`--java-version`), 실패 시 다른 수준으로 재시도 | 1.4 이전 코드는 `enum`, `assert`를 변수명으로 쓰기도 해서 최신 문법으로는 파싱이 깨짐 |
| **M1** | 파일별 인코딩 자동 감지(EUC-KR/MS949) | 구형 국내 프로젝트는 UTF-8이 아닌 경우가 많음. `dstone-common`에 이미 있는 juniversalchardet를 사용 |
| **M1** | 소스 루트 자동 탐지(`package` 선언과 경로 대조) | `src/main/java`가 아닌 `src/`, `WEB-INF/src` 같은 구조 |
| **M2** | 빌드 파일 없이 클래스패스 구성: `--lib-dir`, `WEB-INF/lib` 자동 탐지 | Ant이거나 빌드 파일이 아예 없는 프로젝트 |
| **M3** | "Spring 의미"를 "의미 분석 플러그인"으로 변경. MVP에는 Spring 애노테이션과 일반 Java(`main()`, `HttpServlet` 상속, `web.xml` 서블릿 매핑, `Thread`/`Runnable`) 두 개 | 진입점이 HTTP 엔드포인트만이 아님 |
| **M3** | 계층 분류를 이름·패키지·상속 규칙으로도 수행(`*Action`, `*DAO`, `*Impl` 등, 설정 가능, confidence 낮춤) | 애노테이션이 없으면 역할을 알 단서가 이름뿐임 |
| **M5** | Spring XML Bean 설정(`applicationContext.xml`의 `<bean>`, `ref`), iBATIS sqlMap, Struts `struts-config.xml` | 구버전 Spring은 주입 관계가 XML에만 있음 |
| **M6** | 영향도 분석의 종착점을 "엔드포인트"에서 "진입점"(HTTP, `main`, 서블릿, 스케줄)으로 일반화 | 배치·데몬형 프로그램 대응 |

모델도 하나 바뀝니다. `analysis_endpoint`를 진입점 전체를 담는 테이블로 넓히고 종류 컬럼을 둡니다.

## 새로 생기는 위험

- **JDK에서 사라진 API**: 분석기는 Java 21에서 돌기 때문에 `javax.xml.bind`, CORBA처럼 11에서 제거된 클래스는 JDK에서 해석되지 않습니다. 대상 프로젝트의 jar에 있으면 해석되고, 없으면 외부 미해결로 기록합니다. 내부 호출 해석률 계산에는 영향이 없습니다.
- **jar가 없는 프로젝트**: 라이브러리를 구할 수 없으면 외부 타입을 거치는 호출의 confidence가 전반적으로 내려갑니다. 품질 보고서에 "클래스패스 불완전"을 원인으로 표시합니다.
- **Java 소스 안의 SQL 문자열**: 직접 JDBC 코드는 SQL이 문자열 조합으로 흩어져 있어 정확한 복원이 어렵습니다. 문자열 상수로 확정되는 것만 잡고 나머지는 한계로 기록합니다.
- **검증 표본 부재**: 이 저장소는 전부 Spring Boot 4라서 구버전 검증에 쓸 수 없습니다.

## 확인이 필요한 것

1. **검증 표본**: 실제 분석하려는 구버전 프로젝트가 있으면 그걸 기준으로 삼겠습니다. 없으면 테스트 리소스에 작은 표본(Java 1.4 문법, 서블릿 + JDBC, EUC-KR)을 직접 만듭니다.
2. **우선할 구형 스택**: Struts, iBATIS, EJB, Spring XML 중 실제 대상에 있는 것을 알려주시면 M5 순서를 거기에 맞춥니다. 기본은 Spring XML → iBATIS → Struts 순이고 EJB는 뒤로 미룹니다.
3. **JSP**: 설계서 범위에 없어서 제외했습니다. 구형 웹 프로그램은 JSP 스크립틀릿에서 Java를 직접 호출하는 경우가 많아, 필요하면 M5에 JSP → Java 호출 관계를 추가하겠습니다.

---

## 질문

<pasted_content id="5c16">
확인이 필요한 것.
  1. 검증 표본: 실제 분석하려는 구버전 프로젝트가 있으면 그걸 기준으로 삼겠습니다. 없으면 테스트 리소스에 작은 표본(Java 1.4 문법, 서블릿 + JDBC, EUC-KR)을 직접 만듭니다.
    => OK
  2. 우선할 구형 스택: Struts, iBATIS, EJB, Spring XML 중 실제 대상에 있는 것을 알려주시면 M5 순서를
     거기에 맞춥니다. 기본은 Spring XML → iBATIS → Struts 순이고 EJB는 뒤로 미룹니다.
    => OK
  3. JSP: 설계서 범위에 없어서 제외했습니다. 구형 웹 프로그램은 JSP 스크립틀릿에서 Java를 직접 호출하는 경우가 많아, 필요하면 M5에 JSP → Java 호출 관계를 추가하겠습니다.
    => 추가해줘.


그리고 앞서 문답에서
4. 주요 위험 에서 
    - Lombok => 초기 설계부터 바로잡고 갈 수는 없나?
    - 성능과 메모리 => 선험적으로 볼 때 심볼해석을 메모리에서 해결하려고 하면 큰 프로젝트에서는 반드시 Out Of Memory Error 발생해. 따라서 모든 단계과정이 DB에 기록하면서 진행되어야 할 거야.(모든 유관후보객체를 메모리에 올리기보다는)
    - 리비전 데이터량 => 정확한 의미를 모르겠어.
    - 임베딩 시간 => 동의해. 이것도 초기 설계부터 잡고 가야 할 듯 해.
5. 정해주셔야 할 것
    1. 배포 형태: VM 방식(bin/startApp.sh, batch 계열과 동일)을 권장합니다. 로컬 소스 경로와 git, 대상 프로젝트의 mvn 실행이 필요해서 Pod로 가면 볼륨과 빌드 도구를 이미지에 넣어야 합니다.
    ==>> VM 방식
    2. 포트: 4081을 제안합니다(기존 5081/6081/7081/8081과 겹치지 않음).
    =>> OK
    3. 임베딩 소유: 위 2-⑤처럼 knowledge가 직접 갖는 안을 권장합니다. 대안은 dstone-ai-engine의 /api/ai/embed에 위임하는 것인데, 그래프 테이블과 조인하는 하이브리드 검색이 어려워집니다.
    =>> stone-ai-engine 에서 embed 기능 없애고 이 기능으로 통합을 목적으로 함. 또한 dstone-boot 에서의 analysis 기능도 이 기능으로 통합할 예정임. 이 모듈이 성공적으로 완성되면 상기 두 기능은 제거대상임.
</pasted_content id="5c16">

---

## 답변

답변 주신 내용을 반영하면 계획의 뼈대가 세 군데 바뀝니다. 파이프라인이 DB 중심으로 바뀌고, Lombok과 임베딩 처리가 초기 설계에 들어가며, 모듈 범위가 "코드 분석기"에서 "통합 지식 모듈"로 넓어집니다.

## 1. 메모리: 모든 단계를 DB에 쓰면서 진행

전체 AST나 후보 객체를 메모리에 쌓지 않고, 파일 하나를 처리하면 바로 DB에 쓰고 버리는 여러 번의 패스로 나눕니다.

| 패스 | 처리 | 메모리에 남는 것 |
|---|---|---|
| ① 선언 | 파일 1개 파싱 → 타입·메소드·필드 행 저장, 호출·참조 지점은 미해결 상태로 `analysis_reference`에 저장 → AST 폐기 | 현재 파일 하나 |
| ② 해석 | 파일 1개 재파싱 → 타입 조회는 DB 심볼 색인에 질의 → 관계 행 저장, 참조 상태 갱신 | 현재 파일 + 크기 제한 캐시 |
| ③ 집합 연산 | 오버라이드, 인터페이스 구현 후보, 계층 분류를 SQL(`INSERT … SELECT`)로 계산 | 없음 |
| ④ 문서·임베딩 | 심볼 단위로 읽어 청크 생성, 임베딩은 별도 대기열 | 배치 1개 |

- **심볼 해석기**: JavaSymbolSolver의 기본 소스 해석기는 파싱한 파일을 계속 캐시해서 OOM의 직접 원인이 됩니다. 이를 DB 색인을 조회하는 자체 타입 해석기와 크기 제한 캐시로 교체합니다. 이 계획에서 구현 난도가 가장 높은 부분이라 M1 초반에 먼저 시험 구현해 가능 여부를 확인하겠습니다.
- **재개 가능**: 파일별·패스별 진행 상태를 `analysis_file`에 기록합니다. 중간에 죽어도 이어서 진행하고, 이 구조가 그대로 증분 분석(M7)의 기반이 됩니다.
- **목표**: 필요한 힙 크기가 프로젝트 크기와 무관하게 일정해야 합니다.

## 2. Lombok: 선언 패스에서 해결

패스 ①에서 `@Getter/@Setter/@Data/@Value/@Builder/@*ArgsConstructor/@Slf4j`를 보고 생성될 멤버를 `synthetic=true`인 정식 심볼로 DB에 넣습니다. 위치는 해당 필드나 애노테이션의 라인으로 기록합니다.

해석기가 DB 색인을 보기 때문에 Lombok 호출도 일반 메소드와 같은 경로로 해석되고, 별도 보정 단계나 confidence 강등이 필요 없습니다. 이 "생성 멤버 제공자"는 플러그인 지점으로 두어 다른 코드 생성 도구에도 쓸 수 있게 합니다.

delombok으로 소스를 먼저 변환하는 방법은 라인 번호가 바뀌어 원본 추적이 깨지므로 택하지 않았습니다.

## 3. 임베딩: 내용 해시 기반 대기열

- `rag_chunk`에 내용 해시를 두고, `rag_embedding`의 키를 `(내용 해시, 모델명)`으로 잡습니다. 같은 내용은 리비전이 달라도, 재실행해도 다시 임베딩하지 않습니다.
- 임베딩은 분석 Job과 분리된 대기열 단계입니다(상태: 대기/완료/실패, 배치 단위, 재개 가능). 그래프 조회는 임베딩이 끝나기 전에도 쓸 수 있고, 진행률은 API로 조회합니다.
- 모델명과 차원을 행에 기록해, 모델을 바꾸면 해당 건만 다시 대기열에 들어갑니다.
- 이 때문에 앞서 말한 "Spring AI PgVectorStore를 테이블 이름만 바꿔 사용"은 취소합니다. Spring AI는 임베딩 모델 호출에만 쓰고, 벡터 테이블과 검색 SQL은 MyBatis로 직접 관리합니다.

## 4. "리비전 데이터량"의 의미

리비전(커밋) 하나를 분석할 때마다 그 시점의 심볼·관계·청크 전체를 한 벌씩 저장한다는 뜻입니다. 예를 들어 관계가 50만 건인 프로젝트를 커밋 100개에 대해 분석하면 5천만 건이 쌓이는데, 인접한 리비전끼리는 대부분 같은 내용입니다.

선택지는 둘입니다.
- **전체 스냅샷 + 보관 정책**: 리비전마다 한 벌씩 저장하고 최근 N개와 지정한 리비전만 남깁니다. 조회 SQL이 단순합니다.
- **차분 저장**: 행에 유효 리비전 범위를 두어 바뀐 것만 저장합니다. 용량은 작지만 모든 조회가 복잡해지고 브랜치 처리가 까다롭습니다.

**전체 스냅샷 + 보관 정책**을 권장합니다. 용량이 큰 임베딩은 3번의 해시 키로 이미 리비전 간에 공유되므로, 중복되는 것은 크기가 작은 구조 행뿐입니다. 이견이 없으면 이대로 갑니다.

## 5. 통합에 따른 범위 확대

두 기능을 흡수하려면 `dstone-knowledge`가 기존 기능을 빠짐없이 대체해야 합니다.

**`dstone-ai-engine` 임베딩/RAG 흡수**
- 코드뿐 아니라 일반 문서(PDF/Word 업로드)도 받아야 합니다. `rag_document`에 원천 종류(코드/문서)를 두고, Tika 문서 리더를 이쪽으로 옮깁니다.
- 호출자별 문서 격리(tenant 필터)를 그대로 지원해야 합니다. API 키 인증 필터가 `dstone-knowledge`에도 필요합니다.
- `dstone-ai-engine`의 `RagSearchTool`과 Agent의 `ragEnabled`는 knowledge REST를 호출하는 방식으로 바뀌고, `EmbedService`, `EmbedController`, `RagController`, `RagRetrievalChain`은 제거 대상이 됩니다.

**`dstone-boot` 분석기 흡수**
- 기존 분석기는 화면 → 기능 → 테이블 CRUD 매핑을 만듭니다(VO 이름 기준: `TbUi`, `TbFunc`, `TbFuncTblMapping`, `TbTbl` 등). 그래서 설계서 §28에서 "향후 확장"이던 **SQL 테이블 CRUD 관계**를 M5로 당깁니다.
- 기존 코드에 `Toss*` 계열 파서 구현체가 따로 있습니다. 사이트별 전용 파서로 보이는데 아직 내용을 읽지 않았습니다. M5 시작 전에 기존 분석기를 정독해 기능 대조표를 만들겠습니다.
- 리포트 화면을 어디에 둘지는 M8에서 정하면 되고, 지금 단계를 막지 않습니다.

## 6. 수정된 단계

| 단계 | 내용 |
|---|---|
| **M0** | 모듈 골격(VM 방식, 포트 4081), `dstone_knowledge` DB, 스키마(패스 상태, `analysis_reference`, 해시 키 임베딩 포함), 구버전 검증 표본 제작 |
| **M1** | 패스 ① 선언: 스캔, 인코딩 감지, 문법 수준, 소스 루트 탐지, Lombok 생성 멤버, DB 기반 타입 해석기 시험 구현 |
| **M2** | 패스 ②③ 해석: 호출 그래프, confidence, 구현 후보, 품질 지표, 호출자/피호출자 API |
| **M3** | 의미 분석 플러그인: Spring 애노테이션, 일반 Java 진입점, 이름 규칙 계층 분류 |
| **M4** | 코드 RAG: 문서·청크, 임베딩 대기열, 벡터 검색 → **MVP** |
| **M5** | 리소스: MyBatis, 설정, 빌드 파일, Spring XML → iBATIS → Struts, **JSP → Java 호출**, **SQL 테이블 CRUD** |
| **M6** | 일반 문서 수집(Tika, tenant), 하이브리드 검색, 영향도 분석, `dstone-ai-engine` Tool 연동 |
| **M7** | 증분 분석, 리비전 비교, 보관 정책 |
| **M8** | 품질·리포트 화면 |
| **M9** | 이관: 기능 대조표 확인 → `dstone-ai-engine` 임베딩과 `dstone-boot` 분석기 제거, 문서 정리 |

M9의 실제 코드 제거는 대조 결과를 보여드리고 확인받은 뒤에 진행합니다.

진행하라고 하시면 M0부터 시작하겠습니다. 계획이 여러 답변에 흩어져 있으니, 원하시면 하나로 합쳐 `docs/temp/`에 문서로 남기겠습니다.

---

## 질문

응 진행해.

---

## 답변

M0(모듈 골격)부터 시작합니다. 먼저 기존 모듈의 관례(pom, 기동 클래스, conf, bin 스크립트)를 확인하겠습니다.

---

## 질문

Base directory for this skill: /app/dstone/.claude/skills/java-no-lambdas

# Java는 람다 없이 쓰기

이 저장소의 Java 코드는 람다 식과 메서드 참조를 쓰지 않는다. 모듈을 가리지 않는 상시 규칙이다.

## 왜

사용자가 2026-09-20에 dstone-ai-engine의 람다를 모두 예전 스타일로 바꿔 달라고 했고, 앞으로 만드는 소스에도 계속 적용하라고 했다. 한 번 하고 끝나는 정리가 아니다.

## 이렇게 쓴다

- 함수형 인터페이스(`Comparator`, `Runnable`, `RowMapper`, `CompletableFuture` 콜백 등) → **익명 내부 클래스**
- Stream 체인 → 가능하면 **일반 for 문**

```java
// 쓰지 않는다
list.sort((a, b) -> a.getName().compareTo(b.getName()));
names.forEach(System.out::println);

// 이렇게 쓴다
list.sort(new Comparator<Item>() {
    @Override
    public int compare(Item a, Item b) {
        return a.getName().compareTo(b.getName());
    }
});
for (String name : names) {
    System.out.println(name);
}
```

## 기존 코드를 고칠 때

- 손대는 코드에 람다가 있으면, 따로 요청이 없어도 익명 클래스/반복문으로 바꾼다.
- 깊게 중첩된 Stream 파이프라인처럼 바꾸면 지나치게 장황해지는 경우에는 **말없이 람다를 남겨 두지 말고 사용자에게 물어본다.**

## 끝내기 전 확인

바꾼 Java 파일에 `->` 와 `::` 가 남아 있지 않은지 검색해 본다.

---

## 질문

Base directory for this skill: /app/dstone/.claude/skills/friendly-comments

# 쉽고 친근한 주석 쓰기

주석은 한 번 읽고 바로 이해되어야 한다. 다시 읽어야 뜻이 잡히는 문장은 고쳐 쓴다.

## 오해하지 말 것

**주석을 줄이라는 뜻이 아니다.** 이 코드베이스는 원래 "왜 이렇게 했는지", 결정 배경, 이력을 길게 풀어 쓰는 한글 주석을 쓴다 (dstone-ai-engine의 `ConfigTool`, `CallerContext` 등). 그 관례는 그대로 이어간다. 바꾸는 것은 **말투와 문장**이다.

## 이렇게 쓴다

- 짧고 평범한 문장으로 끊어 쓴다.
- 딱딱한 한자어나 논문 같은 표현 대신, 구체적이고 직관적인 말을 고른다.
- "왜"는 그대로 남긴다. 표현만 쉽게 바꾼다.

```java
// 피한다
// 조건부 빈 등록에 기인한 의존성 부재 가능성을 고려하여 지연 조회 방식을 채택함.

// 이렇게 쓴다
// RAG를 꺼 두면 이 빈이 아예 없다.
// 그래서 꼭 필요할 때만 꺼내 쓴다.
```

## 왜

사용자가 2026-09-14에 dstone-ai-engine 영향 분석 내용을 검토한 뒤 직접 요청했다.

---

## 질문

Base directory for this skill: /app/dstone/.claude/skills/cloud-architecture-wsl

# WSL 위의 클라우드 아키텍처 시뮬레이션

WSL 개발 환경을 클라우드 배포 모양 그대로 만들어 직접 다뤄 보는 것이 목적이다. **전체 설계와 현재 상태는 `docs/04.cloud-architecture.md`가 기준**이다. 작업 전에 먼저 읽는다. 이 문서는 거기서 놓치기 쉬운 점만 추린 것이다.

## 구조

| 대상 | 역할 | 실행 방식 |
|---|---|---|
| `dstone-boot`, `dstone-ai-engine` | 컨테이너 워크로드 | `kind` 클러스터의 Pod (namespace `dstone`, 로컬 레지스트리 `localhost:5000`) |
| `dstone-batch`, `dstone-batchadmin` | VM형 워크로드 | WSL 호스트에서 `bin/*.sh` 스크립트로 제어 (포트 6081/5081) |
| MySQL / Redis / RabbitMQ / Kafka | CSP 관리형 서비스 역할 | 클러스터 밖, WSL 호스트 |
| Jenkins | VM/온프레미스 역할 | WSL 호스트 |

- systemd는 쓰지 않는다 (`no-systemd-app-scripts` skill).
- 별도 배포 디렉터리(`/workshop`)는 없다. 모든 앱 홈은 `/app/dstone` 아래다.
- 문서는 저장소 루트 `docs/` 한 곳에만 둔다. 모듈 안이나 `dstone/docs/` 같은 하위 폴더를 제안하지 않는다.

## 헷갈리기 쉬운 점

### `APP_HOME` 기준이 모듈마다 다르다 — 둘 다 맞다
- `dstone-boot`: 저장소 루트 `/app/dstone`
- `dstone-batch`/`dstone-batchadmin`의 `vm` 프로파일: 모듈 디렉터리 `/app/dstone/<module>`

한쪽에 맞춰 "고치지" 않는다.

### `env-k8s.properties`는 빌드할 때 WAR 안에 들어간다
`application.yml`/`log4j2.xml`처럼 ConfigMap으로 마운트되는 것이 아니다. 고친 뒤 Pod만 재시작하면 아무 변화가 없다. **이미지 빌드 → push → 재배포**까지 해야 반영된다.

### 이미지 태그는 빌드마다 새로 붙인다
`imagePullPolicy: IfNotPresent`에서 같은 태그(`:latest` 등)를 다시 쓰면 kind 노드가 예전 캐시 이미지를 계속 쓴다. 빌드마다 고유 태그를 붙이고 `kubectl set image`로 바꾼다 (Jenkinsfile이 `${BUILD_NUMBER}`로 하는 방식).

### `dstone-boot`는 실행 방식이 두 가지다
- `bin/startApp.sh` + `wsl` 프로파일: WSL에서 바로 실행, 전부 `localhost`, Docker 불필요
- `k8s` 프로파일: Pod로 실행, kind 게이트웨이 IP 사용

### kind NodePort
클러스터에 `extraPortMappings`가 없어서 `kubectl port-forward`가 필요하다.

## 인프라 접속 문제: 먼저 의심할 것

kind Pod가 docker 브리지 너머의 호스트 인프라에 닿아야 하는 지점에서 같은 종류의 문제가 반복된다. **"지금 존재하지 않는 주소"에 묶여 있지 않은지**부터 본다.

- **MySQL / Redis**: `0.0.0.0`으로 바인드한다. kind 게이트웨이 IP(`172.18.0.1`)를 직접 적으면 Docker/kind가 먼저 떠 있어야만 기동되는 순서 의존이 생긴다.
- **Kafka**: 리스너를 둘로 나눈다. `advertised.listeners`는 바인드 범위가 아니라 클라이언트에게 알려 주는 주소라서 `0.0.0.0` 같은 해법이 없다.
  - `9092` (PLAINTEXT, `127.0.0.1` 광고): 로컬 PC, WSL 네이티브 클라이언트, Kafbat UI, `wsl` 프로파일
  - `9094` (DOCKER, `172.18.0.1` 광고): kind Pod 전용 (`env-k8s.properties`의 `KAFKA_PORT`)
  - 증상: 처음 연결은 되는데 produce/fetch가 끝없이 멈춘다 → `server.properties`의 `advertised.listeners`를 먼저 확인한다.

## 문서

인프라나 배포 구조를 바꾸면 `docs/04.cloud-architecture.md`, `docs/02.environment.md`, 해당 `docs/software/*.md`를 함께 고친다. 예전 설정을 바꾼 이유는 지우지 말고 날짜를 붙인 "이전 이력"으로 남긴다. 남은 한계는 `docs/04.cloud-architecture.md`의 "알려진 한계" 절에 있다.

---

## 답변

모듈 골격(pom, conf, bin)은 만들었고, 이제 기동 클래스와 DB 설정 클래스, 스키마 SQL을 작성합니다.

---

## 답변

스키마까지 작성했습니다. 이제 빌드하고, 제 계정으로 띄운 임시 PostgreSQL에서 스키마와 기동을 검증합니다.

---

## 답변

기동과 스크립트 모두 확인했습니다. 이제 구버전 검증 표본(Java 1.4 문법, 서블릿 + JDBC, EUC-KR)을 만듭니다.

---

## 답변

표본까지 끝났습니다. 마지막으로 문서(신규 `11.dstone-knowledge.md`와 기존 문서·`CLAUDE.md`)를 갱신합니다.

---

## 답변

M0(모듈 골격)를 마쳤습니다. 앱은 빌드·기동되고 스키마도 검증했지만, 로컬 PostgreSQL에 `dstone_knowledge` DB를 만드는 일은 `sudo`가 필요해서 하지 못했습니다. 커밋도 아직 하지 않았습니다.

## 만든 것

- **모듈**: `dstone-knowledge` (포트 4081, VM 방식 `bin/*.sh`, `Jenkinsfile`). 루트 pom에 등록했습니다.
- **스키마**: 테이블 20개와 그래프 VIEW 2개. 패스별 진행 상태, 미해결 참조, 해시 키 임베딩 대기열처럼 앞서 합의한 내용이 처음부터 들어 있습니다.
- **상태 확인 API**: `GET /api/system/health`가 정상 / 스키마 미준비 / DB 연결 실패를 구분해 응답합니다.
- **구버전 검증 표본**: `src/test/resources/samples/legacy-app`. EUC-KR, Java 1.4 문법(`enum`을 변수명으로 사용), 서블릿 + `web.xml`, 직접 JDBC, `main()`, JSP 스크립틀릿을 담았고, 기대 값(타입 8개, 메소드 19개 등)은 그 폴더 README에 적었습니다.
- **문서**: `docs/11.dstone-knowledge.md` 신설(설계 원칙, 스키마, 단계별 상태), 그리고 `CLAUDE.md`, `01.index.md`, `README.md`, `03.build.md`, `04.cloud-architecture.md`, `software/05.postgresql.md` 갱신.

## 확인한 것

- 스키마 SQL 두 파일을 임시 PostgreSQL(제 계정으로 띄운 별도 클러스터, pgvector 0.8.1)에서 실행했고, 두 번 돌려도 오류가 없었습니다. 임시 클러스터는 정리했습니다.
- 그 임시 DB에 붙여 기동하면 상태 확인이 `UP`(테이블 20개)을 돌려줍니다.
- `bin/startApp.sh`, `statusApp.sh`, `stopApp.sh`가 동작하고, 실제 설정으로 띄우면 DB가 없어서 `DB_DOWN`(503)이 나옵니다.

## 기존 코드에 손댄 부분

- JavaParser 버전(3.28.1)을 루트 pom 속성으로 올리고 `dstone-boot/pom.xml`이 그 속성을 쓰게 바꿨습니다. 해석되는 버전은 그대로입니다.
- 빌드 과정에서 `dstone-common`을 로컬 Maven 저장소에 다시 설치했습니다.

## 직접 해주셔야 할 것

DB 생성은 별도 터미널에서 해주세요. 먼저 `01-init-postgresql-dstone-knowledge.sql` 안의 `CHANGE_ME`를 실제 비밀번호로 바꿉니다.

```bash
cd /app/dstone/dstone-knowledge/src/main/resources/schema
sudo -u postgres psql -f 01-init-postgresql-dstone-knowledge.sql
psql -h 127.0.0.1 -U dstone_knowledge -d dstone_knowledge -f 02-create-table-postgresql-dstone-knowledge.sql
```

`application.yml`의 비밀번호는 `dstone-batchadmin`·`dstone-ai-engine`에 들어 있는 `ENC(...)` 값을 그대로 넣어 두었습니다. 같은 개발용 비밀번호로 롤을 만들면 설정을 고칠 필요가 없고, 다른 비밀번호를 쓰려면 `EncUtil`로 새 값을 만들어 바꿔야 합니다.

## 다음 단계

M1(파일 스캔 + 선언 패스)은 DB 생성을 기다리지 않고 바로 이어갈 수 있습니다. 순서는 이렇게 잡았습니다.

1. DB 색인 기반 타입 해석기 시험 구현. 이 계획에서 가장 불확실한 부분이라 먼저 가능 여부를 확인합니다.
2. 파일 스캔, 인코딩 감지, 문법 수준 재시도, 소스 루트 탐지.
3. 타입·메소드·필드·애노테이션 저장과 Lombok 생성 멤버.
4. `legacy-app`과 `dstone-batchadmin`으로 기대 값 대조.

지금까지의 변경을 커밋할지, M1까지 마친 뒤 함께 커밋할지 알려주세요.

---

## 질문

지금까지의 변경을 커밋해줘

---

## 답변

커밋했습니다. `main`이 아니라 새 브랜치 `feature/dstone-knowledge`에 올렸고, 푸시는 하지 않았습니다.

- **커밋**: `a117268` "dstone-knowledge: 신규 모듈 골격 추가 (M0)", 파일 45개.
- **브랜치**: 여러 단계에 걸친 신규 모듈이라 `main`에서 분리했습니다. `main`에 바로 두길 원하시면 `git checkout main && git merge feature/dstone-knowledge`로 합치면 됩니다. 말씀해 주시면 제가 하겠습니다.
- **설계서 포함**: `docs/temp/`의 원 설계서(docx)는 문서에서 참조하고 있어 함께 커밋했습니다.
- **제외한 파일**: 설계서 옆의 `…docx:Zone.Identifier`는 Windows가 붙인 부가 파일이라 커밋하지 않고 그대로 두었습니다. 필요 없으면 지워도 됩니다.

이 브랜치에서 M1을 이어가려면 말씀해 주세요.

---

## 질문

git 에 제대로 올라간것 맞아 ?

---

## 답변

아니요, GitHub에는 아직 올라가지 않았습니다. 커밋은 로컬 브랜치 `feature/dstone-knowledge`에만 있고, 원격(`origin`)에는 `main`만 있으며 그 `main`에는 M0 커밋이 들어 있지 않습니다.

현재 상태는 이렇습니다.

| 위치 | 최신 커밋 | M0 커밋(`a117268`) |
|---|---|---|
| GitHub `origin/main` | `6696a38` (history 18:35) | 없음 |
| 로컬 `main` | `6696a38` | 없음 |
| 로컬 `feature/dstone-knowledge` | `ce01b5b` | 있음 |

제 커밋 뒤로 이 브랜치에 커밋이 두 개 더 쌓여 있습니다. 제가 만든 것이 아니고, history 자동 커밋으로 보입니다.
- `3d5905c`: history 18:46 기록
- `ce01b5b` ("."): 제가 일부러 빼 두었던 `…docx:Zone.Identifier` 파일이 여기서 커밋됐습니다.

history 자동 커밋이 지금은 `feature/dstone-knowledge`에 쌓이고 있습니다. 평소처럼 `main`으로 푸시되던 흐름이 제가 브랜치를 바꾸면서 끊긴 것으로 보입니다. 이 저장소 방식에는 `main`에서 바로 작업하는 편이 맞았습니다.

GitHub에 올리려면 `main`에 합쳐서 푸시하면 됩니다. `main`이 이 브랜치의 조상이라 충돌 없이 그대로 앞으로 당겨집니다.

```bash
git checkout main
git merge --ff-only feature/dstone-knowledge
git push origin main
```

푸시는 외부로 나가는 작업이라 확인을 받고 진행하려 합니다. 진행하라고 하시면 위 순서대로 실행하고, 이후 작업은 `main`에서 이어가겠습니다.

---

## 질문

모두 git 에 올려줘

---

## 답변

GitHub에 모두 올라갔습니다. 원격 `origin/main`과 로컬 `main`이 같은 커밋(`29a8e78`)을 가리키고, 올리지 않은 변경은 남아 있지 않습니다.

- **포함된 내용**: M0 커밋(`a117268`)과 그 뒤에 쌓인 history 자동 커밋들이 `6696a38..29a8e78` 구간으로 한 번에 푸시됐습니다.
- **현재 브랜치**: `main`으로 돌아왔습니다. 이후 작업은 `main`에서 이어갑니다.
- **남은 브랜치**: 로컬에 `feature/dstone-knowledge`가 남아 있습니다. 내용이 모두 `main`에 들어갔으므로 필요 없으면 `git branch -d feature/dstone-knowledge`로 지우면 됩니다.

---

## 질문

로컬에서 git pull 할때 아래와 같은 에러 발생해. 

<pasted_content id="5c16">
git.exe pull --progress -v --no-rebase -- "origin"
POST git-upload-pack (165 bytes)
From https://github.com/YongSoekChoeng/dstone
 = [up to date]      main       -> origin/main
error: invalid path 'docs/temp/Java_Application_Knowledge_Graph_RAG_Analysis_Platform_설계서_v1.0.docx:Zone.Identifier'
Updating 6696a38..e4f1f00


git did not exit cleanly (exit code 1) (688 ms @ 2026-10-04 오후 6:50:25)
</pasted_content id="5c16">

---

## 답변

원인은 `…docx:Zone.Identifier` 파일입니다. 파일명에 `:`이 들어 있어 Windows에서는 만들 수 없는 경로인데, 자동 커밋(".")에 딸려 올라갔습니다. 저장소에서 빼고 다시 올라가지 않도록 막겠습니다.

---

## 답변

고쳐서 GitHub에 올렸습니다(`481892b`). Windows에서 다시 `git pull` 하시면 됩니다.

- **원인**: `docs/temp/…설계서_v1.0.docx:Zone.Identifier` 파일이 저장소에 들어가 있었습니다. Windows는 파일명에 `:`을 쓸 수 없어서 `invalid path`로 체크아웃이 막혔습니다.
- **조치**: 그 파일을 저장소에서 제거했고, 루트 `.gitignore`에 `*:Zone.Identifier`를 추가해 자동 커밋에 다시 딸려 올라가지 않게 했습니다. WSL 디스크의 파일 자체는 지우지 않았습니다.

Windows 쪽 pull은 제가 직접 확인하지 못했습니다. 최신 커밋에는 문제 경로가 더 이상 없으므로 통과할 것으로 보지만, 같은 오류가 또 나오면 메시지를 그대로 보내주세요.
