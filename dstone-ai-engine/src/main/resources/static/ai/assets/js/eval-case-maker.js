/*
 * 평가 사례 만들기 대화상자. 실행 관리 화면(admin/workflow/workflow.html)에서 연다.
 * - openStep(executionId, 진행 기록 한 줄)  : 그 Agent step 회차의 스냅숏으로 Agent 사례를 만든다
 * - openExecution(executionId)              : 실행 하나로 Workflow 사례를 만든다
 *
 * 사람은 "무엇이 잘못됐나 → 어디를 볼까 → 어떤 모양이면" 순서로 고르기만 하면 채점 기준(checks)이 만들어진다.
 * 정규식은 글자 칸과 체크박스로 대신 만든다. 숙련자만 "정규식으로 직접 쓰기"를 켠다.
 * [미리 채점]은 그때 결과로 채점한다(LLM 호출 없음). 핵심 기준은 ✗(그때 실수를 잡음), 기본 기준은 ✓가 정상이다.
 * API: /api/ai/eval/drafts/{snapshots|step|execution|preview|save}
 */
var DstoneAiEvalCaseMaker = (function () {

	var esc = function (value) { return DstoneAi.escapeHtml(value == null ? "" : String(value)); };

	/** 무엇이 잘못됐나(증상) → 고를 칸. disabled는 이 화면에서 만들 수 없는 것 */
	var SYMPTOMS = [
		{ id: "fileMissing", label: "파일을 안 만들었다·안 고쳤다" },
		{ id: "fileWrong", label: "파일에 잘못된 내용이 들어갔다" },
		{ id: "fileLacks", label: "파일에 꼭 들어가야 할 내용이 빠졌다" },
		{ id: "answerWrong", label: "판정·답이 틀렸다" },
		{ id: "answerEmpty", label: "답(칸)을 비워 냈다" },
		{ id: "guessed", label: "확인하지 않고 추측했다(Tool을 안 썼다)" },
		{ id: "runtime", label: "만든 기능이 동작하지 않는다", disabled: "시험 서버 설정(server)이 필요해 이 화면에서는 만들 수 없습니다. 저장한 뒤 사례 파일에 http·dbQuery를 더하세요." }
	];

	/** 그때 결과도 ✓여야 정상인 기본 기준 */
	var BASIC_KINDS = { fileExists: true, answerNotEmpty: true };

	var modalEl;
	var st; // 지금 열린 대화상자의 상태

	/* ---------- 열기 ---------- */

	function openStep(executionId, history) {
		var stepId = String(history.stepId || "").replace(/\[\d+\]$/, "");
		DstoneAi.api("GET", "/api/ai/eval/drafts/snapshots?executionId=" + encodeURIComponent(executionId))
			.then(function (list) {
				var picked = pickSnapshot(list || [], stepId, history.executedAt);
				if (!picked) {
					alert("이 줄의 스냅숏이 없습니다.\n스냅숏 기능을 넣기 전의 실행이거나, forEach·TOOL step이거나, 보관 기간이 지났습니다.");
					return null;
				}
				return DstoneAi.api("GET", "/api/ai/eval/drafts/step?executionId=" + encodeURIComponent(executionId) + "&snapshot=" + encodeURIComponent(picked.name));
			})
			.then(function (draft) {
				if (draft) {
					open(draft);
				}
			})
			.catch(function (err) { alert("평가 사례 초안을 읽지 못했습니다: " + err.message); });
	}

	function openExecution(executionId) {
		DstoneAi.api("GET", "/api/ai/eval/drafts/execution?executionId=" + encodeURIComponent(executionId))
			.then(function (draft) { open(draft); })
			.catch(function (err) { alert("평가 사례 초안을 읽지 못했습니다: " + err.message); });
	}

	/** 같은 step의 스냅숏 가운데 끝난 시각이 진행 기록의 시각과 가장 가까운 것(2분 안) */
	function pickSnapshot(list, stepId, executedAt) {
		var target = executedAt ? new Date(executedAt).getTime() : NaN;
		var best = null;
		var bestGap = Infinity;
		list.forEach(function (item) {
			if (item.kind !== "step" || item.stepId !== stepId || !item.endedAt) {
				return;
			}
			var gap = isNaN(target) ? 0 : Math.abs(new Date(item.endedAt).getTime() - target);
			if (gap < bestGap) {
				best = item;
				bestGap = gap;
			}
		});
		return best && bestGap <= 120000 ? best : null;
	}

	function open(draft) {
		ensureModal();
		st = { draft: draft, checks: [], preview: null, editing: -1, projects: [] };
		render();
		modalEl.hidden = false;
		DstoneAi.api("GET", "/api/ai/projects")
			.then(function (projects) {
				st.projects = projects || [];
				renderProjects();
			})
			.catch(function () { renderProjects(); });
	}

	function close() {
		if (st && st.checks.length > 0 && !st.saved && !confirm("저장하지 않은 채점 기준이 있습니다. 닫을까요?")) {
			return;
		}
		modalEl.hidden = true;
		st = null;
	}

	function ensureModal() {
		if (modalEl) {
			return;
		}
		modalEl = document.createElement("div");
		modalEl.className = "ec-modal";
		modalEl.hidden = true;
		modalEl.innerHTML = '<div class="ec-box" role="dialog" aria-modal="true" aria-labelledby="ec-title">'
			+ '<div class="ec-head"><div id="ec-title" class="ec-title"></div><div id="ec-sub" class="ec-sub"></div>'
			+ '<button type="button" class="kn-plain" data-ec="close">닫기</button></div>'
			+ '<div id="ec-body" class="ec-body"></div>'
			+ '<div class="ec-foot"><span id="ec-status" class="ec-status"></span>'
			+ '<button type="button" class="kn-plain" data-ec="preview">그때 결과로 미리 채점</button>'
			+ '<button type="button" data-ec="save">저장</button></div>'
			+ '</div>';
		document.body.appendChild(modalEl);
		modalEl.addEventListener("click", function (event) {
			var action = event.target.getAttribute("data-ec");
			if (action === "close") { close(); }
			if (action === "preview") { preview(); }
			if (action === "save") { save(); }
		});
		document.addEventListener("keydown", function (event) {
			if (event.key === "Escape" && modalEl && !modalEl.hidden) {
				close();
			}
		});
	}

	/* ---------- 그리기 ---------- */

	function render() {
		var d = st.draft;
		var isAgent = d.kind === "agent";
		document.getElementById("ec-title").textContent = isAgent ? "평가 사례로 만들기 (Agent)" : "평가 사례로 만들기 (Workflow)";
		document.getElementById("ec-sub").textContent = isAgent
			? d.agent + " · " + d.stepId + " " + d.round + "회차 · " + (d.success ? "성공" : "실패") + " · 실행 " + d.executionId
			: d.workflowId + " · 실행 " + d.executionId;
		setStatus("");

		var html = '<p class="ec-guide">그때의 실수는 잡고, 올바른 답은 통과시키는 기준을 1~3개 적으세요.</p>';
		(d.warnings || []).forEach(function (w) { html += '<p class="ec-warn">' + esc(w) + '</p>'; });
		if (d.truncated) {
			html += '<p class="ec-warn">이 스냅숏은 용량 상한에 걸려 일부 파일 내용이 빠져 있습니다.</p>';
		}

		html += '<div class="ec-grid">'
			+ '<label for="ec-id">사례 id</label><div><input type="text" id="ec-id" value="' + esc(d.suggestedId) + '" /><div class="ec-hint">영문 소문자·숫자·하이픈. 파일 이름이 됩니다.</div></div>'
			+ '<label for="ec-project">프로젝트</label><div><select id="ec-project"><option value="">불러오는 중…</option></select><div class="ec-hint">사례는 엔진의 conf/eval/{프로젝트ID}/ 에 저장됩니다(git에 올리지 않음).</div></div>'
			+ '<label for="ec-desc">설명</label><div><textarea id="ec-desc" rows="2" placeholder="무엇을 재는 사례인지 한 줄로">' + esc(d.suggestedDescription) + '</textarea>'
			+ (d.nextDecision ? '<div class="ec-hint">바로 다음 결정의 의견에서 채웠습니다(' + esc(d.nextDecision.step) + ' ' + esc(d.nextDecision.decision) + ').</div>' : '') + '</div>'
			+ '<label for="ec-note">그때 일 <small>선택</small></label><div><textarea id="ec-note" rows="2" placeholder="그때 무슨 일이 있었나(사례 파일 머리 주석으로 남습니다)"></textarea></div>';
		if (isAgent) {
			html += '<label for="ec-work">작업 폴더</label><div><select id="ec-work">' + workOptions(d) + '</select>'
				+ '<div class="ec-hint">이 입력 폴더가 사례의 work/ 가 되고, 경로는 ${workDir}로 바뀝니다. 파일 채점은 이 폴더 기준입니다.</div></div>';
		} else {
			html += '<label>요청 폴더</label><div class="ec-hint">' + esc(d.requestDir || "(찾지 못함)") + (d.startSnapshot ? " · 실행 시작 때 남긴 스냅숏으로 만듭니다" : "") + '</div>'
				+ '<label>승인</label><div class="ec-hint">' + approvalsText(d.approvals) + '</div>';
		}
		html += '</div>';

		html += '<details class="ec-then"><summary>그때 결과 보기 (답 · Tool · 쓴 파일)</summary>'
			+ '<div><b>답</b></div><pre>' + esc(prettyAnswer(d.answer)) + '</pre>'
			+ (isAgent ? '<div><b>Tool</b> ' + esc(toolsText(d.tools)) + '</div>' : '')
			+ '<div><b>' + (isAgent ? "이 step이 쓴 파일" : "지금 요청 폴더의 파일") + '</b> ' + esc(fileCandidates().join(", ") || "(없음)") + '</div>'
			+ '</details>';

		html += '<div class="ec-section-title">채점 기준</div>'
			+ '<table class="ec-checks"><thead><tr><th style="width:56px">구분</th><th>기준</th><th style="width:150px">그때</th><th style="width:140px"></th></tr></thead>'
			+ '<tbody id="ec-check-rows"></tbody></table>'
			+ '<div id="ec-basic-tip"></div>'
			+ '<div style="margin-top:8px"><button type="button" class="kn-plain" id="ec-add">+ 기준 추가</button></div>'
			+ '<div id="ec-editor"></div>'
			+ '<details id="ec-yaml-box" style="margin-top:12px"><summary>YAML 보기</summary><pre id="ec-yaml" class="ec-yaml">미리 채점하면 만들어질 사례 파일을 보여 줍니다.</pre></details>';

		var body = document.getElementById("ec-body");
		body.innerHTML = html;
		body.querySelector("#ec-add").addEventListener("click", function () { openEditor(-1); });
		["ec-id", "ec-project", "ec-desc", "ec-note", "ec-work"].forEach(function (id) {
			var el = document.getElementById(id);
			if (el) {
				el.addEventListener("change", stalePreview);
			}
		});
		var work = document.getElementById("ec-work");
		if (work) {
			work.addEventListener("change", function () { renderChecks(); });
		}
		renderChecks();
	}

	function renderProjects() {
		var select = document.getElementById("ec-project");
		if (!select) {
			return;
		}
		var guess = guessProject();
		var html = '<option value="">프로젝트를 고르세요</option>';
		st.projects.forEach(function (p) {
			html += '<option value="' + esc(p.projectId) + '"' + (p.projectId === guess ? " selected" : "") + '>' + esc(p.projectId + (p.projectName ? " - " + p.projectName : "")) + '</option>';
		});
		select.innerHTML = html;
	}

	/** 입력 값 가운데 프로젝트 ID와 같은 것이 있으면 그 프로젝트, 없고 하나뿐이면 그것 */
	function guessProject() {
		var input = st.draft.input;
		var values = [];
		if (input && typeof input === "object") {
			Object.keys(input).forEach(function (k) { values.push(String(input[k])); });
		}
		for (var i = 0; i < st.projects.length; i++) {
			if (values.indexOf(st.projects[i].projectId) >= 0) {
				return st.projects[i].projectId;
			}
		}
		return st.projects.length === 1 ? st.projects[0].projectId : "";
	}

	function workOptions(d) {
		var html = '<option value="">(작업 폴더 없음)</option>';
		(d.inputs || []).forEach(function (item) {
			if (item.type !== "dir") {
				return;
			}
			var label = item.key + " — " + item.path + (item.copied ? "" : " (복사 안 됨: " + (item.reason || "") + ")");
			html += '<option value="' + esc(item.key) + '"' + (item.key === d.workKey ? " selected" : "") + (item.copied ? "" : " disabled") + '>' + esc(label) + '</option>';
		});
		return html;
	}

	function approvalsText(approvals) {
		var keys = Object.keys(approvals || {});
		if (keys.length === 0) {
			return "(사람 결정 없음)";
		}
		return keys.map(function (k) { return esc(k) + " → " + esc(approvals[k]); }).join("<br>")
			+ "<br>평가 때 이 결정대로 고릅니다. 여기에 없는 승인에 도착하면 그 자리에서 멈추고 채점합니다.";
	}

	function prettyAnswer(answer) {
		if (answer == null) {
			return "(답 없음)";
		}
		return typeof answer === "string" ? answer : JSON.stringify(answer, null, 2);
	}

	function toolsText(tools) {
		var keys = Object.keys(tools || {});
		return keys.length === 0 ? "(부르지 않음)" : keys.map(function (k) { return k + " " + tools[k] + "번"; }).join(", ");
	}

	/** 파일 칸의 후보: Agent면 이 step이 읽고 쓴 파일(작업 폴더 기준), Workflow면 지금 요청 폴더의 파일 */
	function fileCandidates() {
		var d = st.draft;
		if (d.kind !== "agent") {
			return d.files || [];
		}
		var base = workPath();
		var result = [];
		(d.files || []).forEach(function (f) {
			var rel = relativeTo(base, f.path);
			if (rel && (f.write || f.read) && result.indexOf(rel) < 0) {
				result.push(rel);
			}
		});
		return result;
	}

	function workPath() {
		var select = document.getElementById("ec-work");
		var key = select ? select.value : st.draft.workKey;
		var found = null;
		(st.draft.inputs || []).forEach(function (item) {
			if (item.key === key) {
				found = item.path;
			}
		});
		return found;
	}

	function relativeTo(base, path) {
		if (!base || !path) {
			return null;
		}
		var b = String(base).replace(/\\/g, "/").replace(/\/+$/, "") + "/";
		var p = String(path).replace(/\\/g, "/");
		var ignoreCase = /^[A-Za-z]:/.test(b);
		var hit = ignoreCase ? p.toLowerCase().indexOf(b.toLowerCase()) === 0 : p.indexOf(b) === 0;
		return hit ? p.substring(b.length) : null;
	}

	/* ---------- 채점 기준 목록 ---------- */

	function renderChecks() {
		var tbody = document.getElementById("ec-check-rows");
		if (!tbody) {
			return;
		}
		if (st.checks.length === 0) {
			tbody.innerHTML = '<tr><td colspan="4" class="ec-empty">아직 기준이 없습니다. [+ 기준 추가]를 누르세요.</td></tr>';
		} else {
			var html = "";
			st.checks.forEach(function (c, i) {
				var basic = !!BASIC_KINDS[c.kind];
				html += '<tr><td><span class="ec-role' + (basic ? "" : " core") + '">' + (basic ? "기본" : "핵심") + '</span></td>'
					+ '<td><b>' + esc(c.name) + '</b>' + (c.name === c.sentence ? "" : '<div class="ec-sentence">' + esc(c.sentence) + '</div>') + '</td>'
					+ '<td>' + thenCell(i, basic) + '</td>'
					+ '<td class="ec-row-buttons"><button type="button" class="kn-plain" data-edit="' + i + '">수정</button><button type="button" class="kn-plain" data-del="' + i + '">삭제</button></td></tr>';
			});
			tbody.innerHTML = html;
			Array.prototype.forEach.call(tbody.querySelectorAll("[data-edit]"), function (b) {
				b.addEventListener("click", function () { openEditor(Number(b.getAttribute("data-edit"))); });
			});
			Array.prototype.forEach.call(tbody.querySelectorAll("[data-del]"), function (b) {
				b.addEventListener("click", function () {
					st.checks.splice(Number(b.getAttribute("data-del")), 1);
					stalePreview();
					closeEditor();
					renderChecks();
				});
			});
		}
		var hasBasic = st.checks.some(function (c) { return BASIC_KINDS[c.kind]; });
		var tip = document.getElementById("ec-basic-tip");
		tip.innerHTML = st.checks.length > 0 && !hasBasic
			? '<p class="ec-warn" style="margin-top:8px">산출물이 있는지(파일 있음·답 칸 채움) 확인을 하나 넣을까요? 이것이 실패하면 나머지 점수는 의미가 없습니다.</p>'
			: "";
	}

	/** "그때" 칸: 미리 채점 결과와 판정(기본은 ✓, 핵심은 ✗가 정상) */
	function thenCell(i, basic) {
		if (!st.preview) {
			return '<span class="ec-then-none">미리 채점 전</span>';
		}
		var r = st.preview[i];
		if (!r || r.ok == null) {
			return '<span class="ec-then-none">–</span><span class="ec-verdict">' + esc(r ? r.detail : "") + '</span>';
		}
		var mark = r.ok ? '<span class="ec-then-ok">✓ 통과</span>' : '<span class="ec-then-fail">✗ 실패</span>';
		var verdict;
		if (basic) {
			verdict = r.ok ? '<span class="ec-verdict good">정상</span>' : '<span class="ec-verdict bad">그때 결과물이 없었습니다. 의도한 것이면 그대로 두세요</span>';
		} else {
			verdict = r.ok ? '<span class="ec-verdict bad">그때 결과도 통과합니다. 그때 실수를 잡지 못하는 기준입니다(너무 느슨함)</span>' : '<span class="ec-verdict good">정상 — 그때 실수를 잡습니다</span>';
		}
		return mark + (r.detail ? ' <span class="ec-hint">' + esc(r.detail) + '</span>' : "") + verdict;
	}

	/* ---------- 기준 하나 편집 ---------- */

	function openEditor(index) {
		st.editing = index;
		var c = index >= 0 ? st.checks[index] : null;
		var ui = c ? c.ui : { symptom: "", file: "", field: "", mode: "absent", a: "", b: "", scope: true, regex: false, pattern: "", fileMode: "exists", tool: "", min: 1 };
		var box = document.getElementById("ec-editor");
		var html = '<div class="ec-editor">'
			+ '<div class="ec-step"><div class="ec-step-title">① 무엇이 잘못됐나요?</div><div class="ec-symptoms">';
		SYMPTOMS.forEach(function (s) {
			html += '<label' + (s.disabled ? ' class="disabled" title="' + esc(s.disabled) + '"' : "") + '><input type="radio" name="ec-symptom" value="' + s.id + '"'
				+ (ui.symptom === s.id ? " checked" : "") + (s.disabled ? " disabled" : "") + ' /> ' + esc(s.label) + (s.disabled ? " (시험 서버 필요)" : "") + '</label>';
		});
		html += '</div></div><div id="ec-fields"></div>'
			+ '<div class="ec-step"><div class="ec-step-title">이름</div><input type="text" id="ec-name" placeholder="기대하는 모습. 예: 직접 보지 않은 것을 PASS로 내지 않음" value="' + esc(c ? c.name : "") + '" /></div>'
			+ '<div id="ec-readout" class="ec-readout">증상을 고르세요.</div>'
			+ '<div class="ec-editor-foot"><button type="button" class="kn-plain" id="ec-ed-cancel">취소</button><button type="button" id="ec-ed-ok">' + (c ? "고치기" : "추가") + '</button></div>'
			+ '</div>';
		box.innerHTML = html;
		box.querySelector("#ec-ed-cancel").addEventListener("click", closeEditor);
		box.querySelector("#ec-ed-ok").addEventListener("click", commitEditor);
		Array.prototype.forEach.call(box.querySelectorAll('input[name="ec-symptom"]'), function (r) {
			r.addEventListener("change", function () {
				ui.symptom = r.value;
				renderFields(ui);
			});
		});
		st.ui = ui;
		renderFields(ui);
		box.scrollIntoView({ block: "nearest" });
	}

	function closeEditor() {
		st.editing = -1;
		st.ui = null;
		var box = document.getElementById("ec-editor");
		if (box) {
			box.innerHTML = "";
		}
	}

	/** 증상에 따라 ②③ 칸을 그린다 */
	function renderFields(ui) {
		var el = document.getElementById("ec-fields");
		var s = ui.symptom;
		var html = "";
		var files = fileCandidates();
		var fileList = '<datalist id="ec-file-list">' + files.map(function (f) { return '<option value="' + esc(f) + '"></option>'; }).join("") + '</datalist>';
		var fileInput = '<input type="text" id="ec-file" list="ec-file-list" placeholder="작업 폴더 기준 경로" value="' + esc(ui.file) + '" />' + fileList;
		var fields = st.draft.outputFields || [];
		var fieldSelect = function (allowAll) {
			var h = '<select id="ec-field">' + (allowAll ? '<option value="">(답 전체)</option>' : "");
			fields.forEach(function (f) { h += '<option value="' + esc(f) + '"' + (ui.field === f ? " selected" : "") + '>' + esc(f) + '</option>'; });
			return h + '</select>';
		};
		var shapeInputs = function (scopeLabel) {
			return '<div class="ec-inline"><span>찾을 글자</span><input type="text" id="ec-a" value="' + esc(ui.a) + '" placeholder="짧게(틀린 것을 가르는 글자만)" />'
				+ '<span>…</span><input type="text" id="ec-b" value="' + esc(ui.b) + '" placeholder="(선택) 그 뒤에 나오는 글자" /></div>'
				+ '<div class="ec-inline"><label><input type="checkbox" id="ec-scope"' + (ui.scope ? " checked" : "") + ' /> ' + scopeLabel + '</label>'
				+ '<label><input type="checkbox" id="ec-regex"' + (ui.regex ? " checked" : "") + ' /> 정규식으로 직접 쓰기</label></div>'
				+ '<div id="ec-regex-row" class="ec-inline"' + (ui.regex ? "" : " hidden") + '><input type="text" id="ec-pattern" value="' + esc(ui.pattern) + '" placeholder="정규식(여러 줄에 걸쳐 찾음)" /></div>'
				+ '<div class="ec-hint">문장 전체 말고, 틀린 것을 가르는 짧은 글자만 적으세요. 두 칸을 채우면 "앞 글자 뒤에 뒤 글자가 나오는 곳"을 찾습니다.</div>';
		};
		if (s === "fileMissing") {
			html = step("② 어느 파일인가요?", fileInput)
				+ step("③ 어때야 하나요?", '<div class="ec-inline"><label><input type="radio" name="ec-fmode" value="exists"' + (ui.fileMode !== "changed" ? " checked" : "") + ' /> 있어야 함</label>'
					+ '<label><input type="radio" name="ec-fmode" value="changed"' + (ui.fileMode === "changed" ? " checked" : "") + ' /> 처음과 달라져 있어야 함(고쳐야 할 파일)</label></div>');
		} else if (s === "fileWrong" || s === "fileLacks") {
			html = step("② 어느 파일인가요?", fileInput)
				+ step(s === "fileWrong" ? "③ 어떤 내용이 나오면 안 되나요?" : "③ 어떤 내용이 있어야 하나요?", shapeInputs("같은 줄 안에서만 찾기"))
				+ (s === "fileWrong" ? '<div class="ec-hint">"없어야 함"은 파일이 아예 없어도 실패로 칩니다.</div>' : "");
		} else if (s === "answerWrong") {
			html = step("② 답의 어느 칸인가요?", fieldSelect(true))
				+ step("③ 어떤 모양이면 맞나요?", '<div class="ec-inline">'
					+ '<label><input type="radio" name="ec-mode" value="absent"' + (ui.mode === "absent" ? " checked" : "") + ' /> 없어야 함</label>'
					+ '<label><input type="radio" name="ec-mode" value="present"' + (ui.mode === "present" ? " checked" : "") + ' /> 있어야 함</label>'
					+ '<label><input type="radio" name="ec-mode" value="equals"' + (ui.mode === "equals" ? " checked" : "") + ' /> 값이 정확히 같아야 함(true/false, 상태값)</label></div>'
					+ shapeInputs("항목 하나 안에서만 찾기(목록 답일 때 다른 항목으로 넘어가지 않음)"))
				+ '<div class="ec-hint">"없어야 함"은 답이 아예 없어도 실패로 칩니다. 목록·맵 칸은 {이름=값, …}, {…} 모양 글자로 바뀐 뒤 찾습니다.</div>';
		} else if (s === "answerEmpty") {
			html = step("② 답의 어느 칸인가요?", fields.length ? fieldSelect(false) : '<input type="text" id="ec-field" value="' + esc(ui.field) + '" placeholder="칸 이름" />')
				+ '<div class="ec-hint">그 칸이 비어 있지 않아야 통과합니다(기본 확인).</div>';
		} else if (s === "guessed") {
			var tools = Object.keys(st.draft.tools || {});
			html = step("② 어떤 Tool을 썼어야 하나요?", '<div class="ec-inline"><input type="text" id="ec-tool" list="ec-tool-list" value="' + esc(ui.tool) + '" placeholder="Tool 이름(앞부분만 맞아도 됨, 예: browser_)" />'
				+ '<datalist id="ec-tool-list">' + tools.map(function (t) { return '<option value="' + esc(t) + '"></option>'; }).join("") + '</datalist>'
				+ '<span>최소</span><input type="number" id="ec-min" min="1" value="' + esc(ui.min || 1) + '" style="width:70px" /><span>번</span></div>')
				+ '<div class="ec-hint">답이 맞아도 과정이 틀린 경우(보지 않고 맞힌 경우)를 잡습니다. 그때 부른 Tool: ' + esc(toolsText(st.draft.tools)) + '</div>';
		}
		el.innerHTML = html;
		Array.prototype.forEach.call(el.querySelectorAll("input, select"), function (input) {
			input.addEventListener("input", function () { readFields(ui); updateReadout(ui); });
			input.addEventListener("change", function () { readFields(ui); updateReadout(ui); });
		});
		var regex = document.getElementById("ec-regex");
		if (regex) {
			regex.addEventListener("change", function () {
				document.getElementById("ec-regex-row").hidden = !regex.checked;
				if (regex.checked && !ui.pattern) {
					var spec = buildSpec(ui);
					if (spec && spec.value && spec.value.pattern) {
						document.getElementById("ec-pattern").value = spec.value.pattern;
						ui.pattern = spec.value.pattern;
					}
				}
			});
		}
		readFields(ui);
		updateReadout(ui);
	}

	function step(title, inner) {
		return '<div class="ec-step"><div class="ec-step-title">' + title + '</div>' + inner + '</div>';
	}

	function readFields(ui) {
		var v = function (id) { var e = document.getElementById(id); return e ? e.value : undefined; };
		var c = function (id) { var e = document.getElementById(id); return e ? e.checked : undefined; };
		if (v("ec-file") !== undefined) { ui.file = v("ec-file").trim(); }
		if (v("ec-field") !== undefined) { ui.field = v("ec-field"); }
		if (v("ec-a") !== undefined) { ui.a = v("ec-a"); }
		if (v("ec-b") !== undefined) { ui.b = v("ec-b"); }
		if (c("ec-scope") !== undefined) { ui.scope = c("ec-scope"); }
		if (c("ec-regex") !== undefined) { ui.regex = c("ec-regex"); }
		if (v("ec-pattern") !== undefined) { ui.pattern = v("ec-pattern"); }
		if (v("ec-tool") !== undefined) { ui.tool = v("ec-tool").trim(); }
		if (v("ec-min") !== undefined) { ui.min = Number(v("ec-min")) || 1; }
		var fmode = document.querySelector('input[name="ec-fmode"]:checked');
		if (fmode) { ui.fileMode = fmode.value; }
		var mode = document.querySelector('input[name="ec-mode"]:checked');
		if (mode) { ui.mode = mode.value; }
	}

	function regexEscape(text) {
		return String(text).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
	}

	/** 글자 칸·체크박스로 정규식을 만든다. 항목 하나 안 = "}, {"를 넘지 않음, 같은 줄 안 = 줄바꿈을 넘지 않음 */
	function buildPattern(ui, target) {
		if (ui.regex) {
			return ui.pattern;
		}
		if (!ui.a) {
			return "";
		}
		var pattern = regexEscape(ui.a);
		if (ui.b) {
			var gap = ui.scope ? (target === "answer" ? "((?!\\}, \\{).)*?" : "[^\\n]*?") : ".*?";
			pattern += gap + regexEscape(ui.b);
		}
		return pattern;
	}

	/** 지금 고른 값으로 {kind, value, sentence}를 만든다. 모자라면 {error} */
	function buildSpec(ui) {
		var s = ui.symptom;
		var shape = function () {
			if (ui.regex) {
				return "정규식 /" + ui.pattern + "/에 맞는 곳";
			}
			return "'" + ui.a + "'" + (ui.b ? " 뒤에 '" + ui.b + "'" : "");
		};
		if (!s) {
			return { error: "증상을 고르세요." };
		}
		if (s === "fileMissing") {
			if (!ui.file) { return { error: "파일을 고르거나 적으세요." }; }
			return ui.fileMode === "changed"
				? { kind: "fileChanged", value: ui.file, sentence: "'" + ui.file + "' 파일이 처음 내용과 달라져 있어야 한다(없던 파일이 생긴 것도 포함)" }
				: { kind: "fileExists", value: ui.file, sentence: "작업 폴더에 '" + ui.file + "' 파일이 있어야 한다" };
		}
		if (s === "fileWrong" || s === "fileLacks") {
			if (!ui.file) { return { error: "파일을 고르거나 적으세요." }; }
			if (ui.regex ? !ui.pattern : !ui.a) { return { error: "찾을 글자를 적으세요." }; }
			var where = "'" + ui.file + "' 파일" + (ui.b && ui.scope && !ui.regex ? "의 같은 줄 안" : "") + "에 ";
			if (s === "fileWrong") {
				return { kind: "fileNotMatches", value: { file: ui.file, pattern: buildPattern(ui, "file") }, sentence: where + shape() + "이(가) 나오면 안 된다" };
			}
			if (!ui.b && !ui.regex) {
				return { kind: "fileContains", value: { file: ui.file, text: ui.a }, sentence: where + "'" + ui.a + "'이(가) 들어 있어야 한다" };
			}
			return { kind: "fileMatches", value: { file: ui.file, pattern: buildPattern(ui, "file") }, sentence: where + shape() + "이(가) 있어야 한다" };
		}
		if (s === "answerWrong") {
			var place = (ui.field ? "답의 [" + ui.field + "] 칸" : "답 전체");
			if (ui.mode === "equals") {
				if (!ui.field) { return { error: "값을 비교할 칸을 고르세요." }; }
				if (!ui.a) { return { error: "같아야 할 값을 '찾을 글자'에 적으세요." }; }
				return { kind: "answerEquals", value: { field: ui.field, value: ui.a }, sentence: place + "의 값이 정확히 '" + ui.a + "'여야 한다" };
			}
			if (ui.regex ? !ui.pattern : !ui.a) { return { error: "찾을 글자를 적으세요." }; }
			var scoped = place + (ui.b && ui.scope && !ui.regex ? "의 한 항목 안" : "") + "에 ";
			var withField = function (value) {
				if (ui.field) { value.field = ui.field; }
				return value;
			};
			if (ui.mode === "present" && !ui.b && !ui.regex) {
				return { kind: "answerContains", value: withField({ text: ui.a }), sentence: scoped + "'" + ui.a + "'이(가) 있어야 한다" };
			}
			if (ui.mode === "present") {
				return { kind: "answerMatches", value: withField({ pattern: buildPattern(ui, "answer") }), sentence: scoped + shape() + "이(가) 있어야 한다" };
			}
			return { kind: "answerNotMatches", value: withField({ pattern: buildPattern(ui, "answer") }), sentence: scoped + shape() + "이(가) 나오면 안 된다" };
		}
		if (s === "answerEmpty") {
			if (!ui.field) { return { error: "칸을 고르세요." }; }
			return { kind: "answerNotEmpty", value: ui.field, sentence: "답의 [" + ui.field + "] 칸이 비어 있지 않아야 한다" };
		}
		if (s === "guessed") {
			if (!ui.tool) { return { error: "Tool 이름을 적으세요." }; }
			return { kind: "toolUsed", value: { name: ui.tool, min: ui.min || 1 }, sentence: "이름이 '" + ui.tool + "'로 시작하는 Tool을 " + (ui.min || 1) + "번 이상 불러야 한다" };
		}
		return { error: "이 증상은 이 화면에서 만들 수 없습니다." };
	}

	function updateReadout(ui) {
		var spec = buildSpec(ui);
		var el = document.getElementById("ec-readout");
		el.textContent = spec.error ? spec.error : "▶ 읽어 보면: " + spec.sentence;
		var name = document.getElementById("ec-name");
		if (name && !spec.error && (!name.value || name.getAttribute("data-auto") === "1")) {
			name.value = spec.sentence.length > 60 ? spec.sentence.substring(0, 60) + "…" : spec.sentence;
			name.setAttribute("data-auto", "1");
		}
		if (name && !name.getAttribute("data-bound")) {
			name.setAttribute("data-bound", "1");
			name.addEventListener("input", function () { name.removeAttribute("data-auto"); });
		}
	}

	function commitEditor() {
		var ui = st.ui;
		readFields(ui);
		var spec = buildSpec(ui);
		if (spec.error) {
			alert(spec.error);
			return;
		}
		var name = document.getElementById("ec-name").value.trim() || spec.sentence;
		var check = { name: name, kind: spec.kind, value: spec.value, sentence: spec.sentence, ui: JSON.parse(JSON.stringify(ui)) };
		if (st.editing >= 0) {
			st.checks[st.editing] = check;
		} else {
			st.checks.push(check);
		}
		closeEditor();
		stalePreview();
		renderChecks();
	}

	/* ---------- 미리 채점 · 저장 ---------- */

	function requestBody() {
		var d = st.draft;
		var work = document.getElementById("ec-work");
		return {
			executionId: d.executionId,
			snapshot: d.kind === "agent" ? d.snapshot : null,
			id: document.getElementById("ec-id").value.trim(),
			projectId: document.getElementById("ec-project").value,
			description: document.getElementById("ec-desc").value,
			note: document.getElementById("ec-note").value,
			workKey: work ? work.value : null,
			approvals: null,
			checks: st.checks.map(function (c) { return { name: c.name, kind: c.kind, value: c.value }; })
		};
	}

	function validate(body) {
		if (!body.id) { return "사례 id를 적으세요."; }
		if (!body.projectId) { return "프로젝트를 고르세요."; }
		if (body.checks.length === 0) { return "채점 기준을 하나 이상 넣으세요."; }
		return null;
	}

	function preview() {
		var body = requestBody();
		var problem = validate(body);
		if (problem) {
			setStatus(problem, "error");
			return;
		}
		setStatus("그때 결과로 채점하는 중…");
		DstoneAi.api("POST", "/api/ai/eval/drafts/preview", body)
			.then(function (result) {
				st.preview = result.checks || [];
				document.getElementById("ec-yaml").textContent = result.yaml || "";
				renderChecks();
				var core = 0;
				var caught = 0;
				st.checks.forEach(function (c, i) {
					if (!BASIC_KINDS[c.kind] && st.preview[i] && st.preview[i].ok != null) {
						core++;
						if (!st.preview[i].ok) { caught++; }
					}
				});
				var warn = (result.warnings || []).length ? " · 참고: " + result.warnings.join(" / ") : "";
				setStatus("미리 채점 완료 — 핵심 기준 " + core + "개 중 " + caught + "개가 그때 실수를 잡습니다." + warn, caught === core ? "done" : "");
			})
			.catch(function (err) { setStatus("미리 채점 실패: " + err.message, "error"); });
	}

	function save() {
		var body = requestBody();
		var problem = validate(body);
		if (problem) {
			setStatus(problem, "error");
			return;
		}
		if (!st.preview && !confirm("아직 미리 채점하지 않았습니다. 그대로 저장할까요?")) {
			return;
		}
		setStatus("저장하는 중…");
		DstoneAi.api("POST", "/api/ai/eval/drafts/save", body)
			.then(function (result) {
				st.saved = true;
				document.getElementById("ec-yaml").textContent = result.yaml || "";
				setStatus("저장했습니다: conf/eval/" + result.casePath + " — Agent 평가 화면(/ai/admin/eval/eval.html)에서 돌려 보세요.", "done");
			})
			.catch(function (err) { setStatus("저장 실패: " + err.message, "error"); });
	}

	function stalePreview() {
		if (st && st.preview) {
			st.preview = null;
			setStatus("내용이 바뀌었습니다. 다시 미리 채점하세요.");
		}
	}

	function setStatus(text, kind) {
		var el = document.getElementById("ec-status");
		el.textContent = text || "";
		el.className = "ec-status" + (kind ? " " + kind : "");
	}

	return {
		openStep: openStep,
		openExecution: openExecution
	};
})();
