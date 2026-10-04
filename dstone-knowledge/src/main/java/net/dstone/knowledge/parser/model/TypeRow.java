package net.dstone.knowledge.parser.model;

/**
 * 타입 하나(class / interface / enum / record / annotation / 익명 클래스). analysis_symbol 테이블의 한 행이 됩니다.
 */
public class TypeRow {

	private long revisionId;

	/** 리비전과 무관한 안정 ID */
	private String symbolId;

	/** CLASS / INTERFACE / ENUM / RECORD / ANNOTATION / ANONYMOUS */
	private String kind;

	/** 패키지를 포함한 전체 이름. 중첩 타입은 Outer.Inner, 익명 클래스는 Outer$1 */
	private String fqn;

	private String simpleName;

	private String packageName;

	/** 중첩/익명/지역 타입이면 바깥 타입의 ID */
	private String outerSymbolId;

	/** public / protected / private / package */
	private String visibility;

	private boolean isAbstract;

	private boolean isFinal;

	private boolean isStatic;

	/** 덧붙이는 정보(JSON). 예: 지역 클래스 여부, Lombok이 만든 타입 여부 */
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

	public String getSymbolId() {
		return symbolId;
	}

	public void setSymbolId(String symbolId) {
		this.symbolId = symbolId;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public String getFqn() {
		return fqn;
	}

	public void setFqn(String fqn) {
		this.fqn = fqn;
	}

	public String getSimpleName() {
		return simpleName;
	}

	public void setSimpleName(String simpleName) {
		this.simpleName = simpleName;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getOuterSymbolId() {
		return outerSymbolId;
	}

	public void setOuterSymbolId(String outerSymbolId) {
		this.outerSymbolId = outerSymbolId;
	}

	public String getVisibility() {
		return visibility;
	}

	public void setVisibility(String visibility) {
		this.visibility = visibility;
	}

	public boolean getIsAbstract() {
		return isAbstract;
	}

	public void setIsAbstract(boolean isAbstract) {
		this.isAbstract = isAbstract;
	}

	public boolean getIsFinal() {
		return isFinal;
	}

	public void setIsFinal(boolean isFinal) {
		this.isFinal = isFinal;
	}

	public boolean getIsStatic() {
		return isStatic;
	}

	public void setIsStatic(boolean isStatic) {
		this.isStatic = isStatic;
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
