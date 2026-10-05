package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.DiffDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 리비전 비교: 두 리비전 사이에 무엇이 달라졌는지를 분석 결과의 말로 돌려줍니다.
 *
 * 소스의 줄 단위 차이(git diff)가 아닙니다. "어느 메소드가 생기고 없어졌는지, 어느 주소가 새로 열렸는지,
 * 어느 SQL이 다른 테이블을 건드리게 됐는지, 누가 누구를 새로 부르게 됐는지"를 봅니다.
 *
 *   FILE       파일          생김 / 없어짐 / 내용이 바뀜
 *   TYPE       타입          생김 / 없어짐
 *   METHOD     메소드        생김 / 없어짐 / 반환 타입이나 제한자가 바뀜 (몸통만 바뀐 것은 FILE의 "바뀜"으로 본다)
 *   ENDPOINT   진입점        생김 / 없어짐 (주소나 처리 메소드가 바뀌면 없어짐 + 생김으로 나온다)
 *   STATEMENT  SQL statement 생김 / 없어짐 / SQL이 바뀜
 *   TABLE_USE  테이블 사용   어느 statement가 어느 테이블을 읽거나 쓰게 됐는지 / 더는 안 쓰는지
 *   CALL       호출          프로젝트 안의 메소드 → 메소드 호출이 생김 / 없어짐
 *
 * 두 리비전은 같은 프로젝트의 것이어야 합니다. 타입과 메소드를 가리는 ID에 프로젝트가 들어 있어서,
 * 다른 프로젝트끼리는 같은 소스라도 전부 다른 것으로 나옵니다.
 * </pre>
 */
@Service
public class DiffService extends BaseObject {

	private static final String[] KINDS = { "FILE", "TYPE", "METHOD", "ENDPOINT", "STATEMENT", "TABLE_USE", "CALL" };

	private static final int DEFAULT_LIMIT = 200;

	private static final int MAX_LIMIT = 2000;

	@Autowired
	private DiffDao diffDao;

	@Autowired
	private RevisionDao revisionDao;

	/**
	 * @param baseRevisionId 기준(앞) 리비전. 없으면 증분 분석의 기준 리비전, 그것도 없으면 같은 프로젝트의 바로 앞 리비전
	 * @param kinds 볼 종류. 비어 있으면 전부
	 * @param limit 종류마다 돌려줄 최대 건수(기본 200, 최대 2000). 건수(summary)는 언제나 전체를 센다
	 */
	public Map<String, Object> getDiff(long revisionId, Long baseRevisionId, List<String> kinds, Integer limit) {
		Map<String, Object> revision = revisionDao.selectRevision(revisionId);
		if (revision == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		long baseId = resolveBase(revision, revisionId, baseRevisionId);
		Map<String, Object> base = revisionDao.selectRevision(baseId);
		if (base == null) {
			throw ApiException.notFound("없는 기준 리비전입니다: " + baseId);
		}
		if (!String.valueOf(revision.get("projectId")).equals(String.valueOf(base.get("projectId")))) {
			throw ApiException.badRequest("같은 프로젝트의 리비전끼리만 비교할 수 있습니다: " + revision.get("projectId") + " / " + base.get("projectId"));
		}
		if (baseId == revisionId) {
			throw ApiException.badRequest("자기 자신과는 비교할 수 없습니다.");
		}
		int rowLimit = limit == null ? DEFAULT_LIMIT : Math.max(0, Math.min(limit.intValue(), MAX_LIMIT));
		List<String> targetKinds = kindsOf(kinds);

		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		Map<String, Object> changes = new LinkedHashMap<String, Object>();
		boolean truncated = false;
		for (int i = 0; i < targetKinds.size(); i++) {
			String kind = targetKinds.get(i);
			Map<String, Object> counts = new LinkedHashMap<String, Object>();
			counts.put("ADDED", Integer.valueOf(0));
			counts.put("REMOVED", Integer.valueOf(0));
			counts.put("CHANGED", Integer.valueOf(0));
			int total = 0;
			List<Map<String, Object>> countRows = diffDao.selectDiffCounts(revisionId, baseId, kind);
			for (int c = 0; c < countRows.size(); c++) {
				int count = ((Number) countRows.get(c).get("count")).intValue();
				counts.put(String.valueOf(countRows.get(c).get("change")), Integer.valueOf(count));
				total += count;
			}
			summary.put(kind, counts);
			if (rowLimit > 0 && total > 0) {
				changes.put(kind, diffDao.selectDiffRows(revisionId, baseId, kind, rowLimit));
			} else {
				changes.put(kind, new ArrayList<Map<String, Object>>());
			}
			if (total > rowLimit) {
				truncated = true;
			}
		}

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revision", brief(revision));
		result.put("base", brief(base));
		result.put("summary", summary);
		// 종류마다 limit 건까지만 담았는지. 건수(summary)는 전체다
		result.put("truncated", Boolean.valueOf(truncated));
		result.put("changes", changes);
		return result;
	}

	private long resolveBase(Map<String, Object> revision, long revisionId, Long baseRevisionId) {
		if (baseRevisionId != null) {
			return baseRevisionId.longValue();
		}
		Object parent = revision.get("parentRevisionId");
		if (parent != null && revisionDao.selectRevision(((Number) parent).longValue()) != null) {
			return ((Number) parent).longValue();
		}
		Long previous = diffDao.selectPreviousReadyRevision(revisionId);
		if (previous == null) {
			throw ApiException.badRequest("비교할 앞 리비전이 없습니다. base로 기준 리비전을 지정하세요.");
		}
		return previous.longValue();
	}

	private List<String> kindsOf(List<String> kinds) {
		List<String> result = new ArrayList<String>();
		if (kinds == null || kinds.isEmpty()) {
			for (int i = 0; i < KINDS.length; i++) {
				result.add(KINDS[i]);
			}
			return result;
		}
		for (int i = 0; i < kinds.size(); i++) {
			String kind = kinds.get(i).trim().toUpperCase();
			boolean known = false;
			for (int k = 0; k < KINDS.length; k++) {
				known = known || KINDS[k].equals(kind);
			}
			if (!known) {
				throw ApiException.badRequest("없는 종류입니다: " + kinds.get(i) + " (FILE / TYPE / METHOD / ENDPOINT / STATEMENT / TABLE_USE / CALL)");
			}
			if (!result.contains(kind)) {
				result.add(kind);
			}
		}
		return result;
	}

	private Map<String, Object> brief(Map<String, Object> revision) {
		Map<String, Object> brief = new LinkedHashMap<String, Object>();
		brief.put("revisionId", revision.get("revisionId"));
		brief.put("revisionLabel", revision.get("revisionLabel"));
		brief.put("status", revision.get("status"));
		brief.put("createdAt", revision.get("createdAt"));
		return brief;
	}

}
