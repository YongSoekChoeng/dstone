/**
 * "노드 맵" 화면. 호출 구조를 그림으로 봅니다.
 *
 *   1. 전체 맵     클래스 / 화면 / SQL 매퍼 / 테이블이 노드입니다. 많으면 패키지(폴더) 묶음으로 접어서 보여 줍니다.
 *   2. 강조        노드를 누르면 거기에 이어진 노드와 선만 남기고 나머지는 흐리게 합니다.
 *   3. 관계 트리   "따로 보기"를 누르면 그 노드에 이어진 길을 메소드 단위로 팝업에 그립니다.
 *   4. 상세        누른 노드의 설명을 오른쪽에 보여 줍니다.
 *
 * 그림은 Cytoscape.js(knowledge/assets/lib/cytoscape.min.js)로 그립니다.
 */
var DstoneKnowledgeGraph = (function () {

	var K = DstoneKnowledge;
	var el = {};

	/** 계층. 이 순서대로 왼쪽에서 오른쪽으로 놓습니다(호출이 흐르는 방향) */
	var TIERS = [
		{ key: "VIEW", label: "화면", color: "#0284c7", fill: "#e0f2fe" },
		{ key: "CONTROLLER", label: "컨트롤러", color: "#2563eb", fill: "#dbeafe" },
		{ key: "SERVICE", label: "서비스", color: "#7c3aed", fill: "#ede9fe" },
		{ key: "REPOSITORY", label: "DAO", color: "#d97706", fill: "#fef3c7" },
		{ key: "MAPPER", label: "SQL 매퍼", color: "#db2777", fill: "#fce7f3" },
		{ key: "TABLE", label: "테이블", color: "#059669", fill: "#d1fae5" },
		{ key: "ETC", label: "그 밖", color: "#4b5563", fill: "#f3f4f6" },
		{ key: "MODEL", label: "모델(VO)", color: "#9ca3af", fill: "#fafafa" }
	];
	var TIER_BY_KEY = {};
	TIERS.forEach(function (tier, index) {
		tier.index = index;
		TIER_BY_KEY[tier.key] = tier;
	});

	/** 선의 종류. 서버가 주는 관계 이름을 그림에서 쓰는 몇 가지로 모읍니다 */
	var EDGE_CATS = {
		CALL: { label: "호출", color: "#9ca3af" },
		SQL: { label: "SQL 실행", color: "#db2777" },
		READ: { label: "테이블 읽기", color: "#059669" },
		WRITE: { label: "테이블 쓰기", color: "#dc2626" },
		RENDER: { label: "화면 열기", color: "#0284c7" },
		REQUEST: { label: "화면의 요청", color: "#0ea5e9" },
		STRUCT: { label: "상속 · 구현 · 주입", color: "#7c3aed" }
	};
	var CAT_OF_RELATION = {
		CALLS: "CALL", CALLS_POSSIBLE_IMPLEMENTATION: "CALL", EXECUTES_SQL: "SQL", READS_TABLE: "READ", WRITES_TABLE: "WRITE",
		RENDERS: "RENDER", REQUESTS: "REQUEST", EXTENDS: "STRUCT", IMPLEMENTS: "STRUCT", INJECTS: "STRUCT", INCLUDES: "STRUCT"
	};

	var KIND_LABEL = { TYPE: "클래스", METHOD: "메소드", FIELD: "필드", JSP: "화면", MAPPER: "SQL 매퍼", SQL: "SQL", TABLE: "테이블", GROUP: "묶음" };
	var CONF_RANK = { HIGH: 3, MEDIUM: 2, LOW: 1 };

	/** 노드가 이보다 많으면 처음에 묶음으로 접어서 보여 줍니다 */
	var AUTO_COLLAPSE_OVER = 250;

	/** 자유 배치를 할 수 있는 최대 노드 수 */
	var FREE_LAYOUT_MAX = 400;

	/** 처음 열 때 이보다 작게는 줄이지 않는다. 맵 전체를 칸에 넣으면 글자가 4~5px로 작아져 읽을 수 없었다 */
	var MIN_START_ZOOM = 0.8;

	var DEFAULT_RELATIONS = "CALLS,CALLS_POSSIBLE_IMPLEMENTATION,EXECUTES_SQL,READS_TABLE,WRITES_TABLE,RENDERS,REQUESTS";

	/** 전체 맵의 상태 */
	var map = { revisionId: null, graph: null, expanded: {}, view: null, cy: null, selectedId: null, hits: [], hitIndex: 0, loadSeq: 0 };

	/** 관계 트리 팝업의 상태. hidden 은 끈 계층(팝업을 다시 열어도 유지한다) */
	var tree = { cy: null, startId: null, nodes: {}, result: null, hidden: {} };

	/** 관계 트리에서 끄고 켤 수 있는 계층. 모델(VO)은 "모델(VO) 호출도"로 서버에서 가려 받는다 */
	var TREE_TIERS = ["VIEW", "CONTROLLER", "SERVICE", "REPOSITORY", "MAPPER", "TABLE", "ETC"];

	function init() {
		["kn-graph-project", "kn-graph-revision", "kn-graph-load", "kn-graph-unit", "kn-graph-layout", "kn-graph-range", "kn-graph-low", "kn-graph-struct",
			"kn-graph-tiers", "kn-graph-edges", "kn-graph-find", "kn-graph-find-btn", "kn-graph-fit", "kn-graph-message", "kn-graph-canvas", "kn-graph-detail",
			"kn-tree-modal", "kn-tree-title", "kn-tree-up", "kn-tree-down", "kn-tree-model", "kn-tree-reload", "kn-tree-full", "kn-tree-close", "kn-tree-message", "kn-tree-tiers",
			"kn-tree-canvas", "kn-tree-detail", "kn-graph-workspace", "kn-graph-full", "kn-graph-cond-toggle",
			"kn-code-modal", "kn-code-title", "kn-code-where", "kn-code-text", "kn-code-raw", "kn-code-copy", "kn-code-close"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});

		renderLegend();
		el["kn-graph-project"].addEventListener("change", function () {
			loadRevisions().then(loadGraph);
		});
		el["kn-graph-revision"].addEventListener("change", loadGraph);
		el["kn-graph-load"].addEventListener("click", loadGraph);
		el["kn-graph-struct"].addEventListener("change", loadGraph);
		["kn-graph-unit", "kn-graph-layout", "kn-graph-low"].forEach(function (id) {
			el[id].addEventListener("change", drawMap);
		});
		el["kn-graph-range"].addEventListener("change", function () {
			if (map.selectedId) {
				selectNode(map.selectedId, true);
			}
		});
		el["kn-graph-fit"].addEventListener("click", function () {
			if (map.cy) {
				map.cy.fit(undefined, 30);
			}
		});
		el["kn-graph-full"].addEventListener("click", function () {
			setFullscreen(!isFullscreen());
		});
		el["kn-graph-cond-toggle"].addEventListener("click", function () {
			setConditionsHidden(!el["kn-graph-workspace"].classList.contains("kn-cond-hidden"));
		});
		el["kn-graph-find-btn"].addEventListener("click", find);
		el["kn-graph-find"].addEventListener("keydown", function (event) {
			if (event.key === "Enter") {
				find();
			}
		});

		el["kn-tree-close"].addEventListener("click", closeTree);
		el["kn-tree-full"].addEventListener("click", function () {
			setTreeFullscreen(!isTreeFullscreen());
		});
		el["kn-tree-reload"].addEventListener("click", function () {
			loadTree(tree.startId);
		});
		el["kn-code-close"].addEventListener("click", closeCode);
		el["kn-code-copy"].addEventListener("click", copyCode);
		el["kn-code-raw"].addEventListener("click", toggleRawCode);
		el["kn-code-modal"].addEventListener("click", function (event) {
			if (event.target === el["kn-code-modal"]) {
				closeCode();
			}
		});
		el["kn-tree-modal"].addEventListener("click", function (event) {
			// 팝업 바깥(어두운 바탕)을 누르면 닫는다.
			if (event.target === el["kn-tree-modal"]) {
				closeTree();
			}
		});
		document.addEventListener("keydown", function (event) {
			if (event.key !== "Escape") {
				return;
			}
			// 팝업이 열려 있으면 맨 위 팝업만 닫는다. 전체 화면은 그다음 Esc 에서 끝낸다.
			if (el["kn-code-modal"].style.display !== "none") {
				closeCode();
				return;
			}
			if (el["kn-tree-modal"].style.display !== "none") {
				// 트리 팝업이 전체 화면이면 먼저 원래 크기로 돌리고, 한 번 더 누르면 닫는다.
				if (isTreeFullscreen()) {
					setTreeFullscreen(false);
				} else {
					closeTree();
				}
				return;
			}
			var tag = (event.target && event.target.tagName) || "";
			if (isFullscreen() && tag !== "INPUT" && tag !== "TEXTAREA" && tag !== "SELECT") {
				setFullscreen(false);
			}
		});

		K.loadProjects(el["kn-graph-project"]).then(loadRevisions).then(loadGraph).catch(function (err) {
			K.message(el["kn-graph-message"], err.message, true);
		});
	}

	/* ====================================================================
	 * 전체 화면: 노드 맵 영역(#kn-graph-workspace)이 브라우저 화면 전체를 덮게 합니다.
	 * 다른 창을 만들지 않고 같은 영역을 키우므로, 그리던 그림과 고른 노드, 펼친 묶음이 그대로 이어집니다.
	 * ==================================================================== */

	function isFullscreen() {
		return el["kn-graph-workspace"].classList.contains("kn-full");
	}

	function setFullscreen(on) {
		if (on === isFullscreen()) {
			return;
		}
		el["kn-graph-workspace"].classList.toggle("kn-full", on);
		document.body.classList.toggle("kn-full-on", on);
		el["kn-graph-full"].textContent = on ? "전체 화면 끝내기" : "전체 화면";
		if (!on) {
			// 접어 둔 조건표는 원래 화면으로 돌아갈 때 다시 편다.
			setConditionsHidden(false);
			return;
		}
		refitMap();
	}

	/** 전체 화면에서 위쪽 조건표를 접거나 폅니다. 그림 칸이 그만큼 넓어집니다 */
	function setConditionsHidden(hidden) {
		el["kn-graph-workspace"].classList.toggle("kn-cond-hidden", hidden);
		el["kn-graph-cond-toggle"].textContent = hidden ? "조건 보이기" : "조건 숨기기";
		refitMap();
	}

	/** 그림 칸의 크기가 바뀌었다. Cytoscape 는 창 크기만 지켜보므로 칸이 바뀐 것을 직접 알려 주고 처음 보여 줄 위치를 다시 잡는다 */
	function refitMap() {
		window.requestAnimationFrame(function () {
			if (map.cy) {
				map.cy.resize();
				startView(map.cy);
			}
		});
	}

	function loadRevisions() {
		return K.loadRevisions(el["kn-graph-project"].value, el["kn-graph-revision"]);
	}

	/** 계층 고르기(색깔 딱지)와 선의 범례를 그립니다 */
	function renderLegend() {
		el["kn-graph-tiers"].innerHTML = TIERS.map(function (tier) {
			// 모델(VO)은 getter / setter 호출이 그림을 덮어서 처음에는 끈다.
			return "<label class=\"kn-graph-chip\" style=\"border-color:" + tier.color + ";background:" + tier.fill + ";\">"
				+ "<input type=\"checkbox\" data-tier=\"" + tier.key + "\"" + (tier.key === "MODEL" ? "" : " checked") + " /> " + K.escapeHtml(tier.label)
				+ " <span data-count=\"" + tier.key + "\"></span></label>";
		}).join("");
		Array.prototype.forEach.call(el["kn-graph-tiers"].querySelectorAll("input"), function (input) {
			input.addEventListener("change", drawMap);
		});
		el["kn-graph-edges"].innerHTML = Object.keys(EDGE_CATS).map(function (key) {
			return "<span class=\"kn-graph-line\"><i style=\"border-top-color:" + EDGE_CATS[key].color + ";\"></i>" + K.escapeHtml(EDGE_CATS[key].label) + "</span>";
		}).join("") + "<span class=\"kn-graph-line\"><i style=\"border-top-style:dashed;border-top-color:#6b7280;\"></i>짐작한 관계(LOW)</span>";
	}

	/* ======================= 전체 맵: 불러오기 ======================= */

	function loadGraph() {
		var revisionId = el["kn-graph-revision"].value;
		if (!revisionId) {
			K.message(el["kn-graph-message"], "리비전을 고르세요. 분석이 끝난 리비전이 있어야 합니다.", true);
			return Promise.resolve();
		}
		K.message(el["kn-graph-message"], "불러오는 중...");
		var relations = DEFAULT_RELATIONS + (el["kn-graph-struct"].checked ? ",EXTENDS,IMPLEMENTS,INJECTS" : "");
		// 프로젝트를 빨리 바꾸면 앞의 응답이 뒤늦게 올 수 있다. 마지막에 부른 것의 응답만 그린다.
		var seq = ++map.loadSeq;
		return K.call("GET", "/api/revisions/" + revisionId + "/graph", { relations: relations }).then(function (graph) {
			if (seq !== map.loadSeq) {
				return;
			}
			// 같은 리비전을 다시 불러온 것이면(관계 종류만 바꾼 경우) 펼쳐 둔 묶음과 고른 노드를 그대로 둔다.
			var sameRevision = map.revisionId === revisionId;
			map.revisionId = revisionId;
			map.graph = graph;
			map.hits = [];
			map.hitWord = null;
			(graph.nodes || []).forEach(function (node) {
				node.tier = tierOf(node.layer);
				node.groupKey = node.kind + "|" + node.group;
			});
			if (!sameRevision) {
				map.expanded = {};
				map.selectedId = null;
				// 노드가 많으면 묶음으로 접어서 시작한다. 한 화면에 수천 개를 그리면 아무것도 읽을 수 없다.
				var big = (graph.nodes || []).length > AUTO_COLLAPSE_OVER;
				el["kn-graph-unit"].value = big ? "GROUP" : "TYPE";
				// 큰 프로젝트는 어디서나 쓰는 공통 클래스를 거쳐 거의 전부가 이어진다. 끝까지 강조하면 다 켜지므로 바로 옆만 강조한다.
				el["kn-graph-range"].value = big ? "1" : "ALL";
				clearDetail(el["kn-graph-detail"], "노드를 누르면 여기에 설명이 나옵니다.");
			}
			drawMap();
		}).catch(function (err) {
			if (seq === map.loadSeq) {
				K.message(el["kn-graph-message"], err.message, true);
			}
		});
	}

	function tierOf(layer) {
		if (layer === "WEB_FILTER") {
			return "CONTROLLER";
		}
		return TIER_BY_KEY[layer] ? layer : "ETC";
	}

	/* ======================= 전체 맵: 그릴 것 고르기 ======================= */

	/**
	 * 서버가 준 그래프에서 지금 화면에 그릴 노드와 선을 만듭니다.
	 *   - 끈 계층의 노드는 뺍니다.
	 *   - "묶음" 단위면 펼치지 않은 패키지(폴더)의 노드를 묶음 노드 하나로 합치고, 선도 거기에 맞춰 합칩니다.
	 */
	function buildView() {
		var hiddenTiers = {};
		Array.prototype.forEach.call(el["kn-graph-tiers"].querySelectorAll("input"), function (input) {
			if (!input.checked) {
				hiddenTiers[input.getAttribute("data-tier")] = true;
			}
		});
		var collapse = el["kn-graph-unit"].value === "GROUP";
		var hideLow = !el["kn-graph-low"].checked;

		var view = { nodes: {}, edges: {}, shownOf: {}, groups: {}, tierCounts: {} };
		map.graph.nodes.forEach(function (node) {
			view.tierCounts[node.tier] = (view.tierCounts[node.tier] || 0) + 1;
			if (hiddenTiers[node.tier]) {
				return;
			}
			(view.groups[node.groupKey] = view.groups[node.groupKey] || []).push(node);
		});
		Object.keys(view.groups).forEach(function (groupKey) {
			var members = view.groups[groupKey];
			// 하나뿐인 묶음은 접어도 얻는 것이 없으므로 그대로 그린다.
			if (collapse && !map.expanded[groupKey] && members.length > 1) {
				var id = "G|" + groupKey;
				view.nodes[id] = { id: id, kind: "GROUP", name: members[0].group, label: shortGroup(members[0].group) + " (" + members.length + ")",
					tier: majorTier(members), groupKey: groupKey, members: members };
				members.forEach(function (member) {
					view.shownOf[member.id] = id;
				});
				return;
			}
			members.forEach(function (member) {
				view.nodes[member.id] = { id: member.id, kind: member.kind, name: member.name, label: member.name, tier: member.tier,
					groupKey: groupKey, raw: member };
				view.shownOf[member.id] = member.id;
			});
		});

		map.graph.edges.forEach(function (edge) {
			var from = view.shownOf[edge.from];
			var to = view.shownOf[edge.to];
			if (!from || !to || from === to || (hideLow && edge.confidence === "LOW")) {
				return;
			}
			var cat = CAT_OF_RELATION[edge.type] || "CALL";
			var key = from + ">" + to + ">" + cat;
			var shown = view.edges[key];
			if (!shown) {
				shown = view.edges[key] = { id: key, from: from, to: to, cat: cat, count: 0, confidence: "LOW", types: {} };
			}
			shown.count += edge.count;
			shown.types[edge.type] = (shown.types[edge.type] || 0) + edge.count;
			if (CONF_RANK[edge.confidence] > CONF_RANK[shown.confidence]) {
				shown.confidence = edge.confidence;
			}
		});
		return view;
	}

	/** 묶음 이름이 길면 뒤의 두 마디만: com.shop.order.service → order.service */
	function shortGroup(name) {
		var parts = String(name).split(/[.\/]/);
		return parts.length <= 2 ? String(name) : parts.slice(parts.length - 2).join(name.indexOf("/") >= 0 ? "/" : ".");
	}

	/** 묶음에 가장 많이 든 계층. 묶음 노드를 그 계층의 자리에 놓습니다 */
	function majorTier(members) {
		var counts = {};
		var best = members[0].tier;
		members.forEach(function (member) {
			counts[member.tier] = (counts[member.tier] || 0) + 1;
			if (counts[member.tier] > counts[best]) {
				best = member.tier;
			}
		});
		return best;
	}

	/* ======================= 전체 맵: 그리기 ======================= */

	/**
	 * @param focusGroupKey 다 그린 뒤 이 묶음(펼친 것이면 든 노드들, 접은 것이면 묶음 노드)을 화면 가운데에 둔다.
	 *        없으면 처음 보여 주는 위치(startView). 이벤트에 바로 걸려서 이벤트 객체가 올 수도 있으므로 글자일 때만 쓴다.
	 */
	function drawMap(focusGroupKey) {
		if (!map.graph) {
			return;
		}
		var view = map.view = buildView();
		TIERS.forEach(function (tier) {
			el["kn-graph-tiers"].querySelector("[data-count=\"" + tier.key + "\"]").textContent = view.tierCounts[tier.key] || 0;
		});

		var ids = Object.keys(view.nodes);
		var byTier = byTierLayout(ids.map(function (id) {
			return view.nodes[id];
		}));
		// 자유 배치는 노드 수의 제곱만큼 계산이 늘어서, 많으면 브라우저가 한참 멈춘다. 그때는 계층별로 그린다.
		var tooManyForFree = el["kn-graph-layout"].value === "FREE" && ids.length > FREE_LAYOUT_MAX;
		var free = el["kn-graph-layout"].value === "FREE" && !tooManyForFree;
		var elements = [];
		ids.forEach(function (id) {
			elements.push(nodeElement(view.nodes[id], byTier.positions[id]));
		});
		if (!free) {
			// 계층 이름을 그 계층의 맨 위에 적어 둔다. 누를 수 없는 글자 노드다.
			byTier.labels.forEach(function (label) {
				elements.push({ group: "nodes", data: { id: "tier-" + label.key, label: label.text, color: "#6b7280", fill: "#ffffff", w: 10, tw: "400px" }, position: { x: label.x, y: label.y }, classes: "tier",
					selectable: false, grabbable: false });
			});
		}
		Object.keys(view.edges).forEach(function (key) {
			elements.push(edgeElement(view.edges[key]));
		});

		if (map.cy) {
			map.cy.destroy();
		}
		map.cy = cytoscape({
			container: el["kn-graph-canvas"],
			elements: elements,
			style: styles(),
			layout: free ? { name: "cose", animate: false, nodeDimensionsIncludeLabels: true, idealEdgeLength: 90, nodeRepulsion: 9000, numIter: 300 } : { name: "preset" },
			minZoom: 0.02,
			maxZoom: 3,
			// 노드가 많을 때 끌거나 확대하는 동안은 선을 감춰서 버벅이지 않게 한다.
			hideEdgesOnViewport: elements.length > 1500,
			textureOnViewport: elements.length > 1500
		});
		var clipped = startView(map.cy);
		if (typeof focusGroupKey === "string" && focusOn(map.cy, focusGroupKey)) {
			clipped = false;
		}
		map.cy.on("tap", "node", function (event) {
			if (!event.target.hasClass("tier")) {
				selectNode(event.target.id());
			}
		});
		map.cy.on("tap", function (event) {
			if (event.target === map.cy) {
				clearSelection();
			}
		});
		map.cy.on("dbltap", "node.group", function (event) {
			toggleGroup(event.target.data("groupKey"), true);
		});

		var total = map.graph.nodes.length;
		var collapsed = ids.filter(function (id) {
			return view.nodes[id].kind === "GROUP";
		}).length;
		K.message(el["kn-graph-message"], "노드 " + ids.length + "개 · 선 " + Object.keys(view.edges).length + "개 (분석된 것: 클래스 · 화면 · 매퍼 · 테이블 " + total + "개)"
			+ (collapsed > 0 ? " · 묶음 " + collapsed + "개가 접혀 있습니다. 묶음을 두 번 누르면 펼쳐집니다." : "")
			+ (map.graph.truncated ? " · 너무 많아서 일부만 가져왔습니다." : "")
			+ (clipped ? " · 글자가 보이게 왼쪽 위부터 보여 줍니다. 전체는 [화면에 맞추기]로 보세요." : "")
			+ (tooManyForFree ? " · 자유 배치는 노드 " + FREE_LAYOUT_MAX + "개까지만 됩니다. 묶음으로 접거나 계층을 꺼서 줄이세요(지금은 계층별로 그렸습니다)." : ""));

		if (map.selectedId && view.nodes[map.selectedId]) {
			selectNode(map.selectedId, true);
		} else {
			map.selectedId = null;
		}
	}

	function nodeElement(node, position) {
		var tier = TIER_BY_KEY[node.tier];
		var width = labelWidth(node.label);
		return {
			group: "nodes",
			data: { id: node.id, label: node.label, kind: node.kind, tier: node.tier, groupKey: node.groupKey, color: tier.color, fill: tier.fill, w: width, tw: (width - 10) + "px" },
			position: position,
			classes: (node.kind === "GROUP" ? "group " : "") + "kind-" + node.kind
		};
	}

	function edgeElement(edge) {
		return {
			group: "edges",
			data: { id: edge.id, source: edge.from, target: edge.to, color: EDGE_CATS[edge.cat].color, w: Math.min(1 + Math.log(edge.count) / Math.LN2 * 0.6, 6) },
			classes: (edge.confidence === "LOW" ? "low " : "") + (edge.cat === "STRUCT" ? "struct" : "")
		};
	}

	/** 글자 수로 노드의 폭을 어림합니다. 한글은 영문보다 넓습니다 */
	function labelWidth(label) {
		var width = 18;
		for (var i = 0; i < label.length; i++) {
			width += label.charCodeAt(i) > 255 ? 12 : 6.6;
		}
		return Math.max(46, Math.min(Math.round(width), 190));
	}

	/**
	 * 계층별 배치: 계층마다 세로 줄(여러 열)을 하나씩 주고 왼쪽에서 오른쪽으로 놓습니다.
	 * 화면 → 컨트롤러 → 서비스 → DAO → SQL 매퍼 → 테이블 순서라 선이 대체로 왼쪽에서 오른쪽으로 흐릅니다.
	 * 같은 묶음(패키지)의 노드는 붙여 놓습니다.
	 */
	function byTierLayout(nodes) {
		var colWidth = 200;
		var rowHeight = 30;
		var tierGap = 110;
		var tierLists = [];
		TIERS.forEach(function (tier) {
			var list = nodes.filter(function (node) {
				return node.tier === tier.key;
			});
			if (list.length > 0) {
				tierLists.push({ tier: tier, list: list });
			}
		});
		var rows = bestRows(tierLists, colWidth, rowHeight, tierGap);
		var positions = {};
		var labels = [];
		var x = 0;
		tierLists.forEach(function (item) {
			var tier = item.tier;
			var list = item.list;
			list.sort(function (a, b) {
				return a.groupKey === b.groupKey ? a.label.localeCompare(b.label) : a.groupKey.localeCompare(b.groupKey);
			});
			var cols = Math.ceil(list.length / rows);
			list.forEach(function (node, index) {
				positions[node.id] = { x: x + Math.floor(index / rows) * colWidth, y: (index % rows) * rowHeight };
			});
			labels.push({ key: tier.key, text: tier.label + " " + list.length, x: x + (cols - 1) * colWidth / 2, y: -46 });
			x += cols * colWidth + tierGap;
		});
		return { positions: positions, labels: labels };
	}

	/**
	 * 처음 보여 줄 위치를 정합니다. 칸에 다 들어가면 그대로 맞추고,
	 * 맞추면 글자가 너무 작아질 때는 MIN_START_ZOOM 배율로 맵의 왼쪽 위(화면 계층 쪽)부터 보여 줍니다.
	 * @return 맵 일부만 보여 주면 true
	 */
	function startView(cy) {
		cy.fit(undefined, 30);
		if (cy.zoom() >= MIN_START_ZOOM) {
			return false;
		}
		var box = cy.elements().boundingBox();
		cy.zoom(MIN_START_ZOOM);
		cy.pan({ x: 30 - box.x1 * MIN_START_ZOOM, y: 30 - box.y1 * MIN_START_ZOOM });
		return true;
	}

	/**
	 * 묶음 하나를 화면 가운데에 둡니다. 펼치거나 접은 뒤에 그 자리를 보여 주려고 씁니다.
	 * (startView 는 맵의 왼쪽 위부터 보여 주므로, 오른쪽에 있는 묶음을 펼치면 펼친 노드가 화면 밖에 있었다)
	 * @return 그 묶음의 노드가 맵에 있어서 옮겼으면 true
	 */
	function focusOn(cy, groupKey) {
		var nodes = cy.nodes().filter(function (node) {
			return node.data("groupKey") === groupKey;
		});
		if (nodes.empty()) {
			return false;
		}
		cy.zoom(Math.max(cy.zoom(), MIN_START_ZOOM));
		cy.center(nodes);
		return true;
	}

	/**
	 * 한 열에 놓을 줄 수를 고릅니다. 다 그린 맵이 그림 칸 모양(가로 · 세로 비율)과 비슷해야
	 * "화면에 맞추기"를 했을 때 덜 줄어들고 글자가 크게 보입니다.
	 * 줄 수를 하나씩 바꿔 보며 맞췄을 때 배율이 가장 큰 값을 씁니다.
	 * (예전에는 노드 수만 보고 정해서, 계층 사이 간격 때문에 맵이 옆으로 길게 늘어나 글자가 4px쯤으로 작아졌다)
	 */
	function bestRows(tierLists, colWidth, rowHeight, tierGap) {
		var canvas = el["kn-graph-canvas"];
		var boxWidth = Math.max(canvas.clientWidth, 400) - 60;
		var boxHeight = Math.max(canvas.clientHeight, 300) - 60;
		var longest = 1;
		tierLists.forEach(function (item) {
			longest = Math.max(longest, item.list.length);
		});
		var best = 12;
		var bestZoom = -1;
		for (var rows = Math.min(12, longest); rows <= longest; rows++) {
			var width = -tierGap;
			tierLists.forEach(function (item) {
				width += Math.ceil(item.list.length / rows) * colWidth + tierGap;
			});
			// 계층 이름 줄(46)과 노드 높이를 더한다
			var height = (rows - 1) * rowHeight + 46 + 26;
			var zoom = Math.min(boxWidth / width, boxHeight / height);
			if (zoom > bestZoom) {
				bestZoom = zoom;
				best = rows;
			}
		}
		return best;
	}

	function styles() {
		return [
			{ selector: "node", style: {
				"label": "data(label)", "font-size": 11, "color": "#111827", "text-valign": "center", "text-halign": "center",
				"text-wrap": "ellipsis", "text-max-width": "data(tw)", "min-zoomed-font-size": 5,
				"shape": "round-rectangle", "width": "data(w)", "height": 22,
				"background-color": "data(fill)", "border-color": "data(color)", "border-width": 1.5
			} },
			{ selector: "node.kind-JSP", style: { "shape": "round-tag" } },
			{ selector: "node.kind-MAPPER, node.kind-SQL", style: { "shape": "barrel" } },
			{ selector: "node.kind-TABLE", style: { "shape": "cut-rectangle" } },
			{ selector: "node.group", style: { "border-width": 4, "border-style": "double", "font-weight": "bold", "height": 26 } },
			{ selector: "node.tier", style: {
				"background-opacity": 0, "border-width": 0, "font-size": 20, "font-weight": "bold", "color": "#6b7280", "width": 10, "height": 10,
				"text-wrap": "none", "min-zoomed-font-size": 0, "events": "no"
			} },
			{ selector: "edge", style: {
				"width": "data(w)", "line-color": "data(color)", "target-arrow-color": "data(color)", "target-arrow-shape": "triangle", "arrow-scale": 0.8,
				"curve-style": "bezier", "opacity": 0.55
			} },
			{ selector: "edge.low", style: { "line-style": "dashed" } },
			{ selector: "edge.struct", style: { "line-style": "dotted" } },
			// 노드를 골랐을 때: 이어진 것만 또렷하게, 나머지는 흐리게
			{ selector: ".faded", style: { "opacity": 0.08, "text-opacity": 0.3 } },
			{ selector: "edge.hl", style: { "opacity": 1 } },
			{ selector: "node.up", style: { "underlay-color": "#60a5fa", "underlay-opacity": 0.45, "underlay-padding": 5 } },
			{ selector: "node.down", style: { "underlay-color": "#fb923c", "underlay-opacity": 0.45, "underlay-padding": 5 } },
			{ selector: "node.sel", style: { "underlay-color": "#facc15", "underlay-opacity": 0.9, "underlay-padding": 7, "border-width": 3, "border-color": "#111827" } }
		];
	}

	/* ======================= 전체 맵: 고르기 / 강조 ======================= */

	/**
	 * 노드를 고릅니다. 이어진 노드와 선만 남기고 나머지는 흐리게 한 뒤, 오른쪽에 설명을 보여 줍니다.
	 * 파란 테 = 이 노드로 들어오는 쪽(부르는 쪽), 주황 테 = 이 노드에서 나가는 쪽(불리는 쪽).
	 */
	function selectNode(id, keepDetail) {
		var node = map.cy.getElementById(id);
		if (node.empty()) {
			return;
		}
		map.selectedId = id;
		var linked = highlight(map.cy, node, el["kn-graph-range"].value);
		if (!keepDetail || !el["kn-graph-detail"].getAttribute("data-node")) {
			showMapDetail(map.view.nodes[id], linked);
		}
	}

	/**
	 * @param range ALL(끝까지) 또는 따라갈 걸음 수("1", "2")
	 * @return {up, down} 강조한 노드들
	 */
	function highlight(cy, node, range) {
		var up;
		var down;
		if (range === "ALL") {
			up = node.predecessors();
			down = node.successors();
		} else {
			up = cy.collection();
			down = cy.collection();
			var upFront = node;
			var downFront = node;
			for (var i = 0; i < parseInt(range, 10); i++) {
				upFront = upFront.incomers();
				downFront = downFront.outgoers();
				up = up.union(upFront);
				down = down.union(downFront);
				upFront = upFront.nodes();
				downFront = downFront.nodes();
			}
		}
		cy.batch(function () {
			cy.elements().removeClass("faded up down sel hl");
			cy.elements().not(up).not(down).not(node).not(".tier").addClass("faded");
			up.nodes().not(node).addClass("up");
			down.nodes().not(node).addClass("down");
			up.edges().addClass("hl");
			down.edges().addClass("hl");
			node.addClass("sel");
		});
		return { up: up.nodes().not(node), down: down.nodes().not(node) };
	}

	function clearSelection() {
		map.selectedId = null;
		map.cy.elements().removeClass("faded up down sel hl");
		clearDetail(el["kn-graph-detail"], "노드를 누르면 여기에 설명이 나옵니다.");
	}

	/** 묶음을 펼치거나 접습니다 */
	function toggleGroup(groupKey, expand) {
		map.expanded[groupKey] = expand;
		// 접혀 있던 묶음을 고른 채로 펼치면 그 노드가 없어진다. 고른 것을 푼다.
		map.selectedId = null;
		clearDetail(el["kn-graph-detail"], "노드를 누르면 여기에 설명이 나옵니다.");
		drawMap(groupKey);
	}

	/** "클래스.메소드"로 찾을 때 메소드 목록을 가져와 볼 클래스 수의 한도 */
	var METHOD_FIND_TYPE_MAX = 20;

	/**
	 * 이름으로 노드를 찾아 그리로 갑니다. 접힌 묶음 안에 있으면 그 묶음을 펼칩니다. 다시 누르면 다음 것으로 갑니다.
	 * 맞는 노드가 없고 "클래스.메소드"(또는 "클래스#메소드") 모양이면, 그 클래스로 간 뒤 메소드의 관계 트리를 엽니다.
	 */
	function find() {
		var word = el["kn-graph-find"].value.trim().toLowerCase();
		if (!map.graph || !word) {
			return;
		}
		if (map.hitWord === word) {
			if (map.hits.length > 0) {
				map.hitIndex = (map.hitIndex + 1) % map.hits.length;
				showHit(word);
			}
			return;
		}
		map.hitWord = word;
		map.hitIndex = 0;
		map.hits = nodesNamed(word, null);
		if (map.hits.length > 0) {
			showHit(word);
			return;
		}
		var cut = Math.max(word.lastIndexOf("."), word.lastIndexOf("#"));
		if (cut <= 0 || cut === word.length - 1) {
			K.message(el["kn-graph-message"], "\"" + word + "\" 이(가) 이름에 들어간 노드가 없습니다.", true);
			return;
		}
		findMethods(word, word.substring(0, cut), word.substring(cut + 1));
	}

	/** 이름이나 전체 이름에 word 가 들어간 노드. 이름이 정확히 같은 것을 앞에 둡니다. kinds 를 주면 그 종류만 봅니다 */
	function nodesNamed(word, kinds) {
		var hits = map.graph.nodes.filter(function (node) {
			if (kinds && kinds.indexOf(node.kind) < 0) {
				return false;
			}
			return String(node.name).toLowerCase().indexOf(word) >= 0 || String(node.fullName).toLowerCase().indexOf(word) >= 0;
		});
		hits.sort(function (a, b) {
			return (String(b.name).toLowerCase() === word ? 1 : 0) - (String(a.name).toLowerCase() === word ? 1 : 0);
		});
		return hits;
	}

	/** "클래스.메소드" 찾기. 클래스(화면 안의 Java 코드 포함)마다 메소드 목록을 가져와, 이름에 메소드 부분이 들어간 것을 모읍니다 */
	function findMethods(word, typeWord, methodWord) {
		var types = nodesNamed(typeWord, ["TYPE", "JSP"]);
		if (types.length === 0) {
			K.message(el["kn-graph-message"], "\"" + typeWord + "\" 이(가) 이름에 들어간 클래스가 없습니다.", true);
			return;
		}
		var checked = types.slice(0, METHOD_FIND_TYPE_MAX);
		K.message(el["kn-graph-message"], "클래스 " + checked.length + "개에서 메소드 \"" + methodWord + "\" 을(를) 찾는 중...");
		Promise.all(checked.map(function (type) {
			return K.call("GET", "/api/revisions/" + map.revisionId + "/graph/node", { id: type.id }).then(function (detail) {
				return { type: type, methods: detail.methods || [] };
			});
		})).then(function (results) {
			// 그사이에 다른 이름을 찾았거나 리비전을 바꿨다.
			if (map.hitWord !== word) {
				return;
			}
			var exact = [];
			var partial = [];
			results.forEach(function (result) {
				result.methods.forEach(function (method) {
					var name = String(method.name).toLowerCase();
					if (name.indexOf(methodWord) >= 0) {
						(name === methodWord ? exact : partial).push({ node: result.type, method: method, fullName: result.type.fullName + "." + method.signature });
					}
				});
			});
			// 이름이 정확히 같은 메소드를 앞에 둔다.
			map.hits = exact.concat(partial);
			if (map.hits.length === 0) {
				K.message(el["kn-graph-message"], "\"" + typeWord + "\" 클래스 " + checked.length + "개에 이름에 \"" + methodWord + "\" 이(가) 들어간 메소드가 없습니다."
					+ (types.length > checked.length ? " 클래스가 " + types.length + "개라 앞의 " + checked.length + "개만 봤습니다. 클래스 이름을 더 길게 적으세요." : ""), true);
				return;
			}
			showHit(word);
		}).catch(function (err) {
			K.message(el["kn-graph-message"], err.message, true);
		});
	}

	/** 찾은 것 가운데 hitIndex 번째로 갑니다. 메소드면 그 클래스로 간 뒤 메소드의 관계 트리를 엽니다 */
	function showHit(word) {
		var hit = map.hits[map.hitIndex];
		if (hit.method) {
			goTo(hit.node);
			openTree(hit.method.methodId);
		} else {
			goTo(hit);
		}
		K.message(el["kn-graph-message"], "\"" + word + "\" " + map.hits.length + "건 가운데 " + (map.hitIndex + 1) + "번째: " + hit.fullName
			+ (map.hits.length > 1 ? " (찾기를 다시 누르면 다음 것)" : ""));
	}

	/** 서버가 준 노드(클래스 / 화면 / 매퍼 / 테이블) 하나로 갑니다. 꺼 둔 계층이면 켜고, 접힌 묶음이면 펼칩니다 */
	function goTo(rawNode) {
		var redraw = false;
		var tierInput = el["kn-graph-tiers"].querySelector("input[data-tier=\"" + rawNode.tier + "\"]");
		if (!tierInput.checked) {
			tierInput.checked = true;
			redraw = true;
		}
		if (redraw || !map.view.nodes[rawNode.id]) {
			map.expanded[rawNode.groupKey] = true;
			drawMap();
		}
		selectNode(rawNode.id);
		var node = map.cy.getElementById(rawNode.id);
		map.cy.animate({ center: { eles: node }, zoom: Math.max(map.cy.zoom(), 0.9) }, { duration: 250 });
	}

	/* ======================= 상세 ======================= */

	function clearDetail(container, text) {
		container.removeAttribute("data-node");
		container.innerHTML = "<p class=\"ai-hint\">" + K.escapeHtml(text) + "</p>";
	}

	/** 전체 맵에서 고른 노드의 설명. 묶음이면 든 것의 목록을, 아니면 서버에서 상세를 가져와 보여 줍니다 */
	function showMapDetail(node, linked) {
		var container = el["kn-graph-detail"];
		container.setAttribute("data-node", node.id);
		var linkedHtml = linkedList("이 노드를 부르는 쪽 (파란 테)", linked.up) + linkedList("이 노드가 부르는 쪽 (주황 테)", linked.down);

		if (node.kind === "GROUP") {
			container.innerHTML = head("GROUP", node.name, node.tier) + "<div class=\"kn-gd-actions\"><button type=\"button\" class=\"kn-btn\" data-act=\"expand\">펼치기</button></div>"
				+ "<p class=\"ai-hint\">패키지(폴더) 하나를 접어 둔 묶음입니다. 펼치면 안에 든 " + node.members.length + "개가 따로 그려집니다. 두 번 눌러도 펼쳐집니다.</p>"
				+ "<div class=\"kn-section-title\">든 것 · " + node.members.length + "개</div><div class=\"kn-gd-list\">" + node.members.map(function (member) {
					return "<a href=\"javascript:void(0)\" data-go=\"" + K.escapeHtml(member.id) + "\">" + K.escapeHtml(member.name) + "</a>";
				}).join("") + "</div>" + linkedHtml;
			bindMapDetail(container, node);
			return;
		}

		container.innerHTML = head(node.kind, node.name, node.tier) + "<p class=\"ai-hint\">불러오는 중...</p>";
		K.call("GET", "/api/revisions/" + map.revisionId + "/graph/node", { id: node.id }).then(function (detail) {
			if (container.getAttribute("data-node") !== node.id) {
				// 그사이에 다른 노드를 골랐다.
				return;
			}
			var actions = "<div class=\"kn-gd-actions\"><button type=\"button\" class=\"kn-btn\" data-act=\"tree\">관계 트리 따로 보기</button>"
				+ (el["kn-graph-unit"].value === "GROUP" && map.view.groups[node.groupKey].length > 1
					? "<button type=\"button\" class=\"kn-btn kn-plain\" data-act=\"collapse\">이 묶음 접기</button>" : "") + "</div>";
			container.innerHTML = head(detail.kind, detail.name, node.tier) + actions + detailBody(detail) + linkedHtml;
			bindMapDetail(container, node);
			bindTreeLinks(container);
			bindCodeButton(container, detail);
		}).catch(function (err) {
			container.innerHTML = head(node.kind, node.name, node.tier) + "<p class=\"kn-message kn-error\">" + K.escapeHtml(err.message) + "</p>";
		});
	}

	function bindMapDetail(container, node) {
		Array.prototype.forEach.call(container.querySelectorAll("[data-act]"), function (button) {
			button.addEventListener("click", function () {
				var act = button.getAttribute("data-act");
				if (act === "tree") {
					openTree(node.id);
				} else {
					toggleGroup(node.groupKey, act === "expand");
				}
			});
		});
		Array.prototype.forEach.call(container.querySelectorAll("[data-go]"), function (link) {
			link.addEventListener("click", function () {
				var id = link.getAttribute("data-go");
				if (map.view.nodes[id]) {
					selectNode(id);
					return;
				}
				// 접힌 묶음 안의 노드다. 원래 노드를 찾아서 펼치고 간다.
				var raw = map.graph.nodes.filter(function (candidate) {
					return candidate.id === id;
				})[0];
				if (raw) {
					goTo(raw);
				}
			});
		});
	}

	/** 상세 안의 "트리" 링크(메소드, SQL statement)를 누르면 그것의 관계 트리를 엽니다 */
	function bindTreeLinks(container) {
		Array.prototype.forEach.call(container.querySelectorAll("[data-tree]"), function (link) {
			link.addEventListener("click", function () {
				openTree(link.getAttribute("data-tree"));
			});
		});
	}

	function linkedList(title, nodes) {
		if (!nodes || nodes.length === 0) {
			return "";
		}
		var items = [];
		nodes.forEach(function (node) {
			items.push({ id: node.id(), label: node.data("label") });
		});
		items.sort(function (a, b) {
			return a.label.localeCompare(b.label);
		});
		var limit = 60;
		return "<div class=\"kn-section-title\">" + K.escapeHtml(title) + " · " + items.length + "개</div><div class=\"kn-gd-list\">" + items.slice(0, limit).map(function (item) {
			return "<a href=\"javascript:void(0)\" data-go=\"" + K.escapeHtml(item.id) + "\">" + K.escapeHtml(item.label) + "</a>";
		}).join("") + (items.length > limit ? "<span class=\"ai-hint\">… 그 밖에 " + (items.length - limit) + "개</span>" : "") + "</div>";
	}

	function head(kind, name, tier) {
		var color = (TIER_BY_KEY[tier] || TIER_BY_KEY.ETC).color;
		return "<div class=\"kn-gd-head\"><span class=\"kn-gd-kind\" style=\"background:" + color + ";\">" + K.escapeHtml(KIND_LABEL[kind] || kind) + "</span>"
			+ "<b>" + K.escapeHtml(name) + "</b></div>";
	}

	/** 한글명이 어디서 왔는지: DB 코멘트 → DDL 의 COMMENT → DDL 의 주석 → SQL 주석 → 소스 → 사전 파일 순서로 먼저 있는 것을 쓴다 */
	var NAME_SOURCE = { DB: "DB", DDL: "DDL", DDL_NOTE: "DDL 주석", SQL: "SQL 주석", SOURCE: "소스", DICT: "사전" };
	var NAME_SOURCE_HINT = {
		DB: "대상 DB의 코멘트",
		DDL: "DDL 파일(.sql)의 COMMENT",
		DDL_NOTE: "DDL 파일(.sql)의 일반 주석",
		SQL: "매퍼 SQL의 주석",
		SOURCE: "Java 소스(엔티티, 또는 이름이 같은 VO 필드의 설명). 이름만 같은 필드에서 가져온 것은 다른 테이블의 뜻일 수 있습니다",
		DICT: "프로젝트 설정에 등록한 사전 파일(CSV). 사람이 짐작해 붙인 이름이 섞여 있을 수 있습니다"
	};

	/** 출처 표시. 마우스를 올리면 어디서 가져왔는지(파일:줄, 필드, SQL 건수)가 나온다 */
	function nameSource(source, where) {
		if (!source) {
			return "";
		}
		var title = (NAME_SOURCE_HINT[source] || source) + (where ? " · " + where : "");
		return " <span class=\"kn-gd-tag" + (source === "SOURCE" ? " kn-gd-tag-guess" : "") + "\" title=\"" + K.escapeHtml(title) + "\">"
			+ K.escapeHtml(NAME_SOURCE[source] || source) + "</span>";
	}

	/** 화면명을 어디서 찾았는지: JSP·WebSquare 는 &lt;title&gt;, Nexacro 는 Form 의 titletext, 둘 다 없으면 메뉴 목록. 한글이 아니면 화면 사전 */
	var SCREEN_NAME_SOURCE = { TITLE: "title", TITLETEXT: "titletext", MENU: "메뉴", DICT: "사전" };
	var SCREEN_NAME_HINT = {
		TITLE: "화면 파일의 <title>",
		TITLETEXT: "화면 파일의 titletext",
		MENU: "소스에 적힌 메뉴 목록의 메뉴 이름(화면 파일에 제목이 없어서 가져온 것)",
		DICT: "프로젝트 설정에 등록한 화면 사전 파일(CSV)의 한글명. 화면 제목이나 메뉴 이름에 한글이 없어서 가져온 것"
	};

	/** 출처 표시. 마우스를 올리면 찾은 파일과 줄이 나온다 */
	function screenNameSource(via, where) {
		if (!via) {
			return "";
		}
		var title = (SCREEN_NAME_HINT[via] || via) + (where ? " · " + where : "");
		return " <span class=\"kn-gd-tag\" title=\"" + K.escapeHtml(title) + "\">" + K.escapeHtml(SCREEN_NAME_SOURCE[via] || via) + "</span>";
	}

	/** 테이블의 컬럼: 컬럼명, 컬럼 한글명, 타입, SQL에서 쓰인 정도 */
	function tableColumns(columns, coverage) {
		if (!coverage) {
			// 컬럼을 주지 않는 예전 dstone-knowledge 다.
			return "<div class=\"kn-section-title\">컬럼</div><p class=\"ai-hint\">dstone-knowledge 가 컬럼을 주지 않는 예전 버전입니다. 새로 빌드해서 다시 띄워 주세요.</p>";
		}
		var unread = Number(coverage.unread || 0);
		var statements = Number(coverage.statements || 0);
		var html = "<div class=\"kn-section-title\">컬럼 · " + columns.length + "개</div>";
		if (columns.length > 0) {
			html += "<table class=\"kn-gd-columns\"><thead><tr><th>컬럼명</th><th>컬럼한글명</th><th>타입</th><th>SQL</th></tr></thead><tbody>"
				+ columns.map(function (column) {
					var used = Number(column.statementCount || 0);
					return "<tr><td class=\"kn-mono\">" + K.escapeHtml(column.column) + "</td>"
						+ "<td>" + (column.koreanName ? K.escapeHtml(column.koreanName) + nameSource(column.koreanNameSource, column.koreanNameWhere) : "<span class=\"ai-hint\">-</span>") + "</td>"
						+ "<td class=\"kn-mono\">" + K.escapeHtml(column.dataType || "") + "</td>"
						+ "<td class=\"ai-hint\">" + (used > 0 ? used + "건" + (column.crud ? " · " + K.escapeHtml(column.crud.split("").join("·")) : "") : "안 씀") + "</td></tr>";
				}).join("") + "</tbody></table>";
		}
		html += dictionaryNote();
		if (statements > 0 && unread === statements) {
			html += "<p class=\"ai-hint\">SQL에서 쓰인 컬럼 정보가 없습니다. SQL을 파서로 읽지 못했거나, 컬럼을 모으기 전에 분석한 리비전입니다. "
				+ "뒤의 경우라면 SEMANTIC 부터 다시 분석하면(rerunFrom: SEMANTIC) 나옵니다.</p>";
		} else if (unread > 0) {
			html += "<p class=\"ai-hint\">SQL " + unread + "건은 파서로 읽지 못해 컬럼을 뽑지 못했습니다. 목록에 빠진 컬럼이 있을 수 있습니다.</p>";
		} else if (columns.length === 0) {
			html += "<p class=\"ai-hint\">SQL에 이름이 적힌 컬럼이 없습니다(SELECT * 등).</p>";
		}
		return html;
	}

	/** 한글명을 찾는 순서 */
	function dictionaryNote() {
		return "<p class=\"ai-hint\">한글명은 DB 코멘트 → DDL COMMENT → DDL 주석 → SQL 주석 → 소스 → 사전 파일 순서로 찾습니다.</p>";
	}

	/** 서버가 준 노드 상세(GET .../graph/node)를 그립니다. 종류마다 들어 있는 항목이 달라서, 있는 것만 그립니다 */
	function detailBody(detail) {
		var html = "<div class=\"kn-mono kn-gd-full\">" + K.escapeHtml(detail.fullName) + "</div>";
		var facts = [];
		if (detail.kind === "TABLE") {
			facts.push(["테이블명", detail.koreanName ? K.escapeHtml(detail.koreanName) + nameSource(detail.koreanNameSource, detail.koreanNameWhere) : "<span class=\"ai-hint\">한글명 없음</span>"]);
			facts.push(["물리명", "<span class=\"kn-mono\">" + K.escapeHtml(detail.name) + "</span>"]);
		}
		if (detail.kind === "JSP") {
			facts.push(["화면명", detail.screenName ? K.escapeHtml(detail.screenName) + screenNameSource(detail.screenNameVia, detail.screenNameWhere)
				: "<span class=\"ai-hint\" title=\"화면 파일에 &lt;title&gt; 이나 titletext 가 없고, 메뉴 목록(menu-rules)과 화면 사전(screen-file)에서도 찾지 못했습니다. 이 기능보다 먼저 분석한 리비전이면 SEMANTIC 부터 다시 분석하면(rerunFrom: SEMANTIC) 나옵니다.\">화면명 없음</span>"]);
			if (detail.screenNameOriginal) {
				// 사전의 한글명을 썼을 때, 화면 제목이나 메뉴에 적힌 원래 이름(일본어 등)
				facts.push(["원어명", K.escapeHtml(detail.screenNameOriginal)]);
			}
		}
		if (detail.layer) {
			facts.push(["계층", K.escapeHtml(detail.layer) + (detail.layerConfidence ? " (" + K.confidence(detail.layerConfidence) + ")" : "")]);
		}
		if (detail.subType && detail.subType !== detail.kind) {
			facts.push(["종류", K.escapeHtml(detail.subType)]);
		}
		if (detail.returnType) {
			facts.push(["반환", "<span class=\"kn-mono\">" + K.escapeHtml(detail.returnType) + "</span>"]);
		}
		if (detail.path) {
			facts.push(["파일", "<span class=\"kn-mono\">" + K.escapeHtml(detail.path) + (detail.lineStart ? " (줄 " + detail.lineStart
				+ (detail.lineEnd ? "-" + detail.lineEnd : "") + ")" : "") + "</span>"]);
		}
		if (detail.lineCount) {
			facts.push(["줄 수", K.escapeHtml(detail.lineCount)]);
		}
		if (detail.tables) {
			facts.push(["테이블", "<span class=\"kn-mono\">" + K.escapeHtml(detail.tables) + "</span>"]);
		}
		if (facts.length > 0) {
			html += "<table class=\"kn-gd-facts\">" + facts.map(function (fact) {
				return "<tr><th>" + fact[0] + "</th><td>" + fact[1] + "</td></tr>";
			}).join("") + "</table>";
		}
		if (detail.kind === "TABLE") {
			html += tableColumns(detail.columns || [], detail.columnCoverage);
		}

		if (detail.endpoints && detail.endpoints.length > 0) {
			html += "<div class=\"kn-section-title\">처리하는 진입점 · " + detail.endpoints.length + "개</div><div class=\"kn-gd-rows\">" + detail.endpoints.map(function (endpoint) {
				return "<div><span class=\"kn-gd-tag\">" + K.escapeHtml(endpoint.httpMethod || endpoint.endpointType) + "</span> <span class=\"kn-mono\">"
					+ K.escapeHtml(endpoint.path || "") + "</span>" + (endpoint.signature ? " <span class=\"ai-hint\">→ " + K.escapeHtml(endpoint.signature) + "</span>" : "") + "</div>";
			}).join("") + "</div>";
		}
		if (detail.methods && detail.methods.length > 0) {
			// 소스에 없는 메소드(자동으로 생기는 생성자, Lombok 이 만드는 것)는 뒤로 보낸다.
			var methods = detail.methods.slice().sort(function (a, b) {
				return (a.isSynthetic ? 1 : 0) - (b.isSynthetic ? 1 : 0);
			});
			// 기능명(title)은 메소드 위 주석의 첫 줄이나 @Description 값이다. 서버가 상세를 열 때 소스에서 읽어 준다.
			var titled = methods.filter(function (method) {
				return method.title;
			}).length;
			html += "<div class=\"kn-section-title\">메소드 · " + methods.length + "개"
				+ (titled > 0 ? " · 기능명 " + titled + "개" : "") + " <span class=\"ai-hint\">(누르면 그 메소드의 관계 트리)</span></div>"
				+ (detail.methodTitleNote ? "<p class=\"ai-hint\">" + K.escapeHtml(detail.methodTitleNote) + "</p>" : "")
				+ "<div class=\"kn-gd-rows\">"
				+ methods.slice(0, 200).map(function (method) {
					return "<div><a href=\"javascript:void(0)\" class=\"kn-mono\" data-tree=\"" + K.escapeHtml(method.methodId) + "\">" + K.escapeHtml(method.signature) + "</a>"
						+ (method.title ? " <span class=\"kn-gd-method-title\">" + K.escapeHtml(method.title) + "</span>" : "")
						+ (method.isSynthetic ? " <span class=\"ai-hint\">자동 생성</span>" : "") + "</div>";
				}).join("") + (methods.length > 200 ? "<div class=\"ai-hint\">… 그 밖에 " + (methods.length - 200) + "개</div>" : "") + "</div>";
		}
		if (detail.statements && detail.statements.length > 0) {
			html += "<div class=\"kn-section-title\">SQL statement · " + detail.statements.length + "개 <span class=\"ai-hint\">(누르면 그 SQL 의 관계 트리)</span></div>"
				+ "<div class=\"kn-gd-rows\">" + detail.statements.map(function (statement) {
					return "<div><span class=\"kn-gd-tag\">" + K.escapeHtml(statement.statementType) + "</span> <a href=\"javascript:void(0)\" class=\"kn-mono\" data-tree=\""
						+ K.escapeHtml(statement.id) + "\">" + K.escapeHtml(statement.statement) + "</a>"
						+ (statement.tables ? "<div class=\"ai-hint kn-mono\">" + K.escapeHtml(statement.tables) + "</div>" : "") + "</div>";
				}).join("") + "</div>";
		}
		if (detail.usage && detail.usage.length > 0) {
			html += "<div class=\"kn-section-title\">이 테이블을 건드리는 SQL · " + detail.usage.length + "건</div><div class=\"kn-gd-rows\">" + detail.usage.map(function (use) {
				return "<div><span class=\"kn-gd-tag\">" + K.escapeHtml(use.crud || "?") + "</span> <span class=\"kn-mono\">" + K.escapeHtml(use.statement) + "</span> "
					+ K.confidence(use.confidence) + (use.method ? "<div class=\"ai-hint kn-mono\">← " + K.escapeHtml(use.method) + "</div>" : "") + "</div>";
			}).join("") + "</div>";
		}
		if (detail.document || detail.code) {
			html += "<div class=\"kn-section-title\">설명 <span class=\"ai-hint\">(분석이 만들어 둔 문서. 검색에 쓰는 것과 같습니다)</span>" + codeButton(detail.code) + "</div>"
				+ (detail.document ? "<pre class=\"kn-gd-doc\">" + K.escapeHtml(detail.document) + "</pre>" : "");
		} else if (detail.kind === "METHOD") {
			html += "<p class=\"ai-hint\">설명 문서가 없습니다. 단순한 getter / setter 는 문서를 만들지 않습니다.</p>";
		}
		return html;
	}

	/* ======================= 소스 / SQL 팝업 ======================= */

	var CODE_LABEL = { SOURCE: "소스", SQL: "SQL" };

	/** 팝업에 띄운 본문. SQL 이면 들여쓰기를 넣은 것(formatted)과 원문(text)을 바꿔 볼 수 있다 */
	var codeView = { code: null, raw: false, language: null };

	/** 이보다 긴 글은 색을 입히지 않고 그대로 보여 준다(브라우저가 오래 멈추지 않게) */
	var HIGHLIGHT_MAX_CHARS = 300000;

	/** 파일 확장자 → highlight.js 언어 이름 */
	var HIGHLIGHT_LANGUAGE_BY_EXT = { java: "java", jsp: "xml", xml: "xml", xfdl: "xml", xadl: "xml", html: "xml", htm: "xml", js: "javascript", sql: "sql" };

	/**
	 * 소스 팝업에서 색을 입힐 언어. SQL 이면 sql, 소스면 파일 확장자로 고른다. 모르면 null(색 없이 보여 준다).
	 */
	function highlightLanguage(detail) {
		if (detail.code.kind === "SQL") {
			return "sql";
		}
		var path = String(detail.path || "");
		var ext = path.lastIndexOf(".") >= 0 ? path.substring(path.lastIndexOf(".") + 1).toLowerCase() : "";
		return HIGHLIGHT_LANGUAGE_BY_EXT[ext] || null;
	}

	/** 설명 제목 옆에 붙는 [소스 보기] / [SQL 보기] 버튼. 소스나 SQL 이 없으면 빈 글자 */
	function codeButton(code) {
		if (!code || !code.text) {
			return "";
		}
		var lines = code.text.split("\n").length;
		return "<button type=\"button\" class=\"kn-plain kn-gd-code-btn\" data-code=\"1\" title=\"" + lines + "줄\">"
			+ K.escapeHtml(CODE_LABEL[code.kind] || "소스") + " 보기</button>";
	}

	function bindCodeButton(container, detail) {
		var button = container.querySelector("[data-code]");
		if (button) {
			button.addEventListener("click", function () {
				openCode(detail);
			});
		}
	}

	function openCode(detail) {
		var label = CODE_LABEL[detail.code.kind] || "소스";
		var kindLabel = KIND_LABEL[detail.kind] || detail.kind;
		// SQL 노드면 "SQL · SQL ..." 이 되므로 종류는 한 번만 적는다.
		el["kn-code-title"].textContent = label + " · " + (kindLabel === label ? "" : kindLabel + " ") + (detail.fullName || detail.name);
		var where = detail.path ? detail.path + (detail.lineStart ? " (줄 " + detail.lineStart + (detail.lineEnd ? "-" + detail.lineEnd : "") + ")" : "") : "";
		if (detail.kind === "JSP") {
			// 화면 문서는 검색용으로 빈 줄과 들여쓰기를 뺀 것이다.
			where += (where ? " · " : "") + "빈 줄과 들여쓰기를 뺀 내용입니다";
		}
		el["kn-code-where"].textContent = where;
		codeView.code = detail.code;
		codeView.raw = false;
		codeView.language = highlightLanguage(detail);
		// SQL 은 들여쓰기를 넣은 것을 먼저 보여 준다. 원문과 다를 때만 바꿔 보기 버튼을 둔다.
		el["kn-code-raw"].style.display = detail.code.formatted && detail.code.formatted !== detail.code.text ? "" : "none";
		showCodeText();
		el["kn-code-modal"].style.display = "flex";
	}

	function showCodeText() {
		var code = codeView.code;
		var text = !codeView.raw && code.formatted ? code.formatted : code.text;
		el["kn-code-text"].textContent = text;
		// 구문 강조(highlight.js, knowledge/assets/lib). 글자는 그대로이고 색만 입히므로 복사는 textContent 로 그대로 된다.
		if (window.hljs && codeView.language && text.length <= HIGHLIGHT_MAX_CHARS) {
			el["kn-code-text"].innerHTML = window.hljs.highlight(text, { language: codeView.language, ignoreIllegals: true }).value;
		}
		el["kn-code-text"].scrollTop = 0;
		el["kn-code-text"].scrollLeft = 0;
		el["kn-code-raw"].textContent = codeView.raw ? "정리해서 보기" : "원문 보기";
		el["kn-code-copy"].textContent = "복사";
	}

	function toggleRawCode() {
		codeView.raw = !codeView.raw;
		showCodeText();
	}

	function closeCode() {
		el["kn-code-modal"].style.display = "none";
		el["kn-code-text"].textContent = "";
	}

	/** 클립보드에 복사합니다. http 주소에서는 navigator.clipboard 가 없어서 예전 방식으로 복사합니다 */
	function copyCode() {
		var text = el["kn-code-text"].textContent;
		var done = function () {
			el["kn-code-copy"].textContent = "복사됨";
		};
		if (navigator.clipboard && window.isSecureContext) {
			navigator.clipboard.writeText(text).then(done).catch(function () {
				copyByTextarea(text) && done();
			});
			return;
		}
		if (copyByTextarea(text)) {
			done();
		}
	}

	function copyByTextarea(text) {
		var area = document.createElement("textarea");
		area.value = text;
		area.style.position = "fixed";
		area.style.top = "-1000px";
		document.body.appendChild(area);
		area.select();
		var ok = false;
		try {
			ok = document.execCommand("copy");
		} catch (e) {
			ok = false;
		}
		document.body.removeChild(area);
		return ok;
	}

	/* ======================= 관계 트리 팝업 ======================= */

	function openTree(nodeId) {
		el["kn-tree-modal"].style.display = "flex";
		loadTree(nodeId);
	}

	/* 관계 트리 팝업도 전체 화면으로 키울 수 있다. 같은 팝업을 키우므로 그린 트리와 고른 노드가 그대로 남는다.
	 * 다시 열 때도 마지막 크기를 그대로 쓴다(계층 보이기 설정처럼). */

	function isTreeFullscreen() {
		return el["kn-tree-modal"].classList.contains("kn-full");
	}

	function setTreeFullscreen(on) {
		el["kn-tree-modal"].classList.toggle("kn-full", on);
		el["kn-tree-full"].textContent = on ? "전체 화면 끝내기" : "전체 화면";
		// Cytoscape 는 창 크기만 지켜보므로 칸이 바뀐 것을 직접 알려 주고 그림을 칸에 맞춘다.
		window.requestAnimationFrame(function () {
			if (tree.cy) {
				tree.cy.resize();
				tree.cy.fit(undefined, 30);
			}
		});
	}

	function closeTree() {
		el["kn-tree-modal"].style.display = "none";
		if (tree.cy) {
			tree.cy.destroy();
			tree.cy = null;
		}
	}

	function loadTree(nodeId) {
		tree.startId = nodeId;
		K.message(el["kn-tree-message"], "불러오는 중...");
		clearDetail(el["kn-tree-detail"], "노드를 누르면 여기에 설명이 나옵니다.");
		K.call("GET", "/api/revisions/" + map.revisionId + "/graph/neighborhood", {
			id: nodeId, up: el["kn-tree-up"].value, down: el["kn-tree-down"].value, includeModel: el["kn-tree-model"].checked
		}).then(function (result) {
			el["kn-tree-title"].textContent = (KIND_LABEL[result.start.kind] || result.start.kind) + " " + result.start.fullName;
			result.nodes.forEach(function (node) {
				node.tier = tierOf(node.layer);
				node.label = treeLabel(node);
			});
			tree.result = result;
			renderTreeTiers();
			drawTree();
		}).catch(function (err) {
			K.message(el["kn-tree-message"], err.message, true);
		});
	}

	/** 관계 트리의 계층 칩(보이기 / 숨기기). 칩 옆 숫자는 이 트리에 있는 그 계층의 노드 수입니다 */
	function renderTreeTiers() {
		var counts = {};
		tree.result.nodes.forEach(function (node) {
			counts[node.tier] = (counts[node.tier] || 0) + 1;
		});
		el["kn-tree-tiers"].innerHTML = TREE_TIERS.map(function (key) {
			var tier = TIER_BY_KEY[key];
			return "<label class=\"kn-graph-chip" + (tree.hidden[key] ? " kn-off" : "") + "\" style=\"border-color:" + tier.color + ";background:" + tier.fill + ";\">"
				+ "<input type=\"checkbox\" data-tree-tier=\"" + key + "\"" + (tree.hidden[key] ? "" : " checked") + " /> " + K.escapeHtml(tier.label)
				+ " <span>" + (counts[key] || 0) + "</span></label>";
		}).join("");
		Array.prototype.forEach.call(el["kn-tree-tiers"].querySelectorAll("input"), function (input) {
			input.addEventListener("change", function () {
				var key = input.getAttribute("data-tree-tier");
				tree.hidden[key] = !input.checked;
				input.parentNode.classList.toggle("kn-off", !input.checked);
				clearDetail(el["kn-tree-detail"], "노드를 누르면 여기에 설명이 나옵니다.");
				drawTree();
			});
		});
	}

	/** 끈 계층이 아니면 보입니다. 고른 노드(가운데)는 늘 보입니다 */
	function treeVisible(node) {
		return node.start || !tree.hidden[node.tier];
	}

	/**
	 * <pre>
	 * 보이는 노드끼리의 선을 만듭니다. 숨긴 노드를 거쳐 이어지는 길은 점선(bypass) 하나로 잇습니다.
	 * 예: 서비스를 끄면 컨트롤러 → (서비스) → DAO 가 컨트롤러 ┄> DAO 가 됩니다.
	 * 선의 색은 마지막 한 걸음(보이는 노드로 들어가는 선)의 종류를 따릅니다.
	 * </pre>
	 */
	function treeEdges(result) {
		var outgoing = {};
		result.edges.forEach(function (edge) {
			(outgoing[edge.from] = outgoing[edge.from] || []).push(edge);
		});
		var edges = [];
		var seen = {};
		result.edges.forEach(function (edge) {
			var from = tree.nodes[edge.from];
			var to = tree.nodes[edge.to];
			if (!from || !to || !treeVisible(from)) {
				return;
			}
			if (treeVisible(to)) {
				edges.push({ from: edge.from, to: edge.to, type: edge.type, low: edge.confidence === "LOW", bypass: false });
				seen[edge.from + ">" + edge.to] = true;
				return;
			}
			// 숨긴 노드에서부터 보이는 노드가 나올 때까지 따라간다(순환이 있어도 한 번씩만 들른다).
			var visited = {};
			var stack = [edge.to];
			visited[edge.to] = true;
			while (stack.length > 0) {
				var id = stack.pop();
				(outgoing[id] || []).forEach(function (next) {
					var target = tree.nodes[next.to];
					if (!target || visited[next.to]) {
						return;
					}
					visited[next.to] = true;
					if (!treeVisible(target)) {
						stack.push(next.to);
						return;
					}
					var key = edge.from + ">" + next.to;
					if (!seen[key] && next.to !== edge.from) {
						seen[key] = true;
						edges.push({ from: edge.from, to: next.to, type: next.type, low: false, bypass: true });
					}
				});
			}
		});
		return edges;
	}

	/**
	 * 관계 트리를 그립니다. 노드의 level 이 가로 자리입니다(0 = 고른 것, 음수 = 부르는 쪽, 양수 = 불리는 쪽).
	 * 한 level 에 노드가 많으면 여러 열로 나눕니다. 끈 계층의 노드는 빼고 자리를 다시 잡습니다.
	 */
	function drawTree() {
		var result = tree.result;
		var colWidth = 230;
		var rowHeight = 30;
		var maxRows = 40;
		var levels = {};
		var hiddenCount = 0;
		tree.nodes = {};
		result.nodes.forEach(function (node) {
			tree.nodes[node.id] = node;
			if (!treeVisible(node)) {
				hiddenCount++;
				return;
			}
			(levels[node.level] = levels[node.level] || []).push(node);
		});

		var elements = [];
		var x = 0;
		Object.keys(levels).map(Number).sort(function (a, b) {
			return a - b;
		}).forEach(function (level) {
			var list = levels[level];
			list.sort(function (a, b) {
				return a.label.localeCompare(b.label);
			});
			var cols = Math.ceil(list.length / maxRows);
			var rows = Math.ceil(list.length / cols);
			list.forEach(function (node, index) {
				var tier = TIER_BY_KEY[node.tier];
				var width = labelWidth(node.label);
				elements.push({
					group: "nodes",
					data: { id: node.id, label: node.label, color: tier.color, fill: tier.fill, w: width, tw: (width - 10) + "px" },
					// 줄 수가 다른 level 끼리도 가운데가 맞도록 세로로 가운데 정렬한다.
					position: { x: x + Math.floor(index / rows) * colWidth, y: ((index % rows) - (rows - 1) / 2) * rowHeight },
					classes: "kind-" + node.kind + (node.start ? " start" : "")
				});
			});
			x += cols * colWidth + 40;
		});
		var edges = treeEdges(result);
		edges.forEach(function (edge, index) {
			var cat = CAT_OF_RELATION[edge.type] || "CALL";
			elements.push({
				group: "edges",
				data: { id: "e" + index, source: edge.from, target: edge.to, color: EDGE_CATS[cat].color, w: 1.5 },
				classes: edge.bypass ? "bypass" : (edge.low ? "low" : "")
			});
		});

		if (tree.cy) {
			tree.cy.destroy();
		}
		tree.cy = cytoscape({
			container: el["kn-tree-canvas"],
			elements: elements,
			style: styles().concat([{ selector: "node.start", style: { "border-width": 3, "border-color": "#ca8a04", "font-weight": "bold" } },
				{ selector: "edge.bypass", style: { "line-style": "dotted", "width": 2 } }]),
			layout: { name: "preset" },
			minZoom: 0.05,
			maxZoom: 3
		});
		tree.cy.fit(undefined, 30);
		// 부르는 쪽이 하나도 없으면 깊이를 늘려도 그대로다. 그렇다고 알려 준다.
		var noCallers = parseInt(el["kn-tree-up"].value, 10) > 0 && !(result.nodes || []).some(function (treeNode) {
			return treeNode.level < 0;
		});
		K.message(el["kn-tree-message"], result.nodeCount === 0
			? "이어진 것이 없습니다. 아무도 부르지 않고 아무것도 부르지 않거나, 분석이 잇지 못한 호출일 수 있습니다."
			: "노드 " + result.nodeCount + "개 · 선 " + result.edgeCount + "개"
				+ (hiddenCount > 0 ? "(끈 계층의 노드 " + hiddenCount + "개는 숨김. 숨긴 노드를 건너 이어지는 길은 점선)" : "")
				+ ". 가운데(노란 테)가 고른 것, 왼쪽이 부르는 쪽, 오른쪽이 불리는 쪽입니다."
				+ (noCallers ? " 이것을 부르는 곳은 분석에서 하나도 나오지 않았습니다. 그래서 부르는 쪽 깊이를 늘려도 화면이 나오지 않습니다."
					+ " 쓰이지 않는 코드이거나, 리플렉션·설정 파일처럼 소스에 이름이 드러나지 않는 방법으로 부를 수 있습니다." : "")
				+ (result.truncated ? " 너무 많아서 가까운 것부터 일부만 가져왔습니다. 깊이를 줄이거나 더 작은 것(메소드 하나)을 고르세요." : ""), false);
		tree.cy.on("tap", "node", function (event) {
			highlight(tree.cy, event.target, "ALL");
			showTreeDetail(tree.nodes[event.target.id()]);
		});
		tree.cy.on("tap", function (event) {
			if (event.target === tree.cy) {
				tree.cy.elements().removeClass("faded up down sel hl");
				clearDetail(el["kn-tree-detail"], "노드를 누르면 여기에 설명이 나옵니다.");
			}
		});
	}

	/** 트리에서는 메소드 이름 앞에 클래스 이름을 붙입니다: OrderService#cancel */
	function treeLabel(node) {
		if (node.kind === "METHOD" || node.kind === "FIELD") {
			return (node.owner ? node.owner + "#" : "") + node.name;
		}
		if (node.kind === "JSP") {
			return String(node.name).replace(/^.*\//, "");
		}
		return String(node.name);
	}

	function showTreeDetail(node) {
		var container = el["kn-tree-detail"];
		container.setAttribute("data-node", node.id);
		container.innerHTML = head(node.kind, node.label, node.tier) + "<p class=\"ai-hint\">불러오는 중...</p>";
		// 필드는 상세가 따로 없다(필드의 초기값에서 부르는 호출 때문에 트리에 나올 뿐이다).
		if (node.kind === "FIELD") {
			container.innerHTML = head(node.kind, node.label, node.tier) + "<div class=\"kn-mono kn-gd-full\">" + K.escapeHtml(node.fullName) + "</div>"
				+ "<p class=\"ai-hint\">필드입니다. 필드의 초기값에서 부르는 호출이 있어서 여기에 나왔습니다.</p>";
			return;
		}
		K.call("GET", "/api/revisions/" + map.revisionId + "/graph/node", { id: node.id }).then(function (detail) {
			if (container.getAttribute("data-node") !== node.id) {
				return;
			}
			container.innerHTML = head(detail.kind, node.label, node.tier)
				+ functionName(detail)
				+ (node.id === tree.startId ? "" : "<div class=\"kn-gd-actions\"><button type=\"button\" class=\"kn-btn kn-plain\" data-tree=\"" + K.escapeHtml(node.id)
					+ "\">이 노드를 가운데로 다시 그리기</button></div>")
				+ detailBody(detail);
			bindTreeLinks(container);
			bindCodeButton(container, detail);
		}).catch(function (err) {
			container.innerHTML = head(node.kind, node.label, node.tier) + "<p class=\"kn-message kn-error\">" + K.escapeHtml(err.message) + "</p>";
		});
	}

	/**
	 * 메소드의 기능명(메소드 위 주석의 첫 줄이나 @Description)을 노드 이름 바로 아래에 따로 보여 줍니다.
	 * 메소드가 아니면 빈 글자입니다.
	 */
	function functionName(detail) {
		if (detail.kind !== "METHOD") {
			return "";
		}
		if (detail.title) {
			return "<div class=\"kn-gd-func\"><span class=\"kn-gd-func-label\">기능명</span>" + K.escapeHtml(detail.title) + "</div>";
		}
		return "<div class=\"kn-gd-func kn-gd-func-none\"><span class=\"kn-gd-func-label\">기능명</span>"
			+ K.escapeHtml(detail.titleNote || "없음 (메소드 위에 설명 주석이 없습니다)") + "</div>";
	}

	return { init: init };
})();
