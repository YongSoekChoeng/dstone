<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%-- 왼쪽 메뉴. 모양은 AI 화면과 같은 파일을 쓰고, 메뉴는 AI 것만 보인다 --%>
<jsp:include page="/WEB-INF/views/console/sidebar.jsp">
	<jsp:param name="section" value="ai" />
</jsp:include>
