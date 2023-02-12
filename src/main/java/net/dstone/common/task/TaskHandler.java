package net.dstone.common.task;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TaskHandler {

	/****************************  설정 시작 ****************************/
	public static int CACHED 	= 0;	// newCachedThreadPool 로 작업
	public static int FIXED 	= 1;	// newFixedThreadPool 로 작업
	public static int SINGLE 	= 2;	// newSingleThreadExecutor 로 작업
	/****************************  설정 끝   ****************************/
	
	public class TaskConfig{
		private TaskConfig(){
		}
		/**
		 * @param taskMode : TaskHandler.CACHED, TaskHandler.FIXED, TaskHandler.SINGLE 중 하나.
		 */
		public TaskConfig(int taskMode){
			this.taskMode = taskMode;
		}
		/**
		 * @param taskMode : TaskHandler.CACHED, TaskHandler.FIXED, TaskHandler.SINGLE 중 하나.
		 * @param threadNumWhenFixed : taskMode 가 TaskHandler.FIXED 일 때 생성할 쓰레드 갯수.
		 */
		public TaskConfig(int taskMode, int threadNumWenFixed){
			this.taskMode = taskMode;
			if(threadNumWenFixed > 0){
				this.threadNumWhenFixed = threadNumWenFixed;
			}			
		}
		/**
		 * @param taskMode : TaskHandler.CACHED, TaskHandler.FIXED, TaskHandler.SINGLE 중 하나.
		 * @param threadNumWhenFixed : taskMode 가 TaskHandler.FIXED 일 때 생성할 쓰레드 갯수.
		 * @param waitTimeAfterShutdown : shutdown 명령 이후 쓰레드가 실제로 종료했는지 확인하기 위한 대기시간. 이 시간이 지나면 강제종료.
		 */
		public TaskConfig(int taskMode, int threadNumWenFixed, int waitTimeAfterShutdown){
			this.taskMode = taskMode;
			if(threadNumWenFixed > 0){
				this.threadNumWhenFixed = threadNumWenFixed;
			}			
			if(waitTimeAfterShutdown > 0){
				this.waitTimeAfterShutdown = waitTimeAfterShutdown;
			}			
		}
		protected int taskMode = SINGLE;
		/*******************************************************************
		(적당한 스레드 수) = (CPU 개수) * (CPU 활용도 0~1) * ( 1 + 작업시간 대비 대기 시간의 비율)
		*******************************************************************/
		protected int threadNumWhenFixed = Runtime.getRuntime().availableProcessors() * (1) * (1);
		protected int waitTimeAfterShutdown = 0;
		
		public int getTaskMode() {
			return taskMode;
		}
		public void setTaskMode(int taskMode) {
			this.taskMode = taskMode;
		}
		public int getThreadNumWhenFixed() {
			return threadNumWhenFixed;
		}
		public void setThreadNumWhenFixed(int threadNumWenFixed) {
			this.threadNumWhenFixed = threadNumWenFixed;
		}
		public int getWaitTimeAfterShutdown() {
			return waitTimeAfterShutdown;
		}
		public void setWaitTimeAfterShutdown(int waitTimeAfterShutdown) {
			this.waitTimeAfterShutdown = waitTimeAfterShutdown;
		}
	}
	
	protected static TaskHandler taskHandler = null;
	
	public static TaskHandler getInstance(){
		if(taskHandler == null){
			taskHandler = new TaskHandler();
		}
		return taskHandler;
	}
	
	protected TaskHandler(){		
		init();
	}

	private void debug(Object o){
		net.dstone.common.utils.LogUtil.sysout(o);
	}
	protected void init(){
		
	}
	
	public TaskConfig getTaskConfig(){
		return new TaskConfig();
	}
	
	/**
	 * @param conf
	 * @param item
	 * @return
	 * @throws Exception
	 */
	public TaskItem doTheTask(TaskConfig conf, TaskItem item) throws Exception{
		ExecutorService executorService = null;
		Future<TaskItem> future = null;
		
		net.dstone.common.utils.DateUtil.stopWatchStart("TaskHandler.doTheTask");
		try {
			
			if(conf.taskMode == TaskHandler.CACHED){
				executorService = Executors.newCachedThreadPool();
				future = executorService.submit(item);
			}else if(conf.taskMode == TaskHandler.FIXED){
				executorService = Executors.newFixedThreadPool(conf.threadNumWhenFixed);
				future = executorService.submit(item);
			}else{
				executorService = Executors.newSingleThreadExecutor();
				future = executorService.submit(item);
			}
			
			if(future != null){
				item = future.get();
			}
			
		} catch (Exception e) {
			debug( this.getClass().getName() + ".doTheTask() 작없중 예외발생. ID[" + item.getId() + "] 상세내용:" + e.toString());
			throw e;
		} finally {
			close(executorService, conf);
			net.dstone.common.utils.DateUtil.stopWatchEnd("TaskHandler.doTheTask");
		}
		return item;
	}

	/**
	 * @param conf
	 * @param itemList
	 * @return
	 * @throws Exception
	 */
	public ArrayList<TaskItem> doTheTasks(TaskConfig conf, ArrayList<TaskItem> itemList) throws Exception{
		ExecutorService executorService = null;
		ArrayList<TaskItem> returnVal = new ArrayList<TaskItem>();
		List<Future<TaskItem>> futureList = new ArrayList<Future<TaskItem>>();

		net.dstone.common.utils.DateUtil.stopWatchStart("TaskHandler.doTheTasks");
		if(conf.taskMode == TaskHandler.CACHED){
			executorService = Executors.newCachedThreadPool();
		}else if(conf.taskMode == TaskHandler.FIXED){
			executorService = Executors.newFixedThreadPool(conf.threadNumWhenFixed);
		}else{
			executorService = Executors.newSingleThreadExecutor();
		}

		try {
			
			futureList = executorService.invokeAll(itemList);
			
			if(futureList != null){
				Future<TaskItem> future = null;
				for(int i=0; i<futureList.size(); i++ ){
					future = futureList.get(i);
				    try {
				    	returnVal.add(future.get());
				    } catch (Exception e) {
				    	returnVal.add(null);
				    }
				}
			}
		} catch (Exception e) {
			debug( this.getClass().getName() + ".doTheTasks() 작없중 예외발생. 상세내용:" + e.toString());
			throw e;
		} finally {
			close(executorService, conf);
			net.dstone.common.utils.DateUtil.stopWatchEnd("TaskHandler.doTheTasks");
		}

		return returnVal;
	}
	
	private void close(ExecutorService executorService, TaskConfig conf){
		try {
			if( executorService != null ){
				executorService.shutdown();
				if(conf.waitTimeAfterShutdown > 0){
			        if (executorService.awaitTermination(conf.waitTimeAfterShutdown, TimeUnit.SECONDS)) {
			        	debug(LocalTime.now() + " 모든 Task가 종료.");
			        } else {
			        	debug(LocalTime.now() + " "+conf.waitTimeAfterShutdown+"초 대기하였으나 일부 Task가 종료되지 않아서 강제종료 처리.");
			            // 모든 Task를 강제 종료.
			            executorService.shutdownNow();
			        }
				}
			}
		} catch (Exception e) {
			debug( this.getClass().getName() + ".close() 작없중 예외발생. 상세내용:" + e.toString());
		}

	}
	
}
