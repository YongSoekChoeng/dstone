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
				<p class="cs-eyebrow">코드 분석</p>
				<h2>검색 · 문서</h2>
				<p>분석 결과(메소드, 타입, SQL, 화면)나 올린 문서를 뜻과 이름으로 찾고, 개발 가이드 같은 일반 문서를 묶음별로 올립니다.</p>
			</section>

			<details class="kn-howto">
				<summary>이렇게 씁니다</summary>
				<ol>
					<li>찾을 곳을 고릅니다. 프로젝트를 고르면 그 프로젝트의 분석 결과(분석이 끝난 가장 최근 리비전)에서, "(올린 문서)"를 고르면 올린 문서에서 찾습니다.</li>
					<li>평소 말하듯 적고 "찾기". 클래스 · 메소드 · 테이블 이름이나 주소를 알면 같이 적으세요. 이름이 정확히 맞는 것이 위로 올라옵니다.</li>
					<li>코드 결과 한 건은 메소드 하나 / SQL 하나 / 화면 하나이고, 문서 결과 한 건은 장/절 단위의 조각 하나(1,000자 안팎)입니다. 본문 상자를 누르면 전체가 펴집니다.
						<code>나온 곳 VECTOR+KEYWORD</code> 는 뜻과 이름 양쪽에서 찾혔다는 뜻이라 가장 믿을 만합니다.</li>
				</ol>
				예시 (누르면 채워집니다. <code>struts-app</code> 기준):
				<span class="kn-example" data-fill='{"kn-search-project":"struts-app","kn-search-query":"게시글을 삭제하는 곳"}'>게시글을 삭제하는 곳</span>
				<span class="kn-example" data-fill='{"kn-search-project":"struts-app","kn-search-query":"BoardDAO.deleteBoard"}'>BoardDAO.deleteBoard</span>
				<span class="kn-example" data-fill='{"kn-search-project":"struts-app","kn-search-query":"TB_BOARD 를 고치는 SQL"}'>TB_BOARD 를 고치는 SQL</span>
				<span class="kn-example" data-fill='{"kn-search-project":"struts-app","kn-search-query":"/board/save.do 요청을 받는 곳"}'>/board/save.do 요청을 받는 곳</span>
				<br>옵션은 그대로 두어도 됩니다. 코드에서는 "종류"를 체크하면 그 종류만(예: SQL 만) 나오고, 올린 문서에서는 "묶음"을 고르면 그 묶음의 문서에서만 찾습니다.
				결과가 적으면 임베딩이 아직 진행 중일 수 있습니다(검색 결과 위 줄의 "임베딩 PENDING" 숫자). 그동안은 이름을 넣어 찾으세요.
				뜻으로 찾은 결과는 유사도가 기준(코드 0.5, 올린 문서 0.35)보다 낮으면 버립니다. 그래서 프로젝트와 상관없는 질문에는 결과가 나오지 않습니다.
			</details>

			<section class="ai-panel">
				<h3>검색</h3>
				<p class="ai-hint">분석 결과(메소드, 타입, SQL, 화면)나 올린 문서를 찾습니다. 뜻으로 찾고("주문 취소는 어디서 처리하나"),
					질문에 들어 있는 클래스 · 메소드 · 테이블 이름이나 주소로도 찾아 두 결과를 합칩니다.
					한 번에 한쪽에서만 찾습니다. 프로젝트를 고르면 코드에서, "(올린 문서)"를 고르면 올린 문서에서 찾습니다.</p>
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th><label for="kn-search-project">찾을 곳</label></th>
						<td><select id="kn-search-project"></select></td>
						<th><label for="kn-search-mode">방법</label></th>
						<td><select id="kn-search-mode">
								<option value="HYBRID">뜻 + 이름 (HYBRID)</option>
								<option value="VECTOR">뜻만 (VECTOR)</option>
								<option value="KEYWORD">이름만 (KEYWORD)</option>
							</select></td>
						<th><label for="kn-search-topk">건수</label></th>
						<td><input type="number" id="kn-search-topk" min="1" max="50" value="10" /></td>
					</tr>
					<tr>
						<th><label for="kn-search-query">찾을 내용</label></th>
						<td colspan="5"><input type="text" id="kn-search-query" placeholder="예: 주문 취소는 어디서 처리하나 / OrderService.cancel / TB_ORDER" /></td>
					</tr>
					<tr id="kn-search-collection-row">
						<th><label for="kn-search-collection">묶음</label></th>
						<td colspan="5"><select id="kn-search-collection"></select></td>
					</tr>
					<tr id="kn-search-doctype-row">
						<th>종류</th>
						<td colspan="5" class="ai-form-checks"><label><input type="checkbox" class="kn-search-doctype" value="METHOD" /> 메소드</label>
							<label><input type="checkbox" class="kn-search-doctype" value="TYPE" /> 타입</label>
							<label><input type="checkbox" class="kn-search-doctype" value="MAPPER" /> SQL</label>
							<label><input type="checkbox" class="kn-search-doctype" value="VIEW" /> 화면</label>
							<label><input type="checkbox" class="kn-search-doctype" value="FILE" /> 파일</label></td>
					</tr>
				</table>
				<div class="ai-form-actions">
					<button type="button" id="kn-search-btn">찾기</button>
				</div>
				<div id="kn-search-message" class="kn-message"></div>
				<div id="kn-search-hits"></div>
			</section>

			<section class="ai-panel">
				<h3>문서 올리기</h3>
				<p class="ai-hint">개발 가이드, 설계서, 운영 매뉴얼 같은 일반 문서(PDF, Word, PowerPoint, Excel, 텍스트)를 올리면 위 검색에서 "(올린 문서)"로 찾을 수 있습니다.
					묶음 이름(예: nexacro, websquare)을 붙이면 묶음별로 찾을 수 있습니다. 같은 문서 이름으로 다시 올리면 바꿔 넣습니다.
					문서는 장/절(Word의 제목 스타일, PDF의 북마크) 단위로 나뉘고, 조각마다 제목 경로와 PDF 쪽이 붙습니다. 임베딩이 끝나기 전에도 영문 이름으로는 찾힙니다.
					AI 채팅에서는 knowledgeSearchDocs 같은 Tool 을 골라 이 문서들을 찾게 합니다.</p>
				<table class="ai-form">
					<colgroup><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /><col class="ai-form-name" /><col /></colgroup>
					<tr>
						<th><label for="kn-doc-file">파일</label></th>
						<td><input type="file" id="kn-doc-file" /></td>
						<th><label for="kn-doc-source-id">문서 이름</label></th>
						<td><input type="text" id="kn-doc-source-id" placeholder="비우면 파일 이름" /></td>
						<th><label for="kn-doc-title">제목</label></th>
						<td><input type="text" id="kn-doc-title" placeholder="비우면 파일 이름" /></td>
					</tr>
					<tr>
						<th><label for="kn-doc-collection">묶음</label></th>
						<td colspan="5"><input type="text" id="kn-doc-collection" list="kn-doc-collection-list" maxlength="100" placeholder="예: nexacro, websquare. 비우면 묶음 없음(쉼표는 쓸 수 없습니다)" />
							<datalist id="kn-doc-collection-list"></datalist></td>
					</tr>
				</table>
				<div class="ai-form-actions">
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
				</div>
			</div>
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
