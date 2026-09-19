package net.dstone.ai.tools.http;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.utils.WcUtil;

/**
 * 사전에 허용된 호스트에 한해 HTTP GET만 보낼 수 있는 Tool이다. 별도의 on/off 플래그는 없다 -
 * allowed-hosts를 아무것도 등록하지 않으면(기본값) 어떤 URL을 줘도 화이트리스트에서 못 찾아
 * 항상 실패하므로, 설정을 안 하는 것 자체가 곧 비활성 상태다.
 */
@AiTool
public class HttpCallTool extends BaseObject {

	@Autowired
	private ConfigProperty configProperty;
	@Autowired
	private Environment environment;

	/**
	 * @param url 요청할 전체 URL(http 또는 https) - 호스트가 화이트리스트에 등록되어 있어야 함
	 */
	@Tool(description = "사전에 허용된 호스트에 한해 HTTP GET 요청을 보내고 응답 본문을 반환한다.  url의 호스트가 화이트리스트에 없으면 거부된다.")
	public String httpGet(@ToolParam(description = "요청할 전체 URL(http 또는 https)") String url) {
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			return "실패: URL 형식이 올바르지 않습니다 - " + e.getMessage();
		}
		if (!this.isAllowedHost(uri.getHost())) {
			return "실패: 화이트리스트에 없는 호스트입니다: " + uri.getHost();
		}

		WebClient webClient = WcUtil.getInstance().getWebClient((int) this.timeout().toSeconds());
		try {
			HttpOutcome outcome = webClient.get()
				.uri(uri)
				.exchangeToMono(new Function<ClientResponse, Mono<HttpOutcome>>() {
					@Override
					public Mono<HttpOutcome> apply(final ClientResponse response) {
						return response.bodyToMono(String.class)
							.defaultIfEmpty("")
							.map(new Function<String, HttpOutcome>() {
								@Override
								public HttpOutcome apply(String body) {
									return HttpCallTool.this.toOutcome(response.statusCode().value(), response.statusCode().is2xxSuccessful(), body);
								}
							});
					}
				})
				.block();
			LogUtil.sysout("dstone-ai-engine tool-audit: http url=" + url + " -> HTTP " + outcome.statusCode());
			return outcome.result();
		} catch (Exception e) {
			LogUtil.sysout("dstone-ai-engine tool-audit: http url=" + url + " -> 실패 " + e.getMessage());
			return "실패: 호출 실패 - " + e.getMessage();
		}
	}

	/**
	 * <pre>
	 * WebClient 응답(상태코드/본문)을 이 Tool의 "통과: .../실패: ..." 응답 문자열 컨벤션으로 바꾼다.
	 * </pre>
	 *
	 * @param statusCode HTTP 상태 코드
	 * @param success    2xx 여부
	 * @param body       응답 본문(없으면 빈 문자열)
	 */
	private HttpOutcome toOutcome(int statusCode, boolean success, String body) {
		String truncated = body.length() > this.maxResponseChars() ? body.substring(0, this.maxResponseChars()) + "...(생략)" : body;
		String result = (success ? "통과: HTTP " : "실패: HTTP ") + statusCode + "\n" + truncated;
		return new HttpOutcome(statusCode, result);
	}

	/** exchangeToMono() 콜백 밖에서 상태코드(로그용)와 최종 응답 문자열을 함께 꺼내기 위한 홀더. */
	private record HttpOutcome(int statusCode, String result) {
	}

	/**
	 * @param host 화이트리스트 포함 여부를 검사할 호스트명
	 */
	private boolean isAllowedHost(String host) {
		if (StringUtil.isEmpty(host)) {
			return false;
		}
		List<String> allowedHosts = Binder.get(this.environment).bind(Constants.Tool.Http.PREFIX + ".allowed-hosts", Bindable.listOf(String.class)).orElse(List.of());
		return allowedHosts.contains(host);
	}

	private Duration timeout() {
		String seconds = this.configProperty.getProperty(Constants.Tool.Http.PREFIX + ".timeout-seconds");
		return Duration.ofSeconds(StringUtil.isEmpty(seconds) ? 10 : Long.parseLong(seconds));
	}

	private int maxResponseChars() {
		String chars = this.configProperty.getProperty(Constants.Tool.Http.PREFIX + ".max-response-chars");
		return StringUtil.isEmpty(chars) ? 4000 : Integer.parseInt(chars);
	}

}
