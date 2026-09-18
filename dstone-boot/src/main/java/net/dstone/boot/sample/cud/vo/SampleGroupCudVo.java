
package net.dstone.boot.sample.cud.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name="SampleGroupCudVo" ) 
public class SampleGroupCudVo extends net.dstone.boot.common.biz.BaseVo{ 
	private String GROUP_ID; 
	private String GROUP_NAME; 
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
	 * @return Returns the GROUP_NAME
	 */ 
	public String getGROUP_NAME() { 
		return this.GROUP_NAME;
	}
	/** 
	 * 
	 * @param GROUP_NAME the GROUP_NAME to set
	 */ 
	public void setGROUP_NAME(String GROUP_NAME) { 
		this.GROUP_NAME = GROUP_NAME;
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
