<%@page import="net.dstone.common.utils.SystemUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%    

String memoryInfo = SystemUtil.getMemoryInfo("\r\n");
String sysInfo = SystemUtil.getAllSystemProperties("\r\n"); 

%>   
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
		<%@ include file="/WEB-INF/views/common/header.jsp" %>
		
		<script type="text/javascript">
			function goLoginCheckAjax(){ 
				$.ajax({ 
					type:"POST", 
					url:"<%=net.dstone.common.config.ConfigSecurity.LOGIN_CHECK_ACTION%>", 
					data:{}, 
					dataType:"json", 
					success:function(data, status, request){
						var dmIsLogin = data.dmIsLogin; 
						if( 'Y' == dmIsLogin.isLogin ){
							$("#LOGOUT_SPAN").show();
						}else{
							$("#LOGOUT_SPAN").hide();
						}
					}, 
					error : function(data, status, e) { 
						$("#LOGOUT_SPAN").hide();
					} 
				}); 
			}
			function goLogoutAjax(){ 
				$.ajax({ 
					type:"POST", 
					url:"<%=net.dstone.common.config.ConfigSecurity.LOGOUT_ACTION%>", 
					data:{}, 
					dataType:"json", 
					success:function(data, status, request){
						var successYn = request.getResponseHeader('successYn');
						var errCd = data['errCd'];
						var errMsg = data['errMsg'];
						if( 'Y' == successYn ){
							alert("로그아웃되었습니다.");
							location.href = "/index.html";
						}
					}, 
					error : function(data, status, e) { 
						
					} 
				}); 
			} 
			function doLogoutSubmit(){ 
				document.SUBMIT_FORM.action="<%=net.dstone.common.config.ConfigSecurity.LOGOUT_ACTION%>";
				document.SUBMIT_FORM.submit();
			} 
		</script>
	</head>
	<body class="is-preload"  onload="javascript:goLoginCheckAjax();" >

		<!-- Wrapper -->
		<div id="wrapper">

			<!-- Main -->
			<div id="main">
			
				<div class="inner">

					<!-- Top -->
					<%@ include file="/WEB-INF/views/common/top.jsp" %>

					<section>
						<!-- Content Start  -->
						
						<span id="LOGOUT_SPAN" style="display: none">
						AJAX 로그아웃.
						<form name="AJAX_FORM" method="post" action="">
						<input type="button" value="GO" onclick="javascript:goLogoutAjax();" >
						</form>
						<br>
						폼서밋 로그아웃.
						<form name="SUBMIT_FORM" method="post" action="">
						<input type="button" value="GO" onclick="javascript:doLogoutSubmit();" >
						</form>
						</span>
						<br>
						<br>
						
						<h2 style="color: blue">【Spring Boot】</h2>
						<h3 style="color: blue">◈시스템메모리정보</h3>
						<textarea rows="10" cols="20" readonly ><%=memoryInfo %></textarea>
						
						<h3 style="color: blue">◈환경변수정보</h3>
						<textarea rows="10" cols="20" readonly ><%=sysInfo%></textarea>
						
						<!-- Content End  -->
					</section>

				</div>
			
			</div>

			<!-- Menu -->
			<%@ include file="/WEB-INF/views/common/left.jsp" %>

		</div>

	</body>
</html>