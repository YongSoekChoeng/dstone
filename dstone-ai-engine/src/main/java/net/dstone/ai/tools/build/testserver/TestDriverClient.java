package net.dstone.ai.tools.build.testserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * 테스트 서버(앱 JVM) 안의 테스트 드라이버에 명령을 보내는 쪽입니다(드라이버의 Control 참고).
 * 제어 파일(port, token)은 드라이버가 처음 불릴 때 생깁니다. 파일이 없으면 앱이 아직 DB에 연결하지 않은 것입니다.
 * </pre>
 */
public final class TestDriverClient {

	/** 조회 결과 */
	public static final class QueryResult {
		/** OK, NOCONN(앱이 아직 그 DB에 연결하지 않음), FAIL */
		public String status;
		public String message;
		public List<String> columns = new ArrayList<String>();
		/** 값이 NULL이면 null */
		public List<String[]> rows = new ArrayList<String[]>();
		public boolean more;
	}

	private final File controlFile;

	public TestDriverClient(File controlFile) {
		this.controlFile = controlFile;
	}

	/** 드라이버가 떴는가(앱이 DB에 한 번이라도 연결했는가) */
	public boolean ready() {
		return this.controlFile.isFile();
	}

	/** 트랜잭션 전체를 되돌립니다. 성공이나 연결한 적 없음이면 null, 실패면 이유. */
	public String rollback() {
		if (!this.ready()) {
			return null;
		}
		try {
			String answer = this.send("ROLLBACK", null, 120000);
			return answer.startsWith("OK") ? null : answer.trim();
		} catch (IOException e) {
			return "테스트 드라이버에 닿지 못했습니다: " + e.getMessage();
		}
	}

	/** 드라이버가 연 물리 연결 수의 합입니다. 드라이버가 없으면 0, 묻지 못하면 -1. */
	public int physicalConnections() {
		if (!this.ready()) {
			return 0;
		}
		try {
			String answer = this.send("STATUS", null, 10000);
			if (!answer.startsWith("OK")) {
				return -1;
			}
			int total = 0;
			String[] lines = answer.split("\n");
			for (int i = 1; i < lines.length; i++) {
				String[] parts = lines[i].split("\t", -1);
				if (parts.length >= 3) {
					total += Integer.parseInt(parts[2].trim());
				}
			}
			return total;
		} catch (IOException | RuntimeException e) {
			return -1;
		}
	}

	/** 앱과 같은 연결에서 조회합니다(커밋하지 않은 테스트 데이터가 보임). */
	public QueryResult query(String url, String user, String sql, int maxRows) {
		QueryResult result = new QueryResult();
		if (!this.ready()) {
			result.status = "NOCONN";
			return result;
		}
		String answer;
		try {
			answer = this.send("QUERY", url + "\n" + (user == null ? "" : user) + "\n" + maxRows + "\n" + sql, 120000);
		} catch (IOException e) {
			result.status = "FAIL";
			result.message = "테스트 드라이버에 닿지 못했습니다: " + e.getMessage();
			return result;
		}
		if (answer.startsWith("NOCONN")) {
			result.status = "NOCONN";
			return result;
		}
		if (!answer.startsWith("OK")) {
			result.status = "FAIL";
			result.message = answer.startsWith("FAIL ") ? answer.substring(5).trim() : answer.trim();
			return result;
		}
		result.status = "OK";
		String[] lines = answer.split("\n", -1);
		if (lines.length > 1) {
			for (String column : lines[1].split("\t", -1)) {
				result.columns.add(unescape(column));
			}
		}
		for (int i = 2; i < lines.length; i++) {
			if (lines[i].isEmpty()) {
				continue;
			}
			if ("MORE".equals(lines[i])) {
				result.more = true;
				break;
			}
			String[] cells = lines[i].split("\t", -1);
			String[] row = new String[cells.length];
			for (int c = 0; c < cells.length; c++) {
				row[c] = "\\N".equals(cells[c]) ? null : unescape(cells[c]);
			}
			result.rows.add(row);
		}
		return result;
	}

	private String send(String command, String body, int timeoutMillis) throws IOException {
		int port = 0;
		String token = null;
		for (String line : Files.readAllLines(this.controlFile.toPath(), StandardCharsets.UTF_8)) {
			if (line.startsWith("port=")) {
				port = Integer.parseInt(line.substring(5).trim());
			} else if (line.startsWith("token=")) {
				token = line.substring(6).trim();
			}
		}
		Socket socket = new Socket();
		try {
			socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
			socket.setSoTimeout(timeoutMillis);
			OutputStream out = socket.getOutputStream();
			out.write((token + "\n" + command + "\n" + (body == null ? "" : body)).getBytes(StandardCharsets.UTF_8));
			out.flush();
			socket.shutdownOutput();
			InputStream in = socket.getInputStream();
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} finally {
			socket.close();
		}
	}

	private static String unescape(String text) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\\' && i + 1 < text.length()) {
				char next = text.charAt(++i);
				out.append(next == 't' ? '\t' : next == 'n' ? '\n' : next == 'r' ? '\r' : next);
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

}
