package net.dstone.ai.tools.build.sandbox;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * <pre>
 * 시험 한 번 동안만 사는 Oracle PDB입니다.
 *
 * Oracle은 MySQL처럼 서버를 매번 새로 만들 수 없습니다(수십 분, 설치와 서비스 등록).
 * 그래서 시험 전용 Oracle 서버(12c 이상 CDB)를 하나 두고, 실행마다 PDB를 만들었다가 지웁니다.
 * PDB끼리는 계정·스키마·데이터가 따로라 실행이 섞이지 않고, 스키마 이름도 원본 그대로 씁니다.
 * 원래 DB와 같은 서버는 쓰지 않습니다. 개발 DB에 PDB를 만들고 지우게 되기 때문입니다.
 *
 * 계정
 *   설정의 관리 계정   PDB 만들기·지우기(SYSDBA). LLM이 쓴 DDL이 들어간 뒤로는 그 PDB 안에서 아무것도 실행하지 않는다
 *   앱의 계정         이름과 비밀번호 그대로. 자기 스키마의 객체만 만든다. 복제가 끝나면 DB 링크 권한을 뺀다
 *   DSTONE_RO_n     dbQuery용. 읽기만(READ ANY TABLE이라 행을 잠그지도 못한다)
 *
 * DDL은 sqlplus가 아니라 JDBC로 보냅니다. sqlplus는 HOST 같은 OS 명령을 실행할 수 있습니다.
 * </pre>
 */
final class SandboxOracle implements SandboxDatabase {

	/** 복제한 객체가 들어가는 테이블스페이스 */
	static final String TABLESPACE = "USERS";

	private static final String PDB_PREFIX = "DSTONE_SBX_";
	private static final Pattern OWN_PDB = Pattern.compile(PDB_PREFIX + "[0-9A-F]{8}");
	private static final String MARKER_FILE = "oracle.pdb";
	private static final String READ_USER = "DSTONE_RO_";
	private static final String APP_GRANTS = "CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE MATERIALIZED VIEW, CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER, CREATE TYPE, CREATE SYNONYM";

	/** 이미 있어서 건너뛰어도 되는 오류: 객체, 컬럼, 기본키, 유니크, 외래키, 인덱스, NOT NULL, 같은 키의 행 */
	private static final Set<Integer> ALREADY_THERE = Set.of(955, 1430, 2260, 2261, 2275, 1408, 1442, 1);

	private static final int NO_SUCH_PDB = 65011;
	private static final int TOO_MANY_PDBS = 65010;

	private final File dir;
	private final OracleAddress server;
	private final String serverUrl;
	private final String adminUser;
	private final String adminPassword;
	private final int maxDbMb;
	private final String pdb = PDB_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
	private final String readPassword = "R" + SandboxProcess.secret();
	private final List<String> notes = new ArrayList<String>();
	private final List<String> databases = new ArrayList<String>();
	private final Map<String, String> readUsers = new LinkedHashMap<String, String>();
	private final Map<String, String> passwords = new LinkedHashMap<String, String>();
	private String pdbUrl;
	private String ddlOwner;
	private OracleRelay relay;
	private boolean made;

	SandboxOracle(File dir, String url, String username, String password, int maxDbMb) throws SandboxException {
		if (blank(url) || blank(username) || blank(password)) {
			throw new SandboxException("시험 전용 Oracle 서버 설정(sandbox-oracle-url, sandbox-oracle-username, sandbox-oracle-password)이 비어 자동 검증을 하지 못합니다. 원래 DB로 대신 시험하지 않습니다.", false);
		}
		this.server = OracleAddress.of(url);
		if (this.server == null || this.server.service() == null) {
			throw new SandboxException("설정 sandbox-oracle-url은 jdbc:oracle:thin:@//호스트:포트/CDB서비스 모양입니다.", false);
		}
		this.dir = dir.getAbsoluteFile();
		this.serverUrl = "jdbc:oracle:thin:@" + OracleAddress.descriptor(this.server.hosts().get(0), this.server.connectData());
		this.adminUser = username.trim();
		this.adminPassword = password;
		this.maxDbMb = maxDbMb;
	}

	@Override
	public void create(List<DatabaseSource> sources, SandboxPaths paths) throws SandboxException {
		Map<String, DatabaseSource> accounts = new LinkedHashMap<String, DatabaseSource>();
		for (DatabaseSource source : sources) {
			DatabaseSource first = accounts.putIfAbsent(source.username(), source);
			if (first != null && (!first.options().equalsIgnoreCase(source.options()) || !sameEndpoint(first.hosts().get(0), source.hosts().get(0)))) {
				throw new SandboxException("이름이 같은 계정 " + source.username() + "이 서로 다른 DB에 있어 PDB 하나에 함께 복제할 수 없습니다.", false);
			}
		}
		List<String> owners = new ArrayList<String>();
		for (DatabaseSource source : sources) {
			if (!accounts.containsKey(source.database())) {
				throw new SandboxException("스키마 " + source.database() + "의 주인 계정이 프로젝트 관리에 없어 복제할 수 없습니다. 그 계정의 DB도 한 줄 등록하십시오.", false);
			}
			// 다른 계정이 쓰는 스키마부터 복제한다. 그래야 그 계정에 준 권한이 따라온다
			if (!source.database().equals(source.username()) && !owners.contains(source.database())) {
				owners.add(source.database());
			}
		}
		for (String account : accounts.keySet()) {
			if (!owners.contains(account)) {
				owners.add(account);
			}
		}
		this.ddlOwner = sources.get(0).database();
		try (Connection root = this.root()) {
			this.refuseSameServer(root, accounts.values());
			this.removeLeftover(root);
			this.makePdb(root, accounts.values());
			for (String owner : owners) {
				DatabaseSource source = accounts.get(owner);
				String descriptor = OracleAddress.descriptor(source.hosts().get(0), source.options());
				try (Connection from = connect("jdbc:oracle:thin:@" + descriptor, owner, source.password());
					Connection to = connect(this.pdbUrl, owner, source.password())) {
					from.setAutoCommit(false);
					to.setAutoCommit(false);
					this.notes.addAll(OracleClone.copy(from, to, source, descriptor, this.maxDbMb));
					from.rollback();
					int shifted = OracleClone.shiftPaths(to, paths);
					if (shifted > 0) {
						this.notes.add("복제 데이터(" + owner + "): 원본 폴더 경로 " + shifted + "칸을 사본 경로로 바꿈");
					}
					to.commit();
				}
			}
			this.seal(root, accounts.keySet());
			this.databases.addAll(this.readUsers.keySet());
			this.relay = new OracleRelay(this.server.hosts().get(0), serviceOf(root, this.pdb));
		} catch (SQLException e) {
			throw new SandboxException("일회용 Oracle PDB를 준비하지 못했습니다: " + OracleClone.brief(e), false);
		} catch (IOException e) {
			throw new SandboxException("일회용 Oracle PDB를 준비하지 못했습니다: " + e.getMessage(), false);
		}
	}

	/** 시험 전용 서버가 원래 DB 서버면 멈춥니다. 주소의 이름이 달라도 알아보려고 서버가 말하는 이름도 견줍니다. */
	private void refuseSameServer(Connection root, Iterable<DatabaseSource> accounts) throws SQLException, SandboxException {
		String test = identity(root);
		for (DatabaseSource source : accounts) {
			boolean same = false;
			for (SandboxGuard.Endpoint host : source.hosts()) {
				same |= sameEndpoint(host, this.server.hosts().get(0));
			}
			if (!same) {
				try (Connection from = connect("jdbc:oracle:thin:@" + OracleAddress.descriptor(source.hosts().get(0), source.options()), source.username(), source.password())) {
					same = test.equalsIgnoreCase(identity(from));
				} catch (SQLException e) {
					throw new SandboxException("프로젝트 DB " + source.name() + "를 읽지 못해 복제할 수 없습니다: " + OracleClone.brief(e), false);
				}
			}
			if (same) {
				throw new SandboxException("설정 sandbox-oracle-url이 프로젝트 DB " + source.name() + "와 같은 Oracle 서버입니다. 시험 전용 서버를 따로 두어야 합니다.", false);
			}
		}
	}

	private static String identity(Connection connection) throws SQLException {
		return String.join("/", OracleClone.strings(connection, "SELECT SYS_CONTEXT('USERENV','SERVER_HOST') || '|' || SYS_CONTEXT('USERENV','INSTANCE_NAME') || '|'"
			+ " || SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') FROM dual"));
	}

	/** 지난번에 이 폴더로 만든 PDB가 남았으면 지웁니다(엔진이 중간에 죽었을 때). */
	private void removeLeftover(Connection root) throws IOException, SQLException {
		File marker = new File(this.dir, MARKER_FILE);
		if (marker.isFile()) {
			String old = Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim();
			if (OWN_PDB.matcher(old).matches()) {
				drop(root, old);
			}
		}
		Files.createDirectories(this.dir.toPath());
		Files.writeString(marker.toPath(), this.pdb, StandardCharsets.UTF_8);
	}

	private void makePdb(Connection root, Iterable<DatabaseSource> accounts) throws SQLException, SandboxException {
		List<String> seedFiles = OracleClone.strings(root, "SELECT name FROM v$datafile WHERE con_id = (SELECT con_id FROM v$containers WHERE name = 'PDB$SEED') ORDER BY file#");
		List<String> omf = OracleClone.strings(root, "SELECT value FROM v$parameter WHERE name = 'db_create_file_dest' AND value IS NOT NULL");
		if (seedFiles.isEmpty()) {
			throw new SandboxException("시험 전용 Oracle 서버는 12c 이상 CDB여야 하고, 주소는 CDB 루트의 서비스여야 합니다.", false);
		}
		String folder = null;
		String placement = "";
		if (omf.isEmpty()) {
			String first = seedFiles.get(0);
			char separator = first.indexOf('\\') >= 0 ? '\\' : '/';
			String seedFolder = first.substring(0, first.lastIndexOf(separator) + 1);
			folder = seedFolder.substring(0, seedFolder.lastIndexOf(separator, seedFolder.length() - 2) + 1) + this.pdb + separator;
			placement = " FILE_NAME_CONVERT = (" + OracleClone.literal(seedFolder) + ", " + OracleClone.literal(folder) + ")";
		}
		try (Statement statement = root.createStatement()) {
			statement.setEscapeProcessing(false);
			try {
				statement.execute("CREATE PLUGGABLE DATABASE " + this.pdb + " ADMIN USER dstone_pdb IDENTIFIED BY \"A" + SandboxProcess.secret() + "\"" + placement);
			} catch (SQLException e) {
				throw new SandboxException(e.getErrorCode() == TOO_MANY_PDBS
					? "시험 전용 Oracle의 PDB 수가 한도에 찼습니다. 동시에 도는 검증이 끝난 뒤 다시 하거나, 남은 " + PDB_PREFIX + "* PDB를 관리자가 지우십시오."
					: "시험 PDB를 만들지 못했습니다(ORA-" + e.getErrorCode() + "). 관리 계정에 SYSDBA가 있는지, 디스크가 남았는지 확인하십시오.", false);
			}
			this.made = true;
			statement.execute("ALTER PLUGGABLE DATABASE " + this.pdb + " OPEN");
			String service = serviceOf(root, this.pdb);
			this.pdbUrl = "jdbc:oracle:thin:@" + OracleAddress.descriptor(this.server.hosts().get(0), "(SERVICE_NAME=" + service + ")");

			statement.execute("ALTER SESSION SET CONTAINER = " + this.pdb);
			if (OracleClone.strings(root, "SELECT name FROM v$tablespace WHERE name = '" + TABLESPACE + "' AND con_id = SYS_CONTEXT('USERENV','CON_ID')").isEmpty()) {
				statement.execute("CREATE TABLESPACE " + TABLESPACE + " DATAFILE " + (folder == null ? "" : OracleClone.literal(folder + "users01.dbf") + " ")
					+ "SIZE 20M AUTOEXTEND ON NEXT 20M MAXSIZE " + Math.max(256, Math.min(30000, this.maxDbMb * 2L)) + "M");
			}
			statement.execute("ALTER PLUGGABLE DATABASE DEFAULT TABLESPACE " + TABLESPACE);
			int number = 0;
			for (DatabaseSource source : accounts) {
				String user = OracleClone.quoted(source.username());
				try {
					statement.execute("CREATE USER " + user + " IDENTIFIED BY " + OracleClone.quoted(source.password()) + " DEFAULT TABLESPACE " + TABLESPACE
						+ " QUOTA UNLIMITED ON " + TABLESPACE);
				} catch (SQLException e) {
					throw new SandboxException("시험 PDB에 앱 계정 " + source.username() + "을 만들지 못했습니다(ORA-" + e.getErrorCode() + "). Oracle 내부 계정 이름이거나 비밀번호 규칙에 걸렸을 수 있습니다.", false);
				}
				statement.execute("GRANT " + APP_GRANTS + ", CREATE DATABASE LINK TO " + user);
				this.passwords.put(source.username(), source.password());

				String reader = READ_USER + (++number);
				statement.execute("CREATE USER " + reader + " IDENTIFIED BY " + OracleClone.quoted(this.readPassword));
				statement.execute("GRANT CREATE SESSION, READ ANY TABLE TO " + reader);
				// 스키마를 붙이지 않은 SELECT도 되게
				statement.execute("CREATE TRIGGER SYS." + reader + "_LOGON AFTER LOGON ON " + reader + ".SCHEMA BEGIN EXECUTE IMMEDIATE "
					+ OracleClone.literal("ALTER SESSION SET CURRENT_SCHEMA = " + user) + "; END;");
				this.readUsers.put(source.username(), reader);
			}
			statement.execute("ALTER SESSION SET CONTAINER = CDB$ROOT");
		}
	}

	/** 복제가 끝났으니 밖으로 나가는 길과 저절로 도는 작업을 닫습니다. */
	private void seal(Connection root, Iterable<String> accounts) throws SQLException {
		try (Statement statement = root.createStatement()) {
			statement.execute("ALTER SESSION SET CONTAINER = " + this.pdb);
			for (String account : accounts) {
				statement.execute("REVOKE CREATE DATABASE LINK FROM " + OracleClone.quoted(account));
			}
			statement.execute("ALTER SYSTEM SET job_queue_processes = 0 SCOPE = MEMORY");
			statement.execute("ALTER SESSION SET CONTAINER = CDB$ROOT");
		}
	}

	private static String serviceOf(Connection root, String pdb) throws SQLException {
		List<String> domain = OracleClone.strings(root, "SELECT value FROM v$parameter WHERE name = 'db_domain' AND value IS NOT NULL");
		return domain.isEmpty() ? pdb : pdb + "." + domain.get(0);
	}

	@Override
	public List<String> apply(List<String> statements) throws SandboxException {
		List<String> existing = new ArrayList<String>();
		if (statements.isEmpty()) {
			return existing;
		}
		try (Connection connection = connect(this.pdbUrl, this.ddlOwner, this.passwords.get(this.ddlOwner)); Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(120);
			statement.setEscapeProcessing(false); // :new 와 { }를 JDBC 문법으로 읽지 않게
			for (int i = 0; i < statements.size(); i++) {
				String where = (i + 1) + "번째 문장(" + SandboxProcess.brief(statements.get(i)) + "): ";
				try {
					statement.clearWarnings();
					statement.execute(statements.get(i));
				} catch (SQLException e) {
					if (!ALREADY_THERE.contains(e.getErrorCode())) {
						throw new SandboxException("DDL을 시험 DB에 반영하지 못했습니다. " + where + OracleClone.brief(e), true);
					}
					existing.add(where + OracleClone.brief(e));
					continue;
				}
				// 컴파일되지 않은 프로시저·트리거도 Oracle은 성공으로 답한다
				SQLWarning warning = statement.getWarnings();
				if (warning != null) {
					List<String> errors = OracleClone.strings(connection, "SELECT name || ' ' || line || '행: ' || text FROM user_errors WHERE attribute = 'ERROR' ORDER BY name, sequence");
					if (!errors.isEmpty()) {
						throw new SandboxException("DDL의 PL/SQL이 컴파일되지 않았습니다. " + where + errors.get(0), true);
					}
				}
			}
		} catch (SQLException e) {
			throw new SandboxException("일회용 Oracle PDB에 DDL 계정으로 접속하지 못했습니다: " + OracleClone.brief(e), false);
		}
		return existing;
	}

	@Override
	public int port() {
		return this.relay.port();
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
		for (Map.Entry<String, String> reader : this.readUsers.entrySet()) {
			result.put(reader.getKey(), Map.of("url", this.pdbUrl, "username", reader.getValue(), "password", this.readPassword));
		}
		return result;
	}

	@Override
	public void close() {
		if (this.relay != null) {
			this.relay.close();
		}
		if (!this.made) {
			return;
		}
		try (Connection root = this.root()) {
			drop(root, this.pdb);
			this.made = false;
			Files.deleteIfExists(new File(this.dir, MARKER_FILE).toPath());
		} catch (SQLException | IOException e) {
			// 표시 파일이 남으므로 이 프로젝트의 다음 시험이 지운다
			this.notes.add("시험 PDB " + this.pdb + "를 지우지 못했습니다: " + e.getMessage());
		}
	}

	private static void drop(Connection root, String pdb) throws SQLException {
		try (Statement statement = root.createStatement()) {
			try {
				statement.execute("ALTER PLUGGABLE DATABASE " + pdb + " CLOSE IMMEDIATE");
			} catch (SQLException e) {
				if (e.getErrorCode() == NO_SUCH_PDB) {
					return;
				}
				// 이미 닫혀 있다. 지우기는 그대로 한다
			}
			statement.execute("DROP PLUGGABLE DATABASE " + pdb + " INCLUDING DATAFILES");
		}
	}

	private Connection root() throws SQLException {
		Properties properties = properties(this.adminUser, this.adminPassword);
		properties.setProperty("internal_logon", "sysdba");
		return DriverManager.getConnection(this.serverUrl, properties);
	}

	private static Connection connect(String url, String user, String password) throws SQLException {
		return DriverManager.getConnection(url, properties(user, password));
	}

	private static Properties properties(String user, String password) {
		Properties properties = new Properties();
		properties.setProperty("user", user);
		properties.setProperty("password", password);
		return properties;
	}

	private static boolean sameEndpoint(SandboxGuard.Endpoint left, SandboxGuard.Endpoint right) {
		return left.port() == right.port() && SandboxGuard.sameHost(left.host(), right.host());
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
