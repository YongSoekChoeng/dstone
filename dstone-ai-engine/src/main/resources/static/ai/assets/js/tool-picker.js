/**
 * "Tool 찾기" 창. Tool 을 종류별(소스 RAG, 문서 RAG, MCP, 일반) 표로 보여 주고 골라 돌려준다.
 * 채팅 화면(chat.js)과 Workflow 디자이너의 Agent 창(workflow-designer.js)이 같이 쓴다.
 *
 *   DstoneToolPicker.open({
 *       title: "Tool 찾기",
 *       tools: [{ name, description, category, disabled, note }],   // category: CODE_RAG / DOC_RAG / MCP / GENERAL
 *       chosen: ["knowledgeSearchDocs"],                              // 지금 고른 이름
 *       onDone: function (names) { ... }                              // [고르기]를 누르면 고른 이름 목록
 *   });
 *
 * 창 안에서 고친 것은 [고르기]를 눌러야 반영된다. [취소], [닫기], Esc 는 고친 것을 버린다.
 * 종류는 dstone-ai-engine 이 Tool 마다 알려 준다(@AiTool 의 category, MCP 서버 Tool 은 MCP).
 * 엔진에 없는 이름(Agent 파일에만 적힌 Tool)은 "등록되지 않음" 표에 따로 모은다.
 */
var DstoneToolPicker = (function () {

	/** 표 순서와 이름. 엔진이 모르는 종류를 보내면 "일반"으로 본다 */
	var CATEGORIES = [
		{ code: "CODE_RAG", label: "소스 RAG", hint: "분석해 둔 소스 코드(메소드, SQL, 테이블, 화면)를 찾습니다. 질문에 프로젝트 ID 를 같이 적습니다." },
		{ code: "DOC_RAG", label: "문서 RAG", hint: "올린 문서(개발 가이드, 설계서 등)를 묶음이나 문서 단위로 찾고, 앞뒤 조각을 이어 읽습니다." },
		{ code: "MCP", label: "MCP", hint: "MCP 서버에서 가져온 Tool 입니다." },
		{ code: "GENERAL", label: "일반", hint: "파일, DB, HTTP, 셸, 날짜 같은 그 밖의 Tool 입니다." },
		{ code: "UNKNOWN", label: "등록되지 않음", hint: "Agent 파일에는 적혀 있지만 엔진에 없는 Tool 입니다. 빼 두는 것이 좋습니다." }
	];

	var els = null;
	var state = null; // { tools, chosen, onDone, category }

	function escapeHtml(value) {
		return String(value === undefined || value === null ? "" : value)
			.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
	}

	/** 종류 코드 → 화면 이름 */
	function labelOf(code) {
		for (var i = 0; i < CATEGORIES.length; i++) {
			if (CATEGORIES[i].code === code) {
				return CATEGORIES[i].label;
			}
		}
		return "일반";
	}

	/** 표에 넣을 종류. 모르는 값은 일반으로 */
	function categoryOf(tool) {
		for (var i = 0; i < CATEGORIES.length; i++) {
			if (CATEGORIES[i].code === tool.category) {
				return tool.category;
			}
		}
		return "GENERAL";
	}

	/** 창을 처음 열 때 한 번만 만든다 */
	function build() {
		if (els) {
			return;
		}
		var dialog = document.createElement("div");
		dialog.className = "wd-dialog wd-pick-dialog tp-dialog";
		dialog.style.display = "none";
		dialog.innerHTML = ""
			+ "<div class=\"wd-dialog-box wd-pick-box tp-box\" role=\"dialog\" aria-modal=\"true\">"
			+ "<div class=\"wd-dialog-head\"><h3 class=\"tp-title\"></h3><button type=\"button\" class=\"kn-plain tp-close\">닫기</button></div>"
			+ "<div class=\"wd-pick-bar\">"
			+ "<input type=\"search\" class=\"tp-search\" placeholder=\"이름이나 설명으로 찾기\" />"
			+ "<label class=\"wd-check\"><input type=\"checkbox\" class=\"tp-only-chosen\" /> 고른 것만</label>"
			+ "<span class=\"ai-hint tp-count\"></span>"
			+ "</div>"
			+ "<div class=\"tp-tabs\" role=\"tablist\"></div>"
			+ "<div class=\"tp-list\"></div>"
			+ "<div class=\"tp-foot\">"
			+ "<button type=\"button\" class=\"kn-plain tp-clear\">모두 빼기</button>"
			+ "<span class=\"tp-foot-gap\"></span>"
			+ "<button type=\"button\" class=\"kn-plain tp-cancel\">취소</button>"
			+ "<button type=\"button\" class=\"tp-ok\">고르기</button>"
			+ "</div>"
			+ "</div>";
		document.body.appendChild(dialog);
		els = {
			dialog: dialog,
			title: dialog.querySelector(".tp-title"),
			search: dialog.querySelector(".tp-search"),
			onlyChosen: dialog.querySelector(".tp-only-chosen"),
			count: dialog.querySelector(".tp-count"),
			tabs: dialog.querySelector(".tp-tabs"),
			list: dialog.querySelector(".tp-list")
		};
		els.search.addEventListener("input", render);
		els.onlyChosen.addEventListener("change", render);
		dialog.querySelector(".tp-close").addEventListener("click", close);
		dialog.querySelector(".tp-cancel").addEventListener("click", close);
		dialog.querySelector(".tp-clear").addEventListener("click", function () {
			state.chosen = [];
			render();
		});
		dialog.querySelector(".tp-ok").addEventListener("click", function () {
			var done = state.onDone;
			var chosen = state.chosen.slice();
			close();
			if (done) {
				done(chosen);
			}
		});
		dialog.addEventListener("keydown", function (e) {
			if (e.key === "Escape") {
				// 이 창 밑의 창(디자이너의 Agent 창)까지 닫히지 않게 여기서 멈춘다.
				e.stopPropagation();
				close();
			}
		});
		// 바깥(어두운 곳)을 누르면 닫는다.
		dialog.addEventListener("mousedown", function (e) {
			if (e.target === dialog) {
				close();
			}
		});
		els.tabs.addEventListener("click", function (e) {
			var tab = e.target.closest("[data-tp-tab]");
			if (tab) {
				state.category = tab.getAttribute("data-tp-tab");
				render();
			}
		});
		els.list.addEventListener("change", function (e) {
			var box = e.target;
			if (box.hasAttribute("data-tp-name")) {
				toggle(box.getAttribute("data-tp-name"), box.checked);
				render();
			} else if (box.hasAttribute("data-tp-all")) {
				// 이 종류에서 지금 보이는 것을 모두 고르거나 뺀다.
				Array.prototype.forEach.call(els.list.querySelectorAll("input[data-tp-name][data-tp-cat=\"" + box.getAttribute("data-tp-all") + "\"]:not(:disabled)"), function (one) {
					toggle(one.getAttribute("data-tp-name"), box.checked);
				});
				render();
			}
		});
	}

	function toggle(name, on) {
		var at = state.chosen.indexOf(name);
		if (on && at < 0) {
			state.chosen.push(name);
		} else if (!on && at >= 0) {
			state.chosen.splice(at, 1);
		}
	}

	function open(options) {
		build();
		state = {
			tools: (options.tools || []).slice(),
			chosen: (options.chosen || []).slice(),
			onDone: options.onDone,
			category: ""
		};
		els.title.textContent = options.title || "Tool 찾기";
		els.search.value = "";
		els.onlyChosen.checked = false;
		render();
		els.dialog.style.display = "flex";
		els.search.focus();
	}

	function close() {
		if (els) {
			els.dialog.style.display = "none";
		}
		state = null;
	}

	/** 검색어(여럿이면 모두 들어 있는 것)와 "고른 것만"에 맞는지 */
	function matches(tool, words, onlyChosen) {
		if (onlyChosen && state.chosen.indexOf(tool.name) < 0) {
			return false;
		}
		var text = (tool.name + " " + (tool.description || "") + " " + labelOf(categoryOf(tool))).toLowerCase();
		for (var i = 0; i < words.length; i++) {
			if (text.indexOf(words[i]) < 0) {
				return false;
			}
		}
		return true;
	}

	function render() {
		if (!state) {
			// 검색 칸에서 Esc 를 누르면 창이 닫힌 뒤에 칸 비우기(input)가 한 번 더 온다.
			return;
		}
		var words = els.search.value.trim().toLowerCase().split(/\s+/).filter(function (w) {
			return w !== "";
		});
		var onlyChosen = els.onlyChosen.checked;

		// 종류별로 모은다(이름 순).
		var groups = {};
		CATEGORIES.forEach(function (c) {
			groups[c.code] = { all: [], shown: [] };
		});
		state.tools.slice().sort(function (a, b) {
			return a.name < b.name ? -1 : (a.name > b.name ? 1 : 0);
		}).forEach(function (tool) {
			var group = groups[categoryOf(tool)];
			group.all.push(tool);
			if (matches(tool, words, onlyChosen)) {
				group.shown.push(tool);
			}
		});

		// 탭: 전체 + Tool 이 있는 종류만. 고른 수를 같이 보여 준다.
		var tabs = [{ code: "", label: "전체", tools: state.tools }];
		CATEGORIES.forEach(function (c) {
			if (groups[c.code].all.length > 0) {
				tabs.push({ code: c.code, label: c.label, tools: groups[c.code].all });
			}
		});
		if (state.category && groups[state.category] && groups[state.category].all.length === 0) {
			state.category = "";
		}
		els.tabs.innerHTML = tabs.map(function (tab) {
			var picked = tab.tools.filter(function (tool) {
				return state.chosen.indexOf(tool.name) >= 0;
			}).length;
			return "<button type=\"button\" role=\"tab\" class=\"tp-tab" + (state.category === tab.code ? " tp-tab-on" : "") + "\" data-tp-tab=\"" + tab.code + "\""
				+ " aria-selected=\"" + (state.category === tab.code) + "\">" + escapeHtml(tab.label)
				+ " <span class=\"tp-tab-num\">" + (picked > 0 ? picked + "/" : "") + tab.tools.length + "</span></button>";
		}).join("");

		// 종류마다 표 하나
		var html = "";
		var shownTotal = 0;
		CATEGORIES.forEach(function (c) {
			if (state.category && state.category !== c.code) {
				return;
			}
			var group = groups[c.code];
			if (group.shown.length === 0) {
				return;
			}
			shownTotal += group.shown.length;
			var selectable = group.shown.filter(function (tool) {
				return !tool.disabled || state.chosen.indexOf(tool.name) >= 0;
			});
			var allOn = selectable.length > 0 && selectable.every(function (tool) {
				return state.chosen.indexOf(tool.name) >= 0;
			});
			html += "<section class=\"tp-group\">"
				+ "<h4 class=\"tp-group-title\">" + escapeHtml(c.label) + " <span class=\"tp-group-num\">" + group.shown.length + "개</span>"
				+ "<span class=\"tp-group-hint\">" + escapeHtml(c.hint) + "</span></h4>"
				+ "<table class=\"tp-table\"><colgroup><col class=\"tp-col-check\" /><col class=\"tp-col-name\" /><col /></colgroup>"
				+ "<thead><tr>"
				+ "<th><input type=\"checkbox\" data-tp-all=\"" + c.code + "\" title=\"이 종류에서 보이는 것을 모두 고르거나 뺍니다\"" + (allOn ? " checked" : "") + (selectable.length === 0 ? " disabled" : "") + " /></th>"
				+ "<th>Tool 이름</th><th>설명</th>"
				+ "</tr></thead><tbody>";
			group.shown.forEach(function (tool) {
				var on = state.chosen.indexOf(tool.name) >= 0;
				// 이미 고른 것은 지금 고를 수 없는 상태여도 뺄 수 있게 둔다.
				var off = tool.disabled && !on;
				html += "<tr class=\"" + (on ? "tp-row-on" : "") + (off ? " tp-row-off" : "") + "\">"
					+ "<td><label class=\"tp-hit\"><input type=\"checkbox\" data-tp-name=\"" + escapeHtml(tool.name) + "\" data-tp-cat=\"" + c.code + "\"" + (on ? " checked" : "") + (off ? " disabled" : "") + " /></label></td>"
					+ "<td class=\"tp-name\">" + escapeHtml(tool.name) + (tool.note ? " <em>" + escapeHtml(tool.note) + "</em>" : "") + "</td>"
					+ "<td><div class=\"tp-desc\" title=\"" + escapeHtml(tool.description || "") + "\">" + escapeHtml(tool.description || "") + "</div></td>"
					+ "</tr>";
			});
			html += "</tbody></table></section>";
		});
		if (shownTotal === 0) {
			html = "<p class=\"ai-hint wd-pick-empty\">" + (state.tools.length === 0 ? "고를 Tool 이 없습니다." : "찾는 Tool 이 없습니다.") + "</p>";
		}
		els.list.innerHTML = html;
		els.count.textContent = "전체 " + state.tools.length + "개 중 " + shownTotal + "개 보임 · " + state.chosen.length + "개 고름";
	}

	// 표의 칸 아무 곳이나 눌러도 그 줄의 체크박스를 바꾼다(체크박스·링크를 직접 누른 것은 그대로 둔다).
	document.addEventListener("click", function (e) {
		if (!els || !state || !els.list.contains(e.target)) {
			return;
		}
		if (e.target.closest("input, label, a, button")) {
			return;
		}
		var row = e.target.closest(".tp-table tbody tr");
		if (!row) {
			return;
		}
		var box = row.querySelector("input[data-tp-name]");
		if (box && !box.disabled) {
			box.checked = !box.checked;
			box.dispatchEvent(new Event("change", { bubbles: true }));
		}
	});

	return {
		open: open,
		close: close,
		labelOf: labelOf
	};
})();
