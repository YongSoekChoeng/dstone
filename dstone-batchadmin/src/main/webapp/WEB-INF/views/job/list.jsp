<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
		<jsp:include page="/WEB-INF/views/common/header.jsp" flush="true"/>
		<style type="text/css">
			table.grid { width:100%; border-collapse: collapse; margin-top: 10px; }
			table.grid th, table.grid td { border: 1px solid #ddd; padding: 6px 8px; font-size: 0.9em; }
			table.grid th { background: #f5f5f5; }
			.search-box { border:1px solid #ddd; border-radius:6px; padding: 14px; margin-bottom: 10px; }
			.search-box .row { display:flex; gap:12px; margin-bottom:8px; flex-wrap: wrap; align-items:flex-end; }
			.search-box .row .field label { display:block; font-size:0.85em; margin-bottom:3px; }
			.btn-link { cursor:pointer; color:#2a6fdb; text-decoration: underline; margin-right:6px; }
			.pager { margin-top: 10px; text-align:center; }
			.pager span { cursor:pointer; margin: 0 4px; }
			.pager span.current { font-weight:bold; text-decoration: underline; cursor:default; }
			.status-STARTED, .status-STARTING { color:#2a7d2a; font-weight:bold; }
			.status-FAILED { color:#c0392b; font-weight:bold; }
			.status-STOPPED, .status-STOPPING { color:#b8860b; font-weight:bold; }
		</style>
		<script type="text/javascript">
			var CUR_PAGE_NUM = 1;

			function loadServerOptions(callback){
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/server/listServer.do",
					data:{USE_YN:"Y"},
					dataType:"json",
					success:function(data, status, request){
						var list = data.returnObj || [];
						var sel = $("#SEARCH_SERVER_ID");
						sel.empty();
						for(var i=0; i<list.length; i++){
							sel.append("<option value='"+list[i].SERVER_ID+"'>"+list[i].SERVER_NM+"</option>");
						}
						if(callback) callback();
					}
				});
			}

			function goSearch(pageNum){
				CUR_PAGE_NUM = pageNum || 1;
				var params = $(document.SEARCH_FORM).serializeObject();
				params.PAGE_NUM = CUR_PAGE_NUM;
				// <input type="date"> 는 yyyy-MM-dd 형식이므로 서버에서 쓰는 yyyyMMdd(8자리)로 변환
				if(params.SEARCH_START_DT_FROM) params.SEARCH_START_DT_FROM = params.SEARCH_START_DT_FROM.replace(/-/g, "");
				if(params.SEARCH_START_DT_TO) params.SEARCH_START_DT_TO = params.SEARCH_START_DT_TO.replace(/-/g, "");
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/listJobExecution.do",
					data:params,
					dataType:"json",
					success:function(data, status, request){
						var successYn = request.getResponseHeader('successYn');
						if('Y' != successYn){
							alert("조회 실패: " + decodeURIComponent(request.getResponseHeader('errMsg')));
							return;
						}
						renderList(data.returnObj.returnObj || []);
						renderPager(data.returnObj.pageUtil);
					},
					error:function(){ alert("조회중 오류가 발생했습니다."); }
				});
			}

			function renderList(list){
				var tbody = $("#JOB_TBODY");
				tbody.empty();
				var serverId = $("#SEARCH_SERVER_ID").val();
				for(var i=0; i<list.length; i++){
					var row = list[i];
					var tr = $("<tr></tr>");
					tr.append("<td>"+row.JOB_INSTANCE_ID+"</td>");
					tr.append("<td><a href='<%=request.getContextPath()%>/job/detail.do?SERVER_ID="+serverId+"&JOB_INSTANCE_ID="+row.JOB_INSTANCE_ID+"'>"+(row.JOB_NAME||"")+"</a></td>");
					tr.append("<td class='status-"+(row.STATUS||"")+"'>"+(row.STATUS||"")+"</td>");
					tr.append("<td>"+(row.START_TIME||"")+"</td>");
					tr.append("<td>"+(row.EXIT_MESSAGE||"")+"</td>");
					tr.append("<td>"+(row.OWNER_NM||"")+"</td>");
					var tdAction = $("<td></td>");
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
					}
					tr.append(tdAction);
					tbody.append(tr);
				}
			}

			function renderPager(pageUtil){
				var pager = $("#PAGER");
				pager.empty();
				if(!pageUtil || pageUtil.intTotalPage <= 1) return;
				for(var i=pageUtil.intPgStartNum; i<=pageUtil.intPgEndNum; i++){
					var span = $("<span>"+i+"</span>");
					if(i == pageUtil.intPageNum){
						span.addClass("current");
					}else{
						span.click((function(pageNum){ return function(){ goSearch(pageNum); }; })(i));
					}
					pager.append(span);
				}
			}

			function controlJob(action, jobExecutionId){
				if(!confirm(action+" 하시겠습니까?")) return;
				$.ajax({
					type:"POST",
					url:"<%=request.getContextPath()%>/job/"+action+".do",
					data:{SERVER_ID: $("#SEARCH_SERVER_ID").val(), JOB_EXECUTION_ID: jobExecutionId},
					dataType:"json",
					success:function(data, status, request){
						alert("결과: " + JSON.stringify(data.returnObj));
						goSearch(CUR_PAGE_NUM);
					},
					error:function(){ alert("호출 실패"); }
				});
			}

			$(document).ready(function(){
				loadServerOptions(function(){ goSearch(1); });
			});
		</script>
	</head>
	<body class="is-preload">
		<div id="wrapper">
			<div id="main">
				<div class="inner">
					<jsp:include page="/WEB-INF/views/common/top.jsp" flush="true"/>
					<section>
						<h2>배치JOB 목록조회</h2>

						<div class="search-box">
							<form name="SEARCH_FORM">
								<div class="row">
									<div class="field"><label>배치서버</label><select id="SEARCH_SERVER_ID" name="SERVER_ID"></select></div>
									<div class="field"><label>실행일자 From</label><input type="date" name="SEARCH_START_DT_FROM" id="SEARCH_START_DT_FROM_UI"></div>
									<div class="field"><label>실행일자 To</label><input type="date" name="SEARCH_START_DT_TO" id="SEARCH_START_DT_TO_UI"></div>
									<div class="field"><label>JOB아이디</label><input type="text" name="SEARCH_JOB_INSTANCE_ID"></div>
									<div class="field"><label>JOB명</label><input type="text" name="SEARCH_JOB_NAME"></div>
									<div class="field"><button type="button" class="button primary" onclick="javascript:goSearch(1);">조회</button></div>
								</div>
							</form>
						</div>

						<table class="grid">
							<thead>
								<tr>
									<th>JOB아이디</th><th>JOB명</th><th>상태</th><th>최종수행일시</th><th>결과메세지</th><th>담당자</th><th>관리</th>
								</tr>
							</thead>
							<tbody id="JOB_TBODY"></tbody>
						</table>
						<div class="pager" id="PAGER"></div>

					</section>
				</div>
			</div>
			<jsp:include page="/WEB-INF/views/common/left.jsp" flush="true"/>
		</div>
	</body>
</html>
