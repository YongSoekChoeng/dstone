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
				<h2>노드 맵</h2>
				<p>화면 → 컨트롤러 → 서비스 → DAO → SQL 매퍼 → 테이블로 이어지는 호출 구조를 그림으로 봅니다.</p>
			</section>

			<details class="kn-howto">
				<summary>처음이신가요? 이 화면은 이렇게 씁니다 (눌러서 펴기)</summary>
				<p>누가 누구를 부르는지 그림으로 따라가는 화면입니다. 처음에는 프로젝트만 고르고, 궁금한 노드를 눌러 보는 것으로 충분합니다.</p>
				<ol>
					<li><b>전체 맵 보기</b> — 프로젝트와 리비전을 고르면 전체 맵이 그려집니다. 왼쪽부터 화면 → 컨트롤러 → 서비스 → DAO → SQL 매퍼 → 테이블 순서이고,
						선은 "부르는 쪽 → 불리는 쪽"입니다. 선이 굵을수록 그 사이의 호출이 많습니다.
						마우스 휠로 확대하고 바탕을 끌어서 옮깁니다. 맵이 크면 글자가 보이도록 왼쪽 위부터 보여 주니, 전체 모양은 <b>"화면에 맞추기"</b>로 봅니다.</li>
					<li><b>이름으로 찾기</b> — 클래스 · 화면 · 테이블 이름의 일부를 적고 "찾기"(또는 Enter)를 누르면 그 노드로 갑니다. 같은 이름이 여럿이면 다시 누를 때마다 다음 것으로 넘어갑니다.
						<code>클래스.메소드</code>처럼 적으면 그 메소드의 관계 트리가 바로 열립니다.</li>
					<li><b>묶음 펼치기</b> — 노드가 많은 프로젝트는 패키지(폴더) <b>묶음</b>으로 접혀서 나옵니다(테두리가 두 줄인 노드). 묶음을 <b>두 번 누르면 펼쳐집니다</b>.
						"보기 단위"를 "클래스"로 바꾸면 처음부터 모두 펼쳐서 그립니다.</li>
					<li><b>노드 누르기</b> — 누른 노드에 이어진 것만 남고 나머지는 흐려집니다. <span class="kn-graph-mark" style="background:#60a5fa;"></span>파란 테는 그 노드를 부르는 쪽,
						<span class="kn-graph-mark" style="background:#fb923c;"></span>주황 테는 그 노드가 부르는 쪽입니다. 바탕을 누르면 풀립니다.
						큰 프로젝트는 공통 클래스를 거쳐 거의 모든 노드가 이어지므로 "눌렀을 때 강조"를 "바로 옆만"으로 두고 시작하는 편이 보기 쉽습니다.</li>
					<li><b>오른쪽 설명 읽기</b> — 누른 노드의 위치, 계층, 처리하는 주소, 메소드 · SQL 목록이 나옵니다. 제목 옆의 <b>"소스 보기"</b> · <b>"SQL 보기"</b>를 누르면 실제 코드가 팝업으로 뜹니다.
						목록의 메소드나 SQL 이름을 누르면 관계 트리 창에 그것의 관계 트리가 열립니다.</li>
					<li><b>관계 트리</b> — 전체 맵은 클래스끼리의 관계입니다. "어느 메소드가 어느 메소드를 부르는지"는 오른쪽의 <b>"관계 트리 따로 보기"</b>를 눌러 <b>새 창(관계 트리 창)</b>에서 봅니다.
						가운데가 고른 것, 왼쪽이 부르는 쪽, 오른쪽이 불리는 쪽입니다. 창 위쪽에서 양쪽 깊이를 바꾸고, "보일 계층" 칩으로 계층을 끄고 켭니다.
						숨긴 계층을 건너 이어지는 길은 점선으로 그립니다. 창은 하나만 뜨고, 다른 노드를 열면 그 창의 내용이 바뀝니다.
						영향도 화면의 "관계 트리 보기"와 프로젝트 화면의 전환 제외 후보 탭도 같은 창을 씁니다. 창이 뜨지 않으면 브라우저에서 이 사이트의 팝업을 허용해 주세요.</li>
					<li><b>전체 화면</b> — "전체 화면"을 누르면 노드 맵이 브라우저 화면 전체로 커집니다. 그리던 그림과 고른 노드는 그대로입니다.
						"조건 숨기기"로 위의 조건표를 접으면 그림을 더 넓게 볼 수 있고, <b>Esc</b>나 "전체 화면 끝내기"로 돌아옵니다.</li>
				</ol>
				예시 (누르면 "이름으로 찾기"가 채워집니다. <code>struts-app</code> 표본 기준이니 프로젝트를 고른 뒤 "찾기"를 누르세요):
				<span class="kn-example" data-fill='{"kn-graph-find":"BoardDAO"}'>BoardDAO 찾기</span>
				<span class="kn-example" data-fill='{"kn-graph-find":"TB_BOARD"}'>테이블 TB_BOARD 찾기</span>
				<span class="kn-example" data-fill='{"kn-graph-find":"list.jsp"}'>화면 list.jsp 찾기</span>
				<span class="kn-example" data-fill='{"kn-graph-find":"BoardDAO.deleteBoard"}'>메소드 BoardDAO.deleteBoard 찾기</span>
				<p>모델(VO)은 처음에 꺼져 있습니다. getter / setter 호출이 그림을 덮어 버리기 때문입니다. "계층" 줄의 색깔 칩으로 계층을 켜고 끕니다. "선" 줄은 선 색깔이 무슨 관계인지 알려 주는 범례이고, 점선은 짐작한 관계(LOW)입니다.
					그림에는 분석이 이어 낸 관계만 나옵니다. 리플렉션이나 문자열로 조립한 주소처럼 분석이 따라가지 못한 호출에는 선이 없습니다.</p>
			</details>

			<%-- 전체 화면이면 이 영역(#kn-graph-workspace)이 브라우저 화면 전체를 덮는다(graph.js setFullscreen) --%>
			<section class="ai-panel kn-graph-workspace" id="kn-graph-workspace">
				<h3>노드 맵</h3>
				<p class="ai-hint">호출 구조를 한눈에 봅니다. 클래스 · 화면(JSP · WebSquare · Nexacro) · SQL 매퍼 · 테이블이 노드이고, 그 사이의 호출 / SQL 실행 / 테이블 사용 / 화면 이동이 선입니다.
					선이 굵을수록 그 사이의 호출이 많습니다.</p>
				<div class="kn-graph-conditions">
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th><label for="kn-graph-project">프로젝트</label></th>
						<td><select id="kn-graph-project"></select></td>
						<th><label for="kn-graph-revision">리비전</label></th>
						<td><select id="kn-graph-revision"></select></td>
						<th><label for="kn-graph-find">이름으로 찾기</label></th>
						<td><div class="ai-form-inline"><input type="text" id="kn-graph-find" placeholder="클래스 / 화면 / 테이블 / 클래스.메소드" />
							<button type="button" id="kn-graph-find-btn" class="kn-plain">찾기</button></div></td>
					</tr>
					<tr>
						<th><label for="kn-graph-unit">보기 단위</label></th>
						<td><select id="kn-graph-unit">
								<option value="GROUP">묶음 (패키지 · 폴더)</option>
								<option value="TYPE">클래스</option>
							</select></td>
						<th><label for="kn-graph-layout">배치</label></th>
						<td><select id="kn-graph-layout">
								<option value="TIER">계층별 (왼쪽 → 오른쪽)</option>
								<option value="FREE">자유 (가까운 것끼리)</option>
							</select></td>
						<th><label for="kn-graph-range">눌렀을 때 강조</label></th>
						<td><select id="kn-graph-range">
								<option value="ALL">이어진 끝까지</option>
								<option value="1">바로 옆만</option>
								<option value="2">두 걸음까지</option>
							</select></td>
					</tr>
					<tr>
						<th>같이 볼 관계</th>
						<td colspan="5" class="ai-form-checks"><label><input type="checkbox" id="kn-graph-low" checked /> 짐작한 관계(LOW)도</label>
							<label><input type="checkbox" id="kn-graph-struct" /> 상속 · 구현 · 주입도</label></td>
					</tr>
					<tr>
						<th>계층</th>
						<td colspan="5"><div class="kn-row" id="kn-graph-tiers"></div></td>
					</tr>
					<tr>
						<th>선</th>
						<td colspan="5"><div class="kn-row" id="kn-graph-edges"></div></td>
					</tr>
				</table>
				</div>
				<div class="ai-form-actions">
					<button type="button" id="kn-graph-cond-toggle" class="kn-plain kn-graph-cond-toggle">조건 숨기기</button>
					<button type="button" id="kn-graph-full" class="kn-plain kn-graph-full-btn">전체 화면</button>
					<button type="button" id="kn-graph-fit" class="kn-plain">화면에 맞추기</button>
					<button type="button" id="kn-graph-load">다시 불러오기</button>
				</div>
				<div id="kn-graph-message" class="kn-message"></div>
				<div class="kn-graph-body">
					<div id="kn-graph-canvas" class="kn-graph-canvas"></div>
					<div id="kn-graph-detail" class="kn-graph-detail"></div>
				</div>
			</section>
					</div>
				</div>
			</div>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<jsp:include page="codeModal.jsp"></jsp:include>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/lib/cytoscape.min.js"></script>
	<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/knowledge/assets/lib/highlight-github.min.css" />
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/lib/highlight.min.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/graph-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/graph.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeGraph.init();
	</script>
</body>
</html>
