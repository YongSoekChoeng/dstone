package net.dstone.sample.rabbitmq;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;

@Service
@Configuration
public class PublishService extends BaseObject {
	
	@Value("${rabbitmq.server.address}")
	private String address;

	@Value("${rabbitmq.server.port}")
	private Integer port;

	@Value("${rabbitmq.server.username}")
	private String username;

	@Value("${rabbitmq.server.password}")
	private String password;

	@Value("${rabbitmq.server.virtualhost}")
	private String virtualhost;
	
	public void sendRabbitMq(String exchang, String queueName, String routingKey, String msg) {
		// RabbitMQ 연결
		CachingConnectionFactory cf = null;
		try {
			
			cf = new CachingConnectionFactory(address, port);
			cf.setUsername(username);
			cf.setPassword(password);

			// 메시지 보내기
			RabbitTemplate template = new RabbitTemplate(cf);
			template.setExchange(exchang);
			template.setQueue(queueName);
			template.convertAndSend(routingKey, msg);
		} catch (Exception e) {
			this.getLogger().error(e);
		} finally {
			if(cf != null) {
				cf.destroy();
			}
		}
	}

}
