<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
		<jsp:include page="/WEB-INF/views/common/header.jsp" flush="true"/>
		<style type="text/css">
			.summary-cards { display:flex; gap:20px; margin: 20px 0; }
			.summary-cards .card { flex:1; padding:20px; border:1px solid #ddd; border-radius:6px; text-align:center; }
			.summary-cards .card .num { font-size:2em; font-weight:bold; }
		</style>
		<script type="text/javascript">
			function goSummaryAjax(){
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/main/summary.do",
					data:{},
					dataType:"json",
					success:function(data, status, request){
						var successYn = request.getResponseHeader('successYn');
						if( 'Y' == successYn ){
							$("#SERVER_CNT").text(data.returnObj.SERVER_CNT);
							$("#JOB_CNT").text(data.returnObj.JOB_CNT);
						}
					}
				});
			}
			function doLogout(){
				document.LOGOUT_FORM.action="<%=net.dstone.batchadmin.common.config.ConfigSecurity.LOGOUT_ACTION%>";
				document.LOGOUT_FORM.submit();
			}
		</script>
	</head>
	<body class="is-preload" onload="javascript:goSummaryAjax();">
		<div id="wrapper">
			<div id="main">
				<div class="inner">
					<jsp:include page="/WEB-INF/views/common/top.jsp" flush="true"/>
					<section>
						<h2>메인화면</h2>
						<form name="LOGOUT_FORM" method="post" action="">
							<button type="button" class="button" onclick="javascript:doLogout();">로그아웃</button>
						</form>
						<div class="summary-cards">
							<div class="card">
								<div>등록된 배치서버</div>
								<div class="num" id="SERVER_CNT">-</div>
							</div>
							<div class="card">
								<div>등록된 배치JOB</div>
								<div class="num" id="JOB_CNT">-</div>
							</div>
						</div>
						<ul>
							<li><a href="<%=request.getContextPath()%>/job/list.do">배치JOB 목록조회</a></li>
							<li><a href="<%=request.getContextPath()%>/job/register.do">배치JOB 등록</a></li>
							<li><a href="<%=request.getContextPath()%>/server/list.do">배치서버 관리</a></li>
						</ul>
					</section>
				</div>
			</div>
			<jsp:include page="/WEB-INF/views/common/left.jsp" flush="true"/>
		</div>
	</body>
</html>
