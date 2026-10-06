package net.dstone.knowledge.resolver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.RelationDao;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.job.AnalysisPass;

/**
 * <pre>
 * LINK 단계: 이미 풀린 관계를 SQL로 엮어서, 파일 하나만 봐서는 알 수 없는 관계를 만듭니다.
 *
 *   OVERRIDES                      하위 타입의 메소드 → 상위 타입의 같은 메소드
 *   CALLS_POSSIBLE_IMPLEMENTATION  몸통 없는 메소드(인터페이스/abstract)를 부르는 호출 → 실제로 실행될 수 있는 구현 메소드
 *
 * 예: OrderServlet이 orderService.findOrders()를 부르면 RESOLVE는 "OrderService.findOrders를 부른다"까지만 압니다.
 * 여기서 OrderServiceImpl.findOrders가 그것을 구현한다는 것을 엮어, OrderServlet → OrderServiceImpl.findOrders를 "가능한 구현"으로 잇습니다.
 *
 * 파일을 읽지 않고 DB 안에서만 계산하므로 메모리를 쓰지 않습니다.
 * 다시 돌려도 결과가 같습니다(이 단계가 만든 관계를 지우고 새로 만듭니다).
 * 마지막에 품질 지표(호출이 얼마나 풀렸는지 등)도 계산해 둡니다.
 * </pre>
 */
@Component
public class LinkPass extends BaseObject implements AnalysisPass {

	public static final String NAME = "LINK";

	@Autowired
	private RelationDao relationDao;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int order() {
		return 400;
	}

	@Override
	public void run(final AnalysisJobContext context) throws Exception {
		final long revisionId = context.getRevisionId();
		final int[] counts = new int[2];
		txTemplateCommon.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				relationDao.deleteLinkRelations(revisionId);
				// 순서가 중요하다. "가능한 구현"은 OVERRIDES를 보고 만든다.
				counts[0] = relationDao.insertOverrides(revisionId);
				counts[1] = relationDao.insertPossibleImplementations(revisionId);
				relationDao.replaceMetrics(context.getAnalysisId(), revisionId);
			}
		});
		info("LINK: OVERRIDES " + counts[0] + "건, CALLS_POSSIBLE_IMPLEMENTATION " + counts[1] + "건. analysisId=" + context.getAnalysisId());
	}

}
