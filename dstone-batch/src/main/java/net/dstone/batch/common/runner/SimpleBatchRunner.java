package net.dstone.batch.common.runner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import net.dstone.batch.common.DstoneBatchApplication;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

/**
 * 독립 Application 으로 기동하여 단건 Job을 처리한다.
 * <pre>
 * - 동기로 Job을 처리.
 * </pre>
 */
public class SimpleBatchRunner extends AbstractRunner {
	
    public static void main(String[] args) throws Exception {
    	int exitCode = 0;
    	ConfigurableApplicationContext context = null;
    	try {
    		net.dstone.batch.common.DstoneBatchApplication.setSysProperties();
    		context = launch(context, exitCode, args);
		} catch (Exception e) {
			exitCode = -1;
			e.printStackTrace();
		} finally {
 			if (context != null) {
 				context.close();
 			}
 			System.exit(exitCode);
 		}
    }
    
    public static ConfigurableApplicationContext launch(ConfigurableApplicationContext context, int exitCode, String[] args) throws Exception {
    	LogUtil.sysout( SimpleBatchRunner.class.getName() + ".launch("+Arrays.toString(args)+") has been called !!!");
    	exitCode = 0;
        String jobName = "";
        String[] jobParams = new String[0];
        JobExecution execution = null;
        Job job = null;

        // 1. 트렌젝션ID 생성.
		String transactionId = newTransactionId();
    	try {
            // 2.ApplicationContext 생성.
    		if(context == null) {
                context = new SpringApplicationBuilder(DstoneBatchApplication.class).web(WebApplicationType.NONE).run(jobParams);
    		}
    		// 3. Job Name 추출
    		jobName = parseJobName(args);
    		// 4. Job Parameter 추출
    		JobParameters jobParameters = getJobParams( parseParameterToMap(args) );
    		// 5. 파라메터레지스트리 등록
    		jobConfigRegister(transactionId, jobParameters);
    		// 6. jobRegistry에 저장
    		jobRegister(context, transactionId, jobName, jobParameters);
    		// 7. Job 조회
    		job = getJob(context, jobName, jobParameters);
    		// 8. Job 실행
    		execution = jobLaunch(context, transactionId, job, jobParameters);
		} catch (Throwable e) {
			exitCode = -1;
			e.printStackTrace();
		} finally {
			// 8. 파라메터레지스트리 삭제
			jobConfigUnRegister(transactionId);
		}
    	return context;
    }
    
    protected static String parseJobName(String[] args) throws Exception{
    	String jobName = "";
    	try {
            if (args == null || args.length < 1) {
                throw new Exception("Job name must be provided as the first argument.");
            }
            String firstArg = args[0];
            if( firstArg.indexOf("spring.batch.job.name=") > -1 ||  firstArg.indexOf("spring.batch.job.names=") > -1 ) {
            	String[] words = StringUtil.toStrArray(firstArg, "=");
            	if(words.length > 1) {
            		jobName = words[1];
            	}
            }
            if( StringUtil.isEmpty(jobName) ) {
            	throw new Exception("Job name must be provided as the first argument.");
            }
    	}catch(Exception e) {
			throw e;
		}
    	return jobName;
    }
    
    protected static Map<String,Object> parseParameterToMap(String[] args) {
    	Map<String,Object> paramMap = new HashMap<String,Object>();
    	try {
            if ( args != null && args.length > 0) {
                for (int i = 0; i < args.length; i++) {
                    String arg = args[i];
                    if (arg.indexOf("=") == -1) {
                        throw new Exception("Parameters must be in KEY=VALUE format.");
                    }
                    String[] batchParams = StringUtil.toStrArray(arg, "=", true);
                    if (batchParams.length == 0) {
                        throw new Exception("Parameters must be in KEY=VALUE format.");
                    }
                    String key = batchParams[0];
                    String val = "";
                    if (batchParams.length > 1) {
                        val = batchParams[1];
                    }
                    paramMap.put(key, val);
                }
            }
    	}catch(Exception e) {
			e.printStackTrace();
		}
    	return paramMap;
    }
}
