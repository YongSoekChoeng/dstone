
package net.dstone.boot.sample.cud.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name="SampleRoleCudVo" ) 
public class SampleRoleCudVo extends net.dstone.boot.common.biz.BaseVo{ 
	private String ROLE_ID; 
	private String ROLE_NAME; 
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
	 * @return Returns the ROLE_NAME
	 */ 
	public String getROLE_NAME() { 
		return this.ROLE_NAME;
	}
	/** 
	 * 
	 * @param ROLE_NAME the ROLE_NAME to set
	 */ 
	public void setROLE_NAME(String ROLE_NAME) { 
		this.ROLE_NAME = ROLE_NAME;
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
