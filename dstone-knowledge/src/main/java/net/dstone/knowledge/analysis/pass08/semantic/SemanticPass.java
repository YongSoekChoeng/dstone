package net.dstone.knowledge.analysis.pass08.semantic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.analysis.job.AnalysisJobContext;
import net.dstone.knowledge.analysis.job.AnalysisPass;
import net.dstone.knowledge.analysis.pass08.semantic.dictionary.MethodTitlePlugin;
import net.dstone.knowledge.analysis.pass08.semantic.dictionary.ScreenDictionaryPlugin;
import net.dstone.knowledge.analysis.pass08.semantic.dictionary.SourceNamePlugin;
import net.dstone.knowledge.analysis.pass08.semantic.dictionary.TableDictionaryPlugin;
import net.dstone.knowledge.analysis.pass08.semantic.entry.EntryFinder;
import net.dstone.knowledge.analysis.pass08.semantic.entry.ServletEntries;
import net.dstone.knowledge.analysis.pass08.semantic.entry.StrutsEntries;
import net.dstone.knowledge.analysis.pass08.semantic.layer.LayerResolver;
import net.dstone.knowledge.analysis.pass08.semantic.rule.ProjectRules;
import net.dstone.knowledge.analysis.pass08.semantic.rule.RuleException;
import net.dstone.knowledge.analysis.pass08.semantic.rule.RuleFile;
import net.dstone.knowledge.analysis.pass08.semantic.rule.RuleFiles;
import net.dstone.knowledge.analysis.pass08.semantic.screen.JspLinker;
import net.dstone.knowledge.analysis.pass08.semantic.screen.ScreenLinker;
import net.dstone.knowledge.analysis.pass08.semantic.screen.ScreenMenuReader;
import net.dstone.knowledge.analysis.pass08.semantic.sql.JavaSqlLinker;
import net.dstone.knowledge.analysis.pass08.semantic.sql.JpaQueryReader;
import net.dstone.knowledge.analysis.pass08.semantic.sql.MapperLinker;
import net.dstone.knowledge.analysis.pass08.semantic.sql.SqlLinker;
import net.dstone.knowledge.analysis.pass08.semantic.view.ViewLinker;
import net.dstone.knowledge.analysis.pass08.semantic.wiring.WiringMarker;

/**
 * <pre>
 * SEMANTIC 단계: 처리기를 정해진 순서로 돌려서 진입점, 계층, SQL · 화면과의 관계를 알아냅니다.
 *
 * 프레임워크마다 다른 관례(어떤 애노테이션이 진입점인지 등)는 규칙 파일(conf/rules)에 있습니다.
 * 규칙 파일의 절마다 처리기가 하나씩 있습니다.
 *
 *   규칙 파일의 절     처리기
 *   layer:            LayerResolver
 *   entry:            EntryFinder
 *   inject: proxy:    WiringMarker
 *   jpa:              JpaQueryReader, JavaSqlLinker
 *   sql:              SqlLinker
 *   view:             ViewLinker
 *   screen:           ScreenLinker, ScreenMenuReader
 *
 * Java 파일을 다시 파싱하지 않습니다. DECLARE 가 넣어 둔 애노테이션과 RESOLVE 가 만든 관계를 SQL 로 엮습니다.
 *
 * 전체를 한 트랜잭션으로 돌립니다. 시작할 때 전에 만든 것을 지우고 새로 만들기 때문에, 다시 돌려도 결과가 같고
 * 도중에 실패하면 지운 것까지 되돌아갑니다. 시작할 때 규칙 폴더를 다시 읽고(RuleFiles), 어떤 파일을 썼는지를
 * analysis_resource 에 RULE_SET 행으로 남깁니다. 규칙 파일을 고친 뒤에는 rerunFrom=SEMANTIC 으로 다시 돌리면 됩니다.
 * </pre>
 */
@Component
public class SemanticPass extends BaseObject implements AnalysisPass {

	public static final String NAME = "SEMANTIC";

	@Autowired private ServletEntries servletEntries;
	@Autowired private StrutsEntries strutsEntries;
	@Autowired private LayerResolver layerResolver;
	@Autowired private EntryFinder entryFinder;
	@Autowired private WiringMarker wiringMarker;
	@Autowired private JpaQueryReader jpaQueryReader;
	@Autowired private MapperLinker mapperLinker;
	@Autowired private SqlLinker sqlLinker;
	@Autowired private JavaSqlLinker javaSqlLinker;
	@Autowired private ViewLinker viewLinker;
	@Autowired private JspLinker jspLinker;
	@Autowired private ScreenLinker screenLinker;
	@Autowired private ScreenMenuReader screenMenuReader;
	@Autowired private SourceNamePlugin sourceNamePlugin;
	@Autowired private MethodTitlePlugin methodTitlePlugin;
	@Autowired private ScreenDictionaryPlugin screenDictionaryPlugin;
	@Autowired private TableDictionaryPlugin tableDictionaryPlugin;

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private RuleFiles ruleFiles;

	@Autowired
	@Qualifier("txTemplateCommon")
	private TransactionTemplate txTemplateCommon;

	/**
	 * <pre>
	 * 처리기를 도는 순서. 순서가 결과를 바꾸는 곳이 있어서 까닭을 함께 적습니다.
	 * </pre>
	 */
	private SemanticStep[] steps() {
		return new SemanticStep[] {
			// 1) 규칙 없이 표준 설정 파일에서 읽는 입구. struts-config 의 Action 은 여기서 CONTROLLER 가 된다(규칙보다 확실하다).
			servletEntries, strutsEntries,
			// 2) 계층. 진입점보다 먼저다: 상위 클래스의 주소를 물려받는 컨트롤러를 계층으로 알아본다.
			layerResolver,
			// 3) 입구와 주입 · 프록시.
			entryFinder, wiringMarker,
			// 4) SQL. JPA statement 를 먼저 만들고, MapperLinker 가 SQL 관계를 지운 뒤 다시 만든다.
			//    SqlLinker 가 이름 있는 JPA 쿼리(createNamedQuery)까지 잇고, JavaSqlLinker 가 Java 문자열 SQL · JPA 를 테이블과 잇는다.
			jpaQueryReader, mapperLinker, sqlLinker, javaSqlLinker,
			// 5) 화면. 진입점이 다 만들어진 뒤라야 화면이 요청하는 주소를 진입점과 맞춰 볼 수 있다.
			viewLinker, jspLinker, screenLinker, screenMenuReader,
			// 6) 이름 사전. SQL 에서 쓰인 컬럼과 화면 제목이 다 모인 뒤에 돈다.
			sourceNamePlugin, methodTitlePlugin, screenDictionaryPlugin, tableDictionaryPlugin
		};
	}

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
		ProjectRules rules;
		try {
			rules = ruleFiles.rulesFor(context.getProjectId(), context.getProjectValue("ruleSets"), null);
		} catch (RuleException e) {
			throw new IllegalStateException("의미 분석 규칙을 읽지 못했습니다. " + e.getMessage(), e);
		}
		context.setProjectRules(rules);

		txTemplateCommon.execute(new TransactionCallbackWithoutResult() {
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				runSteps(context);
			}
		});
	}

	/**
	 * <pre>
	 * 이미 열린 트랜잭션 안에서, 전에 만든 결과를 지우고 처리기를 모두 돌립니다.
	 * 규칙은 context.getProjectRules() 에 미리 실어 두어야 합니다.
	 * 규칙 시험 적용(RuleService.trial)이 트랜잭션을 열고 이것을 부른 뒤 되돌립니다.
	 * </pre>
	 */
	public void runSteps(AnalysisJobContext context) {
		semanticDao.clear(context.getRevisionId());
		recordRuleFiles(context);
		SemanticStep[] steps = steps();
		for (int i = 0; i < steps.length; i++) {
			SemanticStep step = steps[i];
			context.checkCancelled();
			try {
				String summary = step.run(context);
				info("SEMANTIC[" + step.name() + "]: " + summary + " analysisId=" + context.getAnalysisId());
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				// 트랜잭션을 취소시키려면 RuntimeException 이어야 한다.
				throw new IllegalStateException("의미 분석 처리기 " + step.name() + " 실패: " + ErrorText.summaryOf(e), e);
			}
		}
	}

	/** 어떤 규칙 파일로 분석했는지 남깁니다. 규칙 파일이 바뀌었는지 나중에 해시로 견줘 볼 수 있습니다. */
	private void recordRuleFiles(AnalysisJobContext context) {
		List<RuleFile> files = context.getProjectRules().getFiles();
		for (int i = 0; i < files.size(); i++) {
			RuleFile file = files.get(i);
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("tier", file.tier.name());
			properties.put("rules", Integer.valueOf(file.ruleCount()));
			if (file.description != null) {
				properties.put("description", file.description);
			}
			semanticDao.insertSemanticResource(context.getRevisionId(), "RULE_SET", null, file.name, file.path, file.getHash(), properties, null);
		}
	}

}
