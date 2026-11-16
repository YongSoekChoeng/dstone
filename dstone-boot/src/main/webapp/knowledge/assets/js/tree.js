/**
 * 관계 트리 창(knowledge/graph/tree). 노드 하나에 이어진 길을 메소드 단위로 그립니다.
 * 가운데가 고른 것, 왼쪽이 부르는 쪽, 오른쪽이 불리는 쪽입니다.
 *
 * 노드 맵 · 영향도 · 미사용 탭이 이 창을 띄웁니다(knowledge-common.js 의 openTreeWindow).
 * 무엇을 그릴지는 주소의 #p=프로젝트&r=리비전&tree=노드ID 로 받습니다. 창이 하나라서 다른 화면이 새로 열면
 * 주소의 # 뒤만 바뀌므로, hashchange 를 받아 다시 그립니다. 창 안에서 "가운데로 다시 그리기"를 해도 주소를 바꾸므로
 * 브라우저의 뒤로 가기로 앞의 트리에 돌아갈 수 있습니다.
 */
var DstoneKnowledgeTree = (function () {

	var K = DstoneKnowledge;
	var G = DstoneKnowledgeGraphCommon;
	var el = {};

	var TIER_BY_KEY = G.TIER_BY_KEY;
	var EDGE_CATS = G.EDGE_CATS;
	var CAT_OF_RELATION = G.CAT_OF_RELATION;
	var KIND_LABEL = G.KIND_LABEL;
	var tierOf = G.tierOf;
	var labelWidth = G.labelWidth;
	var styles = G.styles;
	var highlight = G.highlight;
	var clearDetail = G.clearDetail;
	var head = G.head;
	var detailBody = G.detailBody;
	var functionName = G.functionName;

	/** 끈 계층을 기억해 두는 곳(브라우저 localStorage). 창을 닫았다 열어도 그대로 쓴다 */
	var HIDDEN_KEY = "dstone.knowledge.tree.hidden";

	/**
	 * 지금 그린 트리. hidden 은 끈 계층이고 화면호출관계는 처음에 꺼 둔다.
	 * loadSeq 는 불러오는 중에 다른 노드를 고르면 늦게 온 앞의 응답을 버리려고 센다.
	 */
	var tree = { cy: null, projectId: null, revisionId: null, startId: null, nodes: {}, result: null, hidden: loadHidden(), loadSeq: 0 };

	/** 관계 트리에서 끄고 켤 수 있는 계층. 모델(VO)은 "모델(VO) 호출도"로 서버에서 가려 받는다 */
	var TREE_TIERS = ["SCREEN_LINK", "VIEW", "CONTROLLER", "SERVICE", "REPOSITORY", "MAPPER", "TABLE", "ETC"];

	function init() {
		["kn-tree-title", "kn-tree-where", "kn-tree-up", "kn-tree-down", "kn-tree-model", "kn-tree-reload", "kn-tree-full", "kn-tree-close",
			"kn-tree-message", "kn-tree-tiers", "kn-tree-canvas", "kn-tree-detail"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		G.initCodePopup();

		el["kn-tree-reload"].addEventListener("click", function () {
			loadTree(tree.startId);
		});
		el["kn-tree-close"].addEventListener("click", function () {
			window.close();
		});
		el["kn-tree-full"].addEventListener("click", toggleFullscreen);
		document.addEventListener("fullscreenchange", function () {
			el["kn-tree-full"].textContent = document.fullscreenElement ? "전체 화면 끝내기" : "전체 화면";
			refit();
		});
		// 창 크기를 바꾸면 그림을 새 크기에 맞춘다(Cytoscape 는 칸 크기를 따로 알려 줘야 한다).
		window.addEventListener("resize", refit);
		document.addEventListener("keydown", function (event) {
			// Esc 는 소스 팝업만 닫는다. 창은 [닫기]로 닫는다(실수로 닫히지 않게).
			if (event.key === "Escape" && G.isCodeOpen()) {
				G.closeCode();
			}
		});
		window.addEventListener("hashchange", fromHash);
		fromHash();
	}

	/** 주소의 #p=..&r=..&tree=.. 대로 그린다 */
	function fromHash() {
		var wanted = K.readHash();
		if (!wanted.r || !wanted.tree) {
			K.message(el["kn-tree-message"], "무엇을 그릴지 모릅니다. 노드 맵 · 영향도 · 미사용 탭에서 관계 트리를 열어 주세요.", true);
			return;
		}
		tree.projectId = wanted.p || "";
		tree.revisionId = wanted.r;
		el["kn-tree-where"].textContent = (tree.projectId ? "프로젝트 " + tree.projectId + " · " : "") + "리비전 " + tree.revisionId;
		loadTree(wanted.tree);
	}

	/** 다른 노드를 가운데로 다시 그린다. 주소를 바꿔서(hashchange → fromHash) 뒤로 가기로 돌아올 수 있게 한다 */
	function recenter(nodeId) {
		var hash = "p=" + encodeURIComponent(tree.projectId) + "&r=" + encodeURIComponent(tree.revisionId) + "&tree=" + encodeURIComponent(nodeId);
		if (location.hash.replace(/^#/, "") === hash) {
			loadTree(nodeId);
			return;
		}
		location.hash = hash;
	}

	/* 전체 화면: 이미 따로 뜬 창이므로 브라우저의 전체 화면(Fullscreen API)을 쓴다. Esc 로도 끝난다 */

	function toggleFullscreen() {
		if (document.fullscreenElement) {
			document.exitFullscreen();
		} else if (document.documentElement.requestFullscreen) {
			document.documentElement.requestFullscreen();
		}
	}

	function refit() {
		window.requestAnimationFrame(function () {
			if (tree.cy) {
				tree.cy.resize();
				tree.cy.fit(undefined, 30);
			}
		});
	}

	function loadHidden() {
		try {
			var saved = window.localStorage.getItem(HIDDEN_KEY);
			if (saved) {
				return JSON.parse(saved);
			}
		} catch (e) {
			// 저장소를 못 쓰는 브라우저면 기본값으로 둔다.
		}
		return { SCREEN_LINK: true };
	}

	function saveHidden() {
		try {
			window.localStorage.setItem(HIDDEN_KEY, JSON.stringify(tree.hidden));
		} catch (e) {
			// 저장하지 못해도 이 창 안에서는 그대로 쓴다.
		}
	}

	function loadTree(nodeId) {
		tree.startId = nodeId;
		var seq = ++tree.loadSeq;
		K.message(el["kn-tree-message"], "불러오는 중...");
		clearDetail(el["kn-tree-detail"], "노드를 누르면 여기에 설명이 나옵니다.");
		K.call("GET", "/api/revisions/" + tree.revisionId + "/graph/neighborhood", {
			id: nodeId, up: el["kn-tree-up"].value, down: el["kn-tree-down"].value, includeModel: el["kn-tree-model"].checked,
			// 화면호출관계는 늘 받아 두고 칩으로만 끄고 켠다. 칩을 켤 때 다시 부르지 않아도 되고, 꺼 둔 채로도 몇 개인지 보인다.
			includeScreenLinks: true
		}).then(function (result) {
			if (seq !== tree.loadSeq) {
				// 그사이에 다른 노드를 골랐다.
				return;
			}
			el["kn-tree-title"].textContent = (KIND_LABEL[result.start.kind] || result.start.kind) + " " + result.start.fullName;
			document.title = "관계 트리 · " + (result.start.name || result.start.fullName);
			result.nodes.forEach(function (node) {
				node.tier = node.screenLink ? "SCREEN_LINK" : tierOf(node.layer);
				node.label = treeLabel(node);
			});
			tree.result = result;
			renderTreeTiers();
			drawTree();
		}).catch(function (err) {
			if (seq === tree.loadSeq) {
				K.message(el["kn-tree-message"], err.message, true);
			}
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
				saveHidden();
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
			if (edge.type === "INCLUDES" && tree.hidden.SCREEN_LINK) {
				return;
			}
			(outgoing[edge.from] = outgoing[edge.from] || []).push(edge);
		});
		var edges = [];
		var seen = {};
		result.edges.forEach(function (edge) {
			var from = tree.nodes[edge.from];
			var to = tree.nodes[edge.to];
			// 화면끼리 부르는 선(INCLUDES)은 화면호출관계를 켰을 때만 그린다.
			if (edge.type === "INCLUDES" && tree.hidden.SCREEN_LINK) {
				return;
			}
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
		K.call("GET", "/api/revisions/" + tree.revisionId + "/graph/node", { id: node.id }).then(function (detail) {
			if (container.getAttribute("data-node") !== node.id) {
				return;
			}
			container.innerHTML = head(detail.kind, node.label, node.tier)
				+ functionName(detail)
				+ (node.id === tree.startId ? "" : "<div class=\"kn-gd-actions\"><button type=\"button\" class=\"kn-btn kn-plain\" data-tree=\"" + K.escapeHtml(node.id)
					+ "\">이 노드를 가운데로 다시 그리기</button></div>")
				+ detailBody(detail);
			G.bindTreeLinks(container, recenter);
			G.bindCodeButton(container, detail);
		}).catch(function (err) {
			container.innerHTML = head(node.kind, node.label, node.tier) + "<p class=\"kn-message kn-error\">" + K.escapeHtml(err.message) + "</p>";
		});
	}

	return { init: init };
})();
