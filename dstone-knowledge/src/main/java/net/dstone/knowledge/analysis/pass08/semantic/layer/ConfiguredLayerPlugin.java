package net.dstone.knowledge.analysis.pass08.semantic.layer;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.pass08.semantic.rule.RuleSet;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticPlugin;

/**
 * <pre>
 * 규칙 파일에 적은 "상위 타입 → 계층" 규칙(layers.superTypes)으로 계층을 정합니다.
 *
 * 애노테이션도 없고 이름 끝말도 흔한 모양(...Service, ...DAO)이 아닌 프로젝트가 있습니다.
 * 자체 프레임워크를 쓰는 프로젝트는 대개 "이 클래스를 상속하면 서비스, 저것을 상속하면 DAO"라는 약속이 있습니다.
 * 그 약속을 규칙 파일에 적어 두면 여기서 계층을 붙입니다.
 *
 *   예(conf/rules/hi-japan/backend.yml):
 *     layers:
 *       superTypes:
 *         - { layer: SERVICE,    types: [jef.application.services.AbstractSubService] }
 *         - { layer: REPOSITORY, types: [jef.application.dao.jdbc.JdbcDAO] }
 *   서블릿(HttpServlet → CONTROLLER, Filter → WEB_FILTER)도 같은 규칙입니다(conf/rules/servlet.yml).
 *
 * 바로 위의 상위 타입(extends / implements에 적힌 것)만 봅니다. 상속을 여러 단계 거슬러 올라가지는 않습니다.
 * 상위 타입은 jar가 없어도 됩니다(import로 전체 이름을 알아낸 것이면 된다).
 * 코드에 적힌 사실(상속)을 프로젝트의 약속으로 읽는 것이라 신뢰도는 MEDIUM입니다. 앞의 플러그인이 이미 정한 계층은 바꾸지 않습니다.
 * 그 상위 타입을 쓰지 않는 프로젝트에서는 아무 일도 하지 않습니다.
 * </pre>
 */
@Component
public class ConfiguredLayerPlugin implements SemanticPlugin {

	@Autowired
	private SemanticDao semanticDao;

	@Override
	public String name() {
		return "configured-layer";
	}

	@Override
	public int order() {
		// 애노테이션, Struts, 화면이 정한 계층이 먼저다. 이름으로 짐작하는 것(900)보다는 앞이다.
		return 800;
	}

	@Override
	public String run(AnalysisJobContext context) {
		List<RuleSet.LayerSuperTypeRule> rules = context.getSemanticRules().getLayerSuperTypes();
		if (rules.isEmpty()) {
			return "규칙 없음.";
		}
		int layered = 0;
		for (int i = 0; i < rules.size(); i++) {
			RuleSet.LayerSuperTypeRule rule = rules.get(i);
			for (int t = 0; t < rule.types.size(); t++) {
				layered += semanticDao.updateLayerByConfiguredSuperType(context.getRevisionId(), rule.layer, rule.types.get(t));
			}
		}
		return "상위 타입으로 계층을 정한 타입 " + layered + ".";
	}

}
