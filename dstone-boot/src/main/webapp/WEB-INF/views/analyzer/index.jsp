<%@page import="net.dstone.common.utils.SystemUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%    

/******************************************* 변수 선언 시작 *******************************************/              
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);

/******************************************* 변수 선언 끝 *********************************************/           
    

%>   
<!DOCTYPE HTML>
<!--
	Minimaxing by HTML5 UP
	html5up.net | @ajlkn
	Free for personal and commercial use under the CCA 3.0 license (html5up.net/license)
-->
<html>

	<!-- Head -->
	<jsp:include page="common/head.jsp"></jsp:include>
	
	<body>
		<div id="page-wrapper">

			<!-- Header -->
			<jsp:include page="common/header.jsp"></jsp:include>
			
			<!-- Banner -->
				<div id="banner-wrapper">
					<div class="container">

						<div id="banner">
							<h2>Application Analyzer</h2>
							<span>Make your application easyer to understand ...</span>
						</div>

					</div>
				</div>

			<!-- Main -->
				<div id="main">
					<div class="container">
						<div class="row main-row">
							<div class="col-12 col-12-medium">

								<section>
									<h2>레거시 Application 분석 툴</h2>
									<p>화면에서 API를 통해서 테이블까지 호출구조를 분석하여 모듈별 영향도와 구조 분석.</p>
									<p>SI 프로젝트의 분석단계에서 레거시 Application의 빠른 이해도 향상.</p>

								</section>

							</div>
							
						</div>
					</div>
				</div>

			<!-- Footer -->
			<jsp:include page="common/footer.jsp"></jsp:include>

		</div>

	</body>
</html>