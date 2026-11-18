package net.dstone.ai.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.web.console.ConsoleSessionFilter;

/**
 * <pre>
 * AI 화면(static/ai/**)과 화면이 부르는 /ai/**.do 를 쓰는 사람을 정하는 필터를 등록합니다.
 * 필터가 하는 일은 dstone-common 의 ConsoleSessionFilter 설명에 있습니다. 설정은 dstone.ai.console 아래입니다.
 *   - 로그인을 끄면(login.enabled=false) 기본 사용자로 바로 씁니다.
 *   - 로그인을 켜면 dstone-boot 메뉴의 링크(/sso/go.do?target=ai)로 들어와야 합니다. dstone-boot 의 로그인 사용자가 그대로 넘어옵니다.
 * 엔진 API(/api/ai/**)는 이 필터와 상관없이 예전처럼 ApiKeyAuthFilter 가 지킵니다.
 * </pre>
 */
@Configuration
public class ConfigConsole {

	@Autowired
	private ConfigProperty configProperty;

	@Bean
	public FilterRegistrationBean<ConsoleSessionFilter> consoleSessionFilter() {
		FilterRegistrationBean<ConsoleSessionFilter> registration = new FilterRegistrationBean<ConsoleSessionFilter>(
				new ConsoleSessionFilter(this.configProperty, "dstone.ai.console", "ai", "/ai/", "/ai/index.html"));
		registration.addUrlPatterns("/*");
		// ApiKeyAuthFilter(@Order(1)), RateLimitFilter(@Order(2))보다 먼저 돈다. 화면 주소만 보므로 순서가 결과를 바꾸지는 않는다.
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		return registration;
	}

}
