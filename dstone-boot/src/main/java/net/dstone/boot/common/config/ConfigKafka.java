package net.dstone.boot.common.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.KafkaUtil;
import net.dstone.common.utils.StringUtil;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class ConfigKafka extends BaseObject {

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

    @Bean
    public KafkaTemplate<String,Object> kafkaTemplate() {
    	
    	Map<String,Object> initValMap = new HashMap<String,Object>();
    	
		if( !initValMap.containsKey(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG) || StringUtil.isEmpty(initValMap.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))  ) {
			initValMap.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configProperty.getProperty("spring.kafka.bootstrap-servers"));
		}
		if( !initValMap.containsKey(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG) || StringUtil.isEmpty(initValMap.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))  ) {
			initValMap.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		}
		if( !initValMap.containsKey(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG) || StringUtil.isEmpty(initValMap.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))  ) {
			initValMap.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		}

        return KafkaUtil.getInstance(initValMap).getKafkaTemplate();
    }
    
}
