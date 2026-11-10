package net.dstone.knowledge.analysis.pass08.semantic.layer;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;
import net.dstone.knowledge.analysis.pass08.semantic.rule.Rule;
import net.dstone.knowledge.analysis.pass08.semantic.rule.Tier;

/**
 * <pre>
 * 규칙 파일의 layer: 절로 클래스의 계층(CONTROLLER, SERVICE ...)을 정합니다.
 *
 * 한 클래스에 규칙 여럿이 맞으면 "먼저 본 것"이 이깁니다. 보는 순서는 이렇습니다.
 *
 *   0) 규칙 없이 코드가 확실히 아는 것
 *        JSP 에서 만든 타입 → VIEW, struts-config.xml 의 Action → CONTROLLER (StrutsEntries 가 먼저 정해 둔다)
 *   1) 프로젝트 규칙(conf/rules/projects/{프로젝트})
 *   2) 공통 규칙(conf/rules/common)
 *   3) 짐작 규칙(conf/rules/guess)
 *   같은 단계 안에서는 애노테이션("@Service", HIGH) → 상속(extends X, MEDIUM) → 이름(name *Dao, LOW) 순서입니다.
 *
 *   예) @Component class OrderAct extends com.acme.BaseAction
 *       프로젝트 규칙 "CONTROLLER: extends com.acme.BaseAction" 이 짐작 규칙 "COMPONENT: @Component" 보다 먼저라 CONTROLLER.
 *
 * 정해진 계층마다 어느 규칙 때문인지 남깁니다(analysis_symbol.properties_json 의 layerRuleAt, layerBy).
 * 진입점(EntryFinder)보다 먼저 돕니다. 상위 클래스의 주소 메소드를 물려받는 컨트롤러를 계층으로 알아보기 때문입니다.
 * </pre>
 */
@Component
public class LayerResolver implements SemanticStep {

	@Autowired
	private SemanticDao semanticDao;

	@Override
	public String name() {
		return "layer";
	}

	@Override
	public String run(AnalysisJobContext context) {
		long revisionId = context.getRevisionId();
		// JSP 에서 만든 타입은 언제나 화면이다. 규칙이 무엇이라 하든 바꾼다.
		int jsp = semanticDao.updateJspLayer(revisionId);

		int[] byTier = new int[Tier.values().length];
		List<Rule.Layer> rules = context.getProjectRules().layers();
		for (int i = 0; i < rules.size(); i++) {
			context.checkCancelled();
			Rule.Layer rule = rules.get(i);
			byTier[rule.tier.ordinal()] += semanticDao.updateLayerByRule(revisionId, rule);
		}
		return "JSP 화면 " + jsp + ", 프로젝트 규칙 " + byTier[Tier.PROJECT.ordinal()] + ", 공통 규칙 " + byTier[Tier.COMMON.ordinal()]
				+ ", 짐작 규칙 " + byTier[Tier.GUESS.ordinal()] + "(규칙 " + rules.size() + "개).";
	}

}
