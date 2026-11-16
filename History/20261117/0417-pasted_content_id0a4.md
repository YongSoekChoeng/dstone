# pasted_content_id0a4

> 생성일시: 2026-11-17 04:17

## 질문

<pasted_content id="0a47">
dstone-boot > knowledge > 노드 맵 에서  
[관계트리 따로 보기]를 윈도우 팝업으로 만드는 건 어떨까?
이유는...
/defaultLink.do?defaultLink=knowledge/index 같은 화면에서 [관계트리 따로 보기]용 링크를 사용하는데 뜬금없이 노드 맵 화면이 먼저 뜨면서 [관계트리 따로 보기]팝업이 떠. 레이어팝업이라서 그런 것은 이해하는데 
그래서 보기에 편하지 않아. 그래서 윈도우 팝업으로 만들고 노드 맵 메뉴를 포함한 다른 메뉴(화면)에서도 이  [관계트리 따로 보기]윈도우팝업 을 사용하게 하면 어떨까?
</pasted_content id="0a47">

---

## 답변

먼저 지금 노드 맵의 [관계트리 따로 보기]가 어떻게 만들어져 있는지 확인해 볼게요.

---

## 답변

경계를 확인했어요. 이제 파이썬으로 범위를 잘라 세 파일(공용·맵·트리)을 만든 다음, 연결 부분은 직접 고치겠습니다.

---

## 답변

Now I'll edit `graph.js`'s top section and `init`.

---

## 답변

Now I'll add the shared helpers for opening the tree window to `knowledge-common.js`.

---

## 답변

Now I'll write the tree window's script (`tree.js`). It's built from the original tree code, with the window-specific parts adjusted.

---

## 답변

Only the indentation was off (three tabs). I'll fix it and run again.

---

## 답변

Now I'll write the top of the file with the Write tool and stitch the two parts together.

---

## 답변

Syntax checks pass. I'll also do a quick scan for any calls to functions that are no longer defined.

---

## 답변

JS is done. Next come the JSPs: I'll remove the layer popup from `graph.jsp`, split the source popup into a shared include, and add the new `tree.jsp`.

---

## 답변

Now the tree window page `tree.jsp`.

---

## 답변

Now the CSS. I'll replace the old layer-popup rules with layout rules for the tree window.

---

## 답변

Next I'll find the docs that need updating.

---

## 답변

코드와 문서 수정은 끝났어요. 이제 dstone-boot를 빌드해서 7181 포트에 시험 사본을 띄우고, 브라우저로 실제 동작을 확인하겠습니다.

---

## 답변

7181 시험 인스턴스가 떴어요. 프록시 API(POST)가 302를 돌려주고 있어서 이유를 확인하는 중입니다.

---

## 답변

맵이 그려졌는지 판단하는 조건이 맞지 않았던 것 같아요. 알림 줄 내용을 찍어 보겠습니다.

---

## 답변

모든 확인 항목을 통과했어요. 창이 어떻게 보이는지 스크린샷으로도 확인하겠습니다.

---

## 답변

관계 트리 따로 보기를 윈도우 팝업으로 바꿨어요. 이제 노드 맵, 영향도, 미사용 탭 어디서 눌러도 노드 맵을 거치지 않고 같은 창 하나에 트리가 뜹니다. 7181에 띄운 새 빌드로 헤드리스 브라우저에서 확인했고, 스크립트 오류는 없었습니다.

**먼저 사과드릴 것이 있어요.** 7081에서 돌고 있는 dstone-boot(PID 2562)는 `target/dstone-boot.war`를 직접 열어 둔 채 실행 중인데, 제가 그 파일을 그 자리에서 다시 빌드해서 덮어썼습니다. 지금은 괜찮아 보여도, 아직 읽지 않은 클래스를 나중에 불러올 때 7081이 오류를 낼 수 있어요. 7081을 재시작하면 새 화면도 같이 반영되니 재시작하는 쪽을 권합니다. 아까 오래 걸린 건 빌드 문제가 아니었어요. 빌드 뒤에 디스크 전체를 뒤지는 `find /`를 붙여 둔 탓이었고, 그 작업은 바로 멈췄습니다.

**바뀐 동작**
- **창은 하나만 씁니다.** 이미 떠 있으면 내용만 바뀌고 창이 앞으로 나옵니다.
- **트리를 여는 곳**
  - 노드 맵: [관계 트리 따로 보기], 상세에 있는 메소드·SQL 이름, "이름으로 찾기"에 `클래스.메소드`를 적은 경우
  - 영향도: 링크 이름을 "노드 맵에서 보기"에서 **"관계 트리 보기"**로 바꿨습니다.
  - 미사용 탭: 이름을 누르면 엽니다.
- **창 안에서 쓰는 기능**
  - 깊이, 보일 계층 칩, 다시 그리기, 소스·SQL 팝업은 예전과 같습니다.
  - [이 노드를 가운데로 다시 그리기]를 한 뒤에는 브라우저의 뒤로 가기로 앞의 트리에 돌아갈 수 있습니다.
  - 꺼 둔 계층 칩은 창을 닫았다 열어도 그대로 남습니다.
  - [전체 화면]은 브라우저 전체 화면으로 바뀌었고, [닫기]는 창을 닫습니다.
- **팝업이 막힐 때:** 노드 맵의 알림 줄에 창을 여는 링크가 대신 나옵니다. 영향도와 미사용 탭의 링크는 새 탭으로 열립니다.
- 영향도나 미사용 탭에서 받은 예전 형식의 노드 맵 주소(`#…&tree=`)로 들어오면, 이제 트리 없이 맵만 보입니다.

**검증 결과 (HI-Japan 리비전 2, `ObjectManager` 클래스)**
- 맵에서 버튼을 누르니 새 창이 열렸고, 노드 85개와 선 114개가 그려졌습니다.
- 다른 화면 형식의 링크로 다시 열어도 창은 2개 그대로였고, 같은 창에 새 트리가 그려졌습니다.
- 뒤로 가기와 계층 칩 저장도 확인했습니다.

확인할 때 7181은 로그인이 필요해서, 시험 스크립트가 API 호출을 4081로 바로 넘기고 CSS·JS 파일은 소스 폴더에서 읽게 했어요. 그래서 로그인 화면을 거치는 과정은 확인하지 못했습니다. 화면(JSP)은 7181의 새 빌드 그대로입니다.

**코드 정리:** 맵과 트리 창이 같이 쓰는 부분(계층 색, 노드 상세, 소스 팝업)은 `graph-common.js`로 옮겼어요. 새 파일은 `tree.jsp`, `tree.js`, `codeModal.jsp`입니다. 화면 안내 문구, `docs/11`, `.claude/docs/knowledge-ui.md`도 고쳤습니다.

아직 커밋하지 않았어요. 7081 재시작과 커밋·push를 진행할까요?
