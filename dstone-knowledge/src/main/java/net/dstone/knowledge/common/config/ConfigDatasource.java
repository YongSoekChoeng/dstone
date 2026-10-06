package net.dstone.knowledge.common.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * 이 모듈이 쓰는 DataSource를 등록합니다.
 *
 * 분석 결과, 그래프, RAG 청크, 임베딩을 모두 한 DB(dstone_knowledge)에 담기 때문에 DataSource는 하나뿐입니다.
 * 이름(dataSourceCommon)은 다른 모듈의 관례를 그대로 따랐습니다.
 * </pre>
 */
@Component
public class ConfigDatasource extends BaseObject {

	/**
	 * <pre>
	 * dstone_knowledge 스키마용 DataSource입니다.
	 * 비밀번호의 ENC(...) 값은 dstone-common이 Spring 바인딩 전에 알아서 풀어 줍니다.
	 * </pre>
	 */
	@Bean(name = "dataSourceCommon")
	@ConfigurationProperties("spring.datasource.common.hikari")
	public DataSource dataSourceCommon() {
		return DataSourceBuilder.create().type(HikariDataSource.class).build();
	}

}
