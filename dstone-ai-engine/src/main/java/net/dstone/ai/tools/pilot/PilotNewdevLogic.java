package net.dstone.ai.tools.pilot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.dstone.ai.common.schema.JsonSchemaUtil;

/**
 * <pre>
 * pilot-workflow의 신규 개발 경로에서 "LLM 없이 코드로 정해지는 일"을 모아 둔 곳입니다.
 * - 검증: Agent가 낸 데이터가 앞뒤가 맞는지 확인합니다(checkAnalysis, checkDesign).
 * - 문서 만들기: Agent가 낸 데이터를 정해진 틀에 끼워 문서(md) 글자를 만듭니다(requirementsDoc, impactDoc, designDoc, tasksDoc).
 * - 할 일 나누기: 설계를 보고 복사/새로 쓰기/고치기/덧붙이기 목록을 만듭니다(planChanges 등).
 *
 * 예전에는 이 일들을 Workflow YAML 안의 jq 표현식으로 했습니다. 표현식은 테스트할 수 없고 읽기 어려워서 자바로 옮겼습니다.
 * YAML은 이제 "어떤 값을 어디로 넘기는지"만 적습니다.
 *
 * 이 클래스는 파일을 읽거나 쓰지 않습니다. 값만 받아서 값만 돌려줍니다. 그래서 단위 테스트로 그대로 확인할 수 있습니다.
 * 파일을 읽고 쓰는 일은 PilotNewdevTool이 합니다.
 *
 * 받는 값은 Agent가 JSON으로 낸 것을 그대로 읽은 맵과 리스트입니다(모양은 definitions/schemas/pilot-newdev-*.json 참고).
 * 값이 빠져 있어도(null) 멈추지 않고 빈 값으로 봅니다. 빠진 것을 찾아 알리는 것이 검증의 일이기 때문입니다.
 * </pre>
 */
public final class PilotNewdevLogic {

	/** 검증에 걸린 것이 없을 때 검증 결과로 쓰는 글자입니다. */
	public static final String NO_PROBLEM = "문제 없음";

	/** 파일 Tool이 실패했을 때 답의 맨 앞에 붙이는 글자입니다(tools.utils.FileUtil). */
	private static final String FAIL = "실패";

	private static final String TYPE_NEW = "신규";
	private static final String TYPE_EDIT = "수정";

	private PilotNewdevLogic() {
	}

	// =====================================================================================
	// 참고 구현 파일
	// =====================================================================================

	/**
	 * 읽어 둔 파일들을 Agent에게 넘길 글자 하나로 잇습니다. 파일마다 "===== 파일: 경로 =====" 줄 아래에 내용을 둡니다.
	 *
	 * @param sources 경로 → 내용
	 */
	public static String sourcesText(Map<String, String> sources) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, String> entry : sources.entrySet()) {
			parts.add("===== 파일: " + entry.getKey() + " =====\n" + entry.getValue());
		}
		return String.join("\n\n", parts);
	}

	// =====================================================================================
	// 검증
	// =====================================================================================

	/**
	 * <pre>
	 * 요구사항/구성 정의(pilot-newdev-analyzer-agent의 답)를 검증해서, 걸린 것을 문장 목록으로 돌려줍니다. 없으면 빈 목록입니다.
	 * 1) 참고 구현 파일로 적힌 경로에 파일이 있는가
	 * 2) 변경 대상이 하나라도 있는가
	 * 3) 요청 범위의 항목마다 FR이 있는가
	 * 4) FR마다 변경 대상이 있는가
	 * 5) 변경 대상이 가리키는 FR이 요구사항에 있는가
	 * 6) 같은 파일이 변경 대상에 두 번 들어 있지 않은가(파일별 설계와 구현이 동시에 돌기 때문에 겹치면 안 됩니다)
	 * 7) 신규라고 한 파일이 이미 있지 않은가, 수정이라고 한 파일이 실제로 있는가
	 * 8) 수정 대상의 내용을 읽어 두었는가
	 * 9) 본뜰 참고 파일의 내용을 읽어 두었는가
	 * </pre>
	 *
	 * @param analysis        요구사항/구성 정의
	 * @param referenceFiles  참고 구현의 파일 목록({path, ...})
	 * @param referenceExists 그 파일들이 지금 있는지(같은 순서)
	 * @param targetExists    변경 대상 파일들이 지금 있는지(analysis.targets와 같은 순서)
	 * @param readPaths       내용을 읽어 둔 파일의 경로들
	 */
	public static List<String> checkAnalysis(Map<String, Object> analysis, List<Object> referenceFiles, List<Boolean> referenceExists, List<Boolean> targetExists, List<String> readPaths) {
		Map<String, Object> o = map(analysis);
		List<Object> targets = list(o.get("targets"));
		List<String> frIds = new ArrayList<>();
		for (Object requirement : list(o.get("requirements"))) {
			frIds.add(text(map(requirement).get("id")));
		}
		List<String> problems = new ArrayList<>();

		List<Object> files = list(referenceFiles);
		for (int i = 0; i < files.size(); i++) {
			if (!isTrueAt(referenceExists, i)) {
				problems.add("참고 구현 파일로 적힌 경로에 파일이 없습니다: " + text(map(files.get(i)).get("path")));
			}
		}
		if (targets.isEmpty()) {
			problems.add("변경 대상이 하나도 없습니다.");
		}
		for (Object scope : list(o.get("scope"))) {
			if (list(map(scope).get("frs")).isEmpty()) {
				problems.add("요청 범위에 FR이 없는 항목이 있습니다: " + text(map(scope).get("item")));
			}
		}
		for (String frId : frIds) {
			boolean covered = false;
			for (Object target : targets) {
				if (texts(map(target).get("frs")).contains(frId)) {
					covered = true;
				}
			}
			if (!covered) {
				problems.add("FR에 대응하는 변경 대상이 없습니다: " + frId);
			}
		}
		for (Object target : targets) {
			for (String frId : texts(map(target).get("frs"))) {
				if (!frIds.contains(frId)) {
					problems.add("변경 대상이 요구사항에 없는 FR을 가리킵니다: " + text(map(target).get("path")) + " → " + frId);
				}
			}
		}
		Map<String, Integer> countByPath = new TreeMap<>();
		for (Object target : targets) {
			String path = text(map(target).get("path"));
			countByPath.put(path, countByPath.containsKey(path) ? countByPath.get(path).intValue() + 1 : 1);
		}
		for (Map.Entry<String, Integer> entry : countByPath.entrySet()) {
			if (entry.getValue().intValue() > 1) {
				problems.add("같은 파일이 변경 대상에 두 번 들어 있습니다: " + entry.getKey());
			}
		}
		for (int i = 0; i < targets.size(); i++) {
			Map<String, Object> target = map(targets.get(i));
			boolean exists = isTrueAt(targetExists, i);
			if (TYPE_NEW.equals(target.get("changeType")) && exists) {
				problems.add("신규라고 했는데 이미 있는 파일입니다: " + text(target.get("path")));
			} else if (TYPE_EDIT.equals(target.get("changeType")) && !exists) {
				problems.add("수정이라고 했는데 없는 파일입니다: " + text(target.get("path")));
			}
		}
		List<String> read = readPaths == null ? List.of() : readPaths;
		for (Object item : targets) {
			Map<String, Object> target = map(item);
			if (TYPE_EDIT.equals(target.get("changeType")) && !read.contains(text(target.get("path")))) {
				problems.add("수정 대상인데 내용을 읽어 두지 않은 파일입니다(참고 구현의 파일 목록에 없습니다): " + text(target.get("path")));
			}
		}
		for (Object item : targets) {
			String referencePath = or(map(item).get("referencePath"), "");
			if (!referencePath.isEmpty() && !read.contains(referencePath)) {
				problems.add("본뜰 참고 파일로 적었는데 읽어 둔 파일 목록에 없습니다: " + referencePath);
			}
		}
		return problems;
	}

	/**
	 * <pre>
	 * 파일별 설계(pilot-newdev-file-designer-agent의 답들)를 검증해서, 걸린 것을 문장 목록으로 돌려줍니다. 없으면 빈 목록입니다.
	 * 1) 설계를 만들지 못한 파일이 있는가(designError)
	 * 2) 변경 대상마다 설계가 있는가
	 * 3) 신규 파일에 본뜰 참고 파일이나 넣을 내용이 있는가
	 * 4) 수정 파일에 바꿀 곳이 있는가
	 * 5) 수정 파일의 '변경 전'이 지금 파일에 정확히 한 번 나오는가
	 *    (줄바꿈 방식(CRLF/LF)이 달라서 못 찾는 일이 없게 양쪽에서 CR을 떼고 견줍니다)
	 * </pre>
	 *
	 * @param analysis    요구사항/구성 정의(변경 대상 목록을 봅니다)
	 * @param designs     파일별 설계 목록(설계에 실패한 파일의 자리는 null)
	 * @param designError 설계 step의 실패 사유(없으면 null)
	 * @param sources     읽어 둔 파일의 경로 → 내용
	 */
	public static List<String> checkDesign(Map<String, Object> analysis, List<Object> designs, String designError, Map<String, String> sources) {
		List<Object> targets = list(map(analysis).get("targets"));
		List<Map<String, Object>> ds = nonNull(designs);
		List<String> problems = new ArrayList<>();

		if (designError != null) {
			problems.add("설계를 만들지 못한 파일이 있습니다: " + designError);
		}
		for (Object target : targets) {
			Object path = map(target).get("path");
			boolean designed = false;
			for (Map<String, Object> design : ds) {
				if (path != null && path.equals(design.get("path"))) {
					designed = true;
				}
			}
			if (!designed) {
				problems.add("설계가 없는 변경 대상입니다: " + text(path));
			}
		}
		for (Map<String, Object> design : ds) {
			if (TYPE_NEW.equals(design.get("changeType")) && or(design.get("referencePath"), "").isEmpty() && list(design.get("adds")).isEmpty()) {
				problems.add("신규 파일인데 본뜰 참고 파일도 넣을 내용도 없습니다: " + text(design.get("path")));
			}
		}
		for (Map<String, Object> design : ds) {
			if (TYPE_EDIT.equals(design.get("changeType")) && list(design.get("edits")).isEmpty()) {
				problems.add("수정 파일인데 바꿀 곳이 없습니다: " + text(design.get("path")));
			}
		}
		for (Map<String, Object> design : ds) {
			if (!TYPE_EDIT.equals(design.get("changeType"))) {
				continue;
			}
			for (Object item : list(design.get("edits"))) {
				Map<String, Object> edit = map(item);
				if (Boolean.TRUE.equals(edit.get("append"))) {
					continue;
				}
				String before = or(edit.get("before"), "").replace("\r", "");
				String where = text(design.get("path")) + " / " + or(edit.get("location"), "");
				if (before.isEmpty()) {
					problems.add("파일 끝에 덧붙이는 변경이 아닌데 변경 전이 비어 있습니다: " + where);
					continue;
				}
				String current = sources == null ? null : sources.get(text(design.get("path")));
				int found = count(current == null ? "" : current.replace("\r", ""), before);
				if (found != 1) {
					problems.add("변경 전이 지금 파일에 " + found + "번 나옵니다(정확히 한 번이어야 합니다): " + where);
				}
			}
		}
		return problems;
	}

	/**
	 * 검증에 걸린 것들을 문서에 넣을 글자로 만듭니다. 없으면 "문제 없음" 한 줄입니다.
	 *
	 * @param problems 검증에 걸린 문장 목록
	 */
	public static String checkText(List<String> problems) {
		if (problems == null || problems.isEmpty()) {
			return NO_PROBLEM;
		}
		List<String> lines = new ArrayList<>();
		for (String problem : problems) {
			lines.add("- " + problem);
		}
		return String.join("\n", lines);
	}

	// =====================================================================================
	// 문서 만들기
	// =====================================================================================

	/**
	 * 요구사항 정의서(01-requirements.md)의 내용을 만듭니다.
	 *
	 * @param analysis  요구사항/구성 정의
	 * @param reference 참고 구현(pilot-newdev-reference-agent의 답)
	 */
	public static String requirementsDoc(Map<String, Object> analysis, Map<String, Object> reference) {
		Map<String, Object> o = map(analysis);
		Map<String, Object> r = map(reference);
		StringBuilder doc = new StringBuilder();
		doc.append("# 요구사항 정의서\n\n");
		doc.append("> Agent가 낸 데이터를 Workflow가 틀에 끼워 만든 문서입니다. 이 파일을 고쳐도 다음 단계에 반영되지 않습니다. 고칠 것은 승인 단계에서 '재분석'을 고르고 의견에 적으십시오.\n\n");
		doc.append("## 배경/목적\n").append(or(o.get("background"), "")).append("\n\n");
		doc.append("## 요청 범위\n| 번호 | 요청서의 메뉴/기능 | 유형 | 다루는 FR |\n|---|---|---|---|\n");
		List<String> scopeRows = new ArrayList<>();
		List<Object> scopes = list(o.get("scope"));
		for (int i = 0; i < scopes.size(); i++) {
			Map<String, Object> scope = map(scopes.get(i));
			scopeRows.add("| " + (i + 1) + " | " + cell(scope.get("item")) + " | 신규 개발 | " + join(scope.get("frs"), ", ") + " |");
		}
		doc.append(String.join("\n", scopeRows)).append("\n\n");
		doc.append("## 확인한 사실\n");
		doc.append("- 같은 기능이 이미 있는지: ").append(or(r.get("existing"), "미확인")).append("\n");
		doc.append("- 참고 구현: ").append(or(r.get("referenceName"), "")).append("\n");
		doc.append("- 참고 구현의 흐름: ").append(or(r.get("entry"), "")).append("\n\n");
		doc.append("## 기능 요구사항\n");
		List<String> requirements = new ArrayList<>();
		for (Object item : list(o.get("requirements"))) {
			Map<String, Object> requirement = map(item);
			requirements.add("### " + text(requirement.get("id")) + " " + or(requirement.get("title"), "")
				+ "\n- Given: " + or(requirement.get("given"), "-")
				+ "\n- When: " + or(requirement.get("when"), "-")
				+ "\n- Then: " + or(requirement.get("then"), "-"));
		}
		doc.append(String.join("\n\n", requirements)).append("\n\n");
		doc.append("## 비기능 요구사항\n").append(bullets(o.get("nonFunctional"))).append("\n\n");
		doc.append("## 범위 제외 항목\n").append(bullets(o.get("outOfScope"))).append("\n\n");
		List<Object> questions = new ArrayList<>(list(r.get("questions")));
		questions.addAll(list(o.get("questions")));
		doc.append("## 확인 필요 질문\n").append(bullets(questions)).append("\n");
		return doc.toString();
	}

	/**
	 * 영향도 분석서(02-impact.md)의 내용을 만듭니다. 맨 위에 자동 검증 결과가 들어갑니다.
	 *
	 * @param analysis  요구사항/구성 정의
	 * @param reference 참고 구현
	 * @param checkText 자동 검증 결과(checkText()). 검증하지 않았으면 null
	 */
	public static String impactDoc(Map<String, Object> analysis, Map<String, Object> reference, String checkText) {
		Map<String, Object> o = map(analysis);
		Map<String, Object> r = map(reference);
		List<Object> targets = list(o.get("targets"));
		StringBuilder doc = new StringBuilder();
		doc.append("# 영향도 분석서 (신규 개발)\n\n");
		doc.append("> Agent가 낸 데이터를 Workflow가 틀에 끼워 만든 문서입니다. 이 파일을 고쳐도 다음 단계에 반영되지 않습니다. 고칠 것은 승인 단계에서 '재분석'을 고르고 의견에 적으십시오.\n\n");
		doc.append("## 자동 검증 결과\n").append(checkText == null ? "검증하지 않음" : checkText).append("\n\n");
		doc.append("## FR별 대응\n| FR | 요구사항 | 변경 대상 파일 |\n|---|---|---|\n");
		List<String> frRows = new ArrayList<>();
		for (Object item : list(o.get("requirements"))) {
			Map<String, Object> requirement = map(item);
			String frId = text(requirement.get("id"));
			List<String> paths = new ArrayList<>();
			for (Object target : targets) {
				if (texts(map(target).get("frs")).contains(frId)) {
					paths.add(cell(map(target).get("path")));
				}
			}
			frRows.add("| " + cell(requirement.get("id")) + " | " + cell(or(requirement.get("title"), "")) + " | " + String.join("<br>", paths) + " |");
		}
		doc.append(String.join("\n", frRows)).append("\n\n");
		doc.append("## 참고 구현: ").append(or(r.get("referenceName"), "")).append("\n");
		doc.append("| 층 | 참고 구현의 파일 | 클래스.메서드 / 쿼리ID / 테이블 | 새 기능에 | 이유 |\n|---|---|---|---|---|\n");
		List<String> layerRows = new ArrayList<>();
		for (Object item : list(o.get("layers"))) {
			Map<String, Object> layer = map(item);
			layerRows.add("| " + cellOr(layer.get("layer")) + " | " + cellOr(layer.get("referencePath")) + " | " + cellOr(layer.get("symbol"))
				+ " | " + cellOr(layer.get("decision")) + " | " + cellOr(layer.get("reason")) + " |");
		}
		doc.append(String.join("\n", layerRows)).append("\n\n");
		doc.append("## 변경 대상\n| 파일 | 유형 | 본뜰 참고 파일 | 변경 내용 | FR |\n|---|---|---|---|---|\n");
		List<String> targetRows = new ArrayList<>();
		for (Object item : targets) {
			Map<String, Object> target = map(item);
			targetRows.add("| " + cellOr(target.get("path")) + " | " + cellOr(target.get("changeType")) + " | " + cellOr(target.get("referencePath"))
				+ " | " + cellOr(target.get("change")) + " | " + join(target.get("frs"), ", ") + " |");
		}
		doc.append(String.join("\n", targetRows)).append("\n\n");
		doc.append("## 함께 쓰는 이름\n| 무엇 | 새 이름 | 참고 구현의 이름 |\n|---|---|---|\n");
		List<String> nameRows = new ArrayList<>();
		for (Object item : list(o.get("sharedNames"))) {
			Map<String, Object> name = map(item);
			nameRows.add("| " + cellOr(name.get("what")) + " | " + cellOr(name.get("name")) + " | " + cellOr(name.get("from")) + " |");
		}
		doc.append(String.join("\n", nameRows)).append("\n\n");
		doc.append("## 영향 받는 기존 기능\n").append(bullets(o.get("impacts"))).append("\n\n");
		doc.append("## 위험 요소와 회귀 테스트 대상\n").append(bullets(o.get("risks"))).append("\n");
		return doc.toString();
	}

	/**
	 * 설계서(04-design.md)의 내용을 만듭니다. 파일별 설계를 모아 틀에 끼웁니다.
	 *
	 * @param designs   파일별 설계 목록(설계에 실패한 파일의 자리는 null)
	 * @param checkText 자동 검증 결과. 검증하지 않았으면 null
	 */
	public static String designDoc(List<Object> designs, String checkText) {
		List<Map<String, Object>> ds = nonNull(designs);
		StringBuilder doc = new StringBuilder();
		doc.append("# 설계서 (신규 개발)\n\n");
		doc.append("> 파일별 설계 데이터를 Workflow가 틀에 끼워 만든 문서입니다. 이 파일을 고쳐도 구현에 반영되지 않습니다. 고칠 것은 승인 단계에서 '재설계'를 고르고 의견에 적으십시오.\n\n");
		doc.append("## 자동 검증 결과\n").append(checkText == null ? "검증하지 않음" : checkText).append("\n\n");
		doc.append("## 결정이 필요한 사항\n");
		List<String> questions = new ArrayList<>();
		for (Map<String, Object> design : ds) {
			for (Object question : list(design.get("questions"))) {
				questions.add("- " + text(question) + " (" + text(design.get("path")) + ")");
			}
		}
		doc.append(questions.isEmpty() ? "없음" : String.join("\n", questions)).append("\n\n");

		List<String> sections = new ArrayList<>();
		for (int i = 0; i < ds.size(); i++) {
			Map<String, Object> design = ds.get(i);
			StringBuilder section = new StringBuilder();
			section.append("## ").append(i + 1).append(". ").append(text(design.get("path"))).append("\n");
			section.append("- 변경 유형: ").append(or(design.get("changeType"), "")).append("\n");
			String referencePath = or(design.get("referencePath"), "");
			section.append("- 본뜰 참고 파일: ").append(referencePath.isEmpty() ? "없음" : referencePath).append("\n");
			section.append("- 해결하는 FR: ").append(join(design.get("frs"), ", ")).append("\n");
			section.append("- 하는 일: ").append(or(design.get("summary"), "")).append("\n");
			section.append("- 확인 방법: ").append(or(design.get("verify"), "")).append("\n");
			List<Object> renames = list(design.get("renames"));
			if (!renames.isEmpty()) {
				section.append("\n### 이름 바꿈 (참고 → 신규)\n| 무엇 | 참고 | 신규 |\n|---|---|---|\n");
				List<String> rows = new ArrayList<>();
				for (Object item : renames) {
					Map<String, Object> rename = map(item);
					rows.add("| " + cellOr(rename.get("what")) + " | " + cellOr(rename.get("from")) + " | " + cellOr(rename.get("to")) + " |");
				}
				section.append(String.join("\n", rows)).append("\n");
			}
			List<Object> removes = list(design.get("removes"));
			if (!removes.isEmpty()) {
				section.append("\n### 빼는 것\n");
				List<String> rows = new ArrayList<>();
				for (Object remove : removes) {
					rows.add("- " + text(remove));
				}
				section.append(String.join("\n", rows)).append("\n");
			}
			for (Object item : list(design.get("adds"))) {
				Map<String, Object> add = map(item);
				section.append("\n### 더하는 것: ").append(or(add.get("where"), "")).append("\n").append(code(add.get("content")));
			}
			for (Object item : list(design.get("edits"))) {
				Map<String, Object> edit = map(item);
				boolean append = Boolean.TRUE.equals(edit.get("append"));
				section.append("\n### 바꿀 곳: ").append(or(edit.get("location"), "")).append(append ? " (파일 끝에 덧붙임)" : "").append("\n");
				String reason = or(edit.get("reason"), "");
				if (!reason.isEmpty()) {
					section.append("이유: ").append(reason).append("\n");
				}
				if (!append) {
					section.append("\n변경 전\n").append(code(edit.get("before")));
				}
				section.append("\n변경 후\n").append(code(edit.get("after")));
			}
			sections.add(section.toString());
		}
		doc.append(String.join("\n", sections));
		return doc.toString();
	}

	// =====================================================================================
	// 할 일 나누기
	// =====================================================================================

	/**
	 * <pre>
	 * 파일별 설계 Agent에게 넣을 값을 변경 대상 파일마다 하나씩 만듭니다(이 목록으로 forEach를 돕니다).
	 * 본뜰 참고 파일의 내용(referenceSource)과 고칠 파일의 지금 내용(currentSource)은 읽어 둔 파일에서 경로로 찾아 넣습니다.
	 * 읽어 두지 않은 파일이면 빈 글자입니다.
	 * </pre>
	 *
	 * @param request  요청서 내용
	 * @param analysis 요구사항/구성 정의
	 * @param sources  읽어 둔 파일의 경로 → 내용
	 * @param feedback 다시 설계할 때 반영할 지적 사항(처음이면 빈 글자)
	 */
	public static List<Map<String, Object>> designInputs(String request, Map<String, Object> analysis, Map<String, String> sources, String feedback) {
		Map<String, Object> o = map(analysis);
		Map<String, Object> plan = new LinkedHashMap<>();
		if (o.containsKey("targets")) {
			plan.put("targets", o.get("targets"));
		}
		if (o.containsKey("sharedNames")) {
			plan.put("sharedNames", o.get("sharedNames"));
		}
		List<Map<String, Object>> inputs = new ArrayList<>();
		for (Object item : list(o.get("targets"))) {
			Map<String, Object> target = map(item);
			Map<String, Object> input = new LinkedHashMap<>();
			input.put("request", request);
			input.put("requirements", o.get("requirements"));
			input.put("target", item);
			input.put("plan", plan);
			input.put("referenceSource", sourceOf(sources, or(target.get("referencePath"), "")));
			input.put("currentSource", sourceOf(sources, or(target.get("path"), "")));
			input.put("feedback", feedback == null ? "" : feedback);
			inputs.add(input);
		}
		return inputs;
	}

	/**
	 * <pre>
	 * 설계를 보고, LLM 없이 그대로 적용할 수 있는 변경을 네 가지 목록으로 나눕니다. 목록의 항목은 각 파일 Tool의 인자 모양 그대로입니다.
	 *   copies   신규 파일(본뜰 참고 파일 있음): 참고 파일을 복사하면서 이름 바꿈 표대로 글자를 바꿉니다  → copyFileWithReplace
	 *   creates  신규 파일(본뜰 참고 파일 없음): 설계가 적어 준 내용을 그대로 저장합니다              → writeFile
	 *   edits    수정 파일: '변경 전'을 '변경 후'로 바꿉니다                                     → replaceInFile
	 *   appends  수정 파일: 파일 끝에 덧붙입니다(내용이 줄바꿈으로 시작하지 않으면 앞에 줄바꿈을 넣습니다)  → appendFile
	 * </pre>
	 *
	 * @param designs 파일별 설계 목록(설계에 실패한 파일의 자리는 null)
	 */
	public static Map<String, Object> planChanges(List<Object> designs) {
		List<Map<String, Object>> copies = new ArrayList<>();
		List<Map<String, Object>> creates = new ArrayList<>();
		List<Map<String, Object>> edits = new ArrayList<>();
		List<Map<String, Object>> appends = new ArrayList<>();
		for (Map<String, Object> design : nonNull(designs)) {
			String referencePath = or(design.get("referencePath"), "");
			if (TYPE_NEW.equals(design.get("changeType")) && !referencePath.isEmpty()) {
				Map<String, Object> copy = new LinkedHashMap<>();
				copy.put("sourcePath", design.get("referencePath"));
				copy.put("targetPath", design.get("path"));
				copy.put("replacements", list(design.get("renames")));
				copies.add(copy);
			} else if (TYPE_NEW.equals(design.get("changeType"))) {
				List<String> contents = new ArrayList<>();
				for (Object add : list(design.get("adds"))) {
					contents.add(or(map(add).get("content"), ""));
				}
				Map<String, Object> create = new LinkedHashMap<>();
				create.put("filePath", design.get("path"));
				create.put("fileContents", String.join("\n", contents));
				creates.add(create);
			} else if (TYPE_EDIT.equals(design.get("changeType"))) {
				for (Object item : list(design.get("edits"))) {
					Map<String, Object> edit = map(item);
					if (Boolean.TRUE.equals(edit.get("append"))) {
						String after = or(edit.get("after"), "");
						Map<String, Object> append = new LinkedHashMap<>();
						append.put("filePath", design.get("path"));
						append.put("fileContents", after.startsWith("\n") || after.startsWith("\r") ? after : "\n" + after);
						appends.add(append);
					} else {
						Map<String, Object> replace = new LinkedHashMap<>();
						replace.put("filePath", design.get("path"));
						replace.put("oldText", or(edit.get("before"), ""));
						replace.put("newText", or(edit.get("after"), ""));
						edits.add(replace);
					}
				}
			}
		}
		Map<String, Object> plan = new LinkedHashMap<>();
		plan.put("copies", copies);
		plan.put("creates", creates);
		plan.put("edits", edits);
		plan.put("appends", appends);
		return plan;
	}

	/**
	 * <pre>
	 * 복사해 만든 새 파일 가운데, 빼거나 더할 것이 있어서 편집 Agent에게 넘겨야 하는 파일의 설계를 고릅니다.
	 * 복사에 실패한 파일(대상이 이미 있었던 파일 등)은 고르지 않습니다. 그런 파일은 더 건드리지 않습니다.
	 * </pre>
	 *
	 * @param designs     파일별 설계 목록
	 * @param copies      planChanges()의 copies
	 * @param copyResults copies를 하나씩 실행한 결과(같은 순서)
	 */
	public static List<Map<String, Object>> designsToDevelop(List<Object> designs, List<Object> copies, List<Object> copyResults) {
		List<Object> copyList = list(copies);
		List<Object> resultList = list(copyResults);
		List<String> copied = new ArrayList<>();
		for (int i = 0; i < copyList.size(); i++) {
			String result = i < resultList.size() && resultList.get(i) != null ? text(resultList.get(i)) : "";
			if (result.startsWith("복사했습니다")) {
				copied.add(text(map(copyList.get(i)).get("targetPath")));
			}
		}
		List<Map<String, Object>> selected = new ArrayList<>();
		for (Map<String, Object> design : nonNull(designs)) {
			if (TYPE_NEW.equals(design.get("changeType")) && needsEdit(design) && copied.contains(text(design.get("path")))) {
				selected.add(design);
			}
		}
		return selected;
	}

	/**
	 * <pre>
	 * 편집 Agent가 정한 줄 번호 변경을 파일마다 적용할 목록으로 만듭니다(editFileLines의 인자 모양).
	 * 편집 Agent 호출이 실패했거나 변경 목록이 비어 있는 파일은 넣지 않습니다.
	 * </pre>
	 *
	 * @param developInputs  편집 Agent에게 넣은 값 목록({design, source})
	 * @param developResults 편집 Agent의 답 목록(같은 순서. 실패한 자리는 null)
	 */
	public static List<Map<String, Object>> applyInputs(List<Object> developInputs, List<Object> developResults) {
		List<Object> inputs = list(developInputs);
		List<Object> results = list(developResults);
		List<Map<String, Object>> applies = new ArrayList<>();
		for (int i = 0; i < inputs.size() && i < results.size(); i++) {
			if (results.get(i) == null || list(map(results.get(i)).get("edits")).isEmpty()) {
				continue;
			}
			Map<String, Object> apply = new LinkedHashMap<>();
			apply.put("filePath", map(map(inputs.get(i)).get("design")).get("path"));
			apply.put("edits", map(results.get(i)).get("edits"));
			applies.add(apply);
		}
		return applies;
	}

	/**
	 * <pre>
	 * 태스크 목록(04-tasks.md)의 내용을 만듭니다. 설계 한 건 = 파일 하나 = 태스크 하나입니다.
	 *
	 * 상태를 정하는 규칙
	 * - 수정 파일: 그 파일의 변경이 하나도 없으면 "변경 없음", 하나라도 실패했으면 "실패", 모두 적용됐으면 "완료"
	 * - 새 파일: 복사/저장이 실패했으면 "실패"
	 *           편집 Agent가 불리지 않았으면(이름만 바꾸면 되는 파일, 내용을 그대로 저장한 파일) 복사/저장만으로 "완료".
	 *             다만 빼거나 더할 것이 있었는데 불리지 않았으면(복사한 파일을 읽지 못함) "실패"
	 *           편집 Agent 호출이 실패했으면 "실패", 변경 목록이 비었으면 "확인 필요"
	 *           변경 목록을 적용했으면 "완료"(괄호의 짝이 달라졌다는 주의가 붙었으면 "확인 필요"), 적용하지 못했으면 "실패"
	 * </pre>
	 *
	 * @param designs        파일별 설계 목록
	 * @param plan           planChanges()의 결과({copies, creates, edits, appends})
	 * @param copyResults    copies를 실행한 결과(같은 순서)
	 * @param createResults  creates를 실행한 결과
	 * @param editResults    edits를 실행한 결과
	 * @param appendResults  appends를 실행한 결과
	 * @param developInputs  편집 Agent에게 넣은 값 목록({design, source})
	 * @param developResults 편집 Agent의 답 목록(실패한 자리는 null)
	 * @param applyInputs    applyInputs()의 결과
	 * @param applyResults   그것을 실행한 결과
	 * @param errors         각 step의 실패 사유들(없는 것은 null이나 빈 글자)
	 */
	public static String tasksDoc(List<Object> designs, Map<String, Object> plan, List<Object> copyResults, List<Object> createResults, List<Object> editResults, List<Object> appendResults,
			List<Object> developInputs, List<Object> developResults, List<Object> applyInputs, List<Object> applyResults, List<Object> errors) {
		Map<String, Object> p = map(plan);
		// 새 파일: 경로 → 복사/저장 결과
		Map<String, String> copyByPath = new LinkedHashMap<>();
		putResults(copyByPath, list(p.get("copies")), "targetPath", copyResults);
		putResults(copyByPath, list(p.get("creates")), "filePath", createResults);
		// 새 파일: 경로 → 편집 Agent의 답(호출이 실패했으면 FAILED)
		Map<String, Object> developByPath = new LinkedHashMap<>();
		List<Object> developInputList = list(developInputs);
		List<Object> developResultList = list(developResults);
		for (int i = 0; i < developInputList.size(); i++) {
			Object result = i < developResultList.size() ? developResultList.get(i) : null;
			developByPath.put(text(map(map(developInputList.get(i)).get("design")).get("path")), result == null ? DEVELOP_FAILED : result);
		}
		// 새 파일: 경로 → 줄 번호 변경을 적용한 결과
		Map<String, String> applyByPath = new LinkedHashMap<>();
		putResults(applyByPath, list(applyInputs), "filePath", applyResults);
		// 수정 파일: (경로, 결과) 목록
		List<String[]> editRows = new ArrayList<>();
		addResults(editRows, list(p.get("edits")), editResults);
		addResults(editRows, list(p.get("appends")), appendResults);

		List<String> tableRows = new ArrayList<>();
		List<String> questions = new ArrayList<>();
		List<Map<String, Object>> ds = nonNull(designs);
		for (int i = 0; i < ds.size(); i++) {
			Map<String, Object> design = ds.get(i);
			String path = text(design.get("path"));
			List<String> editResultsOfFile = new ArrayList<>();
			for (String[] row : editRows) {
				if (row[0].equals(path)) {
					editResultsOfFile.add(row[1]);
				}
			}
			String copyResult = copyByPath.containsKey(path) ? copyByPath.get(path) : "";
			Object develop = developByPath.get(path);
			String applyResult = applyByPath.containsKey(path) ? applyByPath.get(path) : "";
			boolean needEdit = needsEdit(design);

			String status;
			String summary;
			if (TYPE_EDIT.equals(design.get("changeType"))) {
				status = editResultsOfFile.isEmpty() ? "변경 없음" : (anyStartsWith(editResultsOfFile, FAIL) ? "실패" : "완료");
				summary = String.join(" / ", editResultsOfFile);
			} else if (copyResult.startsWith(FAIL)) {
				status = "실패";
				summary = firstLine(copyResult);
			} else if (develop == null) {
				status = needEdit ? "실패" : "완료";
				summary = needEdit ? "복사한 파일을 읽지 못해 빼는 것과 더하는 것을 적용하지 못했습니다" : firstLine(copyResult);
			} else if (develop == DEVELOP_FAILED) {
				status = "실패";
				summary = "편집 Agent 호출이 실패했습니다. 파일은 이름만 바꿔 복사한 모습 그대로입니다";
			} else {
				String developSummary = or(map(develop).get("summary"), "");
				if (list(map(develop).get("edits")).isEmpty()) {
					status = "확인 필요";
					summary = developSummary + " (변경 목록이 비어 있어 아무것도 바꾸지 않았습니다)";
				} else if (applyResult.startsWith("고쳤습니다")) {
					boolean notice = applyResult.contains("주의:");
					status = notice ? "확인 필요" : "완료";
					summary = developSummary + (notice ? " / " + String.join(" ", linesStartingWith(applyResult, "주의:")) : "");
				} else {
					status = "실패";
					summary = "변경 목록을 적용하지 못했습니다. 파일은 이름만 바꿔 복사한 모습 그대로입니다(이유는 아래 '실패한 호출'). 하려던 변경: " + developSummary;
				}
			}
			tableRows.add("| TASK-" + (i + 1) + " | " + cell(path) + " | " + cell(or(design.get("changeType"), "")) + " | " + cell(status) + " | " + cell(summary) + " |");
			if (develop != null && develop != DEVELOP_FAILED) {
				for (Object question : list(map(develop).get("questions"))) {
					questions.add("- " + text(question) + " (" + path + ")");
				}
			}
		}

		List<String> failures = new ArrayList<>();
		for (Object error : list(errors)) {
			if (error != null && !text(error).isEmpty()) {
				failures.add(text(error));
			}
		}
		StringBuilder doc = new StringBuilder();
		doc.append("# 태스크 목록 (신규 개발)\n\n");
		doc.append("> 파일별 구현 결과를 Workflow가 모아 만든 문서입니다. 컴파일과 실행 검증은 하지 않았습니다. 사람이 확인해야 합니다.\n\n");
		doc.append("| 번호 | 대상 파일 | 유형 | 상태 | 구현 요약 |\n|---|---|---|---|---|\n");
		doc.append(String.join("\n", tableRows)).append("\n\n");
		doc.append("## 결정이 필요한 사항과 보류한 이유\n").append(questions.isEmpty() ? "없음" : String.join("\n", questions)).append("\n\n");
		doc.append("## 실패한 호출\n").append(failures.isEmpty() ? "없음" : String.join("\n", failures)).append("\n");
		return doc.toString();
	}

	// =====================================================================================
	// 작은 도우미들
	// =====================================================================================

	/** 편집 Agent 호출이 실패했다는 표시입니다(답이 없는 자리). */
	private static final Object DEVELOP_FAILED = new Object();

	/** 새 파일을 복사한 뒤에 빼거나 더할 것이 있는지 봅니다(본뜰 참고 파일이 있고, removes나 adds가 비어 있지 않으면). */
	private static boolean needsEdit(Map<String, Object> design) {
		return !or(design.get("referencePath"), "").isEmpty() && (!list(design.get("removes")).isEmpty() || !list(design.get("adds")).isEmpty());
	}

	/** 경로로 읽어 둔 파일의 내용을 찾습니다. 없으면 빈 글자입니다. */
	private static String sourceOf(Map<String, String> sources, String path) {
		if (sources == null || path.isEmpty() || sources.get(path) == null) {
			return "";
		}
		return sources.get(path);
	}

	/** Tool 인자 목록과 그 결과 목록(같은 순서)을 "경로 → 결과"로 맵에 담습니다. 같은 경로면 뒤의 것이 남습니다. */
	private static void putResults(Map<String, String> byPath, List<Object> inputs, String pathKey, List<Object> results) {
		List<Object> resultList = list(results);
		for (int i = 0; i < inputs.size() && i < resultList.size(); i++) {
			byPath.put(text(map(inputs.get(i)).get(pathKey)), text(resultList.get(i)));
		}
	}

	/** Tool 인자 목록과 그 결과 목록(같은 순서)을 (경로, 결과) 줄로 더합니다. */
	private static void addResults(List<String[]> rows, List<Object> inputs, List<Object> results) {
		List<Object> resultList = list(results);
		for (int i = 0; i < inputs.size() && i < resultList.size(); i++) {
			rows.add(new String[] { text(map(inputs.get(i)).get("filePath")), text(resultList.get(i)) });
		}
	}

	private static boolean anyStartsWith(List<String> values, String prefix) {
		for (String value : values) {
			if (value.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private static String firstLine(String value) {
		int newline = value.indexOf('\n');
		return newline < 0 ? value : value.substring(0, newline);
	}

	private static List<String> linesStartingWith(String value, String prefix) {
		List<String> lines = new ArrayList<>();
		for (String line : value.split("\n", -1)) {
			if (line.startsWith(prefix)) {
				lines.add(line);
			}
		}
		return lines;
	}

	/** needle이 text에 몇 번 나오는지 셉니다(겹치지 않게). */
	private static int count(String text, String needle) {
		int found = 0;
		int from = text.indexOf(needle);
		while (from >= 0) {
			found++;
			from = text.indexOf(needle, from + needle.length());
		}
		return found;
	}

	/** 값이 맵이면 그 맵을, 아니면 빈 맵을 돌려줍니다. */
	@SuppressWarnings("unchecked")
	static Map<String, Object> map(Object value) {
		return value instanceof Map ? (Map<String, Object>) value : Map.of();
	}

	/** 값이 리스트면 그 리스트를, 아니면 빈 리스트를 돌려줍니다. */
	@SuppressWarnings("unchecked")
	static List<Object> list(Object value) {
		return value instanceof List ? (List<Object>) value : List.of();
	}

	/** null이 아닌 설계만 골라 맵 목록으로 돌려줍니다. */
	private static List<Map<String, Object>> nonNull(List<Object> designs) {
		List<Map<String, Object>> result = new ArrayList<>();
		for (Object design : list(designs)) {
			if (design != null) {
				result.add(map(design));
			}
		}
		return result;
	}

	/** 값을 글자로 바꿉니다. 글자는 그대로, null은 "null", 그 밖(숫자, 맵, 리스트)은 JSON 글자입니다. */
	static String text(Object value) {
		if (value == null) {
			return "null";
		}
		return value instanceof String ? (String) value : JsonSchemaUtil.toText(value);
	}

	/** 값이 없으면(null 또는 false) 기본값을, 있으면 그 값을 글자로 돌려줍니다. */
	private static String or(Object value, String defaultValue) {
		return value == null || Boolean.FALSE.equals(value) ? defaultValue : text(value);
	}

	/** 리스트의 값들을 글자로 바꿔 돌려줍니다. */
	private static List<String> texts(Object values) {
		List<String> result = new ArrayList<>();
		for (Object value : list(values)) {
			result.add(text(value));
		}
		return result;
	}

	/** 리스트의 값들을 구분자로 잇습니다. null은 빈 글자로 칩니다. */
	private static String join(Object values, String separator) {
		List<String> parts = new ArrayList<>();
		for (Object value : list(values)) {
			parts.add(value == null ? "" : text(value));
		}
		return String.join(separator, parts);
	}

	/** 표의 한 칸에 넣을 글자로 바꿉니다. 칸을 나누는 | 는 / 로, 줄바꿈은 빈칸으로 바꿉니다. */
	private static String cell(Object value) {
		return text(value).replace("|", "/").replaceAll("\r?\n", " ");
	}

	/** 값이 없으면 빈 글자로 보고 표의 한 칸에 넣을 글자로 바꿉니다. */
	private static String cellOr(Object value) {
		return cell(or(value, ""));
	}

	/** 리스트를 "- 항목" 줄들로 만듭니다. 비어 있으면 "없음"입니다. */
	private static String bullets(Object values) {
		List<Object> items = list(values);
		if (items.isEmpty()) {
			return "없음";
		}
		List<String> lines = new ArrayList<>();
		for (Object item : items) {
			lines.add("- " + text(item));
		}
		return String.join("\n", lines);
	}

	/** 값을 코드 블록(```)으로 감쌉니다. */
	private static String code(Object value) {
		return "```\n" + or(value, "") + "\n```\n";
	}

	/** 목록의 i번째가 true인지 봅니다. 목록이 짧거나 없으면 false입니다. */
	private static boolean isTrueAt(List<Boolean> values, int index) {
		return values != null && index < values.size() && Boolean.TRUE.equals(values.get(index));
	}

}
