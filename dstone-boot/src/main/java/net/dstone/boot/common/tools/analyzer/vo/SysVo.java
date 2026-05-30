package net.dstone.boot.common.tools.analyzer.vo;

public class SysVo {
	
	/**
	 * 시스템ID
	 */
	protected String sysId;

	/**
	 * 시스템명
	 */
	protected String sysNm;
	
	/**
	 * 분석결과생성경로
	 */
	protected String writhPath;
	
	/**
	 * 분석결과저장파일명
	 */
	protected String saveFileName;

	/**
	 * DBID
	 */
	protected String dbId;

	/**
	 * 테이블목록을DB로부터읽어올지여부
	 */
	protected String isTableListFromDb;

	/**
	 * 테이블명을DB로부터읽어올때적용할프리픽스
	 */
	protected String tableNameLikeStr;

	/**
	 * 테이블목록정보파일명
	 */
	protected String tableListFileName;

	/**
	 * 작업결과를DB에저장할지여부
	 */
	protected String isSaveToDb;

	/**
	 * 분석대상어플리케이션JDK홈
	 */
	protected String appJdkHome;

	/**
	 * 분석대상어플리케이션클래스패스
	 */
	protected String appClassPath;

	/**
	 * 분석작업을진행할쓰레드핸들러종류
	 */
	protected String workerThreadKind;

	/**
	 * 분석작업을진행할쓰레드갯수
	 */
	protected String workerThreadNum;

	public String getSysId() {
		return sysId;
	}

	public void setSysId(String sysId) {
		this.sysId = sysId;
	}

	public String getSysNm() {
		return sysNm;
	}

	public void setSysNm(String sysNm) {
		this.sysNm = sysNm;
	}

	public String getWrithPath() {
		return writhPath;
	}

	public void setWrithPath(String writhPath) {
		this.writhPath = writhPath;
	}

	public String getSaveFileName() {
		return saveFileName;
	}

	public void setSaveFileName(String saveFileName) {
		this.saveFileName = saveFileName;
	}

	public String getDbId() {
		return dbId;
	}

	public void setDbId(String dbId) {
		this.dbId = dbId;
	}

	public String getIsTableListFromDb() {
		return isTableListFromDb;
	}

	public void setIsTableListFromDb(String isTableListFromDb) {
		this.isTableListFromDb = isTableListFromDb;
	}

	public String getTableNameLikeStr() {
		return tableNameLikeStr;
	}

	public void setTableNameLikeStr(String tableNameLikeStr) {
		this.tableNameLikeStr = tableNameLikeStr;
	}

	public String getTableListFileName() {
		return tableListFileName;
	}

	public void setTableListFileName(String tableListFileName) {
		this.tableListFileName = tableListFileName;
	}

	public String getIsSaveToDb() {
		return isSaveToDb;
	}

	public void setIsSaveToDb(String isSaveToDb) {
		this.isSaveToDb = isSaveToDb;
	}

	public String getAppJdkHome() {
		return appJdkHome;
	}

	public void setAppJdkHome(String appJdkHome) {
		this.appJdkHome = appJdkHome;
	}

	public String getAppClassPath() {
		return appClassPath;
	}

	public void setAppClassPath(String appClassPath) {
		this.appClassPath = appClassPath;
	}

	public String getWorkerThreadKind() {
		return workerThreadKind;
	}

	public void setWorkerThreadKind(String workerThreadKind) {
		this.workerThreadKind = workerThreadKind;
	}

	public String getWorkerThreadNum() {
		return workerThreadNum;
	}

	public void setWorkerThreadNum(String workerThreadNum) {
		this.workerThreadNum = workerThreadNum;
	}

	@Override
	public String toString() {
		return "SysVo [sysId=" + sysId + ", sysNm=" + sysNm + ", writhPath=" + writhPath + ", saveFileName="
				+ saveFileName + ", dbId=" + dbId + ", isTableListFromDb=" + isTableListFromDb + ", tableNameLikeStr="
				+ tableNameLikeStr + ", tableListFileName=" + tableListFileName + ", isSaveToDb=" + isSaveToDb
				+ ", appJdkHome=" + appJdkHome + ", appClassPath=" + appClassPath + ", workerThreadKind="
				+ workerThreadKind + ", workerThreadNum=" + workerThreadNum + "]";
	}

	
}
