package net.dstone.knowledge.common.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import net.dstone.common.core.BaseObject;

/**
 * 트랜잭션 설정입니다.
 *
 * 다른 모듈은 Service 메소드 이름(insert*, update* ...)에 AOP로 트랜잭션을 겁니다.
 * 이 모듈의 분석 작업은 몇 시간씩 돌 수 있어서, 메소드 하나를 통째로 한 트랜잭션에 묶으면 안 됩니다.
 * 그래서 "파일 N개마다 커밋"처럼 코드에서 직접 범위를 정할 수 있게 TransactionTemplate을 씁니다.
 * 이렇게 해야 중간에 죽어도 그때까지 한 일이 DB에 남아 이어서 진행할 수 있습니다.
 */
@Component
public class ConfigTransaction extends BaseObject {

	@Bean(name = "txManagerCommon")
	public DataSourceTransactionManager txManagerCommon(@Qualifier("dataSourceCommon") DataSource dataSourceCommon) {
		return new DataSourceTransactionManager(dataSourceCommon);
	}

	@Bean(name = "txTemplateCommon")
	public TransactionTemplate txTemplateCommon(@Qualifier("txManagerCommon") DataSourceTransactionManager txManagerCommon) {
		return new TransactionTemplate(txManagerCommon);
	}

}
