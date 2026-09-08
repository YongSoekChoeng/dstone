/**
 * 대화 세션/히스토리 — dstone-common의 Redis 인프라 재사용(Phase 1).
 *
 * Spring AI가 공식 제공하는 ChatMemoryRepository는 jdbc/cassandra/neo4j뿐이라(Redis는 없음,
 * 2026-09 기준 spring-ai 1.1.8) {@link net.dstone.ai.session.RedisChatMemoryRepository}를
 * 직접 구현했다. net.dstone.ai.config.ConfigRedis가 제공하는 RedisTemplate을 그대로 쓰고,
 * net.dstone.ai.config.ConfigChatMemory가 이를 windowing된 ChatMemory로 감싸 ChatClient의
 * 기본 advisor로 붙인다(호출부는 sessionId를 ChatMemory.CONVERSATION_ID 파라미터로만 넘기면 됨).
 */
package net.dstone.ai.session;
