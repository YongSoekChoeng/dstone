package net.dstone.ai.tools.python;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.exec.ExternalProcessRunner;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * ShellExecTool과 같은 원칙의 Python 버전이다: dstone.ai.tool.python.enabled(기본 false)로 옵트인, allowed-scripts 화이트리스트에 등록된 .py 파일만
 * `python3 <경로> <인자...>`로 실행한다. LLM이 임의 코드 문자열을 만들어 `python3 -c`로 실행시키는 방식은 만들지 않았다 - 항상 화이트리스트에 있는 스크립트 "파일"만 실행 대상이
 * 된다.
 */
@AiTool
public class PythonExecTool extends BaseObject {

	@Autowired
	private ConfigProperty configProperty;

	/**
	 * @param scriptName 실행할 스크립트의 등록된 이름(화이트리스트에 등록된 이름과 정확히 일치해야 함)
	 * @param args       스크립트에 전달할 인자 목록(필요 없으면 빈 배열)
	 */
	@Tool(description = "사전에 허용된 Python 스크립트 하나를 실행한다. scriptName은 관리자가 미리 등록해둔 이름과 정확히 일치해야 하며, 등록되지 않은 이름이나 임의의 Python 코드 문자열은 실행할 수 없다.")
	public String runPythonScript(@ToolParam(description = "실행할 스크립트의 등록된 이름") String scriptName, @ToolParam(description = "스크립트에 전달할 인자 목록(필요 없으면 빈 배열)", required = false) List<String> args) {
		String scriptPath = this.resolvePath(scriptName);
		if (scriptPath == null) {
			return "실패: 화이트리스트에 없는 스크립트입니다: " + scriptName;
		}
		String pythonBin = this.configProperty.getProperty(Constants.Tool.Python.PREFIX + ".python-bin");
		List<String> command = new ArrayList<>();
		command.add(StringUtil.isEmpty(pythonBin) ? "python3" : pythonBin);
		command.add(scriptPath);
		if (args != null) {
			command.addAll(args);
		}
		String result = ExternalProcessRunner.run(command, this.timeout(), this.maxOutputChars());
		LogUtil.sysout("dstone-ai-engine tool-audit: python scriptName=" + scriptName + " path=" + scriptPath + " args=" + args + " -> " + result);
		return result;
	}

	@SuppressWarnings("rawtypes")
	/**
	 * @param scriptName 경로를 찾을 스크립트의 등록된 이름
	 */
	private String resolvePath(String scriptName) {
		List allowed = this.configProperty.getListProperty(Constants.Tool.Python.PREFIX + ".allowed-scripts");
		for (Object entry : allowed) {
			Map map = (Map) entry;
			if (scriptName.equals(String.valueOf(map.get("name")))) {
				return String.valueOf(map.get("path"));
			}
		}
		return null;
	}

	private Duration timeout() {
		String seconds = this.configProperty.getProperty(Constants.Tool.Python.PREFIX + ".timeout-seconds");
		return Duration.ofSeconds(StringUtil.isEmpty(seconds) ? 10 : Long.parseLong(seconds));
	}

	private int maxOutputChars() {
		String chars = this.configProperty.getProperty(Constants.Tool.Python.PREFIX + ".max-output-chars");
		return StringUtil.isEmpty(chars) ? 4000 : Integer.parseInt(chars);
	}

}
