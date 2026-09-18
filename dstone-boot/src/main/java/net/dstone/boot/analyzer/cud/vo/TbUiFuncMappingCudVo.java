
package net.dstone.boot.analyzer.cud.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;
                      
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement( name="TbUiFuncMappingCudVo" ) 
public class TbUiFuncMappingCudVo extends net.dstone.common.biz.BaseVo implements java.io.Serializable { 
	@JsonProperty("SYS_ID") 
	private String SYS_ID; 
	@JsonProperty("UI_ID") 
	private String UI_ID; 
	@JsonProperty("MTD_URL") 
	private String MTD_URL; 
	@JsonProperty("WORKER_ID") 
	private String WORKER_ID; 
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
	 * @return Returns the UI_ID
	 */ 
	public String getUI_ID() { 
		return this.UI_ID;
	}
	/** 
	 * 
	 * @param UI_ID the UI_ID to set
	 */ 
	public void setUI_ID(String UI_ID) { 
		this.UI_ID = UI_ID;
	}
	/** 
	 * 
	 * @return Returns the MTD_URL
	 */ 
	public String getMTD_URL() { 
		return this.MTD_URL;
	}
	/** 
	 * 
	 * @param MTD_URL the MTD_URL to set
	 */ 
	public void setMTD_URL(String MTD_URL) { 
		this.MTD_URL = MTD_URL;
	}
	/** 
	 * 
	 * @return Returns the WORKER_ID
	 */ 
	public String getWORKER_ID() { 
		return this.WORKER_ID;
	}
	/** 
	 * 
	 * @param WORKER_ID the WORKER_ID to set
	 */ 
	public void setWORKER_ID(String WORKER_ID) { 
		this.WORKER_ID = WORKER_ID;
	}
}                     
