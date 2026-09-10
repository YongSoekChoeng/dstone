package net.dstone.boot.ai.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TB_AI_DOCUMENT 매핑용 VO. dstone-ai-engine의 pgvector 적재 상태와는 별개로, dstone-boot가
 * "무엇을 업로드했는지" 화면에 보여주기 위해 자체로 들고 있는 메타데이터다.
 */
public class DocumentVo extends net.dstone.boot.common.biz.BaseVo {

	@JsonProperty("SOURCE_ID")
	private String SOURCE_ID;
	@JsonProperty("FILE_NAME")
	private String FILE_NAME;
	@JsonProperty("CHUNK_COUNT")
	private Integer CHUNK_COUNT;
	@JsonProperty("UPLOADER_ID")
	private String UPLOADER_ID;
	@JsonProperty("INPUT_DT")
	private String INPUT_DT;

	public String getSOURCE_ID() {
		return this.SOURCE_ID;
	}
	public void setSOURCE_ID(String SOURCE_ID) {
		this.SOURCE_ID = SOURCE_ID;
	}
	public String getFILE_NAME() {
		return this.FILE_NAME;
	}
	public void setFILE_NAME(String FILE_NAME) {
		this.FILE_NAME = FILE_NAME;
	}
	public Integer getCHUNK_COUNT() {
		return this.CHUNK_COUNT;
	}
	public void setCHUNK_COUNT(Integer CHUNK_COUNT) {
		this.CHUNK_COUNT = CHUNK_COUNT;
	}
	public String getUPLOADER_ID() {
		return this.UPLOADER_ID;
	}
	public void setUPLOADER_ID(String UPLOADER_ID) {
		this.UPLOADER_ID = UPLOADER_ID;
	}
	public String getINPUT_DT() {
		return this.INPUT_DT;
	}
	public void setINPUT_DT(String INPUT_DT) {
		this.INPUT_DT = INPUT_DT;
	}

}
