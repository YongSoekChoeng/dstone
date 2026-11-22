package net.dstone.common.web.console.management;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mybatis.spring.SqlSessionTemplate;

/**
 * <pre>
 * 콘솔 사용자 관리 테이블({앱}_management_*)을 읽고 씁니다. SQL은 resources/sqlmap/console/ConsoleManagementDao.xml 입니다.
 * 앱마다 데이터는 따로 두고(테이블 이름 앞부분이 다름) SQL과 이 코드는 한 벌만 둡니다.
 *
 * ## 테이블정의(앞부분 {tp} 생략)
 *   user        사용자(USER_ID KEY, USER_NAME, PASSWORD_HASH, USE_YN, PW_CHANGE_YN, LOGIN_FAIL_COUNT, LAST_LOGIN_AT)
 *   group       사용자그룹(GROUP_ID KEY, GROUP_NAME, DESCRIPTION, SORT_ORDER)
 *   group_user  그룹의 사용자(GROUP_ID, USER_ID)
 *   auth        권한(AUTH_ID KEY, AUTH_NAME, DESCRIPTION, ADMIN_YN, SORT_ORDER)
 *   group_auth  그룹이 가진 권한(GROUP_ID, AUTH_ID)
 *   menu        메뉴(MENU_ID KEY, PARENT_ID, MENU_NAME, URL, ICON, SORT_ORDER, USE_YN, ALL_USERS_YN)
 *   auth_menu   권한이 여는 메뉴(AUTH_ID, MENU_ID)
 * 이 클래스는 Spring 빈으로 스캔되지 않습니다. 각 앱의 ConfigConsole 이 자기 세션과 테이블 앞부분을 넘겨 만듭니다.
 * </pre>
 */
public class ConsoleManagementDao {

	private static final String NS = "net.dstone.common.web.console.management.ConsoleManagementDao.";

	private final SqlSessionTemplate sqlSession;

	private final String tablePrefix;

	/**
	 * @param sqlSession  앱의 MyBatis 세션
	 * @param tablePrefix 테이블 이름 앞부분(예: ai_management_)
	 */
	public ConsoleManagementDao(SqlSessionTemplate sqlSession, String tablePrefix) {
		this.sqlSession = sqlSession;
		this.tablePrefix = tablePrefix;
	}

	/** 테이블 앞부분을 넣은 파라미터 맵을 만듭니다. */
	public Map<String, Object> param() {
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("tp", this.tablePrefix);
		return param;
	}

	public Map<String, Object> one(String id, Map<String, Object> param) {
		return this.sqlSession.selectOne(NS + id, param);
	}

	public List<Map<String, Object>> list(String id, Map<String, Object> param) {
		return this.sqlSession.selectList(NS + id, param);
	}

	public List<String> strings(String id, Map<String, Object> param) {
		return this.sqlSession.selectList(NS + id, param);
	}

	public int count(String id, Map<String, Object> param) {
		Integer count = this.sqlSession.selectOne(NS + id, param);
		return count == null ? 0 : count.intValue();
	}

	public int write(String id, Map<String, Object> param) {
		return this.sqlSession.update(NS + id, param);
	}

}
