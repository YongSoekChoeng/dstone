/**
 * "대시보드" 화면(코드 분석의 첫 화면).
 *
 * 숫자는 dstone-knowledge 의 GET /api/dashboard 한 번으로 모두 받습니다. 서버 상태만 /api/system/health 로 따로 묻습니다
 * (DB 가 내려가 있으면 대시보드 API 가 실패하므로, 왜 실패했는지 알려 주려고 따로 둡니다).
 * 돌고 있는 분석이 있거나 임베딩이 진행 중이면 30초마다 다시 읽습니다. 다른 탭을 보고 있는 동안에는 쉽니다.
 */
var DstoneKnowledgeDashboard = (function () {

	var K = DstoneKnowledge;
	var REFRESH_MILLIS = 30000;
	var el = {};
	var timer = null;
	var loading = false;

	/** 호출 해석률 눈금. 분석 실행 · 진단 화면의 "한눈에 보기"(project.js verdictBox)와 같은 기준 */
	var RATE_GOOD = 95;
	var RATE_WARN = 80;

	function init() {
		["kn-dash-updated", "kn-dash-refresh-btn", "kn-dash-message", "kn-dash-status", "kn-dash-start", "kn-dash-projects", "kn-dash-project-count",
			"kn-dash-jobs", "kn-dash-embed-badge", "kn-dash-embed", "kn-dash-docs"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		el["kn-dash-refresh-btn"].addEventListener("click", load);
		document.addEventListener("visibilitychange", function () {
			// 다른 탭에 있다가 돌아오면 바로 새로 읽는다
			if (!document.hidden && timer === null) {
				load();
			}
		});
		load();
	}

	/* ---------------- 읽기 ---------------- */

	function load() {
		if (loading) {
			return;
		}
		loading = true;
		stopTimer();
		Promise.all([
			K.call("GET", "/api/system/health").catch(function (err) { return { status: "DOWN", error: err.message }; }),
			K.call("GET", "/api/dashboard")
		]).then(function (results) {
			K.message(el["kn-dash-message"], "");
			render(results[0], results[1]);
			el["kn-dash-updated"].textContent = results[1].generatedAt + " 기준";
			if (needsRefresh(results[1])) {
				startTimer();
			}
		}).catch(function (err) {
			// 대시보드 API 가 안 되면 서버 상태라도 보여 준다
			K.call("GET", "/api/system/health").catch(function (healthErr) {
				return { status: "DOWN", error: healthErr.message };
			}).then(function (health) {
				el["kn-dash-status"].innerHTML = serverItem(health);
			});
			K.message(el["kn-dash-message"], "현황을 읽지 못했습니다: " + err.message, true);
		}).then(function () {
			loading = false;
		});
	}

	/** 돌고 있는 분석이 있거나 임베딩이 밀려 있을 때만 다시 읽는다 */
	function needsRefresh(data) {
		var embedding = data.embedding || {};
		return number(data.totals && data.totals.activeJobs) > 0 || embedding.state === "WORKING" || embedding.state === "WAITING";
	}

	function startTimer() {
		if (document.hidden) {
			return;
		}
		timer = setTimeout(function () {
			timer = null;
			load();
		}, REFRESH_MILLIS);
	}

	function stopTimer() {
		if (timer !== null) {
			clearTimeout(timer);
			timer = null;
		}
	}

	/* ---------------- 그리기 ---------------- */

	function render(health, data) {
		var projects = data.projects || [];
		renderStatus(health, data);
		el["kn-dash-start"].hidden = projects.length > 0;
		renderProjects(projects);
		renderJobs(data.recentJobs || []);
		renderEmbedding(data.embedding || {});
		renderDocuments(data.documents || {});
	}

	/**
	 * 맨 위 상태 한 줄: 서버 · 분석 작업 · 임베딩 · 올린 문서.
	 * 평소에는 조용한 회색 글씨이고, 문제가 있을 때(서버 안 됨, 실패, 임베딩 멈춤)만 색이 붙는다.
	 */
	function renderStatus(health, data) {
		var totals = data.totals || {};
		var embedding = data.embedding || {};
		var docs = (data.documents || {}).totals || {};
		var html = serverItem(health);

		var active = number(totals.activeJobs);
		var failed = number(totals.failedJobs7d);
		var jobs = active > 0 ? "분석 " + format(active) + "건 진행 중" : "진행 중인 분석 없음";
		if (failed > 0) {
			jobs += " · 최근 7일 실패 " + format(failed) + "건";
		}
		html += statusItem("분석", jobs, active > 0 ? "busy" : failed > 0 ? "warn" : "", "knowledge/index");

		if (embedding.error || !embedding.totals) {
			html += statusItem("임베딩", embedding.error ? "읽지 못함" : "정보 없음", "warn", "knowledge/index");
		} else {
			var state = stateOf(embedding.state);
			html += statusItem("임베딩", embedding.totals.percent + "% · " + state.label,
				embedding.state === "STOPPED" ? "bad" : embedding.state === "WORKING" || embedding.state === "WAITING" ? "busy" : "", "knowledge/index");
		}

		html += statusItem("올린 문서", format(docs.documents) + "건 · 묶음 " + format(docs.collections) + "개", "", "knowledge/search/search");
		el["kn-dash-status"].innerHTML = html;
	}

	/** 서버 상태. UP 이 아니면 빨간색으로 이유를 적는다 */
	function serverItem(health) {
		var up = health && health.status === "UP";
		var text = up ? "정상 · 분석기 " + (health.analyzerVersion || "-")
			: health && health.status === "SCHEMA_NOT_READY" ? "DB 테이블이 덜 만들어졌습니다(스키마 SQL 확인)"
			: "dstone-knowledge 또는 DB 에 연결할 수 없습니다";
		return statusItem("서버", text, up ? "ok" : "bad", "");
	}

	/** 상태 한 칸. level: ok / busy / warn / bad / "" (보통). link 가 있으면 그 화면으로 가는 링크 */
	function statusItem(name, text, level, link) {
		var inner = "<i aria-hidden=\"true\"></i><b>" + K.escapeHtml(name) + "</b> " + K.escapeHtml(text);
		var cls = "kn-dash-status-item" + (level ? " kn-dash-status-" + level : "");
		if (link) {
			return "<a class=\"" + cls + "\" href=\"" + K.escapeHtml(K.pageUrl(link)) + "\">" + inner + "</a>";
		}
		return "<span class=\"" + cls + "\">" + inner + "</span>";
	}

	/** 프로젝트마다 한 장 */
	function renderProjects(projects) {
		el["kn-dash-project-count"].textContent = projects.length > 0 ? format(projects.length) : "";
		var html = "";
		for (var i = 0; i < projects.length; i++) {
			html += projectCard(projects[i]);
		}
		el["kn-dash-projects"].innerHTML = html;
	}

	function projectCard(row) {
		var html = "<article class=\"kn-flow-card\">";
		html += "<header class=\"kn-flow-head\"><div class=\"kn-flow-name\">" + projectLink(row) + revisionText(row) + "</div>"
			+ linksCell(row) + "</header>";
		if (!row.readyRevisionId) {
			html += "<p class=\"kn-flow-empty\">" + (row.latestRevisionId ? "아직 분석이 끝난 리비전이 없습니다." : "아직 분석하지 않았습니다. 프로젝트 화면에서 분석을 시작하세요.") + "</p>";
			return html + "</article>";
		}
		html += flowStrip(row);
		html += "<footer class=\"kn-flow-foot\"><span>진입점 <b>" + format(row.endpoints) + "</b></span><span>메소드 <b>" + format(row.methods)
			+ "</b></span><span>파일 <b>" + format(row.files) + "</b></span>" + rateCell(row)
			+ (row.parseSuccessRate === null || row.parseSuccessRate === undefined ? "" : "<span>파싱 성공 <b>" + Number(row.parseSuccessRate).toFixed(1) + "%</b></span>")
			+ "</footer>";
		return html + "</article>";
	}

	function projectLink(row) {
		var hash = "p=" + encodeURIComponent(row.projectId);
		if (row.readyRevisionId) {
			hash += "&r=" + row.readyRevisionId + "&l=" + encodeURIComponent(row.readyRevisionLabel || "");
		}
		var name = row.projectName && row.projectName !== row.projectId ? " <span class=\"kn-flow-alias\">" + K.escapeHtml(row.projectName) + "</span>" : "";
		return "<a class=\"kn-flow-project\" href=\"" + K.escapeHtml(K.pageUrl("knowledge/project/project", hash)) + "\">" + K.escapeHtml(row.projectId) + "</a>" + name;
	}

	/**
	 * 이름 옆 리비전 설명: 분석이 끝난 리비전과 그 시각.
	 * 가장 최근 리비전이 그것과 다르면(분석 중이거나 실패) 그 상태를 딱지로 덧붙인다.
	 */
	function revisionText(row) {
		var html = "<span class=\"kn-flow-rev\">";
		if (row.readyRevisionId) {
			html += "#" + row.readyRevisionId + " · " + K.escapeHtml(row.readyCreatedAt) + " 분석";
		}
		if (row.latestRevisionId && (row.latestRevisionId !== row.readyRevisionId || number(row.activeJobs) > 0)) {
			html += " " + K.badge(number(row.activeJobs) > 0 && row.latestStatus !== "ANALYZING" ? "ANALYZING" : row.latestStatus)
				+ (row.latestRevisionId !== row.readyRevisionId ? " <span class=\"kn-flow-alias\">#" + row.latestRevisionId + "</span>" : "");
		}
		return html + " · 리비전 " + format(row.revisionCount) + "개</span>";
	}

	/** 화면 → 컨트롤러 → 서비스 → DAO → SQL → 테이블. 칸을 누르면 그 리비전의 노드 맵이 열린다 */
	function flowStrip(row) {
		return K.layerStrip(row, K.pageUrl("knowledge/graph/graph", "p=" + encodeURIComponent(row.projectId) + "&r=" + row.readyRevisionId));
	}

	/** 호출 해석률: 숫자 + 짧은 막대. 95% 이상 초록, 80% 이상 주황, 그 밑은 빨강 */
	function rateCell(row) {
		if (row.callResolutionRate === null || row.callResolutionRate === undefined) {
			return "";
		}
		var rate = Number(row.callResolutionRate);
		var level = rate >= RATE_GOOD ? "good" : rate >= RATE_WARN ? "warn" : "bad";
		var tip = rate >= RATE_GOOD ? "호출 관계와 영향도 결과를 믿고 써도 됩니다."
			: rate >= RATE_WARN ? "빠진 호출이 있을 수 있습니다. 라이브러리(jar) 위치를 확인하세요."
			: "빠진 호출이 많습니다. 프로젝트에 라이브러리(jar) 위치를 적고 다시 분석하세요.";
		return "<span class=\"kn-dash-rate kn-dash-rate-" + level + "\" title=\"" + K.escapeHtml(tip) + "\">호출 해석률 <b>" + rate.toFixed(1) + "%</b>"
			+ "<i><b style=\"width:" + Math.max(0, Math.min(100, rate)) + "%\"></b></i></span>";
	}

	/** 분석이 끝난 리비전이 있으면 그 리비전으로 영향도 · 노드 맵을 바로 연다 */
	function linksCell(row) {
		if (!row.readyRevisionId) {
			return "";
		}
		var hash = "p=" + encodeURIComponent(row.projectId) + "&r=" + row.readyRevisionId;
		return "<span class=\"kn-dash-actions\"><a href=\"" + K.escapeHtml(K.pageUrl("knowledge/impact/impact", hash)) + "\">영향도</a>"
			+ "<a href=\"" + K.escapeHtml(K.pageUrl("knowledge/graph/graph", hash)) + "\">노드 맵</a></span>";
	}

	/** 최근 분석 작업 표. 실패한 것은 이유의 첫 줄을 같이 보여 준다 */
	function renderJobs(jobs) {
		K.table(el["kn-dash-jobs"], [
			{ key: "projectId", label: "프로젝트 · 리비전", html: function (row) {
				return K.escapeHtml(row.projectId) + "<div class=\"kn-dash-sub\">#" + row.revisionId + " " + K.escapeHtml(row.revisionLabel) + "</div>";
			} },
			{ key: "status", label: "상태", html: function (row) {
				var html = K.badge(row.status, "job");
				if (row.status === "RUNNING" && row.currentPass) {
					html += "<div class=\"kn-dash-sub\">" + K.escapeHtml(row.currentPass)
						+ (number(row.totalFiles) > 0 ? " " + format(row.doneFiles) + " / " + format(row.totalFiles) : "") + "</div>";
				}
				if (row.errorMessage) {
					html += "<div class=\"kn-dash-error\" title=\"" + K.escapeHtml(row.errorMessage) + "\">" + K.escapeHtml(firstLine(row.errorMessage)) + "</div>";
				}
				return html;
			} },
			{ key: "elapsedSeconds", label: "걸린 시간", num: true, html: function (row) { return K.escapeHtml(duration(row.elapsedSeconds)); } },
			{ key: "createdAt", label: "시작", html: function (row) {
				return K.escapeHtml(row.createdAt) + "<div class=\"kn-dash-sub kn-mono\">" + K.escapeHtml(row.analysisId) + "</div>";
			} }
		], jobs, "아직 분석한 적이 없습니다.");
	}

	/** 임베딩: 진행 막대, 상태 설명, 건수, 남은 시간 */
	function renderEmbedding(status) {
		if (status.error || !status.totals) {
			el["kn-dash-embed-badge"].innerHTML = "";
			el["kn-dash-embed"].innerHTML = "<div class=\"kn-message kn-error\">임베딩 현황을 읽지 못했습니다" + (status.error ? ": " + K.escapeHtml(status.error) : ".") + "</div>";
			return;
		}
		var state = stateOf(status.state);
		var totals = status.totals;
		el["kn-dash-embed-badge"].innerHTML = "<span class=\"workflow-badge workflow-badge-" + K.escapeHtml(state.css) + "\">" + K.escapeHtml(state.label) + "</span>";
		var html = "<div class=\"kn-embed-bar\"><div style=\"width:" + Number(totals.percent) + "%\"></div></div>";
		html += "<p class=\"kn-dash-embed-text\">" + K.escapeHtml(state.text) + " <span class=\"kn-dash-sub-inline\">(모델 " + K.escapeHtml(status.model || "-") + ")</span></p>";
		html += "<dl class=\"kn-dash-facts\">"
			+ fact("끝남", format(totals.done)) + fact("남음", format(totals.pending)) + fact("실패", format(totals.failed))
			+ fact("남은 시간", status.etaSeconds === null || status.etaSeconds === undefined ? "어림할 수 없음" : "약 " + duration(status.etaSeconds))
			+ "</dl>";
		if (status.worker && status.worker.enabled === false) {
			html += "<div class=\"kn-message kn-error\">임베딩 작업이 꺼져 있습니다(dstone.knowledge.rag.embedding.enabled).</div>";
		}
		el["kn-dash-embed"].innerHTML = html;
	}

	/** 올린 문서: 묶음별 표 + 최근 올린 문서 몇 건 */
	function renderDocuments(documents) {
		var totals = documents.totals || {};
		if (number(totals.documents) === 0) {
			el["kn-dash-docs"].innerHTML = "<p class=\"ai-hint\">아직 올린 문서가 없습니다. <a href=\"" + K.escapeHtml(K.pageUrl("knowledge/search/search"))
				+ "\">검색 · 문서</a> 화면에서 PDF · Word · 텍스트 문서를 올리면 코드와 함께 찾을 수 있습니다.</p>";
			return;
		}
		var html = "<div class=\"kn-dash-docs\"><div><div class=\"kn-section-title\">묶음별</div><div id=\"kn-dash-collections\" class=\"kn-scroll\"></div></div>"
			+ "<div><div class=\"kn-section-title\">최근 올린 문서</div><div id=\"kn-dash-recent-docs\" class=\"kn-scroll\"></div></div></div>";
		el["kn-dash-docs"].innerHTML = html;
		K.table(document.getElementById("kn-dash-collections"), [
			{ key: "collection", label: "묶음", html: function (row) {
				return row.collection ? K.escapeHtml(row.collection) : "<span class=\"ai-hint\">(묶음 없음)</span>";
			} },
			{ key: "documents", label: "문서", num: true },
			{ key: "chunks", label: "청크", num: true },
			{ key: "embedded", label: "임베딩", html: function (row) { return embeddedCell(row.embedded, row.chunks, row.failed); } },
			{ key: "lastUploadedAt", label: "마지막으로 올린 시각" }
		], documents.collections || []);
		K.table(document.getElementById("kn-dash-recent-docs"), [
			{ key: "title", label: "문서", html: function (row) {
				var title = row.title || row.sourceId;
				// 제목을 따로 주지 않으면 파일 이름과 같다. 같으면 한 번만 적는다
				return K.escapeHtml(title) + (row.fileName && row.fileName !== title ? "<div class=\"kn-dash-sub\">" + K.escapeHtml(row.fileName) + "</div>" : "");
			} },
			{ key: "collection", label: "묶음" },
			{ key: "embedded", label: "임베딩", html: function (row) { return embeddedCell(row.embedded, row.chunks, 0); } },
			{ key: "createdAt", label: "올린 시각" }
		], documents.recent || []);
	}

	/** 청크 가운데 임베딩이 끝난 비율. 다 끝났으면 "완료" */
	function embeddedCell(embedded, chunks, failed) {
		var done = number(embedded);
		var all = number(chunks);
		var percent = all === 0 ? 100 : Math.floor(100 * done / all);
		var html = percent >= 100 ? "<span class=\"kn-dash-done\">완료</span>" : percent + "% <span class=\"kn-dash-sub-inline\">(" + format(done) + " / " + format(all) + ")</span>";
		if (number(failed) > 0) {
			html += " <span class=\"kn-dash-error-inline\">실패 " + format(failed) + "</span>";
		}
		return html;
	}

	/* ---------------- 작은 도우미 ---------------- */

	function stateOf(state) {
		var states = (window.DstoneKnowledgeEmbedding && DstoneKnowledgeEmbedding.STATES) || {};
		return states[state] || { label: state || "-", css: "cancelled", text: "" };
	}

	function fact(name, value) {
		return "<div><dt>" + K.escapeHtml(name) + "</dt><dd>" + K.escapeHtml(value) + "</dd></div>";
	}

	function number(value) {
		var n = Number(value);
		return isNaN(n) ? 0 : n;
	}

	function format(value) {
		if (value === null || value === undefined || value === "") {
			return "-";
		}
		return number(value).toLocaleString();
	}

	function firstLine(text) {
		var line = String(text || "").split("\n")[0];
		return line.length > 120 ? line.substring(0, 120) + "…" : line;
	}

	/** 초를 "1시간 5분", "3분 20초", "12초" 처럼 */
	function duration(seconds) {
		if (seconds === null || seconds === undefined) {
			return "-";
		}
		var s = Math.max(0, Math.round(Number(seconds)));
		var h = Math.floor(s / 3600);
		var m = Math.floor((s % 3600) / 60);
		if (h > 0) {
			return h + "시간 " + m + "분";
		}
		if (m > 0) {
			return m + "분 " + (s % 60) + "초";
		}
		return s + "초";
	}

	return { init: init };
})();
