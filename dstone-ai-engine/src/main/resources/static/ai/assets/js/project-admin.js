/**
 * 프로젝트관리 화면(/ai/admin/management/project.html). API 는 /api/ai/projects(ProjectController).
 * 줄을 누르면 아래 칸에 채우고, DB 는 여러 줄로 넣고 뺀다. 비밀번호는 서버가 돌려주지 않으므로 비워 두면 그대로 둔다.
 * [Build]는 저장된 설정으로 리비전을 빌드한다(POST /{id}/build). 빌드 중에는 몇 초마다 상태를 다시 읽는다.
 */
(function () {
	var api = DstoneAi.api;
	var esc = DstoneAi.escapeHtml;
	var listBody = document.getElementById("pj-list-body");
	var dbBody = document.getElementById("pj-db-body");
	var status = document.getElementById("pj-status");
	var rows = [];
	var editing = null;
	var FIELDS = ["projectId", "projectName", "description", "sourceDir", "scmType", "scmUrl", "scmUsername", "javaVersion", "wasContainer", "wasVersion", "contextPath",
		"buildType", "buildArgs", "buildWebRoot", "buildClassesDir", "buildLibDir", "buildSourceDirs", "testUserId", "testStaticPaths", "useYn"];
	var COMMANDS = {
		MAVEN: { command: "mvn -B -DskipTests package {인자}", placeholder: "예: -pl web -am (비워도 됨)" },
		GRADLE: { command: "gradlew(없으면 gradle) build -x test {인자}", placeholder: "비워도 됨" },
		ANT: { command: "ant {인자}", placeholder: "target 이름(비우면 기본 target)" },
		SHELL: { command: "{등록된 스크립트} {리비전 소스 경로}", placeholder: "등록된 스크립트 이름(dstone.ai.tool.shell.allowed-commands)" },
		NONE: { command: "엔진이 소스 폴더 전체를 javac로 컴파일합니다", placeholder: "(인자 없음)" }
	};
	var buildRevisions = [];
	var pollTimer = null;
	var buildStatus = document.getElementById("pj-buildStatus");
	var revisionBody = document.getElementById("pj-revision-body");
	var buildLog = document.getElementById("pj-buildLog");

	function say(text, ok) {
		status.textContent = text || "";
		status.className = "workflow-status" + (text ? (ok ? " mg-ok" : " mg-error") : "");
	}

	function load() {
		return api("GET", "/api/ai/projects").then(function (list) {
			rows = list || [];
			document.getElementById("pj-count").textContent = rows.length + "건";
			listBody.innerHTML = rows.length === 0 ? "<tr><td colspan=\"7\">프로젝트가 없습니다.</td></tr>" : rows.map(function (r) {
				var on = editing && editing.projectId === r.projectId;
				return "<tr class=\"ev-row" + (on ? " ev-row-on" : "") + "\" tabindex=\"0\" data-key=\"" + esc(r.projectId) + "\"><td>" + esc(r.projectId) + "</td><td>" + esc(r.projectName)
					+ "</td><td>" + esc(r.sourceDir || "-") + "</td><td>" + esc(r.scmType || "-") + "</td><td>" + esc(r.currentRevision || "-") + "</td><td>" + esc(String(r.dbCount)) + "</td><td>" + (r.useYn === "Y" ? "예" : "아니오") + "</td></tr>";
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
			+ "<td><input type=\"text\" class=\"pj-jndiName\" value=\"" + esc(db.jndiName || "") + "\" placeholder=\"jdbc/myDS\" /></td>"
			+ "<td><input type=\"text\" class=\"pj-dbDescription\" value=\"" + esc(db.description || "") + "\" /></td>"
			+ "<td><button type=\"button\" class=\"kn-plain pj-remove-db\">빼기</button></td></tr>";
	}

	function fill(project) {
		editing = project;
		document.getElementById("pj-form-title").textContent = project ? "고치기: " + project.projectId : "새로 만들기";
		var p = project || { useYn: "Y" };
		FIELDS.forEach(function (key) {
			document.getElementById("pj-" + key).value = p[key] == null ? "" : p[key];
		});
		var scmPassword = document.getElementById("pj-scmPassword");
		scmPassword.value = "";
		scmPassword.placeholder = p.hasScmPassword === "Y" ? "저장됨(바꿀 때만)" : "";
		document.getElementById("pj-currentRevision").textContent = p.currentRevision || "-";
		document.getElementById("pj-build").disabled = !project;
		buildRevisions = [];
		revisionBody.innerHTML = "";
		buildLog.hidden = true;
		buildStatus.textContent = "";
		showBuildType();
		if (project) {
			loadBuild(project.projectId);
		}
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
		FIELDS.forEach(function (key) {
			body[key] = document.getElementById("pj-" + key).value.trim();
		});
		body.testPassword = document.getElementById("pj-testPassword").value;
		body.scmPassword = document.getElementById("pj-scmPassword").value;
		body.dbs = Array.prototype.map.call(dbBody.querySelectorAll("tr"), function (tr) {
			function v(cls) {
				return tr.querySelector("." + cls).value.trim();
			}
			return { dbSeq: tr.getAttribute("data-seq"), dbName: v("pj-dbName"), jdbcUrl: v("pj-jdbcUrl"), username: v("pj-username"),
				password: tr.querySelector(".pj-password").value, schemaName: v("pj-schemaName"), jndiName: v("pj-jndiName"), description: v("pj-dbDescription") };
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

	/* ---------- 빌드 ---------- */

	// 빌드 종류에 따라 실행할 명령을 보여 주고, 직접 넣는 구조 칸을 연다(빌드 없음이거나 마지막 빌드가 구조 입력 필요일 때)
	function showBuildType() {
		var type = document.getElementById("pj-buildType").value;
		var info = COMMANDS[type];
		document.getElementById("pj-buildCommand").textContent = info ? "실행할 명령: " + info.command : "";
		var args = document.getElementById("pj-buildArgs");
		args.placeholder = info ? info.placeholder : "";
		args.disabled = type === "NONE";
		var needInput = buildRevisions.length > 0 && buildRevisions[0].status === "INPUT";
		var manualSaved = ["buildWebRoot", "buildClassesDir", "buildLibDir", "buildSourceDirs"].some(function (key) {
			return document.getElementById("pj-" + key).value.trim() !== "";
		});
		document.getElementById("pj-buildManual").hidden = !(type === "NONE" || needInput || manualSaved);
	}

	var STATUS_TEXT = { BUILDING: "빌드 중", OK: "성공", FAILED: "실패", INPUT: "구조 입력 필요" };

	function loadBuild(projectId) {
		return api("GET", "/api/ai/projects/" + encodeURIComponent(projectId) + "/build").then(function (answer) {
			if (!editing || editing.projectId !== projectId) {
				return;
			}
			buildRevisions = answer.revisions || [];
			buildStatus.textContent = answer.running ? "빌드 중입니다..." : (answer.message || "");
			document.getElementById("pj-build").disabled = !!answer.running;
			revisionBody.innerHTML = buildRevisions.length === 0 ? "<tr><td colspan=\"7\">아직 빌드하지 않았습니다.</td></tr>" : buildRevisions.map(function (r) {
				var current = editing.currentRevision === r.revision;
				return "<tr><td>" + esc(r.revision) + (current ? " (현재)" : "") + "</td><td>" + esc(STATUS_TEXT[r.status] || r.status) + "</td><td>" + esc(r.kind || "-")
					+ "</td><td>" + esc(DstoneAi.formatTime ? DstoneAi.formatTime(r.startedAt) : String(r.startedAt || "")) + "</td><td>" + esc(r.finishedAt ? (DstoneAi.formatTime ? DstoneAi.formatTime(r.finishedAt) : String(r.finishedAt)) : "-")
					+ "</td><td class=\"pj-message\">" + esc((r.message || "").split("\n")[0]) + "</td><td><button type=\"button\" class=\"kn-plain pj-log\" data-rev=\"" + esc(r.revision) + "\">로그</button></td></tr>";
			}).join("");
			showBuildType();
			clearTimeout(pollTimer);
			if (answer.running) {
				pollTimer = setTimeout(function () {
					loadBuild(projectId);
				}, 3000);
			} else if (editing.currentRevision !== (buildRevisions.filter(function (r) { return r.status === "OK"; })[0] || {}).revision) {
				// 빌드가 끝나 현재 리비전이 바뀌었을 수 있으니 프로젝트를 다시 읽는다
				api("GET", "/api/ai/projects/" + encodeURIComponent(projectId)).then(function (project) {
					editing.currentRevision = project.currentRevision;
					document.getElementById("pj-currentRevision").textContent = project.currentRevision || "-";
				});
			}
		}).catch(function (e) {
			buildStatus.textContent = "빌드 상태를 읽지 못했습니다: " + e.message;
		});
	}

	document.getElementById("pj-buildType").addEventListener("change", showBuildType);
	document.getElementById("pj-build").addEventListener("click", function () {
		if (!editing) {
			return;
		}
		var projectId = editing.projectId;
		document.getElementById("pj-build").disabled = true;
		api("POST", "/api/ai/projects/" + encodeURIComponent(projectId) + "/build").then(function (answer) {
			buildStatus.textContent = answer.message || "";
			loadBuild(projectId);
		}).catch(function (e) {
			document.getElementById("pj-build").disabled = false;
			buildStatus.textContent = e.message;
		});
	});
	revisionBody.addEventListener("click", function (event) {
		var button = event.target.closest(".pj-log");
		if (!button || !editing) {
			return;
		}
		api("GET", "/api/ai/projects/" + encodeURIComponent(editing.projectId) + "/revisions/" + encodeURIComponent(button.getAttribute("data-rev")) + "/log").then(function (answer) {
			buildLog.textContent = answer.text || "(로그가 없습니다)";
			buildLog.hidden = false;
		}).catch(function (e) {
			buildStatus.textContent = e.message;
		});
	});

	// Java 버전과 SHELL 스크립트 선택지
	api("GET", "/api/ai/projects/options").then(function (options) {
		var select = document.getElementById("pj-javaVersion");
		var current = select.value;
		(options.javaVersions || []).forEach(function (item) {
			var option = document.createElement("option");
			option.value = item.version;
			option.textContent = item.version + (item.available ? "" : " (이 서버에 JDK 없음)");
			select.appendChild(option);
		});
		select.value = current;
		document.getElementById("pj-shellScripts").innerHTML = (options.shellScripts || []).map(function (name) {
			return "<option value=\"" + esc(name) + "\"></option>";
		}).join("");
		if (editing) {
			select.value = editing.javaVersion || "";
		}
	}).catch(function () {
		// 선택지를 못 읽어도 화면은 쓸 수 있다
	});

	fill(null);
	load();
})();
