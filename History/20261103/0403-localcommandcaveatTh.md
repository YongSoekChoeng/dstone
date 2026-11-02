# localcommandcaveatTh

> 생성일시: 2026-11-03 04:03

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

dstone-ai-engine 에서 agent 별로 모델명을 넣을 수 있는걸 알아. 그런데 Provider 도 agent 별로 세팅이 가능하도록 하는건 어떨까?

---

## 답변

현재 구조에서 얼마나 바꿔야 하는지 코드를 확인해 보겠습니다.

---

## 답변

**해볼 만하다고 봅니다. 다만 `anthropic`·`openai` 같은 provider 종류가 아니라, 이름 붙인 연결을 Agent가 고르는 방식을 권합니다.** 코드는 아직 바꾸지 않았고, 아래 내용은 코드와 라이브러리 설정을 읽어 확인했을 뿐 실제로 돌려 보지는 않았습니다.

## 먼저 볼 점: 필요한 경우가 생각보다 좁습니다
지금 `openai` provider는 OpenRouter를 가리킵니다. 그래서 OpenRouter에 있는 클라우드 모델끼리는 지금도 `model.name`만 바꾸면 Agent별로 다르게 쓸 수 있습니다.

새 기능이 꼭 필요한 경우는 다음과 같습니다.
- **로컬과 클라우드 혼용:** 라우터나 단순 변환은 사내 Ollama로, 깊은 분석은 클라우드로 보낼 때
- **데이터 반출 제한:** 폐쇄망 SI에서 민감한 소스를 다루는 Agent만 내부 LLM을 쓰게 할 때
- **Anthropic 직접 호출:** OpenRouter를 거치지 않고 thinking 설정 등을 쓰고 싶을 때

이런 요구가 실제로 있거나 SI 재사용을 염두에 둔다면 넣을 가치가 있습니다. 비용 절감만이 목적이면 `model.name`으로 충분합니다.

## 권하는 형태
종류만 고르게 하면 "OpenRouter와 사내 vLLM처럼 둘 다 `openai` 방식인 연결"을 구분할 수 없습니다.

```yaml
# application.yml: 기존 spring.ai.* 는 기본 연결로 그대로 둠
dstone.ai.providers:
  local:
    type: ollama
    base-url: http://localhost:11434
    model: qwen2.5-coder:7b
```
```yaml
# Agent .md: provider를 안 적으면 지금과 똑같이 동작
model:
  provider: local
  reasoning: none
```

기존 Agent는 고칠 필요가 없고, 없는 연결 이름을 적으면 부팅과 디자이너 [검사]에서 실패하게 합니다. 지금은 잘못된 모델명이 첫 호출에서야 드러나는데, 이 약점도 일부 줄어듭니다.

## 고칠 곳

| 위치 | 변경 내용 | 난이도 |
|---|---|---|
| ChatModel 생성 | 자동설정이 `spring.ai.model.chat` 값과 맞는 provider만 만들므로, 추가 연결은 직접 생성해야 함 | **가장 큼** |
| `AgentExecutor` (`buildSpec`, `reshape`) | 공용 ChatClient 하나 대신 연결별 ChatClient를 사용 | 작음 |
| `ConfigChatClient.requestOptions()` | provider를 설정에서 읽지 않고 인자로 받음(provider별 분기는 이미 있음) | 작음 |
| `AgentDefinition`, `AgentRegistry` | `model.provider` 추가와 검증 | 작음 |
| `ChatController` | 응답의 provider와 기본 모델 계산을 Agent 기준으로 변경 | 작음 |
| `ScriptedChatModel` | 추가 연결도 테스트 대역으로 대체 | 중간 |
| 디자이너, 문서 | Agent 대화상자의 선택 항목, `docs/09`, `application.yml` 주석 | 작음 |

추가 연결은 Spring 빈으로 등록하지 않고 별도 레지스트리에 두는 편이 안전합니다. ChatModel 빈이 늘면 자동설정과 충돌할 수 있기 때문입니다. 직접 생성할 때는 timeout, 재시도, Tool 호출 설정도 자동설정과 같게 맞춰야 합니다.

## 코드보다 큰 위험
1. **Ollama 경합:** Ollama는 요청을 사실상 하나씩 처리하고, 이 PC는 CPU로만 돌며 `dstone-knowledge` 임베딩과 같은 서버를 씁니다. forEach 병렬 호출이나 채팅이 임베딩과 줄을 서게 되는데, 지금의 `EmbeddingGate`는 검색만 보호합니다. 그래서 이 PC에서는 Ollama 연결을 쓰더라도 작은 단일 호출 Agent에만 쓰는 게 좋습니다.
2. **모델별 프롬프트 차이:** `pilot` 프롬프트와 `reasoning` 값은 `qwen3.8-27b`로 측정해 맞춘 것입니다. provider를 바꾸면 Tool 호출이나 구조화 출력의 품질이 달라져 다시 측정해야 합니다.
3. **설정 적용 범위:** `execution.timeoutSeconds`는 지금 `openai`에만 적용됩니다. Agent별로 provider가 갈리면 같은 키가 어떤 Agent에서는 무시되므로 함께 고치거나 검사에서 경고해야 합니다.
4. **대화 기억 공유:** 같은 `sessionId`로 provider가 다른 Agent를 번갈아 부르면 기록이 섞입니다. 일반 텍스트는 문제없을 것 같지만 확인이 필요합니다.

## 진행 순서
먼저 `openai` 방식의 추가 연결과 Ollama 연결만 넣고, 간단한 ROUTER Agent 하나를 Ollama로 돌려 Workflow 전체가 도는지 확인하는 것을 권합니다. Anthropic 직접 연결은 실제로 필요해질 때 추가해도 늦지 않습니다. provider 장애 시 다른 provider로 넘기는 fallback은 재시도와 Tool 부작용 문제까지 얽히므로 이번 범위에서는 뺍니다.

---

## 질문

지금은 spring.ai.model.chat 의 설정을 읽어서 model 및 provider 를 결정하는걸로 알고 있어.
그런데 spring.ai.model.chat 의 설정을 agent 별로도 설정할 수 있게 한다면 어떨까?

---

## 답변

**좋습니다. 제가 앞서 권한 이름 붙인 연결보다 이 방식이 더 단순해서, 이쪽으로 가는 것을 권합니다.** 설정을 새로 만들 필요 없이 `application.yml`에 이미 있는 `spring.ai.anthropic|openai|ollama.*`를 그대로 쓰면 되기 때문입니다.

```yaml
# Agent .md: provider를 안 적으면 지금처럼 spring.ai.model.chat 값을 따름
model:
  provider: ollama              # anthropic | openai | ollama
  name: qwen2.5-coder:7b        # 안 적으면 spring.ai.ollama.chat.options.model
```

## 지금 동작에서 짚을 점
- **provider와 모델은 따로 정해집니다:** `spring.ai.model.chat`은 provider만 정합니다. 기본 모델은 `spring.ai.{provider}.chat.options.model`에서 오고, Agent의 `model.name`이 있으면 그 값이 우선합니다.
- **설정값을 호출마다 바꾸는 방식으로는 안 됩니다:** `spring.ai.model.chat`은 기동할 때 어떤 ChatModel을 만들지 정하는 조건이라, 선택되지 않은 provider는 객체 자체가 없습니다. 따라서 나머지 provider의 ChatModel은 엔진이 `spring.ai.{provider}.*`를 읽어 직접 만들어야 합니다.

## 구현 방향
- **기본 provider:** 지금처럼 자동설정이 만든 ChatClient를 그대로 씁니다. `model.provider`가 없거나 기본 provider와 같은 Agent는 동작이 달라지지 않습니다.
- **다른 provider:** Agent가 처음 호출될 때 ChatModel과 ChatClient를 만들어 재사용합니다. 쓰지 않는 Anthropic의 API 키가 비어 있어도 기동이 실패하지 않게 하려는 것입니다.
- **설정값 반영:** 직접 만들 때 `max-tokens`, `temperature`, `timeout`, `max-retries`, `base-url`까지 그대로 넘겨야 합니다. 하나라도 빠지면 조용히 라이브러리 기본값으로 동작합니다. 앞서 겪은 60초 타임아웃과 같은 종류의 문제입니다.
- **옵션 생성:** `requestOptions()`가 provider를 인자로 받게 합니다. provider별 분기는 이미 있어서 수정이 작습니다.
- **검증:** 부팅과 디자이너 [검사]에서 값이 세 가지 중 하나인지 확인합니다. Agent가 고른 provider의 API 키가 없으면 첫 호출이 아니라 이 단계에서 실패하게 합니다.

앞서 말한 나머지 수정(`reshape`, `ChatController`, 테스트 대역, 디자이너, 문서)과 Ollama 경합 같은 위험은 그대로 남습니다. Agent의 `reshape` 호출도 그 Agent의 provider를 따라가야 합니다.

## 포기하는 것
provider 종류마다 연결이 하나뿐입니다. 예를 들어 OpenRouter와 사내 vLLM은 둘 다 `openai` 방식이라 함께 쓸 수 없습니다.

지금 필요한 조합인 OpenRouter + Ollama + Anthropic에는 문제가 없습니다. 나중에 필요해지면 `model.provider` 값을 연결 이름으로도 받도록 넓히면 되고, 기존 Agent 정의도 고칠 필요가 없습니다.
