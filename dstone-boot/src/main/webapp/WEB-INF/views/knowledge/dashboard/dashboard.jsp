<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%--
	코드 분석(Knowledge)의 첫 화면(대시보드).
	상태 한 줄, 프로젝트마다 계층 흐름 띠(화면 → … → 테이블), 최근 분석 작업, 임베딩, 올린 문서를 한눈에 봅니다.
	숫자는 dstone-knowledge 의 GET /api/dashboard 한 번으로 받고, 서버 상태만 /api/system/health 로 따로 묻습니다.
--%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
String linkBase = requestUtil.getStrContextPath() + "/defaultLink.do?defaultLink=";
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
			<section class="cs-page-head kn-dash-head">
				<div>
					<h2>대시보드</h2>
					<p>분석한 프로젝트마다 화면에서 테이블까지 무엇이 몇 개인지, 지금 분석이 어디까지 왔는지 봅니다.</p>
				</div>
				<div class="kn-dash-refresh">
					<span id="kn-dash-updated" class="kn-dash-updated"></span>
					<button type="button" id="kn-dash-refresh-btn" class="kn-btn kn-plain">지금 다시 보기</button>
				</div>
			</section>

			<%-- 서버 · 분석 작업 · 임베딩 · 올린 문서를 한 줄로. 눈에 띄지 않게 두고, 문제가 있을 때만 색이 붙는다 --%>
			<div id="kn-dash-status" class="kn-dash-status"></div>

			<details class="kn-howto">
				<summary>처음이신가요? 이 화면은 이렇게 씁니다 (눌러서 펴기)</summary>
				<p>코드 분석의 첫 화면입니다. 여기서 직접 무언가를 고치지는 않고, 지금 상태를 훑어본 뒤 필요한 화면으로 넘어갑니다.</p>
				<ol>
					<li><b>맨 위 한 줄</b> — 서버, 돌고 있는 분석, 임베딩, 올린 문서의 상태입니다. 문제가 있으면 빨간색으로 바뀝니다. 누르면 그 내용을 다루는 화면으로 갑니다.</li>
					<li><b>프로젝트</b> — 분석이 끝난 가장 최근 리비전에서 화면 → 컨트롤러 → 서비스 → DAO → SQL → 테이블이 몇 개인지 보여 줍니다.
						칸을 누르면 노드 맵이 그 리비전으로 열립니다. 프로젝트 이름을 누르면 프로젝트 화면으로 갑니다.</li>
					<li><b>호출 해석률</b> — 메소드 호출 가운데 어느 메소드를 부르는지 알아낸 비율입니다. 95% 이상이면 초록, 80% 미만이면 빨강입니다. 빨강이면 jar 위치가 빠졌을 가능성이 큽니다.</li>
					<li><b>최근 분석 작업 · 임베딩 · 올린 문서</b> — 실패한 작업은 이유의 첫 줄이 빨간 글씨로 나옵니다.</li>
				</ol>
				<p>돌고 있는 분석이 있거나 임베딩이 진행 중이면 30초마다 저절로 새로 읽습니다. 바로 보고 싶으면 오른쪽 위 "지금 다시 보기"를 누르세요.</p>
			</details>

			<div id="kn-dash-message" class="kn-message"></div>

			<%-- 프로젝트가 하나도 없을 때만 보이는 시작 안내 --%>
			<section id="kn-dash-start" class="ai-panel kn-dash-start" hidden>
				<h3>처음 오셨나요?</h3>
				<p class="ai-hint">아직 등록한 프로젝트가 없습니다. 아래 순서대로 하면 몇 분 안에 첫 결과를 볼 수 있습니다.</p>
				<div class="kn-steps">
					<a class="kn-step" href="<%=linkBase%>knowledge/project/project#new=1">
						<span class="kn-step-no">1</span><span class="kn-step-title">프로젝트 등록</span>
						<p>프로젝트 ID와 소스 폴더(분석 서버에 있는 경로)를 적고 등록합니다.</p>
					</a>
					<a class="kn-step" href="<%=linkBase%>knowledge/project/project">
						<span class="kn-step-no">2</span><span class="kn-step-title">분석 시작</span>
						<p>프로젝트 화면에서 프로젝트를 고르고 "분석 실행"을 누르면 됩니다.</p>
					</a>
					<a class="kn-step" href="<%=linkBase%>knowledge/guide/guide">
						<span class="kn-step-no">3</span><span class="kn-step-title">사용 안내 읽기</span>
						<p>화면마다 무엇을 물어볼 수 있는지 정리해 두었습니다.</p>
					</a>
				</div>
			</section>

			<%-- 프로젝트마다 한 장: 이름 · 리비전, 계층 흐름 띠, 진입점 · 메소드 · 해석률 --%>
			<section class="kn-dash-projects-wrap">
				<h3 class="kn-dash-title">프로젝트 <span id="kn-dash-project-count" class="kn-dash-count"></span></h3>
				<div id="kn-dash-projects" class="kn-flow-list"></div>
			</section>

			<div class="kn-dash-grid">
				<section class="ai-panel">
					<h3>최근 분석 작업</h3>
					<p class="ai-hint">최근 10건. 돌고 있는 작업이 있으면 30초마다 새로 읽습니다.</p>
					<div id="kn-dash-jobs"></div>
				</section>

				<section class="ai-panel">
					<h3>임베딩 <span id="kn-dash-embed-badge"></span></h3>
					<p class="ai-hint">뜻으로 찾는 검색에 쓰입니다. 분석이 끝난 뒤 서버 안에서 따로 진행됩니다.</p>
					<div id="kn-dash-embed"></div>
				</section>
			</div>

			<section class="ai-panel">
				<h3>올린 문서</h3>
				<p class="ai-hint">설계서 · 매뉴얼처럼 직접 올린 문서입니다. 올리거나 지우는 일은 <a href="<%=linkBase%>knowledge/search/search">검색 · 문서</a> 화면에서 합니다.</p>
				<div id="kn-dash-docs"></div>
			</section>

					</div>
				</div>
			</div>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/embedding.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/dashboard.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeDashboard.init();
	</script>
</body>
</html>
