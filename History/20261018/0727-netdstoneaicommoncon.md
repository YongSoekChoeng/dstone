# netdstoneaicommoncon

> 생성일시: 2026-10-18 07:27

## 질문

net.dstone.ai.common.config.ConfigChatClient.defaultAdvisors(List<Advisor>) 는 언제호출되나?

---

## 답변

**한 번만 호출됩니다. 애플리케이션이 뜰 때 Spring이 싱글톤 빈 `defaultAdvisors`를 만들면서 부르고, 그 뒤 LLM을 호출할 때는 다시 불리지 않습니다.** 코드에서 이 메서드를 직접 부르는 곳은 없습니다.

### 호출 순서

1. Spring 컨텍스트가 기동되면서 싱글톤 빈을 미리 만듭니다.
2. `chatClient(ChatClient.Builder builder, List<Advisor> defaultAdvisors)` 빈을 만들려면 `List<Advisor>` 타입 인자가 필요합니다 (`ConfigChatClient.java:134`).
3. 이 인자를 채울 때 Spring은 다음 순서로 찾습니다.
   - 먼저 `Advisor` 타입 빈을 모두 모아 List로 만들려고 합니다.
   - 그런 빈이 **하나도 없으면** `List<Advisor>` 타입 빈 자체를 찾습니다. 이때 걸리는 것이 `defaultAdvisors()` 빈이고, 이 시점에 메서드가 실행됩니다 (`ConfigChatClient.java:110`).
   - 다른 빈 생성 순서 때문에 이보다 먼저 만들어질 수는 있지만, 어느 경우든 한 번입니다.
4. 반환된 리스트(`LlmLoggerAdvisor` 포함)는 `builder.defaultAdvisors(...)`로 공유 `ChatClient`에 고정됩니다. 이후 모든 LLM 호출은 이 고정된 목록을 그대로 씁니다.

메서드 자신의 파라미터 `List<Advisor> advisors`도 같은 방식으로 채워집니다. `Advisor` 빈이 없고 자기 자신은 후보에서 빠지기 때문에 빈 리스트가 들어옵니다. javadoc에 "현재는 비어 있음"이라고 적힌 것이 이 상황입니다.

### 주의할 점
`@Bean Advisor`가 어디에든 하나라도 생기면 3번에서 그 빈들이 모여 `chatClient`의 인자가 됩니다. 그러면 `defaultAdvisors` 빈은 `chatClient`에 주입되지 않아 **`LlmLoggerAdvisor`가 조용히 빠집니다**. `defaultAdvisors()` 자체는 싱글톤으로 여전히 만들어지지만 결과가 쓰이지 않습니다. `ToolResultCompactionAdvisor`를 일부러 빈으로 만들지 않은 이유가 이것입니다 (`runtime/agent/ToolResultCompactionAdvisor.java:28` 주석).

그래서 클래스 javadoc에 적힌 "거버넌스 모듈은 `@Bean Advisor`만 추가하면 된다"는 설명은 지금 구조와 맞지 않습니다. 실제로 그렇게 하면 LLM 로깅이 꺼집니다. 원하시면 `chatClient`가 `ObjectProvider` 없이 두 목록을 합치는 방식으로 고쳐 드리겠습니다. 예를 들어 Advisor 빈 목록과 `LlmLoggerAdvisor`를 `defaultAdvisors` 안에서 합치고, `chatClient`는 `@Qualifier("defaultAdvisors")`로 그 결과를 받게 하는 방법입니다.
