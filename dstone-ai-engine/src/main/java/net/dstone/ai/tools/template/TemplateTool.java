package net.dstone.ai.tools.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.tools.file.FileTool;

/**
 * <pre>
 * 데이터를 문서 틀에 끼워 문서를 만드는 Tool입니다. 어느 Workflow에서나 TOOL step으로 부릅니다.
 *
 * 틀은 resources/definitions/templates/ 아래에 파일로 둡니다(프롬프트, 스키마와 같은 방식).
 * 문서의 모양을 바꾸려면 그 파일만 고치면 됩니다. 자바를 고칠 일이 없습니다.
 * 틀의 문법은 TemplateRenderer의 설명을 보십시오(값 넣기, 되풀이, 있으면/없으면 뿐입니다).
 *
 * YAML 예:
 *   - id: writeReport
 *     type: TOOL
 *     tool: renderTemplate
 *     input:
 *       template: templates/내workflow/report.md.mustache
 *       data:
 *         analysis: "${state.analysis}"
 *       filePath: "${input}/report.md"      # 빼면 저장하지 않고 만든 글자를 돌려줍니다
 *
 * stepOnly = true라서 LLM에게는 보이지 않고, 결과 길이 상한도 걸리지 않습니다.
 * </pre>
 */
@AiTool(stepOnly = true)
public class TemplateTool {

	private static final String FAIL = Constants.Outcome.FAIL_PREFIX;

	/**
	 * 이 클래스를 읽어 온 클래스로더로 틀 파일을 찾습니다. 클래스로더를 정해 두지 않으면 "지금 스레드의 클래스로더"를 쓰는데,
	 * 비동기 실행(/submit)이나 forEach의 스레드에서는 그것이 실행 jar 안의 파일을 보지 못해 틀 파일이 없다고 나옵니다.
	 */
	private final ResourceLoader resourceLoader = new DefaultResourceLoader(TemplateTool.class.getClassLoader());

	@Autowired
	private FileTool fileTool;

	/**
	 * <pre>
	 * 틀에 데이터를 끼워 문서를 만듭니다.
	 * - filePath를 주면 그 파일에 저장하고 저장 결과 안내 문구를 돌려줍니다(writeFile Tool과 같은 문구).
	 * - filePath가 없으면 만든 글자를 그대로 돌려줍니다.
	 * 틀 파일이 없거나 틀의 문법이 틀렸으면 "실패: ..."를 돌려줍니다(TOOL step은 실패가 됩니다).
	 * </pre>
	 *
	 * @param template 틀 파일의 경로(definitions 폴더 기준. templates/ 로 시작)
	 * @param data     틀에 넣을 데이터
	 * @param filePath 만든 문서를 저장할 파일의 절대경로(없어도 됩니다)
	 */
	@Tool(description = "틀 파일(definitions/templates/ 아래의 Mustache 템플릿)에 data를 끼워 문서를 만든다. filePath를 주면 그 파일에 저장하고, 없으면 만든 글자를 돌려준다.")
	public String renderTemplate(
			@ToolParam(description = "틀 파일의 경로(definitions 폴더 기준). 예: templates/sample/report.md.mustache") String template,
			@ToolParam(required = false, description = "틀에 넣을 데이터(이름 → 값)") Map<String, Object> data,
			@ToolParam(required = false, description = "만든 문서를 저장할 파일의 절대경로. 비우면 저장하지 않고 글자를 돌려준다") String filePath) {
		String templateText;
		try {
			templateText = this.readTemplate(template);
		} catch (IllegalArgumentException e) {
			return FAIL + ": " + e.getMessage();
		}
		String rendered;
		try {
			rendered = TemplateRenderer.render(templateText, data);
		} catch (RuntimeException e) {
			return FAIL + ": 틀의 문법이 틀렸습니다(" + template + ") - " + e.getMessage();
		}
		if (filePath == null || filePath.isBlank()) {
			return rendered;
		}
		return this.fileTool.writeFile(filePath, rendered);
	}

	/**
	 * 틀 파일을 읽습니다. 경로가 templates/ 아래가 아니거나 파일이 없으면 IllegalArgumentException을 던집니다.
	 *
	 * @param path definitions 폴더 기준 상대경로
	 */
	private String readTemplate(String path) {
		String dir = Constants.Definition.TEMPLATE_DIR;
		String normalized = path == null ? "" : path.strip().replace('\\', '/');
		if (!normalized.startsWith(dir + "/") || normalized.contains("..")) {
			throw new IllegalArgumentException("틀 파일의 경로는 " + dir + "/ 로 시작해야 합니다(definitions 폴더 기준): " + path);
		}
		Resource file = this.resourceLoader.getResource(Constants.Definition.ROOT_LOCATION + "/" + normalized);
		if (!file.exists() || !file.isReadable()) {
			throw new IllegalArgumentException("틀 파일이 없습니다(resources/definitions/" + normalized + ").");
		}
		try (InputStream input = file.getInputStream()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalArgumentException("틀 파일을 읽지 못했습니다(" + normalized + ") - " + e.getMessage());
		}
	}

}
