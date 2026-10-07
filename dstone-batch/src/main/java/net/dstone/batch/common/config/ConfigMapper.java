package net.dstone.batch.common.config;

import javax.sql.DataSource;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import net.dstone.batch.common.core.BaseBatchObject;

@Configuration
public class ConfigMapper extends BaseBatchObject{

	@Autowired
	@Qualifier("myBatisPartitionSqlInterceptor")
	public Interceptor myBatisPartitionSqlInterceptor;
    
	@Bean(name = "sqlSessionFactoryCommon")
	public SqlSessionFactory sqlSessionFactoryCommon(@Qualifier("dataSourceCommon") DataSource dataSourceCommon) throws Exception {
		PathMatchingResourcePatternResolver pmrpr = new PathMatchingResourcePatternResolver();
		SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
		bean.setDataSource(dataSourceCommon);
		bean.setConfigLocation(pmrpr.getResource("classpath:/sqlmap/sql-mapper-config.xml"));
		bean.setMapperLocations(pmrpr.getResources("classpath:/sqlmap/common/**/*Dao.xml"));
		return bean.getObject();
	}
	@Bean(name = "sqlSessionCommon")
	public SqlSessionTemplate sqlSessionCommon(@Qualifier("sqlSessionFactoryCommon") SqlSessionFactory sqlSessionFactoryCommon) {
		return new SqlSessionTemplate(sqlSessionFactoryCommon, ExecutorType.SIMPLE);
	}

	@Bean(name = "sqlSessionFactorySample")
	public SqlSessionFactory sqlSessionFactorySample(@Qualifier("dataSourceSample") DataSource dataSourceSample) throws Exception {
		PathMatchingResourcePatternResolver pmrpr = new PathMatchingResourcePatternResolver();
		SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
		bean.setDataSource(dataSourceSample);
		bean.setConfigLocation(pmrpr.getResource("classpath:/sqlmap/sql-mapper-config.xml"));
		bean.setMapperLocations(pmrpr.getResources("classpath:/sqlmap/sample/**/*Dao.xml"));
		bean.setPlugins(myBatisPartitionSqlInterceptor);
		return bean.getObject();
	}
	@Bean(name = "sqlSessionSample")
	public SqlSessionTemplate sqlSessionSample(@Qualifier("sqlSessionFactorySample") SqlSessionFactory sqlSessionFactorySample) {
		return new SqlSessionTemplate(sqlSessionFactorySample, ExecutorType.SIMPLE);
	}
	@Bean(name = "sqlBatchSessionSample")
	public SqlSessionTemplate sqlBatchSessionSample(@Qualifier("sqlSessionFactorySample") SqlSessionFactory sqlSessionFactorySample) {
		// Chunk 로 처리할 때 ExecutorType.BATCH 세팅 해서 작업.
		return new SqlSessionTemplate(sqlSessionFactorySample, ExecutorType.BATCH);
	}

}
