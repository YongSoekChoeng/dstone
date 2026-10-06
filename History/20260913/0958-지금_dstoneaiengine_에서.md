# 지금_dstoneaiengine_에서

> 생성일시: 2026-09-13 09:58

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

---

## 질문

ollama 는 응답할 때 띄어쓰기가 안되나?
이 ai모델이 어느정도 스마트하게 응답처리를 할 수 있는지 궁금해.
ollama를 조금 더 똑똑하게 할 수 있는 방법이 있을까?

---

## 답변

llama3.2를 실제로 호출해서 확인해봤습니다.

---

## 답변

실제 테스트로 재현됐습니다. 짧은 질문엔 멀쩡하지만,

```
"저는 지능형 아이프onia 인입니다... 다양한 도메인에서 자격이 있습니다...
altri 언어를 포함합니다."
```

자유 응답에서는 "아이프onia"(한국어+영어가 공백 없이 붙음), "altri"(이탈리아어 단어가 갑자기 튐) 같은 깨짐이 나옵니다. 이건 버그가 아니라 **모델 자체의 한계**입니다 — 지금 붙어있는 `llama3.2:latest`는 3.2B 파라미터짜리 소형 모델이고(Q4_K_M 양자화), 학습 데이터가 영어 중심이라 한국어 토큰 경계 처리가 약합니다. Claude/GPT급 모델은 파라미터 규모와 다국어 학습량 자체가 다르니, 지금 환경(WSL, CPU 전용, RAM 8GB, GPU 없음)에서 로컬로 그 수준을 기대하긴 어렵습니다.

**똑똑하게 만드는 현실적인 방법 (자원 제약 고려)**

| 방법 | 효과 | 트레이드오프 |
|---|---|---|
| 모델 교체 (`ollama pull exaone3.5:2.4b` 또는 `qwen2.5:3b`) | 비슷한 크기에서 한국어 품질 크게 개선 (EXAONE은 LG가 한국어 특화 학습) | 다운로드/디스크 필요, 여전히 소형 모델의 reasoning 한계는 있음 |
| 시스템 프롬프트에 "한국어로만, 정확한 띄어쓰기로" 명시 (`dstone.ai.prompt`) | 즉시 적용 가능, 실제로 완화 효과 있음 | 근본 해결은 아님 |
| `temperature` 낮추기 (`spring.ai.ollama.chat.options.temperature`) | 헛소리/언어 뒤섞임 확률 감소 | 창의성/다양성 감소 |
| RAG로 사실 근거 제공 (이미 `dstone.ai.rag.enabled=true`) | 사실 기반 답변 품질 개선 | 언어 유창성 자체는 안 고쳐짐 |
| 더 큰 모델(7B~14B) | 확실히 똑똑해짐 | 지금 RAM 8GB/GPU 없는 환경에선 체감 속도 크게 느려질 가능성 높음 |

가장 먼저 시도해볼 만한 건 **모델 교체**입니다. 지금 크기 그대로 한국어 품질만 올릴 수 있어서 비용이 가장 낮습니다. `ollama pull exaone3.5:2.4b` 해보고 `spring.ai.ollama.chat.options.model`을 그걸로 바꿔서 비교해볼까요?
