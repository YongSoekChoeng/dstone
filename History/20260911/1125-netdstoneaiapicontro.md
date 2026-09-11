# netdstoneaiapicontro

> 생성일시: 2026-09-11 11:25

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
