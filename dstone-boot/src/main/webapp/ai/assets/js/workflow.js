var DstoneAiWorkflow = (function () {

	var urls = {};
	var POLL_INTERVAL_MS = 5000;

	var workflowIdEl, workflowIdDescEl, sessionIdEl, messageEl, variablesEl;
	var submitBtn, submitStatusEl;
	var executionIdEl, statusBadgeEl, resultEl, pollStopBtn, pollRefreshBtn;
	var historyBodyEl;

	var pollTimer = null;
	var activeExecutionId = null;
	var historyRows = {}; // executionId -> <tr> element, 화면에서 제출한 작업만 기억(서버 저장 아님)
	var workflowsById = {}; // id -> description, 목록 조회 결과를 드롭다운 change 시 다시 쓰기 위해 기억
	var sampleNoteEl;

	// workflowId를 고를 때마다 message/variables에 채워 넣을 기본 샘플값.
	// 각 Workflow YAML(dstone-ai-engine/src/main/resources/workflows/**) 맨 위 주석의 "Workflow 테스트" 예시와 같은 값이다.
	// 여기 없는 workflowId를 고르면 message/variables를 비워 둔다.
	var WORKFLOW_SAMPLES = {
		"sample-agent-basic-echo": {
			message: "오늘 기분이 어때?"
		},
		"sample-agent-model-override": {
			message: "너는 누구니?",
			note: "agents/sample-model-override-agent.yml의 model 값이 지금 켜진 provider에 맞아야 호출됩니다."
		},
		"sample-agent-rag-augmented": {
			message: "적재된 문서 중에 Spring Batch에 대해 설명해줘.",
			note: "RAG 문서 관리 화면에서 적재한 문서에 실제로 있는 키워드로 바꿔서 보내세요."
		},
		"sample-agent-tool-calling": {
			message: "지금 몇 시야? getCurrentDateTime으로 확인해줘."
		},
		"sample-approval-pause-resume": {
			message: "승인 테스트",
			note: "제출하면 WAITING_APPROVAL로 멈춥니다. 관리자 화면에서 승인/반려하세요."
		},
		"sample-foreach-parallel": {
			message: "foreach 테스트",
			variables: { sqlList: ["SELECT 1 FROM dual", "SELECT name FROM member WHERE id = 1"] },
			note: "message는 쓰지 않습니다. sqlList에 \"SELEC 1 FROM dual\"처럼 틀린 SQL을 하나 넣으면 FAIL로 끝나는 것도 확인할 수 있습니다."
		},
		"sample-loop-retry-until-valid": {
			message: "SELECT * FORM member WHERE id = 1",
			note: "\"FORM\" 오타를 fix Agent가 고친 뒤 validate를 통과하면 SUCCESS입니다."
		},
		"sample-mcp-filesystem-list": {
			message: "/app/dstone/dstone-ai-engine/mcp/server-filesystem/sample",
			note: "지금 환경의 APP_HOME 기준 경로입니다(Windows 로컬이면 D:/AppHome/framework/dstone/... 로 바꾸세요)."
		},
		"sample-router-multiway": {
			message: "지난달 카드 요금이 잘못 청구된 것 같아요.",
			variables: { role: "친절한 상담원" },
			note: "\"로그인이 안 돼요\"(technical), \"회사 위치가 어디인가요\"(other)로 바꾸면 다른 분기로 갑니다."
		},
		"sample-structured-output-chain": {
			message: "이 테이블에서 이름이 김철수인 사람을 찾고 싶어. SELECT * FROM member WHERE name = '김철수' 이렇게 쓰면 되지?"
		},
		"sample-supervisor-verdict-gate": {
			message: "\"안녕하세요\"로 시작하는 인사말을 한 문장 만들어줘.",
			variables: { role: "친절한 상담원" },
			note: "\"인사말 없이 오늘 날씨만 알려줘\"로 바꾸면 judge에서 FAIL로 끝납니다."
		},
		"sample-tool-chain-basic": {
			message: "SELECT 1 FROM dual",
			note: "문법이 깨진 SQL로 바꾸면 validate에서 바로 FAIL로 끝납니다."
		},
		"sample-tool-gated-external": {
			message: "외부 Tool 게이트 테스트",
			note: "message는 쓰지 않습니다."
		},
		"sample-tool-rag-search": {
			message: "Spring Batch",
			note: "적재해 둔 문서에 실제로 들어있는 키워드로 바꿔서 보내세요."
		},
		"testApp-sdlc": {
			message: "회원 목록 화면에 가입일자 검색 조건을 추가해줘",
			note: "승인을 모두 통과하면 Jenkins Job \"testApp\" 빌드가 실제로 기동됩니다. 테스트만 하려면 code-review 단계에서 반려하세요."
		}
	};

	function init(options) {
		urls = options;

		workflowIdEl = document.getElementById("workflow-id");
		workflowIdDescEl = document.getElementById("workflow-id-desc");
		sessionIdEl = document.getElementById("workflow-session-id");
		messageEl = document.getElementById("workflow-message");
		variablesEl = document.getElementById("workflow-variables");
		sampleNoteEl = document.getElementById("workflow-sample-note");

		submitBtn = document.getElementById("workflow-submit-btn");
		submitStatusEl = document.getElementById("workflow-submit-status");

		executionIdEl = document.getElementById("workflow-job-id");
		statusBadgeEl = document.getElementById("workflow-status-badge");
		resultEl = document.getElementById("workflow-result");
		pollStopBtn = document.getElementById("workflow-poll-stop-btn");
		pollRefreshBtn = document.getElementById("workflow-poll-refresh-btn");

		historyBodyEl = document.getElementById("workflow-history-body");

		submitBtn.addEventListener("click", submit);
		pollStopBtn.addEventListener("click", stopPolling);
		pollRefreshBtn.addEventListener("click", function () {
			if (activeExecutionId) {
				fetchStatus(activeExecutionId);
			}
		});
		workflowIdEl.addEventListener("change", function () {
			updateWorkflowDescription();
			fillSample();
		});

		loadWorkflowList();
	}

	function loadWorkflowList() {
		fetch(urls.listUrl)
			.then(function (response) {
				if (!response.ok) {
					throw new Error("서버 오류(" + response.status + ")");
				}
				return response.json();
			})
			.then(function (workflows) {
				workflowIdEl.innerHTML = "";
				workflowsById = {};
				(workflows || []).forEach(function (workflow) {
					workflowsById[workflow.id] = workflow.description;
					var option = document.createElement("option");
					option.value = workflow.id;
					option.textContent = workflow.id;
					workflowIdEl.appendChild(option);
				});
				updateWorkflowDescription();
				fillSample();
			})
			.catch(function (err) {
				workflowIdDescEl.textContent = "workflow 목록을 불러오지 못했습니다: " + err.message;
			});
	}

	function updateWorkflowDescription() {
		var description = workflowsById[workflowIdEl.value];
		workflowIdDescEl.textContent = description || "";
	}

	/** 고른 workflowId의 기본 샘플값으로 message/variables를 채운다(이미 입력해 둔 값은 덮어쓴다). */
	function fillSample() {
		var sample = WORKFLOW_SAMPLES[workflowIdEl.value] || {};
		messageEl.value = sample.message || "";
		variablesEl.value = sample.variables ? JSON.stringify(sample.variables, null, 2) : "";
		sampleNoteEl.textContent = sample.note ? "※ " + sample.note : "";
	}

	function submit() {
		var workflowId = workflowIdEl.value.trim();
		var message = messageEl.value.trim();
		if (!workflowId) {
			alert("workflowId를 선택하세요.");
			return;
		}
		if (!message) {
			alert("message를 입력하세요.");
			return;
		}

		var variables = null;
		var variablesText = variablesEl.value.trim();
		if (variablesText) {
			try {
				variables = JSON.parse(variablesText);
			} catch (e) {
				alert("variables는 올바른 JSON 객체여야 합니다: " + e.message);
				return;
			}
		}

		setSubmitLoading(true);

		fetch(urls.submitUrl, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({
				workflowId: workflowId,
				message: message,
				sessionId: sessionIdEl.value.trim() || null,
				variables: variables
			})
		})
			.then(function (response) {
				if (!response.ok) {
					throw new Error("서버 오류(" + response.status + ")");
				}
				return response.json();
			})
			.then(function (result) {
				if (result.error) {
					submitStatusEl.textContent = "제출 실패: " + result.error;
					return;
				}
				submitStatusEl.textContent = "제출 완료";
				startTracking(workflowId, result.executionId);
			})
			.catch(function (err) {
				submitStatusEl.textContent = "요청 실패: " + err.message;
			})
			.finally(function () {
				setSubmitLoading(false);
			});
	}

	function setSubmitLoading(loading) {
		submitBtn.disabled = loading;
		submitStatusEl.textContent = loading ? "제출 중입니다..." : submitStatusEl.textContent;
	}

	function startTracking(workflowId, executionId) {
		stopPolling();

		activeExecutionId = executionId;
		executionIdEl.textContent = executionId;
		setBadge("RUNNING");
		resultEl.value = "";
		pollStopBtn.disabled = false;
		pollRefreshBtn.disabled = false;

		addHistoryRow(workflowId, executionId);

		fetchStatus(executionId);
		pollTimer = setInterval(function () {
			fetchStatus(executionId);
		}, POLL_INTERVAL_MS);
	}

	function fetchStatus(executionId) {
		fetch(urls.statusUrl, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ executionId: executionId })
		})
			.then(function (response) { return response.json(); })
			.then(function (result) {
				if (executionId !== activeExecutionId) {
					// 그 사이 다른 executionId를 선택했으면(이력 클릭) 이 응답은 화면에 반영하지 않는다.
					updateHistoryRow(executionId, result.status);
					return;
				}
				setBadge(result.status);
				resultEl.value = result.status === "FAILED" || result.status === "ERROR"
					? (result.error || "")
					: (result.result || "");
				updateHistoryRow(executionId, result.status);

				if (result.status === "DONE" || result.status === "FAILED" || result.status === "ERROR") {
					stopPolling();
				}
			})
			.catch(function (err) {
				if (executionId === activeExecutionId) {
					setBadge("ERROR");
					resultEl.value = "상태 조회 실패: " + err.message;
					stopPolling();
				}
			});
	}

	function stopPolling() {
		if (pollTimer) {
			clearInterval(pollTimer);
			pollTimer = null;
		}
		pollStopBtn.disabled = true;
	}

	function setBadge(status) {
		statusBadgeEl.textContent = status || "-";
		statusBadgeEl.className = "workflow-badge workflow-badge-" + (status || "unknown").toLowerCase();
	}

	function addHistoryRow(workflowId, executionId) {
		var tr = document.createElement("tr");
		tr.innerHTML =
			"<td>" + escapeHtml(new Date().toLocaleTimeString()) + "</td>" +
			"<td>" + escapeHtml(workflowId) + "</td>" +
			"<td><a href=\"javascript:void(0)\" class=\"workflow-history-jobid\">" + escapeHtml(executionId) + "</a></td>" +
			"<td class=\"workflow-history-status\">RUNNING</td>";
		tr.querySelector(".workflow-history-jobid").addEventListener("click", function () {
			trackExisting(executionId);
		});
		historyBodyEl.insertBefore(tr, historyBodyEl.firstChild);
		historyRows[executionId] = tr;
	}

	function updateHistoryRow(executionId, status) {
		var tr = historyRows[executionId];
		if (tr) {
			tr.querySelector(".workflow-history-status").textContent = status || "-";
		}
	}

	/** 이력 목록에서 이전에 제출한 executionId를 다시 클릭했을 때, 그 작업의 폴링을 다시 시작한다. */
	function trackExisting(executionId) {
		stopPolling();
		activeExecutionId = executionId;
		executionIdEl.textContent = executionId;
		resultEl.value = "";
		pollStopBtn.disabled = false;
		pollRefreshBtn.disabled = false;

		fetchStatus(executionId);
		pollTimer = setInterval(function () {
			fetchStatus(executionId);
		}, POLL_INTERVAL_MS);
	}

	function escapeHtml(value) {
		var div = document.createElement("div");
		div.textContent = value == null ? "" : value;
		return div.innerHTML;
	}

	return { init: init };
})();
