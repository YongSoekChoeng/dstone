package net.dstone.ai.api.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import net.dstone.ai.api.dto.ChatRequest;
import net.dstone.ai.api.dto.ProcessStatusResponse;
import net.dstone.common.biz.BaseService;
import net.dstone.common.config.ConfigProperty;

/**
 * Phase 10 — 비동기 API 계약. 기존 POST /api/ai/chat(+/stream)은 그대로 동기 execute() 계약을
 * 유지하고, 여러 step에 걸쳐 오래 걸릴 수 있는 호출(특히 process 기반 capability)을 위해 이 클래스가
 * submit()→jobId→getStatus(jobId) 폴링 흐름을 담당한다. capability가 process 기반인지 단일 호출인지는
 * 신경 쓰지 않는다 - ChatService.chat()이 이미 그 라우팅을 알아서 하므로, 이 클래스는 그 호출을
 * 비동기로 감싸기만 한다.
 *
 * Job 상태는 session(Phase 1)/ratelimit(Phase 4)과 마찬가지로 Redis에 둔다 - jobId별로 다른 요청과
 * 섞이지 않게 하는 것 외에는 별다른 이유가 없어서, 새 인프라 없이 이미 있는 Redis를 그대로 쓴다.
 * status/result/error 세 필드를 Hash 하나에 담는다(RedisUtil이 만들어주는 RedisTemplate의 hash
 * value serializer가 GenericJackson2JsonRedisSerializer라 문자열도 문제없이 오간다 - plain
 * opsForValue()의 value serializer는 StringRedisSerializer라 구조화된 값에는 안 맞는다).
 *
 * ⚠️ MVP 수준 구현 - CompletableFuture.runAsync()가 기본 ForkJoinPool.commonPool()을 그대로 쓴다.
 * 전용 스레드풀/큐잉/동시 실행 수 제한은 없다(common.filter.RateLimitFilter가 "구현 단순성을 택했다"고
 * 밝힌 것과 같은 방향) - 실제 운영에서 동시 submit()이 많아지면 그때 전용 Executor로 교체한다.
 */
@Service
public class ProcessJobService extends BaseService {

	private static final String JOB_KEY_PREFIX = "dstone:ai:process:job:";
	private static final long JOB_TTL_SECONDS = 3600L; // 폴링이 끝난 결과를 계속 남겨둘 이유가 없어 1시간 후 자동 소멸

	@Autowired(required = false)
	private RedisTemplate<String, Object> redisTemplate;
	@Autowired
	private ChatService chatService;
	@Autowired
	private ConfigProperty configProperty;

	/** jobId를 즉시 돌려주고, 실제 실행은 백그라운드에서 진행한다. */
	public String submit(String caller, ChatRequest request) {
		this.requireRedis();
		String jobId = UUID.randomUUID().toString();
		String sessionId = UUID.randomUUID().toString();
		this.writeState(jobId, "RUNNING", null, null);

		CompletableFuture.runAsync(() -> {
			try {
				String providerId = this.configProperty.getProperty("spring.ai.model.chat");
				String result = this.chatService.chat(sessionId, caller, providerId, request);
				this.writeState(jobId, "DONE", result, null);
			}
			catch (Exception e) {
				this.writeState(jobId, "FAILED", null, e.getMessage());
			}
		});

		return jobId;
	}

	public ProcessStatusResponse status(String jobId) {
		this.requireRedis();
		Map<Object, Object> entries = this.redisTemplate.opsForHash().entries(JOB_KEY_PREFIX + jobId);
		if (entries.isEmpty()) {
			throw new IllegalArgumentException("존재하지 않거나 만료된 jobId입니다: " + jobId);
		}
		return new ProcessStatusResponse(jobId, this.stringOrNull(entries.get("status")),
			this.stringOrNull(entries.get("result")), this.stringOrNull(entries.get("error")));
	}

	private void writeState(String jobId, String status, String result, String error) {
		Map<String, Object> fields = new HashMap<>();
		fields.put("status", status);
		if (result != null) {
			fields.put("result", result);
		}
		if (error != null) {
			fields.put("error", error);
		}
		String key = JOB_KEY_PREFIX + jobId;
		this.redisTemplate.opsForHash().putAll(key, fields);
		this.redisTemplate.expire(key, JOB_TTL_SECONDS, TimeUnit.SECONDS);
	}

	private String stringOrNull(Object value) {
		return value == null ? null : value.toString();
	}

	private void requireRedis() {
		if (this.redisTemplate == null) {
			throw new IllegalStateException("비동기 Process API(submit/status)를 쓰려면 Redis가 필요합니다(spring.data.redis.enabled=false).");
		}
	}

}
