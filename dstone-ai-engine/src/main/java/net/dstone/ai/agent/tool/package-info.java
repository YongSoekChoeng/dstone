/**
 * Tool 등록 체계(Phase 3). {@link net.dstone.ai.agent.tool.AiTool}을 붙인 빈 안에
 * {@code @org.springframework.ai.tool.annotation.Tool} 메소드를 만들면 {@link net.dstone.ai.agent.tool.ToolRegistry}가
 * 기동 시점에 자동으로 찾아 등록한다 - gateway/prompt 패키지와 동일하게 "설정/컨벤션만 따르면 코드 추가 없이
 * 동작"하는 철학을 따른다. 실제 샘플은 {@link net.dstone.ai.agent.tool.sample} 참고.
 */
package net.dstone.ai.agent.tool;
