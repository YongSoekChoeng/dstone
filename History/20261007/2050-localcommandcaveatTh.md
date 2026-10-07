# localcommandcaveatTh

> 생성일시: 2026-10-07 20:50

## 질문

<local-command-caveat>The command below was run directly in Claude Code, not sent to you as a request, and its output goes straight to the user. It's recorded here as context for later messages.</local-command-caveat>

---

## 질문

<command-name>/clear</command-name>
            <command-message>clear</command-message>
            <command-args></command-args>

---

## 질문

SAA를 도입해서 dstone-ai-engine 을 수정한다고 할 때 아래의 의견이 있는데 네 의견이 어떤지 말해줘.
<의견정리파일>
/app/dstone/docs/temp/dstone-ai-engine_SAA_통합전략_설계정리.md

---

## 답변

문서의 진단(긴 프롬프트, 누적되는 컨텍스트, LLM에 과한 책임)은 맞지만, 처방의 대부분은 SAA와 무관하고 SAA가 맡을 부분은 지금 엔진이 이미 하고 있어서 **SAA Graph 도입(Phase 5)은 보류**하는 쪽이 제 의견입니다. 문서도 §10·§19에서 프롬프트·모델 속도는 SAA가 못 푼다고 인정하고 있고, Phase 1~4는 전부 dstone 안에서 하는 일입니다.

## 동의하는 부분

- **§3.1 흐름은 애플리케이션, 판단은 LLM**: 지금 `WorkFlowExecutor` 구조와 같습니다.
- **§14, §18.2 결정적 분석을 먼저 하고 추론은 한 번에**: 가장 값진 항목입니다. 지금 엔진으로도 됩니다. AGENT 단계 앞에 TOOL 단계(`knowledgeImpact` 등)를 두고 결과를 `input`으로 넘기면 됩니다.
- **§16 Tool 결과 압축**: 실제로 겪은 문제입니다(41,524자 목록이 이후 17번의 LLM 호출에 재전송). 지금은 결과 크기 상한만 있고, 루프 도중 오래된 결과를 덜어내는 장치는 없습니다.
- **§18.1 Agent별 reasoning 끄기**: reasoning 토큰이 `max-tokens`를 다 써서 빈 응답이 나온 적이 있으니 효과가 있을 겁니다. `AgentDefinition`에 옵션 하나 추가하는 수준입니다.

## 동의하지 않는 부분

1. **SAA Graph의 이득이 작습니다.** §19 표의 "매우 큼"은 아무것도 없는 상태 기준입니다. 순차·분기·`forEach`·루프·승인 대기·재개·DB 이력·부팅 검증은 이미 있습니다. 반면 jq 식, 계약 검증, 승인 `routes`, 실행 이력 저장을 SAA의 State·Checkpoint 모델에 다시 맞춰야 합니다.

2. **§4의 런타임 두 개 병행은 가장 나쁜 선택입니다.** dstone Runtime과 SAA Adapter를 함께 두면 모든 Step 기능을 두 번 구현하고 두 번 검증해야 합니다. 쓴다면 교체, 아니면 안 쓰는 것이 맞습니다.

3. **§3.2 `reads/writes`는 현재 상태를 잘못 전제합니다.** 지금도 `step01.output → step02.input` 사슬이 아니라 하나의 컨텍스트 트리(`input`, `steps.<id>.output`)를 jq로 읽습니다. Step의 `writes`는 9월 28일에 일부러 없앤 step `output` 키를 되살리는 것이고, "호출받는 쪽이 계약을 가진다"는 결정과 충돌합니다. 또 State를 Agent 입력으로 바꾸는 과정이 암묵적이 됩니다.

4. **§12 Agent 잘게 쪼개기는 이 저장소의 실측과 반대입니다.** Extractor → Normalizer → Java/DB → Aggregator → Analyzer → Reviewer는 LLM 호출 6회 이상이라 §18.2와도 모순됩니다. step02에서 Sub Agent 4회 위임을 없앴을 때 732초가 211초로 줄었습니다. 느린 모델일수록 쪼갤 대상은 LLM Agent가 아니라 결정적 Tool 단계입니다.

5. **§15 Dynamic Tool Selection은 지금 규모에 과합니다.** Agent별 `tools` 목록이 이미 그 역할을 하고, 선택기를 두면 LLM 호출이 하나 더 생깁니다. 실제 문제는 Tool 개수가 아니라 Tool 결과 누적이었습니다.

6. **Phase 1의 "짧은 프롬프트"는 조심해야 합니다.** `pilot` Agent YAML은 파일당 9~22KB로 실제로 깁니다. 다만 규칙 상당수는 실측 실패 뒤에 넣은 것입니다(어느 Tool을 먼저 쓸지, 파일을 두 번 열지 말 것 등). 줄일 때마다 같은 요청으로 다시 재야 합니다.

7. **Model Routing과 Structured Output은 이미 있습니다.** `AgentDefinition.model`과 `SchemaOutputConverter`가 그것이고, 없는 것은 reasoning 옵션뿐입니다.

## 확인이 필요한 위험

- **버전 호환**: SAA가 Spring AI 2.0.1 / Boot 4를 GA로 지원하는지 저는 확인하지 못했습니다. 문서도 "milestone 계열"이라고만 적었습니다. milestone이면 금융 폐쇄망 반입 대상으로는 부담이고, 고객사 오픈소스 심사도 따로 확인해야 합니다.
- **§8 React Flow**: `dstone-boot`는 JSP이고 빌드 체인이 없습니다. 조회용이라면 이미 들여온 Cytoscape로 충분하고, 편집기는 필요가 확인된 뒤에 검토해도 됩니다.

## 권장 순서

1. AGENT 앞에 결정적 TOOL 단계를 두어 근거를 먼저 모으고 추론은 한 번에 한다(YAML만 수정).
2. Agent별 reasoning 옵션을 추가한다.
3. `AgentExecutor`의 Tool 루프를 직접 제어해 오래된 Tool 결과 정리와 호출 횟수 상한을 넣는다. Spring AI의 사용자 제어 Tool 실행 방식으로 가능할 것으로 보지만 2.0.1에서 확인이 필요합니다.
4. 프롬프트는 실측하면서 줄인다.
5. SAA는 위를 다 하고도 "이 기능이 꼭 필요한데 직접 만들기엔 크다"는 것이 구체적으로 나올 때 스파이크부터 한다.

3번을 직접 구현하면 SAA에서 실제로 탐나는 기능(Context Editing, Tool/Model Call Limit)은 대부분 확보됩니다.
