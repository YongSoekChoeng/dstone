<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
String currentLink = requestUtil.getParameter("defaultLink", "");
%>
			<div id="ai-header-wrapper">
				<header id="ai-header">
					<h1><a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/index" id="ai-logo">Dstone AI</a></h1>
					<nav id="ai-nav">
						<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/index" class="<%=(currentLink.equals("ai/index")?"current-page-item":"")%>">메인</a>
						<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/chat/chat" class="<%=(currentLink.equals("ai/chat/chat")?"current-page-item":"")%>">채팅</a>
						<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/document/document" class="<%=(currentLink.equals("ai/document/document")?"current-page-item":"")%>">문서업로드</a>
					</nav>
				</header>
			</div>
