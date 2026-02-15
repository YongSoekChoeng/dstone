<%@page import="net.dstone.common.utils.StringUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%  
	net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
	String slocation = "";
	slocation = requestUtil.getAttribute("location").toString();
System.out.println( "slocation=====>>>" + slocation ); 
%>   
  
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>

	<meta http-equiv="Expires" content="-1">
	<meta http-equiv="Pragma" content="no-cache">
	<meta http-equiv="Cache-Control" content="no-cache">
	<!--  
	<meta http-equiv="Content-Security-Policy" content="default-src 'self' *.kakao.com">
	<meta http-equiv="Content-Security-Policy" content="*">
	-->
	<meta http-equiv="Access-Control-Allow-Origin" content="*">
	
<script type="text/javascript" src="/js/jquery-1.7.2.js"></script> 
<script type="text/javascript" src="/js/jquery.json-2.4.js" ></script> 
<script type="text/javascript">
	
</script>

<meta http-equiv="Content-Type" content="text/html; charset=EUC-KR">
<title>Login</title>
</head>
<body>
<div class="container" style="display: flex; justify-content: center; align-content: center; align-items: center; flex-direction: column; margin: 200px auto; ">
    <h1>카카오 로그인</h1>
    <a href="<%=slocation%>">
        <img src="/images/kakao_login_medium_narrow.png" >
    </a>
</div>
</body>
</html>