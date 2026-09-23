package net.dstone.ai.common.config;

import java.lang.reflect.Method;

import org.apache.logging.log4j.ThreadContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import net.dstone.ai.common.definition.AgentDefinition;
import net.dstone.ai.common.definition.StepDefinition;
import net.dstone.ai.common.definition.WorkFlowDefinition;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 이 클래스 하나가 dstone-ai-engine 전체의 실행 흐름을 자동으로 로그로 남겨줍니다.
 *
 * AOP 스프링 기능을 사용합니다. 다만 AOP는 public 메소드에만 적용됩니다(private/protected 메소드는 감시할 수 없습니다). 
 * 그리고 @NoAspectLog 애노테이션이 붙은 메소드는 이 로깅 대상에서 제외됩니다(예: SQL 로그를 억제하고 싶을 때 사용).
 * </pre>
 */
@Aspect
@Component
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ConfigCallLog extends BaseObject {

	/****************************************** 로깅 관련 AOP 설정 시작 ******************************************/
	/** 
	 * @NoAspectLog 애노테이션이 붙은 메소드는 로깅 대상에서 제외하기 위한 표현식입니다. 
	 */
	private final static String NO_LOG_REGEX = "@annotation(net.dstone.common.annotation.NoAspectLog)";

	/**
	 * <pre>
	 * 컨트롤러(Controller로 끝나는 클래스)의 메소드가 호출될 때마다 자동으로 실행되어 로그를 남깁니다.
	 * 메소드가 시작될 때와 끝날 때를 구분선으로 표시해서, 로그만 봐도 "어느 컨트롤러 호출이 언제
	 * 시작해서 언제 끝났는지" 한눈에 알아볼 수 있게 해줍니다.
	 *
	 * 참고: 스프링 AOP는 public 메소드만 감시할 수 있습니다.
	 * </pre>
	 *
	 * @param joinPoint 지금 호출되고 있는 컨트롤러 메소드에 대한 정보(어떤 메소드인지, 어떤 인자를 받았는지 등)
	 * @return 원래 컨트롤러 메소드가 반환하는 값을 그대로 돌려줍니다
	 * @throws Throwable 원래 컨트롤러 메소드에서 예외가 발생하면 그 예외를 그대로 다시 던집니다
	 */
	@Around("execution(* net.dstone.ai.*..*Controller.*(..))" + " && !" + NO_LOG_REGEX)
	public Object doControllerProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		this.sysout("\n\n||===================================== [" + joinPoint.getTarget().getClass().getName() + "] START ======================================||");
		this.info("+->[CONTROLLER] {" + signatureLog(joinPoint) + "}");

		/*****************************************************************************************************
		 * 컨트롤러가 호출되면 먼저 응답 헤더 successYn 값을 "Y"(성공)로 미리 세팅해 둡니다.
		 * 만약 컨트롤러 로직 안에서 오류가 생겨 setErrCd()가 호출되면 이 값이 "N"으로 바뀌고,
		 * 컨트롤러가 예외를 던지면 DsExceptionResolver가 이 값을 "N"으로 바꿔줍니다.
		 * 즉, 이 값만 보면 요청이 성공했는지 실패했는지 바로 알 수 있습니다.
		 *****************************************************************************************************/
		Object[] args = joinPoint.getArgs();
		if (args != null) {
			for (Object arg : args) {
				if (arg instanceof HttpServletResponse) {
					((HttpServletResponse) arg).setHeader("successYn", "Y");
					break;
				}
			}
		}
		/*****************************************************************************************************
		 * 이제 원래 컨트롤러 메소드를 실제로 실행합니다.
		 *****************************************************************************************************/
		Object retObj = joinPoint.proceed();
		this.sysout("||===================================== [" + joinPoint.getTarget().getClass().getName() + "] END ======================================||\n");
		return retObj;
	}

	/**
	 * <pre>
	 * 서비스(Service가 이름에 들어간 클래스)의 메소드가 호출될 때마다 "어떤 서비스 메소드가
	 * 호출됐는지" 한 줄을 로그로 남깁니다. 컨트롤러 로그와 나란히 보면 "이 요청이 어느
	 * 서비스까지 들어갔는지" 흐름을 따라갈 수 있습니다.
	 *
	 * 참고: 스프링 AOP는 public 메소드만 감시할 수 있습니다.
	 * </pre>
	 *
	 * @param joinPoint 지금 호출되고 있는 서비스 메소드에 대한 정보
	 * @return 원래 서비스 메소드가 반환하는 값을 그대로 돌려줍니다
	 * @throws Throwable 원래 서비스 메소드에서 예외가 발생하면 그 예외를 그대로 다시 던집니다
	 */
	@Around("execution(* net.dstone.ai.api.*..*Service*.*(..))" + " && !" + NO_LOG_REGEX)
	public Object doServiceProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		this.info("+--->[SERVICE ] {" + signatureLog(joinPoint) + "}");
		return joinPoint.proceed();
	}

	/**
	 * <pre>
	 * DAO(Dao가 이름에 들어간 클래스, 데이터베이스에 접근하는 클래스)의 메소드가 호출될 때마다
	 * "어떤 DAO 메소드가 호출됐는지" 한 줄을 로그로 남깁니다.
	 *
	 * 참고: 스프링 AOP는 public 메소드만 감시할 수 있습니다.
	 * </pre>
	 *
	 * @param joinPoint 지금 호출되고 있는 DAO 메소드에 대한 정보
	 * @return 원래 DAO 메소드가 반환하는 값을 그대로 돌려줍니다
	 * @throws Throwable 원래 DAO 메소드에서 예외가 발생하면 그 예외를 그대로 다시 던집니다
	 */
	@Around("execution(* net.dstone.ai.*..*Dao.*(..))")
	public Object doDaoProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
		boolean noLog = method.getAnnotation(net.dstone.common.annotation.NoAspectLog.class) != null;
		try {
			if (noLog) {
				ThreadContext.put("SUPPRESS_SQL_LOG", "Y");
			} else {
				this.info("+----->[DAO   ] {" + signatureLog(joinPoint) + "}");
			}
			return joinPoint.proceed();
		} finally {
			if (noLog) {
				ThreadContext.remove("SUPPRESS_SQL_LOG");
			}
		}
	}

	private final static String SAPERATE_LINE = "\n|--------------------------------------------------------------------------------------------------------------------------------------|\n";
	
	private final static String WORKFLOW_POINTCUT 	= "execution(* net.dstone.ai.runtime.workflow.WorkFlowExecutor.run(..))" + " && !" + NO_LOG_REGEX;
	private final static String STEPRUNNER_POINTCUT = "execution(* net.dstone.ai.runtime.step.StepRunner+.run(..))" + " && !" + NO_LOG_REGEX;
	private final static String AGENT_POINTCUT 		= "execution(* net.dstone.ai.runtime.agent.AgentExecutor.*(..))" + " && !" + NO_LOG_REGEX;
	private final static String TOOL_POINTCUT 		= "execution(* net.dstone.ai.runtime.tool.ToolExecutor.*(..))" + " && !" + NO_LOG_REGEX;
	private final static boolean PARAM_MULTI_LINE 	= false;

	/**
	 * <pre>
	 * Workflow 가 호출 될때마다 로그를 남깁니다.
	 * </pre>
	 * 
	 * @param joinPoint 지금 호출되고 있는 WorkFlowExecutor.run(...) 메소드에 대한 정보
	 * @return 원래 메소드가 반환하는 WorkFlowExecution(실행 결과)을 그대로 돌려줍니다
	 * @throws Throwable 원래 메소드에서 예외가 발생하면 그 예외를 그대로 다시 던집니다
	 */
	@Around(WORKFLOW_POINTCUT)
	public Object doWorkflowLog(ProceedingJoinPoint joinPoint) throws Throwable{
		Object output = null;
		StringBuffer log = new StringBuffer();
		String identity = this.getIdentity(joinPoint);

		log.append("\n");
		log.append(SAPERATE_LINE);
		log.append("[WorkFlowExecutor - "+identity+"] Start !!!");
		log.append("\n");
		log.append("<input>");
		log.append("\n");
		if(PARAM_MULTI_LINE) {
			log.append(""+ this.buildParamInfo(joinPoint) +"");
		}else {
			log.append(""+ StringUtil.replace(this.buildParamInfo(joinPoint), "\n", "") +"");
		}
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		output = joinPoint.proceed();
		
		log.setLength(0);
		log.append("\n");
		log.append(SAPERATE_LINE);
		log.append("[WorkFlowExecutor - "+identity+"] End !!!");
		log.append("\n");
		log.append("<output>");
		log.append("\n");
		if(PARAM_MULTI_LINE) {
			log.append(""+ output.toString() +"");
		}else {
			log.append(""+ StringUtil.replace(output.toString(), "\n", "") +"");
		}
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		return output;
	}

	/**
	 * <pre>
	 * Workflow의 스텝 하나가 실행될 때마다(StepRunner.run(execution, definition, input) 호출될 때마다) 
	 * 그 스텝의 입력과 출력을 로그 한 줄씩으로 남겨줍니다.
	 *
	 * AGENT/TOOL/SUPERVISOR/APPROVAL 등 스텝 종류(StepType)가 다르더라도 전부 똑같은
	 * StepRunner.run(...) 메소드 하나를 거쳐 실행되므로(자세한 내용은 runtime.step.StepRunner
	 * 참고), 이 메소드 하나만 감시해도 모든 스텝의 실행 내역을 다 남길 수 있습니다. 로그에는
	 * "이번 실행에서, 어느 스텝이, 어떤 종류로, 어떤 Agent나 Tool을 불러서, 성공했는지 실패했는지"가
	 * 한 줄로 정리되어 나옵니다.
	 *
	 * 참고로 "다음에 어느 스텝으로 넘어갈지(성공하면 다음 스텝으로, 실패하면 되돌아가는 등)"는
	 * 여기서 로그로 남기지 않습니다. 그 판단은 이 스텝이 다 끝난 뒤에 WorkFlowExecutor가
	 * 내리기 때문에, 이 메소드가 실행되는 시점에는 아직 알 수 없습니다. 대신 같은 executionId로
	 * 로그를 검색해 보면 스텝이 실행된 순서 자체가 곧 Workflow가 진행된 흐름이므로, 그것만으로도
	 * 흐름을 충분히 파악할 수 있습니다.
	 * </pre>
	 *
	 * @param joinPoint 지금 호출되고 있는 StepRunner.run(...) 메소드에 대한 정보
	 * @return 원래 메소드가 반환하는 StepOutcome(이 스텝의 실행 결과)을 그대로 돌려줍니다
	 * @throws Throwable 원래 메소드에서 예외가 발생하면 그 예외를 그대로 다시 던집니다
	 */
	@Around(STEPRUNNER_POINTCUT)
	public Object doStepRunnerLog(ProceedingJoinPoint joinPoint) throws Throwable{
		Object output = null;
		StringBuffer log = new StringBuffer();
		String identity = this.getIdentity(joinPoint);

		log.append("\n");
		log.append(SAPERATE_LINE);
		log.append("[StepRunner - "+identity+"] Start !!!");
		log.append("\n");
		log.append("<input>");
		log.append("\n");
		if(PARAM_MULTI_LINE) {
			log.append(""+ this.buildParamInfo(joinPoint) +"");
		}else {
			log.append(""+ StringUtil.replace(this.buildParamInfo(joinPoint), "\n", "") +"");
		}
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		output = joinPoint.proceed();
		
		log.setLength(0);
		log.append("\n");
		log.append(SAPERATE_LINE);
		log.append("[StepRunner - "+identity+"] End !!!");
		log.append("\n");
		log.append("<output>");
		log.append("\n");		
		if(PARAM_MULTI_LINE) {
			log.append(""+ output.toString() +"");
		}else {
			log.append(""+ StringUtil.replace(output.toString(), "\n", "") +"");
		}
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		return output;
	}

	/**
	 * <pre>
	 * Agent 하나가 실행될 때마다 로깅을 남겨줍니다.
	 * @param joinPoint 지금 호출되고 있는 AgentExecutor.*(...) 메소드에 대한 정보
	 * @return 원래 메소드가 반환하는 객체를 그대로 돌려줍니다
	 * @throws Throwable 원래 메소드에서 예외가 발생하면 그 예외를 그대로 다시 던집니다
	 */
	@Around(AGENT_POINTCUT)
	public Object doAgentLog(ProceedingJoinPoint joinPoint) throws Throwable{
		Object output = null;
		StringBuffer log = new StringBuffer();
		String identity = this.getIdentity(joinPoint);

		log.append("\n");
		log.append(SAPERATE_LINE);
		log.append("[AgentExecutor - "+identity+"] Start !!!");
		log.append("\n");
		log.append("<input>");
		log.append("\n");
		if(PARAM_MULTI_LINE) {
			log.append(""+ this.buildParamInfo(joinPoint) +"");
		}else {
			log.append(""+ StringUtil.replace(this.buildParamInfo(joinPoint), "\n", "") +"");
		}
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		output = joinPoint.proceed();
		
		log.setLength(0);
		log.append("\n");
		log.append(SAPERATE_LINE);
		log.append("[AgentExecutor - "+identity+"] End !!!");
		log.append("\n");
		log.append("<output>");
		log.append("\n");	
		if(PARAM_MULTI_LINE) {
			log.append(""+ output.toString() +"");
		}else {
			log.append(""+ StringUtil.replace(output.toString(), "\n", "") +"");
		}
		log.append(SAPERATE_LINE);
		this.info(log.toString());

		return output;
	}

	
	/**
	 * <pre>
	 * tools 패키지(Agent나 TOOL 스텝이 호출할 수 있는 Tool들이 들어있는 패키지)의 메소드가
	 * 호출될 때마다 "시작"과 "끝" 두 줄을 로그로 남깁니다. 어떤 Tool이 어떤 값으로 호출됐고
	 * 무엇을 돌려줬는지 확인할 수 있습니다.
	 *
	 * 참고: 스프링 AOP는 public 메소드만 감시할 수 있습니다.
	 * </pre>
	 *
	 * @param joinPoint 지금 호출되고 있는 tools 패키지 메소드에 대한 정보
	 * @return 원래 메소드가 반환하는 값을 그대로 돌려줍니다
	 * @throws Throwable 원래 메소드에서 예외가 발생하면 그 예외를 그대로 다시 던집니다
	 */
	@Around(TOOL_POINTCUT)
	public Object doToolsProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		StringBuffer log = new StringBuffer();
		String className = "";
		String methodName = "";
		
		className = joinPoint.getTarget().getClass().getSimpleName();
		methodName = joinPoint.getSignature().getName();
		String identity = className + "." + methodName +"("+ getIdentity(joinPoint) + ")";


		log.append("\n");
		log.append(SAPERATE_LINE);
		log.append("[Tools - "+identity+"] Start !!!");
		log.append("\n");
		log.append("<input>");
		log.append("\n");
		if(PARAM_MULTI_LINE) {
			log.append(""+ this.buildParamInfo(joinPoint) +"");
		}else {
			log.append(""+ StringUtil.replace(this.buildParamInfo(joinPoint), "\n", "") +"");
		}
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		Object output = joinPoint.proceed();

		log.setLength(0);
		log.append("\n");
		log.append(SAPERATE_LINE);
		log.append("[Tools - "+identity+"] End !!!");
		log.append("\n");
		log.append("<output>");
		log.append("\n");	
		if(PARAM_MULTI_LINE) {
			log.append(""+ output.toString() +"");
		}else {
			log.append(""+ StringUtil.replace(output.toString(), "\n", "") +"");
		}
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		return output;
	}
	
	/**
	 * 로그를 읽는 사람이 "이 호출이 정확히 무엇에 관한 것인지" 한눈에 알아볼 수 있도록, 클래스 이름과
	 * 메소드 이름에 덧붙일 짧은 식별자를 만들어 줍니다. 예를 들어 인자로 WorkFlowDefinition을
	 * 받는 메소드라면 "WorkFlowExecutor.run([workFlow(id=agent-basic-echo)])"처럼 어떤 Workflow를
	 * 다루고 있는지가 로그에 그대로 보입니다.
	 *
	 * 인자들을 하나씩 살펴보다가 WorkFlowDefinition, StepDefinition, AgentDefinition 중 하나를
	 * 발견하면 그 즉시 식별자를 만들고 더 이상 찾지 않습니다(하나만 찾아도 충분하기 때문입니다).
	 *
	 * @param joinPoint 식별자를 만들 대상이 되는 메소드 호출 정보
	 */
	public String getIdentity(ProceedingJoinPoint joinPoint) {
		StringBuffer identity = new StringBuffer();
		String div = "-";
		if(joinPoint != null) {
			int args = joinPoint.getArgs().length;
			for (int i = 0; i < args; i++) {
				Object param = joinPoint.getArgs()[i];
				if( param instanceof WorkFlowDefinition ) {
					WorkFlowDefinition workFlowDefinition = (WorkFlowDefinition)param;
					if(identity.length() > 0) {identity.append(div);}
					identity.append("workFlow(id=" + workFlowDefinition.id()+")");
				}else if( param instanceof StepDefinition ) {
					StepDefinition stepDefinition = (StepDefinition)param;
					if(identity.length() > 0) {identity.append(div);}
					identity.append("step(id=" + stepDefinition.id() + ", type="+stepDefinition.type() + ", ref="+stepDefinition.ref()+")");
				}else if( param instanceof AgentDefinition ) {
					AgentDefinition agentDefinition = (AgentDefinition)param;
					if(identity.length() > 0) {identity.append(div);}
					identity.append("agent(id=" + agentDefinition.id()+")");
				}
			}
		}
		return identity.toString();
	}

	/****************************************** 로깅 관련 AOP 설정 종료 ******************************************/

}
