# dstonebootknowledge_

> 생성일시: 2026-10-25 18:19

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

---

## 질문

화면명이 일본어야. 한글로 바꿔줘.

---

## 답변

메뉴 목록 자체가 일본어라서, 프로젝트 안에 한국어 메뉴명이 따로 있는지부터 찾아보겠습니다.

---

## 답변

Making the CSV helpers reusable inside the package, then writing the screen dictionary reader.

---

## 답변

Config entry and front end next.

---

## 답변

Unit test for the CSV reader.

---

## 답변

HI-Japan 화면 327개 중 WebSquare 화면 315개(팝업 포함)가 모두 한글명으로 나왔습니다. 복사본을 내리고, 화면 JS 확인과 문서 수정 뒤 올리겠습니다.

---

## 답변

HI-Japan 화면명을 한국어로 바꿨고, 커밋해서 GitHub에 올렸습니다(`595e6f49`).

**한국어 화면명의 출처**
HI-Japan 문서 폴더의 화면 목록 `docs/01.analysis/01.as-is/E.기능/00-1.화면목록.csv`를 씁니다. 여기에 화면별 "화면명(한글)"과 파일 경로가 정리되어 있어서, 테이블 사전 파일과 같은 방식으로 **화면 사전 파일**로 읽습니다.

**동작 방식**
- 화면 제목이나 메뉴 이름에 한글이 없으면 사전의 한국어 이름을 화면명으로 보여 줍니다.
- 원래 일본어 이름은 바로 아래 **원어명** 줄에 함께 나옵니다. 예를 들어 화면명은 "이행기한 관리 `사전`", 원어명은 "履行期間管理"입니다.
- 오른쪽 상세와 관계 트리 팝업 모두 같습니다.
- 사전 파일은 상세를 열 때 읽기 때문에 다시 분석할 필요가 없습니다. CSV를 고치면 다음 조회부터 바로 반영됩니다.
- 설정은 `conf/application.yml`의 `dstone.knowledge.dictionary.screen-file.HI-Japan`입니다. 다른 프로젝트도 화면 목록 CSV가 있으면 같은 키로 등록하면 됩니다.

**확인한 결과**
- 4181 복사본으로 HI-Japan 화면 327개의 상세를 모두 조회했습니다.
  - WebSquare 화면 315개는 메뉴에 없던 팝업까지 모두 한국어 화면명으로 나옵니다.
  - JSP 7개는 영문 `<title>`(예: "Hi-Japan")이 그대로 나옵니다. 사전에 해당 JSP가 없기 때문입니다.
  - 로그인 JSP 등 5개는 화면명이 없습니다.
- 단위 테스트 144개는 모두 통과했습니다. 화면 JS는 이번에도 상세를 그리는 함수만 node로 확인했습니다.

**직접 하셔야 할 일**
4081 dstone-knowledge와 7081 dstone-boot를 내린 뒤 `mvn clean package`를 하고 다시 띄워 주세요. 둘 다 아직 예전 jar와 war로 떠 있어서, 그래야 화면에 반영됩니다.

---

## 질문

4081이랑 7081 재기동해줘

---

## 답변

4081(dstone-knowledge)과 7081(dstone-boot)을 내리고, 다시 빌드한 뒤 띄우겠습니다. 먼저 돌고 있는 분석 작업이 없는지 확인합니다.

---

## 답변

dstone-knowledge는 내려갔는데 7081은 아직 떠 있습니다. dstone-boot 중지 스크립트를 보겠습니다.

---

## 답변

dstone-boot의 `stopApp.sh`에는 `cd`가 없어서 `bin` 안에서 실행해야 합니다. 다시 내리겠습니다.

---

## 답변

Both are down. Building (dstone-common is unchanged, so only the two modules).

---

## 답변

Both are up. Checking that the new code is live: the detail API, and graph.js served by 7081.

---

## 답변

4081은 새 코드로 응답합니다. 그런데 7081에서 받은 `graph.js`에는 새 코드가 안 보여서, 응답 내용을 확인하겠습니다.

---

## 답변

로그인 화면으로 넘어가서 그랬습니다. war 파일 안을 직접 확인하겠습니다.

---

## 답변

4081(dstone-knowledge)과 7081(dstone-boot)을 새로 빌드해서 다시 띄웠습니다. 두 포트 모두 응답합니다.

- **4081**: HI-Japan 화면 상세를 조회해 보니 `JCIQ0700G.xml`의 화면명은 "이행기한 관리"(출처 사전), 원어명은 "履行期間管理"로 나옵니다. 돌고 있는 분석 작업은 없었고, 임베딩 대기 건도 없습니다.
- **7081**: 화면 스크립트는 로그인을 거쳐야 받을 수 있어서 직접 내려받지는 못했습니다. 대신 새로 만든 war 안의 `graph.js`에 화면명·원어명 코드가 들어 있는 것을 확인했습니다.

브라우저에 예전 `graph.js`가 캐시되어 있으면 화면명·원어명 줄이 안 보일 수 있습니다. 그러면 노드 맵 화면에서 Ctrl+F5로 새로 고쳐 주세요.

dstone-boot의 `bin/stopApp.sh`에는 `cd`가 없습니다. 그래서 `bin` 폴더 안에서 실행해야 하고, 다른 폴더에서 실행하면 아무것도 하지 않고 끝납니다. dstone-knowledge 스크립트처럼 고쳐 둘까요?

---

## 질문

/app/dstone/dstone-*/bin/*.sh 의 모든 파일들을 점검해주고, /app/dstone/bin/*.sh 도 점검해줘. 그리고 올려줘.

---

## 답변

batch와 batchadmin은 실행 위치만 `bin`으로 맞추고, 루트의 일괄 스크립트를 고치겠습니다.

---

## 답변

잘 동작합니다. 남은 경우 하나, pid 파일은 있는데 프로세스는 이미 끝난 경우를 scratchpad 복사본으로 확인하겠습니다.

---

## 답변

문서를 고치겠습니다. `docs/04`는 boot 스크립트를 "쓰지 않는 유산"이라고 적고 있고, `CLAUDE.md`에는 boot 스크립트와 다른 점이 적혀 있습니다.

---

## 답변

`/app/dstone/dstone-*/bin/*.sh` 17개와 `/app/dstone/bin/*.sh` 2개를 점검해서 고쳤고, 커밋해서 GitHub에 올렸습니다(`2fc6f6db`). 지금 knowledge, ai-engine, boot 세 개는 떠 있고 batch 두 개는 내려가 있습니다.

**고친 것**

| 대상 | 문제 | 수정 |
|---|---|---|
| dstone-boot `startApp.sh`/`stopApp.sh` | `bin` 밖에서 실행하면 아무 일도 안 함. 프로파일이 `wsl`로 고정. 중지할 때 내려갈 때까지 기다리지 않음 | knowledge/ai-engine과 같은 모양으로 바꿈 |
| dstone-boot `statusApp.sh` | 없었음 | 새로 만듦 |
| boot·knowledge·ai-engine `startApp.sh` | 이미 떠 있어도 또 띄움. knowledge는 두 번째 인스턴스가 뜨면서 진행 중인 분석 작업을 실패로 바꿈 | 이미 실행 중이면 거절(exit 1). jar/war가 없으면 안내 |
| 같은 세 모듈 `stopApp.sh` | 30초 안에 안 내려가도 pid 파일을 지워서 남은 프로세스를 다시 찾을 수 없음 | pid 파일을 남기고 exit 1. 강제 종료(`kill -9`)는 사람이 판단 |
| batch·batchadmin `startApp.sh` | 앱이 실행한 폴더에 `application.pid`를 하나 더 씀 | `bin`에서 띄우도록 바꿈 |
| `bin/startApps.sh`, `stopApps.sh` | 주석 오타("knowlege", 중지 스크립트에 "실행"). 순서가 boot부터 | 띄우기는 knowledge → ai-engine → boot, 내리기는 그 반대 |
| `bin/statusApps.sh` | 없었음 | 새로 만듦. batch 두 개를 포함해 다섯 모듈의 상태를 봄 |

**확인한 것**
- 모든 스크립트가 `sh -n` 문법 검사를 통과했습니다.
- 떠 있는 boot와 knowledge에 다시 `startApp.sh`를 실행하면 거절합니다(exit 1).
- scratchpad 폴더에서 boot를 내렸다가 다시 띄웠습니다. 7081은 200으로 응답하고, 실행한 폴더에 pid 파일이 생기지 않았습니다.
- pid 파일이 없을 때와, 파일은 있는데 프로세스는 이미 끝났을 때 모두 안내만 하고 정상 종료합니다.
- 30초 안에 안 내려가는 경우와 batch 두 모듈은 실제로 돌려 보지 않았습니다. batch는 띄우면 Job이 실행될 수 있어서입니다.

Jenkins의 batch·batchadmin·knowledge 배포 단계는 그대로 동작합니다. 중지는 `|| true`로 부르고, 상태 확인의 `RUNNING` 출력도 같습니다.

`docs/03.build.md`, `docs/04.cloud-architecture.md`, `CLAUDE.md`도 같이 고쳤습니다. `docs/04`에 boot 스크립트가 "쓰지 않는 예전 파일"이라고 적혀 있던 부분도 지금 쓰임새에 맞게 바꿨습니다. 이번에는 `.sh`만 점검했고 `.bat` 파일은 손대지 않았습니다.
