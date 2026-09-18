package net.dstone.boot.common.config;

import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;

@Component
public class ConfigMapper extends BaseObject{

	@Bean(name = "sqlSessionFactoryCommon")
	public SqlSessionFactory sqlSessionFactoryCommon(@Qualifier("dataSourceCommon") DataSource dataSourceCommon) throws Exception {
		PathMatchingResourcePatternResolver pmrpr = new PathMatchingResourcePatternResolver();
		SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
		bean.setDataSource(dataSourceCommon);
		bean.setConfigLocation(pmrpr.getResource("classpath:/sqlmap/sql-mapper-config.xml"));
		bean.setMapperLocations(pmrpr.getResources("classpath:/sqlmap/**/*Dao.xml"));
		bean.setObjectWrapperFactory(upperCaseObjectWrapperFactory());
		return bean.getObject();
	}
	@Bean(name = "sqlSessionCommon")
	public SqlSessionTemplate sqlSessionCommon(@Qualifier("sqlSessionFactoryCommon") SqlSessionFactory sqlSessionFactoryCommon) {
		return new SqlSessionTemplate(sqlSessionFactoryCommon);
	}

	@Bean(name = "sqlSessionFactorySample")
	public SqlSessionFactory sqlSessionFactorySample(@Qualifier("dataSourceSample") DataSource dataSourceSample) throws Exception {
		PathMatchingResourcePatternResolver pmrpr = new PathMatchingResourcePatternResolver();
		SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
		bean.setDataSource(dataSourceSample);
		bean.setConfigLocation(pmrpr.getResource("classpath:/sqlmap/sql-mapper-config.xml"));
		bean.setMapperLocations(pmrpr.getResources("classpath:/sqlmap/**/*Dao.xml"));
		bean.setObjectWrapperFactory(upperCaseObjectWrapperFactory());
		return bean.getObject();
	}
	@Bean(name = "sqlSessionSample")
	public SqlSessionTemplate sqlSessionSample(@Qualifier("sqlSessionFactorySample") SqlSessionFactory sqlSessionFactorySample) {
		return new SqlSessionTemplate(sqlSessionFactorySample);
	}
	
	@Bean(name = "sqlSessionFactoryAnalyzer")
	public SqlSessionFactory sqlSessionFactoryAnalyzer(@Qualifier("dataSourceAnalyzer") DataSource dataSourceAnalyzer) throws Exception {
		PathMatchingResourcePatternResolver pmrpr = new PathMatchingResourcePatternResolver();
		SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
		bean.setDataSource(dataSourceAnalyzer);
		bean.setConfigLocation(pmrpr.getResource("classpath:/sqlmap/sql-mapper-config.xml"));
		bean.setMapperLocations(pmrpr.getResources("classpath:/sqlmap/**/*Dao.xml"));
		bean.setObjectWrapperFactory(upperCaseObjectWrapperFactory());
		return bean.getObject();
	}
	@Bean(name = "sqlSessionAnalyzer")
	public SqlSessionTemplate sqlSessionAnalyzer(@Qualifier("sqlSessionFactoryAnalyzer") SqlSessionFactory sqlSessionFactoryAnalyzer) {
		return new SqlSessionTemplate(sqlSessionFactoryAnalyzer);
	}

	@Bean
	public ObjectWrapperFactory upperCaseObjectWrapperFactory() {
		ObjectWrapperFactory wrrapper = new ObjectWrapperFactory() {
			@Override
			public boolean hasWrapperFor(Object object) {
				boolean hasWrappe = false;
				if( object instanceof Map ) {
					hasWrappe = true;
				}
				return hasWrappe;
			}
			@Override
			public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
				return new MapWrapper(metaObject, (Map)object) {
					@Override
					public String findProperty(String name, boolean useCamelCaseMapping) {
						return name.toUpperCase();
					}
				};
			}
		};
		return wrrapper;
	}
	
}
