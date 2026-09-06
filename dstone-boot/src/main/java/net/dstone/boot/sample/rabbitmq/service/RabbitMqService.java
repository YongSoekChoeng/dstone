package net.dstone.boot.sample.rabbitmq.service;

import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.boot.common.biz.BaseService;

@Service
public class RabbitMqService extends BaseService {

	@Autowired(required = false)
	private RabbitTemplate rabbitTemplate;

	public void publishOrder(Map<String,Object> param) {
		if (rabbitTemplate == null) {
			this.warn(".publishOrder(" + param + ") - spring.rabbitmq.enabled=false, 발행하지 않음");
			return;
		}
		// exchange=app.direct.exchange, routingKey=orders.process → app.orders.queue로 전달(direct)
		rabbitTemplate.convertAndSend("app.direct.exchange", "orders.process", param);
	}

	public void publishNotification(Map<String,Object> param) {
		if (rabbitTemplate == null) {
			this.warn(".publishNotification(" + param + ") - spring.rabbitmq.enabled=false, 발행하지 않음");
			return;
		}
		// exchange=app.fanout.exchange → 라우팅키 무시하고 바인딩된 모든 큐(app.notifications.queue)로 전달(fanout)
		rabbitTemplate.convertAndSend("app.fanout.exchange", "", param);
	}

	@RabbitListener(queues = "app.orders.queue")
	public void consumeOrders(Map<String,Object> param) {
		this.info("queue[app.orders.queue] 수신완료!!! param=" + param);
	}

	@RabbitListener(queues = "app.notifications.queue")
	public void consumeNotifications(Map<String,Object> param) {
		this.info("queue[app.notifications.queue] 수신완료!!! param=" + param);
	}
}
