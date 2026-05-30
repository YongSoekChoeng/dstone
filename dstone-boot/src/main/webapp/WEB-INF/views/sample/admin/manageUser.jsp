<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
/******************************************* 변수 선언 시작 *******************************************/              
net.dstone.common.utils.RequestUtil                 requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
String                                                successYn;                                       
java.util.HashMap                                     returnObj;                                       
java.util.List<net.dstone.boot.sample.vo.UserVo>           returnVoList;                        
net.dstone.boot.sample.vo.UserVo                           userVo;                
net.dstone.common.utils.PageUtil                     pageUtil;                                        
/******************************************* 변수 선언 끝 *********************************************/           
                                                                                                                
/******************************************* 변수 정의 시작 *******************************************/           
successYn                                           = net.dstone.common.utils.StringUtil.nullCheck(response.getHeader("successYn"), "");            
returnObj                                           = (java.util.HashMap)requestUtil.getAttribute("returnObj");                                   
userVo                                                = null;                                                                          
returnVoList                                        = null;                                                                          
pageUtil                                            = null;                                                                          
if(returnObj != null){ 
    if( returnObj.get("returnObj") instanceof java.util.List ){
        returnVoList                                = (java.util.List<net.dstone.boot.sample.vo.UserVo>)returnObj.get("returnObj"); 
        pageUtil                                    = (net.dstone.common.utils.PageUtil)returnObj.get("pageUtil");           
    }
}        
/******************************************* 변수 정의 끝 *********************************************/
%>                                                                                                              
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
	
		<!-- Header 영역 -->
		<%@ include file="/WEB-INF/views/common/header.jsp" %>
				                                                                             
		<script type="text/javascript">                                                                               
		    
		    function init(){                                                                                             
		                                                                                                    
		    } 
		    
		    function goForSubmitSelect(){                                                                                          
		        document.SUBMIT_SELECT_FORM.submit();                                                                             
		    }   
		       
		    function goForAjaxSelect(){ 
		        $.ajax({ 
		            type:"POST", 
		            url:"/sample/admin/listUser.do", 
		            data:encodeURIComponent(JSON.stringify($(document.AJAX_SELECT_FORM).serializeObject())), 
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
		                            lineStr = lineStr + "<tr id='TR_AJAX_"+ i +"' onclick='javascript:setDetail(\"AJAX\", "+i+")' >"; 
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
		        $(document.AJAX_SELECT_FORM.PAGE_NUM).val(page);
		        goForAjaxSelect();
		    }
		    
		    function setDetail(gubun, idx){
		        var formObj = $("#MANAGE_FORM");
		        var inputList = formObj.find(':input');
		        var trObj = $("#TR_"+gubun+"_" + idx);
		        var tdList = trObj.children();
		        for(var i = 0; i<tdList.length; i++){
		            var td = $(tdList[i]);
		            var input = $(inputList[i]);
		            if(td.text() == 'null'){
		                input.val("");
		            }else{
		                input.val(td.text().replace(/-/gi, ""));
		            }
		        }
		    }
		    
		    function goForInsert(){
		        document.MANAGE_FORM.action = "/sample/admin/insertUser.do";
		        document.MANAGE_FORM.submit();                                                                             
		    }
		
		    function goForUpdate(){
		        document.MANAGE_FORM.action = "/sample/admin/updateUser.do";                                                                                  
		        document.MANAGE_FORM.submit();                                                                             
		    }
		
		    function goForDelete(){
		        document.MANAGE_FORM.action = "/sample/admin/deleteUser.do";                                                                                  
		        document.MANAGE_FORM.submit();                                                                             
		    }
		    
		</script>                                                                                                       
		</head>                                                                                                         
		<body onload='javascript:init();' >                                                                             
		
		<!-- Wrapper -->
		<div id="wrapper">

			<!-- Main -->
			<div id="main">
			
				<div class="inner">

					<!-- Top 영역 -->
					<%@ include file="/WEB-INF/views/common/top.jsp" %>
					
					<section>
						<!-- =============================================== Content 영역 Start =============================================== -->
						   
						 
						    폼서밋 방식 조회<br>                                                                                          
						    <!--폼 시작-->
						    <form name="SUBMIT_SELECT_FORM" method="post" action="/sample/admin/listUser.do" >                                                                                                                               
						        <input type=hidden name="PAGE_NUM" value="<%= (pageUtil != null ? pageUtil.intPageNum : 1) %>">           
						        <input type='button' name='' value='LIST' onclick='javascript:goForSubmitSelect();' >                                 
						        <table border=1>                                                                                              
						            <thead>                                                                                             
						                <tr>                                                                                                      
						                    <th>GROUP_ID</th><th>USER_ID</th><th>USER_PW</th><th>MEMBER_NAME</th><th>AGE</th><th>DUTY</th><th>REGION</th><th>ADDRESS</th><th>ADDRESS_DTL</th><th>JUMINNO</th><th>GENDER</th><th>TEL</th><th>HP</th><th>EMAIL</th><th>INPUT_DT</th><th>UPDATE_DT</th>
						                </tr>   
						            </thead>  
						            <tbody>                                                                                                 
						            <%                                                                                                        
						            if(returnVoList!=null){                                                                                   
						                for(int i=0; i<returnVoList.size(); i++){                                                             
						                    userVo = returnVoList.get(i);                                                         
						            %>                                                                                                        
						            <tr id="TR_FORM_<%= i %>"  onclick="javascript:setDetail('FORM', <%= i %>)"  >                                                                                                      
						                <td><%=userVo.getGROUP_ID() %></td><td><%=userVo.getUSER_ID() %></td><td><%=userVo.getUSER_PW() %></td><td><%=userVo.getMEMBER_NAME() %></td><td><%=userVo.getAGE() %></td><td><%=userVo.getDUTY() %></td><td><%=userVo.getREGION() %></td><td><%=userVo.getADDRESS() %></td><td><%=userVo.getADDRESS_DTL() %></td><td><%=userVo.getJUMINNO() %></td><td><%=userVo.getGENDER() %></td><td><%=userVo.getTEL() %></td><td><%=userVo.getHP() %></td><td><%=userVo.getEMAIL() %></td><td><%=userVo.getINPUT_DT() %></td><td><%=userVo.getUPDATE_DT() %></td>
						            </tr>                                                                                                      
						            <%                                                                                                        
						                }                                                                                                     
						            }                                                                                                         
						            %>                                                                                                        
						            <tr>                                                                                                      
						                <td colspan=16  ><%= (pageUtil != null ? pageUtil.htmlPostPage(request, "SUBMIT_SELECT_FORM", "PAGE_NUM" ) : "" ) %></td> 
						            </tr> 
						            </tbody>                                                                                                  
						        </table>                                                                                                      
						                                                                                                                     
						    </form>                                                                                                          
						    <!--폼 끝-->   
						    <br><br>                                                                                                   
						                    
						    AJAX 방식 조회<br>                                                                                          
						    <!--폼 시작-->                                                                                                   
						    <form name="AJAX_SELECT_FORM" method="post" action="">                            
						        <input type=hidden name="PAGE_NUM" value="<%= (pageUtil != null ? pageUtil.intPageNum : 1) %>">           
						        <input type='button' name='' value='LIST' onclick='javascript:goForAjaxSelect();' >                                 
						        <table border=1>      
						            <thead>                                                                                             
						                <tr>                                                                                                      
						                    <th>GROUP_ID</th><th>USER_ID</th><th>USER_PW</th><th>MEMBER_NAME</th><th>AGE</th><th>DUTY</th><th>REGION</th><th>ADDRESS</th><th>ADDRESS_DTL</th><th>JUMINNO</th><th>GENDER</th><th>TEL</th><th>HP</th><th>EMAIL</th><th>INPUT_DT</th><th>UPDATE_DT</th>
						                </tr>   
						            </thead>  
						            <tbody id="AJAX_TBL">
						            </tbody>                                                                                                     
						            <tr>                                                                                                      
						                <td colspan=16  ><div id="paging" ></div> </td> 
						            </tr>     
						        </table>                                                                                                      
						    </form>                                                                                                          
						    <!--폼 끝-->  
						    <br><br>                                                                                                   
						                     
						    폼서밋 방식 입력<br>                                                                                               
						    <!--폼 시작-->                                                                                                    
						    <form name="MANAGE_FORM" id="MANAGE_FORM"  method="post" action="">                                          
						        <table id="TB_INSERT" border=1>                                                                                              
						            <tr>                                                                                                  
						                <td>GROUP_ID</td><td><input type='text' name='GROUP_ID' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>USER_ID</td><td><input type='text' name='USER_ID' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>USER_PW</td><td><input type='text' name='USER_PW' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>MEMBER_NAME</td><td><input type='text' name='MEMBER_NAME' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>AGE</td><td><input type='text' name='AGE' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>DUTY</td><td><input type='text' name='DUTY' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>REGION</td><td><input type='text' name='REGION' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>ADDRESS</td><td><input type='text' name='ADDRESS' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>ADDRESS_DTL</td><td><input type='text' name='ADDRESS_DTL' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>JUMINNO</td><td><input type='text' name='JUMINNO' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>GENDER</td><td><input type='text' name='GENDER' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>TEL</td><td><input type='text' name='TEL' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>HP</td><td><input type='text' name='HP' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>EMAIL</td><td><input type='text' name='EMAIL' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>INPUT_DT</td><td><input type='text' name='INPUT_DT' value=''></td>                
						            </tr>                                                                                                 
						            <tr>                                                                                                  
						                <td>UPDATE_DT</td><td><input type='text' name='UPDATE_DT' value=''></td>                
						            </tr>                                                                                                 
						        </table>    
						        <br>                                                                                                  
						        <input type='button' name='' value='INSERT' onclick='javascript:goForInsert();' >                     
						        <input type='button' name='' value='UPDATE' onclick='javascript:goForUpdate();' >                     
						        <input type='button' name='' value='DELETE' onclick='javascript:goForDelete();' >                                                                                                   
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
