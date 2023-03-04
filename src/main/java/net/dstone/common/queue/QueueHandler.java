package net.dstone.common.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import net.dstone.common.core.BaseObject;
import net.dstone.common.task.TaskHandler;
import net.dstone.common.utils.StringUtil;

public class QueueHandler extends BaseObject {

	/**************************** 상수 설정 시작 ****************************/
	public static String EXECUTOR_TYPE_CACHED 	= "CACHED";					// CachedThreadPool 로 작업
	public static String EXECUTOR_TYPE_FIXED 	= "FIXED";					// FixedThreadPool 로 작업
	public static String EXECUTOR_TYPE_CUSTOM 	= "CUSTOM";					// FixedThreadPool 로 작업
	public static String EXECUTOR_TYPE_SINGLE 	= "SINGLE";					// CustomThreadPool 로 작업
	/**************************** 상수 설정 끝 ****************************/
	
	private Map<String, QueueService> QUEUE_SERVICE_MAP;

	protected static QueueHandler queueHandler = null;
	
	public static QueueHandler getInstance(){
		if(queueHandler == null){
			queueHandler = new QueueHandler();
		}
		return queueHandler;
	}
	
	private QueueHandler() {
		init();
	}

	protected void init(){
		QUEUE_SERVICE_MAP = new HashMap<String, QueueService>();
	}

	/**
	 * 큐 생성
	 * @param queueServiceId(큐아이디)
	 * @param conf(기본설정)
	 * @throws Exception
	 */
	public QueueService addQueueService(String queueServiceId, Config conf) throws Exception{
		QueueService queueService = null;
		if(!QUEUE_SERVICE_MAP.containsKey(queueServiceId)) {
			queueService = new QueueService(queueServiceId, conf);
			queueService.setQueueServiceId(queueServiceId);
	        QUEUE_SERVICE_MAP.put(queueServiceId, queueService);
		}else {
			throw new Exception("["+queueServiceId+"]은 이미 존재하는 QueueService 입니다.");
		}
		return queueService;
	}
	
	public QueueService getQueueService(String queueServiceId){
		QueueService queueService = null;
		queueService = QUEUE_SERVICE_MAP.get(queueServiceId);
		return queueService;
	}
	
	public void removeQueueService(String queueServiceId){
		if(QUEUE_SERVICE_MAP.containsKey(queueServiceId)) {
			QueueService queueService = this.getQueueService(queueServiceId);
			queueService.close();
			QUEUE_SERVICE_MAP.remove(queueServiceId);
		}
	}

	public Config newConfig(){
		return new Config();
	}
	
	public class Config{

		/****************************  큐관련 설정 시작 ****************************/
		int queueCheckInterval		= -1; 		// 큐를 감시할 시간간격(밀리초단위)
		int queueFetchSizeByOne 	= -1; 		// 큐에 아이템이 있을 경우 Fetch해올 큐아이템 갯수. -1 이면 큐의 모든 아이템을 Fetch해온다.
		/****************************  큐관련 설정 끝   ****************************/

		/**************************** 쓰레드풀관련 설정 시작 ****************************/
		String executorServiceId 	= "";		// 쓰레드풀아이디.
		String executorType 		= "";		// 쓰레드풀타입.
		
		// TaskHandler-Custom 쓰레드풀 타입일때 사용할 설정
		int corePoolSize 			= -1; 		// 기본풀사이즈
		int queueCapacity 			= -1;		// 큐용량-corePoolSize보다 쓰레드요청이 많아졌을 경우 이 수치만큼 큐잉한다. 이 수치가 넘어가게 되면 maximumPoolSize까지 쓰레드가 생성됨. corePoolSize+queueCapacity+maximumPoolSize 를 넘어가는 요청이 발생 시 RejectedExecutionException이 발생.
		int maximumPoolSize 		= -1;		// 최대퓰사이즈
		int keepAliveTime 			= -1;		// corePoolSize를 초과하여 생성된 쓰레드에 대해서 미사용시 제거대기시간(초단위).-corePoolSize보다 쓰레드요청이 많아졌을 경우 queueCapacity까지 큐잉되다가 큐잉초과시 maximumPoolSize까지 쓰레드가 생성되는데 keepAliveTime시간만큼 유지했다가 다시 corePoolSize로 돌아가는동안 유지되는 시간을 의미.
		
		// TaskHandler-Fixed 쓰레드풀 타입일때 사용할 설정
		int poolSizeWhenFixed 		= -1;		// Fixed 풀사이즈.
		/****************************  쓰레드풀관련 설정 끝   ****************************/

		public Config() {
			this.fillDefaultValues();
		}
		
		public void fillDefaultValues() {
			this.queueCheckInterval = 1000;
			this.queueFetchSizeByOne= 200;
			this.corePoolSize		= 10;
			this.queueCapacity		= 50;
			this.maximumPoolSize	= 200;
			this.keepAliveTime		= 1000;
			this.poolSizeWhenFixed	= 30;
		}

		public String getExecutorServiceId() {
			return executorServiceId;
		}

		public void setExecutorServiceId(String executorServiceId) {
			this.executorServiceId = executorServiceId;
		}
		public int getQueueCheckInterval() {
			return queueCheckInterval;
		}

		public void setQueueCheckInterval(int queueCheckInterval) {
			this.queueCheckInterval = queueCheckInterval;
		}

		public int getQueueFetchSizeByOne() {
			return queueFetchSizeByOne;
		}

		public void setQueueFetchSizeByOne(int fetchSizeByOne) {
			this.queueFetchSizeByOne = fetchSizeByOne;
		}

		public String getExecutorType() {
			return executorType;
		}

		public void setExecutorType(String executorType) {
			this.executorType = executorType;
		}

		public int getCorePoolSize() {
			return corePoolSize;
		}

		public void setCorePoolSize(int corePoolSize) {
			this.corePoolSize = corePoolSize;
		}

		public int getMaximumPoolSize() {
			return maximumPoolSize;
		}

		public void setMaximumPoolSize(int maximumPoolSize) {
			this.maximumPoolSize = maximumPoolSize;
		}

		public int getQueueCapacity() {
			return queueCapacity;
		}

		public void setQueueCapacity(int queueCapacity) {
			this.queueCapacity = queueCapacity;
		}

		public int getKeepAliveTime() {
			return keepAliveTime;
		}

		public void setKeepAliveTime(int keepAliveTime) {
			this.keepAliveTime = keepAliveTime;
		}

		public int getPoolSizeWhenFixed() {
			return poolSizeWhenFixed;
		}

		public void setPoolSizeWhenFixed(int poolSizeWhenFixed) {
			this.poolSizeWhenFixed = poolSizeWhenFixed;
		}
	}

	public class QueueService {
		
		private String queueServiceId				= null;
		private Config conf							= null;
		protected Queue queue 						= null;
		protected QueueThread queueThread 			= null;
		private int workingQueueCount 				= 0;
		
		private TaskHandler taskHandler 			= null;
		
		@SuppressWarnings("unused")
		private QueueService (){}
		
		protected QueueService(String queueServiceId, Config taskConf)throws Exception{
			this.queueServiceId = queueServiceId;
			this.conf = taskConf;
			validateConfig(this.conf);
			init();
		}

		private void validateConfig(Config conf) throws Exception{
			
			if(conf == null) {
				throw new Exception("설정[Config]이 null 입니다.");
			}
			if( StringUtil.isEmpty(conf.executorServiceId) ) {
				throw new Exception("쓰레드풀아이디[executorServiceId]를 세팅해야 합니다.");
			}
			if( StringUtil.isEmpty(conf.executorType) ) {
				throw new Exception("쓰레드풀타입[executorType]을 세팅해야 합니다.");
			}
			if( conf.queueCheckInterval < 0 ) {
				throw new Exception("큐를 감시할 시간간격[queueCheckInterval]을 세팅해야 합니다.");
			}
			if( EXECUTOR_TYPE_CUSTOM.equals(conf.executorType) ) {
				if( conf.corePoolSize < 0 ) {
					throw new Exception("기본풀사이즈[corePoolSize]를 세팅해야 합니다.");
				}
				if( conf.queueCapacity < 0 ) {
					throw new Exception("큐용량[queueCapacity]을 세팅해야 합니다.");
				}
				if( conf.maximumPoolSize < 0 ) {
					throw new Exception("최대퓰사이즈[maximumPoolSize]를 세팅해야 합니다.");
				}
				if( conf.keepAliveTime < 0 ) {
					throw new Exception("쓰레드유휴대기시간[keepAliveTime]을 세팅해야 합니다.");
				}
			}else if( EXECUTOR_TYPE_FIXED.equals(conf.executorType) ) {
				if( conf.poolSizeWhenFixed < 0 ) {
					throw new Exception("고정풀사이즈[poolSizeWhenFixed]를 세팅해야 합니다.");
				}	
			}
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
				String executorServiceId = makeExecutorServiceId();
				if(conf.getExecutorType().equals(EXECUTOR_TYPE_CACHED)) {
					taskHandler.addCachedExecutorService(executorServiceId);
				}else if(conf.getExecutorType().equals(EXECUTOR_TYPE_FIXED)) {
					taskHandler.addFixedExecutorService(executorServiceId,  conf.getPoolSizeWhenFixed());
				}else if(conf.getExecutorType().equals(EXECUTOR_TYPE_CUSTOM)) {
					taskHandler.addCustomExecutorService(executorServiceId, conf.getCorePoolSize(), conf.getMaximumPoolSize(), conf.getQueueCapacity(), conf.getKeepAliveTime());
				}else if(conf.getExecutorType().equals(EXECUTOR_TYPE_SINGLE)) {
					taskHandler.addSingleExecutorService(executorServiceId);
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		public String getQueueServiceId() {
			return queueServiceId;
		}

		public void setQueueServiceId(String queueServiceId) {
			this.queueServiceId = queueServiceId;
		}
		
		private String makeExecutorServiceId() {
			String executorServiceId = this.queueServiceId + "-" + conf.getExecutorServiceId();
			return executorServiceId;
		}

		public void addQueue(QueueItem queueItem) {
			try {
				getLogger().debug("||===================== Adding QueueItem ["+queueItem.getId()+"] =====================||");
				queue.offer(queueItem);
			} catch (Exception e) {
				e.printStackTrace();
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

		protected void close(){
			if(this.queue != null){
				synchronized (this.queue) {
					Iterator<QueueItem> iter = this.queue.iterator();
					QueueItem item = null;
					while(iter.hasNext()) {
						item = iter.next();
						this.queue.remove(item);
					}
				}
			}
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
							Thread.sleep(conf.getQueueCheckInterval());
						}else{
							if( !this.isWorking ){
								doTheJob(fetchFromQueue());
							}
						}
					} catch(InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			
			private ArrayList<QueueItem> fetchFromQueue(){
				ArrayList<QueueItem> queueToBeWorked = null;
				try {
					if(conf.getQueueFetchSizeByOne() == -1 || conf.getQueueFetchSizeByOne() >= queue.size() ){
						queueToBeWorked = new ArrayList<QueueItem>(queue.size());
						queue.drainTo(queueToBeWorked);
					}else{
						queueToBeWorked = new ArrayList<QueueItem>(conf.getQueueFetchSizeByOne());
						queue.drainTo(queueToBeWorked, conf.getQueueFetchSizeByOne());
					}
					
					workingQueueCount = queueToBeWorked.size();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					getLogger().info("Queue로부터 Fetch된 아이템갯수["+workingQueueCount+"] 현재 Queue에 남아있는 아이템갯수["+queue.size()+"]");
				}
				return queueToBeWorked;
			}
			
			private void doTheJob(ArrayList<QueueItem> queueToBeWorked) {
				isWorking = true;
				try {
					net.dstone.common.utils.DateUtil.stopWatchStart("QueueHandler["+queueServiceId+"].doTheJob(QueueItem갯수:"+queueToBeWorked.size()+")");
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
						workList = taskHandler.doTheTasks(makeExecutorServiceId(), workList);
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					net.dstone.common.utils.DateUtil.stopWatchEnd("QueueHandler["+queueServiceId+"].doTheJob(QueueItem갯수:"+queueToBeWorked.size()+")");
					isWorking = false;
				}
			}
		}
		
	}
	
}
