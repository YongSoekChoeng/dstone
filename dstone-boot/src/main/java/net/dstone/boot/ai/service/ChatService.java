package net.dstone.boot.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.dstone.boot.ai.vo.AgentSummaryCallResult;
import net.dstone.boot.ai.vo.AgentSummaryResult;
import net.dstone.boot.common.security.vo.CustomUserDetails;
import net.dstone.boot.common.web.SessionListener;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

@Service
public class ChatService extends net.dstone.boot.common.biz.BaseService {

	private static final String AGENT = "sample-general-chat";

	@Autowired
	private ConfigProperty configProperty;

	/**
	 * 등록된 Agent의 id+description 목록을 조회한다("채팅" 화면의 agent 드롭다운용).
	 * WorkFlowTestService.listWorkflows()와 완전히 같은 패턴이다 - dstone-ai-engine의
	 * GET /api/ai/chat을 그대로 받아와 화면용 VO로 옮겨 담기만 한다.
	 */
	public List<AgentSummaryResult> listAgents() {
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		AgentSummaryCallResult[] results = this.getWebClient().get()
				.uri(baseUrl + "/api/ai/chat")
				.retrieve()
				.bodyToMono(AgentSummaryCallResult[].class)
				.block();

		List<AgentSummaryResult> summaries = new ArrayList<>();
		if (results != null) {
			for (AgentSummaryCallResult result : results) {
				summaries.add(new AgentSummaryResult(result.id(), result.description(), result.input(), result.output()));
			}
		}
		return summaries;
	}

	/**
	 * dstone-ai-engine의 POST /api/ai/chat/stream을 그대로 흘려보낸다. sessionId는 화면에서 받지 않고
	 * 로그인 사용자 ID를 그대로 쓴다 - 사용자 한 명당 대화가 하나 계속 이어지는 구조다(멀티 대화방 아님).
	 * dstone-boot ↔ dstone-ai-engine은 서버 대 서버 호출이라 쿠키가 전달되지 않으므로, sessionId를
	 * 매 요청 명시적으로 실어 보내야 대화가 끊기지 않는다.
	 *
	 * agent는 화면의 agent 드롭다운 값을 그대로 받는다 - 비어 있으면 기본값(AGENT, sample-general-chat)을
	 * 쓴다. 프롬프트가 읽는 값은 Agent가 받은 input뿐이라, input 말고 따로 보낼 값은 없다.
	 *
	 * model은 화면의 모델 override 입력칸 값을 그대로 흘려보낸다 - 비어 있으면 body에 아예 안 실어서
	 * dstone-ai-engine이 선택된 Agent 정의값(그마저 없으면 provider 공통 기본값)을 쓰게 둔다.
	 *
	 * input은 선택한 Agent의 input 모양(글자 또는 객체) 그대로 넘긴다. 모양이 틀리면 dstone-ai-engine이 400으로 거절한다.
	 */
	public Flux<String> streamChat(HttpServletRequest servletRequest, Object input, String agent, boolean ragEnabled, boolean toolsEnabled, String model) {

		String resolvedAgent = StringUtil.isEmpty(agent) ? AGENT : agent;

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("sessionId", this.resolveSessionId(servletRequest));
		body.put("agent", resolvedAgent);
		body.put("input", input);
		body.put("ragEnabled", ragEnabled);
		body.put("toolsEnabled", toolsEnabled);
		if (!StringUtil.isEmpty(model)) {
			body.put("model", model);
		}

		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");

		return this.getWebClient().post()
				.uri(baseUrl + "/api/ai/chat/stream")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_EVENT_STREAM)
				.bodyValue(body)
				.retrieve()
				.bodyToFlux(String.class);
	}

	private String resolveSessionId(HttpServletRequest servletRequest) {
		HttpSession session = servletRequest.getSession(true);
		CustomUserDetails userDetails = (CustomUserDetails) session.getAttribute(SessionListener.USER_LOGIN_SESSION_KEY);
		return userDetails.getUsername();
	}

}
