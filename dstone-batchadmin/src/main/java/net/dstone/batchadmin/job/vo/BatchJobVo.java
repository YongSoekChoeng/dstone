package net.dstone.batchadmin.job.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * 배치Job 메타데이터(TB_BATCH_JOB) VO.
 * <pre>
 * JOB_NM : dstone-batch측 @AutoRegJob(name=...) 값과 반드시 일치해야 함(REST 제어/자동스케줄 대상 식별자로 사용).
 * SERVER_ID : 이 Job을 실행할 대상 배치서버(TB_BATCH_SERVER).
 * CRON_EXPRESSION/SCHEDULE_USE_YN : 'Y'일 경우 JobScheduleManager가 CronTrigger로 자동 기동.
 * </pre>
 */
@XmlRootElement(name = "BatchJobVo")
public class BatchJobVo extends net.dstone.batchadmin.common.biz.BaseVo {

	@JsonProperty("JOB_ID")
	private Long JOB_ID;
	@JsonProperty("JOB_NM")
	private String JOB_NM;
	@JsonProperty("SERVER_ID")
	private Long SERVER_ID;
	@JsonProperty("SERVER_NM")
	private String SERVER_NM;
	@JsonProperty("DESCRIPTION")
	private String DESCRIPTION;
	@JsonProperty("CRON_EXPRESSION")
	private String CRON_EXPRESSION;
	@JsonProperty("SCHEDULE_USE_YN")
	private String SCHEDULE_USE_YN;
	@JsonProperty("OWNER_NM")
	private String OWNER_NM;
	@JsonProperty("USE_YN")
	private String USE_YN;
	@JsonProperty("REG_DT")
	private String REG_DT;
	@JsonProperty("UPDATE_DT")
	private String UPDATE_DT;

	public Long getJOB_ID() {
		return JOB_ID;
	}
	public void setJOB_ID(Long JOB_ID) {
		this.JOB_ID = JOB_ID;
	}
	public String getJOB_NM() {
		return JOB_NM;
	}
	public void setJOB_NM(String JOB_NM) {
		this.JOB_NM = JOB_NM;
	}
	public Long getSERVER_ID() {
		return SERVER_ID;
	}
	public void setSERVER_ID(Long SERVER_ID) {
		this.SERVER_ID = SERVER_ID;
	}
	public String getSERVER_NM() {
		return SERVER_NM;
	}
	public void setSERVER_NM(String SERVER_NM) {
		this.SERVER_NM = SERVER_NM;
	}
	public String getDESCRIPTION() {
		return DESCRIPTION;
	}
	public void setDESCRIPTION(String DESCRIPTION) {
		this.DESCRIPTION = DESCRIPTION;
	}
	public String getCRON_EXPRESSION() {
		return CRON_EXPRESSION;
	}
	public void setCRON_EXPRESSION(String CRON_EXPRESSION) {
		this.CRON_EXPRESSION = CRON_EXPRESSION;
	}
	public String getSCHEDULE_USE_YN() {
		return SCHEDULE_USE_YN;
	}
	public void setSCHEDULE_USE_YN(String SCHEDULE_USE_YN) {
		this.SCHEDULE_USE_YN = SCHEDULE_USE_YN;
	}
	public String getOWNER_NM() {
		return OWNER_NM;
	}
	public void setOWNER_NM(String OWNER_NM) {
		this.OWNER_NM = OWNER_NM;
	}
	public String getUSE_YN() {
		return USE_YN;
	}
	public void setUSE_YN(String USE_YN) {
		this.USE_YN = USE_YN;
	}
	public String getREG_DT() {
		return REG_DT;
	}
	public void setREG_DT(String REG_DT) {
		this.REG_DT = REG_DT;
	}
	public String getUPDATE_DT() {
		return UPDATE_DT;
	}
	public void setUPDATE_DT(String UPDATE_DT) {
		this.UPDATE_DT = UPDATE_DT;
	}

	@Override
	public String toString() {
		return "BatchJobVo [JOB_ID=" + JOB_ID + ", JOB_NM=" + JOB_NM + ", SERVER_ID=" + SERVER_ID + ", CRON_EXPRESSION="
				+ CRON_EXPRESSION + ", SCHEDULE_USE_YN=" + SCHEDULE_USE_YN + ", USE_YN=" + USE_YN + "]";
	}
}
