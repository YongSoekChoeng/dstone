# netdstoneaicommoncon

> 생성일시: 2026-09-16 09:32

## 질문

net.dstone.ai.common.config.ConfigChatClient.chatClient(Builder, ChatMemory, List<Advisor>)
에서 governanceAdvisors 는 현재 bean 정의도 되어 있지 않은 것 같은데 기동에는 문제없는 것인가?

---

## 답변

네, 문제없이 기동됩니다.

Spring의 `List<T>` 타입 의존성 주입은 일반 단일 빈 주입과 달리, 해당 타입의 빈이 하나도 없어도 에러 없이 **빈 리스트(empty list)** 를 주입합니다. `@Autowired`(또는 `@Bean` 메서드 파라미터)가 컬렉션 타입일 때는 "필수 빈 1개"가 아니라 "매칭되는 모든 빈을 모아서 컬렉션으로" 라는 의미로 해석되기 때문에, 매칭되는 빈이 0개여도 실패하지 않습니다.

지금 `governanceAdvisors`에 대응하는 `Advisor` 타입 빈이 컨텍스트에 하나도 등록되어 있지 않으므로, `chatClient()` 호출 시 `governanceAdvisors`는 그냥 빈 리스트로 들어오고, 그 뒤 `advisors.add(MessageChatMemoryAdvisor...)`로 세션 메모리 어드바이저만 추가된 상태로 정상 조립됩니다. 실제로 파일 상단 주석(L23-26)에도 "지금은 등록된 Advisor 빈이 없으므로 이 리스트는 항상 비어 있다"고 의도적으로 명시돼 있어, 설계된 동작입니다 (`ConfigChatClient.java:70-73`).

나중에 governance 모듈에서 `@Bean Advisor` 를 하나 이상 추가하면, 이 메서드 시그니처를 건드릴 필요 없이 Spring이 자동으로 해당 리스트에 모아서 넣어줍니다.
