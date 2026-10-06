package net.dstone.knowledge.common.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;

/**
 * <pre>
 * MyBatis 설정입니다. 매퍼 XML은 resources/sqlmap 아래의 *Dao.xml 파일입니다.
 * </pre>
 */
@Component
public class ConfigMapper extends BaseObject {

	@Bean(name = "sqlSessionFactoryCommon")
	public SqlSessionFactory sqlSessionFactoryCommon(@Qualifier("dataSourceCommon") DataSource dataSourceCommon) throws Exception {
		PathMatchingResourcePatternResolver pmrpr = new PathMatchingResourcePatternResolver();
		SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
		bean.setDataSource(dataSourceCommon);
		bean.setConfigLocation(pmrpr.getResource("classpath:/sqlmap/sql-mapper-config.xml"));
		bean.setMapperLocations(pmrpr.getResources("classpath:/sqlmap/**/*Dao.xml"));
		return bean.getObject();
	}

	/** 일반 조회/단건 변경용 세션입니다. */
	@Bean(name = "sqlSessionCommon")
	public SqlSessionTemplate sqlSessionCommon(@Qualifier("sqlSessionFactoryCommon") SqlSessionFactory sqlSessionFactoryCommon) {
		return new SqlSessionTemplate(sqlSessionFactoryCommon);
	}

	/**
	 * <pre>
	 * 대량 저장용 세션입니다.
	 *
	 * 분석은 파일 하나를 처리할 때마다 심볼/참조/관계 행을 수백 건씩 DB에 씁니다.
	 * 한 건씩 보내면 너무 느려서, 같은 SQL을 모아 한 번에 보내는 BATCH 방식 세션을 따로 둡니다.
	 * 반드시 트랜잭션 안에서 써야 모아 둔 SQL이 커밋 시점에 함께 나갑니다.
	 * </pre>
	 */
	@Bean(name = "sqlSessionBatch")
	public SqlSessionTemplate sqlSessionBatch(@Qualifier("sqlSessionFactoryCommon") SqlSessionFactory sqlSessionFactoryCommon) {
		return new SqlSessionTemplate(sqlSessionFactoryCommon, ExecutorType.BATCH);
	}

}
