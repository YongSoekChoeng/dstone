package net.dstone.common.utils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WebSocket Client Util
 * @author Default
 */
public class WsClientUtil extends net.dstone.common.core.BaseObject {

	public static String WEBSOCKET_WS_END_POINT				= "/ws";      	// 웹소켓 엔드포인트
	public static String WEBSOCKET_STOMP_END_POINT			= "/ws-stomp";  // 웹소켓(stomp) 엔드포인트
	public static String WEBSOCKET_STOMP_PUB_PREFIX			= "/pub";      	// 웹소켓(stomp) 발행프리픽스
	public static String WEBSOCKET_STOMP_SUB_PREFIX			= "/sub";      	// 웹소켓(stomp) 구독프리픽스
	
	public static void sendWebSocket(String url, String msg) {
		OkHttpClient client = new OkHttpClient();
		try {
			Request request = new Request.Builder()
		    .url(url)
		    .build();
			
	        WebSocket webSocket = client.newWebSocket(request, new WebSocketListener() {
	            @Override
	            public void onOpen(WebSocket webSocket, Response response) {
	                webSocket.send(msg);
	            }

	            @Override
	            public void onClosed(WebSocket webSocket, int code, String reason) {
	            	net.dstone.common.utils.LogUtil.sysout("연결 종료됨 → 코드: " + code + ", 이유: " + reason);
	            }

	            @Override
	            public void onMessage(WebSocket webSocket, String text) {
	            	if( !StringUtil.isEmpty(text) ) {
	            		net.dstone.common.utils.LogUtil.sysout("서버 응답: " + text);
	            	}else {
	            		net.dstone.common.utils.LogUtil.sysout("서버 응답 완료");
	            	}
	            }

	            @Override
	            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
	                t.printStackTrace();
	            }
	        });
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				client.dispatcher().executorService().shutdown();
			} catch (Exception e) {
				// TODO: handle finally clause
			}
		}
	}
	
	public static void sendStompSockJs(String url, String msg) {
		StompSession session = null;
		try {

	        CountDownLatch latch = new CountDownLatch(1);
	        
	        List<Transport> transports = new ArrayList<Transport>();
	        transports.add(new WebSocketTransport(new org.springframework.web.socket.client.standard.StandardWebSocketClient()));
	        SockJsClient sockJsClient = new SockJsClient(transports);

	        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
	        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

	        ListenableFuture<StompSession> connectFuture = stompClient.connect(url, new StompSessionHandlerAdapter() {
	            @Override
	            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
	            	net.dstone.common.utils.LogUtil.sysout("STOMP 연결 성공");
	            }

	            @Override
	            public void handleFrame(StompHeaders headers, Object payload) {
	            	if( !StringUtil.isEmpty(payload) ) {
	            		net.dstone.common.utils.LogUtil.sysout("서버 응답: " + payload);
	            	}else {
	            		net.dstone.common.utils.LogUtil.sysout("서버 응답 완료");
	            	}
	            }

	            @Override
	            public void handleTransportError(StompSession session, Throwable exception) {
	                exception.printStackTrace();
	            }
			} );
	        session = connectFuture.get();

	        session.subscribe(WEBSOCKET_STOMP_SUB_PREFIX+"/message", new StompFrameHandler() {
	            @Override
	            public Type getPayloadType(StompHeaders headers) {
	                return String.class;
	            }

	            @Override
	            public void handleFrame(StompHeaders headers, Object payload) {
	            	net.dstone.common.utils.LogUtil.sysout("받은 메시지: " + payload);
	            	latch.countDown(); // 메시지 받으면 종료
	            }
	        });
	        
	        session.send(WEBSOCKET_STOMP_PUB_PREFIX+"/message", msg);

	        /****************************************************************
	        StompSession.send() 는 비동기로 메시지를 전송.
	              메시지를 전송한 직후 프로그램이 종료되면 → 메시지가 서버에 도달하기 전에 애플리케이션이 종료될 수 있음.
	              따라서 Thread.sleep()로 프로그램 종료를 지연시켜 메시지 송신과 수신 처리 시간을 확보
	        ==>> CountDownLatch 사용하는걸로 수정
	        ****************************************************************/
	        //Thread.sleep(1 * 1000); // 메시지 송수신 대기
	        latch.await(3, java.util.concurrent.TimeUnit.SECONDS); // 메시지 수신 완료까지 대기
	        
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(session != null) {
				session.disconnect();
			}
		}
	}
}
