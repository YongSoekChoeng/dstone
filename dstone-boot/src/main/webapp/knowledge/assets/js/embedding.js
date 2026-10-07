/**
 * "임베딩 현황" 영역(프로젝트 · 분석 화면 위쪽).
 * 임베딩 대기열 전체가 얼마나 밀려 있고, 지금 돌고 있고, 언제쯤 끝날지를 보여 줍니다. 10초마다 다시 조회합니다.
 *
 * 임베딩은 분석과 따로, dstone-knowledge 서버 안에서 뒤에서 돕니다. 서버가 떠 있는 동안에만 진행되고,
 * 끝난 것은 건별로 저장되므로 서버를 내렸다 올려도 이어서 합니다.
 */
var DstoneKnowledgeEmbedding = (function () {

	var K = DstoneKnowledge;
	var REFRESH_MILLIS = 10000;
	var el = {};
	var timer = null;

	/** 상태 한 낱말을 화면의 말과 딱지 색으로. 색은 AI 화면의 딱지(workflow-badge-*)를 같이 쓴다 */
	var STATES = {
		DONE: { label: "완료", css: "done", text: "밀린 임베딩이 없습니다. 뜻으로 찾는 검색을 온전히 쓸 수 있습니다." },
		WORKING: { label: "진행 중", css: "running", text: "임베딩이 진행 중입니다. 끝난 것부터 뜻으로 찾는 검색에 나옵니다. 이름으로 찾는 검색, 호출 관계, 영향도, 노드 맵은 임베딩과 상관없이 쓸 수 있습니다." },
		WAITING: { label: "기다리는 중", css: "waiting_approval", text: "남은 것이 있는데 최근 10분 동안 처리한 것이 없습니다. 서버를 방금 띄웠거나, 임베딩 서버(Ollama)가 내려가 있거나, 검색에 양보하는 중일 수 있습니다." },
		STOPPED: { label: "멈춤", css: "cancelled", text: "남은 것이 있는데 임베딩 작업이 돌고 있지 않습니다. dstone-knowledge 설정의 dstone.knowledge.rag.embedding.enabled 를 확인하세요." }
	};

	function init() {
		["kn-embed-badge", "kn-embed-message", "kn-embed-bar", "kn-embed-cards", "kn-embed-error", "kn-embed-projects", "kn-embed-refresh"].forEach(function (id) {
			el[id] = document.getElementById(id);
		});
		el["kn-embed-refresh"].addEventListener("click", load);
		load();
		timer = setInterval(load, REFRESH_MILLIS);
	}

	function load() {
		K.call("GET", "/api/system/embedding").then(render).catch(function (err) {
			// 예전 버전의 dstone-knowledge 에는 이 API 가 없다. 화면의 다른 기능은 그대로 쓸 수 있으므로 조용히 알리고 그만 조회한다.
			el["kn-embed-badge"].innerHTML = "";
			el["kn-embed-cards"].innerHTML = "";
			el["kn-embed-bar"].style.display = "none";
			el["kn-embed-projects"].innerHTML = "";
			K.message(el["kn-embed-message"], "임베딩 현황을 가져오지 못했습니다: " + err.message, true);
			if (timer) {
				clearInterval(timer);
				timer = null;
			}
		});
	}

	function render(status) {
		var state = STATES[status.state] || { label: status.state, css: "cancelled", text: "" };
		var totals = status.totals || {};
		var recent = status.recent || {};
		el["kn-embed-badge"].innerHTML = "<span class=\"workflow-badge workflow-badge-" + state.css + "\">" + K.escapeHtml(state.label) + "</span>";
		K.message(el["kn-embed-message"], state.text + " (모델 " + status.model + ", " + timeText(new Date()) + " 기준, 10초마다 갱신)");

		el["kn-embed-bar"].style.display = "";
		el["kn-embed-bar"].firstElementChild.style.width = Math.max(0, Math.min(100, totals.percent || 0)) + "%";

		K.cards(el["kn-embed-cards"], [
			{ label: "진행률", value: (totals.percent === undefined ? "-" : totals.percent + "%") },
			{ label: "끝난 것", value: totals.done },
			{ label: "남은 것", value: totals.pending },
			{ label: "실패", value: totals.failed },
			{ label: "최근 속도 (건/분)", value: recent.itemsPerMinute === undefined ? "-" : recent.itemsPerMinute },
			{ label: "예상 남은 시간", value: etaText(status) }
		]);

		var notes = [];
		if (status.worker && status.worker.lastError) {
			notes.push("마지막 오류: " + status.worker.lastError + " (30초마다 다시 시도합니다)");
		}
		if (totals.failed > 0) {
			notes.push("실패 " + totals.failed + "건은 세 번 시도하고 그만둔 것입니다. 이유: " + (status.failedReasons || []).map(function (row) {
				return row.reason + " (" + row.items + "건)";
			}).join(" / "));
		}
		el["kn-embed-error"].textContent = notes.join("  ·  ");
		el["kn-embed-error"].style.display = notes.length > 0 ? "" : "none";

		var projects = status.pendingByProject || [];
		if (projects.length === 0) {
			el["kn-embed-projects"].innerHTML = "";
			return;
		}
		el["kn-embed-projects"].innerHTML = "<div class=\"kn-section-title\">프로젝트별 남은 것 <span class=\"ai-hint\">(위에서부터 먼저 처리합니다. 올린 문서가 코드보다 먼저입니다)</span></div><div></div>";
		K.table(el["kn-embed-projects"].lastElementChild, [
			{ key: "project", label: "프로젝트" },
			{ key: "items", label: "남은 건수", num: true },
			{ key: "chars", label: "남은 글자 수", num: true },
			{ key: "share", label: "남은 일에서 차지하는 몫", html: function (row) {
				return totals.pendingChars > 0 ? Math.round(row.chars * 100 / sumChars(projects)) + "%" : "";
			} }
		], projects);
	}

	function sumChars(projects) {
		var sum = 0;
		projects.forEach(function (project) {
			sum += project.chars || 0;
		});
		return sum || 1;
	}

	/** 남은 시간을 "약 1시간 5분"처럼. 알 수 없으면 그 이유를 짧게 */
	function etaText(status) {
		if (status.totals && status.totals.pending === 0) {
			return "끝남";
		}
		if (status.etaSeconds === null || status.etaSeconds === undefined) {
			return "알 수 없음";
		}
		var minutes = Math.max(1, Math.round(status.etaSeconds / 60));
		if (minutes < 60) {
			return "약 " + minutes + "분";
		}
		return "약 " + Math.floor(minutes / 60) + "시간 " + (minutes % 60) + "분";
	}

	function timeText(date) {
		function two(value) {
			return (value < 10 ? "0" : "") + value;
		}
		return two(date.getHours()) + ":" + two(date.getMinutes()) + ":" + two(date.getSeconds());
	}

	return { init: init };
})();
