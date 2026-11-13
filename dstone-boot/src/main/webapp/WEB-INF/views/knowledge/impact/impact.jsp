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
				<h2>영향도 · 호출 관계</h2>
				<p>테이블 · SQL · 클래스 · 메소드를 고치면 어디까지 영향이 가는지, 누가 누구를 부르는지 봅니다.</p>
			</section>

			<details class="kn-howto">
				<summary>이렇게 씁니다</summary>
				<ol>
					<li>맨 위에서 프로젝트와 리비전을 고릅니다(리비전은 분석이 끝난 가장 최근 것이 골라져 있습니다).</li>
					<li><b>① 대상 찾기</b>에서 종류(메소드 · 테이블 · SQL · 타입)를 고르고 이름을 적은 뒤 "찾기". 후보가 하나뿐이면 바로 골라집니다.</li>
					<li><b>② 결과</b>에 고른 대상이 맨 위에 보이고, 그 아래 탭 세 개가 모두 이 대상에 대한 결과입니다.
						<ul>
							<li><b>영향도</b>: "이것을 고치면 무엇을 다시 확인하나"를 한 장으로 요약합니다. 맨 위 한 줄 결론, 영향받는 곳(화면 · 진입점을 업무별로), 건드리는 데이터(테이블 읽기 · 쓰기), 믿을 만한가(신뢰도, 분석이 놓쳤을 수 있는 호출) 순서입니다.</li>
							<li><b>부르는 쪽 / 불리는 쪽</b>: 메소드일 때만 씁니다. 불리는 쪽에는 실행하는 SQL과 테이블까지 나옵니다.</li>
						</ul></li>
					<li>결과 표의 <a href="javascript:void(0)" class="kn-retarget" tabindex="-1">밑줄 친 이름</a>을 누르면 그것이 새 대상이 됩니다.
						지나온 대상은 "지나온 대상"에 남아서 누르면 돌아갑니다. 브라우저의 뒤로 가기도 됩니다.</li>
				</ol>
				예시 (누르면 채워집니다. <code>struts-app</code> 기준. 채운 뒤 프로젝트가 <code>struts-app</code> 인지 확인하고 "찾기"를 누르세요):
				<span class="kn-example" data-fill='{"kn-find-kind":"table","kn-find-name":"TB_BOARD"}'>테이블 TB_BOARD</span>
				<span class="kn-example" data-fill='{"kn-find-kind":"statement","kn-find-name":"Board.deleteBoard"}'>SQL Board.deleteBoard</span>
				<span class="kn-example" data-fill='{"kn-find-kind":"type","kn-find-name":"BoardDAO"}'>클래스 BoardDAO</span>
				<span class="kn-example" data-fill='{"kn-find-kind":"method","kn-find-name":"deleteBoard"}'>메소드 deleteBoard</span>
				<br>"거리"는 대상에서 몇 단계 떨어졌는지(0 = 대상을 직접 건드리는 메소드), "신뢰도"는 거기까지 이어진 길이 얼마나 확실한지입니다.
				<span class="kn-conf-LOW">LOW</span> 는 이름만 보고 짐작한 관계가 낀 것이라 참고로만 봅니다.
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
				<h3>① 대상 찾기</h3>
				<p class="ai-hint">무엇을 고칠지 고릅니다. 고른 대상 하나에 대해 아래 ②에서 영향도와 호출 관계를 함께 봅니다.</p>
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col class="kn-find-kindcol" /><col /><col class="ai-form-btncol" /></colgroup>
					<tr>
						<th><label for="kn-find-kind">종류</label></th>
						<td><select id="kn-find-kind">
								<option value="method">메소드</option>
								<option value="table">테이블</option>
								<option value="statement">SQL</option>
								<option value="type">타입</option>
							</select></td>
						<td><input type="text" id="kn-find-name" /></td>
						<td class="ai-form-btn"><button type="button" id="kn-find-btn">찾기</button></td>
					</tr>
				</table>
				<div id="kn-find-message" class="kn-message"></div>
				<div id="kn-find-table" class="kn-scroll kn-find-list"></div>
			</section>

			<section class="ai-panel" id="kn-result-panel" hidden>
				<h3>② 결과</h3>
				<div id="kn-target-head"></div>
				<div id="kn-trail" class="kn-trail"></div>
				<div class="kn-tabbar">
					<div class="kn-tabs" role="tablist">
						<button type="button" role="tab" class="kn-tab" data-tab="impact">영향도<span class="kn-tab-count"></span></button>
						<button type="button" role="tab" class="kn-tab" data-tab="callers">부르는 쪽<span class="kn-tab-count"></span></button>
						<button type="button" role="tab" class="kn-tab" data-tab="callees">불리는 쪽<span class="kn-tab-count"></span></button>
					</div>
					<div class="kn-tab-options">
						<label id="kn-access-wrap">테이블 <select id="kn-impact-access">
								<option value="ALL">읽기 + 쓰기</option>
								<option value="WRITE">쓰기만</option>
								<option value="READ">읽기만</option>
							</select></label>
						<label>깊이 <input type="number" id="kn-depth" min="1" max="10" class="kn-depth" /></label>
					</div>
				</div>
				<p id="kn-tab-hint" class="ai-hint kn-tab-hint"></p>
				<div id="kn-tab-body"></div>
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
