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
import net.dstone.knowledge.common.util.TableName;

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

	@Autowired
	private TableDictionaryService tableDictionaryService;

	/**
	 * <pre>
	 * 대상(테이블, SQL, 타입, 메소드)을 바꾸면 영향을 받는 메소드와 진입점을 호출 관계를 거슬러 올라가며 찾습니다.
	 * </pre>
	 *
	 * @param targetKind TABLE / STATEMENT / TYPE / METHOD
	 * @param target 테이블 이름 / statement 이름(네임스페이스.id 또는 id) / 타입 전체 이름 / 메소드 ID
	 * @param access 대상이 테이블일 때만: ALL(기본) / READ(읽는 SQL만) / WRITE(쓰는 SQL만)
	 * @param depth 몇 단계까지 거슬러 올라갈지(기본 5, 최대 10)
	 * @param includePossible 인터페이스를 통해 부르는 호출("가능한 구현")도 따라갈지. 영향을 빠뜨리지 않으려면 켠다
	 */
	public Map<String, Object> getImpact(long revisionId, String targetKind, String target, String access, Integer depth, boolean includePossible) {
		Map<String, Object> revision = revisionDao.selectRevision(revisionId);
		if (revision == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		// 1) 대상 이름과 깊이 다듬기
		String name = target.trim();
		if ("TABLE".equals(targetKind)) {
			// 테이블 이름은 스키마를 떼고 대문자로 맞춰 저장한다(SqlTableExtractor).
			name = TableName.bare(name);
		}
		int walkDepth = depth == null ? DEFAULT_DEPTH : Math.max(0, Math.min(depth.intValue(), MAX_DEPTH));

		// 2) 거슬러 올라갈 관계: 호출, 화면의 요청, (켜면) 가능한 구현, 익명 클래스를 감싸는 바깥 메소드
		List<String> relationTypes = new ArrayList<String>();
		relationTypes.add("CALLS");
		// 화면(JSP)이 그 메소드의 주소를 요청하는 것도 "부르는 쪽"이다.
		relationTypes.add("REQUESTS");
		if (includePossible) {
			relationTypes.add("CALLS_POSSIBLE_IMPLEMENTATION");
		}
		// new Runnable() { run() { 대상(); } } 처럼 익명 클래스 안에서 부르면, 익명 클래스 메소드를 감싸는 바깥 메소드로 올라간다.
		relationTypes.add("ENCLOSES_ANONYMOUS");

		Map<String, Object> condition = new HashMap<String, Object>();
		condition.put("revisionId", revisionId);
		condition.put("targetKind", targetKind);
		condition.put("target", name);
		condition.put("tableRelationTypes", tableRelationTypes(access));
		condition.put("relationTypes", relationTypes);
		condition.put("depth", Integer.valueOf(walkDepth));
		condition.put("limit", Integer.valueOf(MAX_ROWS));

		// 3) 닿는 메소드 → 그 메소드의 진입점과 화면
		List<Map<String, Object>> methods = relationDao.selectImpactMethods(condition);
		if (methods.isEmpty()) {
			throw ApiException.notFound(notFoundMessage(targetKind, name));
		}
		List<Map<String, Object>> endpoints = relationDao.selectImpactEndpoints(condition);
		List<Map<String, Object>> screens = relationDao.selectImpactScreens(condition);

		// 4) 요약과 응답
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
		putTargetDetail(targetInfo, condition, String.valueOf(revision.get("projectId")));

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

	/** access(ALL / READ / WRITE)에 맞는 테이블 관계 종류. 그 밖의 값이면 400 */
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

	/** 영향 받는 메소드가 없을 때의 안내(대상 종류마다 목록을 볼 수 있는 곳을 함께 알려 줌) */
	/**
	 * <pre>
	 * 화면이 "무엇에 대한 영향도인지" 보여 줄 수 있게 대상의 설명을 붙입니다. 종류마다 붙는 항목이 다릅니다.
	 *   METHOD    ownerFqn, signature, layer, path, lineStart, title(메소드 위 주석의 기능명)
	 *   TYPE      simpleName, typeKind, layer, path, lineStart
	 *   STATEMENT statement(네임스페이스.id), statementType, path, lineStart, matches(이름이 같은 statement 수)
	 *   TABLE     statements(건드리는 statement 수), koreanName / koreanNameSource(테이블 한글명과 출처)
	 * 설명을 못 읽어도 영향도 결과는 그대로 돌려줍니다.
	 * </pre>
	 */
	private void putTargetDetail(Map<String, Object> targetInfo, Map<String, Object> condition, String projectId) {
		Map<String, Object> detail = relationDao.selectImpactTarget(condition);
		if (detail != null) {
			targetInfo.putAll(detail);
		}
		if ("TABLE".equals(condition.get("targetKind"))) {
			try {
				Map<String, Object> dictionary = tableDictionaryService.describe(((Long) condition.get("revisionId")).longValue(), projectId
						, (String) condition.get("target"));
				targetInfo.put("koreanName", dictionary.get("koreanName"));
				targetInfo.put("koreanNameSource", dictionary.get("koreanNameSource"));
			} catch (RuntimeException e) {
				// 한글명은 덤이다. 못 찾아도(대상 DB에 못 붙는 등) 영향도는 보여 준다.
				warn("테이블 한글명을 읽지 못했습니다: " + condition.get("target") + " - " + e.getMessage());
			}
		}
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
