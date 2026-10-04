package net.dstone.knowledge.resolver.model;

/**
 * <pre>
 * 풀린 관계 하나. analysis_relation 테이블의 한 행이 됩니다(그래프의 간선).
 * </pre>
 */
public class RelationRow {

	private long revisionId;

	/** 관계가 시작하는 쪽의 종류: TYPE / METHOD / FIELD */
	private String fromKind;

	private String fromId;

	/** CALLS / CREATES / ACCESSES_FIELD / USES_TYPE / EXTENDS / IMPLEMENTS / THROWS ... */
	private String relationType;

	/** 가리키는 쪽의 종류. 프로젝트 밖이면 EXTERNAL_TYPE / EXTERNAL_METHOD / EXTERNAL_FIELD */
	private String toKind;

	/** 프로젝트 안의 대상 ID. 밖이면 null */
	private String toId;

	/** 프로젝트 밖 대상의 이름. 예: java.util.List.add(E) */
	private String toExternal;

	/** HIGH / MEDIUM / LOW */
	private String confidence;

	/** RESOLVED(해석기가 풀었음) / HEURISTIC(짐작) */
	private String resolutionStatus;

	/** 덧붙이는 정보(JSON). 예: 어떤 방법으로 찾았는지 */
	private String propertiesJson;

	/** 근거가 된 소스 파일 */
	private long fileId;

	private Integer lineStart;

	public long getRevisionId() {
		return revisionId;
	}

	public void setRevisionId(long revisionId) {
		this.revisionId = revisionId;
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

	public String getRelationType() {
		return relationType;
	}

	public void setRelationType(String relationType) {
		this.relationType = relationType;
	}

	public String getToKind() {
		return toKind;
	}

	public void setToKind(String toKind) {
		this.toKind = toKind;
	}

	public String getToId() {
		return toId;
	}

	public void setToId(String toId) {
		this.toId = toId;
	}

	public String getToExternal() {
		return toExternal;
	}

	public void setToExternal(String toExternal) {
		this.toExternal = toExternal;
	}

	public String getConfidence() {
		return confidence;
	}

	public void setConfidence(String confidence) {
		this.confidence = confidence;
	}

	public String getResolutionStatus() {
		return resolutionStatus;
	}

	public void setResolutionStatus(String resolutionStatus) {
		this.resolutionStatus = resolutionStatus;
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

}
