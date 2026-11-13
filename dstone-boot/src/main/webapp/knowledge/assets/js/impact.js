/**
 * "영향도 · 호출 관계" 화면.
 *
 * 흐름은 하나입니다.
 *   맨 위   프로젝트 · 리비전 (화면 전체에 적용)
 *   ①      대상 찾기: 종류(메소드 / 테이블 / SQL / 타입) + 이름 → 후보 목록 → 하나를 고른다
 *   ②      결과: 고른 대상 머리글 + 지나온 대상 + 탭 셋(영향도 / 부르는 쪽 / 불리는 쪽)
 * 결과 표에 나온 이름(밑줄)을 누르면 그것이 새 대상이 됩니다. 지금 상태는 주소창(#...)에 남아서
 * 새로 고침, 뒤로 가기, 링크 전달이 됩니다.
 */
var DstoneKnowledgeImpact = (function () {

	var K = DstoneKnowledge;
	var el = {};

	/** 대상 종류 → 화면 글자 */
	var KIND_LABELS = { method: "메소드", table: "테이블", statement: "SQL", type: "타입" };
	/** 대상 종류 → 영향도 API 의 인자 이름 */
	var IMPACT_PARAMS = { method: "methodId", table: "table", statement: "statement", type: "type" };
	/** 영향도 응답의 target.kind → 화면에서 쓰는 종류 */
	var API_KINDS = { METHOD: "method", TABLE: "table", STATEMENT: "statement", TYPE: "type" };

	/** 찾기 칸의 안내 글자 */
	var PLACEHOLDERS = {
		method: "메소드 이름(정확히). 타입으로 좁히려면 BoardDAO#deleteBoard",
		table: "테이블 이름의 일부. 비우면 많이 쓰이는 순서로 전체",
		statement: "SQL 이름의 일부. 네임스페이스까지면 Board.deleteBoard",
		type: "타입 이름의 일부(패키지 포함 가능)"
	};

	/** 탭마다 깊이 기본값과 범위. 영향도는 서버 최대 10, 호출 관계는 5 */
	var DEPTHS = {
		impact: { value: 5, min: 0, max: 10 },
		callers: { value: 2, min: 1, max: 5 },
		callees: { value: 2, min: 1, max: 5 }
	};

	/** 탭 아래 한 줄 설명 */
	var TAB_HINTS = {
		impact: "이 대상을 고치면 무엇을 다시 확인해야 하는지 한 장으로 요약합니다(영향받는 화면 · 진입점, 건드리는 테이블, 결과를 얼마나 믿어도 되는지). 호출 경로는 부르는 쪽 / 불리는 쪽 탭에서 봅니다.",
		callers: "이 메소드를 누가 부르는지 거슬러 올라갑니다. 화면(JSP)이 주소로 요청하는 것도 함께 나옵니다.",
		callees: "이 메소드가 무엇을 부르는지 따라 내려갑니다. 실행하는 SQL, 그 SQL이 건드리는 테이블까지 나옵니다."
	};

	/** 관계 종류 → 화면 글자. 원래 이름은 마우스를 올리면 보인다. */
	var RELATION_LABELS = {
		CALLS: "호출",
		REQUESTS: "화면 요청",
		CALLS_POSSIBLE_IMPLEMENTATION: "구현체 호출(추정)",
		ENCLOSES_ANONYMOUS: "익명 클래스",
		EXECUTES_SQL: "SQL 실행",
		READS_TABLE: "테이블 읽기",
		WRITES_TABLE: "테이블 쓰기",
		RENDERS: "화면 열기"
	};

	/** 진입점 종류 → 화면 글자 */
	var ENDPOINT_LABELS = { TRANSACTION: "거래 ID", HTTP: "주소", SCHEDULED: "스케줄", MAIN: "main", THREAD: "스레드", LISTENER: "리스너", SERVLET: "서블릿", JSP: "JSP 주소" };

	/** 업무 묶음 이름으로 쓰기엔 너무 흔한 패키지 이름. 이런 이름이면 한 칸 위 패키지를 쓴다 */
	var COMMON_PACKAGES = ["service", "services", "controller", "controllers", "action", "actions", "web", "impl", "biz", "dao", "api", "rest", "handler", "handlers"];

	/** 닿는 메소드 설명의 첫머리. 무엇을 "직접 건드리는" 메소드인지 대상에 맞춰 적는다. */
	var START_LABELS = { table: "이 테이블을 쓰는 메소드", statement: "이 SQL을 쓰는 메소드", type: "이 타입의 메소드", method: "대상 메소드" };

	/** 지금 대상 {kind, value, label, notes[]}. value 는 API 에 넘기는 값(메소드 ID, 테이블 이름, SQL 이름, 타입 전체 이름) */
	var target = null;
	/** 지나온 대상(오래된 것이 앞) */
	var trail = [];
	/** 지금 탭 */
	var tab = "impact";
	/** 탭마다 마지막으로 불러온 조건. 같으면 다시 부르지 않는다 */
	var loaded = {};
	/** 탭마다 요청 번호. 늦게 온 옛 응답은 버린다 */
	var requestNo = { impact: 0, callers: 0, callees: 0 };
	/** 탭마다 결과 칸 {message, content} */
	var panels = {};

	function init() {
		["kn-impact-project", "kn-impact-revision", "kn-find-kind", "kn-find-name", "kn-find-btn", "kn-find-message", "kn-find-table",
			"kn-result-panel", "kn-target-head", "kn-trail", "kn-impact-access", "kn-access-wrap", "kn-depth", "kn-tab-hint", "kn-tab-body"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		el.tabs = document.querySelectorAll(".kn-tab");

		// 탭마다 결과 칸을 하나씩 만들어 둔다. 탭을 오가도 이미 불러온 결과는 그대로 남는다.
		["impact", "callers", "callees"].forEach(function (name) {
			var wrap = document.createElement("div");
			wrap.className = "kn-tab-panel";
			wrap.setAttribute("role", "tabpanel");
			wrap.hidden = true;
			wrap.innerHTML = "<div class=\"kn-message\"></div><div class=\"kn-tab-content\"></div>";
			el["kn-tab-body"].appendChild(wrap);
			panels[name] = { wrap: wrap, message: wrap.firstChild, content: wrap.lastChild };
		});

		el["kn-find-kind"].addEventListener("change", updatePlaceholder);
		el["kn-find-btn"].addEventListener("click", findTargets);
		el["kn-find-name"].addEventListener("keydown", function (event) {
			if (event.key === "Enter") {
				findTargets();
			}
		});
		Array.prototype.forEach.call(el.tabs, function (button) {
			button.addEventListener("click", function () {
				if (!button.disabled) {
					showTab(button.getAttribute("data-tab"), true);
				}
			});
		});
		el["kn-depth"].addEventListener("change", function () {
			var range = DEPTHS[tab];
			var value = parseInt(el["kn-depth"].value, 10);
			range.value = isNaN(value) ? range.value : Math.max(range.min, Math.min(range.max, value));
			el["kn-depth"].value = range.value;
			showTab(tab, false);
		});
		el["kn-impact-access"].addEventListener("change", function () {
			showTab(tab, false);
		});
		el["kn-impact-project"].addEventListener("change", function () {
			loadRevisions().then(function () {
				clearTarget();
				writeHash(false);
			});
		});
		el["kn-impact-revision"].addEventListener("change", function () {
			clearFound();
			if (target) {
				loaded = {};
				showTab(tab, false);
			}
			writeHash(false);
		});
		// 결과 표와 후보 목록의 밑줄 친 이름: 누르면 그것이 새 대상
		document.addEventListener("click", function (event) {
			var link = event.target.closest ? event.target.closest("a.kn-retarget[data-kind]") : null;
			if (link) {
				event.preventDefault();
				pickTarget({ kind: link.getAttribute("data-kind"), value: link.getAttribute("data-value"), label: link.getAttribute("data-label") });
			}
		});
		el["kn-trail"].addEventListener("click", function (event) {
			var button = event.target.closest ? event.target.closest("button[data-trail]") : null;
			if (button) {
				var index = parseInt(button.getAttribute("data-trail"), 10);
				var back = trail[index];
				trail = trail.slice(0, index);
				setTarget(back, { push: true, scroll: true, keepTrail: true });
			}
		});
		window.addEventListener("popstate", function () {
			restoreHash();
		});

		updatePlaceholder();
		K.loadProjects(el["kn-impact-project"]).then(function () {
			var state = readHash();
			selectOption(el["kn-impact-project"], state.p);
			return loadRevisions().then(function () {
				selectOption(el["kn-impact-revision"], state.r);
				applyState(state);
				writeHash(false);
			});
		}).catch(function (err) {
			K.message(el["kn-find-message"], err.message, true);
		});
	}

	function loadRevisions() {
		return K.loadRevisions(el["kn-impact-project"].value, el["kn-impact-revision"]);
	}

	function revisionPath() {
		var revisionId = el["kn-impact-revision"].value;
		if (!revisionId) {
			throw new Error("리비전을 고르세요. 분석이 끝난 리비전이 있어야 합니다.");
		}
		return "/api/revisions/" + revisionId;
	}

	function updatePlaceholder() {
		el["kn-find-name"].placeholder = PLACEHOLDERS[el["kn-find-kind"].value];
	}

	/* ---------------- 주소창(#...)에 지금 상태 남기기 ---------------- */

	/** #p=프로젝트&r=리비전&k=종류&v=값&l=보이는 이름&t=탭 */
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

	/** push 가 true 면 새 기록(뒤로 가기로 돌아올 수 있게), 아니면 지금 기록을 바꾼다 */
	function writeHash(push) {
		var state = { p: el["kn-impact-project"].value, r: el["kn-impact-revision"].value };
		if (target) {
			state.k = target.kind;
			state.v = target.value;
			state.l = target.label;
			state.t = tab;
		}
		var hash = "#" + Object.keys(state).filter(function (key) {
			return state[key];
		}).map(function (key) {
			return key + "=" + encodeURIComponent(state[key]);
		}).join("&");
		if (hash === location.hash) {
			return;
		}
		if (push) {
			history.pushState(null, "", hash);
		} else {
			history.replaceState(null, "", hash);
		}
	}

	/** 뒤로 가기 / 앞으로 가기 */
	function restoreHash() {
		var state = readHash();
		var ready = Promise.resolve();
		if (state.p && state.p !== el["kn-impact-project"].value) {
			selectOption(el["kn-impact-project"], state.p);
			ready = loadRevisions();
		}
		ready.then(function () {
			if (state.r && state.r !== el["kn-impact-revision"].value) {
				selectOption(el["kn-impact-revision"], state.r);
				loaded = {};
			}
			// 지나온 대상의 마지막으로 돌아온 것이면 그 칸을 지운다
			if (trail.length > 0 && state.k && sameTarget(trail[trail.length - 1], { kind: state.k, value: state.v })) {
				trail.pop();
			}
			applyState(state);
		});
	}

	function applyState(state) {
		if (state.k && state.v && KIND_LABELS[state.k]) {
			setTarget({ kind: state.k, value: state.v, label: state.l || state.v }, { push: false, tab: state.t, keepTrail: true });
		} else {
			clearTarget();
		}
	}

	function selectOption(select, value) {
		if (!value) {
			return;
		}
		Array.prototype.forEach.call(select.options, function (option) {
			if (option.value === value) {
				select.value = value;
			}
		});
	}

	/* ---------------- ① 대상 찾기 ---------------- */

	function clearFound() {
		K.message(el["kn-find-message"], "");
		el["kn-find-table"].innerHTML = "";
	}

	/** 종류에 맞는 API 로 후보를 찾는다. 하나뿐이면 바로 고른다. */
	function findTargets() {
		var kind = el["kn-find-kind"].value;
		var text = el["kn-find-name"].value.trim();
		clearFound();
		K.message(el["kn-find-message"], "찾는 중...");
		Promise.resolve().then(function () {
			return FINDERS[kind](text);
		}).then(function (found) {
			var rows = found.rows;
			var total = found.total !== undefined ? found.total : rows.length;
			K.table(el["kn-find-table"], found.columns, rows, "찾는 것이 없습니다. 이름을 줄이거나 종류를 바꿔 보세요.");
			var listed = "후보 " + total + "건" + (total > rows.length ? " (앞쪽 " + rows.length + "건만 보임)" : "");
			if (rows.length === 1) {
				K.message(el["kn-find-message"], "한 건이라 바로 골랐습니다.");
				pickTarget(found.toTarget(rows[0]));
				return;
			}
			// 이름을 정확히 적었으면(TBCM_COMMON_CODE) 비슷한 이름(TBCM_COMMON_CODE_DTL)이 같이 나와도 그것을 바로 고른다
			var exact = rows.filter(function (row) {
				return found.exact(row, text);
			});
			if (exact.length === 1) {
				K.message(el["kn-find-message"], "이름이 똑같은 것을 바로 골랐습니다. " + listed + ". 다른 것은 이름을 누르면 고릅니다.");
				pickTarget(found.toTarget(exact[0]));
				return;
			}
			K.message(el["kn-find-message"], rows.length === 0 ? "" : listed + ". 이름을 누르면 대상으로 고릅니다.");
		}).catch(function (err) {
			K.message(el["kn-find-message"], err.message, true);
		});
	}

	/** 종류별 찾기. {columns, rows, total?, toTarget(row)} 를 돌려준다. 첫 칸은 대상으로 고르는 링크다. */
	var FINDERS = {
		method: function (text) {
			// "BoardDAO#deleteBoard" 나 "deleteBoard(…)" 처럼 적어도 찾는다
			var hash = text.indexOf("#");
			var owner = hash >= 0 ? text.substring(0, hash).trim() : "";
			var name = (hash >= 0 ? text.substring(hash + 1) : text).replace(/\(.*$/, "").trim();
			if (!name) {
				throw new Error("메소드 이름을 적으세요. 이름은 정확히 같아야 합니다.");
			}
			return K.call("GET", revisionPath() + "/methods", { name: name, owner: owner, size: 100 }).then(function (result) {
				var toTarget = function (row) {
					return { kind: "method", value: row.methodId, label: shortMethod(row.ownerFqn + "#" + row.signature) };
				};
				return {
					total: result.total,
					rows: result.methods || [],
					toTarget: toTarget,
					// 타입까지 적었고(BoardDAO#deleteBoard) 그 타입이 하나뿐이면
					exact: function (row) { return !!owner && (row.ownerFqn === owner || endsWith(row.ownerFqn, "." + owner)); },
					columns: [
						{ key: "signature", label: "메소드", html: function (row) { return pickLink(toTarget(row), row.ownerFqn + "#" + row.signature); } },
						{ key: "ownerFqn", label: "타입 (전체 이름)", mono: true },
						{ key: "path", label: "위치", html: function (row) { return whereText(row.path, row.lineStart); } }
					]
				};
			});
		},
		table: function (text) {
			return K.call("GET", revisionPath() + "/tables", { name: text, size: 100 }).then(function (result) {
				var toTarget = function (row) {
					return { kind: "table", value: row.table, label: row.table };
				};
				return {
					rows: result.tables || [],
					toTarget: toTarget,
					exact: function (row, typed) { return sameName(row.table, typed); },
					columns: [
						{ key: "table", label: "테이블", html: function (row) { return pickLink(toTarget(row)); } },
						{ key: "reads", label: "읽는 SQL", num: true },
						{ key: "inserts", label: "넣는 SQL", num: true },
						{ key: "updates", label: "고치는 SQL", num: true },
						{ key: "deletes", label: "지우는 SQL", num: true }
					]
				};
			});
		},
		statement: function (text) {
			// "Board.deleteBoard" 는 네임스페이스와 id 로 나눠 찾는다
			var dot = text.lastIndexOf(".");
			var query = { namespace: dot > 0 ? text.substring(0, dot) : "", id: dot > 0 ? text.substring(dot + 1) : text, size: 100 };
			return K.call("GET", revisionPath() + "/mappers", query).then(function (result) {
				var toTarget = function (row) {
					var name = (row.namespace ? row.namespace + "." : "") + row.statementId;
					return { kind: "statement", value: name, label: name };
				};
				return {
					total: result.total,
					rows: result.statements || [],
					toTarget: toTarget,
					exact: function (row, typed) { return sameName(toTarget(row).value, typed); },
					columns: [
						{ key: "statementId", label: "SQL", html: function (row) { return pickLink(toTarget(row)); } },
						{ key: "statementType", label: "종류" },
						{ key: "tables", label: "테이블 (R 읽기 · C 넣기 · U 고치기 · D 지우기)", mono: true },
						{ key: "callers", label: "실행하는 메소드", num: true },
						{ key: "file", label: "위치", html: function (row) { return whereText(row.file, row.lineStart); } }
					]
				};
			});
		},
		type: function (text) {
			return K.call("GET", revisionPath() + "/symbols", { name: text, size: 100 }).then(function (result) {
				var toTarget = function (row) {
					return { kind: "type", value: row.fqn, label: row.simpleName || row.fqn };
				};
				return {
					total: result.total,
					rows: result.types || [],
					toTarget: toTarget,
					exact: function (row, typed) { return sameName(row.fqn, typed) || sameName(row.simpleName, typed); },
					columns: [
						{ key: "simpleName", label: "타입", html: function (row) { return pickLink(toTarget(row), row.fqn); } },
						{ key: "kind", label: "종류" },
						{ key: "layer", label: "계층" },
						{ key: "packageName", label: "패키지", mono: true },
						{ key: "path", label: "위치", html: function (row) { return whereText(row.path, row.lineStart); } }
					]
				};
			});
		}
	};

	/** 대소문자를 가리지 않고 같은 이름인지 */
	function sameName(a, b) {
		return !!a && !!b && String(a).toUpperCase() === String(b).toUpperCase();
	}

	function endsWith(text, tail) {
		return !!text && text.length >= tail.length && text.substring(text.length - tail.length) === tail;
	}

	/* ---------------- ② 대상과 탭 ---------------- */

	function sameTarget(a, b) {
		return !!a && !!b && a.kind === b.kind && a.value === b.value;
	}

	/** 후보 목록이나 결과 표에서 고른 대상. 지금 대상은 지나온 대상에 남긴다. */
	function pickTarget(next) {
		if (sameTarget(next, target)) {
			flashHead();
			return;
		}
		setTarget(next, { push: true, scroll: true });
	}

	/**
	 * 대상을 바꾼다.
	 * options.push: 주소창에 새 기록을 남길지 / options.tab: 열 탭 / options.scroll: 결과로 옮겨 갈지
	 * options.keepTrail: 지나온 대상을 그대로 둘지(주소창에서 되살릴 때)
	 */
	function setTarget(next, options) {
		options = options || {};
		if (!options.keepTrail && target && !sameTarget(target, next)) {
			// 이미 지나온 대상으로 다시 가면 거기까지 잘라 같은 이름이 두 번 남지 않게 한다
			var at = -1;
			trail.forEach(function (item, index) {
				if (sameTarget(item, next)) {
					at = index;
				}
			});
			trail = at >= 0 ? trail.slice(0, at) : trail.concat([target]);
		}
		target = { kind: next.kind, value: next.value, label: next.label || next.value, notes: next.notes || [] };
		loaded = {};
		["impact", "callers", "callees"].forEach(function (name) {
			panels[name].content.innerHTML = "";
			K.message(panels[name].message, "");
			setCount(name, null);
		});
		el["kn-result-panel"].hidden = false;
		renderHead();
		renderTrail();
		var wanted = options.tab || tab;
		showTab(tabEnabled(wanted) ? wanted : "impact", false);
		writeHash(options.push);
		if (options.scroll) {
			flashHead(true);
		}
	}

	function clearTarget() {
		target = null;
		trail = [];
		loaded = {};
		el["kn-result-panel"].hidden = true;
		clearFound();
	}

	/** 호출 관계 탭은 메소드일 때만 */
	function tabEnabled(name) {
		return name === "impact" || (target && target.kind === "method");
	}

	/**
	 * 탭을 보이고, 조건이 바뀌었으면 결과를 다시 불러온다.
	 * byUser 가 true 면 탭을 눌러 바꾼 것이라 주소창 기록을 바꾼다.
	 */
	function showTab(name, byUser) {
		tab = name;
		Array.prototype.forEach.call(el.tabs, function (button) {
			var me = button.getAttribute("data-tab");
			var enabled = tabEnabled(me);
			button.disabled = !enabled;
			button.title = enabled ? "" : "메소드를 대상으로 골랐을 때만 볼 수 있습니다.";
			button.setAttribute("aria-selected", me === name ? "true" : "false");
			button.classList.toggle("kn-tab-on", me === name);
			panels[me].wrap.hidden = me !== name;
		});
		var range = DEPTHS[name];
		el["kn-depth"].min = range.min;
		el["kn-depth"].max = range.max;
		el["kn-depth"].value = range.value;
		el["kn-access-wrap"].hidden = !(name === "impact" && target && target.kind === "table");
		el["kn-tab-hint"].textContent = TAB_HINTS[name];
		if (byUser) {
			writeHash(false);
		}
		if (target) {
			loadTab(name);
		}
	}

	function loadTab(name) {
		var access = name === "impact" && target.kind === "table" ? el["kn-impact-access"].value : "";
		var key = [el["kn-impact-revision"].value, target.kind, target.value, DEPTHS[name].value, access].join("|");
		if (loaded[name] === key) {
			return;
		}
		loaded[name] = key;
		var panel = panels[name];
		var no = ++requestNo[name];
		var current = target;
		panel.content.innerHTML = "";
		setCount(name, null);
		K.message(panel.message, "불러오는 중...");
		Promise.resolve().then(function () {
			if (name === "impact") {
				var query = { depth: DEPTHS.impact.value };
				query[IMPACT_PARAMS[current.kind]] = current.value;
				if (access) {
					query.access = access;
				}
				return K.call("GET", revisionPath() + "/impact", query);
			}
			return K.call("GET", revisionPath() + "/methods/" + encodeURIComponent(current.value) + "/" + name, { depth: DEPTHS[name].value });
		}).then(function (result) {
			if (no !== requestNo[name] || current !== target) {
				return;
			}
			if (name === "impact") {
				renderImpact(panel, result);
			} else {
				renderCalls(panel, result, name === "callers");
			}
		}).catch(function (err) {
			if (no === requestNo[name]) {
				loaded[name] = null;
				K.message(panel.message, err.message, true);
			}
		});
	}

	function setCount(name, count) {
		Array.prototype.forEach.call(el.tabs, function (button) {
			if (button.getAttribute("data-tab") === name) {
				button.querySelector(".kn-tab-count").textContent = count === null || count === undefined ? "" : String(count);
			}
		});
	}

	/**
	 * 대상 머리글. 세 탭이 모두 이 대상에 대한 결과라는 것을 한눈에 보이게 한다.
	 *   [종류]  큰 이름  을(를) 고치면
	 *           설명(기능명 · 전체 이름 · 파일:줄 · 계층 · 한글명 ...)
	 */
	function renderHead() {
		var notes = target.notes || [];
		el["kn-target-head"].innerHTML = "<div class=\"kn-target-head\" tabindex=\"-1\">"
			+ "<span class=\"kn-target-kind\">" + K.escapeHtml(KIND_LABELS[target.kind]) + "</span>"
			+ "<div class=\"kn-target-body\"><div class=\"kn-target-name\">" + K.escapeHtml(target.label)
			+ " <span class=\"kn-target-suffix\">을(를) 고치면</span></div>"
			+ (notes.length ? "<div class=\"kn-target-note\">" + K.escapeHtml(notes.join(" · ")) + "</div>" : "")
			+ "</div></div>";
	}

	/** 지나온 대상: 누르면 그 대상으로 돌아간다 */
	function renderTrail() {
		if (trail.length === 0) {
			el["kn-trail"].innerHTML = "";
			return;
		}
		el["kn-trail"].innerHTML = "<span class=\"kn-trail-label\">지나온 대상</span>" + trail.map(function (item, index) {
			return "<button type=\"button\" class=\"kn-trail-item\" data-trail=\"" + index + "\" title=\"" + K.escapeHtml(KIND_LABELS[item.kind] + " " + item.value) + "\">"
				+ "<span class=\"kn-trail-kind\">" + K.escapeHtml(KIND_LABELS[item.kind]) + "</span> " + K.escapeHtml(item.label) + "</button>";
		}).join("<span class=\"kn-trail-sep\">›</span>") + "<span class=\"kn-trail-sep\">›</span><span class=\"kn-trail-now\">" + K.escapeHtml(target.label) + "</span>";
	}

	/** 결과로 옮겨 가 머리글을 잠깐 강조한다 */
	function flashHead(scroll) {
		var head = el["kn-target-head"].querySelector(".kn-target-head");
		if (!head) {
			return;
		}
		if (scroll !== false) {
			head.scrollIntoView({ behavior: "smooth", block: "start" });
		}
		head.classList.add("kn-flash");
		setTimeout(function () {
			head.classList.remove("kn-flash");
		}, 1600);
	}

	/** 영향도 응답의 target 으로 머리글 설명을 채운다(처음 고를 때는 이름만 알기 때문) */
	function notesFromImpact(t) {
		var notes = [];
		if (t.kind === "METHOD") {
			if (t.title) {
				notes.push(t.title);
			}
			if (t.ownerFqn) {
				notes.push(t.ownerFqn);
			}
		} else if (t.kind === "TYPE") {
			notes.push(t.name);
		} else if (t.kind === "STATEMENT") {
			if (t.statementType) {
				notes.push(t.statementType);
			}
			if (t.matches > 1) {
				notes.push("이름이 같은 statement " + t.matches + "개를 함께 봤습니다");
			}
		} else if (t.kind === "TABLE") {
			if (t.koreanName) {
				notes.push(t.koreanName);
			}
			if (t.statements !== undefined) {
				notes.push("건드리는 SQL " + t.statements + "개");
			}
		}
		if (t.path) {
			notes.push(fileName(t.path) + (t.lineStart ? ":" + t.lineStart : ""));
		}
		if (t.layer) {
			notes.push(t.layer);
		}
		return notes;
	}

	/** 메소드 대상의 머리글 이름과 설명. 주소창에서 되살렸을 때 이름이 ID 로만 남은 경우를 고친다. */
	function updateMethodHead(ownerFqn, signature, title, path, lineStart, layer) {
		if (!ownerFqn || !signature) {
			return;
		}
		target.label = shortMethod(ownerFqn + "#" + signature);
		var notes = [];
		if (title) {
			notes.push(title);
		}
		notes.push(ownerFqn);
		if (path) {
			notes.push(fileName(path) + (lineStart ? ":" + lineStart : ""));
		}
		if (layer) {
			notes.push(layer);
		}
		if (target.notes.length <= notes.length) {
			target.notes = notes;
		}
		renderHead();
		renderTrail();
		writeHash(false);
	}

	/* ---------------- 영향도 탭 ---------------- */

	function renderImpact(panel, result) {
		var t = result.target || {};
		if (API_KINDS[t.kind] === target.kind) {
			target.notes = notesFromImpact(t);
			if (t.kind === "METHOD") {
				updateMethodHead(t.ownerFqn, t.signature, t.title, t.path, t.lineStart, t.layer);
			} else {
				renderHead();
			}
		}
		K.message(panel.message, result.truncated ? "결과가 많아 일부만 보입니다. 깊이를 줄이거나 대상을 좁혀 보세요." : "");
		var groups = impactGroups(result);
		panel.content.innerHTML = "<div class=\"kn-lead\">" + leadHtml(result, groups) + "</div>";
		renderAffected(panel.content, result, groups);
		renderTouches(panel.content, result);
		renderTrust(panel.content, result);
		renderReachedMethods(panel.content, result);
	}

	/**
	 * 맨 위 한 줄 결론. 숫자를 문장으로 바꿔 "그래서 무엇을 하면 되나"가 바로 보이게 한다.
	 *   - 대상 메소드가 그 자체로 입구면 그렇다고 먼저 알린다(영향이 자기 거래 · 화면에서 끝나는 까닭)
	 *   - 영향받는 화면 · 진입점 수, 그 관계가 얼마나 확실한지, 건드리는 테이블, 놓쳤을 수 있는 곳
	 */
	function leadHtml(result, groups) {
		var endpoints = result.endpoints || [];
		var screens = result.screens || [];
		var lines = [];
		if (target.kind === "method") {
			var own = [];
			endpoints.forEach(function (row) {
				if (row.methodId === target.value && row.depth === 0 && own.indexOf(row.path) < 0) {
					own.push(row.path);
				}
			});
			if (own.length > 0) {
				// 이 메소드를 부르는 Java 메소드가 하나도 없을 때만 "영향이 여기서 끝난다"고 말한다
				var calledByJava = (result.methods || []).some(function (row) { return row.depth > 0; });
				lines.push("이 메소드는 " + own.map(function (path) { return "<b class=\"kn-mono\">" + K.escapeHtml(path) + "</b>"; }).join(", ")
					+ "의 입구입니다." + (calledByJava ? "" : " 이 메소드를 부르는 Java 코드가 없어서 영향은 이 입구와 이 입구를 부르는 화면에서 끝납니다."));
			}
		}
		if (endpoints.length + screens.length === 0) {
			lines.push("고쳐도 사용자가 들어오는 입구(진입점 · 화면)에 닿지 않습니다. 부르는 곳이 없거나, 분석이 잇지 못한 호출일 수 있습니다(아래 \"믿을 만한가\" 참고).");
		} else {
			lines.push("고치면 화면 <b>" + screens.length + "</b>개, 진입점 <b>" + endpoints.length + "</b>개가 영향을 받습니다"
				+ (groups.length > 1 ? " (업무 묶음 " + groups.length + "개)" : "") + ". " + confidenceText(endpoints.concat(screens)));
		}
		var touches = result.touches || [];
		if (target.kind === "table" && touches.length > 0) {
			lines.push("이 테이블을 읽는 SQL <b>" + touches[0].reads.length + "</b>개, 쓰는 SQL <b>" + touches[0].writes.length + "</b>개가 있습니다.");
		} else if (touches.length > 0) {
			var writing = touches.filter(function (touch) { return touch.writes.length > 0; }).length;
			lines.push("테이블 <b>" + touches.length + "</b>개를 건드립니다"
				+ (writing === 0 ? "(모두 읽기만)." : "(쓰기 " + writing + "개 · 읽기만 " + (touches.length - writing) + "개)."));
		}
		var missed = gapCount(result, "missedCallers") + gapCount(result, "unresolvedInside");
		if (missed > 0) {
			lines.push("<span class=\"kn-lead-warn\">분석이 놓쳤을 수 있는 곳이 " + missed + "건 있습니다. 아래 \"믿을 만한가\"를 확인하세요.</span>");
		}
		return lines.map(function (line) { return "<p>" + line + "</p>"; }).join("");
	}

	/** 신뢰도 건수를 한 문장으로: 모두 HIGH 면 짧게 */
	function confidenceText(rows) {
		var counts = { HIGH: 0, MEDIUM: 0, LOW: 0 };
		rows.forEach(function (row) {
			counts[row.confidence] = (counts[row.confidence] || 0) + 1;
		});
		if (counts.MEDIUM === 0 && counts.LOW === 0) {
			return "모두 확실한 관계(HIGH)로 이어졌습니다.";
		}
		return "확실(HIGH) " + counts.HIGH + " · 보통(MEDIUM) " + counts.MEDIUM + " · 짐작(LOW) " + counts.LOW + ".";
	}

	function gapCount(result, key) {
		var gap = (result.gaps || {})[key];
		return gap ? gap.count : 0;
	}

	/**
	 * 영향받는 곳을 업무 묶음으로 나눈다. 행 하나 = 진입점 하나 + 그 진입점을 부르거나 그 진입점이 여는 화면들.
	 * 어느 진입점과도 짝이 없는 화면은 그 화면의 폴더 이름으로 따로 묶는다.
	 * 반환: [{name, rows: [{screens: [path], endpoint, confidence}]}] (이름 순)
	 */
	function impactGroups(result) {
		var byName = {};
		var paired = {};
		function add(name, row) {
			if (!byName[name]) {
				byName[name] = { name: name, rows: [], screens: {}, endpoints: 0 };
			}
			byName[name].rows.push(row);
			row.screens.forEach(function (path) {
				byName[name].screens[path] = true;
			});
			if (row.endpoint) {
				byName[name].endpoints++;
			}
		}
		(result.endpoints || []).forEach(function (endpoint) {
			var screens = [];
			(endpoint.screens || []).forEach(function (screen) {
				if (screens.indexOf(screen.path) < 0) {
					screens.push(screen.path);
				}
				paired[screen.path] = true;
			});
			add(businessName(endpoint.handler), { screens: screens, endpoint: endpoint, confidence: endpoint.confidence });
		});
		(result.screens || []).forEach(function (screen) {
			if (!paired[screen.path]) {
				add("화면 " + parentDir(screen.path), { screens: [screen.path], endpoint: null, confidence: screen.confidence, via: screen.via });
			}
		});
		return Object.keys(byName).sort().map(function (name) {
			var group = byName[name];
			group.screenCount = Object.keys(group.screens).length;
			return group;
		});
	}

	/**
	 * 처리하는 메소드의 패키지로 업무 묶음 이름을 정한다. hi.ja.closing.service.JADC1021M#… → closing
	 * service, controller 처럼 어느 업무에나 있는 이름이면 한 칸 위 이름을 쓴다.
	 */
	function businessName(handler) {
		var owner = (handler || "").split("#")[0];
		var parts = owner.substring(0, Math.max(0, owner.lastIndexOf("."))).split(".");
		var i = parts.length - 1;
		while (i > 0 && COMMON_PACKAGES.indexOf(parts[i].toLowerCase()) >= 0) {
			i--;
		}
		return parts[i] || "(패키지 없음)";
	}

	/** 영향받는 곳: 다시 테스트할 목록 */
	function renderAffected(container, result, groups) {
		var total = (result.endpoints || []).length + (result.screens || []).length;
		var wrap = sectionWrap(container, "영향받는 곳", groups.length + "묶음",
			"다시 테스트할 목록입니다. 화면과 그 화면이 부르는 진입점(거래 ID · 주소)을 한 줄로 묶고, 처리하는 클래스의 패키지로 업무를 나눴습니다.");
		if (total === 0) {
			wrap.insertAdjacentHTML("beforeend", "<p class=\"ai-hint\">닿는 진입점과 화면이 없습니다.</p>");
			return;
		}
		// 진입점 종류별 건수: 거래 ID 30 · 스케줄 2
		var kinds = {};
		(result.endpoints || []).forEach(function (row) {
			var label = ENDPOINT_LABELS[row.endpointType] || row.endpointType;
			kinds[label] = (kinds[label] || 0) + 1;
		});
		var kindText = Object.keys(kinds).map(function (label) { return label + " " + kinds[label]; }).join(" · ");
		if (kindText) {
			wrap.insertAdjacentHTML("beforeend", "<div class=\"kn-target-cond\"><span>진입점 종류: " + K.escapeHtml(kindText) + "</span></div>");
		}
		var open = groups.length <= 3;
		groups.forEach(function (group) {
			var details = document.createElement("details");
			details.className = "kn-group";
			details.open = open;
			details.innerHTML = "<summary><b>" + K.escapeHtml(group.name) + "</b> <span class=\"kn-caller-note\">화면 " + group.screenCount
				+ " · 진입점 " + group.endpoints + "</span></summary><div class=\"kn-scroll\"></div>";
			K.table(details.querySelector(".kn-scroll"), [
				{ key: "screens", label: "화면", html: function (row) {
					if (row.screens.length === 0) {
						return "<span class=\"kn-caller-note\">(이어진 화면 없음)</span>";
					}
					return row.screens.map(function (path) {
						return "<span class=\"kn-mono kn-caller-name\" title=\"" + K.escapeHtml(path) + "\">" + K.escapeHtml(fileName(path)) + "</span>";
					}).join("<br>") + (row.via ? " <span class=\"kn-caller-note\">" + K.escapeHtml(row.via.split(",").map(relationLabel).join(", ")) + "</span>" : "");
				} },
				{ key: "endpoint", label: "진입점", html: function (row) {
					var e = row.endpoint;
					if (!e) {
						return "";
					}
					return "<span class=\"kn-mono\">" + K.escapeHtml((e.httpMethod && e.httpMethod !== "ALL" ? e.httpMethod + " " : "") + (e.path || "")) + "</span>"
						+ " <span class=\"kn-caller-note\">" + K.escapeHtml(ENDPOINT_LABELS[e.endpointType] || e.endpointType || "") + "</span>";
				} },
				{ key: "handler", label: "처리하는 메소드", html: function (row) { return row.endpoint ? methodLink(row.endpoint.methodId, row.endpoint.handler) : ""; } },
				{ key: "confidence", label: "신뢰도", html: function (row) { return K.confidence(row.confidence); } }
			], group.rows);
			wrap.appendChild(details);
		});
	}

	/** 건드리는 데이터: 테이블마다 읽는 SQL · 쓰는 SQL */
	function renderTouches(container, result) {
		var touches = result.touches || [];
		var hints = {
			method: "이 메소드에서 끝까지 따라 내려가며 실행하는 SQL이 읽고 쓰는 테이블입니다. 쓰기가 있으면 데이터까지 확인하세요.",
			type: "이 타입의 메소드들에서 끝까지 따라 내려가며 실행하는 SQL이 읽고 쓰는 테이블입니다. 쓰기가 있으면 데이터까지 확인하세요.",
			statement: "이 SQL이 읽고 쓰는 테이블입니다.",
			table: "이 테이블을 읽고 쓰는 SQL입니다."
		};
		var wrap = sectionWrap(container, "건드리는 데이터", "테이블 " + touches.length + "개", hints[target.kind] || "");
		var scroll = document.createElement("div");
		scroll.className = "kn-scroll";
		wrap.appendChild(scroll);
		K.table(scroll, [
			{ key: "table", label: "테이블", html: function (row) { return pickLink({ kind: "table", value: row.table, label: row.table }, row.table); } },
			{ key: "reads", label: "읽기", num: true, html: function (row) { return row.reads.length || ""; } },
			{ key: "writes", label: "쓰기", num: true, html: function (row) { return row.writes.length ? "<b class=\"kn-write\">" + row.writes.length + "</b>" : ""; } },
			{ key: "statements", label: "SQL", html: function (row) {
				var items = row.writes.map(function (name) { return statementItem(name, "쓰기"); })
					.concat(row.reads.map(function (name) { return statementItem(name, "읽기"); }));
				return "<details class=\"kn-targets\"><summary>SQL " + items.length + "개</summary><ul>" + items.join("") + "</ul></details>";
			} }
		], touches, target.kind === "table" ? "이 테이블을 건드리는 SQL이 없습니다." : "실행하는 SQL에서 이어진 테이블이 없습니다.");
	}

	function statementItem(name, access) {
		return "<li>" + pickLink({ kind: "statement", value: name, label: name }, name) + " <span class=\"kn-caller-note\">" + access + "</span></li>";
	}

	/** 믿을 만한가: 신뢰도, 잘림, 조건, 분석이 놓쳤을 수 있는 곳 */
	function renderTrust(container, result) {
		var t = result.target || {};
		var wrap = sectionWrap(container, "믿을 만한가", "", "\"영향 없음\"이 정말 없는 것인지, 분석이 잇지 못한 것인지 가려 봅니다.");
		var conditions = ["거슬러 올라간 깊이 " + result.depth, result.includePossible ? "인터페이스 경유 호출 포함" : "인터페이스 경유 호출 제외"];
		if (t.kind === "TABLE") {
			conditions.unshift({ ALL: "읽기 + 쓰기", READ: "읽기만", WRITE: "쓰기만" }[t.access] || t.access);
		}
		var rows = (result.endpoints || []).concat(result.screens || []);
		var html = "<div class=\"kn-target-cond\">" + conditions.map(function (text) { return "<span>" + K.escapeHtml(text) + "</span>"; }).join("") + "</div><ul class=\"kn-trust\">";
		if (rows.length > 0) {
			html += "<li>진입점 · 화면의 신뢰도: " + confidenceText(rows) + " <span class=\"kn-caller-note\">짐작(LOW)은 이름만 보고 이은 관계가 낀 것입니다.</span></li>";
		}
		var reachedTop = (result.methods || []).some(function (row) { return row.depth === result.depth; });
		if (result.truncated) {
			html += "<li class=\"kn-lead-warn\">결과가 많아 일부만 담았습니다.</li>";
		} else if (reachedTop) {
			html += "<li class=\"kn-lead-warn\">깊이 " + result.depth + "까지 올라가서도 부르는 쪽이 이어졌습니다. 깊이를 늘리면 더 나올 수 있습니다.</li>";
		} else {
			html += "<li>깊이 제한 안에서 끝까지 올라갔습니다.</li>";
		}
		html += gapItem(result, "missedCallers", "놓쳤을 수 있는 부르는 쪽",
			"닿은 메소드와 이름 · 인자 수가 같은데 분석이 풀지 못한 호출입니다. 실제로 이 메소드를 부르는 것이면 영향 범위가 더 넓습니다.");
		if ((result.gaps || {}).unresolvedInside) {
			html += gapItem(result, "unresolvedInside", "놓쳤을 수 있는 건드리는 데이터",
				"따라 내려간 코드 안에서 풀지 못한 호출(SQL이 이어지지 않은 메소드 안의 것만)과, 이름은 있는데 매퍼에서 찾지 못한 SQL입니다.");
		}
		wrap.insertAdjacentHTML("beforeend", html + "</ul>");
	}

	function gapItem(result, key, title, hint) {
		var gap = (result.gaps || {})[key] || { count: 0, samples: [] };
		if (gap.count === 0) {
			return "<li>" + K.escapeHtml(title) + ": 없음</li>";
		}
		var items = gap.samples.map(function (row) {
			var what = row.kind === "SQL"
				? "SQL " + row.name + " (매퍼에 없음)"
				: (row.scopeText ? row.scopeText + "." : "") + row.name + "(…)";
			return "<li><span class=\"kn-mono\">" + K.escapeHtml(what) + "</span> " + whereText(row.path, row.lineStart) + "</li>";
		});
		return "<li class=\"kn-lead-warn\">" + K.escapeHtml(title) + " " + gap.count + "건 <span class=\"kn-caller-note\">" + K.escapeHtml(hint) + "</span>"
			+ "<details class=\"kn-targets\"><summary>" + (gap.count > gap.samples.length ? "처음 " + gap.samples.length + "건 보기" : "목록 보기") + "</summary><ul>"
			+ items.join("") + "</ul></details></li>";
	}

	/** 닿는 메소드는 한 줄로 줄이고 목록은 접어 둔다. 경로는 부르는 쪽 탭이 맡는다 */
	function renderReachedMethods(container, result) {
		var methods = result.methods || [];
		var layers = (result.summary || {}).methodsByLayer || {};
		var start = methods.filter(function (row) { return row.depth === 0; }).length;
		var wrap = sectionWrap(container, "닿는 메소드", methods.length + "개",
			(START_LABELS[target.kind] || "직접 건드리는 메소드") + " " + start + "개에서 부르는 쪽으로 올라가며 닿은 메소드입니다. 이름을 누르면 그 메소드가 새 대상이 됩니다.");
		var layerText = Object.keys(layers).map(function (layer) { return layer + " " + layers[layer]; }).join(" · ");
		var html = "<div class=\"kn-target-cond\">" + (layerText ? "<span>계층별 " + K.escapeHtml(layerText) + "</span>" : "") + "</div>";
		if (tabEnabled("callers")) {
			html += "<p><button type=\"button\" class=\"kn-to-callers\">부르는 쪽 탭에서 경로 보기</button></p>";
		}
		html += "<details class=\"kn-group\"><summary>목록 펼치기</summary><div class=\"kn-scroll\"></div></details>";
		wrap.insertAdjacentHTML("beforeend", html);
		K.table(wrap.querySelector(".kn-group .kn-scroll"), [
			{ key: "depth", label: "거리", num: true },
			{ key: "confidence", label: "신뢰도", html: function (row) { return K.confidence(row.confidence); } },
			{ key: "layer", label: "계층" },
			{ key: "method", label: "메소드", html: function (row) { return methodLink(row.methodId, row.method); } },
			{ key: "path", label: "위치", html: function (row) { return whereText(row.path, row.lineStart); } }
		], methods, "닿는 메소드가 없습니다.");
		var button = wrap.querySelector(".kn-to-callers");
		if (button) {
			button.addEventListener("click", function () {
				showTab("callers", true);
			});
		}
	}

	/** 제목 · 건수 · 한 줄 설명이 달린 빈 구역을 만들어 붙이고 돌려준다 */
	function sectionWrap(container, title, count, hint) {
		var wrap = document.createElement("div");
		wrap.className = "kn-result-section";
		wrap.innerHTML = "<div class=\"kn-section-title\">" + K.escapeHtml(title) + (count ? " <span class=\"kn-section-count\">" + K.escapeHtml(count) + "</span>" : "") + "</div>"
			+ (hint ? "<p class=\"kn-section-hint\">" + K.escapeHtml(hint) + "</p>" : "");
		container.appendChild(wrap);
		return wrap;
	}

	/* ---------------- 부르는 쪽 / 불리는 쪽 탭 ---------------- */

	function renderCalls(panel, result, callers) {
		var method = result.method || {};
		updateMethodHead(method.ownerFqn, method.signature, null, method.path, method.lineStart, null);
		var relations = result[callers ? "callers" : "callees"] || [];
		// 부르는 쪽은 같은 줄을 한 행으로 합친다(공통 스크립트를 불러오는 화면 한 줄이 주소 여러 개로 이어지는 경우).
		var rows = callers ? groupSameLine(relations) : relations;
		setCount(callers ? "callers" : "callees", rows.length);
		// 건수는 탭에 보이므로, 같은 줄을 합쳤거나 잘렸을 때만 알린다
		var notes = [];
		if (rows.length !== relations.length) {
			notes.push("관계 " + relations.length + "개를 같은 줄끼리 " + rows.length + "행으로 합쳤습니다. \"주소 N개\"를 누르면 펼칩니다.");
		}
		if (result.truncated) {
			notes.push("결과가 많아 일부만 보입니다. 깊이를 줄여 보세요.");
		}
		K.message(panel.message, notes.join(" "));
		panel.content.innerHTML = "<div class=\"kn-scroll\"></div>";
		var columns = [
			{ key: "depth", label: "거리", num: true },
			{ key: "relationType", label: "관계", html: function (row) { return relationLabel(row.relationType) + anonymousNote(row); } },
			{ key: "confidence", label: "신뢰도", html: function (row) { return K.confidence(row.confidence); } }
		];
		if (callers) {
			// 부르는 쪽: 짧은 이름(+ 화면명)과, 그것이 어디에 적혀 있는지(근거) 두 칸
			columns.push({ key: "from", label: "부르는 쪽", html: function (row) { return callerCell(row); } });
		} else {
			columns.push({ key: "to", label: "불리는 쪽", html: function (row) { return calleeCell(row); } });
		}
		columns.push({ key: "path", label: "근거 (파일:줄)", html: function (row) { return evidenceCell(row); } });
		K.table(panel.content.firstChild, columns, rows
			, callers ? "부르는 쪽이 없습니다(진입점이거나, 분석이 잇지 못한 호출일 수 있습니다)." : "부르는 것이 없습니다.");
	}

	/** 익명 클래스(new Runnable() { … }) 안에서 부른 호출을 바깥 메소드의 호출로 바꿔 보여 준 행이면 그렇다고 붙인다. */
	function anonymousNote(row) {
		return row.viaAnonymous
			? " <span class=\"kn-caller-note kn-nowrap\" title=\"익명 클래스 안에서 부른 것을 바깥 메소드에서 부른 것으로 보여 줍니다\">익명 클래스 안</span>"
			: "";
	}

	function relationLabel(type) {
		return "<span class=\"kn-nowrap\" title=\"" + K.escapeHtml(type) + "\">" + K.escapeHtml(RELATION_LABELS[type] || type) + "</span>";
	}

	/**
	 * 거리 · 관계 · 신뢰도 · 부르는 쪽 · 파일 · 줄이 모두 같은 행을 하나로 합칩니다. 나온 순서는 그대로 둡니다.
	 * 합친 행의 targets 에 원래 행들을 담아 두고, 화면에서는 "주소 N개"를 눌러 펼쳐 봅니다.
	 */
	function groupSameLine(relations) {
		var rows = [];
		var byKey = {};
		relations.forEach(function (relation) {
			var key = [relation.depth, relation.relationType, relation.confidence, relation.fromId, relation.path, relation.lineStart].join("|");
			var row = byKey[key];
			if (!row) {
				row = {};
				Object.keys(relation).forEach(function (name) {
					row[name] = relation[name];
				});
				row.targets = [];
				byKey[key] = row;
				rows.push(row);
			}
			row.targets.push(relation);
		});
		return rows;
	}

	/**
	 * 부르는 쪽 칸.
	 *   메소드: CommonCodeController#getCommonCode(…)   (누르면 새 대상, 전체 이름은 마우스를 올리면)
	 *   화면:   layout.jsp · 화면명                     (전체 경로는 마우스를 올리면)
	 * 합친 행이면 아래에 "genexon.js를 불러와 주소 5개 요청"을 붙이고, 누르면 주소마다 어디로 이어졌는지 펼친다.
	 */
	function callerCell(row) {
		var screen = row.fromKind === "FILE";
		var full = row.from || row.fromId || "";
		var html = screen
			? "<span class=\"kn-mono kn-caller-name\" title=\"" + K.escapeHtml(full) + "\">" + K.escapeHtml(fileName(full)) + "</span>"
			: methodLink(row.fromId, full);
		if (screen && row.screenName) {
			html += " <span class=\"kn-caller-note\">" + K.escapeHtml(row.screenName) + "</span>";
		}
		var targets = row.targets || [];
		if (targets.length < 2) {
			return html;
		}
		var requests = row.relationType === "REQUESTS";
		var script = sameScript(targets);
		var summary = requests
			? (script ? fileName(script) + "를 불러와 주소 " + targets.length + "개 요청" : "주소 " + targets.length + "개 요청")
			: "대상 " + targets.length + "개";
		var items = targets.map(function (item) {
			var to = methodLink(item.toId, item.to || item.toId || "");
			var text = requests && item.requestPath ? "<span class=\"kn-mono\">" + K.escapeHtml(item.requestPath) + "</span> → " + to : to;
			var where = item.script ? fileName(item.script) + (item.scriptLine ? ":" + item.scriptLine : "") : "";
			return "<li>" + text
				+ (where ? " <span class=\"kn-caller-note\" title=\"" + K.escapeHtml(item.script) + "\">" + K.escapeHtml(where) + "</span>" : "") + "</li>";
		}).join("");
		return html + "<details class=\"kn-targets\"><summary>" + K.escapeHtml(summary) + "</summary><ul>" + items + "</ul></details>";
	}

	/** 불리는 쪽 칸: 메소드 · SQL · 테이블은 누르면 새 대상이 된다 */
	function calleeCell(row) {
		var to = row.to || row.toId || "";
		if (row.toKind === "METHOD") {
			return methodLink(row.toId, to);
		}
		if (row.relationType === "EXECUTES_SQL" || row.toKind === "SQL") {
			return pickLink({ kind: "statement", value: to, label: to });
		}
		if (row.relationType === "READS_TABLE" || row.relationType === "WRITES_TABLE") {
			return pickLink({ kind: "table", value: to, label: to });
		}
		return "<span class=\"kn-mono\">" + K.escapeHtml(to) + "</span>";
	}

	/**
	 * 근거 칸: 이 관계가 적힌 파일과 줄. 화면이 불러온 스크립트에 적힌 주소면 그 스크립트(주소가 하나면 줄까지)를 잇대어 쓴다.
	 *   CommonCodeController.java:38
	 *   commoncode.jsp:141
	 *   layout.jsp:26 → genexon.js        (주소 여러 개: 줄은 펼친 목록에)
	 *   layout.jsp:30 → session.js:17     (주소 하나)
	 */
	function evidenceCell(row) {
		var text = fileName(row.path) + (row.lineStart ? ":" + row.lineStart : "");
		var targets = row.targets || [row];
		var script = row.relationType === "REQUESTS" ? sameScript(targets) : null;
		if (script) {
			text += " → " + fileName(script) + (targets.length === 1 && targets[0].scriptLine ? ":" + targets[0].scriptLine : "");
		}
		return "<span class=\"kn-mono kn-nowrap\" title=\"" + K.escapeHtml((row.path || "") + (script ? " → " + script : "")) + "\">" + K.escapeHtml(text) + "</span>";
	}

	/** 모두 같은 스크립트를 거쳐 이어졌으면 그 스크립트 경로, 아니면 null */
	function sameScript(targets) {
		var script = targets.length > 0 ? targets[0].script : null;
		for (var i = 1; i < targets.length && script; i++) {
			if (targets[i].script !== script) {
				script = null;
			}
		}
		return script || null;
	}

	/* ---------------- 표시 도우미 ---------------- */

	/** 누르면 그 대상으로 바뀌는 이름. 지금 대상이면 링크 대신 굵은 글자. title 은 마우스를 올리면 보일 전체 이름 */
	function pickLink(next, title) {
		var tip = K.escapeHtml(title || next.value);
		if (target && sameTarget(next, target)) {
			return "<b class=\"kn-mono kn-caller-name\" title=\"" + tip + "\">" + K.escapeHtml(next.label) + "</b>";
		}
		return "<a href=\"javascript:void(0)\" class=\"kn-retarget kn-mono\" data-kind=\"" + next.kind + "\" data-value=\"" + K.escapeHtml(next.value)
			+ "\" data-label=\"" + K.escapeHtml(next.label) + "\" title=\"" + tip + " — 누르면 이것을 대상으로\">" + K.escapeHtml(next.label) + "</a>";
	}

	/** 메소드 이름 링크. ID 를 모르면 글자만 */
	function methodLink(methodId, fqn) {
		fqn = fqn || "";
		if (!methodId) {
			return "<span class=\"kn-mono\" title=\"" + K.escapeHtml(fqn) + "\">" + K.escapeHtml(shortMethod(fqn)) + "</span>";
		}
		return pickLink({ kind: "method", value: methodId, label: shortMethod(fqn) }, fqn);
	}

	/** 파일:줄 (전체 경로는 마우스를 올리면) */
	function whereText(path, line) {
		if (!path) {
			return "";
		}
		return "<span class=\"kn-mono kn-nowrap\" title=\"" + K.escapeHtml(path) + "\">" + K.escapeHtml(fileName(path) + (line ? ":" + line : "")) + "</span>";
	}

	/** 경로의 마지막 이름: src/main/webapp/WEB-INF/sitemesh/layout.jsp → layout.jsp */
	function fileName(path) {
		path = path || "";
		return path.substring(path.lastIndexOf("/") + 1);
	}

	/** 화면 경로의 바로 위 폴더: .../WEB-INF/views/board/list.jsp → board/ (이름이 같은 화면을 가려 보게) */
	function parentDir(path) {
		var parts = (path || "").split("/");
		return parts.length > 1 ? parts[parts.length - 2] + "/" : "";
	}

	/** kr.co.gnx.CommonCodeController#getCommonCode(HttpServletRequest,...) → CommonCodeController#getCommonCode(…) */
	function shortMethod(fqn) {
		var hash = fqn.indexOf("#");
		if (hash < 0) {
			return fqn;
		}
		var owner = fqn.substring(0, hash);
		var name = fqn.substring(hash + 1);
		var paren = name.indexOf("(");
		return owner.substring(owner.lastIndexOf(".") + 1) + "#" + (paren < 0 ? name : name.substring(0, paren) + (name.charAt(paren + 1) === ")" ? "()" : "(…)"));
	}

	return { init: init };
})();
