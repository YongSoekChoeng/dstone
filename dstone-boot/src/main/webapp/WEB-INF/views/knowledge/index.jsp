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
				<p class="ai-hint">분석 결과가 얼마나 만들어졌고 얼마나 믿을 만한지 봅니다. 풀지 못한 참조가 많거나 신뢰도 LOW인 관계가 많으면
					프로젝트의 classpath(라이브러리 jar)가 빠졌는지 확인합니다.</p>
				<div id="kn-summary-cards" class="kn-cards"></div>
				<div id="kn-summary-sections"></div>
			</section>
		</div>

		<jsp:include page="common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/project.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeProject.init();
	</script>
</body>
</html>
