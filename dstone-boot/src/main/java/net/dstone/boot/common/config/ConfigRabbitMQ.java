package net.dstone.boot.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.enabled", havingValue = "true")
public class ConfigRabbitMQ extends BaseObject {

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	/***************************** Rabbit MQ 설정 시작 *****************************/
	
	/****************************************************************************
	1. Exchange(교환기)
		* 특정설정값에 기반해서 큐에 전달.
		* 내구성 (Durability): durable로 설정하면 RabbitMQ 서버가 재시작되어도 Exchange가 유지됩니다. transient는 서버 재시작 시 사라집니다.		
		1-1. Fanout Exchange(브로드캐스트 교환기)	
			라우팅 키는 무시하고 메시지를 모든 바운드된 큐에 전달.
		1-2. Direct Exchange(직접 교환기)
			라우팅 키가 정확하게 일치하는 큐에 전달.
		1-3. Topic Exchange(패턴기반 교환기)
			라우팅 키가 특정패턴에 일치하는 큐에 전달.(패턴은 *, # 와일드카드 사용).
		1-4. Headers Exchange(헤더기반 교환기)
			헤더값이 특정패턴에 일치하는 큐에 전달.
	2. Queue (큐)
		* Queue는 메시지를 최종적으로 저장하고 소비자가 메시지를 가져갈 때까지 대기시키는 곳. 
		* First-In-First-Out (FIFO) 방식으로 메시지를 처리.
		* Queue영구저장여부. RabbitMq가 재 실행되더라도 내용을 유지할지 여부. 실제 큐의 Durability와 동일해야 함. 운영모드에서는 true로 하는게 좋음.
	****************************************************************************/
	
	/*** 바인딩 갯수만큼 세팅 시작 ***/
	// binding-notifications
    /** 1. Exchange 구성합니다. */
    @Bean
    public FanoutExchange exchangeNotifications() {
        return new FanoutExchange(configProperty.getProperty("spring.rabbitmq.bindings.binding-notifications.exchange-id"));
    }
    /** 2. 큐를 구성합니다. */
    @Bean
    public Queue queueNotifications() {
        return new Queue(configProperty.getProperty("spring.rabbitmq.bindings.binding-notifications.queue-id"), Boolean.valueOf(configProperty.getProperty("spring.rabbitmq.bindings.binding-notifications.queue-durable")));
    }
    /** 3. 큐와 Exchange를 바인딩합니다. */
    @Bean
    public Binding bindingNotifications() {
    	// FanoutExchange 방식은 모든 큐에 메세지를 전달하므로 큐ID를 매개변수로 받을 필요가 없음.
        return BindingBuilder
        	.bind(queueNotifications())		// 이 큐(queue)를
        	.to(exchangeNotifications());	// 이 교환기(exchange)방식으로 바인딩. 큐ID가 필요없는 이유는 이 교환기(exchange)방식이 Fanout 이므로 특정큐가 아닌 모든 큐에 메세지를 전달하기 때문임.
    }
    // binding-orders
    /** 1. Exchange 구성합니다. */
    @Bean
    public DirectExchange exchangeOrders() {
        return new DirectExchange(configProperty.getProperty("spring.rabbitmq.bindings.binding-orders.exchange-id"));
    }
    /** 2. 큐를 구성합니다. */
    @Bean
    public Queue queueOrders() {
        return new Queue(configProperty.getProperty("spring.rabbitmq.bindings.binding-orders.queue-id"), Boolean.valueOf(configProperty.getProperty("spring.rabbitmq.bindings.binding-orders.queue-durable")));
    }
    /** 3. 큐와 Exchange를 바인딩합니다. */
    @Bean
    public Binding bindingOrders() {
    	String queueId = configProperty.getProperty("spring.rabbitmq.bindings.binding-orders.queue-id");
        return BindingBuilder
        	.bind(queueOrders())	// 이 큐(queue)를
        	.to(exchangeOrders())	// 이 교환기(exchange)방식으로 
        	.with(queueId);			// 이 매개변수로 비교하여 바인딩.
    }

    /*** 바인딩 갯수만큼 세팅 끝 ***/


    /**
     * 4. RabbitMQ와의 연결을 위한 ConnectionFactory을 구성합니다.
     * Application.properties의 RabbitMQ의 사용자 정보를 가져와서 RabbitMQ와의 연결에 필요한 ConnectionFactory를 구성합니다.
     *
     * @return ConnectionFactory
     */
    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(configProperty.getProperty("spring.rabbitmq.host"));
        connectionFactory.setPort(Integer.parseInt(configProperty.getProperty("spring.rabbitmq.port")));   
        connectionFactory.setUsername(configProperty.getProperty("spring.rabbitmq.username"));
        connectionFactory.setPassword(configProperty.getProperty("spring.rabbitmq.password"));
        connectionFactory.setVirtualHost(configProperty.getProperty("spring.rabbitmq.virtual-host"));
        return connectionFactory;
    }

    /**
     * 5. 메시지를 전송하고 수신하기 위한 JSON 타입으로 메시지를 변경합니다.
     * Jackson2JsonMessageConverter를 사용하여 메시지 변환을 수행합니다. JSON 형식으로 메시지를 전송하고 수신할 수 있습니다
     *
     * @return
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 6. 구성한 ConnectionFactory, MessageConverter를 통해 템플릿을 구성합니다.
     *
     * @return
     */
    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(this.connectionFactory());
        rabbitTemplate.setMessageConverter(this.messageConverter());
        return rabbitTemplate;
    }
	/***************************** Rabbit MQ 설정 끝 *****************************/

}
