package net.dstone.ai.common.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.ObjectMapper;

import net.dstone.ai.common.consts.Constants;
import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;
import net.dstone.common.utils.StringUtil;

/**
 * 대화 내용을 Redis에 저장하는 클래스입니다. Spring AI가 정의한 ChatMemoryRepository라는
 * 인터페이스(SPI)를, dstone-common의 Redis 인프라(common.config.ConfigRedis가 만들어 주는
 * RedisTemplate)를 이용해 직접 구현했습니다. Spring AI가 공식으로 제공하는
 * chat-memory-repository 구현체는 jdbc/cassandra/neo4j용뿐이고 Redis용은 없어서, 이 클래스를
 * 직접 만들었습니다.
 *
 * 대화 하나마다 Redis List를 하나씩 씁니다(키: dstone:ai:session:{conversationId}). 그 List
 * 안에는 메시지들이 JSON 형태로 순서대로 쌓입니다. 그리고 지금까지 만들어진 conversationId
 * 목록은 따로 Set(키: dstone:ai:session:index)으로 관리합니다. 이렇게 목록을 따로 관리해 두면,
 * 전체 키를 다 훑어야 하는 KEYS나 SCAN 명령 없이도 목록 조회를 바로 할 수 있습니다.
 */
@Repository
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
public class RedisChatMemorySession extends BaseObject implements ChatMemoryRepository {

	private final RedisTemplate<String, Object> redisTemplate;
	private final ObjectMapper objectMapper;
	private final long ttlSeconds;

	/**
	 * dstone.ai.session.ttl-seconds 설정값을 읽어서 대화 만료 시간(TTL)을 정합니다. 설정이
	 * 없으면 하루(86400초)를 기본값으로 씁니다.
	 *
	 * @param redisTemplate  Redis 접근용 템플릿
	 * @param objectMapper   메시지 직렬화/역직렬화에 쓰는 JSON 매퍼
	 * @param configProperty 설정값 조회 유틸
	 */
	public RedisChatMemorySession(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper, ConfigProperty configProperty) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		String ttlSeconds = configProperty.getProperty("dstone.ai.session.ttl-seconds");
		this.ttlSeconds = StringUtil.isEmpty(ttlSeconds) ? 86400L : Long.parseLong(ttlSeconds);
	}

	/**
	 * Message를 Redis에 저장하기 위해 만든 최소한의 표현입니다. Jackson이 record를 바로
	 * 직렬화/역직렬화할 수 있는 기능(컴파일러의 -parameters 옵션 덕분에 동작함)을 그대로
	 * 활용합니다.
	 *
	 * @param type 메시지 종류(USER/ASSISTANT/SYSTEM 등)
	 * @param text 메시지 본문
	 */
	private record StoredMessage(String type, String text) {
	}

	/** 지금까지 저장된 대화들의 conversationId 목록을 전부 돌려줍니다. */
	@Override
	public List<String> findConversationIds() {
		Set<Object> members = this.redisTemplate.opsForSet().members(Constants.Session.INDEX_KEY);
		if (members == null) {
			return List.of();
		}
		List<String> conversationIds = new ArrayList<>(members.size());
		for (Object member : members) {
			conversationIds.add(member.toString());
		}
		return conversationIds;
	}

	/**
	 * 이 conversationId로 저장된 메시지 전체를 순서대로 읽어 옵니다. 저장된 게 없으면 빈
	 * 목록을 돌려줍니다.
	 *
	 * @param conversationId 대화 세션 식별자
	 */
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

	/**
	 * 이 conversationId의 메시지를 전부 지우고, 넘겨받은 messages로 다시 채웁니다. messages가
	 * 비어 있으면 그 대화를 목록(index)에서도 제거합니다. 메시지를 새로 저장할 때는 만료 시간
	 * (TTL)도 함께 갱신해서, 오래 쓰지 않는 대화가 Redis에 계속 남아있지 않게 합니다.
	 *
	 * @param conversationId 대화 세션 식별자
	 * @param messages       저장할 대화 메시지 목록
	 */
	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		String key = conversationKey(conversationId);
		this.redisTemplate.delete(key);
		if (messages != null && !messages.isEmpty()) {
			List<Object> serialized = new ArrayList<>(messages.size());
			for (Message message : messages) {
				serialized.add(this.toJson(message));
			}
			this.redisTemplate.opsForList().rightPushAll(key, serialized);
			this.redisTemplate.expire(key, this.ttlSeconds, TimeUnit.SECONDS);
			this.redisTemplate.opsForSet().add(Constants.Session.INDEX_KEY, conversationId);
		} else {
			this.redisTemplate.opsForSet().remove(Constants.Session.INDEX_KEY, conversationId);
		}
	}

	/**
	 * 이 conversationId의 대화를 완전히 지웁니다(저장된 메시지와 목록 항목 둘 다 제거).
	 *
	 * @param conversationId 대화 세션 식별자
	 */
	@Override
	public void deleteByConversationId(String conversationId) {
		this.redisTemplate.delete(conversationKey(conversationId));
		this.redisTemplate.opsForSet().remove(Constants.Session.INDEX_KEY, conversationId);
	}

	/**
	 * 이 conversationId에 해당하는 대화가 저장될 실제 Redis 키를 만들어 줍니다.
	 *
	 * @param conversationId 대화 세션 식별자
	 */
	private String conversationKey(String conversationId) {
		return Constants.Session.KEY_PREFIX + conversationId;
	}

	/**
	 * Message 하나를 StoredMessage로 바꿔서 JSON 문자열로 직렬화합니다.
	 *
	 * @param message 직렬화할 메시지
	 */
	private String toJson(Message message) {
		try {
			return this.objectMapper.writeValueAsString(new StoredMessage(message.getMessageType().name(), message.getText()));
		} catch (Exception e) {
			throw new IllegalStateException("대화 메시지를 JSON으로 직렬화하지 못했습니다: " + message.getMessageType(), e);
		}
	}

	/**
	 * JSON 문자열을 StoredMessage로 역직렬화한 뒤, 실제 메시지 타입(UserMessage 등)의
	 * 객체로 되돌려 놓습니다.
	 *
	 * @param json 역직렬화할 JSON 문자열
	 */
	private Message toMessage(String json) {
		StoredMessage stored;
		try {
			stored = this.objectMapper.readValue(json, StoredMessage.class);
		} catch (Exception e) {
			throw new IllegalStateException("Redis에 저장된 대화 메시지를 역직렬화하지 못했습니다: " + json, e);
		}
		MessageType type = MessageType.valueOf(stored.type());
		return switch (type) {
			case USER -> new UserMessage(stored.text());
			case ASSISTANT -> new AssistantMessage(stored.text());
			case SYSTEM -> new SystemMessage(stored.text());
			// tool-calling 자체는 이미 구현되어 있지만, 그 결과 메시지를 Redis에 직렬화해서
			// 저장하는 부분은 아직 처리하지 않았기 때문에 TOOL 타입은 지원하지 않습니다.
			case TOOL -> throw new IllegalStateException("TOOL 타입 메시지는 아직 지원하지 않습니다: " + json);
		};
	}

}
