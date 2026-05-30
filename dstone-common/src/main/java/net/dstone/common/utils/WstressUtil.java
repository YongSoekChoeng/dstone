package net.dstone.common.utils;

import java.util.ArrayList;

import net.dstone.common.task.TaskItem;

public class WstressUtil extends net.dstone.common.core.BaseObject {

	private static WstressUtil wStressUtil = null;
	WStressUtilConfig config = new WStressUtilConfig();
	private static long EXEC_NUM = 0l;
	
	/**
	 * WstressUtil
	 */
	private WstressUtil() {
		initialize();
	}
	
	public static WstressUtil getInstance() {
		if (wStressUtil == null) {
			wStressUtil = new WstressUtil();
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
		/**
		 * 적재되는 처리의 MAX값(0:무한, 0보다큰 수:처리MAX값-이 값을 넘어가면 처리하지 않는다.)
		 */
		long maxNumLimit = 0;
		
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
		public long getMaxNumLimit() {
			return maxNumLimit;
		}
		public void setMaxNumLimit(long maxNumLimit) {
			this.maxNumLimit = maxNumLimit;
		}
	}

	public void fireStress(String url, DataSet ds){
		this.fireStress(url, "POST", net.dstone.common.utils.WcUtil.CONT_TYPE_FORM, ds);
	}
	
	public void fireStress(String url, String method, String contentType, DataSet ds){
			
		try {
			StringBuffer msg = new StringBuffer();
			msg.append("\n");
			msg.append("||============================== 스트레스테스트 설정값 시작 ==============================||").append("\n");
			msg.append("ConcurrentUserNum["+config.getConcurrentUserNum()+"] FireNumByUser["+config.getFireNumByUser()+"]").append("\n");
			msg.append("||============================== 스트레스테스트 설정값 끝 ==============================||");
			msg.append("\n");
			
			LogUtil.sysout(msg.toString());
			
			ArrayList<TaskItem> taskList = new ArrayList<TaskItem>();
			for( int i=0; i<config.getConcurrentUserNum(); i++){
				TaskItem taskItem = new TaskItem() {
					@Override
					public TaskItem doTheTask() {
						net.dstone.common.utils.WcUtil ws = new net.dstone.common.utils.WcUtil();
						try {
							
							for(int k=0; k<config.getFireNumByUser(); k++){
								if( config.getMaxNumLimit() > 0 && EXEC_NUM > config.getMaxNumLimit()) {
									break;
								}
								if( config.getThinkTimeByMillSec() > 0 ){
									Thread.sleep(config.getThinkTimeByMillSec());
								}
								net.dstone.common.utils.WcUtil.Bean wsBean = new net.dstone.common.utils.WcUtil.Bean();
								wsBean.setParameters(ds);
								wsBean.url = url;
								wsBean.method = method;			
								wsBean.setContentType(contentType);
								ws.execute(wsBean);
								EXEC_NUM = EXEC_NUM+1l;
							}
							
						} catch (Exception e) {
							e.printStackTrace();
						}
						return this;
					}
				};
				if( config.getMaxNumLimit() > 0) {
					if( config.getMaxNumLimit() > 0 && EXEC_NUM > config.getMaxNumLimit()) {
						break;
					}
				}
				taskList.add(taskItem);
			}
			net.dstone.common.task.TaskHandler.getInstance().addFixedExecutorService("WstressUtil", config.getConcurrentUserNum()).doTheSyncTasks("WstressUtil", taskList);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}	
	
	public static void main(String[] args) {
		
		/*****************************************************/
		int concurrentUserNum 	= 20; 		// 동시접속자 수
		int fireNumByUser 		= 10000; 	// 한명의 접속자가 호출할 횟수 
		int thinkTimeByMillSec 	= 5;		// 접속자가 한번 호출할때마다 가지는 체류시간(think time)-밀리세컨단위
		long maxNumLimit		= 0;		// 최대 처리건수리미트(0일 경우 리밋트없음)
		
		String url =  args[0];
		
		//url = "http://localhost:7080/sample/webstress/insertWebStressBySync.do";
		//url = "http://localhost:7080/sample/webstress/insertWebStressByAsync.do";
		
		net.dstone.common.utils.DataSet param = new net.dstone.common.utils.DataSet();
		param.setDatum("fireNumByUser", String.valueOf(fireNumByUser));
		/*****************************************************/
		
		net.dstone.common.utils.DateUtil.stopWatchStart("01.웹스트레스테스트");
		try {
			net.dstone.common.utils.WstressUtil ws = net.dstone.common.utils.WstressUtil.getInstance();
			
			net.dstone.common.utils.WstressUtil.WStressUtilConfig config = ws.newConfig();
			config.setConcurrentUserNum(concurrentUserNum);
			config.setFireNumByUser(fireNumByUser);
			config.setMaxNumLimit(maxNumLimit);
			config.setThinkTimeByMillSec(thinkTimeByMillSec);
			
			ws.fireStress(url, param);
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
			net.dstone.common.utils.DateUtil.stopWatchEnd("01.웹스트레스테스트");
		}

	}
}
