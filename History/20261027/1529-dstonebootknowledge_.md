# dstonebootknowledge_

> 생성일시: 2026-10-27 15:29

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
