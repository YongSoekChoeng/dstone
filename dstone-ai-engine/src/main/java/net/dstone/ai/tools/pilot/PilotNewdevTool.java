package net.dstone.ai.tools.pilot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.tools.utils.FileUtil;

/**
 * <pre>
 * pilot-workflow의 신규 개발 경로가 TOOL step으로 부르는 Tool들입니다. LLM 없이 코드로 정해지는 일을 합니다.
 *
 *   하는 일                      Tool
 *   참고 구현 파일 읽어 두기       pilotNewdevReadSources
 *   요구사항/구성 검증            pilotNewdevCheckAnalysis      → 02-impact-check.md
 *   요구사항 정의서 만들기         pilotNewdevWriteRequirements  → 01-requirements.md
 *   영향도 분석서 만들기           pilotNewdevWriteImpact        → 02-impact.md
 *   파일별 설계에 넣을 값 만들기    pilotNewdevDesignInputs
 *   설계 검증                    pilotNewdevCheckDesign        → 04-design-check.md
 *   설계서 만들기                 pilotNewdevWriteDesign        → 04-design.md
 *   적용할 변경 나누기             pilotNewdevPlanChanges
 *   편집 Agent에 넣을 값 만들기    pilotNewdevDevelopInputs
 *   줄 번호 변경 적용 목록 만들기   pilotNewdevApplyInputs
 *   태스크 목록 만들기             pilotNewdevWriteTasks         → 04-tasks.md
 *
 * 이 클래스는 파일을 읽고 쓰는 일과 값을 주고받는 일만 합니다. 무엇을 검증하고 문서를 어떤 모양으로 만드는지는
 * PilotNewdevLogic에 있습니다(파일 없이 단위 테스트할 수 있게 나눴습니다).
 *
 * stepOnly = true라서 LLM에게는 보이지 않고, 결과 길이 상한도 걸리지 않습니다(읽어 둔 파일 내용처럼 큰 값을 그대로 돌려줍니다).
 * 파일을 읽고 쓸 때는 다른 Agent들이 쓰는 것과 같은 파일 Tool(tools.utils.FileUtil)을 거칩니다. 그래서 읽기 상한
 * (dstone.ai.tool.file.max-read-chars)과 실패했을 때의 답("실패: ...")이 똑같습니다.
 * </pre>
 */
@AiTool(stepOnly = true)
public class PilotNewdevTool {

	@Autowired
	private FileUtil fileUtil;

	/**
	 * <pre>
	 * 참고 구현의 파일들을 한 번만 읽어 둡니다. 있는 파일만, 같은 경로는 한 번만 읽습니다.
	 * 돌려주는 값: {files: {경로: 내용}, missing: [없는 파일의 경로], text: Agent에게 넘길 글자 하나}
	 * 큰 파일은 앞부분만 옵니다(dstone.ai.tool.file.max-read-chars). 그때는 내용 끝에 잘렸다는 안내가 붙습니다.
	 * </pre>
	 *
	 * @param files 참고 구현의 파일 목록입니다({path, ...}).
	 */
	@Tool(description = "참고 구현의 파일들을 읽어 {files: {경로: 내용}, missing, text}로 돌려준다(pilot-workflow 신규 개발 경로 전용).")
	public Map<String, Object> pilotNewdevReadSources(@ToolParam(description = "참고 구현의 파일 목록({path})") List<Map<String, Object>> files) {
		Map<String, String> contents = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (Map<String, Object> file : files == null ? List.<Map<String, Object>>of() : files) {
			String path = PilotNewdevLogic.text(PilotNewdevLogic.map(file).get("path"));
			if (!this.fileUtil.isFileExist(path)) {
				missing.add(path);
				continue;
			}
			if (contents.containsKey(path)) {
				continue;
			}
			String content = this.fileUtil.readFile(path);
			if (content == null || content.startsWith("실패")) {
				return this.fail(content == null ? "파일을 읽지 못했습니다: " + path : content);
			}
			contents.put(path, content);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("files", contents);
		result.put("missing", missing);
		result.put("text", PilotNewdevLogic.sourcesText(contents));
		return result;
	}

	/**
	 * <pre>
	 * 요구사항/구성 정의를 코드로 검증하고, 결과를 02-impact-check.md에 적습니다.
	 * 돌려주는 값: {ok, problems: [걸린 것], text: 문서에 넣을 글자("문제 없음" 또는 목록), feedback: 다시 분석시킬 때 넘길 글자(문제가 없으면 빈 글자)}
	 * </pre>
	 *
	 * @param workDir   작업 폴더(요청서가 있는 폴더)
	 * @param analysis  요구사항/구성 정의(pilot-newdev-analyzer-agent의 답)
	 * @param reference 참고 구현(pilot-newdev-reference-agent의 답)
	 * @param sources   pilotNewdevReadSources의 결과
	 */
	@Tool(description = "신규 개발의 요구사항/구성 정의를 검증하고 02-impact-check.md에 적는다. {ok, problems, text, feedback}을 돌려준다(pilot-workflow 전용).")
	public Map<String, Object> pilotNewdevCheckAnalysis(
			@ToolParam(description = "작업 폴더") String workDir,
			@ToolParam(description = "요구사항/구성 정의") Map<String, Object> analysis,
			@ToolParam(description = "참고 구현") Map<String, Object> reference,
			@ToolParam(description = "pilotNewdevReadSources의 결과") Map<String, Object> sources) {
		List<Object> referenceFiles = PilotNewdevLogic.list(PilotNewdevLogic.map(reference).get("files"));
		List<Boolean> referenceExists = this.exists(referenceFiles);
		List<Boolean> targetExists = this.exists(PilotNewdevLogic.list(PilotNewdevLogic.map(analysis).get("targets")));
		List<String> readPaths = new ArrayList<>(this.sourceFiles(sources).keySet());
		List<String> problems = PilotNewdevLogic.checkAnalysis(analysis, referenceFiles, referenceExists, targetExists, readPaths);
		return this.saveCheck(workDir + "/02-impact-check.md", problems);
	}

	/**
	 * 요구사항 정의서(01-requirements.md)를 만듭니다. Agent가 낸 데이터를 틀에 끼웁니다.
	 *
	 * @param workDir   작업 폴더
	 * @param analysis  요구사항/구성 정의
	 * @param reference 참고 구현
	 */
	@Tool(description = "신규 개발의 요구사항 정의서(01-requirements.md)를 데이터로 만들어 저장한다(pilot-workflow 전용).")
	public String pilotNewdevWriteRequirements(
			@ToolParam(description = "작업 폴더") String workDir,
			@ToolParam(description = "요구사항/구성 정의") Map<String, Object> analysis,
			@ToolParam(description = "참고 구현") Map<String, Object> reference) {
		return this.fileUtil.writeFile(workDir + "/01-requirements.md", PilotNewdevLogic.requirementsDoc(analysis, reference));
	}

	/**
	 * 영향도 분석서(02-impact.md)를 만듭니다. 맨 위에 자동 검증 결과가 들어갑니다.
	 *
	 * @param workDir   작업 폴더
	 * @param analysis  요구사항/구성 정의
	 * @param reference 참고 구현
	 * @param checkText 자동 검증 결과(pilotNewdevCheckAnalysis의 text)
	 */
	@Tool(description = "신규 개발의 영향도 분석서(02-impact.md)를 데이터로 만들어 저장한다(pilot-workflow 전용).")
	public String pilotNewdevWriteImpact(
			@ToolParam(description = "작업 폴더") String workDir,
			@ToolParam(description = "요구사항/구성 정의") Map<String, Object> analysis,
			@ToolParam(description = "참고 구현") Map<String, Object> reference,
			@ToolParam(required = false, description = "자동 검증 결과") String checkText) {
		return this.fileUtil.writeFile(workDir + "/02-impact.md", PilotNewdevLogic.impactDoc(analysis, reference, checkText));
	}

	/**
	 * 파일별 설계 Agent에게 넣을 값을 변경 대상 파일마다 하나씩 만듭니다. 이 목록으로 설계 step이 forEach를 돕니다.
	 *
	 * @param request  요청서 내용
	 * @param analysis 요구사항/구성 정의
	 * @param sources  pilotNewdevReadSources의 결과
	 * @param feedback 다시 설계할 때 반영할 지적 사항(처음이면 빈 글자)
	 */
	@Tool(description = "파일별 설계 Agent에게 넣을 값을 변경 대상 파일마다 하나씩 만든다(pilot-workflow 전용).")
	public List<Map<String, Object>> pilotNewdevDesignInputs(
			@ToolParam(description = "요청서 내용") String request,
			@ToolParam(description = "요구사항/구성 정의") Map<String, Object> analysis,
			@ToolParam(description = "pilotNewdevReadSources의 결과") Map<String, Object> sources,
			@ToolParam(required = false, description = "다시 설계할 때 반영할 지적 사항") String feedback) {
		return PilotNewdevLogic.designInputs(request, analysis, this.sourceFiles(sources), feedback);
	}

	/**
	 * <pre>
	 * 파일별 설계를 코드로 검증하고, 결과를 04-design-check.md에 적습니다.
	 * 돌려주는 값은 pilotNewdevCheckAnalysis와 같은 모양입니다({ok, problems, text, feedback}).
	 * </pre>
	 *
	 * @param workDir     작업 폴더
	 * @param analysis    요구사항/구성 정의
	 * @param designs     파일별 설계 목록(설계에 실패한 파일의 자리는 null)
	 * @param designError 설계 step의 실패 사유(없으면 null)
	 * @param sources     pilotNewdevReadSources의 결과
	 */
	@Tool(description = "신규 개발의 파일별 설계를 검증하고 04-design-check.md에 적는다. {ok, problems, text, feedback}을 돌려준다(pilot-workflow 전용).")
	public Map<String, Object> pilotNewdevCheckDesign(
			@ToolParam(description = "작업 폴더") String workDir,
			@ToolParam(description = "요구사항/구성 정의") Map<String, Object> analysis,
			@ToolParam(required = false, description = "파일별 설계 목록") List<Object> designs,
			@ToolParam(required = false, description = "설계 step의 실패 사유") String designError,
			@ToolParam(description = "pilotNewdevReadSources의 결과") Map<String, Object> sources) {
		List<String> problems = PilotNewdevLogic.checkDesign(analysis, designs, designError, this.sourceFiles(sources));
		return this.saveCheck(workDir + "/04-design-check.md", problems);
	}

	/**
	 * 설계서(04-design.md)를 만듭니다. 파일별 설계를 모아 틀에 끼웁니다.
	 *
	 * @param workDir   작업 폴더
	 * @param designs   파일별 설계 목록
	 * @param checkText 자동 검증 결과(pilotNewdevCheckDesign의 text)
	 */
	@Tool(description = "신규 개발의 설계서(04-design.md)를 데이터로 만들어 저장한다(pilot-workflow 전용).")
	public String pilotNewdevWriteDesign(
			@ToolParam(description = "작업 폴더") String workDir,
			@ToolParam(required = false, description = "파일별 설계 목록") List<Object> designs,
			@ToolParam(required = false, description = "자동 검증 결과") String checkText) {
		return this.fileUtil.writeFile(workDir + "/04-design.md", PilotNewdevLogic.designDoc(designs, checkText));
	}

	/**
	 * <pre>
	 * 설계를 보고, LLM 없이 그대로 적용할 변경을 네 목록으로 나눕니다: {copies, creates, edits, appends}.
	 * 각 목록의 항목은 파일 Tool(copyFileWithReplace / writeFile / replaceInFile / appendFile)의 인자 모양 그대로라서,
	 * Workflow가 목록마다 forEach로 그 Tool을 부르면 됩니다.
	 * </pre>
	 *
	 * @param designs 파일별 설계 목록
	 */
	@Tool(description = "신규 개발의 설계를 복사/새로 쓰기/고치기/덧붙이기 목록({copies, creates, edits, appends})으로 나눈다(pilot-workflow 전용).")
	public Map<String, Object> pilotNewdevPlanChanges(@ToolParam(required = false, description = "파일별 설계 목록") List<Object> designs) {
		return PilotNewdevLogic.planChanges(designs);
	}

	/**
	 * <pre>
	 * 편집 Agent에게 넣을 값을 만듭니다. 복사해 만든 새 파일 가운데 빼거나 더할 것이 있는 파일을 줄 번호와 함께 읽어서
	 * [{design, source}] 목록으로 돌려줍니다. 복사에 실패했거나 읽지 못한 파일은 넣지 않습니다(그런 파일은 더 건드리지 않습니다).
	 * </pre>
	 *
	 * @param designs     파일별 설계 목록
	 * @param copies      pilotNewdevPlanChanges의 copies
	 * @param copyResults copies를 하나씩 실행한 결과(같은 순서)
	 */
	@Tool(description = "복사한 새 파일 가운데 빼거나 더할 것이 있는 파일을 줄 번호와 함께 읽어 [{design, source}]로 돌려준다(pilot-workflow 전용).")
	public List<Map<String, Object>> pilotNewdevDevelopInputs(
			@ToolParam(required = false, description = "파일별 설계 목록") List<Object> designs,
			@ToolParam(required = false, description = "복사 목록") List<Object> copies,
			@ToolParam(required = false, description = "복사 결과 목록") List<Object> copyResults) {
		List<Map<String, Object>> inputs = new ArrayList<>();
		for (Map<String, Object> design : PilotNewdevLogic.designsToDevelop(designs, copies, copyResults)) {
			String source = this.fileUtil.readFileLines(PilotNewdevLogic.text(design.get("path")), null, null);
			if (source == null || source.startsWith("실패")) {
				continue;
			}
			Map<String, Object> input = new LinkedHashMap<>();
			input.put("design", design);
			input.put("source", source);
			inputs.add(input);
		}
		return inputs;
	}

	/**
	 * 편집 Agent가 정한 줄 번호 변경을 파일마다 적용할 목록([{filePath, edits}])으로 만듭니다. editFileLines의 인자 모양입니다.
	 *
	 * @param developInputs  편집 Agent에게 넣은 값 목록
	 * @param developResults 편집 Agent의 답 목록(같은 순서. 실패한 자리는 null)
	 */
	@Tool(description = "편집 Agent가 정한 줄 번호 변경을 파일마다 적용할 목록([{filePath, edits}])으로 만든다(pilot-workflow 전용).")
	public List<Map<String, Object>> pilotNewdevApplyInputs(
			@ToolParam(required = false, description = "편집 Agent에게 넣은 값 목록") List<Object> developInputs,
			@ToolParam(required = false, description = "편집 Agent의 답 목록") List<Object> developResults) {
		return PilotNewdevLogic.applyInputs(developInputs, developResults);
	}

	/**
	 * 태스크 목록(04-tasks.md)을 만듭니다. 파일별 구현 결과를 모아 상태(완료/실패/확인 필요)를 정합니다(규칙은 PilotNewdevLogic.tasksDoc()).
	 *
	 * @param workDir        작업 폴더
	 * @param designs        파일별 설계 목록
	 * @param plan           pilotNewdevPlanChanges의 결과
	 * @param copyResults    복사 결과 목록
	 * @param createResults  새로 쓰기 결과 목록
	 * @param editResults    고치기 결과 목록
	 * @param appendResults  덧붙이기 결과 목록
	 * @param developInputs  편집 Agent에게 넣은 값 목록
	 * @param developResults 편집 Agent의 답 목록
	 * @param applyInputs    pilotNewdevApplyInputs의 결과
	 * @param applyResults   줄 번호 변경을 적용한 결과 목록
	 * @param errors         각 step의 실패 사유들
	 */
	@Tool(description = "신규 개발의 태스크 목록(04-tasks.md)을 파일별 구현 결과로 만들어 저장한다(pilot-workflow 전용).")
	public String pilotNewdevWriteTasks(
			@ToolParam(description = "작업 폴더") String workDir,
			@ToolParam(required = false, description = "파일별 설계 목록") List<Object> designs,
			@ToolParam(required = false, description = "적용할 변경 목록") Map<String, Object> plan,
			@ToolParam(required = false, description = "복사 결과 목록") List<Object> copyResults,
			@ToolParam(required = false, description = "새로 쓰기 결과 목록") List<Object> createResults,
			@ToolParam(required = false, description = "고치기 결과 목록") List<Object> editResults,
			@ToolParam(required = false, description = "덧붙이기 결과 목록") List<Object> appendResults,
			@ToolParam(required = false, description = "편집 Agent에게 넣은 값 목록") List<Object> developInputs,
			@ToolParam(required = false, description = "편집 Agent의 답 목록") List<Object> developResults,
			@ToolParam(required = false, description = "줄 번호 변경 적용 목록") List<Object> applyInputs,
			@ToolParam(required = false, description = "줄 번호 변경 적용 결과 목록") List<Object> applyResults,
			@ToolParam(required = false, description = "각 step의 실패 사유들") List<Object> errors) {
		String doc = PilotNewdevLogic.tasksDoc(designs, plan, copyResults, createResults, editResults, appendResults, developInputs, developResults, applyInputs, applyResults, errors);
		return this.fileUtil.writeFile(workDir + "/04-tasks.md", doc);
	}

	/**
	 * 검증 결과를 파일에 적고, Workflow가 쓸 값({ok, problems, text, feedback})으로 돌려줍니다.
	 *
	 * @param filePath 검증 결과를 적을 파일
	 * @param problems 검증에 걸린 문장 목록
	 */
	private Map<String, Object> saveCheck(String filePath, List<String> problems) {
		String text = PilotNewdevLogic.checkText(problems);
		String saved = this.fileUtil.writeFile(filePath, text);
		if (saved == null || saved.startsWith("실패")) {
			return this.fail(saved == null ? "검증 결과를 저장하지 못했습니다: " + filePath : saved);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("ok", problems.isEmpty());
		result.put("problems", problems);
		result.put("text", text);
		// 문제가 없으면 다시 시킬 것도 없으므로 빈 글자입니다. "문제 없음"이라는 글자를 지적 사항으로 넘기지 않습니다.
		result.put("feedback", problems.isEmpty() ? "" : text);
		return result;
	}

	/** 목록의 항목({path})마다 그 파일이 지금 있는지 봅니다. */
	private List<Boolean> exists(List<Object> items) {
		List<Boolean> result = new ArrayList<>();
		for (Object item : items) {
			Object path = PilotNewdevLogic.map(item).get("path");
			result.add(path != null && this.fileUtil.isFileExist(path.toString()));
		}
		return result;
	}

	/** pilotNewdevReadSources의 결과에서 "경로 → 내용" 맵을 꺼냅니다. */
	private Map<String, String> sourceFiles(Map<String, Object> sources) {
		Map<String, String> files = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : PilotNewdevLogic.map(PilotNewdevLogic.map(sources).get("files")).entrySet()) {
			files.put(entry.getKey(), PilotNewdevLogic.text(entry.getValue()));
		}
		return files;
	}

	/** TOOL step이 실패로 알아보는 모양({success: false, message})을 만듭니다. */
	private Map<String, Object> fail(String message) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", false);
		result.put("message", message);
		return result;
	}

}
