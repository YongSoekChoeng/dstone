package net.dstone.knowledge.semantic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.common.util.KoreanName;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.scanner.EncodingDetector;

/**
 * <pre>
 * Java 소스에서 테이블 / 컬럼의 한글명을 찾아 analysis_resource 에 남깁니다.
 * DB 코멘트, DDL, SQL 주석 어디에도 한글명이 없을 때 쓰는 마지막 근거입니다(TableDictionaryService).
 *
 * 두 가지를 봅니다.
 *   1. JPA 엔티티(@Table 이 붙은 클래스): 테이블이 정해져 있다.
 *        @Table(name = "TB_ORDER") 클래스의 설명    → SOURCE_TABLE_NAME (name = TB_ORDER)
 *        필드(@Column(name) 이 있으면 그 이름)의 설명 → SOURCE_NAME (name = 컬럼, properties.table = TB_ORDER, via = ENTITY)
 *   2. 이름이 컬럼과 같은 필드(VO 등): mb_nm / mbNm → MB_NM. 어느 테이블의 것인지는 모른다.
 *        → SOURCE_NAME (name = 컬럼, properties.table = null, via = NAME)
 *      "이 리비전의 어떤 컬럼" 은 SQL 에서 쓰인 컬럼과 DDL 에 있는 컬럼입니다. 그래서 MyBatisPlugin(400) 뒤에 돕니다.
 *
 * 설명은 이 순서로 찾습니다.
 *   애노테이션: @ApiModelProperty("회사명"), @Schema(description = "회사명"), @Comment("회사명") (클래스는 @ApiModel / @Schema / @Comment)
 *   필드 줄 끝의 주석: private String mb_nm; /&#42; 회사명 VARCHAR(100) &#42;/   또는   // 회사명
 *   바로 위의 Javadoc / 블록 주석: /&#42;&#42; 회사명 &#42;/
 * 바로 위의 // 주석은 쓰지 않습니다. "// 회원사 정보" 처럼 아래 필드 여럿을 묶는 제목인 경우가 많기 때문입니다.
 * 한글이 들어 있는 글만 씁니다(KoreanName).
 *
 * 필드 주석은 DECLARE 가 저장하지 않으므로 소스 파일에서 그 줄만 읽습니다(DocumentPass 처럼). 최근에 읽은 파일 몇 개만 들고 있습니다.
 * </pre>
 */
@Component
public class SourceNamePlugin extends BaseObject implements SemanticPlugin {

	/** 들고 있을 파일 수. 행이 파일 순서로 오므로 몇 개면 충분하다 */
	private static final int MAX_CACHED_FILES = 16;

	/** 위쪽 주석을 찾을 때 거슬러 올라갈 최대 줄 수 */
	private static final int MAX_ABOVE_LINES = 12;

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Override
	public String name() {
		return "SOURCE_NAME";
	}

	@Override
	public int order() {
		// SQL 에서 쓰인 컬럼(MyBatisPlugin 400)이 있어야 이름을 맞춰 볼 수 있다.
		return 950;
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		SourceLines lines = new SourceLines(root);
		int tables = 0, entityColumns = 0, namedColumns = 0;

		List<Map<String, Object>> types = semanticDao.selectEntityTypes(revisionId);
		for (int i = 0; i < types.size(); i++) {
			context.checkCancelled();
			Map<String, Object> type = types.get(i);
			String table = tableName((String) type.get("table"));
			String text = describe(type, lines, false);
			if (table != null && text != null) {
				Map<String, Object> properties = new LinkedHashMap<String, Object>();
				properties.put("type", type.get("fqn"));
				insert(revisionId, "SOURCE_TABLE_NAME", table, text, properties, type);
				tables++;
			}
		}

		List<Map<String, Object>> entityFields = semanticDao.selectEntityFields(revisionId);
		for (int i = 0; i < entityFields.size(); i++) {
			context.checkCancelled();
			Map<String, Object> field = entityFields.get(i);
			String table = tableName((String) field.get("table"));
			String columnName = (String) field.get("columnName");
			String column = columnName != null ? tableName(columnName) : (String) field.get("column");
			String text = describe(field, lines, true);
			if (table != null && column != null && text != null) {
				insert(revisionId, "SOURCE_NAME", column, text, fieldProperties(field, table, "ENTITY"), field);
				entityColumns++;
			}
		}

		List<Map<String, Object>> fields = semanticDao.selectNameCandidateFields(revisionId);
		for (int i = 0; i < fields.size(); i++) {
			context.checkCancelled();
			Map<String, Object> field = fields.get(i);
			String text = describe(field, lines, true);
			if (text != null) {
				insert(revisionId, "SOURCE_NAME", (String) field.get("column"), text, fieldProperties(field, null, "NAME"), field);
				namedColumns++;
			}
		}
		return "엔티티 테이블 한글명 " + tables + "건, 엔티티 컬럼 " + entityColumns + "건, 이름이 같은 필드 " + namedColumns + "건 (후보 필드 " + fields.size() + "개)";
	}

	/** 애노테이션의 설명, 없으면 소스의 주석 */
	private String describe(Map<String, Object> row, SourceLines lines, boolean trailing) {
		String text = KoreanName.clean((String) row.get("description"));
		if (text != null) {
			return text;
		}
		String[] source = lines.of((String) row.get("path"), (String) row.get("encoding"));
		if (source == null) {
			return null;
		}
		if (trailing) {
			text = trailingComment(source, intOf(row.get("lineEnd")));
			if (text != null) {
				return text;
			}
		}
		return commentAbove(source, intOf(row.get("lineStart")));
	}

	/** 선언 마지막 줄의 ; 뒤에 붙은 주석 */
	static String trailingComment(String[] source, int lineEnd) {
		if (lineEnd < 1 || lineEnd > source.length) {
			return null;
		}
		String line = source[lineEnd - 1];
		int semicolon = line.lastIndexOf(';');
		if (semicolon < 0) {
			return null;
		}
		String rest = line.substring(semicolon + 1).trim();
		if (rest.startsWith("//")) {
			return KoreanName.clean(rest.substring(2));
		}
		if (rest.startsWith("/*")) {
			int end = rest.indexOf("*/");
			return KoreanName.clean(rest.substring(rest.startsWith("/**") ? 3 : 2, end < 0 ? rest.length() : end));
		}
		return null;
	}

	/** 선언 바로 위(애노테이션 줄은 건너뛴다)에서 끝나는 Javadoc / 블록 주석 */
	static String commentAbove(String[] source, int lineStart) {
		int i = lineStart - 2;
		// 선언 위에 붙은 애노테이션 줄은 건너뛴다(줄 번호가 애노테이션이 아니라 선언에서 시작하는 경우).
		while (i >= 0 && source[i].trim().startsWith("@")) {
			i--;
		}
		if (i < 0 || !source[i].trim().endsWith("*/")) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (int up = 0; i >= 0 && up < MAX_ABOVE_LINES; i--, up++) {
			String text = source[i].trim();
			sb.insert(0, text + "\n");
			if (text.startsWith("/*")) {
				String comment = sb.toString().trim();
				comment = comment.substring(comment.startsWith("/**") ? 3 : 2, comment.length() - 2);
				return KoreanName.clean(comment);
			}
		}
		return null;
	}

	private Map<String, Object> fieldProperties(Map<String, Object> field, String table, String via) {
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("table", table);
		properties.put("field", shortName((String) field.get("ownerFqn")) + "." + field.get("fieldName"));
		properties.put("via", via);
		return properties;
	}

	private void insert(long revisionId, String type, String name, String value, Map<String, Object> properties, Map<String, Object> at) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("revisionId", revisionId);
		row.put("resourceType", type);
		row.put("name", name);
		row.put("value", value);
		row.put("propertiesJson", JsonText.of(properties));
		row.put("fileId", at.get("fileId"));
		row.put("lineStart", at.get("lineStart"));
		semanticDao.insertSourceName(row);
	}

	/** "SCH"."TB_ORDER" → TB_ORDER. 이름이 아니면 null */
	private String tableName(String written) {
		if (written == null) {
			return null;
		}
		String name = written.replace("\"", "").replace("`", "").replace("[", "").replace("]", "").trim();
		int dot = name.lastIndexOf('.');
		if (dot >= 0) {
			name = name.substring(dot + 1);
		}
		name = name.toUpperCase(Locale.ROOT);
		return name.length() == 0 || name.length() > 300 ? null : name;
	}

	private String shortName(String fqn) {
		return fqn == null ? "" : fqn.substring(fqn.lastIndexOf('.') + 1);
	}

	private int intOf(Object value) {
		return value instanceof Number ? ((Number) value).intValue() : 0;
	}

	/**
	 * <pre>
	 * 최근에 읽은 소스 파일 몇 개의 줄들. 못 읽는 파일은 null
	 * </pre>
	 */
	private class SourceLines {

		private final Path root;

		private final Map<String, String[]> cache = new LinkedHashMap<String, String[]>(MAX_CACHED_FILES, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<String, String[]> eldest) {
				return size() > MAX_CACHED_FILES;
			}
		};

		SourceLines(Path root) {
			this.root = root;
		}

		String[] of(String path, String encoding) {
			if (path == null) {
				return null;
			}
			if (cache.containsKey(path)) {
				return cache.get(path);
			}
			String[] lines = null;
			try {
				byte[] bytes = Files.readAllBytes(root.resolve(path));
				lines = encodingDetector.decode(bytes, encoding).split("\r?\n", -1);
			} catch (Exception e) {
				// 분석 뒤에 지워졌거나 옮겨진 파일. 주석 없이 넘어간다.
				lines = null;
			}
			cache.put(path, lines);
			return lines;
		}
	}

}
