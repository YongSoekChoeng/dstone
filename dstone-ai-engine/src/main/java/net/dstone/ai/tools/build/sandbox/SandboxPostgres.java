package net.dstone.ai.tools.build.sandbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * 시험 한 번 동안만 사는 PostgreSQL입니다.
 *
 * 계정
 *   dstone_sbx    엔진만 쓰는 관리자
 *   dstone_load   복제본을 넣을 때만 쓰고 지우는 관리자
 *   dstone_ddl    복제한 객체의 주인. 슈퍼유저가 아니라서 COPY ... PROGRAM이나 서버 파일 읽기·쓰기를 못 한다
 *   dstone_ro     dbQuery용. 읽기만
 *   앱의 계정      프로젝트 관리에 등록한 계정 그대로. dstone_ddl의 구성원
 *
 * pg_ctl로 띄우고 내립니다. Windows에서 postgres.exe를 바로 띄우면 관리자 계정일 때 거부하기 때문입니다.
 * </pre>
 */
final class SandboxPostgres implements SandboxDatabase {

	private static final String ADMIN = "dstone_sbx";
	private static final String LOAD_USER = "dstone_load";
	private static final String DDL_USER = "dstone_ddl";
	private static final String READ_USER = "dstone_ro";
	private static final Set<String> OWN_USERS = Set.of(ADMIN, LOAD_USER, DDL_USER, READ_USER);
	private static final String PASSWORD_ENV = "PGPASSWORD";

	/** 이미 있어서 건너뛰어도 되는 오류: 테이블·인덱스, 컬럼, 객체, 스키마, 같은 키의 행 */
	private static final Set<String> ALREADY_THERE = Set.of("42P07", "42701", "42710", "42P06", "23505");

	private static final Pattern URL = Pattern.compile("^jdbc:(?:log4jdbc:)?postgresql://([^/?]+)/([^?]+)(\\?.*)?$", Pattern.CASE_INSENSITIVE);

	private final File dir;
	private final File data;
	private final File initdb;
	private final File pgCtl;
	private final File pgDump;
	private final File psql;
	private final int maxDbMb;
	private final String adminPassword = SandboxProcess.secret();
	private final String loadPassword = SandboxProcess.secret();
	private final String ddlPassword = SandboxProcess.secret();
	private final String readPassword = SandboxProcess.secret();
	private final List<String> notes = new ArrayList<String>();
	private final List<String> databases = new ArrayList<String>();
	private final Map<String, String> schemas = new LinkedHashMap<String, String>();
	private final Map<String, String> searchPaths = new LinkedHashMap<String, String>();
	private int port;

	SandboxPostgres(File dir, String home, int maxDbMb) throws SandboxException {
		this.dir = dir.getAbsoluteFile();
		this.data = new File(this.dir, "pgdata");
		this.maxDbMb = maxDbMb;
		this.initdb = SandboxProcess.executable(home, "initdb", "postgresql");
		this.pgCtl = SandboxProcess.executable(home, "pg_ctl", "postgresql");
		this.pgDump = SandboxProcess.executable(home, "pg_dump", "postgresql");
		this.psql = SandboxProcess.executable(home, "psql", "postgresql");
	}

	/** JDBC 주소를 나눕니다. */
	static DatabaseSource sourceOf(String name, String url, String username, String password) throws SandboxException {
		Matcher m = URL.matcher(url == null ? "" : url.trim());
		if (!m.matches() || !m.group(2).matches("[A-Za-z0-9_$-]+") || Set.of("postgres", "template0", "template1").contains(m.group(2))) {
			throw new SandboxException("프로젝트 DB " + name + "의 주소는 jdbc:postgresql://호스트:포트/DB 모양이고 시스템 DB가 아니어야 복제합니다.", false);
		}
		List<SandboxGuard.Endpoint> hosts = new ArrayList<SandboxGuard.Endpoint>();
		for (String part : m.group(1).split(",")) {
			Matcher host = Pattern.compile("^(\\[[^]]+]|[^:]+)(?::([0-9]{1,5}))?$").matcher(part.trim());
			if (!host.matches()) {
				throw new SandboxException("프로젝트 DB " + name + "의 접속 주소를 읽을 수 없습니다.", false);
			}
			hosts.add(new SandboxGuard.Endpoint(host.group(1).replace("[", "").replace("]", ""), host.group(2) == null ? 5432 : Integer.parseInt(host.group(2))));
		}
		return new DatabaseSource(name, hosts, m.group(2), m.group(3) == null ? "" : m.group(3), username == null ? "" : username, password == null ? "" : password);
	}

	@Override
	public void create(List<DatabaseSource> sources, SandboxPaths paths) throws SandboxException {
		try {
			Map<String, DatabaseSource> unique = new LinkedHashMap<String, DatabaseSource>();
			for (DatabaseSource source : sources) {
				DatabaseSource first = unique.putIfAbsent(source.database(), source);
				if (first != null && (first.hosts().get(0).port() != source.hosts().get(0).port()
					|| !SandboxGuard.sameHost(first.hosts().get(0).host(), source.hosts().get(0).host()))) {
					throw new SandboxException("이름이 같은 DB가 서로 다른 서버에 있어 함께 복제할 수 없습니다: " + source.database(), false);
				}
			}
			for (DatabaseSource source : unique.values()) {
				this.inspect(source);
			}
			this.start();
			try (Connection connection = this.admin("postgres"); Statement statement = connection.createStatement()) {
				statement.execute("CREATE ROLE " + LOAD_USER + " SUPERUSER LOGIN PASSWORD " + literal(this.loadPassword));
				statement.execute("CREATE ROLE " + DDL_USER + " LOGIN PASSWORD " + literal(this.ddlPassword));
				statement.execute("CREATE ROLE " + READ_USER + " LOGIN PASSWORD " + literal(this.readPassword));
				List<String> made = new ArrayList<String>();
				for (DatabaseSource source : sources) {
					String user = source.username();
					if (!user.isEmpty() && !OWN_USERS.contains(user) && !made.contains(user)) {
						statement.execute("CREATE ROLE " + quoted(user) + " LOGIN PASSWORD " + literal(source.password()) + " IN ROLE " + DDL_USER);
						made.add(user);
					}
				}
			}
			for (DatabaseSource source : unique.values()) {
				this.copy(source, paths);
			}
			try (Connection connection = this.admin("postgres"); Statement statement = connection.createStatement()) {
				statement.execute("DROP ROLE " + LOAD_USER);
			}
		} catch (IOException e) {
			throw new SandboxException("일회용 DB를 만들지 못했습니다: " + e.getMessage(), false);
		} catch (SQLException e) {
			throw new SandboxException("일회용 DB를 준비하다 SQL 오류가 났습니다: " + e.getMessage(), false);
		}
	}

	private void inspect(DatabaseSource source) throws SandboxException {
		SandboxGuard.Endpoint host = source.hosts().get(0);
		String name = host.host().contains(":") ? "[" + host.host() + "]" : host.host();
		Properties properties = new Properties();
		properties.setProperty("user", source.username());
		properties.setProperty("password", source.password());
		properties.setProperty("connectTimeout", "10");
		String url = "jdbc:postgresql://" + name + ":" + host.port() + "/" + source.database() + source.options();
		try (Connection connection = DriverManager.getConnection(url, properties); Statement statement = connection.createStatement()) {
			connection.setReadOnly(true);
			try (ResultSet rs = statement.executeQuery("SELECT pg_database_size(current_database()) / 1048576, current_schema(), current_setting('search_path')")) {
				if (rs.next()) {
					if (rs.getLong(1) > this.maxDbMb) {
						throw new SandboxException("DB " + source.database() + "가 " + rs.getLong(1) + "MB라 복제하지 않습니다(설정 sandbox-max-db-mb " + this.maxDbMb + ").", false);
					}
					this.schemas.put(source.database(), rs.getString(2) == null ? "public" : rs.getString(2));
					this.searchPaths.put(source.database(), rs.getString(3));
				}
			}
		} catch (SQLException e) {
			throw new SandboxException("프로젝트 DB " + source.name() + "를 읽지 못해 복제할 수 없습니다: " + e.getMessage(), false);
		}
	}

	private void start() throws IOException, SandboxException {
		this.removeLeftover();
		Files.createDirectories(this.dir.toPath());
		File passwordFile = new File(this.dir, "pw.txt");
		Files.writeString(passwordFile.toPath(), this.adminPassword + "\n", StandardCharsets.UTF_8);
		List<String> init = List.of(this.initdb.getPath(), "-D", SandboxProcess.slash(this.data), "-U", ADMIN,
			"--pwfile=" + SandboxProcess.slash(passwordFile), "--auth=scram-sha-256", "-E", "UTF8", "--no-locale");
		String problem = SandboxProcess.execute(this.dir, init, PASSWORD_ENV, null, null, null);
		Files.deleteIfExists(passwordFile.toPath());
		if (problem != null) {
			throw new SandboxException("PostgreSQL 초기화에 실패했습니다(" + problem + ")", false);
		}
		this.port = SandboxProcess.freePort();
		String options = "-p " + this.port + " -c listen_addresses=127.0.0.1" + (SandboxProcess.isWindows() ? "" : " -c unix_socket_directories=''");
		List<String> run = List.of(this.pgCtl.getPath(), "-D", SandboxProcess.slash(this.data), "-l", SandboxProcess.slash(new File(this.dir, "postgres.log")),
			"-o", options, "-t", "90", "-w", "start");
		problem = SandboxProcess.execute(this.dir, run, PASSWORD_ENV, null, null, null);
		if (problem != null) {
			throw new SandboxException("일회용 PostgreSQL이 뜨지 않았습니다(" + problem + "). " + SandboxProcess.tail(new File(this.dir, "postgres.log")), false);
		}
	}

	private void copy(DatabaseSource source, SandboxPaths paths) throws IOException, SQLException, SandboxException {
		String database = source.database();
		try (Connection connection = this.admin("postgres"); Statement statement = connection.createStatement()) {
			statement.execute("CREATE DATABASE " + quoted(database) + " TEMPLATE template0 ENCODING 'UTF8'");
		}
		this.databases.add(database);
		File raw = new File(this.dir, "dump.sql");
		List<String> dump = List.of(this.pgDump.getPath(), "--no-owner", "--no-privileges", "--encoding=UTF8",
			"--host=" + source.hosts().get(0).host(), "--port=" + source.hosts().get(0).port(), "--username=" + source.username(), "--no-password", database);
		String problem = SandboxProcess.execute(this.dir, dump, PASSWORD_ENV, source.password(), null, raw);
		if (problem != null) {
			throw new SandboxException("DB " + database + "를 떠 오지 못했습니다(" + problem + "). 계정에 읽기 권한이 필요하고, pg_dump는 원래 DB와 같거나 높은 버전이어야 합니다.", false);
		}
		File ready = new File(this.dir, "load.sql");
		try (BufferedReader in = Files.newBufferedReader(raw.toPath(), StandardCharsets.ISO_8859_1);
			BufferedWriter out = Files.newBufferedWriter(ready.toPath(), StandardCharsets.ISO_8859_1)) {
			String line;
			while ((line = in.readLine()) != null) {
				out.write(paths.shiftRaw(line));
				out.write('\n');
			}
		}
		List<String> load = List.of(this.psql.getPath(), "-X", "-q", "-v", "ON_ERROR_STOP=1", "--host=127.0.0.1", "--port=" + this.port,
			"--username=" + LOAD_USER, "--no-password", "--dbname=" + database);
		problem = SandboxProcess.execute(this.dir, load, PASSWORD_ENV, this.loadPassword, ready, null);
		if (problem != null) {
			throw new SandboxException("DB " + database + "의 복제본을 넣지 못했습니다(" + problem + "). 시험 PostgreSQL의 버전과 확장(extension)을 원래 DB와 맞추십시오.", false);
		}
		Files.deleteIfExists(raw.toPath());
		Files.deleteIfExists(ready.toPath());
		this.handOver(database);
	}

	/** 복제본의 주인을 슈퍼유저에서 DDL 계정으로 넘기고 읽기 권한을 줍니다. */
	private void handOver(String database) throws SQLException {
		try (Connection connection = this.admin(database); Statement statement = connection.createStatement()) {
			statement.execute("REASSIGN OWNED BY " + LOAD_USER + " TO " + DDL_USER);
			statement.execute("ALTER DATABASE " + quoted(database) + " OWNER TO " + DDL_USER);
			List<String> names = new ArrayList<String>();
			try (ResultSet rs = statement.executeQuery("SELECT nspname FROM pg_namespace WHERE nspname !~ '^pg_' AND nspname <> 'information_schema'")) {
				while (rs.next()) {
					names.add(rs.getString(1));
				}
			}
			for (String name : names) {
				statement.execute("ALTER SCHEMA " + quoted(name) + " OWNER TO " + DDL_USER);
				statement.execute("GRANT USAGE ON SCHEMA " + quoted(name) + " TO " + READ_USER);
				statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA " + quoted(name) + " TO " + READ_USER);
			}
			statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + DDL_USER + " GRANT SELECT ON TABLES TO " + READ_USER);
			statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + DDL_USER + " GRANT USAGE ON SCHEMAS TO " + READ_USER);
			String path = this.searchPaths.get(database);
			if (path != null && !path.isBlank()) {
				statement.execute("SELECT set_config('search_path', " + literal(path) + ", false)");
				statement.execute("ALTER DATABASE " + quoted(database) + " SET search_path FROM CURRENT");
			}
		}
	}

	@Override
	public List<String> apply(List<String> statements) throws SandboxException {
		List<String> existing = new ArrayList<String>();
		if (statements.isEmpty()) {
			return existing;
		}
		String database = this.databases.get(0);
		try (Connection connection = DriverManager.getConnection(this.url(database), DDL_USER, this.ddlPassword); Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(120);
			connection.setSchema(this.schemas.get(database));
			for (int i = 0; i < statements.size(); i++) {
				try {
					statement.execute(statements.get(i));
				} catch (SQLException e) {
					String where = (i + 1) + "번째 문장(" + SandboxProcess.brief(statements.get(i)) + "): " + e.getMessage();
					if (!ALREADY_THERE.contains(e.getSQLState())) {
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

	@Override
	public int port() {
		return this.port;
	}

	@Override
	public List<String> databases() {
		return this.databases;
	}

	@Override
	public List<String> notes() {
		return this.notes;
	}

	@Override
	public Map<String, Map<String, String>> readers() {
		Map<String, Map<String, String>> result = new LinkedHashMap<String, Map<String, String>>();
		for (String database : this.databases) {
			result.put(database, Map.of("url", this.url(database), "username", READ_USER, "password", this.readPassword));
		}
		return result;
	}

	@Override
	public void close() {
		this.stop();
		SandboxProcess.deleteTree(this.dir);
	}

	private void removeLeftover() throws IOException {
		this.stop();
		if (this.dir.exists() && !SandboxProcess.deleteTree(this.dir)) {
			throw new IOException("지난 시험 DB 폴더를 지우지 못했습니다: " + this.dir);
		}
	}

	private void stop() {
		if (!new File(this.data, "postmaster.pid").isFile()) {
			return;
		}
		try {
			// 버릴 DB라 바로 내린다. pg_ctl은 PID 파일이 이 data 폴더의 postgres일 때만 내린다
			SandboxProcess.execute(this.dir, List.of(this.pgCtl.getPath(), "-D", SandboxProcess.slash(this.data), "-m", "immediate", "-w", "stop"), PASSWORD_ENV, null, null, null);
		} catch (IOException e) {
			this.notes.add("일회용 PostgreSQL을 내리지 못했습니다: " + e.getMessage());
		}
	}

	private Connection admin(String database) throws SQLException {
		return DriverManager.getConnection(this.url(database), ADMIN, this.adminPassword);
	}

	private String url(String database) {
		return "jdbc:postgresql://127.0.0.1:" + this.port + "/" + database + "?sslmode=disable";
	}

	private static String quoted(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	private static String literal(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

}
