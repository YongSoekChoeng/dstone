package net.dstone.ai.tools.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * 사전에 허용된 호스트에 한해 HTTP GET만 보낼 수 있는 Tool이다(옵트인: dstone.ai.tool.http.enabled, 기본 false).
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

		HttpClient client = HttpClient.newBuilder().connectTimeout(this.timeout()).build();
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(this.timeout()).GET().build();
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			String body = response.body() == null ? "" : response.body();
			String truncated = body.length() > this.maxResponseChars() ? body.substring(0, this.maxResponseChars()) + "...(생략)" : body;
			String result = response.statusCode() >= 200 && response.statusCode() < 300 ? "통과: HTTP " + response.statusCode() + "\n" + truncated : "실패: HTTP " + response.statusCode() + "\n" + truncated;
			LogUtil.sysout("dstone-ai-engine tool-audit: http url=" + url + " -> HTTP " + response.statusCode());
			return result;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return "실패: 호출이 중단되었습니다.";
		} catch (IOException e) {
			LogUtil.sysout("dstone-ai-engine tool-audit: http url=" + url + " -> 실패 " + e.getMessage());
			return "실패: 호출 실패 - " + e.getMessage();
		}
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
