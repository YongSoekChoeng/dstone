package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.RelationDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 영향도 분석: "이것을 고치면 어디까지 닿는가"를 한 번에 돌려줍니다.
 *
 * 대상(테이블 / SQL statement / 타입 / 메소드)을 직접 건드리는 메소드에서 출발해, 부르는 쪽으로 거슬러 올라가며
 * 닿는 메소드, 진입점(주소, 스케줄, main), 화면(JSP)을 모읍니다.
 *
 *   테이블 TB_ORDER ← SQL ← OrderDAO.cancel ← OrderService.cancel ← OrderController.cancel ← POST /order/cancel.do ← order/detail.jsp
 *
 * 결과마다 신뢰도가 붙습니다. 대상에서 거기까지 가는 길에서 가장 약한 관계의 신뢰도입니다.
 *   HIGH   확실한 관계만 따라왔다
 *   MEDIUM 중간에 덜 확실한 관계가 있다(타입만 알고 메소드를 이름으로 고른 호출, 화면 안의 주소 글자, 정규식으로 찾은 테이블)
 *   LOW    이름만 보고 짐작한 관계가 끼어 있다. 참고로만 본다
 * 답을 지어내지 않고 저장된 관계만 따라가므로, 분석이 잇지 못한 호출(리플렉션, 문자열로 조립한 주소 등)은 여기에도 나오지 않습니다.
 * </pre>
 */
@Service
public class ImpactService extends BaseObject {

	private static final int DEFAULT_DEPTH = 5;

	/** 호출 그래프 조회(최대 5)보다 깊게 허용한다. 화면에서 DAO까지는 보통 3~6단계다 */
	private static final int MAX_DEPTH = 10;

	/** 종류별로 돌려주는 최대 행 수 */
	private static final int MAX_ROWS = 2000;

	@Autowired
	private RelationDao relationDao;

	@Autowired
	private RevisionDao revisionDao;

	/**
	 * @param targetKind TABLE / STATEMENT / TYPE / METHOD
	 * @param target 테이블 이름 / statement 이름(네임스페이스.id 또는 id) / 타입 전체 이름 / 메소드 ID
	 * @param access 대상이 테이블일 때만: ALL(기본) / READ(읽는 SQL만) / WRITE(쓰는 SQL만)
	 * @param depth 몇 단계까지 거슬러 올라갈지(기본 5, 최대 10)
	 * @param includePossible 인터페이스를 통해 부르는 호출("가능한 구현")도 따라갈지. 영향을 빠뜨리지 않으려면 켠다
	 */
	public Map<String, Object> getImpact(long revisionId, String targetKind, String target, String access, Integer depth, boolean includePossible) {
		if (revisionDao.selectRevision(revisionId) == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		String name = target.trim();
		if ("TABLE".equals(targetKind)) {
			// 테이블 이름은 대문자로 맞춰 저장한다(SqlTableExtractor).
			name = name.toUpperCase();
		}
		int walkDepth = depth == null ? DEFAULT_DEPTH : Math.max(0, Math.min(depth.intValue(), MAX_DEPTH));

		List<String> relationTypes = new ArrayList<String>();
		relationTypes.add("CALLS");
		// 화면(JSP)이 그 메소드의 주소를 요청하는 것도 "부르는 쪽"이다.
		relationTypes.add("REQUESTS");
		if (includePossible) {
			relationTypes.add("CALLS_POSSIBLE_IMPLEMENTATION");
		}

		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", revisionId);
		condition.put("targetKind", targetKind);
		condition.put("target", name);
		condition.put("tableRelationTypes", tableRelationTypes(access));
		condition.put("relationTypes", relationTypes);
		condition.put("depth", Integer.valueOf(walkDepth));
		condition.put("limit", Integer.valueOf(MAX_ROWS));

		List<Map<String, Object>> methods = relationDao.selectImpactMethods(condition);
		if (methods.isEmpty()) {
			throw ApiException.notFound(notFoundMessage(targetKind, name));
		}
		List<Map<String, Object>> endpoints = relationDao.selectImpactEndpoints(condition);
		List<Map<String, Object>> screens = relationDao.selectImpactScreens(condition);

		int startMethods = 0;
		for (int i = 0; i < methods.size(); i++) {
			if (((Number) methods.get(i).get("depth")).intValue() == 0) {
				startMethods++;
			}
		}

		Map<String, Object> targetInfo = new LinkedHashMap<String, Object>();
		targetInfo.put("kind", targetKind);
		targetInfo.put("name", name);
		if ("TABLE".equals(targetKind)) {
			targetInfo.put("access", access == null ? "ALL" : access.toUpperCase());
		}

		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		// 대상을 직접 건드리는 메소드 수
		summary.put("startMethods", Integer.valueOf(startMethods));
		summary.put("methods", Integer.valueOf(methods.size()));
		summary.put("endpoints", Integer.valueOf(endpoints.size()));
		summary.put("screens", Integer.valueOf(screens.size()));
		summary.put("endpointsByConfidence", countBy(endpoints, "confidence"));
		summary.put("methodsByLayer", countBy(methods, "layer"));

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", revisionId);
		result.put("target", targetInfo);
		result.put("depth", Integer.valueOf(walkDepth));
		result.put("includePossible", Boolean.valueOf(includePossible));
		result.put("summary", summary);
		result.put("truncated", Boolean.valueOf(methods.size() >= MAX_ROWS || endpoints.size() >= MAX_ROWS || screens.size() >= MAX_ROWS));
		result.put("endpoints", endpoints);
		result.put("screens", screens);
		result.put("methods", methods);
		return result;
	}

	private List<String> tableRelationTypes(String access) {
		List<String> types = new ArrayList<String>();
		String upper = access == null || access.trim().length() == 0 ? "ALL" : access.trim().toUpperCase();
		if (!"ALL".equals(upper) && !"READ".equals(upper) && !"WRITE".equals(upper)) {
			throw ApiException.badRequest("access는 ALL / READ / WRITE 가운데 하나여야 합니다: " + access);
		}
		if (!"WRITE".equals(upper)) {
			types.add("READS_TABLE");
		}
		if (!"READ".equals(upper)) {
			types.add("WRITES_TABLE");
		}
		return types;
	}

	private String notFoundMessage(String targetKind, String name) {
		if ("TABLE".equals(targetKind)) {
			return "그 테이블을 건드리는 SQL을 실행하는 메소드가 없습니다: " + name + " (테이블 목록: /tables)";
		}
		if ("STATEMENT".equals(targetKind)) {
			return "그 SQL statement를 실행하는 메소드가 없습니다: " + name + " (statement 목록: /mappers)";
		}
		if ("TYPE".equals(targetKind)) {
			return "없는 타입이거나 메소드가 없는 타입입니다: " + name + " (타입 전체 이름으로 적습니다. 목록: /types)";
		}
		return "없는 메소드입니다: " + name + " (메소드 ID로 적습니다. 찾기: /methods)";
	}

	/** 그 키의 값별 건수. 값이 없는 행은 "NONE"으로 센다 */
	private Map<String, Object> countBy(List<Map<String, Object>> rows, String key) {
		Map<String, Object> counts = new LinkedHashMap<String, Object>();
		for (int i = 0; i < rows.size(); i++) {
			Object value = rows.get(i).get(key);
			String name = value == null ? "NONE" : String.valueOf(value);
			Number count = (Number) counts.get(name);
			counts.put(name, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
		}
		return counts;
	}

}
