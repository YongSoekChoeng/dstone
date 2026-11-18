package net.dstone.ai.console.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.ai.console.service.AiOverviewService;
import net.dstone.ai.console.vo.WorkFlowOverviewResult;

/**
 * <pre>
 * AI 개요 화면(static/ai/index.html)이 부르는 API입니다.
 * 실행 현황(실행 중, 승인 대기 …)은 실행 관리 화면의 목록 API(/ai/admin/workflow/list.do)를 그대로 씁니다.
 * </pre>
 */
@RestController
@RequestMapping("/ai/overview/*")
public class AiOverviewController {

	@Autowired
	private AiOverviewService aiOverviewService;

	/** 등록된 Workflow마다 step 종류와 순서를 붙인 목록 */
	@GetMapping(value = "/workflows.do")
	public List<WorkFlowOverviewResult> workflows() {
		return this.aiOverviewService.workflows();
	}

	/** Workflow 하나의 step 종류와 순서(실행 상세의 step 흐름 띠) */
	@GetMapping(value = "/workflowSteps.do")
	public WorkFlowOverviewResult workflowSteps(@RequestParam String workflowId) {
		return this.aiOverviewService.workflowSteps(workflowId);
	}

}
