package net.dstone.boot.knowledge.service;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.utils.StringUtil;
import reactor.core.publisher.Mono;

/**
 * <pre>
 * dstone-knowledge(Java 분석 결과와 문서를 담아 둔 서버)의 REST API를 화면 대신 불러 주는 서비스입니다.
 *
 * 브라우저가 dstone-knowledge를 직접 부르지 않고 이 서비스를 거치는 이유:
 *   - dstone-knowledge는 사용자 로그인이 없는 내부 서버라, 로그인한 사용자만 쓰게 하려면 dstone-boot가 앞에 서야 한다.
 *   - 주소(와 API 키)를 브라우저에 내려보내지 않아도 된다.
 *
 * 화면이 쓰는 API가 많아서(프로젝트, 분석, 리비전, 검색, 영향도, 비교 ...) API마다 메소드를 두지 않고 그대로 넘겨줍니다.
 * 대신 넘겨줄 수 있는 범위를 좁혀 둡니다: dstone-knowledge의 /api/ 아래 경로만, GET / POST / DELETE만.
 * 응답은 상태 코드와 본문(JSON)을 손대지 않고 돌려줍니다. 오류 응답({status, message})도 그대로 화면에 보입니다.
 * </pre>
 */
@Service
public class KnowledgeProxyService extends net.dstone.boot.common.biz.BaseService {

	/** 분석 시작처럼 바로 돌아오는 것도 있지만, 검색은 질문을 임베딩하느라 수 초가 걸릴 수 있다 */
	private static final int READ_TIMEOUT_SECONDS = 120;

	/** 한 번에 받을 수 있는 응답의 최대 크기. 가장 큰 응답은 노드 맵의 전체 맵이다(노드 2만, 선 10만 개 한도에서 20MB쯤) */
	private static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;

	@Autowired
	private ConfigProperty configProperty;

	/**
	 * @param method GET / POST / DELETE
	 * @param path dstone-knowledge의 경로. /api/로 시작해야 합니다. 예: /api/projects
	 * @param query 주소 뒤에 붙일 파라미터. 없으면 null. 값이 비어 있는 것은 붙이지 않습니다
	 * @param body POST로 보낼 JSON 본문. 없으면 null
	 * @return dstone-knowledge의 상태 코드와 본문 그대로
	 */
	public ResponseEntity<String> call(String method, String path, Map<String, Object> query, Object body) {
		HttpMethod httpMethod;
		URI uri;
		try {
			httpMethod = methodOf(method);
			uri = uriOf(path, query);
		} catch (RejectedCallException e) {
			return error(e.status, e.getMessage());
		}
		WebClient.RequestBodySpec request = this.client().method(httpMethod).uri(uri);
		withApiKey(request);
		if (body != null && HttpMethod.POST.equals(httpMethod)) {
			return exchange(request.contentType(MediaType.APPLICATION_JSON).bodyValue(body));
		}
		return exchange(request);
	}

	/**
	 * <pre>
	 * 일반 문서(PDF, Word 등)를 올립니다. 받은 파일을 그대로 dstone-knowledge의 POST /api/documents로 넘깁니다.
	 * </pre>
	 *
	 * @param file 서버에 받아 둔 파일
	 * @param originalFileName 사용자가 올린 원래 파일 이름. dstone-knowledge가 파일 종류를 알아내고 문서 이름의 기본값으로 쓴다
	 * @param sourceId 문서 이름(같은 이름이면 바꿔 넣는다). 없으면 파일 이름
	 * @param title 검색 결과에 보일 제목. 없어도 됨
	 * @param projectId 이 문서를 붙일 프로젝트. 없어도 됨
	 */
	public ResponseEntity<String> upload(File file, String originalFileName, String sourceId, String title, String projectId) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		// 받아 둔 파일은 서버가 붙인 이름으로 저장돼 있다. 원래 이름을 따로 붙여 보낸다.
		builder.part("file", new FileSystemResource(file)).filename(originalFileName);
		if (!StringUtil.isEmpty(sourceId)) {
			builder.part("sourceId", sourceId);
		}
		if (!StringUtil.isEmpty(title)) {
			builder.part("title", title);
		}
		if (!StringUtil.isEmpty(projectId)) {
			builder.part("projectId", projectId);
		}
		URI uri;
		try {
			uri = uriOf("/api/documents", null);
		} catch (RejectedCallException e) {
			return error(e.status, e.getMessage());
		}
		WebClient.RequestBodySpec request = this.client().post().uri(uri);
		withApiKey(request);
		return exchange(request.contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(builder.build())));
	}

	/**
	 * <pre>
	 * dstone-knowledge를 부를 때 쓰는 WebClient입니다.
	 * WebClient는 응답을 256KB까지만 받는 것이 기본값이라, 노드 맵의 전체 맵처럼 큰 응답(수 MB)은 한도를 늘려야 받을 수 있습니다.
	 * </pre>
	 */
	private WebClient client() {
		return this.getWebClient(READ_TIMEOUT_SECONDS).mutate().codecs(new Consumer<ClientCodecConfigurer>() {
			@Override
			public void accept(ClientCodecConfigurer configurer) {
				configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES);
			}
		}).build();
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
			return error(HttpStatus.BAD_GATEWAY, "dstone-knowledge를 부르지 못했습니다. 서버가 떠 있는지, interface.knowledge.base-url이 맞는지 확인하세요: " + e.getMessage());
		}
	}

	/**
	 * <pre>
	 * 오류를 dstone-knowledge의 오류 응답과 같은 모양({status, message})으로 돌려줍니다.
	 * 예외를 던지지 않는 이유: dstone-boot의 공통 예외 처리를 타면 상태 코드가 200이 되고 메시지 모양도 달라져서,
	 * 화면이 "성공했는데 내용이 이상한 응답"으로 받게 된다. 화면은 한 가지 모양의 오류만 알면 되게 한다.
	 * </pre>
	 */
	private ResponseEntity<String> error(HttpStatus status, String message) {
		String text = String.valueOf(message).replace("\\", "/").replace("\"", "'").replace("\n", " ").replace("\r", " ");
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body("{\"status\":" + status.value() + ",\"message\":\"" + text + "\"}");
	}

	private HttpMethod methodOf(String method) throws RejectedCallException {
		String upper = method == null ? "" : method.trim().toUpperCase();
		if ("GET".equals(upper)) {
			return HttpMethod.GET;
		}
		if ("POST".equals(upper)) {
			return HttpMethod.POST;
		}
		if ("DELETE".equals(upper)) {
			return HttpMethod.DELETE;
		}
		throw new RejectedCallException(HttpStatus.BAD_REQUEST, "method는 GET / POST / DELETE 가운데 하나여야 합니다: " + method);
	}

	/**
	 * <pre>
	 * 부를 주소를 만듭니다. 경로는 /api/ 아래만 받습니다.
	 * 화면에서 넘어온 경로를 그대로 붙이는 것이라, 다른 서버나 다른 경로로 새지 않게 모양을 먼저 확인합니다.
	 * </pre>
	 */
	private URI uriOf(String path, Map<String, Object> query) throws RejectedCallException {
		String baseUrl = this.configProperty.getProperty("interface.knowledge.base-url");
		if (StringUtil.isEmpty(baseUrl)) {
			throw new RejectedCallException(HttpStatus.SERVICE_UNAVAILABLE, "dstone-knowledge 주소가 설정되지 않았습니다(interface.knowledge.base-url).");
		}
		if (path == null || !path.startsWith("/api/") || path.indexOf("..") >= 0 || path.indexOf("//") >= 0 || path.indexOf('?') >= 0 || path.indexOf('#') >= 0
				|| path.indexOf('\\') >= 0) {
			throw new RejectedCallException(HttpStatus.BAD_REQUEST, "path는 /api/로 시작하는 경로여야 합니다: " + path);
		}
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl.trim()).path(path);
		if (query != null) {
			for (Map.Entry<String, Object> entry : query.entrySet()) {
				Object value = entry.getValue();
				if (value != null && String.valueOf(value).trim().length() > 0) {
					builder.queryParam(entry.getKey(), String.valueOf(value).trim());
				}
			}
		}
		// 한글이나 빈칸이 든 값이 주소에서 깨지지 않게 인코딩한다.
		return builder.build().encode().toUri();
	}

	/** dstone-knowledge가 호출자 인증을 켜 두었을 때 쓰는 키. 설정에 없으면 붙이지 않습니다. */
	private void withApiKey(WebClient.RequestBodySpec request) {
		String apiKey = this.configProperty.getProperty("interface.knowledge.api-key");
		if (!StringUtil.isEmpty(apiKey)) {
			request.header("X-API-Key", apiKey);
		}
	}

	/** 넘겨줄 수 없는 요청(허용하지 않는 메소드나 경로, 주소 설정 없음). 메시지가 곧 화면에 보일 글이다 */
	private static class RejectedCallException extends Exception {
		private static final long serialVersionUID = 1L;

		private final HttpStatus status;

		RejectedCallException(HttpStatus status, String message) {
			super(message);
			this.status = status;
		}
	}

}
