# pasted_content_id9e8

> 생성일시: 2026-11-23 09:50

## 질문

<pasted_content id="9e8c">
Workflow 디자이너 에서 [자동 배치]버튼 클릭 시 배치되는 모양이 너무 불편하게 배치되는 것 같아.
아래와 같은 부분을 고려하여 자동배치가 되었으면 좋겠어.
1. 스탭별 순서가 위에서 아래로 배치되되 한 눈에 들어와야 한다.
2. 각 노드별 스페이스가 너무 넓지 않아야 한다. 너무 넓으니까 전체 플로어를 볼 때 스크롤링을 많이 하게 됨.
3. 화살표는 너무 겹치면 어떤 노드에서 어떤 노드로 이어지는지 헷갈리므로 왠만하면 화살표를 겹치게 하지 않는다.
</pasted_content id="9e8c">

---

## 답변

실제 Workflow 모양을 보고 자동 배치 알고리즘을 다시 짜려고 합니다. 정의 파일 몇 개를 확인하겠습니다.

---

## 답변

Chromium 헤드리스는 있으니, 새 배치 함수를 만든 뒤 실제 Workflow로 그림을 찍어 보며 다듬겠습니다. 먼저 dagre가 돌려주는 값 모양을 확인합니다.

---

## 답변

헤드리스 Chromium으로 화면을 찍을 수 있도록, API를 흉내 내는 작은 서버를 scratchpad에 만들고 그다음 배치 코드를 고치겠습니다.

---

## 답변

Baseline(현재 배치)부터 찍어 봅니다.

---

## 질문

[Image: original 1600x2400, displayed at 1333x2000. Multiply coordinates by 1.20 to map to original image.]

---

## 답변

지금 배치는 40개 step이 넓게 흩어지고 긴 선이 많이 겹칩니다. 이제 배치 함수를 다시 씁니다.

---

## 답변

Now I'll write the new layout code (in a separate file first, then splice it in place of lines 1847–1879).

---

## 답변

Now the button handler (it previously cleared vertices/ports after layout) and the FAIL label position.

---

## 질문

[Image: original 1600x2400, displayed at 1333x2000. Multiply coordinates by 1.20 to map to original image.]

---

## 답변

나아졌지만 노드가 계단처럼 왼쪽으로 밀려 내려가며 폭을 낭비합니다. dagre 옵션 조합별 그림 크기를 node로 비교해 보겠습니다.

---

## 질문

[Image: original 1600x2400, displayed at 1333x2000. Multiply coordinates by 1.20 to map to original image.]

---

## 답변

줄기가 왼쪽에 곧게 서고 반복 화살표는 바깥 길로 분리되었습니다. 겹침을 자세히 보려고 큰 해상도로 다시 찍어 봅니다.

---

## 답변

Now rewriting routeBackEdges (blocked → go through gap tracks) and routeFailEdge, plus a track allocator.

---

## 답변

라우터는 깔끔해졌습니다. sm-workflow 전체를 다시 봅니다.

---

## 답변

[자동 배치]를 다시 만들었습니다. 수정은 `dstone-ai-engine/src/main/resources/static/ai/assets/js/workflow-designer.js`의 `autoLayout()`와 새로 넣은 보조 함수들입니다. 헤드리스 Chromium으로 sm-workflow(step 40개)와 샘플 Workflow 7개를 그려 결과를 확인했습니다. 다만 화면에서 직접 써 보지는 않았고, 해당 테스트 환경에 한글 글꼴이 없어 노드 글자는 확인하지 못했습니다.

**원인**
- 거의 모든 step에서 FAIL로 가는 실패 화살표와 앞 step으로 되돌아가는 반복 화살표가 배치 계산에 같이 들어가 있었습니다.
- dagre가 그 선들이 지나갈 자리를 비워 두느라 노드가 계단처럼 밀렸고, 그림이 옆으로 크게 퍼졌습니다.

**요청하신 세 가지 반영**
1. **위에서 아래로, 한 눈에**
   - 흐름을 따라 내려가는 화살표만으로 노드 자리를 정합니다.
   - `next`로 이어지는 줄기는 왼쪽에 곧은 한 줄로 서고, 갈래 step은 그 옆에 붙습니다.
2. **간격 줄이기**
   - 노드 사이 50→36px, 줄 사이 70→48px로 줄였습니다.
   - sm-workflow는 계단 모양이 사라져 이전보다 훨씬 좁아졌습니다.
3. **화살표가 겹치지 않게**
   - **반복 화살표:** 그림 바깥 왼쪽·오른쪽에 선마다 따로 세로 길을 줍니다. 위아래 구간이 겹치는 선끼리는 같은 길을 쓰지 않고, 짧은 선이 안쪽에 섭니다. 옆에 다른 노드가 있어 옆으로 나갈 수 없으면 줄 사이 빈 곳의 비어 있는 높이로 돌아갑니다.
   - **실패 화살표:** FAIL을 오른쪽 아래에 두고, 각 step 오른쪽에서 나와 FAIL 위의 세로줄 하나로 모읍니다. "실패" 글자는 선이 모이는 곳에서 겹치지 않도록 나가는 쪽 가까이에 둡니다.
   - **여러 줄을 건너가는 화살표:** dagre가 비워 둔 길을 꺾는 점으로 따라가서 다른 선이나 노드와 포개지지 않습니다.
   - **ROUTER·APPROVAL 갈래:** 맨 왼쪽·맨 오른쪽 갈래는 옆 연결점으로 나가게 해서 선이 서로 가로지르지 않습니다.

**바뀐 동작**
- 이전에는 [자동 배치]를 누르면 꺾는 점이 지워졌습니다. 이제는 자동 배치가 정한 꺾는 점과 연결점이 들어가고, 노드 위치와 함께 저장됩니다.
- 직각 선이 노드를 피해 갈 때 두는 여유를 16px에서 10px로 줄였습니다. 16px이면 줄 사이 꺾는 점 근처에서 작은 고리가 생겼습니다. 이 값은 [자동 배치]를 누르지 않은 그림에도 적용됩니다.
- 자동 배치 뒤 노드를 손으로 옮기면, 그 노드에 이어진 선은 원래 꺾는 점을 그대로 지납니다. 모양이 어색하면 [자동 배치]를 다시 누르거나 점을 두 번 눌러 지우면 됩니다.

**남은 한계**
- 노드 사이를 서로 가로지르는 선은 남습니다. 겹침은 피했지만 교차까지 모두 없애지는 못했습니다.
- 한 step에서 여러 갈래가 나갈 때는 아래 연결점에서 잠깐 한 줄로 나오다 갈라집니다.
- 같은 노드로 들어가는 선은 마지막 부분이 모입니다.

같이 고친 문서:
- 꺾는 점 안내 문구
- `docs/09.dstone-ai-engine.md`의 디자이너 설명
- `.claude/docs/ai-engine-features.md`의 배치 방식 설명

작업 트리에 있던 Oracle 쪽 변경과 함께 아무것도 커밋하지 않았습니다.
