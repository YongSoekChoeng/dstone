package net.dstone.knowledge.symbol;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.cache.NoCache;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.ProjectDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.common.exception.ApiException;
import net.dstone.knowledge.parser.JavaSourceParser;
import net.dstone.knowledge.scanner.EncodingDetector;

/**
 * <pre>
 * DB 색인 기반 타입 해석기(DbTypeSolver)를 실제 프로젝트에 돌려 보는 시험입니다.
 *
 * 확인하려는 것:
 *   1) 메모리가 묶이는가 - 파일을 많이 처리해도 들고 있는 AST 수와 힙 사용량이 늘지 않는가
 *   2) 호출이 풀리는가 - 메소드 호출 중 몇 %가 실제 선언으로 이어지는가
 *   3) DECLARE 결과와 맞는가 - 해석기가 찾은 "프로젝트 안의 메소드"가 analysis_method에 있는가
 *
 * 결과를 DB에 저장하지 않고 통계만 돌려줍니다. 호출 관계를 실제로 저장하는 일은 M2의 RESOLVE 단계가 하고,
 * 그때 이 클래스는 없어집니다. 여기서 확인한 사용법(캐시 끄기, 파일마다 비우기)은 RESOLVE가 그대로 가져갑니다.
 * </pre>
 */
@Component
public class TypeSolverTrial extends BaseObject {

	/** 몇 개 파일마다 힙 사용량을 재는지 */
	private static final int SAMPLE_EVERY = 50;

	/** 결과에 담는 예시의 최대 건수 */
	private static final int MAX_EXAMPLES = 10;

	/** 프로젝트에서 찾아 쓸 jar의 최대 개수 */
	private static final int MAX_JARS = 500;

	@Autowired
	private RevisionDao revisionDao;

	@Autowired
	private ProjectDao projectDao;

	@Autowired
	private SymbolDao symbolDao;

	@Autowired
	private JavaSourceParser javaSourceParser;

	@Autowired
	private EncodingDetector encodingDetector;

	/**
	 * @param maxFiles 앞에서부터 몇 개 파일을 볼지
	 * @param cacheSize 메모리에 들고 있을 파싱한 파일 수
	 * @param withJars 프로젝트 폴더 안의 jar(WEB-INF/lib 등)를 클래스패스로 쓸지
	 */
	public Map<String, Object> run(long revisionId, int maxFiles, int cacheSize, boolean withJars) {
		Map<String, Object> revision = revisionDao.selectRevision(revisionId);
		if (revision == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		Map<String, Object> project = projectDao.selectProject((String) revision.get("projectId"));

		// 타입을 찾을 때마다 DB를 조회한다. 그 SQL이 로그를 덮지 않게 이 스레드에서는 SQL 로그를 끈다.
		ThreadContext.put("SUPPRESS_SQL_LOG", "Y");
		try {
			Path root = Paths.get((String) project.get("localPath")).toRealPath();
			return runTrial(revisionId, root, maxFiles, cacheSize, withJars);
		} catch (IOException e) {
			throw ApiException.badRequest("프로젝트의 소스 경로를 읽을 수 없습니다: " + e.getMessage());
		} finally {
			ThreadContext.remove("SUPPRESS_SQL_LOG");
			JavaParserFacade.clearInstances();
		}
	}

	private Map<String, Object> runTrial(long revisionId, Path root, int maxFiles, int cacheSize, boolean withJars) throws IOException {
		long startedAt = System.currentTimeMillis();

		DbTypeSolver dbSolver = new DbTypeSolver(symbolDao, javaSourceParser, encodingDetector, revisionId, root, cacheSize, 50000);
		List<TypeSolver> solvers = new ArrayList<TypeSolver>();
		// 프로젝트의 타입을 먼저 찾고, 없으면 JDK, 그 다음 jar 순서로 찾는다.
		solvers.add(dbSolver);
		solvers.add(new ReflectionTypeSolver(true));
		int jarCount = 0;
		if (withJars) {
			List<Path> jars = findJars(root);
			for (int i = 0; i < jars.size(); i++) {
				try {
					solvers.add(new JarTypeSolver(jars.get(i)));
					jarCount++;
				} catch (Exception e) {
					warn("jar를 읽지 못해 건너뜁니다: " + jars.get(i) + " (" + e.getMessage() + ")");
				}
			}
		}
		// NoCache: CombinedTypeSolver가 찾은 타입을 끝없이 쌓아 두지 못하게 한다. 캐시는 DbTypeSolver가 크기를 정해 따로 한다.
		CombinedTypeSolver combined = new CombinedTypeSolver(CombinedTypeSolver.ExceptionHandlers.IGNORE_ALL, solvers
				, NoCache.<String, SymbolReference<ResolvedReferenceTypeDeclaration>>create());

		List<Map<String, Object>> files = symbolDao.selectParsedJavaFiles(revisionId, maxFiles);
		// 선언 대조 결과를 기억해 둔다. 같은 메소드를 여러 곳에서 부르기 때문이다.
		BoundedCache<String, Boolean> declaredCache = new BoundedCache<String, Boolean>(20000);

		int calls = 0, inProject = 0, external = 0, unresolved = 0, errors = 0, declared = 0, notDeclared = 0, maxCachedUnits = 0;
		List<String> notDeclaredExamples = new ArrayList<String>();
		Map<String, Integer> errorKinds = new LinkedHashMap<String, Integer>();
		List<Map<String, Object>> heapSamples = new ArrayList<Map<String, Object>>();

		for (int f = 0; f < files.size(); f++) {
			CompilationUnit unit = dbSolver.unitOf(files.get(f));
			if (unit != null) {
				List<MethodCallExpr> callExprs = unit.findAll(MethodCallExpr.class);
				for (int c = 0; c < callExprs.size(); c++) {
					calls++;
					try {
						SymbolReference<ResolvedMethodDeclaration> solved = JavaParserFacade.get(combined).solve(callExprs.get(c));
						if (!solved.isSolved()) {
							unresolved++;
							continue;
						}
						ResolvedMethodDeclaration method = solved.getCorrespondingDeclaration();
						String ownerFqn = method.declaringType().getQualifiedName();
						if (!dbSolver.isProjectType(ownerFqn)) {
							external++;
							continue;
						}
						inProject++;
						String key = ownerFqn + "#" + method.getName() + "/" + method.getNumberOfParams();
						Boolean isDeclared = declaredCache.get(key);
						if (isDeclared == null) {
							isDeclared = Boolean.valueOf(symbolDao.countMethod(revisionId, ownerFqn, method.getName(), method.getNumberOfParams()) > 0);
							declaredCache.put(key, isDeclared);
						}
						if (isDeclared.booleanValue()) {
							declared++;
						} else {
							notDeclared++;
							if (notDeclaredExamples.size() < MAX_EXAMPLES && !notDeclaredExamples.contains(key)) {
								notDeclaredExamples.add(key);
							}
						}
					} catch (StackOverflowError e) {
						// 해석기가 서로를 끝없이 부르다 넘치는 경우가 드물게 있다. 그 호출만 포기하고 계속한다.
						errors++;
						count(errorKinds, "StackOverflowError");
					} catch (RuntimeException e) {
						// 클래스패스에 없는 타입이 끼어 있으면 여기로 온다(UnsolvedSymbolException 등).
						errors++;
						count(errorKinds, e.getClass().getSimpleName());
					}
				}
			}
			// 파일 하나를 끝낼 때마다 JavaParserFacade가 쌓아 둔 것을 비운다. 안 비우면 버린 AST가 메모리에 계속 남는다.
			JavaParserFacade.clearInstances();
			maxCachedUnits = Math.max(maxCachedUnits, dbSolver.getCachedUnitCount());

			if ((f + 1) % SAMPLE_EVERY == 0 || f == files.size() - 1) {
				System.gc();
				Map<String, Object> sample = new LinkedHashMap<String, Object>();
				sample.put("files", f + 1);
				sample.put("usedHeapMb", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024));
				sample.put("cachedUnits", dbSolver.getCachedUnitCount());
				heapSamples.add(sample);
			}
		}

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", revisionId);
		result.put("files", files.size());
		result.put("cacheSize", cacheSize);
		result.put("jars", jarCount);
		result.put("elapsedMs", System.currentTimeMillis() - startedAt);

		Map<String, Object> callStats = new LinkedHashMap<String, Object>();
		callStats.put("total", calls);
		callStats.put("resolvedInProject", inProject);
		callStats.put("resolvedExternal", external);
		callStats.put("unresolved", unresolved);
		callStats.put("errors", errors);
		callStats.put("resolvedPercent", calls == 0 ? 0 : Math.round((inProject + external) * 1000.0 / calls) / 10.0);
		callStats.put("errorKinds", errorKinds);
		result.put("calls", callStats);

		// 해석기가 "프로젝트 안의 메소드"라고 한 것이 DECLARE가 넣은 analysis_method에 있는지
		Map<String, Object> declarationCheck = new LinkedHashMap<String, Object>();
		declarationCheck.put("found", declared);
		declarationCheck.put("notFound", notDeclared);
		declarationCheck.put("notFoundExamples", notDeclaredExamples);
		result.put("declarationCheck", declarationCheck);

		Map<String, Object> solverStats = new LinkedHashMap<String, Object>();
		solverStats.put("lookups", dbSolver.getLookups());
		solverStats.put("dbQueries", dbSolver.getDbQueries());
		solverStats.put("parses", dbSolver.getParses());
		solverStats.put("unitEvictions", dbSolver.getUnitEvictions());
		solverStats.put("maxCachedUnits", maxCachedUnits);
		result.put("solver", solverStats);
		result.put("heapSamples", heapSamples);
		return result;
	}

	/** 프로젝트 폴더 안의 jar를 찾습니다(WEB-INF/lib 등). */
	private List<Path> findJars(Path root) throws IOException {
		final List<Path> jars = new ArrayList<Path>();
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (file.getFileName().toString().endsWith(".jar") && jars.size() < MAX_JARS) {
					jars.add(file);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}
		});
		return jars;
	}

	private void count(Map<String, Integer> counts, String key) {
		Integer current = counts.get(key);
		counts.put(key, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
	}

}
