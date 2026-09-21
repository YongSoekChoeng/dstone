package net.dstone.ai.runtime.tool;

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
	 * 풀어줍니다.
	 *
	 * @param rawResult Tool을 호출하고 받은 원본 응답입니다(Spring AI가 JSON으로 감싼 값입니다).
	 */
	private String unwrap(String rawResult) {
		try {
			return this.objectMapper.readValue(rawResult, String.class);
		} catch (JsonProcessingException e) {
			// Tool이 String이 아닌 다른 타입을 돌려주면, 그 값은 애초에 JSON 문자열 리터럴 형태가
			// 아닐 수 있습니다. 그런 경우는 "실패/통과" 텍스트 규칙을 적용할 대상이 아니므로, 굳이
			// 풀어내려 하지 않고 원본 값을 그대로 씁니다.
			return rawResult;
		}
	}

}
