package net.dstone.knowledge.analysis.pass08.semantic.view;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.pass08.semantic.SemanticStep;
import net.dstone.knowledge.analysis.pass08.semantic.rule.Rule;

/**
 * <pre>
 * 규칙 파일의 view: 절로 "이 메소드가 어떤 JSP 화면을 여나"를 잇습니다(메소드 ─RENDERS→ JSP).
 *
 *   view:
 *     - call: [new ModelAndView(VIEW), _.setViewName(VIEW)]
 *       file: "{VIEW}.jsp"                      VIEW 자리의 글자로 만든 경로
 *       skip: '^(redirect|forward):'            이 정규식에 맞는 값은 화면이 아니다
 *
 * VIEW 자리는 따옴표 글자 하나여야 합니다(상수나 + 는 보지 않는다). 앞의 / 는 뗍니다.
 * 틀을 채운 경로로 끝나는 JSP 가 정확히 하나일 때만 잇습니다(뷰 리졸버의 prefix 가 무엇이든 맞도록 "끝이 같은지"로 본다).
 * 이은 관계마다 그 규칙의 자리(properties_json.ruleAt)를 남깁니다.
 * </pre>
 */
@Component
public class ViewLinker implements SemanticStep {

	@Autowired
	private SemanticDao semanticDao;

	@Override
	public String name() {
		return "view";
	}

	@Override
	public String run(AnalysisJobContext context) {
		long revisionId = context.getRevisionId();
		int renders = 0;
		List<Rule.CallArgument> rules = context.getProjectRules().viewCalls();
		for (int i = 0; i < rules.size(); i++) {
			context.checkCancelled();
			renders += semanticDao.insertViewArgumentRelations(revisionId, rules.get(i));
		}
		return "메소드가 여는 화면 " + renders + "(규칙 " + rules.size() + "개).";
	}

}
