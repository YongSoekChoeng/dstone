package net.dstone.batchadmin.main;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.batchadmin.job.BatchJobService;
import net.dstone.batchadmin.job.vo.BatchJobVo;
import net.dstone.batchadmin.server.BatchServerService;
import net.dstone.batchadmin.server.vo.BatchServerVo;

/**
 * 메인화면(대시보드).
 */
@Controller
@RequestMapping(value = "/main/*")
public class MainController extends net.dstone.batchadmin.common.biz.BaseController {

	@Autowired
	private BatchServerService batchServerService;

	@Autowired
	private BatchJobService batchJobService;

	/**
	 * 메인화면의 요약정보(등록된 배치서버수/배치Job수)를 조회한다. main.jsp에서 화면로딩 후 AJAX로 호출.
	 */
	@RequestMapping(value = "/summary.do")
	public ModelAndView summary(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		Map<String, Object> summary = new HashMap<String, Object>();
		List<BatchServerVo> serverList = batchServerService.listServer(new BatchServerVo());
		List<BatchJobVo> jobList = batchJobService.listJob(new BatchJobVo());
		summary.put("SERVER_CNT", serverList == null ? 0 : serverList.size());
		summary.put("JOB_CNT", jobList == null ? 0 : jobList.size());
		mav.addObject("returnObj", summary);
		mav.addObject("successYn", "Y");
		return mav;
	}

}
