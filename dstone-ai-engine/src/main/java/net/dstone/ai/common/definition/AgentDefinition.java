package net.dstone.ai.common.definition;

import java.util.List;

/**
 * resources/agents/*.yml 파일에 등록된 Agent 하나("LLM에게 일을 시키는 단위" - promptName으로
 * 무엇을 시스템 프롬프트로 쓸지, toolsEnabled/ragEnabled로 Tool/RAG를 붙일지를 갖고 있다).
 *
 * description은 WorkflowDefinition.description과 같은 성격이다 - 순수 문서화용이고, 코드 어디서도
 * 읽지 않는다(LLM에게 보내는 실제 내용은 항상 promptName이 가리키는 .st 파일이 전담한다). "이 Agent가
 * 뭘 하는 건지" YAML만 보고 알 수 있게 하려는 목적일 뿐이다.
 *
 * api.controller.ChatController가 request.agent()로 직접 가리킬 수도 있고, StepType.AGENT/SUPERVISOR
 * step의 ref가 가리킬 수도 있다 - 어느 경로로 오든 caller 검증은 이 정의의 allowedCallers로 동일하게
 * 이뤄진다(common.registry.AgentRegistry.resolve() 참고).
 */
public record AgentDefinition(String name, String description, String promptName, boolean toolsEnabled,
		boolean ragEnabled, List<String> allowedCallers) {
}
