<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
		<jsp:include page="/WEB-INF/views/common/header.jsp" flush="true"/>
		<style type="text/css">
			table.grid { width:100%; border-collapse: collapse; margin-top: 10px; }
			table.grid th, table.grid td { border: 1px solid #ddd; padding: 6px 8px; font-size: 0.9em; }
			table.grid th { background: #f5f5f5; }
			.form-box { border:1px solid #ddd; border-radius:6px; padding: 16px; margin-bottom: 16px; }
			.form-box .row { display:flex; gap:12px; margin-bottom:10px; flex-wrap: wrap; }
			.form-box .row .field { flex:1; min-width: 200px; }
			.form-box .row .field label { display:block; font-size:0.85em; margin-bottom:3px; }
			.form-box .row .field input, .form-box .row .field select { width:100%; box-sizing:border-box; }
			.btn-link { cursor:pointer; color:#2a6fdb; text-decoration: underline; margin-right:6px; }
			.hint { font-size:0.85em; color:#666; }
		</style>
		<script type="text/javascript">
			function loadServerOptions(){
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/server/listServer.do",
					data:{USE_YN:"Y"},
					dataType:"json",
					success:function(data, status, request){
						var list = data.returnObj || [];
						var sel = $("#SERVER_ID");
						sel.empty();
						for(var i=0; i<list.length; i++){
							sel.append("<option value='"+list[i].SERVER_ID+"'>"+list[i].SERVER_NM+"</option>");
						}
						loadJobList();
					}
				});
			}

			function loadJobList(){
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/listJob.do",
					data:{},
					dataType:"json",
					success:function(data, status, request){
						var list = data.returnObj || [];
						var tbody = $("#JOB_TBODY");
						tbody.empty();
						for(var i=0; i<list.length; i++){
							var row = list[i];
							var tr = $("<tr></tr>");
							tr.append("<td>"+(row.JOB_NM||"")+"</td>");
							tr.append("<td>"+(row.SERVER_NM||"")+"</td>");
							tr.append("<td>"+(row.CRON_EXPRESSION||"")+"</td>");
							tr.append("<td>"+(row.SCHEDULE_USE_YN||"")+"</td>");
							tr.append("<td>"+(row.OWNER_NM||"")+"</td>");
							tr.append("<td>"+(row.USE_YN||"")+"</td>");
							var tdAction = $("<td></td>");
							var editLink = $("<span class='btn-link'>수정</span>");
							editLink.data("row", row);
							editLink.click(function(){ editJob($(this).data("row")); });
							var delLink = $("<span class='btn-link'>삭제</span>");
							delLink.data("id", row.JOB_ID);
							delLink.click(function(){ deleteJob($(this).data("id")); });
							var startLink = $("<span class='btn-link'>즉시시작</span>");
							startLink.data("row", row);
							startLink.click(function(){ var r=$(this).data("row"); startJobNow(r.JOB_ID, r.JOB_NM); });
							tdAction.append(editLink).append(delLink).append(startLink);
							tr.append(tdAction);
							tbody.append(tr);
						}
					}
				});
			}

			function editJob(row){
				$("#JOB_ID").val(row.JOB_ID);
				$("#JOB_NM").val(row.JOB_NM);
				$("#SERVER_ID").val(row.SERVER_ID);
				$("#DESCRIPTION").val(row.DESCRIPTION);
				$("#CRON_EXPRESSION").val(row.CRON_EXPRESSION);
				$("#SCHEDULE_USE_YN").val(row.SCHEDULE_USE_YN);
				$("#OWNER_NM").val(row.OWNER_NM);
				$("#USE_YN").val(row.USE_YN);
				$("#FORM_TITLE").text("배치JOB 수정");
				loadJobParams(row.JOB_ID);
			}

			function resetForm(){
				document.JOB_FORM.reset();
				$("#JOB_ID").val("");
				$("#FORM_TITLE").text("배치JOB 신규등록");
				$("#PARAM_TBODY").empty();
				addParamRow("", "");
			}

			/*** 실행파라메터 행 관리 시작 ***/
			function addParamRow(name, value){
				var tr = $("<tr></tr>");
				tr.append($("<td></td>").append(
					$("<input type='text' name='PARAM_NAME'>").val(name || "")
				));
				tr.append($("<td></td>").append(
					$("<input type='text' name='PARAM_VALUE'>").val(value || "")
				));
				var tdAction = $("<td></td>");
				var delLink = $("<span class='btn-link'>삭제</span>");
				delLink.click(function(){ $(this).closest("tr").remove(); });
				tdAction.append(delLink);
				tr.append(tdAction);
				$("#PARAM_TBODY").append(tr);
			}

			function loadJobParams(jobId){
				$("#PARAM_TBODY").empty();
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/listJobParam.do",
					data:{JOB_ID: jobId},
					dataType:"json",
					success:function(data, status, request){
						var list = data.returnObj || [];
						if(list.length == 0){
							addParamRow("", "");
						}else{
							for(var i=0; i<list.length; i++){
								addParamRow(list[i].PARAM_NAME, list[i].PARAM_VALUE);
							}
						}
					},
					error:function(){ addParamRow("", ""); }
				});
			}
			/*** 실행파라메터 행 관리 끝 ***/

			function saveJob(){
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/saveJob.do",
					data:$(document.JOB_FORM).serializeObject(),
					traditional:true, /* PARAM_NAME/PARAM_VALUE 배열을 PARAM_NAME=a&PARAM_NAME=b 형태(브라켓 없이)로 직렬화 */
					dataType:"json",
					success:function(data, status, request){
						var successYn = request.getResponseHeader('successYn');
						if('Y' == successYn){
							alert("저장되었습니다.");
							resetForm();
							loadJobList();
						}else{
							alert("저장 실패: " + decodeURIComponent(request.getResponseHeader('errMsg')));
						}
					}
				});
			}

			function deleteJob(jobId){
				if(!confirm("삭제하시겠습니까?")) return;
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/deleteJobMeta.do",
					data:{JOB_ID: jobId},
					dataType:"json",
					success:function(){ loadJobList(); }
				});
			}

			function startJobNow(jobId, jobNm){
				if(!confirm(jobNm+" 을(를) 등록된 파라메터로 즉시 시작하시겠습니까?")) return;
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/startJob.do",
					data:{JOB_ID: jobId},
					dataType:"json",
					success:function(data){ alert("결과: " + JSON.stringify(data.returnObj)); },
					error:function(){ alert("호출 실패"); }
				});
			}

			function loadRegisteredJobs(){
				var serverId = $("#SERVER_ID").val();
				if(!serverId){ alert("배치서버를 먼저 선택하세요."); return; }
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/getRegisteredJobs.do",
					data:{SERVER_ID: serverId},
					dataType:"json",
					success:function(data, status, request){
						var successYn = request.getResponseHeader('successYn');
						if('Y' != successYn){ alert("조회 실패"); return; }
						var jobList = (data.returnObj && data.returnObj.jobList) || [];
						$("#REGISTERED_JOBS").text(jobList.length > 0 ? jobList.join(", ") : "(등록된 Job 없음)");
					},
					error:function(){ alert("호출 실패"); }
				});
			}

			$(document).ready(function(){
				resetForm();
				loadServerOptions();
			});
		</script>
	</head>
	<body class="is-preload">
		<div id="wrapper">
			<div id="main">
				<div class="inner">
					<jsp:include page="/WEB-INF/views/common/top.jsp" flush="true"/>
					<section>
						<h2>배치JOB 등록</h2>
						<p class="hint">JOB명은 dstone-batch측 @AutoRegJob(name=...) 값과 반드시 일치해야 합니다.
							<span class="btn-link" onclick="javascript:loadRegisteredJobs();">선택한 서버에 현재 등록된 Job명 조회</span>
							: <span id="REGISTERED_JOBS"></span>
						</p>

						<div class="form-box">
							<h3 id="FORM_TITLE">배치JOB 신규등록</h3>
							<form name="JOB_FORM">
								<input type="hidden" id="JOB_ID" name="JOB_ID" value="">
								<div class="row">
									<div class="field"><label>JOB명</label><input type="text" id="JOB_NM" name="JOB_NM"></div>
									<div class="field"><label>대상 배치서버</label><select id="SERVER_ID" name="SERVER_ID"></select></div>
									<div class="field"><label>담당자</label><input type="text" id="OWNER_NM" name="OWNER_NM"></div>
								</div>
								<div class="row">
									<div class="field"><label>설명</label><input type="text" id="DESCRIPTION" name="DESCRIPTION"></div>
									<div class="field"><label>CRON 표현식 (예: 0 0 1 * * *)</label><input type="text" id="CRON_EXPRESSION" name="CRON_EXPRESSION"></div>
									<div class="field">
										<label>자동스케줄 사용여부</label>
										<select id="SCHEDULE_USE_YN" name="SCHEDULE_USE_YN">
											<option value="N">N</option>
											<option value="Y">Y</option>
										</select>
									</div>
									<div class="field">
										<label>사용여부</label>
										<select id="USE_YN" name="USE_YN">
											<option value="Y">Y</option>
											<option value="N">N</option>
										</select>
									</div>
								</div>
								<h4>실행파라메터 <span class="btn-link" onclick="javascript:addParamRow('','');">+ 행추가</span></h4>
								<table class="grid">
									<thead>
										<tr><th style="width:35%">파라메터명</th><th style="width:45%">값</th><th>관리</th></tr>
									</thead>
									<tbody id="PARAM_TBODY"></tbody>
								</table>

								<button type="button" class="button primary" onclick="javascript:saveJob();">저장</button>
								<button type="button" class="button" onclick="javascript:resetForm();">신규작성</button>
							</form>
						</div>

						<table class="grid">
							<thead>
								<tr><th>JOB명</th><th>배치서버</th><th>CRON</th><th>스케줄사용</th><th>담당자</th><th>사용여부</th><th>관리</th></tr>
							</thead>
							<tbody id="JOB_TBODY"></tbody>
						</table>

					</section>
				</div>
			</div>
			<jsp:include page="/WEB-INF/views/common/left.jsp" flush="true"/>
		</div>
	</body>
</html>
