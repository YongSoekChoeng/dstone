<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<div id="sidebar">
	<div class="inner">
		<nav id="menu">
			<header class="major">
				<h2>배치관리</h2>
			</header>
			<ul>
				<li><a href="<%=request.getContextPath()%>/main/main.do">메인화면</a></li>
				<li><a href="<%=request.getContextPath()%>/job/list.do">배치JOB 목록조회</a></li>
				<li><a href="<%=request.getContextPath()%>/job/register.do">배치JOB 등록</a></li>
				<li><a href="<%=request.getContextPath()%>/server/list.do">배치서버 관리</a></li>
			</ul>
		</nav>
	</div>
</div>
