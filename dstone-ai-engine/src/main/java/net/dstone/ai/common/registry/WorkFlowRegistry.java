package net.dstone.ai.common.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.consts.StepType;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.common.definition.WorkFlowDefinition;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * 모든 Workflow 정보를 담아두고, id로 찾아 주는 등록소입니다. 로딩 순서는 YamlDefinitionLoader가
 * classpath:workflows/*.yml 파일들을 읽어서 WorkFlowDefinition으로 바꾸고, 이 WorkFlowRegistry가
 * 앱이 기동될 때 그것들을 한 번 모아서 보관하는 식입니다.
 *
 * api.controller.WorkFlowController는 이 레지스트리에서 WorkFlowDefinition을 바로 찾아
 * runtime.workflow.WorkFlowExecutor에게 넘겨 실행을 시킵니다.
 */
@Component
public class WorkFlowRegistry extends BaseObject {

	@Autowired
	private YamlDefinitionLoader loader;

	private Map<String, WorkFlowDefinition> byId = Map.of();

	/**
	 * 앱이 기동될 때 한 번 호출되어, workflows/*.yml에 정의된 Workflow를 전부 읽어 id를 키로
	 * 하는 맵에 채워 넣습니다. id나 steps가 비어 있는 정의가 있거나, 같은 id가 둘 이상 있거나,
	 * APPROVAL/ROUTER step의 조합 규칙을 어기면 기동 자체를 실패시켜서 잘못된 설정이 조용히
	 * 넘어가지 않게 합니다.
	 */
	@PostConstruct
	public void load() {
		Map<String, WorkFlowDefinition> resolved = new HashMap<>();
		for (WorkFlowDefinition definition : this.loader.loadWorkflows()) {
			if (StringUtil.isEmpty(definition.id()) || definition.steps() == null || definition.steps().isEmpty()) {
				throw new IllegalStateException("workflows/*.yml 항목은 id와 steps가 모두 있어야 합니다: " + definition);
			}
			if (resolved.putIfAbsent(definition.id(), definition) != null) {
				throw new IllegalStateException("workflow id가 중복 등록되었습니다: " + definition.id());
			}
			this.validateApprovalSteps(definition);
			this.validateRouterSteps(definition);
		}
		this.byId = Map.copyOf(resolved);
		LogUtil.sysout("dstone-ai-engine workflow: 등록된 Workflow = " + (this.byId.isEmpty() ? "없음" : this.byId.keySet()));
	}

	/**
	 * APPROVAL step은 forEachVariable과 함께 쓸 수 없다는 규칙을 검사합니다. 사람의 승인/반려
	 * 결정은 variables.approvals.{stepId}처럼 step id 하나로만 구분되는데, forEachVariable로
	 * 같은 step을 N번 반복해 버리면 그 N개의 반복이 전부 같은 stepId를 공유하게 되어서 "몇 번째
	 * 반복이 승인됐는지"를 구분할 방법이 없어집니다. 이런 잘못된 조합이 YAML에 있으면 여기서
	 * 앱 기동 시점에 바로 막습니다.
	 *
	 * @param definition 검증할 Workflow 정의
	 */
	private void validateApprovalSteps(WorkFlowDefinition definition) {
		for (StepDefinition step : definition.steps()) {
			if (step.type() == StepType.APPROVAL && !StringUtil.isEmpty(step.forEachVariable())) {
				throw new IllegalStateException("workflow[" + definition.id() + "]의 step[" + step.id() + "]: APPROVAL 스텝은 forEachVariable을 가질 수 없습니다.");
			}
		}
	}

	/**
	 * ROUTER step에 관한 두 가지 규칙을 검사합니다. 첫째, ROUTER step은 routes(route 이름 →
	 * 다음 step id 매핑)가 최소 하나는 있어야 실제로 라우팅을 할 수 있습니다. 둘째, ROUTER step은
	 * forEachVariable과 함께 쓸 수 없습니다 - WorkFlowExecutor.decideTransition은 "이 step
	 * 하나"의 결과만 보고 다음 단계를 정하는데, 만약 같은 step을 여러 번 반복하면 그중 어느
	 * 반복이 고른 route를 따라야 하는지가 모호해지기 때문입니다.
	 *
	 * @param definition 검증할 Workflow 정의
	 */
	private void validateRouterSteps(WorkFlowDefinition definition) {
		for (StepDefinition step : definition.steps()) {
			if (step.type() != StepType.ROUTER) {
				continue;
			}
			if (step.routes() == null || step.routes().isEmpty()) {
				throw new IllegalStateException("workflow[" + definition.id() + "]의 step[" + step.id() + "]: ROUTER step은 routes를 최소 1개 이상 정의해야 합니다.");
			}
			if (!StringUtil.isEmpty(step.forEachVariable())) {
				throw new IllegalStateException("workflow[" + definition.id() + "]의 step[" + step.id() + "]: ROUTER step은 forEachVariable을 가질 수 없습니다.");
			}
		}
	}

	/**
	 * caller가 실행할 수 있는 Workflow만 골라 목록으로 돌려줍니다. api.controller.WorkFlowController의
	 * GET /api/ai/workflow가 이 목록을 그대로 dstone-boot의 "Workflow 테스트" 화면 드롭다운에
	 * 보여줍니다.
	 *
	 * resolve()와 똑같은 allowedCallers 규칙을 씁니다. caller가 쓸 수 없는 Workflow는 나중에
	 * resolve()에서 막히기 전에, 애초에 이 목록에서부터 보이지 않아야 합니다. 그래야 드롭다운에서
	 * 고를 수 있는 Workflow와 실제로 실행할 수 있는 Workflow가 항상 일치합니다.
	 *
	 * @param caller 호출한 앱/서비스를 나타내는 식별자(tenant)
	 */
	public List<WorkFlowDefinition> list(String caller) {
		List<WorkFlowDefinition> result = new ArrayList<>();
		for (WorkFlowDefinition definition : this.byId.values()) {
			List<String> allowedCallers = definition.allowedCallers();
			if (allowedCallers == null || allowedCallers.isEmpty() || (caller != null && allowedCallers.contains(caller))) {
				result.add(definition);
			}
		}
		result.sort(Comparator.comparing(WorkFlowDefinition::id));
		return result;
	}

	/**
	 * id로 Workflow를 찾아서 돌려줍니다. 이때 caller가 그 Workflow를 쓸 수 있는지도 함께
	 * 확인합니다. 등록되지 않은 id이거나, caller가 화이트리스트를 통과하지 못하면 조용히
	 * 넘어가지 않고 바로 예외를 던져서 알려줍니다.
	 *
	 * @param id     조회할 Workflow id
	 * @param caller 호출한 앱/서비스를 나타내는 식별자(tenant)
	 */
	public WorkFlowDefinition resolve(String id, String caller) {
		WorkFlowDefinition definition = this.byId.get(id);
		if (definition == null) {
			throw new IllegalArgumentException("등록되지 않은 workflow입니다: " + id);
		}
		List<String> allowedCallers = definition.allowedCallers();
		if (allowedCallers != null && !allowedCallers.isEmpty() && (caller == null || !allowedCallers.contains(caller))) {
			throw new IllegalArgumentException("workflow[" + id + "]는 caller[" + caller + "]에게 허용되지 않았습니다.");
		}
		return definition;
	}

}
