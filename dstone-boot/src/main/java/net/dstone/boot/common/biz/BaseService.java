package net.dstone.boot.common.biz;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import net.dstone.common.utils.RestFulUtil;

@Service
public class BaseService extends net.dstone.common.biz.BaseService {

	/*** 외부 인터페이스를 위하 RestTemplate 관련 기능 시작 ***/
	
	protected RestTemplate getRestTemplate() {
		return RestFulUtil.getInstance().getRestTemplate();
	}
	
	protected HttpEntity<String> getEntity( MediaType mediaType, String input){
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(new MediaType[] { mediaType }));
		headers.setContentType(mediaType);
		HttpEntity<String> entity = new HttpEntity<String>(input, headers);
		return entity;
	}

	protected HttpEntity<String> getEntity( MediaType mediaType, Map<String, String> header, String input){
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
		HttpEntity<String> entity = new HttpEntity<String>(input, headers);
		return entity;
	}
	
	/*** 외부 인터페이스를 위하 RestTemplate 관련 기능 끝 ***/

	/** 큰 응답을 받을 때 쓰는 한도입니다(32MB). */
	protected static final int LARGE_RESPONSE_BYTES = 32 * 1024 * 1024;

	/**
	 * <pre>
	 * 큰 응답도 받을 수 있는 WebClient입니다.
	 * WebClient는 응답을 256KB까지만 받는 것이 기본값이라, 그보다 큰 응답은 "Exceeded limit on max bytes to buffer" 에러가 납니다.
	 * Workflow 실행 상세처럼 응답이 커질 수 있는 연동은 이 메소드로 얻은 WebClient를 씁니다.
	 * </pre>
	 */
	protected WebClient getLargeResponseWebClient() {
		return this.getWebClient().mutate().codecs(new Consumer<ClientCodecConfigurer>() {
			@Override
			public void accept(ClientCodecConfigurer configurer) {
				configurer.defaultCodecs().maxInMemorySize(LARGE_RESPONSE_BYTES);
			}
		}).build();
	}
	

}
