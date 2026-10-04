package net.dstone.knowledge.parser.model;

/**
 * <pre>
 * 애노테이션이 붙은 자리 하나. analysis_annotation 테이블의 한 행이 됩니다.
 * </pre>
 */
public class AnnotationRow {

	private long revisionId;

	/** TYPE / METHOD / FIELD / PARAMETER */
	private String targetKind;

	/** 붙은 대상의 ID. PARAMETER면 그 메소드의 ID */
	private String targetId;

	/** 단순 이름. 예: Transactional */
	private String annotationName;

	/** import로 알아낸 전체 이름. 모르면 null */
	private String annotationFqn;

	/** 속성(JSON). PARAMETER면 $parameter 키에 파라미터 이름이 들어간다 */
	private String attributesJson;

	private long fileId;

	private Integer lineStart;

	public long getRevisionId() {
		return revisionId;
	}

	public void setRevisionId(long revisionId) {
		this.revisionId = revisionId;
	}

	public String getTargetKind() {
		return targetKind;
	}

	public void setTargetKind(String targetKind) {
		this.targetKind = targetKind;
	}

	public String getTargetId() {
		return targetId;
	}

	public void setTargetId(String targetId) {
		this.targetId = targetId;
	}

	public String getAnnotationName() {
		return annotationName;
	}

	public void setAnnotationName(String annotationName) {
		this.annotationName = annotationName;
	}

	public String getAnnotationFqn() {
		return annotationFqn;
	}

	public void setAnnotationFqn(String annotationFqn) {
		this.annotationFqn = annotationFqn;
	}

	public String getAttributesJson() {
		return attributesJson;
	}

	public void setAttributesJson(String attributesJson) {
		this.attributesJson = attributesJson;
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
