<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
%>
<!DOCTYPE HTML>
<html>

<%-- 관계 트리 창. 노드 맵 · 영향도 · 미사용 탭이 window.open 으로 띄운다(knowledge-common.js 의 openTreeWindow).
     따로 뜬 창이라 왼쪽 메뉴 없이 트리만 창 가득 그린다. 무엇을 그릴지는 주소의 #p=..&r=..&tree=.. 로 받는다(tree.js) --%>
<jsp:include page="../common/head.jsp"></jsp:include>

<body class="ai-body kn-tree-page">
	<div class="kn-tree-window">
		<div class="kn-row">
			<div class="kn-tree-heading">
				<b id="kn-tree-title" class="kn-graph-modal-title">관계 트리</b>
				<span id="kn-tree-where" class="ai-hint"></span>
			</div>
			<label for="kn-tree-up">부르는 쪽 깊이</label>
			<input type="number" id="kn-tree-up" min="0" max="10" value="3" style="width:56px;" />
			<label for="kn-tree-down">불리는 쪽 깊이</label>
			<input type="number" id="kn-tree-down" min="0" max="10" value="3" style="width:56px;" />
			<label><input type="checkbox" id="kn-tree-model" /> 모델(VO) 호출도</label>
			<button type="button" id="kn-tree-reload">다시 그리기</button>
			<button type="button" id="kn-tree-full" class="kn-plain">전체 화면</button>
			<button type="button" id="kn-tree-close" class="kn-plain">닫기</button>
		</div>
		<%-- 계층 보이기 / 숨기기. 숨긴 계층을 건너 이어지는 길은 점선으로 잇는다 --%>
		<div class="kn-row kn-tree-tiers">
			<span class="kn-tree-tiers-label">보일 계층</span>
			<span id="kn-tree-tiers"></span>
		</div>
		<div id="kn-tree-message" class="kn-message"></div>
		<div class="kn-graph-body kn-tree-body">
			<div id="kn-tree-canvas" class="kn-graph-canvas"></div>
			<div id="kn-tree-detail" class="kn-graph-detail"></div>
		</div>
	</div>

	<jsp:include page="codeModal.jsp"></jsp:include>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/lib/cytoscape.min.js"></script>
	<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/knowledge/assets/lib/highlight-github.min.css" />
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/lib/highlight.min.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/graph-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/tree.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeTree.init();
	</script>
</body>
</html>
