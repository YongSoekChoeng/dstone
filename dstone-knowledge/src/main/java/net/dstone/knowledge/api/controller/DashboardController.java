package net.dstone.knowledge.api.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import net.dstone.knowledge.api.service.DashboardService;
import net.dstone.knowledge.common.security.CallerContext;

/**
 * <pre>
 * 대시보드(웰컴 화면) API입니다. 화면 하나에 필요한 숫자를 한 번에 돌려줍니다.
 * </pre>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	@Autowired
	private DashboardService dashboardService;

	/**
	 * <pre>
	 * 전체 현황: 건수 합계, 프로젝트별 최근 리비전, 최근 분석 작업, 올린 문서 묶음별 현황, 임베딩 현황.
	 * 올린 문서는 호출한 사람(X-API-Key)의 것만 셉니다.
	 * </pre>
	 */
	@GetMapping
	public Map<String, Object> getDashboard(HttpServletRequest request) {
		return dashboardService.getDashboard(CallerContext.get(request));
	}

}
