/*
 * Workflow 실행 관리 화면의 "step 문서" 팝업입니다.
 *
 * step이 읽고 쓴 파일 하나를 열어서
 * - [보기]  Markdown을 HTML로 바꿔 보여 줍니다(marked → DOMPurify로 걸러냄 → highlight.js로 코드 색칠).
 * - [수정]  왼쪽은 원문, 오른쪽은 미리보기입니다. [저장](Ctrl+S)하면 엔진이 원래 글자셋과 줄바꿈 그대로 파일에 씁니다.
 *
 * 원문은 textarea로만 고칩니다. WYSIWYG 편집기는 저장할 때 표와 공백을 제멋대로 정리하는데,
 * 뒤 step의 Agent가 editFileTexts로 글자를 정확히 찾아 고치기 때문에 그렇게 바뀌면 그 step이 실패할 수 있습니다.
 *
 * 저장은 엔진이 막는 경우가 있습니다(저장 기능 꺼짐 403, 실행 중이거나 연 뒤에 파일이 바뀜 409).
 * 그때는 엔진이 준 사유를 그대로 보여 주고, [다시 읽기]로 새 내용을 받게 합니다.
 *
 * 쓰는 법: DstoneAiDocViewer.init({contentUrl, saveUrl, onSaved}) 한 번, 그 뒤 DstoneAiDocViewer.open(executionId, path, info)
 */
var DstoneAiDocViewer = (function () {

	var urls = {};
	var onSaved = null;

	var modalEl, boxEl, titleEl, pathEl, noticeEl, viewEl, editEl, textEl, previewEl, metaEl, statusEl;
	var viewTabBtn, editTabBtn, fullBtn, closeBtn, reloadBtn, saveBtn;

	// 지금 열려 있는 파일
	var executionId = null;
	var executionStatus = null;
	var path = null;
	var info = null;
	var content = null;
	var mode = "view";
	var dirty = false;
	var saving = false;
	var previewTimer = null;

	function init(options) {
		urls = options;
		onSaved = options.onSaved || null;

		modalEl = document.getElementById("wa-doc-modal");
		boxEl = modalEl.querySelector(".wa-doc-box");
		titleEl = document.getElementById("wa-doc-title");
		pathEl = document.getElementById("wa-doc-path");
		noticeEl = document.getElementById("wa-doc-notice");
		viewEl = document.getElementById("wa-doc-view");
		editEl = document.getElementById("wa-doc-edit");
		textEl = document.getElementById("wa-doc-text");
		previewEl = document.getElementById("wa-doc-preview");
		metaEl = document.getElementById("wa-doc-meta");
		statusEl = document.getElementById("wa-doc-status");
		viewTabBtn = document.getElementById("wa-doc-tab-view");
		editTabBtn = document.getElementById("wa-doc-tab-edit");
		fullBtn = document.getElementById("wa-doc-full");
		closeBtn = document.getElementById("wa-doc-close");
		reloadBtn = document.getElementById("wa-doc-reload");
		saveBtn = document.getElementById("wa-doc-save");

		viewTabBtn.addEventListener("click", function () { setMode("view"); });
		editTabBtn.addEventListener("click", function () { setMode("edit"); });
		fullBtn.addEventListener("click", toggleFull);
		closeBtn.addEventListener("click", close);
		reloadBtn.addEventListener("click", reload);
		saveBtn.addEventListener("click", save);
		// 바깥(어두운 곳)을 누르면 닫는다. 팝업 안을 누른 것은 무시한다.
		modalEl.addEventListener("mousedown", function (event) {
			if (event.target === modalEl) {
				close();
			}
		});
		textEl.addEventListener("input", function () {
			setDirty(true);
			schedulePreview();
		});
		// 원문을 내리면 미리보기도 같은 비율만큼 내린다(줄 높이가 달라 정확히 맞지는 않는다).
		textEl.addEventListener("scroll", function () {
			var max = textEl.scrollHeight - textEl.clientHeight;
			var ratio = max > 0 ? textEl.scrollTop / max : 0;
			previewEl.scrollTop = ratio * (previewEl.scrollHeight - previewEl.clientHeight);
		});
		document.addEventListener("keydown", onKeyDown);

		// 문서 안의 링크는 새 창으로 연다. 이 화면을 떠나 고치던 내용을 잃지 않게 한다.
		if (window.DOMPurify && DOMPurify.addHook) {
			DOMPurify.addHook("afterSanitizeAttributes", function (node) {
				if (node.tagName === "A" && node.getAttribute("href")) {
					node.setAttribute("target", "_blank");
					node.setAttribute("rel", "noopener noreferrer");
				}
			});
		}
	}

	/*
	 * 파일 하나를 엽니다.
	 * fileInfo: 문서 목록의 한 항목(path, name, extension, steps ...). 없어도 됩니다.
	 * executionStatus: 실행 상태(WAITING_APPROVAL이면 승인 전에 고치는 경우라 안내를 붙입니다).
	 */
	function open(newExecutionId, newPath, fileInfo, newExecutionStatus) {
		if (isOpen() && dirty && !(newExecutionId === executionId && newPath === path)) {
			if (!window.confirm("저장하지 않은 변경이 있습니다. 버리고 다른 문서를 열까요?")) {
				return;
			}
		}
		executionId = newExecutionId;
		executionStatus = newExecutionStatus || null;
		path = newPath;
		info = fileInfo || null;
		content = null;
		setDirty(false);
		titleEl.textContent = (info && info.name) || nameOf(newPath);
		pathEl.textContent = newPath;
		viewEl.innerHTML = "";
		textEl.value = "";
		previewEl.innerHTML = "";
		metaEl.textContent = "";
		setStatus("불러오는 중입니다...", false);
		renderNotice(null);
		modalEl.hidden = false;
		document.body.classList.add("wa-doc-open");
		setMode("view");
		load();
	}

	function isOpen() {
		return modalEl && !modalEl.hidden;
	}

	function load() {
		var params = new URLSearchParams();
		params.set("executionId", executionId);
		params.set("path", path);
		var requested = path;
		post(urls.contentUrl + "?" + params.toString(), null)
			.then(function (result) {
				if (requested !== path) {
					return;
				}
				content = result;
				textEl.value = result.text || "";
				setDirty(false);
				renderMeta();
				renderNotice(result);
				renderView();
				renderPreview();
				editTabBtn.disabled = !result.editable;
				editTabBtn.title = result.editable ? "" : (result.readOnlyReason || "");
				if (!result.editable && mode === "edit") {
					setMode("view");
				}
				setStatus("", false);
			})
			.catch(function (err) {
				if (requested !== path) {
					return;
				}
				editTabBtn.disabled = true;
				setStatus("열지 못했습니다: " + err.message, true);
			});
	}

	function reload() {
		if (!executionId || !path) {
			return;
		}
		if (dirty && !window.confirm("저장하지 않은 변경을 버리고 파일을 다시 읽을까요?")) {
			return;
		}
		setStatus("다시 읽는 중입니다...", false);
		load();
	}

	function save() {
		if (!content || !content.editable || saving) {
			return;
		}
		if (!dirty) {
			setStatus("바뀐 내용이 없습니다.", false);
			return;
		}
		saving = true;
		saveBtn.disabled = true;
		setStatus("저장하는 중입니다...", false);
		post(urls.saveUrl, {
			executionId: executionId,
			path: path,
			text: textEl.value,
			baseHash: content.hash
		})
			.then(function (result) {
				content = result;
				// 저장한 뒤에도 커서와 스크롤을 지키려고 같은 글자면 textarea는 건드리지 않는다.
				if (textEl.value !== result.text) {
					textEl.value = result.text;
				}
				setDirty(false);
				renderMeta();
				renderView();
				setStatus("저장했습니다 (" + clock(new Date()) + ")", false);
				if (onSaved) {
					onSaved(executionId, path, result);
				}
			})
			.catch(function (err) {
				setStatus("저장하지 못했습니다: " + err.message, true);
			})
			.then(function () {
				saving = false;
				saveBtn.disabled = !(content && content.editable);
			});
	}

	function close() {
		if (!isOpen()) {
			return;
		}
		if (dirty && !window.confirm("저장하지 않은 변경이 있습니다. 닫을까요?")) {
			return;
		}
		setDirty(false);
		modalEl.hidden = true;
		modalEl.classList.remove("wa-full");
		fullBtn.textContent = "전체 화면";
		document.body.classList.remove("wa-doc-open");
		executionId = null;
		path = null;
		content = null;
	}

	function toggleFull() {
		var full = modalEl.classList.toggle("wa-full");
		fullBtn.textContent = full ? "전체 화면 끝내기" : "전체 화면";
	}

	// Esc: 전체 화면이면 먼저 끝내고, 아니면 닫는다. Ctrl+S: 수정 중이면 저장한다.
	function onKeyDown(event) {
		if (!isOpen()) {
			return;
		}
		if (event.key === "Escape") {
			event.preventDefault();
			if (modalEl.classList.contains("wa-full")) {
				toggleFull();
			} else {
				close();
			}
			return;
		}
		if ((event.ctrlKey || event.metaKey) && (event.key === "s" || event.key === "S")) {
			event.preventDefault();
			if (mode === "edit") {
				save();
			}
		}
	}

	function setMode(newMode) {
		if (newMode === "edit" && (!content || !content.editable)) {
			return;
		}
		mode = newMode;
		viewTabBtn.classList.toggle("on", mode === "view");
		editTabBtn.classList.toggle("on", mode === "edit");
		viewEl.hidden = mode !== "view";
		editEl.hidden = mode !== "edit";
		saveBtn.hidden = mode !== "edit";
		saveBtn.disabled = !(content && content.editable);
		if (mode === "view") {
			renderView();
		} else {
			renderPreview();
			textEl.focus();
		}
	}

	function setDirty(value) {
		dirty = value;
		boxEl.classList.toggle("wa-doc-dirty", dirty);
		if (dirty) {
			setStatus("저장하지 않은 변경이 있습니다.", false);
		}
	}

	function setStatus(text, failed) {
		statusEl.textContent = text;
		statusEl.classList.toggle("wa-doc-status-failed", !!failed);
	}

	// 파일 정보 한 줄: 크기, 글자셋, 줄바꿈, 수정 시각
	function renderMeta() {
		if (!content) {
			metaEl.textContent = "";
			return;
		}
		metaEl.textContent = sizeText(content.size) + " · " + content.charset + " · " + content.lineBreak + " · 수정 " + formatTime(content.lastModified);
	}

	// 위쪽 안내: 저장할 수 없는 이유, 승인 대기 중에 고칠 때 알아 둘 것, 이 파일을 건드린 step
	function renderNotice(result) {
		var lines = [];
		if (info && info.steps && info.steps.length) {
			var parts = [];
			info.steps.forEach(function (step) {
				parts.push(step.stepId + (step.action === "WRITE" ? "(작성)" : "(읽음)"));
			});
			lines.push({ text: "이 파일을 건드린 step: " + parts.join(", "), kind: "" });
		}
		if (result && !result.editable && result.readOnlyReason) {
			lines.push({ text: "보기만 됩니다. " + result.readOnlyReason, kind: "warn" });
		}
		if (executionStatus === "WAITING_APPROVAL" && (!result || result.editable)) {
			lines.push({
				text: "승인 대기 중입니다. 고친 내용은 이 파일을 다시 읽는 뒤 step부터 반영됩니다. 앞 step이 이미 읽어 둔 내용(state)은 바뀌지 않습니다.",
				kind: "info"
			});
		}
		noticeEl.innerHTML = "";
		lines.forEach(function (line) {
			var div = document.createElement("div");
			div.className = "wa-doc-notice-line" + (line.kind ? " wa-doc-notice-" + line.kind : "");
			div.textContent = line.text;
			noticeEl.appendChild(div);
		});
		noticeEl.hidden = lines.length === 0;
	}

	function renderView() {
		if (!content) {
			return;
		}
		renderInto(viewEl, content.text);
	}

	function schedulePreview() {
		if (previewTimer) {
			clearTimeout(previewTimer);
		}
		previewTimer = setTimeout(function () {
			previewTimer = null;
			renderPreview();
		}, 200);
	}

	function renderPreview() {
		renderInto(previewEl, textEl.value);
	}

	// md는 HTML로 바꿔 보여 주고, 그 밖의 파일은 코드로 보여 준다.
	function renderInto(target, text) {
		var extension = extensionOf(path);
		if (extension === "md" || extension === "markdown") {
			target.innerHTML = markdownToHtml(text);
			highlightBlocks(target);
		} else {
			target.innerHTML = "";
			var pre = document.createElement("pre");
			var code = document.createElement("code");
			code.textContent = text;
			var language = languageOf(extension);
			if (language) {
				code.className = "language-" + language;
			}
			pre.appendChild(code);
			target.appendChild(pre);
			highlightBlocks(target);
		}
	}

	function markdownToHtml(text) {
		if (!window.marked) {
			return "<pre>" + escapeHtml(text) + "</pre>";
		}
		var html = marked.parse(text || "", { gfm: true, breaks: false });
		// LLM이 쓴 문서라 script나 이벤트 속성이 섞일 수 있다. 화면에 넣기 전에 반드시 걸러낸다.
		return window.DOMPurify ? DOMPurify.sanitize(html) : escapeHtml(text);
	}

	// 코드 블록에 색을 칠한다. 아주 큰 블록은 브라우저가 멈추지 않게 건너뛴다.
	function highlightBlocks(target) {
		if (!window.hljs) {
			return;
		}
		Array.prototype.forEach.call(target.querySelectorAll("pre code"), function (block) {
			if (block.textContent.length > 200000) {
				return;
			}
			hljs.highlightElement(block);
		});
	}

	// 엔진이 거절하면 {status, message}가 온다. 그 사유를 오류 문구로 쓴다.
	function post(url, body) {
		var init = { method: "POST" };
		if (body) {
			init.headers = { "Content-Type": "application/json" };
			init.body = JSON.stringify(body);
		}
		return fetch(url, init).then(function (response) {
			return response.text().then(function (raw) {
				var parsed = null;
				try {
					parsed = raw ? JSON.parse(raw) : null;
				} catch (e) {
					parsed = null;
				}
				if (!response.ok || !parsed) {
					throw new Error((parsed && parsed.message) || ("서버 오류(" + response.status + ")"));
				}
				return parsed;
			});
		});
	}

	function languageOf(extension) {
		var map = { java: "java", js: "javascript", ts: "typescript", xml: "xml", jsp: "xml", html: "xml", sql: "sql", yml: "yaml", yaml: "yaml", json: "json", properties: "properties", sh: "bash", py: "python", css: "css" };
		return map[extension] || "";
	}

	function nameOf(value) {
		var text = String(value || "");
		var slash = Math.max(text.lastIndexOf("/"), text.lastIndexOf("\\"));
		return slash < 0 ? text : text.substring(slash + 1);
	}

	function extensionOf(value) {
		var name = nameOf(value);
		var dot = name.lastIndexOf(".");
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
	}

	function sizeText(bytes) {
		if (bytes == null) {
			return "-";
		}
		if (bytes < 1024) {
			return bytes + "B";
		}
		if (bytes < 1024 * 1024) {
			return (bytes / 1024).toFixed(1) + "KB";
		}
		return (bytes / 1024 / 1024).toFixed(1) + "MB";
	}

	function two(number) {
		return (number < 10 ? "0" : "") + number;
	}

	function clock(date) {
		return two(date.getHours()) + ":" + two(date.getMinutes()) + ":" + two(date.getSeconds());
	}

	function formatTime(value) {
		var date = value ? new Date(value) : null;
		if (!date || isNaN(date.getTime())) {
			return value || "-";
		}
		return date.getFullYear() + "-" + two(date.getMonth() + 1) + "-" + two(date.getDate()) + " " + clock(date);
	}

	function escapeHtml(value) {
		var div = document.createElement("div");
		div.textContent = value == null ? "" : value;
		return div.innerHTML;
	}

	return {
		init: init,
		open: open,
		isOpen: isOpen,
		sizeText: sizeText,
		formatTime: formatTime
	};
})();
