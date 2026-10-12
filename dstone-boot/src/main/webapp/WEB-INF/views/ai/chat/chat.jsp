<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
%>
<!DOCTYPE HTML>
<html>

<jsp:include page="../common/head.jsp"></jsp:include>

<body class="ai-body">
	<div id="page-wrapper">

		<jsp:include page="../common/header.jsp"></jsp:include>

		<!-- Main -->
		<div id="main">
			<div class="container">
				<div class="row main-row">
					<div class="col-12">
			<section>
				<h2 align="center" style="font-weight: bold;">Chat</h2>
				<p>dstone-ai-engine 의 Agent 와 실시간으로 대화합니다. Agent 를 고르고, 필요하면 올린 문서 검색(RAG) · 도구호출(Tool) 을 켠 뒤 메시지를 보냅니다.</p>
			</section>

			<section class="ai-panel">
				<h3>대화 설정</h3>
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th><label for="chat-agent">Agent</label></th>
						<td><select id="chat-agent"></select></td>
						<th>옵션</th>
						<td class="ai-form-checks"><label title="켜면 질문과 가까운 문서 조각을 찾아 답변의 참고자료로 붙입니다. 문서는 코드 분석(Knowledge) &gt; 검색 · 문서에서 올립니다."><input type="checkbox" id="chat-rag-enabled" /> 올린 문서 검색(RAG)</label>
							<label><input type="checkbox" id="chat-tools-enabled" /> 도구호출(Tool)</label></td>
						<th><label for="chat-model-override">모델 override</label></th>
						<td><input type="text" id="chat-model-override" placeholder="비우면 기본값(Agent 정의값)" /></td>
					</tr>
				</table>
				<p id="chat-agent-desc" class="ai-hint"></p>
				<p class="ai-hint">"올린 문서 검색(RAG)"을 켜면 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/search/search">코드 분석(Knowledge) &gt; 검색 · 문서</a>에서 올린 문서를 참고해서 답합니다.
					방금 올린 문서는 임베딩이 끝난 뒤(보통 1분 안쪽)부터 뜻으로 찾힙니다.</p>
			</section>

			<section class="ai-panel">
				<h3>대화</h3>
				<div class="ai-chat-box">
					<div id="chat-messages" class="chat-messages"></div>

					<form id="chat-form" class="chat-form">
						<div class="chat-input-row">
							<textarea id="chat-input" class="chat-input" placeholder="메시지를 입력하세요..." rows="2"></textarea>
							<button type="submit" id="chat-send" class="chat-send">전송</button>
						</div>
					</form>
				</div>
			</section>
					</div>
				</div>
			</div>
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
