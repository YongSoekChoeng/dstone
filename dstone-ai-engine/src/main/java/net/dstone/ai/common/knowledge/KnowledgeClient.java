package net.dstone.ai.common.knowledge;

import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.common.consts.Constants;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.utils.WcUtil;
import reactor.core.publisher.Mono;

/**
 * <pre>
 * dstone-knowledge(Java 분석 결과와 올린 문서를 담아 둔 서버)의 REST API를 부르는 단 하나의 창구입니다.
 * 이 엔진에서 dstone-knowledge를 쓰는 곳은 둘입니다.
 *   - tools.knowledge.KnowledgeTool            Agent가 코드 분석 결과를 물어보는 Tool
 *   - common.rag.RagRetrievalChain             올려 둔 문서를 검색하는 RAG
 * 둘 다 같은 서버를 같은 방식(주소, API 키, 대기 시간)으로 부르므로 호출 코드를 여기 한 곳에 모았습니다.
 *
 * 설정(dstone.ai.tool.knowledge.*):
 *   base-url         서버 주소. 비어 있으면 모든 호출이 "주소가 설정되지 않았습니다"로 실패합니다
 *   api-key          dstone-knowledge가 호출자 인증을 켰을 때만. X-API-Key 헤더로 보냅니다
 *   timeout-seconds  응답 대기 시간(기본 60)
 *
 * 실패(연결 실패, 4xx / 5xx)는 모두 KnowledgeCallException으로 바꿉니다.
 * dstone-knowledge의 오류 응답은 {status, message} 모양이라 message를 그대로 전합니다(없는 프로젝트, 없는 문서 등).
 * </pre>
 */
@Component
public class KnowledgeClient extends BaseObject {

	/** 한 번에 받을 수 있는 응답의 최대 크기. WebClient의 기본값(256KB)으로는 검색 결과 수십 건을 받지 못한다 */
	private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private ConfigProperty configProperty;

	/** dstone-knowledge의 주소가 설정돼 있는지. 비어 있으면 어떤 호출도 할 수 없습니다 */
	public boolean isConfigured() {
		return !StringUtil.isEmpty(this.configProperty.getProperty(Constants.Tool.Knowledge.PREFIX + ".base-url"));
	}

	public JsonNode get(String path, Map<String, String> params) throws KnowledgeCallException {
		return this.exchange("GET " + path, this.webClient().get().uri(this.uriOf(path, params)));
	}

	public JsonNode post(String path, Map<String, Object> body) throws KnowledgeCallException {
		String json;
		try {
			json = this.objectMapper.writeValueAsString(body);
		} catch (Exception e) {
			throw new KnowledgeCallException("실패: 요청을 만들지 못했습니다 - " + e.getMessage());
		}
		return this.exchange("POST " + path, this.webClient().post().uri(this.uriOf(path, null)).contentType(MediaType.APPLICATION_JSON).bodyValue(json));
	}

	/** 요청을 보내고 응답 JSON을 읽습니다. 본문이 없는 성공 응답은 빈 객체로 돌려줍니다. */
	private JsonNode exchange(String what, WebClient.RequestHeadersSpec<?> request) throws KnowledgeCallException {
		String apiKey = this.configProperty.getProperty(Constants.Tool.Knowledge.PREFIX + ".api-key");
		if (!StringUtil.isEmpty(apiKey)) {
			request = request.header("X-API-Key", apiKey);
		}
		String[] reply;
		try {
			reply = request.exchangeToMono(new Function<ClientResponse, Mono<String[]>>() {
				@Override
				public Mono<String[]> apply(final ClientResponse response) {
					return response.bodyToMono(String.class).defaultIfEmpty("").map(new Function<String, String[]>() {
						@Override
						public String[] apply(String body) {
							return new String[] { String.valueOf(response.statusCode().value()), body };
						}
					});
				}
			}).block();
		} catch (Exception e) {
			LogUtil.sysout("dstone-ai-engine tool-audit: knowledge " + what + " -> 실패 " + e.getMessage());
			if (this.isTimeout(e)) {
				// 서버는 떠 있는데 응답이 늦은 것이다. "서버가 떠 있는지 확인하라"고 하면 엉뚱한 곳을 보게 된다.
				throw new KnowledgeCallException("실패: dstone-knowledge가 " + this.timeoutSeconds() + "초 안에 답하지 않았습니다(임베딩 서버가 바쁘면 검색이 늦어집니다. "
					+ "대기 시간은 dstone.ai.tool.knowledge.timeout-seconds)");
			}
			throw new KnowledgeCallException("실패: dstone-knowledge를 부르지 못했습니다(서버가 떠 있는지, dstone.ai.tool.knowledge.base-url이 맞는지 확인) - "
				+ (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
		}
		LogUtil.sysout("dstone-ai-engine tool-audit: knowledge " + what + " -> HTTP " + reply[0]);
		int status = Integer.parseInt(reply[0]);
		JsonNode body;
		try {
			body = StringUtil.isEmpty(reply[1]) ? this.objectMapper.createObjectNode() : this.objectMapper.readTree(reply[1]);
		} catch (Exception e) {
			throw new KnowledgeCallException(status, "실패: dstone-knowledge의 응답을 읽지 못했습니다(HTTP " + reply[0] + ")");
		}
		if (status < 200 || status >= 300) {
			throw new KnowledgeCallException(status, "실패: " + (body.hasNonNull("message") ? body.get("message").asText() : "HTTP " + reply[0]));
		}
		return body;
	}

	private URI uriOf(String path, Map<String, String> params) throws KnowledgeCallException {
		String baseUrl = this.configProperty.getProperty(Constants.Tool.Knowledge.PREFIX + ".base-url");
		if (StringUtil.isEmpty(baseUrl)) {
			throw new KnowledgeCallException("실패: dstone-knowledge 주소가 설정되지 않았습니다(dstone.ai.tool.knowledge.base-url).");
		}
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl.trim()).path(path);
		if (params != null) {
			for (Map.Entry<String, String> param : params.entrySet()) {
				if (!StringUtil.isEmpty(param.getValue())) {
					builder.queryParam(param.getKey(), param.getValue().trim());
				}
			}
		}
		// 한글이나 빈칸이 든 값이 주소에서 깨지지 않게 인코딩한다.
		return builder.build().encode().toUri();
	}

	/** 예외의 원인을 따라가며 응답 대기 시간 초과인지 봅니다(WebClient는 원래 예외를 한 겹 싸서 던집니다). */
	private boolean isTimeout(Throwable e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof io.netty.handler.timeout.TimeoutException || cause instanceof java.util.concurrent.TimeoutException) {
				return true;
			}
			if (cause.getCause() == cause) {
				break;
			}
		}
		return false;
	}

	private int timeoutSeconds() {
		String seconds = this.configProperty.getProperty(Constants.Tool.Knowledge.PREFIX + ".timeout-seconds");
		return StringUtil.isEmpty(seconds) ? 60 : Integer.parseInt(seconds);
	}

	private WebClient webClient() {
		return WcUtil.getInstance().getWebClient(this.timeoutSeconds()).mutate().codecs(new Consumer<ClientCodecConfigurer>() {
			@Override
			public void accept(ClientCodecConfigurer configurer) {
				configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES);
			}
		}).build();
	}

}
