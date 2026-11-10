# localcommandcaveatTh

> 생성일시: 2026-11-10 14:54

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="d0fe">
/app 디렉토리아래에 리치클라이언트 기반(넥사크로,웹스퀘어) 어플을 하나씩 만들어줘.
형식은 아래에 기술한 기존 어플을 참고해서 틀을 만들고, 내부적인 기능은 testApp 와 유사한 정도면 될 것 같아.
용도는 dstone-ai-engine 의 Workflow 를 이용해서 리치클라이언트 기반(넥사크로,웹스퀘어) 어플을 개발할 수 있는지 테스트하기 위함이야.
곧장 돌아갈 수 있는 수준이었으면 좋겠어.

1. 넥사크로기반
/app/testNexacroApp
    - 형식 : /app/hen 참고
    - 내용(기능) : /app/testApp 참고
2. 웹스퀘어기반
/app/testWebSqureApp
    - 형식 : /app/HI-Japan 참고
    - 내용(기능) : /app/testApp 참고
</pasted_content id="d0fe">

---

## 답변

먼저 참고할 세 어플(`/app/hen`, `/app/HI-Japan`, `/app/testApp`)의 구조부터 살펴보겠습니다.

---

## 답변

웹스퀘어 엔진이 이 라이선스로 localhost에서 실제 동작하는지 작은 시험 웹앱으로 먼저 확인하겠습니다.

---

## 답변

Spike: minimal WebSquare webapp on a separate Tomcat base.

---

## 답변

HDMF 쪽 라이선스는 만료된 데모 라이선스라 엔진이 거부했습니다. Windows D: 드라이브에 `WEBSQUARE_DEV_PACK_SP5_edu`가 있어 그쪽을 확인하고 있습니다.

---

## 답변

교육용 개발팩(SP5, 2024년판 엔진 + 라이선스)이 있어 이걸로 엔진 기동을 시험하는 중입니다.

---

## 답변

Spike with SP5 engine + edu license.

---

## 답변

Now a typical hen business screen and its Java backend.

---

## 답변

생성 도구(`nexacrodeploy.exe`)가 WSL에서 실행되는 것까지 확인했습니다. DB 접속 정보와 testApp의 기능 코드를 마저 확인하겠습니다.

---

## 답변

MySQL 관리자 접속도 확인했습니다. 이제 HI-Japan의 TranID 호출 규약(화면 → `HiJS.svr.doRequestAjax` → `ZZ.json` → MainService/SubService/DAO)을 읽고 있습니다.

---

## 답변

Now the HI-Japan server side: ServiceConfig, M/S/D, DBIO XML.

---

## 답변

이제 testNexacroApp부터 만들겠습니다. 먼저 넥사크로 프로젝트 골격(xprj/xadl/환경 파일)을 만들고 생성 도구가 통과하는지 확인합니다.

---

## 답변

Frame 화면(로그인·상단·좌측 메뉴·홈)을 작성하고 있습니다.

---

## 답변

Frame 화면이 준비됐으니, 업무 화면을 쓰기 전에 생성 도구가 이 프로젝트를 제대로 빌드하는지 먼저 시험합니다.

---

## 답변

생성 도구가 정상 동작합니다(44개 성공, 실패 0). 생성 결과에 공통 라이브러리가 제대로 들어갔는지 확인합니다.

---

## 답변

생성 확인이 끝났으니 업무 화면 4개를 작성합니다.

---

## 답변

모든 화면이 오류 없이 생성됐습니다(48개 성공). 이제 서버(Java) 쪽을 작성합니다. 먼저 의존성부터 확인합니다.

---

## 답변

xapi API 확인을 마쳤습니다. 이제 hen과 같은 모양의 서버 코드를 작성합니다(넥사크로 어댑터 → 공통 → 업무 순).

---

## 답변

Now the `com.frameq` system/common layer.

---

## 답변

Now login and business controllers/services.

---

## 답변

이제 MyBatis 매퍼 SQL을 작성합니다.

---

## 답변

Spring 설정, web.xml, 로그 설정을 작성합니다.

---

## 답변

설정까지 작성했으니 빌드해 봅니다.

---

## 답변

`javaee-api`(서블릿 API가 들어 있는 jar)가 war에 딸려 들어와 Tomcat과 충돌할 수 있어 제외합니다.

---

## 답변

DB 스크립트(생성, 스키마, 시험 데이터)를 작성합니다.

---

## 답변

DB가 준비됐습니다. 이제 기동/중지/상태 스크립트를 만듭니다(전용 Tomcat 인스턴스, 포트 7090).

---

## 답변

기동됐습니다. 서버 로그를 확인하고, 넥사크로가 보내는 것과 같은 XML 요청을 curl로 보내 로그인부터 시험합니다.

---

## 답변

전자정부 `EgovAbstractServiceImpl`이 필요로 하는 `leaveaTrace` 빈이 빠졌습니다. hen의 설정을 확인해 같은 방식으로 넣겠습니다.

---

## 답변

로그인·메뉴가 정상 응답합니다. 저장(행 추가·수정·삭제), 세션 만료, 잘못된 비밀번호까지 시험합니다.

---

## 답변

헤드리스 Chromium을 찾았습니다. 실제 브라우저로 넥사크로 화면을 띄워 로그인 → 메뉴 → 각 화면 열기를 자동으로 시험합니다.

---

## 답변

All four screens load data in a real browser. Let me look at the screenshots.

---

## 답변

화면에서의 저장·삭제·로그아웃까지 브라우저에서 정상 동작합니다. 시험 데이터를 지우고 testNexacroApp README를 작성합니다.

---

## 답변

testNexacroApp은 완료했습니다. 이제 testWebSqureApp(HI-Japan 틀)을 만듭니다. 먼저 SP5 교육팩의 웹스퀘어 클라이언트 설정 파일을 확인합니다.

---

## 답변

웹스퀘어 클라이언트 폴더를 가져와 이 앱에 맞게 고치고, HI-Japan 모양의 미니 프레임워크(TranID 디스패처, MainService/SubService/DAO, DBIO XML)를 작성합니다.

---

## 답변

이제 프레임워크 Java를 작성합니다(HI-Japan의 `jef.*`/`hi.framework.*` 자리를 `tw.framework.*` 소스로 둡니다).

---

## 답변

Now the DAO layer (DBIO XML + Velocity + named params).

---

## 답변

Now TranID config loading and the servlets.

---

## 답변

Now mappers (MAP), util, and the login/menu (JZCI) services.

---

## 답변

Now 공통코드 (JZCD1010) M/S/D.

---

## 답변

중복 확인 쿼리가 건수를 `SORT_NO`에 빌려 담는 건 헷갈리니, VO에 `cnt` 필드를 두도록 고칩니다.

---

## 답변

Now 회원사 (JZMB1010), 로그인이력 (JZLG1010), 게시판 (JEBD1010).

---

## 답변

Now the DBIO query XMLs (Velocity 조건 + `:name:` 자리표시).

---

## 답변

Now TranID 등록 XML, web.xml, DB 설정, 로그 설정.

---

## 답변

Now the pom (builds the `was/` exploded layout as a WAR).

---

## 답변

빌드됩니다. DB 스크립트와 기동 스크립트를 testNexacroApp 것을 바탕으로 만듭니다(포트 7095, `WEBSQUARE_HOME` 전달).

---

## 답변

메뉴 경로 치환이 안 됐습니다. 직접 고쳐서 다시 넣습니다.

---

## 답변

이제 화면 쪽(로그인 JSP, 공통 JS, CSS, 웹스퀘어 화면 XML)을 작성합니다.

---

## 답변

Now the WebSquare screens: main frame first.

---

## 답변

화면 XML이 문법상 올바른지 확인하고, 앱을 띄워 로그인과 TranID 전체를 curl로 시험합니다.

---

## 답변

앱이 떠서 TranID 13개·쿼리 24개를 읽었고, 예상대로 웹스퀘어 엔진만 라이선스 오류를 냈습니다. 로그인과 모든 TranID를 curl로 시험합니다.

---

## 답변

모든 TranID가 정상입니다. 트랜잭션 롤백(그룹은 넣고 코드에서 오류가 나면 그룹도 취소되는지)도 확인합니다.

---

## 답변

롤백도 정상이고, 웹스퀘어 엔진은 라이선스가 없어 빈 응답(0B)을 냅니다. 마지막으로 testWebSqureApp README와 라이선스 안내를 작성합니다.

---

## 답변

README에 "SP5 API 문서를 따랐다"고 썼는데 실제로 확인하진 않았으니, 엔진 zip 안의 API 문서로 화면에서 쓴 함수 이름이 맞는지 확인하겠습니다.

---

## 답변

화면에서 쓴 API(`setCellReadOnly`, `getFocusedRowIndex`, `getModifiedJSON`, `setSrc` 등)가 모두 SP5 API 문서에 있습니다. 이벤트 인자 순서만 확인합니다.

---

## 답변

마지막으로 두 앱 상태와 시험용 임시 Tomcat이 남아 있지 않은지 확인합니다.

---

## 답변

두 앱을 만들었고 지금 둘 다 떠 있습니다. 넥사크로 앱은 브라우저에서 끝까지 동작하는 것을 확인했습니다. 웹스퀘어 앱은 서버와 로그인은 동작하지만, 유효한 웹스퀘어 라이선스가 없어 화면이 그려지지 않습니다.

| | testNexacroApp | testWebSqureApp |
|---|---|---|
| 주소 | http://localhost:7090/ | http://localhost:7095/ |
| 따른 틀 | hen: eGov 4.2(Spring 5.3) + MyBatis, `com.frameq`, `*.do` 컨트롤러, `FrameqMapper` | HI-Japan: `was/` 폴더를 그대로 war로 묶음, `ui/jz/JZCD1010G.xml`, TranID(`/ZZ.json`), M/S/D/VO/MAP, DBIO XML(Velocity) |
| 기능 | 로그인/로그아웃, 메뉴, 공통코드관리, 회원사관리, 로그인이력, 샘플게시판 | 같음 |
| DB | MySQL `testNexacroApp` | MySQL `testWebSqureApp` |
| 동작 범위 | 화면까지 전부 | 로그인 JSP, TranID 서버, DB까지. 웹스퀘어 화면은 안 그려짐 |

로그인은 둘 다 회사코드 `PRD`, 아이디 `admin`, 비밀번호 `test1234!`입니다. 실행은 각 폴더에서 `bin/startApp.sh`, `bin/stopApp.sh`, `bin/statusApp.sh`로 합니다. 각 앱이 전용 Tomcat을 따로 띄우므로 기존 testApp(7080)과 부딪히지 않습니다.

**확인한 것**
- **넥사크로**:
  - Windows에 설치된 Nexacro Studio의 생성기를 WSL에서 부르는 `bin/generateNexacro.sh`를 만들었습니다. 생성은 48건 모두 성공했습니다.
  - 헤드리스 Chromium으로 로그인 → 메뉴 → 네 화면 열기 → 게시글·코드 저장 → 로그아웃까지 실제로 동작했습니다.
- **웹스퀘어**: curl로 TranID 13개를 모두 호출해 봤습니다. 조회·저장·삭제, 오류 메시지, 세션 만료, 트랜잭션 롤백이 정상입니다.
- **웹스퀘어 화면은 실제로 띄워 보지 못했습니다.** 화면 XML은 문법이 맞는지와, 쓴 API 이름이 SP5 API 문서에 있는지만 확인했습니다.

**알아두실 점**
- **웹스퀘어 라이선스**: 이 PC에서 찾은 라이선스 세 개(교육팩 두 개, HDMF 하나)가 모두 만료된 데모판이라 엔진이 거부했습니다. 유효한 라이선스를 `/app/testWebSqureApp/websquare_home/license/license`에 넣고 앱을 다시 띄우면 화면이 그려질 것입니다.
- **넥사크로 라이선스**: `D:\Swap\Utils\Nexacro\N\라이센스`의 체험판을 넣었습니다. 2026-11-01부터 2개월짜리라 2027-01-01쯤 끝납니다.
- **프레임워크 코드**: hen과 HI-Japan의 프레임워크는 소스가 없는 벤더 jar라서 같은 모양의 클래스를 소스로 새로 작성했습니다.
  - 넥사크로: `egovframework.rte.cmmn.ria.nexacroplatform.*`
  - 웹스퀘어: `tw.framework.*`
- **이름**: 웹스퀘어 앱의 샘플게시판은 HI-Japan에 없는 업무라 업무 코드를 `JE`(예제)로 정했습니다.

**앱 폴더 밖에서 한 일**
- MySQL root로 DB 두 개와 계정 `tnxapp`, `twqapp`를 만들었습니다. 비밀번호는 testApp처럼 설정 파일에 평문으로 들어 있습니다(로컬 전용).
- D: 드라이브에서 넥사크로 라이선스와 웹스퀘어 엔진 jar를 복사했습니다. 라이선스 파일은 `.gitignore`에 넣었습니다.
- git 저장소는 만들지 않았습니다.

화면을 고치거나 새 화면을 붙이는 방법, 업무 하나가 흐르는 경로는 각 앱의 `README.md`에 정리했습니다.

---

## 질문

여기서의 웨스퀘어 버젼이 어떻게 되?

---

## 답변

testWebSqureApp에 넣은 웹스퀘어는 **WebSquare5 SP5**입니다.

| 항목 | 값 |
|---|---|
| 엔진 jar | `was/WEB-INF/lib/websquare-5.0_5.5170B.20240319.123453_1.5.jar` |
| 판 | 5.0 SP5, 빌드 **5170** (2024-03-19 빌드) |
| 끝의 `_1.5` | 엔진 jar 종류 이름입니다. 교육팩에는 이것 말고 POI 4용(`_poi4_1.8`)이 따로 있는데, 이 앱은 POI 3.17을 쓰는 `_1.5` 쪽을 골랐습니다 |
| 클라이언트 설정 (`was/websquare/`) | 같은 SP5 교육팩(`WEBSQUARE_DEV_PACK_SP5_edu`)의 `WRE/WebContent/websquare`에서 가져왔습니다 |

참고할 판 차이도 있습니다.
- **HI-Japan은 SP2를 씁니다** (`websquare_5.0_1.2249B.20160826...`, 2016년 빌드). 화면 XML의 모양과 TranID 방식은 HI-Japan을 따랐지만, 엔진은 그보다 새 SP5입니다. 한때 SP2 엔진도 시험해 봤는데, 쓸 수 있는 라이선스가 만료된 데모판뿐이라 그만뒀습니다. HI-Japan과 엔진 판까지 맞추려면 SP2 jar와 SP2용 라이선스가 필요합니다.
- 교육팩의 WebSquare Studio에 들어 있는 엔진은 빌드 **5180**(2024-04-22)이라 이 앱의 5170과 조금 다릅니다. 화면에서 쓴 API 이름은 이 5180판 API 문서로 확인했습니다.

---

## 질문

두 서버 내려줘.

---

## 답변

두 서버를 내렸습니다. testNexacroApp(7090)과 testWebSqureApp(7095) 모두 `STOPPED` 상태이고, 두 포트도 비어 있습니다.

다시 띄울 때는 각 폴더에서 `bin/startApp.sh -n`을 실행하면 빌드 없이 바로 기동합니다.

---

## 질문

testWebSqureApp 는 포트를 7090 에서 7070 으로 바꿔줘.

---

## 답변

testWebSqureApp의 포트를 7070으로 바꿨습니다. 말씀하신 이전 포트 7090은 testNexacroApp의 포트이고, testWebSqureApp은 원래 7095였습니다. 그래서 testWebSqureApp만 7095 → 7070으로 바꿨고, testNexacroApp은 7090 그대로입니다.

- 고친 파일은 `bin/startApp.sh`, `bin/statusApp.sh`, `README.md`입니다.
- 7070 포트는 비어 있는 것을 확인했습니다.
- 서버는 내려 둔 상태라 아직 7070으로 띄워 보지는 않았습니다. `bin/startApp.sh -n`을 실행하면 새 포트로 뜹니다.

---

## 질문

dstone_knowledge 데이터 베이스의 테이블별 백업/복원 을 할 경우 테이블별 작업순서가 어떻게 되나?

---

## 답변

실제 DB에서 테이블과 외래키 관계를 조회해 순서를 정하겠습니다.

---

## 답변

외래키는 3개뿐이라, 나머지 테이블은 컬럼으로 이어진 논리적 참조와 시퀀스, 뷰를 확인하겠습니다.

---

## 답변

복원할 때만 순서가 중요합니다. 부모 테이블을 먼저 넣고, 지울 때는 반대로 자식 테이블부터 지우면 됩니다. 백업은 순서보다 **모든 테이블을 같은 시점에 뜨는 것**이 중요합니다.

방금 dstone_knowledge DB를 직접 조회해 보니, 일반 테이블 21개와 뷰 2개(`kg_node`, `kg_edge`)가 있습니다. 실제 외래키는 3개뿐입니다.
- `analysis_revision` → `analysis_project`
- `analysis_job` → `analysis_project`
- `analysis_job` → `analysis_revision`

나머지 테이블은 `revision_id`, `file_id` 같은 컬럼으로만 이어져 있어서 DB가 순서를 강제하지 않습니다. 그래도 데이터가 맞으려면 아래 순서를 지키는 것이 안전합니다.

## 복원 순서 (부모 → 자식)

| 단계 | 테이블 | 근거 |
|---|---|---|
| 1 | `analysis_project` | 최상위 |
| 2 | `analysis_revision` | 외래키 → project (`parent_revision_id`는 자기 참조, 외래키 아님) |
| 3 | `analysis_job` | 외래키 → project, revision |
| 4 | `analysis_revision_pass`, `analysis_metric`, `analysis_error` | `analysis_id`(job), `revision_id` |
| 5 | `analysis_file` | `revision_id` (`carried_from_file_id`는 자기 참조) |
| 6 | `analysis_file_pass` | `file_id` |
| 7 | `analysis_symbol` | `file_id` |
| 8 | `analysis_method`, `analysis_field` | `owner_symbol_id` |
| 9 | `analysis_annotation`, `analysis_endpoint`, `analysis_mapper`, `analysis_reference`, `analysis_relation`, `analysis_config`, `analysis_resource` | symbol, method, file |
| 10 | `rag_document`, `rag_chunk` | revision, file |
| 11 | `rag_embedding` | `content_hash`(chunk, document) |

같은 단계 안에서는 순서가 상관없습니다. 데이터를 지울 때는 11 → 1 순서로 지웁니다. 다만 외래키가 걸린 1~3단계는 `TRUNCATE analysis_job, analysis_revision, analysis_project;`처럼 한 문장으로 지워야 합니다(외래키 삭제 규칙이 NO ACTION입니다).

## 주의할 점

1. **백업은 같은 시점으로 뜹니다.** 테이블마다 `pg_dump -t`를 따로 돌리면 시점이 서로 달라져 `file_id` 같은 연결이 어긋날 수 있습니다. 한 번에 뜨고, 꺼낼 때만 테이블별로 꺼내는 방식을 권합니다.
   ```bash
   pg_dump -h 127.0.0.1 -U dstone_knowledge -d dstone_knowledge -Fd -j 4 -f kg_backup   # 한 시점, 테이블별 파일
   pg_restore -h 127.0.0.1 -U dstone_knowledge -d dstone_knowledge --data-only -t analysis_project kg_backup
   ```
   백업이나 복원 중에 분석이 돌지 않도록 4081 인스턴스는 먼저 내립니다(`dstone-knowledge/bin/stopApp.sh`).

2. **테이블을 DROP하지 말고 TRUNCATE한 뒤 데이터만 넣습니다.** `kg_node`, `kg_edge` 뷰가 `analysis_revision`, `analysis_file`, `analysis_symbol`, `analysis_method`, `analysis_field`, `analysis_mapper`, `analysis_relation`에 걸려 있어서, 테이블을 DROP하면 뷰 때문에 막힙니다. 뷰 자체는 데이터가 없으니 따로 백업할 필요가 없습니다.

3. **데이터만 복원했다면 시퀀스 값을 맞춥니다.** `nextval` 기본값을 쓰는 테이블이 12개 있습니다. 시퀀스 값이 복원된 데이터보다 작으면 다음 분석에서 키가 겹칩니다. 예를 들면 이렇게 맞춥니다.
   ```sql
   SELECT setval('analysis_file_file_id_seq', (SELECT COALESCE(MAX(file_id),1) FROM analysis_file));
   ```
   시퀀스 이름은 `<테이블>_<컬럼>_seq` 규칙입니다. 대상 테이블과 컬럼은 다음과 같습니다.
   - `analysis_annotation`, `analysis_config`, `analysis_endpoint`, `analysis_error`, `analysis_file`, `analysis_mapper`, `analysis_reference`, `analysis_relation`, `analysis_resource`, `analysis_revision`: 각 테이블의 `*_id` 컬럼
   - `rag_chunk`: `chunk_id`
   - `rag_document`: `doc_seq`

4. **`rag_*`는 백업 대상에서 빼도 됩니다.** 원본은 `analysis_*`이고, `rag_*`는 언제든 다시 만들 수 있습니다. 다만 `rag_embedding`(약 650MB)을 다시 만들려면 임베딩 모델을 처음부터 다시 돌려야 합니다. 시간이 아까우면 이 테이블만 백업해 두는 편이 낫습니다. `(content_hash, model)` 키로 찾기 때문에 청크를 다시 만들어도 그대로 재사용됩니다.

참고로 큰 테이블은 `analysis_reference` 953MB, `analysis_relation` 712MB, `rag_embedding` 650MB, `analysis_method` 554MB입니다. 9단계에서 시간이 가장 오래 걸립니다.
