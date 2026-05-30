
package net.dstone.boot.analyzer.cud.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;
                      
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement( name="TbFuncFuncMappingCudVo" ) 
public class TbFuncFuncMappingCudVo extends net.dstone.common.biz.BaseVo implements java.io.Serializable { 
	@JsonProperty("SYS_ID") 
	private String SYS_ID; 
	@JsonProperty("FUNC_ID") 
	private String FUNC_ID; 
	@JsonProperty("CALL_FUNC_ID") 
	private String CALL_FUNC_ID; 
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
	 * @return Returns the CALL_FUNC_ID
	 */ 
	public String getCALL_FUNC_ID() { 
		return this.CALL_FUNC_ID;
	}
	/** 
	 * 
	 * @param CALL_FUNC_ID the CALL_FUNC_ID to set
	 */ 
	public void setCALL_FUNC_ID(String CALL_FUNC_ID) { 
		this.CALL_FUNC_ID = CALL_FUNC_ID;
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
