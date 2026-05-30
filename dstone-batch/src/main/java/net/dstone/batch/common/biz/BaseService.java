package net.dstone.batch.common.biz;

import java.util.ArrayList;
import java.util.Collection;
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
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.ReferenceJobFactory;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import net.dstone.batch.common.annotation.AutoRegJob;
import net.dstone.batch.common.core.BaseBatchObject;
import net.dstone.batch.common.core.BaseJobConfig;
import net.dstone.common.utils.GuidUtil;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;


/**
 * Spring Batch Service를 담당하는 클래스
 * 
 * <pre>
 * 1. 구조 개념
 *   1-0. 설계도(Job) → 실체화(JobInstance) → 실행 기록(JobExecution)
 *   
 *   1-1. Job (작업)
 *     개념: 전체 배치 공정을 정의한 설계도 또는 최상위 개념입니다.
 *     특징: 어떤 Step들로 구성되는지, 순서는 어떠한지 등의 구성을 담고 있습니다. 실행 가능한 단위이며, 여러 개의 Step을 포함하는 컨테이너 역할을 합니다.
 *     
 *   1-2. JobInstance (작업 인스턴스)
 *     개념: Job이 실행될 때 논리적인 실행 단위입니다.
 *     특징: Job + JobParameters의 조합으로 결정됩니다. 
 *     중복실행방지: Spring Batch는 동일한 Job에 대해 동일한 JobParameter를 가진 JobInstance가 이미 'COMPLETED' 상태라면 다시 실행할 수 없도록 제한합니다.
 *     
 *   1-3. JobExecution (작업 실행)
 *     개념: JobInstance를 실행하려는 **실제 시도(Attempt)**를 의미합니다.
 *     특징: 한 번의 JobInstance는 여러 번의 JobExecution을 가질 수 있습니다. 예를 들어, '2023-10-27'자 JobInstance가 실행 도중 FAILED 되었다면, 문제를 수정 후 다시 실행할 수 있습니다.
 *          이때 두 번째 실행은 동일한 JobInstance에 대한 새로운 JobExecution이 됩니다.
 *     
 * </pre>
 */
@Service 
public class BaseService extends BaseBatchObject {

	@Autowired
	private ApplicationContext context;

	@Autowired
	@Qualifier("asyncJobLauncher")
	protected JobLauncher asyncJobLauncher;

	@Autowired
	@Qualifier("jobLauncher")
	protected JobLauncher jobLauncher;

	@Autowired
	@Qualifier("jobOperator")
	protected JobOperator jobOperator;

	@Autowired
	protected JobRegistry jobRegistry;

	@Autowired
	protected JobExplorer jobExplorer;

	@Autowired
	protected JobRepository jobRepository;

	private GuidUtil guidUtil = new GuidUtil();
	
	public static String SUCCESS_YN 			= "SUCCESS_YN";
	public static String RETURN_MSG 			= "RETURN_MSG";
	public static String JOB 					= "JOB";
	public static String JOB_LIST 				= "JOB_LIST";
	public static String JOB_INSTANCE 			= "JOB_INSTANCE";
	public static String JOB_INSTANCE_ID 		= "JOB_INSTANCE_ID";
	public static String JOB_EXCUTION 			= "JOB_EXCUTION";
	public static String JOB_EXCUTION_ID 		= "JOB_EXCUTION_ID";
	public static String NEW_JOB_EXCUTION_ID 	= "NEW_JOB_EXCUTION_ID";
	public static String JOB_EXCUTION_LIST 		= "JOB_EXCUTION_LIST";
	
	/**
	 * @return
	 */
	public String newTransactionId() {
		return guidUtil.getNewGuid();
	}

	/**
	 * Map에서 Job Parameter 추출
	 * @param jobParams
	 * @return
	 * @throws Exception
	 */
	protected JobParameters getJobParams(Map<String, Object> jobParams) throws Exception {
		JobParameters jobParameters = new JobParameters();
		try {
			// jobParameter 조립
			JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
			jobParametersBuilder.addString("timestamp", String.valueOf(System.currentTimeMillis()));
            if( jobParams != null ) {
            	Iterator<String> paramKeys = jobParams.keySet().iterator();
            	while( paramKeys.hasNext() ) {
            		String paramKey = paramKeys.next();
            		Object paramVal = jobParams.get(paramKey);
            		if(paramVal != null) {
            			jobParametersBuilder.addString(paramKey, paramVal.toString());
            		}
            	}
            }
            jobParameters = jobParametersBuilder.toJobParameters();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jobParameters;
	}
	
	/**
	 * AutoRegJob 을 JobRegistry에 등록하는 메소드.
	 * @param jobParams
	 * @return
	 * @throws Exception
	 */
	private boolean registerAutoRegJob(String jobName) throws Exception {
		LogUtil.sysout( this.getClass().getName() + ".registerAutoRegJob( "+jobName+") has been called !!!");
		boolean isSucceded = true;
		try {
			if( !this.isRegisteredJob(jobName) ) {
				// @AutoRegisteredJob 애노테이션이 붙은 모든 빈 검색
				Map<String, Object> jobs = context.getBeansWithAnnotation(AutoRegJob.class);
				for(Object jobObj : jobs.values()) {
					if (jobObj instanceof BaseJobConfig) {
						BaseJobConfig abstractJob = (BaseJobConfig)jobObj;
						String autoRegJobName = jobObj.getClass().getAnnotation(AutoRegJob.class).name();
						if( autoRegJobName.equals(jobName) ) {
							abstractJob.setName(jobName);
							Job job = abstractJob.buildAutoRegJob();
							ReferenceJobFactory factory = new ReferenceJobFactory(job);
							this.info("jobName["+jobName+"] job["+job+"] is registered !!!");
							jobRegistry.register(factory);
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			isSucceded = false;
		}
		return isSucceded;
	}
	

	/**
	 * jobName 으로 Job을 JobRegistry(인메모리 레지스트리)에 등록 하는 메소드.
	 * <pre>
	 * JobExecution이 DB에 물리적으로 등록되는 건 JobLauncherObj.run() 호출 시점입니다.
	 * JobRegistry(인메모리 레지스트리)는 JobOperator가 Job을 이름으로 찾기 위해 사용하는 레지스트리입니다.
	 * JobRegistryBeanPostProcessor가 애플리케이션 기동 시 모든 Job 빈을 자동 등록하지만,
	 * 동적으로 생성된 Job이나 등록 해제된 Job을 수동으로 다시 등록할 때 이 API를 사용합니다.
	 * 
	 * 등록 흐름:
	 * 1. ApplicationContext에서 jobName으로 Job 빈 조회
	 * 2. ReferenceJobFactory로 감싸서 JobRegistry(인메모리 레지스트리)에 등록
	 * 3. JobLauncherObj.run()이 호출되어 실제 배치가 시작될 때 DB(BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION 테이블)에 행(Row)이 삽입.
	 * </pre>
	 * @param transactionId
	 * @param jobName
	 * @throws Exception
	 */
	public Map<String, Object> registerJob(String jobName) {
		LogUtil.sysout( this.getClass().getName() + ".registerJob( "+jobName+") has been called !!!");
		
		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		String msg = "";
		
		try {
			// 1. jobName 체크
			if( StringUtil.isEmpty(jobName) ) {
				msg = "JobName["+jobName+"] is not supposed to be empty!";
				throw new Exception(msg);
			}
	        // 2. 이미 등록된 경우 확인
	        if( this.isRegisteredJob(jobName) ) {
	        	msg = "JobName["+jobName+"]은 이미 JobRegistry에 등록된 Job입니다!";
	        	throw new Exception(msg);
	        }
	        // 3. JobRegistry에 등록
	        if( !this.registerAutoRegJob(jobName) ) {
				msg = "JobName["+jobName+"]으로 JobRegistry에 등록할 수 없습니다!";
				throw new Exception(msg);
	        }
    		returnMap.put(BaseService.SUCCESS_YN, "Y");
		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	
	/**
	 * jobName 으로 Job을 JobRegistry(인메모리 레지스트리)에서 삭제 하는 메소드.
	 * <pre>
	 * JobRegistry(인메모리 레지스트리)에서만 제거되며 DB정보 자체는 유지됨.
	 * 해제 후 JobOperator.start(jobName) 호출 시 NoSuchJobException 발생.
	 * 
	 * </pre>
	 * @param jobName
	 * @throws Exception
	 */
	public Map<String, Object> unregisterJob(String jobName) {
		LogUtil.sysout( this.getClass().getName() + ".unregisterJob("+jobName+") has been called !!!");
		
		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		String msg = "";
		
		try {
			// 1. jobName 체크
			if( StringUtil.isEmpty(jobName) ) {
				msg = "JobName["+jobName+"] is not supposed to be empty!";
				throw new Exception(msg);
			}
	        // 2. 이미 등록된 경우 확인
	        if( !this.isRegisteredJob(jobName) ) {
	        	msg = "JobName["+jobName+"]은 존재하지 않는 Job입니다!";
	        	throw new Exception(msg);
	        }
	        // 3. JobRegistry에서 삭제
	        try {
	        	jobRegistry.unregister(jobName);
			} catch (Exception e) {
				e.printStackTrace();
				msg = "JobName["+jobName+"]으로 JobRegistry에서 삭제할 수 없습니다!";
				throw new Exception(msg);
	        }
    		returnMap.put(BaseService.SUCCESS_YN, "Y");
		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	
	/**
	 * jobName 으로 Job이 JobRegistry(인메모리 레지스트리)에 등록되어있는지 확인 하는 메소드.
	 * @param jobName
	 * @throws Exception
	 */
	public boolean isRegisteredJob(String jobName) {
		boolean isRegisteredJob = false;
		isRegisteredJob = jobRegistry.getJobNames().contains(jobName);
		LogUtil.sysout( this.getClass().getName() + ".isRegisteredJob( "+jobName+") has been called !!!" +  "==>> isRegisteredJob:"+isRegisteredJob );
		return isRegisteredJob;
	}
	
	/**
	 * JobRegistry(인메모리 레지스트리)에 저장 된 Job 반환.
	 * @param jobName
	 * @throws Exception
	 */
	public Map<String, Object> getJob(String jobName){
		LogUtil.sysout( this.getClass().getName() + ".getJob( "+jobName+") has been called !!!");

		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		returnMap.put(BaseService.JOB, "");
		
		Job job = null;
		String msg = "";
		
		try {
			// 1. jobName 체크
			if( StringUtil.isEmpty(jobName) ) {
				msg = "JobName["+jobName+"] is not supposed to be empty!";
				throw new Exception(msg);
			}
	        // 2. 이미 등록된 경우 확인
	        if( !this.isRegisteredJob(jobName) ) {
	        	msg = "JobName["+jobName+"]은 존재하지 않는 Job입니다!";
	        	throw new Exception(msg);
	        }
            job = jobRegistry.getJob(jobName);
    		returnMap.put(BaseService.JOB, job);
    		returnMap.put(BaseService.SUCCESS_YN, "Y");
		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}

	/**
	 * JobRegistry(인메모리 레지스트리)에 저장 된 Job 목록 반환.
	 * @throws Exception
	 */
	public Map<String, Object> getJobList(){
		LogUtil.sysout( this.getClass().getName() + ".getJobNames() has been called !!!");

		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		returnMap.put(BaseService.JOB_LIST, null);
		
		List<String> jobList = new ArrayList<String>();
		String msg = "";
		
		try {
			Collection<String> jobCollection = jobRegistry.getJobNames();
			if( jobCollection != null ) {
				for(String jobName : jobCollection) {
					jobList.add(jobName);
				}
			}
    		returnMap.put(BaseService.JOB_LIST, jobList);
    		returnMap.put(BaseService.SUCCESS_YN, "Y");
		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	
	/**
	 * JobInstance 반환.
	 * @param jobInstanceId
	 * @throws Exception
	 */
	public Map<String, Object> getJobInstance(Long jobInstanceId){
		LogUtil.sysout( this.getClass().getName() + ".getJobInstance( "+jobInstanceId+") has been called !!!");

		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		returnMap.put(BaseService.JOB_INSTANCE, "");
		
		JobInstance jobInstance = null;
		String msg = "";
		
		try {
			jobInstance = jobExplorer.getJobInstance(jobInstanceId);
			if(jobInstance == null) {
				throw new Exception("JobInstance 가 null 입니다.");
			}
    		returnMap.put(BaseService.JOB_INSTANCE, jobInstance);
    		returnMap.put(BaseService.SUCCESS_YN, "Y");
		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			msg = "JobExecution Id["+jobInstanceId+"]에 해당하는 JobInstance 조회 실패";
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	/**
	 * JobInstance 물리 삭제 (소속 JobExecution 전체 포함)
	 * <pre>
	 * JobInstance와 연결된 모든 JobExecution을 DB에서 완전히 제거 (복구 불가)
	 * 삭제 후 동일 파라미터로 새 JobInstance 생성 가능 (초기화 효과)
	 * 
	 * 삭제 흐름:
	 *   1. 각 JobExecution의 StepExecution, Context 삭제
	 *   2. JobExecution 삭제
	 *   3. JobInstance 삭제
	 * </pre>
	 * @param executionId
	 * @return
	 */
	public Map<String, Object> deleteJobInstance(Long instanceId) {
		LogUtil.sysout( this.getClass().getName() + ".deleteJobInstance( "+instanceId+" ) has been called !!!");

		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		returnMap.put(BaseService.JOB_INSTANCE_ID, "");

		String msg = "";
		JobInstance instance = null;
		
		try {

			// 1. JobInstance ID 체크
			if( instanceId == null ) {
				msg = "JobInstanceId ID["+instanceId+"] is not supposed to be empty!";
				throw new Exception(msg);
			}
			// 2. Job 조회
			try {
				instance = jobExplorer.getJobInstance(instanceId);
				if(instance == null) {
					throw new Exception("JobInstance 가 null 입니다.");
				}
			} catch (Exception e) {
				msg = "JobInstance를 찾을 수 없습니다. instanceId[" + instanceId + "]";
				throw new Exception(msg);
			}
			// 3. JobExecutions 삭제
	        List<JobExecution> executions = jobExplorer.getJobExecutions(instance);
	        for(JobExecution execution : executions) {
	        	this.deleteJobExecution(execution.getId());
	        }

			// 4. JobInstance 삭제
	        try {
	        	jobRepository.deleteJobInstance(instance);
			} catch (Exception e) {
				msg = "JobInstance ID["+instanceId+"] 로 JobInstance 삭제 실패.";
				throw new Exception(msg);
			}

			returnMap.put(BaseService.JOB_INSTANCE_ID, instanceId);
			returnMap.put(BaseService.SUCCESS_YN, "Y");
			
		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	
	/**
	 * JobExecution 반환.
	 * @param jobExecutionId
	 * @throws Exception
	 */
	public Map<String, Object> getJobExecution(Long jobExecutionId){
		LogUtil.sysout( this.getClass().getName() + ".getJobExecution( "+jobExecutionId+") has been called !!!");

		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		returnMap.put(BaseService.JOB_EXCUTION, "");
		
		JobExecution jobExecution = null;
		String msg = "";
		
		try {
			jobExecution = jobExplorer.getJobExecution(jobExecutionId);
			if(jobExecution == null) {
				throw new Exception("JobExecution 이 null 입니다.");
			}
    		returnMap.put(BaseService.JOB_EXCUTION, jobExecution);
    		returnMap.put(BaseService.SUCCESS_YN, "Y");
		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			msg = "JobExecution Id["+jobExecutionId+"]에 해당하는 JobExecution 조회 실패";
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	
	/**
	 * (Job InstanceId에 해당하는)JobExecutionList 반환.
	 * @param jobInstanceId
	 * @throws Exception
	 */
	public Map<String, Object> getJobExecutionList(Long jobInstanceId){
		LogUtil.sysout( this.getClass().getName() + ".getJobExecutionList( "+jobInstanceId+") has been called !!!");

		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		returnMap.put(BaseService.JOB_EXCUTION_LIST, "");
		
        JobInstance jobInstance = null;
        List<JobExecution> jobExecutionList = new ArrayList<JobExecution>();
		String msg = "";
		
		try {
			jobInstance = jobExplorer.getJobInstance(jobInstanceId);
			if( jobInstance != null ) {
				jobExecutionList = jobExplorer.getJobExecutions(jobInstance);
			}
    		returnMap.put(BaseService.JOB_EXCUTION_LIST, jobExecutionList);
    		returnMap.put(BaseService.SUCCESS_YN, "Y");
		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			msg = "JobInstance Id["+jobInstanceId+"]에 해당하는 JobExecution목록 조회 실패";
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	
	/**
	 * JobExecution을 실행 시키는 메소드.
	 * <pre>
	 * Job을 실행시키고(JobExecution 생성) DB에 물리적으로 등록(BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION 테이블에 행(Row)이 삽입)
	 * </pre>
	 * @param transactionId
	 * @param job
	 * @param jobParameters
	 * @return
	 * @throws Exception 
	 */
	public Map<String, Object> startJobExecution(String transactionId, Job job, JobParameters jobParameters ) throws Exception {
		return startJobExecution(transactionId, job, jobParameters, false);
	}
	
	/**
	 * JobExecution을 실행 시키는 메소드.
	 * <pre>
	 * Job을 실행시키고(JobExecution 생성) DB에 물리적으로 등록(BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION 테이블에 행(Row)이 삽입)
	 * </pre>
	 * @param transactionId
	 * @param job
	 * @param jobParameters
	 * @param asyncYn
	 * @return
	 * @throws Exception 
	 */
	public Map<String, Object> startJobExecution(String transactionId, Job job, JobParameters jobParameters, boolean asyncYn ) throws Exception {
		LogUtil.sysout( this.getClass().getName() + ".startJobExecution( "+transactionId+", "+job+", "+jobParameters+", "+asyncYn+") has been called !!!");

		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		returnMap.put(BaseService.JOB_EXCUTION, "");
		
		JobExecution execution = null;
		String msg = "";
		try {
			if( job == null ) {
				msg = "수행할 Job이 없습니다.";
				throw new Exception(msg);
			}
			try {
				JobLauncher jobLauncherObj = null;
				if( asyncYn ) {
					jobLauncherObj = asyncJobLauncher;
				}else {
					jobLauncherObj = jobLauncher;
				}
				execution = jobLauncherObj.run(job, jobParameters);
				if(execution == null) {
					throw new Exception("JobExecution 이 null 입니다.");
				}
			} catch (Exception e) {
				msg = "JobName["+job.getName()+"] Start 실패. 상세사항:" + e.toString();
				throw new Exception(msg);
			}
			
			this.logJobExcution("StartJobExecution", execution.getId());
			returnMap.put(BaseService.JOB_EXCUTION, execution);
			returnMap.put(BaseService.SUCCESS_YN, "Y");

		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	

	/**
	 * JobExecution을 중지 시키는 메소드.(Graceful Stop)
	 * <pre>
	 * JobOperator.stop()은 즉시 중단이 아닌 '중지 요청'을 보냄
	 * Tasklet/ItemProcessor가 stepExecution.isTerminateOnly()를 체크할 때 처리됨
	 * Job 상태: STARTED → STOPPING → STOPPED
	 * </pre>
	 * @param executionId
	 * @return
	 * @throws Exception 
	 */
	public Map<String, Object> stopJobExecution(Long executionId) {
		LogUtil.sysout( this.getClass().getName() + ".stopJobExecution( "+executionId+") has been called !!!");

		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		returnMap.put(BaseService.JOB_EXCUTION, "");
		
		JobExecution execution = null;
		String msg = "";
		try {
			// 1. JobExecution ID 체크
			if( executionId == null ) {
				msg = "JobExecution ID["+executionId+"] is not supposed to be empty!";
				throw new Exception(msg);
			}
			// 2. JobExecution Stop
			try {
				jobOperator.stop(executionId);
			} catch (Exception e) {
				msg = "JobExecution ID["+executionId+"] 에 해당하는 JobExecution Stop 실패.";
				throw new Exception(msg);
			}
			
			execution = jobExplorer.getJobExecution(executionId);
			this.logJobExcution("StopJobExecution", executionId);
			
			returnMap.put(BaseService.JOB_EXCUTION, execution);
			returnMap.put(BaseService.RETURN_MSG, msg);
			returnMap.put(BaseService.SUCCESS_YN, "Y");
			
		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	
	/**
	 * JobExecution을 재실행 시키는 메소드.(Restart)
	 * <pre>
	 * STOPPED 또는 FAILED 상태의 JobExecution만 재시작 가능
	 * 동일한 JobInstance에 새 JobExecution 생성
	 * ExecutionContext가 복원되어 중단된 지점부터 이어서 처리.
	 * 기존 JobExecution 는 STOP 으로 수정되고 새로운 JobExecution ID가 생성되어서 후속진행된다.
	 * 
	 * -재시작 조건-
	 * 1. JobExecution 상태: STOPPED 또는 FAILED
	 * 2. Job에 preventRestart() 설정이 없어야 함
	 * 3. Step에 allowStartIfComplete(false) 이면 COMPLETED Step은 건너뜀
	 * </pre>
	 * @param executionId
	 * @return
	 */
	public Map<String, Object> restartJobExecution(Long executionId) {
		LogUtil.sysout( this.getClass().getName() + ".restartJobExecution( "+executionId+" ) has been called !!!");

		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		returnMap.put(BaseService.JOB_EXCUTION_ID, "");
		returnMap.put(BaseService.NEW_JOB_EXCUTION_ID, "");
		String msg = "";
		
		JobExecution oldExecution = null;
		Long newExecutionId = null;
		
		try {
			// 1. JobExecution ID 체크
			if( executionId == null ) {
				msg = "JobExecution ID["+executionId+"] is not supposed to be empty!";
				throw new Exception(msg);
			}
			// 2. JobExecution 조회
			try {
				oldExecution = jobExplorer.getJobExecution(executionId);
				if(oldExecution == null) {
					throw new Exception("JobExecution 이 null 입니다.");
				}
			} catch (Exception e) {
				msg = "JobExecution ID["+executionId+"] 로 JobExecution 조회 실패.";
				throw new Exception(msg);
			}
			// 3. Status 확인
	        BatchStatus status = oldExecution.getStatus();
	        if (status != BatchStatus.STOPPED && status != BatchStatus.FAILED) {
	        	msg = "STOPPED 또는 FAILED 상태의 Job만 재시작할 수 있습니다. 현재 status[" + status.toString() + "]";
	        	throw new Exception(msg);
	        }
	        // 4. JobExecution Restart
	        try {
		        // JobOperator.restart()는 동일 JobInstance에 새 JobExecution을 생성하고 실행
		        newExecutionId = jobOperator.restart(executionId);
				if(newExecutionId == null) {
					throw new Exception("New ExecutionId 가 null 입니다.");
				}
			} catch (Exception e) {
				msg = "JobExecution ID["+executionId+"] 로 JobExecution 재시작 실패.";
	        	throw new Exception(msg);
			}

			jobExplorer.getJobExecution(newExecutionId);
			
			this.logJobExcution("jobExecutionId-Old", executionId);
			this.logJobExcution("jobExecutionId-New", newExecutionId);
			
			returnMap.put(BaseService.JOB_EXCUTION_ID, executionId);
			returnMap.put(BaseService.NEW_JOB_EXCUTION_ID, newExecutionId);
			returnMap.put(BaseService.SUCCESS_YN, "Y");

		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	
	/**
	 * JobExecution을 폐기(Abandon) 시키는 메소드.(소프트 삭제)
	 * <pre>
	 * STOPPED 상태의 JobExecution을 ABANDONED으로 변경
	 * 실행 이력은 DB에 유지되지만, 해당 JobInstance는 더 이상 재시작 불가
	 * 잘못 실행된 JobExecution을 완전히 포기할 때 사용
	 * </pre>
	 * @param executionId
	 * @return
	 */
	public Map<String, Object> abandonJobExecution(Long executionId) {
		LogUtil.sysout( this.getClass().getName() + ".abandonJobExecution( "+executionId+" ) has been called !!!");

		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		returnMap.put(BaseService.JOB_EXCUTION_ID, "");
		
		JobExecution execution = null;
		String msg = "";
		
		try {
			
			// 1. JobExecution ID 체크
			if( executionId == null ) {
				msg = "JobExecution ID["+executionId+"] is not supposed to be empty!";
				throw new Exception(msg);
			}
			// 2. JobExecution 조회
			try {
				execution = jobExplorer.getJobExecution(executionId);
				if(execution == null) {
					throw new Exception("JobExecution 이 null 입니다.");
				}
			} catch (Exception e) {
				msg = "JobExecution ID["+executionId+"] 로 JobExecution 조회 실패.";
				throw new Exception(msg);
			}
			// 3. Status 확인
	        BatchStatus status = execution.getStatus();
	        if (status == BatchStatus.STARTED || status == BatchStatus.STARTING || status == BatchStatus.STOPPING) {
	        	msg = "실행 중인 Job은 Abandon할 수 없습니다. 먼저 중지(stop)하세요. 현재 status[" + status.toString() + "]";
	        	throw new Exception(msg);
	        }else if (status == BatchStatus.ABANDONED) {
	        	msg = "이미 ABANDONED 상태입니다. 현재 status[" + status.toString() + "]";
	        	throw new Exception(msg);
	        }
	        // 4. JobExecution 폐기
	        try {
	        	jobOperator.abandon(executionId);
			} catch (Exception e) {
				msg = "JobExecution ID["+executionId+"] 로 JobExecution 폐기 실패.";
	        	throw new Exception(msg);
			}

			this.logJobExcution("AbandonJobExecution", executionId);
			returnMap.put(BaseService.JOB_EXCUTION_ID, executionId);
			returnMap.put(BaseService.SUCCESS_YN, "Y");
			
		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	
	/**
	 * JobExecution을 삭제 시키는 메소드.(물리적인 삭제)
	 * <pre>
	 * DB에서 해당 실행 기록을 완전히 제거 (복구 불가)
	 * 실행 중(STARTED/STARTING/STOPPING)인 JobExecution은 삭제 불가
	 * 삭제 순서: BATCH_STEP_EXECUTION_CONTEXT → BATCH_STEP_EXECUTION → BATCH_JOB_EXECUTION_CONTEXT → BATCH_JOB_EXECUTION_PARAMS → BATCH_JOB_EXECUTION (Spring Batch가 내부적으로 처리)
	 * </pre>
	 * @param executionId
	 * @return
	 */
	public Map<String, Object> deleteJobExecution(Long executionId){
		LogUtil.sysout( this.getClass().getName() + ".deleteJobExecution( "+executionId+" ) has been called !!!");

		Map<String, Object> returnMap = new HashMap<String, Object>();
		returnMap.put(BaseService.SUCCESS_YN, "");
		returnMap.put(BaseService.RETURN_MSG, "");
		returnMap.put(BaseService.JOB_EXCUTION_ID, "");
		
		JobExecution execution = null;
		String msg = "";
		
		try {

			// 1. JobExecution ID 체크
			if( executionId == null ) {
				msg = "JobExecution ID["+executionId+"] is not supposed to be empty!";
				throw new Exception(msg);
			}
			// 2. JobExecution 조회
			try {
				execution = jobExplorer.getJobExecution(executionId);
				if(execution == null) {
					throw new Exception("JobExecution 이 null 입니다.");
				}
			} catch (Exception e) {
				msg = "JobExecution ID["+executionId+"] 로 JobExecution 조회 실패.";
				throw new Exception(msg);
			}
			// 3. Status 확인
	        BatchStatus status = execution.getStatus();
	        if (status == BatchStatus.STARTED || status == BatchStatus.STARTING || status == BatchStatus.STOPPING) {
	        	msg = "실행 중인 JobExecution은 폐기할 수 없습니다. 먼저 중지(stop)하세요. 현재 status[" + status.toString() + "]";
	        	throw new Exception(msg);
	        }
	        
	        this.logJobExcution("DeleteJobExecution", executionId);
	        // 4. JobExecution 폐기
			try {
				jobRepository.deleteJobExecution(execution);
			} catch (Exception e) {
				msg = "JobExecution ID["+executionId+"] 로 JobExecution 폐기 실패.";
				throw new Exception(msg);
			}

			returnMap.put(BaseService.JOB_EXCUTION_ID, executionId);
			returnMap.put(BaseService.SUCCESS_YN, "Y");
			
		} catch (Exception e) {
			e.printStackTrace();
			returnMap.put(BaseService.SUCCESS_YN, "N");
			returnMap.put(BaseService.RETURN_MSG, msg);
		}
		return returnMap;
	}
	


	/**
	 * Job Excution 에 대한 로그 덤프.
	 * @param name
	 * @param executionId
	 */
	protected String logJobExcution(String name, Long executionId) {
    	StringBuffer buff = new StringBuffer();
    	try {
    		buff.append("\n");
    		buff.append("||======================================= ["+name+"] JobExecution Info Start =======================================||").append("\n");
    		buff.append("Job ExecutionId : ").append(executionId).append("\n");
    		JobExecution execution = null;
    		try {
    			Map<String, Object> returnMap = this.getJobExecution(executionId);
    			execution = (JobExecution)returnMap.get("jobExecution");
			} catch (Exception e) {
				// TODO: handle exception
			}
    		if( execution != null ) {
    			buff.append("Job Id : ").append(execution.getJobInstance().getId()).append("\n");
    			buff.append("Job Name : ").append(execution.getJobInstance().getJobName()).append("\n");
    			if( execution.getJobParameters() != null ) {
    				buff.append("Job Parameter : ").append(execution.getJobParameters()).append("\n");
    			}
    			buff.append("Job Status : ").append(execution.getStatus()).append("\n");
    		}else {
    			buff.append("Job Execution Not exists ").append("\n");
    		}
    		
    		buff.append("||======================================= ["+name+"] JobExecution Info End =======================================||").append("\n");
		}finally {
			LogUtil.sysout( buff );
		}
    	return buff.toString();
    }
}
