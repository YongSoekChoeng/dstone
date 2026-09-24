<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
%>
<!DOCTYPE HTML>
<html>

<jsp:include page="../../common/head.jsp"></jsp:include>

<body class="ai-body">
	<div id="ai-page-wrapper">

		<jsp:include page="../../common/header.jsp"></jsp:include>

		<div id="ai-main">
			<section class="ai-panel">
				<h3>WorkFlow 실행 관리</h3>
				<p class="ai-hint">이미 시작된 Workflow 실행을 조회하고, 승인 대기(WAITING_APPROVAL) 중인 실행을 승인/반려합니다.
					임의 workflowId를 직접 실행해보려면 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/workflow/workflow">Workflow 테스트</a> 화면을 쓰십시오.</p>

				<div class="workflow-status-row">
					<label for="workflow-admin-status-filter" class="workflow-status-label">상태</label>
					<select id="workflow-admin-status-filter">
						<option value="">전체</option>
						<option value="WAITING_APPROVAL">WAITING_APPROVAL</option>
						<option value="RUNNING">RUNNING</option>
						<option value="DONE">DONE</option>
						<option value="FAILED">FAILED</option>
						<option value="CANCELLED">CANCELLED</option>
					</select>
					<button type="button" id="workflow-admin-refresh-btn">새로고침</button>
				</div>
			</section>

			<section class="ai-panel">
				<h3>실행 목록</h3>
				<table id="workflow-admin-list-table" class="document-list-table">
					<thead>
						<tr>
							<th>executionId</th>
							<th>workflowId</th>
							<th>상태</th>
							<th>현재 스텝</th>
							<th>수정시각</th>
						</tr>
					</thead>
					<tbody id="workflow-admin-list-body"></tbody>
				</table>
			</section>

			<section class="ai-panel" id="workflow-admin-detail-panel" style="display:none;">
				<h3>실행 상세</h3>
				<div class="workflow-status-row">
					<span class="workflow-status-label">executionId</span>
					<span id="workflow-admin-detail-id" class="workflow-job-id">-</span>
					<span id="workflow-admin-detail-badge" class="workflow-badge">-</span>
				</div>
				<div class="workflow-field">
					<label>결과(resultText) / 실패사유(errorMessage)</label>
					<textarea id="workflow-admin-detail-result" class="workflow-textarea" readonly></textarea>
				</div>
				<div class="workflow-field">
					<label>context</label>
					<textarea id="workflow-admin-detail-context" class="workflow-textarea workflow-textarea-small" readonly></textarea>
				</div>

				<table id="workflow-admin-history-table" class="document-list-table">
					<thead>
						<tr>
							<th>stepId</th>
							<th>type</th>
							<th>ref</th>
							<th>결과</th>
							<th>소요(ms)</th>
							<th>내용/사유</th>
							<th>시각</th>
						</tr>
					</thead>
					<tbody id="workflow-admin-history-body"></tbody>
				</table>

				<div id="workflow-admin-decision-form" class="workflow-form" style="display:none;">
					<h3>승인/반려</h3>
					<div class="workflow-field">
						<label for="workflow-admin-approver">approver</label>
						<input type="text" id="workflow-admin-approver" placeholder="결정하는 사람/역할" />
					</div>
					<div class="workflow-field">
						<label for="workflow-admin-comment">comment (선택)</label>
						<textarea id="workflow-admin-comment" class="workflow-textarea workflow-textarea-small"></textarea>
					</div>
					<div class="workflow-actions">
						<button type="button" id="workflow-admin-approve-btn">승인</button>
						<button type="button" id="workflow-admin-reject-btn">반려</button>
						<span id="workflow-admin-decision-status" class="workflow-status"></span>
					</div>
				</div>
			</section>
		</div>

		<jsp:include page="../../common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/workflow-admin.js"></script>
	<script>
		DstoneAiWorkflowAdmin.init({
			listUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/list.do",
			detailUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/detail.do",
			decisionUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/decision.do"
		});
	</script>
</body>
</html>
