/**
 * "노드 맵" 화면. 호출 구조를 그림으로 봅니다.
 *
 *   1. 전체 맵     클래스 / 화면 / SQL 매퍼 / 테이블이 노드입니다. 많으면 패키지(폴더) 묶음으로 접어서 보여 줍니다.
 *   2. 강조        노드를 누르면 거기에 이어진 노드와 선만 남기고 나머지는 흐리게 합니다.
 *   3. 관계 트리   "따로 보기"를 누르면 그 노드에 이어진 길을 메소드 단위로 따로 띄운 창(관계 트리 창, tree.js)에 그립니다.
 *   4. 상세        누른 노드의 설명을 오른쪽에 보여 줍니다.
 *
 * 그림은 Cytoscape.js(knowledge/assets/lib/cytoscape.min.js)로 그립니다.
 * 계층 색, 그림 모양, 오른쪽 상세 칸, 소스 팝업은 관계 트리 창과 같이 쓰므로 graph-common.js 에 있습니다.
 */
var DstoneKnowledgeGraph = (function () {

	var K = DstoneKnowledge;
	var G = DstoneKnowledgeGraphCommon;
	var el = {};

	var TIERS = G.TIERS;
	var TIER_BY_KEY = G.TIER_BY_KEY;
	var EDGE_CATS = G.EDGE_CATS;
	var CAT_OF_RELATION = G.CAT_OF_RELATION;
	var tierOf = G.tierOf;
	var labelWidth = G.labelWidth;
	var styles = G.styles;
	var highlight = G.highlight;
	var clearDetail = G.clearDetail;
	var head = G.head;
	var detailBody = G.detailBody;

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

	function init() {
		["kn-graph-project", "kn-graph-revision", "kn-graph-load", "kn-graph-unit", "kn-graph-layout", "kn-graph-range", "kn-graph-low", "kn-graph-struct",
			"kn-graph-tiers", "kn-graph-edges", "kn-graph-find", "kn-graph-find-btn", "kn-graph-fit", "kn-graph-message", "kn-graph-canvas", "kn-graph-detail",
			"kn-graph-workspace", "kn-graph-full", "kn-graph-cond-toggle"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		G.initCodePopup();

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

		document.addEventListener("keydown", function (event) {
			if (event.key !== "Escape") {
				return;
			}
			// 소스 팝업이 열려 있으면 그것만 닫는다. 전체 화면은 그다음 Esc 에서 끝낸다.
			if (G.isCodeOpen()) {
				G.closeCode();
				return;
			}
			var tag = (event.target && event.target.tagName) || "";
			if (isFullscreen() && tag !== "INPUT" && tag !== "TEXTAREA" && tag !== "SELECT") {
				setFullscreen(false);
			}
		});

		// 다른 화면(첫 화면 대시보드)에서 오면 주소에 #p=프로젝트&r=리비전 이 붙어 온다. 그 프로젝트 · 리비전을 고르고 맵을 그린다.
		// 관계 트리는 이 화면을 거치지 않고 관계 트리 창(knowledge/graph/tree)을 바로 연다.
		var wanted = K.readHash();
		K.loadProjects(el["kn-graph-project"]).then(function () {
			selectOption(el["kn-graph-project"], wanted.p);
			return loadRevisions();
		}).then(function () {
			selectOption(el["kn-graph-revision"], wanted.r);
			return loadGraph();
		}).then(function () {
			// 한 번 쓰고 나면 주소를 비운다
			if (location.hash) {
				history.replaceState(null, "", location.pathname + location.search);
			}
		}).catch(function (err) {
			K.message(el["kn-graph-message"], err.message, true);
		});
	}

	/** 고르는 칸에 그 값이 있으면 고른다 */
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
		K.message(el["kn-graph-message"], "\"" + word + "\" " + map.hits.length + "건 가운데 " + (map.hitIndex + 1) + "번째: " + hit.fullName
			+ (map.hits.length > 1 ? " (찾기를 다시 누르면 다음 것)" : ""));
		if (hit.method) {
			goTo(hit.node);
			// 팝업이 막히면 openTree 가 알림 줄을 "창 열기" 링크로 바꾼다. 그래서 알림을 먼저 쓴다.
			openTree(hit.method.methodId);
		} else {
			goTo(hit);
		}
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
			G.bindTreeLinks(container, openTree);
			G.bindCodeButton(container, detail);
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

	/**
	 * <pre>
	 * 그 노드의 관계 트리를 관계 트리 창에 엽니다. 창은 하나를 같이 쓰므로 이미 떠 있으면 그 창의 내용만 바뀝니다.
	 * 브라우저가 팝업을 막으면 맵 위의 알림 줄에 창을 여는 링크를 대신 보여 줍니다.
	 * </pre>
	 */
	function openTree(nodeId) {
		var url = K.treeUrl(el["kn-graph-project"].value, map.revisionId, nodeId);
		if (!K.openTreeWindow(url)) {
			K.message(el["kn-graph-message"], "");
			el["kn-graph-message"].innerHTML = "<span class=\"kn-error\">브라우저가 팝업을 막았습니다. 이 사이트의 팝업을 허용하거나 </span>"
				+ "<a href=\"" + K.escapeHtml(url) + "\" target=\"" + K.TREE_WINDOW + "\">여기를 눌러 관계 트리 창을 여세요</a>";
		}
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





	return { init: init };
})();
