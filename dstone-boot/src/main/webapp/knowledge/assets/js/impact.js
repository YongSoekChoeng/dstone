/**
 * "영향도 · 호출 관계" 화면.
 */
var DstoneKnowledgeImpact = (function () {

	var K = DstoneKnowledge;
	var el = {};
	/** 호출 관계에서 [영향도]로 고른 메소드. 대상 칸에는 읽을 수 있는 이름을 보이고, 실제로는 이 ID로 분석한다. */
	var picked = null;

	/** 대상 종류(API 값) → 화면 글자 */
	var KIND_LABELS = { TABLE: "테이블", STATEMENT: "SQL", TYPE: "타입", METHOD: "메소드" };

	function init() {
		["kn-impact-project", "kn-impact-revision", "kn-impact-kind", "kn-impact-target", "kn-impact-access", "kn-impact-depth", "kn-impact-btn",
			"kn-impact-message", "kn-impact-head", "kn-impact-cards", "kn-impact-result", "kn-method-name", "kn-method-owner", "kn-method-btn", "kn-table-name", "kn-table-btn",
			"kn-graph-depth", "kn-graph-message", "kn-method-table", "kn-graph-title", "kn-graph-table"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		el["kn-impact-project"].addEventListener("change", loadRevisions);
		el["kn-impact-btn"].addEventListener("click", function () {
			var kind = el["kn-impact-kind"].value;
			var text = el["kn-impact-target"].value.trim();
			// 고른 메소드의 이름을 그대로 두고 다시 누르면 그 메소드로. 칸을 고쳤으면 적힌 글자(메소드 ID)로 찾는다.
			if (kind === "methodId" && picked && text === picked.label) {
				runImpact(kind, picked.methodId, picked.label);
			} else {
				runImpact(kind, text);
			}
		});
		el["kn-method-btn"].addEventListener("click", findMethods);
		el["kn-table-btn"].addEventListener("click", findTables);

		K.loadProjects(el["kn-impact-project"]).then(loadRevisions).catch(function (err) {
			K.message(el["kn-impact-message"], err.message, true);
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

	/* ---------------- 영향도 ---------------- */

	/**
	 * 영향도를 분석합니다. label 은 메소드를 고른 경우에만 줍니다(대상 칸에 메소드 ID 대신 보일 이름).
	 * jump 가 true 면 결과가 나온 뒤 결과 머리글로 옮겨 갑니다(위쪽 호출 관계에서 실행한 경우).
	 */
	function runImpact(kind, target, label, jump) {
		var query = { access: el["kn-impact-access"].value, depth: el["kn-impact-depth"].value };
		query[kind] = target;
		picked = kind === "methodId" && label ? { methodId: target, label: label } : null;
		el["kn-impact-kind"].value = kind;
		el["kn-impact-target"].value = picked ? label : target;
		el["kn-impact-head"].innerHTML = "";
		el["kn-impact-cards"].innerHTML = "";
		el["kn-impact-result"].innerHTML = "";
		K.message(el["kn-impact-message"], "분석 중...");
		Promise.resolve().then(function () {
			return K.call("GET", revisionPath() + "/impact", query);
		}).then(function (result) {
			K.message(el["kn-impact-message"], result.truncated ? "결과가 많아 일부만 보입니다. 깊이를 줄이거나 대상을 좁혀 보세요." : "");
			el["kn-impact-head"].innerHTML = targetHead(result);
			K.cards(el["kn-impact-cards"], [
				{ label: START_LABELS[result.target.kind] || "직접 건드리는 메소드", value: result.summary.startMethods },
				{ label: "닿는 메소드", value: result.summary.methods },
				{ label: "진입점", value: result.summary.endpoints },
				{ label: "화면", value: result.summary.screens }
			]);
			section(el["kn-impact-result"], "진입점 (사용자가 이 변경을 겪게 되는 입구)", [
				{ key: "depth", label: "거리", num: true },
				{ key: "confidence", label: "신뢰도", html: function (row) { return K.confidence(row.confidence); } },
				{ key: "endpointType", label: "종류" },
				{ key: "httpMethod", label: "HTTP" },
				{ key: "path", label: "주소", mono: true },
				{ key: "handler", label: "처리하는 메소드", mono: true }
			], result.endpoints);
			section(el["kn-impact-result"], "화면 (REQUESTS: 화면이 그 주소를 요청 / RENDERS: 메소드가 이 화면을 연다 / CALLS: 화면의 코드가 직접 부른다)", [
				{ key: "depth", label: "거리", num: true },
				{ key: "confidence", label: "신뢰도", html: function (row) { return K.confidence(row.confidence); } },
				{ key: "path", label: "화면", mono: true },
				{ key: "via", label: "이어진 방식" }
			], result.screens);
			section(el["kn-impact-result"], "메소드 (거리 0 = 대상을 직접 건드린다)", [
				{ key: "depth", label: "거리", num: true },
				{ key: "confidence", label: "신뢰도", html: function (row) { return K.confidence(row.confidence); } },
				{ key: "layer", label: "계층" },
				{ key: "method", label: "메소드", mono: true },
				{ key: "path", label: "파일", mono: true },
				{ key: "lineStart", label: "줄", num: true }
			], result.methods);
			if (jump) {
				showResult();
			}
		}).catch(function (err) {
			K.message(el["kn-impact-message"], err.message, true);
			if (jump) {
				showResult();
			}
		});
	}

	/** 요약 카드의 첫 칸. 무엇을 "직접 건드리는" 메소드인지 대상에 맞춰 적는다. */
	var START_LABELS = { TABLE: "이 테이블을 쓰는 메소드", STATEMENT: "이 SQL을 쓰는 메소드", TYPE: "이 타입의 메소드", METHOD: "대상 메소드" };

	/**
	 * 결과 맨 위의 "대상" 머리글. 무엇을 고치면 생기는 영향인지 한눈에 보이게 한다.
	 *   [종류]  큰 이름
	 *           설명(전체 이름 · 파일:줄 · 계층 · 한글명 ...)
	 *           조건(리비전 · 깊이 · 인터페이스 경유 호출 포함 여부)
	 */
	function targetHead(result) {
		var t = result.target || {};
		var title = t.name;
		var notes = [];
		if (t.kind === "METHOD") {
			title = t.ownerFqn ? shortMethod(t.ownerFqn + "#" + t.signature) : t.name;
			if (t.title) {
				notes.push(t.title);
			}
			if (t.ownerFqn) {
				notes.push(t.ownerFqn);
			}
		} else if (t.kind === "TYPE") {
			title = t.simpleName || t.name;
			notes.push(t.name);
		} else if (t.kind === "STATEMENT") {
			title = t.statement || t.name;
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
			notes.push({ ALL: "읽기 + 쓰기", READ: "읽기만", WRITE: "쓰기만" }[t.access] || t.access);
			if (t.statements !== undefined) {
				notes.push("건드리는 SQL " + t.statements + "개");
			}
		}
		if (t.path) {
			notes.push(t.path + (t.lineStart ? ":" + t.lineStart : ""));
		}
		if (t.layer) {
			notes.push(t.layer);
		}
		var revision = el["kn-impact-revision"].options[el["kn-impact-revision"].selectedIndex];
		var conditions = [
			"리비전 " + (revision ? revision.text : result.revisionId),
			"거슬러 올라간 깊이 " + result.depth,
			result.includePossible ? "인터페이스 경유 호출 포함" : "인터페이스 경유 호출 제외"
		];
		return "<div class=\"kn-target-head\" tabindex=\"-1\">"
			+ "<span class=\"kn-target-kind\">" + K.escapeHtml(KIND_LABELS[t.kind] || t.kind) + "</span>"
			+ "<div class=\"kn-target-body\"><div class=\"kn-target-name\">" + K.escapeHtml(title) + " <span class=\"kn-target-suffix\">을(를) 고치면</span></div>"
			+ (notes.length ? "<div class=\"kn-target-note\">" + K.escapeHtml(notes.join(" · ")) + "</div>" : "")
			+ "<div class=\"kn-target-cond\">" + conditions.map(function (text) { return "<span>" + K.escapeHtml(text) + "</span>"; }).join("") + "</div>"
			+ "</div></div>";
	}

	/** 위쪽(호출 관계)에서 영향도를 실행했을 때 결과 머리글로 옮겨 가 잠깐 강조한다 */
	function showResult() {
		var head = el["kn-impact-head"].querySelector(".kn-target-head");
		var target = head || el["kn-impact-message"];
		target.scrollIntoView({ behavior: "smooth", block: "start" });
		if (head) {
			head.classList.add("kn-flash");
			setTimeout(function () {
				head.classList.remove("kn-flash");
			}, 1600);
		}
	}

	function section(container, title, columns, rows) {
		var wrap = document.createElement("div");
		wrap.innerHTML = "<div class=\"kn-section-title\">" + K.escapeHtml(title) + " · " + (rows || []).length + "건</div><div class=\"kn-scroll\"></div>";
		K.table(wrap.querySelector(".kn-scroll"), columns, rows);
		container.appendChild(wrap);
	}

	/* ---------------- 호출 관계 ---------------- */

	function findMethods() {
		el["kn-graph-title"].textContent = "";
		el["kn-graph-table"].innerHTML = "";
		Promise.resolve().then(function () {
			return K.call("GET", revisionPath() + "/methods", { name: el["kn-method-name"].value.trim(), owner: el["kn-method-owner"].value.trim(), size: 100 });
		}).then(function (result) {
			var methods = result.methods || [];
			K.message(el["kn-graph-message"], "메소드 " + result.total + "건" + (result.total > methods.length ? " (앞쪽 " + methods.length + "건만 보임)" : ""));
			K.table(el["kn-method-table"], [
				{ key: "ownerFqn", label: "타입", mono: true },
				{ key: "signature", label: "메소드", mono: true },
				{ key: "path", label: "파일", mono: true },
				{ key: "lineStart", label: "줄", num: true },
				{ key: "action", label: "", html: function () {
					return "<button type=\"button\" data-act=\"callers\">부르는 쪽</button> <button type=\"button\" data-act=\"callees\">불리는 쪽</button>"
						+ " <button type=\"button\" data-act=\"impact\">영향도</button>";
				} }
			], methods, "그 이름의 메소드가 없습니다.");
			eachRow(el["kn-method-table"], function (tr, index) {
				var method = methods[index];
				Array.prototype.forEach.call(tr.querySelectorAll("button"), function (button) {
					button.addEventListener("click", function () {
						var act = button.getAttribute("data-act");
						if (act === "impact") {
							runImpact("methodId", method.methodId, shortMethod(method.ownerFqn + "#" + method.signature), true);
						} else {
							loadGraph(method, act);
						}
					});
				});
			});
		}).catch(function (err) {
			K.message(el["kn-graph-message"], err.message, true);
		});
	}

	function findTables() {
		el["kn-graph-title"].textContent = "";
		el["kn-graph-table"].innerHTML = "";
		Promise.resolve().then(function () {
			return K.call("GET", revisionPath() + "/tables", { name: el["kn-table-name"].value.trim(), size: 100 });
		}).then(function (result) {
			// 응답에서 목록이 든 키를 찾는다(행의 배열인 첫 번째 값).
			var tables = [];
			Object.keys(result).forEach(function (key) {
				if (tables.length === 0 && Array.isArray(result[key])) {
					tables = result[key];
				}
			});
			K.message(el["kn-graph-message"], "테이블 " + (result.total !== undefined ? result.total : tables.length) + "건. 이름을 누르면 영향도를 분석합니다.");
			K.autoTable(el["kn-method-table"], tables);
			eachRow(el["kn-method-table"], function (tr, index) {
				var name = tables[index].table || tables[index].name || tables[index].tableName;
				var cell = tr.querySelector("td");
				cell.innerHTML = "<a href=\"javascript:void(0)\" class=\"workflow-history-jobid\">" + K.escapeHtml(cell.textContent) + "</a>";
				cell.querySelector("a").addEventListener("click", function () {
					runImpact("table", name, null, true);
				});
			});
		}).catch(function (err) {
			K.message(el["kn-graph-message"], err.message, true);
		});
	}

	/** direction: callers(부르는 쪽) / callees(불리는 쪽) */
	function loadGraph(method, direction) {
		var callers = direction === "callers";
		K.call("GET", revisionPath() + "/methods/" + method.methodId + "/" + direction, { depth: el["kn-graph-depth"].value }).then(function (result) {
			var relations = result[direction] || [];
			// 부르는 쪽은 같은 줄을 한 행으로 합친다(공통 스크립트를 불러오는 화면 한 줄이 주소 여러 개로 이어지는 경우).
			var rows = callers ? groupSameLine(relations) : relations;
			el["kn-graph-title"].textContent = method.ownerFqn + "#" + method.signature + (callers ? " 을(를) 부르는 쪽" : " 이(가) 부르는 쪽") + " · " + rows.length + "건"
				+ (rows.length !== relations.length ? " (관계 " + relations.length + ")" : "")
				+ (result.truncated ? " (일부만 보임)" : "");
			el["kn-graph-table"].innerHTML = "<div class=\"kn-scroll\"></div>";
			var columns = [
				{ key: "depth", label: "거리", num: true },
				{ key: "relationType", label: "관계", html: function (row) { return relationLabel(row.relationType); } },
				{ key: "confidence", label: "신뢰도", html: function (row) { return K.confidence(row.confidence); } }
			];
			if (callers) {
				// 부르는 쪽: 짧은 이름(+ 화면명)과, 그것이 어디에 적혀 있는지(근거) 두 칸
				columns.push({ key: "from", label: "부르는 쪽", html: function (row) { return callerCell(row); } });
				columns.push({ key: "path", label: "근거 (파일:줄)", html: function (row) { return evidenceCell(row); } });
			} else {
				columns.push({ key: "to", label: "불리는 쪽", mono: true });
				columns.push({ key: "path", label: "적힌 파일", mono: true });
				columns.push({ key: "lineStart", label: "줄", num: true });
			}
			K.table(el["kn-graph-table"].querySelector(".kn-scroll"), columns, rows
				, callers ? "부르는 쪽이 없습니다(진입점이거나, 분석이 잇지 못한 호출일 수 있습니다)." : "부르는 것이 없습니다.");
		}).catch(function (err) {
			K.message(el["kn-graph-message"], err.message, true);
		});
	}

	/** 관계 종류 → 화면 글자. 원래 이름은 마우스를 올리면 보인다. */
	var RELATION_LABELS = {
		CALLS: "호출",
		REQUESTS: "화면 요청",
		CALLS_POSSIBLE_IMPLEMENTATION: "구현체 호출(추정)",
		EXECUTES_SQL: "SQL 실행",
		READS_TABLE: "테이블 읽기",
		WRITES_TABLE: "테이블 쓰기",
		RENDERS: "화면 열기"
	};

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

	/** 경로의 마지막 이름: src/main/webapp/WEB-INF/sitemesh/layout.jsp → layout.jsp */
	function fileName(path) {
		path = path || "";
		return path.substring(path.lastIndexOf("/") + 1);
	}

	/**
	 * 부르는 쪽 칸.
	 *   메소드: CommonCodeController#getCommonCode(…)   (전체 이름은 마우스를 올리면)
	 *   화면:   layout.jsp · 화면명                     (전체 경로는 마우스를 올리면)
	 * 합친 행이면 아래에 "genexon.js를 불러와 주소 5개 요청"을 붙이고, 누르면 주소마다 어디로 이어졌는지 펼친다.
	 */
	function callerCell(row) {
		var screen = row.fromKind === "FILE";
		var full = row.from || row.fromId || "";
		var html = "<span class=\"kn-mono kn-caller-name\" title=\"" + K.escapeHtml(full) + "\">" + K.escapeHtml(screen ? fileName(full) : shortMethod(full)) + "</span>";
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
		var items = targets.map(function (target) {
			var method = shortMethod(target.to || target.toId || "");
			var text = requests && target.requestPath ? target.requestPath + " → " + method : method;
			var where = target.script ? fileName(target.script) + (target.scriptLine ? ":" + target.scriptLine : "") : "";
			return "<li><span class=\"kn-mono\">" + K.escapeHtml(text) + "</span>"
				+ (where ? " <span class=\"kn-caller-note\" title=\"" + K.escapeHtml(target.script) + "\">" + K.escapeHtml(where) + "</span>" : "") + "</li>";
		}).join("");
		return html + "<details class=\"kn-targets\"><summary>" + K.escapeHtml(summary) + "</summary><ul>" + items + "</ul></details>";
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

	function eachRow(container, callback) {
		Array.prototype.forEach.call(container.querySelectorAll("tbody tr[data-index]"), function (tr) {
			callback(tr, parseInt(tr.getAttribute("data-index"), 10));
		});
	}

	return { init: init };
})();
