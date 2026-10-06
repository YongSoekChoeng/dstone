/**
 * "검색 · 문서" 화면: 하이브리드 검색과 일반 문서 올리기.
 */
var DstoneKnowledgeSearch = (function () {

	var K = DstoneKnowledge;
	var el = {};

	function init() {
		["kn-search-project", "kn-search-query", "kn-search-btn", "kn-search-mode", "kn-search-topk", "kn-search-message", "kn-search-hits",
			"kn-doc-file", "kn-doc-source-id", "kn-doc-title", "kn-doc-attach", "kn-doc-upload-btn", "kn-doc-message", "kn-doc-refresh-btn", "kn-doc-table"]
			.forEach(function (id) {
				el[id] = document.getElementById(id);
			});
		el["kn-search-btn"].addEventListener("click", search);
		el["kn-search-query"].addEventListener("keydown", function (event) {
			if (event.key === "Enter") {
				search();
			}
		});
		el["kn-doc-upload-btn"].addEventListener("click", uploadDocument);
		el["kn-doc-refresh-btn"].addEventListener("click", loadDocuments);

		K.loadProjects(el["kn-search-project"], "(프로젝트 없이: 올린 문서에서만)").catch(function (err) {
			K.message(el["kn-search-message"], err.message, true);
		});
		loadDocuments();
	}

	/* ---------------- 검색 ---------------- */

	function checkedValues(className) {
		return Array.prototype.filter.call(document.querySelectorAll("." + className), function (box) {
			return box.checked;
		}).map(function (box) {
			return box.value;
		});
	}

	function search() {
		var projectId = el["kn-search-project"].value;
		var body = {
			query: el["kn-search-query"].value,
			mode: el["kn-search-mode"].value,
			topK: parseInt(el["kn-search-topk"].value, 10) || 10
		};
		var sources = checkedValues("kn-search-source");
		if (projectId) {
			body.projectId = projectId;
			body.sourceTypes = sources;
		} else {
			// 프로젝트가 없으면 코드에서는 찾을 수 없다.
			body.sourceTypes = ["DOCUMENT"];
		}
		var docTypes = checkedValues("kn-search-doctype");
		if (docTypes.length > 0) {
			// 올린 문서도 같이 찾을 때는 그 종류(UPLOAD)가 걸러지지 않게 넣어 준다.
			if (body.sourceTypes.indexOf("DOCUMENT") >= 0) {
				docTypes.push("UPLOAD");
			}
			body.docTypes = docTypes;
		}
		K.message(el["kn-search-message"], "찾는 중...");
		el["kn-search-hits"].innerHTML = "";
		var startedAt = Date.now();
		K.call("POST", "/api/search", null, body).then(function (result) {
			var text = result.count + "건 · 방법 " + result.mode + (result.keywords && result.keywords.length ? " · 고른 이름: " + result.keywords.join(", ") : "")
				+ " · " + ((Date.now() - startedAt) / 1000).toFixed(1) + "초";
			if (result.embedding) {
				text += " · 임베딩 " + result.embedding.map(function (row) { return row.status + " " + row.chunks; }).join(" / ");
			}
			K.message(el["kn-search-message"], (result.warning ? result.warning + " · " : "") + text, !!result.warning);
			renderHits(result.hits || []);
		}).catch(function (err) {
			K.message(el["kn-search-message"], err.message, true);
		});
	}

	function renderHits(hits) {
		el["kn-search-hits"].innerHTML = "";
		hits.forEach(function (hit, index) {
			var meta = [];
			meta.push((hit.path || "") + (hit.lineStart ? ":" + hit.lineStart + "-" + hit.lineEnd : ""));
			meta.push("나온 곳 " + (hit.matched || []).join("+"));
			if (hit.score !== null && hit.score !== undefined) {
				meta.push("유사도 " + hit.score);
			}
			if (hit.keywordScore !== null && hit.keywordScore !== undefined) {
				meta.push("이름 점수 " + hit.keywordScore);
			}
			if (hit.refKind === "METHOD") {
				meta.push("methodId " + hit.refId);
			}
			var div = document.createElement("div");
			div.className = "kn-hit";
			div.innerHTML = "<div class=\"kn-hit-title\">" + (index + 1) + ". <span class=\"workflow-badge\">" + K.escapeHtml(hit.docType) + "</span> "
				+ K.escapeHtml(hit.title) + "</div><div class=\"kn-hit-meta\">" + K.escapeHtml(meta.join(" · ")) + "</div><pre class=\"kn-pre\">"
				+ K.escapeHtml(hit.content) + "</pre>";
			// 본문은 접어서 보여 주고, 누르면 전체를 편다.
			div.querySelector("pre").addEventListener("click", function () {
				this.classList.toggle("kn-open");
			});
			el["kn-search-hits"].appendChild(div);
		});
	}

	/* ---------------- 문서 ---------------- */

	function uploadDocument() {
		var file = el["kn-doc-file"].files[0];
		if (!file) {
			K.message(el["kn-doc-message"], "올릴 파일을 고르세요.", true);
			return;
		}
		K.message(el["kn-doc-message"], "올리는 중...");
		K.upload(file, {
			sourceId: el["kn-doc-source-id"].value.trim(),
			title: el["kn-doc-title"].value.trim(),
			projectId: el["kn-doc-attach"].checked ? el["kn-search-project"].value : ""
		}).then(function (result) {
			K.message(el["kn-doc-message"], "올렸습니다: " + result.sourceId + " · 글자 " + result.chars.toLocaleString() + " · 청크 " + result.chunks
				+ (result.replaced ? " · 같은 이름의 문서를 바꿔 넣음" : "") + (result.truncated ? " · 너무 길어 뒷부분은 버림" : ""));
			loadDocuments();
		}).catch(function (err) {
			K.message(el["kn-doc-message"], err.message, true);
		});
	}

	function loadDocuments() {
		K.call("GET", "/api/documents", { size: 200 }).then(function (result) {
			var documents = result.documents || [];
			K.table(el["kn-doc-table"], [
				{ key: "sourceId", label: "문서 이름" },
				{ key: "title", label: "제목" },
				{ key: "projectId", label: "프로젝트" },
				{ key: "chunks", label: "청크", num: true },
				{ key: "embedded", label: "임베딩 끝남", num: true },
				{ key: "createdAt", label: "올린 시각" },
				{ key: "action", label: "", html: function () { return "<button type=\"button\">삭제</button>"; } }
			], documents, "올린 문서가 없습니다.");
			Array.prototype.forEach.call(el["kn-doc-table"].querySelectorAll("tbody tr[data-index]"), function (tr) {
				var row = documents[parseInt(tr.getAttribute("data-index"), 10)];
				tr.querySelector("button").addEventListener("click", function () {
					deleteDocument(row.sourceId);
				});
			});
		}).catch(function (err) {
			K.message(el["kn-doc-message"], err.message, true);
		});
	}

	function deleteDocument(sourceId) {
		if (!confirm("문서 \"" + sourceId + "\"를 지웁니다.")) {
			return;
		}
		K.call("DELETE", "/api/documents", { sourceId: sourceId }).then(function () {
			K.message(el["kn-doc-message"], "지웠습니다: " + sourceId);
			loadDocuments();
		}).catch(function (err) {
			K.message(el["kn-doc-message"], err.message, true);
		});
	}

	return { init: init };
})();
