package net.dstone.ai.common.config;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.web.console.ConsoleController;
import net.dstone.common.web.console.ConsoleLoginFilter;
import net.dstone.common.web.console.ConsoleSettings;
import net.dstone.common.web.console.management.ConsoleManagementDao;
import net.dstone.common.web.console.management.ConsoleManagementService;

/**
 * <pre>
 * AI 화면(static/ai/**)의 로그인과 사용자 관리를 붙입니다. 규칙과 API는 dstone-common 의 ConsoleLoginFilter, ConsoleController 에 있습니다.
 *   - 사용자·사용자그룹·권한·메뉴는 이 앱의 DB(dstone_ai)의 ai_management_* 테이블입니다(schema/02-create-table-postgresql-dstone-ai.sql).
 *   - 로그인 화면은 /ai/login.html, 로그인한 뒤 첫 화면은 /ai/index.html 입니다.
 *   - 왼쪽 메뉴는 /console/session 이 돌려주는 "이 사람이 볼 수 있는 메뉴"로 그립니다(assets/js/console.js).
 * 엔진 API(/api/ai/**)는 이 필터가 아니라 ApiKeyAuthFilter 가 지킵니다. 화면에서 부른 API의 사람은 세션 사용자입니다(RequesterContext).
 * </pre>
 */
@Configuration
public class ConfigConsole {

	/** 사용자 관리 테이블 이름 앞부분입니다. */
	public static final String TABLE_PREFIX = "ai_management_";

	@Autowired
	private ConfigProperty configProperty;

	@Bean
	public ConsoleSettings consoleSettings() {
		return new ConsoleSettings("ai", "/ai/", "/ai/index.html", TABLE_PREFIX);
	}

	@Bean
	public ConsoleManagementService consoleManagementService(@Qualifier("sqlSessionCommon") SqlSessionTemplate sqlSessionCommon, DataSource dataSource) {
		TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		return new ConsoleManagementService(new ConsoleManagementDao(sqlSessionCommon, TABLE_PREFIX), tx);
	}

	@Bean
	public ConsoleController consoleController(ConsoleSettings consoleSettings, ConsoleManagementService consoleManagementService) {
		// 화면 사이 링크(코드 분석 화면 등)에 쓸 다른 앱 주소. 값은 conf/env*.properties 의 *_PUBLIC_URL
		Map<String, String> urls = new LinkedHashMap<String, String>();
		urls.put("ai", this.property("dstone.ai.console.urls.ai"));
		urls.put("knowledge", this.property("dstone.ai.console.urls.knowledge"));
		return new ConsoleController(consoleSettings, consoleManagementService, urls);
	}

	@Bean
	public FilterRegistrationBean<ConsoleLoginFilter> consoleLoginFilter(ConsoleSettings consoleSettings, ConsoleManagementService consoleManagementService) {
		FilterRegistrationBean<ConsoleLoginFilter> registration = new FilterRegistrationBean<ConsoleLoginFilter>(new ConsoleLoginFilter(consoleSettings, consoleManagementService));
		registration.addUrlPatterns("/*");
		// ApiKeyAuthFilter(@Order(1)), RateLimitFilter(@Order(2))보다 먼저 돈다. 화면과 /console/** 만 보므로 순서가 결과를 바꾸지는 않는다.
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		return registration;
	}

	private String property(String key) {
		String value = this.configProperty.getProperty(key);
		return value == null ? "" : value.trim();
	}

}
