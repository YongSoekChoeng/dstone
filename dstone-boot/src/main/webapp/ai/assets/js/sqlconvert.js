var DstoneAiSqlConvert = (function () {

	var urls = {};
	var originalEl, resultEl, submitBtn, statusEl, historyBodyEl;

	function init(options) {
		urls = options;
		originalEl = document.getElementById("sqlconvert-original");
		resultEl = document.getElementById("sqlconvert-result");
		submitBtn = document.getElementById("sqlconvert-submit-btn");
		statusEl = document.getElementById("sqlconvert-status");
		historyBodyEl = document.getElementById("sqlconvert-history-body");

		submitBtn.addEventListener("click", convert);

		loadHistory();
	}

	function convert() {
		var originalSql = originalEl.value.trim();
		if (!originalSql) {
			alert("오라클 SQL을 입력하세요.");
			return;
		}

		setLoading(true);
		resultEl.value = "";

		fetch(urls.convertUrl, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ originalSql: originalSql })
		})
			.then(function (response) {
				if (!response.ok) {
					throw new Error("서버 오류(" + response.status + ")");
				}
				return response.json();
			})
			.then(function (result) {
				if (result.SUCCESS_YN === "Y") {
					resultEl.value = result.CONVERTED_SQL || "";
					statusEl.textContent = "변환 완료";
				} else {
					resultEl.value = "";
					statusEl.textContent = "변환 실패: " + (result.ERROR_MESSAGE || "알 수 없는 오류");
				}
				loadHistory();
			})
			.catch(function (err) {
				statusEl.textContent = "요청 실패: " + err.message;
			})
			.finally(function () {
				setLoading(false);
			});
	}

	function setLoading(loading) {
		submitBtn.disabled = loading;
		statusEl.textContent = loading ? "변환 중입니다... (최대 2분 정도 걸릴 수 있습니다)" : statusEl.textContent;
	}

	function loadHistory() {
		fetch(urls.historyUrl, { method: "POST" })
			.then(function (response) { return response.json(); })
			.then(renderHistory)
			.catch(function (err) { console.error("이력 조회 실패: " + err.message); });
	}

	function renderHistory(historyList) {
		historyBodyEl.innerHTML = "";
		(historyList || []).forEach(function (history) {
			var tr = document.createElement("tr");
			var isSuccess = history.SUCCESS_YN === "Y";
			tr.innerHTML =
				"<td>" + escapeHtml(history.INPUT_DT) + "</td>" +
				"<td>" + escapeHtml(history.REQUESTER_ID) + "</td>" +
				"<td class=\"sqlconvert-history-sql\">" + escapeHtml(history.ORIGINAL_SQL) + "</td>" +
				"<td class=\"sqlconvert-history-sql\">" + escapeHtml(isSuccess ? history.CONVERTED_SQL : history.ERROR_MESSAGE) + "</td>" +
				"<td>" + (isSuccess ? "성공" : "실패") + "</td>";
			historyBodyEl.appendChild(tr);
		});
	}

	function escapeHtml(value) {
		var div = document.createElement("div");
		div.textContent = value == null ? "" : value;
		return div.innerHTML;
	}

	return { init: init };
})();
