package net.dstone.ai.common.loader;

/**
 * <pre>
 * 정의 파일 하나의 경로와 내용(글자 그대로)입니다.
 * 경로는 정의 폴더 기준입니다. 예: workflows/sample/sample-chain.yml
 * </pre>
 *
 * @param path 정의 폴더 기준 경로
 * @param text 파일 내용
 */
public record DefinitionText(String path, String text) {
}
