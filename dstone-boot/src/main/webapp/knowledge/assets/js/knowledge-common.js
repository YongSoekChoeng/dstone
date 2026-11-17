/**
 * "코드 분석(Knowledge)" 화면들이 같이 쓰는 도우미입니다.
 *
 * dstone-knowledge 의 API 는 브라우저가 직접 부르지 않고 dstone-boot 의 /knowledge/api/call.do 를 거칩니다
 * (knowledge.controller.KnowledgeProxyController). 그래서 화면에서는 call("GET", "/api/projects") 처럼 부릅니다.
 */
var DstoneKnowledge = (function () {

	var contextPath = "";

	function init(path) {
		contextPath = path || "";
		bindExamples();
		bindTreeLinks();
	}

	/**
	 * 예시 단추(.kn-example)를 누르면 입력 칸에 그 값을 채웁니다.
	 * 단추의 data-fill 에 {"입력 칸 id": "넣을 값"} 을 JSON 으로 적어 둡니다. 여러 칸을 한 번에 채울 수 있습니다.
	 */
	function bindExamples() {
		Array.prototype.forEach.call(document.querySelectorAll(".kn-example"), function (button) {
			button.addEventListener("click", function () {
				var fill = JSON.parse(button.getAttribute("data-fill") || "{}");
				Object.keys(fill).forEach(function (id) {
					var target = document.getElementById(id);
					if (target) {
						target.value = fill[id];
					}
				});
			});
		});
	}

	/**
	 * dstone-knowledge 의 API 하나를 부릅니다.
	 * 성공하면 응답 JSON 을, 실패하면(4xx/5xx) 서버가 준 message 를 담은 Error 를 돌려줍니다.
	 */
	function call(method, path, query, body) {
		return fetch(contextPath + "/knowledge/api/call.do", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ method: method, path: path, query: query || null, body: body || null })
		}).then(readResponse);
	}

	/** 파일을 올립니다(multipart). fields 는 같이 보낼 값들: {sourceId, title, collection} */
	function upload(file, fields) {
		var form = new FormData();
		form.append("file", file);
		Object.keys(fields || {}).forEach(function (key) {
			if (fields[key]) {
				form.append(key, fields[key]);
			}
		});
		return fetch(contextPath + "/knowledge/api/upload.do", { method: "POST", body: form }).then(readResponse);
	}

	function readResponse(response) {
		return response.text().then(function (text) {
			var data = null;
			try {
				data = text ? JSON.parse(text) : null;
			} catch (e) {
				// JSON 이 아니면(로그인 화면으로 넘어간 경우 등) 글자 그대로 보여 준다.
				data = { message: text.substring(0, 300) };
			}
			if (!response.ok) {
				throw new Error((data && data.message) || ("HTTP " + response.status));
			}
			return data;
		});
	}

	function escapeHtml(value) {
		if (value === null || value === undefined) {
			return "";
		}
		return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
	}

	/**
	 * 상태 딱지에 보이는 한글 이름. 서버가 주는 코드(READY, DONE_WITH_WARNING ...)는 딱지에 마우스를 올리면 보인다.
	 * READY 는 리비전에서는 "분석 완료", 분석 작업에서는 "대기"라서 작업 것은 따로 둔다.
	 */
	var BADGE_LABELS = {
		up: "정상", down: "연결 안 됨", db_down: "DB 연결 안 됨", schema_not_ready: "스키마 없음",
		created: "만듦", analyzing: "분석 중", ready: "분석 완료", failed: "실패",
		running: "진행 중", done: "완료", done_with_warning: "완료(경고)", cancelled: "취소됨", ok: "성공"
	};
	var JOB_BADGE_LABELS = { ready: "대기" };

	/**
	 * 상태를 색깔 있는 딱지로
	 * @param status 서버가 주는 상태 코드
	 * @param kind "job" 이면 분석 작업의 상태(READY = 대기). 그 밖에는 리비전 · 서버 상태
	 */
	function badge(status, kind) {
		var name = String(status || "unknown").toLowerCase();
		var job = kind === "job";
		// AI 화면의 딱지 색을 같이 쓴다. READY / DONE_WITH_WARNING 처럼 거기 없는 이름은 가까운 색으로 맞춘다.
		var css = name === "ready" ? (job ? "queued" : "done") : name === "up" ? "done" : name === "done_with_warning" ? "waiting_approval"
			: name === "created" ? "cancelled" : name === "analyzing" ? "running"
			: name === "down" || name === "db_down" || name === "schema_not_ready" ? "failed" : name;
		var label = (job && JOB_BADGE_LABELS[name]) || BADGE_LABELS[name] || status;
		return "<span class=\"workflow-badge workflow-badge-" + escapeHtml(css) + "\" title=\"" + escapeHtml(status) + "\">" + escapeHtml(label) + "</span>";
	}

	/** 상태 코드의 한글 이름(딱지 없이 글자만 쓸 때). kind 는 badge() 와 같다 */
	function statusLabel(status, kind) {
		var name = String(status || "").toLowerCase();
		return (kind === "job" && JOB_BADGE_LABELS[name]) || BADGE_LABELS[name] || status || "";
	}

	/**
	 * 계층 흐름 띠의 칸. 노드 맵의 계층과 같은 순서 · 이름 · 색입니다(graph-common.js TIERS, knowledge.css --kn-layer-*).
	 *   key  계층별 개수의 이름(GET /api/dashboard 의 projects[], GET /api/revisions/{id}/structure 의 layers)
	 *   unit 숫자 밑에 붙는 설명
	 */
	var LAYER_STRIP = [
		{ key: "screens", layer: "view", label: "화면", unit: "화면 파일" },
		{ key: "controllers", layer: "controller", label: "컨트롤러", unit: "클래스" },
		{ key: "services", layer: "service", label: "서비스", unit: "클래스" },
		{ key: "daos", layer: "dao", label: "DAO", unit: "클래스" },
		{ key: "sqlStatements", layer: "sql", label: "SQL", unit: "" },
		{ key: "tables", layer: "table", label: "테이블", unit: "SQL 에 나오는 테이블" }
	];

	/**
	 * 화면 → 컨트롤러 → 서비스 → DAO → SQL → 테이블 개수를 화살 모양 띠로 그린 HTML.
	 * mapUrl 이 있으면 칸마다 그 주소(노드 맵)로 가는 링크가 됩니다. 0개인 칸은 흐리게 그립니다.
	 */
	function layerStrip(counts, mapUrl) {
		var html = "<div class=\"kn-strip\" role=\"list\" aria-label=\"계층별 개수\">";
		LAYER_STRIP.forEach(function (step) {
			var count = Number(counts[step.key]) || 0;
			var unit = step.key === "sqlStatements" ? "문 · 매퍼 파일 " + (Number(counts.mappers) || 0).toLocaleString() + "개" : step.unit;
			var inner = "<span class=\"kn-strip-label\">" + escapeHtml(step.label) + "</span><span class=\"kn-strip-num\">" + count.toLocaleString()
				+ "</span><span class=\"kn-strip-unit\">" + escapeHtml(unit) + "</span>";
			html += "<div role=\"listitem\" class=\"kn-strip-step kn-layer-" + step.layer + (count === 0 ? " kn-strip-zero" : "") + "\">"
				+ (mapUrl ? "<a href=\"" + escapeHtml(mapUrl) + "\" title=\"노드 맵에서 보기\">" + inner + "</a>" : "<span class=\"kn-strip-box\">" + inner + "</span>")
				+ "</div>";
		});
		return html + "</div>";
	}

	/** 처리 결과나 오류를 한 줄로 알립니다 */
	function message(el, text, isError) {
		el.textContent = text || "";
		el.className = "kn-message" + (isError ? " kn-error" : "");
	}

	/**
	 * 행 목록을 표로 그립니다.
	 * columns: [{key, label, html?(row) → 칸에 넣을 HTML, num?: 숫자 칸(오른쪽 정렬), mono?: 고정폭 글자}]
	 */
	function table(el, columns, rows, emptyText) {
		var html = "<table class=\"document-list-table\"><thead><tr>";
		columns.forEach(function (column) {
			html += "<th" + (column.num ? " class=\"kn-num\"" : "") + ">" + escapeHtml(column.label) + "</th>";
		});
		html += "</tr></thead><tbody>";
		if (!rows || rows.length === 0) {
			html += "<tr><td colspan=\"" + columns.length + "\" class=\"ai-hint\">" + escapeHtml(emptyText || "없습니다.") + "</td></tr>";
		}
		(rows || []).forEach(function (row, index) {
			html += "<tr data-index=\"" + index + "\">";
			columns.forEach(function (column) {
				var css = (column.num ? "kn-num " : "") + (column.mono ? "kn-mono" : "");
				var value = column.html ? column.html(row) : escapeHtml(formatValue(row[column.key]));
				html += "<td" + (css ? " class=\"" + css + "\"" : "") + ">" + value + "</td>";
			});
			html += "</tr>";
		});
		html += "</tbody></table>";
		el.innerHTML = html;
	}

	/**
	 * 모양을 미리 알 수 없는 값을 표로 그립니다. 진단 보기처럼 모양이 정해지지 않은 응답을 통째로 보여 줄 때 씁니다.
	 *   - 객체의 배열 → 키가 열인 표
	 *   - 객체 → "이름 | 값" 표
	 */
	function autoTable(el, value) {
		if (Array.isArray(value)) {
			var keys = [];
			value.forEach(function (row) {
				Object.keys(row || {}).forEach(function (key) {
					if (keys.indexOf(key) < 0) {
						keys.push(key);
					}
				});
			});
			var columns = keys.map(function (key) {
				return { key: key, label: key, num: value.length > 0 && typeof value[0][key] === "number" };
			});
			table(el, columns, value);
			return;
		}
		var rows = Object.keys(value || {}).map(function (key) {
			return { name: key, value: value[key] };
		});
		table(el, [{ key: "name", label: "항목" }, { key: "value", label: "값" }], rows);
	}

	function formatValue(value) {
		if (value === null || value === undefined) {
			return "";
		}
		if (typeof value === "number") {
			return value.toLocaleString();
		}
		if (typeof value === "object") {
			return JSON.stringify(value);
		}
		return String(value);
	}

	/** 숫자 카드 몇 개를 나란히 그립니다. cards: [{label, value}] */
	function cards(el, items) {
		el.innerHTML = items.map(function (item) {
			return "<div class=\"kn-card\"><div class=\"kn-card-value\">" + escapeHtml(formatValue(item.value)) + "</div><div class=\"kn-card-label\">"
				+ escapeHtml(item.label) + "</div></div>";
		}).join("");
	}

	/** 프로젝트 목록을 고르는 칸에 채웁니다. withEmpty 가 있으면 맨 위에 그 글자의 빈 항목을 둡니다 */
	function loadProjects(selectEl, withEmpty) {
		return call("GET", "/api/projects").then(function (projects) {
			var html = withEmpty ? "<option value=\"\">" + escapeHtml(withEmpty) + "</option>" : "";
			(projects || []).forEach(function (project) {
				html += "<option value=\"" + escapeHtml(project.projectId) + "\">" + escapeHtml(project.projectId)
					+ (project.projectName && project.projectName !== project.projectId ? " - " + escapeHtml(project.projectName) : "") + "</option>";
			});
			selectEl.innerHTML = html;
			return projects || [];
		});
	}

	/**
	 * 프로젝트의 리비전을 고르는 칸에 채웁니다(최근 것이 위). 분석이 끝난(READY) 가장 최근 것이 골라집니다.
	 * withEmpty 가 있으면 맨 위에 그 글자의 빈 항목을 두고 그것이 골라집니다.
	 */
	function loadRevisions(projectId, selectEl, withEmpty) {
		if (!projectId) {
			selectEl.innerHTML = withEmpty ? "<option value=\"\">" + escapeHtml(withEmpty) + "</option>" : "";
			return Promise.resolve([]);
		}
		return call("GET", "/api/projects/" + encodeURIComponent(projectId) + "/revisions").then(function (revisions) {
			var html = withEmpty ? "<option value=\"\">" + escapeHtml(withEmpty) + "</option>" : "";
			var selected = false;
			(revisions || []).forEach(function (revision) {
				var pick = !withEmpty && !selected && revision.status === "READY";
				selected = selected || pick;
				html += "<option value=\"" + revision.revisionId + "\"" + (pick ? " selected" : "") + ">#" + revision.revisionId + " " + escapeHtml(revision.revisionLabel)
					+ " (" + escapeHtml(statusLabel(revision.status)) + ")</option>";
			});
			selectEl.innerHTML = html;
			return revisions || [];
		});
	}

	/** 다른 화면의 주소. link 는 defaultLink 값(예: knowledge/graph/graph), hash 는 # 뒤에 붙일 글(없으면 빈 글) */
	function pageUrl(link, hash) {
		return contextPath + "/defaultLink.do?defaultLink=" + encodeURIComponent(link) + (hash ? "#" + hash : "");
	}

	/** 주소의 #키=값&키=값 을 {키: 값} 으로 읽는다 */
	function readHash() {
		var state = {};
		location.hash.replace(/^#/, "").split("&").forEach(function (pair) {
			var at = pair.indexOf("=");
			if (at > 0) {
				state[pair.substring(0, at)] = decodeURIComponent(pair.substring(at + 1));
			}
		});
		return state;
	}

	/* ======================= 관계 트리 창 ======================= */

	/** 관계 트리 창의 이름. 모든 화면이 이 이름 하나를 같이 쓰므로, 창은 늘 하나만 뜨고 새로 열면 내용만 바뀐다 */
	var TREE_WINDOW = "dstoneKnowledgeTree";

	/** 그 노드의 관계 트리를 그리는 창의 주소. projectId 는 창 제목에 보이려고 같이 넘긴다 */
	function treeUrl(projectId, revisionId, nodeId) {
		return pageUrl("knowledge/graph/tree", "p=" + encodeURIComponent(projectId || "") + "&r=" + encodeURIComponent(revisionId)
			+ "&tree=" + encodeURIComponent(nodeId));
	}

	/**
	 * <pre>
	 * 관계 트리 창을 열거나, 이미 떠 있으면 그 창에 주소를 넣고 앞으로 꺼낸다.
	 * 브라우저가 팝업을 막으면 false 를 돌려준다.
	 * </pre>
	 */
	function openTreeWindow(url) {
		var width = Math.min(Math.max(screen.availWidth - 80, 900), 1700);
		var height = Math.min(Math.max(screen.availHeight - 80, 600), 1100);
		var features = "width=" + width + ",height=" + height + ",left=40,top=20,resizable=yes,scrollbars=yes";
		var win = window.open(url, TREE_WINDOW, features);
		if (!win) {
			return false;
		}
		win.focus();
		return true;
	}

	/**
	 * <pre>
	 * data-tree-window 가 붙은 링크(&lt;a href="treeUrl(...)" data-tree-window&gt;)를 누르면 관계 트리 창으로 연다.
	 * 링크라서 가운데 단추나 Ctrl 을 누른 채로 누르면 브라우저가 하던 대로 새 탭에서 열린다.
	 * 표를 다시 그려도 따로 잇지 않아도 되게 문서 전체에서 한 번만 받는다.
	 * </pre>
	 */
	function bindTreeLinks() {
		document.addEventListener("click", function (event) {
			var link = event.target.closest ? event.target.closest("a[data-tree-window]") : null;
			if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
				return;
			}
			if (openTreeWindow(link.href)) {
				event.preventDefault();
			}
			// 팝업이 막혔으면 막지 않는다. 링크의 target 대로 새 탭에서 열린다.
		});
	}

	/** 신뢰도(HIGH / MEDIUM / LOW)를 색깔 글자로 */
	function confidence(value) {
		return "<span class=\"kn-conf-" + escapeHtml(value) + "\">" + escapeHtml(value) + "</span>";
	}

	return {
		init: init,
		call: call,
		upload: upload,
		escapeHtml: escapeHtml,
		badge: badge,
		statusLabel: statusLabel,
		layerStrip: layerStrip,
		message: message,
		table: table,
		autoTable: autoTable,
		cards: cards,
		loadProjects: loadProjects,
		loadRevisions: loadRevisions,
		pageUrl: pageUrl,
		readHash: readHash,
		TREE_WINDOW: TREE_WINDOW,
		treeUrl: treeUrl,
		openTreeWindow: openTreeWindow,
		confidence: confidence
	};
})();
