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

	/** 파일을 올립니다(multipart). fields 는 같이 보낼 값들: {sourceId, title, projectId} */
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

	/** 상태를 색깔 있는 딱지로 */
	function badge(status) {
		var name = String(status || "unknown").toLowerCase();
		// AI 화면의 딱지 색을 같이 쓴다. READY / DONE_WITH_WARNING 처럼 거기 없는 이름은 가까운 색으로 맞춘다.
		var css = name === "ready" || name === "up" ? "done" : name === "done_with_warning" ? "waiting_approval" : name === "created" ? "cancelled" : name;
		return "<span class=\"workflow-badge workflow-badge-" + escapeHtml(css) + "\">" + escapeHtml(status) + "</span>";
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
	 * 모양을 미리 알 수 없는 값을 표로 그립니다. 리비전 요약처럼 항목이 많은 응답을 통째로 보여 줄 때 씁니다.
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
					+ " (" + escapeHtml(revision.status) + ")</option>";
			});
			selectEl.innerHTML = html;
			return revisions || [];
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
		message: message,
		table: table,
		autoTable: autoTable,
		cards: cards,
		loadProjects: loadProjects,
		loadRevisions: loadRevisions,
		confidence: confidence
	};
})();
