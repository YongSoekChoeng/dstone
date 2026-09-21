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
			<section class="ai-banner">
				<h2>Dstone AI</h2>
				<p>dstone-ai-engine과 연동되는 AI 기능 모음입니다. "사용자"는 누구나 기능을 직접 눌러보는 화면이고,
					"관리자"는 이미 진행 중인 작업(문서 임베딩, Workflow 실행)을 보고 판단·조작하는 화면입니다.</p>
			</section>

			<section class="ai-panel">
				<h3>사용자</h3>
				<div class="ai-card-list">
					<a class="ai-card" href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/chat/chat">
						<h3>채팅</h3>
						<p>dstone-ai-engine과 실시간으로 대화합니다. RAG 검색증강, Tool 호출을 선택적으로 켤 수 있습니다.</p>
					</a>
					<a class="ai-card" href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/workflow/workflow">
						<h3>WorkFlow 테스트</h3>
						<p>등록된 Workflow 중 하나를 골라 submit/status 비동기 Workflow 호출을 확인합니다.</p>
					</a>
				</div>
			</section>

			<section class="ai-panel">
				<h3>관리자</h3>
				<div class="ai-card-list">
					<a class="ai-card" href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/admin/document/document">
						<h3>문서 임베딩 관리</h3>
						<p>RAG 검색 대상 문서를 업로드/삭제하고, 지금까지 적재한 문서 목록을 확인합니다.</p>
					</a>
					<a class="ai-card" href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/admin/workflow/workflow">
						<h3>WorkFlow 실행 관리</h3>
						<p>진행 중인 Workflow 실행을 조회하고, 승인 대기 중인 실행을 승인/반려합니다.</p>
					</a>
				</div>
			</section>
		</div>

		<jsp:include page="common/footer.jsp"></jsp:include>

	</div>
</body>
</html>
