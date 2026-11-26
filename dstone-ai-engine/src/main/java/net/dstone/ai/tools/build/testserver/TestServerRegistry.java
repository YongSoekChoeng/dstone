package net.dstone.ai.tools.build.testserver;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.ai.runtime.tool.StepRef;
import net.dstone.ai.runtime.tool.ToolCallCheck;

/**
 * <pre>
 * 지금 떠 있는 테스트 서버의 명부입니다.
 *
 * - DB Tool에 테스트 DB 별칭(test-…)을 알려 줍니다. 그 별칭의 dbQuery는 테스트 서버 안의 같은 연결로 조회합니다(커밋하지 않은 테스트 데이터가 보임).
 *   값: {url, username, password, schema, description, control(테스트 드라이버 제어 파일)}
 * - 테스트 서버를 띄운(또는 다시 받은) step은 그 step이 끝날 때까지 테스트 DB와 테스트 서버 화면만 보게 막습니다.
 *   원래 개발 DB에는 이번 테스트의 결과가 없고, 다른 서버 화면에서 누르면 원복되지 않기 때문입니다.
 * </pre>
 */
@Component
public class TestServerRegistry implements ToolCallCheck {

	/** 테스트 DB 별칭의 앞부분 */
	public static final String ALIAS_PREFIX = "test-";

	private static final Set<String> DB_TOOLS = Set.of("dbQuery", "dbListTables", "dbDescribeTable");
	private static final String BROWSER = "browser_";
	private static final String NAVIGATE = "browser_navigate";
	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * step 하나가 보는 테스트 환경입니다.
	 *
	 * @param url     테스트 서버 주소
	 * @param aliases 테스트 DB 별칭들
	 * @param opened  테스트 서버 화면을 열었는가
	 */
	private record View(String url, Set<String> aliases, AtomicBoolean opened) {
	}

	private final Map<StepRef, View> views = new ConcurrentHashMap<StepRef, View>();
	private final Map<String, Map<String, String>> connections = new ConcurrentHashMap<String, Map<String, String>>();

	/**
	 * 테스트 서버를 step에 넘길 때 올립니다.
	 *
	 * @param stepRef     부른 실행과 step. Workflow 밖이면 null(막지는 않음)
	 * @param url         테스트 서버 주소
	 * @param connections 테스트 DB 별칭 → 접속 정보
	 */
	public void opened(StepRef stepRef, String url, Map<String, Map<String, String>> connections) {
		this.connections.putAll(connections);
		if (stepRef != null) {
			this.views.put(stepRef, new View(url, Set.copyOf(connections.keySet()), new AtomicBoolean()));
		}
	}

	/** 테스트 서버를 내렸을 때 별칭을 뺍니다. */
	public void closed(Set<String> aliases) {
		for (String alias : aliases) {
			this.connections.remove(alias);
		}
	}

	/** step이 끝났을 때 막던 것을 풉니다. */
	public void stepEnded(StepRef stepRef) {
		this.views.remove(stepRef);
	}

	/** 지금 쓸 수 있는 테스트 DB들 */
	public Map<String, Map<String, String>> connections() {
		return new LinkedHashMap<String, Map<String, String>>(this.connections);
	}

	@Override
	public String check(String toolName, String toolInput, StepRef stepRef) {
		if (stepRef == null || (!toolName.startsWith(BROWSER) && !DB_TOOLS.contains(toolName))) {
			return null;
		}
		View view = this.views.get(stepRef);
		if (view == null) {
			return null;
		}
		if (toolName.startsWith(BROWSER) && !NAVIGATE.equals(toolName)) {
			return view.opened().get() ? null : "먼저 browser_navigate로 테스트 서버(" + view.url() + ")를 엽니다. 지금 열려 있는 화면은 다른 서버일 수 있습니다.";
		}
		JsonNode args;
		try {
			args = JSON.readTree(toolInput == null ? "{}" : toolInput);
		} catch (IOException e) {
			return "인자를 읽을 수 없어 테스트 중에는 실행하지 않습니다.";
		}
		if (NAVIGATE.equals(toolName)) {
			String url = args.path("url").asText("").trim();
			if ("about:blank".equals(url)) {
				return null;
			}
			if (sameOrigin(view.url(), url)) {
				view.opened().set(true);
				return null;
			}
			return "테스트 중에는 테스트 서버(" + view.url() + ")의 화면만 엽니다. 다른 서버에서 바꾼 데이터는 원복되지 않습니다.";
		}
		if (view.aliases().contains(args.path("alias").asText("").trim())) {
			return null;
		}
		return "테스트 중에는 테스트 DB만 봅니다. alias는 " + view.aliases() + " 가운데 하나입니다. 다른 별칭에는 이번 테스트의 결과(커밋하지 않은 데이터)가 보이지 않습니다.";
	}

	private static boolean sameOrigin(String expected, String actual) {
		try {
			URI left = URI.create(expected);
			URI right = URI.create(actual);
			return left.getHost() != null && left.getHost().equalsIgnoreCase(right.getHost()) && left.getPort() == right.getPort()
				&& left.getScheme().equalsIgnoreCase(right.getScheme());
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

}
