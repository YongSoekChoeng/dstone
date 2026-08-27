package net.dstone.boot.sample.kafka;

import java.util.Enumeration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.boot.common.biz.BaseController;
import net.dstone.common.utils.DataSet;
import net.dstone.common.utils.RequestUtil;

@Controller
@RequestMapping(value = "/kafka")
public class KafkaController extends BaseController {

	@Autowired 
	KafkaService kafkaService;

	@RequestMapping("/send.do")
	public ModelAndView doTestAjax(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception{
		RequestUtil requestUtil = new RequestUtil(request, response);
		if(isAjax(request)) { mav = new ModelAndView("jsonView"); }
		
		DataSet param = new DataSet();
		Enumeration<String> paramNames = requestUtil.getParameterNames();
		while( paramNames.hasMoreElements() ) {
			String paramKey = paramNames.nextElement();
			param.addDatum(paramKey, requestUtil.getParameter(paramKey));
		}
		kafkaService.publish("order-events", "1", param.toMap());
		
		return mav;
	}


}
