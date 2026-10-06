package net.dstone.batchadmin.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import net.dstone.common.core.BaseObject;

/**
 * batchadmin 자체 스키마(dataSourceCommon)에 대한 트랜잭션 설정.
 * 관리대상 배치서버의 메타데이터DB(routingDataSourceBatch)는 조회전용(SELECT)이므로 별도 트랜잭션 매니저를 두지 않는다.
 */
@Component
public class ConfigTransaction extends BaseObject {

	private int TX_METHOD_TIMEOUT = 30;
	private static String AOP_POINTCUT_EXPRESSION = "execution(public * net.dstone.batchadmin.*..*Service.*(..))";

	@Bean(name = "txManagerCommon")
	public DataSourceTransactionManager txManagerCommon(@Qualifier("dataSourceCommon") DataSource dataSourceCommon) {
		return new DataSourceTransactionManager(dataSourceCommon);
	}

	@Bean(name = "txAdviceCommon")
	public TransactionInterceptor txAdviceCommon(@Qualifier("txManagerCommon") DataSourceTransactionManager txManagerCommon) {
		TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
		Properties txAttributes = new Properties();
		List<RollbackRuleAttribute> rollbackRules = new ArrayList<RollbackRuleAttribute>();
		rollbackRules.add(new RollbackRuleAttribute(Exception.class));
		// read-only
		DefaultTransactionAttribute readOnlyAttribute = new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_SUPPORTS);
		readOnlyAttribute.setReadOnly(true);
		readOnlyAttribute.setTimeout(TX_METHOD_TIMEOUT);
		String readOnlyTransactionAttributesDefinition = readOnlyAttribute.toString();
		txAttributes.setProperty("get*", readOnlyTransactionAttributesDefinition);
		txAttributes.setProperty("select*", readOnlyTransactionAttributesDefinition);
		txAttributes.setProperty("list*", readOnlyTransactionAttributesDefinition);
		// write rollback-rule
		RuleBasedTransactionAttribute writeAttribute = new RuleBasedTransactionAttribute(TransactionDefinition.PROPAGATION_REQUIRED, rollbackRules);
		writeAttribute.setTimeout(TX_METHOD_TIMEOUT);
		String writeTransactionAttributesDefinition = writeAttribute.toString();
		txAttributes.setProperty("insert*", writeTransactionAttributesDefinition);
		txAttributes.setProperty("update*", writeTransactionAttributesDefinition);
		txAttributes.setProperty("delete*", writeTransactionAttributesDefinition);

		transactionInterceptor.setTransactionAttributes(txAttributes);
		transactionInterceptor.setTransactionManager(txManagerCommon);
		return transactionInterceptor;
	}

	@Bean(name = "txAdvisorCommon")
	public Advisor txAdvisorCommon(@Qualifier("txManagerCommon") DataSourceTransactionManager txManagerCommon) {
		AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
		pointcut.setExpression(AOP_POINTCUT_EXPRESSION);
		return new DefaultPointcutAdvisor(pointcut, txAdviceCommon(txManagerCommon));
	}

}
