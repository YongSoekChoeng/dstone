<%@page import="net.dstone.common.utils.SystemUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%    
/******************************************* 변수 선언 시작 *******************************************/              
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);

/******************************************* 변수 선언 끝 *********************************************/           
%>  
	<head>
		<title>Application Analyzer</title>
		<meta charset="utf-8" />
		<meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no" />
		<!-- Scripts -->
		<script src="<%=requestUtil.getStrContextPath()%>/js/jquery-1.7.2.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/js/jquery.json-2.4.js" ></script> 
		<script src="<%=requestUtil.getStrContextPath()%>/js/common.js" ></script> 
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/assets/js/browser.min.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/assets/js/breakpoints.min.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/assets/js/util.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/assets/js/main.js"></script>		
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/assets/js/go.js"></script>		
		
		<!-- slickGrid Start -->
		<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/styles/css/example-demo.css">
		<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/styles/css/slick-alpine-theme.css">
		<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/styles/css/slick.rowdetailview.css">
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/browser/slick.core.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/browser/slick.interactions.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/browser/slick.grid.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/browser/slick.dataview.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/npm/sortablejs/Sortable.min.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/npm/sortablejs/sortable-cdn-fallback.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/browser/plugins/slick.rowdetailview.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/browser/plugins/slick.rowselectionmodel.js"></script>
		
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/browser/plugins/export.excel.js/jquery.slickgrid.export.excel.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/browser/plugins/export.excel.js/require.js"></script>
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/browser/plugins/export.excel.js/underscore.js"></script>
		
		<script src="<%=requestUtil.getStrContextPath()%>/analyzer/slickGrid/lib/utils.js"></script>
		<!-- slickGrid End -->		
		
		<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/analyzer/assets/css/main.css" />
		
	</head>
