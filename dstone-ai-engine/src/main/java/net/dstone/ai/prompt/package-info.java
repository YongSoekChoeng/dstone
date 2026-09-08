/**
 * 프롬프트 템플릿 관리/버저닝 — SI 프로젝트별 커스터마이징 지점(Phase 1).
 *
 * classpath:prompts/{name}/{version}.st 리소스를 {@link net.dstone.ai.prompt.PromptTemplateRegistry}가
 * 이름+버전으로 찾아 렌더링한다. 활성 버전은 {@link net.dstone.ai.prompt.PromptProperties}
 * (dstone.ai.prompt.*)에서 템플릿명별로 override 가능 — SI 프로젝트는 .st 파일 추가/교체와
 * 설정 한 줄로 커스터마이징하면 되고 코드 변경은 필요 없다.
 */
package net.dstone.ai.prompt;
