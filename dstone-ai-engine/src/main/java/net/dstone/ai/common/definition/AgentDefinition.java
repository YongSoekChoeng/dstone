package net.dstone.ai.common.definition;

import java.util.List;

/**
 * resources/agents/*.yml 파일에 등록된 Agent 하나("LLM에게 일을 시키는 단위" - prompt를 시스템 프롬프트로 쓰고, toolsEnabled/ragEnabled로
 * Tool/RAG를 붙일지를 갖고 있다). net.dstone.ai.common.loader.YamlDefinitionLoader 에 의해서 로딩된다.
 *
 * api.controller.ChatController가 request.agent()로 직접 가리킬 수도 있고, StepType.AGENT/SUPERVISOR step의 ref가 가리킬 수도 있다. 어느 경로로
 * 오든 caller 검증은 이 정의의 allowedCallers로 동일하게 이뤄진다(common.registry.AgentRegistry.resolve() 참고).
 *
 * @param name           Agent 이름
 * @param prompt         시스템 프롬프트 원문. {caller}/{today} 같은 변수 토큰을 담고 있으면 runtime.agent.AgentExecutor가 Spring AI의
 *                       PromptTemplate으로 렌더링해서 적용한다
 * @param description    Agent 설명(문서화용, 코드에서는 읽지 않음)
 * @param model          이 Agent 호출에만 쓸 모델명(runtime.agent.AgentExecutor 참고). null이면 spring.ai.{provider}.chat.options.model(엔진 공통 기본값)
 * @param toolsEnabled           Tool 사용 허용 여부
 * @param ragEnabled             RAG 사용 허용 여부
 * @param ragTopK                이 Agent의 RAG 검색 결과 최대 개수. null이면 common.rag.RagRetrievalChain의 전역 기본값(dstone.ai.rag.retrieval.top-k)을 그대로 씀
 * @param ragSimilarityThreshold 이 Agent의 RAG 검색 유사도 임계값. null이면 common.rag.RagRetrievalChain의 전역 기본값(dstone.ai.rag.retrieval.similarity-threshold)을 그대로 씀
 * @param ragAllowEmptyContext   검색 결과가 하나도 없을 때 질의를 그대로 진행시킬지(true, 기본값) 아니면 Spring AI 기본 동작대로 "모른다"고 답하게 강제할지(false).
 *                               null이면 true(기존 동작)
 * @param allowedCallers         이 Agent 호출이 허용된 caller(tenant) 목록
 */
public record AgentDefinition(String name, String prompt, String description, String model, boolean toolsEnabled, boolean ragEnabled, Integer ragTopK, Double ragSimilarityThreshold,
	Boolean ragAllowEmptyContext, List<String> allowedCallers) {}
