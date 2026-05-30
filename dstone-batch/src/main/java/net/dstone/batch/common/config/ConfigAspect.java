package net.dstone.batch.common.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.batch.common.core.BaseBatchObject;
import net.dstone.common.config.ConfigProperty;

@Configuration
public class ConfigAspect extends BaseBatchObject{

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean
	
    /**
     * Job 수행 AOP
     * @return
     */
    @Bean
    public Advisor jobExecuteAdvisor() {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(* org.springframework.batch.core.launch.**.*Launcher.run(..))");

        return new DefaultPointcutAdvisor(pointcut, new MethodInterceptor() {
            @SuppressWarnings("unused")
			@Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
            	boolean isJob = false;
            	org.springframework.batch.core.Job job = null;
            	Object result = null;
            	Object[] args = invocation.getArguments();
            	if( args != null ) {
            		for( Object arg : args) {
            			if( arg instanceof org.springframework.batch.core.Job ) {
            				isJob = true;
            				job = (org.springframework.batch.core.Job)arg;
            				break;
            			}
            		}
            	}
            	if( isJob ) {
                	try {
                		/*** 수행 전 진행할 내용 시작 ***/
                		// TODO: do something here !
                		/*** 수행 전 진행할 내용 끝 ***/
                		
                        result = invocation.proceed();

                		/*** 수행 후 진행할 내용 시작 ***/
                		// TODO: do something here !
                		/*** 수행 후 진행할 내용 끝 ***/
    				} catch (Exception e) {
    					throw e;
    				}
            	}
                return result;
            }
        });
    }
    
}
