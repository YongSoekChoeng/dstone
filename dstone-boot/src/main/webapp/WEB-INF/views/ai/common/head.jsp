<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
%>
	<head>
		<title>Dstone AI</title>
		<meta charset="utf-8" />
		<meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no" />
		<%-- 바탕 모양은 분석툴과 같은 것을 쓰고, AI 화면에만 필요한 것을 ai 의 main.css 에 더한다 --%>
		<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/analyzer/assets/css/main.css" />
		<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/ai/assets/css/main.css" />
	</head>
