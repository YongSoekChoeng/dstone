package net.dstone.ai.tools.build.sandbox;

import java.io.File;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <pre>
 * MySQL입니다. MariaDB 주소는 받지 않습니다. 실행 파일과 초기화 방법이 달라서 따로 구현하고 시험해야 합니다.
 * </pre>
 */
final class MysqlKind implements SandboxDatabaseKind {

	private static final Pattern URL = Pattern.compile("^jdbc:(log4jdbc:)?mysql:.*", Pattern.CASE_INSENSITIVE);

	@Override
	public String id() {
		return "mysql";
	}

	@Override
	public boolean supports(String url) {
		return url != null && URL.matcher(url.trim()).matches();
	}

	@Override
	public DatabaseSource source(String name, String url, String username, String password, String schema) throws SandboxException {
		return SandboxMysql.sourceOf(name, url, username, password, schema);
	}

	@Override
	public SandboxDdl.Dialect dialect() {
		return SandboxDdl.Dialect.MYSQL;
	}

	@Override
	public SandboxDatabase create(File dir, Map<String, String> settings, int maxDbMb) throws SandboxException {
		return new SandboxMysql(dir, settings.get("mysql-home"), maxDbMb);
	}

}
