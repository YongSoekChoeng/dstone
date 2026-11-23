package net.dstone.ai.tools.build.sandbox;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <pre>
 * 시험 서버 한 번을 원래 것들에서 떼어 놓는 묶음입니다(tools.build.TestServerTool이 씁니다).
 *
 *   DB      따로 띄운 일회용 DB에 복제하고, DDL은 거기에만 반영(SandboxDatabase 구현, SandboxDdl)
 *   네트워크  원래 DB로 가는 연결은 복제본으로, 그 밖은 끊음(SandboxGuard)
 *   파일     사본과 복제 데이터 속 원본 폴더 경로를 사본 경로로(SandboxPaths)
 *
 * 하나라도 만들지 못하면 원래 DB로 대신 띄우지 않고 실패합니다.
 * 띄울 때마다 새로 만들고 내릴 때 지웁니다. 그래서 몇 번을 시험해도 시작 상태가 같습니다.
 * </pre>
 */
public final class TestSandbox {

	private static final String DIR = "sandbox";

	/** 지원하는 DB 종류들 */
	private static final List<SandboxDatabaseKind> KINDS = List.of(new MysqlKind(), new PostgresKind(), new OracleKind());

	/** 읽는 설정들(키 sandbox-{이름}) */
	private static final List<String> SETTINGS = List.of("mysql-home", "postgresql-home", "oracle-url", "oracle-username", "oracle-password");

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private final SandboxDatabase database;
	private final List<String> report = new ArrayList<String>();
	private SandboxGuard appGuard;
	private SandboxGuard buildGuard;

	private TestSandbox(SandboxDatabase database) {
		this.database = database;
	}

	/** 읽어야 할 설정의 이름들(설정 키 sandbox-{이름}) */
	public static List<String> settingNames() {
		return SETTINGS;
	}

	/**
	 * 만듭니다. 실패하면 만들던 것을 치우고 던집니다.
	 *
	 * @param base        프로젝트의 시험 서버 폴더
	 * @param sourceRoot  원본 폴더(읽기만)
	 * @param app         원본 + 개발 결과로 맞춘 사본
	 * @param connections 프로젝트 관리의 DB들(별칭 → url, username, password, schema). 첫째 DB에 DDL을 반영
	 * @param ddlFile     DDL 파일. 없거나 빈 값이면 DDL 없음
	 * @param settings    settingNames()의 이름 → 설정값
	 * @param maxDbMb     복제할 DB 하나의 크기 상한
	 * @param allowHosts  앱이 접속해도 되는 곳(host:port, 쉼표로 구분)
	 */
	public static TestSandbox create(File base, File sourceRoot, File app, Map<String, Map<String, String>> connections,
		String ddlFile, Map<String, String> settings, int maxDbMb, String allowHosts) throws SandboxException {
		if (connections.isEmpty()) {
			throw new SandboxException("프로젝트 관리에 이 프로젝트의 DB가 없어 격리 시험 DB를 만들 수 없습니다.", false);
		}
		SandboxDatabaseKind kind = kindOf(connections);
		List<DatabaseSource> sources = new ArrayList<DatabaseSource>();
		for (Map.Entry<String, Map<String, String>> entry : connections.entrySet()) {
			Map<String, String> value = entry.getValue();
			sources.add(kind.source(entry.getKey(), value.get("url"), value.get("username"), value.get("password"), value.get("schema")));
		}
		TestSandbox sandbox = new TestSandbox(kind.create(new File(base, DIR), settings, maxDbMb));
		boolean ready = false;
		try {
			String unguarded = SandboxPaths.unguardedDatabase(app);
			if (unguarded != null) {
				throw new SandboxException("접속을 가로챌 수 없는 DB 드라이버를 쓰는 프로젝트라 격리 시험 서버를 띄우지 않습니다(원래 DB에 쓸 수 있어서): " + unguarded, false);
			}
			SandboxPaths paths = new SandboxPaths(sourceRoot.getAbsolutePath(), app.getAbsolutePath());
			// DDL 파일의 잘못은 복제(수십 초)보다 먼저 알린다
			SandboxDdl.Script script = readScript(ddlFile, paths, kind.dialect());

			int rewritten = paths.rewrite(app);
			sandbox.database.create(sources, paths);
			List<String> existing = sandbox.database.apply(script.statements());

			List<SandboxGuard.Endpoint> originals = new ArrayList<SandboxGuard.Endpoint>();
			for (DatabaseSource source : sources) {
				originals.addAll(source.hosts());
			}
			sandbox.appGuard = new SandboxGuard(originals, sandbox.database.port(), false);
			for (SandboxGuard.Endpoint endpoint : endpointsOf(allowHosts)) {
				sandbox.appGuard.allow(endpoint);
			}
			sandbox.buildGuard = new SandboxGuard(originals, sandbox.database.port(), true);

			sandbox.report.add("격리: 일회용 DB 복제본(" + String.join(", ", sandbox.database.databases()) + ")을 씁니다. 원래 DB에는 쓰지 않습니다.");
			sandbox.report.add("DDL: " + (script.statements().isEmpty() ? "반영할 것 없음" : script.statements().size() + "문장을 복제본에 반영")
				+ (script.skipped() > 0 ? "(롤백 블록 " + script.skipped() + "개는 실행하지 않음)" : ""));
			for (String line : existing) {
				sandbox.report.add("DDL 주의 - 이미 있어 건너뜀. 구조나 값이 DDL과 같은지 확인: " + line);
			}
			if (rewritten > 0) {
				sandbox.report.add("파일 경로: 사본의 " + rewritten + "개 파일에서 원본 폴더 경로를 사본 경로로 바꿈");
			}
			sandbox.report.addAll(sandbox.database.notes());
			ready = true;
			return sandbox;
		} catch (IOException e) {
			throw new SandboxException("격리 시험 환경을 만들지 못했습니다: " + e.getMessage(), false);
		} finally {
			if (!ready) {
				sandbox.close();
			}
		}
	}

	/**
	 * 프로젝트 DB 전부를 다룰 종류 하나를 고릅니다. 하나라도 못 다루면 만들지 않습니다.
	 * 일부만 복제하면 나머지 DB에는 시험이 그대로 쓰게 되기 때문입니다.
	 */
	private static SandboxDatabaseKind kindOf(Map<String, Map<String, String>> connections) throws SandboxException {
		for (SandboxDatabaseKind kind : KINDS) {
			boolean all = true;
			for (Map<String, String> connection : connections.values()) {
				all &= kind.supports(connection.get("url"));
			}
			if (all) {
				return kind;
			}
		}
		List<String> supported = new ArrayList<String>();
		for (SandboxDatabaseKind kind : KINDS) {
			supported.add(kind.id());
		}
		throw new SandboxException("이 프로젝트의 DB 구성은 격리 시험을 지원하지 않아 자동 검증을 하지 못합니다(지원: " + String.join(", ", supported)
			+ ", 한 프로젝트에 한 종류). 원래 DB로 대신 시험하지 않습니다.", false);
	}

	private static SandboxDdl.Script readScript(String ddlFile, SandboxPaths paths, SandboxDdl.Dialect dialect) throws IOException, SandboxException {
		if (ddlFile == null || ddlFile.isBlank() || !new File(ddlFile.trim()).exists()) {
			return new SandboxDdl.Script(List.of(), 0, null);
		}
		File file = new File(ddlFile.trim());
		SandboxDdl.Script script = SandboxDdl.parse(file.getName(), paths.shift(Files.readString(file.toPath(), StandardCharsets.UTF_8)), dialect);
		if (script.problem() != null) {
			throw new SandboxException("DDL 파일을 실행할 수 없습니다(" + file + "): " + script.problem(), true);
		}
		return script;
	}

	private static List<SandboxGuard.Endpoint> endpointsOf(String text) throws SandboxException {
		List<SandboxGuard.Endpoint> result = new ArrayList<SandboxGuard.Endpoint>();
		for (String part : text == null ? new String[0] : text.split(",")) {
			String value = part.trim();
			if (value.isEmpty()) {
				continue;
			}
			int colon = value.lastIndexOf(':');
			if (colon <= 0 || !value.substring(colon + 1).matches("[0-9]{1,5}")) {
				throw new SandboxException("설정 sandbox-allow-hosts는 host:port 모양입니다: " + value, false);
			}
			result.add(new SandboxGuard.Endpoint(value.substring(0, colon), Integer.parseInt(value.substring(colon + 1))));
		}
		return result;
	}

	/**
	 * <pre>
	 * 빌드한 산출물을 띄워도 격리가 지켜지는지 봅니다. 지켜지지 않으면 던집니다(통과로 치지 않음).
	 * </pre>
	 *
	 * @param archive     WAR나 Spring Boot jar
	 * @param trustedLibs 사람이 확인한 jar 이름의 앞부분(쉼표로 구분)
	 */
	public void checkArtifact(File archive, String trustedLibs) throws SandboxException {
		try {
			List<String> found = SandboxArtifact.unguarded(archive, trustedLibs == null ? List.of() : List.of(trustedLibs.split(",")));
			if (!found.isEmpty()) {
				throw new SandboxException("격리를 보장할 수 없어 자동 검증을 하지 못합니다. 접속을 가로챌 수 없는 수단(NIO)이 들어 있습니다: "
					+ String.join(", ", found.subList(0, Math.min(10, found.size()))), false);
			}
		} catch (IOException e) {
			throw new SandboxException("빌드 산출물을 검사하지 못했습니다: " + e.getMessage(), false);
		}
	}

	/** 앱이 자기 자신을 부를 수 있게 서버 포트를 엽니다. */
	public void allowSelf(int port) {
		this.appGuard.allow(new SandboxGuard.Endpoint("127.0.0.1", port));
	}

	/** 앱 JVM을 격리하는 옵션 */
	public String appOptions() {
		return this.appGuard.jvmOptions();
	}

	/** 빌드 JVM 옵션(원래 DB로 가는 연결만 돌림) */
	public String buildOptions() {
		return this.buildGuard.jvmOptions();
	}

	/** 만든 내용 안내 줄들 */
	public List<String> report() {
		return this.report;
	}

	/** 막은 연결들 */
	public List<String> denied() {
		return this.appGuard.denied();
	}

	/** dbQuery로 볼 복제본들(별칭 → url, username, password, schema, description) */
	public Map<String, Map<String, String>> readConnections() {
		Map<String, Map<String, String>> result = new LinkedHashMap<String, Map<String, String>>();
		for (Map.Entry<String, Map<String, String>> reader : this.database.readers().entrySet()) {
			String database = reader.getKey();
			Map<String, String> setting = new LinkedHashMap<String, String>(reader.getValue());
			setting.put("schema", database);
			setting.put("description", "격리 시험 서버의 일회용 복제본");
			result.put(SandboxRegistry.ALIAS_PREFIX + this.id + "." + database, setting);
		}
		return result;
	}

	/** 모두 내리고 지웁니다. 여러 번 불러도 됩니다. */
	public void close() {
		if (this.appGuard != null) {
			this.appGuard.close();
		}
		if (this.buildGuard != null) {
			this.buildGuard.close();
		}
		this.database.close();
	}

}
