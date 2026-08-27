package net.dstone.common.utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

public class KafkaUtil {

	private static KafkaUtil kafkaUtil = null;

	private Map<String, Object> initValMap = new HashMap<String, Object>();

	public static KafkaUtil getInstance() {
		if (kafkaUtil == null) {
			kafkaUtil = new KafkaUtil();
		}
		kafkaUtil.init();
		return kafkaUtil;
	}

	public static KafkaUtil getInstance(Map<String, Object> initValMap) {
		if (kafkaUtil == null) {
			kafkaUtil = new KafkaUtil();
		}
		kafkaUtil.init(initValMap);
		return kafkaUtil;
	}

	private void init() {	
		// 만일 기본 값으로 세팅하고자 한다면
		if( this.initValMap.isEmpty() ) {
	        // 1. 카프카 프로듀서 설정 정보 (Pure Java Map)
	        this.initValMap.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
	        this.initValMap.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
	        this.initValMap.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
	        // 옵션 세팅 (선택)
	        this.initValMap.put(ProducerConfig.ACKS_CONFIG, "all");
		}
	}

	private void init(Map<String, Object> initValMap) {
		try {
			this.initValMap = initValMap;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public ProducerFactory<String, Object> producerFactory() {
		return new DefaultKafkaProducerFactory<>(this.initValMap);
	}

	public ProducerFactory<String, Object> producerFactory(Map<String, Object> config) {
		return new DefaultKafkaProducerFactory<>(config);
	}

	public KafkaTemplate<String, Object> getKafkaTemplate() {
		KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<String, Object>(new DefaultKafkaProducerFactory<>(this.initValMap));
		return kafkaTemplate;
	}

}
