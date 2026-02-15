<%@page import="net.dstone.common.utils.SystemUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%    

/******************************************* 변수 선언 시작 *******************************************/              
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);

/******************************************* 변수 선언 끝 *********************************************/           
    

%> 
<!DOCTYPE HTML>
<!--
	Minimaxing by HTML5 UP
	html5up.net | @ajlkn
	Free for personal and commercial use under the CCA 3.0 license (html5up.net/license)
-->
<html>

	<!-- Head -->
	<jsp:include page="../common/head.jsp"></jsp:include> 
	
	<!-- Script -->
	<script type="text/javascript"> 
		var timeOutObj;
		$(document).ready(function(){
			// 전체체크/해제
			$("#checkAll").click(function() {
				if($("#checkAll").is(":checked")){
					$("input[name=ANALYZE_JOB_KIND]").prop("checked", true);
				} else {
					$("input[name=ANALYZE_JOB_KIND]").prop("checked", false);
				}
			});
			
			$("input[name=ANALYZE_JOB_KIND]").click(function() {
				var total = $("input[name=ANALYZE_JOB_KIND]").length;
				var checked = $("input[name=ANALYZE_JOB_KIND]:checked").length;
				if(total != checked){
					$("#checkAll").prop("checked", false);
				}else{
					$("#checkAll").prop("checked", true); 
				}
			});			
			// 분석시작버튼 클릭
			$("#btnAnalyze").on("click", btnAnalyzeOnclick);
			// 분석중비버튼 클릭
			$("#btnStopAnalyze").on("click", btnStopAnalyzeOnclick);
			// DB저장버튼 클릭
			$("#btnSave").on("click", btnSaveOnclick);

		});
		
	    function goAppSelectList(){ 
	        $.ajax({  
	            type:"POST",  
	            url:"/analyzer/configuration/listSys.do", 
	            data:"{}",  
	            dataType:"json",  
	            success:function(data, status, request){ 
	                var successYn = request.getResponseHeader('successYn'); 
	                var errCd = request.getResponseHeader('errCd'); 
	                var errMsg = decodeURIComponent(request.getResponseHeader('errMsg')); 
	                if( 'Y' == successYn ){ 
	                    var forcedToUrl = request.getResponseHeader('forcedToUrl'); 
	                    if(forcedToUrl && "" != forcedToUrl){ 
	                        location.href = "/defaultLink.do?defaultLink=" + forcedToUrl; 
	                    }else{ 
	                        //console.log('success ===>>> data:' + (JSON.stringify(data))); 
	                        var appSel = $("#SYS_ID"); 
	                        appSel.empty(); 
	                        var returnList = data.returnObj.returnObj; 
	                        var lineStr = ""; 
	                        for(var i=0; i<returnList.length; i++){ 
	                            lineStr = ""; 
	                            lineStr = lineStr + "<option value='"+returnList[i].SYS_ID+"' >" + returnList[i].SYS_NM + "</option>"; 
	                            appSel.append(lineStr); 
	                        }
	                        startMonitoring();
	                    } 
	                }else{ 
	                    console.log('failure ===>>> data:' + (JSON.stringify(data))); 
	                    alert("failure errMsg:" + errMsg); 
	                } 
	            }, 
				error : function(data, status, e) { 
					alert(e); 
				} 
			}); 
		} 
	
	    function btnAnalyzeOnclick(){
	    	goAnalyze();
	    }

		function goAnalyze(){
			
			if( $("input[name=ANALYZE_JOB_KIND]:checked").length == 0 ){
				alert("작업을 선택해 주세요.");
				return;
			}
			
			if( !confirm("분석작업을 시작하시겠습니까. 시간이 다소 소요될 수 있습니다.") ){
				return;
			}
			
			$("#btnAnalyze").attr("disabled", true); 
			$("#btnSave").attr("disabled", true); 
			
	        $.ajax({ 
	            type:"POST", 
	            url:"/analyzer/analysis/doAnalyzing.do", 
	            data:encodeURIComponent(JSON.stringify($(document.FORM_ANALYSIS).serializeObject())), 
	            dataType:"json", 
	            success:function(data, status, request){ 
	                var successYn = request.getResponseHeader('successYn'); 
	                var errCd = request.getResponseHeader('errCd'); 
	                var errMsg = decodeURIComponent(request.getResponseHeader('errMsg')); 
	                if( 'Y' == successYn ){ 
	                	var forcedToUrl = request.getResponseHeader('forcedToUrl'); 
	                	if(forcedToUrl && "" != forcedToUrl){ 
	                		location.href = "/defaultLink.do?defaultLink=" + forcedToUrl; 
	                	}
	                	startMonitoring();
	                }else{ 
	                    console.log('failure ===>>> data:' + (JSON.stringify(data))); 
	                    alert("failure errMsg:" + errMsg); 
	                }
	                
	            }, 
	            error : function(data, status, e) { 
	                console.log('system error ===>>> data:' + (JSON.stringify(data))); 
	                alert("system error"); 
	            } 
	        });
		}
		
		function btnStopAnalyzeOnclick(){
			
			if( $("input[name=ANALYZE_JOB_KIND]:checked").length == 0 ){
				alert("작업을 선택해 주세요.");
				return;
			}
			
			if( !confirm("분석작업을 중지하시겠습니까. 약간의 시간이 소요될 수 있습니다.") ){
				return;
			}
			var isCompleted = "";
	        $.ajax({ 
	            type:"POST", 
	            url:"/analyzer/analysis/stopAnalyzing.do", 
	            data:encodeURIComponent(JSON.stringify($(document.FORM_ANALYSIS).serializeObject())), 
	            dataType:"json", 
	            success:function(data, status, request){ 
	                var successYn = request.getResponseHeader('successYn'); 
	                var errCd = request.getResponseHeader('errCd'); 
	                var errMsg = decodeURIComponent(request.getResponseHeader('errMsg')); 
	                if( 'Y' == successYn ){ 
	                	var forcedToUrl = request.getResponseHeader('forcedToUrl'); 
	                	if(forcedToUrl && "" != forcedToUrl){ 
	                		location.href = "/defaultLink.do?defaultLink=" + forcedToUrl; 
	                		return;
	                	}
	                	var returnList = data.returnObj;
	                	isCompleted = data.isCompleted;
	                	if(returnList){
		                	for(var i = 0; i<returnList.length; i++){
		                		var returnRow = returnList[i];
		                		var taskId = returnRow["taskId"];
		                		var taskRate = returnRow["taskRate"];
		                		console.log('taskId ===>>>' + taskId + ", taskRate:" + taskRate + " isCompleted[" + isCompleted + "]" );
		                		if( taskId && taskRate ){
			                		var imgObj = $("#ANALYZE_" + taskId + "_IMG");
			                		imgObj.css({'width' : taskRate + '%' });
			                		imgObj.attr({'alt': parseInt(taskRate) + '%' });
			                		var rateObj = $("#ANALYZE_" + taskId + "_RATE");
			                		rateObj.text(taskRate + '%');
		                		}
		                	}
	                	}
	                }else{ 
	                    console.log('failure ===>>> data:' + (JSON.stringify(data))); 
	                    alert("failure errMsg:" + errMsg); 
	                }
	    			$("#btnAnalyze").attr("disabled", false); 
	    			$("#btnSave").attr("disabled", false); 
	            }, 
	            error : function(data, status, e) { 
	                console.log('system error ===>>> data:' + (JSON.stringify(data))); 
	                alert("system error"); 
	    			$("#btnAnalyze").attr("disabled", false); 
	    			$("#btnSave").attr("disabled", false); 
	            } 
	        });	
	        return isCompleted;
		}
		
		function doMonitoring(){
			var isCompleted = "";
	        $.ajax({ 
	            type:"POST", 
	            url:"/analyzer/analysis/doMonitoring.do", 
	            data:encodeURIComponent(JSON.stringify($(document.FORM_ANALYSIS).serializeObject())), 
	            dataType:"json", 
	            success:function(data, status, request){ 
	                var successYn = request.getResponseHeader('successYn'); 
	                var errCd = request.getResponseHeader('errCd'); 
	                var errMsg = decodeURIComponent(request.getResponseHeader('errMsg')); 
	                if( 'Y' == successYn ){ 
	                	var forcedToUrl = request.getResponseHeader('forcedToUrl'); 
	                	if(forcedToUrl && "" != forcedToUrl){ 
	                		location.href = "/defaultLink.do?defaultLink=" + forcedToUrl; 
	                		return;
	                	}
	                	//console.log('success ===>>> data:' + (JSON.stringify(data))); 
	                	var returnList = data.returnObj;
	                	isCompleted = data.isCompleted;
	                	if(returnList){
		                	for(var i = 0; i<returnList.length; i++){
		                		var returnRow = returnList[i];
		                		var taskId = returnRow["taskId"];
		                		var taskRate = returnRow["taskRate"];
		                		console.log('taskId ===>>>' + taskId + ", taskRate:" + taskRate + " isCompleted[" + isCompleted + "]" );
		                		if( taskId && taskRate ){
			                		var imgObj = $("#ANALYZE_" + taskId + "_IMG");
			                		imgObj.css({'width' : taskRate + '%' });
			                		var rateObj = $("#ANALYZE_" + taskId + "_RATE");
			                		rateObj.text(taskRate + '%');
		                		}
		                	}
	                	}
	                	if( 'Y' == isCompleted ){ 
	                		//stopMonitoring();
	            			$("#btnAnalyze").attr("disabled", false); 
	            			$("#btnSave").attr("disabled", false); 
	                	}
	                }else{ 
	                    console.log('failure ===>>> data:' + (JSON.stringify(data))); 
	                    alert("failure errMsg:" + errMsg); 
	                }
	            }, 
	            error : function(data, status, e) { 
	                console.log('system error ===>>> data:' + (JSON.stringify(data))); 
	                alert("system error"); 
	            } 
	        });	
	        return isCompleted;
		}

		function startMonitoring(){
			console.log("||================== startMonitoring ==================||" );
			doMonitoring();
			timeOutObj = setTimeout(startMonitoring, 3 * 1000);
		}

		function stopMonitoring(){
			console.log("||================== stopMonitoring ==================||" );
			if(timeOutObj){
				clearTimeout(timeOutObj);
			}
		}
		
		function btnSaveOnclick(){
			if( !confirm("분석정보를 DB에 저장하시겠습니까. 시간이 다소 소요될 수 있습니다.") ){
				return;
			}
			stopMonitoring();
			$("#btnAnalyze").attr("disabled", true); 
			$("#btnSave").attr("disabled", true); 
			
			var isCompleted = "";
	        $.ajax({ 
	            type:"POST", 
	            url:"/analyzer/analysis/saveToDB.do", 
	            data:encodeURIComponent(JSON.stringify($(document.FORM_ANALYSIS).serializeObject())), 
	            dataType:"json", 
	            success:function(data, status, request){ 
	                var successYn = request.getResponseHeader('successYn'); 
	                var errCd = request.getResponseHeader('errCd'); 
	                var errMsg = decodeURIComponent(request.getResponseHeader('errMsg')); 
	                if( 'Y' == successYn ){ 
	                	console.log('isCompleted ===>>> ' + isCompleted); 
	                	alert('success'); 
	                	var forcedToUrl = request.getResponseHeader('forcedToUrl'); 
	                	if(forcedToUrl && "" != forcedToUrl){ 
	                		location.href = "/defaultLink.do?defaultLink=" + forcedToUrl; 
	                		return;
	                	}
	                	//console.log('success ===>>> data:' + (JSON.stringify(data))); 
	                	isCompleted = data.isCompleted;
	                }else{ 
	                    console.log('failure ===>>> data:' + (JSON.stringify(data))); 
	                    alert("failure errMsg:" + errMsg); 
	                }
	    			$("#btnAnalyze").attr("disabled", false); 
	    			$("#btnSave").attr("disabled", false); 
	    			startMonitoring();
	            }, 
	            error : function(data, status, e) { 
	                console.log('system error ===>>> data:' + (JSON.stringify(data))); 
	                alert("system error"); 
	    			$("#btnAnalyze").attr("disabled", false); 
	    			$("#btnSave").attr("disabled", false);
	    			startMonitoring();
	            } 
	        });	
	        return isCompleted;
		}
		
		function checkStep(step){
			var chkObj = $("input[name=ANALYZE_JOB_KIND]");
			var chkObjItemList = new Array();
			for(var i=0; i<chkObj.length; i++){
				var chkObjItem = chkObj[i];
				var fromInt = step*10;
				var toInt = (step+1)*10;
				if( fromInt<chkObjItem.value && chkObjItem.value<toInt ){
					chkObjItemList[chkObjItemList.length] = chkObjItem;
				}	
			}
			if(chkObjItemList.length > 0){
				var firstCheckedYn = chkObjItemList[0].checked;
				for(var i=0; i<chkObjItemList.length; i++){
					var chkObjItem = chkObjItemList[i];
					chkObjItem.checked = !firstCheckedYn;
				}
			}
		}
		
	</script> 

	<body  onLoad="javascript:goAppSelectList();" >
		<div id="page-wrapper">

			<!-- Header -->
			<jsp:include page="../common/header.jsp"></jsp:include>
			
			<!-- Main -->
				<div id="main">
					<div class="container">
						<div class="row main-row">
							<div class="col-12">

								<section>
									<h2 align="center" style="font-weight: bold;">Application Analysis</h2>
									<p>
									Application 을 분석한다.
									</p>
								</section>

								<!--폼 시작--> 
								<form name="FORM_ANALYSIS" id="FORM_ANALYSIS" method="post" >
									
								<section>
									<h2>Application To Be Analyzed</h2>
									
									<ul class="small-image-list">
										<li>
											<h4>1. Application 선택</h4>
											<p>
												<select name="SYS_ID" id="SYS_ID" >
												</select>
											</p>
										</li>
										<li>
											<h4>2. 단계별 분석 선택</h4>
										</li>
									</ul>
									
									<table width="100%" border="1">
										<thead>
											<tr>
												<th width="15%">분류</th>
												<th width="3%"><input type="checkbox" id="checkAll" /></th>
												<th width="*">작업</th>
												<th width="7%">진행</th>
											</tr>
										</thead>
										<tbody>
											<!-- 클래스분석 -->
											<tr>
												<td rowspan="6" ><a href="javascript:checkStep(1);">1. 클래스분석</a></td>
												<td align="center">
													<input type="checkbox" name="ANALYZE_JOB_KIND" value="<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_11_ANALYZE_CLASS%>"  />
												</td>
												<td>1-1. 클래스파일리스트 에서 패키지ID/클래스ID/클래스명/기능종류 등이 담긴 클래스분석파일리스트 추출</td>
												<td id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_11_ANALYZE_ID_CLASS%>_RATE" align="right">0.00%</td>
											</tr>
											<tr>
												<td colspan="3" >
													<img src="<%=requestUtil.getStrContextPath()%>/analyzer/images/pic2.jpg" id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_11_ANALYZE_ID_CLASS%>_IMG" alt="" style="vertical-align:middle; height: 15px; width: 0%;" />
												</td>
											</tr>
											<tr>
												<td align="center">
													<input type="checkbox" name="ANALYZE_JOB_KIND" value="<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_12_ANALYZE_CLASS_IMPL%>"  />
												</td>
												<td>1-2. 클래스파일리스트 에서 인터페이스구현하위클래스ID목록을 추출하여 클래스분석파일리스트에 추가</td>
												<td id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_12_ANALYZE_ID_CLASS_IMPL%>_RATE" align="right">0.00%</td>
											</tr>
											<tr>
												<td colspan="3" >
													<img src="<%=requestUtil.getStrContextPath()%>/analyzer/images/pic2.jpg" id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_12_ANALYZE_ID_CLASS_IMPL%>_IMG" alt="" style="vertical-align:middle; height: 15px; width: 0%;" />
												</td>
											</tr>
											<tr>
												<td align="center">
													<input type="checkbox" name="ANALYZE_JOB_KIND" value="<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_13_ANALYZE_CLASS_ALIAS%>"  />
												</td>
												<td>1-3. 클래스파일리스트 에서 호출알리아스 추출하여 클래스분석파일리스트에 추가</td>
												<td id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_13_ANALYZE_ID_CLASS_ALIAS%>_RATE" align="right">0.00%</td>
											</tr>
											<tr>
												<td colspan="3" >
													<img src="<%=requestUtil.getStrContextPath()%>/analyzer/images/pic2.jpg" id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_13_ANALYZE_ID_CLASS_ALIAS%>_IMG" alt="" style="vertical-align:middle; height: 15px; width: 0%;" />
												</td>
											</tr>
											<tr>
												<td colspan="4" style="background-color: #d1cfcd;" > </td>
											</tr>
											
											<!-- 쿼리분석 -->
											<tr>
												<td rowspan="4" ><a href="javascript:checkStep(2);">2. 쿼리분석</a></td>
												<td align="center">
													<input type="checkbox" name="ANALYZE_JOB_KIND" value="<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_21_ANALYZE_QUERY%>"  />
												</td>
												<td>2-1. 쿼리파일리스트 에서 KEY/네임스페이스/쿼리ID/쿼리종류/쿼리내용 등이 담긴 쿼리분석파일리스트 추출</td>
												<td id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_21_ANALYZE_ID_QUERY%>_RATE" align="right">0.00%</td>
											</tr>
											<tr>
												<td colspan="3" >
													<img src="<%=requestUtil.getStrContextPath()%>/analyzer/images/pic2.jpg" id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_21_ANALYZE_ID_QUERY%>_IMG" alt="" style="vertical-align:middle; height: 15px; width: 0%;" />
												</td>
											</tr>
											<tr>
												<td align="center">
													<input type="checkbox" name="ANALYZE_JOB_KIND" value="<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_22_ANALYZE_QUERY_CALLTBL%>"  />
												</td>
												<td>2-2. 쿼리분석파일리스트 에 호출테이블ID정보목록 추가</td>
												<td id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_22_ANALYZE_ID_QUERY_CALLTBL%>_RATE" align="right">0.00%</td>
											</tr>
											<tr>
												<td colspan="3" >
													<img src="<%=requestUtil.getStrContextPath()%>/analyzer/images/pic2.jpg" id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_22_ANALYZE_ID_QUERY_CALLTBL%>_IMG" alt="" style="vertical-align:middle; height: 15px; width: 0%;" />
												</td>
											</tr>
											<tr>
												<td colspan="4" style="background-color: #d1cfcd;" > </td>
											</tr>
											
											<!-- 메소드분석 -->
											<tr>
												<td rowspan="6" ><a href="javascript:checkStep(3);">3. 메소드분석</a></td>
												<td align="center">
													<input type="checkbox" name="ANALYZE_JOB_KIND" value="<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_31_ANALYZE_MTD%>"  />
												</td>
												<td>3-1. 클래스파일리스트 에서 기능ID/메소드ID/메소드명/메소드URL/메소드내용 등이 담긴 메소드분석파일리스트 추출</td>
												<td id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_31_ANALYZE_ID_MTD%>_RATE" align="right">0.00%</td>
											</tr>
											<tr>
												<td colspan="3" >
													<img src="<%=requestUtil.getStrContextPath()%>/analyzer/images/pic2.jpg" id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_31_ANALYZE_ID_MTD%>_IMG" alt="" style="vertical-align:middle; height: 15px; width: 0%;" />
												</td>
											</tr>
											<tr>
												<td align="center">
													<input type="checkbox" name="ANALYZE_JOB_KIND" value="<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_32_ANALYZE_MTD_CALLMTD%>"  />
												</td>
												<td>3-2. 메소드분석파일리스트 에 메소드내 타 호출메소드 목록 추가</td>
												<td id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_32_ANALYZE_ID_MTD_CALLMTD%>_RATE" align="right">0.00%</td>
											</tr>
											<tr>
												<td colspan="3" >
													<img src="<%=requestUtil.getStrContextPath()%>/analyzer/images/pic2.jpg" id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_32_ANALYZE_ID_MTD_CALLMTD%>_IMG" alt="" style="vertical-align:middle; height: 15px; width: 0%;" />
												</td>
											</tr>
											<tr>
												<td align="center">
													<input type="checkbox" name="ANALYZE_JOB_KIND" value="<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_33_ANALYZE_MTD_CALLTBL%>"  />
												</td>
												<td>3-3. 메소드분석파일리스트 에 메소드내 호출테이블 목록 추가</td>
												<td id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_33_ANALYZE_ID_MTD_CALLTBL%>_RATE" align="right">0.00%</td>
											</tr>
											<tr>
												<td colspan="3" >
													<img src="<%=requestUtil.getStrContextPath()%>/analyzer/images/pic2.jpg" id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_33_ANALYZE_ID_MTD_CALLTBL%>_IMG" alt="" style="vertical-align:middle; height: 15px; width: 0%;" />
												</td>
											</tr>
											<tr>
												<td colspan="4" style="background-color: #d1cfcd;" > </td>
											</tr>
											
											<!-- UI분석 -->
											<tr>
												<td rowspan="4" ><a href="javascript:checkStep(4);">4. UI분석</a></td>
												<td align="center">
													<input type="checkbox" name="ANALYZE_JOB_KIND" value="<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_41_ANALYZE_UI%>"  />
												</td>
												<td>4-1. UI파일로부터  UI아이디/UI명 등이 담긴 UI분석파일목록 추출</td>
												<td id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_41_ANALYZE_ID_UI%>_RATE" align="right">0.00%</td>
											</tr>
											<tr>
												<td colspan="3" >
													<img src="<%=requestUtil.getStrContextPath()%>/analyzer/images/pic2.jpg" id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_41_ANALYZE_ID_UI%>_IMG" alt="" style="vertical-align:middle; height: 15px; width: 0%;" />
												</td>
											</tr>
											<tr>
												<td align="center">
													<input type="checkbox" name="ANALYZE_JOB_KIND" value="<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_42_ANALYZE_UI_LINK%>"  />
												</td>
												<td>4-2. UI파일로부터 링크정보 추출</td>
												<td id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_42_ANALYZE_ID_UI_LINK%>_RATE" align="right">0.00%</td>
											</tr>
											<tr>
												<td colspan="3" >
													<img src="<%=requestUtil.getStrContextPath()%>/analyzer/images/pic2.jpg" id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_42_ANALYZE_ID_UI_LINK%>_IMG" alt="" style="vertical-align:middle; height: 15px; width: 0%;" />
												</td>
											</tr>
											<tr>
												<td colspan="4" style="background-color: #d1cfcd;" > </td>
											</tr>

											<!-- METRIX 추출 -->
											<tr>
												<td rowspan="2" ><a href="javascript:checkStep(5);">5. METRIX 추출</a></td>
												<td align="center">
													<input type="checkbox" name="ANALYZE_JOB_KIND" value="<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_51_ANALYZE_SAVE_METRIX%>"  />
												</td>
												<td>5-1. 클래스, 쿼리, 메소드, UI분석결과를 통한 METRIX 추출</td>
												<td id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_51_ANALYZE_ID_SAVE_METRIX%>_RATE" align="right">0.00%</td>
											</tr>
											<tr>
												<td colspan="3" >
													<img src="<%=requestUtil.getStrContextPath()%>/analyzer/images/pic2.jpg" id="ANALYZE_<%=net.dstone.boot.tools.analyzer.AppAnalyzer.JOB_KIND_51_ANALYZE_ID_SAVE_METRIX%>_IMG" alt="" style="vertical-align:middle; height: 15px; width: 0%;" />
												</td>
											</tr>
											<tr>
												<td colspan="4" style="background-color: #d1cfcd;" > </td>
											</tr>
													
										</tbody>
									</table>

									<h2 align="right">
										<button type="button" id="btnAnalyze"  class="mini_button" >분석시작</button>
										<button type="button" id="btnStopAnalyze"  class="mini_button" >분석중지</button>
										<button type="button" id="btnSave"  class="mini_button" >DB저장</button>
									</h2>
								</section>

								</form> 
							    <!--폼 끝--> 									
								
							</div>
						</div>
					</div>
				</div>

			<!-- Footer -->
			<jsp:include page="../common/footer.jsp"></jsp:include>

		</div>

	</body>
</html>