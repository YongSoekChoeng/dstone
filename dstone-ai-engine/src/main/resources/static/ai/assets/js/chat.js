var DstoneAiChat = (function () {

	var urls = {};
	var messagesEl, inputEl, formEl, agentEl, agentDescEl, toolsEl, modelInputEl, sendBtnEl;
	var agentsById = {}; // id -> {description, input, output, tools}, 목록 조회 결과를 드롭다운 change 시 다시 쓰기 위해 기억
	var agentTools = [];     // 고른 Agent 에 언제나 붙는 Tool [{name, description, category}] (Agent 정의의 tools)
	var addableTools = [];   // 더 붙일 수 있는 Tool [{name, description, category}] (Agent 에 없는 것)
	var chosenTools = [];    // 이번 대화에 더 붙일 Tool 이름
	var welcomeEl;           // 대화가 없을 때 보이는 안내와 예시 질문

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

		welcomeEl = document.getElementById("chat-welcome");
		if (welcomeEl) {
			welcomeEl.addEventListener("click", onExampleClick);
		}

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
		agentTools = agent ? (agent.availableTools || []) : [];
		renderTools(agent ? (agent.addableTools || []) : []);
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
	 * 이번 대화에 붙는 Tool 을 칩으로 그린다(Workflow 디자이너의 Agent 창과 같은 모양).
	 * - Agent 정의의 Tool(agentTools): 언제나 붙는다. 점선 칩이고 × 가 없다.
	 * - 더 고른 Tool(chosenTools): 칩의 × 로 빼고, [+ 찾아서 추가]로 "Tool 찾기" 창(tool-picker.js)을 열어 종류별 표에서 고른다.
	 * Agent 를 바꾸면, 새 Agent 에도 더 붙일 수 있는 Tool 만 고른 채로 남긴다.
	 */
	function renderTools(tools) {
		addableTools = tools || [];
		chosenTools = chosenTools.filter(function (name) {
			return findTool(name) !== null;
		});
		var html = "";
		if (agentTools.length === 0 && chosenTools.length === 0) {
			html += "<span class=\"ai-hint\">붙은 Tool 이 없습니다(Tool 없이 답합니다).</span>";
		}
		agentTools.forEach(function (tool) {
			html += toolChip(tool, true);
		});
		chosenTools.forEach(function (name) {
			html += toolChip(findTool(name), false);
		});
		html += "<button type=\"button\" class=\"kn-plain wd-pick-btn\" data-tool-pick=\"1\"" + (addableTools.length === 0 ? " disabled" : "") + ">+ 찾아서 추가</button>";
		html += "<span class=\"chat-tools-count\">기본 " + agentTools.length + "개";
		if (addableTools.length > 0) {
			html += " · 더 고름 " + chosenTools.length + " / " + addableTools.length + "개";
		} else if (agentTools.length > 0) {
			html += " · 더 붙일 Tool 없음";
		}
		html += "</span>";
		toolsEl.innerHTML = html;
	}

	/** Tool 칩 하나. fixed 면 Agent 정의의 Tool 이라 × 없이 "기본"으로 표시한다 */
	function toolChip(tool, fixed) {
		var category = DstoneToolPicker.labelOf(tool.category);
		var title = (fixed ? "Agent 기본(뺄 수 없음) · " : "") + category + " · " + (tool.description || "");
		var html = "<span class=\"wd-chip" + (fixed ? " chat-tool-fixed" : "") + "\" title=\"" + escapeHtml(title) + "\">"
			+ "<span class=\"chat-tool-cat\">" + escapeHtml(fixed ? "기본 · " + category : category) + "</span>" + escapeHtml(tool.name);
		if (!fixed) {
			html += "<button type=\"button\" class=\"wd-chip-x\" data-tool-remove=\"" + escapeHtml(tool.name) + "\" title=\"빼기\">×</button>";
		}
		return html + "</span>";
	}

	/** 더 붙일 수 있는 Tool 가운데 이 이름인 것. 없으면 null */
	function findTool(name) {
		for (var i = 0; i < addableTools.length; i++) {
			if (addableTools[i].name === name) {
				return addableTools[i];
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
			renderTools(addableTools);
			return;
		}
		if (e.target.closest("[data-tool-pick]")) {
			DstoneToolPicker.open({
				title: "더 붙일 Tool 찾기 - " + agentEl.value,
				tools: addableTools,
				chosen: chosenTools,
				onDone: function (names) {
					chosenTools = names;
					renderTools(addableTools);
				}
			});
		}
	}

	/**
	 * 예시 질문을 누르면 입력창을 채우고, 그 질문에 필요한 종류(data-tools)의 Tool 을 모두 더 골라 둔다.
	 * Agent 에 이미 있는 Tool 은 더 고를 필요가 없다(addableTools 에 없다).
	 * data-tools 가 비어 있으면 더 고른 Tool 을 모두 뺀다(Agent 기본 Tool 은 그대로). 바로 보내지는 않는다(고쳐서 보낼 수 있게).
	 */
	function onExampleClick(e) {
		var example = e.target.closest(".chat-example");
		if (!example) {
			return;
		}
		var category = example.getAttribute("data-tools");
		var names = [];
		for (var i = 0; i < addableTools.length; i++) {
			var tool = addableTools[i];
			if (category && tool.category === category && !tool.disabled) {
				names.push(tool.name);
			}
		}
		chosenTools = names;
		renderTools(addableTools);
		inputEl.value = example.getAttribute("data-text");
		inputEl.focus();
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
		// 내가 보낸 말은 파란 말풍선이라 드래그해도 잘 안 보여서, 한 번에 복사하는 버튼을 붙인다.
		if (role === "user") {
			wrap.appendChild(createCopyButton(text));
		}
		messagesEl.appendChild(wrap);
		scrollToEnd();
		return bubble;
	}

	/** 대화 칸(안내 + 메시지를 감싼 .chat-scroll)을 맨 아래로 내린다 */
	function scrollToEnd() {
		var box = messagesEl.parentNode;
		box.scrollTop = box.scrollHeight;
	}

	/** 누르면 text를 클립보드에 넣는 [복사] 버튼을 만든다. */
	function createCopyButton(text) {
		var btn = document.createElement("button");
		btn.type = "button";
		btn.className = "chat-copy";
		btn.textContent = "복사";
		btn.title = "이 메시지를 복사";
		btn.addEventListener("click", function () {
			copyText(text).then(function () {
				btn.textContent = "복사됨";
			}, function () {
				btn.textContent = "복사 실패";
			}).then(function () {
				setTimeout(function () { btn.textContent = "복사"; }, 1500);
			});
		});
		return btn;
	}

	/** 클립보드에 글자를 넣는다. http 화면처럼 navigator.clipboard를 못 쓰면 숨긴 textarea로 복사한다. */
	function copyText(text) {
		if (navigator.clipboard && window.isSecureContext) {
			return navigator.clipboard.writeText(text);
		}
		return new Promise(function (resolve, reject) {
			var area = document.createElement("textarea");
			area.value = text;
			area.setAttribute("readonly", "");
			area.style.position = "fixed";
			area.style.opacity = "0";
			document.body.appendChild(area);
			area.select();
			var ok = false;
			try {
				ok = document.execCommand("copy");
			} catch (e) {
				ok = false;
			}
			document.body.removeChild(area);
			if (ok) {
				resolve();
			} else {
				reject(new Error("copy failed"));
			}
		});
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
		if (welcomeEl) {
			welcomeEl.hidden = true;
		}
		appendMessage("user", model ? message + " (model: " + model + ")" : message);
		inputEl.value = "";
		sendBtnEl.disabled = true;

		var assistantBubble = appendMessage("assistant", "");
		assistantBubble.classList.add("chat-bubble-pending");

		var requestBody = JSON.stringify({
			input: input,
			agent: agentEl.value,
			// Agent 기본 Tool 은 서버가 언제나 붙인다. 여기에는 더 고른 Tool 만 담는다(비면 Agent 정의 그대로).
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
						scrollToEnd();
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
