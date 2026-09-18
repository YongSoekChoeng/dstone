package net.dstone.ai.tools.shell;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.tools.ExternalProcessTool;

/**
 * 사전에 화이트리스트로 등록된 쉘 스크립트만 실행할 수 있는 Tool이다. LLM은 스크립트를 고를 이름(scriptName)과 인자만 줄 수 있고, 실제로 실행되는 실행 파일 경로는 항상 설정에 미리 등록된
 * 값이다 - 임의의 셸 명령 문자열을 통째로 받아 실행하는 방식은 만들지 않았다. 별도의 on/off 플래그는 없다 - allowed-commands를 아무것도 등록하지 않으면(기본값) 어떤 scriptName을
 * 줘도 화이트리스트에서 못 찾아 항상 실패하므로, 설정을 안 하는 것 자체가 곧 비활성 상태다.
 */
@AiTool
public class ShellExecTool extends ExternalProcessTool {

	@Override
	protected String configPrefix() {
		return Constants.Tool.Shell.PREFIX;
	}

	@Override
	protected String allowlistSuffix() {
		return ".allowed-commands";
	}

	@Override
	protected String auditLabel() {
		return "shell";
	}

	/**
	 * @param scriptName 실행할 스크립트의 등록된 이름(화이트리스트에 등록된 이름과 정확히 일치해야 함)
	 * @param args       스크립트에 전달할 인자 목록(필요 없으면 빈 배열)
	 */
	@Tool(description = "사전에 허용된 셸 스크립트 하나를 실행한다. scriptName은 관리자가 미리 등록해둔 이름과 정확히 일치해야 하며, 등록되지 않은 이름이나 임의의 셸 명령 문자열은 실행할 수 없다.")
	public String runShellScript(@ToolParam(description = "실행할 스크립트의 등록된 이름") String scriptName, @ToolParam(description = "스크립트에 전달할 인자 목록(필요 없으면 빈 배열)", required = false) List<String> args) {
		return this.runWhitelisted(scriptName, List.of(), args);
	}

}
