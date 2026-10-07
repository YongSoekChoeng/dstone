package net.dstone.batch.common.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariDataSource;

import net.dstone.batch.common.core.BaseBatchObject;
import net.dstone.common.config.ConfigProperty;


@Configuration
public class ConfigDatasource extends BaseBatchObject{

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	/********************************************************************************
	1. DataSource 관련 설정(Spring 내부적으로 필수로 dataSource를 사용하는 경우가 있으므로 두개의 이름을 지정)
	********************************************************************************/
    @Bean(name = {"dataSource", "dataSourceCommon"})
    @ConfigurationProperties("spring.datasource.common.hikari")
    public DataSource dataSourceCommon() {
    	return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

	/********************************************************************************
	2. Sample DataSource 관련 설정
	********************************************************************************/
    @Bean(name = "dataSourceSample")
    @ConfigurationProperties("spring.datasource.sample.hikari")
    public DataSource dataSourceSample() {
    	return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

}
