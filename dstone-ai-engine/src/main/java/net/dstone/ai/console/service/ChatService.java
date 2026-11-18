package net.dstone.ai.console.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.ai.console.vo.AgentSummaryCallResult;
import net.dstone.ai.console.vo.AgentSummaryResult;
import net.dstone.ai.console.vo.ToolSummaryResult;
import net.dstone.common.web.console.ConsoleSessionFilter;
import net.dstone.common.web.console.SessionUser;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

@Service
public class ChatService extends ConsoleBaseService {

	private static final String AGENT = "sample-general-chat";

	/**
	 * 등록된 Agent의 id, description, 고를 수 있는 Tool 목록을 조회한다("채팅" 화면의 agent 드롭다운과 Tool 선택 칸용).
	 * WorkFlowTestService.listWorkflows()와 완전히 같은 패턴이다 - dstone-ai-engine의
	 * GET /api/ai/chat을 그대로 받아와 화면용 VO로 옮겨 담기만 한다.
	 */
	public List<AgentSummaryResult> listAgents() {
		String baseUrl = this.engineUrl();

		AgentSummaryCallResult[] results = this.getWebClient().get()
				.uri(baseUrl + "/api/ai/chat")
				.retrieve()
				.bodyToMono(AgentSummaryCallResult[].class)
				.block();

		List<AgentSummaryResult> summaries = new ArrayList<>();
		if (results != null) {
			for (AgentSummaryCallResult result : results) {
				List<ToolSummaryResult> tools = result.availableTools() == null ? new ArrayList<ToolSummaryResult>() : result.availableTools();
				summaries.add(new AgentSummaryResult(result.id(), result.description(), result.input(), result.output(), tools));
			}
		}
		return summaries;
	}

	/**
	 * dstone-ai-engine의 POST /api/ai/chat/stream을 그대로 흘려보낸다. sessionId는 화면에서 받지 않고
	 * 세션 사용자 id를 쓴다(resolveSessionId) - 사용자 한 명당 대화가 하나 계속 이어지는 구조다(멀티 대화방 아님).
	 * 엔진 API는 HTTP로 부르므로 브라우저 쿠키가 전달되지 않는다. 그래서 sessionId를
	 * 매 요청 명시적으로 실어 보내야 대화가 끊기지 않는다.
	 *
	 * agent는 화면의 agent 드롭다운 값을 그대로 받는다 - 비어 있으면 기본값(AGENT, sample-general-chat)을
	 * 쓴다. 프롬프트가 읽는 값은 Agent가 받은 input뿐이라, input 말고 따로 보낼 값은 없다.
	 *
	 * tools는 화면에서 고른 Tool 이름을 그대로 보낸다. 빈 목록이면 Tool 없이 답한다.
	 *
	 * model은 화면의 모델 override 입력칸 값을 그대로 흘려보낸다 - 비어 있으면 body에 아예 안 실어서
	 * dstone-ai-engine이 선택된 Agent 정의값(그마저 없으면 provider 공통 기본값)을 쓰게 둔다.
	 *
	 * input은 선택한 Agent의 input 모양(글자 또는 객체) 그대로 넘긴다. 모양이 틀리면 dstone-ai-engine이 400으로 거절한다.
	 */
	public Flux<String> streamChat(HttpServletRequest servletRequest, Object input, String agent, List<String> tools, String model) {

		String resolvedAgent = StringUtil.isEmpty(agent) ? AGENT : agent;

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("sessionId", this.resolveSessionId(servletRequest));
		body.put("agent", resolvedAgent);
		body.put("input", input);
		body.put("tools", tools);
		if (!StringUtil.isEmpty(model)) {
			body.put("model", model);
		}

		String baseUrl = this.engineUrl();

		return this.getWebClient().post()
				.uri(baseUrl + "/api/ai/chat/stream")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(body)
				.retrieve()
				.bodyToFlux(String.class);
	}

	/**
	 * <pre>
	 * 대화를 이어 갈 세션 id. dstone-boot 에서 넘어온 사용자면 로그인 id 그대로라, 사람마다 대화 하나가 계속 이어진다.
	 * 로그인을 끈 기본 사용자는 여러 사람이 같은 id 를 쓰므로 브라우저 세션 id 를 붙여 대화가 섞이지 않게 한다.
	 * </pre>
	 */
	private String resolveSessionId(HttpServletRequest servletRequest) {
		SessionUser user = ConsoleSessionFilter.currentUser(servletRequest);
		if (user == null) {
			return "anonymous:" + servletRequest.getSession(true).getId();
		}
		if ("DEFAULT".equals(user.getSource())) {
			return user.getUserId() + ":" + servletRequest.getSession(true).getId();
		}
		return user.getUserId();
	}

}
