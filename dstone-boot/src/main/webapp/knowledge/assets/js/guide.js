/**
 * "사용 안내" 화면: 리비전 상세(탭)의 모든 항목 설명을 사전(knowledge-terms.js)에서 꺼내 그립니다.
 * 설명을 화면에 직접 적지 않고 사전에서 그리는 이유: 리비전 상세가 보여 주는 설명과 글자 하나까지 같아야 하기 때문입니다.
 */
var DstoneKnowledgeGuide = (function () {

	var K = DstoneKnowledge;
	var T = DstoneKnowledgeTerms;

	function init() {
		renderCards(document.getElementById("kn-guide-cards"));
		renderUnused(document.getElementById("kn-guide-unused"));
		renderSections(document.getElementById("kn-guide-sections"));
	}

	function renderCards(el) {
		K.table(el, [
			{ key: "name", label: "카드" },
			{ key: "meaning", label: "무엇을 센 것인가" }
		], T.CARDS.map(function (card) {
			return { name: card[0], meaning: card[1] };
		}));
	}

	/** 미사용 탭: 종류마다 무엇을 미사용으로 보는지, 등급마다 무슨 뜻인지 */
	function renderUnused(el) {
		var html = rowsTable("종류", mapOf(T.UNUSED_KIND));
		html += rowsTable("등급", mapOf(T.UNUSED_GRADE));
		el.innerHTML = html;
	}

	/** {코드: [이름, 뜻]} → {"코드 (이름)": 뜻} */
	function mapOf(dictionary) {
		var result = {};
		Object.keys(dictionary).forEach(function (key) {
			result[key + " (" + dictionary[key][0] + ")"] = dictionary[key][1];
		});
		return result;
	}

	/** 탭 순서대로, 탭 안의 표마다 열의 뜻과 값의 뜻, 읽는 법 */
	function renderSections(el) {
		var tabs = [["connect", "연결 품질 탭"], ["content", "분석 내용 탭"], ["history", "실행 기록 탭"]];
		var html = "";
		var number = 0;
		tabs.forEach(function (tab) {
			html += "<h4>" + K.escapeHtml(tab[1]) + "</h4>";
			T.SECTIONS.filter(function (section) { return section.tab === tab[0]; }).forEach(function (section) {
				number++;
				html += "<h5>" + number + ". " + K.escapeHtml(section.title) + "</h5><p>" + K.escapeHtml(section.what) + "</p>";
				html += "<table class=\"document-list-table\"><thead><tr><th style=\"width:150px;\">열</th><th style=\"width:150px;\">화면의 이름</th><th>뜻</th></tr></thead><tbody>";
				section.columns.forEach(function (column) {
					html += "<tr><td><code>" + K.escapeHtml(column[0]) + "</code></td><td>" + K.escapeHtml(column[1]) + "</td><td>" + K.escapeHtml(column[2])
						+ (column[3] ? (column[2] ? "<br>" : "") + "<span class=\"ai-hint\">나올 수 있는 값은 아래 표</span>" : "") + "</td></tr>";
				});
				html += "</tbody></table>";
				section.columns.forEach(function (column) {
					if (column[3]) {
						html += rowsTable(column[1] + "의 값", dictionaryOf(column[3]));
					}
				});
				if (section.read) {
					html += "<div class=\"kn-note\"><b>읽는 법</b> — " + K.escapeHtml(section.read) + "</div>";
				}
			});
		});
		el.innerHTML = html;
	}

	/** 값의 뜻 사전이 함수면(자원의 종류) 대표 값들로 표를 만든다 */
	function dictionaryOf(dictionary) {
		if (typeof dictionary !== "function") {
			return dictionary;
		}
		var result = {};
		(dictionary.samples || []).forEach(function (code) {
			result[code] = dictionary(code);
		});
		return result;
	}

	function rowsTable(label, rows) {
		var html = "<table class=\"document-list-table\"><thead><tr><th style=\"width:300px;\">" + K.escapeHtml(label) + "</th><th>뜻</th></tr></thead><tbody>";
		Object.keys(rows).forEach(function (key) {
			html += "<tr><td><code>" + K.escapeHtml(key === "" ? "(빈 값)" : key) + "</code></td><td>" + K.escapeHtml(rows[key]) + "</td></tr>";
		});
		return html + "</tbody></table>";
	}

	return { init: init };
})();
