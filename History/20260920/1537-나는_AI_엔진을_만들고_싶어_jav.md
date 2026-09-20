# 나는_AI_엔진을_만들고_싶어_jav

> 생성일시: 2026-09-20 15:37

## 질문

나는 AI 엔진을 만들고 싶어.
java 기반의 spring ai를 사용해서 만들 예정이야.
그런데 하고자 하는 것을 조금 더 상세하게 설명하면 아래와 같아.

<기능>
1. 설정 파일(yml)만으로 작업흐름(Workflow)를 정의할 수 있어야 한다.
2. Workflow 실행모듈에서 작업흐름 설정을 보고 agent을 호출해서 작업처리를 한다.
3. 개별 기능은 agent로 구성한다.
    - agent 는 LLM 과 통신을 통해서 명령을 수행한다.
    - agent 는 자발적으로 Tool Calling 도 가능해야 한다.
    - agent 는 설정에 따라 RAG 기능도 지원해야 한다.
    - agent 는 병렬처리 작업도 가능해야 한다.
4. Workflow, agent 이 유연하고 확장성이 있어야 한다.

<의문점>
- dstone-ai-engine 을 사용해서 여러가지 업무에 적용할 수 있을만한 구조인가?

---

## 답변

Login expired · Please run /login

---

## 답변

프로젝트의 실제 구현 상태를 먼저 확인하겠습니다.

---

## 답변

코드를 직접 확인했습니다. 결론부터 말하면 **네 가지 요구사항은 이미 전부 구현되어 있고, "한 건의 입력을 단계적으로 가공·검증하는 선형 파이프라인" 업무군에는 지금 그대로 재사용 가능한 구조입니다.** 다만 업무 종류를 넓히면 바로 걸리는 구조적 제약이 몇 개 있습니다.

## 요구사항 대비 현황

| 요구 | 상태 | 근거 |
|---|---|---|
| YAML만으로 Workflow 정의 | ✅ | `resources/workflows/*.yml` → `YamlDefinitionLoader` → `WorkFlowRegistry`. 코드 변경 없이 파일 추가만으로 등록 |
| 실행모듈이 설정 보고 agent 호출 | ✅ | `WorkFlowExecutor.run()` — 순차/분기/병렬/루프 4패턴 상태기계 |
| agent = LLM 통신 | ✅ | `AgentExecutor.buildSpec()` 한 곳에서만 ChatClient 조립 |
| agent 자발적 Tool Calling | ✅ | `toolsEnabled: true` → Spring AI 툴콜 루프, `ConfigTool` caller별 화이트리스트 |
| agent RAG | ✅ | `ragEnabled`/`ragTopK`/`ragSimilarityThreshold` per-agent 튜닝 가능 |
| agent 병렬처리 | ✅ | `parallelGroup` → `WorkFlowExecutor.runGroup()` (`CompletableFuture`) |

`StepRunner` 인터페이스 하나로 4개 StepType을 균일하게 호출하는 점, `AgentExecutor`/`ToolExecutor`가 각각 단일 통로인 점은 확장 지점으로 잘 잡혀 있습니다.

## 다른 업무로 넓힐 때 바로 걸리는 것

**1. AGENT 스텝이 `inputTemplate`을 아예 무시합니다 (가장 큰 제약)**

`WorkFlowExecutor.java:212`에서 `new StepInput(previousText(execution), variables)` — 즉 `renderedText`는 직전 스텝 출력 원문 그대로입니다. 템플릿 치환은 `ToolStepRunner.java:46`에만 있습니다. 그래서 AGENT 스텝은 **"직전 스텝 출력 전체"만** 입력으로 받을 수 있고, `{원본}` + `{분석결과}` 같이 여러 값을 조합한 입력을 만들 수 없습니다. `StepInput`의 주석("AGENT/TOOL 공통으로 쓰는 입력 원문")과 실제 동작이 어긋나 있습니다.

**2. 스텝 간 구조화 데이터 전달 경로가 사실상 죽어 있습니다**

`StepOutput.data`로 전역 변수에 병합하는 배관은 있는데(`WorkFlowExecutor.java:223,275`), `StepOutput`의 팩토리 3개가 전부 `Map.of()` 하드코딩이라 **어떤 러너도 data를 채우지 않습니다**. 결과적으로 스텝 간 전달은 `__previous` 텍스트 한 줄뿐입니다. "문서에서 항목 N개 추출 → 각각 처리 → 집계" 형태 업무는 표현 불가입니다.

**3. 분기가 2갈래뿐입니다**

AGENT는 항상 성공, SUPERVISOR는 `pass/fail`. "LLM이 민원을 A/B/C로 분류해서 각기 다른 스텝으로" 같은 **다분기 라우팅이 없습니다.** 업무 자동화에서 매우 흔한 패턴입니다.

**4. 병렬이 정적이고 결과가 익명입니다**

`parallelGroup`은 YAML에 개수가 박혀 있어 "입력 N건 동적 fan-out"이 안 되고, `runGroup`은 결과를 `"\n"`으로 그냥 이어붙여서(`WorkFlowExecutor.java:266~278`) 어느 스텝 결과인지 구분이 안 됩니다. map-reduce형 업무에 부적합합니다. 또 `parallelGroupOf()`(`:384`)는 주석과 달리 인접성 검사 없이 전체 steps를 훑습니다 — 떨어져 있는 같은 그룹명 스텝도 묶입니다.

**5. 운영 관점 공백**

- `supplyAsync`가 `ForkJoinPool.commonPool` 사용(`:241`) — LLM 호출은 blocking I/O인데 풀 크기가 CPU 코어 수라 동시 실행이 늘면 서로 굶습니다. 전용 Executor가 필요합니다.
- 스텝별 **retry/timeout이 없습니다.** LLM 호출 한 번 실패 = Workflow 즉시 FAILED.
- `maxIterations`가 "전체 스텝 실행 횟수"라 스텝이 많아지면 업무마다 손으로 계산해 적어야 합니다(`oracle-to-postgresql.yml`의 긴 주석이 그 비용을 보여줍니다). 참고로 기본값은 `Constants.java:65`에서 10인데 `WorkFlowDefinition`/`WorkFlowExecutor` 주석은 5라고 적혀 있습니다.
- TOOL 스텝의 `inputTemplate`(`'{"sql":"{previous}"}'`)은 Tool 메서드 시그니처를 아는 사람만 쓸 수 있습니다. YAML만 보는 신규 업무 담당자에게는 진입장벽입니다.

## 판단

- **지금 구조로 충분한 업무**: SQL/코드 변환, 문서 요약·검수, 규정 준수 체크, 민원 답변 초안, 리뷰 자동화 — "입력 1건 → 단계별 가공 → 검증 루프 → 승인" 형태.
- **손봐야 하는 업무**: 배치성 N건 처리, 분류 기반 라우팅, 외부 시스템 상태에 따라 장기 대기하는 프로세스.

우선순위는 **1 → 2 → 3 → 4** 순을 권합니다. 특히 1번은 `inputTemplate` 렌더링을 `ToolStepRunner`에서 `WorkFlowExecutor.runOne/runGroup`으로 끌어올리는 것만으로 해결되고(AGENT/TOOL 공통화), 효과 대비 비용이 가장 낮습니다. 2번은 `StepDefinition`에 `outputVar`를 추가해 스텝 결과에 이름을 붙이면 이미 있는 병합 배관이 살아납니다. 이 둘만 해도 표현 가능한 업무 범위가 꽤 넓어집니다.

원하시면 1·2번부터 실제로 적용해 드리겠습니다.
