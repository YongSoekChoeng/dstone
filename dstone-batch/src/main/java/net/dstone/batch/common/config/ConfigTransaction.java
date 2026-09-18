package net.dstone.batch.common.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import net.dstone.batch.common.core.BaseBatchObject;

@Configuration
public class ConfigTransaction extends BaseBatchObject{

	/********************************************************************************
	1. TransactionManager 관련 설정(Spring 내부적으로 필수로 transactionManager를 사용하는 경우가 있으므로 두개의 이름을 지정)
	********************************************************************************/
	@Bean(name = {"transactionManager", "txManagerCommon"})
	public PlatformTransactionManager txManagerCommon(@Qualifier("dataSourceCommon") DataSource dataSourceCommon) {
		PlatformTransactionManager txManagerCommon = new DataSourceTransactionManager(dataSourceCommon);
		return txManagerCommon;
	}

	/********************************************************************************
	2. Sample DataSource TransactionManager 관련 설정
	********************************************************************************/
	@Bean(name = "txManagerSample")
	public PlatformTransactionManager txManagerSample(@Qualifier("dataSourceSample") DataSource dataSourceSample) {
		PlatformTransactionManager txManagerSample = new DataSourceTransactionManager(dataSourceSample);
		return txManagerSample;
	}

}
