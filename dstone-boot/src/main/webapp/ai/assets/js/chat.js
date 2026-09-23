var DstoneAiChat = (function () {

	var urls = {};
	var messagesEl, inputEl, formEl, agentEl, agentDescEl, ragCheckEl, toolsCheckEl, modelInputEl, sendBtnEl;
	var agentsById = {}; // id -> description, 목록 조회 결과를 드롭다운 change 시 다시 쓰기 위해 기억

	function init(options) {
		urls = options;
		messagesEl = document.getElementById("chat-messages");
		inputEl = document.getElementById("chat-input");
		formEl = document.getElementById("chat-form");
		agentEl = document.getElementById("chat-agent");
		agentDescEl = document.getElementById("chat-agent-desc");
		ragCheckEl = document.getElementById("chat-rag-enabled");
		toolsCheckEl = document.getElementById("chat-tools-enabled");
		modelInputEl = document.getElementById("chat-model-override");
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
		agentEl.addEventListener("change", updateAgentDescription);

		loadAgentList();
	}

	// "Workflow 테스트" 화면(workflow.js)의 loadWorkflowList()/updateWorkflowDescription()과 같은 패턴이다.
	function loadAgentList() {
		fetch(urls.listUrl)
			.then(function (response) {
				if (!response.ok) {
					throw new Error("서버 오류(" + response.status + ")");
				}
				return response.json();
			})
			.then(function (agents) {
				agentEl.innerHTML = "";
				agentsById = {};
				(agents || []).forEach(function (agent) {
					agentsById[agent.id] = agent.description;
					var option = document.createElement("option");
					option.value = agent.id;
					option.textContent = agent.id;
					agentEl.appendChild(option);
				});
				updateAgentDescription();
			})
			.catch(function (err) {
				agentDescEl.textContent = "agent 목록을 불러오지 못했습니다: " + err.message;
			});
	}

	function updateAgentDescription() {
		agentDescEl.textContent = agentsById[agentEl.value] || "";
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
		var model = modelInputEl.value.trim();
		appendMessage("user", model ? message + " (model: " + model + ")" : message);
		inputEl.value = "";
		sendBtnEl.disabled = true;

		var assistantBubble = appendMessage("assistant", "");
		assistantBubble.classList.add("chat-bubble-pending");

		var requestBody = JSON.stringify({
			message: message,
			agent: agentEl.value,
			ragEnabled: ragCheckEl.checked,
			toolsEnabled: toolsCheckEl.checked,
			model: model ? model : null
		});

		fetch(urls.sendUrl, {
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
