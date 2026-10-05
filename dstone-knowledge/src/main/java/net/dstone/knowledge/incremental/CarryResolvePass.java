package net.dstone.knowledge.incremental;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.CarryDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.job.AnalysisPass;

/**
 * <pre>
 * CARRY_RESOLVE 단계(증분 분석): 바뀌지 않은 파일의 "풀린 관계"를 옮겨도 되는지 가려서, 되는 것만 옮깁니다.
 *
 * 선언은 파일 하나만 보면 정해지지만, 호출이 누구를 가리키는지는 다른 파일에 달려 있습니다.
 * A.java가 그대로여도 A가 부르는 B.java의 메소드가 바뀌었으면 A의 호출을 다시 풀어야 합니다.
 * 그래서 이 단계는 바뀐 파일의 선언이 다 들어온 뒤(DECLARE 뒤), RESOLVE 바로 앞에서 돕니다.
 *
 * 다시 풀어야 하는 파일(옮기지 않는다):
 *   1) 앞 리비전에서 "바뀐 파일(고쳐졌거나 없어진 파일)에 선언된 것"을 가리키던 파일
 *   2) 앞 리비전에서 확실하게 풀지 못한 참조의 이름이 "새로 생겼거나 고쳐진 파일에 선언된 이름"과 같은 파일
 *      (그때는 프로젝트 밖의 것으로 봤지만 이번에는 안에서 풀릴 수 있다)
 * 나머지 파일은 앞 리비전의 관계를 그대로 옮기고 "RESOLVE 끝남"으로 표시합니다. RESOLVE는 표시가 없는 파일만 풉니다.
 *
 * 조심스러운 쪽으로 기울어 있습니다. 헷갈리면 다시 풉니다. 다시 푼다고 결과가 틀려지지는 않고 시간만 더 듭니다.
 * 이 규칙이 놓치는 경우: 분석 대상의 라이브러리(jar)가 바뀐 것은 알아채지 못합니다. 그럴 때는 전체 분석을 돌립니다.
 *
 * LINK / SEMANTIC이 만드는 관계는 옮기지 않습니다. 그 단계들은 언제나 리비전 전체를 보고 새로 만듭니다.
 * </pre>
 */
@Component
public class CarryResolvePass extends BaseObject implements AnalysisPass {

	public static final String NAME = "CARRY_RESOLVE";

	/** LINK와 SEMANTIC이 만드는 관계. RESOLVE의 결과가 아니라서 옮기지 않는다 */
	private static final List<String> NOT_FROM_RESOLVE = Arrays.asList("OVERRIDES", "CALLS_POSSIBLE_IMPLEMENTATION", "INJECTS", "EXECUTES_SQL", "READS_TABLE"
			, "WRITES_TABLE", "RENDERS", "REQUESTS", "INCLUDES");

	@Autowired
	private CarryDao carryDao;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int order() {
		// RESOURCE(250) 뒤, RESOLVE(300) 앞
		return 290;
	}

	@Override
	public void run(final AnalysisJobContext context) throws Exception {
		final long revisionId = context.getRevisionId();
		final Long baseRevisionId = CarryPass.baseRevisionOf(revisionDao, revisionId);
		if (baseRevisionId == null) {
			return;
		}
		final int[] counts = new int[3];
		txTemplateCommon.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				counts[0] = carryDao.carryResolvePasses(revisionId, baseRevisionId.longValue());
				counts[1] = carryDao.copyRows(revisionId, "analysis_relation", "RESOLVE", NOT_FROM_RESOLVE);
				counts[2] = carryDao.resetReferencesToResolve(revisionId);
			}
		});
		// 복사한 테이블의 통계를 새로 낸다(CarryDao.xml의 analyzeTable 참고).
		carryDao.analyzeTable("analysis_relation");
		carryDao.analyzeTable("analysis_reference");
		carryDao.analyzeTable("analysis_file_pass");
		info("CARRY_RESOLVE: 관계를 옮겨 온 파일 " + counts[0] + "개(관계 " + counts[1] + "건), 다시 풀 참조 " + counts[2] + "건. analysisId=" + context.getAnalysisId());
	}

}
