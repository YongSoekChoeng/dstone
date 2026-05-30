package net.dstone.common.utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.UnknownHttpStatusCodeException;

public class RestFulUtil {
	
	private static RestFulUtil restFulUtil = null;
	private HttpComponentsClientHttpRequestFactory HTTP_CLIENT_FACTORY;
	private static String DEFAULT_CHARSET = "UTF-8";
	
	private RestFulUtil() {
		init();
	}
	
	private void init() {
		try {
	        /******************************************
	        SSL 검증 비활성화 (로컬 환경에서만 사용)
	        // 모든 인증서 신뢰
	        SSLContext sslContext = SSLContextBuilder.create()
	                .loadTrustMaterial(TrustAllStrategy.INSTANCE)
	                .build();
			******************************************/
	
			/***************** 설정 시작 *****************/
			int connectTimeout = 20; 			// 연결시도제한시간. 클라이언트가 서버에 TCP 연결을 시도할 때 걸리는 최대 시간을 의미. 서버가 아예 응답을 하지 않거나, DNS 해석이나 네트워크 문제가 있을 때 이 시간이 초과되면 예외가 발생.
			int readTimeout = 30;				// 응답대기시간. 서버와 연결이 된 이후, 응답 바디를 읽는 데 걸리는 최대 시간. 즉, 서버가 요청을 받았지만 응답을 늦게 보내거나 멈춘 경우 이 시간 안에 응답이 도착하지 않으면 예외가 발생.
			int connectionRequestTimeout = 20;	// 커넥션풀에서연결얻는시간. 커넥션 풀을 사용하는 경우, 풀에서 커넥션을 얻기 위해 기다리는 최대 시간.
			
			PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
			connManager.setMaxTotal(100); // 최대로 연결할 수 있는 커넥션 쓰레드 수
			connManager.setDefaultMaxPerRoute(60); // (IP + PORT) 당 커넥션 쓰레드 수
			connManager.closeIdle(TimeValue.of(60, TimeUnit.SECONDS)); // 최대 연결 유효시간을 지정한다
			
			RequestConfig requestConfig = RequestConfig.custom()
	        .setConnectionRequestTimeout(connectTimeout, TimeUnit.SECONDS) //  HttpClient가 커넥션 풀에서 사용 가능한 연결을 가져오기 위해 대기하는 최대 시간을 지정하는 옵션. 커넥션 풀에서 사용 가능한 연결이 없을 때, 요청은 커넥션 풀에 새로운 연결이 생성될 때까지 해당 시간만큼 대기.
	        .setResponseTimeout(readTimeout, TimeUnit.SECONDS) // 응답 타임아웃 (read timeout)
	        .build();

	        CloseableHttpClient httpClient = HttpClients.custom()
	        .setConnectionManager(connManager)
	        .setDefaultRequestConfig(requestConfig)
	        .build();
			
			HTTP_CLIENT_FACTORY = new HttpComponentsClientHttpRequestFactory();
			HTTP_CLIENT_FACTORY.setConnectTimeout(connectTimeout);
			HTTP_CLIENT_FACTORY.setHttpClient(httpClient);
			/***************** 설정 종료 *****************/
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static RestFulUtil getInstance() {
		if( restFulUtil == null ) {
			restFulUtil = new RestFulUtil();
		}
		return restFulUtil;
	}
	
	public RestTemplate getRestTemplate() {
		int connectTimeout = 20; 			// 연결시도제한시간. 클라이언트가 서버에 TCP 연결을 시도할 때 걸리는 최대 시간을 의미. 서버가 아예 응답을 하지 않거나, DNS 해석이나 네트워크 문제가 있을 때 이 시간이 초과되면 예외가 발생.
		int readTimeout = 30;				// 응답대기시간. 서버와 연결이 된 이후, 응답 바디를 읽는 데 걸리는 최대 시간. 즉, 서버가 요청을 받았지만 응답을 늦게 보내거나 멈춘 경우 이 시간 안에 응답이 도착하지 않으면 예외가 발생.
		int connectionRequestTimeout = 20;	// 커넥션풀에서연결얻는시간. 커넥션 풀을 사용하는 경우, 풀에서 커넥션을 얻기 위해 기다리는 최대 시간.
		return this.getRestTemplate(DEFAULT_CHARSET, connectTimeout, readTimeout, connectionRequestTimeout);
	}
	
	/**
	 * RestTemplate 을 얻기위한 메소드.
	 * @param charSet 캐릭터셋.<br>
	 * @param connectTimeout 연결시도제한시간. 클라이언트가 서버에 TCP 연결을 시도할 때 걸리는 최대 시간을 의미. 서버가 아예 응답을 하지 않거나, DNS 해석이나 네트워크 문제가 있을 때 이 시간이 초과되면 예외가 발생.<br>
	 * @param readTimeout 응답대기시간. 서버와 연결이 된 이후, 응답 바디를 읽는 데 걸리는 최대 시간. 즉, 서버가 요청을 받았지만 응답을 늦게 보내거나 멈춘 경우 이 시간 안에 응답이 도착하지 않으면 예외가 발생.<br>
	 * @param connectionRequestTimeout 커넥션풀에서연결얻는시간. 커넥션 풀을 사용하는 경우, 풀에서 커넥션을 얻기 위해 기다리는 최대 시간.<br>
	 * @return
	 */
	public RestTemplate getRestTemplate(String charSet, int connectTimeout, int readTimeout, int connectionRequestTimeout) {
		
		RestTemplate restTemplate = null;
		
		/***************** 설정 시작 *****************/
		PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
		connManager.setMaxTotal(100); // 최대로 연결할 수 있는 커넥션 쓰레드 수
		connManager.setDefaultMaxPerRoute(60); // (IP + PORT) 당 커넥션 쓰레드 수
		connManager.closeIdle(TimeValue.of(60, TimeUnit.SECONDS)); // 최대 연결 유효시간을 지정한다
		
		RequestConfig requestConfig = RequestConfig.custom()
        .setConnectionRequestTimeout(connectTimeout, TimeUnit.SECONDS) //  HttpClient가 커넥션 풀에서 사용 가능한 연결을 가져오기 위해 대기하는 최대 시간을 지정하는 옵션. 커넥션 풀에서 사용 가능한 연결이 없을 때, 요청은 커넥션 풀에 새로운 연결이 생성될 때까지 해당 시간만큼 대기.
        .setResponseTimeout(readTimeout, TimeUnit.SECONDS) // 응답 타임아웃 (read timeout)
        .build();

        CloseableHttpClient httpClient = HttpClients.custom()
        .setConnectionManager(connManager)
        .setDefaultRequestConfig(requestConfig)
        .build();
		
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setHttpClient(httpClient);

        restTemplate = new RestTemplate(factory);
		/***************** 설정 종료 *****************/
		
		for (HttpMessageConverter<?> messageConverter : restTemplate.getMessageConverters()) {
			if (messageConverter instanceof AllEncompassingFormHttpMessageConverter) {
				((AllEncompassingFormHttpMessageConverter) messageConverter).setCharset(Charset.forName("UTF-8"));
				((AllEncompassingFormHttpMessageConverter) messageConverter).setMultipartCharset(Charset.forName("UTF-8"));
			}
		}
		RestResponseErrorHandler errorHandler = new RestResponseErrorHandler();
		errorHandler.setThrowExceptionOnError(false);
		restTemplate.setErrorHandler(errorHandler);
		
		return restTemplate;
	}
	
	public HttpHeaders getHeader(MediaType mediaType, Map<String, String> header) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(new MediaType[] { mediaType }));
		headers.setContentType(mediaType);
		if( header != null && header.size() >0 ) {
			Iterator<String> keys = header.keySet().iterator();
			while(keys.hasNext()) {
				String key = keys.next();
				headers.add(key, header.get(key));
			}
		}
		return headers;
	}
	
	public HttpEntity<String> getEntity(HttpHeaders headers, String input) {
		HttpEntity<String> entity = new HttpEntity<String>(input, headers);
		return entity;
	}

	public HttpEntity<MultiValueMap<String, Object>> getEntity(HttpHeaders headers, LinkedMultiValueMap input) {
		HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<MultiValueMap<String, Object>>(input, headers);
		return entity;
	}
	

	class RestResponseErrorHandler extends DefaultResponseErrorHandler {
		
		private boolean isOk = true;
		private Map<String, Object> msgMap = new HashMap<String, Object>();
		private boolean isThrowExceptionOnError = true;
		
		@Override
		public void handleError(ClientHttpResponse response) throws IOException {
			//logger.info( "handleError :: response.getRawStatusCode()================>>>" + response.getRawStatusCode() );
			if (response.getStatusCode().value() != 520) {
				HttpStatusCode statusCode = response.getStatusCode();
		        if (statusCode == HttpStatus.OK) {
		            // 성공 (200 OK)
		        } else if (statusCode.is4xxClientError()) {
		            // 클라이언트 에러 (4xx)
		        	throw new HttpClientErrorException(statusCode, response.getStatusText(), response.getHeaders(), getResponseBody(response), getCharset(response));
		        } else if (statusCode.is5xxServerError()) {
		            // 서버 에러 (5xx)
		        	throw new HttpServerErrorException(statusCode, response.getStatusText(), response.getHeaders(), getResponseBody(response), getCharset(response));
		        }
			}else {
				throw new UnknownHttpStatusCodeException(520, response.getStatusText(), response.getHeaders(), getResponseBody(response), getCharset(response));
			}
		}

		public boolean isOk() {
			return isOk;
		}

		public void setOk(boolean isOk) {
			this.isOk = isOk;
		}

		public Map<String, Object> getMsgMap() {
			return msgMap;
		}

		public void setMsgMap(Map<String, Object> msgMap) {
			this.msgMap = msgMap;
		}

		public boolean isThrowExceptionOnError() {
			return isThrowExceptionOnError;
		}

		public void setThrowExceptionOnError(boolean isThrowExceptionOnError) {
			this.isThrowExceptionOnError = isThrowExceptionOnError;
		}
		
	}
	
}
