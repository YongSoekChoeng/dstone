# pasted_content_id5c1

> 생성일시: 2026-10-04 17:51

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
