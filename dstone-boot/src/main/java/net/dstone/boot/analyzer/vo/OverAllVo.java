
package net.dstone.boot.analyzer.vo;  
                      
import jakarta.xml.bind.annotation.XmlRootElement;
                      
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement( name="OverAllVo" ) 
public class OverAllVo extends net.dstone.common.biz.BaseVo { 

	@JsonProperty("RNUM") 
	private int RNUM; 

	@JsonProperty("ID") 
	private String ID; 
	
	@JsonProperty("SEQ") 
	private String SEQ; 
	@JsonProperty("SYS_ID") 
	private String SYS_ID; 
	@JsonProperty("UI_ID") 
	private String UI_ID; 
	@JsonProperty("UI_NM") 
	private String UI_NM; 
	@JsonProperty("BASIC_URL") 
	private String BASIC_URL; 

	@JsonProperty("FUNCTION_ID") 
	private String FUNCTION_ID; 
	@JsonProperty("FUNCTION_NAME") 
	private String FUNCTION_NAME; 
	@JsonProperty("CLASS_KIND") 
	private String CLASS_KIND; 

	@JsonProperty("FUNCTION_ID_1") 
	private String FUNCTION_ID_1; 
	@JsonProperty("DISPLAY_ID_1") 
	private String DISPLAY_ID_1; 
	@JsonProperty("FUNCTION_NAME_1") 
	private String FUNCTION_NAME_1; 
	@JsonProperty("CLASS_KIND_1") 
	private String CLASS_KIND_1; 
	@JsonProperty("CLASS_KIND_NM_1") 
	private String CLASS_KIND_NM_1; 
	
	@JsonProperty("FUNCTION_ID_2") 
	private String FUNCTION_ID_2; 
	@JsonProperty("DISPLAY_ID_2") 
	private String DISPLAY_ID_2; 
	@JsonProperty("FUNCTION_NAME_2") 
	private String FUNCTION_NAME_2; 
	@JsonProperty("CLASS_KIND_2") 
	private String CLASS_KIND_2; 
	@JsonProperty("CLASS_KIND_NM_2") 
	private String CLASS_KIND_NM_2; 

	@JsonProperty("FUNCTION_ID_3") 
	private String FUNCTION_ID_3; 
	@JsonProperty("DISPLAY_ID_3") 
	private String DISPLAY_ID_3; 
	@JsonProperty("FUNCTION_NAME_3") 
	private String FUNCTION_NAME_3; 
	@JsonProperty("CLASS_KIND_3") 
	private String CLASS_KIND_3; 
	@JsonProperty("CLASS_KIND_NM_3") 
	private String CLASS_KIND_NM_3; 

	@JsonProperty("FUNCTION_ID_4") 
	private String FUNCTION_ID_4; 
	@JsonProperty("DISPLAY_ID_4") 
	private String DISPLAY_ID_4; 
	@JsonProperty("FUNCTION_NAME_4") 
	private String FUNCTION_NAME_4; 
	@JsonProperty("CLASS_KIND_4") 
	private String CLASS_KIND_4; 
	@JsonProperty("CLASS_KIND_NM_4") 
	private String CLASS_KIND_NM_4; 

	@JsonProperty("FUNCTION_ID_5") 
	private String FUNCTION_ID_5; 
	@JsonProperty("DISPLAY_ID_5") 
	private String DISPLAY_ID_5; 
	@JsonProperty("FUNCTION_NAME_5") 
	private String FUNCTION_NAME_5; 
	@JsonProperty("CLASS_KIND_5") 
	private String CLASS_KIND_5; 
	@JsonProperty("CLASS_KIND_NM_5") 
	private String CLASS_KIND_NM_5; 

	@JsonProperty("FUNCTION_ID_6") 
	private String FUNCTION_ID_6; 
	@JsonProperty("DISPLAY_ID_6") 
	private String DISPLAY_ID_6; 
	@JsonProperty("FUNCTION_NAME_6") 
	private String FUNCTION_NAME_6; 
	@JsonProperty("CLASS_KIND_6") 
	private String CLASS_KIND_6; 
	@JsonProperty("CLASS_KIND_NM_6") 
	private String CLASS_KIND_NM_6; 

	@JsonProperty("FUNCTION_ID_7") 
	private String FUNCTION_ID_7; 
	@JsonProperty("DISPLAY_ID_7") 
	private String DISPLAY_ID_7; 
	@JsonProperty("FUNCTION_NAME_7") 
	private String FUNCTION_NAME_7; 
	@JsonProperty("CLASS_KIND_7") 
	private String CLASS_KIND_7; 
	@JsonProperty("CLASS_KIND_NM_7") 
	private String CLASS_KIND_NM_7; 

	@JsonProperty("FUNCTION_ID_8") 
	private String FUNCTION_ID_8; 
	@JsonProperty("DISPLAY_ID_8") 
	private String DISPLAY_ID_8; 
	@JsonProperty("FUNCTION_NAME_8") 
	private String FUNCTION_NAME_8; 
	@JsonProperty("CLASS_KIND_8") 
	private String CLASS_KIND_8; 
	@JsonProperty("CLASS_KIND_NM_8") 
	private String CLASS_KIND_NM_8; 

	@JsonProperty("FUNCTION_ID_9") 
	private String FUNCTION_ID_9; 
	@JsonProperty("DISPLAY_ID_9") 
	private String DISPLAY_ID_9; 
	@JsonProperty("FUNCTION_NAME_9") 
	private String FUNCTION_NAME_9; 
	@JsonProperty("CLASS_KIND_9") 
	private String CLASS_KIND_9; 
	@JsonProperty("CLASS_KIND_NM_9") 
	private String CLASS_KIND_NM_9; 

	@JsonProperty("FUNCTION_ID_10") 
	private String FUNCTION_ID_10; 
	@JsonProperty("DISPLAY_ID_10") 
	private String DISPLAY_ID_10; 
	@JsonProperty("FUNCTION_NAME_10") 
	private String FUNCTION_NAME_10; 
	@JsonProperty("CLASS_KIND_10") 
	private String CLASS_KIND_10; 
	@JsonProperty("CLASS_KIND_NM_10") 
	private String CLASS_KIND_NM_10; 
	
	@JsonProperty("CALL_TBL") 
	private String CALL_TBL; 
	@JsonProperty("WORKER_ID") 
	private String WORKER_ID; 
	@JsonProperty("LIKE_EQUAL") 
	private String LIKE_EQUAL; 
	
	@JsonProperty("LIMIT") 
	private int LIMIT; 


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
	
	public int getRNUM() {
		return RNUM;
	}
	public void setRNUM(int rNUM) {
		RNUM = rNUM;
	}
	public String getID() {
		return ID;
	}
	public void setID(String iD) {
		ID = iD;
	}
	public String getSEQ() {
		return SEQ;
	}
	public void setSEQ(String sEQ) {
		SEQ = sEQ;
	}
	public String getSYS_ID() {
		return SYS_ID;
	}
	public void setSYS_ID(String sYS_ID) {
		SYS_ID = sYS_ID;
	}
	public String getUI_ID() {
		return UI_ID;
	}
	public void setUI_ID(String uI_ID) {
		UI_ID = uI_ID;
	}
	public String getUI_NM() {
		return UI_NM;
	}
	public void setUI_NM(String uI_NM) {
		UI_NM = uI_NM;
	}
	public String getBASIC_URL() {
		return BASIC_URL;
	}
	public void setBASIC_URL(String bASIC_URL) {
		BASIC_URL = bASIC_URL;
	}
	public String getFUNCTION_ID() {
		return FUNCTION_ID;
	}
	public void setFUNCTION_ID(String fUNCTION_ID) {
		FUNCTION_ID = fUNCTION_ID;
	}
	public String getFUNCTION_NAME() {
		return FUNCTION_NAME;
	}
	public void setFUNCTION_NAME(String fUNCTION_NAME) {
		FUNCTION_NAME = fUNCTION_NAME;
	}
	public String getCLASS_KIND() {
		return CLASS_KIND;
	}
	public void setCLASS_KIND(String cLASS_KIND) {
		CLASS_KIND = cLASS_KIND;
	}
	public String getFUNCTION_ID_1() {
		return FUNCTION_ID_1;
	}
	public void setFUNCTION_ID_1(String fUNCTION_ID_1) {
		FUNCTION_ID_1 = fUNCTION_ID_1;
	}
	public String getDISPLAY_ID_1() {
		return DISPLAY_ID_1;
	}
	public void setDISPLAY_ID_1(String dISPLAY_ID_1) {
		DISPLAY_ID_1 = dISPLAY_ID_1;
	}
	public String getFUNCTION_NAME_1() {
		return FUNCTION_NAME_1;
	}
	public void setFUNCTION_NAME_1(String fUNCTION_NAME_1) {
		FUNCTION_NAME_1 = fUNCTION_NAME_1;
	}
	public String getCLASS_KIND_1() {
		return CLASS_KIND_1;
	}
	public void setCLASS_KIND_1(String cLASS_KIND_1) {
		CLASS_KIND_1 = cLASS_KIND_1;
	}
	public String getCLASS_KIND_NM_1() {
		return CLASS_KIND_NM_1;
	}
	public void setCLASS_KIND_NM_1(String cLASS_KIND_NM_1) {
		CLASS_KIND_NM_1 = cLASS_KIND_NM_1;
	}
	public String getFUNCTION_ID_2() {
		return FUNCTION_ID_2;
	}
	public void setFUNCTION_ID_2(String fUNCTION_ID_2) {
		FUNCTION_ID_2 = fUNCTION_ID_2;
	}
	public String getDISPLAY_ID_2() {
		return DISPLAY_ID_2;
	}
	public void setDISPLAY_ID_2(String dISPLAY_ID_2) {
		DISPLAY_ID_2 = dISPLAY_ID_2;
	}
	public String getFUNCTION_NAME_2() {
		return FUNCTION_NAME_2;
	}
	public void setFUNCTION_NAME_2(String fUNCTION_NAME_2) {
		FUNCTION_NAME_2 = fUNCTION_NAME_2;
	}
	public String getCLASS_KIND_2() {
		return CLASS_KIND_2;
	}
	public void setCLASS_KIND_2(String cLASS_KIND_2) {
		CLASS_KIND_2 = cLASS_KIND_2;
	}
	public String getCLASS_KIND_NM_2() {
		return CLASS_KIND_NM_2;
	}
	public void setCLASS_KIND_NM_2(String cLASS_KIND_NM_2) {
		CLASS_KIND_NM_2 = cLASS_KIND_NM_2;
	}
	public String getFUNCTION_ID_3() {
		return FUNCTION_ID_3;
	}
	public void setFUNCTION_ID_3(String fUNCTION_ID_3) {
		FUNCTION_ID_3 = fUNCTION_ID_3;
	}
	public String getDISPLAY_ID_3() {
		return DISPLAY_ID_3;
	}
	public void setDISPLAY_ID_3(String dISPLAY_ID_3) {
		DISPLAY_ID_3 = dISPLAY_ID_3;
	}
	public String getFUNCTION_NAME_3() {
		return FUNCTION_NAME_3;
	}
	public void setFUNCTION_NAME_3(String fUNCTION_NAME_3) {
		FUNCTION_NAME_3 = fUNCTION_NAME_3;
	}
	public String getCLASS_KIND_3() {
		return CLASS_KIND_3;
	}
	public void setCLASS_KIND_3(String cLASS_KIND_3) {
		CLASS_KIND_3 = cLASS_KIND_3;
	}
	public String getCLASS_KIND_NM_3() {
		return CLASS_KIND_NM_3;
	}
	public void setCLASS_KIND_NM_3(String cLASS_KIND_NM_3) {
		CLASS_KIND_NM_3 = cLASS_KIND_NM_3;
	}
	public String getFUNCTION_ID_4() {
		return FUNCTION_ID_4;
	}
	public void setFUNCTION_ID_4(String fUNCTION_ID_4) {
		FUNCTION_ID_4 = fUNCTION_ID_4;
	}
	public String getDISPLAY_ID_4() {
		return DISPLAY_ID_4;
	}
	public void setDISPLAY_ID_4(String dISPLAY_ID_4) {
		DISPLAY_ID_4 = dISPLAY_ID_4;
	}
	public String getFUNCTION_NAME_4() {
		return FUNCTION_NAME_4;
	}
	public void setFUNCTION_NAME_4(String fUNCTION_NAME_4) {
		FUNCTION_NAME_4 = fUNCTION_NAME_4;
	}
	public String getCLASS_KIND_4() {
		return CLASS_KIND_4;
	}
	public void setCLASS_KIND_4(String cLASS_KIND_4) {
		CLASS_KIND_4 = cLASS_KIND_4;
	}
	public String getCLASS_KIND_NM_4() {
		return CLASS_KIND_NM_4;
	}
	public void setCLASS_KIND_NM_4(String cLASS_KIND_NM_4) {
		CLASS_KIND_NM_4 = cLASS_KIND_NM_4;
	}
	public String getFUNCTION_ID_5() {
		return FUNCTION_ID_5;
	}
	public void setFUNCTION_ID_5(String fUNCTION_ID_5) {
		FUNCTION_ID_5 = fUNCTION_ID_5;
	}
	public String getDISPLAY_ID_5() {
		return DISPLAY_ID_5;
	}
	public void setDISPLAY_ID_5(String dISPLAY_ID_5) {
		DISPLAY_ID_5 = dISPLAY_ID_5;
	}
	public String getFUNCTION_NAME_5() {
		return FUNCTION_NAME_5;
	}
	public void setFUNCTION_NAME_5(String fUNCTION_NAME_5) {
		FUNCTION_NAME_5 = fUNCTION_NAME_5;
	}
	public String getCLASS_KIND_5() {
		return CLASS_KIND_5;
	}
	public void setCLASS_KIND_5(String cLASS_KIND_5) {
		CLASS_KIND_5 = cLASS_KIND_5;
	}
	public String getCLASS_KIND_NM_5() {
		return CLASS_KIND_NM_5;
	}
	public void setCLASS_KIND_NM_5(String cLASS_KIND_NM_5) {
		CLASS_KIND_NM_5 = cLASS_KIND_NM_5;
	}
	public String getFUNCTION_ID_6() {
		return FUNCTION_ID_6;
	}
	public void setFUNCTION_ID_6(String fUNCTION_ID_6) {
		FUNCTION_ID_6 = fUNCTION_ID_6;
	}
	public String getDISPLAY_ID_6() {
		return DISPLAY_ID_6;
	}
	public void setDISPLAY_ID_6(String dISPLAY_ID_6) {
		DISPLAY_ID_6 = dISPLAY_ID_6;
	}
	public String getFUNCTION_NAME_6() {
		return FUNCTION_NAME_6;
	}
	public void setFUNCTION_NAME_6(String fUNCTION_NAME_6) {
		FUNCTION_NAME_6 = fUNCTION_NAME_6;
	}
	public String getCLASS_KIND_6() {
		return CLASS_KIND_6;
	}
	public void setCLASS_KIND_6(String cLASS_KIND_6) {
		CLASS_KIND_6 = cLASS_KIND_6;
	}
	public String getCLASS_KIND_NM_6() {
		return CLASS_KIND_NM_6;
	}
	public void setCLASS_KIND_NM_6(String cLASS_KIND_NM_6) {
		CLASS_KIND_NM_6 = cLASS_KIND_NM_6;
	}
	public String getFUNCTION_ID_7() {
		return FUNCTION_ID_7;
	}
	public void setFUNCTION_ID_7(String fUNCTION_ID_7) {
		FUNCTION_ID_7 = fUNCTION_ID_7;
	}
	public String getDISPLAY_ID_7() {
		return DISPLAY_ID_7;
	}
	public void setDISPLAY_ID_7(String dISPLAY_ID_7) {
		DISPLAY_ID_7 = dISPLAY_ID_7;
	}
	public String getFUNCTION_NAME_7() {
		return FUNCTION_NAME_7;
	}
	public void setFUNCTION_NAME_7(String fUNCTION_NAME_7) {
		FUNCTION_NAME_7 = fUNCTION_NAME_7;
	}
	public String getCLASS_KIND_7() {
		return CLASS_KIND_7;
	}
	public void setCLASS_KIND_7(String cLASS_KIND_7) {
		CLASS_KIND_7 = cLASS_KIND_7;
	}
	public String getCLASS_KIND_NM_7() {
		return CLASS_KIND_NM_7;
	}
	public void setCLASS_KIND_NM_7(String cLASS_KIND_NM_7) {
		CLASS_KIND_NM_7 = cLASS_KIND_NM_7;
	}
	public String getFUNCTION_ID_8() {
		return FUNCTION_ID_8;
	}
	public void setFUNCTION_ID_8(String fUNCTION_ID_8) {
		FUNCTION_ID_8 = fUNCTION_ID_8;
	}
	public String getDISPLAY_ID_8() {
		return DISPLAY_ID_8;
	}
	public void setDISPLAY_ID_8(String dISPLAY_ID_8) {
		DISPLAY_ID_8 = dISPLAY_ID_8;
	}
	public String getFUNCTION_NAME_8() {
		return FUNCTION_NAME_8;
	}
	public void setFUNCTION_NAME_8(String fUNCTION_NAME_8) {
		FUNCTION_NAME_8 = fUNCTION_NAME_8;
	}
	public String getCLASS_KIND_8() {
		return CLASS_KIND_8;
	}
	public void setCLASS_KIND_8(String cLASS_KIND_8) {
		CLASS_KIND_8 = cLASS_KIND_8;
	}
	public String getCLASS_KIND_NM_8() {
		return CLASS_KIND_NM_8;
	}
	public void setCLASS_KIND_NM_8(String cLASS_KIND_NM_8) {
		CLASS_KIND_NM_8 = cLASS_KIND_NM_8;
	}
	public String getFUNCTION_ID_9() {
		return FUNCTION_ID_9;
	}
	public void setFUNCTION_ID_9(String fUNCTION_ID_9) {
		FUNCTION_ID_9 = fUNCTION_ID_9;
	}
	public String getDISPLAY_ID_9() {
		return DISPLAY_ID_9;
	}
	public void setDISPLAY_ID_9(String dISPLAY_ID_9) {
		DISPLAY_ID_9 = dISPLAY_ID_9;
	}
	public String getFUNCTION_NAME_9() {
		return FUNCTION_NAME_9;
	}
	public void setFUNCTION_NAME_9(String fUNCTION_NAME_9) {
		FUNCTION_NAME_9 = fUNCTION_NAME_9;
	}
	public String getCLASS_KIND_9() {
		return CLASS_KIND_9;
	}
	public void setCLASS_KIND_9(String cLASS_KIND_9) {
		CLASS_KIND_9 = cLASS_KIND_9;
	}
	public String getCLASS_KIND_NM_9() {
		return CLASS_KIND_NM_9;
	}
	public void setCLASS_KIND_NM_9(String cLASS_KIND_NM_9) {
		CLASS_KIND_NM_9 = cLASS_KIND_NM_9;
	}
	public String getFUNCTION_ID_10() {
		return FUNCTION_ID_10;
	}
	public void setFUNCTION_ID_10(String fUNCTION_ID_10) {
		FUNCTION_ID_10 = fUNCTION_ID_10;
	}
	public String getDISPLAY_ID_10() {
		return DISPLAY_ID_10;
	}
	public void setDISPLAY_ID_10(String dISPLAY_ID_10) {
		DISPLAY_ID_10 = dISPLAY_ID_10;
	}
	public String getFUNCTION_NAME_10() {
		return FUNCTION_NAME_10;
	}
	public void setFUNCTION_NAME_10(String fUNCTION_NAME_10) {
		FUNCTION_NAME_10 = fUNCTION_NAME_10;
	}
	public String getCLASS_KIND_10() {
		return CLASS_KIND_10;
	}
	public void setCLASS_KIND_10(String cLASS_KIND_10) {
		CLASS_KIND_10 = cLASS_KIND_10;
	}
	public String getCLASS_KIND_NM_10() {
		return CLASS_KIND_NM_10;
	}
	public void setCLASS_KIND_NM_10(String cLASS_KIND_NM_10) {
		CLASS_KIND_NM_10 = cLASS_KIND_NM_10;
	}
	public String getCALL_TBL() {
		return CALL_TBL;
	}
	public void setCALL_TBL(String cALL_TBL) {
		CALL_TBL = cALL_TBL;
	}
	public String getWORKER_ID() {
		return WORKER_ID;
	}
	public void setWORKER_ID(String wORKER_ID) {
		WORKER_ID = wORKER_ID;
	}
	public int getLIMIT() {
		return LIMIT;
	}
	public void setLIMIT(int lIMIT) {
		LIMIT = lIMIT;
	}
	@Override
	public String toString() {
		return "OverAllVo [RNUM=" + RNUM + ", ID=" + ID + ", SEQ=" + SEQ + ", SYS_ID=" + SYS_ID + ", UI_ID=" + UI_ID
				+ ", UI_NM=" + UI_NM + ", BASIC_URL=" + BASIC_URL + ", FUNCTION_ID=" + FUNCTION_ID + ", FUNCTION_NAME="
				+ FUNCTION_NAME + ", CLASS_KIND=" + CLASS_KIND + ", FUNCTION_ID_1=" + FUNCTION_ID_1 + ", DISPLAY_ID_1="
				+ DISPLAY_ID_1 + ", FUNCTION_NAME_1=" + FUNCTION_NAME_1 + ", CLASS_KIND_1=" + CLASS_KIND_1
				+ ", CLASS_KIND_NM_1=" + CLASS_KIND_NM_1 + ", FUNCTION_ID_2=" + FUNCTION_ID_2 + ", DISPLAY_ID_2="
				+ DISPLAY_ID_2 + ", FUNCTION_NAME_2=" + FUNCTION_NAME_2 + ", CLASS_KIND_2=" + CLASS_KIND_2
				+ ", CLASS_KIND_NM_2=" + CLASS_KIND_NM_2 + ", FUNCTION_ID_3=" + FUNCTION_ID_3 + ", DISPLAY_ID_3="
				+ DISPLAY_ID_3 + ", FUNCTION_NAME_3=" + FUNCTION_NAME_3 + ", CLASS_KIND_3=" + CLASS_KIND_3
				+ ", CLASS_KIND_NM_3=" + CLASS_KIND_NM_3 + ", FUNCTION_ID_4=" + FUNCTION_ID_4 + ", DISPLAY_ID_4="
				+ DISPLAY_ID_4 + ", FUNCTION_NAME_4=" + FUNCTION_NAME_4 + ", CLASS_KIND_4=" + CLASS_KIND_4
				+ ", CLASS_KIND_NM_4=" + CLASS_KIND_NM_4 + ", FUNCTION_ID_5=" + FUNCTION_ID_5 + ", DISPLAY_ID_5="
				+ DISPLAY_ID_5 + ", FUNCTION_NAME_5=" + FUNCTION_NAME_5 + ", CLASS_KIND_5=" + CLASS_KIND_5
				+ ", CLASS_KIND_NM_5=" + CLASS_KIND_NM_5 + ", FUNCTION_ID_6=" + FUNCTION_ID_6 + ", DISPLAY_ID_6="
				+ DISPLAY_ID_6 + ", FUNCTION_NAME_6=" + FUNCTION_NAME_6 + ", CLASS_KIND_6=" + CLASS_KIND_6
				+ ", CLASS_KIND_NM_6=" + CLASS_KIND_NM_6 + ", FUNCTION_ID_7=" + FUNCTION_ID_7 + ", DISPLAY_ID_7="
				+ DISPLAY_ID_7 + ", FUNCTION_NAME_7=" + FUNCTION_NAME_7 + ", CLASS_KIND_7=" + CLASS_KIND_7
				+ ", CLASS_KIND_NM_7=" + CLASS_KIND_NM_7 + ", FUNCTION_ID_8=" + FUNCTION_ID_8 + ", DISPLAY_ID_8="
				+ DISPLAY_ID_8 + ", FUNCTION_NAME_8=" + FUNCTION_NAME_8 + ", CLASS_KIND_8=" + CLASS_KIND_8
				+ ", CLASS_KIND_NM_8=" + CLASS_KIND_NM_8 + ", FUNCTION_ID_9=" + FUNCTION_ID_9 + ", DISPLAY_ID_9="
				+ DISPLAY_ID_9 + ", FUNCTION_NAME_9=" + FUNCTION_NAME_9 + ", CLASS_KIND_9=" + CLASS_KIND_9
				+ ", CLASS_KIND_NM_9=" + CLASS_KIND_NM_9 + ", FUNCTION_ID_10=" + FUNCTION_ID_10 + ", DISPLAY_ID_10="
				+ DISPLAY_ID_10 + ", FUNCTION_NAME_10=" + FUNCTION_NAME_10 + ", CLASS_KIND_10=" + CLASS_KIND_10
				+ ", CLASS_KIND_NM_10=" + CLASS_KIND_NM_10 + ", CALL_TBL=" + CALL_TBL + ", WORKER_ID=" + WORKER_ID
				+ ", LIKE_EQUAL=" + LIKE_EQUAL + ", LIMIT=" + LIMIT + ", PAGE_SIZE=" + PAGE_SIZE + ", PAGE_NUM="
				+ PAGE_NUM + ", INT_FROM=" + INT_FROM + ", INT_TO=" + INT_TO + "]";
	}
	public String getLIKE_EQUAL() {
		return LIKE_EQUAL;
	}
	public void setLIKE_EQUAL(String lIKE_EQUAL) {
		LIKE_EQUAL = lIKE_EQUAL;
	}


}                     
