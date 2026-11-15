<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%--
	코드 분석(Knowledge)의 첫 화면(대시보드).
	서버 상태, 프로젝트별 분석 현황, 최근 분석 작업, 임베딩, 올린 문서를 한눈에 봅니다.
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
					<p class="cs-eyebrow">코드 분석</p>
					<h2>대시보드</h2>
					<p>등록한 프로젝트가 어디까지 분석됐는지, 지금 돌고 있는 작업과 임베딩, 올린 문서를 한 화면에서 봅니다.</p>
				</div>
				<div class="kn-dash-refresh">
					<span id="kn-dash-updated" class="kn-dash-updated"></span>
					<button type="button" id="kn-dash-refresh-btn" class="kn-btn kn-plain">지금 다시 보기</button>
				</div>
			</section>

			<details class="kn-howto">
				<summary>처음이신가요? 이 화면은 이렇게 씁니다 (눌러서 펴기)</summary>
				<p>코드 분석의 첫 화면입니다. 여기서 직접 무언가를 고치지는 않고, 지금 상태를 훑어본 뒤 필요한 화면으로 넘어갑니다.</p>
				<ol>
					<li><b>숫자 카드</b> — 맨 위 카드에서 서버 상태, 프로젝트 수, 돌고 있는 분석, 임베딩 진행률, 올린 문서 수를 봅니다. 카드를 누르면 그 내용을 다루는 화면으로 갑니다.</li>
					<li><b>프로젝트 현황</b> — 프로젝트마다 분석이 끝난 가장 최근 리비전의 파일 · 메소드 · 진입점 수와 호출 해석률이 나옵니다.
						호출 해석률은 95% 이상이면 초록, 80% 미만이면 빨강입니다. 빨강이면 jar 위치가 빠졌을 가능성이 큽니다.
						프로젝트 이름을 누르면 "프로젝트 · 분석" 화면에서 그 리비전의 요약이 바로 열리고, 줄 끝의 "영향도" · "노드 맵"을 누르면 그 리비전이 골라진 채로 열립니다.</li>
					<li><b>최근 분석 작업</b> — 최근 10건입니다. 실패한 작업은 이유의 첫 줄이 빨간 글씨로 나옵니다.</li>
					<li><b>임베딩 · 올린 문서</b> — 뜻으로 찾는 검색을 쓸 준비가 얼마나 됐는지, 묶음별로 문서를 얼마나 올렸는지 봅니다.</li>
				</ol>
				<p>돌고 있는 분석이 있거나 임베딩이 진행 중이면 30초마다 저절로 새로 읽습니다. 바로 보고 싶으면 오른쪽 위 "지금 다시 보기"를 누르세요.</p>
			</details>

			<div id="kn-dash-message" class="kn-message"></div>

			<%-- 숫자 카드: 서버 · 프로젝트 · 진행 중인 분석 · 임베딩 · 올린 문서 --%>
			<div id="kn-dash-cards" class="kn-cards kn-dash-cards"></div>

			<%-- 프로젝트가 하나도 없을 때만 보이는 시작 안내 --%>
			<section id="kn-dash-start" class="ai-panel kn-dash-start" hidden>
				<h3>처음 오셨나요?</h3>
				<p class="ai-hint">아직 등록한 프로젝트가 없습니다. 아래 순서대로 하면 몇 분 안에 첫 결과를 볼 수 있습니다.</p>
				<div class="kn-steps">
					<a class="kn-step" href="<%=linkBase%>knowledge/index">
						<span class="kn-step-no">1</span><span class="kn-step-title">프로젝트 등록</span>
						<p>프로젝트 ID와 소스 폴더(분석 서버에 있는 경로)를 적고 등록합니다.</p>
					</a>
					<a class="kn-step" href="<%=linkBase%>knowledge/index">
						<span class="kn-step-no">2</span><span class="kn-step-title">분석 시작</span>
						<p>아무것도 적지 않고 "분석 시작"을 누르면 됩니다.</p>
					</a>
					<a class="kn-step" href="<%=linkBase%>knowledge/guide/guide">
						<span class="kn-step-no">3</span><span class="kn-step-title">사용 안내 읽기</span>
						<p>화면마다 무엇을 물어볼 수 있는지 정리해 두었습니다.</p>
					</a>
				</div>
			</section>

			<section class="ai-panel">
				<h3>프로젝트 현황</h3>
				<p class="ai-hint">숫자는 분석이 끝난(<code>READY</code>) 가장 최근 리비전의 것입니다. 검색 · 영향도 · 노드 맵도 기본으로 이 리비전을 씁니다.
					프로젝트 이름을 누르면 그 리비전의 요약이 열립니다. 호출 해석률은 95% 이상이면 초록, 80% 미만이면 빨강입니다.</p>
				<div id="kn-dash-projects" class="kn-scroll"></div>
			</section>

			<div class="kn-dash-grid">
				<section class="ai-panel">
					<h3>최근 분석 작업</h3>
					<p class="ai-hint">최근 10건입니다. 돌고 있는 작업이 있으면 이 화면이 30초마다 새로 읽습니다.</p>
					<div id="kn-dash-jobs"></div>
				</section>

				<section class="ai-panel">
					<h3>임베딩 <span id="kn-dash-embed-badge"></span></h3>
					<p class="ai-hint">뜻으로 찾는 검색에 필요한 임베딩은 분석이 끝난 뒤 서버 안에서 따로 진행됩니다.
						자세한 내용은 <a href="<%=linkBase%>knowledge/index">프로젝트 · 분석</a> 화면 위쪽에 있습니다.</p>
					<div id="kn-dash-embed"></div>
				</section>
			</div>

			<section class="ai-panel">
				<h3>올린 문서</h3>
				<p class="ai-hint">설계서 · 매뉴얼처럼 직접 올린 일반 문서를 묶음(collection)별로 셉니다. 로그인한 사용자(API 키)의 문서만 보입니다.
					문서를 올리거나 지우는 일은 <a href="<%=linkBase%>knowledge/search/search">검색 · 문서</a> 화면에서 합니다.</p>
				<div id="kn-dash-docs"></div>
			</section>

			<section class="ai-panel">
				<h3>바로 가기</h3>
				<div class="kn-dash-links">
					<a href="<%=linkBase%>knowledge/search/search"><b>검색 · 문서</b><span>"주문 취소는 어디서 처리하지?"처럼 말로 물어봅니다.</span></a>
					<a href="<%=linkBase%>knowledge/impact/impact"><b>영향도</b><span>테이블 · 메소드를 바꾸면 다시 테스트할 화면과 주소를 찾습니다.</span></a>
					<a href="<%=linkBase%>knowledge/graph/graph"><b>노드 맵</b><span>누가 누구를 부르는지 그림으로 따라갑니다.</span></a>
					<a href="<%=linkBase%>knowledge/diff/diff"><b>리비전 비교</b><span>두 번의 분석 사이에 무엇이 바뀌었는지 봅니다.</span></a>
					<a href="<%=linkBase%>knowledge/guide/guide"><b>사용 안내</b><span>말뜻과 화면별 쓰는 법을 정리했습니다.</span></a>
				</div>
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
