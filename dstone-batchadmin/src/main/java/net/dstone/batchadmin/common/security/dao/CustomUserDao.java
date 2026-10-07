package net.dstone.batchadmin.common.security.dao;

import java.util.Map;

import org.springframework.stereotype.Repository;

@Repository("customUserDao")
public class CustomUserDao extends net.dstone.batchadmin.common.biz.BaseDao {

	/**
	 * 관리자 로그인 처리. 사용자ID를 파라메터로 받아서 사용자정보(TB_ADMIN_USER)를 조회.
	 * @param vo Map - USER_ID(사용자ID)
	 * @return Map
	 * @exception Exception
	 */
	public Map<String, Object> selectUser(Map<String, String> vo) throws Exception {
		return sqlSessionCommon.selectOne("net.dstone.batchadmin.common.security.CustomUserDao.selectUser", vo);
	}

	/**
	 * 사용자 로그인 시간을 수정.
	 * @param vo Map - USER_ID(사용자ID)
	 * @exception Exception
	 */
	public void updateUserLoginTime(Map<String, String> vo) throws Exception {
		sqlSessionCommon.update("net.dstone.batchadmin.common.security.CustomUserDao.updateUserLoginTime", vo);
	}

}
