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

			<details class="kn-howto" open>
				<summary>이렇게 씁니다</summary>
				<ol>
					<li>프로젝트와 리비전을 고르면 <b>전체 맵</b>이 그려집니다. 왼쪽에서 오른쪽으로 화면 → 컨트롤러 → 서비스 → DAO → SQL 매퍼 → 테이블 순서이고,
						선은 "부르는 쪽 → 불리는 쪽"입니다. 마우스 휠로 확대하고 바탕을 끌어서 옮깁니다.</li>
					<li>노드가 많은 프로젝트는 패키지(폴더) <b>묶음</b>으로 접혀서 나옵니다(테두리가 두 줄인 노드). 묶음을 <b>두 번 누르면 펼쳐집니다</b>.
						"보기 단위"를 "클래스"로 바꾸면 전부 펼칩니다.</li>
					<li><b>노드를 누르면</b> 거기에 이어진 것만 남고 나머지는 흐려집니다. <span class="kn-graph-mark" style="background:#60a5fa;"></span>파란 테는 그 노드를 부르는 쪽,
						<span class="kn-graph-mark" style="background:#fb923c;"></span>주황 테는 그 노드가 부르는 쪽입니다. 오른쪽에는 그 노드의 <b>설명</b>이 나옵니다. 바탕을 누르면 풀립니다.
						어디까지 강조할지는 "눌렀을 때 강조"에서 고릅니다. 큰 프로젝트는 공통 클래스를 거쳐 거의 전부가 이어지므로 "바로 옆만"으로 시작합니다.</li>
					<li>오른쪽의 <b>"관계 트리 따로 보기"</b>를 누르면 그 노드에 이어진 길을 <b>메소드 단위</b>로 팝업에 그립니다.
						전체 맵은 클래스끼리의 관계라 "어느 메소드가 어느 메소드를 부르는지"는 팝업에서 봅니다.</li>
					<li><b>"전체 화면"</b>을 누르면 노드 맵이 브라우저 화면 전체로 커집니다. 그리던 그림과 고른 노드는 그대로 이어집니다.
						전체 화면에서는 "조건 숨기기"로 위의 조건표를 접어 그림을 더 넓게 볼 수 있고, <b>Esc</b>나 "전체 화면 끝내기"로 돌아옵니다.</li>
				</ol>
				예시 (누르면 채워집니다. <code>struts-app</code> 기준. 채운 뒤 "찾기"를 누르세요):
				<span class="kn-example" data-fill='{"kn-graph-find":"BoardDAO"}'>BoardDAO 찾기</span>
				<span class="kn-example" data-fill='{"kn-graph-find":"TB_BOARD"}'>테이블 TB_BOARD 찾기</span>
				<span class="kn-example" data-fill='{"kn-graph-find":"list.jsp"}'>화면 list.jsp 찾기</span>
				<span class="kn-example" data-fill='{"kn-graph-find":"BoardDAO.deleteBoard"}'>메소드 BoardDAO.deleteBoard 찾기</span>
				<br>모델(VO)은 처음에 꺼져 있습니다. getter / setter 호출이 그림을 덮기 때문입니다. 위의 색깔 딱지를 눌러 계층을 켜고 끕니다.
				그림에는 분석이 이은 관계만 나옵니다. 리플렉션이나 문자열로 조립한 주소처럼 잇지 못한 호출은 선이 없습니다.
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

	<%-- 관계 트리 팝업: 고른 노드에 이어진 길을 메소드 단위로 그린다 --%>
	<div id="kn-tree-modal" class="kn-graph-modal" style="display:none;">
		<div class="kn-graph-modal-box">
			<div class="kn-row">
				<b id="kn-tree-title" class="kn-graph-modal-title"></b>
				<label for="kn-tree-up">부르는 쪽 깊이</label>
				<input type="number" id="kn-tree-up" min="0" max="10" value="3" style="width:56px;" />
				<label for="kn-tree-down">불리는 쪽 깊이</label>
				<input type="number" id="kn-tree-down" min="0" max="10" value="3" style="width:56px;" />
				<label><input type="checkbox" id="kn-tree-model" /> 모델(VO) 호출도</label>
				<button type="button" id="kn-tree-reload">다시 그리기</button>
				<button type="button" id="kn-tree-close" class="kn-plain">닫기</button>
			</div>
			<div id="kn-tree-message" class="kn-message"></div>
			<div class="kn-graph-body kn-graph-modal-body">
				<div id="kn-tree-canvas" class="kn-graph-canvas"></div>
				<div id="kn-tree-detail" class="kn-graph-detail"></div>
			</div>
		</div>
	</div>

	<%-- 소스 / SQL 팝업: 노드 상세의 [소스 보기] [SQL 보기] 버튼으로 연다. 관계 트리 팝업 위에도 뜬다 --%>
	<div id="kn-code-modal" class="kn-graph-modal kn-code-modal" style="display:none;">
		<div class="kn-code-box">
			<div class="kn-row">
				<b id="kn-code-title" class="kn-graph-modal-title"></b>
				<button type="button" id="kn-code-raw" class="kn-plain" style="display:none;">원문 보기</button>
				<button type="button" id="kn-code-copy" class="kn-plain">복사</button>
				<button type="button" id="kn-code-close" class="kn-plain">닫기</button>
			</div>
			<div id="kn-code-where" class="ai-hint kn-mono kn-code-where"></div>
			<pre id="kn-code-text" class="kn-gd-doc kn-code-text"></pre>
		</div>
	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/lib/cytoscape.min.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/graph.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeGraph.init();
	</script>
</body>
</html>
