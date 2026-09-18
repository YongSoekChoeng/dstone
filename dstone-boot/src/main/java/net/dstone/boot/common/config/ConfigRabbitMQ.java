package net.dstone.boot.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
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

	/**
	 * <pre>
	 * application.yml의 spring.rabbitmq.bindings 리스트(개수 제한 없음)를 그대로 바인딩합니다.
	 * bindings 항목을 늘리거나 줄여도 이 클래스는 손댈 필요 없이 yml만 수정하면 됩니다.
	 * </pre>
	 */
	@Bean
	@ConfigurationProperties(prefix = "spring.rabbitmq")
	public RabbitMqBindingHolder rabbitMqBindingHolder() {
		return new RabbitMqBindingHolder();
	}

	/**
	 * <pre>
	 * bindings 리스트 항목마다 Exchange/Queue/Binding을 생성해 RabbitAdmin이 기동 시 한번에 선언(declare)하도록 묶어 반환합니다.
	 * (RabbitAdmin은 Declarable(단일) 또는 Declarables(묶음) 타입의 빈을 자동으로 찾아 선언합니다.)
	 * </pre>
	 */
	@Bean
	public Declarables rabbitDeclarables(RabbitMqBindingHolder rabbitMqBindingHolder) {
		List<Declarable> declarables = new ArrayList<Declarable>();

		for (RabbitMqBindingProperties binding : rabbitMqBindingHolder.getBindings()) {

			Queue queue = new Queue(binding.getQueueId(), binding.isQueueDurable());

			AbstractExchange exchange;
			switch (binding.getExchangeType()) {
				case "fanout":
					exchange = new FanoutExchange(binding.getExchangeId(), binding.isExchangeDurable(), false);
					break;
				case "direct":
					exchange = new DirectExchange(binding.getExchangeId(), binding.isExchangeDurable(), false);
					break;
				case "topic":
					exchange = new TopicExchange(binding.getExchangeId(), binding.isExchangeDurable(), false);
					break;
				default:
					throw new IllegalArgumentException("지원하지 않는 exchange-type[" + binding.getExchangeType() + "] (fanout/direct/topic 중 하나여야 함)");
			}

			Binding queueBinding;
			if (exchange instanceof FanoutExchange) {
				// FanoutExchange는 라우팅키를 무시하고 바인딩된 모든 큐에 전달하므로 routing-key가 필요없음.
				queueBinding = BindingBuilder.bind(queue).to((FanoutExchange) exchange);
			} else if (exchange instanceof DirectExchange) {
				queueBinding = BindingBuilder.bind(queue).to((DirectExchange) exchange).with(binding.getRoutingKey());
			} else {
				queueBinding = BindingBuilder.bind(queue).to((TopicExchange) exchange).with(binding.getRoutingKey());
			}

			declarables.add(queue);
			declarables.add(exchange);
			declarables.add(queueBinding);
		}

		return new Declarables(declarables);
	}

    /**
     * RabbitMQ와의 연결을 위한 ConnectionFactory을 구성합니다.
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
     * 메시지를 전송하고 수신하기 위한 JSON 타입으로 메시지를 변경합니다.
     * Jackson2JsonMessageConverter를 사용하여 메시지 변환을 수행합니다. JSON 형식으로 메시지를 전송하고 수신할 수 있습니다
     *
     * @return
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 구성한 ConnectionFactory, MessageConverter를 통해 템플릿을 구성합니다.
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

	/** spring.rabbitmq.bindings 리스트를 담는 홀더. */
	public static class RabbitMqBindingHolder {

		private List<RabbitMqBindingProperties> bindings = new ArrayList<RabbitMqBindingProperties>();

		public List<RabbitMqBindingProperties> getBindings() {
			return bindings;
		}

		public void setBindings(List<RabbitMqBindingProperties> bindings) {
			this.bindings = bindings;
		}
	}

	/** bindings 리스트 한 항목(= Exchange 1개 + Queue 1개 + 그 사이 Binding 1개)에 대응하는 설정. */
	public static class RabbitMqBindingProperties {

		/** fanout / direct / topic 중 하나. */
		private String exchangeType;
		private String exchangeId;
		private boolean exchangeDurable;
		private String queueId;
		private boolean queueDurable;
		/** direct/topic에서 사용. fanout은 무시됨. */
		private String routingKey;

		public String getExchangeType() {
			return exchangeType;
		}

		public void setExchangeType(String exchangeType) {
			this.exchangeType = exchangeType;
		}

		public String getExchangeId() {
			return exchangeId;
		}

		public void setExchangeId(String exchangeId) {
			this.exchangeId = exchangeId;
		}

		public boolean isExchangeDurable() {
			return exchangeDurable;
		}

		public void setExchangeDurable(boolean exchangeDurable) {
			this.exchangeDurable = exchangeDurable;
		}

		public String getQueueId() {
			return queueId;
		}

		public void setQueueId(String queueId) {
			this.queueId = queueId;
		}

		public boolean isQueueDurable() {
			return queueDurable;
		}

		public void setQueueDurable(boolean queueDurable) {
			this.queueDurable = queueDurable;
		}

		public String getRoutingKey() {
			return routingKey;
		}

		public void setRoutingKey(String routingKey) {
			this.routingKey = routingKey;
		}
	}

}
