package net.dstone.knowledge.api.service;

import java.util.regex.Pattern;

/**
 * <pre>
 * 노드 맵에서 쓰는 노드 ID를 읽습니다. ID의 모양만 보고 종류를 압니다.
 *
 *   T:TB_ORDER   테이블 (이름 그대로)
 *   M123         SQL 매퍼 파일 (123 = 파일 ID). 그 안의 statement 전부를 묶은 것
 *   F123         파일 (123 = 파일 ID). 화면(JSP)
 *   S123         SQL statement 하나 (123 = 매퍼 ID)
 *   그 밖        타입이나 메소드의 ID (영문 소문자와 숫자 40자). 어느 쪽인지는 DB를 봐야 압니다
 * </pre>
 */
public class GraphNodeId {

	public static final String TABLE = "TABLE";
	public static final String MAPPER = "MAPPER";
	public static final String FILE = "FILE";
	public static final String SQL = "SQL";
	public static final String SYMBOL = "SYMBOL";

	public static final String TABLE_PREFIX = "T:";

	private static final Pattern NUMBERED = Pattern.compile("[MFS][0-9]{1,18}");

	private static final Pattern HASH = Pattern.compile("[0-9a-f]{40}");

	private final String id;

	private final String kind;

	private GraphNodeId(String id, String kind) {
		this.id = id;
		this.kind = kind;
	}

	/**
	 * @return 읽은 ID. 모양이 맞지 않으면 null
	 */
	public static GraphNodeId parse(String text) {
		if (text == null) {
			return null;
		}
		String id = text.trim();
		if (id.startsWith(TABLE_PREFIX)) {
			return id.length() > TABLE_PREFIX.length() ? new GraphNodeId(id, TABLE) : null;
		}
		if (NUMBERED.matcher(id).matches()) {
			char first = id.charAt(0);
			return new GraphNodeId(id, first == 'M' ? MAPPER : first == 'F' ? FILE : SQL);
		}
		if (HASH.matcher(id).matches()) {
			return new GraphNodeId(id, SYMBOL);
		}
		return null;
	}

	public String getId() {
		return id;
	}

	/** TABLE / MAPPER / FILE / SQL / SYMBOL */
	public String getKind() {
		return kind;
	}

	/** 테이블 이름. 테이블이 아니면 null */
	public String getTableName() {
		return TABLE.equals(kind) ? id.substring(TABLE_PREFIX.length()) : null;
	}

	/** M / F / S 뒤의 숫자. 숫자가 붙지 않는 종류면 -1 */
	public long getNumber() {
		if (MAPPER.equals(kind) || FILE.equals(kind) || SQL.equals(kind)) {
			return Long.parseLong(id.substring(1));
		}
		return -1L;
	}

}
