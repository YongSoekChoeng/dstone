/**
 * "프로젝트 · 분석" 화면의 "풀지 못한 참조 살펴보기".
 * 리비전 요약에서 버튼을 누르면 열린다. 원인(못 찾은 기호) · 파일 · 이름별로 묶어 세고,
 * 묶음을 누르면 그 조건으로 좁혀서 한 건씩(파일 · 줄 · 호출식 · 이유) 보여 준다.
 */
var DstoneKnowledgeUnresolved = (function () {

	var K = DstoneKnowledge;
	var T = DstoneKnowledgeTerms;
	var PAGE_SIZE = 50;

	var el = {};
	var revision = null;
	// 표의 줄을 눌러 건 조건. 셋 다 같이 걸 수 있다
	// cause 는 보여 줄 글자, causeKey 는 거를 때 보내는 키(원인의 md5)
	var filter = { cause: null, causeKey: null, name: null, fileId: null, filePath: null };
	var page = 1;
	var total = 0;

	function init() {
		["kn-unresolved-panel", "kn-unresolved-title", "kn-unresolved-status", "kn-unresolved-kind", "kn-unresolved-path", "kn-unresolved-search-btn",
			"kn-unresolved-filter", "kn-unresolved-message", "kn-unresolved-by-cause", "kn-unresolved-by-file", "kn-unresolved-by-name",
			"kn-unresolved-count", "kn-unresolved-list", "kn-unresolved-prev-btn", "kn-unresolved-page", "kn-unresolved-next-btn"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		el["kn-unresolved-search-btn"].addEventListener("click", function () { reload(); });
		el["kn-unresolved-status"].addEventListener("change", function () { reload(); });
		el["kn-unresolved-kind"].addEventListener("change", function () { reload(); });
		el["kn-unresolved-path"].addEventListener("keydown", function (event) {
			if (event.key === "Enter") {
				reload();
			}
		});
		el["kn-unresolved-prev-btn"].addEventListener("click", function () { loadList(page - 1); });
		el["kn-unresolved-next-btn"].addEventListener("click", function () { loadList(page + 1); });
	}

	/** 리비전 하나를 연다. status 를 주면 그 상태로 시작한다 (기본 UNRESOLVED) */
	function open(target, status) {
		revision = target;
		filter = { cause: null, causeKey: null, name: null, fileId: null, filePath: null };
		el["kn-unresolved-status"].value = status || "UNRESOLVED";
		el["kn-unresolved-kind"].value = "CALL";
		el["kn-unresolved-path"].value = "";
		el["kn-unresolved-title"].textContent = "#" + target.revisionId + " " + (target.revisionLabel || "");
		el["kn-unresolved-panel"].style.display = "";
		reload();
		el["kn-unresolved-panel"].scrollIntoView({ behavior: "smooth" });
	}

	/** 지금 조건을 API 파라미터로 */
	function query(extra) {
		var q = {
			status: el["kn-unresolved-status"].value,
			refKind: el["kn-unresolved-kind"].value
		};
		var path = el["kn-unresolved-path"].value.trim();
		if (path) {
			q.path = path;
		}
		if (filter.causeKey !== null) {
			q.causeKey = filter.causeKey;
		}
		if (filter.name !== null) {
			q.name = filter.name;
		}
		if (filter.fileId !== null) {
			q.fileId = filter.fileId;
		}
		Object.keys(extra || {}).forEach(function (key) {
			q[key] = extra[key];
		});
		return q;
	}

	function reload() {
		if (!revision) {
			return;
		}
		renderFilter();
		loadGroups();
		loadList(1);
	}

	function loadGroups() {
		["kn-unresolved-by-cause", "kn-unresolved-by-file", "kn-unresolved-by-name"].forEach(function (id) {
			el[id].innerHTML = "<p class=\"ai-hint\">읽는 중...</p>";
		});
		el["kn-unresolved-message"].textContent = "";
		el["kn-unresolved-message"].className = "kn-message";
		K.call("GET", "/api/revisions/" + revision.revisionId + "/unresolved/groups", query({ limit: 50 })).then(function (groups) {
			K.table(el["kn-unresolved-by-cause"], [
				{ key: "cause", label: "원인", mono: true, html: function (row) {
					return "<span title=\"" + K.escapeHtml(row.example) + "\">" + K.escapeHtml(row.cause) + "</span>";
				} },
				{ key: "count", label: "건수", num: true },
				{ key: "files", label: "파일", num: true }
			], groups.byCause, "없습니다.");
			onRowClick(el["kn-unresolved-by-cause"], groups.byCause, function (row) {
				filter.cause = row.cause;
				filter.causeKey = row.causeKey;
			});

			K.table(el["kn-unresolved-by-file"], [
				{ key: "path", label: "파일", mono: true },
				{ key: "count", label: "건수", num: true }
			], groups.byFile, "없습니다.");
			onRowClick(el["kn-unresolved-by-file"], groups.byFile, function (row) {
				filter.fileId = row.fileId;
				filter.filePath = row.path;
			});

			K.table(el["kn-unresolved-by-name"], [
				{ key: "name", label: "이름", mono: true },
				{ key: "count", label: "건수", num: true },
				{ key: "files", label: "파일", num: true }
			], groups.byName, "없습니다.");
			onRowClick(el["kn-unresolved-by-name"], groups.byName, function (row) {
				filter.name = row.name;
			});
		}).catch(function (err) {
			showError(err);
		});
	}

	function loadList(pageNo) {
		if (!revision || pageNo < 1) {
			return;
		}
		el["kn-unresolved-list"].innerHTML = "<p class=\"ai-hint\">읽는 중...</p>";
		K.call("GET", "/api/revisions/" + revision.revisionId + "/unresolved", query({ page: pageNo, size: PAGE_SIZE })).then(function (result) {
			page = result.page;
			total = result.total;
			var lastPage = Math.max(1, Math.ceil(total / result.size));
			el["kn-unresolved-count"].textContent = total.toLocaleString() + "건";
			el["kn-unresolved-page"].textContent = page + " / " + lastPage;
			el["kn-unresolved-prev-btn"].disabled = page <= 1;
			el["kn-unresolved-next-btn"].disabled = page >= lastPage;
			K.table(el["kn-unresolved-list"], [
				{ key: "path", label: "파일:줄", mono: true, html: function (row) {
					return K.escapeHtml(row.path + ":" + (row.lineStart === null || row.lineStart === undefined ? "?" : row.lineStart));
				} },
				{ key: "refKind", label: "종류", html: function (row) {
					return "<span class=\"kn-code\" title=\"" + K.escapeHtml(T.meaningOf(T.REF_KIND, row.refKind) || "") + "\">" + K.escapeHtml(row.refKind) + "</span>";
				} },
				{ key: "call", label: "참조", mono: true, html: function (row) {
					return K.escapeHtml(callText(row));
				} },
				{ key: "fromMethod", label: "들어 있는 메소드", mono: true },
				{ key: "cause", label: "원인", mono: true },
				{ key: "reason", label: "이유", html: function (row) {
					return "<span class=\"ai-hint\">" + K.escapeHtml(row.reason || "") + "</span>";
				} }
			], result.references, "조건에 맞는 참조가 없습니다.");
		}).catch(function (err) {
			showError(err);
		});
	}

	/** 소스에 적힌 모양에 가깝게: scope.name(인자 n개) */
	function callText(row) {
		var text = (row.scopeText ? row.scopeText + "." : "") + row.name;
		if (row.argCount !== null && row.argCount !== undefined) {
			text += "(" + (row.argCount > 0 ? "인자 " + row.argCount + "개" : "") + ")";
		}
		return text;
	}

	/** 묶음 표의 줄을 누르면 그 조건을 걸고 다시 읽는다 */
	function onRowClick(container, rows, apply) {
		Array.prototype.forEach.call(container.querySelectorAll("tbody tr[data-index]"), function (tr) {
			tr.addEventListener("click", function () {
				apply(rows[parseInt(tr.getAttribute("data-index"), 10)]);
				reload();
			});
		});
	}

	/** 걸린 조건을 단추로 보여 준다. 누르면 그 조건이 풀린다 */
	function renderFilter() {
		var chips = [];
		if (filter.cause !== null) {
			chips.push(["cause", "원인: " + filter.cause]);
		}
		if (filter.fileId !== null) {
			chips.push(["fileId", "파일: " + filter.filePath]);
		}
		if (filter.name !== null) {
			chips.push(["name", "이름: " + filter.name]);
		}
		el["kn-unresolved-filter"].innerHTML = chips.map(function (chip) {
			return "<button type=\"button\" class=\"kn-plain kn-unresolved-chip\" data-key=\"" + chip[0] + "\" title=\"누르면 이 조건을 풉니다\">"
				+ K.escapeHtml(chip[1]) + " ✕</button>";
		}).join("");
		Array.prototype.forEach.call(el["kn-unresolved-filter"].querySelectorAll("button"), function (button) {
			button.addEventListener("click", function () {
				var key = button.getAttribute("data-key");
				filter[key] = null;
				if (key === "fileId") {
					filter.filePath = null;
				}
				if (key === "cause") {
					filter.causeKey = null;
				}
				reload();
			});
		});
	}

	function showError(err) {
		el["kn-unresolved-message"].className = "kn-message kn-error";
		el["kn-unresolved-message"].textContent = err.message;
	}

	return { init: init, open: open };
})();
