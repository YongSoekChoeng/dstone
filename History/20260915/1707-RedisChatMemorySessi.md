# RedisChatMemorySessi

> 생성일시: 2026-09-15 17:07

## 질문

RedisChatMemorySession 가 사용되고 있는게 맞나?

---

## 답변

네, 실제로 사용되고 있음.

**연결 경로**
1. `conf/application.yml`에 `spring.data.redis.enabled: true` (line 32) → `ConfigRedis`가 `@ConditionalOnProperty`로 같은 조건일 때 `RedisTemplate` 빈 등록
2. `RedisChatMemorySession`도 동일한 `@ConditionalOnProperty(spring.data.redis.enabled=true)`로 `@Repository` 빈 등록됨 (`ChatMemoryRepository` 구현체)
3. `ConfigChatClient.chatMemoryRepository()`는 `@ConditionalOnMissingBean(ChatMemoryRepository.class)`라서, Redis 빈이 있으면 이 fallback(`InMemoryChatMemoryRepository`)은 등록되지 않고 `RedisChatMemorySession`이 선택됨
4. 그 `ChatMemoryRepository`가 `chatMemory()` 빈(`MessageWindowChatMemory`)에 주입되고, 다시 `chatClient()` 빈에서 `MessageChatMemoryAdvisor`로 붙음
5. `AgentExecutor.java:92,98`에서 이 공유 `ChatClient`로 프롬프트를 만들 때 `ChatMemory.CONVERSATION_ID` 파라미터(`sessionId`)를 넘기며 실제 호출에 사용됨

즉 현재 설정(`enabled: true`) 기준으로는 in-memory fallback이 아니라 `RedisChatMemorySession`이 실제로 활성화된 구현체가 맞습니다. `spring.data.redis.enabled: false`로 바뀌면 자동으로 `InMemoryChatMemoryRepository`로 대체됩니다.
