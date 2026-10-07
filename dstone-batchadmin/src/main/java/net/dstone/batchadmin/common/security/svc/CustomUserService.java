package net.dstone.batchadmin.common.security.svc;

import java.util.Map;

public interface CustomUserService {

	/**
	 * 인증 로그인 처리. 사용자ID를 파라메터로 받아서 사용자정보(TB_ADMIN_USER)를 조회.
	 * net.dstone.batchadmin.common.security.CustomAuthenticationProvider.authenticate(Authentication) 에서 호출
	 * @param param
	 * @return 값이 있을 경우 정상적인로그인 진행. 값이 없거나 NULL일 경우 net.dstone.common.consts.ErrCd.USER_NOT_REG 예외 발생.
	 */
	public Map<String, Object> loginProcess(Map<String, String> param) throws Exception;

	/**
	 * 사용자 로그인 시간을 수정.
	 * @param param
	 * @throws Exception
	 */
	public void updateUserLoginTime(Map<String, String> param) throws Exception;

}
