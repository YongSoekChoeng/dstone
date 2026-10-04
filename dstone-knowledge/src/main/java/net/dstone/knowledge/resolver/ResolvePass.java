package net.dstone.knowledge.resolver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.cache.NoCache;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.RelationDao;
import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.job.AnalysisPass;
import net.dstone.knowledge.job.FileHandler;
import net.dstone.knowledge.job.FilePassRunner;
import net.dstone.knowledge.job.FileResult;
import net.dstone.knowledge.parser.JavaSourceParser;
import net.dstone.knowledge.scanner.EncodingDetector;
import net.dstone.knowledge.symbol.ClasspathResolver;
import net.dstone.knowledge.symbol.DbTypeSolver;

/**
 * <pre>
 * RESOLVE 단계: DECLARE가 이름만 적어 둔 참조가 누구를 가리키는지 풀어서 관계(analysis_relation)로 만듭니다.
 *
 * 파일 하나마다 하는 일:
 *   1) 파일을 다시 파싱한다(타입 해석기의 캐시에 있으면 그것을 쓴다).
 *   2) 이 파일의 참조를 DB에서 읽고, AST에서 같은 위치의 노드를 찾아 푼다(ReferenceResolver).
 *   3) 풀린 관계를 저장하고 참조의 상태를 바꾼다.
 *
 * 다른 타입의 선언이 필요하면 타입 해석기가 DB 색인으로 찾아 그 파일만 파싱합니다(DbTypeSolver).
 * 그래서 이 단계도 메모리에 올라가는 것은 지금 파일과, 정해진 개수의 다른 파일뿐입니다.
 *
 * 대상 프로젝트를 빌드하지 않습니다. 라이브러리 jar는 있으면 쓰고(ClasspathResolver), 없으면 없는 대로 갑니다.
 * jar가 없으면 프로젝트 밖의 타입을 몰라서 "짐작"(LOW/MEDIUM)으로 남는 관계가 늘어납니다.
 * </pre>
 */
@Component
public class ResolvePass extends BaseObject implements AnalysisPass {

	public static final String NAME = "RESOLVE";

	@Autowired
	private FilePassRunner filePassRunner;

	@Autowired
	private ClasspathResolver classpathResolver;

	@Autowired
	private JavaSourceParser javaSourceParser;

	@Autowired
	private EncodingDetector encodingDetector;

	@Autowired
	private SymbolDao symbolDao;

	@Autowired
	private RelationDao relationDao;

	@Autowired
	private ConfigProperty configProperty;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int order() {
		return 300;
	}

	@Override
	public void run(final AnalysisJobContext context) throws Exception {
		final Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		final int cacheSize = intProperty("dstone.knowledge.resolve.unit-cache-size", 100);
		final long fileTimeoutMillis = intProperty("dstone.knowledge.resolve.file-timeout-seconds", 120) * 1000L;
		final List<Path> jars = classpathResolver.findJars(root, context.getProjectValue("classpath"));
		// 파일 하나에 참조가 수천 개일 수 있다. 참조 하나를 풀 때마다 취소 요청을 확인해서, 큰 파일 도중에도 취소가 먹게 한다.
		final ReferenceResolver.Checkpoint checkpoint = new ReferenceResolver.Checkpoint() {
			@Override
			public void check() {
				context.checkCancelled();
			}
		};

		// 해석 도구 한 벌. 파일 처리가 끝나지 않아 작업 스레드를 버리면 통째로 새로 만든다(그래서 배열에 담아 바꿔 끼운다).
		final Engine[] engine = new Engine[] { newEngine(context, root, jars, cacheSize) };
		info("RESOLVE: jar " + engine[0].jarCount + "개를 클래스패스로 씁니다. analysisId=" + context.getAnalysisId());

		try {
			filePassRunner.run(context, NAME, "JAVA", new FileHandler<Prepared>() {
				@Override
				public Prepared prepare(Map<String, Object> file) throws Exception {
					try {
						return resolveFile(engine[0], file, fileTimeoutMillis, checkpoint);
					} finally {
						// JavaParserFacade도 풀어 본 노드를 쌓아 둔다. 파일 하나를 끝낼 때마다 비운다.
						JavaParserFacade.clearInstances();
					}
				}

				@Override
				public FileResult write(Map<String, Object> file, Prepared prepared) throws Exception {
					return saveFile(file, prepared, fileTimeoutMillis);
				}

				@Override
				public void reset() throws Exception {
					// 버려진 스레드가 지금의 해석기와 캐시를 계속 만지고 있을 수 있다. 같이 쓰지 않도록 전부 새로 만든다.
					engine[0] = newEngine(context, root, jars, cacheSize);
					JavaParserFacade.clearInstances();
				}
			});
		} finally {
			JavaParserFacade.clearInstances();
		}
		DbTypeSolver dbSolver = engine[0].dbSolver;
		info("RESOLVE: 타입 조회 " + dbSolver.getLookups() + "회(DB " + dbSolver.getDbQueries() + "회), 파싱 " + dbSolver.getParses() + "회, 캐시에서 버림 "
				+ dbSolver.getUnitEvictions() + "회. analysisId=" + context.getAnalysisId());
	}

	/**
	 * <pre>
	 * 호출을 푸는 데 쓰는 도구 한 벌(타입 해석기와 참조 해석기)입니다.
	 * 스레드 하나가 쓰는 것을 전제로 만든 객체들이라, 그 스레드를 버리면 이것도 버리고 새로 만듭니다.
	 * </pre>
	 */
	private static class Engine {
		DbTypeSolver dbSolver;
		ReferenceResolver resolver;
		int jarCount;
	}

	private Engine newEngine(AnalysisJobContext context, Path root, List<Path> jars, int cacheSize) {
		Engine engine = new Engine();
		engine.dbSolver = new DbTypeSolver(symbolDao, javaSourceParser, encodingDetector, context.getRevisionId(), root, cacheSize, 50000);
		// 프로젝트의 타입을 먼저 찾고, 없으면 JDK, 그 다음 jar 순서로 찾는다.
		List<TypeSolver> solvers = new ArrayList<TypeSolver>();
		solvers.add(engine.dbSolver);
		solvers.add(new ReflectionTypeSolver(new JdkClassFilter()));
		for (int i = 0; i < jars.size(); i++) {
			try {
				solvers.add(new JarTypeSolver(jars.get(i)));
				engine.jarCount++;
			} catch (Exception e) {
				warn("jar를 읽지 못해 건너뜁니다: " + jars.get(i) + " (" + e.getMessage() + ")");
			}
		}
		// NoCache: CombinedTypeSolver가 찾은 타입을 끝없이 쌓아 두지 못하게 한다.
		// 쌓아 두면 버린 파일의 AST가 메모리에 계속 남는다. 캐시는 DbTypeSolver가 크기를 정해 따로 한다.
		CombinedTypeSolver typeSolver = new CombinedTypeSolver(CombinedTypeSolver.ExceptionHandlers.IGNORE_ALL, solvers
				, NoCache.<String, SymbolReference<ResolvedReferenceTypeDeclaration>>create());
		engine.resolver = new ReferenceResolver(context.getRevisionId(), engine.dbSolver, typeSolver, symbolDao);
		return engine;
	}

	/**
	 * <pre>
	 * 파일 하나를 푼 결과입니다. 아직 DB에는 아무것도 쓰지 않은 상태입니다.
	 * </pre>
	 */
	private static class Prepared {
		/** 풀 대상이 아니면 그 이유 */
		String skipReason;
		/** 실패했으면 그 내용 */
		String failMessage;
		ReferenceResolver.Outcome outcome;
	}

	/** 준비: 파일을 다시 파싱하고 참조를 푼다. DB에 쓰지 않는다(조회만 한다). */
	private Prepared resolveFile(Engine engine, Map<String, Object> file, long fileTimeoutMillis, ReferenceResolver.Checkpoint checkpoint) {
		long fileId = ((Number) file.get("fileId")).longValue();
		Prepared prepared = new Prepared();
		if (!"OK".equals(file.get("parseStatus"))) {
			// DECLARE에서 파싱에 실패했거나 뺀 파일은 참조도 없다.
			prepared.skipReason = "DECLARE 단계에서 처리되지 않은 파일입니다.";
			return prepared;
		}
		CompilationUnit unit = engine.dbSolver.unitOf(file);
		if (unit == null) {
			prepared.failMessage = "파일을 다시 읽거나 파싱하지 못했습니다(분석 뒤에 파일이 바뀌었을 수 있습니다).";
			return prepared;
		}
		List<Map<String, Object>> references = symbolDao.selectReferencesByFile(fileId);
		prepared.outcome = engine.resolver.resolveFile(fileId, unit, references, fileTimeoutMillis, checkpoint);
		return prepared;
	}

	/** 저장: 풀린 관계를 넣고 참조의 상태를 바꾼다. 트랜잭션 안이다. */
	private FileResult saveFile(Map<String, Object> file, Prepared prepared, long fileTimeoutMillis) {
		long fileId = ((Number) file.get("fileId")).longValue();
		if (prepared.skipReason != null) {
			return FileResult.skipped(prepared.skipReason);
		}
		if (prepared.failMessage != null) {
			return FileResult.failed("PARSE_ERROR", prepared.failMessage);
		}
		relationDao.replaceRelationsInBatch(fileId, prepared.outcome.relations, prepared.outcome.referenceUpdates);
		if (prepared.outcome.timedOutReferences > 0) {
			// 그때까지 푼 것은 저장하고, 못 푼 것이 있다는 사실을 오류로 남긴다.
			return FileResult.doneWithWarning("RESOLVE_TIMEOUT", "정해 둔 시간(" + (fileTimeoutMillis / 1000) + "초)을 넘겨서 참조 "
					+ prepared.outcome.timedOutReferences + "건을 풀지 못했습니다.");
		}
		return FileResult.done();
	}

	/**
	 * <pre>
	 * JDK의 타입만 이 프로그램의 클래스에서 찾도록 거르는 조건입니다.
	 *
	 * 걸러야 하는 이유: 이 분석기 자신도 Spring 등 많은 라이브러리를 싣고 돌아갑니다.
	 * 거르지 않으면 분석 대상이 쓰는 org.springframework... 를 분석기가 쓰는 버전의 클래스로 풀어 버립니다.
	 * 대상 프로젝트의 라이브러리는 그 프로젝트의 jar에서만 찾아야 합니다.
	 *
	 * JavaSymbolSolver에 들어 있는 "JDK만" 옵션은 java. 와 javax. 만 통과시켜서
	 * org.w3c.dom, org.xml.sax 같은 JDK 타입을 못 찾습니다. 그래서 따로 둡니다.
	 * </pre>
	 */
	static class JdkClassFilter implements Predicate<String> {

		private static final String[] JDK_PACKAGES = { "java.", "javax.", "jdk.", "org.w3c.dom.", "org.xml.sax.", "org.ietf.jgss." };

		@Override
		public boolean test(String className) {
			for (int i = 0; i < JDK_PACKAGES.length; i++) {
				if (className.startsWith(JDK_PACKAGES[i])) {
					return true;
				}
			}
			return false;
		}
	}

	private int intProperty(String key, int defaultValue) {
		String configured = configProperty.getProperty(key);
		if (configured == null || configured.trim().length() == 0) {
			return defaultValue;
		}
		return Integer.parseInt(configured.trim());
	}

}
