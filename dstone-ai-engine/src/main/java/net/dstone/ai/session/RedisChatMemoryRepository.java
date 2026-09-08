package net.dstone.ai.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.dstone.common.core.BaseObject;

/**
 * Spring AI의 {@link ChatMemoryRepository} SPI를 dstone-common의 Redis 인프라
 * (net.dstone.ai.config.ConfigRedis가 제공하는 RedisTemplate)로 구현한다.
 * Spring AI가 공식으로 제공하는 chat-memory-repository는 jdbc/cassandra/neo4j뿐이라
 * (Redis는 없음, 2026-09 기준 spring-ai 1.1.8) 이 저장소는 직접 구현한 것이다.
 *
 * 대화 하나당 Redis List 하나(키: dstone:ai:session:{conversationId})에 메시지를
 * JSON으로 순서대로 저장하고, 존재하는 conversationId 목록은 별도 Set(키:
 * dstone:ai:session:index)으로 관리한다(KEYS/SCAN으로 전체를 훑지 않기 위함).
 */
@Repository
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
public class RedisChatMemoryRepository extends BaseObject implements ChatMemoryRepository {

	private static final String KEY_PREFIX = "dstone:ai:session:";
	private static final String INDEX_KEY = KEY_PREFIX + "index";

	private final RedisTemplate<String, Object> redisTemplate;
	private final ObjectMapper objectMapper;

	@Value("${dstone.ai.session.ttl-seconds:86400}")
	private long ttlSeconds;

	public RedisChatMemoryRepository(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	/** Message를 Redis에 저장하기 위한 최소 표현. Jackson record 지원(컴파일러 -parameters 옵션) 기반으로 직렬화/역직렬화한다. */
	private record StoredMessage(String type, String text) {
	}

	@Override
	public List<String> findConversationIds() {
		Set<Object> members = this.redisTemplate.opsForSet().members(INDEX_KEY);
		if (members == null) {
			return List.of();
		}
		return members.stream().map(Object::toString).collect(Collectors.toList());
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		List<Object> raw = this.redisTemplate.opsForList().range(conversationKey(conversationId), 0, -1);
		if (raw == null || raw.isEmpty()) {
			return List.of();
		}
		List<Message> messages = new ArrayList<>(raw.size());
		for (Object entry : raw) {
			messages.add(toMessage((String) entry));
		}
		return messages;
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		String key = conversationKey(conversationId);
		this.redisTemplate.delete(key);
		if (messages != null && !messages.isEmpty()) {
			List<Object> serialized = messages.stream().map(this::toJson).collect(Collectors.toList());
			this.redisTemplate.opsForList().rightPushAll(key, serialized);
			this.redisTemplate.expire(key, this.ttlSeconds, TimeUnit.SECONDS);
			this.redisTemplate.opsForSet().add(INDEX_KEY, conversationId);
		}
		else {
			this.redisTemplate.opsForSet().remove(INDEX_KEY, conversationId);
		}
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		this.redisTemplate.delete(conversationKey(conversationId));
		this.redisTemplate.opsForSet().remove(INDEX_KEY, conversationId);
	}

	private String conversationKey(String conversationId) {
		return KEY_PREFIX + conversationId;
	}

	private String toJson(Message message) {
		try {
			return this.objectMapper
				.writeValueAsString(new StoredMessage(message.getMessageType().name(), message.getText()));
		}
		catch (Exception e) {
			throw new IllegalStateException("대화 메시지를 JSON으로 직렬화하지 못했습니다: " + message.getMessageType(), e);
		}
	}

	private Message toMessage(String json) {
		StoredMessage stored;
		try {
			stored = this.objectMapper.readValue(json, StoredMessage.class);
		}
		catch (Exception e) {
			throw new IllegalStateException("Redis에 저장된 대화 메시지를 역직렬화하지 못했습니다: " + json, e);
		}
		MessageType type = MessageType.valueOf(stored.type());
		return switch (type) {
			case USER -> new UserMessage(stored.text());
			case ASSISTANT -> new AssistantMessage(stored.text());
			case SYSTEM -> new SystemMessage(stored.text());
			case TOOL -> throw new IllegalStateException(
					"TOOL 타입 메시지는 아직 지원하지 않는다(Phase 3 agent/tool-calling에서 다룰 예정): " + json);
		};
	}

}
