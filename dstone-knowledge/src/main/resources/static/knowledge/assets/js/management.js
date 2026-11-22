/**
 * 사용자 관리 화면(사용자, 사용자그룹, 메뉴, 권한)의 동작입니다. 화면 종류는 <body data-page="user|group|menu|auth">.
 * dstone-ai-engine, dstone-knowledge 가 같은 파일을 하나씩 갖고 있으니 고칠 때 둘 다 맞춥니다.
 * API 는 /console/management/{users|groups|menus|auths} (dstone-common ConsoleController, 관리자만).
 *
 * 화면 모양: [목록 표] 줄을 누르면 아래 [입력 칸]에 채워지고, [새로 만들기]로 비운다. [저장]은 만들기와 고치기를 함께 한다.
 */
(function () {
	var esc = DstoneConsole.escapeHtml;
	var page = document.body.getAttribute("data-page");
	var listBody = document.getElementById("mg-list-body");
	var listHead = document.getElementById("mg-list-head");
	var form = document.getElementById("mg-form");
	var status = document.getElementById("mg-status");
	var rows = [];
	var lookups = { groups: [], auths: [], menus: [] };
	var editing = null;

	/** 아이콘 이름(console.js 의 ICONS) */
	var ICONS = ["home", "chat", "play", "list", "flow", "dashboard", "folder", "search", "impact", "graph", "diff", "book", "user", "tool"];

	function api(method, url, body) {
		var init = { method: method, headers: { "Accept": "application/json" } };
		if (body !== undefined) {
			init.headers["Content-Type"] = "application/json";
			init.body = JSON.stringify(body);
		}
		return fetch(url, init).then(function (response) {
			return response.json().catch(function () {
				return null;
			}).then(function (data) {
				if (!response.ok) {
					throw new Error((data && data.message) || ("서버 오류(" + response.status + ")"));
				}
				return data;
			});
		});
	}

	function say(text, ok) {
		status.textContent = text || "";
		status.className = "workflow-status" + (text ? (ok ? " mg-ok" : " mg-error") : "");
	}

	function split(text) {
		return text ? String(text).split(",").filter(function (v) { return v; }) : [];
	}

	function yn(value) {
		return value === "Y" ? "예" : "아니오";
	}

	function time(value) {
		if (!value) {
			return "-";
		}
		var date = new Date(value);
		return isNaN(date.getTime()) ? String(value) : date.toLocaleString();
	}

	/** 메뉴를 트리 순서로 펼치고 깊이를 붙인다 */
	function menuTree(menus) {
		var out = [];
		function walk(parentId, depth) {
			menus.filter(function (m) {
				return (m.parentId || null) === parentId;
			}).sort(function (a, b) {
				return (a.sortOrder - b.sortOrder) || String(a.menuId).localeCompare(String(b.menuId));
			}).forEach(function (m) {
				out.push({ menu: m, depth: depth });
				walk(m.menuId, depth + 1);
			});
		}
		walk(null, 0);
		return out;
	}

	function checks(name, items, chosen) {
		if (!items.length) {
			return "<span class=\"ai-hint\">없음</span>";
		}
		return "<div class=\"mg-checks\">" + items.map(function (item) {
			return "<label class=\"wa-check\" style=\"margin-left:" + (item.depth || 0) * 18 + "px\"><input type=\"checkbox\" name=\"" + name + "\" value=\"" + esc(item.value) + "\""
				+ (chosen.indexOf(item.value) >= 0 ? " checked" : "") + " /> " + esc(item.label) + "</label>";
		}).join("") + "</div>";
	}

	function checked(name) {
		return Array.prototype.map.call(form.querySelectorAll("input[name=" + name + "]:checked"), function (input) {
			return input.value;
		});
	}

	function value(id) {
		var el = document.getElementById(id);
		return el ? el.value.trim() : "";
	}

	function field(label, html) {
		return "<tr><th>" + label + "</th><td>" + html + "</td></tr>";
	}

	function input(id, val, extra) {
		return "<input type=\"text\" id=\"" + id + "\" value=\"" + esc(val == null ? "" : val) + "\" " + (extra || "") + " />";
	}

	function select(id, options, val) {
		return "<select id=\"" + id + "\">" + options.map(function (o) {
			return "<option value=\"" + esc(o[0]) + "\"" + (String(o[0]) === String(val == null ? "" : val) ? " selected" : "") + ">" + esc(o[1]) + "</option>";
		}).join("") + "</select>";
	}

	/* ---------- 화면 종류별 정의 ---------- */

	var PAGES = {
		user: {
			url: "/console/management/users",
			key: "userId",
			head: ["아이디", "이름", "그룹", "사용", "상태", "마지막 로그인"],
			cells: function (r) {
				var state = Number(r.loginFailCount) >= 5 ? "잠김" : (r.pwChangeYn === "Y" ? "비밀번호 변경 필요" : "-");
				return [r.userId, r.userName, split(r.groupIds).join(", ") || "-", yn(r.useYn), state, time(r.lastLoginAt)];
			},
			form: function (r) {
				var isNew = !r;
				r = r || { useYn: "Y", groupIds: "" };
				return field("아이디", input("f-userId", r.userId, isNew ? "" : "readonly"))
					+ field("이름", input("f-userName", r.userName))
					+ field("사용", select("f-useYn", [["Y", "예"], ["N", "아니오(로그인 불가)"]], r.useYn))
					+ field("사용자그룹", checks("groupIds", lookups.groups.map(function (g) { return { value: g.groupId, label: g.groupName + " (" + g.groupId + ")" }; }), split(r.groupIds)))
					+ (isNew ? field("초기 비밀번호", "<input type=\"password\" id=\"f-initialPassword\" autocomplete=\"new-password\" /> <span class=\"ai-hint\">8자 이상, 영문과 숫자. 첫 로그인 때 바꾸게 합니다.</span>") : "");
			},
			body: function () {
				return { userId: value("f-userId"), userName: value("f-userName"), useYn: value("f-useYn"), groupIds: checked("groupIds"), initialPassword: (document.getElementById("f-initialPassword") || {}).value };
			},
			extra: function (r) {
				return r ? "<button type=\"button\" class=\"kn-plain\" id=\"mg-reset\">비밀번호 초기화</button>" : "";
			}
		},
		group: {
			url: "/console/management/groups",
			key: "groupId",
			head: ["그룹 ID", "이름", "설명", "순서", "사용자 수", "권한"],
			cells: function (r) {
				return [r.groupId, r.groupName, r.description || "", r.sortOrder, r.userCount, split(r.authIds).join(", ") || "-"];
			},
			form: function (r) {
				var isNew = !r;
				r = r || { sortOrder: 0, authIds: "" };
				return field("그룹 ID", input("f-groupId", r.groupId, isNew ? "" : "readonly"))
					+ field("이름", input("f-groupName", r.groupName))
					+ field("설명", input("f-description", r.description))
					+ field("순서", input("f-sortOrder", r.sortOrder))
					+ field("권한", checks("authIds", lookups.auths.map(function (a) { return { value: a.authId, label: a.authName + " (" + a.authId + ")" + (a.adminYn === "Y" ? " · 관리자" : "") }; }), split(r.authIds)));
			},
			body: function () {
				return { groupId: value("f-groupId"), groupName: value("f-groupName"), description: value("f-description"), sortOrder: value("f-sortOrder"), authIds: checked("authIds") };
			}
		},
		auth: {
			url: "/console/management/auths",
			key: "authId",
			head: ["권한 ID", "이름", "설명", "관리자", "순서", "메뉴"],
			cells: function (r) {
				return [r.authId, r.authName, r.description || "", yn(r.adminYn), r.sortOrder, split(r.menuIds).join(", ") || "-"];
			},
			form: function (r) {
				var isNew = !r;
				r = r || { adminYn: "N", sortOrder: 0, menuIds: "" };
				return field("권한 ID", input("f-authId", r.authId, isNew ? "" : "readonly"))
					+ field("이름", input("f-authName", r.authName))
					+ field("설명", input("f-description", r.description))
					+ field("관리자 권한", select("f-adminYn", [["N", "아니오"], ["Y", "예(관리 화면, 남의 실행 조작)"]], r.adminYn))
					+ field("순서", input("f-sortOrder", r.sortOrder))
					+ field("메뉴", checks("menuIds", menuTree(lookups.menus).map(function (t) {
						return { value: t.menu.menuId, depth: t.depth, label: t.menu.menuName + (t.menu.url ? "" : " (묶음)") + (t.menu.allUsersYn === "Y" ? " · 모두에게 보임" : "") };
					}), split(r.menuIds)));
			},
			body: function () {
				return { authId: value("f-authId"), authName: value("f-authName"), description: value("f-description"), adminYn: value("f-adminYn"), sortOrder: value("f-sortOrder"), menuIds: checked("menuIds") };
			}
		},
		menu: {
			url: "/console/management/menus",
			key: "menuId",
			tree: true,
			head: ["메뉴 ID", "이름", "주소", "아이콘", "순서", "사용", "모두에게 보임"],
			cells: function (r, depth) {
				return [r.menuId, (depth ? "└ " : "") + r.menuName, r.url || "(묶음)", r.icon || "-", r.sortOrder, yn(r.useYn), yn(r.allUsersYn)];
			},
			form: function (r) {
				var isNew = !r;
				r = r || { useYn: "Y", allUsersYn: "N", sortOrder: 0 };
				var parents = [["", "(없음: 맨 위)"]].concat(lookups.menus.filter(function (m) { return !m.url && m.menuId !== r.menuId; }).map(function (m) { return [m.menuId, m.menuName + " (" + m.menuId + ")"]; }));
				return field("메뉴 ID", input("f-menuId", r.menuId, isNew ? "" : "readonly"))
					+ field("상위 메뉴(묶음)", select("f-parentId", parents, r.parentId))
					+ field("이름", input("f-menuName", r.menuName))
					+ field("주소", input("f-url", r.url, "placeholder=\"비우면 묶음 제목\"") + " <span class=\"ai-hint\">화면 주소. 권한이 없는 사람은 이 화면을 열 수 없습니다.</span>")
					+ field("아이콘", select("f-icon", [["", "(없음)"]].concat(ICONS.map(function (i) { return [i, i]; })), r.icon))
					+ field("순서", input("f-sortOrder", r.sortOrder))
					+ field("사용", select("f-useYn", [["Y", "예"], ["N", "아니오(숨김)"]], r.useYn))
					+ field("모두에게 보임", select("f-allUsersYn", [["N", "아니오(권한에 연결된 사람만)"], ["Y", "예(로그인한 모두)"]], r.allUsersYn));
			},
			body: function () {
				return { menuId: value("f-menuId"), parentId: value("f-parentId"), menuName: value("f-menuName"), url: value("f-url"), icon: value("f-icon"),
					sortOrder: value("f-sortOrder"), useYn: value("f-useYn"), allUsersYn: value("f-allUsersYn") };
			}
		}
	};

	var def = PAGES[page];

	function loadLookups() {
		var wanted = [];
		if (page === "user") {
			wanted.push(api("GET", "/console/management/groups").then(function (d) { lookups.groups = d || []; }));
		}
		if (page === "group") {
			wanted.push(api("GET", "/console/management/auths").then(function (d) { lookups.auths = d || []; }));
		}
		if (page === "auth" || page === "menu") {
			wanted.push(api("GET", "/console/management/menus").then(function (d) { lookups.menus = d || []; }));
		}
		return Promise.all(wanted);
	}

	function load() {
		return Promise.all([api("GET", def.url), loadLookups()]).then(function (result) {
			rows = result[0] || [];
			if (page === "menu") {
				lookups.menus = rows;
			}
			render();
		}).catch(function (e) {
			say("목록을 읽지 못했습니다: " + e.message, false);
		});
	}

	function render() {
		listHead.innerHTML = "<tr>" + def.head.map(function (h) { return "<th>" + esc(h) + "</th>"; }).join("") + "</tr>";
		var ordered = def.tree ? menuTree(rows) : rows.map(function (r) { return { menu: r, depth: 0 }; });
		document.getElementById("mg-count").textContent = rows.length + "건";
		listBody.innerHTML = ordered.length === 0 ? "<tr><td colspan=\"" + def.head.length + "\">없습니다.</td></tr>" : ordered.map(function (t) {
			var r = t.menu;
			var on = editing && editing[def.key] === r[def.key];
			return "<tr class=\"ev-row" + (on ? " ev-row-on" : "") + "\" tabindex=\"0\" data-key=\"" + esc(r[def.key]) + "\">"
				+ def.cells(r, t.depth).map(function (c) { return "<td>" + esc(c == null ? "" : String(c)) + "</td>"; }).join("") + "</tr>";
		}).join("");
		renderForm();
	}

	function renderForm() {
		document.getElementById("mg-form-title").textContent = editing ? "고치기: " + editing[def.key] : "새로 만들기";
		form.querySelector("tbody").innerHTML = def.form(editing);
		document.getElementById("mg-extra").innerHTML = def.extra ? def.extra(editing) : "";
		document.getElementById("mg-delete").hidden = !editing;
	}

	function pick(key) {
		editing = rows.filter(function (r) { return r[def.key] === key; })[0] || null;
		say("");
		render();
	}

	listBody.addEventListener("click", function (event) {
		var row = event.target.closest("tr.ev-row");
		if (row) {
			pick(row.getAttribute("data-key"));
		}
	});
	listBody.addEventListener("keydown", function (event) {
		var row = event.target.closest("tr.ev-row");
		if (row && (event.key === "Enter" || event.key === " ")) {
			event.preventDefault();
			pick(row.getAttribute("data-key"));
		}
	});

	document.getElementById("mg-new").addEventListener("click", function () {
		editing = null;
		say("");
		render();
	});

	form.addEventListener("submit", function (event) {
		event.preventDefault();
		var body = def.body();
		api("POST", def.url, body).then(function () {
			say("저장했습니다.", true);
			return load().then(function () {
				pick(body[def.key]);
				say("저장했습니다.", true);
			});
		}).catch(function (e) {
			say(e.message, false);
		});
	});

	document.getElementById("mg-delete").addEventListener("click", function () {
		if (!editing || !confirm("'" + editing[def.key] + "'을(를) 지울까요? 되돌릴 수 없습니다.")) {
			return;
		}
		api("DELETE", def.url + "/" + encodeURIComponent(editing[def.key])).then(function () {
			editing = null;
			say("지웠습니다.", true);
			load();
		}).catch(function (e) {
			say(e.message, false);
		});
	});

	document.getElementById("mg-extra").addEventListener("click", function (event) {
		if (event.target.id !== "mg-reset" || !editing) {
			return;
		}
		var next = prompt("'" + editing.userId + "'의 새 비밀번호(8자 이상, 영문과 숫자). 잠김도 풀리고, 다음 로그인 때 바꾸게 합니다.");
		if (!next) {
			return;
		}
		api("POST", def.url + "/" + encodeURIComponent(editing.userId) + "/password", { newPassword: next }).then(function () {
			say("비밀번호를 초기화했습니다. 사용자에게 알려 주세요.", true);
			load();
		}).catch(function (e) {
			say(e.message, false);
		});
	});

	load();
})();
