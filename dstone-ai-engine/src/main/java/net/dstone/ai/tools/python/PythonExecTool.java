package net.dstone.ai.tools.python;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.tools.ExternalProcessTool;
import net.dstone.common.utils.StringUtil;

/**
 * ShellExecTool과 같은 원칙의 Python 버전이다: allowed-scripts 화이트리스트에 등록된 .py 파일만
 * `python3 <경로> <인자...>`로 실행한다. LLM이 임의 코드 문자열을 만들어 `python3 -c`로 실행시키는 방식은 만들지 않았다 - 항상 화이트리스트에 있는 스크립트 "파일"만 실행 대상이
 * 된다. ShellExecTool과 마찬가지로 별도의 on/off 플래그는 없다 - allowed-scripts를 비워두면(기본값) 곧 비활성 상태다.
 */
@AiTool
public class PythonExecTool extends ExternalProcessTool {

	@Override
	protected String configPrefix() {
		return Constants.Tool.Python.PREFIX;
	}

	@Override
	protected String allowlistSuffix() {
		return ".allowed-scripts";
	}

	@Override
	protected String auditLabel() {
		return "python";
	}

	/**
	 * @param scriptName 실행할 스크립트의 등록된 이름(화이트리스트에 등록된 이름과 정확히 일치해야 함)
	 * @param args       스크립트에 전달할 인자 목록(필요 없으면 빈 배열)
	 */
	@Tool(description = "사전에 허용된 Python 스크립트 하나를 실행한다. scriptName은 관리자가 미리 등록해둔 이름과 정확히 일치해야 하며, 등록되지 않은 이름이나 임의의 Python 코드 문자열은 실행할 수 없다.")
	public String runPythonScript(@ToolParam(description = "실행할 스크립트의 등록된 이름") String scriptName, @ToolParam(description = "스크립트에 전달할 인자 목록(필요 없으면 빈 배열)", required = false) List<String> args) {
		String pythonBin = this.configProperty.getProperty(Constants.Tool.Python.PREFIX + ".python-bin");
		return this.runWhitelisted(scriptName, List.of(StringUtil.isEmpty(pythonBin) ? "python3" : pythonBin), args);
	}

}
