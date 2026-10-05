/**
 * "영향도 · 호출 관계" 화면.
 */
var DstoneKnowledgeImpact = (function () {

	var K = DstoneKnowledge;
	var el = {};

	function init() {
		["kn-impact-project", "kn-impact-revision", "kn-impact-kind", "kn-impact-target", "kn-impact-access", "kn-impact-depth", "kn-impact-btn",
			"kn-impact-message", "kn-impact-cards", "kn-impact-result", "kn-method-name", "kn-method-owner", "kn-method-btn", "kn-table-name", "kn-table-btn",
			"kn-graph-depth", "kn-graph-message", "kn-method-table", "kn-graph-title", "kn-graph-table"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		el["kn-impact-project"].addEventListener("change", loadRevisions);
		el["kn-impact-btn"].addEventListener("click", function () {
			runImpact(el["kn-impact-kind"].value, el["kn-impact-target"].value.trim());
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

	function runImpact(kind, target) {
		var query = { access: el["kn-impact-access"].value, depth: el["kn-impact-depth"].value };
		query[kind] = target;
		el["kn-impact-kind"].value = kind;
		el["kn-impact-target"].value = target;
		el["kn-impact-cards"].innerHTML = "";
		el["kn-impact-result"].innerHTML = "";
		K.message(el["kn-impact-message"], "분석 중...");
		Promise.resolve().then(function () {
			return K.call("GET", revisionPath() + "/impact", query);
		}).then(function (result) {
			K.message(el["kn-impact-message"], "대상: " + result.target.kind + " " + result.target.name + (result.truncated ? " · 결과가 많아 일부만 보입니다" : ""));
			K.cards(el["kn-impact-cards"], [
				{ label: "직접 건드리는 메소드", value: result.summary.startMethods },
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
		}).catch(function (err) {
			K.message(el["kn-impact-message"], err.message, true);
		});
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
							runImpact("methodId", method.methodId);
							el["kn-impact-message"].scrollIntoView({ behavior: "smooth" });
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
					runImpact("table", name);
					el["kn-impact-message"].scrollIntoView({ behavior: "smooth" });
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
			var rows = result[direction] || [];
			el["kn-graph-title"].textContent = method.ownerFqn + "#" + method.signature + (callers ? " 을(를) 부르는 쪽" : " 이(가) 부르는 쪽") + " · " + rows.length + "건"
				+ (result.truncated ? " (일부만 보임)" : "");
			el["kn-graph-table"].innerHTML = "<div class=\"kn-scroll\"></div>";
			K.table(el["kn-graph-table"].querySelector(".kn-scroll"), [
				{ key: "depth", label: "거리", num: true },
				{ key: "relationType", label: "관계" },
				{ key: "confidence", label: "신뢰도", html: function (row) { return K.confidence(row.confidence); } },
				{ key: callers ? "from" : "to", label: callers ? "부르는 쪽" : "불리는 쪽", mono: true },
				{ key: "path", label: "적힌 파일", mono: true },
				{ key: "lineStart", label: "줄", num: true }
			], rows, callers ? "부르는 쪽이 없습니다(진입점이거나, 분석이 잇지 못한 호출일 수 있습니다)." : "부르는 것이 없습니다.");
		}).catch(function (err) {
			K.message(el["kn-graph-message"], err.message, true);
		});
	}

	function eachRow(container, callback) {
		Array.prototype.forEach.call(container.querySelectorAll("tbody tr[data-index]"), function (tr) {
			callback(tr, parseInt(tr.getAttribute("data-index"), 10));
		});
	}

	return { init: init };
})();
