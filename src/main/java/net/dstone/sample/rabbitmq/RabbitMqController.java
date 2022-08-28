package net.dstone.sample.rabbitmq;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import net.dstone.common.utils.DataSet;

@Controller
@RequestMapping(value = "/rabbitmq/*")
public class RabbitMqController extends net.dstone.common.biz.BaseController { 

    /********* SVC 정의부분 시작 *********/
    @Autowired 
    private net.dstone.sample.rabbitmq.PublishService publishService; 
    /********* SVC 정의부분 끝 *********/

    /** 
     * 샘플그룹정보 리스트조회 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/sendMsg.do") 
    public void sendMsg(@RequestBody Map<String,String> input) {
    	
    	String jsonMsg = net.dstone.common.utils.BeanUtil.toJson(input);
    	
    	String exchang = "amq.direct";
    	String queueName = "TestQ";
    	String routingKey = "foo.bar";
    	
    	publishService.sendRabbitMq(exchang, queueName, routingKey, jsonMsg);
    }
   
}
