/**
 * "프로젝트" 화면(knowledge/project/project).
 *
 * 주소의 # 로 세 가지 모습을 오갑니다. 뒤로 가기도 그대로 됩니다.
 *   (없음)               프로젝트 목록. GET /api/projects + GET /api/dashboard(계층별 개수)
 *   #p=ID&r=리비전        프로젝트 홈. GET /api/revisions/{r}/structure 한 번으로 신뢰도 · 현행 구조를 그린다
 *   #new, #p=ID&edit=1   프로젝트 등록 / 설정. 저장은 POST /api/projects(dbConnections 포함)
 */
var DstoneKnowledgeProjectHome = (function () {

	var K = DstoneKnowledge;
	var el = {};

	/** 지금 보고 있는 프로젝트와 그 응답(설정 화면에서 그대로 다시 보낼 값이 들어 있다) */
	var current = { projectId: null, project: null, revisionId: null, structure: null };

	/** 늦게 온 응답이 새 화면을 덮지 않게 요청마다 번호를 붙인다 */
	var requestNo = 0;

	/** 호출 해석률 눈금. 대시보드와 같은 기준 */
	var RATE_GOOD = 95;
	var RATE_WARN = 80;

	function init() {
		["kn-proj-title", "kn-proj-sub", "kn-proj-new", "kn-proj-run", "kn-proj-edit", "kn-proj-message", "kn-proj-list-view", "kn-proj-list",
			"kn-proj-home", "kn-proj-back", "kn-proj-revision", "kn-proj-trust", "kn-proj-empty", "kn-proj-structure", "kn-proj-strip", "kn-proj-kinds",
			"kn-proj-area-note", "kn-proj-areas", "kn-proj-area-csv", "kn-proj-form-view", "kn-proj-form-title", "kn-pf-id", "kn-pf-name", "kn-pf-path",
			"kn-pf-classpath", "kn-pf-desc", "kn-pf-dbs", "kn-pf-db-add", "kn-pf-message", "kn-pf-cancel", "kn-pf-save"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		el["kn-proj-new"].addEventListener("click", function () { go("new=1"); });
		el["kn-proj-edit"].addEventListener("click", function () { go("p=" + encodeURIComponent(current.projectId) + "&edit=1"); });
		el["kn-proj-run"].addEventListener("click", runAnalysis);
		el["kn-proj-back"].addEventListener("click", function (event) {
			event.preventDefault();
			go("");
		});
		el["kn-proj-revision"].addEventListener("change", function () {
			var revisionId = el["kn-proj-revision"].value;
			history.replaceState(null, "", "#p=" + encodeURIComponent(current.projectId) + "&r=" + revisionId);
			loadStructure(revisionId);
		});
		el["kn-proj-area-csv"].addEventListener("click", downloadAreas);
		el["kn-pf-db-add"].addEventListener("click", function () { addDbRow({}); });
		el["kn-pf-cancel"].addEventListener("click", function () { history.back(); });
		el["kn-pf-save"].addEventListener("click", save);
		el["kn-pf-dbs"].addEventListener("click", onDbRowClick);
		window.addEventListener("hashchange", route);
		route();
	}

	/** # 뒤를 바꿔 다른 모습으로 간다(뒤로 가기에 남는다) */
	function go(hash) {
		if (location.hash.replace(/^#/, "") === hash) {
			route();
		} else {
			location.hash = hash;
		}
	}

	function route() {
		var state = K.readHash();
		K.message(el["kn-proj-message"], "");
		if (state["new"]) {
			showForm(null);
		} else if (state.p && state.edit) {
			K.call("GET", "/api/projects/" + encodeURIComponent(state.p)).then(showForm).catch(fail);
		} else if (state.p) {
			showHome(state.p, state.r);
		} else {
			showList();
		}
	}

	/** 세 모습 가운데 하나만 보이게 하고, 제목 줄의 단추를 맞춘다 */
	function show(view) {
		el["kn-proj-list-view"].hidden = view !== "list";
		el["kn-proj-home"].hidden = view !== "home";
		el["kn-proj-form-view"].hidden = view !== "form";
		el["kn-proj-new"].hidden = view !== "list";
		el["kn-proj-run"].hidden = view !== "home";
		el["kn-proj-edit"].hidden = view !== "home";
	}

	function fail(err) {
		K.message(el["kn-proj-message"], err.message, true);
	}

	/* ======================= 목록 ======================= */

	function showList() {
		show("list");
		current = { projectId: null, project: null, revisionId: null, structure: null };
		el["kn-proj-title"].textContent = "프로젝트";
		el["kn-proj-sub"].textContent = "분석한 애플리케이션마다 화면에서 테이블까지 어떻게 이어져 있는지, 업무 영역별로 얼마나 큰지 봅니다.";
		var no = ++requestNo;
		Promise.all([K.call("GET", "/api/projects"), K.call("GET", "/api/dashboard")]).then(function (results) {
			if (no !== requestNo) {
				return;
			}
			var counts = {};
			(results[1].projects || []).forEach(function (row) {
				counts[row.projectId] = row;
			});
			renderList(results[0] || [], counts);
		}).catch(fail);
	}

	function renderList(projects, counts) {
		if (projects.length === 0) {
			el["kn-proj-list"].innerHTML = "<div class=\"kn-proj-empty\"><p>아직 등록한 프로젝트가 없습니다.</p>"
				+ "<p class=\"ai-hint\">오른쪽 위 <b>프로젝트 등록</b>을 눌러 소스 폴더를 적고, 저장한 뒤 <b>분석 실행</b>을 누르면 몇 분 안에 현행 구조가 나옵니다.</p></div>";
			return;
		}
		el["kn-proj-list"].innerHTML = projects.map(function (project) {
			var row = counts[project.projectId] || {};
			var hash = "p=" + encodeURIComponent(project.projectId) + (row.readyRevisionId ? "&r=" + row.readyRevisionId : "");
			var name = project.projectName && project.projectName !== project.projectId
				? " <span class=\"kn-flow-alias\">" + K.escapeHtml(project.projectName) + "</span>" : "";
			var facts = row.readyRevisionId
				? fact("화면", row.screens) + fact("진입점", row.endpoints) + fact("SQL 문", row.sqlStatements) + fact("테이블", row.tables)
					+ "<span class=\"kn-proj-when\">" + K.escapeHtml(row.readyCreatedAt) + " 분석</span>"
				: "<span class=\"kn-proj-when\">" + (row.latestRevisionId ? K.statusLabel(row.latestStatus) : "아직 분석하지 않음") + "</span>";
			return "<a class=\"kn-proj-item\" href=\"#" + K.escapeHtml(hash) + "\"><div class=\"kn-proj-item-name\"><b>" + K.escapeHtml(project.projectId) + "</b>" + name + "</div>"
				+ (project.description ? "<div class=\"kn-proj-item-desc\">" + K.escapeHtml(project.description) + "</div>" : "")
				+ "<div class=\"kn-proj-item-path kn-mono\">" + K.escapeHtml(project.localPath || "") + "</div>"
				+ "<div class=\"kn-proj-item-facts\">" + facts + "</div></a>";
		}).join("");
	}

	function fact(name, value) {
		return "<span>" + K.escapeHtml(name) + " <b>" + K.escapeHtml(format(value)) + "</b></span>";
	}

	/* ======================= 홈 ======================= */

	function showHome(projectId, revisionId) {
		show("home");
		var no = ++requestNo;
		var sameProject = current.projectId === projectId && current.project;
		current.projectId = projectId;
		el["kn-proj-title"].textContent = projectId;
		el["kn-proj-trust"].innerHTML = "";
		var projectCall = sameProject ? Promise.resolve(current.project) : K.call("GET", "/api/projects/" + encodeURIComponent(projectId));
		projectCall.then(function (project) {
			if (no !== requestNo) {
				return null;
			}
			current.project = project;
			var alias = project.projectName && project.projectName !== project.projectId ? project.projectName : "";
			el["kn-proj-sub"].textContent = [alias, project.description].filter(function (text) { return !!text; }).join(" · ")
				|| "현행 구조와 업무 영역별 규모입니다.";
			return K.loadRevisions(projectId, el["kn-proj-revision"]);
		}).then(function (revisions) {
			if (no !== requestNo || revisions === null) {
				return;
			}
			// 주소에 리비전이 있고 목록에 있으면 그것, 아니면 loadRevisions 가 고른 것(분석이 끝난 가장 최근 리비전)
			if (revisionId && revisions.some(function (revision) { return String(revision.revisionId) === String(revisionId); })) {
				el["kn-proj-revision"].value = revisionId;
			}
			var ready = revisions.some(function (revision) { return revision.status === "READY"; });
			if (!ready) {
				showEmpty(revisions.length === 0 ? "아직 분석하지 않았습니다. 오른쪽 위 <b>분석 실행</b>을 누르면 현행 구조가 만들어집니다."
					: "아직 분석이 끝난 리비전이 없습니다. 분석이 끝나면 여기에 현행 구조가 나옵니다.");
				return;
			}
			loadStructure(el["kn-proj-revision"].value);
		}).catch(fail);
	}

	function showEmpty(html) {
		el["kn-proj-empty"].innerHTML = "<p>" + html + "</p>";
		el["kn-proj-empty"].hidden = false;
		el["kn-proj-structure"].hidden = true;
	}

	function loadStructure(revisionId) {
		var no = ++requestNo;
		current.revisionId = revisionId;
		el["kn-proj-empty"].hidden = true;
		el["kn-proj-structure"].hidden = false;
		K.call("GET", "/api/revisions/" + encodeURIComponent(revisionId) + "/structure").then(function (structure) {
			if (no !== requestNo) {
				return;
			}
			current.structure = structure;
			renderTrust(structure.trust || {});
			renderStructure(structure);
		}).catch(fail);
	}

	/**
	 * 신뢰도 한 줄. 개발 리더가 가장 먼저 묻는 "이 결과 믿어도 되나?"에 답한다.
	 * 자세한 내용(못 푼 참조를 파일 · 줄까지)은 "분석 실행 · 진단" 화면의 연결 품질 탭에 있다.
	 */
	function renderTrust(trust) {
		var rate = trust.callResolutionRate;
		var level = rate === undefined || rate === null ? "" : rate >= RATE_GOOD ? "good" : rate >= RATE_WARN ? "warn" : "bad";
		var html = "<span class=\"kn-proj-trust-label\">신뢰도</span>";
		if (level) {
			html += "<span class=\"kn-dash-rate kn-dash-rate-" + level + "\">호출 해석률 <b>" + Number(rate).toFixed(1) + "%</b>"
				+ "<i><b style=\"width:" + Math.max(0, Math.min(100, Number(rate))) + "%\"></b></i></span>";
		}
		if (trust.callHeuristicRate !== undefined) {
			html += "<span title=\"이름만 보고 이은 호출. 영향도에서는 신뢰도 LOW 로 표시됩니다\">짐작으로 이음 <b>" + Number(trust.callHeuristicRate).toFixed(1) + "%</b></span>";
		}
		if (trust.parseSuccessRate !== undefined) {
			html += "<span>파싱 성공 <b>" + Number(trust.parseSuccessRate).toFixed(1) + "%</b></span>";
		}
		if (trust.topUnresolvedReason) {
			html += "<span class=\"kn-proj-trust-reason\">못 푼 참조 <b>" + format(trust.unresolvedCount) + "</b>건 · 가장 많은 이유: "
				+ K.escapeHtml(trust.topUnresolvedReason.reason) + " (" + format(trust.topUnresolvedReason.count) + "건)</span>";
		}
		var label = el["kn-proj-revision"].options[el["kn-proj-revision"].selectedIndex];
		var diagnose = K.pageUrl("knowledge/index", "p=" + encodeURIComponent(current.projectId) + "&r=" + current.revisionId
			+ "&l=" + encodeURIComponent(label ? label.text.replace(/^#\d+\s+/, "").replace(/\s+\(.*\)$/, "") : ""));
		html += "<a href=\"" + K.escapeHtml(diagnose) + "\">진단 보기</a>";
		el["kn-proj-trust"].innerHTML = html;
	}

	/** 화면 종류 · 진입점 종류의 한글 이름 */
	var SCREEN_TYPES = { JSP: "JSP", WEBSQUARE: "WebSquare", NEXACRO: "Nexacro" };
	var ENDPOINT_TYPES = { HTTP: "HTTP 주소", JSP: "JSP 직접 호출", MAIN: "main", SERVLET: "서블릿", THREAD: "스레드", SCHEDULE: "스케줄", BATCH: "배치", MESSAGE: "메시지" };

	function renderStructure(structure) {
		var layers = structure.layers || {};
		var mapUrl = K.pageUrl("knowledge/graph/graph", "p=" + encodeURIComponent(current.projectId) + "&r=" + structure.revisionId);
		el["kn-proj-strip"].innerHTML = K.layerStrip(layers, mapUrl);

		var screens = (structure.screenTypes || []).map(function (row) {
			return (SCREEN_TYPES[row.type] || row.type) + " " + format(row.count);
		});
		var endpoints = (structure.endpointTypes || []).map(function (row) {
			return (ENDPOINT_TYPES[row.endpointType] || row.endpointType) + " " + format(row.count);
		});
		el["kn-proj-kinds"].innerHTML = (screens.length ? "<span><b>화면 기술</b> " + K.escapeHtml(screens.join(" · ")) + "</span>" : "")
			+ (endpoints.length ? "<span><b>진입점</b> " + K.escapeHtml(endpoints.join(" · ")) + "</span>" : "");

		var prefixes = structure.areaPrefixes || {};
		var parts = [];
		if (prefixes.java) {
			parts.push("패키지 <code>" + K.escapeHtml(prefixes.java) + "</code>");
		}
		if (prefixes.screen) {
			parts.push("화면 폴더 <code>" + K.escapeHtml(prefixes.screen) + "</code>");
		}
		if (prefixes.mapper) {
			parts.push("SQL 매퍼 폴더 <code>" + K.escapeHtml(prefixes.mapper) + "</code>");
		}
		el["kn-proj-area-note"].innerHTML = "영역은 공통 앞부분(" + (parts.length ? parts.join(", ") : "없음") + ")을 뗀 첫 마디입니다. "
			+ "<code>dao</code> · <code>mapper</code> 같은 기술 이름은 건너뜁니다. 패키지와 폴더를 같은 이름으로 나눈 프로젝트라면 한 줄에 모입니다.";

		var areas = structure.areas || [];
		var max = 0;
		areas.forEach(function (row) {
			max = Math.max(max, sizeOf(row));
		});
		K.table(el["kn-proj-areas"], [
			{ key: "area", label: "업무 영역", html: function (row) {
				var special = String(row.area).charAt(0) === "(";
				var width = max > 0 ? Math.max(2, Math.round(100 * sizeOf(row) / max)) : 0;
				return "<div class=\"kn-proj-area\"><span class=\"" + (special ? "ai-hint" : "kn-mono") + "\">" + K.escapeHtml(row.area) + "</span>"
					+ "<i aria-hidden=\"true\"><b style=\"width:" + width + "%\"></b></i></div>";
			} },
			{ key: "screens", label: "화면", num: true, html: function (row) { return countCell(row.screens); } },
			{ key: "endpoints", label: "진입점", num: true, html: function (row) { return countCell(row.endpoints); } },
			{ key: "controllers", label: "컨트롤러", num: true, html: function (row) { return countCell(row.controllers); } },
			{ key: "services", label: "서비스", num: true, html: function (row) { return countCell(row.services); } },
			{ key: "daos", label: "DAO", num: true, html: function (row) { return countCell(row.daos); } },
			{ key: "sqlStatements", label: "SQL 문", num: true, html: function (row) { return countCell(row.sqlStatements); } },
			{ key: "tables", label: "테이블", num: true, html: function (row) { return countCell(row.tables); } }
		], areas, "영역으로 나눌 것이 없습니다.");
	}

	/** 영역의 크기(막대 길이): 화면 + 진입점 + 클래스 + SQL 문. 서버(StructureService)의 정렬 기준과 같다 */
	function sizeOf(row) {
		return number(row.screens) + number(row.endpoints) + number(row.controllers) + number(row.services) + number(row.daos) + number(row.sqlStatements);
	}

	/** 0 은 흐리게 */
	function countCell(value) {
		return number(value) === 0 ? "<span class=\"kn-proj-zero\">0</span>" : K.escapeHtml(format(value));
	}

	/** 업무 영역별 규모를 CSV 로 내려받는다(엑셀에서 바로 열리게 BOM 을 붙인다) */
	function downloadAreas() {
		if (!current.structure) {
			return;
		}
		var lines = ["업무 영역,화면,진입점,컨트롤러,서비스,DAO,SQL 문,테이블"];
		(current.structure.areas || []).forEach(function (row) {
			lines.push([csv(row.area), number(row.screens), number(row.endpoints), number(row.controllers), number(row.services), number(row.daos),
				number(row.sqlStatements), number(row.tables)].join(","));
		});
		var blob = new Blob(["﻿" + lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
		var link = document.createElement("a");
		link.href = URL.createObjectURL(blob);
		link.download = current.projectId + "-r" + current.structure.revisionId + "-업무영역.csv";
		document.body.appendChild(link);
		link.click();
		setTimeout(function () {
			URL.revokeObjectURL(link.href);
			link.remove();
		}, 0);
	}

	function csv(value) {
		var text = String(value === null || value === undefined ? "" : value);
		return /[",\r\n]/.test(text) ? "\"" + text.replace(/"/g, "\"\"") + "\"" : text;
	}

	/** 기본값(새 리비전, 처음부터)으로 분석을 시작한다. 세밀한 설정은 "분석 실행 · 진단" 화면에 있다 */
	function runAnalysis() {
		if (!current.projectId || !confirm(current.projectId + " 을(를) 지금 분석할까요? 프로젝트 크기에 따라 몇 분에서 수십 분 걸립니다.")) {
			return;
		}
		el["kn-proj-run"].disabled = true;
		K.call("POST", "/api/projects/" + encodeURIComponent(current.projectId) + "/analyses", null, {}).then(function (result) {
			var link = K.pageUrl("knowledge/index", "p=" + encodeURIComponent(current.projectId));
			el["kn-proj-message"].className = "kn-message";
			el["kn-proj-message"].innerHTML = "분석을 시작했습니다" + (result && result.analysisId ? " (" + K.escapeHtml(result.analysisId) + ")" : "")
				+ ". 끝나면 리비전 목록에 새 리비전이 생깁니다. 진행 상황은 <a href=\"" + K.escapeHtml(link) + "\">분석 실행 · 진단</a> 화면이나 대시보드에서 봅니다.";
		}).catch(fail).then(function () {
			el["kn-proj-run"].disabled = false;
		});
	}

	/* ======================= 등록 / 설정 ======================= */

	/** project 가 null 이면 새로 등록, 있으면 그 값을 채운 설정 화면 */
	function showForm(project) {
		show("form");
		current.project = project;
		current.projectId = project ? project.projectId : null;
		el["kn-proj-title"].textContent = project ? project.projectId : "프로젝트";
		el["kn-proj-sub"].textContent = project ? "소스 폴더와 DB 접속 정보를 고칩니다." : "분석할 애플리케이션의 소스 폴더를 등록합니다. DB 접속 정보는 있으면 적습니다.";
		el["kn-proj-form-title"].textContent = project ? "프로젝트 설정" : "프로젝트 등록";
		el["kn-pf-id"].value = project ? project.projectId : "";
		el["kn-pf-id"].readOnly = !!project;
		el["kn-pf-name"].value = project && project.projectName !== project.projectId ? project.projectName || "" : "";
		el["kn-pf-path"].value = project ? project.localPath || "" : "";
		el["kn-pf-classpath"].value = project ? project.classpath || "" : "";
		el["kn-pf-desc"].value = project ? project.description || "" : "";
		K.message(el["kn-pf-message"], "");
		el["kn-pf-dbs"].innerHTML = "";
		((project && project.dbConnections) || []).forEach(addDbRow);
	}

	/** DB 접속 한 줄. dbSeq 는 저장된 줄을 고칠 때 저장된 비밀번호를 찾는 데 쓴다 */
	function addDbRow(db) {
		var saved = db.password === "****";
		var row = document.createElement("div");
		row.className = "kn-proj-db";
		row.setAttribute("data-seq", db.dbSeq === undefined || db.dbSeq === null ? "" : String(db.dbSeq));
		row.innerHTML = "<div class=\"kn-proj-db-fields\">"
			+ dbField("dbName", "이름", db.dbName, "예: 계정계", "text")
			+ dbField("envLabel", "환경", db.envLabel, "예: 개발 / 검증 / 운영", "text")
			+ dbField("schemaName", "스키마 (선택)", db.schemaName, "비우면 사용자의 기본 스키마", "text")
			+ dbField("jdbcUrl", "JDBC 주소", db.jdbcUrl, "jdbc:oracle:thin:@host:1521/SID", "text", "kn-proj-db-url")
			+ dbField("username", "사용자", db.username, "", "text")
			+ dbField("password", "비밀번호", "", saved ? "저장됨 · 바꿀 때만 입력" : "", "password")
			+ "</div><div class=\"kn-proj-db-actions\">"
			+ "<button type=\"button\" class=\"kn-btn kn-plain\" data-act=\"test\">접속 시험</button>"
			+ "<button type=\"button\" class=\"kn-btn kn-plain\" data-act=\"up\" title=\"먼저 찾기\" aria-label=\"위로\">위로</button>"
			+ "<button type=\"button\" class=\"kn-btn kn-plain\" data-act=\"down\" title=\"나중에 찾기\" aria-label=\"아래로\">아래로</button>"
			+ "<button type=\"button\" class=\"kn-btn kn-plain kn-proj-db-remove\" data-act=\"remove\">빼기</button>"
			+ "<span class=\"kn-proj-db-result\" aria-live=\"polite\"></span></div>";
		el["kn-pf-dbs"].appendChild(row);
	}

	function dbField(name, label, value, placeholder, type, css) {
		return "<label class=\"kn-field" + (css ? " " + css : "") + "\"><span>" + K.escapeHtml(label) + "</span><input type=\"" + type + "\" data-name=\"" + name + "\" value=\""
			+ K.escapeHtml(value || "") + "\" placeholder=\"" + K.escapeHtml(placeholder || "") + "\"" + (type === "password" ? " autocomplete=\"new-password\"" : "") + " /></label>";
	}

	function onDbRowClick(event) {
		var button = event.target.closest ? event.target.closest("button[data-act]") : null;
		if (!button) {
			return;
		}
		var row = button.closest(".kn-proj-db");
		var act = button.getAttribute("data-act");
		if (act === "remove") {
			row.remove();
		} else if (act === "up" && row.previousElementSibling) {
			row.parentNode.insertBefore(row, row.previousElementSibling);
		} else if (act === "down" && row.nextElementSibling) {
			row.parentNode.insertBefore(row.nextElementSibling, row);
		} else if (act === "test") {
			testDb(row, button);
		}
	}

	/** 화면에 적힌 값 그대로 접속해 본다(저장하지 않아도 된다). 비밀번호를 비워 두면 저장된 비밀번호를 쓴다 */
	function testDb(row, button) {
		var result = row.querySelector(".kn-proj-db-result");
		var projectId = el["kn-pf-id"].value.trim();
		if (!current.project) {
			result.className = "kn-proj-db-result kn-proj-db-bad";
			result.textContent = "먼저 프로젝트를 저장한 뒤 시험할 수 있습니다.";
			return;
		}
		button.disabled = true;
		result.className = "kn-proj-db-result";
		result.textContent = "붙어 보는 중…";
		K.call("POST", "/api/projects/" + encodeURIComponent(projectId) + "/db-connections/test", null, readDbRow(row)).then(function (test) {
			result.className = "kn-proj-db-result " + (test.ok ? "kn-proj-db-ok" : "kn-proj-db-bad");
			result.textContent = test.ok
				? "접속됨 · " + test.product + " · 스키마 " + (test.schema || "(기본)") + " · 테이블 " + format(test.tableCount) + "개"
				: "접속 안 됨 · " + (test.message || "");
		}).catch(function (err) {
			result.className = "kn-proj-db-result kn-proj-db-bad";
			result.textContent = err.message;
		}).then(function () {
			button.disabled = false;
		});
	}

	function readDbRow(row) {
		var db = {};
		Array.prototype.forEach.call(row.querySelectorAll("input[data-name]"), function (input) {
			db[input.getAttribute("data-name")] = input.getAttribute("data-name") === "password" ? input.value : input.value.trim();
		});
		var seq = row.getAttribute("data-seq");
		db.dbSeq = seq ? Number(seq) : null;
		// 비밀번호를 비워 두면 서버가 저장된 값을 그대로 쓴다
		if (!db.password) {
			db.password = null;
		}
		return db;
	}

	function save() {
		var projectId = el["kn-pf-id"].value.trim();
		// 설정 화면이면 이 화면에 없는 값(루트 패키지, 자바 버전, 규칙 묶음 ...)도 그대로 다시 보낸다. 안 보내면 지워지기 때문
		var body = {};
		var base = current.project || {};
		["rootPackages", "javaVersion", "sourceEncoding", "ruleSets"].forEach(function (key) {
			if (base[key] !== undefined && base[key] !== null) {
				body[key] = base[key];
			}
		});
		body.projectId = projectId;
		body.projectName = el["kn-pf-name"].value.trim() || projectId;
		body.localPath = el["kn-pf-path"].value.trim();
		body.classpath = el["kn-pf-classpath"].value.trim();
		body.description = el["kn-pf-desc"].value.trim();
		body.dbConnections = Array.prototype.map.call(el["kn-pf-dbs"].querySelectorAll(".kn-proj-db"), readDbRow);
		el["kn-pf-save"].disabled = true;
		K.call("POST", "/api/projects", null, body).then(function (project) {
			current.project = project;
			current.projectId = project.projectId;
			go("p=" + encodeURIComponent(project.projectId));
		}).catch(function (err) {
			K.message(el["kn-pf-message"], err.message, true);
		}).then(function () {
			el["kn-pf-save"].disabled = false;
		});
	}

	/* ======================= 작은 도우미 ======================= */

	function number(value) {
		var n = Number(value);
		return isNaN(n) ? 0 : n;
	}

	function format(value) {
		if (value === null || value === undefined || value === "") {
			return "-";
		}
		return number(value).toLocaleString();
	}

	return { init: init };
})();
