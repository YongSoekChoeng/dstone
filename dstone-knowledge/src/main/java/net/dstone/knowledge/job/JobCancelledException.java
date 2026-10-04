package net.dstone.knowledge.job;

/**
 * <pre>
 * 사용자가 분석을 취소했을 때, 돌고 있던 단계를 빠져나오기 위해 던지는 예외입니다.
 * 오류가 아니라 "여기서 멈춘다"는 신호입니다.
 * </pre>
 */
public class JobCancelledException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public JobCancelledException() {
		super("사용자가 분석을 취소했습니다.");
	}

}
