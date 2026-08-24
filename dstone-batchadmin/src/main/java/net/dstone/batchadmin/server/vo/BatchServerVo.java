package net.dstone.batchadmin.server.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * 관리대상 dstone-batch 서버 레지스트리(TB_BATCH_SERVER) VO.
 * <pre>
 * REST_BASE_URL : dstone-batch RestApiRunner의 base url (예: http://localhost:6081/batch)
 * DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD : 해당 서버가 사용하는 Spring Batch 메타데이터DB 접속정보(DB_PASSWORD는 ENC(...) 암호화 저장)
 * DBMS_TYPE : MYSQL/POSTGRES - 매퍼 XML의 벤더별 쿼리분기(<if test="DBMS_TYPE=='...'">)에 사용
 * </pre>
 */
@XmlRootElement(name = "BatchServerVo")
public class BatchServerVo extends net.dstone.batchadmin.common.biz.BaseVo {

	@JsonProperty("SERVER_ID")
	private Long SERVER_ID;
	@JsonProperty("SERVER_NM")
	private String SERVER_NM;
	@JsonProperty("REST_BASE_URL")
	private String REST_BASE_URL;
	@JsonProperty("DB_HOST")
	private String DB_HOST;
	@JsonProperty("DB_PORT")
	private String DB_PORT;
	@JsonProperty("DB_NAME")
	private String DB_NAME;
	@JsonProperty("DB_USER")
	private String DB_USER;
	@JsonProperty("DB_PASSWORD")
	private String DB_PASSWORD;
	@JsonProperty("DBMS_TYPE")
	private String DBMS_TYPE;
	@JsonProperty("USE_YN")
	private String USE_YN;
	@JsonProperty("DESCRIPTION")
	private String DESCRIPTION;
	@JsonProperty("REG_DT")
	private String REG_DT;
	@JsonProperty("UPDATE_DT")
	private String UPDATE_DT;

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
	public String getREST_BASE_URL() {
		return REST_BASE_URL;
	}
	public void setREST_BASE_URL(String REST_BASE_URL) {
		this.REST_BASE_URL = REST_BASE_URL;
	}
	public String getDB_HOST() {
		return DB_HOST;
	}
	public void setDB_HOST(String DB_HOST) {
		this.DB_HOST = DB_HOST;
	}
	public String getDB_PORT() {
		return DB_PORT;
	}
	public void setDB_PORT(String DB_PORT) {
		this.DB_PORT = DB_PORT;
	}
	public String getDB_NAME() {
		return DB_NAME;
	}
	public void setDB_NAME(String DB_NAME) {
		this.DB_NAME = DB_NAME;
	}
	public String getDB_USER() {
		return DB_USER;
	}
	public void setDB_USER(String DB_USER) {
		this.DB_USER = DB_USER;
	}
	public String getDB_PASSWORD() {
		return DB_PASSWORD;
	}
	public void setDB_PASSWORD(String DB_PASSWORD) {
		this.DB_PASSWORD = DB_PASSWORD;
	}
	public String getDBMS_TYPE() {
		return DBMS_TYPE;
	}
	public void setDBMS_TYPE(String DBMS_TYPE) {
		this.DBMS_TYPE = DBMS_TYPE;
	}
	public String getUSE_YN() {
		return USE_YN;
	}
	public void setUSE_YN(String USE_YN) {
		this.USE_YN = USE_YN;
	}
	public String getDESCRIPTION() {
		return DESCRIPTION;
	}
	public void setDESCRIPTION(String DESCRIPTION) {
		this.DESCRIPTION = DESCRIPTION;
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
		return "BatchServerVo [SERVER_ID=" + SERVER_ID + ", SERVER_NM=" + SERVER_NM + ", REST_BASE_URL=" + REST_BASE_URL
				+ ", DB_HOST=" + DB_HOST + ", DB_PORT=" + DB_PORT + ", DB_NAME=" + DB_NAME + ", DB_USER=" + DB_USER
				+ ", DBMS_TYPE=" + DBMS_TYPE + ", USE_YN=" + USE_YN + ", DESCRIPTION=" + DESCRIPTION + "]";
	}
}
