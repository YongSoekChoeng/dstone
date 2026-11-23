/**
 * 프로젝트관리 화면(/ai/admin/management/project.html). API 는 /api/ai/projects(ProjectController).
 * 줄을 누르면 아래 칸에 채우고, DB 는 여러 줄로 넣고 뺀다. 비밀번호는 서버가 돌려주지 않으므로 비워 두면 그대로 둔다.
 */
(function () {
	var api = DstoneAi.api;
	var esc = DstoneAi.escapeHtml;
	var listBody = document.getElementById("pj-list-body");
	var dbBody = document.getElementById("pj-db-body");
	var status = document.getElementById("pj-status");
	var rows = [];
	var editing = null;

	function say(text, ok) {
		status.textContent = text || "";
		status.className = "workflow-status" + (text ? (ok ? " mg-ok" : " mg-error") : "");
	}

	function load() {
		return api("GET", "/api/ai/projects").then(function (list) {
			rows = list || [];
			document.getElementById("pj-count").textContent = rows.length + "건";
			listBody.innerHTML = rows.length === 0 ? "<tr><td colspan=\"6\">프로젝트가 없습니다.</td></tr>" : rows.map(function (r) {
				var on = editing && editing.projectId === r.projectId;
				return "<tr class=\"ev-row" + (on ? " ev-row-on" : "") + "\" tabindex=\"0\" data-key=\"" + esc(r.projectId) + "\"><td>" + esc(r.projectId) + "</td><td>" + esc(r.projectName)
					+ "</td><td>" + esc(r.sourceDir || "-") + "</td><td>" + esc(r.scmType || "-") + "</td><td>" + esc(String(r.dbCount)) + "</td><td>" + (r.useYn === "Y" ? "예" : "아니오") + "</td></tr>";
			}).join("");
		}).catch(function (e) {
			say("목록을 읽지 못했습니다: " + e.message, false);
		});
	}

	function dbRow(db) {
		db = db || {};
		return "<tr data-seq=\"" + esc(db.dbSeq == null ? "" : db.dbSeq) + "\">"
			+ "<td><input type=\"text\" class=\"pj-dbName\" value=\"" + esc(db.dbName || "") + "\" placeholder=\"main\" /></td>"
			+ "<td><input type=\"text\" class=\"pj-jdbcUrl\" value=\"" + esc(db.jdbcUrl || "") + "\" placeholder=\"jdbc:mysql://host:3306/db\" /></td>"
			+ "<td><input type=\"text\" class=\"pj-username\" value=\"" + esc(db.username || "") + "\" /></td>"
			+ "<td><input type=\"password\" class=\"pj-password\" autocomplete=\"new-password\" placeholder=\"" + (db.hasPassword === "Y" ? "저장됨(바꿀 때만)" : "") + "\" /></td>"
			+ "<td><input type=\"text\" class=\"pj-schemaName\" value=\"" + esc(db.schemaName || "") + "\" /></td>"
			+ "<td><input type=\"text\" class=\"pj-dbDescription\" value=\"" + esc(db.description || "") + "\" /></td>"
			+ "<td><button type=\"button\" class=\"kn-plain pj-remove-db\">빼기</button></td></tr>";
	}

	function fill(project) {
		editing = project;
		document.getElementById("pj-form-title").textContent = project ? "고치기: " + project.projectId : "새로 만들기";
		var p = project || { useYn: "Y" };
		["projectId", "projectName", "description", "sourceDir", "scmType", "scmUrl", "javaVersion", "testUserId", "testStaticPaths", "useYn"].forEach(function (key) {
			document.getElementById("pj-" + key).value = p[key] == null ? "" : p[key];
		});
		var testPassword = document.getElementById("pj-testPassword");
		testPassword.value = "";
		testPassword.placeholder = p.hasTestPassword === "Y" ? "저장됨(바꿀 때만)" : "";
		document.getElementById("pj-projectId").readOnly = !!project;
		dbBody.innerHTML = (p.dbs || []).map(dbRow).join("");
		document.getElementById("pj-delete").hidden = !project;
	}

	function pick(projectId) {
		api("GET", "/api/ai/projects/" + encodeURIComponent(projectId)).then(function (project) {
			fill(project);
			say("");
			load();
		}).catch(function (e) {
			say(e.message, false);
		});
	}

	listBody.addEventListener("click", function (event) {
		var row = event.target.closest("tr.ev-row");
		if (row) {
			pick(row.getAttribute("data-key"));
		}
	});
	listBody.addEventListener("keydown", function (event) {
		var row = event.target.closest("tr.ev-row");
		if (row && (event.key === "Enter" || event.key === " ")) {
			event.preventDefault();
			pick(row.getAttribute("data-key"));
		}
	});
	document.getElementById("pj-add-db").addEventListener("click", function () {
		dbBody.insertAdjacentHTML("beforeend", dbRow(null));
	});
	dbBody.addEventListener("click", function (event) {
		if (event.target.classList.contains("pj-remove-db")) {
			event.target.closest("tr").remove();
		}
	});
	document.getElementById("pj-new").addEventListener("click", function () {
		fill(null);
		say("");
		load();
	});

	document.getElementById("pj-form").addEventListener("submit", function (event) {
		event.preventDefault();
		var body = {};
		["projectId", "projectName", "description", "sourceDir", "scmType", "scmUrl", "javaVersion", "testUserId", "testStaticPaths", "useYn"].forEach(function (key) {
			body[key] = document.getElementById("pj-" + key).value.trim();
		});
		body.testPassword = document.getElementById("pj-testPassword").value;
		body.dbs = Array.prototype.map.call(dbBody.querySelectorAll("tr"), function (tr) {
			function v(cls) {
				return tr.querySelector("." + cls).value.trim();
			}
			return { dbSeq: tr.getAttribute("data-seq"), dbName: v("pj-dbName"), jdbcUrl: v("pj-jdbcUrl"), username: v("pj-username"),
				password: tr.querySelector(".pj-password").value, schemaName: v("pj-schemaName"), description: v("pj-dbDescription") };
		});
		api("POST", "/api/ai/projects", body).then(function () {
			say("저장했습니다.", true);
			pick(body.projectId);
		}).catch(function (e) {
			say(e.message, false);
		});
	});

	document.getElementById("pj-delete").addEventListener("click", function () {
		if (!editing || !confirm("프로젝트 '" + editing.projectId + "'와 DB 정보를 지울까요? 되돌릴 수 없습니다.")) {
			return;
		}
		api("DELETE", "/api/ai/projects/" + encodeURIComponent(editing.projectId)).then(function () {
			fill(null);
			say("지웠습니다.", true);
			load();
		}).catch(function (e) {
			say(e.message, false);
		});
	});

	fill(null);
	load();
})();
