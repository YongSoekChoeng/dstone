<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%--
	AI 화면과 코드 분석(Knowledge) 화면의 왼쪽 메뉴입니다. 두 화면은 서로 다른 서버(dstone-ai-engine, dstone-knowledge)를 쓰는
	따로 된 화면이라 메뉴도 따로 보입니다. 모양만 같이 쓰려고 파일을 하나로 두었습니다.
	ai/common/header.jsp 는 section=ai, knowledge/common/header.jsp 는 section=knowledge 로 이 파일을 include 합니다.
	지금 화면은 주소의 defaultLink 값으로 알아내 강조합니다.
--%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
String currentLink = requestUtil.getParameter("defaultLink", "");
String contextPath = requestUtil.getStrContextPath();
String linkBase = contextPath + "/defaultLink.do?defaultLink=";
boolean knowledge = "knowledge".equals(request.getParameter("section"));

// 메뉴 한 줄: {화면(defaultLink), 이름, 아이콘 이름}
String[][] aiMenus = {
	{"ai/index", "개요", "home"},
	{"ai/chat/chat", "채팅", "chat"},
	{"ai/workflow/workflow", "Workflow 테스트", "play"},
	{"ai/admin/workflow/workflow", "Workflow 실행 관리", "list"},
	{"ai/designer/designer", "Workflow 디자이너", "flow"}
};
String[][] knowledgeMenus = {
	{"knowledge/index", "프로젝트 · 분석", "folder"},
	{"knowledge/search/search", "검색 · 문서", "search"},
	{"knowledge/impact/impact", "영향도 · 호출 관계", "impact"},
	{"knowledge/graph/graph", "노드 맵", "graph"},
	{"knowledge/diff/diff", "리비전 비교", "diff"},
	{"knowledge/guide/guide", "사용 안내", "book"}
};

// 아이콘(선으로 그린 24x24 SVG 경로). 외부 아이콘 글꼴을 쓰지 않아 폐쇄망에서도 그대로 보인다.
java.util.Map<String, String> icons = new java.util.HashMap<String, String>();
icons.put("home", "<path d='M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'/>");
icons.put("chat", "<path d='M21 12a8 8 0 0 1-11.6 7.1L4 21l1.9-5.4A8 8 0 1 1 21 12z'/>");
icons.put("play", "<circle cx='12' cy='12' r='9'/><path d='m10 8.5 5.5 3.5-5.5 3.5z'/>");
icons.put("list", "<path d='M8 6h13M8 12h13M8 18h13'/><circle cx='4' cy='6' r='1'/><circle cx='4' cy='12' r='1'/><circle cx='4' cy='18' r='1'/>");
icons.put("flow", "<rect x='3' y='3' width='7' height='6' rx='1.5'/><rect x='14' y='15' width='7' height='6' rx='1.5'/><path d='M6.5 9v3.5a2 2 0 0 0 2 2H17.5V15'/>");
icons.put("folder", "<path d='M3 6.5A1.5 1.5 0 0 1 4.5 5H9l2 2.5h8.5A1.5 1.5 0 0 1 21 9v9.5a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z'/>");
icons.put("search", "<circle cx='11' cy='11' r='7'/><path d='m20 20-3.5-3.5'/>");
icons.put("impact", "<circle cx='12' cy='12' r='2.5'/><circle cx='12' cy='12' r='6'/><circle cx='12' cy='12' r='9.5'/>");
icons.put("graph", "<circle cx='5' cy='6' r='2'/><circle cx='19' cy='6' r='2'/><circle cx='12' cy='18' r='2'/><path d='M7 6h10M6.2 7.7l4.6 8.6M17.8 7.7l-4.6 8.6'/>");
icons.put("diff", "<path d='M7 3v12M3 7h8M17 21V9M13 17h8'/>");
icons.put("book", "<path d='M4 4.5A1.5 1.5 0 0 1 5.5 3H20v15H5.5A1.5 1.5 0 0 0 4 19.5z'/><path d='M4 19.5A1.5 1.5 0 0 0 5.5 21H20'/>");
icons.put("back", "<path d='M15 18 9 12l6-6'/>");

// 이 화면 묶음의 이름, 첫 화면, 메뉴
String brandName = knowledge ? "Knowledge" : "AI";
String brandHome = knowledge ? "knowledge/index" : "ai/index";
String[][] menus = knowledge ? knowledgeMenus : aiMenus;
%>
<%!
	/** 메뉴 한 줄의 HTML. 지금 화면이면 cs-nav-on 을 붙인다 */
	private String menuItem(String linkBase, String currentLink, String[] menu, java.util.Map<String, String> icons) {
		boolean on = currentLink.equals(menu[0]);
		return "<a href=\"" + linkBase + menu[0] + "\" class=\"cs-nav-item" + (on ? " cs-nav-on" : "") + "\"" + (on ? " aria-current=\"page\"" : "") + ">"
			+ "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\">" + icons.get(menu[2]) + "</svg><span>" + menu[1] + "</span></a>";
	}
%>
			<aside id="header-wrapper" class="cs-sidebar">
				<a class="cs-brand" href="<%=linkBase%><%=brandHome%>">
					<span class="cs-brand-mark" aria-hidden="true"><%=knowledge ? "K" : "AI"%></span>
					<span class="cs-brand-text"><b>Dstone</b> <%=brandName%></span>
				</a>
				<%-- dstone-boot 의 첫 화면으로 돌아간다(dstone-boot 왼쪽 메뉴의 "main" 과 같은 곳) --%>
				<a class="cs-nav-item cs-nav-home" href="<%=contextPath%>/views/main"><svg viewBox="0 0 24 24" aria-hidden="true"><%=icons.get("back")%></svg><span>dstone-boot로 돌아가기</span></a>
				<nav class="cs-nav" aria-label="<%=brandName%> 메뉴">
					<p class="cs-nav-group"><%=knowledge ? "코드 분석" : "AI"%></p>
<%
for (int i = 0; i < menus.length; i++) {
	out.print(menuItem(linkBase, currentLink, menus[i], icons));
}
%>
				</nav>
			</aside>
