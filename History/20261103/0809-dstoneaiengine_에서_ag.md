# dstoneaiengine_에서_ag

> 생성일시: 2026-11-03 08:09

## 질문

dstone-ai-engine 에서 agent 별로 모델명을 넣을 수 있는걸 알아. 그런데 Provider 도 agent 별로 세팅이 가능하도록 하는건 어떨까?

---

## 답변

먼저 지금 코드에서 Provider가 어떻게 묶여 있는지 확인해 보겠습니다.

---

## 답변

**넣을 만한 기능이지만, Agent에 `openai` 같은 Provider 종류를 직접 적기보다 설정에 이름 붙인 연결을 Agent가 고르는 방식을 권합니다.** 다만 여러 회사의 모델을 섞는 것만이 목적이라면 이 기능은 필요 없습니다. 지금 `openai` Provider가 OpenRouter를 쓰고 있어서 `model.name`만 바꿔도 되기 때문입니다.

## 이럴 때 필요합니다
- **민감한 데이터:** 소스 코드가 담긴 Agent는 사내 Ollama로, 나머지는 외부 LLM으로 보낼 때
- **비용 절감:** ROUTER나 문서 작성처럼 단순한 Agent는 로컬 모델로 돌릴 때
- **직접 연결:** OpenRouter를 거치지 않고 Anthropic API나 사내 vLLM에 바로 붙일 때

현재 정의 파일에서 `model.name`을 적은 Agent는 1개뿐입니다. 위와 같은 쓰임새가 아직 없다면 서두를 일은 아닙니다.

## 권하는 형식
```yaml
# conf/application.yml: 접속 정보는 여기에만 둠
dstone.ai.llm:
  default: openrouter
  connections:
    openrouter: { type: openai, base-url: ..., api-key: ENC(...), model: qwen/qwen3.8-27b }
    local:      { type: ollama, base-url: http://localhost:11434, model: ... }
```
```yaml
# Agent .md 앞부분
model:
  provider: local     # 생략하면 default
  name: ...           # 생략하면 그 연결의 기본 모델
```

- **이름을 쓰는 이유:** `openai` 호환 서버가 여러 개여도 구분할 수 있습니다. 개발자가 아닌 사람도 고치는 정의 파일에 URL이나 API 키가 들어가지 않습니다.
- **기존 Agent:** `provider`를 생략하면 지금처럼 동작하므로 고칠 필요가 없습니다.
- **잘못된 값 확인:** 없는 연결 이름은 기동할 때와 디자이너의 [검사]에서 실패시킵니다. 지금은 다른 Provider의 모델명을 적어도 첫 호출에서야 오류가 나는데, 연결별 기본 모델을 두면 이름 누락은 줄어듭니다. 모델명 오타는 여전히 호출 때 드러납니다.

## 바뀌는 코드와 위험
1. **`ChatClient`:** 지금은 하나를 공유하므로 연결마다 하나씩 두고, `AgentExecutor.buildSpec()`이 Agent에 맞는 것을 고르게 합니다.
2. **`requestOptions()`:** 지금은 `spring.ai.model.chat` 값 하나로 옵션을 정하므로, Agent 연결의 `type`을 받도록 바꿉니다. 이걸 놓치면 Ollama로 가는 호출에 OpenAI 옵션이 붙습니다.
3. **모델 객체 생성(가장 큰 위험):** Spring AI 자동 설정은 `spring.ai.model.chat`에 지정한 Provider만 만드는 구조입니다. 나머지는 직접 만들어야 하고, 그러면 `spring.ai.openai.*` 설정이 자동으로 적용되지 않을 수 있습니다. 2.0.1에서 어떻게 만들 수 있는지는 아직 확인하지 않았으므로 구현 전에 먼저 확인해야 합니다.
4. **테스트:** `ScriptedChatModel`이 `@Primary` 하나로 LLM 호출을 대신하고 있어, 연결별 모델도 모두 대신하도록 고쳐야 합니다.
5. **검증 범위:** Ollama와 Anthropic의 `reasoning` 설정은 아직 실제로 돌려 본 적이 없습니다. 또 `timeoutSeconds`는 `openai`에만 적용되고, 구조화 출력과 Tool 호출 품질도 모델마다 다릅니다. 지금 프롬프트는 대부분 OpenRouter의 `qwen/qwen3.8-27b`로 측정해 맞춘 것이라, Provider를 바꾼 Agent는 다시 측정해야 합니다.

## 범위에서 뺄 것
- **자동 전환:** 장애 시 다른 Provider로 넘기는 기능은 넣지 않습니다. 모델이 바뀌면 결과도 달라지는데, 그 사실이 드러나지 않습니다.
- **채팅 API 지정:** 요청마다 Provider를 고르는 기능도 넣지 않습니다. 호출자가 사내 전용으로 정한 Agent를 외부로 보낼 수 있습니다.
- **연결 설정 다시 읽기:** 디자이너의 다시 읽기 대상에서 뺍니다. MCP 정의처럼 고치면 재기동하고, Agent가 어떤 연결을 고를지는 지금처럼 재기동 없이 바꿀 수 있습니다.

---

## 질문

<pasted_content id="c172">
그러면 이 방식이 application.yml 의 아래 설정을 대체하는건가?
spring.ai.model.chat
spring.ai.anthropic    
spring.ai.openai
...    
</pasted_content id="c172">

---

## 답변

실제 자동 설정이 이 값들을 어떻게 쓰는지에 따라 답이 달라지므로 Spring AI 2.0.1 jar에서 확인하겠습니다.

---

## 답변

**앞서 제안한 형식대로라면 대체하는 것이 맞지만, 코드를 확인해 보니 대체하지 않는 쪽이 낫습니다. 제 제안을 수정해서, `spring.ai.*`를 그대로 두고 Agent에는 Provider 이름만 적는 방식을 권합니다.**

## 수정한 형식
`application.yml`은 고치지 않아도 됩니다.

```yaml
spring.ai:
  model.chat: openai   # 그대로: model.provider를 생략한 Agent가 쓰는 기본값
  openai: ...          # 그대로: 접속 정보, 기본 모델, max-tokens, timeout
  anthropic: ...       # 그대로
  ollama: ...          # 그대로
```
```yaml
# Agent .md 앞부분
model:
  provider: ollama     # anthropic | openai | ollama, 생략하면 spring.ai.model.chat
  name: ...            # 생략하면 그 Provider의 chat.options.model
```

## 설정별 역할

| 설정 | 지금 | 수정한 방식 | 처음 제안한 방식 |
|---|---|---|---|
| `spring.ai.model.chat` | 엔진 전체의 Provider | 생략한 Agent의 기본값 | `none`으로 고정 |
| `spring.ai.openai` 등 | 선택된 Provider만 사용 | 세 블록 모두 사용 가능 | 새 설정으로 이동 |
| `spring.ai.model.embedding`, `audio`, `image`, `moderation` 등 | 끔(`none`) | 그대로 | 그대로 |
| `spring.ai.tools.limits` | Tool 호출 한도 | 그대로 | 그대로 |

## 바꾼 이유
- **전부 대체하면 일이 커집니다:** `spring.ai.model.chat`을 `none`으로 두면 자동 설정이 `ChatModel`을 만들지 않으므로, 세 Provider를 모두 직접 만들어야 합니다. `ChatClient.Builder`도 `ChatModel`을 받아 만들어지므로 함께 손봐야 할 가능성이 큽니다.
- **값을 지우는 것도 안 됩니다:** 세 자동 설정 모두 `matchIfMissing=true`여서, 값을 지우면 동시에 켜집니다.
- **기존 설정을 살릴 수 있습니다:** 세 블록이 이미 있으니 기본 모델, `max-tokens`, 타임아웃을 옮기지 않아도 되고 설정 위치도 지금처럼 한 곳입니다.
- **잘못된 값을 잡기 쉽습니다:** `provider`에 올 수 있는 값이 3개뿐이라 기동할 때와 [검사]에서 바로 걸러집니다.

## 포기하는 것
**같은 종류의 연결을 둘 이상 쓸 수 없습니다.** 예를 들어 OpenRouter와 사내 vLLM은 둘 다 `openai` 방식이라, 한 엔진에서 동시에 쓰려면 이름 붙인 연결이 필요합니다. 그런 요구가 생기면 그때 `spring.ai.*`와 별도로 연결 목록을 추가해도 Agent 쪽 `provider` 키는 그대로 쓸 수 있습니다.

## 구현할 때 남는 일
자동 설정은 기본 Provider의 모델만 만들기 때문에, 나머지 두 개는 엔진이 같은 `spring.ai.*` 값을 읽어 직접 만들어야 합니다. OpenAI 자동 설정의 클라이언트 생성 메서드가 `private`이라 그대로 가져다 쓸 수 없고, 이 부분은 코드를 일부 다시 작성해야 합니다. 설정 값을 Spring AI의 Properties 클래스로 직접 읽어 올 수 있는지는 아직 시험하지 않았습니다.

아직 쓰지 않는 Provider의 API 키가 비어 있어도 기동은 되어야 합니다. 그래서 모델은 처음 호출할 때 만들고, `provider`를 지정한 Agent가 있는데 그 키가 비어 있으면 기동할 때 실패시키는 방식으로 하겠습니다.
