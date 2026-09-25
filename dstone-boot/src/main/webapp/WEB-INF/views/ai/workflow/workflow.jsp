<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
%>
<!DOCTYPE HTML>
<html>

<jsp:include page="../common/head.jsp"></jsp:include>

<body class="ai-body">
	<div id="ai-page-wrapper">

		<jsp:include page="../common/header.jsp"></jsp:include>

		<div id="ai-main">
			<section class="ai-panel">
				<h3>Workflow 비동기 테스트</h3>
				<p class="ai-hint">dstone-ai-engine의 GET /api/ai/workflow(목록) + POST
					/api/ai/workflow/{workflowId}/submit + GET /api/ai/workflow/executions/{executionId}
					비동기 계약을 직접 호출해보는 개발/테스트 화면입니다. [제출]을 누르면
					executionId를 바로 돌려받고, 이후 2초 간격으로 상태를 자동 조회해 RUNNING → DONE/FAILED/WAITING_APPROVAL 전이를 보여줍니다.
					승인 대기 실행을 목록으로 보거나 승인/반려하려면 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/admin/workflow/workflow">관리자 화면</a>을 쓰십시오.</p>

				<div class="workflow-form">
					<div class="workflow-field">
						<label for="workflow-id">workflowId</label>
						<select id="workflow-id"></select>
						<p id="workflow-id-desc" class="ai-hint"></p>
					</div>
					<div class="workflow-field">
						<label for="workflow-session-id">sessionId (선택, 비우면 자동 발급)</label>
						<input type="text" id="workflow-session-id" placeholder="예: 이전 응답에서 이어가고 싶은 세션 ID" />
					</div>
					<div class="workflow-field">
						<label for="workflow-message">message</label>
						<textarea id="workflow-message" class="workflow-textarea" placeholder="예: SELECT NVL(a, 0) FROM dual WHERE ROWNUM &lt;= 10"></textarea>
						<p id="workflow-sample-note" class="ai-hint"></p>
					</div>
					<div class="workflow-field">
						<label for="workflow-variables">variables (선택, JSON 객체)</label>
						<textarea id="workflow-variables" class="workflow-textarea workflow-textarea-small" placeholder="예: {}"></textarea>
					</div>
				</div>

				<div class="workflow-actions">
					<button type="button" id="workflow-submit-btn">제출(submit)</button>
					<span id="workflow-submit-status" class="workflow-status"></span>
				</div>
			</section>

			<section class="ai-panel">
				<h3>실행 상태</h3>
				<div class="workflow-status-row">
					<span class="workflow-status-label">executionId</span>
					<span id="workflow-job-id" class="workflow-job-id">-</span>
					<span id="workflow-status-badge" class="workflow-badge">-</span>
					<button type="button" id="workflow-poll-stop-btn" disabled>폴링 중지</button>
					<button type="button" id="workflow-poll-refresh-btn" disabled>지금 새로고침</button>
				</div>
				<div class="workflow-field">
					<label>result / error</label>
					<textarea id="workflow-result" class="workflow-textarea" readonly></textarea>
				</div>
			</section>

			<section class="ai-panel">
				<h3>이번 화면에서 제출한 작업</h3>
				<p class="ai-hint">서버에 저장되지 않고, 이 화면을 새로고침하면 사라지는 목록입니다. executionId를 클릭하면 그 작업의 상태를 다시 조회합니다.</p>
				<table id="workflow-history-table" class="document-list-table">
					<thead>
						<tr>
							<th>제출시각</th>
							<th>workflowId</th>
							<th>executionId</th>
							<th>상태</th>
						</tr>
					</thead>
					<tbody id="workflow-history-body"></tbody>
				</table>
			</section>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/workflow.js"></script>
	<script>
		DstoneAiWorkflow.init({
			listUrl: "<%=requestUtil.getStrContextPath()%>/ai/workflow/list.do",
			submitUrl: "<%=requestUtil.getStrContextPath()%>/ai/workflow/submit.do",
			statusUrl: "<%=requestUtil.getStrContextPath()%>/ai/workflow/status.do"
		});
	</script>
</body>
</html>
