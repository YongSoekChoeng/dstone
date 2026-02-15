<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
/******************************************* 변수 선언 시작 *******************************************/        	  
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
String                                           	successYn;                                       
java.util.HashMap                                	returnObj;                                       
java.util.List<net.dstone.boot.sample.vo.UserVo>     	returnVoList;                       
net.dstone.boot.sample.vo.UserVo                  		userVo;                
net.dstone.common.utils.PageUtil               		pageUtil;                                       
/******************************************* 변수 선언 끝 *********************************************/           
                                                                                                       
/******************************************* 변수 정의 시작 *******************************************/           
successYn           	= net.dstone.common.utils.StringUtil.nullCheck(response.getHeader("successYn"), "");			
returnObj           	= (java.util.HashMap)requestUtil.getAttribute("returnObj");                                   
userVo            		= null;                                                                          
returnVoList        	= null;                                                                          
pageUtil            	= null;                                                                          
if(returnObj != null){                                                                                          
    returnVoList    	= (java.util.List<net.dstone.boot.sample.vo.UserVo>)returnObj.get("returnObj"); 
    pageUtil        	= (net.dstone.common.utils.PageUtil)returnObj.get("pageUtil");                                   
}                                                                                                             
/******************************************* 변수 정의 끝 *********************************************/
%>  
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
	
		<!-- Header 영역 -->
		<%@ include file="/WEB-INF/views/common/header.jsp" %>
		
		<script type="text/javascript">

			function goForAjax(){ 
				$.ajax({ 
					type:"POST", 
					url:"/sample/user/listUser.do", 
					data:encodeURIComponent(JSON.stringify($(document.AJAX_FORM).serializeObject())), 
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
								console.log('success ===>>> data:' + (JSON.stringify(data)));
								var tbody = $("#AJAX_TBL"); 
								tbody.empty(); 
								var returnList = data.returnObj.returnObj; 
								var lineStr = ""; 
								for(var i=0; i<returnList.length; i++){ 
									lineStr = ""; 
									lineStr = lineStr + "<tr>"; 
									lineStr = lineStr + "<td>"+returnList[i].GROUP_ID+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].USER_ID+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].USER_PW+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].MEMBER_NAME+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].AGE+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].DUTY+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].REGION+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].ADDRESS+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].ADDRESS_DTL+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].JUMINNO+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].GENDER+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].TEL+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].HP+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].EMAIL+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].INPUT_DT+"</td>"; 
									lineStr = lineStr + "<td>"+returnList[i].UPDATE_DT+"</td>"; 
									lineStr = lineStr + "</tr>"; 
									tbody.append(lineStr); 
								} 
								document.getElementById("paging").innerHTML = data.pageHTML; 
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
			} 
		
			function goPageAjax(page){
				$(document.AJAX_FORM.PAGE_NUM).val(page);
				goForAjax();
			}
			
			function goForSubmit(){                                                                                          
				document.SUBMIT_FORM.submit();                                                                             
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
						   
						폼서밋 방식<br>                                                                              
						<!--폼 시작-->                                                                                                   
						<form name="SUBMIT_FORM" method="post" action="/sample/user/listUser.do">                            
							<input type=hidden name="PAGE_NUM" value="<%= (pageUtil != null ? pageUtil.intPageNum : 1) %>">           
							<input type='button' name='' value='LIST' onclick='javascript:goForSubmit();' > 	                     
                                
							<table border=1 class="table-wrapper" >   						                                                                                        
								<thead>                                                                                             
									<tr>                                                                                                      
										<th>GROUP_ID&nbsp;</th><th>USER_ID&nbsp;</th><th>USER_PW&nbsp;</th><th>MEMBER_NAME&nbsp;</th><th>AGE&nbsp;</th><th>DUTY&nbsp;</th><th>REGION&nbsp;</th><th>ADDRESS&nbsp;</th><th>ADDRESS_DTL&nbsp;</th><th>JUMINNO&nbsp;</th><th>GENDER&nbsp;</th><th>TEL&nbsp;</th><th>HP&nbsp;</th><th>EMAIL&nbsp;</th><th>INPUT_DT&nbsp;</th><th>UPDATE_DT&nbsp;</th>
									</tr>   
								</thead> 
								<tbody>
								                                                                                                  
								<%                                                                                                        
								if(returnVoList!=null){                                                                                   
									for(int i=0; i<returnVoList.size(); i++){                                                             
										userVo = returnVoList.get(i);                                                         
								%>                                                                                                        
								<tr>                                                                                                      
									<td><%=userVo.getGROUP_ID() %>&nbsp;</td><td><%=userVo.getUSER_ID() %>&nbsp;</td><td><%=userVo.getUSER_PW() %>&nbsp;</td><td><%=userVo.getMEMBER_NAME() %>&nbsp;</td><td><%=userVo.getAGE() %>&nbsp;</td><td><%=userVo.getDUTY() %>&nbsp;</td><td><%=userVo.getREGION() %>&nbsp;</td><td><%=userVo.getADDRESS() %>&nbsp;</td><td><%=userVo.getADDRESS_DTL() %>&nbsp;</td><td><%=userVo.getJUMINNO() %>&nbsp;</td><td><%=userVo.getGENDER() %>&nbsp;</td><td><%=userVo.getTEL() %>&nbsp;</td><td><%=userVo.getHP() %>&nbsp;</td><td><%=userVo.getEMAIL() %>&nbsp;</td><td><%=userVo.getINPUT_DT() %>&nbsp;</td><td><%=userVo.getUPDATE_DT() %>&nbsp;</td>
								</tr>	                                                                                                  
								<%                                                                                                        
									}                                                                                                     
								}                                                                                                         
								%>                                                                                                        
								<tr>                                                                                                      
									<td colspan=16 &nbsp; ><%= (pageUtil != null ? pageUtil.htmlPostPage(request, "SUBMIT_FORM", "PAGE_NUM" ) : "" ) %></td> 
								</tr>	 
								</tbody>                                                                                              
							</table>                                                                                                   
						                                                                                                             
						</form>                                                                                                          
						<!--폼 끝-->       
						
						<br>
						<br>
						
						AJAX 방식<br>               
						<!--폼 시작-->                                                                                                   
						<form name="AJAX_FORM" method="post" action="">                            
							<input type=hidden name="PAGE_NUM" value="<%= (pageUtil != null ? pageUtil.intPageNum : 1) %>">           
							<input type='button' name='' value='LIST' onclick='javascript:goForAjax();' >  
							 
							<table border=1 class="table-wrapper" > 
							                               
								<thead>                                                                                             
									<tr>                                                                                                      
										<th>GROUP_ID&nbsp;</th><th>USER_ID&nbsp;</th><th>USER_PW&nbsp;</th><th>MEMBER_NAME&nbsp;</th><th>AGE&nbsp;</th><th>DUTY&nbsp;</th><th>REGION&nbsp;</th><th>ADDRESS&nbsp;</th><th>ADDRESS_DTL&nbsp;</th><th>JUMINNO&nbsp;</th><th>GENDER&nbsp;</th><th>TEL&nbsp;</th><th>HP&nbsp;</th><th>EMAIL&nbsp;</th><th>INPUT_DT&nbsp;</th><th>UPDATE_DT&nbsp;</th>
									</tr>   
								</thead>  
								<tbody id="AJAX_TBL">
								</tbody>                                                                                                                    
								<tr>                                                                                                      
									<td colspan=16 &nbsp; ><div id="paging" ></div> </td> 
								</tr>	
							</table>  
	                                                                                                         
						</form>                                                                                                          
						<!--폼 끝-->                                                                                                      
	                                 
						<!-- =============================================== Content 영역 End =============================================== -->
					</section>

				</div>
			
			</div>

			<!-- Menu 영역 -->
			<%@ include file="/WEB-INF/views/common/left.jsp" %>

		</div>

	</body>
</html>
