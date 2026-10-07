# dstone-ai-engine × Spring AI Alibaba(SAA) 통합 전략 및 설계 정리

- 작성일: 2026-10-07
- 대상 프로젝트: `dstone-ai-engine`
- 기준 환경: Spring Boot 4.x / Spring AI 2.0.1 / Ollama
- 목적: AI Agent Workflow, Prompt/Context Engineering, Model Routing, Tool/RAG, Human Approval을 통합한 폐쇄망용 AI Engineering Platform 구축

---

## 1. 배경과 문제 정의

현재 `dstone-ai-engine`은 다음과 같은 방향으로 개발되고 있다.

- YAML 기반 Workflow 정의
- WorkflowStep → Agent → Tool → Loop/NextStep 구조
- Spring AI 기반 LLM 호출
- Ollama 기반 로컬 모델
- MCP 기반 외부/시스템 Tool
- 향후 Java Source Analysis + Knowledge Graph + RAG
- 요구사항 분석 → 영향도 분석 → 리뷰 → 관리자 승인 → 설계 → 구현 → 승인 Workflow

현재 가장 큰 문제는 두 가지다.

### 1.1 Prompt가 지나치게 길고 복잡함

기존 Agent Prompt에 다음 내용이 한꺼번에 들어가기 쉽다.

- Agent 역할
- 업무 규칙
- Workflow 설명
- 입력 데이터
- 이전 Step 결과
- Tool 설명
- RAG 결과
- JSON 출력 규칙
- 검증 규칙
- 예외 처리 규칙
- 추론 지침

이 구조는 Prompt Token과 Context를 불필요하게 증가시키고, 로컬 Ollama 모델에서는 처리 지연을 증가시킬 수 있다.

### 1.2 LLM에게 너무 많은 일을 맡김

예를 들어 Requirement Analyzer가 다음을 모두 수행하도록 하면 Agent가 과도하게 복잡해진다.

- 요구사항 추출
- Java 호출관계 분석
- DB 영향 분석
- RAG 검색
- 영향도 판단
- 구현 방법 판단
- 결과 검증
- JSON 생성

이 경우 작은/중간 크기 모델에서는 추론 시간이 증가하고 결과의 안정성도 떨어질 수 있다.

---

# 2. SAA를 도입하는 이유

Spring AI Alibaba(SAA)는 단순한 LLM 호출 라이브러리보다 Agentic Workflow와 Context Engineering에 초점을 둔다.

주요 개념:

- Graph
- StateGraph
- Node
- Edge
- Conditional Edge
- CompiledGraph
- Sequential
- Parallel
- Routing
- Loop
- Supervisor
- Human-in-the-loop
- Context Engineering
- Context Compaction
- Context Editing
- Tool Call Limit
- Model Call Limit
- Dynamic Tool Selection
- Planning
- Tool Retry

따라서 SAA를 `dstone-ai-engine`의 모든 기능을 대체하는 프레임워크로 보기보다는 다음과 같이 사용하는 것이 적합하다.

> dstone = Workflow DSL + Governance + Domain Model + Enterprise Platform
>
> SAA = Agent/Graph 실행 Runtime으로 활용할 수 있는 Adapter/Engine

---

# 3. 핵심 설계 원칙

## 3.1 Workflow는 LLM에게 맡기지 않는다

Workflow의 흐름은 애플리케이션이 결정한다.

```text
Workflow Engine
      |
      +-- Requirement Analysis
      |
      +-- Impact Analysis
      |
      +-- Review
      |
      +-- Human Approval
      |
      +-- Architecture
      |
      +-- Implementation
```

LLM은 각 Node 안에서 필요한 판단을 수행한다.

즉:

```text
Workflow Control = Application
Business Judgment = LLM
```

으로 분리한다.

---

## 3.2 State를 중심으로 설계한다

기존 방식:

```text
step01.output
   |
   v
step02.input
```

보다 다음과 같은 State 중심 모델을 권장한다.

```text
Workflow State

requirementAnalysis
impactAnalysis
review
approval
architecture
implementation
```

각 Step은:

```yaml
reads:
  - requirementAnalysis

writes:
  - impactAnalysis
```

처럼 선언한다.

이렇게 하면 Step 간 input/output 매핑을 일일이 작성할 필요가 줄어든다.

---

# 4. 권장 전체 아키텍처

```text
                    dstone Workflow YAML
                            |
                            v
                   Workflow Compiler
                            |
                            v
                   WorkflowDefinition
                            |
                +-----------+-----------+
                |                       |
                v                       v
        dstone Graph Model       SAA Graph Adapter
                |                       |
                v                       v
        dstone Runtime             StateGraph
                |                       |
                +-----------+-----------+
                            |
                            v
                    Agent / Tool / Human
                            |
            +---------------+---------------+
            |               |               |
            v               v               v
        Spring AI         MCP              RAG
            |
            v
          Ollama
```

---

# 5. Workflow YAML의 권장 방향

YAML을 dstone의 Canonical Source of Truth로 유지한다.

예:

```yaml
workflow:
  id: application-analysis
  version: 1.0

  state:
    requirementAnalysis:
      schema: RequirementAnalysis

    impactAnalysis:
      schema: ImpactAnalysis

    review:
      schema: ReviewResult

  steps:

    - id: requirement-analysis
      type: AGENT
      agent: requirement-analyzer
      writes:
        - requirementAnalysis

    - id: impact-analysis
      type: AGENT
      agent: impact-analyzer
      reads:
        - requirementAnalysis
      writes:
        - impactAnalysis

    - id: impact-review
      type: AGENT
      agent: impact-review-agent
      reads:
        - requirementAnalysis
        - impactAnalysis
      writes:
        - review

      transitions:
        - condition: review.pass == true
          next: approval

        - condition: review.pass == false
          next: impact-analysis

    - id: approval
      type: HUMAN_APPROVAL
      reads:
        - requirementAnalysis
        - impactAnalysis
        - review
```

핵심은 `input/output`보다 `reads/writes`를 중심으로 하는 것이다.

---

# 6. Workflow Compiler

권장 구조:

```text
workflow.yml
     |
     v
YamlParser
     |
     v
WorkflowDefinition
     |
     +-- StateDefinition
     +-- StepDefinition
     +-- AgentDefinition
     +-- TransitionDefinition
     +-- ToolDefinition
     |
     v
Workflow Graph
```

Java 개념 모델:

```java
class WorkflowDefinition {
    String id;
    String version;
    Map<String, StateDefinition> states;
    List<WorkflowStepDefinition> steps;
}
```

각 Step은 다음과 같은 정보를 가진다.

```java
class WorkflowStepDefinition {
    String id;
    StepType type;
    String agent;
    List<String> reads;
    List<String> writes;
    List<TransitionDefinition> transitions;
}
```

---

# 7. SAA와의 접목 방법

SAA의 `StateGraph`를 dstone의 내부 DSL로 직접 노출시키지 않는다.

대신:

```text
dstone WorkflowDefinition
        |
        v
SaaGraphCompiler
        |
        v
StateGraph
        |
        v
CompiledGraph
```

구조를 만든다.

예:

```java
class SaaGraphCompiler {

    CompiledGraph compile(WorkflowDefinition definition) {
        // WorkflowDefinition -> StateGraph
    }
}
```

이렇게 하면 향후 SAA가 변경되어도 dstone DSL은 유지할 수 있다.

---

# 8. Graph UI 전략

SAA에는 Studio/Admin 계열의 시각화/디버깅/관측 기능이 존재한다.

다만 다음 기능은 구분해야 한다.

| 기능 | 판단 |
|---|---|
| Graph 실행 | 가능 |
| Workflow 시각화 | 가능 |
| Runtime 관측 | 가능 |
| Trace/Observability | 가능 |
| Mermaid/PlantUML 표현 | 가능 |
| Agent 시각 개발 | 지원 |
| 완전한 Drag & Drop Workflow Designer | 별도 검토 필요 |
| dstone YAML ↔ SAA 양방향 변환 | dstone에서 구현 권장 |

따라서 dstone에서는:

```text
YAML = Canonical Source of Truth
```

로 유지하고,

```text
React Flow = Workflow Designer
Mermaid = Documentation / Read-only View
SAA Studio/Admin = Runtime/Agent 운영/관측 보조
```

구조를 권장한다.

React Flow는 편집 가능한 Workflow Designer에 더 적합하고, Mermaid는 문서화/조회에 적합하다.

---

# 9. Spring Boot 4 / Spring AI 2.0.1 환경

현재 dstone의 기준 환경은:

```text
Spring Boot 4.x
Spring AI 2.0.1
```

이다.

Spring AI 2.x는 Spring Boot 4.x 계열에 대응한다.

SAA는 기존 1.x 계열이 Spring AI 1.x / Spring Boot 3.5.x를 기반으로 했지만, Boot 4 / Spring AI 2.0 계열을 위한 2.0 milestone 계열도 존재한다.

따라서 SAA를 검토할 때는 반드시:

```text
dstone
Spring AI 2.0.1
Spring Boot 4.x
        |
        +--- SAA 2.x compatibility
```

를 실제 dependency 기준으로 검증해야 한다.

특히 SAA 2.x가 GA인지 milestone인지, 사용하는 정확한 버전의 Spring AI 2.0.1과 binary compatibility가 맞는지는 도입 시점에 확인해야 한다.

---

# 10. 가장 중요한 문제: Prompt Engineering

SAA를 도입한다고 Prompt 자체가 자동으로 좋아지는 것은 아니다.

진짜 개선 방향은:

```text
Prompt Engineering
        +
Context Engineering
        +
Agent Decomposition
        +
Model Routing
```

이다.

현재 dstone의 거대한 Prompt:

```text
System Prompt
+ Agent Role
+ Workflow
+ Rules
+ Tool descriptions
+ RAG
+ Previous results
+ JSON rules
+ Validation
+ Reasoning instructions
```

을 다음처럼 분리한다.

```text
Agent
 |
 +-- Small System Prompt
 |
 +-- Context Builder
 |
 +-- State
 |
 +-- Required Tools
 |
 +-- Model
 |
 +-- Structured Output
```

---

# 11. Context Engineering 구조

권장 Context Pipeline:

```text
Workflow State
      |
      v
Context Builder
      |
      +-- required State
      +-- relevant documents
      +-- required RAG evidence
      +-- selected Tools
      |
      v
Minimal Prompt
      |
      v
LLM
```

중요한 원칙:

> Agent에게 State 전체를 전달하지 않는다.

Agent가 실제로 필요한 데이터만 Context로 만든다.

예:

```yaml
agent: impact-analyzer

reads:
  - requirementAnalysis

context:
  include:
    - requirementAnalysis
    - javaImpactEvidence
    - dbImpactEvidence
```

---

# 12. Agent Decomposition

Requirement Analyzer 하나가 모든 일을 하지 않도록 한다.

권장 구조:

```text
Requirement
    |
    v
Requirement Extractor
    |
    v
Requirement Normalizer
    |
    +--------------------+
    |                    |
    v                    v
Java Analysis       DB Analysis
    |                    |
    +---------+----------+
              |
              v
       Impact Aggregator
              |
              v
       Impact Analyzer
              |
              v
          Reviewer
```

각 Agent의 책임을 작게 유지한다.

---

# 13. Requirement Extractor 예시

거대한 Prompt 대신:

```text
SYSTEM

Extract explicit requirements from the input.

Rules:
- Do not analyze implementation.
- Do not infer architecture.
- Do not determine impact.
- Extract facts explicitly stated or directly implied.

Return structured RequirementItem objects.
```

정도로 제한한다.

Java:

```java
record RequirementItem(
    String id,
    String description,
    String actor,
    String action,
    String target,
    String constraint
) {}
```

LLM 결과를 구조화된 객체로 받으면 JSON 형식 자체를 Prompt로 반복 설명할 필요가 줄어든다.

---

# 14. Deterministic Analysis와 LLM의 분리

Java Source Analysis는 LLM에게 맡기지 않는 것을 원칙으로 한다.

```text
JavaParser
JavaSymbolSolver
Knowledge Graph
```

로 가능한 것은 deterministic하게 처리한다.

예:

```text
Class
Method
Interface
Implementation
Caller
Callee
Dependency
Annotation
Repository
Controller
Service
Mapper
SQL
```

DB 분석도 가능한 부분은:

```text
SQL Parser
Schema Metadata
Rule Engine
```

으로 처리한다.

LLM은 다음과 같은 판단에 집중한다.

```text
"이 변경사항이 실제 업무 영향도를 가지는가?"
"여러 분석 결과를 종합했을 때 영향도가 High인가?"
"이 설계안이 요구사항을 만족하는가?"
```

---

# 15. Tool Engineering

Tool이 많아지면 모든 Tool을 Agent에 노출하지 않는다.

나쁜 구조:

```text
Agent
  |
  +-- searchClass
  +-- searchMethod
  +-- findCaller
  +-- findCallee
  +-- findInterface
  +-- searchSql
  +-- searchTable
  +-- searchMapper
  +-- searchConfig
  +-- ...
```

권장 구조:

```text
Agent
  |
  v
Tool Selector
  |
  +-- Java tools
  +-- DB tools
  +-- RAG tools
  +-- Project tools
```

필요한 Tool만 동적으로 노출한다.

Spring AI 2.x의 Tool Search 계열 기능과 SAA의 Dynamic Tool Selection/Progressive Disclosure 방향을 함께 검토할 수 있다.

---

# 16. Context Compaction

Agent Loop가 길어지면:

```text
Prompt
+ Tool Call 1
+ Tool Result 1
+ Tool Call 2
+ Tool Result 2
+ Tool Call 3
+ Tool Result 3
...
```

Context가 계속 증가한다.

권장 방식:

```text
Raw Tool Results
      |
      v
Evidence Extractor
      |
      v
Compact Evidence
      |
      v
Agent Context
```

즉 Tool 결과 전체를 계속 들고 가지 않고 Agent가 다음 판단에 필요한 핵심 Evidence만 State에 저장한다.

---

# 17. Model Routing

모든 Agent를 동일한 모델로 실행하지 않는다.

예:

```yaml
agents:

  requirement-extractor:
    model: local-small
    reasoning: false

  requirement-normalizer:
    model: local-small
    reasoning: false

  impact-analyzer:
    model: local-medium
    reasoning: true

  architecture-designer:
    model: local-large
    reasoning: true

  reviewer:
    model: local-medium
    reasoning: true
```

개념적으로:

```text
                 Task
                  |
         +--------+--------+
         |        |        |
      Simple   Normal    Hard
         |        |        |
       Small    Medium    Large
```

로 분배한다.

---

# 18. 추론 비용을 줄이는 핵심 전략

## 18.1 모든 작업에서 reasoning 사용 금지

단순 추출:

```text
reasoning = false
```

구조화:

```text
reasoning = false
```

단순 분류:

```text
reasoning = false
```

복합 영향 판단:

```text
reasoning = true
```

설계 판단:

```text
reasoning = true
```

---

## 18.2 LLM 호출 횟수를 줄인다

나쁜 구조:

```text
Agent
 -> LLM
 -> Tool
 -> LLM
 -> Tool
 -> LLM
 -> Tool
 -> LLM
```

가능하면:

```text
Deterministic Tools
      |
      v
Evidence
      |
      v
Single Reasoning Call
```

로 만든다.

---

# 19. SAA가 해결하는 부분과 해결하지 못하는 부분

| 문제 | SAA 도움 |
|---|---:|
| Workflow Graph | 매우 큼 |
| Agent 분해 | 큼 |
| Routing | 큼 |
| Loop 제어 | 큼 |
| Parallel | 큼 |
| Human Approval | 큼 |
| Context 관리 | 큼 |
| Tool 관리 | 큼 |
| Tool Retry | 큼 |
| Model Call 제한 | 큼 |
| Prompt 자동 최적화 | 제한적 |
| 모델 자체 추론속도 | 없음 |
| 모델 intelligence | 없음 |
| Ollama inference 성능 | 없음 |
| 좋은 Domain Prompt 설계 | 결국 dstone에서 해야 함 |

따라서:

> SAA = Agent Runtime/Orchestration/Context Engineering에 강함
>
> dstone = Domain Knowledge/Governance/Workflow DSL/Enterprise Integration에 강함

으로 역할을 나누는 것이 좋다.

---

# 20. 최종 권장 dstone 구조

```text
dstone-ai-engine
│
├── workflow
│   ├── yaml
│   ├── compiler
│   ├── graph
│   └── runtime
│
├── agent
│   ├── definition
│   ├── executor
│   ├── context
│   ├── routing
│   └── supervisor
│
├── model
│   ├── ModelRouter
│   ├── ModelPolicy
│   └── OllamaProvider
│
├── tool
│   ├── ToolRegistry
│   ├── ToolSelector
│   └── MCP
│
├── rag
│   ├── retrieval
│   ├── evidence
│   └── context
│
├── knowledge
│   ├── java
│   ├── graph
│   └── metadata
│
├── governance
│   ├── approval
│   ├── audit
│   └── policy
│
└── adapter
    └── saa
        └── SaaGraphCompiler
```

---

# 21. 권장 실행 모델

최종적으로 다음 구조를 목표로 한다.

```text
                     Workflow YAML
                          |
                          v
                  Workflow Compiler
                          |
                          v
                  Execution Graph
                          |
        +-----------------+-----------------+
        |                 |                 |
        v                 v                 v
   Agent Node        Tool Node        Human Node
        |                 |                 |
        v                 v                 |
 Context Builder      MCP/Tools             |
        |                 |                 |
        +--------+--------+                 |
                 v                          |
              State <-----------------------+
                 |
                 v
             ModelRouter
                 |
       +---------+---------+
       |         |         |
      Small    Medium     Large
       |         |         |
       +---------+---------+
                 |
                 v
             Ollama
```

---

# 22. 최종 판단

현재 dstone-ai-engine을 버리고 SAA로 전환하는 것은 권장하지 않는다.

대신:

```text
dstone = Platform
SAA    = Agent/Graph Runtime
Spring AI = Model/Tool abstraction
Ollama = Local Model Runtime
MCP    = External Tool Protocol
RAG    = Knowledge Retrieval
React Flow = Workflow Designer
```

라는 역할 분리가 가장 적합하다.

특히 현재 사용자의 가장 큰 문제인:

```text
긴 Prompt
   +
큰 Context
   +
너무 많은 Tool
   +
모든 작업에 reasoning
   +
Agent 하나가 너무 많은 책임
```

을:

```text
짧은 Prompt
   +
필요한 Context만
   +
Dynamic Tool Selection
   +
Model Routing
   +
작은 Agent
   +
Deterministic Analysis
   +
SAA Graph/Workflow
```

로 전환하는 것이 핵심이다.

---

# 23. 다음 구현 단계

권장 순서는 다음과 같다.

### Phase 1 — 현재 dstone Prompt 구조 개선

`01.requirement-analyzer-agent.yml`을 대상으로:

```text
Large Prompt
→ Short Prompt
→ State
→ Context Builder
→ Structured Output
```

으로 리팩터링한다.

### Phase 2 — Model Routing

```text
Simple → Small Model
Normal → Medium Model
Reasoning → Large Model
```

정책을 dstone에 추가한다.

### Phase 3 — Tool Context 최적화

```text
Static Tool List
→ Dynamic Tool Selection
```

으로 변경한다.

### Phase 4 — Evidence 기반 RAG

```text
Raw Document
→ Retrieval
→ Evidence
→ Compact Context
→ LLM
```

으로 변경한다.

### Phase 5 — SAA Graph Adapter

```text
WorkflowDefinition
→ SAA StateGraph
→ CompiledGraph
```

을 구현한다.

### Phase 6 — Workflow Designer

```text
Workflow YAML
↔ WorkflowDefinition
↔ React Flow
```

을 구현한다.

### Phase 7 — Governance

```text
Review
→ Human Approval
→ Audit
→ Resume
```

을 Graph Runtime과 통합한다.

---

# 24. 한 문장으로 정리

> **dstone-ai-engine의 방향은 SAA로 대체하는 것이 아니라, dstone을 Enterprise AI Engineering Platform으로 유지하면서 SAA의 Graph/Agent/Context Engineering 능력을 Runtime 계층에 흡수하고, Spring AI 2.0.1의 Structured Output/Tool/Model 기능과 결합하는 것이다.**

이 구조가 현재 목표인 금융 SI 폐쇄망용 AI Engineering Platform에 가장 적합한 방향이다.
