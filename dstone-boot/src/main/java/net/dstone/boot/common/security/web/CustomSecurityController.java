package net.dstone.boot.common.security.web;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.boot.common.config.ConfigSecurity;
import net.dstone.boot.common.security.svc.CustomUserService;
import net.dstone.boot.common.security.vo.CustomUserDetails;
import net.dstone.boot.common.web.SessionListener;
import net.dstone.boot.common.biz.BaseController;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.consts.ErrCd;
import net.dstone.common.exception.SecException;

@Controller
@RequestMapping(value = "/com/login")
public class CustomSecurityController extends BaseController {

    /********* SVC 정의부분 시작 *********/ 
	@Resource(name = "customUserService")
    private CustomUserService customUserService; 
    /********* SVC 정의부분 끝 *********/

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	/**
	 * 사용자가 로그인된 상태인지 체크하는 액션
	 * @param request
	 * @param response
	 * @param mav
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/loginCheck.do")
	public ModelAndView loginCheck(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{
		if(isAjax(request)) { 
			mav = new ModelAndView("jsonView"); 
		}
		Map<String, Object> dmIsLogin = new HashMap<String, Object>();
		if( request.getSession().getAttribute(SessionListener.USER_LOGIN_SESSION_KEY) != null ) {
			dmIsLogin.put("isLogin", "Y");
		}else {
			dmIsLogin.put("isLogin", "N");
		}
		getLogger().info("dmIsLogin ::: ["+dmIsLogin+"]");
		mav.addObject("dmIsLogin", dmIsLogin);
		return mav;
	}

	/**
	 * 로그인 가기 액션(통과 후 로그인 페이지에 도달)
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/loginGo.do")
	public ModelAndView loginGo(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{
		if(isAjax(request)) { 
			mav = new ModelAndView("jsonView");
			this.setForcedToUrl(response, ConfigSecurity.LOGIN_PAGE);
		}else {
			mav.setViewName(ConfigSecurity.LOGIN_PAGE);
		}
		return mav;
	}

	/**
	 * 로그인 처리 액션
	 * @param dataRequest
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/loginProcess.do")
	public ModelAndView loginProcess(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{
		return mav;
	}

	/**
	 * 로그인 처리 성공시 진행될 액션
	 * @param dataRequest
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/loginProcessSuccess.do")
	public ModelAndView loginProcessSuccess(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{
		if(isAjax(request)) { 
			mav = new ModelAndView("jsonView"); 
		}else {
			mav.setViewName(ConfigSecurity.MAIN_PAGE);
		}
		Map<String, String> loginUpdate = new HashMap<String, String>();
		CustomUserDetails userDetails = (CustomUserDetails)SecurityContextHolder.getContext().getAuthentication().getDetails();
		loginUpdate.put( ConfigSecurity.USERNAME_PARAMETER, userDetails.getUsername() );
		customUserService.updateUserLoginTime(loginUpdate);	
		mav.addObject("successYn", "Y");
		return mav;
	}

	/**
	 * 로그인 처리 실패시 진행될 액션
	 * @param dataRequest
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/loginProcessFailure.do")
	public void loginProcessFailure(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{
		SecException secException = null;
		if( request.getAttribute("SecException") != null) {
			secException = (SecException)request.getAttribute("SecException");
		}else {
        	secException = new SecException(ErrCd.SYS_ERR);
        }
		throw secException;
	}

	/**
	 * 로그아웃 처리 액션
	 * @param dataRequest
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping(value = "/logout.do")
	public ModelAndView logout(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{
		request.getSession().invalidate();
		return mav;
	}

	/**
	 * 로그아웃 처리 성공시 진행될 액션
	 * @param dataRequest
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping(value = "/logoutSuccess.do")
	public ModelAndView logoutSuccess(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{
		if(isAjax(request)) { 
			mav = new ModelAndView("jsonView"); 
		}else {
			mav.setViewName(ConfigSecurity.LOGIN_PAGE);
		}
		return mav;
	}

	/**
	 * 접근권한이 없을 시 진행될 액션
	 * @param request
	 * @param response
	 * @param model
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/accessDenied.do")
	public void accessDenied(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{
		throw new SecException(ErrCd.ACCESS_DENIED);
	}

}
