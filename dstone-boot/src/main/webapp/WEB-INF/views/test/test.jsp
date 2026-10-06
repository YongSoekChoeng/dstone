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
			
			function doTestAjax(){ 
				$.ajax({ 
					type:"POST", 
					url:"/test/doTestAjax.do", 
					data:encodeURIComponent(JSON.stringify($(document.AJAX_FORM).serializeObject())), 
					dataType:"json", 
					success:function(data, status, request){
						var successYn = request.getResponseHeader('successYn');
						var errCd = data['errCd'];
						var errMsg = data['errMsg'];
						if( 'Y' == successYn ){
							console.log('success ===>>> data:' + (JSON.stringify(data)));
							alert("success");
							$("#successYn").text("성공");
						}else{
							console.log('failure ===>>> data:' + (JSON.stringify(data)));
							alert("failure");
							$("#successYn").text("실패");
						}
						
					}, 
					error : function(data, status, e) { 
						console.log('system error ===>>> data:' + (JSON.stringify(data))); 
						alert("system error");
						$("#successYn").text("에러]");
					} 
				}); 
			} 
		
			function doTestSubmit(){ 
				document.SUBMIT_FORM.action="/test/doTestSubmit.do";
				document.SUBMIT_FORM.submit();
			} 

			function doTest(){ 
				alert('doTest');
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
						<!-- =============================================== Content 영역 Start =============================================== -->
						   
						*** 폼서밋 테스트<%=("Y".equals(successYn)?"성공":("N".equals(successYn)?"실패":"")) %><br>
						<form name="SUBMIT_FORM" method="post" action="">
						TEST_PARAM1:<input type="text" name="MY_NAME" value="TEST..." >
						<br>
						FORCE_EXCEPTION_YN:<select name="FORCE_EXCEPTION_YN" ><option value="N">성공</option><option value="Y">실패</option></select>
						<br>
						<table border=1>
							<tr>
								<td colspan="2"> <input type="button" value="GO" onclick="javascript:doTestSubmit();" > </td>
							</tr>
						</table>
						</form>
						
						<br>
						<br>
						
						*** AJAX 테스트<span id="successYn"></span><br>
						<form name="AJAX_FORM" method="post" action="">
						TEST_PARAM1:<input type="text" name="MY_NAME" value="TEST..." >
						<br>
						FORCE_EXCEPTION_YN:<select name="FORCE_EXCEPTION_YN" ><option value="N">성공</option><option value="Y">실패</option></select>
						<br>
						<table border=1>
							<tr>
								<td colspan="2"> <input type="button" value="GO" onclick="javascript:doTestAjax();" > </td>
							</tr>
						</table>
						</form>
						        
						<br>
						<br>
						
						<!-- =============================================== Content 영역 End =============================================== -->
					</section>

				</div>
			
			</div>

			<!-- Menu 영역 -->
			<%@ include file="/WEB-INF/views/common/left.jsp" %>

		</div>

	</body>
</html>
