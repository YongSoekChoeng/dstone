package net.dstone.ai.api.dto;

import java.util.List;

/**
 * <pre>
 * 일시정지했거나 실패한 실행을 이어 가면 어느 step부터 도는지 알려 주는 값입니다.
 * 화면은 재개 버튼을 누른 사람에게 이 step과 warnings를 먼저 보여 줍니다.
 * </pre>
 *
 * @param stepId    다시 실행할 step의 id입니다.
 * @param stepType  그 step의 종류입니다.
 * @param stepIndex 그 step이 지금 정의에서 몇 번째인지입니다(0부터).
 * @param resumable 이어 갈 수 있으면 true입니다.
 * @param problem   이어 갈 수 없는 이유입니다(이어 갈 수 있으면 null).
 * @param warnings  이어 갈 수는 있지만 사람이 알아야 할 점입니다.
 */
public record ResumePoint(String stepId, String stepType, int stepIndex, boolean resumable, String problem, List<String> warnings) {

	/**
	 * 이어 갈 수 없을 때의 값을 만듭니다.
	 *
	 * @param problem 이어 갈 수 없는 이유입니다.
	 */
	public static ResumePoint blocked(String problem) {
		return new ResumePoint(null, null, -1, false, problem, List.of());
	}

}
