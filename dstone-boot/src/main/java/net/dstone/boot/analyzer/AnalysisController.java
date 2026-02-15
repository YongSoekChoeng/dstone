package net.dstone.boot.analyzer; 
 
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import net.dstone.boot.analyzer.taskitem.AnalysisItem;
import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.common.queue.QueueHandler;
import net.dstone.common.queue.QueueItem;
import net.dstone.common.task.TaskHandler;
import net.dstone.common.task.TaskHandler.TaskReport;
import net.dstone.common.utils.RequestUtil;

@Controller
@RequestMapping(value = "/analyzer/analysis/*")
public class AnalysisController extends net.dstone.boot.common.biz.BaseController { 

	private static String QUEUE_HANDLER_ID = "doAnalyzingQueue";
	
    /********* SVC 정의부분 시작 *********/
    @Autowired 
    private net.dstone.boot.analyzer.ConfigurationService configurationService; 
    /********* SVC 정의부분 끝 *********/
    
    /** 
     * 분석작업 입력 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/doAnalyzing.do") 
    public ModelAndView doAnalyzing(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 			requestUtil;
   		String                  sysId;
   		String[]				analyzeJobKindArr; 
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		sysId					= null;
   		analyzeJobKindArr	 	= null;
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 취합
   		sysId					= requestUtil.getParameter("SYS_ID");
   		analyzeJobKindArr	 	= requestUtil.getParameterValues("ANALYZE_JOB_KIND");
   		
   		// 2. 분석작업종류별 작업실행.
   		if( analyzeJobKindArr != null && analyzeJobKindArr.length > 0 ) {
   			net.dstone.boot.analyzer.vo.SysVo paramVo = new net.dstone.boot.analyzer.vo.SysVo();
   	   		paramVo.setSYS_ID(sysId);
   	   		paramVo = configurationService.getSys(paramVo);
   			if(paramVo != null) { 
   				
   				String analyzingServiceId = "doAnalyzing";
   				int queueCheckInterval = 1*1000; 
   				int queueItemSizeByOne = 1;   				
   				for(int i=0; i<analyzeJobKindArr.length; i++) {
   					String analyzeJobKind = analyzeJobKindArr[i];
   					String executorServiceId = AppAnalyzer.JOB_KIND_MAP.get(Integer.valueOf(analyzeJobKind));
   	   				String confFilePath = paramVo.getCONF_FILE_PATH();
   	   				
   	   				QueueItem queueItem = new QueueItem() {
   						@Override
   						public void doTheJob() {
   							TaskHandler taskHandler = TaskHandler.getInstance();
   							try {
   								if( !taskHandler.isExecutorServiceExists(analyzingServiceId) ) {
   									taskHandler.addSingleExecutorService(analyzingServiceId);
   								}
   	   							AnalysisItem analysisItem = new AnalysisItem();
   	   							analysisItem.setId(executorServiceId);
	   	    	   				analysisItem.setAnalyzeJobKind(Integer.valueOf(analyzeJobKind));
	   	    	   				analysisItem.setConfigFilePath(confFilePath);
	   	    	   				taskHandler.doTheSyncTask(analyzingServiceId, analysisItem);
							} catch (Exception e) {
								e.printStackTrace();
							}
   						}
   					};
   					queueItem.setId(executorServiceId);
   	   				
   	   				QueueHandler.getInstance(QUEUE_HANDLER_ID, queueCheckInterval, queueItemSizeByOne).addQueue(queueItem);
   				}
   			}
   		}
   		
   		boolean result 			= true;
   		
   		// 3. 결과처리
   		mav.addObject("RETURN_CD", net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   		mav.addObject("returnObj", new Boolean(result)	);
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    }
    
    /********* SVC 정의부분 끝 *********/
    
    /** 
     * 분석작업 중지
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/stopAnalyzing.do") 
    public ModelAndView stopAnalyzing(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 						requestUtil;
   		String[]							analyzeJobKindArr; 
   		ArrayList<HashMap<String, String>>	taskReportList;
   		String								isCompleted;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		analyzeJobKindArr	 	= null;
   		taskReportList			= new ArrayList<HashMap<String, String>>();
   		isCompleted				= "N";
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 분석작업종류별 중지.
   		
   		analyzeJobKindArr	 	= requestUtil.getParameterValues("ANALYZE_JOB_KIND");
   		if(analyzeJobKindArr != null) {
   			for(String analyzeJobKind : analyzeJobKindArr) {
   				Integer key = Integer.valueOf(analyzeJobKind);
   				String executorServiceId = AppAnalyzer.JOB_KIND_MAP.get(key);
   				HashMap<String, String> taskReportMap = new HashMap<String, String>();
   				TaskReport taskReport = TaskHandler.getInstance().getTaskReport(executorServiceId);
   				if( taskReport != null && taskReport.getRate() != null ) {
   					taskReportMap.put("taskId", executorServiceId);
   					taskReportMap.put("taskRate", "0.00");
   					taskReportList.add(taskReportMap);
   				}
   				TaskHandler.getInstance().removeExecutorService(executorServiceId);
   				QueueHandler.getInstance(QUEUE_HANDLER_ID).removeQueueById(executorServiceId);
   			}
   		}
   		
   		
   		/*
		Iterator<Integer> keys = AppAnalyzer.JOB_KIND_MAP.keySet().iterator();
		while(keys.hasNext()) {
			Integer key = keys.next();
			String executorServiceId = AppAnalyzer.JOB_KIND_MAP.get(key);
			HashMap<String, String> taskReportMap = new HashMap<String, String>();
			TaskReport taskReport = TaskHandler.getInstance().getTaskReport(executorServiceId);
			if( taskReport != null && taskReport.getRate() != null ) {
				taskReportMap.put("taskId", executorServiceId);
				taskReportMap.put("taskRate", "0.00");
				taskReportList.add(taskReportMap);
			}
			TaskHandler.getInstance().removeExecutorService(executorServiceId);
			QueueHandler.getInstance(QUEUE_HANDLER_ID).removeQueueById(executorServiceId);
		}
		*/
		
   		
		isCompleted				= "Y";
   		
   		// 3. 결과처리
   		mav.addObject("RETURN_CD", net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   		mav.addObject("isCompleted", isCompleted);
   		mav.addObject("returnObj", taskReportList);
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    }
    

    /** 
     * 분석작업 모니터링 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/doMonitoring.do") 
    public ModelAndView doMonitoring(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 						requestUtil;
   		//파라메터로 사용할 VO
   		ArrayList<HashMap<String, String>>	taskReportList;
   		String								isCompleted;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		taskReportList			= new ArrayList<HashMap<String, String>>();
   		isCompleted				= "N";
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 분석작업종류별 모니터링실행.
		int analyzeJobKindNum = 0;
		int analyzeJobKindCompletedNum = 0;
		
		Iterator<Integer> keys = AppAnalyzer.JOB_KIND_MAP.keySet().iterator();
		while(keys.hasNext()) {
			Integer key = keys.next();
			String executorServiceId = AppAnalyzer.JOB_KIND_MAP.get(key);
			HashMap<String, String> taskReportMap = new HashMap<String, String>();
			TaskReport taskReport = TaskHandler.getInstance().getTaskReport(executorServiceId);
			if( taskReport != null && taskReport.getRate() != null ) {
				taskReportMap.put("taskId", executorServiceId);
				taskReportMap.put("taskRate", taskReport.getRate().toPlainString());
				taskReportList.add(taskReportMap);
				if( taskReport.getRate().doubleValue() > 0.0 ) {
					analyzeJobKindNum++;
				}
				if( taskReport.getRate().intValue() == 100 ) {
					analyzeJobKindCompletedNum++;
				}
			}
		}
		
		if( analyzeJobKindNum == analyzeJobKindCompletedNum && QueueHandler.getInstance(QUEUE_HANDLER_ID).getQueueSize() == 0 ) {
   			isCompleted = "Y";
   		}

debug("isCompleted["+isCompleted+"]" + " analyzeJobKindNum["+analyzeJobKindNum+"]" + " analyzeJobKindCompletedNum["+analyzeJobKindCompletedNum+"]" + " getQueueSize["+QueueHandler.getInstance(QUEUE_HANDLER_ID).getQueueSize()+"]");

   		TaskHandler.getInstance().checkExecutorServiceAll();
   		
   		// 3. 결과처리
   		mav.addObject("RETURN_CD", net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   		mav.addObject("isCompleted", isCompleted);
   		mav.addObject("returnObj", taskReportList);
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    }
    
    /** 
     * 분석작업 DB저장 
     * @param request 
     * @param model 
     * @return 
     */ 
    @RequestMapping(value = "/saveToDB.do") 
    public ModelAndView saveToDB(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception {
   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.
   		/************************ 뷰생성 시작 ************************/
   		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
   		/************************ 뷰생성 끝 **************************/
   		
   		/************************ 변수 선언 시작 ************************/
   		RequestUtil 						requestUtil;
   		String                  			sysId;
   		String								isCompleted;
   		/************************ 변수 선언 끝 **************************/
   		
   		/************************ 변수 정의 시작 ************************/
   		requestUtil 			= new RequestUtil(request, response);
   		sysId					= null;
   		isCompleted				= "N";
   		/************************ 변수 정의 끝 ************************/
   		
   		/************************ 컨트롤러 로직 시작 ************************/
   		// 1. 파라메터 취합
   		sysId					= requestUtil.getParameter("SYS_ID");
   		// 2. 분석작업DB저장 
   		net.dstone.boot.analyzer.vo.SysVo paramVo = new net.dstone.boot.analyzer.vo.SysVo();
	   	paramVo.setSYS_ID(sysId);
	   	paramVo = configurationService.getSys(paramVo);
	   	if(paramVo != null) {
	   		AppAnalyzer.getInstance(paramVo.getCONF_FILE_PATH()).saveToDb();
	   		isCompleted = "Y";
	   	}
	   	
   		// 3. 결과처리
   		mav.addObject("RETURN_CD", net.dstone.common.biz.BaseController.RETURN_SUCCESS );
   		mav.addObject("isCompleted", isCompleted);
   		/************************ 컨트롤러 로직 끝 ************************/
   		return mav;
    }

} 
