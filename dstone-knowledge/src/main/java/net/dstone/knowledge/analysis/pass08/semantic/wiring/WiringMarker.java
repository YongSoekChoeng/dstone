package net.dstone.knowledge.analysis.pass08.semantic.wiring;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;
import net.dstone.knowledge.analysis.pass08.semantic.rule.ProjectRules;
import net.dstone.knowledge.analysis.pass08.semantic.rule.Rule;

/**
 * <pre>
 * 규칙 파일의 inject: 와 proxy: 절로 "객체끼리 어떻게 엮이나"를 표시합니다.
 *
 *   inject: ["@Autowired"]       이 애노테이션이 붙은 필드 → INJECTS 관계(필드를 가진 타입 → 필드의 타입). ruleAt 을 남긴다
 *   proxy: ["@Transactional"]    이 애노테이션이 메소드나 그 클래스에 붙으면 프록시가 끼어드는 메소드(proxy_related)
 *
 * 애노테이션은 단순 이름으로 알아봅니다. 아직 생성자 주입, setter 주입은 다루지 않습니다.
 * </pre>
 */
@Component
public class WiringMarker implements SemanticStep {

	@Autowired
	private SemanticDao semanticDao;

	@Override
	public String name() {
		return "wiring";
	}

	@Override
	public String run(AnalysisJobContext context) {
		long revisionId = context.getRevisionId();
		ProjectRules rules = context.getProjectRules();
		int injects = semanticDao.insertInjects(revisionId, rules.injects());
		List<Rule.Mark> proxies = rules.proxies();
		List<String> annotations = new ArrayList<String>();
		for (int i = 0; i < proxies.size(); i++) {
			annotations.add(proxies.get(i).annotation);
		}
		int proxied = semanticDao.markProxyRelated(revisionId, annotations);
		return "주입 " + injects + ", 프록시 메소드 " + proxied + ".";
	}

}
