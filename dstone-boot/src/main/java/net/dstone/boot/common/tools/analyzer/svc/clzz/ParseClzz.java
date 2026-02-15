package net.dstone.boot.common.tools.analyzer.svc.clzz;

import java.util.List;
import java.util.Map;

import net.dstone.boot.common.tools.analyzer.consts.ClzzKind;
import net.dstone.boot.common.tools.analyzer.vo.ClzzVo;

public interface ParseClzz {
	/**
	 * 패키지ID 추출
	 * @param classFile 클래스파일
	 * @return
	 */
	public String getPackageId(String classFile) throws Exception ;
	/**
	 * 클래스ID 추출
	 * @param classFile
	 * @return
	 */
	public String getClassId(String classFile) throws Exception ;
	/**
	 * 클래스명 추출
	 * @param classFile
	 * @return
	 */
	public String getClassName(String classFile) throws Exception ;
	/**
	 * 기능종류(UI:화면/JS:자바스크립트/CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지) 추출
	 * @param classFile
	 * @return
	 */
	public ClzzKind getClassKind(String classFile) throws Exception ;
	
	/**
	 * 어노테이션으로 표현된 리소스ID 추출
	 * @param classFile
	 * @return
	 */
	public String getResourceId(String classFile) throws Exception ;

	/**
	 * 클래스or인터페이스(C:클래스/I:인터페이스) 추출
	 * @param classFile
	 * @return
	 */
	public String getClassOrInterface(String classFile) throws Exception ;

	/**
	 * 인터페이스의 클래스ID 추출.(인터페이스를 구현한 클래스의 경우에만 존재)
	 * @param classFile
	 * @return
	 */
	public List<String> getInterfaceIdList(String classFile) throws Exception ;

	/**
	 * 부모클래스ID 추출
	 * @param classFile
	 * @return
	 */
	public String getParentClassId(String classFile) throws Exception ;
	
	/**
	 * 인터페이스구현클래스ID목록 추출.(인터페이스인 경우에만 존재)
	 * @param selfClzzVo
	 * @param otherClassFileList
	 * @return
	 */
	public List<String> getImplClassIdList(ClzzVo selfClzzVo, String[] otherClassFileList) throws Exception ;
	
	/**
	 * 호출알리아스 추출. 리스트<맵>을 반환. 맵항목- Full클래스,알리아스 .(예: FULL_CLASS:aaa.bbb.Clzz2, ALIAS:clzz2)
	 * @param selfClzzVo
	 * @param otherClassFileList
	 * @return
	 */
	public List<Map<String, String>> getCallClassAlias(ClzzVo selfClzzVo, String[] otherClassFileList) throws Exception ;
}
