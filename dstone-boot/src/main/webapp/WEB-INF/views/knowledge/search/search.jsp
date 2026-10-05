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
				<h3>검색</h3>
				<p class="ai-hint">분석 결과(메소드, 타입, SQL, 화면)와 올린 문서를 찾습니다. 뜻으로 찾고("주문 취소는 어디서 처리하나"),
					질문에 들어 있는 클래스 · 메소드 · 테이블 이름이나 주소로도 찾아 두 결과를 합칩니다.
					프로젝트를 고르지 않으면 올린 문서에서만 찾습니다.</p>
				<div class="kn-row">
					<label for="kn-search-project">프로젝트</label>
					<select id="kn-search-project"></select>
					<input type="text" id="kn-search-query" class="kn-wide" placeholder="찾을 내용. 예: 주문 취소는 어디서 처리하나 / OrderService.cancel / TB_ORDER" />
					<button type="button" id="kn-search-btn">찾기</button>
				</div>
				<div class="kn-row">
					<label for="kn-search-mode">방법</label>
					<select id="kn-search-mode">
						<option value="HYBRID">뜻 + 이름 (HYBRID)</option>
						<option value="VECTOR">뜻만 (VECTOR)</option>
						<option value="KEYWORD">이름만 (KEYWORD)</option>
					</select>
					<label><input type="checkbox" class="kn-search-source" value="CODE" checked /> 코드</label>
					<label><input type="checkbox" class="kn-search-source" value="DOCUMENT" /> 올린 문서</label>
					<label>종류</label>
					<label><input type="checkbox" class="kn-search-doctype" value="METHOD" /> 메소드</label>
					<label><input type="checkbox" class="kn-search-doctype" value="TYPE" /> 타입</label>
					<label><input type="checkbox" class="kn-search-doctype" value="MAPPER" /> SQL</label>
					<label><input type="checkbox" class="kn-search-doctype" value="VIEW" /> 화면</label>
					<label><input type="checkbox" class="kn-search-doctype" value="FILE" /> 파일</label>
					<label for="kn-search-topk">건수</label>
					<input type="number" id="kn-search-topk" min="1" max="50" value="10" style="width:60px;" />
				</div>
				<div id="kn-search-message" class="kn-message"></div>
				<div id="kn-search-hits"></div>
			</section>

			<section class="ai-panel">
				<h3>문서 올리기</h3>
				<p class="ai-hint">설계서, 운영 매뉴얼 같은 일반 문서(PDF, Word, PowerPoint, Excel, 텍스트)를 올리면 위 검색에서 "올린 문서"로 찾을 수 있습니다.
					같은 이름으로 다시 올리면 바꿔 넣습니다. 임베딩이 끝나기 전에도 이름으로는 찾힙니다.</p>
				<div class="kn-row">
					<input type="file" id="kn-doc-file" />
					<input type="text" id="kn-doc-source-id" placeholder="문서 이름 (비우면 파일 이름)" />
					<input type="text" id="kn-doc-title" placeholder="제목 (비우면 파일 이름)" />
					<label><input type="checkbox" id="kn-doc-attach" /> 위에서 고른 프로젝트에 붙이기</label>
					<button type="button" id="kn-doc-upload-btn">올리기</button>
				</div>
				<div id="kn-doc-message" class="kn-message"></div>
				<div class="kn-row">
					<div class="kn-section-title" style="margin:0;">올린 문서</div>
					<button type="button" id="kn-doc-refresh-btn" class="kn-plain">새로고침</button>
				</div>
				<div id="kn-doc-table" class="kn-scroll"></div>
			</section>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/knowledge-common.js"></script>
	<script src="<%=requestUtil.getStrContextPath()%>/knowledge/assets/js/search.js"></script>
	<script>
		DstoneKnowledge.init("<%=requestUtil.getStrContextPath()%>");
		DstoneKnowledgeSearch.init();
	</script>
</body>
</html>
