<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
String linkBase = requestUtil.getStrContextPath() + "/defaultLink.do?defaultLink=";
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
				<h2>AI 엔진</h2>
				<p>업무 절차를 Workflow로 정해 두면 순서, 갈림길, 사람의 승인은 엔진이 지키고 각 단계의 판단은 AI Agent가 맡습니다.</p>
				<%-- 엔진 연결, Workflow 수, 실행 현황을 한 줄로. 평소엔 조용하고, 승인 대기나 연결 문제가 있을 때만 색이 붙는다(overview.js) --%>
				<p id="ov-status" class="ov-status" aria-live="polite">엔진 상태를 확인하고 있습니다…</p>
			</section>

			<div class="ov-grid">
				<section class="ai-panel ov-flows">
					<div class="ov-flows-head">
						<h3>Workflow <span id="ov-flow-count" class="ov-count"></span></h3>
						<div id="ov-folders" class="ov-folders" role="group" aria-label="폴더로 좁히기"></div>
					</div>
					<%-- 띠의 색이 무엇인지. 띠 한 칸 = step 하나, 정의 파일에 적힌 순서 --%>
					<ul class="ov-legend" aria-label="step 종류">
						<li><i class="ov-seg ov-seg-AGENT"></i>Agent가 판단</li>
						<li><i class="ov-seg ov-seg-SUPERVISOR"></i>결과 검토</li>
						<li><i class="ov-seg ov-seg-ROUTER"></i>갈림길</li>
						<li><i class="ov-seg ov-seg-TOOL"></i>도구 실행</li>
						<li><i class="ov-seg ov-seg-APPROVAL"></i>사람이 승인</li>
						<li><i class="ov-seg ov-seg-TOOL ov-seg-each"></i>항목마다 반복</li>
					</ul>
					<div id="ov-flow-list" class="ov-flow-list">
						<p class="ov-empty">Workflow 목록을 불러오고 있습니다…</p>
					</div>
				</section>

				<aside class="ov-side">
					<section class="ai-panel">
						<div class="ov-side-head">
							<h3>최근 실행</h3>
							<a href="<%=linkBase%>ai/admin/workflow/workflow">실행 관리</a>
						</div>
						<ol id="ov-runs" class="ov-runs">
							<li class="ov-empty">불러오고 있습니다…</li>
						</ol>
					</section>

					<section class="ai-panel ov-chat">
						<h3>Agent와 대화하기</h3>
						<p>등록된 Agent에게 바로 물어봅니다. 올린 문서나 분석해 둔 소스 코드를 찾아 보고 답하게 할 수 있습니다.</p>
						<div class="ov-chat-actions">
							<a class="cs-btn" href="<%=linkBase%>ai/chat/chat">채팅 열기</a>
							<a href="<%=linkBase%>knowledge/search/search">참고 문서 올리기</a>
						</div>
					</section>
				</aside>
			</div>
					</div>
				</div>
			</div>
		</div>

		<jsp:include page="common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/overview.js"></script>
	<script>
		DstoneAiOverview.init({
			workflowsUrl: "<%=requestUtil.getStrContextPath()%>/ai/overview/workflows.do",
			runsUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/list.do",
			linkBase: "<%=linkBase%>"
		});
	</script>
</body>
</html>
