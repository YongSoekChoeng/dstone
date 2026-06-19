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

		/* ===== DB 직접 저장 전환 후 추가된 SQL ===== */
		public static StringBuffer INSERT_TB_UI_WITH_FILE     = new StringBuffer();
		public static StringBuffer INSERT_TB_FUNC_WITH_BODY   = new StringBuffer();
		public static StringBuffer INSERT_TB_QUERY            = new StringBuffer();
		public static StringBuffer DELETE_TB_QUERY            = new StringBuffer();
		public static StringBuffer UPDATE_TB_CLZZ_IMPL        = new StringBuffer();
		public static StringBuffer UPDATE_TB_CLZZ_ALIAS       = new StringBuffer();
		public static StringBuffer UPDATE_TB_QUERY_CALLTBL    = new StringBuffer();
		public static StringBuffer SELECT_ALL_CLZZ            = new StringBuffer();
		public static StringBuffer SELECT_ALL_MTD_WITH_CALLS  = new StringBuffer();
		public static StringBuffer SELECT_CTL_MTD_WITH_URL    = new StringBuffer();
		public static StringBuffer SELECT_ALL_UI              = new StringBuffer();
		public static StringBuffer SELECT_ALL_UI_LINKS        = new StringBuffer();
		public static StringBuffer SELECT_ALL_QUERY_WITH_TBL  = new StringBuffer();
		public static StringBuffer SELECT_ALL_FUNC_ID         = new StringBuffer();

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

			/* ===== DB 직접 저장 전환 후 추가된 SQL 초기화 ===== */

			/* <화면(FILE_NAME 포함)-TB_UI> */
			INSERT_TB_UI_WITH_FILE.append("INSERT INTO TB_UI ( SYS_ID, UI_ID, UI_NM, FILE_NAME, WORKER_ID ) VALUES ( ?, ?, ?, ?, 'SYSTEM' ) ").append("\n");

			/* <기능메서드(MTD_BODY 포함)-TB_FUNC> */
			INSERT_TB_FUNC_WITH_BODY.append("INSERT INTO TB_FUNC ( ").append("\n");
			INSERT_TB_FUNC_WITH_BODY.append("	SYS_ID, FUNC_ID, CLZZ_ID, MTD_ID, MTD_NM, MTD_URL, MTD_BODY, FILE_NAME, WORKER_ID ").append("\n");
			INSERT_TB_FUNC_WITH_BODY.append(") VALUES ( ").append("\n");
			INSERT_TB_FUNC_WITH_BODY.append("	?, ?, ?, ?, ?, ?, ?, ?, 'SYSTEM' ").append("\n");
			INSERT_TB_FUNC_WITH_BODY.append(") ").append("\n");

			/* <쿼리-TB_QUERY> */
			INSERT_TB_QUERY.append("INSERT INTO TB_QUERY ( ").append("\n");
			INSERT_TB_QUERY.append("	SYS_ID, SQL_KEY, SQL_NAMESPACE, SQL_ID, SQL_KIND, SQL_BODY, WORKER_ID ").append("\n");
			INSERT_TB_QUERY.append(") VALUES ( ?, ?, ?, ?, ?, ?, 'SYSTEM' ) ").append("\n");

			DELETE_TB_QUERY.append("DELETE FROM TB_QUERY WHERE SYS_ID = ? AND WORKER_ID = 'SYSTEM' ").append("\n");

			/* <TB_CLZZ UPDATE - 인터페이스구현하위클래스목록> */
			UPDATE_TB_CLZZ_IMPL.append("UPDATE TB_CLZZ SET INTF_IMPL_CLZZ_ID_LIST = ? WHERE SYS_ID = ? AND CLZZ_ID = ? ").append("\n");

			/* <TB_CLZZ UPDATE - 호출알리아스> */
			UPDATE_TB_CLZZ_ALIAS.append("UPDATE TB_CLZZ SET MEMBER_ALIAS_LIST = ? WHERE SYS_ID = ? AND CLZZ_ID = ? ").append("\n");

			/* <TB_QUERY UPDATE - 호출테이블목록> */
			UPDATE_TB_QUERY_CALLTBL.append("UPDATE TB_QUERY SET CALL_TBL_LIST = ? WHERE SYS_ID = ? AND SQL_KEY = ? ").append("\n");

			/* <TB_CLZZ SELECT ALL> */
			SELECT_ALL_CLZZ.append("SELECT CLZZ_ID, PKG_ID, CLZZ_NM, CLZZ_KIND, RESOURCE_ID, CLZZ_INTF, ").append("\n");
			SELECT_ALL_CLZZ.append("       INTF_ID_LIST, PARENT_CLZZ_ID, INTF_IMPL_CLZZ_ID_LIST, MEMBER_ALIAS_LIST, FILE_NAME ").append("\n");
			SELECT_ALL_CLZZ.append("FROM TB_CLZZ WHERE SYS_ID = ? ").append("\n");

			/* <TB_FUNC SELECT ALL + CALL LISTS (METRIX 캐시용, MTD_BODY 제외)> */
			SELECT_ALL_MTD_WITH_CALLS.append("SELECT f.FUNC_ID, f.CLZZ_ID, f.MTD_ID, f.MTD_NM, f.MTD_URL, ").append("\n");
			SELECT_ALL_MTD_WITH_CALLS.append("  GROUP_CONCAT(DISTINCT fm.CALL_FUNC_ID ORDER BY fm.CALL_FUNC_ID SEPARATOR ',') AS CALL_MTD_LIST, ").append("\n");
			SELECT_ALL_MTD_WITH_CALLS.append("  GROUP_CONCAT(CONCAT(tm.TBL_ID,'!',IFNULL(tm.JOB_KIND,'')) ORDER BY tm.TBL_ID SEPARATOR '|') AS CALL_TBL_LIST ").append("\n");
			SELECT_ALL_MTD_WITH_CALLS.append("FROM TB_FUNC f ").append("\n");
			SELECT_ALL_MTD_WITH_CALLS.append("LEFT JOIN TB_FUNC_FUNC_MAPPING fm ON f.SYS_ID=fm.SYS_ID AND f.FUNC_ID=fm.FUNC_ID ").append("\n");
			SELECT_ALL_MTD_WITH_CALLS.append("LEFT JOIN TB_FUNC_TBL_MAPPING tm ON f.SYS_ID=tm.SYS_ID AND f.FUNC_ID=tm.FUNC_ID ").append("\n");
			SELECT_ALL_MTD_WITH_CALLS.append("WHERE f.SYS_ID=? ").append("\n");
			SELECT_ALL_MTD_WITH_CALLS.append("GROUP BY f.FUNC_ID, f.CLZZ_ID, f.MTD_ID, f.MTD_NM, f.MTD_URL ").append("\n");

			/* <CT 클래스 메서드 + URL (METRIX 기본구조용)> */
			SELECT_CTL_MTD_WITH_URL.append("SELECT f.FUNC_ID, f.CLZZ_ID, f.MTD_NM, f.MTD_URL ").append("\n");
			SELECT_CTL_MTD_WITH_URL.append("FROM TB_FUNC f ").append("\n");
			SELECT_CTL_MTD_WITH_URL.append("JOIN TB_CLZZ c ON f.SYS_ID=c.SYS_ID AND f.CLZZ_ID=c.CLZZ_ID ").append("\n");
			SELECT_CTL_MTD_WITH_URL.append("WHERE f.SYS_ID=? AND c.CLZZ_KIND='CT' ").append("\n");
			SELECT_CTL_MTD_WITH_URL.append("  AND f.MTD_URL IS NOT NULL AND f.MTD_URL <> '' ").append("\n");

			/* <TB_UI SELECT ALL> */
			SELECT_ALL_UI.append("SELECT UI_ID, UI_NM, FILE_NAME FROM TB_UI WHERE SYS_ID=? ").append("\n");

			/* <TB_UI_FUNC_MAPPING SELECT ALL> */
			SELECT_ALL_UI_LINKS.append("SELECT UI_ID, MTD_URL FROM TB_UI_FUNC_MAPPING WHERE SYS_ID=? ").append("\n");

			/* <TB_QUERY SELECT ALL (C-3 캐시용, SQL_BODY 제외)> */
			SELECT_ALL_QUERY_WITH_TBL.append("SELECT SQL_KEY, SQL_NAMESPACE, SQL_ID, SQL_KIND, CALL_TBL_LIST ").append("\n");
			SELECT_ALL_QUERY_WITH_TBL.append("FROM TB_QUERY WHERE SYS_ID=? ").append("\n");

			/* <TB_FUNC FUNC_ID 목록 (C-2, C-3 청크 처리용)> */
			SELECT_ALL_FUNC_ID.append("SELECT FUNC_ID FROM TB_FUNC WHERE SYS_ID=? ORDER BY FUNC_ID ").append("\n");

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
		deleteTB_QUERY(DBID, sysId);
		deleteTB_CLZZ(DBID, sysId);
		deleteTB_METRIX(DBID, sysId);
	}

	private static void deleteTB_QUERY(String DBID, String sysId) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.DELETE_TB_QUERY.toString());
			db.pstmt.setString(1, sysId);
			db.delete();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if (db != null) db.release();
		}
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
	
	/* =========================================================
	 * DB 직접 저장 전환 후 추가된 메서드
	 * ========================================================= */

	/** A-1: TB_CLZZ 배치 INSERT */
	public static void insertBatchTB_CLZZ(String DBID, String sysId, java.util.List<net.dstone.boot.common.tools.analyzer.vo.ClzzVo> list) throws Exception {
		if (list == null || list.isEmpty()) return;
		net.dstone.common.utils.DbUtil db = null;
		int chunkSize = 500;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.INSERT_TB_CLZZ.toString());
			for (int i = 0; i < list.size(); i++) {
				net.dstone.boot.common.tools.analyzer.vo.ClzzVo v = list.get(i);
				if (StringUtil.isEmpty(v.getClassId())) continue;
				int p = 0;
				p = setParam(db.pstmt, p, sysId);
				p = setParam(db.pstmt, p, v.getClassId());
				p = setParam(db.pstmt, p, v.getPackageId());
				p = setParam(db.pstmt, p, v.getClassName());
				p = setParam(db.pstmt, p, v.getClassKind() != null ? v.getClassKind().getClzzKindCd() : null);
				p = setParam(db.pstmt, p, v.getResourceId());
				p = setParam(db.pstmt, p, v.getClassOrInterface());
				p = setParam(db.pstmt, p, joinList(v.getInterfaceIdList(), ","));
				p = setParam(db.pstmt, p, v.getParentClassId());
				p = setParam(db.pstmt, p, joinList(v.getImplClassIdList(), ","));
				p = setParam(db.pstmt, p, serializeAlias(v.getCallClassAlias()));
				p = setParam(db.pstmt, p, v.getFileName());
				db.pstmt.addBatch();
				if (i > 0 && i % chunkSize == 0) db.pstmt.executeBatch();
			}
			db.pstmt.executeBatch();
		} finally {
			if (db != null) db.release();
		}
	}

	/** A-2: TB_CLZZ 인터페이스구현하위클래스목록 UPDATE */
	public static void updateTB_CLZZ_IMPL(String DBID, String sysId, String clzzId, java.util.List<String> implList) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.UPDATE_TB_CLZZ_IMPL.toString());
			setParam(db.pstmt, 0, joinList(implList, ","));
			db.pstmt.setString(2, sysId);
			db.pstmt.setString(3, clzzId);
			db.pstmt.executeUpdate();
		} finally {
			if (db != null) db.release();
		}
	}

	/** A-3: TB_CLZZ 호출알리아스 UPDATE */
	public static void updateTB_CLZZ_ALIAS(String DBID, String sysId, String clzzId, java.util.List<java.util.Map<String, String>> aliasList) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.UPDATE_TB_CLZZ_ALIAS.toString());
			setParam(db.pstmt, 0, serializeAlias(aliasList));
			db.pstmt.setString(2, sysId);
			db.pstmt.setString(3, clzzId);
			db.pstmt.executeUpdate();
		} finally {
			if (db != null) db.release();
		}
	}

	/** B-1: TB_QUERY 배치 INSERT */
	public static void insertBatchTB_QUERY(String DBID, String sysId, java.util.List<net.dstone.boot.common.tools.analyzer.vo.QueryVo> list) throws Exception {
		if (list == null || list.isEmpty()) return;
		net.dstone.common.utils.DbUtil db = null;
		int chunkSize = 500;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.INSERT_TB_QUERY.toString());
			for (int i = 0; i < list.size(); i++) {
				net.dstone.boot.common.tools.analyzer.vo.QueryVo v = list.get(i);
				if (StringUtil.isEmpty(v.getKey())) continue;
				int p = 0;
				p = setParam(db.pstmt, p, sysId);
				p = setParam(db.pstmt, p, v.getKey());
				p = setParam(db.pstmt, p, v.getNamespace());
				p = setParam(db.pstmt, p, v.getQueryId());
				p = setParam(db.pstmt, p, v.getQueryKind());
				p = setParam(db.pstmt, p, v.getQueryBody());
				db.pstmt.addBatch();
				if (i > 0 && i % chunkSize == 0) db.pstmt.executeBatch();
			}
			db.pstmt.executeBatch();
		} finally {
			if (db != null) db.release();
		}
	}

	/** B-2: TB_QUERY 호출테이블목록 UPDATE */
	public static void updateTB_QUERY_CALLTBL(String DBID, String sysId, String sqlKey, java.util.List<String> tblList) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.UPDATE_TB_QUERY_CALLTBL.toString());
			setParam(db.pstmt, 0, joinList(tblList, ","));
			db.pstmt.setString(2, sysId);
			db.pstmt.setString(3, sqlKey);
			db.pstmt.executeUpdate();
		} finally {
			if (db != null) db.release();
		}
	}

	/** C-1: TB_FUNC 배치 INSERT (MTD_BODY 포함) */
	public static void insertBatchTB_FUNC(String DBID, String sysId, java.util.List<net.dstone.boot.common.tools.analyzer.vo.MtdVo> list) throws Exception {
		if (list == null || list.isEmpty()) return;
		net.dstone.common.utils.DbUtil db = null;
		int chunkSize = 200;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.INSERT_TB_FUNC_WITH_BODY.toString());
			for (int i = 0; i < list.size(); i++) {
				net.dstone.boot.common.tools.analyzer.vo.MtdVo v = list.get(i);
				if (StringUtil.isEmpty(v.getFunctionId())) continue;
				int p = 0;
				p = setParam(db.pstmt, p, sysId);
				p = setParam(db.pstmt, p, v.getFunctionId());
				p = setParam(db.pstmt, p, v.getClassId());
				p = setParam(db.pstmt, p, v.getMethodId());
				p = setParam(db.pstmt, p, v.getMethodName());
				p = setParam(db.pstmt, p, v.getMethodUrl());
				p = setParam(db.pstmt, p, v.getMethodBody());
				p = setParam(db.pstmt, p, v.getFileName());
				db.pstmt.addBatch();
				if (i > 0 && i % chunkSize == 0) db.pstmt.executeBatch();
			}
			db.pstmt.executeBatch();
		} finally {
			if (db != null) db.release();
		}
	}

	/** C-2: TB_FUNC_FUNC_MAPPING 배치 INSERT. entries = List of [funcId, callFuncId] */
	public static void insertBatchTB_FUNC_FUNC_MAPPING(String DBID, String sysId, java.util.List<String[]> entries) throws Exception {
		if (entries == null || entries.isEmpty()) return;
		net.dstone.common.utils.DbUtil db = null;
		int chunkSize = 500;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.INSERT_TB_FUNC_FUNC_MAPPING.toString());
			for (int i = 0; i < entries.size(); i++) {
				String[] e = entries.get(i);
				if (StringUtil.isEmpty(e[0]) || StringUtil.isEmpty(e[1])) continue;
				int p = 0;
				p = setParam(db.pstmt, p, sysId);
				p = setParam(db.pstmt, p, e[0]);
				p = setParam(db.pstmt, p, e[1]);
				db.pstmt.addBatch();
				if (i > 0 && i % chunkSize == 0) db.pstmt.executeBatch();
			}
			db.pstmt.executeBatch();
		} finally {
			if (db != null) db.release();
		}
	}

	/** C-3: TB_FUNC_TBL_MAPPING 배치 INSERT. entries = List of [funcId, tblId, jobKind] */
	public static void insertBatchTB_FUNC_TBL_MAPPING(String DBID, String sysId, java.util.List<String[]> entries) throws Exception {
		if (entries == null || entries.isEmpty()) return;
		net.dstone.common.utils.DbUtil db = null;
		int chunkSize = 500;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.INSERT_TB_FUNC_TBL_MAPPING.toString());
			for (int i = 0; i < entries.size(); i++) {
				String[] e = entries.get(i);
				if (StringUtil.isEmpty(e[0]) || StringUtil.isEmpty(e[1])) continue;
				int p = 0;
				p = setParam(db.pstmt, p, sysId);
				p = setParam(db.pstmt, p, e[0]);
				p = setParam(db.pstmt, p, e[1]);
				p = setParam(db.pstmt, p, e.length > 2 ? e[2] : null);
				db.pstmt.addBatch();
				if (i > 0 && i % chunkSize == 0) db.pstmt.executeBatch();
			}
			db.pstmt.executeBatch();
		} finally {
			if (db != null) db.release();
		}
	}

	/** D-1: TB_UI 배치 INSERT (FILE_NAME 포함) */
	public static void insertBatchTB_UI(String DBID, String sysId, java.util.List<net.dstone.boot.common.tools.analyzer.vo.UiVo> list) throws Exception {
		if (list == null || list.isEmpty()) return;
		net.dstone.common.utils.DbUtil db = null;
		int chunkSize = 500;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.INSERT_TB_UI_WITH_FILE.toString());
			for (int i = 0; i < list.size(); i++) {
				net.dstone.boot.common.tools.analyzer.vo.UiVo v = list.get(i);
				if (StringUtil.isEmpty(v.getUiId())) continue;
				int p = 0;
				p = setParam(db.pstmt, p, sysId);
				p = setParam(db.pstmt, p, v.getUiId());
				p = setParam(db.pstmt, p, v.getUiName());
				p = setParam(db.pstmt, p, v.getFileName());
				db.pstmt.addBatch();
				if (i > 0 && i % chunkSize == 0) db.pstmt.executeBatch();
			}
			db.pstmt.executeBatch();
		} finally {
			if (db != null) db.release();
		}
	}

	/** D-2: TB_UI_FUNC_MAPPING 배치 INSERT. entries = List of [uiId, linkUrl] */
	public static void insertBatchTB_UI_FUNC_MAPPING(String DBID, String sysId, java.util.List<String[]> entries) throws Exception {
		if (entries == null || entries.isEmpty()) return;
		net.dstone.common.utils.DbUtil db = null;
		int chunkSize = 500;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.INSERT_TB_UI_FUNC_MAPPING.toString());
			for (int i = 0; i < entries.size(); i++) {
				String[] e = entries.get(i);
				if (StringUtil.isEmpty(e[0]) || StringUtil.isEmpty(e[1])) continue;
				int p = 0;
				p = setParam(db.pstmt, p, sysId);
				p = setParam(db.pstmt, p, e[0]);
				p = setParam(db.pstmt, p, e[1]);
				db.pstmt.addBatch();
				if (i > 0 && i % chunkSize == 0) db.pstmt.executeBatch();
			}
			db.pstmt.executeBatch();
		} finally {
			if (db != null) db.release();
		}
	}

	/** F: TB_METRIX 배치 INSERT */
	public static void insertBatchTB_METRIX(String DBID, String sysId, java.util.List<DataSet> metrixList) throws Exception {
		if (metrixList == null || metrixList.isEmpty()) return;
		net.dstone.common.utils.DbUtil db = null;
		int chunkSize = 500;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.INSERT_TB_METRIX.toString());
			for (int i = 0; i < metrixList.size(); i++) {
				DataSet row = metrixList.get(i);
				int p = 0;
				p = setParam(db.pstmt, p, String.valueOf(i + 1));
				p = setParam(db.pstmt, p, sysId);
				p = setParam(db.pstmt, p, row.getDatum("UI_ID"));
				p = setParam(db.pstmt, p, row.getDatum("UI_NM"));
				p = setParam(db.pstmt, p, row.getDatum("BASIC_URL"));
				for (int k = 1; k <= FUNC_DEPTH_CNT; k++) {
					p = setParam(db.pstmt, p, row.getDatum("FUNCTION_ID_" + k, ""));
					p = setParam(db.pstmt, p, row.getDatum("FUNCTION_NAME_" + k, ""));
					p = setParam(db.pstmt, p, row.getDatum("CLASS_KIND_" + k, ""));
				}
				p = setParam(db.pstmt, p, row.getDatum("CALL_TBL"));
				db.pstmt.addBatch();
				if (i > 0 && i % chunkSize == 0) db.pstmt.executeBatch();
			}
			db.pstmt.executeBatch();
		} finally {
			if (db != null) db.release();
		}
	}

	/* ---- SELECT 메서드 ---- */

	/** A-2, A-3, C-2 캐시용: 전체 ClzzVo 목록 */
	public static java.util.List<net.dstone.boot.common.tools.analyzer.vo.ClzzVo> selectAllClzzVo(String DBID, String sysId) throws Exception {
		java.util.List<net.dstone.boot.common.tools.analyzer.vo.ClzzVo> result = new java.util.ArrayList<>();
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.SELECT_ALL_CLZZ.toString());
			db.pstmt.setString(1, sysId);
			java.sql.ResultSet rs = db.select();
			while (rs.next()) {
				net.dstone.boot.common.tools.analyzer.vo.ClzzVo v = new net.dstone.boot.common.tools.analyzer.vo.ClzzVo();
				v.setClassId(rs.getString("CLZZ_ID"));
				v.setPackageId(rs.getString("PKG_ID"));
				v.setClassName(rs.getString("CLZZ_NM"));
				String kind = rs.getString("CLZZ_KIND");
				if (!StringUtil.isEmpty(kind)) v.setClassKind(net.dstone.boot.common.tools.analyzer.consts.ClzzKind.getClzzKindCd(kind));
				v.setResourceId(rs.getString("RESOURCE_ID"));
				v.setClassOrInterface(rs.getString("CLZZ_INTF"));
				v.setInterfaceIdList(splitToList(rs.getString("INTF_ID_LIST"), ","));
				v.setParentClassId(rs.getString("PARENT_CLZZ_ID"));
				v.setImplClassIdList(splitToList(rs.getString("INTF_IMPL_CLZZ_ID_LIST"), ","));
				v.setCallClassAlias(deserializeAlias(rs.getString("MEMBER_ALIAS_LIST")));
				v.setFileName(rs.getString("FILE_NAME"));
				result.add(v);
			}
		} finally {
			if (db != null) db.release();
		}
		return result;
	}

	/** METRIX 캐시용: 전체 MtdVo + call lists (MTD_BODY 제외) */
	public static java.util.List<net.dstone.boot.common.tools.analyzer.vo.MtdVo> selectAllMtdVoWithCalls(String DBID, String sysId) throws Exception {
		java.util.List<net.dstone.boot.common.tools.analyzer.vo.MtdVo> result = new java.util.ArrayList<>();
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.SELECT_ALL_MTD_WITH_CALLS.toString());
			db.pstmt.setString(1, sysId);
			java.sql.ResultSet rs = db.select();
			while (rs.next()) {
				net.dstone.boot.common.tools.analyzer.vo.MtdVo v = new net.dstone.boot.common.tools.analyzer.vo.MtdVo();
				v.setFunctionId(rs.getString("FUNC_ID"));
				v.setClassId(rs.getString("CLZZ_ID"));
				v.setMethodId(rs.getString("MTD_ID"));
				v.setMethodName(rs.getString("MTD_NM"));
				v.setMethodUrl(rs.getString("MTD_URL"));
				v.setCallMtdVoList(splitToList(rs.getString("CALL_MTD_LIST"), ","));
				v.setCallTblVoList(splitToList(rs.getString("CALL_TBL_LIST"), "\\|"));
				result.add(v);
			}
		} finally {
			if (db != null) db.release();
		}
		return result;
	}

	/** METRIX 기본구조용: CT 클래스 메서드 + URL */
	public static java.util.List<net.dstone.boot.common.tools.analyzer.vo.MtdVo> selectCtMtdWithUrl(String DBID, String sysId) throws Exception {
		java.util.List<net.dstone.boot.common.tools.analyzer.vo.MtdVo> result = new java.util.ArrayList<>();
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.SELECT_CTL_MTD_WITH_URL.toString());
			db.pstmt.setString(1, sysId);
			java.sql.ResultSet rs = db.select();
			while (rs.next()) {
				net.dstone.boot.common.tools.analyzer.vo.MtdVo v = new net.dstone.boot.common.tools.analyzer.vo.MtdVo();
				v.setFunctionId(rs.getString("FUNC_ID"));
				v.setClassId(rs.getString("CLZZ_ID"));
				v.setMethodName(rs.getString("MTD_NM"));
				v.setMethodUrl(rs.getString("MTD_URL"));
				result.add(v);
			}
		} finally {
			if (db != null) db.release();
		}
		return result;
	}

	/** D-2 이후 METRIX용: 전체 UI 목록 */
	public static java.util.List<net.dstone.boot.common.tools.analyzer.vo.UiVo> selectAllUiVo(String DBID, String sysId) throws Exception {
		java.util.List<net.dstone.boot.common.tools.analyzer.vo.UiVo> result = new java.util.ArrayList<>();
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.SELECT_ALL_UI.toString());
			db.pstmt.setString(1, sysId);
			java.sql.ResultSet rs = db.select();
			while (rs.next()) {
				net.dstone.boot.common.tools.analyzer.vo.UiVo v = new net.dstone.boot.common.tools.analyzer.vo.UiVo();
				v.setUiId(rs.getString("UI_ID"));
				v.setUiName(rs.getString("UI_NM"));
				v.setFileName(rs.getString("FILE_NAME"));
				result.add(v);
			}
		} finally {
			if (db != null) db.release();
		}
		return result;
	}

	/** METRIX용: UI 링크 맵 (uiId → List<link>) */
	public static java.util.Map<String, java.util.List<String>> selectAllUiLinks(String DBID, String sysId) throws Exception {
		java.util.Map<String, java.util.List<String>> result = new java.util.LinkedHashMap<>();
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.SELECT_ALL_UI_LINKS.toString());
			db.pstmt.setString(1, sysId);
			java.sql.ResultSet rs = db.select();
			while (rs.next()) {
				String uiId = rs.getString("UI_ID");
				String link = rs.getString("MTD_URL");
				result.computeIfAbsent(uiId, k -> new java.util.ArrayList<>()).add(link);
			}
		} finally {
			if (db != null) db.release();
		}
		return result;
	}

	/** C-3 캐시용: 전체 QueryVo 맵 (sqlKey → QueryVo, SQL_BODY 제외) */
	public static java.util.Map<String, net.dstone.boot.common.tools.analyzer.vo.QueryVo> selectAllQueryVoMap(String DBID, String sysId) throws Exception {
		java.util.Map<String, net.dstone.boot.common.tools.analyzer.vo.QueryVo> result = new java.util.LinkedHashMap<>();
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.SELECT_ALL_QUERY_WITH_TBL.toString());
			db.pstmt.setString(1, sysId);
			java.sql.ResultSet rs = db.select();
			while (rs.next()) {
				net.dstone.boot.common.tools.analyzer.vo.QueryVo v = new net.dstone.boot.common.tools.analyzer.vo.QueryVo();
				v.setKey(rs.getString("SQL_KEY"));
				v.setNamespace(rs.getString("SQL_NAMESPACE"));
				v.setQueryId(rs.getString("SQL_ID"));
				v.setQueryKind(rs.getString("SQL_KIND"));
				v.setCallTblList(splitToList(rs.getString("CALL_TBL_LIST"), ","));
				result.put(v.getKey(), v);
			}
		} finally {
			if (db != null) db.release();
		}
		return result;
	}

	/** C-2, C-3 청크 처리용: 전체 FUNC_ID 목록 */
	public static java.util.List<String> selectAllFuncId(String DBID, String sysId) throws Exception {
		java.util.List<String> result = new java.util.ArrayList<>();
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(QUERY.SELECT_ALL_FUNC_ID.toString());
			db.pstmt.setString(1, sysId);
			java.sql.ResultSet rs = db.select();
			while (rs.next()) result.add(rs.getString("FUNC_ID"));
		} finally {
			if (db != null) db.release();
		}
		return result;
	}

	/** C-2, C-3 청크 처리용: FUNC_ID 목록으로 MtdVo + MTD_BODY SELECT */
	public static java.util.List<net.dstone.boot.common.tools.analyzer.vo.MtdVo> selectMtdVoWithBodyByIds(String DBID, String sysId, java.util.List<String> funcIds) throws Exception {
		java.util.List<net.dstone.boot.common.tools.analyzer.vo.MtdVo> result = new java.util.ArrayList<>();
		if (funcIds == null || funcIds.isEmpty()) return result;
		net.dstone.common.utils.DbUtil db = null;
		try {
			StringBuilder placeholders = new StringBuilder();
			for (int i = 0; i < funcIds.size(); i++) {
				if (i > 0) placeholders.append(",");
				placeholders.append("?");
			}
			String sql = "SELECT FUNC_ID, CLZZ_ID, MTD_BODY FROM TB_FUNC WHERE SYS_ID=? AND FUNC_ID IN (" + placeholders + ")";
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(sql);
			db.pstmt.setString(1, sysId);
			for (int i = 0; i < funcIds.size(); i++) {
				db.pstmt.setString(i + 2, funcIds.get(i));
			}
			java.sql.ResultSet rs = db.select();
			while (rs.next()) {
				net.dstone.boot.common.tools.analyzer.vo.MtdVo v = new net.dstone.boot.common.tools.analyzer.vo.MtdVo();
				v.setFunctionId(rs.getString("FUNC_ID"));
				v.setClassId(rs.getString("CLZZ_ID"));
				v.setMethodBody(rs.getString("MTD_BODY"));
				result.add(v);
			}
		} finally {
			if (db != null) db.release();
		}
		return result;
	}

	/** B-2 처리용: 전체 QueryVo SELECT (SQL_BODY 포함) */
	public static java.util.List<net.dstone.boot.common.tools.analyzer.vo.QueryVo> selectAllQueryVoWithBody(String DBID, String sysId) throws Exception {
		java.util.List<net.dstone.boot.common.tools.analyzer.vo.QueryVo> result = new java.util.ArrayList<>();
		net.dstone.common.utils.DbUtil db = null;
		try {
			String sql = "SELECT SQL_KEY, SQL_NAMESPACE, SQL_ID, SQL_KIND, SQL_BODY FROM TB_QUERY WHERE SYS_ID=?";
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery(sql);
			db.pstmt.setString(1, sysId);
			java.sql.ResultSet rs = db.select();
			while (rs.next()) {
				net.dstone.boot.common.tools.analyzer.vo.QueryVo v = new net.dstone.boot.common.tools.analyzer.vo.QueryVo();
				v.setKey(rs.getString("SQL_KEY"));
				v.setNamespace(rs.getString("SQL_NAMESPACE"));
				v.setQueryId(rs.getString("SQL_ID"));
				v.setQueryKind(rs.getString("SQL_KIND"));
				v.setQueryBody(rs.getString("SQL_BODY"));
				result.add(v);
			}
		} finally {
			if (db != null) db.release();
		}
		return result;
	}

	/* ---- 직렬화/역직렬화 유틸 ---- */

	private static String joinList(java.util.List<String> list, String sep) {
		if (list == null || list.isEmpty()) return null;
		StringBuilder sb = new StringBuilder();
		for (String s : list) {
			if (sb.length() > 0) sb.append(sep);
			sb.append(s);
		}
		return sb.toString();
	}

	private static java.util.List<String> splitToList(String str, String sep) {
		java.util.List<String> list = new java.util.ArrayList<>();
		if (StringUtil.isEmpty(str)) return list;
		for (String s : str.split(sep)) {
			String trimmed = s.trim();
			if (!trimmed.isEmpty()) list.add(trimmed);
		}
		return list;
	}

	/** MEMBER_ALIAS_LIST 직렬화: "fullClass-alias,fullClass-alias,..." */
	private static String serializeAlias(java.util.List<java.util.Map<String, String>> aliasList) {
		if (aliasList == null || aliasList.isEmpty()) return null;
		StringBuilder sb = new StringBuilder();
		for (java.util.Map<String, String> m : aliasList) {
			if (sb.length() > 0) sb.append(",");
			sb.append(m.get("FULL_CLASS")).append("-").append(m.get("ALIAS"));
		}
		return sb.toString();
	}

	/** MEMBER_ALIAS_LIST 역직렬화 */
	public static java.util.List<java.util.Map<String, String>> deserializeAlias(String str) {
		java.util.List<java.util.Map<String, String>> list = new java.util.ArrayList<>();
		if (StringUtil.isEmpty(str)) return list;
		for (String entry : str.split(",")) {
			int dashIdx = entry.lastIndexOf("-");
			if (dashIdx > 0) {
				java.util.Map<String, String> m = new java.util.HashMap<>();
				m.put("FULL_CLASS", entry.substring(0, dashIdx));
				m.put("ALIAS", entry.substring(dashIdx + 1));
				list.add(m);
			}
		}
		return list;
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
