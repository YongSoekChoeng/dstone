package net.dstone.batchadmin.common.config;

import java.util.HashMap;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

import net.dstone.batchadmin.common.datasource.RoutingDataSource;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

@Component
public class ConfigDatasource extends BaseObject {

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	/**
	 * batchadmin 자체 스키마(로그인사용자/배치서버레지스트리/Job메타데이터).
	 */
	@Bean(name = "dataSourceCommon")
	@ConfigurationProperties("spring.datasource.common.hikari")
	public DataSource dataSourceCommon() {
		return DataSourceBuilder.create().type(HikariDataSource.class).build();
	}

	/**
	 * 관리대상 dstone-batch 서버의 배치 메타데이터DB로 동적 라우팅되는 DataSource.
	 * 실제 대상맵은 BatchServerDataSourceRegistry가 TB_BATCH_SERVER를 읽어 채운다.
	 * 컨테이너 기동시 InitializingBean.afterPropertiesSet()이 자동 호출되므로, 최초에는 빈 맵으로 초기화해둔다.
	 */
	@Bean(name = "routingDataSourceBatch")
	public RoutingDataSource routingDataSourceBatch() throws Exception {
		RoutingDataSource routingDataSource = new RoutingDataSource();
		routingDataSource.setTargetDataSources(new HashMap<Object, Object>());
		routingDataSource.afterPropertiesSet();
		return routingDataSource;
	}

}
