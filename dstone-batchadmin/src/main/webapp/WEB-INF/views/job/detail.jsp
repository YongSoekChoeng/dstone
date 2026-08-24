<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
	String serverId = String.valueOf(request.getAttribute("SERVER_ID"));
	String jobInstanceId = String.valueOf(request.getAttribute("JOB_INSTANCE_ID"));
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
		<jsp:include page="/WEB-INF/views/common/header.jsp" flush="true"/>
		<style type="text/css">
			table.grid { width:100%; border-collapse: collapse; margin-top: 10px; }
			table.grid th, table.grid td { border: 1px solid #ddd; padding: 6px 8px; font-size: 0.9em; }
			table.grid th { background: #f5f5f5; }
			.btn-link { cursor:pointer; color:#2a6fdb; text-decoration: underline; margin-right:6px; }
			.panel { border:1px solid #ddd; border-radius:6px; padding:14px; margin-top:14px; }
		</style>
		<script type="text/javascript">
			var SERVER_ID = "<%=serverId%>";
			var JOB_INSTANCE_ID = "<%=jobInstanceId%>";

			function loadHistory(){
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/executionHistory.do",
					data:{SERVER_ID: SERVER_ID, JOB_INSTANCE_ID: JOB_INSTANCE_ID},
					dataType:"json",
					success:function(data, status, request){
						var list = data.returnObj || [];
						if(list.length > 0){
							$("#JOB_NAME_TITLE").text(list[0].JOB_NAME + " (JOB아이디: " + JOB_INSTANCE_ID + ")");
						}
						var tbody = $("#HIST_TBODY");
						tbody.empty();
						for(var i=0; i<list.length; i++){
							var row = list[i];
							var tr = $("<tr></tr>");
							tr.append("<td>"+row.JOB_EXECUTION_ID+"</td>");
							tr.append("<td>"+(row.STATUS||"")+"</td>");
							tr.append("<td>"+(row.START_TIME||"")+"</td>");
							tr.append("<td>"+(row.END_TIME||"")+"</td>");
							tr.append("<td>"+(row.EXIT_MESSAGE||"")+"</td>");
							var tdAction = $("<td></td>");
							var viewLink = $("<span class='btn-link'>상세</span>");
							viewLink.data("id", row.JOB_EXECUTION_ID);
							viewLink.click(function(){ loadExecutionDetail($(this).data("id")); });
							tdAction.append(viewLink);
							if(row.STATUS == 'STARTED' || row.STATUS == 'STARTING'){
								var stopLink = $("<span class='btn-link'>중지</span>");
								stopLink.data("id", row.JOB_EXECUTION_ID);
								stopLink.click(function(){ controlJob('stopJob', $(this).data('id')); });
								tdAction.append(stopLink);
							}
							if(row.STATUS == 'STOPPED' || row.STATUS == 'FAILED'){
								var restartLink = $("<span class='btn-link'>재시작</span>");
								restartLink.data("id", row.JOB_EXECUTION_ID);
								restartLink.click(function(){ controlJob('restartJob', $(this).data('id')); });
								tdAction.append(restartLink);

								var abandonLink = $("<span class='btn-link'>폐기</span>");
								abandonLink.data("id", row.JOB_EXECUTION_ID);
								abandonLink.click(function(){ controlJob('abandonJob', $(this).data('id')); });
								tdAction.append(abandonLink);
							}
							tr.append(tdAction);
							tbody.append(tr);
						}
					}
				});
			}

			function loadExecutionDetail(jobExecutionId){
				$("#STEP_PANEL").show();
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/stepExecution.do",
					data:{SERVER_ID: SERVER_ID, JOB_EXECUTION_ID: jobExecutionId},
					dataType:"json",
					success:function(data, status, request){
						var list = data.returnObj || [];
						var tbody = $("#STEP_TBODY");
						tbody.empty();
						for(var i=0; i<list.length; i++){
							var row = list[i];
							var tr = $("<tr></tr>");
							tr.append("<td>"+(row.STEP_NAME||"")+"</td>");
							tr.append("<td>"+(row.STATUS||"")+"</td>");
							tr.append("<td>"+(row.READ_COUNT||0)+"</td>");
							tr.append("<td>"+(row.WRITE_COUNT||0)+"</td>");
							tr.append("<td>"+(row.COMMIT_COUNT||0)+"</td>");
							tr.append("<td>"+(row.EXIT_MESSAGE||"")+"</td>");
							tbody.append(tr);
						}
					}
				});
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/executionParams.do",
					data:{SERVER_ID: SERVER_ID, JOB_EXECUTION_ID: jobExecutionId},
					dataType:"json",
					success:function(data, status, request){
						var list = data.returnObj || [];
						var tbody = $("#PARAM_TBODY");
						tbody.empty();
						for(var i=0; i<list.length; i++){
							var row = list[i];
							var tr = $("<tr></tr>");
							tr.append("<td>"+(row.PARAMETER_NAME||"")+"</td>");
							tr.append("<td>"+(row.PARAMETER_TYPE||"")+"</td>");
							tr.append("<td>"+(row.PARAMETER_VALUE||"")+"</td>");
							tbody.append(tr);
						}
					}
				});
			}

			function controlJob(action, jobExecutionId){
				if(!confirm(action+" 하시겠습니까?")) return;
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/"+action+".do",
					data:{SERVER_ID: SERVER_ID, JOB_EXECUTION_ID: jobExecutionId},
					dataType:"json",
					success:function(data, status, request){
						alert("결과: " + JSON.stringify(data.returnObj));
						loadHistory();
					},
					error:function(){ alert("호출 실패"); }
				});
			}

			$(document).ready(function(){
				$("#STEP_PANEL").hide();
				loadHistory();
			});
		</script>
	</head>
	<body class="is-preload">
		<div id="wrapper">
			<div id="main">
				<div class="inner">
					<jsp:include page="/WEB-INF/views/common/top.jsp" flush="true"/>
					<section>
						<h2>배치JOB 상세조회 - <span id="JOB_NAME_TITLE"></span></h2>

						<h3>수행이력</h3>
						<table class="grid">
							<thead>
								<tr><th>실행ID</th><th>상태</th><th>시작일시</th><th>종료일시</th><th>결과메세지</th><th>관리</th></tr>
							</thead>
							<tbody id="HIST_TBODY"></tbody>
						</table>

						<div class="panel" id="STEP_PANEL">
							<h3>Step 실행상세</h3>
							<table class="grid">
								<thead>
									<tr><th>Step명</th><th>상태</th><th>READ</th><th>WRITE</th><th>COMMIT</th><th>결과메세지</th></tr>
								</thead>
								<tbody id="STEP_TBODY"></tbody>
							</table>

							<h3>실행 파라메터</h3>
							<table class="grid">
								<thead>
									<tr><th>파라메터명</th><th>타입</th><th>값</th></tr>
								</thead>
								<tbody id="PARAM_TBODY"></tbody>
							</table>
						</div>

					</section>
				</div>
			</div>
			<jsp:include page="/WEB-INF/views/common/left.jsp" flush="true"/>
		</div>
	</body>
</html>
