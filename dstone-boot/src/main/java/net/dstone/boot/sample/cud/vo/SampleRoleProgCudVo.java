
package net.dstone.boot.sample.cud.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name="SampleRoleProgCudVo" ) 
public class SampleRoleProgCudVo extends net.dstone.boot.common.biz.BaseVo{ 
	private String ROLE_ID; 
	private String PROG_ID; 
	private String PROG_NAME; 
	private String PROG_URL; 
	private String INPUT_DT; 
	/** 
	 * 
	 * @return Returns the ROLE_ID
	 */ 
	public String getROLE_ID() { 
		return this.ROLE_ID;
	}
	/** 
	 * 
	 * @param ROLE_ID the ROLE_ID to set
	 */ 
	public void setROLE_ID(String ROLE_ID) { 
		this.ROLE_ID = ROLE_ID;
	}
	/** 
	 * 
	 * @return Returns the PROG_ID
	 */ 
	public String getPROG_ID() { 
		return this.PROG_ID;
	}
	/** 
	 * 
	 * @param PROG_ID the PROG_ID to set
	 */ 
	public void setPROG_ID(String PROG_ID) { 
		this.PROG_ID = PROG_ID;
	}
	/** 
	 * 
	 * @return Returns the PROG_NAME
	 */ 
	public String getPROG_NAME() { 
		return this.PROG_NAME;
	}
	/** 
	 * 
	 * @param PROG_NAME the PROG_NAME to set
	 */ 
	public void setPROG_NAME(String PROG_NAME) { 
		this.PROG_NAME = PROG_NAME;
	}
	/** 
	 * 
	 * @return Returns the PROG_URL
	 */ 
	public String getPROG_URL() { 
		return this.PROG_URL;
	}
	/** 
	 * 
	 * @param PROG_URL the PROG_URL to set
	 */ 
	public void setPROG_URL(String PROG_URL) { 
		this.PROG_URL = PROG_URL;
	}
	/** 
	 * 
	 * @return Returns the INPUT_DT
	 */ 
	public String getINPUT_DT() { 
		return this.INPUT_DT;
	}
	/** 
	 * 
	 * @param INPUT_DT the INPUT_DT to set
	 */ 
	public void setINPUT_DT(String INPUT_DT) { 
		this.INPUT_DT = INPUT_DT;
	}
}                     
