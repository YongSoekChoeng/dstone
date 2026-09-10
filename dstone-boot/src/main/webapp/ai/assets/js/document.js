var DstoneAiDocument = (function () {

	var urls = {};
	var formEl, sourceIdEl, fileEl, listBodyEl;

	function init(options) {
		urls = options;
		formEl = document.getElementById("document-upload-form");
		sourceIdEl = document.getElementById("document-source-id");
		fileEl = document.getElementById("document-file");
		listBodyEl = document.getElementById("document-list-body");

		formEl.addEventListener("submit", function (e) {
			e.preventDefault();
			upload();
		});

		loadList();
	}

	function loadList() {
		fetch(urls.listUrl, { method: "POST" })
			.then(function (response) { return response.json(); })
			.then(renderList)
			.catch(function (err) { alert("목록 조회 실패: " + err.message); });
	}

	function renderList(documents) {
		listBodyEl.innerHTML = "";
		(documents || []).forEach(function (doc) {
			var tr = document.createElement("tr");
			tr.innerHTML =
				"<td>" + escapeHtml(doc.SOURCE_ID) + "</td>" +
				"<td>" + escapeHtml(doc.FILE_NAME) + "</td>" +
				"<td>" + doc.CHUNK_COUNT + "</td>" +
				"<td>" + escapeHtml(doc.UPLOADER_ID) + "</td>" +
				"<td>" + escapeHtml(doc.INPUT_DT) + "</td>";
			var deleteTd = document.createElement("td");
			var deleteBtn = document.createElement("button");
			deleteBtn.type = "button";
			deleteBtn.textContent = "삭제";
			deleteBtn.addEventListener("click", function () { remove(doc.SOURCE_ID); });
			deleteTd.appendChild(deleteBtn);
			tr.appendChild(deleteTd);
			listBodyEl.appendChild(tr);
		});
	}

	function upload() {
		if (!sourceIdEl.value.trim() || !fileEl.files.length) {
			alert("SOURCE_ID와 업로드 파일을 모두 입력하세요.");
			return;
		}
		var formData = new FormData();
		formData.append("SOURCE_ID", sourceIdEl.value.trim());
		formData.append("file", fileEl.files[0]);

		fetch(urls.uploadUrl, { method: "POST", body: formData })
			.then(function (response) {
				if (!response.ok) {
					throw new Error("서버 오류(" + response.status + ")");
				}
				sourceIdEl.value = "";
				fileEl.value = "";
				loadList();
			})
			.catch(function (err) { alert("업로드 실패: " + err.message); });
	}

	function remove(sourceId) {
		if (!confirm(sourceId + " 문서를 삭제할까요?")) {
			return;
		}
		var formData = new FormData();
		formData.append("SOURCE_ID", sourceId);
		fetch(urls.deleteUrl, { method: "POST", body: formData })
			.then(function (response) {
				if (!response.ok) {
					throw new Error("서버 오류(" + response.status + ")");
				}
				loadList();
			})
			.catch(function (err) { alert("삭제 실패: " + err.message); });
	}

	function escapeHtml(value) {
		var div = document.createElement("div");
		div.textContent = value == null ? "" : value;
		return div.innerHTML;
	}

	return { init: init };
})();
