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
 * <pre>
 * Workflow 정보를 저장하는 컴퍼넌트. 
 * YamlDefinitionLoader => WorkFlowDefinition => WorkFlowRegistry 순서로 내용이 로딩된다.
 * classpath:workflows/*.yml 전체를 기동 시 한 번 읽어 id로 찾아주는 저장소. 
 * api.controller.WorkFlowController가 이 레지스트리에서 바로 WorkFlowDefinition을 찾아 runtime.workflow.WorkFlowExecutor에 넘긴다.
 * </pre>
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
			this.validateParallelGroups(definition);
			this.validateRouterSteps(definition);
			this.validateForEachSteps(definition);
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
	 * parallelGroup(병렬로 묶어서 실행할 스텝들)을 쓸 때 꼭 지켜야 하는 규칙 두 가지를 기동 시점에 미리 확인한다.
	 *
	 * 왜 이 검사가 필요하냐면, WorkFlowExecutor가 병렬 그룹 하나를 다 실행하고 나서 "그 다음에 어디로 갈까?"를
	 * 정할 때 그룹 안의 스텝 전부를 보는 게 아니라 딱 하나, steps 목록에서 제일 뒤에 적힌(=마지막) 스텝의
	 * onSuccess/onFailure만 보고 정하기 때문이다. 그래서:
	 *
	 * 규칙 1) 같은 parallelGroup 값을 가진 스텝들은 steps 목록에서 서로 붙어 있어야 한다.
	 *         중간에 다른 스텝이 하나라도 끼어 있으면, "그룹 다음 스텝을 찾는 계산"이 엉뚱한 스텝을
	 *         가리키게 될 수 있다.
	 *
	 * 규칙 2) onSuccess/onFailure는 그 그룹의 "제일 마지막 스텝"에만 적어야 한다.
	 *         마지막이 아닌 스텝에 적어봤자 실행 시점에 조용히 무시되기만 하고 아무 효과가 없으므로,
	 *         혼란을 막기 위해 애초에 YAML 작성 단계에서부터 못 적게 막는다.
	 *
	 * 두 규칙 모두 어기면 Workflow를 실행해봐야 뒤늦게 이상하게 동작하므로, 서버가 뜨는 시점에 바로
	 * 에러를 내서 YAML을 고치도록 유도한다.
	 * </pre>
	 *
	 * @param definition 검증할 workflow 정의
	 */
	private void validateParallelGroups(WorkFlowDefinition definition) {
		List<StepDefinition> steps = definition.steps();

		// 그룹 이름(parallelGroup 값)별로 "steps 목록에서 처음 나온 위치", "마지막으로 나온 위치", "몇 번 나왔는지"를 센다.
		Map<String, Integer> firstIndexByGroup = new HashMap<>();
		Map<String, Integer> lastIndexByGroup = new HashMap<>();
		Map<String, Integer> countByGroup = new HashMap<>();
		for (int i = 0; i < steps.size(); i++) {
			String group = steps.get(i).parallelGroup();
			if (StringUtil.isEmpty(group)) {
				continue;
			}
			if (!firstIndexByGroup.containsKey(group)) {
				firstIndexByGroup.put(group, i);
			}
			lastIndexByGroup.put(group, i);
			Integer countSoFar = countByGroup.get(group);
			countByGroup.put(group, countSoFar == null ? 1 : countSoFar + 1);
		}

		for (Map.Entry<String, Integer> entry : lastIndexByGroup.entrySet()) {
			String group = entry.getKey();
			int firstIndex = firstIndexByGroup.get(group);
			int lastIndex = entry.getValue();
			int count = countByGroup.get(group);

			// 규칙 1 검사: 만약 이 그룹이 정말로 붙어 있다면, firstIndex부터 lastIndex까지의 칸 수(lastIndex - firstIndex + 1)와
			// 실제로 이 그룹인 스텝 개수(count)가 정확히 같아야 한다. 둘이 다르다면 그 사이에 남의 스텝이 끼어 있다는 뜻이다.
			if (lastIndex - firstIndex + 1 != count) {
				throw new IllegalStateException("workflow[" + definition.id() + "]의 parallelGroup[" + group
					+ "]: 같은 그룹의 스텝들은 steps 목록에서 서로 붙어 있어야 합니다(중간에 다른 그룹/스텝이 끼어 있습니다).");
			}

			// 규칙 2 검사: 마지막 스텝(lastIndex)을 뺀 나머지 멤버(firstIndex ~ lastIndex-1)는 onSuccess/onFailure를 적으면 안 된다.
			for (int i = firstIndex; i < lastIndex; i++) {
				StepDefinition notLastMember = steps.get(i);
				if (!StringUtil.isEmpty(notLastMember.onSuccess()) || !StringUtil.isEmpty(notLastMember.onFailure())) {
					throw new IllegalStateException("workflow[" + definition.id() + "]의 step[" + notLastMember.id() + "]: parallelGroup[" + group
						+ "]의 마지막 스텝이 아니라서 여기 적은 onSuccess/onFailure는 무시됩니다. 그룹이 끝난 뒤 진행할 경로는 이 그룹의 마지막 스텝인 ["
						+ steps.get(lastIndex).id() + "]에 적어주세요.");
				}
			}
		}
	}

	/**
	 * <pre>
	 * ROUTER step은 routes(route 이름 -> 다음 step id)가 있어야 라우팅을 할 수 있고, parallelGroup/forEachVariable과는
	 * 함께 쓸 수 없다 - 병렬로 도는 형제 중 누구의 route를 따라야 하는지, 또는 반복 중 어느 반복의 route를 따라야 하는지가
	 * 모호해지기 때문이다(WorkFlowExecutor.decideTransition은 그룹/반복의 "마지막 step 하나"의 결과만 보고 다음을 정하는데,
	 * ROUTER의 route는 애초에 여러 개가 나올 수 있는 값이 아니라 "이 step 하나가 고른 값"이어야 의미가 있다).
	 * </pre>
	 *
	 * @param definition 검증할 workflow 정의
	 */
	private void validateRouterSteps(WorkFlowDefinition definition) {
		for (StepDefinition step : definition.steps()) {
			if (step.type() != StepType.ROUTER) {
				continue;
			}
			if (step.routes() == null || step.routes().isEmpty()) {
				throw new IllegalStateException("workflow[" + definition.id() + "]의 step[" + step.id() + "]: ROUTER step은 routes를 최소 1개 이상 정의해야 합니다.");
			}
			if (!StringUtil.isEmpty(step.parallelGroup())) {
				throw new IllegalStateException("workflow[" + definition.id() + "]의 step[" + step.id() + "]: ROUTER step은 parallelGroup을 가질 수 없습니다.");
			}
			if (!StringUtil.isEmpty(step.forEachVariable())) {
				throw new IllegalStateException("workflow[" + definition.id() + "]의 step[" + step.id() + "]: ROUTER step은 forEachVariable을 가질 수 없습니다.");
			}
		}
	}

	/**
	 * <pre>
	 * forEachVariable(동적 팬아웃)이 설정된 step은 parallelGroup(정적 병렬 그룹)과 함께 쓸 수 없다 - "정적으로 묶인 그룹 안의
	 * 한 step이 다시 동적으로 반복되는" 조합까지 다루려면 그룹/반복이 이중으로 얽혀서 "그 다음엔 어디로 갈까" 계산이 급격히
	 * 복잡해지므로, 필요해지기 전까지는 아예 막아둔다.
	 * </pre>
	 *
	 * @param definition 검증할 workflow 정의
	 */
	private void validateForEachSteps(WorkFlowDefinition definition) {
		for (StepDefinition step : definition.steps()) {
			if (!StringUtil.isEmpty(step.forEachVariable()) && !StringUtil.isEmpty(step.parallelGroup())) {
				throw new IllegalStateException("workflow[" + definition.id() + "]의 step[" + step.id() + "]: forEachVariable과 parallelGroup을 함께 쓸 수 없습니다.");
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
