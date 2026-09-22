# 동양생명 SDLC 워크플로우 구축 계획 (dstone-ai-engine)

작성일: 2026-09-22
대상: `dstone-ai-engine` (`resources/workflows/dongyang/*.yml`)
참고자료: `docs/temp/생성형AI_코드에이전트_시스템구축_제안서.pdf` (Braincrew, 동양생명 대상)

---

## 0. 먼저 스코프를 좁힌다 — 제안서 전체가 아니라 그 안의 "승인형 SDLC 오케스트레이션"만

첨부 제안서는 데스크탑 앱/IDE 플러그인/CLI 3채널, 위키형 지식DB(SoT), 온프레미스 GPU 추론서버(H200×4~33장),
격리 실행(Docker/VM), MCP/npm 사내 배포체계까지 포함하는 **전사 코드에이전트 플랫폼(DeepCode) 구축 사업**입니다.
이건 별도의 인프라·제품 도입 사업이고, 지금 우리가 가진 `dstone-ai-engine`이 대체하거나 재현할 대상이 아닙니다.

이번에 실제로 요청하신 건 그중 한 조각입니다:

> JIRA 접수 → AI 분석/설계 → 검수 → AI 명세서 작성 → PL 검토/승인 → AI 소스 생성 → 개발자 수정/검수 →
> PL 최종 승인 → Jenkins 검증서버 파이프라인 기동

이건 "코드에이전트 제품"이 아니라 **사람의 승인 게이트가 여러 번 끼어드는 다단계 업무 프로세스**입니다.
그리고 `dstone-ai-engine`은 이미 이 모양을 위해 설계되어 있습니다: `Workflow → Step(AGENT/TOOL/SUPERVISOR/
APPROVAL/ROUTER) → Agent → Tool` 구조, 그중에서도 `APPROVAL` 스텝(`ApprovalStepRunner`)이 정확히
"사람이 승인/반려할 때까지 실행을 멈췄다가, 결정이 나면 그 자리부터 이어간다"는 동작을 이미 구현하고 있습니다.
실행 상태는 `AI_WORKFLOW_EXECUTION`/`AI_WORKFLOW_EXECUTION_STEP_HISTORY` (Postgres, `WorkFlowExecutionStore`)에
영속 저장되므로, 승인 대기가 며칠~몇 주 걸려도(개발자가 코드를 고치는 동안 포함) 엔진 재기동과 무관하게 안전합니다.

**결론: 새 플랫폼을 만드는 게 아니라, 기존 Workflow 엔진 위에 `dongyang` 전용 Workflow/Agent/Tool 정의를 얹는
작업입니다.** 인프라(GPU서버, 위키, 격리실행, 3채널 UI 등)는 이번 스코프에 포함하지 않습니다.

---

## 1. 8단계 요구사항 → 기존 엔진 구성요소 매핑

| # | 요구사항 | dstone-ai-engine 매핑 |
|---|---|---|
| 1 | JIRA 접수 | 신규 `TOOL` step(`jiraGetIssue`) — JIRA Automation/Webhook이 `POST /api/ai/workflow/dongyang-sdlc/submit` 호출 |
| 2 | AI 분석/설계 | `AGENT` step, 신규 Agent `dongyang-analysis-agent` |
| 3 | 분석/설계 검수 | `APPROVAL` step (`approverRole: "business-reviewer"`) — 반려 시 2번으로 루프백 |
| 4 | AI 개발명세서 작성 → PL 검토 | `AGENT` step(신규 `dongyang-spec-writer-agent`) + `APPROVAL` step(`approverRole: "PL"`) — 반려 시 재작성 루프백 |
| 5 | AI 기본 소스 생성 | `AGENT` step(신규 `dongyang-codegen-agent`, structuredOutput) + `TOOL` step(코드 산출물 기록) |
| 6 | 개발자 수정/검수 | 워크플로우 밖(IDE에서 직접 작업) + `APPROVAL` 성격의 "제출" step(`approverRole: "developer"`) |
| 7 | PL 검토/승인 | `APPROVAL` step(`approverRole: "PL"`) — 반려 시 6번(개발자 제출)으로 루프백 |
| 8 | Jenkins 검증서버 기동 | 신규 `TOOL` step(`jenkinsTriggerBuild`) |

재사용(신규 코드 불필요):
- `WorkFlowExecutor`/`ApprovalStepRunner` — 승인 대기/재개
- `WorkFlowExecutionController` (`/api/ai/workflow/executions`, `GET ?status=WAITING_APPROVAL`, `POST /{id}/decision`)
  — dstone-boot의 "Workflow 실행 관리" 화면이 이미 이 API로 큐를 보여주고 승인/반려를 처리함
- `structuredOutput`/`{stepId.키}` 변수 전달 — 분석 결과 → 명세서 → 소스 생성으로 데이터 체이닝
- `ConfigTool` 화이트리스트(caller별 allowed-by-caller) — dongyang 전용 Tool을 다른 프로젝트 caller에서 못 쓰게 격리

신규로 만들어야 하는 것(아래 2~5절):
- `resources/workflows/dongyang/*.yml`, `resources/agents/dongyang-*.yml`
- `tools.jira.JiraTool`, `tools.jenkins.JenkinsTool` (신규 `@AiTool`)
- `conf/application.yml`에 `dstone.ai.tool.jira.*` / `dstone.ai.tool.jenkins.*` 설정 + `ENC(...)` 토큰

---

## 2. 신규 Tool: JIRA / Jenkins 연동

기존 `tools/http/HttpCallTool`은 인증 헤더 없는 단순 GET 화이트리스트 Tool이라 JIRA/Jenkins처럼 토큰 인증이
필요한 API에는 그대로 못 씁니다. `tools/shell`, `tools/python`과 같은 "deny-by-default, 화이트리스트가
곧 on/off" 철학을 그대로 따르는 전용 Tool 2개를 새로 만듭니다.

### `tools.jira.JiraTool`
- `getIssue(issueKey)` — 요약/설명/상태 텍스트로 반환 (분석 Agent의 입력이 됨)
- `addComment(issueKey, comment)` — 각 승인 게이트 결과를 JIRA에 코멘트로 남김 (감사 추적)
- `transitionIssue(issueKey, transitionName)` — 승인 통과 시 JIRA 상태 전이(예: "AI분석완료", "개발중", "검증대기")
- 인증: API 토큰은 `conf/application.yml`에 `ENC(...)`로 저장 (기존 DB 비밀번호와 동일한 방식, `EncUtil` 재사용)
- 화이트리스트: `dstone.ai.tool.jira.allowed-project-keys` — 이슈 키의 프로젝트 접두사가 목록에 없으면 거부
  (다른 프로젝트 JIRA 이슈를 실수로/악의적으로 건드리는 것 방지)

### `tools.jenkins.JenkinsTool`
- `triggerBuild(jobName, params)` — Jenkins Remote API로 파이프라인 기동
- 화이트리스트: `dstone.ai.tool.jenkins.allowed-jobs` (job 이름 화이트리스트, `ShellExecTool.allowed-commands`와 동일한 패턴)
- 인증: Jenkins API 토큰도 `ENC(...)`

두 Tool 모두 별도 on/off 플래그 없이 "화이트리스트가 비어있으면 곧 꺼진 상태"인 기존 원칙을 그대로 따릅니다.

**보완 필요(확인 사항)**: 동양생명 JIRA가 Cloud인지 Server/Data Center인지에 따라 REST API 버전(v2 vs v3)과
인증 방식(API 토큰 vs PAT vs OAuth)이 달라집니다 — 착수 시 확인 필요.

---

## 3. Workflow 설계 — `resources/workflows/dongyang/`

하나의 긴 Workflow(`dongyang-sdlc.yml`)로 8단계를 전부 표현합니다. 분리하지 않는 이유: `APPROVAL` step은
이미 "몇 주가 걸려도 상태가 영속되는" 멈춤-재개를 지원하므로, 굳이 여러 Workflow로 쪼개고 그 사이를 JIRA 상태로
동기화하는 복잡성을 만들 필요가 없습니다. 한 실행(`executionId`)이 JIRA 이슈 하나의 전체 생애주기를 그대로 따라갑니다.

```
intake (TOOL: jiraGetIssue)
  └─▶ analyze (AGENT: dongyang-analysis-agent, structuredOutput)
        └─▶ design-review (APPROVAL: business-reviewer)
              ├─ 승인 ─▶ write-spec (AGENT: dongyang-spec-writer-agent, structuredOutput)
              │             └─▶ spec-review (APPROVAL: PL)
              │                   ├─ 승인 ─▶ generate-code (AGENT: dongyang-codegen-agent, structuredOutput)
              │                   │             └─▶ record-code (TOOL: 코드 산출물 기록/첨부)
              │                   │                   └─▶ dev-submit (APPROVAL: developer, "제출")
              │                   │                         ├─ 제출 ─▶ code-review (APPROVAL: PL)
              │                   │                         │             ├─ 승인 ─▶ trigger-jenkins (TOOL: jenkinsTriggerBuild)
              │                   │                         │             │             └─▶ notify-done (TOOL: jiraTransition/comment) ─▶ SUCCESS
              │                   │                         │             └─ 반려 ─▶ dev-submit (재수정 루프백)
              │                   │                         └─ (반려는 없음 — 제출만 하는 게이트)
              │                   └─ 반려 ─▶ write-spec (재작성 루프백, PL 코멘트를 inputTemplate에 반영)
              └─ 반려 ─▶ analyze (재작성 루프백, 반려 코멘트를 inputTemplate에 반영)
```

각 승인 스텝의 반려 사유(`comment`)는 `variables.approvals.{stepId}.comment`에 이미 저장되므로, 루프백 대상
Agent step의 `inputTemplate`에서 그대로 참조해 "이전에 왜 반려됐는지"를 다음 생성 시도에 반영합니다.

`maxIterations`는 기본값(5)보다 넉넉하게(예: 20) 잡아야 합니다 — 승인 루프가 3군데(분석/명세/코드)라 재시도가
누적되면 5회로는 부족합니다.

`allowedCallers: ["dongyang"]` 로 caller를 고정해, 이 Workflow와 전용 Tool(jira/jenkins)이 다른 SI 프로젝트
caller에서 오호출되지 않도록 격리합니다.

---

## 4. 신규 Agent 3종 — `resources/agents/`

| 파일 | 역할 | 비고 |
|---|---|---|
| `dongyang-analysis-agent.yml` | JIRA 원문 → 분석/설계 초안(structuredOutput: primaryText=요약, data={분석서 마크다운}) | `ragEnabled: true` 권장(5절) |
| `dongyang-spec-writer-agent.yml` | 승인된 분석/설계 → 개발명세서(표준 레이어/명명규칙 반영) | `ragEnabled: true` 권장 |
| `dongyang-codegen-agent.yml` | 승인된 명세서 → 기본 소스 초안(파일 경로+내용 목록을 data에) | `toolsEnabled` 여부는 6절 참고 |

세 Agent 모두 `allowedCallers: ["dongyang"]`로 다른 프로젝트에서 못 쓰게 막습니다.

---

## 5. 지식DB(위키) 자리에 — 새로 안 만들고 기존 RAG 재사용

제안서 3.4장의 "위키형 코드지식DB"는 별도 시스템입니다. 하지만 `dstone-ai-engine`에는 이미
`dstone.ai.rag.enabled`, `RagRetrievalChain`, `EmbedController`(`POST /api/ai/embed/documents`),
`RagSearchTool`, `Agent.ragEnabled`로 구성된 RAG 파이프라인이 존재합니다(Postgres+pgvector, 로컬 Ollama
bge-m3). 동양생명 프로젝트의 개발표준 문서·명명규칙·기존 설계문서를 이 파이프라인에 적재하고, 위 3개
Agent에 `ragEnabled: true`를 주면 "레거시/표준을 참조해서 분석·명세·코드를 생성"하는 효과를 별도 위키
시스템 없이 얻을 수 있습니다.

**보완 필요**: 지금 RAG는 tenant(=caller) 단위로 문서를 격리하므로, 적재 시 `caller=dongyang`으로 넣어야
다른 프로젝트 문서와 섞이지 않습니다. 어떤 문서(표준 가이드, 기존 소스 일부, ERD 등)를 얼마나 적재할지는
착수 후 실제 문서를 보고 정해야 합니다 — 제안서의 "1개월차 진단" 단계에 해당하는 작업입니다.

---

## 6. 보완이 필요한 부분 (그대로 가면 위험한 지점)

1. **`approverRole`은 지금 감사 기록용일 뿐, 실제 권한 검증이 아닙니다.**
   `StepDefinition` 문서에 명시되어 있듯 "서버가 실제로 그 역할인지 검사하지는 않습니다" — 즉 지금 구조로는
   아무나 `decision` API를 호출해 "PL 승인"을 자칭할 수 있습니다. PL 승인이 Jenkins 파이프라인 기동으로
   이어지는 이상, 최소한 승인자 식별(예: dstone-boot 로그인 사용자와 PL 목록 대조) 정도는 붙여야 실사용
   가능한 게이트가 됩니다. 지금 범위에서는 API Key(caller) 수준 보호만 있고, 개인별 권한 검증은 없습니다.
2. **소스 코드 생성(5단계)의 파일 쓰기 범위.** 지금 엔진에 "LLM이 생성한 코드를 실제 파일/Git 브랜치에
   쓰는" Tool은 없습니다(`ShellExecTool`/`PythonExecTool`은 화이트리스트된 스크립트만 실행하는 범용
   실행기라 이 용도로 쓰려면 스크립트 자체를 새로 작성해야 함). 1차로는 "코드 산출물을 텍스트/구조화
   데이터로 만들어 JIRA 첨부·코멘트로 남기고 개발자가 직접 적용"하는 안전한 방식으로 시작하고, 실제
   파일 자동 커밋은 보안 검토 후 2단계로 넣는 걸 권장합니다.
3. **JIRA 접수 트리거 방식 미확정.** JIRA Automation의 아웃고잉 웹훅이 `dstone-ai-engine`의
   `/api/ai/workflow/dongyang-sdlc/submit`을 직접 호출할 수 있는 네트워크 경로가 있는지(사내망 내부인지,
   방화벽/프록시 필요한지) 확인 필요.
4. **Jenkins Job 이름/파라미터, 대상 저장소(어떤 Git 레포에 코드를 생성하는지)가 아직 미정.** 이건
   "동양생명 사내 개발 대상 프로젝트"가 dstone 자체가 아니라 별도 고객 프로젝트라는 전제인데, 맞는지
   확인 필요.

---

## 7. 단계별 구축 순서

| 단계 | 내용 |
|---|---|
| Phase 0 | `JiraTool`/`JenkinsTool` 뼈대 + 설정(`dstone.ai.tool.jira.*`, `.jenkins.*`) 추가, `getIssue`만 우선 구현해 연동 확인 |
| Phase 1 | `dongyang-analysis-agent`/`dongyang-spec-writer-agent` + `dongyang-sdlc.yml`에서 intake→analyze→design-review→write-spec→spec-review 까지 (승인 2개 게이트, 코드생성 이전까지) 구현·검증 |
| Phase 2 | `dongyang-codegen-agent`(텍스트/구조화 산출물만) + dev-submit/code-review 게이트 + `trigger-jenkins` 추가로 8단계 전체 완성 |
| Phase 3 (보완) | RAG에 동양생명 개발표준 문서 적재(`ragEnabled: true` 전환), 승인자 실제 권한 검증 붙이기, 코드 자동 커밋(파일 쓰기) 검토 |

---

## 8. 확인이 필요한 질문 — 답변 반영 완료 (2026-09-22)

- JIRA는 Cloud / Server(Data Center) 중 무엇이고, 인증은 API 토큰/PAT/OAuth 중 무엇을 쓰나요?
  → **아직 미설치. WSL 로컬에 신규 설치 예정** (9절 참고)
- JIRA → dstone-ai-engine 호출 경로(Automation 웹훅이 사내망에서 도달 가능한지)를 확인해 주실 수 있나요?
  → **WSL 로컬 설치이므로 문제 없음** (같은 머신, `host.docker.internal` 또는 동일 네트워크로 도달 가능 — 9절 참고)
- 5단계에서 생성한 소스는 어떤 저장소/브랜치 전략에 반영되나요?
  → `/app/dongyang/testApp`, 원격 `git@github.com:YongSoekChoeng/dongyang-testApp.git`. 실제 서비스 소스가 아니라
    "동양생명 애플리케이션을 흉내낸" 테스트용 더미 프로젝트 — 자유롭게 수정/삭제 가능. **MariaDB(로컬 MySQL)
    기반 부분만 남기고 나머지는 삭제, 돌아가게만 포팅** (10절 참고 — 이미 상당 부분 진행함)
- 8단계 "Jenkins 검증서버 파이프라인"은 기존 CI/CD Jenkinsfile 중 하나를 재사용하나요, 아니면 새로 만들어야 하나요?
  → **Jenkins는 설치만 된 상태, 파이프라인은 아직 없음.** 신규로 만들고 "로컬에 포팅"(로컬 빌드 후 로컬 배포)되도록 구성
- "PL"의 승인 권한을 시스템적으로 검증해야 하나요?
  → **1차 MVP는 로그인 사용자 대조 없이 감사 기록 수준으로 충분** (6절의 승인자 실권한 미검증 이슈는 당분간 그대로 두고 진행)

---

## 9. JIRA 로컬 설치 가이드 (WSL)

Atlassian은 더 이상 "Server" 라이선스를 판매하지 않는다 (2024년 종료) — 로컬에서 띄울 수 있는 건
**Jira Software Data Center 평가판**(Docker 이미지, 기간제 무료 평가 라이선스)뿐이다. 절차:

1. https://www.atlassian.com/software/jira/download (또는 my.atlassian.com) 에서 평가 라이선스를 발급받는다
   (Atlassian 계정 필요 — 이 과정은 브라우저에서 사용자가 직접 진행).
2. WSL에 Docker Compose로 Jira + 자체 DB(Postgres)를 띄운다:
   ```yaml
   services:
     jira-db:
       image: postgres:13
       environment:
         POSTGRES_DB: jiradb
         POSTGRES_USER: jira
         POSTGRES_PASSWORD: jira
       volumes: [ jira-db-data:/var/lib/postgresql/data ]
     jira:
       image: atlassian/jira-software:latest
       depends_on: [ jira-db ]
       ports: [ "8090:8080" ]   # dstone-ai-engine(8081)/Jenkins(8080)와 겹치지 않게 8090 사용
       volumes: [ jira-data:/var/atlassian/application-data/jira ]
   volumes:
     jira-db-data:
     jira-data:
   ```
3. `docker compose up -d` 후 `http://localhost:8090` 설정 마법사에서 위 평가 라이선스 입력, DB는 `jira-db`(Postgres) 직접 연결로 선택.
4. 프로젝트 생성 후, **Automation** 규칙(이슈가 특정 상태/라벨로 바뀌면 Webhook 발송)을 만들어
   `POST http://host.docker.internal:8081/api/ai/workflow/dongyang-sdlc/submit`(dstone-ai-engine, `X-API-Key` 헤더)로 연결한다.
   같은 WSL 머신 안이므로 방화벽/터널링 이슈 없음 — Docker 컨테이너에서 호스트로 나가는 방향은 `host.docker.internal`이면 된다
   (또는 dstone-ai-engine을 `0.0.0.0`으로 바인딩하고 WSL 호스트 IP를 직접 써도 됨).

## 10. testApp 정리 진행 상황 (2026-09-22 세션에서 완료)

원본은 `kr.co.gnx`/`cms4_prd`라는, 실제 서비스 중인 것으로 보이는 회사(KB라이프파트너스, `kblifepartners.co.kr`)의
보험 수수료/인사관리 시스템(Spring 4.3 / Spring Security 4.2 / MyBatis 3.4 / Java 8, WAR)이었다.

**보안 조치(완료)**: `globals.xml`에 있던 실제 운영/개발 Oracle+MariaDB 접속정보(Jasypt `ENC(...)` +
평문 복호화 비밀번호)를 전부 제거했다. 로컬 MySQL 평문 설정으로 교체 — 더 이상 실제 크리덴셜이 없으므로
GitHub에 올려도 안전하다.

**구조 정리(완료)**:
- 표준 Maven 레이아웃(`src/main/java` / `src/main/resources` / `src/main/webapp`)으로 재배치, `pom.xml`을 프로젝트 루트로 이동
- 데이터소스를 Oracle(메인) + MariaDB(ERP) + MariaDB(정보계) 3원화 → **로컬 MySQL 하나로 통합**
  (`getSqlSession()`/`getSqlSessionAnybiz()` 호출부 Java 코드는 그대로 두고, 두 Bean을 같은 데이터소스로 alias)
- 유지: 로그인(Spring Security, `security`/`system.login`), ERP(`erp`), 공통코드관리(`system.commoncode`),
  회원관리(`system.member`), 액션/에러 로그(`logs`), 파일(`system.file`), 공통 프레임워크(`base`/`config`/`exception`/
  `filter`/`interceptor`/`comm`)
- 삭제: 인사/보험수수료계산/보험수수료확정/전처리작업/기타수수료(Oracle 전용 대형 업무모듈), SMS, Swagger API 문서,
  메뉴/역할/사용자권한/엑셀업로드/마감/ubi4뷰어(권한 세부 체크 UI), CMS→정보계 이관 배치(스케줄러)
- Jasypt/Oracle JDBC/JasperReports/iText/Springfox 등 더 이상 필요 없는 의존성 제거, `javax.annotation-api`
  (JDK11+에서 빠진 `@Resource`) 추가, 로컬 실행용 `tomcat7-maven-plugin` 추가
- **`mvn compile` / `mvn package` 둘 다 성공** (JDK 21로 컴파일, `maven.compiler.source/target=1.8` 유지 — 별도 JDK8 설치 불필요, 시스템 Tomcat 설치도 불필요·`mvn tomcat7:run`으로 로컬 실행 가능)

**추가 진행 및 완료 (2026-09-22, 같은 세션 후속)**:

- **이름 정리**: 사용자 요청으로 프로젝트 전체에서 "dongyang" 단어를 제거했다 — 디렉토리
  `/app/dongyang/testApp` → **`/app/testApp`**, MySQL DB `dongyang_testapp` → **`testApp`**
  (계정도 `dongyang` → **`testapp`**), git 원격 저장소도 **`git@github.com:YongSoekChoeng/testApp.git`**로 변경.
  (본 계획 문서 자체와 dstone-ai-engine 쪽 "동양생명" 관련 내용은 실제 업무 맥락이라 그대로 둠 — 이름 변경은
  `/app/testApp` 스캐폴드 프로젝트에만 적용된 것.)
- **추가 보안 스캔에서 2차 유출 발견 및 제거**: 1차 정리에서 놓친 것들을 재스캔으로 찾아 제거했다.
  - `dev_globals.xml`/`real_globals.xml` — 원본 `globals.xml`과 동일한 KB라이프파트너스 운영/개발 Oracle+MariaDB
    접속정보(재정리 과정에서 파일 이동 순서 버그로 삭제가 누락됐던 것) → 삭제
  - `WEB-INF/lib/ubiserver.xml` — UbiServer(전자문서뷰어) 설정 파일에 **평문** Oracle DB 계정/비밀번호와
    사내 IP가 그대로 들어있었음(암호화조차 안 된 상태) → UbiServer 관련 기능(`system.ubi` 패키지·`ubi4` 뷰는 이미
    삭제됨) 전체와 함께 파일·jar·web.xml 서블릿 등록·pom.xml 의존성까지 모두 제거
  - `CommController.login()`/`redirectERP.jsp`에 하드코딩된 `https://erp.kblifepartners.co.kr` 폴백 URL과
    사내 공인IP(`106.245.252.82`) 기반 접근제한 로직 → 로컬 테스트에서 로그인 화면 자체가 안 뜨는 원인이기도 했어서
    제거(항상 로그인 화면 표시하도록 단순화)
  - 로그인 화면 `<title>`에 있던 "KB 라이프파트너스 수수료시스템" 문구 → "testApp"으로 교체
  - `git add`는 했지만 **`git commit`은 이 정리가 끝난 뒤에 했으므로, 유출된 정보가 git 히스토리에 남은 적은 없음**
- **매퍼 XML의 Oracle→MySQL 변환 완료**: `springsecurity-mapper.xml`(Oracle 전용 로그인 경로 3개 삭제, ERP
  경로만 유지 + `ORACLE_NVL`→`IFNULL`), `commoncode-mapper.xml`, `member-mapper.xml`(파라미터 괄호 짝이 안
  맞던 원본 버그도 같이 수정), `login-mapper.xml`(조직도 조인 제거, Oracle 시퀀스→AUTO_INCREMENT),
  `logs-mapper.xml`(조직도 조인·Oracle 시퀀스 제거), `file-mapper.xml`(Oracle 시퀀스 제거), `erp-mapper.xml`
  (`ORACLE_NVL` 1건). `comm-mapper.xml`의 죽은 Oracle 전용 프래그먼트(`menuEmpCdRole`/`menuScdRole`/`OrgTree`/
  `RolesHierarchyTree`/`OrgMonthTree`, 어디서도 안 쓰임)는 삭제하고, 실제 쓰이는 `PagingStart`/`PagingEnd`
  (Oracle ROWNUM 3중 서브쿼리 → MySQL `COUNT(*) OVER()` + `LIMIT`)와 재귀 CTE `ErpRolesHierarchyTree`
  (원래도 MySQL 문법이었음, `role_path` 컬럼 폭만 넓힘 - 자기참조 데이터가 있으면 타입 추론 때문에 잘림)만 고쳤다.
  없는 Oracle 저장 프로시저(`prc_newmember`) 호출도 `MemberService.insertMember()`에서 제거.
- **로컬 MySQL 스키마·데이터**: `db/schema.sql`(테이블 15개)·`db/seed.sql` 작성, root 계정으로 `testApp` DB와
  `testapp` 전용 계정 생성 후 적재 완료. 로그인 계정 **`admin` / `test1234!`** (Spring Security
  `StandardPasswordEncoder`로 인코딩).
- **JDK21 컴파일·실행 이슈 해결**: `javax.annotation-api`(JDK11+에서 빠짐), `javax.xml.bind:jaxb-api`
  (Spring Security `StandardPasswordEncoder`가 내부적으로 쓰는 `DatatypeConverter`가 JDK11+에서 빠짐) 추가.
  `sqlSession`/`sqlSessionAnybiz` 두 SqlSessionTemplate이 이제 같은 데이터소스를 쓰다 보니 ExecutorType이
  서로 다르면(원본은 BATCH/기본 혼용) 같은 트랜잭션 안에서 충돌 나는 것도 발견해 통일함.
- **로컬 실행 방식 확정**: `tomcat7-maven-plugin`(embedded)은 Maven 자체 classloader와 `javax.servlet` 충돌로
  실패 → **독립 Tomcat 9**(`/opt/tomcat9`, 포트 7080, `mvn package` 결과물을 `ROOT.war`로 배포)로 전환.
  `bin/startApp.sh`/`bin/stopApp.sh`/`bin/statusApp.sh` 작성 완료(dstone-batch/batchadmin과 같은
  "systemd 없이 스크립트" 방식). Jenkins(8080)와 포트 안 겹치게 7080 사용.
- **✅ 엔드투엔드 검증 완료**: `bin/startApp.sh` → 로그인(`admin`/`test1234!`) → 302 리다이렉트로 메인 진입 →
  공통코드관리·회원사관리 AJAX 조회 둘 다 시드 데이터를 정상적으로 반환하는 것까지 curl로 직접 확인함.
- **git**: GitHub `git@github.com:YongSoekChoeng/testApp.git`(브랜치 `main`)에 push 완료.
- **✅ Jenkins 파이프라인 완료 (12절 참고)**: `Jenkinsfile` 작성, Jenkins Job(`testApp`) 생성, GitHub SSH
  자격증명 등록까지 마치고 **빌드→배포→기동→헬스체크까지 전부 그린으로 확인**. 다만 "Jenkins가 띄운 데몬
  프로세스가 빌드 종료 직후 죽는" 문제를 잡는 데 시간이 많이 들었다 — 원인과 최종 해법은 12절에 기록.

**진짜 남은 것**:
1. ~~JIRA 로컬 설치~~ → **철회 (11절 참고)**
2. ~~Jenkins 로컬 배포 파이프라인~~ → **완료 (12절 참고)**
3. 본 문서 1~7절의 dstone-ai-engine `dongyang-sdlc` 워크플로우 실제 구현 착수 — **1단계(접수) 설계를
   11절 기준으로 다시 잡아야 함** (JIRA 웹훅 대신 dstone-boot "Workflow 테스트" 화면에서 직접 입력)

---

## 11. JIRA 도입 철회 (2026-09-22)

9절 가이드대로 `/app/jira`에 Docker Compose(Jira Software Data Center 평가판 + Postgres)로 로컬 JIRA를
띄우는 데까지는 성공했으나(포트 8090, 설치 마법사까지 정상 접속), **라이선스 발급 단계에서 막혔다**:

- Atlassian이 **2026-03-30부로 Data Center 제품의 셀프서비스 평가판 라이선스 발급을 완전히 중단**했다
  (my.atlassian.com에 Server ID로 즉시 발급받던 기존 경로가 사라짐). 기존 Data Center 고객만 영업팀에 별도
  문의해서 trial을 받을 수 있는데, 우리는 해당하지 않는다.
- 대안(Jira Cloud 무료 플랜 - 로컬 아님, 오픈소스 대체제 - OpenProject/Redmine/GitLab CE 등)을 검토했으나,
  **사용자 결정: JIRA 자체를 이번 스코프에서 뺀다.**

**조치 완료**: `/app/jira`의 docker-compose 스택(컨테이너·볼륨·네트워크) 전부 내리고 이미지까지 삭제, 디렉토리
자체도 삭제함. dstone-ai-engine 쪽에는 애초에 아무 코드도 안 만들었으므로(계획 단계였음) 되돌릴 것 없음.

**영향 — 1단계(현업요청사항 접수) 재설계 필요**: 원래 계획(2절)은 "JIRA Automation 웹훅이
`POST /api/ai/workflow/dongyang-sdlc/submit`을 호출"하는 것을 전제로 `tools.jira.JiraTool`
(`getIssue`/`addComment`/`transitionIssue`)을 만들 계획이었다. JIRA를 빼면:

- `JiraTool`은 만들 필요 없음 — 2절의 "신규 Tool: JIRA/Jenkins 연동" 중 JIRA 부분은 통째로 제외
- 1단계 접수는 **사람이 직접 요청사항 텍스트를 입력해서 Workflow를 시작**하는 방식으로 단순화 —
  dstone-boot의 "Workflow 테스트" 화면(`POST /api/ai/workflow/dongyang-sdlc/execute` 또는 `/submit`,
  `message` 필드에 요청사항 원문)이 그대로 접수 창구 역할을 한다. 별도 웹훅/외부 연동 코드가 필요 없어져
  오히려 1단계 구현이 더 간단해짐.
- 각 승인 게이트 결과를 "JIRA에 코멘트/상태 전이로 남긴다"던 부분(2절 `addComment`/`transitionIssue`)도
  제외 — 대신 기존 `WorkFlowExecutionController`(`GET /executions?status=WAITING_APPROVAL`,
  `POST /executions/{id}/decision`)와 dstone-boot의 "Workflow 실행 관리" 화면이 그대로 승인 큐/이력
  역할을 한다(원래도 이 경로가 1차 승인 UI였고, JIRA 코멘트는 부가적인 알림 채널이었을 뿐이었음).
- 결과적으로 3절 다이어그램의 `intake (TOOL: jiraGetIssue)` 스텝은 삭제하고, `analyze` 스텝이 바로
  Workflow 입력(`message`)을 받아 시작하는 구조로 단순화된다.

---

## 12. Jenkins 파이프라인 구축 (2026-09-22)

### 구성
- `Jenkinsfile`(저장소 루트): Checkout(Jenkins 자체 워크스페이스) → Build(`mvn clean package`, fail-fast
  검증용) → Sync to Deploy Dir(`rsync`로 `/app/testApp`에 반영) → Stop Existing Process →
  Deploy(`bin/startApp.sh`) → Health Check(`bin/statusApp.sh`).
- Jenkins Job `testApp`: Pipeline 타입, "Pipeline script from SCM"으로 GitHub 저장소를 직접 체크아웃
  (메모리 `feedback_real-git-checkout-not-local-bypass` 원칙대로 `file://` 로컬 경로 우회 없이 실제
  GitHub 원격 사용). GitHub SSH 자격증명(jysn007의 기존 키 재사용)을 Jenkins Credentials Store에 등록.
- REST API(`jysn007` 계정 + CSRF crumb)로 Job 생성·빌드 트리거·로그 조회까지 전부 자동화해서 반복
  디버깅함(웹 UI 조작 없이 진행).

### 겪은 문제와 최종 해법 (순서대로)
로컬 인프라 특유의 문제들이 겹겹이 있었다. 나중에 비슷한 걸 또 만들 때 참고할 것:

1. **`git fetch` 시 `Host key verification failed`** — `jenkins` 시스템 계정의 `~/.ssh/known_hosts`에
   github.com 호스트 키가 없었음 → `ssh-keyscan`으로 받아 등록.
2. **`rsync` 권한 오류(`chgrp`/시간/권한 설정 실패)** — `jenkins` 계정이 `/app/testApp`(jysn007 소유)에
   쓰기는 되지만(그룹 권한) 소유권·타임스탬프·모드 변경은 안 됨(리눅스에서 그건 소유자만 가능, ACL로도
   못 풂) → `rsync --no-perms --no-owner --no-group --omit-dir-times`로 내용만 동기화.
   `/app/testApp`, `/opt/tomcat9` 둘 다 `jenkins`를 `jysn007` 그룹에 넣고(`usermod -aG`) ACL
   (`setfacl -R -d -m u:jysn007:rwX,u:jenkins:rwX`)까지 걸어 두 계정이 서로 자유롭게 쓰게 했다.
3. **`mvn package`가 파일 덮어쓰기 실패** — `target/`을 jysn007과 jenkins가 번갈아 빌드하면서, 상대방이
   만든 파일의 메타데이터(mtime 등)를 못 바꿔 실패 → `mvn clean package`로 매번 target 자체를 새로
   만들게 바꿔서 회피.
4. **Tomcat이 빌드 종료 직후(수 초 내) 죽음 — 가장 오래 걸린 문제.** `startup.sh` 자체 백그라운드,
   `nohup setsid ... & disown`, `at(1)/atd` 단독, Jenkins 쿠키 환경변수(`JENKINS_SERVER_COOKIE` 등)
   제거, `loginctl enable-linger jenkins`까지 전부 시도했지만 Jenkins Pipeline의 sh 스텝이 끝날 때마다
   (빌드 전체가 아니라 스텝 단위로) 자신이 띄운 프로세스 트리를 정리하는 동작에서 끝내 벗어나지 못했다.
   **최종 해법**: `jenkins` 계정이 스크립트를 실행 중이면 `jysn007@localhost`로 SSH를 떠서(PAM이 완전히
   새 로그인 세션을 만들어 Jenkins의 추적 메커니즘과 아예 무관해짐) 그 세션 안에서 다시 `at(1)`에
   제출하는 방식으로 Tomcat을 띄운다(SSH 명령 자체가 `catalina.sh run`을 포그라운드로 직접 물고
   있으면 SSH가 안 끝나므로, SSH 너머에서도 꼭 `at`으로 한 번 더 넘겨야 한다). 이를 위해 `jenkins`
   전용 SSH 키(`/var/lib/jenkins/.ssh/localhost_deploy`)를 만들어 `jysn007`의 `authorized_keys`에
   등록했다. `bin/startApp.sh`/`bin/stopApp.sh`는 `whoami`로 실행 계정을 구분해 jenkins일 때만 이
   SSH 경로를 타고, `jysn007`이 직접 실행할 때는 기존 `at(1)` 큐만으로 충분하다(이건 Jenkins 없이도
   원래 잘 동작했음). `bin/statusApp.sh`도 `kill -0`(소유자만 확인 가능) 대신 포트 응답으로 상태를
   판단하도록 바꿨다 — jenkins가 jysn007 소유 PID를 `kill -0`으로 조회하면 권한 오류로 항상 실패하기
   때문.

### 결과
빌드 #21에서 Checkout→Build→Sync→Stop→Deploy→Health Check 전 단계 SUCCESS, 빌드 종료 40초 후에도
Tomcat 생존 확인, 그 상태에서 로그인(`admin`/`test1234!`)까지 302로 정상 동작 확인함.
