package net.dstone.boot.ai.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.boot.ai.service.AiOverviewService;
import net.dstone.boot.ai.vo.WorkFlowOverviewResult;

/**
 * <pre>
 * AI 개요 화면(views/ai/index.jsp)이 부르는 API입니다.
 * 실행 현황(실행 중, 승인 대기 …)은 실행 관리 화면의 목록 API(/ai/admin/workflow/list.do)를 그대로 씁니다.
 * </pre>
 */
@RestController
@RequestMapping("/ai/overview/*")
public class AiOverviewController extends net.dstone.boot.common.biz.BaseController {

	@Autowired
	private AiOverviewService aiOverviewService;

	/** 등록된 Workflow마다 step 종류와 순서를 붙인 목록 */
	@GetMapping(value = "/workflows.do")
	public List<WorkFlowOverviewResult> workflows() {
		return this.aiOverviewService.workflows();
	}

}
