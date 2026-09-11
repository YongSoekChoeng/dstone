var DstoneAiChat = (function () {

	var sendUrl = "";
	var messagesEl, inputEl, formEl, ragCheckEl, toolsCheckEl, sendBtnEl;

	function init(url) {
		sendUrl = url;
		messagesEl = document.getElementById("chat-messages");
		inputEl = document.getElementById("chat-input");
		formEl = document.getElementById("chat-form");
		ragCheckEl = document.getElementById("chat-rag-enabled");
		toolsCheckEl = document.getElementById("chat-tools-enabled");
		sendBtnEl = document.getElementById("chat-send");

		formEl.addEventListener("submit", function (e) {
			e.preventDefault();
			sendMessage();
		});
		inputEl.addEventListener("keydown", function (e) {
			if (e.key === "Enter" && !e.shiftKey) {
				e.preventDefault();
				sendMessage();
			}
		});
	}

	function appendMessage(role, text) {
		var wrap = document.createElement("div");
		wrap.className = "chat-message chat-message-" + role;
		var bubble = document.createElement("div");
		bubble.className = "chat-bubble";
		bubble.textContent = text;
		wrap.appendChild(bubble);
		messagesEl.appendChild(wrap);
		messagesEl.scrollTop = messagesEl.scrollHeight;
		return bubble;
	}

	// text/event-stream 프레임(빈 줄로 구분)을 파싱한다. 같은 프레임 안의 여러 data: 줄은
	// SSE 스펙대로 "\n"으로 이어붙여야 토큰 안에 포함된 줄바꿈이 유실되지 않는다.
	function parseSseChunk(buffer) {
		var frames = buffer.split("\n\n");
		var remainder = frames.pop();
		var tokens = frames.map(function (frame) {
			return frame.split("\n")
				.filter(function (line) { return line.indexOf("data:") === 0; })
				.map(function (line) { return line.substring(5).replace(/^ /, ""); })
				.join("\n");
		});
		return { tokens: tokens, remainder: remainder };
	}

	function sendMessage() {
		var message = inputEl.value.trim();
		if (!message) {
			return;
		}
		appendMessage("user", message);
		inputEl.value = "";
		sendBtnEl.disabled = true;

		var assistantBubble = appendMessage("assistant", "");
		assistantBubble.classList.add("chat-bubble-pending");

		var requestBody = JSON.stringify({
			message: message,
			ragEnabled: ragCheckEl.checked,
			toolsEnabled: toolsCheckEl.checked
		});

		fetch(sendUrl, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: requestBody
		}).then(function (response) {
			if (!response.ok) {
				throw new Error("서버 오류(" + response.status + ")");
			}
			assistantBubble.classList.remove("chat-bubble-pending");
			var reader = response.body.getReader();
			var decoder = new TextDecoder("utf-8");
			var buffer = "";
			var answer = "";

			function read() {
				return reader.read().then(function (result) {
					if (result.done) {
						sendBtnEl.disabled = false;
						return;
					}
					buffer += decoder.decode(result.value, { stream: true });
					var parsed = parseSseChunk(buffer);
					buffer = parsed.remainder;
					parsed.tokens.forEach(function (token) {
						answer += token;
						assistantBubble.textContent = answer;
						messagesEl.scrollTop = messagesEl.scrollHeight;
					});
					return read();
				});
			}
			return read();
		}).catch(function (err) {
			assistantBubble.classList.remove("chat-bubble-pending");
			assistantBubble.textContent = "오류: " + err.message;
			sendBtnEl.disabled = false;
		});
	}

	return { init: init };
})();
