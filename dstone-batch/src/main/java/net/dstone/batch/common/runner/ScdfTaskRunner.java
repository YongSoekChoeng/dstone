package net.dstone.batch.common.runner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import net.dstone.batch.common.DstoneBatchApplication;
import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.core.BaseJobConfig;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.ConvertUtil;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * Spring Cloud Data Flow 에서 호출하는 경우에 대한 조치.
 */
@Component
public class ScdfTaskRunner extends AbstractRunner implements ApplicationRunner {

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	/**
	 * SCDF에서 호출하는 Job요청을 처리하는 메소드
	 */
	@Override
	public void run(ApplicationArguments args) throws Exception {
		String jobName = configProperty.getProperty("spring.batch.job.names");
		LogUtil.sysout( this.getClass().getName() + "run(jobName["+jobName+"]) has been called !!!" );
        if( !StringUtil.isEmpty(jobName) ) {
        	ArrayList<String> listArgs = new ArrayList<String>();
    		listArgs.add( "spring.batch.job.names=" + jobName);
        	String[] strArgs = null;
        	if( args.getSourceArgs() != null ) {
                for (String arg : args.getSourceArgs()) {
                	listArgs.add(arg);
                }
        	}
        	strArgs = new String[listArgs.size()];
        	listArgs.toArray(strArgs);
        	listArgs.clear();
        	listArgs = null;
        	this.launch(strArgs);
        }
	}

	/**
	 * 배치작업을 실행하는 메소드
	 */
	private void launch(String[] jobParams) {
        String jobName = "";
        JobExecution execution = null;
        Job job = null;
        // 1. 트렌젝션ID 생성.
		String transactionId = newTransactionId();
		try {
    		// 2. Job Name 추출
    		jobName = SimpleBatchRunner.parseJobName(jobParams);
    		// 3. Job Parameter 추출
    		JobParameters jobParameters = getJobParams( SimpleBatchRunner.parseParameterToMap(jobParams) );
    		// 4. 파라메터레지스트리 등록
    		jobConfigRegister(transactionId, jobParameters);
    		// 5. jobRegistry에 저장
    		jobRegister(context, transactionId, jobName, jobParameters);
    		// 6. Job 조회
    		job = getJob(context, jobName, jobParameters);
    		// 7. Job 실행
    		execution = jobLaunch(context, transactionId, job, jobParameters);
    		LogUtil.sysout( "JobName["+jobName+"] 작업결과:" + execution.getExitStatus() );
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// 8. 파라메터레지스트리 삭제
			jobConfigUnRegister(transactionId);
		}
	}

	public static void main(String[] args) {
		try {
			/*** SCDF에 Job 의 Task를 등록 시작 ***/
			net.dstone.batch.common.DstoneBatchApplication.setSysProperties();
			ScdfTaskRunner.registerJosToScdf();
			/*** SCDF에 Job 의 Task를 등록 끝 ***/
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * SCDF에 Job 의 Task를 등록하기 위해 SpringApplication 을 실행하는 메소드.
	 */
	public static void registerJosToScdf() {
		try {

		    SpringApplicationBuilder springApplicationBuilder = new SpringApplicationBuilder(DstoneBatchApplication.class);
		    StringBuffer msg = new StringBuffer();
		    String appConfDir = System.getProperty("APP_CONF_DIR");
		    Map<String,Object> prop = new HashMap<String,Object>();
		    prop.put("spring.config.location", appConfDir + "/application.yml" );
		    prop.put("logging.config", appConfDir + "/log4j2.xml" );
		    
		    msg.append("/******************************* 설정파일 로딩 시작 *********************************/").append("\n");
		    msg.append( ConvertUtil.convertToJson(prop) ).append("\n");
		    msg.append("/******************************* 설정파일 로딩 끝 *********************************/").append("\n");
		    LogUtil.sysout(msg);

		    springApplicationBuilder.properties(prop);
		    
			String[] args = new String[0];
			ConfigurableApplicationContext context = springApplicationBuilder.web(WebApplicationType.NONE).run(args);
			registerAllJobToDataflow(context);
		} catch (Exception e) {
			//e.printStackTrace();
		}
	}
	
	/**
	 * SCDF에 Job 의 Task를 등록하는 메소드
	 */
	public static void registerAllJobToDataflow(ConfigurableApplicationContext context) {
		RestTemplate restTemplate = null;
		try {
			ConfigProperty configProperty = (ConfigProperty)context.getBean("configProperty");
			// @AutoRegisteredJob 애노테이션이 붙은 모든 빈 검색
			Map<String, Object> jobs = context.getBeansWithAnnotation(AutoRegJob.class);
			for(Object jobObj : jobs.values()) {
				if (jobObj instanceof BaseJobConfig) {
					BaseJobConfig abstractJob = (BaseJobConfig)jobObj;
					String jobName = jobObj.getClass().getAnnotation(AutoRegJob.class).name();
					String taskDefUrl = configProperty.getProperty("spring.cloud.dataflow.client.server-uri") + "/tasks/definitions/" + jobName;
					if( !isExistsInDataflow(jobName, taskDefUrl) ) {
						restTemplate = net.dstone.common.utils.RestFulUtil.getInstance().getRestTemplate();
			            // SCDF에 Task 정의 생성
						String taskRegUrl = configProperty.getProperty("spring.cloud.dataflow.client.server-uri") + "/tasks/definitions";
			            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
			            params.add("name", jobName);
			            params.add("definition", configProperty.getProperty("spring.application.name") + " --spring.batch.job.names=" + jobName);
			            ResponseEntity<String> response = null;
			            try {
			            	response = restTemplate.postForEntity(taskRegUrl, params, String.class);
			                LogUtil.sysout("Registered job: " + jobName + ", StatusCode:" + response.getStatusCode());
			            } catch (Exception e) {
			            	e.printStackTrace();
			            	LogUtil.sysout("Failed to register job: " + jobName + ", StatusCode:" + (response==null?"":response.getStatusCode()));
			            }
					}

				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * SCDF에 Job 의 Task가 존재하는지 확인하는 메소드
	 */
	private static boolean isExistsInDataflow(String jobName, String taskDefUrl) {
		boolean isExists = false;
		RestTemplate restTemplate = null;
		try {
			restTemplate = net.dstone.common.utils.RestFulUtil.getInstance().getRestTemplate();
            try {
            	ResponseEntity<String> response = restTemplate.getForEntity(taskDefUrl, String.class);
            	int statusCode = response.getStatusCode().value();
            	if (statusCode == 404) {
            		isExists = false;
            	}else {
            		isExists = true;
            	}
            	LogUtil.sysout("JobName["+jobName+"] isExists ====>>>" + isExists);
            } catch (Exception e) {
            	isExists = false;
            	//e.printStackTrace();
            }
		} catch (Exception e) {
			isExists = false;
			//e.printStackTrace();
		}
		return isExists;
	}
	

}
