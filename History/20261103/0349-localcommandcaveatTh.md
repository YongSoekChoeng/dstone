# localcommandcaveatTh

> 생성일시: 2026-11-03 03:49

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
