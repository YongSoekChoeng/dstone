package net.dstone.batchadmin.server;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.batchadmin.server.vo.BatchServerVo;
import net.dstone.common.utils.RequestUtil;

/**
 * 배치서버(TB_BATCH_SERVER) 등록/조회/수정/삭제 화면.
 */
@Controller
@RequestMapping(value = "/server/*")
public class BatchServerController extends net.dstone.batchadmin.common.biz.BaseController {

	@Autowired
	private BatchServerService batchServerService;

	/**
	 * 배치서버 관리 화면(목록+등록/수정 폼을 한 화면에서 처리).
	 */
	@RequestMapping(value = "/list.do")
	public ModelAndView list(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav.setViewName("server/list");
		return mav;
	}

	/**
	 * 배치서버 목록조회(AJAX)
	 */
	@RequestMapping(value = "/listServer.do")
	public ModelAndView listServer(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		BatchServerVo paramVo = (BatchServerVo) bindSingleValue(requestUtil, new BatchServerVo());
		List<BatchServerVo> list = batchServerService.listServer(paramVo);
		mav.addObject("returnObj", list);
		mav.addObject("successYn", "Y");
		return mav;
	}

	/**
	 * 배치서버 등록/수정(AJAX). SERVER_ID가 있으면 수정, 없으면 신규등록.
	 */
	@RequestMapping(value = "/saveServer.do")
	public ModelAndView saveServer(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		BatchServerVo paramVo = (BatchServerVo) bindSingleValue(requestUtil, new BatchServerVo());
		if (paramVo.getSERVER_ID() == null) {
			batchServerService.insertServer(paramVo);
		} else {
			batchServerService.updateServer(paramVo);
		}
		mav.addObject("successYn", "Y");
		return mav;
	}

	/**
	 * 배치서버 삭제(AJAX)
	 */
	@RequestMapping(value = "/deleteServer.do")
	public ModelAndView deleteServer(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		batchServerService.deleteServer(serverId);
		mav.addObject("successYn", "Y");
		return mav;
	}

	/**
	 * 배치서버 상태점검(dstone-batch RestApiRunner /healthCheck 호출, AJAX)
	 */
	@RequestMapping(value = "/healthCheck.do")
	public ModelAndView healthCheck(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		Map<String, Object> result = batchServerService.healthCheck(serverId);
		mav.addObject("returnObj", result);
		mav.addObject("successYn", "Y");
		return mav;
	}

}
