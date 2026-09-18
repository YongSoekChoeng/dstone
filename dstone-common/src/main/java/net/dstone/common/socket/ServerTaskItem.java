package net.dstone.common.socket;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import net.dstone.common.task.TaskItem;
import net.dstone.common.utils.LogUtil;

public class ServerTaskItem extends TaskItem {

    LogUtil logger = new LogUtil(ServerTaskItem.class);

	private ServerSocket serverSocket;
	private Socket socket;
	
	public ServerTaskItem(){
		
	}
	
	public void setSocket(ServerSocket serverSocket, Socket socket) {
		this.serverSocket = serverSocket;
		this.socket = socket;
	}

	private void release(Object obj) {
		if(obj != null) {
			try {
				if( obj instanceof java.net.Socket ) {
					Socket socket = (java.net.Socket)obj;
					if( !socket.isClosed() ) {
						socket.close();
					}
				} else if( obj instanceof ObjectOutputStream ) {
					ObjectOutputStream castObj = (ObjectOutputStream)obj;
					castObj.close();
				} else if( obj instanceof ObjectInputStream ) {
					ObjectInputStream castObj = (ObjectInputStream)obj;
					castObj.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public TaskItem doTheTask() {
		
		ObjectOutputStream oos = null;
		ObjectInputStream  ois = null;
		try{
				
			if(this.serverSocket == null ) {
				throw new Exception( "Server Socket should be set first !!!" );
			}
			if(this.serverSocket.isClosed() ) {
				throw new Exception( "Server Socket is closed !!!" );
			}
			if(this.socket == null ) {
				throw new Exception( "Client Socket should be set first !!!" );
			}
			if(this.socket.isClosed() ) {
				throw new Exception( "Client Socket is closed !!!" );
			}
			
			ois = new ObjectInputStream (socket.getInputStream());  
			oos = new ObjectOutputStream(socket.getOutputStream());
			
			Object readMsg = ois.readObject();
			
			Object writeMsg = this.doTheServerJob(readMsg);
			if( writeMsg != null ) {
				oos.writeObject(writeMsg);
			}
			oos.flush();

		}catch(Exception e){
			logger.error(e);
		}finally{
			release(ois);
			release(oos);
			release(this.socket);
		}
		
		return this;
	}
	
	protected Object doTheServerJob( Object param) {
		Object output = "Hi [" + param + "] ~!";
		System.out.println(output);
		return output;
	}

}
