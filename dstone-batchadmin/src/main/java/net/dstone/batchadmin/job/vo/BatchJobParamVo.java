package net.dstone.batchadmin.job.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 배치Job 실행파라메터(TB_BATCH_JOB_PARAM) VO.
 * <pre>
 * Job 등록화면에서 여러건을 입력받아 저장하고, Job 시작(수동 즉시시작/자동스케줄) 시
 * dstone-batch RestApiRunner의 /startJob/{jobName} 호출 파라메터로 그대로 전달된다.
 * </pre>
 */
public class BatchJobParamVo {

	@JsonProperty("JOB_ID")
	private Long JOB_ID;
	@JsonProperty("PARAM_NAME")
	private String PARAM_NAME;
	@JsonProperty("PARAM_VALUE")
	private String PARAM_VALUE;

	public Long getJOB_ID() {
		return JOB_ID;
	}
	public void setJOB_ID(Long JOB_ID) {
		this.JOB_ID = JOB_ID;
	}
	public String getPARAM_NAME() {
		return PARAM_NAME;
	}
	public void setPARAM_NAME(String PARAM_NAME) {
		this.PARAM_NAME = PARAM_NAME;
	}
	public String getPARAM_VALUE() {
		return PARAM_VALUE;
	}
	public void setPARAM_VALUE(String PARAM_VALUE) {
		this.PARAM_VALUE = PARAM_VALUE;
	}

	@Override
	public String toString() {
		return "BatchJobParamVo [JOB_ID=" + JOB_ID + ", PARAM_NAME=" + PARAM_NAME + ", PARAM_VALUE=" + PARAM_VALUE + "]";
	}
}
