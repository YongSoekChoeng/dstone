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
			<section class="cs-page-head">
				<p class="cs-eyebrow">AI</p>
				<h2>채팅</h2>
				<p>dstone-ai-engine 의 Agent 와 실시간으로 대화합니다. Agent 를 고르고, 이번 대화에 쓸 도구(Tool)를 고른 뒤 메시지를 보냅니다.</p>
			</section>

			<section class="ai-panel">
				<h3>대화 설정</h3>
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th><label for="chat-agent">Agent</label></th>
						<td><select id="chat-agent"></select></td>
						<th><label for="chat-model-override">모델 override</label></th>
						<td><input type="text" id="chat-model-override" placeholder="비우면 기본값(Agent 정의값)" /></td>
					</tr>
					<tr>
						<th>도구호출(Tool)</th>
						<td colspan="3">
							<div id="chat-tools" class="chat-tools wd-chip-list"></div>
						</td>
					</tr>
				</table>
				<p id="chat-agent-desc" class="ai-hint"></p>
				<details class="kn-howto chat-tool-guide">
					<summary>무엇을 물을 때 어떤 Tool 을 고르나 (눌러서 펴기)</summary>
					<p class="ai-hint">[+ 찾아서 추가]를 누르면 고른 Agent 가 쓸 수 있는 Tool 이 종류별(소스 RAG, 문서 RAG, MCP, 일반) 표로 나옵니다. 표 머리의 체크박스로 그 종류를 한꺼번에 고를 수 있습니다.
						고른 Tool 만 이번 대화에 붙고, 하나도 고르지 않으면 Tool 없이 답합니다.
						Agent 는 필요할 때 Tool 을 직접 불러 찾아봅니다. 질문마다 문서를 미리 찾아 붙이는 일은 없습니다.</p>
					<table class="document-list-table ai-guide-table">
						<thead>
							<tr><th>묻고 싶은 것</th><th>고를 Tool</th><th>Agent</th><th>이렇게 묻습니다</th></tr>
						</thead>
						<tbody>
							<tr>
								<td>올린 문서(개발 가이드, 설계서, 매뉴얼 등)의 내용</td>
								<td><b>문서 RAG</b> 모두<br><code>knowledgeListDocs</code> <code>knowledgeSearchDocs</code> <code>knowledgeGetChunks</code></td>
								<td><code>sample-general-chat</code></td>
								<td>평소 말하듯 묻습니다. 어느 문서 묶음인지 같이 적으면 그 안에서만 찾습니다.<br>
									예: <code>웹스퀘어 가이드에서 GridView 셀을 여러 개 선택하는 방법을 찾아줘</code><br>
									문서는 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/search/search">코드 분석(Knowledge) &gt; 검색 · 문서</a>에서 올립니다.
									방금 올린 문서는 임베딩이 끝난 뒤부터 뜻으로 찾힙니다(그 전에도 영문 이름으로는 찾힙니다).</td>
							</tr>
							<tr>
								<td>분석해 둔 소스 코드<br>(어느 테이블을 누가 쓰나, 누가 누구를 부르나, 고치면 어디가 영향받나)</td>
								<td><b>소스 RAG</b> 모두<br><code>knowledgeSearch</code> <code>knowledgeTableUsage</code> <code>knowledgeCallers</code> 등</td>
								<td><code>sample-general-chat</code></td>
								<td><b>프로젝트 ID 를 같이 적습니다.</b> 예: <code>HI-Japan 프로젝트에서 JC_ACDT_AGMT 테이블을 사용하는 소스들을 알려줘</code><br>
									프로젝트 ID 는 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/index">코드 분석(Knowledge) &gt; 프로젝트 · 분석</a>의 projectId 입니다. 분석이 끝난(READY) 가장 최근 리비전에서 찾습니다.</td>
							</tr>
							<tr>
								<td>위와 같음 (코드 분석 전용 Agent)</td>
								<td><b>소스 RAG</b> 모두</td>
								<td><code>sample-knowledge-agent</code></td>
								<td>입력을 JSON 으로 적습니다. 예: <code>{"projectId": "HI-Japan", "question": "JC_ACDT_AGMT 테이블을 사용하는 소스들을 알려줘"}</code></td>
							</tr>
						</tbody>
					</table>
				</details>
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

	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/tool-picker.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/chat.js"></script>
	<script>
		DstoneAiChat.init({
			listUrl: "<%=requestUtil.getStrContextPath()%>/ai/chat/list.do",
			sendUrl: "<%=requestUtil.getStrContextPath()%>/ai/chat/sendMessage.do",
			// 화면을 열었을 때 골라 둘 Agent (서버가 agent 를 비워 받았을 때 쓰는 기본값과 같다)
			defaultAgent: "sample-general-chat"
		});
	</script>
</body>
</html>
