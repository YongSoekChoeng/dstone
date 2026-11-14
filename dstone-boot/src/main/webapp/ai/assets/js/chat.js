var DstoneAiChat = (function () {

	var urls = {};
	var messagesEl, inputEl, formEl, agentEl, agentDescEl, toolsEl, toolsCountEl, modelInputEl, sendBtnEl;
	var agentsById = {}; // id -> {description, input, output, tools}, 목록 조회 결과를 드롭다운 change 시 다시 쓰기 위해 기억

	// "올린 문서 찾기" 단추가 고르는 Tool(dstone-ai-engine의 KnowledgeTool)
	var DOC_TOOLS = ["knowledgeListDocs", "knowledgeSearchDocs", "knowledgeGetChunks"];

	function init(options) {
		urls = options;
		messagesEl = document.getElementById("chat-messages");
		inputEl = document.getElementById("chat-input");
		formEl = document.getElementById("chat-form");
		agentEl = document.getElementById("chat-agent");
		agentDescEl = document.getElementById("chat-agent-desc");
		toolsEl = document.getElementById("chat-tools");
		toolsCountEl = document.getElementById("chat-tools-count");
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
		toolsEl.addEventListener("change", updateToolCount);
		document.getElementById("chat-tools-docs").addEventListener("click", function () {
			checkTools(function (name) { return DOC_TOOLS.indexOf(name) >= 0; });
		});
		document.getElementById("chat-tools-all").addEventListener("click", function () {
			checkTools(function () { return true; });
		});
		document.getElementById("chat-tools-none").addEventListener("click", function () {
			checkTools(function () { return false; });
		});

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
					agentsById[agent.id] = agent;
					var option = document.createElement("option");
					option.value = agent.id;
					option.textContent = agent.id;
					agentEl.appendChild(option);
				});
				// 처음에는 일반 대화용 Agent를 골라 둔다. 목록에 없으면 첫 번째 것이 그대로 골라진다.
				if (urls.defaultAgent && agentsById[urls.defaultAgent]) {
					agentEl.value = urls.defaultAgent;
				}
				updateAgentDescription();
			})
			.catch(function (err) {
				agentDescEl.textContent = "agent 목록을 불러오지 못했습니다: " + err.message;
			});
	}

	function updateAgentDescription() {
		var agent = agentsById[agentEl.value];
		renderTools(agent ? agent.tools : []);
		if (!agent) {
			agentDescEl.textContent = "";
			return;
		}
		var hint = agent.description || "";
		if (!isText(agent.input)) {
			hint += " [input: JSON - 입력창을 JSON으로 읽어 보냅니다. " + JSON.stringify(agent.input) + "]";
		}
		if (!isText(agent.output)) {
			hint += " [output이 string이 아니라 이 채팅 화면(스트리밍)에서는 부를 수 없습니다 - POST /api/ai/chat을 쓰십시오]";
		}
		agentDescEl.textContent = hint;
	}

	/**
	 * 고른 Agent 가 쓸 수 있는 Tool 을 체크박스로 그린다.
	 * Agent 를 바꿔도, 새 Agent 에도 있는 Tool 은 고른 상태를 그대로 둔다.
	 */
	function renderTools(names) {
		var checked = selectedTools();
		toolsEl.innerHTML = "";
		(names || []).slice().sort().forEach(function (name) {
			var label = document.createElement("label");
			var box = document.createElement("input");
			box.type = "checkbox";
			box.value = name;
			box.checked = checked.indexOf(name) >= 0;
			label.appendChild(box);
			label.appendChild(document.createTextNode(" " + name));
			toolsEl.appendChild(label);
		});
		updateToolCount();
	}

	/** 조건에 맞는 Tool 만 고른다 */
	function checkTools(match) {
		Array.prototype.forEach.call(toolsEl.querySelectorAll("input[type=checkbox]"), function (box) {
			box.checked = match(box.value);
		});
		updateToolCount();
	}

	/** 고른 Tool 이름 */
	function selectedTools() {
		return Array.prototype.filter.call(toolsEl.querySelectorAll("input[type=checkbox]"), function (box) {
			return box.checked;
		}).map(function (box) {
			return box.value;
		});
	}

	function updateToolCount() {
		var total = toolsEl.querySelectorAll("input[type=checkbox]").length;
		toolsCountEl.textContent = total === 0 ? "" : selectedTools().length + " / " + total + " 개 고름";
	}

	/** Agent의 input/output 스키마가 글자(string)인지 본다. 스키마가 없으면 글자로 본다. */
	function isText(schema) {
		return !schema || schema.type === "string";
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
		// Agent input이 글자가 아니면(예: object) 입력창 내용을 JSON으로 읽어서 보낸다.
		var input = message;
		var agent = agentsById[agentEl.value];
		if (agent && !isText(agent.input)) {
			try {
				input = JSON.parse(message);
			} catch (e) {
				alert("이 Agent의 input은 JSON이어야 합니다: " + e.message);
				return;
			}
		}
		var model = modelInputEl.value.trim();
		appendMessage("user", model ? message + " (model: " + model + ")" : message);
		inputEl.value = "";
		sendBtnEl.disabled = true;

		var assistantBubble = appendMessage("assistant", "");
		assistantBubble.classList.add("chat-bubble-pending");

		var requestBody = JSON.stringify({
			input: input,
			agent: agentEl.value,
			// 고른 Tool 만 붙는다. 하나도 고르지 않으면 빈 목록이라 Tool 없이 답한다.
			tools: selectedTools(),
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
