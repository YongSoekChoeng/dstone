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

@Aspect
@Component
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ConfigCallLog extends BaseObject {

	/****************************************** 로깅 관련 AOP 설정 시작 ******************************************/
	private final static String NO_LOG_REGEX = "@annotation(net.dstone.common.annotation.NoAspectLog)";

	/**
	 * <pre>
	 * 컨트롤러 메소드 로깅.(AOP는 public 메소드에 대해서만 캐치할 수 있음)
	 * </pre>
	 *
	 * @param joinPoint 가로챈 컨트롤러 메소드 호출 지점
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.ai.*..*Controller.*(..))" + " && !" + NO_LOG_REGEX)
	public Object doControllerProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		this.sysout("\n\n||===================================== [" + joinPoint.getTarget().getClass().getName() + "] START ======================================||");
		this.info("+->[CONTROLLER] {" + signatureLog(joinPoint) + "}");

		/*****************************************************************************************************
		 * 컨트롤러 호출 시 응답헤더에 기본값 세팅 - Response 헤더[successYn]에 "Y"를 자동세팅한다. 컨크롤러 로직 수행중 오류 발생 시(setErrCd 호출 시) 자동으로 "N"으로 세팅된다. -
		 * Exception 발생 시 DsExceptionResolver에 의해 Response 헤더[successYn]는 N"으로 자동세팅된다.
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
		 * 객체 실행
		 *****************************************************************************************************/
		Object retObj = joinPoint.proceed();
		this.sysout("||===================================== [" + joinPoint.getTarget().getClass().getName() + "] END ======================================||\n");
		return retObj;
	}

	/**
	 * <pre>
	 * 서비스 메소드 로깅.(AOP는 public 메소드에 대해서만 캐치할 수 있음)
	 * </pre>
	 *
	 * @param joinPoint 가로챈 서비스 메소드 호출 지점
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.ai.*..*Service*.*(..))" + " && !" + NO_LOG_REGEX)
	public Object doServiceProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		this.info("+--->[SERVICE ] {" + signatureLog(joinPoint) + "}");
		return joinPoint.proceed();
	}

	/**
	 * <pre>
	 * DAO 메소드 로깅.(AOP는 public 메소드에 대해서만 캐치할 수 있음)
	 * </pre>
	 *
	 * @param joinPoint 가로챈 DAO 메소드 호출 지점
	 * @return
	 * @throws Throwable
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

	/**
	 * <pre>
	 * runtime 패키지 메소드 로깅.(AOP는 public 메소드에 대해서만 캐치할 수 있음)
	 * </pre>
	 *
	 * @param joinPoint 가로챈 runtime 패키지 메소드 호출 지점
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.ai.runtime.*..*.*(..))" + " && !" + NO_LOG_REGEX + " && !execution(* net.dstone.ai.runtime.step.StepRunner+.run(..))")
	public Object doRuntimeProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		String identity = getIdentity(joinPoint);
		this.info("+----->[Runtime - "+identity+"] Start {" + signatureLog(joinPoint) + "}");
		Object retObj = joinPoint.proceed();
		this.info("+----->[Runtime - "+identity+"] End {" + retObj + "}");
		return retObj;
	}

	/** Step IN/OUT 전용 로거 - net.dstone.ai 하위라 log4j2.xml의 net.dstone.ai Logger 설정(콘솔+execution.log)을 그대로 물려받는다. */
	private static final Logger STEP_AUDIT_LOG = LogManager.getLogger("net.dstone.ai.workflow.audit");

	/** 로그 한 줄에 담는 입력/출력 텍스트의 최대 길이 - 이보다 길면 잘라서 "...(생략)"을 붙인다. */
	private static final int STEP_AUDIT_MAX_CHARS = 500;

	/**
	 * <pre>
	 * StepRunner.run(execution, definition, input) 전용 로깅. 모든 StepType(AGENT/TOOL/SUPERVISOR/APPROVAL)이
	 * 이 시그니처 하나로 호출되므로(runtime.step.StepRunner 참고), 이 advice 하나만으로 모든 스텝의 IN/OUT을
	 * "이 실행에서, 이 스텝이, 무슨 유형으로, 어떤 Agent/Tool을 불러서, 어떻게 끝났는지" 한 줄로 남긴다.
	 *
	 * 흐름 제어(NEXT_STEP/LOOP/SUCCESS/FAIL)는 여기서 안 남긴다 - 그건 이 메서드가 끝난 뒤 WorkFlowExecutor가
	 * 결정하는 것이라 이 시점엔 알 수 없다. 같은 executionId로 로그를 grep하면 스텝이 실행된 순서 자체가
	 * 곧 흐름이므로 그걸로 충분하다.
	 * </pre>
	 *
	 * @param joinPoint 가로챈 StepRunner.run(..) 호출 지점
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.ai.runtime.step.StepRunner+.run(..))")
	public Object doStepAuditLog(ProceedingJoinPoint joinPoint) throws Throwable {
		WorkFlowExecution execution = (WorkFlowExecution) joinPoint.getArgs()[0];
		StepDefinition step = (StepDefinition) joinPoint.getArgs()[1];
		StepInput input = (StepInput) joinPoint.getArgs()[2];

		STEP_AUDIT_LOG.info("WF_STEP phase=START executionId={} workflowId={} stepId={} stepType={} ref={} input=\"{}\"",
			execution.executionId(), execution.workflowId(), step.id(), step.type(), step.ref(), this.truncate(input.renderedText()));

		long start = System.nanoTime();
		try {
			StepOutput output = (StepOutput) joinPoint.proceed();
			long durationMs = (System.nanoTime() - start) / 1_000_000;
			if (output.failureReason() != null) {
				STEP_AUDIT_LOG.info("WF_STEP phase=END   executionId={} workflowId={} stepId={} stepType={} ref={} result={} durationMs={} failureReason=\"{}\"",
					execution.executionId(), execution.workflowId(), step.id(), step.type(), step.ref(), output.result(), durationMs, this.truncate(output.failureReason()));
			} else {
				STEP_AUDIT_LOG.info("WF_STEP phase=END   executionId={} workflowId={} stepId={} stepType={} ref={} result={} durationMs={} output=\"{}\"",
					execution.executionId(), execution.workflowId(), step.id(), step.type(), step.ref(), output.result(), durationMs, this.truncate(output.primaryText()));
			}
			return output;
		} catch (Throwable e) {
			long durationMs = (System.nanoTime() - start) / 1_000_000;
			STEP_AUDIT_LOG.info("WF_STEP phase=END   executionId={} workflowId={} stepId={} stepType={} ref={} result=ERROR durationMs={} error=\"{}\"",
				execution.executionId(), execution.workflowId(), step.id(), step.type(), step.ref(), durationMs, e.getMessage());
			throw e;
		}
	}

	/** @param text 로그 한 줄 길이를 넘지 않도록 자를 텍스트 */
	private String truncate(String text) {
		if (text == null) {
			return "";
		}
		return text.length() > STEP_AUDIT_MAX_CHARS ? text.substring(0, STEP_AUDIT_MAX_CHARS) + "...(생략)" : text;
	}

	/**
	 * <pre>
	 * tools 패키지 메소드 로깅.(AOP는 public 메소드에 대해서만 캐치할 수 있음)
	 * </pre>
	 *
	 * @param joinPoint 가로챈 tools 패키지 메소드 호출 지점
	 * @return
	 * @throws Throwable
	 */
	@Around("execution(* net.dstone.ai.tools.*..*.*(..))" + " && !" + NO_LOG_REGEX)
	public Object doToolsProfiling(ProceedingJoinPoint joinPoint) throws Throwable {
		String identity = getIdentity(joinPoint);
		this.info("+----->[Tools - "+identity+"] Start {" + signatureLog(joinPoint) + "}");
		Object retObj = joinPoint.proceed();
		this.info("+----->[Tools - "+identity+"] End {" + getIdentity(joinPoint) + " Return [" + retObj + "] }");
		return retObj;
	}
	
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
					// prompt 원문은 여러 줄짜리 큰 텍스트라 로그 한 줄에 담지 않는다(name만으로도 어떤 Agent인지는 충분히 식별된다).
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
