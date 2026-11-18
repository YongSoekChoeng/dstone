/*
 * AI 화면 목록 표의 칸 너비를 고정하고, 마우스로 끌어서 바꿀 수 있게 한다.
 *
 * - 표가 처음 보일 때 지금 칸 너비를 재서 그대로 굳힌다(table-layout: fixed).
 *   그 뒤로는 내용이 바뀌어도(자동 갱신, 다른 실행 선택) 칸이 들썩이지 않는다.
 * - 머리 칸 오른쪽 경계를 끌면 그 칸만 넓어지거나 좁아진다. 다른 칸은 그대로다.
 *   표가 화면보다 넓어지면 표만 가로로 밀리고, 화면보다 좁아지면 '남는 칸'(가장 넓은 칸)이 빈자리를 채운다.
 * - 경계를 두 번 누르면 처음 너비로 돌아간다. 경계에 초점을 두고 ←/→ 키로도 바꿀 수 있다.
 * - 바꾼 너비는 이 브라우저에만 기억한다(화면 주소 + 표 이름 기준). 저장이 막혀 있으면 기억하지 않을 뿐 동작은 같다.
 *
 * 대상은 아래 SELECTOR의 표다. 나중에 그려지는 표(Tool 고르기 창 등)도 저절로 붙는다.
 * 입력 양식 표(ai-form)는 목록이 아니라서 대상이 아니다.
 * common/head.jsp가 모든 AI 화면에 읽어 둔다.
 */
var DstoneAiTable = (function () {

	var SELECTOR = "table.document-list-table, table.wd-guide-table, table.tp-table";
	// 칸을 이보다 좁게는 줄이지 않는다
	var MIN_WIDTH = 40;
	// ←/→ 키 한 번에 바뀌는 너비
	var KEY_STEP = 16;
	var STORE_PREFIX = "dstone.ai.colwidth.";

	function init() {
		scan(document);
		// 스크립트가 나중에 그리는 표도 붙인다. 표가 들어온 변화만 살핀다.
		if (window.MutationObserver) {
			new MutationObserver(function (mutations) {
				for (var i = 0; i < mutations.length; i++) {
					var added = mutations[i].addedNodes;
					for (var j = 0; j < added.length; j++) {
						var node = added[j];
						if (node.nodeType !== 1) {
							continue;
						}
						if (node.matches(SELECTOR)) {
							setup(node);
						} else if (node.querySelector(SELECTOR)) {
							scan(node);
						}
					}
				}
			}).observe(document.body, { childList: true, subtree: true });
		}
		// 좁아서 잘린 칸은 마우스를 올리면 전체 글자를 보여 준다
		document.addEventListener("mouseover", showClippedTitle);
	}

	function scan(root) {
		var tables = root.querySelectorAll(SELECTOR);
		for (var i = 0; i < tables.length; i++) {
			setup(tables[i]);
		}
	}

	function headerCells(table) {
		var row = table.tHead && table.tHead.rows[0];
		return row ? row.cells : [];
	}

	function setup(table) {
		if (table.getAttribute("data-col-resize")) {
			return;
		}
		var cells = headerCells(table);
		if (cells.length < 2) {
			return;
		}
		for (var i = 0; i < cells.length; i++) {
			if (cells[i].colSpan > 1) {
				return;
			}
		}
		table.setAttribute("data-col-resize", "wait");
		wrap(table);
		// 숨겨진 표(닫힌 상세 패널, 접힌 안내)는 너비가 0이라 잴 수 없다. 보이는 순간까지 기다린다.
		whenVisible(table, function () {
			fix(table);
		});
	}

	// 표를 가로 스크롤 상자에 넣는다. 이미 스크롤되는 상자 안이면(.wa-table-scroll, Tool 고르기 목록) 그대로 둔다.
	function wrap(table) {
		var parent = table.parentNode;
		var overflowX = window.getComputedStyle(parent).overflowX;
		if (overflowX === "auto" || overflowX === "scroll") {
			return;
		}
		var box = document.createElement("div");
		box.className = "ai-col-scroll";
		parent.insertBefore(box, table);
		box.appendChild(table);
	}

	function whenVisible(table, callback) {
		if (table.offsetWidth > 0) {
			callback();
			return;
		}
		if (!window.ResizeObserver) {
			return;
		}
		var observer = new ResizeObserver(function () {
			if (table.offsetWidth > 0) {
				observer.disconnect();
				callback();
			}
		});
		observer.observe(table);
	}

	/*
	 * 지금 너비를 재서 굳힌다. 상태는 표 요소에 붙여 둔다.
	 * base: 사람이 정한(또는 처음 잰) 칸 너비. 화면에 보이는 너비는 여기에 빈자리 채움을 더한 것이다.
	 */
	function fix(table) {
		var cells = headerCells(table);
		var measured = measure(table);
		var state = {
			key: storeKey(table),
			natural: measured.widths,
			base: measured.widths.slice(),
			chrome: measured.chrome,
			fill: widestIndex(measured.widths),
			cols: ensureCols(table, cells.length)
		};
		var saved = load(state.key);
		if (saved && saved.length === cells.length) {
			state.base = saved;
		}
		table._colState = state;
		table.setAttribute("data-col-resize", "on");
		table.classList.add("ai-col-fixed");
		// 화면별 CSS의 최소 너비(.wa-table 등)와 내용 맞춤(table-layout: auto)보다 앞서도록 표에 직접 적는다
		table.style.tableLayout = "fixed";
		table.style.minWidth = "0";
		for (var i = 0; i < cells.length; i++) {
			cells[i].appendChild(createGrip(table, i));
		}
		apply(table, -1);
		// 화면 너비가 바뀌면 빈자리 채움을 다시 계산한다.
		// 가로 스크롤 막대가 생기고 없어질 때는 높이만 바뀌므로 넘어간다. 끄는 동안에도 넘어간다(끄는 쪽이 계산한다).
		if (window.ResizeObserver) {
			var lastWidth = table.parentNode.clientWidth;
			new ResizeObserver(function () {
				var width = table.parentNode.clientWidth;
				if (width === lastWidth || table.classList.contains("ai-col-dragging")) {
					return;
				}
				lastWidth = width;
				apply(table, -1);
			}).observe(table.parentNode);
		}
	}

	// 머리 칸 너비와, 칸 말고 표가 차지하는 너비(테두리)를 잰다
	function measure(table) {
		var cells = headerCells(table);
		var widths = [];
		var sum = 0;
		for (var i = 0; i < cells.length; i++) {
			var width = Math.max(Math.round(cells[i].getBoundingClientRect().width), MIN_WIDTH);
			widths.push(width);
			sum += width;
		}
		return { widths: widths, chrome: Math.max(Math.round(table.getBoundingClientRect().width) - sum, 0) };
	}

	function widestIndex(widths) {
		var index = 0;
		for (var i = 1; i < widths.length; i++) {
			if (widths[i] > widths[index]) {
				index = i;
			}
		}
		return index;
	}

	// 칸 수만큼 col을 맞춘다. 원래 colgroup이 있으면 그대로 쓰고(너비는 덮어쓴다), 모자라면 새로 만든다.
	function ensureCols(table, count) {
		var group = table.querySelector("colgroup");
		if (!group || group.children.length !== count) {
			if (group) {
				group.parentNode.removeChild(group);
			}
			group = document.createElement("colgroup");
			for (var i = 0; i < count; i++) {
				group.appendChild(document.createElement("col"));
			}
			table.insertBefore(group, table.firstChild);
		}
		return group.children;
	}

	/*
	 * base 너비를 화면에 반영한다. 칸의 합이 상자보다 좁으면 빈자리를 '남는 칸'에 더한다.
	 * dragging: 지금 끌고 있는 칸. 그 칸이 '남는 칸'이면 빈자리는 옆 칸이 받는다(안 그러면 줄여도 줄지 않는다).
	 */
	function apply(table, dragging) {
		var state = table._colState;
		var widths = state.base.slice();
		var sum = 0;
		for (var i = 0; i < widths.length; i++) {
			sum += widths[i];
		}
		var room = table.parentNode.clientWidth - state.chrome - sum;
		if (room > 0) {
			var target = state.fill;
			if (target === dragging) {
				target = dragging < widths.length - 1 ? dragging + 1 : dragging - 1;
			}
			widths[target] += room;
			sum += room;
		}
		for (var j = 0; j < widths.length; j++) {
			state.cols[j].style.width = widths[j] + "px";
		}
		table.style.width = (sum + state.chrome) + "px";
		state.shown = widths;
	}

	function createGrip(table, index) {
		var grip = document.createElement("span");
		grip.className = "ai-col-grip";
		grip.setAttribute("role", "separator");
		grip.setAttribute("aria-orientation", "vertical");
		grip.setAttribute("aria-label", "칸 너비 조절");
		grip.setAttribute("tabindex", "0");
		grip.title = "끌어서 칸 너비를 바꿉니다. 두 번 누르면 처음 너비로 돌아갑니다.";
		// 마지막으로 누른 동안 실제로 끌었는지
		var moved = false;

		grip.addEventListener("pointerdown", function (event) {
			if (event.button !== 0) {
				return;
			}
			event.preventDefault();
			var state = table._colState;
			// 지금 보이는 너비에서 시작한다(빈자리 채움 포함)
			state.base = state.shown.slice();
			var startX = event.clientX;
			var startWidth = state.base[index];
			moved = false;
			grip.setPointerCapture(event.pointerId);
			table.classList.add("ai-col-dragging");
			document.body.classList.add("ai-col-dragging-body");

			function move(e) {
				if (Math.abs(e.clientX - startX) > 2) {
					moved = true;
				}
				state.base[index] = Math.max(MIN_WIDTH, startWidth + Math.round(e.clientX - startX));
				apply(table, index);
			}
			function end() {
				grip.removeEventListener("pointermove", move);
				grip.removeEventListener("pointerup", end);
				grip.removeEventListener("pointercancel", end);
				table.classList.remove("ai-col-dragging");
				document.body.classList.remove("ai-col-dragging-body");
				// 보이는 너비를 그대로 기준으로 삼는다. 그래야 다음 계산에서 칸이 튀지 않는다.
				state.base = state.shown.slice();
				save(state.key, state.base);
			}
			grip.addEventListener("pointermove", move);
			grip.addEventListener("pointerup", end);
			grip.addEventListener("pointercancel", end);
		});

		grip.addEventListener("dblclick", function (event) {
			event.preventDefault();
			// 끌고 놓은 것을 연달아 두 번 하면 브라우저가 두 번 누른 것으로 친다. 그때는 되돌리지 않는다.
			if (moved) {
				return;
			}
			reset(table);
		});

		grip.addEventListener("keydown", function (event) {
			var step = event.key === "ArrowRight" ? KEY_STEP : (event.key === "ArrowLeft" ? -KEY_STEP : 0);
			if (step === 0) {
				return;
			}
			event.preventDefault();
			var state = table._colState;
			state.base = state.shown.slice();
			state.base[index] = Math.max(MIN_WIDTH, state.base[index] + step);
			apply(table, index);
			state.base = state.shown.slice();
			save(state.key, state.base);
		});

		// 경계를 눌러도 머리 칸이 눌린 것으로 치지 않는다(체크박스 머리 칸 등)
		grip.addEventListener("click", function (event) {
			event.stopPropagation();
		});
		return grip;
	}

	// 처음 잰 너비로 되돌리고 기억도 지운다
	function reset(table) {
		var state = table._colState;
		state.base = state.natural.slice();
		remove(state.key);
		apply(table, -1);
	}

	// 칸이 좁아 글자가 잘렸으면 마우스를 올렸을 때 전체를 보여 준다. 원래 title이 있는 칸은 건드리지 않는다.
	function showClippedTitle(event) {
		var cell = event.target.closest ? event.target.closest("table.ai-col-fixed td, table.ai-col-fixed th") : null;
		if (!cell || (cell.title && !cell.hasAttribute("data-clip-title"))) {
			return;
		}
		if (cell.scrollWidth > cell.clientWidth + 1) {
			cell.title = cell.textContent.replace(/\s+/g, " ").trim();
			cell.setAttribute("data-clip-title", "1");
		} else if (cell.hasAttribute("data-clip-title")) {
			cell.removeAttribute("title");
			cell.removeAttribute("data-clip-title");
		}
	}

	// 화면 주소 + 표 id(없으면 머리 칸 글자). Tool 고르기 창처럼 같은 모양의 표가 여럿이면 너비를 같이 쓴다.
	function storeKey(table) {
		var name = table.id;
		if (!name) {
			var texts = [];
			var cells = headerCells(table);
			for (var i = 0; i < cells.length; i++) {
				texts.push(cells[i].textContent.trim());
			}
			name = texts.join("|");
		}
		return STORE_PREFIX + location.pathname + location.search + "#" + name;
	}

	function load(key) {
		try {
			var value = window.localStorage.getItem(key);
			var widths = value ? JSON.parse(value) : null;
			if (!widths || !widths.length) {
				return null;
			}
			for (var i = 0; i < widths.length; i++) {
				if (typeof widths[i] !== "number" || widths[i] < MIN_WIDTH) {
					return null;
				}
			}
			return widths;
		} catch (e) {
			return null;
		}
	}

	function save(key, widths) {
		try {
			window.localStorage.setItem(key, JSON.stringify(widths));
		} catch (e) {
			// 저장이 막힌 브라우저면 기억하지 않을 뿐이다
		}
	}

	function remove(key) {
		try {
			window.localStorage.removeItem(key);
		} catch (e) {
			// 위와 같다
		}
	}

	if (document.readyState === "loading") {
		document.addEventListener("DOMContentLoaded", init);
	} else {
		init();
	}

	return {
		reset: reset
	};
})();
