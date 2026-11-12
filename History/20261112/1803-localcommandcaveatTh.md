# localcommandcaveatTh

> 생성일시: 2026-11-12 18:03

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

---

## 질문

응 계획을 세워봐.

---

## 답변

현재 화면 분석 구조(ScreenLinker, 주소 매칭, 화면 판정 SQL)를 확인하고 있습니다. 확인이 끝나면 계획을 정리하겠습니다.

---

## 답변

# Vue/React 화면 분석 계획

## 1. 목표

Vue/React 프로젝트에서도 이미 WebSquare에서 되는 사슬이 그대로 이어지게 하는 것이 목표입니다.

```
화면(컴포넌트) → [import한 API 모듈 함수] → HTTP 호출 '/api/orders' → Spring @GetMapping → Service → DAO → SQL → 테이블
```

이렇게 되면 "테이블 `TB_ORDER`를 바꾸면 어느 화면이 영향을 받나"라는 질문에 Vue/React 화면까지 답할 수 있습니다.

## 2. 지금 구조에서 그대로 쓸 수 있는 것과 막히는 것

| 항목 | 현재 상태 | Vue/React에서 |
|---|---|---|
| SCAN (`FileClassifier`) | `.vue/.jsx/.tsx/.ts`는 목록에 없음. `.js`는 `SCRIPT`(공통 스크립트)로만 분류 | 확장자 추가와 화면 판정이 필요함 |
| 주소 매칭 (`ScreenLinker.linkPaths`) | 엔드포인트 경로와 **글자가 같을 때만** 연결 | `/api/orders/{id}` ↔ `` `/api/orders/${id}` ``가 맞지 않음. HTTP 메소드도 보지 않음 |
| 공통 함수 추적 (`ScriptFunctions`) | `a.b = function`, `function x`, `var x = function`만 인식. 이름만으로 전역 매칭 | `export const getOrders = () =>`와 화살표 함수를 모름. `getList` 같은 이름은 파일 수십 개에서 겹침 |
| 화면 판정 | `GraphDao.isScreen`, `DiagnosticDao`, `ScreenDictionaryPlugin`, `ScreenMenuReader`, `ResourceDocumentBuilder`, boot의 `knowledge-terms.js`에 `WEBSQUARE/NEXACRO`가 하드코딩됨 | 새 file_type을 전부에 추가해야 함 |
| 제외 폴더 | `node_modules`는 이미 제외됨 | `dist`와 `*.min.js`(빌드 산출물)는 제외되지 않아 잡음이 생김 |
| 규칙 (`screen: call`) | `ID → 메소드 이름 틀` 방식 | 이번에는 `URL → 엔드포인트` 방식이 필요함 |

SPA에서 가장 흔한 구조는 아래와 같습니다. 화면이 주소를 직접 쓰지 않고 API 모듈 함수를 import해서 부릅니다. 그래서 **import를 따라가는 모듈 연결이 이번 작업의 핵심**입니다.

```js
// src/api/order.ts
const http = axios.create({ baseURL: '/api' })
export const getOrders = (q) => http.get('/orders', { params: q })
// src/views/OrderList.vue
import { getOrders } from '@/api/order'
```

## 3. 설계 원칙 (기존 규칙과 맞춤)

- **파서**: JS/TS AST 파서는 넣지 않고, 기존 `ScreenScript`를 키운 **경량 토크나이저**를 씁니다. 문자열, 템플릿 리터럴, 주석, 정규식, JSX 텍스트만 구분하면 충분합니다. 필요한 것이 import/export, 함수 경계, 따옴표 글자, 호출 이름뿐이기 때문입니다. Java에는 TSX를 제대로 파싱하는 순수 라이브러리가 사실상 없고, GraalJS는 무겁고 호출 스레드 제약도 있습니다.
- **추측하지 않습니다.** 따옴표 리터럴만 인정합니다. 템플릿 리터럴(`` `/orders/${id}` ``)은 2단계 결정 사항으로 남겨 둡니다.
- **관례는 규칙 파일에 적습니다.** axios/fetch 관례는 `conf/rules/common/frontend.yml`에, 프로젝트별 래퍼 함수와 alias는 `projects/{ID}/`에 둡니다.
- **DB 기반으로 처리합니다.** 파일은 하나씩 읽고, 모듈 표(export 함수와 그 안의 URL)만 메모리에 둡니다. 공통 스크립트를 미리 읽어 두는 지금 방식과 같습니다.
- 람다와 Lombok은 쓰지 않고, 새 클래스는 `analysis.pass08.semantic.screen` 아래에 순수 클래스로 만들어 단위 테스트합니다.

## 4. 단계별 작업

### F0. 샘플과 기대값 (먼저 만듦)
- `src/test/resources/samples/vue-app`(Vue 3 SFC, Pinia, vue-router, axios 인스턴스, `@` alias)
- `src/test/resources/samples/react-app`(TSX, react-router, fetch 래퍼, 커스텀 훅)
- 백엔드 Spring Controller와 MyBatis를 같이 넣고, README에 기대 연결 수를 적습니다. `websquare-app` README와 같은 방식입니다.
- 실제 대상 프로젝트가 없으므로 이 샘플이 검증 기준이 됩니다. `/app` 아래에는 Vue/React 소스가 없었습니다.

### F1. SCAN
- 확장자를 추가합니다. `.vue` → `VUE`, `.jsx/.tsx` → `REACT`. `.js/.ts`는 내용에 JSX가 있고 react를 import하면 `REACT`, 아니면 `SCRIPT`(API 모듈, store)로 분류합니다. language는 `SCRIPT`로 둡니다(WebSquare가 `XML`을 유지한 것과 같은 방식).
- `dist`를 exclude-dirs에 추가하고, `*.min.js`와 한 줄이 아주 긴 파일(번들)은 `SKIPPED` 처리합니다.
- 화면 판정 6곳에 `VUE`, `REACT`를 추가하고 `schema` 주석과 `knowledge-terms.js`도 갱신합니다.
- 기존 프로젝트에 반영하려면 **새 리비전**이 필요합니다(`rerunFrom`으로는 안 됨).

### F2. 모듈 연결: `ScriptModules`(새 순수 클래스)
- import/export를 읽습니다: `export function`, `export const x = (…) =>`, `export default {…}`, `import {a as b}`, `import * as api`.
- 경로를 해석합니다: 상대 경로, 확장자 생략, `index.*`, alias. alias는 `tsconfig/jsconfig`의 `paths`(JSON이라 그대로 읽을 수 있음)를 쓰고, 안 되면 규칙 파일의 `alias: { "@": src }`를 씁니다. `vite.config`는 코드라서 읽지 않습니다.
- `.vue`는 `<script>`/`<script setup>`만 꺼내서 처리합니다.
- 결과: "모듈 파일 + export 이름 → 함수 본문"을 담은 표입니다. 이름이 아니라 **import 경로로 정확히 찾기 때문에** 겹치는 이름 문제가 없어집니다.
- 기존 `ScriptFunctions`의 "안정될 때까지 합치기"를 재사용해 컴포넌트 → store action → API 함수 → URL처럼 여러 단계를 따라갑니다.

### F3. HTTP 호출 → 엔드포인트
- 규칙 절을 확장합니다(`screen:`에 `request` 종류 추가).
  ```yaml
  screen:
    - { request: _.get(URL), verb: GET }      # axios.get, http.get, this.$http.get
    - { request: _.post(URL), verb: POST }
    - { request: fetch(URL) }                 # verb는 옵션의 method: 'POST' 글자로 판단, 없으면 GET
    - { request: _.request(URL) }
  base:                                        # axios.create({ baseURL: '/api' })는 자동으로 읽고, 못 읽으면 여기 적음
    - { module: src/api/http.ts, url: /api }
  ```
- 매칭: 엔드포인트의 `{var}`와 `**`는 한 마디 와일드카드로 봅니다. 글자가 완전히 같고 verb도 같으면 `HIGH`, 패턴으로 맞으면 `MEDIUM`, 첫 마디(context path나 프록시 접두어)를 떼고 맞으면 `LOW`입니다(기존 `withoutFirstSegment`와 같은 방식).
- `analysis_endpoint.http_method` 컬럼이 이미 있으므로 verb 비교에 그대로 씁니다.
- 결과는 `REQUESTS`이고 `via`는 `HTTP_CALL`(직접 호출) 또는 `MODULE`(import 경유)입니다. `properties_json`에는 `{url, verb, module, function, ruleAt}`를 남깁니다.
- 변수 URL은 연결하지 않고 `SCREEN_RULES` 통계에만 셉니다(기존과 같음).

### F4. 컴포넌트 트리와 라우터
- 컴포넌트 → 컴포넌트 연결은 import한 `.vue/.jsx/.tsx`가 템플릿이나 JSX에서 실제로 쓰일 때 만듭니다. 결과는 `INCLUDES`, `via = IMPORT`입니다. 그래야 자식 컴포넌트의 API 호출이 페이지까지 영향도로 올라갑니다.
- 라우터: `{ path: '/orders', component: OrderList | () => import('…') }`, `<Route path element={<X/>}>`에서 route path를 읽습니다. 라우터에 등록된 컴포넌트를 "페이지"로 표시하고, `meta.title`이나 `name`이 리터럴이면 `SCREEN_NAME`으로 씁니다. 메뉴 화면명보다 우선합니다.

### F5. 화면, 문서, 측정
- DOCUMENT가 VUE/REACT도 `VIEW` 문서로 쓰게 합니다(RAG 검색 대상이 됨).
- 노드 맵, 영향도, 진단("서버와 이어지지 않은 화면", "엔드포인트 없는 URL")을 확인합니다.
- 증분 분석과 전체 분석의 결과가 같은지 비교합니다(`knowledge-search-incremental.md` 절차).
- 문서: `docs/11` 2-1, 2-8(12번), 3.10, "못 하는 것"을 고치고, `.claude/docs/knowledge-screens-rich-client.md`에 기록합니다.

## 5. 범위 밖 (못 하는 것으로 문서화)
- 변수나 조립으로 만든 URL, 런타임 `baseURL` 변경, 환경 변수(`import.meta.env.VITE_API`) 값 해석. 단, `.env` 파일의 리터럴은 F3 이후 검토할 수 있습니다.
- GraphQL, WebSocket, Next.js/Nuxt의 서버 라우트(SSR)
- 백엔드와 다른 저장소에 있는 프런트엔드. 지금은 한 프로젝트가 폴더 하나라서, 두 폴더를 한 상위 폴더 아래에 두어야 합니다. 다중 소스 루트는 별도 과제입니다.

## 6. 먼저 정해야 할 것

1. **실제 대상 프로젝트가 있나요?** Vue 2/3 중 어느 것인지, TS 여부, API 호출 방식(axios 인스턴스, 자체 래퍼)에 따라 F2와 F3의 우선순위가 바뀝니다.
2. **템플릿 리터럴을 인정할까요?** `` `/orders/${id}` ``를 `/orders/{*}`로 보고 `{id}` 엔드포인트에 `MEDIUM`으로 연결하는 방안입니다. "계산하고 추측하지 않는다" 원칙의 경계에 있는 문제입니다. 고정 글자만 쓰고 `${}`는 한 마디 와일드카드로만 보는 방식이면 원칙을 지키면서 실용성도 크다고 봅니다. **인정하는 쪽을 권합니다.**
3. **화면 노드 범위**: 모든 컴포넌트를 화면 노드로 둘지(JSP 조각과 같은 방식), 라우터에 등록된 페이지만 화면으로 두고 나머지는 페이지 안에 접을지입니다. 처음에는 전자로 하고 노드 맵에서 접기를 나중에 하는 쪽을 권합니다.

진행 순서는 **F0 → F1 → F2 → F3**을 먼저 하는 것을 권합니다. 이 네 단계가 끝나면 핵심 사슬이 이어집니다. F4와 F5는 그다음입니다. 위 질문에 답해 주시면 F0 샘플부터 시작하겠습니다. 이 계획을 `.claude/docs/`나 `docs/temp`에 파일로 저장하길 원하시면 말씀해 주세요.

---

## 질문

일단 /app/testApp와 동일한 기능을 하는 샘플 어플리케이션을 아래와 같이 만들어줘. 그리고 올려줘.
/app/testVue
/app/testReact

---

## 답변

백엔드의 인증과 공통코드 부분을 다 만들었고, 이어서 회원사, 로그인이력, 게시판 API를 작성하고 있습니다.

---

## 답변

Vue 패키지를 설치하는 동안 testReact 백엔드를 준비합니다.

---

## 답변

패키지 설치를 기다리는 동안 `.gitignore`와 README를 작성합니다.

---

## 답변

React 백엔드도 빌드가 통과했습니다. npm 설치가 끝나면 두 화면을 빌드하고 실행해서 확인하겠습니다.
