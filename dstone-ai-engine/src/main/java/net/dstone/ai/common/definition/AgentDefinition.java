package net.dstone.ai.common.definition;

import java.util.List;

/**
 * resources/agents/*.yml 파일에 등록된 Agent 하나("LLM에게 일을 시키는 단위" - promptName으로 무엇을 시스템 프롬프트로 쓸지, toolsEnabled/ragEnabled로
 * Tool/RAG를 붙일지를 갖고 있다). net.dstone.ai.common.loader.YamlDefinitionLoader 에 의해서 로딩된다.
 * 
 * api.controller.ChatController가 request.agent()로 직접 가리킬 수도 있고, StepType.AGENT/SUPERVISOR step의 ref가 가리킬 수도 있다. 어느 경로로
 * 오든 caller 검증은 이 정의의 allowedCallers로 동일하게 이뤄진다(common.registry.AgentRegistry.resolve() 참고).
 * 
 * @param name           Agent 이름
 * @param description    Agent 설명(문서화용, 코드에서는 읽지 않음)
 * @param promptName     시스템 프롬프트로 쓸 프롬프트 템플릿 이름
 * @param toolsEnabled   Tool 사용 허용 여부
 * @param ragEnabled     RAG 사용 허용 여부
 * @param allowedCallers 이 Agent 호출이 허용된 caller(tenant) 목록
 */
public record AgentDefinition(String name, String description, String promptName, boolean toolsEnabled, boolean ragEnabled, List<String> allowedCallers) {
}
