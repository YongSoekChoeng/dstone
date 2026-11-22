/**
 * 로그인 화면(/{앱}/login.html)의 동작입니다. dstone-ai-engine, dstone-knowledge 가 같은 파일을 하나씩 갖고 있으니 고칠 때 둘 다 맞춥니다.
 *   - 로그인: POST /console/login {userId, password, next} → 성공하면 돌아갈 화면으로. 첫 로그인이면 비밀번호 바꾸기로
 *   - 비밀번호 바꾸기(?change=1 이거나 첫 로그인): POST /console/password {currentPassword, newPassword}
 */
(function () {
	var params = new URLSearchParams(location.search);
	var loginForm = document.getElementById("cs-login-form");
	var changeForm = document.getElementById("cs-change-form");
	var message = document.getElementById("cs-login-message");

	function show(text, ok) {
		message.textContent = text || "";
		message.className = "cs-login-message" + (text ? (ok ? " cs-login-ok" : " cs-login-error") : "");
	}

	function post(url, body) {
		return fetch(url, {
			method: "POST",
			headers: { "Content-Type": "application/json", "Accept": "application/json" },
			body: JSON.stringify(body)
		}).then(function (response) {
			return response.json().catch(function () {
				return {};
			}).then(function (data) {
				data.httpOk = response.ok;
				return data;
			});
		});
	}

	function changeMode(reason) {
		loginForm.hidden = true;
		changeForm.hidden = false;
		show(reason || "", false);
		document.getElementById("cs-current-password").focus();
	}

	loginForm.addEventListener("submit", function (event) {
		event.preventDefault();
		var button = loginForm.querySelector("button");
		button.disabled = true;
		show("");
		post("/console/login", {
			userId: document.getElementById("cs-user-id").value.trim(),
			password: document.getElementById("cs-password").value,
			next: params.get("next") || ""
		}).then(function (data) {
			if (!data.httpOk || !data.ok) {
				show(data.message || "로그인하지 못했습니다.", false);
				return;
			}
			if (data.pwChangeRequired) {
				document.getElementById("cs-current-password").value = document.getElementById("cs-password").value;
				changeMode("처음 로그인했거나 관리자가 비밀번호를 초기화했습니다. 새 비밀번호로 바꾸세요.");
				return;
			}
			location.href = data.next;
		}).catch(function () {
			show("서버에 연결하지 못했습니다.", false);
		}).then(function () {
			button.disabled = false;
		});
	});

	changeForm.addEventListener("submit", function (event) {
		event.preventDefault();
		var next = document.getElementById("cs-new-password").value;
		if (next !== document.getElementById("cs-new-password2").value) {
			show("새 비밀번호 두 칸이 서로 다릅니다.", false);
			return;
		}
		var button = changeForm.querySelector("button[type=submit]");
		button.disabled = true;
		post("/console/password", {
			currentPassword: document.getElementById("cs-current-password").value,
			newPassword: next
		}).then(function (data) {
			if (!data.httpOk) {
				show(data.message || "비밀번호를 바꾸지 못했습니다.", false);
				return;
			}
			show("비밀번호를 바꿨습니다.", true);
			setTimeout(function () {
				location.href = data.next;
			}, 600);
		}).catch(function () {
			show("서버에 연결하지 못했습니다.", false);
		}).then(function () {
			button.disabled = false;
		});
	});

	document.getElementById("cs-change-cancel").addEventListener("click", function () {
		// 로그인한 상태면 이전 화면으로, 아니면 로그인 칸으로
		if (params.get("change")) {
			history.length > 1 ? history.back() : (location.href = "/");
		} else {
			changeForm.hidden = true;
			loginForm.hidden = false;
			show("");
		}
	});

	if (params.get("change")) {
		changeMode("");
	} else {
		document.getElementById("cs-user-id").focus();
	}
})();
