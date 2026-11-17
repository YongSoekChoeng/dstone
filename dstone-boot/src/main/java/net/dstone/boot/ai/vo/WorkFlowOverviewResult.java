package net.dstone.boot.ai.vo;

import java.util.List;

/**
 * <pre>
 * /ai/overview/workflows.do 응답 한 항목. AI 개요 화면이 Workflow마다 step 흐름 띠를 그리는 데 씁니다.
 * </pre>
 *
 * @param id          Workflow 식별자
 * @param description Workflow 설명
 * @param path        정의 파일 경로(예: workflows/sample/sample-foreach-parallel.yml). 폴더별로 묶는 데 씁니다
 * @param steps       정의 파일에 적힌 순서대로의 step 요약
 * @param error       정의를 읽지 못했을 때의 이유. 읽었으면 null
 */
public record WorkFlowOverviewResult(String id, String description, String path, List<Step> steps, String error) {

	/**
	 * @param id      step id
	 * @param type    AGENT, SUPERVISOR, ROUTER, TOOL, APPROVAL
	 * @param target  step이 부르는 Agent 또는 Tool 이름. 없으면 null
	 * @param forEach 항목마다 반복하는 step이면 true
	 */
	public record Step(String id, String type, String target, boolean forEach) {
	}

}
