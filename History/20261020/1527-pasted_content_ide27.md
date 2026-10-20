# pasted_content_ide27

> 생성일시: 2026-10-20 15:27

## 질문

<pasted_content id="e27d">
dstone-ai-engine 의 workflow 및 step들을 UI적으로 처리할 수 있는 UI관련 라이브러리가 있을까?
설정된 workflow 랜더링.
Drag And Drop 을 이용한 설정.
화살표를 이용한 관계연결 등.
</pasted_content id="e27d">

---

## 답변

네, 있습니다. 다만 dstone 화면은 JSP + jQuery 기반이고 빌드 단계가 없으며, 폐쇄망 SI 현장을 생각해 라이브러리를 직접 복사해 두는 방식입니다(노드 맵의 `knowledge/assets/lib/cytoscape.min.js`처럼). 그래서 **빌드 없이 `<script>` 한 줄로 붙는 라이브러리인지**가 가장 중요한 기준입니다.

## 후보 비교

| 라이브러리 | 라이선스 | 빌드 없이 사용 | 렌더링 | 드래그로 노드 추가 | 화살표 연결 | 맞는 경우 |
|---|---|---|---|---|---|---|
| **AntV X6** | MIT | ○ (UMD 배포본) | ◎ | ◎ (Dnd/Stencil 플러그인) | ◎ (포트, 연결 규칙 검사) | 편집기를 제대로 만들 때 |
| **Drawflow** | MIT | ◎ (JS 1개, 의존성 없음) | ○ | ◎ | ○ | 작고 빠르게 만들 때 |
| **Cytoscape.js** + `edgehandles` + `dagre` | MIT | ◎ (이미 들어와 있음) | ◎ (자동 배치) | △ (직접 구현) | ○ (edgehandles) | 보기 전용, 실행 상태 표시 |
| **maxGraph** (mxGraph/draw.io 후속) | Apache 2.0 | ○ | ◎ | ○ | ◎ | draw.io 같은 편집기 |
| **JointJS** (오픈소스 코어) | MPL 2.0 | ○ | ◎ | ○ (고급 기능은 유료 JointJS+) | ◎ | |
| **React Flow (xyflow)** | MIT | ✕ (React 빌드 필요) | ◎ | ◎ | ◎ | 화면을 React로 새로 만들 때. 생태계는 가장 큼 |
| Rete.js / Baklava / LiteGraph | MIT | △ | ○ | ○ | ○ | 데이터 흐름(포트 대 포트) 편집기. 이 엔진의 상태 머신과는 결이 다름 |
| GoJS | 상용 | ○ | ◎ | ◎ | ◎ | 라이선스 비용 때문에 제외 |
| bpmn-js | 소스 공개 (로고 유지 조건) | ○ | ◎ | ◎ | ◎ | BPMN 표기를 강제하므로 과함 |
| Mermaid | MIT | ◎ | ○ | ✕ | ✕ | 문서용 정적 그림 |

## 추천

1. **1단계, 렌더링(보기 전용)은 Cytoscape.js + `cytoscape-dagre`로 합니다.** 이미 복사해 둔 라이브러리이고 노드 맵에서 쓰는 방식도 검증돼 있습니다. step이 노드, `next`/`onFailure`/`routes`가 화살표, `END`/`FAIL`이 종착 노드가 됩니다. 실행 이력(`/api/ai/workflow/executions/{id}`)을 겹쳐서 지금 어느 step에 있는지, 어디서 실패했는지, 어느 승인 단계에서 기다리는지 색으로 보여 주면 효과가 가장 큽니다.
2. **2단계, 편집기는 AntV X6를 권합니다.** 빌드 없이 붙고, 왼쪽 팔레트(AGENT/SUPERVISOR/ROUTER/TOOL/APPROVAL)에서 끌어다 놓기, 포트 사이 화살표 연결, 연결 가능 여부 검사(`validateConnection`), 되돌리기와 미니맵을 모두 기본 플러그인으로 제공합니다. 더 가볍게 시작하고 싶다면 Drawflow도 괜찮지만, 연결 규칙 검사와 자동 배치는 직접 만들어야 합니다.
   - X6 v2의 UMD 배포 파일명은 붙이기 전에 한 번 확인해야 합니다.

## UI보다 먼저 정해야 할 것

라이브러리보다 아래 항목이 더 큰 결정입니다.

- **step 구조를 내보내는 API가 아직 없습니다.** `GET /api/ai/workflow`는 id, 설명, input 스키마, version만 돌려줍니다. 렌더링하려면 `WorkFlowDefinition`(steps, state, routes)을 그대로 내보내는 API가 하나 필요합니다. 1단계는 이것만 추가하면 됩니다.
- **정의 파일은 jar 안의 `classpath:definitions/`에서 읽습니다.** 그래서 UI에서 편집한 결과를 저장할 곳이 없습니다. 파일 경로(`file:`)로 읽게 바꿀지, DB에 저장할지, YAML만 생성해서 내려받게 할지를 정해야 합니다. 가장 안전한 시작은 "YAML 생성 + 다운로드/복사"입니다.
- **YAML과 그래프를 오가는 변환이 필요합니다.** 그래프에서 YAML을 다시 만들면 YAML 주석이 사라지고, 노드 좌표를 따로 저장할 곳도 필요합니다(별도 `.layout.json` 파일이나 자동 배치). `with`/`save`/Picoschema처럼 화살표로 표현되지 않는 내용은 노드를 눌렀을 때 뜨는 속성 패널에서 편집하게 됩니다.
- **검증은 서버에 맡깁니다.** `WorkFlowRegistry`에 부팅 시 검증 로직이 이미 있습니다. 이를 "정의 검증" API로 열어 주면 UI가 같은 규칙을 다시 구현할 필요가 없습니다.

원하시면 1단계(정의 조회 API + Cytoscape 렌더링 화면 + 실행 상태 표시)부터 바로 만들어 보겠습니다.
