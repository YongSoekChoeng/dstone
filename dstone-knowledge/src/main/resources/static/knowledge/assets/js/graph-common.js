/**
 * "노드 맵"과 "관계 트리" 창이 같이 쓰는 것들입니다.
 *
 *   - 계층 · 선 · 노드 종류의 이름과 색
 *   - Cytoscape 그림 모양(styles)과 노드를 눌렀을 때의 강조(highlight)
 *   - 오른쪽 상세 칸(head, detailBody, functionName)
 *   - 소스 / SQL 팝업(화면에 #kn-code-modal 이 있어야 합니다. graph/codeModal.jsp 를 끼워 넣습니다)
 */
var DstoneKnowledgeGraphCommon = (function () {

	var K = DstoneKnowledge;

	/** 소스 / SQL 팝업의 요소들. initCodePopup() 에서 채운다 */
	var el = {};

	/**
	 * 계층. 이 순서대로 왼쪽에서 오른쪽으로 놓습니다(호출이 흐르는 방향).
	 * 색은 대시보드의 흐름 띠(knowledge.css 의 --kn-layer-*)와 같은 값입니다. 바꾸면 거기도 같이 바꿉니다.
	 * 화면(하늘색)과 컨트롤러가 나란히 놓여도 구분되도록 컨트롤러는 남색으로 둡니다.
	 */
	var TIERS = [
		{ key: "VIEW", label: "화면", color: "#0284c7", fill: "#e0f2fe" },
		{ key: "CONTROLLER", label: "컨트롤러", color: "#4338ca", fill: "#e0e7ff" },
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
	/** 관계 트리에만 있는 계층: 트리에 나온 화면을 부르거나 그 화면이 부르는(끼워 넣거나 띄우는) 다른 화면 */
	TIER_BY_KEY.SCREEN_LINK = { key: "SCREEN_LINK", label: "화면호출관계", color: "#0e7490", fill: "#cffafe", index: -1 };

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
		CALLS: "CALL", CALLS_POSSIBLE_IMPLEMENTATION: "CALL", ENCLOSES_ANONYMOUS: "CALL", EXECUTES_SQL: "SQL", READS_TABLE: "READ", WRITES_TABLE: "WRITE",
		RENDERS: "RENDER", REQUESTS: "REQUEST", EXTENDS: "STRUCT", IMPLEMENTS: "STRUCT", INJECTS: "STRUCT", INCLUDES: "STRUCT"
	};

	var KIND_LABEL = { TYPE: "클래스", METHOD: "메소드", FIELD: "필드", JSP: "화면", MAPPER: "SQL 매퍼", SQL: "SQL", TABLE: "테이블", GROUP: "묶음" };

	function tierOf(layer) {
		if (layer === "WEB_FILTER") {
			return "CONTROLLER";
		}
		return TIER_BY_KEY[layer] ? layer : "ETC";
	}

	/** 글자 수로 노드의 폭을 어림합니다. 한글은 영문보다 넓습니다 */
	function labelWidth(label) {
		var width = 18;
		for (var i = 0; i < label.length; i++) {
			width += label.charCodeAt(i) > 255 ? 12 : 6.6;
		}
		return Math.max(46, Math.min(Math.round(width), 190));
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

	function clearDetail(container, text) {
		container.removeAttribute("data-node");
		container.innerHTML = "<p class=\"ai-hint\">" + K.escapeHtml(text) + "</p>";
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
			// 기능명(title)은 메소드 위 주석의 첫 줄이나 @Description 값이다. 분석할 때(SEMANTIC) 저장해 둔 것을 서버가 준다.
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
			html += "<div class=\"kn-section-title\">설명 <span class=\"ai-hint\">(분석이 만들어 둔 문서. 검색에 쓰는 것과 같습니다)</span>" + codeButton(detail) + "</div>"
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

	/** 화면 노드의 [소스 보기] 버튼에 마우스를 올리면 나오는 안내 */
	var SCREEN_CODE_DISABLED = "화면 문서는 검색용으로 앞부분만 담아 두어서 전체 소스를 보여 줄 수 없습니다";

	/**
	 * 설명 제목 옆에 붙는 [소스 보기] / [SQL 보기] 버튼. 소스나 SQL 이 없으면 빈 글자.
	 * 화면(JSP·WebSquare·Nexacro)은 버튼을 꺼 둔다. 문서에 앞부분만 들어 있어서, 띄우면 전체 소스로 오해하기 쉽다.
	 */
	function codeButton(detail) {
		var code = detail.code;
		if (!code || !code.text) {
			return "";
		}
		var label = K.escapeHtml(CODE_LABEL[code.kind] || "소스") + " 보기";
		if (detail.kind === "JSP") {
			return "<button type=\"button\" class=\"kn-plain kn-gd-code-btn\" disabled title=\"" + K.escapeHtml(SCREEN_CODE_DISABLED) + "\">" + label + "</button>";
		}
		var lines = code.text.split("\n").length;
		return "<button type=\"button\" class=\"kn-plain kn-gd-code-btn\" data-code=\"1\" title=\"" + lines + "줄\">" + label + "</button>";
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

	/** 소스 / SQL 팝업의 단추를 잇습니다. 화면마다 처음에 한 번 부릅니다 */
	function initCodePopup() {
		["kn-code-modal", "kn-code-title", "kn-code-where", "kn-code-text", "kn-code-raw", "kn-code-copy", "kn-code-close"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		el["kn-code-close"].addEventListener("click", closeCode);
		el["kn-code-copy"].addEventListener("click", copyCode);
		el["kn-code-raw"].addEventListener("click", toggleRawCode);
		el["kn-code-modal"].addEventListener("click", function (event) {
			// 팝업 바깥(어두운 바탕)을 누르면 닫는다.
			if (event.target === el["kn-code-modal"]) {
				closeCode();
			}
		});
	}

	function isCodeOpen() {
		return el["kn-code-modal"].style.display !== "none";
	}

	/** 상세 안의 "트리" 링크(메소드, SQL statement, 가운데로 다시 그리기)를 누르면 onTree(노드ID)를 부릅니다 */
	function bindTreeLinks(container, onTree) {
		Array.prototype.forEach.call(container.querySelectorAll("[data-tree]"), function (link) {
			link.addEventListener("click", function () {
				onTree(link.getAttribute("data-tree"));
			});
		});
	}

	return {
		TIERS: TIERS,
		TIER_BY_KEY: TIER_BY_KEY,
		EDGE_CATS: EDGE_CATS,
		CAT_OF_RELATION: CAT_OF_RELATION,
		KIND_LABEL: KIND_LABEL,
		tierOf: tierOf,
		labelWidth: labelWidth,
		styles: styles,
		highlight: highlight,
		clearDetail: clearDetail,
		head: head,
		detailBody: detailBody,
		functionName: functionName,
		bindTreeLinks: bindTreeLinks,
		initCodePopup: initCodePopup,
		bindCodeButton: bindCodeButton,
		isCodeOpen: isCodeOpen,
		closeCode: closeCode
	};
})();
