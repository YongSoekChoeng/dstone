package net.dstone.ai.api.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import net.dstone.ai.api.dto.WorkflowRequest;
import net.dstone.ai.api.dto.WorkflowStatusResponse;
import net.dstone.ai.common.consts.Constants;
import net.dstone.ai.common.definition.WorkflowDefinition;
import net.dstone.ai.runtime.WorkflowContext;
import net.dstone.ai.runtime.WorkflowExecutor;
import net.dstone.common.annotation.NoAspectLog;
import net.dstone.common.biz.BaseService;

/**
 * Workflow는 여러 step에 걸쳐 오래 걸릴 수 있어서, submit()→jobId→status(jobId) 폴링 흐름을 제공한다 (api.controller.WorkflowController가
 * workflowId/caller를 이미 검증한 WorkflowDefinition을 넘겨준다 - 이 클래스는 "이미 확정된 작업 하나를 비동기로 돌리고 상태를 추적"하는 역할만 한다).
 *
 * Job 상태는 세션/RateLimit과 마찬가지로 Redis에 둔다 - status/result/error 세 필드를 Hash 하나에 담는다(RedisUtil이 만들어주는 RedisTemplate의 hash
 * value serializer가 GenericJackson2JsonRedisSerializer라 문자열도 문제없이 오간다 - plain opsForValue()의 value serializer는
 * StringRedisSerializer라 구조화된 값에는 안 맞는다).
 *
 * ⚠️ MVP 수준 구현 - CompletableFuture.runAsync()가 기본 ForkJoinPool.commonPool()을 그대로 쓴다. 전용 스레드풀/큐잉/동시 실행 수 제한은 없다 - 실제
 * 운영에서 동시 submit()이 많아지면 그때 전용 Executor로 교체한다.
 */
@Service
public class AsyncJobService extends BaseService {

	@Autowired(required = false)
	private RedisTemplate<String, Object> redisTemplate;
	@Autowired
	private WorkflowExecutor workflowExecutor;

	/**
	 * <pre>
	 * jobId를 즉시 돌려주고, 실제 실행은 백그라운드에서 진행한다.
	 * </pre>
	 *
	 * @param jobId     이 작업에 부여할 id
	 * @param workflow  실행할 Workflow 정의
	 * @param sessionId 대화 세션 ID
	 * @param caller    호출한 앱(tenant) 식별자
	 * @param request   Workflow 입력 메시지/변수
	 */
	public String submit(String jobId, WorkflowDefinition workflow, String sessionId, String caller, WorkflowRequest request) {
		this.requireRedis();
		this.writeState(jobId, "RUNNING", null, null);

		CompletableFuture.runAsync(new Runnable()
			{
				@Override
				public void run() {
					try {
						WorkflowContext context = AsyncJobService.this.workflowExecutor.run(workflow, sessionId, caller, request.variables(), request.message());
						AsyncJobService.this.writeState(jobId, "DONE", context.<String>get("result"), null);
					} catch (Exception e) {
						AsyncJobService.this.writeState(jobId, "FAILED", null, e.getMessage());
					}
				}
			});

		return jobId;
	}

	/** @param jobId 조회할 작업 id */
	@NoAspectLog
	public WorkflowStatusResponse status(String jobId) {
		this.requireRedis();
		Map<Object, Object> entries = this.redisTemplate.opsForHash().entries(Constants.AsyncJob.JOB_KEY_PREFIX + jobId);
		if (entries.isEmpty()) {
			throw new IllegalArgumentException("존재하지 않거나 만료된 jobId입니다: " + jobId);
		}
		return new WorkflowStatusResponse(jobId, this.stringOrNull(entries.get("status")), this.stringOrNull(entries.get("result")), this.stringOrNull(entries.get("error")));
	}

	/**
	 * @param jobId  상태를 기록할 작업 id
	 * @param status 작업 상태(RUNNING/DONE/FAILED)
	 * @param result 작업 성공 결과(없으면 null)
	 * @param error  작업 실패 사유(없으면 null)
	 */
	private void writeState(String jobId, String status, String result, String error) {
		Map<String, Object> fields = new HashMap<>();
		fields.put("status", status);
		if (result != null) {
			fields.put("result", result);
		}
		if (error != null) {
			fields.put("error", error);
		}
		String key = Constants.AsyncJob.JOB_KEY_PREFIX + jobId;
		this.redisTemplate.opsForHash().putAll(key, fields);
		this.redisTemplate.expire(key, Constants.AsyncJob.JOB_TTL_SECONDS, TimeUnit.SECONDS);
	}

	/** @param value 문자열로 변환할 Redis Hash 값 */
	private String stringOrNull(Object value) {
		return value == null ? null : value.toString();
	}

	private void requireRedis() {
		if (this.redisTemplate == null) {
			throw new IllegalStateException("비동기 Workflow API(submit/status)를 쓰려면 Redis가 필요합니다(spring.data.redis.enabled=false).");
		}
	}

}
