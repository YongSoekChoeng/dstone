package net.dstone.common.queue;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import net.dstone.common.core.BaseObject;
import net.dstone.common.task.TaskHandler;
import net.dstone.common.utils.StringUtil;

public class QueueHandler extends BaseObject {
	
	/****************************  큐관련 설정 시작 ****************************/
	/**
	 * 큐를 감시할 시간간격.
	 */
	public static int QUEUE_CHECK_INTERVAL 		= 1000; 
	/**
	 * 큐에 아이템이 있을 경우 Fetch해올 큐아이템 갯수. -1 이면 큐의 모든 아이템을 Fetch해온다.
	 */
	public static int FETCH_SIZE_BY_ONE 		= 200; 
	/****************************  큐관련 설정 끝   ****************************/

	/**************************** 쓰레드풀관련 설정 시작 ****************************/
	public static String EXECUTOR_TYPE_CACHED 	= "CACHED";					// CachedThreadPool 로 작업
	public static String EXECUTOR_TYPE_FIXED 	= "FIXED";					// FixedThreadPool 로 작업
	public static String EXECUTOR_TYPE_CUSTOM 	= "CUSTOM";					// FixedThreadPool 로 작업
	public static String EXECUTOR_TYPE_SINGLE 	= "SINGLE";					// CustomThreadPool 로 작업
	
	public static String EXECUTOR_TYPE 			= EXECUTOR_TYPE_CACHED;		// 선택된 쓰레드풀 타입

	// TaskHandler-Custom 쓰레드풀 타입일때 사용할 설정
	public static String EXECUTOR_SERVICE_ID 	= "QHANDLER_THREAD_POOL";	// 쓰레드풀아이디.
	public static int CORE_POOL_SIZE 			= 10; 						// 기본풀사이즈
	public static int MAXIMUM_POOL_SIZE 		= 200;						// 최대퓰사이즈
	public static int QUEUE_CAPACITY 			= 50;						// 큐용량-corePoolSize보다 쓰레드요청이 많아졌을 경우 이 수치만큼 큐잉한다. 이 수치가 넘어가게 되면 maximumPoolSize까지 쓰레드가 생성됨. corePoolSize+queueCapacity+maximumPoolSize 를 넘어가는 요청이 발생 시 RejectedExecutionException이 발생.
	public static int KEEP_ALIVE_TIME 			= 1000;						// corePoolSize를 초과하여 생성된 쓰레드에 대해서 미사용시 제거대기시간(초단위).-corePoolSize보다 쓰레드요청이 많아졌을 경우 queueCapacity까지 큐잉되다가 큐잉초과시 maximumPoolSize까지 쓰레드가 생성되는데 keepAliveTime시간만큼 유지했다가 다시 corePoolSize로 돌아가는동안 유지되는 시간을 의미.
	
	// TaskHandler-Fixed 쓰레드풀 타입일때 사용할 설정
	public static int POOL_SIZE_WHEN_FIXED 		= 30;						// Fixed 풀사이즈.
	/****************************  쓰레드풀관련 설정 끝   ****************************/
	
	protected static QueueHandler queueHandler 	= null;
	protected Queue queue 						= null;
	protected QueueThread queueThread 			= null;
	private static int workingQueueCount 		= 0;
	
	private TaskHandler taskHandler 			= null;
	
	public static QueueHandler getInstance(){
		if(queueHandler == null){
			queueHandler = new QueueHandler();
		}
		return queueHandler;
	}
	
	protected QueueHandler(){		
		init();
	}

	private void debug(Object o){
		getLogger().info(o);
	}
	protected void init(){
		try {
			
			// 큐 초기화
			queue = new Queue();
			// 쓰레드 시작
			queueThread = new QueueThread();
			queueThread.setDaemon(true);
			queueThread.start();
			// Task 쓰레드풀 등록
			taskHandler = net.dstone.common.task.TaskHandler.getInstance();
			if(EXECUTOR_TYPE.equals(EXECUTOR_TYPE_CACHED)) {
				taskHandler.addCachedExecutorService(EXECUTOR_SERVICE_ID);
			}else if(EXECUTOR_TYPE.equals(EXECUTOR_TYPE_FIXED)) {
				taskHandler.addFixedExecutorService(EXECUTOR_SERVICE_ID,  POOL_SIZE_WHEN_FIXED);
			}else if(EXECUTOR_TYPE.equals(EXECUTOR_TYPE_CUSTOM)) {
				taskHandler.addCustomExecutorService(EXECUTOR_SERVICE_ID, CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, QUEUE_CAPACITY, KEEP_ALIVE_TIME);
			}else if(EXECUTOR_TYPE.equals(EXECUTOR_TYPE_SINGLE)) {
				taskHandler.addSingleExecutorService(EXECUTOR_SERVICE_ID);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	public void addQueue(QueueItem queueItem) {
		try {
			
			debug("||======================================= Adding QueueItem ["+queueItem.getId()+"] =======================================||");
			queue.offer(queueItem);
			
		} catch (Exception e) {
			debug(e);
		}
	}
	
	public int getQueueSize(){
		int intSize = 0;
		if(this.queue != null){
			intSize = this.queue.size();
		}
		intSize = intSize + workingQueueCount;
		return intSize;
	}
	
	public void dumpQueue(){
		net.dstone.common.utils.LogUtil.sysout(queue);
	}
	
	@SuppressWarnings({ "serial" })
	class Queue extends LinkedBlockingQueue<QueueItem>{
		
		@Override
		public boolean isEmpty(){
			boolean isEmpty = true;
			if(this.size() > 0){
				isEmpty = false;
			}
			return isEmpty;
		}

		@Override
		public String toString(){
			StringBuffer buff = new StringBuffer();
			Object[] oblArray = super.toArray();
			if(oblArray != null){
				for(int i=0; i<oblArray.length; i++){
					buff.append(oblArray[i]).append("\n");
				}
			}
			return buff.toString();
		}

	}
	
	class QueueThread extends Thread{
		boolean isWorking = false;
		public void run() {
			while (true) {
				try {
					if (queue.isEmpty()){
						Thread.sleep(QUEUE_CHECK_INTERVAL);
					}else{
						if( !this.isWorking ){
							doTheJob(fetchFromQueue());
						}
					}
				} catch(InterruptedException e) {
					debug(e);
				}
			}
		}
		
		private ArrayList<QueueItem> fetchFromQueue(){
			ArrayList<QueueItem> queueToBeWorked = null;
			try {
				if(FETCH_SIZE_BY_ONE == -1 || FETCH_SIZE_BY_ONE >= queue.size() ){
					queueToBeWorked = new ArrayList<QueueItem>(queue.size());
					queue.drainTo(queueToBeWorked);
				}else{
					queueToBeWorked = new ArrayList<QueueItem>(FETCH_SIZE_BY_ONE);
					queue.drainTo(queueToBeWorked, FETCH_SIZE_BY_ONE);
				}
				
				workingQueueCount = queueToBeWorked.size();
			} catch (Exception e) {
				debug(e);
			}
			return queueToBeWorked;
		}
		
		private void doTheJob(ArrayList<QueueItem> queueToBeWorked) {
			isWorking = true;
			try {
				if (queueToBeWorked != null) {
					java.util.ArrayList<net.dstone.common.task.TaskItem> workList = new java.util.ArrayList<net.dstone.common.task.TaskItem>();
					for(int i=0; i<queueToBeWorked.size(); i++) {
						QueueItem queueItem = queueToBeWorked.get(i);
						net.dstone.common.task.TaskItem taskItem = new net.dstone.common.task.TaskItem(){
							@Override
							public net.dstone.common.task.TaskItem doTheTask(){
								try {
									queueItem.doTheJob();
								} catch (Exception e) {
									throw e;
								} finally {
									workingQueueCount--;
								}
								return this;
							}
						};
						if(StringUtil.isEmpty(taskItem.getId())) {
							taskItem.setId("TaskItem-" + i);
						}
						workList.add(taskItem);
					}
					workList = taskHandler.doTheTasks(EXECUTOR_SERVICE_ID, workList);
				}
			} catch (Exception e) {
				debug(e);
			} finally {
				isWorking = false;
			}
		}
		
		
	}

}
