<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
%>
<!DOCTYPE HTML>
<html>

<jsp:include page="common/head.jsp"></jsp:include>

<body class="ai-body">
	<div id="page-wrapper">

		<jsp:include page="common/header.jsp"></jsp:include>

		<!-- Main -->
		<div id="main">
			<div class="container">
				<div class="row main-row">
					<div class="col-12">
			<section class="cs-page-head">
				<p class="cs-eyebrow">코드 분석</p>
				<h2>프로젝트 · 분석</h2>
				<p>Java 프로젝트를 등록하고 분석을 돌린 뒤, 리비전별 분석 결과와 임베딩 진행 상황을 봅니다.</p>
			</section>

			<details class="kn-howto">
				<summary>처음이신가요? 이 화면은 이렇게 씁니다 (눌러서 펴기)</summary>
				<p>이 화면에서는 분석할 프로젝트를 등록하고, 분석을 돌리고, 결과를 믿고 써도 되는지 확인합니다. 위에서부터 차례로 따라오면 됩니다.</p>
				<ol>
					<li><b>임베딩 현황 (맨 위)</b> — 뜻으로 찾는 검색을 쓸 준비가 얼마나 됐는지 보여 줍니다. 분석과 따로 돌아가므로 처음에는 그냥 지나쳐도 됩니다.</li>
					<li><b>프로젝트 등록</b> — "프로젝트 등록 / 수정" 칸에 projectId(영문), 이름, 소스 폴더를 적고 "등록"을 누릅니다.
						소스 폴더는 내 PC가 아니라 <b>dstone-knowledge 서버에서 보이는 경로</b>입니다. 이미 표에 있는 프로젝트를 쓴다면 이 단계는 건너뜁니다.</li>
					<li><b>프로젝트 고르기</b> — 프로젝트 표에서 <b>projectId(파란 글자)</b>를 누릅니다. 아래에 "분석" 칸이 열리고, 등록 칸에도 그 프로젝트의 값이 채워집니다.
						경로나 jar 위치를 고친 뒤 "등록"을 다시 누르면 수정됩니다.</li>
					<li><b>분석 시작</b> — 칸은 비워 둔 채 "분석 시작"만 누르면 됩니다. 두 번째부터는 "증분 분석"을 체크하세요. 바뀐 파일만 다시 읽어서 훨씬 빨리 끝납니다.
						단계별 진행 상황이 3초마다 새로 보이고, 멈추고 싶으면 "취소"를 누릅니다.</li>
					<li><b>결과 확인</b> — 리비전 표에서 상태가 <code>READY</code>인 줄의 "요약"을 누릅니다. 맨 위 <b>"한눈에 보기"</b>가 모두 초록 ✔이면 그대로 쓰면 됩니다.
						주황 <b>!</b>나 빨간 <b>✖</b>가 있으면 그 줄에 무엇을 하면 되는지 적혀 있습니다.</li>
					<li><b>모자란 곳 살펴보기 (필요할 때만)</b> — 요약 안의 "적용 진단"은 덜 이어진 곳을 해결 방법별(규칙 파일, jar 위치 등)로 나눠 보여 줍니다.
						"풀지 못한 참조 살펴보기"를 누르면 무엇을 못 찾았는지 파일 · 줄 번호까지 나옵니다.</li>
					<li><b>물어보기</b> — 왼쪽 메뉴의 "검색 · 문서", "영향도", "노드 맵", "리비전 비교"로 갑니다. 이 화면들은 분석이 끝난 가장 최근 리비전을 알아서 골라 둡니다.</li>
				</ol>
				<p>리비전은 프로젝트마다 최근 5개까지만 남습니다. 분석이 끝날 때마다 오래된 것부터 지워지고, 더 줄이고 싶으면 리비전 표 아래의 "보관 정책 적용"을 씁니다.</p>
				<p>먼저 연습해 보고 싶다면 저장소에 든 게시판 표본을 등록해 보세요. projectId는 <code>struts-app</code>, 소스 폴더는
					<code>dstone-knowledge/src/test/resources/samples/struts-app</code>(이 개발 서버에서는 <code>/app/dstone/dstone-knowledge/src/test/resources/samples/struts-app</code>)입니다.
					파일 11개짜리라 몇 초면 끝나고, 다른 화면의 예시도 이 표본을 기준으로 적었습니다. 일부러 jar 없이 넣어 둔 표본이라 "한눈에 보기"에 빨간 줄이 나와도 정상입니다.</p>
				말뜻과 화면별 설명은 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/guide/guide">사용 안내</a>에 있습니다.
			</details>

			<section class="ai-panel">
				<h3>임베딩 현황 <span id="kn-embed-badge"></span>
					<button type="button" id="kn-embed-refresh" class="kn-btn kn-plain kn-embed-refresh">지금 다시 보기</button></h3>
				<p class="ai-hint">뜻으로 찾는 검색에 필요한 임베딩은 분석이 끝난 뒤 서버 안에서 따로 진행됩니다. 여기서는 프로젝트를 통틀어 얼마나 남았는지 봅니다.
					dstone-knowledge 서버가 떠 있는 동안에만 진행되고, 서버를 내렸다 올려도 남은 것부터 이어서 합니다.
					예상 남은 시간은 최근 10분의 속도로 어림한 값입니다(검색이 끼어들면 그동안 양보해서 더 걸립니다).</p>
				<div id="kn-embed-message" class="kn-message"></div>
				<div id="kn-embed-bar" class="kn-embed-bar" style="display:none;"><div></div></div>
				<div id="kn-embed-cards" class="kn-cards"></div>
				<div id="kn-embed-error" class="kn-message kn-error" style="display:none;"></div>
				<div id="kn-embed-projects"></div>
			</section>

			<section class="ai-panel">
				<h3>프로젝트 <span id="kn-health"></span></h3>
				<p class="ai-hint">dstone-knowledge에 등록한 Java 프로젝트입니다. 프로젝트를 고르면 아래에서 분석을 시작하고 결과를 볼 수 있습니다.
					소스는 dstone-knowledge 서버가 직접 읽을 수 있는 폴더여야 합니다.</p>
				<div id="kn-project-table" class="kn-scroll"></div>

				<div class="kn-section-title">프로젝트 등록 / 수정</div>
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th><label for="kn-new-project-id">projectId</label></th>
						<td><input type="text" id="kn-new-project-id" placeholder="영문" /></td>
						<th><label for="kn-new-project-name">이름</label></th>
						<td><input type="text" id="kn-new-project-name" placeholder="화면에 보일 이름" /></td>
					</tr>
					<tr>
						<th><label for="kn-new-project-path">소스 폴더</label></th>
						<td colspan="3"><input type="text" id="kn-new-project-path" placeholder="서버의 경로. 예: /app/sampleApps/anybiz_prd" /></td>
					</tr>
					<tr>
						<th><label for="kn-new-project-classpath">라이브러리 위치</label></th>
						<td colspan="3"><input type="text" id="kn-new-project-classpath" placeholder="(선택) jar 가 소스 폴더 밖에 있을 때만. 파일이나 폴더를 쉼표로 구분" /></td>
					</tr>
				</table>
				<div class="ai-form-actions">
					<button type="button" id="kn-project-save-btn">등록</button>
				</div>
				<p class="ai-hint">같은 projectId 로 다시 등록하면 수정입니다. 소스 폴더 안의 jar 는 알아서 찾으므로, 라이브러리 위치는 jar 가 다른 곳에 있을 때만 적습니다.
					jar 가 없어도 분석은 되지만 "누가 누구를 부르는지"가 덜 정확해집니다.</p>
				<div id="kn-project-message" class="kn-message"></div>
			</section>

			<section class="ai-panel" id="kn-analysis-panel" style="display:none;">
				<h3>분석 <span id="kn-selected-project" class="workflow-job-id"></span></h3>
				<p class="ai-hint">리비전은 "그 시점의 분석 결과 전체"입니다. 라벨을 비우면 지금 시각으로 새 리비전을 만듭니다.
					이미 있는 라벨을 적으면 그 리비전을 이어서 분석합니다(끝난 단계는 건너뜁니다).</p>
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th><label for="kn-revision-label">리비전 라벨</label></th>
						<td><input type="text" id="kn-revision-label" placeholder="비우면 지금 시각" /></td>
						<th><label for="kn-rerun-from">이 단계부터 다시</label></th>
						<td><select id="kn-rerun-from">
								<option value="">(쓰지 않음)</option>
								<option>SCAN</option>
								<option>DECLARE</option>
								<option>RESOURCE</option>
								<option>RESOLVE</option>
								<option>LINK</option>
								<option>SEMANTIC</option>
								<option>DOCUMENT</option>
							</select></td>
						<th>증분 분석</th>
						<td class="ai-form-checks"><label><input type="checkbox" id="kn-incremental" /> 바뀐 파일만 다시 분석</label></td>
					</tr>
				</table>
				<div class="ai-form-actions">
					<button type="button" id="kn-analysis-start-btn">분석 시작</button>
				</div>
				<div id="kn-analysis-message" class="kn-message"></div>

				<div id="kn-job-box" style="display:none;">
					<div class="workflow-status-row">
						<span class="workflow-status-label">Job</span>
						<span id="kn-job-id" class="workflow-job-id">-</span>
						<span id="kn-job-badge"></span>
						<span id="kn-job-progress" class="workflow-status-label"></span>
						<button type="button" id="kn-job-cancel-btn">취소</button>
					</div>
					<div id="kn-job-passes" class="kn-scroll"></div>
					<div id="kn-job-errors"></div>
				</div>

				<div class="kn-section-title">리비전</div>
				<div id="kn-revision-table" class="kn-scroll"></div>
				<div class="kn-row">
					<label for="kn-retention-keep">최근</label>
					<input type="number" id="kn-retention-keep" min="1" value="5" style="width:70px;" />
					<label>개만 남기고 지우기</label>
					<button type="button" id="kn-retention-btn" class="kn-plain">보관 정책 적용</button>
				</div>
				<div id="kn-revision-message" class="kn-message"></div>
			</section>

			<section class="ai-panel" id="kn-summary-panel" style="display:none;">
				<h3>리비전 요약 <span id="kn-summary-title" class="workflow-job-id"></span></h3>
				<p class="ai-hint">분석 결과가 얼마나 만들어졌고 얼마나 믿을 만한지 봅니다. 숫자를 다 읽을 필요는 없습니다.
					<b>"한눈에 보기"</b>만 봐도 이 리비전을 써도 되는지 알 수 있고, 더 알고 싶을 때 아래 표를 봅니다.
					표의 영문 값 옆에는 뜻이 붙어 있고, 표마다 <b>"이 표 읽는 법"</b>을 누르면 각 열의 뜻과 숫자를 어떻게 해석하는지 나옵니다.
					모든 항목의 설명은 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=knowledge/guide/guide#summary">사용 안내의 "리비전 요약 읽는 법"</a>에 모아 두었습니다.</p>
				<div id="kn-summary-cards" class="kn-cards"></div>
				<div id="kn-summary-sections"></div>
			</section>

			<section class="ai-panel" id="kn-unresolved-panel" style="display:none;">
				<h3>풀지 못한 참조 살펴보기 <span id="kn-unresolved-title" class="workflow-job-id"></span></h3>
				<p class="ai-hint">분석률을 높이려면 어디부터 봐야 하는지 찾는 곳입니다. 먼저 <b>원인별</b> 표를 봅니다.
					같은 기호(예: <code>Model</code>, <code>logger</code>, <code>HttpEntity</code>)가 많이 나오면 그 타입이 든 라이브러리(jar)가 빠진 것입니다.
					프로젝트 등록 칸에 jar 위치를 적고 다시 분석하면 한꺼번에 풀립니다. 표의 줄을 누르면 그 조건으로 좁혀지고, 아래 목록에 파일 · 줄 번호가 나옵니다.</p>
				<div class="kn-row">
					<label for="kn-unresolved-status">상태</label>
					<select id="kn-unresolved-status">
						<option value="UNRESOLVED">UNRESOLVED (못 풂)</option>
						<option value="HEURISTIC">HEURISTIC (이름으로 짐작해 이음)</option>
						<option value="IGNORED">IGNORED (일부러 뺌)</option>
					</select>
					<label for="kn-unresolved-kind">참조의 종류</label>
					<select id="kn-unresolved-kind">
						<option value="CALL">CALL (메소드 호출)</option>
						<option value="">모든 종류</option>
						<option value="FIELD_ACCESS">FIELD_ACCESS (필드 읽기 / 쓰기)</option>
						<option value="TYPE_USE">TYPE_USE (타입 사용)</option>
						<option value="CREATE">CREATE (new)</option>
						<option value="METHOD_REF">METHOD_REF (메소드 참조)</option>
					</select>
					<label for="kn-unresolved-path">파일 경로</label>
					<input type="text" id="kn-unresolved-path" class="kn-wide" placeholder="경로에 들어간 글자 (예: integration/)" />
					<button type="button" id="kn-unresolved-search-btn">조회</button>
				</div>
				<div id="kn-unresolved-filter" class="kn-unresolved-filter"></div>
				<div id="kn-unresolved-message" class="kn-message"></div>
				<div class="kn-unresolved-groups">
					<div>
						<div class="kn-section-title">원인별 (못 찾은 기호)</div>
						<div id="kn-unresolved-by-cause" class="kn-scroll"></div>
					</div>
					<div>
						<div class="kn-section-title">파일별</div>
						<div id="kn-unresolved-by-file" class="kn-scroll"></div>
					</div>
					<div>
						<div class="kn-section-title">부르려던 이름별</div>
						<div id="kn-unresolved-by-name" class="kn-scroll"></div>
					</div>
				</div>
				<div class="kn-section-title">한 건씩 <span id="kn-unresolved-count" class="workflow-status-label"></span></div>
				<div id="kn-unresolved-list" class="kn-scroll"></div>
				<div class="kn-row">
					<button type="button" id="kn-unresolved-prev-btn" class="kn-plain">이전</button>
					<span id="kn-unresolved-page" class="workflow-status-label"></span>
					<button type="button" id="kn-unresolved-next-btn" class="kn-plain">다음</button>
				</div>
			</section>
					</div>
				</div>
			</div>
		</div>

		<jsp:include page="common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-terms.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/embedding.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/unresolved.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/project.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeProject.init();
		DstoneKnowledgeUnresolved.init();
		DstoneKnowledgeEmbedding.init();
	</script>
</body>
</html>
