package net.dstone.ai.tools.shell;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.tools.ExternalProcessTool;

/**
 * 미리 화이트리스트로 등록해 둔 셸 스크립트만 실행할 수 있는 Tool입니다. LLM은 실행할 스크립트를
 * 고르는 이름(scriptName)과 인자만 줄 수 있을 뿐, 실제로 실행되는 파일 경로는 항상 설정에 미리
 * 등록해 둔 값을 씁니다 - 임의의 셸 명령 문자열을 통째로 받아서 그대로 실행하는 방식은 일부러
 * 만들지 않았습니다. 이 Tool도 따로 켜고 끄는 on/off 플래그가 없습니다 - allowed-commands에
 * 아무것도 등록하지 않으면(기본값) 어떤 scriptName을 줘도 화이트리스트에서 찾지 못해 항상
 * 실패로 끝나기 때문에, 설정을 비워두는 것 자체가 곧 "이 Tool을 끈 상태"가 됩니다.
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
	 * @param scriptName 실행할 스크립트의 등록된 이름입니다(화이트리스트에 등록된 이름과 정확히 같아야 합니다).
	 * @param args       스크립트에 넘겨줄 인자 목록입니다(필요 없으면 빈 배열로 주면 됩니다).
	 */
	@Tool(description = "사전에 허용된 셸 스크립트 하나를 실행한다. scriptName은 관리자가 미리 등록해둔 이름과 정확히 일치해야 하며, 등록되지 않은 이름이나 임의의 셸 명령 문자열은 실행할 수 없다.")
	public String runShellScript(@ToolParam(description = "실행할 스크립트의 등록된 이름") String scriptName, @ToolParam(description = "스크립트에 전달할 인자 목록(필요 없으면 빈 배열)", required = false) List<String> args) {
		return this.runWhitelisted(scriptName, List.of(), args);
	}

}
