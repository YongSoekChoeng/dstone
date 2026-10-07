package net.dstone.ai.tools.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;

import net.dstone.ai.common.annotation.AiTool;

/**
 * Tool을 어떻게 등록하는지 보여주기 위한 샘플입니다(dstone-boot의 sample/ 패키지와 같은 성격입니다 -
 * 실제 SI 프로젝트에서는 이 샘플을 지우고 자기 도메인에 맞는 Tool로 바꿔서 써도 됩니다).
 */
@AiTool
public class DateTimeTool {

	@Tool(description = "현재 날짜와 시간을 ISO-8601 형식(yyyy-MM-ddTHH:mm:ss)으로 반환한다. 사용자가 '오늘 며칠', '날짜', '지금 몇 시', '현재 날짜/시간' 등을 물어볼 때 사용한다.")
	public String getCurrentDateTime() {
		return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

}
