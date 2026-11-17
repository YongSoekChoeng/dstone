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
			"kn-pf-classpath", "kn-pf-desc", "kn-pf-dbs", "kn-pf-db-add", "kn-pf-message", "kn-pf-cancel", "kn-pf-save",
			"kn-proj-tabs", "kn-pu-notes", "kn-pu-kinds", "kn-pu-area", "kn-pu-q", "kn-pu-csv", "kn-pu-message", "kn-pu-list", "kn-pu-prev", "kn-pu-page",
			"kn-pu-next", "kn-pg-table-filter", "kn-pg-area", "kn-pg-q", "kn-pg-csv", "kn-pg-message", "kn-pg-list", "kn-pg-prev", "kn-pg-page", "kn-pg-next",
			"kn-cr-summary", "kn-cr-q", "kn-cr-csv", "kn-cr-message", "kn-cr-list", "kn-cr-prev", "kn-cr-page", "kn-cr-next"].forEach(function (id) {
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
			replaceHash();
			loadStructure(revisionId);
		});
		el["kn-proj-area-csv"].addEventListener("click", downloadAreas);
		el["kn-pf-db-add"].addEventListener("click", function () { addDbRow({}); });
		el["kn-pf-cancel"].addEventListener("click", function () { history.back(); });
		el["kn-pf-save"].addEventListener("click", save);
		el["kn-pf-dbs"].addEventListener("click", onDbRowClick);
		bindTabs();
		bindUnused();
		bindPrograms();
		bindCrud();
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
		var tab = K.readHash().t;
		openTab(TABS.indexOf(tab) >= 0 ? tab : "structure", true);
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
			// 나머지 탭은 열려 있을 때만 읽는다(처음 계산에 몇 초 걸릴 수 있다)
			unused.revisionId = null;
			programs.revisionId = null;
			crud.revisionId = null;
			loadTab(currentTab);
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

	/* ======================= 탭 ======================= */

	var currentTab = "structure";

	/** 탭 이름(주소의 t 값) */
	var TABS = ["structure", "programs", "crud", "unused"];

	function bindTabs() {
		el["kn-proj-tabs"].addEventListener("click", function (event) {
			var tab = event.target.closest ? event.target.closest("button[data-tab]") : null;
			if (tab) {
				openTab(tab.getAttribute("data-tab"));
			}
		});
	}

	/** 탭을 연다. quiet 가 아니면 주소의 t 도 바꾸고, 그 탭의 내용을 (아직 안 읽었으면) 읽는다 */
	function openTab(name, quiet) {
		currentTab = name;
		Array.prototype.forEach.call(el["kn-proj-tabs"].querySelectorAll("button[data-tab]"), function (button) {
			var on = button.getAttribute("data-tab") === name;
			button.className = "kn-tab" + (on ? " kn-tab-on" : "");
			button.setAttribute("aria-selected", on ? "true" : "false");
		});
		Array.prototype.forEach.call(document.querySelectorAll("#kn-proj-home .kn-tab-panel"), function (panel) {
			panel.hidden = panel.getAttribute("data-panel") !== name;
		});
		if (quiet) {
			return;
		}
		replaceHash();
		loadTab(name);
	}

	/** 그 탭의 내용을 아직 이 리비전으로 읽지 않았으면 읽는다 */
	function loadTab(name) {
		if (!current.revisionId) {
			return;
		}
		if (name === "unused" && unused.revisionId !== current.revisionId) {
			loadUnused();
		} else if (name === "programs" && programs.revisionId !== current.revisionId) {
			programs.revisionId = current.revisionId;
			programs.page = 1;
			loadPrograms();
		} else if (name === "crud" && crud.revisionId !== current.revisionId) {
			crud.revisionId = current.revisionId;
			crud.page = 1;
			loadCrud();
		}
	}

	/** 지금 프로젝트 · 리비전 · 탭을 주소에 적는다(뒤로 가기 기록은 남기지 않는다) */
	function replaceHash() {
		var revisionId = el["kn-proj-revision"].value || current.revisionId;
		history.replaceState(null, "", "#p=" + encodeURIComponent(current.projectId) + (revisionId ? "&r=" + revisionId : "")
			+ (currentTab !== "structure" ? "&t=" + currentTab : ""));
	}

	/* ======================= 프로그램 목록 ======================= */

	var programs = { revisionId: null, q: "", area: "", table: "", page: 1, size: 50, total: 0 };
	var programTimer = null;

	/** 진입점 종류의 짧은 이름. HTTP 는 따로 적지 않는다 */
	var ENDPOINT_SHORT = { JSP: "JSP", MAIN: "main", SERVLET: "서블릿", THREAD: "스레드", SCHEDULE: "스케줄", BATCH: "배치", MESSAGE: "메시지" };

	/** 신뢰도 한글 이름 */
	var CONFIDENCE = { HIGH: "높음", MEDIUM: "보통", LOW: "낮음" };

	function bindPrograms() {
		el["kn-pg-area"].addEventListener("change", function () {
			programs.area = el["kn-pg-area"].value;
			programs.page = 1;
			loadPrograms();
		});
		el["kn-pg-q"].addEventListener("input", function () {
			clearTimeout(programTimer);
			programTimer = setTimeout(function () {
				programs.q = el["kn-pg-q"].value.trim();
				programs.page = 1;
				loadPrograms();
			}, 300);
		});
		el["kn-pg-table-filter"].addEventListener("click", function () {
			programs.table = "";
			programs.page = 1;
			loadPrograms();
		});
		el["kn-pg-prev"].addEventListener("click", function () {
			if (programs.page > 1) {
				programs.page--;
				loadPrograms();
			}
		});
		el["kn-pg-next"].addEventListener("click", function () {
			if (programs.page * programs.size < programs.total) {
				programs.page++;
				loadPrograms();
			}
		});
		el["kn-pg-csv"].addEventListener("click", downloadPrograms);
	}

	function programQuery(page, size) {
		var query = { page: page, size: size };
		if (programs.q) {
			query.q = programs.q;
		}
		if (programs.area) {
			query.area = programs.area;
		}
		if (programs.table) {
			query.table = programs.table;
		}
		return query;
	}

	function loadPrograms() {
		var revisionId = programs.revisionId;
		el["kn-pg-table-filter"].hidden = !programs.table;
		el["kn-pg-table-filter"].innerHTML = programs.table ? "테이블 <b>" + K.escapeHtml(programs.table) + "</b> 을(를) 쓰는 프로그램만 <span aria-hidden=\"true\">×</span>" : "";
		el["kn-pg-table-filter"].title = "누르면 이 조건을 풉니다";
		K.message(el["kn-pg-message"], programs.total === 0 && !el["kn-pg-list"].innerHTML ? "프로그램마다 호출을 끝까지 따라가는 중입니다. 처음에는 몇 초 걸립니다…" : "");
		K.call("GET", "/api/revisions/" + encodeURIComponent(revisionId) + "/programs", programQuery(programs.page, programs.size)).then(function (result) {
			if (programs.revisionId !== revisionId) {
				return;
			}
			K.message(el["kn-pg-message"], "");
			programs.total = result.total;
			renderSelect(el["kn-pg-area"], result.areas || [], programs.area);
			renderPrograms(result);
		}).catch(function (err) {
			K.message(el["kn-pg-message"], err.message, true);
		});
	}

	/** 업무 영역 고르기 칸: 영역별 건수를 붙인다 */
	function renderSelect(select, areas, picked) {
		var total = 0;
		areas.forEach(function (item) {
			total += item.count;
		});
		select.innerHTML = "<option value=\"\">모든 영역 (" + format(total) + ")</option>" + areas.filter(function (item) { return !!item.area; }).map(function (item) {
			return "<option value=\"" + K.escapeHtml(item.area) + "\"" + (item.area === picked ? " selected" : "") + ">" + K.escapeHtml(item.area)
				+ " (" + format(item.count) + ")</option>";
		}).join("");
	}

	function renderPrograms(result) {
		var revisionId = programs.revisionId;
		K.table(el["kn-pg-list"], [
			{ key: "path", label: "프로그램", html: function (row) {
				var kind = ENDPOINT_SHORT[row.endpointType] ? "<span class=\"kn-pg-kind\">" + K.escapeHtml(ENDPOINT_SHORT[row.endpointType]) + "</span>" : "";
				var handler = row.handler
					? (row.methodId ? "<a href=\"" + K.escapeHtml(K.treeUrl(current.projectId, revisionId, row.methodId)) + "\" data-tree-window title=\"관계 트리 창에서 보기\">"
						+ K.escapeHtml(row.handler) + "</a>" : K.escapeHtml(row.handler))
					: "<span class=\"ai-hint\">처리 메소드 없음</span>";
				return "<div class=\"kn-pg-path\">" + kind + "<span class=\"kn-mono\">" + K.escapeHtml(row.path || "") + "</span></div><div class=\"kn-pg-handler\">" + handler + "</div>";
			} },
			{ key: "area", label: "업무 영역", html: function (row) { return "<span class=\"kn-mono\">" + K.escapeHtml(row.area || "") + "</span>"; } },
			{ key: "screens", label: "화면", html: function (row) {
				if (!row.screenCount) {
					return "<span class=\"kn-proj-zero\">-</span>";
				}
				var names = row.screens.map(function (screen) { return K.escapeHtml(screen.replace(/^.*\//, "")); });
				return "<span title=\"" + K.escapeHtml(row.screens.join("\n")) + "\">" + names.join("<br>")
					+ (row.screenCount > row.screens.length ? "<br><span class=\"ai-hint\">외 " + (row.screenCount - row.screens.length) + "개</span>" : "") + "</span>";
			} },
			{ key: "sqlCount", label: "SQL", num: true, html: function (row) { return countCell(row.sqlCount); } },
			{ key: "tables", label: "테이블 (C / R / U / D)", html: tablesCell },
			{ key: "confidence", label: "신뢰도", html: function (row) {
				return row.confidence ? "<span class=\"kn-conf-" + K.escapeHtml(row.confidence) + "\" title=\"테이블까지 가는 길 가운데 가장 약한 관계\">"
					+ K.escapeHtml(CONFIDENCE[row.confidence] || row.confidence) + "</span>" : "";
			} }
		], result.rows || [], "조건에 맞는 프로그램이 없습니다.");
		pager(el["kn-pg-page"], el["kn-pg-prev"], el["kn-pg-next"], result.total, programs.page, programs.size);
	}

	/** 테이블 칸: 수와 C/R/U/D 별 수. 눌러서 펴면 테이블마다 글자와 신뢰도(테이블 이름은 영향도로 가는 링크) */
	function tablesCell(row) {
		if (!row.tableCount) {
			return "<span class=\"kn-proj-zero\">닿는 테이블 없음</span>";
		}
		var summary = "<b>" + format(row.tableCount) + "</b>개 " + ["C", "R", "U", "D"].map(function (letter) {
			return "<span class=\"kn-crud-mini" + (row[letter] ? "" : " kn-proj-zero") + "\">" + letter + " " + row[letter] + "</span>";
		}).join("");
		var list = row.tables.map(function (table) {
			return "<li><a href=\"" + K.escapeHtml(impactUrl(table.table)) + "\" class=\"kn-mono\">" + K.escapeHtml(table.table) + "</a> "
				+ crudLetters(table.crud) + (table.confidence !== "HIGH" ? " <span class=\"kn-conf-" + K.escapeHtml(table.confidence) + "\">"
					+ K.escapeHtml(CONFIDENCE[table.confidence] || table.confidence) + "</span>" : "") + "</li>";
		}).join("");
		return "<details class=\"kn-pg-tables\"><summary>" + summary + "</summary><ul>" + list + "</ul></details>";
	}

	/** C R U D 네 칸. 있는 글자만 진하게 */
	function crudLetters(crud) {
		return "<span class=\"kn-crud\">" + ["C", "R", "U", "D"].map(function (letter) {
			return "<i class=\"" + (String(crud || "").indexOf(letter) >= 0 ? "kn-crud-on kn-crud-" + letter : "") + "\">" + letter + "</i>";
		}).join("") + "</span>";
	}

	/** 그 테이블의 영향도(다시 테스트할 곳) 화면 */
	function impactUrl(table) {
		return K.pageUrl("knowledge/impact/impact", "p=" + encodeURIComponent(current.projectId) + "&r=" + current.revisionId + "&k=table&v=" + encodeURIComponent(table));
	}

	function pager(label, prev, next, total, page, size) {
		var pages = Math.max(1, Math.ceil(total / size));
		label.textContent = format(total) + "건 · " + page + " / " + pages + " 쪽";
		prev.disabled = page <= 1;
		next.disabled = page >= pages;
	}

	/** 목록 API 를 끝까지 읽어 모은다(내려받기용) */
	function fetchAll(path, query) {
		var rows = [];
		var size = 1000;
		var next = function (page) {
			var q = {};
			Object.keys(query || {}).forEach(function (key) {
				q[key] = query[key];
			});
			q.page = page;
			q.size = size;
			return K.call("GET", path, q).then(function (result) {
				rows = rows.concat(result.rows || []);
				return page * size < result.total ? next(page + 1) : rows;
			});
		};
		return next(1);
	}

	/** 지금 조건의 프로그램 목록 전부를 CSV 로. 엑셀에서 바로 열리게 BOM 을 붙인다 */
	function downloadPrograms() {
		if (!programs.revisionId) {
			return;
		}
		var revisionId = programs.revisionId;
		var query = programQuery(1, 1);
		delete query.page;
		delete query.size;
		K.message(el["kn-pg-message"], "내려받을 목록을 모으는 중…");
		fetchAll("/api/revisions/" + revisionId + "/programs", query).then(function (rows) {
			var lines = ["주소,종류,처리 메소드,클래스,업무 영역,화면 수,화면(앞 3개),SQL 수,테이블 수,C,R,U,D,신뢰도,테이블(CRUD)"];
			rows.forEach(function (row) {
				var tables = (row.tables || []).map(function (table) { return table.table + "(" + table.crud + ")"; }).join("; ");
				lines.push([row.path, row.endpointType, row.handler, row.type, row.area, row.screenCount, (row.screens || []).join("; "), row.sqlCount,
					row.tableCount, row.C, row.R, row.U, row.D, CONFIDENCE[row.confidence] || "", tables].map(csv).join(","));
			});
			saveCsv(lines, current.projectId + "-r" + revisionId + "-프로그램목록.csv");
			K.message(el["kn-pg-message"], "");
		}).catch(function (err) {
			K.message(el["kn-pg-message"], err.message, true);
		});
	}

	function saveCsv(lines, name) {
		var blob = new Blob(["﻿" + lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
		var link = document.createElement("a");
		link.href = URL.createObjectURL(blob);
		link.download = name;
		document.body.appendChild(link);
		link.click();
		setTimeout(function () {
			URL.revokeObjectURL(link.href);
			link.remove();
		}, 0);
	}

	/* ======================= 테이블 · CRUD ======================= */

	var crud = { revisionId: null, letter: "", q: "", page: 1, size: 50, total: 0 };
	var crudTimer = null;

	function bindCrud() {
		Array.prototype.forEach.call(document.querySelectorAll(".kn-cr-letter"), function (button) {
			button.addEventListener("click", function () {
				Array.prototype.forEach.call(document.querySelectorAll(".kn-cr-letter"), function (other) {
					other.className = "kn-pu-grade kn-cr-letter" + (other === button ? " kn-pu-on" : "");
				});
				crud.letter = button.getAttribute("data-crud");
				crud.page = 1;
				loadCrud();
			});
		});
		el["kn-cr-q"].addEventListener("input", function () {
			clearTimeout(crudTimer);
			crudTimer = setTimeout(function () {
				crud.q = el["kn-cr-q"].value.trim();
				crud.page = 1;
				loadCrud();
			}, 300);
		});
		el["kn-cr-prev"].addEventListener("click", function () {
			if (crud.page > 1) {
				crud.page--;
				loadCrud();
			}
		});
		el["kn-cr-next"].addEventListener("click", function () {
			if (crud.page * crud.size < crud.total) {
				crud.page++;
				loadCrud();
			}
		});
		// 프로그램 수를 누르면 그 테이블을 쓰는 프로그램 목록으로
		el["kn-cr-list"].addEventListener("click", function (event) {
			var button = event.target.closest ? event.target.closest("button[data-table]") : null;
			if (!button) {
				return;
			}
			programs.table = button.getAttribute("data-table");
			programs.area = "";
			programs.q = "";
			el["kn-pg-q"].value = "";
			programs.page = 1;
			programs.revisionId = current.revisionId;
			openTab("programs");
			loadPrograms();
		});
		el["kn-cr-csv"].addEventListener("click", downloadMatrix);
	}

	function crudQuery(page, size) {
		var query = { page: page, size: size };
		if (crud.letter) {
			query.crud = crud.letter;
		}
		if (crud.q) {
			query.q = crud.q;
		}
		return query;
	}

	function loadCrud() {
		var revisionId = crud.revisionId;
		K.call("GET", "/api/revisions/" + encodeURIComponent(revisionId) + "/programs/tables", crudQuery(crud.page, crud.size)).then(function (result) {
			if (crud.revisionId !== revisionId) {
				return;
			}
			K.message(el["kn-cr-message"], "");
			crud.total = result.total;
			var totals = result.totals || {};
			el["kn-cr-summary"].innerHTML = "<span><b>테이블</b> " + format(totals.tables) + "개</span><span><b>프로그램</b> " + format(totals.programs) + "개</span>"
				+ "<span title=\"SQL 에는 나오지만, 어느 진입점에서 출발해도 그 SQL 까지 닿지 않는 테이블. 배치 · 외부 호출 전용이거나 쓰지 않는 SQL 일 수 있습니다\">"
				+ "<b>어느 프로그램에서도 닿지 않는 테이블</b> " + format(totals.unreached) + "개</span>";
			renderCrud(result);
		}).catch(function (err) {
			K.message(el["kn-cr-message"], err.message, true);
		});
	}

	function renderCrud(result) {
		K.table(el["kn-cr-list"], [
			{ key: "table", label: "테이블", html: function (row) {
				return "<a href=\"" + K.escapeHtml(impactUrl(row.table)) + "\" class=\"kn-mono\" title=\"이 테이블을 바꾸면 다시 테스트할 곳(영향도)\">" + K.escapeHtml(row.table) + "</a>";
			} },
			{ key: "programs", label: "프로그램", num: true, html: function (row) {
				return row.programs ? "<button type=\"button\" class=\"kn-link-btn\" data-table=\"" + K.escapeHtml(row.table) + "\" title=\"이 테이블을 쓰는 프로그램 보기\">"
					+ format(row.programs) + "</button>" : "<span class=\"kn-proj-zero\" title=\"어느 프로그램에서도 닿지 않습니다\">0</span>";
			} },
			{ key: "C", label: "C", num: true, html: function (row) { return countCell(row.C); } },
			{ key: "R", label: "R", num: true, html: function (row) { return countCell(row.R); } },
			{ key: "U", label: "U", num: true, html: function (row) { return countCell(row.U); } },
			{ key: "D", label: "D", num: true, html: function (row) { return countCell(row.D); } },
			{ key: "sqlCount", label: "SQL", num: true, html: function (row) { return countCell(row.sqlCount); } },
			{ key: "areas", label: "쓰는 업무 영역", html: function (row) {
				var areas = row.areas || [];
				return "<span class=\"kn-mono\">" + K.escapeHtml(areas.slice(0, 5).join(", ")) + "</span>"
					+ (areas.length > 5 ? " <span class=\"ai-hint\">외 " + (areas.length - 5) + "</span>" : "");
			} }
		], result.rows || [], "조건에 맞는 테이블이 없습니다.");
		pager(el["kn-cr-page"], el["kn-cr-prev"], el["kn-cr-next"], result.total, crud.page, crud.size);
	}

	/**
	 * CRUD 매트릭스: 줄 = 프로그램, 칸 = 테이블(쓰는 프로그램이 많은 순), 값 = C/R/U/D 글자.
	 * 어느 프로그램에서도 닿지 않는 테이블은 칸을 만들지 않는다.
	 */
	function downloadMatrix() {
		if (!crud.revisionId) {
			return;
		}
		var revisionId = crud.revisionId;
		K.message(el["kn-cr-message"], "매트릭스를 만드는 중…");
		Promise.all([fetchAll("/api/revisions/" + revisionId + "/programs/tables", {}), fetchAll("/api/revisions/" + revisionId + "/programs", {})]).then(function (results) {
			var tables = results[0].filter(function (row) { return row.programs > 0; }).map(function (row) { return row.table; });
			var lines = [["주소", "처리 메소드", "업무 영역"].concat(tables).map(csv).join(",")];
			results[1].forEach(function (row) {
				if (!row.tableCount) {
					return;
				}
				var byTable = {};
				row.tables.forEach(function (table) {
					byTable[table.table] = table.crud;
				});
				lines.push([row.path, row.handler, row.area].concat(tables.map(function (table) { return byTable[table] || ""; })).map(csv).join(","));
			});
			saveCsv(lines, current.projectId + "-r" + revisionId + "-CRUD매트릭스.csv");
			K.message(el["kn-cr-message"], "");
		}).catch(function (err) {
			K.message(el["kn-cr-message"], err.message, true);
		});
	}

	/* ======================= 전환 제외 후보 ======================= */

	var T = window.DstoneKnowledgeTerms;

	/** 보여 주는 순서. 차세대 범위를 정할 때 먼저 보는 것(화면, SQL)부터 */
	var UNUSED_ORDER = ["SCREEN", "SQL", "TYPE", "METHOD", "TABLE"];

	/** 지금 고른 조건과, 마지막으로 읽은 요약 */
	var unused = { revisionId: null, summary: null, kind: null, grade: "", area: "", q: "", page: 1, size: 50, total: 0 };
	var searchTimer = null;

	function bindUnused() {
		el["kn-pu-kinds"].addEventListener("click", function (event) {
			var button = event.target.closest ? event.target.closest("button[data-kind]") : null;
			if (button) {
				pickKind(button.getAttribute("data-kind"));
			}
		});
		Array.prototype.forEach.call(document.querySelectorAll(".kn-pu-grade"), function (button) {
			button.addEventListener("click", function () {
				Array.prototype.forEach.call(document.querySelectorAll(".kn-pu-grade"), function (other) {
					other.className = "kn-pu-grade" + (other === button ? " kn-pu-on" : "");
				});
				unused.grade = button.getAttribute("data-grade");
				unused.page = 1;
				loadUnusedList();
			});
		});
		el["kn-pu-area"].addEventListener("change", function () {
			unused.area = el["kn-pu-area"].value;
			unused.page = 1;
			loadUnusedList();
		});
		el["kn-pu-q"].addEventListener("input", function () {
			clearTimeout(searchTimer);
			searchTimer = setTimeout(function () {
				unused.q = el["kn-pu-q"].value.trim();
				unused.page = 1;
				loadUnusedList();
			}, 300);
		});
		el["kn-pu-prev"].addEventListener("click", function () {
			if (unused.page > 1) {
				unused.page--;
				loadUnusedList();
			}
		});
		el["kn-pu-next"].addEventListener("click", function () {
			if (unused.page * unused.size < unused.total) {
				unused.page++;
				loadUnusedList();
			}
		});
		el["kn-pu-csv"].addEventListener("click", downloadUnused);
	}

	/** 요약(종류별 건수와 알아 둘 사실)을 읽고, 건수가 있는 첫 종류의 목록을 연다 */
	function loadUnused() {
		var revisionId = current.revisionId;
		unused.revisionId = revisionId;
		el["kn-pu-kinds"].innerHTML = "";
		el["kn-pu-notes"].innerHTML = "";
		el["kn-pu-list"].innerHTML = "";
		K.message(el["kn-pu-message"], "아무도 쓰지 않는 것을 찾는 중입니다. 처음에는 몇 초 걸립니다…");
		K.call("GET", "/api/revisions/" + encodeURIComponent(revisionId) + "/unused").then(function (summary) {
			if (unused.revisionId !== revisionId) {
				return;
			}
			unused.summary = summary;
			renderUnusedNotes(summary);
			var counts = {};
			(summary.kinds || []).forEach(function (item) {
				counts[item.kind] = item;
			});
			var first = null;
			UNUSED_ORDER.forEach(function (kind) {
				if (!first && counts[kind] && counts[kind].total > 0) {
					first = kind;
				}
			});
			pickKind(unused.kind && counts[unused.kind] && counts[unused.kind].total > 0 ? unused.kind : first || "SCREEN");
		}).catch(function (err) {
			K.message(el["kn-pu-message"], err.message, true);
		});
	}

	/** 판정을 읽을 때 알아 둘 것: 해석률이 낮음, 메뉴 정보 없음, 테이블을 견줄 목록이 없음 */
	function renderUnusedNotes(summary) {
		var notes = [];
		if (summary.warning) {
			notes.push("<p class=\"kn-proj-note kn-proj-note-warn\">" + K.escapeHtml(summary.warning) + "</p>");
		}
		var sources = summary.tableSources || {};
		if (sources.db !== "OK") {
			var settings = "#p=" + encodeURIComponent(current.projectId) + "&edit=1";
			var why = sources.db === "FAILED" ? "등록한 DB 에서 테이블 목록을 읽지 못했습니다(" + K.escapeHtml(sources.dbMessage || "") + "). "
				: "DB 접속이 등록되지 않았습니다. ";
			notes.push("<p class=\"kn-proj-note\">" + why + "<a href=\"" + K.escapeHtml(settings) + "\">프로젝트 설정</a>에서 DB 접속을 등록하면 "
				+ "<b>DB 에는 있는데 아무 SQL 도 쓰지 않는 테이블</b>까지 찾습니다."
				+ (sources.ddl > 0 || sources.dict > 0 ? " 지금은 DDL 파일 · 테이블 사전의 목록과만 견줍니다." : "") + "</p>");
		}
		if (summary.menuKnown === false) {
			notes.push("<p class=\"kn-proj-note\">메뉴 정보가 분석에 없어 화면은 모두 <b>추정</b>입니다. 메뉴에서만 여는 화면이 섞여 있을 수 있습니다.</p>");
		}
		el["kn-pu-notes"].innerHTML = notes.join("");
	}

	/** 종류 단추: 건수, 그 가운데 확실한 것, 전체 대비 비율(셀 수 있는 종류만) */
	function renderUnusedKinds() {
		var counts = {};
		((unused.summary && unused.summary.kinds) || []).forEach(function (item) {
			counts[item.kind] = item;
		});
		el["kn-pu-kinds"].innerHTML = UNUSED_ORDER.map(function (kind) {
			var item = counts[kind] || { total: 0, certain: 0 };
			var base = baseOf(kind, item);
			var ratio = base > 0 && item.total > 0 ? "전체의 " + Math.max(1, Math.round(100 * item.total / base)) + "%" : "";
			var on = kind === unused.kind;
			return "<button type=\"button\" role=\"tab\" aria-selected=\"" + on + "\" data-kind=\"" + kind + "\" class=\"kn-pu-kind" + (on ? " kn-pu-on" : "")
				+ (item.total === 0 ? " kn-pu-none" : "") + "\" title=\"" + K.escapeHtml(T.UNUSED_KIND[kind][1]) + "\">"
				+ "<span class=\"kn-pu-kind-name\">" + K.escapeHtml(T.UNUSED_KIND[kind][0]) + "</span>"
				+ "<span class=\"kn-pu-kind-num\">" + format(item.total) + "</span>"
				+ "<span class=\"kn-pu-kind-sub\">" + (item.total > 0 ? "확실 " + format(item.certain) + " · 추정 " + format(item.total - item.certain)
					+ (ratio ? " · " + ratio : "") : "없음") + "</span></button>";
		}).join("");
	}

	/** 비율의 바탕(전체 수). 현행 구조의 계층별 개수를 쓴다. 클래스 · 메소드는 셀 바탕이 달라 비율을 내지 않는다 */
	function baseOf(kind, item) {
		var layers = (current.structure && current.structure.layers) || {};
		if (kind === "SCREEN") {
			return number(layers.screens);
		}
		if (kind === "SQL") {
			return number(layers.sqlStatements);
		}
		if (kind === "TABLE") {
			// SQL 에 나오는 테이블 + 아무 SQL 도 안 쓰는 테이블
			return number(layers.tables) + number(item.total);
		}
		return 0;
	}

	function pickKind(kind) {
		unused.kind = kind;
		unused.area = "";
		unused.page = 1;
		renderUnusedKinds();
		loadUnusedList();
	}

	function unusedQuery(page, size) {
		var query = { page: page, size: size };
		if (unused.grade) {
			query.grade = unused.grade;
		}
		if (unused.q) {
			query.q = unused.q;
		}
		if (unused.area) {
			query.area = unused.area;
		}
		return query;
	}

	function loadUnusedList() {
		var revisionId = unused.revisionId;
		var kind = unused.kind;
		K.message(el["kn-pu-message"], "");
		K.call("GET", "/api/revisions/" + encodeURIComponent(revisionId) + "/unused/" + kind, unusedQuery(unused.page, unused.size)).then(function (result) {
			if (unused.revisionId !== revisionId || unused.kind !== kind) {
				return;
			}
			unused.total = result.total;
			renderAreaSelect(result.areas || []);
			renderUnusedList(result);
		}).catch(function (err) {
			K.message(el["kn-pu-message"], err.message, true);
		});
	}

	/** 업무 영역 고르기: 영역별 건수를 붙인다. 테이블처럼 영역이 없는 종류는 숨긴다 */
	function renderAreaSelect(areas) {
		var named = areas.filter(function (item) { return !!item.area; });
		el["kn-pu-area"].parentNode.hidden = named.length === 0;
		var total = 0;
		areas.forEach(function (item) {
			total += item.count;
		});
		el["kn-pu-area"].innerHTML = "<option value=\"\">모든 영역 (" + format(total) + ")</option>" + named.map(function (item) {
			return "<option value=\"" + K.escapeHtml(item.area) + "\"" + (item.area === unused.area ? " selected" : "") + ">" + K.escapeHtml(item.area)
				+ " (" + format(item.count) + ")</option>";
		}).join("");
	}

	function renderUnusedList(result) {
		var revisionId = unused.revisionId;
		K.table(el["kn-pu-list"], [
			{ key: "name", label: "이름", html: function (row) {
				var name = K.escapeHtml(row.name) + (row.title ? " <span class=\"kn-flow-alias\">" + K.escapeHtml(row.title) + "</span>" : "");
				if (!row.graphId) {
					return name;
				}
				return "<a href=\"" + K.escapeHtml(K.treeUrl(current.projectId, revisionId, row.graphId)) + "\" data-tree-window title=\"관계 트리 창에서 확인\">" + name + "</a>";
			} },
			{ key: "area", label: "업무 영역", html: function (row) { return row.area ? "<span class=\"kn-mono\">" + K.escapeHtml(row.area) + "</span>" : ""; } },
			{ key: "grade", label: "등급", html: function (row) {
				var grade = T.UNUSED_GRADE[row.grade] || [row.grade, ""];
				return "<span class=\"kn-pu-badge kn-pu-badge-" + K.escapeHtml(String(row.grade).toLowerCase()) + "\" title=\"" + K.escapeHtml(grade[1]) + "\">"
					+ K.escapeHtml(grade[0]) + "</span>";
			} },
			{ key: "reason", label: "근거" },
			{ key: "path", label: "위치", html: function (row) {
				return row.path ? "<span class=\"kn-mono\">" + K.escapeHtml(row.path) + (row.line ? ":" + row.line : "") + "</span>" : "";
			} }
		], result.rows || [], "조건에 맞는 것이 없습니다.");
		var pages = Math.max(1, Math.ceil(result.total / unused.size));
		el["kn-pu-page"].textContent = format(result.total) + "건 · " + unused.page + " / " + pages + " 쪽";
		el["kn-pu-prev"].disabled = unused.page <= 1;
		el["kn-pu-next"].disabled = unused.page >= pages;
	}

	/** 지금 조건의 목록 전부를 CSV 로(엑셀에서 한글이 깨지지 않게 BOM 을 붙인다) */
	function downloadUnused() {
		if (!unused.revisionId || !unused.kind) {
			return;
		}
		var revisionId = unused.revisionId;
		var kind = unused.kind;
		var rows = [];
		var size = 500;
		K.message(el["kn-pu-message"], "내려받을 목록을 모으는 중…");
		var next = function (page) {
			return K.call("GET", "/api/revisions/" + revisionId + "/unused/" + kind, unusedQuery(page, size)).then(function (result) {
				rows = rows.concat(result.rows || []);
				return page * size < result.total ? next(page + 1) : rows;
			});
		};
		next(1).then(function (all) {
			var lines = ["종류,업무 영역,등급,이름,한글명,근거,파일,줄"];
			all.forEach(function (row) {
				lines.push([T.UNUSED_KIND[kind][0], row.area, (T.UNUSED_GRADE[row.grade] || [row.grade])[0], row.name, row.title, row.reason, row.path, row.line]
					.map(csv).join(","));
			});
			var blob = new Blob(["\ufeff" + lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
			var link = document.createElement("a");
			link.href = URL.createObjectURL(blob);
			link.download = current.projectId + "-r" + revisionId + "-전환제외후보-" + T.UNUSED_KIND[kind][0] + ".csv";
			document.body.appendChild(link);
			link.click();
			setTimeout(function () {
				URL.revokeObjectURL(link.href);
				link.remove();
			}, 0);
			K.message(el["kn-pu-message"], "");
		}).catch(function (err) {
			K.message(el["kn-pu-message"], err.message, true);
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
