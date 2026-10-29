# localcommandcaveatTh

> 생성일시: 2026-10-29 13:52

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

<pasted_content id="18ed">
dstone-boot>knowledge 의 graph 에서 HI-Japan 프로젝트의 호출관계를 추출하는데에 있어서... 
JMAT0202G 화면을 보면 아래의 화면들도 호출하는데 관계트리에서 보이지 않네.
JMAT0116P
JZCO0102P
</pasted_content id="18ed">

---

## 답변

HI-Japan 소스에서 JMAT0202G 화면이 두 화면을 어떻게 부르는지부터 확인하겠습니다.

---

## 답변

원인이 보입니다. 두 화면은 JMAT0202G가 직접 여는 게 아니라, 공통 스크립트 `js/wq/com_lib.js`의 함수를 거쳐 열립니다. 함수 내용을 더 봅니다.

---

## 답변

JMAT0202G가 쓰는 공통 함수는 위와 같습니다. 이제 분석기가 화면 → 화면 관계를 어떻게 찾는지 코드를 봅니다.

---

## 답변

.js 파일은 스캔 단계에서 빠집니다. 고칠 방향을 정하기 전에 HI-Japan에 .js가 얼마나 있는지와, 플러그인이 화면 본문을 어떻게 읽는지 확인합니다.

---

## 답변

.js는 34개뿐이라 스캔에 넣어도 부담이 없습니다. 플러그인과 `ScreenScript` 전체를 읽고 설계를 정합니다.

---

## 답변

설계를 정하기 전에, 새 언어(`.js`)를 스캔에 넣으면 어디에 영향이 가는지 봅니다. 최근에 `.sql`을 넣었을 때 손댄 곳을 따라가겠습니다.

---

## 답변

.js를 스캔에 넣었을 때 영향은 크지 않아 보입니다. 공통 스크립트에서 함수가 어떤 모양으로 정의되는지 확인합니다(이름 맞추는 규칙을 정하려고).

---

## 답변

샘플 표본(`websquare-app`)에도 공통 스크립트 경우를 넣겠습니다. 스캐너 테스트가 이 표본의 파일 수를 세는지 확인합니다.

---

## 답변

다른 단계가 새 언어 `SCRIPT` 파일을 건드리는지 확인합니다.

---

## 답변

주석 판정 부분에 끝 글자에서 범위를 벗어날 수 있는 곳이 있어 고칩니다.

---

## 답변

이제 `RichClientPlugin`에 공통 스크립트를 읽고 함수끼리 따라가 화면을 모으는 부분과, 화면의 호출을 잇는 부분을 넣습니다.

---

## 답변

Javadoc 설명을 먼저 고칩니다.

---

## 답변

Now the constants, run() wiring and the new methods.

---

## 답변

Now the scanner: classify `.js` / `.xjs` as `SCRIPT`.

---

## 답변

이제 단위 테스트와 표본입니다. `websquare-app`에 공통 스크립트를 넣고, `Header.xml`이 공통 함수를 거쳐 `OrderPopup.xml`을 열게 하겠습니다.

---

## 답변

Now the unit test.

---

## 답변

테스트는 모두 통과했습니다. 이제 실제 HI-Japan 소스로 같은 로직을 DB 없이 돌려 JMAT0202G 결과와 전체 영향(관계가 얼마나 늘어나는지)을 미리 봅니다.

---

## 답변

Docs: the sample README, the term dictionary, and the module doc.

---

## 답변

Updating the module doc.

---

## 답변

Now the sample README expectations (files 8 → 9, INCLUDES 2 → 3).

---

## 답변

CLAUDE.md의 리치클라이언트 단락에도 한 줄 넣습니다.

---

## 답변

고쳤습니다. 다만 실제 DB에 새 분석을 돌려서 확인하지는 않았습니다.

## 원인
JMAT0202G는 두 화면을 직접 열지 않고 공통 스크립트 `js/wq/com_lib.js`의 함수에 맡깁니다.

```
JMAT0202G.xml : comLib.gfnOpenPopupEmp(joParam);
com_lib.js    : comLib.gfnOpenPopupEmp = function(joParam) {
                    comLib.openPopup("JZCO0102P", "/ui/jz/JZCO0102P.xml", options); };
```

그런데 분석기는 `.js` 파일을 스캔 대상에 넣지 않았습니다. 코드 주석에도 "아직 다루지 않는 것"으로 적혀 있었습니다. 그래서 리비전 55에서 JMAT0202G의 화면 관계는 경로가 직접 적힌 JMAJ0202P와 JMCT0205P 두 개뿐이었습니다.

**JMAT0116P는 소스상 지금 열리지 않습니다.** `gfnOpenPopupAgnt` 안에서 JMAT0116P를 여는 줄은 주석 처리돼 있고, 실제로는 **JMAT0119P**를 엽니다(`com_lib.js:1594-1595`). 그래서 고친 뒤에도 JMAT0116P는 이어지지 않고 JMAT0119P가 이어집니다. 주석으로 지운 옛 호출을 잇지 않는 것은 기존 원칙과 같습니다.

## 바꾼 것
- **스캔**: `.js`와 `.xjs`를 종류 `SCRIPT`로 파일 목록에 올립니다. 내용은 읽지 않습니다.
- **새 클래스 `semantic.ScriptFunctions`**: 공통 스크립트에서 함수 정의와 본문 범위를 찾습니다. 문자열·주석·정규식 안의 중괄호는 세지 않습니다. 화면이나 스크립트 안에서 그 함수를 부르는 곳도 찾습니다.
- **`RichClientPlugin`**: 공통 함수마다 본문에 적힌 화면을 모읍니다. 함수가 다른 공통 함수를 부르면 그 함수가 여는 화면도 합칩니다(`gfnOneResultSearchEmp` → `gfnOpenPopupEmp` → JZCO0102P). 화면에서 그 함수를 부르면 `INCLUDES`로 잇습니다(`via = COMMON_SCRIPT`, `MEDIUM`).
- **잘못 잇지 않도록 한 것**:
  - 같은 이름이 여러 곳에 정의된 함수는 쓰지 않습니다.
  - 화면이 같은 이름의 함수를 직접 만들었으면 그 화면에서는 잇지 않습니다.
  - 다른 객체의 같은 이름 함수(`otherLib.gfnX`)는 맞추지 않습니다.
- 단위 테스트(`ScriptFunctionsTest`), `websquare-app` 표본과 README, `docs/11.dstone-knowledge.md`, `knowledge-terms.js`, CLAUDE.md를 고쳤습니다.

## 확인한 것
- `dstone-knowledge` 테스트 157개가 모두 통과했습니다.
- DB 없이 같은 로직을 HI-Japan 소스에 돌렸습니다.
  - JMAT0202G → **JMAT0119P, JZCO0102P, JZCO0104P**가 이어집니다. JZCO0104P는 조직 검색 팝업입니다.
  - 프로젝트 전체에서는 화면을 여는 공통 함수 15개, 화면 99개에서 화면 → 화면 관계 155건이 늘어납니다.
- **확인하지 않은 것**: 서버를 띄워 실제 분석을 돌리는 것과 노드 맵 화면.

## 반영하려면
`.js`는 예전 리비전에 없던 파일입니다. 그래서 `rerunFrom: SEMANTIC`으로는 나오지 않고, HI-Japan을 **새 리비전으로 분석**해야 합니다(증분 분석도 됩니다). 분석이 성공하면 보관 정책(최근 5개)에 따라 가장 오래된 리비전이 지워집니다. 그래서 제가 돌리지 않았습니다. 원하시면 돌리겠습니다.

## 남은 한계
공통 스크립트 안에서 거래 ID로 서버를 부르는 호출은 아직 화면과 잇지 않습니다. 예를 들어 `gfnOneResultSearchAgnt`가 부르는 `JMAT0116M01L`이 그렇습니다. 같은 방식으로 넣을 수 있는데, 하시겠습니까? 커밋은 하지 않았습니다.
