<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
%>
<!DOCTYPE HTML>
<html>

<jsp:include page="common/head.jsp"></jsp:include>

<body class="ai-body">
	<div id="ai-page-wrapper">

		<jsp:include page="common/header.jsp"></jsp:include>

		<div id="ai-main">
			<details class="kn-howto">
				<summary>처음이신가요? 이 화면은 이렇게 씁니다 (눌러서 펴기)</summary>
				<ol>
					<li><b>프로젝트 등록</b> — 아래 "프로젝트 등록 / 수정"에 projectId(영문), 이름, 소스 폴더(분석 서버에 있는 경로)를 적고 "등록".
						이미 등록된 것을 쓰려면 이 단계는 건너뜁니다.</li>
					<li><b>프로젝트 고르기</b> — 표에서 <b>projectId 를 누릅니다</b>. 아래에 "분석" 칸이 나타납니다.</li>
					<li><b>분석 시작</b> — 아무것도 적지 않고 "분석 시작"을 누르면 됩니다. 두 번째부터는 "증분 분석"을 체크하면 바뀐 파일만 다시 분석합니다.
						단계별 진행 상황이 3초마다 갱신됩니다.</li>
					<li><b>결과 확인</b> — 리비전 목록에서 상태가 <code>READY</code> 인 줄의 "요약"을 누릅니다. 파일 · 타입 · 관계 수와 품질 지표가 나옵니다.</li>
					<li><b>물어보기</b> — 위 메뉴의 "검색 · 문서", "영향도 · 호출 관계", "리비전 비교"로 갑니다.</li>
				</ol>
				바로 눌러 보려면 이미 분석해 둔 <code>struts-app</code>(게시판 예제)을 고르세요.
				이 예제는 일부러 라이브러리 없이 넣어 둔 것이라 요약의 "한눈에 보기" 첫 줄이 빨간색으로 나옵니다(정상).
				말뜻과 화면별 설명은 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/guide/guide">사용 안내</a>에 있습니다.
			</details>

			<section class="ai-panel">
				<h3>프로젝트 <span id="kn-health"></span></h3>
				<p class="ai-hint">dstone-knowledge에 등록한 Java 프로젝트입니다. 프로젝트를 고르면 아래에서 분석을 시작하고 결과를 볼 수 있습니다.
					소스는 dstone-knowledge 서버가 직접 읽을 수 있는 폴더여야 합니다.</p>
				<div id="kn-project-table" class="kn-scroll"></div>

				<div class="kn-section-title">프로젝트 등록 / 수정</div>
				<div class="kn-row">
					<input type="text" id="kn-new-project-id" placeholder="projectId (영문)" />
					<input type="text" id="kn-new-project-name" placeholder="이름" />
					<input type="text" id="kn-new-project-path" class="kn-wide" placeholder="소스 폴더 (서버의 경로. 예: /app/sampleApps/anybiz_prd)" />
					<button type="button" id="kn-project-save-btn">등록</button>
				</div>
				<div class="kn-row">
					<input type="text" id="kn-new-project-classpath" class="kn-wide" placeholder="(선택) 라이브러리 jar 의 위치. 소스 폴더 밖에 있을 때만. 파일이나 폴더를 쉼표로 구분" />
				</div>
				<p class="ai-hint">같은 projectId 로 다시 등록하면 수정입니다. 소스 폴더 안의 jar 는 알아서 찾으므로, 라이브러리 위치는 jar 가 다른 곳에 있을 때만 적습니다.
					jar 가 없어도 분석은 되지만 "누가 누구를 부르는지"가 덜 정확해집니다.</p>
				<div id="kn-project-message" class="kn-message"></div>
			</section>

			<section class="ai-panel" id="kn-analysis-panel" style="display:none;">
				<h3>분석 <span id="kn-selected-project" class="workflow-job-id"></span></h3>
				<p class="ai-hint">리비전은 "그 시점의 분석 결과 전체"입니다. 라벨을 비우면 지금 시각으로 새 리비전을 만듭니다.
					이미 있는 라벨을 적으면 그 리비전을 이어서 분석합니다(끝난 단계는 건너뜁니다).</p>
				<div class="kn-row">
					<input type="text" id="kn-revision-label" placeholder="리비전 라벨 (비우면 지금 시각)" />
					<label><input type="checkbox" id="kn-incremental" /> 증분 분석 (바뀐 파일만 다시 분석)</label>
					<label for="kn-rerun-from">이 단계부터 다시</label>
					<select id="kn-rerun-from">
						<option value="">(쓰지 않음)</option>
						<option>SCAN</option>
						<option>DECLARE</option>
						<option>RESOURCE</option>
						<option>RESOLVE</option>
						<option>LINK</option>
						<option>SEMANTIC</option>
						<option>DOCUMENT</option>
					</select>
					<button type="button" id="kn-analysis-start-btn">분석 시작</button>
				</div>
				<div id="kn-analysis-message" class="kn-message"></div>

				<div id="kn-job-box" style="display:none;">
					<div class="workflow-status-row">
						<span class="workflow-status-label">Job</span>
						<span id="kn-job-id" class="workflow-job-id">-</span>
						<span id="kn-job-badge"></span>
						<span id="kn-job-progress" class="workflow-status-label"></span>
						<button type="button" id="kn-job-cancel-btn">취소</button>
					</div>
					<div id="kn-job-passes" class="kn-scroll"></div>
					<div id="kn-job-errors"></div>
				</div>

				<div class="kn-section-title">리비전</div>
				<div id="kn-revision-table" class="kn-scroll"></div>
				<div class="kn-row">
					<label for="kn-retention-keep">최근</label>
					<input type="number" id="kn-retention-keep" min="1" value="5" style="width:70px;" />
					<label>개만 남기고 지우기</label>
					<button type="button" id="kn-retention-btn" class="kn-plain">보관 정책 적용</button>
				</div>
				<div id="kn-revision-message" class="kn-message"></div>
			</section>

			<section class="ai-panel" id="kn-summary-panel" style="display:none;">
				<h3>리비전 요약 <span id="kn-summary-title" class="workflow-job-id"></span></h3>
				<p class="ai-hint">분석 결과가 얼마나 만들어졌고 얼마나 믿을 만한지 봅니다. 숫자를 다 읽을 필요는 없습니다.
					<b>"한눈에 보기"</b>만 봐도 이 리비전을 써도 되는지 알 수 있고, 더 알고 싶을 때 아래 표를 봅니다.
					표의 영문 값 옆에는 뜻이 붙어 있고, 표마다 <b>"이 표 읽는 법"</b>을 누르면 각 열의 뜻과 숫자를 어떻게 해석하는지 나옵니다.
					모든 항목의 설명은 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/guide/guide#summary">사용 안내의 "리비전 요약 읽는 법"</a>에 모아 두었습니다.</p>
				<div id="kn-summary-cards" class="kn-cards"></div>
				<div id="kn-summary-sections"></div>
			</section>
		</div>

		<jsp:include page="common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-terms.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/project.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeProject.init();
	</script>
</body>
</html>
