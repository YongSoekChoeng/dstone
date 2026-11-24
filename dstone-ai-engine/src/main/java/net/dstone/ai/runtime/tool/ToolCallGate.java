package net.dstone.ai.runtime.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.consts.Constants;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 엔진에 등록된 Tool이 실제로 실행될 때 모두 지나가는 문입니다.
 *
 * Tool이 불리는 길은 두 가지입니다.
 * - TOOL step: ToolExecutor가 이름으로 찾아서 직접 부릅니다.
 * - AGENT step과 /chat: LLM이 고르면 Spring AI가 ToolCallback을 바로 부릅니다.
 * 두 길 모두 ConfigTool.LimitedToolCallback을 거치고, LimitedToolCallback은 이 bean을 거쳐 Tool을 실행합니다.
 * 로컬 @AiTool, MCP Tool, Skill Tool, Sub Agent도 모두 같습니다.
 *
 * LimitedToolCallback은 Spring bean이 아니라서 AOP가 걸리지 않습니다.
 * 그래서 실행 자체를 이 bean으로 옮겨 두고, 호출 로그(ConfigCallLog.TOOL_POINTCUT)는 이 클래스에 겁니다.
 * 나중에 Tool 호출을 감사하거나 막는 기능을 넣을 때도 이곳이 자리입니다.
 *
 * ## 비밀값 자리표시자
 * LLM에게 비밀번호를 주지 않으려고, 인자에 ${secret:참조}를 쓰게 합니다(예: ${secret:myApp.testPassword}).
 * 설정 dstone.ai.tool.secret.allowed-tools에 적은 Tool(브라우저 입력)에 넘기기 직전에만 SecretSource로 실제 값을 넣고,
 * 그 밖의 Tool에 자리표시자를 주면 실행하지 않고 실패로 돌려줍니다(파일에 적거나 다른 곳으로 보내지 못하게).
 * 결과에 이제까지 넣은 비밀값이 섞여 있으면 그 값의 자리표시자(${secret:참조})로 되돌려 가립니다(브라우저 Tool은 실행한 코드에 입력값을 담아 돌려줍니다).
 * ****가 아니라 자리표시자로 되돌리는 까닭: Agent가 결과를 보고 "제대로 들어갔다"와 "두 번 들어갔다·다른 값이 들어 있다"를 구별할 수 있게 하려고입니다.
 * ****로 가리면 입력이 맞았는지 알 수 없어 같은 입력을 되풀이하다가, 칸에 있던 값 뒤에 덧붙여 로그인에 실패하곤 했습니다(2026-11-24).
 * 호출 로그는 이 메서드의 인자(자리표시자 그대로)와 반환값(가린 결과)을 남기므로 로그에도 실제 값이 남지 않습니다.
 *
 * ## 실행 전 확인
 * ToolCallCheck bean들이 "지금은 안 된다"고 하면 실행하지 않고 그 이유를 실패로 돌려줍니다(예: 격리 시험 중 원래 DB 조회).
 *
 * 주의: 이 클래스는 ConfigTool에 의존하면 안 됩니다. ConfigTool이 이 bean을 주입받기 때문에 순환 참조가 생깁니다.
 * 예산 초과·같은 파일 다시 읽기·모르는 Tool처럼 앞 단계에서 막힌 호출은 Tool을 실행하지 않으므로 여기까지 오지 않습니다.
 * </pre>
 */
@Component
public class ToolCallGate extends BaseObject {

	/** 비밀값 자리표시자 ${secret:참조} */
	private static final Pattern SECRET = Pattern.compile("\\$\\{secret:([A-Za-z0-9_.-]+)\\}");

	/** 이보다 짧은 값은 가리지 않습니다(결과 곳곳의 같은 글자까지 지워 버리므로). */
	private static final int MIN_MASK_LENGTH = 4;

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	private SecretSource secretSource;

	@Autowired
	private ConfigProperty configProperty;

	@Autowired
	private List<ToolCallCheck> toolCallChecks;

	/** 이제까지 넣은 비밀값(JSON 안에 들어간 모양 포함) → 그 값의 자리표시자. 결과를 가릴 때 씁니다. 메모리에만 둡니다. */
	private final Map<String, String> usedSecrets = new ConcurrentHashMap<String, String>();

	/**
	 * <pre>
	 * Tool 하나를 실행하고 결과를 돌려줍니다. 결과 자르기는 부른 쪽(LimitedToolCallback)이 합니다.
	 * 비밀값 자리표시자가 있으면 위 설명대로 바꾸고, 결과에 비밀값이 있으면 가립니다.
	 * </pre>
	 *
	 * @param toolName    실행할 Tool 이름입니다(로그에 씁니다).
	 * @param toolInput   Tool에 넘길 인자 JSON입니다.
	 * @param toolContext caller·실행 id·step id를 담은 ToolContext입니다. 없으면 null입니다.
	 * @param delegate    실제로 Tool을 실행할 ToolCallback입니다.
	 * @return Tool이 돌려준 결과(비밀값은 가림)
	 */
	public String invoke(String toolName, String toolInput, ToolContext toolContext, ToolCallback delegate) {
		StepRef stepRef = StepRef.from(toolContext);
		for (ToolCallCheck check : this.toolCallChecks) {
			String problem = check.check(toolName, toolInput, stepRef);
			if (problem != null) {
				return "실패: " + problem;
			}
		}
		String input = toolInput;
		if (toolInput != null && SECRET.matcher(toolInput).find()) {
			if (!this.secretAllowedTools().contains(toolName)) {
				return "실패: 비밀값 자리표시자(${secret:...})는 " + this.secretAllowedTools() + " Tool에만 쓸 수 있습니다. 다른 Tool에는 넘기지 않습니다.";
			}
			StringBuilder missing = new StringBuilder();
			input = this.resolveSecrets(toolInput, missing);
			if (missing.length() > 0) {
				return "실패: 비밀값을 찾지 못했습니다: " + missing + ". 프로젝트 관리에서 시험계정을 확인하십시오.";
			}
		}
		String result = toolContext == null ? delegate.call(input) : delegate.call(input, toolContext);
		return this.mask(result);
	}

	/** 자리표시자를 실제 값으로 바꿉니다. 값은 JSON 문자열 안에 들어가므로 JSON 규칙대로 이스케이프합니다. */
	private String resolveSecrets(String toolInput, StringBuilder missing) {
		Matcher m = SECRET.matcher(toolInput);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (m.find()) {
			String value = this.secretSource.secret(m.group(1));
			if (value == null) {
				if (missing.length() > 0) {
					missing.append(", ");
				}
				missing.append(m.group(1));
				continue;
			}
			String escaped = jsonEscape(value);
			this.remember(value, m.group());
			this.remember(escaped, m.group());
			out.append(toolInput, last, m.start()).append(escaped);
			last = m.end();
		}
		out.append(toolInput.substring(last));
		return out.toString();
	}

	/** 결과에서 이제까지 넣은 비밀값을 그 자리표시자로 되돌립니다. 긴 값부터 바꿔서 한 값이 다른 값의 일부일 때도 온전히 가립니다. */
	private String mask(String result) {
		if (result == null || this.usedSecrets.isEmpty()) {
			return result;
		}
		List<String> secrets = new ArrayList<String>(this.usedSecrets.keySet());
		Collections.sort(secrets, new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				return b.length() - a.length();
			}
		});
		String masked = result;
		for (String secret : secrets) {
			if (masked.contains(secret)) {
				masked = masked.replace(secret, this.usedSecrets.get(secret));
			}
		}
		return masked;
	}

	private void remember(String value, String placeholder) {
		if (value != null && value.length() >= MIN_MASK_LENGTH) {
			this.usedSecrets.put(value, placeholder);
		}
	}

	/** 자리표시자를 쓸 수 있는 Tool 이름들(설정 dstone.ai.tool.secret.allowed-tools, 쉼표로 구분). */
	private List<String> secretAllowedTools() {
		String value = this.configProperty.getProperty(Constants.Tool.Secret.ALLOWED_TOOLS);
		List<String> names = new ArrayList<String>();
		if (value != null) {
			for (String name : Arrays.asList(value.split(","))) {
				if (!name.trim().isEmpty()) {
					names.add(name.trim());
				}
			}
		}
		return names;
	}

	private static String jsonEscape(String value) {
		try {
			String quoted = JSON.writeValueAsString(value);
			return quoted.substring(1, quoted.length() - 1);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("비밀값을 JSON에 넣지 못했습니다", e);
		}
	}
}
