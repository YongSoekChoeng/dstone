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
				<h3>오라클 → PostgreSQL SQL 변환</h3>
				<p class="ai-hint">오라클 SQL 한 문장을 입력하고 [변환] 버튼을 누르면, dstone-ai-engine이 PostgreSQL SQL로 바꿔서
					보여줍니다. 변환에는 몇 초에서 최대 2분 정도 걸릴 수 있습니다.</p>

				<div class="sqlconvert-columns">
					<div class="sqlconvert-column">
						<label for="sqlconvert-original">오라클 SQL</label>
						<textarea id="sqlconvert-original" class="sqlconvert-textarea" placeholder="예: SELECT NVL(a, 0) FROM dual"></textarea>
					</div>
					<div class="sqlconvert-column">
						<label for="sqlconvert-result">PostgreSQL SQL</label>
						<textarea id="sqlconvert-result" class="sqlconvert-textarea" readonly></textarea>
					</div>
				</div>

				<div class="sqlconvert-actions">
					<button type="button" id="sqlconvert-submit-btn">변환</button>
					<span id="sqlconvert-status" class="sqlconvert-status"></span>
				</div>
			</section>

			<section class="ai-panel">
				<h3>최근 변환 이력</h3>
				<table id="sqlconvert-history-table" class="document-list-table">
					<thead>
						<tr>
							<th>요청시각</th>
							<th>요청자</th>
							<th>오라클 SQL</th>
							<th>변환 SQL / 오류</th>
							<th>결과</th>
						</tr>
					</thead>
					<tbody id="sqlconvert-history-body"></tbody>
				</table>
			</section>
		</div>

		<jsp:include page="../common/footer.jsp"></jsp:include>

	</div>

	<script src="<%=requestUtil.getStrContextPath()%>/ai/assets/js/sqlconvert.js"></script>
	<script>
		DstoneAiSqlConvert.init({
			convertUrl: "<%=requestUtil.getStrContextPath()%>/ai/sqlconvert/convert.do",
			historyUrl: "<%=requestUtil.getStrContextPath()%>/ai/sqlconvert/listHistory.do"
		});
	</script>
</body>
</html>
