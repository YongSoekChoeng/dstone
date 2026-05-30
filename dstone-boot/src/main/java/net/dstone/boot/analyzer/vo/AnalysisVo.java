package net.dstone.boot.analyzer.vo;

import jakarta.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement( name="AnalysisVo" ) 
public class AnalysisVo extends net.dstone.common.biz.BaseVo { 
	@JsonProperty("SYS_ID") 
	private String SYS_ID; 
	@JsonProperty("SYS_NM") 
	private String SYS_NM; 
	@JsonProperty("CONF_FILE_PATH") 
	private String CONF_FILE_PATH; 

	/** 
	 * 
	 * @return Returns the SYS_ID
	 */ 
	public String getSYS_ID() { 
		return this.SYS_ID;
	}
	/** 
	 * 
	 * @param SYS_ID the SYS_ID to set
	 */ 
	public void setSYS_ID(String SYS_ID) { 
		this.SYS_ID = SYS_ID;
	}
	/** 
	 * 
	 * @return Returns the SYS_NM
	 */ 
	public String getSYS_NM() { 
		return this.SYS_NM;
	}
	/** 
	 * 
	 * @param SYS_NM the SYS_NM to set
	 */ 
	public void setSYS_NM(String SYS_NM) { 
		this.SYS_NM = SYS_NM;
	}
	/** 
	 * 
	 * @return Returns the CONF_FILE_PATH
	 */ 
	public String getCONF_FILE_PATH() { 
		return this.CONF_FILE_PATH;
	}
	/** 
	 * 
	 * @param CONF_FILE_PATH the CONF_FILE_PATH to set
	 */ 
	public void setCONF_FILE_PATH(String CONF_FILE_PATH) { 
		this.CONF_FILE_PATH = CONF_FILE_PATH;
	}
}
