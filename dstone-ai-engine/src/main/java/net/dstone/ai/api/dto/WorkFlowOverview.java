package net.dstone.ai.api.dto;

import java.util.ArrayList;
import java.util.List;

import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;

/**
 * <pre>
 * Workflow 하나를 step 종류와 순서로 요약한 것입니다. GET /api/ai/workflow/overview, GET /api/ai/workflow/{id}/steps 응답입니다.
 * AI 화면이 "어떤 step 이 어떤 순서로 도는지"를 색 띠로 그리는 데 씁니다(개요 화면, 실행 관리의 step 흐름 띠).
 * 등록된 정의 기준이라, 실행한 뒤에 정의를 고쳤으면 그 실행이 실제로 돈 step 과 다를 수 있습니다.
 * </pre>
 *
 * @param id          Workflow 식별자
 * @param description Workflow 설명
 * @param path        정의 파일 경로(정의 폴더 기준, 예: workflows/sample/sample-foreach-parallel.yml). 폴더별로 묶는 데 쓴다
 * @param steps       정의 파일에 적힌 순서대로의 step 요약
 */
public record WorkFlowOverview(String id, String description, String path, List<Step> steps) {

	/**
	 * @param id      step id
	 * @param type    AGENT, SUPERVISOR, ROUTER, TOOL, APPROVAL
	 * @param target  step이 부르는 Agent 또는 Tool 이름. 없으면 null
	 * @param forEach 항목마다 반복하는 step이면 true
	 */
	public record Step(String id, String type, String target, boolean forEach) {
	}

	/**
	 * @param definition 등록된 Workflow 정의
	 * @param path       그 정의 파일의 경로(common.registry.WorkFlowRegistry.pathOf())
	 */
	public static WorkFlowOverview from(WorkFlowDefinition definition, String path) {
		List<Step> steps = new ArrayList<Step>();
		if (definition.steps() != null) {
			for (StepDefinition step : definition.steps()) {
				steps.add(new Step(step.id(), step.type() == null ? null : step.type().name(), StepDefinition.refOf(step), StepDefinition.forEachOf(step) != null));
			}
		}
		return new WorkFlowOverview(definition.id(), definition.description(), path, steps);
	}

}
