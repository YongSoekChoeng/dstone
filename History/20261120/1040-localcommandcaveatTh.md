# localcommandcaveatTh

> 생성일시: 2026-11-20 10:40

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="1c9f">
dstone-common/src/main/resources 가 잘못 위치하고 있는 것 같아.
dstone-common 는 서버용 라이브러리들을 모아놓은 공통모듈인데 웹자원들이 들어오면 안될 것 같아.
또 dstone-console 이라는 명칭도 난 생소해. 아마도 dstone-boot 에서 사용하는 것 같은데...
모두 제자리로 돌려줘. dstone-common 는 적어도 지금까지는 resources가 필요 없을 것 같아.
</pasted_content id="1c9f">

---

## 답변

먼저 `dstone-common/src/main/resources`에 무엇이 있는지, 어디서 쓰는지 확인하겠습니다.

---

## 답변

ai-engine 쪽 `css` 폴더가 없어서 CSS 두 개만 옮겨지지 않았습니다. 다시 옮깁니다.

---

## 답변

이제 파일 머리 주석을 각 앱 기준으로 고칩니다.

---

## 답변

문서를 고치기 전에, 자원 주소가 `/dstone-console/...`에서 `/ai/assets/...`로 바뀌면서 로그인 필터에 걸리지 않는지 확인합니다.

---

## 답변

필터는 `<uiRoot>/assets/`를 이미 빼 주므로 문제없습니다. 이제 dstone-common pom과 문서를 고칩니다.

---

## 답변

문서 갱신을 마쳤습니다. 리소스 복사 단계만 돌려서 빌드 결과에 파일이 제대로 들어가는지 확인합니다 (실행 중인 jar는 건드리지 않습니다).

---

## 답변

웹 자원은 dstone-common에서 모두 빼서 제자리로 돌려놓았습니다. 커밋은 아직 하지 않았습니다.

**`dstone-console`은 무엇이었나**
dstone-boot에서 쓰던 것이 아니었습니다. 원래 dstone-boot의 `webapp/ai/assets/css/console.css`, `main.css`, `fonts/`에 있던 AI·코드 분석 화면의 모양 파일입니다. 11-18에 두 화면을 dstone-ai-engine과 dstone-knowledge로 옮기면서, 두 앱이 같이 쓰도록 dstone-common의 `META-INF/resources/dstone-console/`로 넣고 그때 이름을 새로 붙였습니다. 지금 dstone-boot는 이 파일을 쓰지 않습니다.

**바꾼 내용**
- 화면을 실제로 쓰는 두 앱에 하나씩 넣었습니다. `console-parts.css`는 원래 이름인 `main.css`로 되돌렸습니다.
  - dstone-ai-engine: `static/ai/assets/{css/console.css, css/main.css, js/console.js, fonts/}`
  - dstone-knowledge: `static/knowledge/assets/` 아래에 같은 구성
- AI 화면 5개와 Knowledge 화면 9개의 `/dstone-console/...` 경로를 각 앱의 `assets` 경로로 바꿨습니다.
- dstone-common `pom.xml`에서 `META-INF/resources` 복사 설정을 지웠습니다.
- 파일 머리 주석, `ConsoleSessionFilter` 주석, `CLAUDE.md`, `docs/09`, `docs/11`, `.claude/docs/knowledge-ui.md`를 고쳤습니다.

**확인한 것**
- 리소스 복사 단계만 실행해 두 앱의 빌드 결과에 파일이 들어가는지 봤고, 글꼴 파일이 깨지지 않은 것도 확인했습니다.
- 로그인 필터는 `/ai/assets/`, `/knowledge/assets/` 경로를 원래 건너뛰므로, 로그인을 켜도 CSS나 JS 요청이 SSO로 넘어가지 않습니다.
- 앱을 띄워 화면을 직접 열어 보지는 않았습니다.

같은 파일이 두 앱에 하나씩 생겼으니 모양을 고칠 때는 둘 다 맞춰야 합니다. 이 내용은 문서와 파일 주석에 적어 두었습니다.

**dstone-common `resources`에 아직 남은 것 (웹 자원이 아니라 그대로 둠)**

| 파일 | 하는 일 |
|---|---|
| `META-INF/spring.factories` | `ENC(...)` 설정값을 자동으로 복호화하는 처리기를 등록합니다. 지우면 다섯 모듈 모두 DB 비밀번호를 복호화하지 못해 기동에 실패합니다. 꼭 필요합니다. |
| `log4jdbc.log4j2.properties` | 모듈마다 자기 사본이 있어서 dstone-common 쪽은 사실상 쓰이지 않습니다. 지워도 될 것으로 보입니다. |
| `schema/01-create-table-mysql-dstone-messaging.sql`, `sqlmap-template/messaging/*.xml.sample` | dstone-common에 있는 Saga/Outbox 메시징 코드용 테이블 DDL과 매퍼 견본입니다. 서버 쪽 자원이라 웹 자원과는 다릅니다. |

`spring.factories`만큼은 dstone-common에 남겨야 합니다. `log4jdbc.log4j2.properties`는 지우고, 메시징 DDL과 견본은 `dstone-common/docs` 같은 비리소스 폴더로 옮기는 정리도 할 수 있습니다. 원하시면 말씀해 주세요.

참고로 API 호출자 이름 기본값 `caller: dstone-console`(`ApiKeyAuthFilter`)에도 같은 이름이 남아 있습니다. 파일 위치와는 관계없는 값이라 손대지 않았지만, 이름을 바꾸길 원하시면 함께 정리하겠습니다.
