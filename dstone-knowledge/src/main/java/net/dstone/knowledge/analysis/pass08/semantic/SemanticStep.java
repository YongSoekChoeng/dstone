package net.dstone.knowledge.analysis.pass08.semantic;

import net.dstone.knowledge.analysis.job.AnalysisJobContext;

/**
 * <pre>
 * SEMANTIC 단계의 처리기 하나입니다. 코드의 구조(타입, 메소드, 호출)에 "이것이 무엇인가"라는 뜻을 붙입니다.
 * 예: 이 메소드는 HTTP 요청을 받는 진입점이다, 이 클래스는 Service 계층이다.
 *
 * 처리기는 규칙 파일의 절(layer, entry, sql ...)마다 하나씩 있습니다(LayerResolver, EntryFinder, SqlLinker ...).
 * 규칙이 필요 없는 표준 형식(web.xml, struts-config.xml, JSP, 매퍼 인터페이스)을 읽는 처리기도 있습니다.
 * 실행 순서는 SemanticPass 의 목록 한 곳에 적혀 있습니다.
 *
 * 구현할 때 지켜야 할 것:
 * - 해당 사항이 없는 프로젝트에서는 아무것도 하지 않고 끝나야 한다(오류를 내지 않는다).
 * - SemanticPass 가 시작할 때 전에 만든 결과를 모두 지워 주므로, 처리기는 넣기만 하면 된다.
 * - 한 트랜잭션 안에서 불린다. 일반 세션(sqlSessionCommon)만 쓴다.
 * - 규칙으로 만든 결과에는 그 규칙의 자리(rule.at(), 예: common/spring.yml:5)를 properties_json.ruleAt 에 남긴다.
 * </pre>
 */
public interface SemanticStep {

	/** 처리기 이름. 로그에 찍힙니다. */
	String name();

	/**
	 * @return 무엇을 얼마나 만들었는지 한 줄 요약(로그용)
	 */
	String run(AnalysisJobContext context) throws Exception;

}
