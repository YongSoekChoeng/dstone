
package net.dstone.boot.analyzer.cud.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement( name="TbFuncCudVo" ) 
public class TbFuncCudVo extends net.dstone.common.biz.BaseVo implements java.io.Serializable { 
	@JsonProperty("SYS_ID") 
	private String SYS_ID; 
	@JsonProperty("FUNC_ID") 
	private String FUNC_ID; 
	@JsonProperty("CLZZ_ID") 
	private String CLZZ_ID; 
	@JsonProperty("MTD_ID") 
	private String MTD_ID; 
	@JsonProperty("MTD_NM") 
	private String MTD_NM; 
	@JsonProperty("MTD_URL") 
	private String MTD_URL; 
	@JsonProperty("FILE_NAME") 
	private String FILE_NAME; 
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
	 * @return Returns the FUNC_ID
	 */ 
	public String getFUNC_ID() { 
		return this.FUNC_ID;
	}
	/** 
	 * 
	 * @param FUNC_ID the FUNC_ID to set
	 */ 
	public void setFUNC_ID(String FUNC_ID) { 
		this.FUNC_ID = FUNC_ID;
	}
	/** 
	 * 
	 * @return Returns the CLZZ_ID
	 */ 
	public String getCLZZ_ID() { 
		return this.CLZZ_ID;
	}
	/** 
	 * 
	 * @param CLZZ_ID the CLZZ_ID to set
	 */ 
	public void setCLZZ_ID(String CLZZ_ID) { 
		this.CLZZ_ID = CLZZ_ID;
	}
	/** 
	 * 
	 * @return Returns the MTD_ID
	 */ 
	public String getMTD_ID() { 
		return this.MTD_ID;
	}
	/** 
	 * 
	 * @param MTD_ID the MTD_ID to set
	 */ 
	public void setMTD_ID(String MTD_ID) { 
		this.MTD_ID = MTD_ID;
	}
	/** 
	 * 
	 * @return Returns the MTD_NM
	 */ 
	public String getMTD_NM() { 
		return this.MTD_NM;
	}
	/** 
	 * 
	 * @param MTD_NM the MTD_NM to set
	 */ 
	public void setMTD_NM(String MTD_NM) { 
		this.MTD_NM = MTD_NM;
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
	 * @return Returns the FILE_NAME
	 */ 
	public String getFILE_NAME() { 
		return this.FILE_NAME;
	}
	/** 
	 * 
	 * @param FILE_NAME the FILE_NAME to set
	 */ 
	public void setFILE_NAME(String FILE_NAME) { 
		this.FILE_NAME = FILE_NAME;
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
