package net.dstone.common.biz;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name="MemberVo" ) 
public class BaseVo implements java.io.Serializable{

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
	
}
