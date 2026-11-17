package net.dstone.knowledge.api.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.DashboardDao;
import net.dstone.knowledge.api.dao.StructureDao;
import net.dstone.knowledge.rag.RagSettings;

/**
 * <pre>
 * 대시보드(웰컴 화면)에 필요한 숫자를 한 번에 모읍니다.
 *
 * 화면이 프로젝트마다 리비전 목록·요약을 따로 부르면 프로젝트가 늘수록 호출이 늘어서,
 * 여기서 SQL 몇 번으로 모아 한 응답으로 돌려줍니다. 읽기만 하고 아무것도 계산해서 저장하지 않습니다.
 * </pre>
 */
@Service
public class DashboardService extends BaseObject {

	/** 최근 분석 작업을 몇 건 보여 줄지 */
	private static final int RECENT_JOBS = 10;

	/** 최근 올린 문서를 몇 건 보여 줄지 */
	private static final int RECENT_DOCUMENTS = 5;

	@Autowired
	private DashboardDao dashboardDao;

	@Autowired
	private StructureDao structureDao;

	@Autowired
	private EmbeddingStatusService embeddingStatusService;

	@Autowired
	private UploadDocumentService uploadDocumentService;

	@Autowired
	private RagSettings ragSettings;

	/**
	 * <pre>
	 * 대시보드 한 화면 분량을 돌려줍니다.
	 *   totals     프로젝트 수, 분석이 끝난 리비전 수, 진행 중인 분석 수, 최근 7일 실패 수
	 *   projects   프로젝트마다 최근 리비전과 분석이 끝난 최근 리비전(건수, 호출 해석률,
	 *              계층별 개수: 화면 → 컨트롤러 → 서비스 → DAO → SQL 매퍼 · SQL 문 → 테이블)
	 *   recentJobs 최근 분석 작업
	 *   documents  올린 일반 문서: 합계, 묶음별 현황, 최근 올린 문서 (호출자 것만)
	 *   embedding  임베딩 대기열 현황(/api/system/embedding 과 같은 내용)
	 * </pre>
	 *
	 * @param tenant 호출자. 올린 문서는 호출자별로 나뉘어 있어서 그 사람 것만 셉니다.
	 */
	public Map<String, Object> getDashboard(String tenant) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("generatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
		result.put("totals", dashboardDao.selectTotals());
		result.put("projects", projects());
		result.put("recentJobs", dashboardDao.selectRecentJobs(RECENT_JOBS));
		result.put("documents", documents(tenant));
		result.put("embedding", embedding());
		return result;
	}

	/**
	 * <pre>
	 * 프로젝트마다 한 줄. 분석이 끝난 리비전이 있으면 계층별 개수(screens, controllers, services, daos, mappers, sqlStatements, tables)를 붙인다.
	 * 계층별 개수는 프로젝트 화면의 "현행 구조"와 같은 SQL(StructureDao.selectLayerCounts)이라 두 화면의 숫자가 늘 같다.
	 * </pre>
	 */
	private List<Map<String, Object>> projects() {
		List<Map<String, Object>> projects = dashboardDao.selectProjects();
		for (int i = 0; i < projects.size(); i++) {
			Object ready = projects.get(i).get("readyRevisionId");
			if (ready instanceof Number) {
				Map<String, Object> layers = structureDao.selectLayerCounts(((Number) ready).longValue());
				if (layers != null) {
					projects.get(i).putAll(layers);
				}
			}
		}
		return projects;
	}

	/** 올린 문서 현황: 묶음별 줄을 더한 합계 + 묶음별 줄 + 최근 올린 문서 몇 건 */
	private Map<String, Object> documents(String tenant) {
		List<Map<String, Object>> collections = dashboardDao.selectDocumentCollections(tenant, ragSettings.embeddingModel());
		long documents = 0;
		long chunks = 0;
		long embedded = 0;
		long failed = 0;
		for (int i = 0; i < collections.size(); i++) {
			Map<String, Object> row = collections.get(i);
			documents += longOf(row.get("documents"));
			chunks += longOf(row.get("chunks"));
			embedded += longOf(row.get("embedded"));
			failed += longOf(row.get("failed"));
		}
		Map<String, Object> totals = new LinkedHashMap<String, Object>();
		totals.put("collections", Integer.valueOf(collections.size()));
		totals.put("documents", Long.valueOf(documents));
		totals.put("chunks", Long.valueOf(chunks));
		totals.put("embedded", Long.valueOf(embedded));
		totals.put("failed", Long.valueOf(failed));

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("totals", totals);
		result.put("collections", collections);
		result.put("recent", uploadDocumentService.getDocumentList(tenant, null, 1, RECENT_DOCUMENTS).get("documents"));
		return result;
	}

	/**
	 * <pre>
	 * 임베딩 현황. 여기서 실패해도 대시보드의 나머지는 보여 줘야 하므로,
	 * 오류는 응답의 error 에 담아 돌려주고 예외를 밖으로 던지지 않습니다.
	 * </pre>
	 */
	private Map<String, Object> embedding() {
		try {
			return embeddingStatusService.getStatus();
		} catch (RuntimeException e) {
			warn("대시보드: 임베딩 현황을 읽지 못했습니다 - " + e.getMessage());
			Map<String, Object> failure = new LinkedHashMap<String, Object>();
			failure.put("error", String.valueOf(e.getMessage()));
			return failure;
		}
	}

	private long longOf(Object value) {
		return value instanceof Number ? ((Number) value).longValue() : 0L;
	}

}
