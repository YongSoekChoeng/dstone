<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%--
	"프로젝트" 화면. 분석한 애플리케이션의 현행 구조를 보는 곳입니다(차세대 전환의 ASIS 분석, SM 의 영향도 출발점).
	한 화면에 세 가지 모습이 있고, 주소의 # 로 나뉩니다(project-home.js).
	  (없음)          프로젝트 목록
	  #p=ID&r=리비전   프로젝트 홈: 신뢰도 한 줄 + 탭(현행 구조 ...)
	  #new / #p=ID&edit=1   프로젝트 등록 / 설정(DB 접속 정보 포함)
	분석을 세밀하게 돌리거나 연결 품질 · 실행 기록을 보는 일은 "분석 실행 · 진단"(knowledge/index) 화면에서 합니다.
--%>
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
			<section class="cs-page-head kn-proj-head">
				<div>
					<h2 id="kn-proj-title">프로젝트</h2>
					<p id="kn-proj-sub">분석한 애플리케이션마다 화면에서 테이블까지 어떻게 이어져 있는지, 업무 영역별로 얼마나 큰지 봅니다.</p>
				</div>
				<div class="kn-proj-actions">
					<button type="button" id="kn-proj-new" class="kn-btn">프로젝트 등록</button>
					<button type="button" id="kn-proj-run" class="kn-btn kn-plain" hidden>분석 실행</button>
					<button type="button" id="kn-proj-edit" class="kn-btn kn-plain" hidden>프로젝트 설정</button>
				</div>
			</section>

			<div id="kn-proj-message" class="kn-message"></div>

			<%-- 프로젝트 목록 --%>
			<section id="kn-proj-list-view">
				<div id="kn-proj-list" class="kn-proj-list"></div>
			</section>

			<%-- 프로젝트 홈 --%>
			<section id="kn-proj-home" hidden>
				<div class="kn-proj-bar">
					<a href="#" id="kn-proj-back" class="kn-proj-back">프로젝트 목록</a>
					<label class="kn-proj-rev">리비전 <select id="kn-proj-revision"></select></label>
				</div>
				<div id="kn-proj-trust" class="kn-proj-trust"></div>

				<div class="kn-tabs" id="kn-proj-tabs" role="tablist">
					<button type="button" class="kn-tab kn-tab-on" data-tab="structure" role="tab">현행 구조</button>
				</div>

				<div class="kn-tab-panel" data-panel="structure">
					<div id="kn-proj-empty" class="kn-proj-empty" hidden></div>
					<div id="kn-proj-structure">
						<div id="kn-proj-strip"></div>
						<div id="kn-proj-kinds" class="kn-proj-kinds"></div>
						<section class="ai-panel kn-proj-areas-panel">
							<div class="kn-proj-sectionhead">
								<h3>업무 영역별 규모</h3>
								<button type="button" id="kn-proj-area-csv" class="kn-btn kn-plain">CSV 내려받기</button>
							</div>
							<p class="ai-hint" id="kn-proj-area-note"></p>
							<div id="kn-proj-areas" class="kn-scroll kn-proj-areas"></div>
						</section>
					</div>
				</div>
			</section>

			<%-- 프로젝트 등록 / 설정 --%>
			<section id="kn-proj-form-view" class="ai-panel kn-proj-form" hidden>
				<h3 id="kn-proj-form-title">프로젝트 등록</h3>
				<div class="kn-proj-grid">
					<div class="kn-field"><label for="kn-pf-id">프로젝트 ID</label><input type="text" id="kn-pf-id" placeholder="영문 · 숫자 (예: order-system)" /></div>
					<div class="kn-field"><label for="kn-pf-name">이름</label><input type="text" id="kn-pf-name" placeholder="화면에 보일 이름" /></div>
					<div class="kn-field kn-proj-wide"><label for="kn-pf-path">소스 폴더</label><input type="text" id="kn-pf-path" placeholder="분석 서버에서 보이는 경로. 예: /app/sampleApps/anybiz_prd" /></div>
					<div class="kn-field kn-proj-wide"><label for="kn-pf-classpath">라이브러리 위치 (선택)</label><input type="text" id="kn-pf-classpath" placeholder="jar 가 소스 폴더 밖에 있을 때만. 쉼표로 구분" /></div>
					<div class="kn-field kn-proj-wide"><label for="kn-pf-desc">설명 (선택)</label><input type="text" id="kn-pf-desc" placeholder="예: 인사 · 급여 시스템 (차세대 대상)" /></div>
				</div>

				<h4 class="kn-proj-subtitle">DB 접속 <small>선택 · 여러 개 가능</small></h4>
				<p class="ai-hint">테이블 · 컬럼의 정의와 한글명을 소스보다 먼저 여기서 찾습니다. 위에서부터 순서대로 찾고, <code>스키마.테이블</code>처럼 스키마가 붙은 이름은 그 스키마를 적은 접속부터 봅니다.
					비밀번호는 암호화해 저장하고 다시 보여 주지 않습니다. 메타데이터만 읽으므로 <b>읽기 전용 계정</b>을 권합니다.</p>
				<div id="kn-pf-dbs" class="kn-proj-dbs"></div>
				<button type="button" id="kn-pf-db-add" class="kn-btn kn-plain">DB 접속 추가</button>

				<div id="kn-pf-message" class="kn-message"></div>
				<div class="ai-form-actions kn-proj-form-actions">
					<button type="button" id="kn-pf-cancel" class="kn-btn kn-plain">취소</button>
					<button type="button" id="kn-pf-save" class="kn-btn">저장</button>
				</div>
			</section>
					</div>
				</div>
			</div>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/project-home.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeProjectHome.init();
	</script>
</body>
</html>
