package net.dstone.boot.common.tools.analyzer.svc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.dstone.common.core.BaseObject;
import net.dstone.common.task.TaskHandler;
import net.dstone.common.task.TaskItem;
import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.tools.analyzer.consts.ClzzKind;
import net.dstone.boot.common.tools.analyzer.svc.clzz.impl.JavaParseClzz;
import net.dstone.boot.common.tools.analyzer.svc.clzz.impl.TextParseClzz;
import net.dstone.boot.common.tools.analyzer.svc.clzz.impl.TossParseClzz;
import net.dstone.boot.common.tools.analyzer.svc.mtd.impl.JavaParseMtd;
import net.dstone.boot.common.tools.analyzer.svc.mtd.impl.TextParseMtd;
import net.dstone.boot.common.tools.analyzer.svc.mtd.impl.TossParseMtd;
import net.dstone.boot.common.tools.analyzer.svc.query.impl.MybatisParseQuery;
import net.dstone.boot.common.tools.analyzer.svc.query.impl.TossParseQuery;
import net.dstone.boot.common.tools.analyzer.svc.ui.impl.JspParseUi;
import net.dstone.boot.common.tools.analyzer.svc.ui.impl.TossParseUi;
import net.dstone.boot.common.tools.analyzer.util.DbGen;
import net.dstone.boot.common.tools.analyzer.util.ParseUtil;
import net.dstone.boot.common.tools.analyzer.vo.ClzzVo;
import net.dstone.boot.common.tools.analyzer.vo.MtdVo;
import net.dstone.boot.common.tools.analyzer.vo.QueryVo;
import net.dstone.boot.common.tools.analyzer.vo.UiVo;
import net.dstone.common.utils.DataSet;
import net.dstone.common.utils.DbUtil;
import net.dstone.common.utils.FileUtil;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.PartitionUtil;
import net.dstone.common.utils.StringUtil;

public class SvcAnalyzer extends BaseObject{
	
	public SvcAnalyzer() {
		init(); 
	}
	private void init() {
		taskHandler = TaskHandler.getInstance();
	}
	
	private TaskHandler taskHandler = null;
	
	private static ArrayList<String> SRC_FILTER = new ArrayList<String>();
	static {
		SRC_FILTER.add("java");
	}
	private static ArrayList<String> QUERY_FILTER = new ArrayList<String>();
	static {
		QUERY_FILTER.add("xml");
	}
	private static ArrayList<String> UI_FILTER = new ArrayList<String>();
	static {
		UI_FILTER.add("jsp");
		//UI_FILTER.add("js");
		//UI_FILTER.add("xml");
	}

	public static boolean isValidSvcFile(String file) {
		boolean isValid = false;
		if( FileUtil.isFileExist(file) ) {
			String ext = FileUtil.getFileExt(file);
			if(SRC_FILTER.contains(ext)) {
				isValid = true;
			}
		}
		if( isValid ) {
			for(String packagePattern : AppAnalyzer.EXCLUDE_PACKAGE_PATTERN) {
				if( StringUtil.replace(StringUtil.replace(file, AppAnalyzer.CLASS_ROOT_PATH, ""), "/", ".").indexOf(packagePattern) > -1) {
					isValid = false;
					break;
				}
			}
		}
		return isValid;
	}

	public static boolean isValidSvcPackage(String packageIdParam) {
		boolean isValid = false;
		String packageId = packageIdParam;
		if( packageId.indexOf("(") > -1 ) {
			packageId = packageId.substring(0, packageId.indexOf("("));
		}
		for(String packageRoot : AppAnalyzer.INCLUDE_PACKAGE_ROOT) {
			if( packageId.startsWith(packageRoot) ) {
				isValid = true;
				break;
			}
		}
		if( isValid ) {
			for(String packagePattern : AppAnalyzer.EXCLUDE_PACKAGE_PATTERN) {
				if( packageId.indexOf(packagePattern) > -1) {
					isValid = false;
					break;
				}
			}
		}
		return isValid;
	}
	
	public static boolean isValidQueryFile(String file) {
		boolean isValid = false;
		if( FileUtil.isFileExist(file) ) {
			String ext = FileUtil.getFileExt(file);
			if(QUERY_FILTER.contains(ext)) {
				isValid = true;
			}
		}
		return isValid;
	}
	
	public static boolean isValidUiFile(String file) {
		boolean isValid = false;
		if( FileUtil.isFileExist(file) ) {
			String ext = FileUtil.getFileExt(file);
			if(UI_FILTER.contains(ext)) {
				isValid = true;
			}
		}
		return isValid;
	}

	
	/*********************** Factory 시작 ***********************/
	/**
	 * 클래스 분석 팩토리 클래스
	 * @author jysn007
	 *
	 */
	private static class ClassFactory {
		
		static TextParseClzz textParseClzz = new TextParseClzz();
		static JavaParseClzz javaParseClzz = new JavaParseClzz();
		static TossParseClzz tossParseClzz = new TossParseClzz();
		
		/**
		 * 패키지ID 추출
		 * @param classFile 클래스파일
		 * @return
		 */
		static String getPackageId(String classFile) throws Exception {
			return javaParseClzz.getPackageId(classFile);
		}
		/**
		 * 클래스ID 추출
		 * @param classFile
		 * @return
		 */
		static String getClassId(String classFile) throws Exception {
			return javaParseClzz.getClassId(classFile);
		}
		/**
		 * 클래스명 추출
		 * @param classFile
		 * @return
		 */
		static String getClassName(String classFile) throws Exception {
			return javaParseClzz.getClassName(classFile);
		}
		/**
		 * 기능종류(UI:화면/JS:자바스크립트/CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지) 추출
		 * @param classFile
		 * @return
		 */
		static ClzzKind getClassKind(String classFile) throws Exception {
			return javaParseClzz.getClassKind(classFile);
		}
		/**
		 * 리소스ID 추출
		 * @param classFile
		 * @return
		 */
		static String getResourceId(String classFile) throws Exception {
			return javaParseClzz.getResourceId(classFile);
		}
		
		/**
		 * 클래스or인터페이스(C:클래스/I:인터페이스) 추출
		 * @param classFile
		 * @return
		 */
		static String getClassOrInterface(String classFile) throws Exception {
			return javaParseClzz.getClassOrInterface(classFile);
		}

		/**
		 * 상위인터페이스ID 추출(인터페이스를 구현한 클래스의 경우에만 존재)
		 * @param classFile
		 * @return
		 */
		static List<String> getInterfaceIdList(String classFile) throws Exception {
			return javaParseClzz.getInterfaceIdList(classFile);
		}
		/**
		 * 상위클래스ID 추출
		 * @param classFile
		 * @return
		 */
		static String getParentClassId(String classFile) throws Exception {
			return javaParseClzz.getParentClassId(classFile);
		}
		/**
		 * 인터페이스구현하위클래스ID목록(인터페이스인 경우에만 존재)
		 * @param selfClzzVo
		 * @param otherClassFileList
		 * @return
		 */
		static List<String> getImplClassIdList(ClzzVo selfClzzVo, String[] otherClassFileList) throws Exception {
			return javaParseClzz.getImplClassIdList(selfClzzVo, otherClassFileList);
		}
		/**
		 * 호출알리아스 추출. 리스트<맵>을 반환. 맵항목- Full클래스,알리아스 .(예: FULL_CLASS:aaa.bbb.Clzz2, ALIAS:clzz2)
		 * @param selfClzzVo
		 * @param otherClassFileList
		 * @return
		 */
		static List<Map<String, String>> getCallClassAlias(ClzzVo selfClzzVo, String[] otherClassFileList) throws Exception {
			return javaParseClzz.getCallClassAlias(selfClzzVo, otherClassFileList);
		}
	}
	
	/**
	 * 메서드 분석 팩토리 클래스
	 * @author jysn007
	 */
	private static class MethodFactory {

		static TextParseMtd textParseMtd = new TextParseMtd();
		static JavaParseMtd javaParseMtd = new JavaParseMtd();
		static TossParseMtd tossParseMtd = new TossParseMtd();
		
		/**
		 * 메서드ID/메서드명/메서드URL/메서드내용 추출
		 * @param classFile
		 * @return
		 */
		static List<Map<String, String>> getMtdInfoList(String classFile) throws Exception {
			return javaParseMtd.getMtdInfoList(classFile);
		}
		
		/**
		 * 호출메소드 목록 추출
		 * @param analyzedMethodFile
		 * @return
		 */
		static List<String> getCallMtdList(String analyzedMethodFile) throws Exception {
			return javaParseMtd.getCallMtdList(analyzedMethodFile);
		}

		/**
		 * 호출테이블 목록 추출
		 * @param methodFile
		 * @return
		 */
		static List<String> getCallTblList(String methodFile) throws Exception {
			return tossParseMtd.getCallTblList(methodFile);
		}

	}

	/**
	 * SQL 분석 팩토리 클래스
	 * @author jysn007
	 */
	private static class QueryFactory {

		static MybatisParseQuery mybatisParseQuery = new MybatisParseQuery();
		static TossParseQuery tossParseQuery = new TossParseQuery();
		
		/**
		 * 파일로부터 쿼리KEY(아이디)를 추출. 쿼리KEY는 파일명으로 사용됨.
		 * @param queryInfo(쿼리KEY를 추출할 수 있는 각종 정보를 담은 맵)
		 * @return
		 */
		static public String getQueryKey(Map<String, String> queryInfo) throws Exception {
			return mybatisParseQuery.getQueryKey(queryInfo);
		}
		
		/**
		 * 파일로부터 쿼리정보목록 추출. 쿼리정보는 아래와 같은 항목을 추출해야 한다.
		 * SQL_NAMESPACE - 네임스페이스
		 * SQL_ID - SQL아이디
		 * SQL_KIND - SQL종류(SELECT/INSERT/UPDATE/DELETE)
		 * SQL_BODY - SQL구문
		 * @param classFile
		 * @return
		 */
		static List<Map<String, String>> getQueryInfoList(String queryFile) throws Exception {
			return mybatisParseQuery.getQueryInfoList(queryFile);
		}

		/**
		 * 쿼리정보파일로부터 테이블ID정보목록 추출
		 * @param queryInfoFile
		 * @return
		 */
		static List<String> getCallTblList(String queryInfoFile, List<String> allTblList) throws Exception {
			return mybatisParseQuery.getTblInfoList(queryInfoFile, allTblList);
		}
		
	}

	/**
	 * UI 분석 팩토리 클래스
	 * @author jysn007
	 */
	private static class UiFactory {

		static JspParseUi jspParseUi = new JspParseUi();
		static TossParseUi tossParseUi = new TossParseUi();

		/**
		 * UI파일로부터 UI아이디 추출
		 * @param uiFile
		 * @return
		 */
		static String getUiId(String uiFile) throws Exception{
			return tossParseUi.getUiId(uiFile);
		}
		/**
		 * UI파일로부터 UI명 추출
		 * @param uiFile
		 * @return
		 */
		static String getUiName(String uiFile) throws Exception{
			return tossParseUi.getUiName(uiFile);
		}
		/**
		 * UI파일로부터 링크목록 추출
		 * @param uiFile
		 * @return
		 */
		static List<String> getUiLinkList(String uiFile) throws Exception {
			List<String> composeLinkList = new ArrayList<String>();
			
			composeLinkList = tossParseUi.getUiLinkList(uiFile);

			return composeLinkList.stream().distinct().collect(Collectors.toList());
		}

	}

	/*********************** Factory 끝 ***********************/

	public void analyze(int jobKind) {
		this.analyze(jobKind, false);
	}
	
	public void analyze(int analyzeJobKind, boolean isUnitOnly) {
		String[] 	classFileList = null;				/* 클래스파일리스트 */
		String[] 	queryFileList = null;				/* 쿼리파일리스트 */
		String[] 	uiFileList = null;					/* UI파일리스트 */

		String[] 	analyzedQueryFileList = null;		/* 쿼리분석파일리스트 */
		String[] 	analyzedMethodFileList = null;		/* 메소드분석파일리스트 */
		
		ArrayList<String> filteredFileList = null;
		List<String> allTblList = new ArrayList<String>();
		try {
			
			getLogger().info("/**************************************** A.클래스 분석 시작 ****************************************/");
			/*** 클래스 파일추출 시작 ***/
			classFileList = FileUtil.readFileListAll(AppAnalyzer.CLASS_ROOT_PATH);
			filteredFileList = new ArrayList<String>();
			for(String file : classFileList) {
				if( !SvcAnalyzer.isValidSvcFile(file) ||  !SvcAnalyzer.isValidSvcPackage(ClassFactory.getPackageId(file)) ) {
					continue;
				}
				filteredFileList.add(file);
			}
			classFileList = new String[filteredFileList.size()];
			filteredFileList.toArray(classFileList);
			filteredFileList.clear();
			filteredFileList = null;
			
			/*** A-1.클래스파일리스트 에서 패키지ID/클래스ID/클래스명/기능종류 등이 담긴 클래스분석파일리스트 추출 ***/
			if(isUnitOnly) {
				if(analyzeJobKind == AppAnalyzer.JOB_KIND_11_ANALYZE_CLASS) {
					this.analyzeClass(classFileList);
				}
			}else {
				if(analyzeJobKind >= AppAnalyzer.JOB_KIND_11_ANALYZE_CLASS) {
					this.analyzeClass(classFileList);
				}
			}

			/*** A-2.클래스파일리스트 에서 인터페이스구현하위클래스ID목록을 추출하여 클래스분석파일리스트에 추가 ***/
			if(isUnitOnly) {
				if(analyzeJobKind == AppAnalyzer.JOB_KIND_12_ANALYZE_CLASS_IMPL) {
					this.analyzeClassImpl(classFileList);
				}
			}else {
				if(analyzeJobKind >= AppAnalyzer.JOB_KIND_12_ANALYZE_CLASS_IMPL) {
					this.analyzeClassImpl(classFileList);
				}
			}
			
			/*** A-3.클래스파일리스트 에서 호출알리아스를 추출하여 클래스분석파일리스트에 추가 ***/
			if(isUnitOnly) {
				if(analyzeJobKind == AppAnalyzer.JOB_KIND_13_ANALYZE_CLASS_ALIAS) {
					this.analyzeClassAlias(classFileList);
				}
			}else {
				if(analyzeJobKind >= AppAnalyzer.JOB_KIND_13_ANALYZE_CLASS_ALIAS) {
					this.analyzeClassAlias(classFileList);
				}
			}
			getLogger().info("/**************************************** A.클래스 분석 끝 ****************************************/");
			
			getLogger().info("/**************************************** B.쿼리 분석 시작 ****************************************/");
			/*** 쿼리 파일추출 시작 ***/
			queryFileList = FileUtil.readFileListAll(AppAnalyzer.QUERY_ROOT_PATH);
			filteredFileList = new ArrayList<String>();
			for(String file : queryFileList) {
				if( !SvcAnalyzer.isValidQueryFile(file) ) {
					continue;
				}
				filteredFileList.add(file);
			}
			queryFileList = new String[filteredFileList.size()];
			filteredFileList.toArray(queryFileList);
			filteredFileList.clear();
			filteredFileList = null;
			
			/*** B-1.쿼리파일리스트 에서 KEY/네임스페이스/쿼리ID/쿼리종류/쿼리내용 등이 담긴 쿼리분석파일리스트 추출 ***/
			if(isUnitOnly) {
				if(analyzeJobKind == AppAnalyzer.JOB_KIND_21_ANALYZE_QUERY) {
					this.analyzeQuery(queryFileList);
				}
			}else {
				if(analyzeJobKind >= AppAnalyzer.JOB_KIND_21_ANALYZE_QUERY) {
					this.analyzeQuery(queryFileList);
				}
			}
			
			/*** B-2.쿼리분석파일리스트 에 호출테이블ID정보목록 추가 ***/
			if(AppAnalyzer.IS_TABLE_LIST_FROM_DB) {
				allTblList = DbUtil.getTabs(AppAnalyzer.DBID, AppAnalyzer.TABLE_NAME_LIKE_STR).getDataSetListVal("TBL_LIST", "TABLE_NAME");
			}else {
				allTblList = ParseUtil.getMannalTableList();
			}
			analyzedQueryFileList = FileUtil.readFileListAll(AppAnalyzer.WRITE_PATH + "/query");
			if(isUnitOnly) {
				if(analyzeJobKind == AppAnalyzer.JOB_KIND_22_ANALYZE_QUERY_CALLTBL) {
					this.analyzeQueryCallTbl(analyzedQueryFileList, allTblList);
				}
			}else {
				if(analyzeJobKind >= AppAnalyzer.JOB_KIND_22_ANALYZE_QUERY_CALLTBL) {
					this.analyzeQueryCallTbl(analyzedQueryFileList, allTblList);
				}
			}
			getLogger().info("/**************************************** B.쿼리 분석 끝 ****************************************/");
			
			getLogger().info("/**************************************** C.메소드 분석 시작 ****************************************/");
			/*** C-1.클래스파일리스트 에서 기능ID/메소드ID/메소드명/메소드URL/메소드내용 등이 담긴 메소드분석파일리스트 추출 ***/
			if(isUnitOnly) {
				if(analyzeJobKind == AppAnalyzer.JOB_KIND_31_ANALYZE_MTD) {
					this.analyzeMtd(classFileList);
				}
			}else {
				if(analyzeJobKind >= AppAnalyzer.JOB_KIND_31_ANALYZE_MTD) {
					this.analyzeMtd(classFileList);
				}
			}

			/*** C-2.메소드분석파일리스트 에 메소드내 타 호출메소드 목록 추가 ***/
			analyzedMethodFileList = FileUtil.readFileListAll(AppAnalyzer.WRITE_PATH + "/method");
			if(isUnitOnly) {
				if(analyzeJobKind == AppAnalyzer.JOB_KIND_32_ANALYZE_MTD_CALLMTD) {
					this.analyzeMtdCallMtd(analyzedMethodFileList);
				}
			}else {
				if(analyzeJobKind >= AppAnalyzer.JOB_KIND_32_ANALYZE_MTD_CALLMTD) {
					this.analyzeMtdCallMtd(analyzedMethodFileList);
				}
			}
			
			/*** C-3.메소드분석파일리스트 에 메소드내 호출테이블 목록 추가 ***/
			if(isUnitOnly) {
				if(analyzeJobKind == AppAnalyzer.JOB_KIND_33_ANALYZE_MTD_CALLTBL) {
					this.analyzeMtdCallTbl(analyzedMethodFileList);
				}
			}else {
				if(analyzeJobKind >= AppAnalyzer.JOB_KIND_33_ANALYZE_MTD_CALLTBL) {
					this.analyzeMtdCallTbl(analyzedMethodFileList);
				}
			}
			getLogger().info("/**************************************** C.메소드 분석 끝 ****************************************/");
			
			getLogger().info("/**************************************** D.UI 분석 시작 ****************************************/");
			/*** UI 파일추출 시작 ***/
			uiFileList = FileUtil.readFileListAll(AppAnalyzer.ROOT_PATH);
			filteredFileList = new ArrayList<String>();
			for(String file : uiFileList) {
				if( !SvcAnalyzer.isValidUiFile(file) ) {
					continue;
				}
				filteredFileList.add(file);
			}
			uiFileList = new String[filteredFileList.size()];
			filteredFileList.toArray(uiFileList);
			filteredFileList.clear();
			filteredFileList = null;
			
			/*** D-1.UI파일로부터 UI아이디/UI명 등이 담긴 UI분석파일목록 추출 ***/
			if(isUnitOnly) {
				if(analyzeJobKind == AppAnalyzer.JOB_KIND_41_ANALYZE_UI) {
					this.analyzeUi(uiFileList);
				}
			}else {
				if(analyzeJobKind >= AppAnalyzer.JOB_KIND_41_ANALYZE_UI) {
					this.analyzeUi(uiFileList);
				}
			}
			
			/*** D-2.UI파일로부터 링크 추출 ***/
			if(isUnitOnly) {
				if(analyzeJobKind == AppAnalyzer.JOB_KIND_42_ANALYZE_UI_LINK) {
					this.analyzeUiLink(uiFileList);
				}
			}else {
				if(analyzeJobKind >= AppAnalyzer.JOB_KIND_42_ANALYZE_UI_LINK) {
					this.analyzeUiLink(uiFileList);
				}
			}
			getLogger().info("/**************************************** D.UI 분석 끝 ****************************************/");
			
			getLogger().info("/**************************************** F.분석결과파일저장 시작 ****************************************/");
			if(isUnitOnly) {
				if(analyzeJobKind == AppAnalyzer.JOB_KIND_51_ANALYZE_SAVE_METRIX) {
					this.saveToFile();
				}
			}else {
				if(analyzeJobKind >= AppAnalyzer.JOB_KIND_51_ANALYZE_SAVE_METRIX) {
					this.saveToFile();
				}
			}
			getLogger().info("/**************************************** F.분석결과파일저장 끝 ****************************************/");	
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 클래스파일리스트 에서 패키지ID/클래스ID/클래스명/기능종류 등이 담긴 클래스분석파일리스트 추출
	 * @param classFileList	클래스파일리스트
	 */
	private void analyzeClass(String[] paramFileList)throws Exception {
		getLogger().info("/*** A-1.클래스파일리스트 에서 패키지ID/클래스ID/클래스명/기능종류 등이 담긴 클래스분석파일리스트 추출");

		String executorServiceId = AppAnalyzer.JOB_KIND_11_ANALYZE_ID_CLASS;
		
		if(paramFileList == null || paramFileList.length == 0) {return;}
		List<List<String>> divFileList = null;
		int chunkSize = 1;
		if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
			chunkSize = Math.max((paramFileList.length/AppAnalyzer.WORKER_THREAD_NUM), 1);
		}
		divFileList = PartitionUtil.ofSize(Arrays.asList(paramFileList), chunkSize);


		ArrayList<TaskItem> taskItemList = new ArrayList<TaskItem>();
		for(int n=0; n<divFileList.size(); n++) {
			List<String> divClassFileListItem = divFileList.get(n);
			String[] classFileList = new String[divClassFileListItem.size()];
			divClassFileListItem.toArray(classFileList);
			TaskItem taskItem = new TaskItem(){
				@Override
				public TaskItem doTheTask(){
					
					/************************ 작업세팅 시작 ************************/
					String[] classFileList = (String[])this.getObj("classFileList");
					
					ClzzVo clzzVo = null;
					String classFile= "";
					
					if(classFileList != null) {
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(classFileList.length);
						for(int i=0; i<classFileList.length; i++) {
							boolean isSucceded = true;
							try {
								classFile = classFileList[i];
								if( SvcAnalyzer.isValidSvcFile(classFile) ) {
									
									clzzVo = new ClzzVo();
									
									/*** 패키지ID ***/
									clzzVo.setPackageId(ClassFactory.getPackageId(classFile));
									
									/*** 클래스ID ***/
									clzzVo.setClassId(ClassFactory.getClassId(classFile));
									
									/*** 클래스명 ***/
									clzzVo.setClassName(ClassFactory.getClassName(classFile));
									
									/*** 기능종류 ***/
									clzzVo.setClassKind(ClassFactory.getClassKind(classFile));

									/*** 리소스ID ***/
									clzzVo.setResourceId(ClassFactory.getResourceId(classFile));

									/*** 클래스or인터페이스 ***/
									clzzVo.setClassOrInterface(ClassFactory.getClassOrInterface(classFile));

									/*** 상위인터페이스ID ***/
									clzzVo.setInterfaceIdList(ClassFactory.getInterfaceIdList(classFile));

									/*** 상위클래스ID ***/
									clzzVo.setParentClassId(ClassFactory.getParentClassId(classFile));
									
									/*** 파일명 ***/
									clzzVo.setFileName(classFile);
									
									// 파일저장			
									ParseUtil.writeClassVo(clzzVo, AppAnalyzer.WRITE_PATH + "/class");
									
								}
							} catch (Exception e) {
								LogUtil.sysout(this.getClass().getName() + ".analyzeClass()수행중 예외발생. classFile["+classFile+"]");
								e.printStackTrace();
								isSucceded = false;
							} finally {
								if(isSucceded) {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
								}else {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
								}
								taskHandler.doMonitoring(executorServiceId);
							}
						}
					}
					/************************ 작업세팅 종료 ************************/

					return this;
				}
			};
			taskItem.setObj("classFileList", classFileList);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		
		if( !this.taskHandler.isExecutorServiceExists(executorServiceId) ) {
			if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_SINGLE) {
				this.taskHandler.addSingleExecutorService(executorServiceId);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
				this.taskHandler.addFixedExecutorService(executorServiceId, AppAnalyzer.WORKER_THREAD_NUM);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_CACHED) {
				this.taskHandler.addCachedExecutorService(executorServiceId);
			}
		}
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}
	
	/**
	 * 클래스파일리스트 에서 해당클래스파일이 인터페이스인 경우 인터페이스구현하위클래스ID목록을 추출하여 클래스분석파일리스트에 추가
	 * @param classFileList 클래스파일리스트
	 */
	private void analyzeClassImpl(String[] paramFileList) throws Exception {
		getLogger().info("/*** A-2.클래스파일리스트 에서 인터페이스구현하위클래스ID목록을 추출하여 클래스분석파일리스트에 추가");
		
		String executorServiceId = AppAnalyzer.JOB_KIND_12_ANALYZE_ID_CLASS_IMPL;
		
		if(paramFileList == null || paramFileList.length == 0) {return;}
		List<List<String>> divFileList = null;
		int chunkSize = 1;
		if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
			chunkSize = Math.max((paramFileList.length/AppAnalyzer.WORKER_THREAD_NUM), 1);
		}
		divFileList = PartitionUtil.ofSize(Arrays.asList(paramFileList), chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<TaskItem>();
		for(int n=0; n<divFileList.size(); n++) {
			List<String> divClassFileListItem = divFileList.get(n);
			String[] classFileList = new String[divClassFileListItem.size()];
			divClassFileListItem.toArray(classFileList);
			TaskItem taskItem = new TaskItem(){
				@Override
				public TaskItem doTheTask(){
					
					/************************ 작업세팅 시작 ************************/
					String[] classFileList = (String[])this.getObj("classFileList");
					ClzzVo clzzVo = null;
					String pkgClassId = "";
					String classFile= "";
					String[] analyzedClassFileList = null;
					
					
					if(classFileList != null) {
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(classFileList.length);
						
						analyzedClassFileList = FileUtil.readFileList(AppAnalyzer.WRITE_PATH + "/class", false);
						for(int i=0; i<classFileList.length; i++) {
							boolean isSucceded = true;
							try {
								classFile = classFileList[i];
								if( SvcAnalyzer.isValidSvcFile(classFile) ) {
									String fileNoExt = classFile.substring(0, classFile.lastIndexOf("."));
									pkgClassId = StringUtil.replace(fileNoExt, AppAnalyzer.CLASS_ROOT_PATH, "");
									if(pkgClassId.startsWith("/")) {
										pkgClassId = pkgClassId.substring(1);
									}
									pkgClassId = StringUtil.replace(pkgClassId, "/", ".");
									clzzVo = ParseUtil.readClassVo(pkgClassId, AppAnalyzer.WRITE_PATH + "/class");
									
									if( "I".equals(clzzVo.getClassOrInterface()) ) {
										
										/*** 인터페이스구현하위클래스ID목록 ***/
										clzzVo.setImplClassIdList(ClassFactory.getImplClassIdList(clzzVo, analyzedClassFileList));
										
										// 파일저장	
										ParseUtil.writeClassVo(clzzVo, AppAnalyzer.WRITE_PATH + "/class");
									}

								}
							} catch (Exception e) {
								LogUtil.sysout(this.getClass().getName() + ".analyzeClassAlias()수행중 예외발생. classFile["+classFile+"]");
								e.printStackTrace();
								isSucceded = false;
							} finally {
								if(isSucceded) {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
								}else {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
								}
								taskHandler.doMonitoring(executorServiceId);
							}
						}
					}
					/************************ 작업세팅 종료 ************************/

					return this;
				}
			};
			taskItem.setObj("classFileList", classFileList);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		
		if( !this.taskHandler.isExecutorServiceExists(executorServiceId) ) {
			if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_SINGLE) {
				this.taskHandler.addSingleExecutorService(executorServiceId);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
				this.taskHandler.addFixedExecutorService(executorServiceId, AppAnalyzer.WORKER_THREAD_NUM);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_CACHED) {
				this.taskHandler.addCachedExecutorService(executorServiceId);
			}
		}
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}
	
	/**
	 * 클래스파일리스트 에서 호출알리아스 추출하여 클래스분석파일리스트에 추가
	 * @param classFileList 클래스파일리스트
	 */
	private void analyzeClassAlias(String[] paramFileList) throws Exception {
		getLogger().info("/*** A-3.클래스파일리스트 에서 호출알리아스를 추출하여 클래스분석파일리스트에 추가");
		
		String executorServiceId = AppAnalyzer.JOB_KIND_13_ANALYZE_ID_CLASS_ALIAS;
		
		if(paramFileList == null || paramFileList.length == 0) {return;}
		List<List<String>> divFileList = null;
		int chunkSize = 1;
		if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
			chunkSize = Math.max((paramFileList.length/AppAnalyzer.WORKER_THREAD_NUM), 1);
		}
		divFileList = PartitionUtil.ofSize(Arrays.asList(paramFileList), chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<TaskItem>();
		for(int n=0; n<divFileList.size(); n++) {
			List<String> divClassFileListItem = divFileList.get(n);
			String[] classFileList = new String[divClassFileListItem.size()];
			divClassFileListItem.toArray(classFileList);
			TaskItem taskItem = new TaskItem(){
				@Override
				public TaskItem doTheTask(){
					
					/************************ 작업세팅 시작 ************************/
					String[] classFileList = (String[])this.getObj("classFileList");
					ClzzVo clzzVo = null;
					String pkgClassId = "";
					String classFile= "";
					String[] analyzedClassFileList = null;
					
					if(classFileList != null) {
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(classFileList.length);
						analyzedClassFileList = FileUtil.readFileList(AppAnalyzer.WRITE_PATH + "/class", false);
						for(int i=0; i<classFileList.length; i++) {
							boolean isSucceded = true;
							try {
								classFile = classFileList[i];
								if( SvcAnalyzer.isValidSvcFile(classFile) ) {
									String fileNoExt = classFile.substring(0, classFile.lastIndexOf("."));
									pkgClassId = StringUtil.replace(fileNoExt, AppAnalyzer.CLASS_ROOT_PATH, "");
									if(pkgClassId.startsWith("/")) {
										pkgClassId = pkgClassId.substring(1);
									}
									pkgClassId = StringUtil.replace(pkgClassId, "/", ".");
									clzzVo = ParseUtil.readClassVo(pkgClassId, AppAnalyzer.WRITE_PATH + "/class");
									
									/*** 호출알리아스 ***/
									clzzVo.setCallClassAlias(ClassFactory.getCallClassAlias(clzzVo, analyzedClassFileList));
									
									// 파일저장	
									ParseUtil.writeClassVo(clzzVo, AppAnalyzer.WRITE_PATH + "/class");
								}
							} catch (Exception e) {
								LogUtil.sysout(this.getClass().getName() + ".analyzeClassAlias()수행중 예외발생. classFile["+classFile+"]");
								e.printStackTrace();
								isSucceded = false;
							} finally {
								if(isSucceded) {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
								}else {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
								}
								taskHandler.doMonitoring(executorServiceId);
							}
						}
					}
					/************************ 작업세팅 종료 ************************/

					return this;
				}
			};
			taskItem.setObj("classFileList", classFileList);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		
		if( !this.taskHandler.isExecutorServiceExists(executorServiceId) ) {
			if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_SINGLE) {
				this.taskHandler.addSingleExecutorService(executorServiceId);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
				this.taskHandler.addFixedExecutorService(executorServiceId, AppAnalyzer.WORKER_THREAD_NUM);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_CACHED) {
				this.taskHandler.addCachedExecutorService(executorServiceId);
			}
		}
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}

	/**
	 * 쿼리파일리스트 에서 KEY/네임스페이스/쿼리ID/쿼리종류/쿼리내용 등이 담긴 쿼리분석파일리스트 추출
	 * @param paramFileList 쿼리파일리스트
	 */
	private void analyzeQuery(String[] paramFileList) throws Exception {
		getLogger().info("/*** B-1.쿼리파일리스트 에서 KEY/네임스페이스/쿼리ID/쿼리종류/쿼리내용 등이 담긴 쿼리분석파일리스트 추출");
		
		String executorServiceId = AppAnalyzer.JOB_KIND_21_ANALYZE_ID_QUERY;
		
		if(paramFileList == null || paramFileList.length == 0) {return;}
		List<List<String>> divFileList = null;
		int chunkSize = 1;
		if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
			chunkSize = Math.max((paramFileList.length/AppAnalyzer.WORKER_THREAD_NUM), 1);
		}
		divFileList = PartitionUtil.ofSize(Arrays.asList(paramFileList), chunkSize);
		
		ArrayList<TaskItem> taskItemList = new ArrayList<TaskItem>();
		for(int n=0; n<divFileList.size(); n++) {
			List<String> divQueryFileListItem = divFileList.get(n);
			String[] queryFileList = new String[divQueryFileListItem.size()];
			divQueryFileListItem.toArray(queryFileList);
			TaskItem taskItem = new TaskItem(){
				@Override
				public TaskItem doTheTask(){
					
					/************************ 작업세팅 시작 ************************/
					String[] queryFileList = (String[])this.getObj("queryFileList");
					QueryVo queryVo = null;
					List<Map<String, String>> queryInfoList = null;
					String queryFile= "";
					
					if(queryFileList != null) {
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(queryFileList.length);
						for(int i=0; i<queryFileList.length; i++) {
							boolean isSucceded = true;
							try {
								queryFile = queryFileList[i];
								if( SvcAnalyzer.isValidQueryFile(queryFile) ) {

									/*** 파일로부터 쿼리정보목록 추출. ***
									 * SQL_NAMESPACE - 네임스페이스
									 * SQL_ID - SQL아이디
									 * SQL_KIND - SQL종류(SELECT/INSERT/UPDATE/DELETE)
									 * SQL_BODY - SQL구문
									****************************/
									queryInfoList = QueryFactory.getQueryInfoList(queryFile);
												
									if( queryInfoList != null ) {
										for(Map<String, String> queryInfo : queryInfoList) {
											queryVo = new QueryVo();

											/*** KEY ***/
											queryVo.setKey(QueryFactory.getQueryKey(queryInfo));
											
											/*** 네임스페이스 ***/
											queryVo.setNamespace(queryInfo.get("SQL_NAMESPACE"));

											/*** 쿼리ID ***/
											queryVo.setQueryId(queryInfo.get("SQL_ID"));
											
											/*** 쿼리종류 ***/
											queryVo.setQueryKind(queryInfo.get("SQL_KIND"));

											/*** 파일명 ***/
											queryVo.setFileName(AppAnalyzer.WRITE_PATH + "/query/" + queryVo.getKey() + ".txt");

											/*** 쿼리내용 ***/
											queryVo.setQueryBody(queryInfo.get("SQL_BODY"));

											// 파일저장			
											ParseUtil.writeQueryVo(queryVo, AppAnalyzer.WRITE_PATH + "/query");
										}
									}
								}
							} catch (Exception e) {
								LogUtil.sysout(this.getClass().getName() + ".analyzeQuery()수행중 예외발생. queryFile["+queryFile+"]");
								e.printStackTrace();
								isSucceded = false;
							} finally {
								if(isSucceded) {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
								}else {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
								}
								taskHandler.doMonitoring(executorServiceId);
							}
						}
					}
					/************************ 작업세팅 종료 ************************/

					return this;
				}
			};
			taskItem.setObj("queryFileList", queryFileList);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		
		if( !this.taskHandler.isExecutorServiceExists(executorServiceId) ) {
			if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_SINGLE) {
				this.taskHandler.addSingleExecutorService(executorServiceId);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
				this.taskHandler.addFixedExecutorService(executorServiceId, AppAnalyzer.WORKER_THREAD_NUM);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_CACHED) {
				this.taskHandler.addCachedExecutorService(executorServiceId);
			}
		}
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
		
		
	}
	
	/**
	 * 쿼리분석파일리스트 에 호출테이블ID정보목록 추출
	 * @param analyzedQueryFileList 쿼리분석파일리스트
	 * @param analyzedQueryFileList 전체테이블목록리스트
	 */
	private void analyzeQueryCallTbl(String[] paramFileList, List<String> allTblList) throws Exception {
		getLogger().info("/*** B-2.쿼리분석파일리스트 에 호출테이블ID정보목록 추가");
		
		String executorServiceId = AppAnalyzer.JOB_KIND_22_ANALYZE_ID_QUERY_CALLTBL;
		
		if(paramFileList == null || paramFileList.length == 0) {return;}
		List<List<String>> divFileList = null;
		int chunkSize = 1;
		if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
			chunkSize = Math.max((paramFileList.length/AppAnalyzer.WORKER_THREAD_NUM), 1);
		}
		divFileList = PartitionUtil.ofSize(Arrays.asList(paramFileList), chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<TaskItem>();
		for(int n=0; n<divFileList.size(); n++) {
			List<String> divQueryFileListItem = divFileList.get(n);
			String[] queryFileList = new String[divQueryFileListItem.size()];
			divQueryFileListItem.toArray(queryFileList);
			TaskItem taskItem = new TaskItem(){
				@Override
				public TaskItem doTheTask(){
					
					/************************ 작업세팅 시작 ************************/
					String[] queryFileList = (String[])this.getObj("queryFileList");
					QueryVo queryVo = null;
					String key = "";
					String analyzedQueryFile= "";
					
					if(queryFileList != null) {
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(queryFileList.length);
						for(int i=0; i<queryFileList.length; i++) {
							boolean isSucceded = true;
							try {
								analyzedQueryFile = queryFileList[i];
								key = FileUtil.getFileName(analyzedQueryFile, false);
								queryVo = ParseUtil.readQueryVo(key, AppAnalyzer.WRITE_PATH + "/query");
								
								/*** 테이블ID정보목록 ***/
								queryVo.setCallTblList(QueryFactory.getCallTblList(analyzedQueryFile, allTblList));
								
								// 파일저장	
								ParseUtil.writeQueryVo(queryVo, AppAnalyzer.WRITE_PATH + "/query");
							} catch (Exception e) {
								LogUtil.sysout(this.getClass().getName() + ".analyzeQueryCallTbl()수행중 예외발생. analyzedQueryFile["+analyzedQueryFile+"]");
								e.printStackTrace();
								isSucceded = false;
							} finally {
								if(isSucceded) {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
								}else {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
								}
								taskHandler.doMonitoring(executorServiceId);
							}
						}
					}
					/************************ 작업세팅 종료 ************************/
			
					return this;
				}
			};
			taskItem.setObj("queryFileList", queryFileList);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		
		if( !this.taskHandler.isExecutorServiceExists(executorServiceId) ) {
			if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_SINGLE) {
				this.taskHandler.addSingleExecutorService(executorServiceId);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
				this.taskHandler.addFixedExecutorService(executorServiceId, AppAnalyzer.WORKER_THREAD_NUM);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_CACHED) {
				this.taskHandler.addCachedExecutorService(executorServiceId);
			}
		}
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}
	

	/**
	 * 클래스파일리스트 에서 기능ID/메소드ID/메소드명/메소드URL/메소드내용 등이 담긴 메소드분석파일리스트 추출
	 * @param classFileList 클래스파일리스트
	 */
	private void analyzeMtd(String[] paramFileList) throws Exception {
		getLogger().info("/*** C-1.클래스파일리스트 에서 기능ID/메소드ID/메소드명/메소드URL/메소드내용 등이 담긴 메소드분석파일리스트 추출");
		
		String executorServiceId = AppAnalyzer.JOB_KIND_31_ANALYZE_ID_MTD;
		
		if(paramFileList == null || paramFileList.length == 0) {return;}

		List<List<String>> divFileList = null;
		int chunkSize = 1;
		if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
			chunkSize = Math.max((paramFileList.length/AppAnalyzer.WORKER_THREAD_NUM), 1);
		}
		divFileList = PartitionUtil.ofSize(Arrays.asList(paramFileList), chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<TaskItem>();
		for(int n=0; n<divFileList.size(); n++) {
			List<String> divClassFileListItem = divFileList.get(n);
			String[] classFileList = new String[divClassFileListItem.size()];
			divClassFileListItem.toArray(classFileList);
			TaskItem taskItem = new TaskItem(){
				@Override
				public TaskItem doTheTask(){
					
					/************************ 작업세팅 시작 ************************/
					String[] classFileList = (String[])this.getObj("classFileList");
					MtdVo mtdVo = null;
					List<Map<String, String>> methodInfoList = null;
					String classFile= "";
					String functionId = "";
					
					if(classFileList != null) {
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(classFileList.length);
						for(int i=0; i<classFileList.length; i++) {
							boolean isSucceded = true;
							try {
								classFile = classFileList[i];
								if( SvcAnalyzer.isValidSvcFile(classFile) ) {
									// 메소드ID/메소드명/메소드URL/메소드내용 추출
									methodInfoList = MethodFactory.getMtdInfoList(classFile);
									if( methodInfoList != null ) {
										for(Map<String, String> methodInfo : methodInfoList) {
											mtdVo = new MtdVo();
											if( !SvcAnalyzer.isValidSvcPackage(methodInfo.get("CLASS_ID")) ) {
												continue;
											}

											try {
												/*** 기능ID ***/
												functionId = methodInfo.get("FUNCTION_ID");
												mtdVo.setFunctionId(functionId);

												/*** 클래스ID ***/
												mtdVo.setClassId(methodInfo.get("CLASS_ID"));

												/*** 메소드ID ***/
												mtdVo.setMethodId(methodInfo.get("METHOD_ID"));

												/*** 메소드명 ***/
												mtdVo.setMethodName(methodInfo.get("METHOD_NAME"));

												/*** 메소드URL ***/
												mtdVo.setMethodUrl(methodInfo.get("METHOD_URL"));

												/*** 파일명 ***/
												mtdVo.setFileName(AppAnalyzer.WRITE_PATH + "/method/" + StringUtil.replace(StringUtil.replace(functionId, "<", "["), ">", "]") + ".txt");

												/*** 메소드내용 ***/
												mtdVo.setMethodBody(methodInfo.get("METHOD_BODY"));

												// 파일저장			
												ParseUtil.writeMethodVo(mtdVo, AppAnalyzer.WRITE_PATH + "/method");
											} catch (Exception e) {
												LogUtil.sysout(this.getClass().getName() + ".analyzeMtd()수행중 예외발생. functionId["+functionId+"]");
												e.printStackTrace();
											}
										}
									}
									
								}
							} catch (Exception e) {
								LogUtil.sysout(this.getClass().getName() + ".analyzeMtd()수행중 예외발생. classFile["+classFile+"]");
								e.printStackTrace();
								isSucceded = false;
							} finally {
								if(isSucceded) {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
								}else {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
								}
								taskHandler.doMonitoring(executorServiceId);
							}
						}
					}
					/************************ 작업세팅 종료 ************************/

					return this;
				}
			};
			taskItem.setObj("classFileList", classFileList);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		
		if( !this.taskHandler.isExecutorServiceExists(executorServiceId) ) {
			if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_SINGLE) {
				this.taskHandler.addSingleExecutorService(executorServiceId);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
				this.taskHandler.addFixedExecutorService(executorServiceId, AppAnalyzer.WORKER_THREAD_NUM);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_CACHED) {
				this.taskHandler.addCachedExecutorService(executorServiceId);
			}
		}
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}
	
	/**
	 * 메소드내 타 호출메소드 목록 추출
	 * @param analyzedMethodFileList 메소드분석파일리스트
	 */
	private void analyzeMtdCallMtd(String[] paramFileList) throws Exception {
		getLogger().info("/*** C-2.메소드분석파일리스트 에 메소드내 타 호출메소드 목록 추가");

		String debugStr = "";
		
		String executorServiceId = AppAnalyzer.JOB_KIND_32_ANALYZE_ID_MTD_CALLMTD;
		
		if(paramFileList == null || paramFileList.length == 0) {return;}
		List<List<String>> divFileList = null;
		int chunkSize = 1;
		if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
			chunkSize = Math.max((paramFileList.length/AppAnalyzer.WORKER_THREAD_NUM), 1);
		}
		divFileList = PartitionUtil.ofSize(Arrays.asList(paramFileList), chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<TaskItem>();
		for(int n=0; n<divFileList.size(); n++) {
			List<String> divClassFileListItem = divFileList.get(n);
			String[] analyzedMethodFileList = new String[divClassFileListItem.size()];
			divClassFileListItem.toArray(analyzedMethodFileList);
			TaskItem taskItem = new TaskItem(){
				@Override
				public TaskItem doTheTask(){
					/************************ 작업세팅 시작 ************************/
					String[] analyzedMethodFileList = (String[])this.getObj("analyzedMethodFileList");
					MtdVo mtdVo = null;
					String functionId = "";
					String analyzedMethodFile = "";
					
					if(analyzedMethodFileList != null) {
						
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(analyzedMethodFileList.length);

						for(int i=0; i<analyzedMethodFileList.length; i++) {
							boolean isSucceded = true;
							try {

								analyzedMethodFile = analyzedMethodFileList[i];

								String fileNoExt = analyzedMethodFile.substring(0, analyzedMethodFile.lastIndexOf("."));
								functionId = StringUtil.replace(fileNoExt, AppAnalyzer.WRITE_PATH + "/method", "");
								if(functionId.startsWith("/")) {
									functionId = functionId.substring(1);
								}
								functionId = StringUtil.replace(functionId, "/", ".");
								if( !StringUtil.isEmpty(debugStr) && analyzedMethodFile.indexOf(debugStr) > -1 ) {
									debug("분석대상:"+analyzedMethodFile + ", functionId:" + functionId );
								}

								if( !SvcAnalyzer.isValidSvcPackage(functionId) ) {
									continue;
								}

								mtdVo = ParseUtil.readMethodVo(functionId, AppAnalyzer.WRITE_PATH + "/method");
								
								/*** 호출메소드 ***/
								mtdVo.setCallMtdVoList(MethodFactory.getCallMtdList(analyzedMethodFile));
								
								// 파일저장	
								ParseUtil.writeMethodVo(mtdVo, AppAnalyzer.WRITE_PATH + "/method");
								
							} catch (Exception e) {
								LogUtil.sysout(this.getClass().getName() + ".analyzeMtdCallMtd()수행중 예외발생. analyzedMethodFile["+analyzedMethodFile+"]");
								e.printStackTrace();
								isSucceded = false;
							} finally {
								if(isSucceded) {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
								}else {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
								}
								taskHandler.doMonitoring(executorServiceId);
							}
						}
					}
					/************************ 작업세팅 종료 ************************/

					return this;
				}
			};
			taskItem.setObj("analyzedMethodFileList", analyzedMethodFileList);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		
		if( !this.taskHandler.isExecutorServiceExists(executorServiceId) ) {
			if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_SINGLE) {
				this.taskHandler.addSingleExecutorService(executorServiceId);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
				this.taskHandler.addFixedExecutorService(executorServiceId, AppAnalyzer.WORKER_THREAD_NUM);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_CACHED) {
				this.taskHandler.addCachedExecutorService(executorServiceId);
			}
		}
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}
	/**
	 * 메소드내 호출테이블 목록 추출
	 * @param analyzedMethodFileList 메소드분석파일리스트
	 */
	private void analyzeMtdCallTbl(String[] paramFileList) throws Exception {
		getLogger().info("/*** C-3.메소드분석파일리스트 에 메소드내 호출테이블 목록 추가");

		String executorServiceId = AppAnalyzer.JOB_KIND_33_ANALYZE_ID_MTD_CALLTBL;
		
		if(paramFileList == null || paramFileList.length == 0) {return;}
		List<List<String>> divFileList = null;
		int chunkSize = 1;
		if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
			chunkSize = Math.max((paramFileList.length/AppAnalyzer.WORKER_THREAD_NUM), 1);
		}
		divFileList = PartitionUtil.ofSize(Arrays.asList(paramFileList), chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<TaskItem>();
		for(int n=0; n<divFileList.size(); n++) {
			List<String> divClassFileListItem = divFileList.get(n);
			String[] analyzedMethodFileList = new String[divClassFileListItem.size()];
			divClassFileListItem.toArray(analyzedMethodFileList);
			TaskItem taskItem = new TaskItem(){
				@Override
				public TaskItem doTheTask(){
					
					/************************ 작업세팅 시작 ************************/
					String[] analyzedMethodFileList = (String[])this.getObj("analyzedMethodFileList");
					MtdVo mtdVo = null;
					String functionId = "";
					String analyzedMethodFile = "";
					
					
					if(analyzedMethodFileList != null) {
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(analyzedMethodFileList.length);
						for(int i=0; i<analyzedMethodFileList.length; i++) {
							boolean isSucceded = true;
							try {
								analyzedMethodFile = analyzedMethodFileList[i];
								String fileNoExt = analyzedMethodFile.substring(0, analyzedMethodFile.lastIndexOf("."));
								functionId = StringUtil.replace(fileNoExt, AppAnalyzer.WRITE_PATH + "/method", "");
								if(functionId.startsWith("/")) {
									functionId = functionId.substring(1);
								}
								functionId = StringUtil.replace(functionId, "/", ".");
								mtdVo = ParseUtil.readMethodVo(functionId, AppAnalyzer.WRITE_PATH + "/method");
								
								/*** 호출테이블 ***/
								mtdVo.setCallTblVoList(MethodFactory.getCallTblList(analyzedMethodFile));
								
								// 파일저장	
								ParseUtil.writeMethodVo(mtdVo, AppAnalyzer.WRITE_PATH + "/method");
								
							} catch (Exception e) {
								LogUtil.sysout(this.getClass().getName() + ".analyzeMtdCallTbl()수행중 예외발생. analyzedMethodFile["+analyzedMethodFile+"]");
								e.printStackTrace();
								isSucceded = false;
							} finally {
								if(isSucceded) {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
								}else {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
								}
								taskHandler.doMonitoring(executorServiceId);
							}
						}
					}
					/************************ 작업세팅 종료 ************************/

					return this;
				}
			};
			taskItem.setObj("analyzedMethodFileList", analyzedMethodFileList);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		
		if( !this.taskHandler.isExecutorServiceExists(executorServiceId) ) {
			if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_SINGLE) {
				this.taskHandler.addSingleExecutorService(executorServiceId);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
				this.taskHandler.addFixedExecutorService(executorServiceId, AppAnalyzer.WORKER_THREAD_NUM);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_CACHED) {
				this.taskHandler.addCachedExecutorService(executorServiceId);
			}
		}
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}
	
	/**
	 * UI파일리스트 에서 UI아이디/UI명 등이 담긴 UI분석파일리스트 추출
	 * @param uiFileList UI파일리스트
	 */
	private void analyzeUi(String[] paramFileList) throws Exception {
		getLogger().info("/*** D-1.UI파일로부터 UI아이디/UI명 등이 담긴 UI분석파일목록 추출");

		String executorServiceId = AppAnalyzer.JOB_KIND_41_ANALYZE_ID_UI;
		
		if(paramFileList == null || paramFileList.length == 0) {return;}
		List<List<String>> divFileList = null;
		int chunkSize = 1;
		if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
			chunkSize = Math.max((paramFileList.length/AppAnalyzer.WORKER_THREAD_NUM), 1);
		}
		divFileList = PartitionUtil.ofSize(Arrays.asList(paramFileList), chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<TaskItem>();
		for(int n=0; n<divFileList.size(); n++) {
			List<String> divClassFileListItem = divFileList.get(n);
			String[] uiFileList = new String[divClassFileListItem.size()];
			divClassFileListItem.toArray(uiFileList);
			TaskItem taskItem = new TaskItem(){
				@Override
				public TaskItem doTheTask(){
					
					/************************ 작업세팅 시작 ************************/
					String[] uiFileList = (String[])this.getObj("uiFileList");
					UiVo uiVo = null;
					String uiFile= "";
					
					if(uiFileList != null) {
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(uiFileList.length);
						for(int i=0; i<uiFileList.length; i++) {
							boolean isSucceded = true;
							try {
								uiFile = StringUtil.replace(uiFileList[i], "\\", "/");
								if( SvcAnalyzer.isValidUiFile(uiFile) ) {
									
									// UI아이디/UI명/인크루드파일/링크 추출
									uiVo = new UiVo();
									
									/*** UI아이디 ***/
									uiVo.setUiId(UiFactory.getUiId(uiFile));
									
									/*** UI명 ***/
									uiVo.setUiName(UiFactory.getUiName(uiFile));

									/*** 파일명 ***/
									uiVo.setFileName(uiFile);
									
									// 파일저장			
									ParseUtil.writeUiVo(uiVo, AppAnalyzer.WRITE_PATH + "/ui");
									
								}
							} catch (Exception e) {
								LogUtil.sysout(this.getClass().getName() + ".analyzeUi()수행중 예외발생. uiFile["+uiFile+"]");
								e.printStackTrace();
								isSucceded = false;
							} finally {
								if(isSucceded) {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
								}else {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
								}
								taskHandler.doMonitoring(executorServiceId);
							}
						}
					}
					/************************ 작업세팅 종료 ************************/

					return this;
				}
			};
			taskItem.setObj("uiFileList", uiFileList);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		
		if( !this.taskHandler.isExecutorServiceExists(executorServiceId) ) {
			if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_SINGLE) {
				this.taskHandler.addSingleExecutorService(executorServiceId);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
				this.taskHandler.addFixedExecutorService(executorServiceId, AppAnalyzer.WORKER_THREAD_NUM);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_CACHED) {
				this.taskHandler.addCachedExecutorService(executorServiceId);
			}
		}
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}
	/**
	 * UI파일리스트 에서 링크정보를 추출하여 UI분석파일리스트 에 추가
	 * @param uiFileList UI파일리스트
	 */
	private void analyzeUiLink(String[] paramFileList) throws Exception {
		getLogger().info("/*** D-2.UI파일로부터 링크 추출");

		String executorServiceId = AppAnalyzer.JOB_KIND_42_ANALYZE_ID_UI_LINK;
		
		if(paramFileList == null || paramFileList.length == 0) {return;}
		List<List<String>> divFileList = null;
		int chunkSize = 1;
		if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
			chunkSize = Math.max((paramFileList.length/AppAnalyzer.WORKER_THREAD_NUM), 1);
		}
		divFileList = PartitionUtil.ofSize(Arrays.asList(paramFileList), chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<TaskItem>();
		for(int n=0; n<divFileList.size(); n++) {
			List<String> divClassFileListItem = divFileList.get(n);
			String[] uiFileList = new String[divClassFileListItem.size()];
			divClassFileListItem.toArray(uiFileList);
			TaskItem taskItem = new TaskItem(){
				@Override
				public TaskItem doTheTask(){
					
					/************************ 작업세팅 시작 ************************/
					String[] uiFileList = (String[])this.getObj("uiFileList");
					UiVo uiVo = null;
					String uiFile= "";
					
					if(uiFileList != null) {
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(uiFileList.length);
						for(int i=0; i<uiFileList.length; i++) {
							boolean isSucceded = true;
							try {
								uiFile = StringUtil.replace(uiFileList[i], "\\", "/");
								if( SvcAnalyzer.isValidUiFile(uiFile) ) {
									
									// UI Vo
									uiVo = ParseUtil.readUiVo(UiFactory.getUiId(uiFile), AppAnalyzer.WRITE_PATH + "/ui");
									
									/*** 링크 ***/
									uiVo.setLinkList(UiFactory.getUiLinkList(uiFile));

									// 파일저장			
									ParseUtil.writeUiVo(uiVo, AppAnalyzer.WRITE_PATH + "/ui");
									
								}
							} catch (Exception e) {
								LogUtil.sysout(this.getClass().getName() + ".analyzeUiLink()수행중 예외발생. uiFile["+uiFile+"]");
								e.printStackTrace();
								isSucceded = false;
							} finally {
								if(isSucceded) {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
								}else {
									taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
								}
								taskHandler.doMonitoring(executorServiceId);
							}
						}
					}
					/************************ 작업세팅 종료 ************************/

					return this;
				}
			};
			taskItem.setObj("uiFileList", uiFileList);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		
		if( !this.taskHandler.isExecutorServiceExists(executorServiceId) ) {
			if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_SINGLE) {
				this.taskHandler.addSingleExecutorService(executorServiceId);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
				this.taskHandler.addFixedExecutorService(executorServiceId, AppAnalyzer.WORKER_THREAD_NUM);
			}else if(AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_CACHED) {
				this.taskHandler.addCachedExecutorService(executorServiceId);
			}
		}
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}

	public void saveToFile() throws Exception{
		getLogger().info("/*** F.분석결과파일저장 ");

		String executorServiceId = AppAnalyzer.JOB_KIND_51_ANALYZE_ID_SAVE_METRIX;
		
		TaskItem taskItem = new TaskItem(){
			@Override
			public TaskItem doTheTask(){
				boolean isSucceded = true;
				taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(1);
				
				try {
					/***************** 작업시작 *****************/

					StringBuffer conts = new StringBuffer();
					List<DataSet> dsList = new ArrayList<DataSet>();
					DataSet ds = new DataSet();
					DataSet dsRow = null;

					getLogger().info("/*** F-1.기본구조 세팅 시작 ***/");
					ds = makeAnalyzeBasicFileConts();
					getLogger().info("/*** F-1.기본구조 세팅 끝 ***/");

					getLogger().info("/*** F-2.호출구조 세팅 시작 ***/");
					if( ds.getDataSetRowCount("METRIX") > 0 ) {
						dsList = ds.getDataSetList("METRIX");
						List<DataSet> dsCopyList = new ArrayList<DataSet>();
						for(int i=0; i<dsList.size(); i++) {
							dsRow = dsList.get(i);
							String functionId = dsRow.getDatum("BASIC_ID");
							dsCopyList.addAll(makeAnalyzeCallChainFileConts(dsRow, functionId, 1, new ArrayList<String>()));
						}
						ds.setDataSetList("METRIX", (ArrayList<DataSet>)dsCopyList);
					}
					getLogger().info("/*** F-2.호출구조 세팅 끝 ***/");

					getLogger().info("/*** F-3.MAX호출레벨 세팅 시작 ***/");
					int maxCallLevel = 0;
					if( ds.getDataSetRowCount("METRIX") > 0 ) {
						for(DataSet row : ds.getDataSetList("METRIX")) {
							if( Integer.parseInt(row.getDatum("CALL_LEVEL", "0")) > maxCallLevel ) {
								maxCallLevel = Integer.parseInt(row.getDatum("CALL_LEVEL", "0"));
							}
						}
					}
					getLogger().info("/*** F-3.MAX호출레벨 세팅 끝 ***/");
					
					getLogger().info("/*** F-4.파일생성 시작 ***/");
					// 컬럼
					conts.append("UI_ID").append("\t");
					conts.append("UI_NM").append("\t");
					conts.append("BASIC_URL").append("\t");
					for(int i=1; i<=maxCallLevel; i++) {
						conts.append("FUNCTION_ID_"+i).append("\t");
						conts.append("FUNCTION_NAME_"+i).append("\t");
						conts.append("CLASS_KIND_"+i).append("\t");
					}
					conts.append("CALL_TBL").append("\t");
					conts.append("\n");
					// 내용
					if( ds.getDataSetRowCount("METRIX") > 0 ) {
						for(DataSet row : ds.getDataSetList("METRIX")) {
							String uiId = row.getDatum("UI_ID", " ");
							if( !StringUtil.isEmpty(uiId.trim()) ) {
								uiId = StringUtil.replace(uiId, ".", "/");
								if(!uiId.startsWith("/")) {
									uiId = "/" + uiId; 
								}
							}
							conts.append(uiId).append("\t");
							conts.append(row.getDatum("UI_NM", " ")).append("\t");
							conts.append(row.getDatum("BASIC_URL", " ")).append("\t");
							for(int i=1; i<=maxCallLevel; i++) {
								conts.append(row.getDatum("FUNCTION_ID_"+i, " ")).append("\t");
								conts.append(row.getDatum("FUNCTION_NAME_"+i, " ")).append("\t");
								conts.append(row.getDatum("CLASS_KIND_"+i, " ")).append("\t");
							}
							conts.append( makeAnalyzeTblInfo(row.getDatum("CALL_TBL", " ")) ).append("\t");
							conts.append("\n");
						}
					}
					FileUtil.writeFile(AppAnalyzer.WRITE_PATH, AppAnalyzer.SAVE_FILE_NAME, conts.toString());
					getLogger().info("/*** F-4.파일생성 끝 ***/");
					/***************** 작업끝 *****************/
				} catch (Exception e) {
					LogUtil.sysout(this.getClass().getName() + ".saveToFile()수행중 예외발생.");
					e.printStackTrace();
					isSucceded = false;
				} finally {
					if(isSucceded) {
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
					}else {
						taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
					}
					taskHandler.doMonitoring(executorServiceId);
				}
				return this;
			}
		};
		taskItem.setId(executorServiceId + "-" + 0);

		if( !this.taskHandler.isExecutorServiceExists(executorServiceId) ) {
			this.taskHandler.addSingleExecutorService(executorServiceId);
		}
		this.taskHandler.doTheSyncTask(executorServiceId, taskItem);
		
	}
	

	
	/**
	 * 분석결과파일 기본내용을 만들어 반환하는 메소드. 
	 * UI_ID <-> BASIC_URL을 기본 축으로 하되 여러개의 UI_ID가 동일한 BASIC_URL을 호출을 하는 거라면 Multi Rows로 표현한다.
	 * 예)
	 * UI_ID         BASIC_URL
	 * /test/test1   test/testList.ajax
	 * /test/test2   test/testList.ajax
	 * @return
	 * @throws Exception
	 */
	private DataSet makeAnalyzeBasicFileConts() throws Exception {
		DataSet ds = new DataSet();
		DataSet dsRow = null;
		ClzzVo clzzVo = null;
		MtdVo mtdVo = null;
		
		String[] analyzedClassFileList = FileUtil.readFileList( AppAnalyzer.WRITE_PATH + "/class", false );
		String[] analyzedMethodFileList = null;
		ArrayList<DataSet> dsMetrixList = new ArrayList<DataSet>();
		HashMap<String, ArrayList<DataSet>> urlDsMap = new HashMap<String, ArrayList<DataSet>>();
		ArrayList<DataSet> dsRowList = null;
		HashMap<String, ArrayList<UiVo>> urlUiMap = new HashMap<String, ArrayList<UiVo>>();
		ArrayList<UiVo> uiVoList = null;

		/*** 컨트롤러 URL 정보추출 ***/
		getLogger().info("/*** F-1-1.컨트롤러 URL 정보추출  ***/");
		if( analyzedClassFileList != null ) {
			for(String classId : analyzedClassFileList) {		
				clzzVo = ParseUtil.readClassVo(classId, AppAnalyzer.WRITE_PATH + "/class");	
				if( clzzVo.getClassKind() != null && ClzzKind.CT.getClzzKindCd().equals(clzzVo.getClassKind().getClzzKindCd()) ) {
					analyzedMethodFileList = FileUtil.readFileList(AppAnalyzer.WRITE_PATH + "/method", clzzVo.getClassId(), false);
					if( analyzedMethodFileList != null ) {
						for(String functionId : analyzedMethodFileList) {
							mtdVo = ParseUtil.readMethodVo(functionId, AppAnalyzer.WRITE_PATH + "/method");
							if( StringUtil.isEmpty(mtdVo.getMethodUrl()) ) {continue;}

							dsRow = new DataSet();
							dsRow.setDatum("BASIC_ID", mtdVo.getFunctionId());
							dsRow.setDatum("BASIC_URL", mtdVo.getMethodUrl());
							dsRowList = new ArrayList<DataSet>();
							dsRowList.add(dsRow);
							
							urlDsMap.put(dsRow.getDatum("BASIC_URL"), dsRowList);
						}
					}
				}
			}
		}

		/*** UI URL 정보추출 ***/
		getLogger().info("/*** F-1-2.UI URL 정보추출  ***/");
		String[] analyzedUiFileList = FileUtil.readFileList( AppAnalyzer.WRITE_PATH + "/ui", false );
		if( analyzedUiFileList != null ) {
			UiVo uiVo = null;
			for(String uiId : analyzedUiFileList) {
				uiVo = ParseUtil.readUiVo(uiId, AppAnalyzer.WRITE_PATH + "/ui");
				if( uiVo.getLinkList() != null ) {
					// UI 의 링크 루프
					for(String link : uiVo.getLinkList()) {
						link = link.trim();
						if( StringUtil.isEmpty(link) ) {continue;}
						if(!urlUiMap.containsKey(link)) {
							uiVoList = new ArrayList<UiVo>();
							urlUiMap.put(link, uiVoList);
						}
						urlUiMap.get(link).add(uiVo);
					}
				}
			}
		}

		/*** 컨트롤러, UI 조합 ***/
		getLogger().info("/*** F-1-3.컨트롤러, UI 조합  ***/");
		Iterator<String> urlDsMapIter = urlDsMap.keySet().iterator();
		String urlDs = "";
		Iterator<String> urlUiMapIter = urlUiMap.keySet().iterator();
		String urlUi = "";
		DataSet dsRowForCopy = null;
		boolean isUiAdded = false;
		while(urlDsMapIter.hasNext()) {
			urlDs = urlDsMapIter.next();
			dsRowList = urlDsMap.get(urlDs);
			dsRow = dsRowList.get(0);

			isUiAdded = false;
			urlUiMapIter = urlUiMap.keySet().iterator();
			while(urlUiMapIter.hasNext()) {
				urlUi = urlUiMapIter.next();
				if( urlUi.indexOf(urlDs)>-1 ) {
					uiVoList = urlUiMap.get(urlUi);
					for(UiVo uiVo : uiVoList) {
						dsRowForCopy = dsRow.copy();
						dsRowForCopy.setDatum("UI_ID", uiVo.getUiId());
						dsRowForCopy.setDatum("UI_NM", StringUtil.nullCheck(uiVo.getUiName(), "") );
						dsMetrixList.add(dsRowForCopy);
						isUiAdded = true;
					}
				}
			}
			if(!isUiAdded) {
				dsMetrixList.add(dsRow);
			}
		}
		ds.setDataSetList("METRIX", dsMetrixList);

		return ds;
	}
	
	
	/**
	 * 기능에 대한 추가정보 및 해당기능이 호출하는 타기능에 대한 정보를 재귀적으로 추적하여 반환하는 메소드. 
	 * @param dsRow 기능ID의 정보가 담긴(추가정보가 담길) DataSet.
	 * @param functionId 기능ID
	 * @param callLevel 호출레벨(재귀적으로 호출될 때마다 1씩 증가)
	 * @param callStackList 호출스택(동일기능ID의 재귀호출로 인한 무한루프를 방지하기 위해 호출스택을 쌓고 이미 호출된 기능ID를 재호출 한 경우 재귀호출을 멈추도록 구현한다.)
	 * @return
	 * @throws Exception
	 */
	private List<DataSet> makeAnalyzeCallChainFileConts(DataSet dsRow, String functionId, int callLevel, List<String> callStackList) throws Exception {
		List<DataSet> dsList = new ArrayList<DataSet>();
		callStackList.add(functionId);
		MtdVo mtdVo = ParseUtil.readMethodVo(functionId, AppAnalyzer.WRITE_PATH + "/method");
		ClzzVo clzzVo = ParseUtil.readClassVo(mtdVo.getClassId(), AppAnalyzer.WRITE_PATH + "/class");
		
		/********************************* 메소드별 사용할 항목 시작 *********************************/
		if(callStackList.size() == 1) {
			getLogger().info("/*** F-2-1.functionId["+callStackList.get(0)+"] 메소드별 사용할 항목 추출 ***/");
		}
		
		dsRow.setDatum("FUNCTION_ID_" + callLevel, mtdVo.getFunctionId());		// 기능ID
		dsRow.setDatum("FUNCTION_NAME_" + callLevel, mtdVo.getMethodName());	// 기능명
		dsRow.setDatum("CLASS_KIND_" + callLevel, "");							// 기능종류(UI:화면/JS:자바스크립트/CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지)
		dsRow.setDatum("FUNCTION_URL_" + callLevel, "");						// 기능URL
		if( clzzVo.getClassKind() != null ) {
			dsRow.setDatum("CLASS_KIND_" + callLevel, clzzVo.getClassKind().getClzzKindCd());
			if( ClzzKind.CT.getClzzKindCd().equals(clzzVo.getClassKind().getClzzKindCd()) ) {
				dsRow.setDatum("FUNCTION_URL_" + callLevel, mtdVo.getMethodUrl());
			}
		}
		dsRow.setDatum("CALL_LEVEL", String.valueOf(callLevel));				// 기능레벨
		if( mtdVo.getCallTblVoList() != null ) {
			StringBuffer tblBuff = new StringBuffer();
			String[] words = null;
			for(int i=0; i<mtdVo.getCallTblVoList().size(); i++) {
				String tblId = mtdVo.getCallTblVoList().get(i);
				if(tblBuff.length() > 0) {
					tblBuff.append("|");
				}
				if(!StringUtil.isEmpty(tblId) && tblId.indexOf("!")>-1) {
					words = StringUtil.toStrArray(tblId, "!");
					tblId = words[0] + "!" + words[1];
				}
				tblBuff.append(tblId);
			}
			dsRow.setDatum("CALL_TBL", tblBuff.toString());						// 호출테이블
		}
		/********************************* 메소드별 사용할 항목 끝 *********************************/

		/********************************* 호출메소드 재귀적 처리 시작 *********************************/
		if(callStackList.size() == 1) {
			getLogger().info("/*** F-2-2.functionId["+callStackList.get(0)+"] 호출메소드 재귀적 처리  ***/");
		}
		if( mtdVo.getCallMtdVoList() != null && mtdVo.getCallMtdVoList().size() > 0 ) {
			int childCallLevel = callLevel+1;
			DataSet dsRowCopy = null;
			int index = 0;
			for(String callFunctionId : mtdVo.getCallMtdVoList()) {
				if(FileUtil.isFileExist(AppAnalyzer.WRITE_PATH + "/method/" + ParseUtil.convFunctionIdToFileName(callFunctionId)  + ".txt")) {
					if( !callStackList.contains(callFunctionId) ) {
						List<String> callStackListCopy = new ArrayList<String>();
						callStackListCopy.addAll(callStackList);
						if(index == 0) {
							dsList.addAll(this.makeAnalyzeCallChainFileConts(dsRow, callFunctionId, childCallLevel, callStackListCopy));
						}else {
							dsRowCopy = dsRow.copy();
							dsList.addAll(this.makeAnalyzeCallChainFileConts(dsRowCopy, callFunctionId, childCallLevel, callStackListCopy));
						}
						index++;
					}
				}
			}
		}else {
			if(FileUtil.isFileExist(AppAnalyzer.WRITE_PATH + "/method/" + ParseUtil.convFunctionIdToFileName(functionId) + ".txt")) {
				dsList.add(dsRow);
			}
		}
		/********************************* 호출메소드 재귀적 처리 끝 *********************************/
		return dsList;
	}
	
	/**
	 * 테이블정보를 가공해서 반환하는 메소드
	 * @param input
	 * @return
	 * @throws Exception
	 */
	private String makeAnalyzeTblInfo(String input) throws Exception {
		StringBuffer tblInfo = new StringBuffer();	
		if(!StringUtil.isEmpty(input)) {
			String[] tblInfoArr = StringUtil.toStrArray(input, "|");
			String tableName = "";
			String tableComment = "";
			String queryKind = "";
			if( tblInfoArr != null && tblInfoArr.length > 0 ) {
				DataSet tblDs = null;
				Map<String, String> tableMap = null;
				for(String tblInfoRow : tblInfoArr) {
					tableName = "";
					tableComment = "";
					queryKind = "";
					String[] tblArr = StringUtil.toStrArray(tblInfoRow, "!");
					if( tblArr != null ) {
						if(tblInfo.length() > 0) {
							tblInfo.append(", ");
						}
						if(tblArr.length > 0) {
							tableName = tblArr[0].toUpperCase();
							tblInfo.append(tableName);
							if(AppAnalyzer.IS_TABLE_LIST_FROM_DB) {
								tblDs = DbUtil.getTabs(AppAnalyzer.DBID, tableName);
								if( tblDs.getDataSetRowCount("TBL_LIST") > 0 && !StringUtil.isEmpty(tblDs.getDataSet("TBL_LIST", 0).getDatum("TABLE_COMMENT"))) {
									tableComment = tblDs.getDataSet("TBL_LIST", 0).getDatum("TABLE_COMMENT");
								}
							}else {
								tableMap = ParseUtil.getMannalTableMap(tableName);
								if( tableMap != null && tableMap.size() > 0 && tableMap.containsKey("TABLE_COMMENT") ) {
									tableComment = tableMap.get("TABLE_COMMENT");
								}
							}
							if(!StringUtil.isEmpty(tableComment)) {
								tblInfo.append("(").append(tableComment).append(")");
							}
						}
						if( tblArr.length > 1 && tblArr[1].length() > 0) {
							queryKind = tblArr[1].substring(0, 1);
							tblInfo.append("-").append(queryKind);
						}
					}
				}
			}
		}
		return tblInfo.toString();
	}


	@SuppressWarnings("static-access")
	public void saveToDb(String DBID) {
		try {
			if( ! AppAnalyzer.IS_SAVE_TO_DB ) {
				getLogger().sysout("IS_SAVE_TO_DB["+AppAnalyzer.IS_SAVE_TO_DB+"]가 false 이므로 DB저장은 Skip 합니다.");
				return;
			}
			getLogger().info("/**************************************** G-1.기존데이터삭제 시작 ****************************************/");
			this.deleteFromDb(DBID);
			getLogger().info("/**************************************** G-1.기존데이터삭제 끝 ****************************************/");

			getLogger().info("/**************************************** G-2.데이터적재 시작 ****************************************/");
			this.insertToDb(DBID);
			getLogger().info("/**************************************** G-2.데이터적재 끝 ****************************************/");
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void deleteFromDb(String DBID) throws Exception {
		String sysId = AppAnalyzer.CONF.getNode("SYS_ID").getTextContent();
		DbGen.deleteAll(DBID, sysId);
	}

	private void insertToDb(String DBID) throws Exception {

		String sysId = AppAnalyzer.CONF.getNode("SYS_ID").getTextContent();
		String[] fileList = null;
		String subPath = "";
		
		try {
			// 클래스
			getLogger().info("/*** G-2-1.클래스 데이터적재 시작 ***/");
			subPath = AppAnalyzer.WRITE_PATH + "/class";
			fileList = FileUtil.readFileList(subPath, false);
			if(fileList != null) {
				DbGen.insertTB_CLZZ(DBID, sysId, fileList);
			}

			// 기능메서드
			getLogger().info("/*** G-2-2.기능메서드 데이터적재 시작 ***/");
			subPath = AppAnalyzer.WRITE_PATH + "/method";
			fileList = FileUtil.readFileList(subPath, false);
			if(fileList != null) {
				DbGen.insertTB_FUNC(DBID, sysId, fileList);
			}

			// 테이블
			getLogger().info("/*** G-2-3.테이블 데이터적재 시작 ***/");
			DbGen.insertTB_TBL(DBID, sysId);
			
			// 기능간맵핑
			getLogger().info("/*** G-2-4.기능간맵핑 데이터적재 시작 ***/");
			subPath = AppAnalyzer.WRITE_PATH + "/method";
			fileList = FileUtil.readFileList(subPath, false);
			if(fileList != null) {
				DbGen.insertTB_FUNC_FUNC_MAPPING(DBID, sysId, fileList);
			}

			// 테이블맵핑
			getLogger().info("/*** G-2-5.테이블맵핑 데이터적재 시작 ***/");
			subPath = AppAnalyzer.WRITE_PATH + "/method";
			fileList = FileUtil.readFileList(subPath, false);
			if(fileList != null) {
				DbGen.insertTB_FUNC_TBL_MAPPING(DBID, sysId, fileList);
			}

			// 화면
			getLogger().info("/*** G-2-6.화면 데이터적재 시작 ***/");
			subPath = AppAnalyzer.WRITE_PATH + "/ui";
			fileList = FileUtil.readFileList(subPath, false);
			if(fileList != null) {
				DbGen.insertTB_UI(DBID, sysId, fileList);
			}

			// 화면기능맵핑
			getLogger().info("/*** G-2-7.화면기능맵핑 데이터적재 시작 ***/");
			subPath = AppAnalyzer.WRITE_PATH + "/ui";
			fileList = FileUtil.readFileList(subPath, false);
			if(fileList != null) {
				DbGen.insertTB_UI_FUNC_MAPPING(DBID, sysId, fileList);
			}

			// 종합메트릭스
			getLogger().info("/*** G-2-8.종합메트릭스 데이터적재 시작 ***/");
			DbGen.insertTB_METRIX(DBID, sysId);

		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}
	
}
