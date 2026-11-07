package net.dstone.knowledge.semantic;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.semantic.rule.RuleSet;

/**
 * <pre>
 * 클래스 이름으로 계층을 짐작합니다. 다른 플러그인이 정하지 못한 타입만 채웁니다(그래서 맨 뒤에 돕니다).
 *
 * 애노테이션이 없던 시절의 프로젝트는 계층을 알려 주는 표시가 이름뿐입니다(OrderDAO, OrderServiceImpl, OrderVO).
 * 이름은 약속일 뿐 강제가 아니라서 틀릴 수 있습니다. 그래서 신뢰도는 LOW입니다.
 * (애노테이션으로 정한 것은 HIGH, 상속으로 정한 것은 MEDIUM입니다.)
 *
 * 규칙은 규칙 파일의 layers.names 에 있습니다(기본: conf/rules/fallback.yml).
 * 프로젝트의 이름 약속(예: 끝이 M 이면 화면 요청을 받는 클래스)은 규칙 파일을 더해 알려 줍니다.
 * 위에서부터 차례로 맞춰 보고 먼저 맞은 것으로 정합니다.
 * </pre>
 */
@Component
public class NamingLayerPlugin implements SemanticPlugin {

	@Autowired
	private SemanticDao semanticDao;

	@Override
	public String name() {
		return "naming-layer";
	}

	@Override
	public int order() {
		return 900;
	}

	@Override
	public String run(AnalysisJobContext context) {
		int layered = 0;
		List<RuleSet.LayerNameRule> rules = context.getSemanticRules().getLayerNames();
		for (int i = 0; i < rules.size(); i++) {
			layered += semanticDao.updateLayerByName(context.getRevisionId(), rules.get(i).layer, rules.get(i).pattern);
		}
		return "이름으로 계층을 짐작한 타입 " + layered + ".";
	}

}
