package net.dstone.batch.common.runner;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.biz.BaseService;
import net.dstone.batch.common.core.BaseJobConfig;
import net.dstone.common.config.ConfigProperty;

/**
 * SpringBoot WebApplicaton 형식으로 기동하여 Rest Api로 호출되는 모든 요청을 처리한다.
 * <pre>
 * - URL 형식 : /batch/restapi/{jobName}
 * - 비동기로 Job을 처리.
 * </pre>
 */
@RestController
@RequestMapping("/batch")
public class RestApiRunner extends AbstractRunner{
	
	@Autowired
	ConfigurableApplicationContext context;

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	@Autowired
	BaseService baseService;

	/**
	 * healthCheck을 수행하는 메소드.
	 * @param params
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/healthCheck")
    public ResponseEntity<?> healthCheck(@RequestParam Map<String, Object> params, HttpServletRequest request) throws Exception {
        return ResponseEntity.ok(Map.of(
             "status", BatchStatus.STARTED
        ));
    }
	
	
	/**
	 * AutoRegJob 어노테이션들이 붙은 Job들 등록하는 메소드.
	 * @param params
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/registerJobs")
    public ResponseEntity<?> registerJobs(@RequestParam Map<String, Object> params, HttpServletRequest request){
		
		Map<String, Object> returnMap = new HashMap<String, Object>();
		
		/*** Job 등록 시작 ***/
		try {
			// @AutoRegisteredJob 애노테이션이 붙은 모든 빈 검색
			Map<String, Object> jobs = context.getBeansWithAnnotation(AutoRegJob.class);
			for(Object jobObj : jobs.values()) {
				if (jobObj instanceof BaseJobConfig) {
					String jobName = jobObj.getClass().getAnnotation(AutoRegJob.class).name();
					returnMap = baseService.registerJob(jobName);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		/*** Job 등록 끝 ***/
		
        return ResponseEntity.ok(Map.of(
        	"success", (String)returnMap.get(BaseService.SUCCESS_YN)
        ));
    }
	
	/** 
	 * jobName 에 해당하는 Job을 jobRegistry에 등록하는 메소드.
	 * @param jobName
	 * @param params
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/registerJob/{jobName}")
    public ResponseEntity<?> registerJob(@PathVariable String jobName, HttpServletRequest request){

        ResponseEntity<?> response = null;
        Map<String, Object> returnMap = new HashMap<String, Object>();
		
		try {
    		// jobRegistry에 저장
			returnMap = baseService.registerJob(jobName);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(returnMap.containsKey(BaseService.SUCCESS_YN) && returnMap.get(BaseService.SUCCESS_YN).equals("Y") ) {
	    		response = ResponseEntity.ok(Map.of(
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	    	            ));
			}else {
				response = ResponseEntity.ok(Map.of(
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN),
	    	            "message", (String)returnMap.get(BaseService.RETURN_MSG)
	    	            ));
			}
		}
        return response;
    }
	
	/** 
	 * jobRegistry에 등록된 Job Name 목록을 반환하는 메소드.
	 * @param jobName
	 * @param params
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping("/getJobs")
    public ResponseEntity<?> getJobs(HttpServletRequest request){

        ResponseEntity<?> response = null;
        Map<String, Object> returnMap = new HashMap<String, Object>();
		
		try {
    		// jobRegistry에 저장
			returnMap = baseService.getJobList();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(returnMap.containsKey(BaseService.SUCCESS_YN) && returnMap.get(BaseService.SUCCESS_YN).equals("Y") ) {
	    		response = ResponseEntity.ok(Map.of(
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN),
	    	            "jobList", (List<String>)returnMap.get(BaseService.JOB_LIST)
	    	            ));
			}else {
				response = ResponseEntity.ok(Map.of(
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN),
	    	            "message", (String)returnMap.get(BaseService.RETURN_MSG)
	    	            ));
			}
		}
        return response;
    }
	

	
	/** 
	 * jobName 에 해당하는 JOB을 (jobRegistry에 등록되지 않았을 경우 등록하고)실행하는 메소드.
	 * @param jobName
	 * @param params
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/startJob/{jobName}")
    public ResponseEntity<?> startJob(@PathVariable String jobName, @RequestParam Map<String, Object> params, HttpServletRequest request){

        ResponseEntity<?> response = null;
        Map<String, Object> returnMap = new HashMap<String, Object>();
        Job job = null;
        JobExecution execution = null;
		
        // 1. 트렌젝션ID 생성.
		String transactionId = baseService.newTransactionId();
		
		try {
    		// 2. Job Parameter 추출
			JobParameters jobParameters = getJobParams(this.parseParameterToMap(params));
    		// 3. 파라메터레지스트리 등록
    		jobConfigRegister(transactionId, jobParameters);
    		// 4. jobRegistry에 저장
    		if( !baseService.isRegisteredJob(jobName) ) {
    			returnMap = baseService.registerJob(jobName);
        		if( !returnMap.containsKey(BaseService.SUCCESS_YN) || !"Y".equals(returnMap.get(BaseService.SUCCESS_YN)) ) {
        			throw new Exception("["+jobName+"]을 jobRegistry에 등록하는데 실패했습니다. 상세내용:" + returnMap.get(BaseService.RETURN_MSG));
        		}
    		}
    		// 5. Job 조회
    		returnMap = baseService.getJob(jobName);
    		if( !returnMap.containsKey(BaseService.SUCCESS_YN) || !"Y".equals(returnMap.get(BaseService.SUCCESS_YN)) ) {
    			throw new Exception("["+jobName+"]을 조회하는데 실패했습니다. 상세내용:" + returnMap.get(BaseService.RETURN_MSG));
    		}
    		job = (Job)returnMap.get(BaseService.JOB);
    		// 6. Job 실행
    		returnMap = baseService.startJobExecution(transactionId, job, jobParameters, true);
    		if( !returnMap.containsKey(BaseService.SUCCESS_YN) || !"Y".equals(returnMap.get(BaseService.SUCCESS_YN)) ) {
    			throw new Exception("["+jobName+"]을 실행하는데 실패했습니다. 상세내용:" + returnMap.get(BaseService.RETURN_MSG));
    		}
    		execution = (JobExecution)returnMap.get(BaseService.JOB_EXCUTION);
    		
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// 6. 파라메터레지스트리 삭제
			jobConfigUnRegister(transactionId);
			
			if(returnMap.containsKey(BaseService.SUCCESS_YN) && returnMap.get(BaseService.SUCCESS_YN).equals("Y") ) {
	    		response = ResponseEntity.ok(Map.of(
	    				"jobInstanceId", (execution==null?"":execution.getJobId()),
	    		        "jobExecutionId", (execution==null?"":execution.getId()),
	    	            "status", BatchStatus.STARTED,
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	    	            ));
			}else {
				response = ResponseEntity.ok(Map.of(
	    				"jobInstanceId", (execution==null?"":execution.getJobId()),
	    		        "jobExecutionId", (execution==null?"":execution.getId()),
	    	            "status", BatchStatus.FAILED,
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN),
	    	            "message", (String)returnMap.get(BaseService.RETURN_MSG)
	    	            ));
			}
		}
        return response;
    }
	
	
    /**
     * jobExecutionId 에 해당하는 JobExecution 을 중지하는 메소드
     * @param jobExecutionId
     * @return
     */
	@RequestMapping("/stopJob/{jobExecutionId}")
    public ResponseEntity<?> stopJob(@PathVariable Long jobExecutionId) {

        ResponseEntity<?> response = null;
        Map<String, Object> returnMap = new HashMap<String, Object>();
		JobExecution execution = null;
		
		try {
			returnMap = baseService.stopJobExecution(jobExecutionId);
			execution = (JobExecution)returnMap.get(BaseService.JOB_EXCUTION);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(returnMap.containsKey(BaseService.SUCCESS_YN) && returnMap.get(BaseService.SUCCESS_YN).equals("Y") ) {
	    		response = ResponseEntity.ok(Map.of(
		                "jobExecutionId", jobExecutionId,
		                "status", execution == null ? "" : execution.getStatus(),
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN),
		                "message", "중지 신호를 보냈습니다. Job이 현재 처리 중인 청크 완료 후 중지됩니다."
	    		        ));
			}else {
				response = ResponseEntity.ok(Map.of(
			        	"jobExecutionId", execution == null ? "" : execution.getId(),
			        	"status", BatchStatus.FAILED,
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN),
		                "message", (String)returnMap.get(BaseService.RETURN_MSG)
	    	            ));
			}
		}
		return response; 
    }
	
    /**
     * jobExecutionId 에 해당하는 JobExecution 실행상태를 조회하는 메소드
     * @param jobExecutionId
     * @return
     */
	@RequestMapping("/statusJob/{jobExecutionId}")
    public ResponseEntity<?> statusJob(@PathVariable Long jobExecutionId) {

        ResponseEntity<?> response = null;
        Map<String, Object> returnMap = new HashMap<String, Object>();
        JobInstance instance = null;
        JobExecution execution = null;
        String jobName = "";
        try {
        	returnMap = baseService.getJobExecution(jobExecutionId);
        	execution = (JobExecution)returnMap.get(BaseService.JOB_EXCUTION);
        	if( execution != null ) {
            	instance = execution.getJobInstance();
            	jobName = instance.getJobName();
        	}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(returnMap.containsKey(BaseService.SUCCESS_YN) && returnMap.get(BaseService.SUCCESS_YN).equals("Y") ) {
	            if( execution.isRunning() ) {
	            	response = ResponseEntity.ok(Map.of(
	                        "jobName", jobName,
	                        "status", execution.getStatus().toString(),
	                        "startTime", execution.getStartTime(),
		    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	                ));
	            }else {
	            	response = ResponseEntity.ok(Map.of(
	                        "jobName", jobName,
	                        "status", execution.getStatus().toString(),
	                        "exitStatus", execution.getExitStatus().toString(),
	                        "startTime", execution.getStartTime(),
	                        "endTime", execution.getEndTime(),
		    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	                ));
	            }
			}else {
				if( execution != null ) {
					response = ResponseEntity.ok(Map.of(
				        	"jobExecutionId", execution.getId(),
				        	"status", BatchStatus.FAILED,
			                "message", (String)returnMap.get(BaseService.RETURN_MSG),
		    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
		    	            ));
				}else {
					response = ResponseEntity.notFound().build();
				}
			}
		}
		return response; 
    }
    
    /**
     * jobExecutionId 에 해당하는 JobExecution 을 재시작하는 메소드
     * @param jobExecutionId
     * @return
     */
	@RequestMapping("/restartJob/{jobExecutionId}")
    public ResponseEntity<?> restartJob(@PathVariable Long jobExecutionId) {

    	ResponseEntity<?> response = null;
        Map<String, Object> returnMap = new HashMap<String, Object>();
		
		try {
			returnMap = baseService.restartJobExecution(jobExecutionId);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(returnMap.containsKey(BaseService.SUCCESS_YN) && returnMap.get(BaseService.SUCCESS_YN).equals("Y") ) {
	    		response = ResponseEntity.ok(Map.of(
						"jobExecutionId", returnMap.get(BaseService.JOB_EXCUTION_ID),
						"newJobExecutionId", returnMap.get(BaseService.NEW_JOB_EXCUTION_ID),
						"message", (String)returnMap.get(BaseService.RETURN_MSG),
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	    		        ));
			}else {
				response = ResponseEntity.ok(Map.of(
						"jobExecutionId", returnMap.get(BaseService.JOB_EXCUTION_ID),
						"message", (String)returnMap.get(BaseService.RETURN_MSG),
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	    	            ));
			}
		}
		return response; 
    }
    
    /**
     * jobExecutionId 에 해당하는 JobExecution 을 삭제(Abandon)하는 메소드.
     * @param jobExecutionId
     * @return
     */
	@RequestMapping("/abandonJob/{jobExecutionId}")
    public ResponseEntity<?> abandonJob(@PathVariable Long jobExecutionId) {

    	ResponseEntity<?> response = null;
        Map<String, Object> returnMap = new HashMap<String, Object>();
		
		try {
			returnMap = baseService.abandonJobExecution(jobExecutionId);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(returnMap.containsKey(BaseService.SUCCESS_YN) && returnMap.get(BaseService.SUCCESS_YN).equals("Y") ) {
	    		response = ResponseEntity.ok(Map.of(
						"jobExecutionId", returnMap.get(BaseService.JOB_EXCUTION_ID),
						"message", (String)returnMap.get(BaseService.RETURN_MSG),
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	    		        ));
			}else {
				response = ResponseEntity.ok(Map.of(
						"jobExecutionId", returnMap.get(BaseService.JOB_EXCUTION_ID),
						"message", (String)returnMap.get(BaseService.RETURN_MSG),
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	    	            ));
			}
		}
		return response; 
    }
	
	/** 
	 * jobName 에 해당하는 JOB을 jobRegistry에서 삭제하는 메소드.
	 * @param jobName
	 * @param params
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/unregisterJob/{jobName}")
    public ResponseEntity<?> unregisterJob(@PathVariable String jobName, HttpServletRequest request){

        ResponseEntity<?> response = null;
        Map<String, Object> returnMap = new HashMap<String, Object>();
		
		try {
    		// jobRegistry에 저장
			returnMap = baseService.unregisterJob(jobName);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(returnMap.containsKey(BaseService.SUCCESS_YN) && returnMap.get(BaseService.SUCCESS_YN).equals("Y") ) {
	    		response = ResponseEntity.ok(Map.of(
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	    	            ));
			}else {
				response = ResponseEntity.ok(Map.of(
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN),
	    	            "message", (String)returnMap.get(BaseService.RETURN_MSG)
	    	            ));
			}
		}
        return response;
    }

    /**
     * jobExecutionId 에 해당하는 JobExecution을 삭제(물리적으로 DB삭제)하는 메소드
     * @param jobExecutionId
     * @return
     */
	@RequestMapping("/deleteJob/{jobExecutionId}")
    public ResponseEntity<?> deleteJob(@PathVariable Long jobExecutionId) {

    	ResponseEntity<?> response = null;
        Map<String, Object> returnMap = new HashMap<String, Object>();
		
		try {
			returnMap = baseService.deleteJobExecution(jobExecutionId);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(returnMap.containsKey(BaseService.SUCCESS_YN) && returnMap.get(BaseService.SUCCESS_YN).equals("Y") ) {
	    		response = ResponseEntity.ok(Map.of(
						"jobExecutionId", returnMap.get(BaseService.JOB_EXCUTION_ID),
						"message", (String)returnMap.get(BaseService.RETURN_MSG),
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	    		        ));
			}else {
				response = ResponseEntity.ok(Map.of(
						"jobExecutionId", returnMap.get(BaseService.JOB_EXCUTION_ID),
						"message", (String)returnMap.get(BaseService.RETURN_MSG),
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	    	            ));
			}
		}
		return response; 
    }
    
    /**
     * jobInstanceId 에 해당하는 JobInstance를 삭제(물리적으로 DB삭제)하는 메소드
	 * <pre>
	 * JobInstance와 연결된 모든 JobExecution을 DB에서 완전히 제거 (복구 불가)
	 * 삭제 후 동일 파라미터로 새 JobInstance 생성 가능 (초기화 효과)
	 * 
	 * 삭제 흐름:
	 *   1. 각 JobExecution의 StepExecution, Context 삭제
	 *   2. JobExecution 삭제
	 *   3. JobInstance 삭제
	 * </pre>
     * @param jobInstanceId
     * @return
     */
	@RequestMapping("/deleteJobInstance/{jobInstanceId}")
    public ResponseEntity<?> deleteJobInstance(@PathVariable Long jobInstanceId) {

    	ResponseEntity<?> response = null;
        Map<String, Object> returnMap = new HashMap<String, Object>();
		
		try {
			returnMap = baseService.deleteJobInstance(jobInstanceId);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(returnMap.containsKey(BaseService.SUCCESS_YN) && returnMap.get(BaseService.SUCCESS_YN).equals("Y") ) {
	    		response = ResponseEntity.ok(Map.of(
						"jobInstanceId", returnMap.get(BaseService.JOB_INSTANCE_ID),
						"message", (String)returnMap.get(BaseService.RETURN_MSG),
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	    		        ));
			}else {
				response = ResponseEntity.ok(Map.of(
						"jobInstanceId", returnMap.get(BaseService.JOB_INSTANCE_ID),
						"message", (String)returnMap.get(BaseService.RETURN_MSG),
	    	            "success", (String)returnMap.get(BaseService.SUCCESS_YN)
	    	            ));
			}
		}
		return response; 
    }
    
    private Map<String,Object> parseParameterToMap(Map<String,Object> params) {
    	try {
    		// Job파라메터 등록
			JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
			jobParametersBuilder.addString("timestamp", String.valueOf(System.currentTimeMillis()));
            if( params != null ) {
            	Iterator<String> paramKeys = params.keySet().iterator();
            	while( paramKeys.hasNext() ) {
            		String paramKey = paramKeys.next();
            		Object paramVal = params.get(paramKey);
            		if(paramVal != null) {
            			jobParametersBuilder.addString(paramKey, paramVal.toString());
            		}
            	}
            }
    	}catch(Exception e) {
			e.printStackTrace();
		}
    	return params;
    }
}
