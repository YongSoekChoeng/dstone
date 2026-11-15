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

	/** 호출 해석률 눈금. 프로젝트 · 분석 화면의 "한눈에 보기"(project.js verdictBox)와 같은 기준 */
	var RATE_GOOD = 95;
	var RATE_WARN = 80;

	function init() {
		["kn-dash-updated", "kn-dash-refresh-btn", "kn-dash-message", "kn-dash-cards", "kn-dash-start", "kn-dash-projects",
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
				el["kn-dash-cards"].innerHTML = serverCard(health);
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
		renderCards(health, data);
		el["kn-dash-start"].hidden = projects.length > 0;
		renderProjects(projects);
		renderJobs(data.recentJobs || []);
		renderEmbedding(data.embedding || {});
		renderDocuments(data.documents || {});
	}

	/** 숫자 카드 5장 */
	function renderCards(health, data) {
		var totals = data.totals || {};
		var embedding = data.embedding || {};
		var docs = (data.documents || {}).totals || {};
		var html = serverCard(health);

		html += card("프로젝트", format(totals.projects), "분석이 끝난 리비전 " + format(totals.readyRevisions) + "개", "", "knowledge/index");

		var active = number(totals.activeJobs);
		var failed = number(totals.failedJobs7d);
		html += card("진행 중인 분석", format(active), failed > 0 ? "최근 7일 실패 " + format(failed) + "건" : "최근 7일 실패 없음",
			failed > 0 || active > 0 ? "kn-card-warn" : "", "knowledge/index");

		if (embedding.error || !embedding.totals) {
			html += card("임베딩", "-", embedding.error ? "읽지 못했습니다" : "정보 없음", "", "knowledge/index");
		} else {
			var state = stateOf(embedding.state);
			html += card("임베딩", embedding.totals.percent + "%", state.label + (number(embedding.totals.pending) > 0 ? " · 남은 " + format(embedding.totals.pending) + "건" : ""),
				embedding.state === "DONE" ? "kn-card-ok" : embedding.state === "STOPPED" ? "kn-card-danger" : "", "knowledge/index");
		}

		html += card("올린 문서", format(docs.documents), "묶음 " + format(docs.collections) + "개 · 청크 " + format(docs.chunks) + "개", "", "knowledge/search/search");
		el["kn-dash-cards"].innerHTML = html;
	}

	/** 서버 상태 카드. UP 이 아니면 빨간 카드에 이유를 적는다 */
	function serverCard(health) {
		var up = health && health.status === "UP";
		var help = up ? "분석기 " + (health.analyzerVersion || "-") + " · DB " + ((health.db && health.db.database) || "-")
			: health && health.status === "SCHEMA_NOT_READY" ? "DB 테이블이 덜 만들어졌습니다(스키마 SQL 확인)"
			: "dstone-knowledge 또는 DB 에 연결할 수 없습니다";
		return "<div class=\"kn-card kn-dash-card" + (up ? " kn-card-ok" : " kn-card-danger") + "\"><div class=\"kn-card-value\">" + K.badge(health ? health.status : "DOWN")
			+ "</div><div class=\"kn-card-label\">서버 상태</div><div class=\"kn-card-help\">" + K.escapeHtml(help) + "</div></div>";
	}

	/** 카드 한 장. link 가 있으면 카드 전체가 그 화면으로 가는 링크가 된다 */
	function card(label, value, help, css, link) {
		var inner = "<div class=\"kn-card-value\">" + K.escapeHtml(value) + "</div><div class=\"kn-card-label\">" + K.escapeHtml(label)
			+ "</div><div class=\"kn-card-help\">" + K.escapeHtml(help) + "</div>";
		var cls = "kn-card kn-dash-card" + (css ? " " + css : "");
		if (link) {
			return "<a class=\"" + cls + "\" href=\"" + K.escapeHtml(K.pageUrl(link)) + "\">" + inner + "</a>";
		}
		return "<div class=\"" + cls + "\">" + inner + "</div>";
	}

	/** 프로젝트 현황 표 */
	function renderProjects(projects) {
		K.table(el["kn-dash-projects"], [
			{ key: "projectId", label: "프로젝트", html: projectCell },
			{ key: "ready", label: "분석이 끝난 리비전", html: readyCell },
			{ key: "latestStatus", label: "최근 리비전", html: latestCell },
			{ key: "files", label: "파일", num: true, html: function (row) { return countCell(row, "files"); } },
			{ key: "methods", label: "메소드", num: true, html: function (row) { return countCell(row, "methods"); } },
			{ key: "endpoints", label: "진입점", num: true, html: function (row) { return countCell(row, "endpoints"); } },
			{ key: "callResolutionRate", label: "호출 해석률", html: rateCell },
			{ key: "links", label: "", html: linksCell }
		], projects, "등록된 프로젝트가 없습니다.");
	}

	function projectCell(row) {
		var hash = "p=" + encodeURIComponent(row.projectId);
		if (row.readyRevisionId) {
			hash += "&r=" + row.readyRevisionId + "&l=" + encodeURIComponent(row.readyRevisionLabel || "");
		}
		var name = row.projectName && row.projectName !== row.projectId ? "<div class=\"kn-dash-sub\">" + K.escapeHtml(row.projectName) + "</div>" : "";
		return "<a class=\"kn-dash-project\" href=\"" + K.escapeHtml(K.pageUrl("knowledge/index", hash)) + "\">" + K.escapeHtml(row.projectId) + "</a>" + name;
	}

	function readyCell(row) {
		if (!row.readyRevisionId) {
			return "<span class=\"ai-hint\">아직 없음</span>";
		}
		return "#" + row.readyRevisionId + " " + K.escapeHtml(row.readyRevisionLabel) + "<div class=\"kn-dash-sub\">" + K.escapeHtml(row.readyCreatedAt) + "</div>";
	}

	/** 가장 최근 리비전이 분석이 끝난 리비전과 다를 때(분석 중이거나 실패)만 그 리비전을 따로 적는다 */
	function latestCell(row) {
		if (!row.latestRevisionId) {
			return "<span class=\"ai-hint\">분석한 적 없음</span>";
		}
		var html = K.badge(number(row.activeJobs) > 0 && row.latestStatus !== "ANALYZING" ? "ANALYZING" : row.latestStatus);
		if (row.latestRevisionId !== row.readyRevisionId) {
			html += "<div class=\"kn-dash-sub\">#" + row.latestRevisionId + " " + K.escapeHtml(row.latestRevisionLabel) + "</div>";
		}
		html += "<div class=\"kn-dash-sub\">리비전 " + format(row.revisionCount) + "개</div>";
		return html;
	}

	function countCell(row, key) {
		return row.readyRevisionId ? K.escapeHtml(format(row[key])) : "";
	}

	/** 호출 해석률: 숫자 + 짧은 막대. 95% 이상 초록, 80% 이상 주황, 그 밑은 빨강 */
	function rateCell(row) {
		if (row.callResolutionRate === null || row.callResolutionRate === undefined) {
			return row.readyRevisionId ? "<span class=\"ai-hint\">-</span>" : "";
		}
		var rate = Number(row.callResolutionRate);
		var level = rate >= RATE_GOOD ? "good" : rate >= RATE_WARN ? "warn" : "bad";
		var tip = rate >= RATE_GOOD ? "호출 관계와 영향도 결과를 믿고 써도 됩니다."
			: rate >= RATE_WARN ? "빠진 호출이 있을 수 있습니다. 라이브러리(jar) 위치를 확인하세요."
			: "빠진 호출이 많습니다. 프로젝트에 라이브러리(jar) 위치를 적고 다시 분석하세요.";
		return "<div class=\"kn-dash-rate kn-dash-rate-" + level + "\" title=\"" + K.escapeHtml(tip) + "\"><span>" + rate.toFixed(1) + "%</span>"
			+ "<i><b style=\"width:" + Math.max(0, Math.min(100, rate)) + "%\"></b></i></div>";
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
				var html = K.badge(row.status);
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
