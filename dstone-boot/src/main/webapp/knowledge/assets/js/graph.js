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

	var DEFAULT_RELATIONS = "CALLS,CALLS_POSSIBLE_IMPLEMENTATION,EXECUTES_SQL,READS_TABLE,WRITES_TABLE,RENDERS,REQUESTS";

	/** 전체 맵의 상태 */
	var map = { revisionId: null, graph: null, expanded: {}, view: null, cy: null, selectedId: null, hits: [], hitIndex: 0, loadSeq: 0 };

	/** 관계 트리 팝업의 상태 */
	var tree = { cy: null, startId: null, nodes: {} };

	function init() {
		["kn-graph-project", "kn-graph-revision", "kn-graph-load", "kn-graph-unit", "kn-graph-layout", "kn-graph-range", "kn-graph-low", "kn-graph-struct",
			"kn-graph-tiers", "kn-graph-edges", "kn-graph-find", "kn-graph-find-btn", "kn-graph-fit", "kn-graph-message", "kn-graph-canvas", "kn-graph-detail",
			"kn-tree-modal", "kn-tree-title", "kn-tree-up", "kn-tree-down", "kn-tree-model", "kn-tree-reload", "kn-tree-close", "kn-tree-message",
			"kn-tree-canvas", "kn-tree-detail"].forEach(function (id) {
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
		el["kn-graph-find-btn"].addEventListener("click", find);
		el["kn-graph-find"].addEventListener("keydown", function (event) {
			if (event.key === "Enter") {
				find();
			}
		});

		el["kn-tree-close"].addEventListener("click", closeTree);
		el["kn-tree-reload"].addEventListener("click", function () {
			loadTree(tree.startId);
		});
		el["kn-tree-modal"].addEventListener("click", function (event) {
			// 팝업 바깥(어두운 바탕)을 누르면 닫는다.
			if (event.target === el["kn-tree-modal"]) {
				closeTree();
			}
		});
		document.addEventListener("keydown", function (event) {
			if (event.key === "Escape") {
				closeTree();
			}
		});

		K.loadProjects(el["kn-graph-project"]).then(loadRevisions).then(loadGraph).catch(function (err) {
			K.message(el["kn-graph-message"], err.message, true);
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

	function drawMap() {
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
		map.cy.fit(undefined, 30);
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
		// 전체가 화면 비율(가로가 조금 긴 모양)에 가깝게 한 열의 줄 수를 정한다.
		var rows = Math.max(12, Math.ceil(Math.sqrt(nodes.length * colWidth / (rowHeight * 1.8))));
		var positions = {};
		var labels = [];
		var x = 0;
		TIERS.forEach(function (tier) {
			var list = nodes.filter(function (node) {
				return node.tier === tier.key;
			});
			if (list.length === 0) {
				return;
			}
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
		drawMap();
	}

	/** 이름으로 노드를 찾아 그리로 갑니다. 접힌 묶음 안에 있으면 그 묶음을 펼칩니다. 다시 누르면 다음 것으로 갑니다 */
	function find() {
		var word = el["kn-graph-find"].value.trim().toLowerCase();
		if (!map.graph || !word) {
			return;
		}
		if (map.hitWord !== word) {
			map.hitWord = word;
			map.hitIndex = 0;
			map.hits = map.graph.nodes.filter(function (node) {
				return String(node.name).toLowerCase().indexOf(word) >= 0 || String(node.fullName).toLowerCase().indexOf(word) >= 0;
			});
			// 이름이 정확히 같은 것을 앞에 둔다.
			map.hits.sort(function (a, b) {
				return (String(b.name).toLowerCase() === word ? 1 : 0) - (String(a.name).toLowerCase() === word ? 1 : 0);
			});
		} else {
			map.hitIndex = (map.hitIndex + 1) % Math.max(map.hits.length, 1);
		}
		if (map.hits.length === 0) {
			K.message(el["kn-graph-message"], "\"" + word + "\" 이(가) 이름에 들어간 노드가 없습니다.", true);
			return;
		}
		var hit = map.hits[map.hitIndex];
		goTo(hit);
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

	/** 서버가 준 노드 상세(GET .../graph/node)를 그립니다. 종류마다 들어 있는 항목이 달라서, 있는 것만 그립니다 */
	function detailBody(detail) {
		var html = "<div class=\"kn-mono kn-gd-full\">" + K.escapeHtml(detail.fullName) + "</div>";
		var facts = [];
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
			html += "<div class=\"kn-section-title\">메소드 · " + methods.length + "개 <span class=\"ai-hint\">(누르면 그 메소드의 관계 트리)</span></div><div class=\"kn-gd-rows\">"
				+ methods.slice(0, 200).map(function (method) {
					return "<div><a href=\"javascript:void(0)\" class=\"kn-mono\" data-tree=\"" + K.escapeHtml(method.methodId) + "\">" + K.escapeHtml(method.signature) + "</a>"
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
		if (detail.document) {
			html += "<div class=\"kn-section-title\">설명 <span class=\"ai-hint\">(분석이 만들어 둔 문서. 검색에 쓰는 것과 같습니다)</span></div><pre class=\"kn-gd-doc\">"
				+ K.escapeHtml(detail.document) + "</pre>";
		} else if (detail.kind === "METHOD") {
			html += "<p class=\"ai-hint\">설명 문서가 없습니다. 단순한 getter / setter 는 문서를 만들지 않습니다.</p>";
		}
		return html;
	}

	/* ======================= 관계 트리 팝업 ======================= */

	function openTree(nodeId) {
		el["kn-tree-modal"].style.display = "flex";
		loadTree(nodeId);
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
			drawTree(result);
			K.message(el["kn-tree-message"], result.nodeCount === 0
				? "이어진 것이 없습니다. 아무도 부르지 않고 아무것도 부르지 않거나, 분석이 잇지 못한 호출일 수 있습니다."
				: "노드 " + result.nodeCount + "개 · 선 " + result.edgeCount + "개. 가운데(노란 테)가 고른 것, 왼쪽이 부르는 쪽, 오른쪽이 불리는 쪽입니다."
					+ (result.truncated ? " 너무 많아서 가까운 것부터 일부만 가져왔습니다. 깊이를 줄이거나 더 작은 것(메소드 하나)을 고르세요." : ""), false);
		}).catch(function (err) {
			K.message(el["kn-tree-message"], err.message, true);
		});
	}

	/**
	 * 관계 트리를 그립니다. 노드의 level 이 가로 자리입니다(0 = 고른 것, 음수 = 부르는 쪽, 양수 = 불리는 쪽).
	 * 한 level 에 노드가 많으면 여러 열로 나눕니다.
	 */
	function drawTree(result) {
		var colWidth = 230;
		var rowHeight = 30;
		var maxRows = 40;
		var levels = {};
		tree.nodes = {};
		result.nodes.forEach(function (node) {
			node.tier = tierOf(node.layer);
			node.label = treeLabel(node);
			tree.nodes[node.id] = node;
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
		result.edges.forEach(function (edge, index) {
			var cat = CAT_OF_RELATION[edge.type] || "CALL";
			elements.push({
				group: "edges",
				data: { id: "e" + index, source: edge.from, target: edge.to, color: EDGE_CATS[cat].color, w: 1.5 },
				classes: edge.confidence === "LOW" ? "low" : ""
			});
		});

		if (tree.cy) {
			tree.cy.destroy();
		}
		tree.cy = cytoscape({
			container: el["kn-tree-canvas"],
			elements: elements,
			style: styles().concat([{ selector: "node.start", style: { "border-width": 3, "border-color": "#ca8a04", "font-weight": "bold" } }]),
			layout: { name: "preset" },
			minZoom: 0.05,
			maxZoom: 3
		});
		tree.cy.fit(undefined, 30);
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
				+ (node.id === tree.startId ? "" : "<div class=\"kn-gd-actions\"><button type=\"button\" class=\"kn-btn kn-plain\" data-tree=\"" + K.escapeHtml(node.id)
					+ "\">이 노드를 가운데로 다시 그리기</button></div>")
				+ detailBody(detail);
			bindTreeLinks(container);
		}).catch(function (err) {
			container.innerHTML = head(node.kind, node.label, node.tier) + "<p class=\"kn-message kn-error\">" + K.escapeHtml(err.message) + "</p>";
		});
	}

	return { init: init };
})();
