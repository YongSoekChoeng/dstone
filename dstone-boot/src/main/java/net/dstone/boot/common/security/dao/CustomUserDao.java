package net.dstone.boot.common.security.dao;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

@Repository("customUserDao")
public class CustomUserDao extends net.dstone.boot.common.biz.BaseDao {

	/**
	 * 인증 로그인 처리. 사용자ID, 사용자PASSWORD를 파라메터로 받아서 사용자정보를 조회.
	 * @param vo Map - USER_ID(사용자ID)
	 * @return Map
	 * @exception Exception
	 */
    public Map<String, Object> selectUser(Map<String, String> vo) throws Exception {
    	return sqlSessionCommon.selectOne("net.dstone.boot.common.security.CustomUserDao.selectUser", vo);
    }

	/**
	 * 사용자 로그인 시간을 수정.
	 * @param vo Map - USER_ID(사용자ID)
	 * @return Map
	 * @exception Exception
	 */
    public void updateUserLoginTime(Map<String, String> vo) throws Exception {
    	sqlSessionCommon.update("net.dstone.boot.common.security.CustomUserDao.updateUserLoginTime", vo);
    }

	/**
	 * 인가 ROLL 조회. 사용자ID를 파라메터로 받아서 사용자ROLL정보목록을 조회.
	 * @param vo Map - USER_ID(사용자ID)
	 * @return Map
	 * @exception Exception
	 */
    public List<String> selectListAuthority(Map<String, String> vo) throws Exception {
    	return sqlSessionCommon.selectList("net.dstone.boot.common.security.CustomUserDao.selectListAuthority", vo);
    }

	/**
	 * ROLE과 프로그램URLL(전체)맵핑목록을 조회. 정적체크-Application기동시 한번만 전체설청 받아오는 경우 사용.
	 * @param vo Map
	 * @return Map
	 * @exception Exception
	 */
    public List<Map<String, Object>> selectListUrlAndRole(Map<String, String> vo) throws Exception {
    	return sqlSessionCommon.selectList("net.dstone.boot.common.security.CustomUserDao.selectListUrlAndRole", vo);
    }

	/**
	 * ROLE을 파라메터로 프로그램URLL목록을 조회. 동적체크-URL호출시마다 DB체크하는 경우 사용.
	 * @param vo Map - ROLE_ID(ROLEID)
	 * @return Map
	 * @exception Exception
	 */
    public List<Map<String, Object>> selectListUrlByRole(Map<String, String> vo) throws Exception {
    	return sqlSessionCommon.selectList("net.dstone.boot.common.security.CustomUserDao.selectListUrlByRole", vo);
    }

}
