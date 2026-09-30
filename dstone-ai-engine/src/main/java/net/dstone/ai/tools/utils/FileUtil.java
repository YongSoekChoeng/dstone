package net.dstone.ai.tools.utils;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;

/**
 * 파일 목록/읽기/쓰기/삭제 Tool입니다.
 *
 * 파일 읽기는 한 번에 돌려주는 글자 수에 상한(dstone.ai.tool.file.max-read-chars)을 둡니다.
 * Tool 결과는 그대로 대화 이력에 쌓여 다음 LLM 호출마다 다시 보내지기 때문에, 큰 로그 파일을
 * 통째로 돌려주면 금방 모델의 컨텍스트 한도를 넘습니다(실제로 232KB error.log를 두 번 읽고
 * "400: Provider returned error"로 실패했습니다). 그래서 앞부분(readFile)이나 끝부분(readFileTail)만
 * 잘라서 주고, 잘렸다는 사실을 결과에 적어 모델이 알 수 있게 합니다.
 */
@AiTool
public class FileUtil {

	/** 설정이 없을 때 쓰는 기본 상한입니다(대략 1만 토큰 안팎). */
	private static final int DEFAULT_MAX_READ_CHARS = 30000;

	@Autowired
	private Environment environment;

	@Tool(description = "basePath 디렉토리와 하위디렉토리의 파일목록을 스트링배열로 반환한다. 하위디렉토리는 재귀적으로 검색/조회해서 모든 파일목록을 절대경로로 반환한다. 사용자가 '파일목록 읽기' 등을 요청할 때 사용한다.")
	public String[] readFileListAll(@ToolParam String basePath) {
		String[] result = net.dstone.common.utils.FileUtil.readFileListAll(basePath);
		// 읽기 실패(권한 등)로 null이 오면 모델에게는 빈 목록으로 알려줍니다.
		if (result == null) {
			return new String[0];
		}
		return result;
	}

	@Tool(description = "절대경로 fileFullPath의 파일을 읽어서 파일내용을 스트링형식으로 반환한다. 사용자가 '파일 읽기' 등을 요청할 때 사용한다. "
		+ "파일이 크면 앞부분만 반환하고 잘렸다는 안내를 붙인다. 로그처럼 끝부분이 중요한 큰 파일은 readFileTail을 사용한다. 같은 파일을 다시 읽지 마라.")
	public String readFile(@ToolParam String fileFullPath) {
		String contents = net.dstone.common.utils.FileUtil.readFile(fileFullPath);
		if (contents == null) {
			return "파일을 읽을 수 없습니다(존재하지 않거나 읽기 권한이 없음): " + fileFullPath;
		}
		int maxChars = this.maxReadChars();
		if (contents.length() <= maxChars) {
			return contents;
		}
		return contents.substring(0, maxChars)
			+ "\n\n...(파일이 커서 전체 " + contents.length() + "자 중 앞 " + maxChars + "자만 반환했습니다. 끝부분이 필요하면 readFileTail을 사용하세요.)";
	}

	@Tool(description = "절대경로 fileFullPath 파일의 끝부분만 읽어서 반환한다. 로그 파일처럼 크고 최근 내용(끝부분)이 중요한 파일을 읽을 때 사용한다.")
	public String readFileTail(@ToolParam String fileFullPath) {
		String contents = net.dstone.common.utils.FileUtil.readFile(fileFullPath);
		if (contents == null) {
			return "파일을 읽을 수 없습니다(존재하지 않거나 읽기 권한이 없음): " + fileFullPath;
		}
		int maxChars = this.maxReadChars();
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

	/** 한 번에 돌려줄 최대 글자 수입니다(dstone.ai.tool.file.max-read-chars, 없으면 기본값). */
	private int maxReadChars() {
		Integer value = this.environment.getProperty(Constants.Tool.File.PREFIX + ".max-read-chars", Integer.class);
		if (value == null || value.intValue() <= 0) {
			return DEFAULT_MAX_READ_CHARS;
		}
		return value.intValue();
	}

}
