<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%                                                                                                              
/******************************************* 변수 선언 시작 *******************************************/        	  
String                                                         RETURN_CD;                                       
java.util.HashMap                                              returnObj;                                       
java.util.List<net.dstone.sample.member.vo.SampleMemberVo>                  returnVoList;                       
net.dstone.sample.member.vo.SampleMemberVo                                  sampleMemberVo;                
net.dstone.common.utils.PageUtil                                      pageUtil;                                        
/******************************************* 변수 선언 끝 *********************************************/           
                                                                                                                
/******************************************* 변수 정의 시작 *******************************************/           
RETURN_CD           = (String)request.getAttribute("RETURN_CD");                                             
returnObj           = (java.util.HashMap)request.getAttribute("returnObj");                                   
sampleMemberVo            = null;                                                                          
returnVoList        = null;                                                                          
pageUtil            = null;                                                                          
if(returnObj != null){                                                                                          
    returnVoList    = (java.util.List<net.dstone.sample.member.vo.SampleMemberVo>)returnObj.get("returnObj"); 
    pageUtil        = (net.dstone.common.utils.PageUtil)returnObj.get("pageUtil");                                   
}                                                                                                             
/******************************************* 변수 정의 끝 *********************************************/        
%>                                                                                                              
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">      
<html>                                                                                                          
<head>                                                                                                          
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">                                         
<title>Insert title here</title>                                                                                
<script type="text/javascript">                                                                               
	function goForIt(){                                                                                          
		document.MAIN_FORM.submit();                                                                             
	}                                                                                                            
</script>                                                                                                       
</head>                                                                                                         
<body>                                                                                                          
                                                                                                                
<!--폼 시작-->                                                                                                   
<form name="MAIN_FORM" method="post" action="//member/listSampleMember.do">                            
	<input type=hidden name="PAGE_NUM" value="<%= (pageUtil != null ? pageUtil.intPageNum : 1) %>">           
	<input type='button' name='' value='LIST' onclick='javascript:goForIt();' >                                 
	<table border=1>                                                                                              
		<tr>                                                                                                      
			<td>GROUP_ID&nbsp;</td><td>USER_ID&nbsp;</td><td>DEPT_ID&nbsp;</td><td>USER_PW&nbsp;</td><td>NAME&nbsp;</td><td>AGE&nbsp;</td><td>DUTY&nbsp;</td><td>REGION&nbsp;</td><td>ADDRESS&nbsp;</td><td>ADDRESS_DTL&nbsp;</td><td>JUMINNO&nbsp;</td><td>GENDER&nbsp;</td><td>TEL&nbsp;</td><td>HP&nbsp;</td><td>EMAIL&nbsp;</td><td>INPUT_DT&nbsp;</td>
		</tr>                                                                                                     
		<%                                                                                                        
		if(returnVoList!=null){                                                                                   
			for(int i=0; i<returnVoList.size(); i++){                                                             
				sampleMemberVo = returnVoList.get(i);                                                         
		%>                                                                                                        
		<tr>                                                                                                      
			<td><%=sampleMemberVo.getGROUP_ID() %>&nbsp;</td><td><%=sampleMemberVo.getUSER_ID() %>&nbsp;</td><td><%=sampleMemberVo.getDEPT_ID() %>&nbsp;</td><td><%=sampleMemberVo.getUSER_PW() %>&nbsp;</td><td><%=sampleMemberVo.getNAME() %>&nbsp;</td><td><%=sampleMemberVo.getAGE() %>&nbsp;</td><td><%=sampleMemberVo.getDUTY() %>&nbsp;</td><td><%=sampleMemberVo.getREGION() %>&nbsp;</td><td><%=sampleMemberVo.getADDRESS() %>&nbsp;</td><td><%=sampleMemberVo.getADDRESS_DTL() %>&nbsp;</td><td><%=sampleMemberVo.getJUMINNO() %>&nbsp;</td><td><%=sampleMemberVo.getGENDER() %>&nbsp;</td><td><%=sampleMemberVo.getTEL() %>&nbsp;</td><td><%=sampleMemberVo.getHP() %>&nbsp;</td><td><%=sampleMemberVo.getEMAIL() %>&nbsp;</td><td><%=sampleMemberVo.getINPUT_DT() %>&nbsp;</td>
		</tr>	                                                                                                  
		<%                                                                                                        
			}                                                                                                     
		}                                                                                                         
		%>                                                                                                        
		<tr>                                                                                                      
			<td colspan=16 &nbsp; ><%= (pageUtil != null ? pageUtil.htmlPostPage(request, "MAIN_FORM", "PAGE_NUM" ) : "" ) %></td> 
		</tr>	                                                                                                  
	</table>                                                                                                      
                                                                                                                 
</form>                                                                                                          
<!--폼 끝-->                                                                                                      
                                                                                                                 
</body>                                                                                                          
</html>  