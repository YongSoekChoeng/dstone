package net.dstone.ai.common.config;

import java.lang.reflect.Method;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import net.dstone.ai.runtime.status.StepInput;
import net.dstone.ai.runtime.status.StepOutput;
import net.dstone.ai.runtime.workflow.execution.WorkFlowExecution;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * 이 클래스 하나가 dstone-ai-engine 전체의 실행 흐름을 자동으로 로그로 남겨줍니다.
 *
 * "AOP(관점 지향 프로그래밍)"라는 스프링 기능을 사용합니다. 쉽게 말해, 우리가 로그를 남기고 싶은
 * 메소드마다 직접 로그 코드를 써넣지 않아도, 이 클래스에 적어둔 규칙(어떤 패키지의 어떤 메소드를
 * 감시할지)에 해당하는 메소드가 호출될 때마다 스프링이 자동으로 이 클래스의 코드를 먼저/나중에
 * 실행해줍니다. 그래서 컨트롤러·서비스·DAO·runtime 패키지·tools 패키지의 메소드를 호출할 때마다
 * "누가 무엇을 호출했고 어떤 값을 주고받았는지"가 자동으로 로그에 남습니다.
 *
 * 다만 AOP는 public 메소드에만 적용됩니다(private/protected 메소드는 감시할 수 없습니다). 그리고
 * @NoAspectLog 애노테이션이 붙은 메소드는 이 로깅 대상에서 제외됩니다(예: SQL 로그를 억제하고
 * 싶을 때 사용).
 */
@Aspect
@Component
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ConfigCallLog extends BaseObject {

	/****************************************** 로깅 관련 AOP 설정 시작 ******************************************/
	/** @NoAspectLog 애노테이션이 붙은 메소드는 로깅 대상에서 제외하기 위한 표현식입니다. */
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
	private final static String SAPERATE_LINE_FRONT = "\n|-------------------------------------------";
	private final static String SAPERATE_LINE_END = "-------------------------------------------|\n";
	
	/**
	 * <pre>
	 * runtime 패키지(Workflow를 실제로 실행하는 엔진 코드가 들어있는 패키지)의 메소드가 호출될
	 * 때마다 "시작"과 "끝" 두 줄을 로그로 남깁니다. 시작할 때는 어떤 메소드가 어떤 값으로
	 * 호출됐는지, 끝날 때는 그 결과가 무엇인지 남기므로, Workflow가 내부적으로 어떤 순서로
	 * 동작하는지 로그만 보고도 따라갈 수 있습니다.
	 *
	 * 단, StepRunner.run(...) 메소드는 이 로깅에서 제외됩니다. 그 메소드는 바로 아래의
	 * doStepAuditLog()가 훨씬 더 보기 좋은 형태로 따로 로그를 남기기 때문입니다.
	 *
	 * 참고: 스프링 AOP는 public 메소드만 감시할 수 있습니다.
	 * </pre>
	 *
	 * @param joinPoint 지금 호출되고 있는 runtime 패키지 메소드에 대한 정보
	 * @return 원래 메소드가 반환하는 값을 그대로 돌려줍니다
	 * @throws Throwable 원래 메소드에서 예외가 발생하면 그 예외를 그대로 다시 던집니다
	 */
	@Around("execution(* net.dstone.ai.runtime.*..*.*(..))" + " && !" + NO_LOG_REGEX + " && !execution(* net.dstone.ai.runtime.step.StepRunner+.run(..))")
	public Object doRuntimeProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		StringBuffer log = new StringBuffer();
		String identity = getIdentity(joinPoint);
		
		log.append("\n");
		log.append(SAPERATE_LINE_FRONT+"[Runtime - "+identity+"] Start"+SAPERATE_LINE_END);
		log.append( signatureLog(joinPoint) );
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		Object retObj = joinPoint.proceed();
		
		log.setLength(0);
		log.append("\n");
		log.append(SAPERATE_LINE_FRONT+"[Runtime - "+identity+"] End"+SAPERATE_LINE_END);
		log.append(retObj);
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		return retObj;
	}

	/**
	 * <pre>
	 * Workflow의 스텝 하나가 실행될 때마다(StepRunner.run(execution, definition, input) 호출될
	 * 때마다) 그 스텝의 입력과 출력을 로그 한 줄씩으로 남겨줍니다.
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
	 * @return 원래 메소드가 반환하는 StepOutput(이 스텝의 실행 결과)을 그대로 돌려줍니다
	 * @throws Throwable 원래 메소드에서 예외가 발생하면 그 예외를 그대로 다시 던집니다
	 */
	@Around("execution(* net.dstone.ai.runtime.step.StepRunner+.run(..))")
	public Object doStepAuditLog(ProceedingJoinPoint joinPoint) throws Throwable{
		StepOutput output = null;
		StringBuffer log = new StringBuffer();
		String identity = "";
		WorkFlowExecution execution = (WorkFlowExecution) joinPoint.getArgs()[0];
		StepDefinition step = (StepDefinition) joinPoint.getArgs()[1];
		StepInput input = (StepInput) joinPoint.getArgs()[2];		
		identity = "StepRunner.run([workflowId="+execution.workflowId()+" step="+step.id()+"])";
		
		log.append("\n");
		log.append(SAPERATE_LINE_FRONT+"[Runtime - "+identity+"] Start"+SAPERATE_LINE_END);
		log.append("executionId={"+execution.executionId()+"}");
		log.append(", workflowId={"+execution.workflowId()+"}");
		log.append(", stepId={"+step.id()+"}");
		log.append(", stepType={"+step.type()+"}");
		log.append(", ref={"+step.ref()+"}");
		log.append(", input={"+this.truncate(input.renderedText())+"}");
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		output = (StepOutput) joinPoint.proceed();
		
		log.setLength(0);
		log.append("\n");
		log.append(SAPERATE_LINE_FRONT+"[Runtime - "+identity+"] End"+SAPERATE_LINE_END);
		log.append("executionId={"+execution.executionId()+"}");
		log.append(", workflowId={"+execution.workflowId()+"}");
		log.append(", stepId={"+step.id()+"}");
		log.append(", stepType={"+step.type()+"}");
		log.append(", ref={"+step.ref()+"}");
		log.append(", result={"+output.result()+"}");
		log.append(", output={"+this.truncate(output.primaryText())+"}");
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		return output;
		
		
	}

	/**
	 * 여러 줄로 된 텍스트를 로그 한 줄에 깔끔하게 담기 위해, 줄바꿈 문자를 전부 지워서
	 * 한 줄짜리 텍스트로 만들어 줍니다.
	 *
	 * @param text 한 줄로 줄여서 로그에 보여줄 원본 텍스트
	 */
	private String truncate(String text) {
		if (text == null) {
			return "";
		}
		text = StringUtil.replace(text, "\r\n", "");
		text = StringUtil.replace(text, "\n", "");
		return text;
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
	@Around("execution(* net.dstone.ai.tools.*..*.*(..))" + " && !" + NO_LOG_REGEX)
	public Object doToolsProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		StringBuffer log = new StringBuffer();
		String identity = getIdentity(joinPoint);

		log.append("\n");
		log.append(SAPERATE_LINE_FRONT+"[Tools - "+identity+"] Start"+SAPERATE_LINE_END);
		log.append( signatureLog(joinPoint) );
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		Object retObj = joinPoint.proceed();
		
		log.setLength(0);
		log.append("\n");
		log.append(SAPERATE_LINE_FRONT+"[Tools - "+identity+"] End"+SAPERATE_LINE_END);
		log.append(retObj);
		log.append(SAPERATE_LINE);
		this.info(log.toString());
		
		return retObj;
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
		String className = "";
		String methodName = "";
		String identity = "";
		if(joinPoint != null) {
			className = joinPoint.getTarget().getClass().getSimpleName();
			methodName = joinPoint.getSignature().getName();
			int args = joinPoint.getArgs().length;
			boolean isSelcted = false;
			for (int i = 0; i < args; i++) {
				Object param = joinPoint.getArgs()[i];
				if( param instanceof WorkFlowDefinition ) {
					WorkFlowDefinition workFlowDefinition = (WorkFlowDefinition)param;
					identity = "workFlow(id=" + workFlowDefinition.id()+")" ;
					isSelcted = true;
				}else if( param instanceof StepDefinition ) {
					StepDefinition stepDefinition = (StepDefinition)param;
					identity = "step(id=" + stepDefinition.id() + ", type="+stepDefinition.type() + ", ref="+stepDefinition.ref()+")" ;
					isSelcted = true;
				}else if( param instanceof AgentDefinition ) {
					AgentDefinition agentDefinition = (AgentDefinition)param;
					// Agent의 prompt 원문은 여러 줄짜리 긴 텍스트라 로그 한 줄에 담기엔 너무 깁니다.
					// name만 남겨도 어떤 Agent인지 충분히 알아볼 수 있으므로 name만 사용합니다.
					identity = "agent(name=" + agentDefinition.name() + ")" ;
					isSelcted = true;
				}
				if(isSelcted) {
					break;
				}
			}
		}
		return (className + "." + methodName + "(" + (StringUtil.isEmpty(identity)?"":"[") + identity + (StringUtil.isEmpty(identity)?"":"]") + ")");
	}

	/****************************************** 로깅 관련 AOP 설정 종료 ******************************************/

}
