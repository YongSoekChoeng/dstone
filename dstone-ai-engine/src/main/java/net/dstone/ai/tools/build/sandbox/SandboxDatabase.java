package net.dstone.ai.tools.build.sandbox;

import java.util.List;
import java.util.Map;

/**
 * <pre>
 * 일회용 DB 한 종류입니다. 격리의 나머지(접속 돌리기, 경로 바꾸기, 시험 서버)는 DB 종류를 모릅니다.
 *
 * 새 DB를 지원하려면 이 인터페이스와 SandboxDatabaseKind를 구현하고 TestSandbox.KINDS에 더합니다.
 * 구현이 지켜야 할 것
 * - 원래 서버에는 읽기만 한다.
 * - DDL 계정은 복제본 밖에 권한이 없고, 서버의 파일과 OS 명령을 다루지 못한다.
 * - 비밀번호를 명령줄과 로그에 남기지 않는다.
 * </pre>
 */
interface SandboxDatabase {

	/** 띄우고 복제합니다. */
	void create(List<DatabaseSource> sources, SandboxPaths paths) throws SandboxException;

	/** DDL을 복제본에 반영하고, 이미 있어 건너뛴 안내를 돌려줍니다. */
	List<String> apply(List<String> statements) throws SandboxException;

	/** 복제본이 듣는 127.0.0.1의 포트 */
	int port();

	/** 복제한 DB 이름들 */
	List<String> databases();

	/** 사람에게 알릴 참고 */
	List<String> notes();

	/** 읽기 전용 접속 정보(DB 이름 → url, username, password) */
	Map<String, Map<String, String>> readers();

	/** 내리고 지웁니다. 여러 번 불러도 됩니다. */
	void close();

}
