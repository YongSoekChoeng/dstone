# 어제_진행하던_dstone_knowl

> 생성일시: 2026-10-05 08:52

## 질문

어제 진행하던 dstone_knowledge 전체 진행계획 다시 공유해주고 현재 어디까지 진행된건지 알려줘. 그리고 이 모든 것은 /app/dstone/docs/11.dstone-knowledge.md 에 기록해줘.

---

## 답변

먼저 기존 문서와 모듈의 실제 상태를 확인하겠습니다.

---

## 답변

`docs/11.dstone-knowledge.md`의 10절을 전체 계획(10.1), 현재 상태(10.2), 남은 일(10.3)로 나눠 다시 썼습니다. 커밋은 하지 않았습니다.

## 현재 위치

**M0까지 끝났고, M1은 아직 시작하지 않았습니다.** 어제(2026-10-04) 커밋 `a117268`로 모듈 골격이 들어갔고, 분석 코드(`scanner`, `parser`, `symbol` 등)는 아직 하나도 없습니다.

## 전체 계획

| 단계 | 내용 | 상태 |
|---|---|---|
| **M0** | 모듈 골격, 스키마, 검증 표본 | 완료 (2026-10-04) |
| **M1** | SCAN + DECLARE 패스: 인코딩 감지, 문법 수준, 소스 루트 탐지, 타입/메소드/필드/애노테이션, Lombok 생성 멤버, DB 기반 타입 해석기 시험 구현 | 다음 작업 |
| **M2** | RESOLVE 패스 + 집합 연산: 호출 그래프, confidence, 구현 후보, 품질 지표, 호출자/피호출자 API | 예정 |
| **M3** | 의미 분석 플러그인: Spring 애노테이션, 일반 Java 진입점(`main`, 서블릿, `web.xml`, `Runnable`), 계층 분류 | 예정 |
| **M4** | 코드 RAG: FILE/TYPE/METHOD 문서와 청크, 임베딩 대기열, 벡터 검색 (여기까지가 MVP) | 예정 |
| **M5** | 리소스: MyBatis, 설정, 빌드 파일, Spring XML → iBATIS → Struts, JSP → Java 호출, SQL 테이블 CRUD | 예정 |
| **M6** | 일반 문서 수집(Tika, tenant), 하이브리드 검색, 영향도 분석, `dstone-ai-engine` Tool 연동 | 예정 |
| **M7** | 증분 분석, 리비전 비교, 보관 정책 | 예정 |
| **M8** | 품질·리포트 화면 | 예정 |
| **M9** | 이관: 기능 대조 후 `dstone-ai-engine` 임베딩과 `dstone-boot` 분석기 제거 | 예정 |

## M0에서 끝낸 것

- **모듈**: `pom.xml`, 루트 pom 등록, `DstoneKnowledgeApplication`, 설정 클래스 4종(`Config`, `ConfigDatasource`, `ConfigMapper`, `ConfigTransaction`), `BaseDao`
- **스키마**: SQL 두 파일로 테이블 20개와 VIEW 2개 (두 번 실행해도 오류 없음)
- **API**: `GET /api/system/health` (`UP` / `SCHEMA_NOT_READY` / `DB_DOWN`)
- **운영**: `bin/startApp.sh`, `stopApp.sh`, `statusApp.sh`, `Jenkinsfile`
- **검증 표본**: `legacy-app` (EUC-KR, Java 1.4 문법)과 기대 값 `README.md`

## M1 전에 남은 일

로컬 PostgreSQL에 `dstone_knowledge` 롤/DB/테이블을 만들어야 합니다. `sudo`가 필요해서 어제는 임시 클러스터에서만 검증했습니다. 순서는 `docs/software/05.postgresql.md` 6.1절에 있습니다.

지금 실제로 만들어져 있는지는 postgres 비밀번호가 없어 확인하지 못했고, 문서에는 "아직 안 함"으로 적어 두었습니다. 이미 만드셨다면 알려 주세요. 문서를 고치겠습니다. 앱은 현재 중지 상태입니다.

## 문서에 추가한 내용

- **10.1**: 위 계획 표와, 단계별로 어떤 패스가 어떤 테이블을 채우는지 대응표
- **10.2**: M0 산출물 목록과 확인한 항목
- **10.3**: M1 선행 작업과 M1 작업 7개

10.3의 M1 작업 7개와 완료 기준은 기존 M1 설명과 `legacy-app` 표본의 특성을 바탕으로 제가 풀어 쓴 것이니, 어제 합의한 범위와 다르면 말씀해 주세요.

---

## 질문

로컬 postgresql 에 dstone_knowledge 생성했어. 그리고 이 프로젝트 진행하는동안 필요한 것 같아서 알려줄께 su의 패스워드는 db2admin 이야. 그리고 데이터베이스 dstone_knowledge 의 패스워드는 db2admin!@ 야.

---

## 답변

롤 비밀번호를 맞췄고 접속도 확인했습니다. 이제 앱을 띄워서 `application.yml`의 `ENC(...)` 값이 이 비밀번호와 맞는지 `/api/system/health`로 확인합니다.

---

## 답변

로컬 DB는 확인됐고, 앱이 저장소 설정 그대로 붙어서 `/api/system/health`가 `UP`(테이블 20개, pgvector 0.8.1)으로 나옵니다. M1을 막는 선행 작업은 이제 없습니다.

## 확인 중에 고친 것

- **롤 비밀번호가 `CHANGE_ME`로 남아 있었습니다.** 알려 주신 `db2admin!@`로는 접속이 안 됐습니다. 롤이 처음에 `CHANGE_ME`로 만들어진 뒤라, 비밀번호를 고친 SQL을 다시 돌려도 "이미 있으면 건너뜀"에 걸린 것으로 보입니다.
- **`ALTER ROLE`로 알려 주신 비밀번호에 맞췄습니다.** sudo로 postgres에 들어가 변경했고, 이후 `psql` 접속과 앱 기동 모두 정상입니다. 확인 후 앱은 원래대로 중지해 두었습니다.

## 확인이 필요한 것

`schema/01-init-postgresql-dstone-knowledge.sql` 12행에 DB 비밀번호가 평문으로 들어간 채 커밋(`1fa785d`)되어 있습니다. 이 파일은 손대지 않았습니다. 로컬 `main`이 `origin/main`과 같은 상태라 GitHub에도 올라가 있을 가능성이 큽니다.

`CHANGE_ME`로 되돌리고 필요하면 비밀번호도 바꾸는 쪽을 권합니다. 다만 이미 푸시된 이력은 되돌려도 남습니다. 되돌릴지 알려 주세요.

## 문서와 메모리

- **`docs/11.dstone-knowledge.md`**: 10.2에 로컬 DB 준비 완료와 확인 결과를 적고, 10.3의 "M1 전에 할 일"을 없음으로 바꿨습니다.
- **`docs/software/05.postgresql.md` 6.1절**: 롤이 이미 있으면 비밀번호가 안 바뀐다는 점과 `ALTER ROLE`로 고치는 방법을 추가했습니다.
- **메모리**: 두 비밀번호는 다음 세션에서도 쓰도록 저장소 밖의 메모리 파일에 저장했습니다. 저장소 파일에는 적지 않았습니다.

문서 변경 두 건은 아직 커밋하지 않았습니다.

---

## 질문

비밀번호 관련해서는 적어도 프로젝트 끝날때까지는 노출되어도 되니까 그냥 두고 필요한 만큼 커밋해줘

---

## 답변

문서 변경 두 건을 `main`에 커밋했습니다(`cc8ba06`). 푸시는 하지 않아서 `origin/main`보다 커밋 1개 앞서 있습니다.

- **커밋 내용**: `docs/11.dstone-knowledge.md`(전체 계획 / 현재 상태 / 남은 일)와 `docs/software/05.postgresql.md` 6.1절(`ALTER ROLE` 안내).
- **비밀번호**: `01-init-postgresql-dstone-knowledge.sql`의 평문 비밀번호는 말씀대로 그대로 두었습니다. 프로젝트가 끝날 때까지 다시 지적하지 않도록 메모리에도 적어 두었습니다.

이 저장소는 그동안 `main`에 바로 커밋해 왔기에 별도 브랜치는 만들지 않았습니다.

---

## 질문

그럼 다음 진행할 것은 무엇인가?

---

## 답변

다음은 **M1(SCAN + DECLARE 패스)** 입니다. 소스를 읽어 "어떤 타입·메소드·필드가 있는지"를 DB에 채우는 단계이고, 호출 관계를 푸는 일은 M2입니다.

## M1 작업 순서

1. **분석 실행의 뼈대**: 프로젝트 등록 → 리비전 생성 → Job 실행 API와, 파일별·패스별 진행 상태(`analysis_file_pass`)를 기록하는 실행기. 이후 모든 패스가 이 위에 올라갑니다.
2. **SCAN 패스**: 파일 수집, 인코딩 감지, 체크섬을 `analysis_file`에 저장. `legacy-app`의 EUC-KR 파일이 깨지지 않고 읽히는지가 기준입니다.
3. **소스 루트와 문법 수준 탐지**: 빌드 파일이 없는 `WEB-INF/src` 구조와, `enum`을 변수 이름으로 쓰는 Java 1.4 문법을 통과시킵니다.
4. **DECLARE 패스**: 파일 하나를 파싱해 타입/메소드/필드/애노테이션을 저장하고, 호출과 참조는 미해결 상태로 `analysis_reference`에 넣은 뒤 AST를 버립니다.
5. **Lombok 생성 멤버**: getter/setter 등을 `is_synthetic=true`로 저장합니다.
6. **DB 기반 타입 해석기 시험 구현**: M2에서 본격적으로 쓰기 전에, 메모리가 프로젝트 크기와 무관하게 유지되는지 먼저 확인합니다.
7. **검증**: `legacy-app`은 `README.md`의 기대 값과, `dstone-batchadmin`은 손으로 센 값과 대조합니다.

1~2번을 먼저 끝내고 한 번 돌려 본 뒤 3~5번으로 넘어가는 순서를 권합니다. 실행기와 이어 하기 구조가 흔들리면 뒤 패스를 전부 다시 손봐야 하기 때문입니다.

## 시작 전에 정할 것

- **분석 대상 지정 방식**: 로컬 디렉터리 경로만 받을지, git URL과 리비전까지 받아 체크아웃할지. M1은 로컬 경로만 받고 git은 M7(증분 분석)에서 붙이는 쪽을 권합니다.
- **실행 방식**: REST API로 비동기 Job을 띄울지, CLI도 같이 만들지. REST 하나로 시작하는 쪽을 권합니다.

이 두 가지에 이견이 없으면 권한 대로 1번부터 시작하겠습니다.

---

## 질문

<pasted_content id="23eb">
<시작 전에 정할 것>
- 분석 대상 지정 방식: 로컬 디렉터리 경로만 받을지, git URL과 리비전까지 받아 체크아웃할지. M1은 로컬 경로만 받고 git은 M7(증분 분석)에서 붙이는 쪽을 권합니다.
 ==>> ok.
- 실행 방식: REST API로 비동기 Job을 띄울지, CLI도 같이 만들지. REST 하나로 시작하는 쪽을 권합니다.
 ==>> ok.
   
그리고 앞으로 진행할 내용에 대해서 샘플소스로 활용할 수 있는 어플리케이션 소스들을 아래의 디렉토리에 올려놨어. 추후 이 소스들을 이용해서 붆석대상으로 활용해도 될 것 같아.
/app/sampleApps/
</pasted_content id="23eb">

---

## 답변

두 가지 모두 권한 대로 확정하고 M1의 1~2번(실행 뼈대 + SCAN 패스)부터 시작하겠습니다. 먼저 올려 주신 샘플 소스와 현재 모듈 코드를 살펴봅니다.

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

## 답변

공통 예외 처리까지 만들었고, 이제 DAO와 MyBatis 매퍼(프로젝트·리비전·Job·파일)를 작성합니다.

---

## 답변

DAO까지 끝났습니다. 이제 SCAN 패스의 핵심인 인코딩 감지, 파일 분류, 스캔 본체를 작성합니다.

---

## 답변

스캔 본체까지 작성했습니다. 마지막으로 서비스와 REST 컨트롤러를 만들고 빌드합니다.

---

## 답변

빌드가 통과했습니다. 로컬 DB에 새 테이블을 반영하고, 앱을 띄워 `legacy-app`부터 실제로 돌려 봅니다.

---

## 답변

세 표본 모두 정상적으로 스캔됐습니다. 이제 이어 하기(재실행 시 건너뛰기, 취소, 강제 종료 후 복구)를 시험합니다.

---

## 답변

강제 종료 복구까지 모두 통과했습니다. 스캐너의 순수 로직에 단위 테스트를 붙이고 문서를 갱신하겠습니다.

---

## 답변

테스트 4건이 통과했습니다. 이제 문서(`11.dstone-knowledge.md`, `CLAUDE.md`)를 현재 상태에 맞게 고칩니다.

---

## 답변

M1의 1~2번(Job 실행 뼈대와 SCAN 패스)을 끝냈습니다. 세 표본 모두 오류 없이 스캔됐고, 빌드와 단위 테스트 4건이 통과합니다. 커밋은 아직 하지 않았습니다.

## SCAN 결과

| 대상 | 파일 | Java | JSP | MyBatis 매퍼 | 인코딩 | Java 소스 루트 |
|---|---|---|---|---|---|---|
| `legacy-app` | 9 | 7 | 1 | 0 | Java 7개 모두 EUC-KR | `WEB-INF/src` |
| `anybiz_prd` | 1,142 | 489 | 538 | 92 | UTF-8 1,051 / US-ASCII 89 / EUC-KR 2 | `WEB-INF/classes` |
| `cms4_prd` | 495 | 232 | 200 | 42 | UTF-8 446 / US-ASCII 49 | `WEB-INF/classes` |

- **대조**: `legacy-app`은 README 기대 값(Java 7)과 같고, 올려 주신 두 앱은 `find`로 센 Java·JSP 수와 같습니다.
- **샘플 앱 구조**: 두 앱은 펼친 WAR라서 소스가 `WEB-INF/classes`에 있습니다. 소스 루트를 폴더 이름이 아니라 `package` 선언으로 거꾸로 찾게 해서 그대로 잡혔습니다.

## 만든 것

- **Job 실행기**: 등록된 단계를 순서대로 돌리고, 그 리비전에서 이미 끝난 단계는 건너뜁니다. 같은 리비전 라벨로 다시 시작하면 이어서 합니다.
- **SCAN 패스**: 파일 수집, 종류 분류, 파일별 인코딩 감지, SHA-256, 패키지·소스 루트·모듈.
- **API**: 프로젝트 등록·조회, 분석 시작·상태·취소, 리비전 조회·파일 목록·삭제.

## 이어 하기 시험

`/app` 전체(파일 51,144개, 약 1.1GB)를 시험 대상으로 썼습니다.

- **규모**: 기본 힙으로 43초에 끝났고 오류가 없었습니다.
- **취소 후 재시작**: 1,000개에서 멈춘 뒤 다시 시작해 51,144개로 끝났습니다.
- **분석 도중 `kill -9`**: 재기동 때 Job이 `FAILED`로 정리됐고, 다시 시작해 중복 없이 51,144개가 됐습니다.

`/app`에는 `HDMF` 등 다른 소스도 들어 있어 함께 스캔됐습니다. 시험이 끝난 뒤 해당 리비전과 프로젝트는 지웠습니다. 로컬 DB에는 위 세 표본의 `r1` 리비전만 남아 있습니다.

## 계획과 달라진 점

- **테이블 1개 추가**: SCAN처럼 파일 단위가 아닌 단계가 끝났는지 기록할 곳이 없어 `analysis_revision_pass`를 만들었습니다. 테이블이 21개가 됐고 로컬 DB에도 반영했습니다.
- **소스 루트 탐지를 SCAN에 넣음**: 파싱 없이도 되는 일이라 앞당겼습니다. 문법 수준 탐지는 파서가 필요해 DECLARE와 함께 합니다.
- **파일별 이어 하기는 다음 단계로**: `analysis_file_pass`를 쓰는 반복 구조는 실제로 쓰는 DECLARE와 같이 만들어야 시험할 수 있어 미뤘습니다.
- **분석 스레드의 SQL 로그 끔**: 저장 SQL이 수만 건이라 로그를 덮어서, 기존 `SUPPRESS_SQL_LOG` 장치로 껐습니다.
- **surefire 3.5.2 지정**: 이 저장소에는 테스트가 없었고 Maven 기본값(2.17)은 JUnit 5를 못 돌려서, `dstone-knowledge` pom에만 버전을 적었습니다.

문서는 `docs/11.dstone-knowledge.md`(Job 실행 방식, SCAN, 설정, API, 표본, 현재 상태)와 `CLAUDE.md`를 고쳤습니다. 앱은 중지해 두었습니다.

## 다음

DECLARE 패스입니다. 문법 수준 탐지(Java 1.4의 `enum` 변수 이름), 타입·메소드·필드·애노테이션 저장, 미해결 참조 저장, 파일별 이어 하기 순으로 진행합니다. 지금까지의 변경을 먼저 커밋할지 알려 주시면 그렇게 하고 넘어가겠습니다.

---

## 질문

지금 네가 하는 테스트를 나도 로컬에서 진행 가능해 ?

---

## 답변

네, 가능합니다. WSL(`/app/dstone`)에서는 지금 바로 되고, 준비할 것은 없습니다. jar는 이미 빌드돼 있고 DB에도 스키마가 반영돼 있습니다.

## 1. 단위 테스트 (DB 없이)

```bash
cd /app/dstone && mvn -pl dstone-knowledge test
```

`Tests run: 4, Failures: 0`이 나오면 정상입니다.

## 2. 실제 분석 돌려 보기

```bash
# 기동 (지금은 중지 상태)
sh /app/dstone/dstone-knowledge/bin/startApp.sh
curl http://localhost:4081/api/system/health        # "status":"UP" 확인

B=http://localhost:4081/api

# 프로젝트 등록 (세 표본은 이미 등록돼 있어 생략 가능)
curl -X POST $B/projects -H 'Content-Type: application/json' \
     -d '{"projectId":"anybiz_prd","localPath":"/app/sampleApps/anybiz_prd"}'

# 분석 시작: 응답의 analysisId, revisionId를 아래에서 씁니다
curl -X POST $B/projects/anybiz_prd/analyses -H 'Content-Type: application/json' \
     -d '{"revisionLabel":"r2"}'

# 상태와 결과
curl $B/analyses/<analysisId>
curl $B/revisions/<revisionId>
curl "$B/revisions/<revisionId>/files?language=JAVA&size=20"
```

- **라벨**: `r1`은 제가 이미 끝까지 돌려서, 같은 라벨로 다시 시작하면 SCAN을 건너뛰고 바로 `DONE`이 됩니다. 처음부터 보려면 `r2`처럼 새 라벨을 쓰시면 됩니다.
- **삭제**: `curl -X DELETE $B/revisions/<revisionId>`로 리비전을 지울 수 있습니다.
- **한글 출력**: 응답 뒤에 `| python3 -m json.tool --no-ensure-ascii`를 붙이면 보기 좋게 나옵니다.

## 3. 이어 하기 시험

두 표본은 1초 안에 끝나서 취소나 강제 종료를 끼워 넣기 어렵습니다. 저는 `/app` 전체(약 43초)를 대상으로 했습니다.

```bash
curl -X POST $B/projects -H 'Content-Type: application/json' -d '{"projectId":"big","localPath":"/app"}'
curl -X POST $B/projects/big/analyses -H 'Content-Type: application/json' -d '{"revisionLabel":"r1"}'

# (가) 취소 후 이어 하기
curl -X POST $B/analyses/<analysisId>/cancel
# (나) 또는 강제 종료 후 재기동: Job이 FAILED로 정리됩니다
kill -9 $(pgrep -f dstone-knowledge.jar) && sh /app/dstone/dstone-knowledge/bin/startApp.sh

# 같은 라벨로 다시 시작하면 이어서 끝까지 갑니다
curl -X POST $B/projects/big/analyses -H 'Content-Type: application/json' -d '{"revisionLabel":"r1"}'
```

## DB에서 직접 보기

```bash
PGPASSWORD='db2admin!@' psql -h 127.0.0.1 -U dstone_knowledge -d dstone_knowledge
```

```sql
SELECT analysis_id, status, current_pass, done_files FROM analysis_job ORDER BY created_at;
SELECT language, file_type, encoding, count(*) FROM analysis_file WHERE revision_id = 2 GROUP BY 1,2,3 ORDER BY 1,2;
```

## Windows PC에서 하려면

Windows 쪽 클론(`D:/AppHome/framework/dstone`)에서는 아직 안 됩니다. 이번 변경을 커밋·푸시하지 않아서 그쪽에는 코드가 없습니다. 푸시한 뒤에도 아래 두 가지가 필요합니다.

- **DB**: 기본 프로파일의 `env.properties`가 가리키는 PostgreSQL에 `dstone_knowledge`가 있어야 합니다. 그쪽 설정이 WSL의 PostgreSQL을 보는지는 확인하지 않았습니다.
- **소스 경로**: `localPath`는 서버가 직접 읽는 경로라서, Windows에서 띄우면 Windows 경로로 등록해야 합니다.

Windows에서도 돌려 보실 계획이면 커밋·푸시부터 하겠습니다.

---

## 질문

응 커밋, 푸시 해줘.

---

## 답변

커밋하고 `origin/main`에 푸시했습니다(`e7cc0d5`). 로컬과 원격이 같은 상태입니다.

- **포함된 것**: `dstone-knowledge`의 Job 실행기·SCAN 패스·API·스키마·테스트 37개 파일과 `docs/11.dstone-knowledge.md`, `CLAUDE.md`.
- **앞선 문서 커밋**: 아까 만든 `cc8ba06`도 이미 원격에 올라가 있습니다.

Windows 쪽 클론에서 `git pull` 하시면 받을 수 있습니다. 거기서 돌리실 때는 스키마 SQL(`02-create-table-…sql`)을 한 번 다시 실행해야 새 테이블(`analysis_revision_pass`)이 생깁니다. WSL의 DB는 이미 반영돼 있습니다.

다음은 DECLARE 패스입니다.

---

## 질문

음... dstone 내에서는 lombok을 사용하지 말아줘. 지금 ScannedFile 클래스에서 lombok을 사용하는 것 같은데...

---

## 답변

`ScannedFile`에서 Lombok을 걷어내고 getter/setter를 직접 작성했습니다. 빌드와 테스트 4건이 통과합니다. 아직 커밋하지 않았습니다.

- **`ScannedFile`**: `@Getter`/`@Setter`와 `import lombok`을 지우고 필드 13개의 getter/setter를 넣었습니다.
- **`dstone-knowledge/pom.xml`**: 더 쓰는 곳이 없어 Lombok 의존성도 뺐습니다.
- **앞으로**: 이 저장소에서는 Lombok을 쓰지 않도록 메모리에 저장했습니다.

`dstone-boot`의 `sample/kakao`, `sample/google`, `sample/naver` 아래 9개 파일은 예전부터 Lombok을 쓰고 있습니다. 이번에는 손대지 않았습니다. 이것도 걷어낼지 알려 주세요. 걷어내면 `dstone-boot`, `dstone-batchadmin`, `dstone-ai-engine`의 pom에 남은 Lombok 의존성도 함께 정리할 수 있습니다.

참고로 분석 대상 프로젝트의 Lombok을 해석하는 기능(생성 멤버를 `is_synthetic`으로 저장)은 이와 별개라서 계획대로 진행합니다.

이번 변경을 커밋·푸시할까요?

---

## 질문

응

---

## 답변

커밋하고 푸시했습니다(`bfa85d1`). 로컬과 `origin/main`이 같은 상태입니다.

`dstone-boot`의 sample 9개 파일에 남은 Lombok은 "응"이 커밋·푸시에 대한 답이라고 보고 건드리지 않았습니다. 그것도 걷어내길 원하시면 말씀해 주세요.

다음은 DECLARE 패스입니다.

---

## 질문

아냐 분석대상으로서의 lombok은 당연히 있어야해.

---

## 답변

네, 그대로입니다. 분석 대상의 Lombok을 해석하는 기능은 건드리지 않았고 계획대로 갑니다.

- **이번에 뺀 것**: `dstone-knowledge` 자기 코드가 쓰던 Lombok(`ScannedFile`의 `@Getter`/`@Setter`와 pom 의존성)뿐입니다.
- **그대로인 것**: 분석 대상 소스에 붙은 `@Getter`, `@Data` 같은 애노테이션을 읽어 생성될 멤버를 `is_synthetic=true`로 저장하는 일입니다. 다음 DECLARE 패스에서 만듭니다. 이 기능은 소스의 애노테이션을 읽기만 하므로 Lombok 라이브러리 의존성이 없어도 됩니다.
- **`dstone-boot`의 sample 9개 파일**: 손대지 않았고, 따로 요청하시기 전까지 그대로 둡니다.

DECLARE 패스로 넘어가겠습니다.

---

## 질문

응 진행해

---

## 답변

DECLARE 패스를 시작합니다. 먼저 JavaParser가 지원하는 문법 수준과, 표본에서 구버전 문법·Lombok이 실제로 어떻게 쓰이는지 확인합니다.

---

## 답변

확인 결과를 반영해 구현합니다. 먼저 파일 단위 진행을 맡는 공통 실행기와 DB 매퍼, 저장용 행 클래스를 만듭니다.

---

## 답변

공통 실행기까지 만들었습니다. 이제 핵심인 Java 파서(문법 수준 탐지)와 선언 수집기를 작성합니다.

---

## 답변

빌드가 통과했습니다. 단위 테스트로 `legacy-app`의 기대 값과 Lombok 처리부터 확인합니다.

---

## 답변

다섯 표본 모두 최종 코드로 다시 돌려 오류 없이 끝났습니다. 문서를 현재 상태에 맞게 갱신합니다.

---

## 답변

DECLARE 패스를 끝냈습니다. 다섯 표본 모두 파싱 실패 없이 돌았고, 빌드와 단위 테스트 10건이 통과합니다. 아직 커밋하지 않았습니다.

## 결과

| 대상 | Java 파일 | 타입 (이름 있는 것 / 익명) | 메소드 | 만들어 넣은 메소드 | 필드 | 참조 | 시간 |
|---|---|---|---|---|---|---|---|
| `legacy-app` | 7 | 8 / 1 | 20 | 5 | 8 | 134 | 0.2초 |
| `anybiz_prd` | 489 | 497 / 23 | 14,732 | 472 | 5,456 | 107,306 | 10.7초 |
| `cms4_prd` | 232 | 235 / 9 | 5,767 | 218 | 2,076 | 38,957 | 3.6초 |
| `dstone-batchadmin` | 44 | 44 / 4 | 303 | 39 | 122 | 2,303 | 0.5초 |
| `dstone-boot` | 183 | 208 / 14 | 1,425 | 262 | 730 | 19,128 | 2.9초 |

- **`legacy-app`**: README 기대 값과 일치합니다. `enum`을 변수 이름으로 쓴 `OrderServiceImpl.java`만 문법 수준 `1.4`로 읽혔고 나머지는 `21`입니다.
- **`.class` 파일 수와 대조**: 컴파일하면 타입 하나에 `.class` 하나가 생기는데, 세 프로젝트 모두 정확히 맞았습니다. `anybiz_prd` 520, `dstone-batchadmin` 48, `dstone-boot` 222입니다.
- **Lombok**: `dstone-boot`의 `KakaoLoginVo`(`@AllArgsConstructor @Builder @Getter`)에 생성자, `builder()`, getter 2개, 중첩 타입 `KakaoLoginVoBuilder`가 생겼습니다. 올려 주신 두 앱은 Lombok을 쓰지 않아 `dstone-boot`로 확인했습니다.

## 규모와 이어 하기 시험

`/app` 전체(Java 31,952개)를 대상으로 했고, 시험 후 리비전과 프로젝트는 지웠습니다.

- **규모**: `-Xmx1024m`로 약 9분에 끝났고 메모리 오류가 없었습니다. 메소드 107만, 참조 473만 행이 들어갔습니다.
- **파싱 실패 32개(0.1%)**: 직접 열어 본 8개는 모두 원본이 깨진 소스였습니다(역컴파일 잔해 `}this.acc))`, `new ArrayList<int>()`, 한 파일에 같은 내용이 여러 번 붙은 것). 나머지는 오류 메시지가 같은 유형이라 같은 원인으로 봤습니다.
- **취소 후 재시작**: 861개에서 멈춘 뒤 남은 파일만 이어서 처리했습니다.
- **`kill -9` 후 재기동**: 4,047개 완료 상태에서 죽였고, 재시작해 31,952개로 끝났습니다.

## 시험 중에 찾아 고친 것

- **이어진 호출의 위치**: `a.get().get()`은 식의 시작 위치가 같아 구분이 안 됐습니다. 호출 "이름"의 위치를 기록하도록 바꿨습니다.
- **다른 파일과의 타입 중복**: `TPLP0106M.java`와 백업본 `TPLP0106M_1.java`가 같은 클래스를 선언해 DB 중복 오류가 났습니다. 파일 이름이 클래스 이름과 같은 쪽을 남기고, 다른 쪽은 `DUPLICATE_TYPE` 오류로 기록합니다. 처리 순서와 무관합니다.

## 알아 두실 설계 결정

- **메소드 시그니처의 타입은 소스에 적힌 그대로입니다.** `findOrders(String)`이지 `findOrders(java.lang.String)`이 아닙니다. 전체 이름으로 바꾸려면 다른 파일을 봐야 하고, 그러면 ID가 분석 순서에 따라 달라질 수 있어서입니다. 전체 이름은 RESOLVE가 따로 채웁니다.
- **심볼 ID에 소스 루트를 넣었습니다.** 한 프로젝트에 같은 이름의 클래스가 여러 벌 있는 경우가 실제로 있어서입니다. 소스 루트가 바뀌면 ID도 바뀝니다.
- **익명 클래스는 종류 `ANONYMOUS`로 따로 뒀습니다.** 그 안의 호출은 바깥 메소드가 아니라 익명 클래스의 메소드에서 나가는 것으로 기록합니다.
- **Lombok 미지원 항목**: `@SuperBuilder`, `@With`, `@Delegate`, `@Accessors`, 메소드에 붙인 `@Builder`는 아직 다루지 않습니다.

`docs/11.dstone-knowledge.md`(4.3절 DECLARE, 스키마, API, 결과)와 `CLAUDE.md`를 갱신했습니다. 앱은 중지해 두었고, 로컬 DB에는 다섯 표본의 `r1` 리비전이 남아 있습니다. 조회는 `GET /api/revisions/{id}`와 `/api/revisions/{id}/symbols`로 하시면 됩니다.

## 다음

M1에서 남은 것은 DB 색인 기반 타입 해석기 시험 구현 하나입니다. 지금까지의 변경을 커밋·푸시하고 넘어갈지 알려 주세요.

---

## 질문

커밋, 푸시 후에 나머지 진행해줘

---

## 답변

빌드가 통과했습니다. 앱을 띄워 `legacy-app`부터 타입 해석기 시험을 돌려 봅니다.

---

## 답변

M1의 마지막 항목인 DB 색인 기반 타입 해석기 시험 구현을 끝냈습니다. 이로써 **M1이 완료**됐습니다. 빌드와 단위 테스트 11건이 통과하고, 이번 변경은 아직 커밋하지 않았습니다. 앞선 DECLARE 변경은 요청대로 먼저 커밋·푸시했습니다(`c31e498`).

## 시험 결과

메소드 호출을 전부 풀어 보고 통계만 냈습니다(DB에는 저장하지 않습니다).

| 대상 | 파일 | jar | 캐시 크기 | 호출 | 풀린 비율 | 시간 | 힙 (GC 뒤) |
|---|---|---|---|---|---|---|---|
| `anybiz_prd` | 489 | 106 | 50 | 58,386 | 99.9% | 43초 | 92~137MB |
| `cms4_prd` | 232 | 113 | 50 | 19,005 | 99.8% | 10초 | 86~110MB |
| `cms4_prd` | 232 | 113 | 5 | 19,005 | 99.8% | 49초 | 61~70MB |
| `legacy-app` | 7 | 0 | 3 | 49 | 83.7% | 0.5초 | 35MB |
| `dstone-batchadmin` | 44 | 0 | 10 | 929 | 18.9% | 3초 | 38MB |

- **메모리가 묶입니다.** 들고 있는 AST 수가 캐시 크기를 넘지 않았고, 파일 50개마다 잰 힙 사용량이 처리한 파일 수와 무관하게 일정했습니다.
- **캐시 크기는 속도만 바꿉니다.** `cms4_prd`를 캐시 50과 5로 돌린 결과가 완전히 같았습니다.
- **DECLARE 결과와 맞습니다.** 해석기가 "프로젝트 안의 메소드"라고 한 호출 50,448건이 전부 `analysis_method`에 있었습니다.
- **`legacy-app`의 못 푼 8건**은 표본에서 일부러 뺀 `javax.servlet`입니다.

## 구현하면서 알게 된 것

소스 해석기만 바꿔서는 메모리가 묶이지 않습니다. JavaSymbolSolver의 다른 두 곳에도 크기 제한 없는 캐시가 있어서 같이 처리했습니다.

- **`CombinedTypeSolver`**: 찾은 타입을 끝없이 캐시해서, 만들 때 캐시를 껐습니다.
- **`JavaParserFacade`**: 풀어 본 노드를 캐시해서, 파일 하나를 끝낼 때마다 비웁니다.

## M2 전에 정해야 할 것

`dstone-batchadmin`이 18.9%에 그친 것이 핵심입니다. 폴더 안에 jar가 없고 의존성이 `~/.m2`에 있어서, 부모 클래스를 못 찾으면 그 클래스 안의 호출이 줄줄이 실패합니다. 올려 주신 두 앱은 `WEB-INF/lib`가 있어 문제가 없었습니다.

1. **클래스패스를 어떻게 얻을지.** 두 가지 방법이 있습니다.
   - 대상 프로젝트에서 `mvn dependency:build-classpath`를 실행해 얻습니다. 자동이지만 대상이 빌드 가능한 상태여야 하고 Maven/Gradle 프로젝트에만 됩니다.
   - 프로젝트 등록 때 jar 폴더를 직접 지정하게 합니다. 단순하고 구버전 프로젝트에도 통하지만 사람이 넣어 줘야 합니다.
   
   저는 둘 다 지원하되, 기본은 "폴더 안의 jar 자동 탐색 + 직접 지정"으로 시작하고 Maven 실행은 선택 사항으로 두는 쪽을 권합니다.
2. **못 푼 호출을 어떻게 남길지.** 이름과 인자 수로 짐작해 `confidence = LOW`로 남길지, `UNRESOLVED`로만 둘지입니다. 설계 원칙("불확실한 것은 지우지 않고 정도를 남긴다")에 따라 짐작한 후보를 `LOW`로 남기는 쪽을 권합니다.

Lombok 멤버는 JavaSymbolSolver가 모르기 때문에, 못 푼 호출을 만들어 넣은 멤버에서 다시 찾는 단계를 M2에 넣습니다.

## 그 밖에

- **시험용 API**: `POST /api/revisions/{id}/trials/type-solver`를 추가했습니다. M2에서 RESOLVE가 들어오면 없앱니다.
- **문서**: `docs/11.dstone-knowledge.md`(4.4절 타입 해석기, 시험 결과, M2 준비 사항)와 `CLAUDE.md`를 갱신했습니다. M1을 완료로 표시했습니다.
- **상태**: 앱은 중지해 두었습니다.

이번 변경을 커밋·푸시하고, 위 두 가지에 이견이 없으시면 권한 대로 M2를 시작하겠습니다.

---

## 질문

현재 이 분석기가 반드시 빌드 가능한 어플리케이션에 대해서만 분석이 가능한가?

---

## 답변

아닙니다. 빌드가 안 되는 애플리케이션도 분석됩니다. 컴파일하지 않고 소스 파일을 직접 읽는 방식이라서, 빌드 도구도 컴파일 성공도 필요 없습니다. 다만 호출 관계를 푸는 단계에서는 라이브러리 jar가 있느냐에 따라 결과의 질이 달라집니다.

## 단계별로 필요한 것

| 단계 | 빌드 필요 여부 | 근거 |
|---|---|---|
| SCAN (파일 목록) | 필요 없음 | 폴더를 훑기만 합니다 |
| DECLARE (타입·메소드·필드) | 필요 없음 | 파일 하나씩 파싱하고, 다른 파일이나 jar를 보지 않습니다 |
| RESOLVE (호출 관계, M2) | 필요 없음. 단 jar가 없으면 풀리는 비율이 떨어짐 | 아래 설명 |

## 실제로 확인된 것

- **빌드 파일이 아예 없는 경우**: `legacy-app`은 `pom.xml`도 `WEB-INF/lib`도 없는데 DECLARE가 기대 값대로 나왔습니다.
- **소스 일부가 깨진 경우**: `/app` 전체 시험에서 31,952개 중 32개가 문법 오류로 파싱에 실패했습니다. 이런 프로젝트는 빌드가 안 되지만, 분석은 그 32개만 오류로 남기고 나머지 31,919개를 정상 처리했습니다.
- **같은 클래스가 두 파일에 있는 경우**: 컴파일러라면 오류지만, 분석기는 한쪽만 넣고 계속했습니다.

## 빌드 불가 상태가 영향을 주는 부분

호출 관계를 풀 때입니다. `service.findOrders()`가 어느 클래스의 메소드인지 알려면 관련 타입을 찾아야 하는데, 프로젝트 밖의 타입(Spring, 서블릿 등)은 jar에서 찾습니다.

- **jar가 있으면**: `anybiz_prd`, `cms4_prd`는 `WEB-INF/lib`가 있어서 99.8% 이상 풀렸습니다. 이 두 앱도 빌드해 본 적은 없습니다.
- **jar가 없으면**: `dstone-batchadmin`은 18.9%였습니다. 부모 클래스를 못 찾으면 그 클래스 안의 호출이 줄줄이 실패하기 때문입니다.
- **일부만 없으면**: `legacy-app`은 `javax.servlet`만 빠져 83.7%였습니다.

즉 필요한 것은 "빌드 성공"이 아니라 "jar 파일이 어딘가에 있는 것"입니다. 그래서 앞서 M2에서 정할 것으로 클래스패스 얻는 방법을 말씀드렸습니다. `mvn dependency:build-classpath`를 쓰는 방법만 대상이 빌드 가능해야 하고, jar 폴더를 직접 지정하는 방법은 빌드 가능 여부와 무관합니다.

## jar가 없을 때의 대비

jar를 구할 수 없는 프로젝트도 있을 것이라, 못 푼 호출을 버리지 않고 이름과 인자 수로 프로젝트 안의 후보를 찾아 `confidence = LOW`로 남기는 방식을 M2에 넣자고 권했습니다. 이렇게 하면 jar가 전혀 없어도 프로젝트 내부의 호출 관계는 "짐작" 등급으로라도 남습니다.

이 방향이 맞다면 M2는 "jar가 없어도 끝까지 가고, 있으면 더 정확해진다"를 기준으로 설계하겠습니다.

---

## 질문

ok. 그대로 진행해. 그리고 java 주석처리할 때 <pre> 를 꼭 넣어줘.

---

## 답변

API를 확인했습니다. 스키마와 DB 매퍼부터 작성합니다(클래스패스 컬럼, 관계 저장, 집합 연산 SQL, 호출 그래프 조회).

---

## 답변

기반 부품을 만들었습니다. 이제 M2의 핵심인 참조 해석기(해석기로 풀고, 안 되면 단계적으로 짐작)를 작성합니다.

---

## 답변

람다 검사에 걸린 한 줄은 문자열 안의 `::` 글자라 문제없습니다. 테스트 14건이 통과했으니, 마지막으로 문서를 M2 내용으로 갱신합니다.

---

## 답변

M2(RESOLVE와 LINK 단계)를 끝냈습니다. 호출·상속·타입 사용이 누구를 가리키는지 풀어 관계로 저장하고, 호출자/피호출자를 API로 조회할 수 있습니다. 빌드와 단위 테스트 14건이 통과하고, 이번 변경은 아직 커밋하지 않았습니다. 수만 파일 규모의 RESOLVE는 아직 돌려 보지 않았습니다.

`<pre>` 주석 규칙은 `dstone-knowledge`의 기존 Java 파일 전체에 적용해 먼저 커밋·푸시했습니다(`69ebb9f`).

## 결과 (호출 기준)

| 대상 | jar | 호출 | 프로젝트 안 | 프로젝트 밖 | 짐작 | 못 풂 | RESOLVE 시간 |
|---|---|---|---|---|---|---|---|
| `anybiz_prd` | 106 | 58,398 | 65.8% | 34.2% | 0.0% | 0.03% | 51초 |
| `cms4_prd` | 113 | 19,009 | 63.2% | 36.7% | 0.0% | 0.09% | 15초 |
| `dstone-boot` | 316 | 11,884 | 9.0% | 90.8% | 0.0% | 0.15% | 25초 |
| `dstone-batchadmin` | 216 | 930 | 25.8% | 74.2% | 0.0% | 0.00% | 5초 |
| `legacy-app` | 0 | 49 | 30.6% | 63.3% | 4.1% | 2.04% | 0.2초 |
| `lombok-app` | 0 | 13 | 84.6% | 15.4% | 0.0% | 0.00% | 0.3초 |

- **`legacy-app`**: README가 기대한 관계가 만들어졌습니다. `OrderServlet.doGet`의 `findOrders` 호출이 가능한 구현 `OrderServiceImpl.findOrders`로 이어집니다.
- **Lombok**: `dstone-boot`는 Lombok 클래스를 선언만 하고 그 멤버를 부르는 코드가 없어 검증이 안 됐습니다. 그래서 Lombok 멤버를 부르는 작은 표본 `lombok-app`을 저장소에 추가했습니다. 빌더 체인, `setActive(true)`, `log.info(...)`를 포함한 호출 13건이 Lombok jar 없이 전부 기대대로 풀렸습니다.

## jar가 전혀 없을 때

말씀하신 방향("jar가 없어도 끝까지 가고, 있으면 더 정확해진다")대로 되는지 확인했습니다. `dstone-batchadmin`의 소스만 복사해 jar 0개로 돌리고, jar가 있을 때의 결과를 정답으로 놓고 대조했습니다.

- **프로젝트 안의 호출 쌍 234개 중 227개(97%)를 찾았습니다.**
- **신뢰도 등급이 실제 정확도를 반영합니다.** `HIGH` 46건과 `MEDIUM` 170건은 전부 정답이고, `LOW`는 27건 중 11건만 정답입니다. `LOW`는 참고용으로만 써야 합니다.
- **못 푼 28.9%**는 대부분 프로젝트 밖의 상위 클래스에서 물려받은 메소드입니다. 이 경우는 일부러 짐작하지 않습니다.

## 시험 중에 찾아 고친 것

- **"가능한 구현"의 잡음**: `anybiz_prd`에서 27,715건이 나왔습니다. `BaseVO.setMb_id()`를 부르는 호출 1,553건이 그것을 재정의한 7개 클래스 전부로 퍼진 것이었습니다. 몸통이 없는 메소드(인터페이스/abstract)를 부를 때만 만들도록 좁혔습니다.
- **JDK 타입 누락**: `org.w3c.dom.Node` 같은 JDK 타입을 못 찾아, JDK 판별 조건을 따로 두었습니다.
- **jar 없는 타입의 변수**: `request.getParameter(...)`를 변수 선언과 import로 풀도록 보강해 `legacy-app`이 83.7%에서 93.9%로 올랐습니다.

## 클래스패스

정한 대로 프로젝트 폴더 안의 jar는 자동으로 찾고, 프로젝트 등록 때 `classpath`로 추가 지정할 수 있습니다. `mvn dependency:build-classpath`의 출력을 그대로 넣어도 됩니다(`dstone-boot`를 이렇게 돌렸습니다). 대상 프로젝트에서 Maven을 자동 실행하는 기능은 넣지 않았습니다.

## 추가된 API

- `GET /api/revisions/{id}/methods?owner=&name=`: 메소드 찾기
- `GET /api/revisions/{id}/methods/{methodId}/callers?depth=`: 부르는 쪽 (최대 5단계)
- `GET /api/revisions/{id}/methods/{methodId}/callees?depth=`: 불리는 쪽

M1 끝에 만든 시험용 API(`/trials/type-solver`)는 예고대로 없앴습니다.

## 남은 한계

- **대규모 RESOLVE 미확인**: 가장 큰 실행이 489개 파일입니다. 파일당 시간으로 보면 3만 개에 한 시간쯤으로 예상합니다.
- **같은 이름의 타입이 여러 모듈에 있는 경우**: 지금은 하나만 골라서, 멀티모듈 프로젝트에서 틀릴 수 있습니다.
- **리플렉션 호출**: 관계가 생기지 않습니다.

문서는 `docs/11.dstone-knowledge.md`(4.5절 RESOLVE, 4.6절 LINK, 결과, 한계)와 `CLAUDE.md`를 갱신했습니다. 스키마에 `analysis_project.classpath` 컬럼이 추가됐고 로컬 DB에는 반영했습니다. 앱은 중지해 두었습니다.

## 다음

M3(의미 분석: Spring 진입점, 서블릿·`main`·`Runnable`, 계층 분류)입니다. 커밋·푸시할지, 그리고 M3 전에 대규모 RESOLVE를 `/app` 전체로 한 번 돌려 볼지 알려 주세요. 돌리면 한 시간쯤 걸립니다.

---

## 질문

먼저 확인할게... 순환호출에 대한 처리가 되어있나? 자칫 무한대기로 빠질 수 있는 함정이잖아.

---

## 답변

좋은 질문입니다. 추측으로 답하지 않고, 순환이 생길 수 있는 곳을 코드에서 짚어 본 뒤 실제로 순환이 들어 있는 표본을 만들어 돌려 확인하겠습니다.

---

## 답변

순환 호출 자체는 처리돼 있었습니다. 다만 확인하는 과정에서 Job 전체를 죽이는 다른 함정이 하나 실제로 나와서 고쳤고, 아직 막지 못하는 경우가 하나 남아 있습니다. 변경은 아직 커밋하지 않았습니다(M2 변경과 함께 있습니다).

확인을 위해 순환이 든 표본(`cycle-app`)을 만들어 돌렸습니다. 재귀 호출, `a → b → c → a`, 순환 상속, 문자열 6,000개를 `+`로 이은 식이 들어 있습니다.

## 순환 호출: 원래 안전했던 부분

- **분석(RESOLVE)**: 호출을 따라가지 않습니다. 호출 하나를 관계 하나로 적을 뿐이라 순환의 영향을 받지 않습니다. `a → b → c → a`는 관계 3건으로 끝났습니다.
- **호출 그래프 조회**: 따라가는 깊이가 최대 5로 묶여 있고, 같은 행을 다시 넣지 않습니다. 순환이 있는 `a()`의 호출자를 depth 5로 조회하면 4건이 0.02초에 나옵니다.
- **실제 앱**: `anybiz_prd`에서 가장 많이 불리는 메소드의 호출자 depth 5 조회는 1초였습니다(2,000행에서 잘림).
- **순환 상속**: 상위 타입을 20단계까지만 올라갑니다. 해석기가 스택 넘침으로 실패하면 그 참조 하나만 포기하고 계속합니다.

## 찾아서 고친 것

- **깊게 중첩된 식이 Job 전체를 죽임**: `StackOverflowError`가 파일 단위에서 잡히지 않아 Job이 `FAILED`가 됐고, 다시 시작해도 같은 파일에서 또 죽어 영원히 넘어가지 못했습니다. 이제 그 파일 하나만 `TOO_DEEP` 오류로 남고 나머지는 끝까지 갑니다.
- **파일 하나에 시간 제한이 없었음**: 파일당 120초 제한을 넣었습니다(설정 가능). 넘기면 그때까지 푼 것만 저장하고 남은 참조는 "시간 초과"로 남깁니다. 제한을 0초로 두고 돌려 동작을 확인했습니다.
- **큰 파일 도중에는 취소가 먹지 않았음**: 참조 하나를 풀 때마다 취소 요청을 확인하도록 했습니다. 이 부분은 따로 시험하지는 않았습니다.
- **순환 상속의 잘못된 관계**: 메소드가 자기 자신을 재정의한다는 관계가 생겨서 뺐습니다.
- **조회 결과의 중복**: 순환이 있으면 같은 호출이 깊이를 바꿔 여러 번 나와서, 가장 얕은 깊이 하나만 돌려주도록 했습니다.

## 아직 막지 못하는 경우

시간 제한과 취소는 "참조 하나를 풀고 다음으로 넘어갈 때" 확인합니다. 해석기 호출 **한 번**이 스택도 넘치지 않으면서 끝나지 않으면, 그 스레드를 밖에서 멈출 방법이 없습니다.

- **발생 여부**: 지금까지 약 9만 건의 호출을 푸는 동안에는 없었습니다. 해석기의 무한 반복은 대부분 스택 넘침으로 나타나고, 그것은 잡고 있습니다.
- **생기면**: `heartbeat_at`이 멈춘 것으로 알 수 있고, 서버를 다시 띄우면 Job이 정리됩니다. 다만 그 파일이 다시 걸리므로 수동으로 `FAILED` 표시를 해야 넘어갑니다.
- **자동으로 넘어가게 하려면**: 파일 처리를 감시 스레드 아래에서 돌려야 합니다. 멈춘 스레드는 강제로 끝낼 수 없어 CPU 하나를 계속 쓰게 되므로, 일정 횟수를 넘으면 Job을 실패시키는 장치도 같이 필요합니다.

감시 스레드는 구조가 꽤 바뀌는 일이라 넣지 않았습니다. 대규모 RESOLVE를 돌려 보고 실제로 멈추는 파일이 나오는지 본 뒤 결정하는 쪽을 권합니다. 지금 넣기를 원하시면 말씀해 주세요.

## 정리

- `cycle-app`을 저장소의 검증 표본으로 추가했습니다(기대 값은 그 폴더의 README).
- `docs/11.dstone-knowledge.md`에 4.7절(순환과 멈춤에 대한 방어)을 추가했고 `CLAUDE.md`도 갱신했습니다.
- 빌드와 테스트 14건이 통과합니다. 앱은 중지해 두었습니다.

커밋·푸시할지, 그리고 M3 전에 `/app` 전체로 대규모 RESOLVE(한 시간쯤)를 돌려 볼지 알려 주세요.

---

## 질문

순환참조 방지로직은 지금 처리해줘.

---

## 답변

파일 처리를 감시 스레드 아래에서 돌려, 해석기 호출 한 번이 끝나지 않는 경우에도 자동으로 그 파일을 건너뛰게 하겠습니다. 먼저 현재 코드를 확인합니다.

---

## 답변

단위 테스트 16건이 통과했습니다. 이제 실제 앱에서 확인합니다. 제한 시간을 1초로 낮춰 오래 걸리는 파일이 "끝나지 않는 파일"로 취급되게 합니다.

---

## 답변

끝나지 않는 처리에 대한 방어(감시 스레드)를 넣었습니다. 파일 하나가 정해 둔 시간 안에 끝나지 않으면 그 파일만 실패로 남기고 다음 파일로 넘어갑니다. 빌드와 단위 테스트 16건이 통과하고, 변경은 아직 커밋하지 않았습니다(M2 변경과 함께 있습니다).

## 동작 방식

파일 하나의 처리를 두 걸음으로 나눴습니다.

- **준비**: 파일을 읽고, 파싱하고, 호출을 푸는 부분입니다. DB에 쓰지 않고, 감시 아래의 작업 스레드에서 돕니다.
- **저장**: 준비한 결과를 한 트랜잭션으로 씁니다.

준비가 제한 시간(기본 600초) 안에 끝나지 않으면 이렇게 됩니다.

1. 그 파일을 `FAILED`(오류 종류 `HUNG`)로 남깁니다.
2. 작업 스레드를 버리고 새 스레드로 다음 파일을 계속합니다.
3. 버려진 스레드가 쓰던 해석 도구(타입 해석기, 캐시)를 통째로 새로 만듭니다.
4. 이렇게 건너뛴 파일이 3개를 넘으면 Job을 실패시킵니다(설정 가능).

준비 걸음이 DB 연결이나 트랜잭션을 잡지 않도록 나눈 덕분에, 버려진 스레드가 연결이나 잠금을 붙들고 남지 않습니다.

## 알아 두실 한계

Java에서는 돌고 있는 스레드를 밖에서 강제로 끝낼 수 없습니다. 그래서 "멈춘다"가 아니라 "버리고 넘어간다"입니다. 실제로 무한 반복에 빠진 스레드는 서버를 다시 띄울 때까지 CPU 하나를 계속 씁니다. 건너뛴 파일 수에 상한(3개)을 둔 이유가 이것입니다. 완전히 끝내려면 분석을 별도 프로세스에서 돌려야 하는데, 거기까지는 하지 않았습니다.

## 확인한 것

실제로 끝나지 않는 호출은 지금까지 나온 적이 없어서, 제한 시간을 1초로 낮춰 "오래 걸리는 파일"로 대신 시험했습니다.

| 시험 | 결과 |
|---|---|
| 제한 1초, 허용 1,000개 (`anybiz_prd`) | 1초 넘게 걸린 8개만 `HUNG`으로 건너뛰고 481개 처리, `DONE_WITH_WARNING` |
| 제한 1초, 허용 2개 | 3번째에서 Job이 이유를 적고 `FAILED` |
| 설정을 되돌려 다시 시작 | 건너뛴 3개는 `FAILED`로 남고 남은 486개만 처리 |
| 정상 설정으로 전체 재실행 | 일곱 표본 모두 감시자를 넣기 전과 관계 수까지 동일 |
| 단위 테스트 | 무한 반복하는 일을 0.2초 뒤 버리고 다음 일을 바로 처리 |

DECLARE도 같은 구조로 바꿨고, 세 표본을 처음부터 다시 분석해 결과가 이전과 같은 것을 확인했습니다.

## 지금까지의 방어 정리

| 함정 | 방어 |
|---|---|
| 재귀·순환 호출 | 호출 그래프 조회의 깊이 제한(최대 5)과 중복 제거 |
| 순환 상속 | 상위 타입 20단계 제한, 해석기의 스택 넘침을 참조 단위로 처리 |
| 지나치게 깊게 중첩된 식 | 파일 하나의 실패(`TOO_DEEP`) |
| 파일 하나가 오래 걸림 | 부드러운 제한 120초: 그때까지 푼 것만 저장 |
| 호출 한 번이 끝나지 않음 | 감시자, 600초: 파일을 건너뜀(`HUNG`) |

설정 두 개가 `conf/application.yml`에 추가됐습니다(`job.file-hard-timeout-seconds`, `job.max-stuck-files`). 문서는 `docs/11.dstone-knowledge.md` 4.1절과 4.7절, `CLAUDE.md`를 갱신했습니다. 앱은 중지해 두었습니다.

커밋·푸시할지 알려 주세요.

---

## 질문

커밋 푸시 해줘.

---

## 답변

커밋하고 푸시했습니다(`1b04280`). 로컬과 `origin/main`이 같은 상태입니다.

M2 전체(RESOLVE/LINK 패스, 호출 그래프 API, 클래스패스 처리)와 순환·멈춤 방어(감시자 포함), 검증 표본 두 개(`lombok-app`, `cycle-app`), 문서 갱신이 한 커밋에 들어 있습니다. 파일 41개입니다.

Windows 쪽에서 받으실 때는 스키마 SQL(`02-create-table-…sql`)을 한 번 다시 실행해야 `analysis_project.classpath` 컬럼이 생깁니다. WSL의 DB는 이미 반영돼 있습니다.

다음은 M3(의미 분석: Spring 진입점, 서블릿·`main`·`Runnable`, 계층 분류)입니다. 그 전에 `/app` 전체로 대규모 RESOLVE를 돌려 볼지는 아직 정하지 않으셨습니다. 한 시간쯤 걸리는 작업인데, 감시자가 실제로 걸리는 파일이 있는지도 함께 볼 수 있습니다. 어느 쪽으로 진행할지 알려 주세요.

---

## 질문

일단 M3 진행하기 전에 현재 까지 진행된 개발부분을 내가 로컬에서 확인해볼 수 있는 방법이 있어?

---

## 답변

네, WSL(`/app/dstone`)에서 바로 확인하실 수 있습니다. jar는 최신 코드로 빌드돼 있고, DB에는 일곱 개 표본의 분석 결과가 이미 들어 있어서 앱만 띄우면 조회부터 해 볼 수 있습니다.

## 1. 앱 띄우기

```bash
sh /app/dstone/dstone-knowledge/bin/startApp.sh
curl http://localhost:4081/api/system/health     # "status":"UP", "tableCount":21 확인
```

아래 명령은 `B=http://localhost:4081/api`를 먼저 잡아 두고 쓰시면 됩니다. 응답 뒤에 `| python3 -m json.tool --no-ensure-ascii`를 붙이면 한글이 보기 좋게 나옵니다.

## 2. 이미 들어 있는 결과 조회

현재 DB의 리비전 번호입니다.

| 프로젝트 | 리비전 | 특징 |
|---|---|---|
| `legacy-app` | 20 | 구버전 Java, jar 없음 |
| `lombok-app` | 15 | Lombok 멤버를 부르는 코드 |
| `cycle-app` | 21 | 순환 호출·순환 상속, 일부러 실패하는 파일 1개 |
| `anybiz_prd` | 22 | 올려 주신 실제 앱 (파일 1,142개) |
| `cms4_prd` | 12 | 올려 주신 실제 앱 |
| `dstone-batchadmin` | 13 | 이 저장소의 Spring 앱 |
| `dstone-boot` | 14 | 이 저장소의 Spring 앱 |

```bash
# 리비전 요약: 단계별 상태, 파일·타입·관계 수, 못 푼 이유, 품질 지표
curl $B/revisions/22

# 파일 목록 (SCAN 결과)
curl "$B/revisions/22/files?language=JAVA&size=10"
curl "$B/revisions/22/files?fileType=MYBATIS_MAPPER&size=10"

# 타입 목록과 타입 하나의 상세 (DECLARE 결과)
curl "$B/revisions/20/symbols?name=OrderServiceImpl"
curl $B/revisions/20/symbols/<symbolId>       # 메소드, 필드, 애노테이션, 참조

# 호출 그래프 (RESOLVE/LINK 결과)
curl "$B/revisions/20/methods?owner=OrderServiceImpl&name=findOrders"   # methodId 얻기
curl "$B/revisions/20/methods/<methodId>/callers?depth=2"
curl "$B/revisions/20/methods/<methodId>/callees?depth=3"
```

`legacy-app`의 `OrderServiceImpl.findOrders` 호출자를 보면, `OrderServlet.doGet`이 인터페이스를 거쳐 "가능한 구현"으로 이어진 것을 확인하실 수 있습니다.

## 3. 처음부터 직접 돌려 보기

```bash
# 프로젝트 등록 (이미 등록된 것은 생략 가능. 본인 소스 경로를 넣어도 됩니다)
curl -X POST $B/projects -H 'Content-Type: application/json' \
     -d '{"projectId":"my-test","localPath":"/app/sampleApps/cms4_prd"}'

# 분석 시작: 응답의 analysisId, revisionId를 아래에서 씁니다
curl -X POST $B/projects/my-test/analyses -H 'Content-Type: application/json' -d '{"revisionLabel":"r1"}'

# 진행 상태 (SCAN → DECLARE → RESOLVE → LINK 순으로 넘어갑니다)
curl $B/analyses/<analysisId>
```

`cms4_prd` 기준으로 20초쯤 걸립니다. 이미 있는 라벨(`r1`)로 기존 프로젝트를 다시 시작하면 끝난 단계를 전부 건너뛰고 바로 `DONE`이 되니, 처음부터 보려면 새 프로젝트 ID나 새 라벨을 쓰세요.

## 4. 표본의 기대 값과 대조

표본 세 개는 폴더의 `README.md`에 기대 값이 적혀 있어서 결과와 직접 비교할 수 있습니다.

- `dstone-knowledge/src/test/resources/samples/legacy-app/README.md`
- `dstone-knowledge/src/test/resources/samples/lombok-app/README.md`
- `dstone-knowledge/src/test/resources/samples/cycle-app/README.md`

## 5. DB에서 직접 보기

```bash
PGPASSWORD='db2admin!@' psql -h 127.0.0.1 -U dstone_knowledge -d dstone_knowledge
```

```sql
-- 관계 종류와 신뢰도별 수
SELECT relation_type, to_kind, confidence, count(*) FROM analysis_relation
 WHERE revision_id = 22 GROUP BY 1,2,3 ORDER BY 1,2,3;

-- 그래프 모양으로 보기 (VIEW)
SELECT a.fqn AS 부르는쪽, e.edge_type, b.fqn AS 불리는쪽, e.confidence
  FROM kg_edge e JOIN kg_node a ON a.revision_id = e.revision_id AND a.node_id = e.from_node_id
                 JOIN kg_node b ON b.revision_id = e.revision_id AND b.node_id = e.to_node_id
 WHERE e.revision_id = 20 AND e.edge_type = 'CALLS' LIMIT 20;

-- 못 푼 참조와 이유
SELECT ref_kind, name, fail_reason FROM analysis_reference
 WHERE revision_id = 22 AND status = 'UNRESOLVED' LIMIT 20;
```

## 6. 그 밖에

```bash
# 단위 테스트 16건 (DB 없이 돕니다)
cd /app/dstone && mvn -pl dstone-knowledge test

# 취소와 이어 하기
curl -X POST $B/analyses/<analysisId>/cancel
# 같은 라벨로 다시 시작하면 남은 파일부터 이어서 합니다

# 리비전 삭제
curl -X DELETE $B/revisions/<revisionId>

# 끝나면 중지
sh /app/dstone/dstone-knowledge/bin/stopApp.sh
```

로그는 `dstone-knowledge/logs/dstone-knowledge.out`에 단계별로 남습니다.

## 참고

- **화면은 아직 없습니다.** 지금은 REST API와 SQL로만 확인할 수 있습니다. 화면은 M8입니다.
- **Windows에서 확인하시려면** `git pull` 후 스키마 SQL을 다시 실행해야 하고, `localPath`를 Windows 경로로 등록해야 합니다. 그쪽 설정이 어느 DB를 보는지는 제가 확인하지 못했습니다.

이 확인 절차를 `docs/11.dstone-knowledge.md`에 "직접 확인하기" 절로 넣어 둘까요? 원하시면 위 명령을 한 번에 돌리는 셸 스크립트도 만들 수 있습니다.
