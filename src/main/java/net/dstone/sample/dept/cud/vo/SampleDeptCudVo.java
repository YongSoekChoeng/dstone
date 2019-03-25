
package net.dstone.sample.dept.cud.vo;  
                      
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name="SampleDeptCudVo" ) 
public class SampleDeptCudVo extends net.dstone.common.biz.BaseVo implements java.io.Serializable { 
	private String DEPT_ID; 
	private String DEPT_NAME; 
	private String INPUT_DT; 
	/** 
	 * 
	 * @return Returns the DEPT_ID
	 */ 
	public String getDEPT_ID() { 
		return this.DEPT_ID;
	}
	/** 
	 * 
	 * @param DEPT_ID the DEPT_ID to set
	 */ 
	public void setDEPT_ID(String DEPT_ID) { 
		this.DEPT_ID = DEPT_ID;
	}
	/** 
	 * 
	 * @return Returns the DEPT_NAME
	 */ 
	public String getDEPT_NAME() { 
		return this.DEPT_NAME;
	}
	/** 
	 * 
	 * @param DEPT_NAME the DEPT_NAME to set
	 */ 
	public void setDEPT_NAME(String DEPT_NAME) { 
		this.DEPT_NAME = DEPT_NAME;
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
