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
						<td class="ai-form-checks"><label title="켜면 직접 올린 문서에서 질문과 가까운 조각을 찾아 답변의 참고자료로 붙입니다. 소스 코드 분석 결과는 여기서 찾지 않습니다(도구호출을 쓰세요)."><input type="checkbox" id="chat-rag-enabled" /> 올린 문서 검색(RAG)</label>
							<label title="켜면 Agent 에 허용된 Tool 을 씁니다. 소스 코드 분석 결과(테이블 사용처, 호출 관계)도 Tool 로 찾습니다."><input type="checkbox" id="chat-tools-enabled" /> 도구호출(Tool)</label></td>
						<th><label for="chat-model-override">모델 override</label></th>
						<td><input type="text" id="chat-model-override" placeholder="비우면 기본값(Agent 정의값)" /></td>
					</tr>
				</table>
				<p id="chat-agent-desc" class="ai-hint"></p>
				<div class="kn-section-title">무엇을 물을 때 무엇을 켜나</div>
				<p class="ai-hint">"올린 문서 검색(RAG)"과 "도구호출(Tool)"은 찾는 곳이 서로 다릅니다. RAG 는 <b>직접 올린 문서</b>에서만 찾고,
					소스 코드 분석 결과(테이블을 쓰는 소스, 호출 관계 등)는 <b>Tool</b> 로만 찾습니다.</p>
				<table class="document-list-table ai-guide-table">
					<thead>
						<tr><th>묻고 싶은 것</th><th>켤 것</th><th>Agent</th><th>이렇게 묻습니다</th></tr>
					</thead>
					<tbody>
						<tr>
							<td>올린 문서(설계서, 매뉴얼 등)의 내용</td>
							<td><b>올린 문서 검색(RAG)</b></td>
							<td><code>sample-general-chat</code></td>
							<td>평소 말하듯 묻습니다. 예: <code>배치 재처리 절차를 알려줘</code><br>
								문서는 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/search/search">코드 분석(Knowledge) &gt; 검색 · 문서</a>에서 올립니다.
								방금 올린 문서는 임베딩이 끝난 뒤(보통 1분 안쪽)부터 뜻으로 찾힙니다.</td>
						</tr>
						<tr>
							<td>분석해 둔 소스 코드<br>(어느 테이블을 누가 쓰나, 누가 누구를 부르나, 고치면 어디가 영향받나)</td>
							<td><b>도구호출(Tool)</b></td>
							<td><code>sample-general-chat</code></td>
							<td><b>프로젝트 ID 를 같이 적습니다.</b> 예: <code>HI-Japan 프로젝트에서 JC_ACDT_AGMT 테이블을 사용하는 소스들을 알려줘</code><br>
								프로젝트 ID 는 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/index">코드 분석(Knowledge) &gt; 프로젝트 · 분석</a>의 projectId 입니다. 분석이 끝난(READY) 가장 최근 리비전에서 찾습니다.</td>
						</tr>
						<tr>
							<td>위와 같음 (코드 분석 전용 Agent)</td>
							<td><b>도구호출(Tool)</b></td>
							<td><code>sample-knowledge-agent</code></td>
							<td>입력을 JSON 으로 적습니다. 예: <code>{"projectId": "HI-Japan", "question": "JC_ACDT_AGMT 테이블을 사용하는 소스들을 알려줘"}</code></td>
						</tr>
					</tbody>
				</table>
				<p class="ai-hint">체크하지 않은 옵션은 Agent 정의에 켜져 있어도 이번 대화에서는 꺼집니다. 예를 들어 "도구호출(Tool)"을 끄면 어떤 Agent 든 Tool 없이 답합니다.
					RAG 는 문서를 못 찾거나 dstone-knowledge 가 응답하지 않으면 오류 없이 참고자료 없는 답을 합니다.</p>
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
			sendUrl: "<%=requestUtil.getStrContextPath()%>/ai/chat/sendMessage.do",
			// 화면을 열었을 때 골라 둘 Agent (서버가 agent 를 비워 받았을 때 쓰는 기본값과 같다)
			defaultAgent: "sample-general-chat"
		});
	</script>
</body>
</html>
