
package net.dstone.boot.analyzer.cud.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement( name="TbClzzCudVo" ) 
public class TbClzzCudVo extends net.dstone.common.biz.BaseVo implements java.io.Serializable { 
	@JsonProperty("SYS_ID") 
	private String SYS_ID; 
	@JsonProperty("CLZZ_ID") 
	private String CLZZ_ID; 
	@JsonProperty("PKG_ID") 
	private String PKG_ID; 
	@JsonProperty("CLZZ_NM") 
	private String CLZZ_NM; 
	@JsonProperty("CLZZ_KIND") 
	private String CLZZ_KIND; 
	@JsonProperty("RESOURCE_ID") 
	private String RESOURCE_ID; 
	@JsonProperty("CLZZ_INTF") 
	private String CLZZ_INTF; 
	@JsonProperty("INTF_ID_LIST") 
	private String INTF_ID_LIST; 
	@JsonProperty("PARENT_CLZZ_ID") 
	private String PARENT_CLZZ_ID; 
	@JsonProperty("INTF_IMPL_CLZZ_ID_LIST") 
	private String INTF_IMPL_CLZZ_ID_LIST; 
	@JsonProperty("MEMBER_ALIAS_LIST") 
	private String MEMBER_ALIAS_LIST; 
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
	 * @return Returns the PKG_ID
	 */ 
	public String getPKG_ID() { 
		return this.PKG_ID;
	}
	/** 
	 * 
	 * @param PKG_ID the PKG_ID to set
	 */ 
	public void setPKG_ID(String PKG_ID) { 
		this.PKG_ID = PKG_ID;
	}
	/** 
	 * 
	 * @return Returns the CLZZ_NM
	 */ 
	public String getCLZZ_NM() { 
		return this.CLZZ_NM;
	}
	/** 
	 * 
	 * @param CLZZ_NM the CLZZ_NM to set
	 */ 
	public void setCLZZ_NM(String CLZZ_NM) { 
		this.CLZZ_NM = CLZZ_NM;
	}
	/** 
	 * 
	 * @return Returns the CLZZ_KIND
	 */ 
	public String getCLZZ_KIND() { 
		return this.CLZZ_KIND;
	}
	/** 
	 * 
	 * @param CLZZ_KIND the CLZZ_KIND to set
	 */ 
	public void setCLZZ_KIND(String CLZZ_KIND) { 
		this.CLZZ_KIND = CLZZ_KIND;
	}
	/** 
	 * 
	 * @return Returns the RESOURCE_ID
	 */ 
	public String getRESOURCE_ID() { 
		return this.RESOURCE_ID;
	}
	/** 
	 * 
	 * @param RESOURCE_ID the RESOURCE_ID to set
	 */ 
	public void setRESOURCE_ID(String RESOURCE_ID) { 
		this.RESOURCE_ID = RESOURCE_ID;
	}
	/** 
	 * 
	 * @return Returns the CLZZ_INTF
	 */ 
	public String getCLZZ_INTF() { 
		return this.CLZZ_INTF;
	}
	/** 
	 * 
	 * @param CLZZ_INTF the CLZZ_INTF to set
	 */ 
	public void setCLZZ_INTF(String CLZZ_INTF) { 
		this.CLZZ_INTF = CLZZ_INTF;
	}
	/** 
	 * 
	 * @return Returns the INTF_ID_LIST
	 */ 
	public String getINTF_ID_LIST() { 
		return this.INTF_ID_LIST;
	}
	/** 
	 * 
	 * @param INTF_ID_LIST the INTF_ID_LIST to set
	 */ 
	public void setINTF_ID_LIST(String INTF_ID_LIST) { 
		this.INTF_ID_LIST = INTF_ID_LIST;
	}
	/** 
	 * 
	 * @return Returns the PARENT_CLZZ_ID
	 */ 
	public String getPARENT_CLZZ_ID() { 
		return this.PARENT_CLZZ_ID;
	}
	/** 
	 * 
	 * @param PARENT_CLZZ_ID the PARENT_CLZZ_ID to set
	 */ 
	public void setPARENT_CLZZ_ID(String PARENT_CLZZ_ID) { 
		this.PARENT_CLZZ_ID = PARENT_CLZZ_ID;
	}
	/** 
	 * 
	 * @return Returns the INTF_IMPL_CLZZ_ID_LIST
	 */ 
	public String getINTF_IMPL_CLZZ_ID_LIST() { 
		return this.INTF_IMPL_CLZZ_ID_LIST;
	}
	/** 
	 * 
	 * @param INTF_IMPL_CLZZ_ID_LIST the INTF_IMPL_CLZZ_ID_LIST to set
	 */ 
	public void setINTF_IMPL_CLZZ_ID_LIST(String INTF_IMPL_CLZZ_ID_LIST) { 
		this.INTF_IMPL_CLZZ_ID_LIST = INTF_IMPL_CLZZ_ID_LIST;
	}
	/** 
	 * 
	 * @return Returns the MEMBER_ALIAS_LIST
	 */ 
	public String getMEMBER_ALIAS_LIST() { 
		return this.MEMBER_ALIAS_LIST;
	}
	/** 
	 * 
	 * @param MEMBER_ALIAS_LIST the MEMBER_ALIAS_LIST to set
	 */ 
	public void setMEMBER_ALIAS_LIST(String MEMBER_ALIAS_LIST) { 
		this.MEMBER_ALIAS_LIST = MEMBER_ALIAS_LIST;
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
