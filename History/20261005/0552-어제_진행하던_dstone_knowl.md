# 어제_진행하던_dstone_knowl

> 생성일시: 2026-10-05 05:52

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
