package net.dstone.ai.common.definition;

import java.util.List;

/**
 * "Agent" 하나를 표현하는 클래스입니다. Agent란 쉽게 말해 "LLM에게 어떤 역할과 규칙으로 일을 시킬지"를
 * 정의해 둔 설정 묶음입니다. resources/agents/*.yml 파일 하나하나가 Agent 하나에 해당하며,
 * net.dstone.ai.common.loader.YamlDefinitionLoader 가 그 YAML 파일을 읽어서 이 클래스의 값으로 채워줍니다.
 *
 * prompt는 이 Agent가 항상 지키는 시스템 프롬프트(LLM에게 미리 주는 지시문)이고, toolsEnabled/ragEnabled는
 * 이 Agent가 Tool 호출이나 RAG(문서 검색) 기능을 쓸 수 있는지를 켜고 끄는 값입니다.
 *
 * 이 Agent는 두 가지 경로로 호출될 수 있습니다: 사용자가 api.controller.ChatController를 통해 채팅 요청의
 * agent 값으로 직접 지정하거나, Workflow 안의 AGENT/SUPERVISOR 타입 step이 ref 값으로 가리키는 경우입니다.
 * 어느 경로로 호출되든, "이 caller(호출 주체)가 이 Agent를 써도 되는지"는 항상 allowedCallers 값으로 똑같이
 * 검사합니다(자세한 검사 로직은 common.registry.AgentRegistry.resolve() 참고).
 *
 * @param name           이 Agent를 식별하는 이름
 * @param prompt         시스템 프롬프트 원문입니다. 이 안에 {caller}나 {today}처럼 중괄호로 감싼 변수 이름을
 *                       넣어두면, runtime.agent.AgentExecutor가 실제 호출 시점에 그 값을 채워서(Spring AI의
 *                       PromptTemplate 기능을 사용) 프롬프트를 완성합니다
 * @param description    이 Agent가 무엇을 하는지 사람이 읽기 위한 설명입니다. 코드 동작에는 아무 영향을 주지 않습니다
 * @param model          이 Agent를 호출할 때만 특별히 쓸 모델 이름입니다(자세한 적용 방식은
 *                       runtime.agent.AgentExecutor 참고). 비워두면(null) 엔진 전체의 기본 모델
 *                       (spring.ai.{provider}.chat.options.model 설정값)을 그대로 사용합니다
 * @param toolsEnabled           이 Agent가 등록된 Tool을 스스로 호출할 수 있는지 여부입니다
 * @param ragEnabled             이 Agent가 RAG(적재된 문서를 검색해서 답변에 참고하는 기능)를 쓸 수 있는지 여부입니다
 * @param ragTopK                RAG 검색 시 가져올 결과의 최대 개수입니다. 비워두면(null) 엔진 전체 기본값
 *                               (설정 파일의 dstone.ai.rag.retrieval.top-k, common.rag.RagRetrievalChain 참고)을 그대로 씁니다
 * @param ragSimilarityThreshold RAG 검색에서 "이 정도는 관련 있다고 볼 최소 유사도" 기준값입니다. 비워두면(null)
 *                               엔진 전체 기본값(dstone.ai.rag.retrieval.similarity-threshold)을 그대로 씁니다
 * @param ragAllowEmptyContext   RAG 검색 결과가 하나도 없을 때의 동작을 정합니다. true(기본값)면 검색 결과가
 *                               없어도 질문에 그냥 답을 시도하고, false면 Spring AI의 기본 동작대로 "모른다"고
 *                               답하도록 강제합니다. 비워두면(null) true로 동작합니다
 * @param allowedCallers         이 Agent를 호출할 수 있도록 허락된 caller(호출 주체, tenant) 목록입니다
 */
public record AgentDefinition(String name, String prompt, String description, String model, boolean toolsEnabled, boolean ragEnabled, Integer ragTopK, Double ragSimilarityThreshold,
	Boolean ragAllowEmptyContext, List<String> allowedCallers) {}
