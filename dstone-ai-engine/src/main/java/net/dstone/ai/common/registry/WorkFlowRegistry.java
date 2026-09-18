package net.dstone.ai.common.registry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.common.definition.StepType;
import net.dstone.ai.common.definition.WorkFlowDefinition;
import net.dstone.ai.common.loader.YamlDefinitionLoader;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * Workflow 정보를 저장하는 컴퍼넌트. YamlDefinitionLoader => WorkFlowDefinition => WorkFlowRegistry 순서로 내용이 로딩된다.
 * classpath:workflows/*.yml 전체를 기동 시 한 번 읽어 id로 찾아주는 등록소다. api.controller.WorkFlowController가 이 레지스트리에서 바로
 * WorkFlowDefinition을 찾아 runtime.workflow.WorkFlowExecutor에 넘긴다.
 */
@Component
public class WorkFlowRegistry extends BaseObject {

	@Autowired
	private YamlDefinitionLoader loader;

	private Map<String, WorkFlowDefinition> byId = Map.of();

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
			this.validateApprovalStepsNotParallel(definition);
		}
		this.byId = Map.copyOf(resolved);
		LogUtil.sysout("dstone-ai-engine workflow: 등록된 Workflow = " + (this.byId.isEmpty() ? "없음" : this.byId.keySet()));
	}

	/**
	 * <pre>
	 * APPROVAL 스텝은 parallelGroup을 가질 수 없다 - 승인 대기로 멈춘 스텝과 이미 끝난 형제 스텝을 함께
	 * 영속화/재개하는 시나리오는 아직 다루지 않으므로, 그런 조합이 YAML에 있으면 기동 시점에 바로 막는다.
	 * </pre>
	 *
	 * @param definition 검증할 workflow 정의
	 */
	private void validateApprovalStepsNotParallel(WorkFlowDefinition definition) {
		for (StepDefinition step : definition.steps()) {
			if (step.type() == StepType.APPROVAL && !StringUtil.isEmpty(step.parallelGroup())) {
				throw new IllegalStateException("workflow[" + definition.id() + "]의 step[" + step.id() + "]: APPROVAL 스텝은 parallelGroup을 가질 수 없습니다.");
			}
		}
	}

	/**
	 * <pre>
	 * 모르는 id거나 caller가 화이트리스트를 통과하지 못하면 조용히 넘어가지 않고 바로 에러로 알려준다.
	 * </pre>
	 *
	 * @param id     조회할 workflow id
	 * @param caller 호출한 앱/서비스 식별자(tenant)
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
