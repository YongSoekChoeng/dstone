<%@page import="net.dstone.common.utils.SystemUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%    
/******************************************* 변수 선언 시작 *******************************************/              
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);

/******************************************* 변수 선언 끝 *********************************************/           
%>   
			<!-- Header -->
				<div id="header-wrapper">
					<div class="container">
						<div class="row">
							<div class="col-12">

								<header id="header">
									<h1><a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=analyzer/index" id="logo">Application Analyzer</a></h1>
									<nav id="nav">
										<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=analyzer/configuration/configuration" class="<%=(requestUtil.getParameter("defaultLink", "").equals("analyzer/configuration/configuration")?"current-page-item":"")%>" >Configuration</a>
										<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=analyzer/configuration/optionsetting" class="<%=(requestUtil.getParameter("defaultLink", "").equals("analyzer/configuration/optionsetting")?"current-page-item":"")%>" >Options</a>
										<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=analyzer/analyzation/analyzation" class="<%=(requestUtil.getParameter("defaultLink", "").equals("analyzer/analyzation/analyzation")?"current-page-item":"")%>" >Analyzation</a>
										<a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=analyzer/report/overall" class="<%=(requestUtil.getParameter("defaultLink", "").equals("analyzer/report/main")?"current-page-item":"")%>" >Report</a
									</nav>
								</header>

							</div>
						</div>
					</div>
				</div>
