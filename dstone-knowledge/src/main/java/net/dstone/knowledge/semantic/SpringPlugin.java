package net.dstone.knowledge.semantic;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.semantic.rule.RuleSet;
import net.dstone.knowledge.semantic.rule.SemanticRules;

/**
 * <pre>
 * 애노테이션에서 뜻을 읽어 냅니다. 어떤 애노테이션이 무슨 뜻인지는 규칙 파일(conf/rules/*.yml)에 있습니다.
 * 기본 규칙은 Spring 의 것(conf/rules/spring.yml)이고, 자체 프레임워크의 애노테이션은 규칙 파일을 더해 알려 줍니다.
 *
 *   endpoints.http         예: @RequestMapping / @GetMapping ...   → HTTP 진입점. 클래스의 주소와 메소드의 주소를 이어 붙인다.
 *   endpoints.annotations  예: @Scheduled → SCHEDULED, @KafkaListener → LISTENER 진입점
 *   injects                예: @Autowired / @Inject / @Resource    → INJECTS 관계(필드를 가진 타입 → 필드의 타입)
 *   proxies                예: @Transactional / @Async ...         → 프록시가 끼어드는 메소드 표시(proxy_related)
 *   layers.annotations     예: @Controller / @Service ...          → 계층(HIGH). 규칙 묶음 order 순서, 묶음 안에서는 적힌 순서로 먼저 맞는 것
 *
 * 애노테이션은 단순 이름으로 알아봅니다(예: Service). 전체 이름(org.springframework.stereotype.Service)은
 * import가 와일드카드면 DECLARE 단계에서 알 수 없기 때문입니다. 같은 이름의 다른 애노테이션을 쓰는 프로젝트에서는 틀릴 수 있습니다.
 *
 * 아직 다루지 않는 것:
 * - 주소를 상수로 적은 경우(@RequestMapping(Urls.ORDER)): 상수 이름이 주소로 들어간다.
 * - 상위 클래스에 붙은 @RequestMapping의 주소.
 * - 생성자 주입, setter 주입.
 * - XML로 적은 빈 설정(M5).
 * </pre>
 */
@Component
public class SpringPlugin implements SemanticPlugin {

	@Autowired
	private SemanticDao semanticDao;

	@Override
	public String name() {
		return "spring";
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public String run(AnalysisJobContext context) {
		long revisionId = context.getRevisionId();
		SemanticRules rules = context.getSemanticRules();

		int http = 0;
		List<RuleSet.HttpMappingRule> httpRules = rules.getHttpMappings();
		for (int i = 0; i < httpRules.size(); i++) {
			http += semanticDao.insertHttpEndpoints(revisionId, httpRules.get(i));
		}
		int annotated = 0;
		List<RuleSet.AnnotatedEndpointRule> annotatedRules = rules.getAnnotatedEndpoints();
		for (int i = 0; i < annotatedRules.size(); i++) {
			annotated += semanticDao.insertAnnotatedEndpoints(revisionId, annotatedRules.get(i));
		}
		int injects = semanticDao.insertInjects(revisionId, rules.getInjectAnnotations());
		int proxied = semanticDao.markProxyRelated(revisionId, rules.getProxyAnnotations());

		int layered = 0;
		List<RuleSet.LayerAnnotationRule> layerRules = rules.getLayerAnnotations();
		for (int i = 0; i < layerRules.size(); i++) {
			layered += semanticDao.updateLayerByAnnotation(revisionId, layerRules.get(i).layer, layerRules.get(i).annotations);
		}

		return "HTTP 진입점 " + http + ", 애노테이션 진입점(스케줄, 리스너 등) " + annotated + ", 주입 " + injects + ", 프록시 메소드 " + proxied + ", 계층 " + layered + ".";
	}

}
