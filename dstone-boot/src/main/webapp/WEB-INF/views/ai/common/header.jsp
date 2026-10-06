<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
String currentLink = requestUtil.getParameter("defaultLink", "");
%>
			<div id="ai-header-wrapper">
				<header id="ai-header">
					<h1><a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/index" id="ai-logo">Dstone AI</a></h1>
					<nav id="ai-nav">
						<%-- dstone-boot 의 첫 화면으로 돌아간다(왼쪽 메뉴의 "main" 과 같은 곳) --%>
						<a href="<%=requestUtil.getStrContextPath()%>/views/main" class="ai-nav-home">&larr; Dstone 홈</a>
						<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/index" class="<%=(currentLink.equals("ai/index")?"current-page-item":"")%>">메인</a>
						<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/chat/chat" class="<%=(currentLink.equals("ai/chat/chat")?"current-page-item":"")%>">채팅</a>
						<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/workflow/workflow" class="<%=(currentLink.equals("ai/workflow/workflow")?"current-page-item":"")%>">Workflow 테스트</a>
						<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/admin/workflow/workflow" class="<%=(currentLink.equals("ai/admin/workflow/workflow")?"current-page-item":"")%>">Workflow 실행 관리</a>
						<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/index">코드 분석(Knowledge)</a>
					</nav>
				</header>
			</div>
