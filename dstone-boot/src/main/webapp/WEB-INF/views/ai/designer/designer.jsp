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

			<%-- 디자이너 작업 영역. [전체 화면]을 누르면 이 영역이 화면 전체를 덮는다(편집 상태는 그대로 이어진다).
				전체 화면일 때만 맨 위 띠가 보이고, 위쪽 패널의 메시지와 상태 글자를 그 띠로 옮겨 온다 --%>
			<section id="wd-workspace" class="ai-panel wd-workspace">
				<div class="wd-full-bar">
					<h3>Workflow 디자이너 <span id="wd-full-title" class="workflow-job-id"></span></h3>
					<span id="wd-full-status-slot" class="wd-full-slot"></span>
					<div id="wd-full-message-slot" class="wd-full-message"></div>
					<div class="wd-full-actions">
						<button type="button" class="kn-plain" data-proxy="wd-layout-btn">자동 배치</button>
						<button type="button" class="kn-plain" data-proxy="wd-undo-btn" title="Ctrl+Z">되돌리기</button>
						<button type="button" data-proxy="wd-check-btn">검사</button>
						<button type="button" data-proxy="wd-save-btn">저장</button>
						<button type="button" class="kn-plain wd-full-exit" data-full-toggle="1" title="Esc">전체 화면 끝내기</button>
					</div>
				</div>
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
							<button type="button" id="wd-full-btn" class="kn-plain wd-full-btn" data-full-toggle="1" title="디자이너를 화면 전체로 넓혀서 봅니다">전체 화면</button>
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

			<%-- Agent 창의 [입력·출력 작성 가이드]. 엔진 규칙(common.expression.PromptText, AgentExecutor)과 같아야 한다.
				JSP가 달러+중괄호를 EL로 읽지 않도록 내용의 달러 기호는 &#36; 로 적는다 --%>
			<div id="wd-guide-dialog" class="wd-dialog wd-guide-dialog" style="display: none;">
				<div class="wd-dialog-box wd-guide-box" role="dialog" aria-labelledby="wd-guide-title">
					<div class="wd-dialog-head wd-guide-head">
						<div>
							<p class="cs-eyebrow">Agent 작성</p>
							<h3 id="wd-guide-title">입력·출력 작성 가이드</h3>
							<p class="wd-guide-sub">Agent가 무엇을 받고, 프롬프트에서 그 값을 어떻게 쓰고, 무엇을 돌려주는지 정리했습니다.</p>
						</div>
						<button type="button" id="wd-guide-close-btn" class="kn-plain">닫기</button>
					</div>
					<nav class="wd-guide-nav">
						<button type="button" data-guide-go="wd-g-flow">한눈에 보기</button>
						<button type="button" data-guide-go="wd-g-in">받는 값</button>
						<button type="button" data-guide-go="wd-g-prompt">프롬프트에서 쓰기</button>
						<button type="button" data-guide-go="wd-g-out">돌려주는 값</button>
						<button type="button" data-guide-go="wd-g-mistake">자주 하는 실수</button>
						<button type="button" data-guide-go="wd-g-example">전체 예시</button>
					</nav>
					<div class="wd-guide-body">

						<section id="wd-g-flow" class="wd-guide-sec">
							<h4><span class="wd-guide-num">1</span>한눈에 보기</h4>
							<div class="wd-guide-rule">프롬프트가 읽을 수 있는 값은 <b>이 Agent가 받은 input뿐</b>입니다. 다른 값이 필요하면 부르는 쪽(Workflow step의 <code>with</code>)이 input에 넣어 줍니다.</div>
							<ol class="wd-flow">
								<li><span class="wd-flow-tag">Workflow</span><b>step의 with</b><small>input 모양에 맞춰 값을 넣음</small></li>
								<li><span class="wd-flow-tag wd-flow-in">input</span><b>모양 검사</b><small>어긋나면 부르기 전에 실패</small></li>
								<li><span class="wd-flow-tag wd-flow-prompt">프롬프트</span><b>&#36;{input.필드} 채우기</b><small>엔진 규칙 뒤에 붙어 시스템 프롬프트가 됨</small></li>
								<li><span class="wd-flow-tag">LLM</span><b>답하기</b><small>input 전체는 사용자 메시지로도 감</small></li>
								<li><span class="wd-flow-tag wd-flow-out">output</span><b>모양 검사</b><small>필드를 적었으면 JSON으로 읽어 검사</small></li>
								<li><span class="wd-flow-tag">Workflow</span><b>step의 save</b><small>&#36;{state.이름.필드}로 다음 step이 읽음</small></li>
							</ol>
						</section>

						<section id="wd-g-in" class="wd-guide-sec">
							<h4><span class="wd-guide-num">2</span>받는 값 (input)</h4>
							<div class="wd-guide-cols">
								<div class="wd-guide-card">
									<h5>적지 않으면 글자 하나</h5>
									<p>채팅 화면에서 친 말이나, step의 <code>with: &#36;{state.draft}</code>처럼 넣은 글자를 그대로 받습니다. 프롬프트에서는 <code>&#36;{input}</code>으로 읽습니다.</p>
								</div>
								<div class="wd-guide-card">
									<h5>필드를 적으면 그 모양만 받음</h5>
									<p>부르는 쪽이 이 모양의 값을 넣어야 합니다. 빠진 필드가 있으면 LLM을 부르기 전에 실패합니다(채팅 API는 400). 이름 뒤 <code>?</code>는 없어도 되는 필드입니다.</p>
								</div>
							</div>
<pre class="wd-guide-code"><span class="c"># Agent 파일 머리말</span>
input:
  inputStr: string, 나눌 글자
  div: string, 구분자
  note?: string, 덧붙일 말 <span class="c">← ? 는 없어도 됨</span></pre>
						</section>

						<section id="wd-g-prompt" class="wd-guide-sec">
							<h4><span class="wd-guide-num">3</span>프롬프트에서 쓰기</h4>
							<p>값을 끼워 넣는 자리는 Workflow YAML과 같은 <code>&#36;{ }</code> 하나뿐입니다. 계산이나 조건은 쓸 수 없습니다. 프롬프트 칸 위의 단추를 누르면 커서 자리에 바로 들어갑니다.</p>
							<table class="wd-guide-table">
								<thead><tr><th>이렇게 쓰면</th><th>이렇게 바뀝니다</th></tr></thead>
								<tbody>
									<tr><td><code>&#36;{input}</code></td><td>받은 값 전체. 필드 묶음이면 JSON 글자</td></tr>
									<tr><td><code>&#36;{input.inputStr}</code></td><td>그 필드의 값</td></tr>
									<tr><td><code>&#36;{input.note:없음}</code></td><td>값이 없으면 콜론 뒤의 기본값. 기본값을 안 적으면 빈 글자</td></tr>
									<tr><td><code>&#36;{input.files[0].path}</code></td><td>목록의 n번째(0부터) 항목 안의 필드</td></tr>
									<tr><td><code>&#36;{output.outArr}</code></td><td>돌려줄 필드의 <b>이름</b> 그대로(<code>outArr</code>). 답은 아직 없으니 "어디에 담을지"를 가리킬 때 씁니다. 이름이 틀리면 검사에서 잡힙니다</td></tr>
									<tr><td><code>&#36;{APP_HOME}</code></td><td>환경 변수(대문자). 서버가 파일을 읽을 때 바꿉니다</td></tr>
									<tr><td><code>\&#36;{param}</code></td><td><code>&#36;{param}</code> 글자 그대로(JSP EL, MyBatis 예시 등)</td></tr>
									<tr><td><code>{"a": 1}</code></td><td>그냥 글자. <code>&#36;</code>가 없는 중괄호는 JSON 예시로 마음껏 써도 됩니다</td></tr>
								</tbody>
							</table>
							<ul class="wd-guide-notes">
								<li>input 전체는 사용자 메시지로도 따로 전달됩니다. 모든 필드를 프롬프트에 끼워 넣을 필요는 없고, 지시문 속에서 가리킬 값만 넣으면 됩니다.</li>
								<li>이름에 공백이나 괄호가 있는 필드(예: <code>2. 현재 상태(As-Is)</code>)는 <code>&#36;{input.…}</code>로 읽을 수 없습니다. 사용자 메시지의 JSON에 들어 있으니 말로 가리키십시오.</li>
								<li>시스템 프롬프트 맨 앞에는 엔진이 정한 공통 규칙이 붙습니다. 프롬프트에는 이 Agent의 일만 적으면 됩니다.</li>
							</ul>
						</section>

						<section id="wd-g-out" class="wd-guide-sec">
							<h4><span class="wd-guide-num">4</span>돌려주는 값 (output)</h4>
							<div class="wd-guide-cols">
								<div class="wd-guide-card">
									<h5>적지 않거나 string이면 글자</h5>
									<p>LLM의 답을 그대로 돌려줍니다. 긴 문서나 대화 답변에 맞습니다. 채팅의 실시간 답(스트리밍)도 이 경우에만 됩니다.</p>
								</div>
								<div class="wd-guide-card">
									<h5>필드를 적으면 JSON + 검사</h5>
									<p>엔진이 "이 모양의 JSON으로만 답하라"는 지시를 알아서 붙이고, 답을 JSON으로 읽어 검사합니다. 그래서 프롬프트에 JSON 모양을 다시 설명하지 않습니다. 필드의 뜻은 필드 설명에 적습니다.</p>
								</div>
							</div>
							<ul class="wd-guide-notes">
								<li>모양이 틀린 답은 실패입니다. <code>execution.onInvalidOutput: RETRY</code>와 <code>maxAttempts</code>를 적으면 그 횟수만큼 다시 묻습니다.</li>
								<li>SUPERVISOR·ROUTER step에 쓰는 Agent는 output을 적지 않습니다. 결과 모양은 엔진이 정합니다(<code>{pass, reason}</code>, <code>{route, reason}</code>).</li>
								<li>ROUTER의 <code>route</code>는 Workflow step의 <code>routes</code> 이름 중 하나로만 받습니다. LLM은 프롬프트에 적힌 경로 이름으로 답하므로, <b>routes 이름이 프롬프트에 그대로 있어야</b> 합니다.
									다르면(예: 화살표를 이을 때 붙는 임시 이름 <code>경로1</code>을 그대로 둠) [검사]와 기동에서 막힙니다. step 설정의 "답 모양"에 이름마다 ✓/✕가 표시됩니다.</li>
								<li>Workflow에서는 step의 <code>save: 이름</code>으로 결과를 저장하고, 다음 step이 <code>&#36;{state.이름.필드}</code>로 읽습니다.</li>
							</ul>
						</section>

						<section id="wd-g-mistake" class="wd-guide-sec">
							<h4><span class="wd-guide-num">5</span>자주 하는 실수</h4>
							<div class="wd-guide-pairs">
								<div class="wd-guide-bad"><span>예전 문법</span><code>{inputStr}을 {div}로 나눈다.</code></div>
								<div class="wd-guide-good"><span>이렇게</span><code>&#36;{input.inputStr}을 &#36;{input.div}로 나눈다.</code></div>

								<div class="wd-guide-bad"><span>output을 값처럼 씀</span><code>결과를 {outArr}에 넣어라.</code></div>
								<div class="wd-guide-good"><span>이렇게</span><code>결과를 &#36;{output.outArr}에 넣어라.</code></div>

								<div class="wd-guide-bad"><span>프롬프트에서 state를 읽음</span><code>초안: &#36;{state.draft}</code></div>
								<div class="wd-guide-good"><span>with로 넘기고 input으로 읽기</span><code>with: {draft: "&#36;{state.draft}"}<br/>초안: &#36;{input.draft}</code></div>

								<div class="wd-guide-bad"><span>JSON 모양을 프롬프트에 설명</span><code>{"outArr": [...]} 모양으로 답하라.</code></div>
								<div class="wd-guide-good"><span>output 필드로 적기</span><code>outArr(array, 나눈 결과): string</code></div>

								<div class="wd-guide-bad"><span>없어도 되는 필드를 그대로</span><code>특히 볼 점: &#36;{input.focus}</code></div>
								<div class="wd-guide-good"><span>기본값을 붙이기</span><code>특히 볼 점: &#36;{input.focus:전반적으로}</code></div>
							</div>
							<p class="wd-guide-foot">위의 세 가지는 [검사]와 기동 때 막히고, 프롬프트 칸 아래에도 바로 표시됩니다. 예전 문법은 [한 번에 바꾸기]로 고칠 수 있습니다. 아래의 두 가지는 막히지는 않지만 답이 흔들리거나 문장이 어색해지기 쉽습니다.</p>
						</section>

						<section id="wd-g-example" class="wd-guide-sec">
							<h4><span class="wd-guide-num">6</span>전체 예시</h4>
							<div class="wd-guide-cols">
								<div>
									<h5 class="wd-guide-caption">Agent 파일 <small>agents/test/split-agent.md</small></h5>
<pre class="wd-guide-code">---
id: split-agent
version: 1.0.0
description: 글자를 구분자로 나눈다
input:
  inputStr: string, 나눌 글자
  div: string, 구분자
output:
  outArr(array, 나눈 결과): string
execution:
  onInvalidOutput: RETRY
  maxAttempts: 3
---
주어진 글자 <span class="k">&#36;{input.inputStr}</span> 를
구분자 <span class="k">&#36;{input.div}</span> 기준으로 나누어
<span class="k">&#36;{output.outArr}</span>에 담아 돌려준다.
빈 조각은 버린다.</pre>
								</div>
								<div>
									<h5 class="wd-guide-caption">Workflow step <small>부르는 쪽</small></h5>
<pre class="wd-guide-code">- id: step01
  type: AGENT
  agent: split-agent
  with:
    inputStr: <span class="k">&#36;{input.text}</span>
    div: "|"
  save: split
  next: step02

<span class="c"># 다음 step에서</span>
<span class="k">&#36;{state.split.outArr}</span></pre>
									<h5 class="wd-guide-caption">LLM이 받는 시스템 프롬프트 <small>input.text가 "바나나|딸기"일 때</small></h5>
<pre class="wd-guide-code"><span class="c">(엔진 공통 규칙)</span>
주어진 글자 바나나|딸기 를
구분자 | 기준으로 나누어
outArr에 담아 돌려준다.
빈 조각은 버린다.</pre>
								</div>
							</div>
						</section>

					</div>
				</div>
			</div>

			<%-- Agent 창 위에 뜨는 고르기 창. Tool이나 Sub Agent를 이름·설명으로 찾아 고른다 --%>
			<div id="wd-pick-dialog" class="wd-dialog wd-pick-dialog" style="display: none;">
				<div class="wd-dialog-box wd-pick-box">
					<div class="wd-dialog-head">
						<h3 id="wd-pick-title"></h3>
						<button type="button" id="wd-pick-close-btn" class="kn-plain">닫기</button>
					</div>
					<div class="wd-pick-bar">
						<input type="search" id="wd-pick-search" placeholder="이름이나 설명으로 찾기" />
						<label class="wd-check"><input type="checkbox" id="wd-pick-only-chosen" /> 고른 것만</label>
						<span id="wd-pick-count" class="ai-hint"></span>
					</div>
					<div id="wd-pick-list" class="wd-pick-list"></div>
					<div class="ai-form-actions">
						<button type="button" id="wd-pick-cancel-btn" class="kn-plain">취소</button>
						<button type="button" id="wd-pick-ok-btn">고르기</button>
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
	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/picoschema-editor.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/workflow-designer.js"></script>
	<script>
		DstoneAiDesigner.init({
			callUrl: "<%=requestUtil.getStrContextPath()%>/ai/designer/call.do"
		});
	</script>
</body>
</html>
