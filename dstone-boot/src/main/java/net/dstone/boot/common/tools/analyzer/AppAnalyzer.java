package net.dstone.boot.common.tools.analyzer;

import java.util.HashMap;

import net.dstone.common.core.BaseObject;
import net.dstone.boot.common.tools.analyzer.svc.SvcAnalyzer;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.utils.XmlUtil;

public class AppAnalyzer extends BaseObject{

	private static AppAnalyzer analizer = null;
	
	public static XmlUtil CONF = null; 
	public static boolean CONF_CHANGED = false; 

	/**
	 * DBID
	 */
	public static String DBID;
	/**
	 * 프로젝트 루트 디렉토리
	 */
	public static String ROOT_PATH;
	/**
	 * 클래스 루트 디렉토리
	 */
	public static String CLASS_ROOT_PATH;
	/**
	 * 분석패키지루트 목록(분석대상 패키지 루트. 해당 패키지이하의 모듈만 분석한다.)
	 */
	public static String[] INCLUDE_PACKAGE_ROOT;
	/**
	 * 분석제외패키지패턴 목록(분석제외대상 패키지 패턴. 해당 패키지명이 속하는 패키지는 분석제외한다.)
	 */
	public static String[] EXCLUDE_PACKAGE_PATTERN;
	/**
	 * 쿼리 루트 디렉토리
	 */
	public static String QUERY_ROOT_PATH;
	/**
	 * 웹 루트 디렉토리
	 */
	public static String WEB_ROOT_PATH;
	/**
	 * 중간산출물 저장디렉토리
	 */
	public static String WRITE_PATH;
	/**
	 * 테이블목록을 DB로부터 읽어올지 여부
	 */
	public static boolean IS_TABLE_LIST_FROM_DB;
	/**
	 * 테이블명을 DB로부터 읽어올때 적용할 프리픽스(IS_TABLE_NAME_FROM_DB 가 true일 경우에만 유효)
	 */
	public static String TABLE_NAME_LIKE_STR;
	/**
	 * 작업결과를 DB로 저장할지 여부
	 */
	public static boolean IS_SAVE_TO_DB;

	/**
	 * WORKER_THREAD_NUM - 분석작업을 진행 할 쓰레드 갯수(Fixed 쓰레드풀 일 경우에만 유효)
	 */
	public static int WORKER_THREAD_NUM = 1;

	/**
	 * SAVE_FILE_NAME - 저장파일명(디렉토리 -WRITE_PATH)
	 */
	public static String SAVE_FILE_NAME = "";

	/**
	 * TABLE_LIST_FILE_NAME - 테이블목록정보파일명(디렉토리 -WRITE_PATH)
	 */
	public static String TABLE_LIST_FILE_NAME = "";

	public static HashMap<Integer, String> JOB_KIND_MAP 		= new HashMap<Integer, String>();
	/**
	 * 작업종류 - 클래스파일리스트 에서 패키지ID/클래스ID/클래스명/기능종류 등이 담긴 클래스분석파일리스트 추출
	 */
	public static int JOB_KIND_11_ANALYZE_CLASS          		= 11;
	public static String JOB_KIND_11_ANALYZE_ID_CLASS 	 		= "analyzeClass-Task";
	/**
	 * 작업종류 - 클래스파일리스트 에서 인터페이스구현하위클래스ID목록을 추출하여 클래스분석파일리스트에 추가
	 */
	public static int JOB_KIND_12_ANALYZE_CLASS_IMPL     		= 12;
	public static String JOB_KIND_12_ANALYZE_ID_CLASS_IMPL 		= "analyzeClassImpl-Task";
	/**
	 * 작업종류 - 클래스파일리스트 에서 호출알리아스 추출하여 클래스분석파일리스트에 추가
	 */
	public static int JOB_KIND_13_ANALYZE_CLASS_ALIAS    		= 13;
	public static String JOB_KIND_13_ANALYZE_ID_CLASS_ALIAS    	= "analyzeClassAlias-Task";
	/**
	 * 작업종류 - 쿼리파일리스트 에서 KEY/네임스페이스/쿼리ID/쿼리종류/쿼리내용 등이 담긴 쿼리분석파일리스트 추출
	 */
	public static int JOB_KIND_21_ANALYZE_QUERY          		= 21;
	public static String JOB_KIND_21_ANALYZE_ID_QUERY          	= "analyzeQuery-Task";
	/**
	 * 작업종류 - 쿼리분석파일리스트 에 호출테이블ID정보목록 추가
	 */
	public static int JOB_KIND_22_ANALYZE_QUERY_CALLTBL  		= 22;
	public static String JOB_KIND_22_ANALYZE_ID_QUERY_CALLTBL  	= "analyzeQueryCallTbl-Task";
	/**
	 * 작업종류 - 클래스파일리스트 에서 기능ID/메소드ID/메소드명/메소드URL/메소드내용 등이 담긴 메소드분석파일리스트 추출
	 */
	public static int JOB_KIND_31_ANALYZE_MTD            		= 31;
	public static String JOB_KIND_31_ANALYZE_ID_MTD            	= "analyzeMtd-Task";
	/**
	 * 작업종류 - 메소드분석파일리스트 에 메소드내 타 호출메소드 목록 추가
	 */
	public static int JOB_KIND_32_ANALYZE_MTD_CALLMTD    		= 32;
	public static String JOB_KIND_32_ANALYZE_ID_MTD_CALLMTD    	= "analyzeMtdCallMtd-Task";
	/**
	 * 작업종류 - 메소드분석파일리스트 에 메소드내 호출테이블 목록 추가
	 */
	public static int JOB_KIND_33_ANALYZE_MTD_CALLTBL    		= 33;
	public static String JOB_KIND_33_ANALYZE_ID_MTD_CALLTBL    	= "analyzeMtdCallTbl-Task";
	/**
	 * 작업종류 - UI파일로부터  UI아이디/UI명 등이 담긴 UI분석파일목록 추출
	 */
	public static int JOB_KIND_41_ANALYZE_UI		     		= 41;
	public static String JOB_KIND_41_ANALYZE_ID_UI		     	= "analyzeUi-Task";
	/**
	 * 작업종류 - UI파일로부터 링크정보 추출
	 */
	public static int JOB_KIND_42_ANALYZE_UI_LINK		 		= 42;
	public static String JOB_KIND_42_ANALYZE_ID_UI_LINK		 	= "analyzeUiLink-Task";
	/**
	 * 작업종류 - METRIX 추출
	 */
	public static int JOB_KIND_51_ANALYZE_SAVE_METRIX    		= 51;
	public static String JOB_KIND_51_ANALYZE_ID_SAVE_METRIX    	= "analyzeMetrix-Task";
	/**
	 * 작업종류 - 전체작업 진행
	 */
	public static int JOB_KIND_99_ANALYZE_ALL          	 		= 99;
	public static String JOB_KIND_99_ANALYZE_ID_ALL          	= "analyzeAll";
	
	static {
		JOB_KIND_MAP.put(JOB_KIND_11_ANALYZE_CLASS, JOB_KIND_11_ANALYZE_ID_CLASS);
		JOB_KIND_MAP.put(JOB_KIND_12_ANALYZE_CLASS_IMPL, JOB_KIND_12_ANALYZE_ID_CLASS_IMPL);
		JOB_KIND_MAP.put(JOB_KIND_13_ANALYZE_CLASS_ALIAS, JOB_KIND_13_ANALYZE_ID_CLASS_ALIAS);

		JOB_KIND_MAP.put(JOB_KIND_21_ANALYZE_QUERY, JOB_KIND_21_ANALYZE_ID_QUERY);
		JOB_KIND_MAP.put(JOB_KIND_22_ANALYZE_QUERY_CALLTBL, JOB_KIND_22_ANALYZE_ID_QUERY_CALLTBL);
		
		JOB_KIND_MAP.put(JOB_KIND_31_ANALYZE_MTD, JOB_KIND_31_ANALYZE_ID_MTD);
		JOB_KIND_MAP.put(JOB_KIND_32_ANALYZE_MTD_CALLMTD, JOB_KIND_32_ANALYZE_ID_MTD_CALLMTD);
		JOB_KIND_MAP.put(JOB_KIND_33_ANALYZE_MTD_CALLTBL, JOB_KIND_33_ANALYZE_ID_MTD_CALLTBL);
		
		JOB_KIND_MAP.put(JOB_KIND_41_ANALYZE_UI, JOB_KIND_41_ANALYZE_ID_UI);
		JOB_KIND_MAP.put(JOB_KIND_42_ANALYZE_UI_LINK, JOB_KIND_42_ANALYZE_ID_UI_LINK);

		JOB_KIND_MAP.put(JOB_KIND_51_ANALYZE_SAVE_METRIX, JOB_KIND_51_ANALYZE_ID_SAVE_METRIX);
		
		JOB_KIND_MAP.put(JOB_KIND_99_ANALYZE_ALL, JOB_KIND_99_ANALYZE_ID_ALL);
	}


	/**
	 * WORKER_THREAD_KIND_SINGLE - 분석작업을 진행 할 쓰레드핸들러 종류(싱글 쓰레드풀 생성)
	 */
	public static int WORKER_THREAD_KIND_SINGLE = 1;

	/**
	 * WORKER_THREAD_KIND_FIXED - 분석작업을 진행 할 쓰레드핸들러 종류(Fixed 쓰레드풀 생성)
	 */
	public static int WORKER_THREAD_KIND_FIXED = 2;

	/**
	 * WORKER_THREAD_KIND_CACHED - 분석작업을 진행 할 쓰레드핸들러 종류(Cached 쓰레드풀 생성)
	 */
	public static int WORKER_THREAD_KIND_CACHED = 3;
	
	/**
	 * WORKER_THREAD_KIND - 분석작업을 진행 할 쓰레드핸들러 종류
	 */
	public static int WORKER_THREAD_KIND = WORKER_THREAD_KIND_FIXED;
	
	public static String DIV = "↕"; 

	private SvcAnalyzer svcAnalyzer = new SvcAnalyzer();

	private AppAnalyzer(){
	}
	
	/**
	 * @param configPath - 설정파일경로
	 * @return
	 * @throws Exception 
	 */
	public static AppAnalyzer getInstance(String configPath) throws Exception{
		
		if(analizer == null || AppAnalyzer.CONF_CHANGED){   
			
			analizer = new AppAnalyzer();
			
			// 설정세팅
			analizer.setConfig(configPath);
			AppAnalyzer.CONF_CHANGED = false;
			
		}
		return analizer;
	}
	
	private void setConfig(String configPath) throws Exception {

		if( !FileUtil.isFileExist(configPath) ) {
			throw new Exception("설정파일(configPath)["+configPath+"]이 존재하지 않습니다.");
		}
		CONF = XmlUtil.getNonSingletonInstance(XmlUtil.XML_SOURCE_KIND_PATH, configPath);
		
		// 1. 어플리케이션루트
		String rootPath = CONF.getNode("APP_ROOT_PATH").getTextContent();
		rootPath = StringUtil.replace(rootPath, "\\", "/");
		if(rootPath.endsWith("/")) {rootPath = rootPath.substring(0, rootPath.lastIndexOf("/"));}
		AppAnalyzer.ROOT_PATH = rootPath;
		AppAnalyzer.ROOT_PATH = StringUtil.replace(AppAnalyzer.ROOT_PATH, "//", "/");
		
		// 2. 어플리케이션서버소스경로
		String classRootPath = CONF.getNode("APP_SRC_PATH").getTextContent();
		classRootPath = StringUtil.replace(classRootPath, "\\", "/");
		if(classRootPath.endsWith("/")) {classRootPath = classRootPath.substring(0, classRootPath.lastIndexOf("/"));}
		AppAnalyzer.CLASS_ROOT_PATH = AppAnalyzer.ROOT_PATH + "/" + classRootPath;
		AppAnalyzer.CLASS_ROOT_PATH = StringUtil.replace(AppAnalyzer.CLASS_ROOT_PATH, "//", "/");
		
		// 3. 어플리케이션웹소스경로
		String webRootPath = CONF.getNode("APP_WEB_PATH").getTextContent();
		webRootPath = StringUtil.replace(webRootPath, "\\", "/");
		if(webRootPath.endsWith("/")) {webRootPath = webRootPath.substring(0, webRootPath.lastIndexOf("/"));}
		AppAnalyzer.WEB_ROOT_PATH = AppAnalyzer.ROOT_PATH + "/" + webRootPath;
		AppAnalyzer.WEB_ROOT_PATH = StringUtil.replace(AppAnalyzer.WEB_ROOT_PATH, "//", "/");

		// 4. 어플리케이션쿼리소스루트
		String queryRootPath = CONF.getNode("APP_SQL_PATH").getTextContent();
		queryRootPath = StringUtil.replace(queryRootPath, "\\", "/");
		if(queryRootPath.endsWith("/")) {queryRootPath = queryRootPath.substring(0, queryRootPath.lastIndexOf("/"));}
		AppAnalyzer.QUERY_ROOT_PATH = queryRootPath;
		AppAnalyzer.QUERY_ROOT_PATH = StringUtil.replace(AppAnalyzer.QUERY_ROOT_PATH, "//", "/");
		
		// 5. 분석결과생성경로
		String writePath = CONF.getNode("WRITE_PATH").getTextContent();
		writePath = StringUtil.replace(writePath, "\\", "/");
		if(writePath.endsWith("/")) {writePath = writePath.substring(0, writePath.lastIndexOf("/"));}
		AppAnalyzer.WRITE_PATH = writePath;
		AppAnalyzer.WRITE_PATH = StringUtil.replace(AppAnalyzer.WRITE_PATH, "//", "/");
		
		// 6.분석결과저장파일명
		String saveFileName = CONF.getNode("SAVE_FILE_NAME").getTextContent();
		AppAnalyzer.SAVE_FILE_NAME = saveFileName;
		
		// 7.DB저장여부
		AppAnalyzer.IS_SAVE_TO_DB = Boolean.valueOf(CONF.getNode("IS_SAVE_TO_DB").getTextContent());
		
		// 8.DB아이디
		AppAnalyzer.DBID = CONF.getNode("DBID").getTextContent();
		
		// 9.테이블명DB추출여부	
		AppAnalyzer.IS_TABLE_LIST_FROM_DB = Boolean.valueOf(CONF.getNode("IS_TABLE_LIST_FROM_DB").getTextContent());
		
		// 10.테이블명 조회프리픽스
		AppAnalyzer.TABLE_NAME_LIKE_STR = CONF.getNode("TABLE_NAME_LIKE_STR").getTextContent();
		
		// 11.테이블명 정보파일명
		AppAnalyzer.TABLE_LIST_FILE_NAME = CONF.getNode("TABLE_LIST_FILE_NAME").getTextContent();
		
		// 12.JDK 홈
		// CONF.getNode("APP_JDK_HOME").getTextContent(); ==>> net.dstone.boot.common.tools.analyzer.util.ParseUtil.getJavaSymbolSolver() 에서만 사용
		
		// 13.어플리케이션 클래스패스
		// CONF.getNode("APP_CLASSPATH").getTextContent(); ==>> net.dstone.boot.common.tools.analyzer.util.ParseUtil.getJavaSymbolSolver() 에서만 사용
		
		// 14.쓰레드 핸들러 종류(분석작업을 진행 할 쓰레드핸들러 종류. 1:싱글 쓰레드풀, 2:Fixed 쓰레드풀, 3:Cached 쓰레드풀)
		AppAnalyzer.WORKER_THREAD_KIND = Integer.valueOf(CONF.getNode("WORKER_THREAD_KIND").getTextContent());
		
		// 15.쓰레드 갯수(분석작업을 진행 할 쓰레드 갯수. 쓰레드 핸들러 종류가 Fixed 쓰레드 핸들러 일 경우에만 유효)
		AppAnalyzer.WORKER_THREAD_NUM = Integer.valueOf(CONF.getNode("WORKER_THREAD_NUM").getTextContent());
		
		// 16.분석패키지루트 목록
		String includePackageRootStr = CONF.getNode("INCLUDE_PACKAGE_ROOT").getTextContent();
		if( !StringUtil.isEmpty(includePackageRootStr) ) {
			String[] includePackageRootArr = StringUtil.toStrArray(includePackageRootStr, ",", true);
			AppAnalyzer.INCLUDE_PACKAGE_ROOT = includePackageRootArr;
		}
		
		// 17.분석제외패키지패턴 목록
		String excludePackagePatternStr = CONF.getNode("EXCLUDE_PACKAGE_PATTERN").getTextContent();
		if( !StringUtil.isEmpty(excludePackagePatternStr) ) {
			String[] excludePackagePatternArr = StringUtil.toStrArray(excludePackagePatternStr, ",", true);
			AppAnalyzer.EXCLUDE_PACKAGE_PATTERN = excludePackagePatternArr;
		}
		
	}

	public void initAll() {
		try {
			
			/*** 파일정보 삭제 ***/
			FileUtil.deleteDir(AppAnalyzer.WRITE_PATH + "/class" );
			FileUtil.deleteDir(AppAnalyzer.WRITE_PATH + "/query" );
			FileUtil.deleteDir(AppAnalyzer.WRITE_PATH + "/method" );
			FileUtil.deleteDir(AppAnalyzer.WRITE_PATH + "/ui" );

			/*** 파일정보 생성 ***/
			/*
			FileUtil.makeDir(AppAnalyzer.WRITE_PATH + "/class" );
			FileUtil.makeDir(AppAnalyzer.WRITE_PATH + "/query" );
			FileUtil.makeDir(AppAnalyzer.WRITE_PATH + "/method" );
			FileUtil.makeDir(AppAnalyzer.WRITE_PATH + "/ui" );
			*/
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void analyzeApp(int analyzeJobKind, boolean isUnitOnly) {
		try {
			
			/*** 서버소스 분석 ***/
			svcAnalyzer.analyze(analyzeJobKind, isUnitOnly);
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}
	
	public void saveToDb() {
		try {
			
			/*** DB저장 ***/
			svcAnalyzer.saveToDb(AppAnalyzer.DBID);
			
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}
	
}
