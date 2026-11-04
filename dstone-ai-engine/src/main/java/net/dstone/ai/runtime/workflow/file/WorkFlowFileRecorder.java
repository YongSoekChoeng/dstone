package net.dstone.ai.runtime.workflow.file;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.ai.runtime.tool.StepRef;

/**
 * <pre>
 * Workflow step이 파일 Tool로 읽고 쓴 파일을 기록합니다. 실행 관리 화면이 step별 문서를 보여 줄 때 이 기록을 씁니다.
 *
 * 모든 Tool 호출은 ConfigTool의 포장지(LimitedToolCallback)를 지나가고, 거기서 이 클래스를 부릅니다.
 * 그래서 AGENT step의 LLM이 고른 Tool, TOOL step이 직접 부른 Tool, Sub Agent가 부른 Tool이 모두 기록됩니다.
 *
 * 기록하지 않는 경우:
 * - Workflow 밖의 호출(채팅 API 등). ToolContext에 실행 id가 없습니다.
 * - 파일을 건드리지 않는 Tool, 또는 "실패: ..."로 끝난 호출(FileAccessRule).
 *
 * 쓰는 Tool이면 부르기 전에 그 파일이 있었는지 봐 둡니다(missingBefore()). 없던 파일을 만든 기록은 CREATED로 남아,
 * 재실행 때 이 실행이 만든 파일만 지울 수 있게 합니다. 원래 있던 소스를 고친 파일은 지우지 않습니다.
 *
 * 기록이 실패해도(테이블이 없거나 DB가 잠깐 끊긴 경우) Tool 호출은 그대로 성공합니다. 경고 로그만 남깁니다.
 * 기록은 화면을 위한 부가 기능이라, 이 때문에 Workflow가 멈추면 안 되기 때문입니다.
 * </pre>
 */
@Component
public class WorkFlowFileRecorder {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(WorkFlowFileRecorder.class);

	@Autowired
	private WorkFlowFileStore store;

	/**
	 * <pre>
	 * Tool을 부르기 직전에 불립니다. 이 호출이 쓸 파일 가운데 아직 없는 파일의 경로를 돌려줍니다.
	 * Workflow 밖의 호출이거나 쓰는 Tool이 아니면 빈 목록입니다.
	 * </pre>
	 *
	 * @param toolName    Tool 이름
	 * @param toolInput   Tool에 넘길 인자(JSON 글자)
	 * @param toolContext Tool이 받을 ToolContext
	 */
	public List<String> missingBefore(String toolName, String toolInput, ToolContext toolContext) {
		List<String> missing = new ArrayList<String>();
		if (StepRef.from(toolContext) == null) {
			return missing;
		}
		for (FileAccessRule.FileAccess access : FileAccessRule.of(toolName, toolInput)) {
			if (FileAccessRule.WRITE.equals(access.action()) && !new File(access.path()).exists()) {
				missing.add(access.path());
			}
		}
		return missing;
	}

	/**
	 * Tool 호출 하나가 끝난 뒤 불립니다. 파일을 건드린 호출이면 기록합니다.
	 *
	 * @param toolName      Tool 이름
	 * @param toolInput     Tool에 넘긴 인자(JSON 글자)
	 * @param result        Tool의 답
	 * @param toolContext   Tool이 받은 ToolContext(실행 id와 step id가 실려 있으면 기록합니다)
	 * @param missingBefore 부르기 전에 없던 파일들(missingBefore()의 값). 이 파일을 쓴 기록은 "새로 만듦"으로 남습니다
	 */
	public void record(String toolName, String toolInput, String result, ToolContext toolContext, List<String> missingBefore) {
		StepRef stepRef = StepRef.from(toolContext);
		if (stepRef == null || FileAccessRule.isFailure(result)) {
			return;
		}
		List<FileAccessRule.FileAccess> accesses = FileAccessRule.of(toolName, toolInput);
		for (FileAccessRule.FileAccess access : accesses) {
			try {
				boolean created = FileAccessRule.WRITE.equals(access.action()) && missingBefore != null && missingBefore.contains(access.path());
				this.store.record(stepRef.executionId(), stepRef.stepId(), access.path(), access.action(), created);
			} catch (RuntimeException e) {
				LOGGER.warn("dstone-ai-engine file: [" + stepRef.executionId() + "/" + stepRef.stepId() + "] 파일 기록을 남기지 못했습니다("
					+ access.path() + "). Workflow는 계속 진행합니다 - " + e.getMessage());
			}
		}
	}

}
