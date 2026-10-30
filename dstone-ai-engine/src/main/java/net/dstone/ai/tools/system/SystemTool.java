package net.dstone.ai.tools.system;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import net.dstone.ai.common.annotation.AiTool;

@AiTool
public class SystemTool {
	@Tool(description = "시스템 프로퍼티명을 받아서 시스템 프로퍼티값을 반환한다.")
	public String getSystemProperty(@ToolParam(description = "시스템 프로퍼티명") String systemPropertyName) {
		return net.dstone.common.utils.SystemUtil.getSystemProperty(systemPropertyName);
	}
}
