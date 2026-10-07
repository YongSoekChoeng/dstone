# 지금_dstoneaiengine_에서

> 생성일시: 2026-09-13 09:33

## 질문

지금 dstone-ai-engine 에서 LLM을 anthropic 을 사용하지 않고 ollama로 사용하려 한다면 아래 부분만 변경하면되는게 아닌가?
<application.yml>
spring.ai.model.chat: ollama

---

## 답변

dstone-ai-engine 코드 상태를 먼저 확인해보겠습니다.

---

## 답변

확인해봤습니다. 결론부터 말하면 **아니요, 그 한 줄만으로는 안 됩니다.**

## Java 코드는 손댈 필요 없음 (맞는 부분)

이미 Phase 1 게이트웨이 패턴이 코드에 구현돼 있어서:
- `pom.xml`에 anthropic/openai/ollama 스타터가 전부 이미 포함돼 있고 (`dstone-ai-engine/pom.xml:88-99`)
- `AiProvider` enum에 `OLLAMA`가 이미 있고 (`common/consts/AiProvider.java:26`)
- `ConfigChatClient`는 provider 무관하게 `ChatClient.Builder`만 주입받아 build()만 함 (`config/ConfigChatClient.java:16`)

→ `spring.ai.model.chat` 값만 바꾸면 실제로 활성화되는 provider의 자동설정이 통째로 바뀌는 구조가 맞습니다.

## 하지만 application.yml에서 추가로 손볼 게 있습니다

1. **YAML 키 중복 문제** — `application.yml:112-116`에 주석 처리된 `# ollama:` chat 설정 블록이 있는데, 이걸 그대로 주석 해제하면 **123번째 줄에 이미 있는 임베딩용 `ollama:` 블록과 같은 레벨에 키가 두 번** 생깁니다. YAML은 같은 레벨 중복 키를 뒤엣것이 덮어써버려서, 임베딩(`bge-m3`) 설정이 날아갈 수 있습니다. 반드시 하나의 `ollama:` 블록으로 합쳐서 `chat.options.model`을 추가해야 합니다:
```yaml
ollama:
    base-url: http://localhost:11434
    chat:
        options:
            model: llama3.1   # 로컬에 pull된 채팅 모델
    embedding:
        model: bge-m3
```

2. **chat 모델 지정 필수** — 로컬 Ollama엔 지금 임베딩 전용 `bge-m3`만 설치돼 있습니다(`docs/software/14.ollama.md`). 이건 채팅을 못 하는 모델이라, `chat.options.model`을 비워두면 Spring AI 기본값(대략 mistral)으로 요청했다가 로컬에 없어서 실패합니다. `ollama pull llama3.1` 등으로 채팅 가능한 모델을 별도로 받아야 합니다.

## 참고로 발견한 별개 이슈

`ChatRequest.java:21-33` 주석에는 요청 단위로 `provider`/`ollamaModel`을 지정해 기본 provider와 별개로 라우팅하는 기능(`ConfigOllamaOverride`, `dstone.ai.gateway.ollama-override.*`)이 설명돼 있는데, 실제로는 `ConfigOllamaOverride` 클래스 자체가 존재하지 않고 `ChatService.getSpec()`도 이 필드들을 전혀 참조하지 않습니다 — 즉 지금은 문서만 있고 미구현 상태입니다. 요청에 `provider`를 넣어도 무시되고, 여전히 `spring.ai.model.chat` 전역 설정 하나로만 라우팅됩니다. (별도 작업 필요 여부는 말씀 주시면 확인하겠습니다.)
