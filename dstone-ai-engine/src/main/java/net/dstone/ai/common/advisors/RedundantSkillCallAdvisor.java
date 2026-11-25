package net.dstone.ai.common.advisors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.runtime.agent.ReadHistory;
import net.dstone.ai.runtime.agent.SkillTool;

/**
 * <pre>
 * 일을 마친 모델이 최종 답을 본문으로 쓰면서 "이미 받은 Skill"을 또 부르는 응답을, 그 자리에서 끝내 주는 Advisor입니다.
 *
 * 왜 필요한가:
 * Spring AI의 Tool 고리(ToolCallingAdvisor)는 응답에 Tool 호출이 하나라도 있으면(hasToolCalls) 끝난 이유나 본문을 보지 않고 Tool을 실행해
 * 한 번 더 돕니다. 그런데 qwen3.8-27b는 매 라운드를 "설명 문장 + Tool 호출"로 내는 버릇이 있어, 마무리 라운드에서도 최종 답을 본문에 쓰고
 * 쓸모없는 Skill 호출을 덧붙입니다. 본문은 버려지고, 같은 호출이 5번 되풀이되면 반복 호출 가드(RepeatCallGuardCallback)가 Agent 호출을 실패시켜
 * 이미 끝난 일이 통째로 버려졌습니다(2026-11-25 실행 d9f0bcc0: sm-developer-agent 3번, sm-auto-fix-agent 1번, 모두 같은 Skill).
 * 이미 받은 Skill을 다시 부르는 것은 아무 일도 하지 않는 호출이라, 실행해 봐야 알 것이 없습니다.
 *
 * 무엇을 하나:
 *   응답의 Tool 호출이 모두 "이미 본문을 건넨 Skill을 다시 부르는 것"이고 본문이 있다   → 연속 횟수를 셉니다
 *   그 연속이 2번째(= "이미 받았다"는 안내를 한 번 받고도 또 불렀을 때)              → 호출을 떼어 낸 응답을 돌려줍니다
 *   그 밖의 응답                                                                    → 그대로 돌려주고 셈을 처음부터 다시 합니다
 * 호출을 뗀 응답이 돌아가면 hasToolCalls()가 false라 Tool 고리가 끝나고, 본문이 최종 답으로 AgentExecutor의 계약 검사
 * (output 모양, requiredTools, requiredWrites)를 그대로 거칩니다. 일을 안 하고 끝난 것을 가려내는 일은 그 검사가 합니다.
 * 본문은 연속된 라운드 가운데 가장 긴 것을 씁니다. 같은 말을 되풀이하며 점점 짧아지는 경향이 있어서, 마지막 것이 가장 부실합니다.
 *
 * 첫 번째 재호출은 그대로 둡니다. 모델이 안내(SkillTool.alreadyLoadedNotice)를 보고 스스로 마무리할 기회를 한 번 주는 것입니다.
 * 설명 문장과 함께 "아직 받지 않은 Skill"을 부르는 정상 호출, 다른 Tool이 섞인 응답은 건드리지 않습니다.
 *
 * 어디서 도는가:
 * ToolCallingAdvisor보다 안쪽, AgentCallStatsAdvisor(-50)보다 바깥입니다. 그래서 회차 로그에는 모델이 실제로 낸 응답이 남습니다.
 * 설정 dstone.ai.agent.redundant-skill-end가 false면 붙이지 않습니다(AgentExecutor). Skill Tool이나 읽은 기록 장부가 없는 Agent에도 붙이지 않습니다.
 * Spring 빈으로 만들지 않는 이유는 ReasoningStripAdvisor와 같습니다. Agent를 부를 때마다 새로 만듭니다.
 * </pre>
 */
public class RedundantSkillCallAdvisor implements CallAdvisor {

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RedundantSkillCallAdvisor.class);

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** Advisor 순서입니다(위 설명 참고). RunawayRetryAdvisor(-60)와 AgentCallStatsAdvisor(-50) 사이입니다. */
	public static final int ORDER = -55;

	/** 이 횟수째 연속에서 호출을 뗍니다. 1번째는 안내를 받을 기회로 그대로 둡니다. */
	static final int END_AT = 2;

	private final String agentId;
	private final ReadHistory readHistory;

	/** 지금까지 연달아 나온, 이미 받은 Skill만 다시 부른 응답의 수 */
	private int streak;

	/** 그 응답들의 본문 가운데 가장 긴 것 */
	private String longestText = "";

	/**
	 * @param agentId     이 호출의 Agent id입니다(로그용).
	 * @param readHistory 이번 호출에서 받은 Skill을 적어 둔 장부입니다(SkillTool과 같은 것).
	 */
	public RedundantSkillCallAdvisor(String agentId, ReadHistory readHistory) {
		this.agentId = agentId;
		this.readHistory = readHistory;
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
		ChatResponse chatResponse = response == null ? null : response.chatResponse();
		String text = this.redundantOnlyText(chatResponse);
		if (text == null) {
			this.streak = 0;
			this.longestText = "";
			return response;
		}
		this.streak++;
		if (text.length() > this.longestText.length()) {
			this.longestText = text;
		}
		if (this.streak < END_AT) {
			return response;
		}
		LOGGER.info("dstone-ai-engine agent: agent[" + this.agentId + "]가 이미 받은 Skill을 안내를 받고도 " + this.streak
			+ "번 연달아 불러서, 부른 것을 떼고 본문(" + this.longestText.length() + "자)을 최종 답으로 삼았습니다.");
		return response.mutate().chatResponse(withoutToolCalls(chatResponse, this.longestText)).build();
	}

	/**
	 * <pre>
	 * 이 응답이 "이미 받은 Skill을 다시 부르는 것뿐이고 본문이 있는 응답"이면 그 본문을, 아니면 null을 돌려줍니다.
	 * </pre>
	 *
	 * @param chatResponse 모델 응답
	 */
	String redundantOnlyText(ChatResponse chatResponse) {
		if (chatResponse == null || chatResponse.getResults() == null || chatResponse.getResults().size() != 1) {
			return null;
		}
		AssistantMessage output = chatResponse.getResult().getOutput();
		if (output == null || output.getText() == null || output.getText().isBlank()
			|| output.getToolCalls() == null || output.getToolCalls().isEmpty()) {
			return null;
		}
		for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
			if (!SkillTool.NAME.equals(toolCall.name()) || !this.readHistory.hasSkill(skillNameOf(toolCall.arguments()))) {
				return null;
			}
		}
		return output.getText().strip();
	}

	/** Skill Tool 인자({"command": "이름"})에서 Skill 이름을 꺼냅니다. 읽지 못하면 null입니다. */
	static String skillNameOf(String arguments) {
		if (arguments == null || arguments.isBlank()) {
			return null;
		}
		try {
			JsonNode command = OBJECT_MAPPER.readTree(arguments).get("command");
			return command == null || command.isNull() ? null : command.asText().trim();
		} catch (Exception e) {
			return null;
		}
	}

	/** Tool 호출을 떼고 본문을 바꾼 사본을 만듭니다. 끝난 이유는 STOP으로 고칩니다. 토큰 수 같은 나머지 정보는 그대로입니다. */
	static ChatResponse withoutToolCalls(ChatResponse original, String text) {
		AssistantMessage message = original.getResult().getOutput();
		Map<String, Object> properties = message.getMetadata();
		AssistantMessage stripped = AssistantMessage.builder().content(text).properties(properties).media(message.getMedia()).build();
		List<Generation> generations = new ArrayList<Generation>();
		generations.add(new Generation(stripped, ChatGenerationMetadata.builder().finishReason("STOP").build()));
		return ChatResponse.builder().from(original).generations(generations).build();
	}

}
