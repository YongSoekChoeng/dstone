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
					<label><input type="checkbox" id="chat-rag-enabled" /> 문서검색(RAG)</label>
					<label><input type="checkbox" id="chat-tools-enabled" /> 도구호출(Tool)</label>
					<label>provider
						<select id="chat-provider" name="provider">
							<option value="sqlcoder">sqlcoder</option>
							<option value="llama3.2">llama3.2</option>
							<option value="bge-m3" selected>bge-m3</option>
						</select>
					</label>
				</div>
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
		DstoneAiChat.init("<%=requestUtil.getStrContextPath()%>/ai/chat/sendMessage.do");
	</script>
</body>
</html>
