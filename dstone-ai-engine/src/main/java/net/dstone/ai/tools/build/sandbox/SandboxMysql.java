package net.dstone.ai.tools.build.sandbox;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 시험 한 번 동안만 사는 MySQL입니다.
 *
 * 원래 서버에 이름만 다른 DB를 만들지 않고 mysqld를 따로 띄웁니다.
 * LLM이 쓴 DDL에 DROP DATABASE나 다른DB.테이블이 있어도 원래 서버에는 닿을 길이 없게 하려는 것입니다.
 *
 * 계정
 *   dstone_sbx   엔진만 쓰는 관리 계정(비밀번호는 매번 새로, 메모리에만)
 *   dstone_ddl   DDL 실행용. 복제한 DB에만 권한
 *   dstone_ro    dbQuery용. 복제한 DB를 읽기만
 *   앱의 계정     프로젝트 관리에 등록한 계정 그대로. 복제한 DB에만 권한
 *
 * 원래 DB에서는 mysqldump로 읽기만 합니다. 비밀번호는 명령줄에 싣지 않고 환경변수(MYSQL_PWD)로 넘깁니다.
 * </pre>
 */
final class SandboxMysql implements SandboxDatabase {

	private static final String ADMIN = "dstone_sbx";
	private static final String DDL_USER = "dstone_ddl";
	private static final String READ_USER = "dstone_ro";
	private static final Set<String> OWN_USERS = Set.of(ADMIN, DDL_USER, READ_USER);

	private static final String PID_FILE = "mysqld.pid.txt";
	private static final String PASSWORD_ENV = "MYSQL_PWD";
	private static final int START_TIMEOUT_SECONDS = 90;

	/** 이미 있어서 건너뛰어도 되는 오류: 테이블, 컬럼, 인덱스, 같은 키의 행 */
	private static final Set<Integer> ALREADY_THERE = Set.of(1050, 1060, 1061, 1062);

	private static final Pattern DEFINER = Pattern.compile("DEFINER=`[^`]*`@`[^`]*`");
	private static final Pattern URL = Pattern.compile("^jdbc:(?:log4jdbc:)?mysql:(?://)([^/?]+)(?:/([^?]*))?(\\?.*)?$", Pattern.CASE_INSENSITIVE);

	private final File dir;
	private final File mysqld;
	private final File mysql;
	private final File mysqldump;
	private final int maxDbMb;
	private final String adminPassword = SandboxProcess.secret();
	private final String ddlPassword = SandboxProcess.secret();
	private final String readPassword = SandboxProcess.secret();
	private final List<String> notes = new ArrayList<String>();
	private final List<String> databases = new ArrayList<String>();

	private Process process;
	private int port;

	/**
	 * @param dir     일회용 폴더(통째로 만들고 지움)
	 * @param home    MySQL 설치 폴더(bin 또는 sbin에 mysqld, mysql, mysqldump)
	 * @param maxDbMb 복제할 DB 하나의 크기 상한
	 */
	SandboxMysql(File dir, String home, int maxDbMb) throws SandboxException {
		this.dir = dir.getAbsoluteFile();
		this.maxDbMb = maxDbMb;
		this.mysqld = SandboxProcess.executable(home, "mysqld", "mysql");
		this.mysql = SandboxProcess.executable(home, "mysql", "mysql");
		this.mysqldump = SandboxProcess.executable(home, "mysqldump", "mysql");
	}

	/** JDBC 주소를 나눠 Source로 만듭니다. MySQL 계열이 아니면 환경 실패입니다. */
	static DatabaseSource sourceOf(String name, String url, String username, String password, String schema) throws SandboxException {
		Matcher m = URL.matcher(url == null ? "" : url.trim());
		if (!m.matches() || m.group(1).contains("(") || m.group(1).contains("@")) {
			throw new SandboxException("격리 시험 DB는 jdbc:mysql://호스트:포트/DB 모양의 MySQL 주소라야 만들 수 있습니다: 프로젝트 DB " + name, false);
		}
		List<SandboxGuard.Endpoint> hosts = new ArrayList<SandboxGuard.Endpoint>();
		for (String part : m.group(1).split(",")) {
			String host = part.trim();
			int port = 3306;
			int colon = host.lastIndexOf(':');
			if (colon > 0 && (host.indexOf(':') == colon || host.charAt(colon - 1) == ']')) {
				try {
					port = Integer.parseInt(host.substring(colon + 1));
				} catch (NumberFormatException e) {
					throw new SandboxException("프로젝트 DB " + name + "의 포트를 읽을 수 없습니다.", false);
				}
				host = host.substring(0, colon);
			}
			hosts.add(new SandboxGuard.Endpoint(host.replace("[", "").replace("]", ""), port));
		}
		String database = m.group(2) == null || m.group(2).isBlank() ? (schema == null ? "" : schema.trim()) : m.group(2).trim();
		if (!database.matches("[A-Za-z0-9_$-]+") || Set.of("mysql", "sys", "information_schema", "performance_schema").contains(database.toLowerCase(Locale.ROOT))) {
			throw new SandboxException("프로젝트 DB " + name + "의 DB 이름을 복제에 쓸 수 없습니다: '" + database + "'", false);
		}
		return new DatabaseSource(name, hosts, database, m.group(3) == null ? "" : m.group(3), username == null ? "" : username, password == null ? "" : password);
	}

	@Override
	public int port() {
		return this.port;
	}

	@Override
	public List<String> notes() {
		return this.notes;
	}

	@Override
	public List<String> databases() {
		return this.databases;
	}

	@Override
	public Map<String, Map<String, String>> readers() {
		Map<String, Map<String, String>> result = new LinkedHashMap<String, Map<String, String>>();
		for (String database : this.databases) {
			result.put(database, Map.of("url", this.url(database), "username", READ_USER, "password", this.readPassword));
		}
		return result;
	}

	String url(String database) {
		return "jdbc:mysql://127.0.0.1:" + this.port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&allowLoadLocalInfile=false&characterEncoding=UTF-8";
	}

	/**
	 * mysqld를 띄우고 DB들을 복제합니다.
	 *
	 * @param sources 프로젝트 관리의 DB들. 같은 DB가 계정만 달리 여럿일 수 있음
	 * @param paths   복제 데이터 속 원본 경로를 바꿀 규칙
	 */
	@Override
	public void create(List<DatabaseSource> sources, SandboxPaths paths) throws SandboxException {
		try {
			Map<String, DatabaseSource> unique = new LinkedHashMap<String, DatabaseSource>();
			for (DatabaseSource source : sources) {
				DatabaseSource first = unique.putIfAbsent(source.database(), source);
				if (first != null && !sameServer(first, source)) {
					throw new SandboxException("이름이 같은 DB가 서로 다른 서버에 있어 함께 복제할 수 없습니다: " + source.database(), false);
				}
			}
			Map<String, String> settings = this.inspect(unique);
			this.start(settings);
			for (DatabaseSource source : unique.values()) {
				this.copy(source, settings, paths);
			}
			this.createUsers(sources);
		} catch (IOException e) {
			throw new SandboxException("일회용 DB를 만들지 못했습니다: " + e.getMessage(), false);
		} catch (SQLException e) {
			throw new SandboxException("일회용 DB를 준비하다 SQL 오류가 났습니다: " + e.getMessage(), false);
		}
	}

	private static boolean sameServer(DatabaseSource a, DatabaseSource b) {
		SandboxGuard.Endpoint left = a.hosts().get(0);
		SandboxGuard.Endpoint right = b.hosts().get(0);
		return left.port() == right.port() && SandboxGuard.sameHost(left.host(), right.host());
	}

	/** 원래 DB의 크기를 확인하고, 똑같이 맞출 서버 설정을 읽습니다. */
	private Map<String, String> inspect(Map<String, DatabaseSource> sources) throws SandboxException {
		Map<String, String> settings = new LinkedHashMap<String, String>();
		for (DatabaseSource source : sources.values()) {
			SandboxGuard.Endpoint host = source.hosts().get(0);
			String name = host.host().contains(":") ? "[" + host.host() + "]" : host.host();
			String url = "jdbc:mysql://" + name + ":" + host.port() + "/" + source.database() + source.options();
			Properties properties = new Properties();
			properties.setProperty("user", source.username());
			properties.setProperty("password", source.password());
			properties.setProperty("connectTimeout", "10000");
			try (Connection connection = DriverManager.getConnection(url, properties); Statement statement = connection.createStatement()) {
				connection.setReadOnly(true);
				try (PreparedStatement size = connection.prepareStatement("SELECT COALESCE(SUM(DATA_LENGTH),0)/1048576 FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?")) {
					size.setString(1, source.database());
					try (ResultSet rs = size.executeQuery()) {
						if (rs.next() && rs.getDouble(1) > this.maxDbMb) {
							throw new SandboxException("DB " + source.database() + "가 " + Math.round(rs.getDouble(1)) + "MB라 복제하지 않습니다(설정 sandbox-max-db-mb "
								+ this.maxDbMb + "). 작은 개발 DB를 프로젝트 관리에 등록하십시오.", false);
						}
					}
				}
				try (ResultSet rs = statement.executeQuery("SELECT @@lower_case_table_names, @@sql_mode, @@character_set_database, @@collation_database")) {
					if (rs.next()) {
						settings.putIfAbsent("lowerCase", rs.getString(1));
						settings.putIfAbsent("sqlMode", rs.getString(2));
						settings.put("charset." + source.database(), rs.getString(3));
						settings.put("collation." + source.database(), rs.getString(4));
					}
				}
			} catch (SQLException e) {
				throw new SandboxException("프로젝트 DB " + source.name() + "를 읽지 못해 복제할 수 없습니다: " + e.getMessage(), false);
			}
		}
		return settings;
	}

	private void start(Map<String, String> settings) throws IOException, SandboxException {
		removeLeftover(this.dir);
		Files.createDirectories(this.dir.toPath());
		File data = new File(this.dir, "data");
		File log = new File(this.dir, "mysqld.log");
		List<String> common = new ArrayList<String>();
		common.add(this.mysqld.getPath());
		common.add("--no-defaults");
		common.add("--datadir=" + SandboxProcess.slash(data));
		common.add("--log-error=" + SandboxProcess.slash(log));
		String lowerCase = settings.get("lowerCase");
		// 0은 대소문자를 가리는 파일 시스템에서만, 2는 가리지 않는 곳에서만 쓸 수 있다
		if ("1".equals(lowerCase) || ("0".equals(lowerCase) && !SandboxProcess.isWindows())) {
			common.add("--lower-case-table-names=" + lowerCase);
		}
		List<String> init = new ArrayList<String>(common);
		init.add("--initialize-insecure");
		String problem = SandboxProcess.execute(this.dir, init, PASSWORD_ENV, null, null, null);
		if (problem != null) {
			throw new SandboxException("mysqld 초기화에 실패했습니다(" + problem + "). " + SandboxProcess.tail(log), false);
		}

		File initFile = new File(this.dir, "init.sql");
		Files.writeString(initFile.toPath(), "CREATE USER '" + ADMIN + "'@'%' IDENTIFIED BY '" + this.adminPassword + "';\n"
			+ "GRANT ALL PRIVILEGES ON *.* TO '" + ADMIN + "'@'%' WITH GRANT OPTION;\n"
			+ "DROP USER 'root'@'localhost';\n", StandardCharsets.UTF_8);
		this.port = SandboxProcess.freePort();
		List<String> run = new ArrayList<String>(common);
		run.add("--init-file=" + SandboxProcess.slash(initFile));
		run.add("--bind-address=127.0.0.1");
		run.add("--port=" + this.port);
		run.add("--socket=s"); // datadir 기준. 절대경로는 107자 제한에 걸린다
		run.add("--pid-file=p");
		run.add("--skip-name-resolve");
		run.add("--skip-log-bin");
		run.add("--secure-file-priv=NULL"); // SQL로 파일을 읽고 쓰지 못하게
		run.add("--local-infile=0");
		run.add("--event-scheduler=OFF"); // 복제된 예약 작업이 돌지 않게
		run.add("--max-allowed-packet=64M");
		run.add("--loose-mysqlx=OFF");
		run.add("--loose-no-monitor");
		String mode = settings.get("sqlMode");
		if (mode != null) {
			run.add("--sql-mode=" + mode.replace("NO_AUTO_CREATE_USER,", "").replace(",NO_AUTO_CREATE_USER", ""));
		}
		this.process = new ProcessBuilder(run).directory(this.dir).redirectErrorStream(true)
			.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(this.dir, "mysqld.out"))).start();
		Files.writeString(new File(this.dir, PID_FILE).toPath(), String.valueOf(this.process.pid()), StandardCharsets.UTF_8);

		long deadline = System.currentTimeMillis() + START_TIMEOUT_SECONDS * 1000L;
		String last = "";
		while (System.currentTimeMillis() < deadline) {
			if (!this.process.isAlive()) {
				throw new SandboxException("일회용 mysqld가 바로 끝났습니다(종료 코드 " + this.process.exitValue() + "). " + SandboxProcess.tail(log), false);
			}
			try (Connection connection = this.admin("")) {
				if (connection.isValid(5)) {
					Files.deleteIfExists(initFile.toPath());
					return;
				}
			} catch (SQLException e) {
				last = e.getMessage();
			}
			SandboxProcess.pause(500L);
		}
		throw new SandboxException("일회용 mysqld에 접속하지 못했습니다: " + last + " " + SandboxProcess.tail(log), false);
	}

	private Connection admin(String database) throws SQLException {
		return DriverManager.getConnection(this.url(database), ADMIN, this.adminPassword);
	}

	private void copy(DatabaseSource source, Map<String, String> settings, SandboxPaths paths) throws IOException, SQLException, SandboxException {
		String database = source.database();
		try (Connection connection = this.admin(""); Statement statement = connection.createStatement()) {
			String charset = SandboxProcess.word(settings.get("charset." + database));
			String collation = SandboxProcess.word(settings.get("collation." + database));
			try {
				statement.execute("CREATE DATABASE `" + database + "`" + (charset == null ? "" : " CHARACTER SET " + charset) + (collation == null ? "" : " COLLATE " + collation));
			} catch (SQLException e) {
				statement.execute("CREATE DATABASE `" + database + "`" + (charset == null ? "" : " CHARACTER SET " + charset));
				this.notes.add("참고: 정렬 규칙 " + collation + "을(를) 시험 MySQL이 몰라 기본값으로 복제했습니다.");
			}
		}
		this.databases.add(database);
		File raw = new File(this.dir, "dump.sql");
		String problem = this.dump(source, raw, true);
		if (problem != null) {
			// 프로시저 정의는 읽기 전용 계정이 못 읽는 경우가 많다. 그것만 빼고 다시 뜬다
			String first = problem;
			problem = this.dump(source, raw, false);
			if (problem != null) {
				throw new SandboxException("DB " + database + "를 떠 오지 못했습니다(" + problem + "). 계정에 SELECT, SHOW VIEW, TRIGGER 권한이 필요합니다.", false);
			}
			this.notes.add("참고: 프로시저·함수는 복제하지 못했습니다(" + first + ").");
		}
		File ready = new File(this.dir, "load.sql");
		prepareDump(raw, ready, paths);
		List<String> load = new ArrayList<String>();
		load.add(this.mysql.getPath());
		load.add("--no-defaults");
		load.add("--binary-mode"); // system, \! 같은 명령을 끈다
		load.add("--default-character-set=utf8mb4");
		load.add("--max-allowed-packet=64M");
		load.add("--protocol=TCP");
		load.add("--host=127.0.0.1");
		load.add("--port=" + this.port);
		load.add("--user=" + ADMIN);
		load.add("--loose-get-server-public-key");
		load.add(database);
		problem = SandboxProcess.execute(this.dir, load, PASSWORD_ENV, this.adminPassword, ready, null);
		if (problem != null) {
			throw new SandboxException("DB " + database + "의 복제본을 넣지 못했습니다(" + problem + "). 시험 MySQL(mysql-home)의 버전을 원래 DB와 맞추십시오.", false);
		}
		Files.deleteIfExists(raw.toPath());
		Files.deleteIfExists(ready.toPath());
	}

	private String dump(DatabaseSource source, File out, boolean routines) throws IOException {
		List<String> command = new ArrayList<String>();
		command.add(this.mysqldump.getPath());
		command.add("--no-defaults");
		command.add("--single-transaction");
		command.add("--skip-lock-tables");
		command.add("--no-tablespaces");
		command.add("--hex-blob");
		command.add("--triggers");
		if (routines) {
			command.add("--routines");
		}
		command.add("--loose-set-gtid-purged=OFF");
		command.add("--loose-column-statistics=0");
		command.add("--loose-get-server-public-key");
		command.add("--default-character-set=utf8mb4");
		command.add("--max-allowed-packet=64M");
		command.add("--protocol=TCP");
		command.add("--host=" + source.hosts().get(0).host());
		command.add("--port=" + source.hosts().get(0).port());
		command.add("--user=" + source.username());
		command.add(source.database());
		return SandboxProcess.execute(this.dir, command, PASSWORD_ENV, source.password(), null, out);
	}

	/** 덤프에서 DEFINER(복제본에 없는 계정)를 떼고 원본 경로를 바꿉니다. */
	static void prepareDump(File from, File to, SandboxPaths paths) throws IOException {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new BufferedInputStream(Files.newInputStream(from.toPath())), StandardCharsets.ISO_8859_1));
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(Files.newOutputStream(to.toPath())), StandardCharsets.ISO_8859_1))) {
			String line;
			while ((line = in.readLine()) != null) {
				String changed = line.startsWith("INSERT ") ? line : DEFINER.matcher(line).replaceAll("");
				out.write(paths.shiftRaw(changed));
				out.write('\n');
			}
		}
	}

	private void createUsers(List<DatabaseSource> sources) throws SQLException {
		Map<String, String> users = new LinkedHashMap<String, String>();
		users.put(DDL_USER, this.ddlPassword);
		users.put(READ_USER, this.readPassword);
		for (DatabaseSource source : sources) {
			if (!source.username().isEmpty() && !OWN_USERS.contains(source.username())) {
				users.putIfAbsent(source.username(), source.password());
			}
		}
		try (Connection connection = this.admin(""); Statement statement = connection.createStatement()) {
			for (Map.Entry<String, String> user : users.entrySet()) {
				try (PreparedStatement create = connection.prepareStatement("CREATE USER ?@'%' IDENTIFIED BY ?")) {
					create.setString(1, user.getKey());
					create.setString(2, user.getValue());
					create.execute();
				}
				String privileges = READ_USER.equals(user.getKey()) ? "SELECT, SHOW VIEW" : "ALL PRIVILEGES";
				for (String database : this.databases) {
					// GRANT의 DB 이름에서 _는 아무 글자나 뜻하므로 막는다
					String sql = "GRANT " + privileges + " ON `" + database.replace("_", "\\_") + "`.* TO ?@'%'";
					try (PreparedStatement grant = connection.prepareStatement(sql)) {
						grant.setString(1, user.getKey());
						grant.execute();
					}
				}
			}
		}
		// caching_sha2_password는 한 번 로그인해 둬야 옛 드라이버(useSSL=false)가 들어온다
		for (Map.Entry<String, String> user : users.entrySet()) {
			try (Connection connection = DriverManager.getConnection(this.url(this.databases.get(0)), user.getKey(), user.getValue())) {
				connection.isValid(5);
			}
		}
	}

	/**
	 * DDL을 복제본에 반영하고 "이미 있어 건너뛴" 안내들을 돌려줍니다.
	 *
	 * @param statements 실행할 문장들
	 */
	@Override
	public List<String> apply(List<String> statements) throws SandboxException {
		List<String> existing = new ArrayList<String>();
		if (statements.isEmpty()) {
			return existing;
		}
		try (Connection connection = DriverManager.getConnection(this.url(this.databases.get(0)), DDL_USER, this.ddlPassword);
			Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(120);
			for (int i = 0; i < statements.size(); i++) {
				try {
					statement.execute(statements.get(i));
				} catch (SQLException e) {
					String where = (i + 1) + "번째 문장(" + SandboxProcess.brief(statements.get(i)) + "): " + e.getMessage();
					if (!ALREADY_THERE.contains(Integer.valueOf(e.getErrorCode()))) {
						throw new SandboxException("DDL을 시험 DB에 반영하지 못했습니다. " + where, true);
					}
					existing.add(where);
				}
			}
		} catch (SQLException e) {
			throw new SandboxException("일회용 DB에 DDL 계정으로 접속하지 못했습니다: " + e.getMessage(), false);
		}
		return existing;
	}

	/** mysqld를 내리고 폴더를 지웁니다. 여러 번 불러도 됩니다. */
	@Override
	public void close() {
		if (this.process != null) {
			SandboxProcess.killTree(this.process.toHandle());
		}
		SandboxProcess.deleteTree(this.dir);
	}

	/** 엔진이 죽어 남은 mysqld와 폴더를 치웁니다. 명령줄(Linux)이나 실행 파일 이름(Windows)이 mysqld일 때만 내립니다. */
	static void removeLeftover(File dir) throws IOException {
		File pidFile = new File(dir, PID_FILE);
		if (pidFile.isFile()) {
			try {
				Optional<ProcessHandle> found = ProcessHandle.of(Long.parseLong(Files.readString(pidFile.toPath(), StandardCharsets.UTF_8).trim()));
				if (found.isPresent() && found.get().isAlive()) {
					ProcessHandle.Info info = found.get().info();
					Optional<String> commandLine = info.commandLine();
					boolean ours;
					if (commandLine.isPresent()) {
						ours = commandLine.get().contains(SandboxProcess.slash(new File(dir.getAbsoluteFile(), "data")));
					} else {
						// Windows는 명령줄을 알려 주지 않는다. PID 파일보다 먼저 뜬 mysqld만 우리 것으로 본다(PID가 다시 쓰였으면 뒤에 뜬 것)
						ours = info.command().orElse("").toLowerCase(Locale.ROOT).endsWith("mysqld.exe") && info.startInstant().isPresent()
							&& Math.abs(info.startInstant().get().toEpochMilli() - pidFile.lastModified()) < 5000L;
					}
					if (ours) {
						SandboxProcess.killTree(found.get());
					}
				}
			} catch (NumberFormatException e) {
				// 쓰다 만 PID 파일
			}
		}
		if (dir.exists() && !SandboxProcess.deleteTree(dir)) {
			throw new IOException("지난 시험 DB 폴더를 지우지 못했습니다: " + dir);
		}
	}

}
