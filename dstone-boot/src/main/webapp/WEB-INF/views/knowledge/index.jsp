<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
%>
<!DOCTYPE HTML>
<html>

<jsp:include page="common/head.jsp"></jsp:include>

<body class="ai-body">
	<div id="page-wrapper">

		<jsp:include page="common/header.jsp"></jsp:include>

		<!-- Main -->
		<div id="main">
			<div class="container">
				<div class="row main-row">
					<div class="col-12">
			<section class="cs-page-head">
				<p class="cs-eyebrow">코드 분석</p>
				<h2>프로젝트 · 분석</h2>
				<p>왼쪽에서 프로젝트와 리비전을 고르면, 오른쪽에 그 리비전의 분석 결과가 탭으로 나옵니다.</p>
			</section>

			<details class="kn-howto">
				<summary>처음이신가요? 이 화면은 이렇게 씁니다 (눌러서 펴기)</summary>
				<ol>
					<li><b>프로젝트 고르기 (왼쪽 위)</b> — 목록에서 프로젝트를 누릅니다. 새 프로젝트는 "프로젝트 등록 / 수정"을 펴서 projectId(영문), 이름, 소스 폴더를 적고 "등록"을 누릅니다.
						소스 폴더는 내 PC가 아니라 <b>dstone-knowledge 서버에서 보이는 경로</b>입니다. 고른 프로젝트의 값을 고쳐 다시 "등록"하면 수정됩니다.</li>
					<li><b>분석 시작 (왼쪽)</b> — 칸은 비워 둔 채 "분석 시작"만 누르면 됩니다. 두 번째부터는 "바뀐 파일만"을 체크하세요. 훨씬 빨리 끝납니다.
						단계별 진행 상황이 3초마다 새로 보이고, 멈추고 싶으면 "취소"를 누릅니다.</li>
					<li><b>리비전 고르기 (왼쪽 아래)</b> — 상태가 <code>READY</code>인 리비전을 누르면 오른쪽에 결과가 열립니다.</li>
					<li><b>개요 탭</b> — <b>"한눈에 보기"</b>가 모두 초록 ✔이면 그대로 쓰면 됩니다. 주황 <b>!</b>나 빨간 <b>✖</b>가 있으면 그 줄에 무엇을 하면 되는지 적혀 있습니다.
						"파일 구성" 표는 파일을 화면 · 서버 코드 · SQL · 설정 · 기타로 나누고, 서버 코드(Java)는 컨트롤러 · 서비스 · DAO 등으로 몇 개씩인지 보여 줍니다. 줄을 누르면 아래에 그 파일 목록이 나옵니다. 탭 이름 옆의 <b>⚠</b>는 그 탭에 살펴볼 것이 있다는 뜻입니다.</li>
					<li><b>연결 품질 탭</b> — 누가 무엇을 부르는지 얼마나 풀었는지 봅니다. 맨 위 "적용 진단"은 모자란 곳을 해결 방법별(규칙 파일, jar 위치 등)로 나눠 보여 줍니다. "풀지 못한 참조의 이유" 표의 줄을 누르면 바로 아래에 그 조건으로 조회되어, 무엇을 못 찾았는지 파일 · 줄 번호까지 나옵니다.</li>
					<li><b>미사용 탭</b> — 있지만 아무도 쓰지 않는 테이블 · SQL · 클래스 · 메소드 · 화면입니다. 숫자를 누르면 목록이 나오고, 줄을 누르면 노드 맵에서 엽니다.</li>
					<li><b>물어보기</b> — 왼쪽 메뉴의 "검색 · 문서", "영향도", "노드 맵", "리비전 비교"로 갑니다. 이 화면들은 분석이 끝난 가장 최근 리비전을 알아서 골라 둡니다.</li>
				</ol>
				<p>리비전은 프로젝트마다 최근 5개까지만 남습니다. 분석이 끝날 때마다 오래된 것부터 지워지고, 더 줄이고 싶으면 리비전 목록 아래의 "보관 정책"을 씁니다.
					연습용으로는 저장소에 든 게시판 표본(projectId <code>struts-app</code>, 소스 폴더 <code>/app/dstone/dstone-knowledge/src/test/resources/samples/struts-app</code>)을 등록해 보세요.
					파일 11개짜리라 몇 초면 끝납니다. 일부러 jar 없이 넣어 둔 표본이라 "한눈에 보기"에 빨간 줄이 나와도 정상입니다.</p>
				말뜻과 화면별 설명은 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/guide/guide">사용 안내</a>에 있습니다.
			</details>

			<!-- 서버 상태와 임베딩(모든 프로젝트를 통틀어). 자세한 내용은 펴서 본다 -->
			<details class="kn-topbar" id="kn-embed-box">
				<summary>
					<span class="kn-topbar-item">서버 <span id="kn-health"></span></span>
					<span class="kn-topbar-item">임베딩 <span id="kn-embed-badge"></span></span>
					<span id="kn-embed-bar" class="kn-embed-bar kn-topbar-bar" style="display:none;"><div></div></span>
					<span class="kn-topbar-text"><span id="kn-embed-message" class="kn-message"></span></span>
				</summary>
				<div class="kn-topbar-body">
					<p class="ai-hint">뜻으로 찾는 검색에 필요한 임베딩은 분석이 끝난 뒤 서버 안에서 따로 진행됩니다. 여기서는 모든 프로젝트를 통틀어 얼마나 남았는지 봅니다.
						dstone-knowledge 서버가 떠 있는 동안에만 진행되고, 서버를 내렸다 올려도 남은 것부터 이어서 합니다.
						예상 남은 시간은 최근 10분의 속도로 어림한 값입니다(검색이 끼어들면 그동안 양보해서 더 걸립니다).
						<button type="button" id="kn-embed-refresh" class="kn-btn kn-plain kn-embed-refresh">지금 다시 보기</button></p>
					<div id="kn-embed-cards" class="kn-cards"></div>
					<div id="kn-embed-error" class="kn-message kn-error" style="display:none;"></div>
					<div id="kn-embed-projects"></div>
				</div>
			</details>

			<div class="kn-split">

				<!-- 왼쪽: 프로젝트 · 분석 실행 · 리비전 -->
				<aside class="kn-side">
					<section class="ai-panel kn-side-panel">
						<h3>프로젝트</h3>
						<div id="kn-project-list" class="kn-pick-list"></div>
						<div id="kn-project-message" class="kn-message"></div>
						<details class="kn-howto kn-project-form" id="kn-project-form">
							<summary>프로젝트 등록 / 수정</summary>
							<div class="kn-field"><label for="kn-new-project-id">projectId</label><input type="text" id="kn-new-project-id" placeholder="영문" /></div>
							<div class="kn-field"><label for="kn-new-project-name">이름</label><input type="text" id="kn-new-project-name" placeholder="화면에 보일 이름" /></div>
							<div class="kn-field"><label for="kn-new-project-path">소스 폴더</label><input type="text" id="kn-new-project-path" placeholder="서버의 경로. 예: /app/sampleApps/anybiz_prd" /></div>
							<div class="kn-field"><label for="kn-new-project-classpath">라이브러리 위치</label><input type="text" id="kn-new-project-classpath" placeholder="(선택) jar 가 소스 폴더 밖에 있을 때만, 쉼표로 구분" /></div>
							<p class="ai-hint">같은 projectId 로 다시 등록하면 수정입니다. 소스 폴더 안의 jar 는 알아서 찾으므로, 라이브러리 위치는 jar 가 다른 곳에 있을 때만 적습니다.</p>
							<div class="ai-form-actions">
								<button type="button" id="kn-new-project-btn" class="kn-plain">새로 쓰기</button>
								<button type="button" id="kn-project-save-btn">등록</button>
							</div>
						</details>
					</section>

					<section class="ai-panel kn-side-panel" id="kn-analysis-panel" style="display:none;">
						<h3>분석 <span id="kn-selected-project" class="workflow-job-id"></span></h3>
						<div class="kn-field"><label for="kn-revision-label">리비전 라벨</label><input type="text" id="kn-revision-label" placeholder="비우면 지금 시각으로 새 리비전" /></div>
						<div class="kn-field"><label for="kn-rerun-from">이 단계부터 다시</label>
							<select id="kn-rerun-from">
								<option value="">(쓰지 않음)</option>
								<option>SCAN</option>
								<option>DECLARE</option>
								<option>RESOURCE</option>
								<option>RESOLVE</option>
								<option>LINK</option>
								<option>SEMANTIC</option>
								<option>DOCUMENT</option>
							</select></div>
						<label class="kn-check"><input type="checkbox" id="kn-incremental" /> 바뀐 파일만 다시 분석 (증분 분석)</label>
						<p class="ai-hint">이미 있는 라벨을 적으면 그 리비전을 이어서 분석합니다(끝난 단계는 건너뜁니다).</p>
						<div class="ai-form-actions">
							<button type="button" id="kn-analysis-start-btn">분석 시작</button>
						</div>
						<div id="kn-analysis-message" class="kn-message"></div>

						<div id="kn-job-box" style="display:none;">
							<div class="workflow-status-row">
								<span id="kn-job-id" class="workflow-job-id">-</span>
								<span id="kn-job-badge"></span>
								<button type="button" id="kn-job-cancel-btn" class="kn-plain">취소</button>
							</div>
							<div id="kn-job-progress" class="workflow-status-label"></div>
							<div id="kn-job-passes" class="kn-scroll"></div>
							<div id="kn-job-errors"></div>
						</div>

						<div class="kn-section-title">리비전</div>
						<div id="kn-revision-list" class="kn-pick-list"></div>
						<div id="kn-revision-message" class="kn-message"></div>
						<details class="kn-howto">
							<summary>보관 정책</summary>
							<div class="kn-retention-row">
								<label for="kn-retention-keep">최근</label>
								<input type="number" id="kn-retention-keep" min="1" value="5" />
								<label for="kn-retention-keep">개만 남기고 지우기</label>
								<button type="button" id="kn-retention-btn" class="kn-plain">적용</button>
							</div>
						</details>
					</section>
				</aside>

				<!-- 오른쪽: 고른 리비전의 결과 -->
				<section class="ai-panel kn-detail">
					<div id="kn-detail-empty" class="kn-empty">
						<p>왼쪽에서 프로젝트를 고른 뒤 리비전을 누르면 여기에 분석 결과가 나옵니다.</p>
					</div>
					<div id="kn-summary-panel" style="display:none;">
						<div class="kn-detail-head">
							<h3>리비전 <span id="kn-summary-title" class="workflow-job-id"></span> <span id="kn-summary-badge"></span></h3>
							<button type="button" id="kn-summary-delete-btn" class="kn-plain">이 리비전 지우기</button>
						</div>
						<div class="kn-tabs" id="kn-tabs" role="tablist">
							<button type="button" class="kn-tab kn-tab-on" data-tab="overview" role="tab">개요</button>
							<button type="button" class="kn-tab" data-tab="connect" role="tab">연결 품질</button>
							<button type="button" class="kn-tab" data-tab="content" role="tab">분석 내용</button>
							<button type="button" class="kn-tab" data-tab="unused" role="tab">미사용</button>
							<button type="button" class="kn-tab" data-tab="history" role="tab">실행 기록</button>
						</div>

						<div class="kn-tab-panel" data-panel="overview">
							<div id="kn-summary-cards" class="kn-cards"></div>
							<div id="kn-overview"></div>
						</div>

						<div class="kn-tab-panel" data-panel="connect" style="display:none;">
							<p class="ai-hint">분석이 "누가 무엇을 부르는지"를 얼마나 풀었는지 봅니다. 호출 관계 · 영향도 · 리비전 비교가 모두 이 결과를 따라갑니다.</p>
							<div id="kn-connect"></div>
							<!-- "풀지 못한 참조 살펴보기". 화면이 "풀지 못한 참조의 이유" 표 바로 아래로 옮겨 붙인다(project.js) -->
							<div id="kn-unresolved-home" style="display:none;">
								<div id="kn-unresolved-panel" class="kn-subpanel">
									<div class="kn-section-title kn-inline-title">살펴보기 <span id="kn-unresolved-count" class="workflow-status-label"></span></div>
									<p class="ai-hint">위 표의 줄을 누르면 그 조건이 아래에 걸리고 바로 조회됩니다. 먼저 <b>원인별</b> 표를 봅니다.
										같은 기호(예: <code>Model</code>, <code>logger</code>, <code>HttpEntity</code>)가 많이 나오면 그 타입이 든 라이브러리(jar)가 빠진 것입니다.
										묶음 표의 줄을 누르면 조건이 더 좁혀지고, 맨 아래 목록에 파일 · 줄 번호가 나옵니다.</p>
									<!-- 조회 조건: 이름 칸을 한 줄로 맞춘 표 모양. 1행 참조의 종류 · 상태, 2행 이유, 3행 파일 경로 + 조회 -->
									<div class="kn-cond-grid">
										<label for="kn-unresolved-kind">참조의 종류</label>
										<select id="kn-unresolved-kind"></select>
										<label for="kn-unresolved-status">상태</label>
										<select id="kn-unresolved-status">
											<option value="UNRESOLVED">UNRESOLVED (못 풂)</option>
											<option value="HEURISTIC">HEURISTIC (이름으로 짐작해 이음)</option>
											<option value="IGNORED">IGNORED (일부러 뺌)</option>
										</select>
										<label>이유</label>
										<div id="kn-unresolved-reason" class="kn-reason-cond kn-cond-wide"></div>
										<label for="kn-unresolved-path">파일 경로</label>
										<div class="kn-cond-wide kn-cond-inline">
											<input type="text" id="kn-unresolved-path" placeholder="경로에 들어간 글자 (예: integration/)" />
											<button type="button" id="kn-unresolved-search-btn">조회</button>
										</div>
									</div>
									<div id="kn-unresolved-filter" class="kn-unresolved-filter"></div>
									<div id="kn-unresolved-message" class="kn-message"></div>
									<div id="kn-unresolved-result" style="display:none;">
										<div class="kn-unresolved-groups">
											<div>
												<div class="kn-section-title">원인별 (못 찾은 기호)</div>
												<div id="kn-unresolved-by-cause" class="kn-scroll"></div>
											</div>
											<div>
												<div class="kn-section-title">파일별</div>
												<div id="kn-unresolved-by-file" class="kn-scroll"></div>
											</div>
											<div>
												<div class="kn-section-title">부르려던 이름별</div>
												<div id="kn-unresolved-by-name" class="kn-scroll"></div>
											</div>
										</div>
										<div class="kn-section-title">한 건씩</div>
										<div id="kn-unresolved-list" class="kn-scroll"></div>
										<div class="kn-row">
											<button type="button" id="kn-unresolved-prev-btn" class="kn-plain">이전</button>
											<span id="kn-unresolved-page" class="workflow-status-label"></span>
											<button type="button" id="kn-unresolved-next-btn" class="kn-plain">다음</button>
										</div>
									</div>
								</div>
							</div>
						</div>

						<div class="kn-tab-panel" data-panel="content" style="display:none;">
							<p class="ai-hint">분석이 찾아낸 것들입니다. 진입점은 영향도 분석이, 계층은 검색 · 노드 맵이 씁니다.</p>
							<div id="kn-content"></div>
						</div>

						<div class="kn-tab-panel" data-panel="unused" style="display:none;">
							<p class="ai-hint">있지만 아무도 쓰지 않는 것입니다. 기준은 "들어오는 참조가 하나도 없다"이고, 정적 분석이라 틀릴 수 있어 항목마다 등급을 붙입니다.
								<b>확실</b>은 쓰일 흔적이 없는 것, <b>추정</b>은 프레임워크가 이름으로 부르거나 분석기가 못 푼 참조가 가리킬 수 있는 것입니다.
								지우기 전에는 줄을 눌러 노드 맵에서 한 번 더 확인하세요.</p>
							<div id="kn-unused-note"></div>
							<div id="kn-unused-cards" class="kn-cards kn-unused-cards"></div>
							<div id="kn-unused-list-box" style="display:none;">
								<div class="kn-row">
									<span id="kn-unused-list-title" class="kn-section-title kn-inline-title"></span>
									<select id="kn-unused-grade">
										<option value="">확실 + 추정</option>
										<option value="CERTAIN">확실만</option>
										<option value="LIKELY">추정만</option>
									</select>
									<input type="text" id="kn-unused-q" class="kn-wide" placeholder="이름 · 경로 · 한글명에 들어간 글자" />
									<button type="button" id="kn-unused-search-btn">조회</button>
									<button type="button" id="kn-unused-csv-btn" class="kn-plain">CSV 내려받기</button>
								</div>
								<div id="kn-unused-message" class="kn-message"></div>
								<div id="kn-unused-list" class="kn-scroll"></div>
								<div class="kn-row">
									<button type="button" id="kn-unused-prev-btn" class="kn-plain">이전</button>
									<span id="kn-unused-page" class="workflow-status-label"></span>
									<button type="button" id="kn-unused-next-btn" class="kn-plain">다음</button>
								</div>
							</div>
						</div>

						<div class="kn-tab-panel" data-panel="history" style="display:none;">
							<p class="ai-hint">이 리비전을 어떻게 만들었는지입니다. 문제가 있을 때만 보면 됩니다.</p>
							<div id="kn-history"></div>
						</div>
					</div>
				</section>
			</div>
					</div>
				</div>
			</div>
		</div>

		<jsp:include page="common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-terms.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/embedding.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/unresolved.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/unused.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/project.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeProject.init();
		DstoneKnowledgeUnresolved.init();
		DstoneKnowledgeUnused.init();
		DstoneKnowledgeEmbedding.init();
	</script>
</body>
</html>
