package net.dstone.knowledge.common.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.web.console.BootSessionReader;
import net.dstone.common.web.console.ConsoleSessionFilter;

/**
 * <pre>
 * 코드 분석 화면(static/knowledge/**)을 연 사람을 정하는 필터를 등록합니다.
 * 필터가 하는 일은 dstone-common 의 ConsoleSessionFilter 설명에 있습니다. 설정은 dstone.knowledge.console 아래입니다.
 *   - 로그인을 끄면(login.enabled=false) 기본 사용자로 바로 씁니다.
 *   - 로그인을 켜면 1순위 주소의 ssoToken(dstone-boot 링크), 2순위 dstone-boot 의 Redis 세션(같은 브라우저의 dstone-boot 쿠키)으로 사람을 정합니다.
 * </pre>
 */
@Configuration
public class ConfigConsole {

	/** 설정 이름 앞부분 */
	public static final String PREFIX = "dstone.knowledge.console";

	@Autowired
	private ConfigProperty configProperty;

	/** dstone-boot 와 같은 Redis. 로그인을 켰을 때 dstone-boot 의 로그인 세션을 읽는다(2순위) */
	@Autowired
	private RedisConnectionFactory redisConnectionFactory;

	@Bean
	public FilterRegistrationBean<ConsoleSessionFilter> consoleSessionFilter() {
		FilterRegistrationBean<ConsoleSessionFilter> registration = new FilterRegistrationBean<ConsoleSessionFilter>(
				new ConsoleSessionFilter(this.configProperty, PREFIX, "knowledge", "/knowledge/", "/knowledge/dashboard/dashboard.html", new BootSessionReader(this.redisConnectionFactory, this.configProperty, PREFIX)));
		registration.addUrlPatterns("/*");
		// 요청 시각을 적는 RequestClockFilter 다음, API 키 필터보다 먼저 돈다(화면 주소만 보므로 순서가 결과를 바꾸지는 않는다).
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		return registration;
	}

}
