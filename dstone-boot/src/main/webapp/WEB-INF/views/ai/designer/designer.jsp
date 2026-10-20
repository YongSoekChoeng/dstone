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
				<p class="cs-eyebrow">AI</p>
				<h2>Workflow 디자이너</h2>
				<p>dstone-ai-engine의 Workflow 정의(conf/definitions/workflows/**.yml)를 그림으로 보고 고칩니다.
					왼쪽 상자에서 step을 끌어다 놓고, step 아래쪽 동그라미를 끌어 다른 step에 놓으면 화살표(다음 step)가 생깁니다.
					step이나 화살표를 누르면 오른쪽에서 설정을 고칠 수 있고, 빈 곳을 누르면 Workflow 전체 설정이 나옵니다.
					[저장]은 엔진이 전부 검사해서 통과했을 때만 파일에 쓰고 바로 반영합니다(엔진을 다시 띄우지 않아도 됩니다).</p>
			</section>

			<section class="ai-panel">
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th><label for="wd-workflow">Workflow</label></th>
						<td><select id="wd-workflow"></select></td>
						<th><label for="wd-path">파일 경로</label></th>
						<td><input type="text" id="wd-path" placeholder="workflows/폴더/이름.yml" />
							<p id="wd-path-hint" class="ai-hint"></p></td>
					</tr>
				</table>
				<div class="ai-form-actions">
					<span id="wd-status" class="workflow-status"></span>
					<button type="button" id="wd-new-btn" class="kn-plain">새 Workflow</button>
					<button type="button" id="wd-copy-btn" class="kn-plain">복사해서 새로</button>
					<button type="button" id="wd-reload-btn" class="kn-plain" title="정의 폴더의 파일을 엔진이 다시 읽습니다(손으로 고친 파일 반영)">엔진 다시 읽기</button>
					<button type="button" id="wd-layout-btn" class="kn-plain">자동 배치</button>
					<button type="button" id="wd-undo-btn" class="kn-plain" title="Ctrl+Z">되돌리기</button>
					<button type="button" id="wd-delete-btn" class="kn-plain">삭제</button>
					<button type="button" id="wd-check-btn">검사</button>
					<button type="button" id="wd-save-btn">저장</button>
				</div>
				<div id="wd-message" class="wd-message"></div>
			</section>

			<section class="ai-panel">
				<div class="wd-wrap">
					<div class="wd-palette">
						<p class="wd-palette-title">step 끌어 놓기</p>
						<div class="wd-palette-item wd-type-AGENT" data-type="AGENT"><b>AGENT</b><span>Agent가 일을 함</span></div>
						<div class="wd-palette-item wd-type-SUPERVISOR" data-type="SUPERVISOR"><b>SUPERVISOR</b><span>통과/불합격 판정</span></div>
						<div class="wd-palette-item wd-type-ROUTER" data-type="ROUTER"><b>ROUTER</b><span>여러 길 중 하나</span></div>
						<div class="wd-palette-item wd-type-TOOL" data-type="TOOL"><b>TOOL</b><span>Tool을 바로 부름</span></div>
						<div class="wd-palette-item wd-type-APPROVAL" data-type="APPROVAL"><b>APPROVAL</b><span>사람이 승인</span></div>
						<p class="wd-palette-title">화살표</p>
						<p class="wd-legend"><span class="wd-legend-line wd-legend-next"></span>다음(next)</p>
						<p class="wd-legend"><span class="wd-legend-line wd-legend-fail"></span>실패(onFailure)</p>
						<p class="wd-legend"><span class="wd-legend-line wd-legend-route"></span>경로(routes)</p>
						<p class="wd-palette-help">Delete: 고른 것 지우기<br />Ctrl+휠: 확대/축소<br />빈 곳 끌기: 화면 이동</p>
					</div>
					<div class="wd-center">
						<div class="wd-tabs">
							<button type="button" class="wd-tab wd-tab-on" data-tab="graph">그림</button>
							<button type="button" class="wd-tab" data-tab="yaml">YAML</button>
							<label class="wd-line-style">선 모양
								<select id="wd-line-style">
									<option value="manhattan">직각 · 노드 피해 가기</option>
									<option value="orth">직각</option>
									<option value="smooth">곡선</option>
									<option value="normal">직선</option>
								</select>
							</label>
							<label class="wd-check wd-hide-fail"><input type="checkbox" id="wd-hide-fail" /> FAIL로 가는 실패 화살표 숨기기</label>
							<span id="wd-yaml-note" class="ai-hint"></span>
						</div>
						<div id="wd-graph-box" class="wd-graph-box">
							<div id="wd-canvas" class="wd-canvas"></div>
						</div>
						<div id="wd-yaml-box" class="wd-yaml-box" style="display: none;">
							<p class="ai-hint">여기서 고친 글자는 [저장] 때 그대로 저장됩니다(주석 포함). 그림에 반영하려면 [그림에 반영]을 누르십시오.
								그림에서 고치면 이 글자는 다시 만들어지고, 맨 위 주석만 남습니다.</p>
							<textarea id="wd-yaml" class="wd-yaml" spellcheck="false"></textarea>
							<div class="ai-form-actions">
								<button type="button" id="wd-yaml-apply-btn">그림에 반영</button>
							</div>
						</div>
					</div>
					<div id="wd-props" class="wd-props"></div>
				</div>
			</section>

			<%-- Agent 파일을 고치는 창. step 설정의 [Agent 파일 열기]나 [새 Agent]로 연다.
				[양식]은 항목별 입력 칸, [텍스트]는 파일 글자 그대로. 저장하는 것은 늘 [텍스트]의 글자다 --%>
			<div id="wd-agent-dialog" class="wd-dialog" style="display: none;">
				<div class="wd-dialog-box wd-agent-box">
					<div class="wd-dialog-head">
						<h3>Agent <span id="wd-agent-title" class="workflow-job-id"></span></h3>
						<button type="button" id="wd-agent-close-btn" class="kn-plain">닫기</button>
					</div>
					<table class="ai-form">
						<colgroup><col class="ai-form-name" /><col /></colgroup>
						<tr>
							<th><label for="wd-agent-path">파일 경로</label></th>
							<td><input type="text" id="wd-agent-path" placeholder="agents/폴더/이름.md" />
								<p id="wd-agent-path-hint" class="ai-hint"></p></td>
						</tr>
					</table>
					<div class="wd-tabs">
						<button type="button" class="wd-tab wd-tab-on" data-agent-tab="form">양식</button>
						<button type="button" class="wd-tab" data-agent-tab="text">텍스트</button>
						<span id="wd-agent-note" class="ai-hint"></span>
					</div>
					<div id="wd-agent-form" class="wd-agent-form"></div>
					<div id="wd-agent-text-box" style="display: none;">
						<p class="ai-hint">파일 글자 그대로입니다(맨 위 --- 와 --- 사이가 설정, 그 아래가 프롬프트). 여기서 고친 글자는 주석까지 그대로 저장되고,
							[양식] 탭으로 돌아가면 양식에도 채워집니다.</p>
						<textarea id="wd-agent-text" class="wd-yaml wd-agent-text" spellcheck="false"></textarea>
					</div>
					<div id="wd-agent-message" class="wd-message"></div>
					<div class="ai-form-actions">
						<button type="button" id="wd-agent-check-btn" class="kn-plain">검사</button>
						<button type="button" id="wd-agent-save-btn">저장</button>
					</div>
				</div>
			</div>
				</div>
			</div>
					</div>
				</div>
			</div>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<%-- 그림 그리기(X6), 자동 배치(dagre), YAML 읽고 쓰기(js-yaml). 폐쇄망에서도 열리도록 CDN 대신 이 프로젝트에 넣어 둔 파일을 쓴다 --%>
	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/lib/x6.min.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/lib/dagre.min.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/lib/js-yaml.min.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/workflow-designer.js"></script>
	<script>
		DstoneAiDesigner.init({
			callUrl: "<%=requestUtil.getStrContextPath()%>/ai/designer/call.do"
		});
	</script>
</body>
</html>
