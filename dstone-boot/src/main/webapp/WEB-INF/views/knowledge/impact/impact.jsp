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
				<p class="cs-eyebrow">코드 분석</p>
				<h2>영향도</h2>
				<p>테이블 · 컬럼 · SQL · 클래스 · 메소드, 또는 이번 변경분을 고치면 무엇을 다시 테스트해야 하는지 봅니다.</p>
			</section>

			<details class="kn-howto">
				<summary>이렇게 씁니다</summary>
				<ol>
					<li>맨 위에서 프로젝트와 리비전을 고릅니다(분석이 끝난 가장 최근 리비전이 골라져 있습니다).</li>
					<li><b>① 대상</b>에서 종류를 고르고 이름을 적은 뒤 "보기"를 누릅니다. 후보가 하나뿐이면 바로 골라집니다.
						"변경분"은 이름 대신 견줄 앞 리비전을 고릅니다.</li>
					<li><b>② 결과</b>는 숫자 네 개로 시작합니다. 그 아래 <b>다시 테스트할 곳</b>이 확실한 관계(HIGH)로 이어진 화면과 진입점이고,
						<b>[왜?]</b>를 누르면 대상에서 거기까지 이어진 길이 나옵니다.
						이름만 보고 짐작했거나 덜 확실한 관계로 이어진 곳은 <b>추정 관계</b>에 따로 모았습니다.</li>
					<li><a href="javascript:void(0)" class="kn-retarget" tabindex="-1">밑줄 친 이름</a>을 누르면 그것이 새 대상이 됩니다.
						누가 누구를 부르는지는 <b>노드 맵에서 보기</b>로 그림을 엽니다.</li>
				</ol>
				예시 (누르면 채워집니다. <code>struts-app</code> 기준):
				<span class="kn-example" data-fill='{"kn-find-kind":"table","kn-find-name":"TB_BOARD"}'>테이블 TB_BOARD</span>
				<span class="kn-example" data-fill='{"kn-find-kind":"column","kn-find-name":"TB_BOARD.TITLE"}'>컬럼 TB_BOARD.TITLE</span>
				<span class="kn-example" data-fill='{"kn-find-kind":"statement","kn-find-name":"Board.deleteBoard"}'>SQL Board.deleteBoard</span>
				<span class="kn-example" data-fill='{"kn-find-kind":"method","kn-find-name":"BoardDAO#deleteBoard"}'>메소드 deleteBoard</span>
			</details>

			<section class="ai-panel kn-scope">
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th><label for="kn-impact-project">프로젝트</label></th>
						<td><select id="kn-impact-project"></select></td>
						<th><label for="kn-impact-revision">리비전</label></th>
						<td><select id="kn-impact-revision"></select></td>
					</tr>
				</table>
			</section>

			<section class="ai-panel">
				<h3>① 대상</h3>
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col class="kn-find-kindcol" /><col /><col class="ai-form-btncol" /></colgroup>
					<tr>
						<th><label for="kn-find-kind">종류</label></th>
						<td><select id="kn-find-kind">
								<option value="method">메소드</option>
								<option value="table">테이블</option>
								<option value="column">컬럼</option>
								<option value="statement">SQL</option>
								<option value="type">클래스</option>
								<option value="changes">변경분</option>
							</select></td>
						<td><input type="text" id="kn-find-name" /><select id="kn-find-base" hidden></select></td>
						<td class="ai-form-btn"><button type="button" id="kn-find-btn">보기</button></td>
					</tr>
				</table>
				<div id="kn-find-message" class="kn-message"></div>
				<div id="kn-find-table" class="kn-scroll kn-find-list"></div>
			</section>

			<section class="ai-panel" id="kn-result-panel" hidden>
				<h3>② 결과</h3>
				<div id="kn-target-head"></div>
				<div id="kn-trail" class="kn-trail"></div>
				<div id="kn-impact-message" class="kn-message"></div>
				<div id="kn-impact-body"></div>
			</section>

					 </div>
				</div>
			</div>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/impact.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeImpact.init();
	</script>
</body>
</html>
