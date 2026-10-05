package net.dstone.knowledge.rag.model;

/**
 * <pre>
 * 문서를 나눈 조각 하나. 검색 결과로 돌려주는 단위이고 rag_chunk 테이블의 한 행이 됩니다.
 * </pre>
 */
public class ChunkRow {

	/** 어느 문서의 조각인지 */
	private String documentId;

	private long revisionId;

	/** 문서 안에서의 순서(0부터) */
	private int chunkNo;

	/** FILE / TYPE / METHOD */
	private String chunkType;

	/** 임베딩하고 검색 결과로 돌려줄 글 */
	private String content;

	/** content의 SHA-256. 임베딩을 찾는 키 */
	private String contentHash;

	private int charCount;

	/** 검색을 거를 때 쓰는 정보(전체 이름, 계층 등) */
	private String metadataJson;

	private long fileId;

	/** 원본 파일에서 이 조각이 시작하는 줄 */
	private Integer lineStart;

	private Integer lineEnd;

	public String getDocumentId() {
		return documentId;
	}

	public void setDocumentId(String documentId) {
		this.documentId = documentId;
	}

	public long getRevisionId() {
		return revisionId;
	}

	public void setRevisionId(long revisionId) {
		this.revisionId = revisionId;
	}

	public int getChunkNo() {
		return chunkNo;
	}

	public void setChunkNo(int chunkNo) {
		this.chunkNo = chunkNo;
	}

	public String getChunkType() {
		return chunkType;
	}

	public void setChunkType(String chunkType) {
		this.chunkType = chunkType;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getContentHash() {
		return contentHash;
	}

	public void setContentHash(String contentHash) {
		this.contentHash = contentHash;
	}

	public int getCharCount() {
		return charCount;
	}

	public void setCharCount(int charCount) {
		this.charCount = charCount;
	}

	public String getMetadataJson() {
		return metadataJson;
	}

	public void setMetadataJson(String metadataJson) {
		this.metadataJson = metadataJson;
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
