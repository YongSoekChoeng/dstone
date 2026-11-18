# localcommandcaveatTh

> 생성일시: 2026-11-18 10:05

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="6a42">
dstone-boot>ai, dstone-boot>knowledge 이 두 메뉴를 
각자의 원래 어플리케이션으로 옮기고 싶어.
dstone-boot>ai => dstone-ai-engine
dstone-boot>knowledge => dstone-knowledge
먼저 웹자원들 및 dstone-boot 에 존재하던 각 모듈소속 api(컨트롤러등) 들은 해당 모듈로 옮겨줘.(dstone-boot 에 쓰레기 데이터가 남지 않도록 모두 옮긴 후에는 삭제)
dstone-ai-engine, dstone-knowledge 는 독립적으로 사용될 때는 로그인 없이 진행 가능하도록 만들어주고 dstone-boot에서 링크로 들어올 때는 세션값을 공유하도록 만들어줘.
(application.yml 에 설정으로 로그인적용여부를 on/off 할 수 있게. 로그인적용여부 off 일 때는 디폴트세션값을 넣어주도록 처리.)
</pasted_content id="6a42">

---

## 답변

먼저 dstone-boot 쪽 ai·knowledge 메뉴의 구성 요소를 확인하겠습니다.

---

## 답변

두 모듈로 옮길 때 바뀌어야 할 부분(JSP 문법, JS 안의 dstone-boot 주소)을 확인하고 있습니다.

---

## 답변

세션 넘기기 방식과 화면 이전 방식을 정했습니다. 이제 배포 환경에서 브라우저가 각 모듈에 닿는 주소(k8s NodePort 등)를 확인합니다.

---

## 답변

공통 Java 3개를 만들었습니다. 이제 git mv로 정적 자원을 각 모듈로 옮깁니다(이력 보존).

---

## 답변

이제 JSP 공통 조각(head/header/footer, 왼쪽 메뉴)을 대신할 공통 스크립트 `console.js`를 만듭니다.

---

## 답변

이제 JSP 21개를 정적 HTML로 바꾸는 변환 스크립트를 돌립니다(JSP 문법만 바꾸고 화면 내용은 그대로 둡니다).

---

## 답변

HTML 변환 결과가 정상입니다. 이제 메뉴 목록 파일과 JS 안의 dstone-boot 주소를 고칩니다.

---

## 답변

knowledge 화면 JS는 이제 같은 서버의 `/api/**`를 바로 부릅니다. 다음은 knowledge 쪽 Java(필터 등록, API 키 필터의 세션 허용)와 설정입니다.

---

## 답변

Now the ConsoleBaseService and the rewrites of AiEngineUser / ChatService.

---

## 답변

엔진 쪽 이동은 거의 끝났고, 남은 dstone-boot 관련 문구와 설정 키 이름을 정리하고 있습니다.

---

## 답변

ai-engine 설정 파일과 k8s ConfigMap에 화면 세션 설정을 넣습니다.

---

## 답변

이제 dstone-boot 정리입니다: 옮긴 원본을 지우고, 링크용 `/sso/go.do`를 추가합니다.

---

## 답변

dstone-boot에 링크용 컨트롤러(`/sso/go.do`)를 추가합니다.

---

## 답변

코드 작성이 끝났습니다. 빌드 전에 실행 중인 인스턴스가 target의 jar를 쓰고 있는지 확인합니다(그 자리에서 다시 빌드하면 안 되기 때문).

---

## 답변

세 앱이 모두 `target`의 jar로 떠 있어서, 저장소를 scratchpad에 복사해 거기서 빌드합니다.

---

## 답변

dstone-common이 리소스 필터링을 켜 두어 글꼴(woff2)에서 실패했습니다. pom을 확인해 고칩니다.

---

## 답변

빌드 성공했습니다. 이제 복사본을 다른 포트로 띄워 실제 동작을 확인합니다(4081/8081/7081의 기존 인스턴스는 건드리지 않음).

---

## 답변

테스트용 복사본이 기존 로그·설정 폴더를 쓰지 않도록 scratchpad 전용 env로 다시 빌드하고 다른 포트(14081, 18081)로 띄웁니다.

---

## 답변

시험 인스턴스가 응답하지 않아 원인을 보고 있습니다(curl에 시간 제한을 걸어 다시 확인).

---

## 답변

Both died. Logs:

---

## 답변

Now relaunch the test instances (with timeouts on every check).

---

## 답변

Both test instances are up. Testing login-off flow (pages, session info, static resources, APIs):

---

## 답변

Java test instance is already stopped; continuing with a script file to avoid self-matching.

---

## 답변

Seed test users exist (sample data). Building a boot copy with scratch APP_HOME and launching on 17081:

---

## 답변

The ai test instance was stopped but the shell was killed before the build. Running the rest from a script file:

---

## 답변

Now the end-to-end flow through boot login → `/sso/go.do` → ai-engine:

---

## 답변

시험 인스턴스를 모두 내렸습니다(기존 4081/7081/8081 인스턴스는 건드리지 않음). 이제 문서를 갱신합니다. 먼저 옛 위치를 가리키는 문서를 찾습니다.

---

## 답변

History is logs — skip. Looking at the live docs' references:

---

## 답변

Writing the migration script for the docs chapter:

---

## 답변

Next, the remaining references in 06 (package tree, upload section) and in docs/09:

---

## 답변

Now the knowledge documents. Viewing the relevant passages in `docs/11.dstone-knowledge.md`:

---

## 답변

Now the `.claude/docs` notes:

---

## 답변

Updating CLAUDE.md (security rules, doc table, env table) — keeping it short:

---

## 답변

Docs are updated. Last step: install the new dstone-common into the local Maven repo so per-module builds pick it up (running instances are unaffected), then clean up scratch run processes.

---

## 답변

AI 화면과 코드 분석(Knowledge) 화면을 각자의 앱으로 옮겼고, dstone-boot에서는 원본을 모두 지웠습니다. 복사본을 다른 포트(14081, 17081, 18081)로 띄워 로그인을 끈 경우와 켠 경우를 모두 끝까지 시험했고, 정상 동작을 확인했습니다. 실제 브라우저로 화면을 눌러 보지는 않았고, JS 문법 검사만 했습니다. 커밋은 하지 않았습니다.

## 옮긴 것
- **dstone-ai-engine**: AI 화면 5개를 `static/ai/**`로, 컨트롤러·서비스·VO는 `net.dstone.ai.console` 패키지로 옮겼습니다. 화면이 부르는 주소(`/ai/**.do`)는 예전과 같습니다.
  - 컨트롤러 하나는 엔진의 같은 이름 클래스와 충돌해서 `ChatConsoleController`로 이름을 바꿨습니다.
- **dstone-knowledge**: 화면 9개를 `static/knowledge/**`로 옮겼습니다. dstone-boot에 있던 중계 컨트롤러는 없애고, 화면이 같은 서버의 `/api/**`를 바로 부르도록 바꿨습니다.
- **dstone-common**: 두 앱이 같이 쓰는 모양(`console.css`, 글꼴, 왼쪽 메뉴 스크립트)을 넣었습니다.
- **dstone-boot**: 옮긴 화면·자바 파일, 관련 설정(`interface.ai-engine`, `interface.knowledge`, 환경변수, 업로드 105MB 한도)을 지웠습니다.
- 실행 jar에서는 JSP를 쓸 수 없어서 화면은 정적 HTML로 바꿨습니다. 화면 내용은 그대로입니다.

## 로그인 on/off와 세션 공유
- 설정 키는 `dstone.ai.console.login.enabled`, `dstone.knowledge.console.login.enabled`이고 기본값은 `false`입니다.
- **off**: 로그인 없이 기본 세션 사용자로 씁니다. 기본값은 `local` / 로컬 사용자 / 관리자이고, `login.default-user`에서 바꿉니다.
- **on**: dstone-boot 메뉴의 링크로만 들어옵니다. 세션이 없으면 dstone-boot로 보내 로그인 정보를 받아 옵니다.
- **세션 공유 방식**: dstone-boot 메뉴가 `/sso/go.do`를 거쳐, 로그인 사용자(id, 역할, 관리자 여부)를 암호화한 1회용 토큰(60초)에 담아 넘깁니다. 받는 앱이 그 사용자로 자기 세션을 만듭니다. 토큰을 다시 쓰거나 다른 앱 앞으로 만든 토큰을 쓰면 거절합니다.
- **Redis 세션 공유가 아닌 이유**: 설정에는 Redis 세션이 적혀 있지만, dstone-boot는 실제로는 Tomcat 메모리 세션을 씁니다(Spring Boot 4에서 필요한 모듈이 빠져 있음). 그래서 세션 저장소를 같이 쓸 수 없었습니다.
- 그 결과 **dstone-boot에서 로그아웃해도 넘겨받은 앱의 세션은 120분 동안 남습니다.**
- 두 앱의 세션 쿠키 이름은 따로 정했습니다. 같은 `JSESSIONID`를 쓰면 포트가 달라도 dstone-boot 로그인을 덮어쓰기 때문입니다.

## 같이 고친 것
- **빌드 설정**: 세 모듈이 리소스 전체를 Maven 필터링하고 있어서 글꼴이 깨지고 JS 라이브러리가 바뀔 위험이 있었습니다. 화면 자원 폴더는 필터링에서 뺐습니다(pom 3개).
- **주소 충돌**: dstone-common `BaseController`의 `/proxy.do`가 새 컨트롤러와 겹쳐 기동에 실패했습니다. dstone-boot 쪽은 클래스 경로를 붙였고, ai-engine 화면 컨트롤러는 그 프록시가 열리지 않게 `BaseController`를 상속하지 않도록 했습니다.
- **knowledge 인증**: API 키 인증을 켜려면 화면 로그인도 같이 켜야 합니다. 로그인을 켠 상태에서는 세션 사용자를 키 대신 받아 줍니다.
- **문서**: 06(SSO 링크), 09 18장(AI 화면, 06에서 옮김), 11 4장과 11-rag·11-rules, `.claude/docs`, CLAUDE.md를 고쳤습니다.

## 지금 떠 있는 4081/8081/7081에는 아직 반영되지 않았습니다
세 앱 모두 `target`의 jar로 실행 중이라 다시 빌드하지 않았습니다. 새 `dstone-common`은 로컬 Maven 저장소에 설치해 두었습니다. 반영하려면 세 앱을 내리고, 다시 빌드한 뒤 올리면 됩니다. k8s의 dstone-boot와 ai-engine은 ConfigMap도 다시 적용해야 합니다.

---

## 질문

전부 올려줘.

---

## 답변

세 앱을 내리고 새로 빌드한 뒤 다시 올리겠습니다. 먼저 루트 스크립트와 현재 상태를 확인합니다.

---

## 답변

Stopped. Building from the repo now:

---

## 답변

All three are up. Quick smoke test on the real ports:

---

## 답변

세 앱을 새 빌드로 다시 올렸습니다. 모두 실행 중이고 상태 확인 주소도 정상으로 응답합니다.

| 앱 | 주소 | PID |
|---|---|---|
| dstone-knowledge | http://localhost:4081/ → 코드 분석 대시보드 | 22206 |
| dstone-ai-engine | http://localhost:8081/ → AI 개요 | 22210 |
| dstone-boot | http://localhost:7081 | 22216 |

curl로 실제 포트에서 확인한 결과는 아래와 같습니다. 브라우저로 화면을 직접 열어 보지는 않았습니다.
- knowledge: 첫 화면 이동, 대시보드, `/api/projects` 모두 200입니다.
- ai-engine: 첫 화면 이동, 개요 화면, 화면용 API, 공통 CSS 모두 200입니다.
- dstone-boot에서 `ADMIN01`로 로그인한 뒤 메뉴 링크로 AI 화면에 들어가면, ai-engine 세션이 `ADMIN01`(관리자)로 만들어집니다.

지금은 기본 설정이라 두 앱 모두 로그인이 꺼져 있습니다(`login.enabled: false`). 그래서 4081이나 8081로 바로 열면 로그인 없이 기본 사용자 `local`로 들어갑니다. 로그인을 켜려면 각 앱의 `conf/application.yml`에서 `dstone.{ai,knowledge}.console.login.enabled`를 `true`로 바꾸고 그 앱을 다시 띄우면 됩니다.

커밋과 푸시는 아직 하지 않았습니다. "올려줘"가 git push를 뜻했다면 말씀해 주세요. 커밋하고 푸시하겠습니다.

---

## 질문

커밋 푸시해줘.

---

## 답변

파일 이동과 삭제가 이미 커밋되어 있는 것으로 보입니다. 최근 커밋을 확인합니다.

---

## 답변

파일 이동·삭제(이름만 바뀐 것)는 작업 중에 자동 history 커밋(7a434145)에 이미 들어가 푸시까지 되어 있습니다. 내용 변경분을 커밋하고 푸시합니다.

---

## 답변

커밋하고 `main`에 푸시했습니다. 로컬과 `origin/main`이 같은 상태입니다.

- **`a3d45af4`**: 이번 작업의 내용 변경 전부입니다(87개 파일).
  - 새로 만든 공통 클래스와 화면 HTML
  - 설정, pom, dstone-boot의 `SsoController`
  - 문서
- **`7a434145`**: 파일 이동과 삭제는 작업 도중에 자동 history 커밋에 먼저 들어가 이미 푸시되어 있었습니다. 경로만 바뀐 내용이고, 실제 수정은 위 커밋에 있습니다.
