package net.dstone.knowledge.api.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.dstone.knowledge.api.service.AnalysisService;

/**
 * 분석 Job API입니다. 시작은 ProjectController(POST /api/projects/{projectId}/analyses)에 있고,
 * 여기서는 시작한 Job의 상태 조회와 취소를 다룹니다.
 */
@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

	@Autowired
	private AnalysisService analysisService;

	/** Job 상태 조회: 상태, 현재 단계, 진행 건수, 단계별 진행 상태, 오류 일부 */
	@GetMapping("/{analysisId}")
	public Map<String, Object> getAnalysis(@PathVariable("analysisId") String analysisId) {
		return analysisService.getAnalysis(analysisId);
	}

	/** Job 취소 요청. 실제로 멈추기까지 조금 걸릴 수 있습니다. */
	@PostMapping("/{analysisId}/cancel")
	public Map<String, Object> cancelAnalysis(@PathVariable("analysisId") String analysisId) {
		return analysisService.cancelAnalysis(analysisId);
	}

}
