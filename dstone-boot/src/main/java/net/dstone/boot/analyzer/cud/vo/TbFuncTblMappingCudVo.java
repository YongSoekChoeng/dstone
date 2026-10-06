
package net.dstone.boot.analyzer.cud.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;
                      
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement( name="TbFuncTblMappingCudVo" ) 
public class TbFuncTblMappingCudVo extends net.dstone.common.biz.BaseVo implements java.io.Serializable { 
	@JsonProperty("SYS_ID") 
	private String SYS_ID; 
	@JsonProperty("FUNC_ID") 
	private String FUNC_ID; 
	@JsonProperty("TBL_ID") 
	private String TBL_ID; 
	@JsonProperty("JOB_KIND") 
	private String JOB_KIND; 
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
	 * @return Returns the JOB_KIND
	 */ 
	public String getJOB_KIND() { 
		return this.JOB_KIND;
	}
	/** 
	 * 
	 * @param JOB_KIND the JOB_KIND to set
	 */ 
	public void setJOB_KIND(String JOB_KIND) { 
		this.JOB_KIND = JOB_KIND;
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
