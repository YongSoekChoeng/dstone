package net.dstone.ai.tools.shell;

import java.io.File;
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
	 * <pre>
	 * 확장자 없이 등록한 경로면 지금 OS에 맞는 스크립트를 찾아 씁니다.
	 * Windows는 경로.bat, 경로.cmd 순서로, 그 밖(리눅스, WSL, k8s)은 경로.sh를 찾습니다.
	 * 그래서 같은 이름 하나로 Windows와 리눅스에서 각자 맞는 스크립트가 실행됩니다(예: scripts/sm/compile-check).
	 * 등록한 경로에 파일이 그대로 있거나 이미 확장자가 있으면 손대지 않습니다. 맞는 파일이 없어도 등록한 경로를 그대로 돌려주어,
	 * 실행 단계에서 "프로세스를 시작하지 못했습니다"로 실패하게 둡니다.
	 * </pre>
	 *
	 * @param path 설정에 등록된 경로
	 */
	@Override
	protected String platformPath(String path) {
		File registered = new File(path);
		if (registered.isFile() || registered.getName().contains(".")) {
			return path;
		}
		boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
		String[] extensions = windows ? new String[] { ".bat", ".cmd" } : new String[] { ".sh" };
		for (String extension : extensions) {
			if (new File(path + extension).isFile()) {
				return path + extension;
			}
		}
		return path;
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
