package net.dstone.boot.ai.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TB_AI_SQLCONVERT 매핑용 VO다. "오라클 SQL을 넣었더니 뭐가 나왔는지"를 그대로 담아서, 화면에
 * 바로 보여주는 용도와 이력 저장용으로 같이 쓴다.
 */
public class SqlConvertVo extends net.dstone.boot.common.biz.BaseVo {

	@JsonProperty("ID")
	private Long ID;
	@JsonProperty("ORIGINAL_SQL")
	private String ORIGINAL_SQL;
	@JsonProperty("CONVERTED_SQL")
	private String CONVERTED_SQL;
	@JsonProperty("SUCCESS_YN")
	private String SUCCESS_YN;
	@JsonProperty("ERROR_MESSAGE")
	private String ERROR_MESSAGE;
	@JsonProperty("REQUESTER_ID")
	private String REQUESTER_ID;
	@JsonProperty("INPUT_DT")
	private String INPUT_DT;

	public Long getID() {
		return this.ID;
	}
	public void setID(Long ID) {
		this.ID = ID;
	}
	public String getORIGINAL_SQL() {
		return this.ORIGINAL_SQL;
	}
	public void setORIGINAL_SQL(String ORIGINAL_SQL) {
		this.ORIGINAL_SQL = ORIGINAL_SQL;
	}
	public String getCONVERTED_SQL() {
		return this.CONVERTED_SQL;
	}
	public void setCONVERTED_SQL(String CONVERTED_SQL) {
		this.CONVERTED_SQL = CONVERTED_SQL;
	}
	public String getSUCCESS_YN() {
		return this.SUCCESS_YN;
	}
	public void setSUCCESS_YN(String SUCCESS_YN) {
		this.SUCCESS_YN = SUCCESS_YN;
	}
	public String getERROR_MESSAGE() {
		return this.ERROR_MESSAGE;
	}
	public void setERROR_MESSAGE(String ERROR_MESSAGE) {
		this.ERROR_MESSAGE = ERROR_MESSAGE;
	}
	public String getREQUESTER_ID() {
		return this.REQUESTER_ID;
	}
	public void setREQUESTER_ID(String REQUESTER_ID) {
		this.REQUESTER_ID = REQUESTER_ID;
	}
	public String getINPUT_DT() {
		return this.INPUT_DT;
	}
	public void setINPUT_DT(String INPUT_DT) {
		this.INPUT_DT = INPUT_DT;
	}

}
