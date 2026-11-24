package net.dstone.ai.common.config;

import java.lang.reflect.Method;
import java.util.function.Function;

import org.apache.logging.log4j.ThreadContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import net.dstone.ai.common.definition.agent.AgentDefinition;
import net.dstone.ai.common.definition.workflow.WorkFlowDefinition;
import net.dstone.ai.common.definition.workflow.step.StepDefinition;
import net.dstone.ai.common.exec.ExecContext;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.ConvertUtil;
import net.dstone.common.utils.DateUtil;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.SpringUtil;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Flux;

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
	/** 
	 * @NoAspectLog 애노테이션이 붙은 메소드는 로깅 대상에서 제외하기 위한 표현식입니다. 
	 */
	private final static String NO_LOG_REGEX 			= "@annotation(net.dstone.common.annotation.NoAspectLog)";

	public static boolean IS_LLM_LOGGING_YN 			= true;
	private final static String SAPERATE_LINE 			= "|--------------------------------------------------------------------------------------------------------------------------------------|";
	
	public final static String SAPERATE_LINE_HALF_PRONT = "\n|----------------------------------------------------";
	public final static String SAPERATE_LINE_HALF_END 	= "----------------------------------------------------|\n";
	
	private final static String WORKFLOW_POINTCUT 		= "execution(* net.dstone.ai.runtime.workflow.WorkFlowExecutor.run(..))" + " && !" + NO_LOG_REGEX;
	private final static String STEPEXECUTOR_POINTCUT 	= "execution(* net.dstone.ai.runtime.step.*StepExecutor.run(..))" + " && !" + NO_LOG_REGEX;
	private final static String AGENT_POINTCUT 			= "execution(* net.dstone.ai.runtime.agent.AgentExecutor.*(..))" + " && !" + NO_LOG_REGEX;
	private final static String TOOL_POINTCUT 			= "execution(* net.dstone.ai.runtime.tool.ToolCallGate.invoke(..))" + " && !" + NO_LOG_REGEX;
	private final static boolean PARAM_MULTI_LINE 		= false;

	
	/****************************************** 로깅 관련 AOP 설정 시작 ******************************************/
	
	private static String getSaperateLine(String space) {
		String line = "\n" + StringUtil.ifEmpty(space, "") + SAPERATE_LINE + "\n";
		return line;
	}

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
		this.info("+->[CONTROLLER] {" + this.trimParams( signatureLog(joinPoint)) + "}");

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
		this.info("+--->[SERVICE ] {" + this.trimParams( signatureLog(joinPoint)) + "}");
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
				this.info("+----->[DAO   ] {" + this.trimParams(signatureLog(joinPoint)) + "}");
			}
			return joinPoint.proceed();
		} finally {
			if (noLog) {
				ThreadContext.remove("SUPPRESS_SQL_LOG");
			}
		}
	}

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
		
		String className = "";
		String methodName = "";
		className = joinPoint.getTarget().getClass().getSimpleName();
		methodName = joinPoint.getSignature().getName();
		String calleeInfo = " Call 정보 : " + className + "." + methodName + "()";

		log.append("\n");
		log.append(getSaperateLine(""));
		log.append("[WorkFlowExecutor - "+identity+"]"+calleeInfo+" Start !!!" );
		log.append("\n");
		log.append("<input>");
		log.append("\n");
		log.append(this.trimParams(this.buildParamInfo(joinPoint)));
		log.append("\n");
		log.append("</input>");
		log.append(getSaperateLine(""));
		this.info(log.toString());
		
		output = joinPoint.proceed();
		
		log.setLength(0);
		log.append("\n");
		log.append(getSaperateLine(""));
		log.append("[WorkFlowExecutor - "+identity+"]"+calleeInfo+" End !!!");
		log.append("\n");
		log.append("<output>");
		log.append("\n");
		log.append(this.trimParams(output.toString()));
		log.append("\n");
		log.append("</output>");
		log.append(getSaperateLine(""));
		this.info(log.toString());
		
		return output;
	}

	/**
	 * <pre>
	 * Workflow의 스텝 하나가 실행될 때마다(runtime.step의 각 *StepExecutor.run(...)이 호출될 때마다) 
	 * 그 스텝의 입력과 출력을 로그 한 줄씩으로 남겨줍니다.
	 *
	 * AGENT/SUPERVISOR/ROUTER/TOOL/APPROVAL 스텝 종류마다 StepExecutor가 하나씩 있고, 모두 run(...) 메소드
	 * 하나로 실행되도록 이름을 맞춰 두었습니다(AgentStepExecutor.run, ToolStepExecutor.run 등).
	 * 그래서 "runtime.step 패키지의 *StepExecutor.run" 하나만 감시해도 모든 스텝의 실행 내역을 다 남길 수 있습니다. 로그에는
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
	 * @param joinPoint 지금 호출되고 있는 *StepExecutor.run(...) 메소드에 대한 정보
	 * @return 원래 메소드가 반환하는 StepOutcome(이 스텝의 실행 결과)을 그대로 돌려줍니다
	 * @throws Throwable 원래 메소드에서 예외가 발생하면 그 예외를 그대로 다시 던집니다
	 */
	@Around(STEPEXECUTOR_POINTCUT)
	public Object doStepExecutorLog(ProceedingJoinPoint joinPoint) throws Throwable{
		Object output = null;
		StringBuffer log = new StringBuffer();
		String identity = this.getIdentity(joinPoint);

		String className = "";
		String methodName = "";
		className = joinPoint.getTarget().getClass().getSimpleName();
		methodName = joinPoint.getSignature().getName();
		String calleeInfo = " Call 정보 : " + className + "." + methodName + "()";

		log.append("\t").append("\n");
		log.append("\t").append(getSaperateLine("\t"));
		log.append("\t").append("[StepExecutor - "+identity+"]"+calleeInfo+" Start !!!" );
		log.append("\t").append("\n");
		log.append("\t").append("<input>");
		log.append("\t").append("\n");
		log.append("\t").append(this.trimParams(this.buildParamInfo(joinPoint)));
		log.append("\t").append("\n");
		log.append("\t").append("</input>");
		this.info(log.toString());
		
		output = joinPoint.proceed();
		
		log.append("\t").setLength(0);
		log.append("\t").append("\n");
		log.append("\t").append("<output>");
		log.append("\t").append("\n");		
		log.append("\t").append(this.trimParams(output.toString()));
		log.append("\t").append("\n");
		log.append("\t").append("</output>");
		log.append("\t").append("\n");
		log.append("\t").append("[StepExecutor - "+identity+"]"+calleeInfo+" End !!!");
		log.append("\t").append(getSaperateLine("\t"));
		log.append("\t").append("\n");
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

		String className = "";
		String methodName = "";
		className = joinPoint.getTarget().getClass().getSimpleName();
		methodName = joinPoint.getSignature().getName();
		String calleeInfo = " Call 정보 : " + className + "." + methodName + "()";

		log.append("\t\t").append("\n");
		log.append("\t\t").append(getSaperateLine("\t\t"));
		log.append("\t\t").append("[AgentExecutor - "+identity+"]"+calleeInfo+" Start !!!" );
		log.append("\t\t").append("\n");
		log.append("\t\t").append("<input>");
		log.append("\t\t").append("\n");
		log.append("\t\t").append(this.trimParams(this.buildParamInfo(joinPoint)));
		log.append("\t\t").append("\n");
		log.append("\t\t").append("</input>");
		log.append("\t\t").append(getSaperateLine("\t\t"));
		this.info(log.toString());
		
		output = joinPoint.proceed();
		
		log.append("\t\t").setLength(0);
		log.append("\t\t").append("\n");
		log.append("\t\t").append(getSaperateLine("\t\t"));
		log.append("\t\t").append("<output>");
		log.append("\t\t").append("\n");	
		log.append("\t\t").append(this.trimParams(output.toString()));
		log.append("\t\t").append("\n");
		log.append("\t\t").append("</output>");
		log.append("\t\t").append("\n");
		log.append("\t\t").append("[AgentExecutor - "+identity+"]"+calleeInfo+" End !!!");
		log.append("\t\t").append(getSaperateLine("\t\t"));
		this.info(log.toString());

		return output;
	}

	
	/**
	 * <pre>
	 * Tool이 실행될 때마다 "시작"과 "끝" 두 줄을 로그로 남깁니다. 어떤 Tool이 어떤 값으로 호출됐고
	 * 무엇을 돌려줬는지 확인할 수 있습니다.
	 *
	 * TOOL step이든, AGENT step에서 LLM이 고른 Tool이든, MCP·Skill·Sub Agent든 실행은 모두
	 * runtime.tool.ToolCallGate.invoke(...)를 지나므로 그 메소드 하나만 감시합니다.
	 * 앞 단계에서 막혀 실행되지 않은 호출(예산 초과, 같은 파일 다시 읽기, 끊긴 인자 등)은 남지 않습니다.
	 * 결과는 자르기 전의 원래 값입니다. 인자 중 ToolCallback은 내부 객체라 로그에서 뺍니다.
	 * </pre>
	 *
	 * @param joinPoint 지금 호출되고 있는 ToolCallGate.invoke(...)에 대한 정보
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
		String calleeInfo = " Call 정보 : " + className + "." + methodName + "()";
		String toolName = "";
		
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		String[] paramNames = signature.getParameterNames();
		Object[] paramValues = joinPoint.getArgs();
		int minLength = Math.min(paramNames.length, paramValues.length);
		for (int i = 0; i < minLength; i++) {
		    String name = paramNames[i];
		    Object value = paramValues[i];
			if( "toolName".equals(name) && value != null ) {
				toolName = value.toString();
				break;
			}
		}

		log.append("\t\t").append("\n");
		log.append("\t\t").append(getSaperateLine("\t\t"));
		log.append("\t\t").append("[Tools - "+toolName+"]"+calleeInfo+" Start !!!" );
		log.append("\t\t").append("\n");
		log.append("\t\t").append("<input>");
		log.append("\t\t").append("\n");
		log.append("\t\t").append(this.trimParams(this.buildParamInfo(joinPoint)));
		log.append("\t\t").append("\n");
		log.append("\t\t").append("</input>");
		this.info(log.append("\t\t").toString());
		
		Object output = joinPoint.proceed();

		log.append("\t\t").setLength(0);
		log.append("\t\t").append("\n");
		log.append("\t\t").append("<output>");
		log.append("\t\t").append("\n");	
		log.append("\t\t").append(this.trimParams(output.toString()));
		log.append("\t\t").append("\n");
		log.append("\t\t").append("</output>");
		log.append("\t\t").append("\n");
		log.append("\t\t").append("[Tools - "+toolName+"]"+calleeInfo+" End !!!" );
		log.append("\t\t").append(getSaperateLine("\t\t"));
		this.info(log.append("\t\t").toString());
		
		return output;
	}
	
	/**
	 * <pre>
	 * Tool 호출 로그에 남길 인자 목록을 "이름=[값]" 모양으로 만듭니다.
	 * ToolCallback은 내부 객체라 통째로 찍으면 읽을 수 없으므로 빼고, ToolContext는 안에 든 값만 찍습니다.
	 * </pre>
	 *
	 * @param paramNames  인자 이름들
	 * @param paramValues 인자 값들
	 */
	private String buildToolParamInfo(String[] paramNames, Object[] paramValues) {
		StringBuffer info = new StringBuffer();
		if (paramNames == null || paramValues == null) {
			return "";
		}
		int minLength = Math.min(paramNames.length, paramValues.length);
		for (int i = 0; i < minLength; i++) {
			Object value = paramValues[i];
			if (value instanceof ToolCallback) {
				continue;
			}
			if (info.length() > 0) {
				info.append(", ");
			}
			if (value instanceof ToolContext) {
				value = ((ToolContext) value).getContext();
			}
			info.append(paramNames[i]).append("=[").append(String.valueOf(value)).append("]");
		}
		return info.toString();
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
		if (joinPoint != null) {
			int args = joinPoint.getArgs().length;
			for (int i = 0; i < args; i++) {
				Object param = joinPoint.getArgs()[i];
				if (param instanceof WorkFlowDefinition) {
					WorkFlowDefinition workFlowDefinition = (WorkFlowDefinition) param;
					if (identity.length() > 0) {
						identity.append(div);
					}
					identity.append("workFlow(id=" + workFlowDefinition.id() + ")");
				} else if (param instanceof StepDefinition) {
					StepDefinition stepDefinition = (StepDefinition) param;
					if (identity.length() > 0) {
						identity.append(div);
					}
					identity.append("step(id=" + stepDefinition.id() + ", type=" + stepDefinition.type() + ", ref=" + StepDefinition.refOf(stepDefinition) + ")");
				}else if( param instanceof AgentDefinition ) {
					AgentDefinition agentDefinition = (AgentDefinition)param;
					if(identity.length() > 0) {identity.append(div);}
					identity.append("agent(id=" + agentDefinition.id()+")");
				}
			}
		}
		return identity.toString();
	}
	

	private String trimParams(String input) {
		String output = input;
		if(!PARAM_MULTI_LINE) {
			output = this.removeCarrageReturn(output) ;
		}
		return output;
	}
	
	private String removeCarrageReturn(String input) {
		String output = "";
		if( input != null ) {
			output = input;
			output = StringUtil.textTail(output, 500);
			output = StringUtil.replace(output, "\r\n", "");
			output = StringUtil.replace(output, "\n", "");
		}
		return output;
	}

	/****************************************** 로깅 관련 AOP 설정 종료 ******************************************/

}
