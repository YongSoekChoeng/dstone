package net.dstone.boot.common.tools.analyzer.svc.mtd;

import java.util.List;
import java.util.Map;

public interface ParseMtd {
	/**
	 * 파일로부터 메소드ID/메소드명/메소드URL/메소드내용 이 담긴 메소드정보목록 추출
	 * LIST[ MAP<메서드ID(METHOD_ID), 메서드명(METHOD_NAME), 메서드URL(METHOD_URL), 메서드바디(METHOD_BODY)> ]
	 * @param classFile
	 * @return
	 */
	public List<Map<String, String>> getMtdInfoList(String classFile) throws Exception ;

	/**
	 * 호출메소드 목록 추출
	 * @param analyzedMethodFile
	 * @return
	 */
	public List<String> getCallMtdList(String analyzedMethodFile) throws Exception ;
	
	/**
	 * 호출테이블 목록 추출
	 * 파일에서 매칭되는 queryKey를 찾아내어 해당 [쿼리정보.테이블목록]을 읽어온 후 [메소드정보.호출테이블]에 저장.
	 * @param analyzedMethodFile
	 * @return
	 */
	public List<String> getCallTblList(String analyzedMethodFile) throws Exception ;
}
