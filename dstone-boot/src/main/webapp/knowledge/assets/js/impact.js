/**
 * "영향도" 화면.
 *
 * 질문은 하나입니다: "이것을 고치면 무엇을 다시 테스트해야 하나"
 *   맨 위   프로젝트 · 리비전 (화면 전체에 적용)
 *   ①      대상: 종류(메소드 / 테이블 / 컬럼 / SQL / 클래스 / 변경분) + 이름 → 후보 → 하나를 고른다
 *   ②      결과: 숫자 네 개 → 다시 테스트할 곳(확실한 관계) → 추정 관계 → 건드리는 데이터 · 확인 필요
 *           (숫자 카드 네 개는 아래 구역 네 개와 이름 · 숫자가 같은 짝이고, 누르면 그 구역으로 갑니다)
 * 줄마다 [왜?]를 누르면 대상에서 거기까지 이어진 길(근거 길)을 보여 줍니다.
 * 누가 누구를 부르는지 메소드 단위로 따라가는 일은 관계 트리 창이 맡습니다("관계 트리 보기").
 * 결과에 나온 이름(밑줄)을 누르면 그것이 새 대상이 됩니다. 지금 상태는 주소창(#...)에 남아서
 * 새로 고침, 뒤로 가기, 링크 전달이 됩니다.
 */
var DstoneKnowledgeImpact = (function () {

	var K = DstoneKnowledge;
	var el = {};

	/** 대상 종류 → 화면 글자 */
	var KIND_LABELS = { method: "메소드", table: "테이블", column: "컬럼", statement: "SQL", type: "클래스", changes: "변경분" };
	/** 대상 종류 → 영향도 API 의 인자 이름 */
	var IMPACT_PARAMS = { method: "methodId", table: "table", column: "column", statement: "statement", type: "type", changes: "changedSince" };

	/** 찾기 칸의 안내 글자 */
	var PLACEHOLDERS = {
		method: "메소드 이름(정확히). 클래스로 좁히려면 BoardDAO#deleteBoard",
		table: "테이블 이름의 일부. 비우면 많이 쓰이는 순서로 전체",
		column: "테이블.컬럼 (예: TB_BOARD.TITLE). 테이블 이름만 적으면 컬럼 목록이 나옵니다",
		statement: "SQL 이름의 일부. 네임스페이스까지면 Board.deleteBoard",
		type: "클래스 이름의 일부(패키지 포함 가능)"
	};

	/** 관계 종류 → 화면 글자. 원래 이름은 마우스를 올리면 보인다. */
	var RELATION_LABELS = {
		CALLS: "호출",
		REQUESTS: "화면 요청",
		CALLS_POSSIBLE_IMPLEMENTATION: "구현체 호출(추정)",
		ENCLOSES_ANONYMOUS: "익명 클래스 안",
		RENDERS: "화면 열기"
	};

	/** 화면이 이어진 방식 → 화면 글자 */
	var SCREEN_VIA = { REQUESTS: "주소 요청", CALLS: "화면 코드", RENDERS: "끝나고 여는 화면", CHANGED: "화면 파일이 바뀜" };

	/** 진입점 종류 → 화면 글자 */
	var ENDPOINT_LABELS = { TRANSACTION: "거래 ID", HTTP: "주소", SCHEDULED: "스케줄", MAIN: "main", THREAD: "스레드", LISTENER: "리스너", SERVLET: "서블릿", JSP: "JSP 주소" };

	/** 지금 대상 {kind, value, label, notes[], graphId}. value 는 API 에 넘기는 값 */
	var target = null;
	/** 지나온 대상(오래된 것이 앞) */
	var trail = [];
	/** 요청 번호. 늦게 온 옛 응답은 버린다 */
	var requestNo = 0;

	function init() {
		["kn-impact-project", "kn-impact-revision", "kn-find-kind", "kn-find-name", "kn-find-base", "kn-find-btn", "kn-find-message", "kn-find-table",
			"kn-result-panel", "kn-target-head", "kn-trail", "kn-impact-message", "kn-impact-body"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});

		el["kn-find-kind"].addEventListener("change", updateKindUi);
		el["kn-find-btn"].addEventListener("click", findTargets);
		el["kn-find-name"].addEventListener("keydown", function (event) {
			if (event.key === "Enter") {
				findTargets();
			}
		});
		// 예시 단추는 칸 값만 바꾼다(change 가 생기지 않는다). 종류에 맞춰 안내 글자를 다시 맞춘다.
		Array.prototype.forEach.call(document.querySelectorAll(".kn-example"), function (button) {
			button.addEventListener("click", function () {
				setTimeout(updateKindUi, 0);
			});
		});
		el["kn-impact-project"].addEventListener("change", function () {
			loadRevisions().then(function () {
				clearTarget();
				updateKindUi();
				writeHash(false);
			});
		});
		el["kn-impact-revision"].addEventListener("change", function () {
			clearFound();
			updateKindUi();
			if (target) {
				loadImpact();
			}
			writeHash(false);
		});
		// 결과와 후보 목록의 밑줄 친 이름: 누르면 그것이 새 대상
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
		// [왜?] 단추: 그 줄 아래의 근거 길을 펴고 접는다
		el["kn-impact-body"].addEventListener("click", function (event) {
			var button = event.target.closest ? event.target.closest("button.kn-why") : null;
			if (button) {
				var row = document.getElementById(button.getAttribute("aria-controls"));
				if (row) {
					row.hidden = !row.hidden;
					button.setAttribute("aria-expanded", row.hidden ? "false" : "true");
					button.textContent = row.hidden ? "왜?" : "접기";
				}
			}
		});
		window.addEventListener("popstate", function () {
			restoreHash();
		});

		updateKindUi();
		K.loadProjects(el["kn-impact-project"]).then(function () {
			var state = readHash();
			selectOption(el["kn-impact-project"], state.p);
			return loadRevisions().then(function () {
				selectOption(el["kn-impact-revision"], state.r);
				updateKindUi();
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

	/**
	 * 종류에 맞춰 입력 칸을 바꾼다. 변경분은 이름 대신 견줄 앞 리비전을 고른다(기본: 바로 앞 리비전).
	 */
	function updateKindUi() {
		var kind = el["kn-find-kind"].value;
		var changes = kind === "changes";
		el["kn-find-name"].hidden = changes;
		el["kn-find-base"].hidden = !changes;
		el["kn-find-name"].placeholder = PLACEHOLDERS[kind] || "";
		if (changes) {
			var current = el["kn-impact-revision"].value;
			var html = "";
			var picked = false;
			var passed = false;
			Array.prototype.forEach.call(el["kn-impact-revision"].options, function (option) {
				if (option.value === current) {
					passed = true;
					return;
				}
				// 목록은 최근 것이 위다. 지금 리비전보다 앞서 만든 것만 견줄 수 있고, 그 가운데 분석이 끝난 첫째를 기본으로 고른다
				if (!passed) {
					return;
				}
				var pick = !picked && option.text.indexOf("(READY)") >= 0;
				picked = picked || pick;
				html += "<option value=\"" + K.escapeHtml(option.value) + "\"" + (pick ? " selected" : "") + ">견줄 리비전 " + K.escapeHtml(option.text) + "</option>";
			});
			el["kn-find-base"].innerHTML = html || "<option value=\"\">견줄 다른 리비전이 없습니다</option>";
		}
	}

	/* ---------------- 주소창(#...)에 지금 상태 남기기 ---------------- */

	/** #p=프로젝트&r=리비전&k=종류&v=값&l=보이는 이름 */
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
			}
			updateKindUi();
			// 지나온 대상의 마지막으로 돌아온 것이면 그 칸을 지운다
			if (trail.length > 0 && state.k && sameTarget(trail[trail.length - 1], { kind: state.k, value: state.v })) {
				trail.pop();
			}
			applyState(state);
		});
	}

	function applyState(state) {
		if (state.k && state.v && KIND_LABELS[state.k]) {
			setTarget({ kind: state.k, value: state.v, label: state.l || state.v }, { push: false, keepTrail: true });
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
			if (found.direct) {
				K.message(el["kn-find-message"], "");
				pickTarget(found.direct);
				return;
			}
			var rows = found.rows;
			var total = found.total !== undefined ? found.total : rows.length;
			K.table(el["kn-find-table"], found.columns, rows, found.empty || "찾는 것이 없습니다. 이름을 줄이거나 종류를 바꿔 보세요.");
			var listed = "후보 " + total + "건" + (total > rows.length ? " (앞쪽 " + rows.length + "건만 보임)" : "");
			if (rows.length === 1 && found.toTarget) {
				K.message(el["kn-find-message"], "한 건이라 바로 골랐습니다.");
				pickTarget(found.toTarget(rows[0]));
				return;
			}
			// 이름을 정확히 적었으면(TBCM_COMMON_CODE) 비슷한 이름(TBCM_COMMON_CODE_DTL)이 같이 나와도 그것을 바로 고른다
			var exact = found.exact ? rows.filter(function (row) {
				return found.exact(row, text);
			}) : [];
			if (exact.length === 1) {
				K.message(el["kn-find-message"], "이름이 똑같은 것을 바로 골랐습니다. " + listed + ". 다른 것은 이름을 누르면 고릅니다.");
				pickTarget(found.toTarget(exact[0]));
				return;
			}
			K.message(el["kn-find-message"], rows.length === 0 ? "" : (found.hint || listed + ". 이름을 누르면 대상으로 고릅니다."));
		}).catch(function (err) {
			K.message(el["kn-find-message"], err.message, true);
		});
	}

	/**
	 * 종류별 찾기. {columns, rows, total?, toTarget(row), exact?(row, text)} 또는 바로 고를 대상 {direct} 를 돌려준다.
	 * 표의 첫 칸은 대상으로 고르는 링크다.
	 */
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
					// 클래스까지 적었고(BoardDAO#deleteBoard) 그 클래스가 하나뿐이면
					exact: function (row) { return !!owner && (row.ownerFqn === owner || endsWith(row.ownerFqn, "." + owner)); },
					columns: [
						{ key: "signature", label: "메소드", html: function (row) { return pickLink(toTarget(row), row.ownerFqn + "#" + row.signature); } },
						{ key: "ownerFqn", label: "클래스 (전체 이름)", mono: true },
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
		column: function (text) {
			// TB_BOARD.TITLE 이면 바로 고른다. 테이블 이름만 적으면 그 테이블의 컬럼 목록(SQL 이 쓰는 것)을 보여 준다
			var dot = text.lastIndexOf(".");
			if (dot > 0 && dot < text.length - 1) {
				var value = text.toUpperCase();
				return { direct: { kind: "column", value: value, label: value } };
			}
			if (!text) {
				throw new Error("테이블.컬럼 이나 테이블 이름을 적으세요.");
			}
			var table = text.toUpperCase();
			return K.call("GET", revisionPath() + "/graph/node", { id: "T:" + table }).then(function (detail) {
				var toTarget = function (row) {
					var name = table + "." + row.column;
					return { kind: "column", value: name, label: name };
				};
				var rows = (detail.columns || []).filter(function (row) {
					return row.statementCount > 0;
				});
				return {
					rows: rows,
					toTarget: null,
					empty: "SQL 이 쓰는 컬럼을 찾지 못했습니다. 테이블 이름이 맞는지 보세요.",
					hint: "SQL 이 쓰는 컬럼 " + rows.length + "개. 이름을 누르면 대상으로 고릅니다.",
					columns: [
						{ key: "column", label: "컬럼", html: function (row) { return pickLink(toTarget(row), table + "." + row.column); } },
						{ key: "koreanName", label: "한글명" },
						{ key: "dataType", label: "타입" },
						{ key: "statementCount", label: "쓰는 SQL", num: true },
						{ key: "crud", label: "CRUD" }
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
						{ key: "simpleName", label: "클래스", html: function (row) { return pickLink(toTarget(row), row.fqn); } },
						{ key: "kind", label: "종류" },
						{ key: "layer", label: "계층" },
						{ key: "packageName", label: "패키지", mono: true },
						{ key: "path", label: "위치", html: function (row) { return whereText(row.path, row.lineStart); } }
					]
				};
			});
		},
		changes: function () {
			var base = el["kn-find-base"].value;
			if (!base) {
				throw new Error("견줄 앞 리비전이 없습니다. 이 프로젝트를 한 번 더 분석해야 변경분을 볼 수 있습니다.");
			}
			revisionPath();
			return { direct: { kind: "changes", value: base, label: "#" + base + " 이후 변경분" } };
		}
	};

	/** 대소문자를 가리지 않고 같은 이름인지 */
	function sameName(a, b) {
		return !!a && !!b && String(a).toUpperCase() === String(b).toUpperCase();
	}

	function endsWith(text, tail) {
		return !!text && text.length >= tail.length && text.substring(text.length - tail.length) === tail;
	}

	/* ---------------- ② 대상과 결과 ---------------- */

	function sameTarget(a, b) {
		return !!a && !!b && a.kind === b.kind && a.value === b.value;
	}

	/** 후보 목록이나 결과에서 고른 대상. 지금 대상은 지나온 대상에 남긴다. */
	function pickTarget(next) {
		if (sameTarget(next, target)) {
			flashHead();
			return;
		}
		setTarget(next, { push: true, scroll: true });
	}

	/**
	 * 대상을 바꾼다.
	 * options.push: 주소창에 새 기록을 남길지 / options.scroll: 결과로 옮겨 갈지
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
		target = { kind: next.kind, value: next.value, label: next.label || next.value, notes: [], graphId: null, access: "ALL" };
		fillFind(target);
		el["kn-result-panel"].hidden = false;
		renderHead();
		renderTrail();
		writeHash(options.push);
		loadImpact();
		if (options.scroll) {
			flashHead(true);
		}
	}

	/**
	 * ① 대상 칸(종류, 이름)을 지금 대상에 맞춘다. 주소창(#k=table&v=…)으로 들어오거나
	 * 결과 안의 링크로 대상을 옮겨도 칸이 비어 있거나 앞 대상으로 남지 않게 한다.
	 */
	function fillFind(current) {
		var kindEl = el["kn-find-kind"];
		var known = Array.prototype.some.call(kindEl.options, function (option) {
			return option.value === current.kind;
		});
		if (!known) {
			return;
		}
		kindEl.value = current.kind;
		updateKindUi();
		if (current.kind === "changes") {
			selectOption(el["kn-find-base"], String(current.value));
		} else if (current.kind === "method") {
			// 메소드의 값은 id 라서, 찾기 칸에 적는 모양(BoardDAO#deleteBoard)인 보이는 이름을 쓴다
			el["kn-find-name"].value = String(current.label || "").replace(/\(.*$/, "");
		} else {
			el["kn-find-name"].value = current.value;
		}
	}

	function clearTarget() {
		target = null;
		trail = [];
		el["kn-result-panel"].hidden = true;
		clearFound();
	}

	function loadImpact() {
		var no = ++requestNo;
		var current = target;
		el["kn-impact-body"].innerHTML = "";
		K.message(el["kn-impact-message"], "불러오는 중...");
		Promise.resolve().then(function () {
			var query = {};
			query[IMPACT_PARAMS[current.kind]] = current.value;
			if ((current.kind === "table" || current.kind === "column") && current.access !== "ALL") {
				query.access = current.access;
			}
			return K.call("GET", revisionPath() + "/impact", query);
		}).then(function (result) {
			if (no !== requestNo || current !== target) {
				return;
			}
			K.message(el["kn-impact-message"], result.truncated ? "결과가 많아 앞쪽 " + 2000 + "건씩만 담았습니다. 대상을 좁혀 보세요." : "");
			describeTarget(result);
			renderImpact(result);
		}).catch(function (err) {
			if (no === requestNo) {
				K.message(el["kn-impact-message"], err.message, true);
			}
		});
	}

	/** 영향도 응답의 target 으로 머리글 설명을 채운다(처음 고를 때는 이름만 알기 때문) */
	function describeTarget(result) {
		var t = result.target || {};
		var notes = [];
		target.graphId = t.graphId || null;
		if (t.kind === "METHOD") {
			if (t.ownerFqn && t.signature) {
				target.label = shortMethod(t.ownerFqn + "#" + t.signature);
			}
			pushIf(notes, t.title);
			pushIf(notes, t.ownerFqn);
		} else if (t.kind === "TYPE") {
			notes.push(t.name);
		} else if (t.kind === "STATEMENT") {
			pushIf(notes, t.statementType);
			if (t.matches > 1) {
				notes.push("이름이 같은 statement " + t.matches + "개를 함께 봤습니다");
			}
		} else if (t.kind === "TABLE") {
			pushIf(notes, t.koreanName);
			if (t.statements !== undefined) {
				notes.push("건드리는 SQL " + t.statements + "개");
			}
		} else if (t.kind === "COLUMN") {
			pushIf(notes, [t.koreanName, t.columnKoreanName].filter(function (v) { return !!v; }).join(" · "));
			pushIf(notes, t.dataType);
			if (t.statements !== undefined) {
				notes.push("이 컬럼을 쓰는 SQL " + t.statements + "개" + (t.unknownStatements ? " · 컬럼을 읽지 못한 SQL " + t.unknownStatements + "개" : ""));
			}
		} else if (t.kind === "CHANGES" && result.changes) {
			var base = result.changes.base || {};
			target.label = "#" + base.revisionId + " → #" + result.revisionId;
			notes.push("견준 리비전 #" + base.revisionId + " " + (base.revisionLabel || "") + " (" + (base.createdAt || "") + ")");
		}
		if (t.path) {
			notes.push(fileName(t.path) + (t.lineStart ? ":" + t.lineStart : ""));
		}
		pushIf(notes, t.layer);
		target.notes = notes;
		renderHead();
		renderTrail();
		writeHash(false);
	}

	function pushIf(list, value) {
		if (value) {
			list.push(value);
		}
	}

	/**
	 * 대상 머리글.
	 *   [종류]  큰 이름  을(를) 고치면                     [관계 트리 보기] [테이블 읽기+쓰기 ▾]
	 *           설명(기능명 · 전체 이름 · 파일:줄 · 계층 · 한글명 ...)
	 */
	function renderHead() {
		var notes = target.notes || [];
		var actions = "";
		if (target.kind === "table" || target.kind === "column") {
			actions += "<label class=\"kn-head-access\">SQL <select data-act=\"access\">"
				+ [["ALL", "읽기 + 쓰기"], ["WRITE", "쓰기만"], ["READ", "읽기만"]].map(function (item) {
					return "<option value=\"" + item[0] + "\"" + (target.access === item[0] ? " selected" : "") + ">" + item[1] + "</option>";
				}).join("") + "</select></label>";
		}
		if (target.graphId) {
			// 관계 트리 창으로 연다(knowledge-common.js 의 data-tree-window)
			var treeUrl = K.treeUrl(el["kn-impact-project"].value, el["kn-impact-revision"].value, target.graphId);
			actions += "<a class=\"kn-head-link\" href=\"" + K.escapeHtml(treeUrl) + "\" target=\"" + K.TREE_WINDOW + "\" data-tree-window"
				+ " title=\"부르는 쪽과 불리는 쪽을 메소드 단위로 그린 그림을 새 창에 엽니다\">관계 트리 보기</a>";
		}
		var suffix = target.kind === "changes" ? "에서 바뀐 것" : "을(를) 고치면";
		el["kn-target-head"].innerHTML = "<div class=\"kn-target-head\" tabindex=\"-1\">"
			+ "<span class=\"kn-target-kind\">" + K.escapeHtml(KIND_LABELS[target.kind]) + "</span>"
			+ "<div class=\"kn-target-body\"><div class=\"kn-target-name\">" + K.escapeHtml(target.label)
			+ " <span class=\"kn-target-suffix\">" + suffix + "</span></div>"
			+ (notes.length ? "<div class=\"kn-target-note\">" + K.escapeHtml(notes.join(" · ")) + "</div>" : "")
			+ "</div>" + (actions ? "<div class=\"kn-head-actions\">" + actions + "</div>" : "") + "</div>";
		var select = el["kn-target-head"].querySelector("select[data-act=access]");
		if (select) {
			select.addEventListener("change", function () {
				target.access = select.value;
				loadImpact();
			});
		}
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

	/* ---------------- 결과 그리기 ---------------- */

	function renderImpact(result) {
		lastNodes = result.nodes || {};
		var rows = testRows(result);
		var sure = rows.filter(function (row) { return row.confidence === "HIGH"; });
		var guessed = rows.filter(function (row) { return row.confidence !== "HIGH"; });
		var checks = checkItems(result);
		var body = el["kn-impact-body"];
		body.innerHTML = cardsHtml(result, sure, guessed, checks) + leadHtml(result, rows, sure, guessed);

		var testSection = sectionWrap(body, "다시 테스트할 곳", sure.length + "곳 (" + rowsUnit(sure) + ")",
			"확실한 관계(HIGH)로 이어진 진입점과, 그 진입점을 부르는 화면입니다. 한 줄이 한 곳입니다. [왜?]를 누르면 대상에서 거기까지 이어진 길이 나옵니다.");
		testSection.setAttribute("data-section", "test");
		testSection.insertAdjacentHTML("beforeend", rowsTable(sure, result, false, "확실한 관계로 이어진 화면 · 진입점이 없습니다."));

		// 카드와 짝을 맞추려고 0곳이어도 구역을 둔다(접힌 채로).
		var details = document.createElement("details");
		details.className = "kn-result-section kn-guessed";
		details.setAttribute("data-section", "guessed");
		details.innerHTML = "<summary><span class=\"kn-section-title\">추정 관계</span> <span class=\"kn-section-count\">" + guessed.length + "곳 (" + rowsUnit(guessed) + ")</span></summary>"
			+ "<p class=\"kn-section-hint\">이어지는 길에 덜 확실한 관계(MEDIUM: 화면 안의 주소 글자, 인터페이스를 거친 호출 등)나 "
			+ "짐작(LOW: 이름만 보고 이은 관계, 어느 메소드가 불리는지 모르는 진입점)이 낀 곳입니다. 실제로 이어지는지 [왜?]로 확인하세요.</p>"
			+ rowsTable(guessed, result, true, "추정 관계로만 이어진 곳이 없습니다.");
		body.appendChild(details);

		var grid = document.createElement("div");
		grid.className = "kn-impact-grid";
		body.appendChild(grid);
		renderTouches(grid, result);
		renderChecks(grid, result, checks);
		bindCards(body);
	}

	/**
	 * 다시 테스트할 줄 목록. 줄 하나 = 진입점 하나 + 그 진입점을 요청하거나 그 진입점이 여는 화면들.
	 * 어느 진입점과도 짝이 없는 화면은 따로 한 줄씩.
	 */
	function testRows(result) {
		var screenByPath = {};
		(result.screens || []).forEach(function (screen) {
			screenByPath[screen.path] = screen;
		});
		var paired = {};
		var rows = (result.endpoints || []).map(function (endpoint) {
			var screens = (endpoint.screens || []).map(function (link) {
				paired[link.path] = true;
				return screenByPath[link.path] || link;
			});
			return { endpoint: endpoint, screens: uniqueScreens(screens), confidence: endpoint.confidence, depth: endpoint.depth, chain: endpoint.chain || [] };
		});
		(result.screens || []).forEach(function (screen) {
			if (!paired[screen.path]) {
				rows.push({ endpoint: null, screens: [screen], confidence: screen.confidence, depth: screen.depth, chain: screen.chain || [], chainVia: screen.chainVia });
			}
		});
		rows.sort(function (a, b) {
			return a.depth - b.depth;
		});
		return rows;
	}

	function uniqueScreens(screens) {
		var seen = {};
		return screens.filter(function (screen) {
			if (seen[screen.path]) {
				return false;
			}
			seen[screen.path] = true;
			return true;
		});
	}

	/**
	 * 맨 위 숫자 네 개. 아래 구역 네 개(다시 테스트할 곳 · 추정 관계 · 건드리는 데이터 · 확인 필요)와 하나씩 짝이고,
	 * 카드의 숫자는 짝인 구역 제목 옆의 숫자와 같습니다. 카드를 누르면 그 구역으로 갑니다.
	 */
	function cardsHtml(result, sure, guessed, checks) {
		var data = dataCount(result);
		var html = "<div class=\"kn-cards kn-impact-cards\">"
			+ card("test", sure.length, "다시 테스트할 곳", rowsUnit(sure), "")
			+ card("guessed", guessed.length, "추정 관계", rowsUnit(guessed), "")
			+ card("data", data.value, "건드리는 데이터", data.help, data.writes > 0 ? "kn-card-danger" : "")
			+ card("checks", checks.count, "확인 필요", checks.count === 0 ? "분석이 놓친 곳 없음" : "분석이 놓쳤을 수 있는 곳", checks.count > 0 ? "kn-card-warn" : "kn-card-ok")
			+ "</div>";
		if (result.changes) {
			var c = result.changes;
			var m = c.methods || {};
			html += "<p class=\"kn-change-line\">바뀐 메소드 <b>" + ((m.ADDED || 0) + (m.BODY || 0) + (m.SIGNATURE || 0) + (m.FILE || 0)) + "</b>"
				+ " (새로 생김 " + (m.ADDED || 0) + " · 내용 " + (m.BODY || 0) + " · 반환 타입/제한자 " + (m.SIGNATURE || 0)
				+ (m.FILE ? " · 파일 단위 " + m.FILE : "") + ") · 바뀐 SQL <b>" + c.statements + "</b> · 바뀐 화면 파일 <b>" + c.screens + "</b>"
				+ (c.removedMethods ? " · 없어진 메소드 " + c.removedMethods : "") + "</p>";
		}
		return html;
	}

	/** 누르면 짝인 구역(data-jump)으로 가는 카드 */
	function card(jump, value, label, help, css) {
		return "<button type=\"button\" class=\"kn-card kn-card-button " + (css || "") + "\" data-jump=\"" + jump + "\" title=\"아래 '" + K.escapeHtml(label) + "' 구역으로\">"
			+ "<div class=\"kn-card-value\">" + K.escapeHtml(String(value)) + "</div>"
			+ "<div class=\"kn-card-label\">" + K.escapeHtml(label) + "</div><div class=\"kn-card-help\">" + K.escapeHtml(help) + "</div></button>";
	}

	/** 줄 묶음의 단위 풀이: "진입점 4 · 화면 5". 줄 하나 = 진입점 하나 + 그 화면들, 또는 진입점 없는 화면 하나 */
	function rowsUnit(rows) {
		var endpoints = 0;
		var screens = {};
		var screenCount = 0;
		rows.forEach(function (row) {
			if (row.endpoint) {
				endpoints++;
			}
			row.screens.forEach(function (screen) {
				if (!screens[screen.path]) {
					screens[screen.path] = true;
					screenCount++;
				}
			});
		});
		return "진입점 " + endpoints + " · 화면 " + screenCount;
	}

	/**
	 * "건드리는 데이터" 카드와 구역 제목의 숫자.
	 * 테이블 · 컬럼이 대상이면 그것을 쓰는 SQL 수, 그 밖에는 실행하는 SQL 이 닿는 테이블 수입니다.
	 */
	function dataCount(result) {
		var touches = result.touches || [];
		var writes = 0;
		var reads = 0;
		var unsure = 0;
		var writtenTables = 0;
		touches.forEach(function (touch) {
			writes += touch.writes.length;
			reads += touch.reads.length;
			unsure += (touch.unsure || []).length;
			if (touch.writes.length > 0) {
				writtenTables++;
			}
		});
		if (target.kind === "table" || target.kind === "column") {
			return { value: writes + reads + unsure, unit: "SQL", writes: writes,
				help: "쓰기 " + writes + " · 읽기 " + reads + (unsure ? " · 컬럼 모름 " + unsure : "") };
		}
		return { value: touches.length, unit: "테이블", writes: writtenTables, help: "쓰는 테이블 " + writtenTables + " · 읽기만 " + (touches.length - writtenTables) };
	}

	/** 카드를 누르면 짝인 구역으로 스크롤합니다. 접힌 "추정 관계"는 펼칩니다 */
	function bindCards(body) {
		Array.prototype.forEach.call(body.querySelectorAll(".kn-impact-cards [data-jump]"), function (button) {
			button.addEventListener("click", function () {
				var section = body.querySelector("[data-section=\"" + button.getAttribute("data-jump") + "\"]");
				if (!section) {
					return;
				}
				if (section.tagName === "DETAILS") {
					section.open = true;
				}
				section.scrollIntoView({ behavior: "smooth", block: "start" });
			});
		});
	}

	/** 숫자 아래 한 줄 결론 */
	function leadHtml(result, rows, sure, guessed) {
		var lines = [];
		if (target.kind === "method") {
			var own = (result.endpoints || []).filter(function (row) {
				return row.methodId === target.value && row.depth === 0;
			}).map(function (row) {
				return "<b class=\"kn-mono\">" + K.escapeHtml(row.path) + "</b>";
			});
			if (own.length > 0) {
				lines.push("이 메소드 자체가 진입점(" + own.join(", ") + ")입니다.");
			}
		}
		if (rows.length === 0) {
			lines.push("고쳐도 닿는 화면이나 진입점이 없습니다. 부르는 곳이 없거나, 분석이 잇지 못한 호출일 수 있습니다(\"확인 필요\" 참고).");
		} else if (guessed.length === 0) {
			lines.push("모두 확실한 관계로 이어진 " + sure.length + "곳을 다시 테스트하면 됩니다.");
		} else {
			lines.push("확실한 " + sure.length + "곳을 다시 테스트하고, 추정 관계 " + guessed.length + "곳은 실제로 이어지는지 확인하세요.");
		}
		return "<div class=\"kn-lead\">" + lines.map(function (line) { return "<p>" + line + "</p>"; }).join("") + "</div>";
	}

	var rowSeq = 0;

	/** 다시 테스트할 줄 표. 줄마다 [왜?] 단추와 접힌 근거 길 줄이 붙는다 */
	function rowsTable(rows, result, withConfidence, emptyText) {
		if (rows.length === 0) {
			return "<p class=\"ai-hint\">" + K.escapeHtml(emptyText) + "</p>";
		}
		var cols = withConfidence ? 6 : 5;
		var html = "<div class=\"kn-scroll\"><table class=\"document-list-table kn-test-table\"><thead><tr><th>화면</th><th>진입점</th><th>처리하는 메소드</th>"
			+ (withConfidence ? "<th>신뢰도</th>" : "") + "<th class=\"kn-num\">거리</th><th></th></tr></thead><tbody>";
		rows.forEach(function (row) {
			var id = "kn-why-" + (++rowSeq);
			html += "<tr><td>" + screensCell(row) + "</td><td>" + endpointCell(row.endpoint) + "</td><td>" + handlerCell(row) + "</td>"
				+ (withConfidence ? "<td>" + K.confidence(row.confidence) + "</td>" : "")
				+ "<td class=\"kn-num\">" + row.depth + "</td>"
				+ "<td><button type=\"button\" class=\"kn-why\" aria-expanded=\"false\" aria-controls=\"" + id + "\">왜?</button></td></tr>"
				+ "<tr id=\"" + id + "\" class=\"kn-why-row\" hidden><td colspan=\"" + cols + "\">" + chainHtml(row) + "</td></tr>";
		});
		return html + "</tbody></table></div>";
	}

	function screensCell(row) {
		if (row.screens.length === 0) {
			return "<span class=\"kn-caller-note\">(이어진 화면 없음)</span>";
		}
		return row.screens.map(function (screen) {
			var conf = screen.confidence && screen.confidence !== "HIGH" ? " " + K.confidence(screen.confidence) : "";
			var via = row.endpoint ? "" : " <span class=\"kn-caller-note\">" + K.escapeHtml(viaText(screen.via)) + "</span>";
			return "<span class=\"kn-mono kn-caller-name\" title=\"" + K.escapeHtml(screen.path) + "\">" + K.escapeHtml(fileName(screen.path)) + "</span>"
				+ (screen.screenName ? " <span class=\"kn-caller-note\">" + K.escapeHtml(screen.screenName) + "</span>" : "") + conf + via;
		}).join("<br>");
	}

	function viaText(via) {
		return String(via || "").split(",").map(function (item) {
			return SCREEN_VIA[item] || item;
		}).join(" · ");
	}

	function endpointCell(endpoint) {
		if (!endpoint) {
			return "";
		}
		return "<span class=\"kn-mono\">" + K.escapeHtml((endpoint.httpMethod && endpoint.httpMethod !== "ALL" ? endpoint.httpMethod + " " : "") + (endpoint.path || "")) + "</span>"
			+ " <span class=\"kn-caller-note\">" + K.escapeHtml(ENDPOINT_LABELS[endpoint.endpointType] || endpoint.endpointType || "") + "</span>";
	}

	function handlerCell(row) {
		if (row.endpoint) {
			if (row.endpoint.matchedBy === "TYPE") {
				return "<span class=\"kn-mono\" title=\"" + K.escapeHtml(row.endpoint.handler) + "\">" + K.escapeHtml(simpleName(row.endpoint.handler))
					+ "</span> <span class=\"kn-caller-note\">(어느 메소드인지 모름)</span>";
			}
			return methodLink(row.endpoint.methodId, row.endpoint.handler);
		}
		var first = row.chain[0];
		return first && nodeKind(first) === "METHOD" ? methodLink(first.id, nodeLabel(first)) : "";
	}

	/** 근거 길에 쓸 이름 표(마지막 응답의 nodes) */
	var lastNodes = {};

	function nodeKind(step) {
		return (lastNodes[step.id] || {}).kind || "METHOD";
	}

	function nodeLabel(step) {
		return (lastNodes[step.id] || {}).name || step.id;
	}

	/**
	 * 근거 길: 화면 → 진입점 → 처리하는 메소드 → ... → 대상을 직접 건드리는 메소드(그 이유)
	 * 관계가 그냥 호출(HIGH)이 아니면 그 관계와 신뢰도를 화살표 옆에 적는다. 약한 고리가 어디인지 보이게.
	 */
	function chainHtml(row) {
		var items = [];
		if (row.endpoint) {
			// 이 진입점을 요청하거나 이 진입점이 끝나고 여는 화면들은 한 줄에 모은다
			if (row.screens.length > 0) {
				items.push("<span class=\"kn-chain-kind\">화면</span> " + row.screens.map(function (screen) {
					return "<span class=\"kn-mono\" title=\"" + K.escapeHtml(screen.path) + "\">" + K.escapeHtml(fileName(screen.path))
						+ "</span> <span class=\"kn-caller-note\">(" + K.escapeHtml(viaText(screen.via)) + ")</span>";
				}).join(", "));
			}
			items.push("<span class=\"kn-chain-kind\">진입점</span> " + endpointCell(row.endpoint)
				+ (row.endpoint.matchedBy === "TYPE" ? " <span class=\"kn-chain-rel kn-chain-weak\">클래스까지만 이어짐 · 짐작(LOW)</span>" : ""));
		} else if (row.chainVia === "CHANGED") {
			items.push("<span class=\"kn-chain-kind\">화면</span> <span class=\"kn-mono\">" + K.escapeHtml(row.screens[0].path) + "</span> <span class=\"kn-caller-note\">화면 파일 자체가 바뀌었습니다</span>");
		} else if (row.chainVia === "RENDERS") {
			items.push("<span class=\"kn-chain-kind\">화면</span> <span class=\"kn-mono\">" + K.escapeHtml(fileName(row.screens[0].path)) + "</span> <span class=\"kn-caller-note\">아래 메소드가 끝나고 이 화면을 엽니다</span>");
		}
		row.chain.forEach(function (step, index) {
			var kind = nodeKind(step);
			var name = nodeLabel(step);
			var node = kind === "FILE"
				? "<span class=\"kn-chain-kind\">화면</span> <span class=\"kn-mono\" title=\"" + K.escapeHtml(name) + "\">" + K.escapeHtml(fileName(name)) + "</span>"
				: methodLink(step.id, name);
			var note = "";
			if (index === row.chain.length - 1) {
				note = step.via ? " <span class=\"kn-chain-via\">" + K.escapeHtml(step.via) + "</span>" : " <span class=\"kn-chain-via\">대상</span>";
			} else if (step.relation && (step.relation !== "CALLS" || step.confidence !== "HIGH")) {
				var weak = step.confidence !== "HIGH";
				note = " <span class=\"kn-chain-rel" + (weak ? " kn-chain-weak" : "") + "\" title=\"" + K.escapeHtml(step.relation) + "\">"
					+ K.escapeHtml((RELATION_LABELS[step.relation] || step.relation) + (weak ? " · " + step.confidence : "")) + "</span>";
			}
			items.push(node + note);
		});
		if (items.length === 0) {
			return "<span class=\"kn-caller-note\">길 정보가 없습니다.</span>";
		}
		return "<ol class=\"kn-chain\">" + items.map(function (item) { return "<li>" + item + "</li>"; }).join("") + "</ol>";
	}

	/** 건드리는 데이터: 테이블마다 읽는 SQL · 쓰는 SQL */
	function renderTouches(container, result) {
		var touches = result.touches || [];
		var hints = {
			method: "이 메소드에서 끝까지 따라 내려가며 실행하는 SQL이 읽고 쓰는 테이블입니다.",
			type: "이 클래스의 메소드들에서 끝까지 따라 내려가며 실행하는 SQL이 읽고 쓰는 테이블입니다.",
			changes: "바뀐 메소드에서 끝까지 따라 내려가며 실행하는 SQL이 읽고 쓰는 테이블입니다.",
			statement: "이 SQL이 읽고 쓰는 테이블입니다.",
			table: "이 테이블을 읽고 쓰는 SQL입니다.",
			column: "이 컬럼을 쓰는 SQL입니다."
		};
		var data = dataCount(result);
		var wrap = sectionWrap(container, "건드리는 데이터", data.unit + " " + data.value + "개 (" + data.help + ")", hints[target.kind] || "");
		wrap.setAttribute("data-section", "data");
		var scroll = document.createElement("div");
		scroll.className = "kn-scroll";
		wrap.appendChild(scroll);
		var columns = [
			{ key: "table", label: "테이블", html: function (row) { return pickLink({ kind: "table", value: row.table, label: row.table }, row.table); } },
			{ key: "reads", label: "읽기", num: true, html: function (row) { return row.reads.length || ""; } },
			{ key: "writes", label: "쓰기", num: true, html: function (row) { return row.writes.length ? "<b class=\"kn-write\">" + row.writes.length + "</b>" : ""; } }
		];
		if (target.kind === "column") {
			columns.push({ key: "unsure", label: "컬럼 모름", num: true, html: function (row) { return (row.unsure || []).length || ""; } });
		}
		columns.push({ key: "statements", label: "SQL", html: function (row) {
			var items = row.writes.map(function (name) { return statementItem(name, "쓰기"); })
				.concat(row.reads.map(function (name) { return statementItem(name, "읽기"); }))
				.concat((row.unsure || []).map(function (name) { return statementItem(name, "컬럼을 읽지 못함"); }));
			return "<details class=\"kn-targets\"><summary>SQL " + items.length + "개</summary><ul>" + items.join("") + "</ul></details>";
		} });
		K.table(scroll, columns, touches, target.kind === "table" || target.kind === "column" ? "건드리는 SQL이 없습니다." : "실행하는 SQL에서 이어진 테이블이 없습니다.");
	}

	function statementItem(name, access) {
		return "<li>" + pickLink({ kind: "statement", value: name, label: name }, name) + " <span class=\"kn-caller-note\">" + access + "</span></li>";
	}

	/**
	 * 확인 필요: 분석이 놓쳤을 수 있는 곳. count 가 맨 위 카드의 숫자다.
	 *   놓쳤을 수 있는 부르는 쪽(이름 · 인자 수만 같은 풀지 못한 호출), 놓쳤을 수 있는 데이터(풀지 못한 호출, 매퍼에 없는 SQL),
	 *   컬럼을 읽지 못한 SQL, 파일 단위로만 견준 변경분, 잘린 결과
	 */
	function checkItems(result) {
		var items = [];
		var count = 0;
		var gaps = result.gaps || {};
		if (gaps.missedCallers && gaps.missedCallers.count > 0) {
			count += gaps.missedCallers.count;
			items.push(gapItem(gaps.missedCallers, "놓쳤을 수 있는 부르는 쪽",
				"닿은 메소드와 이름 · 인자 수가 같은데 분석이 풀지 못한 호출입니다. 실제로 이 메소드를 부르는 것이면 영향 범위가 더 넓습니다."));
		}
		if (gaps.unresolvedInside && gaps.unresolvedInside.count > 0) {
			count += gaps.unresolvedInside.count;
			items.push(gapItem(gaps.unresolvedInside, "놓쳤을 수 있는 데이터",
				"따라 내려간 코드 안에서 풀지 못한 호출(SQL이 이어지지 않은 메소드 안의 것)과, 이름은 있는데 매퍼에서 찾지 못한 SQL입니다."));
		}
		var unsure = 0;
		(result.touches || []).forEach(function (touch) {
			unsure += (touch.unsure || []).length;
		});
		if (unsure > 0) {
			count += unsure;
			items.push("<li class=\"kn-lead-warn\">컬럼을 읽지 못한 SQL " + unsure + "개 <span class=\"kn-caller-note\">SELECT * 나 COUNT(*)처럼 컬럼 이름이 SQL에 없어서 "
				+ "이 컬럼을 쓰는지 알 수 없습니다. 추정 관계(LOW)에 넣어 두었으니 직접 확인하세요.</span></li>");
		}
		var fileLevel = result.changes && result.changes.methods ? result.changes.methods.FILE || 0 : 0;
		if (fileLevel > 0) {
			count += 1;
			items.push("<li class=\"kn-lead-warn\">파일 단위로 견준 메소드 " + fileLevel + "개 <span class=\"kn-caller-note\">한쪽 리비전에 메소드 단위 지문이 없어서(분석기 1.5 전에 분석함) "
				+ "바뀐 파일의 메소드를 모두 넣었습니다(MEDIUM). 두 리비전을 다시 분석하면 바뀐 메소드만 남습니다.</span></li>");
		}
		if (result.truncated) {
			count += 1;
			items.push("<li class=\"kn-lead-warn\">결과가 많아 일부만 담았습니다.</li>");
		}
		return { count: count, items: items };
	}

	function renderChecks(container, result, checks) {
		var wrap = sectionWrap(container, "확인 필요", checks.count + "건",
			"\"영향 없음\"이 정말 없는 것인지, 분석이 잇지 못한 것인지 가려 봅니다.");
		wrap.setAttribute("data-section", "checks");
		var conditions = ["부르는 쪽을 끝까지 따라감 (가장 먼 거리 " + result.depth + ")",
			result.includePossible ? "인터페이스를 거친 호출 포함" : "인터페이스를 거친 호출 제외"];
		var html = "<div class=\"kn-target-cond\">" + conditions.map(function (text) { return "<span>" + K.escapeHtml(text) + "</span>"; }).join("") + "</div>";
		html += "<ul class=\"kn-trust\">" + (checks.items.length ? checks.items.join("") : "<li class=\"kn-ok\">분석이 놓쳤을 수 있는 곳이 없습니다.</li>") + "</ul>";
		wrap.insertAdjacentHTML("beforeend", html);
	}

	function gapItem(gap, title, hint) {
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

	/** 제목 · 건수 · 한 줄 설명이 달린 빈 구역을 만들어 붙이고 돌려준다 */
	function sectionWrap(container, title, count, hint) {
		var wrap = document.createElement("div");
		wrap.className = "kn-result-section";
		wrap.innerHTML = "<div class=\"kn-section-title\">" + K.escapeHtml(title) + (count ? " <span class=\"kn-section-count\">" + K.escapeHtml(count) + "</span>" : "") + "</div>"
			+ (hint ? "<p class=\"kn-section-hint\">" + K.escapeHtml(hint) + "</p>" : "");
		container.appendChild(wrap);
		return wrap;
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

	/** com.sample.BoardAdminAction → BoardAdminAction */
	function simpleName(fqn) {
		fqn = fqn || "";
		return fqn.substring(fqn.lastIndexOf(".") + 1);
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
