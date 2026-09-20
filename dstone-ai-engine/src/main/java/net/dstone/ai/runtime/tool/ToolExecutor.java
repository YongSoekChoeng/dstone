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
		// caller를 ToolContext에 실어 보낸다 - tools.rag.RagSearchTool처럼 caller(tenant) 기준으로 검색
		// 범위를 좁혀야 하는 Tool이 AGENT의 tool-calling 경로(runtime.agent.AgentExecutor)와 동일하게 이
		// 값을 받을 수 있게 하기 위함이다. caller가 없어도 키는 반드시 채워 넣는다(값은 빈 문자열) -
		// runtime.agent.AgentExecutor의 toolContext 설명과 동일하게, Spring AI는 @Tool 메서드가 ToolContext
		// 파라미터를 선언했는데 ToolContext가 null이거나 엔트리 0개인 빈 Map이면 예외를 던진다.
		ToolContext toolContext = new ToolContext(Map.of(Constants.Security.Caller.ADVISOR_CONTEXT_KEY, caller == null ? "" : caller));
		String rawResult = callback.call(jsonInput, toolContext);
		return this.unwrap(rawResult);
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
