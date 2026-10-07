package net.dstone.boot.sample.google.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/google/auth")
@RequiredArgsConstructor
public class AuthController extends net.dstone.boot.common.biz.BaseController { 

    @RequestMapping("/token.do")
    public void initPage(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, ModelAndView mav) throws Exception{
    	

    	/************************ 변수 선언 시작 ************************/
   		net.dstone.common.utils.RequestUtil 					requestUtil;
   		/************************ 변수 선언 끝 **************************/

		/************************ 변수 정의 시작 ************************/
		requestUtil 			= new net.dstone.common.utils.RequestUtil(request, response);
		/************************ 변수 정의 끝 ************************/
		
		response.getWriter().print(requestUtil.getParameter("code"));
    }
    
}
