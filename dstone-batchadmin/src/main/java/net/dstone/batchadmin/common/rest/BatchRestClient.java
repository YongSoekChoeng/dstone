package net.dstone.batchadmin.common.rest;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.RestFulUtil;

/**
 * dstone-batch의 RestApiRunner(net.dstone.batch.common.runner.RestApiRunner, "/batch/**") 를 호출하는 클라이언트.
 * 대상 서버는 TB_BATCH_SERVER.REST_BASE_URL (예: http://localhost:6081/batch) 이다.
 */
@Component
public class BatchRestClient extends BaseObject {

	private RestTemplate getRestTemplate() {
		return RestFulUtil.getInstance().getRestTemplate();
	}

	private Map<String, Object> exchange(String url, HttpMethod method) {
		try {
			ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(url, method, HttpEntity.EMPTY,
					new ParameterizedTypeReference<Map<String, Object>>() {
					});
			return response.getBody();
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".exchange(" + url + ") 호출중 예외발생. 상세사항:" + e.toString());
			Map<String, Object> errorMap = new HashMap<String, Object>();
			errorMap.put("success", "N");
			errorMap.put("message", e.toString());
			return errorMap;
		}
	}

	private Map<String, Object> exchange(URI uri, HttpMethod method) {
		try {
			ResponseEntity<Map<String, Object>> response = getRestTemplate().exchange(uri, method, HttpEntity.EMPTY,
					new ParameterizedTypeReference<Map<String, Object>>() {
					});
			return response.getBody();
		} catch (Exception e) {
			this.error(this.getClass().getName() + ".exchange(" + uri + ") 호출중 예외발생. 상세사항:" + e.toString());
			Map<String, Object> errorMap = new HashMap<String, Object>();
			errorMap.put("success", "N");
			errorMap.put("message", e.toString());
			return errorMap;
		}
	}

	public Map<String, Object> healthCheck(String restBaseUrl) {
		return exchange(restBaseUrl + "/healthCheck", HttpMethod.GET);
	}

	public Map<String, Object> getJobs(String restBaseUrl) {
		return exchange(restBaseUrl + "/getJobs", HttpMethod.GET);
	}

	public Map<String, Object> registerJob(String restBaseUrl, String jobName) {
		return exchange(restBaseUrl + "/registerJob/" + jobName, HttpMethod.POST);
	}

	public Map<String, Object> unregisterJob(String restBaseUrl, String jobName) {
		return exchange(restBaseUrl + "/unregisterJob/" + jobName, HttpMethod.POST);
	}

	/**
	 * @param params Job 등록시 저장된 실행파라메터(TB_BATCH_JOB_PARAM). 값은 쿼리파라메터로 percent-encoding되어 전달된다.
	 */
	public Map<String, Object> startJob(String restBaseUrl, String jobName, Map<String, Object> params) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(restBaseUrl + "/startJob/" + jobName);
		if (params != null) {
			for (Map.Entry<String, Object> entry : params.entrySet()) {
				builder.queryParam(entry.getKey(), entry.getValue());
			}
		}
		URI uri = builder.build().encode().toUri();
		return exchange(uri, HttpMethod.POST);
	}

	public Map<String, Object> stopJob(String restBaseUrl, Long jobExecutionId) {
		return exchange(restBaseUrl + "/stopJob/" + jobExecutionId, HttpMethod.POST);
	}

	public Map<String, Object> statusJob(String restBaseUrl, Long jobExecutionId) {
		return exchange(restBaseUrl + "/statusJob/" + jobExecutionId, HttpMethod.GET);
	}

	public Map<String, Object> restartJob(String restBaseUrl, Long jobExecutionId) {
		return exchange(restBaseUrl + "/restartJob/" + jobExecutionId, HttpMethod.POST);
	}

	public Map<String, Object> abandonJob(String restBaseUrl, Long jobExecutionId) {
		return exchange(restBaseUrl + "/abandonJob/" + jobExecutionId, HttpMethod.POST);
	}

	public Map<String, Object> deleteJob(String restBaseUrl, Long jobExecutionId) {
		return exchange(restBaseUrl + "/deleteJob/" + jobExecutionId, HttpMethod.POST);
	}

	public Map<String, Object> deleteJobInstance(String restBaseUrl, Long jobInstanceId) {
		return exchange(restBaseUrl + "/deleteJobInstance/" + jobInstanceId, HttpMethod.POST);
	}

}
