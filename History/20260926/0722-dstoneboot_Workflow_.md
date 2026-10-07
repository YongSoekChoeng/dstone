# dstoneboot_Workflow_

> 생성일시: 2026-09-26 07:22

## 질문

dstone-boot > Workflow 테스트 메뉴에서 workflowId 를 선택할 때마다 message/variables 등에 테스트를 진행할 수 있는 기본샘플 값들을 자동으로 채워줘.

---

## 답변

Workflow YAML 주석에 적힌 권장 샘플값들을 모았습니다. 이제 프롬프트 변수 쓰는 Agent를 확인합니다.

---

## 답변

Workflow 테스트 화면에서 workflowId를 고를 때마다 message와 variables 칸에 테스트용 샘플값이 자동으로 채워집니다. 브라우저에서 직접 눌러보지는 않았고, JS 파일 문법 검사만 통과했습니다.

**변경 내용**
- `dstone-boot/src/main/webapp/ai/assets/js/workflow.js`
  - `WORKFLOW_SAMPLES` 맵을 추가했습니다. workflowId마다 message, variables, 주의사항(note)이 들어 있습니다.
  - 등록된 15개 Workflow(`sample-*` 14개 + `testApp-sdlc`)의 값을 모두 넣었습니다. 각 YAML 맨 위 주석에 적힌 테스트 예시를 그대로 옮겼습니다.
    - variables는 `sample-foreach-parallel`에만 `{"sqlList": [...]}`, `sample-router-multiway`와 `sample-supervisor-verdict-gate`에만 `{"role": "친절한 상담원"}`이 들어갑니다.
  - 새로 만든 `fillSample()`이 드롭다운을 바꿀 때와 목록을 처음 불러올 때 실행됩니다. 이미 입력해 둔 값은 덮어쓰고, 맵에 없는 workflowId를 고르면 두 칸을 비웁니다.
- `dstone-boot/src/main/webapp/WEB-INF/views/ai/workflow/workflow.jsp`: message 아래에 `#workflow-sample-note` 안내 문구를 추가했습니다. "SELEC 오타는 일부러 넣은 것", "APP_HOME 경로에 맞게 수정" 같은 주의사항이 여기에 표시됩니다.
- 문서: `docs/06.dstone-boot.md` §10.4와 `docs/09.dstone-ai-engine.md` §7.8에 이 기능을 설명하고, Workflow를 추가하면 이 맵도 함께 고쳐야 한다고 적었습니다.

**직접 확인이 필요한 샘플값**
- **`sample-mcp-filesystem-list`**: 경로를 WSL/k8s 기준인 `/app/dstone/...`로 넣었습니다. Windows 로컬에서 쓰려면 경로를 바꿔야 합니다.
- **RAG 샘플 2종**(`sample-agent-rag-augmented`, `sample-tool-rag-search`): 검색어에 "Spring Batch"를 임시로 넣었습니다. 실제로 적재한 문서에 있는 키워드로 바꿔야 결과가 나옵니다.

**다른 방식:** 지금은 샘플값을 화면 쪽 JS에만 두었기 때문에, 엔진에 Workflow YAML을 새로 추가하면 이 맵에도 한 줄을 따로 넣어야 합니다. YAML에 `sample:` 필드를 두고 `GET /api/ai/workflow`가 함께 내려주게 하면 YAML 하나만 관리하면 됩니다. 원하시면 그렇게 바꾸겠습니다.
