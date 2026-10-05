package net.dstone.knowledge.parser.model;

/**
 * <pre>
 * 아직 누구를 가리키는지 모르는 참조 하나. analysis_reference 테이블의 한 행이 됩니다. RESOLVE 단계가 풀어서 관계로 옮깁니다.
 * </pre>
 */
public class ReferenceRow {

	private long revisionId;

	private long fileId;

	/** 참조가 들어 있는 쪽의 종류: TYPE / METHOD / FIELD */
	private String fromKind;

	private String fromId;

	/** CALL / CREATE / FIELD_ACCESS / TYPE_USE / THROWS / EXTENDS / IMPLEMENTS / ANONYMOUS_SUPER */
	private String refKind;

	/** 메소드/타입/필드 이름(소스에 적힌 그대로) */
	private String name;

	/** 호출 대상 식. 예: orderService, this.dao */
	private String scopeText;

	private Integer argCount;

	/** 첫 인자를 소스에 적힌 그대로. 문자열이 들어 있을 때만 담는다. 예: getOrderMapper() + "findAll" */
	private String argText;

	private Integer lineStart;

	private Integer columnStart;

	public long getRevisionId() {
		return revisionId;
	}

	public void setRevisionId(long revisionId) {
		this.revisionId = revisionId;
	}

	public long getFileId() {
		return fileId;
	}

	public void setFileId(long fileId) {
		this.fileId = fileId;
	}

	public String getFromKind() {
		return fromKind;
	}

	public void setFromKind(String fromKind) {
		this.fromKind = fromKind;
	}

	public String getFromId() {
		return fromId;
	}

	public void setFromId(String fromId) {
		this.fromId = fromId;
	}

	public String getRefKind() {
		return refKind;
	}

	public void setRefKind(String refKind) {
		this.refKind = refKind;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getScopeText() {
		return scopeText;
	}

	public void setScopeText(String scopeText) {
		this.scopeText = scopeText;
	}

	public Integer getArgCount() {
		return argCount;
	}

	public void setArgCount(Integer argCount) {
		this.argCount = argCount;
	}

	public Integer getLineStart() {
		return lineStart;
	}

	public void setLineStart(Integer lineStart) {
		this.lineStart = lineStart;
	}

	public Integer getColumnStart() {
		return columnStart;
	}

	public void setColumnStart(Integer columnStart) {
		this.columnStart = columnStart;
	}

	public String getArgText() {
		return argText;
	}

	public void setArgText(String argText) {
		this.argText = argText;
	}

}
