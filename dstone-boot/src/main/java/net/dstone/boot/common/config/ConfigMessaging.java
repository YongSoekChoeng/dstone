package net.dstone.boot.common.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import net.dstone.boot.common.messaging.outbox.OutboxDao;
import net.dstone.boot.common.messaging.saga.SagaDao;
import net.dstone.common.core.BaseObject;
import net.dstone.common.messaging.outbox.OutboxAppender;
import net.dstone.common.messaging.outbox.OutboxAppenderImpl;
import net.dstone.common.messaging.outbox.OutboxRelay;
import net.dstone.common.messaging.saga.SagaOrchestrator;
import net.dstone.common.messaging.saga.SagaStepHandler;

/**
 * dstone-common의 사가+아웃박스 엔진(OutboxAppenderImpl/OutboxRelay/SagaOrchestrator)을
 * dstone-boot의 실제 DataSource(OutboxDao/SagaDao, sqlSessionCommon 기반)와 KafkaTemplate으로 연결한다.
 * net.dstone.boot 하위이므로 DstoneBootApplication의 @ComponentScan(basePackages="net.dstone.boot")에 자동으로 포함되어 별도 @Import가 필요 없다.
 */
@Component
public class ConfigMessaging extends BaseObject {

	@Bean
	public OutboxAppender outboxAppender(OutboxDao outboxDao) {
		return new OutboxAppenderImpl(outboxDao);
	}

	/**
	 * spring.kafka.enabled=false이면 KafkaTemplate 빈 자체가 없으므로(ConfigKafka 참고) 이 빈도 함께 비활성화한다.
	 */
	@Bean
	@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
	public OutboxRelay outboxRelay(OutboxDao outboxDao, KafkaTemplate<String, Object> kafkaTemplate) {
		return new OutboxRelay(outboxDao, kafkaTemplate);
	}

	/**
	 * stepHandlers: 각 모듈이 @Component로 등록한 SagaStepHandler 구현체들을 Spring이 모아서 주입.
	 * 하나도 없으면 빈 리스트가 주입된다(에러 아님).
	 */
	@Bean
	public SagaOrchestrator sagaOrchestrator(SagaDao sagaDao, OutboxAppender outboxAppender,
			List<SagaStepHandler> stepHandlers) {
		return new SagaOrchestrator(sagaDao, outboxAppender, stepHandlers);
	}

}
