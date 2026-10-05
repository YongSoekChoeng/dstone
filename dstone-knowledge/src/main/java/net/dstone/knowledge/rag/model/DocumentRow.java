package net.dstone.knowledge.rag.model;

/**
 * <pre>
 * 논리 문서 하나(파일/타입/메소드 단위). rag_document 테이블의 한 행이 됩니다.
 * </pre>
 */
public class DocumentRow {

	/** 같은 대상이면 리비전이 달라도 같은 값. 예: METHOD:메소드ID */
	private String documentId;

	private String projectId;

	private long revisionId;

	/** FILE / TYPE / METHOD */
	private String docType;

	/** 이 문서의 근거가 된 대상의 종류: FILE / TYPE / METHOD */
	private String refKind;

	/** 그 대상의 ID(파일은 파일 번호) */
	private String refId;

	private String title;

	/** 원본 파일의 경로(프로젝트 루트 기준) */
	private String sourcePath;

	private String metadataJson;

	/** 문서 전체 내용의 SHA-256 */
	private String contentHash;

	public String getDocumentId() {
		return documentId;
	}

	public void setDocumentId(String documentId) {
		this.documentId = documentId;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public long getRevisionId() {
		return revisionId;
	}

	public void setRevisionId(long revisionId) {
		this.revisionId = revisionId;
	}

	public String getDocType() {
		return docType;
	}

	public void setDocType(String docType) {
		this.docType = docType;
	}

	public String getRefKind() {
		return refKind;
	}

	public void setRefKind(String refKind) {
		this.refKind = refKind;
	}

	public String getRefId() {
		return refId;
	}

	public void setRefId(String refId) {
		this.refId = refId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}

	public String getMetadataJson() {
		return metadataJson;
	}

	public void setMetadataJson(String metadataJson) {
		this.metadataJson = metadataJson;
	}

	public String getContentHash() {
		return contentHash;
	}

	public void setContentHash(String contentHash) {
		this.contentHash = contentHash;
	}

}
