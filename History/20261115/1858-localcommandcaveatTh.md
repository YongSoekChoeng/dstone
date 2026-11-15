# localcommandcaveatTh

> 생성일시: 2026-11-15 18:58

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-boot>knowledge 에 웰컴페이지로서 대시보드를 만들고 싶어.
네 계획을 먼저 말해줘.

---

## 답변

먼저 현재 knowledge 화면 구조를 살펴보겠습니다.

---

## 답변

대시보드에 쓸 수 있는 dstone-knowledge API가 무엇인지 확인합니다.

---

## 답변

## 웰컴 대시보드 계획

### 지금 상태
- Knowledge 콘솔의 첫 화면(`brandHome`, 사이드바 첫 메뉴, `left.jsp` 링크)은 `knowledge/index`, 즉 **프로젝트 · 분석** 작업 화면입니다. 들어오자마자 등록 폼과 리비전 목록이 보입니다.
- 서버 전체를 한눈에 보여 주는 API는 따로 없습니다. 있는 것은 `/api/system/health`(DB·스키마 상태), `/api/system/embedding`(임베딩 진행률·작업기 상태), `/api/projects`(프로젝트만 있고 최근 리비전은 없음), `/api/projects/{id}/revisions` 정도입니다.

### 화면 구성 (안)
`knowledge/dashboard/dashboard` 화면을 새로 만들고, 위에서부터 이렇게 배치합니다.

1. **시스템 상태 카드 4개**: 서버(UP / DB_DOWN / SCHEMA_NOT_READY), 프로젝트 수, 분석 중인 작업 수, 임베딩 진행률(완료 %, 대기·실패 건수, 작업기 켜짐 여부)
2. **프로젝트 현황 표**: 프로젝트마다 최근 리비전, 상태(READY/RUNNING/FAILED 배지), 분석 시각, 파일·메소드·진입점 수, 호출 해석률. 해석률이 낮으면 빨간색으로 표시합니다(요약 화면의 `verdictBox`와 같은 기준). 행을 누르면 프로젝트 · 분석 화면의 해당 리비전으로 갑니다.
3. **최근 분석 작업**: 최근 작업 5~10건의 상태, 걸린 시간, 실패 사유 첫 줄
4. **바로 가기**: 검색 · 영향도 · 노드 맵 · 리비전 비교 · 사용 안내로 가는 버튼. 처음 쓰는 사람에게는 "프로젝트 등록부터" 안내를 띄웁니다(프로젝트가 0개일 때).

### 서버 쪽 (추천: 집계 API 하나 추가)
- 기존 API만 쓰면 프로젝트마다 `revisions`와 리비전 요약을 따로 불러야 해서 N+1 호출이 됩니다. 요약 API는 무거운 편이기도 합니다.
- 그래서 dstone-knowledge에 **`GET /api/dashboard`** 하나를 추가하려고 합니다. 구성은 `DashboardController` + `DashboardService` + `sqlmap/.../DashboardDao.xml`(`BaseDao` 상속, 읽기 전용 SQL)이고, 프로젝트별 최신 리비전·건수·해석률과 최근 작업 목록을 한 번에 돌려줍니다. 해석률 계산식은 `RelationDao.xml` `insertMetrics`에 이미 저장된 값을 그대로 읽고 새로 계산하지 않습니다.
- 브라우저는 지금처럼 `KnowledgeProxyController`(`/knowledge/api/call.do`)를 거칩니다. `/api/` 아래 경로라서 프록시는 고칠 필요가 없습니다.

### dstone-boot 쪽 변경
- 추가: `WEB-INF/views/knowledge/dashboard/dashboard.jsp`, `knowledge/assets/js/dashboard.js`, `knowledge.css`에 대시보드용 클래스 몇 개(색은 `console.css` 변수만 사용)
- 수정: `console/sidebar.jsp`(맨 위에 "대시보드" 메뉴, `brandHome` 변경), `common/left.jsp` 링크, `guide.jsp`(1장 화면 표에 한 줄 추가)
- 그래프·차트 라이브러리는 추가하지 않습니다. 진행률 막대와 숫자 카드는 HTML/CSS로 그립니다(폐쇄망 원칙과 기존 화면 스타일에 맞춤).
- Java는 람다 없이, Lombok 없이 작성합니다. 주석은 쉬운 한글로 씁니다.

### 검증과 문서
- 4181 시험 사본(임베딩 끔)과 7181 dstone-boot 사본을 띄우고, headless Chromium으로 1440px·390px에서 확인합니다. 콘솔 오류가 없는지, 프로젝트가 0개일 때와 분석 중일 때 화면이 어떻게 나오는지도 봅니다.
- `.claude/docs/knowledge-ui.md`, `docs/11.dstone-knowledge.md`(API 목록)을 고칩니다.

### 정해 주셔야 할 것
1. **집계 API(`/api/dashboard`)를 새로 만들어도 될까요?** 아니면 기존 API만 써서 화면 쪽에서 모을까요? 화면 쪽에서 모으면 서버를 고칠 일은 없지만, 프로젝트가 많아질수록 느려집니다.
2. **진입점을 바꿀까요?** 사이드바 브랜드, `left.jsp`, AI 채팅 화면 링크가 모두 대시보드를 가리키게 하고, "프로젝트 · 분석"은 두 번째 메뉴로 내리는 안입니다. AI 채팅 화면의 링크는 "프로젝트 ID"를 설명하는 링크라서 지금처럼 `knowledge/index`에 둘 생각입니다.
3. 위 4개 영역 말고 꼭 넣고 싶은 내용(예: 업로드한 일반 문서 컬렉션 현황, 규칙 적용 진단 요약)이 있으면 알려 주세요.

정해 주시면 바로 진행하겠습니다.
