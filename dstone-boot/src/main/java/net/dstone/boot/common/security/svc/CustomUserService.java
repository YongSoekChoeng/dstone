package net.dstone.boot.common.security.svc;

import java.util.List;
import java.util.Map;

public interface CustomUserService {

	/**
	 * 인증 로그인 처리. 사용자ID, 사용자PASSWORD를 파라메터로 받아서 사용자정보를 조회.
	 * net.dstone.boot.common.security.CustomAuthenticationProvider.authenticate(Authentication) 에서 호출
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
	
	/**
	 * 인가 ROLL 조회. 사용자ID를 파라메터로 받아서 사용자ROLL정보목록을 조회.
	 * net.dstone.boot.common.security.CustomAuthenticationProvider.authenticate(Authentication) 에서 호출
	 * @param param
	 * @return
	 */
	public List<String> selectListAuthority(Map<String, String> param) throws Exception;
	
	/**
	 * ROLE과 프로그램URLL(전체)맵핑목록을 조회. 정적체크-Application기동시 한번만 전체설청 받아오는 경우 사용.
	 * net.dstone.common.config.ConfigSecurity.setAntMatchers(HttpSecurity http) 에서 호출
	 * @param param
	 * @return
	 */
	public List<Map<String, Object>> selectListUrlAndRole(Map<String, String> param) throws Exception;

	/**
	 * ROLE을 파라메터로 프로그램URLL목록을 조회. 동적체크-URL호출시마다 DB체크하는 경우 사용.
	 * net.dstone.boot.common.security.CustomAuthChecker.check(HttpServletRequest, Authentication) 에서 호출
	 * @param param
	 * @return
	 */
	public List<Map<String, Object>> selectListUrlByRole(Map<String, String> param) throws Exception;
	
}
