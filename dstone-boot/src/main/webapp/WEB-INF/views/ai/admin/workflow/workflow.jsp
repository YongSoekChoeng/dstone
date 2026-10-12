<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
%>
<!DOCTYPE HTML>
<html>

<jsp:include page="../../common/head.jsp"></jsp:include>

<body class="ai-body">
	<div id="page-wrapper">

		<jsp:include page="../../common/header.jsp"></jsp:include>

		<!-- Main -->
		<div id="main">
			<div class="container">
				<div class="row main-row">
					<div class="col-12">
			<section>
				<h2 align="center" style="font-weight: bold;">Workflow Execution</h2>
				<p>이미 시작된 Workflow 실행을 조회하고, 승인 대기(WAITING_APPROVAL) 중인 실행을 승인/반려합니다.
					임의 workflowId를 직접 실행해보려면 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/workflow/workflow">Workflow 테스트</a> 화면을 쓰십시오.</p>
			</section>

			<section class="ai-panel">
				<h3>조회 조건</h3>
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-btncol" /></colgroup>
					<tr>
						<th><label for="workflow-admin-status-filter">상태</label></th>
						<td><select id="workflow-admin-status-filter">
								<option value="">전체</option>
								<option value="WAITING_APPROVAL">WAITING_APPROVAL</option>
								<option value="RUNNING">RUNNING</option>
								<option value="DONE">DONE</option>
								<option value="FAILED">FAILED</option>
								<option value="CANCELLED">CANCELLED</option>
							</select></td>
						<td class="ai-form-btn"><button type="button" id="workflow-admin-refresh-btn">새로고침</button></td>
					</tr>
				</table>
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
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th>executionId</th>
						<td><span id="workflow-admin-detail-id" class="workflow-job-id">-</span></td>
						<th>상태</th>
						<td><span id="workflow-admin-detail-badge" class="workflow-badge">-</span></td>
					</tr>
					<tr>
						<th><label for="workflow-admin-detail-result">결과 / 실패사유</label></th>
						<td colspan="3"><textarea id="workflow-admin-detail-result" class="workflow-textarea" readonly></textarea></td>
					</tr>
					<tr>
						<th><label for="workflow-admin-detail-context">context</label></th>
						<td colspan="3"><textarea id="workflow-admin-detail-context" class="workflow-textarea workflow-textarea-small" readonly></textarea></td>
					</tr>
				</table>

				<div class="kn-section-title">스텝 이력</div>
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

				<div id="workflow-admin-decision-form" style="display:none;">
					<div class="kn-section-title">승인/반려 <span id="workflow-admin-decision-step" class="ai-hint"></span></div>
					<table class="ai-form">
						<colgroup><col class="ai-form-name" /><col /></colgroup>
						<tr>
							<th><label for="workflow-admin-approver">approver</label></th>
							<td><input type="text" id="workflow-admin-approver" placeholder="결정하는 사람/역할" /></td>
						</tr>
						<tr>
							<th><label for="workflow-admin-comment">comment</label></th>
							<td><textarea id="workflow-admin-comment" class="workflow-textarea workflow-textarea-small" placeholder="(선택)"></textarea></td>
						</tr>
					</table>
					<div class="ai-form-actions">
						<span id="workflow-admin-decision-status" class="workflow-status"></span>
						<%-- 승인/반려 방식 APPROVAL일 때 보이는 버튼 --%>
						<button type="button" id="workflow-admin-approve-btn">승인</button>
						<button type="button" id="workflow-admin-reject-btn" class="kn-plain">반려</button>
						<%-- 선택지 방식 APPROVAL(routes)일 때는 선택지 이름마다 버튼이 여기에 하나씩 만들어진다 --%>
						<span id="workflow-admin-route-buttons" class="ai-form-buttons"></span>
					</div>
				</div>
			</section>
					</div>
				</div>
			</div>
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
