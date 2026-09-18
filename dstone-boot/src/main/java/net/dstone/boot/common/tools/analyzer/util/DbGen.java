package net.dstone.boot.common.tools.analyzer.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.tools.analyzer.vo.ClzzVo;
import net.dstone.boot.common.tools.analyzer.vo.MtdVo;
import net.dstone.boot.common.tools.analyzer.vo.SysVo;
import net.dstone.boot.common.tools.analyzer.vo.UiVo;
import net.dstone.common.utils.DataSet;
import net.dstone.common.utils.DbUtil;
import net.dstone.common.utils.DbUtil.LoggableStatement;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;

public class DbGen {

	public static int FUNC_DEPTH_CNT = 10; 
	
	public static class DDL {
		
		public static StringBuffer MYSQL_CREATE = new StringBuffer();
		public static StringBuffer ORACLE_CREATE = new StringBuffer();

		public static StringBuffer MYSQL_FUNCTION = new StringBuffer();
		public static StringBuffer ORACLE_FUNCTION = new StringBuffer();

		public static StringBuffer DROP = new StringBuffer();
		
		static {

			/* <시스템-TB_SYS> */
			MYSQL_CREATE.append("CREATE TABLE TB_SYS ( ").append("\n");
			MYSQL_CREATE.append("  SYS_ID VARCHAR(20) NOT NULL COMMENT '시스템ID', ").append("\n");
			MYSQL_CREATE.append("  SYS_NM VARCHAR(200) COMMENT '시스템명', ").append("\n");
			MYSQL_CREATE.append("  CONF_FILE_PATH VARCHAR(500) NOT NULL COMMENT '설정파일경로', ").append("\n");
			MYSQL_CREATE.append("  APP_ROOT_PATH VARCHAR(500) NOT NULL COMMENT '어플리케이션루트', ").append("\n");
			MYSQL_CREATE.append("  APP_SRC_PATH VARCHAR(4000) NOT NULL COMMENT '어플리케이션서버소스루트', ").append("\n");
			MYSQL_CREATE.append("  APP_WEB_PATH VARCHAR(4000) NOT NULL COMMENT '어플리케이션웹소스루트', ").append("\n");
			MYSQL_CREATE.append("  APP_SQL_PATH VARCHAR(4000) NOT NULL COMMENT '어플리케이션쿼리소스루트', ").append("\n");
			MYSQL_CREATE.append("  WRITE_PATH VARCHAR(500) COMMENT '분석결과생성경로', ").append("\n");
			MYSQL_CREATE.append("  SAVE_FILE_NAME VARCHAR(500) COMMENT '분석결과저장파일명', ").append("\n");
			MYSQL_CREATE.append("  DBID VARCHAR(10) COMMENT 'DBID', ").append("\n");
			MYSQL_CREATE.append("  IS_TABLE_LIST_FROM_DB VARCHAR(10) COMMENT '테이블목록을DB로부터읽어올지여부', ").append("\n");
			MYSQL_CREATE.append("  TABLE_NAME_LIKE_STR VARCHAR(500) COMMENT '테이블명을DB로부터읽어올때적용할프리픽스', ").append("\n");
			MYSQL_CREATE.append("  TABLE_LIST_FILE_NAME VARCHAR(500) COMMENT '테이블목록정보파일명', ").append("\n");
			MYSQL_CREATE.append("  IS_SAVE_TO_DB VARCHAR(10) COMMENT '작업결과를DB에저장할지여부', ").append("\n");
			MYSQL_CREATE.append("  APP_JDK_HOME VARCHAR(200) COMMENT '분석대상어플리케이션JDK홈', ").append("\n");
			MYSQL_CREATE.append("  APP_CLASSPATH TEXT COMMENT '분석대상어플리케이션클래스패스', ").append("\n");
			MYSQL_CREATE.append("  WORKER_THREAD_KIND VARCHAR(2) COMMENT '분석작업을진행할쓰레드핸들러종류', ").append("\n");
			MYSQL_CREATE.append("  WORKER_THREAD_NUM VARCHAR(10) COMMENT '분석작업을진행할쓰레드갯수', ").append("\n");
			MYSQL_CREATE.append("  WORKER_ID VARCHAR(10) NOT NULL COMMENT '입력자ID', ").append("\n");
			MYSQL_CREATE.append("  PRIMARY KEY (SYS_ID) ").append("\n");
			MYSQL_CREATE.append(") COMMENT '시스템'; ").append("\n");
			
			/* <클래스-TB_CLZZ> */
			MYSQL_CREATE.append("CREATE TABLE TB_CLZZ ( ").append("\n");
			MYSQL_CREATE.append("  SYS_ID VARCHAR(20) NOT NULL COMMENT '시스템ID', ").append("\n");
			MYSQL_CREATE.append("  CLZZ_ID VARCHAR(300) NOT NULL COMMENT '클래스ID', ").append("\n");
			MYSQL_CREATE.append("  PKG_ID VARCHAR(200) COMMENT '패키지', ").append("\n");
			MYSQL_CREATE.append("  CLZZ_NM VARCHAR(200) COMMENT '클래스명', ").append("\n");
			MYSQL_CREATE.append("  CLZZ_KIND VARCHAR(2) COMMENT '기능종류(CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지)', ").append("\n");
			MYSQL_CREATE.append("  RESOURCE_ID VARCHAR(100) COMMENT '리소스ID', ").append("\n");
			MYSQL_CREATE.append("  CLZZ_INTF VARCHAR(1) COMMENT '클래스or인터페이스', ").append("\n");
			MYSQL_CREATE.append("  INTF_ID_LIST TEXT COMMENT '상위인터페이스ID목록', ").append("\n");
			MYSQL_CREATE.append("  PARENT_CLZZ_ID VARCHAR(300) COMMENT '상위클래스ID', ").append("\n");
			MYSQL_CREATE.append("  INTF_IMPL_CLZZ_ID_LIST TEXT COMMENT '인터페이스구현하위클래스ID목록', ").append("\n");
			MYSQL_CREATE.append("  MEMBER_ALIAS_LIST TEXT COMMENT '호출알리아스', ").append("\n");			
			MYSQL_CREATE.append("  FILE_NAME VARCHAR(1000) COMMENT '파일명', ").append("\n");			
			MYSQL_CREATE.append("  WORKER_ID VARCHAR(10) NOT NULL COMMENT '입력자ID', ").append("\n");
			MYSQL_CREATE.append("  PRIMARY KEY (SYS_ID, CLZZ_ID) ").append("\n");
			MYSQL_CREATE.append(") COMMENT '클래스'; ").append("\n");
			
			/* <기능메서드-TB_FUNC> */
			MYSQL_CREATE.append("CREATE TABLE TB_FUNC ( ").append("\n");
			MYSQL_CREATE.append("  SYS_ID VARCHAR(20) NOT NULL COMMENT '시스템ID', ").append("\n");
			MYSQL_CREATE.append("  FUNC_ID VARCHAR(300) NOT NULL COMMENT '기능ID', ").append("\n");
			MYSQL_CREATE.append("  CLZZ_ID VARCHAR(300) NOT NULL COMMENT '클래스ID', ").append("\n");
			MYSQL_CREATE.append("  MTD_ID VARCHAR(300) COMMENT '메서드ID', ").append("\n");
			MYSQL_CREATE.append("  MTD_NM VARCHAR(400) COMMENT '메서드명', ").append("\n");
			MYSQL_CREATE.append("  MTD_URL VARCHAR(300) COMMENT '메서드URL', ").append("\n");	
			MYSQL_CREATE.append("  FILE_NAME VARCHAR(1000) COMMENT '파일명', ").append("\n");			
			MYSQL_CREATE.append("  WORKER_ID VARCHAR(10) NOT NULL COMMENT '입력자ID', ").append("\n");
			MYSQL_CREATE.append("  PRIMARY KEY (SYS_ID, FUNC_ID) ").append("\n");
			MYSQL_CREATE.append(") COMMENT '기능메서드'; ").append("\n");
			
			/* <테이블-TB_TBL> */
			MYSQL_CREATE.append("CREATE TABLE TB_TBL ( ").append("\n");
			MYSQL_CREATE.append("  SYS_ID VARCHAR(20) NOT NULL COMMENT '시스템ID', ").append("\n");
			MYSQL_CREATE.append("  TBL_ID VARCHAR(100) NOT NULL COMMENT '테이블ID', ").append("\n");
			MYSQL_CREATE.append("  TBL_OWNER VARCHAR(100) COMMENT '테이블오너', ").append("\n");
			MYSQL_CREATE.append("  TBL_NM VARCHAR(200) COMMENT '테이블명', ").append("\n");
			MYSQL_CREATE.append("  WORKER_ID VARCHAR(10) NOT NULL COMMENT '입력자ID', ").append("\n");
			MYSQL_CREATE.append("  PRIMARY KEY (SYS_ID, TBL_ID) ").append("\n");
			MYSQL_CREATE.append(") COMMENT '테이블'; ").append("\n");
			
			/* <기능간맵핑-TB_FUNC_FUNC_MAPPING> */
			MYSQL_CREATE.append("CREATE TABLE TB_FUNC_FUNC_MAPPING ( ").append("\n");
			MYSQL_CREATE.append("  SYS_ID VARCHAR(20) NOT NULL COMMENT '시스템ID', ").append("\n");
			MYSQL_CREATE.append("  FUNC_ID VARCHAR(300) NOT NULL COMMENT '기능ID', ").append("\n");
			MYSQL_CREATE.append("  CALL_FUNC_ID VARCHAR(300) NOT NULL COMMENT '호출기능ID', ").append("\n");
			MYSQL_CREATE.append("  WORKER_ID VARCHAR(10) NOT NULL COMMENT '입력자ID', ").append("\n");
			MYSQL_CREATE.append("  PRIMARY KEY (SYS_ID, FUNC_ID, CALL_FUNC_ID) ").append("\n");
			MYSQL_CREATE.append(") COMMENT '기능간맵핑'; ").append("\n");
			
			/* <테이블맵핑-TB_FUNC_TBL_MAPPING> */
			MYSQL_CREATE.append("CREATE TABLE TB_FUNC_TBL_MAPPING ( ").append("\n");
			MYSQL_CREATE.append("  SYS_ID VARCHAR(20) NOT NULL COMMENT '시스템ID', ").append("\n");
			MYSQL_CREATE.append("  FUNC_ID VARCHAR(300) NOT NULL COMMENT '기능ID', ").append("\n");
			MYSQL_CREATE.append("  TBL_ID VARCHAR(100) NOT NULL COMMENT '테이블ID', ").append("\n");
			MYSQL_CREATE.append("  JOB_KIND VARCHAR(10) COMMENT '작업종류', ").append("\n");
			MYSQL_CREATE.append("  WORKER_ID VARCHAR(10) NOT NULL COMMENT '입력자ID', ").append("\n");
			MYSQL_CREATE.append("  PRIMARY KEY (SYS_ID, FUNC_ID, TBL_ID, JOB_KIND) ").append("\n");
			MYSQL_CREATE.append(") COMMENT '테이블맵핑'; ").append("\n");
			
			/* <화면-TB_UI> */
			MYSQL_CREATE.append("CREATE TABLE TB_UI ( ").append("\n");
			MYSQL_CREATE.append("  SYS_ID VARCHAR(20) NOT NULL COMMENT '시스템ID', ").append("\n");
			MYSQL_CREATE.append("  UI_ID VARCHAR(100) NOT NULL COMMENT '화면ID', ").append("\n");
			MYSQL_CREATE.append("  UI_NM VARCHAR(200) COMMENT '화면명', ").append("\n");	
			MYSQL_CREATE.append("  FILE_NAME VARCHAR(1000) COMMENT '파일명', ").append("\n");			
			MYSQL_CREATE.append("  WORKER_ID VARCHAR(10) NOT NULL COMMENT '입력자ID', ").append("\n");
			MYSQL_CREATE.append("  PRIMARY KEY (SYS_ID, UI_ID) ").append("\n");
			MYSQL_CREATE.append(") COMMENT '화면'; ").append("\n");
			
			/* <화면기능맵핑-TB_UI_FUNC_MAPPING> */
			MYSQL_CREATE.append("CREATE TABLE TB_UI_FUNC_MAPPING ( ").append("\n");
			MYSQL_CREATE.append("  SYS_ID VARCHAR(20) NOT NULL COMMENT '시스템ID', ").append("\n");
			MYSQL_CREATE.append("  UI_ID VARCHAR(100) NOT NULL COMMENT '화면ID', ").append("\n");
			MYSQL_CREATE.append("  MTD_URL VARCHAR(300) NOT NULL COMMENT '메서드URL', ").append("\n");
			MYSQL_CREATE.append("  WORKER_ID VARCHAR(10) NOT NULL COMMENT '입력자ID', ").append("\n");
			MYSQL_CREATE.append("  PRIMARY KEY (SYS_ID, UI_ID, MTD_URL) ").append("\n");
			MYSQL_CREATE.append(") COMMENT '화면기능맵핑'; ").append("\n");

			/* <종합메트릭스-TB_METRIX> */
			MYSQL_CREATE.append("CREATE TABLE TB_METRIX ( ").append("\n");
			MYSQL_CREATE.append("  SEQ BIGINT UNSIGNED NOT NULL COMMENT '시퀀스', ").append("\n");
			MYSQL_CREATE.append("  SYS_ID VARCHAR(20) NOT NULL COMMENT '시스템ID', ").append("\n");
			MYSQL_CREATE.append("  UI_ID VARCHAR(100) COMMENT '화면ID', ").append("\n");
			MYSQL_CREATE.append("  UI_NM VARCHAR(200) COMMENT '화면명', ").append("\n");
			MYSQL_CREATE.append("  BASIC_URL VARCHAR(300) COMMENT '기준URL', ").append("\n");
			for(int i=1; i<=FUNC_DEPTH_CNT; i++) {
				MYSQL_CREATE.append("  FUNCTION_ID_"+i+" VARCHAR(300) COMMENT '기능ID_"+i+"', ").append("\n");
				MYSQL_CREATE.append("  FUNCTION_NAME_"+i+" VARCHAR(400) COMMENT '기능명_"+i+"', ").append("\n");
				MYSQL_CREATE.append("  CLASS_KIND_"+i+" VARCHAR(2) COMMENT '클래스종류"+i+"(CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지)', ").append("\n");
			}
			MYSQL_CREATE.append("  CALL_TBL VARCHAR(4000) COMMENT '호출테이블', ").append("\n");
			MYSQL_CREATE.append("  WORKER_ID VARCHAR(10) NOT NULL COMMENT '입력자ID', ").append("\n");
			MYSQL_CREATE.append("  PRIMARY KEY (SYS_ID, SEQ) ").append("\n");
			MYSQL_CREATE.append(") COMMENT '종합메트릭스'; ").append("\n");

			/* <시스템-TB_SYS> */
			ORACLE_CREATE.append("CREATE TABLE TB_SYS ( ").append("\n");
			ORACLE_CREATE.append("  SYS_ID VARCHAR2(20) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  SYS_NM VARCHAR2(200), ").append("\n");
			ORACLE_CREATE.append("  CONF_FILE_PATH VARCHAR2(500) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  APP_ROOT_PATH VARCHAR2(500) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  APP_SRC_PATH VARCHAR2(4000) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  APP_WEB_PATH VARCHAR2(4000) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  APP_SQL_PATH VARCHAR2(4000) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  WRITE_PATH VARCHAR2(500), ").append("\n");
			ORACLE_CREATE.append("  SAVE_FILE_NAME VARCHAR2(500), ").append("\n");
			ORACLE_CREATE.append("  DBID VARCHAR2(10), ").append("\n");
			ORACLE_CREATE.append("  IS_TABLE_LIST_FROM_DB VARCHAR2(10), ").append("\n");
			ORACLE_CREATE.append("  TABLE_NAME_LIKE_STR VARCHAR2(500), ").append("\n");
			ORACLE_CREATE.append("  TABLE_LIST_FILE_NAME VARCHAR2(500), ").append("\n");
			ORACLE_CREATE.append("  IS_SAVE_TO_DB VARCHAR2(10), ").append("\n");
			ORACLE_CREATE.append("  APP_JDK_HOME VARCHAR2(200), ").append("\n");
			ORACLE_CREATE.append("  APP_CLASSPATH CLOB, ").append("\n");
			ORACLE_CREATE.append("  WORKER_THREAD_KIND VARCHAR2(2), ").append("\n");
			ORACLE_CREATE.append("  WORKER_THREAD_NUM VARCHAR2(10), ").append("\n");
			ORACLE_CREATE.append("  WORKER_ID VARCHAR2(10) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  PRIMARY KEY (SYS_ID) ").append("\n");
			ORACLE_CREATE.append("); ").append("\n");
			ORACLE_CREATE.append("COMMENT ON TABLE TB_SYS IS '시스템' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.SYS_ID IS '시스템ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.SYS_NM IS '시스템명'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.CONF_FILE_PATH IS '설정파일경로' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.APP_ROOT_PATH IS '어플리케이션루트' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.APP_SRC_PATH IS '어플리케이션서버소스루트' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.APP_WEB_PATH IS '어플리케이션웹소스루트' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.APP_SQL_PATH IS '어플리케이션쿼리소스루트' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.WRITE_PATH IS '분석결과생성경로'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.SAVE_FILE_NAME IS '분석결과저장파일명'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.DBID IS 'DBID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.IS_TABLE_LIST_FROM_DB IS '테이블목록을DB로부터읽어올지여부'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.TABLE_NAME_LIKE_STR IS '테이블명을DB로부터읽어올때적용할프리픽스'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.TABLE_LIST_FILE_NAME IS '테이블목록정보파일명'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.IS_SAVE_TO_DB IS '작업결과를DB에저장할지여부'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.APP_JDK_HOME IS '분석대상어플리케이션JDK홈'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.APP_CLASSPATH IS '분석대상어플리케이션클래스패스'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.WORKER_THREAD_KIND IS '분석작업을진행할쓰레드핸들러종류'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.WORKER_THREAD_NUM IS '분석작업을진행할쓰레드갯수'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_SYS.WORKER_ID IS '입력자ID'; ").append("\n");
			
			/* <클래스-TB_CLZZ> */
			ORACLE_CREATE.append("CREATE TABLE TB_CLZZ ( ").append("\n");
			ORACLE_CREATE.append("  SYS_ID VARCHAR2(20) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  CLZZ_ID VARCHAR2(300) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  PKG_ID VARCHAR2(200), ").append("\n");
			ORACLE_CREATE.append("  CLZZ_NM VARCHAR2(200), ").append("\n");
			ORACLE_CREATE.append("  CLZZ_KIND VARCHAR2(2), ").append("\n");
			ORACLE_CREATE.append("  RESOURCE_ID VARCHAR2(100), ").append("\n");
			ORACLE_CREATE.append("  CLZZ_INTF VARCHAR2(1), ").append("\n");
			ORACLE_CREATE.append("  INTF_ID_LIST VARCHAR2(4000), ").append("\n");
			ORACLE_CREATE.append("  PARENT_CLZZ_ID VARCHAR2(300), ").append("\n");
			ORACLE_CREATE.append("  INTF_IMPL_CLZZ_ID_LIST VARCHAR2(4000), ").append("\n");
			ORACLE_CREATE.append("  MEMBER_ALIAS_LIST LONG, ").append("\n");			
			ORACLE_CREATE.append("  FILE_NAME VARCHAR2(1000), ").append("\n");			
			ORACLE_CREATE.append("  WORKER_ID VARCHAR2(10) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  PRIMARY KEY (SYS_ID, CLZZ_ID) ").append("\n");
			ORACLE_CREATE.append("); ").append("\n");			
			ORACLE_CREATE.append("COMMENT ON TABLE TB_CLZZ IS '클래스' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.SYS_ID IS '시스템ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.CLZZ_ID IS '클래스ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.PKG_ID IS '패키지ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.CLZZ_NM IS '클래스명'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.CLZZ_KIND IS '클래스종류(CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지)'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.RESOURCE_ID IS '리소스ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.CLZZ_INTF IS '클래스or인터페이스'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.INTF_ID_LIST IS '상위인터페이스ID목록'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.PARENT_CLZZ_ID IS '상위클래스ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.INTF_IMPL_CLZZ_ID_LIST IS '인터페이스구현하위클래스ID목록'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.MEMBER_ALIAS_LIST IS '호출알리아스'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.FILE_NAME IS '파일명'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_CLZZ.WORKER_ID IS '입력자ID'; ").append("\n");

			/* <기능메서드-TB_FUNC> */
			ORACLE_CREATE.append("CREATE TABLE TB_FUNC ( ").append("\n");
			ORACLE_CREATE.append("  SYS_ID VARCHAR2(20) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  FUNC_ID VARCHAR2(300) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  MTD_ID VARCHAR2(100), ").append("\n");
			ORACLE_CREATE.append("  MTD_NM VARCHAR2(400), ").append("\n");
			ORACLE_CREATE.append("  CLZZ_ID VARCHAR2(300), ").append("\n");
			ORACLE_CREATE.append("  MTD_URL VARCHAR2(300), ").append("\n");
			ORACLE_CREATE.append("  FILE_NAME VARCHAR2(1000), ").append("\n");		
			ORACLE_CREATE.append("  WORKER_ID VARCHAR2(10) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  PRIMARY KEY (FUNC_ID) ").append("\n");
			ORACLE_CREATE.append("); ").append("\n");			
			ORACLE_CREATE.append("COMMENT ON TABLE TB_FUNC IS '기능메서드' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC.SYS_ID IS '시스템ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC.FUNC_ID IS '기능ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC.MTD_ID IS '메서드ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC.MTD_NM IS '메서드명'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC.CLZZ_ID IS '클래스ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC.MTD_URL IS '메서드URL'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC.FILE_NAME IS '파일명'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC.WORKER_ID IS '입력자ID'; ").append("\n");
			
			/* <테이블-TB_TBL> */
			ORACLE_CREATE.append("CREATE TABLE TB_TBL ( ").append("\n");
			ORACLE_CREATE.append("  SYS_ID VARCHAR2(20) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  TBL_ID VARCHAR2(100) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  TBL_OWNER VARCHAR2(100), ").append("\n");
			ORACLE_CREATE.append("  TBL_NM VARCHAR2(200), ").append("\n");
			ORACLE_CREATE.append("  WORKER_ID VARCHAR2(10) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  PRIMARY KEY (SYS_ID, TBL_ID) ").append("\n");
			ORACLE_CREATE.append("); ").append("\n");			
			ORACLE_CREATE.append("COMMENT ON TABLE TB_TBL IS '테이블' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_TBL.SYS_ID IS '시스템ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_TBL.TBL_ID IS '테이블ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_TBL.TBL_OWNER IS '테이블오너'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_TBL.TBL_NM IS '테이블명'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_TBL.WORKER_ID IS '입력자ID'; ").append("\n");

			/* <기능간맵핑-TB_FUNC_FUNC_MAPPING> */
			ORACLE_CREATE.append("CREATE TABLE TB_FUNC_FUNC_MAPPING ( ").append("\n");
			ORACLE_CREATE.append("  SYS_ID VARCHAR2(20) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  FUNC_ID VARCHAR2(300) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  CALL_FUNC_ID VARCHAR2(300) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  WORKER_ID VARCHAR2(10) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  PRIMARY KEY (SYS_ID, FUNC_ID, CALL_FUNC_ID) ").append("\n");
			ORACLE_CREATE.append("); ").append("\n");			
			ORACLE_CREATE.append("COMMENT ON TABLE TB_FUNC_FUNC_MAPPING IS '기능간맵핑' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC_FUNC_MAPPING.SYS_ID IS '시스템ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC_FUNC_MAPPING.FUNC_ID IS '기능ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC_FUNC_MAPPING.CALL_FUNC_ID IS '호출기능ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC_FUNC_MAPPING.WORKER_ID IS '입력자ID'; ").append("\n");

			/* <테이블맵핑-TB_FUNC_TBL_MAPPING> */
			ORACLE_CREATE.append("CREATE TABLE TB_FUNC_TBL_MAPPING ( ").append("\n");
			ORACLE_CREATE.append("  SYS_ID VARCHAR2(20) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  FUNC_ID VARCHAR2(300) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  TBL_ID VARCHAR2(100) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  JOB_KIND VARCHAR2(10), ").append("\n");
			ORACLE_CREATE.append("  WORKER_ID VARCHAR2(10) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  PRIMARY KEY (SYS_ID, FUNC_ID, TBL_ID, JOB_KIND) ").append("\n");
			ORACLE_CREATE.append("); ").append("\n");			
			ORACLE_CREATE.append("COMMENT ON TABLE TB_FUNC_TBL_MAPPING IS '테이블맵핑' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC_TBL_MAPPING.SYS_ID IS '시스템ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC_TBL_MAPPING.FUNC_ID IS '기능ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC_TBL_MAPPING.TBL_ID IS '테이블ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC_TBL_MAPPING.JOB_KIND IS '작업종류'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_FUNC_TBL_MAPPING.WORKER_ID IS '입력자ID'; ").append("\n");

			/* <화면-TB_UI> */
			ORACLE_CREATE.append("CREATE TABLE TB_UI ( ").append("\n");
			ORACLE_CREATE.append("  SYS_ID VARCHAR2(20) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  UI_ID VARCHAR2(100) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  UI_NM VARCHAR2(200), ").append("\n");
			ORACLE_CREATE.append("  WORKER_ID VARCHAR2(10) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  PRIMARY KEY (SYS_ID, UI_ID) ").append("\n");
			ORACLE_CREATE.append("); ").append("\n");			
			ORACLE_CREATE.append("COMMENT ON TABLE TB_UI IS '화면' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_UI.SYS_ID IS '시스템ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_UI.UI_ID IS '화면ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_UI.UI_NM IS '화면명'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_UI.WORKER_ID IS '입력자ID'; ").append("\n");

			/* <화면기능맵핑-TB_UI_FUNC_MAPPING> */
			ORACLE_CREATE.append("CREATE TABLE TB_UI_FUNC_MAPPING ( ").append("\n");
			ORACLE_CREATE.append("  SYS_ID VARCHAR2(20) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  UI_ID VARCHAR2(100) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  MTD_URL VARCHAR2(300) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  WORKER_ID VARCHAR2(10) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  PRIMARY KEY (SYS_ID, UI_ID, MTD_URL) ").append("\n");
			ORACLE_CREATE.append("); ").append("\n");			
			ORACLE_CREATE.append("COMMENT ON TABLE TB_UI_FUNC_MAPPING IS '화면기능맵핑' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_UI_FUNC_MAPPING.SYS_ID IS '시스템ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_UI_FUNC_MAPPING.UI_ID IS '화면ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_UI_FUNC_MAPPING.MTD_URL IS '메서드URL'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_UI_FUNC_MAPPING.WORKER_ID IS '입력자ID'; ").append("\n");			

			/* <종합메트릭스-TB_METRIX> */
			ORACLE_CREATE.append("CREATE TABLE TB_METRIX ( ").append("\n");
			ORACLE_CREATE.append("  SEQ NUMBER(10) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  SYS_ID VARCHAR(20) NOT NULL, ").append("\n");
			ORACLE_CREATE.append("  UI_ID VARCHAR2(100), ").append("\n");
			ORACLE_CREATE.append("  UI_NM VARCHAR2(200), ").append("\n");
			ORACLE_CREATE.append("  BASIC_URL VARCHAR2(300), ").append("\n");
			for(int i=1; i<=FUNC_DEPTH_CNT; i++) {
				ORACLE_CREATE.append("  FUNCTION_ID_"+i+" VARCHAR2(300), ").append("\n");
				ORACLE_CREATE.append("  FUNCTION_NAME_"+i+" VARCHAR2(400), ").append("\n");
				ORACLE_CREATE.append("  CLASS_KIND_"+i+" VARCHAR2(2), ").append("\n");
			}
			ORACLE_CREATE.append("  CALL_TBL VARCHAR2(4000), ").append("\n");
			ORACLE_CREATE.append("  WORKER_ID VARCHAR2(10) NOT NULL,").append("\n");
			ORACLE_CREATE.append("  PRIMARY KEY (SYS_ID, SEQ) ").append("\n");
			ORACLE_CREATE.append("); ").append("\n");			
			ORACLE_CREATE.append("COMMENT ON TABLE TB_METRIX IS '종합메트릭스' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_METRIX.SEQ IS '시퀀스' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_METRIX.SYS_ID IS '시스템' ; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_METRIX.UI_ID IS '화면ID'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_METRIX.UI_NM IS '화면명'; ").append("\n");
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_METRIX.BASIC_URL IS '기준URL'; ").append("\n");
			for(int i=1; i<=FUNC_DEPTH_CNT; i++) {
				ORACLE_CREATE.append("COMMENT ON COLUMN TB_METRIX.FUNCTION_ID_"+i+" IS '기능ID"+i+"'; ").append("\n");
				ORACLE_CREATE.append("COMMENT ON COLUMN TB_METRIX.FUNCTION_NAME_"+i+" IS '기능명"+i+"'; ").append("\n");
				ORACLE_CREATE.append("COMMENT ON COLUMN TB_METRIX.CLASS_KIND_"+i+" IS '클래스종류"+i+"'; ").append("\n");
			}
			ORACLE_CREATE.append("COMMENT ON COLUMN TB_METRIX.WORKER_ID IS '입력자ID'; ").append("\n");
			
			/* <시스템-TB_SYS> */
			DROP.append("DROP TABLE TB_SYS;").append("\n");
			/* <클래스-TB_CLZZ> */
			DROP.append("DROP TABLE TB_CLZZ;").append("\n");
			/* <기능메서드-TB_FUNC> */
			DROP.append("DROP TABLE TB_FUNC; ").append("\n");
			/* <테이블-TB_TBL> */
			DROP.append("DROP TABLE TB_TBL; ").append("\n");
			/* <기능간맵핑-TB_FUNC_FUNC_MAPPING> */
			DROP.append("DROP TABLE TB_FUNC_FUNC_MAPPING; ").append("\n");
			/* <테이블맵핑-TB_FUNC_TBL_MAPPING> */
			DROP.append("DROP TABLE TB_FUNC_TBL_MAPPING; ").append("\n");
			/* <화면-TB_UI> */
			DROP.append("DROP TABLE TB_UI; ").append("\n");
			/* <화면기능맵핑-TB_UI_FUNC_MAPPING> */
			DROP.append("DROP TABLE TB_UI_FUNC_MAPPING; ").append("\n");
			/* <종합메트릭스-TB_METRIX> */
			DROP.append("DROP TABLE TB_METRIX; ").append("\n");

		}
	}
	
	public static class QUERY {

		public static StringBuffer MERGE_TB_SYS = new StringBuffer();
		public static StringBuffer INSERT_TB_CLZZ = new StringBuffer();
		public static StringBuffer INSERT_TB_FUNC = new StringBuffer();
		public static StringBuffer INSERT_TB_TBL = new StringBuffer();
		public static StringBuffer INSERT_TB_FUNC_FUNC_MAPPING = new StringBuffer();
		public static StringBuffer INSERT_TB_FUNC_TBL_MAPPING = new StringBuffer();
		public static StringBuffer INSERT_TB_UI = new StringBuffer();
		public static StringBuffer INSERT_TB_UI_FUNC_MAPPING = new StringBuffer();
		public static StringBuffer INSERT_TB_METRIX = new StringBuffer();

		public static StringBuffer DELETE_TB_SYS = new StringBuffer();
		public static StringBuffer DELETE_TB_CLZZ = new StringBuffer();
		public static StringBuffer DELETE_TB_FUNC = new StringBuffer();
		public static StringBuffer DELETE_TB_TBL = new StringBuffer();
		public static StringBuffer DELETE_TB_FUNC_FUNC_MAPPING = new StringBuffer();
		public static StringBuffer DELETE_TB_FUNC_TBL_MAPPING = new StringBuffer();
		public static StringBuffer DELETE_TB_UI = new StringBuffer();
		public static StringBuffer DELETE_TB_UI_FUNC_MAPPING = new StringBuffer();
		public static StringBuffer DELETE_TB_METRIX = new StringBuffer();
		
		public static StringBuffer SELECT_TB_FUNC_ALL = new StringBuffer();
		
		static {

			/* <시스템-TB_SYS> */
			MERGE_TB_SYS.append("MERGE INTO TB_SYS ").append("\n");
			MERGE_TB_SYS.append("USING DUAL ").append("\n");
			MERGE_TB_SYS.append("ON ( ").append("\n");
			MERGE_TB_SYS.append("	SYS_ID = ? /* 시스템ID */ ").append("\n");
			MERGE_TB_SYS.append(") ").append("\n");
			MERGE_TB_SYS.append("WHEN MATCH THEN  ").append("\n");
			MERGE_TB_SYS.append("UPDATE SET  ").append("\n");
			MERGE_TB_SYS.append("	SYS_ID = ? /* 시스템ID */ ").append("\n");
			MERGE_TB_SYS.append("	, SYS_NM = ? /* 시스템명 */ ").append("\n");
			MERGE_TB_SYS.append("	, WRITE_PATH = ? /* 분석결과생성경로 */ ").append("\n");
			MERGE_TB_SYS.append("	, SAVE_FILE_NAME = ? /* 분석결과저장파일명 */ ").append("\n");
			MERGE_TB_SYS.append("	, DBID = ? /* DBID */ ").append("\n");
			MERGE_TB_SYS.append("	, IS_TABLE_LIST_FROM_DB = ? /* 테이블목록을DB로부터읽어올지여부 */ ").append("\n");
			MERGE_TB_SYS.append("	, TABLE_NAME_LIKE_STR = ? /* 테이블명을DB로부터읽어올때적용할프리픽스 */ ").append("\n");
			MERGE_TB_SYS.append("	, TABLE_LIST_FILE_NAME = ? /* 테이블목록정보파일명 */ ").append("\n");
			MERGE_TB_SYS.append("	, IS_SAVE_TO_DB = ? /* 작업결과를DB에저장할지여부 */ ").append("\n");
			MERGE_TB_SYS.append("	, APP_JDK_HOME = ? /* 분석대상어플리케이션JDK홈 */ ").append("\n");
			MERGE_TB_SYS.append("	, APP_CLASSPATH = ? /* 분석대상어플리케이션클래스패스 */ ").append("\n");
			MERGE_TB_SYS.append("	, WORKER_THREAD_KIND = ? /* 분석작업을진행할쓰레드핸들러종류 */ ").append("\n");
			MERGE_TB_SYS.append("	, WORKER_THREAD_NUM = ? /* 분석작업을진행할쓰레드갯수 */ ").append("\n");
			MERGE_TB_SYS.append("	, WORKER_ID = 'SYSTEM' /* 입력자ID */ ").append("\n");
			MERGE_TB_SYS.append("WHEN NOT MATCH THEN  ").append("\n");
			MERGE_TB_SYS.append("INSERT ( ").append("\n");
			MERGE_TB_SYS.append("	SYS_ID /* 시스템ID */ ").append("\n");
			MERGE_TB_SYS.append("	, SYS_NM /* 시스템명 */ ").append("\n");
			MERGE_TB_SYS.append("	, WRITE_PATH /* 분석결과생성경로 */ ").append("\n");
			MERGE_TB_SYS.append("	, SAVE_FILE_NAME /* 분석결과저장파일명 */ ").append("\n");
			MERGE_TB_SYS.append("	, DBID /* DBID */ ").append("\n");
			MERGE_TB_SYS.append("	, IS_TABLE_LIST_FROM_DB /* 테이블목록을DB로부터읽어올지여부 */ ").append("\n");
			MERGE_TB_SYS.append("	, TABLE_NAME_LIKE_STR /* 테이블명을DB로부터읽어올때적용할프리픽스 */ ").append("\n");
			MERGE_TB_SYS.append("	, TABLE_LIST_FILE_NAME /* 테이블목록정보파일명 */ ").append("\n");
			MERGE_TB_SYS.append("	, IS_SAVE_TO_DB /* 작업결과를DB에저장할지여부 */ ").append("\n");
			MERGE_TB_SYS.append("	, APP_JDK_HOME /* 분석대상어플리케이션JDK홈 */ ").append("\n");
			MERGE_TB_SYS.append("	, APP_CLASSPATH /* 분석대상어플리케이션클래스패스 */ ").append("\n");
			MERGE_TB_SYS.append("	, WORKER_THREAD_KIND /* 분석작업을진행할쓰레드핸들러종류 */ ").append("\n");
			MERGE_TB_SYS.append("	, WORKER_THREAD_NUM /* 분석작업을진행할쓰레드갯수 */ ").append("\n");
			MERGE_TB_SYS.append("	, WORKER_ID /* 입력자ID */ ").append("\n");
			MERGE_TB_SYS.append(") VALUES ( ").append("\n");
			MERGE_TB_SYS.append("	? /* 시스템ID */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* 시스템명 */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* 분석결과생성경로 */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* 분석결과저장파일명 */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* DBID */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* 테이블목록을DB로부터읽어올지여부 */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* 테이블명을DB로부터읽어올때적용할프리픽스 */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* 테이블목록정보파일명 */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* 작업결과를DB에저장할지여부 */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* 분석대상어플리케이션JDK홈 */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* 분석대상어플리케이션클래스패스 */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* 분석작업을진행할쓰레드핸들러종류 */ ").append("\n");
			MERGE_TB_SYS.append("	, ? /* 분석작업을진행할쓰레드갯수 */ ").append("\n");
			MERGE_TB_SYS.append("	, 'SYSTEM' /* 입력자ID */ ").append("\n");
			MERGE_TB_SYS.append(") ").append("\n");
			
			/* <클래스-TB_CLZZ> */
			INSERT_TB_CLZZ.append("INSERT INTO TB_CLZZ ( ").append("\n");
			INSERT_TB_CLZZ.append("	SYS_ID /* 시스템ID */ ").append("\n");
			INSERT_TB_CLZZ.append("	, CLZZ_ID /* 클래스ID */ ").append("\n");
			INSERT_TB_CLZZ.append("	, PKG_ID /* 패키지 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, CLZZ_NM /* 클래스명 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, CLZZ_KIND /* 기능종류(CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지) */ ").append("\n");
			INSERT_TB_CLZZ.append("	, RESOURCE_ID /* 리소스ID */ ").append("\n");
			INSERT_TB_CLZZ.append("	, CLZZ_INTF /* 클래스or인터페이스 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, INTF_ID_LIST /* 상위인터페이스ID목록 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, PARENT_CLZZ_ID /* 상위클래스ID */ ").append("\n");
			INSERT_TB_CLZZ.append("	, INTF_IMPL_CLZZ_ID_LIST /* 인터페이스구현하위클래스ID목록 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, MEMBER_ALIAS_LIST /* 호출알리아스 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, FILE_NAME /* 파일명 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, WORKER_ID /* 입력자ID */ ").append("\n");
			INSERT_TB_CLZZ.append(") VALUES ( ").append("\n");
			INSERT_TB_CLZZ.append("	? /* 시스템ID */ ").append("\n");
			INSERT_TB_CLZZ.append("	, ? /* 클래스ID */ ").append("\n");
			INSERT_TB_CLZZ.append("	, ? /* 패키지 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, ? /* 클래스명 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, ? /* 기능종류(CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지) */ ").append("\n");
			INSERT_TB_CLZZ.append("	, ? /* 리소스ID */ ").append("\n");
			INSERT_TB_CLZZ.append("	, ? /* 클래스or인터페이스 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, ? /* 상위인터페이스ID목록 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, ? /* 상위클래스ID */ ").append("\n");
			INSERT_TB_CLZZ.append("	, ? /* 인터페이스구현하위클래스ID목록 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, ? /* 호출알리아스 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, ? /* 파일명 */ ").append("\n");
			INSERT_TB_CLZZ.append("	, 'SYSTEM' /* 입력자ID */ ").append("\n");
			INSERT_TB_CLZZ.append(") ").append("\n");
			
			/* <기능메서드-TB_FUNC> */
			INSERT_TB_FUNC.append("INSERT INTO TB_FUNC ( ").append("\n");
			INSERT_TB_FUNC.append("	SYS_ID /* 시스템ID */ ").append("\n");
			INSERT_TB_FUNC.append("	, FUNC_ID /* 기능ID */ ").append("\n");
			INSERT_TB_FUNC.append("	, CLZZ_ID /* 클래스ID */ ").append("\n");
			INSERT_TB_FUNC.append("	, MTD_ID /* 메서드ID */ ").append("\n");
			INSERT_TB_FUNC.append("	, MTD_NM /* 메서드명 */ ").append("\n");
			INSERT_TB_FUNC.append("	, MTD_URL /* 메서드URL */ ").append("\n");
			INSERT_TB_FUNC.append("	, FILE_NAME /* 파일명 */ ").append("\n");
			INSERT_TB_FUNC.append("	, WORKER_ID /* 입력자ID */ ").append("\n");
			INSERT_TB_FUNC.append(") VALUES ( ").append("\n");
			INSERT_TB_FUNC.append("	? /* 시스템ID */ ").append("\n");
			INSERT_TB_FUNC.append("	, ? /* 기능ID */ ").append("\n");
			INSERT_TB_FUNC.append("	, ? /* 클래스ID */ ").append("\n");
			INSERT_TB_FUNC.append("	, ? /* 메서드ID */ ").append("\n");
			INSERT_TB_FUNC.append("	, ? /* 메서드명 */ ").append("\n");
			INSERT_TB_FUNC.append("	, ? /* 메서드URL */ ").append("\n");
			INSERT_TB_FUNC.append("	, ? /* 파일명 */ ").append("\n");
			INSERT_TB_FUNC.append("	, 'SYSTEM' /* 입력자ID */ ").append("\n");
			INSERT_TB_FUNC.append(") ").append("\n");
			
			/* <테이블-TB_TBL> */
			INSERT_TB_TBL.append("INSERT INTO TB_TBL ( ").append("\n");
			INSERT_TB_TBL.append("	SYS_ID /* 시스템ID */ ").append("\n");
			INSERT_TB_TBL.append("	, TBL_ID /* 테이블ID */ ").append("\n");
			INSERT_TB_TBL.append("	, TBL_OWNER /* 테이블오너 */ ").append("\n");
			INSERT_TB_TBL.append("	, TBL_NM /* 테이블명 */ ").append("\n");
			INSERT_TB_TBL.append("	, WORKER_ID /* 입력자ID */ ").append("\n");
			INSERT_TB_TBL.append(") VALUES ( ").append("\n");
			INSERT_TB_TBL.append("	? /* 시스템ID */ ").append("\n");
			INSERT_TB_TBL.append("	, ? /* 테이블ID */ ").append("\n");
			INSERT_TB_TBL.append("	, ? /* 테이블오너 */ ").append("\n");
			INSERT_TB_TBL.append("	, ? /* 테이블명 */ ").append("\n");
			INSERT_TB_TBL.append("	, 'SYSTEM' /* 입력자ID */ ").append("\n");
			INSERT_TB_TBL.append(") ").append("\n");

			/* <기능간맵핑-TB_FUNC_FUNC_MAPPING> */
			INSERT_TB_FUNC_FUNC_MAPPING.append("INSERT INTO TB_FUNC_FUNC_MAPPING ( ").append("\n");
			INSERT_TB_FUNC_FUNC_MAPPING.append("	SYS_ID /* 시스템ID */ ").append("\n");
			INSERT_TB_FUNC_FUNC_MAPPING.append("	, FUNC_ID /* 기능ID */ ").append("\n");
			INSERT_TB_FUNC_FUNC_MAPPING.append("	, CALL_FUNC_ID /* 호출기능ID */ ").append("\n");
			INSERT_TB_FUNC_FUNC_MAPPING.append("	, WORKER_ID /* 입력자ID */ ").append("\n");
			INSERT_TB_FUNC_FUNC_MAPPING.append(") VALUES ( ").append("\n");
			INSERT_TB_FUNC_FUNC_MAPPING.append("	? /* 시스템ID */ ").append("\n");
			INSERT_TB_FUNC_FUNC_MAPPING.append("	, ? /* 기능ID */ ").append("\n");
			INSERT_TB_FUNC_FUNC_MAPPING.append("	, ? /* 호출기능ID */ ").append("\n");
			INSERT_TB_FUNC_FUNC_MAPPING.append("	, 'SYSTEM' /* 입력자ID */ ").append("\n");
			INSERT_TB_FUNC_FUNC_MAPPING.append(") ").append("\n");

			/* <테이블맵핑-TB_FUNC_TBL_MAPPING> */
			INSERT_TB_FUNC_TBL_MAPPING.append("INSERT INTO TB_FUNC_TBL_MAPPING ( ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append("	SYS_ID /* 시스템ID */ ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append("	, FUNC_ID /* 기능ID */ ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append("	, TBL_ID /* 테이블ID */ ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append("	, JOB_KIND /* 작업종류 */ ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append("	, WORKER_ID /* 입력자ID */ ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append(") VALUES ( ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append("	? /* 시스템ID */ ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append("	, ? /* 기능ID */ ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append("	, ? /* 테이블ID */ ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append("	, ? /* 작업종류 */ ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append("	, 'SYSTEM' /* 입력자ID */ ").append("\n");
			INSERT_TB_FUNC_TBL_MAPPING.append(") ").append("\n");

			/* <화면-TB_UI> */
			INSERT_TB_UI.append("INSERT INTO TB_UI ( ").append("\n");
			INSERT_TB_UI.append("	SYS_ID /* 시스템ID */ ").append("\n");
			INSERT_TB_UI.append("	, UI_ID /* 화면ID */ ").append("\n");
			INSERT_TB_UI.append("	, UI_NM /* 화면명 */ ").append("\n");
			INSERT_TB_UI.append("	, WORKER_ID /* 입력자ID */ ").append("\n");
			INSERT_TB_UI.append(") VALUES ( ").append("\n");
			INSERT_TB_UI.append("	? /* 시스템ID */ ").append("\n");
			INSERT_TB_UI.append("	, ? /* 화면ID */ ").append("\n");
			INSERT_TB_UI.append("	, ? /* 화면명 */ ").append("\n");
			INSERT_TB_UI.append("	, 'SYSTEM' /* 입력자ID */ ").append("\n");
			INSERT_TB_UI.append(") ").append("\n");

			/* <화면기능맵핑-TB_UI_FUNC_MAPPING> */
			INSERT_TB_UI_FUNC_MAPPING.append("INSERT INTO TB_UI_FUNC_MAPPING ( ").append("\n");
			INSERT_TB_UI_FUNC_MAPPING.append("	SYS_ID /* 시스템ID */ ").append("\n");
			INSERT_TB_UI_FUNC_MAPPING.append("	, UI_ID /* 화면ID */ ").append("\n");
			INSERT_TB_UI_FUNC_MAPPING.append("	, MTD_URL /* 메서드URL */ ").append("\n");
			INSERT_TB_UI_FUNC_MAPPING.append("	, WORKER_ID /* 입력자ID */ ").append("\n");
			INSERT_TB_UI_FUNC_MAPPING.append(") VALUES ( ").append("\n");
			INSERT_TB_UI_FUNC_MAPPING.append("	? /* 시스템ID */ ").append("\n");
			INSERT_TB_UI_FUNC_MAPPING.append("	, ? /* 화면ID */ ").append("\n");
			INSERT_TB_UI_FUNC_MAPPING.append("	, ? /* 메서드URL */ ").append("\n");
			INSERT_TB_UI_FUNC_MAPPING.append("	, 'SYSTEM' /* 입력자ID */ ").append("\n");
			INSERT_TB_UI_FUNC_MAPPING.append(") ").append("\n");

			/* <종합메트릭스-TB_METRIX> */
			INSERT_TB_METRIX.append("INSERT INTO TB_METRIX (").append("\n");
			INSERT_TB_METRIX.append("	SEQ /* 시퀀스 */ ").append("\n");
			INSERT_TB_METRIX.append("	, SYS_ID /* 시스템ID */ ").append("\n");
			INSERT_TB_METRIX.append("	, UI_ID /* 화면ID */").append("\n");
			INSERT_TB_METRIX.append("	, UI_NM /* 화면명 */").append("\n");
			INSERT_TB_METRIX.append("	, BASIC_URL /* 기준URL */").append("\n");
			for(int i=1; i<=FUNC_DEPTH_CNT; i++) {
				INSERT_TB_METRIX.append("	, FUNCTION_ID_"+i+" /* 기능ID_"+i+" */").append("\n");
				INSERT_TB_METRIX.append("	, FUNCTION_NAME_"+i+" /* 기능명_"+i+" */").append("\n");
				INSERT_TB_METRIX.append("	, CLASS_KIND_"+i+" /* 클래스종류"+i+"(CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지) */").append("\n");
			}
			INSERT_TB_METRIX.append("	, CALL_TBL /* 호출테이블 */").append("\n");
			INSERT_TB_METRIX.append("	, WORKER_ID /* 입력자ID */").append("\n");
			INSERT_TB_METRIX.append(") VALUES (").append("\n");
			INSERT_TB_METRIX.append("	? /* 시퀀스 */ ").append("\n");
			INSERT_TB_METRIX.append("	, ? /* 시스템ID */ ").append("\n");
			INSERT_TB_METRIX.append("	, ? /* 화면ID */").append("\n");
			INSERT_TB_METRIX.append("	, ? /* 화면명 */").append("\n");
			INSERT_TB_METRIX.append("	, ? /* 기준URL */").append("\n");
			for(int i=1; i<=FUNC_DEPTH_CNT; i++) {
				INSERT_TB_METRIX.append("	, ? /* 기능ID_"+i+" */").append("\n");
				INSERT_TB_METRIX.append("	, ? /* 기능명_"+i+" */").append("\n");
				INSERT_TB_METRIX.append("	, ? /* 클래스종류"+i+"(CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지) */").append("\n");
			}
			INSERT_TB_METRIX.append("	, ? /* 호출테이블 */").append("\n");
			INSERT_TB_METRIX.append("	, 'SYSTEM' /* 입력자ID */").append("\n");
			INSERT_TB_METRIX.append(")").append("\n");

			/* <클래스-TB_CLZZ> */
			DELETE_TB_CLZZ.append("DELETE FROM TB_CLZZ WHERE SYS_ID = ? AND WORKER_ID = 'SYSTEM' ").append("\n");
			
			/* <기능메서드-TB_FUNC> */
			DELETE_TB_FUNC.append("DELETE FROM TB_FUNC WHERE SYS_ID = ? AND WORKER_ID = 'SYSTEM' ").append("\n");
			
			/* <테이블-TB_TBL> */
			DELETE_TB_TBL.append("DELETE FROM TB_TBL WHERE SYS_ID = ? AND WORKER_ID = 'SYSTEM' ").append("\n");

			/* <기능간맵핑-TB_FUNC_FUNC_MAPPING> */
			DELETE_TB_FUNC_FUNC_MAPPING.append("DELETE FROM TB_FUNC_FUNC_MAPPING WHERE SYS_ID = ? AND WORKER_ID = 'SYSTEM' ").append("\n");

			/* <테이블맵핑-TB_FUNC_TBL_MAPPING> */
			DELETE_TB_FUNC_TBL_MAPPING.append("DELETE FROM TB_FUNC_TBL_MAPPING WHERE SYS_ID = ? AND WORKER_ID = 'SYSTEM' ").append("\n");

			/* <화면-TB_UI> */
			DELETE_TB_UI.append("DELETE FROM TB_UI WHERE SYS_ID = ? AND WORKER_ID = 'SYSTEM' ").append("\n");

			/* <화면기능맵핑-TB_UI_FUNC_MAPPING> */
			DELETE_TB_UI_FUNC_MAPPING.append("DELETE FROM TB_UI_FUNC_MAPPING WHERE SYS_ID = ? AND WORKER_ID = 'SYSTEM' ").append("\n");

			/* <종합메트릭스-TB_METRIX> */
			DELETE_TB_METRIX.append("DELETE FROM TB_METRIX WHERE SYS_ID = ? AND WORKER_ID = 'SYSTEM' ").append("\n");

		}
	}
	
	public static String getDdlQuery(String DB_KIND, String JOB_KIND) {
		StringBuffer ddl = new StringBuffer();
		if( "DROP".equals(JOB_KIND) ) {
			ddl.append(DDL.DROP);
		}else {
			if( "MYSQL".equals(DB_KIND) ) {
				ddl.append(DDL.MYSQL_CREATE);
				ddl.append("\n");
				ddl.append(DDL.MYSQL_FUNCTION);
			}else if( "ORACLE".equals(DB_KIND) ) {
				ddl.append(DDL.ORACLE_CREATE);
				ddl.append("\n");
				ddl.append(DDL.ORACLE_FUNCTION);
			}
		}
		return ddl.toString();
	}
	
	public static String getDeleteQuery() {
		StringBuffer DELETE_ALL = new StringBuffer();
		DELETE_ALL.append(QUERY.DELETE_TB_SYS).append(";").append("\n");
		DELETE_ALL.append(QUERY.DELETE_TB_CLZZ).append(";").append("\n");
		DELETE_ALL.append(QUERY.DELETE_TB_FUNC).append(";").append("\n");
		DELETE_ALL.append(QUERY.DELETE_TB_TBL).append(";").append("\n");
		DELETE_ALL.append(QUERY.DELETE_TB_FUNC_FUNC_MAPPING).append(";").append("\n");
		DELETE_ALL.append(QUERY.DELETE_TB_FUNC_TBL_MAPPING).append(";").append("\n");
		DELETE_ALL.append(QUERY.DELETE_TB_UI).append(";").append("\n");
		DELETE_ALL.append(QUERY.DELETE_TB_UI_FUNC_MAPPING).append(";").append("\n");
		DELETE_ALL.append("COMMIT;").append("\n");
		return DELETE_ALL.toString();
	}
	
	private static int setParam(LoggableStatement pstmt, int parameterIndex, String input ) throws Exception {
		parameterIndex = parameterIndex + 1;
		if(StringUtil.isEmpty(input)) {
			pstmt.setNull(parameterIndex, java.sql.Types.NULL);
		}else {
			pstmt.setString(parameterIndex, input);
		}
		return parameterIndex;
	}
	
	public static void mergeTB_SYS(String DBID, SysVo sysVo) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		int parameterIndex = 0;

		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			
			/* <클래스-TB_CLZZ> */
			db.setQuery(QUERY.MERGE_TB_SYS.toString());

			parameterIndex = 0;
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getSysId()); /* 시스템ID */
			
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getSysId()); /* 시스템ID */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getSysNm()); /* 시스템명 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getWrithPath()); /* 분석결과생성경로 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getSaveFileName()); /* 분석결과저장파일명 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getDbId()); /* DBID */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getIsTableListFromDb()); /* 테이블목록을DB로부터읽어올지여부 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getTableNameLikeStr()); /* 테이블명을DB로부터읽어올때적용할프리픽스 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getTableListFileName()); /* 테이블목록정보파일명 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getIsSaveToDb()); /* 작업결과를DB에저장할지여부 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getAppJdkHome()); /* 분석대상어플리케이션JDK홈 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getAppClassPath()); /* 분석대상어플리케이션클래스패스 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getWorkerThreadKind()); /* 분석작업을진행할쓰레드핸들러종류 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getWorkerThreadNum()); /* 분석작업을진행할쓰레드갯수 */

			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getSysId()); /* 시스템ID */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getSysNm()); /* 시스템명 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getWrithPath()); /* 분석결과생성경로 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getSaveFileName()); /* 분석결과저장파일명 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getDbId()); /* DBID */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getIsTableListFromDb()); /* 테이블목록을DB로부터읽어올지여부 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getTableNameLikeStr()); /* 테이블명을DB로부터읽어올때적용할프리픽스 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getTableListFileName()); /* 테이블목록정보파일명 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getIsSaveToDb()); /* 작업결과를DB에저장할지여부 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getAppJdkHome()); /* 분석대상어플리케이션JDK홈 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getAppClassPath()); /* 분석대상어플리케이션클래스패스 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getWorkerThreadKind()); /* 분석작업을진행할쓰레드핸들러종류 */
			parameterIndex = setParam(db.pstmt, parameterIndex, sysVo.getWorkerThreadNum()); /* 분석작업을진행할쓰레드갯수 */


			db.pstmt.executeUpdate();
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	public static void insertTB_CLZZ(String DBID, String sysId, String[] fileList) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		int parameterIndex = 0;
		
		ClzzVo clzzVo = null;
		String subPath = AppAnalyzer.WRITE_PATH + "/class";
		int chunkSize = 500;
		
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			
			/* <클래스-TB_CLZZ> */
			db.setQuery(QUERY.INSERT_TB_CLZZ.toString());
			
			for(int i=0; i<fileList.length; i++) {
				String file = fileList[i];
				clzzVo = ParseUtil.readClassVo(file, subPath);

				if(StringUtil.isEmpty(clzzVo.getClassId())) {
					continue;
				}
				
				parameterIndex = 0;
				
				parameterIndex = setParam(db.pstmt, parameterIndex, sysId);	/* 시스템ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, clzzVo.getClassId());	/* 클래스ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, clzzVo.getPackageId());	/* 패키지ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, clzzVo.getClassName());	/* 클래스명 */
				parameterIndex = setParam(db.pstmt, parameterIndex, clzzVo.getClassKind().getClzzKindCd());	/* 기능종류(CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지) */
				parameterIndex = setParam(db.pstmt, parameterIndex, clzzVo.getResourceId());	/* 리소스ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, clzzVo.getClassOrInterface());	/* 클래스or인터페이스 */
				
				/* 상위인터페이스ID목록 */
				StringBuffer interfaceIdList = new StringBuffer();
				if(clzzVo.getInterfaceIdList() != null) {
					for(String interfaceId : clzzVo.getInterfaceIdList()) {
						if(interfaceIdList.length() > 0) {
							interfaceIdList.append(",");
						}
						interfaceIdList.append(interfaceId);
					}
				}
				parameterIndex = setParam(db.pstmt, parameterIndex, interfaceIdList.toString());	
				
				parameterIndex = setParam(db.pstmt, parameterIndex, clzzVo.getParentClassId());	/* 상위클래스ID */
				
				/* 인터페이스구현하위클래스ID목록 */
				StringBuffer implClassIdList = new StringBuffer();
				if(clzzVo.getImplClassIdList() != null) {
					for(String implClassId : clzzVo.getImplClassIdList()) {
						if(implClassIdList.length() > 0) {
							implClassIdList.append(",");
						}
						implClassIdList.append(implClassId);
					}
				}
				parameterIndex = setParam(db.pstmt, parameterIndex, implClassIdList.toString());	

				/* 호출알리아스 */
				StringBuffer callClassAlias = new StringBuffer();
				if(clzzVo.getCallClassAlias() != null) {
					for(Map<String, String> classAlias : clzzVo.getCallClassAlias()) {
						if(callClassAlias.length() > 0) {
							callClassAlias.append(",");
						}
						callClassAlias.append(classAlias.get("FULL_CLASS")+"-"+classAlias.get("ALIAS"));
					}
				}

				parameterIndex = setParam(db.pstmt, parameterIndex, callClassAlias.toString());	

				parameterIndex = setParam(db.pstmt, parameterIndex, clzzVo.getFileName());	/* 파일명 */
				
				db.pstmt.addBatch();
				if(i > 0 && i%chunkSize==0 ) {
					db.pstmt.executeBatch();
				}
			}
			
			db.pstmt.executeBatch();
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	public static void insertTB_FUNC(String DBID, String sysId, String[] fileList) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		int parameterIndex = 0;
		MtdVo mtdVo = null;
		String subPath = AppAnalyzer.WRITE_PATH + "/method";
		int chunkSize = 500;
		
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			
			/* <기능메서드-TB_FUNC> */
			db.setQuery(QUERY.INSERT_TB_FUNC.toString());

			for(int i=0; i<fileList.length; i++) {
				String file = fileList[i];
				mtdVo = ParseUtil.readMethodVo(file, subPath);
				if(StringUtil.isEmpty(mtdVo.getFunctionId())) {
					continue;
				}

				parameterIndex = 0;
				parameterIndex = setParam(db.pstmt, parameterIndex, sysId);	/* 시스템ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, mtdVo.getFunctionId());	/* 기능ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, mtdVo.getClassId());	/* 클래스ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, mtdVo.getMethodId());	/* 메서드ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, mtdVo.getMethodName());	/* 메서드명 */
				parameterIndex = setParam(db.pstmt, parameterIndex, mtdVo.getMethodUrl());	/* 메서드URL */
				parameterIndex = setParam(db.pstmt, parameterIndex, mtdVo.getFileName());	/* 파일명 */
				
				db.pstmt.addBatch();
				if(i > 0 && i%chunkSize==0 ) {
					db.pstmt.executeBatch();
				}
			}
			db.pstmt.executeBatch();
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	public static void insertTB_TBL(String DBID, String sysId) throws Exception {
		DbUtil db = null;
		DataSet dsTblList = new DataSet();
		DataSet dsTblRow = null;
		
		int parameterIndex = 0;
		int chunkSize = 500;
		
		try {
			if( AppAnalyzer.IS_TABLE_LIST_FROM_DB ) {
				dsTblList = DbUtil.getTabs(DBID);
			}else {
				List<Map<String, String>> mannalTableMapList = ParseUtil.getMannalTableMapList();
				if(mannalTableMapList != null && mannalTableMapList.size() > 0) {
					List<DataSet> dslList = new ArrayList<DataSet>();
					for(Map<String, String> mannalTableMap : mannalTableMapList) {
						dsTblRow = new  DataSet();
						dsTblRow.setDatum("TABLE_NAME", mannalTableMap.get("TABLE_NAME"));
						dsTblRow.setDatum("TABLE_COMMENT", mannalTableMap.get("TABLE_COMMENT"));
						dslList.add(dsTblRow);
					}
					dsTblList.setDataSetList("TBL_LIST", (ArrayList<DataSet>)dslList);
				}
			}

			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();

			/* <테이블-TB_TBL> */
			db.setQuery(QUERY.INSERT_TB_TBL.toString());
			
			for(int i=0; i<dsTblList.getDataSetRowCount("TBL_LIST") ; i++) {
				dsTblRow = dsTblList.getDataSet("TBL_LIST", i);
				if(StringUtil.isEmpty(dsTblRow.getDatum("TABLE_NAME"))) {
					continue;
				}
				parameterIndex = 0;
				parameterIndex = setParam(db.pstmt, parameterIndex, sysId);	/* 시스템ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, dsTblRow.getDatum("TABLE_NAME").toUpperCase());		/* 테이블ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, dsTblRow.getDatum("TABLE_OWNER"));		/* 테이블오너 */
				parameterIndex = setParam(db.pstmt, parameterIndex, StringUtil.textTail(dsTblRow.getDatum("TABLE_COMMENT"), 50));	/* 테이블명 */
				
				db.pstmt.addBatch();
				if(i > 0 && i%chunkSize==0 ) {
					db.pstmt.executeBatch();
				}
			}
			db.pstmt.executeBatch();
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	public static void insertTB_FUNC_FUNC_MAPPING(String DBID, String sysId, String[] fileList) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		int parameterIndex = 0;
		MtdVo mtdVo = null;
		String subPath = AppAnalyzer.WRITE_PATH + "/method";		
		int chunkSize = 500;
		
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();

			/* <기능간맵핑-TB_FUNC_FUNC_MAPPING> */
			db.setQuery(QUERY.INSERT_TB_FUNC_FUNC_MAPPING.toString());
			for(int i=0; i<fileList.length; i++) {
				String file = fileList[i];	
				mtdVo = ParseUtil.readMethodVo(file, subPath);
				
				if( mtdVo.getCallMtdVoList() != null && mtdVo.getCallMtdVoList().size()>0 ) {
					for(String callMtdFunctionId : mtdVo.getCallMtdVoList()) {

						if(StringUtil.isEmpty(mtdVo.getFunctionId()) || StringUtil.isEmpty(callMtdFunctionId)) {
							continue;
						}
						
						parameterIndex = 0;
						parameterIndex = setParam(db.pstmt, parameterIndex, sysId);	/* 시스템ID */
						parameterIndex = setParam(db.pstmt, parameterIndex, mtdVo.getFunctionId());	/* 기능ID */
						parameterIndex = setParam(db.pstmt, parameterIndex, callMtdFunctionId);	/* 호출기능ID */
						
						db.pstmt.addBatch();
						if(i > 0 && i%chunkSize==0 ) {
							db.pstmt.executeBatch();
						}
					}
				}
			}
			db.pstmt.executeBatch();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	public static void insertTB_FUNC_TBL_MAPPING(String DBID, String sysId, String[] fileList) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		int parameterIndex = 0;

		MtdVo mtdVo = null;
		String subPath = AppAnalyzer.WRITE_PATH + "/method";		
		int chunkSize = 500;
		
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();

			/* <테이블맵핑-TB_FUNC_TBL_MAPPING> */
			db.setQuery(QUERY.INSERT_TB_FUNC_TBL_MAPPING.toString());

			for(int i=0; i<fileList.length; i++) {
				String file = fileList[i];	
				mtdVo = ParseUtil.readMethodVo(file, subPath);
				
				if( mtdVo.getCallTblVoList() != null && mtdVo.getCallTblVoList().size()>0 ) {
					String[] words = null;
					String tblId = "";
					String jobKind = "";
					for(String callTbl : mtdVo.getCallTblVoList()) {
						if(StringUtil.isEmpty(callTbl)) {
							continue;
						}
						words = StringUtil.toStrArray(callTbl, "!");
						tblId = "";
						jobKind = "";
						if(words.length > 0) {
							tblId = words[0];
							if(tblId.indexOf(".")>-1) {
								tblId = tblId.substring(tblId.indexOf(".")+1);
							}
						}
						if(words.length > 1) {
							jobKind = words[1];
						}

						if(StringUtil.isEmpty(mtdVo.getFunctionId()) || StringUtil.isEmpty(tblId)) {
							continue;
						}
						
						parameterIndex = 0;
						parameterIndex = setParam(db.pstmt, parameterIndex, sysId);	/* 시스템ID */
						parameterIndex = setParam(db.pstmt, parameterIndex, mtdVo.getFunctionId());	/* 기능ID */
						parameterIndex = setParam(db.pstmt, parameterIndex, tblId);	/* 테이블ID */
						parameterIndex = setParam(db.pstmt, parameterIndex, jobKind);	/* 작업종류(SELECT/INSERT/UPDATE/DELETE/MERGE) */
						
						db.pstmt.addBatch();
						if(i > 0 && i%chunkSize==0 ) {
							db.pstmt.executeBatch();
						}
					}
				}
			}
			db.pstmt.executeBatch();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	public static void insertTB_UI(String DBID, String sysId, String[] fileList) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		int parameterIndex = 0;

		UiVo uiVo = null;
		String subPath = AppAnalyzer.WRITE_PATH + "/ui";
		int chunkSize = 500;
		
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			
			/* <화면-TB_UI> */
			db.setQuery(QUERY.INSERT_TB_UI.toString());

			for(int i=0; i<fileList.length; i++) {
				String file = fileList[i];	
				uiVo = ParseUtil.readUiVo(file, subPath);

				if(StringUtil.isEmpty(uiVo.getUiId())) {
					continue;
				}
				
				parameterIndex = 0;
				parameterIndex = setParam(db.pstmt, parameterIndex, sysId);	/* 시스템ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, uiVo.getUiId());	/* 화면ID */
				parameterIndex = setParam(db.pstmt, parameterIndex, uiVo.getUiName());	/* 화면명 */

				db.pstmt.addBatch();
				
				if(i > 0 && i%chunkSize==0 ) {
					db.pstmt.executeBatch();
				}
			}
			db.pstmt.executeBatch();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	public static void insertTB_UI_FUNC_MAPPING(String DBID, String sysId, String[] fileList) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		int parameterIndex = 0;

		UiVo uiVo = null;
		String subPath = AppAnalyzer.WRITE_PATH + "/ui";
		int chunkSize = 500;

		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();

			/* <화면링크맵핑-TB_UI_FUNC_MAPPING> */
			db.setQuery(QUERY.INSERT_TB_UI_FUNC_MAPPING.toString());

			for(int i=0; i<fileList.length; i++) {
				String file = fileList[i];	
				uiVo = ParseUtil.readUiVo(file, subPath);

				if( uiVo.getLinkList() != null && uiVo.getLinkList().size()>0 ) {
					for(String link : uiVo.getLinkList()) {

						if(StringUtil.isEmpty(uiVo.getUiId())) {
							continue;
						}
						
						parameterIndex = 0;
						parameterIndex = setParam(db.pstmt, parameterIndex, sysId);	/* 시스템ID */
						parameterIndex = setParam(db.pstmt, parameterIndex, uiVo.getUiId());	/* 화면ID */
						parameterIndex = setParam(db.pstmt, parameterIndex, link);	/* 링크 */
						
						db.pstmt.addBatch();
						if(i > 0 && i%chunkSize==0 ) {
							db.pstmt.executeBatch();
						}
					}
				}
			}
			db.pstmt.executeBatch();
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	public static void insertTB_METRIX(String DBID, String sysId) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		int parameterIndex = 0;
		int chunkSize = 500;
		
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();

			/* <종합메트릭스-TB_METRIX> */
			db.setQuery(QUERY.INSERT_TB_METRIX.toString());
			String[] lines = FileUtil.readFileByLines(AppAnalyzer.WRITE_PATH + "/AppMetrix.ouput");
			if(lines != null) {
				String line = "";
				String[] cols = null;
				String col = null;
				String[] colVals = null;
				String colVal = null;
				DataSet dsRow = null;
				int lineNum = 0;
				for(int i=0; i<lines.length; i++) {
					line = lines[i];
					if(StringUtil.isEmpty(line)) {continue;}
					if(lineNum == 0) {
						cols = StringUtil.toStrArray(line, "\t");
					}else {
						colVals = StringUtil.toStrArray(line, "\t");
						dsRow = new DataSet();
						for(int k=0; k<colVals.length; k++) {
							col = cols[k];
							colVal = colVals[k];
							dsRow.setDatum(col, colVal);
						}
						parameterIndex = 0;
						parameterIndex = setParam(db.pstmt, parameterIndex, String.valueOf(i));	/* 시퀀스 */
						parameterIndex = setParam(db.pstmt, parameterIndex, sysId);	/* 시스템ID */
						parameterIndex = setParam(db.pstmt, parameterIndex, dsRow.getDatum("UI_ID"));	/* 화면ID */
						parameterIndex = setParam(db.pstmt, parameterIndex, dsRow.getDatum("UI_NM"));	/* 화면명 */
						parameterIndex = setParam(db.pstmt, parameterIndex, dsRow.getDatum("BASIC_URL"));	/* 기준URL */
						for(int k=1; k<=FUNC_DEPTH_CNT; k++) {
							parameterIndex = setParam(db.pstmt, parameterIndex, dsRow.getDatum("FUNCTION_ID_"+k, ""));	/* 기능ID */
							parameterIndex = setParam(db.pstmt, parameterIndex, dsRow.getDatum("FUNCTION_NAME_"+k, ""));	/* 기능명 */
							parameterIndex = setParam(db.pstmt, parameterIndex, dsRow.getDatum("CLASS_KIND_"+k, ""));	/* 클래스종류 */
						}
						parameterIndex = setParam(db.pstmt, parameterIndex, dsRow.getDatum("CALL_TBL"));	/* 호출테이블 */
						
						db.pstmt.addBatch();
						if(i > 0 && i%chunkSize==0 ) {
							db.pstmt.executeBatch();
						}
					}
					lineNum++;
				}
			}
			db.pstmt.executeBatch();
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	public static void deleteAll(String DBID, String sysId) throws Exception {
		deleteTB_UI_FUNC_MAPPING(DBID, sysId);
		deleteTB_UI(DBID, sysId);
		deleteTB_FUNC_TBL_MAPPING(DBID, sysId);
		deleteTB_FUNC_FUNC_MAPPING(DBID, sysId);
		deleteTB_TBL(DBID, sysId);
		deleteTB_FUNC(DBID, sysId);
		deleteTB_CLZZ(DBID, sysId);
		deleteTB_METRIX(DBID, sysId);
	}
	
	private static void deleteTB_CLZZ(String DBID, String sysId) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.DELETE_TB_CLZZ.toString());
			db.pstmt.setString(1, sysId);
			db.delete();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	private static void deleteTB_FUNC(String DBID, String sysId) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.DELETE_TB_FUNC.toString());
			db.pstmt.setString(1, sysId);
			db.delete();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	private static void deleteTB_TBL(String DBID, String sysId) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.DELETE_TB_TBL.toString());
			db.pstmt.setString(1, sysId);
			db.delete();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}

	private static void deleteTB_FUNC_FUNC_MAPPING(String DBID, String sysId) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.DELETE_TB_FUNC_FUNC_MAPPING.toString());
			db.pstmt.setString(1, sysId);
			db.delete();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}

	private static void deleteTB_FUNC_TBL_MAPPING(String DBID, String sysId) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.DELETE_TB_FUNC_TBL_MAPPING.toString());
			db.pstmt.setString(1, sysId);
			db.delete();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}

	private static void deleteTB_UI(String DBID, String sysId) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.DELETE_TB_UI.toString());
			db.pstmt.setString(1, sysId);
			db.delete();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}

	private static void deleteTB_UI_FUNC_MAPPING(String DBID, String sysId) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.DELETE_TB_UI_FUNC_MAPPING.toString());
			db.pstmt.setString(1, sysId);
			db.delete();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	private static void deleteTB_METRIX(String DBID, String sysId) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.DELETE_TB_METRIX.toString());
			db.pstmt.setString(1, sysId);
			db.delete();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				db.release();
			}
		}
	}
	
	public static net.dstone.common.utils.DataSet selectTB_FUNC_ALL(String DBID, String FUNC_ID, String CLZZ_KIND, String FUNC_RECURSIVE_YN, String TBL_RECURSIVE_YN) throws Exception {
		net.dstone.common.utils.DataSet ds = new net.dstone.common.utils.DataSet();
		net.dstone.common.utils.DbUtil db = null;
		int parameterIndex = 0;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			
			/* <기능조회ALL> */
			db.setQuery(QUERY.SELECT_TB_FUNC_ALL.toString());
			
			parameterIndex = 0;
			parameterIndex = setParam(db.pstmt, parameterIndex, StringUtil.nullCheck(FUNC_RECURSIVE_YN, "N"));	/* 기능ID 재귀조회여부 */
			parameterIndex = setParam(db.pstmt, parameterIndex, StringUtil.nullCheck(TBL_RECURSIVE_YN, "N"));	/* 테이블ID 재귀조회여부 */
			db.pstmt.setString(++parameterIndex, StringUtil.nullCheck(FUNC_ID, ""));	/* 기능ID */
			db.pstmt.setString(++parameterIndex, StringUtil.nullCheck(CLZZ_KIND, ""));	/* 기능종류(CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지) */

			ds.buildFromResultSet(db.select(), "FUNC_LIST");
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(db != null) {
				LogUtil.sysout(db.getQuery());
				db.release();
			}
		}
		return ds;
	}
	
}
