package net.dstone.ai.agent.tool.sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;

import net.dstone.ai.agent.tool.AiTool;

/**
 * Tool 등록 방법을 보여주는 샘플(dstone-boot의 sample/ 패키지와 같은 성격 - 실제 SI 프로젝트에서는
 * 지우거나 자기 도메인 Tool로 바꿔도 된다). LLM은 학습 시점 이후의 "지금"을 알 방법이 없어서
 * 날짜/시간 질문은 function calling을 보여주기에 가장 흔히 쓰이는 예시다.
 */
@AiTool
public class DateTimeTools {

	@Tool(description = "현재 날짜와 시간을 ISO-8601 형식(yyyy-MM-ddTHH:mm:ss)으로 반환한다. "
			+ "사용자가 '오늘', '지금 몇 시', '현재 날짜/시간' 등을 물어볼 때 사용한다.")
	public String getCurrentDateTime() {
		return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

}
