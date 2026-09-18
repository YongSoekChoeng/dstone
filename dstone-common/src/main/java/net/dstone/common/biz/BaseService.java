package net.dstone.common.biz;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import net.dstone.common.utils.RestFulUtil;
import net.dstone.common.utils.WcUtil;

@Service
public abstract class BaseService extends net.dstone.common.core.BaseObject {

	/*** 외부 인터페이스를 위하 RestTemplate 관련 기능 시작 ***/

	protected RestTemplate getRestTemplate() {
		return RestFulUtil.getInstance().getRestTemplate();
	}

	/** RestTemplate로는 다룰 수 없는 스트리밍(SSE) 연동에 사용. 응답대기시간 기본 60초. */
	protected WebClient getWebClient() {
		return WcUtil.getInstance().getWebClient();
	}

	/** 문서 적재처럼 60초보다 오래 걸릴 수 있는 연동을 위한 오버로드. */
	protected WebClient getWebClient(int readTimeoutSeconds) {
		return WcUtil.getInstance().getWebClient(readTimeoutSeconds);
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
	

}
