package net.dstone.batchadmin.job.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * 배치서버의 BATCH_JOB_EXECUTION JOIN BATCH_JOB_INSTANCE 조회결과 VO. (조회조건 겸용)
 * <pre>
 * 목록조회 행 grain = JobExecution 1건 = 1행.
 * SERVER_ID/DBMS_TYPE : 조회 대상 배치서버 지정 및 매퍼 XML의 벤더별 페이징 쿼리 분기용.
 * </pre>
 */
@XmlRootElement(name = "BatchJobExecVo")
public class BatchJobExecVo extends net.dstone.batchadmin.common.biz.BaseVo {

	/*** 조회결과 컬럼 ***/
	@JsonProperty("JOB_EXECUTION_ID")
	private Long JOB_EXECUTION_ID;
	@JsonProperty("JOB_INSTANCE_ID")
	private Long JOB_INSTANCE_ID;
	@JsonProperty("JOB_NAME")
	private String JOB_NAME;
	@JsonProperty("STATUS")
	private String STATUS;
	@JsonProperty("CREATE_TIME")
	private String CREATE_TIME;
	@JsonProperty("START_TIME")
	private String START_TIME;
	@JsonProperty("END_TIME")
	private String END_TIME;
	@JsonProperty("EXIT_CODE")
	private String EXIT_CODE;
	@JsonProperty("EXIT_MESSAGE")
	private String EXIT_MESSAGE;
	@JsonProperty("LAST_UPDATED")
	private String LAST_UPDATED;

	/*** batchadmin 자체 메타(TB_BATCH_JOB) 보강 정보(선택적, JOB_NAME 기준 앱레벨 병합) ***/
	@JsonProperty("DESCRIPTION")
	private String DESCRIPTION;
	@JsonProperty("OWNER_NM")
	private String OWNER_NM;

	/*** 조회조건 ***/
	@JsonProperty("SERVER_ID")
	private Long SERVER_ID;
	@JsonProperty("DBMS_TYPE")
	private String DBMS_TYPE;
	@JsonProperty("SEARCH_JOB_INSTANCE_ID")
	private String SEARCH_JOB_INSTANCE_ID;
	@JsonProperty("SEARCH_JOB_NAME")
	private String SEARCH_JOB_NAME;
	@JsonProperty("SEARCH_START_DT_FROM")
	private String SEARCH_START_DT_FROM;
	@JsonProperty("SEARCH_START_DT_TO")
	private String SEARCH_START_DT_TO;

	public Long getJOB_EXECUTION_ID() {
		return JOB_EXECUTION_ID;
	}
	public void setJOB_EXECUTION_ID(Long JOB_EXECUTION_ID) {
		this.JOB_EXECUTION_ID = JOB_EXECUTION_ID;
	}
	public Long getJOB_INSTANCE_ID() {
		return JOB_INSTANCE_ID;
	}
	public void setJOB_INSTANCE_ID(Long JOB_INSTANCE_ID) {
		this.JOB_INSTANCE_ID = JOB_INSTANCE_ID;
	}
	public String getJOB_NAME() {
		return JOB_NAME;
	}
	public void setJOB_NAME(String JOB_NAME) {
		this.JOB_NAME = JOB_NAME;
	}
	public String getSTATUS() {
		return STATUS;
	}
	public void setSTATUS(String STATUS) {
		this.STATUS = STATUS;
	}
	public String getCREATE_TIME() {
		return CREATE_TIME;
	}
	public void setCREATE_TIME(String CREATE_TIME) {
		this.CREATE_TIME = CREATE_TIME;
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
	public String getLAST_UPDATED() {
		return LAST_UPDATED;
	}
	public void setLAST_UPDATED(String LAST_UPDATED) {
		this.LAST_UPDATED = LAST_UPDATED;
	}
	public String getDESCRIPTION() {
		return DESCRIPTION;
	}
	public void setDESCRIPTION(String DESCRIPTION) {
		this.DESCRIPTION = DESCRIPTION;
	}
	public String getOWNER_NM() {
		return OWNER_NM;
	}
	public void setOWNER_NM(String OWNER_NM) {
		this.OWNER_NM = OWNER_NM;
	}
	public Long getSERVER_ID() {
		return SERVER_ID;
	}
	public void setSERVER_ID(Long SERVER_ID) {
		this.SERVER_ID = SERVER_ID;
	}
	public String getDBMS_TYPE() {
		return DBMS_TYPE;
	}
	public void setDBMS_TYPE(String DBMS_TYPE) {
		this.DBMS_TYPE = DBMS_TYPE;
	}
	public String getSEARCH_JOB_INSTANCE_ID() {
		return SEARCH_JOB_INSTANCE_ID;
	}
	public void setSEARCH_JOB_INSTANCE_ID(String SEARCH_JOB_INSTANCE_ID) {
		this.SEARCH_JOB_INSTANCE_ID = SEARCH_JOB_INSTANCE_ID;
	}
	public String getSEARCH_JOB_NAME() {
		return SEARCH_JOB_NAME;
	}
	public void setSEARCH_JOB_NAME(String SEARCH_JOB_NAME) {
		this.SEARCH_JOB_NAME = SEARCH_JOB_NAME;
	}
	public String getSEARCH_START_DT_FROM() {
		return SEARCH_START_DT_FROM;
	}
	public void setSEARCH_START_DT_FROM(String SEARCH_START_DT_FROM) {
		this.SEARCH_START_DT_FROM = SEARCH_START_DT_FROM;
	}
	public String getSEARCH_START_DT_TO() {
		return SEARCH_START_DT_TO;
	}
	public void setSEARCH_START_DT_TO(String SEARCH_START_DT_TO) {
		this.SEARCH_START_DT_TO = SEARCH_START_DT_TO;
	}

	@Override
	public String toString() {
		return "BatchJobExecVo [JOB_EXECUTION_ID=" + JOB_EXECUTION_ID + ", JOB_INSTANCE_ID=" + JOB_INSTANCE_ID
				+ ", JOB_NAME=" + JOB_NAME + ", STATUS=" + STATUS + ", START_TIME=" + START_TIME + "]";
	}
}
