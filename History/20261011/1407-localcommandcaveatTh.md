# localcommandcaveatTh

> 생성일시: 2026-10-11 14:07

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

현재 dstone-ai-engine 에 Spring AI Playground 를 접목할 수 있나?

---

## 답변

Spring AI Playground의 현재 버전·요구사항과 이 엔진의 설정을 먼저 확인하겠습니다.

---

## 답변

**결론: "엔진 안에 넣는 것"은 안 되고, "옆에 띄워 MCP로 연결하는 것"은 가능합니다.** 아래는 Playground의 GitHub README와 엔진 코드를 읽고 판단한 것이고, 실제로 띄워서 연결해 보지는 않았습니다.

## 왜 내장은 안 되나

Spring AI Playground는 라이브러리가 아니라 독립 실행 앱입니다(Vaadin UI, 포트 8282, 데스크톱 앱·Docker·fat JAR). 자기 `ChatClient`, 자기 벡터 저장소(`SimpleVectorStore`), 자기 Tool 런타임을 갖고 있어서 엔진의 Workflow/Agent YAML, `AgentExecutor`, `dstone-knowledge` RAG를 전혀 모릅니다. 의존성으로 넣어 쓰는 방법도 README에 없습니다.

## 가능한 접목 방식

| 방식 | 내용 | 엔진 쪽 작업 | 얻는 것 |
|---|---|---|---|
| **A. 엔진 → Playground** (추천) | Playground의 내장 MCP 서버(`/mcp`)를 `definitions/mcp/*.yml`에 등록 | `McpTransport`에 streamable-http 추가 (`ConfigMcp`가 지금은 STDIO/SSE만 지원) | Tool Studio에서 만든 JavaScript Tool과 Playground가 프록시하는 외부 MCP 서버 Tool을 Agent의 `tools.allowed`나 TOOL 스텝에서 사용 |
| **B. Playground → 엔진** | 엔진에 MCP 서버 스타터를 넣어 `@AiTool`을 MCP로 노출 | 중간 규모: 의존성 추가, `stepOnly`·caller 화이트리스트 반영, 인증 | 엔진의 Tool을 Playground 화면에서 손으로 호출해 보는 시험대 |
| **C. 연결 없이 나란히** | 같은 Ollama/OpenRouter를 바라보게만 설정 | 없음 | 프롬프트·모델 파라미터를 빠르게 실험 |

## 알고 계셔야 할 제약

- **모델 제공자**: Playground는 Ollama와 OpenAI 호환 API만 지원합니다. OpenRouter는 맞지만 엔진 기본값인 Anthropic 직접 연결은 안 됩니다.
- **Workflow는 대상 밖**: Playground는 단일 Agent 채팅만 하고 멀티 Agent 오케스트레이션은 범위 밖이라고 명시합니다. `pilot-workflow` 같은 흐름을 시험하는 도구가 되지는 못합니다.
- **RAG는 따로 놉니다**: Playground의 RAG 실험 결과는 `dstone-knowledge`의 하이브리드 검색과 무관합니다.
- **JS Tool의 성격**: Tool Studio의 Tool은 브라우저에서 작성한 JavaScript라 `mvn test`로 검증되지 않습니다. 샌드박스도 README 스스로 "적대적 수준의 격리는 아니다"라고 적고 있어, 운영보다는 개발·시연용으로 보는 게 맞습니다.
- **버전**: Playground가 쓰는 Spring AI 버전은 "최신 릴리스를 따라간다"고만 되어 있어 확인하지 못했습니다. A·B는 MCP 프로토콜로만 만나므로 엔진(Spring AI 2.0.1 / Boot 4.1.1)과 버전이 달라도 문제가 되지 않습니다.
- **프로젝트 상태**: 커뮤니티 인큐베이팅 단계이고, 외부 기여는 버그 신고와 문서 수정만 받습니다.

## 추천

해 본다면 A가 가장 작고 엔진 설계와도 맞습니다. `ConfigMcp`의 transport 분기에 한 줄기를 추가하고 YAML 하나를 두면 되며, `allowedTools`와 caller 화이트리스트, `LimitedToolCallback`이 그대로 적용됩니다. 다만 "화면에서 Tool을 빨리 만들어 Agent에 붙여 본다"는 필요가 실제로 있을 때만 의미가 있고, Workflow 개발·디버깅을 돕는 화면이 목적이라면 Playground로는 해결되지 않습니다.

원하시면 A를 구현하고 Playground를 다른 포트에 띄워 실제 연결까지 확인하겠습니다.

Sources:
- [spring-ai-community/spring-ai-playground (GitHub)](https://github.com/spring-ai-community/spring-ai-playground)
- [Project Request: Spring AI Playground #14](https://github.com/spring-ai-community/community/issues/14)
- [Glama: spring-ai-playground](https://glama.ai/mcp/servers/spring-ai-community/spring-ai-playground)
