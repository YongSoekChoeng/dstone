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
			<section class="ai-panel">
				<h3>영향도 분석</h3>
				<p class="ai-hint">"이것을 고치면 어디까지 닿는가"를 봅니다. 대상을 직접 건드리는 메소드에서 부르는 쪽으로 거슬러 올라가며
					닿는 진입점(주소), 화면, 메소드를 모읍니다. 신뢰도는 거기까지 가는 길에서 가장 약한 관계의 값입니다.
					LOW는 이름만 보고 짐작한 관계가 낀 것이라 참고로만 봅니다.</p>
				<div class="kn-row">
					<label for="kn-impact-project">프로젝트</label>
					<select id="kn-impact-project"></select>
					<label for="kn-impact-revision">리비전</label>
					<select id="kn-impact-revision"></select>
				</div>
				<div class="kn-row">
					<label for="kn-impact-kind">대상</label>
					<select id="kn-impact-kind">
						<option value="table">테이블</option>
						<option value="statement">SQL statement</option>
						<option value="type">타입 (전체 이름)</option>
						<option value="methodId">메소드 (methodId)</option>
					</select>
					<input type="text" id="kn-impact-target" class="kn-wide" placeholder="예: TB_ORDER / Order.selectList / com.shop.OrderService" />
					<label for="kn-impact-access">테이블일 때</label>
					<select id="kn-impact-access">
						<option value="ALL">읽기 + 쓰기</option>
						<option value="WRITE">쓰기만</option>
						<option value="READ">읽기만</option>
					</select>
					<label for="kn-impact-depth">깊이</label>
					<input type="number" id="kn-impact-depth" min="0" max="10" value="5" style="width:60px;" />
					<button type="button" id="kn-impact-btn">분석</button>
				</div>
				<div id="kn-impact-message" class="kn-message"></div>
				<div id="kn-impact-cards" class="kn-cards"></div>
				<div id="kn-impact-result"></div>
			</section>

			<section class="ai-panel">
				<h3>호출 관계</h3>
				<p class="ai-hint">메소드를 이름으로 찾아, 부르는 쪽과 불리는 쪽을 따라갑니다. 불리는 쪽에는 실행하는 SQL, 그 SQL이 건드리는 테이블,
					여는 화면까지 나옵니다. 테이블 이름을 모르면 아래에서 테이블을 먼저 찾으세요.</p>
				<div class="kn-row">
					<input type="text" id="kn-method-name" placeholder="메소드 이름 (정확히)" />
					<input type="text" id="kn-method-owner" placeholder="타입 이름의 일부 (선택)" />
					<button type="button" id="kn-method-btn">메소드 찾기</button>
					<input type="text" id="kn-table-name" placeholder="테이블 이름의 일부" />
					<button type="button" id="kn-table-btn" class="kn-plain">테이블 찾기</button>
					<label for="kn-graph-depth">따라갈 깊이</label>
					<input type="number" id="kn-graph-depth" min="1" max="5" value="2" style="width:60px;" />
				</div>
				<div id="kn-graph-message" class="kn-message"></div>
				<div id="kn-method-table" class="kn-scroll"></div>
				<div id="kn-graph-title" class="kn-section-title"></div>
				<div id="kn-graph-table"></div>
			</section>
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
