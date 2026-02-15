
package net.dstone.boot.analyzer.vo;  
                      
import java.util.ArrayList;

import jakarta.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement( name="SysVo" ) 
public class SysVo extends net.dstone.common.biz.BaseVo { 
	@JsonProperty("SYS_ID") 
	private String SYS_ID; 
	@JsonProperty("SYS_NM") 
	private String SYS_NM; 
	@JsonProperty("CONF_FILE_PATH") 
	private String CONF_FILE_PATH; 
	@JsonProperty("APP_ROOT_PATH") 
	private String APP_ROOT_PATH; 
	@JsonProperty("APP_SRC_PATH") 
	private String APP_SRC_PATH; 
	@JsonProperty("APP_WEB_PATH") 
	private String APP_WEB_PATH; 
	@JsonProperty("APP_SQL_PATH") 
	private String APP_SQL_PATH; 
	@JsonProperty("WRITE_PATH") 
	private String WRITE_PATH; 
	@JsonProperty("SAVE_FILE_NAME") 
	private String SAVE_FILE_NAME; 
	@JsonProperty("DBID") 
	private String DBID; 
	@JsonProperty("IS_TABLE_LIST_FROM_DB") 
	private String IS_TABLE_LIST_FROM_DB; 
	@JsonProperty("TABLE_NAME_LIKE_STR") 
	private String TABLE_NAME_LIKE_STR; 
	@JsonProperty("TABLE_LIST_FILE_NAME") 
	private String TABLE_LIST_FILE_NAME; 
	@JsonProperty("IS_SAVE_TO_DB") 
	private String IS_SAVE_TO_DB; 
	@JsonProperty("APP_JDK_HOME") 
	private String APP_JDK_HOME; 
	@JsonProperty("APP_CLASSPATH") 
	private String APP_CLASSPATH; 
	@JsonProperty("WORKER_THREAD_KIND") 
	private String WORKER_THREAD_KIND; 
	@JsonProperty("WORKER_THREAD_NUM") 
	private String WORKER_THREAD_NUM; 

	@JsonProperty("INCLUDE_PACKAGE_ROOT") 
	private ArrayList<String> INCLUDE_PACKAGE_ROOT; 
	@JsonProperty("EXCLUDE_PACKAGE_PATTERN") 
	private ArrayList<String> EXCLUDE_PACKAGE_PATTERN; 
	
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
	 * @return Returns the SYS_NM
	 */ 
	public String getSYS_NM() { 
		return this.SYS_NM;
	}
	/** 
	 * 
	 * @param SYS_NM the SYS_NM to set
	 */ 
	public void setSYS_NM(String SYS_NM) { 
		this.SYS_NM = SYS_NM;
	}
	/** 
	 * 
	 * @return Returns the CONF_FILE_PATH
	 */ 
	public String getCONF_FILE_PATH() { 
		return this.CONF_FILE_PATH;
	}
	/** 
	 * 
	 * @param CONF_FILE_PATH the CONF_FILE_PATH to set
	 */ 
	public void setCONF_FILE_PATH(String CONF_FILE_PATH) { 
		this.CONF_FILE_PATH = CONF_FILE_PATH;
	}
	/** 
	 * 
	 * @return Returns the APP_ROOT_PATH
	 */ 
	public String getAPP_ROOT_PATH() { 
		return this.APP_ROOT_PATH;
	}
	/** 
	 * 
	 * @param APP_ROOT_PATH the APP_ROOT_PATH to set
	 */ 
	public void setAPP_ROOT_PATH(String APP_ROOT_PATH) { 
		this.APP_ROOT_PATH = APP_ROOT_PATH;
	}
	/** 
	 * 
	 * @return Returns the APP_SRC_PATH
	 */ 
	public String getAPP_SRC_PATH() { 
		return this.APP_SRC_PATH;
	}
	/** 
	 * 
	 * @param APP_SRC_PATH the APP_SRC_PATH to set
	 */ 
	public void setAPP_SRC_PATH(String APP_SRC_PATH) { 
		this.APP_SRC_PATH = APP_SRC_PATH;
	}
	/** 
	 * 
	 * @return Returns the APP_SQL_PATH
	 */ 
	public String getAPP_SQL_PATH() { 
		return this.APP_SQL_PATH;
	}
	/** 
	 * 
	 * @param APP_SQL_PATH the APP_SQL_PATH to set
	 */ 
	public void setAPP_SQL_PATH(String APP_SQL_PATH) { 
		this.APP_SQL_PATH = APP_SQL_PATH;
	}
	/** 
	 * 
	 * @return Returns the APP_WEB_PATH
	 */ 
	public String getAPP_WEB_PATH() { 
		return this.APP_WEB_PATH;
	}
	/** 
	 * 
	 * @param APP_WEB_PATH the APP_WEB_PATH to set
	 */ 
	public void setAPP_WEB_PATH(String APP_WEB_PATH) { 
		this.APP_WEB_PATH = APP_WEB_PATH;
	}
	/** 
	 * 
	 * @return Returns the WRITE_PATH
	 */ 
	public String getWRITE_PATH() { 
		return this.WRITE_PATH;
	}
	/** 
	 * 
	 * @param WRITE_PATH the WRITE_PATH to set
	 */ 
	public void setWRITE_PATH(String WRITE_PATH) { 
		this.WRITE_PATH = WRITE_PATH;
	}
	/** 
	 * 
	 * @return Returns the SAVE_FILE_NAME
	 */ 
	public String getSAVE_FILE_NAME() { 
		return this.SAVE_FILE_NAME;
	}
	/** 
	 * 
	 * @param SAVE_FILE_NAME the SAVE_FILE_NAME to set
	 */ 
	public void setSAVE_FILE_NAME(String SAVE_FILE_NAME) { 
		this.SAVE_FILE_NAME = SAVE_FILE_NAME;
	}
	/** 
	 * 
	 * @return Returns the DBID
	 */ 
	public String getDBID() { 
		return this.DBID;
	}
	/** 
	 * 
	 * @param DBID the DBID to set
	 */ 
	public void setDBID(String DBID) { 
		this.DBID = DBID;
	}
	/** 
	 * 
	 * @return Returns the IS_TABLE_LIST_FROM_DB
	 */ 
	public String getIS_TABLE_LIST_FROM_DB() { 
		return this.IS_TABLE_LIST_FROM_DB;
	}
	/** 
	 * 
	 * @param IS_TABLE_LIST_FROM_DB the IS_TABLE_LIST_FROM_DB to set
	 */ 
	public void setIS_TABLE_LIST_FROM_DB(String IS_TABLE_LIST_FROM_DB) { 
		this.IS_TABLE_LIST_FROM_DB = IS_TABLE_LIST_FROM_DB;
	}
	/** 
	 * 
	 * @return Returns the TABLE_NAME_LIKE_STR
	 */ 
	public String getTABLE_NAME_LIKE_STR() { 
		return this.TABLE_NAME_LIKE_STR;
	}
	/** 
	 * 
	 * @param TABLE_NAME_LIKE_STR the TABLE_NAME_LIKE_STR to set
	 */ 
	public void setTABLE_NAME_LIKE_STR(String TABLE_NAME_LIKE_STR) { 
		this.TABLE_NAME_LIKE_STR = TABLE_NAME_LIKE_STR;
	}
	/** 
	 * 
	 * @return Returns the TABLE_LIST_FILE_NAME
	 */ 
	public String getTABLE_LIST_FILE_NAME() { 
		return this.TABLE_LIST_FILE_NAME;
	}
	/** 
	 * 
	 * @param TABLE_LIST_FILE_NAME the TABLE_LIST_FILE_NAME to set
	 */ 
	public void setTABLE_LIST_FILE_NAME(String TABLE_LIST_FILE_NAME) { 
		this.TABLE_LIST_FILE_NAME = TABLE_LIST_FILE_NAME;
	}
	/** 
	 * 
	 * @return Returns the IS_SAVE_TO_DB
	 */ 
	public String getIS_SAVE_TO_DB() { 
		return this.IS_SAVE_TO_DB;
	}
	/** 
	 * 
	 * @param IS_SAVE_TO_DB the IS_SAVE_TO_DB to set
	 */ 
	public void setIS_SAVE_TO_DB(String IS_SAVE_TO_DB) { 
		this.IS_SAVE_TO_DB = IS_SAVE_TO_DB;
	}
	/** 
	 * 
	 * @return Returns the APP_JDK_HOME
	 */ 
	public String getAPP_JDK_HOME() { 
		return this.APP_JDK_HOME;
	}
	/** 
	 * 
	 * @param APP_JDK_HOME the APP_JDK_HOME to set
	 */ 
	public void setAPP_JDK_HOME(String APP_JDK_HOME) { 
		this.APP_JDK_HOME = APP_JDK_HOME;
	}
	/** 
	 * 
	 * @return Returns the APP_CLASSPATH
	 */ 
	public String getAPP_CLASSPATH() { 
		return this.APP_CLASSPATH;
	}
	/** 
	 * 
	 * @param APP_CLASSPATH the APP_CLASSPATH to set
	 */ 
	public void setAPP_CLASSPATH(String APP_CLASSPATH) { 
		this.APP_CLASSPATH = APP_CLASSPATH;
	}
	/** 
	 * 
	 * @return Returns the WORKER_THREAD_KIND
	 */ 
	public String getWORKER_THREAD_KIND() { 
		return this.WORKER_THREAD_KIND;
	}
	/** 
	 * 
	 * @param WORKER_THREAD_KIND the WORKER_THREAD_KIND to set
	 */ 
	public void setWORKER_THREAD_KIND(String WORKER_THREAD_KIND) { 
		this.WORKER_THREAD_KIND = WORKER_THREAD_KIND;
	}
	/** 
	 * 
	 * @return Returns the WORKER_THREAD_NUM
	 */ 
	public String getWORKER_THREAD_NUM() { 
		return this.WORKER_THREAD_NUM;
	}
	/** 
	 * 
	 * @param WORKER_THREAD_NUM the WORKER_THREAD_NUM to set
	 */ 
	public void setWORKER_THREAD_NUM(String WORKER_THREAD_NUM) { 
		this.WORKER_THREAD_NUM = WORKER_THREAD_NUM;
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

	public ArrayList<String> getINCLUDE_PACKAGE_ROOT() {
		return INCLUDE_PACKAGE_ROOT;
	}
	public void setINCLUDE_PACKAGE_ROOT(ArrayList<String> iNCLUDE_PACKAGE_ROOT) {
		INCLUDE_PACKAGE_ROOT = iNCLUDE_PACKAGE_ROOT;
	}
	public ArrayList<String> getEXCLUDE_PACKAGE_PATTERN() {
		return EXCLUDE_PACKAGE_PATTERN;
	}
	public void setEXCLUDE_PACKAGE_PATTERN(ArrayList<String> eXCLUDE_PACKAGE_PATTERN) {
		EXCLUDE_PACKAGE_PATTERN = eXCLUDE_PACKAGE_PATTERN;
	}
	
}                     
