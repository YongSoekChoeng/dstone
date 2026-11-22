package net.dstone.ai.tools.build.sandbox;

import java.io.File;

/**
 * <pre>
 * JDBC 주소를 보고 어떤 일회용 DB를 만들지 정하는 자리입니다.
 * </pre>
 */
interface SandboxDatabaseKind {

	/** 설정과 안내에 쓰는 이름(설정 키 sandbox-{id}-home) */
	String id();

	/** 이 주소를 다루는가 */
	boolean supports(String url);

	/** 주소를 나눕니다. */
	DatabaseSource source(String name, String url, String username, String password, String schema) throws SandboxException;

	/** 따옴표 안의 \\가 다음 글자를 감싸는가(MySQL은 true, PostgreSQL은 false) */
	boolean backslashEscapes();

	/**
	 * @param dir     일회용 폴더
	 * @param home    그 DB의 설치 폴더
	 * @param maxDbMb 복제할 DB 하나의 크기 상한
	 */
	SandboxDatabase create(File dir, String home, int maxDbMb) throws SandboxException;

}
