/**
 * Tool 등록 체계(Phase 3). {@link net.dstone.ai.common.annotation.AiTool}을 붙인 빈 안에
 * {@code @org.springframework.ai.tool.annotation.Tool} 메소드를 만들면 {@link net.dstone.ai.config.ConfigTool}이
 * 기동 시점에 자동으로 찾아 등록한다(ConfigTool은 dstone-boot/batch/batchadmin과 동일한 컨벤션에 따라
 * 다른 Config* 클래스들과 함께 {@code net.dstone.ai.config.Config}에서 @Import된다) - gateway/prompt
 * 패키지와 동일하게 "설정/컨벤션만 따르면 코드 추가 없이 동작"하는 철학을 따른다.
 * 실제 샘플은 {@link net.dstone.ai.agent.tool.sample} 참고.
 */
package net.dstone.ai.agent.tool;
