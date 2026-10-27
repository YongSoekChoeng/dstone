# dstonebootknowledge_

> 생성일시: 2026-10-27 15:52

## 질문

dstone-boot>knowledge 의 graph 에서 관계트리 따로보기 팝업에서 
보일계층 [화면] 앞에 [호출화면] 항목을 두어서(디폴트:Uncheck) 체크하면 해당 화면을 호출하는 다른 화면들도 보여주도록 하면 어떨까?

---

## 답변

Now the server-side changes.

---

## 답변

Now the service and controller.

---

## 답변

Server done. Now the screen side.

---

## 답변

컴파일은 통과했습니다. 실제 데이터로 확인하려고 테스트용 `dstone-knowledge`를 다른 포트에 띄워 보겠습니다.

---

## 답변

서버와 화면 코드 수정은 끝났고, 지금은 실제 DB(HI-Japan)에서 새 SQL이 맞게 도는지 확인하고 있습니다.

---

## 답변

SQL은 의도대로 나옵니다(JRIW1009P 팝업을 부르는 화면 7개, 그 위에 `main.xml`). 이제 jar 복사본으로 4181 포트에 띄워 API를 끝까지 확인합니다.

---

## 답변

API는 두 경우 모두 의도대로 동작합니다. 테스트 인스턴스를 내리고 문서를 고치겠습니다.

---

## 답변

좋은 생각이라 바로 넣었습니다. [호출화면] 칩을 [화면] 앞에 두었고, 처음에는 꺼져 있습니다. API는 HI-Japan 실제 데이터로 확인했지만, 칩 화면은 브라우저로 보지 못했습니다.

**동작**
- 켜면 트리에 나온 화면마다, 그 화면을 끼워 넣거나 팝업·`Div url`·`jsp:include`로 띄우는 다른 화면이 한 칸 왼쪽에 나옵니다. 이 관계는 분석이 이미 `INCLUDES`로 저장해 두고 있었고, 그것을 거꾸로 따라갑니다.
- 부르는 화면을 부르는 화면까지 "부르는 쪽 깊이"만큼 올라갑니다. 깊이가 0이면 가져오지 않습니다.
- 다른 칩과 같이 끄고 켤 때 서버를 다시 부르지 않습니다. 데이터는 늘 받아 두기 때문에 꺼 둔 상태에서도 칩 옆에 개수가 보입니다.
- 칩이 꺼져 있으면 화면끼리 잇는 선도 그리지 않습니다. 그래서 끈 상태의 그림은 지금과 같습니다.

**고친 곳**
- `dstone-knowledge`
  - `GraphDao.xml`에 `selectCallerScreenEdges` 쿼리를 추가했습니다.
  - `GraphService.collectCallerScreens`를 추가했습니다.
  - `/graph/neighborhood`에 `includeCallerScreens` 파라미터를 추가했습니다(기본값 false). 이렇게만 트리에 들어온 노드에는 `callerScreen: true`가 붙습니다.
- `dstone-boot`: `graph.js`에 `CALLER` 계층과 칩을 추가했습니다.
- 문서: `docs/11.dstone-knowledge.md`와 `CLAUDE.md`에 반영했습니다.

**확인한 것** (jar 복사본을 4181 포트에 띄워 리비전 55로 확인)
- 화면 `ui/jr/JRIW1008P.xml`(부르는 쪽 깊이 3): 노드가 2개에서 10개로 늘었습니다. 바로 부르는 화면이 7개이고, 그 위에 `ui/main.xml`이 있습니다.
- 화면 2개가 요청하는 메소드: 노드가 3개에서 29개로 늘었습니다.
- 확인을 마친 뒤 테스트 인스턴스는 내렸습니다.

**알아 두실 점**
- 링크 이동(`location.href` 등)은 관계로 저장되지 않기 때문에 호출화면에 나오지 않습니다.
- `ui/main.xml` 같은 메뉴 화면이나 공통 머리글은 거의 모든 화면의 호출화면으로 나옵니다. 기본값을 꺼 둔 이유입니다.
- 지금 4081에서 도는 `dstone-knowledge`는 다시 빌드하고 재시작해야 반영됩니다. `dstone-boot`도 다시 배포해야 합니다. 커밋은 아직 하지 않았습니다.

---

## 질문

올려줘

---

## 답변

GitHub `main`에 커밋 `04bd0fba`로 올렸습니다. 이번에 고친 7개 파일만 커밋에 넣었습니다(코드 5개, `docs/11.dstone-knowledge.md`, `CLAUDE.md`).

지금 떠 있는 서버에는 아직 반영되지 않았습니다. 화면에서 보려면 `dstone-knowledge`를 다시 빌드하고 재시작한 뒤 `dstone-boot`도 다시 배포해야 합니다.

---

## 질문

JIMC1000G 화면은 아래의 화면에서도 호출하고 있는데?
JIMC0900G.xml
JIMC1000G.xml

---

## 답변

JIMC0900G에서 JIMC1000G를 어떻게 부르는지 소스와 저장된 관계부터 확인하겠습니다.

---

## 답변

테스트 9개가 모두 통과했습니다. 이제 문서를 고칩니다.

---

## 답변

메뉴 항목 하나가 다릅니다(`JZCO0101G` → `JZCO0100G.xml`). 이 ID로 여는 호출이 있는지 확인합니다.

---

## 답변

맞습니다, 그 호출을 놓치고 있었습니다. 고쳤지만 노드 맵에 보이려면 리비전 55를 다시 분석해야 합니다. 이 단계는 아직 하지 않았습니다.

**놓친 이유**
JIMC0900G는 파일 경로가 아니라 화면 ID로 화면을 엽니다.
```
parent.scwin.fnOpenPage("JIMC1000G",{"plyNo":paramPlyNo,...});   // JIMC0900G.xml:172, 177
```
지금까지 분석은 따옴표 안에 적힌 화면 파일 경로(`src="/ui/ji/B.xml"`)만 화면끼리의 관계(`INCLUDES`)로 저장했습니다. 그래서 이 호출은 빠졌습니다.

**JIMC1000G.xml 자신에 대해서**
이 파일에서 JIMC1000G가 나오는 곳은 599행의 `parent.scwin.fnCloseTab("JIMC1000G")` 하나입니다. 자기 탭을 닫는 코드라서 다른 화면을 부르는 것이 아닙니다. 화면이 자기 자신을 부르는 경우는 호출화면에서 일부러 뺐습니다.

**고친 것**
- `dstone-knowledge`에 화면 열기 규칙 설정을 추가했습니다.
  ```yaml
  open-rules: "fnOpenPage={id}.xml"
  ```
  첫 인자로 적힌 화면 ID에서 `JIMC1000G.xml` 경로를 만들고, 그 경로로 끝나는 화면이 하나뿐일 때만 잇습니다. 거래 ID 규칙(`call-rules`)과 같은 방식이라 주석 안의 호출, 함수를 정의하는 곳, ID를 변수로 넘긴 호출은 잡지 않습니다.
- 바꾼 파일은 `RichClientPlugin`, `ScreenScript`, `conf/application.yml`, 단위 테스트(9개 모두 통과), `docs/11.dstone-knowledge.md`, `CLAUDE.md`입니다.

**HI-Japan 화면 파일에 직접 돌려 본 결과**
- `fnOpenPage` 호출은 138건입니다. ID를 변수로 넘긴 3건과 자기 자신을 여는 1건을 빼면, 서로 다른 화면 사이의 연결이 113개 새로 생깁니다. 화면을 못 찾은 호출은 없었습니다.
- 그중에 JIMC0900G → JIMC1000G도 들어 있습니다.
- 한계가 하나 있습니다. 이 규칙은 "화면 ID = 파일 이름"을 전제로 합니다. 메뉴 목록에서 이것이 어긋나는 항목이 하나 있습니다(`JZCO0101G` → `JZCO0100G.xml`). 지금은 이 ID를 `fnOpenPage`로 여는 곳이 없어서 잘못 이어진 것은 없습니다.

**화면에 반영하는 방법**
1. `dstone-knowledge`를 다시 빌드하고 재시작합니다.
2. HI-Japan 리비전 55를 `rerunFrom: SEMANTIC`으로 다시 분석합니다. SEMANTIC과 DOCUMENT 단계를 다시 돌기 때문에, 바뀐 화면 문서는 다시 임베딩됩니다.

재시작과 다시 분석을 제가 할까요? 커밋해서 올리는 것도 말씀해 주시면 하겠습니다.
