package net.dstone.knowledge.semantic;

import net.dstone.knowledge.job.AnalysisJobContext;

/**
 * <pre>
 * 의미 분석 플러그인입니다. 코드의 구조(타입, 메소드, 호출)에 "이것이 무엇인가"라는 뜻을 붙입니다.
 * 예: 이 메소드는 HTTP 요청을 받는 진입점이다, 이 클래스는 Service 계층이다.
 *
 * 스캔, 선언, 호출 해석까지는 어떤 프레임워크를 쓰는지 모르는 채로 순수 Java만으로 끝납니다.
 * 프레임워크마다 다른 규칙은 여기에 플러그인으로 나눠 둡니다(Spring, 서블릿, 일반 Java ...).
 * 그래서 맞는 플러그인이 하나도 없는 프로젝트도 분석은 정상으로 끝나고, 그래프와 호출 관계는 그대로 쓸 수 있습니다.
 *
 * 이 인터페이스를 구현한 빈을 만들면 SemanticPass가 알아서 찾아 order() 순서대로 실행합니다.
 *
 * 구현할 때 지켜야 할 것:
 * - 해당 사항이 없는 프로젝트에서는 아무것도 하지 않고 끝나야 한다(오류를 내지 않는다).
 * - SemanticPass가 시작할 때 전에 만든 결과를 모두 지워 주므로, 플러그인은 넣기만 하면 된다.
 * - 한 트랜잭션 안에서 불린다. 일반 세션(sqlSessionCommon)만 쓴다.
 * </pre>
 */
public interface SemanticPlugin {

	/** 플러그인 이름. 로그에 찍힙니다. */
	String name();

	/** 실행 순서. 작은 값이 먼저 실행됩니다. 계층 분류처럼 "앞에서 못 정한 것만 채우는" 플러그인은 뒤에 둡니다. */
	int order();

	/**
	 * @return 무엇을 얼마나 만들었는지 한 줄 요약(로그용)
	 */
	String run(AnalysisJobContext context) throws Exception;

}
