<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%                                                                                                              
/******************************************* 변수 선언 시작 *******************************************/        	  
String                                               RETURN_CD;                                       
Boolean                                              returnObj;                                                 
/******************************************* 변수 선언 끝 *********************************************/           
                                                                                                                
/******************************************* 변수 정의 시작 *******************************************/           
RETURN_CD           = (String)request.getAttribute("RETURN_CD");                                             
returnObj           = (Boolean)request.getAttribute("returnObj");                                   
/******************************************* 변수 정의 끝 *********************************************/        
%>                                                                                                              
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">      
<html>                                                                                                          
<head>                                                                                                          
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">                                         
<title>Insert title here</title>                                                                                
<script type="text/javascript">                                                                               
	                                                                                                             
	function init(){                                                                                             
		<%                                                                                                       
		if( RETURN_CD != null ){                                                                                 
			if(RETURN_CD.equals(net.dstone.common.biz.BaseController.RETURN_SUCCESS)){                                                                                   
		%>	                                                                                                     
			alert('SUCCESS');                                                                                    
		<%                                                                                                       
			}else{                                                                                               
		%>	                                                                                                     
			alert('FAIL');                                                                                       
		<%	                                                                                                     
			}                                                                                                    
		}                                                                                                        
		%>                                                                                                       
	}                                                                                                            
	                                                                                                             
	function goForIt(){                                                                                          
		document.MAIN_FORM.submit();                                                                             
	}                                                                                                            
</script>                                                                                                       
</head>                                                                                                         
<body onload='javascript:init();' >                                                                             
                                                                                                                
<!--폼 시작-->                                                                                                    
<form name="MAIN_FORM" method="post" action="/member/updateSampleMember.do">                      		 
	<input type='button' name='' value='UPDATE' onclick='javascript:goForIt();' >                                 
	<table border=1>                                                                                              
		<tr>                                                                                                  
			<td>GROUP_ID&nbsp;</td><td><input type='text' name='GROUP_ID' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>USER_ID&nbsp;</td><td><input type='text' name='USER_ID' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>DEPT_ID&nbsp;</td><td><input type='text' name='DEPT_ID' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>USER_PW&nbsp;</td><td><input type='text' name='USER_PW' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>NAME&nbsp;</td><td><input type='text' name='NAME' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>AGE&nbsp;</td><td><input type='text' name='AGE' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>DUTY&nbsp;</td><td><input type='text' name='DUTY' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>REGION&nbsp;</td><td><input type='text' name='REGION' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>ADDRESS&nbsp;</td><td><input type='text' name='ADDRESS' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>ADDRESS_DTL&nbsp;</td><td><input type='text' name='ADDRESS_DTL' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>JUMINNO&nbsp;</td><td><input type='text' name='JUMINNO' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>GENDER&nbsp;</td><td><input type='text' name='GENDER' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>TEL&nbsp;</td><td><input type='text' name='TEL' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>HP&nbsp;</td><td><input type='text' name='HP' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>EMAIL&nbsp;</td><td><input type='text' name='EMAIL' value=''></td>                
		</tr>                                                                                                 
		<tr>                                                                                                  
			<td>INPUT_DT&nbsp;</td><td><input type='text' name='INPUT_DT' value=''></td>                
		</tr>                                                                                                 
	</table>                                                                                                      
                                                                                                                
</form>                                                                                                         
<!--폼 끝-->                                                                                                    
                                                                                                                
</body>                                                                                                         
</html>   