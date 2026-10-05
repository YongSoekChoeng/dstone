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

		<div id="ai-main">
			<details class="kn-howto" open>
				<summary>이렇게 씁니다</summary>
				<ol>
					<li>같은 프로젝트를 <b>두 번 이상 분석했을 때</b> 씁니다. 소스가 바뀐 뒤 "프로젝트 · 분석"에서 한 번 더 분석하면(증분 분석을 체크하면 빠릅니다) 리비전이 둘이 됩니다.</li>
					<li>프로젝트와 (새) 리비전을 고르고 "비교". 기준은 그대로 두면 바로 앞 리비전과 견줍니다.</li>
					<li>요약 표에서 종류별로 생김 / 없어짐 / 바뀜 건수를 보고, 아래 목록에서 무엇인지 확인합니다.</li>
				</ol>
				이런 것을 확인하는 데 씁니다: 이번 변경으로 <b>주소가 새로 열렸는지</b>(진입점 · 생김), <b>테이블을 새로 건드리게 된 SQL</b>이 있는지(테이블 사용 · 생김),
				<b>없어진 메소드를 아직 부르는 곳</b>은 없는지(호출 · 없어짐). 메소드 이름을 바꾸면 "없어짐 + 생김" 두 줄로 나옵니다.
			</details>

			<section class="ai-panel">
				<h3>리비전 비교</h3>
				<p class="ai-hint">두 리비전 사이에 달라진 것을 분석 결과의 말로 봅니다: 어느 메소드가 생기고 없어졌는지, 어느 주소가 새로 열렸는지,
					어느 SQL이 다른 테이블을 건드리게 됐는지, 누가 누구를 새로 부르게 됐는지. 소스의 줄 단위 차이가 아닙니다.
					메소드의 몸통만 바뀐 것은 "파일 · 바뀜"과, 그 결과로 달라진 호출 · 테이블 사용으로 드러납니다.</p>
				<div class="kn-row">
					<label for="kn-diff-project">프로젝트</label>
					<select id="kn-diff-project"></select>
					<label for="kn-diff-revision">리비전</label>
					<select id="kn-diff-revision"></select>
					<label for="kn-diff-base">기준(앞) 리비전</label>
					<select id="kn-diff-base"></select>
					<label for="kn-diff-limit">종류마다</label>
					<input type="number" id="kn-diff-limit" min="0" max="2000" value="200" style="width:70px;" />
					<label>건까지</label>
					<button type="button" id="kn-diff-btn">비교</button>
				</div>
				<div id="kn-diff-message" class="kn-message"></div>
				<div id="kn-diff-summary"></div>
				<div id="kn-diff-result"></div>
			</section>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/diff.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeDiff.init();
	</script>
</body>
</html>
