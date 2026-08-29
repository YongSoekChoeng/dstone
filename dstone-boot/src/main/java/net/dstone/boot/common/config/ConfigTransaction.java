package net.dstone.boot.common.config;

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

@Component
public class ConfigTransaction extends BaseObject{

	/********************************************************************************
	Datasource/Transaction 설정
	********************************************************************************/
	private int TX_METHOD_TIMEOUT = 30;
	// "*ServiceImpl"뿐 아니라 인터페이스 없는 "*Service" 클래스도 트랜잭션 대상이 되도록 "*Service*"(포함 매칭)로 지정.
	// proxyTargetClass=true(ConfigAspect)라 인터페이스 유무와 무관하게 CGLIB로 구체 클래스가 프록시되므로 매칭에 문제없음.
	private static String AOP_POINTCUT_EXPRESSION = "execution(public * net.dstone.*..*Service*.*(..))";
	

	/********************************************************************************
	1. Common 관련 설정
	********************************************************************************/
	@Bean(name = "txManagerCommon")
	public DataSourceTransactionManager txManagerCommon(@Qualifier("dataSourceCommon") DataSource dataSourceCommon) {
		DataSourceTransactionManager txManagerCommon = new DataSourceTransactionManager(dataSourceCommon);
		return txManagerCommon;
	}

	@Bean(name = "txAdviceCommon")
	public TransactionInterceptor txAdviceCommon(@Qualifier("txManagerCommon") DataSourceTransactionManager txManagerCommon) {
	    TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
		Properties txAttributes = new Properties();
		List<RollbackRuleAttribute> rollbackRules = new ArrayList<RollbackRuleAttribute>();
		/** If need to add additionall exception, add here **/
		rollbackRules.add(new RollbackRuleAttribute(Exception.class));
		// read-only
		DefaultTransactionAttribute readOnlyAttribute = new DefaultTransactionAttribute( TransactionDefinition.PROPAGATION_SUPPORTS);
		readOnlyAttribute.setReadOnly(true);
		readOnlyAttribute.setTimeout(TX_METHOD_TIMEOUT);
		String readOnlyTransactionAttributesDefinition = readOnlyAttribute.toString();
		txAttributes.setProperty("get*", readOnlyTransactionAttributesDefinition);
		txAttributes.setProperty("select*", readOnlyTransactionAttributesDefinition);
		txAttributes.setProperty("list*", readOnlyTransactionAttributesDefinition);
		// write rollback-rule
		RuleBasedTransactionAttribute writeAttribute = new RuleBasedTransactionAttribute( TransactionDefinition.PROPAGATION_REQUIRED, rollbackRules);
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

	/********************************************************************************
	2. Sample 관련 설정
	********************************************************************************/
	@Bean(name = "txManagerSample")
	public DataSourceTransactionManager txManagerSample(@Qualifier("dataSourceSample") DataSource dataSourceSample) {
		DataSourceTransactionManager txManagerSample = new DataSourceTransactionManager(dataSourceSample);
		return txManagerSample;
	}

	@Bean(name = "txAdviceSample")
	public TransactionInterceptor txAdviceSample(@Qualifier("txManagerSample") DataSourceTransactionManager txManagerSample) {
	    TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
		Properties txAttributes = new Properties();
		List<RollbackRuleAttribute> rollbackRules = new ArrayList<RollbackRuleAttribute>();
		/** If need to add additionall exception, add here **/
		rollbackRules.add(new RollbackRuleAttribute(Exception.class));
		// read-only
		DefaultTransactionAttribute readOnlyAttribute = new DefaultTransactionAttribute( TransactionDefinition.PROPAGATION_SUPPORTS);
		readOnlyAttribute.setReadOnly(true);
		readOnlyAttribute.setTimeout(TX_METHOD_TIMEOUT);
		String readOnlyTransactionAttributesDefinition = readOnlyAttribute.toString();
		txAttributes.setProperty("get*", readOnlyTransactionAttributesDefinition);
		txAttributes.setProperty("select*", readOnlyTransactionAttributesDefinition);
		txAttributes.setProperty("list*", readOnlyTransactionAttributesDefinition);
		// write rollback-rule
		RuleBasedTransactionAttribute writeAttribute = new RuleBasedTransactionAttribute( TransactionDefinition.PROPAGATION_REQUIRED, rollbackRules);
		writeAttribute.setTimeout(TX_METHOD_TIMEOUT);
		String writeTransactionAttributesDefinition = writeAttribute.toString();
		txAttributes.setProperty("insert*", writeTransactionAttributesDefinition);
		txAttributes.setProperty("update*", writeTransactionAttributesDefinition);
		txAttributes.setProperty("delete*", writeTransactionAttributesDefinition);
		
		transactionInterceptor.setTransactionAttributes(txAttributes);
		transactionInterceptor.setTransactionManager(txManagerSample);
	    return transactionInterceptor;
	}

	@Bean(name = "txAdvisorSample")
	public Advisor txAdvisorSample(@Qualifier("txManagerSample") DataSourceTransactionManager txManagerSample) {
		AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
		pointcut.setExpression(AOP_POINTCUT_EXPRESSION);
		return new DefaultPointcutAdvisor(pointcut, txAdviceSample(txManagerSample));
	}

	/********************************************************************************
	3. Analyzer 관련 설정
	********************************************************************************/
	@Bean(name = "txManagerAnalyzer")
	public DataSourceTransactionManager txManagerAnalyzer(@Qualifier("dataSourceAnalyzer") DataSource dataSourceAnalyzer) {
		DataSourceTransactionManager txManagerAnalyzer = new DataSourceTransactionManager(dataSourceAnalyzer);
		return txManagerAnalyzer;
	}

	@Bean(name = "txAdviceAnalyzer")
	public TransactionInterceptor txAdviceAnalyzer(@Qualifier("txManagerAnalyzer") DataSourceTransactionManager txManagerAnalyzer) {
	    TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
		Properties txAttributes = new Properties();
		List<RollbackRuleAttribute> rollbackRules = new ArrayList<RollbackRuleAttribute>();
		/** If need to add additionall exception, add here **/
		rollbackRules.add(new RollbackRuleAttribute(Exception.class));
		// read-only
		DefaultTransactionAttribute readOnlyAttribute = new DefaultTransactionAttribute( TransactionDefinition.PROPAGATION_SUPPORTS);
		readOnlyAttribute.setReadOnly(true);
		readOnlyAttribute.setTimeout(TX_METHOD_TIMEOUT);
		String readOnlyTransactionAttributesDefinition = readOnlyAttribute.toString();
		txAttributes.setProperty("get*", readOnlyTransactionAttributesDefinition);
		txAttributes.setProperty("select*", readOnlyTransactionAttributesDefinition);
		txAttributes.setProperty("list*", readOnlyTransactionAttributesDefinition);
		// write rollback-rule
		RuleBasedTransactionAttribute writeAttribute = new RuleBasedTransactionAttribute( TransactionDefinition.PROPAGATION_REQUIRED, rollbackRules);
		writeAttribute.setTimeout(TX_METHOD_TIMEOUT);
		String writeTransactionAttributesDefinition = writeAttribute.toString();
		txAttributes.setProperty("insert*", writeTransactionAttributesDefinition);
		txAttributes.setProperty("update*", writeTransactionAttributesDefinition);
		txAttributes.setProperty("delete*", writeTransactionAttributesDefinition);
		
		transactionInterceptor.setTransactionAttributes(txAttributes);
		transactionInterceptor.setTransactionManager(txManagerAnalyzer);
	    return transactionInterceptor;
	}

	@Bean(name = "txAdvisorAnalyzer")
	public Advisor txAdvisorAnalyzer(@Qualifier("txManagerAnalyzer") DataSourceTransactionManager txManagerAnalyzer) {
		AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
		pointcut.setExpression(AOP_POINTCUT_EXPRESSION);
		return new DefaultPointcutAdvisor(pointcut, txAdviceAnalyzer(txManagerAnalyzer));
	}

}
