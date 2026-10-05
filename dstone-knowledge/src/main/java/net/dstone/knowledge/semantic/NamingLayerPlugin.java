package net.dstone.knowledge.semantic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.job.AnalysisJobContext;

/**
 * <pre>
 * 클래스 이름의 끝말로 계층을 짐작합니다. 다른 플러그인이 정하지 못한 타입만 채웁니다(그래서 맨 뒤에 돕니다).
 *
 * 애노테이션이 없던 시절의 프로젝트는 계층을 알려 주는 표시가 이름뿐입니다(OrderDAO, OrderServiceImpl, OrderVO).
 * 이름은 약속일 뿐 강제가 아니라서 틀릴 수 있습니다. 그래서 신뢰도는 LOW입니다.
 * (애노테이션으로 정한 것은 HIGH, 상속으로 정한 것은 MEDIUM입니다.)
 *
 * 규칙은 위에서부터 차례로 맞춰 보고 먼저 맞은 것으로 정합니다.
 * </pre>
 */
@Component
public class NamingLayerPlugin implements SemanticPlugin {

	/** [계층, 단순 이름에 맞춰 볼 정규식] */
	private static final String[][] RULES = {
		{ "CONTROLLER", "(Controller|Action|Servlet)$" },
		{ "SERVICE", "(Service|ServiceImpl|Manager|ManagerImpl|Facade|FacadeImpl|Biz|BizImpl)$" },
		{ "REPOSITORY", "(DAO|Dao|DAOImpl|DaoImpl|Repository|RepositoryImpl|Mapper)$" },
		{ "MODEL", "(VO|Vo|DTO|Dto|Entity|Bean|Model|Form)$" },
		{ "CONFIG", "(Config|Configuration)$" },
		{ "WEB_FILTER", "(Filter|Interceptor)$" },
		{ "UTIL", "(Util|Utils|Utility|Helper)$" },
		{ "EXCEPTION", "(Exception|Error)$" },
		// 배치는 끝말이 제각각이라(OrderBatchMain, DailyJob) 이름 어디에든 Batch가 있으면 본다.
		{ "BATCH", "(Batch|Job$|Scheduler$|Tasklet$)" }
	};

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
		for (int i = 0; i < RULES.length; i++) {
			layered += semanticDao.updateLayerByName(context.getRevisionId(), RULES[i][0], RULES[i][1]);
		}
		return "이름으로 계층을 짐작한 타입 " + layered + ".";
	}

}
