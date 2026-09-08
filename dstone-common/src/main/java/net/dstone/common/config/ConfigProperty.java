package net.dstone.common.config;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

import java.util.List;

import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.EncUtil;

@Component("configProperty")
@PropertySources({
    @PropertySource("classpath:env.properties")
})
public class ConfigProperty extends BaseObject{

	/**
	 * Environment는 기본적으로 application.yml의 프로퍼티정보를 로딩한다. @PropertySource 가 세팅되어 있으면 해당 프로퍼티의 정보도 시스템프로퍼티로 로딩한다.
	 * 이 경우 application.yml에서 시스템프로퍼티를 ${프로퍼티} 형식으로 사용할 수 있다.
	 */
	@Autowired
	Environment env;

	public String getProperty(String key) {
		String val = env.getProperty(key);
		return val;
	}

	@SuppressWarnings("rawtypes")
	public List getListProperty(String key) {
		List val = env.getProperty(key, List.class);
		if(val == null) {
			val = (List)new ArrayList();
		}
		return val;
	}

	/**
	 * jasypt-spring-boot-starter를 대체하는 자체 구현체. application.yml의 ENC(...) 값을
	 * Spring의 {@code @ConfigurationProperties} 바인딩(예: net.dstone.*.common.config.ConfigDatasource의
	 * spring.datasource.*.hikari.password)이 참조하기 전에, Environment의 PropertySource 계층에서
	 * 직접 복호화한다 — @ConfigurationProperties는 이 클래스의 getProperty()를 거치지 않고
	 * Environment에서 바로 값을 읽으므로, 복호화는 이 지점(PropertySource 레벨)에서 이뤄져야 한다.
	 *
	 * spring.factories(org.springframework.boot.env.EnvironmentPostProcessor)로 등록되며,
	 * ApplicationContext가 만들어지기 전 환경 준비 단계에서 실행되므로 static nested class여야
	 * 하고(리플렉션 no-arg 생성자 호출), Spring 빈인 바깥 ConfigProperty 인스턴스와는 무관하게 동작한다.
	 * 이 시점에는 application.yml(ConfigDataEnvironmentPostProcessor가 이미 로딩)은 잡히지만,
	 * 위 @PropertySource("classpath:env.properties")처럼 컨텍스트 refresh 중에야 추가되는
	 * PropertySource는 잡히지 않는다 — 다만 env.properties에는 애초에 ENC(...) 값이 들어가지 않는
	 * 컨벤션(DB/비밀키는 전부 application.yml)이라 실질적인 커버리지 공백은 없다.
	 */
	public static final class EncPropertyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

		private static final Pattern ENC_PATTERN = Pattern.compile("^ENC\\((.*)\\)$");

		@Override
		public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
			StringEncryptor encryptor = EncUtil.getEncryptor();
			MutablePropertySources propertySources = environment.getPropertySources();
			for (org.springframework.core.env.PropertySource<?> propertySource : propertySources) {
				if (propertySource instanceof EnumerablePropertySource<?> enumerable
						&& !(propertySource instanceof DecryptingPropertySource)) {
					propertySources.replace(propertySource.getName(), new DecryptingPropertySource(enumerable, encryptor));
				}
			}
		}

		@Override
		public int getOrder() {
			// application.yml이 이미 로딩된 뒤(ConfigDataEnvironmentPostProcessor는 매우 이른 순서로 실행됨)
			// 실행되어야 ENC(...) 원문이 존재하는 PropertySource를 감쌀 수 있다.
			return Ordered.LOWEST_PRECEDENCE;
		}

		/** delegate의 값이 ENC(...) 형태면 복호화해서 돌려주는 얇은 래퍼. 그 외 동작은 전부 delegate에 위임. */
		private static final class DecryptingPropertySource extends EnumerablePropertySource<Object> {

			private final EnumerablePropertySource<?> delegate;
			private final StringEncryptor encryptor;

			DecryptingPropertySource(EnumerablePropertySource<?> delegate, StringEncryptor encryptor) {
				super(delegate.getName(), delegate);
				this.delegate = delegate;
				this.encryptor = encryptor;
			}

			@Override
			public String[] getPropertyNames() {
				return this.delegate.getPropertyNames();
			}

			@Override
			public Object getProperty(String name) {
				Object value = this.delegate.getProperty(name);
				if (value instanceof String stringValue) {
					Matcher matcher = ENC_PATTERN.matcher(stringValue);
					if (matcher.matches()) {
						return this.encryptor.decrypt(matcher.group(1));
					}
				}
				return value;
			}

		}

	}

}
