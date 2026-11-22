package net.dstone.ai.tools.build.sandbox;

import java.io.File;
import java.util.regex.Pattern;

/**
 * <pre>
 * PostgreSQL입니다.
 * </pre>
 */
final class PostgresKind implements SandboxDatabaseKind {

	private static final Pattern URL = Pattern.compile("^jdbc:(log4jdbc:)?postgresql:.*", Pattern.CASE_INSENSITIVE);

	@Override
	public String id() {
		return "postgresql";
	}

	@Override
	public boolean supports(String url) {
		return url != null && URL.matcher(url.trim()).matches();
	}

	@Override
	public DatabaseSource source(String name, String url, String username, String password, String schema) throws SandboxException {
		return SandboxPostgres.sourceOf(name, url, username, password);
	}

	@Override
	public boolean backslashEscapes() {
		return false;
	}

	@Override
	public SandboxDatabase create(File dir, String home, int maxDbMb) throws SandboxException {
		return new SandboxPostgres(dir, home, maxDbMb);
	}

}
