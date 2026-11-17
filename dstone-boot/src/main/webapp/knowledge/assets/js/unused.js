/**
 * "분석 실행 · 진단" 화면의 미사용 탭(관리용. 같은 내용을 "프로젝트" 화면의 전환 제외 후보 탭이 고객용으로 보여 준다).
 * 있지만 아무도 쓰지 않는 테이블 · SQL · 클래스 · 메소드 · 화면의 건수를 카드로 보여 주고, 카드를 누르면 그 목록을 연다.
 * 목록의 이름을 누르면 관계 트리 창이 떠서, 정말 아무도 부르지 않는지 한 번 더 확인할 수 있다.
 * 계산은 dstone-knowledge 의 /api/revisions/{id}/unused 가 한다(UnusedService).
 */
var DstoneKnowledgeUnused = (function () {

	var K = DstoneKnowledge;
	var T = DstoneKnowledgeTerms;
	var el = {};
	// 지금 보는 리비전 {projectId, revisionId, revisionLabel} 과 그 건수 요약
	var target = null;
	var summaryData = null;
	// 지금 연 목록의 조건
	var list = { kind: null, page: 1, size: 50, total: 0 };
	// 늦게 도착한 이전 응답은 버린다
	var summaryRequestNo = 0;
	var listRequestNo = 0;

	var KINDS = ["TABLE", "SQL", "TYPE", "METHOD", "SCREEN"];

	function init() {
		["kn-unused-note", "kn-unused-cards", "kn-unused-list-box", "kn-unused-list-title", "kn-unused-grade", "kn-unused-q", "kn-unused-search-btn",
			"kn-unused-csv-btn", "kn-unused-message", "kn-unused-list", "kn-unused-prev-btn", "kn-unused-page", "kn-unused-next-btn"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		el["kn-unused-search-btn"].addEventListener("click", function () {
			list.page = 1;
			loadList();
		});
		el["kn-unused-q"].addEventListener("keydown", function (event) {
			if (event.key === "Enter") {
				list.page = 1;
				loadList();
			}
		});
		el["kn-unused-grade"].addEventListener("change", function () {
			list.page = 1;
			loadList();
		});
		el["kn-unused-prev-btn"].addEventListener("click", function () {
			if (list.page > 1) {
				list.page--;
				loadList();
			}
		});
		el["kn-unused-next-btn"].addEventListener("click", function () {
			if (list.page * list.size < list.total) {
				list.page++;
				loadList();
			}
		});
		el["kn-unused-csv-btn"].addEventListener("click", downloadCsv);
	}

	/**
	 * 리비전 하나의 미사용 건수를 읽어 탭을 채운다. 다 읽으면 done(요약) 을 부른다(실패하면 null).
	 */
	function load(revision, done) {
		close();
		target = revision;
		var requestNo = ++summaryRequestNo;
		el["kn-unused-cards"].innerHTML = "<p class=\"ai-hint\">계산하는 중... (큰 프로젝트는 몇 초 걸립니다)</p>";
		K.call("GET", "/api/revisions/" + revision.revisionId + "/unused").then(function (summary) {
			if (requestNo !== summaryRequestNo) {
				return;
			}
			summaryData = summary;
			render(summary);
			done(summary);
		}).catch(function (err) {
			if (requestNo !== summaryRequestNo) {
				return;
			}
			el["kn-unused-cards"].innerHTML = "<div class=\"kn-message kn-error\">" + K.escapeHtml(err.message) + "</div>";
			done(null);
		});
	}

	/** 탭을 비운다. 읽고 있던 응답도 버린다 */
	function close() {
		summaryRequestNo++;
		listRequestNo++;
		target = null;
		summaryData = null;
		list.kind = null;
		el["kn-unused-note"].innerHTML = "";
		el["kn-unused-cards"].innerHTML = "";
		el["kn-unused-list"].innerHTML = "";
		el["kn-unused-list-box"].style.display = "none";
		el["kn-unused-q"].value = "";
		el["kn-unused-grade"].value = "";
		K.message(el["kn-unused-message"], "");
	}

	/** 탭 위쪽: 읽을 때 알아야 할 것 + 종류별 카드 */
	function render(summary) {
		var notes = [];
		if (summary.warning) {
			notes.push("<div class=\"kn-verdict-line kn-verdict-warn\"><span class=\"kn-verdict-mark\">!</span>" + K.escapeHtml(summary.warning) + "</div>");
		}
		var tableNote = tableSourceNote(summary.tableSources || {});
		if (tableNote) {
			notes.push("<div class=\"kn-verdict-line kn-verdict-info\"><span class=\"kn-verdict-mark\">·</span>" + K.escapeHtml(tableNote) + "</div>");
		}
		el["kn-unused-note"].innerHTML = notes.length > 0 ? "<div class=\"kn-verdict\">" + notes.join("") + "</div>" : "";

		var kinds = byKind(summary);
		el["kn-unused-cards"].innerHTML = KINDS.map(function (kind) {
			var item = kinds[kind] || { total: 0, certain: 0, likely: 0 };
			var label = T.UNUSED_KIND[kind];
			return "<button type=\"button\" class=\"kn-card kn-card-button" + (item.certain > 0 ? " kn-card-warn" : "") + "\" data-kind=\"" + kind + "\" title=\""
				+ K.escapeHtml(label[1]) + "\"><div class=\"kn-card-value\">" + T.fmt(item.total) + "</div><div class=\"kn-card-label\">" + K.escapeHtml(label[0]) + "</div>"
				+ "<div class=\"kn-card-help\">확실 " + T.fmt(item.certain) + " · 추정 " + T.fmt(item.likely) + "</div></button>";
		}).join("");
		Array.prototype.forEach.call(el["kn-unused-cards"].querySelectorAll(".kn-card-button"), function (button) {
			button.addEventListener("click", function () {
				openList(button.getAttribute("data-kind"));
			});
		});
	}

	/** 테이블 목록을 어디서 얻었는지. 아무 데서도 못 얻었으면 그 까닭 */
	function tableSourceNote(sources) {
		var parts = [];
		if (sources.db === "OK") {
			parts.push("DB " + T.fmt(sources.dbTables) + "개");
		}
		if (sources.ddl > 0) {
			parts.push("DDL 파일 " + T.fmt(sources.ddl) + "개");
		}
		if (sources.dict > 0) {
			parts.push("테이블 사전 " + T.fmt(sources.dict) + "개");
		}
		var failed = sources.db === "FAILED" ? " DB 에서 테이블 목록을 읽지 못했습니다: " + (sources.dbMessage || "") : "";
		if (parts.length === 0) {
			return "테이블은 셀 수 없습니다. 어떤 테이블이 있는지 알려 줄 것(DB 접속 설정, DDL 파일, 테이블 사전 CSV)이 이 프로젝트에 없습니다." + failed;
		}
		return "테이블은 " + parts.join(" · ") + "의 목록과 견줘 셉니다." + failed;
	}

	/**
	 * 개요 탭의 미사용 상자: 종류별 건수 한 줄과 "미사용 탭에서 보기".
	 * 개요는 요약 응답과 미사용 응답이 따로 도착하므로, 늦게 온 쪽이 이 함수로 채운다.
	 */
	function renderOverview(box, summary, openTab) {
		if (!summary) {
			box.innerHTML = "<div class=\"kn-section-title\" style=\"margin-top:0;\">미사용</div><p class=\"ai-hint\">계산하지 못했습니다. 미사용 탭에서 까닭을 봅니다.</p>";
			return;
		}
		var kinds = byKind(summary);
		var certain = 0;
		var parts = KINDS.map(function (kind) {
			var item = kinds[kind] || { total: 0, certain: 0 };
			certain += item.certain;
			return T.UNUSED_KIND[kind][0] + " " + T.fmt(item.total);
		});
		box.innerHTML = "<div class=\"kn-section-title\" style=\"margin-top:0;\">미사용</div>"
			+ "<div class=\"kn-verdict-line kn-verdict-" + (certain > 0 ? "warn" : "good") + "\"><span class=\"kn-verdict-mark\">" + (certain > 0 ? "!" : "✔") + "</span>"
			+ K.escapeHtml(parts.join(" · ")) + " <span class=\"ai-hint\">(그 가운데 확실 " + T.fmt(certain) + ")</span>"
			+ " <button type=\"button\" class=\"kn-plain kn-unused-open\">미사용 탭에서 보기</button></div>";
		box.querySelector(".kn-unused-open").addEventListener("click", openTab);
	}

	function byKind(summary) {
		var kinds = {};
		(summary.kinds || []).forEach(function (item) {
			kinds[item.kind] = item;
		});
		return kinds;
	}

	/* ---------------- 목록 ---------------- */

	function openList(kind) {
		list.kind = kind;
		list.page = 1;
		el["kn-unused-q"].value = "";
		el["kn-unused-grade"].value = "";
		Array.prototype.forEach.call(el["kn-unused-cards"].querySelectorAll(".kn-card-button"), function (button) {
			button.classList.toggle("kn-card-on", button.getAttribute("data-kind") === kind);
		});
		el["kn-unused-list-title"].textContent = "미사용 " + T.UNUSED_KIND[kind][0];
		el["kn-unused-list-box"].style.display = "";
		loadList();
	}

	function query(page, size) {
		var params = { page: page, size: size };
		if (el["kn-unused-grade"].value) {
			params.grade = el["kn-unused-grade"].value;
		}
		if (el["kn-unused-q"].value.trim()) {
			params.q = el["kn-unused-q"].value.trim();
		}
		return params;
	}

	function loadList() {
		if (!target || !list.kind) {
			return;
		}
		var requestNo = ++listRequestNo;
		el["kn-unused-list"].innerHTML = "<p class=\"ai-hint\">읽는 중...</p>";
		K.message(el["kn-unused-message"], "");
		K.call("GET", "/api/revisions/" + target.revisionId + "/unused/" + list.kind, query(list.page, list.size)).then(function (result) {
			if (requestNo !== listRequestNo) {
				return;
			}
			list.total = result.total;
			renderList(result.rows);
			var pages = Math.max(1, Math.ceil(result.total / list.size));
			el["kn-unused-page"].textContent = list.page + " / " + pages + " 쪽 (모두 " + T.fmt(result.total) + "건)";
			el["kn-unused-prev-btn"].disabled = list.page <= 1;
			el["kn-unused-next-btn"].disabled = list.page >= pages;
		}).catch(function (err) {
			if (requestNo === listRequestNo) {
				el["kn-unused-list"].innerHTML = "";
				K.message(el["kn-unused-message"], err.message, true);
			}
		});
	}

	function renderList(rows) {
		K.table(el["kn-unused-list"], [
			{ key: "grade", label: "등급", html: function (row) {
				var grade = T.UNUSED_GRADE[row.grade] || [row.grade, ""];
				return "<span class=\"kn-grade kn-grade-" + K.escapeHtml(row.grade) + "\" title=\"" + K.escapeHtml(grade[1]) + "\">" + K.escapeHtml(grade[0]) + "</span>";
			} },
			{ key: "name", label: "이름", html: function (row) {
				return "<a href=\"" + K.escapeHtml(K.treeUrl(target.projectId, target.revisionId, row.graphId)) + "\" target=\"" + K.TREE_WINDOW
					+ "\" data-tree-window class=\"kn-mono\" title=\"관계 트리 창에서 열기\">" + K.escapeHtml(row.name) + "</a>"
					+ (row.title ? "<div class=\"kn-term\">" + K.escapeHtml(row.title) + "</div>" : "");
			} },
			{ key: "kind", label: "종류", html: function (row) {
				return K.escapeHtml([row.kind, row.layer].filter(function (value) { return value; }).join(" · "));
			} },
			{ key: "path", label: "위치", html: function (row) {
				return row.path ? "<span class=\"kn-mono\">" + K.escapeHtml(row.path + (row.line ? ":" + row.line : "")) + "</span>" : "";
			} },
			{ key: "reason", label: "까닭" }
		], rows, "없습니다.");
	}

	/** 지금 조건의 목록 전부를 CSV 로 내려받는다(엑셀에서 한글이 깨지지 않게 BOM 을 붙인다) */
	function downloadCsv() {
		if (!target || !list.kind) {
			return;
		}
		var kind = list.kind;
		var revisionId = target.revisionId;
		var rows = [];
		var size = 500;
		K.message(el["kn-unused-message"], "내려받을 목록을 모으는 중...");
		var next = function (page) {
			return K.call("GET", "/api/revisions/" + revisionId + "/unused/" + kind, query(page, size)).then(function (result) {
				rows = rows.concat(result.rows);
				if (page * size < result.total) {
					return next(page + 1);
				}
				return rows;
			});
		};
		next(1).then(function (all) {
			var header = ["등급", "이름", "한글명", "종류", "계층", "파일", "줄", "까닭"];
			var lines = [header.map(csvCell).join(",")];
			all.forEach(function (row) {
				lines.push([(T.UNUSED_GRADE[row.grade] || [row.grade])[0], row.name, row.title, row.kind, row.layer, row.path, row.line, row.reason].map(csvCell).join(","));
			});
			var blob = new Blob(["﻿" + lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
			var link = document.createElement("a");
			link.href = URL.createObjectURL(blob);
			link.download = "unused-" + kind.toLowerCase() + "-r" + revisionId + ".csv";
			document.body.appendChild(link);
			link.click();
			document.body.removeChild(link);
			URL.revokeObjectURL(link.href);
			K.message(el["kn-unused-message"], T.fmt(all.length) + "건을 내려받았습니다.");
		}).catch(function (err) {
			K.message(el["kn-unused-message"], err.message, true);
		});
	}

	function csvCell(value) {
		var text = value === null || value === undefined ? "" : String(value);
		return /[",\r\n]/.test(text) ? "\"" + text.replace(/"/g, "\"\"") + "\"" : text;
	}

	/** 지금 탭에 있는 미사용 요약(아직 없으면 null) */
	function summary() {
		return summaryData;
	}

	return { init: init, load: load, close: close, summary: summary, renderOverview: renderOverview };
})();
