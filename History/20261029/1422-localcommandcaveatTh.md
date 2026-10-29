# localcommandcaveatTh

> 생성일시: 2026-10-29 14:22

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

---

## 질문

응 진행해주고 올려줘.

---

## 답변

먼저 공통 스크립트 안의 거래 호출을 화면과 잇는 작업을 하고, 테스트한 뒤 커밋·푸시하고, HI-Japan 새 리비전 분석까지 돌리겠습니다. `via` 값을 어디서 읽는지부터 확인합니다.

---

## 답변

Now `commonUsesOf`, the reworked `linkCommonScreens`, and collecting requests in `loadCommonFunctions`.

---

## 답변

클래스 설명도 맞춥니다.

---

## 답변

표본 README의 기대 값도 바뀝니다(Header.xml이 공통 함수를 거쳐 `ORD0100M01S`를 부르게 됨).

---

## 답변

테스트 157개가 통과했습니다. 이제 실제 분석을 돌리기 전에 지금 떠 있는 `dstone-knowledge`가 어디 것인지, 돌고 있는 분석 작업이 있는지 확인합니다.

---

## 답변

분석이 끝났습니다(`DONE_WITH_WARNING`). 경고가 이번 변경 때문인지 이전 리비전과 비교하고, SEMANTIC 결과와 JMAT0202G 관계를 확인합니다.

---

## 답변

DB에 의도한 대로 들어갔습니다. 노드 맵 API에서도 보이는지 확인합니다.

---

## 답변

노드 맵 API의 [화면호출관계]에도 JMAT0119P, JZCO0102P, JZCO0104P가 나옵니다. 문서를 실제 결과로 고친 뒤 커밋하고 푸시합니다.

---

## 답변

공통 스크립트 안의 거래 호출까지 잇도록 고쳤고, HI-Japan을 새로 분석해 확인한 뒤 커밋하고 푸시했습니다(`fd4949c0`).

## 이번에 더한 것
화면이 부르는 공통 함수 안에 거래 ID가 글자로 적혀 있으면, 그 함수를 부르는 화면의 `REQUESTS`로 잇습니다. 예를 들어 `gfnOneResultSearchAgnt` 안의 `doRequestAjax("JMAT0116M01L")`는 이 함수를 부르는 화면과 이어집니다.
- 함수 안에서 조건에 따라 부를 수 있어서 대상 메소드가 하나여도 신뢰도는 `MEDIUM`입니다.
- 줄 번호는 화면에서 그 공통 함수를 부른 줄입니다.
- 거래 ID를 변수로 받아 그대로 넘기기만 하는 함수는 세지 않습니다.

## 실제 분석 결과 (HI-Japan 리비전 56, 증분 분석 2분 34초)
- WSL의 `dstone-knowledge`(4081)를 새 jar로 다시 띄운 뒤 분석했습니다.
- 공통 함수를 거친 관계가 화면 → 화면 154건, 화면 → 메소드 요청 394건 늘었습니다. 화면을 열거나 서버를 부르는 공통 함수는 21개 찾았습니다.
- JMAT0202G에 새로 생긴 관계:
  - 화면: **JMAT0119P**(대리점 검색), **JZCO0102P**(사원 검색), **JZCO0104P**(조직 검색)
  - 거래: `performJMAT0116M01L`, `performJZCO0102P01S`, `performJZCO0104P01S`, `performJZCT0108M01S`
- 노드 맵 API에서 [화면호출관계]를 켜면 위 화면들이 불리는 화면으로 나오는 것을 확인했습니다. 브라우저 화면으로는 보지 않았습니다.
- 테스트 157개가 모두 통과했습니다.

## 참고할 점
- **JMAT0116P는 여전히 이어지지 않습니다.** `com_lib.js`에서 JMAT0116P를 여는 줄이 주석 처리돼 있고, 실제로는 JMAT0119P를 열기 때문입니다. 대신 JMAT0116의 조회 거래(`JMAT0116M01L`)는 이제 이어집니다.
- 분석 상태가 `DONE_WITH_WARNING`입니다. 외부 라이브러리 `js/xml2json.js`에 Latin-1 글자가 있어 인코딩 경고 1건이 생긴 것이고, 파일은 ISO-8859-1로 정상적으로 읽혔습니다.
- 보관 개수가 5개라 이번 분석으로 가장 오래된 리비전 53이 지워졌습니다. 지금은 49, 52, 54, 55, 56이 남아 있습니다.
- `dstone-boot`은 다시 띄우지 않았습니다. 바뀐 것은 용어 설명 파일(`knowledge-terms.js`) 한 줄이라, 다음에 배포할 때 반영됩니다.
- 공통 스크립트 안에 적힌 주소(`/xxx.do`)는 아직 읽지 않습니다.

---

## 질문

모두 적용하고 올려줘.

---

## 답변

남은 것을 모두 적용하겠습니다. 공통 스크립트 안의 주소(`/xxx.do`)도 잇고, HI-Japan을 다시 분석하고, `dstone-boot`을 다시 배포한 뒤 커밋·푸시합니다. 먼저 `dstone-boot`이 어떻게 떠 있는지 확인합니다.

---

## 답변

`dstone-boot`도 WSL에서 스크립트로 떠 있습니다(kind 클러스터는 꺼져 있음). 이제 `linkPaths`를 공통 함수 안의 주소까지 보도록 고칩니다.

---

## 답변

`loadCommonFunctions`에 진입점 목록을 넘기고, 도우미 메소드와 설명을 맞춥니다.

---

## 답변

표본에 공통 함수 안의 주소 경우도 넣습니다.

---

## 답변

테스트 통과. `dstone-knowledge`를 새로 배포하고 리비전 56을 SEMANTIC부터 다시 돌립니다.

---

## 답변

Meanwhile, rebuild and restart `dstone-boot` (needs `dstone-common` installed — check it's current).

---

## 답변

표본 분석 결과가 README 기대 값과 같습니다(`INCLUDES` 3, `REQUESTS` 7, 공통 함수를 거친 화면·거래·주소 각 1건). 문서를 고치고 커밋·푸시합니다.

---

## 답변

Remaining items are applied, both servers are redeployed, and I committed and pushed (`8b327832`).

## What I applied
1. **Addresses inside common functions**: if a common function's body contains an endpoint address like `/order/detail.do`, every screen that calls that function now gets a link to the matching server method (`REQUESTS`).
   - Confidence follows the same rule as an address written in the screen itself: `MEDIUM` for an exact match, `LOW` when it only matches after dropping the leading context path.
   - Each link records which common function and which script file it came through.
2. **`dstone-knowledge` redeployed**: rebuilt and restarted on port 4081.
3. **HI-Japan revision 56 re-run** from the screen-linking step (SEMANTIC) onward: finished `DONE`.
4. **`dstone-boot` redeployed**: rebuilt and restarted on 7081. The updated description for the new `SCRIPT` file type is included.

## Verification
- **HI-Japan**: links that go through common functions stay at 154 screen → screen and 394 server calls. The common scripts here contain no endpoint addresses (everything is called by transaction ID), so the new address path adds nothing for this project. JMAT0202G's results are the same as last time.
- **`websquare-app` sample** (analyzed as new revision 57): each of the three cases (screen, transaction ID, address through a common function) produced one link, matching the expected values in the sample's README.
- All 157 tests passed.
- I did not check the node map screen in a browser.

## Notes
- The `websquare-app` analysis added a new revision. That project had fewer than 5 revisions, so nothing was deleted.
- Calls that a script makes on its own are still not linked. Links are only created when a screen calls a common function.

---

## 질문

기억해. 앞으로 설명은 한글로 해줘. 지금 모든게 올라간 상태지?

---

## 답변

앞 답변을 영어로 드려서 죄송합니다. 원래 메모리에 있던 한글 규칙에 이번 일을 덧붙여 저장했습니다. 이번처럼 작업이 길게 이어진 뒤 마지막 요약에서 실수가 나기 쉽다는 점도 함께 적었습니다.

네, 모두 올라간 상태입니다.

- **Git**: 작업 폴더에 남은 변경이 없고, `main`이 `origin/main`과 같습니다. 이번 작업 커밋 두 개(`fd4949c0`, `8b327832`)가 원격에 올라가 있습니다. 그 뒤의 `2ca6fc86`은 자동으로 들어간 history 커밋입니다.
- **서버**:
  - `dstone-knowledge`(4081)와 `dstone-boot`(7081) 모두 새로 빌드한 파일로 다시 띄웠습니다.
  - `dstone-ai-engine`(8081)은 바뀐 것이 없어 그대로 두었습니다.
- **분석 결과**:
  - HI-Japan은 리비전 56에 이번 변경이 반영됐습니다.
  - `websquare-app` 표본은 새 리비전 57로 분석해, README 기대 값과 같게 나왔습니다.

노드 맵 화면은 아직 브라우저로 직접 보지 않았습니다. 노드 맵에서 JMAT0202G를 열고 [화면호출관계]를 켜면 JMAT0119P, JZCO0102P, JZCO0104P가 나와야 합니다.
