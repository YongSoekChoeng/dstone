package net.dstone.knowledge.analysis.pass03.declare.model;

/**
 * <pre>
 * 필드 하나(enum 상수 포함). analysis_field 테이블의 한 행이 됩니다.
 * </pre>
 */
public class FieldRow {

	/** 리비전 ID */
	private long revisionId;

	/** 필드 ID. 같은 필드면 리비전이 달라도 같은 값(해시) */
	private String fieldId;

	/** 이 필드를 가진 타입의 ID */
	private String ownerSymbolId;

	/** 필드 이름 */
	private String name;

	/** 소스에 적힌 그대로의 타입 */
	private String type;

	/** 접근 제한자: public / protected / package / private */
	private String visibility;

	/** static 여부 */
	private boolean isStatic;

	/** final 여부 */
	private boolean isFinal;

	/** 소스에는 없지만 컴파일하면 생기는 필드인지. 예: Lombok @Slf4j 의 log */
	private boolean isSynthetic;

	/** 합성 필드를 만든 근거. 예: LOMBOK_LOG */
	private String syntheticOrigin;

	/** 초기값을 적은 식(길면 자른다). 상수 값을 따라갈 때 쓴다 */
	private String initializerSummary;

	/** 선언된 파일 ID */
	private long fileId;

	/** 시작 줄 */
	private Integer lineStart;

	/** 끝 줄 */
	private Integer lineEnd;

	public long getRevisionId() {
		return revisionId;
	}

	public void setRevisionId(long revisionId) {
		this.revisionId = revisionId;
	}

	public String getFieldId() {
		return fieldId;
	}

	public void setFieldId(String fieldId) {
		this.fieldId = fieldId;
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

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
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

	public boolean getIsFinal() {
		return isFinal;
	}

	public void setIsFinal(boolean isFinal) {
		this.isFinal = isFinal;
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

	public String getInitializerSummary() {
		return initializerSummary;
	}

	public void setInitializerSummary(String initializerSummary) {
		this.initializerSummary = initializerSummary;
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
