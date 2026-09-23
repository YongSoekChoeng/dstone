package net.dstone.ai.runtime.tool;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.config.ConfigTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.common.core.BaseObject;

/**
 * TOOL step이 LLM을 거치지 않고 Tool 하나를 이름만으로 직접 호출할 때 반드시 지나가는 유일한
 * 통로입니다. 모든 TOOL 호출이 예외 없이 이 클래스를 거치기 때문에, 나중에 governance 기능이
 * 생겨서 Tool 호출 내역을 감사하거나 제한하고 싶어질 때도 이 클래스 하나만 손보면 됩니다.
 */
@Component
public class ToolExecutor extends BaseObject {

	@Autowired
	private ConfigTool configTool;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Tool 하나를 이름으로 찾아서 직접 호출하고, 그 결과를 문자열로 돌려줍니다.
	 *
	 * @param caller    이 호출을 요청한 주체(tenant)를 식별하는 값입니다.
	 * @param toolName  호출할 Tool의 이름입니다.
	 * @param jsonInput Tool에 넘길 인자를 담은 JSON 문자열입니다.
	 */
	public String call(String caller, String toolName, String jsonInput) {
		// caller 별 ToolCallback 을 구한다.
		ToolCallback callback = this.configTool.findByName(caller, toolName);
		if (callback == null) {
			throw new IllegalStateException("caller[" + caller + "]가 쓸 수 있는 Tool 중 '" + toolName + "'가 없습니다(화이트리스트 또는 이름을 확인하십시오).");
		}
		// caller 별 ToolContext 를 구한다.
		ToolContext toolContext = new ToolContext(Map.of(Constants.Security.Caller.ADVISOR_CONTEXT_KEY, caller == null ? "" : caller));
		String rawResult = callback.call(jsonInput, toolContext);
		return this.unwrap(rawResult);
	}

	/**
	 * Spring AI가 JSON 문자열 리터럴로 한 번 감싸서 돌려준 Tool의 응답을, 감싸기 전의 원래 문자열로
	 * 풀어줍니다. 로컬 @AiTool이 String을 돌려주는 경우는 이 규칙 하나로 충분합니다.
	 *
	 * MCP Tool은 이 규칙을 따르지 않습니다 - Spring AI의 SyncMcpToolCallback(spring-ai-mcp 2.0.1
	 * 기준, io.modelcontextprotocol.spec.McpSchema.CallToolResult.content()를 그대로 JSON 배열로
	 * 직렬화해서 돌려줍니다, 예: [{"type":"text","text":"..."}]) - CallToolResult가 원래 갖고 있는
	 * structuredContent 필드(도구가 출력 스키마를 선언했을 때만 채워지는 진짜 구조화된 JSON)는 이
	 * 버전에서는 아예 쓰이지 않고 버려집니다. 그래서 String으로 풀어내는 시도가 실패하면, 이 MCP
	 * content 배열 모양인지 한 번 더 확인해서 사람이 읽는 순수 텍스트로 바꿔줍니다(unwrapMcpContent
	 * 참고). 그 모양도 아니면(로컬 Tool이 String이 아닌 다른 POJO를 돌려준 경우 등) 더 손대지 않고
	 * 원본 값을 그대로 씁니다.
	 *
	 * @param rawResult Tool을 호출하고 받은 원본 응답입니다(Spring AI가 JSON으로 감싼 값입니다).
	 */
	private String unwrap(String rawResult) {
		try {
			return this.objectMapper.readValue(rawResult, String.class);
		} catch (JsonProcessingException e) {
			String mcpText = this.unwrapMcpContent(rawResult);
			return mcpText != null ? mcpText : rawResult;
		}
	}

	/**
	 * rawResult가 MCP의 content 배열(각 항목이 최소한 "text" 필드를 가진 객체들의 JSON 배열)
	 * 모양인지 확인해서, 맞으면 각 항목의 text 값을 순서대로 줄바꿈으로 이어붙인 텍스트를
	 * 돌려줍니다. 이 모양이 아니면(배열이 아니거나, 항목 중 하나라도 text 필드가 없으면) null을
	 * 돌려줘서 호출한 쪽이 원본 rawResult를 그대로 쓰게 합니다 - "이 값이 MCP content 배열이
	 * 맞는지 확신할 수 없다면 손대지 않는다"는 원칙입니다(runtime.step.ToolStepRunner의
	 * tryParseOutcome/tryParsePayload가 애매하면 null을 돌려주는 것과 같은 이유입니다).
	 *
	 * @param rawResult String으로 풀어내는 데 실패한 원본 응답입니다.
	 */
	@SuppressWarnings("unchecked")
	private String unwrapMcpContent(String rawResult) {
		List<Object> items;
		try {
			items = this.objectMapper.readValue(rawResult, List.class);
		} catch (JsonProcessingException e) {
			return null;
		}
		StringBuilder joined = new StringBuilder();
		for (Object item : items) {
			if (!(item instanceof Map)) {
				return null;
			}
			Object text = ((Map<String, Object>) item).get("text");
			if (text == null) {
				return null;
			}
			if (joined.length() > 0) {
				joined.append("\n");
			}
			joined.append(text);
		}
		return joined.toString();
	}

}
