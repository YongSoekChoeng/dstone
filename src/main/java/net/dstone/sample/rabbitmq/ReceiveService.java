package net.dstone.sample.rabbitmq;

import javax.annotation.PostConstruct;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import net.dstone.common.biz.BaseService;

@Service
@Configuration
public class ReceiveService extends BaseService{

	@Value("${spring.rabbitmq.host}")
	private String host;

	@Value("${spring.rabbitmq.port}")
	private Integer port;

	@Value("${spring.rabbitmq.username}")
	private String username;

	@Value("${spring.rabbitmq.password}")
	private String password;

	@PostConstruct
	private void init() {
		// RabbitMQ 연결
		CachingConnectionFactory cf = new CachingConnectionFactory(host, port);
		cf.setUsername(username);
		cf.setPassword(password);

		// 큐 생성
		RabbitAdmin admin = new RabbitAdmin(cf);
		Queue queue = new Queue("TestQ");
		admin.declareQueue(queue);

		// Exchange에 바인딩
		DirectExchange exchange = new DirectExchange("amq.direct");
		admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("foo.bar"));

		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(cf);
		Object listener = new Object() {
			// 메시지 처리
			public void handleMessage(Object foo) {
				getLogger().sysout("ReceiveService.handleMessage()==========================>>> foo["+foo+"]");
			}
		};
		// 메시지 리스닝
		MessageListenerAdapter adapter = new MessageListenerAdapter(listener);
		container.setMessageListener(adapter);
		container.setQueueNames("TestQ");
		container.start();
	}
}
