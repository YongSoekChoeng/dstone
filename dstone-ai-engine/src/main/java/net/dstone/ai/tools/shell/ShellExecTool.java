package net.dstone.ai.tools.shell;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.exec.ExternalProcessRunner;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * 사전에 화이트리스트로 등록된 셸 스크립트만 실행할 수 있는 Tool이다. dstone.ai.tool.shell.enabled
 * (기본 false)가 꺼져 있으면 이 빈 자체가 뜨지 않고, 화이트리스트(allowed-commands)가 비어있으면
 * 켜져 있어도 호출할 게 없다.
 *
 * LLM은 스크립트를 고를 이름(scriptName)과 인자만 줄 수 있고, 실제로 실행되는 실행 파일 경로는 항상
 * 설정에 미리 등록된 값이다 - 임의의 셸 명령 문자열을 통째로 받아 실행하는 방식은 만들지 않았다
 * (common.exec.ExternalProcessRunner가 셸을 거치지 않아 인자에 셸 메타문자가 있어도 명령 주입으로
 * 이어지지 않는다).
 */
@AiTool
@ConditionalOnProperty(name = "dstone.ai.tool.shell.enabled", havingValue = "true")
public class ShellExecTool extends BaseObject {

	private static final String PREFIX = "dstone.ai.tool.shell";

	@Autowired
	private ConfigProperty configProperty;

	@Tool(description = "사전에 허용된 셸 스크립트 하나를 실행한다. scriptName은 관리자가 미리 등록해둔 이름과 정확히 "
			+ "일치해야 하며, 등록되지 않은 이름이나 임의의 셸 명령 문자열은 실행할 수 없다.")
	public String runShellScript(
			@ToolParam(description = "실행할 스크립트의 등록된 이름") String scriptName,
			@ToolParam(description = "스크립트에 전달할 인자 목록(필요 없으면 빈 배열)", required = false) List<String> args) {
		String scriptPath = this.resolvePath(scriptName);
		if (scriptPath == null) {
			return "실패: 화이트리스트에 없는 스크립트입니다: " + scriptName;
		}
		List<String> command = new ArrayList<>();
		command.add(scriptPath);
		if (args != null) {
			command.addAll(args);
		}
		String result = ExternalProcessRunner.run(command, this.timeout(), this.maxOutputChars());
		LogUtil.sysout("dstone-ai-engine tool-audit: shell scriptName=" + scriptName + " path=" + scriptPath + " args="
			+ args + " -> " + result);
		return result;
	}

	@SuppressWarnings("rawtypes")
	private String resolvePath(String scriptName) {
		List allowed = this.configProperty.getListProperty(PREFIX + ".allowed-commands");
		for (Object entry : allowed) {
			Map map = (Map) entry;
			if (scriptName.equals(String.valueOf(map.get("name")))) {
				return String.valueOf(map.get("path"));
			}
		}
		return null;
	}

	private Duration timeout() {
		String seconds = this.configProperty.getProperty(PREFIX + ".timeout-seconds");
		return Duration.ofSeconds(StringUtil.isEmpty(seconds) ? 10 : Long.parseLong(seconds));
	}

	private int maxOutputChars() {
		String chars = this.configProperty.getProperty(PREFIX + ".max-output-chars");
		return StringUtil.isEmpty(chars) ? 4000 : Integer.parseInt(chars);
	}

}
