
package net.dstone.boot.sample.cud.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name="SampleGroupRoleCudVo" ) 
public class SampleGroupRoleCudVo extends net.dstone.boot.common.biz.BaseVo{ 
	private String GROUP_ID; 
	private String ROLE_ID; 
	private String INPUT_DT; 
	/** 
	 * 
	 * @return Returns the GROUP_ID
	 */ 
	public String getGROUP_ID() { 
		return this.GROUP_ID;
	}
	/** 
	 * 
	 * @param GROUP_ID the GROUP_ID to set
	 */ 
	public void setGROUP_ID(String GROUP_ID) { 
		this.GROUP_ID = GROUP_ID;
	}
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
