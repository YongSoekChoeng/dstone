package net.dstone.boot.common.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.KafkaUtil;
import net.dstone.common.utils.StringUtil;

@Configuration
public class ConfigKafka extends BaseObject {

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	// @KafkaListener 는 containerFactory 를 명시하지 않으면 이 빈("kafkaListenerContainerFactory")을 사용함.
	// spring.kafka.enabled=false 이면 컨테이너는 등록만 되고 기동(브로커 연결)은 하지 않음.
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object,Object> kafkaListenerContainerFactory(
    		ConsumerFactory<Object,Object> consumerFactory) {

    	ConcurrentKafkaListenerContainerFactory<Object,Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
    	factory.setConsumerFactory(consumerFactory);
    	factory.setAutoStartup(Boolean.parseBoolean(configProperty.getProperty("spring.kafka.enabled")));
    	return factory;
    }

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
    public KafkaTemplate<String,Object> kafkaTemplate() {
    	
    	Map<String,Object> initValMap = new HashMap<String,Object>();
    	
		if( !initValMap.containsKey(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG) || StringUtil.isEmpty(initValMap.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))  ) {
			initValMap.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configProperty.getProperty("spring.kafka.bootstrap-servers"));
		}
		if( !initValMap.containsKey(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG) || StringUtil.isEmpty(initValMap.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))  ) {
			initValMap.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		}
		if( !initValMap.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG) || StringUtil.isEmpty(initValMap.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))  ) {
			initValMap.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
		}

        return KafkaUtil.getInstance(initValMap).getKafkaTemplate();
    }
    
}
