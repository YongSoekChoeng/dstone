package net.dstone.ai.api.dto;

import java.util.List;
import java.util.Map;

/**
 * <pre>
 * GET /api/ai/definitions 응답입니다. dstone-boot의 "Workflow 디자이너" 화면이 처음 열릴 때 한 번 받아,
 * Workflow 목록과 step에서 고를 수 있는 Agent/Tool/Skill 목록을 채웁니다.
 * </pre>
 *
 * @param location  정의 폴더 위치(설정 dstone.ai.definition.location)
 * @param editable  화면에서 저장할 수 있는지(dstone.ai.definition.edit-enabled가 켜져 있고 정의 폴더가 디스크 폴더일 때)
 * @param workflows 등록된 Workflow
 * @param agents    등록된 Agent(입출력 계약 포함)
 * @param tools     등록된 Tool(MCP Tool 포함)
 * @param skills    등록된 Skill
 */
public record DefinitionCatalog(String location, boolean editable, List<WorkflowItem> workflows, List<AgentItem> agents, List<ToolItem> tools, List<SkillItem> skills) {

	/**
	 * @param id          Workflow id
	 * @param version     버전
	 * @param description 설명
	 * @param path        파일 경로(정의 폴더 기준)
	 * @param steps       step 수
	 */
	public record WorkflowItem(String id, String version, String description, String path, int steps) {
	}

	/**
	 * @param id          Agent id
	 * @param version     버전
	 * @param description 설명
	 * @param role        역할 이름
	 * @param path        파일 경로(정의 폴더 기준)
	 * @param input       받는 값의 모양(JSON Schema)
	 * @param output      돌려주는 값의 모양(JSON Schema)
	 * @param declaresOutput output을 선언했는지(SUPERVISOR/ROUTER step에는 선언하지 않은 Agent만 씁니다)
	 * @param prompt         프롬프트 본문. 화면이 ROUTER step의 routes 이름이 프롬프트에 있는지 미리 볼 때 씁니다
	 * @param tools       쓸 수 있는 Tool 이름
	 * @param skills      꺼내 읽을 수 있는 Skill 이름
	 * @param subAgents   일을 맡길 수 있는 Agent id
	 * @param subAgentBackground Sub Agent에게 기다리지 않고 맡길 수 있는지(subAgents.background)
	 */
	public record AgentItem(String id, String version, String description, String role, String path, Map<String, Object> input, Map<String, Object> output,
		boolean declaresOutput, String prompt, List<String> tools, List<String> skills, List<String> subAgents, boolean subAgentBackground) {
	}

	/**
	 * @param name        Tool 이름
	 * @param description 설명
	 * @param input       인자 모양(JSON Schema)
	 * @param stepOnly    TOOL step에서만 부를 수 있는 Tool인지
	 */
	public record ToolItem(String name, String description, Map<String, Object> input, boolean stepOnly) {
	}

	/**
	 * @param name        Skill 이름
	 * @param description 설명
	 * @param path        SKILL.md 경로(정의 폴더 기준)
	 */
	public record SkillItem(String name, String description, String path) {
	}

}
