package net.dstone.knowledge.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

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
 * 코드 분석 화면(static/knowledge/**)의 로그인과 사용자 관리를 붙입니다. 규칙과 API는 dstone-common 의 ConsoleLoginFilter, ConsoleController 에 있습니다.
 *   - 사용자·사용자그룹·권한·메뉴는 이 앱의 DB(dstone_knowledge)의 knowledge_management_* 테이블입니다
 *     (schema/02-create-table-postgresql-dstone-knowledge.sql, 기본 데이터는 03-create-data-postgresql-dstone-knowledge.sql).
 *   - 로그인 화면은 /knowledge/login.html, 로그인한 뒤 첫 화면은 대시보드입니다.
 * 분석 API(/api/**)는 이 필터가 아니라 ApiKeyAuthFilter 가 지킵니다(dstone-ai-engine 이 서버끼리 부르기 때문).
 * </pre>
 */
@Configuration
public class ConfigConsole {

	/** 설정 이름 앞부분 */
	public static final String PREFIX = "dstone.knowledge.console";

	/** 사용자 관리 테이블 이름 앞부분 */
	public static final String TABLE_PREFIX = "knowledge_management_";

	@Autowired
	private ConfigProperty configProperty;

	@Bean
	public ConsoleSettings consoleSettings() {
		return new ConsoleSettings("knowledge", "/knowledge/", "/knowledge/dashboard/dashboard.html", TABLE_PREFIX);
	}

	@Bean
	public ConsoleManagementService consoleManagementService(@Qualifier("sqlSessionCommon") SqlSessionTemplate sqlSessionCommon,
			@Qualifier("txManagerCommon") DataSourceTransactionManager txManagerCommon) {
		return new ConsoleManagementService(new ConsoleManagementDao(sqlSessionCommon, TABLE_PREFIX), new TransactionTemplate(txManagerCommon));
	}

	@Bean
	public ConsoleController consoleController(ConsoleSettings consoleSettings, ConsoleManagementService consoleManagementService) {
		// 화면 사이 링크에 쓸 다른 앱 주소. 값은 conf/env*.properties 의 *_PUBLIC_URL
		Map<String, String> urls = new LinkedHashMap<String, String>();
		urls.put("ai", this.property(PREFIX + ".urls.ai"));
		urls.put("knowledge", this.property(PREFIX + ".urls.knowledge"));
		return new ConsoleController(consoleSettings, consoleManagementService, urls);
	}

	@Bean
	public FilterRegistrationBean<ConsoleLoginFilter> consoleLoginFilter(ConsoleSettings consoleSettings, ConsoleManagementService consoleManagementService) {
		FilterRegistrationBean<ConsoleLoginFilter> registration = new FilterRegistrationBean<ConsoleLoginFilter>(new ConsoleLoginFilter(consoleSettings, consoleManagementService));
		registration.addUrlPatterns("/*");
		// 요청 시각을 적는 RequestClockFilter 다음, API 키 필터보다 먼저 돈다(화면과 /console/** 만 보므로 순서가 결과를 바꾸지는 않는다).
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		return registration;
	}

	private String property(String key) {
		String value = this.configProperty.getProperty(key);
		return value == null ? "" : value.trim();
	}

}
