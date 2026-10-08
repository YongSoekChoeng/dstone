package net.dstone.ai.tools.utils;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;

/**
 * <pre>
 * 파일 목록/검색/읽기/쓰기/삭제 Tool입니다.
 *
 * 이 Tool들은 모두 "한 번에 돌려주는 양"에 상한을 둡니다.
 * Tool 결과는 그대로 대화 이력에 쌓여 다음 LLM 호출마다 다시 보내지기 때문에, 큰 결과를 통째로
 * 돌려주면 금방 모델의 컨텍스트 한도를 넘습니다. 실제로 두 번 겪었습니다.
 * - 232KB error.log를 두 번 읽고 "400: Provider returned error"로 실패
 * - 프로젝트 루트의 파일 목록(.git 속까지 338KB)을 두 번 받고 요청이 700KB가 되어
 *   "OpenAIIoException: Request failed"(응답 대기 시간 초과)로 실패
 *
 * 그래서 이렇게 합니다.
 * - 목록(readFileListAll): 소스와 상관없는 폴더(.git, target 등)는 빼고, 개수 상한까지만 줍니다.
 * - 검색(searchInFiles): 키워드가 들어 있는 "파일:줄번호: 그 줄"만, 건수 상한까지만 줍니다.
 * - 읽기(readFile/readFileLines/readFileTail): 앞부분 또는 끝부분만 글자 수 상한까지 줍니다.
 * 잘렸을 때는 결과에 그 사실과 "어떻게 좁히면 되는지"를 적어 모델이 다음 행동을 정할 수 있게 합니다.
 * </pre>
 */
@AiTool
public class FileUtil {

	/** 설정이 없을 때 쓰는 읽기 상한입니다(대략 1만 토큰 안팎). */
	private static final int DEFAULT_MAX_READ_CHARS = 30000;
	/** 설정이 없을 때 쓰는 파일 목록 개수 상한입니다. */
	private static final int DEFAULT_MAX_LIST_FILES = 500;
	/** 설정이 없을 때, 파일이 이 개수를 넘으면 파일 이름 대신 폴더별 개수만 줍니다. */
	private static final int DEFAULT_LIST_SUMMARY_OVER = 100;
	/** 폴더별 개수로 줄 때 담는 폴더 수의 상한입니다. */
	private static final int MAX_SUMMARY_FOLDERS = 150;
	/** 설정이 없을 때 쓰는 검색 결과 건수 상한입니다. */
	private static final int DEFAULT_MAX_SEARCH_MATCHES = 100;
	/** 설정이 없을 때, 이보다 큰 파일은 검색에서 건너뜁니다(1MB). */
	private static final int DEFAULT_MAX_SEARCH_FILE_BYTES = 1024 * 1024;
	/** 검색 결과 한 줄을 이 글자 수까지만 보여줍니다(한 줄짜리 거대한 파일 대비). */
	private static final int MAX_MATCH_LINE_CHARS = 200;
	/** 설정이 없을 때 목록/검색에서 빼는 폴더 이름들입니다(소스가 아닌 것들). */
	private static final String DEFAULT_EXCLUDE_DIRS = ".git,.svn,target,node_modules,.settings,.idea,.gradle,.metadata";

	@Autowired
	private Environment environment;

	@Tool(description = "basePath 디렉토리와 하위디렉토리의 파일목록을 절대경로로 반환한다(files). 사용자가 '파일목록 읽기' 등을 요청할 때 사용한다. "
		+ ".git, target 같은 폴더는 제외한다. 파일이 많으면 파일 이름 대신 폴더별 파일 수(folders)와 truncated=true, 안내(message)를 준다 - 그때는 필요한 하위 폴더를 basePath로 다시 호출한다. "
		+ "프로젝트 루트 전체를 조회하지 말고 소스 폴더로 좁혀서 호출하라. 특정 내용이 들어 있는 파일을 찾을 때는 이 Tool 대신 searchInFiles를 사용한다. 같은 경로를 다시 조회하지 마라.")
	public Map<String, Object> readFileListAll(@ToolParam(description = "조회할 디렉토리의 절대경로") String basePath) {
		Map<String, Object> result = new LinkedHashMap<>();
		List<String> files = new ArrayList<>();
		File base = this.toDirectory(basePath);
		if (base == null) {
			result.put("files", files);
			result.put("total", Integer.valueOf(0));
			result.put("truncated", Boolean.FALSE);
			result.put("message", "디렉토리를 찾을 수 없습니다: " + basePath);
			return result;
		}

		List<File> all = new ArrayList<>();
		this.collectFiles(base, this.excludeDirs(), all);
		// 파일이 많으면 파일 이름을 늘어놓지 않고 폴더별 개수만 줍니다(이유는 summarizeFolders의 설명 참고).
		if (all.size() > this.intProperty("list-summary-over", DEFAULT_LIST_SUMMARY_OVER)) {
			return this.summarizeFolders(all);
		}
		int max = this.intProperty("max-list-files", DEFAULT_MAX_LIST_FILES);
		for (File file : all) {
			if (files.size() >= max) {
				break;
			}
			files.add(this.toPath(file));
		}

		boolean truncated = all.size() > files.size();
		result.put("files", files);
		result.put("total", Integer.valueOf(all.size()));
		result.put("truncated", Boolean.valueOf(truncated));
		if (truncated) {
			result.put("message", "파일이 많아서 전체 " + all.size() + "개 중 " + files.size() + "개만 반환했습니다. "
				+ "더 좁은 하위 폴더를 basePath로 다시 호출하거나, 찾는 내용이 있으면 searchInFiles를 사용하세요.");
		}
		return result;
	}

	/**
	 * <pre>
	 * 파일이 많은 폴더를 조회했을 때, 파일 이름 대신 "폴더 경로 (그 폴더에 바로 들어 있는 파일 수)"만 돌려줍니다.
	 *
	 * 프로젝트 루트를 통째로 조회하면 파일 수백 개의 절대경로(4만 자)가 대화에 남아서, 그 뒤의 모든 LLM 호출에
	 * 다시 실려 갑니다. 실제로 요구사항 분석 Agent가 두 번째 조회에서 루트 목록을 받았고, 남은 17번의 호출이
	 * 전부 그만큼 느려졌습니다. "루트를 조회하지 마라"고 프롬프트에 적어 두었지만 지켜지지 않았습니다.
	 * 폴더 구조만 보여 주면 모델은 필요한 폴더를 골라 다시 조회하고, 그때는 파일 이름이 나옵니다.
	 * </pre>
	 *
	 * @param all 조회한 폴더 아래의 모든 파일입니다(이름순).
	 */
	private Map<String, Object> summarizeFolders(List<File> all) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (File file : all) {
			String folder = this.toPath(file.getParentFile());
			Integer count = counts.get(folder);
			counts.put(folder, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
		}
		List<String> folders = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (folders.size() >= MAX_SUMMARY_FOLDERS) {
				break;
			}
			folders.add(entry.getKey() + " (" + entry.getValue() + "개)");
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("files", new ArrayList<String>());
		result.put("folders", folders);
		result.put("total", Integer.valueOf(all.size()));
		result.put("truncated", Boolean.TRUE);
		String message = "파일이 " + all.size() + "개라서 파일 이름 대신 폴더별 파일 수(folders)만 반환했습니다. "
			+ "필요한 폴더 하나를 basePath로 다시 호출하면 파일 이름이 나옵니다. 찾는 내용이 있으면 searchInFiles를 사용하세요.";
		if (counts.size() > folders.size()) {
			message += " 폴더도 많아서 전체 " + counts.size() + "개 중 " + folders.size() + "개만 담았습니다.";
		}
		result.put("message", message);
		return result;
	}

	@Tool(description = "basePath 디렉토리와 하위디렉토리의 파일 내용에서 keyword가 들어 있는 줄을 찾아 '파일절대경로:줄번호: 그 줄 내용' 목록으로 반환한다(matches). "
		+ "어떤 소스가 특정 컬럼명/테이블명/클래스명/메서드명/URL/문구를 쓰는지 찾을 때 사용한다. 파일을 하나씩 열어 보기 전에 먼저 이 Tool로 후보를 찾아라. "
		+ "keyword는 정규식이 아니라 단순 문자열이며 대소문자를 구분하지 않는다. 결과가 많으면 일부만 반환하고 truncated=true와 안내(message)를 준다 - "
		+ "그때는 keyword를 더 구체적으로 하거나 fileNamePattern/basePath로 범위를 좁혀 다시 호출한다.")
	public Map<String, Object> searchInFiles(
			@ToolParam(description = "검색을 시작할 디렉토리의 절대경로") String basePath,
			@ToolParam(description = "찾을 문자열(정규식 아님, 대소문자 구분 안 함)") String keyword,
			@ToolParam(required = false, description = "검색할 파일명 패턴. 콤마로 여러 개 지정 가능. 예) *.java,*.xml  비워두면 모든 파일") String fileNamePattern) {
		Map<String, Object> result = new LinkedHashMap<>();
		List<String> matches = new ArrayList<>();
		result.put("matches", matches);

		File base = this.toDirectory(basePath);
		if (base == null) {
			result.put("totalMatches", Integer.valueOf(0));
			result.put("truncated", Boolean.FALSE);
			result.put("message", "디렉토리를 찾을 수 없습니다: " + basePath);
			return result;
		}
		if (keyword == null || keyword.isBlank()) {
			result.put("totalMatches", Integer.valueOf(0));
			result.put("truncated", Boolean.FALSE);
			result.put("message", "keyword가 비어 있습니다. 찾을 문자열을 지정하세요.");
			return result;
		}

		List<File> all = new ArrayList<>();
		this.collectFiles(base, this.excludeDirs(), all);
		List<Pattern> namePatterns = this.toNamePatterns(fileNamePattern);
		int maxMatches = this.intProperty("max-search-matches", DEFAULT_MAX_SEARCH_MATCHES);
		int maxFileBytes = this.intProperty("max-search-file-bytes", DEFAULT_MAX_SEARCH_FILE_BYTES);
		String lowerKeyword = keyword.toLowerCase(Locale.ROOT);

		int totalMatches = 0;
		int searchedFiles = 0;
		for (File file : all) {
			if (!this.matchesName(file.getName(), namePatterns) || file.length() > maxFileBytes) {
				continue;
			}
			String contents = this.readText(file);
			// 바이너리 파일이거나 읽지 못한 파일은 건너뜁니다.
			if (contents == null) {
				continue;
			}
			searchedFiles++;
			String[] lines = contents.split("\r?\n", -1);
			for (int i = 0; i < lines.length; i++) {
				if (!lines[i].toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
					continue;
				}
				totalMatches++;
				if (matches.size() < maxMatches) {
					matches.add(this.toPath(file) + ":" + (i + 1) + ": " + this.shorten(lines[i].trim()));
				}
			}
		}

		boolean truncated = totalMatches > matches.size();
		result.put("totalMatches", Integer.valueOf(totalMatches));
		result.put("searchedFiles", Integer.valueOf(searchedFiles));
		result.put("truncated", Boolean.valueOf(truncated));
		if (truncated) {
			result.put("message", "일치하는 줄이 많아서 전체 " + totalMatches + "건 중 " + matches.size() + "건만 반환했습니다. "
				+ "keyword를 더 구체적으로 하거나 fileNamePattern/basePath로 범위를 좁혀 다시 호출하세요.");
		} else if (totalMatches == 0) {
			result.put("message", "일치하는 줄이 없습니다(검색한 파일 " + searchedFiles + "개). 다른 keyword나 다른 경로로 시도해 보세요.");
		}
		return result;
	}

	/**
	 * 파일을 읽지 못했을 때 돌려주는 문구의 앞부분입니다. "실패"로 시작해야 TOOL step이 실패로 판정합니다
	 * (runtime.step.ToolStepExecutor). 그렇지 않으면 이 문구가 파일 내용인 것처럼 다음 step으로 넘어갑니다.
	 */
	private static final String CANNOT_READ_MESSAGE = "실패: 파일을 읽을 수 없습니다(존재하지 않거나 읽기 권한이 없음): ";

	@Tool(description = "절대경로 fileFullPath의 파일을 읽어서 파일내용을 스트링형식으로 반환한다. 사용자가 '파일 읽기' 등을 요청할 때 사용한다. "
		+ "파일이 크면 앞부분만 반환하고 잘렸다는 안내를 붙인다. 로그처럼 끝부분이 중요한 큰 파일은 readFileTail을 사용한다. 같은 파일을 다시 읽지 마라.")
	public String readFile(@ToolParam String fileFullPath) {
		String contents = this.readOrNull(fileFullPath);
		if (contents == null) {
			return CANNOT_READ_MESSAGE + fileFullPath;
		}
		int maxChars = this.intProperty("max-read-chars", DEFAULT_MAX_READ_CHARS);
		if (contents.length() <= maxChars) {
			return contents;
		}
		return contents.substring(0, maxChars)
			+ "\n\n...(파일이 커서 전체 " + contents.length() + "자 중 앞 " + maxChars + "자만 반환했습니다. 끝부분이 필요하면 readFileTail을 사용하세요.)";
	}

	/**
	 * <pre>
	 * 파일을 "줄번호: 그 줄 내용" 모양으로 읽어 줍니다.
	 *
	 * readFile은 본문만 돌려주기 때문에, "몇 번째 줄에 무엇이 있다"를 확인하려는 모델은 줄을 1번부터 직접 세어야 합니다.
	 * 실제로 리뷰 Agent가 줄을 세느라 출력 토큰 한도를 추론에 다 써서, 답을 한 글자도 쓰지 못하고 끝난 적이 있습니다.
	 * 줄 번호가 필요할 때는 이 Tool을 쓰게 합니다.
	 *
	 * readFile에 줄 번호를 붙이지 않고 Tool을 따로 둔 이유: 읽은 내용을 writeFile로 되쓰는 Agent가 있어서,
	 * 줄 번호가 파일 내용에 섞여 들어가면 안 되기 때문입니다.
	 * 줄 번호는 searchInFiles가 알려 주는 줄 번호와 같은 방식으로 셉니다.
	 * </pre>
	 */
	@Tool(description = "절대경로 fileFullPath의 파일을 '줄번호: 그 줄 내용' 형식으로 읽어서 반환한다. 어느 줄에 무엇이 있는지(줄 번호) 확인하거나 문서에 줄 번호를 적을 때 사용한다. "
		+ "줄을 직접 세지 말고 이 Tool의 줄 번호를 그대로 써라. startLine/endLine으로 필요한 구간만 읽을 수 있다(searchInFiles가 알려 준 줄 번호의 앞뒤를 볼 때 좋다). "
		+ "구간이 크면 앞부분만 반환하고 어디까지 반환했는지 안내를 붙인다. 이 결과는 줄 번호가 붙어 있으므로 writeFile의 내용으로 그대로 쓰면 안 된다.")
	public String readFileLines(
			@ToolParam(description = "읽을 파일의 절대경로(파일명 포함)") String fileFullPath,
			@ToolParam(required = false, description = "읽기 시작할 줄 번호(1부터). 비워두면 첫 줄부터") Integer startLine,
			@ToolParam(required = false, description = "마지막으로 읽을 줄 번호(이 줄 포함). 비워두면 끝까지") Integer endLine) {
		String contents = this.readOrNull(fileFullPath);
		if (contents == null) {
			return CANNOT_READ_MESSAGE + fileFullPath;
		}
		String[] lines = contents.split("\r?\n", -1);
		int totalLines = lines.length;
		// 파일이 줄바꿈으로 끝나면 마지막에 빈 조각이 하나 생깁니다. 그것은 줄로 세지 않습니다.
		if (totalLines > 0 && lines[totalLines - 1].isEmpty()) {
			totalLines--;
		}
		int from = startLine == null || startLine.intValue() < 1 ? 1 : startLine.intValue();
		int to = endLine == null || endLine.intValue() > totalLines ? totalLines : endLine.intValue();
		if (from > to) {
			return "읽을 줄이 없습니다(파일은 전체 " + totalLines + "줄, 요청한 구간은 " + from + "~" + (endLine == null ? "끝" : String.valueOf(endLine)) + "줄): " + fileFullPath;
		}

		int maxChars = this.intProperty("max-read-chars", DEFAULT_MAX_READ_CHARS);
		StringBuilder result = new StringBuilder();
		int lastLine = from - 1;
		for (int lineNo = from; lineNo <= to; lineNo++) {
			String numbered = lineNo + ": " + lines[lineNo - 1] + "\n";
			// 상한을 넘으면 멈춥니다. 다만 한 줄도 못 준 채로 끝나지 않도록 첫 줄은 넘더라도 줍니다.
			if (result.length() + numbered.length() > maxChars && lastLine >= from) {
				break;
			}
			result.append(numbered);
			lastLine = lineNo;
		}
		if (lastLine < to) {
			result.append("\n...(구간이 커서 ").append(from).append("~").append(lastLine).append("줄만 반환했습니다. 파일은 전체 ").append(totalLines)
				.append("줄입니다. 이어서 보려면 startLine=").append(lastLine + 1).append("로 다시 호출하세요.)");
		} else if (from > 1 || to < totalLines) {
			result.append("\n(").append(from).append("~").append(to).append("줄을 반환했습니다. 파일은 전체 ").append(totalLines).append("줄입니다.)");
		}
		return result.toString();
	}

	@Tool(description = "절대경로 fileFullPath 파일의 끝부분만 읽어서 반환한다. 로그 파일처럼 크고 최근 내용(끝부분)이 중요한 파일을 읽을 때 사용한다.")
	public String readFileTail(@ToolParam String fileFullPath) {
		String contents = this.readOrNull(fileFullPath);
		if (contents == null) {
			return CANNOT_READ_MESSAGE + fileFullPath;
		}
		int maxChars = this.intProperty("max-read-chars", DEFAULT_MAX_READ_CHARS);
		if (contents.length() <= maxChars) {
			return contents;
		}
		return "...(파일이 커서 전체 " + contents.length() + "자 중 끝 " + maxChars + "자만 반환했습니다.)\n\n"
			+ contents.substring(contents.length() - maxChars);
	}

	@Tool(description = "절대경로 filePath의 파일을 삭제한다. 사용자가 '파일 삭제' 등을 요청할 때 사용한다.")
	public void deleteFile(@ToolParam String fileFullPath) {
		net.dstone.common.utils.FileUtil.deleteFile(fileFullPath);
	}

	/**
	 * <pre>
	 * 파일을 새로 쓰거나 덮어씁니다. 받은 내용을 한 글자도 바꾸지 않고 그대로 저장합니다.
	 *
	 * 내용을 줄 단위로 잘라서 저장하면 안 됩니다. 줄을 나누는 순간 줄바꿈이 사라지고(공통 StringUtil.toStrArray는 들여쓰기까지 지웁니다)
	 * 소스 파일이 한 줄로 붙어 버립니다. 그리고 긴 문서가 끊기는 것은 파일을 쓰는 쪽이 아니라 모델이 Tool 인자를 만드는 쪽의
	 * 출력 토큰 한도 때문이라, 여기서 나눠 써도 해결되지 않습니다(그 대책이 appendFile, replaceInFile, copyFileWithReplace입니다).
	 *
	 * 저장하지 못하면 "실패: ..."로 답합니다. 전에는 답이 없어서(void) 실패해도 TOOL step이 성공으로 보았습니다.
	 * </pre>
	 */
	@Tool(description = "절대경로 filePath(폴더+파일명)에 fileContents 내용으로 파일을 저장한다. 폴더가 없으면 만들고, 파일이 있으면 덮어쓴다. 저장 결과 안내 문구를 돌려준다. "
		+ "이미 있는 파일의 일부만 고칠 때는 이 Tool로 전체를 다시 쓰지 말고 replaceInFile을 사용한다.")
	public String writeFile(@ToolParam(description = "저장할 파일의 절대경로(파일명 포함)") String filePath, @ToolParam (description = "파일 내용")String fileContents) {
		if (filePath == null || filePath.isBlank()) {
			return "실패: filePath가 비어 있습니다.";
		}
		File file = new File(filePath.trim());
		try {
			if (file.getParentFile() != null) {
				Files.createDirectories(file.getParentFile().toPath());
			}
			String contents = fileContents == null ? "" : fileContents;
			Files.write(file.toPath(), contents.getBytes(Charset.defaultCharset()), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			return "저장했습니다. 파일은 전체 " + contents.length() + "자입니다: " + this.toPath(file);
		} catch (Exception e) {
			return "실패: 파일을 저장하지 못했습니다(" + e.getClass().getSimpleName() + ": " + e.getMessage() + "): " + filePath;
		}
	}
	
	/**
	 * <pre>
	 * 파일 끝에 내용을 이어 붙입니다.
	 *
	 * 긴 문서를 writeFile 한 번으로 쓰면 모델의 출력 토큰 한도(max-tokens)에 걸려 인자가 중간에 끊깁니다.
	 * 추론을 하는 모델은 추론에 쓴 분량도 그 한도에 들어가서, 12,000자쯤 되는 문서에서 실제로 끊겼습니다.
	 * 그래서 앞부분은 writeFile로, 나머지는 이 Tool로 나눠 쓰게 합니다.
	 * 글자셋은 writeFile과 같습니다(실행 환경의 기본 글자셋).
	 * </pre>
	 */
	@Tool(description = "절대경로 filePath 파일의 끝에 fileContents를 이어 붙인다(파일이 없으면 새로 만든다). 긴 문서를 나눠서 저장할 때 사용한다: "
		+ "writeFile로 앞부분을 저장한 뒤 이 Tool로 나머지를 순서대로 이어 붙인다. 줄바꿈은 자동으로 넣지 않으므로 fileContents의 첫머리에 필요한 줄바꿈을 직접 넣어라. "
		+ "저장한 뒤 파일의 전체 글자 수를 알려 준다.")
	public String appendFile(@ToolParam(description = "이어 붙일 파일의 절대경로(파일명 포함)") String filePath, @ToolParam(description = "이어 붙일 내용") String fileContents) {
		if (filePath == null || filePath.isBlank()) {
			return "실패: filePath가 비어 있습니다.";
		}
		File file = new File(filePath.trim());
		try {
			if (file.getParentFile() != null) {
				Files.createDirectories(file.getParentFile().toPath());
			}
			String contents = fileContents == null ? "" : fileContents;
			// 이미 있는 파일에 붙일 때는 그 파일의 글자셋과 줄바꿈 방식을 따릅니다. 따르지 않으면 EUC-KR 파일 끝의 한글이 깨지고,
			// CRLF 파일 끝에 LF 줄이 섞입니다. 새 파일이면 writeFile과 같게(실행 환경의 기본 글자셋, 받은 그대로) 씁니다.
			Charset charset = Charset.defaultCharset();
			if (file.isFile() && file.length() > 0) {
				byte[] existing = Files.readAllBytes(file.toPath());
				charset = charsetOf(existing);
				if (new String(existing, charset).contains("\r\n")) {
					contents = toLineBreak(contents, true);
				}
			}
			Files.write(file.toPath(), contents.getBytes(charset), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			String saved = new String(Files.readAllBytes(file.toPath()), charset);
			return "이어 붙였습니다. 지금 파일은 전체 " + saved.length() + "자입니다: " + this.toPath(file);
		} catch (Exception e) {
			return "실패: 파일에 이어 붙이지 못했습니다(" + e.getClass().getSimpleName() + ": " + e.getMessage() + "): " + filePath;
		}
	}

	/** 파일을 읽고 고쳐서 다시 쓰는 Tool들이 같은 파일을 동시에 건드리지 않게 하는 잠금입니다(forEach step은 동시에 실행됩니다). */
	private static final Object EDIT_LOCK = new Object();

	/**
	 * <pre>
	 * 파일에서 정확히 한 곳만 바꿉니다.
	 *
	 * 300줄짜리 파일의 한 줄을 고치려고 writeFile로 파일 전체를 다시 쓰면, 그 전체가 모델의 출력 토큰이 되어 한도에 걸리고
	 * 고치지 않을 부분까지 모델이 옮겨 적다가 바꿔 버릴 수 있습니다. 이 Tool은 바꿀 부분만 받습니다.
	 *
	 * - oldText가 파일에 정확히 한 번 나올 때만 바꿉니다. 없거나 여러 번 나오면 바꾸지 않고 "실패: ..."로 답합니다
	 *   (엉뚱한 곳을 바꾸는 것보다 안 바꾸는 쪽이 안전합니다).
	 * - 줄바꿈 방식만 다른 것은 맞춰 줍니다. 모델은 줄바꿈을 LF로 보내는데 Windows에서 만든 소스는 CRLF라서,
	 *   글자 그대로만 견주면 여러 줄짜리 oldText는 하나도 찾지 못합니다. newText의 줄바꿈도 파일의 방식으로 바꿉니다.
	 * - 파일의 글자셋을 그대로 지킵니다(UTF-8로 읽히면 UTF-8, 아니면 MS949).
	 * - newText를 비우면 그 부분을 지웁니다.
	 * - 못 찾았을 때는 파일의 그 자리가 실제로 어떻게 생겼는지를 답에 붙입니다(nearestText).
	 *   "못 찾았다"만 돌려주면 모델이 같은 oldText를 그대로 다시 보내는 일을 되풀이합니다(실제로 한 파일에서 20번 되풀이했습니다).
	 * - oldText와 newText가 같으면 바꾸지 않고 "실패: ..."로 답합니다. 할 일을 마친 모델이 아무것도 바뀌지 않는 호출을 이어 가는 것을 끊습니다.
	 * </pre>
	 */
	@Tool(description = "절대경로 filePath 파일에서 oldText를 newText로 바꾼다. 이미 있는 파일의 일부를 고칠 때 쓴다(파일 전체를 writeFile로 다시 쓰지 않는다). "
		+ "oldText는 파일에 정확히 한 번만 나와야 한다. 없거나 여러 번 나오면 아무것도 바꾸지 않고 '실패: ...'로 답한다 - 그때는 앞뒤 줄을 더 포함해 한 곳만 가리키게 하거나, 들여쓰기와 공백이 파일과 같은지 확인한다. "
		+ "못 찾았을 때는 파일의 그 자리에 실제로 있는 글자를 답에 붙여 주니, 파일을 다시 읽지 말고 그 글자로 oldText를 고쳐 다시 부른다. "
		+ "줄바꿈 방식(CRLF/LF)의 차이는 자동으로 맞춘다. newText를 빈 글자로 주면 그 부분을 지운다. 줄을 끼워 넣으려면 oldText에 기준이 되는 줄을, newText에 그 줄과 새 줄을 함께 적는다.")
	public String replaceInFile(
			@ToolParam(description = "고칠 파일의 절대경로(파일명 포함)") String filePath,
			@ToolParam(description = "바꿀 대상. 파일에 있는 글자 그대로(들여쓰기와 줄바꿈 포함)") String oldText,
			@ToolParam(required = false, description = "바꾼 뒤의 글자. 비우면 oldText를 지운다") String newText) {
		if (filePath == null || filePath.isBlank()) {
			return "실패: filePath가 비어 있습니다.";
		}
		if (oldText == null || oldText.isEmpty()) {
			return "실패: oldText가 비어 있습니다. 파일 끝에 덧붙이려면 appendFile을 사용하세요.";
		}
		if (toLineBreak(oldText, false).equals(toLineBreak(newText == null ? "" : newText, false))) {
			return "실패: oldText와 newText가 같아서 바꿀 것이 없습니다. 아무것도 바꾸지 않았습니다. 할 일을 모두 마쳤으면 Tool을 더 부르지 말고 답하세요.";
		}
		File file = new File(filePath.trim());
		if (!file.isFile()) {
			return CANNOT_READ_MESSAGE + filePath;
		}
		synchronized (EDIT_LOCK) {
			try {
				byte[] bytes = Files.readAllBytes(file.toPath());
				Charset charset = charsetOf(bytes);
				String contents = new String(bytes, charset);
				String from = oldText;
				String to = newText == null ? "" : newText;
				int found = countOf(contents, from);
				if (found == 0) {
					// 줄바꿈 방식만 달라서 못 찾은 것일 수 있습니다. 파일의 방식에 맞춰 다시 찾아 봅니다.
					boolean crlfFile = contents.contains("\r\n");
					from = toLineBreak(oldText, crlfFile);
					to = toLineBreak(to, crlfFile);
					found = countOf(contents, from);
				}
				if (found == 0) {
					return "실패: oldText를 파일에서 찾지 못했습니다. 아무것도 바꾸지 않았습니다. 같은 oldText로 다시 부르지 마세요. "
						+ nearestText(contents, oldText) + "\n파일: " + this.toPath(file);
				}
				if (found > 1) {
					return "실패: oldText가 파일에 " + found + "번 나옵니다. 아무것도 바꾸지 않았습니다. 앞뒤 줄을 더 포함해 한 곳만 가리키게 하세요: " + this.toPath(file);
				}
				int at = contents.indexOf(from);
				String changed = contents.substring(0, at) + to + contents.substring(at + from.length());
				Files.write(file.toPath(), changed.getBytes(charset), StandardOpenOption.TRUNCATE_EXISTING);
				return "바꿨습니다(" + lineOf(contents, at) + "번째 줄부터). 파일은 전체 " + changed.length() + "자입니다: " + this.toPath(file);
			} catch (Exception e) {
				return "실패: 파일을 고치지 못했습니다(" + e.getClass().getSimpleName() + ": " + e.getMessage() + "): " + filePath;
			}
		}
	}

	/**
	 * <pre>
	 * 파일을 복사하면서 이름 바꿈 표대로 글자를 바꿉니다.
	 *
	 * 기존 기능을 본떠 새 파일을 만들 때, 달라지는 것의 대부분은 이름입니다(클래스, URL, 쿼리ID, 테이블).
	 * 그 치환을 모델에게 시키면 파일 전체를 다시 써야 하고 옛 이름을 한두 군데 남기기도 합니다. 정해진 치환은 코드가 합니다.
	 *
	 * - 긴 이름부터 맞춰 봅니다. "sampleBoard → faq"와 "/sample/board/sampleBoard.go → /sample/faq/faq.go"가 함께 있으면
	 *   긴 쪽이 먼저 들어맞아야 주소가 깨지지 않습니다.
	 * - 한 번 바꾼 자리는 다시 바꾸지 않습니다(앞에서부터 한 번만 훑습니다). "A → B", "B → C"가 함께 있어도 A가 C가 되지 않습니다.
	 * - 대상 파일이 이미 있으면 복사하지 않습니다. 덮어쓰면 기존 내용이 사라집니다.
	 * - 원본의 글자셋과 줄바꿈 방식은 그대로 갑니다.
	 * </pre>
	 */
	@Tool(description = "절대경로 sourcePath 파일을 targetPath로 복사하면서 replacements의 이름 바꿈 표대로 글자를 바꾼다. 기존 파일을 본떠 새 파일을 만들 때 쓴다 - "
		+ "이름만 바뀌는 부분은 이 Tool이 하고, 빼거나 더할 부분만 그 뒤에 replaceInFile로 고친다. 긴 이름부터 맞추고 한 번 바꾼 자리는 다시 바꾸지 않는다. "
		+ "대소문자를 구분한다. targetPath에 파일이 이미 있으면 복사하지 않고 '실패: ...'로 답한다. 결과에 이름마다 몇 군데를 바꿨는지 알려 준다(0군데면 그 이름이 원본에 없었다는 뜻이다).")
	public String copyFileWithReplace(
			@ToolParam(description = "본뜰 원본 파일의 절대경로") String sourcePath,
			@ToolParam(description = "새로 만들 파일의 절대경로(파일명 포함)") String targetPath,
			@ToolParam(required = false, description = "이름 바꿈 표. 항목마다 from(원본에 있는 글자)과 to(바꿀 글자). 비우면 그대로 복사한다") List<Map<String, String>> replacements) {
		if (sourcePath == null || sourcePath.isBlank() || targetPath == null || targetPath.isBlank()) {
			return "실패: sourcePath와 targetPath를 모두 주어야 합니다.";
		}
		File source = new File(sourcePath.trim());
		File target = new File(targetPath.trim());
		if (!source.isFile()) {
			return CANNOT_READ_MESSAGE + sourcePath;
		}
		synchronized (EDIT_LOCK) {
			if (target.exists()) {
				return "실패: 대상 파일이 이미 있어서 복사하지 않았습니다(덮어쓰면 기존 내용이 사라집니다): " + this.toPath(target);
			}
			try {
				byte[] bytes = Files.readAllBytes(source.toPath());
				Charset charset = charsetOf(bytes);
				List<String[]> rules = toRules(replacements);
				int[] counts = new int[rules.size()];
				String changed = replaceOnePass(new String(bytes, charset), rules, counts);
				if (target.getParentFile() != null) {
					Files.createDirectories(target.getParentFile().toPath());
				}
				Files.write(target.toPath(), changed.getBytes(charset), StandardOpenOption.CREATE_NEW);

				StringBuilder result = new StringBuilder();
				result.append("복사했습니다. 파일은 전체 ").append(changed.length()).append("자입니다: ").append(this.toPath(target));
				for (int i = 0; i < rules.size(); i++) {
					result.append("\n- ").append(rules.get(i)[0]).append(" → ").append(rules.get(i)[1]).append(": ").append(counts[i]).append("군데");
				}
				return result.toString();
			} catch (Exception e) {
				return "실패: 파일을 복사하지 못했습니다(" + e.getClass().getSimpleName() + ": " + e.getMessage() + "): " + targetPath;
			}
		}
	}

	/** editFileLines의 action 값입니다. */
	private static final String ACTION_DELETE = "delete";
	private static final String ACTION_REPLACE = "replace";
	private static final String ACTION_INSERT_AFTER = "insertAfter";

	/**
	 * <pre>
	 * 파일을 줄 번호로 고칩니다. 여러 곳을 한 번에 받아서, 모두 맞을 때만 한꺼번에 적용합니다.
	 *
	 * 왜 줄 번호인가:
	 * replaceInFile은 지울 글자를 모델이 한 글자도 틀리지 않고 옮겨 적어야 합니다. 메서드 하나를 지우려면 그 메서드 전체가
	 * 모델의 출력이 되고, 들여쓰기 하나만 달라도 실패합니다. 줄 번호는 "40번째 줄부터 75번째 줄"이면 끝입니다.
	 * 그래서 모델이 Tool을 여러 번 부르며 고쳐 나갈 필요가 없고, 답 한 번(변경 목록)으로 끝낼 수 있습니다.
	 *
	 * 규칙:
	 * - 줄 번호는 모두 "고치기 전의 파일" 기준입니다(readFileLines가 보여 준 번호). 앞의 변경 때문에 번호가 밀리는 것은 여기서 계산합니다.
	 * - action은 delete(startLine~endLine 줄을 지움), replace(그 줄들을 newText로 바꿈), insertAfter(startLine 줄 뒤에 newText를 넣음. 0이면 맨 앞)입니다.
	 * - startText / endText를 주면 그 줄의 내용(앞뒤 공백 제외)과 같은지 확인합니다. 모델이 번호를 한 줄 밀려 읽는 실수를 여기서 잡습니다.
	 *   구간의 첫 줄이나 끝 줄이 빈 줄이면 그 안쪽의 글자가 있는 첫 줄 / 끝 줄과 견줘도 됩니다.
	 * - 하나라도 틀리면(번호가 범위 밖, 구간이 겹침, 확인 글자가 다름) 아무것도 바꾸지 않고 "실패: ..."로 이유를 모두 알려 줍니다.
	 *   절반만 적용된 파일은 그 뒤의 줄 번호가 모두 어긋나서 다시 고칠 수도 없기 때문입니다.
	 * - 파일의 글자셋과 줄바꿈 방식은 그대로 지킵니다.
	 * - 고친 뒤에 괄호의 짝이 고치기 전과 달라졌으면 답 끝에 "주의: ..."를 붙입니다(bracketNotice). 고친 것은 되돌리지 않습니다.
	 * </pre>
	 */
	@Tool(description = "절대경로 filePath 파일을 줄 번호로 고친다. 여러 곳을 edits에 한 번에 담는다. 줄 번호는 모두 고치기 전의 파일 기준이다(readFileLines가 보여 준 번호. 앞의 변경으로 밀리는 것은 계산하지 않는다). "
		+ "action이 delete면 startLine~endLine 줄을 지우고, replace면 그 줄들을 newText로 바꾸고, insertAfter면 startLine 줄 뒤에 newText를 넣는다(0이면 맨 앞). "
		+ "startText와 endText에 그 줄의 내용을 적으면 번호가 맞는지 확인해 준다. 하나라도 틀리면 아무것도 바꾸지 않고 '실패: ...'로 이유를 알려 준다. "
		+ "고친 뒤에 괄호의 짝이 고치기 전과 달라졌으면 결과 끝에 '주의: ...'를 붙인다.")
	public String editFileLines(
			@ToolParam(description = "고칠 파일의 절대경로(파일명 포함)") String filePath,
			@ToolParam(description = "변경 목록. 항목마다 action(delete/replace/insertAfter), startLine, endLine(insertAfter에는 필요 없음), newText(delete에는 필요 없음), "
				+ "startText(startLine 줄의 내용, 확인용), endText(endLine 줄의 내용, 확인용)") List<Map<String, Object>> edits) {
		if (filePath == null || filePath.isBlank()) {
			return "실패: filePath가 비어 있습니다.";
		}
		if (edits == null || edits.isEmpty()) {
			return "실패: edits가 비어 있어서 바꿀 것이 없습니다.";
		}
		File file = new File(filePath.trim());
		if (!file.isFile()) {
			return CANNOT_READ_MESSAGE + filePath;
		}
		synchronized (EDIT_LOCK) {
			try {
				byte[] bytes = Files.readAllBytes(file.toPath());
				Charset charset = charsetOf(bytes);
				String contents = new String(bytes, charset);
				List<String> problems = new ArrayList<String>();
				String changed = applyLineEdits(contents, edits, problems);
				if (!problems.isEmpty()) {
					StringBuilder message = new StringBuilder("실패: 변경 목록에 맞지 않는 항목이 있어서 아무것도 바꾸지 않았습니다: " + this.toPath(file));
					for (String problem : problems) {
						message.append("\n- ").append(problem);
					}
					return message.toString();
				}
				Files.write(file.toPath(), changed.getBytes(charset), StandardOpenOption.TRUNCATE_EXISTING);
				return "고쳤습니다(" + edits.size() + "곳). 파일은 " + lineCountOf(contents) + "줄에서 " + lineCountOf(changed) + "줄이 됐습니다: " + this.toPath(file)
					+ bracketNotice(contents, changed);
			} catch (Exception e) {
				return "실패: 파일을 고치지 못했습니다(" + e.getClass().getSimpleName() + ": " + e.getMessage() + "): " + filePath;
			}
		}
	}

	/**
	 * <pre>
	 * 줄 번호 변경 목록을 contents에 적용한 결과를 돌려줍니다. 맞지 않는 항목이 있으면 problems에 이유를 담고 contents를 그대로 돌려줍니다.
	 *
	 * 줄 번호가 밀리지 않게 하는 방법: 줄마다 "지울지", "이 줄 자리에 대신 넣을 글", "이 줄 뒤에 넣을 글"을 먼저 표시해 두고,
	 * 마지막에 첫 줄부터 한 번 훑으면서 새 내용을 만듭니다. 그래서 어떤 순서로 적혀 있어도 결과가 같습니다.
	 * </pre>
	 */
	static String applyLineEdits(String contents, List<Map<String, Object>> edits, List<String> problems) {
		boolean crlf = contents.contains("\r\n");
		String lineBreak = crlf ? "\r\n" : "\n";
		String[] lines = contents.split("\r?\n", -1);
		int totalLines = lines.length;
		// 파일이 줄바꿈으로 끝나면 마지막에 빈 조각이 하나 생깁니다. 그것은 줄로 세지 않습니다(readFileLines와 같은 방식).
		boolean endsWithLineBreak = totalLines > 0 && lines[totalLines - 1].isEmpty();
		if (endsWithLineBreak) {
			totalLines--;
		}

		boolean[] removed = new boolean[totalLines + 1];
		String[] replacedBy = new String[totalLines + 1];
		// insertedAfter[0]은 맨 앞에 넣을 글입니다.
		StringBuilder[] insertedAfter = new StringBuilder[totalLines + 1];

		for (int i = 0; i < edits.size(); i++) {
			Map<String, Object> edit = edits.get(i);
			String label = "edits[" + i + "]";
			if (edit == null) {
				problems.add(label + ": 내용이 비어 있습니다.");
				continue;
			}
			String action = textOf(edit.get("action"));
			Integer start = lineNumberOf(edit.get("startLine"));
			Integer end = lineNumberOf(edit.get("endLine"));
			String newText = edit.get("newText") == null ? "" : String.valueOf(edit.get("newText"));
			if (start == null) {
				problems.add(label + ": startLine이 숫자가 아닙니다.");
				continue;
			}

			if (ACTION_INSERT_AFTER.equals(action)) {
				if (start.intValue() < 0 || start.intValue() > totalLines) {
					problems.add(label + ": startLine " + start + "이 파일의 줄 범위(0~" + totalLines + ")를 벗어났습니다.");
					continue;
				}
				if (newText.isEmpty()) {
					problems.add(label + ": insertAfter인데 newText가 비어 있습니다.");
					continue;
				}
				if (start.intValue() > 0 && !sameLine(lines[start.intValue() - 1], textOf(edit.get("startText")))) {
					problems.add(label + ": " + start + "번째 줄은 startText와 다릅니다. 파일의 그 줄: " + lines[start.intValue() - 1].trim());
					continue;
				}
				if (insertedAfter[start.intValue()] == null) {
					insertedAfter[start.intValue()] = new StringBuilder();
				}
				insertedAfter[start.intValue()].append(wholeLines(newText, lineBreak));
				continue;
			}

			if (!ACTION_DELETE.equals(action) && !ACTION_REPLACE.equals(action)) {
				problems.add(label + ": action은 delete, replace, insertAfter 가운데 하나여야 합니다(받은 값: " + action + ").");
				continue;
			}
			if (end == null) {
				end = start;
			}
			if (start.intValue() < 1 || end.intValue() > totalLines || start.intValue() > end.intValue()) {
				problems.add(label + ": " + start + "~" + end + "줄이 파일의 줄 범위(1~" + totalLines + ")를 벗어났습니다.");
				continue;
			}
			// 구간의 가장자리에 있는 빈 줄은 건너뛰고 확인합니다. "뒤따르는 빈 줄까지 지운다"고 구간을 잡은 모델이
			// 확인 글자에는 빈 줄이 아니라 그 안쪽의 글자가 있는 줄(닫는 중괄호 등)을 적기 때문입니다(실제로 그래서 맞는 구간이 거절됐습니다).
			int firstText = start.intValue();
			while (firstText < end.intValue() && lines[firstText - 1].trim().isEmpty()) {
				firstText++;
			}
			int lastText = end.intValue();
			while (lastText > start.intValue() && lines[lastText - 1].trim().isEmpty()) {
				lastText--;
			}
			if (!sameLine(lines[start.intValue() - 1], textOf(edit.get("startText"))) && !sameLine(lines[firstText - 1], textOf(edit.get("startText")))) {
				problems.add(label + ": " + start + "번째 줄은 startText와 다릅니다. 파일의 그 줄: " + lines[firstText - 1].trim());
				continue;
			}
			if (!sameLine(lines[end.intValue() - 1], textOf(edit.get("endText"))) && !sameLine(lines[lastText - 1], textOf(edit.get("endText")))) {
				problems.add(label + ": " + end + "번째 줄은 endText와 다릅니다. 파일의 그 줄: " + lines[lastText - 1].trim());
				continue;
			}
			boolean overlapped = false;
			for (int lineNo = start.intValue(); lineNo <= end.intValue(); lineNo++) {
				if (removed[lineNo]) {
					overlapped = true;
					break;
				}
			}
			if (overlapped) {
				problems.add(label + ": " + start + "~" + end + "줄이 다른 항목의 구간과 겹칩니다.");
				continue;
			}
			for (int lineNo = start.intValue(); lineNo <= end.intValue(); lineNo++) {
				removed[lineNo] = true;
			}
			if (ACTION_REPLACE.equals(action) && !newText.isEmpty()) {
				replacedBy[start.intValue()] = wholeLines(newText, lineBreak);
			}
		}

		// 지우는 구간의 한가운데 뒤에 넣으라는 것은 어디에 넣으라는 뜻인지 알 수 없습니다(구간의 마지막 줄 뒤는 괜찮습니다).
		for (int lineNo = 1; lineNo < totalLines; lineNo++) {
			if (insertedAfter[lineNo] != null && removed[lineNo] && removed[lineNo + 1] && replacedBy[lineNo + 1] == null) {
				problems.add("insertAfter의 startLine " + lineNo + "이 지우거나 바꾸는 구간의 한가운데입니다.");
			}
		}
		if (!problems.isEmpty()) {
			return contents;
		}

		StringBuilder result = new StringBuilder(contents.length() + 256);
		if (insertedAfter[0] != null) {
			result.append(insertedAfter[0]);
		}
		for (int lineNo = 1; lineNo <= totalLines; lineNo++) {
			if (replacedBy[lineNo] != null) {
				result.append(replacedBy[lineNo]);
			}
			if (!removed[lineNo]) {
				result.append(lines[lineNo - 1]);
				// 마지막 줄이 줄바꿈 없이 끝나는 파일이면 그 모습을 지킵니다. 다만 그 뒤에 넣을 글이 있으면 줄바꿈을 넣어야 줄이 붙지 않습니다.
				if (lineNo < totalLines || endsWithLineBreak || insertedAfter[lineNo] != null) {
					result.append(lineBreak);
				}
			}
			if (insertedAfter[lineNo] != null) {
				result.append(insertedAfter[lineNo]);
			}
		}
		return result.toString();
	}

	/**
	 * <pre>
	 * 고친 뒤에 괄호의 짝이 고치기 전과 달라졌으면 그 사실을 알리는 안내를 만듭니다. 달라지지 않았으면 빈 글자입니다.
	 *
	 * 줄 단위로 지우다 보면 지운 줄에 닫는 괄호가 함께 들어 있는 일이 있습니다. 실제로 SQL의
	 *   AND (   title LIKE ...
	 *        OR writer_nm LIKE ...)     ← 이 줄을 지우면서 닫는 괄호가 사라졌습니다
	 * 가 그랬습니다. XML로는 멀쩡하고 컴파일도 하지 않으니 사람이 눈으로 보기 전에는 드러나지 않습니다.
	 *
	 * 세는 방법은 단순합니다. 괄호 종류마다 "여는 것 - 닫는 것"을 고치기 전과 뒤에 세어 견줍니다.
	 * 글자열이나 주석 안의 괄호도 함께 세지만, 전과 후의 차이만 보므로 원래 있던 것은 영향을 주지 않습니다.
	 * 틀릴 수 있는 어림이라 고친 것을 되돌리지는 않고 알리기만 합니다.
	 * </pre>
	 */
	static String bracketNotice(String before, String after) {
		String[][] pairs = { { "(", ")" }, { "{", "}" }, { "[", "]" } };
		StringBuilder changedPairs = new StringBuilder();
		for (String[] pair : pairs) {
			int beforeGap = countOf(before, pair[0]) - countOf(before, pair[1]);
			int afterGap = countOf(after, pair[0]) - countOf(after, pair[1]);
			if (beforeGap == afterGap) {
				continue;
			}
			if (changedPairs.length() > 0) {
				changedPairs.append(", ");
			}
			int diff = afterGap - beforeGap;
			changedPairs.append(diff > 0 ? pair[1] : pair[0]).append(" 가 ").append(Math.abs(diff)).append("개 모자랍니다");
		}
		if (changedPairs.length() == 0) {
			return "";
		}
		return "\n주의: 괄호의 짝이 고치기 전과 달라졌습니다(" + changedPairs + "). 지운 줄에 괄호가 함께 들어 있었는지 사람이 확인해야 합니다.";
	}

	/** newText를 "온전한 줄들"로 만듭니다. 줄바꿈을 파일의 방식으로 맞추고, 끝에 줄바꿈이 없으면 붙입니다(다음 줄과 붙지 않게). */
	static String wholeLines(String newText, String lineBreak) {
		String text = toLineBreak(newText, "\r\n".equals(lineBreak));
		return text.endsWith(lineBreak) ? text : text + lineBreak;
	}

	/**
	 * <pre>
	 * 파일의 한 줄이 모델이 적어 준 확인 글자와 같은지 봅니다. 앞뒤 공백은 보지 않습니다.
	 * 확인 글자가 비어 있으면 확인하지 않습니다. 모델이 "12: 내용"처럼 줄 번호까지 옮겨 적은 것도 같은 것으로 봅니다.
	 * </pre>
	 */
	static boolean sameLine(String fileLine, String expected) {
		if (expected == null || expected.trim().isEmpty()) {
			return true;
		}
		String line = fileLine.trim();
		String text = expected.trim();
		if (line.equals(text)) {
			return true;
		}
		int colon = text.indexOf(':');
		if (colon > 0 && colon <= 6) {
			boolean numberOnly = true;
			for (int i = 0; i < colon; i++) {
				if (!Character.isDigit(text.charAt(i))) {
					numberOnly = false;
					break;
				}
			}
			return numberOnly && line.equals(text.substring(colon + 1).trim());
		}
		return false;
	}

	/** 줄 번호로 온 값을 숫자로 바꿉니다. JSON에서 온 값이라 숫자일 수도, 글자("12")일 수도 있습니다. 숫자가 아니면 null입니다. */
	static Integer lineNumberOf(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return Integer.valueOf(((Number) value).intValue());
		}
		try {
			return Integer.valueOf(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** null이면 빈 글자로 바꿉니다. */
	private static String textOf(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	/** 줄 수를 셉니다(readFileLines와 같은 방식. 끝의 줄바꿈 뒤는 줄로 세지 않습니다). */
	static int lineCountOf(String contents) {
		if (contents.isEmpty()) {
			return 0;
		}
		String[] lines = contents.split("\r?\n", -1);
		return lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
	}

	/** 못 찾았을 때 답에 붙여 주는 "파일의 그 자리" 글자의 최대 줄 수와 최대 글자 수입니다. 길면 대화에 쌓여서 다음 호출마다 다시 보내집니다. */
	private static final int NEAREST_MAX_LINES = 20;
	private static final int NEAREST_MAX_CHARS = 1500;

	/**
	 * <pre>
	 * oldText를 못 찾았을 때, 파일에서 그 자리로 보이는 곳의 글자를 그대로 보여 주는 안내를 만듭니다.
	 *
	 * 못 찾는 까닭은 대개 둘 중 하나입니다.
	 *   1) 들여쓰기나 공백이 파일과 조금 다르다  → 파일의 실제 글자를 보여 주면 모델이 그대로 옮겨 다시 부릅니다.
	 *   2) 앞의 호출이 그 부분을 이미 바꿨다     → "첫 줄이 파일에 없다"고 알려 주면 같은 호출을 멈춥니다.
	 *
	 * 찾는 방법: oldText에서 글자가 있는 첫 줄을 골라, 앞뒤 공백을 뗀 모습이 같은 줄을 파일에서 찾습니다.
	 * 그런 줄이 한 곳이면 거기서부터 oldText의 줄 수만큼 보여 줍니다. 여러 곳이면 줄 번호만 알려 줍니다(어느 곳인지 알 수 없습니다).
	 * </pre>
	 */
	static String nearestText(String contents, String oldText) {
		String firstLine = "";
		String[] oldLines = toLineBreak(oldText, false).split("\n", -1);
		for (String oldLine : oldLines) {
			if (!oldLine.trim().isEmpty()) {
				firstLine = oldLine.trim();
				break;
			}
		}
		if (firstLine.isEmpty()) {
			return "oldText에 글자가 있는 줄이 없습니다. 바꿀 줄의 글자를 포함해 적으세요.";
		}
		String[] fileLines = toLineBreak(contents, false).split("\n", -1);
		List<Integer> found = new ArrayList<Integer>();
		for (int i = 0; i < fileLines.length; i++) {
			if (fileLines[i].trim().equals(firstLine)) {
				found.add(Integer.valueOf(i));
			}
		}
		if (found.isEmpty()) {
			return "oldText의 첫 줄(" + firstLine + ")부터 파일에 없습니다. 앞에서 이미 바꿨거나 지운 부분일 수 있습니다. "
				+ "이미 끝난 변경이면 다음 일로 넘어가고, 아니면 파일에 지금 있는 글자로 oldText를 다시 적으세요.";
		}
		if (found.size() > 1) {
			StringBuilder lineNumbers = new StringBuilder();
			for (Integer index : found) {
				if (lineNumbers.length() > 0) {
					lineNumbers.append(", ");
				}
				lineNumbers.append(index.intValue() + 1);
			}
			return "oldText의 첫 줄(" + firstLine + ")은 파일의 " + lineNumbers + "번째 줄에 있지만 그 뒤가 파일과 다릅니다. 들여쓰기와 공백까지 파일과 같아야 합니다.";
		}
		int start = found.get(0).intValue();
		int count = Math.min(oldLines.length, NEAREST_MAX_LINES);
		StringBuilder actual = new StringBuilder();
		for (int i = start; i < start + count && i < fileLines.length; i++) {
			if (actual.length() + fileLines[i].length() > NEAREST_MAX_CHARS) {
				break;
			}
			actual.append(fileLines[i]).append("\n");
		}
		return "oldText의 첫 줄은 파일의 " + (start + 1) + "번째 줄에 있지만 그 뒤가 파일과 다릅니다. 파일에는 그 줄부터 아래처럼 적혀 있습니다(이 글자를 그대로 써서 oldText를 다시 적으세요).\n"
			+ "-----\n" + actual + "-----";
	}

	/** 글자가 text 안에 겹치지 않게 몇 번 나오는지 셉니다. */
	static int countOf(String text, String part) {
		int count = 0;
		int from = 0;
		while (true) {
			int at = text.indexOf(part, from);
			if (at < 0) {
				return count;
			}
			count++;
			from = at + part.length();
		}
	}

	/** 줄바꿈을 한 가지 방식으로 맞춥니다. crlf가 true면 모두 CRLF로, 아니면 모두 LF로 바꿉니다. */
	static String toLineBreak(String text, boolean crlf) {
		String lf = text.replace("\r\n", "\n");
		return crlf ? lf.replace("\n", "\r\n") : lf;
	}

	/** text의 index 자리가 몇 번째 줄인지 셉니다(1부터). */
	static int lineOf(String text, int index) {
		int line = 1;
		for (int i = 0; i < index && i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	/**
	 * <pre>
	 * 이름 바꿈 표를 [from, to] 목록으로 바꿉니다. 긴 from이 앞에 오게 줄을 세웁니다(길이가 같으면 받은 순서대로).
	 * from이 비었거나, from과 to가 같거나, 앞에 이미 나온 from은 뺍니다.
	 * </pre>
	 */
	static List<String[]> toRules(List<Map<String, String>> replacements) {
		List<String[]> rules = new ArrayList<String[]>();
		if (replacements == null) {
			return rules;
		}
		for (Map<String, String> replacement : replacements) {
			if (replacement == null) {
				continue;
			}
			String from = replacement.get("from");
			String to = replacement.get("to");
			if (from == null || from.isEmpty() || from.equals(to)) {
				continue;
			}
			boolean duplicated = false;
			for (String[] rule : rules) {
				if (rule[0].equals(from)) {
					duplicated = true;
					break;
				}
			}
			if (duplicated) {
				continue;
			}
			// 자기보다 짧은 첫 규칙의 앞에 끼워 넣습니다. 그러면 긴 것이 앞에 오고, 길이가 같은 것은 받은 순서가 지켜집니다.
			int position = rules.size();
			for (int i = 0; i < rules.size(); i++) {
				if (rules.get(i)[0].length() < from.length()) {
					position = i;
					break;
				}
			}
			rules.add(position, new String[] { from, to == null ? "" : to });
		}
		return rules;
	}

	/**
	 * <pre>
	 * text를 앞에서부터 한 번만 훑으면서 규칙을 적용합니다. 자리마다 규칙을 순서대로(긴 것부터) 맞춰 보고,
	 * 맞으면 바꾼 뒤 그 뒤로 건너뜁니다. 그래서 바꾼 결과가 다른 규칙에 다시 걸리지 않습니다.
	 * counts[i]에는 i번째 규칙으로 바꾼 횟수가 담깁니다.
	 * </pre>
	 */
	static String replaceOnePass(String text, List<String[]> rules, int[] counts) {
		if (rules.isEmpty()) {
			return text;
		}
		StringBuilder result = new StringBuilder(text.length() + 64);
		int i = 0;
		while (i < text.length()) {
			boolean replaced = false;
			for (int r = 0; r < rules.size(); r++) {
				String from = rules.get(r)[0];
				if (text.startsWith(from, i)) {
					result.append(rules.get(r)[1]);
					counts[r]++;
					i += from.length();
					replaced = true;
					break;
				}
			}
			if (!replaced) {
				result.append(text.charAt(i));
				i++;
			}
		}
		return result.toString();
	}

	/** 파일의 글자셋을 고릅니다. UTF-8로 깨지지 않고 읽히면 UTF-8, 아니면 MS949(오래된 국내 프로젝트의 EUC-KR 소스)입니다. */
	static Charset charsetOf(byte[] bytes) {
		try {
			StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes));
			return StandardCharsets.UTF_8;
		} catch (CharacterCodingException e) {
			try {
				return Charset.forName("MS949");
			} catch (Exception unsupported) {
				return StandardCharsets.UTF_8;
			}
		}
	}

	@Tool(description = "절대경로 fileFullPath(폴더+파일명)파일이 존재하는지 여부를 boolean 값으로 반환한다. 저장 결과 안내 문구를 돌려준다. 사용자가 '파일 존재' 등을 요청할 때 사용한다.")
	public boolean isFileExist(@ToolParam(description = "저장할 파일의 절대경로(파일명 포함)") String filePath) {
		return net.dstone.common.utils.FileUtil.isFileExist(filePath);
	}


	/**
	 * <pre>
	 * 파일 내용을 읽습니다. 파일이 없으면 읽으려 하지 않고 바로 null을 돌려줍니다.
	 *
	 * 모델은 경로를 짐작해서 열어 보는 일이 잦습니다. 공통 FileUtil은 없는 파일을 열면 오류 내용을 통째로 찍기 때문에,
	 * 그때마다 로그에 90줄짜리 오류가 남아서 진짜 오류처럼 보였습니다. 없는 파일은 오류가 아니라 "없다"는 답입니다.
	 * </pre>
	 */
	private String readOrNull(String fileFullPath) {
		if (fileFullPath == null || fileFullPath.isBlank() || !new File(fileFullPath.trim()).isFile()) {
			return null;
		}
		return net.dstone.common.utils.FileUtil.readFile(fileFullPath.trim());
	}

	/** 경로가 실제로 있는 디렉토리면 File로, 아니면 null로 돌려줍니다. */
	private File toDirectory(String basePath) {
		if (basePath == null || basePath.isBlank()) {
			return null;
		}
		File base = new File(basePath.trim());
		if (!base.isDirectory()) {
			return null;
		}
		return base;
	}

	/**
	 * dir 아래의 파일을 하위 폴더까지 따라 내려가며 모읍니다.
	 * 제외 폴더(.git 등)는 아예 들어가지 않고, 심볼릭 링크도 따라가지 않습니다(같은 곳을 맴도는 것을 막기 위해).
	 * 이름순으로 정렬해서 모으기 때문에, 상한에 걸려 앞부분만 줄 때도 매번 같은 결과가 나옵니다.
	 */
	private void collectFiles(File dir, List<String> excludeDirs, List<File> collected) {
		File[] children = dir.listFiles();
		// 권한이 없어 못 읽는 폴더는 그냥 지나갑니다.
		if (children == null) {
			return;
		}
		Arrays.sort(children);
		for (File child : children) {
			if (Files.isSymbolicLink(child.toPath())) {
				continue;
			}
			if (child.isDirectory()) {
				if (!excludeDirs.contains(child.getName())) {
					this.collectFiles(child, excludeDirs, collected);
				}
			} else if (child.isFile()) {
				collected.add(child);
			}
		}
	}

	/** 모델에게 보여줄 경로입니다. Windows에서도 '/'로 통일합니다(JSON에서 '\'가 이스케이프되어 길어지는 것을 피합니다). */
	private String toPath(File file) {
		return file.getAbsolutePath().replace('\\', '/');
	}

	/** "*.java,*.xml" 같은 파일명 패턴을 정규식 목록으로 바꿉니다. 비어 있으면 빈 목록(= 모든 파일)입니다. */
	private List<Pattern> toNamePatterns(String fileNamePattern) {
		List<Pattern> patterns = new ArrayList<>();
		if (fileNamePattern == null || fileNamePattern.isBlank()) {
			return patterns;
		}
		for (String part : fileNamePattern.split(",")) {
			String glob = part.trim();
			if (glob.isEmpty()) {
				continue;
			}
			// '*'와 '?'만 와일드카드로 보고, 나머지 글자는 글자 그대로 비교합니다.
			StringBuilder regex = new StringBuilder();
			for (int i = 0; i < glob.length(); i++) {
				char c = glob.charAt(i);
				if (c == '*') {
					regex.append(".*");
				} else if (c == '?') {
					regex.append('.');
				} else {
					regex.append(Pattern.quote(String.valueOf(c)));
				}
			}
			patterns.add(Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE));
		}
		return patterns;
	}

	/** 파일명이 패턴 중 하나에 맞는지 봅니다. 패턴이 없으면 모든 파일이 대상입니다. */
	private boolean matchesName(String fileName, List<Pattern> patterns) {
		if (patterns.isEmpty()) {
			return true;
		}
		for (Pattern pattern : patterns) {
			if (pattern.matcher(fileName).matches()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 검색용으로 파일을 글자로 읽습니다. 바이너리 파일(이미지, class, jar 등)이거나 읽지 못하면 null입니다.
	 * 먼저 UTF-8로 읽어 보고, 깨지면 MS949(오래된 국내 프로젝트의 EUC-KR 소스)로 다시 읽습니다.
	 */
	private String readText(File file) {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(file.toPath());
		} catch (Exception e) {
			return null;
		}
		// 앞부분에 0 바이트가 있으면 글자 파일이 아닌 것으로 봅니다.
		int probe = Math.min(bytes.length, 4096);
		for (int i = 0; i < probe; i++) {
			if (bytes[i] == 0) {
				return null;
			}
		}
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			try {
				return new String(bytes, Charset.forName("MS949"));
			} catch (Exception unsupported) {
				return new String(bytes, StandardCharsets.UTF_8);
			}
		}
	}

	/** 검색 결과의 한 줄이 너무 길면 앞부분만 남깁니다. */
	private String shorten(String line) {
		if (line.length() <= MAX_MATCH_LINE_CHARS) {
			return line;
		}
		return line.substring(0, MAX_MATCH_LINE_CHARS) + "...";
	}

	/** 목록/검색에서 뺄 폴더 이름들입니다(dstone.ai.tool.file.exclude-dirs, 콤마 구분. 없으면 기본값). */
	private List<String> excludeDirs() {
		String value = this.environment.getProperty(Constants.Tool.File.PREFIX + ".exclude-dirs");
		if (value == null || value.isBlank()) {
			value = DEFAULT_EXCLUDE_DIRS;
		}
		List<String> dirs = new ArrayList<>();
		for (String part : value.split(",")) {
			if (!part.trim().isEmpty()) {
				dirs.add(part.trim());
			}
		}
		return dirs;
	}

	/** dstone.ai.tool.file.{name} 숫자 설정을 읽습니다. 없거나 0 이하면 기본값을 씁니다. */
	private int intProperty(String name, int defaultValue) {
		Integer value = this.environment.getProperty(Constants.Tool.File.PREFIX + "." + name, Integer.class);
		if (value == null || value.intValue() <= 0) {
			return defaultValue;
		}
		return value.intValue();
	}

}
