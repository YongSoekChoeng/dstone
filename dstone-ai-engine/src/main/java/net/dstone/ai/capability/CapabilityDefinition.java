package net.dstone.ai.capability;

/**
 * Capability 설정 한 줄을 그대로 담는 상자다(dstone.ai.capability.definitions의 항목 하나).
 *
 * toolsEnabled/ragEnabled를 비워두면(null) "이 값은 요청이 알아서 정해라"는 뜻이다.
 * 실제로 어떻게 쓰이는지는 ChatService.getSpec()을 보면 된다 - 요청에 promptName/toolsEnabled/
 * ragEnabled를 직접 넣으면 그게 항상 이 값보다 우선한다.
 */
public record CapabilityDefinition(String name, String promptName, Boolean toolsEnabled, Boolean ragEnabled) {
}
