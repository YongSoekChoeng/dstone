package net.dstone.common.tools;

import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.utils.SystemUtil;

public class BizGenerator {

	/******************************************* 공통세팅 시작 *******************************************/

	/**************************************************************************************************
	 * WEB-INF/lib/SYSTEMINFO.properties 를 사용자홈/SYSTEMINFO.properties 에 편집/복사 후에 사용한다. 
	 * SYSTEMINFO.properties에는 WEB-INF/lib/framework.jar 가 사용할 프러퍼티들이 정의되어 있다. 
	 * framework.jar는 해당 프러퍼티들을 활용하여 소스를 제너레이팅 한다. 
	 ***************************************************************************************************/

	// 프레임웍에 설정된 DB 아이디(사용자홈/SYSTEMINFO.properties 에 등록된 타겟 DBID)
	public static String DBID = "DBID_1";
	// SqlSessionTemplate 로 등록된 ID
	public static String SQL_CLIENT_ID = "sqlMapClientTemplate1";
	// 프로젝트 루트
	public static String PROJ_ROOT = "D:/WorkShop/AppHome/FRAMEWORK/dstone";
	// 소스가 생성될 루트
	public static String SRC_ROOT = PROJ_ROOT + "/src/main/java";
	// 웹 컨텍스트루트
	public static String WEB_CONTEXT_ROOT = "/";
	// 쿼리파일 위치. Vo 생성시 genVoBySql 일 경우 참조. DB에서 돌아가게 쿼리를 작성한 후 파일로 저장하면 자동으로 읽음.
	public static String SQL_LOCATION = PROJ_ROOT + "/WorkShop/01.유틸리티/01.VO생성용쿼리.txt";

	// CUD(입력/수정/삭제)용 공통VO,공통SQL,공통DAO 이 위치할 패키지명. 전체테이블에 대한 CUD는 공통VO,공통SQL,공통DAO에 구현해놓고 개별 비즈니스는 공통DAO를 상속하여 해당기능을 이용한다.
	public static String COMM_CUD_PACKAGE_NAME = "net.dstone.common.biz.cud";
	// CUD(입력/수정/삭제)용 DAO명(공통DAO명)
	public static String COMM_CUD_DAO_NAME = "BaseCudDao";

	// JSP 파일 루트(웹루트로부터 시작되는 JSP파일루트 예:/view
	public static String JSP_ROOT_PATH = "/sample/view";
	// RestFul WebService 호출시 웹URL
	public static String WS_WEB_URL = "http://localhost:8080";
	// RestFul WebService 호출시 루트 Path. WebService Restful통신을 위해서 jersey라이브러리를 사용한다. web.xml에 등록된 com.sun.jersey.spi.spring.container.servlet.SpringServlet에 맵핑될 url-pattern.
	public static String WS_ROOT_PATH = "/ws";

	// 캐릭터셋
	public static String CHARSET = "UTF-8";

	/******************************************* 공통세팅 끝 *********************************************/

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

	protected static void debug(Object o) {
		System.out.println(o);
	}

	protected static String[] getPrimarykeys(String TABLE_NAME) {
		net.dstone.common.utils.DbUtil db = null;
		net.dstone.common.utils.DataSet ds = null; 
		String[] primarykeys = new String[0];
		StringBuffer keySql = new StringBuffer();

		try {
			db = new net.dstone.common.utils.DbUtil(DBID);

			if ("ORACLE".equals(db.currentDbKind)) {
				db.getConnection();

				keySql.append("SELECT COLS.TABLE_NAME, COLS.COLUMN_NAME, COLS.POSITION, CONS.STATUS, CONS.OWNER ").append("\n");
				keySql.append("FROM ALL_CONSTRAINTS CONS, ALL_CONS_COLUMNS COLS ").append("\n");
				keySql.append("WHERE COLS.TABLE_NAME = '" + TABLE_NAME + "' ").append("\n");
				keySql.append("AND CONS.CONSTRAINT_TYPE = 'P' ").append("\n");
				keySql.append("AND CONS.CONSTRAINT_NAME = COLS.CONSTRAINT_NAME ").append("\n");
				keySql.append("AND CONS.OWNER = COLS.OWNER ").append("\n");
				keySql.append("ORDER BY COLS.TABLE_NAME, COLS.POSITION ").append("\n");

				db.setQuery(keySql.toString());
				ds = new net.dstone.common.utils.DataSet();
				ds.buildFromResultSet(db.select(), "PK_LIST");
				primarykeys = new String[ds.getDataSetRowCount("PK_LIST")];
				if (primarykeys.length > 0) {
					for (int i = 0; i < primarykeys.length; i++) {
						primarykeys[i] = ds.getDataSet("PK_LIST", i).getDatum("COLUMN_NAME");
					}
				}
			} else if ("MSSQL".equals(db.currentDbKind)) {
				db.getConnection();

				keySql.append("SELECT KU.TABLE_NAME AS TABLENAME,COLUMN_NAME AS COLUMN_NAME ").append("\n");
				keySql.append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS AS TC ").append("\n");
				keySql.append("INNER JOIN ").append("\n");
				keySql.append("INFORMATION_SCHEMA.KEY_COLUMN_USAGE AS KU ").append("\n");
				keySql.append("ON TC.CONSTRAINT_TYPE = 'PRIMARY KEY' AND ").append("\n");
				keySql.append("TC.CONSTRAINT_NAME = KU.CONSTRAINT_NAME ").append("\n");
				keySql.append("AND KU.TABLE_NAME='" + TABLE_NAME + "' ").append("\n");
				keySql.append("ORDER BY KU.TABLE_NAME, KU.ORDINAL_POSITION ").append("\n");

				db.setQuery(keySql.toString());
				ds.buildFromResultSet(db.select(), "PK_LIST");
				primarykeys = new String[ds.getDataSetRowCount("PK_LIST")];
				if (primarykeys.length > 0) {
					for (int i = 0; i < primarykeys.length; i++) {
						primarykeys[i] = ds.getDataSet("PK_LIST", i).getDatum("COLUMN_NAME");
					}
				}
			} else if ("MYSQL".equals(db.currentDbKind)) {
				db.getConnection();
				String DB_URL = net.dstone.common.utils.SystemUtil.getInstance().getProperty(DBID + ".strUrl");
				String DB_SID = "MYDB";
				if(DB_URL.indexOf("/") != -1){
					DB_SID =  DB_URL.substring(DB_URL.lastIndexOf("/")+1);
					if( DB_SID.indexOf("?") != -1 ) {
						DB_SID =  DB_SID.substring(0, DB_SID.lastIndexOf("?"));
					}
				}
				keySql.append("SELECT ").append("\n"); 
				keySql.append("	   TABLE_NAME, COLUMN_NAME ").append("\n");
				keySql.append("FROM  ").append("\n");
				keySql.append("	   INFORMATION_SCHEMA.COLUMNS  ").append("\n");
				keySql.append("WHERE 1=1 ").append("\n");
				keySql.append("	   AND COLUMN_KEY = 'PRI'  ").append("\n");
				keySql.append("	   AND TABLE_SCHEMA='" + DB_SID + "'  ").append("\n");
				keySql.append("    AND TABLE_NAME='" + TABLE_NAME + "' ").append("\n");
				keySql.append("ORDER BY ORDINAL_POSITION ").append("\n");

				db.setQuery(keySql.toString());
				ds.buildFromResultSet(db.select(), "PK_LIST");
				primarykeys = new String[ds.getDataSetRowCount("PK_LIST")];
				if (ds.getDataSetRowCount("PK_LIST") > 0) {
					for (int i = 0; i < ds.getDataSetRowCount("PK_LIST"); i++) {
						primarykeys[i] = ds.getDataSet("PK_LIST", i).getDatum("COLUMN_NAME");
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			db.release();
		}
		return primarykeys;
	}

	/**
	 * 테이블명으로 CUD용 VO 생성하는 메소드.
	 * 
	 * @param TABLE_NAME (물리테이블명. 필수.)
	 * @param TABLE_HAN_NAME (논리테이블명. 필수.)
	 * @param fileGenYn (소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genVoForCud("TB_EVT_INFO", "이벤트정보", false);
	 */
	public static void genVoForCud(String TABLE_NAME, String TABLE_HAN_NAME, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new VoGen().genVoByTable(TABLE_NAME, TABLE_HAN_NAME, net.dstone.common.tools.BizGenerator.COMM_CUD_PACKAGE_NAME + ".vo", "", fileGenYn);
	}

	/**
	 * 테이블명으로 VO 생성하는 메소드.
	 * 
	 * @param TABLE_NAME (물리테이블명. 필수.)
	 * @param TABLE_HAN_NAME (논리테이블명. 필수.)
	 * @param strVoPackageName (VO패키지명. (VO패키지명. 옵션(없을경우 CUD(입력/수정/삭제)용 패키지에 생성))
	 * @param strVoName (VO명. 옵션(없을경우 테이블명으로 자동생성))
	 * @param fileGenYn (소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genVoByTable("TB_EVT_INFO", "이벤트정보", "com.test.common.biz.vo", "TbEvtInfoVo", false);
	 */
	public static void genVoByTable(String TABLE_NAME, String TABLE_HAN_NAME, String strVoPackageName, String strVoName, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new VoGen().genVoByTable(TABLE_NAME, TABLE_HAN_NAME, strVoPackageName, strVoName, fileGenYn);
	}

	/**
	 * <개요> SQL로 VO 생성하는 메소드. net.dstone.common.tools.BizGenerator.SQL_LOCATION에 정의된 SQL을 기반으로 VO를 생성한다.
	 * 
	 * @param strVoPackageName (VO패키지명. 필수.)
	 * @param strVoName (VO명. 필수)
	 * @param pageYn (페이징용 카운트 멤버를 생성할지 여부. 페이징이 들어가는 화면에 사용되는 VO라면 true로 세팅.)
	 * @param fileGenYn (소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genVoBySql("com.test.biz.event.vo", "EventListVo", true, false);
	 */
	public static void genVoBySql(String strVoPackageName, String strVoName, boolean pageYn, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new VoGen().genVoBySql(strVoPackageName, strVoName, pageYn, fileGenYn);
	}

	/**
	 * 테이블명으로 CUD용 SQL 생성하는 메소드.(파일을 자동생성하지 않고 소스만 프린트)
	 * 
	 * @param TABLE_NAME (물리테이블명. 필수.)
	 * @param TABLE_HAN_NAME (논리테이블명. 필수.)
	 * @param cudOnlyYn (CUD용인지여부. false일 경우 조회용 쿼리를 생성하지 않음.)
	 * @param fileGenYn (소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genSqlForCud("TB_EVT_INFO", "이벤트정보", true, false);
	 */
	public static void genSqlForCud(String TABLE_NAME, String TABLE_HAN_NAME, boolean cudOnlyYn, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new SqlGen().genSqlByTable(TABLE_NAME, TABLE_HAN_NAME, COMM_CUD_PACKAGE_NAME, COMM_CUD_DAO_NAME, cudOnlyYn, fileGenYn);
	}

	/**
	 * 테이블명으로 SQL 소스코드를 생성하는 메소드.(파일을 자동생성하지 않고 소스만 프린트)
	 * 
	 * @param TABLE_NAME (물리테이블명. 필수.)
	 * @param TABLE_HAN_NAME (논리테이블명. 필수.)
	 * @param strDaoPackageName (DAO 패키지명. 필수.)
	 * @param strDaoName (DAO 명. 필수.)
	 * @param cudOnlyYn (CUD용인지여부. false일 경우 조회용 쿼리를 생성하지 않음.)
	 * @param fileGenYn (소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genSqlByTable("TB_EVT_INFO", "이벤트정보", "com.test.biz.event", "EventDao", false, false);
	 */
	public static void genSqlByTable(String TABLE_NAME, String TABLE_HAN_NAME, String strDaoPackageName, String strDaoName, boolean cudOnlyYn, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new SqlGen().genSqlByTable(TABLE_NAME, TABLE_HAN_NAME, strDaoPackageName, strDaoName, cudOnlyYn, fileGenYn);
	}

	/**
	 * SQL로 SQL 생성하는 메소드. (파일을 자동생성하지 않고 소스만 프린트)
	 * 
	 * @param strDaoPackageName (DAO 패키지명. 필수.)
	 * @param strDaoName (DAO 명. 필수.)
	 * @param strMethodName (DAO 메소드명. 필수.)
	 * @param strMethodComment (DAO 메소드설명. 필수.)
	 * @param strVoPackageName (VO 패키지명. 필수.)
	 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
	 * @param pageYn (카운트 SQL을 생성할지 여부.)
	 * @param fileGenYn (SQL 소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genSqlBySql( "com.test.biz.event", "EventDao", "listEvent", "이벤트리스트조회", "com.test.biz.event.vo", "EventListVo", true, false);
	 */
	public static void genSqlBySql(String strDaoPackageName, String strDaoName, String strMethodName, String strMethodComment, String strVoPackageName, String strVoName, boolean pageYn, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new SqlGen().genSqlBySql(strDaoPackageName, strDaoName, strMethodName, strMethodComment, strVoPackageName, strVoName, pageYn, fileGenYn);
	}

	/**
	 * 테이블명으로 DAO 소스코드를 생성하는 메소드.
	 * 
	 * @param TABLE_NAME (물리테이블명. 필수.)
	 * @param TABLE_HAN_NAME (논리테이블명. 필수.)
	 * @param strDaoPackageName (DAO 패키지명. 필수.)
	 * @param strDaoName (DAO 명. 필수.)
	 * @param cudOnlyYn (CUD용인지여부. false일 경우 조회용 메소드를 생성하지 않음.)
	 * @param fileGenYn (DAO소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genDaoForCud("TB_EVT_INFO", "이벤트정보", true, false);
	 */
	public static void genDaoForCud(String TABLE_NAME, String TABLE_HAN_NAME, boolean cudOnlyYn, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new DaoGen().genDaoByTable(TABLE_NAME, TABLE_HAN_NAME, COMM_CUD_PACKAGE_NAME, COMM_CUD_DAO_NAME, cudOnlyYn, fileGenYn);
	}

	/**
	 * 테이블명으로 DAO 소스코드를 생성하는 메소드.
	 * 
	 * @param TABLE_NAME (물리테이블명. 필수.)
	 * @param TABLE_HAN_NAME (논리테이블명. 필수.)
	 * @param strDaoPackageName (DAO 패키지명. 필수.)
	 * @param strDaoName (DAO 명. 필수.)
	 * @param cudOnlyYn (CUD용인지여부. false일 경우 조회용 메소드를 생성하지 않음.)
	 * @param fileGenYn (DAO소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genDaoByTable("TB_EVT_INFO", "이벤트정보", "com.test.biz.event", "EventDao", false, false);
	 */
	public static void genDaoByTable(String TABLE_NAME, String TABLE_HAN_NAME, String strDaoPackageName, String strDaoName, boolean cudOnlyYn, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new DaoGen().genDaoByTable(TABLE_NAME, TABLE_HAN_NAME, strDaoPackageName, strDaoName, cudOnlyYn, fileGenYn);
	}

	/**
	 * 테이블명으로 DAO 소스코드를 생성하는 메소드.
	 * 
	 * @param strDaoPackageName (DAO 패키지명. 필수.)
	 * @param strDaoName (DAO 명. 필수.)
	 * @param strMethodName (DAO 메소드명. 필수.)
	 * @param strMethodComment (DAO 메소드설명. 필수.)
	 * @param strVoPackageName (VO 패키지명. 필수.)
	 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
	 * @param listYn (리스트성인지 상세조회성인지 여부.)
	 * @param pageYn (카운트 메소드를 생성할지 여부.)
	 * @param fileGenYn (DAO소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genDaoBySql( "com.test.biz.event", "EventDao", "listEvent", "이벤트리스트조회", "com.test.biz.event.vo", "EventListVo", true, false);
	 */
	public static void genDaoBySql(String strDaoPackageName, String strDaoName, String strMethodName, String strMethodComment, String strVoPackageName, String strVoName, boolean listYn, boolean pageYn, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new DaoGen().genDaoBySql(strDaoPackageName, strDaoName, strMethodName, strMethodComment, strVoPackageName, strVoName, listYn, pageYn, fileGenYn);
	}

	/**
	 * JSP용 SVC 소스코드를 생성하는 메소드.
	 * 
	 * @param strSvcPackageName (SVC 패키지명. 필수.)
	 * @param strSvcName (SVC 명. 필수.)
	 * @param strDaoPackageName (DAO 패키지명.)
	 * @param strDaoName (DAO 명.)
	 * @param strMethodName (SVC 메소드명. 필수.)
	 * @param strMethodComment (SVC 메소드설명. 필수.)
	 * @param strVoPackageName (VO 패키지명. 필수.)
	 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
	 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
	 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
	 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
	 * @param fileGenYn (SVC소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genSvcForJsp( "com.test.biz.event", "EventService", "com.test.biz.event", "EventDao", "listEvent", "이벤트리스트조회", "com.test.biz.event.vo", "EventListVo", 0, true, false);
	 */
	public static void genSvc(String strSvcPackageName, String strSvcName, String strDaoPackageName, String strDaoName, String strMethodName, String strMethodComment, String strVoPackageName, String strVoName, int CRUD, String strTableName, boolean pageYn, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new SvcGen().genSvc(strSvcPackageName, strSvcName, strDaoPackageName, strDaoName, strMethodName, strMethodComment, strVoPackageName, strVoName, CRUD, strTableName, pageYn, fileGenYn);
	}

	/**
	 * JSP용 CTRL 소스코드를 생성하는 메소드.
	 * 
	 * @param strCtrlPackageName (CTRL 패키지명. 필수.)
	 * @param strCtrlName (CTRL 명. 필수.)
	 * @param strSvcPackageName (SVC 패키지명. 필수.)
	 * @param strSvcName (SVC 명. 필수.)
	 * @param strMethodName (CTRL 메소드명. 필수.)
	 * @param strMethodComment (CTRL 메소드설명. 필수.)
	 * @param strVoPackageName (VO 패키지명. 필수.)
	 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
	 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
	 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
	 * @param strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value="")에 들어갈 값. 필수.)
	 * @param strUri (메소드 URI. 메소드의 @RequestMapping(value = "")에 들어갈 값. 필수.)
	 * @param strReturnJsp (CTRL 리턴JSP명. ModelAndView.setViewName( strPrefix + "/" + strReturnJsp )로 반환. 필수.)
	 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
	 * @param fileGenYn (CTRL소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genCtrlForJsp( "com.test.biz.event", "EventController", "com.test.biz.event", "EventService", "listEvent", "이벤트리스트조회", "com.test.biz.event.vo", "EventListVo", 0, "TB_EVENT", "/jsp/event", "/listEventPlain.do", "listEventPlain", true, false);
	 */
	public static void genCtrlForJsp(String strCtrlPackageName, String strCtrlName, String strSvcPackageName, String strSvcName, String strMethodName, String strMethodComment, String strVoPackageName, String strVoName, int CRUD, String strTableName, String strPrefix, String strUri, String strReturnJsp, boolean pageYn, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new CtrlGen().genCtrlForJsp(strCtrlPackageName, strCtrlName, strSvcPackageName, strSvcName, strMethodName, strMethodComment, strVoPackageName, strVoName, CRUD, strTableName, strPrefix, strUri, strReturnJsp, pageYn, fileGenYn);
	}

	/**
	 * JSON용 CTRL 소스코드를 생성하는 메소드.
	 * 
	 * @param strCtrlPackageName (CTRL 패키지명. 필수.)
	 * @param strCtrlName (CTRL 명. 필수.)
	 * @param strSvcPackageName (SVC 패키지명. 필수.)
	 * @param strSvcName (SVC 명. 필수.)
	 * @param strMethodName (CTRL 메소드명. 필수.)
	 * @param strMethodComment (CTRL 메소드설명. 필수.)
	 * @param strVoPackageName (VO 패키지명. 필수.)
	 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
	 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
	 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
	 * @param strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value="")에 들어갈 값. 필수.)
	 * @param strUri (메소드 URI. 메소드의 @RequestMapping(value = "")에 들어갈 값. 필수.)
	 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
	 * @param fileGenYn (CTRL소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genCtrlForJson( "com.test.biz.event", "EventController", "com.test.biz.event", "EventService", "listEvent", "이벤트리스트조회", "com.test.biz.event.vo", "EventListVo", 0, "TB_EVENT", "/jsp/event", "/listEventPlain.do", false);
	 */
	public static void genCtrlForJson(String strCtrlPackageName, String strCtrlName, String strSvcPackageName, String strSvcName, String strMethodName, String strMethodComment, String strVoPackageName, String strVoName, int CRUD, String strTableName, String strPrefix, String strUri, boolean pageYn, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new CtrlGen().genCtrlForJson(strCtrlPackageName, strCtrlName, strSvcPackageName, strSvcName, strMethodName, strMethodComment, strVoPackageName, strVoName, CRUD, strTableName, strPrefix, strUri, pageYn, fileGenYn);
	}

	/**
	 * RestFul Web Service 용 WS 소스코드를 생성하는 메소드.
	 * 
	 * @param strWSPackageName (WS 패키지명. 필수.)
	 * @param strWSName (WS 명. 필수.)
	 * @param strSvcPackageName (SVC 패키지명. 필수.)
	 * @param strSvcName (SVC 명. 필수.)
	 * @param strWSPath (WS Path. 필수.)
	 * @param strMethodKind (메소드종류:PUT/POST/DELETE. 필수.)
	 * @param strMethodName (메소드명. 필수.)
	 * @param strMethodComment (메소드설명. 필수.)
	 * @param strWsVoPackageName (웹서비스VO 패키지명-파라메터. 필수.)
	 * @param strWsVoName (웹서비스VO 명-파라메터. 필수.)
	 * @param strVoPackageName (VO 패키지명-파라메터. 필수.)
	 * @param strVoName (VO 명-파라메터. 필수.)
	 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
	 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
	 * @param strMediaType (미디어타입:XML/JSON). 필수.)
	 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
	 * @param fileGenYn (WS소스를 파일로 생성할지 여부.) 예) net.dstone.common.tools.BizGenerator.genWsForRestFul( "com.test.biz.event.ws", "WsEventService", "com.test.biz.event", "EventService", "/Event", "PUT", "listEvent", "이벤트리스트조회", "com.test.biz.event.vo.ws", "EventListWsVo", "com.test.biz.event.vo", "EventListVo", 0, "TB_EVENT", "XML", true, false);
	 */
	public static void genWsForRestFul(String strWSPackageName, String strWSName, String strSvcPackageName, String strSvcName, String strWSPath, String strMethodKind, String strMethodName, String strMethodComment, String strWsVoPackageName, String strWsVoName, String strVoPackageName, String strVoName, int CRUD, String strTableName, String strMediaType, boolean pageYn, boolean fileGenYn) {
		(new net.dstone.common.tools.BizGenerator()).new WsGen().genWsForRestFul(strWSPackageName, strWSName, strSvcPackageName, strSvcName, strWSPath, strMethodKind, strMethodName, strMethodComment, strWsVoPackageName, strWsVoName, strVoPackageName, strVoName, CRUD, strTableName, strMediaType, pageYn, fileGenYn);
	}

	/**
	 * JSP 용 테스트 소스코드를 생성하는 메소드.
	 * 
	 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
	 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
	 * @param strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value="")에 들어갈 값. 필수.)
	 * @param strUri (메소드 URI. 메소드의 @RequestMapping(value = "")에 들어갈 값. 필수.)
	 * @param strVoPackageName (VO 패키지명. 필수.)
	 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
	 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
	 */
	public static void genTestSrcForJsp(int CRUD, String strTableName, String strPrefix, String strUri, String strVoPackageName, String strVoName, boolean pageYn) {
		(new net.dstone.common.tools.BizGenerator()).new TestGen().genTestSrcForJsp(CRUD, strTableName, strPrefix, strUri, strVoPackageName, strVoName, pageYn);
	}

	/**
	 * JSON 용 테스트 소스코드를 생성하는 메소드.
	 * 
	 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
	 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
	 * @param strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value="")에 들어갈 값. 필수.)
	 * @param strUri (메소드 URI. 메소드의 @RequestMapping(value = "")에 들어갈 값. 필수.)
	 * @param strVoPackageName (VO 패키지명. 필수.)
	 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
	 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
	 */
	public static void genTestSrcForJson(int CRUD, String strTableName, String strPrefix, String strUri, String strVoPackageName, String strVoName, boolean pageYn) {
		(new net.dstone.common.tools.BizGenerator()).new TestGen().genTestSrcForJson(CRUD, strTableName, strPrefix, strUri, strVoPackageName, strVoName, pageYn);
	}

	/**
	 * RestFul Web Service 용 테스트 소스코드를 생성하는 메소드.
	 * 
	 * @param strWSPath (WS Path. 필수.)
	 * @param strMethodKind (메소드종류:PUT/POST/DELETE. 필수.)
	 * @param strMethodName (메소드명. 필수.)
	 * @param strVoPackageName (VO 패키지명-파라메터. 필수.)
	 * @param strVoName (VO 명-파라메터. 필수.)
	 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
	 * @param strMediaType (미디어타입:XML/JSON). 필수.) 예) (new net.dstone.common.tools.BizGenerator()).new TestGen().genTestSrcForWsJquery("/Event", "PUT", "listEvent", "com.test.biz.event.vo", "EventListWsVo", 0, "XML");
	 */
	public static void genTestSrcForWsJquery(String strWSPath, String strMethodKind, String strMethodName, String strVoPackageName, String strVoName, int CRUD, String strMediaType) {
		(new net.dstone.common.tools.BizGenerator()).new TestGen().genTestSrcForWsJquery(strWSPath, strMethodKind, strMethodName, strVoPackageName, strVoName, CRUD, strMediaType);
	}

	/**
	 * RestFul Web Service 용 테스트 소스코드를 생성하는 메소드.
	 * 
	 * @param strWSPath (WS Path. 필수.)
	 * @param strMethodKind (메소드종류:PUT/POST/DELETE. 필수.)
	 * @param strMethodName (메소드명. 필수.)
	 * @param strVoPackageName (VO 패키지명-파라메터. 필수.)
	 * @param strVoName (VO 명-파라메터. 필수.)
	 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
	 * @param strMediaType (미디어타입:XML/JSON). 필수.) 예) (new net.dstone.common.tools.BizGenerator()).new TestGen().genTestSrcForWsJava("/Event", "PUT", "listEvent", "com.test.biz.event.vo", "EventListWsVo", 0, "XML");
	 */
	public static void genTestSrcForWsJava(String strWSPath, String strMethodKind, String strMethodName, String strVoPackageName, String strVoName, int CRUD, String strMediaType) {
		(new net.dstone.common.tools.BizGenerator()).new TestGen().genTestSrcForWsJava(strWSPath, strMethodKind, strMethodName, strVoPackageName, strVoName, CRUD, strMediaType);
	}

	public static class DbInfo{
		
		static java.util.Properties tabs = new java.util.Properties();
		
		private TabInfo newTabInfo(){
			return  new TabInfo();
		}

		private ColInfo newColInfo(){
			return  new ColInfo();
		}
		
		public static TabInfo getTab(String TABLE_NAME){
			TabInfo tab = null;
			ColInfo col = null;
			net.dstone.common.utils.DataSet ds = null;
			try {
				if(tabs.containsKey(TABLE_NAME)){
					tab = (TabInfo)tabs.get(TABLE_NAME);
				}else{
					// 1. 컬럼기본정보 조회
					ds = net.dstone.common.tools.BizGenerator.Util.getCols(TABLE_NAME);
					if(ds != null){
						if (ds.getDataSetRowCount("COL_LIST") > 0) {
							tab = new DbInfo().newTabInfo();
							tab.TABLE_NAME = TABLE_NAME;
							tab.TABLE_COMMENT = "";
							for (int i = 0; i < ds.getDataSetRowCount("COL_LIST"); i++) {
								col = new DbInfo().newColInfo();
								col.COLUMN_NAME = ds.getDataSet("COL_LIST", i).getDatum("COLUMN_NAME");
								col.COLUMN_COMMENT = ds.getDataSet("COL_LIST", i).getDatum("COLUMN_COMMENT");
								col.DATA_TYPE = ds.getDataSet("COL_LIST", i).getDatum("DATA_TYPE");
								if(StringUtil.isEmpty(ds.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH"))){
									col.DATA_LENGTH = 0;
								}else{
									col.DATA_LENGTH = Integer.parseInt(ds.getDataSet("COL_LIST", i).getDatum("DATA_LENGTH", "0"));
								}
								col.COLUMN_NAME = ds.getDataSet("COL_LIST", i).getDatum("COLUMN_NAME");
								tab.cols.put(ds.getDataSet("COL_LIST", i).getDatum("COLUMN_NAME"), col);
							}
						}
					}	
					tabs.put(TABLE_NAME, tab);
					// 2. 키값 조회
					ds = net.dstone.common.tools.BizGenerator.Util.getKeys(TABLE_NAME);
					if(ds != null){
						if (ds.getDataSetRowCount("KEY_LIST") > 0) {
							for (int i = 0; i < ds.getDataSetRowCount("KEY_LIST"); i++) {
								col = tab.getCol(ds.getDataSet("KEY_LIST", i).getDatum("COLUMN_NAME"));
								col.IS_KEY = true;
							}
						}
					}
					
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return tab;
		}
		
		public class TabInfo{
			
			public String TABLE_NAME = "";
			public String TABLE_COMMENT = "";
			
			java.util.LinkedHashMap cols = new java.util.LinkedHashMap();
			
			public ColInfo getCol(String COLUMN_NAME){
				ColInfo col = null;
				if(cols.containsKey(COLUMN_NAME)){
					col = (ColInfo)cols.get(COLUMN_NAME);
				}
				return col;
			}
			
			public ColInfo[] getCols(){
				ColInfo[] colArr = null;
				ColInfo col = null;
				java.util.Iterator iter = cols.keySet().iterator();
				java.util.Vector list = new java.util.Vector();
				while(iter.hasNext()){
					list.add(cols.get(iter.next()));
				}
				colArr = new ColInfo[list.size()];
				list.copyInto(colArr);
				list.clear();
				return colArr;
			}

			public ColInfo[] getKey(){
				ColInfo[] keys = null;
				ColInfo col = null;
				java.util.Iterator iter = cols.keySet().iterator();
				java.util.Vector list = new java.util.Vector();
				
				while(iter.hasNext()){
					col = (ColInfo)cols.get(iter.next());
					if(col.IS_KEY){
						list.add(col);
					}
				}
				keys = new ColInfo[list.size()];
				list.copyInto(keys);
				list.clear();
				return keys;
			}

			@Override
			public String toString() {
				return "TabInfo [TABLE_NAME=" + TABLE_NAME + ", TABLE_COMMENT=" + TABLE_COMMENT + ", cols=" + cols + "]";
			}
			
		}
		
		public class ColInfo{
			public boolean IS_KEY = false;
			public String COLUMN_NAME = "";
			public String COLUMN_COMMENT = "";
			/**
			 * DATA_TYPE 은 다음과 같은 값을 갖는다.
			 * <숫자형> float/int/double
			 * <문자형> String
			 * <날짜형> java.sql.Date/java.sql.Time/java.sql.Timestamp
			 */
			public String DATA_TYPE = "";
			public int 	DATA_LENGTH = 0;
			@Override
			public String toString() {
				return "ColInfo [IS_KEY=" + IS_KEY + ", COLUMN_NAME=" + COLUMN_NAME + ", COLUMN_COMMENT=" + COLUMN_COMMENT + ", DATA_TYPE=" + DATA_TYPE + ", DATA_LENGTH=" + DATA_LENGTH + "]";
			}
		}
	}
	
	protected class VoGen {
		/**
		 * 테이블명으로 VO 생성하는 메소드.(파일자동생성)
		 * 
		 * @param TABLE_NAME (물리테이블명. 필수.)
		 * @param TABLE_HAN_NAME (논리테이블명. 필수.)
		 * @param strVoPackageName (VO패키지명. 옵션(없을경우 CUD(입력/수정/삭제)용 패키지에 생성))
		 * @param strVoName (VO명. 옵션(없을경우 테이블명으로 자동생성))
		 * @param fileGenYn (소스를 파일로 생성할지 여부.) 예) (new net.dstone.common.tools.BizGenerator()).new VoGen().genVoByTable("TB_EVT_INFO", "이벤트정보", "com.test.biz.event.vo", "EventListVo", true);
		 */
		protected void genVoByTable(String TABLE_NAME, String TABLE_HAN_NAME, String strVoPackageName, String strVoName, boolean fileGenYn) {

			debug("||===================== genVoByTable(테이블명으로 VO 생성하는 메소드) =====================||");
			debug("TABLE_NAME (물리테이블명. 필수.) [" + TABLE_NAME + "]");
			debug("TABLE_HAN_NAME (논리테이블명. 필수.) [" + TABLE_HAN_NAME + "]");
			debug("strVoPackageName (VO패키지명. 옵션(없을경우 CUD(입력/수정/삭제)용 패키지에 생성)) [" + strVoPackageName + "]");
			debug("strVoName (VO명. 옵션(없을경우 테이블명으로 자동생성)) [" + strVoName + "]");
			debug("fileGenYn (소스를 파일로 생성할지 여부.) [" + fileGenYn + "]");
			debug("||====================================================================================||");

			String voStr = "";

			StringBuffer vo = new StringBuffer();

			if (TABLE_NAME == null || "".equals(TABLE_NAME)) {
				System.out.println("테이블명 입력필수.");
			} else {
				if (strVoName == null || "".equals(strVoName)) {
					if (TABLE_NAME != null && !"".equals(TABLE_NAME)) {
						strVoName = net.dstone.common.utils.StringUtil.getHungarianName(TABLE_NAME, " ").trim() + "CudVo";
					}
				}
				if (strVoPackageName == null || "".equals(strVoPackageName)) {
					strVoPackageName = net.dstone.common.tools.BizGenerator.COMM_CUD_PACKAGE_NAME + ".vo";
				}
				if (!strVoName.endsWith("CudVo")) {
					strVoName = strVoName + "CudVo";
				}

				DbInfo.ColInfo[] cols = null;
				DbInfo.ColInfo col = null;

				try {
					cols = DbInfo.getTab(TABLE_NAME).getCols();

					vo.append("\n");
					vo.append("package " + strVoPackageName + ";  ").append("\n");
					vo.append("                      ").append("\n");
					vo.append("import javax.xml.bind.annotation.XmlRootElement;").append("\n");
					vo.append("").append("\n");
					vo.append("@XmlRootElement( name=\"" + strVoName + "\" ) ").append("\n");
					vo.append("public class " + strVoName + " extends net.dstone.common.biz.BaseVo implements java.io.Serializable { ").append("\n");
					if (cols != null) {
						for (int i = 0; i < cols.length; i++) {
							col = cols[i];
							vo.append("\tprivate String " + col.COLUMN_NAME + "; ").append("\n");
						}
						String javaType = "String";
						String capColumnName = "";
						String comments = "";
						for (int i = 0; i < cols.length; i++) {
							col = cols[i];
							int lenght = col.COLUMN_NAME.length();
							StringBuffer tempColName = new StringBuffer();
							for (int k = 0; k < lenght; k++) {
								if (k == 0)
									tempColName.append(String.valueOf(col.COLUMN_NAME.charAt(k)).toUpperCase());
								else
									tempColName.append(col.COLUMN_NAME.charAt(k));
							}
							capColumnName = tempColName.toString();

							// 1. getter 처리
							vo.append("\t/** " + "\n");
							vo.append("\t * " + comments + "\n");
							vo.append("\t * @return Returns the " + col.COLUMN_NAME + "\n");
							vo.append("\t */ " + "\n");

							vo.append("\tpublic " + javaType + " get" + capColumnName + "() { " + "\n");
							vo.append("\t\treturn this." + col.COLUMN_NAME + ";" + "\n");
							vo.append("\t}" + "\n");

							// 2. setter 처리
							vo.append("\t/** " + "\n");
							vo.append("\t * " + comments + "\n");
							vo.append("\t * @param " + col.COLUMN_NAME + " the " + col.COLUMN_NAME + " to set" + "\n");
							vo.append("\t */ " + "\n");

							vo.append("\tpublic void set" + capColumnName + "(" + javaType + " " + col.COLUMN_NAME + ") { " + "\n");
							vo.append("\t\tthis." + col.COLUMN_NAME + " = " + col.COLUMN_NAME + ";" + "\n");
							vo.append("\t}" + "\n");
						}

					}
					vo.append("}                     ").append("\n");
					// System.out.println("============================ VO START ============================");
					// System.out.println(vo);
					// System.out.println("============================ VO END ============================");

					voStr = vo.toString();
					if (fileGenYn) {
						net.dstone.common.utils.FileUtil.writeFile(SRC_ROOT + "/" + StringUtil.replace(strVoPackageName, ".", "/"), strVoName + ".java", voStr, CHARSET);
					}
					System.out.println("============================ VO START ============================");
					System.out.println(voStr);
					System.out.println("============================ VO END ============================");
					System.out.println("");

				} catch (Exception e) {
					e.printStackTrace();
				} 
			}

		}

		/**
		 * <개요> SQL로 VO 생성하는 메소드. net.dstone.common.tools.BizGenerator.SQL_LOCATION에 정의된 SQL을 기반으로 VO를 생성한다. SQL로 VO 생성하는 메소드.(파일자동생성)
		 * 
		 * @param strVoPackageName (VO패키지명. 필수.)
		 * @param strVoName (VO명. 필수.)
		 * @param pageYn (페이징용 카운트 멤버를 생성할지 여부. 페이징이 들어가는 화면에 사용되는 VO라면 true로 세팅.)
		 * @param fileGenYn (소스를 파일로 생성할지 여부.) 예) (new net.dstone.common.tools.BizGenerator()).new VoGen().genVoBySql("com.test.biz.event.vo", "EventListVo", true);
		 */
		protected void genVoBySql(String strVoPackageName, String strVoName, boolean pageYn, boolean fileGenYn) {

			debug("||========================= genVoBySql(SQL로 VO 생성하는 메소드) =========================||");
			debug("strVoPackageName (VO패키지명. 필수.) [" + strVoPackageName + "]");
			debug("strVoName (VO명. 필수.) [" + strVoName + "]");
			debug("pageYn (페이징용 카운트 멤버를 생성할지 여부. 페이징이 들어가는 화면에 사용되는 VO라면 true로 세팅.) [" + pageYn + "]");
			debug("fileGenYn (소스를 파일로 생성할지 여부.) [" + fileGenYn + "]");
			debug("||====================================================================================||");

			String voStr = "";

			String sql = net.dstone.common.utils.FileUtil.readFile(SQL_LOCATION, CHARSET);
			StringBuffer vo = new StringBuffer();

			if (strVoName == null || "".equals(strVoName)) {
				System.out.println("VO 명 입력필수.");
			} else {

				if (!strVoName.endsWith("Vo")) {
					strVoName = strVoName + "Vo";
				}

				net.dstone.common.utils.DbUtil db = null;
				net.dstone.common.utils.DataSet ds = null;
				String[] cols = null;
				String col = "";

				try {
					db = new net.dstone.common.utils.DbUtil(DBID);
					db.getConnection();

					db.setQuery(sql);
					ds = new net.dstone.common.utils.DataSet();
					ds.buildFromResultSet(db.select(), "SELECT_RESULT");
					cols = db.columnNames;

					vo.append("\n");
					vo.append("package " + strVoPackageName + ";  ").append("\n");
					vo.append("                      ").append("\n");
					vo.append("import javax.xml.bind.annotation.XmlRootElement;").append("\n");
					vo.append("").append("\n");
					vo.append("@XmlRootElement( name=\"" + strVoName + "\" ) ").append("\n");
					vo.append("public class " + strVoName + " extends net.dstone.common.biz.BaseVo implements java.io.Serializable { ").append("\n");
					if (cols != null) {
						for (int i = 0; i < cols.length; i++) {
							col = cols[i];
							vo.append("\tprivate String " + col + "; ").append("\n");
						}
						String javaType = "String";
						String capColumnName = "";
						String comments = "";
						for (int i = 0; i < cols.length; i++) {
							col = cols[i];
							int lenght = col.length();
							StringBuffer sb = new StringBuffer();
							for (int k = 0; k < lenght; k++) {
								if (k == 0)
									sb.append(String.valueOf(col.charAt(k)).toUpperCase());
								else
									sb.append(col.charAt(k));
							}
							capColumnName = sb.toString();

							// 1. getter 처리
							vo.append("\t/** " + "\n");
							vo.append("\t * " + comments + "\n");
							vo.append("\t * @return Returns the " + col + "\n");
							vo.append("\t */ " + "\n");

							vo.append("\tpublic " + javaType + " get" + capColumnName + "() { " + "\n");
							vo.append("\t\treturn this." + col + ";" + "\n");
							vo.append("\t}" + "\n");

							// 2. setter 처리
							vo.append("\t/** " + "\n");
							vo.append("\t * " + comments + "\n");
							vo.append("\t * @param " + col + " the " + col + " to set" + "\n");
							vo.append("\t */ " + "\n");

							vo.append("\tpublic void set" + capColumnName + "(" + javaType + " " + col + ") { " + "\n");
							vo.append("\t\tthis." + col + " = " + col + ";" + "\n");
							vo.append("\t}" + "\n");
						}

						if (pageYn) {
							vo.append("\t/*** 페이징용 멤버 시작 ***/").append("\n");
							vo.append("\tint PAGE_SIZE;").append("\n");
							vo.append("\tint PAGE_NUM;").append("\n");
							vo.append("\tint INT_FROM;").append("\n");
							vo.append("\tint INT_TO;").append("\n");
							vo.append("\tpublic int getPAGE_SIZE() { " + "\n");
							vo.append("\t	return PAGE_SIZE; " + "\n");
							vo.append("\t} " + "\n");
							vo.append("\tpublic void setPAGE_SIZE(int pAGE_SIZE) { " + "\n");
							vo.append("\t	PAGE_SIZE = pAGE_SIZE; " + "\n");
							vo.append("\t} " + "\n");
							vo.append("\tpublic int getPAGE_NUM() { " + "\n");
							vo.append("\t	return PAGE_NUM; " + "\n");
							vo.append("\t} " + "\n");
							vo.append("\tpublic void setPAGE_NUM(int pAGE_NUM) { " + "\n");
							vo.append("\t	PAGE_NUM = pAGE_NUM; " + "\n");
							vo.append("\t} " + "\n");
							vo.append("\tpublic int getINT_FROM() { " + "\n");
							vo.append("\t	return INT_FROM; " + "\n");
							vo.append("\t} " + "\n");
							vo.append("\tpublic void setINT_FROM(int iNT_FROM) { " + "\n");
							vo.append("\t	INT_FROM = iNT_FROM; " + "\n");
							vo.append("\t} " + "\n");
							vo.append("\tpublic int getINT_TO() { " + "\n");
							vo.append("\t	return INT_TO; " + "\n");
							vo.append("\t} " + "\n");
							vo.append("\tpublic void setINT_TO(int iNT_TO) { " + "\n");
							vo.append("\t	INT_TO = iNT_TO; " + "\n");
							vo.append("\t} " + "\n");
							vo.append("\t/*** 페이징용 멤버 끝 ***/").append("\n");
						}
					}
					vo.append("}                     ").append("\n");
					// System.out.println("============================ VO START ============================");
					// System.out.println(vo);
					// System.out.println("============================ VO END ============================");

					voStr = vo.toString();
					if (fileGenYn) {
						net.dstone.common.utils.FileUtil.writeFile(SRC_ROOT + "/" + StringUtil.replace(strVoPackageName, ".", "/"), strVoName + ".java", voStr, CHARSET);
					}

					System.out.println("============================ VO START ============================");
					System.out.println(voStr);
					System.out.println("============================ VO END ============================");
					System.out.println("");

				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					db.release();
				}

			}
		}
	}

	protected class SqlGen {

		/**
		 * 테이블명으로 SQL 생성하는 메소드.
		 * 
		 * @param TABLE_NAME (물리테이블명. 필수.)
		 * @param TABLE_HAN_NAME (논리테이블명. 필수.)
		 * @param strDaoPackageName (DAO 패키지명. 필수.)
		 * @param strDaoName (DAO 명. 필수.)
		 * @param cudOnlyYn (CUD용인지여부. false일 경우 조회용 쿼리를 생성하지 않음.)
		 * @param fileGenYn (SQL 소스를 파일로 생성할지 여부.) 예) (new net.dstone.common.tools.BizGenerator()).new SqlGen().genSqlByTable("TB_EVT_INFO", "이벤트정보", "com.test.biz.event", "EventDao", false, false);
		 */
		protected void genSqlByTable(String TABLE_NAME, String TABLE_HAN_NAME, String strDaoPackageName, String strDaoName, boolean cudOnlyYn, boolean fileGenYn) {

			debug("||==================== genSqlByTable(테이블명으로 SQL 생성하는 메소드) ====================||");
			debug("TABLE_NAME (물리테이블명. 필수.) [" + TABLE_NAME + "]");
			debug("TABLE_HAN_NAME (논리테이블명. 필수.) [" + TABLE_HAN_NAME + "]");
			debug("strDaoPackageName (DAO 패키지명. 필수.) [" + strDaoPackageName + "]");
			debug("strDaoName (DAO 명. 필수.) [" + strDaoName + "]");
			debug("cudOnlyYn (CUD용인지여부. false일 경우 조회용 쿼리를 생성하지 않음.) [" + cudOnlyYn + "]");
			debug("fileGenYn (SQL 소스를 파일로 생성할지 여부.) [" + fileGenYn + "]");
			debug("||====================================================================================||");

			String PACKAGE_NAME = strDaoPackageName;

			StringBuffer xmlH = new StringBuffer();
			StringBuffer xmlVo = new StringBuffer();
			StringBuffer xmlMethd1 = new StringBuffer();
			StringBuffer xmlMethd2 = new StringBuffer();
			StringBuffer xmlMethd3 = new StringBuffer();
			StringBuffer xmlMethd4 = new StringBuffer();
			StringBuffer xmlMethd5 = new StringBuffer();
			StringBuffer xmlMethd6 = new StringBuffer();
			StringBuffer xmlF = new StringBuffer();
			String xmlFileConts = "";
			StringBuffer xmlConts = new StringBuffer();
			
			DbInfo.ColInfo[] cols = null;
			DbInfo.ColInfo col = null;
			
			DbInfo.ColInfo[] keys = null;
			DbInfo.ColInfo key = null;
			DbInfo.ColInfo mainKey = null;
			DbInfo.ColInfo[] parentKeys = null;
			
			String voName = "";
			String sVoName = "";
			String tableName = "";
			String nameSpace = "";
			String fileName = "";
			String fullFileName = "";
			boolean fileExists = false;

			try {
				cols = DbInfo.getTab(TABLE_NAME).getCols();
				keys = DbInfo.getTab(TABLE_NAME).getKey();
				if (keys.length > 1) {
					parentKeys = new DbInfo.ColInfo[keys.length - 1];
					for (int i = 0; i < keys.length; i++) {
						if (i == keys.length - 1) {
							mainKey = keys[i];
						} else {
							parentKeys[i] = keys[i];
						}
					}
				} else if (keys.length == 1) {
					mainKey = keys[0];
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			tableName = net.dstone.common.utils.StringUtil.getHungarianName(TABLE_NAME, " ").trim();
			voName = net.dstone.common.utils.StringUtil.getHungarianName(TABLE_NAME, " ").trim() + "CudVo";
			sVoName = net.dstone.common.utils.StringUtil.getHungarianName(TABLE_NAME, "").trim() + "CudVo";
			fileName = tableName + "CudDao.xml";
			nameSpace = StringUtil.replace((PACKAGE_NAME + "." + strDaoName).toUpperCase(), ".", "_");

			fullFileName = SRC_ROOT + "/" + StringUtil.replace(strDaoPackageName, ".", "/") + "/sql/" + fileName;
			fileExists = FileUtil.isFileExist(fullFileName);

			System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< " + fileName + " >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

			xmlH.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>                                            ").append("\n");
			xmlH.append("                                                                                      ").append("\n");
			xmlH.append("<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">").append("\n");
			xmlH.append("                                                                                      ").append("\n");
			xmlH.append("<mapper namespace=\"" + nameSpace + "\">                                                       ").append("\n");
			xmlH.append("                                                                                      ").append("\n");

			xmlVo.append("    <!-- " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] VO -->                                                                ").append("\n");
			xmlVo.append("                                                                                   ").append("\n");
			
			/* 1. 리스트 조회 */
			if (!cudOnlyYn) {
				
				xmlMethd1.append("    <!--  " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 리스트 조회 -->                                                      ").append("\n");
				xmlMethd1.append("    <select id=\"list" + tableName + "\" parameterType=\"" + PACKAGE_NAME + ".vo." + voName + "\" resultType=\"" + PACKAGE_NAME + ".vo." + voName + "\">         ").append("\n");
				xmlMethd1.append("        SELECT                                                                        ").append("\n");
				if (cols != null) {
					for (int i = 0; i < cols.length; i++) {
						if (i == 0) {
							xmlMethd1.append("            ");
						} else if (i % 5 == 0) {
							xmlMethd1.append("\n").append("            ");
						}
						col = cols[i];
						xmlMethd1.append(col.COLUMN_NAME);
						if (i < (cols.length - 1)) {
							xmlMethd1.append(", ");
						}
					}
					xmlMethd1.append("\n");
				}
				
				xmlMethd1.append("        FROM                                                                          ").append("\n");
				xmlMethd1.append("            " + TABLE_NAME + "                                                                       ").append("\n");
				xmlMethd1.append("        WHERE 2 > 1                                                                   ").append("\n");
				xmlMethd1.append("    </select>                                                                       ").append("\n");
				xmlMethd1.append("                                                                                   ").append("\n");

				/* 2. 상세 조회 */
				xmlMethd2.append("    <!--  " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 상세 조회 -->                                                        ").append("\n");
				xmlMethd2.append("    <select id=\"select" + tableName + "\" parameterType=\"" + PACKAGE_NAME + ".vo." + voName + "\" resultType=\"" + PACKAGE_NAME + ".vo." + voName + "\">       ").append("\n");
				xmlMethd2.append("        SELECT                                                                        ").append("\n");
				if (cols != null) {
					for (int i = 0; i < cols.length; i++) {
						if (i == 0) {
							xmlMethd2.append("            ");
						} else if (i % 5 == 0) {
							xmlMethd2.append("\n").append("            ");
						}
						col = cols[i];
						xmlMethd2.append(col.COLUMN_NAME);
						if (i < (cols.length - 1)) {
							xmlMethd2.append(", ");
						}
					}
					xmlMethd2.append("\n");
				}
				xmlMethd2.append("        FROM                                                                          ").append("\n");
				xmlMethd2.append("            " + TABLE_NAME + "                                                                       ").append("\n");
				xmlMethd2.append("        WHERE 1=1                                                                     ").append("\n");
				if (keys != null) {
					for (int i = 0; i < keys.length; i++) {
						key = keys[i];
						xmlMethd2.append("            AND " + key.COLUMN_NAME + " = #{" + key.COLUMN_NAME + "}").append("\n");
					}
				}
				xmlMethd2.append("    </select>                                                                       ").append("\n");
				xmlMethd2.append("                                                                                   ").append("\n");
			}

			/* 3. NEW키값 조회 */
			xmlMethd3.append("    <!--  " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] NEW키값 조회 -->                                                        ").append("\n");
			xmlMethd3.append("    <select id=\"select" + tableName + "NewKey\" parameterType=\"" + PACKAGE_NAME + ".vo." + voName + "\" resultType=\"" + PACKAGE_NAME + ".vo." + voName + "\">       ").append("\n");
			xmlMethd3.append("        SELECT                                                                        ").append("\n");
			xmlMethd3.append( getNewKeySql(mainKey, SystemUtil.getInstance().getProperty(DBID + ".DbKind")) );
			xmlMethd3.append("        FROM                                                                          ").append("\n");
			xmlMethd3.append("            " + TABLE_NAME + "                                                                       ").append("\n");
			xmlMethd3.append("        WHERE 1=1                                                                     ").append("\n");
			xmlMethd3.append( getNewKeyWhereSql(parentKeys, mainKey, SystemUtil.getInstance().getProperty(DBID + ".DbKind")) );
			xmlMethd3.append("    </select>                                                                       ").append("\n");
			xmlMethd3.append("                                                                                   ").append("\n");

			/* 4. 입력 */
			xmlMethd4.append("    <!--  " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 입력 -->                                                             ").append("\n");
			xmlMethd4.append("    <insert id=\"insert" + tableName + "\" parameterType=\"" + PACKAGE_NAME + ".vo." + voName + "\" >                             ").append("\n");
			xmlMethd4.append("        INSERT INTO " + TABLE_NAME + " (                                                            ").append("\n");
			xmlMethd4.append( getInsertColSql(cols, SystemUtil.getInstance().getProperty(DBID + ".DbKind"), "COL") ).append("\n");
			xmlMethd4.append("        ) VALUES (                                                                    ").append("\n");
			xmlMethd4.append( getInsertColSql(cols, SystemUtil.getInstance().getProperty(DBID + ".DbKind"), "VAL") ).append("\n");
			xmlMethd4.append("        )                                                                             ").append("\n");
			xmlMethd4.append("    </insert>                                                                       ").append("\n");
			xmlMethd4.append("                                                                                   ").append("\n");

			/* 5. 수정 */
			xmlMethd5.append("    <!--  " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 수정 -->                                                             ").append("\n");
			xmlMethd5.append("    <update id=\"update" + tableName + "\" parameterType=\"" + PACKAGE_NAME + ".vo." + voName + "\" >                             ").append("\n");
			xmlMethd5.append("        UPDATE " + TABLE_NAME + "                                                                   ").append("\n");
			xmlMethd5.append("        SET                                                                           ").append("\n");
			xmlMethd5.append( getUpdateColSql(cols, SystemUtil.getInstance().getProperty(DBID + ".DbKind")) 		).append("\n");
			xmlMethd5.append("        WHERE 1=1                                                                        ").append("\n");
			xmlMethd5.append( getUpdateColWhereSql(keys, SystemUtil.getInstance().getProperty(DBID + ".DbKind")) );
			xmlMethd5.append("    </update>                                                                       ").append("\n");
			xmlMethd5.append("                                                                                   ").append("\n");

			/* 6. 삭제 */
			xmlMethd6.append("    <!--  " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 삭제 -->                                                             ").append("\n");
			xmlMethd6.append("    <delete id=\"delete" + tableName + "\" parameterType=\"" + PACKAGE_NAME + ".vo." + voName + "\" >                             ").append("\n");
			xmlMethd6.append("        DELETE FROM " + TABLE_NAME + "                                                              ").append("\n");
			xmlMethd6.append("        WHERE 1=1                                                                         ").append("\n");
			xmlMethd6.append( getDeleteColWhereSql(keys, SystemUtil.getInstance().getProperty(DBID + ".DbKind")) );
			xmlMethd6.append("    </delete>                                                                       ").append("\n");
			xmlMethd6.append("                                                                                   ").append("\n");

			xmlF.append("</mapper>                                                                             ").append("\n");
			xmlF.append("").append("\n");

			if (fileExists) {
				xmlFileConts = net.dstone.common.utils.FileUtil.readFile(fullFileName, CHARSET);
				xmlConts = new StringBuffer();
				xmlConts.append(xmlVo);
				xmlConts.append(xmlMethd1);
				xmlConts.append(xmlMethd2);
				xmlConts.append(xmlMethd3);
				xmlConts.append(xmlMethd4);
				xmlConts.append(xmlMethd5);
				xmlConts.append(xmlMethd6);

				xmlFileConts = StringUtil.replace(xmlFileConts, "</mapper>", xmlConts.toString() + "</mapper>");

			} else {
				xmlConts = new StringBuffer();
				xmlConts.append(xmlH);
				xmlConts.append(xmlVo);
				xmlConts.append(xmlMethd1);
				xmlConts.append(xmlMethd2);
				xmlConts.append(xmlMethd3);
				xmlConts.append(xmlMethd4);
				xmlConts.append(xmlMethd5);
				xmlConts.append(xmlMethd6);
				xmlConts.append(xmlF);

				xmlFileConts = xmlConts.toString();
			}

			if (fileGenYn) {
				net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(fullFileName), FileUtil.getFileName(fullFileName), xmlFileConts, CHARSET);
			}

			System.out.println("============================ XML START ============================");
			System.out.println(xmlFileConts);
			System.out.println("============================ XML END ============================");
			System.out.println("");

		}

		/**
		 * SQL로 SQL 생성하는 메소드.
		 * 
		 * @param strDaoPackageName (DAO 패키지명. 필수.)
		 * @param strDaoName (DAO 명. 필수.)
		 * @param strMethodName (DAO 메소드명. 필수.)
		 * @param strMethodComment (DAO 메소드설명. 필수.)
		 * @param strVoPackageName (VO 패키지명. 필수.)
		 * @param strVoName (VO명. 필수.)
		 * @param pageYn (카운트 SQL을 생성할지 여부.)
		 * @param fileGenYn (SQL 소스를 파일로 생성할지 여부.) 예) (new net.dstone.common.tools.BizGenerator()).new sqlGen().genSqlBySql("com.test.biz.event", "EventDao", "listEvent", "이벤트리스트", "com.test.biz.event.vo", "EventListVo", true, false);
		 */
		protected void genSqlBySql(String strDaoPackageName, String strDaoName, String strMethodName, String strMethodComment, String strVoPackageName, String strVoName, boolean pageYn, boolean fileGenYn) {

			debug("||======================== genSqlBySql(SQL로 SQL 생성하는 메소드) ========================||");
			debug("strDaoPackageName (DAO 패키지명. 필수.) [" + strDaoPackageName + "]");
			debug("strDaoName (DAO 명. 필수.) [" + strDaoName + "]");
			debug("strMethodName (DAO 메소드명. 필수.) [" + strMethodName + "]");
			debug("strMethodComment (DAO 메소드설명. 필수.) [" + strMethodComment + "]");
			debug("strVoPackageName (VO 패키지명. 필수.) [" + strVoPackageName + "]");
			debug("strVoName (VO명. 필수.) [" + strVoName + "]");
			debug("pageYn (카운트 SQL을 생성할지 여부.) [" + pageYn + "]");
			debug("fileGenYn (SQL 소스를 파일로 생성할지 여부.) [" + fileGenYn + "]");
			debug("||====================================================================================||");

			String sql = "";
			String PACKAGE_NAME = strDaoPackageName;

			sql = net.dstone.common.utils.FileUtil.readFile(SQL_LOCATION, CHARSET);

			StringBuffer xmlH = new StringBuffer();
			StringBuffer xmlVo = new StringBuffer();
			String voIdStr = "";
			StringBuffer xmlMethd1 = new StringBuffer();
			StringBuffer xmlF = new StringBuffer();
			String xmlFileConts = "";
			StringBuffer xmlConts = new StringBuffer();
			
			StringBuffer pagingUpConts = new StringBuffer(Util.getPagingQuery(SystemUtil.getInstance().getProperty(DBID + ".DbKind"), 0));
			StringBuffer pagingLowConts = new StringBuffer(Util.getPagingQuery(SystemUtil.getInstance().getProperty(DBID + ".DbKind"), 1));

			String voName = "";
			String sVoName = "";
			String nameSpace = "";
			String fileName = "";
			String fullFileName = "";
			boolean fileExists = false;

			voName = strVoName;
			sVoName = strVoName.substring(0, 1).toLowerCase() + strVoName.substring(1);

			nameSpace = StringUtil.replace((PACKAGE_NAME + "." + strDaoName).toUpperCase(), ".", "_");
			fileName = strDaoName + ".xml";

			fullFileName = SRC_ROOT + "/" + StringUtil.replace(strDaoPackageName, ".", "/") + "/sql/" + fileName;
			fileExists = FileUtil.isFileExist(fullFileName);

			System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< " + fileName + " >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

			xmlH.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>                                            ").append("\n");
			xmlH.append("                                                                                      ").append("\n");
			xmlH.append("<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">").append("\n");
			xmlH.append("                                                                                      ").append("\n");
			xmlH.append("<mapper namespace=\"" + nameSpace + "\">                                                       ").append("\n");
			xmlH.append("                                                                                      ").append("\n");

			xmlVo.append("    <!-- " + strVoName + " VO -->                                                                ").append("\n");
			xmlVo.append("                                                                                   ").append("\n");
			voIdStr = "";
			if (pageYn) {
				xmlMethd1.append("    <!--  " + strMethodComment + "(카운트) -->                                                      ").append("\n");
				xmlMethd1.append("    <select id=\"" + strMethodName + "Count\" parameterType=\"" + PACKAGE_NAME + ".vo." + voName + "\" resultType=\"Integer\">         ").append("\n");
				xmlMethd1.append("        SELECT COUNT(*) CNT FROM ( ").append("\n");
				xmlMethd1.append(sql);
				xmlMethd1.append("        ) P").append("\n");
				xmlMethd1.append("    </select>                                                                       ").append("\n");
				xmlMethd1.append("                                                                                   ").append("\n");
			}

			xmlMethd1.append("    <!--  " + strMethodComment + " -->                                                      ").append("\n");
			xmlMethd1.append("    <select id=\"" + strMethodName + "\" parameterType=\"" + PACKAGE_NAME + ".vo." + voName + "\" resultType=\"" + PACKAGE_NAME + ".vo." + voName + "\">         ").append("\n");
			if (pageYn) {
				xmlMethd1.append(pagingUpConts).append("\n");
			}
			xmlMethd1.append(sql).append("\n");
			if (pageYn) {
				xmlMethd1.append(pagingLowConts).append("\n");
			}
			xmlMethd1.append("    </select>                                                                       ").append("\n");
			xmlMethd1.append(" ").append("\n");
			xmlF.append("</mapper>                                                                             ").append("\n");
			xmlF.append("").append("\n");

			if (fileExists) {

				xmlFileConts = net.dstone.common.utils.FileUtil.readFile(fullFileName, CHARSET);
				xmlConts = new StringBuffer();
				if (xmlFileConts.indexOf(voIdStr) == -1) {
					xmlConts.append(xmlVo);
				}
				xmlConts.append(xmlMethd1);

				xmlFileConts = StringUtil.replace(xmlFileConts, "</mapper>", xmlConts.toString() + "</mapper>");

			} else {
				xmlConts = new StringBuffer();
				xmlConts.append(xmlH);
				xmlConts.append(xmlVo);
				xmlConts.append(xmlMethd1);
				xmlConts.append(xmlF);

				xmlFileConts = xmlConts.toString();
			}
			if (fileGenYn) {
				net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(fullFileName), FileUtil.getFileName(fullFileName), xmlFileConts, CHARSET);
			}

			System.out.println("============================ XML START ============================");
			System.out.println(xmlFileConts);
			System.out.println("============================ XML END ============================");
			System.out.println("");

		}
		
		private String getNewKeySql(DbInfo.ColInfo mainKey, String dbKind){
			StringBuffer sql = new StringBuffer();
			
			if(mainKey != null){
				if ("ORACLE".equals(dbKind)) {
					if (mainKey.DATA_TYPE.equals("float") || mainKey.DATA_TYPE.equals("int") ||  mainKey.DATA_TYPE.equals("double")) {
						sql.append("            (NVL(MAX(" + mainKey.COLUMN_NAME + "),0)+1) " + mainKey.COLUMN_NAME + " ").append("\n");
					}else if (mainKey.DATA_TYPE.equals("java.sql.Date") || mainKey.DATA_TYPE.equals("java.sql.Time") ||  mainKey.DATA_TYPE.equals("java.sql.Timestamp")) {
						if ( mainKey.DATA_TYPE.equals("java.sql.Date") ) {
							sql.append("            SYSDATE " + mainKey.COLUMN_NAME + " ").append("\n");
						}else if ( mainKey.DATA_TYPE.equals("java.sql.Time") ) {
							sql.append("            SYSDATE " + mainKey.COLUMN_NAME + " ").append("\n");
						}else if ( mainKey.DATA_TYPE.equals("java.sql.Timestamp") ) {
							sql.append("            SYSTIMESTAMP " + mainKey.COLUMN_NAME + " ").append("\n");
						}
					} else {
						sql.append("            LPAD((NVL(MAX(" + mainKey.COLUMN_NAME + "),0)+1), " + mainKey.DATA_LENGTH + ", '0') " + mainKey.COLUMN_NAME + " ").append("\n");
					}
				} else if ("MSSQL".equals(dbKind)) {
					if (mainKey.DATA_TYPE.equals("float") || mainKey.DATA_TYPE.equals("int") ||  mainKey.DATA_TYPE.equals("double")) {
						sql.append("            (IsNULL(MAX(" + mainKey.COLUMN_NAME + "),0)+1) " + mainKey.COLUMN_NAME + "                            ").append("\n");
					}else if (mainKey.DATA_TYPE.equals("java.sql.Date") || mainKey.DATA_TYPE.equals("java.sql.Time") ||  mainKey.DATA_TYPE.equals("java.sql.Timestamp")) {
						sql.append("            GETDATE() " + mainKey.COLUMN_NAME + " ").append("\n");
					} else {
						sql.append("            (IsNULL(MAX(" + mainKey.COLUMN_NAME + "),0)+1) " + mainKey.COLUMN_NAME + "                            ").append("\n");
					}
				} else if ("MYSQL".equals(dbKind)) {
					if (mainKey.DATA_TYPE.equals("float") || mainKey.DATA_TYPE.equals("int") ||  mainKey.DATA_TYPE.equals("double")) {
						sql.append("            ( IF( ISNULL(MAX(" + mainKey.COLUMN_NAME + ")), 0, MAX(" + mainKey.COLUMN_NAME + ") )+1) " + mainKey.COLUMN_NAME + "  ").append("\n");
					}else if (mainKey.DATA_TYPE.equals("java.sql.Date") || mainKey.DATA_TYPE.equals("java.sql.Time") ||  mainKey.DATA_TYPE.equals("java.sql.Timestamp")) {
						sql.append("            NOW() " + mainKey.COLUMN_NAME + " ").append("\n");
					} else {
						sql.append("            LPAD(( IF( ISNULL(MAX(" + mainKey.COLUMN_NAME + ")), 0, MAX(" + mainKey.COLUMN_NAME + ") )+1), " + mainKey.DATA_LENGTH + ", '0') " + mainKey.COLUMN_NAME + " ").append("\n");
					}
				}
			}
			
			return sql.toString();
		}
		
		private String getNewKeyWhereSql(DbInfo.ColInfo[] parentKeys, DbInfo.ColInfo mainKey, String dbKind){
			StringBuffer sql = new StringBuffer();
			DbInfo.ColInfo key = null;
			if (parentKeys != null) {
				for (int i = 0; i < parentKeys.length; i++) {
					key = parentKeys[i];
					sql.append("            AND " + key.COLUMN_NAME + " = " + Util.getParamByType(key, dbKind) + " ").append("\n");
				}
			}
			return sql.toString();
		}
		private String getInsertColSql(DbInfo.ColInfo[] cols, String dbKind, String gubun){
			StringBuffer sql = new StringBuffer();
			DbInfo.ColInfo col = null;
			sql.append("                ").append("<trim prefix=\"\" suffixOverrides=\",\">").append("\n");
			if (cols != null) {
				if("COL".equals(gubun)){
					for (int i = 0; i < cols.length; i++) {
						col = cols[i];
						sql.append("                ");
						sql.append("<if test=\""+ col.COLUMN_NAME +" != null and !"+ col.COLUMN_NAME +".equals('')\" >").append(col.COLUMN_NAME).append(",</if>");
						sql.append("\n");
					}
				}else if("VAL".equals(gubun)){
					for (int i = 0; i < cols.length; i++) {
						col = cols[i];
						sql.append("                ");
						sql.append("<if test=\""+ col.COLUMN_NAME +" != null and !"+ col.COLUMN_NAME +".equals('')\" >").append(Util.getParamByType(col, dbKind)).append(",</if>");
						sql.append("\n");
					}
				}
			}
			sql.append("                ").append("</trim>");
			return sql.toString();
		}
		private String getUpdateColSql(DbInfo.ColInfo[] cols, String dbKind){
			StringBuffer sql = new StringBuffer();
			DbInfo.ColInfo col = null;
			sql.append("                ").append("<trim prefix=\"\" suffixOverrides=\",\">").append("\n");
			if (cols != null) {
				for (int i = 0; i < cols.length; i++) {
					col = cols[i];
					sql.append("                ");
					sql.append("<if test=\""+ col.COLUMN_NAME +" != null and !"+ col.COLUMN_NAME +".equals('')\" >");
					sql.append(col.COLUMN_NAME).append(" = ").append(Util.getParamByType(col, dbKind)).append(",");
					sql.append("</if>");
					sql.append("\n");
				}
			}
			sql.append("                ").append("</trim>");
			return sql.toString();
		}
		
		private String getUpdateColWhereSql(DbInfo.ColInfo[] keys, String dbKind){
			StringBuffer sql = new StringBuffer();
			DbInfo.ColInfo key = null;
			if (keys != null) {
				for (int i = 0; i < keys.length; i++) {
					key = keys[i];
					sql.append("            AND ").append(key.COLUMN_NAME + " = ").append(Util.getParamByType(key, dbKind)).append("\n");
				}
			}
			return sql.toString();
		}
		
		private String getDeleteColWhereSql(DbInfo.ColInfo[] keys, String dbKind){
			StringBuffer sql = new StringBuffer();
			DbInfo.ColInfo key = null;
			if (keys != null) {
				for (int i = 0; i < keys.length; i++) {
					key = keys[i];
					sql.append("            AND ").append(key.COLUMN_NAME + " = ").append(Util.getParamByType(key, dbKind)).append("\n");
				}
			}
			return sql.toString();
		}

	}

	protected class DaoGen {
		/**
		 * 테이블명으로 DAO 소스코드를 생성하는 메소드.(파일자동생성)
		 * 
		 * @param TABLE_NAME (물리테이블명. 필수.)
		 * @param TABLE_HAN_NAME (논리테이블명. 필수.)
		 * @param strDaoPackageName (DAO 패키지명. 필수.)
		 * @param strDaoName (DAO 명. 필수.)
		 * @param cudOnlyYn (CUD용인지여부. false일 경우 조회용 메소드를 생성하지 않음.)
		 * @param fileGenYn (DAO소스를 파일로 생성할지 여부.) 예) (new net.dstone.common.tools.BizGenerator()).new DaoGen().genDaoByTable("TB_EVT_INFO", "이벤트정보", "com.test.biz.event", "EventDao", false, false);
		 */
		protected void genDaoByTable(String TABLE_NAME, String TABLE_HAN_NAME, String strDaoPackageName, String strDaoName, boolean cudOnlyYn, boolean fileGenYn) {

			debug("||================ genDaoByTable(테이블명으로 DAO 소스코드를 생성하는 메소드) ================||");
			debug("TABLE_NAME (물리테이블명. 필수.) [" + TABLE_NAME + "]");
			debug("TABLE_HAN_NAME (논리테이블명. 필수.) [" + TABLE_HAN_NAME + "]");
			debug("strDaoPackageName (DAO 패키지명. 필수.) [" + strDaoPackageName + "]");
			debug("strDaoName (DAO 명. 필수.) [" + strDaoName + "]");
			debug("cudOnlyYn (CUD용인지여부. false일 경우 조회용 쿼리를 생성하지 않음.) [" + cudOnlyYn + "]");
			debug("fileGenYn (SQL 소스를 파일로 생성할지 여부.) [" + fileGenYn + "]");
			debug("||====================================================================================||");

			String PACKAGE_NAME = strDaoPackageName;
			StringBuffer daoH = new StringBuffer();
			StringBuffer daoConts = new StringBuffer();
			StringBuffer daoF = new StringBuffer();

			String voName = "";
			String sVoName = "";
			String tableName = "";
			String nameSpace = "";
			String daoName = "";
			String daoFileName = "";

			voName = net.dstone.common.utils.StringUtil.getHungarianName(TABLE_NAME, " ").trim() + "CudVo";
			sVoName = net.dstone.common.utils.StringUtil.getHungarianName(TABLE_NAME, "").trim() + "CudVo";
			tableName = net.dstone.common.utils.StringUtil.getHungarianName(TABLE_NAME, " ").trim();

			daoName = strDaoName;
			nameSpace = StringUtil.replace((PACKAGE_NAME + "." + strDaoName).toUpperCase(), ".", "_");

			daoH.append("package " + PACKAGE_NAME + "; ").append("\n");
			daoH.append(" ").append("\n");
			daoH.append("import java.util.List; ").append("\n");
			daoH.append("import java.util.Map; ").append("\n");
			daoH.append(" ").append("\n");
			daoH.append("import org.springframework.stereotype.Repository; ").append("\n");
			daoH.append("import org.springframework.beans.factory.annotation.Autowired; ").append("\n");
			daoH.append("import org.springframework.beans.factory.annotation.Qualifier; ").append("\n");
			daoH.append("import org.mybatis.spring.SqlSessionTemplate; ").append("\n");
			daoH.append(" ").append("\n");
			daoH.append("@Repository ").append("\n");

			if ((PACKAGE_NAME + "." + daoName).equals(COMM_CUD_PACKAGE_NAME + "." + COMM_CUD_DAO_NAME + "")) {
				nameSpace = StringUtil.replace((PACKAGE_NAME + "." + strDaoName).toUpperCase(), ".", "_");
				daoH.append("public class " + daoName + " extends net.dstone.common.biz.BaseDao { ").append("\n");
			} else {
				nameSpace = StringUtil.replace((PACKAGE_NAME + "." + strDaoName).toUpperCase(), ".", "_");
				daoH.append("public class " + daoName + " extends net.dstone.common.biz.BaseDao { ").append("\n");
			}
			daoH.append("    @Autowired ").append("\n");
			daoH.append("    @Qualifier(\""+SQL_CLIENT_ID+"\") ").append("\n");
			daoH.append("    private SqlSessionTemplate "+SQL_CLIENT_ID+"; ").append("\n");
			daoH.append("     ").append("\n");

			daoConts.append("    /******************************************* " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 시작 *******************************************/ ").append("\n");

			if (!cudOnlyYn) {
				daoConts.append("    /* ").append("\n");
				daoConts.append("     * " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 리스트 조회 ").append("\n");
				daoConts.append("     */ ").append("\n");
				daoConts.append("    public List<" + COMM_CUD_PACKAGE_NAME + ".vo." + voName + "> list" + tableName + "(" + COMM_CUD_PACKAGE_NAME + ".vo." + voName + " " + sVoName + ") throws Exception { ").append("\n");
				daoConts.append("        return (List<" + COMM_CUD_PACKAGE_NAME + ".vo." + voName + ">) "+SQL_CLIENT_ID+".selectList(\"" + nameSpace + ".list" + tableName + "\", " + sVoName + "); ").append("\n");
				daoConts.append("    } ").append("\n");
				daoConts.append("    /* ").append("\n");
				daoConts.append("     * " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 상세 조회 ").append("\n");
				daoConts.append("     */ ").append("\n");
				daoConts.append("    public " + COMM_CUD_PACKAGE_NAME + ".vo." + voName + " select" + tableName + "(" + COMM_CUD_PACKAGE_NAME + ".vo." + voName + " " + sVoName + ") throws Exception { ").append("\n");
				daoConts.append("        return (" + COMM_CUD_PACKAGE_NAME + ".vo." + voName + ") "+SQL_CLIENT_ID+".selectOne(\"" + nameSpace + ".select" + tableName + "\", " + sVoName + "); ").append("\n");
				daoConts.append("    } ").append("\n");
			}

			daoConts.append("    /* ").append("\n");
			daoConts.append("     * " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] NEW키값 조회 ").append("\n");
			daoConts.append("     */ ").append("\n");
			daoConts.append("    public " + COMM_CUD_PACKAGE_NAME + ".vo." + voName + " select" + tableName + "NewKey(" + COMM_CUD_PACKAGE_NAME + ".vo." + voName + " " + sVoName + ") throws Exception { ").append("\n");
			daoConts.append("        return (" + COMM_CUD_PACKAGE_NAME + ".vo." + voName + ") "+SQL_CLIENT_ID+".selectOne(\"" + nameSpace + ".select" + tableName + "NewKey\", " + sVoName + "); ").append("\n");
			daoConts.append("    } ").append("\n");
			daoConts.append("    /* ").append("\n");
			daoConts.append("     * " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 입력 ").append("\n");
			daoConts.append("     */  ").append("\n");
			daoConts.append("    public void insert" + tableName + "(" + COMM_CUD_PACKAGE_NAME + ".vo." + voName + " " + sVoName + ") throws Exception { ").append("\n");
			daoConts.append("        "+SQL_CLIENT_ID+".insert(\"" + nameSpace + ".insert" + tableName + "\", " + sVoName + "); ").append("\n");
			daoConts.append("    } ").append("\n");
			daoConts.append("    /* ").append("\n");
			daoConts.append("     * " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 수정 ").append("\n");
			daoConts.append("     */  ").append("\n");
			daoConts.append("    public void update" + tableName + "(" + COMM_CUD_PACKAGE_NAME + ".vo." + voName + " " + sVoName + ") throws Exception { ").append("\n");
			daoConts.append("        "+SQL_CLIENT_ID+".update(\"" + nameSpace + ".update" + tableName + "\", " + sVoName + "); ").append("\n");
			daoConts.append("    } ").append("\n");
			daoConts.append("    /* ").append("\n");
			daoConts.append("     * " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 삭제 ").append("\n");
			daoConts.append("     */ ").append("\n");
			daoConts.append("    public void delete" + tableName + "(" + COMM_CUD_PACKAGE_NAME + ".vo." + voName + " " + sVoName + ") throws Exception { ").append("\n");
			daoConts.append("        "+SQL_CLIENT_ID+".delete(\"" + nameSpace + ".delete" + tableName + "\", " + sVoName + "); ").append("\n");
			daoConts.append("    } ").append("\n");
			daoConts.append("    /******************************************* " + TABLE_HAN_NAME + "[" + TABLE_NAME + "] 끝 *******************************************/ ").append("\n\n");

			daoF.append("} ").append("\n");

			System.out.println("============================ DAO START ============================");
			String fileConts = "";
			if (!fileGenYn) {
				fileConts = daoH.toString() + daoConts.toString() + daoF.toString();
			} else {
				daoFileName = SRC_ROOT + "/" + StringUtil.replace(PACKAGE_NAME + "." + daoName, ".", "/") + ".java";
				if (!FileUtil.isFileExist(daoFileName)) {
					net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(daoFileName), FileUtil.getFileName(daoFileName), daoH.toString() + daoF.toString(), CHARSET);
				}
				fileConts = net.dstone.common.utils.FileUtil.readFile(daoFileName, CHARSET);
				String fileContsH = fileConts.substring(0, fileConts.lastIndexOf("}"));
				String fileContsF = fileConts.substring(fileConts.lastIndexOf("}"));
				fileConts = fileContsH + "\n" + daoConts.toString() + "\n" + fileContsF;
				net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(daoFileName), FileUtil.getFileName(daoFileName), fileConts, CHARSET);
			}
			System.out.println(fileConts);
			System.out.println("============================ DAO END ============================");
			// System.out.println("");
		}

		/**
		 * SQL로 DAO 소스코드를 생성하는 메소드.(파일자동생성)
		 * 
		 * @param strDaoPackageName (DAO 패키지명. 필수.)
		 * @param strDaoName (DAO 명. 필수.)
		 * @param strMethodName (DAO 메소드명. 필수.)
		 * @param strMethodComment (DAO 메소드설명. 필수.)
		 * @param strVoPackageName (VO 패키지명. 필수.)
		 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
		 * @param listYn (리스트성인지 상세조회성인지 여부.)
		 * @param pageYn (카운트 SQL을 생성할지 여부.)
		 * @param fileGenYn (DAO소스를 파일로 생성할지 여부.) 예) (new net.dstone.common.tools.BizGenerator()).new DaoGen().genDaoBySql( "com.test.biz.event", "EventDao", "listEvent", "이벤트리스트조회", "com.test.biz.event.vo", "EventListVo", false, true, false);
		 */
		protected void genDaoBySql(String strDaoPackageName, String strDaoName, String strMethodName, String strMethodComment, String strVoPackageName, String strVoName, boolean listYn, boolean pageYn, boolean fileGenYn) {

			debug("||=================== genDaoBySql(SQL로 DAO 소스코드를 생성하는 메소드) ===================||");
			debug("strDaoPackageName (DAO 패키지명. 필수.) [" + strDaoPackageName + "]");
			debug("strDaoName (DAO 명. 필수.) [" + strDaoName + "]");
			debug("strMethodName (DAO 메소드명. 필수.) [" + strMethodName + "]");
			debug("strMethodComment (DAO 메소드설명. 필수.) [" + strMethodComment + "]");
			debug("strVoPackageName (VO 패키지명. 필수.) [" + strVoPackageName + "]");
			debug("strVoName (VO명. 필수.) [" + strVoName + "]");
			debug("listYn (리스트성인지 상세조회성인지 여부.) [" + listYn + "]");
			debug("pageYn (카운트 SQL을 생성할지 여부.) [" + pageYn + "]");
			debug("fileGenYn (SQL 소스를 파일로 생성할지 여부.) [" + fileGenYn + "]");
			debug("||====================================================================================||");

			String PACKAGE_NAME = strDaoPackageName;
			StringBuffer daoH = new StringBuffer();
			StringBuffer daoConts = new StringBuffer();
			StringBuffer daoF = new StringBuffer();
			
			String voName = "";
			String sVoName = "";
			String nameSpace = "";
			String daoName = "";
			
			String daoFileName = "";
			boolean isDaoFileNameExists = false;

			voName = strVoName;
			sVoName = strVoName.substring(0, 1).toLowerCase() + strVoName.substring(1);

			daoName = strDaoName;
			nameSpace = StringUtil.replace((PACKAGE_NAME + "." + daoName).toUpperCase(), ".", "_");

			daoFileName = SRC_ROOT + "/" + StringUtil.replace(PACKAGE_NAME + "." + daoName, ".", "/") + ".java";
			isDaoFileNameExists = FileUtil.isFileExist(daoFileName);

			daoH.append("package " + PACKAGE_NAME + "; ").append("\n");
			daoH.append(" ").append("\n");
			daoH.append("import java.util.List; ").append("\n");
			daoH.append("import java.util.Map; ").append("\n");
			daoH.append(" ").append("\n");
			daoH.append("import org.springframework.stereotype.Repository; ").append("\n");
			daoH.append("import org.springframework.beans.factory.annotation.Autowired; ").append("\n");
			daoH.append("import org.springframework.beans.factory.annotation.Qualifier; ").append("\n");
			daoH.append("import org.mybatis.spring.SqlSessionTemplate; ").append("\n");
			daoH.append(" ").append("\n");
			daoH.append("@Repository ").append("\n");
			daoH.append("public class " + daoName + " extends net.dstone.common.biz.BaseDao { ").append("\n");
			
			if(!isDaoFileNameExists || !net.dstone.common.utils.BeanUtil.isBeanMemberName(PACKAGE_NAME + "." + daoName, SQL_CLIENT_ID)){
				daoConts.append("    @Autowired ").append("\n");
				daoConts.append("    @Qualifier(\""+SQL_CLIENT_ID+"\") ").append("\n");
				daoConts.append("    private SqlSessionTemplate "+SQL_CLIENT_ID+"; ").append("\n");
				daoConts.append("     ").append("\n");
			}

			if (pageYn) {
				daoConts.append("    /* ").append("\n");
				daoConts.append("     * " + strMethodComment + "(카운트) ").append("\n");
				daoConts.append("     */ ").append("\n");
				daoConts.append("    public int " + strMethodName + "Count(" + strVoPackageName + "." + voName + " " + sVoName + ") throws Exception { ").append("\n");
				daoConts.append("        Object returnObj = "+SQL_CLIENT_ID+".selectOne(\"" + nameSpace + "." + strMethodName + "Count\", " + sVoName + "); ").append("\n");
				daoConts.append("        if (returnObj == null) {").append("\n");
				daoConts.append("            return 0;").append("\n");
				daoConts.append("        } else {").append("\n");
				daoConts.append("            return ((Integer) returnObj).intValue();").append("\n");
				daoConts.append("        }").append("\n");
				daoConts.append("    } ").append("\n");
			}

			daoConts.append("    /* ").append("\n");
			daoConts.append("     * " + strMethodComment + " ").append("\n");
			daoConts.append("     */ ").append("\n");
			if (listYn) {
				daoConts.append("    public List<" + strVoPackageName + "." + voName + "> " + strMethodName + "(" + strVoPackageName + "." + voName + " " + sVoName + ") throws Exception { ").append("\n");
				daoConts.append("        List<" + strVoPackageName + "." + voName + "> list = "+SQL_CLIENT_ID+".selectList(\"" + nameSpace + "." + strMethodName + "\", " + sVoName + "); ").append("\n");
				daoConts.append("        return list; ").append("\n");
				daoConts.append("    } ").append("\n");
			} else {
				daoConts.append("    public " + strVoPackageName + "." + voName + " " + strMethodName + "(" + strVoPackageName + "." + voName + " " + sVoName + ") throws Exception { ").append("\n");
				daoConts.append("        return (" + strVoPackageName + "." + voName + ") "+SQL_CLIENT_ID+".selectOne(\"" + nameSpace + "." + strMethodName + "\", " + sVoName + "); ").append("\n");
				daoConts.append("    } ").append("\n");
			}
			daoF.append("} ").append("\n");

			System.out.println("============================ DAO START ============================");
			String fileConts = "";
			if (!fileGenYn) {
				fileConts = daoH.toString() + daoConts.toString() + daoF.toString();
			} else {
				daoFileName = SRC_ROOT + "/" + StringUtil.replace(PACKAGE_NAME + "." + daoName, ".", "/") + ".java";
				if (!FileUtil.isFileExist(daoFileName)) {
					net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(daoFileName), FileUtil.getFileName(daoFileName), daoH.toString() + daoF.toString(), CHARSET);
				}
				fileConts = net.dstone.common.utils.FileUtil.readFile(daoFileName, CHARSET);
				String fileContsH = fileConts.substring(0, fileConts.lastIndexOf("}"));
				String fileContsF = fileConts.substring(fileConts.lastIndexOf("}"));
				fileConts = fileContsH + "\n" +  daoConts.toString() + "\n" + fileContsF;
				// System.out.println(fileConts);
				net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(daoFileName), FileUtil.getFileName(daoFileName), fileConts, CHARSET);
			}
			System.out.println(fileConts);
			System.out.println("============================ DAO END ============================");

		}
	}

	protected class SvcGen {
		/**
		 * SVC 소스코드를 생성하는 메소드.
		 * 
		 * @param strSvcPackageName (SVC 패키지명. 필수.)
		 * @param strSvcName (SVC 명. 필수.)
		 * @param strDaoPackageName (DAO 패키지명. 필수.)
		 * @param strDaoName (DAO 명. 필수.)
		 * @param strMethodName (SVC 메소드명. 필수.)
		 * @param strMethodComment (SVC 메소드설명. 필수.)
		 * @param strVoPackageName (VO 패키지명. 필수.)
		 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
		 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
		 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
		 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
		 * @param fileGenYn (SVC소스를 파일로 생성할지 여부.) 예) (new net.dstone.common.tools.BizGenerator()).new WsGen().genSvcForJsp( "com.test.biz.event", "EventService", "com.test.biz.event", "EventDao", "listEvent", "이벤트리스트조회", "com.test.biz.event.vo", "EventListVo", 0, true, false);
		 */
		protected void genSvc(String strSvcPackageName, String strSvcName, String strDaoPackageName, String strDaoName, String strMethodName, String strMethodComment, String strVoPackageName, String strVoName, int CRUD, String strTableName, boolean pageYn, boolean fileGenYn) {

			debug("||======================= genSvc(SVC 소스코드를 생성하는 메소드) ==========================||");
			debug("strSvcPackageName (SVC 패키지명. 필수.) [" + strSvcPackageName + "]");
			debug("strSvcName (SVC 명. 필수.) [" + strSvcName + "]");
			debug("strDaoPackageName (DAO 패키지명. 필수.) [" + strDaoPackageName + "]");
			debug("strDaoName (DAO 명. 필수.) [" + strDaoName + "]");
			debug("strMethodName (DAO 메소드명. 필수.) [" + strMethodName + "]");
			debug("strMethodComment (DAO 메소드설명. 필수.) [" + strMethodComment + "]");
			debug("strVoPackageName (VO 패키지명. 필수.) [" + strVoPackageName + "]");
			debug("strVoName (VO명. 필수.) [" + strVoName + "]");
			debug("CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.) [" + CRUD + "]");
			debug("strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당) [" + strTableName + "]");
			debug("pageYn (카운트 SQL을 생성할지 여부.) [" + pageYn + "]");
			debug("fileGenYn (SQL 소스를 파일로 생성할지 여부.) [" + fileGenYn + "]");
			debug("||====================================================================================||");

			String PACKAGE_NAME = strSvcPackageName;
			StringBuffer svcH = new StringBuffer();
			StringBuffer svcConts = new StringBuffer();
			StringBuffer svcF = new StringBuffer();
			String fileConts = "";

			String voName = strVoName;
			String sVoName = "";
			String svcName = "";
			String svcFileName = "";
			String commonDaoAlias = net.dstone.common.tools.BizGenerator.COMM_CUD_DAO_NAME.substring(0, 1).toLowerCase() + net.dstone.common.tools.BizGenerator.COMM_CUD_DAO_NAME.substring(1);

			String tableVoPackageName = "";
			String tableVoName = "";
			String tableName = "";

			String daoAlias = "";

			DbInfo.ColInfo[] keys = null;
			DbInfo.ColInfo key = null;
			DbInfo.ColInfo mainKey = null;
			DbInfo.ColInfo[] parentKeys = null;

			if (CRUD == 2 || CRUD == 3 || CRUD == 4) {
				if (!StringUtil.isEmpty(strTableName)) {
					tableVoPackageName = COMM_CUD_PACKAGE_NAME + ".vo";
					tableVoName = net.dstone.common.utils.StringUtil.getHungarianName(strTableName, " ").trim() + "CudVo";
					tableName = net.dstone.common.utils.StringUtil.getHungarianName(strTableName, " ").trim();
				}
				// KEY값 구해오는 부분.

				try {
					keys = DbInfo.getTab(strTableName).getKey();
					if (keys.length > 1) {
						parentKeys = new DbInfo.ColInfo[keys.length - 1];
						for (int i = 0; i < keys.length; i++) {
							if (i == keys.length - 1) {
								mainKey = keys[i];
							} else {
								parentKeys[i] = keys[i];
							}
						}
					} else if (keys.length == 1) {
						mainKey = keys[0];
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			try {

				if (!StringUtil.isEmpty(strDaoName)) {
					daoAlias = strDaoName.substring(0, 1).toLowerCase() + strDaoName.substring(1);
				}

				sVoName = "";
				if (!StringUtil.isEmpty(strVoName)) {
					sVoName = strVoName.substring(0, 1).toLowerCase() + strVoName.substring(1);
				}
				svcName = strSvcName;

				svcFileName = SRC_ROOT + "/" + StringUtil.replace(PACKAGE_NAME + "." + strSvcName, ".", "/") + ".java";

				svcH.append("package " + PACKAGE_NAME + "; ").append("\n");
				svcH.append(" ").append("\n");
				svcH.append("import java.util.Map; ").append("\n");
				svcH.append("import java.util.HashMap; ").append("\n");
				svcH.append("import java.util.List; ").append("\n");
				svcH.append(" ").append("\n");
				svcH.append("import org.springframework.beans.factory.annotation.Autowired; ").append("\n");
				svcH.append("import org.springframework.stereotype.Service; ").append("\n");
				svcH.append("import org.springframework.transaction.annotation.Transactional; ").append("\n");
				svcH.append(" ").append("\n");
				svcH.append("import net.dstone.common.biz.BaseService; ").append("\n");
				svcH.append("import net.dstone.common.utils.LogUtil; ").append("\n");
				svcH.append(" ").append("\n");
				//svcH.append("@Transactional(propagation=Propagation.REQUIRED, noRollbackFor=Exception.class)").append("\n");
				svcH.append("@Service ").append("\n");
				svcH.append("public class " + strSvcName + " extends BaseService { ").append("\n");
				svcH.append("     ").append("\n");
				svcH.append("    LogUtil logger = getLogger(); ").append("\n");
				svcH.append("     ").append("\n");

				if (!FileUtil.isFileExist(svcFileName)) {
					svcConts.append("    /********* 공통 입력/수정/삭제 DAO 정의부분 시작 *********/").append("\n");
					svcConts.append("    @Autowired ").append("\n");
					svcConts.append("    private " + net.dstone.common.tools.BizGenerator.COMM_CUD_PACKAGE_NAME + "." + net.dstone.common.tools.BizGenerator.COMM_CUD_DAO_NAME + " " + commonDaoAlias + "; ").append("\n");
					svcConts.append("    /********* 공통 입력/수정/삭제 DAO 정의부분 끝 *********/").append("\n");

					/* 트랜젝션종류 - 0:다건조회, 1:단건조회 */
					if (CRUD == 0 || CRUD == 1) {
						if (!StringUtil.isEmpty(strDaoPackageName) && !StringUtil.isEmpty(strDaoName)) {
							svcConts.append("    /********* DAO 정의부분 시작 *********/").append("\n");
							svcConts.append("    @Autowired ").append("\n");
							svcConts.append("    private " + strDaoPackageName + "." + strDaoName + " " + daoAlias + "; ").append("\n");
							svcConts.append("    /********* DAO 정의부분 끝 *********/").append("\n");
						}
					}
					svcConts.append("    ").append("\n");
				} else if (!net.dstone.common.utils.BeanUtil.isBeanMemberName(PACKAGE_NAME + "." + strSvcName, daoAlias)) {
					if (!net.dstone.common.utils.BeanUtil.isBeanMemberName(PACKAGE_NAME + "." + strSvcName, commonDaoAlias)) {
						svcConts.append("    /********* 공통 입력/수정/삭제 DAO 정의부분 시작 *********/").append("\n");
						svcConts.append("    @Autowired ").append("\n");
						svcConts.append("    private " + net.dstone.common.tools.BizGenerator.COMM_CUD_PACKAGE_NAME + "." + net.dstone.common.tools.BizGenerator.COMM_CUD_DAO_NAME + " " + commonDaoAlias + "; ").append("\n");
						svcConts.append("    /********* 공통 입력/수정/삭제 DAO 정의부분 끝 *********/").append("\n");
					}
					/* 트랜젝션종류 - 0:다건조회, 1:단건조회 */
					if (CRUD == 0 || CRUD == 1) {
						if (!StringUtil.isEmpty(strDaoPackageName) && !StringUtil.isEmpty(strDaoName)) {
							if (!net.dstone.common.utils.BeanUtil.isBeanMemberName(strDaoPackageName + "." + strDaoName, daoAlias)) {
								svcConts.append("    /********* DAO 정의부분 시작 *********/").append("\n");
								svcConts.append("    @Autowired ").append("\n");
								svcConts.append("    private " + strDaoPackageName + "." + strDaoName + " " + daoAlias + "; ").append("\n");
								svcConts.append("    /********* DAO 정의부분 끝 *********/").append("\n");
							}
						}
					}
					svcConts.append("    ").append("\n");
				}

				/* 트랜젝션종류 - 0:다건조회 */
				if (CRUD == 0) {

					svcConts.append("    /** ").append("\n");
					svcConts.append("     * " + strMethodComment + " ").append("\n");
					svcConts.append("     * @param paramVo ").append("\n");
					svcConts.append("     * @return ").append("\n");
					svcConts.append("     * @throws Exception ").append("\n");
					svcConts.append("     */ ").append("\n");
					svcConts.append("    public Map " + strMethodName + "(" + strVoPackageName + "." + strVoName + " paramVo) throws Exception{ ").append("\n");
					svcConts.append("        // 필요없는 주석들은 제거하시고 사용하시면 됩니다.").append("\n");
					svcConts.append("        /************************ 변수 선언 시작 ************************/ ").append("\n");
					svcConts.append("        HashMap returnMap;                                        // 반환대상 맵 ").append("\n");

					svcConts.append("        List<" + strVoPackageName + "." + strVoName + "> list;            // 리스트 ").append("\n");
					if (pageYn) {
						svcConts.append("        /*** 페이징파라메터 세팅 시작 ***/").append("\n");
						svcConts.append("        net.dstone.common.utils.PageUtil pageUtil; 						// 페이징 유틸 ").append("\n");
						svcConts.append("        int INT_TOTAL_CNT = 0;").append("\n");
						svcConts.append("        int INT_FROM = 0;").append("\n");
						svcConts.append("        int INT_TO = 0;").append("\n");
						svcConts.append("        /*** 페이징파라메터 세팅 끝 ***/").append("\n");
					}
					svcConts.append("        /************************ 변수 선언 끝 **************************/ ").append("\n");
					svcConts.append("        try { ").append("\n");
					svcConts.append("            /************************ 변수 정의 시작 ************************/ ").append("\n");
					svcConts.append("            returnMap = new HashMap(); ").append("\n");
					svcConts.append("            list = null; ").append("\n");
					svcConts.append("            /************************ 변수 정의 끝 **************************/ ").append("\n");
					svcConts.append("            ").append("\n");
					svcConts.append("            /************************ 비즈니스로직 시작 ************************/ ").append("\n");
					if (pageYn) {
						svcConts.append("            /*** 페이징파라메터 세팅 시작 ***/").append("\n");
						svcConts.append("            INT_TOTAL_CNT = " + daoAlias + "." + strMethodName + "Count(paramVo);").append("\n");
						svcConts.append( getPagingLogic(SystemUtil.getInstance().getProperty(DBID + ".DbKind"))).append("\n");
						svcConts.append("            /*** 페이징파라메터 세팅 끝 ***/").append("\n");
					}
					svcConts.append("            ").append("\n");
					svcConts.append("            //DAO 호출부분 구현").append("\n");
					svcConts.append("            list = " + daoAlias + "." + strMethodName + "(paramVo); ").append("\n");
					svcConts.append("            returnMap.put(\"returnObj\", list); ").append("\n");
					svcConts.append("            ").append("\n");
					if (pageYn) {
						svcConts.append("            /*** 페이징유틸 생성 시작 ***/ ").append("\n");
						svcConts.append("            pageUtil = new net.dstone.common.utils.PageUtil(paramVo.getPAGE_NUM(), paramVo.getPAGE_SIZE(), INT_TOTAL_CNT);").append("\n");
						svcConts.append("            returnMap.put(\"pageUtil\", pageUtil);").append("\n");
						svcConts.append("            /*** 페이징유틸 생성 끝 ***/ ").append("\n");
					}

					svcConts.append("            /************************ 비즈니스로직 끝 **************************/ ").append("\n");
					svcConts.append("        } catch (Exception e) { ").append("\n");
					svcConts.append("            logger.error(this.getClass().getName() + \"." + strMethodName + " 수행중 예외발생. 상세사항:\" + e.toString()); ").append("\n");
					svcConts.append("            throw new Exception( \"" + strMethodComment + " 수행중 예외발생\" );").append("\n");
					svcConts.append("        } ").append("\n");
					svcConts.append("        return returnMap; ").append("\n");
					svcConts.append("    } ").append("\n");

					/* 트랜젝션종류 - 1:단건조회 */
				} else if (CRUD == 1) {

					svcConts.append("    /** ").append("\n");
					svcConts.append("     * " + strMethodComment + " ").append("\n");
					svcConts.append("     * @param paramVo ").append("\n");
					svcConts.append("     * @return ").append("\n");
					svcConts.append("     * @throws Exception ").append("\n");
					svcConts.append("     */ ").append("\n");
					svcConts.append("    public " + strVoPackageName + "." + strVoName + " " + strMethodName + "(" + strVoPackageName + "." + strVoName + " paramVo) throws Exception{ ").append("\n");
					svcConts.append("        // 필요없는 주석들은 제거하시고 사용하시면 됩니다.").append("\n");
					svcConts.append("        /************************ 변수 선언 시작 ************************/ ").append("\n");
					svcConts.append("        " + strVoPackageName + "." + strVoName + " returnObj;            // 반환객체 ").append("\n");
					svcConts.append("        /************************ 변수 선언 끝 **************************/ ").append("\n");
					svcConts.append("        try { ").append("\n");
					svcConts.append("            /************************ 변수 정의 시작 ************************/ ").append("\n");
					svcConts.append("            returnObj = null;").append("\n");
					svcConts.append("            /************************ 변수 정의 끝 **************************/ ").append("\n");
					svcConts.append("            ").append("\n");
					svcConts.append("            /************************ 비즈니스로직 시작 ************************/ ").append("\n");
					svcConts.append("            ").append("\n");
					svcConts.append("            //DAO 호출부분 구현").append("\n");
					svcConts.append("            returnObj = " + daoAlias + "." + strMethodName + "(paramVo); ").append("\n");
					svcConts.append("            ").append("\n");

					svcConts.append("            /************************ 비즈니스로직 끝 **************************/ ").append("\n");
					svcConts.append("        } catch (Exception e) { ").append("\n");
					svcConts.append("            logger.error(this.getClass().getName() + \"." + strMethodName + " 수행중 예외발생. 상세사항:\" + e.toString()); ").append("\n");
					svcConts.append("            throw new Exception( \"" + strMethodComment + " 수행중 예외발생\" );").append("\n");
					svcConts.append("        } ").append("\n");
					svcConts.append("        return returnObj; ").append("\n");
					svcConts.append("    } ").append("\n");

					/* 트랜젝션종류 - 2:입력 */
				} else if (CRUD == 2) {

					svcConts.append("    /**  ").append("\n");
					svcConts.append("     * " + strMethodComment + " ").append("\n");
					svcConts.append("     * @param paramVo  ").append("\n");
					svcConts.append("     * @return boolean ").append("\n");
					svcConts.append("     * @throws Exception  ").append("\n");
					svcConts.append("    */  ").append("\n");
					svcConts.append("    public boolean " + strMethodName + "(" + tableVoPackageName + "." + tableVoName + " paramVo) throws Exception{  ").append("\n");
					svcConts.append("        // 필요없는 주석들은 제거하시고 사용하시면 됩니다. ").append("\n");
					svcConts.append("        /************************ 변수 선언 시작 ************************/  ").append("\n");
					svcConts.append("        boolean isSuccess = false; ").append("\n");
					svcConts.append("        " + tableVoPackageName + "." + tableVoName + " newKeyVo; ").append("\n");
					svcConts.append("        /************************ 변수 선언 끝 **************************/  ").append("\n");
					svcConts.append("        try {  ").append("\n");
					svcConts.append("            /************************ 변수 정의 시작 ************************/  ").append("\n");
					svcConts.append("            newKeyVo = new " + tableVoPackageName + "." + tableVoName + "();").append("\n");
					svcConts.append("            /************************ 변수 정의 끝 **************************/  ").append("\n");
					svcConts.append("             ").append("\n");
					svcConts.append("            /************************ 비즈니스로직 시작 ************************/  ").append("\n");
					svcConts.append("            //NEW KEY 생성 부분 구현 ").append("\n");
					
					if (parentKeys != null) {
						for (int i = 0; i < parentKeys.length; i++) {
							key = parentKeys[i];
							svcConts.append("            newKeyVo.set" + key.COLUMN_NAME + "( paramVo.get" + key.COLUMN_NAME + "() ); ").append("\n");
						}
					}
					if (mainKey != null) {
						svcConts.append("            paramVo.set" + mainKey.COLUMN_NAME + "( " + commonDaoAlias + ".select" + tableName + "NewKey(newKeyVo).get" + mainKey.COLUMN_NAME + "() ); ").append("\n");
					}
					svcConts.append("            //DAO 호출부분 구현 ").append("\n");
					svcConts.append("            " + commonDaoAlias + ".insert" + tableName + "(paramVo);  ").append("\n");
					svcConts.append("            isSuccess = true; ").append("\n");
					svcConts.append("            /************************ 비즈니스로직 끝 **************************/  ").append("\n");
					svcConts.append("        } catch (Exception e) { ").append("\n");
					svcConts.append("            logger.error(this.getClass().getName() + \"." + strMethodName + "(\"+paramVo+\") 수행중 예외발생. 상세사항:\" + e.toString());  ").append("\n");
					svcConts.append("            throw new Exception( \"" + strMethodComment + " 수행중 예외발생\" ); ").append("\n");
					svcConts.append("        }  ").append("\n");
					svcConts.append("        return isSuccess;  ").append("\n");
					svcConts.append("    } ").append("\n");

					/* 트랜젝션종류 - 3:수정 */
				} else if (CRUD == 3) {

					svcConts.append("    /**  ").append("\n");
					svcConts.append("     * " + strMethodComment + " ").append("\n");
					svcConts.append("     * @param paramVo  ").append("\n");
					svcConts.append("     * @return boolean ").append("\n");
					svcConts.append("     * @throws Exception  ").append("\n");
					svcConts.append("    */  ").append("\n");
					svcConts.append("    public boolean " + strMethodName + "(" + tableVoPackageName + "." + tableVoName + " paramVo) throws Exception{  ").append("\n");
					svcConts.append("        // 필요없는 주석들은 제거하시고 사용하시면 됩니다. ").append("\n");
					svcConts.append("        /************************ 변수 선언 시작 ************************/  ").append("\n");
					svcConts.append("        boolean isSuccess = false; ").append("\n");
					svcConts.append("        /************************ 변수 선언 끝 **************************/  ").append("\n");
					svcConts.append("        try {  ").append("\n");
					svcConts.append("            /************************ 변수 정의 시작 ************************/  ").append("\n");
					svcConts.append("            ").append("\n");
					svcConts.append("            /************************ 변수 정의 끝 **************************/  ").append("\n");
					svcConts.append("             ").append("\n");
					svcConts.append("            /************************ 비즈니스로직 시작 ************************/  ").append("\n");
					svcConts.append("            //DAO 호출부분 구현 ").append("\n");
					svcConts.append("            " + commonDaoAlias + ".update" + tableName + "(paramVo);  ").append("\n");
					svcConts.append("            isSuccess = true; ").append("\n");
					svcConts.append("            /************************ 비즈니스로직 끝 **************************/  ").append("\n");
					svcConts.append("        } catch (Exception e) { ").append("\n");
					svcConts.append("            logger.error(this.getClass().getName() + \"." + strMethodName + "(\"+paramVo+\") 수행중 예외발생. 상세사항:\" + e.toString());  ").append("\n");
					svcConts.append("            throw new Exception( \"" + strMethodComment + " 수행중 예외발생\" ); ").append("\n");
					svcConts.append("        }  ").append("\n");
					svcConts.append("        return isSuccess;  ").append("\n");
					svcConts.append("    } ").append("\n");

					/* 트랜젝션종류 - 4:삭제 */
				} else if (CRUD == 4) {

					svcConts.append("    /**  ").append("\n");
					svcConts.append("     * " + strMethodComment + " ").append("\n");
					svcConts.append("     * @param paramVo  ").append("\n");
					svcConts.append("     * @return boolean ").append("\n");
					svcConts.append("     * @throws Exception  ").append("\n");
					svcConts.append("    */  ").append("\n");
					svcConts.append("    public boolean " + strMethodName + "(" + tableVoPackageName + "." + tableVoName + " paramVo) throws Exception{  ").append("\n");
					svcConts.append("        // 필요없는 주석들은 제거하시고 사용하시면 됩니다. ").append("\n");
					svcConts.append("        /************************ 변수 선언 시작 ************************/  ").append("\n");
					svcConts.append("        boolean isSuccess = false; ").append("\n");
					svcConts.append("        /************************ 변수 선언 끝 **************************/  ").append("\n");
					svcConts.append("        try {  ").append("\n");
					svcConts.append("            /************************ 변수 정의 시작 ************************/  ").append("\n");
					svcConts.append("            ").append("\n");
					svcConts.append("            /************************ 변수 정의 끝 **************************/  ").append("\n");
					svcConts.append("             ").append("\n");
					svcConts.append("            /************************ 비즈니스로직 시작 ************************/  ").append("\n");
					svcConts.append("            //DAO 호출부분 구현 ").append("\n");
					svcConts.append("            " + commonDaoAlias + ".delete" + tableName + "(paramVo);  ").append("\n");
					svcConts.append("            isSuccess = true; ").append("\n");
					svcConts.append("            /************************ 비즈니스로직 끝 **************************/  ").append("\n");
					svcConts.append("        } catch (Exception e) { ").append("\n");
					svcConts.append("            logger.error(this.getClass().getName() + \"." + strMethodName + "(\"+paramVo+\") 수행중 예외발생. 상세사항:\" + e.toString());  ").append("\n");
					svcConts.append("            throw new Exception( \"" + strMethodComment + " 수행중 예외발생\" ); ").append("\n");
					svcConts.append("        }  ").append("\n");
					svcConts.append("        return isSuccess;  ").append("\n");
					svcConts.append("    } ").append("\n");

				}

				svcF.append("} ").append("\n");

				System.out.println("============================ SVC START ============================");

				if (!FileUtil.isFileExist(svcFileName)) {
					fileConts = svcH.toString() + svcConts.toString() + svcF.toString();
				} else {
					fileConts = svcConts.toString();
				}

				if (fileGenYn) {
					if (!FileUtil.isFileExist(svcFileName)) {
						net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(svcFileName), FileUtil.getFileName(svcFileName), svcH.toString() + svcF.toString(), CHARSET);
					}
					fileConts = net.dstone.common.utils.FileUtil.readFile(svcFileName, CHARSET);
					String fileContsH = fileConts.substring(0, fileConts.lastIndexOf("}"));
					String fileContsF = fileConts.substring(fileConts.lastIndexOf("}"));
					fileConts = fileContsH + "\n" + svcConts.toString() + "\n" + fileContsF;
					// System.out.println(fileConts);
					net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(svcFileName), FileUtil.getFileName(svcFileName), fileConts, CHARSET);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			System.out.println(fileConts);
			System.out.println("============================ SVC END ============================");

		}
		
		private String getPagingLogic(String dbKind){
			StringBuffer paging = new StringBuffer();

			if ("ORACLE".equals(dbKind)) {
				paging.append("            if ( 1>paramVo.getPAGE_NUM() ) { paramVo.setPAGE_NUM(1); } ").append("\n");
				paging.append("            if ( 1>paramVo.getPAGE_SIZE() ) { paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE); } ").append("\n");
				paging.append("            INT_FROM = (paramVo.getPAGE_NUM() - 1) * paramVo.getPAGE_SIZE(); ").append("\n");
				paging.append("            INT_TO = (paramVo.getPAGE_NUM()) * paramVo.getPAGE_SIZE(); ").append("\n");
				paging.append("            paramVo.setINT_FROM(INT_FROM);").append("\n");
				paging.append("            paramVo.setINT_TO(INT_TO);").append("\n");
			} else if ("MSSQL".equals(dbKind)) {
				paging.append("            if ( 1>paramVo.getPAGE_NUM() ) { paramVo.setPAGE_NUM(1); } ").append("\n");
				paging.append("            if ( 1>paramVo.getPAGE_SIZE() ) { paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE); } ").append("\n");
				paging.append("            INT_FROM = (paramVo.getPAGE_NUM() - 1) * paramVo.getPAGE_SIZE(); ").append("\n");
				paging.append("            INT_TO = (paramVo.getPAGE_NUM()) * paramVo.getPAGE_SIZE(); ").append("\n");
				paging.append("            paramVo.setINT_FROM(INT_FROM);").append("\n");
				paging.append("            paramVo.setINT_TO(INT_TO);").append("\n");
			} else if ("MYSQL".equals(dbKind)) {
				paging.append("            if ( 1>paramVo.getPAGE_NUM() ) { paramVo.setPAGE_NUM(1); } ").append("\n");
				paging.append("            if ( 1>paramVo.getPAGE_SIZE() ) { paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE); } ").append("\n");
				paging.append("            INT_FROM = (paramVo.getPAGE_NUM() - 1) * paramVo.getPAGE_SIZE(); ").append("\n");
				paging.append("            INT_TO = paramVo.getPAGE_SIZE(); ").append("\n");
				paging.append("            paramVo.setINT_FROM(INT_FROM);").append("\n");
				paging.append("            paramVo.setINT_TO(INT_TO);").append("\n");
			}
			
			return paging.toString();
		}
	}

	protected class CtrlGen {
		/**
		 * JSP용 CTRL 소스코드를 생성하는 메소드.
		 * 
		 * @param strCtrlPackageName (CTRL 패키지명. 필수.)
		 * @param strCtrlName (CTRL 명. 필수.)
		 * @param strSvcPackageName (SVC 패키지명. 필수.)
		 * @param strSvcName (SVC 명. 필수.)
		 * @param strMethodName (CTRL 메소드명. 필수.)
		 * @param strMethodComment (CTRL 메소드설명. 필수.)
		 * @param strVoPackageName (VO 패키지명. 필수.)
		 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
		 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
		 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
		 * @param strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value="")에 들어갈 값. 필수.)
		 * @param strUri (메소드 URI. 메소드의 @RequestMapping(value = "")에 들어갈 값. 필수.)
		 * @param strReturnJsp (CTRL 리턴JSP명. ModelAndView.setViewName( strPrefix + "/" + strReturnJsp )로 반환. 필수.)
		 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
		 * @param fileGenYn (CTRL소스를 파일로 생성할지 여부.) 예) (new net.dstone.common.tools.BizGenerator()).new CtrlGen().genCtrlForJsp( "com.test.biz.event", "EventController", "com.test.biz.event", "EventService", "listEvent", "이벤트리스트조회", "com.test.biz.event.vo", "EventListVo", 0, "TB_EVENT", "/jsp/event", "/listEventPlain.do", "listEventPlain", true, false);
		 */
		protected void genCtrlForJsp(String strCtrlPackageName, String strCtrlName, String strSvcPackageName, String strSvcName, String strMethodName, String strMethodComment, String strVoPackageName, String strVoName, int CRUD, String strTableName, String strPrefix, String strUri, String strReturnJsp, boolean pageYn, boolean fileGenYn) {

			debug("||================== genCtrlForJsp(JSP용 CTRL 소스코드를 생성하는 메소드) ==================||");
			debug("strCtrlPackageName (CTRL 패키지명. 필수.) [" + strCtrlPackageName + "]");
			debug("strCtrlName (CTRL 명. 필수.) [" + strCtrlName + "]");
			debug("strSvcPackageName (SVC 패키지명. 필수.) [" + strSvcPackageName + "]");
			debug("strSvcName (SVC 명. 필수.) [" + strSvcName + "]");
			debug("strMethodName (DAO 메소드명. 필수.) [" + strMethodName + "]");
			debug("strMethodComment (DAO 메소드설명. 필수.) [" + strMethodComment + "]");
			debug("strVoPackageName (VO 패키지명. 필수.) [" + strVoPackageName + "]");
			debug("strVoName (VO명. 필수.) [" + strVoName + "]");
			debug("CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.) [" + CRUD + "]");
			debug("strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당) [" + strTableName + "]");
			debug("strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value=)에 들어갈 값. 필수.) [" + strPrefix + "]");
			debug("strUri (메소드 URI. 메소드의 @RequestMapping(value =)에 들어갈 값. 필수.) [" + strUri + "]");
			debug("strReturnJsp (CTRL 리턴JSP명. ModelAndView.setViewName( strPrefix + / + strReturnJsp )로 반환. 필수.) [" + strReturnJsp + "]");
			debug("pageYn (카운트 SQL을 생성할지 여부.) [" + pageYn + "]");
			debug("fileGenYn (SQL 소스를 파일로 생성할지 여부.) [" + fileGenYn + "]");
			debug("||====================================================================================||");

			String PACKAGE_NAME = strCtrlPackageName;
			net.dstone.common.utils.DbUtil db = null;
			net.dstone.common.utils.DataSet ds = null;
			StringBuffer ctrlH = new StringBuffer();
			StringBuffer ctrlConts = new StringBuffer();
			StringBuffer ctrlF = new StringBuffer();

			String ctrlName = "";
			String ctrlFileName = "";
			ctrlName = strCtrlName;

			ctrlFileName = SRC_ROOT + "/" + StringUtil.replace(PACKAGE_NAME + "." + strCtrlName, ".", "/") + ".java";

			if (CRUD == 2 || CRUD == 3 || CRUD == 4) {
				if (!StringUtil.isEmpty(strTableName)) {
					strVoPackageName = COMM_CUD_PACKAGE_NAME + ".vo";
					strVoName = net.dstone.common.utils.StringUtil.getHungarianName(strTableName, " ").trim() + "CudVo";
				}
			}

			String svcAlias = "";
			if (!StringUtil.isEmpty(strSvcName)) {
				svcAlias = strSvcName.substring(0, 1).toLowerCase() + strSvcName.substring(1);
			}

			ctrlH.append("package " + PACKAGE_NAME + "; ").append("\n");
			ctrlH.append(" ").append("\n");
			ctrlH.append("import java.util.ArrayList;").append("\n");
			ctrlH.append("import java.util.HashMap;").append("\n");
			ctrlH.append("import java.util.Map;").append("\n");
			ctrlH.append("import java.util.List;").append("\n");
			ctrlH.append("").append("\n");
			ctrlH.append("import javax.servlet.http.HttpSession;").append("\n");
			ctrlH.append("").append("\n");
			ctrlH.append("import org.springframework.beans.factory.annotation.Autowired;").append("\n");
			ctrlH.append("import org.springframework.stereotype.Controller;").append("\n");
			ctrlH.append("import org.springframework.ui.Model;").append("\n");
			ctrlH.append("import org.springframework.web.servlet.ModelAndView;").append("\n");
			ctrlH.append("import org.springframework.web.bind.annotation.RequestMapping;").append("\n");
			ctrlH.append("").append("\n");
			ctrlH.append("import net.dstone.common.utils.DateUtil;").append("\n");
			ctrlH.append("import net.dstone.common.utils.StringUtil;").append("\n");
			ctrlH.append("import net.dstone.common.utils.BeanUtil;").append("\n");

			ctrlH.append("@Controller").append("\n");
			ctrlH.append("@RequestMapping(value = \"" + strPrefix + "/*\")").append("\n");

			ctrlH.append("public class " + strCtrlName + " extends net.dstone.common.biz.BaseController { ").append("\n");
			ctrlH.append("    ").append("\n");

			if (!FileUtil.isFileExist(ctrlFileName)) {
				ctrlConts.append("    /********* SVC 정의부분 시작 *********/").append("\n");
				ctrlConts.append("    @Autowired ").append("\n");
				ctrlConts.append("    private " + strSvcPackageName + "." + strSvcName + " " + svcAlias + "; ").append("\n");
				ctrlConts.append("    /********* SVC 정의부분 끝 *********/").append("\n");
				ctrlConts.append("    ").append("\n");
			} else {
				if (!net.dstone.common.utils.BeanUtil.isBeanMemberName(PACKAGE_NAME + "." + strCtrlName, svcAlias)) {
					ctrlConts.append("    /********* SVC 정의부분 시작 *********/").append("\n");
					ctrlConts.append("    @Autowired ").append("\n");
					ctrlConts.append("    private " + strSvcPackageName + "." + strSvcName + " " + svcAlias + "; ").append("\n");
					ctrlConts.append("    /********* SVC 정의부분 끝 *********/").append("\n");
					ctrlConts.append("    ").append("\n");
				}
			}

			ctrlConts.append("    /** ").append("\n");
			ctrlConts.append("     * " + strMethodComment + " ").append("\n");
			ctrlConts.append("     * @param request ").append("\n");
			ctrlConts.append("     * @param model ").append("\n");
			ctrlConts.append("     * @return ").append("\n");
			ctrlConts.append("     */ ").append("\n");
			ctrlConts.append("    @RequestMapping(value = \"" + strUri + "\") ").append("\n");
			ctrlConts.append("    public ModelAndView " + strMethodName + "(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, ModelAndView mav) {").append("\n");
			ctrlConts.append("   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.").append("\n");
			ctrlConts.append("   		/************************ 세션체크 시작 ************************/").append("\n");
			ctrlConts.append("   		loginCheck(request, response);").append("\n");
			ctrlConts.append("   		/************************ 세션체크 끝 **************************/").append("\n");
			ctrlConts.append("   		").append("\n");

			// 트랜젝션종류 - 0:다건조회
			if (CRUD == 0) {

				ctrlConts.append("   		/************************ 변수 선언 시작 ************************/").append("\n");
				ctrlConts.append("   		net.dstone.common.utils.RequestUtil 					requestUtil;").append("\n");
				ctrlConts.append("   		Map 											returnObj;").append("\n");
				ctrlConts.append("   		//파라메터").append("\n");
				ctrlConts.append("   		//String										ACTION_MODE;").append("\n");
				ctrlConts.append("   		//파라메터로 사용할 VO").append("\n");
				ctrlConts.append("   		" + strVoPackageName + "." + strVoName + " 					paramVo;").append("\n");
				ctrlConts.append("   		//" + strVoPackageName + "." + strVoName + "[] 				paramList;").append("\n");
				ctrlConts.append("   		/************************ 변수 선언 끝 **************************/").append("\n");
				ctrlConts.append("   		try {").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 시작 ************************/").append("\n");
				ctrlConts.append("   			requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);").append("\n");
				ctrlConts.append("   			paramVo					= null;").append("\n");
				ctrlConts.append("   			//paramList				= null;").append("\n");
				ctrlConts.append("   			returnObj				= null;").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 끝 ************************/").append("\n");
				ctrlConts.append("   			").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 시작 ************************/").append("\n");
				ctrlConts.append("   			// 1. 파라메터 바인딩").append("\n");
				ctrlConts.append("   			// 일반 파라메터 받는경우").append("\n");
				ctrlConts.append("   			//ACTION_MODE				= requestUtil.getParameter(\"ACTION_MODE\", \"LIST_PLAIN\");").append("\n");
				ctrlConts.append("   			// 싱글 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			paramVo 				= (" + strVoPackageName + "." + strVoName + ")bindSingleValue(requestUtil, new " + strVoPackageName + "." + strVoName + "());").append("\n");
				ctrlConts.append("   			// 멀티 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			//paramList 			= (" + strVoPackageName + "." + strVoName + "[])bindMultiValues(requestUtil, \"" + strVoPackageName + "." + strVoName + "\");").append("\n");
				if (pageYn) {
					ctrlConts.append("   			/*** 페이징파라메터 세팅 시작 ***/").append("\n");
					ctrlConts.append("   			if(!net.dstone.common.utils.StringUtil.isEmpty(requestUtil.getParameter(\"PAGE_NUM\", \"\"))){").append("\n");
					ctrlConts.append("   				paramVo.setPAGE_NUM(requestUtil.getIntParameter(\"PAGE_NUM\"));").append("\n");
					ctrlConts.append("   				paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE);").append("\n");
					ctrlConts.append("   			}").append("\n");
					ctrlConts.append("   			/*** 페이징파라메터 세팅 끝 ***/").append("\n");
				}
				ctrlConts.append("   			// 2. 서비스 호출").append("\n");
				ctrlConts.append("   			returnObj 				= " + svcAlias + "." + strMethodName + "(paramVo);").append("\n");
				ctrlConts.append("   			// 3. 결과처리").append("\n");
				ctrlConts.append("   			request.setAttribute(\"RETURN_CD\"	, net.dstone.common.biz.BaseController.RETURN_SUCCESS );").append("\n");
				ctrlConts.append("   			request.setAttribute(\"RETURN_MSG\"	, \"\" );").append("\n");
				ctrlConts.append("   			//request.setAttribute(\"ACTION_MODE\"	, ACTION_MODE	);").append("\n");
				ctrlConts.append("   			request.setAttribute(\"returnObj\"	, returnObj	);").append("\n");
				ctrlConts.append("   			mav.setViewName(\"" + net.dstone.common.tools.BizGenerator.JSP_ROOT_PATH + strPrefix + "/" + strReturnJsp + "\");").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 끝 ************************/").append("\n");

				// 트랜젝션종류 - 1:단건조회
			} else if (CRUD == 1) {

				ctrlConts.append("   		/************************ 변수 선언 시작 ************************/").append("\n");
				ctrlConts.append("   		net.dstone.common.utils.RequestUtil 					requestUtil;").append("\n");
				ctrlConts.append("   		" + strVoPackageName + "." + strVoName + "					returnObj;").append("\n");
				ctrlConts.append("   		//파라메터").append("\n");
				ctrlConts.append("   		//String										ACTION_MODE;").append("\n");
				ctrlConts.append("   		//파라메터로 사용할 VO").append("\n");
				ctrlConts.append("   		" + strVoPackageName + "." + strVoName + " 					paramVo;").append("\n");
				ctrlConts.append("   		//" + strVoPackageName + "." + strVoName + "[] 				paramList;").append("\n");
				ctrlConts.append("   		/************************ 변수 선언 끝 **************************/").append("\n");
				ctrlConts.append("   		try {").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 시작 ************************/").append("\n");
				ctrlConts.append("   			requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);").append("\n");
				ctrlConts.append("   			paramVo					= null;").append("\n");
				ctrlConts.append("   			//paramList				= null;").append("\n");
				ctrlConts.append("   			returnObj				= null;").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 끝 ************************/").append("\n");
				ctrlConts.append("   			").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 시작 ************************/").append("\n");
				ctrlConts.append("   			// 1. 파라메터 바인딩").append("\n");
				ctrlConts.append("   			// 일반 파라메터 받는경우").append("\n");
				ctrlConts.append("   			//ACTION_MODE				= requestUtil.getParameter(\"ACTION_MODE\", \"LIST_PLAIN\");").append("\n");
				ctrlConts.append("   			// 싱글 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			paramVo 				= (" + strVoPackageName + "." + strVoName + ")bindSingleValue(requestUtil, new " + strVoPackageName + "." + strVoName + "());").append("\n");
				ctrlConts.append("   			// 멀티 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			//paramList 			= (" + strVoPackageName + "." + strVoName + "[])bindMultiValues(requestUtil, \"" + strVoPackageName + "." + strVoName + "\");").append("\n");
				ctrlConts.append("   			// 2. 서비스 호출").append("\n");
				ctrlConts.append("   			returnObj 				= " + svcAlias + "." + strMethodName + "(paramVo);").append("\n");
				ctrlConts.append("   			// 3. 결과처리").append("\n");
				ctrlConts.append("   			request.setAttribute(\"RETURN_CD\"	, net.dstone.common.biz.BaseController.RETURN_SUCCESS );").append("\n");
				ctrlConts.append("   			request.setAttribute(\"RETURN_MSG\"	, \"\" );").append("\n");
				ctrlConts.append("   			//request.setAttribute(\"ACTION_MODE\"	, ACTION_MODE	);").append("\n");
				ctrlConts.append("   			request.setAttribute(\"returnObj\"	, returnObj	);").append("\n");
				ctrlConts.append("   			mav.setViewName(\"" + net.dstone.common.tools.BizGenerator.JSP_ROOT_PATH + strPrefix + "/" + strReturnJsp + "\");").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 끝 ************************/").append("\n");

				// 트랜젝션종류 - 2:입력, 3:수정, 4:삭제
			} else if (CRUD == 2 || CRUD == 3 || CRUD == 4) {

				ctrlConts.append("   		/************************ 변수 선언 시작 ************************/").append("\n");
				ctrlConts.append("   		net.dstone.common.utils.RequestUtil 					requestUtil;").append("\n");
				ctrlConts.append("   		//파라메터").append("\n");
				ctrlConts.append("   		//String											ACTION_MODE;").append("\n");
				ctrlConts.append("   		//파라메터로 사용할 VO").append("\n");
				ctrlConts.append("   		" + strVoPackageName + "." + strVoName + " 							paramVo;").append("\n");
				ctrlConts.append("   		//" + strVoPackageName + "." + strVoName + "[] 							paramList;").append("\n");
				ctrlConts.append("   		/************************ 변수 선언 끝 **************************/").append("\n");
				ctrlConts.append("   		try {").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 시작 ************************/").append("\n");
				ctrlConts.append("   			requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);").append("\n");
				ctrlConts.append("   			paramVo					= null;").append("\n");
				ctrlConts.append("   			//paramList				= null;").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 끝 ************************/").append("\n");
				ctrlConts.append("   			").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 시작 ************************/").append("\n");
				ctrlConts.append("   			// 1. 파라메터 바인딩").append("\n");
				ctrlConts.append("   			// 일반 파라메터 받는경우").append("\n");
				ctrlConts.append("   			//ACTION_MODE				= requestUtil.getParameter(\"ACTION_MODE\", \"LIST_PLAIN\");").append("\n");
				ctrlConts.append("   			// 싱글 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			paramVo 				= (" + strVoPackageName + "." + strVoName + ")bindSingleValue(requestUtil, new " + strVoPackageName + "." + strVoName + "());").append("\n");
				ctrlConts.append("   			// 멀티 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			//paramList 			= (" + strVoPackageName + "." + strVoName + "[])bindMultiValues(requestUtil, \"" + strVoPackageName + "." + strVoName + "\");").append("\n");
				ctrlConts.append("   			// 2. 서비스 호출").append("\n");
				ctrlConts.append("   			boolean result 			= " + svcAlias + "." + strMethodName + "(paramVo);").append("\n");
				ctrlConts.append("   			// 3. 결과처리").append("\n");
				ctrlConts.append("   			request.setAttribute(\"RETURN_CD\"	, net.dstone.common.biz.BaseController.RETURN_SUCCESS );").append("\n");
				ctrlConts.append("   			request.setAttribute(\"RETURN_MSG\"	, \"\" );").append("\n");
				ctrlConts.append("   			//request.setAttribute(\"ACTION_MODE\"	, ACTION_MODE	);").append("\n");
				ctrlConts.append("   			request.setAttribute(\"returnObj\"	, new Boolean(result) );").append("\n");
				ctrlConts.append("   			mav.setViewName(\"" + net.dstone.common.tools.BizGenerator.JSP_ROOT_PATH + strPrefix + "/" + strReturnJsp + "\");").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 끝 ************************/").append("\n");

			}
			ctrlConts.append("   		").append("\n");
			ctrlConts.append("   		} catch (Exception e) {").append("\n");
			ctrlConts.append("   			handleException(request, response, e);").append("\n");
			ctrlConts.append("   			request.setAttribute(\"RETURN_CD\"	, net.dstone.common.biz.BaseController.RETURN_FAIL );").append("\n");
			ctrlConts.append("   			request.setAttribute(\"RETURN_MSG\"	, e.toString() );").append("\n");
			ctrlConts.append("   			mav.setViewName(\"" + net.dstone.common.tools.BizGenerator.JSP_ROOT_PATH + strPrefix + "/" + strReturnJsp + "\");").append("\n");
			ctrlConts.append("   		}").append("\n");
			ctrlConts.append("   		return mav;").append("\n");
			ctrlConts.append("    } ").append("\n");

			ctrlF.append("} ").append("\n");

			System.out.println("============================ CTRL START ============================");
			String fileConts = "";

			if (!FileUtil.isFileExist(ctrlFileName)) {
				fileConts = ctrlH.toString() + ctrlConts.toString() + ctrlF.toString();
			} else {
				fileConts = ctrlConts.toString();
			}

			if (fileGenYn) {
				if (!FileUtil.isFileExist(ctrlFileName)) {
					net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(ctrlFileName), FileUtil.getFileName(ctrlFileName), ctrlH.toString() + ctrlF.toString(), CHARSET);
				}
				fileConts = net.dstone.common.utils.FileUtil.readFile(ctrlFileName, CHARSET);
				String fileContsH = fileConts.substring(0, fileConts.lastIndexOf("}"));
				String fileContsF = fileConts.substring(fileConts.lastIndexOf("}"));
				fileConts = fileContsH + "\n" + ctrlConts.toString() + "\n" + fileContsF;
				// System.out.println(fileConts);
				net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(ctrlFileName), FileUtil.getFileName(ctrlFileName), fileConts, CHARSET);
			}

			System.out.println(fileConts);
			System.out.println("============================ CTRL END ============================");

		}

		/**
		 * JSON용 CTRL 소스코드를 생성하는 메소드.
		 * 
		 * @param strCtrlPackageName (CTRL 패키지명. 필수.)
		 * @param strCtrlName (CTRL 명. 필수.)
		 * @param strSvcPackageName (SVC 패키지명. 필수.)
		 * @param strSvcName (SVC 명. 필수.)
		 * @param strMethodName (CTRL 메소드명. 필수.)
		 * @param strMethodComment (CTRL 메소드설명. 필수.)
		 * @param strVoPackageName (VO 패키지명. 필수.)
		 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
		 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
		 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
		 * @param strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value="")에 들어갈 값. 필수.)
		 * @param strUri (메소드 URI. 메소드의 @RequestMapping(value = "")에 들어갈 값. 필수.)
		 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
		 * @param fileGenYn (CTRL소스를 파일로 생성할지 여부.) 예) (new net.dstone.common.tools.BizGenerator()).new CtrlGen().genCtrlForJson( "com.test.biz.event", "EventController", "com.test.biz.event", "EventService", "listEvent", "이벤트리스트조회", "com.test.biz.event.vo", "EventListVo", 0, "TB_EVENT", "/jsp/event", "/listEventPlain.do",true , false);
		 */
		protected void genCtrlForJson(String strCtrlPackageName, String strCtrlName, String strSvcPackageName, String strSvcName, String strMethodName, String strMethodComment, String strVoPackageName, String strVoName, int CRUD, String strTableName, String strPrefix, String strUri, boolean pageYn, boolean fileGenYn) {

			debug("||================ genCtrlForJson(JSON용 CTRL 소스코드를 생성하는 메소드) =================||");
			debug("strCtrlPackageName (CTRL 패키지명. 필수.) [" + strCtrlPackageName + "]");
			debug("strCtrlName (CTRL 명. 필수.) [" + strCtrlName + "]");
			debug("strSvcPackageName (SVC 패키지명. 필수.) [" + strSvcPackageName + "]");
			debug("strSvcName (SVC 명. 필수.) [" + strSvcName + "]");
			debug("strMethodName (DAO 메소드명. 필수.) [" + strMethodName + "]");
			debug("strMethodComment (DAO 메소드설명. 필수.) [" + strMethodComment + "]");
			debug("strVoPackageName (VO 패키지명. 필수.) [" + strVoPackageName + "]");
			debug("strVoName (VO명. 필수.) [" + strVoName + "]");
			debug("CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.) [" + CRUD + "]");
			debug("strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당) [" + strTableName + "]");
			debug("strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value=)에 들어갈 값. 필수.) [" + strPrefix + "]");
			debug("strUri (메소드 URI. 메소드의 @RequestMapping(value =)에 들어갈 값. 필수.) [" + strUri + "]");
			debug("pageYn (카운트 SQL을 생성할지 여부.) [" + pageYn + "]");
			debug("fileGenYn (SQL 소스를 파일로 생성할지 여부.) [" + fileGenYn + "]");
			debug("||====================================================================================||");

			String PACKAGE_NAME = strCtrlPackageName;
			net.dstone.common.utils.DbUtil db = null;
			net.dstone.common.utils.DataSet ds = null;
			StringBuffer ctrlH = new StringBuffer();
			StringBuffer ctrlConts = new StringBuffer();
			StringBuffer ctrlF = new StringBuffer();

			String ctrlName = "";
			String ctrlFileName = "";
			ctrlName = strCtrlName;

			ctrlFileName = SRC_ROOT + "/" + StringUtil.replace(PACKAGE_NAME + "." + strCtrlName, ".", "/") + ".java";

			if (CRUD == 2 || CRUD == 3 || CRUD == 4) {
				if (!StringUtil.isEmpty(strTableName)) {
					strVoPackageName = COMM_CUD_PACKAGE_NAME + ".vo";
					strVoName = net.dstone.common.utils.StringUtil.getHungarianName(strTableName, " ").trim() + "CudVo";
				}
			}

			String svcAlias = "";
			if (!StringUtil.isEmpty(strSvcName)) {
				svcAlias = strSvcName.substring(0, 1).toLowerCase() + strSvcName.substring(1);
			}

			ctrlH.append("package " + PACKAGE_NAME + "; ").append("\n");
			ctrlH.append(" ").append("\n");
			ctrlH.append("import java.util.ArrayList;").append("\n");
			ctrlH.append("import java.util.HashMap;").append("\n");
			ctrlH.append("import java.util.Map;").append("\n");
			ctrlH.append("import java.util.List;").append("\n");
			ctrlH.append("").append("\n");
			ctrlH.append("import javax.servlet.http.HttpSession;").append("\n");
			ctrlH.append("").append("\n");
			ctrlH.append("import org.springframework.beans.factory.annotation.Autowired;").append("\n");
			ctrlH.append("import org.springframework.stereotype.Controller;").append("\n");
			ctrlH.append("import org.springframework.ui.Model;").append("\n");
			ctrlH.append("import org.springframework.web.servlet.ModelAndView;").append("\n");
			ctrlH.append("import org.springframework.web.bind.annotation.RequestMapping;").append("\n");
			ctrlH.append("").append("\n");
			ctrlH.append("import net.dstone.common.utils.DateUtil;").append("\n");
			ctrlH.append("import net.dstone.common.utils.StringUtil;").append("\n");
			ctrlH.append("import net.dstone.common.utils.BeanUtil;").append("\n");

			ctrlH.append("@Controller").append("\n");
			ctrlH.append("@RequestMapping(value = \"" + strPrefix + "/*\")").append("\n");

			ctrlH.append("public class " + strCtrlName + " extends net.dstone.common.biz.BaseController { ").append("\n");
			ctrlH.append("    ").append("\n");

			if (!FileUtil.isFileExist(ctrlFileName)) {
				ctrlConts.append("    /********* SVC 정의부분 시작 *********/").append("\n");
				ctrlConts.append("    @Autowired ").append("\n");
				ctrlConts.append("    private " + strSvcPackageName + "." + strSvcName + " " + svcAlias + "; ").append("\n");
				ctrlConts.append("    /********* SVC 정의부분 끝 *********/").append("\n");
				ctrlConts.append("    ").append("\n");
			} else {
				if (!net.dstone.common.utils.BeanUtil.isBeanMemberName(PACKAGE_NAME + "." + strCtrlName, svcAlias)) {
					ctrlConts.append("    /********* SVC 정의부분 시작 *********/").append("\n");
					ctrlConts.append("    @Autowired ").append("\n");
					ctrlConts.append("    private " + strSvcPackageName + "." + strSvcName + " " + svcAlias + "; ").append("\n");
					ctrlConts.append("    /********* SVC 정의부분 끝 *********/").append("\n");
					ctrlConts.append("    ").append("\n");
				}
			}

			ctrlConts.append("    /** ").append("\n");
			ctrlConts.append("     * " + strMethodComment + " ").append("\n");
			ctrlConts.append("     * @param request ").append("\n");
			ctrlConts.append("     * @param model ").append("\n");
			ctrlConts.append("     * @return ").append("\n");
			ctrlConts.append("     */ ").append("\n");
			ctrlConts.append("    @RequestMapping(value = \"" + strUri + "\") ").append("\n");
			ctrlConts.append("    public ModelAndView " + strMethodName + "(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, ModelAndView mav) {").append("\n");
			ctrlConts.append("   		// 필요없는 주석들은 제거하시고 사용하시면 됩니다.").append("\n");
			ctrlConts.append("   		/************************ 세션체크 시작 ************************/").append("\n");
			ctrlConts.append("   		loginCheck(request, response);").append("\n");
			ctrlConts.append("   		/************************ 세션체크 끝 **************************/").append("\n");
			ctrlConts.append("   		").append("\n");

			// 트랜젝션종류 - 0:다건조회
			if (CRUD == 0) {

				ctrlConts.append("   		/************************ 변수 선언 시작 ************************/").append("\n");
				ctrlConts.append("   		net.dstone.common.utils.RequestUtil 					requestUtil;").append("\n");
				ctrlConts.append("   		Map 											returnObj;").append("\n");
				ctrlConts.append("   		//파라메터").append("\n");
				ctrlConts.append("   		//String										ACTION_MODE;").append("\n");
				ctrlConts.append("   		//파라메터로 사용할 VO").append("\n");
				ctrlConts.append("   		" + strVoPackageName + "." + strVoName + " 					paramVo;").append("\n");
				ctrlConts.append("   		//" + strVoPackageName + "." + strVoName + "[] 				paramList;").append("\n");
				ctrlConts.append("   		/************************ 변수 선언 끝 **************************/").append("\n");
				ctrlConts.append("   		try {").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 시작 ************************/").append("\n");
				ctrlConts.append("   			requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);").append("\n");
				ctrlConts.append("   			paramVo					= null;").append("\n");
				ctrlConts.append("   			//paramList				= null;").append("\n");
				ctrlConts.append("   			returnObj				= null;").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 끝 ************************/").append("\n");
				ctrlConts.append("   			").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 시작 ************************/").append("\n");
				ctrlConts.append("   			// 1. 파라메터 바인딩").append("\n");
				ctrlConts.append("   			// 일반 파라메터 받는경우").append("\n");
				ctrlConts.append("   			//ACTION_MODE				= requestUtil.getJsonParameterValue(\"ACTION_MODE\");").append("\n");
				ctrlConts.append("   			// 싱글 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			paramVo 				= (" + strVoPackageName + "." + strVoName + ")bindSingleJsonObj(requestUtil, new " + strVoPackageName + "." + strVoName + "());").append("\n");
				ctrlConts.append("   			// 멀티 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			//paramList 			= (" + strVoPackageName + "." + strVoName + "[])bindMultiJsonObjs(requestUtil, \"" + strVoPackageName + "." + strVoName + "\");").append("\n");
				if (pageYn) {
					ctrlConts.append("   			/*** 페이징파라메터 세팅 시작 ***/").append("\n");
					ctrlConts.append("   			if(!net.dstone.common.utils.StringUtil.isEmpty(requestUtil.getJsonParameterValue(\"PAGE_NUM\"))){").append("\n");
					ctrlConts.append("   				paramVo.setPAGE_NUM(Integer.parseInt(requestUtil.getJsonParameterValue(\"PAGE_NUM\")));").append("\n");
					ctrlConts.append("   				paramVo.setPAGE_SIZE(net.dstone.common.utils.PageUtil.DEFAULT_PAGE_SIZE);").append("\n");
					ctrlConts.append("   			}").append("\n");
					ctrlConts.append("   			/*** 페이징파라메터 세팅 끝 ***/").append("\n");
				}
				ctrlConts.append("   			// 2. 서비스 호출").append("\n");
				ctrlConts.append("   			returnObj 				= " + svcAlias + "." + strMethodName + "(paramVo);").append("\n");
				ctrlConts.append("   			// 3. 결과처리").append("\n");
				ctrlConts.append("   			mav.addObject(\"RETURN_CD\", net.dstone.common.biz.BaseController.RETURN_SUCCESS );").append("\n");
				ctrlConts.append("   			mav.addObject(\"returnObj\", returnObj	);").append("\n");
				if (pageYn) {
					ctrlConts.append("   			/*** 페이징객체 세팅 시작 ***/").append("\n");
					ctrlConts.append("   			mav.addObject(\"pageHTML\", ((net.dstone.common.utils.PageUtil) returnObj.get(\"pageUtil\")).htmlPostPage(request, \"MAIN_FORM\", \"PAGE_NUM\", \"goPage\")	);").append("\n");
					ctrlConts.append("   			/*** 페이징객체 세팅 끝 ***/").append("\n");
				}
				ctrlConts.append("   			mav.setViewName(\"jsonView\");").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 끝 ************************/").append("\n");

				// 트랜젝션종류 - 1:단건조회
			} else if (CRUD == 1) {

				ctrlConts.append("   		/************************ 변수 선언 시작 ************************/").append("\n");
				ctrlConts.append("   		net.dstone.common.utils.RequestUtil 					requestUtil;").append("\n");
				ctrlConts.append("   		" + strVoPackageName + "." + strVoName + "					returnObj;").append("\n");
				ctrlConts.append("   		//파라메터").append("\n");
				ctrlConts.append("   		//String										ACTION_MODE;").append("\n");
				ctrlConts.append("   		//파라메터로 사용할 VO").append("\n");
				ctrlConts.append("   		" + strVoPackageName + "." + strVoName + " 					paramVo;").append("\n");
				ctrlConts.append("   		//" + strVoPackageName + "." + strVoName + "[] 				paramList;").append("\n");
				ctrlConts.append("   		/************************ 변수 선언 끝 **************************/").append("\n");
				ctrlConts.append("   		try {").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 시작 ************************/").append("\n");
				ctrlConts.append("   			requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);").append("\n");
				ctrlConts.append("   			paramVo					= null;").append("\n");
				ctrlConts.append("   			//paramList				= null;").append("\n");
				ctrlConts.append("   			returnObj				= null;").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 끝 ************************/").append("\n");
				ctrlConts.append("   			").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 시작 ************************/").append("\n");
				ctrlConts.append("   			// 1. 파라메터 바인딩").append("\n");
				ctrlConts.append("   			// 일반 파라메터 받는경우").append("\n");
				ctrlConts.append("   			//ACTION_MODE				= requestUtil.getJsonParameterValue(\"ACTION_MODE\");").append("\n");
				ctrlConts.append("   			// 싱글 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			paramVo 				= (" + strVoPackageName + "." + strVoName + ")bindSingleJsonObj(requestUtil, new " + strVoPackageName + "." + strVoName + "());").append("\n");
				ctrlConts.append("   			// 멀티 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			//paramList 			= (" + strVoPackageName + "." + strVoName + "[])bindMultiJsonObjs(requestUtil, \"" + strVoPackageName + "." + strVoName + "\");").append("\n");
				ctrlConts.append("   			// 2. 서비스 호출").append("\n");
				ctrlConts.append("   			returnObj 				= " + svcAlias + "." + strMethodName + "(paramVo);").append("\n");
				ctrlConts.append("   			// 3. 결과처리").append("\n");
				ctrlConts.append("   			mav.addObject(\"RETURN_CD\", net.dstone.common.biz.BaseController.RETURN_SUCCESS );").append("\n");
				ctrlConts.append("   			mav.addObject(\"returnObj\", returnObj	);").append("\n");
				ctrlConts.append("   			mav.setViewName(\"jsonView\");").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 끝 ************************/").append("\n");

				// 트랜젝션종류 - 2:입력, 3:수정, 4:삭제
			} else if (CRUD == 2 || CRUD == 3 || CRUD == 4) {

				ctrlConts.append("   		/************************ 변수 선언 시작 ************************/").append("\n");
				ctrlConts.append("   		net.dstone.common.utils.RequestUtil 					requestUtil;").append("\n");
				ctrlConts.append("   		//파라메터").append("\n");
				ctrlConts.append("   		//String										ACTION_MODE;").append("\n");
				ctrlConts.append("   		//파라메터로 사용할 VO").append("\n");
				ctrlConts.append("   		" + strVoPackageName + "." + strVoName + " 				paramVo;").append("\n");
				ctrlConts.append("   		//" + strVoPackageName + "." + strVoName + "[] 			paramList;").append("\n");
				ctrlConts.append("   		/************************ 변수 선언 끝 **************************/").append("\n");
				ctrlConts.append("   		try {").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 시작 ************************/").append("\n");
				ctrlConts.append("   			requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);").append("\n");
				ctrlConts.append("   			paramVo					= null;").append("\n");
				ctrlConts.append("   			//paramList				= null;").append("\n");
				ctrlConts.append("   			/************************ 변수 정의 끝 ************************/").append("\n");
				ctrlConts.append("   			").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 시작 ************************/").append("\n");
				ctrlConts.append("   			// 1. 파라메터 바인딩").append("\n");
				ctrlConts.append("   			// 일반 파라메터 받는경우").append("\n");
				ctrlConts.append("   			//ACTION_MODE			= requestUtil.getJsonParameterValue(\"ACTION_MODE\");").append("\n");
				ctrlConts.append("   			// 싱글 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			paramVo 				= (" + strVoPackageName + "." + strVoName + ")bindSingleJsonObj(requestUtil, new " + strVoPackageName + "." + strVoName + "());").append("\n");
				ctrlConts.append("   			// 멀티 VALUE 맵핑일 경우").append("\n");
				ctrlConts.append("   			//paramList 			= (" + strVoPackageName + "." + strVoName + "[])bindMultiJsonObjs(requestUtil, \"" + strVoPackageName + "." + strVoName + "\");").append("\n");
				ctrlConts.append("   			// 2. 서비스 호출").append("\n");
				ctrlConts.append("   			boolean result 			= " + svcAlias + "." + strMethodName + "(paramVo);").append("\n");
				ctrlConts.append("   			// 3. 결과처리").append("\n");
				ctrlConts.append("   			mav.addObject(\"RETURN_CD\", net.dstone.common.biz.BaseController.RETURN_SUCCESS );").append("\n");
				ctrlConts.append("   			mav.addObject(\"returnObj\", new Boolean(result)	);").append("\n");
				ctrlConts.append("   			mav.setViewName(\"jsonView\");").append("\n");
				ctrlConts.append("   			/************************ 컨트롤러 로직 끝 ************************/").append("\n");

			}

			ctrlConts.append("   		").append("\n");
			ctrlConts.append("   		} catch (Exception e) {").append("\n");
			ctrlConts.append("   			handleException(request, response, e);").append("\n");
			ctrlConts.append("   			mav.addObject(\"RETURN_CD\", net.dstone.common.biz.BaseController.RETURN_FAIL );").append("\n");
			ctrlConts.append("   		}").append("\n");
			ctrlConts.append("   		return mav;").append("\n");
			ctrlConts.append("    } ").append("\n");

			ctrlF.append("} ").append("\n");

			System.out.println("============================ CTRL START ============================");
			String fileConts = "";

			if (!FileUtil.isFileExist(ctrlFileName)) {
				fileConts = ctrlH.toString() + ctrlConts.toString() + ctrlF.toString();
			} else {
				fileConts = ctrlConts.toString();
			}

			if (fileGenYn) {
				if (!FileUtil.isFileExist(ctrlFileName)) {
					net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(ctrlFileName), FileUtil.getFileName(ctrlFileName), ctrlH.toString() + ctrlF.toString(), CHARSET);
				}
				fileConts = net.dstone.common.utils.FileUtil.readFile(ctrlFileName, CHARSET);
				String fileContsH = fileConts.substring(0, fileConts.lastIndexOf("}"));
				String fileContsF = fileConts.substring(fileConts.lastIndexOf("}"));
				fileConts = fileContsH + "\n" + ctrlConts.toString() + "\n" + fileContsF;
				// System.out.println(fileConts);
				net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(ctrlFileName), FileUtil.getFileName(ctrlFileName), fileConts, CHARSET);
			}

			System.out.println(fileConts);
			System.out.println("============================ CTRL END ============================");

		}
	}

	protected class WsGen {

		/**
		 * RestFul Web Service 용 WS 소스코드를 생성하는 메소드.
		 * 
		 * @param strWSPackageName (WS 패키지명. 필수.)
		 * @param strWSName (WS 명. 필수.)
		 * @param strSvcPackageName (SVC 패키지명. 필수.)
		 * @param strSvcName (SVC 명. 필수.)
		 * @param strWSPath (WS Path. 필수.)
		 * @param strMethodKind (메소드종류:PUT/POST/DELETE. 필수.)
		 * @param strMethodName (메소드명. 필수.)
		 * @param strMethodComment (메소드설명. 필수.)
		 * @param strWsVoPackageName (웹서비스VO 패키지명-파라메터. 필수.)
		 * @param strWsVoName (웹서비스VO 명-파라메터. 필수.)
		 * @param strVoPackageName (VO 패키지명-파라메터. 필수.)
		 * @param strVoName (VO 명-파라메터. 필수.)
		 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
		 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
		 * @param strMediaType (미디어타입:XML/JSON). 필수.)
		 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
		 * @param fileGenYn (WS소스를 파일로 생성할지 여부.) 예) (new net.dstone.common.tools.BizGenerator()).new WsGen().genWsForRestFul( "com.test.biz.event.ws", "WsEventService", "com.test.biz.event", "EventService", "/Event", "PUT", "listEvent", "이벤트리스트조회", "com.test.biz.event.vo", "EventListVo", "com.test.biz.event.ws", "EventListWsVo", 0, "TB_EVENT", "XML", true, false);
		 */
		protected void genWsForRestFul(String strWSPackageName, String strWSName, String strSvcPackageName, String strSvcName, String strWSPath, String strMethodKind, String strMethodName, String strMethodComment, String strWsVoPackageName, String strWsVoName, String strVoPackageName, String strVoName, int CRUD, String strTableName, String strMediaType, boolean pageYn, boolean fileGenYn) {

			debug("||========= genWsForRestFul(RestFul Web Service 용 WS 소스코드를 생성하는 메소드) =========||");
			debug("strWSPackageName (WS 패키지명. 필수.) [" + strWSPackageName + "]");
			debug("strWSName (WS 명. 필수.) [" + strWSName + "]");
			debug("strSvcPackageName (SVC 패키지명. 필수.) [" + strSvcPackageName + "]");
			debug("strSvcName (SVC 명. 필수.) [" + strSvcName + "]");
			debug("strWSPath (WS Path. 필수.) [" + strWSPath + "]");
			debug("strMethodKind (메소드종류:PUT/POST/DELETE. 필수.) [" + strMethodKind + "]");
			debug("strMethodName (메소드명. 필수.) [" + strMethodName + "]");
			debug("strMethodComment (메소드설명. 필수.) [" + strMethodComment + "]");
			debug("strWsVoPackageName (웹서비스VO 패키지명-파라메터. 필수.) [" + strWsVoPackageName + "]");
			debug("strWsVoName (웹서비스VO 명-파라메터. 필수.) [" + strWsVoName + "]");
			debug("strVoPackageName (VO 패키지명-파라메터. 필수.) [" + strVoPackageName + "]");
			debug("strVoName (VO 명-파라메터. 필수.) [" + strVoName + "]");
			debug("CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.) [" + CRUD + "]");
			debug("strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당) [" + strTableName + "]");
			debug("strMediaType (미디어타입:XML/JSON). 필수.) [" + strMediaType + "]");
			debug("pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.) [" + pageYn + "]");
			debug("fileGenYn (WS소스를 파일로 생성할지 여부.) [" + fileGenYn + "]");
			debug("||====================================================================================||");

			String wsFileName = "";

			StringBuffer svcH = new StringBuffer();
			StringBuffer svcConts = new StringBuffer();
			StringBuffer svcF = new StringBuffer();

			String wsVoAlias = "";
			String svcAlias = "";

			try {

				wsFileName = SRC_ROOT + "/" + StringUtil.replace(strWSPackageName + "." + strWSName, ".", "/") + ".java";
				wsVoAlias = strWsVoName.substring(0, 1).toLowerCase() + strWsVoName.substring(1);

				if (!StringUtil.isEmpty(strSvcName)) {
					svcAlias = strSvcName.substring(0, 1).toLowerCase() + strSvcName.substring(1);
				}

				genWsVo(strWsVoPackageName, strWsVoName, strVoPackageName, strVoName, strMethodName, CRUD, strTableName, strMediaType, fileGenYn);

				svcH.append("package " + strWSPackageName + ";").append("\r\n");
				svcH.append(" ").append("\r\n");
				svcH.append("import java.util.HashMap; ").append("\r\n");
				svcH.append("import java.util.List; ").append("\r\n");
				svcH.append("import java.util.Map; ").append("\r\n");
				svcH.append(" ").append("\r\n");
				svcH.append("import javax.ws.rs.core.*; ").append("\r\n");
				svcH.append("import javax.ws.rs.*; ").append("\r\n");
				svcH.append(" ").append("\r\n");
				svcH.append("import org.springframework.beans.factory.annotation.Autowired; ").append("\r\n");
				svcH.append("import org.springframework.stereotype.Component; ").append("\r\n");
				svcH.append(" ").append("\r\n");
				svcH.append("import net.dstone.common.utils.LogUtil; ").append("\n");
				svcH.append(" ").append("\r\n");
				svcH.append("@Component ").append("\r\n");
				svcH.append("@Path(\"" + strWSPath + "\") ").append("\r\n");
				svcH.append("public class " + strWSName + " { ").append("\r\n");
				svcH.append("  ").append("\r\n");
				svcH.append("    /**************************************************************************************************************  ").append("\r\n");
				svcH.append("     * <개요> ").append("\r\n");
				svcH.append("     * " + strWSName + " 는 " + WS_WEB_URL + WEB_CONTEXT_ROOT + "" + WS_ROOT_PATH + "" + strWSPath + "/... 로 호출되는 요청에대한 대응서비스.    ").append("\r\n");
				svcH.append("     * 호출구조는 WEB-INF/web.xml 에 기술된 url-pattern(" + WS_ROOT_PATH + ") + 서비스패스(" + strWSPath + ") + 메소드패스 로 구성된다.          ").append("\r\n");
				svcH.append("     * PUT/POST/DELETE 의 경우 VO형식으로 통신하되 내부적으로 원하는 미디어 타입으로 전환해준다.  ").append("\r\n");
				svcH.append("     * 단, VO는 반드시 @XmlRootElement( name=\"" + wsVoAlias + "\" )를 Annotation 으로 선언해주어야 한다. ").append("\r\n");
				svcH.append("     * 예)  ").append("\r\n");
				svcH.append("     * package " + strVoPackageName + ";").append("\r\n");
				svcH.append("     * @XmlRootElement( name=\"" + wsVoAlias + "\" ) ").append("\r\n");
				svcH.append("     * public class " + strVoName + " implements java.io.Serializable {").append("\r\n");
				svcH.append("     *     private String  paramName;").append("\r\n");
				svcH.append("     *     private java.util.List<com.test.biz.sample.vo.SampleVo> returnList;").append("\r\n");
				svcH.append("     *     // 멤버들에 대한 getter/setter 메소드").append("\r\n");
				svcH.append("     *     .....").append("\r\n");
				svcH.append("     * }  ").append("\r\n");
				svcH.append("     * <참고사이트> ").append("\r\n");
				svcH.append("     * http://bcho.tistory.com/739  ").append("\r\n");
				svcH.append("     * <테스트 툴> ").append("\r\n");
				svcH.append("     * http://www.ywebb.com/http4e/install/ 로 HTTP4e플러그인 설치 후 개발. ").append("\r\n");
				svcH.append("    **************************************************************************************************************/ ").append("\r\n");
				svcH.append("      ").append("\r\n");
				svcH.append("    LogUtil logger = getLogger();  ").append("\r\n");
				svcH.append("     ").append("\r\n");

				if (!FileUtil.isFileExist(wsFileName)) {
					svcH.append("    /********* SVC 정의부분 시작 *********/").append("\n");
					svcH.append("    @Autowired ").append("\n");
					svcH.append("    private " + strSvcPackageName + "." + strSvcName + " " + svcAlias + "; ").append("\n");
					svcH.append("    /********* SVC 정의부분 끝 *********/").append("\n");
					svcH.append("    ").append("\n");
				} else {
					if (!net.dstone.common.utils.BeanUtil.isBeanMemberName(strWSPackageName + "." + strWSName, svcAlias)) {
						svcH.append("    /********* SVC 정의부분 시작 *********/").append("\n");
						svcH.append("    @Autowired ").append("\n");
						svcH.append("    private " + strSvcPackageName + "." + strSvcName + " " + svcAlias + "; ").append("\n");
						svcH.append("    /********* SVC 정의부분 끝 *********/").append("\n");
						svcH.append("    ").append("\n");
					}
				}

				svcH.append("    /**************************** GET으로 호출하는 샘플(필요시 응용하기 바람) 시작  ****************************").append("\r\n");
				svcH.append("     * GET으로 호출하는 샘플.(필요시 응용하기 바람) ").append("\r\n");
				svcH.append("     * 예) " + WS_WEB_URL + WEB_CONTEXT_ROOT + "" + WS_ROOT_PATH + "" + strWSPath + "/select/jysn007@hanmail.net ").append("\r\n");
				svcH.append("     * <반환타입> ").append("\r\n");
				svcH.append("     * XML 타입으로 반환할 경우  ").append("\r\n");
				svcH.append("     * return javax.ws.rs.core.Response.status(200).entity(sampleVo).type(MediaType.APPLICATION_XML).build(); ").append("\r\n");
				svcH.append("     * JSON 타입으로 반환할 경우  ").append("\r\n");
				svcH.append("     * return javax.ws.rs.core.Response.status(200).entity(sampleVo).type(MediaType.APPLICATION_JSON).build(); ").append("\r\n");
				svcH.append("    @GET ").append("\r\n");
				svcH.append("    @Path(\"/select/{EMAIL}\") ").append("\r\n");
				svcH.append("    public javax.ws.rs.core.Response getWsSample(@PathParam(\"EMAIL\") String EMAIL){ ").append("\r\n");
				svcH.append("        System.out.println(\"EMAIL ====>>>[\"+EMAIL+\"]\"); ").append("\r\n");
				svcH.append("        // 변수 선언 시작").append("\r\n");
				svcH.append("        com.test.biz.sample.vo.SampleVo     sampleVo = null; ").append("\r\n");
				svcH.append("        // 변수 선언 끝 ").append("\r\n");
				svcH.append("        try {  ").append("\r\n");
				svcH.append("            // 변수 정의 시작").append("\r\n");
				svcH.append("            sampleVo = new com.test.biz.sample.vo.SampleVo(); ").append("\r\n");
				svcH.append("            // 변수 정의 끝").append("\r\n");
				svcH.append("            ").append("\r\n");
				svcH.append("            // 비즈니스로직 시작").append("\r\n");
				svcH.append("            sampleVo = sampleService.doSomething(EMAIL); ").append("\r\n");
				svcH.append("            // 비즈니스로직 끝").append("\r\n");
				svcH.append("        } catch (Exception e) {  ").append("\r\n");
				svcH.append("            e.printStackTrace(); ").append("\r\n");
				svcH.append("            logger.error(this.getClass().getName() + \".getWsSample 수행중 예외발생. 상세사항:\" + e.toString());  ").append("\r\n");
				svcH.append("        } ").append("\r\n");
				svcH.append("        return javax.ws.rs.core.Response.status(200).entity(sampleVo).type(MediaType.APPLICATION_XML).build(); ").append("\r\n");
				svcH.append("    } ").append("\r\n");
				svcH.append("    **************************** GET으로 호출하는 샘플(필요시 응용하기 바람) 끝  ****************************/").append("\r\n");
				svcH.append(" ").append("\r\n");

				svcConts.append("    /** ").append("\r\n");
				svcConts.append("     * " + strMethodComment + " ").append("\r\n");
				svcConts.append("     * @param paramVo ").append("\r\n");
				svcConts.append("     * @return javax.ws.rs.core.Response").append("\r\n");
				svcConts.append("     * 호출예)").append("\r\n");
				svcConts.append("     * " + WS_WEB_URL + WEB_CONTEXT_ROOT + "" + WS_ROOT_PATH + "" + strWSPath + "/" + strMethodName + "").append("\r\n");
				svcConts.append("     */ ").append("\r\n");
				svcConts.append("    @" + strMethodKind + " ").append("\r\n");
				svcConts.append("    @Path(\"/" + strMethodName + "\") ").append("\r\n");
				svcConts.append("    public javax.ws.rs.core.Response " + strMethodName + "(" + strWsVoPackageName + "." + strWsVoName + " wsVo){ ").append("\r\n");

				// 트랜젝션종류 - 0:다건조회
				if (CRUD == 0) {

					svcConts.append("        /************************ 변수 선언 시작 ************************/  ").append("\r\n");
					svcConts.append("        " + strVoPackageName + "." + strVoName + " paramVo = null; ").append("\r\n");
					svcConts.append("        /************************ 변수 선언 끝 **************************/  ").append("\r\n");
					svcConts.append("        try { ").append("\r\n");
					svcConts.append("            /************************ 변수 정의 시작 ************************/  ").append("\r\n");
					svcConts.append("            paramVo = wsVo.get" + strVoName + "(); ").append("\r\n");
					svcConts.append("            /************************ 변수 정의 끝 **************************/  ").append("\r\n");
					svcConts.append("             ").append("\r\n");
					svcConts.append("            /************************ 비즈니스로직 시작 ************************/  ").append("\r\n");
					svcConts.append("            // 1. 파라메터세팅 ").append("\r\n");
					svcConts.append("            //paramVo.setPAGE_NUM(1); ").append("\r\n");
					svcConts.append("            //paramVo.setPAGE_SIZE(10); ").append("\r\n");
					svcConts.append("            // 2. 비즈니스 서비스 호출 ").append("\r\n");
					svcConts.append("            wsVo.set" + strVoName + "List( (List<" + strVoPackageName + "." + strVoName + ">)" + svcAlias + "." + strMethodName + "(paramVo).get(\"returnObj\")); ").append("\r\n");

					// 트랜젝션종류 - 1:단건조회
				} else if (CRUD == 1) {

					svcConts.append("        /************************ 변수 선언 시작 ************************/  ").append("\r\n");
					svcConts.append("        " + strVoPackageName + "." + strVoName + " paramVo = null; ").append("\r\n");
					svcConts.append("        /************************ 변수 선언 끝 **************************/  ").append("\r\n");
					svcConts.append("        try { ").append("\r\n");
					svcConts.append("            /************************ 변수 정의 시작 ************************/  ").append("\r\n");
					svcConts.append("            paramVo = wsVo.get" + strVoName + "(); ").append("\r\n");
					svcConts.append("            /************************ 변수 정의 끝 **************************/  ").append("\r\n");
					svcConts.append("             ").append("\r\n");
					svcConts.append("            /************************ 비즈니스로직 시작 ************************/  ").append("\r\n");
					svcConts.append("            // 1. 파라메터세팅 ").append("\r\n");
					svcConts.append("            // 2. 비즈니스 서비스 호출 ").append("\r\n");
					svcConts.append("            wsVo.set" + strVoName + "( " + svcAlias + "." + strMethodName + "(paramVo) ); ").append("\r\n");

					// 트랜젝션종류 - 2:입력, 3:수정, 4:삭제
				} else if (CRUD == 2 || CRUD == 3 || CRUD == 4) {

					svcConts.append("        /************************ 변수 선언 시작 ************************/  ").append("\r\n");
					svcConts.append("        " + strVoPackageName + "." + strVoName + " paramVo = null; ").append("\r\n");
					svcConts.append("        /************************ 변수 선언 끝 **************************/  ").append("\r\n");
					svcConts.append("        try { ").append("\r\n");
					svcConts.append("            /************************ 변수 정의 시작 ************************/  ").append("\r\n");
					svcConts.append("            paramVo = wsVo.get" + strVoName + "(); ").append("\r\n");
					svcConts.append("            /************************ 변수 정의 끝 **************************/  ").append("\r\n");
					svcConts.append("             ").append("\r\n");
					svcConts.append("            /************************ 비즈니스로직 시작 ************************/  ").append("\r\n");
					svcConts.append("            // 1. 파라메터세팅 ").append("\r\n");
					svcConts.append("            // 2. 비즈니스 서비스 호출 ").append("\r\n");
					svcConts.append("            wsVo.setCudFlag( " + svcAlias + "." + strMethodName + "(paramVo) ); ").append("\r\n");
				}

				svcConts.append("            /************************ 비즈니스로직 끝 **************************/  ").append("\r\n");
				svcConts.append("        } catch (Exception e) {  ").append("\r\n");
				svcConts.append("            logger.error(this.getClass().getName() + \"." + strMethodName + " 수행중 예외발생. 상세사항:\" + e.toString());  ").append("\r\n");
				svcConts.append("        } ").append("\r\n");
				if ("JSON".equals(strMediaType)) {
					svcConts.append("        return javax.ws.rs.core.Response.status(200).entity(wsVo).type(MediaType.APPLICATION_JSON).build(); ").append("\r\n");
				} else {
					svcConts.append("        return javax.ws.rs.core.Response.status(200).entity(wsVo).type(MediaType.APPLICATION_XML).build(); ").append("\r\n");
				}
				svcConts.append("    } ").append("\r\n");

				svcF.append("} ").append("\n");

				System.out.println("\r\n============================ WS Method SRC START ============================");

				String fileConts = "";

				if (!FileUtil.isFileExist(wsFileName)) {
					fileConts = svcH.toString() + svcConts.toString() + svcF.toString();
				} else {
					fileConts = svcConts.toString();
				}

				if (fileGenYn) {
					if (!FileUtil.isFileExist(wsFileName)) {
						net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(wsFileName), FileUtil.getFileName(wsFileName), svcH.toString() + svcF.toString(), CHARSET);
					}
					fileConts = net.dstone.common.utils.FileUtil.readFile(wsFileName, CHARSET);
					String fileContsH = fileConts.substring(0, fileConts.lastIndexOf("}"));
					String fileContsF = fileConts.substring(fileConts.lastIndexOf("}"));
					fileConts = fileContsH + "\n" + svcConts.toString() + "\n" + fileContsF;
					// System.out.println(fileConts);
					net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(wsFileName), FileUtil.getFileName(wsFileName), fileConts, CHARSET);
				}
				System.out.println(fileConts);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("============================ WS Method SRC END ============================\r\n");

		}

		/**
		 * RestFul Web Service 용 VO코드를 생성하는 메소드.
		 * 
		 * @param strWsVoPackageName (웹서비스VO 패키지명-파라메터. 필수.)
		 * @param strWsVoName (웹서비스VO 명-파라메터. 필수.)
		 * @param strVoPackageName (VO 패키지명-파라메터. 필수.)
		 * @param strVoName (VO 명-파라메터. 필수.)
		 * @param strMethodComment (메소드설명. 필수.)
		 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
		 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
		 * @param strMediaType (미디어타입:XML/JSON). 필수.)
		 * @param fileGenYn (WS소스를 파일로 생성할지 여부.)
		 */
		private String genWsVo(String strWsVoPackageName, String strWsVoName, String strVoPackageName, String strVoName, String strMethodComment, int CRUD, String strTableName, String strMediaType, boolean fileGenYn) {

			debug("||================= genWsVo(RestFul Web Service 용 VO코드를 생성하는 메소드) ===============||");
			debug("strWsVoPackageName (웹서비스VO 패키지명-파라메터. 필수.) [" + strWsVoPackageName + "]");
			debug("strWsVoName (웹서비스VO 명-파라메터. 필수.) [" + strWsVoName + "]");
			debug("strVoPackageName (VO 패키지명-파라메터. 필수.) [" + strVoPackageName + "]");
			debug("strVoName (VO 명-파라메터. 필수.) [" + strVoName + "]");
			debug("strMethodComment (메소드설명. 필수.) [" + strMethodComment + "]");
			debug("CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.) [" + CRUD + "]");
			debug("strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당) [" + strTableName + "]");
			debug("strMediaType (미디어타입:XML/JSON). 필수.) [" + strMediaType + "]");
			debug("fileGenYn (WS소스를 파일로 생성할지 여부.) [" + fileGenYn + "]");
			debug("||====================================================================================||");

			String wsVoFileName = "";

			String wsVoAlias = "";
			String voAlias = "";

			StringBuffer voH = new StringBuffer();
			StringBuffer voConts = new StringBuffer();
			StringBuffer voF = new StringBuffer();

			wsVoFileName = SRC_ROOT + "/" + StringUtil.replace(strWsVoPackageName + "." + strWsVoName, ".", "/") + ".java";

			wsVoAlias = strWsVoName.substring(0, 1).toLowerCase() + strWsVoName.substring(1);
			voAlias = strVoName.substring(0, 1).toLowerCase() + strVoName.substring(1);

			voH.append("package " + strWsVoPackageName + "; ").append("\n");
			voH.append(" ").append("\n");
			voH.append("import java.util.Arrays; ").append("\n");
			voH.append(" ").append("\n");
			voH.append("import javax.ws.rs.Consumes; ").append("\n");
			voH.append("import javax.xml.bind.annotation.XmlRootElement; ").append("\n");
			voH.append("@XmlRootElement( name=\"" + strWsVoName.substring(0, 1).toLowerCase() + strWsVoName.substring(1) + "\" )  ").append("\n");
			voH.append("public class " + strWsVoName + " implements java.io.Serializable { ").append("\n");
			voH.append("	 ").append("\n");

			// 신규 VO 일 경우
			if (!FileUtil.isFileExist(wsVoFileName)) {

				voConts.append("	/**** " + strMethodComment + " 관련 멤버 시작 ****/ ").append("\n");
				voConts.append("	private " + strVoPackageName + "." + strVoName + " " + voAlias + ";  ").append("\n");
				voConts.append("	 ").append("\n");
				voConts.append("	public " + strVoPackageName + "." + strVoName + " get" + strVoName + "() { ").append("\n");
				voConts.append("		return " + voAlias + "; ").append("\n");
				voConts.append("	} ").append("\n");
				voConts.append(" ").append("\n");
				voConts.append("	public void set" + strVoName + "(" + strVoPackageName + "." + strVoName + " " + voAlias + ") { ").append("\n");
				voConts.append("		this." + voAlias + " = " + voAlias + "; ").append("\n");
				voConts.append("	} ").append("\n");
				voConts.append(" ").append("\n");

				voConts.append("	private java.util.List<" + strVoPackageName + "." + strVoName + "> " + voAlias + "List; ").append("\n");
				voConts.append(" ").append("\n");
				voConts.append("	public java.util.List<" + strVoPackageName + "." + strVoName + "> get" + strVoName + "List() { ").append("\n");
				voConts.append("		return " + voAlias + "List; ").append("\n");
				voConts.append("	} ").append("\n");
				voConts.append(" ").append("\n");
				voConts.append("	public void set" + strVoName + "List( ").append("\n");
				voConts.append("			java.util.List<" + strVoPackageName + "." + strVoName + "> " + voAlias + "List) { ").append("\n");
				voConts.append("		this." + voAlias + "List = " + voAlias + "List; ").append("\n");
				voConts.append("	} ").append("\n");
				voConts.append("	/**** " + strMethodComment + " 관련 멤버 끝 ****/ ").append("\n");
				voConts.append(" ").append("\n");

				// 트랜젝션종류 - 2:입력, 3:수정, 4:삭제
				if (CRUD == 2 || CRUD == 3 || CRUD == 4) {
					voConts.append("	private boolean cudFlag;  ").append("\n");
					voConts.append("	public boolean getCudFlag() { ").append("\n");
					voConts.append("		return cudFlag; ").append("\n");
					voConts.append("	} ").append("\n");
					voConts.append("	public void setCudFlag(boolean cudFlag) { ").append("\n");
					voConts.append("		this.cudFlag = cudFlag; ").append("\n");
					voConts.append("	} ").append("\n");
					voConts.append(" ").append("\n");
				}

				// 기존재 VO 일 경우
			} else {

				if (!net.dstone.common.utils.BeanUtil.isBeanMemberName(strWsVoPackageName + "." + strWsVoName, voAlias)) {

					voConts.append("	/**** " + strMethodComment + " 관련 멤버 시작 ****/ ").append("\n");
					voConts.append("	private " + strVoPackageName + "." + strVoName + " " + voAlias + ";  ").append("\n");
					voConts.append("	 ").append("\n");
					voConts.append("	public " + strVoPackageName + "." + strVoName + " get" + strVoName + "() { ").append("\n");
					voConts.append("		return " + voAlias + "; ").append("\n");
					voConts.append("	} ").append("\n");
					voConts.append(" ").append("\n");
					voConts.append("	public void set" + strVoName + "(" + strVoPackageName + "." + strVoName + " " + voAlias + ") { ").append("\n");
					voConts.append("		this." + voAlias + " = " + voAlias + "; ").append("\n");
					voConts.append("	} ").append("\n");
					voConts.append(" ").append("\n");

					voConts.append("	private java.util.List<" + strVoPackageName + "." + strVoName + "> " + voAlias + "List; ").append("\n");
					voConts.append(" ").append("\n");
					voConts.append("	public java.util.List<" + strVoPackageName + "." + strVoName + "> get" + strVoName + "List() { ").append("\n");
					voConts.append("		return " + voAlias + "List; ").append("\n");
					voConts.append("	} ").append("\n");
					voConts.append(" ").append("\n");
					voConts.append("	public void set" + strVoName + "List( ").append("\n");
					voConts.append("			java.util.List<" + strVoPackageName + "." + strVoName + "> " + voAlias + "List) { ").append("\n");
					voConts.append("		this." + voAlias + "List = " + voAlias + "List; ").append("\n");
					voConts.append("	} ").append("\n");
					voConts.append("	/**** " + strMethodComment + " 관련 멤버 끝 ****/ ").append("\n");
					voConts.append(" ").append("\n");

				}

				// 트랜젝션종류 - 2:입력, 3:수정, 4:삭제
				if (CRUD == 2 || CRUD == 3 || CRUD == 4) {
					if (!net.dstone.common.utils.BeanUtil.isBeanMemberName(strWsVoPackageName + "." + strWsVoName, "cudFlag")) {
						voConts.append("	private boolean cudFlag;  ").append("\n");
						voConts.append("	public boolean getCudFlag() { ").append("\n");
						voConts.append("		return cudFlag; ").append("\n");
						voConts.append("	} ").append("\n");
						voConts.append("	public void setCudFlag(boolean cudFlag) { ").append("\n");
						voConts.append("		this.cudFlag = cudFlag; ").append("\n");
						voConts.append("	} ").append("\n");
						voConts.append(" ").append("\n");
					}
				}

			}

			voF.append("} ").append("\n");

			System.out.println("\r\n============================ WS VO SRC START ============================");

			String fileConts = "";

			if (!FileUtil.isFileExist(wsVoFileName)) {
				fileConts = voH.toString() + voConts.toString() + voF.toString();
			} else {
				fileConts = voConts.toString();
			}

			if (fileGenYn) {
				if (!FileUtil.isFileExist(wsVoFileName)) {
					net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(wsVoFileName), FileUtil.getFileName(wsVoFileName), voH.toString() + voF.toString(), CHARSET);
				}
				fileConts = net.dstone.common.utils.FileUtil.readFile(wsVoFileName, CHARSET);
				String fileContsH = fileConts.substring(0, fileConts.lastIndexOf("}"));
				String fileContsF = fileConts.substring(fileConts.lastIndexOf("}"));
				fileConts = fileContsH + "\n" + voConts.toString() + "\n" + fileContsF;
				// System.out.println(fileConts);
				net.dstone.common.utils.FileUtil.writeFile(FileUtil.getFilePath(wsVoFileName), FileUtil.getFileName(wsVoFileName), fileConts, CHARSET);
			}

			System.out.println(fileConts);
			System.out.println("============================ WS VO SRC END ============================\r\n");

			return (strWsVoPackageName + "." + strWsVoName);
		}
	}

	protected class TestGen {
		/**
		 * JSP 용 테스트 소스코드를 생성하는 메소드.
		 * 
		 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
		 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
		 * @param strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value="")에 들어갈 값. 필수.)
		 * @param strUri (메소드 URI. 메소드의 @RequestMapping(value = "")에 들어갈 값. 필수.)
		 * @param strVoPackageName (VO 패키지명. 필수.)
		 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
		 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
		 */
		private void genTestSrcForJsp(int CRUD, String strTableName, String strPrefix, String strUri, String strVoPackageName, String strVoName, boolean pageYn) {

			debug("||============== genTestSrcForJsp(JSP 용 테스트 소스코드를 생성하는 메소드.) =================||");
			debug("CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.) [" + CRUD + "]");
			debug("strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당) [" + strTableName + "]");
			debug("strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value=)에 들어갈 값. 필수.) [" + strPrefix + "]");
			debug("strUri (메소드 URI. 메소드의 @RequestMapping(value = )에 들어갈 값. 필수.) [" + strUri + "]");
			debug("strVoPackageName (VO 패키지명-파라메터. 필수.) [" + strVoPackageName + "]");
			debug("strVoName (VO 명-파라메터. 필수.) [" + strVoName + "]");
			debug("pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음. 필수.) [" + pageYn + "]");
			debug("||====================================================================================||");

			StringBuffer buff = new StringBuffer("\r\n");

			net.dstone.common.utils.DbUtil db = null;
			net.dstone.common.utils.DataSet ds = null;
			String sql = "";
			String[] cols = null;
			String col = "";
			String[] colsTypes = null;
			String colsType = "";
			String[] keys = null;
			String key = "";
			String mainKey = "";
			String[] parentKeys = null;
			java.util.Properties keyInfo = new java.util.Properties();
			boolean isKey = false;

			String strVoAlias = "";

			// 컬럼값 구해오는 부분.
			try {

				if (CRUD == 0 || CRUD == 1) {
					sql = net.dstone.common.utils.FileUtil.readFile(SQL_LOCATION, CHARSET);
				} else {
					sql = "SELECT * FROM " + strTableName + " WHERE 1>2 ";
				}

				db = new net.dstone.common.utils.DbUtil(DBID);
				db.getConnection();

				db.setQuery(sql);
				ds = new net.dstone.common.utils.DataSet();
				ds.buildFromResultSet(db.select(), "SELECT_RESULT");
				cols = db.columnNames;
				colsTypes = db.columnTypes;
				for (int i = 0; i < cols.length; i++) {
					keyInfo.setProperty(cols[i], colsTypes[i].trim());
				}

				if (CRUD == 2 || CRUD == 3 || CRUD == 4) {
					keys = BizGenerator.getPrimarykeys(strTableName);
					if (keys.length > 0) {
						parentKeys = new String[keys.length - 1];
						for (int i = 0; i < keys.length; i++) {
							if (i == keys.length - 1) {
								mainKey = keys[i];
							} else {
								parentKeys[i] = keys[i];
							}
						}
					} else {
						parentKeys = keys;
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				db.release();
			}

			try {

				strVoAlias = strVoName.substring(0, 1).toLowerCase() + strVoName.substring(1);

				// 트랜젝션종류 - 0:다건조회
				if (CRUD == 0) {

					buff.append("<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\"%>").append("\n");
					buff.append("<%                                                                                                              ").append("\n");
					buff.append("/******************************************* 변수 선언 시작 *******************************************/        	  ").append("\n");
					buff.append("net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);").append("\n");
					buff.append("String                                                         RETURN_CD;                                       ").append("\n");
					buff.append("java.util.HashMap                                              returnObj;                                       ").append("\n");
					buff.append("java.util.List<" + strVoPackageName + "." + strVoName + ">                  returnVoList;                       ").append("\n");
					buff.append("" + strVoPackageName + "." + strVoName + "                                  " + strVoAlias + ";                ").append("\n");
					if (pageYn) {
						buff.append("net.dstone.common.utils.PageUtil                                      pageUtil;                                        ").append("\n");
					}
					buff.append("/******************************************* 변수 선언 끝 *********************************************/           ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("/******************************************* 변수 정의 시작 *******************************************/           ").append("\n");
					buff.append("RETURN_CD           = (String)requestUtil.getAttribute(\"RETURN_CD\");                                             ").append("\n");
					buff.append("returnObj           = (java.util.HashMap)requestUtil.getAttribute(\"returnObj\");                                   ").append("\n");
					buff.append("" + strVoAlias + "            = null;                                                                          ").append("\n");
					buff.append("returnVoList        = null;                                                                          ").append("\n");
					if (pageYn) {
						buff.append("pageUtil            = null;                                                                          ").append("\n");
					}
					buff.append("if(returnObj != null){                                                                                          ").append("\n");
					buff.append("    returnVoList    = (java.util.List<" + strVoPackageName + "." + strVoName + ">)returnObj.get(\"returnObj\"); ").append("\n");
					if (pageYn) {
						buff.append("    pageUtil        = (net.dstone.common.utils.PageUtil)returnObj.get(\"pageUtil\");                                   ").append("\n");
					}
					buff.append("}                                                                                                             ").append("\n");
					buff.append("/******************************************* 변수 정의 끝 *********************************************/        ").append("\n");
					buff.append("%>                                                                                                              ").append("\n");
					buff.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">      ").append("\n");
					buff.append("<html>                                                                                                          ").append("\n");
					buff.append("<head>                                                                                                          ").append("\n");
					buff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">                                         ").append("\n");
					buff.append("<title>Insert title here</title>                                                                                ").append("\n");
					buff.append("<script type=\"text/javascript\">                                                                               ").append("\n");
					buff.append("	function goForIt(){                                                                                          ").append("\n");
					buff.append("		document.MAIN_FORM.submit();                                                                             ").append("\n");
					buff.append("	}                                                                                                            ").append("\n");
					buff.append("</script>                                                                                                       ").append("\n");
					buff.append("</head>                                                                                                         ").append("\n");
					buff.append("<body>                                                                                                          ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("<!--폼 시작-->                                                                                                   ").append("\n");
					buff.append("<form name=\"MAIN_FORM\" method=\"post\" action=\"" + (WEB_CONTEXT_ROOT.equals("/")?"":WEB_CONTEXT_ROOT) + strPrefix + "" + strUri + "\">                            ").append("\n");
					if (pageYn) {
						buff.append("	<input type=hidden name=\"PAGE_NUM\" value=\"<%= (pageUtil != null ? pageUtil.intPageNum : 1) %>\">           ").append("\n");
					}
					buff.append("	<input type='button' name='' value='LIST' onclick='javascript:goForIt();' >                                 ").append("\n");
					buff.append("	<table border=1>                                                                                              ").append("\n");
					buff.append("		<tr>                                                                                                      ").append("\n");

					buff.append("			");
					for (int i = 0; i < cols.length; i++) {
						buff.append("<td>" + cols[i] + "&nbsp;</td>");
					}
					buff.append("\n");

					buff.append("		</tr>                                                                                                     ").append("\n");
					buff.append("		<%                                                                                                        ").append("\n");
					buff.append("		if(returnVoList!=null){                                                                                   ").append("\n");
					buff.append("			for(int i=0; i<returnVoList.size(); i++){                                                             ").append("\n");
					buff.append("				" + strVoAlias + " = returnVoList.get(i);                                                         ").append("\n");
					buff.append("		%>                                                                                                        ").append("\n");
					buff.append("		<tr>                                                                                                      ").append("\n");

					buff.append("			");
					for (int i = 0; i < cols.length; i++) {
						buff.append("<td><%=" + strVoAlias + ".get" + cols[i] + "() %>&nbsp;</td>");
					}
					buff.append("\n");

					buff.append("		</tr>	                                                                                                  ").append("\n");
					buff.append("		<%                                                                                                        ").append("\n");
					buff.append("			}                                                                                                     ").append("\n");
					buff.append("		}                                                                                                         ").append("\n");
					buff.append("		%>                                                                                                        ").append("\n");
					if (pageYn) {
						buff.append("		<tr>                                                                                                      ").append("\n");
						buff.append("			<td colspan=" + cols.length + " &nbsp; ><%= (pageUtil != null ? pageUtil.htmlPostPage(request, \"MAIN_FORM\", \"PAGE_NUM\" ) : \"\" ) %></td> ").append("\n");
						buff.append("		</tr>	                                                                                                  ").append("\n");
					}
					buff.append("	</table>                                                                                                      ").append("\n");
					buff.append("                                                                                                                 ").append("\n");
					buff.append("</form>                                                                                                          ").append("\n");
					buff.append("<!--폼 끝-->                                                                                                      ").append("\n");
					buff.append("                                                                                                                 ").append("\n");
					buff.append("</body>                                                                                                          ").append("\n");
					buff.append("</html>                                                                                                          ").append("\n");

					// 트랜젝션종류 - 1:단건조회
				} else if (CRUD == 1) {

					buff.append("<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\"%>").append("\n");
					buff.append("<%                                                                                                              ").append("\n");
					buff.append("/******************************************* 변수 선언 시작 *******************************************/        	  ").append("\n");
					buff.append("net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);").append("\n");
					buff.append("String                                                         RETURN_CD;                                       ").append("\n");
					buff.append("" + strVoPackageName + "." + strVoName + "                                      returnObj;                                       ").append("\n");
					buff.append("/******************************************* 변수 선언 끝 *********************************************/           ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("/******************************************* 변수 정의 시작 *******************************************/           ").append("\n");
					buff.append("RETURN_CD           = (String)requestUtil.getAttribute(\"RETURN_CD\");                                             ").append("\n");
					buff.append("returnObj           = (" + strVoPackageName + "." + strVoName + ")requestUtil.getAttribute(\"returnObj\");                                   ").append("\n");
					buff.append("/******************************************* 변수 정의 끝 *********************************************/        ").append("\n");
					buff.append("%>                                                                                                              ").append("\n");
					buff.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">      ").append("\n");
					buff.append("<html>                                                                                                          ").append("\n");
					buff.append("<head>                                                                                                          ").append("\n");
					buff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">                                         ").append("\n");
					buff.append("<title>Insert title here</title>                                                                                ").append("\n");
					buff.append("<script type=\"text/javascript\">                                                                               ").append("\n");
					buff.append("	function goForIt(){                                                                                          ").append("\n");
					buff.append("		var param = document.MAIN_FORM.PARAM.value;                                                              ").append("\n");
					buff.append("		location.href = \"" + (WEB_CONTEXT_ROOT.equals("/")?"":WEB_CONTEXT_ROOT) + strPrefix + "" + strUri + "?\" + param;                                            ").append("\n");
					buff.append("	}                                                                                                            ").append("\n");
					buff.append("</script>                                                                                                       ").append("\n");

					buff.append("</head>                                                                                                         ").append("\n");
					buff.append("<body>                                                                                                          ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("<!--폼 시작-->                                                                                                   ").append("\n");
					buff.append("<form name=\"MAIN_FORM\" method=\"post\"  >                                                                     ").append("\n");
					buff.append("	<input type='button' name='' value='GET' onclick='javascript:goForIt();' >                                 ").append("\n");
					buff.append("	                                                                                                              ").append("\n");
					buff.append("	<table border=1>                                                                                              ").append("\n");
					buff.append("		<tr>                                                                                                      ").append("\n");
					buff.append("			<td>파라메터(예:USER_ID=0002&GROUP_ID=1)</td>                                                                                       ").append("\n");
					buff.append("		</tr>                                                                                                     ").append("\n");
					buff.append("		<tr>                                                                                                      ").append("\n");
					buff.append("			<td><input type='text' name='PARAM' value='' size=50 ></td>                                                    ").append("\n");
					buff.append("		</tr>                                                                                                     ").append("\n");
					buff.append("	</table>                                                                                                      ").append("\n");
					buff.append("	<br>                                                                                                         ").append("\n");
					buff.append("	<table border=1>                                                                                              ").append("\n");
					buff.append("		<tr>                                                                                                      ").append("\n");
					buff.append("			");
					for (int i = 0; i < cols.length; i++) {
						buff.append("<td>" + cols[i] + "&nbsp;</td>");
					}
					buff.append("\n");
					buff.append("		</tr>                                                                                                     ").append("\n");
					buff.append("		<%                                                                                                        ").append("\n");
					buff.append("		if(returnObj!=null){                                                                                   ").append("\n");
					buff.append("		%>                                                                                                        ").append("\n");
					buff.append("		<tr>                                                                                                      ").append("\n");
					buff.append("			");
					for (int i = 0; i < cols.length; i++) {
						buff.append("<td><%=returnObj.get" + cols[i] + "() %>&nbsp;</td>");
					}
					buff.append("\n");
					buff.append("		</tr>	                                                                                                  ").append("\n");
					buff.append("		<%                                                                                                        ").append("\n");
					buff.append("		}                                                                                                         ").append("\n");
					buff.append("		%>                                                                                                        ").append("\n");
					buff.append("	</table>                                                                                                      ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("</form>                                                                                                         ").append("\n");
					buff.append("<!--폼 끝-->                                                                                                    ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("</body>                                                                                                         ").append("\n");
					buff.append("</html>                                                                                                         ").append("\n");

					// 트랜젝션종류 - 2:입력
				} else if (CRUD == 2) {

					buff.append("<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\"%>").append("\n");
					buff.append("<%                                                                                                              ").append("\n");
					buff.append("/******************************************* 변수 선언 시작 *******************************************/        	  ").append("\n");
					buff.append("net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);").append("\n");
					buff.append("String                                               RETURN_CD;                                       ").append("\n");
					buff.append("Boolean                                              returnObj;                                                 ").append("\n");
					buff.append("/******************************************* 변수 선언 끝 *********************************************/           ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("/******************************************* 변수 정의 시작 *******************************************/           ").append("\n");
					buff.append("RETURN_CD           = (String)requestUtil.getAttribute(\"RETURN_CD\");                                             ").append("\n");
					buff.append("returnObj           = (Boolean)requestUtil.getAttribute(\"returnObj\");                                            ").append("\n");
					buff.append("/******************************************* 변수 정의 끝 *********************************************/        ").append("\n");
					buff.append("%>                                                                                                              ").append("\n");
					buff.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">      ").append("\n");
					buff.append("<html>                                                                                                          ").append("\n");
					buff.append("<head>                                                                                                          ").append("\n");
					buff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">                                         ").append("\n");
					buff.append("<title>Insert title here</title>                                                                                ").append("\n");
					buff.append("<script type=\"text/javascript\">                                                                               ").append("\n");
					buff.append("	                                                                                                             ").append("\n");
					buff.append("	function init(){                                                                                             ").append("\n");
					buff.append("		<%                                                                                                       ").append("\n");
					buff.append("		if( RETURN_CD != null ){                                                                                 ").append("\n");
					buff.append("			if(RETURN_CD.equals(net.dstone.common.biz.BaseController.RETURN_SUCCESS)){                                                                                   ").append("\n");
					buff.append("		%>	                                                                                                     ").append("\n");
					buff.append("			alert('SUCCESS');                                                                                    ").append("\n");
					buff.append("		<%                                                                                                       ").append("\n");
					buff.append("			}else{                                                                                               ").append("\n");
					buff.append("		%>	                                                                                                     ").append("\n");
					buff.append("			alert('FAIL');                                                                                       ").append("\n");
					buff.append("		<%	                                                                                                     ").append("\n");
					buff.append("			}                                                                                                    ").append("\n");
					buff.append("		}                                                                                                        ").append("\n");
					buff.append("		%>                                                                                                       ").append("\n");
					buff.append("	}                                                                                                            ").append("\n");
					buff.append("	                                                                                                             ").append("\n");
					buff.append("	function goForIt(){                                                                                          ").append("\n");
					buff.append("		document.MAIN_FORM.submit();                                                                             ").append("\n");
					buff.append("	}                                                                                                            ").append("\n");
					buff.append("</script>                                                                                                       ").append("\n");
					buff.append("</head>                                                                                                         ").append("\n");
					buff.append("<body onload='javascript:init();' >                                                                             ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("<!--폼 시작-->                                                                                                    ").append("\n");
					buff.append("<form name=\"MAIN_FORM\" method=\"post\" action=\"" + (WEB_CONTEXT_ROOT.equals("/")?"":WEB_CONTEXT_ROOT) + strPrefix + "" + strUri + "\">                      		 ").append("\n");
					buff.append("	<input type='button' name='' value='INSERT' onclick='javascript:goForIt();' >                                 ").append("\n");
					buff.append("	<table border=1>                                                                                              ").append("\n");

					for (int i = 0; i < cols.length; i++) {
						buff.append("		<tr>                                                                                                  ").append("\n");
						buff.append("			<td>" + cols[i] + "&nbsp;</td><td><input type='text' name='" + cols[i] + "' value=''></td>                ").append("\n");
						buff.append("		</tr>                                                                                                 ").append("\n");
					}

					buff.append("	</table>                                                                                                      ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("</form>                                                                                                         ").append("\n");
					buff.append("<!--폼 끝-->                                                                                                    ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("</body>                                                                                                         ").append("\n");
					buff.append("</html>                                                                                                         ").append("\n");

					// 트랜젝션종류 - 3:수정
				} else if (CRUD == 3) {

					buff.append("<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\"%>").append("\n");
					buff.append("<%                                                                                                              ").append("\n");
					buff.append("/******************************************* 변수 선언 시작 *******************************************/        	  ").append("\n");
					buff.append("net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);").append("\n");
					buff.append("String                                               RETURN_CD;                                       ").append("\n");
					buff.append("Boolean                                              returnObj;                                                 ").append("\n");
					buff.append("/******************************************* 변수 선언 끝 *********************************************/           ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("/******************************************* 변수 정의 시작 *******************************************/           ").append("\n");
					buff.append("RETURN_CD           = (String)requestUtil.getAttribute(\"RETURN_CD\");                                             ").append("\n");
					buff.append("returnObj           = (Boolean)requestUtil.getAttribute(\"returnObj\");                                   ").append("\n");
					buff.append("/******************************************* 변수 정의 끝 *********************************************/        ").append("\n");
					buff.append("%>                                                                                                              ").append("\n");
					buff.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">      ").append("\n");
					buff.append("<html>                                                                                                          ").append("\n");
					buff.append("<head>                                                                                                          ").append("\n");
					buff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">                                         ").append("\n");
					buff.append("<title>Insert title here</title>                                                                                ").append("\n");
					buff.append("<script type=\"text/javascript\">                                                                               ").append("\n");
					buff.append("	                                                                                                             ").append("\n");
					buff.append("	function init(){                                                                                             ").append("\n");
					buff.append("		<%                                                                                                       ").append("\n");
					buff.append("		if( RETURN_CD != null ){                                                                                 ").append("\n");
					buff.append("			if(RETURN_CD.equals(net.dstone.common.biz.BaseController.RETURN_SUCCESS)){                                                                                   ").append("\n");
					buff.append("		%>	                                                                                                     ").append("\n");
					buff.append("			alert('SUCCESS');                                                                                    ").append("\n");
					buff.append("		<%                                                                                                       ").append("\n");
					buff.append("			}else{                                                                                               ").append("\n");
					buff.append("		%>	                                                                                                     ").append("\n");
					buff.append("			alert('FAIL');                                                                                       ").append("\n");
					buff.append("		<%	                                                                                                     ").append("\n");
					buff.append("			}                                                                                                    ").append("\n");
					buff.append("		}                                                                                                        ").append("\n");
					buff.append("		%>                                                                                                       ").append("\n");
					buff.append("	}                                                                                                            ").append("\n");
					buff.append("	                                                                                                             ").append("\n");
					buff.append("	function goForIt(){                                                                                          ").append("\n");
					buff.append("		document.MAIN_FORM.submit();                                                                             ").append("\n");
					buff.append("	}                                                                                                            ").append("\n");
					buff.append("</script>                                                                                                       ").append("\n");
					buff.append("</head>                                                                                                         ").append("\n");
					buff.append("<body onload='javascript:init();' >                                                                             ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("<!--폼 시작-->                                                                                                    ").append("\n");
					buff.append("<form name=\"MAIN_FORM\" method=\"post\" action=\"" + (WEB_CONTEXT_ROOT.equals("/")?"":WEB_CONTEXT_ROOT) + strPrefix + "" + strUri + "\">                      		 ").append("\n");
					buff.append("	<input type='button' name='' value='UPDATE' onclick='javascript:goForIt();' >                                 ").append("\n");
					buff.append("	<table border=1>                                                                                              ").append("\n");

					for (int i = 0; i < keys.length; i++) {
						buff.append("		<tr>                                                                                                  ").append("\n");
						buff.append("			<td bgcolor=blue >" + keys[i] + "&nbsp;</td><td><input type='text' name='" + keys[i] + "' value=''></td>    ").append("\n");
						buff.append("		</tr>                                                                                                 ").append("\n");
					}
					for (int i = 0; i < cols.length; i++) {
						isKey = false;
						for (int k = 0; k < keys.length; k++) {
							if (cols[i].equals(keys[k])) {
								isKey = true;
								break;
							}
						}
						if (isKey) {
							continue;
						}
						buff.append("		<tr>                                                                                                  ").append("\n");
						buff.append("			<td>" + cols[i] + "&nbsp;</td><td><input type='text' name='" + cols[i] + "' value=''></td>                ").append("\n");
						buff.append("		</tr>                                                                                                 ").append("\n");
					}

					buff.append("	</table>                                                                                                      ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("</form>                                                                                                         ").append("\n");
					buff.append("<!--폼 끝-->                                                                                                    ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("</body>                                                                                                         ").append("\n");
					buff.append("</html>                                                                                                         ").append("\n");

					// 트랜젝션종류 - 4:삭제
				} else if (CRUD == 4) {

					buff.append("<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\"%>").append("\n");
					buff.append("<%                                                                                                              ").append("\n");
					buff.append("/******************************************* 변수 선언 시작 *******************************************/        	  ").append("\n");
					buff.append("net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);").append("\n");
					buff.append("String                                               RETURN_CD;                                       ").append("\n");
					buff.append("Boolean                                              returnObj;                                                 ").append("\n");
					buff.append("/******************************************* 변수 선언 끝 *********************************************/           ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("/******************************************* 변수 정의 시작 *******************************************/           ").append("\n");
					buff.append("RETURN_CD           = (String)requestUtil.getAttribute(\"RETURN_CD\");                                             ").append("\n");
					buff.append("returnObj           = (Boolean)requestUtil.getAttribute(\"returnObj\");                                   ").append("\n");
					buff.append("/******************************************* 변수 정의 끝 *********************************************/        ").append("\n");
					buff.append("%>                                                                                                              ").append("\n");
					buff.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">      ").append("\n");
					buff.append("<html>                                                                                                          ").append("\n");
					buff.append("<head>                                                                                                          ").append("\n");
					buff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">                                         ").append("\n");
					buff.append("<title>Insert title here</title>                                                                                ").append("\n");
					buff.append("<script type=\"text/javascript\">                                                                               ").append("\n");
					buff.append("	                                                                                                             ").append("\n");
					buff.append("	function init(){                                                                                             ").append("\n");
					buff.append("		<%                                                                                                       ").append("\n");
					buff.append("		if( RETURN_CD != null ){                                                                                 ").append("\n");
					buff.append("			if(RETURN_CD.equals(net.dstone.common.biz.BaseController.RETURN_SUCCESS)){                                                                                   ").append("\n");
					buff.append("		%>	                                                                                                     ").append("\n");
					buff.append("			alert('SUCCESS');                                                                                    ").append("\n");
					buff.append("		<%                                                                                                       ").append("\n");
					buff.append("			}else{                                                                                               ").append("\n");
					buff.append("		%>	                                                                                                     ").append("\n");
					buff.append("			alert('FAIL');                                                                                       ").append("\n");
					buff.append("		<%	                                                                                                     ").append("\n");
					buff.append("			}                                                                                                    ").append("\n");
					buff.append("		}                                                                                                        ").append("\n");
					buff.append("		%>                                                                                                       ").append("\n");
					buff.append("	}                                                                                                            ").append("\n");
					buff.append("	                                                                                                             ").append("\n");
					buff.append("	function goForIt(){                                                                                          ").append("\n");
					buff.append("		document.MAIN_FORM.submit();                                                                             ").append("\n");
					buff.append("	}                                                                                                            ").append("\n");
					buff.append("</script>                                                                                                       ").append("\n");
					buff.append("</head>                                                                                                         ").append("\n");
					buff.append("<body onload='javascript:init();' >                                                                             ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("<!--폼 시작-->                                                                                                    ").append("\n");
					buff.append("<form name=\"MAIN_FORM\" method=\"post\" action=\"" + (WEB_CONTEXT_ROOT.equals("/")?"":WEB_CONTEXT_ROOT) + strPrefix + "" + strUri + "\">                      		 ").append("\n");
					buff.append("	<input type='button' name='' value='DELETE' onclick='javascript:goForIt();' >                                 ").append("\n");
					buff.append("	<table border=1>                                                                                              ").append("\n");

					for (int i = 0; i < keys.length; i++) {
						buff.append("		<tr>                                                                                                  ").append("\n");
						buff.append("			<td bgcolor=blue >" + keys[i] + "&nbsp;</td><td><input type='text' name='" + keys[i] + "' value=''></td>    ").append("\n");
						buff.append("		</tr>                                                                                                 ").append("\n");
					}

					buff.append("	</table>                                                                                                      ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("</form>                                                                                                         ").append("\n");
					buff.append("<!--폼 끝-->                                                                                                    ").append("\n");
					buff.append("                                                                                                                ").append("\n");
					buff.append("</body>                                                                                                         ").append("\n");
					buff.append("</html>                                                                                                         ").append("\n");

				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println(buff);
		}

		/**
		 * Json 용 테스트 소스코드를 생성하는 메소드.
		 * 
		 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
		 * @param strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당)
		 * @param strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value="")에 들어갈 값. 필수.)
		 * @param strUri (메소드 URI. 메소드의 @RequestMapping(value = "")에 들어갈 값. 필수.)
		 * @param strVoPackageName (VO 패키지명. 필수.)
		 * @param strVoName (VO 명-파라메터 및 반환값타입. 필수.)
		 * @param pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음.)
		 */
		private void genTestSrcForJson(int CRUD, String strTableName, String strPrefix, String strUri, String strVoPackageName, String strVoName, boolean pageYn) {

			debug("||============== genTestSrcForJsp(JSP 용 테스트 소스코드를 생성하는 메소드.) =================||");
			debug("CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.) [" + CRUD + "]");
			debug("strTableName (테이블 명-CRUD가 2:입력, 3:수정, 4:삭제 일 경우에만 해당) [" + strTableName + "]");
			debug("strPrefix (CTRL 프리픽스. CTRL의 @RequestMapping(value=)에 들어갈 값. 필수.) [" + strPrefix + "]");
			debug("strUri (메소드 URI. 메소드의 @RequestMapping(value = )에 들어갈 값. 필수.) [" + strUri + "]");
			debug("strVoPackageName (VO 패키지명-파라메터. 필수.) [" + strVoPackageName + "]");
			debug("strVoName (VO 명-파라메터. 필수.) [" + strVoName + "]");
			debug("pageYn (페이징 여부. CRUD가 0 일 경우에만 의미있음. 필수.) [" + pageYn + "]");
			debug("||====================================================================================||");

			StringBuffer buff = new StringBuffer("\r\n");

			net.dstone.common.utils.DbUtil db = null;
			net.dstone.common.utils.DataSet ds = null;
			String sql = "";
			String[] cols = null;
			String col = "";
			String[] colsTypes = null;
			String colsType = "";
			String[] keys = null;
			String key = "";
			String mainKey = "";
			String[] parentKeys = null;
			java.util.Properties keyInfo = new java.util.Properties();
			boolean isKey = false;

			String strVoAlias = "";

			// 컬럼값 구해오는 부분.
			try {

				if (CRUD == 0 || CRUD == 1) {
					sql = net.dstone.common.utils.FileUtil.readFile(SQL_LOCATION, CHARSET);
				} else {
					sql = "SELECT * FROM " + strTableName + " WHERE 1>2 ";
				}

				db = new net.dstone.common.utils.DbUtil(DBID);
				db.getConnection();

				db.setQuery(sql);
				ds = new net.dstone.common.utils.DataSet();
				ds.buildFromResultSet(db.select(), "SELECT_RESULT");
				cols = db.columnNames;
				colsTypes = db.columnTypes;
				for (int i = 0; i < cols.length; i++) {
					keyInfo.setProperty(cols[i], colsTypes[i].trim());
				}

				if (CRUD == 2 || CRUD == 3 || CRUD == 4) {
					keys = BizGenerator.getPrimarykeys(strTableName);
					if (keys.length > 0) {
						parentKeys = new String[keys.length - 1];
						for (int i = 0; i < keys.length; i++) {
							if (i == keys.length - 1) {
								mainKey = keys[i];
							} else {
								parentKeys[i] = keys[i];
							}
						}
					} else {
						parentKeys = keys;
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				db.release();
			}

			try {

				strVoAlias = strVoName.substring(0, 1).toLowerCase() + strVoName.substring(1);

				// 트랜젝션종류 - 0:다건조회
				if (CRUD == 0) {

					buff.append("<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\"%> ").append("\n");
					buff.append("<% ").append("\n");
					buff.append("/******************************************* 변수 선언 시작 *******************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 선언 끝 *********************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 정의 시작 *******************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 정의 끝 *********************************************/ ").append("\n");
					buff.append("%> ").append("\n");
					buff.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\"> ").append("\n");
					buff.append("<html> ").append("\n");
					buff.append("<head> ").append("\n");
					buff.append("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=8\" /> ").append("\n");
					buff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"> ").append("\n");
					buff.append("<title>Insert title here</title> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("<script type=\"text/javascript\" src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js\"></script> ").append("\n");
					buff.append("<script type=\"text/javascript\" src=\"http://www.x-non.com/json/jquery.json-2.4.min.js\" ></script> ").append("\n");
					buff.append("<script type=\"text/javascript\"> ").append("\n");
					buff.append("	$.fn.serializeObject = function() { ").append("\n");
					buff.append("	    var o = {}; ").append("\n");
					buff.append("	    $(this).find('input[type=\"hidden\"], input[type=\"text\"], input[type=\"password\"], input[type=\"checkbox\"]:checked, input[type=\"radio\"]:checked, select').each(function() { ").append("\n");
					buff.append("	        if ($(this).attr('type') == 'hidden') { //if checkbox is checked do not take the hidden field ").append("\n");
					buff.append("	            var $parent = $(this).parent(); ").append("\n");
					buff.append("	            var $chb = $parent.find('input[type=\"checkbox\"][name=\"' + this.name.replace(/\\[/g, '\\[').replace(/\\]/g, '\\]') + '\\\"]'); ").append("\n");
					buff.append("	            if ($chb != null) { ").append("\n");
					buff.append("	                if ($chb.prop('checked')) return; ").append("\n");
					buff.append("	            } ").append("\n");
					buff.append("	        } ").append("\n");
					buff.append("	        if (this.name === null || this.name === undefined || this.name === '') return; ").append("\n");
					buff.append("	        var elemValue = null; ").append("\n");
					buff.append("	        if ($(this).is('select')) elemValue = $(this).find('option:selected').val(); ").append("\n");
					buff.append("	        else elemValue = this.value; ").append("\n");
					buff.append("	        if (o[this.name] !== undefined) { ").append("\n");
					buff.append("	            if (!o[this.name].push) { ").append("\n");
					buff.append("	                o[this.name] = [o[this.name]]; ").append("\n");
					buff.append("	            } ").append("\n");
					buff.append("	            o[this.name].push(elemValue || ''); ").append("\n");
					buff.append("	        } else { ").append("\n");
					buff.append("	            o[this.name] = elemValue || ''; ").append("\n");
					buff.append("	        } ").append("\n");
					buff.append("	    }); ").append("\n");
					buff.append("	    return o; ").append("\n");
					buff.append("	} ").append("\n");
					buff.append(" ").append("\n");
					buff.append("	function goForIt(){ ").append("\n");
					buff.append("		//alert(JSON.stringify($(document.MAIN_FORM).serializeObject())); ").append("\n");
					buff.append("		$.ajax({ ").append("\n");
					buff.append("			type:\"POST\", ").append("\n");
					buff.append("			url:\"" + strPrefix + "" + strUri + "\", ").append("\n");
					buff.append("			data:encodeURIComponent(JSON.stringify($(document.MAIN_FORM).serializeObject())), ").append("\n");
					buff.append("			dataType:\"json\", ").append("\n");
					buff.append("			success:function(data){ ").append("\n");
					buff.append("				var tbody = $(\"#out_tbody\"); ").append("\n");
					buff.append("				tbody.empty(); ").append("\n");
					buff.append("				var returnList = data.returnObj.returnObj; ").append("\n");
					buff.append("				var lineStr = \"\"; ").append("\n");
					buff.append("				tbody.append(lineStr); ").append("\n");
					buff.append("				for(var i=0; i<returnList.length; i++){ ").append("\n");
					buff.append("					lineStr = \"\"; ").append("\n");
					buff.append("					lineStr = lineStr + \"<tr>\"; ").append("\n");

					for (int i = 0; i < cols.length; i++) {
						buff.append("					lineStr = lineStr + \"<td>\"+returnList[i]." + cols[i].substring(0, 1).toLowerCase() + cols[i].substring(1) + "+\"</td>\"; ").append("\n");
					}

					buff.append("					lineStr = lineStr + \"</tr>\"; ").append("\n");
					buff.append("					tbody.append(lineStr); ").append("\n");
					buff.append("				} ").append("\n");
					if (pageYn) {
						buff.append("				document.getElementById(\"paging\").innerHTML = data.pageHTML; ").append("\n");
					}
					buff.append("			}, ").append("\n");
					buff.append("			error : function(data, status, e) { ").append("\n");
					buff.append("				alert(e); ").append("\n");
					buff.append("			} ").append("\n");
					buff.append("		}); ").append("\n");
					buff.append("	} ").append("\n");
					buff.append(" ").append("\n");
					if (pageYn) {
						buff.append("	function goPage(PAGE_NUM){ ").append("\n");
						buff.append("		document.MAIN_FORM.PAGE_NUM.value = PAGE_NUM; ").append("\n");
						buff.append("		goForIt(); ").append("\n");
						buff.append("	} ").append("\n");
						buff.append(" ").append("\n");
					}
					buff.append("</script> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("</head> ").append("\n");
					buff.append("<body> ").append("\n");
					buff.append("	<table border=1 > ").append("\n");
					buff.append("		<!--폼 시작--> ").append("\n");
					buff.append("		<form name=\"MAIN_FORM\" method=\"post\" action=\"\" enctype=\"multipart/form-data\" > ").append("\n");
					buff.append("		<input type=\"button\" value=\"LIST\" onclick=\"javascript:goForIt();\" > ").append("\n");
					if (pageYn) {
						buff.append("		<tr> ").append("\n");
						buff.append("			<td>페이지번호</td> ").append("\n");
						buff.append("			<td colspan=2 ><input type=\"text\" name=\"PAGE_NUM\" value=\"\"></td> ").append("\n");
						buff.append("		</tr> ").append("\n");
					}
					buff.append("		</form> ").append("\n");
					buff.append("		<!--폼 끝--> ").append("\n");
					buff.append("	</table> ").append("\n");
					buff.append("	<br> ").append("\n");
					buff.append("	<table border=1 width=\"80%\" > ").append("\n");
					buff.append("		<tr> ").append("\n");

					for (int i = 0; i < cols.length; i++) {
						buff.append("			<td>" + cols[i] + "</td> ").append("\n");
					}
					buff.append("		</tr> ").append("\n");
					buff.append("		<tbody id=\"out_tbody\" > ").append("\n");
					buff.append("		</tbody> ").append("\n");
					if (pageYn) {
						buff.append("		<tr> ").append("\n");
						buff.append("			<td colspan=" + cols.length + " > ").append("\n");
						buff.append("				<div id=\"paging\" ></div> ").append("\n");
						buff.append("			</td> ").append("\n");
						buff.append("		</tr> ").append("\n");
					}
					buff.append("	</table> ").append("\n");
					buff.append("</body> ").append("\n");
					buff.append("</html> ").append("\n");

					// 트랜젝션종류 - 1:단건조회
				} else if (CRUD == 1) {

					buff.append("<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\"%> ").append("\n");
					buff.append("<% ").append("\n");
					buff.append("/******************************************* 변수 선언 시작 *******************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 선언 끝 *********************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 정의 시작 *******************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 정의 끝 *********************************************/ ").append("\n");
					buff.append("%> ").append("\n");
					buff.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\"> ").append("\n");
					buff.append("<html> ").append("\n");
					buff.append("<head> ").append("\n");
					buff.append("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=8\" /> ").append("\n");
					buff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"> ").append("\n");
					buff.append("<title>Insert title here</title> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("<script type=\"text/javascript\" src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js\"></script> ").append("\n");
					buff.append("<script type=\"text/javascript\" src=\"http://www.x-non.com/json/jquery.json-2.4.min.js\" ></script> ").append("\n");
					buff.append("<script type=\"text/javascript\"> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("	$.fn.serializeObject = function() { ").append("\n");
					buff.append("	    var o = {}; ").append("\n");
					buff.append("	    $(this).find('input[type=\"hidden\"], input[type=\"text\"], input[type=\"password\"], input[type=\"checkbox\"]:checked, input[type=\"radio\"]:checked, select').each(function() { ").append("\n");
					buff.append("	        if ($(this).attr('type') == 'hidden') { //if checkbox is checked do not take the hidden field ").append("\n");
					buff.append("	            var $parent = $(this).parent(); ").append("\n");
					buff.append("	            var $chb = $parent.find('input[type=\"checkbox\"][name=\"' + this.name.replace(/\\[/g, '\\[').replace(/\\]/g, '\\]') + '\\\"]'); ").append("\n");
					buff.append("	            if ($chb != null) { ").append("\n");
					buff.append("	                if ($chb.prop('checked')) return; ").append("\n");
					buff.append("	            } ").append("\n");
					buff.append("	        } ").append("\n");
					buff.append("	        if (this.name === null || this.name === undefined || this.name === '') return; ").append("\n");
					buff.append("	        var elemValue = null; ").append("\n");
					buff.append("	        if ($(this).is('select')) elemValue = $(this).find('option:selected').val(); ").append("\n");
					buff.append("	        else elemValue = this.value; ").append("\n");
					buff.append("	        if (o[this.name] !== undefined) { ").append("\n");
					buff.append("	            if (!o[this.name].push) { ").append("\n");
					buff.append("	                o[this.name] = [o[this.name]]; ").append("\n");
					buff.append("	            } ").append("\n");
					buff.append("	            o[this.name].push(elemValue || ''); ").append("\n");
					buff.append("	        } else { ").append("\n");
					buff.append("	            o[this.name] = elemValue || ''; ").append("\n");
					buff.append("	        } ").append("\n");
					buff.append("	    }); ").append("\n");
					buff.append("	    return o; ").append("\n");
					buff.append("	} ").append("\n");
					buff.append(" ").append("\n");
					buff.append("	function goForIt(){ ").append("\n");
					buff.append("		$.ajax({ ").append("\n");
					buff.append("			type:\"POST\", ").append("\n");
					buff.append("			url:\"" + strPrefix + "" + strUri + "\", ").append("\n");
					buff.append("			data:encodeURIComponent(JSON.stringify($(document.MAIN_FORM).serializeObject())), ").append("\n");
					buff.append("			dataType:\"json\", ").append("\n");
					buff.append("			success:function(data){ ").append("\n");
					buff.append("				var tbody = $(\"#out_tbody\"); ").append("\n");
					buff.append("				tbody.empty(); ").append("\n");
					buff.append("				var returnObj = data.returnObj; ").append("\n");
					buff.append("				var lineStr = \"\"; ").append("\n");
					buff.append(" ").append("\n");
					buff.append("				if(returnObj){ ").append("\n");
					buff.append("					lineStr = \"\"; ").append("\n");
					buff.append("					lineStr = lineStr + \"<tr>\"; ").append("\n");

					for (int i = 0; i < cols.length; i++) {
						buff.append("					lineStr = lineStr + \"<td>\"+returnObj." + cols[i].substring(0, 1).toLowerCase() + cols[i].substring(1) + "+\"</td>\"; ").append("\n");
					}

					buff.append("					lineStr = lineStr + \"</tr>\"; ").append("\n");
					buff.append("					tbody.append(lineStr); ").append("\n");
					buff.append("				} ").append("\n");
					buff.append("			}, ").append("\n");
					buff.append("			error : function(data, status, e) { ").append("\n");
					buff.append("				alert(e); ").append("\n");
					buff.append("			} ").append("\n");
					buff.append("		}); ").append("\n");
					buff.append("	} ").append("\n");
					buff.append(" ").append("\n");
					buff.append("</script> ").append("\n");
					buff.append("</head> ").append("\n");
					buff.append("<body> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("<!--폼 시작--> ").append("\n");
					buff.append("<form name=\"MAIN_FORM\" method=\"post\"  > ").append("\n");
					buff.append("	<input type='button' name='' value='GET' onclick='javascript:goForIt();' > ").append("\n");
					buff.append("	<table border=1> ").append("\n");
					buff.append("		<tr> ").append("\n");
					buff.append("		<!-- 하단에 조회조건을 세팅해 주세요 --> ").append("\n");
					for (int i = 0; i < cols.length; i++) {
						buff.append("			<td>" + cols[i] + "</td><td><input type='text' name='" + cols[i] + "' value=''></td> ").append("\n");
						break;
					}
					buff.append("		<!-- 상단에 조회조건을 세팅해 주세요 --> ").append("\n");

					buff.append("		</tr> ").append("\n");
					buff.append("	</table> ").append("\n");
					buff.append("	<br> ").append("\n");
					buff.append("	<table border=1 width=\"80%\" > ").append("\n");
					buff.append("		<tr> ").append("\n");

					for (int i = 0; i < cols.length; i++) {
						buff.append("			<td>" + cols[i] + "</td>").append("\n");
					}

					buff.append("		</tr> ").append("\n");
					buff.append("		<tbody id=\"out_tbody\" > ").append("\n");
					buff.append("		</tbody> ").append("\n");
					buff.append("	</table> ").append("\n");
					buff.append("</form> ").append("\n");
					buff.append("<!--폼 끝--> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("</body> ").append("\n");
					buff.append("</html> ").append("\n");

					// 트랜젝션종류 - 2:입력
				} else if (CRUD == 2) {

					buff.append("<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\"%> ").append("\n");
					buff.append("<% ").append("\n");
					buff.append("/******************************************* 변수 선언 시작 *******************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 선언 끝 *********************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 정의 시작 *******************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 정의 끝 *********************************************/ ").append("\n");
					buff.append("%> ").append("\n");
					buff.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\"> ").append("\n");
					buff.append("<html> ").append("\n");
					buff.append("<head> ").append("\n");
					buff.append("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=8\" /> ").append("\n");
					buff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"> ").append("\n");
					buff.append("<title>Insert title here</title> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("<script type=\"text/javascript\" src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js\"></script> ").append("\n");
					buff.append("<script type=\"text/javascript\" src=\"http://www.x-non.com/json/jquery.json-2.4.min.js\" ></script> ").append("\n");
					buff.append("<script type=\"text/javascript\"> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("	$.fn.serializeObject = function() { ").append("\n");
					buff.append("	    var o = {}; ").append("\n");
					buff.append("	    $(this).find('input[type=\"hidden\"], input[type=\"text\"], input[type=\"password\"], input[type=\"checkbox\"]:checked, input[type=\"radio\"]:checked, select').each(function() { ").append("\n");
					buff.append("	        if ($(this).attr('type') == 'hidden') { //if checkbox is checked do not take the hidden field ").append("\n");
					buff.append("	            var $parent = $(this).parent(); ").append("\n");
					buff.append("	            var $chb = $parent.find('input[type=\"checkbox\"][name=\"' + this.name.replace(/\\[/g, '\\[').replace(/\\]/g, '\\]') + '\\\"]'); ").append("\n");
					buff.append("	            if ($chb != null) { ").append("\n");
					buff.append("	                if ($chb.prop('checked')) return; ").append("\n");
					buff.append("	            } ").append("\n");
					buff.append("	        } ").append("\n");
					buff.append("	        if (this.name === null || this.name === undefined || this.name === '') return; ").append("\n");
					buff.append("	        var elemValue = null; ").append("\n");
					buff.append("	        if ($(this).is('select')) elemValue = $(this).find('option:selected').val(); ").append("\n");
					buff.append("	        else elemValue = this.value; ").append("\n");
					buff.append("	        if (o[this.name] !== undefined) { ").append("\n");
					buff.append("	            if (!o[this.name].push) { ").append("\n");
					buff.append("	                o[this.name] = [o[this.name]]; ").append("\n");
					buff.append("	            } ").append("\n");
					buff.append("	            o[this.name].push(elemValue || ''); ").append("\n");
					buff.append("	        } else { ").append("\n");
					buff.append("	            o[this.name] = elemValue || ''; ").append("\n");
					buff.append("	        } ").append("\n");
					buff.append("	    }); ").append("\n");
					buff.append("	    return o; ").append("\n");
					buff.append("	} ").append("\n");
					buff.append(" ").append("\n");
					buff.append("	function goForIt(){ ").append("\n");
					buff.append("		$.ajax({ ").append("\n");
					buff.append("			type:\"POST\", ").append("\n");
					buff.append("			url:\"" + strPrefix + "" + strUri + "\", ").append("\n");
					buff.append("			data:encodeURIComponent(JSON.stringify($(document.MAIN_FORM).serializeObject())), ").append("\n");
					buff.append("			dataType:\"json\", ").append("\n");
					buff.append("			success:function(data){ ").append("\n");
					buff.append("				alert('success'); ").append("\n");
					buff.append("			}, ").append("\n");
					buff.append("			error : function(data, status, e) { ").append("\n");
					buff.append("				alert(e); ").append("\n");
					buff.append("			} ").append("\n");
					buff.append("		}); ").append("\n");
					buff.append("	} ").append("\n");
					buff.append(" ").append("\n");
					buff.append("</script> ").append("\n");
					buff.append("</head> ").append("\n");
					buff.append("<body> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("<!--폼 시작--> ").append("\n");
					buff.append("<form name=\"MAIN_FORM\" method=\"post\"  > ").append("\n");
					buff.append("	<input type='button' name='' value='INSERT' onclick='javascript:goForIt();' > ").append("\n");
					buff.append("	<table border=1 width=\"80%\" > ").append("\n");

					for (int i = 0; i < keys.length; i++) {
						buff.append("		<tr>                                                                                                  ").append("\n");
						buff.append("			<td bgcolor=blue >" + keys[i] + "</td><td><input type='text' name='" + keys[i] + "' value=''></td> ").append("\n");
						buff.append("		</tr>                                                                                                 ").append("\n");
					}
					for (int i = 0; i < cols.length; i++) {
						isKey = false;
						for (int k = 0; k < keys.length; k++) {
							if (cols[i].equals(keys[k])) {
								isKey = true;
								break;
							}
						}
						if (isKey) {
							continue;
						}
						buff.append("		<tr>                                                                                                  ").append("\n");
						buff.append("			<td>" + cols[i] + "&nbsp;</td><td><input type='text' name='" + cols[i] + "' value=''></td>                ").append("\n");
						buff.append("		</tr>                                                                                                 ").append("\n");
					}

					buff.append("	</table> ").append("\n");
					buff.append("</form> ").append("\n");
					buff.append("<!--폼 끝--> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("</body> ").append("\n");
					buff.append("</html> ").append("\n");

					// 트랜젝션종류 - 3:수정
				} else if (CRUD == 3) {

					buff.append("<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\"%> ").append("\n");
					buff.append("<% ").append("\n");
					buff.append("/******************************************* 변수 선언 시작 *******************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 선언 끝 *********************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 정의 시작 *******************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 정의 끝 *********************************************/ ").append("\n");
					buff.append("%> ").append("\n");
					buff.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\"> ").append("\n");
					buff.append("<html> ").append("\n");
					buff.append("<head> ").append("\n");
					buff.append("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=8\" /> ").append("\n");
					buff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"> ").append("\n");
					buff.append("<title>Insert title here</title> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("<script type=\"text/javascript\" src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js\"></script> ").append("\n");
					buff.append("<script type=\"text/javascript\" src=\"http://www.x-non.com/json/jquery.json-2.4.min.js\" ></script> ").append("\n");
					buff.append("<script type=\"text/javascript\"> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("	$.fn.serializeObject = function() { ").append("\n");
					buff.append("	    var o = {}; ").append("\n");
					buff.append("	    $(this).find('input[type=\"hidden\"], input[type=\"text\"], input[type=\"password\"], input[type=\"checkbox\"]:checked, input[type=\"radio\"]:checked, select').each(function() { ").append("\n");
					buff.append("	        if ($(this).attr('type') == 'hidden') { //if checkbox is checked do not take the hidden field ").append("\n");
					buff.append("	            var $parent = $(this).parent(); ").append("\n");
					buff.append("	            var $chb = $parent.find('input[type=\"checkbox\"][name=\"' + this.name.replace(/\\[/g, '\\[').replace(/\\]/g, '\\]') + '\\\"]'); ").append("\n");
					buff.append("	            if ($chb != null) { ").append("\n");
					buff.append("	                if ($chb.prop('checked')) return; ").append("\n");
					buff.append("	            } ").append("\n");
					buff.append("	        } ").append("\n");
					buff.append("	        if (this.name === null || this.name === undefined || this.name === '') return; ").append("\n");
					buff.append("	        var elemValue = null; ").append("\n");
					buff.append("	        if ($(this).is('select')) elemValue = $(this).find('option:selected').val(); ").append("\n");
					buff.append("	        else elemValue = this.value; ").append("\n");
					buff.append("	        if (o[this.name] !== undefined) { ").append("\n");
					buff.append("	            if (!o[this.name].push) { ").append("\n");
					buff.append("	                o[this.name] = [o[this.name]]; ").append("\n");
					buff.append("	            } ").append("\n");
					buff.append("	            o[this.name].push(elemValue || ''); ").append("\n");
					buff.append("	        } else { ").append("\n");
					buff.append("	            o[this.name] = elemValue || ''; ").append("\n");
					buff.append("	        } ").append("\n");
					buff.append("	    }); ").append("\n");
					buff.append("	    return o; ").append("\n");
					buff.append("	} ").append("\n");
					buff.append(" ").append("\n");
					buff.append("	function goForIt(){ ").append("\n");
					buff.append("		$.ajax({ ").append("\n");
					buff.append("			type:\"POST\", ").append("\n");
					buff.append("			url:\"" + strPrefix + "" + strUri + "\", ").append("\n");
					buff.append("			data:encodeURIComponent(JSON.stringify($(document.MAIN_FORM).serializeObject())), ").append("\n");
					buff.append("			dataType:\"json\", ").append("\n");
					buff.append("			success:function(data){ ").append("\n");
					buff.append("				alert('success'); ").append("\n");
					buff.append("			}, ").append("\n");
					buff.append("			error : function(data, status, e) { ").append("\n");
					buff.append("				alert(e); ").append("\n");
					buff.append("			} ").append("\n");
					buff.append("		}); ").append("\n");
					buff.append("	} ").append("\n");
					buff.append(" ").append("\n");
					buff.append("</script> ").append("\n");
					buff.append("</head> ").append("\n");
					buff.append("<body> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("<!--폼 시작--> ").append("\n");
					buff.append("<form name=\"MAIN_FORM\" method=\"post\"  > ").append("\n");
					buff.append("	<input type='button' name='' value='UPDATE' onclick='javascript:goForIt();' > ").append("\n");
					buff.append("	<table border=1 width=\"80%\" > ").append("\n");

					for (int i = 0; i < keys.length; i++) {
						buff.append("		<tr>                                                                                                  ").append("\n");
						buff.append("			<td bgcolor=blue >" + keys[i] + "</td><td><input type='text' name='" + keys[i] + "' value=''></td> ").append("\n");
						buff.append("		</tr>                                                                                                 ").append("\n");
					}
					for (int i = 0; i < cols.length; i++) {
						isKey = false;
						for (int k = 0; k < keys.length; k++) {
							if (cols[i].equals(keys[k])) {
								isKey = true;
								break;
							}
						}
						if (isKey) {
							continue;
						}
						buff.append("		<tr>                                                                                                  ").append("\n");
						buff.append("			<td>" + cols[i] + "&nbsp;</td><td><input type='text' name='" + cols[i] + "' value=''></td>                ").append("\n");
						buff.append("		</tr>                                                                                                 ").append("\n");
					}

					buff.append("	</table> ").append("\n");
					buff.append("</form> ").append("\n");
					buff.append("<!--폼 끝--> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("</body> ").append("\n");
					buff.append("</html> ").append("\n");

					// 트랜젝션종류 - 4:삭제
				} else if (CRUD == 4) {

					buff.append("<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\"%> ").append("\n");
					buff.append("<% ").append("\n");
					buff.append("/******************************************* 변수 선언 시작 *******************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 선언 끝 *********************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 정의 시작 *******************************************/ ").append("\n");
					buff.append(" ").append("\n");
					buff.append("/******************************************* 변수 정의 끝 *********************************************/ ").append("\n");
					buff.append("%> ").append("\n");
					buff.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\"> ").append("\n");
					buff.append("<html> ").append("\n");
					buff.append("<head> ").append("\n");
					buff.append("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=8\" /> ").append("\n");
					buff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"> ").append("\n");
					buff.append("<title>Insert title here</title> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("<script type=\"text/javascript\" src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js\"></script> ").append("\n");
					buff.append("<script type=\"text/javascript\" src=\"http://www.x-non.com/json/jquery.json-2.4.min.js\" ></script> ").append("\n");
					buff.append("<script type=\"text/javascript\"> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("	$.fn.serializeObject = function() { ").append("\n");
					buff.append("	    var o = {}; ").append("\n");
					buff.append("	    $(this).find('input[type=\"hidden\"], input[type=\"text\"], input[type=\"password\"], input[type=\"checkbox\"]:checked, input[type=\"radio\"]:checked, select').each(function() { ").append("\n");
					buff.append("	        if ($(this).attr('type') == 'hidden') { //if checkbox is checked do not take the hidden field ").append("\n");
					buff.append("	            var $parent = $(this).parent(); ").append("\n");
					buff.append("	            var $chb = $parent.find('input[type=\"checkbox\"][name=\"' + this.name.replace(/\\[/g, '\\[').replace(/\\]/g, '\\]') + '\\\"]'); ").append("\n");
					buff.append("	            if ($chb != null) { ").append("\n");
					buff.append("	                if ($chb.prop('checked')) return; ").append("\n");
					buff.append("	            } ").append("\n");
					buff.append("	        } ").append("\n");
					buff.append("	        if (this.name === null || this.name === undefined || this.name === '') return; ").append("\n");
					buff.append("	        var elemValue = null; ").append("\n");
					buff.append("	        if ($(this).is('select')) elemValue = $(this).find('option:selected').val(); ").append("\n");
					buff.append("	        else elemValue = this.value; ").append("\n");
					buff.append("	        if (o[this.name] !== undefined) { ").append("\n");
					buff.append("	            if (!o[this.name].push) { ").append("\n");
					buff.append("	                o[this.name] = [o[this.name]]; ").append("\n");
					buff.append("	            } ").append("\n");
					buff.append("	            o[this.name].push(elemValue || ''); ").append("\n");
					buff.append("	        } else { ").append("\n");
					buff.append("	            o[this.name] = elemValue || ''; ").append("\n");
					buff.append("	        } ").append("\n");
					buff.append("	    }); ").append("\n");
					buff.append("	    return o; ").append("\n");
					buff.append("	} ").append("\n");
					buff.append(" ").append("\n");
					buff.append("	function goForIt(){ ").append("\n");
					buff.append("		$.ajax({ ").append("\n");
					buff.append("			type:\"POST\", ").append("\n");
					buff.append("			url:\"" + strPrefix + "" + strUri + "\", ").append("\n");
					buff.append("			data:encodeURIComponent(JSON.stringify($(document.MAIN_FORM).serializeObject())), ").append("\n");
					buff.append("			dataType:\"json\", ").append("\n");
					buff.append("			success:function(data){ ").append("\n");
					buff.append("				alert('success'); ").append("\n");
					buff.append("			}, ").append("\n");
					buff.append("			error : function(data, status, e) { ").append("\n");
					buff.append("				alert(e); ").append("\n");
					buff.append("			} ").append("\n");
					buff.append("		}); ").append("\n");
					buff.append("	} ").append("\n");
					buff.append(" ").append("\n");
					buff.append("</script> ").append("\n");
					buff.append("</head> ").append("\n");
					buff.append("<body> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("<!--폼 시작--> ").append("\n");
					buff.append("<form name=\"MAIN_FORM\" method=\"post\"  > ").append("\n");
					buff.append("	<input type='button' name='' value='DELETE' onclick='javascript:goForIt();' > ").append("\n");
					buff.append("	<table border=1 width=\"80%\" > ").append("\n");

					for (int i = 0; i < keys.length; i++) {
						buff.append("		<tr>                                                                                                  ").append("\n");
						buff.append("			<td bgcolor=blue >" + keys[i] + "</td><td><input type='text' name='" + keys[i] + "' value=''></td> ").append("\n");
						buff.append("		</tr>                                                                                                 ").append("\n");
					}

					buff.append("	</table> ").append("\n");
					buff.append("</form> ").append("\n");
					buff.append("<!--폼 끝--> ").append("\n");
					buff.append(" ").append("\n");
					buff.append("</body> ").append("\n");
					buff.append("</html> ").append("\n");

				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println(buff);
		}

		/**
		 * RestFul Web Service - JQUERY 용 테스트 소스코드를 생성하는 메소드.
		 * 
		 * @param strWSPath (WS Path. 필수.)
		 * @param strMethodKind (메소드종류:PUT/POST/DELETE. 필수.)
		 * @param strMethodName (메소드명. 필수.)
		 * @param strWsVoPackageName (웹서비스VO 패키지명-파라메터. 필수.)
		 * @param strWsVoName (웹서비스VO 명-파라메터. 필수.)
		 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
		 * @param strMediaType (미디어타입:XML/JSON). 필수.) 예) (new net.dstone.common.tools.BizGenerator()).new WsGen().genWsTestSrcForJquery("/Event", "PUT", "listEvent", "com.test.biz.event.vo", "EventListWsVo", 0, "XML");
		 */
		private void genTestSrcForWsJquery(String strWSPath, String strMethodKind, String strMethodName, String strWsVoPackageName, String strWsVoName, int CRUD, String strMediaType) {

			debug("||=== genTestSrcForWsJquery(RestFul Web Service - JQUERY 용 테스트 소스코드를 생성하는 메소드) ===||");
			debug("strWSPath (WS Path. 필수.) [" + strWSPath + "]");
			debug("strMethodKind (메소드종류:PUT/POST/DELETE. 필수.) [" + strMethodKind + "]");
			debug("strMethodName (메소드명. 필수.) [" + strMethodName + "]");
			debug("strWsVoPackageName (웹서비스VO 패키지명-파라메터. 필수.) [" + strWsVoPackageName + "]");
			debug("strWsVoName (웹서비스VO 명-파라메터. 필수.) [" + strWsVoName + "]");
			debug("CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.) [" + CRUD + "]");
			debug("strMediaType (미디어타입:XML/JSON). 필수.) [" + strMediaType + "]");
			debug("||====================================================================================||");

			StringBuffer tBuff = new StringBuffer("\r\n");
			tBuff.append("============================ JQUERY-TEST SRC START ============================").append("\r\n");
			tBuff.append("<script type=\"text/javascript\"  src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.7/jquery.min.js\"></script>").append("\r\n");
			tBuff.append("<script language=\"Javascript\"> ").append("\r\n");
			tBuff.append("  /* ").append("\r\n");
			tBuff.append("   * This is a sample jQuert HTTP REST client generated by HTTP4e (http://www.nextinterfaces.com). ").append("\r\n");
			tBuff.append("   */ ").append("\r\n");
			tBuff.append("  var url = \"" + WS_WEB_URL + (WEB_CONTEXT_ROOT.equals("/")?"":WEB_CONTEXT_ROOT) + "" + WS_ROOT_PATH + "" + strWSPath + "/" + strMethodName + "\"; ").append("\r\n");
			tBuff.append("  var method = \"" + strMethodKind + "\"; ").append("\r\n");
			tBuff.append("  var params = \"\"; ").append("\r\n");

			if ("XML".equals(strMediaType)) {
				// tBuff.append("  var data = \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\" standalone=\\\"yes\\\"?>");
				tBuff.append("  var data = \"");
				try {
					Object obj = net.dstone.common.tools.DataGenerator.genRandomData(strWsVoPackageName + "." + strWsVoName, 1);
					String dataStr = net.dstone.common.utils.BeanUtil.toXml(obj);
					dataStr = StringUtil.replace(dataStr, "\"", "\\\"");
					dataStr = StringUtil.replace(dataStr, "\n", "");
					tBuff.append(dataStr);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if ("JSON".equals(strMediaType)) {
				try {
					Object obj = net.dstone.common.tools.DataGenerator.genRandomData(strWsVoPackageName + "." + strWsVoName, 1);
					String dataStr = net.dstone.common.utils.BeanUtil.toJson(obj);
					dataStr = StringUtil.replace(dataStr, "\"", "\\\"");
					dataStr = StringUtil.replace(dataStr, "\n", "");
					tBuff.append("  var data = \"" + dataStr);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			tBuff.append("\"; ").append("\r\n");
			tBuff.append("  var dataType = \"text\"; //\"xml\", \"html\", \"script\", \"json\", \"jsonp\", \"text\" ").append("\r\n");
			tBuff.append(" ").append("\r\n");
			tBuff.append("  var setRequestHeaders = function(xhr) { ").append("\r\n");
			if ("XML".equals(strMediaType)) {
				tBuff.append("      xhr.setRequestHeader(\"Content-Type\", \"application/xml; charset="+CHARSET+"\"); ").append("\r\n");
			} else if ("JSON".equals(strMediaType)) {
				tBuff.append("      xhr.setRequestHeader(\"Content-Type\", \"application/json; charset="+CHARSET+"\"); ").append("\r\n");
			}
			tBuff.append("  } ").append("\r\n");
			tBuff.append(" ").append("\r\n");
			tBuff.append("  var onSuccess = function(responseData, status, xhr) { ").append("\r\n");
			tBuff.append("      alert(\"Success\"); ").append("\r\n");
			tBuff.append("      debug(xhr); ").append("\r\n");
			tBuff.append("  }; ").append("\r\n");
			tBuff.append(" ").append("\r\n");
			tBuff.append("  var onError = function(xhr) { ").append("\r\n");
			tBuff.append("      alert(\"Failure\"); ").append("\r\n");
			tBuff.append("      debug(xhr); ").append("\r\n");
			tBuff.append("  } ").append("\r\n");
			tBuff.append(" ").append("\r\n");
			tBuff.append("  var debug = function(xhr) {		 ").append("\r\n");
			tBuff.append("      if (xhr.readyState == 4) { ").append("\r\n");
			tBuff.append("      alert(\"HTTP/1.1 \" + xhr.status + \" \" + xhr.statusText +  ").append("\r\n");
			tBuff.append("            \"\\n\" + xhr.getAllResponseHeaders() +  ").append("\r\n");
			tBuff.append("            \"\\n\" + xhr.responseText);	 ").append("\r\n");
			tBuff.append("	} ").append("\r\n");
			tBuff.append("  } ").append("\r\n");
			tBuff.append("	 ").append("\r\n");
			tBuff.append("  $.ajax ({  ").append("\r\n");
			tBuff.append("      type: method,  ").append("\r\n");
			tBuff.append("      url: url,  ").append("\r\n");
			tBuff.append("      data: data, ").append("\r\n");
			tBuff.append("      dataType: dataType, ").append("\r\n");
			tBuff.append("      cache: false,  ").append("\r\n");
			tBuff.append("      beforeSend: setRequestHeaders, ").append("\r\n");
			tBuff.append("      success: onSuccess, ").append("\r\n");
			tBuff.append("      error: onError ").append("\r\n");
			tBuff.append("  }); ").append("\r\n");
			tBuff.append("</script> ").append("\r\n");
			tBuff.append("============================ JQUERY-TEST SRC END ============================").append("\r\n");
			System.out.println(tBuff);

		}

		/**
		 * RestFul Web Service - JAVA 용 테스트 소스코드를 생성하는 메소드.
		 * 
		 * @param strWSPath (WS Path. 필수.)
		 * @param strMethodKind (메소드종류:PUT/POST/DELETE. 필수.)
		 * @param strMethodName (메소드명. 필수.)
		 * @param strWsVoPackageName (웹서비스VO 패키지명-파라메터. 필수.)
		 * @param strWsVoName (웹서비스VO 명-파라메터. 필수.)
		 * @param CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.)
		 * @param strMediaType (미디어타입:XML/JSON). 필수.) 예) (new net.dstone.common.tools.BizGenerator()).new WsGen().genWsTestSrcForJava("/Event", "PUT", "listEvent", "com.test.biz.event.vo", "EventListWsVo", 0, "XML");
		 */
		private void genTestSrcForWsJava(String strWSPath, String strMethodKind, String strMethodName, String strWsVoPackageName, String strWsVoName, int CRUD, String strMediaType) {

			debug("||=== genTestSrcForWsJava(RestFul Web Service - JAVA 용 테스트 소스코드를 생성하는 메소드) ===||");
			debug("strWSPath (WS Path. 필수.) [" + strWSPath + "]");
			debug("strMethodKind (메소드종류:PUT/POST/DELETE. 필수.) [" + strMethodKind + "]");
			debug("strMethodName (메소드명. 필수.) [" + strMethodName + "]");
			debug("strWsVoPackageName (웹서비스VO 패키지명-파라메터. 필수.) [" + strWsVoPackageName + "]");
			debug("strWsVoName (웹서비스VO 명-파라메터. 필수.) [" + strWsVoName + "]");
			debug("CRUD (트랜젝션종류 - 0:다건조회, 1:단건조회, 2:입력, 3:수정, 4:삭제.) [" + CRUD + "]");
			debug("strMediaType (미디어타입:XML/JSON). 필수.) [" + strMediaType + "]");
			debug("||====================================================================================||");

			StringBuffer tBuff = new StringBuffer("\r\n");
			String testClassName = "HTTP4eFor" + strMethodName.substring(0, 1).toUpperCase() + strMethodName.substring(1);
			if ("XML".equals(strMediaType)) {
				testClassName = testClassName + "ForXml";
			} else if ("JSON".equals(strMediaType)) {
				testClassName = testClassName + "ForJson";
			}
			tBuff.append("============================ JAVA-TEST SRC START ============================").append("\r\n");
			tBuff.append("import java.util.*; ").append("\r\n");
			tBuff.append("import org.apache.commons.httpclient.*; ").append("\r\n");
			tBuff.append("import org.apache.commons.httpclient.methods.*; ").append("\r\n");
			tBuff.append(" ").append("\r\n");
			tBuff.append("public class " + testClassName + " { ").append("\r\n");
			tBuff.append(" ").append("\r\n");
			tBuff.append("    /** ").append("\r\n");
			tBuff.append("     * The main method contains your HTTP4e data. You should modify the client here. ").append("\r\n");
			tBuff.append("     */ ").append("\r\n");
			tBuff.append("    public static void main(String[] args) { ").append("\r\n");
			tBuff.append("        " + strWsVoPackageName + "." + strWsVoName + " bean = new " + strWsVoPackageName + "." + strWsVoName + "(); ").append("\r\n");
			tBuff.append("        net.dstone.common.utils.WsUtil wsUtil = new net.dstone.common.utils.WsUtil(); ").append("\r\n");
			tBuff.append("        ").append("\r\n");
			tBuff.append("        try {").append("\r\n");
			tBuff.append("            wsUtil.bean = new net.dstone.common.utils.WsUtil.Bean(); ").append("\r\n");
			tBuff.append("            wsUtil.bean.method = \"" + strMethodKind + "\"; ").append("\r\n");
			tBuff.append("            wsUtil.bean.url = \"" + WS_WEB_URL + (WEB_CONTEXT_ROOT.equals("/")?"":WEB_CONTEXT_ROOT) + "" + WS_ROOT_PATH + "" + strWSPath + "/" + strMethodName + "\"; ").append("\r\n");
			tBuff.append("            /*** bean 파라메터 세팅 시작 ***/ ").append("\r\n");
			tBuff.append("            //bean.setXXX(\"XXX\"); ").append("\r\n");
			tBuff.append("            /*** bean 파라메터 세팅 끝 ***/ ").append("\r\n");
			
			if ("XML".equals(strMediaType)) {
				tBuff.append("            wsUtil.bean.body = net.dstone.common.utils.BeanUtil.toXml(bean);").append("\n");
				tBuff.append("            wsUtil.bean.addHeader(\"Content-Type\", \"application/xml; charset="+CHARSET+"\"); ").append("\r\n");
				tBuff.append("            String outStr = wsUtil.execute(wsUtil.bean, \""+CHARSET+"\"); ").append("\r\n");
				tBuff.append("            bean = (" + strWsVoPackageName + "." + strWsVoName + ")net.dstone.common.utils.BeanUtil.fromXml(outStr, " + strWsVoPackageName + "." + strWsVoName + ".class); ").append("\r\n");
			} else if ("JSON".equals(strMediaType)) {
				tBuff.append("            wsUtil.bean.body = net.dstone.common.utils.BeanUtil.toJson(bean);").append("\n");
				tBuff.append("            wsUtil.bean.addHeader(\"Content-Type\", \"application/json; charset="+CHARSET+"\"); ").append("\r\n");
				tBuff.append("            String outStr = wsUtil.execute(wsUtil.bean, \""+CHARSET+"\" ); ").append("\r\n");
				tBuff.append("            bean = (" + strWsVoPackageName + "." + strWsVoName + ")net.dstone.common.utils.BeanUtil.fromJson(outStr, " + strWsVoPackageName + "." + strWsVoName + ".class); ").append("\r\n");
			}
			
			tBuff.append("            System.out.println(\"||========================== bean ==========================||\"); ").append("\r\n");
			tBuff.append("            System.out.println(bean); ").append("\r\n");
			tBuff.append("            System.out.println(\"||========================== bean ==========================||\"); ").append("\r\n");
			tBuff.append("        } catch (Exception e) {").append("\r\n");
			tBuff.append("        	e.printStackTrace();").append("\r\n");
			tBuff.append("        } ").append("\r\n");
			tBuff.append("    } ").append("\r\n");
			tBuff.append("} ").append("\r\n");
			tBuff.append("============================ JAVA-TEST SRC END ============================").append("\r\n");
			System.out.println(tBuff);

		}
	}

	public static class Util {
		/**
		 * Ajax 파일업로드 시 사용하는 JS소스(ajaxfileupload.js) 생성하는 메소드. 예) net.dstone.common.tools.BizGenerator.Util.genAjaxFileUploadJs();
		 */
		public static void genAjaxFileUploadJs() {
			StringBuffer jsBuff = new StringBuffer();
			jsBuff.append("jQuery.extend({ ").append("\n");
			jsBuff.append("    createUploadIframe: function(id, uri) ").append("\n");
			jsBuff.append("	{ ").append("\n");
			jsBuff.append("			//create frame ").append("\n");
			jsBuff.append("            var frameId = 'jUploadFrame' + id; ").append("\n");
			jsBuff.append("            var iframeHtml = '<iframe id=\"' + frameId + '\" name=\"' + frameId + '\" style=\"position:absolute; top:-9999px; left:-9999px\"'; ").append("\n");
			jsBuff.append("			if(window.ActiveXObject) ").append("\n");
			jsBuff.append("			{ ").append("\n");
			jsBuff.append("                if(typeof uri== 'boolean'){ ").append("\n");
			jsBuff.append("					iframeHtml += ' src=\"' + 'javascript:false' + '\"'; ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("                } ").append("\n");
			jsBuff.append("                else if(typeof uri== 'string'){ ").append("\n");
			jsBuff.append("					iframeHtml += ' src=\"' + uri + '\"'; ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("                } ").append("\n");
			jsBuff.append("			} ").append("\n");
			jsBuff.append("			iframeHtml += ' />'; ").append("\n");
			jsBuff.append("			jQuery(iframeHtml).appendTo(document.body); ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("            return jQuery('#' + frameId).get(0); ").append("\n");
			jsBuff.append("    }, ").append("\n");
			jsBuff.append("    createUploadForm: function(id, fileElementId, data) ").append("\n");
			jsBuff.append("	{ ").append("\n");
			jsBuff.append("		//create form ").append("\n");
			jsBuff.append("		var formId = 'jUploadForm' + id; ").append("\n");
			jsBuff.append("		var fileId = 'jUploadFile' + id; ").append("\n");
			jsBuff.append("		var form = jQuery('<form  action=\"\" method=\"POST\" name=\"' + formId + '\" id=\"' + formId + '\" enctype=\"multipart/form-data\"></form>'); ").append("\n");
			jsBuff.append("		if(data) ").append("\n");
			jsBuff.append("		{ ").append("\n");
			jsBuff.append("			for(var i in data) ").append("\n");
			jsBuff.append("			{ ").append("\n");
			jsBuff.append("				jQuery('<input type=\"hidden\" name=\"' + i + '\" value=\"' + data[i] + '\" />').appendTo(form); ").append("\n");
			jsBuff.append("			} ").append("\n");
			jsBuff.append("		} ").append("\n");
			jsBuff.append("		var oldElement = jQuery('#' + fileElementId); ").append("\n");
			jsBuff.append("		var newElement = jQuery(oldElement).clone(); ").append("\n");
			jsBuff.append("		jQuery(oldElement).attr('id', fileId); ").append("\n");
			jsBuff.append("		jQuery(oldElement).before(newElement); ").append("\n");
			jsBuff.append("		jQuery(oldElement).appendTo(form); ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("		//set attributes ").append("\n");
			jsBuff.append("		jQuery(form).css('position', 'absolute'); ").append("\n");
			jsBuff.append("		jQuery(form).css('top', '-1200px'); ").append("\n");
			jsBuff.append("		jQuery(form).css('left', '-1200px'); ").append("\n");
			jsBuff.append("		jQuery(form).appendTo('body'); ").append("\n");
			jsBuff.append("		return form; ").append("\n");
			jsBuff.append("    }, ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("    ajaxFileUpload: function(s) { ").append("\n");
			jsBuff.append("        // TODO introduce global settings, allowing the client to modify them for all requests, not only timeout ").append("\n");
			jsBuff.append("        s = jQuery.extend({}, jQuery.ajaxSettings, s); ").append("\n");
			jsBuff.append("        var id = new Date().getTime() ").append("\n");
			jsBuff.append("		var form = jQuery.createUploadForm(id, s.fileElementId, (typeof(s.data)=='undefined'?false:s.data)); ").append("\n");
			jsBuff.append("		var io = jQuery.createUploadIframe(id, s.secureuri); ").append("\n");
			jsBuff.append("		var frameId = 'jUploadFrame' + id; ").append("\n");
			jsBuff.append("		var formId = 'jUploadForm' + id; ").append("\n");
			jsBuff.append("        // Watch for a new set of requests ").append("\n");
			jsBuff.append("        if ( s.global && ! jQuery.active++ ) ").append("\n");
			jsBuff.append("		{ ").append("\n");
			jsBuff.append("			jQuery.event.trigger( \"ajaxStart\" ); ").append("\n");
			jsBuff.append("		} ").append("\n");
			jsBuff.append("        var requestDone = false; ").append("\n");
			jsBuff.append("        // Create the request object ").append("\n");
			jsBuff.append("        var xml = {} ").append("\n");
			jsBuff.append("        if ( s.global ) ").append("\n");
			jsBuff.append("            jQuery.event.trigger(\"ajaxSend\", [xml, s]); ").append("\n");
			jsBuff.append("        // Wait for a response to come back ").append("\n");
			jsBuff.append("        var uploadCallback = function(isTimeout) ").append("\n");
			jsBuff.append("		{ ").append("\n");
			jsBuff.append("			var io = document.getElementById(frameId); ").append("\n");
			jsBuff.append("            try ").append("\n");
			jsBuff.append("			{ ").append("\n");
			jsBuff.append("				if(io.contentWindow) ").append("\n");
			jsBuff.append("				{ ").append("\n");
			jsBuff.append("					 xml.responseText = io.contentWindow.document.body?io.contentWindow.document.body.innerHTML:null; ").append("\n");
			jsBuff.append("                	 xml.responseXML = io.contentWindow.document.XMLDocument?io.contentWindow.document.XMLDocument:io.contentWindow.document; ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("				}else if(io.contentDocument) ").append("\n");
			jsBuff.append("				{ ").append("\n");
			jsBuff.append("					 xml.responseText = io.contentDocument.document.body?io.contentDocument.document.body.innerHTML:null; ").append("\n");
			jsBuff.append("                	xml.responseXML = io.contentDocument.document.XMLDocument?io.contentDocument.document.XMLDocument:io.contentDocument.document; ").append("\n");
			jsBuff.append("				} ").append("\n");
			jsBuff.append("            }catch(e) ").append("\n");
			jsBuff.append("			{ ").append("\n");
			jsBuff.append("				jQuery.handleError(s, xml, null, e); ").append("\n");
			jsBuff.append("			} ").append("\n");
			jsBuff.append("            if ( xml || isTimeout == \"timeout\") ").append("\n");
			jsBuff.append("			{ ").append("\n");
			jsBuff.append("                requestDone = true; ").append("\n");
			jsBuff.append("                var status; ").append("\n");
			jsBuff.append("                try { ").append("\n");
			jsBuff.append("                    status = isTimeout != \"timeout\" ? \"success\" : \"error\"; ").append("\n");
			jsBuff.append("                    // Make sure that the request was successful or notmodified ").append("\n");
			jsBuff.append("                    if ( status != \"error\" ) ").append("\n");
			jsBuff.append("					{ ").append("\n");
			jsBuff.append("                        // process the data (runs the xml through httpData regardless of callback) ").append("\n");
			jsBuff.append("                        var data = jQuery.uploadHttpData( xml, s.dataType ); ").append("\n");
			jsBuff.append("                        // If a local callback was specified, fire it and pass it the data ").append("\n");
			jsBuff.append("                        if ( s.success ) ").append("\n");
			jsBuff.append("                            s.success( data, status ); ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("                        // Fire the global callback ").append("\n");
			jsBuff.append("                        if( s.global ) ").append("\n");
			jsBuff.append("                            jQuery.event.trigger( \"ajaxSuccess\", [xml, s] ); ").append("\n");
			jsBuff.append("                    } else ").append("\n");
			jsBuff.append("                        jQuery.handleError(s, xml, status); ").append("\n");
			jsBuff.append("                } catch(e) ").append("\n");
			jsBuff.append("				{ ").append("\n");
			jsBuff.append("                    status = \"error\"; ").append("\n");
			jsBuff.append("                    jQuery.handleError(s, xml, status, e); ").append("\n");
			jsBuff.append("                } ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("                // The request was completed ").append("\n");
			jsBuff.append("                if( s.global ) ").append("\n");
			jsBuff.append("                    jQuery.event.trigger( \"ajaxComplete\", [xml, s] ); ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("                // Handle the global AJAX counter ").append("\n");
			jsBuff.append("                if ( s.global && ! --jQuery.active ) ").append("\n");
			jsBuff.append("                    jQuery.event.trigger( \"ajaxStop\" ); ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("                // Process result ").append("\n");
			jsBuff.append("                if ( s.complete ) ").append("\n");
			jsBuff.append("                    s.complete(xml, status); ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("                jQuery(io).unbind() ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("                setTimeout(function() ").append("\n");
			jsBuff.append("									{	try ").append("\n");
			jsBuff.append("										{ ").append("\n");
			jsBuff.append("											jQuery(io).remove(); ").append("\n");
			jsBuff.append("											jQuery(form).remove(); ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("										} catch(e) ").append("\n");
			jsBuff.append("										{ ").append("\n");
			jsBuff.append("											jQuery.handleError(s, xml, null, e); ").append("\n");
			jsBuff.append("										} ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("									}, 100) ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("                xml = null ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("            } ").append("\n");
			jsBuff.append("        } ").append("\n");
			jsBuff.append("        // Timeout checker ").append("\n");
			jsBuff.append("        if ( s.timeout > 0 ) ").append("\n");
			jsBuff.append("		{ ").append("\n");
			jsBuff.append("            setTimeout(function(){ ").append("\n");
			jsBuff.append("                // Check to see if the request is still happening ").append("\n");
			jsBuff.append("                if( !requestDone ) uploadCallback( \"timeout\" ); ").append("\n");
			jsBuff.append("            }, s.timeout); ").append("\n");
			jsBuff.append("        } ").append("\n");
			jsBuff.append("        try ").append("\n");
			jsBuff.append("		{ ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("			var form = jQuery('#' + formId); ").append("\n");
			jsBuff.append("			jQuery(form).attr('action', s.url); ").append("\n");
			jsBuff.append("			jQuery(form).attr('method', 'POST'); ").append("\n");
			jsBuff.append("			jQuery(form).attr('target', frameId); ").append("\n");
			jsBuff.append("            if(form.encoding) ").append("\n");
			jsBuff.append("			{ ").append("\n");
			jsBuff.append("				jQuery(form).attr('encoding', 'multipart/form-data'); ").append("\n");
			jsBuff.append("            } ").append("\n");
			jsBuff.append("            else ").append("\n");
			jsBuff.append("			{ ").append("\n");
			jsBuff.append("				jQuery(form).attr('enctype', 'multipart/form-data'); ").append("\n");
			jsBuff.append("            } ").append("\n");
			jsBuff.append("            jQuery(form).submit(); ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("        } catch(e) ").append("\n");
			jsBuff.append("		{ ").append("\n");
			jsBuff.append("            jQuery.handleError(s, xml, null, e); ").append("\n");
			jsBuff.append("        } ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("		jQuery('#' + frameId).load(uploadCallback	); ").append("\n");
			jsBuff.append("        return {abort: function () {}}; ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("    }, ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("    uploadHttpData: function( r, type ) { ").append("\n");
			jsBuff.append("        var data = !type; ").append("\n");
			jsBuff.append("        data = type == \"xml\" || data ? r.responseXML : r.responseText; ").append("\n");
			jsBuff.append("        // If the type is \"script\", eval it in global context ").append("\n");
			jsBuff.append("        if ( type == \"script\" ) ").append("\n");
			jsBuff.append("            jQuery.globalEval( data ); ").append("\n");
			jsBuff.append("        // Get the JavaScript object, if JSON is used. ").append("\n");
			jsBuff.append("        if ( type == \"json\" ) ").append("\n");
			jsBuff.append("            eval( \"data = \" + data ); ").append("\n");
			jsBuff.append("        // evaluate scripts within html ").append("\n");
			jsBuff.append("        if ( type == \"html\" ) ").append("\n");
			jsBuff.append("            jQuery(\"<div>\").html(data).evalScripts(); ").append("\n");
			jsBuff.append(" ").append("\n");
			jsBuff.append("        return data; ").append("\n");
			jsBuff.append("    } ").append("\n");
			jsBuff.append("}) ").append("\n");
			jsBuff.append(" ").append("\n");

			StringBuffer jspBuff = new StringBuffer();
			jspBuff.append("<%@ page language=\"java\" contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\"%> ").append("\n");
			jspBuff.append("<% ").append("\n");
			jspBuff.append("/******************************************* 변수 선언 시작 *******************************************/ ").append("\n");
			jspBuff.append(" ").append("\n");
			jspBuff.append("/******************************************* 변수 선언 끝 *********************************************/ ").append("\n");
			jspBuff.append(" ").append("\n");
			jspBuff.append("/******************************************* 변수 정의 시작 *******************************************/ ").append("\n");
			jspBuff.append(" ").append("\n");
			jspBuff.append("/******************************************* 변수 정의 끝 *********************************************/ ").append("\n");
			jspBuff.append("%> ").append("\n");
			jspBuff.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\"> ").append("\n");
			jspBuff.append("<html> ").append("\n");
			jspBuff.append("<head> ").append("\n");
			jspBuff.append("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=8\" /> ").append("\n");
			jspBuff.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"> ").append("\n");
			jspBuff.append("<title>Insert title here</title> ").append("\n");
			jspBuff.append(" ").append("\n");
			jspBuff.append("<script type=\"text/javascript\" src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js\"></script> ").append("\n");
			jspBuff.append("<script type=\"text/javascript\" src=\"http://www.x-non.com/json/jquery.json-2.4.min.js\" ></script> ").append("\n");

			jspBuff.append("\n");
			jspBuff.append("<!-- ajaxfileupload.js START --> ").append("\n");
			jspBuff.append("<script type=\"text/javascript\"> ").append("\n");
			jspBuff.append(jsBuff.toString()).append("\n");
			jspBuff.append("</script> ").append("\n");
			jspBuff.append("<!-- ajaxfileupload.js END --> ").append("\n");
			jspBuff.append("\n");

			jspBuff.append("<script type=\"text/javascript\"> ").append("\n");
			jspBuff.append(" ").append("\n");
			jspBuff.append("	function ajaxFileUpload(fileId) { ").append("\n");
			jspBuff.append("		$.ajaxFileUpload({ ").append("\n");
			jspBuff.append("			url : '/sample/view/fileUpJson.do', ").append("\n");
			jspBuff.append("			secureuri : false, ").append("\n");
			jspBuff.append("			fileElementId : fileId, ").append("\n");
			jspBuff.append("			dataType : 'json', ").append("\n");
			jspBuff.append("			beforeSend : function() { ").append("\n");
			jspBuff.append("				//$(\"#loading\").show(); ").append("\n");
			jspBuff.append("			}, ").append("\n");
			jspBuff.append("			complete : function() { ").append("\n");
			jspBuff.append("				//$(\"#loading\").hide(); ").append("\n");
			jspBuff.append("				alert('complete !!!'); ").append("\n");
			jspBuff.append("			}, ").append("\n");
			jspBuff.append("			success : function(data, status) { ").append("\n");
			jspBuff.append("				alert( status); ").append("\n");
			jspBuff.append("				if(status == 'success'){ ").append("\n");
			jspBuff.append("					alert( 'fileId['+data.fileId+'] originalFileName['+data.originalFileName+'] savedFileName['+data.savedFileName+'] fileSize['+data.fileSize+']' ); ").append("\n");
			jspBuff.append("				} ").append("\n");
			jspBuff.append("			}, ").append("\n");
			jspBuff.append("			error : function(data, status, e) { ").append("\n");
			jspBuff.append("				alert(e); ").append("\n");
			jspBuff.append("			} ").append("\n");
			jspBuff.append("		}); ").append("\n");
			jspBuff.append("		//return false; ").append("\n");
			jspBuff.append("	} ").append("\n");
			jspBuff.append(" ").append("\n");
			jspBuff.append("</script> ").append("\n");
			jspBuff.append(" ").append("\n");
			jspBuff.append("</head> ").append("\n");
			jspBuff.append("<body> ").append("\n");
			jspBuff.append("	<table border=1 > ").append("\n");
			jspBuff.append("		<!--폼 시작--> ").append("\n");
			jspBuff.append("		<form name=\"MAIN_FORM\" method=\"post\" action=\"\" enctype=\"multipart/form-data\" > ").append("\n");
			jspBuff.append("		<tr> ").append("\n");
			jspBuff.append("			<td>첨부파일1</td> ").append("\n");
			jspBuff.append("			<td><input type=\"file\" name=\"file1\" id=\"file1\" value=\"file1\"><a href=\"javascript:ajaxFileUpload('file1');\" >GO</a></td> ").append("\n");
			jspBuff.append("		</tr> ").append("\n");
			jspBuff.append("		<tr> ").append("\n");
			jspBuff.append("			<td>첨부파일2</td> ").append("\n");
			jspBuff.append("			<td><input type=\"file\" name=\"file2\" id=\"file2\" value=\"file2\"><a href=\"javascript:ajaxFileUpload('file2');\" >GO</a></td> ").append("\n");
			jspBuff.append("		</tr> ").append("\n");
			jspBuff.append("		</form> ").append("\n");
			jspBuff.append("		<!--폼 끝--> ").append("\n");
			jspBuff.append("	</table> ").append("\n");
			jspBuff.append("</body> ").append("\n");
			jspBuff.append("</html> ").append("\n");

			StringBuffer ctrlBuff = new StringBuffer();
			ctrlBuff.append("    /** ").append("\n");
			ctrlBuff.append("     * 파일업로드 ").append("\n");
			ctrlBuff.append("     * @param request ").append("\n");
			ctrlBuff.append("     * @param model ").append("\n");
			ctrlBuff.append("     * @return ").append("\n");
			ctrlBuff.append("     */ ").append("\n");
			ctrlBuff.append("    @RequestMapping(value = \"/fileUpJson.do\") ").append("\n");
			ctrlBuff.append("    public ModelAndView fileUpJson(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, ModelAndView mav) { ").append("\n");
			ctrlBuff.append(" ").append("\n");
			ctrlBuff.append("   		/************************ 세션체크 시작 ************************/ ").append("\n");
			ctrlBuff.append(" ").append("\n");
			ctrlBuff.append("   		/************************ 세션체크 끝 **************************/ ").append("\n");
			ctrlBuff.append(" ").append("\n");
			ctrlBuff.append("   		/************************ 변수 선언 시작 ************************/ ").append("\n");
			ctrlBuff.append("   		net.dstone.common.utils.RequestUtil 					requestUtil; ").append("\n");
			ctrlBuff.append("   		/************************ 변수 선언 끝 **************************/ ").append("\n");
			ctrlBuff.append("   		try { ").append("\n");
			ctrlBuff.append("   			/************************ 변수 정의 시작 ************************/ ").append("\n");
			ctrlBuff.append("   			requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response); ").append("\n");
			ctrlBuff.append("   			/************************ 변수 정의 끝 ************************/ ").append("\n");
			ctrlBuff.append(" ").append("\n");
			ctrlBuff.append("   			/************************ 컨트롤러 로직 시작 ************************/ ").append("\n");
			ctrlBuff.append("   			mav.addObject(\"fileId\", requestUtil.getSavedFileName(0).substring(0, requestUtil.getSavedFileName(0).lastIndexOf(\".\"))); ").append("\n");
			ctrlBuff.append("   			mav.addObject(\"originalFileName\", requestUtil.getOriginalFileName(0)); ").append("\n");
			ctrlBuff.append("   			mav.addObject(\"savedFileName\", requestUtil.getSavedFileName(0)); ").append("\n");
			ctrlBuff.append("   			mav.addObject(\"fileSize\", requestUtil.getFileSize(0)); ").append("\n");
			ctrlBuff.append("   			mav.setViewName(\"jsonView\"); ").append("\n");
			ctrlBuff.append("   			/************************ 컨트롤러 로직 끝 ************************/ ").append("\n");
			ctrlBuff.append(" ").append("\n");
			ctrlBuff.append("   		} catch (Exception e) { ").append("\n");
			ctrlBuff.append("   			handleException(request, response, e); return null; ").append("\n");
			ctrlBuff.append("   		} ").append("\n");
			ctrlBuff.append("   		return mav; ").append("\n");
			ctrlBuff.append("    } ").append("\n");

			System.out.println("============================ JSP SRC START ============================");
			System.out.println(jspBuff);
			System.out.println("============================ JSP SRC END ============================");
			System.out.println("");
			System.out.println("============================ CTRL SRC START ============================");
			System.out.println(ctrlBuff);
			System.out.println("============================ CTRL SRC END ============================");

		}
		public static net.dstone.common.utils.DataSet getCols(String TABLE_NAME) {
			net.dstone.common.utils.DbUtil db = null;
			net.dstone.common.utils.DataSet ds = null;
			String[] colNames = null;
			String[] colTypes = null;
			StringBuffer sql = new StringBuffer();
			java.util.HashMap<String, java.util.Properties> colInfo = null;
			java.util.Properties colInfoItem = new java.util.Properties();

			try {
				db = new net.dstone.common.utils.DbUtil(DBID);
				db.getConnection();
				
				// 1. 컬럼 기본정보 조회.
				if ("ORACLE".equals(db.currentDbKind)) {
					sql.append("SELECT  ").append("\n");
					sql.append("    C.TABLE_NAME ").append("\n");
					sql.append("    , C.COLUMN_NAME ").append("\n");
					sql.append("    , D.COMMENTS COLUMN_COMMENT ").append("\n");
					sql.append("    , C.DATA_TYPE ").append("\n");
					sql.append("    , C.DATA_LENGTH ").append("\n");
					sql.append("FROM  ").append("\n");
					sql.append("    USER_TAB_COLUMNS C ").append("\n");
					sql.append("    , USER_COL_COMMENTS D ").append("\n");
					sql.append("WHERE 1=1 ").append("\n");
					sql.append("    AND C.TABLE_NAME = D.TABLE_NAME ").append("\n");
					sql.append("    AND C.COLUMN_NAME = D.COLUMN_NAME ").append("\n");
					sql.append("	AND C.TABLE_NAME = '" + TABLE_NAME + "' ").append("\n");
				} else if ("MSSQL".equals(db.currentDbKind)) {
					sql.append("SELECT   ").append("\n");
					sql.append("	A.TABLE_NAME  ").append("\n");
					sql.append("	, A.COLUMN_NAME  ").append("\n");
					sql.append("	, '' COLUMN_COMMENT ").append("\n");
					sql.append("	, A.DATA_TYPE  ").append("\n");
					sql.append("	, ISNULL(CAST(A.CHARACTER_MAXIMUM_LENGTH AS VARCHAR), CAST(A.NUMERIC_PRECISION AS VARCHAR) + ',' + CAST(A.NUMERIC_SCALE AS VARCHAR)) AS DATA_LENGTH  ").append("\n");
					sql.append("FROM   ").append("\n");
					sql.append("	INFORMATION_SCHEMA.COLUMNS A  ").append("\n");
					sql.append("WHERE   ").append("\n");
					sql.append("	A.TABLE_NAME = '" + TABLE_NAME + "'  ").append("\n");
					sql.append("ORDER BY A.TABLE_NAME, A.ORDINAL_POSITION ").append("\n");
				} else if ("MYSQL".equals(db.currentDbKind)) {
					String DB_URL = SystemUtil.getInstance().getProperty(DBID + ".strUrl");
					String DB_SID = "MYDB";
					if(DB_URL.indexOf("/") != -1){
						DB_SID =  DB_URL.substring(DB_URL.lastIndexOf("/")+1);
						if( DB_SID.indexOf("?") != -1 ){
							DB_SID =  DB_SID.substring(0, DB_SID.indexOf("?"));
						}
					}
					sql.append("SELECT ").append("\n"); 
					sql.append("	TABLE_NAME ").append("\n");
					sql.append("	, COLUMN_NAME ").append("\n");
					sql.append("	, COLUMN_COMMENT ").append("\n");
					sql.append("	, DATA_TYPE ").append("\n");
					sql.append("	, (CASE WHEN character_maximum_length IS NULL THEN NUMERIC_PRECISION ELSE character_maximum_length END) AS DATA_LENGTH ").append("\n");
					sql.append("FROM  ").append("\n");
					sql.append("	INFORMATION_SCHEMA.COLUMNS  ").append("\n");
					sql.append("WHERE 1=1 ").append("\n");
					sql.append("	AND TABLE_SCHEMA='"+DB_SID+"'  ").append("\n");
					sql.append("    AND TABLE_NAME='" + TABLE_NAME + "' ").append("\n");
					sql.append("ORDER BY ORDINAL_POSITION ").append("\n");
				}

				db.setQuery(sql.toString());
				ds = new net.dstone.common.utils.DataSet();
				ds.buildFromResultSet(db.select(), "COL_LIST");
				
				sql = new StringBuffer();
				if ("ORACLE".equals(db.currentDbKind)) {
					sql.append(" SELECT * FROM " + TABLE_NAME + " WHERE ROWNUM < 2");
				} else if ("MSSQL".equals(db.currentDbKind)) {
					sql.append(" SELECT TOP 1 * FROM " + TABLE_NAME + " ");
				} else if ("MYSQL".equals(db.currentDbKind)) {
					sql.append(" SELECT  * FROM " + TABLE_NAME + " LIMIT 1 ");
				}
				
				db.setQuery(sql.toString());
				db.select();
				colNames = db.columnNames;
				colTypes = db.columnTypes;
				
				for(int i=0; i<ds.getDataSetRowCount("COL_LIST"); i++){
					for(int k=0; k<colNames.length; k++){
						if(ds.getDataSet("COL_LIST", i).getDatum("COLUMN_NAME").equals(colNames[k])){
							ds.getDataSet("COL_LIST", i).setDatum("DATA_TYPE", colTypes[k].trim());
							break;
						}
					}
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				db.release();
			}
			return ds;
		}
		
		public static net.dstone.common.utils.DataSet getKeys(String TABLE_NAME) {
			net.dstone.common.utils.DbUtil db = null;
			net.dstone.common.utils.DataSet ds = null;
			StringBuffer keySql = new StringBuffer();

			try {
				db = new net.dstone.common.utils.DbUtil(DBID);
				db.getConnection();
				
				if ("ORACLE".equals(db.currentDbKind)) {
					keySql.append("SELECT COLS.TABLE_NAME, COLS.COLUMN_NAME, COLS.POSITION, CONS.STATUS, CONS.OWNER ").append("\n");
					keySql.append("FROM ALL_CONSTRAINTS CONS, ALL_CONS_COLUMNS COLS ").append("\n");
					keySql.append("WHERE COLS.TABLE_NAME = '" + TABLE_NAME + "' ").append("\n");
					keySql.append("AND CONS.CONSTRAINT_TYPE = 'P' ").append("\n");
					keySql.append("AND CONS.CONSTRAINT_NAME = COLS.CONSTRAINT_NAME ").append("\n");
					keySql.append("AND CONS.OWNER = COLS.OWNER ").append("\n");
					keySql.append("ORDER BY COLS.TABLE_NAME, COLS.POSITION ").append("\n");
				} else if ("MSSQL".equals(db.currentDbKind)) {
					keySql.append("SELECT KU.TABLE_NAME AS TABLENAME,COLUMN_NAME AS COLUMN_NAME ").append("\n");
					keySql.append("FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS AS TC ").append("\n");
					keySql.append("INNER JOIN ").append("\n");
					keySql.append("INFORMATION_SCHEMA.KEY_COLUMN_USAGE AS KU ").append("\n");
					keySql.append("ON TC.CONSTRAINT_TYPE = 'PRIMARY KEY' AND ").append("\n");
					keySql.append("TC.CONSTRAINT_NAME = KU.CONSTRAINT_NAME ").append("\n");
					keySql.append("AND KU.TABLE_NAME='" + TABLE_NAME + "' ").append("\n");
					keySql.append("ORDER BY KU.TABLE_NAME, KU.ORDINAL_POSITION ").append("\n");
				} else if ("MYSQL".equals(db.currentDbKind)) {
					String DB_URL = SystemUtil.getInstance().getProperty(DBID + ".strUrl");
					String DB_SID = "MYDB";
					if(DB_URL.indexOf("/") != -1){
						DB_SID =  DB_URL.substring(DB_URL.lastIndexOf("/")+1);
						if(DB_SID.indexOf("?") != -1){
							DB_SID = DB_SID.substring(0, DB_SID.lastIndexOf("?"));
						}
					}
					keySql.append("SELECT ").append("\n"); 
					keySql.append("	   TABLE_NAME, COLUMN_NAME ").append("\n");
					keySql.append("FROM  ").append("\n");
					keySql.append("	   INFORMATION_SCHEMA.COLUMNS  ").append("\n");
					keySql.append("WHERE 1=1 ").append("\n");
					keySql.append("	   AND COLUMN_KEY = 'PRI'  ").append("\n");
					keySql.append("	   AND TABLE_SCHEMA='" + DB_SID + "'  ").append("\n");
					keySql.append("    AND TABLE_NAME='" + TABLE_NAME + "' ").append("\n");
					keySql.append("ORDER BY ORDINAL_POSITION ").append("\n");

				}
				db.setQuery(keySql.toString());
				ds = new net.dstone.common.utils.DataSet();
				ds.buildFromResultSet(db.select(), "KEY_LIST");

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				db.release();
			}
			return ds;
		}
		
		public static String getParamByType(DbInfo.ColInfo col, String dbKind){
			String outStr = "";
			if ("ORACLE".equals(dbKind)) {
				if (col.DATA_TYPE.equals("float") || col.DATA_TYPE.equals("int") ||  col.DATA_TYPE.equals("double")) {
					outStr = "#{" + col.COLUMN_NAME + "}";
				}else if (col.DATA_TYPE.equals("java.sql.Date") || col.DATA_TYPE.equals("java.sql.Time") ||  col.DATA_TYPE.equals("java.sql.Timestamp")) {
					if ( col.DATA_TYPE.equals("java.sql.Date") ) {
						outStr = "TO_DATE( #{" + col.COLUMN_NAME + "}, 'YYYYMMDDHH24MISS')";
					}else if ( col.DATA_TYPE.equals("java.sql.Time") ) {
						outStr = "TO_DATE( #{" + col.COLUMN_NAME + "}, 'YYYYMMDDHH24MISS')";
					}else if ( col.DATA_TYPE.equals("java.sql.Timestamp") ) {
						outStr = "TO_DATE( #{" + col.COLUMN_NAME + "}, 'YYYYMMDDHH24MISSFF3')";
					}
				} else {
					outStr = "#{" + col.COLUMN_NAME + "}";
				}
			} else if ("MSSQL".equals(dbKind)) {
				if (col.DATA_TYPE.equals("float") || col.DATA_TYPE.equals("int") ||  col.DATA_TYPE.equals("double")) {
					outStr = "#{" + col.COLUMN_NAME + "}";
				}else if (col.DATA_TYPE.equals("java.sql.Date") || col.DATA_TYPE.equals("java.sql.Time") ||  col.DATA_TYPE.equals("java.sql.Timestamp")) {
					outStr = "CONVERT(DATETIME, #{" + col.COLUMN_NAME + "} )";
				} else {
					outStr = "#{" + col.COLUMN_NAME + "}";
				}
			} else if ("MYSQL".equals(dbKind)) {
				if (col.DATA_TYPE.equals("float") || col.DATA_TYPE.equals("int") ||  col.DATA_TYPE.equals("double")) {
					outStr = "#{" + col.COLUMN_NAME + "}";
				}else if (col.DATA_TYPE.equals("java.sql.Date") || col.DATA_TYPE.equals("java.sql.Time") ||  col.DATA_TYPE.equals("java.sql.Timestamp")) {
					outStr = "STR_TO_DATE( #{" + col.COLUMN_NAME + "}, '%Y%m%d%H%i%s' )";
				} else {
					outStr = "#{" + col.COLUMN_NAME + "}";
				}
			}
			return outStr;
		}
		
		public static String getPagingQuery(String dbKind, int upOrDown){
			String pagingQuery = "";
			StringBuffer pagingUpConts = new StringBuffer();
			StringBuffer pagingLowConts = new StringBuffer();
			
			if ("ORACLE".equals(dbKind)) {
				// 상단 쿼리
				if(upOrDown == 0){

					pagingUpConts.append("		SELECT  ").append("\n");
					pagingUpConts.append("			RNUM ").append("\n");
					pagingUpConts.append("			,P.* ").append("\n");
					pagingUpConts.append("		FROM  ").append("\n");
					pagingUpConts.append("		    (  ").append("\n");
					pagingUpConts.append("		    SELECT  ").append("\n");
					pagingUpConts.append("		        ROWNUM RNUM, P1.*  ").append("\n");
					pagingUpConts.append("		    FROM  ").append("\n");
					pagingUpConts.append("		        (  ").append("\n");
					pagingUpConts.append("                /**********************************************************************************************************************************/ ").append("\n");

					// 하단 쿼리
				}else if(upOrDown == 1){

					pagingLowConts.append("                /**********************************************************************************************************************************/ ").append("\n");
					pagingLowConts.append("		        ) P1 ").append("\n");
					pagingLowConts.append("		    )   P ").append("\n");
					pagingLowConts.append("		WHERE 2>1 ").append("\n");
					pagingLowConts.append("		    AND RNUM <![CDATA[>=]]> #{INT_FROM} ").append("\n");
					pagingLowConts.append("		    AND RNUM <![CDATA[<=]]> #{INT_TO} ").append("\n");
					pagingLowConts.append("		    AND ROWNUM <![CDATA[<=]]> #{PAGE_SIZE} ");

				}
			} else if ("MSSQL".equals(dbKind)) {

				// 상단 쿼리
				if(upOrDown == 0){

					pagingUpConts.append("		SELECT  ").append("\n");
					pagingUpConts.append("			RNUM ").append("\n");
					pagingUpConts.append("			,P.* ").append("\n");
					pagingUpConts.append("		FROM  ").append("\n");
					pagingUpConts.append("		    (  ").append("\n");
					pagingUpConts.append("		    SELECT  ").append("\n");
					pagingUpConts.append("		        /* 페이징의 기준이 되는 KEY값을 반드시 넣어주시기 바랍니다. */  ").append("\n");
					pagingUpConts.append("		        ROW_NUMBER() OVER(ORDER BY @KEY값@ DESC) AS ROWNUM RNUM, P1.*  ").append("\n");
					pagingUpConts.append("		    FROM  ").append("\n");
					pagingUpConts.append("		        (  ").append("\n");
					pagingUpConts.append("                /**********************************************************************************************************************************/ ").append("\n");
				
				// 하단 쿼리
				}else if(upOrDown == 1){

					pagingLowConts.append("                /**********************************************************************************************************************************/ ").append("\n");
					pagingLowConts.append("		        ) P1 ").append("\n");
					pagingLowConts.append("		    )   P ").append("\n");
					pagingLowConts.append("		WHERE 2>1 ").append("\n");
					pagingLowConts.append("		    AND RNUM <![CDATA[>=]]> #{INT_FROM} ").append("\n");
					pagingLowConts.append("		    AND RNUM <![CDATA[<=]]> #{INT_TO} ").append("\n");
					pagingLowConts.append("		    AND ROWNUM <![CDATA[<=]]> #{PAGE_SIZE} ");

				}
			} else if ("MYSQL".equals(dbKind)) {
				// 상단 쿼리
				if(upOrDown == 0){

					pagingUpConts.append("		SELECT  ").append("\n");
					pagingUpConts.append("			P.* ").append("\n");
					pagingUpConts.append("		FROM  ").append("\n");
					pagingUpConts.append("		    (  ").append("\n");
					pagingUpConts.append("            /**********************************************************************************************************************************/ ").append("\n");
				
				// 하단 쿼리
				}else if(upOrDown == 1){

					pagingLowConts.append("            /**********************************************************************************************************************************/ ").append("\n");
					pagingLowConts.append("		    )   P ").append("\n");
					pagingLowConts.append("		WHERE 2>1 ").append("\n");
					pagingLowConts.append("		    /* 페이징의 기준이 되는 KEY값을 반드시 넣어주시기 바랍니다. */  ").append("\n");
					pagingLowConts.append("		ORDER BY @KEY값@ LIMIT #{INT_FROM}, #{INT_TO} ").append("\n");

				}
			}
			
			// 상단 쿼리
			if(upOrDown == 0){
				pagingQuery = pagingUpConts.toString();
			// 하단 쿼리
			}else if(upOrDown == 1){
				pagingQuery = pagingLowConts.toString();
			}

			return pagingQuery;
		}

	}
}
