<%@page import="net.dstone.common.utils.SystemUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%    

/******************************************* 변수 선언 시작 *******************************************/              
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);

/******************************************* 변수 선언 끝 *********************************************/           
    

%> 
<!DOCTYPE HTML>

<html>

	<!-- Head -->
	<jsp:include page="../common/head.jsp"></jsp:include> 
	
	<!-- Script -->
	<script type="text/javascript"> 
	
		$(document).ready(function(){
			$("#sTitle").text('Call Chain For Seq[<%=requestUtil.getParameter("SEQ")%>]');
			goSelectDetail();
			$("#btnClose").on("click", closeThis);
		});

		function closeThis(){
			self.close();
		}
		
	    function goSelectDetail(){ 
	        $.ajax({  
	            type:"POST",  
	            url:"/analyzer/report/selectOverAll.do", 
	            data:encodeURIComponent(JSON.stringify($(document.FORM_APP_DETAIL).serializeObject())),  
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
	                        var maxLevel = data.maxLevel; 
	                        var detailVo = data.detailVo;
	                        var tblList = data.tblList;
	                        var html = "";
	                        
	                        // 1. 화면
	                        html = "";
	                        html = html + "<tr>";
	                        html = html + "	<td>"+detailVo["UI_ID"]+"</td>";
	                        html = html + "	<td>"+detailVo["UI_NM"]+"</td>";
	                        html = html + "	<td>"+detailVo["BASIC_URL"]+"</td>";
	                        html = html + "</tr>";
	                        $('#tbUi').html(html);

	                        // 2. API
	                        html = "";
	                        for(var i=0; i<data.maxLevel; i++){
		                        html = html + "<tr>";
		                        html = html + "	<td style='text-align:right;'>"+(i+1)+"</td>";
		                        var displayId = detailVo["DISPLAY_ID_"+(i+1)];
		                        if(displayId != ""){
		                        	var classId = "";
		                        	var methodId = "";
		                        	if(displayId.indexOf(".") > -1){
		                        		var dpIdArr = displayId.split(".");
		                        		classId = dpIdArr[0];
		                        		methodId = dpIdArr[1];
		                        		
		                        		classId = "<font color='green'>" + classId +  "</font>";
		                        		methodId = "<font color='blue'>" + methodId  + "</font>";
		                        		displayId =  "<section style='font-weight:bold;'>" + classId + "<br>&nbsp;" + "." + methodId + "</section>";
		                        	}
		                        }
		                        html = html + "	<td>"+displayId+"</td>";
		                        html = html + "	<td><textarea style='width:100%' row=2 readonly >"+detailVo["FUNCTION_ID_"+(i+1)]+"</textarea></td>";
		                        html = html + "	<td>"+detailVo["FUNCTION_NAME_"+(i+1)]+"</td>";
		                        html = html + "	<td style='text-align:center;'>"+detailVo["CLASS_KIND_"+(i+1)]+"</td>";
		                        html = html + "</tr>";
	                        }
	                        $('#tbApi').html(html);

	                        // 3. 테이블
	                        html = "";
	                        for(var i=0; i<tblList.length; i++){
		                        html = html + "<tr>";
		                        html = html + "	<td>"+tblList[i]["TBL_ID"]+"</td>";
		                        html = html + "	<td>"+tblList[i]["TBL_NM"]+"</td>";
		                        if( tblList[i]["TBL_CRUD"] == "I" ){
		                        	html = html + "	<td style='text-align:center;'>입력("+tblList[i]["TBL_CRUD"]+")</td>";
		                        }else if( tblList[i]["TBL_CRUD"] == "S" ){
		                        	html = html + "	<td style='text-align:center;'>조회("+tblList[i]["TBL_CRUD"]+")</td>";
		                        }else if( tblList[i]["TBL_CRUD"] == "U" ){
		                        	html = html + "	<td style='text-align:center;'>수정("+tblList[i]["TBL_CRUD"]+")</td>";
		                        }else if( tblList[i]["TBL_CRUD"] == "D" ){
		                        	html = html + "	<td style='text-align:center;'>삭제("+tblList[i]["TBL_CRUD"]+")</td>";
		                        }else{
		                        	html = html + "	<td>"+tblList[i]["TBL_CRUD"]+"</td>";
		                        }
		                        
		                        html = html + "</tr>";
	                        }
	                        $('#tbTbl').html(html);
	                        
	                    } 
	                }else{ 
	                    console.log('failure ===>>> data:' + (JSON.stringify(data))); 
	                    alert("failure errMsg:" + errMsg); 
	                }
	            }, 
				error : function(data, status, e) { 
					loadingIndicator.fadeOut();
					alert(e); 
				} 
			});
		} 
	
	</script> 

	<body >
	

			<!-- Main -->
			<div id="main">
				<div class="container">
					<div class="row main-row">
					
						<div class="col-12">
							<section>
								<h2 id="sTitle" ></h2>
								
								<!--폼 시작--> 
								<form name="FORM_APP_DETAIL" id="FORM_APP_DETAIL" method="post" >
									<input type="hidden" name="SYS_ID" value="<%=requestUtil.getParameter("SYS_ID")%>" /> 
									<input type="hidden" name="SEQ" value="<%=requestUtil.getParameter("SEQ")%>" /> 
								</form> 
						        <!--폼 끝--> 									
								
								<h3>Step1. 화면</h3>
								<table width="100%" border="1">
									<thead>
										<tr>
											<th width="30%">ID</th>
											<th width="30%">명</th>
											<th width="40%">호출URL</th>
										</tr>
									</thead>
									<tbody id="tbUi" >
									</tbody>
								</table>
							</section>
							
							<section>
								<h3>Step2. API</h3>
								<table width="100%" border="1">
									<thead>
										<tr>
											<th width="5%">호출<br>순서</th>
											<th width="20%">ID</th>
											<th width="50%">FUNCTION ID</th>
											<th width="20%">명</th>
											<th width="5%">종류</th>
										</tr>
									</thead>
									<tbody id="tbApi" >
									</tbody>
								</table>
							</section>
			
							<section>
								<h3>Step3. 테이블</h3>
								<table width="100%" border="1">
									<thead>
										<tr>
											<th width="40%">ID</th>
											<th width="50%">명</th>
											<th width="10%">CRUD</th>
										</tr>
									</thead>
									<tbody id="tbTbl" >
									</tbody>
								</table>
			
							</section>
			
							<section>
								<h2 align="right">
									<button type="button" id="btnClose"  class="mini_button" >닫기</button>
								</h2>
							</section>
			
						</div>
					</div>
				</div>
			</div>


	</body>
</html>