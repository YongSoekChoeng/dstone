package net.dstone.batchadmin.job.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * 배치서버의 BATCH_STEP_EXECUTION 조회결과 VO.
 */
@XmlRootElement(name = "BatchStepExecVo")
public class BatchStepExecVo extends net.dstone.batchadmin.common.biz.BaseVo {

	@JsonProperty("STEP_EXECUTION_ID")
	private Long STEP_EXECUTION_ID;
	@JsonProperty("STEP_NAME")
	private String STEP_NAME;
	@JsonProperty("JOB_EXECUTION_ID")
	private Long JOB_EXECUTION_ID;
	@JsonProperty("STATUS")
	private String STATUS;
	@JsonProperty("START_TIME")
	private String START_TIME;
	@JsonProperty("END_TIME")
	private String END_TIME;
	@JsonProperty("COMMIT_COUNT")
	private Long COMMIT_COUNT;
	@JsonProperty("READ_COUNT")
	private Long READ_COUNT;
	@JsonProperty("WRITE_COUNT")
	private Long WRITE_COUNT;
	@JsonProperty("FILTER_COUNT")
	private Long FILTER_COUNT;
	@JsonProperty("READ_SKIP_COUNT")
	private Long READ_SKIP_COUNT;
	@JsonProperty("WRITE_SKIP_COUNT")
	private Long WRITE_SKIP_COUNT;
	@JsonProperty("PROCESS_SKIP_COUNT")
	private Long PROCESS_SKIP_COUNT;
	@JsonProperty("ROLLBACK_COUNT")
	private Long ROLLBACK_COUNT;
	@JsonProperty("EXIT_CODE")
	private String EXIT_CODE;
	@JsonProperty("EXIT_MESSAGE")
	private String EXIT_MESSAGE;

	/*** 조회조건 ***/
	@JsonProperty("SERVER_ID")
	private Long SERVER_ID;

	public Long getSTEP_EXECUTION_ID() {
		return STEP_EXECUTION_ID;
	}
	public void setSTEP_EXECUTION_ID(Long STEP_EXECUTION_ID) {
		this.STEP_EXECUTION_ID = STEP_EXECUTION_ID;
	}
	public String getSTEP_NAME() {
		return STEP_NAME;
	}
	public void setSTEP_NAME(String STEP_NAME) {
		this.STEP_NAME = STEP_NAME;
	}
	public Long getJOB_EXECUTION_ID() {
		return JOB_EXECUTION_ID;
	}
	public void setJOB_EXECUTION_ID(Long JOB_EXECUTION_ID) {
		this.JOB_EXECUTION_ID = JOB_EXECUTION_ID;
	}
	public String getSTATUS() {
		return STATUS;
	}
	public void setSTATUS(String STATUS) {
		this.STATUS = STATUS;
	}
	public String getSTART_TIME() {
		return START_TIME;
	}
	public void setSTART_TIME(String START_TIME) {
		this.START_TIME = START_TIME;
	}
	public String getEND_TIME() {
		return END_TIME;
	}
	public void setEND_TIME(String END_TIME) {
		this.END_TIME = END_TIME;
	}
	public Long getCOMMIT_COUNT() {
		return COMMIT_COUNT;
	}
	public void setCOMMIT_COUNT(Long COMMIT_COUNT) {
		this.COMMIT_COUNT = COMMIT_COUNT;
	}
	public Long getREAD_COUNT() {
		return READ_COUNT;
	}
	public void setREAD_COUNT(Long READ_COUNT) {
		this.READ_COUNT = READ_COUNT;
	}
	public Long getWRITE_COUNT() {
		return WRITE_COUNT;
	}
	public void setWRITE_COUNT(Long WRITE_COUNT) {
		this.WRITE_COUNT = WRITE_COUNT;
	}
	public Long getFILTER_COUNT() {
		return FILTER_COUNT;
	}
	public void setFILTER_COUNT(Long FILTER_COUNT) {
		this.FILTER_COUNT = FILTER_COUNT;
	}
	public Long getREAD_SKIP_COUNT() {
		return READ_SKIP_COUNT;
	}
	public void setREAD_SKIP_COUNT(Long READ_SKIP_COUNT) {
		this.READ_SKIP_COUNT = READ_SKIP_COUNT;
	}
	public Long getWRITE_SKIP_COUNT() {
		return WRITE_SKIP_COUNT;
	}
	public void setWRITE_SKIP_COUNT(Long WRITE_SKIP_COUNT) {
		this.WRITE_SKIP_COUNT = WRITE_SKIP_COUNT;
	}
	public Long getPROCESS_SKIP_COUNT() {
		return PROCESS_SKIP_COUNT;
	}
	public void setPROCESS_SKIP_COUNT(Long PROCESS_SKIP_COUNT) {
		this.PROCESS_SKIP_COUNT = PROCESS_SKIP_COUNT;
	}
	public Long getROLLBACK_COUNT() {
		return ROLLBACK_COUNT;
	}
	public void setROLLBACK_COUNT(Long ROLLBACK_COUNT) {
		this.ROLLBACK_COUNT = ROLLBACK_COUNT;
	}
	public String getEXIT_CODE() {
		return EXIT_CODE;
	}
	public void setEXIT_CODE(String EXIT_CODE) {
		this.EXIT_CODE = EXIT_CODE;
	}
	public String getEXIT_MESSAGE() {
		return EXIT_MESSAGE;
	}
	public void setEXIT_MESSAGE(String EXIT_MESSAGE) {
		this.EXIT_MESSAGE = EXIT_MESSAGE;
	}
	public Long getSERVER_ID() {
		return SERVER_ID;
	}
	public void setSERVER_ID(Long SERVER_ID) {
		this.SERVER_ID = SERVER_ID;
	}

}
