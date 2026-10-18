# netdstoneaicommoncon

> 생성일시: 2026-10-18 07:45

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

---

## 질문

Function<@Nullable ChatClientRequest, String> 의 의미를 모륵겠어

---

## 답변

`ConfigCallLog.java:539` 쪽 코드를 말씀하신 것 같습니다. 앞에서부터 하나씩 풀면 이렇습니다.

## 1. `Function<A, B>`: "A를 받아 B를 돌려주는 함수" 객체

`java.util.function.Function<T, R>`는 메서드가 `R apply(T t)` 하나뿐인 인터페이스입니다.

- 첫 번째 타입 `T`는 **입력**입니다. 여기서는 `ChatClientRequest`(LLM에 보낼 요청)입니다.
- 두 번째 타입 `R`은 **출력**입니다. 여기서는 `String`입니다.

그래서 `Function<ChatClientRequest, String>`은 **"LLM 요청을 받아서 로그에 찍을 문자열로 바꿔 주는 변환기"**입니다.

## 2. `@Nullable`: "이 입력은 null일 수도 있다"는 표시

`org.jspecify.annotations.Nullable`은 타입 앞에 붙이는 표시입니다. `Function<@Nullable ChatClientRequest, String>`은 **"이 함수에는 null이 들어올 수 있으니 함수 쪽에서 null을 처리해야 한다"**는 약속입니다.

- 실행 동작은 바뀌지 않습니다. 컴파일러가 이 표시만 보고 막거나 검사해 주지는 않습니다. NullAway 같은 정적 분석 도구와 IDE 경고에만 쓰입니다.
- 그 약속대로 기본 구현은 null을 직접 걸러냅니다. `chatClientRequest != null ? chatClientRequest.toString() : "null"` 부분입니다.

비교하면 위치에 따라 뜻이 다릅니다.

| 쓴 위치 | 뜻 |
|---|---|
| `Function<@Nullable ChatClientRequest, String>` | 함수에 **들어오는 값**이 null일 수 있음 |
| `@Nullable Function<...> requestToString` (`:545`, `:577`) | **함수 객체 자체**가 null일 수 있음. 그래서 생성자에서 null이면 `DEFAULT_REQUEST_TO_STRING`으로 대신합니다 |

## 3. 람다 없이 쓰면

`:539` 줄을 익명 클래스로 풀면 아래와 같습니다.

```java
public static final Function<ChatClientRequest, String> DEFAULT_REQUEST_TO_STRING = new Function<ChatClientRequest, String>() {
	@Override
	public String apply(ChatClientRequest chatClientRequest) {
		// 요청이 없으면 "null"이라는 글자를 찍습니다.
		if (chatClientRequest == null) {
			return "null";
		}
		return chatClientRequest.toString();
	}
};
```

## 4. 이 클래스에서 하는 일

`LlmLoggerAdvisor`는 요청과 응답을 로그로 남깁니다. **"로그에 무엇을 어떻게 찍을지"를 밖에서 바꿔 끼울 수 있게** 이 변환기를 필드로 받아 둡니다.

- `builder().build()`처럼 아무것도 넘기지 않으면 `toString()`으로 찍는 기본 변환기를 씁니다. 지금 `ConfigChatClient`가 이렇게 쓰고 있습니다.
- `builder().requestToString(내 변환기)`를 넘기면 그 방식으로 찍습니다. 예를 들어 사용자 메시지만 찍거나 앞부분만 잘라서 찍을 수 있습니다.

이 구조는 Spring AI의 `SimpleLoggerAdvisor` 코드를 그대로 옮겨 온 것입니다. `@Nullable`도 그쪽 코드 스타일(JSpecify)이 따라온 것입니다.

참고로 이 코드에는 저장소 규칙(람다 금지)에 어긋나는 부분이 남아 있습니다. `:539`/`:540`의 람다 두 개와 `adviseStream`의 `this::logResponse` 메서드 참조입니다. 원하시면 익명 클래스로 바꿔 드리겠습니다.
