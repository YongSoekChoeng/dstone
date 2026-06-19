package net.dstone.boot.common.tools.analyzer.svc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dstone.common.core.BaseObject;
import net.dstone.common.task.TaskHandler;
import net.dstone.common.task.TaskItem;
import net.dstone.boot.common.tools.analyzer.AppAnalyzer;
import net.dstone.boot.common.tools.analyzer.consts.ClzzKind;
import net.dstone.boot.common.tools.analyzer.svc.clzz.impl.JavaParseClzz;
import net.dstone.boot.common.tools.analyzer.svc.mtd.impl.JavaParseMtd;
import net.dstone.boot.common.tools.analyzer.svc.mtd.impl.TossParseMtd;
import net.dstone.boot.common.tools.analyzer.svc.query.impl.MybatisParseQuery;
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
import net.dstone.common.utils.SqlUtil;
import net.dstone.common.utils.StringUtil;

public class SvcAnalyzer extends BaseObject {

	public SvcAnalyzer() {
		init();
	}

	private void init() {
		taskHandler = TaskHandler.getInstance();
	}

	private TaskHandler taskHandler = null;

	private static final List<String> SRC_FILTER   = Arrays.asList("java");
	private static final List<String> QUERY_FILTER = Arrays.asList("xml");
	private static final List<String> UI_FILTER    = Arrays.asList("jsp");

	/* 파서 인스턴스 (스레드 안전하지 않으므로 TaskItem 내에서 new 로 생성) */
	private final TossParseMtd tossParseMtd = new TossParseMtd();

	public static boolean isValidSvcFile(String file) {
		boolean isValid = false;
		if (FileUtil.isFileExist(file)) {
			String ext = FileUtil.getFileExt(file);
			if (SRC_FILTER.contains(ext)) isValid = true;
		}
		if (isValid) {
			for (String packagePattern : AppAnalyzer.EXCLUDE_PACKAGE_PATTERN) {
				if (StringUtil.replace(StringUtil.replace(file, AppAnalyzer.CLASS_ROOT_PATH, ""), "/", ".").indexOf(packagePattern) > -1) {
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
		if (packageId.indexOf("(") > -1) {
			packageId = packageId.substring(0, packageId.indexOf("("));
		}
		for (String packageRoot : AppAnalyzer.INCLUDE_PACKAGE_ROOT) {
			if (packageId.startsWith(packageRoot)) {
				isValid = true;
				break;
			}
		}
		if (isValid) {
			for (String packagePattern : AppAnalyzer.EXCLUDE_PACKAGE_PATTERN) {
				if (packageId.indexOf(packagePattern) > -1) {
					isValid = false;
					break;
				}
			}
		}
		return isValid;
	}

	public static boolean isValidQueryFile(String file) {
		if (!FileUtil.isFileExist(file)) return false;
		return QUERY_FILTER.contains(FileUtil.getFileExt(file));
	}

	public static boolean isValidUiFile(String file) {
		if (!FileUtil.isFileExist(file)) return false;
		return UI_FILTER.contains(FileUtil.getFileExt(file));
	}

	/* ===================================================================
	 * 메인 분석 진입점
	 * =================================================================== */

	public void analyze(int jobKind) {
		this.analyze(jobKind, false);
	}

	public void analyze(int analyzeJobKind, boolean isUnitOnly) {
		String DBID  = AppAnalyzer.DBID;
		String sysId = "";
		try {
			sysId = AppAnalyzer.CONF.getNode("SYS_ID").getTextContent();
		} catch (Exception e) {
			throw new RuntimeException("SYS_ID 설정이 없습니다.", e);
		}

		try {
			/* 분석 시작 전 기존 데이터 삭제 */
			getLogger().info("/*** [초기화] 기존 DB 데이터 삭제 시작 ***/");
			DbGen.deleteAll(DBID, sysId);
			getLogger().info("/*** [초기화] 기존 DB 데이터 삭제 완료 ***/");

			/* 파일 목록 수집 - Files.walk() 스트리밍으로 전체 로드 없이 처리 */
			List<String> classFileList = collectFiles(AppAnalyzer.CLASS_ROOT_PATH, SRC_FILTER);
			List<String> filteredClassList = classFileList.stream()
				.filter(SvcAnalyzer::isValidSvcFile)
				.collect(Collectors.toList());

			List<String> queryFileList = collectFiles(AppAnalyzer.QUERY_ROOT_PATH, QUERY_FILTER)
				.stream()
				.filter(SvcAnalyzer::isValidQueryFile)
				.collect(Collectors.toList());

			List<String> uiFileList = collectFiles(AppAnalyzer.ROOT_PATH, UI_FILTER)
				.stream()
				.filter(SvcAnalyzer::isValidUiFile)
				.collect(Collectors.toList());

			/* 전체 테이블 목록 (B-2, C-3 공통 사용) */
			List<String> allTblList = new ArrayList<>();
			if (AppAnalyzer.IS_TABLE_LIST_FROM_DB) {
				allTblList = DbUtil.getTabs(DBID, AppAnalyzer.TABLE_NAME_LIKE_STR).getDataSetListVal("TBL_LIST", "TABLE_NAME");
			} else {
				allTblList = ParseUtil.getMannalTableList();
			}

			getLogger().info("/*** A.클래스 분석 시작 ***/");
			if (shouldRun(analyzeJobKind, AppAnalyzer.JOB_KIND_11_ANALYZE_CLASS, isUnitOnly))
				analyzeClass(filteredClassList, DBID, sysId);
			if (shouldRun(analyzeJobKind, AppAnalyzer.JOB_KIND_12_ANALYZE_CLASS_IMPL, isUnitOnly))
				analyzeClassImpl(DBID, sysId);
			if (shouldRun(analyzeJobKind, AppAnalyzer.JOB_KIND_13_ANALYZE_CLASS_ALIAS, isUnitOnly))
				analyzeClassAlias(filteredClassList, DBID, sysId);
			getLogger().info("/*** A.클래스 분석 완료 ***/");

			getLogger().info("/*** B.쿼리 분석 시작 ***/");
			if (shouldRun(analyzeJobKind, AppAnalyzer.JOB_KIND_21_ANALYZE_QUERY, isUnitOnly))
				analyzeQuery(queryFileList, DBID, sysId);
			if (shouldRun(analyzeJobKind, AppAnalyzer.JOB_KIND_22_ANALYZE_QUERY_CALLTBL, isUnitOnly))
				analyzeQueryCallTbl(DBID, sysId, allTblList);
			getLogger().info("/*** B.쿼리 분석 완료 ***/");

			/* 테이블 마스터 적재 */
			getLogger().info("/*** C-0.테이블 마스터 적재 ***/");
			DbGen.insertTB_TBL(DBID, sysId);

			getLogger().info("/*** C.메소드 분석 시작 ***/");
			if (shouldRun(analyzeJobKind, AppAnalyzer.JOB_KIND_31_ANALYZE_MTD, isUnitOnly))
				analyzeMtd(filteredClassList, DBID, sysId);
			if (shouldRun(analyzeJobKind, AppAnalyzer.JOB_KIND_32_ANALYZE_MTD_CALLMTD, isUnitOnly))
				analyzeMtdCallMtd(DBID, sysId);
			if (shouldRun(analyzeJobKind, AppAnalyzer.JOB_KIND_33_ANALYZE_MTD_CALLTBL, isUnitOnly))
				analyzeMtdCallTbl(DBID, sysId);
			getLogger().info("/*** C.메소드 분석 완료 ***/");

			getLogger().info("/*** D.UI 분석 시작 ***/");
			if (shouldRun(analyzeJobKind, AppAnalyzer.JOB_KIND_41_ANALYZE_UI, isUnitOnly))
				analyzeUi(uiFileList, DBID, sysId);
			if (shouldRun(analyzeJobKind, AppAnalyzer.JOB_KIND_42_ANALYZE_UI_LINK, isUnitOnly))
				analyzeUiLink(DBID, sysId);
			getLogger().info("/*** D.UI 분석 완료 ***/");

			getLogger().info("/*** F.METRIX 집계 및 저장 ***/");
			if (shouldRun(analyzeJobKind, AppAnalyzer.JOB_KIND_51_ANALYZE_SAVE_METRIX, isUnitOnly))
				saveMetrix(DBID, sysId);
			getLogger().info("/*** F.METRIX 완료 ***/");

		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	/** jobKind 실행 여부 판단 */
	private boolean shouldRun(int analyzeJobKind, int targetKind, boolean isUnitOnly) {
		if (isUnitOnly) return analyzeJobKind == targetKind;
		return analyzeJobKind >= targetKind;
	}

	/** Files.walk()로 파일 목록 수집 (전체 경로를 String[]로 올리지 않음) */
	private List<String> collectFiles(String rootPath, List<String> extFilter) {
		List<String> result = new ArrayList<>();
		if (rootPath == null || rootPath.isEmpty()) return result;
		try (Stream<Path> stream = Files.walk(Paths.get(rootPath))) {
			stream.filter(p -> !Files.isDirectory(p))
				.map(Path::toString)
				.map(p -> StringUtil.replace(p, "\\", "/"))
				.filter(p -> extFilter.contains(FileUtil.getFileExt(p)))
				.forEach(result::add);
		} catch (Exception e) {
			LogUtil.sysout("collectFiles 오류: rootPath=" + rootPath + ", " + e.getMessage());
		}
		return result;
	}

	/* ===================================================================
	 * A-1. 클래스 파싱 → TB_CLZZ INSERT
	 * =================================================================== */

	private void analyzeClass(List<String> classFileList, String DBID, String sysId) throws Exception {
		getLogger().info("/*** A-1.클래스 파싱 → TB_CLZZ INSERT");
		String executorServiceId = AppAnalyzer.JOB_KIND_11_ANALYZE_ID_CLASS;
		if (classFileList == null || classFileList.isEmpty()) return;

		int chunkSize = Math.max(classFileList.size() / Math.max(AppAnalyzer.WORKER_THREAD_NUM, 1), 1);
		List<List<String>> divList = PartitionUtil.ofSize(classFileList, chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<>();
		for (int n = 0; n < divList.size(); n++) {
			List<String> chunk = divList.get(n);
			TaskItem taskItem = new TaskItem() {
				@Override
				public TaskItem doTheTask() {
					@SuppressWarnings("unchecked")
					List<String> files = (List<String>) this.getObj("files");
					List<ClzzVo> batch = new ArrayList<>();
					taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(files.size());
					JavaParseClzz parser = new JavaParseClzz();
					for (String classFile : files) {
						boolean ok = true;
						try {
							if (!SvcAnalyzer.isValidSvcFile(classFile)) continue;
							ClzzVo v = new ClzzVo();
							v.setPackageId(parser.getPackageId(classFile));
							if (!SvcAnalyzer.isValidSvcPackage(v.getPackageId())) continue;
							v.setClassId(parser.getClassId(classFile));
							v.setClassName(parser.getClassName(classFile));
							v.setClassKind(parser.getClassKind(classFile));
							v.setResourceId(parser.getResourceId(classFile));
							v.setClassOrInterface(parser.getClassOrInterface(classFile));
							v.setInterfaceIdList(parser.getInterfaceIdList(classFile));
							v.setParentClassId(parser.getParentClassId(classFile));
							v.setFileName(classFile);
							batch.add(v);
						} catch (Exception e) {
							LogUtil.sysout("A-1 오류. classFile[" + classFile + "]");
							e.printStackTrace();
							ok = false;
						} finally {
							if (ok) taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
							else    taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
							taskHandler.doMonitoring(executorServiceId);
						}
					}
					/* 청크 단위 배치 INSERT */
					try {
						String dbid = (String) this.getObj("DBID");
						String sid  = (String) this.getObj("sysId");
						DbGen.insertBatchTB_CLZZ(dbid, sid, batch);
					} catch (Exception e) {
						LogUtil.sysout("A-1 DB INSERT 오류");
						e.printStackTrace();
					}
					return this;
				}
			};
			taskItem.setObj("files", chunk);
			taskItem.setObj("DBID", DBID);
			taskItem.setObj("sysId", sysId);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}

		ensureExecutorService(executorServiceId);
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}

	/* ===================================================================
	 * A-2. 인터페이스구현 클래스 목록 → TB_CLZZ UPDATE
	 * (JavaParseClzz.getImplClassIdList 는 파라미터 analyzedClassFileList 미사용)
	 * =================================================================== */

	private void analyzeClassImpl(String DBID, String sysId) throws Exception {
		getLogger().info("/*** A-2.인터페이스구현하위클래스 목록 UPDATE");
		List<ClzzVo> allClzz = DbGen.selectAllClzzVo(DBID, sysId);
		JavaParseClzz parser = new JavaParseClzz();
		for (ClzzVo clzzVo : allClzz) {
			if (!"I".equals(clzzVo.getClassOrInterface())) continue;
			try {
				List<String> implList = parser.getImplClassIdList(clzzVo, null);
				DbGen.updateTB_CLZZ_IMPL(DBID, sysId, clzzVo.getClassId(), implList);
			} catch (Exception e) {
				LogUtil.sysout("A-2 오류. clzzId[" + clzzVo.getClassId() + "]");
				e.printStackTrace();
			}
		}
	}

	/* ===================================================================
	 * A-3. 호출알리아스 → TB_CLZZ UPDATE
	 * (JavaParseClzz.getCallClassAlias 는 파라미터 analyzedClassFileList 미사용,
	 *  selfClzzVo.getFileName() 의 원본 Java 파일 직접 읽음)
	 * =================================================================== */

	private void analyzeClassAlias(List<String> classFileList, String DBID, String sysId) throws Exception {
		getLogger().info("/*** A-3.호출알리아스 UPDATE");
		String executorServiceId = AppAnalyzer.JOB_KIND_13_ANALYZE_ID_CLASS_ALIAS;
		if (classFileList == null || classFileList.isEmpty()) return;

		/* DB에서 ClzzVo 전체 로드 (fileName, classId 가 필요) */
		List<ClzzVo> allClzz = DbGen.selectAllClzzVo(DBID, sysId);
		Map<String, ClzzVo> clzzByFile = new HashMap<>();
		for (ClzzVo v : allClzz) {
			if (!StringUtil.isEmpty(v.getFileName())) clzzByFile.put(v.getFileName(), v);
		}

		int chunkSize = Math.max(classFileList.size() / Math.max(AppAnalyzer.WORKER_THREAD_NUM, 1), 1);
		List<List<String>> divList = PartitionUtil.ofSize(classFileList, chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<>();
		for (int n = 0; n < divList.size(); n++) {
			List<String> chunk = divList.get(n);
			TaskItem taskItem = new TaskItem() {
				@Override
				public TaskItem doTheTask() {
					@SuppressWarnings("unchecked")
					List<String> files = (List<String>) this.getObj("files");
					@SuppressWarnings("unchecked")
					Map<String, ClzzVo> byFile = (Map<String, ClzzVo>) this.getObj("clzzByFile");
					String dbid = (String) this.getObj("DBID");
					String sid  = (String) this.getObj("sysId");
					taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(files.size());
					JavaParseClzz parser = new JavaParseClzz();
					for (String classFile : files) {
						boolean ok = true;
						try {
							if (!SvcAnalyzer.isValidSvcFile(classFile)) continue;
							ClzzVo clzzVo = byFile.get(classFile);
							if (clzzVo == null) continue;
							List<Map<String, String>> aliasList = parser.getCallClassAlias(clzzVo, null);
							DbGen.updateTB_CLZZ_ALIAS(dbid, sid, clzzVo.getClassId(), aliasList);
						} catch (Exception e) {
							LogUtil.sysout("A-3 오류. classFile[" + classFile + "]");
							e.printStackTrace();
							ok = false;
						} finally {
							if (ok) taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
							else    taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
							taskHandler.doMonitoring(executorServiceId);
						}
					}
					return this;
				}
			};
			taskItem.setObj("files", chunk);
			taskItem.setObj("clzzByFile", clzzByFile);
			taskItem.setObj("DBID", DBID);
			taskItem.setObj("sysId", sysId);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		ensureExecutorService(executorServiceId);
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}

	/* ===================================================================
	 * B-1. 쿼리 파싱 → TB_QUERY INSERT
	 * =================================================================== */

	private void analyzeQuery(List<String> queryFileList, String DBID, String sysId) throws Exception {
		getLogger().info("/*** B-1.쿼리 파싱 → TB_QUERY INSERT");
		String executorServiceId = AppAnalyzer.JOB_KIND_21_ANALYZE_ID_QUERY;
		if (queryFileList == null || queryFileList.isEmpty()) return;

		int chunkSize = Math.max(queryFileList.size() / Math.max(AppAnalyzer.WORKER_THREAD_NUM, 1), 1);
		List<List<String>> divList = PartitionUtil.ofSize(queryFileList, chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<>();
		for (int n = 0; n < divList.size(); n++) {
			List<String> chunk = divList.get(n);
			TaskItem taskItem = new TaskItem() {
				@Override
				public TaskItem doTheTask() {
					@SuppressWarnings("unchecked")
					List<String> files = (List<String>) this.getObj("files");
					String dbid = (String) this.getObj("DBID");
					String sid  = (String) this.getObj("sysId");
					List<QueryVo> batch = new ArrayList<>();
					taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(files.size());
					MybatisParseQuery parser = new MybatisParseQuery();
					for (String queryFile : files) {
						boolean ok = true;
						try {
							if (!SvcAnalyzer.isValidQueryFile(queryFile)) continue;
							List<Map<String, String>> infoList = parser.getQueryInfoList(queryFile);
							if (infoList == null) continue;
							for (Map<String, String> info : infoList) {
								QueryVo v = new QueryVo();
								Map<String, String> keyInfo = new HashMap<>(info);
								v.setKey(parser.getQueryKey(keyInfo));
								v.setNamespace(info.get("SQL_NAMESPACE"));
								v.setQueryId(info.get("SQL_ID"));
								v.setQueryKind(info.get("SQL_KIND"));
								v.setQueryBody(info.get("SQL_BODY"));
								if (!StringUtil.isEmpty(v.getKey())) batch.add(v);
							}
						} catch (Exception e) {
							LogUtil.sysout("B-1 오류. queryFile[" + queryFile + "]");
							e.printStackTrace();
							ok = false;
						} finally {
							if (ok) taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
							else    taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
							taskHandler.doMonitoring(executorServiceId);
						}
					}
					try {
						DbGen.insertBatchTB_QUERY(dbid, sid, batch);
					} catch (Exception e) {
						LogUtil.sysout("B-1 DB INSERT 오류");
						e.printStackTrace();
					}
					return this;
				}
			};
			taskItem.setObj("files", chunk);
			taskItem.setObj("DBID", DBID);
			taskItem.setObj("sysId", sysId);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		ensureExecutorService(executorServiceId);
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}

	/* ===================================================================
	 * B-2. 쿼리별 호출 테이블 추출 → TB_QUERY UPDATE
	 * =================================================================== */

	private void analyzeQueryCallTbl(String DBID, String sysId, List<String> allTblList) throws Exception {
		getLogger().info("/*** B-2.쿼리별 호출테이블 UPDATE");
		List<QueryVo> queryList = DbGen.selectAllQueryVoWithBody(DBID, sysId);
		for (QueryVo queryVo : queryList) {
			try {
				if (StringUtil.isEmpty(queryVo.getQueryBody())) continue;
				List<String> tblList = SqlUtil.getTableNames(queryVo.getQueryBody(), allTblList);
				DbGen.updateTB_QUERY_CALLTBL(DBID, sysId, queryVo.getKey(), tblList);
			} catch (Exception e) {
				LogUtil.sysout("B-2 오류. sqlKey[" + queryVo.getKey() + "]");
				e.printStackTrace();
			}
		}
	}

	/* ===================================================================
	 * C-1. 메서드 파싱 → TB_FUNC INSERT (MTD_BODY 포함)
	 * =================================================================== */

	private void analyzeMtd(List<String> classFileList, String DBID, String sysId) throws Exception {
		getLogger().info("/*** C-1.메서드 파싱 → TB_FUNC INSERT");
		String executorServiceId = AppAnalyzer.JOB_KIND_31_ANALYZE_ID_MTD;
		if (classFileList == null || classFileList.isEmpty()) return;

		int chunkSize = Math.max(classFileList.size() / Math.max(AppAnalyzer.WORKER_THREAD_NUM, 1), 1);
		List<List<String>> divList = PartitionUtil.ofSize(classFileList, chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<>();
		for (int n = 0; n < divList.size(); n++) {
			List<String> chunk = divList.get(n);
			TaskItem taskItem = new TaskItem() {
				@Override
				public TaskItem doTheTask() {
					@SuppressWarnings("unchecked")
					List<String> files = (List<String>) this.getObj("files");
					String dbid = (String) this.getObj("DBID");
					String sid  = (String) this.getObj("sysId");
					List<MtdVo> batch = new ArrayList<>();
					taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(files.size());
					JavaParseMtd parser = new JavaParseMtd();
					for (String classFile : files) {
						boolean ok = true;
						try {
							if (!SvcAnalyzer.isValidSvcFile(classFile)) continue;
							List<Map<String, String>> mtdInfoList = parser.getMtdInfoList(classFile);
							if (mtdInfoList == null) continue;
							for (Map<String, String> info : mtdInfoList) {
								String classId = info.get("CLASS_ID");
								if (!SvcAnalyzer.isValidSvcPackage(classId)) continue;
								String functionId = info.get("FUNCTION_ID");
								MtdVo v = new MtdVo();
								v.setFunctionId(functionId);
								v.setClassId(classId);
								v.setMethodId(info.get("METHOD_ID"));
								v.setMethodName(info.get("METHOD_NAME"));
								v.setMethodUrl(info.get("METHOD_URL"));
								v.setMethodBody(info.get("METHOD_BODY"));
								v.setFileName(classFile);
								if (!StringUtil.isEmpty(functionId)) batch.add(v);
							}
						} catch (Exception e) {
							LogUtil.sysout("C-1 오류. classFile[" + classFile + "]");
							e.printStackTrace();
							ok = false;
						} finally {
							if (ok) taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
							else    taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
							taskHandler.doMonitoring(executorServiceId);
						}
					}
					try {
						DbGen.insertBatchTB_FUNC(dbid, sid, batch);
					} catch (Exception e) {
						LogUtil.sysout("C-1 DB INSERT 오류");
						e.printStackTrace();
					}
					return this;
				}
			};
			taskItem.setObj("files", chunk);
			taskItem.setObj("DBID", DBID);
			taskItem.setObj("sysId", sysId);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		ensureExecutorService(executorServiceId);
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}

	/* ===================================================================
	 * C-2. 메서드 내 호출메서드 추출 → TB_FUNC_FUNC_MAPPING INSERT
	 * (TextParseMtd.getCallMtdList 로직을 DB 데이터 기반으로 인라인 구현)
	 * =================================================================== */

	private void analyzeMtdCallMtd(String DBID, String sysId) throws Exception {
		getLogger().info("/*** C-2.호출메서드 → TB_FUNC_FUNC_MAPPING INSERT");

		/* ClzzVo 전체 캐시 (alias, classOrInterface 조회용) */
		List<ClzzVo> allClzz = DbGen.selectAllClzzVo(DBID, sysId);
		Map<String, ClzzVo> clzzCache = new HashMap<>();
		for (ClzzVo v : allClzz) clzzCache.put(v.getClassId(), v);

		/* FUNC_ID 전체 목록 → 청크 처리 */
		List<String> allFuncIds = DbGen.selectAllFuncId(DBID, sysId);
		int chunkSize = 1000;
		List<List<String>> chunks = PartitionUtil.ofSize(allFuncIds, chunkSize);
		String[] div = {"("};

		for (List<String> chunk : chunks) {
			List<MtdVo> mtdList = DbGen.selectMtdVoWithBodyByIds(DBID, sysId, chunk);
			List<String[]> callMtdBatch = new ArrayList<>();

			for (MtdVo mtdVo : mtdList) {
				if (!SvcAnalyzer.isValidSvcPackage(mtdVo.getFunctionId())) continue;
				ClzzVo clzzVo = clzzCache.get(mtdVo.getClassId());
				if (clzzVo == null) continue;

				List<String> callList = computeCallMtdList(mtdVo, clzzVo, clzzCache, div);
				for (String callFuncId : callList) {
					callMtdBatch.add(new String[]{mtdVo.getFunctionId(), callFuncId});
				}
			}

			if (!callMtdBatch.isEmpty()) {
				DbGen.insertBatchTB_FUNC_FUNC_MAPPING(DBID, sysId, callMtdBatch);
			}
		}
	}

	/** TextParseMtd.getCallMtdList() 로직을 DB 캐시 기반으로 구현 */
	private List<String> computeCallMtdList(MtdVo mtdVo, ClzzVo clzzVo, Map<String, ClzzVo> clzzCache, String[] div) {
		List<String> result = new ArrayList<>();
		List<Map<String, String>> aliasList = clzzVo.getCallClassAlias();
		String mtdBody = mtdVo.getMethodBody();
		if (StringUtil.isEmpty(mtdBody) || aliasList == null || aliasList.isEmpty()) return result;

		String[] lines = StringUtil.toStrArray(mtdBody, "\n");
		for (String line : lines) {
			for (Map<String, String> alias : aliasList) {
				String fullClass = alias.get("FULL_CLASS");
				String callAlias = alias.get("ALIAS");
				String callMtd = "";

				ClzzVo targetClzz = clzzCache.get(fullClass);
				List<String> targetClassIds = new ArrayList<>();

				if (targetClzz != null && "I".equals(targetClzz.getClassOrInterface())) {
					try {
						List<String> implList = ParseUtil.findImplClassList(targetClzz.getClassId(), targetClzz.getResourceId());
						if (implList != null) targetClassIds.addAll(implList);
					} catch (Exception e) {
						targetClassIds.add(fullClass);
					}
				} else {
					targetClassIds.add(fullClass);
				}

				for (String targetClassId : targetClassIds) {
					String kw = callAlias + ".";
					if (line.indexOf(" " + kw) > -1 || line.startsWith(kw)) {
						callMtd = targetClassId + "." + StringUtil.nextWord(line, kw, div);
					}
					if (StringUtil.isEmpty(callMtd)) {
						kw = ParseUtil.getGetterNmFromField(callAlias) + ".";
						if (line.indexOf(" " + kw) > -1 || line.startsWith(kw)) {
							callMtd = targetClassId + "." + StringUtil.nextWord(line, kw, div);
						}
					}
					if (!StringUtil.isEmpty(callMtd)) {
						if (!result.contains(callMtd)) result.add(callMtd);
						break;
					}
				}
				if (!StringUtil.isEmpty(callMtd)) break;
			}
		}
		return result;
	}

	/* ===================================================================
	 * C-3. 메서드 내 호출테이블 추출 → TB_FUNC_TBL_MAPPING INSERT
	 * (TossParseMtd.getCallTblList 로직을 DB 데이터 기반으로 인라인 구현)
	 * =================================================================== */

	private void analyzeMtdCallTbl(String DBID, String sysId) throws Exception {
		getLogger().info("/*** C-3.호출테이블 → TB_FUNC_TBL_MAPPING INSERT");

		/* 전체 QueryVo 캐시 (key → QueryVo, SQL_BODY 없이 CALL_TBL_LIST 있음) */
		Map<String, QueryVo> queryVoMap = DbGen.selectAllQueryVoMap(DBID, sysId);
		List<String> queryKeyList = new ArrayList<>(queryVoMap.keySet());

		/* FUNC_ID 청크 처리 */
		List<String> allFuncIds = DbGen.selectAllFuncId(DBID, sysId);
		int chunkSize = 1000;
		List<List<String>> chunks = PartitionUtil.ofSize(allFuncIds, chunkSize);

		for (List<String> chunk : chunks) {
			List<MtdVo> mtdList = DbGen.selectMtdVoWithBodyByIds(DBID, sysId, chunk);
			List<String[]> tblBatch = new ArrayList<>();

			for (MtdVo mtdVo : mtdList) {
				List<String> tblList = computeCallTblList(mtdVo.getMethodBody(), queryKeyList, queryVoMap);
				for (String tblEntry : tblList) {
					/* tblEntry = "TABLENAME!JOBKIND" */
					String[] parts = StringUtil.toStrArray(tblEntry, "!");
					String tblId  = parts.length > 0 ? parts[0] : "";
					String jobKind = parts.length > 1 ? parts[1] : "";
					if (tblId.indexOf(".") > -1) tblId = tblId.substring(tblId.indexOf(".") + 1);
					if (!StringUtil.isEmpty(mtdVo.getFunctionId()) && !StringUtil.isEmpty(tblId)) {
						tblBatch.add(new String[]{mtdVo.getFunctionId(), tblId, jobKind});
					}
				}
			}

			if (!tblBatch.isEmpty()) {
				DbGen.insertBatchTB_FUNC_TBL_MAPPING(DBID, sysId, tblBatch);
			}
		}
	}

	/** TossParseMtd.getCallTblList() 로직을 queryVoMap 기반으로 구현 */
	private List<String> computeCallTblList(String methodBody, List<String> queryKeyList, Map<String, QueryVo> queryVoMap) {
		List<String> callTblList = new ArrayList<>();
		if (StringUtil.isEmpty(methodBody)) return callTblList;

		String[] lines = StringUtil.toStrArray(methodBody, "\n");
		for (String line : lines) {
			for (String queryKey : queryKeyList) {
				boolean isUsed = false;

				/* CASE-1: "Namespace.sqlId" 패턴 */
				String keyword = queryKey;
				if (keyword.indexOf("_") > -1) {
					keyword = keyword.substring(0, keyword.lastIndexOf("_")) + "." + keyword.substring(keyword.lastIndexOf("_") + 1);
				}
				keyword = "\"" + keyword + "\"";
				if (line.indexOf(keyword) > -1) isUsed = true;

				/* CASE-2: getXxxMapper() + "sqlId" 패턴 */
				if (!isUsed && queryKey.indexOf("_") > -1 && (line.indexOf("getSqlSession().") > -1 || line.indexOf("sqlSession.") > -1)) {
					try {
						String[] qkParts = StringUtil.toStrArray(queryKey, "_");
						String nameSpace = qkParts[0];
						String getterName = tossParseMtd.getGetterMethodNameByNameSpace(nameSpace) + "()";
						String queryId = "\"" + qkParts[1] + "\"";
						String kw2 = StringUtil.replace(StringUtil.trimTextForParse(getterName + "+" + queryId), " ", "").toUpperCase();
						if (StringUtil.replace(StringUtil.trimTextForParse(line), " ", "").toUpperCase().indexOf(kw2) > -1) {
							isUsed = true;
						}
					} catch (Exception ignore) {}
				}

				if (isUsed) {
					QueryVo queryVo = queryVoMap.get(queryKey);
					if (queryVo != null && queryVo.getCallTblList() != null) {
						for (String callTbl : queryVo.getCallTblList()) {
							String tblKey = callTbl + "!" + queryVo.getQueryKind();
							if (!callTblList.contains(tblKey)) callTblList.add(tblKey);
						}
					}
					break;
				}
			}
		}
		return callTblList;
	}

	/* ===================================================================
	 * D-1. JSP 파싱 → TB_UI INSERT
	 * =================================================================== */

	private void analyzeUi(List<String> uiFileList, String DBID, String sysId) throws Exception {
		getLogger().info("/*** D-1.UI 파싱 → TB_UI INSERT");
		String executorServiceId = AppAnalyzer.JOB_KIND_41_ANALYZE_ID_UI;
		if (uiFileList == null || uiFileList.isEmpty()) return;

		int chunkSize = Math.max(uiFileList.size() / Math.max(AppAnalyzer.WORKER_THREAD_NUM, 1), 1);
		List<List<String>> divList = PartitionUtil.ofSize(uiFileList, chunkSize);

		ArrayList<TaskItem> taskItemList = new ArrayList<>();
		for (int n = 0; n < divList.size(); n++) {
			List<String> chunk = divList.get(n);
			TaskItem taskItem = new TaskItem() {
				@Override
				public TaskItem doTheTask() {
					@SuppressWarnings("unchecked")
					List<String> files = (List<String>) this.getObj("files");
					String dbid = (String) this.getObj("DBID");
					String sid  = (String) this.getObj("sysId");
					List<UiVo> batch = new ArrayList<>();
					taskHandler.getExecutorServiceTaskReport(executorServiceId).addTryCount(files.size());
					TossParseUi parser = new TossParseUi();
					for (String uiFile : files) {
						boolean ok = true;
						try {
							uiFile = StringUtil.replace(uiFile, "\\", "/");
							if (!SvcAnalyzer.isValidUiFile(uiFile)) continue;
							UiVo v = new UiVo();
							v.setUiId(parser.getUiId(uiFile));
							v.setUiName(parser.getUiName(uiFile));
							v.setFileName(uiFile);
							if (!StringUtil.isEmpty(v.getUiId())) batch.add(v);
						} catch (Exception e) {
							LogUtil.sysout("D-1 오류. uiFile[" + uiFile + "]");
							e.printStackTrace();
							ok = false;
						} finally {
							if (ok) taskHandler.getExecutorServiceTaskReport(executorServiceId).addSuccessCount();
							else    taskHandler.getExecutorServiceTaskReport(executorServiceId).addErrorCount();
							taskHandler.doMonitoring(executorServiceId);
						}
					}
					try {
						DbGen.insertBatchTB_UI(dbid, sid, batch);
					} catch (Exception e) {
						LogUtil.sysout("D-1 DB INSERT 오류");
						e.printStackTrace();
					}
					return this;
				}
			};
			taskItem.setObj("files", chunk);
			taskItem.setObj("DBID", DBID);
			taskItem.setObj("sysId", sysId);
			taskItem.setId(executorServiceId + "-" + n);
			taskItemList.add(taskItem);
		}
		ensureExecutorService(executorServiceId);
		this.taskHandler.doTheSyncTasks(executorServiceId, taskItemList);
	}

	/* ===================================================================
	 * D-2. UI 링크 추출 → TB_UI_FUNC_MAPPING INSERT
	 * DB에서 UI(fileName) 조회 후 원본 JSP 재파싱
	 * =================================================================== */

	private void analyzeUiLink(String DBID, String sysId) throws Exception {
		getLogger().info("/*** D-2.UI 링크 → TB_UI_FUNC_MAPPING INSERT");
		List<UiVo> allUi = DbGen.selectAllUiVo(DBID, sysId);
		List<String[]> batch = new ArrayList<>();
		TossParseUi parser = new TossParseUi();

		for (UiVo uiVo : allUi) {
			String uiFile = uiVo.getFileName();
			if (StringUtil.isEmpty(uiFile) || !FileUtil.isFileExist(uiFile)) continue;
			try {
				List<String> links = parser.getUiLinkList(uiFile);
				for (String link : links) {
					if (!StringUtil.isEmpty(link) && !StringUtil.isEmpty(uiVo.getUiId())) {
						batch.add(new String[]{uiVo.getUiId(), link.trim()});
					}
				}
			} catch (Exception e) {
				LogUtil.sysout("D-2 오류. uiFile[" + uiFile + "]");
				e.printStackTrace();
			}
		}

		if (!batch.isEmpty()) DbGen.insertBatchTB_UI_FUNC_MAPPING(DBID, sysId, batch);
	}

	/* ===================================================================
	 * F. METRIX 집계 → TB_METRIX INSERT
	 * makeAnalyzeBasicFileConts + makeAnalyzeCallChainFileConts 를 DB 기반으로 구현
	 * =================================================================== */

	private void saveMetrix(String DBID, String sysId) throws Exception {
		getLogger().info("/*** F-1.METRIX 기본구조 생성 (DB 기반)");

		/* F-1-1. CT 클래스 메서드 + URL 맵 */
		List<MtdVo> ctMtdList = DbGen.selectCtMtdWithUrl(DBID, sysId);
		Map<String, List<DataSet>> urlDsMap = new LinkedHashMap<>();
		for (MtdVo mtdVo : ctMtdList) {
			String url = mtdVo.getMethodUrl();
			if (StringUtil.isEmpty(url)) continue;
			DataSet dsRow = new DataSet();
			dsRow.setDatum("BASIC_ID", mtdVo.getFunctionId());
			dsRow.setDatum("BASIC_URL", url);
			urlDsMap.computeIfAbsent(url, k -> new ArrayList<>()).add(dsRow);
		}

		/* F-1-2. UI 링크 맵 (link → List<UiVo>) */
		List<UiVo> allUiList = DbGen.selectAllUiVo(DBID, sysId);
		Map<String, List<String>> uiLinkMap = DbGen.selectAllUiLinks(DBID, sysId);
		Map<String, List<UiVo>> urlUiMap = new LinkedHashMap<>();
		for (UiVo uiVo : allUiList) {
			List<String> links = uiLinkMap.getOrDefault(uiVo.getUiId(), new ArrayList<>());
			uiVo.setLinkList(links);
			for (String link : links) {
				if (!StringUtil.isEmpty(link)) {
					urlUiMap.computeIfAbsent(link.trim(), k -> new ArrayList<>()).add(uiVo);
				}
			}
		}

		/* F-1-3. urlDsMap × urlUiMap 조합 */
		List<DataSet> metrixBase = new ArrayList<>();
		for (Map.Entry<String, List<DataSet>> entry : urlDsMap.entrySet()) {
			String urlDs = entry.getKey();
			DataSet dsRow = entry.getValue().get(0);
			boolean uiAdded = false;
			for (Map.Entry<String, List<UiVo>> uiEntry : urlUiMap.entrySet()) {
				if (uiEntry.getKey().indexOf(urlDs) > -1) {
					for (UiVo uiVo : uiEntry.getValue()) {
						DataSet copy = dsRow.copy();
						copy.setDatum("UI_ID", uiVo.getUiId());
						copy.setDatum("UI_NM", StringUtil.nullCheck(uiVo.getUiName(), ""));
						metrixBase.add(copy);
						uiAdded = true;
					}
				}
			}
			if (!uiAdded) metrixBase.add(dsRow);
		}

		getLogger().info("/*** F-2.METRIX 호출체인 추적 (DB 캐시 기반)");

		/* F-2. 전체 MtdVo + ClzzVo 캐시 로드 */
		List<MtdVo> allMtd = DbGen.selectAllMtdVoWithCalls(DBID, sysId);
		Map<String, MtdVo> mtdVoCache = new HashMap<>();
		for (MtdVo v : allMtd) mtdVoCache.put(v.getFunctionId(), v);

		List<ClzzVo> allClzz = DbGen.selectAllClzzVo(DBID, sysId);
		Map<String, ClzzVo> clzzVoCache = new HashMap<>();
		for (ClzzVo v : allClzz) clzzVoCache.put(v.getClassId(), v);

		/* F-3. 호출체인 재귀 추적 */
		List<DataSet> metrixList = new ArrayList<>();
		for (DataSet dsRow : metrixBase) {
			String functionId = dsRow.getDatum("BASIC_ID");
			metrixList.addAll(makeCallChain(dsRow, functionId, 1, new ArrayList<>(), mtdVoCache, clzzVoCache));
		}

		/* F-4. TB_METRIX INSERT */
		getLogger().info("/*** F-4.TB_METRIX INSERT 총 " + metrixList.size() + "건");
		deleteTB_METRIX(DBID, sysId);
		DbGen.insertBatchTB_METRIX(DBID, sysId, metrixList);
	}

	/** makeAnalyzeCallChainFileConts 의 DB 캐시 기반 버전 */
	private List<DataSet> makeCallChain(DataSet dsRow, String functionId, int callLevel,
		List<String> callStack, Map<String, MtdVo> mtdCache, Map<String, ClzzVo> clzzCache) {

		List<DataSet> dsList = new ArrayList<>();
		callStack.add(functionId);

		MtdVo mtdVo = mtdCache.get(functionId);
		if (mtdVo == null) return dsList;

		ClzzVo clzzVo = clzzCache.get(mtdVo.getClassId());

		dsRow.setDatum("FUNCTION_ID_" + callLevel, mtdVo.getFunctionId());
		dsRow.setDatum("FUNCTION_NAME_" + callLevel, mtdVo.getMethodName());
		dsRow.setDatum("CLASS_KIND_" + callLevel, "");
		if (clzzVo != null && clzzVo.getClassKind() != null) {
			dsRow.setDatum("CLASS_KIND_" + callLevel, clzzVo.getClassKind().getClzzKindCd());
		}
		dsRow.setDatum("CALL_LEVEL", String.valueOf(callLevel));

		if (mtdVo.getCallTblVoList() != null) {
			StringBuilder tblBuff = new StringBuilder();
			for (String tblId : mtdVo.getCallTblVoList()) {
				if (tblBuff.length() > 0) tblBuff.append("|");
				tblBuff.append(tblId);
			}
			dsRow.setDatum("CALL_TBL", tblBuff.toString());
		}

		List<String> callMtdList = mtdVo.getCallMtdVoList();
		if (callMtdList != null && !callMtdList.isEmpty()) {
			int childLevel = callLevel + 1;
			int index = 0;
			for (String callFuncId : callMtdList) {
				if (!mtdCache.containsKey(callFuncId)) continue;
				if (callStack.contains(callFuncId)) continue;
				List<String> stackCopy = new ArrayList<>(callStack);
				if (index == 0) {
					dsList.addAll(makeCallChain(dsRow, callFuncId, childLevel, stackCopy, mtdCache, clzzCache));
				} else {
					DataSet dsRowCopy = dsRow.copy();
					dsList.addAll(makeCallChain(dsRowCopy, callFuncId, childLevel, stackCopy, mtdCache, clzzCache));
				}
				index++;
			}
		} else {
			if (mtdCache.containsKey(functionId)) dsList.add(dsRow);
		}
		return dsList;
	}

	/** TB_METRIX DELETE (saveMetrix 내부 사용) */
	public static void deleteTB_METRIX(String DBID, String sysId) throws Exception {
		net.dstone.common.utils.DbUtil db = null;
		try {
			db = new net.dstone.common.utils.DbUtil(DBID);
			db.getConnection();
			db.setQuery("DELETE FROM TB_METRIX WHERE SYS_ID = ? AND WORKER_ID = 'SYSTEM'");
			db.pstmt.setString(1, sysId);
			db.delete();
		} finally {
			if (db != null) db.release();
		}
	}

	/* ===================================================================
	 * saveToDb() — 하위호환을 위해 유지. 분석은 analyze() 에서 완료되므로
	 * TB_SYS MERGE 만 수행.
	 * =================================================================== */

	public void saveToDb(String DBID) {
		try {
			String sysId = AppAnalyzer.CONF.getNode("SYS_ID").getTextContent();
			net.dstone.boot.common.tools.analyzer.vo.SysVo sysVo = new net.dstone.boot.common.tools.analyzer.vo.SysVo();
			sysVo.setSysId(sysId);
			sysVo.setSysNm(AppAnalyzer.CONF.getNode("SYS_NM") != null ? AppAnalyzer.CONF.getNode("SYS_NM").getTextContent() : "");
			sysVo.setWrithPath(AppAnalyzer.WRITE_PATH);
			sysVo.setSaveFileName(AppAnalyzer.SAVE_FILE_NAME);
			sysVo.setDbId(AppAnalyzer.DBID);
			sysVo.setIsTableListFromDb(String.valueOf(AppAnalyzer.IS_TABLE_LIST_FROM_DB));
			sysVo.setTableNameLikeStr(AppAnalyzer.TABLE_NAME_LIKE_STR);
			sysVo.setTableListFileName(AppAnalyzer.TABLE_LIST_FILE_NAME);
			sysVo.setIsSaveToDb(String.valueOf(AppAnalyzer.IS_SAVE_TO_DB));
			sysVo.setWorkerThreadKind(String.valueOf(AppAnalyzer.WORKER_THREAD_KIND));
			sysVo.setWorkerThreadNum(String.valueOf(AppAnalyzer.WORKER_THREAD_NUM));
			DbGen.mergeTB_SYS(DBID, sysVo);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/* ===================================================================
	 * 유틸
	 * =================================================================== */

	private void ensureExecutorService(String id) {
		if (!this.taskHandler.isExecutorServiceExists(id)) {
			if (AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_SINGLE) {
				this.taskHandler.addSingleExecutorService(id);
			} else if (AppAnalyzer.WORKER_THREAD_KIND == AppAnalyzer.WORKER_THREAD_KIND_FIXED) {
				this.taskHandler.addFixedExecutorService(id, AppAnalyzer.WORKER_THREAD_NUM);
			} else {
				this.taskHandler.addCachedExecutorService(id);
			}
		}
	}

}
