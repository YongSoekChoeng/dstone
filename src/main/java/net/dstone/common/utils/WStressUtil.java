package net.dstone.common.utils;

import java.util.ArrayList;

import net.dstone.common.task.TaskItem;

public class WStressUtil extends net.dstone.common.core.BaseObject {

	private static WStressUtil wStressUtil = null;
	WStressUtilConfig config = new WStressUtilConfig();
	
	/**
	 * WStressUtil
	 */
	private WStressUtil() {
		initialize();
	}
	
	public static WStressUtil getInstance() {
		if (wStressUtil == null) {
			wStressUtil = new WStressUtil();
		}
		return wStressUtil;
	}
	
	private void initialize() {
		
	}
	
	public WStressUtilConfig newConfig(){
		this.config = new WStressUtilConfig();
		return this.config;
	}
	
	public class WStressUtilConfig{
		/**
		 * 동시접속유저 수
		 */
		int concurrentUserNum = 0;
		/**
		 * 각 유저별클릭 수 
		 */
		int fireNumByUser = 0;
		/**
		 * 유저별 클릭진행사이마다 갖는 interval(쉬는시간)
		 */
		long thinkTimeByMillSec = 0;
		
		public int getConcurrentUserNum() {
			return concurrentUserNum;
		}
		public void setConcurrentUserNum(int concurrentUserNum) {
			this.concurrentUserNum = concurrentUserNum;
		}
		public int getFireNumByUser() {
			return fireNumByUser;
		}
		public void setFireNumByUser(int fireNumByUser) {
			this.fireNumByUser = fireNumByUser;
		}
		public long getThinkTimeByMillSec() {
			return thinkTimeByMillSec;
		}
		public void setThinkTimeByMillSec(long thinkTimeByMillSec) {
			this.thinkTimeByMillSec = thinkTimeByMillSec;
		}
	}

	public void fireStress(String url, DataSet ds){
		this.fireStress(url, "POST", net.dstone.common.utils.WsUtil.CONT_TYPE_FORM, ds);
	}
	
	public void fireStress(String url, String method, String contentType, DataSet ds){
		
		net.dstone.common.task.TaskHandler.TaskConfig conf = net.dstone.common.task.TaskHandler.getInstance().getTaskConfig();
		conf.setTaskMode(net.dstone.common.task.TaskHandler.FIXED);
		conf.setThreadNumWhenFixed(config.getConcurrentUserNum());
		conf.setWaitTimeAfterShutdown(1);
						
		try {
			StringBuffer msg = new StringBuffer();
			msg.append("\n");
			msg.append("||============================== 스트레스테스트 설정값 시작 ==============================||").append("\n");
			msg.append("TaskMode["+net.dstone.common.task.TaskHandler.FIXED+"] ThreadNumWhenFixed["+config.getConcurrentUserNum()+"]").append("\n");
			msg.append("ConcurrentUserNum["+config.getConcurrentUserNum()+"] FireNumByUser["+config.getFireNumByUser()+"] ThinkTimeByMillSec["+config.getThinkTimeByMillSec()+"]").append("\n");
			msg.append("||============================== 스트레스테스트 설정값 끝 ==============================||");
			msg.append("\n");
			this.getLogger().info(msg.toString());
			
			ArrayList<TaskItem> taskList = new ArrayList<TaskItem>();
			for(int i=0; i<conf.getThreadNumWhenFixed(); i++){
				taskList.add(
					new TaskItem() {
						@Override
						public TaskItem doTheTask() {
							
							net.dstone.common.utils.WsUtil ws = new net.dstone.common.utils.WsUtil();
							try {
								
								for(int k=0; k<config.getFireNumByUser(); k++){
									
									if( config.getThinkTimeByMillSec() > 0 ){
										Thread.sleep(config.getThinkTimeByMillSec());
									}
									net.dstone.common.utils.WsUtil.Bean wsBean = new net.dstone.common.utils.WsUtil.Bean();
									wsBean.setParameters(ds);
									wsBean.url = url;
									wsBean.method = method;			
									wsBean.setContentType(contentType);
									ws.execute(wsBean);
									
								}

							} catch (Exception e) {
								e.printStackTrace();
							}
							return this;
						}
					}						
				);
			}
			net.dstone.common.task.TaskHandler.getInstance().doTheTasks(conf, taskList);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}	
}
