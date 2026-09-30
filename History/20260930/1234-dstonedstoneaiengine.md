# dstonedstoneaiengine

> 생성일시: 2026-09-30 12:34

## 질문

dstone/dstone-ai-engine/src/main/resources/agents/pilot/requirment-analyzer-agent.yml 에서 프롬프트 내의 {input} 를 어떻게 표현해야 실제로 들어오는 input 값으로 치환될 수 있는거야?

---

## 답변

짧게 답하면, **지금 구조에서는 `input: string`인 Agent의 프롬프트에 있는 `{input}`은 치환되지 않습니다.**

## 이유

`AgentExecutor.promptVariables()` (`src/main/java/net/dstone/ai/runtime/agent/AgentExecutor.java:193`)는 프롬프트 변수를 **Agent input이 object(Map)일 때 그 필드들로만** 만듭니다.

```java
if (input instanceof Map) {
    merged.putAll((Map<String, Object>) input);
}
```

`requirment-analyzer-agent`는 `input: string`이라서 변수 맵이 비어 있습니다. 그래서 `{input}`을 채울 값이 없습니다. 이 경우 Spring AI `PromptTemplate`이 "변수 누락"으로 예외를 낼 수도 있습니다. 문자열 input은 프롬프트(system)가 아니라 **사용자 메시지(user)로만** 전달됩니다.

## 방법 1 (권장): Agent input을 object로 선언하고, 필드 이름으로 참조

`sample/sample-role-reply-agent.yml`의 `{role}`과 같은 방식입니다.

**agents/pilot/requirment-analyzer-agent.yml**
```yaml
agent:
  id: requirment-analyzer-agent
  description: 요구사항 분석/정의 에이젼트
  prompt: |
    [역할] 너는 고객의 업무를 이해하는 시니어 업무 분석가다.
    [컨텍스트] 입력으로 받은 {workDir}/00-request.md 를 읽어라.
    ...
    [산출물] {workDir}/01-requirements.md
    ...
  input:
    schema:
      type: object
      properties:
        workDir: { type: string, description: 수정아이디 경로(prompt의 workDir 변수를 채움) }
      required: [workDir]
  toolsEnabled: true
  ragEnabled: false
```

**workflows/pilot/pilot-workflow.yml** (Workflow input은 그대로 string으로 두고, step에서 맵으로 감쌉니다)
```yaml
    - id: step01
      type: AGENT
      ref: requirment-analyzer-agent
      input:
        workDir: "${ .input }"
      onSuccess: SUCCESS
      onFailure: FAIL
```

변수 이름은 `input`으로 지어도 동작합니다(`{input}` + `input: {input: "${ .input }"}`). 다만 Workflow의 `.input`과 헷갈리기 쉬워서 `workDir`처럼 뜻이 드러나는 이름을 추천합니다.

## 방법 2: 프롬프트에서 플레이스홀더를 빼기

string input은 어차피 사용자 메시지로 LLM에 전달됩니다. 그래서 프롬프트를 이렇게 바꿔도 됩니다.

```
[컨텍스트] 사용자 메시지로 받은 경로(이하 '작업경로')의 00-request.md 를 읽어라.
[산출물] 작업경로/01-requirements.md
```

경로가 프롬프트 안에 문자 그대로 박히지 않아서 LLM이 경로를 정확히 쓰는지는 조금 덜 확실합니다. 그래서 파일 경로처럼 정확해야 하는 값에는 방법 1이 낫습니다.

---

string input도 `{input}`으로 자동 노출되게 엔진(`promptVariables()`)을 바꾸는 방법도 있습니다. 하지만 지금은 "프롬프트 변수는 Agent input의 object 필드에서만 온다"는 설계로 정해져 있어서 따로 손대지 않았습니다. 원하시면 YAML 두 파일을 방법 1대로 바로 고쳐 드릴게요.
