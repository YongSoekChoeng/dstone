package net.dstone.ai.console.service;

import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

import net.dstone.common.config.ConfigProperty;

/**
 * <pre>
 * AI 화면(static/ai/**)이 부르는 서비스들의 바탕입니다.
 *
 * 화면용 서비스는 엔진 기능을 Java 로 바로 부르지 않고, 이 앱의 REST API(/api/ai/**)를 HTTP 로 부릅니다.
 * 예전에 dstone-boot 가 엔진을 부르던 방식을 그대로 옮긴 것이라, 화면이 받는 응답·오류 모양과
 * 사용자 헤더(X-Dstone-User)로 실행한 사람을 남기는 규칙이 API 를 직접 부르는 다른 앱과 똑같습니다.
 *
 * 부를 주소는 dstone.ai.console.engine-url 입니다(비우면 http://localhost:{server.port}, 곧 자기 자신).
 * API 키 인증(dstone.ai.security.auth.enabled)을 켰으면 dstone.ai.console.api-key 의 키를 붙입니다.
 * </pre>
 */
public abstract class ConsoleBaseService extends net.dstone.common.biz.BaseService {

	/** 큰 응답을 받을 때 쓰는 한도입니다(32MB). */
	protected static final int LARGE_RESPONSE_BYTES = 32 * 1024 * 1024;

	@Autowired
	protected ConfigProperty configProperty;

	/** 엔진 API 를 부를 주소(끝의 / 없음). 예: http://localhost:8081 */
	protected String engineUrl() {
		String url = this.configProperty.getProperty("dstone.ai.console.engine-url");
		if (url == null || url.trim().length() == 0) {
			String port = this.configProperty.getProperty("server.port");
			url = "http://localhost:" + (port == null || port.trim().length() == 0 ? "8081" : port.trim());
		}
		url = url.trim();
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}

	/** 엔진 API 를 부르는 WebClient. API 키를 설정했으면 헤더로 붙인다 */
	@Override
	protected WebClient getWebClient() {
		WebClient client = super.getWebClient();
		final String apiKey = this.configProperty.getProperty("dstone.ai.console.api-key");
		if (apiKey == null || apiKey.trim().length() == 0) {
			return client;
		}
		return client.mutate().defaultHeader("X-API-Key", apiKey.trim()).build();
	}

	/**
	 * <pre>
	 * 큰 응답도 받을 수 있는 WebClient입니다.
	 * WebClient는 응답을 256KB까지만 받는 것이 기본값이라, 그보다 큰 응답은 "Exceeded limit on max bytes to buffer" 에러가 납니다.
	 * Workflow 실행 상세처럼 응답이 커질 수 있는 호출은 이 메소드로 얻은 WebClient를 씁니다.
	 * </pre>
	 */
	protected WebClient getLargeResponseWebClient() {
		return this.getWebClient().mutate().codecs(new Consumer<ClientCodecConfigurer>() {
			@Override
			public void accept(ClientCodecConfigurer configurer) {
				configurer.defaultCodecs().maxInMemorySize(LARGE_RESPONSE_BYTES);
			}
		}).build();
	}

}
