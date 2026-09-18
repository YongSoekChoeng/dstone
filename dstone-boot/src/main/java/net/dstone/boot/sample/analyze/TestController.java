package net.dstone.boot.sample.analyze;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller("analTestController")
@RequestMapping(value = "/analyze/test")
public class TestController extends BaseController {
	
	/**
	 * 테스트용 메서드
	 * @param request
	 * @param response
	 * @param mav
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/doTestService01.do")
	public ModelAndView doTestService01(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{

		String name = "용시기";
		getTestService().doTestService01(name); 
		
		return mav;
	}

}
