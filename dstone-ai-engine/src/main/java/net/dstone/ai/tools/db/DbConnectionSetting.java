package net.dstone.ai.tools.db;

/**
 * <pre>
 * DB 조회 Tool(DbQueryTool)이 접속할 DB 하나의 설정입니다.
 * dstone.ai.tool.db.connections.{별칭} 아래 값이 여기에 담깁니다.
 *
 * LLM은 별칭만 알고, 주소와 계정은 모릅니다. 비밀번호는 ENC(...)로 적습니다.
 * </pre>
 */
public class DbConnectionSetting {

	/** JDBC 주소. 예: jdbc:mysql://host:3306/sampleDB */
	private String url;

	/** 접속 계정. 읽기 권한만 있는 계정을 권한다 */
	private String username;

	/** 비밀번호. ENC(...)로 적는다 */
	private String password;

	/** 테이블을 찾을 기본 스키마(MySQL은 데이터베이스 이름). 비우면 접속한 계정의 기본 스키마 */
	private String schema;

	/** 사람과 LLM이 읽는 설명. 예: "sample-app 운영 복제 DB" */
	private String description;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getSchema() {
		return schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	/** 설정이 바뀌었는지 비교할 때 쓰는 값. 바뀌면 커넥션 풀을 새로 만든다 */
	String signature() {
		return url + "\n" + username + "\n" + password;
	}

}
