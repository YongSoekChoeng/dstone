package net.dstone.ai.tools.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import net.dstone.ai.common.annotation.AiTool;

@AiTool
public class FileUtil {

	@Tool(description = "basePath 디렉토리와 하위디렉토리의 파일목록을 스트링배열로 반환한다. 하위디렉토리는 재귀적으로 검색/조회해서 모든 파일목록을 절대경로로 반환한다. 사용자가 '파일목록 읽기' 등을 요청할 때 사용한다.")
	public String[] readFileListAll(@ToolParam String basePath) {
		
		return net.dstone.common.utils.FileUtil.readFileListAll(basePath);
	}

	@Tool(description = "절대경로 filePath의 파일을 읽어서 파일내용을 스트링형식으로 반환한다. 사용자가 '파일 읽기' 등을 요청할 때 사용한다.")
	public String readFile(@ToolParam String fileFullPath) {
		return net.dstone.common.utils.FileUtil.readFile(fileFullPath);
	}

	@Tool(description = "절대경로 filePath의 파일을 삭제한다. 사용자가 '파일 삭제' 등을 요청할 때 사용한다.")
	public void deleteFile(@ToolParam String fileFullPath) {
		net.dstone.common.utils.FileUtil.deleteFile(fileFullPath);
	}

	@Tool(description = "절대경로 filePath에 strFileName 이름으로 strContents 내용의 파일을 생성한다. 사용자가 '파일 생성' 등을 요청할 때 사용한다.")
	public void writeFile(@ToolParam String filePath, @ToolParam String fileName, @ToolParam String fileContents) {
		net.dstone.common.utils.FileUtil.writeFile(filePath, fileName, fileContents);
	}

}
