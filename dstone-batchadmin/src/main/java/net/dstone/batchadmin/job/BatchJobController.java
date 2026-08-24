package net.dstone.batchadmin.job;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.dstone.batchadmin.job.vo.BatchJobExecVo;
import net.dstone.batchadmin.job.vo.BatchJobVo;
import net.dstone.common.utils.RequestUtil;
import net.dstone.common.utils.StringUtil;

/**
 * 배치JOB 목록조회/상세조회/등록 화면.
 */
@Controller
@RequestMapping(value = "/job/*")
public class BatchJobController extends net.dstone.batchadmin.common.biz.BaseController {

	@Autowired
	private BatchJobService batchJobService;

	/*** 화면 시작 ***/

	@RequestMapping(value = "/list.do")
	public ModelAndView list(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav.setViewName("job/list");
		return mav;
	}

	@RequestMapping(value = "/detail.do")
	public ModelAndView detail(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		RequestUtil requestUtil = new RequestUtil(request, response);
		mav.setViewName("job/detail");
		mav.addObject("SERVER_ID", requestUtil.getParameter("SERVER_ID", ""));
		mav.addObject("JOB_INSTANCE_ID", requestUtil.getParameter("JOB_INSTANCE_ID", ""));
		return mav;
	}

	@RequestMapping(value = "/register.do")
	public ModelAndView register(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav.setViewName("job/register");
		return mav;
	}

	/*** 화면 끝 ***/

	/*** 배치JOB 목록조회(BATCH_JOB_EXECUTION 직접조회, 페이징) 시작 ***/

	@RequestMapping(value = "/listJobExecution.do")
	public ModelAndView listJobExecution(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		BatchJobExecVo paramVo = (BatchJobExecVo) bindSingleValue(requestUtil, new BatchJobExecVo());
		if (StringUtil.isEmpty(requestUtil.getParameter("PAGE_NUM", ""))) {
			paramVo.setPAGE_NUM(1);
		} else {
			paramVo.setPAGE_NUM(requestUtil.getIntParameter("PAGE_NUM"));
		}
		Map<String, Object> returnObj = batchJobService.listJobExecution(paramVo);
		mav.addObject("returnObj", returnObj);
		mav.addObject("successYn", "Y");
		return mav;
	}

	@RequestMapping(value = "/executionHistory.do")
	public ModelAndView executionHistory(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		Long jobInstanceId = Long.valueOf(requestUtil.getParameter("JOB_INSTANCE_ID"));
		List<BatchJobExecVo> list = batchJobService.listJobExecutionByInstance(serverId, jobInstanceId);
		mav.addObject("returnObj", list);
		mav.addObject("successYn", "Y");
		return mav;
	}

	@RequestMapping(value = "/stepExecution.do")
	public ModelAndView stepExecution(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		Long jobExecutionId = Long.valueOf(requestUtil.getParameter("JOB_EXECUTION_ID"));
		mav.addObject("returnObj", batchJobService.listStepExecution(serverId, jobExecutionId));
		mav.addObject("successYn", "Y");
		return mav;
	}

	@RequestMapping(value = "/executionParams.do")
	public ModelAndView executionParams(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		Long jobExecutionId = Long.valueOf(requestUtil.getParameter("JOB_EXECUTION_ID"));
		mav.addObject("returnObj", batchJobService.listExecutionParams(serverId, jobExecutionId));
		mav.addObject("successYn", "Y");
		return mav;
	}

	/*** 배치JOB 목록/상세조회 끝 ***/

	/*** 배치JOB 메타데이터(TB_BATCH_JOB) 등록관리 시작 ***/

	@RequestMapping(value = "/listJob.do")
	public ModelAndView listJob(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		BatchJobVo paramVo = (BatchJobVo) bindSingleValue(requestUtil, new BatchJobVo());
		mav.addObject("returnObj", batchJobService.listJob(paramVo));
		mav.addObject("successYn", "Y");
		return mav;
	}

	@RequestMapping(value = "/saveJob.do")
	public ModelAndView saveJob(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		BatchJobVo paramVo = (BatchJobVo) bindSingleValue(requestUtil, new BatchJobVo());
		batchJobService.saveJob(paramVo);
		mav.addObject("successYn", "Y");
		return mav;
	}

	@RequestMapping(value = "/deleteJobMeta.do")
	public ModelAndView deleteJobMeta(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long jobId = Long.valueOf(requestUtil.getParameter("JOB_ID"));
		batchJobService.deleteJob(jobId);
		mav.addObject("successYn", "Y");
		return mav;
	}

	/**
	 * 대상 배치서버에 현재 등록되어있는(JobRegistry) Job명 목록 조회 - 등록화면에서 참고용으로 사용.
	 */
	@RequestMapping(value = "/getRegisteredJobs.do")
	public ModelAndView getRegisteredJobs(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		mav.addObject("returnObj", batchJobService.getRegisteredJobs(serverId));
		mav.addObject("successYn", "Y");
		return mav;
	}

	/*** 배치JOB 메타데이터 등록관리 끝 ***/

	/*** 배치JOB 제어(시작/중지/재시작/폐기/삭제) 시작 ***/

	@RequestMapping(value = "/startJob.do")
	public ModelAndView startJob(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		String jobNm = requestUtil.getParameter("JOB_NAME");
		mav.addObject("returnObj", batchJobService.startJob(serverId, jobNm));
		mav.addObject("successYn", "Y");
		return mav;
	}

	@RequestMapping(value = "/stopJob.do")
	public ModelAndView stopJob(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		Long jobExecutionId = Long.valueOf(requestUtil.getParameter("JOB_EXECUTION_ID"));
		mav.addObject("returnObj", batchJobService.stopJob(serverId, jobExecutionId));
		mav.addObject("successYn", "Y");
		return mav;
	}

	@RequestMapping(value = "/restartJob.do")
	public ModelAndView restartJob(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		Long jobExecutionId = Long.valueOf(requestUtil.getParameter("JOB_EXECUTION_ID"));
		mav.addObject("returnObj", batchJobService.restartJob(serverId, jobExecutionId));
		mav.addObject("successYn", "Y");
		return mav;
	}

	@RequestMapping(value = "/abandonJob.do")
	public ModelAndView abandonJob(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		Long jobExecutionId = Long.valueOf(requestUtil.getParameter("JOB_EXECUTION_ID"));
		mav.addObject("returnObj", batchJobService.abandonJob(serverId, jobExecutionId));
		mav.addObject("successYn", "Y");
		return mav;
	}

	@RequestMapping(value = "/deleteJobExecution.do")
	public ModelAndView deleteJobExecution(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		Long jobExecutionId = Long.valueOf(requestUtil.getParameter("JOB_EXECUTION_ID"));
		mav.addObject("returnObj", batchJobService.deleteJobExecution(serverId, jobExecutionId));
		mav.addObject("successYn", "Y");
		return mav;
	}

	@RequestMapping(value = "/deleteJobInstance.do")
	public ModelAndView deleteJobInstance(HttpServletRequest request, HttpServletResponse response, ModelAndView mav) throws Exception {
		mav = new ModelAndView("jsonView");
		RequestUtil requestUtil = new RequestUtil(request, response);
		Long serverId = Long.valueOf(requestUtil.getParameter("SERVER_ID"));
		Long jobInstanceId = Long.valueOf(requestUtil.getParameter("JOB_INSTANCE_ID"));
		mav.addObject("returnObj", batchJobService.deleteJobInstance(serverId, jobInstanceId));
		mav.addObject("successYn", "Y");
		return mav;
	}

	/*** 배치JOB 제어 끝 ***/

}
