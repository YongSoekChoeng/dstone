/**
 * "코드 분석(Knowledge)" 화면들이 같이 쓰는 도우미입니다.
 *
 * 화면은 dstone-knowledge 가 직접 내려주므로 API(/api/**)도 같은 서버에 바로 부릅니다. 예: call("GET", "/api/projects").
 * 화면을 연 사람(세션)은 common.web.ConfigConsole 의 ConsoleSessionFilter 가 정합니다.
 */
var DstoneKnowledge = (function () {

	var contextPath = "";

	function init(path) {
		contextPath = path || "";
		bindExamples();
		bindTreeLinks();
		watchTables();
	}

	/* ============================== 표 컬럼 너비: 고정 + 끌어서 조정 ============================== */

	/** 너비를 고정할 표. 입력 폼 표(ai-form)는 뺀다 */
	var FIXED_TABLE = "table.document-list-table, table.kn-gd-facts, table.kn-gd-columns";

	/** 컬럼이 이보다 좁아지지 않는다 */
	var MIN_COL = 48;

	/** 처음 잴 때 한 칸이 가질 수 있는 가장 넓은 너비. 긴 경로 하나가 표를 다 차지하지 않게 한다 */
	var MAX_FIRST_COL = 480;

	/** 이 화면에서 정한 너비. 표 머리글이 같으면 다시 그려도(거르기, 정렬, 페이지 이동) 같은 너비를 쓴다 */
	var columnWidths = {};

	/**
	 * <pre>
	 * 화면에 나타나는 목록 표의 컬럼 너비를 고정합니다. 내용에 따라 너비가 바뀌지 않고, 머리글 오른쪽 끝을 끌어 너비를 바꿉니다.
	 *   - 처음 너비는 표가 처음 그려질 때 한 번만 재서 정하고, 그 뒤로는 내용이 바뀌어도 그대로 둔다.
	 *   - 사용자가 바꾼 너비는 화면 · 표 머리글마다 이 브라우저에 기억해 둔다(다음에 열어도 같다).
	 *   - 넘치는 글은 칸 안에서 줄을 바꾼다. 표가 자리보다 넓어지면 표를 감싼 곳에서 가로로 스크롤한다.
	 * 표는 화면마다 여러 방식(K.table, 직접 만든 HTML, JSP)으로 그려지므로, 새로 붙는 표를 지켜보다가 처리합니다.
	 * </pre>
	 */
	function watchTables() {
		var scheduled = false;
		function scan() {
			scheduled = false;
			Array.prototype.forEach.call(document.querySelectorAll(FIXED_TABLE), fixTable);
		}
		function schedule() {
			if (!scheduled) {
				scheduled = true;
				window.setTimeout(scan, 30);
			}
		}
		scan();
		if (window.MutationObserver) {
			// 숨겨 둔 탭이 열릴 때(hidden, class, style)도 다시 본다. 숨은 표는 자리 너비를 알 수 없어 열릴 때 처리한다.
			new MutationObserver(schedule).observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ["hidden", "class", "style"] });
		}
		window.addEventListener("resize", schedule);
	}

	/** 표 하나의 너비를 고정하고 손잡이를 붙인다. 이미 처리했으면 아무것도 안 한다 */
	function fixTable(table) {
		if (table.getAttribute("data-kn-fixed") === "1" || table.classList.contains("ai-form")) {
			return;
		}
		var headers = headerCells(table);
		if (headers.length === 0 || table.offsetParent === null || !hasRows(table)) {
			return;
		}
		var key = widthKey(headers);
		var widths = columnWidths[key] || storedWidths(key) || defaultWidths(table, headers);
		columnWidths[key] = widths;
		var colgroup = document.createElement("colgroup");
		headers.forEach(function (th, index) {
			var col = document.createElement("col");
			col.style.width = widths[index] + "px";
			colgroup.appendChild(col);
			addResizer(table, th, index, key);
		});
		table.insertBefore(colgroup, table.firstChild);
		table.classList.add("kn-fixed-table");
		setTableWidth(table, widths);
		allowSideScroll(table);
		table.setAttribute("data-kn-fixed", "1");
	}

	/** 내용 행이 있는지. "없습니다" 한 줄(칸을 합친 행)만 있으면 아직 재지 않는다 */
	function hasRows(table) {
		var body = table.tBodies[0];
		if (!body || body.rows.length === 0) {
			return false;
		}
		return !(body.rows.length === 1 && body.rows[0].cells.length === 1 && body.rows[0].cells[0].colSpan > 1);
	}

	/** 맨 위 머리글 줄의 칸들. 머리글이 없거나 칸을 합친 표는 다루지 않는다(빈 배열) */
	function headerCells(table) {
		var row = table.tHead && table.tHead.rows.length === 1 ? table.tHead.rows[0] : null;
		if (!row) {
			return [];
		}
		var cells = Array.prototype.slice.call(row.cells);
		for (var i = 0; i < cells.length; i++) {
			if (cells[i].colSpan > 1) {
				return [];
			}
		}
		return cells;
	}

	/** 화면 + 머리글 글자로 만든 이름. 같은 머리글의 표는 같은 너비를 쓴다 */
	function widthKey(headers) {
		var labels = headers.map(function (th) {
			return (th.textContent || "").trim();
		});
		return "kn-colw:" + location.pathname + location.search.replace(/[?&](p|r|t)=[^&]*/g, "") + "|" + labels.join("|");
	}

	function storedWidths(key) {
		try {
			var text = window.localStorage.getItem(key);
			var widths = text ? JSON.parse(text) : null;
			return Array.isArray(widths) ? widths : null;
		} catch (e) {
			return null;
		}
	}

	function storeWidths(key, widths) {
		try {
			window.localStorage.setItem(key, JSON.stringify(widths));
		} catch (e) {
			// 저장할 수 없는 브라우저(개인 정보 보호 모드 등). 이번 화면에서만 기억한다.
		}
	}

	/**
	 * 처음 너비. 표가 처음 그려졌을 때 한 번만 재고, 그 뒤로는 바꾸지 않는다(거르기 · 다시 그리기로 너비가 바뀌지 않게).
	 *   1) 지금 그려진 대로 칸마다 너비를 잰다(한 칸은 MAX_FIRST_COL 까지).
	 *   2) 합이 자리보다 좁으면, 숫자 칸을 뺀 칸들을 비율대로 넓혀 자리를 채운다.
	 */
	function defaultWidths(table, headers) {
		var space = (table.parentElement && table.parentElement.clientWidth) || 960;
		var widths = headers.map(function (th) {
			return Math.max(MIN_COL, Math.min(MAX_FIRST_COL, Math.ceil(th.getBoundingClientRect().width)));
		});
		var total = sum(widths);
		var flexible = 0;
		headers.forEach(function (th, index) {
			if (!th.classList.contains("kn-num")) {
				flexible += widths[index];
			}
		});
		if (total < space - 2 && flexible > 0) {
			var extra = space - 2 - total;
			widths = widths.map(function (width, index) {
				return headers[index].classList.contains("kn-num") ? width : width + Math.floor(extra * width / flexible);
			});
		}
		return widths;
	}

	function sum(values) {
		var total = 0;
		values.forEach(function (value) {
			total += value;
		});
		return total;
	}

	/** 표 너비 = 컬럼 너비의 합. 자리보다 넓으면 가로 스크롤이 생긴다 */
	function setTableWidth(table, widths) {
		var total = sum(widths);
		table.style.width = total + "px";
		table.style.minWidth = total + "px";
	}

	/** 표를 감싼 곳이 넘치는 표를 잘라 버리지 않고 가로로 스크롤하게 한다 */
	function allowSideScroll(table) {
		var parent = table.parentElement;
		if (parent && window.getComputedStyle(parent).overflowX === "visible") {
			parent.style.overflowX = "auto";
		}
	}

	/** 머리글 오른쪽 끝에 끌어서 너비를 바꾸는 손잡이를 붙인다. 두 번 누르면 처음 너비로 돌아간다 */
	function addResizer(table, th, index, key) {
		var handle = document.createElement("span");
		handle.className = "kn-col-resizer";
		handle.setAttribute("aria-hidden", "true");
		handle.title = "끌어서 너비 조정 (두 번 누르면 처음 너비)";
		handle.addEventListener("mousedown", function (event) {
			if (event.button !== 0) {
				return;
			}
			event.preventDefault();
			event.stopPropagation();
			var widths = columnWidths[key];
			var startX = event.clientX;
			var startWidth = widths[index];
			var col = table.querySelector("colgroup").children[index];
			document.body.classList.add("kn-col-resizing");
			function move(e) {
				widths[index] = Math.max(MIN_COL, Math.round(startWidth + e.clientX - startX));
				col.style.width = widths[index] + "px";
				setTableWidth(table, widths);
			}
			function up() {
				document.removeEventListener("mousemove", move);
				document.removeEventListener("mouseup", up);
				document.body.classList.remove("kn-col-resizing");
				storeWidths(key, widths);
				applyWidths(key, table);
			}
			document.addEventListener("mousemove", move);
			document.addEventListener("mouseup", up);
		});
		// 손잡이를 눌러도 머리글 정렬 등 다른 동작이 일어나지 않게 한다.
		handle.addEventListener("click", function (event) {
			event.stopPropagation();
		});
		handle.addEventListener("dblclick", function (event) {
			event.stopPropagation();
			try {
				window.localStorage.removeItem(key);
			} catch (e) {
				// 지울 수 없으면 그대로 둔다.
			}
			columnWidths[key] = defaultWidths(table, headerCells(table));
			applyWidths(key, null);
		});
		th.appendChild(handle);
	}

	/** 같은 머리글의 다른 표(같은 화면에 여럿일 때)에도 너비를 맞춘다. except 는 이미 맞춘 표 */
	function applyWidths(key, except) {
		var widths = columnWidths[key];
		Array.prototype.forEach.call(document.querySelectorAll("table.kn-fixed-table"), function (table) {
			if (table === except || widthKey(headerCells(table)) !== key) {
				return;
			}
			var cols = table.querySelector("colgroup").children;
			for (var i = 0; i < cols.length && i < widths.length; i++) {
				cols[i].style.width = widths[i] + "px";
			}
			setTableWidth(table, widths);
		});
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
		var params = new URLSearchParams();
		Object.keys(query || {}).forEach(function (key) {
			var value = query[key];
			// 값이 비어 있는 것은 붙이지 않는다
			if (value !== null && value !== undefined && String(value).trim() !== "") {
				params.append(key, String(value).trim());
			}
		});
		var url = contextPath + path + (params.toString() ? "?" + params.toString() : "");
		var options = { method: method, headers: { "Accept": "application/json" } };
		if (body && method === "POST") {
			options.headers["Content-Type"] = "application/json";
			options.body = JSON.stringify(body);
		}
		return fetch(url, options).then(readResponse);
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
		return fetch(contextPath + "/api/documents", { method: "POST", body: form }).then(readResponse);
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

	/** 다른 화면의 주소. link 는 화면 경로(예: knowledge/graph/graph), hash 는 # 뒤에 붙일 글(없으면 빈 글) */
	function pageUrl(link, hash) {
		return contextPath + "/" + link + ".html" + (hash ? "#" + hash : "");
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
