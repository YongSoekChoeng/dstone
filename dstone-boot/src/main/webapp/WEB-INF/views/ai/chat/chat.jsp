<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
%>
<!DOCTYPE HTML>
<html>

<jsp:include page="../common/head.jsp"></jsp:include>

<body class="ai-body">
	<div id="ai-page-wrapper">

		<jsp:include page="../common/header.jsp"></jsp:include>

		<div id="ai-main" class="ai-chat-main">
			<div id="chat-messages" class="chat-messages"></div>

			<form id="chat-form" class="chat-form">
				<div class="chat-options">
					<label>Agent
						<select id="chat-agent"></select>
					</label>
					<label title="켜면 질문과 가까운 문서 조각을 찾아 답변의 참고자료로 붙입니다. 문서는 코드 분석(Knowledge) &gt; 검색 · 문서에서 올립니다."><input type="checkbox" id="chat-rag-enabled" /> 올린 문서 검색(RAG)</label>
					<label><input type="checkbox" id="chat-tools-enabled" /> 도구호출(Tool)</label>
					<label>모델 override
						<input type="text" id="chat-model-override" class="chat-model-input" placeholder="비우면 기본값(Agent 정의값)" />
					</label>
				</div>
				<p id="chat-agent-desc" class="ai-hint"></p>
				<p class="ai-hint">"올린 문서 검색(RAG)"을 켜면 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/search/search">코드 분석(Knowledge) &gt; 검색 · 문서</a>에서 올린 문서를 참고해서 답합니다.
					방금 올린 문서는 임베딩이 끝난 뒤(보통 1분 안쪽)부터 뜻으로 찾힙니다.</p>
				<div class="chat-input-row">
					<textarea id="chat-input" class="chat-input" placeholder="메시지를 입력하세요..." rows="2"></textarea>
					<button type="submit" id="chat-send" class="chat-send">전송</button>
				</div>
			</form>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/chat.js"></script>
	<script>
		DstoneAiChat.init({
			listUrl: "<%=requestUtil.getStrContextPath()%>/ai/chat/list.do",
			sendUrl: "<%=requestUtil.getStrContextPath()%>/ai/chat/sendMessage.do"
		});
	</script>
</body>
</html>
