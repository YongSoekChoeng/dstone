package net.dstone.knowledge.semantic;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.job.AnalysisJobContext;

/**
 * <pre>
 * 설정에 적은 "상위 타입 → 계층" 규칙으로 계층을 정합니다.
 *
 * 애노테이션도 없고 이름 끝말도 흔한 모양(...Service, ...DAO)이 아닌 프로젝트가 있습니다.
 * 자체 프레임워크를 쓰는 프로젝트는 대개 "이 클래스를 상속하면 서비스, 저것을 상속하면 DAO"라는 약속이 있습니다.
 * 그 약속을 설정(dstone.knowledge.semantic.layer.super-types)에 적어 두면 여기서 계층을 붙입니다.
 *
 *   모양: "상위 타입의 전체 이름=계층" 을 쉼표로 이어 쓴다.
 *   예:   jef.application.services.AbstractSubService=SERVICE, jef.application.dao.jdbc.JdbcDAO=REPOSITORY
 *
 * 바로 위의 상위 타입(extends / implements에 적힌 것)만 봅니다. 상속을 여러 단계 거슬러 올라가지는 않습니다.
 * 상위 타입은 jar가 없어도 됩니다(import로 전체 이름을 알아낸 것이면 된다).
 * 코드에 적힌 사실(상속)을 프로젝트의 약속으로 읽는 것이라 신뢰도는 MEDIUM입니다. 앞의 플러그인이 이미 정한 계층은 바꾸지 않습니다.
 * 그 상위 타입을 쓰지 않는 프로젝트에서는 아무 일도 하지 않으므로, 여러 프로젝트의 규칙을 함께 적어 두어도 됩니다.
 * </pre>
 */
@Component
public class ConfiguredLayerPlugin implements SemanticPlugin {

	/** 계층으로 쓸 수 있는 이름들. 설정에 다른 글자가 적혀 있으면 그 규칙은 버립니다(오타로 엉뚱한 계층이 생기지 않게). */
	private static final List<String> LAYERS = Arrays.asList(
			"CONTROLLER", "SERVICE", "REPOSITORY", "MODEL", "VIEW", "CONFIG", "WEB_FILTER", "UTIL", "EXCEPTION", "BATCH");

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private ConfigProperty configProperty;

	@Override
	public String name() {
		return "configured-layer";
	}

	@Override
	public int order() {
		// 애노테이션, 서블릿, Struts, 화면이 정한 계층이 먼저다. 이름으로 짐작하는 것(900)보다는 앞이다.
		return 800;
	}

	@Override
	public String run(AnalysisJobContext context) {
		String configured = configProperty.getProperty("dstone.knowledge.semantic.layer.super-types");
		if (configured == null || configured.trim().length() == 0) {
			return "규칙 없음.";
		}
		int layered = 0, ignored = 0;
		String[] items = configured.split(",");
		for (int i = 0; i < items.length; i++) {
			String item = items[i].trim();
			if (item.length() == 0) {
				continue;
			}
			int eq = item.indexOf('=');
			String superType = eq <= 0 ? "" : item.substring(0, eq).trim();
			String layer = eq <= 0 ? "" : item.substring(eq + 1).trim().toUpperCase(java.util.Locale.ROOT);
			if (superType.length() == 0 || !LAYERS.contains(layer)) {
				ignored++;
				continue;
			}
			layered += semanticDao.updateLayerByConfiguredSuperType(context.getRevisionId(), layer, superType);
		}
		return "상위 타입으로 계층을 정한 타입 " + layered + (ignored > 0 ? "(잘못 적힌 규칙 " + ignored + "개는 버림)" : "") + ".";
	}

}
