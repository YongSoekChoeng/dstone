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
					url:"/kafka/send.do", 
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
						$("#successYn").text("에러");
					} 
				}); 
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
						  
						*** KAFKA 테스트<span id="successYn"></span><br>
						<form name="AJAX_FORM" method="post" action="">
						주문ID:<input type="text" name="orderId" value="" >
						<br>
						주문명:<input type="text" name="orderName" value="테스트주문" >
						<br>
						주문아이템:<input type="text" name="orderItem" value="연필" >
						<br>
						주문수량:<input type="text" name="orderCount" value="1000" >
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
