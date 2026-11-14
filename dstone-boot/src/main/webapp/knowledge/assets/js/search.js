/**
 * "검색 · 문서" 화면: 하이브리드 검색(코드 또는 올린 문서)과 일반 문서 올리기.
 */
var DstoneKnowledgeSearch = (function () {

	var K = DstoneKnowledge;
	var el = {};

	function init() {
		["kn-search-project", "kn-search-query", "kn-search-btn", "kn-search-mode", "kn-search-topk", "kn-search-message", "kn-search-hits",
			"kn-search-collection", "kn-search-collection-row", "kn-search-doctype-row",
			"kn-doc-file", "kn-doc-source-id", "kn-doc-title", "kn-doc-collection", "kn-doc-collection-list", "kn-doc-upload-btn", "kn-doc-message", "kn-doc-refresh-btn", "kn-doc-table"]
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
		el["kn-search-project"].addEventListener("change", updateScopeRows);

		K.loadProjects(el["kn-search-project"], "(올린 문서)").then(updateScopeRows).catch(function (err) {
			K.message(el["kn-search-message"], err.message, true);
		});
		updateScopeRows();
		loadDocuments();
	}

	/** 코드를 찾을 때는 "종류"를, 올린 문서를 찾을 때는 "묶음"을 보여 준다 */
	function updateScopeRows() {
		var code = !!el["kn-search-project"].value;
		el["kn-search-doctype-row"].style.display = code ? "" : "none";
		el["kn-search-collection-row"].style.display = code ? "none" : "";
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
		if (projectId) {
			// 프로젝트를 고르면 그 프로젝트의 코드에서 찾는다.
			body.projectId = projectId;
			var docTypes = checkedValues("kn-search-doctype");
			if (docTypes.length > 0) {
				body.docTypes = docTypes;
			}
		} else if (el["kn-search-collection"].value) {
			// 프로젝트가 없으면 올린 문서에서 찾는다. 묶음을 고르면 그 안에서만.
			body.collections = [el["kn-search-collection"].value];
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
			// 뜻으로 찾은 것이 모두 기준보다 멀면 결과가 0건이다. 엉뚱한 결과 대신 왜 없는지 알려 준다.
			if (result.count === 0 && result.belowMinScore > 0) {
				text = "질문과 가까운 내용이 없습니다(가장 가까운 것의 유사도 " + result.bestBelowMinScore
					+ ", 기준 " + result.minScore + ") · " + text;
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
			if (hit.sourceType === "DOCUMENT") {
				// 올린 문서: 어느 문서의 몇 번째 조각인지, 묶음
				meta.push((hit.collection ? "[" + hit.collection + "] " : "") + hit.documentId + " #" + hit.chunkNo);
			} else {
				meta.push((hit.path || "") + (hit.lineStart ? ":" + hit.lineStart + "-" + hit.lineEnd : ""));
			}
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
		var collection = el["kn-doc-collection"].value.trim();
		if (collection.indexOf(",") >= 0) {
			K.message(el["kn-doc-message"], "묶음 이름에는 쉼표를 쓸 수 없습니다.", true);
			return;
		}
		K.upload(file, {
			sourceId: el["kn-doc-source-id"].value.trim(),
			title: el["kn-doc-title"].value.trim(),
			collection: collection
		}).then(function (result) {
			K.message(el["kn-doc-message"], "올렸습니다: " + result.sourceId + (result.collection ? " · 묶음 " + result.collection : "")
				+ " · 글자 " + result.chars.toLocaleString() + " · 청크 " + result.chunks + " · 나눈 방법 " + STRUCTURE_LABELS[result.structure]
				+ (result.replaced ? " · 같은 이름의 문서를 바꿔 넣음" : "") + (result.truncated ? " · 너무 길어 뒷부분은 버림" : ""));
			loadDocuments();
		}).catch(function (err) {
			K.message(el["kn-doc-message"], err.message, true);
		});
	}

	/** 올린 결과의 structure 값을 사람이 읽는 말로 */
	var STRUCTURE_LABELS = {
		HEADING: "제목 스타일(장/절)",
		BOOKMARK: "PDF 북마크(장/절)",
		NONE: "제목 없음(문단)",
		LINE: "한 줄씩(JSONL)"
	};

	function loadDocuments() {
		loadCollections();
		K.call("GET", "/api/documents", { size: 200 }).then(function (result) {
			var documents = result.documents || [];
			K.table(el["kn-doc-table"], [
				{ key: "collection", label: "묶음" },
				{ key: "sourceId", label: "문서 이름" },
				{ key: "title", label: "제목" },
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

	/** 묶음 목록: 검색의 "묶음" 칸과 올리기의 묶음 이름 제안에 채운다 */
	function loadCollections() {
		K.call("GET", "/api/documents/collections").then(function (rows) {
			var selected = el["kn-search-collection"].value;
			var options = "<option value=\"\">(전부)</option>";
			var suggestions = "";
			(rows || []).forEach(function (row) {
				if (!row.collection) {
					return;
				}
				options += "<option value=\"" + K.escapeHtml(row.collection) + "\">" + K.escapeHtml(row.collection) + " (" + row.documents + "건)</option>";
				suggestions += "<option value=\"" + K.escapeHtml(row.collection) + "\"></option>";
			});
			el["kn-search-collection"].innerHTML = options;
			el["kn-search-collection"].value = selected;
			if (el["kn-search-collection"].value !== selected) {
				el["kn-search-collection"].value = "";
			}
			el["kn-doc-collection-list"].innerHTML = suggestions;
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
