package net.dstone.knowledge.dictionary;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.common.util.ErrorText;
import net.dstone.knowledge.common.util.KoreanName;

/**
 * <pre>
 * 프로젝트 담당자가 따로 정리해 둔 테이블 / 컬럼 사전 파일(CSV)에서 한글명을 읽습니다.
 *
 * DB 접속도 DDL 도 없는 프로젝트에서, 사람이 만든 테이블 목록이나 컬럼 사전이 있으면 그것을 씁니다.
 * 있어도 되고 없어도 되는 기능이라, 설정이 없으면 아무 파일도 읽지 않고 NOT_CONFIGURED 를 돌려줍니다.
 * 사전에는 사람이 짐작해서 붙인 이름이 섞여 있을 수 있으므로, 한글명을 찾는 순서에서 맨 뒤에 둡니다(TableDictionaryService).
 *
 * 설정(conf/application.yml, 프로젝트 ID 마다):
 *   dstone.knowledge.dictionary.file.{프로젝트ID}   CSV 파일 경로. 여러 개면 쉼표로 나누거나 YAML 목록으로 적는다.
 *                                                    상대 경로는 프로젝트의 소스 폴더(localPath) 기준이다.
 *
 * 파일 모양: 첫 줄이 머리글인 CSV(.tsv 나 탭으로 나눈 파일도 된다). 머리글 이름으로 열을 찾는다(대소문자, 띄어쓰기, 끝의 괄호 설명은 보지 않는다).
 *   테이블      테이블ID, 테이블영문명, 물리테이블명, TABLE_ID, TABLE_NAME, TABLE, 테이블, 테이블명 중 앞에 있는 것
 *   테이블한글명 테이블한글명, 테이블논리명, 논리테이블명, TABLE_COMMENT, 테이블명
 *               (컬럼 열이 없는 파일이면 한글명, 논리명도)
 *   컬럼        컬럼ID, 컬럼영문명, 물리컬럼명, COLUMN_ID, COLUMN_NAME, COLUMN, 컬럼, 컬럼명
 *   컬럼한글명   컬럼한글명, 컬럼논리명, 논리컬럼명, COLUMN_COMMENT, 컬럼설명, 컬럼명, 한글명, 논리명, 설명
 * 테이블 목록(테이블 + 한글명)과 컬럼 사전(테이블 + 컬럼 + 컬럼한글명)을 모두 읽을 수 있습니다.
 * 이름이 영문 식별자가 아닌 행, 한글이 없는 한글명은 건너뜁니다. 같은 것이 여러 번 나오면 먼저 나온 것을 씁니다.
 * 글자 코드는 UTF-8(BOM 있어도 됨)로 읽고, 안 맞으면 MS949 로 읽습니다.
 *
 * 분석 결과(DB)에 저장하지 않고, 화면에서 테이블 상세를 열 때 읽습니다. 파일은 고친 시각과 크기가 같으면 다시 읽지 않습니다.
 * </pre>
 */
@Component
public class DictionaryFileReader extends BaseObject {

	public static final String NOT_CONFIGURED = "NOT_CONFIGURED";
	public static final String OK = "OK";
	public static final String FAILED = "FAILED";

	private static final String PREFIX = "dstone.knowledge.dictionary.file.";

	/** YAML 목록으로 적었을 때 [0], [1] ... 을 이만큼까지 본다 */
	private static final int MAX_FILES = 50;

	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][\\w$#]*");

	private static final String[] TABLE_HEADERS = { "테이블ID", "테이블영문명", "물리테이블명", "TABLE_ID", "TABLE_NAME", "TABLE", "테이블", "테이블명" };
	private static final String[] TABLE_NAME_HEADERS = { "테이블한글명", "테이블논리명", "논리테이블명", "TABLE_COMMENT", "테이블명" };
	private static final String[] TABLE_ONLY_NAME_HEADERS = { "한글명", "논리명" };
	private static final String[] COLUMN_HEADERS = { "컬럼ID", "컬럼영문명", "물리컬럼명", "COLUMN_ID", "COLUMN_NAME", "COLUMN", "컬럼", "컬럼명" };
	private static final String[] COLUMN_NAME_HEADERS = { "컬럼한글명", "컬럼논리명", "논리컬럼명", "COLUMN_COMMENT", "컬럼설명", "컬럼명", "한글명", "논리명", "설명" };

	@Autowired
	private ConfigProperty configProperty;

	/** 파일 경로 → 읽어 둔 사전 */
	private final Map<String, Cached> cache = new ConcurrentHashMap<String, Cached>();

	/**
	 * <pre>
	 * 한글명 하나와 그것이 적힌 곳
	 * </pre>
	 */
	public static class Name {

		public final String text;

		/** 파일이름:줄 */
		public final String where;

		Name(String text, String where) {
			this.text = text;
			this.where = where;
		}
	}

	/**
	 * <pre>
	 * 사전 파일 하나에서 읽은 것. 이름은 모두 대문자다.
	 * </pre>
	 */
	public static class Dictionary {

		public final Map<String, Name> tables = new HashMap<String, Name>();

		/** 테이블 → 컬럼 → 한글명 */
		public final Map<String, Map<String, Name>> columns = new HashMap<String, Map<String, Name>>();

		/** 머리글에서 열을 못 찾았을 때 그 까닭. 읽은 것이 있으면 null */
		public String problem;
	}

	/**
	 * <pre>
	 * 테이블 하나에 대해 사전 파일들에서 찾은 것
	 * </pre>
	 */
	public static class Result {

		/** NOT_CONFIGURED / OK / FAILED(읽지 못한 파일이 있다. 읽은 파일의 것은 그래도 들어 있다) */
		public final String status;

		/** 읽지 못한 파일과 그 까닭 */
		public final String message;

		/** 설정된 파일들(설정에 적은 그대로) */
		public final List<String> files;

		/** 테이블 한글명. 없으면 null */
		public final Name tableName;

		/** 컬럼 → 한글명 */
		public final Map<String, Name> columns;

		Result(String status, String message, List<String> files, Name tableName, Map<String, Name> columns) {
			this.status = status;
			this.message = message;
			this.files = files;
			this.tableName = tableName;
			this.columns = columns;
		}
	}

	private static class Cached {
		final long modified;
		final long size;
		final Dictionary dictionary;

		Cached(long modified, long size, Dictionary dictionary) {
			this.modified = modified;
			this.size = size;
			this.dictionary = dictionary;
		}
	}

	/**
	 * @param projectId 분석 프로젝트 ID. 설정 키를 찾는 데 쓴다
	 * @param baseDir 상대 경로의 기준 폴더(프로젝트의 localPath). null 이면 현재 폴더
	 * @param table 테이블 이름(대문자). 스키마가 붙어 있으면(DPHJSDBA.JZ_CMNS_DET) 사전에서는 스키마를 떼고 찾는다
	 */
	public Result read(String projectId, String baseDir, String table) {
		int dot = table.lastIndexOf('.');
		if (dot >= 0) {
			table = table.substring(dot + 1);
		}
		List<String> paths = configuredFiles(projectId);
		if (paths.isEmpty()) {
			return new Result(NOT_CONFIGURED, null, paths, null, new LinkedHashMap<String, Name>());
		}
		Name tableName = null;
		Map<String, Name> columns = new LinkedHashMap<String, Name>();
		List<String> problems = new ArrayList<String>();
		for (int i = 0; i < paths.size(); i++) {
			Dictionary dictionary;
			try {
				dictionary = load(resolve(baseDir, paths.get(i)));
			} catch (Exception e) {
				warn("사전 파일을 읽지 못했습니다. projectId=" + projectId + ", file=" + paths.get(i) + ", 까닭=" + ErrorText.summaryOf(e));
				problems.add(paths.get(i) + ": " + ErrorText.summaryOf(e));
				continue;
			}
			if (dictionary.problem != null) {
				problems.add(paths.get(i) + ": " + dictionary.problem);
			}
			if (tableName == null) {
				tableName = dictionary.tables.get(table);
			}
			Map<String, Name> found = dictionary.columns.get(table);
			if (found != null) {
				for (Map.Entry<String, Name> entry : found.entrySet()) {
					if (!columns.containsKey(entry.getKey())) {
						columns.put(entry.getKey(), entry.getValue());
					}
				}
			}
		}
		return new Result(problems.isEmpty() ? OK : FAILED, problems.isEmpty() ? null : String.join(" / ", problems), paths, tableName, columns);
	}

	private List<String> configuredFiles(String projectId) {
		List<String> paths = new ArrayList<String>();
		String value = configProperty.getProperty(PREFIX + projectId);
		if (value != null) {
			String[] parts = value.split(",");
			for (int i = 0; i < parts.length; i++) {
				if (parts[i].trim().length() > 0) {
					paths.add(parts[i].trim());
				}
			}
		}
		// YAML 목록으로 적은 경우
		for (int i = 0; i < MAX_FILES; i++) {
			String item = configProperty.getProperty(PREFIX + projectId + "[" + i + "]");
			if (item == null) {
				break;
			}
			if (item.trim().length() > 0 && !paths.contains(item.trim())) {
				paths.add(item.trim());
			}
		}
		return paths;
	}

	private File resolve(String baseDir, String path) {
		File file = new File(path);
		if (file.isAbsolute() || baseDir == null) {
			return file;
		}
		return new File(baseDir, path);
	}

	private Dictionary load(File file) throws Exception {
		if (!file.isFile()) {
			throw new IllegalArgumentException("파일이 없습니다: " + file.getPath());
		}
		String key = file.getCanonicalPath();
		long modified = file.lastModified();
		long size = file.length();
		Cached cached = cache.get(key);
		if (cached != null && cached.modified == modified && cached.size == size) {
			return cached.dictionary;
		}
		Dictionary dictionary = parse(decode(Files.readAllBytes(file.toPath())), file.getName());
		cache.put(key, new Cached(modified, size, dictionary));
		return dictionary;
	}

	/** UTF-8 로 읽고, 맞지 않는 글자가 있으면 MS949 로 읽습니다. */
	static String decode(byte[] bytes) {
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			text = new String(bytes, Charset.forName("MS949"));
		}
		return text.startsWith("\uFEFF") ? text.substring(1) : text;
	}

	/**
	 * <pre>
	 * 사전 파일의 글을 읽습니다.
	 * </pre>
	 * @param text 파일 내용
	 * @param fileName 한글명이 어디서 왔는지 적을 때 쓰는 파일 이름
	 */
	static Dictionary parse(String text, String fileName) {
		Dictionary dictionary = new Dictionary();
		if (text.startsWith("\uFEFF")) {
			text = text.substring(1);
		}
		List<List<String>> rows = rowsOf(text, delimiterOf(text, fileName));
		if (rows.isEmpty()) {
			dictionary.problem = "내용이 없습니다";
			return dictionary;
		}
		List<String> header = new ArrayList<String>();
		for (int i = 0; i < rows.get(0).size(); i++) {
			header.add(headerKey(rows.get(0).get(i)));
		}
		int table = indexOf(header, TABLE_HEADERS, -1);
		int column = indexOf(header, COLUMN_HEADERS, table);
		int tableName = indexOf(header, TABLE_NAME_HEADERS, table);
		if (tableName < 0 && column < 0) {
			tableName = indexOf(header, TABLE_ONLY_NAME_HEADERS, table);
		}
		int columnName = column < 0 ? -1 : indexOf(header, COLUMN_NAME_HEADERS, column, tableName);
		if (table < 0 || (tableName < 0 && columnName < 0)) {
			dictionary.problem = "머리글에서 테이블 열과 한글명 열을 찾지 못했습니다";
			return dictionary;
		}
		for (int r = 1; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			String tableId = identifierOf(cell(row, table));
			if (tableId == null) {
				continue;
			}
			String where = fileName + ":" + (r + 1);
			String name = KoreanName.clean(cell(row, tableName));
			if (name != null && !dictionary.tables.containsKey(tableId)) {
				dictionary.tables.put(tableId, new Name(name, where));
			}
			String columnId = identifierOf(cell(row, column));
			String columnText = KoreanName.clean(cell(row, columnName));
			if (columnId == null || columnText == null) {
				continue;
			}
			Map<String, Name> byColumn = dictionary.columns.get(tableId);
			if (byColumn == null) {
				byColumn = new HashMap<String, Name>();
				dictionary.columns.put(tableId, byColumn);
			}
			if (!byColumn.containsKey(columnId)) {
				byColumn.put(columnId, new Name(columnText, where));
			}
		}
		return dictionary;
	}

	/** .tsv 이거나 첫 줄에 쉼표 없이 탭만 있으면 탭으로 나눈다 */
	private static char delimiterOf(String text, String fileName) {
		if (fileName.toLowerCase(Locale.ROOT).endsWith(".tsv")) {
			return '\t';
		}
		int newline = text.indexOf('\n');
		String first = newline < 0 ? text : text.substring(0, newline);
		return first.indexOf(',') < 0 && first.indexOf('\t') >= 0 ? '\t' : ',';
	}

	/** 따옴표로 묶은 칸(안의 쉼표, 줄바꿈, "" 포함)을 읽는 CSV 읽기 */
	private static List<List<String>> rowsOf(String text, char delimiter) {
		List<List<String>> rows = new ArrayList<List<String>>();
		List<String> row = new ArrayList<String>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				} else if (c == '"') {
					quoted = false;
				} else {
					cell.append(c);
				}
			} else if (c == '"') {
				quoted = true;
			} else if (c == delimiter) {
				row.add(cell.toString());
				cell.setLength(0);
			} else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
					i++;
				}
				row.add(cell.toString());
				cell.setLength(0);
				addRow(rows, row);
				row = new ArrayList<String>();
			} else {
				cell.append(c);
			}
		}
		row.add(cell.toString());
		addRow(rows, row);
		return rows;
	}

	/** 빈 줄도 빈 행으로 넣는다. 그래야 행 번호가 파일의 줄 번호와 맞는다(칸 안에 줄바꿈이 없을 때). */
	private static void addRow(List<List<String>> rows, List<String> row) {
		if (row.size() == 1 && row.get(0).trim().length() == 0) {
			row.clear();
		}
		rows.add(row);
	}

	/** 머리글 비교용 열쇠: 끝의 괄호 설명, 띄어쓰기, 밑줄을 떼고 대문자로. "컬럼설명(SQL주석)" → "컬럼설명", "Table Name" → "TABLENAME" */
	private static String headerKey(String header) {
		String key = header.trim();
		int paren = key.indexOf('(');
		if (paren > 0) {
			key = key.substring(0, paren);
		}
		return key.replaceAll("[\\s_]+", "").toUpperCase(Locale.ROOT);
	}

	/** candidates 를 앞에서부터 찾아 처음 있는 열. skip 에 든 열은 이미 다른 뜻으로 쓴 것이라 건너뛴다 */
	private static int indexOf(List<String> header, String[] candidates, int... skip) {
		for (int c = 0; c < candidates.length; c++) {
			String wanted = headerKey(candidates[c]);
			for (int i = 0; i < header.size(); i++) {
				boolean skipped = false;
				for (int s = 0; s < skip.length; s++) {
					skipped = skipped || skip[s] == i;
				}
				if (!skipped && header.get(i).equals(wanted)) {
					return i;
				}
			}
		}
		return -1;
	}

	private static String cell(List<String> row, int index) {
		return index < 0 || index >= row.size() ? null : row.get(index);
	}

	/** "SCHEMA.TB_ORDER" → "TB_ORDER". 영문 식별자가 아니면 null */
	private static String identifierOf(String value) {
		if (value == null) {
			return null;
		}
		String text = value.trim();
		int dot = text.lastIndexOf('.');
		if (dot >= 0) {
			text = text.substring(dot + 1);
		}
		return IDENTIFIER.matcher(text).matches() ? text.toUpperCase(Locale.ROOT) : null;
	}

}
