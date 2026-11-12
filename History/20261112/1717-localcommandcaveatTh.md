# localcommandcaveatTh

> 생성일시: 2026-11-12 17:17

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-knowledge 가 vue 나 react 도 분석가능한가?

---

## 답변

아니요. 지금의 dstone-knowledge는 Vue나 React 화면을 분석하지 못합니다.

**이유**
- 분석할 파일은 `pass01/scan/FileClassifier.java:33`에서 확장자로 고릅니다. 받는 확장자는 `.java`, `.jsp` 계열, `.xml`, `.xfdl`/`.xadl`, `.yml`, `.properties`, `.gradle`, `.sql`/`.ddl`, `.js`/`.xjs`입니다.
- `.vue`, `.jsx`, `.tsx`, `.ts`는 이 목록에 없어서 파일 목록에도 올라가지 않습니다.
- `.js` 파일은 읽지만 용도가 좁습니다. `ScreenLinker`가 WebSquare/Nexacro 화면이 부르는 **공통 함수**를 찾을 때만 씁니다(`file_type = SCRIPT`, via = `COMMON_SCRIPT`). 그래서 React/Vue 번들 소스를 넣어도 컴포넌트, 라우트, `axios`/`fetch` 호출은 잡히지 않습니다.
- JavaScript 파서도 없습니다. 지금 쓰는 파서는 javaparser, JSQLParser, XML 파서뿐이고 Rhino, GraalJS, esprima 같은 것은 들어 있지 않습니다.

**Vue/React 프로젝트를 넣었을 때 결과**
- 백엔드 쪽(Spring Controller → Service → DAO → SQL → 테이블)은 지금처럼 분석됩니다. 그래서 `/api/...` 엔드포인트에서 테이블까지 이어지는 영향도는 나옵니다.
- 화면 → API 연결은 빠집니다. "이 화면이 어떤 API를 부르는가", "이 테이블을 바꾸면 어느 화면이 영향을 받는가"라는 질문에는 답하지 못합니다.

**지원하려면 필요한 작업 (참고)**
1. `FileClassifier`에 `.vue`/`.jsx`/`.tsx`/`.ts`를 추가합니다.
2. 화면 단위를 정합니다. Vue는 SFC 파일, React는 컴포넌트 파일 단위가 됩니다. 라우터 설정(`vue-router`, `react-router`)에서 화면 경로도 읽어야 합니다.
3. API 호출을 추출합니다. 프로젝트 규칙과 맞추려면 `axios.get('/api/x')`처럼 **따옴표 리터럴 주소만** 인정하는 규칙 파일 절(`call:` 계열)이 필요합니다. 공통 API 래퍼(`api.get(...)`)도 기존 "공통 함수" 방식으로 따라갈 수 있습니다.
4. 파서를 고릅니다. 정규식만으로는 TSX/템플릿 구문을 다루기에 한계가 있어서 JS/TS AST 파서를 새로 넣어야 할 수 있습니다.

원하시면 실제 대상 프로젝트의 Vue/React 코드가 어떤 모양인지(API 호출 방식, 라우터 구성)를 먼저 보고 설계안을 정리해 드리겠습니다.
