package net.dstone.common.utils;

import java.util.HashMap;
import java.util.Map;

import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

public class KafkaUtil {

	private static KafkaUtil kafkaUtil = null;

	private Map<String, Object> initValMap = new HashMap<String, Object>();

	public static KafkaUtil getInstance(Map<String, Object> initValMap) {
		if (kafkaUtil == null) {
			kafkaUtil = new KafkaUtil();
		}
		kafkaUtil.init(initValMap);
		return kafkaUtil;
	}

	private void init() {
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
