<%@page import="net.dstone.common.utils.StringUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%    
	net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);
	String scheme = requestUtil.getScheme();
%>   
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
	<head>
	
		<!-- Header 영역 -->
		<%@ include file="/WEB-INF/views/common/header.jsp" %>
		
		<script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
		<script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
	    
	</head>
	<body class="is-preload">

		<!-- Wrapper -->
		<div id="wrapper">

			<!-- Main -->
			<div id="main">
			
				<div class="inner">

					<!-- Top 영역 -->
					<%@ include file="/WEB-INF/views/common/top.jsp" %>
					
					<section>
						<!-- =============================================== Content 영역 Start =============================================== -->
						   
					    <h2>클라이언트(WebSocket) 와 서버(WebSocket)의 통신교환</h2>
					    <input type="text" id="greetingName" placeholder="인사할 사람" />
					    <button onclick="sendWsMessage()">SEND</button>
					    
					    <script>
					    	/***************** WebSocketConfigurer 사용시 ******************************/
					    	const socket = new WebSocket("<%=scheme.equals("http")?"ws":"wss"%>://<%=requestUtil.getServerName()%>:<%=requestUtil.getServerPort()%><%=net.dstone.common.config.ConfigWebSocket.WEBSOCKET_WS_END_POINT%>");
						    socket.onopen = function () {
						        console.log("연결 성공");
						    };
						    socket.onmessage = function (event) {
						        console.log("서버로부터 메시지: " + event.data);
						        var msgData = JSON.parse(event.data);
						        document.getElementById("chat-box").innerHTML += msgData.greetingName + "<br/>";
						    };
						    function sendWsMessage() {
					            const greetingName = document.getElementById("greetingName").value + "님 안녕하세요";
					            var msgData = JSON.stringify({
					                greetingName: greetingName
					            });
						        socket.send(msgData);
						    }
					    	/*************************************************************************/
					    </script>
					    
					    <br><br><br>
					    
					    <h2>클라이언트(SockJS) 와 서버(WebSocket Message Broker)의 통신교환</h2>
					    
					    <input type="text" id="sender" placeholder="이름 입력" />
					    <input type="text" id="message" placeholder="메시지 입력" />
					    <button onclick="sendStompMessage()">SEND</button>
					    
					    <script>
					    	/***************** WebSocketMessageBrokerConfigurer 사용시 *****************/
					    	let stompClient = null;
					        function stompClientconnect() {
					            const socket = new SockJS("<%=scheme%>://<%=requestUtil.getServerName()%>:<%=requestUtil.getServerPort()%><%=net.dstone.common.config.ConfigWebSocket.WEBSOCKET_STOMP_END_POINT%>");
					            stompClient = Stomp.over(socket);
					            stompClient.connect({}, function () {
					                stompClient.subscribe("<%=net.dstone.common.config.ConfigWebSocket.WEBSOCKET_STOMP_SUB_PREFIX%>/message", function (msg) {
					                    const msgObj = JSON.parse(msg.body);
					                    const text = msgObj.sender + ": " + msgObj.content;
					                    document.getElementById("chat-box").innerHTML += text + "<br/>";
					                });
					            });
					        }
					        function sendStompMessage() {
					            const sender = document.getElementById("sender").value;
					            const content = document.getElementById("message").value;
					            stompClient.send("<%=net.dstone.common.config.ConfigWebSocket.WEBSOCKET_STOMP_PUB_PREFIX%>/message", {}, JSON.stringify({
					                sender: sender,
					                content: content
					            }));
					        }
					        stompClientconnect();
					    	/*************************************************************************/
					    </script>
					    
					    <br><br><br>
					    
					    <hr>
					    <div id="chat-box"></div>
					
						<!-- =============================================== Content 영역 End =============================================== -->
					</section>

				</div>
			
			</div>

			<!-- Menu 영역 -->
			<%@ include file="/WEB-INF/views/common/left.jsp" %>

		</div>

	</body>
</html>
