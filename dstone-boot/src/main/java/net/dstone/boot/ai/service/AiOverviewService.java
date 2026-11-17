package net.dstone.boot.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import net.dstone.boot.ai.vo.WorkFlowOverviewResult;
import net.dstone.boot.ai.vo.WorkFlowSummaryResult;
import net.dstone.common.config.ConfigProperty;

/**
 * <pre>
 * AI 개요 화면(views/ai/index.jsp)에 보여 줄 Workflow 요약을 만듭니다.
 *
 * 엔진의 Workflow 목록(GET /api/ai/workflow)에는 id와 설명만 있어서,
 * Workflow마다 정의 원문(GET /api/ai/definitions/workflows/{id})을 읽어 step의 종류와 순서만 뽑습니다.
 * 화면은 이걸로 "어떤 step이 어떤 순서로 도는지"를 색 띠로 그립니다.
 *
 * 정의 하나를 못 읽어도 나머지는 보여 줍니다(그 항목의 error에 이유를 담습니다).
 * 목록 자체를 못 읽으면 예외를 그대로 던집니다. 화면은 이걸 "엔진에 연결되지 않음"으로 보여 줍니다.
 * </pre>
 */
@Service
public class AiOverviewService extends net.dstone.boot.common.biz.BaseService {

	@Autowired
	private ConfigProperty configProperty;

	/** Workflow 목록은 테스트 화면과 같은 것을 씁니다(같은 caller 규칙). */
	@Autowired
	private WorkFlowTestService workFlowTestService;

	/** @return 엔진에 등록된 Workflow마다 step 요약을 붙인 목록 */
	public List<WorkFlowOverviewResult> workflows() {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		List<WorkFlowOverviewResult> results = new ArrayList<WorkFlowOverviewResult>();
		for (WorkFlowSummaryResult summary : this.workFlowTestService.listWorkflows()) {
			List<WorkFlowOverviewResult.Step> steps = new ArrayList<WorkFlowOverviewResult.Step>();
			String error = null;
			try {
				steps = this.readSteps(baseUrl, summary.id());
			} catch (Exception e) {
				error = e.getMessage();
			}
			results.add(new WorkFlowOverviewResult(summary.id(), summary.description(), summary.path(), steps, error));
		}
		return results;
	}

	/** 정의 원문을 받아 steps 아래의 id, type, 부르는 대상, forEach 여부만 뽑습니다. */
	@SuppressWarnings("unchecked")
	private List<WorkFlowOverviewResult.Step> readSteps(String baseUrl, String workflowId) {
		Map<String, Object> source = this.getLargeResponseWebClient().get()
				.uri(baseUrl + "/api/ai/definitions/workflows/{id}", workflowId)
				.retrieve()
				.bodyToMono(Map.class)
				.block();
		List<WorkFlowOverviewResult.Step> steps = new ArrayList<WorkFlowOverviewResult.Step>();
		Object text = source == null ? null : source.get("text");
		if (!(text instanceof String)) {
			return steps;
		}
		// 정의 파일은 사람이 고치는 파일이라 SafeConstructor로 읽는다(임의 클래스를 만들지 않는다).
		Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load((String) text);
		if (!(root instanceof Map)) {
			return steps;
		}
		Object list = ((Map<String, Object>) root).get("steps");
		if (!(list instanceof List)) {
			return steps;
		}
		for (Object item : (List<Object>) list) {
			if (!(item instanceof Map)) {
				continue;
			}
			Map<String, Object> step = (Map<String, Object>) item;
			String target = text(step.get("agent"));
			if (target == null) {
				target = text(step.get("tool"));
			}
			steps.add(new WorkFlowOverviewResult.Step(text(step.get("id")), text(step.get("type")), target, step.get("forEach") != null));
		}
		return steps;
	}

	private static String text(Object value) {
		return value == null ? null : String.valueOf(value);
	}

}
