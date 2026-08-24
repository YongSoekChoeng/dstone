package net.dstone.batchadmin.common.config;

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
public class ConfigMapper extends BaseObject {

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

	/**
	 * 관리대상 배치서버들의 배치 메타데이터DB(BATCH_JOB_*)를 조회하기 위한 SqlSessionFactory.
	 * DataSource는 RoutingDataSource이므로 MyBatis의 databaseIdProvider(빌드시점 1회 판별)는 사용하지 않고,
	 * 매퍼 XML에서 파라메터로 전달되는 DBMS_TYPE 값으로 <if> 분기한다.
	 */
	@Bean(name = "sqlSessionFactoryBatch")
	public SqlSessionFactory sqlSessionFactoryBatch(@Qualifier("routingDataSourceBatch") DataSource routingDataSourceBatch) throws Exception {
		PathMatchingResourcePatternResolver pmrpr = new PathMatchingResourcePatternResolver();
		SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
		bean.setDataSource(routingDataSourceBatch);
		bean.setConfigLocation(pmrpr.getResource("classpath:/sqlmap/sql-mapper-config.xml"));
		bean.setMapperLocations(pmrpr.getResources("classpath:/sqlmap/**/*Dao.xml"));
		bean.setObjectWrapperFactory(upperCaseObjectWrapperFactory());
		return bean.getObject();
	}

	@Bean(name = "sqlSessionBatch")
	public SqlSessionTemplate sqlSessionBatch(@Qualifier("sqlSessionFactoryBatch") SqlSessionFactory sqlSessionFactoryBatch) {
		return new SqlSessionTemplate(sqlSessionFactoryBatch);
	}

	@Bean
	public ObjectWrapperFactory upperCaseObjectWrapperFactory() {
		ObjectWrapperFactory wrapper = new ObjectWrapperFactory() {
			@Override
			public boolean hasWrapperFor(Object object) {
				return object instanceof Map;
			}

			@Override
			public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
				return new MapWrapper(metaObject, (Map) object) {
					@Override
					public String findProperty(String name, boolean useCamelCaseMapping) {
						return name.toUpperCase();
					}
				};
			}
		};
		return wrapper;
	}

}
