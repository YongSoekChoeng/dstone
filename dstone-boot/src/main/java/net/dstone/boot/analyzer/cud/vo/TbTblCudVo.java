
package net.dstone.boot.analyzer.cud.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;
                      
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement( name="TbTblCudVo" ) 
public class TbTblCudVo extends net.dstone.common.biz.BaseVo implements java.io.Serializable { 
	@JsonProperty("SYS_ID") 
	private String SYS_ID; 
	@JsonProperty("TBL_ID") 
	private String TBL_ID; 
	@JsonProperty("TBL_OWNER") 
	private String TBL_OWNER; 
	@JsonProperty("TBL_NM") 
	private String TBL_NM; 
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
	 * @return Returns the TBL_ID
	 */ 
	public String getTBL_ID() { 
		return this.TBL_ID;
	}
	/** 
	 * 
	 * @param TBL_ID the TBL_ID to set
	 */ 
	public void setTBL_ID(String TBL_ID) { 
		this.TBL_ID = TBL_ID;
	}
	/** 
	 * 
	 * @return Returns the TBL_OWNER
	 */ 
	public String getTBL_OWNER() { 
		return this.TBL_OWNER;
	}
	/** 
	 * 
	 * @param TBL_OWNER the TBL_OWNER to set
	 */ 
	public void setTBL_OWNER(String TBL_OWNER) { 
		this.TBL_OWNER = TBL_OWNER;
	}
	/** 
	 * 
	 * @return Returns the TBL_NM
	 */ 
	public String getTBL_NM() { 
		return this.TBL_NM;
	}
	/** 
	 * 
	 * @param TBL_NM the TBL_NM to set
	 */ 
	public void setTBL_NM(String TBL_NM) { 
		this.TBL_NM = TBL_NM;
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
