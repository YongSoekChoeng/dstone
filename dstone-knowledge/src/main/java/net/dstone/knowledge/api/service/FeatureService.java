package net.dstone.knowledge.api.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.dstone.common.core.BaseObject;
import net.dstone.knowledge.api.dao.FeatureDao;
import net.dstone.knowledge.api.dao.RevisionDao;
import net.dstone.knowledge.common.exception.ApiException;

/**
 * <pre>
 * 기능 한 묶음: 동작하는 기존 기능 하나를 화면부터 SQL까지 통째로 모아 돌려줍니다.
 * 새 기능을 설계할 때 "비슷한 기능 하나를 그대로 본떠" 만들게 하려는 것입니다.
 * 보내는 쪽(화면)과 받는 쪽(Controller)을 서로 다른 기능에서 가져오면 주고받는 형식이 어긋나는데,
 * 묶음은 저장된 관계를 따라 모으므로 한 기능의 것만 들어 있습니다.
 *
 * 시작(entry)은 화면 파일 경로나 주소입니다. 거기서 이렇게 따라갑니다.
 *   화면 → (REQUESTS) → 처리 메소드 → (CALLS ...) → 메소드 → (EXECUTES_SQL) → SQL
 *   메소드 → (RENDERS) → 화면(팝업 등)  → 그 화면의 요청도 따라간다(화면 단계 MAX_SCREEN_HOPS 까지)
 *   화면 ← (RENDERS) ← 그 화면을 여는 메소드
 * 주소로 시작하면 그 주소를 요청하는 화면에서 시작한 것과 같습니다.
 *
 * 따라가지 않는 것(목록에 이름만 남김):
 *   - 여러 화면이 함께 넣는 공통 스크립트 안의 요청(한 기능의 것이 아니다)
 *   - MODEL · UTIL 계층의 메소드(VO의 get/set, 공통 유틸). 공통 유틸은 shared에 이름과 위치만 적는다
 * 분석이 잇지 못한 호출 너머는 나오지 않습니다(지어내지 않는다).
 * </pre>
 */
@Service
public class FeatureService extends BaseObject {

	/** 화면 단계: 시작 화면이 0, 그 화면이 연 팝업이 1, 팝업이 연 팝업이 2 */
	private static final int MAX_SCREEN_HOPS = 2;

	/** 메소드를 따라 내려가는 최대 단계(처리 메소드가 1) */
	private static final int MAX_METHOD_DEPTH = 6;

	/** 결과가 한없이 커지지 않게 묶는 상한. 넘으면 truncated = true */
	private static final int MAX_SCREENS = 8;
	private static final int MAX_METHODS = 80;
	private static final int MAX_STATEMENTS = 40;
	private static final int MAX_SHARED = 40;

	/** 이 수보다 많은 화면이 넣는 스크립트는 공통 스크립트로 본다 */
	private static final int SHARED_SCRIPT_SCREENS = 2;

	/** 따라가지 않는 계층 */
	private static final Set<String> LEAF_LAYERS = new HashSet<String>();
	static {
		LEAF_LAYERS.add("MODEL");
		LEAF_LAYERS.add("UTIL");
	}

	@Autowired
	private FeatureDao featureDao;

	@Autowired
	private RevisionDao revisionDao;

	/** 따라가는 동안의 상태 */
	private static class Walk {
		final long revisionId;
		final Set<String> sharedScripts;
		/** 화면 id → 단계 */
		final Map<String, Integer> screenHop = new LinkedHashMap<String, Integer>();
		/** 화면 id → 들어온 까닭 */
		final Map<String, String> screenReason = new LinkedHashMap<String, String>();
		/** 화면 id → 그 화면이 넣은(공통이 아닌) 스크립트 */
		final Map<String, Set<String>> screenScripts = new LinkedHashMap<String, Set<String>>();
		/** 메소드 id → 단계(처리 메소드 1) */
		final Map<String, Integer> methodDepth = new LinkedHashMap<String, Integer>();
		/** 메소드 id → 화면 단계(그 메소드까지 오게 한 화면의 단계) */
		final Map<String, Integer> methodHop = new LinkedHashMap<String, Integer>();
		/** 처리 메소드 id → 요청한 화면 id */
		final Map<String, Set<String>> requestedBy = new LinkedHashMap<String, Set<String>>();
		final Set<String> handlers = new LinkedHashSet<String>();
		/** 묶음에 코드로 넣기로 한 메소드 */
		final Set<String> kept = new LinkedHashSet<String>();
		final Set<String> statements = new LinkedHashSet<String>();
		final Map<String, Map<String, Object>> methodInfo = new LinkedHashMap<String, Map<String, Object>>();
		final Map<String, Map<String, Object>> shared = new LinkedHashMap<String, Map<String, Object>>();
		final Set<String> models = new LinkedHashSet<String>();
		final List<String> notes = new ArrayList<String>();
		boolean truncated;

		Walk(long revisionId, Set<String> sharedScripts) {
			this.revisionId = revisionId;
			this.sharedScripts = sharedScripts;
		}
	}

	/**
	 * <pre>
	 * 기능 한 묶음을 돌려줍니다.
	 *   entries     시작한 것마다 {entry, kind: SCREEN | ENDPOINT | NOT_FOUND, matched}
	 *   screens     {path, hop, reason, scripts}
	 *   endpoints   {path, methodId, handler, requestedBy}
	 *   methods     {methodId, owner, layer, signature, path, lineStart, lineEnd, depth}
	 *   statements  {id, statement, statementType, path, lineStart, lineEnd, tables}
	 *   shared      따라가지 않은 공통 메소드 {owner, layer, signature, path, lineStart}
	 *   models      쓰는 VO 같은 MODEL 타입 이름
	 *   truncated, notes
	 * </pre>
	 *
	 * @param entries 화면 파일 경로(앞부분 생략 가능)나 주소(/로 시작)
	 */
	public Map<String, Object> getFeature(long revisionId, List<String> entries) {
		if (revisionDao.selectRevision(revisionId) == null) {
			throw ApiException.notFound("없는 리비전입니다: " + revisionId);
		}
		if (entries == null || entries.isEmpty()) {
			throw ApiException.badRequest("entry 를 하나 이상 주십시오(화면 파일 경로나 주소).");
		}
		Walk walk = new Walk(revisionId, new HashSet<String>(featureDao.selectSharedScripts(revisionId, SHARED_SCRIPT_SCREENS)));

		// 1) 시작점을 찾는다
		List<Map<String, Object>> entryRows = new ArrayList<Map<String, Object>>();
		Set<String> newScreens = new LinkedHashSet<String>();
		Set<String> newMethods = new LinkedHashSet<String>();
		for (int i = 0; i < entries.size(); i++) {
			String entry = clean(entries.get(i));
			if (entry.isEmpty()) {
				continue;
			}
			entryRows.add(this.resolveEntry(walk, entry, newScreens, newMethods));
		}

		// 2) 화면 → 처리 메소드 → 아래로, 팝업 화면이 나오면 다시 화면부터. 새로 닿은 것이 없을 때까지
		while (!newScreens.isEmpty() || !newMethods.isEmpty()) {
			Set<String> handlersFromScreens = this.expandScreens(walk, newScreens);
			newMethods.addAll(handlersFromScreens);
			newScreens = new LinkedHashSet<String>();
			newMethods = this.expandMethods(walk, newMethods, newScreens);
		}

		return this.result(walk, entryRows);
	}

	/* ======================= 시작점 ======================= */

	private Map<String, Object> resolveEntry(Walk walk, String entry, Set<String> newScreens, Set<String> newMethods) {
		Map<String, Object> row = new LinkedHashMap<String, Object>();
		row.put("entry", entry);
		if (entry.startsWith("/")) {
			String path = entry;
			int query = path.indexOf('?');
			if (query > 0) {
				path = path.substring(0, query);
			}
			List<Map<String, Object>> endpoints = featureDao.selectEndpointsByPath(walk.revisionId, path);
			if (!endpoints.isEmpty()) {
				List<String> methodIds = new ArrayList<String>();
				for (int i = 0; i < endpoints.size(); i++) {
					String methodId = String.valueOf(endpoints.get(i).get("methodId"));
					methodIds.add(methodId);
					this.addHandler(walk, methodId, 0, null, newMethods);
				}
				// 이 주소를 요청하는 화면도 이 기능의 것이다(공통 스크립트로만 이어진 화면은 뺀다)
				List<Map<String, Object>> screens = featureDao.selectRequestingScreens(walk.revisionId, methodIds);
				for (int i = 0; i < screens.size(); i++) {
					Map<String, Object> screen = screens.get(i);
					if (this.isSharedRequest(walk, screen)) {
						continue;
					}
					this.addScreen(walk, String.valueOf(screen.get("from")), 0, "주소 " + path + "를 요청하는 화면", newScreens);
				}
				row.put("kind", "ENDPOINT");
				row.put("matched", path);
				return row;
			}
		}
		String filePath = entry.startsWith("/") ? entry.substring(1) : entry;
		List<Map<String, Object>> files = featureDao.selectFilesByPath(walk.revisionId, filePath);
		if (!files.isEmpty()) {
			String screenId = "F" + files.get(0).get("fileId");
			this.addScreen(walk, screenId, 0, "시작 화면", newScreens);
			row.put("kind", "SCREEN");
			row.put("matched", files.get(0).get("path"));
			if (files.size() > 1) {
				walk.notes.add("'" + entry + "'에 맞는 파일이 여럿이라 가장 짧은 경로를 썼습니다: " + files.get(0).get("path"));
			}
			return row;
		}
		row.put("kind", "NOT_FOUND");
		row.put("matched", null);
		walk.notes.add("'" + entry + "'에 맞는 주소나 파일이 분석 결과에 없습니다.");
		return row;
	}

	/* ======================= 화면 ======================= */

	/** 새 화면들이 요청하는 처리 메소드와, 그 화면을 여는 메소드를 처리 메소드로 더하고 돌려줍니다. */
	private Set<String> expandScreens(Walk walk, Set<String> screens) {
		Set<String> added = new LinkedHashSet<String>();
		if (screens.isEmpty()) {
			return added;
		}
		List<Map<String, Object>> requests = featureDao.selectScreenRequests(walk.revisionId, screens);
		for (int i = 0; i < requests.size(); i++) {
			Map<String, Object> request = requests.get(i);
			if (this.isSharedRequest(walk, request)) {
				continue;
			}
			String screenId = String.valueOf(request.get("from"));
			String script = request.get("script") == null ? null : String.valueOf(request.get("script"));
			if (script != null && "SCRIPT_SRC".equals(request.get("via"))) {
				Set<String> scripts = walk.screenScripts.get(screenId);
				if (scripts == null) {
					scripts = new LinkedHashSet<String>();
					walk.screenScripts.put(screenId, scripts);
				}
				scripts.add(script);
			}
			this.addHandler(walk, String.valueOf(request.get("to")), hopOf(walk.screenHop, screenId), screenId, added);
		}
		List<Map<String, Object>> renderers = featureDao.selectRenderingMethods(walk.revisionId, screens);
		for (int i = 0; i < renderers.size(); i++) {
			Map<String, Object> renderer = renderers.get(i);
			String screenId = String.valueOf(renderer.get("to"));
			this.addHandler(walk, String.valueOf(renderer.get("from")), hopOf(walk.screenHop, screenId), null, added);
		}
		return added;
	}

	private boolean isSharedRequest(Walk walk, Map<String, Object> request) {
		Object script = request.get("script");
		return "SCRIPT_SRC".equals(request.get("via")) && script != null && walk.sharedScripts.contains(String.valueOf(script));
	}

	private void addScreen(Walk walk, String screenId, int hop, String reason, Set<String> newScreens) {
		if (walk.screenHop.containsKey(screenId)) {
			return;
		}
		if (walk.screenHop.size() >= MAX_SCREENS) {
			truncate(walk, "화면이 " + MAX_SCREENS + "개를 넘어 나머지는 넣지 않았습니다.");
			return;
		}
		walk.screenHop.put(screenId, Integer.valueOf(hop));
		walk.screenReason.put(screenId, reason);
		newScreens.add(screenId);
	}

	private void addHandler(Walk walk, String methodId, int hop, String requestedBy, Set<String> added) {
		walk.handlers.add(methodId);
		if (requestedBy != null) {
			Set<String> screens = walk.requestedBy.get(methodId);
			if (screens == null) {
				screens = new LinkedHashSet<String>();
				walk.requestedBy.put(methodId, screens);
			}
			screens.add(requestedBy);
		}
		if (!walk.methodDepth.containsKey(methodId)) {
			walk.methodDepth.put(methodId, Integer.valueOf(1));
			walk.methodHop.put(methodId, Integer.valueOf(hop));
			added.add(methodId);
		}
	}

	/* ======================= 메소드 ======================= */

	/**
	 * <pre>
	 * 메소드들을 한 단계씩 따라 내려갑니다. 돌려주는 화면(RENDERS)이 새로 나오면 newScreens에 넣고,
	 * 그 화면에서 다시 시작할 메소드는 다음 바퀴에서 더해지므로 여기서는 빈 묶음을 돌려줍니다.
	 * </pre>
	 */
	private Set<String> expandMethods(Walk walk, Set<String> start, Set<String> newScreens) {
		Set<String> frontier = new LinkedHashSet<String>();
		// 처음 들어온 메소드의 정보(계층)를 먼저 읽어, 따라갈지 정한다
		this.loadInfo(walk, start);
		for (String methodId : start) {
			if (this.keep(walk, methodId)) {
				frontier.add(methodId);
			}
		}
		while (!frontier.isEmpty()) {
			List<Map<String, Object>> edges = featureDao.selectMethodEdges(walk.revisionId, frontier);
			Set<String> nextMethods = new LinkedHashSet<String>();
			for (int i = 0; i < edges.size(); i++) {
				Map<String, Object> edge = edges.get(i);
				String from = String.valueOf(edge.get("from"));
				String to = String.valueOf(edge.get("to"));
				String toKind = String.valueOf(edge.get("toKind"));
				int depth = walk.methodDepth.get(from).intValue();
				int hop = hopOf(walk.methodHop, from);
				if ("SQL".equals(toKind)) {
					if (!walk.statements.contains(to)) {
						if (walk.statements.size() >= MAX_STATEMENTS) {
							truncate(walk, "SQL이 " + MAX_STATEMENTS + "개를 넘어 나머지는 넣지 않았습니다.");
						} else {
							walk.statements.add(to);
						}
					}
				} else if ("FILE".equals(toKind)) {
					if (hop + 1 <= MAX_SCREEN_HOPS) {
						this.addScreen(walk, to, hop + 1, "메소드가 돌려주는 화면", newScreens);
					} else if (!walk.screenHop.containsKey(to)) {
						walk.notes.add("화면 단계 " + MAX_SCREEN_HOPS + "를 넘어 따라가지 않은 화면이 있습니다(" + to + ").");
					}
				} else if (!walk.methodDepth.containsKey(to) && depth + 1 <= MAX_METHOD_DEPTH) {
					walk.methodDepth.put(to, Integer.valueOf(depth + 1));
					walk.methodHop.put(to, Integer.valueOf(hop));
					nextMethods.add(to);
				}
			}
			this.loadInfo(walk, nextMethods);
			frontier = new LinkedHashSet<String>();
			for (String methodId : nextMethods) {
				if (this.keep(walk, methodId)) {
					frontier.add(methodId);
				}
			}
		}
		return new LinkedHashSet<String>();
	}

	private void loadInfo(Walk walk, Set<String> methodIds) {
		Set<String> missing = new LinkedHashSet<String>();
		for (String methodId : methodIds) {
			if (!walk.methodInfo.containsKey(methodId)) {
				missing.add(methodId);
			}
		}
		List<Map<String, Object>> rows = featureDao.selectMethods(walk.revisionId, missing);
		for (int i = 0; i < rows.size(); i++) {
			walk.methodInfo.put(String.valueOf(rows.get(i).get("methodId")), rows.get(i));
		}
	}

	/**
	 * <pre>
	 * 이 메소드를 묶음의 코드로 넣고 더 따라갈지 정합니다.
	 * MODEL · UTIL 계층과 합성 메소드는 넣지 않고(공통 유틸은 shared에 이름만), 상한을 넘으면 넣지 않습니다.
	 * </pre>
	 */
	private boolean keep(Walk walk, String methodId) {
		Map<String, Object> info = walk.methodInfo.get(methodId);
		if (info == null) {
			walk.methodDepth.remove(methodId);
			return false;
		}
		String layer = info.get("layer") == null ? "" : String.valueOf(info.get("layer")).toUpperCase(Locale.ROOT);
		if (Boolean.TRUE.equals(info.get("isSynthetic"))) {
			walk.methodDepth.remove(methodId);
			return false;
		}
		if (isAccessor(info)) {
			// 한두 줄짜리 get/set은 본뜰 것이 없어 넣지도, 따라가지도 않는다
			walk.methodDepth.remove(methodId);
			return false;
		}
		if (LEAF_LAYERS.contains(layer)) {
			walk.methodDepth.remove(methodId);
			if ("MODEL".equals(layer)) {
				walk.models.add(String.valueOf(info.get("owner")));
			} else if (walk.shared.size() < MAX_SHARED) {
				walk.shared.put(methodId, info);
			}
			return false;
		}
		if (walk.kept.size() >= MAX_METHODS) {
			walk.methodDepth.remove(methodId);
			truncate(walk, "메소드가 " + MAX_METHODS + "개를 넘어 나머지는 넣지 않았습니다.");
			return false;
		}
		walk.kept.add(methodId);
		return true;
	}

	/* ======================= 결과 ======================= */

	private Map<String, Object> result(Walk walk, List<Map<String, Object>> entryRows) {
		Map<String, Map<String, Object>> files = new LinkedHashMap<String, Map<String, Object>>();
		List<Map<String, Object>> fileRows = featureDao.selectFiles(walk.revisionId, walk.screenHop.keySet());
		for (int i = 0; i < fileRows.size(); i++) {
			files.put(String.valueOf(fileRows.get(i).get("id")), fileRows.get(i));
		}

		List<Map<String, Object>> screens = new ArrayList<Map<String, Object>>();
		for (Map.Entry<String, Integer> entry : walk.screenHop.entrySet()) {
			Map<String, Object> file = files.get(entry.getKey());
			Map<String, Object> screen = new LinkedHashMap<String, Object>();
			screen.put("path", file == null ? entry.getKey() : file.get("path"));
			screen.put("lineCount", file == null ? null : file.get("lineCount"));
			screen.put("hop", entry.getValue());
			screen.put("reason", walk.screenReason.get(entry.getKey()));
			Set<String> scripts = walk.screenScripts.get(entry.getKey());
			screen.put("scripts", scripts == null ? new ArrayList<String>() : new ArrayList<String>(scripts));
			screens.add(screen);
		}

		Map<String, List<String>> urls = new LinkedHashMap<String, List<String>>();
		List<Map<String, Object>> endpointRows = featureDao.selectEndpointsByMethods(walk.revisionId, walk.handlers);
		for (int i = 0; i < endpointRows.size(); i++) {
			String methodId = String.valueOf(endpointRows.get(i).get("methodId"));
			List<String> list = urls.get(methodId);
			if (list == null) {
				list = new ArrayList<String>();
				urls.put(methodId, list);
			}
			list.add(String.valueOf(endpointRows.get(i).get("path")));
		}
		List<Map<String, Object>> endpoints = new ArrayList<Map<String, Object>>();
		for (String methodId : walk.handlers) {
			Map<String, Object> info = walk.methodInfo.get(methodId);
			List<String> paths = urls.get(methodId);
			Map<String, Object> endpoint = new LinkedHashMap<String, Object>();
			endpoint.put("paths", paths == null ? new ArrayList<String>() : paths);
			endpoint.put("methodId", methodId);
			endpoint.put("handler", info == null ? methodId : info.get("owner") + "#" + info.get("signature"));
			List<String> by = new ArrayList<String>();
			Set<String> requesters = walk.requestedBy.get(methodId);
			if (requesters != null) {
				for (String screenId : requesters) {
					Map<String, Object> file = files.get(screenId);
					by.add(file == null ? screenId : String.valueOf(file.get("path")));
				}
			}
			endpoint.put("requestedBy", by);
			endpoints.add(endpoint);
		}

		List<Map<String, Object>> methods = new ArrayList<Map<String, Object>>();
		for (Map.Entry<String, Integer> entry : walk.methodDepth.entrySet()) {
			Map<String, Object> info = walk.methodInfo.get(entry.getKey());
			if (info == null || !walk.kept.contains(entry.getKey())) {
				continue;
			}
			Map<String, Object> method = new LinkedHashMap<String, Object>();
			method.put("methodId", entry.getKey());
			method.put("owner", info.get("owner"));
			method.put("layer", info.get("layer"));
			method.put("signature", info.get("signature"));
			method.put("path", info.get("path"));
			method.put("lineStart", info.get("lineStart"));
			method.put("lineEnd", info.get("lineEnd"));
			method.put("depth", entry.getValue());
			methods.add(method);
		}

		List<Map<String, Object>> shared = new ArrayList<Map<String, Object>>();
		for (Map<String, Object> info : walk.shared.values()) {
			Map<String, Object> row = new LinkedHashMap<String, Object>();
			row.put("owner", info.get("owner"));
			row.put("layer", info.get("layer"));
			row.put("signature", info.get("signature"));
			row.put("path", info.get("path"));
			row.put("lineStart", info.get("lineStart"));
			shared.add(row);
		}

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("revisionId", Long.valueOf(walk.revisionId));
		result.put("entries", entryRows);
		result.put("screens", screens);
		result.put("endpoints", endpoints);
		result.put("methods", methods);
		result.put("statements", featureDao.selectStatements(walk.revisionId, walk.statements));
		result.put("shared", shared);
		result.put("models", new ArrayList<String>(walk.models));
		result.put("truncated", Boolean.valueOf(walk.truncated));
		result.put("notes", walk.notes);
		return result;
	}

	/** 상한에 걸렸다고 표시하고, 같은 안내는 한 번만 남긴다 */
	private static void truncate(Walk walk, String note) {
		walk.truncated = true;
		if (!walk.notes.contains(note)) {
			walk.notes.add(note);
		}
	}

	/** get/set/is 로 시작하고 본문이 3줄 이하인 메소드 */
	static boolean isAccessor(Map<String, Object> info) {
		String signature = info.get("signature") == null ? "" : String.valueOf(info.get("signature"));
		boolean named = signature.startsWith("get") || signature.startsWith("set") || signature.startsWith("is");
		if (!named || !(info.get("lineStart") instanceof Number) || !(info.get("lineEnd") instanceof Number)) {
			return false;
		}
		int lines = ((Number) info.get("lineEnd")).intValue() - ((Number) info.get("lineStart")).intValue() + 1;
		return lines <= 3;
	}

	private static int hopOf(Map<String, Integer> hops, String id) {
		Integer hop = hops.get(id);
		return hop == null ? 0 : hop.intValue();
	}

	/** 목록 기호, 따옴표, 백틱을 떼어 낸 시작점 */
	static String clean(String entry) {
		if (entry == null) {
			return "";
		}
		String value = entry.trim();
		while (value.startsWith("-") || value.startsWith("*") || value.startsWith("`") || value.startsWith("\"") || value.startsWith("'")) {
			value = value.substring(1).trim();
		}
		while (value.endsWith("`") || value.endsWith("\"") || value.endsWith("'")) {
			value = value.substring(0, value.length() - 1).trim();
		}
		return value.replace('\\', '/');
	}

}
