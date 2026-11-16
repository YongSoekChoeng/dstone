<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
	<%-- 소스 / SQL 팝업: 노드 상세의 [소스 보기] [SQL 보기] 버튼으로 연다. 노드 맵과 관계 트리 창이 같이 쓴다(graph-common.js) --%>
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
