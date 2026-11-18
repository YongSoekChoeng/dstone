package net.dstone.boot.ai.service;

import java.net.URI;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Mono;

/**
 * <pre>
 * "Workflow 디자이너" 화면 대신 dstone-ai-engine의 정의 API(/api/ai/definitions)를 불러 주는 서비스입니다.
 *
 * 화면이 쓰는 API가 여러 개라(목록, 원문, 검사, 저장, 삭제, 다시 읽기) API마다 메소드를 두지 않고 그대로 넘겨줍니다
 * (knowledge.service.KnowledgeProxyService와 같은 방식).
 * 대신 넘겨줄 수 있는 범위를 좁혀 둡니다: /api/ai/definitions 아래 경로만, GET / POST만.
 * 응답은 상태 코드와 본문(JSON)을 손대지 않고 돌려줍니다. 검사에 걸린 이유({ok: false, message})도 그대로 화면에 보입니다.
 * </pre>
 */
@Service
public class WorkFlowDesignerService extends net.dstone.boot.common.biz.BaseService {

	/** 넘겨줄 수 있는 경로의 앞부분 */
	private static final String PATH_PREFIX = "/api/ai/definitions";

	@Autowired
	private ConfigProperty configProperty;

	/**
	 * @param method GET / POST
	 * @param path dstone-ai-engine의 경로. /api/ai/definitions로 시작해야 합니다
	 * @param body POST로 보낼 JSON 본문. 없으면 null
	 * @return dstone-ai-engine의 상태 코드와 본문 그대로
	 */
	public ResponseEntity<String> call(String method, String path, Object body) {
		String upper = method == null ? "" : method.trim().toUpperCase();
		if (!"GET".equals(upper) && !"POST".equals(upper)) {
			return error(HttpStatus.BAD_REQUEST, "method는 GET 또는 POST입니다: " + method);
		}
		if (path == null || !(path.equals(PATH_PREFIX) || path.startsWith(PATH_PREFIX + "/")) || path.indexOf("..") >= 0 || path.indexOf("//") >= 0
				|| path.indexOf('?') >= 0 || path.indexOf('#') >= 0 || path.indexOf('\\') >= 0) {
			return error(HttpStatus.BAD_REQUEST, "path는 " + PATH_PREFIX + "로 시작하는 경로여야 합니다: " + path);
		}
		String baseUrl = this.configProperty.getProperty("interface.ai-engine.base-url");
		if (StringUtil.isEmpty(baseUrl)) {
			return error(HttpStatus.SERVICE_UNAVAILABLE, "dstone-ai-engine 주소가 설정되지 않았습니다(interface.ai-engine.base-url).");
		}
		// 한글이 든 경로(id)가 깨지지 않게 인코딩한다.
		URI uri = UriComponentsBuilder.fromUriString(baseUrl.trim()).path(path).build().encode().toUri();
		WebClient client = this.getLargeResponseWebClient();
		if ("POST".equals(upper)) {
			return exchange(client.method(HttpMethod.POST).uri(uri).contentType(MediaType.APPLICATION_JSON).bodyValue(body == null ? "{}" : body));
		}
		return exchange(client.method(HttpMethod.GET).uri(uri));
	}

	/** 요청을 보내고, 상태 코드와 본문을 그대로 돌려줍니다. 서버에 닿지 못하면 502로 이유를 알려 줍니다. */
	private ResponseEntity<String> exchange(WebClient.RequestHeadersSpec<?> request) {
		try {
			return request.exchangeToMono(new Function<ClientResponse, Mono<ResponseEntity<String>>>() {
				@Override
				public Mono<ResponseEntity<String>> apply(final ClientResponse response) {
					return response.bodyToMono(String.class).defaultIfEmpty("").map(new Function<String, ResponseEntity<String>>() {
						@Override
						public ResponseEntity<String> apply(String responseBody) {
							return ResponseEntity.status(response.statusCode()).contentType(MediaType.APPLICATION_JSON).body(responseBody);
						}
					});
				}
			}).block();
		} catch (RuntimeException e) {
			return error(HttpStatus.BAD_GATEWAY, "dstone-ai-engine을 부르지 못했습니다. 서버가 떠 있는지, interface.ai-engine.base-url이 맞는지 확인하세요: " + e.getMessage());
		}
	}

	/**
	 * <pre>
	 * 오류를 dstone-ai-engine의 정의 API 실패 응답과 같은 모양({ok: false, message})으로 돌려줍니다.
	 * 예외를 던지지 않는 이유: dstone-boot의 공통 예외 처리를 타면 상태 코드가 200이 되고 메시지 모양도 달라진다.
	 * </pre>
	 */
	private ResponseEntity<String> error(HttpStatus status, String message) {
		String text = String.valueOf(message).replace("\\", "/").replace("\"", "'").replace("\n", " ").replace("\r", " ");
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body("{\"ok\":false,\"message\":\"" + text + "\"}");
	}

}
