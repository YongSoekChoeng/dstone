package net.dstone.ai.runtime.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.config.ConfigTool;
import net.dstone.common.core.BaseObject;

/**
 * TOOL step이 LLM 없이 Tool 하나를 이름으로 직접 호출하는 유일한 통로다 - 모든 TOOL 호출이 반드시 여길 거치므로, 나중에 governance가 Tool 호출을 감사/제한하고 싶어지면 이
 * 클래스 하나만 감싸면 된다.
 */
@Component
public class ToolExecutor extends BaseObject {

	@Autowired
	private ConfigTool configTool;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * @param caller    호출 주체 식별자(tenant)
	 * @param toolName  호출할 Tool 이름
	 * @param jsonInput Tool에 넘길 JSON 인자 문자열
	 */
	public String call(String caller, String toolName, String jsonInput) {
		ToolCallback callback = this.configTool.findByName(caller, toolName);
		if (callback == null) {
			throw new IllegalStateException("caller[" + caller + "]가 쓸 수 있는 Tool 중 '" + toolName + "'가 없습니다(화이트리스트 또는 이름을 확인하십시오).");
		}
		// ToolCallback.call()의 반환값은 순수 텍스트가 아니라 Spring AI가 JSON으로 감싼 값이다(String
		// 리턴 타입도 예외 없이 감싸지므로 "실패: ..."가 아니라 "\"실패: ...\""로 온다) - 이 값을 두고
		// 직접 "실패" 접두사를 검사해야 하므로 먼저 JSON을 벗겨낸다.
		return this.unwrap(callback.call(jsonInput));
	}

	/**
	 * @param rawResult Tool 호출 원본 응답(Spring AI가 JSON으로 감싼 값)
	 */
	private String unwrap(String rawResult) {
		try {
			return this.objectMapper.readValue(rawResult, String.class);
		} catch (JsonProcessingException e) {
			// Tool이 String이 아닌 다른 타입을 반환하면 JSON이 문자열 리터럴이 아닐 수 있다 - 그런
			// 경우는 애초에 "실패/통과" 텍스트 컨벤션 대상이 아니므로 원본을 그대로 쓴다.
			return rawResult;
		}
	}

}
