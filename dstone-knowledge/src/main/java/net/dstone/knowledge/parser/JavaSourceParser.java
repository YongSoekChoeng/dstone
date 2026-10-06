package net.dstone.knowledge.parser;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.resolution.SymbolResolver;

/**
 * <pre>
 * Java 소스 한 파일을 AST로 바꿉니다. 어느 문법 수준으로 읽어야 하는지도 여기서 알아냅니다.
 *
 * 문법 수준이 왜 필요한가:
 * Java는 버전이 올라가면서 예약어가 늘었습니다. 예전 소스는 지금의 예약어를 변수 이름으로 쓰기도 합니다
 * (Java 1.4까지는 enum, 1.3까지는 assert, 8까지는 _ 가 이름으로 쓸 수 있었습니다).
 * 그런 파일을 최신 문법으로 읽으면 파일 전체가 파싱 오류가 됩니다.
 *
 * 그래서 최신 문법부터 시도하고, 실패하면 수준을 낮춰 다시 읽습니다: 21 → 8 → 1.4
 * 프로젝트에 문법 수준(javaVersion)을 지정해 두었으면 그것을 맨 먼저 시도합니다.
 * 성공한 수준을 파일마다 기록해 둡니다(analysis_file.language_level).
 * </pre>
 */
@Component
public class JavaSourceParser {

	/** 차례로 시도할 문법 수준. 높은 것부터. */
	private static final LanguageLevel[] FALLBACK_LEVELS = {
		LanguageLevel.JAVA_21, LanguageLevel.JAVA_8, LanguageLevel.JAVA_1_4
	};

	/** 파싱 오류 내용이 지나치게 길면 앞쪽 몇 건만 남깁니다. */
	private static final int MAX_PROBLEMS = 5;

	/**
	 * <pre>
	 * 파싱 결과
	 * </pre>
	 */
	public static class Result {

		/** 성공했으면 AST, 실패했으면 null */
		public final CompilationUnit unit;

		/** 성공한 문법 수준(예: 21, 8, 1.4). 실패했으면 null */
		public final String languageLevel;

		/** 실패했을 때의 오류 내용. 가장 높은 수준으로 읽었을 때의 오류입니다. */
		public final String error;

		Result(CompilationUnit unit, String languageLevel, String error) {
			this.unit = unit;
			this.languageLevel = languageLevel;
			this.error = error;
		}

		public boolean isSuccessful() {
			return unit != null;
		}
	}

	/**
	 * @param text 소스 내용
	 * @param projectJavaVersion 프로젝트에 지정해 둔 문법 수준(예: 1.4, 8, 17). 없으면 null
	 */
	public Result parse(String text, String projectJavaVersion) {
		return parse(text, projectJavaVersion, null);
	}

	/**
	 * @param symbolResolver 파싱한 AST에 붙여 둘 심볼 해석기. 호출이 누구를 가리키는지 풀 때(RESOLVE) 필요합니다. 없으면 null
	 */
	public Result parse(String text, String projectJavaVersion, SymbolResolver symbolResolver) {
		List<LanguageLevel> levels = new ArrayList<LanguageLevel>();
		LanguageLevel preferred = levelOf(projectJavaVersion);
		if (preferred != null) {
			levels.add(preferred);
		}
		for (int i = 0; i < FALLBACK_LEVELS.length; i++) {
			if (!levels.contains(FALLBACK_LEVELS[i])) {
				levels.add(FALLBACK_LEVELS[i]);
			}
		}

		String firstError = null;
		for (int i = 0; i < levels.size(); i++) {
			LanguageLevel level = levels.get(i);
			// JavaParser 객체는 여러 스레드가 같이 쓸 수 없어서 매번 새로 만든다(만드는 비용은 작다).
			ParserConfiguration configuration = new ParserConfiguration();
			configuration.setLanguageLevel(level);
			// 주석은 지금 단계에서 쓰지 않는다. 붙이지 않으면 AST가 가벼워진다.
			configuration.setAttributeComments(false);
			if (symbolResolver != null) {
				configuration.setSymbolResolver(symbolResolver);
			}

			ParseResult<CompilationUnit> parsed = new JavaParser(configuration).parse(text);
			if (parsed.isSuccessful() && parsed.getResult().isPresent()) {
				return new Result(parsed.getResult().get(), nameOf(level), null);
			}
			if (firstError == null) {
				firstError = describe(parsed.getProblems());
			}
		}
		return new Result(null, null, firstError);
	}

	/** "1.4", "5", "8", "17" 같은 표기를 JavaParser의 문법 수준으로 바꿉니다. 모르는 값이면 null */
	LanguageLevel levelOf(String javaVersion) {
		if (javaVersion == null || javaVersion.trim().length() == 0) {
			return null;
		}
		String version = javaVersion.trim();
		// 1.5 ~ 1.8 은 5 ~ 8 과 같은 말이다.
		if (version.startsWith("1.") && version.length() == 3 && version.charAt(2) >= '5') {
			version = version.substring(2);
		}
		String enumName = "JAVA_" + version.replace('.', '_');
		LanguageLevel[] all = LanguageLevel.values();
		for (int i = 0; i < all.length; i++) {
			if (all[i].name().equals(enumName)) {
				return all[i];
			}
		}
		return null;
	}

	/** JAVA_1_4 → 1.4, JAVA_21 → 21 */
	private String nameOf(LanguageLevel level) {
		return level.name().substring("JAVA_".length()).replace('_', '.');
	}

	private String describe(List<Problem> problems) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < problems.size() && i < MAX_PROBLEMS; i++) {
			if (i > 0) {
				sb.append('\n');
			}
			sb.append(problems.get(i).getVerboseMessage());
		}
		if (problems.size() > MAX_PROBLEMS) {
			sb.append("\n... 외 ").append(problems.size() - MAX_PROBLEMS).append("건");
		}
		return sb.toString();
	}

}
