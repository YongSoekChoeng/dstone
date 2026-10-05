<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
<head><title>게시글 목록</title></head>
<body>
<jsp:include page="/common/header.jsp"/>
<p>전체 ${total}건</p>
<table>
<c:forEach items="${boards}" var="b">
    <tr><td>${b.boardId}</td><td>${b.title}</td></tr>
</c:forEach>
</table>
<a href="/board/edit.do">글쓰기</a>
<form method="post" action="/board/admin.do?cmd=delete">
    <input type="text" name="boardId"><input type="submit" value="삭제">
</form>
</body>
</html>
