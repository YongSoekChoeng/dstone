# localcommandcaveatTh

> 생성일시: 2026-11-26 12:31

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/model</command-name>
            <command-message>model</command-message>
            <command-args></command-args>

---

## 질문

<local-command-stdout>Set model to `Opus 5.5` and saved as your default for new sessions</local-command-stdout>

---

## 질문

<pasted_content id="9c6b">
dstone-ai-engine 의 테스트 방식에 대해서 상의하고자 해.
테스트 관련해서 이미 구현된 내용을 잊어버리고 새고 진행한다는 마음으로 아래의 내용을 참고해서 네 의견을 알려줘.

1. 고객사의 환경은 WAS/DB 가 다양할 수 있음.
2. 실행id별로 독립된 테스트가 가능해야 함.
3. 테스트의 깊이는 헤드리스 로 호출된 결과를 보고 llm이 성공여부를 판단할 수 있어야 함.
4. 진행속도나 리소스비용이 효율적이어야 함.
</pasted_content id="9c6b">

---

## 답변

요구사항 네 가지에 맞춰 처음부터 다시 설계한다는 생각으로 정리했습니다. 한 줄로 요약하면 **"실행은 결정적으로, 판단만 LLM에게"** 맡기자는 것입니다.

---

## 1. 다양한 WAS/DB: WAS마다 코드를 만들지 않고 "대역 WAS"와 계약으로 해결

**WAS**
- 기본 실행 환경은 **표준 서블릿 컨테이너 하나(Tomcat)를 대역으로** 씁니다. 고객사 앱 대부분은 표준 Servlet/JSP 위에서 돕니다. JEUS·WebLogic 전용 기능(JNDI 데이터소스, 벤더 디스크립터, 정적 경로 매핑 같은 것)은 프로젝트 설정에 "대역에서는 이렇게 바꾼다"는 매핑으로 둡니다.
- 대역으로 안 되는 현장을 위해 **외부 모드**를 둡니다. 엔진은 `prepare / start → baseUrl / health / stop` 네 가지 계약만 알고, 구현은 고객사가 준 스크립트(배포·기동 명령)로 채웁니다.
- 대역 환경과 실제 환경이 다른 점은 판정 입력에 꼭 넣습니다. 그래야 LLM이 환경 차이를 FAIL이 아닌 BLOCKED로 분류할 수 있습니다.

**DB**: 실행마다 DB를 복제하는 방식은 Oracle·Tibero 같은 곳에서 비싸고 느립니다. 그래서 **미리 만든 시험 스키마 슬롯 N개를 빌려 쓰는 방식**을 권합니다.
- 슬롯은 기준 데이터를 담은 스키마(또는 계정) N개입니다. 고객 DBA가 한 번 만들어 주면 됩니다.
- 실행 ID가 슬롯 하나를 임대하고, 끝나면 **그 실행이 건드린 테이블만** 기준 상태로 되돌린 뒤 반납합니다(DDL 변경은 슬롯 단위로 다시 만듭니다).
- 동시 실행 수 = 슬롯 수입니다. 그래서 리소스 상한이 자연스럽게 정해집니다.
- PostgreSQL처럼 template 복제가 싼 DB는 같은 계약 안에서 "그때그때 복제" 구현으로 바꿔 끼우면 됩니다.

## 2. 실행 ID별 독립성: 격리 단위를 명시적으로 묶기

실행 ID 하나에 아래 네 가지가 1:1로 붙습니다.

| 자원 | 격리 방법 |
|---|---|
| 소스 | 원본 위에 변경분만 얹은 작업 공간(overlay) |
| 서버 | 전용 포트 또는 전용 context path (`/app-{runId}`) |
| DB | 임대한 슬롯 |
| 증거 | `evidence/{runId}/`: 요청·응답, DOM 요약, 스크린샷, DB 전후 비교, 서버 로그 |

- 원본 DB·원본 사이트에 닿는 접속은 **엔진이 막습니다**(프롬프트로 막지 않습니다). 격리를 보장할 수 없으면 원본으로 대신 돌리지 않고 실패시킵니다.
- 실행이 끝나거나 실패·일시정지되면 서버 종료와 슬롯 반납을 `finally` 한 곳에서 처리합니다.

## 3. 테스트 깊이: LLM이 브라우저를 직접 몰지 않게 하기

여기가 비용과 신뢰도를 가장 크게 좌우합니다. 판정을 세 단계로 나눕니다.

1. **시나리오 작성 (LLM, 한 번만)**: 요구사항의 수용 기준을 기준별 시나리오로 바꿉니다. HTTP 호출 목록, 화면 조작 단계, 확인할 DB 쿼리가 들어갑니다. 사람이 승인하는 설계 단계에 같이 넣으면 좋습니다.
2. **실행 (엔진, 결정적)**: 시나리오를 그대로 헤드리스로 돌리고 증거만 모읍니다. API 기준은 HTTP로 확인하고, 브라우저는 화면 기준에만 씁니다. 화면 증거는 스크린샷 대신 **접근성 트리나 텍스트 요약**으로 남겨 토큰을 줄입니다.
3. **판정 (LLM)**: 기준별로 PASS / FAIL / BLOCKED를 정하되 **증거 ID 인용을 필수로** 합니다. 인용이 없는 PASS는 엔진이 BLOCKED로 내립니다.

추가로 **결정적 사전 판정**을 둡니다. 5xx 응답, 서버 로그의 예외, SQL 오류가 보이면 LLM을 부르지 않고 바로 FAIL로 처리합니다.

LLM이 탐색하듯 브라우저를 모는 방식은 **시나리오 실행이 깨졌을 때(요소를 못 찾는 경우 등)의 보조 수단**으로만 씁니다.

## 4. 속도와 비용: 싼 관문부터, 바뀐 것만 다시

- **단계별 관문**: 정적 검사(컴파일, XML/MyBatis id, JSP 균형) → 기동 → 시나리오 순서로 진행하고, 앞 단계에서 실패하면 뒤로 가지 않습니다.
- **기동 재사용**: 같은 실행 ID 안에서 검증 라운드가 바뀌어도 서버를 유지합니다. JSP·정적 파일 변경은 재기동 없이 반영하고, 클래스 변경일 때만 context를 다시 올립니다.
- **증분 재검증**: 변경 파일 지문과 기준별 관련 파일을 대조합니다. 이전에 PASS였고 관련 파일이 그대로인 기준은 다시 판정하지 않습니다.
- **모델 배분**: 증거가 압축된 판정은 작은 모델로도 충분합니다. 큰 모델은 시나리오 작성과 실패 원인 분석에만 씁니다.
- **재생 가능성**: 시나리오와 증거가 파일로 남으므로, 같은 시나리오를 LLM 없이 다시 돌리는 회귀 테스트로 그대로 쓸 수 있습니다.

---

## 결정이 필요한 것

1. **DB 슬롯 방식을 고객사에 요구할 수 있는지**: 시험 스키마 N개를 DBA에게 받아야 합니다. 어렵다면 차선은 "시험 DB 하나 + 동시 실행 1건 + 실행 후 복원"입니다.
2. **대역 WAS를 기본으로 할지, 외부 모드를 기본으로 할지**: 저는 대역 WAS를 기본으로 두고 외부 모드는 예외로 쓰는 쪽을 권합니다.
3. **시나리오를 사람이 승인할지**: 승인하면 판정 기준이 고정돼 LLM 판정이 흔들리지 않지만, 승인 단계가 하나 늘어납니다.

원하시면 이 안을 지금 구현(`startSandboxServer`, `sm-verifier-agent`의 검증 루프)과 항목별로 비교해서 무엇을 유지하고 무엇을 바꿀지 정리해 드리겠습니다.

---

## 질문

대역was 에 생성/수정된 소스를 적용하는 방식은 무엇인가?

---

## 답변

생성·수정된 소스를 대역 WAS에 적용하는 방식으로는 **"원본은 한 번만 펼쳐 공유하고, 실행 ID마다 변경분만 위에 겹치는"** 구조를 권합니다. 매번 전체를 빌드하거나 복사하지 않는 것이 핵심입니다.

## 1. 기본 구조: 공유 기준본 + 실행별 덧씌움

```
/sandbox/{프로젝트}/base/{리비전}/        ← 원본 앱을 풀어 놓은 것 (읽기 전용, 실행끼리 공유)
/sandbox/{프로젝트}/runs/{runId}/
    overlay/                               ← 이 실행이 바꾼 것만
        WEB-INF/classes/...                ← 바뀐 .java를 컴파일한 결과
        WEB-INF/sqlmap/...                 ← 바뀐 매퍼 XML
        board/list.jsp, js/board.js ...    ← 바뀐 화면·정적 파일
    catalina_base/                         ← 이 실행 전용 Tomcat 인스턴스
        conf/server.xml                    ← 전용 포트
        conf/Catalina/localhost/ROOT.xml   ← 아래 context 설정
```

Tomcat의 **리소스 겹치기(`PreResources`)** 를 씁니다. 파일을 복사하지 않고, 같은 경로라면 overlay 쪽이 먼저 보이게 합니다.

```xml
<Context docBase="/sandbox/p1/base/r123" reloadable="false">
  <Resources>
    <PreResources className="org.apache.catalina.webresources.DirResourceSet"
                  base="/sandbox/p1/runs/{runId}/overlay" webAppMount="/" />
  </Resources>
</Context>
```

- 기준본은 실행끼리 공유하므로 디스크와 준비 시간이 거의 들지 않습니다.
- Tomcat 설치본(`CATALINA_HOME`)은 하나만 두고, 실행마다 `CATALINA_BASE`만 따로 둡니다. JVM이 실행마다 분리되므로 static 상태, 포트, 메모리 누수가 서로 섞이지 않습니다. Tomcat 자체 기동은 1~2초라서, 비용은 대부분 앱의 Spring context 기동입니다. 이 비용은 어느 방식이든 같습니다.

## 2. 기준본은 어디서 오나

고객사마다 빌드 방식(Maven, Ant, Eclipse 전용 레거시)이 제각각이라 **빌드 재현에 기대지 않는 편이 좋습니다.**

1. **1순위: 개발·운영 서버에 실제 배포된 WAR나 디렉터리를 받아 풉니다.** 컴파일된 클래스와 lib이 이미 다 들어 있습니다.
2. **2순위: 고객사 빌드 명령을 프로젝트 설정에 등록해 한 번 빌드합니다.**

기준본은 원본 소스 리비전별로 캐시합니다. 원본이 바뀌었을 때만 새로 만듭니다.

## 3. 변경 종류별 적용 방법

| 변경 | 만드는 법 | 반영 방법 |
|---|---|---|
| JSP, JS, CSS, 이미지 | overlay에 그대로 둠 | 즉시 반영 (Jasper `development=true`가 수정 시각을 확인) |
| `.java` | 바뀐 파일만 `javac -cp base/WEB-INF/classes:base/WEB-INF/lib/*` 로 컴파일, 결과는 `overlay/WEB-INF/classes` | context reload |
| MyBatis XML, Spring XML, properties, web.xml | overlay에 둠 | context reload (기동 때 읽기 때문) |
| lib jar 추가 | `overlay/WEB-INF/lib` | context reload |
| DDL, 기준 데이터 | 임대한 DB 슬롯에 실행 | 앱 재시작 불필요 |

- **reload는 JVM 재시작이 아닙니다.** Tomcat이 해당 context만 내렸다 올립니다(manager API나 JMX로 호출). 같은 실행 ID 안에서 검증 라운드가 여러 번 돌아도 JVM은 그대로 둡니다.
- 바뀐 파일 종류만 보고 "즉시 반영"인지 "reload"인지를 엔진이 정합니다. JSP만 바뀐 라운드는 기동 비용이 0입니다.

## 4. 조심할 곳 세 가지

**(1) 삭제된 파일**: 덧씌움으로는 기준본의 파일을 가릴 수 없습니다. 삭제가 있는 실행만 기준본을 **하드링크로 복제**(`cp -al`, 디스크 거의 안 씀)한 뒤 거기서 지우고 docBase로 씁니다. 이때 파일을 제자리에서 고치면 기준본까지 바뀌므로, 쓰기는 항상 "새 파일 쓰고 rename" 방식만 허용합니다.

**(2) 부분 컴파일의 함정**: 바뀐 클래스만 컴파일하면 다음 두 경우에 깨집니다.
- `static final` 상수를 바꿨는데, 그 값을 인라인한 다른 클래스는 옛 값을 그대로 씀
- public 메서드 시그니처를 바꿨는데, 호출하는 쪽을 다시 컴파일하지 않아 런타임에 `NoSuchMethodError` 발생

대응은 두 가지입니다. 바뀐 클래스의 public 시그니처와 상수를 비교해 달라졌으면 **역참조 클래스까지 다시 컴파일**합니다(dstone-knowledge의 호출 관계를 쓰면 됩니다). 판단이 어려우면 전체 소스를 컴파일하는 쪽으로 물러섭니다.

**(3) 컴파일 환경 차이**: 원본이 annotation processor(Lombok, QueryDSL 등)나 특정 `-source/-target`을 쓰면 javac 옵션을 똑같이 맞춰야 합니다. 이 옵션은 기준본을 만들 때 한 번 알아내 프로젝트 설정에 저장합니다.

## 5. 대역이 아닌 외부 WAS를 쓰는 경우

계약(`prepare/start/health/stop`)은 같고, `prepare`가 하는 일만 다릅니다. 기준본에 overlay를 합친 결과(디렉터리나 WAR)를 만들어 고객사 배포 스크립트에 넘깁니다. 외부 WAS는 겹치기 기능을 기대할 수 없으므로 합친 결과물을 넘기는 것이 유일하게 공통으로 통하는 방법입니다.

---

정리하면 이렇습니다.
- 기준본은 배포본을 풀어서 공유합니다.
- 실행마다 overlay와 `CATALINA_BASE`를 따로 둡니다.
- `.java`는 바뀐 것만 컴파일하고, 시그니처나 상수가 바뀌면 역참조까지 다시 컴파일합니다.
- 반영은 즉시 반영과 context reload로 나누고, JVM 재시작은 실행 ID당 한 번만 합니다.

다음으로 DB 슬롯의 "건드린 테이블만 되돌리기"를 벤더별로 어떻게 구현할지도 이어서 정리할까요?

---

## 질문

응

---

## 답변

DB 슬롯을 되돌리는 방식은 **"무엇을 건드렸는지는 엔진이 직접 기록하고, 되돌리기는 같은 인스턴스에 둔 기준 스키마에서 테이블 단위로 다시 채우는"** 구조를 권합니다. 벤더마다 다른 부분은 작은 방언(dialect) 몇 가지로 모읍니다.

## 1. 전체 구성

```
같은 DB 인스턴스 안
├── GOLDEN_P1          ← 기준 데이터 (읽기 전용, 프로젝트당 1개)
├── SLOT_P1_01         ← 실행이 빌려 쓰는 시험 스키마
├── SLOT_P1_02
└── SLOT_P1_03
```

- 슬롯 계정에는 자기 스키마의 DDL 권한과 GOLDEN에 대한 SELECT 권한만 줍니다.
- 기준 데이터는 원본 개발 DB에서 **일부만, 개인정보는 가려서** 만듭니다. 슬롯 데이터가 작아야 되돌리기가 빠르기 때문입니다. 이 작업은 프로젝트당 한 번, 고객 DBA와 같이 합니다.
- PostgreSQL은 데이터베이스끼리 바로 조회할 수 없으므로, 슬롯을 database가 아닌 **schema**로 만듭니다.

## 2. 건드린 테이블을 아는 방법

벤더 통계(Oracle `DBA_TAB_MODIFICATIONS`, MySQL `UPDATE_TIME` 등)는 늦게 반영되거나 재시작하면 사라집니다. 그래서 주 수단으로는 쓰지 않습니다.

**주 수단: 대역 WAS의 JDBC 프록시**
- 대역 WAS의 데이터소스(JNDI)는 엔진이 정의합니다. 그러니 실제 드라이버를 감싼 엔진 프록시 드라이버를 끼울 수 있습니다.
- 프록시는 INSERT/UPDATE/DELETE/MERGE/TRUNCATE의 대상 테이블을 JSQLParser로 뽑아 `runId`별로 기록합니다. 파싱에 실패하면 "알 수 없음"으로 표시하고, 반납할 때 전체 초기화로 넘깁니다.
- 같은 프록시가 **격리 경비** 역할도 합니다. `PRD.TB_USER`처럼 다른 스키마를 붙인 참조나, 원본을 가리키는 synonym·dblink 접근이 보이면 실행을 거부합니다. 슬롯을 빌려 놓고 실제로는 원본에 쓰는 사고를 막는 장치입니다.

**보조 수단**
- 엔진이 직접 실행한 DDL·기준 데이터 기록과 검증 Tool이 실행한 쿼리도 같은 기록에 넣습니다.
- 반납할 때 **카탈로그 비교**(객체 목록, 컬럼 시그니처를 GOLDEN과 대조)를 한 번 더 합니다. 기록에서 빠진 DDL을 잡는 안전망입니다.

## 3. 슬롯 상태와 되돌리기

```
CLEAN ──임대──▶ LEASED(runId) ──반납──▶ RESETTING ──▶ CLEAN
                                   │
                                   └─ DDL 있음 / 알 수 없음 / 검증 실패 ──▶ DIRTY ──▶ REBUILDING ──▶ CLEAN
```

**가벼운 초기화**(DML만 있었을 때): 기록된 테이블마다 다음을 실행합니다.
1. FK·트리거를 끕니다. 감사(audit) 트리거가 되돌리기 중에 실행되지 않게 하려는 것입니다.
2. `TRUNCATE` 후 `INSERT INTO slot.t SELECT * FROM golden.t`
3. identity·auto-increment 값을 맞춥니다.
4. FK·트리거를 다시 켭니다.
5. 행 수를 GOLDEN과 비교합니다. 다르면 DIRTY로 보냅니다.

**전체 재구축**(DDL이 있었거나 판단할 수 없을 때): 슬롯을 지우고 GOLDEN으로 다시 만듭니다. 시간이 걸리므로 **백그라운드**에서 하고, 그동안 새 실행에는 다른 CLEAN 슬롯을 빌려줍니다.

- 시퀀스는 **뒤로 되돌리지 않습니다.** 값이 커지기만 해서는 문제가 없고, 오히려 GOLDEN 데이터와 키가 겹치지 않게 해 줍니다. 확인하는 것은 "GOLDEN의 최대 키보다 크다"는 조건 하나입니다.
- 임대·반납은 엔진 DB에서 조건부 UPDATE로 하고, heartbeat가 끊긴 임대는 고아로 보고 회수해 DIRTY로 보냅니다.

## 4. 벤더별 차이(방언이 맡을 일)

| 할 일 | Oracle / Tibero | MySQL / MariaDB | PostgreSQL | SQL Server |
|---|---|---|---|---|
| FK 끄기 | `ALTER TABLE … DISABLE CONSTRAINT` (FK마다) | `SET FOREIGN_KEY_CHECKS=0` (세션) | FK를 `DEFERRABLE`로 만들어 `SET CONSTRAINTS ALL DEFERRED`, 또는 FK 순서대로 처리 | `ALTER TABLE … NOCHECK CONSTRAINT ALL` |
| 트리거 끄기 | `ALTER TABLE … DISABLE ALL TRIGGERS` | 끌 수 없음 → 트리거를 지웠다가 다시 만들거나, 트리거 안에서 세션 변수 확인 | `ALTER TABLE … DISABLE TRIGGER USER` | `DISABLE TRIGGER ALL ON …` |
| 자동 증가 맞추기 | 시퀀스는 그대로 둠 (12c identity는 `START WITH LIMIT VALUE`) | TRUNCATE가 1로 되돌림 → `ALTER TABLE … AUTO_INCREMENT = n` 필수 | `setval()`로 GOLDEN의 최댓값 이상 | `DBCC CHECKIDENT … RESEED` |
| 카탈로그 비교 | `ALL_OBJECTS`, `ALL_TAB_COLUMNS` | `information_schema` | `information_schema`, `pg_catalog` | `sys.objects`, `sys.columns` |
| 큰 테이블 대안 | `FLASHBACK TABLE … TO SCN` (row movement 필요) | 없음 | 없음 | 스냅숏 DB (전체 단위) |

방언 인터페이스는 메서드 6개 정도면 충분합니다: `disableConstraints`, `disableTriggers`, `copyFromGolden`, `syncIdentity`, `catalogSignature`, `rebuildSlot`. 엔진은 벤더를 모르고, 프로젝트 설정의 DB 종류로 방언을 고릅니다.

## 5. 한계와 대응

- **큰 테이블**: 수백만 건짜리는 TRUNCATE+INSERT도 느립니다. 처음부터 GOLDEN에 일부만 담는 것이 기본 대책입니다. 꼭 전체가 필요하면 그 테이블만 Oracle Flashback을 쓰거나, 아예 "읽기 전용 테이블"로 지정해 프록시가 쓰기를 거부하게 합니다.
- **MySQL 트리거**: 끄는 기능이 없어서 가장 번거롭습니다. 트리거가 있는 테이블은 전체 재구축 쪽으로 보내는 것이 단순합니다.
- **앱이 DB 밖에 남기는 상태**(파일 업로드, 캐시, 외부 연동)는 슬롯으로 되돌릴 수 없습니다. 실행별 작업 공간과 대역 설정(업로드 경로를 실행 폴더로 돌리기, 외부 연동은 막기)에서 따로 다룹니다.

---

정리하면, 기록은 프록시에서 하고 되돌리기는 GOLDEN에서 테이블 단위로 합니다. DDL이 있었거나 판단이 안 되면 백그라운드에서 슬롯 전체를 다시 만들고, 벤더 차이는 방언 6개 메서드 안에 가둡니다.

다음으로 JDBC 프록시를 대역 WAS에 끼우는 방법을 정리할까요? JNDI 매핑, 그리고 앱이 데이터소스를 직접 만드는 레거시 앱에서 어떻게 끼울지가 주제입니다. 아니면 지금까지의 안을 현재 구현과 항목별로 비교해 드릴까요?
