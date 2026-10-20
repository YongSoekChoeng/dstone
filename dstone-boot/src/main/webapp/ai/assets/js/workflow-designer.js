/*
 * "Workflow 디자이너" 화면(views/ai/designer/designer.jsp)의 스크립트입니다.
 *
 * 기준은 늘 Workflow 정의(YAML을 읽은 객체, model)입니다. 그림은 model을 그대로 그린 것이고,
 * 그림에서 무엇을 고치면(step 추가, 화살표 연결, 설정 변경) model을 고친 뒤 그림을 통째로 다시 그립니다.
 *   step          = model.steps의 항목 하나 = 그림의 네모 하나(네모 id = step id)
 *   화살표         = step의 next / onFailure / routes 값
 *   시작 동그라미   = model.steps의 첫 step으로 가는 화살표(엔진은 첫 step부터 실행합니다)
 *   END / FAIL    = 끝내는 예약어
 *
 * 저장할 글자(YAML)는 model을 고칠 때만 새로 만듭니다. 그래서 노드 위치만 옮기고 저장하면 파일 글자는 그대로이고(주석 유지),
 * YAML 탭에서 직접 고친 글자도 그대로 저장됩니다. 그림에서 고치면 맨 위 주석만 남기고 다시 만듭니다.
 *
 * 그리기는 X6(AntV), 자동 배치는 dagre, YAML 읽고 쓰기는 js-yaml을 씁니다(모두 ai/assets/lib에 넣어 둔 파일).
 * 서버 호출은 dstone-boot의 /ai/designer/call.do를 거쳐 dstone-ai-engine의 /api/ai/definitions로 갑니다.
 */
var DstoneAiDesigner = (function () {

	var START_ID = "__START__";
	var END_ID = "END";
	var FAIL_ID = "FAIL";
	var STEP_W = 230;
	var STEP_H = 58;
	var TERM_SIZE = 48;
	var STEP_ID_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/;

	/** step 종류별 색(테두리, 바탕) */
	var TYPE_COLORS = {
		AGENT: { stroke: "#2563eb", fill: "#eff6ff" },
		SUPERVISOR: { stroke: "#7c3aed", fill: "#f5f3ff" },
		ROUTER: { stroke: "#d97706", fill: "#fffbeb" },
		TOOL: { stroke: "#059669", fill: "#ecfdf5" },
		APPROVAL: { stroke: "#db2777", fill: "#fdf2f8" }
	};

	/** 화살표 종류별 모양 */
	var EDGE_STYLES = {
		next: { stroke: "#4e4d43", dash: null },
		onFailure: { stroke: "#b91c1c", dash: "6 4" },
		route: { stroke: "#d97706", dash: null },
		rejectTo: { stroke: "#b91c1c", dash: "6 4" },
		start: { stroke: "#007b9d", dash: null }
	};

	/** step 종류별로 오른쪽 설정 칸에 보여 줄 항목(순서대로) */
	var STEP_FIELDS = {
		AGENT: ["agent", "with", "forEach", "itemVariable", "save", "saveError", "saveInput", "saveEach", "memory"],
		SUPERVISOR: ["agent", "with", "forEach", "itemVariable", "save", "saveError", "saveInput", "saveEach", "memory"],
		ROUTER: ["agent", "with", "save", "saveError", "saveInput", "memory"],
		TOOL: ["tool", "with", "forEach", "itemVariable", "save", "saveError", "saveInput", "saveEach"],
		APPROVAL: ["approval.artifact", "approval.approverRole", "approval.requireCommentOnReject", "save"]
	};

	/** 설정 항목 설명(오른쪽 칸의 이름 옆에 작게 보입니다) */
	var FIELD_HELP = {
		agent: "부를 Agent",
		tool: "부를 Tool",
		"with": "넘길 값. 예: ${input}, ${state.이름}, 또는 키: 값 여러 줄",
		forEach: "목록 하나를 표현식으로. 예: ${state.files} (항목마다 따로 실행)",
		itemVariable: "항목을 읽을 이름(기본 item → ${item})",
		save: "결과를 저장할 state 이름",
		saveError: "실패 이유를 저장할 state 이름",
		saveInput: "받은 입력을 저장할 state 이름",
		saveEach: "forEach 반복별 묶음을 저장할 state 이름",
		memory: "대화 기억 쓰기(forEach와 같이 못 씀)",
		"approval.artifact": "승인자에게 보여 줄 값(표현식)",
		"approval.approverRole": "승인자 역할",
		"approval.requireCommentOnReject": "반려할 때 의견 필수",
		next: "성공하면 갈 step",
		onFailure: "실패하면 갈 step"
	};

	var urls = {};
	var els = {};
	var graph = null;
	var dnd = null;

	/** 디자이너 첫 화면에서 받은 Workflow/Agent/Tool 목록과 저장 가능 여부 */
	var catalog = { workflows: [], agents: [], tools: [], editable: false };

	/**
	 * 지금 열려 있는 Workflow.
	 *   id: 엔진에 등록된 id(새로 만든 것이면 null), originalPath: 서버의 원래 파일 경로(새로 만든 것이면 null),
	 *   model: YAML을 읽은 객체, header: 맨 위 주석, bodyComments: 맨 위 말고 남은 주석 줄 수
	 */
	var doc = null;
	/** 노드 위치(노드 id → {x, y}). Workflow 파일 옆 이름.layout.json에 저장합니다 */
	var positions = {};
	/** 고른 것: null(Workflow 전체) | {type: "step", id} | {type: "edge", from, kind, route} */
	var selected = null;
	/** 저장하지 않은 변경이 있는지 */
	var dirty = false;
	/** YAML 탭에서 글자를 고쳤는데 아직 그림에 반영하지 않았는지 */
	var textEdited = false;
	/** 되돌리기용 이전 상태(model + 위치) */
	var undoStack = [];
	/** FAIL로 가는 실패 화살표를 숨길지(step이 많으면 그 선들이 그림을 덮습니다) */
	var hideFailEdges = false;

	/**
	 * 화살표 모양. router = 길 찾기, connector = 선을 그리는 법(X6 기본 제공).
	 * 고른 값은 이 브라우저에만 기억한다(정의 파일이나 노드 위치 파일에는 넣지 않는다).
	 * 포트가 step 아래(나감)와 위(들어옴)에 있으므로, 직각 선은 아래로 나가 위로 들어오게 방향을 정해 준다.
	 */
	var LINE_STYLES = {
		manhattan: { router: { name: "manhattan", args: { padding: 16, startDirections: ["bottom"], endDirections: ["top"] } }, connector: { name: "rounded", args: { radius: 8 } } },
		orth: { router: { name: "orth", args: { padding: 16 } }, connector: { name: "rounded", args: { radius: 8 } } },
		smooth: { router: { name: "normal" }, connector: { name: "smooth" } },
		normal: { router: { name: "normal" }, connector: { name: "normal" } }
	};
	var LINE_STYLE_KEY = "dstone.designer.lineStyle";
	/** 지금 고른 화살표 모양(LINE_STYLES의 키). 기본값은 직각 + 노드 피해 가기 */
	var lineStyle = "manhattan";

	/* ====================================================================
	 * 시작
	 * ==================================================================== */

	function init(options) {
		urls = options;
		els.workflow = document.getElementById("wd-workflow");
		els.path = document.getElementById("wd-path");
		els.pathHint = document.getElementById("wd-path-hint");
		els.status = document.getElementById("wd-status");
		els.message = document.getElementById("wd-message");
		els.canvas = document.getElementById("wd-canvas");
		els.props = document.getElementById("wd-props");
		els.yaml = document.getElementById("wd-yaml");
		els.yamlNote = document.getElementById("wd-yaml-note");
		els.graphBox = document.getElementById("wd-graph-box");
		els.yamlBox = document.getElementById("wd-yaml-box");
		els.agentDialog = document.getElementById("wd-agent-dialog");
		els.agentPath = document.getElementById("wd-agent-path");
		els.agentText = document.getElementById("wd-agent-text");
		els.agentMessage = document.getElementById("wd-agent-message");

		createGraph();
		bindToolbar();
		bindPalette();
		bindTabs();
		bindKeyboard();
		bindAgentDialog();

		window.addEventListener("beforeunload", function (e) {
			if (dirty) {
				e.preventDefault();
				e.returnValue = "";
			}
		});

		loadCatalog().then(function () {
			var wanted = decodeURIComponent((location.hash || "").replace(/^#/, ""));
			var first = findWorkflow(wanted) || catalog.workflows[0];
			if (first) {
				openWorkflow(first.id);
			} else {
				newWorkflow();
			}
		});
	}

	/* ====================================================================
	 * 서버 호출
	 * ==================================================================== */

	/**
	 * dstone-ai-engine의 정의 API 하나를 부릅니다.
	 * 검사에 걸린 경우({ok: false, message}, HTTP 200)는 그대로 돌려주고, 4xx/5xx는 message를 담은 Error로 돌려줍니다.
	 */
	function call(method, path, body) {
		return fetch(urls.callUrl, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ method: method, path: path, body: body || null })
		}).then(function (response) {
			return response.text().then(function (text) {
				var data = null;
				try {
					data = text ? JSON.parse(text) : null;
				} catch (e) {
					// JSON이 아니면(로그인 화면으로 넘어간 경우 등) 글자 그대로 보여 준다.
					data = { message: text.substring(0, 300) };
				}
				if (!response.ok) {
					throw new Error((data && data.message) || ("HTTP " + response.status));
				}
				return data;
			});
		});
	}

	function loadCatalog() {
		setStatus("목록을 읽는 중...");
		return call("GET", "/api/ai/definitions").then(function (data) {
			catalog = data;
			fillWorkflowSelect();
			document.getElementById("wd-save-btn").disabled = !catalog.editable;
			document.getElementById("wd-delete-btn").disabled = !catalog.editable;
			if (!catalog.editable) {
				showMessage("info", "이 엔진은 정의 파일 저장이 꺼져 있습니다(dstone.ai.definition.edit-enabled). 보기와 검사만 됩니다. 정의 폴더: " + catalog.location);
			}
			setStatus("");
		}).catch(function (e) {
			setStatus("");
			showMessage("error", "목록을 읽지 못했습니다: " + e.message);
		});
	}

	function fillWorkflowSelect() {
		var groups = {};
		var order = [];
		catalog.workflows.forEach(function (item) {
			var folder = item.path.substring(0, item.path.lastIndexOf("/"));
			if (!groups[folder]) {
				groups[folder] = [];
				order.push(folder);
			}
			groups[folder].push(item);
		});
		var html = "";
		if (doc && !doc.id) {
			html += "<option value=\"\">(새 Workflow - 저장 전)</option>";
		}
		order.forEach(function (folder) {
			html += "<optgroup label=\"" + escapeHtml(folder) + "\">";
			groups[folder].forEach(function (item) {
				html += "<option value=\"" + escapeHtml(item.id) + "\">" + escapeHtml(item.id) + " (step " + item.steps + ")</option>";
			});
			html += "</optgroup>";
		});
		els.workflow.innerHTML = html;
		els.workflow.value = doc && doc.id ? doc.id : "";
	}

	function findWorkflow(id) {
		for (var i = 0; i < catalog.workflows.length; i++) {
			if (catalog.workflows[i].id === id) {
				return catalog.workflows[i];
			}
		}
		return null;
	}

	function findAgent(id) {
		for (var i = 0; i < catalog.agents.length; i++) {
			if (catalog.agents[i].id === id) {
				return catalog.agents[i];
			}
		}
		return null;
	}

	function findTool(name) {
		for (var i = 0; i < catalog.tools.length; i++) {
			if (catalog.tools[i].name === name) {
				return catalog.tools[i];
			}
		}
		return null;
	}

	/* ====================================================================
	 * Workflow 열기 / 새로 만들기 / 저장
	 * ==================================================================== */

	function confirmDiscard() {
		return !dirty || window.confirm("저장하지 않은 변경이 있습니다. 버리고 계속할까요?");
	}

	function openWorkflow(id) {
		if (!confirmDiscard()) {
			els.workflow.value = doc && doc.id ? doc.id : "";
			return;
		}
		setStatus("읽는 중...");
		call("GET", "/api/ai/definitions/workflows/" + encodeURIComponent(id)).then(function (source) {
			setStatus("");
			loadDoc(source.text, { id: source.id, path: source.path, originalPath: source.path, layout: source.layout });
			history.replaceState(null, "", "#" + encodeURIComponent(source.id));
			showMessage("", "");
		}).catch(function (e) {
			setStatus("");
			showMessage("error", "Workflow를 읽지 못했습니다: " + e.message);
		});
	}

	/**
	 * 글자(YAML)로 Workflow를 엽니다.
	 * @param text YAML 글자
	 * @param info {id, path, originalPath, layout}
	 */
	function loadDoc(text, info) {
		var model;
		try {
			model = jsyaml.load(text);
		} catch (e) {
			model = null;
			showMessage("error", "YAML을 읽지 못해 그림을 그릴 수 없습니다. YAML 탭에서 고치십시오: " + e.message);
		}
		if (model !== null && (typeof model !== "object" || Array.isArray(model))) {
			model = null;
			showMessage("error", "파일 맨 위가 키: 값 모양이 아닙니다. YAML 탭에서 고치십시오.");
		}
		var split = splitHeader(text);
		doc = {
			id: info.id || null,
			originalPath: info.originalPath || null,
			model: model || { steps: [] },
			header: split.header,
			bodyComments: split.bodyComments
		};
		if (!Array.isArray(doc.model.steps)) {
			doc.model.steps = [];
		}
		positions = info.layout && info.layout.nodes ? info.layout.nodes : {};
		selected = null;
		undoStack = [];
		dirty = false;
		textEdited = false;
		els.path.value = info.path || "";
		els.yaml.value = text;
		fillWorkflowSelect();
		placeMissingNodes();
		redraw();
		showPanel();
		updatePathHint();
		updateYamlNote();
		graph.zoomToFit({ padding: 30, maxScale: 1 });
	}

	function newWorkflow() {
		if (!confirmDiscard()) {
			return;
		}
		var id = window.prompt("새 Workflow의 id를 적으십시오(영문, 숫자, -, _).", "new-workflow");
		if (!id) {
			return;
		}
		id = id.trim();
		var folder = currentFolder();
		var text = buildText("", {
			id: id,
			version: "1.0.0",
			description: "",
			input: "string",
			state: {},
			steps: []
		});
		loadDoc(text, { id: null, path: folder + "/" + id + ".yml", originalPath: null, layout: null });
		dirty = true;
		showMessage("info", "왼쪽 상자에서 step을 끌어다 놓고, 시작 동그라미 아래쪽을 끌어 첫 step에 놓으십시오. 저장하면 " + els.path.value + " 파일이 생깁니다.");
	}

	function copyWorkflow() {
		if (!doc) {
			return;
		}
		var id = window.prompt("복사본의 id를 적으십시오.", (doc.model.id || "workflow") + "-copy");
		if (!id) {
			return;
		}
		var model = clone(doc.model);
		model.id = id.trim();
		var keep = positions;
		var text = buildText(doc.header, model);
		loadDoc(text, { id: null, path: currentFolder() + "/" + model.id + ".yml", originalPath: null, layout: { nodes: clone(keep) } });
		dirty = true;
		showMessage("info", "복사본입니다. 저장하면 " + els.path.value + " 파일이 새로 생깁니다.");
	}

	function currentFolder() {
		var path = els.path.value || (doc && doc.originalPath) || "workflows/sample/x.yml";
		return path.substring(0, path.lastIndexOf("/")) || "workflows/sample";
	}

	function requestBody(withLayout) {
		var body = {
			path: els.path.value.trim(),
			originalPath: doc.originalPath,
			text: els.yaml.value
		};
		if (withLayout) {
			body.layout = { nodes: positions };
		}
		return body;
	}

	function checkWorkflow() {
		if (!doc) {
			return;
		}
		setStatus("검사하는 중...");
		call("POST", "/api/ai/definitions/workflows/check", requestBody(false)).then(function (result) {
			setStatus("");
			if (result.ok) {
				showMessage("ok", "검사를 통과했습니다. (Workflow " + result.workflows + "개, Agent " + result.agents + "개를 함께 검사)");
			} else {
				showMessage("error", result.message);
			}
		}).catch(function (e) {
			setStatus("");
			showMessage("error", e.message);
		});
	}

	function saveWorkflow() {
		if (!doc || !catalog.editable) {
			return;
		}
		if (textEdited && !window.confirm("YAML 탭에서 고친 글자를 그대로 저장합니다(그림에는 아직 반영하지 않았습니다). 저장할까요?")) {
			return;
		}
		setStatus("저장하는 중...");
		call("POST", "/api/ai/definitions/workflows/save", requestBody(true)).then(function (result) {
			setStatus("");
			if (!result.ok) {
				showMessage("error", "저장하지 않았습니다. " + result.message);
				return;
			}
			var savedId = doc.model.id;
			if (textEdited) {
				try {
					savedId = jsyaml.load(els.yaml.value).id;
				} catch (e) {
					// 서버가 읽은 글자라 여기서 틀릴 일은 거의 없다. 틀리면 화면의 id를 쓴다.
				}
			}
			doc.id = savedId;
			doc.originalPath = result.path;
			dirty = false;
			showMessage("ok", "저장하고 엔진에 바로 반영했습니다: " + result.path);
			loadCatalog().then(function () {
				if (textEdited) {
					openWorkflow(savedId);
				} else {
					updatePathHint();
				}
			});
			history.replaceState(null, "", "#" + encodeURIComponent(savedId));
		}).catch(function (e) {
			setStatus("");
			showMessage("error", e.message);
		});
	}

	function deleteWorkflow() {
		if (!doc || !catalog.editable) {
			return;
		}
		if (!doc.originalPath) {
			showMessage("info", "아직 저장하지 않은 Workflow입니다. 다른 Workflow를 고르면 사라집니다.");
			return;
		}
		if (!window.confirm(doc.originalPath + " 파일을 지웁니다. 엔진에서도 바로 빠집니다. 지울까요?")) {
			return;
		}
		setStatus("지우는 중...");
		call("POST", "/api/ai/definitions/workflows/delete", { path: doc.originalPath }).then(function (result) {
			setStatus("");
			if (!result.ok) {
				showMessage("error", result.message);
				return;
			}
			dirty = false;
			showMessage("ok", "지웠습니다: " + result.path);
			doc = null;
			loadCatalog().then(function () {
				if (catalog.workflows.length > 0) {
					openWorkflow(catalog.workflows[0].id);
				}
			});
		}).catch(function (e) {
			setStatus("");
			showMessage("error", e.message);
		});
	}

	function reloadEngine() {
		setStatus("엔진이 다시 읽는 중...");
		call("POST", "/api/ai/definitions/reload").then(function (result) {
			setStatus("");
			if (result.ok) {
				showMessage("ok", "엔진이 정의 폴더를 다시 읽었습니다(Workflow " + result.workflows + "개, Agent " + result.agents + "개).");
			} else {
				showMessage("error", result.message);
			}
			return loadCatalog();
		}).catch(function (e) {
			setStatus("");
			showMessage("error", e.message);
		});
	}

	/* ====================================================================
	 * YAML 글자 만들기
	 * ==================================================================== */

	/** 파일 맨 위의 주석(과 빈 줄)을 떼어 냅니다. 그 아래에 남은 주석 줄 수도 셉니다. */
	function splitHeader(text) {
		var lines = String(text || "").split(/\r?\n/);
		var i = 0;
		while (i < lines.length && (lines[i].trim() === "" || lines[i].trim().charAt(0) === "#")) {
			i++;
		}
		var header = lines.slice(0, i).join("\n").replace(/\s+$/, "");
		var bodyComments = 0;
		for (var j = i; j < lines.length; j++) {
			if (/^\s*#/.test(lines[j]) || /\s#\s/.test(lines[j])) {
				bodyComments++;
			}
		}
		return { header: header, bodyComments: bodyComments };
	}

	/** 맨 위 주석 + model을 YAML 글자로 만듭니다. step 사이에는 빈 줄을 둡니다. */
	function buildText(header, model) {
		var body = jsyaml.dump(model, { lineWidth: -1, noRefs: true, quotingType: "\"" });
		body = body.replace(/\n  - id: /g, "\n\n  - id: ");
		return (header ? header + "\n" : "") + body;
	}

	/* ====================================================================
	 * model 고치기
	 * ==================================================================== */

	function steps() {
		return doc.model.steps;
	}

	function stepById(id) {
		var list = steps();
		for (var i = 0; i < list.length; i++) {
			if (list[i] && list[i].id === id) {
				return list[i];
			}
		}
		return null;
	}

	/**
	 * model을 고칩니다. 고치기 전 상태를 되돌리기용으로 남기고, 고친 뒤 글자를 다시 만들고 다시 그립니다.
	 * @param fn 실제로 고치는 함수. false를 돌려주면 고친 것이 없는 것으로 봅니다
	 */
	function change(fn) {
		if (textEdited && !window.confirm("YAML 탭에서 고친 글자가 아직 그림에 반영되지 않았습니다. 그림을 고치면 그 글자는 버립니다. 계속할까요?")) {
			showPanel();
			return;
		}
		var before = JSON.stringify({ model: doc.model, positions: positions });
		var result = fn();
		if (result === false) {
			showPanel();
			return;
		}
		undoStack.push(before);
		if (undoStack.length > 50) {
			undoStack.shift();
		}
		afterModelChange();
	}

	/**
	 * 그림과 설정 칸을 다시 그리지 않고 model만 고칩니다(되돌리기와 글자 다시 만들기는 change()와 같습니다).
	 * 설정 칸 안의 편집기처럼, 다시 그리면 입력 중인 칸이 사라지는 곳에서 씁니다.
	 */
	function quietChange(fn) {
		if (textEdited && !window.confirm("YAML 탭에서 고친 글자가 아직 그림에 반영되지 않았습니다. 고치면 그 글자는 버립니다. 계속할까요?")) {
			return;
		}
		undoStack.push(JSON.stringify({ model: doc.model, positions: positions }));
		if (undoStack.length > 50) {
			undoStack.shift();
		}
		fn();
		els.yaml.value = buildText(doc.header, doc.model);
		textEdited = false;
		dirty = true;
		updateYamlNote();
	}

	function afterModelChange() {
		els.yaml.value = buildText(doc.header, doc.model);
		textEdited = false;
		dirty = true;
		redraw();
		showPanel();
		updateYamlNote();
	}

	function undo() {
		if (undoStack.length === 0) {
			setStatus("되돌릴 것이 없습니다.");
			return;
		}
		var before = JSON.parse(undoStack.pop());
		doc.model = before.model;
		positions = before.positions;
		if (selected && selected.type === "step" && !stepById(selected.id)) {
			selected = null;
		}
		afterModelChange();
	}

	/**
	 * 비어 있는 값이면 키를 지우고, 아니면 넣습니다. "a.b.c" 모양이면 그 깊이에 넣고, 없는 묶음은 만듭니다.
	 * 지운 뒤 비어 버린 묶음(approval: {}, context: {retrieval: {}})은 위로 올라가며 함께 지웁니다.
	 */
	function setValue(target, path, value) {
		var keys = path.split(".");
		var owners = [target];
		for (var i = 0; i < keys.length - 1; i++) {
			var owner = owners[i];
			if (!owner[keys[i]] || typeof owner[keys[i]] !== "object" || Array.isArray(owner[keys[i]])) {
				if (isEmpty(value)) {
					return;
				}
				owner[keys[i]] = {};
			}
			owners.push(owner[keys[i]]);
		}
		var last = keys[keys.length - 1];
		if (isEmpty(value)) {
			delete owners[keys.length - 1][last];
		} else {
			owners[keys.length - 1][last] = value;
		}
		for (var j = keys.length - 2; j >= 0; j--) {
			var child = owners[j][keys[j]];
			if (child && typeof child === "object" && !Array.isArray(child) && Object.keys(child).length === 0) {
				delete owners[j][keys[j]];
			} else {
				break;
			}
		}
	}

	function getValue(target, path) {
		var keys = path.split(".");
		var value = target;
		for (var i = 0; i < keys.length; i++) {
			if (value === null || value === undefined || typeof value !== "object") {
				return undefined;
			}
			value = value[keys[i]];
		}
		return value;
	}

	function isEmpty(value) {
		return value === undefined || value === null || value === ""
			|| (Array.isArray(value) && value.length === 0)
			|| (typeof value === "object" && !Array.isArray(value) && Object.keys(value).length === 0);
	}

	/** stepNN 모양으로 아직 쓰지 않은 다음 번호를 만듭니다. */
	function nextStepId() {
		var max = -1;
		steps().forEach(function (step) {
			var m = /^step(\d+)$/.exec(step && step.id ? step.id : "");
			if (m) {
				max = Math.max(max, parseInt(m[1], 10));
			}
		});
		var n = max < 0 ? steps().length : max + 1;
		var id = "step" + (n < 10 ? "0" + n : String(n));
		while (stepById(id)) {
			n++;
			id = "step" + (n < 10 ? "0" + n : String(n));
		}
		return id;
	}

	/** 끌어다 놓은 자리에 새 step을 만듭니다. */
	function addStep(type, position) {
		change(function () {
			var id = nextStepId();
			var step = { id: id, type: type };
			// agent/tool은 오른쪽 칸에서 고르게 비워 두고, 거의 늘 쓰는 값만 미리 넣는다.
			if (type === "AGENT" || type === "SUPERVISOR" || type === "ROUTER") {
				step["with"] = "${input}";
			} else if (type === "APPROVAL") {
				step.approval = { approverRole: "PL" };
			}
			steps().push(step);
			positions[id] = { x: Math.round(position.x), y: Math.round(position.y) };
			selected = { type: "step", id: id };
		});
	}

	/** step의 바깥으로 나가는 화살표 목록입니다. [{kind, route, target}] */
	function outgoing(step) {
		var list = [];
		if (!step) {
			return list;
		}
		if (step.next) {
			list.push({ kind: "next", route: null, target: String(step.next) });
		}
		if (step.onFailure) {
			list.push({ kind: "onFailure", route: null, target: String(step.onFailure) });
		}
		if (step.routes && typeof step.routes === "object") {
			Object.keys(step.routes).forEach(function (name) {
				list.push({ kind: "route", route: name, target: String(step.routes[name]) });
			});
		}
		if (step.approval && step.approval.rejectTo) {
			list.push({ kind: "rejectTo", route: null, target: String(step.approval.rejectTo) });
		}
		return list;
	}

	/** 이 step 종류가 쓸 수 있는 화살표 종류입니다. */
	function edgeKindsOf(type) {
		if (type === "ROUTER") {
			return ["route", "onFailure"];
		}
		if (type === "APPROVAL") {
			return ["route"];
		}
		return ["next", "onFailure"];
	}

	/** 화살표 하나를 model에서 지웁니다. */
	function removeEdge(step, kind, route) {
		if (kind === "next") {
			delete step.next;
		} else if (kind === "onFailure") {
			delete step.onFailure;
		} else if (kind === "route" && step.routes) {
			delete step.routes[route];
		} else if (kind === "rejectTo" && step.approval) {
			delete step.approval.rejectTo;
		}
	}

	/** 아직 쓰지 않은 경로 이름을 하나 만듭니다(APPROVAL은 APPROVED, REJECTED부터). */
	function newRouteName(step) {
		var routes = step.routes || {};
		if (step.type === "APPROVAL") {
			if (!routes.APPROVED) {
				return "APPROVED";
			}
			if (!routes.REJECTED && !(step.approval && step.approval.rejectTo)) {
				return "REJECTED";
			}
		}
		var n = 1;
		while (routes["경로" + n]) {
			n++;
		}
		return "경로" + n;
	}

	/** 그림에서 화살표를 이었을 때: 시작이면 첫 step을 바꾸고, 아니면 step 종류에 맞는 자리(next → onFailure, 또는 routes)에 넣습니다. */
	function connect(from, to) {
		if (from === START_ID) {
			change(function () {
				var step = stepById(to);
				if (!step) {
					return false;
				}
				var list = steps();
				list.splice(list.indexOf(step), 1);
				list.unshift(step);
			});
			return;
		}
		var source = stepById(from);
		if (!source) {
			redraw();
			return;
		}
		change(function () {
			if (source.type === "ROUTER" || source.type === "APPROVAL") {
				var name = newRouteName(source);
				source.routes = source.routes || {};
				source.routes[name] = to;
				selected = { type: "edge", from: from, kind: "route", route: name };
				return;
			}
			if (!source.next) {
				source.next = to;
				selected = { type: "edge", from: from, kind: "next", route: null };
			} else if (!source.onFailure) {
				source.onFailure = to;
				selected = { type: "edge", from: from, kind: "onFailure", route: null };
			} else {
				showMessage("error", from + " step에는 next와 onFailure가 이미 있습니다. 바꿀 화살표를 골라 지운 뒤 다시 이으십시오.");
				return false;
			}
		});
	}

	/** step id를 바꿉니다. 이 step을 가리키는 화살표와 위치도 함께 바꿉니다. */
	function renameStep(oldId, newId) {
		if (newId === oldId) {
			return;
		}
		if (!STEP_ID_PATTERN.test(newId) || newId === END_ID || newId === FAIL_ID) {
			showMessage("error", "step id는 영문, 숫자, 밑줄(_)만 쓰고 숫자로 시작할 수 없습니다(END, FAIL은 예약어): " + newId);
			showPanel();
			return;
		}
		if (stepById(newId)) {
			showMessage("error", "이미 있는 step id입니다: " + newId);
			showPanel();
			return;
		}
		change(function () {
			steps().forEach(function (step) {
				if (step.id === oldId) {
					step.id = newId;
				}
				if (step.next === oldId) {
					step.next = newId;
				}
				if (step.onFailure === oldId) {
					step.onFailure = newId;
				}
				if (step.routes) {
					Object.keys(step.routes).forEach(function (name) {
						if (step.routes[name] === oldId) {
							step.routes[name] = newId;
						}
					});
				}
				if (step.approval && step.approval.rejectTo === oldId) {
					step.approval.rejectTo = newId;
				}
			});
			if (positions[oldId]) {
				positions[newId] = positions[oldId];
				delete positions[oldId];
			}
			selected = { type: "step", id: newId };
		});
	}

	/** 고른 것(step 또는 화살표)을 지웁니다. step을 지우면 그 step을 가리키던 화살표도 지웁니다. */
	function deleteSelected() {
		if (!selected) {
			return;
		}
		if (selected.type === "step") {
			var id = selected.id;
			change(function () {
				var list = steps();
				list.splice(list.indexOf(stepById(id)), 1);
				list.forEach(function (step) {
					outgoing(step).forEach(function (edge) {
						if (edge.target === id) {
							removeEdge(step, edge.kind, edge.route);
						}
					});
				});
				delete positions[id];
				selected = null;
			});
		} else if (selected.type === "edge") {
			var edge = selected;
			change(function () {
				var step = stepById(edge.from);
				if (!step) {
					return false;
				}
				removeEdge(step, edge.kind, edge.route);
				selected = { type: "step", id: edge.from };
			});
		}
	}

	/** step들이 저장하는 이름을 모읍니다. 이름 → 저장하는 step id 목록 */
	function savedNames() {
		var names = {};
		steps().forEach(function (step) {
			["save", "saveError", "saveInput", "saveEach"].forEach(function (key) {
				var name = step[key];
				if (name) {
					name = String(name).trim();
					names[name] = names[name] || [];
					names[name].push(step.id);
				}
			});
		});
		return names;
	}

	/** state 선언을 step들이 저장하는 이름에 맞춥니다(빠진 이름은 넣고, 아무도 저장하지 않는 이름은 지웁니다). */
	function tidyState() {
		var names = savedNames();
		var state = doc.model.state && typeof doc.model.state === "object" ? doc.model.state : {};
		var unused = Object.keys(state).filter(function (name) {
			return !names[name];
		});
		var missing = Object.keys(names).filter(function (name) {
			return !Object.prototype.hasOwnProperty.call(state, name);
		});
		if (unused.length === 0 && missing.length === 0) {
			showMessage("ok", "state 선언이 step들이 저장하는 이름과 이미 맞습니다.");
			return;
		}
		if (unused.length > 0 && !window.confirm("아무 step도 저장하지 않는 이름을 state에서 지웁니다: " + unused.join(", ") + "\n계속할까요?")) {
			unused = [];
		}
		change(function () {
			var next = {};
			Object.keys(state).forEach(function (name) {
				if (unused.indexOf(name) < 0) {
					next[name] = state[name];
				}
			});
			missing.forEach(function (name) {
				next[name] = names[name].join(", ") + " step이 저장";
			});
			doc.model.state = next;
		});
	}

	/** save 칸에 새 이름을 적으면 state 선언에도 넣습니다(엔진은 선언하지 않은 이름에 저장하면 거절합니다). */
	function declareState(name, stepId, key) {
		if (!name) {
			return;
		}
		if (!doc.model.state || typeof doc.model.state !== "object") {
			doc.model.state = {};
		}
		if (!Object.prototype.hasOwnProperty.call(doc.model.state, name)) {
			var what = { save: "결과", saveError: "실패 이유", saveInput: "받은 입력", saveEach: "반복별 결과" }[key] || "결과";
			doc.model.state[name] = stepId + " step의 " + what;
		}
	}

	/* ====================================================================
	 * 그림
	 * ==================================================================== */

	var PORT_GROUPS = {
		"in": {
			position: "top",
			attrs: { circle: { r: 5, magnet: "passive", stroke: "#8a8f86", strokeWidth: 1, fill: "#fff" } }
		},
		out: {
			position: "bottom",
			attrs: { circle: { r: 7, magnet: true, stroke: "#007b9d", strokeWidth: 2, fill: "#fff", cursor: "crosshair" } }
		}
	};

	function createGraph() {
		graph = new X6.Graph({
			container: els.canvas,
			// YAML 탭으로 바꾸면 그림 칸이 숨어 크기가 0이 된다. 자동 크기 맞춤은 그때 크기 계산이 망가지므로, 보일 때만 직접 맞춘다(fitCanvas).
			autoResize: false,
			width: els.canvas.clientWidth,
			height: els.canvas.clientHeight,
			background: { color: "#fafaf8" },
			grid: { size: 10, visible: true },
			panning: { enabled: true },
			mousewheel: { enabled: true, modifiers: ["ctrl", "meta"], minScale: 0.2, maxScale: 2 },
			interacting: {
				edgeMovable: false,
				arrowheadMovable: false,
				vertexMovable: false,
				vertexAddable: false,
				vertexDeletable: false,
				edgeLabelMovable: false
			},
			connecting: {
				snap: { radius: 30 },
				allowBlank: false,
				allowLoop: true,
				allowNode: true,
				allowEdge: false,
				allowPort: true,
				allowMulti: true,
				highlight: true,
				createEdge: function () {
					var line = LINE_STYLES[lineStyle];
					return this.createEdge({
						shape: "edge",
						router: line.router,
						connector: line.connector,
						attrs: { line: { stroke: "#007b9d", strokeWidth: 1.6, targetMarker: { name: "block", width: 10, height: 7 } } }
					});
				},
				validateConnection: function (args) {
					if (!args.targetCell || args.sourcePort !== "out") {
						return false;
					}
					var target = args.targetCell.id;
					return target !== START_ID && String(target).indexOf("missing:") !== 0;
				}
			},
			highlighting: {
				magnetAdsorbed: { name: "stroke", args: { attrs: { fill: "#007b9d", stroke: "#007b9d" } } }
			}
		});
		window.addEventListener("resize", fitCanvas);

		dnd = new X6.Dnd({
			target: graph,
			scaled: false,
			validateNode: function (node) {
				// 그림에 바로 넣지 않고 model에 step을 만든 뒤 다시 그린다.
				addStep(node.getData().type, node.getPosition());
				return false;
			}
		});

		graph.on("node:click", function (args) {
			var data = args.node.getData() || {};
			selected = data.kind === "step" ? { type: "step", id: args.node.id } : null;
			redraw();
			showPanel();
		});
		graph.on("edge:click", function (args) {
			var data = args.edge.getData() || {};
			if (data.kind === "start") {
				selected = null;
			} else {
				selected = { type: "edge", from: data.from, kind: data.kind, route: data.route };
			}
			redraw();
			showPanel();
		});
		graph.on("blank:click", function () {
			if (selected) {
				selected = null;
				redraw();
				showPanel();
			}
		});
		graph.on("edge:connected", function (args) {
			if (!args.isNew) {
				return;
			}
			var from = args.edge.getSourceCellId();
			var to = args.edge.getTargetCellId();
			args.edge.remove();
			if (from && to) {
				connect(from, to);
			}
		});
		graph.on("node:moved", function (args) {
			var p = args.node.getPosition();
			positions[args.node.id] = { x: Math.round(p.x), y: Math.round(p.y) };
			dirty = true;
		});
	}

	/** 그림 칸이 보일 때만 그림 크기를 칸에 맞춥니다. */
	function fitCanvas() {
		var width = els.canvas.clientWidth;
		var height = els.canvas.clientHeight;
		if (graph && width > 0 && height > 0) {
			graph.resize(width, height);
		}
	}

	function isSelectedStep(id) {
		return selected && selected.type === "step" && selected.id === id;
	}

	function isSelectedEdge(from, kind, route) {
		return selected && selected.type === "edge" && selected.from === from && selected.kind === kind && (selected.route || null) === (route || null);
	}

	function shorten(text, max) {
		text = String(text === undefined || text === null ? "" : text);
		return text.length > max ? text.substring(0, max - 1) + "…" : text;
	}

	function stepNodeConfig(step, position) {
		var color = TYPE_COLORS[step.type] || { stroke: "#878e83", fill: "#fff" };
		var target = step.agent || step.tool || (step.approval && step.approval.approverRole ? "승인자 " + step.approval.approverRole : "");
		var second = (step.type || "?") + (target ? " · " + target : "") + (step.forEach ? "  ↻" : "");
		return {
			id: step.id,
			shape: "rect",
			x: position.x,
			y: position.y,
			width: STEP_W,
			height: STEP_H,
			// 화살표가 네모 위를 지나도 네모를 누를 수 있게 네모를 화살표보다 위에 그린다.
			zIndex: 2,
			data: { kind: "step", type: step.type },
			attrs: {
				body: { fill: color.fill, stroke: color.stroke, strokeWidth: isSelectedStep(step.id) ? 3 : 1.5, rx: 8, ry: 8 },
				label: { text: shorten(step.id, 30) + "\n" + shorten(second, 34), fill: "#283121", fontSize: 12 }
			},
			ports: { groups: PORT_GROUPS, items: [{ id: "in", group: "in" }, { id: "out", group: "out" }] }
		};
	}

	function terminalNodeConfig(id, text, color, ports) {
		var p = positions[id] || { x: 0, y: 0 };
		var items = [];
		if (ports.indexOf("in") >= 0) {
			items.push({ id: "in", group: "in" });
		}
		if (ports.indexOf("out") >= 0) {
			items.push({ id: "out", group: "out" });
		}
		return {
			id: id,
			shape: "circle",
			x: p.x,
			y: p.y,
			width: TERM_SIZE,
			height: TERM_SIZE,
			zIndex: 2,
			data: { kind: "terminal" },
			attrs: {
				body: { fill: "#fff", stroke: color, strokeWidth: 2 },
				label: { text: text, fill: color, fontSize: 12, fontWeight: "bold" }
			},
			ports: { groups: PORT_GROUPS, items: items }
		};
	}

	function edgeConfig(from, to, kind, route) {
		var style = EDGE_STYLES[kind] || EDGE_STYLES.next;
		var label = kind === "route" ? route : (kind === "onFailure" ? "실패" : (kind === "rejectTo" ? "REJECTED" : ""));
		var sourceStep = stepById(from);
		if (kind === "next" && sourceStep && sourceStep.type === "SUPERVISOR") {
			label = "통과";
		} else if (kind === "onFailure" && sourceStep && sourceStep.type === "SUPERVISOR") {
			label = "불합격";
		}
		var selectedNow = isSelectedEdge(from, kind, route);
		return {
			shape: "edge",
			id: "edge|" + from + "|" + kind + "|" + (route || ""),
			source: { cell: from, port: "out" },
			target: { cell: to, port: "in" },
			router: LINE_STYLES[lineStyle].router,
			connector: LINE_STYLES[lineStyle].connector,
			zIndex: 1,
			data: { kind: kind, from: from, route: route },
			attrs: {
				line: {
					stroke: selectedNow ? "#007b9d" : style.stroke,
					strokeWidth: selectedNow ? 3 : 1.6,
					strokeDasharray: style.dash,
					targetMarker: { name: "block", width: 10, height: 7 }
				}
			},
			labels: label ? [{ attrs: { text: { text: label, fill: style.stroke, fontSize: 11 } } }] : []
		};
	}

	/** model을 그림으로 통째로 다시 그립니다. */
	function redraw() {
		if (!doc) {
			return;
		}
		var cells = [];
		var ids = {};
		cells.push(graph.createNode(terminalNodeConfig(START_ID, "시작", "#007b9d", ["out"])));
		cells.push(graph.createNode(terminalNodeConfig(END_ID, "END", "#15803d", ["in"])));
		cells.push(graph.createNode(terminalNodeConfig(FAIL_ID, "FAIL", "#b91c1c", ["in"])));
		steps().forEach(function (step) {
			if (!step || !step.id || ids[step.id]) {
				return;
			}
			ids[step.id] = true;
			cells.push(graph.createNode(stepNodeConfig(step, positions[step.id] || { x: 0, y: 0 })));
		});
		var missing = {};
		if (steps().length > 0 && steps()[0] && steps()[0].id) {
			var startEdge = edgeConfig(START_ID, steps()[0].id, "start", null);
			startEdge.id = "edge|start";
			cells.push(graph.createEdge(startEdge));
		}
		steps().forEach(function (step) {
			outgoing(step).forEach(function (edge) {
				var target = edge.target;
				if (hideFailEdges && target === FAIL_ID && edge.kind !== "route") {
					return;
				}
				if (!ids[target] && target !== END_ID && target !== FAIL_ID) {
					// 없는 step을 가리키면 빨간 점선 상자로 보여 준다(엔진 검사에서도 걸립니다).
					var missingId = "missing:" + target;
					if (!missing[missingId]) {
						missing[missingId] = true;
						var p = positions[step.id] || { x: 0, y: 0 };
						cells.push(graph.createNode({
							id: missingId, shape: "rect", x: p.x + 40, y: p.y + STEP_H + 50, width: 150, height: 40, zIndex: 2,
							data: { kind: "missing" },
							attrs: {
								body: { fill: "#fff", stroke: "#b91c1c", strokeDasharray: "4 3", rx: 6, ry: 6 },
								label: { text: "없는 step: " + shorten(target, 14), fill: "#b91c1c", fontSize: 11 }
							},
							ports: { groups: PORT_GROUPS, items: [{ id: "in", group: "in" }] }
						}));
					}
					target = missingId;
				}
				cells.push(graph.createEdge(edgeConfig(step.id, target, edge.kind, edge.route)));
			});
		});
		graph.resetCells(cells);
	}

	/** 위치가 없는 노드에 자리를 줍니다. 위치가 하나도 없으면 전체를 자동 배치합니다. */
	function placeMissingNodes() {
		var all = [START_ID, END_ID, FAIL_ID];
		steps().forEach(function (step) {
			if (step && step.id) {
				all.push(step.id);
			}
		});
		var missing = all.filter(function (id) {
			return !positions[id];
		});
		if (missing.length === 0) {
			return;
		}
		if (missing.length === all.length || missing.length > 3) {
			autoLayout();
			return;
		}
		var bottom = 0;
		Object.keys(positions).forEach(function (id) {
			bottom = Math.max(bottom, positions[id].y);
		});
		missing.forEach(function (id, index) {
			positions[id] = { x: 40 + index * (STEP_W + 40), y: bottom + 120 };
		});
	}

	/** dagre로 위에서 아래로 자동 배치합니다. */
	function autoLayout() {
		var g = new dagre.graphlib.Graph();
		g.setGraph({ rankdir: "TB", nodesep: 50, ranksep: 70, marginx: 20, marginy: 20 });
		g.setDefaultEdgeLabel(function () {
			return {};
		});
		g.setNode(START_ID, { width: TERM_SIZE, height: TERM_SIZE });
		g.setNode(END_ID, { width: TERM_SIZE, height: TERM_SIZE });
		g.setNode(FAIL_ID, { width: TERM_SIZE, height: TERM_SIZE });
		var ids = {};
		steps().forEach(function (step) {
			if (step && step.id) {
				ids[step.id] = true;
				g.setNode(step.id, { width: STEP_W, height: STEP_H });
			}
		});
		if (steps().length > 0 && steps()[0] && steps()[0].id) {
			g.setEdge(START_ID, steps()[0].id);
		}
		steps().forEach(function (step) {
			outgoing(step).forEach(function (edge) {
				if (ids[edge.target] || edge.target === END_ID || edge.target === FAIL_ID) {
					g.setEdge(step.id, edge.target);
				}
			});
		});
		dagre.layout(g);
		g.nodes().forEach(function (id) {
			var n = g.node(id);
			positions[id] = { x: Math.round(n.x - n.width / 2), y: Math.round(n.y - n.height / 2) };
		});
	}

	/* ====================================================================
	 * 오른쪽 설정 칸
	 * ==================================================================== */

	function showPanel() {
		if (!doc) {
			els.props.innerHTML = "";
			return;
		}
		if (selected && selected.type === "step" && stepById(selected.id)) {
			showStepPanel(stepById(selected.id));
		} else if (selected && selected.type === "edge" && stepById(selected.from)) {
			showEdgePanel(selected);
		} else {
			selected = null;
			showWorkflowPanel();
		}
	}

	/** 값 하나를 고칠 수 있는 칸(YAML 표기)의 글자를 만듭니다. 글자 값은 그대로, 묶음은 YAML로 보여 줍니다. */
	function valueToText(value) {
		if (value === undefined || value === null) {
			return "";
		}
		if (typeof value === "object") {
			return jsyaml.dump(value, { lineWidth: -1, noRefs: true, quotingType: "\"" }).replace(/\n$/, "");
		}
		return String(value);
	}

	/** 칸의 글자를 값으로 바꿉니다. 키: 값이나 목록으로 읽히면 묶음으로, 아니면 글자 그대로 둡니다. */
	function textToValue(text) {
		var trimmed = String(text || "").trim();
		if (trimmed === "") {
			return undefined;
		}
		try {
			var parsed = jsyaml.load(trimmed);
			if (parsed !== null && typeof parsed === "object") {
				return parsed;
			}
		} catch (e) {
			throw new Error("YAML로 읽을 수 없습니다: " + e.message);
		}
		return trimmed;
	}

	/** JSON Schema를 한두 줄 설명으로 줄입니다(Agent/Tool 입력 모양 안내용). */
	function schemaText(schema) {
		if (!schema) {
			return "";
		}
		var type = Array.isArray(schema.type) ? schema.type[0] : schema.type;
		if (type === "object" && schema.properties) {
			var required = schema.required || [];
			return Object.keys(schema.properties).map(function (name) {
				var p = schema.properties[name] || {};
				var t = Array.isArray(p.type) ? p.type[0] : (p.type || "any");
				return name + (required.indexOf(name) >= 0 ? "" : "?") + ": " + t + (p.description ? " - " + p.description : "");
			}).join("\n");
		}
		return (type || "any") + (schema.description ? " - " + schema.description : "");
	}

	function row(label, help, control) {
		return "<div class=\"wd-field\"><label>" + escapeHtml(label) + (help ? " <span class=\"wd-help\">" + escapeHtml(help) + "</span>" : "") + "</label>" + control + "</div>";
	}

	function showWorkflowPanel() {
		var m = doc.model;
		var settings = m.settings || {};
		var html = "<h4>Workflow 설정</h4>"
			+ "<p class=\"ai-hint\">step이나 화살표를 누르면 그것의 설정이 나옵니다.</p>"
			+ row("id", "엔진에 등록되는 이름", "<input type=\"text\" data-wf=\"id\" value=\"" + escapeHtml(m.id || "") + "\" />")
			+ row("version", "예: 1.0.0", "<input type=\"text\" data-wf=\"version\" value=\"" + escapeHtml(m.version === undefined ? "" : String(m.version)) + "\" />")
			+ row("description", "", "<textarea data-wf=\"description\" rows=\"2\">" + escapeHtml(m.description || "") + "</textarea>")
			+ row("allowedCallers", "쉼표로 구분, 비우면 누구나", "<input type=\"text\" data-wf=\"allowedCallers\" value=\"" + escapeHtml((m.allowedCallers || []).join(", ")) + "\" />")
			+ row("settings.maxIterations", "step 실행 횟수 한도", "<input type=\"number\" min=\"1\" data-wf=\"settings.maxIterations\" value=\"" + escapeHtml(settings.maxIterations === undefined ? "" : String(settings.maxIterations)) + "\" />")
			+ row("settings.checkpoint", "", "<label class=\"wd-check\"><input type=\"checkbox\" data-wf=\"settings.checkpoint\"" + (settings.checkpoint ? " checked" : "") + " /> step마다 상태 저장</label>")
			+ row("input", "실행 요청의 input 모양", "<div class=\"pe-host\" id=\"wd-wf-input\"></div>")
			+ row("state", "저장하는 이름: 설명", "<textarea data-wf=\"state\" rows=\"5\" class=\"wd-code\">" + escapeHtml(valueToText(m.state)) + "</textarea>"
				+ "<div class=\"wd-inline-actions\"><button type=\"button\" class=\"kn-plain\" id=\"wd-tidy-state-btn\">step의 save에 맞추기</button></div>")
			+ row("return", "끝났을 때 돌려줄 값. 비우면 state 전체", "<textarea data-wf=\"return\" rows=\"3\" class=\"wd-code\">" + escapeHtml(valueToText(m["return"])) + "</textarea>")
			+ "<p class=\"ai-hint\">step " + steps().length + "개</p>";
		els.props.innerHTML = html;
		Array.prototype.forEach.call(els.props.querySelectorAll("[data-wf]"), function (input) {
			input.addEventListener("change", function () {
				var key = input.getAttribute("data-wf");
				var value;
				try {
					value = readWorkflowField(key, input);
				} catch (e) {
					showMessage("error", key + " - " + e.message);
					return;
				}
				change(function () {
					setValue(doc.model, key, value);
				});
			});
		});
		document.getElementById("wd-tidy-state-btn").addEventListener("click", tidyState);
		// input 모양은 Picoschema 편집기가 맡는다. 그림과 상관없는 값이라 설정 칸을 다시 그리지 않고 글자만 바꾼다(커서가 사라지지 않게).
		DstonePicoschemaEditor.create(document.getElementById("wd-wf-input"), m.input, {
			noneLabel: "적지 않음(기본: 글자 하나)",
			noneHint: "실행 요청의 input으로 글자 하나(string)를 받습니다.",
			compact: true,
			onChange: function (value) {
				quietChange(function () {
					setValue(doc.model, "input", value);
				});
			}
		});
	}

	function readWorkflowField(key, input) {
		if (key === "settings.checkpoint") {
			return input.checked ? true : undefined;
		}
		if (key === "settings.maxIterations") {
			return input.value === "" ? undefined : parseInt(input.value, 10);
		}
		if (key === "allowedCallers") {
			var list = input.value.split(",").map(function (v) {
				return v.trim();
			}).filter(function (v) {
				return v !== "";
			});
			return list.length > 0 ? list : undefined;
		}
		if (key === "input" || key === "state" || key === "return") {
			var value = textToValue(input.value);
			if (key === "state" && value !== undefined && (typeof value !== "object" || Array.isArray(value))) {
				throw new Error("state는 이름: 설명 모양이어야 합니다.");
			}
			return value;
		}
		return input.value.trim() === "" ? undefined : input.value.trim();
	}

	function stateOptions() {
		var names = Object.keys(doc.model.state && typeof doc.model.state === "object" ? doc.model.state : {});
		return "<datalist id=\"wd-state-names\">" + names.map(function (name) {
			return "<option value=\"" + escapeHtml(name) + "\"></option>";
		}).join("") + "</datalist>";
	}

	function agentSelect(step) {
		var current = step.agent || "";
		var engineFixed = step.type === "SUPERVISOR" || step.type === "ROUTER";
		var html = "<select data-step=\"agent\"><option value=\"\">(고르십시오)</option>";
		var found = false;
		catalog.agents.forEach(function (agent) {
			if (engineFixed && agent.declaresOutput && agent.id !== current) {
				return;
			}
			if (agent.id === current) {
				found = true;
			}
			html += "<option value=\"" + escapeHtml(agent.id) + "\"" + (agent.id === current ? " selected" : "") + ">" + escapeHtml(agent.id) + "</option>";
		});
		if (current && !found) {
			html += "<option value=\"" + escapeHtml(current) + "\" selected>" + escapeHtml(current) + " (등록되지 않음)</option>";
		}
		html += "</select>";
		var agent = findAgent(current);
		if (agent) {
			html += "<p class=\"wd-desc\">" + escapeHtml(agent.description || "") + "</p>"
				+ "<pre class=\"wd-schema\">받는 값\n" + escapeHtml(schemaText(agent.input)) + "</pre>";
		}
		if (engineFixed) {
			html += "<p class=\"ai-hint\">" + step.type + " step에는 output을 선언하지 않은 Agent만 씁니다(답의 모양은 엔진이 정합니다).</p>";
		}
		html += "<div class=\"wd-inline-actions\">"
			+ (agent ? "<button type=\"button\" class=\"kn-plain\" id=\"wd-open-agent-btn\">Agent 파일 열기</button>" : "")
			+ "<button type=\"button\" class=\"kn-plain\" id=\"wd-new-agent-btn\">새 Agent</button></div>";
		return html;
	}

	function toolSelect(step) {
		var current = step.tool || "";
		var html = "<select data-step=\"tool\"><option value=\"\">(고르십시오)</option>";
		var found = false;
		catalog.tools.forEach(function (tool) {
			if (tool.name === current) {
				found = true;
			}
			html += "<option value=\"" + escapeHtml(tool.name) + "\"" + (tool.name === current ? " selected" : "") + ">" + escapeHtml(tool.name) + (tool.stepOnly ? " (step 전용)" : "") + "</option>";
		});
		if (current && !found) {
			html += "<option value=\"" + escapeHtml(current) + "\" selected>" + escapeHtml(current) + " (등록되지 않음)</option>";
		}
		html += "</select>";
		var tool = findTool(current);
		if (tool) {
			html += "<p class=\"wd-desc\">" + escapeHtml(shorten(tool.description || "", 300)) + "</p>"
				+ "<pre class=\"wd-schema\">인자\n" + escapeHtml(schemaText(tool.input)) + "</pre>"
				+ "<div class=\"wd-inline-actions\"><button type=\"button\" class=\"kn-plain\" id=\"wd-fill-args-btn\">with에 인자 이름 채우기</button></div>";
		}
		return html;
	}

	function showStepPanel(step) {
		var fields = STEP_FIELDS[step.type] || [];
		var html = "<h4><span class=\"wd-type-chip wd-type-" + escapeHtml(step.type) + "\">" + escapeHtml(step.type) + "</span> step</h4>"
			+ row("id", "영문, 숫자, 밑줄", "<input type=\"text\" id=\"wd-step-id\" value=\"" + escapeHtml(step.id) + "\" />");
		fields.forEach(function (key) {
			var value = getValue(step, key);
			if (key === "agent") {
				html += row("agent", FIELD_HELP.agent, agentSelect(step));
			} else if (key === "tool") {
				html += row("tool", FIELD_HELP.tool, toolSelect(step));
			} else if (key === "with") {
				html += row("with", FIELD_HELP["with"], "<textarea data-step=\"with\" rows=\"4\" class=\"wd-code\">" + escapeHtml(valueToText(value)) + "</textarea>");
			} else if (key === "memory" || key === "approval.requireCommentOnReject") {
				html += row(key, "", "<label class=\"wd-check\"><input type=\"checkbox\" data-step=\"" + key + "\"" + (value ? " checked" : "") + " /> " + escapeHtml(FIELD_HELP[key]) + "</label>");
			} else if (key.indexOf("save") === 0) {
				html += row(key, FIELD_HELP[key], "<input type=\"text\" list=\"wd-state-names\" data-step=\"" + key + "\" value=\"" + escapeHtml(value === undefined ? "" : String(value)) + "\" />");
			} else {
				html += row(key, FIELD_HELP[key], "<input type=\"text\" data-step=\"" + key + "\" value=\"" + escapeHtml(value === undefined ? "" : valueToText(value)) + "\" />");
			}
		});
		html += "<h4>나가는 화살표</h4>" + outgoingList(step)
			+ "<p class=\"ai-hint\">아래쪽 동그라미를 끌어 다른 step이나 END/FAIL에 놓으면 화살표가 생깁니다.</p>"
			+ "<div class=\"wd-inline-actions\"><button type=\"button\" class=\"kn-plain\" id=\"wd-delete-step-btn\">이 step 지우기</button></div>"
			+ stateOptions();
		els.props.innerHTML = html;

		document.getElementById("wd-step-id").addEventListener("change", function (e) {
			renameStep(step.id, e.target.value.trim());
		});
		Array.prototype.forEach.call(els.props.querySelectorAll("[data-step]"), function (input) {
			input.addEventListener("change", function () {
				var key = input.getAttribute("data-step");
				var value;
				if (input.type === "checkbox") {
					value = input.checked ? true : undefined;
				} else if (key === "with") {
					try {
						value = textToValue(input.value);
					} catch (e) {
						showMessage("error", "with - " + e.message);
						return;
					}
				} else {
					value = input.value.trim() === "" ? undefined : input.value.trim();
				}
				change(function () {
					setValue(step, key, value);
					if (key.indexOf("save") === 0) {
						declareState(value, step.id, key);
					}
				});
			});
		});
		Array.prototype.forEach.call(els.props.querySelectorAll("[data-edge-select]"), function (link) {
			link.addEventListener("click", function () {
				selected = { type: "edge", from: step.id, kind: link.getAttribute("data-kind"), route: link.getAttribute("data-route") || null };
				redraw();
				showPanel();
			});
		});
		document.getElementById("wd-delete-step-btn").addEventListener("click", deleteSelected);
		var openAgentBtn = document.getElementById("wd-open-agent-btn");
		if (openAgentBtn) {
			openAgentBtn.addEventListener("click", function () {
				openAgent(step.agent);
			});
		}
		var newAgentBtn = document.getElementById("wd-new-agent-btn");
		if (newAgentBtn) {
			newAgentBtn.addEventListener("click", newAgent);
		}
		var fillArgsBtn = document.getElementById("wd-fill-args-btn");
		if (fillArgsBtn) {
			fillArgsBtn.addEventListener("click", function () {
				var tool = findTool(step.tool);
				var props = tool && tool.input && tool.input.properties ? Object.keys(tool.input.properties) : [];
				change(function () {
					var args = step["with"] && typeof step["with"] === "object" ? step["with"] : {};
					var added = false;
					props.forEach(function (name) {
						if (!Object.prototype.hasOwnProperty.call(args, name)) {
							args[name] = "";
							added = true;
						}
					});
					if (!added) {
						return false;
					}
					step["with"] = args;
				});
			});
		}
	}

	function outgoingList(step) {
		var list = outgoing(step);
		if (list.length === 0) {
			return "<p class=\"ai-hint\">없습니다.</p>";
		}
		return "<ul class=\"wd-edge-list\">" + list.map(function (edge) {
			var name = edge.kind === "route" ? "경로 " + edge.route : (edge.kind === "onFailure" ? "실패(onFailure)" : (edge.kind === "rejectTo" ? "반려(approval.rejectTo)" : "다음(next)"));
			return "<li><a href=\"javascript:void(0)\" data-edge-select=\"1\" data-kind=\"" + escapeHtml(edge.kind) + "\" data-route=\"" + escapeHtml(edge.route || "") + "\">"
				+ escapeHtml(name) + "</a> → <b>" + escapeHtml(edge.target) + "</b></li>";
		}).join("") + "</ul>";
	}

	function showEdgePanel(edge) {
		var step = stepById(edge.from);
		var current = outgoing(step).filter(function (e) {
			return e.kind === edge.kind && (e.route || null) === (edge.route || null);
		})[0];
		if (!current) {
			selected = { type: "step", id: step.id };
			showPanel();
			return;
		}
		var kinds = edgeKindsOf(step.type);
		var kindNames = { next: "다음(next)", onFailure: "실패(onFailure)", route: "경로(routes)", rejectTo: "반려(approval.rejectTo)" };
		var html = "<h4>화살표</h4>"
			+ "<p><b>" + escapeHtml(edge.from) + "</b> → <b>" + escapeHtml(current.target) + "</b></p>"
			+ row("종류", "", "<select id=\"wd-edge-kind\">" + kinds.concat(kinds.indexOf(edge.kind) < 0 ? [edge.kind] : []).map(function (kind) {
				return "<option value=\"" + kind + "\"" + (kind === edge.kind ? " selected" : "") + ">" + kindNames[kind] + "</option>";
			}).join("") + "</select>");
		if (edge.kind === "route") {
			var help = step.type === "APPROVAL" ? "승인자가 고르는 결정 이름(예: APPROVED, REJECTED)" : "ROUTER Agent가 고르는 경로 이름";
			html += row("경로 이름", help, "<input type=\"text\" id=\"wd-edge-route\" value=\"" + escapeHtml(edge.route) + "\" />");
		}
		html += row("가는 곳", "", "<select id=\"wd-edge-target\">" + targetOptions(current.target) + "</select>");
		html += "<div class=\"wd-inline-actions\"><button type=\"button\" class=\"kn-plain\" id=\"wd-delete-edge-btn\">이 화살표 지우기</button>"
			+ "<button type=\"button\" class=\"kn-plain\" id=\"wd-back-step-btn\">" + escapeHtml(edge.from) + " step 설정</button></div>";
		els.props.innerHTML = html;

		document.getElementById("wd-edge-kind").addEventListener("change", function (e) {
			var kind = e.target.value;
			change(function () {
				if ((kind === "next" && step.next) || (kind === "onFailure" && step.onFailure)) {
					showMessage("error", "이 step에는 " + kind + " 화살표가 이미 있습니다. 그것을 먼저 지우십시오.");
					return false;
				}
				removeEdge(step, edge.kind, edge.route);
				var route = null;
				if (kind === "route") {
					route = newRouteName(step);
					step.routes = step.routes || {};
					step.routes[route] = current.target;
				} else {
					step[kind] = current.target;
				}
				selected = { type: "edge", from: step.id, kind: kind, route: route };
			});
		});
		var routeInput = document.getElementById("wd-edge-route");
		if (routeInput) {
			routeInput.addEventListener("change", function () {
				var name = routeInput.value.trim();
				if (!name || name === edge.route) {
					return;
				}
				change(function () {
					if (step.routes[name]) {
						showMessage("error", "이미 있는 경로 이름입니다: " + name);
						return false;
					}
					// 경로 순서를 지키며 이름만 바꾼다.
					var next = {};
					Object.keys(step.routes).forEach(function (key) {
						next[key === edge.route ? name : key] = step.routes[key];
					});
					step.routes = next;
					selected = { type: "edge", from: step.id, kind: "route", route: name };
				});
			});
		}
		document.getElementById("wd-edge-target").addEventListener("change", function (e) {
			var target = e.target.value;
			change(function () {
				if (edge.kind === "route") {
					step.routes[edge.route] = target;
				} else if (edge.kind === "rejectTo") {
					step.approval.rejectTo = target;
				} else {
					step[edge.kind] = target;
				}
			});
		});
		document.getElementById("wd-delete-edge-btn").addEventListener("click", deleteSelected);
		document.getElementById("wd-back-step-btn").addEventListener("click", function () {
			selected = { type: "step", id: step.id };
			redraw();
			showPanel();
		});
	}

	function targetOptions(current) {
		var ids = steps().map(function (step) {
			return step.id;
		}).concat([END_ID, FAIL_ID]);
		if (ids.indexOf(current) < 0) {
			ids.push(current);
		}
		return ids.map(function (id) {
			return "<option value=\"" + escapeHtml(id) + "\"" + (id === current ? " selected" : "") + ">" + escapeHtml(id) + "</option>";
		}).join("");
	}

	/* ====================================================================
	 * Agent 파일 창
	 *
	 * Agent 파일은 맨 위 --- 와 --- 사이가 설정(머리말, YAML), 그 아래가 프롬프트입니다.
	 * 기준은 늘 [텍스트] 탭의 글자이고, 저장과 검사도 그 글자를 보냅니다.
	 *   [양식]에서 머리말 항목을 고치면 머리말을 다시 만듭니다(머리말 맨 위 주석만 남깁니다).
	 *   [양식]에서 프롬프트만 고치면 머리말 글자는 그대로 두고 프롬프트만 바꿉니다.
	 *   [텍스트]에서 고친 글자는 [양식] 탭으로 돌아갈 때 읽어서 양식에 채웁니다(글자는 그대로 둡니다).
	 * ==================================================================== */

	/**
	 * 열려 있는 Agent 파일.
	 *   originalPath: 서버의 원래 경로(새로 만든 것이면 null), front: 머리말을 읽은 객체, frontRaw: 머리말 글자 그대로,
	 *   header: 머리말 맨 위 주석, frontComments: 그 밖의 머리말 주석 줄 수, body: 프롬프트, frontDirty: 양식에서 머리말을 고쳤는지
	 */
	var agentDoc = null;
	/** [텍스트]에서 글자를 고쳤는데 아직 양식에 채우지 않았는지 */
	var agentTextEdited = false;

	/** 머리말을 다시 만들 때 키를 놓는 순서(Agent 파일들이 쓰는 순서) */
	var AGENT_KEY_ORDER = ["id", "version", "role", "description", "model", "input", "output", "context", "tools", "subAgents", "allowedCallers", "execution"];
	var AGENT_SUB_ORDER = {
		model: ["name", "temperature", "reasoning"],
		context: ["sources", "retrieval"],
		retrieval: ["topK", "similarityThreshold", "allowEmptyContext"],
		execution: ["maxAttempts", "onInvalidOutput", "timeoutSeconds", "maxToolCalls"]
	};

	function bindAgentDialog() {
		els.agentForm = document.getElementById("wd-agent-form");
		els.agentTextBox = document.getElementById("wd-agent-text-box");
		els.agentNote = document.getElementById("wd-agent-note");
		els.agentTitle = document.getElementById("wd-agent-title");
		els.agentPathHint = document.getElementById("wd-agent-path-hint");
		document.getElementById("wd-agent-close-btn").addEventListener("click", closeAgent);
		document.getElementById("wd-agent-check-btn").addEventListener("click", function () {
			agentRequest("check");
		});
		document.getElementById("wd-agent-save-btn").addEventListener("click", function () {
			agentRequest("save");
		});
		Array.prototype.forEach.call(document.querySelectorAll("[data-agent-tab]"), function (tab) {
			tab.addEventListener("click", function () {
				showAgentTab(tab.getAttribute("data-agent-tab"));
			});
		});
		els.agentText.addEventListener("input", function () {
			agentTextEdited = true;
			agentDoc.dirty = true;
		});
		els.agentPath.addEventListener("input", function () {
			agentDoc.dirty = true;
			updateAgentPathHint();
		});
		els.agentDialog.addEventListener("keydown", function (e) {
			if (e.key === "Escape") {
				closeAgent();
			}
		});
	}

	function openAgent(id) {
		call("GET", "/api/ai/definitions/agents/" + encodeURIComponent(id)).then(function (source) {
			showAgent(source.text, source.path, source.path);
			agentMessage("", "");
		}).catch(function (e) {
			showMessage("error", "Agent 파일을 읽지 못했습니다: " + e.message);
		});
	}

	function newAgent() {
		var folder = currentFolder().replace(/^workflows/, "agents");
		showAgent("---\nid: new-agent\nversion: 1.0.0\ndescription: 이 Agent가 하는 일\ninput: string\n---\n여기에 시스템 프롬프트를 적습니다.\n",
			folder + "/new-agent.md", null);
		agentMessage("info", "id와 파일 이름을 바꾸고 저장하십시오. 저장하면 step의 agent 목록에 바로 나옵니다.");
	}

	function closeAgent() {
		if (agentDoc && agentDoc.dirty && !window.confirm("저장하지 않은 Agent 변경이 있습니다. 닫을까요?")) {
			return;
		}
		els.agentDialog.style.display = "none";
	}

	/** Agent 파일 글자로 창을 엽니다. */
	function showAgent(text, path, originalPath) {
		agentDoc = { originalPath: originalPath, dirty: false };
		els.agentPath.value = path;
		els.agentText.value = text;
		agentTextEdited = false;
		var problem = readAgentText(text);
		els.agentDialog.style.display = "";
		document.getElementById("wd-agent-save-btn").disabled = !catalog.editable;
		updateAgentPathHint();
		if (problem) {
			showAgentTab("text");
			agentMessage("error", problem);
		} else {
			showAgentTab("form");
		}
	}

	/**
	 * Agent 파일 글자를 머리말 객체와 프롬프트로 나눠 agentDoc에 넣습니다. 틀렸으면 이유를 돌려줍니다(agentDoc은 그대로).
	 */
	function readAgentText(text) {
		var lines = String(text || "").replace(/^﻿/, "").split(/\r?\n/);
		var start = 0;
		while (start < lines.length && lines[start].trim() === "") {
			start++;
		}
		if (start >= lines.length || lines[start].trim() !== "---") {
			return "첫 줄이 --- 여야 합니다(--- 와 --- 사이에 설정, 그 아래에 프롬프트). [텍스트]에서 고치십시오.";
		}
		var end = start + 1;
		while (end < lines.length && lines[end].trim() !== "---") {
			end++;
		}
		if (end >= lines.length) {
			return "설정을 닫는 --- 줄이 없습니다. [텍스트]에서 고치십시오.";
		}
		var frontRaw = lines.slice(start + 1, end).join("\n");
		var front;
		try {
			front = jsyaml.load(frontRaw) || {};
		} catch (e) {
			return "설정(--- 사이)을 YAML로 읽지 못했습니다: " + e.message;
		}
		if (typeof front !== "object" || Array.isArray(front)) {
			return "설정(--- 사이)이 키: 값 모양이 아닙니다.";
		}
		var split = splitHeader(frontRaw);
		agentDoc.front = front;
		agentDoc.frontRaw = frontRaw;
		agentDoc.header = split.header;
		agentDoc.frontComments = split.bodyComments;
		agentDoc.body = lines.slice(end + 1).join("\n").replace(/^\s*\n/, "").replace(/\s+$/, "");
		agentDoc.frontDirty = false;
		return null;
	}

	/** agentDoc으로 파일 글자를 만듭니다. 머리말을 고치지 않았으면 머리말 글자는 원래 그대로 씁니다. */
	function buildAgentText() {
		var front;
		if (agentDoc.frontDirty) {
			front = (agentDoc.header ? agentDoc.header + "\n" : "")
				+ jsyaml.dump(orderAgentFront(agentDoc.front), { lineWidth: -1, noRefs: true, quotingType: "\"" }).replace(/\n$/, "");
		} else {
			front = agentDoc.frontRaw.replace(/\s+$/, "");
		}
		return "---\n" + front + "\n---\n" + agentDoc.body + "\n";
	}

	/** 머리말 키를 Agent 파일들이 쓰는 순서로 다시 놓습니다(모르는 키는 뒤에 그대로). */
	function orderAgentFront(front) {
		return orderKeys(front, AGENT_KEY_ORDER);
	}

	function orderKeys(source, order) {
		var result = {};
		order.forEach(function (key) {
			if (Object.prototype.hasOwnProperty.call(source, key)) {
				result[key] = orderChild(key, source[key]);
			}
		});
		Object.keys(source).forEach(function (key) {
			if (!Object.prototype.hasOwnProperty.call(result, key)) {
				result[key] = source[key];
			}
		});
		return result;
	}

	function orderChild(key, value) {
		if (AGENT_SUB_ORDER[key] && value && typeof value === "object" && !Array.isArray(value)) {
			return orderKeys(value, AGENT_SUB_ORDER[key]);
		}
		return value;
	}

	function showAgentTab(name) {
		if (name === "form" && agentTextEdited) {
			// [텍스트]에서 고친 글자를 양식에 채운다. 읽지 못하면 [텍스트]에 남는다.
			var problem = readAgentText(els.agentText.value);
			if (problem) {
				agentMessage("error", problem);
				name = "text";
			} else {
				agentTextEdited = false;
				agentMessage("", "");
			}
		}
		Array.prototype.forEach.call(document.querySelectorAll("[data-agent-tab]"), function (tab) {
			tab.className = "wd-tab" + (tab.getAttribute("data-agent-tab") === name ? " wd-tab-on" : "");
		});
		els.agentForm.style.display = name === "form" ? "" : "none";
		els.agentTextBox.style.display = name === "text" ? "" : "none";
		if (name === "form") {
			renderAgentForm();
		}
		updateAgentNote();
	}

	/**
	 * 양식에서 무엇을 고칩니다. 고친 뒤 [텍스트]의 글자를 다시 만듭니다.
	 * @param fn 실제로 고치는 함수
	 * @param bodyOnly 프롬프트만 고쳤으면 true(머리말 글자를 그대로 둡니다)
	 */
	function agentChange(fn, bodyOnly) {
		fn();
		if (!bodyOnly) {
			agentDoc.frontDirty = true;
		}
		agentDoc.dirty = true;
		els.agentText.value = buildAgentText();
		agentTextEdited = false;
		updateAgentNote();
	}

	function updateAgentNote() {
		if (!agentDoc) {
			return;
		}
		els.agentTitle.textContent = agentDoc.front && agentDoc.front.id ? String(agentDoc.front.id) : "";
		if (agentDoc.frontComments > 0) {
			els.agentNote.textContent = "설정(--- 사이)에 맨 위 말고도 주석이 " + agentDoc.frontComments + "줄 있습니다. 양식에서 설정을 고치면 그 주석은 사라집니다(프롬프트만 고치거나 [텍스트]에서 고치면 남습니다).";
		} else {
			els.agentNote.textContent = "";
		}
	}

	function updateAgentPathHint() {
		var path = els.agentPath.value.trim();
		if (!agentDoc.originalPath) {
			els.agentPathHint.textContent = "새 파일로 저장합니다.";
		} else if (path !== agentDoc.originalPath) {
			els.agentPathHint.textContent = "저장하면 " + agentDoc.originalPath + " 에서 이 경로로 옮깁니다.";
		} else {
			els.agentPathHint.textContent = "";
		}
	}

	/** 숫자 칸의 글자를 숫자로 바꿉니다. 비었거나 숫자가 아니면 undefined(그 키를 지웁니다). */
	function numberOf(text, integer) {
		var trimmed = String(text || "").trim();
		if (trimmed === "") {
			return undefined;
		}
		var value = integer ? parseInt(trimmed, 10) : parseFloat(trimmed);
		return isNaN(value) ? undefined : value;
	}

	function checkedValues(container, name) {
		var values = [];
		Array.prototype.forEach.call(container.querySelectorAll("input[data-list='" + name + "']:checked"), function (box) {
			values.push(box.value);
		});
		return values;
	}

	/** 체크 상자 목록의 HTML. items: [{value, label, title, disabled, note}] */
	function checkList(name, items, chosen) {
		if (items.length === 0) {
			return "<p class=\"ai-hint\">고를 것이 없습니다.</p>";
		}
		return "<div class=\"wd-check-list\">" + items.map(function (item) {
			var on = chosen.indexOf(item.value) >= 0;
			return "<label class=\"wd-check" + (item.disabled && !on ? " wd-check-off" : "") + "\" title=\"" + escapeHtml(item.title || "") + "\">"
				+ "<input type=\"checkbox\" data-list=\"" + name + "\" value=\"" + escapeHtml(item.value) + "\"" + (on ? " checked" : "") + (item.disabled && !on ? " disabled" : "") + " /> "
				+ "<span>" + escapeHtml(item.label) + (item.note ? " <em>" + escapeHtml(item.note) + "</em>" : "") + "</span></label>";
		}).join("") + "</div>";
	}

	/** 양식을 그립니다. */
	function renderAgentForm() {
		var f = agentDoc.front;
		var model = f.model || {};
		var context = f.context || {};
		var retrieval = context.retrieval || {};
		var execution = f.execution || {};
		var tools = f.tools && Array.isArray(f.tools.allowed) ? f.tools.allowed.map(String) : [];
		var subAgents = Array.isArray(f.subAgents) ? f.subAgents.map(String) : [];
		var sources = Array.isArray(context.sources) ? context.sources.map(String) : [];
		var allTools = tools.indexOf("*") >= 0;

		// Tool 목록: TOOL step 전용 Tool은 Agent에 줄 수 없다(엔진이 막는다). 등록되지 않은 이름도 지우지 않고 보여 준다.
		var toolItems = [];
		catalog.tools.forEach(function (tool) {
			if (!tool.stepOnly) {
				toolItems.push({ value: tool.name, label: tool.name, title: tool.description || "", disabled: allTools });
			}
		});
		tools.forEach(function (name) {
			if (name !== "*" && !findTool(name)) {
				toolItems.push({ value: name, label: name, note: "등록되지 않음" });
			}
		});
		// Sub Agent 목록: 자기 자신은 빼고, 맡을 수 없는 Agent(설명이 없거나 자기 Sub Agent가 있는 것)는 고를 수 없게 둔다.
		var agentItems = [];
		catalog.agents.forEach(function (agent) {
			if (agent.id === f.id) {
				return;
			}
			var reason = !agent.description ? "설명 없음" : (agent.subAgents && agent.subAgents.length > 0 ? "자기 Sub Agent가 있음" : "");
			agentItems.push({ value: agent.id, label: agent.id, title: agent.description || "", disabled: reason !== "", note: reason });
		});
		subAgents.forEach(function (id) {
			if (!findAgent(id)) {
				agentItems.push({ value: id, label: id, note: "등록되지 않음" });
			}
		});

		var html = ""
			+ "<div class=\"wd-agent-grid\">"
			+ "<section class=\"wd-agent-sec\"><h4>기본</h4>"
			+ row("id", "Workflow step의 agent에 적는 이름", "<input type=\"text\" data-agent=\"id\" value=\"" + escapeHtml(f.id === undefined ? "" : String(f.id)) + "\" />")
			+ row("version", "예: 1.0.0", "<input type=\"text\" data-agent=\"version\" value=\"" + escapeHtml(f.version === undefined ? "" : String(f.version)) + "\" />")
			+ row("role", "사람이 읽는 역할 이름(선택)", "<input type=\"text\" data-agent=\"role\" value=\"" + escapeHtml(f.role || "") + "\" />")
			+ row("description", "하는 일. Sub Agent로 쓰이면 부르는 쪽 LLM이 이 설명을 봅니다", "<textarea data-agent=\"description\" rows=\"2\">" + escapeHtml(f.description || "") + "</textarea>")
			+ row("allowedCallers", "쉼표로 구분, 비우면 누구나", "<input type=\"text\" data-agent=\"allowedCallers\" value=\"" + escapeHtml((Array.isArray(f.allowedCallers) ? f.allowedCallers : []).join(", ")) + "\" />")
			+ "</section>"
			+ "<section class=\"wd-agent-sec\"><h4>모델</h4>"
			+ row("model.name", "비우면 엔진 기본 모델", "<input type=\"text\" data-agent=\"model.name\" value=\"" + escapeHtml(model.name || "") + "\" />")
			+ row("model.temperature", "0 ~ 2, 비우면 기본값", "<input type=\"number\" min=\"0\" max=\"2\" step=\"0.1\" data-agent=\"model.temperature\" value=\"" + escapeHtml(model.temperature === undefined ? "" : String(model.temperature)) + "\" />")
			+ row("model.reasoning", "추론 정도", "<select data-agent=\"model.reasoning\">" + ["", "none", "low", "medium", "high"].map(function (v) {
				var label = v === "" ? "(모델 기본값)" : v + (v === "none" ? " - 추론 끔" : "");
				return "<option value=\"" + v + "\"" + (String(model.reasoning === undefined ? "" : model.reasoning) === v ? " selected" : "") + ">" + label + "</option>";
			}).join("") + "</select>")
			+ "<h4>실행</h4>"
			+ row("execution.onInvalidOutput", "답이 output 모양과 다를 때", "<select data-agent=\"execution.onInvalidOutput\">" + [["", "(기본: FAIL)"], ["RETRY", "RETRY - 다시 묻기"], ["FAIL", "FAIL - step 실패"]].map(function (o) {
				return "<option value=\"" + o[0] + "\"" + (String(execution.onInvalidOutput || "") === o[0] ? " selected" : "") + ">" + o[1] + "</option>";
			}).join("") + "</select>")
			+ row("execution.maxAttempts", "RETRY일 때 최대 시도 수", "<input type=\"number\" min=\"1\" data-agent=\"execution.maxAttempts\" value=\"" + escapeHtml(execution.maxAttempts === undefined ? "" : String(execution.maxAttempts)) + "\" />")
			+ row("execution.timeoutSeconds", "응답 제한 시간(초)", "<input type=\"number\" min=\"1\" data-agent=\"execution.timeoutSeconds\" value=\"" + escapeHtml(execution.timeoutSeconds === undefined ? "" : String(execution.timeoutSeconds)) + "\" />")
			+ row("execution.maxToolCalls", "한 번 불릴 때 Tool 호출 한도", "<input type=\"number\" min=\"1\" data-agent=\"execution.maxToolCalls\" value=\"" + escapeHtml(execution.maxToolCalls === undefined ? "" : String(execution.maxToolCalls)) + "\" />")
			+ "</section>"
			+ "</div>"
			+ "<section class=\"wd-agent-sec\"><h4>입출력 계약</h4>"
			+ "<p class=\"ai-hint\">무엇을 받고 무엇을 돌려주는지 정합니다. 값 하나(예: 글자)이거나, 이름이 붙은 필드 묶음입니다. "
			+ "output을 적지 않거나 string 값 하나면 답을 글자로 받고, 필드를 적으면 JSON으로 받아 검사합니다. 프롬프트의 <code>{필드}</code> 자리는 input 필드 값으로 채워집니다. "
			+ "[직접 입력]을 고르면 짧은 타입 표기(Picoschema)로 바로 적을 수 있습니다.</p>"
			+ row("input", "받는 값", "<div class=\"pe-host\" data-pico=\"input\"></div>")
			+ row("output", "돌려주는 값(SUPERVISOR/ROUTER에 쓸 Agent는 적지 않습니다)", "<div class=\"pe-host\" data-pico=\"output\"></div>")
			+ "</section>"
			+ "<section class=\"wd-agent-sec\"><h4>Tool <span class=\"wd-help\">LLM이 스스로 골라 부를 수 있는 Tool</span></h4>"
			+ "<label class=\"wd-check\"><input type=\"checkbox\" id=\"wd-agent-all-tools\"" + (allTools ? " checked" : "") + " /> 전부(*) - 등록된 Tool을 모두 씀(TOOL step 전용 Tool은 빠짐)</label>"
			+ checkList("tools", toolItems, tools)
			+ "</section>"
			+ "<section class=\"wd-agent-sec\"><h4>Sub Agent <span class=\"wd-help\">일을 맡길 다른 Agent. LLM에게는 Tool처럼 보입니다(깊이는 한 단계)</span></h4>"
			+ checkList("subAgents", agentItems, subAgents)
			+ "</section>"
			+ "<section class=\"wd-agent-sec\"><h4>참고자료</h4>"
			+ "<div class=\"wd-check-list\">"
			+ "<label class=\"wd-check\"><input type=\"checkbox\" data-list=\"sources\" value=\"workflowInput\"" + (sources.indexOf("workflowInput") >= 0 ? " checked" : "") + " /> <span>workflowInput <em>Workflow 입력</em></span></label>"
			+ "<label class=\"wd-check\"><input type=\"checkbox\" data-list=\"sources\" value=\"retrievedDocuments\"" + (sources.indexOf("retrievedDocuments") >= 0 ? " checked" : "") + " /> <span>retrievedDocuments <em>올린 문서 검색(RAG)</em></span></label>"
			+ "</div>"
			+ (sources.indexOf("retrievedDocuments") >= 0
				? "<div class=\"wd-agent-grid wd-agent-grid-3\">"
					+ row("topK", "가져올 조각 수", "<input type=\"number\" min=\"1\" data-agent=\"context.retrieval.topK\" value=\"" + escapeHtml(retrieval.topK === undefined ? "" : String(retrieval.topK)) + "\" />")
					+ row("similarityThreshold", "0 ~ 1, 이보다 덜 비슷하면 버림", "<input type=\"number\" min=\"0\" max=\"1\" step=\"0.05\" data-agent=\"context.retrieval.similarityThreshold\" value=\"" + escapeHtml(retrieval.similarityThreshold === undefined ? "" : String(retrieval.similarityThreshold)) + "\" />")
					+ row("allowEmptyContext", "", "<label class=\"wd-check\"><input type=\"checkbox\" data-agent=\"context.retrieval.allowEmptyContext\"" + (retrieval.allowEmptyContext !== false ? " checked" : "") + " /> 찾은 문서가 없어도 답함</label>")
					+ "</div>"
				: "")
			+ "</section>"
			+ "<section class=\"wd-agent-sec\"><h4>프롬프트 <span class=\"wd-help\">시스템 프롬프트(파일의 --- 아래 전부)</span></h4>"
			+ "<textarea data-agent-body=\"1\" class=\"wd-code wd-agent-prompt\" spellcheck=\"false\">" + escapeHtml(agentDoc.body) + "</textarea>"
			+ "</section>";
		els.agentForm.innerHTML = html;

		// 입출력 계약은 Picoschema 편집기(picoschema-editor.js)가 맡는다. 고칠 때마다 머리말의 input/output을 바꾼다.
		Array.prototype.forEach.call(els.agentForm.querySelectorAll("[data-pico]"), function (host) {
			var key = host.getAttribute("data-pico");
			DstonePicoschemaEditor.create(host, f[key], {
				noneLabel: key === "output" ? "적지 않음(답을 글자로 받음)" : "적지 않음(기본: 글자 하나)",
				noneHint: key === "output" ? "답을 글자 그대로 받습니다. JSON으로 받으려면 [필드 묶음]을 고르십시오." : "글자 하나(string)를 받습니다.",
				onChange: function (value) {
					agentChange(function () {
						setValue(agentDoc.front, key, value);
					}, false);
				}
			});
		});

		Array.prototype.forEach.call(els.agentForm.querySelectorAll("[data-agent]"), function (input) {
			input.addEventListener("change", function () {
				var key = input.getAttribute("data-agent");
				var value;
				try {
					value = readAgentField(key, input);
				} catch (e) {
					agentMessage("error", key + " - " + e.message);
					return;
				}
				agentChange(function () {
					setValue(agentDoc.front, key, value);
				}, false);
				if (key === "id") {
					renderAgentForm();
				}
			});
		});
		els.agentForm.querySelector("[data-agent-body]").addEventListener("change", function (e) {
			agentChange(function () {
				agentDoc.body = e.target.value.replace(/\s+$/, "");
			}, true);
		});
		Array.prototype.forEach.call(els.agentForm.querySelectorAll("input[data-list]"), function (box) {
			box.addEventListener("change", function () {
				var name = box.getAttribute("data-list");
				var values = checkedValues(els.agentForm, name);
				agentChange(function () {
					if (name === "tools") {
						setValue(agentDoc.front, "tools.allowed", values.length > 0 ? values : undefined);
					} else if (name === "subAgents") {
						setValue(agentDoc.front, "subAgents", values.length > 0 ? values : undefined);
					} else if (name === "sources") {
						setValue(agentDoc.front, "context.sources", values.length > 0 ? values : undefined);
						if (values.indexOf("retrievedDocuments") < 0) {
							// 엔진은 retrievedDocuments 없이 retrieval을 적으면 막는다.
							setValue(agentDoc.front, "context.retrieval", undefined);
						}
					}
				}, false);
				if (name === "sources") {
					renderAgentForm();
				}
			});
		});
		document.getElementById("wd-agent-all-tools").addEventListener("change", function (e) {
			var on = e.target.checked;
			agentChange(function () {
				setValue(agentDoc.front, "tools.allowed", on ? ["*"] : undefined);
			}, false);
			renderAgentForm();
		});
	}

	function readAgentField(key, input) {
		if (input.type === "checkbox") {
			// allowEmptyContext 의 기본값은 true라서, 켜면 키를 지우고 끄면 false를 적는다.
			return input.checked ? undefined : false;
		}
		if (key === "model.temperature" || key === "context.retrieval.similarityThreshold") {
			return numberOf(input.value, false);
		}
		if (key.indexOf("execution.max") === 0 || key === "execution.timeoutSeconds" || key === "context.retrieval.topK") {
			return numberOf(input.value, true);
		}
		if (key === "allowedCallers") {
			var list = input.value.split(",").map(function (v) {
				return v.trim();
			}).filter(function (v) {
				return v !== "";
			});
			return list.length > 0 ? list : undefined;
		}
		if (key === "input" || key === "output") {
			return textToValue(input.value);
		}
		return input.value.trim() === "" ? undefined : input.value.trim();
	}

	function agentRequest(action) {
		if (agentTextEdited) {
			// [텍스트]에서 고친 글자를 그대로 보낸다. 양식에도 채워 둔다(읽지 못해도 보낸다 - 서버가 이유를 알려 준다).
			if (!readAgentText(els.agentText.value)) {
				agentTextEdited = false;
			}
		}
		var body = { path: els.agentPath.value.trim(), originalPath: agentDoc ? agentDoc.originalPath : null, text: els.agentText.value };
		agentMessage("info", action === "save" ? "저장하는 중..." : "검사하는 중...");
		call("POST", "/api/ai/definitions/agents/" + action, body).then(function (result) {
			if (!result.ok) {
				agentMessage("error", result.message);
				return;
			}
			if (action === "check") {
				agentMessage("ok", "검사를 통과했습니다(이 Agent를 부르는 Workflow까지 함께 검사).");
				return;
			}
			agentDoc.originalPath = result.path;
			agentDoc.dirty = false;
			updateAgentPathHint();
			agentMessage("ok", "저장하고 엔진에 바로 반영했습니다: " + result.path);
			loadCatalog().then(function () {
				showPanel();
			});
		}).catch(function (e) {
			agentMessage("error", e.message);
		});
	}

	function agentMessage(kind, text) {
		els.agentMessage.className = "wd-message" + (kind ? " wd-message-" + kind : "");
		els.agentMessage.textContent = text;
	}

	/* ====================================================================
	 * 단추, 끌어 놓기, 탭, 키보드
	 * ==================================================================== */

	function bindToolbar() {
		els.workflow.addEventListener("change", function () {
			if (els.workflow.value) {
				openWorkflow(els.workflow.value);
			}
		});
		els.path.addEventListener("input", function () {
			dirty = true;
			updatePathHint();
		});
		document.getElementById("wd-new-btn").addEventListener("click", newWorkflow);
		document.getElementById("wd-copy-btn").addEventListener("click", copyWorkflow);
		document.getElementById("wd-reload-btn").addEventListener("click", reloadEngine);
		document.getElementById("wd-layout-btn").addEventListener("click", function () {
			if (!doc) {
				return;
			}
			autoLayout();
			dirty = true;
			redraw();
			graph.zoomToFit({ padding: 30, maxScale: 1 });
		});
		document.getElementById("wd-undo-btn").addEventListener("click", undo);
		document.getElementById("wd-delete-btn").addEventListener("click", deleteWorkflow);
		document.getElementById("wd-check-btn").addEventListener("click", checkWorkflow);
		document.getElementById("wd-save-btn").addEventListener("click", saveWorkflow);
		document.getElementById("wd-yaml-apply-btn").addEventListener("click", applyYaml);
		document.getElementById("wd-hide-fail").addEventListener("change", function (e) {
			hideFailEdges = e.target.checked;
			redraw();
		});
		var lineSelect = document.getElementById("wd-line-style");
		try {
			var saved = window.localStorage.getItem(LINE_STYLE_KEY);
			if (saved && LINE_STYLES[saved]) {
				lineStyle = saved;
			}
		} catch (e) {
			// 브라우저 저장소를 쓸 수 없으면(사생활 보호 모드 등) 기본값을 쓴다.
		}
		lineSelect.value = lineStyle;
		lineSelect.addEventListener("change", function () {
			lineStyle = LINE_STYLES[lineSelect.value] ? lineSelect.value : "manhattan";
			try {
				window.localStorage.setItem(LINE_STYLE_KEY, lineStyle);
			} catch (e) {
				// 기억하지 못해도 지금 화면에는 반영한다.
			}
			redraw();
		});
		els.yaml.addEventListener("input", function () {
			textEdited = true;
			dirty = true;
			updateYamlNote();
		});
	}

	function bindPalette() {
		Array.prototype.forEach.call(document.querySelectorAll(".wd-palette-item"), function (item) {
			item.addEventListener("mousedown", function (e) {
				if (!doc) {
					return;
				}
				var type = item.getAttribute("data-type");
				var node = graph.createNode(stepNodeConfig({ id: "새 " + type, type: type }, { x: 0, y: 0 }));
				dnd.start(node, e);
			});
		});
	}

	function bindTabs() {
		Array.prototype.forEach.call(document.querySelectorAll(".wd-tab[data-tab]"), function (tab) {
			tab.addEventListener("click", function () {
				var name = tab.getAttribute("data-tab");
				Array.prototype.forEach.call(document.querySelectorAll(".wd-tab[data-tab]"), function (other) {
					other.className = "wd-tab" + (other === tab ? " wd-tab-on" : "");
				});
				els.graphBox.style.display = name === "graph" ? "" : "none";
				els.yamlBox.style.display = name === "yaml" ? "" : "none";
				fitCanvas();
			});
		});
	}

	function bindKeyboard() {
		document.addEventListener("keydown", function (e) {
			var tag = (e.target && e.target.tagName) || "";
			if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || els.agentDialog.style.display !== "none") {
				return;
			}
			if (e.key === "Delete" || e.key === "Backspace") {
				if (selected) {
					e.preventDefault();
					deleteSelected();
				}
			} else if ((e.ctrlKey || e.metaKey) && (e.key === "z" || e.key === "Z")) {
				e.preventDefault();
				undo();
			}
		});
	}

	/** YAML 탭의 글자를 읽어 그림에 반영합니다. 글자는 그대로 두므로 저장하면 주석도 남습니다. */
	function applyYaml() {
		var text = els.yaml.value;
		var model;
		try {
			model = jsyaml.load(text);
		} catch (e) {
			showMessage("error", "YAML을 읽지 못했습니다: " + e.message);
			return;
		}
		if (!model || typeof model !== "object" || Array.isArray(model)) {
			showMessage("error", "파일 맨 위가 키: 값 모양이 아닙니다.");
			return;
		}
		undoStack.push(JSON.stringify({ model: doc.model, positions: positions }));
		var split = splitHeader(text);
		doc.model = model;
		if (!Array.isArray(doc.model.steps)) {
			doc.model.steps = [];
		}
		doc.header = split.header;
		doc.bodyComments = split.bodyComments;
		textEdited = false;
		dirty = true;
		if (selected && selected.type === "step" && !stepById(selected.id)) {
			selected = null;
		}
		placeMissingNodes();
		redraw();
		showPanel();
		updateYamlNote();
		showMessage("ok", "YAML을 그림에 반영했습니다. 저장해야 파일에 들어갑니다.");
	}

	/* ====================================================================
	 * 안내 문구
	 * ==================================================================== */

	function updatePathHint() {
		var path = els.path.value.trim();
		if (!doc) {
			els.pathHint.textContent = "";
		} else if (!doc.originalPath) {
			els.pathHint.textContent = "새 파일로 저장합니다.";
		} else if (path !== doc.originalPath) {
			els.pathHint.textContent = "저장하면 " + doc.originalPath + " 에서 이 경로로 옮깁니다.";
		} else {
			els.pathHint.textContent = "정의 폴더: " + (catalog.location || "");
		}
	}

	function updateYamlNote() {
		if (!doc) {
			els.yamlNote.textContent = "";
			return;
		}
		if (textEdited) {
			els.yamlNote.textContent = "YAML을 고쳤습니다. [그림에 반영]을 눌러야 그림이 바뀝니다.";
		} else if (doc.bodyComments > 0) {
			els.yamlNote.textContent = "이 파일에는 맨 위 말고도 주석이 " + doc.bodyComments + "줄 있습니다. 그림에서 고치면 그 주석은 사라집니다(YAML 탭에서 고치면 남습니다).";
		} else {
			els.yamlNote.textContent = "";
		}
	}

	function showMessage(kind, text) {
		els.message.className = "wd-message" + (kind ? " wd-message-" + kind : "");
		els.message.textContent = text || "";
	}

	function setStatus(text) {
		els.status.textContent = text || "";
	}

	function clone(value) {
		return JSON.parse(JSON.stringify(value));
	}

	function escapeHtml(value) {
		if (value === null || value === undefined) {
			return "";
		}
		return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
	}

	return {
		init: init
	};
})();
