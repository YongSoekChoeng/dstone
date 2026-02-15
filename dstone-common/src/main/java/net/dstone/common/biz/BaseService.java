package net.dstone.common.biz;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import net.dstone.common.utils.RestFulUtil;

@Service
public abstract class BaseService extends net.dstone.common.core.BaseObject {

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
	

}
