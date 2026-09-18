package net.dstone.ai.tools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import net.dstone.ai.common.exec.ExternalProcessRunner;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * ShellExecTool/PythonExecTool이 공유하는 "화이트리스트에서 스크립트 경로를 찾고, ExternalProcessRunner로 실행하고, 감사 로그를 남긴다"
 * 골격이다. 두 Tool의 차이는 화이트리스트 설정 키 접미사(.allowed-commands vs .allowed-scripts)와 실행 커맨드 앞에 붙는 것(Shell은
 * 없음, Python은 python 바이너리)뿐이라, 그 세 가지만 하위 클래스가 정하면 된다.
 */
public abstract class ExternalProcessTool extends BaseObject {

	@Autowired
	protected ConfigProperty configProperty;

	/** 이 Tool의 설정 키 prefix(예: dstone.ai.tool.shell). */
	protected abstract String configPrefix();

	/** 화이트리스트 설정 키의 접미사(.allowed-commands 또는 .allowed-scripts). */
	protected abstract String allowlistSuffix();

	/** 감사 로그 한 줄에 남길 짧은 이름(예: "shell", "python"). */
	protected abstract String auditLabel();

	/**
	 * <pre>
	 * scriptName을 화이트리스트에서 찾아 commandPrefix + 경로 + args 순서로 실행한다. 화이트리스트에 없으면 아예
	 * 실행하지 않고 실패 문구만 돌려준다.
	 * </pre>
	 *
	 * @param scriptName    화이트리스트에 등록된 이름
	 * @param commandPrefix 스크립트 경로 앞에 붙일 실행 파일(Shell은 빈 목록, Python은 python 바이너리 1개)
	 * @param args          스크립트에 전달할 인자 목록(없으면 null 가능)
	 */
	protected String runWhitelisted(String scriptName, List<String> commandPrefix, List<String> args) {
		String scriptPath = this.resolvePath(scriptName);
		if (scriptPath == null) {
			return "실패: 화이트리스트에 없는 스크립트입니다: " + scriptName;
		}
		List<String> command = new ArrayList<>(commandPrefix);
		command.add(scriptPath);
		if (args != null) {
			command.addAll(args);
		}
		String result = ExternalProcessRunner.run(command, this.timeout(), this.maxOutputChars());
		LogUtil.sysout("dstone-ai-engine tool-audit: " + this.auditLabel() + " scriptName=" + scriptName + " path=" + scriptPath + " args=" + args + " -> " + result);
		return result;
	}

	/** @param scriptName 경로를 찾을 스크립트의 등록된 이름 */
	@SuppressWarnings("rawtypes")
	private String resolvePath(String scriptName) {
		List allowed = this.configProperty.getListProperty(this.configPrefix() + this.allowlistSuffix());
		for (Object entry : allowed) {
			Map map = (Map) entry;
			if (scriptName.equals(String.valueOf(map.get("name")))) {
				return String.valueOf(map.get("path"));
			}
		}
		return null;
	}

	private Duration timeout() {
		String seconds = this.configProperty.getProperty(this.configPrefix() + ".timeout-seconds");
		return Duration.ofSeconds(StringUtil.isEmpty(seconds) ? 10 : Long.parseLong(seconds));
	}

	private int maxOutputChars() {
		String chars = this.configProperty.getProperty(this.configPrefix() + ".max-output-chars");
		return StringUtil.isEmpty(chars) ? 4000 : Integer.parseInt(chars);
	}

}
