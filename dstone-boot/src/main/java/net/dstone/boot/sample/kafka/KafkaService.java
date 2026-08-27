package net.dstone.boot.sample.kafka;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import net.dstone.boot.common.biz.BaseService;

@Service
public class KafkaService  extends BaseService { 

	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;
	
	public void publish(String topic, String key, Map<String,Object> param) {
		// key = aggregateId → 같은 주문의 이벤트는 항상 같은 파티션으로 (순서 보장)
        kafkaTemplate.send(topic, key, param);
	}
	
    @KafkaListener(topics = "order-events", groupId = "inventory-service-group")
    public void consume(Map<String,Object> param) {
        // 처리 로직
    	this.info("param===>>" + param);
    }
}
