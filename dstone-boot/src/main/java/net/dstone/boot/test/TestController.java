package net.dstone.boot.test;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.boot.common.biz.BaseController;
import net.dstone.common.utils.RequestUtil;

@Controller
@RequestMapping(value = "/test")
public class TestController extends BaseController {

	@RequestMapping("/doTestAjax.do")
	public ModelAndView doTestAjax(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{
		RequestUtil requestUtil = new RequestUtil(request, response);
		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
		
		// 강제에러발생의 경우
		if( "Y".equals(requestUtil.getParameter("FORCE_EXCEPTION_YN")) ) {
			"".substring(5);
		}

		mav.addObject("TEST_NAME", "doTestAjax Test....");
		return mav;
	}

	@RequestMapping("/doTestSubmit.do")
	public ModelAndView doTestSubmit(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{
		RequestUtil requestUtil = new RequestUtil(request, response);
		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }

		// 강제에러발생의 경우
		if( "Y".equals(requestUtil.getParameter("FORCE_EXCEPTION_YN")) ) {
			"".substring(5);
		}

		mav.addObject("TEST_NAME", "doTestSubmit Test....");
		mav.setViewName("test/test");
		return mav;
	}

}
