<%@page import="net.dstone.common.utils.SystemUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%    
  
%>  
<div id="sidebar">
	<div class="inner">
		<nav id="menu">
			<header class="major">
				<h2>Dstone</h2>
			</header>
			<ul>
				<li><a href="<%=request.getContextPath()%>/views/main">main</a></li>
				<li><a href="<%=request.getContextPath()%>/views/login">로그인</a></li>
				<li><a href="<%=request.getContextPath()%>/views/sample/user/listUser">멤버조회</a></li>
				<li><a href="<%=request.getContextPath()%>/views/sample/admin/manageUser">멤머입력</a></li>
				<li><a href="<%=request.getContextPath()%>/views/analyzer/index">분석툴</a></li>
				<li><a href="<%=request.getContextPath()%>/views/test/test">테스트1</a></li>

				<li><a href="<%=request.getContextPath()%>/views/sample/google/maps/main">구글맵</a></li>
				<li><a href="<%=request.getContextPath()%>/views/websocket/test">웹소켓테스트</a></li>
				<li><a href="<%=request.getContextPath()%>/kakao/loginPage.do">카카오로그인</a></li>
				<li><a href="<%=request.getContextPath()%>/naver/loginPage.do">네이버로그인</a></li>
				
			</ul>
		</nav>
	</div>
</div>