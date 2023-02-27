package net.dstone.common.task;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.dstone.common.core.BaseObject;

public class TaskHandler extends BaseObject{
	
	private Map<String, ExecutorService> EXECUTOR_SERVICE_MAP;

	private ThreadFactory threadFactory = new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        }
    };
	
	/**
	 * 싱글 쓰레드풀 생성.
	 * @param executorServiceId(쓰레드풀아이디)
	 * @throws Exception
	 */
	public TaskHandler addSingleExecutorService(String executorServiceId) throws Exception{
		if(!EXECUTOR_SERVICE_MAP.containsKey(executorServiceId)) {
			ExecutorService executorService = null;
	        executorService = Executors.newSingleThreadExecutor(threadFactory);
			EXECUTOR_SERVICE_MAP.put(executorServiceId, executorService);
		}else {
			throw new Exception("["+executorServiceId+"]은 이미 존재하는 ExecutorService 입니다.");
		}
		return this;
	}
	
	/**
	 * Fixed 쓰레드풀 생성
	 * @param executorServiceId(쓰레드풀아이디)
	 * @param threadNumWhenFixed(쓰레드갯수)
	 * @throws Exception
	 */
	public TaskHandler addFixedExecutorService(String executorServiceId, int threadNumWhenFixed) throws Exception{
		if(!EXECUTOR_SERVICE_MAP.containsKey(executorServiceId)) {
			ExecutorService executorService = null;
			if(threadNumWhenFixed < 1) {
				threadNumWhenFixed = Runtime.getRuntime().availableProcessors() * (1) * (1);
			}
			executorService = Executors.newFixedThreadPool(threadNumWhenFixed, threadFactory);
			EXECUTOR_SERVICE_MAP.put(executorServiceId, executorService);
		}else {
			throw new Exception("["+executorServiceId+"]은 이미 존재하는 ExecutorService 입니다.");
		}
		return this;
	}
	
	/**
	 * Cached 쓰레드풀 생성
	 * @param executorServiceId(쓰레드풀아이디)
	 * @throws Exception
	 */
	public TaskHandler addCachedExecutorService(String executorServiceId) throws Exception{
		if(!EXECUTOR_SERVICE_MAP.containsKey(executorServiceId)) {
			ExecutorService executorService = null;
	        executorService = Executors.newCachedThreadPool(threadFactory);
			EXECUTOR_SERVICE_MAP.put(executorServiceId, executorService);
		}else {
			throw new Exception("["+executorServiceId+"]은 이미 존재하는 ExecutorService 입니다.");
		}
		return this;
	}

	/**
	 * Custom 쓰레드풀 생성
	 * @param executorServiceId(쓰레드풀아이디)
	 * @param corePoolSize(기본풀사이즈)
	 * @param maximumPoolSize(최대퓰사이즈)
	 * @param queueCapacity(큐용량-corePoolSize보다 쓰레드요청이 많아졌을 경우 이 수치만큼 큐잉한다. 이 수치가 넘어가게 되면 maximumPoolSize까지 쓰레드가 생성됨. corePoolSize+queueCapacity+maximumPoolSize 를 넘어가는 요청이 발생 시 RejectedExecutionException이 발생)
	 * @param keepAliveTime(쓰레드미사용시 제거대기시간-corePoolSize보다 쓰레드요청이 많아졌을 경우 queueCapacity까지 큐잉되다가 큐잉초과시 maximumPoolSize까지 쓰레드가 생성되는데 keepAliveTime시간만큼 유지했다가 다시 corePoolSize로 돌아가는동안 유지되는 시간을 의미)
	 * @throws Exception
	 */
	public TaskHandler addCustomExecutorService(String executorServiceId, int corePoolSize, int maximumPoolSize, int queueCapacity, long keepAliveTime) throws Exception{
		if(!EXECUTOR_SERVICE_MAP.containsKey(executorServiceId)) {
			ThreadPoolExecutor executorService;
			BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(queueCapacity);
	        executorService = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, java.util.concurrent.TimeUnit.SECONDS, workQueue, threadFactory);
			EXECUTOR_SERVICE_MAP.put(executorServiceId, executorService);
		}else {
			throw new Exception("["+executorServiceId+"]은 이미 존재하는 ExecutorService 입니다.");
		}
		return this;
	}
	
	public ExecutorService getExecutorService(String executorServiceId){
		ExecutorService executorService = null;
		executorService = EXECUTOR_SERVICE_MAP.get(executorServiceId);
		return executorService;
	}
	
	public void removeExecutorService(String executorServiceId){
		if(EXECUTOR_SERVICE_MAP.containsKey(executorServiceId)) {
			ExecutorService executorService = this.getExecutorService(executorServiceId);
			this.close(executorService);
			EXECUTOR_SERVICE_MAP.remove(executorServiceId);
		}
	}
	
	/******************************************************************/
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
		getLogger().info(o);
	}
	protected void init(){
		EXECUTOR_SERVICE_MAP = new HashMap<String, ExecutorService>();
	}


	/**
	 * @param executorServiceId
	 * @param item
	 * @return
	 * @throws Exception
	 */
	public TaskItem doTheTask(String executorServiceId, TaskItem item) throws Exception{
		ExecutorService executorService = null;
		Future<TaskItem> future = null;
		
		try {
			net.dstone.common.utils.DateUtil.stopWatchStart("TaskHandler["+executorServiceId+"].doTheTask");
			
			executorService = this.getExecutorService(executorServiceId);
			
			future = executorService.submit(item);
			
			if(future != null){
				item = future.get();
			}
			
		} catch (Exception e) {
			debug( this.getClass().getName() + ".doTheTask() 작없중 예외발생. ID[" + item.getId() + "] 상세내용:" + e.toString());
			throw e;
		} finally {
			net.dstone.common.utils.DateUtil.stopWatchEnd("TaskHandler["+executorServiceId+"].doTheTask");
			//this.close(executorService);
		}
		return item;
	}

	/**
	 * @param executorServiceId
	 * @param itemList
	 * @return
	 * @throws Exception
	 */
	public ArrayList<TaskItem> doTheTasks(String executorServiceId, ArrayList<TaskItem> itemList) throws Exception{
		ExecutorService executorService = null;
		ArrayList<TaskItem> returnVal = new ArrayList<TaskItem>();
		List<Future<TaskItem>> futureList = new ArrayList<Future<TaskItem>>();

		try {
			net.dstone.common.utils.DateUtil.stopWatchStart("TaskHandler["+executorServiceId+"].doTheTasks("+itemList.size()+")");
			executorService = getExecutorService(executorServiceId);
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
			net.dstone.common.utils.DateUtil.stopWatchEnd("TaskHandler["+executorServiceId+"].doTheTasks("+itemList.size()+")");
			//this.close(executorService);
		}

		return returnVal;
	}
	
	private void close(ExecutorService executorService){
		try {
			if( executorService != null ){
				executorService.shutdown();
	            int waitTimeAfterShutdown = 30;
		        if (executorService.awaitTermination(waitTimeAfterShutdown, TimeUnit.SECONDS)) {
		        	debug(LocalTime.now() + " 모든 Task가 종료.");
		        } else {
		        	debug(LocalTime.now() + " "+waitTimeAfterShutdown+"초 대기하였으나 일부 Task가 종료되지 않아서 강제종료 처리.");

		        }
			}
		} catch (Exception e) {
			debug( this.getClass().getName() + ".close() 작없중 예외발생. 상세내용:" + e.toString());
		}

	}
	
}
