package net.dstone.ai.common.advisors;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;

import net.dstone.ai.common.definition.agent.AgentDefinition;
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
 * LLM 을 호출할 때의 실질적인 IN/OUT 을 로깅하는 클래스.
 * </pre>
 */
public class LlmLoggerAdvisor extends BaseObject implements CallAdvisor, StreamAdvisor {

	private ConfigProperty configProperty;
	private int order;
	
	public LlmLoggerAdvisor(ConfigProperty configProperty, int order) {
		this.configProperty = configProperty;
		this.order = order;
	}

	protected void logRequest(ChatClientRequest request) {
		
		StringBuffer log = new StringBuffer();
		log.append("\n");
		log.append(net.dstone.ai.common.config.ConfigCallLog.SAPERATE_LINE_HALF_PRONT);
		log.append("[LLM Call Request] Start !!!" );
		log.append(net.dstone.ai.common.config.ConfigCallLog.SAPERATE_LINE_HALF_END);
		log.append("\n");
		log.append(ConvertUtil.convertToJson(request));
		log.append("\n");
		log.append(net.dstone.ai.common.config.ConfigCallLog.SAPERATE_LINE_HALF_PRONT);
		log.append("[LLM Call Request] End !!!" );
		log.append(net.dstone.ai.common.config.ConfigCallLog.SAPERATE_LINE_HALF_END);
		this.writeLog("REQ", log.toString());
	}
	
	protected void logResponse(ChatClientResponse response) {
		StringBuffer log = new StringBuffer();
		log.append("\n");
		log.append(net.dstone.ai.common.config.ConfigCallLog.SAPERATE_LINE_HALF_PRONT);
		log.append("[LLM Call Response] Start !!!" );
		log.append(net.dstone.ai.common.config.ConfigCallLog.SAPERATE_LINE_HALF_END);
		log.append("\n");
		log.append(ConvertUtil.convertToJson(response));
		log.append("\n");
		log.append(net.dstone.ai.common.config.ConfigCallLog.SAPERATE_LINE_HALF_PRONT);
		log.append("[LLM Call Response] End !!!" );
		log.append(net.dstone.ai.common.config.ConfigCallLog.SAPERATE_LINE_HALF_END);
		this.writeLog("RES", log.toString());
	}
	
	private void writeLog(String reqRes, String msg) {
		if(configProperty == null) {
			configProperty = (ConfigProperty)SpringUtil.getBean(ConfigProperty.class);
		}
		String logPath = configProperty.getProperty("APP_HOME") + "/LOGS/" + configProperty.getProperty("APP_NAME") + "/llm/"+DateUtil.getToDate("yyyyMMdd");
		String logFile =  DateUtil.getToDate("yyyyMMddHHmmss") + "-" + StringUtil.getRandomNumber(6);
		
		WorkFlowExecution workFlowExecution = null;
		StepDefinition stepDefinition = null;
		AgentDefinition agentDefinition = null;

		if( ExecContext.getInstance().existKey("WorkFlowExecution") ) {
			workFlowExecution = (WorkFlowExecution)ExecContext.getInstance().get("WorkFlowExecution");
			logPath = logPath + "/" + workFlowExecution.workflowId() + "/" + workFlowExecution.executionId();
			logFile = DateUtil.getToDate("HHmmss");
			if( ExecContext.getInstance().existKey("StepDefinition") ) {
				stepDefinition = (StepDefinition)ExecContext.getInstance().get("StepDefinition");
				if( !StringUtil.isEmpty(stepDefinition.id()) ) {
					logFile = logFile + "-" + stepDefinition.id();
				}
			}
			if( ExecContext.getInstance().existKey("AgentDefinition") ) {
				agentDefinition = (AgentDefinition)ExecContext.getInstance().get("AgentDefinition");
				if( !StringUtil.isEmpty(agentDefinition.id()) ) {
					logFile = logFile + "-" + agentDefinition.id();
				}
			}
		}
		logFile = logFile + "-" + reqRes + ".log";
		FileUtil.writeFile(logPath, logFile, msg);
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}
	@Override
	public int getOrder() {
		return this.order;
	}
	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		logRequest(chatClientRequest);
		ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
		logResponse(chatClientResponse);
		return chatClientResponse;
	}
	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
		logRequest(chatClientRequest);
		Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);
		return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses, this::logResponse);
	}

}
