package net.dstone.knowledge.parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.DeclarationDao;
import net.dstone.knowledge.api.dao.FilePassDao;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.job.AnalysisPass;
import net.dstone.knowledge.job.FileHandler;
import net.dstone.knowledge.job.FilePassRunner;
import net.dstone.knowledge.job.FileResult;
import net.dstone.knowledge.parser.model.FileDeclarations;
import net.dstone.knowledge.scanner.EncodingDetector;
import net.dstone.knowledge.scanner.ScanPass;

/**
 * <pre>
 * DECLARE 단계: Java 파일을 하나씩 파싱해서 "무엇이 선언돼 있는지"를 DB에 넣습니다.
 *
 * 파일 하나마다 하는 일:
 *   1) SCAN이 알아낸 인코딩으로 읽는다.
 *   2) 파싱한다. 최신 문법으로 안 되면 낮은 문법 수준으로 다시 시도한다(JavaSourceParser).
 *   3) 타입/메소드/필드/애노테이션과, 아직 누구를 가리키는지 모르는 참조를 뽑는다(DeclarationCollector).
 *   4) DB에 쓰고 AST를 버린다.
 *
 * 이 단계에서는 다른 파일을 보지 않습니다. 그래서 파일을 어떤 순서로 처리해도 결과가 같고,
 * 메모리에는 지금 처리 중인 파일 하나만 올라갑니다. 호출이 누구를 가리키는지는 RESOLVE 단계가 풉니다.
 *
 * 파싱에 실패한 파일은 숨기지 않습니다. analysis_file.parse_status = FAILED 와 오류 내용을 남기고 계속합니다.
 * </pre>
 */
@Component
public class DeclarePass extends BaseObject implements AnalysisPass {

	public static final String NAME = "DECLARE";

	@Autowired
	private FilePassRunner filePassRunner;

	@Autowired
	private EncodingDetector encodingDetector;

	@Autowired
	private JavaSourceParser javaSourceParser;

	@Autowired
	private DeclarationDao declarationDao;

	@Autowired
	private FilePassDao filePassDao;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int order() {
		return 200;
	}

	@Override
	public void run(final AnalysisJobContext context) throws Exception {
		final Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		final String javaVersion = context.getProjectValue("javaVersion");
		final String[] rootPackages = rootPackagesOf(context.getProjectValue("rootPackages"));

		FileHandler<Prepared> handler = new FileHandler<Prepared>() {
			@Override
			public Prepared prepare(Map<String, Object> file) throws Exception {
				return parseFile(context, root, javaVersion, rootPackages, file);
			}

			@Override
			public FileResult write(Map<String, Object> file, Prepared prepared) throws Exception {
				return saveFile(context, file, prepared);
			}

			@Override
			public void reset() {
				// 파일 사이에 걸쳐 들고 있는 것이 없다. 파일마다 파서와 수집기를 새로 만든다.
			}
		};
		filePassRunner.run(context, NAME, "JAVA", handler);
		// JSP 안의 Java 코드(스크립틀릿)도 같은 방식으로 선언과 참조를 뽑는다(JspToJava).
		filePassRunner.run(context, NAME, "JSP", handler);
	}

	/**
	 * <pre>
	 * 파일 하나를 읽고 파싱한 결과입니다. 아직 DB에는 아무것도 쓰지 않은 상태입니다.
	 * </pre>
	 */
	private static class Prepared {
		/** 분석 대상이 아니면 그 이유. 대상이면 null */
		String skipReason;
		/** 실패했으면 오류 종류(PARSE_ERROR / TOO_DEEP)와 내용. 성공했으면 null */
		String failType;
		String failMessage;
		/** 성공했을 때의 결과 */
		FileDeclarations declarations;
		String languageLevel;
		String packageName;
		String sourceRoot;
	}

	/** 준비: 파일을 읽고, 파싱하고, 선언과 참조를 뽑는다. DB에 쓰지 않는다. */
	private Prepared parseFile(AnalysisJobContext context, Path root, String javaVersion, String[] rootPackages, Map<String, Object> file) throws Exception {
		long fileId = ((Number) file.get("fileId")).longValue();
		String path = (String) file.get("path");
		Prepared prepared = new Prepared();

		// 분석할 루트 패키지를 정해 둔 프로젝트면, 그 밖의 파일은 읽지도 않는다(SCAN이 정규식으로 찾아 둔 패키지로 판단).
		// JSP는 패키지가 없으므로 이 조건으로 거르지 않는다.
		if (!JspToJava.isJsp(path) && !isUnderRootPackages((String) file.get("packageName"), rootPackages)) {
			prepared.skipReason = "분석할 루트 패키지 밖입니다.";
			return prepared;
		}

		byte[] bytes = Files.readAllBytes(root.resolve(path));
		String text = encodingDetector.decode(bytes, (String) file.get("encoding"));
		if (JspToJava.isJsp(path)) {
			// JSP는 그 안의 Java 코드를 Java 소스로 바꿔서 일반 Java 파일과 같은 길로 보낸다. 줄 번호는 원본 JSP와 같다.
			text = JspToJava.convert(text, path);
			if (text == null) {
				prepared.skipReason = "Java 코드가 없는 JSP입니다.";
				return prepared;
			}
		}
		try {
			JavaSourceParser.Result parsed = javaSourceParser.parse(text, javaVersion);
			if (!parsed.isSuccessful()) {
				prepared.failType = "PARSE_ERROR";
				prepared.failMessage = parsed.error;
				return prepared;
			}
			// 패키지는 파서가 읽은 값이 정확하다. 소스 루트도 그 값으로 다시 구한다.
			prepared.packageName = parsed.unit.getPackageDeclaration().isPresent()
					? parsed.unit.getPackageDeclaration().get().getNameAsString() : "";
			prepared.sourceRoot = ScanPass.sourceRootOf(path, prepared.packageName);
			prepared.languageLevel = parsed.languageLevel;
			// 폴더 구조가 패키지와 맞지 않아 소스 루트를 못 찾은 파일은, 놓인 폴더를 대신 써서 ID가 겹치지 않게 한다.
			String idScope = prepared.sourceRoot != null ? prepared.sourceRoot : "?" + directoryOf(path);
			prepared.declarations = new DeclarationCollector(context.getProjectId(), context.getRevisionId(), fileId, idScope).collect(parsed.unit);
		} catch (StackOverflowError e) {
			// 식이 지나치게 깊게 중첩된 파일("a" + "b" + ... 를 수천 번 이은 것 등)은 파싱하거나 AST를 따라 내려가다 스택이 넘친다.
			// 여기서 막아서 "실패했다"는 사실을 파일 행에도 남길 수 있게 한다.
			prepared.declarations = null;
			prepared.failType = "TOO_DEEP";
			prepared.failMessage = "식이 너무 깊게 중첩돼 있어 처리하지 못했습니다(StackOverflowError).";
		}
		return prepared;
	}

	/** 저장: 준비한 결과를 DB에 쓴다. 트랜잭션 안이다. */
	private FileResult saveFile(AnalysisJobContext context, Map<String, Object> file, Prepared prepared) {
		long fileId = ((Number) file.get("fileId")).longValue();
		String path = (String) file.get("path");

		if (prepared.skipReason != null) {
			return FileResult.skipped(prepared.skipReason);
		}
		if (prepared.failType != null) {
			// 전에 성공했던 결과가 남아 있으면 지운다(파일이 바뀐 뒤 다시 돌린 경우).
			declarationDao.deleteDeclarationsInBatch(fileId);
			declarationDao.updateFileParsedInBatch(fileId, "FAILED", prepared.failMessage, null, null, null);
			return FileResult.failed(prepared.failType, prepared.failMessage);
		}
		FileDeclarations declarations = prepared.declarations;

		// 같은 소스 루트에 같은 이름의 타입이 다른 파일에도 있으면 ID가 겹친다(복사해 둔 파일, 이름만 바꾼 백업 파일 등).
		// 컴파일러라면 오류를 내는 상황이라 둘 중 하나만 넣는다. 어느 쪽을 살릴지는 처리 순서가 아니라 파일 이름으로 정한다:
		// 파일 이름이 클래스 이름과 같은 쪽(Order.java 안의 Order)이 진짜고, 아닌 쪽(Order_1.java 안의 Order)이 복사본이다.
		String displaced = null;
		List<Map<String, Object>> duplicates = findTypesDeclaredElsewhere(context.getRevisionId(), fileId, declarations);
		if (!duplicates.isEmpty()) {
			String fqn = (String) duplicates.get(0).get("fqn");
			String otherPath = (String) duplicates.get(0).get("path");
			long otherFileId = ((Number) duplicates.get(0).get("fileId")).longValue();
			if (isNamedAfterType(path, fqn) && !isNamedAfterType(otherPath, fqn)) {
				// 이 파일이 진짜다. 먼저 들어가 있던 복사본을 빼고 이 파일을 넣는다.
				String message = "같은 소스 루트에 같은 이름의 타입이 있어서 이 파일은 뺐습니다: " + fqn + " (남긴 파일: " + path + ")";
				declarationDao.deleteDeclarationsInBatch(otherFileId);
				declarationDao.updateFileParsedInBatch(otherFileId, "SKIPPED", message, null, null, null);
				filePassDao.updateFilePassInBatch(otherFileId, NAME, "FAILED", message);
				displaced = "같은 소스 루트에 같은 이름의 타입이 있어서 다른 파일을 뺐습니다: " + fqn + " (뺀 파일: " + otherPath + ")";
			} else {
				String message = "같은 소스 루트에 같은 이름의 타입이 이미 있어서 이 파일은 넣지 않았습니다: " + fqn + " (남긴 파일: " + otherPath + ")";
				declarationDao.deleteDeclarationsInBatch(fileId);
				declarationDao.updateFileParsedInBatch(fileId, "SKIPPED", message, prepared.languageLevel, prepared.packageName, prepared.sourceRoot);
				return FileResult.failed("DUPLICATE_TYPE", message);
			}
		}

		declarationDao.replaceDeclarationsInBatch(fileId, declarations);
		declarationDao.updateFileParsedInBatch(fileId, "OK", null, prepared.languageLevel, prepared.packageName, prepared.sourceRoot);

		if (displaced != null) {
			return FileResult.doneWithWarning("DUPLICATE_TYPE", displaced);
		}
		if (!declarations.getWarnings().isEmpty()) {
			return FileResult.doneWithWarning("DUPLICATE_DECLARATION", join(declarations.getWarnings()));
		}
		return FileResult.done();
	}

	private List<Map<String, Object>> findTypesDeclaredElsewhere(long revisionId, long fileId, FileDeclarations declarations) {
		List<String> symbolIds = new ArrayList<String>();
		for (int i = 0; i < declarations.getTypes().size(); i++) {
			symbolIds.add(declarations.getTypes().get(i).getSymbolId());
		}
		if (symbolIds.isEmpty()) {
			return new ArrayList<Map<String, Object>>();
		}
		return declarationDao.selectTypesDeclaredElsewhereInBatch(revisionId, fileId, symbolIds);
	}

	/** 파일 이름이 그 타입의 이름과 같은지 봅니다. 예: a/b/Order.java 와 a.b.Order */
	private boolean isNamedAfterType(String path, String fqn) {
		String fileName = path.substring(path.lastIndexOf('/') + 1);
		String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
		return fileName.equals(simpleName + ".java");
	}

	private String[] rootPackagesOf(String configured) {
		if (configured == null) {
			return new String[0];
		}
		String[] packages = configured.split(",");
		for (int i = 0; i < packages.length; i++) {
			packages[i] = packages[i].trim();
		}
		return packages;
	}

	private boolean isUnderRootPackages(String packageName, String[] rootPackages) {
		if (rootPackages.length == 0) {
			return true;
		}
		String name = packageName == null ? "" : packageName;
		for (int i = 0; i < rootPackages.length; i++) {
			String rootPackage = rootPackages[i];
			if (rootPackage.length() > 0 && (name.equals(rootPackage) || name.startsWith(rootPackage + "."))) {
				return true;
			}
		}
		return false;
	}

	private String directoryOf(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? "" : path.substring(0, slash);
	}

	private String join(List<String> lines) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				sb.append('\n');
			}
			sb.append(lines.get(i));
		}
		return sb.toString();
	}

}
