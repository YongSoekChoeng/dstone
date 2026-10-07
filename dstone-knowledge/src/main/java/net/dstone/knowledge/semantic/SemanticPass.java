package net.dstone.knowledge.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.job.AnalysisPass;

/**
 * <pre>
 * SEMANTIC 단계: 등록된 의미 분석 플러그인을 순서대로 돌려서 진입점과 계층을 알아냅니다.
 *
 * 만드는 것:
 *   진입점(analysis_endpoint)   밖에서 이 프로그램으로 들어오는 입구. HTTP 주소, 서블릿, main(), 스케줄, 리스너, 스레드, JSP
 *   계층(analysis_symbol.layer) CONTROLLER / SERVICE / REPOSITORY / MODEL ...
 *   주입(INJECTS 관계)          @Autowired 필드
 *
 * Java 파일을 다시 파싱하지 않습니다. DECLARE가 넣어 둔 애노테이션과 RESOLVE가 만든 관계를 SQL로 엮습니다.
 * 파일을 읽는 것은 web.xml뿐입니다. 그래서 큰 프로젝트에서도 금방 끝나고 메모리를 쓰지 않습니다.
 *
 * 전체를 한 트랜잭션으로 돌립니다. 시작할 때 전에 만든 것을 지우고 새로 만들기 때문에, 다시 돌려도 결과가 같고
 * 도중에 실패하면 지운 것까지 되돌아갑니다.
 * </pre>
 */
@Component
public class SemanticPass extends BaseObject implements AnalysisPass {

	public static final String NAME = "SEMANTIC";

	/** 등록된 모든 플러그인. Spring이 SemanticPlugin 구현 빈을 모아서 넣어 줍니다. */
	@Autowired
	private List<SemanticPlugin> plugins;

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int order() {
		return 500;
	}

	@Override
	public void run(final AnalysisJobContext context) throws Exception {
		final List<SemanticPlugin> ordered = new ArrayList<SemanticPlugin>(plugins);
		Collections.sort(ordered, new Comparator<SemanticPlugin>() {
			@Override
			public int compare(SemanticPlugin a, SemanticPlugin b) {
				return Integer.compare(a.order(), b.order());
			}
		});

		txTemplateCommon.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				semanticDao.clear(context.getRevisionId());
				for (int i = 0; i < ordered.size(); i++) {
					SemanticPlugin plugin = ordered.get(i);
					context.checkCancelled();
					try {
						String summary = plugin.run(context);
						info("SEMANTIC[" + plugin.name() + "]: " + summary + " analysisId=" + context.getAnalysisId());
					} catch (RuntimeException e) {
						throw e;
					} catch (Exception e) {
						// 트랜잭션을 취소시키려면 RuntimeException이어야 한다.
						throw new IllegalStateException("의미 분석 플러그인 " + plugin.name() + " 실패: " + ErrorText.summaryOf(e), e);
					}
				}
			}
		});
	}

}
