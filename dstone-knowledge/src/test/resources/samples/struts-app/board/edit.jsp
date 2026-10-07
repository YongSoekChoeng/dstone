<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<html>
<head><title>게시글 쓰기</title></head>
<body>
<%@ include file="../common/header.jsp" %>
<form method="post" action="/board/save.do">
    제목 <input type="text" name="title">
    <input type="submit" value="저장">
</form>
</body>
</html>
