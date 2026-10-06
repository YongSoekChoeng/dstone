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
		</style>
		<script type="text/javascript">
			var EDIT_SERVER_ID = "";

			function loadServerList(){
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/server/listServer.do",
					data:{},
					dataType:"json",
					success:function(data, status, request){
						var list = data.returnObj || [];
						var tbody = $("#SERVER_TBODY");
						tbody.empty();
						for(var i=0; i<list.length; i++){
							var row = list[i];
							var tr = $("<tr></tr>");
							tr.append("<td>"+row.SERVER_ID+"</td>");
							tr.append("<td>"+(row.SERVER_NM||"")+"</td>");
							tr.append("<td>"+(row.REST_BASE_URL||"")+"</td>");
							tr.append("<td>"+(row.DB_HOST||"")+":"+(row.DB_PORT||"")+"/"+(row.DB_NAME||"")+"</td>");
							tr.append("<td>"+(row.DBMS_TYPE||"")+"</td>");
							tr.append("<td>"+(row.USE_YN||"")+"</td>");
							var tdAction = $("<td></td>");
							var editLink = $("<span class='btn-link'>수정</span>");
							editLink.data("row", row);
							editLink.click(function(){ editServer($(this).data("row")); });
							var delLink = $("<span class='btn-link'>삭제</span>");
							delLink.data("id", row.SERVER_ID);
							delLink.click(function(){ deleteServer($(this).data("id")); });
							var healthLink = $("<span class='btn-link'>상태점검</span>");
							healthLink.data("id", row.SERVER_ID);
							healthLink.click(function(){ healthCheckServer($(this).data("id")); });
							tdAction.append(editLink).append(delLink).append(healthLink);
							tr.append(tdAction);
							tbody.append(tr);
						}
					}
				});
			}

			function editServer(row){
				EDIT_SERVER_ID = row.SERVER_ID;
				$("#SERVER_ID").val(row.SERVER_ID);
				$("#SERVER_NM").val(row.SERVER_NM);
				$("#REST_BASE_URL").val(row.REST_BASE_URL);
				$("#DB_HOST").val(row.DB_HOST);
				$("#DB_PORT").val(row.DB_PORT);
				$("#DB_NAME").val(row.DB_NAME);
				$("#DB_USER").val(row.DB_USER);
				$("#DB_PASSWORD").val("");
				$("#DBMS_TYPE").val(row.DBMS_TYPE);
				$("#USE_YN").val(row.USE_YN);
				$("#DESCRIPTION").val(row.DESCRIPTION);
				$("#FORM_TITLE").text("배치서버 수정");
			}

			function resetForm(){
				EDIT_SERVER_ID = "";
				document.SERVER_FORM.reset();
				$("#SERVER_ID").val("");
				$("#FORM_TITLE").text("배치서버 신규등록");
			}

			function saveServer(){
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/server/saveServer.do",
					data:$(document.SERVER_FORM).serializeObject(),
					dataType:"json",
					success:function(data, status, request){
						var successYn = request.getResponseHeader('successYn');
						if('Y' == successYn){
							alert("저장되었습니다.");
							resetForm();
							loadServerList();
						}else{
							alert("저장 실패: " + decodeURIComponent(request.getResponseHeader('errMsg')));
						}
					}
				});
			}

			function deleteServer(serverId){
				if(!confirm("삭제하시겠습니까?")) return;
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/server/deleteServer.do",
					data:{SERVER_ID: serverId},
					dataType:"json",
					success:function(data, status, request){
						loadServerList();
					}
				});
			}

			function healthCheckServer(serverId){
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/server/healthCheck.do",
					data:{SERVER_ID: serverId},
					dataType:"json",
					success:function(data, status, request){
						alert("상태: " + JSON.stringify(data.returnObj));
					},
					error:function(){
						alert("상태점검 호출 실패");
					}
				});
			}

			$(document).ready(function(){ loadServerList(); });
		</script>
	</head>
	<body class="is-preload">
		<div id="wrapper">
			<div id="main">
				<div class="inner">
					<jsp:include page="/WEB-INF/views/common/top.jsp" flush="true"/>
					<section>
						<h2>배치서버 관리</h2>

						<div class="form-box">
							<h3 id="FORM_TITLE">배치서버 신규등록</h3>
							<form name="SERVER_FORM">
								<input type="hidden" id="SERVER_ID" name="SERVER_ID" value="">
								<div class="row">
									<div class="field"><label>서버명</label><input type="text" id="SERVER_NM" name="SERVER_NM"></div>
									<div class="field"><label>REST BASE URL (예: http://localhost:6081/batch)</label><input type="text" id="REST_BASE_URL" name="REST_BASE_URL"></div>
								</div>
								<div class="row">
									<div class="field"><label>배치메타DB 호스트</label><input type="text" id="DB_HOST" name="DB_HOST"></div>
									<div class="field"><label>배치메타DB 포트</label><input type="text" id="DB_PORT" name="DB_PORT"></div>
									<div class="field"><label>배치메타DB 명</label><input type="text" id="DB_NAME" name="DB_NAME"></div>
								</div>
								<div class="row">
									<div class="field"><label>DB 사용자</label><input type="text" id="DB_USER" name="DB_USER"></div>
									<div class="field"><label>DB 비밀번호 (수정시 변경할 때만 입력)</label><input type="password" id="DB_PASSWORD" name="DB_PASSWORD"></div>
									<div class="field">
										<label>DBMS 종류</label>
										<select id="DBMS_TYPE" name="DBMS_TYPE">
											<option value="MYSQL">MYSQL</option>
											<option value="POSTGRES">POSTGRES</option>
										</select>
									</div>
								</div>
								<div class="row">
									<div class="field">
										<label>사용여부</label>
										<select id="USE_YN" name="USE_YN">
											<option value="Y">Y</option>
											<option value="N">N</option>
										</select>
									</div>
									<div class="field"><label>설명</label><input type="text" id="DESCRIPTION" name="DESCRIPTION"></div>
								</div>
								<button type="button" class="button primary" onclick="javascript:saveServer();">저장</button>
								<button type="button" class="button" onclick="javascript:resetForm();">신규작성</button>
							</form>
						</div>

						<table class="grid">
							<thead>
								<tr>
									<th>서버ID</th><th>서버명</th><th>REST BASE URL</th><th>배치메타DB</th><th>DBMS</th><th>사용여부</th><th>관리</th>
								</tr>
							</thead>
							<tbody id="SERVER_TBODY"></tbody>
						</table>

					</section>
				</div>
			</div>
			<jsp:include page="/WEB-INF/views/common/left.jsp" flush="true"/>
		</div>
	</body>
</html>
