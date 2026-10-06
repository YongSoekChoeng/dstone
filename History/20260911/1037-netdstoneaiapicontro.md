# netdstoneaiapicontro

> 생성일시: 2026-09-11 10:37

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
