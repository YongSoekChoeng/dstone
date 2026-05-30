<%@page import="net.dstone.common.utils.StringUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%    
	String successYn = StringUtil.nullCheck(response.getHeader("successYn"), ""); 
%> 
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
		
		<!-- Header 영역 -->
		<%@ include file="/WEB-INF/views/common/header.jsp" %>
		
		<script type="text/javascript">

			function goLoginAjax(){ 
				$.ajax({ 
					type:"POST", 
					url:"<%=net.dstone.common.config.ConfigSecurity.LOGIN_PROCESS_ACTION%>", 
					data:encodeURIComponent(JSON.stringify($(document.AJAX_FORM).serializeObject())), 
					dataType:"json", 
					success:function(data, status, request){
						var successYn = request.getResponseHeader('successYn');
						var errCd = request.getResponseHeader('errCd');
						var errMsg = decodeURIComponent(request.getResponseHeader('errMsg'));
						if( 'Y' == successYn ){
							console.log('success ===>>> data:' + (JSON.stringify(data)));
							$("#successYn").text("성공");
						}else{
							console.log('failure ===>>> data:' + (JSON.stringify(data)));
							$("#successYn").text("실패");
							alert(errMsg);
						}
						
					}, 
					error : function(data, status, e) { 
						console.log('system error ===>>> data:' + (JSON.stringify(data))); 
						$("#successYn").text("에러");
						alert(errMsg);
					} 
				}); 
			} 

			function doLoginSubmit(){ 
				document.SUBMIT_FORM.action="<%=net.dstone.common.config.ConfigSecurity.LOGIN_PROCESS_ACTION%>";
				document.SUBMIT_FORM.submit();
			} 
		 
		</script>
	</head>
	<body class="is-preload">

		<!-- Wrapper -->
		<div id="wrapper">

			<!-- Main -->
			<div id="main">
			
				<div class="inner">

					<!-- Top 영역 -->
					<%@ include file="/WEB-INF/views/common/top.jsp" %>

					<section>
						<!-- Content Start  -->
						
						AJAX 로그인<span id="successYn"></span><br>
						<form name="AJAX_FORM" method="post" action="">
						<table border=1>
							<tr>
								<td>ID</td><td><input type="text" name="<%=net.dstone.common.config.ConfigSecurity.USERNAME_PARAMETER%>" value="USER01" ></td>
							</tr>
							<tr>
								<td>PASSWORD</td><td><input type="password" name="<%=net.dstone.common.config.ConfigSecurity.PASSWORD_PARAMETER%>" value="12345678" ></td>
							</tr>
							<tr>
								<td colspan="2"> <input type="button" value="GO" onclick="javascript:goLoginAjax();" > </td>
							</tr>
						</table>
						</form>
						
						<br><br>
						
						폼서밋 로그인<br>
						<form name="SUBMIT_FORM" method="post" action="">
						<table border=1>
							<tr>
								<td>ID</td><td><input type="text" name="<%=net.dstone.common.config.ConfigSecurity.USERNAME_PARAMETER%>" value="ADMIN01" ></td>
							</tr>
							<tr>
								<td>PASSWORD</td><td><input type="password" name="<%=net.dstone.common.config.ConfigSecurity.PASSWORD_PARAMETER%>" value="12345678" ></td>
							</tr>
							<tr>
								<td colspan="2"> <input type="button" value="GO" onclick="javascript:doLoginSubmit();" > </td>
							</tr>
						</table>
						</form>
	
						
						<!-- Content End  -->
					</section>

				</div>
			
			</div>

			<!-- Menu 영역 -->
			<%@ include file="/WEB-INF/views/common/left.jsp" %>

		</div>

	</body>
</html>