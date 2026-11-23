package net.dstone.ai.tools.build.sandbox;

import java.io.File;
import java.util.Map;

/**
 * <pre>
 * JDBC 주소를 보고 어떤 일회용 DB를 만들지 정하는 자리입니다.
 * </pre>
 */
interface SandboxDatabaseKind {

	/** 설정과 안내에 쓰는 이름 */
	String id();

	/** 이 주소를 다루는가 */
	boolean supports(String url);

	/** 주소를 나눕니다. */
	DatabaseSource source(String name, String url, String username, String password, String schema) throws SandboxException;

	/** SQL 파일을 문장으로 나누는 규칙 */
	SandboxDdl.Dialect dialect();

	/**
	 * @param dir     일회용 폴더
	 * @param settings 설정 sandbox-* 값들(키는 sandbox-를 뺀 것. TestSandbox.SETTINGS)
	 * @param maxDbMb 복제할 DB 하나의 크기 상한
	 */
	SandboxDatabase create(File dir, Map<String, String> settings, int maxDbMb) throws SandboxException;

}
