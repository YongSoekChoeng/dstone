package net.dstone.batch.common.runner;

import java.util.Iterator;
import java.util.Map;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
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
public class RestApiRunner extends AbstractRunner {
	
	@Autowired
	ConfigurableApplicationContext context;

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

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
	 * AutoRegJob 어노테이션들이 붙은 Job들을 SCDF에 등록하는 메소드.
	 * @param params
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/regtasks")
    public ResponseEntity<?> regtasks(@RequestParam Map<String, Object> params, HttpServletRequest request) throws Exception {
		
		/*** SCDF에 Job 의 Task를 등록 시작 ***/
		net.dstone.batch.common.DstoneBatchApplication.setSysProperties();
		ScdfTaskRunner.registerAllJobToDataflow(context);
		/*** SCDF에 Job 의 Task를 등록 끝 ***/
		
        return ResponseEntity.ok(Map.of(
             "status", BatchStatus.STARTED
        ));
    }
	
	
	/** 
	 * jobName 에 해당하는 JOB을 호출하는 메소드.
	 * @param jobName
	 * @param params
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/restapi/{jobName}")
    public ResponseEntity<?> runJob(@PathVariable String jobName, @RequestParam Map<String, Object> params, HttpServletRequest request) throws Exception {
        JobExecution execution = null;
        Job job = null;
		
        // 1. 트렌젝션ID 생성.
		String transactionId = newTransactionId();
		
		try {
    		// 2. Job Parameter 추출
			JobParameters jobParameters = getJobParams(this.parseParameterToMap(params));
    		// 3. 파라메터레지스트리 등록
    		jobConfigRegister(transactionId, jobParameters);
    		// 4. jobRegistry에 저장
    		jobRegister(context, transactionId, jobName, jobParameters);
    		// 5. Job 조회
    		job = getJob(context, jobName, jobParameters);
    		// 6. Job 실행
    		execution = startAsyncJob(context, transactionId, job, jobParameters);
		} catch (Exception e) {
			e.printStackTrace();
	        return ResponseEntity.ok(Map.of(
	        	"jobInstanceId", execution.getJobId(),
	        	"jobExecutionId", execution.getId(),
	        	"status", BatchStatus.FAILED
	        ));
		} finally {
			// 6. 파라메터레지스트리 삭제
			jobConfigUnRegister(transactionId);
		}
        return ResponseEntity.ok(Map.of(
	        "jobInstanceId", execution.getJobId(),
	        "jobExecutionId", execution.getId(),
             "status", BatchStatus.STARTED
        ));
    }
	
    /**
     * jobExecutionId 에 해당하는 Job실행상태를 조회하는 메소드
     * @param jobExecutionId
     * @return
     */
    @GetMapping("/status/{jobExecutionId}")
    public ResponseEntity<?> status(@PathVariable Long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        if( execution.isRunning() ) {
            return ResponseEntity.ok(Map.of(
                    "status", execution.getStatus(),
                    "startTime", execution.getStartTime()
            ));
        }else {
            return ResponseEntity.ok(Map.of(
                    "status", execution.getStatus(),
                    "exitStatus", execution.getExitStatus(),
                    "startTime", execution.getStartTime(),
                    "endTime", execution.getEndTime()
                    //,"stepExecutions", execution.getStepExecutions()
            ));
        }
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
