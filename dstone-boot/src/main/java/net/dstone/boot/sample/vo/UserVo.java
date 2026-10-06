
package net.dstone.boot.sample.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;
                      
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement( name="UserVo" ) 
public class UserVo extends net.dstone.boot.common.biz.BaseVo { 
	@JsonProperty("GROUP_ID") 
	private String GROUP_ID; 
	@JsonProperty("USER_ID") 
	private String USER_ID; 
	@JsonProperty("USER_PW") 
	private String USER_PW; 
	@JsonProperty("MEMBER_NAME") 
	private String MEMBER_NAME; 
	@JsonProperty("AGE") 
	private String AGE; 
	@JsonProperty("DUTY") 
	private String DUTY; 
	@JsonProperty("REGION") 
	private String REGION; 
	@JsonProperty("ADDRESS") 
	private String ADDRESS; 
	@JsonProperty("ADDRESS_DTL") 
	private String ADDRESS_DTL; 
	@JsonProperty("JUMINNO") 
	private String JUMINNO; 
	@JsonProperty("GENDER") 
	private String GENDER; 
	@JsonProperty("TEL") 
	private String TEL; 
	@JsonProperty("HP") 
	private String HP; 
	@JsonProperty("EMAIL") 
	private String EMAIL; 
	@JsonProperty("INPUT_DT") 
	private String INPUT_DT; 
	@JsonProperty("UPDATE_DT") 
	private String UPDATE_DT; 
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
	 * @return Returns the USER_ID
	 */ 
	public String getUSER_ID() { 
		return this.USER_ID;
	}
	/** 
	 * 
	 * @param USER_ID the USER_ID to set
	 */ 
	public void setUSER_ID(String USER_ID) { 
		this.USER_ID = USER_ID;
	}
	/** 
	 * 
	 * @return Returns the USER_PW
	 */ 
	public String getUSER_PW() { 
		return this.USER_PW;
	}
	/** 
	 * 
	 * @param USER_PW the USER_PW to set
	 */ 
	public void setUSER_PW(String USER_PW) { 
		this.USER_PW = USER_PW;
	}
	/** 
	 * 
	 * @return Returns the MEMBER_NAME
	 */ 
	public String getMEMBER_NAME() { 
		return this.MEMBER_NAME;
	}
	/** 
	 * 
	 * @param MEMBER_NAME the MEMBER_NAME to set
	 */ 
	public void setMEMBER_NAME(String MEMBER_NAME) { 
		this.MEMBER_NAME = MEMBER_NAME;
	}
	/** 
	 * 
	 * @return Returns the AGE
	 */ 
	public String getAGE() { 
		return this.AGE;
	}
	/** 
	 * 
	 * @param AGE the AGE to set
	 */ 
	public void setAGE(String AGE) { 
		this.AGE = AGE;
	}
	/** 
	 * 
	 * @return Returns the DUTY
	 */ 
	public String getDUTY() { 
		return this.DUTY;
	}
	/** 
	 * 
	 * @param DUTY the DUTY to set
	 */ 
	public void setDUTY(String DUTY) { 
		this.DUTY = DUTY;
	}
	/** 
	 * 
	 * @return Returns the REGION
	 */ 
	public String getREGION() { 
		return this.REGION;
	}
	/** 
	 * 
	 * @param REGION the REGION to set
	 */ 
	public void setREGION(String REGION) { 
		this.REGION = REGION;
	}
	/** 
	 * 
	 * @return Returns the ADDRESS
	 */ 
	public String getADDRESS() { 
		return this.ADDRESS;
	}
	/** 
	 * 
	 * @param ADDRESS the ADDRESS to set
	 */ 
	public void setADDRESS(String ADDRESS) { 
		this.ADDRESS = ADDRESS;
	}
	/** 
	 * 
	 * @return Returns the ADDRESS_DTL
	 */ 
	public String getADDRESS_DTL() { 
		return this.ADDRESS_DTL;
	}
	/** 
	 * 
	 * @param ADDRESS_DTL the ADDRESS_DTL to set
	 */ 
	public void setADDRESS_DTL(String ADDRESS_DTL) { 
		this.ADDRESS_DTL = ADDRESS_DTL;
	}
	/** 
	 * 
	 * @return Returns the JUMINNO
	 */ 
	public String getJUMINNO() { 
		return this.JUMINNO;
	}
	/** 
	 * 
	 * @param JUMINNO the JUMINNO to set
	 */ 
	public void setJUMINNO(String JUMINNO) { 
		this.JUMINNO = JUMINNO;
	}
	/** 
	 * 
	 * @return Returns the GENDER
	 */ 
	public String getGENDER() { 
		return this.GENDER;
	}
	/** 
	 * 
	 * @param GENDER the GENDER to set
	 */ 
	public void setGENDER(String GENDER) { 
		this.GENDER = GENDER;
	}
	/** 
	 * 
	 * @return Returns the TEL
	 */ 
	public String getTEL() { 
		return this.TEL;
	}
	/** 
	 * 
	 * @param TEL the TEL to set
	 */ 
	public void setTEL(String TEL) { 
		this.TEL = TEL;
	}
	/** 
	 * 
	 * @return Returns the HP
	 */ 
	public String getHP() { 
		return this.HP;
	}
	/** 
	 * 
	 * @param HP the HP to set
	 */ 
	public void setHP(String HP) { 
		this.HP = HP;
	}
	/** 
	 * 
	 * @return Returns the EMAIL
	 */ 
	public String getEMAIL() { 
		return this.EMAIL;
	}
	/** 
	 * 
	 * @param EMAIL the EMAIL to set
	 */ 
	public void setEMAIL(String EMAIL) { 
		this.EMAIL = EMAIL;
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
	/** 
	 * 
	 * @return Returns the UPDATE_DT
	 */ 
	public String getUPDATE_DT() { 
		return this.UPDATE_DT;
	}
	/** 
	 * 
	 * @param UPDATE_DT the UPDATE_DT to set
	 */ 
	public void setUPDATE_DT(String UPDATE_DT) { 
		this.UPDATE_DT = UPDATE_DT;
	}
	/*** 페이징용 멤버 시작 ***/
	int PAGE_SIZE;
	int PAGE_NUM;
	int INT_FROM;
	int INT_TO;
	public int getPAGE_SIZE() { 
		return PAGE_SIZE; 
	} 
	public void setPAGE_SIZE(int pAGE_SIZE) { 
		PAGE_SIZE = pAGE_SIZE; 
	} 
	public int getPAGE_NUM() { 
		return PAGE_NUM; 
	} 
	public void setPAGE_NUM(int pAGE_NUM) { 
		PAGE_NUM = pAGE_NUM; 
	} 
	public int getINT_FROM() { 
		return INT_FROM; 
	} 
	public void setINT_FROM(int iNT_FROM) { 
		INT_FROM = iNT_FROM; 
	} 
	public int getINT_TO() { 
		return INT_TO; 
	} 
	public void setINT_TO(int iNT_TO) { 
		INT_TO = iNT_TO; 
	} 
	/*** 페이징용 멤버 끝 ***/
	@Override
	public String toString() {
		return "UserVo [GROUP_ID=" + GROUP_ID + ", USER_ID=" + USER_ID + ", USER_PW=" + USER_PW + ", MEMBER_NAME="
				+ MEMBER_NAME + ", AGE=" + AGE + ", DUTY=" + DUTY + ", REGION=" + REGION + ", ADDRESS=" + ADDRESS
				+ ", ADDRESS_DTL=" + ADDRESS_DTL + ", JUMINNO=" + JUMINNO + ", GENDER=" + GENDER + ", TEL=" + TEL
				+ ", HP=" + HP + ", EMAIL=" + EMAIL + ", INPUT_DT=" + INPUT_DT + ", UPDATE_DT=" + UPDATE_DT
				+ ", PAGE_SIZE=" + PAGE_SIZE + ", PAGE_NUM=" + PAGE_NUM + ", INT_FROM=" + INT_FROM + ", INT_TO="
				+ INT_TO + "]";
	}
}                     
