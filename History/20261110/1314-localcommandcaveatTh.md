# localcommandcaveatTh

> 생성일시: 2026-11-10 13:14

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
