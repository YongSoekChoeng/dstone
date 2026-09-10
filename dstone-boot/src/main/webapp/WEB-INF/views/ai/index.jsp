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
				<p>dstone-ai-engine과 연동되는 AI 기능 모음입니다. 지금은 채팅과 문서업로드(RAG 적재) 두 가지를 제공하며,
					앞으로 기능이 늘어나면 이 메뉴세트 안에 탭만 추가됩니다.</p>
			</section>

			<section class="ai-card-list">
				<a class="ai-card" href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/chat/chat">
					<h3>채팅</h3>
					<p>dstone-ai-engine과 실시간으로 대화합니다. RAG 검색증강, Tool 호출을 선택적으로 켤 수 있습니다.</p>
				</a>
				<a class="ai-card" href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/document/document">
					<h3>문서업로드</h3>
					<p>RAG 검색 대상 문서를 업로드/삭제하고, 지금까지 적재한 문서 목록을 확인합니다.</p>
				</a>
			</section>
		</div>

		<jsp:include page="common/footer.jsp"></jsp:include>

	</div>
</body>
</html>
