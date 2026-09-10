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
				<h3>문서 업로드</h3>
				<p class="ai-hint">업로드한 문서는 dstone-ai-engine의 RAG 검색 대상으로 적재됩니다. SOURCE_ID는 문서를 구분하는 값으로,
					같은 SOURCE_ID로 다시 업로드하면 기존 내용을 대체합니다.</p>
				<form id="document-upload-form" class="document-upload-form">
					<input type="text" id="document-source-id" name="SOURCE_ID" placeholder="SOURCE_ID" required />
					<input type="file" id="document-file" name="file" required />
					<button type="submit" id="document-upload-btn">업로드</button>
				</form>
			</section>

			<section class="ai-panel">
				<h3>적재된 문서 목록</h3>
				<table id="document-list-table" class="document-list-table">
					<thead>
						<tr>
							<th>SOURCE_ID</th>
							<th>파일명</th>
							<th>청크 수</th>
							<th>업로더</th>
							<th>업로드일시</th>
							<th></th>
						</tr>
					</thead>
					<tbody id="document-list-body"></tbody>
				</table>
			</section>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/document.js"></script>
	<script>
		DstoneAiDocument.init({
			uploadUrl: "<%=requestUtil.getStrContextPath()%>/ai/document/uploadDocument.do",
			listUrl: "<%=requestUtil.getStrContextPath()%>/ai/document/listDocument.do",
			deleteUrl: "<%=requestUtil.getStrContextPath()%>/ai/document/deleteDocument.do"
		});
	</script>
</body>
</html>
