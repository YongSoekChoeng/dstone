package net.dstone.knowledge.semantic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.job.AnalysisJobContext;

/**
 * <pre>
 * 프레임워크 없이도 있는 진입점을 알아냅니다. 어떤 Java 프로그램에나 해당합니다.
 *
 *   public static void main(String[])            → MAIN 진입점
 *   Runnable / Thread / TimerTask 의 run()       → THREAD 진입점
 *   Callable 의 call()                           → THREAD 진입점
 *   주소로 바로 열 수 있는 JSP(WEB-INF 밖)       → JSP 진입점
 *
 * 스레드 진입점은 "누가 직접 부르지 않는데도 실행되는 메소드"라서 알아 둘 필요가 있습니다.
 * 호출 그래프만 보면 run()을 부르는 곳이 없어 죽은 코드처럼 보이기 때문입니다.
 *
 * 아직 다루지 않는 것:
 * - 람다로 적은 Runnable(new Thread(() -> ...)): 람다는 메소드로 저장하지 않으므로 잡히지 않는다.
 * - Runnable을 프로젝트 안의 다른 타입을 거쳐 구현한 경우(class A implements MyTask, interface MyTask extends Runnable).
 * - JSP 안에서 부르는 Java(M5).
 * </pre>
 */
@Component
public class PlainJavaPlugin implements SemanticPlugin {

	@Autowired
	private SemanticDao semanticDao;

	@Override
	public String name() {
		return "plain-java";
	}

	@Override
	public int order() {
		return 300;
	}

	@Override
	public String run(AnalysisJobContext context) {
		long revisionId = context.getRevisionId();
		int mains = semanticDao.insertMainEndpoints(revisionId);
		int threads = semanticDao.insertThreadEndpoints(revisionId);
		int jsps = semanticDao.insertJspEndpoints(revisionId);
		return "main " + mains + ", 스레드 " + threads + ", JSP " + jsps + ".";
	}

}
