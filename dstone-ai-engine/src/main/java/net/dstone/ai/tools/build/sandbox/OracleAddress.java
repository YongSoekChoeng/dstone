package net.dstone.ai.tools.build.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * Oracle 접속 주소를 읽습니다. 다음 모양을 압니다.
 *
 *   &#64;host:port:SID
 *   &#64;host:port/service, &#64;//host:port/service, &#64;tcp://host:port/service
 *   &#64;(DESCRIPTION=...(HOST=..)(PORT=..)...(SERVICE_NAME=..))
 *
 * tnsnames.ora의 별칭은 어느 서버인지 알 수 없어 받지 않습니다. 암호화 접속(tcps)도 중계가 첫 패킷을 읽지 못해 받지 않습니다.
 * </pre>
 *
 * @param hosts   접속 주소들(첫째에서 읽음)
 * @param service 서비스 이름. SID로 적었으면 null
 * @param sid     SID. 서비스 이름으로 적었으면 null
 */
record OracleAddress(List<SandboxGuard.Endpoint> hosts, String service, String sid) {

	private static final int DEFAULT_PORT = 1521;

	private static final Pattern PREFIX = Pattern.compile("^jdbc:(?:log4jdbc:)?oracle:thin:@", Pattern.CASE_INSENSITIVE);
	private static final Pattern SID_FORM = Pattern.compile("^([A-Za-z0-9_.-]+):([0-9]{1,5}):([A-Za-z0-9_$#.-]+)$");
	private static final Pattern SERVICE_FORM = Pattern.compile("^(?:tcp:)?(?://)?([A-Za-z0-9_.-]+|\\[[0-9A-Fa-f:]+])(?::([0-9]{1,5}))?/([A-Za-z0-9_$#.-]+)(?::dedicated)?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern ADDRESS = Pattern.compile("\\(\\s*ADDRESS\\s*=((?:\\s*\\([^()]*\\))+)\\s*\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern ACCOUNT = Pattern.compile("[A-Za-z][A-Za-z0-9_$#]{0,127}");

	/**
	 * @param url JDBC 주소
	 * @return 읽은 주소. 다루지 못하는 모양이면 null
	 */
	static OracleAddress of(String url) {
		if (url == null) {
			return null;
		}
		Matcher prefix = PREFIX.matcher(url.trim());
		if (!prefix.find()) {
			return null;
		}
		String rest = url.trim().substring(prefix.end()).trim();
		if (rest.startsWith("(")) {
			return ofDescriptor(rest);
		}
		int options = rest.indexOf('?');
		String target = options < 0 ? rest : rest.substring(0, options);
		Matcher bySid = SID_FORM.matcher(target);
		if (bySid.matches()) {
			return new OracleAddress(List.of(new SandboxGuard.Endpoint(bySid.group(1), Integer.parseInt(bySid.group(2)))), null, bySid.group(3));
		}
		Matcher byService = SERVICE_FORM.matcher(target);
		if (!byService.matches()) {
			return null;
		}
		String host = byService.group(1).replace("[", "").replace("]", "");
		int port = byService.group(2) == null ? DEFAULT_PORT : Integer.parseInt(byService.group(2));
		return new OracleAddress(List.of(new SandboxGuard.Endpoint(host, port)), byService.group(3), null);
	}

	private static OracleAddress ofDescriptor(String text) {
		List<SandboxGuard.Endpoint> hosts = new ArrayList<SandboxGuard.Endpoint>();
		Matcher address = ADDRESS.matcher(text);
		while (address.find()) {
			String protocol = valueOf(address.group(1), "PROTOCOL");
			String host = valueOf(address.group(1), "HOST");
			String port = valueOf(address.group(1), "PORT");
			if (host == null || (protocol != null && !"TCP".equalsIgnoreCase(protocol)) || (port != null && !port.matches("[0-9]{1,5}"))) {
				return null;
			}
			hosts.add(new SandboxGuard.Endpoint(host, port == null ? DEFAULT_PORT : Integer.parseInt(port)));
		}
		String service = valueOf(text, "SERVICE_NAME");
		String sid = service == null ? valueOf(text, "SID") : null;
		return hosts.isEmpty() || (service == null && sid == null) ? null : new OracleAddress(hosts, service, sid);
	}

	private static String valueOf(String text, String key) {
		Matcher m = Pattern.compile("\\(\\s*" + key + "\\s*=\\s*([A-Za-z0-9_$#.:-]+)\\s*\\)", Pattern.CASE_INSENSITIVE).matcher(text);
		return m.find() ? m.group(1) : null;
	}

	/** 프로젝트 DB 한 줄을 복제할 원본으로 바꿉니다. */
	static DatabaseSource sourceOf(String name, String url, String username, String password, String schema) throws SandboxException {
		OracleAddress address = of(url);
		if (address == null) {
			throw new SandboxException("프로젝트 DB " + name + "의 주소를 읽을 수 없습니다. jdbc:oracle:thin:@//호스트:포트/서비스, @호스트:포트:SID, @(DESCRIPTION=...) 가운데 하나여야 합니다(tnsnames 별칭, tcps는 안 됨).", false);
		}
		String user = username == null ? "" : username.trim();
		String owner = schema == null || schema.isBlank() ? user : schema.trim();
		String upper = user.toUpperCase(Locale.ROOT);
		if (!ACCOUNT.matcher(user).matches() || !ACCOUNT.matcher(owner).matches() || "SYS".equals(upper) || "SYSTEM".equals(upper)) {
			throw new SandboxException("프로젝트 DB " + name + "는 앱이 쓰는 보통 계정이어야 복제합니다(SYS, SYSTEM, 따옴표가 필요한 이름은 안 됨).", false);
		}
		if (password == null || password.isEmpty() || password.matches("(?s).*[\"\\r\\n].*")) {
			throw new SandboxException("프로젝트 DB " + name + "의 비밀번호가 비었거나 큰따옴표·줄바꿈이 들어 있어 복제 계정을 만들 수 없습니다.", false);
		}
		return new DatabaseSource(name, address.hosts(), owner.toUpperCase(Locale.ROOT), address.connectData(), upper, password);
	}

	/** (SERVICE_NAME=x)나 (SID=x) */
	String connectData() {
		return this.service != null ? "(SERVICE_NAME=" + this.service + ")" : "(SID=" + this.sid + ")";
	}

	/** 접속 서술자 */
	static String descriptor(SandboxGuard.Endpoint endpoint, String connectData) {
		return "(DESCRIPTION=(CONNECT_TIMEOUT=15)(ADDRESS=(PROTOCOL=TCP)(HOST=" + endpoint.host() + ")(PORT=" + endpoint.port() + "))(CONNECT_DATA=" + connectData + "))";
	}

}
