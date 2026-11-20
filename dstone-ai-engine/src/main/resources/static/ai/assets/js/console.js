/**
 * dstone-ai-engine, dstone-knowledge 화면이 같이 쓰는 도우미입니다. 두 앱(ai/assets, knowledge/assets)에 하나씩 있으니 고칠 때 둘 다 맞춥니다.
 *
 * 하는 일:
 *   - 왼쪽 메뉴를 그린다(예전 dstone-boot 의 views/console/sidebar.jsp). 메뉴 목록은 앱마다 *-menu.js 가 넘긴다.
 *   - 세션 정보(GET /console/session)를 한 번 받아 둔다. 사용자 이름, 로그인 사용 여부, 다른 앱 주소가 들어 있다.
 *   - 다른 앱으로 가는 링크(data-app-link="knowledge:/knowledge/search/search.html")의 주소를 채운다.
 *
 * 쓰는 법(화면 HTML):
 *   <aside id="header-wrapper" class="cs-sidebar"></aside>
 *   <script src="/ai/assets/js/console.js"></script>
 *   <script src="/ai/assets/js/ai-menu.js"></script>   ← DstoneConsole.sidebar({...}) 를 부른다
 */
var DstoneConsole = (function () {

	/** 아이콘(선으로 그린 24x24 SVG 경로). 외부 아이콘 글꼴을 쓰지 않아 폐쇄망에서도 그대로 보인다 */
	var ICONS = {
		home: "<path d='M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'/>",
		chat: "<path d='M21 12a8 8 0 0 1-11.6 7.1L4 21l1.9-5.4A8 8 0 1 1 21 12z'/>",
		play: "<circle cx='12' cy='12' r='9'/><path d='m10 8.5 5.5 3.5-5.5 3.5z'/>",
		list: "<path d='M8 6h13M8 12h13M8 18h13'/><circle cx='4' cy='6' r='1'/><circle cx='4' cy='12' r='1'/><circle cx='4' cy='18' r='1'/>",
		flow: "<rect x='3' y='3' width='7' height='6' rx='1.5'/><rect x='14' y='15' width='7' height='6' rx='1.5'/><path d='M6.5 9v3.5a2 2 0 0 0 2 2H17.5V15'/>",
		dashboard: "<rect x='3' y='3' width='8' height='10' rx='1.5'/><rect x='13' y='3' width='8' height='6' rx='1.5'/><rect x='13' y='11' width='8' height='10' rx='1.5'/><rect x='3' y='15' width='8' height='6' rx='1.5'/>",
		folder: "<path d='M3 6.5A1.5 1.5 0 0 1 4.5 5H9l2 2.5h8.5A1.5 1.5 0 0 1 21 9v9.5a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z'/>",
		search: "<circle cx='11' cy='11' r='7'/><path d='m20 20-3.5-3.5'/>",
		impact: "<circle cx='12' cy='12' r='2.5'/><circle cx='12' cy='12' r='6'/><circle cx='12' cy='12' r='9.5'/>",
		graph: "<circle cx='5' cy='6' r='2'/><circle cx='19' cy='6' r='2'/><circle cx='12' cy='18' r='2'/><path d='M7 6h10M6.2 7.7l4.6 8.6M17.8 7.7l-4.6 8.6'/>",
		diff: "<path d='M7 3v12M3 7h8M17 21V9M13 17h8'/>",
		book: "<path d='M4 4.5A1.5 1.5 0 0 1 5.5 3H20v15H5.5A1.5 1.5 0 0 0 4 19.5z'/><path d='M4 19.5A1.5 1.5 0 0 0 5.5 21H20'/>",
		back: "<path d='M15 18 9 12l6-6'/>",
		user: "<circle cx='12' cy='8' r='4'/><path d='M4 21a8 8 0 0 1 16 0'/>",
		tool: "<path d='M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18v3h3l6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.5-.5-.5-2.5z'/>"
	};

	var sessionPromise = null;

	/** 세션 정보. 한 화면에서 한 번만 묻는다. 못 받으면 빈 값으로 채운다(메뉴는 그대로 그린다) */
	function session() {
		if (!sessionPromise) {
			sessionPromise = fetch("/console/session", { headers: { "Accept": "application/json" } }).then(function (response) {
				return response.ok ? response.json() : {};
			}).catch(function () {
				return {};
			}).then(function (info) {
				info.urls = info.urls || {};
				return info;
			});
		}
		return sessionPromise;
	}

	/**
	 * 다른 앱 화면의 주소. 같은 앱이면 path 그대로.
	 * 로그인을 켠 환경이면 dstone-boot 를 거쳐 가야 로그인 정보가 넘어가므로 dstone-boot 의 /sso/go.do 로 보낸다.
	 * 갈 주소를 모르면 null.
	 */
	function appUrl(info, app, path) {
		if (!app || app === info.app) {
			return path;
		}
		if (info.loginEnabled && info.urls.boot) {
			return info.urls.boot + "/sso/go.do?target=" + encodeURIComponent(app) + "&path=" + encodeURIComponent(path);
		}
		return info.urls[app] ? info.urls[app] + path : null;
	}

	function escapeHtml(text) {
		return String(text == null ? "" : text).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
	}

	function icon(name) {
		return "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\">" + (ICONS[name] || "") + "</svg>";
	}

	/**
	 * 왼쪽 메뉴를 그린다.
	 * options: {brand: "AI", mark: "AI", home: "/ai/index.html", group: "AI"(첫 묶음 제목, 없어도 됨),
	 *           menus: [[주소, 이름, 아이콘] ...]}. 주소가 "#group" 이면 메뉴가 아니라 묶음 제목 줄이다.
	 */
	function sidebar(options) {
		var aside = document.getElementById("header-wrapper");
		if (!aside) {
			return;
		}
		var current = location.pathname;
		var html = "<a class=\"cs-brand\" href=\"" + escapeHtml(options.home) + "\">"
			+ "<span class=\"cs-brand-mark\" aria-hidden=\"true\">" + escapeHtml(options.mark) + "</span>"
			+ "<span class=\"cs-brand-text\"><b>Dstone</b> " + escapeHtml(options.brand) + "</span></a>"
			// dstone-boot 주소를 알면 돌아가는 링크를 채운다(session() 뒤)
			+ "<a class=\"cs-nav-item cs-nav-home\" id=\"cs-nav-boot\" href=\"#\" hidden>" + icon("back") + "<span>dstone-boot로 돌아가기</span></a>"
			+ "<nav class=\"cs-nav\" aria-label=\"" + escapeHtml(options.brand) + " 메뉴\">";
		if (options.group) {
			html += "<p class=\"cs-nav-group\">" + escapeHtml(options.group) + "</p>";
		}
		options.menus.forEach(function (menu) {
			if (menu[0] === "#group") {
				html += "<p class=\"cs-nav-group\">" + escapeHtml(menu[1]) + "</p>";
				return;
			}
			var on = current === menu[0];
			html += "<a href=\"" + escapeHtml(menu[0]) + "\" class=\"cs-nav-item" + (on ? " cs-nav-on" : "") + "\"" + (on ? " aria-current=\"page\"" : "") + ">"
				+ icon(menu[2]) + "<span>" + escapeHtml(menu[1]) + "</span></a>";
		});
		html += "</nav><p class=\"cs-nav-user\" id=\"cs-nav-user\" hidden>" + icon("user") + "<span></span></p>";
		aside.innerHTML = html;

		session().then(function (info) {
			if (info.urls.boot) {
				var back = document.getElementById("cs-nav-boot");
				back.href = info.urls.boot + "/views/main";
				back.hidden = false;
			}
			if (info.userId) {
				var user = document.getElementById("cs-nav-user");
				user.querySelector("span").textContent = (info.userName || info.userId) + (info.source === "DEFAULT" ? " (로그인 없음)" : "");
				user.title = "로그인 id: " + info.userId + (info.admin ? " · 관리자" : "");
				user.hidden = false;
			}
		});
	}

	/** data-app-link="앱:경로" 가 붙은 링크의 주소를 채운다. 갈 수 없으면 링크를 숨긴다 */
	function bindAppLinks() {
		var links = document.querySelectorAll("a[data-app-link]");
		if (!links.length) {
			return;
		}
		session().then(function (info) {
			Array.prototype.forEach.call(links, function (link) {
				var value = link.getAttribute("data-app-link");
				var at = value.indexOf(":");
				var url = appUrl(info, value.substring(0, at), value.substring(at + 1));
				if (url) {
					link.href = url;
				} else {
					link.hidden = true;
				}
			});
		});
	}

	if (document.readyState === "loading") {
		document.addEventListener("DOMContentLoaded", bindAppLinks);
	} else {
		bindAppLinks();
	}

	return {
		session: session,
		sidebar: sidebar,
		appUrl: function (app, path) {
			return session().then(function (info) {
				return appUrl(info, app, path);
			});
		},
		escapeHtml: escapeHtml
	};
})();
