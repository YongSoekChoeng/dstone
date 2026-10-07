package net.dstone.knowledge.incremental;

import java.util.Arrays;
import java.util.Map;

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
 * CARRY 단계(증분 분석): 바뀌지 않은 파일의 분석 결과를 앞 리비전에서 옮겨 옵니다.
 *
 * 리비전은 언제나 "그 시점의 전체 모습"을 담습니다. 증분 분석은 그 전체를 만드는 길을 줄이는 것입니다.
 * 파일을 다시 파싱하는 대신, 내용이 같은 파일의 결과를 앞 리비전(기준 리비전)에서 그대로 복사합니다.
 *
 *   SCAN ─▶ CARRY ─▶ DECLARE / RESOURCE ─▶ CARRY_RESOLVE ─▶ RESOLVE ─▶ LINK ─▶ SEMANTIC ─▶ DOCUMENT
 *           같은 파일의 선언과     바뀐 파일만       풀린 관계를 옮겨도       남은 파일만     ───── 언제나 전체를 다시 ─────
 *           자원을 옮긴다          처리한다          되는 파일을 골라 옮긴다   푼다
 *
 * 여기서 옮기는 것은 "파일 하나만 보면 정해지는 결과"입니다.
 *   - DECLARE의 결과: 타입, 메소드, 필드, 애노테이션, 참조
 *   - RESOURCE의 결과: SQL statement, 설정 값, 그 밖의 자원
 * 옮긴 파일은 그 단계가 "끝남"으로 표시되므로 DECLARE와 RESOURCE가 건너뜁니다.
 * 다른 파일에 따라 달라지는 결과(풀린 관계)는 바뀐 파일의 선언이 들어온 뒤에 CARRY_RESOLVE가 따로 판단합니다.
 *
 * 기준 리비전이 없으면(전체 분석) 아무것도 하지 않습니다.
 * 한 트랜잭션으로 하므로, 도중에 죽으면 아무것도 옮겨지지 않은 상태로 돌아가고 다시 시작하면 처음부터 합니다.
 * </pre>
 */
@Component
public class CarryPass extends BaseObject implements AnalysisPass {

	public static final String NAME = "CARRY";

	/** DECLARE가 파일마다 쓰는 테이블 */
	private static final String[] DECLARE_TABLES = { "analysis_symbol", "analysis_method", "analysis_field", "analysis_annotation", "analysis_reference" };

	/** RESOURCE가 파일마다 쓰는 테이블 */
	private static final String[] RESOURCE_TABLES = { "analysis_mapper", "analysis_config", "analysis_resource" };

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
		// SCAN(100) 뒤, DECLARE(200) 앞
		return 150;
	}

	@Override
	public void run(final AnalysisJobContext context) throws Exception {
		final long revisionId = context.getRevisionId();
		final Long baseRevisionId = baseRevisionOf(revisionDao, revisionId);
		if (baseRevisionId == null) {
			return;
		}
		final int[] counts = new int[3];
		txTemplateCommon.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				counts[0] = carryDao.markCarriedFiles(revisionId, baseRevisionId.longValue());
				counts[1] = carryDao.carryFilePasses(revisionId, Arrays.asList("DECLARE", "RESOURCE"));
				for (int i = 0; i < DECLARE_TABLES.length; i++) {
					counts[2] += carryDao.copyRows(revisionId, DECLARE_TABLES[i], "DECLARE", null);
				}
				carryDao.carryFileParsed(revisionId);
				for (int i = 0; i < RESOURCE_TABLES.length; i++) {
					counts[2] += carryDao.copyRows(revisionId, RESOURCE_TABLES[i], "RESOURCE", null);
				}
			}
		});
		// 복사한 테이블의 통계를 새로 낸다(CarryDao.xml의 analyzeTable 참고).
		carryDao.analyzeTable("analysis_file");
		carryDao.analyzeTable("analysis_file_pass");
		for (int i = 0; i < DECLARE_TABLES.length; i++) {
			carryDao.analyzeTable(DECLARE_TABLES[i]);
		}
		for (int i = 0; i < RESOURCE_TABLES.length; i++) {
			carryDao.analyzeTable(RESOURCE_TABLES[i]);
		}
		info("CARRY: 기준 리비전 " + baseRevisionId + "과 같은 파일 " + counts[0] + "개, 옮겨 온 단계 " + counts[1] + "건, 옮긴 행 " + counts[2] + "건. analysisId="
				+ context.getAnalysisId());
	}

	/**
	 * <pre>
	 * 이 리비전의 기준 리비전을 돌려줍니다. 전체 분석이거나 기준 리비전이 그사이 지워졌으면 null입니다.
	 * (지워졌으면 옮겨 올 것이 없으므로 전체 분석과 똑같이 돕니다.)
	 * </pre>
	 */
	static Long baseRevisionOf(RevisionDao revisionDao, long revisionId) {
		Map<String, Object> revision = revisionDao.selectRevision(revisionId);
		Object parent = revision == null ? null : revision.get("parentRevisionId");
		if (parent == null) {
			return null;
		}
		long baseRevisionId = ((Number) parent).longValue();
		return revisionDao.selectRevision(baseRevisionId) == null ? null : Long.valueOf(baseRevisionId);
	}

}
