# dstonebootknowledge_

> 생성일시: 2026-10-25 17:55

## 질문

dstone-boot>knowledge 의 graph 에서 화면노드를 선택했을 때 우측 상세사항에 화면명을 넣어줘. 관계트리 따로보기도 마찬가지로 넣어주고.

---

## 답변

먼저 화면 노드 상세와 관계 트리 쪽 코드를 살펴보겠습니다.

---

## 답변

HI-Japan 화면에는 `<title>`이 없어서, 화면명을 어디서 얻을 수 있는지 더 찾아보겠습니다.

---

## 답변

Now writing the pure title extractor.

---

## 답변

Now the DAO, plugins and SQL.

---

## 답변

Now the detail API (GraphDao/GraphService) and the VIEW document title.

---

## 답변

Unit test next.

---

## 답변

Now the screen side — one row in `detailBody()`, shared by the right panel and the tree popup.

---

## 답변

Compiling both modules.

---

## 답변

4081은 아직 살아 있고, 분석 작업도 임베딩 대기도 없습니다. 운영 중인 4081/7081은 건드리지 않고, 복사본을 4181/7181에 띄워서 확인하겠습니다.

---

## 답변

Now re-running SEMANTIC on the three sample projects (struts / websquare / nexacro) through 4181.

---

## 답변

확인이 끝나서 4181 복사본을 내리고 문서를 고치겠습니다.

---

## 답변

마지막으로 dstone-knowledge 단위 테스트를 전부 돌리겠습니다.

---

## 답변

노드 맵에서 화면 노드를 고르면 오른쪽 상세 맨 위에 **화면명** 줄이 나옵니다. 오른쪽 상세와 관계 트리 팝업은 같은 함수로 그리기 때문에 팝업에도 똑같이 나옵니다.

**화면명을 찾는 곳**
- JSP와 WebSquare는 `<title>`, Nexacro와 X-Platform은 `<Form titletext="...">`에서 찾습니다. `<title>`이 있으면 그것을 씁니다.
- `${...}`나 `<%= %>`처럼 실행할 때 정해지는 제목, 태그가 섞인 제목, 주석 안의 제목은 화면명으로 쓰지 않습니다. 화면명이 없으면 "화면명 없음"이라고 표시합니다.
- 분석의 SEMANTIC 단계에서 찾아 분석 결과(`analysis_resource`, 종류 `SCREEN_NAME`)에 남깁니다. 노드 상세 API에는 `screenName`과 찾은 곳 `screenNameVia`가 들어갑니다.

**확인한 것**
- 새 단위 테스트를 포함해 dstone-knowledge 단위 테스트 136개가 모두 통과했습니다.
- jar 복사본을 4181 포트에 띄워 샘플 세 개를 SEMANTIC부터 다시 분석했습니다. `struts-app`의 `board/list.jsp`는 "게시글 목록", `nexacro-app`의 `OrderDetail.xfdl`은 "주문 상세"로 나왔습니다. 제목이 없는 `header.jsp`와 WebSquare 샘플은 비어 있었습니다.
- 화면 JS는 상세를 그리는 함수만 떼어 node로 돌려 HTML을 확인했습니다. 브라우저에서 직접 눌러 보지는 않았습니다.

**직접 하셔야 할 일**
1. **4081을 다시 띄워 주세요.** 제가 실수로 4081이 실행 중인 `target/dstone-knowledge.jar` 자리에 jar를 다시 빌드했습니다. 지금은 정상 응답하지만, 다음에 클래스를 새로 읽을 때 죽을 수 있습니다. `bin/stopApp.sh` 뒤 `bin/startApp.sh`로 다시 띄우면 이번 변경도 같이 반영됩니다. 지금 돌고 있는 분석 작업과 임베딩 대기는 없습니다.
2. **dstone-boot는 아직 빌드하지 않았습니다.** 7081도 `target/dstone-boot.war`로 떠 있어서입니다. 먼저 내린 다음 `mvn clean package`를 하고 다시 띄워 주세요.
3. **기존 리비전은 다시 분석해야 화면명이 나옵니다.** `rerunFrom: SEMANTIC`으로 다시 돌리면 됩니다. 샘플 세 개는 제가 이미 돌렸습니다.

**HI-Japan은 여전히 비어 있습니다.** HI-Japan 화면에는 `<title>`이 없고, 화면 이름은 `ui/main.xml`의 메뉴 목록에만 있습니다(`'MENU_NM':'履行期間管理' … 'SRC_PATH':'/ui/jc/JCIQ0700G.xml'`). 화면 318개 중 162개가 메뉴에 있고, 팝업(`…P.xml`)은 메뉴에 없습니다. 원하시면 메뉴 목록에서 화면명을 가져오는 규칙도 넣겠습니다. 거래 호출 규칙처럼 설정으로 넣는 방식이 될 것 같습니다.

`docs/11.dstone-knowledge.md`와 `CLAUDE.md`에도 이 내용을 반영했습니다. 커밋은 하지 않았습니다.

---

## 질문

응 화면명을 확보하지 못한 경우 메뉴목록에서 화면명을 가져오는 규칙도 넣어줘. 그리고 올려줘.

---

## 답변

메뉴 목록 규칙을 넣겠습니다. 먼저 화면 경로를 맞추는 기존 코드와 파일 분류를 보겠습니다.

---

## 답변

메뉴 목록에서 (화면명, 화면 경로) 쌍을 뽑는 순수 클래스부터 만들겠습니다.

---

## 답변

Now the plugin itself (runs after JSP 500 / rich-client 510).

---

## 답변

Front end: label for MENU and the "where" on hover.

---

## 답변

Unit test for `ScreenMenu`, including the real HI-Japan `main.xml` shape.

---

## 답변

단위 테스트 141개 모두 통과했습니다. 이제 HI-Japan으로 실제 확인할 차례인데, 먼저 4081이 아직 `target` jar로 떠 있는지 보겠습니다.

---

## 답변

4081이 여전히 `target` jar로 떠 있어서, 이번에는 소스를 scratchpad에 복사해 그곳에서 빌드하겠습니다.

---

## 답변

HI-Japan에서 메뉴 항목 157개 중 155개 화면에 화면명이 채워졌습니다. grep으로 따로 세어 맞는지 확인하고, 못 찾은 2개도 보겠습니다.

---

## 답변

숫자가 맞습니다. 161개 중 주석 안의 4개를 빼면 157개이고, 그중 파일이 실제로 없는 2개를 빼면 155개입니다. 상세 API로 확인하겠습니다.

---

## 답변

Docs update, then commit and push.

---

## 답변

화면 파일에 화면명이 없으면 메뉴 목록에서 가져오는 규칙을 넣었고, `main`에 커밋해 GitHub에 올렸습니다(`b5a9f415`).

**동작 방식**
- 화면 파일의 `<title>`이나 Nexacro `titletext`가 먼저이고, 둘 다 없을 때만 메뉴 목록을 봅니다. 새 플러그인 `ScreenMenuPlugin`이 SEMANTIC 단계에서 이 일을 합니다.
- 규칙은 `conf/application.yml`의 `dstone.knowledge.semantic.screen.menu-rules: "MENU_NM=SRC_PATH"`이고, "화면명 키=화면 경로 키" 모양입니다. 프로젝트마다 키 이름이 다르면 쉼표로 이어서 더 적으면 됩니다.
- 한 메뉴 항목(`{ }` 하나 또는 XML 태그 하나) 안에 두 키가 함께 있을 때만 씁니다. 경로가 빈 상위 메뉴와 주석 안의 항목은 뺍니다.
- 메뉴에 적힌 경로는 "그 경로로 끝나는 화면 파일"로 찾고, 화면이 하나로 정해질 때만 이름을 붙입니다.
- 오른쪽 상세와 관계 트리 팝업 모두 화면명 옆에 `title`/`titletext`/`메뉴` 표시가 붙습니다. 마우스를 올리면 찾은 파일과 줄이 나옵니다(예: `ui/main.xml:793`).

**HI-Japan에서 확인한 결과**
- 4181 복사본으로 HI-Japan 최신 리비전을 SEMANTIC부터 다시 분석했습니다.
- `ui/main.xml`의 메뉴 항목 157개 가운데 155개 화면에 화면명이 붙었습니다. 예를 들어 `JCIQ0700G.xml`은 "履行期間管理"입니다. grep으로 따로 세어 본 숫자와도 맞습니다(전체 161개에서 주석 안의 4개를 뺀 157개).
- 나머지 2개는 메뉴에 적힌 화면 파일(`JIMC0500G.xml` 등)이 실제로 없습니다.
- 메뉴에 없는 팝업(`…P.xml`)은 여전히 "화면명 없음"으로 나옵니다.
- 단위 테스트 141개는 모두 통과했습니다. 화면 JS는 상세를 그리는 함수만 떼어 node로 확인했고, 브라우저에서 눌러 보지는 않았습니다.

**직접 하셔야 할 일**
1. **4081과 7081을 다시 띄워 주세요.** 4081 dstone-knowledge는 아직 지난번에 제가 덮어쓴 jar 위에서 돌고 있습니다. 이번 빌드는 scratchpad에서 해서 `target`은 건드리지 않았습니다. 그래서 두 모듈 모두 내린 뒤 `mvn clean package`를 하고 다시 띄워야 변경이 반영됩니다.
2. **다른 프로젝트는 다시 분석해야 화면명이 나옵니다.** `rerunFrom: SEMANTIC`으로 돌리면 됩니다. HI-Japan(리비전 55)과 샘플 세 개는 이미 다시 돌려 두었습니다.

메뉴를 DB에만 두는 프로젝트는 소스에 메뉴 목록이 없어서 이 규칙으로는 채울 수 없습니다. `docs/11.dstone-knowledge.md`와 `CLAUDE.md`도 같이 고쳤습니다.
