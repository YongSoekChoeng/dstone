# netdstoneaicommoncon

> 생성일시: 2026-09-16 09:37

## 질문

net.dstone.ai.common.config.ConfigChatClient.chatClient(Builder, ChatMemory, List<Advisor>)
에서 governanceAdvisors 는 현재 bean 정의도 되어 있지 않은 것 같은데 기동에는 문제없는 것인가?

---

## 답변

네, 문제없이 기동됩니다.

Spring의 `List<T>` 타입 의존성 주입은 일반 단일 빈 주입과 달리, 해당 타입의 빈이 하나도 없어도 에러 없이 **빈 리스트(empty list)** 를 주입합니다. `@Autowired`(또는 `@Bean` 메서드 파라미터)가 컬렉션 타입일 때는 "필수 빈 1개"가 아니라 "매칭되는 모든 빈을 모아서 컬렉션으로" 라는 의미로 해석되기 때문에, 매칭되는 빈이 0개여도 실패하지 않습니다.

지금 `governanceAdvisors`에 대응하는 `Advisor` 타입 빈이 컨텍스트에 하나도 등록되어 있지 않으므로, `chatClient()` 호출 시 `governanceAdvisors`는 그냥 빈 리스트로 들어오고, 그 뒤 `advisors.add(MessageChatMemoryAdvisor...)`로 세션 메모리 어드바이저만 추가된 상태로 정상 조립됩니다. 실제로 파일 상단 주석(L23-26)에도 "지금은 등록된 Advisor 빈이 없으므로 이 리스트는 항상 비어 있다"고 의도적으로 명시돼 있어, 설계된 동작입니다 (`ConfigChatClient.java:70-73`).

나중에 governance 모듈에서 `@Bean Advisor` 를 하나 이상 추가하면, 이 메서드 시그니처를 건드릴 필요 없이 Spring이 자동으로 해당 리스트에 모아서 넣어줍니다.

---

## 질문

그러면 List<Advisor> 가 추가되도록 하는 구현방법이 뭔가? 샘플을 보여줘.

---

## 답변

`List<Advisor>` 파라미터는 **타입이 `Advisor`(또는 `CallAdvisor`/`StreamAdvisor`)인 스프링 빈을 하나라도 등록하면 자동으로 그 리스트에 들어갑니다.** `ConfigChatClient`를 고칠 필요 없이, governance 모듈 쪽에 `@Component`(또는 `@Bean`)로 Advisor 구현체 하나만 추가하면 됩니다.

가장 간단한 방법은 `BaseAdvisor`를 구현하는 것입니다(`before`/`after`만 구현하면 동기 호출(`adviseCall`)과 스트리밍(`adviseStream`) 양쪽에 자동 적용됨):

```java
package net.dstone.ai.governance;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// @Component 만 붙이면 끝 - ConfigChatClient.chatClient(...)의 List<Advisor>에 스프링이 알아서 모아준다.
@Slf4j
@Component
public class UsageLoggingAdvisor implements BaseAdvisor {

	@Override
	public String getName() {
		return "usageLoggingAdvisor";
	}

	@Override
	public int getOrder() {
		// 숫자가 작을수록 먼저(바깥쪽) 실행됨.
		// 참고: MessageChatMemoryAdvisor의 기본 순서는 Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER(=HIGHEST_PRECEDENCE+1000)라서
		// 대부분의 사용자 정의 Advisor보다 훨씬 먼저(더 바깥쪽) 실행된다 - 로깅/가드레일을 메모리 저장보다 앞에 두고 싶으면
		// 이 값보다 더 작은 값을 주면 된다.
		return Ordered.HIGHEST_PRECEDENCE + 500;
	}

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
		// 여기서 PII/민감어 검사, 사용량 로깅 시작 등을 처리
		log.info("[AI 요청] {}", request.prompt().getContents());
		return request; // 검열/마스킹된 새 요청을 반환해도 됨
	}

	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
		// 응답 로깅, 쿼터 차감 등
		log.info("[AI 응답] {}", response.chatResponse());
		return response;
	}
}
```

이 클래스가 컴포넌트 스캔 범위(`net.dstone.ai` 하위)에만 있으면, `chatClient(ChatClient.Builder builder, ChatMemory chatMemory, List<Advisor> governanceAdvisors)` 호출 시 `governanceAdvisors`에 이 빈이 자동으로 담겨 들어오고, 기존 `MessageChatMemoryAdvisor`와 함께 어드바이저 체인에 합류합니다. `ConfigChatClient` 쪽 코드는 전혀 수정할 필요 없습니다.

여러 개를 등록해도 됩니다 — governance 모듈이 PII 마스킹용, 사용량 로깅용, 쿼터 체크용 Advisor를 각각 별도 `@Component`로 만들면 모두 같은 `List<Advisor>`에 모여서 `getOrder()` 순서대로 체인에 끼워집니다.
