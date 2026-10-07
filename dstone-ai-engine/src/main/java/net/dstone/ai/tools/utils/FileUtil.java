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

	@Tool(description = "절대경로 fileFullPath의 파일을 읽어서 파일내용을 스트링형식으로 반환한다. 사용자가 '파일 읽기' 등을 요청할 때 사용한다. "
		+ "파일이 크면 앞부분만 반환하고 잘렸다는 안내를 붙인다. 로그처럼 끝부분이 중요한 큰 파일은 readFileTail을 사용한다. 같은 파일을 다시 읽지 마라.")
	public String readFile(@ToolParam String fileFullPath) {
		String contents = this.readOrNull(fileFullPath);
		if (contents == null) {
			return "파일을 읽을 수 없습니다(존재하지 않거나 읽기 권한이 없음): " + fileFullPath;
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
			return "파일을 읽을 수 없습니다(존재하지 않거나 읽기 권한이 없음): " + fileFullPath;
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
			return "파일을 읽을 수 없습니다(존재하지 않거나 읽기 권한이 없음): " + fileFullPath;
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

	@Tool(description = "절대경로 fileFullPath(폴더+파일명)에 fileContents 내용으로 파일을 저장한다. 폴더가 없으면 만들고, 파일이 있으면 덮어쓴다. 저장 결과 안내 문구를 돌려준다. 사용자가 '파일 생성' 등을 요청할 때 사용한다.")
	public void writeFile(@ToolParam(description = "저장할 파일의 절대경로(파일명 포함)") String filePath, @ToolParam (description = "파일 내용")String fileContents) {
		String fileParentPath = net.dstone.common.utils.FileUtil.getFilePath(filePath);
		String fileName = net.dstone.common.utils.FileUtil.getFileName(filePath, true);
		net.dstone.common.utils.FileUtil.writeFile(fileParentPath, fileName, fileContents);
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
			Files.write(file.toPath(), contents.getBytes(Charset.defaultCharset()), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			String saved = new String(Files.readAllBytes(file.toPath()), Charset.defaultCharset());
			return "이어 붙였습니다. 지금 파일은 전체 " + saved.length() + "자입니다: " + this.toPath(file);
		} catch (Exception e) {
			return "실패: 파일에 이어 붙이지 못했습니다(" + e.getClass().getSimpleName() + ": " + e.getMessage() + "): " + filePath;
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
