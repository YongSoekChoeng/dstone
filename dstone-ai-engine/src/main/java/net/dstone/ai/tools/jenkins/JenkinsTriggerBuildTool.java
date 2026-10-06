package net.dstone.ai.tools.jenkins;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import net.dstone.ai.common.annotation.AiTool;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.runtime.tool.ToolOutcome;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.LogUtil;
import net.dstone.common.utils.StringUtil;
import net.dstone.common.utils.WcUtil;

/**
 * 미리 허용해 둔 Jenkins Job만 REST API로 원격 기동할 수 있는 Tool입니다. tools.shell.ShellExecTool과
 * 같은 원칙을 따릅니다: allowed-jobs에 아무 Job도 등록하지 않으면(기본값) 어떤 jobName을 줘도
 * 화이트리스트에서 찾지 못해 항상 실패로 끝나기 때문에, 설정을 비워두는 것 자체가 곧 "이 Tool을 끈
 * 상태"가 됩니다. 이 Tool에는 jobName만 넘길 수 있고, Job에 넘길 파라미터나 임의의 Jenkins REST
 * 경로를 LLM이 직접 지어낼 수는 없습니다.
 *
 * Jenkins는 기본적으로 CSRF 보호(크럼)가 켜져 있고, 이 크럼은 세션 쿠키에 묶여 발급됩니다(Basic Auth로
 * 매 요청을 새로 보내더라도 마찬가지입니다) - 그래서 이 Tool은 크럼을 발급받을 때 함께 온 Set-Cookie
 * 값을 그대로 보관해 뒀다가, 바로 이어지는 빌드 기동 요청에 다시 실어 보냅니다. 쿠키 없이 크럼만
 * 보내면 Jenkins가 "No valid crumb"(403)로 거부합니다.
 */
@AiTool
public class JenkinsTriggerBuildTool extends BaseObject {

	@Autowired
	private ConfigProperty configProperty;
	@Autowired
	private Environment environment;

	/**
	 * @param jobName 기동할 Jenkins Job 이름입니다(화이트리스트에 등록된 이름과 정확히 같아야 합니다).
	 */
	@Tool(description = "사전에 허용된 Jenkins Job 하나를 REST API로 원격 기동한다(큐에 등록만 하고 빌드 완료를 기다리지 않는다). "
		+ "jobName은 관리자가 미리 등록해둔 이름과 정확히 일치해야 하며, 등록되지 않은 이름은 기동할 수 없다.")
	public ToolOutcome triggerBuild(@ToolParam(description = "기동할 Jenkins Job의 등록된 이름") String jobName) {
		if (!this.isAllowedJob(jobName)) {
			LogUtil.sysout("dstone-ai-engine tool-audit: jenkins jobName=" + jobName + " -> 실패 화이트리스트에 없는 Job");
			return ToolOutcome.fail("화이트리스트에 없는 Job입니다: " + jobName);
		}
		String baseUrl = this.property("base-url");
		String authHeader = this.basicAuthHeader();
		if (StringUtil.isEmpty(baseUrl) || authHeader == null) {
			return ToolOutcome.fail("Jenkins 연결 설정이 비어 있습니다(dstone.ai.tool.jenkins.base-url / .user / .api-token을 확인하세요).");
		}

		WebClient webClient = WcUtil.getInstance().getWebClient((int) this.timeout().toSeconds());
		try {
			CrumbOutcome crumb = this.fetchCrumb(webClient, baseUrl, authHeader);
			if (crumb == null) {
				return ToolOutcome.fail("Jenkins 크럼(CSRF 토큰) 발급에 실패했습니다 - 접속 정보(base-url/user/api-token)를 확인하세요.");
			}
			BuildOutcome outcome = this.postBuild(webClient, baseUrl, jobName, authHeader, crumb);
			LogUtil.sysout("dstone-ai-engine tool-audit: jenkins jobName=" + jobName + " -> HTTP " + outcome.statusCode() + " queue=" + outcome.queueLocation());
			if (!outcome.success()) {
				return ToolOutcome.fail("Jenkins 빌드 기동 실패 - HTTP " + outcome.statusCode());
			}
			return ToolOutcome.pass("Jenkins Job[" + jobName + "] 빌드를 큐에 등록했습니다."
				+ (StringUtil.isEmpty(outcome.queueLocation()) ? "" : " (" + outcome.queueLocation() + ")"));
		} catch (Exception e) {
			LogUtil.sysout("dstone-ai-engine tool-audit: jenkins jobName=" + jobName + " -> 실패 " + e.getMessage());
			return ToolOutcome.fail("Jenkins 호출 실패 - " + e.getMessage());
		}
	}

	/**
	 * @param webClient 이번 호출 전체에서 재사용할 WebClient입니다.
	 * @param baseUrl   Jenkins 서버 주소입니다.
	 * @param authHeader Basic 인증 헤더 값입니다.
	 */
	private CrumbOutcome fetchCrumb(WebClient webClient, String baseUrl, String authHeader) {
		return webClient.get()
			.uri(baseUrl + "/crumbIssuer/api/json")
			.header("Authorization", authHeader)
			.exchangeToMono(new Function<ClientResponse, Mono<CrumbOutcome>>() {
				@Override
				public Mono<CrumbOutcome> apply(final ClientResponse response) {
					if (!response.statusCode().is2xxSuccessful()) {
						return Mono.empty();
					}
					final String cookie = JenkinsTriggerBuildTool.this.cookieHeaderValue(response);
					return response.bodyToMono(Map.class).map(new Function<Map, CrumbOutcome>() {
						@Override
						public CrumbOutcome apply(Map body) {
							return new CrumbOutcome(String.valueOf(body.get("crumb")), String.valueOf(body.get("crumbRequestField")), cookie);
						}
					});
				}
			})
			.block();
	}

	/**
	 * @param webClient  이번 호출 전체에서 재사용할 WebClient입니다.
	 * @param baseUrl    Jenkins 서버 주소입니다.
	 * @param jobName    기동할 Job 이름입니다.
	 * @param authHeader Basic 인증 헤더 값입니다.
	 * @param crumb      fetchCrumb()로 미리 발급받아 둔 크럼/쿠키입니다.
	 */
	private BuildOutcome postBuild(WebClient webClient, String baseUrl, String jobName, String authHeader, CrumbOutcome crumb) {
		return webClient.post()
			.uri(baseUrl + "/job/" + jobName + "/build?delay=0sec")
			.header("Authorization", authHeader)
			.header("Cookie", crumb.cookie())
			.header(crumb.crumbField(), crumb.crumb())
			.exchangeToMono(new Function<ClientResponse, Mono<BuildOutcome>>() {
				@Override
				public Mono<BuildOutcome> apply(final ClientResponse response) {
					final int statusCode = response.statusCode().value();
					final boolean success = response.statusCode().is2xxSuccessful() || response.statusCode().is3xxRedirection();
					final String queueLocation = response.headers().asHttpHeaders().getFirst("Location");
					return response.bodyToMono(String.class).defaultIfEmpty("").map(new Function<String, BuildOutcome>() {
						@Override
						public BuildOutcome apply(String body) {
							return new BuildOutcome(statusCode, success, queueLocation);
						}
					});
				}
			})
			.block();
	}

	/** @param response Set-Cookie를 뽑아낼 응답입니다. 여러 개면 "이름=값" 형태로 이어 붙여 하나의 Cookie 헤더 값으로 만듭니다. */
	private String cookieHeaderValue(ClientResponse response) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, List<ResponseCookie>> entry : response.cookies().entrySet()) {
			for (ResponseCookie value : entry.getValue()) {
				if (sb.length() > 0) {
					sb.append("; ");
				}
				sb.append(value.getName()).append("=").append(value.getValue());
			}
		}
		return sb.toString();
	}

	/** 크럼 발급/빌드 기동 응답에서 필요한 값만 뽑아 담아두는 작은 보관용 클래스입니다. */
	private record CrumbOutcome(String crumb, String crumbField, String cookie) {
	}

	/** 빌드 기동 응답에서 필요한 값만 뽑아 담아두는 작은 보관용 클래스입니다. */
	private record BuildOutcome(int statusCode, boolean success, String queueLocation) {
	}

	/** @param jobName 화이트리스트에 있는지 확인할 Job 이름입니다. */
	private boolean isAllowedJob(String jobName) {
		if (StringUtil.isEmpty(jobName)) {
			return false;
		}
		List<String> allowedJobs = Binder.get(this.environment).bind(Constants.Tool.Jenkins.PREFIX + ".allowed-jobs", Bindable.listOf(String.class)).orElse(List.of());
		return allowedJobs.contains(jobName);
	}

	/** @param user Jenkins 계정 이름입니다. @param apiToken 그 계정의 Jenkins API 토큰입니다. */
	private String basicAuthHeader() {
		String user = this.property("user");
		String apiToken = this.property("api-token");
		if (StringUtil.isEmpty(user) || StringUtil.isEmpty(apiToken)) {
			return null;
		}
		String credentials = user + ":" + apiToken;
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}

	/** @param suffix Constants.Tool.Jenkins.PREFIX 뒤에 붙일 설정 키 접미사입니다(점 없이, 예: "base-url"). */
	private String property(String suffix) {
		return this.configProperty.getProperty(Constants.Tool.Jenkins.PREFIX + "." + suffix);
	}

	private Duration timeout() {
		String seconds = this.property("timeout-seconds");
		return Duration.ofSeconds(StringUtil.isEmpty(seconds) ? 15 : Long.parseLong(seconds));
	}

}
