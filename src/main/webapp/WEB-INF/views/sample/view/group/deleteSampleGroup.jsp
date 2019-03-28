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
<form name="MAIN_FORM" method="post" action="/group/deleteSampleGroup.do">                      		 
	<input type='button' name='' value='DELETE' onclick='javascript:goForIt();' >                                 
	<table border=1>   
		<tr>                                                                                                  
			<td bgcolor=blue >GROUP_ID&nbsp;</td><td><input type='text' name='GROUP_ID' value=''></td>    
		</tr>  
	</table>                                                                                                      
                                                                                                                
</form>                                                                                                         
<!--폼 끝-->                                                                                                    
                                                                                                                
</body>                                                                                                         
</html>                             