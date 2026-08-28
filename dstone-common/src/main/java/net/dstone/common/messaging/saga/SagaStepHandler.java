package net.dstone.common.messaging.saga;

import java.util.Map;

/**
 * 사가의 한 스텝을 실제로 처리하는 핸들러 계약.
 * 각 모듈이 자기 도메인 로직으로 구현하여 @Component로 등록하면,
 * SagaOrchestrator가 Spring이 모아준 List&lt;SagaStepHandler&gt;에서 getStepName()으로 찾아서 실행한다.
 */
public interface SagaStepHandler {

	/** 이 핸들러가 담당하는 스텝 이름(사가 정의에서 참조하는 식별자) */
	String getStepName();

	/** 정상 처리. 다음 스텝으로 전달할 결과를 반환한다. */
	Map<String, Object> handle(Map<String, Object> command) throws Exception;

	/** 보상 처리(실패 시 앞선 성공 스텝을 되돌림). 예외를 던지지 않는 것을 원칙으로 한다. */
	void compensate(Map<String, Object> command);

}
