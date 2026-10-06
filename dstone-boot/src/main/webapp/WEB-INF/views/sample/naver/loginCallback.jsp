<%@page import="net.dstone.common.utils.StringUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%  
	net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
	java.util.Map<String, String> userInfo = (java.util.Map<String, String>)requestUtil.getAttribute("userInfo");
	
	String id = "";
	String email = "";
	String nickname = "";
	
	if( userInfo != null ){
	
		id = StringUtil.nullCheck(userInfo.get("id"), "");
		email = StringUtil.nullCheck(userInfo.get("email"), "");
		nickname = StringUtil.nullCheck(userInfo.get("nickname"), "");
		
	}
%>   
  
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>

<script type="text/javascript" src="/js/jquery-1.7.2.js"></script> 
<script type="text/javascript" src="/js/jquery.json-2.4.js" ></script> 
<script type="text/javascript">
	
</script>

<meta http-equiv="Content-Type" content="text/html; charset=EUC-KR">
<title>Login</title>
</head>
<body>
<div class="container" style="display: flex; justify-content: center; align-content: center; align-items: center; flex-direction: column; margin: 200px auto; ">
    <h1>Naver 로그인</h1>
       로그인 되었습니다.
    <br>
    <br>
    id:<%=id %> email:<%=email %> nickname:<%=nickname %> 
    <br>
    <br>
    <a href="/naver/logout.do" >로그아웃</a>
</div>
</body>
</html>