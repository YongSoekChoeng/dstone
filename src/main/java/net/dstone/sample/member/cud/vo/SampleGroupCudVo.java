
package net.dstone.sample.member.cud.vo;  
                      
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name="SampleGroupCudVo" ) 
public class SampleGroupCudVo extends net.dstone.common.biz.BaseVo implements java.io.Serializable { 
	private String GROUP_ID; 
	private String NAME; 
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
	 * @return Returns the NAME
	 */ 
	public String getNAME() { 
		return this.NAME;
	}
	/** 
	 * 
	 * @param NAME the NAME to set
	 */ 
	public void setNAME(String NAME) { 
		this.NAME = NAME;
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
