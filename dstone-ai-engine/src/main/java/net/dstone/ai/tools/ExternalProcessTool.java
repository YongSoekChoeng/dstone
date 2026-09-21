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
 * ShellExecTool과 PythonExecTool이 공통으로 쓰는 뼈대 클래스입니다. 두 Tool 모두 하는 일은 똑같습니다:
 * 화이트리스트에서 스크립트 경로를 찾고, ExternalProcessRunner로 실제 실행한 뒤, 감사 로그를 남기는 것입니다.
 *
 * 두 Tool의 차이는 딱 세 가지뿐입니다 - 화이트리스트 설정 키의 접미사(.allowed-commands인지
 * .allowed-scripts인지), 실행 커맨드 앞에 무엇을 붙일지(Shell은 아무것도 안 붙이고, Python은 python
 * 바이너리를 붙입니다), 그리고 감사 로그에 남길 이름입니다. 그래서 하위 클래스는 이 세 가지만 정해주면
 * 나머지 실행 로직은 그대로 물려받아 쓸 수 있습니다.
 */
public abstract class ExternalProcessTool extends BaseObject {

	@Autowired
	protected ConfigProperty configProperty;

	/** 이 Tool이 쓰는 설정 키의 접두사입니다(예: dstone.ai.tool.shell). */
	protected abstract String configPrefix();

	/** 화이트리스트 설정 키에 붙는 접미사입니다(.allowed-commands 또는 .allowed-scripts 중 하나). */
	protected abstract String allowlistSuffix();

	/** 감사 로그 한 줄에 남길 짧은 이름입니다(예: "shell", "python"). */
	protected abstract String auditLabel();

	/**
	 * <pre>
	 * scriptName이 화이트리스트에 있는지 먼저 찾아보고, 있으면 commandPrefix + 스크립트 경로 + args
	 * 순서로 이어 붙여서 실제로 실행합니다. 화이트리스트에 없는 이름이면 아예 실행하지 않고 실패했다는
	 * 문구만 돌려줍니다.
	 * </pre>
	 *
	 * @param scriptName    화이트리스트에 등록되어 있는 이름입니다.
	 * @param commandPrefix 스크립트 경로 앞에 붙일 실행 파일입니다(Shell은 빈 목록, Python은 python 바이너리 1개).
	 * @param args          스크립트에 넘겨줄 인자 목록입니다(없으면 null이어도 됩니다).
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

	/** @param scriptName 경로를 찾아볼 스크립트의 등록된 이름입니다. */
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
