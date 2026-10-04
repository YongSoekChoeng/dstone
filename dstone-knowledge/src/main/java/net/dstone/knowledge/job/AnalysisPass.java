package net.dstone.knowledge.job;

/**
 * 분석의 한 단계(패스)입니다. 예: SCAN, DECLARE, RESOLVE.
 *
 * 이 인터페이스를 구현한 빈을 만들면 AnalysisJobRunner가 알아서 찾아 order() 순서대로 실행합니다.
 *
 * 구현할 때 지켜야 할 것:
 * - 프로젝트 전체를 메모리에 올리지 않는다. 조금씩 처리하고 그때그때 DB에 쓴다.
 * - 중간에 죽은 뒤 다시 실행해도 결과가 같아야 한다(같은 행이 두 번 들어가지 않게).
 * - 오래 걸리는 반복문 안에서는 틈틈이 context.checkCancelled()를 불러 취소 요청에 응한다.
 */
public interface AnalysisPass {

	/** 단계 이름. analysis_job.current_pass, analysis_revision_pass.pass에 그대로 들어갑니다. */
	String name();

	/** 실행 순서. 작은 값이 먼저 실행됩니다. */
	int order();

	/** 이 단계의 일을 합니다. 실패하면 예외를 던집니다(Job 전체가 FAILED가 됩니다). */
	void run(AnalysisJobContext context) throws Exception;

}
