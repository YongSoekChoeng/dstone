package net.dstone.common.task;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.DateUtil;
import net.dstone.common.utils.StringUtil;

public class TaskHandler extends BaseObject{
	
	public class TaskReport{

		int monitoringSec = Integer.parseInt(DateUtil.getToDate("HHmmss"));
		
		int tryCount = 0;
		int successCount = 0;
		int errorCount = 0;
		BigDecimal rate = BigDecimal.ZERO;
		
		public int getTryCount() {
			return tryCount;
		}
		public void setTryCount(int tryCount) {
			this.tryCount = tryCount;
			this.calcurateRate();
		}
		public synchronized void addTryCount() {
			this.tryCount++;
			this.calcurateRate();
		}
		public synchronized void addTryCount(int tryCount) {
			this.tryCount = this.tryCount + tryCount;
			this.calcurateRate();
		}
		
		public int getSuccessCount() {
			return successCount;
		}
		public void setSuccessCount(int successCount) {
			this.successCount = successCount;
			this.calcurateRate();
		}
		public synchronized void addSuccessCount() {
			this.successCount++;
			this.calcurateRate();
		}
		public synchronized void addSuccessCount(int successCount) {
			this.successCount = this.successCount + successCount;
			this.calcurateRate();
		}
		
		public int getErrorCount() {
			return errorCount;
		}
		public void setErrorCount(int errorCount) {
			this.errorCount = errorCount;
			this.calcurateRate();
		}
		public synchronized void addErrorCount() {
			this.errorCount++;
			this.calcurateRate();
		}
		public synchronized void addErrorCount(int errorCount) {
			this.errorCount = this.errorCount + errorCount;
			this.calcurateRate();
		}

		public int getMonitoringSec() {
			return monitoringSec;
		}
		public void setMonitoringSec(int monitoringSec) {
			this.monitoringSec = monitoringSec;
		}

		public BigDecimal getRate() {
			return rate;
		}
		public void setRate(BigDecimal rate) {
			this.rate = rate;
		}
		
		private void calcurateRate() {
			try {
				if( this.getTryCount() > 0 && this.getSuccessCount() > 0) {
					BigDecimal bRate = new BigDecimal(this.getSuccessCount());
					bRate = bRate.add(new BigDecimal(this.getErrorCount()));
					bRate = bRate.divide(new BigDecimal(this.getTryCount()), 4, BigDecimal.ROUND_HALF_UP);
					bRate = bRate.multiply(new BigDecimal(100));
					bRate = bRate.setScale(2);
					this.rate = bRate;
				}
			} catch (Exception e) {
				// TODO: handle exception
			}
		}
		
		@Override
		public String toString() {
			return "TaskReport [monitoringSec=" + monitoringSec + ", tryCount=" + tryCount + ", successCount="
					+ successCount + ", errorCount=" + errorCount + ", rate=" + rate + "]";
		}
	}
	
	private Map<String, ExecutorService> EXECUTOR_SERVICE_MAP;
	private Map<String, TaskReport> EXECUTOR_SERVICE_REPORT_MAP;
	private int EXECUTOR_SERVICE_REPORT_INTERVAL = 10;

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
		getLogger().info("TaskHandler.addSingleExecutorService("+executorServiceId+") has been called !!!");
		if(!EXECUTOR_SERVICE_MAP.containsKey(executorServiceId)) {
			ExecutorService executorService = null;
	        executorService = Executors.newSingleThreadExecutor(threadFactory);
			EXECUTOR_SERVICE_MAP.put(executorServiceId, executorService);
		}
		if(!EXECUTOR_SERVICE_REPORT_MAP.containsKey(executorServiceId)) {
			EXECUTOR_SERVICE_REPORT_MAP.put(executorServiceId, new TaskReport());
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
		getLogger().info("TaskHandler.addFixedExecutorService("+executorServiceId+", "+threadNumWhenFixed+") has been called !!!");
		if(!EXECUTOR_SERVICE_MAP.containsKey(executorServiceId)) {
			ExecutorService executorService = null;
			if(threadNumWhenFixed < 1) {
				threadNumWhenFixed = Runtime.getRuntime().availableProcessors() * (1) * (1);
			}
			executorService = Executors.newFixedThreadPool(threadNumWhenFixed, threadFactory);
			EXECUTOR_SERVICE_MAP.put(executorServiceId, executorService);
		}
		if(!EXECUTOR_SERVICE_REPORT_MAP.containsKey(executorServiceId)) {
			EXECUTOR_SERVICE_REPORT_MAP.put(executorServiceId, new TaskReport());
		}
		return this;
	}
	
	/**
	 * Cached 쓰레드풀 생성
	 * @param executorServiceId(쓰레드풀아이디)
	 * @throws Exception
	 */
	public TaskHandler addCachedExecutorService(String executorServiceId) throws Exception{
		getLogger().info("TaskHandler.addCachedExecutorService("+executorServiceId+") has been called !!!");
		if(!EXECUTOR_SERVICE_MAP.containsKey(executorServiceId)) {
			ExecutorService executorService = null;
	        executorService = Executors.newCachedThreadPool(threadFactory);
			EXECUTOR_SERVICE_MAP.put(executorServiceId, executorService);
		}
		if(!EXECUTOR_SERVICE_REPORT_MAP.containsKey(executorServiceId)) {
			EXECUTOR_SERVICE_REPORT_MAP.put(executorServiceId, new TaskReport());
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
		getLogger().info("TaskHandler.addCustomExecutorService("+executorServiceId+", "+corePoolSize+", "+maximumPoolSize+" ,"+queueCapacity+", "+keepAliveTime+") has been called !!!");
		if(!EXECUTOR_SERVICE_MAP.containsKey(executorServiceId)) {
			ThreadPoolExecutor executorService;
			BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(queueCapacity);
	        executorService = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, java.util.concurrent.TimeUnit.SECONDS, workQueue, threadFactory);
			EXECUTOR_SERVICE_MAP.put(executorServiceId, executorService);
		}
		if(!EXECUTOR_SERVICE_REPORT_MAP.containsKey(executorServiceId)) {
			EXECUTOR_SERVICE_REPORT_MAP.put(executorServiceId, new TaskReport());
		}
		return this;
	}
	
	private ExecutorService getExecutorService(String executorServiceId) throws Exception{
		ExecutorService executorService = null;
		if(this.isExecutorServiceExists(executorServiceId)) {
			executorService = EXECUTOR_SERVICE_MAP.get(executorServiceId);
		}else {
			throw new Exception("["+executorServiceId+"]은  존재하지 않는 ExecutorService입니다.");
		}
		return executorService;
	}
	
	private void close(String executorServiceId){
		ExecutorService executorService = null;
		try {
			executorService = this.getExecutorService(executorServiceId);
			if( executorService != null ){
				executorService.shutdown();
	            int waitTimeAfterShutdown = 1*60;
		        if (executorService.awaitTermination(waitTimeAfterShutdown, TimeUnit.SECONDS)) {
		        	getLogger().debug(LocalTime.now() + " 모든 Task가 종료.");
		        } else {
		        	getLogger().debug(LocalTime.now() + " "+waitTimeAfterShutdown+"초 대기하였으나 일부 Task가 종료되지 않아서 강제종료 처리.");
		        	executorService.shutdownNow();
		        }
			}
		} catch (Exception e) {
			getLogger().debug( this.getClass().getName() + ".close() 작없중 예외발생. 상세내용:" + e.toString());
		}

	}
	
	public boolean isExecutorServiceExists(String executorServiceId) {
		boolean isExists = false;
		isExists = EXECUTOR_SERVICE_MAP.containsKey(executorServiceId);
		return isExists;
	}
	public TaskReport getExecutorServiceTaskReport(String executorServiceId){
		TaskReport taskReport = null;
		taskReport = EXECUTOR_SERVICE_REPORT_MAP.get(executorServiceId);
		return taskReport;
	}
	
	public void removeExecutorService(String executorServiceId) throws Exception{
		if(EXECUTOR_SERVICE_MAP.containsKey(executorServiceId)) {
			this.close(executorServiceId);
			EXECUTOR_SERVICE_MAP.remove(executorServiceId);
		}
		if(EXECUTOR_SERVICE_REPORT_MAP.containsKey(executorServiceId)) {
			EXECUTOR_SERVICE_REPORT_MAP.remove(executorServiceId);
		}
	}
	
	public void checkExecutorServiceAll(){
		Iterator<String> keys = EXECUTOR_SERVICE_REPORT_MAP.keySet().iterator();
		StringBuffer buff = new StringBuffer();

		buff.append("\n\n");
		buff.append("||@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ 쓰레드풀체크 Start  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@||").append("\n");
		debug(buff);
		
		while(keys.hasNext()) {
			String executorServiceId = keys.next();
			this.checkExecutorService(executorServiceId);
		}
		buff.setLength(0);
		buff.append("\n");
		buff.append("||@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ 쓰레드풀체크 End  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@||").append("\n");
		buff.append("\n\n");
		debug(buff);
		
	}
	
	public void checkExecutorService(String executorServiceId){
		if(EXECUTOR_SERVICE_REPORT_MAP.containsKey(executorServiceId)) {
			this.doMonitoringNow(executorServiceId);
		}
	}
	
	public int getTaskItemCount(){
		int cnt = 0;
		if( EXECUTOR_SERVICE_MAP != null) {
			Iterator<String> keys = EXECUTOR_SERVICE_MAP.keySet().iterator();
			while(keys.hasNext()) {
				String executorServiceId = keys.next();
				cnt =  cnt + ( this.getExecutorServiceTaskReport(executorServiceId).getTryCount() + (this.getExecutorServiceTaskReport(executorServiceId).getSuccessCount()+this.getExecutorServiceTaskReport(executorServiceId).getErrorCount()) );
			}
		}
		return cnt;
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

	protected void init(){
		EXECUTOR_SERVICE_MAP = new HashMap<String, ExecutorService>();
		EXECUTOR_SERVICE_REPORT_MAP = new HashMap<String, TaskReport>();
	}

	/**
	 * @param executorServiceId
	 * @param item
	 * @return
	 * @throws Exception
	 */
	public TaskItem doTheSyncTask(String executorServiceId, TaskItem item) throws Exception{
		ExecutorService executorService = null;
		Future<TaskItem> future = null;
		try {
			net.dstone.common.utils.DateUtil.stopWatchStart("TaskHandler["+executorServiceId+"].doTheSyncTask");
			executorService = this.getExecutorService(executorServiceId);
			item.setExecutorServiceId(executorServiceId);
			future = executorService.submit((Callable<TaskItem>)item);
			
			if(future != null){
				item = future.get();
			}

		} catch (Exception e) {
			getLogger().info( this.getClass().getName() + ".doTheSyncTask() 작없중 예외발생. ID[" + item.getId() + "] 상세내용:" + e.toString());
			throw e;
		} finally {
			doMonitoring(executorServiceId);
			net.dstone.common.utils.DateUtil.stopWatchEnd("TaskHandler["+executorServiceId+"].doTheSyncTask");
			//this.close(executorServiceId);
		}
		return item;
	}
	
	/**
	 * @param executorServiceId
	 * @param item
	 * @throws Exception
	 */
	public void doTheAsyncTask(String executorServiceId, TaskItem item) throws Exception{
		ExecutorService executorService = null;
		try {
			net.dstone.common.utils.DateUtil.stopWatchStart("TaskHandler["+executorServiceId+"].doTheAyncTask");
			executorService = this.getExecutorService(executorServiceId);
			
			item.setExecutorServiceId(executorServiceId);

			executorService.execute((Runnable)item);

		} catch (Exception e) {
			getLogger().info( this.getClass().getName() + ".doTheAyncTask() 작없중 예외발생. ID[" + item.getId() + "] 상세내용:" + e.toString());
			throw e;
		} finally {
			doMonitoring(executorServiceId);
			net.dstone.common.utils.DateUtil.stopWatchEnd("TaskHandler["+executorServiceId+"].doTheAyncTask");
			//this.close(executorServiceId);
		}
	}

	/**
	 * @param executorServiceId
	 * @param itemList
	 * @return
	 * @throws Exception
	 */
	public ArrayList<TaskItem> doTheSyncTasks(String executorServiceId, ArrayList<TaskItem> itemList) throws Exception{
		ExecutorService executorService = null;
		ArrayList<TaskItem> returnVal = new ArrayList<TaskItem>();
		List<Future<TaskItem>> futureList = new ArrayList<Future<TaskItem>>();
		
		String msg = "TaskHandler["+executorServiceId+"].doTheSyncTasks(TaskItem갯수:"+itemList.size()+")";
		
		try {
			net.dstone.common.utils.DateUtil.stopWatchStart(msg);
			executorService = getExecutorService(executorServiceId);
			for(TaskItem item : itemList) {
				item.setExecutorServiceId(executorServiceId);
			}
			
			futureList = executorService.invokeAll(itemList);
			
			if(futureList != null){
				Future<TaskItem> future = null;
				for(int i=0; i<futureList.size(); i++ ){
					future = futureList.get(i);
					TaskItem item = future.get();
				    try {
				    	returnVal.add(item);
				    } catch (Exception e) {
				    	returnVal.add(null);
				    } finally {

				    }
				}
			}
			//getLogger().info("Active 쓰레드갯수[doTheTasks시작시점:"+startThreadCount+"개 ==>> doTheTasks종료시점:"+Thread.activeCount()+"개]");
		} catch (Exception e) {
			getLogger().info( this.getClass().getName() + ".doTheSyncTasks() 작없중 예외발생. 상세내용:" + e.toString());
			throw e;
		} finally {
			doMonitoring(executorServiceId);
			net.dstone.common.utils.DateUtil.stopWatchEnd(msg);

			if(futureList != null) {
				futureList.clear();
				futureList = null;
			}
			if(itemList != null) {
				itemList.clear();
				itemList = null;
			}
			
			//this.close(executorServiceId);
		}
		return returnVal;
	}
	
	/**
	 * @param executorServiceId
	 * @param itemList
	 * @throws Exception
	 */
	public void doTheAsyncTasks(String executorServiceId, ArrayList<TaskItem> itemList) throws Exception{
		ExecutorService executorService = null;
		
		String msg = "TaskHandler["+executorServiceId+"].doTheAsyncTasks(TaskItem갯수:"+itemList.size()+")";
		
		try {
			net.dstone.common.utils.DateUtil.stopWatchStart(msg);
			executorService = getExecutorService(executorServiceId);
			for(TaskItem item : itemList) {
				item.setExecutorServiceId(executorServiceId);
				executorService.execute(item);
			}
			//getLogger().info("Active 쓰레드갯수[doTheTasks시작시점:"+startThreadCount+"개 ==>> doTheTasks종료시점:"+Thread.activeCount()+"개]");
		} catch (Exception e) {
			getLogger().info( this.getClass().getName() + ".doTheAsyncTasks() 작없중 예외발생. 상세내용:" + e.toString());
			throw e;
		} finally {
			doMonitoring(executorServiceId);
			net.dstone.common.utils.DateUtil.stopWatchEnd(msg);

			if(itemList != null) {
				itemList.clear();
				itemList = null;
			}
			//this.close(executorServiceId);
		}
	}
	

	
	/**
	 * 작업진행모니터링(EXECUTOR_SERVICE_REPORT_INTERVAL 만큼 주기를 두고 모니터링한다.)
	 */
	public TaskReport doMonitoring(String executorServiceId) {
		TaskReport taskReport = this.getExecutorServiceTaskReport(executorServiceId);
		if( taskReport != null ) {
			boolean isTimeToReport = false;
			
			if( taskReport.getTryCount() > 0) {
				if( taskReport.getTryCount() == (taskReport.getSuccessCount() + taskReport.getErrorCount()) ) {
					isTimeToReport = true;
				}else {
					String beforeSec = StringUtil.filler(String.valueOf(taskReport.getMonitoringSec()), 6, "0");
					int hour = Integer.parseInt(beforeSec.substring(0, 2));
					int min = Integer.parseInt(beforeSec.substring(2, 4));
					int sec = Integer.parseInt(beforeSec.substring(4));
					
					java.util.Calendar beforeCal = java.util.Calendar.getInstance();
					beforeCal.set(java.util.Calendar.HOUR_OF_DAY, hour);
					beforeCal.set(java.util.Calendar.MINUTE, min);
					beforeCal.set(java.util.Calendar.SECOND, sec);
					
					beforeCal.add(java.util.Calendar.SECOND, EXECUTOR_SERVICE_REPORT_INTERVAL);
					
					java.util.Calendar currentCal = java.util.Calendar.getInstance();				
					if( beforeCal.compareTo(currentCal) <= 0 ) {
						isTimeToReport = true;
						taskReport.setMonitoringSec(Integer.parseInt(DateUtil.getToDate("HHmmss")));
					}
				}
			}
			
			if(isTimeToReport) {
				this.doMonitoringNow(executorServiceId);
			}
		}
		return taskReport;
	}
	
	/**
	 * 작업진행모니터링(주기에 상관없이 현재 처리진행을 모니터링한다.)
	 */
	public TaskReport doMonitoringNow(String executorServiceId) {
		TaskReport taskReport = this.getExecutorServiceTaskReport(executorServiceId);
		if( taskReport != null ) {
			StringBuffer buff = new StringBuffer();

			buff.append("\n\n");
			buff.append("||============================================== 쓰레드풀아이디ID["+executorServiceId+"] 작업진행현황  ==============================================||").append("\n");
			buff.append("서비스ID["+executorServiceId+"]" + " 총진행대상건수["+taskReport.getTryCount()+"] 진행완료건수["+taskReport.getSuccessCount()+"] 작업진행률["+ taskReport.getRate().intValue() +"%]").append("\n");
			buff.append("||============================================== 쓰레드풀아이디ID["+executorServiceId+"] 작업진행현황  ==============================================||").append("\n");
			debug(buff);
		}
		return taskReport;
	}
	
	public TaskReport getTaskReport(String executorServiceId){
		return EXECUTOR_SERVICE_REPORT_MAP.get(executorServiceId);
	}
	
	public Map<String, TaskReport> getTaskReportAll(){
		return EXECUTOR_SERVICE_REPORT_MAP;
	}
	
}
