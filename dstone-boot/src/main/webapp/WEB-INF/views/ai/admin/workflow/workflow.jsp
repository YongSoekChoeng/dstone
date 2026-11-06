<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
// 로그인 사용자. 승인/반려한 사람은 이 값으로 남는다(엔진에도 이 사용자를 헤더로 넘긴다 - ai.service.AiEngineUser).
String loginUser = request.getRemoteUser() == null ? "" : request.getRemoteUser();
%>
<!DOCTYPE HTML>
<html>

<jsp:include page="../../common/head.jsp"></jsp:include>
<%-- step 문서 팝업: Markdown 모양(GitHub 스타일)과 코드 색칠. 라이브러리는 폐쇄망에서도 열리도록 앱 안에 들어 있다 --%>
<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/ai/assets/lib/github-markdown-light.css" />
<link rel="stylesheet" href="<%=requestUtil.getStrContextPath()%>/knowledge/assets/lib/highlight-github.min.css" />

<body class="ai-body">
	<div id="page-wrapper">

		<jsp:include page="../../common/header.jsp"></jsp:include>

		<!-- Main -->
		<div id="main">
			<div class="container">
				<div class="row main-row">
					<div class="col-12">
			<section class="cs-page-head">
				<p class="cs-eyebrow">AI</p>
				<h2>Workflow 실행 관리</h2>
				<p>이미 시작된 Workflow 실행을 조회하고, 승인 대기 중인 실행을 승인/반려합니다. 실행 중에 문제가 보이면 일시정지하고, 해결한 뒤 멈춘 step부터 이어 갑니다.
					step이 작성한 md 문서는 스텝 이력의 '문서' 칸이나 아래 'step 문서' 표에서 열어 보고, 승인하기 전에 고칠 수 있습니다.
					엔진이 동시에 돌리는 실행 수에는 한도가 있어서, 사람이 많으면 새 실행은 QUEUED(대기)로 차례를 기다립니다.
					일시정지·재개·되감기·문서 저장은 실행을 시작한 사람과 관리자만 할 수 있습니다.
					임의 workflowId를 직접 실행해보려면 <a href="<%=requestUtil.getStrContextPath()%>/defaultLink.do?defaultLink=ai/workflow/workflow">Workflow 테스트</a> 화면을 쓰십시오.</p>
			</section>

			<section class="ai-panel">
				<h3>조회 조건</h3>
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th><label for="workflow-admin-status-filter">상태</label></th>
						<td><select id="workflow-admin-status-filter">
								<option value="">전체</option>
								<option value="WAITING_APPROVAL">WAITING_APPROVAL</option>
								<option value="QUEUED">QUEUED</option>
								<option value="RUNNING">RUNNING</option>
								<option value="PAUSED">PAUSED</option>
								<option value="DONE">DONE</option>
								<option value="FAILED">FAILED</option>
								<option value="CANCELLED">CANCELLED</option>
							</select></td>
						<th><label for="workflow-admin-workflow-filter">workflowId</label></th>
						<td><input type="text" id="workflow-admin-workflow-filter" placeholder="일부만 적어도 찾습니다" /></td>
					</tr>
					<tr>
						<th><label for="workflow-admin-execution-filter">executionId</label></th>
						<td><input type="text" id="workflow-admin-execution-filter" placeholder="일부만 적어도 찾습니다" /></td>
						<th><label for="workflow-admin-mine-filter">시작한 사람</label></th>
						<td><label class="wa-check"><input type="checkbox" id="workflow-admin-mine-filter" checked /> 내 실행만</label></td>
					</tr>
				</table>
				<div class="ai-form-actions">
					<button type="button" id="workflow-admin-reset-btn" class="kn-plain">초기화</button>
					<button type="button" id="workflow-admin-refresh-btn">조회</button>
				</div>
			</section>

			<section class="ai-panel">
				<h3>실행 목록</h3>
				<div class="wa-table-scroll">
					<table id="workflow-admin-list-table" class="document-list-table wa-table">
						<colgroup>
							<col />
							<col style="width:22%" />
							<col style="width:110px" />
							<col style="width:150px" />
							<col style="width:84px" />
							<col style="width:160px" />
						</colgroup>
						<thead>
							<tr>
								<th>executionId</th>
								<th>workflowId</th>
								<th>시작한 사람</th>
								<th>상태</th>
								<th class="wa-num">현재 스텝</th>
								<th>수정시각</th>
							</tr>
						</thead>
						<tbody id="workflow-admin-list-body"></tbody>
					</table>
				</div>
				<div class="wa-pager">
					<span id="workflow-admin-total" class="wa-pager-total"></span>
					<div id="workflow-admin-pager" class="wa-pager-buttons"></div>
				</div>
			</section>

			<section class="ai-panel" id="workflow-admin-detail-panel" style="display:none;">
				<h3>실행 상세</h3>
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th>executionId</th>
						<td><span id="workflow-admin-detail-id" class="workflow-job-id">-</span></td>
						<th>상태</th>
						<td><span id="workflow-admin-detail-badge" class="workflow-badge">-</span></td>
					</tr>
					<tr>
						<th>시작한 사람</th>
						<td colspan="3"><span id="workflow-admin-detail-requester">-</span></td>
					</tr>
					<tr>
						<th><label for="workflow-admin-detail-result">결과 / 실패사유</label></th>
						<td colspan="3"><textarea id="workflow-admin-detail-result" class="workflow-textarea" readonly></textarea></td>
					</tr>
					<tr>
						<th><label for="workflow-admin-detail-context">context</label></th>
						<td colspan="3"><textarea id="workflow-admin-detail-context" class="workflow-textarea workflow-textarea-small" readonly></textarea></td>
					</tr>
				</table>

				<div id="workflow-admin-control" class="wa-control" style="display:none;">
					<div class="wa-control-text">
						<div id="workflow-admin-control-message"></div>
						<ul id="workflow-admin-control-warnings" class="wa-control-warnings"></ul>
					</div>
					<div class="wa-control-buttons">
						<span id="workflow-admin-control-status" class="workflow-status"></span>
						<button type="button" id="workflow-admin-pause-btn">일시정지</button>
						<button type="button" id="workflow-admin-resume-btn">재개</button>
					</div>
				</div>

				<%-- 스텝 이력의 [여기서 다시]를 누르면 나온다. 무엇이 남고 무엇을 지울지 보여 주고 확인을 받는다 --%>
				<div id="workflow-admin-rewind" class="wa-control wa-rewind" style="display:none;">
					<div class="wa-control-text">
						<div id="workflow-admin-rewind-message"></div>
						<ul id="workflow-admin-rewind-warnings" class="wa-control-warnings"></ul>
						<%-- 구간의 step이 새로 만든 파일(재실행할 때 지운다)과 원래 있던 파일을 고친 것(그대로 둔다) --%>
						<details id="workflow-admin-rewind-files" class="wa-rewind-files" style="display:none;">
							<summary></summary>
							<ul></ul>
						</details>
						<details id="workflow-admin-rewind-kept" class="wa-rewind-files" style="display:none;">
							<summary></summary>
							<ul></ul>
						</details>
						<label id="workflow-admin-rewind-clear-label" class="wa-rewind-clear" style="display:none;">
							<input type="checkbox" id="workflow-admin-rewind-clear" /> <span></span>
						</label>
					</div>
					<div class="wa-control-buttons">
						<span id="workflow-admin-rewind-status" class="workflow-status"></span>
						<button type="button" id="workflow-admin-rewind-run-btn">다시 실행</button>
						<button type="button" id="workflow-admin-rewind-cancel-btn" class="kn-plain">취소</button>
					</div>
				</div>

				<div class="kn-section-title">스텝 이력</div>
				<div class="wa-table-scroll">
					<table id="workflow-admin-history-table" class="document-list-table wa-table wa-history-table">
						<thead>
							<tr>
								<th class="wa-fit">stepId</th>
								<th class="wa-fit">type</th>
								<th class="wa-fit">ref</th>
								<th class="wa-fit">결과</th>
								<th class="wa-fit wa-num">소요(ms)</th>
								<th>내용/사유</th>
								<th class="wa-doc-col">문서</th>
								<th class="wa-fit">시각</th>
								<th class="wa-fit">재실행</th>
							</tr>
						</thead>
						<tbody id="workflow-admin-history-body"></tbody>
					</table>
				</div>

				<div class="kn-section-title">step 문서
					<span id="workflow-admin-docs-count" class="ai-hint"></span>
					<label class="wa-docs-all"><input type="checkbox" id="workflow-admin-docs-all" /> 모든 파일 보기</label>
				</div>
				<div class="wa-table-scroll">
					<table id="workflow-admin-docs-table" class="document-list-table wa-table">
						<colgroup>
							<col />
							<col style="width:22%" />
							<col style="width:84px" />
							<col style="width:160px" />
							<col style="width:96px" />
						</colgroup>
						<thead>
							<tr>
								<th>파일</th>
								<th>step</th>
								<th class="wa-num">크기</th>
								<th>수정시각</th>
								<th></th>
							</tr>
						</thead>
						<tbody id="workflow-admin-docs-body"></tbody>
					</table>
				</div>

				<div id="workflow-admin-decision-form" style="display:none;">
					<div class="kn-section-title">승인/반려 <span id="workflow-admin-decision-step" class="ai-hint"></span></div>
					<table class="ai-form">
						<colgroup><col class="ai-form-name" /><col /></colgroup>
						<tr>
							<th><label for="workflow-admin-approver">approver</label></th>
							<%-- 로그인했으면 결정한 사람은 로그인 사용자로 남는다(바꿀 수 없다). 로그인하지 않은 환경에서만 직접 적는다 --%>
							<td><input type="text" id="workflow-admin-approver" placeholder="결정하는 사람/역할" value="<%=org.springframework.web.util.HtmlUtils.htmlEscape(loginUser)%>" <%=loginUser.isEmpty() ? "" : "readonly"%> /></td>
						</tr>
						<tr>
							<th><label for="workflow-admin-comment">comment</label></th>
							<td><textarea id="workflow-admin-comment" class="workflow-textarea workflow-textarea-small" placeholder="(선택)"></textarea></td>
						</tr>
					</table>
					<div class="ai-form-actions">
						<span id="workflow-admin-decision-status" class="workflow-status"></span>
						<%-- 승인/반려 방식 APPROVAL일 때 보이는 버튼 --%>
						<button type="button" id="workflow-admin-approve-btn">승인</button>
						<button type="button" id="workflow-admin-reject-btn" class="kn-plain">반려</button>
						<%-- 선택지 방식 APPROVAL(routes)일 때는 선택지 이름마다 버튼이 여기에 하나씩 만들어진다 --%>
						<span id="workflow-admin-route-buttons" class="ai-form-buttons"></span>
					</div>
				</div>
			</section>
					</div>
				</div>
			</div>
		</div>

		<jsp:include page="../../common/footer.jsp"></jsp:include>

	</div>

	<%-- step 문서 팝업. [보기]는 Markdown을 HTML로, [수정]은 원문과 미리보기를 나란히 보여 준다(workflow-doc-viewer.js) --%>
	<div id="wa-doc-modal" class="wa-doc-modal" hidden>
		<div class="wa-doc-box" role="dialog" aria-modal="true" aria-labelledby="wa-doc-title">
			<div class="wa-doc-head">
				<div class="wa-doc-titles">
					<div id="wa-doc-title" class="wa-doc-title"></div>
					<div id="wa-doc-path" class="wa-doc-path"></div>
				</div>
				<div class="wa-doc-tabs" role="tablist">
					<button type="button" id="wa-doc-tab-view" class="on" role="tab">보기</button>
					<button type="button" id="wa-doc-tab-edit" role="tab">수정</button>
				</div>
				<div class="wa-doc-head-buttons">
					<button type="button" id="wa-doc-full" class="kn-plain">전체 화면</button>
					<button type="button" id="wa-doc-close" class="kn-plain">닫기</button>
				</div>
			</div>
			<div id="wa-doc-notice" class="wa-doc-notice" hidden></div>
			<div class="wa-doc-body">
				<div id="wa-doc-view" class="wa-doc-view markdown-body"></div>
				<div id="wa-doc-edit" class="wa-doc-edit" hidden>
					<textarea id="wa-doc-text" class="wa-doc-text" spellcheck="false" aria-label="문서 원문"></textarea>
					<div id="wa-doc-preview" class="wa-doc-preview markdown-body" aria-label="미리보기"></div>
				</div>
			</div>
			<div class="wa-doc-foot">
				<span id="wa-doc-meta" class="wa-doc-meta"></span>
				<span id="wa-doc-status" class="wa-doc-status"></span>
				<button type="button" id="wa-doc-reload" class="kn-plain">다시 읽기</button>
				<button type="button" id="wa-doc-save" hidden>저장 (Ctrl+S)</button>
			</div>
		</div>
	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/lib/marked.min.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/lib/purify.min.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/lib/highlight.min.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/workflow-doc-viewer.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/workflow-admin.js"></script>
	<script>
		DstoneAiWorkflowAdmin.init({
			listUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/list.do",
			detailUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/detail.do",
			decisionUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/decision.do",
			pauseUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/pause.do",
			resumeUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/resume.do",
			rewindPointUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/rewindPoint.do",
			rewindUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/rewind.do",
			filesUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/files.do",
			fileContentUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/fileContent.do",
			saveFileUrl: "<%=requestUtil.getStrContextPath()%>/ai/admin/workflow/saveFile.do"
		});
	</script>
</body>
</html>
