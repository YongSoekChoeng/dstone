var DstoneAiChat = (function () {

	var urls = {};
	var messagesEl, inputEl, formEl, agentEl, agentDescEl, toolsEl, modelInputEl, sendBtnEl;
	var agentsById = {}; // id -> {description, input, output, tools}, 목록 조회 결과를 드롭다운 change 시 다시 쓰기 위해 기억
	var availableTools = []; // 고른 Agent 가 쓸 수 있는 Tool [{name, description, category}]
	var chosenTools = [];    // 이번 대화에 쓸 Tool 이름

	function init(options) {
		urls = options;
		messagesEl = document.getElementById("chat-messages");
		inputEl = document.getElementById("chat-input");
		formEl = document.getElementById("chat-form");
		agentEl = document.getElementById("chat-agent");
		agentDescEl = document.getElementById("chat-agent-desc");
		toolsEl = document.getElementById("chat-tools");
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
		toolsEl.addEventListener("click", onToolsClick);

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
	 * 고른 Tool 을 칩으로 그린다(Workflow 디자이너의 Agent 창과 같은 모양).
	 * 칩의 × 로 빼고, [+ 찾아서 추가]로 "Tool 찾기" 창(tool-picker.js)을 열어 종류별 표에서 고른다.
	 * Agent 를 바꾸면, 새 Agent 에도 있는 Tool 만 고른 채로 남긴다.
	 */
	function renderTools(tools) {
		availableTools = tools || [];
		chosenTools = chosenTools.filter(function (name) {
			return findTool(name) !== null;
		});
		var html = "";
		if (availableTools.length === 0) {
			html += "<span class=\"ai-hint\">이 Agent 는 고를 수 있는 Tool 이 없습니다.</span>";
		} else if (chosenTools.length === 0) {
			html += "<span class=\"ai-hint\">고른 Tool 이 없습니다(Tool 없이 답합니다).</span>";
		}
		chosenTools.forEach(function (name) {
			var tool = findTool(name);
			var category = DstoneToolPicker.labelOf(tool.category);
			html += "<span class=\"wd-chip\" title=\"" + escapeHtml(category + " · " + (tool.description || "")) + "\">"
				+ "<span class=\"chat-tool-cat\">" + escapeHtml(category) + "</span>" + escapeHtml(name)
				+ "<button type=\"button\" class=\"wd-chip-x\" data-tool-remove=\"" + escapeHtml(name) + "\" title=\"빼기\">×</button></span>";
		});
		html += "<button type=\"button\" class=\"kn-plain wd-pick-btn\" data-tool-pick=\"1\"" + (availableTools.length === 0 ? " disabled" : "") + ">+ 찾아서 추가</button>";
		if (availableTools.length > 0) {
			html += "<span class=\"chat-tools-count\">" + chosenTools.length + " / " + availableTools.length + " 개 고름</span>";
		}
		toolsEl.innerHTML = html;
	}

	/** 이 Agent 가 쓸 수 있는 Tool 가운데 이 이름인 것. 없으면 null */
	function findTool(name) {
		for (var i = 0; i < availableTools.length; i++) {
			if (availableTools[i].name === name) {
				return availableTools[i];
			}
		}
		return null;
	}

	/** 칩의 × 와 [+ 찾아서 추가] */
	function onToolsClick(e) {
		var remove = e.target.closest("[data-tool-remove]");
		if (remove) {
			var name = remove.getAttribute("data-tool-remove");
			chosenTools = chosenTools.filter(function (one) {
				return one !== name;
			});
			renderTools(availableTools);
			return;
		}
		if (e.target.closest("[data-tool-pick]")) {
			DstoneToolPicker.open({
				title: "Tool 찾기 - " + agentEl.value,
				tools: availableTools,
				chosen: chosenTools,
				onDone: function (names) {
					chosenTools = names;
					renderTools(availableTools);
				}
			});
		}
	}

	function escapeHtml(value) {
		return String(value === undefined || value === null ? "" : value)
			.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
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
			tools: chosenTools.slice(),
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
