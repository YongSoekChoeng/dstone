package net.dstone.boot.common.tools.analyzer.vo;

import java.util.List;
import java.util.Map;

import net.dstone.boot.common.tools.analyzer.consts.ClzzKind;

public class ClzzVo {
	
	/**
	 * 패키지ID
	 */
	protected String packageId;
	/**
	 * 클래스ID
	 */
	protected String classId;
	/**
	 * 클래스명
	 */
	protected String className;
	/**
	 * 기능종류(UI:화면/JS:자바스크립트/CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지)
	 */
	protected ClzzKind classKind;
	/**
	 * 어노테이션으로 표현된 리소스ID
	 */
	protected String resourceId;
	/**
	 * 클래스or인터페이스(C:클래스/I:인터페이스)
	 */
	protected String classOrInterface;
	/**
	 * 상위인터페이스ID(인터페이스를 구현한 클래스의 경우에만 존재)
	 */
	protected List<String> interfaceIdList;
	/**
	 * 상위클래스ID
	 */
	protected String parentClassId;
	/**
	 * 인터페이스구현하위클래스ID목록(인터페이스인 경우에만 존재)
	 */
	protected List<String> implClassIdList;
	/**
	 * 호출알리아스리스트<맵>
	 * 맵항목- Full클래스,알리아스.
	 * (예: FULL_CLASS:aaa.bbb.Clzz2, ALIAS:clzz2)
	 */
	protected List<Map<String, String>> callClassAlias;
	/**
	 * 파일명
	 */
	protected String fileName;
	
	public String getPackageId() {
		return packageId;
	}
	public void setPackageId(String packageId) {
		this.packageId = packageId;
	}
	public String getClassId() {
		return classId;
	}
	public void setClassId(String classId) {
		this.classId = classId;
	}
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	public ClzzKind getClassKind() {
		return classKind;
	}
	public void setClassKind(ClzzKind classKind) {
		this.classKind = classKind;
	}
	public String getResourceId() {
		return resourceId;
	}
	public void setResourceId(String resourceId) {
		this.resourceId = resourceId;
	}
	public String getClassOrInterface() {
		return classOrInterface;
	}
	public void setClassOrInterface(String classOrInterface) {
		this.classOrInterface = classOrInterface;
	}
	public List<String> getInterfaceIdList() {
		return interfaceIdList;
	}
	public void setInterfaceIdList(List<String> interfaceIdList) {
		this.interfaceIdList = interfaceIdList;
	}
	public String getParentClassId() {
		return parentClassId;
	}
	public void setParentClassId(String parentClassId) {
		this.parentClassId = parentClassId;
	}
	public List<String> getImplClassIdList() {
		return implClassIdList;
	}
	public void setImplClassIdList(List<String> implClassIdList) {
		this.implClassIdList = implClassIdList;
	}
	public List<Map<String, String>> getCallClassAlias() {
		return callClassAlias;
	}
	public void setCallClassAlias(List<Map<String, String>> callClassAlias) {
		this.callClassAlias = callClassAlias;
	}
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	@Override
	public String toString() {
		return "ClzzVo [packageId=" + packageId + ", classId=" + classId + ", className=" + className + ", classKind="
				+ classKind + ", resourceId=" + resourceId + ", classOrInterface=" + classOrInterface + ", interfaceIdList="
				+ interfaceIdList + ", parentClassId=" + parentClassId + ", implClassIdList=" + implClassIdList
				+ ", callClassAlias=" + callClassAlias + ", fileName=" + fileName + "]";
	}
	
}
