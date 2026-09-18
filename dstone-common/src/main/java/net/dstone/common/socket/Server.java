package net.dstone.common.socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;

public class Server extends BaseObject {
	
	public static class SOCKET_SERVER_LIST{
		
		public static class ECHO_SERVER{
			public static String ID = "ECHO_SOCKET_SERVER";
			public static int PORT = 1000;
		}
		
		public static class GREETING_SERVER{
			public static String ID = "GREETING_SOCKET_SERVER";
			public static int PORT = 2000;
		}
		
	}
	
	public static class SOCKET_COMMAND{
		public static String QUIT = "SOCKET_COMMAND.QUIT";
	}
	
	LogUtil logger = new LogUtil(Server.class);
	
	private net.dstone.common.task.TaskHandler taskHandler;

	String executorServiceId;
	private int port;
	
	private Server(){
	}

	public Server(String executorServiceId, int port) throws Exception{
		this.executorServiceId = executorServiceId;
		this.port = port;
		taskHandler = net.dstone.common.task.TaskHandler.getInstance().addFixedExecutorService(executorServiceId+"_FIXED", 10);
	}
	
	public void start() throws Exception{

		ServerSocket serverSocket = null;
		Socket socket = null;
		try{
			LogUtil.sysout("executorServiceId["+executorServiceId+"] START");
			serverSocket = new ServerSocket(this.port);
			
			while( true ) {
				socket = serverSocket.accept();
				
				ServerTaskItem taskItem = new ServerTaskItem();
				taskItem.setSocket(serverSocket, socket);
				taskItem.setExecutorServiceId(executorServiceId);
				taskHandler.doTheAsyncTask(executorServiceId+"_FIXED", taskItem);
				
			}
			
		} catch(IOException e){
			LogUtil.sysout(e);
		} finally {
			release(serverSocket);
			LogUtil.sysout("executorServiceId["+executorServiceId+"] END");
		}
	}
	
	private void release(Object obj) {
		if(obj != null) {
			try {
				if( obj instanceof java.net.ServerSocket ) {
					ServerSocket serverSocket = (java.net.ServerSocket)obj;
					if( !serverSocket.isClosed() ) {
						serverSocket.close();
					}
				}else if( obj instanceof java.net.Socket ) {
					Socket socket = (java.net.Socket)obj;
					if( !socket.isClosed() ) {
						socket.close();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	

	public static void main(String[] args) {
		
		try {
			
			Server greetingServer = new Server(SOCKET_SERVER_LIST.GREETING_SERVER.ID, SOCKET_SERVER_LIST.GREETING_SERVER.PORT);
			greetingServer.start();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
}
