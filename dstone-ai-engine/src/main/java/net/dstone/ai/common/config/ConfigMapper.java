package net.dstone.ai.common.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * MyBatis 설정입니다. 이 모듈의 DB 접근은 모두 MyBatis로 합니다(다른 dstone 모듈과 같은 방식).
 *   - 매퍼 XML: resources/sqlmap 아래의 *Dao.xml
 *   - DataSource: 하나뿐이라 Spring Boot가 spring.datasource.* 설정으로 만들어 주는 것을 그대로 씁니다
 *     (dstone-boot처럼 여러 개가 아니어서 별도 ConfigDatasource 클래스가 없습니다).
 * </pre>
 */
@Configuration
public class ConfigMapper extends BaseObject {

	@Bean(name = "sqlSessionFactoryCommon")
	public SqlSessionFactory sqlSessionFactoryCommon(DataSource dataSource) throws Exception {
		PathMatchingResourcePatternResolver pmrpr = new PathMatchingResourcePatternResolver();
		SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
		bean.setDataSource(dataSource);
		bean.setConfigLocation(pmrpr.getResource("classpath:/sqlmap/sql-mapper-config.xml"));
		bean.setMapperLocations(pmrpr.getResources("classpath:/sqlmap/**/*Dao.xml"));
		return bean.getObject();
	}

	/** Dao가 쓰는 세션입니다. */
	@Bean(name = "sqlSessionCommon")
	public SqlSessionTemplate sqlSessionCommon(@Qualifier("sqlSessionFactoryCommon") SqlSessionFactory sqlSessionFactoryCommon) {
		return new SqlSessionTemplate(sqlSessionFactoryCommon);
	}

}
