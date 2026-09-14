package net.dstone.ai.capability;

import java.util.List;

/**
 * Capability 설정 한 줄을 그대로 담는 상자다(dstone.ai.capability.definitions의 항목 하나).
 *
 * toolsEnabled/ragEnabled를 비워두면(null) "이 값은 요청이 알아서 정해라"는 뜻이다.
 * 실제로 어떻게 쓰이는지는 ChatService.getSpec()을 보면 된다 - 요청에 promptName/toolsEnabled/
 * ragEnabled를 직접 넣으면 그게 항상 이 값보다 우선한다.
 *
 * allowedCallers를 비워두면(null 또는 빈 리스트) "누구나 이 capability를 쓸 수 있다"는 뜻이고,
 * 채워두면 그 목록에 있는 caller(=tenant_id, ApiKeyAuthFilter가 API Key로 식별한 값)만 이 capability를
 * 요청할 수 있다 - 여러 앱이 공유하는 엔진에서 한 앱 전용 capability를 다른 앱이 잘못 호출하지 못하게
 * 막는 용도다. CapabilityRegistry.resolve()가 이 값을 검사한다.
 *
 * processName을 채우면(Phase 6, dstone.ai.process.definitions에 등록된 이름) ChatService가 단일 호출
 * 대신 net.dstone.ai.process.ProcessExecutor로 라우팅한다 - 이때 promptName/toolsEnabled/ragEnabled는
 * 쓰이지 않고, 실제 단계 구성은 그 ProcessDefinition의 steps가 결정한다. 비워두면(기본값) 지금까지처럼
 * 단일 호출로 동작한다.
 */
public record CapabilityDefinition(String name, String promptName, Boolean toolsEnabled, Boolean ragEnabled,
		List<String> allowedCallers, String processName) {
}
