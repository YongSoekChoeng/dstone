package net.dstone.boot.sample.kafka.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.boot.common.biz.BaseController;
import net.dstone.boot.sample.kafka.service.KafkaService;
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
		
		Map<String, Object> param = new HashMap<String, Object>();
		param.put("orderId", requestUtil.getParameter("orderId"));
		param.put("orderName", requestUtil.getParameter("orderName"));
		param.put("orderItem", requestUtil.getParameter("orderItem"));
		param.put("orderCount", requestUtil.getParameter("orderCount"));
		
		kafkaService.publish("order-events", "1", param);
		
		return mav;
	}


}
