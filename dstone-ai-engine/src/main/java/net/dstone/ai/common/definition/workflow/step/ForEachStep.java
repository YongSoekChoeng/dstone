package net.dstone.ai.common.definition.workflow.step;

import net.dstone.ai.common.consts.Constants;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * forEach로 같은 step을 여러 번 동시에(병렬로) 실행할 수 있는 step들(AGENT/SUPERVISOR/TOOL)의 공통 약속입니다.
 *
 * forEach에 리스트를 가리키는 참조 경로를 적으면(예: inputs.sqlList, steps.list.output.lines), 그 리스트의
 * 항목 개수만큼 이 step을 한꺼번에 실행합니다. 각 실행에서는 자기가 맡은 항목을 {{item}}(itemVariable로
 * 이름을 바꿀 수 있음)으로 꺼내 씁니다. 결과는 steps.이id.items에 반복 순서대로 쌓입니다.
 *
 * APPROVAL과 ROUTER는 이 interface가 없어서 forEach를 쓸 수 없습니다. APPROVAL은 사람의 결정이 step id
 * 하나로만 구분되고, ROUTER는 여러 반복 중 어느 반복의 선택을 따라야 할지 정할 수 없기 때문입니다.
 * </pre>
 */
public sealed interface ForEachStep extends StepDefinition permits AgentStep, SupervisorStep, ToolStep {

	/** 반복 실행할 리스트의 참조 경로입니다(예: inputs.sqlList). 비워두면 한 번만 실행합니다. */
	String forEach();

	/** 반복 중 항목을 담을 변수 이름입니다. 비워두면 "item"입니다. */
	String itemVariable();

	/** forEach가 적혀 있어서 반복 실행하는 step인지 알려줍니다. */
	default boolean repeats() {
		return !StringUtil.isEmpty(this.forEach());
	}

	/** 항목을 담을 변수 이름을 돌려줍니다. itemVariable이 비어 있으면 "item"입니다. */
	default String itemKey() {
		return StringUtil.isEmpty(this.itemVariable()) ? Constants.WorkFlow.DEFAULT_ITEM_VARIABLE_KEY : this.itemVariable();
	}

}
