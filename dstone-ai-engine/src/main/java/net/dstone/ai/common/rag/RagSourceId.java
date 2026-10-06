package net.dstone.ai.common.rag;

import net.dstone.common.utils.StringUtil;

/**
 * <pre>
 * 문서 이름(sourceId)에 호출자(caller = tenant)를 붙이고 떼는 규칙입니다.
 *
 * 문서는 dstone-knowledge에 저장됩니다. 그런데 dstone-knowledge가 보는 호출자는 이 엔진 하나뿐이라,
 * 이 엔진을 부른 앱들(caller)의 문서를 서로 가려 주지 못합니다. 그래서 이 엔진이 문서 이름 앞에 caller를 붙여서 저장하고,
 * 조회 / 검색 / 삭제할 때 그 이름으로 "이 caller의 문서인가"를 가립니다.
 *
 *   caller = shop, sourceId = 약관.pdf   →  저장되는 이름: shop::약관.pdf
 *   caller가 없을 때(인증을 꺼 둔 환경)     →  저장되는 이름: 약관.pdf (그대로). 모든 문서가 보입니다
 * </pre>
 */
public class RagSourceId {

	private static final String SEPARATOR = "::";

	private RagSourceId() {
	}

	/** dstone-knowledge에 저장할 이름 */
	public static String stored(String caller, String sourceId) {
		return StringUtil.isEmpty(caller) ? sourceId : caller + SEPARATOR + sourceId;
	}

	/** 저장된 이름이 이 caller에게 보여도 되는 문서인지. caller가 없으면 전부 보입니다 */
	public static boolean visibleTo(String caller, String storedId) {
		if (StringUtil.isEmpty(caller)) {
			return true;
		}
		return storedId != null && storedId.startsWith(caller + SEPARATOR);
	}

	/** caller에게 보여 줄 이름. 자기 문서는 앞에 붙인 caller를 떼고, caller가 없으면 저장된 이름 그대로입니다(그 이름으로 지울 수 있어야 하므로) */
	public static String shown(String caller, String storedId) {
		if (StringUtil.isEmpty(caller) || storedId == null) {
			return storedId;
		}
		return storedId.startsWith(caller + SEPARATOR) ? storedId.substring(caller.length() + SEPARATOR.length()) : storedId;
	}

	/** 저장된 이름에 붙어 있는 caller. 붙어 있지 않으면 null */
	public static String tenantOf(String storedId) {
		int at = storedId == null ? -1 : storedId.indexOf(SEPARATOR);
		return at > 0 ? storedId.substring(0, at) : null;
	}

}
