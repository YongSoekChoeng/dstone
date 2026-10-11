# localcommandcaveatTh

> 생성일시: 2026-10-11 14:04

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

현재 dstone-ai-engine 에 Langfuse 를 접목할 수 있나?

---

## 답변

dstone-ai-engine의 현재 의존성과 설정을 먼저 확인해 보겠습니다.

---

## 답변

네, 접목할 수 있습니다. 엔진 구조를 바꿀 필요 없이 의존성·설정 추가와 약간의 코드로 되는 수준입니다. 다만 아래는 코드와 설정을 읽고 판단한 것이고, 실제로 붙여서 돌려 본 것은 아닙니다.

## 왜 가능한가

- **Langfuse는 OpenTelemetry(OTLP)로 트레이스를 받습니다.** 전용 Java 트레이싱 SDK 없이 OTLP HTTP로 `/api/public/otel`에 보내면 됩니다(gRPC는 안 됨, 인증은 public/secret key의 Basic 헤더).
- **Spring AI 2.0.1은 이미 Micrometer Observation을 냅니다.** `ChatClient` 호출, 모델 호출(모델명·토큰 사용량), Tool 호출이 각각 span으로 나옵니다.
- **Actuator는 이미 들어 있습니다** (`pom.xml:62`). 없는 것은 tracing bridge와 OTLP exporter뿐입니다.

## 해야 할 일

1. **의존성 추가**: Micrometer tracing의 OTel bridge와 OTLP exporter(Spring Boot 4에서는 `spring-boot-starter-opentelemetry` 한 개로 묶여 있는 것으로 압니다).
2. **`conf/application.yml` 설정**: OTLP endpoint, 인증 헤더(`ENC(...)` 규칙대로), 샘플링 1.0, 프롬프트·응답 본문을 span에 싣는 Spring AI 옵션. 이 옵션의 키 이름은 버전마다 바뀌어 왔으니 2.0.1 기준으로 확인해야 합니다.
3. **Workflow 단위로 묶는 코드**: 설정만으로는 LLM 호출이 낱개 트레이스로 흩어집니다. `WorkFlowExecutor`에서 실행 1건을 상위 span으로, step을 하위 span으로 열고 `executionId`·`sessionId`·caller·agent id/version을 속성으로 달아야 "Workflow → Step → Agent → Tool" 모양으로 보입니다.
4. **스레드 간 컨텍스트 전파**: `forEach` 병렬 실행과 `/submit` 비동기 실행은 다른 스레드에서 돌기 때문에, executor에 컨텍스트 전파를 걸지 않으면 트레이스가 끊깁니다. 여기가 가장 손이 가는 부분입니다.
5. **TOOL step용 span**: LLM을 거치지 않는 TOOL step은 Spring AI 관측에 잡히지 않으므로 `ToolExecutor.call()`에서 span을 직접 열어야 합니다.

## 미리 알아둘 점

- **Langfuse 서버가 무겁습니다.** v3 self-host는 PostgreSQL 외에 ClickHouse, Redis, S3 호환 저장소(MinIO)가 필요합니다. 폐쇄망 SI 현장이면 self-host가 사실상 필수입니다.
- **프롬프트 본문에 소스 코드가 실립니다.** `pilot` Agent의 입력에는 고객 소스와 요청서가 들어가므로, 본문 전송은 설정으로 끌 수 있게 두는 편이 좋습니다.
- **Tool 결과가 크면 span도 커집니다.** `stepOnly` Tool은 결과 길이 제한이 없으니, span에는 잘라서 싣는 처리가 필요합니다.
- **Langfuse의 프롬프트 관리 기능은 쓰지 않는 것을 권합니다.** 프롬프트는 `definitions/prompts/**.st` 파일과 부팅 검증이 기준이라, 두 군데서 관리하면 어긋납니다. 트레이싱·비용·평가 용도로만 쓰는 것이 맞습니다.
- **기존 로그와 역할이 겹칩니다.** `LlmLoggerAdvisor`와 `ConfigCallLog`는 그대로 두고, Langfuse는 설정으로 켜고 끄는 선택 기능(기본 off)으로 넣는 것이 안전합니다.

## 권장 순서

먼저 1·2번만 넣고 로컬 Langfuse(docker compose)에 채팅 API 한 건을 보내, 2.0.1에서 span과 토큰 수가 실제로 어떻게 찍히는지 확인합니다. 그 결과를 보고 3~5번을 붙이는 순서가 좋습니다.

진행하길 원하시면 1단계 검증부터 시작하겠습니다. 이 박스에 Langfuse를 docker로 새로 띄워야 하는데, 그래도 되는지만 알려 주세요.
