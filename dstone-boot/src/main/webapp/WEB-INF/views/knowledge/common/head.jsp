<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
%>
	<head>
		<title>Dstone Knowledge · Dstone Console</title>
		<meta charset="utf-8" />
		<meta name="viewport" content="width=device-width, initial-scale=1" />
		<%-- 바탕(색, 글꼴, 왼쪽 메뉴, 표, 입력 칸, 단추)은 console.css, AI 화면의 부품은 ai 의 main.css 에 있다. 코드 분석 화면은 knowledge.css 를 더한다 --%>
		<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/ai/assets/css/console.css" />
		<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/ai/assets/css/main.css" />
		<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/knowledge/assets/css/knowledge.css" />
	</head>
