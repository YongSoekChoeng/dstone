package net.dstone.boot.common.tools.analyzer.vo;

import java.util.List;

public class MtdVo {

	/**
	 * 기능ID
	 */
	protected String functionId;
	/**
	 * 클래스ID
	 */
	protected String classId;
	/**
	 * 메서드ID
	 */
	protected String methodId;
	/**
	 * 메서드명
	 */
	protected String methodName;
	/**
	 * 메서드URL
	 */
	protected String methodUrl;
	/**
	 * 메서드Body
	 */
	protected String methodBody;
	/**
	 * 파일명
	 */
	protected String fileName;

	protected List<String> callMtdVoList;
	protected List<String> callTblVoList;
	
	public String getFunctionId() {
		return functionId;
	}
	public void setFunctionId(String functionId) {
		this.functionId = functionId;
	}
	public String getMethodId() {
		return methodId;
	}
	public void setMethodId(String methodId) {
		this.methodId = methodId;
	}
	public String getMethodName() {
		return methodName;
	}
	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}
	public String getMethodUrl() {
		return methodUrl;
	}
	public void setMethodUrl(String methodUrl) {
		this.methodUrl = methodUrl;
	}
	public String getMethodBody() {
		return methodBody;
	}
	public void setMethodBody(String methodBody) {
		this.methodBody = methodBody;
	}
	public List<String> getCallMtdVoList() {
		return callMtdVoList;
	}
	public void setCallMtdVoList(List<String> callsMtdVoList) {
		this.callMtdVoList = callsMtdVoList;
	}
	public List<String> getCallTblVoList() {
		return callTblVoList;
	}
	public void setCallTblVoList(List<String> callsTblVoList) {
		this.callTblVoList = callsTblVoList;
	}
	
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	public String getClassId() {
		return classId;
	}
	public void setClassId(String classId) {
		this.classId = classId;
	}
	@Override
	public String toString() {
		return "MtdVo [functionId=" + functionId + ", classId=" + classId + ", methodId=" + methodId + ", methodName="
				+ methodName + ", methodUrl=" + methodUrl + ", methodBody=" + methodBody + ", fileName=" + fileName
				+ ", callMtdVoList=" + callMtdVoList + ", callTblVoList=" + callTblVoList + "]";
	}
	
}
