package net.dstone.ai.tools.build.sandbox;

import java.io.File;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <pre>
 * Oracle입니다. thin 드라이버만 다룹니다. OCI 드라이버는 C 라이브러리로 접속해서 SandboxGuard가 잡지 못합니다.
 * </pre>
 */
final class OracleKind implements SandboxDatabaseKind {

	private static final Pattern URL = Pattern.compile("^jdbc:(log4jdbc:)?oracle:thin:.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	@Override
	public String id() {
		return "oracle";
	}

	@Override
	public boolean supports(String url) {
		return url != null && URL.matcher(url.trim()).matches();
	}

	@Override
	public DatabaseSource source(String name, String url, String username, String password, String schema) throws SandboxException {
		return OracleAddress.sourceOf(name, url, username, password, schema);
	}

	@Override
	public SandboxDdl.Dialect dialect() {
		return SandboxDdl.Dialect.ORACLE;
	}

	@Override
	public SandboxDatabase create(File dir, Map<String, String> settings, int maxDbMb) throws SandboxException {
		return new SandboxOracle(dir, settings.get("oracle-url"), settings.get("oracle-username"), settings.get("oracle-password"), maxDbMb);
	}

}
