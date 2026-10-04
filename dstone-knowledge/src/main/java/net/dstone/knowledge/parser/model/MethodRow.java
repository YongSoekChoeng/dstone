package net.dstone.knowledge.parser.model;

/**
 * <pre>
 * 메소드 또는 생성자 하나. analysis_method 테이블의 한 행이 됩니다.
 * </pre>
 */
public class MethodRow {

	private long revisionId;

	private String methodId;

	private String ownerSymbolId;

	/** 메소드 이름. 생성자는 <init> */
	private String name;

	/** 이름(파라미터 타입들). 타입은 소스에 적힌 그대로이고 제네릭은 뺀다. 예: findOrders(String) */
	private String signature;

	/** 소스에 적힌 그대로의 반환 타입. 생성자는 null */
	private String returnType;

	private int paramCount;

	/** [{name, type}] */
	private String parametersJson;

	private String visibility;

	private boolean isStatic;

	private boolean isAbstract;

	private boolean isConstructor;

	/** 소스에는 없지만 컴파일하면 생기는 멤버(Lombok getter, 기본 생성자 등) */
	private boolean isSynthetic;

	/** 예: LOMBOK_GETTER, DEFAULT_CONSTRUCTOR */
	private String syntheticOrigin;

	private String propertiesJson;

	private long fileId;

	private Integer lineStart;

	private Integer lineEnd;

	public long getRevisionId() {
		return revisionId;
	}

	public void setRevisionId(long revisionId) {
		this.revisionId = revisionId;
	}

	public String getMethodId() {
		return methodId;
	}

	public void setMethodId(String methodId) {
		this.methodId = methodId;
	}

	public String getOwnerSymbolId() {
		return ownerSymbolId;
	}

	public void setOwnerSymbolId(String ownerSymbolId) {
		this.ownerSymbolId = ownerSymbolId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSignature() {
		return signature;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}

	public String getReturnType() {
		return returnType;
	}

	public void setReturnType(String returnType) {
		this.returnType = returnType;
	}

	public int getParamCount() {
		return paramCount;
	}

	public void setParamCount(int paramCount) {
		this.paramCount = paramCount;
	}

	public String getParametersJson() {
		return parametersJson;
	}

	public void setParametersJson(String parametersJson) {
		this.parametersJson = parametersJson;
	}

	public String getVisibility() {
		return visibility;
	}

	public void setVisibility(String visibility) {
		this.visibility = visibility;
	}

	public boolean getIsStatic() {
		return isStatic;
	}

	public void setIsStatic(boolean isStatic) {
		this.isStatic = isStatic;
	}

	public boolean getIsAbstract() {
		return isAbstract;
	}

	public void setIsAbstract(boolean isAbstract) {
		this.isAbstract = isAbstract;
	}

	public boolean getIsConstructor() {
		return isConstructor;
	}

	public void setIsConstructor(boolean isConstructor) {
		this.isConstructor = isConstructor;
	}

	public boolean getIsSynthetic() {
		return isSynthetic;
	}

	public void setIsSynthetic(boolean isSynthetic) {
		this.isSynthetic = isSynthetic;
	}

	public String getSyntheticOrigin() {
		return syntheticOrigin;
	}

	public void setSyntheticOrigin(String syntheticOrigin) {
		this.syntheticOrigin = syntheticOrigin;
	}

	public String getPropertiesJson() {
		return propertiesJson;
	}

	public void setPropertiesJson(String propertiesJson) {
		this.propertiesJson = propertiesJson;
	}

	public long getFileId() {
		return fileId;
	}

	public void setFileId(long fileId) {
		this.fileId = fileId;
	}

	public Integer getLineStart() {
		return lineStart;
	}

	public void setLineStart(Integer lineStart) {
		this.lineStart = lineStart;
	}

	public Integer getLineEnd() {
		return lineEnd;
	}

	public void setLineEnd(Integer lineEnd) {
		this.lineEnd = lineEnd;
	}

}
