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
			$("#btnDelete").on("click", btnDeleteOnclick);
		});
		
	    function goSelectList(){ 
	        $.ajax({  
	            type:"POST",  
	            url:"/analyzer/configuration/listSys.do", 
	            data:encodeURIComponent(JSON.stringify($(document.MAIN_FORM).serializeObject())),  
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
	                        var tbody = $("#out_tbody"); 
	                        tbody.empty(); 
	                        var returnList = data.returnObj.returnObj; 
	                        var lineStr = ""; 
	                        for(var i=0; i<returnList.length; i++){ 
	                            lineStr = ""; 
	                            lineStr = lineStr + "<tr name='trObj' onmouseover='this.style.cursor=\"pointer\"' title='행을 클릭하면 상세내용을 보실 수 있습니다.' >"; 
	                            lineStr = lineStr + "<td name='SYS_ID' style='color: blue; font-weight: bold;' >"+returnList[i].SYS_ID+"</td>"; 
	                            lineStr = lineStr + "<td style='color: blue; font-weight: bold;' >"+returnList[i].SYS_NM+"</td>"; ; 
	                            lineStr = lineStr + "<td>"+returnList[i].CONF_FILE_PATH+"</td>";  
	                            lineStr = lineStr + "<td>"+returnList[i].SAVE_FILE_NAME+"</td>";  
	                            lineStr = lineStr + "<td>"+returnList[i].IS_SAVE_TO_DB+"</td>"; 
	                            lineStr = lineStr + "</tr>"; 
	                            tbody.append(lineStr); 
	                        } 
	                        document.getElementById("paging").innerHTML = data.pageHTML; 
	                        $("tr[name=trObj]").on("click", trObjOnClick);
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
	
	    function goPage(PAGE_NUM){ 
	        document.MAIN_FORM.PAGE_NUM.value = PAGE_NUM; 
	        goSelectList(); 
	    } 
			
		function trObjOnClick(e){
			var trObj = $(e.target);
			var SYS_ID = trObj.parent().find("[name=SYS_ID]").text();
			goSelectDetail(SYS_ID);
		}
	
	    function goSelectDetail(SYS_ID){ 
	        $.ajax({ 
	            type:"POST", 
	            url:"/analyzer/configuration/getSys.do", 
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
	                		var formObj = document.FORM_APP_DETAIL; 
							formObj.SYS_ID.value =  returnObj.SYS_ID; 
							formObj.SYS_NM.value =  returnObj.SYS_NM; 
							formObj.CONF_FILE_PATH.value =  returnObj.CONF_FILE_PATH; 
							formObj.APP_ROOT_PATH.value =  returnObj.APP_ROOT_PATH; 
							formObj.APP_SRC_PATH.value =  returnObj.APP_SRC_PATH; 
							formObj.APP_WEB_PATH.value =  returnObj.APP_WEB_PATH; 
							formObj.APP_SQL_PATH.value =  returnObj.APP_SQL_PATH; 
							formObj.WRITE_PATH.value =  returnObj.WRITE_PATH; 
							formObj.SAVE_FILE_NAME.value =  returnObj.SAVE_FILE_NAME; 
							formObj.DBID.value =  returnObj.DBID; 
							formObj.IS_TABLE_LIST_FROM_DB.value =  returnObj.IS_TABLE_LIST_FROM_DB; 
							formObj.TABLE_NAME_LIKE_STR.value =  returnObj.TABLE_NAME_LIKE_STR; 
							formObj.TABLE_LIST_FILE_NAME.value =  returnObj.TABLE_LIST_FILE_NAME; 
							formObj.IS_SAVE_TO_DB.value =  returnObj.IS_SAVE_TO_DB; 
							formObj.APP_JDK_HOME.value =  returnObj.APP_JDK_HOME; 
							formObj.APP_CLASSPATH.value =  returnObj.APP_CLASSPATH; 
							formObj.WORKER_THREAD_KIND.value =  returnObj.WORKER_THREAD_KIND; 
							formObj.WORKER_THREAD_NUM.value =  returnObj.WORKER_THREAD_NUM; 
						}	
	                } 
	            }, 
	            error : function(data, status, e) { 
	                alert(e); 
	            } 
	        }); 
	    } 
	    
	    function isValid(){ 
	    	var isValid = true;
	    	var formObj = document.FORM_APP_DETAIL;
	    	if( isValid && formObj.SYS_ID.value == ''){
	    		alert("어플리케이션 아이디를 입력하세요.");
	    		isValid = false;
	    	}
	    	if( isValid && formObj.SYS_NM.value == ''){
	    		alert("어플리케이션 명을 입력하세요.");
	    		isValid = false;
	    	}
	    	if( isValid && formObj.CONF_FILE_PATH.value == ''){
	    		alert("설정파일경로를 입력하세요.");
	    		isValid = false;
	    	}
	    	if( isValid && formObj.APP_ROOT_PATH.value == ''){
	    		alert("어플리케이션루트를 입력하세요.");
	    		isValid = false;
	    	}
	    	if( isValid && formObj.APP_SRC_PATH.value == ''){
	    		alert("어플리케이션서버소스경로를 입력하세요.");
	    		isValid = false;
	    	}
	    	if( isValid && formObj.APP_WEB_PATH.value == ''){
	    		alert("어플리케이션웹소스경로를 입력하세요.");
	    		isValid = false;
	    	}
	    	if( isValid && formObj.APP_SQL_PATH.value == ''){
	    		alert("어플리케이션쿼리소스경로를 입력하세요.");
	    		isValid = false;
	    	}
	    	if( isValid && formObj.WRITE_PATH.value == ''){
	    		alert("분석결과생성경로를 입력하세요.");
	    		isValid = false;
	    	}
	    	if( isValid && formObj.SAVE_FILE_NAME.value == ''){
	    		alert("분석결과저장파일명을 입력하세요.");
	    		isValid = false;
	    	}
	    	
	    	if( isValid ){
	    		if(formObj.IS_SAVE_TO_DB.value == 'true'){
	    			if(isValid && formObj.DBID.value == ''){
	    				alert("DB아이디를 입력하세요.");
	    				isValid = false;
	    			}
	    		}
	    	}
	    	
	    	if( isValid ){
	    		if(isValid && formObj.IS_TABLE_LIST_FROM_DB.value == 'true'){
	    			if(isValid && formObj.DBID.value == ''){
	    				alert("DB아이디를 입력하세요.");
	    				isValid = false;
	    			}
	    		}
	    	}
	    	
	    	if( isValid ){
	    		if(isValid && formObj.TABLE_NAME_LIKE_STR.value != ''){
	    			if(isValid && formObj.DBID.value == ''){
	    				alert("DB아이디를 입력하세요.");
	    				isValid = false;
	    			}
	    		}
	    	}
	    	
	    	if( isValid ){
	    		if(formObj.IS_TABLE_LIST_FROM_DB.value == 'false'){
	    			if(isValid && formObj.TABLE_LIST_FILE_NAME.value == ''){
	    				alert("테이블정보파일명을 입력하세요.");
	    				isValid = false;
	    			}
	    		}
	    	}
	    	
	    	if( isValid && formObj.APP_JDK_HOME.value == ''){
	    		alert("JDK홈을 입력하세요.");
	    		isValid = false;
	    	}
	    	if( isValid && formObj.APP_CLASSPATH.value == ''){
	    		alert("클래스패스를 입력하세요.");
	    		isValid = false;
	    	}
	    	if( isValid && formObj.WORKER_THREAD_NUM.value == ''){
	    		alert("쓰레드 갯수를 입력하세요.");
	    		isValid = false;
	    	}
	    	
	    	return isValid;
	    }
			
		function btnSaveOnclick(){
			if( isValid() ){
				goInsert();
			}
		}
	
	    function goInsert(){ 
	        $.ajax({       
	            type:"POST", 
	            url:"/analyzer/configuration/insertSys.do", 
	            data:encodeURIComponent(JSON.stringify($(document.FORM_APP_DETAIL).serializeObject())), 
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
	    
		function btnDeleteOnclick(){
			var formObj = document.FORM_APP_DETAIL; 
			if( formObj.SYS_ID.value != "" ){
				if(confirm("삭제하시겠습니까?")){
					goDelete(formObj.SYS_ID.value);
				}
			}
		}
		
		function goDelete(SYS_ID){
	        $.ajax({ 
	            type:"POST", 
	            url:"/analyzer/configuration/deleteSys.do", 
	            data:'{"SYS_ID":"'+SYS_ID+'"}', 
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

	<body  onLoad="javascript:goSelectList();" >
		<div id="page-wrapper">

			<!-- Header -->
			<jsp:include page="../common/header.jsp"></jsp:include>
			
			<!-- Main -->
				<div id="main">
					<div class="container">
						<div class="row main-row">
							<div class="col-12">

								<section>
									<h2 align="center" style="font-weight: bold;">Global Setting</h2>
									<p>
									Application분석 프로그램을 수행하기 위한 기본적인 환경 설정값을 결정한다.
									<br>
									Application목록에서 관리하고자 하는 Application을 선택하고 원하는 값을 설정함으로써 환경이 상호 다를수 있는 여러  Application들을 독립적으로 관리한다.
									</p>
								</section>

								<section>
									<h2>Application List</h2>
									
									<!--폼 시작--> 
									<form name="MAIN_FORM" method="post" action="" >
									<input type="hidden" name="PAGE_NUM" value="1">
	
									<table width="100%" border="1">
										<thead>
											<tr>
												<th width="15%">어플리케이션 아이디</th>
												<th width="15%">어플리케이션명</th>
												<th width="50%">설정파일경로</th>
												<th width="12%">분석결과파일명</th>
												<th width="8%">DB저장여부</th>
											</tr>
										</thead>
										<tbody id="out_tbody">
										
										</tbody>
										<tfoot>
											<td colspan=5>
												<div id="paging"></div>
											</td>
										</tfoot>
									</table>
	
									</form> 
							        <!--폼 끝--> 									
									
								</section>

								<section>
									<h2>Application Detail</h2>
									
									<!--폼 시작--> 
									<form name="FORM_APP_DETAIL" id="FORM_APP_DETAIL" method="post" >
										
									<table width="100%" border="1">
										<thead>
											<tr>
												<th width="20%">항목명</th>
												<th width="25%">항목값</th>
												<th width="55%">설명</th>
											</tr>
										</thead>
										<tbody>
											<tr>
												<td>어플리케이션 아이디</td>
												<td><input type="text" name="SYS_ID" size="30" value="" /></td>
												<td>어플리케이션 을 특정하는 UNIQUE 아이디</td>
											</tr>
											<tr>
												<td>어플리케이션 명</td>
												<td><input type="text" name="SYS_NM" size="30" value="" /></td>
												<td>어플리케이션 이름</td>
											</tr>
											<tr>
												<td>설정파일경로</td>
												<td><input type="text" name="CONF_FILE_PATH" size="30" value="" /></td>
												<td>어플리케이션 분석(Analyze)관련 설정파일 경로</td>
											</tr>
											<tr>
												<td>어플리케이션루트</td>
												<td><input type="text" name="APP_ROOT_PATH" size="30" value="" /></td>
												<td>분석대상 어플리케이션 루트 경로</td>
											</tr>
											<tr>
												<td>어플리케이션서버소스경로</td>
												<td><input type="text" name="APP_SRC_PATH" size="30" value=""  /></td>
												<td>분석대상 어플리케이션 java소스 루트 경로. 어플리케이션루트이하의 경로만 기술.</td>
											</tr>
											<tr>
												<td>어플리케이션웹소스경로</td>
												<td><input type="text" name="APP_WEB_PATH" size="30" value=""  /></td>
												<td>분석대상 어플리케이션 웹 루트 경로. 어플리케이션루트이하의 경로만 기술.</td>
											</tr>
											<tr>
												<td>어플리케이션쿼리소스루트</td>
												<td><input type="text" name="APP_SQL_PATH" size="30" value=""  /></td>
												<td>분석대상 어플리케이션 쿼리 루트 경로. 전체 경로 기술.</td>
											</tr>
											<tr>
												<td>분석결과생성경로</td>
												<td><input type="text" name="WRITE_PATH" size="30" value=""  /></td>
												<td>분석작업중 중간산출물 저장디렉토리</td>
											</tr>
											<tr>
												<td>분석결과저장파일명</td>
												<td><input type="text" name="SAVE_FILE_NAME" size="30" value=""  /></td>
												<td>분석작업최종결과 파일명. 분석결과생성경로 아래에 생성.</td>
											</tr>
											<tr>
												<td>DB저장여부</td>
												<td>
													<select name="IS_SAVE_TO_DB" >
														<option value="false">false</option>
														<option value="true">true</option>
													</select>
												</td>
												<td>분석작업결과를DB에저장할지여부.</td>
											</tr>
											<tr>
												<td>DB아이디</td>
												<td><input type="text" name="DBID" size="30"  value="" /></td>
												<td>DB저장여부가 true 일 때 사용할 DB아이디</td>
											</tr>
											<tr style="display: none;">
												<td>테이블명DB추출여부</td>
												<td>
													<select name="IS_TABLE_LIST_FROM_DB" >
														<option value="false" selected >false</option>
														<option value="true">true</option>
													</select>
												</td>
												<td>테이블명을 DB로부터 읽어올지 여부</td>
											</tr>
											<tr style="display: none;">
												<td>테이블명 조회프리픽스</td>
												<td><input type="text" name="TABLE_NAME_LIKE_STR" size="30"  value="" /></td>
												<td>테이블명을 DB로부터 읽어올 때 적용할 프리픽스</td>
											</tr>
											<tr>
												<td>테이블명 정보파일명</td>
												<td><input type="text" name="TABLE_LIST_FILE_NAME" size="30"  value="" /></td>
												<td>테이블명을 DB가 아닌 파일로부터 읽어 올때 사용할 테이블명정보 파일명. 분석결과생성경로 아래 생성</td>
											</tr>
											<tr>
												<td>JDK 홈</td>
												<td><input type="text" name="APP_JDK_HOME" size="30"  value=""  /></td>
												<td>분석대상 어플리케이션의 JDK 홈</td>
											</tr>
											<tr>
												<td>분석대상<br>어플리케이션<br>클래스패스</td>
												<td colspan="2">
													<textarea rows="15" cols="100" name="APP_CLASSPATH" ></textarea>
												</td>
											</tr>
											<tr>
												<td>쓰레드 핸들러 종류</td>
												<td>
													<select name="WORKER_THREAD_KIND" >
														<option value="1">Single</option>
														<option value="2">Fixed</option>
														<option value="3">Cached</option>
													</select>
												</td>
												<td>분석작업을 진행 할 쓰레드핸들러 종류(1:싱글 쓰레드풀, 2:Fixed 쓰레드풀, 3:Cached 쓰레드풀)</td>
											</tr>
											<tr>
												<td>쓰레드 갯수</td>
												<td><input type="text" name="WORKER_THREAD_NUM" size="30" value="" /></td>
												<td>분석작업을 진행 할 쓰레드 갯수(쓰레드 핸들러 종류가 Fixed 쓰레드 핸들러 일 경우에만 유효)</td>
											</tr>
										</tbody>
									</table>

									</form> 
							        <!--폼 끝--> 									
									
								
									<h2 align="right">
										<button type="button" id="btnSave"  class="mini_button" >저장</button>
										<button type="button" id="btnDelete"  class="mini_button" >삭제</button>
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