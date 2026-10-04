package net.dstone.knowledge.scanner;

/**
 * <pre>
 * 스캔한 파일 하나의 정보입니다. analysis_file 테이블의 한 행이 됩니다.
 * </pre>
 */
public class ScannedFile {

	private long revisionId;

	/** 멀티모듈일 때 이 파일이 속한 모듈의 경로. 모듈이 따로 없으면 null */
	private String module;

	/** 프로젝트 루트 기준 상대 경로. 구분자는 항상 '/' */
	private String path;

	/** Java 파일이 속한 소스 루트(예: src/main/java, WEB-INF/src). 못 찾았으면 null */
	private String sourceRoot;

	/** JAVA / JSP / XML / YAML / PROPERTIES / GRADLE */
	private String language;

	/** SOURCE / MYBATIS_MAPPER / SPRING_XML / WEB_XML / CONFIG / BUILD ... */
	private String fileType;

	private String packageName;

	/** 실제로 읽을 때 쓴 인코딩 */
	private String encoding;

	/** 파일 내용의 SHA-256 */
	private String checksum;

	private long sizeBytes;

	private Integer lineCount;

	/** PENDING(아직 파싱 전) / SKIPPED(너무 커서 읽지 않음) */
	private String parseStatus;

	private String parseError;

	public long getRevisionId() {
		return revisionId;
	}

	public void setRevisionId(long revisionId) {
		this.revisionId = revisionId;
	}

	public String getModule() {
		return module;
	}

	public void setModule(String module) {
		this.module = module;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getSourceRoot() {
		return sourceRoot;
	}

	public void setSourceRoot(String sourceRoot) {
		this.sourceRoot = sourceRoot;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getFileType() {
		return fileType;
	}

	public void setFileType(String fileType) {
		this.fileType = fileType;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public String getChecksum() {
		return checksum;
	}

	public void setChecksum(String checksum) {
		this.checksum = checksum;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public void setSizeBytes(long sizeBytes) {
		this.sizeBytes = sizeBytes;
	}

	public Integer getLineCount() {
		return lineCount;
	}

	public void setLineCount(Integer lineCount) {
		this.lineCount = lineCount;
	}

	public String getParseStatus() {
		return parseStatus;
	}

	public void setParseStatus(String parseStatus) {
		this.parseStatus = parseStatus;
	}

	public String getParseError() {
		return parseError;
	}

	public void setParseError(String parseError) {
		this.parseError = parseError;
	}

}
