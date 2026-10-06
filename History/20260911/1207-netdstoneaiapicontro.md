# netdstoneaiapicontro

> 생성일시: 2026-09-11 12:07

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

---

## 질문

사실 이 질문은 아래의 내용때문에 발생했어.
VectorStore vectorStore = this.vectorStoreProvider.getIfAvailable();
if (vectorStore == null) {
    throw new IllegalStateException("RAG가 비활성화되어 있습니다(dstone.ai.rag.enabled=false 또는 미설정).");
}
RetrievalService retrievalService = this.retrievalServiceProvider.getObject(); ==>> 이부분.

retrievalServiceProvider는 자동주입되었겠지만 getObject()로 RetrievalService를 받아오는 소스가 아무 설명없이 사용되고 있어서...
내 의심은... retrievalServiceProvider.getObject()로 사용되는것은 반드시 retrievalServiceProvider 안에 Object 가 null일 수도 있을 거라는 의심이었고
방금전 질문에 대한 답변으로..
VectorStore vectorStore = this.vectorStoreProvider.getIfAvailable() 가 존재한다고 확인된 시점엔 RetrievalService가 반드시 존재한다고 보장된다고 했는데
그 연관관계를 모르겠다는 거야.

---

## 답변

좋은 지적입니다. 방금 전 제 답변("VectorStore가 확인되면 RetrievalService도 반드시 존재한다")은 **틀렸다기보다 과하게 단정한 것**이었습니다. 정확한 연관관계를 다시 짚으면:

**두 빈은 사실 서로 다른, 독립된 조건으로 뜬다는 게 핵심입니다.**

| 빈 | 존재 조건 |
|---|---|
| `RetrievalService` | dstone 코드의 `@ConditionalOnProperty(dstone.ai.rag.enabled=true)` (RetrievalService.java:24-25) — **오직 이 프로퍼티 하나** |
| `VectorStore` | dstone 코드가 전혀 관여 안 함. Spring AI `PgVectorStoreAutoConfiguration`이 `EmbeddingModel` 빈 존재 여부로 자동 생성 → 그 `EmbeddingModel`은 `spring.ai.model.embedding`(application.yml:76 `embedding: ollama`) 프로퍼티로 활성화됨 |

즉 `dstone.ai.rag.enabled`와 `spring.ai.model.embedding`은 **서로 다른 키(key)**이고, Spring이 이 둘을 자동으로 묶어주는 메커니즘은 없습니다. application.yml에 지금 `rag.enabled: true`이면서 동시에 `embedding: ollama`라서(153행, 76행 — 둘 다 "켜짐" 상태로 나란히 설정돼있어서) 우연히 맞아떨어지는 것이지, 코드가 그 일치를 강제하는 게 아닙니다.

**그래서 실제로 아래 시나리오가 가능합니다:**
- 누군가 `dstone.ai.rag.enabled: false`로만 바꾸고 `spring.ai.model.embedding: ollama`는 그대로 둔다면
- → `EmbeddingModel` 빈은 여전히 뜨고 → `VectorStore` 빈도 여전히 뜬다 (rag.enabled와 무관)
- → 하지만 `RetrievalService` 빈은 `@ConditionalOnProperty`에 안 걸려서 **뜨지 않는다**
- → 이 상태에서 `ragEnabled=true`로 요청이 오면: `vectorStoreProvider.getIfAvailable()`은 non-null이라 233행 체크를 통과하고, 곧바로 `retrievalServiceProvider.getObject()`(235행)가 호출되는데 **이때는 정말로 `NoSuchBeanDefinitionException`이 던져집니다.**

당신이 처음에 가졌던 의심("getObject()는 null일 수도 있는데 왜 그냥 쓰나")이 맞는 방향이었고, 제가 "둘 다 같은 조건이라 안전하다"고 한 건 **현재 application.yml의 우연한 배치**를 마치 코드가 보장하는 불변식인 것처럼 과장한 것이었습니다. 정정합니다.

실제로 이 코드가 안전한 근거는 "논리적 보장"이 아니라 **"운영 관례"**입니다 — CLAUDE.md/EmbeddingProperties 주석에 "RAG 안 쓰면 `dstone.ai.rag.enabled=false`로 두면 임베딩 설정 없이도 기동된다"고 되어 있는 걸 보면, 설계 의도상 두 값은 항상 같이 켜고 같이 끄도록 되어 있지만, 그걸 코드 레벨에서 강제하는 assertion은 없습니다. 즉 이건 **잠재적 버그(설정 불일치 시 500 대신 스택트레이스 노출)** 이지, "반드시 안전하다"고 할 수 있는 게 아닙니다.

원하시면 이 부분을 방어적으로 고칠 수도 있습니다(예: `retrievalServiceProvider.getIfAvailable()`로 바꾸고 null이면 명확한 `IllegalStateException`을 던지도록). 수정을 원하시나요?

---

## 질문

아래와 같이 사용하게 될 경우 문제가 될 거리가 있을까?
<원소스>
if (Boolean.TRUE.equals(request.ragEnabled())) {
    VectorStore vectorStore = this.vectorStoreProvider.getIfAvailable();
    if (vectorStore == null) {
        throw new IllegalStateException("RAG가 비활성화되어 있습니다(dstone.ai.rag.enabled=false 또는 미설정).");
    }
    RetrievalService retrievalService = this.retrievalServiceProvider.getObject();
    SearchRequest searchRequest = SearchRequest.builder()
        .topK(retrievalService.defaultTopK()) // 검색할 청크 수
        .similarityThreshold(retrievalService.defaultSimilarityThreshold()) // 실측 기반 조정값
        .build();
    spec = spec.advisors(QuestionAnswerAdvisor.builder(vectorStore).searchRequest(searchRequest).build());
}

<수정>
@Autowired
RetrievalService retrievalService;
...
if (Boolean.TRUE.equals(request.ragEnabled())) {
    VectorStore vectorStore = this.vectorStoreProvider.getIfAvailable();
    if (vectorStore == null) {
        throw new IllegalStateException("RAG가 비활성화되어 있습니다(dstone.ai.rag.enabled=false 또는 미설정).");
    }
    SearchRequest searchRequest = SearchRequest.builder()
        .topK(retrievalService.defaultTopK()) // 검색할 청크 수
        .similarityThreshold(retrievalService.defaultSimilarityThreshold()) // 실측 기반 조정값
        .build();
    spec = spec.advisors(QuestionAnswerAdvisor.builder(vectorStore).searchRequest(searchRequest).build());
}

---

## 답변

네, 이 수정은 **RAG를 끈 배포 환경에서 애플리케이션 자체가 기동 불가 상태**가 되는 문제가 있습니다.

**원인**

`RetrievalService`는 `@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true")`(RetrievalService.java:24-25)가 붙어 있어서, `dstone.ai.rag.enabled=false`(또는 미설정)이면 **이 빈 자체가 컨텍스트에 아예 존재하지 않습니다.**

반면 `ChatController`는 RAG를 쓰든 안 쓰든 **항상 떠 있어야 하는 필수 컨트롤러**입니다(`/api/ai/chat`은 dstone-ai-engine의 기본 엔드포인트). 그런데 여기에 `@Autowired RetrievalService retrievalService;`처럼 **필수 의존성**으로 걸어버리면:

- `dstone.ai.rag.enabled=false`인 환경에서 `RetrievalService` 빈이 없으므로
- `ChatController` 자체의 빈 생성이 `NoSuchBeanDefinitionException`으로 실패
- 결국 **`dstone-ai-engine` 애플리케이션 전체가 기동하지 못합니다**(RAG와 무관한 일반 채팅 기능까지 전부 죽음)

원래 `ObjectProvider<RetrievalService>`로 받은 이유가 정확히 이걸 막기 위해서였습니다. 코드 주석(ChatController.java:58-60)에도 명시돼 있습니다:

> "RAG는 dstone.ai.rag.enabled=true일 때만 존재하는 빈이다. ChatController는 RAG를 안 쓰는 배포에서도 항상 떠 있어야 하므로, 필수 의존성이 아니라 ObjectProvider로 있으면 쓰고 없으면 마는 식으로 받는다."

**정리**

- 지금 이 환경(`application.yml`에 `rag.enabled: true`)에서만 테스트하면 수정 코드도 정상 동작하는 것처럼 보일 겁니다.
- 하지만 CLAUDE.md에 명시된 설계 의도(`dstone.ai.rag.enabled=false`인 SI 프로젝트는 Postgres/pgvector/임베딩 설정 없이도 엔진이 그대로 떠야 함)를 정면으로 깨뜨리는 변경입니다.
- 즉 "지연 조회(ObjectProvider) → 있으면 쓰고 없으면 명시적 에러" 패턴을 "즉시 필수 주입"으로 바꾸면, **원래 null 체크로 해결하려던 문제(RAG 꺼졌을 때의 graceful 처리)가 null 체크 이전 단계, 즉 기동 시점으로 앞당겨져서 훨씬 나쁜 형태(전체 서비스 다운)로 터지는 것**이 핵심 문제입니다.

바꾸고 싶으시다면 `ObjectProvider`는 유지하되 `getObject()` 대신 `getIfAvailable()` + null 체크로 바꾸는 정도가 (지난번 제가 제안했던) 안전한 방향입니다.

---

## 질문

그렇다면 RetrievalService 에서 
@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true") 를 제거하면 되지 않을까?

---

## 답변

아니요, 그건 근본적인 해결이 안 됩니다. 오히려 더 큰 문제를 만듭니다.

**왜 안 되는가**

`@ConditionalOnProperty`를 지워도 `RetrievalService`의 생성자는 그대로입니다:

```java
public RetrievalService(VectorStore vectorStore, ConfigProperty configProperty) {
```

`VectorStore`는 `ObjectProvider`가 아니라 **필수 생성자 파라미터**입니다. 그리고 `VectorStore` 빈 자체가 (지난 답변에서 확인했듯) `spring.ai.model.embedding` 설정으로 `EmbeddingModel`이 있어야만 Spring AI가 만들어주는, **그 자체로 조건부 존재하는 빈**입니다.

즉 `@ConditionalOnProperty`를 없애면:

- `RetrievalService`가 "무조건 뜨려고 시도하는 빈"이 되지만
- `dstone.ai.rag.enabled=false`이고 그에 맞춰 Postgres/pgvector/임베딩 설정도 없는(=CLAUDE.md가 의도한 "RAG 안 쓰는 SI 프로젝트" 배포) 환경에서는
- `VectorStore` 빈이 없으므로 `RetrievalService` 생성자 자체가 와이어링 실패 → `NoSuchBeanDefinitionException` → **여전히 앱 전체가 기동 실패**

즉 문제를 없앤 게 아니라 "ChatController가 RetrievalService를 필수로 요구해서 실패" → "RetrievalService가 VectorStore를 필수로 요구해서 실패"로 **위치만 한 단계 옮긴 것**입니다.

**설계 의도 자체를 깨는 부작용도 있습니다**

CLAUDE.md에 명시된 대로 `dstone-ai-engine`은 "RAG가 필요 없는 SI 프로젝트는 `dstone.ai.rag.enabled=false`(또는 미설정)로 두면 Postgres/pgvector/임베딩 설정 없이도 그대로 기동된다"가 핵심 요구사항입니다. `RetrievalService`의 `@ConditionalOnProperty`는 바로 이 요구사항을 구현하는 장치입니다. 이걸 지우면:

- RAG를 원하지 않는 배포에서도 사실상 Postgres+pgvector+임베딩 모델 설정을 **강제로 갖춰야만** 엔진이 뜨게 됨
- `DocumentIngestService`/`RagController`는 여전히 같은 조건(`dstone.ai.rag.enabled=true`)으로 남아있을 테니, `RetrievalService`만 홀로 조건 없이 떠서 "RAG 절반만 켜진" 불일치 상태가 생김

**결론**

`@ConditionalOnProperty`는 그대로 두는 게 맞습니다. 문제의 실체는 `RetrievalService`의 조건부 존재 자체가 아니라, `ChatController`가 그걸 다루는 방식(`getObject()`가 예외를 던질 수 있는데 방어 코드 없이 쓰는 것)이었습니다. 이전에 제안한 대로 `ChatController` 쪽만 `retrievalServiceProvider.getIfAvailable()` + null 체크로 바꾸는 게 구조를 유지하면서 실제 위험을 없애는 방법입니다.

---

## 질문

혹시 RetrievalService 는 @Autowired 로 사용할 수 없나?

---

## 답변

정확히 이 `RetrievalTools`가 바로 그 예시입니다. **네, `@Autowired`(또는 생성자 주입)로 `RetrievalService`를 받아도 되는 경우가 있습니다** — 단, 조건이 하나 있습니다.

**될 때: 받는 쪽 클래스도 같은 조건으로 뜰 때**

`RetrievalTools.java:26`을 보면:

```java
@AiTool
@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true")
public class RetrievalTools extends BaseObject {
    public RetrievalTools(RetrievalService retrievalService) { ... }  // 필수 주입, ObjectProvider 아님
```

`RetrievalTools` 자체가 `RetrievalService`와 **똑같은 조건**(`dstone.ai.rag.enabled=true`)을 달고 있습니다. 그래서:

- `rag.enabled=false` → `RetrievalService`도 없고 `RetrievalTools`도 없음(둘 다 안 뜸) → 애초에 주입 시도 자체가 발생하지 않음
- `rag.enabled=true` → 둘 다 있음 → 평범한 필수 의존성 주입으로 안전하게 동작

즉 "이 값이 없을 수도 있다"는 문제가, **소비하는 쪽 빈의 생사 자체를 같은 스위치에 묶어버림**으로써 사라지는 구조입니다. 있으면 둘 다 있고, 없으면 둘 다 없으니 중간 상태(하나만 있는 상태)가 아예 발생하지 않습니다.

**안 될 때: `ChatController`처럼 항상 떠 있어야 하는 빈일 때**

`ChatController`는 조건이 없는(=RAG on/off와 무관하게 항상 존재해야 하는) 빈입니다. 그래서 `RetrievalService`처럼 조건부 빈을 `@Autowired` 필수로 받으면, `rag.enabled=false`일 때 "받는 쪽은 항상 있어야 하는데 줄 게 없는" 불일치가 생겨서 기동 실패로 이어집니다. 이게 `ChatController`가 `ObjectProvider`를 써야 하는 이유입니다.

**한 줄 정리**

`@Autowired RetrievalService`가 안전한지는 "받는 클래스가 `RetrievalService`와 같은 `@ConditionalOnProperty` 조건을 공유하는가"에 달려 있습니다. 같으면 필수 주입 OK(`RetrievalTools`가 실례), 다르면(특히 항상 떠야 하는 `ChatController` 같은 경우) `ObjectProvider` + null 체크가 필요합니다.

---

## 질문

소스 전체적으로 @ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true") 를 제거한다면 ?

---

## 답변

전체적으로 제거하면 문제가 훨씬 커집니다. 영향받는 클래스는 5개입니다: `RetrievalService`, `DocumentIngestService`, `RagController`, `RetrievalTools`, `PostgresExplainValidationTools`(전부 `dstone.ai.rag.enabled=true` 조건).

**핵심 문제: 전부 "조건부 존재"에서 "무조건 존재 시도"로 바뀌는데, 그 생성자들이 전부 하드 디펜던시를 갖고 있습니다.**

| 클래스 | 생성자 필수 의존성 | 없으면 |
|---|---|---|
| `EmbeddingProperties` | `ConfigProperty`(항상 있음)지만 `@PostConstruct`에서 `spring.ai.model.embedding`이 비어있으면 즉시 `IllegalStateException` | RAG 안 쓰는 배포는 보통 이 값을 안 정하므로 **기동 즉시 실패** |
| `RetrievalService` | `VectorStore`(필수 파라미터) | `VectorStore` 빈 없으면(임베딩 모델 미설정 등) 생성자 와이어링 실패 → **기동 실패** |
| `DocumentIngestService` | `VectorStore`(필수 파라미터, DocumentIngestService.java:46) | 동일 |
| `RagController` | `DocumentIngestService`(필수) | 위가 실패하면 연쇄 실패 |
| `RetrievalTools` | `RetrievalService`(필수) | 동일 연쇄 |
| `PostgresExplainValidationTools` | `DataSource`(필수) | RAG 안 쓰는 SI 프로젝트가 `spring.datasource`(postgres) 설정 자체를 안 하면 `DataSource` 빈이 없어 실패 |

**결과적으로:**

지금 이 환경(`rag.enabled: true` + `embedding: ollama` + postgres datasource 다 설정됨)에서는 당장 안 터질 수 있습니다. 하지만 어노테이션을 지운다는 건 **"RAG를 원치 않는 SI 프로젝트도 항상 Postgres + pgvector + 임베딩 모델(Ollama/OpenAI)을 갖추고 있어야만 `dstone-ai-engine`이 뜬다"**로 아키텍처 자체를 바꾸는 겁니다. 이건 CLAUDE.md에 명시된 설계 목표와 정면으로 배치됩니다:

> "RAG가 필요 없는 SI 프로젝트는 false(또는 미설정)로 두면 Postgres/pgvector/임베딩 설정 없이도 그대로 기동된다"

즉 `dstone.ai.rag.enabled`는 이 엔진 전체가 "여러 SI 프로젝트가 재사용하는 범용 프레임워크"라는 정체성을 지키기 위한 핵심 스위치입니다. 이걸 없애면 RAG가 선택 기능이 아니라 필수 인프라가 되어버려서, 앞으로 RAG 없이 이 엔진을 갖다 쓰려는 프로젝트가 전부 영향을 받습니다. (참고로 `RateLimitProperties`/`ApiKeyProperties` 주석도 `dstone.ai.rag.enabled`를 "옵트인 방식의 모범 사례"로 인용하고 있어서, 이 패턴이 다른 optional 기능들의 기준점 역할도 하고 있습니다.)

**정리**

전체 제거는 권장하지 않습니다. 원래 논의하던 문제(`ChatController`가 `getObject()`를 방어 없이 쓰는 것)는 `ChatController` 한 곳만 고치면 되는 국소적 이슈였고, 지금 이 변경은 그와 무관하게 훨씬 큰 아키텍처 리스크입니다.

---

## 질문

그래서 난 @Autowired 로 가능한 스타일로만 코딩해 ...

---

## 답변

이해했습니다. 다만 "항상 `@Autowired`가 되는 스타일"을 이 코드베이스에 적용하려면, `ObjectProvider`를 없애는 게 아니라 **"필수 의존성이 항상 채워지도록 만드는 것"**으로 접근해야 합니다. 지금 방식(`ObjectProvider` + null 체크)이 왜 들어갔는지 다시 보면:

- `ChatController`는 항상 떠야 함 (RAG on/off 무관)
- `RetrievalService`/`VectorStore`는 RAG가 꺼지면 존재하지 않음
- → 이 둘을 있는 그대로 `@Autowired`로 연결하면 "항상 뜨는 빈이 없을 수도 있는 빈을 필수로 요구"하는 모순이 생김

이 모순을 없애면서도 `@Autowired`(필수 주입) 스타일을 유지하는 표준 Spring 패턴이 있습니다: **"항상 존재하는 fallback(no-op) 빈"을 만들어서, 진짜 구현체가 없을 때 그 자리를 대신 채워주는 방식**입니다.

예를 들면:

```java
public interface RagSupport {
    List<Document> search(String query);
    // ...
}

@Component
@ConditionalOnProperty(name = "dstone.ai.rag.enabled", havingValue = "true")
public class VectorStoreRagSupport implements RagSupport { ... } // 진짜 구현

@Component
@ConditionalOnMissingBean(RagSupport.class)  // 위 빈이 없을 때만 대신 등록됨
public class DisabledRagSupport implements RagSupport {
    public List<Document> search(String query) {
        throw new IllegalStateException("RAG가 비활성화되어 있습니다(dstone.ai.rag.enabled=false 또는 미설정).");
    }
}
```

이렇게 하면 `ChatController`는:

```java
@Autowired
RagSupport ragSupport; // 항상 뜬다 - 둘 중 하나는 항상 등록되니까
```

처럼 `ObjectProvider`/null 체크 없이 순수 `@Autowired` 스타일로 쓸 수 있고, "RAG가 꺼져 있으면 에러를 던진다"는 로직도 코드 안의 `if (vectorStore == null)`이 아니라 **Spring 설정(빈 등록 레벨)으로 옮겨가는** 것뿐입니다. RAG를 완전히 선택적으로 유지하는 아키텍처 요구사항도 그대로 지켜집니다.

다만 이건 `RetrievalService`(우리 코드)는 쉽게 적용되지만, `VectorStore`는 Spring AI가 자동 구성하는 서드파티 인터페이스라 메서드가 많아서 no-op 구현체를 직접 만드는 비용이 좀 듭니다.

이 패턴으로 `ChatController`/`RetrievalService`/`VectorStore` 쪽을 실제로 리팩터링해드릴까요, 아니면 지금은 논의만 해두고 진행은 나중에 하시겠어요?
