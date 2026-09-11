# netdstoneaiapicontro

> 생성일시: 2026-09-11 11:35

## 질문

net.dstone.ai.api.controller.ChatController.ChatController 에서 
net.dstone.ai.gateway.GatewayProperties 는 
이미 값이 채워진 것 처럼 사용되고 있는데
언제 값이 채워지는지?

---

## 답변

`GatewayProperties.provider`는 **Spring 컨테이너가 빈을 생성하는 시점**, 정확히는 `@PostConstruct` 메서드 `validate()`(GatewayProperties.java:31-39)에서 채워집니다.

순서:
1. 애플리케이션 기동 시 Spring이 `GatewayProperties` 빈을 생성하면서 `configProperty`(`ConfigProperty`)를 먼저 `@Autowired`로 주입.
2. 의존성 주입이 끝나면 Spring이 자동으로 `@PostConstruct` 메서드 `validate()`를 호출 → `spring.ai.model.chat` 값을 읽어 `AiProvider`로 변환해 `this.provider`에 세팅(비어있으면 그 자리에서 `IllegalStateException`으로 기동 중단).
3. `ChatController`는 생성자 주입(`ChatController(ChatClient, GatewayProperties, ...)`)으로 `GatewayProperties`를 받는데, Spring은 의존 빈(`GatewayProperties`)의 초기화(생성자 → `@Autowired` → `@PostConstruct`)를 **완전히 끝낸 뒤**에야 그 인스턴스를 의존하는 빈(`ChatController`)의 생성자에 넘겨줍니다.

즉 `ChatController` 생성자가 실행되는 시점엔 이미 `gatewayProperties.provider`가 채워진 상태이고, 이후 `resolveChatClient()`(ChatController.java:191)에서 `activeProvider()`를 호출해도 항상 값이 있다고 가정할 수 있는 겁니다. 별도의 setter 호출이나 지연 초기화 로직은 없고, 순전히 Spring 빈 생명주기(생성자 주입은 의존 빈의 `@PostConstruct`까지 끝난 걸 보장)에 의존하는 구조입니다.

---

## 질문

net.dstone.ai.api.controller.ChatController.ChatController 에서 
ObjectProvider<RetrievalService> retrievalServiceProvider 는 
이미 값이 채워진 것 처럼 사용되고 있는데
언제 값이 채워지는지?

---

## 답변

`retrievalServiceProvider`는 앞서 물어본 `GatewayProperties`와는 채워지는 방식 자체가 다릅니다.

**`GatewayProperties`**는 항상 존재하는 필수 싱글턴 빈이라, 생성 시점(`@PostConstruct`)에 즉시 값이 채워지고 그 결과(인스턴스 자체)가 `ChatController` 생성자에 주입됩니다.

**`RetrievalService`**는 `RetrievalService.java:24-25`에 보이듯 `@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true")`가 붙어 있어서 **RAG가 꺼져 있으면 빈 자체가 아예 존재하지 않습니다.** 그래서 `ChatController`가 이걸 평범한 생성자 파라미터(`RetrievalService retrievalService`)로 받으면 RAG 비활성 환경에서는 `NoSuchBeanDefinitionException`으로 기동이 실패합니다.

이걸 피하려고 `ObjectProvider<RetrievalService>`로 받는 건데, 핵심은:

1. **생성자 주입 시점**엔 `ObjectProvider` 자체(빈을 찾아주는 핸들/프록시)만 주입됩니다. 이건 대상 빈(`RetrievalService`)이 존재하든 안 하든 Spring이 항상 만들어 줄 수 있어서, RAG on/off와 무관하게 `ChatController` 생성이 절대 실패하지 않습니다. 이 시점엔 실제 `RetrievalService` 인스턴스는 아직 찾아지지 않은 상태입니다.

2. **실제 값이 채워지는(=조회되는) 시점**은 요청 처리 중 `ChatController.buildRequestSpec()`(ChatController.java:235) 안에서 `this.retrievalServiceProvider.getObject()`를 호출하는 그 순간입니다. 즉 HTTP 요청이 들어와서 `request.ragEnabled()==true`일 때만, 그것도 매 요청마다 컨테이너에 다시 빈을 조회하는 지연(lazy) 방식입니다.

3. 이 코드가 "이미 값이 채워진 것처럼" `getObject()`(nullable 아닌 확정 조회, 없으면 예외)를 바로 써도 안전한 이유는 바로 위 줄(233행)에서 `vectorStoreProvider.getIfAvailable()`이 `null`이면 먼저 `IllegalStateException`을 던지기 때문입니다. `VectorStore`와 `RetrievalService`는 둘 다 같은 `dstone.ai.rag.enabled=true` 조건으로 뜨는 빈이라, `VectorStore`가 존재한다고 확인된 시점엔 `RetrievalService`도 반드시 존재한다고 보장되므로 뒤이은 `getObject()`가 안전하게 성공하는 구조입니다.

---

## 질문

그럼 ObjectProvider<VectorStore> vectorStoreProvider 는 언제 채워지는가 ?

---

## 답변

`vectorStoreProvider`는 앞의 두 경우와 또 다른 결이 있습니다. `RetrievalService`는 dstone 자체 코드(`@ConditionalOnProperty(dstone.ai.rag.enabled=true)`)로 존재 여부가 결정되지만, `VectorStore`는 **dstone 코드가 전혀 관여하지 않는, Spring AI가 자동으로 만드는 빈**이기 때문입니다.

**1) 주입 시점(`ChatController` 생성자)** — `ObjectProvider<VectorStore>`는 실제 `VectorStore` 빈이 있든 없든 Spring이 항상 만들어줄 수 있는 핸들이라, 이 시점엔 "채워졌다"고 할 게 없고 그냥 조회용 프록시만 준비됩니다.

**2) 실제 조회 시점** — `buildRequestSpec()`(ChatController.java:231) 안 `this.vectorStoreProvider.getIfAvailable()` 호출 시점, 즉 `request.ragEnabled()==true`인 요청이 들어올 때마다 매번 컨테이너에 다시 조회합니다.

**3) 그 조회가 실제로 빈을 찾아내는지는 애플리케이션 기동 시점에 Spring AI의 `PgVectorStoreAutoConfiguration`(spring-ai-autoconfigure-vector-store-pgvector, dstone 코드 아님)이 `VectorStore` 빈을 만들었는지에 달려 있습니다.** 이 클래스의 `vectorStore(...)` `@Bean` 메서드 시그니처를 직접 까보면:

```
vectorStore(JdbcTemplate, EmbeddingModel, PgVectorStoreProperties,
            ObjectProvider<ObservationRegistry>, ObjectProvider<...Convention>, BatchingStrategy)
```

`EmbeddingModel`이 `ObjectProvider`가 아니라 **필수 파라미터**로 박혀 있어서, 컨텍스트에 `EmbeddingModel` 빈이 없으면 이 `@Bean` 메서드 자체가 호출되지 못해 `VectorStore` 빈이 생기지 않습니다(그 외 조건: classpath에 `PgVectorStore`/`DataSource`/`JdbcTemplate` 존재 — application.yml의 postgres `datasource` 설정으로 항상 충족 — 그리고 `@ConditionalOnMissingBean(VectorStore.class)`로 다른 VectorStore 빈이 없어야 함).

그 `EmbeddingModel` 빈은 다시 Spring AI의 Ollama/OpenAI 임베딩 자동설정이 `spring.ai.model.embedding` 프로퍼티(application.yml:76 `embedding: ollama`)를 보고 켜주는 것이고, **이 프로퍼티는 `dstone.ai.rag.enabled`와 별개로 존재**합니다(`EmbeddingProperties.java` 주석/CLAUDE.md에도 명시: "chat 쪽 provider와 무관하게 항상 적용").

**즉 `dstone.ai.rag.enabled=true`가 `VectorStore` 빈의 존재를 직접 보장해주지 않습니다** — 그건 순전히 `spring.ai.model.embedding` 설정 + Postgres/pgvector classpath 여부로 Spring AI가 독자적으로 결정하는 것이고, dstone의 rag.enabled 플래그는 `RetrievalService`/`RagController`/`ChatController`의 `ragEnabled` *기능 경로*만 켜고 끕니다. 그래서 `ChatController`가 231~234행에서 `getIfAvailable() == null`이면 명시적으로 `IllegalStateException`을 던지도록 방어 코드를 넣어둔 것이고, 이게 바로 "이미 값이 채워진 것처럼" 보이지 않고 null 체크를 거치는 이유입니다.

---

## 질문

<task-notification>
<task-id>bom96loih</task-id>
<tool-use-id>toolu_01D1NHNjfDRXsyF3RQeEw3ks</tool-use-id>
<output-file>/tmp/claude-1000/-app-dstone/05d7906c-dc58-4088-a7f8-0f8212d7c39d/tasks/bom96loih.output</output-file>
<status>completed</status>
<summary>Background command "find / -iname "*spring-ai-pgvector-store*.jar" -o -iname "*spring-ai-autoconfigure-vector-store-pgvector*.jar" 2&gt;/dev/null | head" completed (exit code 0)</summary>
</task-notification>

---

## 답변

That background `find` finished (superseded by the targeted `~/.m2` search already used) — no action needed.
