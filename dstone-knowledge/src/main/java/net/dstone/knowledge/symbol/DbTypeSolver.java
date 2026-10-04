package net.dstone.knowledge.symbol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;

import net.dstone.knowledge.api.dao.SymbolDao;
import net.dstone.knowledge.parser.JavaSourceParser;
import net.dstone.knowledge.scanner.EncodingDetector;

/**
 * <pre>
 * 프로젝트 안의 타입을 "DB 색인"으로 찾아 주는 타입 해석기입니다.
 *
 * JavaSymbolSolver가 호출 하나를 풀려면 관련된 타입의 선언(AST)이 필요합니다.
 * 기본으로 들어 있는 소스 해석기(JavaParserTypeSolver)는 소스 폴더를 뒤져 파일을 파싱하고,
 * 한 번 파싱한 파일을 끝까지 들고 있습니다. 큰 프로젝트에서는 결국 전체 AST가 메모리에 쌓여 OutOfMemoryError가 납니다.
 *
 * 이 해석기는 다르게 합니다.
 *   1) "이 이름의 타입이 어느 파일에 있나"는 DB(analysis_symbol)에 묻는다. DECLARE 단계가 이미 다 넣어 두었다.
 *   2) 그 파일 하나만 파싱한다.
 *   3) 파싱한 AST는 정해진 개수까지만 들고 있고, 넘치면 오래 안 쓴 것부터 버린다.
 * 그래서 프로젝트가 커져도 메모리에 올라가 있는 AST 수는 그대로입니다.
 *
 * 주의: 이 해석기만으로는 메모리가 묶이지 않습니다. 같이 쓰는 쪽에서 아래 두 가지를 지켜야 합니다.
 * - CombinedTypeSolver는 찾은 타입을 끝없이 캐시한다. 만들 때 캐시를 끈다(NoCache).
 * - JavaParserFacade도 풀어 본 노드를 캐시한다. 파일 하나를 끝낼 때마다 JavaParserFacade.clearInstances()로 비운다.
 * (ResolvePass가 그렇게 씁니다.)
 *
 * 스레드 하나에서만 써야 합니다. 분석 한 번에 객체 하나를 만들어 쓰고 버립니다.
 * </pre>
 */
public class DbTypeSolver implements TypeSolver {

	/** 파싱한 파일(AST)에 "이것이 몇 번 파일인지"를 적어 두는 꼬리표. 해석기가 찾은 선언이 어느 파일의 것인지 되짚을 때 씁니다. */
	public static final DataKey<Long> FILE_ID = new DataKey<Long>() {
	};

	/** "프로젝트 안에 그런 타입 없음"을 캐시에 적어 둘 때 쓰는 표시 */
	private static final Map<String, Object> NOT_IN_PROJECT = new LinkedHashMap<String, Object>();

	private final SymbolDao symbolDao;
	private final JavaSourceParser javaSourceParser;
	private final EncodingDetector encodingDetector;
	private final long revisionId;
	private final Path root;

	/** 파싱한 파일(AST). 키는 파일 ID */
	private final BoundedCache<Long, CompilationUnit> units;

	/**
	 * <pre>
	 * 이름 → 그 타입이 있는 파일. 없는 이름도 "없음"으로 적어 둡니다.
	 * 해석기는 java.lang.String 같은 프로젝트 밖의 이름도 수없이 물어보기 때문에, 없다는 답도 기억해야 DB를 덜 부릅니다.
	 * </pre>
	 */
	private final BoundedCache<String, Map<String, Object>> locations;

	private TypeSolver parent;

	private long lookups = 0;
	private long dbQueries = 0;
	private long parses = 0;

	/**
	 * @param root 프로젝트 소스의 루트 폴더
	 * @param maxUnits 메모리에 들고 있을 파싱한 파일 수
	 * @param maxLocations 기억해 둘 "이름 → 파일" 수
	 */
	public DbTypeSolver(SymbolDao symbolDao, JavaSourceParser javaSourceParser, EncodingDetector encodingDetector
			, long revisionId, Path root, int maxUnits, int maxLocations) {
		this.symbolDao = symbolDao;
		this.javaSourceParser = javaSourceParser;
		this.encodingDetector = encodingDetector;
		this.revisionId = revisionId;
		this.root = root;
		this.units = new BoundedCache<Long, CompilationUnit>(maxUnits);
		this.locations = new BoundedCache<String, Map<String, Object>>(maxLocations);
	}

	@Override
	public TypeSolver getParent() {
		return parent;
	}

	@Override
	public void setParent(TypeSolver parent) {
		this.parent = parent;
	}

	@Override
	public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
		lookups++;
		Map<String, Object> location = locationOf(name);
		if (location == null) {
			return SymbolReference.unsolved();
		}
		CompilationUnit unit = unitOf(location);
		if (unit == null) {
			return SymbolReference.unsolved();
		}
		TypeDeclaration<?> declaration = findType(unit, name, (String) location.get("packageName"));
		if (declaration == null) {
			return SymbolReference.unsolved();
		}
		return SymbolReference.solved(JavaParserFacade.get(getRoot()).getTypeDeclaration(declaration));
	}

	@Override
	public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveTypeInModule(String qualifiedModuleName, String simpleTypeName) {
		return tryToSolveType(simpleTypeName);
	}

	/** 이 이름의 타입이 프로젝트 안에 있는지 */
	public boolean isProjectType(String fqn) {
		return locationOf(fqn) != null;
	}

	/**
	 * <pre>
	 * 파일 하나의 AST를 돌려줍니다. 캐시에 있으면 그것을, 없으면 파싱해서 캐시에 넣고 돌려줍니다.
	 * 분석하려는 파일 자체도 이 메소드로 얻어야 합니다. 그래야 해석기가 돌려주는 선언과 같은 AST를 보게 됩니다.
	 * </pre>
	 *
	 * @param file {fileId, path, encoding, languageLevel}
	 * @return 읽거나 파싱하지 못했으면 null
	 */
	public CompilationUnit unitOf(Map<String, Object> file) {
		Long fileId = Long.valueOf(((Number) file.get("fileId")).longValue());
		CompilationUnit cached = units.get(fileId);
		if (cached != null) {
			return cached;
		}
		try {
			byte[] bytes = Files.readAllBytes(root.resolve((String) file.get("path")));
			String text = encodingDetector.decode(bytes, (String) file.get("encoding"));
			parses++;
			// DECLARE가 성공한 문법 수준으로 바로 읽는다.
			JavaSourceParser.Result parsed = javaSourceParser.parse(text, (String) file.get("languageLevel"), new JavaSymbolSolver(getRoot()));
			if (!parsed.isSuccessful()) {
				return null;
			}
			parsed.unit.setData(FILE_ID, fileId);
			units.put(fileId, parsed.unit);
			return parsed.unit;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * <pre>
	 * 이 이름의 타입이 프로젝트의 어느 파일에 있는지 돌려줍니다. 프로젝트 안에 없으면 null입니다.
	 * </pre>
	 *
	 * @return {fqn, symbolId, kind, packageName, fileId, path, encoding, languageLevel}
	 */
	public Map<String, Object> locationOf(String name) {
		Map<String, Object> cached = locations.get(name);
		if (cached != null) {
			return cached == NOT_IN_PROJECT ? null : cached;
		}
		dbQueries++;
		Map<String, Object> location = symbolDao.selectTypeLocation(revisionId, name);
		locations.put(name, location == null ? NOT_IN_PROJECT : location);
		return location;
	}

	/** 파일 안에서 그 이름의 타입 선언을 찾습니다. 중첩 타입(Outer.Inner)은 바깥에서 안으로 따라 들어갑니다. */
	private TypeDeclaration<?> findType(CompilationUnit unit, String fqn, String packageName) {
		String localName = packageName == null || packageName.length() == 0 ? fqn : fqn.substring(packageName.length() + 1);
		String[] parts = localName.split("\\.");

		TypeDeclaration<?> current = null;
		for (int i = 0; i < unit.getTypes().size(); i++) {
			if (unit.getTypes().get(i).getNameAsString().equals(parts[0])) {
				current = unit.getTypes().get(i);
				break;
			}
		}
		for (int p = 1; p < parts.length && current != null; p++) {
			TypeDeclaration<?> next = null;
			List<Node> children = current.getChildNodes();
			for (int i = 0; i < children.size(); i++) {
				Node child = children.get(i);
				if (child instanceof TypeDeclaration && ((TypeDeclaration<?>) child).getNameAsString().equals(parts[p])) {
					next = (TypeDeclaration<?>) child;
					break;
				}
			}
			current = next;
		}
		return current;
	}

	/** 지금 메모리에 들고 있는 파싱한 파일 수 */
	public int getCachedUnitCount() {
		return units.size();
	}

	public long getLookups() {
		return lookups;
	}

	public long getDbQueries() {
		return dbQueries;
	}

	public long getParses() {
		return parses;
	}

	public long getUnitEvictions() {
		return units.getEvictions();
	}

}
