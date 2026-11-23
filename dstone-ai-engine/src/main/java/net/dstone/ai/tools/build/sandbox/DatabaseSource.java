package net.dstone.ai.tools.build.sandbox;

import java.util.List;

/**
 * <pre>
 * 복제할 원래 DB 하나입니다. DB 종류와 상관없는 모양입니다.
 * </pre>
 *
 * @param name     프로젝트 관리의 별칭
 * @param hosts    접속 주소들(여러 개면 첫째에서 읽음)
 * @param database DB 이름(Oracle은 스키마 주인)
 * @param options  JDBC 주소의 ? 뒤(? 포함). 없으면 빈 글자. Oracle은 접속할 서비스나 SID
 * @param username 계정
 * @param password 비밀번호
 */
record DatabaseSource(String name, List<SandboxGuard.Endpoint> hosts, String database, String options, String username, String password) {

	@Override
	public String toString() {
		return this.name + "(" + this.database + ")"; // 비밀번호가 로그에 찍히지 않게
	}

}
