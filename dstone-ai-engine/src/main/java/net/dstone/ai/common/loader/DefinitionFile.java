package net.dstone.ai.common.loader;

/**
 * <pre>
 * 읽어 들인 정의 하나와 그 정의가 들어 있던 파일의 경로입니다.
 * 경로는 정의 폴더 기준입니다. 예: workflows/sample/sample-chain.yml
 * </pre>
 *
 * @param path       정의 폴더 기준 경로
 * @param definition 파일에서 읽은 정의(WorkFlowDefinition, AgentDefinition)
 */
public record DefinitionFile<T>(String path, T definition) {
}
