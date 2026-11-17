package net.dstone.knowledge.analysis.pass08.semantic.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <pre>
 * Vue / React 화면이 부르는 주소(GET /sample/boards/{})를 진입점(GET /api/sample/boards/{seq})과 맞춥니다. 순수한 클래스입니다.
 *
 * 맞추는 법:
 *   - 마디(/ 사이) 수가 같아야 한다.
 *   - 마디마다: 글자가 똑같거나, 값 자리끼리({} 와 {seq}) 맞는다.
 *     글자로 적은 마디가 진입점의 경로 변수에 들어가는 것(/codes/USE_YN → /codes/{grpCmmCd})도 맞지만,
 *     글자끼리 똑같이 맞는 진입점이 있으면 그쪽만 남긴다(Spring 도 더 구체적인 주소를 고른다).
 *   - HTTP 메소드를 알면 같은 메소드이거나 메소드를 가리지 않는(ALL) 진입점만 남긴다.
 *   - 진입점 주소에 * 가 있으면(/sys/**) 맞추지 않는다. 화면 주소를 돌려주는 컨트롤러가 모든 호출에 걸리지 않게 한다.
 * </pre>
 */
public class SpaEndpoints {

	/** 진입점 하나 */
	private static class Endpoint {
		final String[] segments;
		final String verb;
		final String methodId;

		Endpoint(String[] segments, String verb, String methodId) {
			this.segments = segments;
			this.verb = verb;
			this.methodId = methodId;
		}
	}

	/** 마디 수 → 진입점들 */
	private final Map<Integer, List<Endpoint>> bySize = new HashMap<Integer, List<Endpoint>>();

	/**
	 * @param rows [{path, httpMethod, methodId}]
	 */
	public SpaEndpoints(List<Map<String, Object>> rows) {
		for (int i = 0; i < rows.size(); i++) {
			String path = (String) rows.get(i).get("path");
			String methodId = (String) rows.get(i).get("methodId");
			if (path == null || methodId == null || path.indexOf('*') >= 0) {
				continue;
			}
			String[] segments = canonical(path);
			if (segments == null) {
				continue;
			}
			Integer size = Integer.valueOf(segments.length);
			List<Endpoint> list = bySize.get(size);
			if (list == null) {
				list = new ArrayList<Endpoint>();
				bySize.put(size, list);
			}
			list.add(new Endpoint(segments, (String) rows.get(i).get("httpMethod"), methodId));
		}
	}

	public boolean isEmpty() {
		return bySize.isEmpty();
	}

	/**
	 * <pre>
	 * 주소가 진입점과 맞춰 볼 만한 모양인지. 마디마다 글자이거나 값 자리 하나({})여야 합니다.
	 *   /sample/boards/{}   → 됨
	 *   /api{}{}            → 안 됨(fetch(API_BASE + path) 처럼 주소 전체가 값이다)
	 *   /a/{}.do            → 안 됨(마디 일부만 값이다)
	 * </pre>
	 */
	public static boolean usable(String url) {
		String[] segments = canonical(url);
		if (segments == null || segments.length == 0) {
			return false;
		}
		boolean literal = false;
		for (int i = 0; i < segments.length; i++) {
			String s = segments[i];
			if ("{}".equals(s)) {
				continue;
			}
			if (s.indexOf('{') >= 0 || s.indexOf('}') >= 0) {
				return false;
			}
			literal = true;
		}
		return literal;
	}

	/**
	 * <pre>
	 * 주소와 맞는 진입점 메소드들. 없으면 빈 목록.
	 * </pre>
	 *
	 * @param verb HTTP 메소드. 모르면 null
	 */
	public List<String> match(String url, String verb) {
		List<String> methods = new ArrayList<String>();
		String[] wanted = canonical(url);
		if (wanted == null) {
			return methods;
		}
		List<Endpoint> list = bySize.get(Integer.valueOf(wanted.length));
		if (list == null) {
			return methods;
		}
		// 글자가 경로 변수에 들어간 마디 수가 가장 적은 것만 남긴다.
		int best = Integer.MAX_VALUE;
		Set<String> found = new LinkedHashSet<String>();
		for (int i = 0; i < list.size(); i++) {
			Endpoint endpoint = list.get(i);
			if (!verbMatches(endpoint.verb, verb)) {
				continue;
			}
			int loose = looseMatches(wanted, endpoint.segments);
			if (loose < 0 || loose > best) {
				continue;
			}
			if (loose < best) {
				best = loose;
				found.clear();
			}
			found.add(endpoint.methodId);
		}
		methods.addAll(found);
		return methods;
	}

	/** 마디가 모두 맞으면 "글자가 경로 변수에 들어간" 마디 수, 하나라도 안 맞으면 -1 */
	private int looseMatches(String[] wanted, String[] segments) {
		int loose = 0;
		for (int i = 0; i < wanted.length; i++) {
			if (wanted[i].equals(segments[i])) {
				continue;
			}
			if ("{}".equals(segments[i]) && wanted[i].indexOf('{') < 0) {
				loose++;
				continue;
			}
			return -1;
		}
		return loose;
	}

	/** 진입점이 그 HTTP 메소드를 받는지. 진입점이 메소드를 가리지 않거나(ALL, 비어 있음) 부르는 쪽 메소드를 모르면 받는다 */
	private boolean verbMatches(String endpointVerb, String verb) {
		if (verb == null || endpointVerb == null || endpointVerb.length() == 0 || "ALL".equals(endpointVerb)) {
			return true;
		}
		return endpointVerb.equalsIgnoreCase(verb);
	}

	/** 주소를 마디로 나누고, 경로 변수({seq}, {id:\d+})를 {} 로 바꿉니다. 짝이 맞지 않으면 null */
	static String[] canonical(String path) {
		String p = path.trim();
		while (p.startsWith("/")) {
			p = p.substring(1);
		}
		while (p.endsWith("/")) {
			p = p.substring(0, p.length() - 1);
		}
		if (p.length() == 0) {
			return new String[0];
		}
		String[] parts = p.split("/+");
		for (int i = 0; i < parts.length; i++) {
			StringBuilder sb = new StringBuilder();
			String part = parts[i];
			int at = 0;
			while (at < part.length()) {
				char c = part.charAt(at);
				if (c != '{') {
					sb.append(c);
					at++;
					continue;
				}
				int close = part.indexOf('}', at);
				if (close < 0) {
					return null;
				}
				sb.append("{}");
				at = close + 1;
			}
			parts[i] = sb.toString();
		}
		return parts;
	}

}
