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
	
		$(document).ready(function(){
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
	                        appSel.on("change", appSelOnChange);
	                        
	                        if( returnList.length > 0 ){
	                        	goSelectDetail(returnList[0].SYS_ID);
	                        }
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
			
		function appSelOnChange(e){
			var appSel = $(e.target);
			var SYS_ID = appSel.val();
			goSelectDetail(SYS_ID);
		}
	
	    function goSelectDetail(SYS_ID){
	        $.ajax({ 
	            type:"POST", 
	            url:"/analyzer/configuration/getAppOption.do", 
	            data:'{"SYS_ID":"'+SYS_ID+'"}', 
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
	                		var returnObj = data.returnObj;
	                		var formObj = document.FORM_APP_SETTING; 
	                		var tBodyObj;
	                		
	                		// 분석패키지루트
	                		tBodyObj = $("#tbodyIncPkg");
	                		tBodyObj.empty();
	                		if(returnObj.INCLUDE_PACKAGE_ROOT.length){
		                		for(var i=0; i<returnObj.INCLUDE_PACKAGE_ROOT.length; i++){ 
		                			doIncPkgAdd(returnObj.INCLUDE_PACKAGE_ROOT[i]);
		                		}
	                		}

	                		// 분석제외패키지패턴
	                		tBodyObj = $("#tbodyExcPkg"); 
	                		tBodyObj.empty();
	                		if(returnObj.EXCLUDE_PACKAGE_PATTERN.length){
		                		for(var i=0; i<returnObj.EXCLUDE_PACKAGE_PATTERN.length; i++){ 
		                			doExcPkgAdd(returnObj.EXCLUDE_PACKAGE_PATTERN[i]);
		                		}
	                		}
	                		
						}	
	                } 
	            }, 
	            error : function(data, status, e) { 
	                alert(e); 
	            } 
	        }); 
	    } 
	    

	    function doIncPkgAdd(val){
	    	var tBodyObj = $("#tbodyIncPkg");
	    	
	    	var html = "";
	    	html = html + "<tr>";
	    	html = html + "	<td>";
	    	if(val){
	    		html = html + "		<input type='text' name='INCLUDE_PACKAGE_ROOT' size='110' value='"+val+"' />";
	    	}else{
	    		html = html + "		<input type='text' name='INCLUDE_PACKAGE_ROOT' size='110' value='' />";
	    	}
	    	html = html + "	</td>";
	    	html = html + "	<td align='center'>";
	    	html = html + "		<button type='button' id='btnIncPkgAdd' onclick='doIncPkgAdd()' >추가</button>";
	    	html = html + "		<button type='button' id='btnIncPkgDel' onclick='doIncPkgDel(this)' >삭제</button>";
	    	html = html + "	</td>";
	    	html = html + "</tr>";	
	    	tBodyObj.append(html);
	    	
	    	var trArray = tBodyObj.find("tr");
	    	if(trArray && trArray.length > 0){
	    		for(var i=0; i<trArray.length ; i++){
	    			var trObj = $(trArray[i]);
	    			trObj.attr("id", "trIncPkg_"+i);
	    		}
	    	}
	    } 

	    function doIncPkgDel(thisObj){
	    	var tBodyObj = $("#tbodyIncPkg");
	    	var trBodyObjRowCnt = $("#tbodyIncPkg tr").length;
	    	if( trBodyObjRowCnt == 1 ){
	    		alert("적어도 하나 이상의 항목은 필수 입니다.");
	    	}else{
	    		var trObj = $(thisObj).parent().parent();
	    		trObj.remove();
		    	var trArray = tBodyObj.find("tr");
		    	if(trArray && trArray.length > 0){
		    		for(var i=0; i<trArray.length ; i++){
		    			var trObj = $(trArray[i]);
		    			trObj.attr("id", "trIncPkg_"+i);
		    		}
		    	}
	    	}
	    } 

	    function doExcPkgAdd(val){
	    	var tBodyObj = $("#tbodyExcPkg");
	    	
	    	var html = "";
	    	html = html + "<tr>";
	    	html = html + "	<td>";
	    	if(val){
	    		html = html + "		<input type='text' name='EXCLUDE_PACKAGE_PATTERN' size='110' value='"+val+"' />";
	    	}else{
	    		html = html + "		<input type='text' name='EXCLUDE_PACKAGE_PATTERN' size='110' value='' />";
	    	}	    	
	    	html = html + "	</td>";
	    	html = html + "	<td align='center'>";
	    	html = html + "		<button type='button' id='btnExcPkgAdd' onclick='doExcPkgAdd()' >추가</button>";
	    	html = html + "		<button type='button' id='btnExcPkgDel' onclick='doExcPkgDel(this)' >삭제</button>";
	    	html = html + "	</td>";
	    	html = html + "</tr>";	
	    	tBodyObj.append(html);
	    	
	    	var trArray = tBodyObj.find("tr");
	    	if(trArray && trArray.length > 0){
	    		for(var i=0; i<trArray.length ; i++){
	    			var trObj = $(trArray[i]);
	    			trObj.attr("id", "trExcPkg_"+i);
	    		}
	    	}
	    } 

	    function doExcPkgDel(thisObj){
	    	var tBodyObj = $("#tbodyExcPkg");
	    	var trBodyObjRowCnt = $("#tbodyExcPkg tr").length;
	    	if( trBodyObjRowCnt == 1 ){
	    		alert("적어도 하나 이상의 항목은 필수 입니다.");
	    	}else{
	    		var trObj = $(thisObj).parent().parent();
	    		trObj.remove();
		    	var trArray = tBodyObj.find("tr");
		    	if(trArray && trArray.length > 0){
		    		for(var i=0; i<trArray.length ; i++){
		    			var trObj = $(trArray[i]);
		    			trObj.attr("id", "trExcPkg_"+i);
		    		}
		    	}
	    	}
	    } 
		
		function btnSaveOnclick(){
			saveAppOption();
		}
	
	    function saveAppOption(){ 
	        $.ajax({       
	            type:"POST", 
	            url:"/analyzer/configuration/saveAppOption.do", 
	            data:encodeURIComponent(JSON.stringify($(document.FORM_APP_SETTING).serializeObject())), 
	            dataType:"json", 
	            success:function(data, status, request){ 
	                var successYn = request.getResponseHeader('successYn'); 
	                var errCd = request.getResponseHeader('errCd'); 
	                var errMsg = decodeURIComponent(request.getResponseHeader('errMsg')); 
	                if( 'Y' == successYn ){ 
	                	alert('success'); 
	                	var forcedToUrl = request.getResponseHeader('forcedToUrl'); 
	                	if(forcedToUrl && "" != forcedToUrl){ 
	                		location.href = "/defaultLink.do?defaultLink=" + forcedToUrl; 
	                	} 
	                }else{ 
	                    console.log('failure ===>>> data:' + (JSON.stringify(data))); 
	                    alert("failure errMsg:" + errMsg); 
	                } 
	                goPage(1);
	            }, 
	            error : function(data, status, e) { 
	                console.log('system error ===>>> data:' + (JSON.stringify(data))); 
	                alert("system error"); 
	            } 
	        }); 
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
									<h2 align="center" style="font-weight: bold;">Option Setting</h2>
									<p>
									Application분석과정에 필요한 옵션값을을 설정한다.
									</p>
								</section>

								<section>
									<h2>Application Analysis Option Setting</h2>
									
									<!--폼 시작--> 
									<form name="FORM_APP_SETTING" id="FORM_APP_SETTING" method="post" >
									
									<ul class="small-image-list">
										<li>
											<h4>1. Application 선택</h4>
											<p>
												<select name="SYS_ID" id="SYS_ID" >
												</select>
											</p>
										</li>
									</ul>
									
									<ul class="small-image-list">
										<li>
											<h4>2. 분석패키지</h4>
											<p>
												분석대상 패키지 루트. 해당 패키지이하의 모듈만 분석한다.
												<br>
												예) net.dstone.sample.analyze
											</p>
										</li>
									</ul>
									
									<table width="100%" border="1" >
										<thead>
											<tr>
												<th width="80%">분석대상 패키지</th>
												<th width="20%"></th>
											</tr>														
										</thead>
										<tbody border="1" id="tbodyIncPkg" >
											<tr>
												<td>
													<input type="text" name="INCLUDE_PACKAGE_ROOT" size="110" value="" />
												</td>
												<td align="center">
													<button type="button" id="btnIncPkgAdd" onclick="doIncPkgAdd()" >추가</button>
													<button type="button" id="btnIncPkgDel" onclick="doIncPkgDel(this)" >삭제</button>
												</td>
											</tr>														
										</tbody>
									</table>

									<h2></h2>
									
									<ul class="small-image-list">
										<li>
											<h4>3. 분석제외 패키지 패턴</h4>
											<p>
												분석제외대상 패키지 패턴. 해당 패키지 패턴이 들어가는 패키지는 분석제외한다.
												<br>
												<font color="blue">분석제외 패키지 패턴과 분석대상 패키지가 겹칠 경우 분석제외 패키지 패턴이 우선한다.</font>
												<br>
												예) .vo., .model. (이 경우 vo, model 이 들어가는 패키지는 분석대상에서 제외한다.)
											</p>
										</li>
									</ul>
									
									<table width="100%" border="1" >
										<thead>
											<tr>
												<th width="80%">분석제외 패키지 패턴</th>
												<th width="20%"></th>
											</tr>														
										</thead>
										<tbody border="1" id="tbodyExcPkg" >
											<tr>
												<td>
													<input type="text" name="EXCLUDE_PACKAGE_PATTERN" size="110" value="" />
												</td>
												<td align="center">
													<button type="button" id="btnPkgAdd" onclick="doExcPkgAdd()" >추가</button>
													<button type="button" id="btnPkgDel" onclick="doExcPkgDel(this)" >삭제</button>
												</td>
											</tr>														
										</tbody>
									</table>

									</form> 
							        <!--폼 끝--> 									
								
									<h2 align="right">
										<button type="button" id="btnSave"  class="mini_button" >저장</button>
									</h2>
								</section>

							</div>
						</div>
					</div>
				</div>

			<!-- Footer -->
			<jsp:include page="../common/footer.jsp"></jsp:include>

		</div>

	</body>
</html>