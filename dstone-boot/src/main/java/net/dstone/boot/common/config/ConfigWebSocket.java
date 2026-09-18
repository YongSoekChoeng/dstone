package net.dstone.boot.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.websocket.handler.BaseTextWebSocketHandler;

@Configuration
@ConditionalOnProperty(name = "spring.websocket.enabled", havingValue = "true")
@EnableWebSocket
@EnableWebSocketMessageBroker
public class ConfigWebSocket extends BaseObject implements WebSocketConfigurer, WebSocketMessageBrokerConfigurer {

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean
	
	public static String WEBSOCKET_WS_END_POINT				= "/ws";      	// 웹소켓 엔드포인트
	public static String WEBSOCKET_STOMP_END_POINT			= "/ws-stomp";  // 웹소켓(stomp) 엔드포인트
	public static String WEBSOCKET_STOMP_PUB_PREFIX			= "/pub";      	// 웹소켓(stomp) 발행프리픽스
	public static String WEBSOCKET_STOMP_SUB_PREFIX			= "/sub";      	// 웹소켓(stomp) 구독프리픽스
	
	/***************************** WebSocketConfigurer 설정 시작 *****************************/
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new BaseTextWebSocketHandler(), WEBSOCKET_WS_END_POINT )
        		.addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOriginPatterns("*")
                ;
    }

	@Bean
	public ServletServerContainerFactoryBean createWebSocketContainer() {
		ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
		//Text Message의 최대 버퍼 크기 설정
		container.setMaxTextMessageBufferSize(8192);
		//Binary Message의 최대 버퍼 크기 설정
		container.setMaxBinaryMessageBufferSize(8192);
		return container;
	}
	/***************************** WebSocketConfigurer 설정 끝 *****************************/

	/***************************** WebSocketMessageBrokerConfigurer 설정 시작 *****************************/
	
	/***************************************************************************************************
	1. 전송루트 예(Example)
		▶(발행자)화면 stompClient.send("/pub/message", ...)
			▶ ConfigWebSocket.configureMessageBroker	
				▶ config.setApplicationDestinationPrefixes("/pub")	
					- 이 세팅으로 인해 /pub로 시작하는 발행자에게만 메세지를 전달받는다.
					▶ WebSocketController.sendMessage() < @MessageMapping 어노테이션값 이 "/message" 인 메서드 >
						- 내부작업(필요하다면)
						- template.convertAndSend("/sub/message", ...) 로 구독자에서 메세지 전달	
				◀ config.enableSimpleBroker("/sub")
					- 이 세팅으로 인해 /sub로 시작하는 구독자에게만 메세지가 전달된다.
			◀(구독자) 화면 stompClient.subscribe("/sub/message", ...)
	***************************************************************************************************/
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
    	this.info("registerStompEndpoints("+registry+") has been called !!!");
        /*
         * addEndpoint : 클라이언트가 WebSocket에 연결하기 위한 엔드포인트를 "/ws-stomp"로 설정합니다.
         * withSockJS : WebSocket을 지원하지 않는 브라우저에서도 SockJS를 통해 WebSocket 기능을 사용할 수 있게 합니다.
         */
    	
    	// 클라이언트가 WebSocket에 연결하기 위한 엔드포인트를 "/ws-stomp"로 설정합니다.
        registry.addEndpoint(WEBSOCKET_STOMP_END_POINT)
	        // 클라이언트의 origin을 명시적으로 지정
	        .setAllowedOriginPatterns("*")
	        // WebSocket을 지원하지 않는 브라우저에서도 SockJS를 통해 WebSocket 기능을 사용할 수 있게 합니다.
	        .withSockJS();
    }
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
    	this.info("configureMessageBroker("+config+") has been called !!!");
    	
    	config.setCacheLimit(1024 * 4);

    	/*** 클라이언트로가보낸(send) 요청 처리 ***/
        // 발행(pub) : 접두사로 시작하는 메시지는 @MessageMapping이 달린 메서드로 라우팅됩니다. 클라이언트가 서버로 메시지를 보낼 때 이 접두사를 사용합니다.
        // 예를 들어, 소켓 통신에서 사용자가 특정 메시지를 전송하기 위해 "/pub"라는 prefix 기반 메시지 전송을 위해 Publish 합니다.
        config.setApplicationDestinationPrefixes(WEBSOCKET_STOMP_PUB_PREFIX);

    	/*** 구독(sub)하는 클라이언트에게 메시지 전달 ***/
        // 구독(sub) : 접두사로 시작하는 메시지를 브로커가 처리하도록 설정합니다. 클라이언트는 이 접두사로 시작하는 주제를 구독하여 메시지를 받을 수 있습니다.
        // 예를 들어, 소켓 통신에서 사용자가 특정 메시지를 받기위해 "/sub"이라는 prefix 기반 메시지 수신을 위해 Subscribe합니다.
        config.enableSimpleBroker(WEBSOCKET_STOMP_SUB_PREFIX);

    }
	/***************************** WebSocketMessageBrokerConfigurer 설정 끝 *****************************/

}
