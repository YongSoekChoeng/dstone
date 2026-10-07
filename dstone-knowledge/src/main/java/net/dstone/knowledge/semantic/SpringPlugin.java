package net.dstone.knowledge.semantic;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.job.AnalysisJobContext;

/**
 * <pre>
 * Spring 애노테이션에서 뜻을 읽어 냅니다.
 *
 *   @RequestMapping / @GetMapping ...   → HTTP 진입점. 클래스의 주소와 메소드의 주소를 이어 붙인다.
 *   @Scheduled                          → SCHEDULED 진입점
 *   @KafkaListener / @EventListener ... → LISTENER 진입점
 *   @Autowired / @Inject / @Resource    → INJECTS 관계(필드를 가진 타입 → 필드의 타입)
 *   @Transactional / @Async ...         → 프록시가 끼어드는 메소드 표시(proxy_related)
 *   @Controller / @Service ...          → 계층(HIGH)
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
		int http = semanticDao.insertSpringHttpEndpoints(revisionId);
		int scheduled = semanticDao.insertScheduledEndpoints(revisionId);
		int listeners = semanticDao.insertListenerEndpoints(revisionId);
		int injects = semanticDao.insertInjects(revisionId);
		int proxied = semanticDao.markProxyRelated(revisionId);

		int layered = 0;
		layered += semanticDao.updateLayerByAnnotation(revisionId, "CONTROLLER", Arrays.asList("Controller", "RestController", "ControllerAdvice", "RestControllerAdvice"));
		layered += semanticDao.updateLayerByAnnotation(revisionId, "SERVICE", Arrays.asList("Service"));
		layered += semanticDao.updateLayerByAnnotation(revisionId, "REPOSITORY", Arrays.asList("Repository", "Mapper"));
		layered += semanticDao.updateLayerByAnnotation(revisionId, "CONFIG", Arrays.asList("Configuration", "SpringBootApplication", "ConfigurationProperties"));
		layered += semanticDao.updateLayerByAnnotation(revisionId, "MODEL", Arrays.asList("Entity", "Embeddable", "MappedSuperclass", "Document"));
		layered += semanticDao.updateLayerByAnnotation(revisionId, "ASPECT", Arrays.asList("Aspect"));
		// @Component는 뜻이 넓어서 맨 뒤에 둔다. 위의 것에 해당하지 않을 때만 COMPONENT가 된다.
		layered += semanticDao.updateLayerByAnnotation(revisionId, "COMPONENT", Arrays.asList("Component"));

		return "HTTP 진입점 " + http + ", 스케줄 " + scheduled + ", 리스너 " + listeners + ", 주입 " + injects + ", 프록시 메소드 " + proxied + ", 계층 " + layered + ".";
	}

}
