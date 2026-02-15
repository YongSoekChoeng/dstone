<%@page import="net.dstone.common.utils.Base64Util"%>
<%@page import="net.dstone.common.utils.StringUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
	String errCd 			= StringUtil.nullCheck(request.getAttribute("errCd"), "");
	String errMsg 			= StringUtil.nullCheck(request.getAttribute("errMsg"), "");
	String errMsgDetail 	= StringUtil.nullCheck(request.getAttribute("errMsgDetail"), ""); 
	errMsg					= Base64Util.decodeString(errMsg);
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
		<jsp:include page="./header.jsp" flush="true"/>
	</head>
	<body class="is-preload">

		<!-- Wrapper -->
		<div id="wrapper">

			<!-- Main -->
			<div id="main">
			
				<div class="inner">

					<!-- Top -->
					<jsp:include page="./top.jsp" flush="true"/>

					<section>
						<!-- Content Start  -->
						<header class="main">
							에러코드 : <%=errCd %>
							<br>
							에러메세지 : <%=errMsg %>
							<br>
							에러메세지상세
							<br>
							<textarea rows="10" cols="100"><%=errMsgDetail %></textarea>
						</header>
						<!-- Content End  -->
					</section>

				</div>
			
			</div>

			<!-- Menu -->
			<jsp:include page="./left.jsp" flush="true"/>

		</div>

	</body>
</html>