package net.dstone.ai.api.dto;

import java.util.List;

/**
 * <pre>
 * 실행을 지난 step으로 되돌려 다시 돌리면 무엇이 어떻게 되는지 미리 알려 주는 값입니다.
 * 화면은 [여기서 다시] 버튼을 누른 사람에게 이 값을 먼저 보여 주고, 지울 state를 고르게 합니다.
 *
 * 되돌리는 구간은 "이 step이 마지막으로 돈 자리부터 실행 기록의 끝까지"입니다.
 * 그 구간의 step이 저장한 state 이름(savedNames)은 지우지 않으면 그대로 남아,
 * 다시 돌 때 경로가 달라지면 예전 값을 읽을 수 있습니다.
 * </pre>
 *
 * @param fromStep     다시 시작할 step의 id입니다.
 * @param stepType     그 step의 종류입니다(정의에 없으면 null).
 * @param stepIndex    그 step이 지금 정의에서 몇 번째인지입니다(0부터, 없으면 -1).
 * @param rewindable   되돌릴 수 있으면 true입니다.
 * @param problem      되돌릴 수 없는 이유입니다(되돌릴 수 있으면 null).
 * @param steps        되돌리는 구간에서 돈 step id들입니다(실행 기록 순서, 중복 없음).
 * @param savedNames   그 구간의 step이 저장해서 지금 state에 남아 있는 이름들입니다. clearState로 지울 수 있습니다.
 * @param writtenFiles 그 구간의 step이 쓴 파일들입니다. 되돌려도 원래대로 돌아가지 않습니다.
 * @param warnings     되돌릴 수는 있지만 사람이 알아야 할 점입니다.
 */
public record RewindPoint(String fromStep, String stepType, int stepIndex, boolean rewindable, String problem, List<String> steps, List<String> savedNames,
	List<String> writtenFiles, List<String> warnings) {

	/**
	 * 되돌릴 수 없을 때의 값을 만듭니다.
	 *
	 * @param fromStep 고른 step의 id입니다.
	 * @param problem  되돌릴 수 없는 이유입니다.
	 */
	public static RewindPoint blocked(String fromStep, String problem) {
		return new RewindPoint(fromStep, null, -1, false, problem, List.of(), List.of(), List.of(), List.of());
	}

}
