package net.dstone.common.websocket.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.common.core.BaseObject;

@Configuration
@ConditionalOnProperty(name = "spring.websocket.enabled", havingValue = "true")
@RestController
public class WebSocketController extends BaseObject {

    @Autowired
	private SimpMessagingTemplate simpMessagingTemplate;       // 특정 사용자에게 메시지를 보내는데 사용되는 STOMP을 이용한 템플릿입니다.

    @MessageMapping("/message")
    public void sendMessage(@RequestBody String payload) {
    	simpMessagingTemplate.convertAndSend("/sub/message", payload);       // 구독중인 모든 사용자에게 메시지를 전달합니다.
        return ;
    }
}
