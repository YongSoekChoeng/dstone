package net.dstone.knowledge.semantic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.dstone.knowledge.api.dao.SemanticDao;
import net.dstone.knowledge.common.util.JsonText;
import net.dstone.knowledge.job.AnalysisJobContext;
import net.dstone.knowledge.scanner.EncodingDetector;

/**
 * <pre>
 * 화면(JSP)을 Java 쪽과 잇습니다.
 *
 * 만드는 관계:
 *   메소드 ─RENDERS→ JSP    컨트롤러의 메소드가 이 화면을 연다 (new ModelAndView("order/list"), setViewName(...))
 *   JSP ─REQUESTS→ 메소드   이 화면이 이 메소드의 주소를 요청한다 (form action, AJAX url, 링크)
 *   JSP ─INCLUDES→ JSP      이 화면이 다른 JSP를 끼워 넣는다 (jsp:include, include 지시자)
 * 이것이 있어야 "이 화면에서 저장을 누르면 어디로 가나", "이 메소드는 어느 화면에서 불리나"를 따라갈 수 있습니다.
 *
 * JSP 안의 Java 코드(스크립틀릿)가 부르는 것은 여기서 하지 않습니다. JSP를 Java로 바꿔서(JspToJava)
 * 일반 Java 파일과 같은 길(DECLARE, RESOLVE)로 이미 풀려 있습니다. 여기서는 그 타입에 화면 계층(VIEW)을 붙이고
 * JSP 진입점에 실행 메소드를 달아 주기만 합니다.
 *
 * "요청하는 주소"를 찾는 방법:
 * JSP에서 주소가 적히는 모양은 제각각입니다(action="...", url: '...', location.href = ..., ${ctx}/..., c:url ...).
 * 모양을 하나하나 따지지 않고, JSP 안에서 /로 시작하는 경로처럼 생긴 글자를 전부 찾아
 * 그것이 이 프로젝트의 진입점 주소와 정확히 같을 때만 잇습니다. 진입점과 같지 않은 글자는 버려지므로 잘못 잇는 일이 적습니다.
 * 글자가 같다는 것만으로 잇는 것이라 신뢰도는 MEDIUM입니다.
 *
 * 아직 다루지 않는 것:
 * - 주소를 JavaScript에서 조립하는 경우('/order/' + type + '.do').
 * - 컨텍스트 경로를 글자로 붙여 쓴 경우(/myapp/order/list.do).
 * - return "order/list" 처럼 메소드가 뷰 이름을 문자열로 돌려주는 방식, Tiles 정의 이름.
 *
 * Struts의 화면 이동(forward / result)은 StrutsPlugin이 같은 RENDERS 관계로 넣습니다.
 * </pre>
 */
@Component
public class JspPlugin implements SemanticPlugin {

	/** /로 시작하는 경로처럼 생긴 글자. 예: /order/list.do, /api/auth/me */
	private static final Pattern PATH = Pattern.compile("/[A-Za-z0-9_\\-]+(?:/[A-Za-z0-9_\\-]+)*(?:\\.[A-Za-z0-9]+)?");

	/** 끼워 넣는 JSP. &lt;jsp:include page="..."&gt; 와 &lt;%@ include file="..." %&gt; */
	private static final Pattern INCLUDE = Pattern.compile("(?:<jsp:include\\s+[^>]*page|<%@\\s*include\\s+file)\\s*=\\s*\"([^\"]+)\"");

	private static final int PAGE_SIZE = 200;

	@Autowired
	private SemanticDao semanticDao;

	@Autowired
	private EncodingDetector encodingDetector;

	@Override
	public String name() {
		return "jsp";
	}

	@Override
	public int order() {
		// 진입점(Spring, 서블릿, JSP)이 다 만들어진 뒤에 돌아야 한다.
		return 500;
	}

	@Override
	public String run(AnalysisJobContext context) throws Exception {
		long revisionId = context.getRevisionId();
		// 화면과 이어진 관계는 SEMANTIC 단계가 시작할 때 이미 지워져 있다(SemanticDao.clear).

		int layered = semanticDao.updateJspLayer(revisionId);
		semanticDao.updateJspEndpointMethods(revisionId);
		int renders = semanticDao.insertRenders(revisionId);

		// 주소 → 그 주소를 처리하는 메소드들. 진입점 수만큼만 메모리에 올라간다.
		Map<String, List<String>> handlers = new HashMap<String, List<String>>();
		List<Map<String, Object>> endpoints = semanticDao.selectEndpointHandlers(revisionId);
		for (int i = 0; i < endpoints.size(); i++) {
			String path = (String) endpoints.get(i).get("path");
			List<String> list = handlers.get(path);
			if (list == null) {
				list = new ArrayList<String>();
				handlers.put(path, list);
			}
			list.add((String) endpoints.get(i).get("methodId"));
		}

		Path root = Paths.get(context.getProjectValue("localPath")).toRealPath();
		int files = 0, requests = 0, includes = 0;
		long afterId = 0;
		while (true) {
			context.checkCancelled();
			List<Map<String, Object>> page = semanticDao.selectFilePage(revisionId, "JSP", afterId, PAGE_SIZE);
			if (page.isEmpty()) {
				break;
			}
			for (int i = 0; i < page.size(); i++) {
				Map<String, Object> file = page.get(i);
				long fileId = ((Number) file.get("fileId")).longValue();
				afterId = fileId;
				String text;
				try {
					byte[] bytes = Files.readAllBytes(root.resolve((String) file.get("path")));
					text = encodingDetector.decode(bytes, (String) file.get("encoding"));
				} catch (Exception e) {
					// 파일 하나를 못 읽었다고 전체를 멈추지 않는다.
					continue;
				}
				files++;
				requests += linkRequests(revisionId, fileId, text, handlers);
				includes += linkIncludes(revisionId, fileId, (String) file.get("path"), text);
			}
		}
		return "JSP " + files + "개. 화면 계층 " + layered + ", 메소드가 여는 화면 " + renders + ", 화면이 요청하는 주소 " + requests + ", 끼워 넣기 " + includes + ".";
	}

	/** JSP 안의 주소 가운데 진입점과 같은 것을 찾아 잇습니다. 같은 메소드는 한 번만 잇습니다. */
	private int linkRequests(long revisionId, long fileId, String text, Map<String, List<String>> handlers) {
		if (handlers.isEmpty()) {
			return 0;
		}
		int count = 0;
		Set<String> linked = new HashSet<String>();
		Matcher m = PATH.matcher(text);
		while (m.find()) {
			List<String> methods = handlers.get(m.group());
			if (methods == null) {
				continue;
			}
			for (int i = 0; i < methods.size(); i++) {
				if (!linked.add(methods.get(i))) {
					continue;
				}
				Map<String, Object> properties = new LinkedHashMap<String, Object>();
				properties.put("path", m.group());
				properties.put("via", "PATH_TEXT");
				semanticDao.insertRelation(relation(revisionId, "FILE", "F" + fileId, "REQUESTS", "METHOD", methods.get(i), "MEDIUM", "HEURISTIC"
						, properties, fileId, lineAt(text, m.start())));
				count++;
			}
		}
		return count;
	}

	/** 끼워 넣는 JSP를 찾아 잇습니다. 경로가 가리키는 JSP가 하나로 정해질 때만 잇습니다. */
	private int linkIncludes(long revisionId, long fileId, String path, String text) {
		int count = 0;
		Set<Long> linked = new HashSet<Long>();
		Matcher m = INCLUDE.matcher(text);
		while (m.find()) {
			String target = m.group(1).trim();
			if (target.indexOf("${") >= 0 || target.indexOf("<%") >= 0) {
				// 실행할 때 정해지는 경로
				continue;
			}
			String resolved;
			if (target.startsWith("/")) {
				// 웹 루트 기준의 경로: 앞의 /를 떼고 "그 경로로 끝나는 JSP"를 찾는다.
				resolved = target.substring(1);
			} else {
				// 지금 파일이 있는 폴더 기준의 경로
				String directory = path.lastIndexOf('/') < 0 ? "" : path.substring(0, path.lastIndexOf('/') + 1);
				resolved = normalize(directory + target);
			}
			List<Long> found = semanticDao.selectJspByPathEnd(revisionId, resolved);
			if (found.size() != 1 || found.get(0).longValue() == fileId || !linked.add(found.get(0))) {
				continue;
			}
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
			properties.put("path", target);
			semanticDao.insertRelation(relation(revisionId, "FILE", "F" + fileId, "INCLUDES", "FILE", "F" + found.get(0), "HIGH", "RESOLVED"
					, properties, fileId, lineAt(text, m.start())));
			count++;
		}
		return count;
	}

	/** a/b/../c.jsp → a/c.jsp */
	private String normalize(String path) {
		List<String> parts = new ArrayList<String>();
		String[] segments = path.split("/");
		for (int i = 0; i < segments.length; i++) {
			if ("..".equals(segments[i])) {
				if (!parts.isEmpty()) {
					parts.remove(parts.size() - 1);
				}
			} else if (!".".equals(segments[i]) && segments[i].length() > 0) {
				parts.add(segments[i]);
			}
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			sb.append(i > 0 ? "/" : "").append(parts.get(i));
		}
		return sb.toString();
	}

	private Integer lineAt(String text, int index) {
		int line = 1;
		for (int i = 0; i < index; i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return Integer.valueOf(line);
	}

	private Map<String, Object> relation(long revisionId, String fromKind, String fromId, String relationType, String toKind, String toId
			, String confidence, String resolutionStatus, Map<String, Object> properties, long fileId, Integer lineStart) {
		Map<String, Object> relation = new HashMap<String, Object>();
		relation.put("revisionId", revisionId);
		relation.put("fromKind", fromKind);
		relation.put("fromId", fromId);
		relation.put("relationType", relationType);
		relation.put("toKind", toKind);
		relation.put("toId", toId);
		relation.put("toExternal", null);
		relation.put("confidence", confidence);
		relation.put("resolutionStatus", resolutionStatus);
		relation.put("propertiesJson", JsonText.of(properties));
		relation.put("fileId", Long.valueOf(fileId));
		relation.put("lineStart", lineStart);
		return relation;
	}

}
