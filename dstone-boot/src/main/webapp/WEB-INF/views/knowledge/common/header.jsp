<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
String currentLink = requestUtil.getParameter("defaultLink", "");
String linkBase = requestUtil.getStrContextPath() + "/defaultLink.do?defaultLink=";
%>
			<div id="ai-header-wrapper">
				<header id="ai-header">
					<h1><a href="<%=linkBase%>knowledge/index" id="ai-logo">Dstone Knowledge</a></h1>
					<nav id="ai-nav">
						<a href="<%=linkBase%>knowledge/index" class="<%=(currentLink.equals("knowledge/index")?"current-page-item":"")%>">프로젝트 · 분석</a>
						<a href="<%=linkBase%>knowledge/search/search" class="<%=(currentLink.equals("knowledge/search/search")?"current-page-item":"")%>">검색 · 문서</a>
						<a href="<%=linkBase%>knowledge/impact/impact" class="<%=(currentLink.equals("knowledge/impact/impact")?"current-page-item":"")%>">영향도 · 호출 관계</a>
						<a href="<%=linkBase%>knowledge/diff/diff" class="<%=(currentLink.equals("knowledge/diff/diff")?"current-page-item":"")%>">리비전 비교</a>
						<a href="<%=linkBase%>knowledge/guide/guide" class="<%=(currentLink.equals("knowledge/guide/guide")?"current-page-item":"")%>">사용 안내</a>
						<a href="<%=linkBase%>ai/index">AI 화면으로</a>
					</nav>
				</header>
			</div>
