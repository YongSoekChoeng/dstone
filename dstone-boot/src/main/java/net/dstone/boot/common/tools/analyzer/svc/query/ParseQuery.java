package net.dstone.boot.common.tools.analyzer.svc.query;

import java.util.List;
import java.util.Map;

public interface ParseQuery {
	
	/**
	 * 파일로부터 쿼리KEY(아이디)를 추출. 쿼리KEY는 파일명으로 사용됨.
	 * @param queryInfo(쿼리KEY를 추출할 수 있는 각종 정보를 담은 맵)
	 * @return
	 */
	public String getQueryKey(Map<String, String> queryInfo) throws Exception ;
	
	/**
	 * 파일로부터 쿼리정보목록 추출. 쿼리정보는 아래와 같은 항목을 추출해야 한다.
	 * SQL_NAMESPACE - 네임스페이스
	 * SQL_ID - SQL아이디
	 * SQL_KIND - SQL종류(SELECT/INSERT/UPDATE/DELETE)
	 * SQL_BODY - SQL구문
	 * @param file
	 * @return
	 */
	public List<Map<String, String>> getQueryInfoList(String queryFile) throws Exception ;
	
	/**
	 * 쿼리정보파일로부터 테이블ID정보목록 추출
	 * @param file
	 * @return
	 */
	public List<String> getTblInfoList(String queryInfoFile, List<String> allTblList) throws Exception ;
	
}
